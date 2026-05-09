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
     * Limpia toda la arena en un área grande alrededor del centro.
     */
    public void clearArena() {
        World world = getWorld();
        if (world == null) return;
        
        // Limpiar un área de 200x200 bloques centrada en 0,0
        for (int x = -100; x <= 100; x++) {
            for (int z = -100; z <= 100; z++) {
                for (int y = 0; y <= 200; y++) {
                    if (world.getBlockAt(x, y, z).getType() != Material.AIR) {
                        world.getBlockAt(x, y, z).setType(Material.AIR, false);
                    }
                }
            }
        }
        
        // Eliminar todas las entidades
        world.getEntities().forEach(entity -> {
            if (!(entity instanceof org.bukkit.entity.Player)) {
                entity.remove();
            }
        });
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
