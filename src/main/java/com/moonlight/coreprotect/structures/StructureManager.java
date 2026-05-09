package com.moonlight.coreprotect.structures;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sistema de generación de estructuras en el overworld.
 * Genera pozos, ruinas, santuarios y altares con cofres de loot.
 */
public class StructureManager implements Listener {

    private final CoreProtectPlugin plugin;
    private final Random random = new Random();
    private final Set<Long> generatedChunks = ConcurrentHashMap.newKeySet();
    private final Map<String, StructureData> placedStructures = new ConcurrentHashMap<>();
    private final Set<UUID> discoveredZones = ConcurrentHashMap.newKeySet(); // playerUUID:structureId combo
    private File dataFile;
    private FileConfiguration dataConfig;

    // Generation settings
    private static final double SPAWN_CHANCE = 0.004; // 0.4% per chunk = ~1 per 250 chunks
    private static final int MIN_DISTANCE_FROM_SPAWN = 150;
    private static final int MIN_DISTANCE_BETWEEN = 80;

    public enum StructureType {
        WELL("§b§l⛲ Gran Pozo Ancestral", "pozo_ancestral", Material.COBBLESTONE),
        RUINED_SHRINE("§6§l⛩ Santuario en Ruinas", "santuario_ruinas", Material.MOSSY_STONE_BRICKS),
        FORGOTTEN_ALTAR("§5§l✦ Altar Olvidado", "altar_olvidado", Material.DEEPSLATE_BRICKS),
        WANDERER_CAMP("§2§l⛺ Campamento del Errante", "campamento_errante", Material.SPRUCE_PLANKS),
        WATCHTOWER("§7§l🗼 Torre Vigía Abandonada", "torre_vigia", Material.STONE_BRICKS),
        DUNGEON("§4§l☠ Mazmorra Subterránea", "mazmorra", Material.DEEPSLATE_TILES),
        OBELISK("§d§l✧ Obelisco Arcano", "obelisco_arcano", Material.CRYING_OBSIDIAN),
        COLOSSEUM("§c§l⚔ Coliseo en Ruinas", "coliseo_ruinas", Material.SANDSTONE),
        FORTRESS("§4§l🏰 Fortaleza Oscura", "fortaleza_oscura", Material.NETHER_BRICKS),
        LIBRARY("§9§l📖 Biblioteca Antigua", "biblioteca_antigua", Material.BOOKSHELF),
        GRAVEYARD("§8§l⚰ Cementerio Maldito", "cementerio_maldito", Material.SOUL_SAND),
        PYRAMID("§e§l△ Pirámide del Desierto", "piramide_desierto", Material.SANDSTONE),
        SHIPWRECK("§3§l⚓ Navío Varado", "navio_varado", Material.DARK_OAK_PLANKS),
        TITAN_ARENA("§4§l👑 Arena del Titán", "arena_titan", Material.GILDED_BLACKSTONE);

        public final String displayName;
        public final String id;
        public final Material primary;

        StructureType(String displayName, String id, Material primary) {
            this.displayName = displayName;
            this.id = id;
            this.primary = primary;
        }
    }

    public static class StructureData {
        public final StructureType type;
        public final int x, y, z;
        public final String world;
        public final long timestamp;

        public StructureData(StructureType type, int x, int y, int z, String world) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.z = z;
            this.world = world;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public StructureManager(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        loadData();
        startDiscoveryTask();
    }

    // ==================== CHUNK GENERATION ====================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkPopulate(ChunkPopulateEvent event) {
        Chunk chunk = event.getChunk();
        World world = chunk.getWorld();

        // Only generate in overworld
        if (world.getEnvironment() != World.Environment.NORMAL) return;

        long chunkKey = chunkKey(chunk.getX(), chunk.getZ());
        if (generatedChunks.contains(chunkKey)) return;
        generatedChunks.add(chunkKey);

        // Random chance to spawn
        if (random.nextDouble() > SPAWN_CHANCE) return;

        // Schedule structure placement on main thread with delay
        new BukkitRunnable() {
            @Override
            public void run() {
                tryPlaceStructure(chunk);
            }
        }.runTaskLater(plugin, 20L + random.nextInt(40));
    }

    private void tryPlaceStructure(Chunk chunk) {
        World world = chunk.getWorld();
        int baseX = chunk.getX() * 16 + 4 + random.nextInt(8);
        int baseZ = chunk.getZ() * 16 + 4 + random.nextInt(8);

        // Check distance from spawn
        Location spawn = new Location(world, 0, 0, 0);
        Location candidate = new Location(world, baseX, 0, baseZ);
        if (candidate.distance(spawn) < MIN_DISTANCE_FROM_SPAWN) return;

        // Check not in protected zone or spawn area
        if (plugin.getProtectionManager().isSpawnProtected(new Location(world, baseX, 64, baseZ))) return;
        if (plugin.getProtectionManager().isLocationProtected(new Location(world, baseX, 64, baseZ))) return;

        // Check distance from other structures
        for (StructureData sd : placedStructures.values()) {
            if (sd.world.equals(world.getName())) {
                double dist = Math.sqrt(Math.pow(sd.x - baseX, 2) + Math.pow(sd.z - baseZ, 2));
                if (dist < MIN_DISTANCE_BETWEEN) return;
            }
        }

        // Find safe Y position
        int y = findSafeY(world, baseX, baseZ);
        if (y < 0) return;

        // Choose random structure type
        StructureType type = StructureType.values()[random.nextInt(StructureType.values().length)];

        // Place the structure
        Location loc = new Location(world, baseX, y, baseZ);
        boolean placed = false;

        switch (type) {
            case WELL: placed = buildWell(loc); break;
            case RUINED_SHRINE: placed = buildRuinedShrine(loc); break;
            case FORGOTTEN_ALTAR: placed = buildForgottenAltar(loc); break;
            case WANDERER_CAMP: placed = buildWandererCamp(loc); break;
            case WATCHTOWER: placed = buildWatchtower(loc); break;
            case DUNGEON: placed = buildDungeon(loc); break;
            case OBELISK: placed = buildObelisk(loc); break;
            case COLOSSEUM: placed = buildColosseum(loc); break;
            case FORTRESS: placed = buildFortress(loc); break;
            case LIBRARY: placed = buildLibrary(loc); break;
            case GRAVEYARD: placed = buildGraveyard(loc); break;
            case PYRAMID: placed = buildPyramid(loc); break;
            case SHIPWRECK: placed = buildShipwreck(loc); break;
            case TITAN_ARENA: placed = buildTitanArena(loc); break;
        }

        if (placed) {
            String structId = baseX + "_" + y + "_" + baseZ;
            StructureData data = new StructureData(type, baseX, y, baseZ, world.getName());
            placedStructures.put(structId, data);
            saveData();
            plugin.getLogger().info("[Structures] " + type.displayName + " §rgenerado en " +
                    world.getName() + " [" + baseX + ", " + y + ", " + baseZ + "]");
            if (plugin.getBlueMapIntegration() != null) {
                plugin.getBlueMapIntegration().addStructureMarker(structId, data);
            }
        }
    }

    private int findSafeY(World world, int x, int z) {
        // Find highest non-air, non-liquid block
        for (int y = world.getMaxHeight() - 1; y > 50; y--) {
            Block block = world.getBlockAt(x, y, z);
            Block above = world.getBlockAt(x, y + 1, z);
            Block above2 = world.getBlockAt(x, y + 2, z);

            if (block.getType().isSolid() && !block.isLiquid()
                    && above.getType().isAir() && above2.getType().isAir()) {

                // Check it's not underwater or in a cave (need sky access)
                if (!block.getType().name().contains("LEAVE")) {
                    // Verify flat-ish area (check 4 corners within 3 blocks)
                    boolean flat = true;
                    for (int dx = -2; dx <= 2; dx += 4) {
                        for (int dz = -2; dz <= 2; dz += 4) {
                            Block corner = world.getBlockAt(x + dx, y, z + dz);
                            Block cornerUp = world.getBlockAt(x + dx, y + 1, z + dz);
                            if (!corner.getType().isSolid() || corner.isLiquid() || cornerUp.isLiquid()) {
                                flat = false;
                                break;
                            }
                        }
                        if (!flat) break;
                    }
                    if (flat) return y;
                }
            }
        }
        return -1;
    }

    // ==================== STRUCTURE BUILDERS ====================

    private boolean buildWell(Location loc) {
        World w = loc.getWorld();
        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();

        // Clear area (giant well — radius 7)
        for (int dx = -7; dx <= 7; dx++)
            for (int dz = -7; dz <= 7; dz++)
                for (int dy = 1; dy <= 10; dy++)
                    setBlock(w, bx + dx, by + dy, bz + dz, Material.AIR);

        // Large circular base platform (radius 7)
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                if (dx * dx + dz * dz <= 50) {
                    setBlock(w, bx + dx, by, bz + dz,
                            random.nextFloat() < 0.3 ? Material.MOSSY_COBBLESTONE : Material.COBBLESTONE);
                }
            }
        }

        // Inner ring of stone bricks (radius 5)
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                int dist = dx * dx + dz * dz;
                if (dist <= 26 && dist > 16) {
                    setBlock(w, bx + dx, by, bz + dz,
                            random.nextFloat() < 0.4 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS);
                }
            }
        }

        // Well walls — 3 blocks high, radius 3
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int dist = dx * dx + dz * dz;
                if (dist <= 10 && dist > 4) {
                    for (int dy = 1; dy <= 3; dy++) {
                        setBlock(w, bx + dx, by + dy, bz + dz,
                                random.nextFloat() < 0.3 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS);
                    }
                }
            }
        }

        // Water pool inside (radius 2, depth 3)
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx * dx + dz * dz <= 4) {
                    for (int dy = -2; dy <= 0; dy++) {
                        setBlock(w, bx + dx, by + dy, bz + dz, Material.WATER);
                    }
                }
            }
        }

        // 4 tall pillars at corners
        int[][] corners = {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}};
        for (int[] c : corners) {
            for (int dy = 1; dy <= 6; dy++)
                setBlock(w, bx + c[0], by + dy, bz + c[1], Material.STONE_BRICK_WALL);
            setBlock(w, bx + c[0], by + 7, bz + c[1], Material.STONE_BRICK_SLAB);
        }

        // Roof beams connecting pillars
        for (int dx = -3; dx <= 3; dx++) {
            setBlock(w, bx + dx, by + 7, bz - 3, Material.SPRUCE_SLAB);
            setBlock(w, bx + dx, by + 7, bz + 3, Material.SPRUCE_SLAB);
        }
        for (int dz = -3; dz <= 3; dz++) {
            setBlock(w, bx - 3, by + 7, bz + dz, Material.SPRUCE_SLAB);
            setBlock(w, bx + 3, by + 7, bz + dz, Material.SPRUCE_SLAB);
        }

        // Center roof
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                setBlock(w, bx + dx, by + 7, bz + dz, Material.SPRUCE_SLAB);

        // Hanging chains and lanterns
        for (int[] c : corners) {
            setBlock(w, bx + c[0], by + 6, bz + c[1], Material.CHAIN);
            setBlock(w, bx + c[0], by + 5, bz + c[1], Material.LANTERN);
        }

        // Decorative elements around base
        setBlock(w, bx + 5, by + 1, bz, Material.FLOWER_POT);
        setBlock(w, bx - 5, by + 1, bz, Material.FERN);
        setBlock(w, bx, by + 1, bz + 5, Material.DANDELION);
        setBlock(w, bx, by + 1, bz - 5, Material.POPPY);

        // Benches (stairs) around well
        setBlock(w, bx + 5, by + 1, bz + 1, Material.STONE_BRICK_STAIRS);
        setBlock(w, bx + 5, by + 1, bz - 1, Material.STONE_BRICK_STAIRS);
        setBlock(w, bx - 5, by + 1, bz + 1, Material.STONE_BRICK_STAIRS);
        setBlock(w, bx - 5, by + 1, bz - 1, Material.STONE_BRICK_STAIRS);

        // Loot chest
        placeChest(w, bx + 4, by + 1, bz, StructureType.WELL);

        return true;
    }

    private boolean buildRuinedShrine(Location loc) {
        World w = loc.getWorld();
        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();

        // Clear area
        for (int dx = -6; dx <= 6; dx++)
            for (int dz = -6; dz <= 6; dz++)
                for (int dy = 1; dy <= 8; dy++)
                    setBlock(w, bx + dx, by + dy, bz + dz, Material.AIR);

        // Circular base platform (radius 6)
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                if (dx * dx + dz * dz <= 38) {
                    Material mat = random.nextFloat() < 0.3 ? Material.MOSSY_STONE_BRICKS :
                            random.nextFloat() < 0.5 ? Material.CRACKED_STONE_BRICKS : Material.STONE_BRICKS;
                    setBlock(w, bx + dx, by, bz + dz, mat);
                }
            }
        }

        // 8 pillars in a circle (partially ruined)
        double angleStep = Math.PI / 4;
        for (int p = 0; p < 8; p++) {
            int px = (int) (Math.cos(angleStep * p) * 5);
            int pz = (int) (Math.sin(angleStep * p) * 5);
            int pillarHeight = 3 + random.nextInt(4); // 3-6 blocks tall
            for (int dy = 1; dy <= pillarHeight; dy++) {
                setBlock(w, bx + px, by + dy, bz + pz,
                        random.nextFloat() < 0.2 ? Material.CRACKED_STONE_BRICKS : Material.STONE_BRICKS);
            }
        }

        // Center altar pedestal
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                setBlock(w, bx + dx, by + 1, bz + dz, Material.CHISELED_STONE_BRICKS);
        setBlock(w, bx, by + 2, bz, Material.CHISELED_STONE_BRICKS);
        setBlock(w, bx, by + 3, bz, Material.SOUL_LANTERN);

        // Scattered moss and fern
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                if (random.nextFloat() < 0.12) {
                    Block b = w.getBlockAt(bx + dx, by + 1, bz + dz);
                    if (b.getType().isAir()) {
                        setBlock(w, bx + dx, by + 1, bz + dz,
                                random.nextBoolean() ? Material.MOSS_CARPET : Material.FERN);
                    }
                }
            }
        }

        // Steps leading up
        setBlock(w, bx, by + 1, bz - 4, Material.STONE_BRICK_STAIRS);
        setBlock(w, bx - 1, by + 1, bz - 4, Material.STONE_BRICK_STAIRS);
        setBlock(w, bx + 1, by + 1, bz - 4, Material.STONE_BRICK_STAIRS);

        // Loot chest
        placeChest(w, bx + 2, by + 1, bz + 1, StructureType.RUINED_SHRINE);

        return true;
    }

    private boolean buildForgottenAltar(Location loc) {
        World w = loc.getWorld();
        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();

        // Clear area
        for (int dx = -5; dx <= 5; dx++)
            for (int dz = -5; dz <= 5; dz++)
                for (int dy = 1; dy <= 6; dy++)
                    setBlock(w, bx + dx, by + dy, bz + dz, Material.AIR);

        // Deepslate cross base (larger)
        for (int d = -5; d <= 5; d++) {
            setBlock(w, bx + d, by, bz, Material.DEEPSLATE_BRICKS);
            setBlock(w, bx, by, bz + d, Material.DEEPSLATE_BRICKS);
            if (Math.abs(d) <= 3) {
                setBlock(w, bx + d, by, bz + 1, Material.DEEPSLATE_BRICKS);
                setBlock(w, bx + d, by, bz - 1, Material.DEEPSLATE_BRICKS);
                setBlock(w, bx + 1, by, bz + d, Material.DEEPSLATE_BRICKS);
                setBlock(w, bx - 1, by, bz + d, Material.DEEPSLATE_BRICKS);
            }
        }

        // Center 3x3 polished
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                setBlock(w, bx + dx, by, bz + dz, Material.POLISHED_DEEPSLATE);

        // Center pedestal (3 blocks high)
        setBlock(w, bx, by + 1, bz, Material.POLISHED_DEEPSLATE);
        setBlock(w, bx, by + 2, bz, Material.POLISHED_DEEPSLATE);
        setBlock(w, bx, by + 3, bz, Material.ENCHANTING_TABLE);

        // 4 tall deepslate pillars
        int[][] pp = {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}};
        for (int[] p : pp) {
            for (int dy = 1; dy <= 5; dy++)
                setBlock(w, bx + p[0], by + dy, bz + p[1], Material.DEEPSLATE_BRICK_WALL);
            setBlock(w, bx + p[0], by + 5, bz + p[1], Material.SOUL_LANTERN);
        }

        // 4 candles on cardinal directions
        setBlock(w, bx - 4, by + 1, bz, Material.CANDLE);
        setBlock(w, bx + 4, by + 1, bz, Material.CANDLE);
        setBlock(w, bx, by + 1, bz - 4, Material.CANDLE);
        setBlock(w, bx, by + 1, bz + 4, Material.CANDLE);

        // Loot chest
        placeChest(w, bx + 1, by + 1, bz + 1, StructureType.FORGOTTEN_ALTAR);

        return true;
    }

    private boolean buildWandererCamp(Location loc) {
        World w = loc.getWorld();
        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();

        // Clear area
        for (int dx = -5; dx <= 5; dx++)
            for (int dz = -5; dz <= 5; dz++)
                for (int dy = 1; dy <= 5; dy++)
                    setBlock(w, bx + dx, by + dy, bz + dz, Material.AIR);

        // Dirt floor (radius 5)
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                if (dx * dx + dz * dz <= 26) {
                    setBlock(w, bx + dx, by, bz + dz,
                            random.nextFloat() < 0.4 ? Material.COARSE_DIRT : Material.DIRT_PATH);
                }
            }
        }

        // Main tent (larger)
        for (int dy = 1; dy <= 3; dy++) {
            setBlock(w, bx - 3, by + dy, bz, Material.SPRUCE_LOG);
            setBlock(w, bx + 3, by + dy, bz, Material.SPRUCE_LOG);
        }
        for (int dx = -3; dx <= 3; dx++) {
            setBlock(w, bx + dx, by + 4, bz, Material.SPRUCE_SLAB);
            setBlock(w, bx + dx, by + 4, bz + 1, Material.SPRUCE_SLAB);
            setBlock(w, bx + dx, by + 4, bz - 1, Material.SPRUCE_SLAB);
        }
        for (int dz = -2; dz <= 2; dz++) {
            setBlock(w, bx, by + 4, bz + dz, Material.SPRUCE_SLAB);
        }

        // Carpet inside
        Material[] carpets = {Material.RED_CARPET, Material.ORANGE_CARPET, Material.YELLOW_CARPET, Material.BROWN_CARPET};
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -1; dz <= 1; dz++)
                setBlock(w, bx + dx, by + 1, bz + dz, carpets[random.nextInt(carpets.length)]);

        // Campfire
        setBlock(w, bx, by + 1, bz - 3, Material.CAMPFIRE);

        // Crafting & utility
        setBlock(w, bx + 3, by + 1, bz + 2, Material.CRAFTING_TABLE);
        setBlock(w, bx - 3, by + 1, bz + 2, Material.BARREL);
        setBlock(w, bx + 4, by + 1, bz, Material.SMOKER);

        // Hay bales
        setBlock(w, bx - 4, by + 1, bz - 1, Material.HAY_BLOCK);
        setBlock(w, bx - 4, by + 2, bz - 1, Material.HAY_BLOCK);

        // Loot chest
        placeChest(w, bx + 2, by + 1, bz + 3, StructureType.WANDERER_CAMP);

        return true;
    }

    // ==================== NEW STRUCTURES ====================

    private boolean buildWatchtower(Location loc) {
        World w = loc.getWorld();
        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();

        // Clear area
        for (int dx = -5; dx <= 5; dx++)
            for (int dz = -5; dz <= 5; dz++)
                for (int dy = 1; dy <= 16; dy++)
                    setBlock(w, bx + dx, by + dy, bz + dz, Material.AIR);

        // Stone base platform
        for (int dx = -4; dx <= 4; dx++)
            for (int dz = -4; dz <= 4; dz++)
                if (dx * dx + dz * dz <= 18)
                    setBlock(w, bx + dx, by, bz + dz, random.nextFloat() < 0.3 ? Material.MOSSY_COBBLESTONE : Material.COBBLESTONE);

        // Tower walls (3x3 hollow, 12 high)
        for (int dy = 1; dy <= 12; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                        // Skip some blocks for ruined look
                        if (dy > 8 && random.nextFloat() < 0.3) continue;
                        Material mat = random.nextFloat() < 0.2 ? Material.CRACKED_STONE_BRICKS :
                                random.nextFloat() < 0.3 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS;
                        setBlock(w, bx + dx, by + dy, bz + dz, mat);
                    }
                }
            }
            // Door opening
            setBlock(w, bx, by + 1, bz + 2, Material.AIR);
            setBlock(w, bx, by + 2, bz + 2, Material.AIR);
        }

        // Interior floors (every 4 blocks)
        for (int dy : new int[]{4, 8}) {
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++)
                    setBlock(w, bx + dx, by + dy, bz + dz, Material.SPRUCE_PLANKS);
        }

        // Ladder up through floors
        for (int dy = 1; dy <= 12; dy++)
            setBlock(w, bx + 1, by + dy, bz - 1, Material.LADDER);

        // Top platform (wider — 5x5)
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++)
                setBlock(w, bx + dx, by + 12, bz + dz, Material.SPRUCE_PLANKS);

        // Battlements
        for (int dx = -3; dx <= 3; dx++) {
            if (dx % 2 == 0) {
                setBlock(w, bx + dx, by + 13, bz - 3, Material.STONE_BRICK_WALL);
                setBlock(w, bx + dx, by + 13, bz + 3, Material.STONE_BRICK_WALL);
            }
        }
        for (int dz = -3; dz <= 3; dz++) {
            if (dz % 2 == 0) {
                setBlock(w, bx - 3, by + 13, bz + dz, Material.STONE_BRICK_WALL);
                setBlock(w, bx + 3, by + 13, bz + dz, Material.STONE_BRICK_WALL);
            }
        }

        // Lanterns at top
        setBlock(w, bx - 2, by + 13, bz - 2, Material.LANTERN);
        setBlock(w, bx + 2, by + 13, bz + 2, Material.LANTERN);

        // Loot chests — one at base, one at top
        placeChest(w, bx - 1, by + 1, bz, StructureType.WATCHTOWER);
        placeChest(w, bx, by + 13, bz, StructureType.WATCHTOWER);

        return true;
    }

    private boolean buildDungeon(Location loc) {
        World w = loc.getWorld();
        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();

        // Underground dungeon — dig down
        int baseY = by - 5;

        // Entrance (trapdoor + staircase down)
        setBlock(w, bx, by + 1, bz, Material.STONE_BRICK_STAIRS);
        setBlock(w, bx, by, bz + 1, Material.STONE_BRICK_STAIRS);
        for (int dy = 0; dy >= -5; dy--) {
            setBlock(w, bx, by + dy, bz - dy, Material.AIR);
            setBlock(w, bx, by + dy - 1, bz - dy, Material.STONE_BRICK_STAIRS);
            setBlock(w, bx - 1, by + dy, bz - dy, Material.DEEPSLATE_BRICKS);
            setBlock(w, bx + 1, by + dy, bz - dy, Material.DEEPSLATE_BRICKS);
        }

        // Main chamber (hollow 9x5x9)
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                // Floor
                setBlock(w, bx + dx, baseY, bz + dz, Material.DEEPSLATE_TILES);
                // Ceiling
                setBlock(w, bx + dx, baseY + 5, bz + dz, Material.DEEPSLATE_BRICKS);
                // Walls
                if (Math.abs(dx) == 4 || Math.abs(dz) == 4) {
                    for (int dy = 1; dy <= 4; dy++)
                        setBlock(w, bx + dx, baseY + dy, bz + dz,
                                random.nextFloat() < 0.2 ? Material.CRACKED_DEEPSLATE_TILES : Material.DEEPSLATE_BRICKS);
                } else {
                    // Air inside
                    for (int dy = 1; dy <= 4; dy++)
                        setBlock(w, bx + dx, baseY + dy, bz + dz, Material.AIR);
                }
            }
        }

        // Pillars inside
        int[][] pillars = {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}};
        for (int[] p : pillars) {
            for (int dy = 1; dy <= 4; dy++)
                setBlock(w, bx + p[0], baseY + dy, bz + p[1], Material.DEEPSLATE_BRICK_WALL);
        }

        // Mob spawner area (cobwebs)
        setBlock(w, bx, baseY + 4, bz, Material.COBWEB);
        setBlock(w, bx + 1, baseY + 4, bz + 1, Material.COBWEB);
        setBlock(w, bx - 1, baseY + 4, bz - 1, Material.COBWEB);

        // Soul lanterns
        setBlock(w, bx - 3, baseY + 3, bz - 3, Material.SOUL_LANTERN);
        setBlock(w, bx + 3, baseY + 3, bz + 3, Material.SOUL_LANTERN);
        setBlock(w, bx + 3, baseY + 3, bz - 3, Material.SOUL_LANTERN);
        setBlock(w, bx - 3, baseY + 3, bz + 3, Material.SOUL_LANTERN);

        // Loot chests (2 in dungeon — good loot!)
        placeChest(w, bx - 3, baseY + 1, bz, StructureType.DUNGEON);
        placeChest(w, bx + 3, baseY + 1, bz, StructureType.DUNGEON);

        return true;
    }

    private boolean buildObelisk(Location loc) {
        World w = loc.getWorld();
        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();

        // Clear area
        for (int dx = -4; dx <= 4; dx++)
            for (int dz = -4; dz <= 4; dz++)
                for (int dy = 1; dy <= 15; dy++)
                    setBlock(w, bx + dx, by + dy, bz + dz, Material.AIR);

        // Base platform (obsidian circle)
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (dx * dx + dz * dz <= 18) {
                    setBlock(w, bx + dx, by, bz + dz,
                            random.nextFloat() < 0.3 ? Material.CRYING_OBSIDIAN : Material.OBSIDIAN);
                }
            }
        }

        // Obelisk tower (tapered — 3x3 base, 1x1 top, 14 high)
        for (int dy = 1; dy <= 14; dy++) {
            int width;
            if (dy <= 3) width = 2;
            else if (dy <= 7) width = 1;
            else width = 0;

            for (int dx = -width; dx <= width; dx++) {
                for (int dz = -width; dz <= width; dz++) {
                    Material mat;
                    if (dy <= 3) mat = random.nextFloat() < 0.2 ? Material.CRYING_OBSIDIAN : Material.OBSIDIAN;
                    else if (dy <= 10) mat = Material.DEEPSLATE_BRICKS;
                    else mat = Material.BLACKSTONE;
                    setBlock(w, bx + dx, by + dy, bz + dz, mat);
                }
            }
        }

        // Glowing top
        setBlock(w, bx, by + 14, bz, Material.AMETHYST_BLOCK);
        setBlock(w, bx, by + 15, bz, Material.END_ROD);

        // Floating amethyst clusters
        setBlock(w, bx + 1, by + 12, bz, Material.AMETHYST_CLUSTER);
        setBlock(w, bx - 1, by + 12, bz, Material.AMETHYST_CLUSTER);
        setBlock(w, bx, by + 12, bz + 1, Material.AMETHYST_CLUSTER);
        setBlock(w, bx, by + 12, bz - 1, Material.AMETHYST_CLUSTER);

        // Rune circles on the base
        setBlock(w, bx + 3, by + 1, bz, Material.CANDLE);
        setBlock(w, bx - 3, by + 1, bz, Material.CANDLE);
        setBlock(w, bx, by + 1, bz + 3, Material.CANDLE);
        setBlock(w, bx, by + 1, bz - 3, Material.CANDLE);

        // Loot chest
        placeChest(w, bx + 2, by + 1, bz, StructureType.OBELISK);

        return true;
    }

    private boolean buildColosseum(Location loc) {
        World w = loc.getWorld();
        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();

        // Clear area (giant — radius 10)
        for (int dx = -10; dx <= 10; dx++)
            for (int dz = -10; dz <= 10; dz++)
                for (int dy = 1; dy <= 10; dy++)
                    setBlock(w, bx + dx, by + dy, bz + dz, Material.AIR);

        // Arena floor (sandstone circle, radius 8)
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                if (dx * dx + dz * dz <= 65) {
                    setBlock(w, bx + dx, by, bz + dz,
                            random.nextFloat() < 0.3 ? Material.RED_SANDSTONE : Material.SANDSTONE);
                }
            }
        }

        // Walls (ring at radius 7-9, 5 blocks high, partially ruined)
        for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
                int dist = dx * dx + dz * dz;
                if (dist <= 82 && dist > 50) {
                    int maxH = 4 + random.nextInt(3); // 4-6 (ruined top)
                    for (int dy = 1; dy <= maxH; dy++) {
                        if (dy > 3 && random.nextFloat() < 0.3) continue; // Ruined
                        Material mat = random.nextFloat() < 0.15 ? Material.CRACKED_STONE_BRICKS :
                                random.nextFloat() < 0.3 ? Material.SANDSTONE : Material.CUT_SANDSTONE;
                        setBlock(w, bx + dx, by + dy, bz + dz, mat);
                    }
                }
            }
        }

        // 4 grand entrance arches (N/S/E/W openings)
        int[][] entrances = {{0, 8}, {0, -8}, {8, 0}, {-8, 0}};
        for (int[] e : entrances) {
            for (int dy = 1; dy <= 4; dy++) {
                setBlock(w, bx + e[0], by + dy, bz + e[1], Material.AIR);
                int ox = e[0] == 0 ? 1 : 0;
                int oz = e[1] == 0 ? 1 : 0;
                setBlock(w, bx + e[0] + ox, by + dy, bz + e[1] + oz, Material.AIR);
                setBlock(w, bx + e[0] - ox, by + dy, bz + e[1] - oz, Material.AIR);
            }
        }

        // Center pedestal
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                setBlock(w, bx + dx, by + 1, bz + dz, Material.CHISELED_SANDSTONE);
        setBlock(w, bx, by + 2, bz, Material.CHISELED_SANDSTONE);

        // Torches around arena
        for (int angle = 0; angle < 8; angle++) {
            int tx = (int) (Math.cos(angle * Math.PI / 4) * 6);
            int tz = (int) (Math.sin(angle * Math.PI / 4) * 6);
            setBlock(w, bx + tx, by + 1, bz + tz, Material.SOUL_TORCH);
        }

        // Spectator seating (stairs in tiers)
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                int dist = dx * dx + dz * dz;
                if (dist <= 50 && dist > 36) {
                    setBlock(w, bx + dx, by + 2, bz + dz, Material.SANDSTONE_STAIRS);
                }
            }
        }

        // Loot chests (3 chests — big arena!)
        placeChest(w, bx + 1, by + 2, bz, StructureType.COLOSSEUM);
        placeChest(w, bx + 6, by + 1, bz, StructureType.COLOSSEUM);
        placeChest(w, bx - 6, by + 1, bz, StructureType.COLOSSEUM);

        return true;
    }

    // ==================== MORE STRUCTURES ====================

    private boolean buildFortress(Location loc) {
        World w = loc.getWorld();
        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();

        // Clear area (radius 8)
        for (int dx = -8; dx <= 8; dx++)
            for (int dz = -8; dz <= 8; dz++)
                for (int dy = 1; dy <= 12; dy++)
                    setBlock(w, bx + dx, by + dy, bz + dz, Material.AIR);

        // Nether brick base (radius 7)
        for (int dx = -7; dx <= 7; dx++)
            for (int dz = -7; dz <= 7; dz++)
                if (dx * dx + dz * dz <= 50)
                    setBlock(w, bx + dx, by, bz + dz, random.nextFloat() < 0.3 ? Material.CRACKED_NETHER_BRICKS : Material.NETHER_BRICKS);

        // Walls (7x7 hollow, 8 high)
        for (int dy = 1; dy <= 8; dy++) {
            for (int dx = -6; dx <= 6; dx++) {
                for (int dz = -6; dz <= 6; dz++) {
                    if (Math.abs(dx) >= 5 || Math.abs(dz) >= 5) {
                        if (Math.abs(dx) <= 6 && Math.abs(dz) <= 6) {
                            if (dy > 5 && random.nextFloat() < 0.25) continue;
                            setBlock(w, bx + dx, by + dy, bz + dz,
                                    random.nextFloat() < 0.2 ? Material.CRACKED_NETHER_BRICKS : Material.NETHER_BRICKS);
                        }
                    }
                }
            }
        }

        // Gate opening
        for (int dy = 1; dy <= 4; dy++)
            for (int dx = -1; dx <= 1; dx++)
                setBlock(w, bx + dx, by + dy, bz + 6, Material.AIR);

        // 4 Corner towers (10 high)
        int[][] towers = {{-6, -6}, {6, -6}, {-6, 6}, {6, 6}};
        for (int[] t : towers) {
            for (int dy = 1; dy <= 10; dy++) {
                setBlock(w, bx + t[0], by + dy, bz + t[1], Material.NETHER_BRICKS);
                setBlock(w, bx + t[0] - 1, by + dy, bz + t[1], Material.NETHER_BRICKS);
                setBlock(w, bx + t[0], by + dy, bz + t[1] - 1, Material.NETHER_BRICKS);
                setBlock(w, bx + t[0] + 1, by + dy, bz + t[1], Material.NETHER_BRICKS);
                setBlock(w, bx + t[0], by + dy, bz + t[1] + 1, Material.NETHER_BRICKS);
            }
            setBlock(w, bx + t[0], by + 11, bz + t[1], Material.NETHER_BRICK_FENCE);
            setBlock(w, bx + t[0], by + 10, bz + t[1], Material.SOUL_LANTERN);
        }

        // Inner floor
        for (int dx = -4; dx <= 4; dx++)
            for (int dz = -4; dz <= 4; dz++)
                setBlock(w, bx + dx, by, bz + dz, Material.RED_NETHER_BRICKS);

        // Center altar
        setBlock(w, bx, by + 1, bz, Material.NETHER_BRICK_FENCE);
        setBlock(w, bx, by + 2, bz, Material.SOUL_LANTERN);

        // Loot (2 chests)
        placeChest(w, bx - 3, by + 1, bz, StructureType.FORTRESS);
        placeChest(w, bx + 3, by + 1, bz, StructureType.FORTRESS);
        return true;
    }

    private boolean buildLibrary(Location loc) {
        World w = loc.getWorld();
        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();

        // Clear (radius 6)
        for (int dx = -6; dx <= 6; dx++)
            for (int dz = -6; dz <= 6; dz++)
                for (int dy = 1; dy <= 8; dy++)
                    setBlock(w, bx + dx, by + dy, bz + dz, Material.AIR);

        // Stone floor
        for (int dx = -5; dx <= 5; dx++)
            for (int dz = -5; dz <= 5; dz++)
                setBlock(w, bx + dx, by, bz + dz, random.nextFloat() < 0.3 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS);

        // Walls (5x5 hollow, 6 high)
        for (int dy = 1; dy <= 6; dy++)
            for (int dx = -5; dx <= 5; dx++)
                for (int dz = -5; dz <= 5; dz++)
                    if (Math.abs(dx) == 5 || Math.abs(dz) == 5) {
                        if (dy > 4 && random.nextFloat() < 0.2) continue;
                        setBlock(w, bx + dx, by + dy, bz + dz,
                                random.nextFloat() < 0.3 ? Material.CRACKED_STONE_BRICKS : Material.STONE_BRICKS);
                    }

        // Bookshelves along walls
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = 1; dy <= 3; dy++) {
                if (random.nextFloat() < 0.7) setBlock(w, bx + dx, by + dy, bz - 4, Material.BOOKSHELF);
                if (random.nextFloat() < 0.7) setBlock(w, bx + dx, by + dy, bz + 4, Material.BOOKSHELF);
            }
        }
        for (int dz = -4; dz <= 4; dz++) {
            for (int dy = 1; dy <= 3; dy++) {
                if (random.nextFloat() < 0.7) setBlock(w, bx - 4, by + dy, bz + dz, Material.BOOKSHELF);
            }
        }

        // Lectern center
        setBlock(w, bx, by + 1, bz, Material.LECTERN);

        // Tables
        setBlock(w, bx - 2, by + 1, bz - 1, Material.SPRUCE_SLAB);
        setBlock(w, bx + 2, by + 1, bz + 1, Material.SPRUCE_SLAB);

        // Lanterns
        setBlock(w, bx - 2, by + 4, bz - 2, Material.LANTERN);
        setBlock(w, bx + 2, by + 4, bz + 2, Material.LANTERN);

        // Door
        for (int dy = 1; dy <= 3; dy++) setBlock(w, bx + 5, by + dy, bz, Material.AIR);

        // Roof
        for (int dx = -5; dx <= 5; dx++)
            for (int dz = -5; dz <= 5; dz++)
                setBlock(w, bx + dx, by + 7, bz + dz, Material.SPRUCE_SLAB);

        placeChest(w, bx + 3, by + 1, bz - 3, StructureType.LIBRARY);
        placeChest(w, bx - 3, by + 1, bz + 3, StructureType.LIBRARY);
        return true;
    }

    private boolean buildGraveyard(Location loc) {
        World w = loc.getWorld();
        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();

        // Clear (radius 8)
        for (int dx = -8; dx <= 8; dx++)
            for (int dz = -8; dz <= 8; dz++)
                for (int dy = 1; dy <= 5; dy++)
                    setBlock(w, bx + dx, by + dy, bz + dz, Material.AIR);

        // Ground
        for (int dx = -8; dx <= 8; dx++)
            for (int dz = -8; dz <= 8; dz++)
                if (dx * dx + dz * dz <= 65)
                    setBlock(w, bx + dx, by, bz + dz, random.nextFloat() < 0.4 ? Material.SOUL_SAND : Material.PODZOL);

        // Gravestones in rows
        for (int row = -6; row <= 6; row += 3) {
            for (int col = -5; col <= 5; col += 3) {
                if (random.nextFloat() < 0.7) {
                    setBlock(w, bx + col, by + 1, bz + row, Material.STONE_BRICK_WALL);
                    if (random.nextFloat() < 0.5)
                        setBlock(w, bx + col, by + 2, bz + row, Material.STONE_BRICK_SLAB);
                }
            }
        }

        // Dead trees (logs)
        int[][] trees = {{-5, -5}, {5, 4}, {-4, 6}};
        for (int[] t : trees) {
            int h = 3 + random.nextInt(3);
            for (int dy = 1; dy <= h; dy++)
                setBlock(w, bx + t[0], by + dy, bz + t[1], Material.STRIPPED_DARK_OAK_LOG);
        }

        // Cobwebs scattered
        for (int i = 0; i < 8; i++) {
            int cx = bx - 6 + random.nextInt(13);
            int cz = bz - 6 + random.nextInt(13);
            setBlock(w, cx, by + 1, cz, Material.COBWEB);
        }

        // Soul lanterns
        setBlock(w, bx, by + 1, bz, Material.SOUL_LANTERN);
        setBlock(w, bx + 4, by + 1, bz + 4, Material.SOUL_TORCH);
        setBlock(w, bx - 4, by + 1, bz - 4, Material.SOUL_TORCH);

        placeChest(w, bx + 1, by + 1, bz + 1, StructureType.GRAVEYARD);
        placeChest(w, bx - 5, by + 1, bz, StructureType.GRAVEYARD);
        return true;
    }

    private boolean buildPyramid(Location loc) {
        World w = loc.getWorld();
        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();

        // Clear (radius 12)
        for (int dx = -12; dx <= 12; dx++)
            for (int dz = -12; dz <= 12; dz++)
                for (int dy = 1; dy <= 14; dy++)
                    setBlock(w, bx + dx, by + dy, bz + dz, Material.AIR);

        // Pyramid — 12 layers, each smaller
        for (int layer = 0; layer <= 11; layer++) {
            int r = 11 - layer;
            Material mat = random.nextFloat() < 0.15 ? Material.CUT_SANDSTONE :
                    random.nextFloat() < 0.1 ? Material.CHISELED_SANDSTONE : Material.SANDSTONE;
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    // Only outer ring for layers > 0 to save blocks (hollow inside)
                    if (layer > 0 && layer < 10 && Math.abs(dx) < r && Math.abs(dz) < r) continue;
                    setBlock(w, bx + dx, by + layer + 1, bz + dz,
                            random.nextFloat() < 0.15 ? Material.CUT_SANDSTONE : Material.SANDSTONE);
                }
        }

        // Entrance (south side)
        for (int dy = 1; dy <= 4; dy++)
            for (int dx = -1; dx <= 1; dx++)
                setBlock(w, bx + dx, by + dy, bz + 11, Material.AIR);

        // Inner chamber (5x5x4)
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++)
                for (int dy = 1; dy <= 4; dy++)
                    setBlock(w, bx + dx, by + dy, bz + dz, Material.AIR);

        // Floor
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++)
                setBlock(w, bx + dx, by, bz + dz, Material.RED_SANDSTONE);

        // Passage from entrance to chamber
        for (int dz = 3; dz <= 11; dz++)
            for (int dy = 1; dy <= 3; dy++)
                for (int dx = -1; dx <= 1; dx++)
                    setBlock(w, bx + dx, by + dy, bz + dz, Material.AIR);

        // Gold accents inside
        setBlock(w, bx, by + 1, bz, Material.GOLD_BLOCK);
        setBlock(w, bx - 2, by + 1, bz - 2, Material.SOUL_LANTERN);
        setBlock(w, bx + 2, by + 1, bz + 2, Material.SOUL_LANTERN);

        // Loot (3 chests)
        placeChest(w, bx - 2, by + 1, bz, StructureType.PYRAMID);
        placeChest(w, bx + 2, by + 1, bz, StructureType.PYRAMID);
        placeChest(w, bx, by + 1, bz - 2, StructureType.PYRAMID);
        return true;
    }

    private boolean buildShipwreck(Location loc) {
        World w = loc.getWorld();
        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();

        // Clear (radius 8)
        for (int dx = -8; dx <= 8; dx++)
            for (int dz = -4; dz <= 4; dz++)
                for (int dy = 1; dy <= 8; dy++)
                    setBlock(w, bx + dx, by + dy, bz + dz, Material.AIR);

        // Hull (elongated, 15 long x 7 wide, 5 tall)
        for (int dx = -7; dx <= 7; dx++) {
            int halfWidth = (int) (3.5 - Math.abs(dx) * 0.3);
            if (halfWidth < 1) halfWidth = 1;
            for (int dz = -halfWidth; dz <= halfWidth; dz++) {
                // Floor
                setBlock(w, bx + dx, by, bz + dz,
                        random.nextFloat() < 0.3 ? Material.SPRUCE_PLANKS : Material.DARK_OAK_PLANKS);
                // Walls
                if (Math.abs(dz) == halfWidth) {
                    for (int dy = 1; dy <= 3; dy++)
                        if (random.nextFloat() < 0.8)
                            setBlock(w, bx + dx, by + dy, bz + dz,
                                    random.nextFloat() < 0.3 ? Material.SPRUCE_PLANKS : Material.DARK_OAK_PLANKS);
                }
            }
        }

        // Mast
        for (int dy = 1; dy <= 7; dy++)
            setBlock(w, bx, by + dy, bz, Material.SPRUCE_LOG);
        // Sail (wool)
        for (int dy = 4; dy <= 6; dy++)
            for (int dz = -2; dz <= 2; dz++)
                if (random.nextFloat() < 0.7)
                    setBlock(w, bx, by + dy, bz + dz, Material.WHITE_WOOL);

        // Barrel and crate
        setBlock(w, bx + 4, by + 1, bz, Material.BARREL);
        setBlock(w, bx - 4, by + 1, bz, Material.BARREL);
        setBlock(w, bx + 3, by + 1, bz + 1, Material.CRAFTING_TABLE);

        placeChest(w, bx + 5, by + 1, bz, StructureType.SHIPWRECK);
        placeChest(w, bx - 5, by + 1, bz, StructureType.SHIPWRECK);
        return true;
    }

    // === TITAN ARENA — GIANT (radius ~50) — 2% spawn chance ===
    private boolean buildTitanArena(Location loc) {
        World w = loc.getWorld();
        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();

        int R = 25; // half-size of the arena

        // Floor — giant circular arena
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                int dist = dx * dx + dz * dz;
                if (dist <= R * R) {
                    // Outer ring (gilded blackstone)
                    if (dist > (R - 3) * (R - 3)) {
                        setBlock(w, bx + dx, by, bz + dz,
                                random.nextFloat() < 0.3 ? Material.GILDED_BLACKSTONE : Material.POLISHED_BLACKSTONE_BRICKS);
                    }
                    // Inner arena (red/sandstone mix)
                    else {
                        setBlock(w, bx + dx, by, bz + dz,
                                random.nextFloat() < 0.4 ? Material.RED_SANDSTONE : Material.SANDSTONE);
                    }
                }
            }
        }

        // Clear above
        for (int dx = -R; dx <= R; dx++)
            for (int dz = -R; dz <= R; dz++)
                if (dx * dx + dz * dz <= R * R)
                    for (int dy = 1; dy <= 15; dy++)
                        setBlock(w, bx + dx, by + dy, bz + dz, Material.AIR);

        // Walls — outer ring 12 blocks high, partially ruined
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                int dist = dx * dx + dz * dz;
                if (dist <= R * R && dist > (R - 4) * (R - 4)) {
                    int h = 8 + random.nextInt(5); // 8-12 high
                    for (int dy = 1; dy <= h; dy++) {
                        if (dy > 6 && random.nextFloat() < 0.3) continue;
                        Material mat = random.nextFloat() < 0.15 ? Material.CRACKED_POLISHED_BLACKSTONE_BRICKS :
                                random.nextFloat() < 0.2 ? Material.GILDED_BLACKSTONE : Material.POLISHED_BLACKSTONE_BRICKS;
                        setBlock(w, bx + dx, by + dy, bz + dz, mat);
                    }
                }
            }
        }

        // 4 Grand entrance arches
        int[][] entrances = {{0, R - 1}, {0, -(R - 1)}, {R - 1, 0}, {-(R - 1), 0}};
        for (int[] e : entrances) {
            for (int dy = 1; dy <= 6; dy++) {
                for (int d = -2; d <= 2; d++) {
                    int ex = e[0] == 0 ? d : e[0];
                    int ez = e[1] == 0 ? d : e[1];
                    setBlock(w, bx + ex, by + dy, bz + ez, Material.AIR);
                }
            }
        }

        // 8 Giant pillars around the arena
        for (int p = 0; p < 8; p++) {
            int px = (int) (Math.cos(p * Math.PI / 4) * (R - 6));
            int pz = (int) (Math.sin(p * Math.PI / 4) * (R - 6));
            for (int dy = 1; dy <= 14; dy++) {
                setBlock(w, bx + px, by + dy, bz + pz, Material.POLISHED_BLACKSTONE_BRICKS);
                setBlock(w, bx + px + 1, by + dy, bz + pz, Material.POLISHED_BLACKSTONE_BRICKS);
                setBlock(w, bx + px, by + dy, bz + pz + 1, Material.POLISHED_BLACKSTONE_BRICKS);
            }
            setBlock(w, bx + px, by + 14, bz + pz, Material.SOUL_LANTERN);
        }

        // Center platform with golden throne
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++)
                setBlock(w, bx + dx, by + 1, bz + dz, Material.GOLD_BLOCK);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                setBlock(w, bx + dx, by + 2, bz + dz, Material.GOLD_BLOCK);
        setBlock(w, bx, by + 3, bz, Material.GOLD_BLOCK);
        setBlock(w, bx, by + 4, bz, Material.BEACON);

        // Spectator tiers (3 levels)
        for (int tier = 0; tier < 3; tier++) {
            int tr = R - 6 - tier * 2;
            for (int dx = -tr; dx <= tr; dx++) {
                for (int dz = -tr; dz <= tr; dz++) {
                    int dist = dx * dx + dz * dz;
                    if (dist <= tr * tr && dist > (tr - 2) * (tr - 2)) {
                        setBlock(w, bx + dx, by + 2 + tier, bz + dz, Material.POLISHED_BLACKSTONE_SLAB);
                    }
                }
            }
        }

        // Soul torches ring
        for (int t = 0; t < 16; t++) {
            int tx = (int) (Math.cos(t * Math.PI / 8) * 12);
            int tz = (int) (Math.sin(t * Math.PI / 8) * 12);
            setBlock(w, bx + tx, by + 1, bz + tz, Material.SOUL_TORCH);
        }

        // LOTS of loot (6 chests — legendary!)
        placeChest(w, bx + 2, by + 2, bz, StructureType.TITAN_ARENA);
        placeChest(w, bx - 2, by + 2, bz, StructureType.TITAN_ARENA);
        placeChest(w, bx, by + 2, bz + 2, StructureType.TITAN_ARENA);
        placeChest(w, bx, by + 2, bz - 2, StructureType.TITAN_ARENA);
        placeChest(w, bx + 10, by + 1, bz, StructureType.TITAN_ARENA);
        placeChest(w, bx - 10, by + 1, bz, StructureType.TITAN_ARENA);

        return true;
    }

    // ==================== HELPER METHODS ====================

    private void setBlock(World w, int x, int y, int z, Material mat) {
        w.getBlockAt(x, y, z).setType(mat, false);
    }

    private void placeChest(World w, int x, int y, int z, StructureType type) {
        w.getBlockAt(x, y, z).setType(Material.CHEST, false);
        if (w.getBlockAt(x, y, z).getState() instanceof Chest chest) {
            fillLootChest(chest.getInventory(), type);
        }
    }

    // ==================== LOOT SYSTEM ====================

    private void fillLootChest(Inventory inv, StructureType type) {
        List<ItemStack> loot = new ArrayList<>();

        // ===== ESSENCES & KEYS (always present with good chances) =====
        addWithChance(loot, createEssenceItem(1 + random.nextInt(2)), 0.50);
        addWithChance(loot, createEssenceItem(2 + random.nextInt(2)), 0.20);
        addWithChance(loot, createKeyItem(), 0.40);
        addWithChance(loot, createKeyItem(), 0.15); // chance of 2 keys

        // ===== Common loot (all structures) =====
        addWithChance(loot, new ItemStack(Material.IRON_INGOT, 3 + random.nextInt(8)), 0.65);
        addWithChance(loot, new ItemStack(Material.GOLD_INGOT, 2 + random.nextInt(6)), 0.55);
        addWithChance(loot, new ItemStack(Material.DIAMOND, 1 + random.nextInt(4)), 0.40);
        addWithChance(loot, new ItemStack(Material.EMERALD, 3 + random.nextInt(8)), 0.50);
        addWithChance(loot, new ItemStack(Material.ENDER_PEARL, 2 + random.nextInt(4)), 0.35);
        addWithChance(loot, new ItemStack(Material.EXPERIENCE_BOTTLE, 8 + random.nextInt(16)), 0.60);
        addWithChance(loot, new ItemStack(Material.GOLDEN_APPLE, 1 + random.nextInt(3)), 0.30);
        addWithChance(loot, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1), 0.08);

        // ===== Type-specific loot =====
        switch (type) {
            case WELL:
                addWithChance(loot, createEssenceItem(1 + random.nextInt(2)), 0.35);
                addWithChance(loot, new ItemStack(Material.HEART_OF_THE_SEA, 1), 0.15);
                addWithChance(loot, new ItemStack(Material.TRIDENT, 1), 0.05);
                addWithChance(loot, new ItemStack(Material.PRISMARINE_SHARD, 4 + random.nextInt(6)), 0.40);
                break;
            case RUINED_SHRINE:
                addWithChance(loot, createEssenceItem(1 + random.nextInt(3)), 0.40);
                addWithChance(loot, createKeyItem(), 0.30);
                addWithChance(loot, new ItemStack(Material.ECHO_SHARD, 1 + random.nextInt(3)), 0.20);
                addWithChance(loot, new ItemStack(Material.TOTEM_OF_UNDYING, 1), 0.08);
                break;
            case FORGOTTEN_ALTAR:
                addWithChance(loot, createEssenceItem(2 + random.nextInt(3)), 0.40);
                addWithChance(loot, createKeyItem(), 0.35);
                addWithChance(loot, new ItemStack(Material.NETHERITE_SCRAP, 1 + random.nextInt(2)), 0.20);
                addWithChance(loot, new ItemStack(Material.ANCIENT_DEBRIS, 1 + random.nextInt(2)), 0.15);
                addWithChance(loot, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1), 0.12);
                addWithChance(loot, new ItemStack(Material.NETHERITE_INGOT, 1), 0.05);
                break;
            case WANDERER_CAMP:
                addWithChance(loot, createEssenceItem(1), 0.35);
                addWithChance(loot, new ItemStack(Material.COOKED_BEEF, 8 + random.nextInt(16)), 0.70);
                addWithChance(loot, new ItemStack(Material.GOLDEN_APPLE, 2 + random.nextInt(3)), 0.30);
                addWithChance(loot, new ItemStack(Material.SADDLE, 1), 0.20);
                addWithChance(loot, new ItemStack(Material.NAME_TAG, 1 + random.nextInt(2)), 0.25);
                break;
            case WATCHTOWER:
                addWithChance(loot, createEssenceItem(1 + random.nextInt(3)), 0.40);
                addWithChance(loot, createKeyItem(), 0.35);
                addWithChance(loot, new ItemStack(Material.DIAMOND, 2 + random.nextInt(4)), 0.35);
                addWithChance(loot, new ItemStack(Material.BOW, 1), 0.30);
                addWithChance(loot, new ItemStack(Material.ARROW, 16 + random.nextInt(32)), 0.50);
                addWithChance(loot, new ItemStack(Material.SPYGLASS, 1), 0.20);
                break;
            case DUNGEON:
                // Best loot — hardest to find (underground)
                addWithChance(loot, createEssenceItem(3 + random.nextInt(3)), 0.55);
                addWithChance(loot, createKeyItem(), 0.50);
                addWithChance(loot, createKeyItem(), 0.25);
                addWithChance(loot, new ItemStack(Material.NETHERITE_SCRAP, 1 + random.nextInt(3)), 0.30);
                addWithChance(loot, new ItemStack(Material.NETHERITE_INGOT, 1), 0.10);
                addWithChance(loot, new ItemStack(Material.TOTEM_OF_UNDYING, 1), 0.12);
                addWithChance(loot, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1 + random.nextInt(2)), 0.15);
                addWithChance(loot, new ItemStack(Material.DIAMOND_BLOCK, 1), 0.10);
                addWithChance(loot, new ItemStack(Material.ANCIENT_DEBRIS, 1 + random.nextInt(3)), 0.20);
                break;
            case OBELISK:
                addWithChance(loot, createEssenceItem(2 + random.nextInt(3)), 0.45);
                addWithChance(loot, createKeyItem(), 0.40);
                addWithChance(loot, new ItemStack(Material.ECHO_SHARD, 2 + random.nextInt(4)), 0.25);
                addWithChance(loot, new ItemStack(Material.NETHERITE_SCRAP, 1 + random.nextInt(2)), 0.20);
                addWithChance(loot, new ItemStack(Material.END_CRYSTAL, 1), 0.10);
                addWithChance(loot, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1), 0.10);
                break;
            case COLOSSEUM:
                // Great loot — big structure
                addWithChance(loot, createEssenceItem(2 + random.nextInt(4)), 0.50);
                addWithChance(loot, createKeyItem(), 0.45);
                addWithChance(loot, new ItemStack(Material.DIAMOND, 3 + random.nextInt(5)), 0.40);
                addWithChance(loot, new ItemStack(Material.DIAMOND_SWORD, 1), 0.15);
                addWithChance(loot, new ItemStack(Material.DIAMOND_CHESTPLATE, 1), 0.10);
                addWithChance(loot, new ItemStack(Material.GOLDEN_APPLE, 2 + random.nextInt(4)), 0.35);
                addWithChance(loot, new ItemStack(Material.TOTEM_OF_UNDYING, 1), 0.08);
                addWithChance(loot, new ItemStack(Material.NETHERITE_SCRAP, 1), 0.15);
                break;
            case FORTRESS:
                addWithChance(loot, createEssenceItem(2 + random.nextInt(3)), 0.45);
                addWithChance(loot, createKeyItem(), 0.40);
                addWithChance(loot, new ItemStack(Material.NETHERITE_SCRAP, 1 + random.nextInt(2)), 0.25);
                addWithChance(loot, new ItemStack(Material.BLAZE_ROD, 3 + random.nextInt(5)), 0.40);
                addWithChance(loot, new ItemStack(Material.WITHER_SKELETON_SKULL, 1), 0.05);
                addWithChance(loot, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1), 0.10);
                break;
            case LIBRARY:
                addWithChance(loot, createEssenceItem(1 + random.nextInt(3)), 0.40);
                addWithChance(loot, createKeyItem(), 0.35);
                addWithChance(loot, new ItemStack(Material.EXPERIENCE_BOTTLE, 16 + random.nextInt(16)), 0.60);
                addWithChance(loot, new ItemStack(Material.BOOK, 5 + random.nextInt(10)), 0.50);
                addWithChance(loot, new ItemStack(Material.NAME_TAG, 1 + random.nextInt(2)), 0.25);
                break;
            case GRAVEYARD:
                addWithChance(loot, createEssenceItem(1 + random.nextInt(2)), 0.40);
                addWithChance(loot, createKeyItem(), 0.35);
                addWithChance(loot, new ItemStack(Material.BONE, 5 + random.nextInt(10)), 0.60);
                addWithChance(loot, new ItemStack(Material.SKULL_BANNER_PATTERN, 1), 0.10);
                addWithChance(loot, new ItemStack(Material.TOTEM_OF_UNDYING, 1), 0.06);
                addWithChance(loot, new ItemStack(Material.ECHO_SHARD, 1 + random.nextInt(2)), 0.15);
                break;
            case PYRAMID:
                addWithChance(loot, createEssenceItem(2 + random.nextInt(3)), 0.50);
                addWithChance(loot, createKeyItem(), 0.40);
                addWithChance(loot, new ItemStack(Material.GOLD_INGOT, 4 + random.nextInt(8)), 0.55);
                addWithChance(loot, new ItemStack(Material.DIAMOND, 2 + random.nextInt(4)), 0.35);
                addWithChance(loot, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1), 0.12);
                addWithChance(loot, new ItemStack(Material.NETHERITE_SCRAP, 1), 0.15);
                break;
            case SHIPWRECK:
                addWithChance(loot, createEssenceItem(1 + random.nextInt(2)), 0.40);
                addWithChance(loot, createKeyItem(), 0.30);
                addWithChance(loot, new ItemStack(Material.IRON_INGOT, 4 + random.nextInt(8)), 0.55);
                addWithChance(loot, new ItemStack(Material.EMERALD, 3 + random.nextInt(6)), 0.45);
                addWithChance(loot, new ItemStack(Material.HEART_OF_THE_SEA, 1), 0.08);
                addWithChance(loot, new ItemStack(Material.MAP, 1), 0.30);
                break;
            case TITAN_ARENA:
                // LEGENDARY — best loot in the game (very rare structure)
                addWithChance(loot, createEssenceItem(3 + random.nextInt(5)), 0.70);
                addWithChance(loot, createKeyItem(), 0.60);
                addWithChance(loot, createKeyItem(), 0.30);
                addWithChance(loot, new ItemStack(Material.NETHERITE_INGOT, 1), 0.15);
                addWithChance(loot, new ItemStack(Material.NETHERITE_SCRAP, 1 + random.nextInt(3)), 0.35);
                addWithChance(loot, new ItemStack(Material.DIAMOND_BLOCK, 1), 0.15);
                addWithChance(loot, new ItemStack(Material.TOTEM_OF_UNDYING, 1), 0.15);
                addWithChance(loot, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1 + random.nextInt(2)), 0.20);
                addWithChance(loot, new ItemStack(Material.ELYTRA, 1), 0.05);
                break;
        }

        // ===== Rare loot (all structures) =====
        addWithChance(loot, new ItemStack(Material.NETHERITE_SCRAP, 1), 0.08);
        addWithChance(loot, createEssenceItem(1 + random.nextInt(2)), 0.25);
        addWithChance(loot, createKeyItem(), 0.20);

        // Shuffle and place in random slots
        Collections.shuffle(loot);
        for (ItemStack item : loot) {
            int slot = random.nextInt(inv.getSize());
            int attempts = 0;
            while (inv.getItem(slot) != null && attempts < 27) {
                slot = random.nextInt(inv.getSize());
                attempts++;
            }
            if (inv.getItem(slot) == null) {
                inv.setItem(slot, item);
            }
        }
    }

    private void addWithChance(List<ItemStack> loot, ItemStack item, double chance) {
        if (random.nextDouble() < chance) {
            loot.add(item);
        }
    }

    private ItemStack createEssenceItem(int amount) {
        ItemStack item = new ItemStack(Material.NETHER_STAR, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§d§l✦ §5Esencia Arcana");
            meta.setLore(Arrays.asList(
                    "",
                    "§7Click derecho para obtener",
                    "§d+" + amount + " Esencias",
                    "",
                    "§8Encontrado en una estructura antigua"
            ));
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(new NamespacedKey("wardstone", "essence_amount"),
                    PersistentDataType.INTEGER, amount);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createKeyItem() {
        ItemStack item = new ItemStack(Material.TRIPWIRE_HOOK, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§l🗝 §6Llave de Estructura");
            meta.setLore(Arrays.asList(
                    "",
                    "§7Click derecho para recibir",
                    "§euna llave de crate aleatoria",
                    "",
                    "§8Encontrada en una estructura antigua"
            ));
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(new NamespacedKey("wardstone", "structure_key"),
                    PersistentDataType.BOOLEAN, true);
            item.setItemMeta(meta);
        }
        return item;
    }

    // ==================== ZONE DISCOVERY ====================

    private void startDiscoveryTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    checkDiscovery(player);
                }
            }
        }.runTaskTimer(plugin, 40L, 40L); // Every 2 seconds
    }

    private void checkDiscovery(Player player) {
        Location pLoc = player.getLocation();
        for (Map.Entry<String, StructureData> entry : placedStructures.entrySet()) {
            StructureData sd = entry.getValue();
            if (!sd.world.equals(pLoc.getWorld().getName())) continue;

            double dist = Math.sqrt(Math.pow(sd.x - pLoc.getBlockX(), 2) + Math.pow(sd.z - pLoc.getBlockZ(), 2));
            if (dist > 15) continue;

            // Combine player UUID and structure ID for unique discovery tracking
            String discoveryKey = player.getUniqueId() + ":" + entry.getKey();
            UUID discoveryId = UUID.nameUUIDFromBytes(discoveryKey.getBytes());

            if (!discoveredZones.contains(discoveryId)) {
                discoveredZones.add(discoveryId);
                saveData();

                // Discovery message
                player.sendTitle(
                        "§a§l✦ ZONA DESBLOQUEADA ✦",
                        sd.type.displayName,
                        10, 60, 20
                );
                player.sendMessage("");
                player.sendMessage(SmallCaps.convert("§a§l✦ §fNueva zona desbloqueada: " + sd.type.displayName));
                player.sendMessage(SmallCaps.convert("§7  Coordenadas: §e" + sd.x + ", " + sd.y + ", " + sd.z));
                player.sendMessage("");

                // Sound
                player.playSound(pLoc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);

                // Particles
                pLoc.getWorld().spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, pLoc.clone().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.2);
            }
        }
    }

    // ==================== ITEM INTERACTION ====================

    public boolean handleEssenceItem(Player player, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey("wardstone", "essence_amount");
        if (!pdc.has(key, PersistentDataType.INTEGER)) return false;

        int amount = pdc.get(key, PersistentDataType.INTEGER);
        plugin.getEssenceManager().addEssences(player.getUniqueId(), amount);
        plugin.getEssenceManager().saveData();
        player.sendMessage(SmallCaps.convert("§d§l✦ §a+" + amount + " Esencias §7obtenidas."));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);

        // Remove one item
        if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
        else player.getInventory().setItemInMainHand(null);
        return true;
    }

    public boolean handleKeyItem(Player player, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey("wardstone", "structure_key");
        if (!pdc.has(key, PersistentDataType.BOOLEAN)) return false;

        // Give a random crate key
        String[] keyTypes = {"common", "rare", "special"};
        double roll = random.nextDouble();
        String keyType;
        if (roll < 0.5) keyType = keyTypes[0];       // 50% common
        else if (roll < 0.85) keyType = keyTypes[1];  // 35% rare
        else keyType = keyTypes[2];                    // 15% special

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "excellentcrates key give " + player.getName() + " " + keyType + " 1");

        player.sendMessage(SmallCaps.convert("§e§l🗝 §fHas obtenido una llave §e" + keyType + "§f."));
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.3f);

        // 2% chance de llave_espadas al abrir llave de estructura
        if (random.nextDouble() < 0.02) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "excellentcrates key give " + player.getName() + " llave_espadas 1");
            player.sendMessage(com.moonlight.coreprotect.util.SmallCaps.convert("§c§l⚔ §f¡Has obtenido una §c§lLlave de Espadas§f! §7(Estructura - Ultra rara)"));
            Bukkit.broadcastMessage(com.moonlight.coreprotect.util.SmallCaps.convert("§c§l⚔ §e" + player.getName() + " §fha encontrado una §c§lLlave de Espadas §fen una estructura!"));
        }

        if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
        else player.getInventory().setItemInMainHand(null);
        return true;
    }

    // ==================== DATA PERSISTENCE ====================

    private void loadData() {
        dataFile = new File(plugin.getDataFolder(), "structures.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Error creating structures.yml: " + e.getMessage());
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        // Load placed structures
        if (dataConfig.contains("structures")) {
            for (String key : dataConfig.getConfigurationSection("structures").getKeys(false)) {
                try {
                    String typeName = dataConfig.getString("structures." + key + ".type");
                    int x = dataConfig.getInt("structures." + key + ".x");
                    int y = dataConfig.getInt("structures." + key + ".y");
                    int z = dataConfig.getInt("structures." + key + ".z");
                    String world = dataConfig.getString("structures." + key + ".world");
                    StructureType type = StructureType.valueOf(typeName);
                    placedStructures.put(key, new StructureData(type, x, y, z, world));
                } catch (Exception ignored) {}
            }
        }

        // Load generated chunks
        List<String> chunks = dataConfig.getStringList("generatedChunks");
        for (String c : chunks) {
            try {
                generatedChunks.add(Long.parseLong(c));
            } catch (NumberFormatException ignored) {}
        }

        // Load discoveries
        List<String> discoveries = dataConfig.getStringList("discoveries");
        for (String d : discoveries) {
            try {
                discoveredZones.add(UUID.fromString(d));
            } catch (IllegalArgumentException ignored) {}
        }

        plugin.getLogger().info("[Structures] Cargadas " + placedStructures.size() + " estructuras.");
    }

    public void saveData() {
        if (dataConfig == null) return;

        dataConfig.set("structures", null);
        for (Map.Entry<String, StructureData> entry : placedStructures.entrySet()) {
            String path = "structures." + entry.getKey();
            StructureData sd = entry.getValue();
            dataConfig.set(path + ".type", sd.type.name());
            dataConfig.set(path + ".x", sd.x);
            dataConfig.set(path + ".y", sd.y);
            dataConfig.set(path + ".z", sd.z);
            dataConfig.set(path + ".world", sd.world);
        }

        // Save only last 5000 chunks to prevent bloat
        List<String> chunkList = new ArrayList<>();
        int count = 0;
        for (Long c : generatedChunks) {
            chunkList.add(c.toString());
            if (++count > 5000) break;
        }
        dataConfig.set("generatedChunks", chunkList);

        List<String> discoveryList = new ArrayList<>();
        for (UUID d : discoveredZones) discoveryList.add(d.toString());
        dataConfig.set("discoveries", discoveryList);

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Error saving structures.yml: " + e.getMessage());
        }
    }

    // ==================== MANUAL SPAWN ====================

    public void spawnStructureAt(Location loc, String typeStr) {
        StructureType type = switch (typeStr.toLowerCase()) {
            case "well" -> StructureType.WELL;
            case "shrine" -> StructureType.RUINED_SHRINE;
            case "altar" -> StructureType.FORGOTTEN_ALTAR;
            case "camp" -> StructureType.WANDERER_CAMP;
            case "tower" -> StructureType.WATCHTOWER;
            case "dungeon" -> StructureType.DUNGEON;
            case "obelisk" -> StructureType.OBELISK;
            case "colosseum" -> StructureType.COLOSSEUM;
            case "fortress" -> StructureType.FORTRESS;
            case "library" -> StructureType.LIBRARY;
            case "graveyard" -> StructureType.GRAVEYARD;
            case "pyramid" -> StructureType.PYRAMID;
            case "shipwreck" -> StructureType.SHIPWRECK;
            case "titan" -> StructureType.TITAN_ARENA;
            default -> null;
        };

        if (type == null) return;

        switch (type) {
            case WELL -> buildWell(loc);
            case RUINED_SHRINE -> buildRuinedShrine(loc);
            case FORGOTTEN_ALTAR -> buildForgottenAltar(loc);
            case WANDERER_CAMP -> buildWandererCamp(loc);
            case WATCHTOWER -> buildWatchtower(loc);
            case DUNGEON -> buildDungeon(loc);
            case OBELISK -> buildObelisk(loc);
            case COLOSSEUM -> buildColosseum(loc);
            case FORTRESS -> buildFortress(loc);
            case LIBRARY -> buildLibrary(loc);
            case GRAVEYARD -> buildGraveyard(loc);
            case PYRAMID -> buildPyramid(loc);
            case SHIPWRECK -> buildShipwreck(loc);
            case TITAN_ARENA -> buildTitanArena(loc);
        }

        // Save structure data (don't call saveData() here — caller handles batch save)
        String structId = UUID.randomUUID().toString();
        StructureData data = new StructureData(type, 
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), loc.getWorld().getName());
        placedStructures.put(structId, data);

        // Add BlueMap marker
        if (plugin.getBlueMapIntegration() != null) {
            plugin.getBlueMapIntegration().addStructureMarker(structId, data);
        }

        plugin.getLogger().info("[Structures] " + type.id + " en " + loc.getWorld().getName() + " " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ());
    }

    public void shutdown() {
        saveData();
    }

    public Map<String, StructureData> getPlacedStructures() {
        return placedStructures;
    }

    private long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
