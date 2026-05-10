package com.moonlight.coreprotect.minigames;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.GameRuleUtil;
import org.bukkit.*;
import org.bukkit.generator.ChunkGenerator;

/**
 * Gestor del mundo de minijuegos (void).
 * Crea un mundo vacío donde se construyen las arenas dinámicamente.
 */
public class MiniGameWorld {

    private final CoreProtectPlugin plugin;
    private static final String WORLD_NAME = "minigames";

    public MiniGameWorld(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    public World getOrCreateWorld() {
        World existing = Bukkit.getWorld(WORLD_NAME);
        if (existing != null) {
            applySettings(existing);
            return existing;
        }

        plugin.getLogger().info("[MiniGames] Creando mundo vacío 'minigames'...");
        WorldCreator creator = new WorldCreator(WORLD_NAME);
        creator.generator(new VoidChunkGenerator());
        creator.type(WorldType.FLAT);
        creator.generateStructures(false);
        creator.environment(World.Environment.NORMAL);

        World world = creator.createWorld();
        if (world == null) {
            plugin.getLogger().severe("[MiniGames] ¡Error al crear el mundo de minijuegos!");
            return null;
        }

        applySettings(world);
        world.getBlockAt(0, 64, 0).setType(Material.BEDROCK);
        plugin.getLogger().info("[MiniGames] Mundo 'minigames' creado exitosamente.");
        return world;
    }

    private void applySettings(World world) {
        world.setPVP(true);
        world.setDifficulty(Difficulty.NORMAL);
        GameRuleUtil.set(world, "doMobSpawning", false);
        GameRuleUtil.set(world, "mobGriefing", false);
        GameRuleUtil.set(world, "doFireTick", false);
        GameRuleUtil.set(world, "doDaylightCycle", false);
        world.setTime(6000);
        GameRuleUtil.set(world, "doWeatherCycle", false);
        world.setStorm(false);
        world.setThundering(false);
        GameRuleUtil.set(world, "keepInventory", true);
        GameRuleUtil.set(world, "naturalRegeneration", true);
        GameRuleUtil.set(world, "announceAdvancements", false);
        GameRuleUtil.set(world, "fallDamage", true);
        world.setSpawnLocation(0, 100, 0);
        world.save();
    }

    public World getWorld() {
        return Bukkit.getWorld(WORLD_NAME);
    }

    public boolean worldExists() {
        return Bukkit.getWorld(WORLD_NAME) != null;
    }

    public static String getWorldName() {
        return WORLD_NAME;
    }

    /**
     * Limpia toda la arena de forma asíncrona por batches para no causar lag.
     * Recorre TODOS los chunks cargados y elimina todos los bloques no-aire.
     */
    public void clearArena() {
        World world = getWorld();
        if (world == null) return;

        // Eliminar todas las entidades inmediatamente
        world.getEntities().forEach(entity -> {
            if (!(entity instanceof org.bukkit.entity.Player)) {
                entity.remove();
            }
        });

        // Recoger todos los chunks cargados que tengan bloques
        Chunk[] loadedChunks = world.getLoadedChunks();
        if (loadedChunks.length == 0) return;

        final int CHUNKS_PER_TICK = 16; // Limpiar 16 chunks por tick (rápido para mapas grandes)
        final java.util.List<Chunk> chunksToClean = new java.util.ArrayList<>();
        for (Chunk chunk : loadedChunks) {
            chunksToClean.add(chunk);
        }

        plugin.getLogger().info("[MiniGames] Limpieza async: " + chunksToClean.size() + " chunks por limpiar.");

        new org.bukkit.scheduler.BukkitRunnable() {
            int index = 0;

            @Override
            public void run() {
                World w = getWorld();
                if (w == null) { cancel(); return; }

                int cleaned = 0;
                while (index < chunksToClean.size() && cleaned < CHUNKS_PER_TICK) {
                    Chunk chunk = chunksToClean.get(index);
                    index++;
                    cleaned++;

                    if (!chunk.isLoaded()) continue;

                    int baseX = chunk.getX() * 16;
                    int baseZ = chunk.getZ() * 16;

                    // Solo escanear rango Y con posibles bloques (0 a 200, suficiente para cualquier arena)
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            for (int y = 0; y <= 200; y++) {
                                org.bukkit.block.Block block = chunk.getBlock(x, y, z);
                                if (block.getType() != Material.AIR) {
                                    block.setType(Material.AIR, false);
                                }
                            }
                        }
                    }

                    chunk.setForceLoaded(false);
                }

                // Eliminar entidades que hayan aparecido mientras tanto
                if (index % 20 == 0) {
                    w.getEntities().forEach(entity -> {
                        if (!(entity instanceof org.bukkit.entity.Player)) {
                            entity.remove();
                        }
                    });
                }

                if (index >= chunksToClean.size()) {
                    // Última pasada: eliminar entidades y descargar chunks
                    w.getEntities().forEach(entity -> {
                        if (!(entity instanceof org.bukkit.entity.Player)) {
                            entity.remove();
                        }
                    });
                    for (Chunk c : w.getLoadedChunks()) {
                        c.setForceLoaded(false);
                    }
                    plugin.getLogger().info("[MiniGames] Limpieza async completada: " + chunksToClean.size() + " chunks limpiados.");
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L); // 1 tick entre batches
    }

    public static class VoidChunkGenerator extends ChunkGenerator {
        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateBedrock() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }
    }
}
