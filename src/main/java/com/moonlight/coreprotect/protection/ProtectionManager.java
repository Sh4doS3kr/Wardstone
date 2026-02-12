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
    }

    private void startAmbientTask(CoreProtectPlugin plugin) {
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                for (ProtectedRegion region : regions.values()) {
                    if (region.getCoreLocation() == null || region.getCoreLocation().getWorld() == null)
                        continue;

                    Location center = region.getCoreLocation();
                    int size = region.getSize();
                    int radius = size / 2;

                    // Esquinas: Pilares SUPER ALTOS de particulas BLANCAS
                    for (int corner = 0; corner < 4; corner++) {
                        double cx = (corner < 2 ? 1 : -1) * radius + (corner < 2 ? 0.5 : 0.5);
                        double cz = (corner % 2 == 0 ? 1 : -1) * radius + (corner % 2 == 0 ? 0.5 : 0.5);
                        Location baseLoc = center.clone().add(cx, 0.0, cz);

                        // Pilar de 20 bloques de altura (SUPER ALTO)
                        for (int y = 0; y < 20; y++) {
                            Location pilarLoc = baseLoc.clone().add(0, y + 0.5, 0);
                            // END_ROD = particulas blancas brillantes
                            center.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, pilarLoc, 2, 0.1, 0.1, 0.1, 0);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 40L, 40L); // 2 segundos
    }

    private void startActionBarTask(CoreProtectPlugin plugin) {
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                for (org.bukkit.entity.Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
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

    private boolean isSpawn(Location loc) {
        // Area: 101, 136 a -101, -66
        // X: -101 a 101
        // Z: -66 a 136
        // Ignoramos Y (infinito)
        if (loc.getWorld() == null)
            return false; // Asumimos world principal si no chequeamos nombre
        // Podriamos chequear loc.getWorld().getName().equals("world")?
        // Mejor aplicarlo globalmente a esas coordenadas por si acaso.

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

            int regionHalfSize = region.getSize() / 2;
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
