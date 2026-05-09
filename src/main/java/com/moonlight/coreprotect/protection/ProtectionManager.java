package com.moonlight.coreprotect.protection;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.core.ProtectedRegion;
import org.bukkit.Location;

import java.util.*;

public class ProtectionManager {

    private final CoreProtectPlugin plugin;
    private final Map<UUID, ProtectedRegion> regions;
    private final Set<Location> lockedLocations;
    private final Map<UUID, Integer> activeVisuals;

    public ProtectionManager(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        this.regions = new HashMap<>();
        this.lockedLocations = new HashSet<>();
        this.activeVisuals = new HashMap<>();

        startAmbientTask(plugin);
        startActionBarTask(plugin);
        startBuffTask(plugin);
        startFixedTimeTask(plugin);
        startResourceGeneratorTask(plugin);
        startCoreIntegrityTask(plugin);
    }

    private void startAmbientTask(CoreProtectPlugin plugin) {
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                java.util.Collection<? extends org.bukkit.entity.Player> online = org.bukkit.Bukkit.getOnlinePlayers();
                if (online.isEmpty()) return;

                for (ProtectedRegion region : regions.values()) {
                    Location center = region.getCoreLocation();
                    if (center == null || center.getWorld() == null) continue;

                    // Only spawn particles if a player is within 80 blocks
                    boolean hasNearbyPlayer = false;
                    for (org.bukkit.entity.Player p : online) {
                        if (p.getWorld().equals(center.getWorld()) && p.getLocation().distanceSquared(center) < 6400) {
                            hasNearbyPlayer = true;
                            break;
                        }
                    }
                    if (!hasNearbyPlayer) continue;

                    // Check chunk is loaded
                    if (!center.getWorld().isChunkLoaded(center.getBlockX() >> 4, center.getBlockZ() >> 4)) continue;

                    int size = region.getEffectiveSize();
                    int radius = size / 2;

                    // Pilares de particulas en esquinas (reducido a 5 niveles)
                    for (int corner = 0; corner < 4; corner++) {
                        double cx = (corner < 2 ? 1 : -1) * radius + 0.5;
                        double cz = (corner % 2 == 0 ? 1 : -1) * radius + 0.5;
                        Location baseLoc = center.clone().add(cx, 0.0, cz);

                        for (int y = 0; y < 10; y += 2) {
                            Location pilarLoc = baseLoc.clone().add(0, y + 0.5, 0);
                            center.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, pilarLoc, 1, 0.05, 0.1, 0.05, 0);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 60L, 60L); // 3 segundos
    }

    private void startActionBarTask(CoreProtectPlugin plugin) {
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                for (org.bukkit.entity.Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
                    // No mostrar actionbar de protección en mundos especiales
                    String wName = player.getWorld().getName().toLowerCase();
                    if (wName.equals("minigames") || wName.contains("bossarena")) continue;

                    // No sobreescribir action bar prioritario (racha, vault warning, etc.)
                    if (com.moonlight.coreprotect.streak.DailyStreakListener.hasActiveOverride(player.getUniqueId())) continue;

                    ProtectedRegion region = getRegionAt(player.getLocation());
                    String message;

                    if (region != null) {
                        String ownerName = org.bukkit.Bukkit.getOfflinePlayer(region.getOwner()).getName();
                        message = "§c🛡 Zona de " + (ownerName != null ? ownerName : "Desconocido");
                    } else if (isSpawn(player.getLocation())) {
                        // Zona de Spawn (Protegida por el servidor)
                        message = "§6Tierra protegida (spawn)";
                    } else {
                        // Zona Salvaje (Tierra desprotegida)
                        message = "§a🌿 Tierra desprotegida";
                    }

                    // Usar Reflection para Action Bar (sin spam de comandos ni modo Title)
                    sendActionBar(player, message);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Cada segundo
    }

    private void startBuffTask(CoreProtectPlugin plugin) {
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                for (org.bukkit.entity.Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
                    ProtectedRegion region = getRegionAt(player.getLocation());

                    if (region != null && region.canAccess(player.getUniqueId())) {
                        // Speed Boost
                        if (region.isSpeedBoost()) {
                            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                    org.bukkit.potion.PotionEffectType.SPEED, 100, 0, true, false, true));
                        }

                        // Auto Heal
                        if (region.isAutoHeal()) {
                            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                    org.bukkit.potion.PotionEffectType.REGENERATION, 100, 0, true, false, true));
                        }

                        // Health Boost - longer duration to prevent flicker damage
                        if (region.getHealthBoostLevel() > 0) {
                            int amplifier = region.getHealthBoostLevel() - 1;
                            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                    org.bukkit.potion.PotionEffectType.HEALTH_BOOST, 100, amplifier, true, false, true));
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 40L); // Every 2 seconds, effects last 5s = always overlap
    }

    private void startFixedTimeTask(CoreProtectPlugin plugin) {
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                for (org.bukkit.entity.Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
                    ProtectedRegion region = getRegionAt(player.getLocation());
                    if (region != null && region.canAccess(player.getUniqueId()) && region.getFixedTime() > 0) {
                        long time = region.getFixedTime() == 1 ? 6000L : 18000L;
                        player.setPlayerTime(time, false);
                    } else {
                        if (player.isPlayerTimeRelative() == false) {
                            player.resetPlayerTime();
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 40L);
    }

    private void startResourceGeneratorTask(CoreProtectPlugin plugin) {
        new org.bukkit.scheduler.BukkitRunnable() {
            private final org.bukkit.Material[] RESOURCES = {
                org.bukkit.Material.COAL, org.bukkit.Material.COAL, org.bukkit.Material.COAL,
                org.bukkit.Material.RAW_IRON, org.bukkit.Material.RAW_IRON,
                org.bukkit.Material.RAW_GOLD,
                org.bukkit.Material.LAPIS_LAZULI,
                org.bukkit.Material.REDSTONE,
                org.bukkit.Material.DIAMOND,
                org.bukkit.Material.EMERALD
            };
            private final java.util.Random random = new java.util.Random();

            @Override
            public void run() {
                for (ProtectedRegion region : regions.values()) {
                    if (!region.isResourceGenerator()) continue;
                    Location coreLoc = region.getCoreLocation();
                    if (coreLoc == null || coreLoc.getWorld() == null) continue;

                    // Check for a chest adjacent to core (1 block in any cardinal direction)
                    org.bukkit.block.Block[] adjacent = {
                        coreLoc.getBlock().getRelative(org.bukkit.block.BlockFace.NORTH),
                        coreLoc.getBlock().getRelative(org.bukkit.block.BlockFace.SOUTH),
                        coreLoc.getBlock().getRelative(org.bukkit.block.BlockFace.EAST),
                        coreLoc.getBlock().getRelative(org.bukkit.block.BlockFace.WEST),
                        coreLoc.getBlock().getRelative(org.bukkit.block.BlockFace.UP)
                    };

                    for (org.bukkit.block.Block block : adjacent) {
                        if (block.getType() == org.bukkit.Material.CHEST || block.getType() == org.bukkit.Material.BARREL) {
                            org.bukkit.block.Container container = (org.bukkit.block.Container) block.getState();
                            org.bukkit.inventory.Inventory inv = container.getInventory();
                            if (inv.firstEmpty() != -1) {
                                org.bukkit.Material resource = RESOURCES[random.nextInt(RESOURCES.length)];
                                inv.addItem(new org.bukkit.inventory.ItemStack(resource, 1));
                            }
                            break; // Only fill the first chest found
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 6000L, 6000L); // Every 5 minutes
    }

    private void startCoreIntegrityTask(CoreProtectPlugin plugin) {
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                for (ProtectedRegion region : regions.values()) {
                    Location coreLoc = region.getCoreLocation();
                    if (coreLoc == null || coreLoc.getWorld() == null) continue;

                    // Only check loaded chunks
                    if (!coreLoc.getWorld().isChunkLoaded(coreLoc.getBlockX() >> 4, coreLoc.getBlockZ() >> 4)) continue;

                    // Skip if location is currently locked (animation in progress)
                    if (lockedLocations.contains(coreLoc)) continue;

                    org.bukkit.Material currentMat = coreLoc.getBlock().getType();

                    // If the core block is AIR or a non-core material, restore it
                    if (currentMat == org.bukkit.Material.AIR || currentMat == org.bukkit.Material.CAVE_AIR || currentMat == org.bukkit.Material.VOID_AIR) {
                        com.moonlight.coreprotect.core.CoreLevel coreLevel =
                                com.moonlight.coreprotect.core.CoreLevel.fromConfig(plugin.getConfig(), region.getLevel());
                        if (coreLevel != null) {
                            coreLoc.getBlock().setType(coreLevel.getMaterial());
                            plugin.getLogger().warning("[CoreIntegrity] Restored core at " +
                                    coreLoc.getWorld().getName() + " " + coreLoc.getBlockX() + " " +
                                    coreLoc.getBlockY() + " " + coreLoc.getBlockZ() +
                                    " (level " + region.getLevel() + " -> " + coreLevel.getMaterial() + ")");
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 600L, 600L); // Every 30 seconds
    }

    public boolean isSpawnProtected(Location loc) {
        return isSpawn(loc);
    }

    /**
     * Zona interna del spawn (barrera de cristales rojos PvP).
     * Dentro de esta zona se bloquean habilidades.
     * Fuera de esta zona pero dentro de isSpawnProtected se permite usar habilidades
     * (pero NO construir/romper).
     * Coordenadas: X -75 a 75, Z -40 a 110
     */
    public boolean isSpawnCore(Location loc) {
        if (loc.getWorld() == null) return false;
        String wn = loc.getWorld().getName().toLowerCase();
        if (wn.contains("bossarena") || wn.equals("minigames")) return false;
        org.bukkit.World.Environment env = loc.getWorld().getEnvironment();
        if (env == org.bukkit.World.Environment.NETHER || env == org.bukkit.World.Environment.THE_END) return false;
        double x = loc.getX();
        double z = loc.getZ();
        return (x >= -75 && x <= 75) && (z >= -40 && z <= 110);
    }
    
    private boolean isSpawn(Location loc) {
        // Area: 101, 136 a -101, -66
        // X: -101 a 101
        // Z: -66 a 136
        // Ignoramos Y (infinito)
        if (loc.getWorld() == null)
            return false; // Asumimos world principal si no chequeamos nombre
            
        // Ignorar mundos de boss arenas y minijuegos
        String worldName = loc.getWorld().getName().toLowerCase();
        if (worldName.contains("bossarena") || worldName.contains("_arena") || worldName.equals("minigames")) {
            return false;
        }

        double x = loc.getX();
        double z = loc.getZ();

        return (x >= -101 && x <= 101) && (z >= -66 && z <= 136);
    }

    private void sendActionBar(org.bukkit.entity.Player player, String message) {
        // METODO 1: Paper Adventure API (sendActionBar con Component)
        try {
            Class<?> componentClass = Class.forName("net.kyori.adventure.text.Component");
            java.lang.reflect.Method textMethod = componentClass.getMethod("text", String.class);
            Object component = textMethod.invoke(null, message);

            java.lang.reflect.Method sendActionBarMethod = player.getClass().getMethod("sendActionBar", componentClass);
            sendActionBarMethod.invoke(player, component);
            return; // Exito!
        } catch (Exception ignored) {
        }

        // METODO 2: Spigot BungeeChat API (player.spigot().sendMessage)
        try {
            Class<?> chatMessageTypeEnum = Class.forName("net.md_5.bungee.api.ChatMessageType");
            Object actionBarType = Enum.valueOf((Class<Enum>) chatMessageTypeEnum, "ACTION_BAR");

            Class<?> textComponentClass = Class.forName("net.md_5.bungee.api.chat.TextComponent");
            Object textComponent = textComponentClass.getConstructor(String.class).newInstance(message);

            Object spigot = player.getClass().getMethod("spigot").invoke(player);

            Class<?> baseComponentClass = Class.forName("net.md_5.bungee.api.chat.BaseComponent");
            Class<?> baseComponentArrayClass = java.lang.reflect.Array.newInstance(baseComponentClass, 0).getClass();

            // Buscar metodo con signature correcta
            for (java.lang.reflect.Method m : spigot.getClass().getMethods()) {
                if (m.getName().equals("sendMessage")) {
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length == 2 && params[0].equals(chatMessageTypeEnum)) {
                        // Crear array de BaseComponent
                        Object components = java.lang.reflect.Array.newInstance(baseComponentClass, 1);
                        java.lang.reflect.Array.set(components, 0, textComponent);
                        m.invoke(spigot, actionBarType, components);
                        return; // Exito!
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // METODO 3: Ultimo recurso - NMS packet (muy version-dependiente pero funciona)
        try {
            Object craftPlayer = player.getClass().getMethod("getHandle").invoke(player);

            // Obtener PlayerConnection
            java.lang.reflect.Field connectionField = null;
            for (java.lang.reflect.Field f : craftPlayer.getClass().getSuperclass().getDeclaredFields()) {
                if (f.getType().getSimpleName().contains("Connection") ||
                        f.getType().getSimpleName().contains("PlayerConnection")) {
                    connectionField = f;
                    break;
                }
            }

            if (connectionField == null) {
                for (java.lang.reflect.Field f : craftPlayer.getClass().getDeclaredFields()) {
                    if (f.getType().getSimpleName().contains("Connection")) {
                        connectionField = f;
                        break;
                    }
                }
            }

            if (connectionField != null) {
                connectionField.setAccessible(true);
                Object connection = connectionField.get(craftPlayer);

                // En 1.20.5+ usar ClientboundSetActionBarTextPacket
                // En versiones anteriores usar ClientboundSystemChatPacket
                // Intentar ambos
                try {
                    // Paper/Spigot 1.20.5+ tiene un metodo sendActionBar directo en ServerPlayer
                    for (java.lang.reflect.Method m : craftPlayer.getClass().getMethods()) {
                        if (m.getName().equals("sendActionBarMessage") || m.getName().contains("ActionBar")) {
                            if (m.getParameterCount() == 1) {
                                // Crear Component de Minecraft
                                Class<?> mcComponentClass = Class.forName("net.minecraft.network.chat.Component");
                                java.lang.reflect.Method literalMethod = mcComponentClass.getMethod("literal",
                                        String.class);
                                Object mcComponent = literalMethod.invoke(null, message);
                                m.invoke(craftPlayer, mcComponent);
                                return;
                            }
                        }
                    }
                } catch (Exception ignored2) {
                }
            }
        } catch (Exception ignored) {
        }

        // Si todo falla, no hacer nada (silencioso)
    }

    public void addRegion(ProtectedRegion region) {
        regions.put(region.getId(), region);
        if (plugin.getBlueMapIntegration() != null) {
            plugin.getBlueMapIntegration().addRegionMarker(region);
        }
    }

    public void removeRegion(UUID regionId) {
        ProtectedRegion region = regions.get(regionId);
        if (region != null && plugin.getBlueMapIntegration() != null) {
            plugin.getBlueMapIntegration().removeRegionMarker(region);
        }
        regions.remove(regionId);
    }

    public ProtectedRegion getRegion(UUID regionId) {
        return regions.get(regionId);
    }

    public ProtectedRegion getRegionAt(Location location) {
        for (ProtectedRegion region : regions.values()) {
            if (region.contains(location)) {
                return region;
            }
        }
        return null;
    }

    public ProtectedRegion getRegionByCore(Location coreLocation) {
        for (ProtectedRegion region : regions.values()) {
            Location core = region.getCoreLocation();
            if (core != null &&
                    core.getBlockX() == coreLocation.getBlockX() &&
                    core.getBlockY() == coreLocation.getBlockY() &&
                    core.getBlockZ() == coreLocation.getBlockZ() &&
                    core.getWorld().getName().equals(coreLocation.getWorld().getName())) {
                return region;
            }
        }
        return null;
    }

    public List<ProtectedRegion> getRegionsByOwner(UUID owner) {
        List<ProtectedRegion> ownerRegions = new ArrayList<>();
        for (ProtectedRegion region : regions.values()) {
            if (region.getOwner().equals(owner)) {
                ownerRegions.add(region);
            }
        }
        return ownerRegions;
    }

    public boolean isLocationProtected(Location location) {
        return getRegionAt(location) != null;
    }

    public boolean canBuild(UUID player, Location location) {
        ProtectedRegion region = getRegionAt(location);
        if (region == null) {
            return true;
        }
        return region.canAccess(player);
    }

    public boolean canPlaceCore(Location location, int size) {
        int halfSize = size / 2;
        int minX = location.getBlockX() - halfSize;
        int maxX = location.getBlockX() + halfSize;
        int minZ = location.getBlockZ() - halfSize;
        int maxZ = location.getBlockZ() + halfSize;

        // 1. Verificar si solapa con el SPAWN (Zona protegida por el servidor)
        // Spawn: X[-101, 101], Z[-66, 136]
        int spawnMinX = -101;
        int spawnMaxX = 101;
        int spawnMinZ = -66;
        int spawnMaxZ = 136;

        if (minX <= spawnMaxX && maxX >= spawnMinX && minZ <= spawnMaxZ && maxZ >= spawnMinZ) {
            // Hay solapamiento con el Spawn
            return false;
        }

        // 2. Verificar solapamiento con otras regiones
        for (ProtectedRegion region : regions.values()) {
            Location core = region.getCoreLocation();
            if (core == null || !core.getWorld().getName().equals(location.getWorld().getName())) {
                continue;
            }

            int regionHalfSize = region.getEffectiveSize() / 2;
            int rMinX = region.getCoreX() - regionHalfSize;
            int rMaxX = region.getCoreX() + regionHalfSize;
            int rMinZ = region.getCoreZ() - regionHalfSize;
            int rMaxZ = region.getCoreZ() + regionHalfSize;

            // Verificar si hay solapamiento
            if (minX <= rMaxX && maxX >= rMinX && minZ <= rMaxZ && maxZ >= rMinZ) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if a core can be upgraded to a new size without overlapping
     * another player's protection or the spawn zone.
     * The region's own area is excluded from the check.
     */
    public boolean canUpgradeCore(ProtectedRegion upgrading, int newSize) {
        int halfSize = newSize / 2;
        int minX = upgrading.getCoreX() - halfSize;
        int maxX = upgrading.getCoreX() + halfSize;
        int minZ = upgrading.getCoreZ() - halfSize;
        int maxZ = upgrading.getCoreZ() + halfSize;

        // 1. Check spawn overlap
        int spawnMinX = -101, spawnMaxX = 101, spawnMinZ = -66, spawnMaxZ = 136;
        if (minX <= spawnMaxX && maxX >= spawnMinX && minZ <= spawnMaxZ && maxZ >= spawnMinZ) {
            return false;
        }

        // 2. Check overlap with other regions (skip self)
        for (ProtectedRegion region : regions.values()) {
            if (region.getId().equals(upgrading.getId())) continue;
            Location core = region.getCoreLocation();
            if (core == null || !core.getWorld().getName().equals(upgrading.getWorldName())) continue;

            int rHalf = region.getEffectiveSize() / 2;
            int rMinX = region.getCoreX() - rHalf;
            int rMaxX = region.getCoreX() + rHalf;
            int rMinZ = region.getCoreZ() - rHalf;
            int rMaxZ = region.getCoreZ() + rHalf;

            if (minX <= rMaxX && maxX >= rMinX && minZ <= rMaxZ && maxZ >= rMinZ) {
                return false;
            }
        }
        return true;
    }

    public Collection<ProtectedRegion> getAllRegions() {
        return regions.values();
    }

    public void clearRegions() {
        regions.clear();
    }

    public int getRegionCount() {
        return regions.size();
    }

    // Locking system
    public void lockLocation(Location loc) {
        lockedLocations.add(loc);
    }

    public void unlockLocation(Location loc) {
        lockedLocations.remove(loc);
    }

    public boolean isLocked(Location loc) {
        return lockedLocations.contains(loc);
    }

    // Visuals system
    public boolean isVisualActive(UUID regionId) {
        return activeVisuals.containsKey(regionId);
    }

    public void addVisualTask(UUID regionId, int taskId) {
        activeVisuals.put(regionId, taskId);
    }

    public void removeVisualTask(UUID regionId) {
        activeVisuals.remove(regionId);
    }

    public Integer getVisualTask(UUID regionId) {
        return activeVisuals.get(regionId);
    }
}
