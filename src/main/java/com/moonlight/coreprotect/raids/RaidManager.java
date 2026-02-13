package com.moonlight.coreprotect.raids;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.core.ProtectedRegion;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class RaidManager implements Listener {

    private final CoreProtectPlugin plugin;
    private final Map<UUID, ActiveRaid> activeRaids = new HashMap<>();
    private final Set<UUID> raidMobUUIDs = new HashSet<>();
    private final Random random = new Random();

    private static final long RAID_INTERVAL_TICKS = 20L * 60 * 45; // 45 min
    private static final long WARNING_TICKS = 20L * 60 * 3; // 3 min warning
    private static final int WAVE_MOB_COUNT = 10;

    // Mob names for variety
    private static final String[] ZOMBIE_NAMES = {
        "Saqueador Putrefacto", "Muerto Hambriento", "Invasor Zombie",
        "Devorador de Núcleos", "Zombie Furioso", "Carroñero"
    };
    private static final String[] SKELETON_NAMES = {
        "Arquero Invasor", "Esqueleto Saqueador", "Francotirador Óseo"
    };
    private static final String[] SPECIAL_NAMES = {
        "Brujo Invasor", "Araña Venenosa", "Creeper Suicida",
        "Zombie Gigante", "Esqueleto de Élite"
    };

    // Valuable drops
    private static final Material[] VALUABLE_DROPS = {
        Material.DIAMOND, Material.EMERALD, Material.GOLD_INGOT,
        Material.IRON_INGOT, Material.GOLDEN_APPLE, Material.ENDER_PEARL,
        Material.EXPERIENCE_BOTTLE, Material.NAME_TAG
    };
    // Junk drops
    private static final Material[] JUNK_DROPS = {
        Material.ROTTEN_FLESH, Material.BONE, Material.SPIDER_EYE,
        Material.STRING, Material.GUNPOWDER, Material.STICK,
        Material.POISONOUS_POTATO, Material.DEAD_BUSH
    };

    private BossBar countdownBar = null;

    public RaidManager(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        startRaidScheduler();
    }

    private void startRaidScheduler() {
        new BukkitRunnable() {
            private long tickCounter = 0;
            private long nextRaidTick = RAID_INTERVAL_TICKS;

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

                // Create bar lazily
                if (countdownBar == null) {
                    countdownBar = Bukkit.createBossBar(
                            ChatColor.GRAY + "⚔ Próxima oleada en " + formatTime(timeUntilRaid) + " ⚔",
                            BarColor.GREEN, BarStyle.SEGMENTED_20);
                    countdownBar.setProgress(1.0);
                }

                // Show only to players inside their zone AND not in an active raid
                for (Player p : Bukkit.getOnlinePlayers()) {
                    ProtectedRegion region = plugin.getProtectionManager().getRegionAt(p.getLocation());
                    boolean inZone = region != null && region.canAccess(p.getUniqueId());
                    boolean inActiveRaid = region != null && activeRaids.containsKey(region.getId());

                    if (inZone && !inActiveRaid) {
                        if (!countdownBar.getPlayers().contains(p)) {
                            countdownBar.addPlayer(p);
                        }
                    } else {
                        countdownBar.removePlayer(p);
                    }
                }

                // Update bar
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
        if (region.isNoMobSpawn()) return;

        Location coreLoc = region.getCoreLocation();
        if (coreLoc == null || coreLoc.getWorld() == null) return;

        Player owner = Bukkit.getPlayer(region.getOwner());
        if (owner == null || !owner.isOnline()) return;

        World world = coreLoc.getWorld();

        // Ensure chunk is loaded for spawning
        Chunk coreChunk = coreLoc.getChunk();
        if (!coreChunk.isLoaded()) coreChunk.load();

        // Create raid boss bar
        BossBar raidBar = Bukkit.createBossBar(
                ChatColor.DARK_RED + "⚔ ¡OLEADA ATACANDO ZONA DE " + owner.getName().toUpperCase() + "! ⚔",
                BarColor.RED, BarStyle.SOLID);
        raidBar.setProgress(1.0);
        raidBar.addPlayer(owner);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p != owner && p.getWorld().equals(world) && p.getLocation().distanceSquared(coreLoc) < 10000) {
                raidBar.addPlayer(p);
            }
        }

        // Spawn mobs near the core (close range, on solid ground)
        List<Entity> spawnedMobs = new ArrayList<>();
        int spawnRadius = Math.min(region.getSize() / 2, 25); // Max 25 blocks out
        if (spawnRadius < 5) spawnRadius = 5;

        for (int i = 0; i < WAVE_MOB_COUNT; i++) {
            // Try multiple times to find a valid spawn
            Location spawnLoc = null;
            for (int attempt = 0; attempt < 10; attempt++) {
                double angle = random.nextDouble() * 2 * Math.PI;
                int dist = 5 + random.nextInt(spawnRadius);
                int spawnX = coreLoc.getBlockX() + (int)(Math.cos(angle) * dist);
                int spawnZ = coreLoc.getBlockZ() + (int)(Math.sin(angle) * dist);

                // Load chunk if needed
                Chunk chunk = world.getChunkAt(spawnX >> 4, spawnZ >> 4);
                if (!chunk.isLoaded()) chunk.load();

                int spawnY = world.getHighestBlockYAt(spawnX, spawnZ);
                Location test = new Location(world, spawnX + 0.5, spawnY + 1.0, spawnZ + 0.5);

                // Check ground is solid
                if (world.getBlockAt(spawnX, spawnY, spawnZ).getType().isSolid()) {
                    spawnLoc = test;
                    break;
                }
            }

            if (spawnLoc == null) {
                // Fallback: spawn right next to core
                spawnLoc = coreLoc.clone().add(
                    random.nextInt(5) - 2, 1, random.nextInt(5) - 2);
            }

            Entity mob = spawnRaidMob(world, spawnLoc, i);
            if (mob != null) {
                spawnedMobs.add(mob);
                raidMobUUIDs.add(mob.getUniqueId());
            }
        }

        plugin.getMessageManager().send(owner, "raids.started");
        owner.playSound(owner.getLocation(), Sound.EVENT_RAID_HORN, 1.0f, 1.0f);

        ActiveRaid raid = new ActiveRaid(region.getId(), raidBar, spawnedMobs, region.getOwner());
        activeRaids.put(region.getId(), raid);

        // Timeout after 3 min
        new BukkitRunnable() {
            @Override
            public void run() {
                endRaid(region.getId(), false);
            }
        }.runTaskLater(plugin, 20L * 60 * 3);

        // Track alive mobs
        new BukkitRunnable() {
            @Override
            public void run() {
                ActiveRaid r = activeRaids.get(region.getId());
                if (r == null) { cancel(); return; }

                int alive = 0;
                for (Entity e : r.getMobs()) {
                    if (e != null && !e.isDead() && e.isValid()) alive++;
                }
                double prog = (double) alive / WAVE_MOB_COUNT;
                r.getBossBar().setProgress(Math.max(0, Math.min(1, prog)));
                r.getBossBar().setTitle(ChatColor.DARK_RED + "⚔ OLEADA (" + alive + "/" + WAVE_MOB_COUNT + ") ⚔");

                if (alive <= 0) {
                    endRaid(region.getId(), true);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private Entity spawnRaidMob(World world, Location loc, int index) {
        // Decide mob type based on wave composition
        // 40% zombies, 20% skeletons, 15% husks/strays, 10% spiders, 10% creepers, 5% witch
        Entity mob;
        String customName;
        double roll = random.nextDouble();

        if (roll < 0.40) {
            // Zombie variants
            if (random.nextBoolean()) {
                mob = world.spawnEntity(loc, EntityType.ZOMBIE);
            } else {
                mob = world.spawnEntity(loc, EntityType.HUSK);
            }
            customName = ChatColor.RED + "⚔ " + ZOMBIE_NAMES[random.nextInt(ZOMBIE_NAMES.length)];
        } else if (roll < 0.60) {
            // Skeleton variants
            if (random.nextBoolean()) {
                mob = world.spawnEntity(loc, EntityType.SKELETON);
            } else {
                mob = world.spawnEntity(loc, EntityType.STRAY);
            }
            customName = ChatColor.GOLD + "⚔ " + SKELETON_NAMES[random.nextInt(SKELETON_NAMES.length)];
        } else if (roll < 0.75) {
            mob = world.spawnEntity(loc, EntityType.SPIDER);
            customName = ChatColor.DARK_PURPLE + "⚔ Araña Invasora";
        } else if (roll < 0.85) {
            mob = world.spawnEntity(loc, EntityType.CREEPER);
            customName = ChatColor.GREEN + "⚔ Creeper Suicida";
        } else if (roll < 0.95) {
            // Armored zombie (mini-boss)
            Zombie zombie = (Zombie) world.spawnEntity(loc, EntityType.ZOMBIE);
            zombie.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
            zombie.getEquipment().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
            zombie.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
            zombie.getEquipment().setHelmetDropChance(0f);
            zombie.getEquipment().setChestplateDropChance(0f);
            zombie.getEquipment().setItemInMainHandDropChance(0f);
            if (zombie.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
                zombie.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(40.0);
                zombie.setHealth(40.0);
            }
            mob = zombie;
            customName = ChatColor.DARK_RED + "⚔ Zombie de Élite";
        } else {
            mob = world.spawnEntity(loc, EntityType.WITCH);
            customName = ChatColor.LIGHT_PURPLE + "⚔ Brujo Invasor";
        }

        if (mob instanceof LivingEntity) {
            LivingEntity living = (LivingEntity) mob;
            living.setCustomName(customName);
            living.setCustomNameVisible(true);
            living.setRemoveWhenFarAway(false);
            // Make them persistent so they don't despawn
            // Prevent burning in daylight via helmet
            if (mob instanceof Zombie && ((LivingEntity) mob).getEquipment().getHelmet() == null) {
                ((LivingEntity) mob).getEquipment().setHelmet(new ItemStack(Material.LEATHER_HELMET));
                ((LivingEntity) mob).getEquipment().setHelmetDropChance(0f);
            }
        }

        return mob;
    }

    // Handle drops when raid mobs die
    @EventHandler(priority = EventPriority.HIGH)
    public void onRaidMobDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!raidMobUUIDs.contains(entity.getUniqueId())) return;

        raidMobUUIDs.remove(entity.getUniqueId());

        // Clear default drops and replace with custom
        event.getDrops().clear();
        event.setDroppedExp(random.nextInt(20) + 10);

        Location loc = entity.getLocation();

        // 30% chance valuable drop, 70% junk
        if (random.nextDouble() < 0.30) {
            Material mat = VALUABLE_DROPS[random.nextInt(VALUABLE_DROPS.length)];
            int amount = mat == Material.DIAMOND || mat == Material.EMERALD ? 1 : random.nextInt(3) + 1;
            event.getDrops().add(new ItemStack(mat, amount));
        } else {
            Material mat = JUNK_DROPS[random.nextInt(JUNK_DROPS.length)];
            event.getDrops().add(new ItemStack(mat, random.nextInt(3) + 1));
        }

        // Elite mobs always drop something good
        String name = entity.getCustomName();
        if (name != null && name.contains("Élite")) {
            Material bonus = random.nextBoolean() ? Material.DIAMOND : Material.GOLDEN_APPLE;
            event.getDrops().add(new ItemStack(bonus, 1));
        }
    }

    public void endRaid(UUID regionId, boolean victory) {
        ActiveRaid raid = activeRaids.remove(regionId);
        if (raid == null) return;

        raid.getBossBar().removeAll();

        // Clean up mob tracking + remove alive mobs
        for (Entity e : raid.getMobs()) {
            if (e != null) {
                raidMobUUIDs.remove(e.getUniqueId());
                if (!e.isDead() && e.isValid()) e.remove();
            }
        }

        Player owner = Bukkit.getPlayer(raid.getOwnerId());
        if (owner != null && owner.isOnline()) {
            if (victory) {
                plugin.getMessageManager().send(owner, "raids.victory");
                owner.playSound(owner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);

                plugin.getAchievementManager().incrementStat(owner.getUniqueId(), "raids_survived");
                int survived = plugin.getAchievementManager().getPlayerStat(owner.getUniqueId(), "raids_survived");
                plugin.getAchievementManager().grant(owner, "survive_raid");
                if (survived >= 5) plugin.getAchievementManager().grant(owner, "survive_5_raids");
                if (survived >= 10) plugin.getAchievementManager().grant(owner, "survive_10_raids");
                plugin.getAchievementManager().savePlayerData();

                double reward = 500 + random.nextInt(1500);
                plugin.getEconomy().depositPlayer(owner, reward);
                owner.sendMessage(ChatColor.GOLD + "  +" + ChatColor.YELLOW + "$" + (int) reward
                        + ChatColor.GOLD + " por defender tu zona");
            } else {
                plugin.getMessageManager().send(owner, "raids.timeout");
            }
        }
    }

    private String formatTime(long ticks) {
        long seconds = ticks / 20;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        if (minutes > 0) return minutes + "m " + seconds + "s";
        return seconds + "s";
    }

    public boolean isRaidActive(UUID regionId) {
        return activeRaids.containsKey(regionId);
    }

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
