package com.moonlight.coreprotect.spawn;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;

/**
 * Fuerza el spawn de mobs del Nether (Wither Skeletons, Blazes, etc.)
 * cuando el servidor no los genera naturalmente.
 * Ejecuta un ciclo periódico que spawneado mobs nativos del Nether
 * cerca de jugadores en el mundo Nether.
 */
public class NetherSpawnFixer implements Listener {

    private final CoreProtectPlugin plugin;
    private final Random random = new Random();

    public NetherSpawnFixer(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        startForceSpawnTask();
    }

    /**
     * Asegura que el mundo Nether tenga las gamerules correctas para spawn de mobs.
     */
    public void ensureNetherSettings() {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.NETHER) {
                com.moonlight.coreprotect.util.GameRuleUtil.set(world, "doMobSpawning", true);
                world.setDifficulty(org.bukkit.Difficulty.HARD);
                try {
                    world.setSpawnLimit(SpawnCategory.MONSTER, 70);
                } catch (Exception ignored) {}
                plugin.getLogger().info("[NetherFix] Nether '" + world.getName() + "' — doMobSpawning=true, difficulty=HARD, monsterLimit=70");
            }
        }
    }

    /**
     * Cada 30 segundos, spawneado mobs nativos del Nether cerca de jugadores
     * que estén en el Nether, si hay pocos mobs hostiles alrededor.
     */
    private void startForceSpawnTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    World world = player.getWorld();
                    if (world.getEnvironment() != World.Environment.NETHER) continue;

                    // Contar mobs hostiles en radio 64
                    long hostileCount = world.getEntities().stream()
                            .filter(e -> e instanceof org.bukkit.entity.Monster)
                            .filter(e -> e.getLocation().distanceSquared(player.getLocation()) < 4096) // 64^2
                            .count();

                    // Si ya hay suficientes mobs, no forzar
                    if (hostileCount >= 12) continue;

                    // Intentar spawnear 2-3 mobs nativos del Nether
                    int toSpawn = 2 + random.nextInt(2);
                    for (int i = 0; i < toSpawn; i++) {
                        spawnNetherMob(player, world);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L * 30, 20L * 30); // Cada 30 segundos
    }

    private void spawnNetherMob(Player player, World world) {
        // Buscar ubicación válida para spawn (24-48 bloques de distancia)
        for (int attempt = 0; attempt < 10; attempt++) {
            int dx = (random.nextInt(48) - 24);
            int dz = (random.nextInt(48) - 24);
            if (Math.abs(dx) < 16 && Math.abs(dz) < 16) continue; // Mínimo 16 bloques

            int baseX = player.getLocation().getBlockX() + dx;
            int baseZ = player.getLocation().getBlockZ() + dz;

            // Buscar Y válido (suelo sólido + 2 bloques de aire encima)
            Location spawnLoc = findNetherSpawnY(world, baseX, baseZ);
            if (spawnLoc == null) continue;

            // Determinar qué mob spawnear
            EntityType mobType = pickNetherMob(spawnLoc);
            if (mobType == null) continue;

            world.spawnEntity(spawnLoc, mobType);
            return;
        }
    }

    private Location findNetherSpawnY(World world, int x, int z) {
        // Buscar desde Y=30 hasta Y=100 un espacio válido (suelo + 2 aire)
        for (int y = 30; y < 100; y++) {
            Block floor = world.getBlockAt(x, y, z);
            Block above1 = world.getBlockAt(x, y + 1, z);
            Block above2 = world.getBlockAt(x, y + 2, z);

            if (floor.getType().isSolid()
                    && !floor.getType().name().contains("LAVA")
                    && above1.getType() == Material.AIR
                    && above2.getType() == Material.AIR) {
                return new Location(world, x + 0.5, y + 1, z + 0.5);
            }
        }
        return null;
    }

    private EntityType pickNetherMob(Location loc) {
        // Fortress mobs tienen más prioridad: Wither Skeleton, Blaze
        // Pero también spawneamos Piglins, Hoglins, Magma Cubes, Zombified Piglins
        int roll = random.nextInt(100);

        if (roll < 25) return EntityType.WITHER_SKELETON;
        if (roll < 40) return EntityType.BLAZE;
        if (roll < 55) return EntityType.ZOMBIFIED_PIGLIN;
        if (roll < 65) return EntityType.PIGLIN;
        if (roll < 75) return EntityType.HOGLIN;
        if (roll < 85) return EntityType.MAGMA_CUBE;
        if (roll < 92) return EntityType.GHAST;
        return EntityType.ENDERMAN;
    }
}
