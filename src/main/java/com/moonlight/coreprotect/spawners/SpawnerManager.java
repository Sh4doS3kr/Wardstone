package com.moonlight.coreprotect.spawners;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpawnerManager implements Listener {

    private final CoreProtectPlugin plugin;
    private final Map<String, SpawnerData> trackedSpawners = new ConcurrentHashMap<>();
    private final Map<String, ArmorStand> holograms = new ConcurrentHashMap<>();
    private final Random random = new Random();

    private static final int MIN_DELAY_SECONDS = 15;
    private static final int MAX_DELAY_SECONDS = 45;
    private static final int ACTIVATION_RANGE_SQ = 16 * 16; // 16 blocks

    public SpawnerManager(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        startTickTask();
        startHologramUpdateTask();
        startCleanupTask();
        // Initial scan of all loaded chunks (catches spawners from previous sessions)
        Bukkit.getScheduler().runTaskLater(plugin, this::scanAllLoadedChunks, 60L);
        plugin.getLogger().info("[SpawnerManager] Inicializado — spawners custom 15-45s con hologramas");
    }

    // ==================== DETECTION ====================

    // Cancel ALL vanilla spawner spawns — we handle everything
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        event.setCancelled(true);
        // Also register if not tracked yet
        Location loc = event.getSpawner().getLocation();
        String key = locKey(loc);
        if (!trackedSpawners.containsKey(key)) {
            registerSpawner(loc);
        }
    }

    // Detect spawners when chunks load (most reliable method)
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        // Delay 1 tick to ensure tile entities are fully loaded
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!chunk.isLoaded()) return;
            for (BlockState tile : chunk.getTileEntities()) {
                if (tile instanceof CreatureSpawner) {
                    String key = locKey(tile.getLocation());
                    if (!trackedSpawners.containsKey(key)) {
                        registerSpawner(tile.getLocation());
                    }
                }
            }
        }, 2L);
    }

    // Detect when a player places a spawner block
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlock().getType() == Material.SPAWNER) {
            // Read mob type from the item's BlockStateMeta
            ItemStack hand = event.getItemInHand();
            EntityType itemType = null;
            if (hand != null && hand.hasItemMeta() && hand.getItemMeta() instanceof BlockStateMeta) {
                BlockStateMeta bsm = (BlockStateMeta) hand.getItemMeta();
                if (bsm.getBlockState() instanceof CreatureSpawner) {
                    CreatureSpawner itemCs = (CreatureSpawner) bsm.getBlockState();
                    if (itemCs.getSpawnedType() != null && itemCs.getSpawnedType() != EntityType.UNKNOWN) {
                        itemType = itemCs.getSpawnedType();
                    }
                }
            }

            // Delay 1 tick so the spawner's tile entity is initialized, then apply the type
            Location loc = event.getBlock().getLocation();
            final EntityType mobType = itemType;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (loc.getBlock().getType() == Material.SPAWNER) {
                    // Apply the mob type from the item to the placed block
                    if (mobType != null) {
                        CreatureSpawner cs = (CreatureSpawner) loc.getBlock().getState();
                        cs.setSpawnedType(mobType);
                        cs.update();
                    }
                    String key = locKey(loc);
                    if (!trackedSpawners.containsKey(key)) {
                        registerSpawner(loc);
                    }
                }
            }, 2L);
        }
    }

    // Silk touch mining for spawners (vanilla behavior)
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.SPAWNER) return;

        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();

        // Check for silk touch enchantment
        boolean hasSilkTouch = tool != null && tool.containsEnchantment(org.bukkit.enchantments.Enchantment.SILK_TOUCH);
        if (!hasSilkTouch) return;

        // Get the spawner's mob type
        CreatureSpawner spawner = (CreatureSpawner) event.getBlock().getState();
        EntityType mobType = spawner.getSpawnedType();

        // Create spawner item with mob type preserved
        ItemStack spawnerItem = new ItemStack(Material.SPAWNER);
        BlockStateMeta meta = (BlockStateMeta) spawnerItem.getItemMeta();
        if (meta != null) {
            CreatureSpawner spawnerState = (CreatureSpawner) meta.getBlockState();
            spawnerState.setSpawnedType(mobType);
            meta.setBlockState(spawnerState);
            spawnerItem.setItemMeta(meta);
        }

        // Drop the custom spawner and cancel vanilla drop
        event.setDropItems(false);
        event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), spawnerItem);

        // Remove from tracking
        String key = locKey(event.getBlock().getLocation());
        removeHologram(key);
        trackedSpawners.remove(key);
    }

    // Scan all currently loaded chunks on startup
    private void scanAllLoadedChunks() {
        int found = 0;
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                for (BlockState tile : chunk.getTileEntities()) {
                    if (tile instanceof CreatureSpawner) {
                        String key = locKey(tile.getLocation());
                        if (!trackedSpawners.containsKey(key)) {
                            registerSpawner(tile.getLocation());
                            found++;
                        }
                    }
                }
            }
        }
        plugin.getLogger().info("[SpawnerManager] Escaneo inicial completado — " + found + " spawners encontrados");
    }

    // ==================== CORE LOGIC ====================

    private static String locKey(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    private static Location keyToLoc(String key) {
        String[] parts = key.split(":");
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        return new Location(world, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
    }

    private void registerSpawner(Location loc) {
        Block block = loc.getBlock();
        if (block.getType() != Material.SPAWNER) return;

        CreatureSpawner cs = (CreatureSpawner) block.getState();
        EntityType type = cs.getSpawnedType();
        if (type == null || type == EntityType.UNKNOWN) type = EntityType.PIG;

        // Disable vanilla spawner mechanics completely
        cs.setDelay(999999);
        cs.setMaxSpawnDelay(999999); // Set max FIRST to avoid IllegalArgumentException
        cs.setMinSpawnDelay(999999); // Then set min
        cs.setRequiredPlayerRange(0);
        cs.update();

        int delaySec = MIN_DELAY_SECONDS + random.nextInt(MAX_DELAY_SECONDS - MIN_DELAY_SECONDS + 1);
        int delayTicks = delaySec * 20;

        String key = locKey(loc);
        SpawnerData data = new SpawnerData(loc.clone(), type, delayTicks, delayTicks);
        trackedSpawners.put(key, data);

        // Create hologram
        createHologram(key, data);

        plugin.getLogger().info("[SpawnerManager] Registrado: " + type.name() + " en " +
                loc.getWorld().getName() + " [" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "]" +
                " — spawn cada " + MIN_DELAY_SECONDS + "-" + MAX_DELAY_SECONDS + "s (próximo: " + delaySec + "s)");
    }

    // ==================== SPAWNER TICK ====================

    private void startTickTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                Iterator<Map.Entry<String, SpawnerData>> it = trackedSpawners.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, SpawnerData> entry = it.next();
                    String key = entry.getKey();
                    SpawnerData data = entry.getValue();
                    Location loc = data.location;

                    // Validate chunk loaded
                    if (loc.getWorld() == null || !loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                        continue;
                    }

                    // Validate spawner still exists
                    if (loc.getBlock().getType() != Material.SPAWNER) {
                        removeHologram(key);
                        it.remove();
                        continue;
                    }

                    // Check if any player is within 16 blocks
                    boolean playerNearby = false;
                    for (Player p : loc.getWorld().getPlayers()) {
                        if (p.getLocation().distanceSquared(loc) <= ACTIVATION_RANGE_SQ) {
                            playerNearby = true;
                            break;
                        }
                    }
                    if (!playerNearby) continue;

                    // Decrement timer
                    data.ticksRemaining--;

                    if (data.ticksRemaining <= 0) {
                        spawnMob(loc, data.entityType);

                        // Reset with new random delay
                        int newDelaySec = MIN_DELAY_SECONDS + random.nextInt(MAX_DELAY_SECONDS - MIN_DELAY_SECONDS + 1);
                        data.totalTicks = newDelaySec * 20;
                        data.ticksRemaining = data.totalTicks;
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 1L);
    }

    // ==================== MOB SPAWNING ====================

    private void spawnMob(Location spawnerLoc, EntityType type) {
        World world = spawnerLoc.getWorld();
        if (world == null) return;

        for (int attempt = 0; attempt < 5; attempt++) {
            double ox = (random.nextDouble() - 0.5) * 8;
            double oz = (random.nextDouble() - 0.5) * 8;

            for (int dy = -2; dy <= 2; dy++) {
                int checkX = spawnerLoc.getBlockX() + (int) ox;
                int checkY = spawnerLoc.getBlockY() + dy;
                int checkZ = spawnerLoc.getBlockZ() + (int) oz;

                Block check = world.getBlockAt(checkX, checkY, checkZ);
                Block above = world.getBlockAt(checkX, checkY + 1, checkZ);
                Block above2 = world.getBlockAt(checkX, checkY + 2, checkZ);

                if (check.getType().isSolid() && above.getType().isAir() && above2.getType().isAir()) {
                    Location finalLoc = above.getLocation().add(0.5, 0, 0.5);
                    try {
                        world.spawnEntity(finalLoc, type);
                        world.spawnParticle(Particle.FLAME, finalLoc.clone().add(0, 0.5, 0), 10, 0.3, 0.3, 0.3, 0.02);
                        world.spawnParticle(Particle.SMOKE, finalLoc.clone().add(0, 0.5, 0), 5, 0.2, 0.2, 0.2, 0.01);
                    } catch (Exception ignored) {}
                    return;
                }
            }
        }

        // Fallback: spawn directly above spawner
        try {
            world.spawnEntity(spawnerLoc.clone().add(0.5, 1, 0.5), type);
        } catch (Exception ignored) {}
    }

    // ==================== HOLOGRAMS ====================

    private void createHologram(String key, SpawnerData data) {
        removeHologram(key);

        Location loc = data.location;
        World world = loc.getWorld();
        if (world == null) return;

        // Position hologram above the spawner block
        Location holoLoc = loc.clone().add(0.5, 1.8, 0.5);

        try {
            ArmorStand stand = world.spawn(holoLoc, ArmorStand.class, as -> {
                as.setVisible(false);
                as.setGravity(false);
                as.setSmall(true);
                as.setMarker(true);
                as.setInvulnerable(true);
                as.setCustomNameVisible(true);
                as.setCustomName(buildHologramText(data));
                as.addScoreboardTag("spawner_hologram");
                as.setPersistent(false); // Don't save to disk — we recreate on load
            });
            holograms.put(key, stand);
            plugin.getLogger().info("[SpawnerManager] Holograma creado para " + data.entityType.name() +
                    " en [" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "]");
        } catch (Exception e) {
            plugin.getLogger().warning("[SpawnerManager] Error creando holograma: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void removeHologram(String key) {
        ArmorStand stand = holograms.remove(key);
        if (stand != null && !stand.isDead()) {
            stand.remove();
        }
    }

    private void startHologramUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<String, SpawnerData> entry : trackedSpawners.entrySet()) {
                    String key = entry.getKey();
                    SpawnerData data = entry.getValue();
                    Location loc = data.location;

                    if (loc.getWorld() == null || !loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                        continue;
                    }

                    ArmorStand stand = holograms.get(key);
                    if (stand == null || stand.isDead()) {
                        // Recreate hologram if missing
                        if (loc.getBlock().getType() == Material.SPAWNER) {
                            createHologram(key, data);
                        }
                        continue;
                    }

                    stand.setCustomName(buildHologramText(data));
                }
            }
        }.runTaskTimer(plugin, 10L, 10L); // Update every 0.5s
    }

    private String buildHologramText(SpawnerData data) {
        double progress = 1.0 - ((double) data.ticksRemaining / data.totalTicks);
        int secondsLeft = Math.max(0, data.ticksRemaining / 20);

        int barLength = 20;
        int filled = (int) (progress * barLength);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < barLength; i++) {
            bar.append(i < filled ? "§a|" : "§7|");
        }

        String mobName = formatEntityName(data.entityType);

        String timeColor;
        if (progress < 0.33) timeColor = "§c";
        else if (progress < 0.66) timeColor = "§e";
        else timeColor = "§a";

        return "§f" + mobName + " " + bar + " " + timeColor + secondsLeft + "s";
    }

    private String formatEntityName(EntityType type) {
        String name = type.name().toLowerCase().replace("_", " ");
        StringBuilder sb = new StringBuilder();
        for (String word : name.split(" ")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    // ==================== CLEANUP ====================

    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Remove holograms for destroyed spawners
                Iterator<Map.Entry<String, ArmorStand>> it = holograms.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, ArmorStand> entry = it.next();
                    String key = entry.getKey();
                    ArmorStand stand = entry.getValue();

                    if (stand.isDead() || !stand.isValid()) {
                        it.remove();
                        continue;
                    }

                    Location loc = keyToLoc(key);
                    if (loc != null && loc.getWorld() != null &&
                            loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                        if (loc.getBlock().getType() != Material.SPAWNER) {
                            stand.remove();
                            it.remove();
                            trackedSpawners.remove(key);
                        }
                    }
                }

                // Clean up orphaned ArmorStands with our tag
                for (World world : plugin.getServer().getWorlds()) {
                    for (ArmorStand as : world.getEntitiesByClass(ArmorStand.class)) {
                        if (as.getScoreboardTags().contains("spawner_hologram")) {
                            boolean tracked = holograms.containsValue(as);
                            if (!tracked) {
                                as.remove();
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 600L, 1200L);
    }

    public void shutdown() {
        for (ArmorStand stand : holograms.values()) {
            if (stand != null && !stand.isDead()) {
                stand.remove();
            }
        }
        holograms.clear();
        trackedSpawners.clear();
    }

    // ==================== DATA ====================

    private static class SpawnerData {
        final Location location;
        final EntityType entityType;
        int totalTicks;
        int ticksRemaining;

        SpawnerData(Location location, EntityType entityType, int totalTicks, int ticksRemaining) {
            this.location = location;
            this.entityType = entityType;
            this.totalTicks = totalTicks;
            this.ticksRemaining = ticksRemaining;
        }
    }
}
