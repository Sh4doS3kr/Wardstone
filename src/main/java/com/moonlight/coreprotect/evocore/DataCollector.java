package com.moonlight.coreprotect.evocore;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.core.ProtectedRegion;
import com.moonlight.coreprotect.evocore.metrics.ItemMetrics;
import com.moonlight.coreprotect.evocore.metrics.MobMetrics;
import com.moonlight.coreprotect.evocore.metrics.ZoneMetrics;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DataCollector implements Listener {

    private final CoreProtectPlugin plugin;
    private final Map<UUID, ZoneMetrics> zoneMetrics = new ConcurrentHashMap<>();
    private final Map<EntityType, MobMetrics> mobMetrics = new ConcurrentHashMap<>();
    private final Map<Material, ItemMetrics> itemMetrics = new ConcurrentHashMap<>();

    // Track which zone each player is in
    private final Map<UUID, UUID> playerCurrentZone = new ConcurrentHashMap<>();
    // Track mob damage start times for avg kill time
    private final Map<UUID, Long> mobFirstDamageTime = new ConcurrentHashMap<>();
    // Track equipped items for meta analysis
    private final Map<Material, Integer> equippedWeapons = new ConcurrentHashMap<>();

    // Global stats
    private long totalMobKills = 0;
    private long totalBlocksBroken = 0;
    private long totalBlocksPlaced = 0;
    private int onlinePlayers = 0;

    public DataCollector(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        startTasks();
    }

    private void startTasks() {
        // Track player zone presence every 2 seconds
        new BukkitRunnable() {
            @Override
            public void run() {
                onlinePlayers = Bukkit.getOnlinePlayers().size();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    trackPlayerZone(p);
                    trackEquippedItem(p);
                }
            }
        }.runTaskTimer(plugin, 40L, 40L);

        // Hourly reset
        new BukkitRunnable() {
            @Override
            public void run() {
                for (ZoneMetrics zm : zoneMetrics.values()) zm.resetHourly();
                for (MobMetrics mm : mobMetrics.values()) mm.resetHourly();
                for (ItemMetrics im : itemMetrics.values()) im.decayScores();
            }
        }.runTaskTimer(plugin, 20L * 60 * 60, 20L * 60 * 60);

        // Daily reset
        new BukkitRunnable() {
            @Override
            public void run() {
                for (ZoneMetrics zm : zoneMetrics.values()) zm.resetDaily();
                for (MobMetrics mm : mobMetrics.values()) mm.resetDaily();
            }
        }.runTaskTimer(plugin, 20L * 60 * 60 * 24, 20L * 60 * 60 * 24);
    }

    private void trackPlayerZone(Player player) {
        ProtectedRegion region = plugin.getProtectionManager().getRegionAt(player.getLocation());
        UUID playerId = player.getUniqueId();
        UUID oldZone = playerCurrentZone.get(playerId);

        if (region != null) {
            UUID newZone = region.getId();
            ZoneMetrics zm = getOrCreateZoneMetrics(newZone);
            zm.addPlayerTime(40); // 2 seconds in ticks

            if (!newZone.equals(oldZone)) {
                // Left old zone
                if (oldZone != null) {
                    ZoneMetrics oldZm = zoneMetrics.get(oldZone);
                    if (oldZm != null) oldZm.recordPlayerLeave();
                }
                zm.recordPlayerEnter();
                playerCurrentZone.put(playerId, newZone);
            }
        } else if (oldZone != null) {
            ZoneMetrics oldZm = zoneMetrics.get(oldZone);
            if (oldZm != null) oldZm.recordPlayerLeave();
            playerCurrentZone.remove(playerId);
        }
    }

    private void trackEquippedItem(Player player) {
        if (player.getInventory().getItemInMainHand().getType() != Material.AIR) {
            Material mat = player.getInventory().getItemInMainHand().getType();
            equippedWeapons.merge(mat, 1, Integer::sum);
            getOrCreateItemMetrics(mat).recordEquipped();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) return;

        EntityType type = entity.getType();
        MobMetrics mm = getOrCreateMobMetrics(type);

        // Calculate kill time
        Long firstDamage = mobFirstDamageTime.remove(entity.getUniqueId());
        long killTime = firstDamage != null ? System.currentTimeMillis() - firstDamage : 5000;

        // Count nearby players
        int nearbyPlayers = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().equals(entity.getWorld()) && p.getLocation().distanceSquared(entity.getLocation()) < 2500) {
                nearbyPlayers++;
            }
        }

        mm.recordKill(killTime, Math.max(1, nearbyPlayers));
        totalMobKills++;

        // Zone tracking
        ProtectedRegion region = plugin.getProtectionManager().getRegionAt(entity.getLocation());
        if (region != null) {
            getOrCreateZoneMetrics(region.getId()).recordKill();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof LivingEntity && !(event.getEntity() instanceof Player)) {
            mobFirstDamageTime.putIfAbsent(event.getEntity().getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        totalBlocksBroken++;
        ProtectedRegion region = plugin.getProtectionManager().getRegionAt(event.getBlock().getLocation());
        if (region != null) {
            ZoneMetrics zm = getOrCreateZoneMetrics(region.getId());
            zm.recordBlockBroken();
            zm.recordResourceCollected();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        totalBlocksPlaced++;
        ProtectedRegion region = plugin.getProtectionManager().getRegionAt(event.getBlock().getLocation());
        if (region != null) {
            getOrCreateZoneMetrics(region.getId()).recordBlockPlaced();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChestOpen(InventoryOpenEvent event) {
        if (event.getInventory().getLocation() != null) {
            ProtectedRegion region = plugin.getProtectionManager().getRegionAt(event.getInventory().getLocation());
            if (region != null) {
                getOrCreateZoneMetrics(region.getId()).recordChestOpened();
            }
        }
    }

    // --- Accessors ---

    public ZoneMetrics getOrCreateZoneMetrics(UUID regionId) {
        return zoneMetrics.computeIfAbsent(regionId, ZoneMetrics::new);
    }

    public MobMetrics getOrCreateMobMetrics(EntityType type) {
        return mobMetrics.computeIfAbsent(type, MobMetrics::new);
    }

    public ItemMetrics getOrCreateItemMetrics(Material material) {
        return itemMetrics.computeIfAbsent(material, ItemMetrics::new);
    }

    public Map<UUID, ZoneMetrics> getAllZoneMetrics() { return Collections.unmodifiableMap(zoneMetrics); }
    public Map<EntityType, MobMetrics> getAllMobMetrics() { return Collections.unmodifiableMap(mobMetrics); }
    public Map<Material, ItemMetrics> getAllItemMetrics() { return Collections.unmodifiableMap(itemMetrics); }
    public Map<Material, Integer> getEquippedWeapons() { return Collections.unmodifiableMap(equippedWeapons); }

    public long getTotalMobKills() { return totalMobKills; }
    public long getTotalBlocksBroken() { return totalBlocksBroken; }
    public long getTotalBlocksPlaced() { return totalBlocksPlaced; }
    public int getOnlinePlayers() { return onlinePlayers; }
}
