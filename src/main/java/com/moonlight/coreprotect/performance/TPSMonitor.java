package com.moonlight.coreprotect.performance;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class TPSMonitor {

    private final JavaPlugin plugin;
    private BukkitRunnable monitorTask;
    private boolean safeMode = false;

    // Saved normal values to restore later
    private int savedViewDistance = -1;
    private int savedSimDistance = -1;

    private static final double TPS_THRESHOLD_ENTER = 13.0;
    private static final double TPS_THRESHOLD_EXIT  = 16.0;

    // Safe-mode target values
    private static final int SAFE_VIEW_DISTANCE = 4;
    private static final int SAFE_SIM_DISTANCE  = 4;

    public TPSMonitor(JavaPlugin plugin) {
        this.plugin = plugin;
        startMonitoring();
        plugin.getLogger().info("[TPSMonitor] Inicializado — protección automática a <" +
                (int) TPS_THRESHOLD_ENTER + " TPS");
    }

    // ==================== MONITORING ====================

    private void startMonitoring() {
        monitorTask = new BukkitRunnable() {
            @Override
            public void run() {
                double tps = getCurrentTPS();

                if (!safeMode && tps < TPS_THRESHOLD_ENTER) {
                    enterSafeMode(tps);
                } else if (safeMode && tps >= TPS_THRESHOLD_EXIT) {
                    exitSafeMode(tps);
                } else if (safeMode) {
                    // Proactive cleanup while in safe mode
                    cleanEntities();
                }
            }
        };
        monitorTask.runTaskTimer(plugin, 100L, 100L); // Check every 5 seconds
    }

    // ==================== ENTER SAFE MODE ====================

    private void enterSafeMode(double tps) {
        safeMode = true;

        // Save current values before changing
        World mainWorld = Bukkit.getWorlds().get(0);
        savedViewDistance = getWorldViewDistance(mainWorld);
        savedSimDistance = getWorldSimDistance(mainWorld);

        // Reduce view distance and simulation distance on ALL worlds
        for (World world : Bukkit.getWorlds()) {
            setWorldViewDistance(world, SAFE_VIEW_DISTANCE);
            setWorldSimDistance(world, SAFE_SIM_DISTANCE);

            // Slash mob spawn limits
            try {
                world.setSpawnLimit(SpawnCategory.MONSTER, 10);
                world.setSpawnLimit(SpawnCategory.ANIMAL, 3);
                world.setSpawnLimit(SpawnCategory.AMBIENT, 0);
                world.setSpawnLimit(SpawnCategory.WATER_AMBIENT, 0);
                world.setSpawnLimit(SpawnCategory.WATER_ANIMAL, 0);
                world.setSpawnLimit(SpawnCategory.WATER_UNDERGROUND_CREATURE, 0);
            } catch (Exception ignored) {}
        }

        // Immediate entity cleanup
        cleanEntities();

        // Notify only operators
        notifyOps(String.format("§c[TPS] §fModo seguro ACTIVADO §7(TPS: %.1f) §8— view=%d, sim=%d, mobs reducidos",
                tps, SAFE_VIEW_DISTANCE, SAFE_SIM_DISTANCE));
        plugin.getLogger().warning("[TPSMonitor] SAFE MODE ON — TPS: " + String.format("%.1f", tps) +
                " — view=" + SAFE_VIEW_DISTANCE + " sim=" + SAFE_SIM_DISTANCE);
    }

    // ==================== EXIT SAFE MODE ====================

    private void exitSafeMode(double tps) {
        safeMode = false;

        // Restore view and simulation distance
        int restoreView = savedViewDistance > 0 ? savedViewDistance : 10;
        int restoreSim = savedSimDistance > 0 ? savedSimDistance : 10;

        for (World world : Bukkit.getWorlds()) {
            setWorldViewDistance(world, restoreView);
            setWorldSimDistance(world, restoreSim);

            // Restore mob spawn limits to defaults
            try {
                world.setSpawnLimit(SpawnCategory.MONSTER, 70);
                world.setSpawnLimit(SpawnCategory.ANIMAL, 10);
                world.setSpawnLimit(SpawnCategory.AMBIENT, 15);
                world.setSpawnLimit(SpawnCategory.WATER_AMBIENT, 5);
                world.setSpawnLimit(SpawnCategory.WATER_ANIMAL, 5);
                world.setSpawnLimit(SpawnCategory.WATER_UNDERGROUND_CREATURE, 5);
            } catch (Exception ignored) {}
        }

        notifyOps(String.format("§a[TPS] §fModo seguro DESACTIVADO §7(TPS: %.1f) §8— restaurado view=%d, sim=%d",
                tps, restoreView, restoreSim));
        plugin.getLogger().info("[TPSMonitor] SAFE MODE OFF — TPS: " + String.format("%.1f", tps));
    }

    // ==================== ENTITY CLEANUP ====================

    private void cleanEntities() {
        for (World world : Bukkit.getWorlds()) {
            int removedItems = 0, removedMobs = 0;
            for (Entity e : world.getEntities()) {
                if (e instanceof Player) continue;

                // Remove ground items (keep max 100)
                if (e instanceof Item) {
                    if (removedItems++ > 100) e.remove();
                    continue;
                }
                // Remove XP orbs
                if (e instanceof ExperienceOrb) { e.remove(); continue; }
                // Remove falling blocks
                if (e instanceof FallingBlock) { e.remove(); continue; }
                // Remove ambient mobs (bats etc)
                if (e instanceof Ambient) { e.remove(); continue; }

                // Remove unnamed passive animals far from players
                if (e instanceof Animals && e.getCustomName() == null
                        && !(e instanceof Tameable && ((Tameable) e).isTamed())) {
                    boolean nearPlayer = false;
                    for (Player p : world.getPlayers()) {
                        if (p.getLocation().distanceSquared(e.getLocation()) < 1024) {
                            nearPlayer = true;
                            break;
                        }
                    }
                    if (!nearPlayer && removedMobs++ < 80) {
                        e.remove();
                    }
                }
            }
        }
    }

    // ==================== UTILITIES ====================

    private void notifyOps(String message) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isOp()) {
                p.sendMessage(message);
            }
        }
    }

    private double getCurrentTPS() {
        try {
            Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
            Object recentTps = server.getClass().getField("recentTps").get(server);
            if (recentTps instanceof double[]) {
                return Math.min(20.0, ((double[]) recentTps)[0]);
            }
        } catch (Exception e) {
            try {
                Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
                Object[] recentTps = (Object[]) server.getClass().getField("recentTps").get(server);
                return Math.min(20.0, ((Number) recentTps[0]).doubleValue());
            } catch (Exception ignored) {}
        }
        return 20.0;
    }

    // ==================== PLAYER EVENTS ====================

    public void onPlayerJoin(Player player) {
        // If safe mode is active, new player gets reduced settings automatically (world-level)
        if (safeMode && player.isOp()) {
            player.sendMessage("§c[TPS] §fModo seguro activo — rendimiento reducido temporalmente");
        }
    }

    public void onPlayerQuit(Player player) {
        // Nothing needed — settings are world-level
    }

    // ==================== LIFECYCLE ====================

    public void shutdown() {
        if (monitorTask != null) monitorTask.cancel();
        // Ensure normal state on shutdown
        if (safeMode) {
            for (World world : Bukkit.getWorlds()) {
                try {
                    if (savedViewDistance > 0) setWorldViewDistance(world, savedViewDistance);
                    if (savedSimDistance > 0) setWorldSimDistance(world, savedSimDistance);
                    world.setSpawnLimit(SpawnCategory.MONSTER, 70);
                    world.setSpawnLimit(SpawnCategory.ANIMAL, 10);
                    world.setSpawnLimit(SpawnCategory.AMBIENT, 15);
                } catch (Exception ignored) {}
            }
        }
    }

    public void forceRestoreNormal() {
        if (safeMode) {
            exitSafeMode(getCurrentTPS());
            notifyOps("§a[TPS] §fRestauración forzada por comando");
        }
    }

    public boolean isSafeMode() {
        return safeMode;
    }

    // ==================== REFLECTION HELPERS (Paper API) ====================

    private void setWorldViewDistance(World world, int distance) {
        try { world.getClass().getMethod("setViewDistance", int.class).invoke(world, distance); } catch (Exception ignored) {}
    }

    private void setWorldSimDistance(World world, int distance) {
        try { world.getClass().getMethod("setSimulationDistance", int.class).invoke(world, distance); } catch (Exception ignored) {}
    }

    private int getWorldViewDistance(World world) {
        try { return (int) world.getClass().getMethod("getViewDistance").invoke(world); } catch (Exception e) { return 10; }
    }

    private int getWorldSimDistance(World world) {
        try { return (int) world.getClass().getMethod("getSimulationDistance").invoke(world); } catch (Exception e) { return 10; }
    }
}
