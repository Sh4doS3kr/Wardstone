package com.moonlight.coreprotect.koth;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.*;
import org.bukkit.generator.ChunkGenerator;

import java.util.Random;

/**
 * Gestor del mundo KOTH vacío (void).
 * Crea un mundo completamente vacío donde se construirá la arena manualmente.
 * 
 * Configuración del mundo:
 * - Sin generación de terreno (void)
 * - Sin mobs naturales
 * - Sin ciclo de día/noche (siempre día)
 * - Sin clima
 * - PvP activado
 */
public class KothWorld {

    private final CoreProtectPlugin plugin;
    private static final String KOTH_WORLD_NAME = "koth";

    public KothWorld(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Crea o carga el mundo KOTH vacío.
     * Si ya existe, simplemente lo carga.
     * 
     * @return El mundo KOTH
     */
    public World getOrCreateWorld() {
        // Intentar cargar mundo existente
        World existing = Bukkit.getWorld(KOTH_WORLD_NAME);
        if (existing != null) {
            applyWorldSettings(existing);
            return existing;
        }

        // Crear mundo vacío con generador void
        plugin.getLogger().info("[KOTH] Creando mundo vacío 'koth'...");
        WorldCreator creator = new WorldCreator(KOTH_WORLD_NAME);
        creator.generator(new VoidChunkGenerator());
        creator.type(WorldType.FLAT);
        creator.generateStructures(false);
        creator.environment(World.Environment.NORMAL);

        World kothWorld = creator.createWorld();
        if (kothWorld == null) {
            plugin.getLogger().severe("[KOTH] ¡Error al crear el mundo KOTH!");
            return null;
        }

        applyWorldSettings(kothWorld);

        // Colocar un bloque de bedrock en el centro para referencia
        kothWorld.getBlockAt(0, 64, 0).setType(Material.BEDROCK);

        plugin.getLogger().info("[KOTH] Mundo 'koth' creado exitosamente.");
        return kothWorld;
    }

    /**
     * Aplica las configuraciones necesarias al mundo KOTH.
     */
    private void applyWorldSettings(World world) {
        // PvP activado
        world.setPVP(true);

        // Sin mobs hostiles
        world.setDifficulty(Difficulty.NORMAL);
        setRule(world, "doMobSpawning", false);
        setRule(world, "mobGriefing", false);
        setRule(world, "doFireTick", false);

        // Siempre de día
        setRule(world, "doDaylightCycle", false);
        world.setTime(6000); // Mediodía

        // Sin clima
        setRule(world, "doWeatherCycle", false);
        world.setStorm(false);
        world.setThundering(false);

        // Sin drops de muerte (se manejan manualmente)
        setRule(world, "keepInventory", true);

        // Sin daño por caída al void (para evitar pérdidas accidentales)
        setRule(world, "naturalRegeneration", true);

        // Sin anuncios de avances
        setRule(world, "announceAdvancements", false);

        // Spawn en el centro
        world.setSpawnLocation(0, 65, 0);
        
        // Guardar el mundo inmediatamente para evitar pérdida
        world.save();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void setRule(World world, String rule, boolean value) {
        GameRule gameRule = GameRule.getByName(rule);
        if (gameRule != null) {
            world.setGameRule(gameRule, value);
        }
    }

    /**
     * Obtiene el mundo KOTH si existe.
     * 
     * @return El mundo o null
     */
    public World getWorld() {
        return Bukkit.getWorld(KOTH_WORLD_NAME);
    }

    /**
     * Verifica si el mundo KOTH existe.
     */
    public boolean worldExists() {
        return Bukkit.getWorld(KOTH_WORLD_NAME) != null;
    }

    /**
     * Obtiene el nombre del mundo KOTH.
     */
    public static String getWorldName() {
        return KOTH_WORLD_NAME;
    }

    /**
     * Generador de chunks vacío (void world).
     * No genera absolutamente nada - mundo completamente vacío.
     */
    public static class VoidChunkGenerator extends ChunkGenerator {

        @Override
        public boolean shouldGenerateNoise() {
            return false;
        }

        @Override
        public boolean shouldGenerateSurface() {
            return false;
        }

        @Override
        public boolean shouldGenerateBedrock() {
            return false;
        }

        @Override
        public boolean shouldGenerateCaves() {
            return false;
        }

        @Override
        public boolean shouldGenerateDecorations() {
            return false;
        }

        @Override
        public boolean shouldGenerateMobs() {
            return false;
        }

        @Override
        public boolean shouldGenerateStructures() {
            return false;
        }
    }
}
