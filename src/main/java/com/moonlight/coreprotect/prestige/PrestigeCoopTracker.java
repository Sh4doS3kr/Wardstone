package com.moonlight.coreprotect.prestige;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerFishEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks collaborative (CO-OP) prestige mission stats.
 * Missions require proximity to other players to count.
 *
 * Types tracked:
 * - coop_mine     : mine blocks near an ally (20 blocks)
 * - coop_kill     : kill mobs near an ally
 * - coop_assist   : both you and ally damage same mob before it dies
 * - heal_ally     : heal other players with splash potions
 * - buddy_walk    : walk/sprint while an ally is within 30 blocks
 * - coop_fish     : fish while an ally is nearby
 * - coop_enchant  : enchant items while an ally is nearby
 * - sync_kill     : kill a mob within 5s of an ally's kill, while nearby
 * - coop_boss     : kill Wither/Dragon with allies nearby (50 blocks)
 * - gift_unique   : give money via /pay to unique different players
 */
public class PrestigeCoopTracker implements Listener {

    private final CoreProtectPlugin plugin;

    private static final double COOP_RADIUS = 20.0;
    private static final double BUDDY_RADIUS = 30.0;
    private static final double BOSS_RADIUS = 50.0;
    private static final long SYNC_KILL_WINDOW_MS = 5000;

    // Track damage dealers per living entity for assist detection
    private final Map<UUID, Set<UUID>> mobDamageDealers = new ConcurrentHashMap<>();

    // Track last kill timestamp per player for sync kills
    private final Map<UUID, Long> recentKillTimestamps = new ConcurrentHashMap<>();

    // Track last location per player for buddy walk calculation
    private final Map<UUID, Location> lastLocations = new ConcurrentHashMap<>();

    // Track last location per player for ice/piston walk exploit
    private final Map<UUID, Location> lastIceLocations = new ConcurrentHashMap<>();

    public PrestigeCoopTracker(CoreProtectPlugin plugin) {
        this.plugin = plugin;

        // Periodic buddy walk check every 5 seconds (100 ticks)
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickBuddyWalk, 100L, 100L);

        // Periodic ice/piston walk check every 1 second (20 ticks)
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickIceWalk, 20L, 20L);

        // Periodic coop mission completion check every 30 seconds
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickCoopCompletion, 600L, 600L);

        // Cleanup stale mob damage data every 5 minutes
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            mobDamageDealers.clear();
            recentKillTimestamps.entrySet().removeIf(e ->
                    System.currentTimeMillis() - e.getValue() > 60_000);
        }, 6000L, 6000L);
    }

    // ═══════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════

    private boolean hasNearbyAlly(Player player, double radius) {
        for (Entity e : player.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof Player) return true;
        }
        return false;
    }

    private List<Player> getNearbyAllies(Player player, double radius) {
        List<Player> allies = new ArrayList<>();
        for (Entity e : player.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof Player p) allies.add(p);
        }
        return allies;
    }

    private PrestigeManager.PrestigeData pd(UUID uuid) {
        PrestigeManager mgr = plugin.getPrestigeManager();
        return mgr == null ? null : mgr.getData(uuid);
    }

    // ═══════════════════════════════════════════════════════════
    // CO-OP MINE — mine blocks while an ally is within 20 blocks
    // ═══════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreakCoop(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!hasNearbyAlly(player, COOP_RADIUS)) return;
        PrestigeManager.PrestigeData data = pd(player.getUniqueId());
        if (data != null) data.coopMineBlocks++;
    }

    // ═══════════════════════════════════════════════════════════
    // DAMAGE TRACKING — record who damages each mob (for assists)
    // ═══════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity mob)) return;
        if (mob instanceof Player) return; // skip PvP

        Player damager = null;
        if (event.getDamager() instanceof Player p) damager = p;
        else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) damager = p;
        if (damager == null) return;

        mobDamageDealers.computeIfAbsent(mob.getUniqueId(), k -> ConcurrentHashMap.newKeySet())
                .add(damager.getUniqueId());
    }

    // ═══════════════════════════════════════════════════════════
    // CO-OP KILL / ASSIST / SYNC KILL / CO-OP BOSS
    // ═══════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeathCoop(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller();
        if (killer == null || dead instanceof Player) return;

        PrestigeManager mgr = plugin.getPrestigeManager();
        if (mgr == null) return;

        UUID killerUuid = killer.getUniqueId();
        PrestigeManager.PrestigeData killerData = mgr.getData(killerUuid);

        // ── Co-op Kill: kill near ally ──
        if (hasNearbyAlly(killer, COOP_RADIUS)) {
            killerData.coopKills++;
        }

        // ── Assist: 2+ players damaged same mob ──
        Set<UUID> dealers = mobDamageDealers.remove(dead.getUniqueId());
        if (dealers != null && dealers.size() >= 2) {
            for (UUID dealerUuid : dealers) {
                PrestigeManager.PrestigeData dd = mgr.getData(dealerUuid);
                dd.coopAssists++;
            }
        }

        // ── Sync Kill: ally also killed a mob within 5 seconds & 20 blocks ──
        long now = System.currentTimeMillis();
        boolean isSync = false;
        for (Map.Entry<UUID, Long> entry : recentKillTimestamps.entrySet()) {
            if (entry.getKey().equals(killerUuid)) continue;
            if (now - entry.getValue() > SYNC_KILL_WINDOW_MS) continue;
            Player other = Bukkit.getPlayer(entry.getKey());
            if (other != null && other.getWorld().equals(killer.getWorld())
                    && other.getLocation().distanceSquared(killer.getLocation()) <= COOP_RADIUS * COOP_RADIUS) {
                isSync = true;
                break;
            }
        }
        if (isSync) killerData.syncKills++;
        recentKillTimestamps.put(killerUuid, now);

        // ── Co-op Boss: Dragon / Wither with allies within 50 blocks ──
        if ((dead instanceof EnderDragon || dead instanceof Wither)
                && hasNearbyAlly(killer, BOSS_RADIUS)) {
            killerData.coopBossKills++;
            for (Player ally : getNearbyAllies(killer, BOSS_RADIUS)) {
                mgr.getData(ally.getUniqueId()).coopBossKills++;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // HEAL ALLY — splash potions that affect other players
    // ═══════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        if (!(event.getPotion().getShooter() instanceof Player thrower)) return;
        PrestigeManager.PrestigeData data = pd(thrower.getUniqueId());
        if (data == null) return;

        for (LivingEntity affected : event.getAffectedEntities()) {
            if (affected instanceof Player target && !target.equals(thrower)
                    && event.getIntensity(target) > 0) {
                data.healedAllies++;
                return; // count once per splash, even if multiple allies hit
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // CO-OP FISH — catch fish with an ally within 20 blocks
    // ═══════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFishCoop(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (!hasNearbyAlly(event.getPlayer(), COOP_RADIUS)) return;
        PrestigeManager.PrestigeData data = pd(event.getPlayer().getUniqueId());
        if (data != null) data.coopFished++;
    }

    // ═══════════════════════════════════════════════════════════
    // CO-OP ENCHANT — enchant items with an ally within 20 blocks
    // ═══════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchantCoop(EnchantItemEvent event) {
        if (!hasNearbyAlly(event.getEnchanter(), COOP_RADIUS)) return;
        PrestigeManager.PrestigeData data = pd(event.getEnchanter().getUniqueId());
        if (data != null) data.coopEnchants++;
    }

    // ═══════════════════════════════════════════════════════════
    // BUDDY WALK — move while ally is within 30 blocks (periodic)
    // ═══════════════════════════════════════════════════════════

    private void tickBuddyWalk() {
        PrestigeManager mgr = plugin.getPrestigeManager();
        if (mgr == null) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            PrestigeManager.PrestigeData data = mgr.getData(uuid);
            if (data.prestige <= 0) continue;

            Location current = player.getLocation();
            Location last = lastLocations.put(uuid, current.clone());

            if (last == null || !last.getWorld().equals(current.getWorld())) continue;
            double dist = last.distance(current);
            if (dist < 0.5 || dist > 100) continue; // ignore standing still and teleports

            if (hasNearbyAlly(player, BUDDY_RADIUS)) {
                data.buddyDistanceCm += (long) (dist * 100);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // ICE/PISTON WALK — detect passive movement on ice (farm exploit)
    // ═══════════════════════════════════════════════════════════

    private static final Set<Material> ICE_TYPES = Set.of(
            Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE, Material.FROSTED_ICE
    );

    private void tickIceWalk() {
        PrestigeManager mgr = plugin.getPrestigeManager();
        if (mgr == null) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            PrestigeManager.PrestigeData data = mgr.getData(uuid);
            if (data.prestige <= 0) continue;

            Location current = player.getLocation();
            Location last = lastIceLocations.put(uuid, current.clone());

            if (last == null || !last.getWorld().equals(current.getWorld())) continue;
            double dist = last.distance(current);
            if (dist < 0.3 || dist > 60) continue; // ignore still & teleports

            // Check if the block below feet is ice
            Block below = current.clone().subtract(0, 1, 0).getBlock();
            if (!ICE_TYPES.contains(below.getType())) continue;

            // Player is moving on ice — credit the distance
            data.extraWalkCm += (long) (dist * 100);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // GIFT UNIQUE — /pay to unique different players
    // ═══════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPayCommandCoop(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled()) return;
        String msg = event.getMessage().toLowerCase();
        if (!msg.startsWith("/pay ")) return;
        String[] parts = msg.split("\\s+");
        if (parts.length < 3) return;

        String targetName = parts[1];
        Player sender = event.getPlayer();
        if (plugin.getEconomy() == null) return;

        double balanceBefore = plugin.getEconomy().getBalance(sender);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!sender.isOnline()) return;
            double balanceAfter = plugin.getEconomy().getBalance(sender);
            if (balanceBefore - balanceAfter > 0) {
                PrestigeManager.PrestigeData data = pd(sender.getUniqueId());
                if (data != null) data.giftedUniquePlayers.add(targetName.toLowerCase());
            }
        }, 3L);
    }

    // ═══════════════════════════════════════════════════════════
    // PERIODIC COOP COMPLETION CHECK
    // ═══════════════════════════════════════════════════════════

    private void tickCoopCompletion() {
        PrestigeManager mgr = plugin.getPrestigeManager();
        if (mgr == null) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            PrestigeManager.PrestigeData data = mgr.getData(uuid);
            List<PrestigeManager.PrestigeMission> missions = mgr.getMissionsForPrestige(data.prestige);

            for (PrestigeManager.PrestigeMission m : missions) {
                if (data.completedMissions.contains(m.id)) continue;
                if (!isCoopType(m.type)) continue;
                long current = mgr.getMissionProgress(uuid, m.type);
                if (current >= m.target) {
                    mgr.completeMission(uuid, m.id);
                }
            }
        }
    }

    private static boolean isCoopType(String type) {
        return type.startsWith("coop_") || type.equals("heal_ally")
                || type.equals("buddy_walk") || type.equals("sync_kill")
                || type.equals("gift_unique");
    }
}
