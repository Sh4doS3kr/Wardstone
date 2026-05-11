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
    private static final int ARENA_RADIUS = 800;
    private static final int ARENA_Y = 70;
    private static final int BLOCKS_PER_TICK = 50; // Ultra lento para cero lag

    // === STORM (Fortnite-style shrinking border) ===
    private static final double STORM_INITIAL_RADIUS = 500.0; // Empieza MÁS GRANDE que el mapa
    private static final double STORM_MIN_RADIUS = 5.0;
    private static final int STORM_START = 60; // Segundos hasta que empieza a cerrar
    private static final double STORM_DAMAGE_BASE = 1.5;
    private static final double STORM_DAMAGE_PER_PHASE = 2.0;

    // === PHASES (storm shrinks in stages like Fortnite) ===
    // Fase 1: 0-60s = loot libre, borde enorme
    // Fase 2: 60-150s = borde cierra hasta r160
    // Fase 3: 150-240s = borde cierra hasta r80
    // Fase 4: 240-320s = borde cierra hasta r25
    // Fase 5: 320s+ = borde cierra hasta r5
    private static final int PHASE_2_TIME = 60;
    private static final int PHASE_3_TIME = 150;
    private static final int PHASE_4_TIME = 240;
    private static final int PHASE_5_TIME = 320;
    private static final int CRUSH_TIME = 440; // 320 + 120s (2 min en zona final) = aplastamiento

    // === STATE ===
    private BossBar stormBar;
    private final Random random = new Random();
    private boolean gameStarted = false;
    private boolean stormActive = false;
    private int currentPhase = 1;
    private double stormRadius = STORM_INITIAL_RADIUS;
    private double stormCenterX = 0;
    private double stormCenterZ = 0;
    private boolean crushStarted = false;
    private boolean graceActive = true; // 60s sin PvP para lootear

    // Tasks
    private int stormEffectTaskId = -1;
    private int stormSyncTaskId = -1;
    private int centerMoveTaskId = -1;

    // Callback cuando la arena termina de construirse
    private Runnable onArenaBuiltCallback = null;

    public EyeOfStormGame(CoreProtectPlugin plugin, MiniGameManager manager) {
        super(plugin, manager, MiniGameType.EYE_OF_STORM);
    }

    /**
     * Override start para manejar la construcción async del mapa gigante.
     * En vez de esperar un delay fijo (5s), espera a que buildArena termine via callback.
     */
    @Override
    public void start(Set<UUID> joinedPlayers) {
        this.players.addAll(joinedPlayers);
        this.alivePlayers.addAll(joinedPlayers);
        this.running = true;
        this.gameTime = 0;

        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (world == null) return;

        // Teletransportar jugadores a ubicación segura mientras se construye
        Location safeWaitLocation = new Location(world, 0.5, 120, 0.5);
        for (UUID uuid : joinedPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.teleport(safeWaitLocation);
                p.setGameMode(org.bukkit.GameMode.SPECTATOR);
                p.setAllowFlight(true);
                p.setFlying(true);
                p.sendMessage("§e§l⏳ §7Construyendo mapa gigante, espera...");
            }
        }

        // Configurar callback: cuando la arena termine de construirse, teleportar y empezar
        onArenaBuiltCallback = () -> {
            if (!running) return;

            List<Location> spawns = getSpawnLocations(world);
            cachedSpawns = spawns;
            for (Location spawn : spawns) {
                world.getChunkAt(spawn).load(true);
                world.getChunkAt(spawn).setForceLoaded(true);
            }
            world.getChunkAt(0, 0).load(true);
            world.getChunkAt(0, 0).setForceLoaded(true);

            // Teletransportar a spawns
            teleportPlayersToArena(world, spawns);
            onPreCountdown();

            // Safety TP retry
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (UUID uuid : players) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline() && !p.getWorld().getName().equals(MiniGameWorld.getWorldName())) {
                        int idx2 = new ArrayList<>(players).indexOf(uuid);
                        Location spawn = spawns.get(idx2 % spawns.size());
                        spawn.getChunk().load(true);
                        p.teleport(spawn);
                        p.setGameMode(org.bukkit.GameMode.SURVIVAL);
                        p.setFallDistance(0);
                    }
                }
            }, 20L);

            // Countdown de inicio (5 segundos)
            new BukkitRunnable() {
                int count = 5;
                @Override
                public void run() {
                    if (!running) { cancel(); return; }
                    if (count <= 0) {
                        cancel();
                        for (Location spawn : spawns) {
                            world.getChunkAt(spawn).setForceLoaded(false);
                        }
                        world.getChunkAt(0, 0).setForceLoaded(false);

                        for (UUID uuid : players) {
                            Player p = Bukkit.getPlayer(uuid);
                            if (p != null && p.isOnline()) {
                                p.sendTitle("§a§l¡GO!", "", 5, 20, 5);
                                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);
                            }
                        }
                        startGameLogic();
                        startTickTask();
                        return;
                    }

                    String color = count <= 2 ? "§c" : count <= 4 ? "§e" : "§a";
                    for (UUID uuid : players) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null && p.isOnline()) {
                            p.sendTitle(color + "§l" + count, "§7Prepárate...", 5, 15, 5);
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, count == 1 ? 2.0f : 1.0f);
                        }
                    }
                    count--;
                }
            }.runTaskTimer(plugin, 0L, 20L);
        };

        // Construir arena (cuando termine, se dispara onArenaBuiltCallback)
        buildArena(world);
    }

    // ═══════════════════════════════════════════════════════════
    //  ARENA: Mapa gigante con terreno variado (async)
    // ═══════════════════════════════════════════════════════════

    @Override
    public void buildArena(World world) {
        plugin.getLogger().info("[EyeOfStorm] Iniciando construcción de arena (radio=" + ARENA_RADIUS + ")...");

        // Fase 1: Cargar chunks en batches (no todo de golpe)
        // Fase 2: Construir terreno fila por fila
        // Fase 3: Colocar estructuras
        // Todo dentro del mismo BukkitRunnable progresivo

        final int chunkRadius = (ARENA_RADIUS / 16) + 2;
        final int totalChunkRows = chunkRadius * 2 + 1;

        new BukkitRunnable() {
            // Estado de la máquina de fases
            int phase = 0; // 0=chunks, 1=terreno, 2=estructuras
            // Fase 0: chunk loading
            int chunkRow = -chunkRadius;
            // Fase 1: terreno por filas
            int terrainX = -ARENA_RADIUS;
            // Fase 2: estructuras
            int structIndex = 0;
            boolean structsGenerated = false;
            List<Runnable> structTasks = null;

            @Override
            public void run() {
                long startTime = System.currentTimeMillis();

                // === FASE 0: Cargar chunks (4 filas de chunks por tick) ===
                if (phase == 0) {
                    int rowsDone = 0;
                    while (chunkRow <= chunkRadius && rowsDone < 4) {
                        for (int cz = -chunkRadius; cz <= chunkRadius; cz++) {
                            world.getChunkAt(chunkRow, cz).load(true);
                        }
                        chunkRow++;
                        rowsDone++;
                    }
                    if (chunkRow > chunkRadius) {
                        phase = 1;
                        plugin.getLogger().info("[EyeOfStorm] Chunks cargados. Generando terreno...");
                    }
                    return;
                }

                // === FASE 1: Terreno (varias filas X por tick) ===
                if (phase == 1) {
                    int rowsDone = 0;
                    while (terrainX <= ARENA_RADIUS && rowsDone < BLOCKS_PER_TICK) {
                        // Generar toda la fila Z para este X
                        for (int z = -ARENA_RADIUS; z <= ARENA_RADIUS; z++) {
                            double dist = Math.sqrt((double) terrainX * terrainX + (double) z * z);
                            if (dist > ARENA_RADIUS) continue;

                            double noise = Math.sin(terrainX * 0.05) * Math.cos(z * 0.05) * 4
                                    + Math.sin(terrainX * 0.12 + 3) * Math.cos(z * 0.08 + 1) * 3
                                    + Math.sin(terrainX * 0.02) * Math.sin(z * 0.02) * 6;
                            int height = ARENA_Y + (int) noise;
                            if (height < ARENA_Y - 6) height = ARENA_Y - 6;

                            double edgeDist = ARENA_RADIUS - dist;
                            if (edgeDist < 8) {
                                height -= (int) (8 - edgeDist);
                            }

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
                                world.getBlockAt(terrainX, y, z).setType(mat, false);
                            }
                        }
                        terrainX++;
                        rowsDone++;

                        // Protección: si llevamos >40ms en este tick, parar
                        if (System.currentTimeMillis() - startTime > 40) break;
                    }
                    if (terrainX > ARENA_RADIUS) {
                        phase = 2;
                        plugin.getLogger().info("[EyeOfStorm] Terreno completo. Generando estructuras...");
                    }
                    return;
                }

                // === FASE 2: Estructuras (pocas por tick) ===
                if (phase == 2) {
                    if (!structsGenerated) {
                        structTasks = new ArrayList<>();
                        // Ruinas (120)
                        for (int i = 0; i < 120; i++) {
                            double angle = random.nextDouble() * Math.PI * 2;
                            double d = 15 + random.nextDouble() * 750;
                            int sx = (int) (d * Math.cos(angle));
                            int sz = (int) (d * Math.sin(angle));
                            structTasks.add(() -> buildRuin(world, sx, sz));
                        }
                        // Casas (80)
                        for (int i = 0; i < 80; i++) {
                            double angle = random.nextDouble() * Math.PI * 2;
                            double d = 20 + random.nextDouble() * 750;
                            int sx = (int) (d * Math.cos(angle));
                            int sz = (int) (d * Math.sin(angle));
                            structTasks.add(() -> buildHouse(world, sx, sz));
                        }
                        // Torres (30)
                        for (int i = 0; i < 30; i++) {
                            double angle = (2 * Math.PI / 30) * i + random.nextDouble() * 0.5;
                            double d = 40 + random.nextDouble() * 700;
                            int sx = (int) (d * Math.cos(angle));
                            int sz = (int) (d * Math.sin(angle));
                            structTasks.add(() -> buildTower(world, sx, sz));
                        }
                        // Bunkers (50)
                        for (int i = 0; i < 50; i++) {
                            double angle = random.nextDouble() * Math.PI * 2;
                            double d = 25 + random.nextDouble() * 740;
                            int sx = (int) (d * Math.cos(angle));
                            int sz = (int) (d * Math.sin(angle));
                            structTasks.add(() -> buildBunker(world, sx, sz));
                        }
                        // Árboles (250)
                        for (int i = 0; i < 250; i++) {
                            double angle = random.nextDouble() * Math.PI * 2;
                            double d = 10 + random.nextDouble() * 770;
                            int sx = (int) (d * Math.cos(angle));
                            int sz = (int) (d * Math.sin(angle));
                            structTasks.add(() -> buildTree(world, sx, sz));
                        }
                        // Rocas (150)
                        for (int i = 0; i < 150; i++) {
                            double angle = random.nextDouble() * Math.PI * 2;
                            double d = 10 + random.nextDouble() * 770;
                            int sx = (int) (d * Math.cos(angle));
                            int sz = (int) (d * Math.sin(angle));
                            structTasks.add(() -> buildRockFormation(world, sx, sz));
                        }
                        // Muros (80)
                        for (int i = 0; i < 80; i++) {
                            double angle = random.nextDouble() * Math.PI * 2;
                            double d = 15 + random.nextDouble() * 750;
                            int sx = (int) (d * Math.cos(angle));
                            int sz = (int) (d * Math.sin(angle));
                            double wallAngle = random.nextDouble() * Math.PI;
                            structTasks.add(() -> buildBrokenWall(world, sx, sz, wallAngle));
                        }
                        // Cofres (250)
                        for (int i = 0; i < 250; i++) {
                            double angle = random.nextDouble() * Math.PI * 2;
                            double d = 10 + random.nextDouble() * 770;
                            int sx = (int) (d * Math.cos(angle));
                            int sz = (int) (d * Math.sin(angle));
                            structTasks.add(() -> placeChest(world, sx, sz));
                        }
                        structsGenerated = true;
                    }

                    int done = 0;
                    while (structIndex < structTasks.size() && done < 10) {
                        structTasks.get(structIndex).run();
                        structIndex++;
                        done++;
                        if (System.currentTimeMillis() - startTime > 40) break;
                    }
                    if (structIndex >= structTasks.size()) {
                        plugin.getLogger().info("[EyeOfStorm] Arena construida completamente (" + structTasks.size() + " estructuras).");
                        cancel();
                        if (onArenaBuiltCallback != null) {
                            Bukkit.getScheduler().runTask(plugin, onArenaBuiltCallback);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 2L);
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
        double spawnRadius = 350;

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
        stormActive = false;
        currentPhase = 1;
        stormRadius = STORM_INITIAL_RADIUS;
        stormCenterX = 0;
        stormCenterZ = 0;
        crushStarted = false;
        graceActive = true;

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

        // Iniciar WorldBorder amplio (más grande que el mapa)
        initStormBorder();

        // Kit inicial
        giveStartingKit();

        // Efectos de tormenta constantes
        startStormEffects();

        broadcastGame("§8§l⛈ §7La tormenta rodea la isla. §eLootea rápido antes de que se cierre.");
        broadcastGame("§8§l⛈ §7La tormenta empezará a cerrarse en §f" + STORM_START + "s§7.");
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

        // Grace period: primeros 60s sin PvP para lootear
        if (graceActive && gameTime >= STORM_START) {
            graceActive = false;
            broadcastGame("§c§l⚔ §f¡TIEMPO DE GRACIA TERMINADO! §7El PvP está activado.");
            titleAlive("§c§l⚔ PvP ACTIVADO", "§7¡Cuidado con los demás jugadores!");
            soundAll(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1.5f);
        }
        if (graceActive) {
            int timeLeft = STORM_START - gameTime;
            if (timeLeft == 30 || timeLeft == 15 || timeLeft == 10 || timeLeft == 5) {
                broadcastGame("§e§l⏳ §fPvP se activa en §c" + timeLeft + "s§f. ¡Lootea cofres!");
            }
        }

        updatePhase();
        applyStormDamage();
        strikeLightningOutside();
        checkCrush();
        updateBossBar();
        showActionBar();
    }

    private void updatePhase() {
        int newPhase = 1;
        if (gameTime >= PHASE_5_TIME) newPhase = 5;
        else if (gameTime >= PHASE_4_TIME) newPhase = 4;
        else if (gameTime >= PHASE_3_TIME) newPhase = 3;
        else if (gameTime >= PHASE_2_TIME) newPhase = 2;

        if (newPhase != currentPhase) {
            currentPhase = newPhase;

            // Calcular nuevo radio objetivo y duración del shrink
            double targetRadius;
            int shrinkDuration;
            switch (currentPhase) {
                case 2: targetRadius = 250; shrinkDuration = 80; break;   // 60-150s → cierra a 250
                case 3: targetRadius = 120; shrinkDuration = 70; break;   // 150-240s → cierra a 120
                case 4: targetRadius = 40; shrinkDuration = 60; break;    // 240-320s → cierra a 40
                case 5: targetRadius = STORM_MIN_RADIUS; shrinkDuration = 50; break; // 320+ → cierra a 5
                default: targetRadius = STORM_INITIAL_RADIUS; shrinkDuration = 90; break;
            }

            if (currentPhase >= 2) {
                stormActive = true;

                // Elegir nuevo centro ALEATORIO dentro de la zona segura actual
                double targetCX = stormCenterX;
                double targetCZ = stormCenterZ;
                double maxOffset = Math.max(0, stormRadius - targetRadius - 10);
                if (maxOffset > 0) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    double offset = random.nextDouble() * maxOffset;
                    targetCX = stormCenterX + offset * Math.cos(angle);
                    targetCZ = stormCenterZ + offset * Math.sin(angle);
                    // Asegurar que el nuevo centro + radio no se salga del mapa
                    double maxDist = ARENA_RADIUS - targetRadius - 5;
                    if (maxDist > 0) {
                        double distFromOrigin = Math.sqrt(targetCX * targetCX + targetCZ * targetCZ);
                        if (distFromOrigin > maxDist) {
                            double ratio = maxDist / distFromOrigin;
                            targetCX *= ratio;
                            targetCZ *= ratio;
                        }
                    }
                }

                // Mover el centro GRADUALMENTE durante el shrink (no teletransportar)
                startGradualCenterMove(targetCX, targetCZ, shrinkDuration);

                // Usar WorldBorder para el shrink suave del tamaño
                World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
                if (world != null) {
                    org.bukkit.WorldBorder border = world.getWorldBorder();
                    border.setSize(targetRadius * 2, shrinkDuration);
                }
            }

            broadcastGame("§4§l⚠ FASE " + currentPhase + " §8— §cLa tormenta se intensifica.");
            String dmgStr = String.format("%.1f", getStormDamage());
            if (currentPhase >= 2) {
                broadcastGame("§7El borde se cierra. §cDaño fuera: §f" + dmgStr + "❤/s");
            }
            titleAlive("§4§lFASE " + currentPhase, "§c¡La tormenta se cierra!");
            soundAll(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.5f);

            // Speed boost al cambiar de fase para que corran al centro
            if (currentPhase >= 2) {
                for (UUID uuid : alivePlayers) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1, false, true));
                    }
                }
            }
        }

        // Countdown warnings antes de la tormenta
        if (!stormActive) {
            int timeToStorm = PHASE_2_TIME - gameTime;
            if (timeToStorm == 10) {
                broadcastGame("§c§l⚠ §cLa tormenta empezará a cerrarse en §f10 §csegundos...");
                soundAll(Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
            } else if (timeToStorm == 5) {
                broadcastGame("§4§l⚠ §c§l5 SEGUNDOS...");
                soundAll(Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.5f);
            } else if (timeToStorm == 3 || timeToStorm == 2 || timeToStorm == 1) {
                soundAll(Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 2.0f);
            }
        }
    }

    /**
     * Mueve el centro del WorldBorder gradualmente (cada tick) desde la posición actual
     * hasta la posición objetivo durante la duración del shrink. Sin teletransportes.
     */
    private void startGradualCenterMove(double targetCX, double targetCZ, int durationSeconds) {
        // Cancelar movimiento anterior si existe
        if (centerMoveTaskId != -1) {
            Bukkit.getScheduler().cancelTask(centerMoveTaskId);
            centerMoveTaskId = -1;
        }

        final double startCX = stormCenterX;
        final double startCZ = stormCenterZ;
        final int totalTicks = durationSeconds * 20; // 20 ticks por segundo
        final double endCX = targetCX;
        final double endCZ = targetCZ;

        centerMoveTaskId = new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!gameStarted || tick >= totalTicks) {
                    // Asegurar posición final exacta
                    stormCenterX = endCX;
                    stormCenterZ = endCZ;
                    World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
                    if (world != null) {
                        world.getWorldBorder().setCenter(endCX, endCZ);
                    }
                    centerMoveTaskId = -1;
                    cancel();
                    return;
                }

                tick++;
                double progress = (double) tick / totalTicks;

                // Interpolación lineal
                stormCenterX = startCX + (endCX - startCX) * progress;
                stormCenterZ = startCZ + (endCZ - startCZ) * progress;

                World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
                if (world != null) {
                    world.getWorldBorder().setCenter(stormCenterX, stormCenterZ);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L).getTaskId();
    }

    /**
     * Si llevan +2 min en la zona final (fase 5), la tormenta aplasta:
     * borde se cierra a 0, daño masivo, y mata a todos si no terminan.
     */
    private void checkCrush() {
        if (currentPhase < 5 || crushStarted) return;
        if (gameTime < CRUSH_TIME) {
            // Warnings
            int timeLeft = CRUSH_TIME - gameTime;
            if (timeLeft == 30 || timeLeft == 15 || timeLeft == 10 || timeLeft == 5) {
                broadcastGame("§4§l☠ §c¡APLASTAMIENTO en §f" + timeLeft + "s§c! §lMATEN O MUERAN.");
                soundAll(Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.5f);
            }
            return;
        }

        crushStarted = true;
        broadcastGame("§4§l☠☠☠ APLASTAMIENTO FINAL ☠☠☠");
        broadcastGame("§c§lLa tormenta aplasta todo. Nadie sobrevive.");
        titleAlive("§4§l☠ APLASTAMIENTO", "§c§lLa tormenta te aplasta");
        soundAll(Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f);

        // Cerrar borde a 0 en 15 segundos
        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (world != null) {
            world.getWorldBorder().setSize(1, 15);
        }

        // Daño masivo creciente cada segundo hasta matar a todos
        new BukkitRunnable() {
            int crushTick = 0;
            @Override
            public void run() {
                if (!gameStarted || alivePlayers.size() <= 1) {
                    cancel();
                    return;
                }
                crushTick++;
                double crushDamage = 4.0 + crushTick * 2.0; // 6, 8, 10, 12... 
                for (UUID uuid : new ArrayList<>(alivePlayers)) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null || !p.isOnline()) continue;
                    p.damage(crushDamage);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 1, false, false));
                    if (crushTick % 2 == 0) {
                        p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.3f);
                    }
                }
                // A los 20s, matar a todos los que queden
                if (crushTick >= 20) {
                    for (UUID uuid : new ArrayList<>(alivePlayers)) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null && p.isOnline()) {
                            p.setHealth(0);
                        }
                        eliminatePlayer(uuid);
                    }
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // cada segundo
    }

    private void applyStormDamage() {
        if (!stormActive) return;
        double damage = getStormDamage();

        // Usar el WorldBorder REAL para determinar si están dentro o fuera
        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (world == null) return;
        org.bukkit.WorldBorder border = world.getWorldBorder();
        stormRadius = border.getSize() / 2.0;
        double centerX = border.getCenter().getX();
        double centerZ = border.getCenter().getZ();

        for (UUID uuid : new ArrayList<>(alivePlayers)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;

            Location loc = p.getLocation();

            // Usar isInside() del WorldBorder — coincide EXACTAMENTE con el borde visual
            if (!border.isInside(loc)) {
                // Calcular distancia fuera del borde (WorldBorder es cuadrado)
                double dx = Math.abs(loc.getX() - centerX);
                double dz = Math.abs(loc.getZ() - centerZ);
                double halfSize = stormRadius;
                double outsideX = Math.max(0, dx - halfSize);
                double outsideZ = Math.max(0, dz - halfSize);
                double extraDist = Math.max(outsideX, outsideZ);

                double scaledDamage = damage + (extraDist * 0.05);
                scaledDamage = Math.min(scaledDamage, 10.0);

                p.damage(scaledDamage);
                p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 0, false, false));

                // Sonido de tormenta personal
                if (gameTime % 3 == 0) {
                    p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.3f, 0.5f);
                }

                if (p.getHealth() <= 0 || p.isDead()) {
                    if (alivePlayers.contains(uuid)) {
                        eliminatePlayer(uuid);
                    }
                }
            }
        }
    }

    private void strikeLightningOutside() {
        if (!stormActive) return;
        if (gameTime % 4 != 0) return;

        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (world == null) return;

        // Rayos aleatorios fuera del borde
        int numLightning = 2 + currentPhase;
        for (int i = 0; i < numLightning; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = stormRadius + 5 + random.nextDouble() * 40;
            double lx = stormCenterX + dist * Math.cos(angle);
            double lz = stormCenterZ + dist * Math.sin(angle);
            if (Math.abs(lx) <= ARENA_RADIUS && Math.abs(lz) <= ARENA_RADIUS) {
                int ly = getGroundY(world, (int) lx, (int) lz) + 1;
                world.strikeLightningEffect(new Location(world, lx, ly, lz));
            }
        }

        // Rayos dirigidos a jugadores fuera del borde (fase 4+)
        if (currentPhase >= 4 && random.nextInt(3) == 0) {
            org.bukkit.WorldBorder border = world.getWorldBorder();
            for (UUID uuid : alivePlayers) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null) continue;
                if (!border.isInside(p.getLocation()) && random.nextInt(3) == 0) {
                    world.strikeLightning(p.getLocation());
                }
            }
        }
    }

    private void showActionBar() {
        if (gameTime % 2 != 0) return;

        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        org.bukkit.WorldBorder border = (world != null) ? world.getWorldBorder() : null;

        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;

            boolean safe = (border != null) ? border.isInside(p.getLocation()) : true;
            // Distancia al borde más cercano (cuadrado)
            double dx = Math.abs(p.getLocation().getX() - stormCenterX);
            double dz = Math.abs(p.getLocation().getZ() - stormCenterZ);
            double distToBorder = Math.min(stormRadius - dx, stormRadius - dz);
            if (distToBorder < 0) distToBorder = -distToBorder;
            String distStr = String.format("%.0f", distToBorder);
            String radiusStr = String.format("%.0f", stormRadius);

            if (stormActive) {
                String safeColor = safe ? "§a" : "§c";
                com.moonlight.coreprotect.util.ActionBarUtil.send(p,
                        "§4§l⛈ " + safeColor + (safe ? "SEGURO" : "¡TORMENTA!") +
                                " §8| §7Tu dist: " + safeColor + distStr +
                                " §8| §7Radio: §f" + radiusStr +
                                " §8| §fFase " + currentPhase);
            } else {
                int timeToStorm = PHASE_2_TIME - gameTime;
                com.moonlight.coreprotect.util.ActionBarUtil.send(p,
                        "§a§l✦ LOOTEA §8| §eTormenta en §f" + timeToStorm + "s" +
                                " §8| §7Radio actual: §f" + radiusStr +
                                " §8| §fVivos: §a" + alivePlayers.size());
            }
        }
    }

    private double getStormDamage() {
        return STORM_DAMAGE_BASE + (currentPhase - 1) * STORM_DAMAGE_PER_PHASE;
    }

    // ═══════════════════════════════════════════
    //  STORM BORDER (WorldBorder nativo del cliente)
    // ═══════════════════════════════════════════

    private void initStormBorder() {
        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (world == null) return;

        org.bukkit.WorldBorder border = world.getWorldBorder();
        border.setCenter(0, 0);
        border.setSize(STORM_INITIAL_RADIUS * 2); // diámetro, empieza MÁS GRANDE que el mapa
        border.setWarningDistance(10);
        border.setWarningTime(15);
        border.setDamageAmount(0); // Daño lo manejamos nosotros
        border.setDamageBuffer(0);
    }

    private void resetStormBorder() {
        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (world == null) return;
        org.bukkit.WorldBorder border = world.getWorldBorder();
        border.reset();
    }

    // ═══════════════════════════════════════════
    //  VISUAL EFFECTS
    // ═══════════════════════════════════════════

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
                    w.setTime(18000);
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

        if (stormActive) {
            double progress = Math.max(0, (stormRadius - STORM_MIN_RADIUS) / (STORM_INITIAL_RADIUS - STORM_MIN_RADIUS));
            stormBar.setProgress(Math.min(1.0, progress));
            stormBar.setColor(stormRadius < 30 ? BarColor.RED : BarColor.PURPLE);
            stormBar.setTitle("§4§l⛈ TORMENTA §8| §7Radio: §c" + String.format("%.0f", stormRadius) +
                    " §8| §7Fase §f" + currentPhase +
                    " §8| §7Daño: §c" + String.format("%.1f", getStormDamage()) + "❤" +
                    " §8| §fVivos: §a" + alivePlayers.size());
        } else {
            int timeToStorm = PHASE_2_TIME - gameTime;
            double progress = Math.max(0, (double) timeToStorm / PHASE_2_TIME);
            stormBar.setProgress(progress);
            stormBar.setColor(BarColor.PURPLE);
            stormBar.setTitle("§8§l⛈ §7Tormenta en §f" + timeToStorm + "s" +
                    " §8| §eLootea cofres y prepárate" +
                    " §8| §fVivos: §a" + alivePlayers.size());
        }
    }

    // ═══════════════════════════════════════════
    //  CLEANUP
    // ═══════════════════════════════════════════

    @Override
    public void onCleanup() {
        gameStarted = false;
        stormActive = false;

        if (stormEffectTaskId != -1) {
            Bukkit.getScheduler().cancelTask(stormEffectTaskId);
            stormEffectTaskId = -1;
        }
        if (stormSyncTaskId != -1) {
            Bukkit.getScheduler().cancelTask(stormSyncTaskId);
            stormSyncTaskId = -1;
        }
        if (centerMoveTaskId != -1) {
            Bukkit.getScheduler().cancelTask(centerMoveTaskId);
            centerMoveTaskId = -1;
        }

        resetStormBorder();

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

    private String formatTime(int secs) {
        int m = secs / 60;
        int s = secs % 60;
        return (m > 0 ? m + "m " : "") + s + "s";
    }

    public boolean isPvpGame() { return true; }
    public boolean allowsBlockPlace() { return true; }
    public boolean allowsBlockBreak() { return true; }
    public boolean allowsDrops() { return true; }
    public boolean allowsPickups() { return true; }
    public boolean allowsInventory() { return true; }
    public boolean allowsHunger() { return true; }
    public boolean isGraceActive() { return graceActive; }
}
