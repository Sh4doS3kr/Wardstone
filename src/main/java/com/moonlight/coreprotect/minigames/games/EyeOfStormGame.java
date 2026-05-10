package com.moonlight.coreprotect.minigames.games;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.minigames.MiniGame;
import com.moonlight.coreprotect.minigames.MiniGameManager;
import com.moonlight.coreprotect.minigames.MiniGameType;
import com.moonlight.coreprotect.minigames.MiniGameWorld;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * OJO DE LA TORMENTA — "Eye of the Storm"
 *
 * Mapa GIGANTE estilo Battle Royale. Una tormenta mortal cubre TODO el mapa.
 * Solo hay UNA zona segura (el "ojo") que se mueve aleatoriamente.
 * Los jugadores deben perseguir el ojo mientras pelean entre sí.
 *
 * Mecánicas:
 * - El ojo se mueve cada ~18s a una posición random
 * - El ojo se encoge con el tiempo (empieza radio 15, mínimo 4)
 * - Fuera del ojo: daño creciente + oscuridad + rayos
 * - Dentro del ojo: PvP brutal en espacio reducido
 * - Fases cada 60s: tormenta más agresiva
 * - Cofres con loot repartidos por el mapa
 */
public class EyeOfStormGame extends MiniGame {

    // === ARENA ===
    private static final int ARENA_RADIUS = 120;
    private static final int ARENA_Y = 70;
    private static final int BLOCKS_PER_TICK = 6000;

    // === EYE ===
    private static final double EYE_INITIAL_RADIUS = 16.0;
    private static final double EYE_MIN_RADIUS = 4.0;
    private static final int EYE_MOVE_INTERVAL = 18; // seconds
    private static final double EYE_MOVE_MAX_DIST = 70; // max distance the eye can jump

    // === STORM DAMAGE ===
    private static final double STORM_DAMAGE_BASE = 1.0;
    private static final double STORM_DAMAGE_PER_PHASE = 1.5;

    // === PHASES ===
    private static final int PHASE_2_TIME = 60;
    private static final int PHASE_3_TIME = 120;
    private static final int PHASE_4_TIME = 180;

    // === STATE ===
    private BossBar stormBar;
    private final Random random = new Random();
    private boolean gameStarted = false;
    private int currentPhase = 1;

    // Eye position (center of safe zone)
    private double eyeX = 0;
    private double eyeZ = 0;
    private double eyeRadius = EYE_INITIAL_RADIUS;
    private double targetEyeX = 0;
    private double targetEyeZ = 0;

    // Eye movement tracking
    private int nextEyeMoveAt = EYE_MOVE_INTERVAL;
    private boolean eyeMoving = false;
    private int eyeMoveCountdown = 0;

    // Tasks
    private int particleTaskId = -1;
    private int stormEffectTaskId = -1;

    public EyeOfStormGame(CoreProtectPlugin plugin, MiniGameManager manager) {
        super(plugin, manager, MiniGameType.EYE_OF_STORM);
    }

    // ═══════════════════════════════════════════════════════════
    //  ARENA: Mapa gigante con terreno variado (async)
    // ═══════════════════════════════════════════════════════════

    @Override
    public void buildArena(World world) {
        // Pre-cargar chunks
        int chunkRadius = (ARENA_RADIUS / 16) + 2;
        for (int cx = -chunkRadius; cx <= chunkRadius; cx++) {
            for (int cz = -chunkRadius; cz <= chunkRadius; cz++) {
                world.getChunkAt(cx, cz).load(true);
                world.getChunkAt(cx, cz).setForceLoaded(true);
            }
        }

        // Generar todo de forma async por batches
        List<Runnable> blockTasks = new ArrayList<>();

        // === TERRENO BASE: colinas y valles con noise simulado ===
        for (int x = -ARENA_RADIUS; x <= ARENA_RADIUS; x++) {
            for (int z = -ARENA_RADIUS; z <= ARENA_RADIUS; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > ARENA_RADIUS) continue;

                final int fx = x;
                final int fz = z;

                blockTasks.add(() -> {
                    // Altura variable (colinas) usando funciones trigonométricas como pseudo-noise
                    double noise = Math.sin(fx * 0.05) * Math.cos(fz * 0.05) * 4
                            + Math.sin(fx * 0.12 + 3) * Math.cos(fz * 0.08 + 1) * 3
                            + Math.sin(fx * 0.02) * Math.sin(fz * 0.02) * 6;
                    int height = ARENA_Y + (int) noise;
                    if (height < ARENA_Y - 6) height = ARENA_Y - 6;

                    // Borde del mapa: bajar para que caigan al vacío
                    double edgeDist = ARENA_RADIUS - dist;
                    if (edgeDist < 8) {
                        height -= (int) (8 - edgeDist);
                    }

                    // Capas de terreno
                    for (int y = ARENA_Y - 10; y <= height; y++) {
                        Material mat;
                        if (y == height) {
                            mat = Material.GRASS_BLOCK;
                        } else if (y >= height - 2) {
                            mat = Material.DIRT;
                        } else if (y >= height - 5) {
                            mat = Material.STONE;
                        } else {
                            mat = Material.DEEPSLATE;
                        }
                        world.getBlockAt(fx, y, fz).setType(mat, false);
                    }
                });
            }
        }

        // === ESTRUCTURAS: Ruinas, casas, torres ===
        // Ruinas dispersas (20)
        for (int i = 0; i < 20; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 15 + random.nextDouble() * 95;
            int sx = (int) (dist * Math.cos(angle));
            int sz = (int) (dist * Math.sin(angle));
            blockTasks.add(() -> buildRuin(world, sx, sz));
        }

        // Casas pequeñas (12)
        for (int i = 0; i < 12; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 20 + random.nextDouble() * 90;
            int sx = (int) (dist * Math.cos(angle));
            int sz = (int) (dist * Math.sin(angle));
            blockTasks.add(() -> buildHouse(world, sx, sz));
        }

        // Torres de vigilancia (6)
        for (int i = 0; i < 6; i++) {
            double angle = (2 * Math.PI / 6) * i + random.nextDouble() * 0.5;
            double dist = 50 + random.nextDouble() * 50;
            int sx = (int) (dist * Math.cos(angle));
            int sz = (int) (dist * Math.sin(angle));
            blockTasks.add(() -> buildTower(world, sx, sz));
        }

        // Bunkers subterráneos (8)
        for (int i = 0; i < 8; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 25 + random.nextDouble() * 80;
            int sx = (int) (dist * Math.cos(angle));
            int sz = (int) (dist * Math.sin(angle));
            blockTasks.add(() -> buildBunker(world, sx, sz));
        }

        // Árboles grandes (35)
        for (int i = 0; i < 35; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 10 + random.nextDouble() * 105;
            int sx = (int) (dist * Math.cos(angle));
            int sz = (int) (dist * Math.sin(angle));
            blockTasks.add(() -> buildTree(world, sx, sz));
        }

        // Rocas/piedras grandes (25)
        for (int i = 0; i < 25; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 10 + random.nextDouble() * 100;
            int sx = (int) (dist * Math.cos(angle));
            int sz = (int) (dist * Math.sin(angle));
            blockTasks.add(() -> buildRockFormation(world, sx, sz));
        }

        // Muros rotos/trincheras (15)
        for (int i = 0; i < 15; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 15 + random.nextDouble() * 95;
            int sx = (int) (dist * Math.cos(angle));
            int sz = (int) (dist * Math.sin(angle));
            double wallAngle = random.nextDouble() * Math.PI;
            blockTasks.add(() -> buildBrokenWall(world, sx, sz, wallAngle));
        }

        // Cofres con loot (40 repartidos por todo el mapa)
        for (int i = 0; i < 40; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 10 + random.nextDouble() * 105;
            int sx = (int) (dist * Math.cos(angle));
            int sz = (int) (dist * Math.sin(angle));
            blockTasks.add(() -> placeChest(world, sx, sz));
        }

        // === EJECUTAR ASYNC POR BATCHES ===
        new BukkitRunnable() {
            int index = 0;
            @Override
            public void run() {
                int done = 0;
                while (index < blockTasks.size() && done < BLOCKS_PER_TICK) {
                    blockTasks.get(index).run();
                    index++;
                    done++;
                }
                if (index >= blockTasks.size()) {
                    plugin.getLogger().info("[EyeOfStorm] Arena construida: " + blockTasks.size() + " tareas ejecutadas.");
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // --- Estructura: Ruina ---
    private void buildRuin(World world, int bx, int bz) {
        int baseY = getGroundY(world, bx, bz);
        int size = 3 + random.nextInt(3);
        Material[] mats = {Material.COBBLESTONE, Material.MOSSY_COBBLESTONE, Material.STONE_BRICKS, Material.CRACKED_STONE_BRICKS};

        for (int x = -size; x <= size; x++) {
            for (int z = -size; z <= size; z++) {
                int wallHeight = 1 + random.nextInt(3);
                if (Math.abs(x) == size || Math.abs(z) == size) {
                    for (int y = 1; y <= wallHeight; y++) {
                        if (random.nextInt(3) > 0) { // Paredes rotas
                            world.getBlockAt(bx + x, baseY + y, bz + z).setType(mats[random.nextInt(mats.length)], false);
                        }
                    }
                }
            }
        }
        // Suelo interior
        for (int x = -size + 1; x < size; x++) {
            for (int z = -size + 1; z < size; z++) {
                world.getBlockAt(bx + x, baseY, bz + z).setType(Material.STONE_BRICKS, false);
            }
        }
    }

    // --- Estructura: Casa ---
    private void buildHouse(World world, int bx, int bz) {
        int baseY = getGroundY(world, bx, bz);
        int w = 3 + random.nextInt(2);
        int h = 3 + random.nextInt(2);
        Material wall = random.nextBoolean() ? Material.OAK_PLANKS : Material.SPRUCE_PLANKS;
        Material roof = random.nextBoolean() ? Material.DARK_OAK_SLAB : Material.SPRUCE_SLAB;

        // Paredes
        for (int x = -w; x <= w; x++) {
            for (int z = -w; z <= w; z++) {
                for (int y = 1; y <= h; y++) {
                    if (Math.abs(x) == w || Math.abs(z) == w) {
                        world.getBlockAt(bx + x, baseY + y, bz + z).setType(wall, false);
                    }
                }
                // Techo
                world.getBlockAt(bx + x, baseY + h + 1, bz + z).setType(roof, false);
            }
        }
        // Puerta (hueco en una pared)
        world.getBlockAt(bx + w, baseY + 1, bz).setType(Material.AIR, false);
        world.getBlockAt(bx + w, baseY + 2, bz).setType(Material.AIR, false);
        // Ventanas
        if (h >= 3) {
            world.getBlockAt(bx - w, baseY + 2, bz).setType(Material.GLASS_PANE, false);
            world.getBlockAt(bx, baseY + 2, bz + w).setType(Material.GLASS_PANE, false);
        }
        // Suelo
        for (int x = -w + 1; x < w; x++) {
            for (int z = -w + 1; z < w; z++) {
                world.getBlockAt(bx + x, baseY, bz + z).setType(Material.OAK_PLANKS, false);
            }
        }
    }

    // --- Estructura: Torre de vigilancia ---
    private void buildTower(World world, int bx, int bz) {
        int baseY = getGroundY(world, bx, bz);
        int towerHeight = 10 + random.nextInt(5);

        // Pilar central
        for (int y = 1; y <= towerHeight; y++) {
            world.getBlockAt(bx, baseY + y, bz).setType(Material.STONE_BRICKS, false);
            world.getBlockAt(bx + 1, baseY + y, bz).setType(Material.STONE_BRICKS, false);
            world.getBlockAt(bx, baseY + y, bz + 1).setType(Material.STONE_BRICKS, false);
            world.getBlockAt(bx + 1, baseY + y, bz + 1).setType(Material.STONE_BRICKS, false);
            // Interior hueco
            if (y < towerHeight) {
                world.getBlockAt(bx, baseY + y, bz).setType(Material.AIR, false);
            }
        }
        // Esquinas
        for (int y = 1; y <= towerHeight; y++) {
            world.getBlockAt(bx - 1, baseY + y, bz - 1).setType(Material.STONE_BRICK_WALL, false);
            world.getBlockAt(bx + 2, baseY + y, bz - 1).setType(Material.STONE_BRICK_WALL, false);
            world.getBlockAt(bx - 1, baseY + y, bz + 2).setType(Material.STONE_BRICK_WALL, false);
            world.getBlockAt(bx + 2, baseY + y, bz + 2).setType(Material.STONE_BRICK_WALL, false);
        }
        // Plataforma superior
        for (int x = -2; x <= 3; x++) {
            for (int z = -2; z <= 3; z++) {
                world.getBlockAt(bx + x, baseY + towerHeight, bz + z).setType(Material.STONE_BRICK_SLAB, false);
            }
        }
        // Escalera interior (ladders)
        for (int y = 1; y < towerHeight; y++) {
            world.getBlockAt(bx, baseY + y, bz).setType(Material.LADDER, false);
        }
        // Puerta
        world.getBlockAt(bx - 1, baseY + 1, bz).setType(Material.AIR, false);
        world.getBlockAt(bx - 1, baseY + 2, bz).setType(Material.AIR, false);
    }

    // --- Estructura: Bunker subterráneo ---
    private void buildBunker(World world, int bx, int bz) {
        int baseY = getGroundY(world, bx, bz);
        int depth = 3;
        int size = 3;

        // Cavar espacio
        for (int x = -size; x <= size; x++) {
            for (int z = -size; z <= size; z++) {
                for (int y = -depth; y <= 0; y++) {
                    world.getBlockAt(bx + x, baseY + y, bz + z).setType(Material.AIR, false);
                }
                // Techo reforzado
                world.getBlockAt(bx + x, baseY + 1, bz + z).setType(Material.IRON_BARS, false);
            }
        }
        // Paredes del bunker
        for (int x = -size; x <= size; x++) {
            for (int z = -size; z <= size; z++) {
                if (Math.abs(x) == size || Math.abs(z) == size) {
                    for (int y = -depth; y <= 0; y++) {
                        world.getBlockAt(bx + x, baseY + y, bz + z).setType(Material.IRON_BLOCK, false);
                    }
                }
            }
        }
        // Suelo
        for (int x = -size + 1; x < size; x++) {
            for (int z = -size + 1; z < size; z++) {
                world.getBlockAt(bx + x, baseY - depth, bz + z).setType(Material.POLISHED_DEEPSLATE, false);
            }
        }
        // Entrada (escalera bajando)
        world.getBlockAt(bx + size, baseY, bz).setType(Material.AIR, false);
        world.getBlockAt(bx + size, baseY - 1, bz).setType(Material.AIR, false);
        world.getBlockAt(bx + size + 1, baseY, bz).setType(Material.AIR, false);
        world.getBlockAt(bx + size + 1, baseY - 1, bz).setType(Material.OAK_STAIRS, false);
        // Luz
        world.getBlockAt(bx, baseY - 1, bz).setType(Material.SOUL_LANTERN, false);
    }

    // --- Estructura: Árbol grande ---
    private void buildTree(World world, int bx, int bz) {
        int baseY = getGroundY(world, bx, bz);
        int trunkHeight = 5 + random.nextInt(4);
        Material log = random.nextBoolean() ? Material.OAK_LOG : Material.DARK_OAK_LOG;
        Material leaves = random.nextBoolean() ? Material.OAK_LEAVES : Material.DARK_OAK_LEAVES;

        // Tronco
        for (int y = 1; y <= trunkHeight; y++) {
            world.getBlockAt(bx, baseY + y, bz).setType(log, false);
        }
        // Copa
        int leafStart = trunkHeight - 2;
        for (int y = leafStart; y <= trunkHeight + 2; y++) {
            int leafRadius = (y <= trunkHeight) ? 3 : 1;
            for (int x = -leafRadius; x <= leafRadius; x++) {
                for (int z = -leafRadius; z <= leafRadius; z++) {
                    if (x == 0 && z == 0 && y <= trunkHeight) continue; // Tronco
                    double d = Math.sqrt(x * x + z * z);
                    if (d <= leafRadius + 0.5 && random.nextInt(5) > 0) {
                        Block b = world.getBlockAt(bx + x, baseY + y, bz + z);
                        if (b.getType() == Material.AIR) {
                            b.setType(leaves, false);
                        }
                    }
                }
            }
        }
    }

    // --- Estructura: Formación rocosa ---
    private void buildRockFormation(World world, int bx, int bz) {
        int baseY = getGroundY(world, bx, bz);
        int rockHeight = 2 + random.nextInt(4);
        int rockSize = 1 + random.nextInt(2);
        Material[] rocks = {Material.STONE, Material.ANDESITE, Material.DIORITE, Material.COBBLESTONE};

        for (int x = -rockSize; x <= rockSize; x++) {
            for (int z = -rockSize; z <= rockSize; z++) {
                int h = rockHeight - Math.abs(x) - Math.abs(z);
                if (h < 1) h = 1;
                for (int y = 1; y <= h; y++) {
                    world.getBlockAt(bx + x, baseY + y, bz + z).setType(rocks[random.nextInt(rocks.length)], false);
                }
            }
        }
    }

    // --- Estructura: Muro roto ---
    private void buildBrokenWall(World world, int bx, int bz, double wallAngle) {
        int baseY = getGroundY(world, bx, bz);
        int length = 5 + random.nextInt(6);
        Material[] mats = {Material.STONE_BRICKS, Material.CRACKED_STONE_BRICKS, Material.MOSSY_STONE_BRICKS};

        for (int i = -length; i <= length; i++) {
            int wx = bx + (int) (i * Math.cos(wallAngle));
            int wz = bz + (int) (i * Math.sin(wallAngle));
            int wallH = 1 + random.nextInt(3);
            for (int y = 1; y <= wallH; y++) {
                if (random.nextInt(4) > 0) { // 75% chance block exists (broken)
                    world.getBlockAt(wx, baseY + y, wz).setType(mats[random.nextInt(mats.length)], false);
                }
            }
        }
    }

    // --- Cofre con loot ---
    private void placeChest(World world, int bx, int bz) {
        int baseY = getGroundY(world, bx, bz);
        world.getBlockAt(bx, baseY + 1, bz).setType(Material.CHEST, false);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Block chestBlock = world.getBlockAt(bx, baseY + 1, bz);
            if (chestBlock.getState() instanceof org.bukkit.block.Chest chest) {
                fillChestLoot(chest);
            }
        }, 5L);
    }

    private void fillChestLoot(org.bukkit.block.Chest chest) {
        org.bukkit.inventory.Inventory inv = chest.getInventory();
        inv.clear();

        // Loot aleatorio
        ItemStack[][] lootTable = {
                {new ItemStack(Material.IRON_SWORD)},
                {new ItemStack(Material.STONE_SWORD)},
                {new ItemStack(Material.DIAMOND_SWORD)},
                {new ItemStack(Material.BOW)},
                {new ItemStack(Material.ARROW, 8 + random.nextInt(16))},
                {new ItemStack(Material.GOLDEN_APPLE, 1 + random.nextInt(3))},
                {new ItemStack(Material.IRON_HELMET)},
                {new ItemStack(Material.IRON_CHESTPLATE)},
                {new ItemStack(Material.IRON_LEGGINGS)},
                {new ItemStack(Material.IRON_BOOTS)},
                {new ItemStack(Material.DIAMOND_HELMET)},
                {new ItemStack(Material.DIAMOND_CHESTPLATE)},
                {new ItemStack(Material.CHAINMAIL_CHESTPLATE)},
                {new ItemStack(Material.SHIELD)},
                {new ItemStack(Material.COBBLESTONE, 16 + random.nextInt(32))},
                {new ItemStack(Material.OAK_PLANKS, 16 + random.nextInt(16))},
                {new ItemStack(Material.ENDER_PEARL, 1 + random.nextInt(2))},
                {new ItemStack(Material.COOKED_BEEF, 4 + random.nextInt(8))},
                {new ItemStack(Material.CROSSBOW)},
                {new ItemStack(Material.SPLASH_POTION)}, // placeholder
                {new ItemStack(Material.FISHING_ROD)},
                {new ItemStack(Material.SNOWBALL, 8)},
        };

        int numItems = 3 + random.nextInt(4);
        Set<Integer> usedSlots = new HashSet<>();
        for (int i = 0; i < numItems; i++) {
            int slot = random.nextInt(27);
            while (usedSlots.contains(slot)) slot = random.nextInt(27);
            usedSlots.add(slot);
            ItemStack item = lootTable[random.nextInt(lootTable.length)][0].clone();
            inv.setItem(slot, item);
        }
    }

    private int getGroundY(World world, int x, int z) {
        // Calcular la misma altura que buildArena genera
        double noise = Math.sin(x * 0.05) * Math.cos(z * 0.05) * 4
                + Math.sin(x * 0.12 + 3) * Math.cos(z * 0.08 + 1) * 3
                + Math.sin(x * 0.02) * Math.sin(z * 0.02) * 6;
        int height = ARENA_Y + (int) noise;
        if (height < ARENA_Y - 6) height = ARENA_Y - 6;

        double dist = Math.sqrt(x * x + z * z);
        double edgeDist = ARENA_RADIUS - dist;
        if (edgeDist < 8) {
            height -= (int) (8 - edgeDist);
        }
        return height;
    }

    // ═══════════════════════════════════════════
    //  SPAWN LOCATIONS
    // ═══════════════════════════════════════════

    @Override
    public List<Location> getSpawnLocations(World world) {
        List<Location> spawns = new ArrayList<>();
        int maxPlayers = 16;
        double spawnRadius = 80;

        for (int i = 0; i < maxPlayers; i++) {
            double angle = (2 * Math.PI / maxPlayers) * i;
            int sx = (int) (spawnRadius * Math.cos(angle));
            int sz = (int) (spawnRadius * Math.sin(angle));
            int sy = getGroundY(world, sx, sz) + 1;
            Location spawn = new Location(world, sx + 0.5, sy, sz + 0.5);
            spawn.setYaw((float) Math.toDegrees(-angle + Math.PI));
            spawns.add(spawn);
        }
        return spawns;
    }

    // ═══════════════════════════════════════════
    //  GAME LOGIC
    // ═══════════════════════════════════════════

    @Override
    public void startGameLogic() {
        gameStarted = true;
        currentPhase = 1;
        eyeX = 0;
        eyeZ = 0;
        eyeRadius = EYE_INITIAL_RADIUS;
        nextEyeMoveAt = EYE_MOVE_INTERVAL;

        // BossBar
        stormBar = Bukkit.createBossBar("§8§l⛈ OJO DE LA TORMENTA ⛈", BarColor.PURPLE, BarStyle.SEGMENTED_10);
        stormBar.setVisible(true);
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) stormBar.addPlayer(p);
        }
        for (UUID uuid : spectators) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) stormBar.addPlayer(p);
        }

        // Kit inicial
        giveStartingKit();

        // Efectos de tormenta constantes
        startStormEffects();

        // Partículas del ojo
        startEyeParticles();

        broadcastGame("§8§l⛈ §7La tormenta cubre todo. §eBusca el OJO para sobrevivir.");
        broadcastGame("§8§l⛈ §7El ojo se mueve cada §f" + EYE_MOVE_INTERVAL + "s§7. ¡Persíguelo!");
    }

    private void giveStartingKit() {
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            p.getInventory().setItem(0, new ItemStack(Material.WOODEN_SWORD));
            p.getInventory().setItem(1, new ItemStack(Material.COBBLESTONE, 32));
            p.getInventory().setItem(2, new ItemStack(Material.COOKED_BEEF, 8));
            p.getInventory().setHelmet(new ItemStack(Material.LEATHER_HELMET));
            p.getInventory().setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
        }
    }

    // ═══════════════════════════════════════════
    //  TICK (every second)
    // ═══════════════════════════════════════════

    @Override
    public void onTick() {
        if (!gameStarted) return;
        gameTime++;

        updatePhase();
        handleEyeMovement();
        applyStormDamage();
        strikeLightningOutside();
        updateBossBar();
        showEyeDirection();
    }

    private void updatePhase() {
        int newPhase = 1;
        if (gameTime >= PHASE_4_TIME) newPhase = 4;
        else if (gameTime >= PHASE_3_TIME) newPhase = 3;
        else if (gameTime >= PHASE_2_TIME) newPhase = 2;

        if (newPhase != currentPhase) {
            currentPhase = newPhase;
            // Encoger el ojo
            double shrinkFactor = 0.75;
            eyeRadius = Math.max(EYE_MIN_RADIUS, eyeRadius * shrinkFactor);

            broadcastGame("§4§l⚠ FASE " + currentPhase + " §8— §cLa tormenta se intensifica.");
            broadcastGame("§7Ojo: radio §f" + String.format("%.0f", eyeRadius) + " §7bloques. Daño: §c" + String.format("%.1f", getStormDamage()) + "❤");
            titleAlive("§4§lFASE " + currentPhase, "§c¡La tormenta empeora!");
            soundAll(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.5f);
        }
    }

    private void handleEyeMovement() {
        // Countdown warning
        int timeToMove = nextEyeMoveAt - gameTime;

        if (timeToMove == 5) {
            broadcastGame("§e§l⚠ §eEl ojo se moverá en §f5 §esegundos...");
            soundAll(Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.5f);
        } else if (timeToMove == 3) {
            soundAll(Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.8f);
        } else if (timeToMove == 1) {
            soundAll(Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 2.0f);
        }

        if (gameTime >= nextEyeMoveAt) {
            moveEye();

            // El intervalo se reduce con las fases
            int interval = EYE_MOVE_INTERVAL;
            if (currentPhase >= 3) interval = 14;
            if (currentPhase >= 4) interval = 10;
            nextEyeMoveAt = gameTime + interval;
        }
    }

    private void moveEye() {
        // Elegir nueva posición (dentro del mapa, no demasiado lejos del centro)
        double maxRadius = Math.max(30, ARENA_RADIUS - 20 - (currentPhase * 10));
        double angle = random.nextDouble() * Math.PI * 2;
        double dist = random.nextDouble() * maxRadius;

        double newX = dist * Math.cos(angle);
        double newZ = dist * Math.sin(angle);

        // Limitar distancia de salto
        double jumpDist = Math.sqrt((newX - eyeX) * (newX - eyeX) + (newZ - eyeZ) * (newZ - eyeZ));
        if (jumpDist > EYE_MOVE_MAX_DIST) {
            double ratio = EYE_MOVE_MAX_DIST / jumpDist;
            newX = eyeX + (newX - eyeX) * ratio;
            newZ = eyeZ + (newZ - eyeZ) * ratio;
        }

        eyeX = newX;
        eyeZ = newZ;

        // Encoger un poco cada movimiento
        eyeRadius = Math.max(EYE_MIN_RADIUS, eyeRadius - 0.5);

        // Anunciar
        broadcastGame("§d§l⚡ §dEl OJO se ha movido. §7Radio: §f" + String.format("%.0f", eyeRadius));
        titleAlive("§d§l⚡ OJO MOVIDO", "§7¡Corre al nuevo punto!");
        soundAll(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.8f);

        // Trueno visual en la nueva posición
        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (world != null) {
            int groundY = getGroundY(world, (int) eyeX, (int) eyeZ);
            world.strikeLightningEffect(new Location(world, eyeX, groundY + 1, eyeZ));
        }

        // Speed boost para que corran al ojo
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 2, false, true));
        }
    }

    private void applyStormDamage() {
        double damage = getStormDamage();

        for (UUID uuid : new ArrayList<>(alivePlayers)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;

            double distToEye = horizontalDist(p.getLocation(), eyeX, eyeZ);

            if (distToEye > eyeRadius) {
                // FUERA DEL OJO: daño + efectos
                double extraDist = distToEye - eyeRadius;
                double scaledDamage = damage + (extraDist * 0.1); // Más lejos = más daño
                scaledDamage = Math.min(scaledDamage, 8.0); // Cap

                p.damage(scaledDamage);
                p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 0, false, false));

                // Sonido de tormenta personal
                if (gameTime % 3 == 0) {
                    p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.3f, 0.5f);
                }

                // Muerte por tormenta
                if (p.getHealth() <= 0 || p.isDead()) {
                    if (alivePlayers.contains(uuid)) {
                        eliminatePlayer(uuid);
                    }
                }
            }
        }
    }

    private void strikeLightningOutside() {
        if (gameTime % 4 != 0) return; // Cada 4 segundos

        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (world == null) return;

        // Rayos aleatorios en la zona de tormenta
        for (int i = 0; i < 2 + currentPhase; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = eyeRadius + 5 + random.nextDouble() * 40;
            double lx = eyeX + dist * Math.cos(angle);
            double lz = eyeZ + dist * Math.sin(angle);
            int ly = getGroundY(world, (int) lx, (int) lz) + 1;
            world.strikeLightningEffect(new Location(world, lx, ly, lz));
        }

        // Rayos dirigidos a jugadores fuera del ojo (fase 3+)
        if (currentPhase >= 3 && random.nextInt(3) == 0) {
            for (UUID uuid : alivePlayers) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null) continue;
                double distToEye = horizontalDist(p.getLocation(), eyeX, eyeZ);
                if (distToEye > eyeRadius + 10 && random.nextInt(3) == 0) {
                    world.strikeLightning(p.getLocation());
                }
            }
        }
    }

    private void showEyeDirection() {
        if (gameTime % 2 != 0) return;

        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;

            double distToEye = horizontalDist(p.getLocation(), eyeX, eyeZ);
            boolean inEye = distToEye <= eyeRadius;

            int timeToMove = nextEyeMoveAt - gameTime;
            String distStr = String.format("%.0f", distToEye);

            if (inEye) {
                com.moonlight.coreprotect.util.ActionBarUtil.send(p,
                        "§a§l✦ SEGURO §8| §7Ojo: §f" + distStr + "§7/" + String.format("%.0f", eyeRadius) +
                                " §8| §eMovimiento: §f" + timeToMove + "s");
            } else {
                com.moonlight.coreprotect.util.ActionBarUtil.send(p,
                        "§c§l⚠ TORMENTA §8| §7Distancia al ojo: §c" + distStr +
                                " §8| §eMovimiento: §f" + timeToMove + "s");
            }
        }
    }

    private double getStormDamage() {
        return STORM_DAMAGE_BASE + (currentPhase - 1) * STORM_DAMAGE_PER_PHASE;
    }

    // ═══════════════════════════════════════════
    //  VISUAL EFFECTS
    // ═══════════════════════════════════════════

    private void startEyeParticles() {
        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (world == null) return;

        particleTaskId = new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameStarted) { cancel(); return; }
                World w = Bukkit.getWorld(MiniGameWorld.getWorldName());
                if (w == null) { cancel(); return; }

                int groundY = getGroundY(w, (int) eyeX, (int) eyeZ);

                // Anillo del ojo (borde visible)
                for (int i = 0; i < 40; i++) {
                    double angle = (2 * Math.PI / 40) * i;
                    double px = eyeX + eyeRadius * Math.cos(angle);
                    double pz = eyeZ + eyeRadius * Math.sin(angle);
                    w.spawnParticle(Particle.END_ROD, px, groundY + 2, pz, 1, 0, 0.5, 0, 0.01);
                }

                // Columna de luz en el centro del ojo
                for (int y = 0; y < 15; y++) {
                    w.spawnParticle(Particle.END_ROD, eyeX, groundY + y, eyeZ, 2, 0.3, 0, 0.3, 0.01);
                }

                // Partículas de tormenta fuera del ojo
                for (int i = 0; i < 30; i++) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    double dist = eyeRadius + 5 + random.nextDouble() * 30;
                    double sx = eyeX + dist * Math.cos(angle);
                    double sz = eyeZ + dist * Math.sin(angle);
                    w.spawnParticle(Particle.SMOKE, sx, groundY + 1 + random.nextDouble() * 5, sz, 1, 1, 0.5, 1, 0.02);
                }

                // Lluvia de partículas grises en la tormenta (ambiente)
                for (UUID uuid : alivePlayers) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null) continue;
                    double distToEye = horizontalDist(p.getLocation(), eyeX, eyeZ);
                    if (distToEye > eyeRadius) {
                        Location loc = p.getLocation();
                        for (int i = 0; i < 5; i++) {
                            p.spawnParticle(Particle.SMOKE,
                                    loc.getX() + random.nextDouble() * 6 - 3,
                                    loc.getY() + 3 + random.nextDouble() * 3,
                                    loc.getZ() + random.nextDouble() * 6 - 3,
                                    1, 0, -0.3, 0, 0.05);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 4L).getTaskId();
    }

    private void startStormEffects() {
        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (world == null) return;

        // Tormenta constante
        world.setStorm(true);
        world.setThundering(true);
        world.setThunderDuration(999999);
        world.setWeatherDuration(999999);
        // Hacer noche para más tensión
        world.setTime(18000);

        stormEffectTaskId = new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameStarted) { cancel(); return; }
                World w = Bukkit.getWorld(MiniGameWorld.getWorldName());
                if (w != null) {
                    w.setTime(18000); // Mantener noche
                    w.setStorm(true);
                    w.setThundering(true);
                }
            }
        }.runTaskTimer(plugin, 100L, 100L).getTaskId();
    }

    // ═══════════════════════════════════════════
    //  BOSSBAR
    // ═══════════════════════════════════════════

    private void updateBossBar() {
        if (stormBar == null) return;

        int timeToMove = nextEyeMoveAt - gameTime;
        double progress = Math.max(0, Math.min(1, (double) timeToMove / EYE_MOVE_INTERVAL));
        stormBar.setProgress(progress);

        BarColor color = timeToMove <= 5 ? BarColor.RED : BarColor.PURPLE;
        stormBar.setColor(color);

        stormBar.setTitle("§8§l⛈ §7Fase §f" + currentPhase +
                " §8| §7Ojo: §f" + String.format("%.0f", eyeRadius) + " bloques" +
                " §8| §eMovimiento: §f" + timeToMove + "s" +
                " §8| §fVivos: §a" + alivePlayers.size());
    }

    // ═══════════════════════════════════════════
    //  CLEANUP
    // ═══════════════════════════════════════════

    @Override
    public void onCleanup() {
        gameStarted = false;

        if (particleTaskId != -1) {
            Bukkit.getScheduler().cancelTask(particleTaskId);
            particleTaskId = -1;
        }
        if (stormEffectTaskId != -1) {
            Bukkit.getScheduler().cancelTask(stormEffectTaskId);
            stormEffectTaskId = -1;
        }

        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (world != null) {
            world.setStorm(false);
            world.setThundering(false);
            world.setTime(6000);
        }

        if (stormBar != null) {
            stormBar.removeAll();
            stormBar.setVisible(false);
            stormBar = null;
        }
    }

    // ═══════════════════════════════════════════
    //  UTILITY
    // ═══════════════════════════════════════════

    private double horizontalDist(Location loc, double tx, double tz) {
        double dx = loc.getX() - tx;
        double dz = loc.getZ() - tz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public boolean isPvpGame() { return true; }
    public boolean allowsBlockPlace() { return true; }
    public boolean allowsBlockBreak() { return true; }
    public boolean allowsDrops() { return true; }
    public boolean allowsPickups() { return true; }
    public boolean allowsInventory() { return true; }
    public boolean allowsHunger() { return true; }
}
