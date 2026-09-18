package com.moonlight.coreprotect.survivalupdate;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.GameRuleUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gestiona el sistema de Survival Update:
 *  - Mundo lobby vacío
 *  - Spawn del lobby
 *  - Portales RTP (End Portal Frame → random teleport al world)
 */
public class SurvivalUpdateManager {

    private final CoreProtectPlugin plugin;
    private final File configFile;

    // ═══ Configuración persistente ═══
    private String lobbyWorldName;
    private Location lobbySpawn;
    private final List<Location> rtpPortals = new ArrayList<>();
    private final List<Location> launcherPads = new ArrayList<>();
    private Location launcherTarget = null;
    private boolean setupComplete = false;

    // ═══ RTP Config ═══
    private static final int RTP_MIN_RADIUS = 500;
    private static final int RTP_MAX_RADIUS = 5000;
    private static final String SURVIVAL_WORLD = "world";

    public SurvivalUpdateManager(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "survivalupdate.yml");
        loadConfig();
    }

    // ==================== MUNDO LOBBY ====================

    /**
     * Crea un mundo void vacío para el lobby.
     * El admin copiará el mapa del lobby aquí con WorldEdit/etc.
     */
    public World createLobbyWorld(String worldName) {
        this.lobbyWorldName = worldName;

        World existing = Bukkit.getWorld(worldName);
        if (existing != null) {
            plugin.getLogger().info("[SurvivalUpdate] Mundo lobby '" + worldName + "' ya existe.");
            saveConfig();
            return existing;
        }

        plugin.getLogger().info("[SurvivalUpdate] Creando mundo void para lobby: " + worldName);
        WorldCreator creator = new WorldCreator(worldName);
        creator.generator(new VoidChunkGenerator());
        creator.type(WorldType.FLAT);
        creator.generateStructures(false);
        creator.environment(World.Environment.NORMAL);

        World world = creator.createWorld();
        if (world != null) {
            GameRuleUtil.set(world, "doDaylightCycle", false);
            GameRuleUtil.set(world, "doWeatherCycle", false);
            GameRuleUtil.set(world, "doMobSpawning", false);
            GameRuleUtil.set(world, "doFireTick", false);
            GameRuleUtil.set(world, "mobGriefing", false);
            GameRuleUtil.set(world, "announceAdvancements", false);
            world.setTime(6000); // Mediodía perpetuo
            world.setDifficulty(Difficulty.PEACEFUL);
            plugin.getLogger().info("[SurvivalUpdate] Mundo lobby creado exitosamente.");
        }

        saveConfig();
        return world;
    }

    // ==================== SPAWN ====================

    /**
     * Guarda la posición actual del jugador como spawn del lobby.
     */
    public void setLobbySpawn(Location loc) {
        this.lobbySpawn = loc.clone();
        if (loc.getWorld() != null) {
            loc.getWorld().setSpawnLocation(loc);
        }
        saveConfig();
    }

    public Location getLobbySpawn() {
        return lobbySpawn;
    }

    // ==================== PORTALES RTP ====================

    /**
     * Detecta END_PORTAL_FRAME en un radio de 10 bloques alrededor del jugador
     * y los guarda como portales de RTP.
     */
    public int detectRtpPortals(Player player) {
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) return 0;

        int radius = 10;
        Set<String> foundKeys = new HashSet<>();
        int count = 0;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = world.getBlockAt(
                            center.getBlockX() + x,
                            center.getBlockY() + y,
                            center.getBlockZ() + z);
                    if (block.getType() == Material.END_PORTAL) {
                        // Agrupar por posición de bloque para evitar duplicados
                        String key = block.getX() + "," + block.getY() + "," + block.getZ();
                        if (foundKeys.add(key)) {
                            Location portalLoc = block.getLocation().add(0.5, 0, 0.5);
                            // Evitar duplicados con portales ya guardados
                            if (!isNearExistingRtpPortal(portalLoc)) {
                                rtpPortals.add(portalLoc);
                                count++;
                            }
                        }
                    }
                }
            }
        }

        saveConfig();
        return count;
    }

    private boolean isNearExistingRtpPortal(Location loc) {
        for (Location existing : rtpPortals) {
            if (existing.getWorld() != null && loc.getWorld() != null
                    && existing.getWorld().getName().equals(loc.getWorld().getName())
                    && existing.distanceSquared(loc) < 4) {
                return true;
            }
        }
        return false;
    }

    /**
     * Comprueba si una ubicación está cerca de un portal RTP registrado.
     */
    public boolean isNearRtpPortal(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        for (Location portal : rtpPortals) {
            if (portal.getWorld() != null
                    && portal.getWorld().getName().equals(loc.getWorld().getName())
                    && portal.distanceSquared(loc) < 25) { // 5 bloques
                return true;
            }
        }
        return false;
    }

    /**
     * Genera coordenadas RTP aleatorias en el mundo survival.
     */
    public Location getRandomRtpLocation() {
        World world = Bukkit.getWorld(SURVIVAL_WORLD);
        if (world == null) {
            plugin.getLogger().warning("[SurvivalUpdate] Mundo '" + SURVIVAL_WORLD + "' no encontrado para RTP.");
            return null;
        }

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 50; attempt++) {
            int x = rng.nextInt(-RTP_MAX_RADIUS, RTP_MAX_RADIUS + 1);
            int z = rng.nextInt(-RTP_MAX_RADIUS, RTP_MAX_RADIUS + 1);

            // Asegurar distancia mínima del spawn
            if (Math.abs(x) < RTP_MIN_RADIUS && Math.abs(z) < RTP_MIN_RADIUS) continue;

            int y = world.getHighestBlockYAt(x, z);
            if (y < 1) continue;

            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();
            // Evitar océanos y lava
            if (type == Material.WATER || type == Material.LAVA) continue;

            Location loc = new Location(world, x + 0.5, y + 1, z + 0.5);
            // Pre-cargar chunk
            world.getChunkAt(loc).load(true);
            return loc;
        }

        // Fallback
        return new Location(world, 0.5, world.getHighestBlockYAt(0, 0) + 1, 0.5);
    }


    // ==================== LAUNCHER PADS (SLIME BLOCKS) ====================

    /**
     * Detecta SLIME_BLOCK en un radio de 5 bloques alrededor del jugador
     * y los guarda como launcher pads.
     */
    public int detectLauncherPads(Player player) {
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) return 0;

        int radius = 5;
        Set<String> foundKeys = new HashSet<>();
        int count = 0;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = world.getBlockAt(
                            center.getBlockX() + x,
                            center.getBlockY() + y,
                            center.getBlockZ() + z);
                    if (block.getType() == Material.SLIME_BLOCK) {
                        String key = block.getX() + "," + block.getY() + "," + block.getZ();
                        if (foundKeys.add(key)) {
                            Location padLoc = block.getLocation();
                            if (!isNearExistingLauncherPad(padLoc)) {
                                launcherPads.add(padLoc);
                                count++;
                            }
                        }
                    }
                }
            }
        }

        saveConfig();
        return count;
    }

    private boolean isNearExistingLauncherPad(Location loc) {
        for (Location existing : launcherPads) {
            if (existing.getWorld() != null && loc.getWorld() != null
                    && existing.getWorld().getName().equals(loc.getWorld().getName())
                    && existing.distanceSquared(loc) < 4) {
                return true;
            }
        }
        return false;
    }

    /**
     * Comprueba si una ubicación está encima de un launcher pad registrado.
     */
    public boolean isOnLauncherPad(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        // Comprobar el bloque debajo del jugador
        Location below = loc.clone().subtract(0, 1, 0);
        for (Location pad : launcherPads) {
            if (pad.getWorld() != null
                    && pad.getWorld().getName().equals(below.getWorld().getName())
                    && pad.getBlockX() == below.getBlockX()
                    && pad.getBlockY() == below.getBlockY()
                    && pad.getBlockZ() == below.getBlockZ()) {
                return true;
            }
        }
        return false;
    }

    public void setLauncherTarget(Location target) {
        this.launcherTarget = target;
        saveConfig();
    }

    public Location getLauncherTarget() {
        return launcherTarget;
    }

    public List<Location> getLauncherPads() {
        return Collections.unmodifiableList(launcherPads);
    }

    // ==================== UTILIDADES ====================

    public boolean isLobbyWorld(World world) {
        return lobbyWorldName != null && world != null
                && world.getName().equalsIgnoreCase(lobbyWorldName);
    }

    public String getLobbyWorldName() {
        return lobbyWorldName;
    }

    public boolean isSetupComplete() {
        return setupComplete;
    }

    public void setSetupComplete(boolean complete) {
        this.setupComplete = complete;
        saveConfig();
    }

    public List<Location> getRtpPortals() {
        return Collections.unmodifiableList(rtpPortals);
    }


    // ==================== CONFIG ====================

    public void saveConfig() {
        YamlConfiguration yaml = new YamlConfiguration();

        yaml.set("setup-complete", setupComplete);
        yaml.set("lobby-world", lobbyWorldName);

        if (lobbySpawn != null) {
            yaml.set("lobby-spawn.world", lobbySpawn.getWorld() != null ? lobbySpawn.getWorld().getName() : lobbyWorldName);
            yaml.set("lobby-spawn.x", lobbySpawn.getX());
            yaml.set("lobby-spawn.y", lobbySpawn.getY());
            yaml.set("lobby-spawn.z", lobbySpawn.getZ());
            yaml.set("lobby-spawn.yaw", lobbySpawn.getYaw());
            yaml.set("lobby-spawn.pitch", lobbySpawn.getPitch());
        }

        // Guardar portales RTP
        List<Map<String, Object>> rtpList = new ArrayList<>();
        for (Location loc : rtpPortals) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("world", loc.getWorld() != null ? loc.getWorld().getName() : lobbyWorldName);
            map.put("x", loc.getX());
            map.put("y", loc.getY());
            map.put("z", loc.getZ());
            rtpList.add(map);
        }
        yaml.set("rtp-portals", rtpList);

        // Guardar launcher pads
        List<Map<String, Object>> launcherList = new ArrayList<>();
        for (Location loc : launcherPads) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("world", loc.getWorld() != null ? loc.getWorld().getName() : lobbyWorldName);
            map.put("x", loc.getX());
            map.put("y", loc.getY());
            map.put("z", loc.getZ());
            launcherList.add(map);
        }
        yaml.set("launcher-pads", launcherList);

        if (launcherTarget != null) {
            yaml.set("launcher-target.world", launcherTarget.getWorld() != null ? launcherTarget.getWorld().getName() : lobbyWorldName);
            yaml.set("launcher-target.x", launcherTarget.getX());
            yaml.set("launcher-target.y", launcherTarget.getY());
            yaml.set("launcher-target.z", launcherTarget.getZ());
        }

        try {
            yaml.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[SurvivalUpdate] Error guardando config: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public void loadConfig() {
        if (!configFile.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);

        setupComplete = yaml.getBoolean("setup-complete", false);
        lobbyWorldName = yaml.getString("lobby-world", null);

        // Cargar spawn
        if (yaml.contains("lobby-spawn")) {
            String worldName = yaml.getString("lobby-spawn.world", lobbyWorldName);
            World world = worldName != null ? Bukkit.getWorld(worldName) : null;
            if (world != null) {
                lobbySpawn = new Location(world,
                        yaml.getDouble("lobby-spawn.x"),
                        yaml.getDouble("lobby-spawn.y"),
                        yaml.getDouble("lobby-spawn.z"),
                        (float) yaml.getDouble("lobby-spawn.yaw"),
                        (float) yaml.getDouble("lobby-spawn.pitch"));
            }
        }

        // Cargar portales RTP
        rtpPortals.clear();
        List<Map<?, ?>> rtpList = yaml.getMapList("rtp-portals");
        for (Map<?, ?> map : rtpList) {
            String wName = (String) map.get("world");
            World w = wName != null ? Bukkit.getWorld(wName) : null;
            if (w != null) {
                double x = ((Number) map.get("x")).doubleValue();
                double y = ((Number) map.get("y")).doubleValue();
                double z = ((Number) map.get("z")).doubleValue();
                rtpPortals.add(new Location(w, x, y, z));
            }
        }

        // Cargar launcher pads
        launcherPads.clear();
        List<Map<?, ?>> launcherList = yaml.getMapList("launcher-pads");
        for (Map<?, ?> map : launcherList) {
            String wName = (String) map.get("world");
            World w = wName != null ? Bukkit.getWorld(wName) : null;
            if (w != null) {
                double x = ((Number) map.get("x")).doubleValue();
                double y = ((Number) map.get("y")).doubleValue();
                double z = ((Number) map.get("z")).doubleValue();
                launcherPads.add(new Location(w, x, y, z));
            }
        }

        // Cargar launcher target
        if (yaml.contains("launcher-target")) {
            String wName = yaml.getString("launcher-target.world", lobbyWorldName);
            World w = wName != null ? Bukkit.getWorld(wName) : null;
            if (w != null) {
                launcherTarget = new Location(w,
                        yaml.getDouble("launcher-target.x"),
                        yaml.getDouble("launcher-target.y"),
                        yaml.getDouble("launcher-target.z"));
            }
        }

        plugin.getLogger().info("[SurvivalUpdate] Config cargada — Lobby: " + lobbyWorldName
                + " | RTP portals: " + rtpPortals.size()
                + " | Launcher pads: " + launcherPads.size());
    }

    /**
     * Asegurar que el mundo lobby se carga al iniciar el servidor.
     */
    public void ensureLobbyLoaded() {
        if (lobbyWorldName == null || lobbyWorldName.isEmpty()) return;

        World world = Bukkit.getWorld(lobbyWorldName);
        if (world != null) return;

        plugin.getLogger().info("[SurvivalUpdate] Cargando mundo lobby: " + lobbyWorldName);
        WorldCreator creator = new WorldCreator(lobbyWorldName);
        creator.generator(new VoidChunkGenerator());
        creator.type(WorldType.FLAT);
        creator.generateStructures(false);
        creator.environment(World.Environment.NORMAL);
        creator.createWorld();

        // Recargar locations que dependían del mundo
        loadConfig();
    }

    // ==================== VOID GENERATOR ====================

    public static class VoidChunkGenerator extends ChunkGenerator {
        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateBedrock() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }
    }
}
