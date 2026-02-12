package com.moonlight.coreprotect.raids;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.core.ProtectedRegion;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class RaidManager implements Listener {

    private final CoreProtectPlugin plugin;
    private final Map<UUID, ActiveRaid> activeRaids = new HashMap<>(); // regionId -> ActiveRaid
    private final Random random = new Random();

    // Raid config
    private static final long RAID_INTERVAL_TICKS = 20L * 60 * 45; // Every 45 minutes
    private static final long WARNING_TICKS = 20L * 60 * 3; // 3 min warning before raid
    private static final int WAVE_MOB_COUNT = 8;

    public RaidManager(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        startRaidScheduler();
    }

    private void startRaidScheduler() {
        // Boss bar only visible when inside your zone
        new BukkitRunnable() {
            private long tickCounter = 0;
            private long nextRaidTick = RAID_INTERVAL_TICKS;
            private BossBar countdownBar = null;

            @Override
            public void run() {
                tickCounter += 20;

                Collection<ProtectedRegion> allRegions = plugin.getProtectionManager().getAllRegions();
                if (allRegions.isEmpty()) {
                    tickCounter = 0;
                    if (countdownBar != null) { countdownBar.removeAll(); countdownBar = null; }
                    return;
                }

                long timeUntilRaid = nextRaidTick - tickCounter;

                // Create bar on first tick (invisible until player enters zone)
                if (countdownBar == null) {
                    countdownBar = Bukkit.createBossBar(
                            ChatColor.GRAY + "⚔ Próxima oleada en " + formatTime(timeUntilRaid) + " ⚔",
                            BarColor.GREEN, BarStyle.SEGMENTED_20);
                    countdownBar.setProgress(1.0);
                }

                // Only show to players inside a protected zone they have access to
                for (Player p : Bukkit.getOnlinePlayers()) {
                    ProtectedRegion region = plugin.getProtectionManager().getRegionAt(p.getLocation());
                    if (region != null && region.canAccess(p.getUniqueId())) {
                        if (!countdownBar.getPlayers().contains(p)) {
                            countdownBar.addPlayer(p);
                        }
                    } else {
                        countdownBar.removePlayer(p);
                    }
                }

                // Update bar appearance based on time remaining
                double progress = Math.max(0, Math.min(1, (double) timeUntilRaid / nextRaidTick));
                countdownBar.setProgress(progress);

                if (timeUntilRaid > WARNING_TICKS) {
                    countdownBar.setColor(progress > 0.5 ? BarColor.GREEN : BarColor.YELLOW);
                    countdownBar.setTitle(ChatColor.GRAY + "⚔ Próxima oleada en " + formatTime(timeUntilRaid) + " ⚔");
                } else if (timeUntilRaid > 0) {
                    countdownBar.setColor(BarColor.RED);
                    countdownBar.setTitle(ChatColor.RED + "" + ChatColor.BOLD + "⚔ ¡OLEADA EN " + formatTime(timeUntilRaid) + "! ⚔");
                }

                // Time to raid!
                if (tickCounter >= nextRaidTick) {
                    countdownBar.removeAll();
                    countdownBar = null;
                    tickCounter = 0;
                    nextRaidTick = RAID_INTERVAL_TICKS;

                    List<ProtectedRegion> regionList = new ArrayList<>(allRegions);
                    Collections.shuffle(regionList);
                    int raidCount = Math.min(regionList.size(), random.nextInt(3) + 1);
                    for (int i = 0; i < raidCount; i++) {
                        startRaid(regionList.get(i));
                    }
                }
            }
        }.runTaskTimer(plugin, 20L * 60, 20L);
    }

    private void startRaid(ProtectedRegion region) {
        if (activeRaids.containsKey(region.getId())) return;
        if (region.isNoMobSpawn()) return; // Anti-mobs upgrade protects from raids too

        Location coreLoc = region.getCoreLocation();
        if (coreLoc == null || coreLoc.getWorld() == null) return;

        // Check if owner or members are online
        Player owner = Bukkit.getPlayer(region.getOwner());
        if (owner == null || !owner.isOnline()) return;

        // Create raid boss bar
        String ownerName = owner.getName();
        BossBar raidBar = Bukkit.createBossBar(
                ChatColor.DARK_RED + "⚔ ¡OLEADA ATACANDO ZONA DE " + ownerName.toUpperCase() + "! ⚔",
                BarColor.RED, BarStyle.SOLID);
        raidBar.setProgress(1.0);
        raidBar.addPlayer(owner);

        // Add nearby players
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getLocation().distance(coreLoc) < 100) {
                raidBar.addPlayer(p);
            }
        }

        // Spawn mobs around the core
        List<Entity> spawnedMobs = new ArrayList<>();
        int halfSize = region.getSize() / 2;
        World world = coreLoc.getWorld();

        for (int i = 0; i < WAVE_MOB_COUNT; i++) {
            // Spawn at edges of the protected zone
            double angle = random.nextDouble() * 2 * Math.PI;
            int spawnX = coreLoc.getBlockX() + (int)(Math.cos(angle) * halfSize);
            int spawnZ = coreLoc.getBlockZ() + (int)(Math.sin(angle) * halfSize);
            int spawnY = world.getHighestBlockYAt(spawnX, spawnZ) + 1;
            Location spawnLoc = new Location(world, spawnX + 0.5, spawnY, spawnZ + 0.5);

            EntityType mobType = getRandomRaidMob();
            Entity mob = world.spawnEntity(spawnLoc, mobType);
            if (mob instanceof LivingEntity) {
                ((LivingEntity) mob).setCustomName(ChatColor.RED + "⚔ Invasor");
                ((LivingEntity) mob).setCustomNameVisible(true);
            }
            spawnedMobs.add(mob);
        }

        // Announce to owner
        plugin.getMessageManager().send(owner, "raids.started");
        owner.playSound(owner.getLocation(), Sound.EVENT_RAID_HORN, 1.0f, 1.0f);

        ActiveRaid raid = new ActiveRaid(region.getId(), raidBar, spawnedMobs, region.getOwner());
        activeRaids.put(region.getId(), raid);

        // Raid timeout - auto-end after 3 minutes
        new BukkitRunnable() {
            @Override
            public void run() {
                endRaid(region.getId(), false);
            }
        }.runTaskLater(plugin, 20L * 60 * 3);

        // Track mob deaths
        new BukkitRunnable() {
            @Override
            public void run() {
                ActiveRaid r = activeRaids.get(region.getId());
                if (r == null) {
                    cancel();
                    return;
                }
                // Count alive mobs
                int alive = 0;
                for (Entity e : r.getMobs()) {
                    if (e != null && !e.isDead() && e.isValid()) alive++;
                }
                double progress = (double) alive / WAVE_MOB_COUNT;
                r.getBossBar().setProgress(Math.max(0, Math.min(1, progress)));
                r.getBossBar().setTitle(ChatColor.DARK_RED + "⚔ OLEADA (" + alive + "/" + WAVE_MOB_COUNT + ") ⚔");

                if (alive <= 0) {
                    endRaid(region.getId(), true);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void endRaid(UUID regionId, boolean victory) {
        ActiveRaid raid = activeRaids.remove(regionId);
        if (raid == null) return;

        raid.getBossBar().removeAll();

        // Kill remaining mobs
        for (Entity e : raid.getMobs()) {
            if (e != null && !e.isDead() && e.isValid()) {
                e.remove();
            }
        }

        Player owner = Bukkit.getPlayer(raid.getOwnerId());
        if (owner != null && owner.isOnline()) {
            if (victory) {
                plugin.getMessageManager().send(owner, "raids.victory");
                owner.playSound(owner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);

                // Grant achievement
                plugin.getAchievementManager().incrementStat(owner.getUniqueId(), "raids_survived");
                int survived = plugin.getAchievementManager().getPlayerStat(owner.getUniqueId(), "raids_survived");
                plugin.getAchievementManager().grant(owner, "survive_raid");
                if (survived >= 5) plugin.getAchievementManager().grant(owner, "survive_5_raids");
                if (survived >= 10) plugin.getAchievementManager().grant(owner, "survive_10_raids");
                plugin.getAchievementManager().savePlayerData();

                // Reward: money
                double reward = 500 + (random.nextInt(1500));
                plugin.getEconomy().depositPlayer(owner, reward);
                owner.sendMessage(ChatColor.GOLD + "  +" + ChatColor.YELLOW + "$" + (int) reward
                        + ChatColor.GOLD + " por defender tu zona");
            } else {
                plugin.getMessageManager().send(owner, "raids.timeout");
            }
        }
    }

    private EntityType getRandomRaidMob() {
        EntityType[] mobs = {
            EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.ZOMBIE,
            EntityType.SKELETON, EntityType.SKELETON,
            EntityType.SPIDER,
            EntityType.CREEPER,
            EntityType.WITCH
        };
        return mobs[random.nextInt(mobs.length)];
    }

    private String formatTime(long ticks) {
        long seconds = ticks / 20;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    public boolean isRaidActive(UUID regionId) {
        return activeRaids.containsKey(regionId);
    }

    // Inner class
    private static class ActiveRaid {
        private final UUID regionId;
        private final BossBar bossBar;
        private final List<Entity> mobs;
        private final UUID ownerId;

        public ActiveRaid(UUID regionId, BossBar bossBar, List<Entity> mobs, UUID ownerId) {
            this.regionId = regionId;
            this.bossBar = bossBar;
            this.mobs = mobs;
            this.ownerId = ownerId;
        }

        public UUID getRegionId() { return regionId; }
        public BossBar getBossBar() { return bossBar; }
        public List<Entity> getMobs() { return mobs; }
        public UUID getOwnerId() { return ownerId; }
    }
}
