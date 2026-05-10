package com.moonlight.coreprotect.minigames.games;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.minigames.*;
import com.moonlight.coreprotect.util.ActionBarUtil;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * 🌑 BLACK HOLE — Agujero Negro
 *
 * Un agujero negro crece en el centro del mapa, atrayendo bloques y jugadores.
 * El mapa es una isla gigante con recursos, cofres y estructuras.
 * Los jugadores deben:
 *   - Lootear items (spawn periódico + cofres)
 *   - Pelear entre ellos (PvP completo)
 *   - Escapar de la gravedad del agujero que crece
 *
 * Fases:
 *   1. CALMA (0-45s):  Agujero pequeño, gravedad suave. Loot rápido.
 *   2. DESPERTAR (45-120s): Agujero crece, gravedad media. Bloques empiezan a ser arrancados.
 *   3. COLAPSO (120-210s): Agujero enorme, gravedad fuerte. Plataforma se destruye.
 *   4. SINGULARIDAD (210s+): Tormenta + gravedad extrema. Todo se consume.
 *
 * Efectos visuales:
 *   - Esfera de bloques negros (obsidiana/crying_obsidian) en el centro como agujero
 *   - Disco de acreción con partículas (soul_fire_flame, end_rod, dust)
 *   - Bloques arrancados del suelo con FallingBlock entities hacia el centro
 *   - Partículas de succión en espiral
 *   - Sonido ambiental creciente
 *   - WorldBorder como tormenta final
 *
 * Último en pie gana.
 */
public class BlackHoleGame extends MiniGame {

    // === ARENA ===
    private static final int ARENA_RADIUS = 100;
    private static final int ARENA_Y = 80;
    private static final int HOLE_CENTER_Y = ARENA_Y + 20; // El agujero flota sobre la arena

    // === BLACK HOLE PHASES ===
    private static final int PHASE_2_TIME = 45;
    private static final int PHASE_3_TIME = 120;
    private static final int PHASE_4_TIME = 210;

    // === GRAVITY ===
    private static final double GRAVITY_PHASE_1 = 0.08;
    private static final double GRAVITY_PHASE_2 = 0.18;
    private static final double GRAVITY_PHASE_3 = 0.35;
    private static final double GRAVITY_PHASE_4 = 0.55;

    // === HOLE SIZE (radius of the visual sphere) ===
    private static final double HOLE_SIZE_PHASE_1 = 3.0;
    private static final double HOLE_SIZE_PHASE_2 = 6.0;
    private static final double HOLE_SIZE_PHASE_3 = 10.0;
    private static final double HOLE_SIZE_PHASE_4 = 15.0;

    // === KILL RADIUS (sucked into the hole = death) ===
    private static final double KILL_RADIUS_PHASE_1 = 4.5;
    private static final double KILL_RADIUS_PHASE_2 = 8.0;
    private static final double KILL_RADIUS_PHASE_3 = 12.0;
    private static final double KILL_RADIUS_PHASE_4 = 18.0;

    // === STORM ===
    private static final int STORM_START = PHASE_4_TIME;
    private static final double STORM_INITIAL_RADIUS = 110.0;
    private static final double STORM_MIN_RADIUS = 10.0;
    private static final int STORM_SHRINK_DURATION = 150;
    private static final double STORM_DAMAGE = 4.0;

    // === ASYNC BUILD ===
    private static final int BLOCKS_PER_TICK = 5000; // bloques por tick para no lagear

    // === LOOT ===
    private static final int FIRST_LOOT_AT = 3;

    // === STATE ===
    private BossBar statusBar;
    private final Random random = new Random();
    private boolean gameStarted = false;
    private boolean stormActive = false;
    private double stormRadius = STORM_INITIAL_RADIUS;
    private double currentHoleSize = HOLE_SIZE_PHASE_1;
    private double currentKillRadius = KILL_RADIUS_PHASE_1;
    private double currentGravity = GRAVITY_PHASE_1;
    private int lootRound = 0;
    private int nextLootAt = FIRST_LOOT_AT;
    private int particleTaskId = -1;
    private int blockRipTaskId = -1;
    private int stormBorderTaskId = -1;
    private int ambientSoundTaskId = -1;

    // Tracking de bloques arrancados para no repetir
    private final Set<Long> rippedBlocks = new HashSet<>();
    private int lastPhaseAnnounced = 0;

    public BlackHoleGame(CoreProtectPlugin plugin, MiniGameManager manager) {
        super(plugin, manager, MiniGameType.BLACK_HOLE);
    }

    // ═══════════════════════════════════════════════════════════
    //  ARENA: Isla circular con estructuras, vegetación y recursos
    // ═══════════════════════════════════════════════════════════

    @Override
    public void buildArena(World world) {
        // Pre-cargar chunks necesarios
        int chunkRadius = (ARENA_RADIUS / 16) + 2;
        for (int cx = -chunkRadius; cx <= chunkRadius; cx++) {
            for (int cz = -chunkRadius; cz <= chunkRadius; cz++) {
                world.getChunkAt(cx, cz).load(true);
                world.getChunkAt(cx, cz).setForceLoaded(true);
            }
        }

        // Generar la arena de forma batch (BLOCKS_PER_TICK bloques por tick)
        List<Runnable> tasks = new ArrayList<>();
        int baseY = ARENA_Y;
        Random buildRng = new Random(42); // Seed fija para determinismo

        // === TAREA 1: Terreno base ===
        tasks.add(() -> {
            for (int x = -ARENA_RADIUS; x <= ARENA_RADIUS; x++) {
                for (int z = -ARENA_RADIUS; z <= ARENA_RADIUS; z++) {
                    double dist = Math.sqrt(x * x + z * z);
                    if (dist > ARENA_RADIUS) continue;

                    double heightMod = Math.sin(dist / ARENA_RADIUS * Math.PI) * 5;
                    double noise = Math.sin(x * 0.2) * Math.cos(z * 0.2) * 3
                            + Math.sin(x * 0.08 + z * 0.1) * 4
                            + Math.sin(x * 0.05 - z * 0.07) * 2;
                    int surfaceY = baseY + (int) (heightMod + noise);

                    for (int y = surfaceY - 6; y <= surfaceY; y++) {
                        Material mat;
                        if (y < surfaceY - 4) {
                            mat = Material.STONE;
                        } else if (y < surfaceY - 2) {
                            mat = Material.DEEPSLATE;
                        } else if (y < surfaceY) {
                            mat = Material.DIRT;
                        } else {
                            if (dist < 8) {
                                mat = ((x + z) % 2 == 0) ? Material.OBSIDIAN : Material.CRYING_OBSIDIAN;
                            } else if (dist < 20) {
                                mat = Material.PODZOL;
                            } else if (dist < ARENA_RADIUS - 5) {
                                mat = Material.GRASS_BLOCK;
                            } else {
                                mat = ((x + z) % 3 == 0) ? Material.COBBLESTONE : Material.STONE;
                            }
                        }
                        world.getBlockAt(x, y, z).setType(mat);
                    }
                    // Underneath: deepslate foundation
                    for (int y = surfaceY - 7; y >= surfaceY - 9; y--) {
                        world.getBlockAt(x, y, z).setType(Material.DEEPSLATE);
                    }
                }
            }
        });

        // === TAREA 2: Esfera + estructuras + decoración ===
        tasks.add(() -> {
            // Esfera visual del agujero negro
            buildHoleSphere(world, 3);

            // Anillo interior de crying obsidian
            for (int angle = 0; angle < 360; angle += 5) {
                double rad = Math.toRadians(angle);
                int rx = (int) (12 * Math.cos(rad));
                int rz = (int) (12 * Math.sin(rad));
                int ry = getHighestSolid(world, rx, rz, baseY + 15);
                if (ry > baseY - 5) {
                    world.getBlockAt(rx, ry, rz).setType(Material.CRYING_OBSIDIAN);
                }
            }

            // 10 ruinas en dos anillos
            for (int i = 0; i < 10; i++) {
                double angle = (2 * Math.PI / 10) * i;
                double ringDist = (i % 2 == 0) ? 40 : 65;
                int sx = (int) (ringDist * Math.cos(angle));
                int sz = (int) (ringDist * Math.sin(angle));
                int sy = getHighestSolid(world, sx, sz, baseY + 15) + 1;
                buildRuin(world, sx, sy, sz, i);
            }

            // 6 torres en el borde exterior
            for (int i = 0; i < 6; i++) {
                double angle = (2 * Math.PI / 6) * i + Math.PI / 6;
                int tx = (int) (85 * Math.cos(angle));
                int tz = (int) (85 * Math.sin(angle));
                int ty = getHighestSolid(world, tx, tz, baseY + 15) + 1;
                buildTower(world, tx, ty, tz);
            }

            // 8 pilares de End Stone con End Rod
            for (int i = 0; i < 8; i++) {
                double angle = (2 * Math.PI / 8) * i + Math.PI / 8;
                int px = (int) (55 * Math.cos(angle));
                int pz = (int) (55 * Math.sin(angle));
                int py = getHighestSolid(world, px, pz, baseY + 15) + 1;
                for (int y = py; y < py + 7; y++) {
                    world.getBlockAt(px, y, pz).setType(Material.END_STONE_BRICKS);
                }
                world.getBlockAt(px, py + 7, pz).setType(Material.END_ROD);
            }
        });

        // === TAREA 3: Árboles, cofres y detalles ===
        tasks.add(() -> {
            // 50 árboles muertos
            Random treeRng = new Random(123);
            for (int i = 0; i < 50; i++) {
                double angle = treeRng.nextDouble() * Math.PI * 2;
                double dist = 15 + treeRng.nextDouble() * 75;
                int tx = (int) (dist * Math.cos(angle));
                int tz = (int) (dist * Math.sin(angle));
                int ty = getHighestSolid(world, tx, tz, baseY + 15) + 1;
                if (ty > baseY - 5 && world.getBlockAt(tx, ty - 1, tz).getType() == Material.GRASS_BLOCK) {
                    buildDeadTree(world, tx, ty, tz);
                }
            }

            // 30 cofres de loot esparcidos
            Random chestRng = new Random(456);
            for (int i = 0; i < 30; i++) {
                double angle = chestRng.nextDouble() * Math.PI * 2;
                double dist = 15 + chestRng.nextDouble() * 80;
                int chx = (int) (dist * Math.cos(angle));
                int chz = (int) (dist * Math.sin(angle));
                int chy = getHighestSolid(world, chx, chz, baseY + 15) + 1;
                if (chy > baseY - 5) {
                    world.getBlockAt(chx, chy, chz).setType(Material.CHEST);
                    try {
                        org.bukkit.block.Chest chest = (org.bukkit.block.Chest) world.getBlockAt(chx, chy, chz).getState();
                        fillChest(chest);
                    } catch (Exception ignored) {}
                }
            }

            // Caminos de piedra entre estructuras
            for (int angle = 0; angle < 360; angle += 60) {
                double rad = Math.toRadians(angle);
                for (int d = 20; d < 80; d++) {
                    int px = (int) (d * Math.cos(rad));
                    int pz = (int) (d * Math.sin(rad));
                    int py = getHighestSolid(world, px, pz, baseY + 15);
                    if (py > baseY - 5) {
                        world.getBlockAt(px, py, pz).setType(Material.STONE_BRICKS);
                        if (world.getBlockAt(px + 1, py, pz).getType() == Material.GRASS_BLOCK)
                            world.getBlockAt(px + 1, py, pz).setType(Material.STONE_BRICK_SLAB);
                        if (world.getBlockAt(px - 1, py, pz).getType() == Material.GRASS_BLOCK)
                            world.getBlockAt(px - 1, py, pz).setType(Material.STONE_BRICK_SLAB);
                    }
                }
            }
        });

        // Ejecutar las tareas secuencialmente con delay entre ellas
        for (int i = 0; i < tasks.size(); i++) {
            final int index = i;
            Bukkit.getScheduler().runTaskLater(plugin, tasks.get(index), (long) i * 10L);
        }
    }

    private void buildTower(World world, int x, int y, int z) {
        // Torre 3x3 con escalera interior
        for (int dy = 0; dy < 8; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dx) == 1 || Math.abs(dz) == 1) {
                        world.getBlockAt(x + dx, y + dy, z + dz).setType(Material.STONE_BRICKS);
                    }
                }
            }
        }
        // Techo
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) + Math.abs(dz) <= 3) {
                    world.getBlockAt(x + dx, y + 8, z + dz).setType(Material.STONE_BRICK_SLAB);
                }
            }
        }
        // Cofre en la cima
        world.getBlockAt(x, y + 8, z).setType(Material.CHEST);
        try {
            org.bukkit.block.Chest chest = (org.bukkit.block.Chest) world.getBlockAt(x, y + 8, z).getState();
            fillChest(chest);
        } catch (Exception ignored) {}
        // Antorchas
        world.getBlockAt(x + 1, y + 6, z).setType(Material.SOUL_TORCH);
        world.getBlockAt(x - 1, y + 6, z).setType(Material.SOUL_TORCH);
    }

    private void buildHoleSphere(World world, int radius) {
        int cx = 0, cz = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + y * y + z * z <= radius * radius) {
                        Material mat;
                        if (x * x + y * y + z * z <= (radius - 1) * (radius - 1)) {
                            mat = Material.BLACK_CONCRETE;
                        } else {
                            mat = random.nextInt(3) == 0 ? Material.CRYING_OBSIDIAN : Material.OBSIDIAN;
                        }
                        world.getBlockAt(cx + x, HOLE_CENTER_Y + y, cz + z).setType(mat);
                    }
                }
            }
        }
    }

    private void growHoleSphere(World world, int newRadius, int oldRadius) {
        int cx = 0, cz = 0;
        for (int x = -newRadius; x <= newRadius; x++) {
            for (int y = -newRadius; y <= newRadius; y++) {
                for (int z = -newRadius; z <= newRadius; z++) {
                    double distSq = x * x + y * y + z * z;
                    if (distSq <= newRadius * newRadius && distSq > oldRadius * oldRadius) {
                        Material mat;
                        if (distSq <= (newRadius - 1) * (newRadius - 1)) {
                            mat = Material.BLACK_CONCRETE;
                        } else {
                            mat = random.nextInt(3) == 0 ? Material.CRYING_OBSIDIAN : Material.OBSIDIAN;
                        }
                        world.getBlockAt(cx + x, HOLE_CENTER_Y + y, cz + z).setType(mat);
                    }
                }
            }
        }
    }

    private void buildRuin(World world, int x, int y, int z, int variant) {
        Material wallMat = switch (variant % 3) {
            case 0 -> Material.STONE_BRICKS;
            case 1 -> Material.MOSSY_STONE_BRICKS;
            default -> Material.CRACKED_STONE_BRICKS;
        };

        // Muros parciales (ruinas)
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                    int height = 2 + random.nextInt(3);
                    for (int dy = 0; dy < height; dy++) {
                        if (random.nextInt(4) > 0) { // 75% de los bloques (aspecto roto)
                            world.getBlockAt(x + dx, y + dy, z + dz).setType(wallMat);
                        }
                    }
                }
            }
        }
        // Cofre en el centro de la ruina
        world.getBlockAt(x, y, z).setType(Material.CHEST);
        org.bukkit.block.Chest chest = (org.bukkit.block.Chest) world.getBlockAt(x, y, z).getState();
        fillChest(chest);

        // Antorchas en esquinas (si las paredes existen)
        if (random.nextBoolean()) world.getBlockAt(x + 2, y + 2, z).setType(Material.SOUL_TORCH);
        if (random.nextBoolean()) world.getBlockAt(x - 2, y + 2, z).setType(Material.SOUL_TORCH);
    }

    private void buildDeadTree(World world, int x, int y, int z) {
        int height = 3 + random.nextInt(4);
        for (int dy = 0; dy < height; dy++) {
            world.getBlockAt(x, y + dy, z).setType(Material.STRIPPED_DARK_OAK_LOG);
        }
        // Ramas
        if (height > 3) {
            int branchY = y + height - 2;
            int bx = random.nextBoolean() ? 1 : -1;
            int bz = random.nextBoolean() ? 1 : -1;
            world.getBlockAt(x + bx, branchY, z).setType(Material.STRIPPED_DARK_OAK_LOG);
            world.getBlockAt(x, branchY, z + bz).setType(Material.STRIPPED_DARK_OAK_LOG);
        }
    }

    private void fillChest(org.bukkit.block.Chest chest) {
        org.bukkit.inventory.Inventory inv = chest.getInventory();
        int items = 2 + random.nextInt(4);
        for (int i = 0; i < items; i++) {
            int slot = random.nextInt(inv.getSize());
            inv.setItem(slot, getRandomLootItem());
        }
    }

    private int getHighestSolid(World world, int x, int z, int maxY) {
        for (int y = maxY; y > 50; y--) {
            if (world.getBlockAt(x, y, z).getType().isSolid()) return y;
        }
        return ARENA_Y;
    }

    // ═══════════════════════════════════════════
    //  SPAWN LOCATIONS
    // ═══════════════════════════════════════════

    @Override
    public List<Location> getSpawnLocations(World world) {
        List<Location> spawns = new ArrayList<>();
        int maxPlayers = 16;
        double spawnRadius = 70;

        for (int i = 0; i < maxPlayers; i++) {
            double angle = (2 * Math.PI / maxPlayers) * i;
            int sx = (int) (spawnRadius * Math.cos(angle));
            int sz = (int) (spawnRadius * Math.sin(angle));
            int sy = getHighestSolid(world, sx, sz, ARENA_Y + 15) + 1;

            Location spawn = new Location(world, sx + 0.5, sy, sz + 0.5);
            spawn.setYaw((float) Math.toDegrees(-angle + Math.PI)); // Mirar al centro
            spawns.add(spawn);
        }
        return spawns;
    }

    // ═══════════════════════════════════════════
    //  PRE-COUNTDOWN
    // ═══════════════════════════════════════════

    @Override
    protected void onPreCountdown() {
        // Nothing special needed
    }

    // ═══════════════════════════════════════════
    //  GAME START
    // ═══════════════════════════════════════════

    @Override
    public void startGameLogic() {
        gameStarted = true;

        statusBar = Bukkit.createBossBar("§8§l🌑 AGUJERO NEGRO §8§l🌑", BarColor.PURPLE, BarStyle.SEGMENTED_10);
        statusBar.setProgress(1.0);
        statusBar.setVisible(true);
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) statusBar.addPlayer(p);
        }

        broadcastGame("");
        broadcastGame("§8§l🌑 §5§lAGUJERO NEGRO §8§l🌑");
        broadcastGame("§7Un agujero negro ha aparecido en el centro del mapa.");
        broadcastGame("§7¡§eLootea§7, §cpelea §7y §bescape de la gravedad§7!");
        broadcastGame("§4§l⚠ §cEl agujero crece. Si te atrapa, mueres.");
        broadcastGame("");

        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            p.getInventory().clear();
            // Kit inicial: espada de piedra + comida
            p.getInventory().setItem(0, new ItemStack(Material.STONE_SWORD));
            p.getInventory().setItem(1, new ItemStack(Material.COOKED_BEEF, 5));
            p.getInventory().setItem(2, new ItemStack(Material.OAK_PLANKS, 16));
        }

        startParticleEffects();
        startBlockRipping();
        startAmbientSound();
    }

    // ═══════════════════════════════════════════
    //  TICK (cada segundo)
    // ═══════════════════════════════════════════

    @Override
    public void onTick() {
        if (!gameStarted) return;

        int phase = getCurrentPhase();

        // === Actualizar parámetros según fase ===
        updatePhaseParams(phase);

        // === Anunciar cambio de fase ===
        if (phase != lastPhaseAnnounced) {
            lastPhaseAnnounced = phase;
            announcePhase(phase);
        }

        // === Gravedad: atraer jugadores hacia el centro ===
        applyGravity();

        // === Kill radius: jugadores demasiado cerca del agujero mueren ===
        checkKillRadius();

        // === Caída al vacío ===
        for (UUID uuid : new ArrayList<>(alivePlayers)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;
            if (p.getLocation().getY() < 40) {
                eliminatePlayer(uuid);
            }
        }

        // === TORMENTA (Fase 4) ===
        if (gameTime == STORM_START) {
            stormActive = true;
            stormRadius = STORM_INITIAL_RADIUS;
            startStormBorder();
        }

        if (stormActive) {
            int stormTime = gameTime - STORM_START;
            double shrinkFraction = Math.min(1.0, (double) stormTime / STORM_SHRINK_DURATION);
            stormRadius = STORM_INITIAL_RADIUS - (STORM_INITIAL_RADIUS - STORM_MIN_RADIUS) * shrinkFraction;

            for (UUID uuid : new ArrayList<>(alivePlayers)) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline()) continue;
                double dist = horizontalDist(p.getLocation());
                if (dist > stormRadius) {
                    p.damage(STORM_DAMAGE);
                    p.playSound(p.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 0.5f);
                }
            }
        }

        // === Loot periódico ===
        if (gameTime >= nextLootAt) {
            lootRound++;
            deliverLoot();
            nextLootAt = gameTime + getLootInterval();
        }

        // === BossBar ===
        updateBossBar();

        // === ActionBar ===
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            double dist = horizontalDist(p.getLocation());
            int nextIn = nextLootAt - gameTime;
            String dangerColor = dist < currentKillRadius + 5 ? "§c§l" : dist < currentKillRadius + 15 ? "§e" : "§a";

            if (stormActive) {
                String stormColor = dist > stormRadius ? "§c" : "§a";
                ActionBarUtil.send(p, "§8§l🌑 " + dangerColor + "Dist: " + String.format("%.0f", dist)
                        + " §8| " + stormColor + "Tormenta: " + String.format("%.0f", stormRadius)
                        + " §8| §eItems: §f" + nextIn + "s");
            } else {
                int timeToStorm = STORM_START - gameTime;
                ActionBarUtil.send(p, "§8§l🌑 " + dangerColor + "Dist: " + String.format("%.0f", dist)
                        + " §8| §fFase " + phase + " §8| §eItems: §f" + nextIn + "s §8| §cTormenta: §f" + formatTime(timeToStorm));
            }
        }

        // === Info periódica ===
        if (gameTime % 30 == 0 && gameTime > 0 && !stormActive) {
            broadcastGame("§8§l🌑 §fTiempo: §e" + formatTime(gameTime)
                    + " §8| §fVivos: §a" + alivePlayers.size()
                    + " §8| §fFase: §5" + getPhaseNameShort(phase));
        }
    }

    // ═══════════════════════════════════════════
    //  PHASE MANAGEMENT
    // ═══════════════════════════════════════════

    private int getCurrentPhase() {
        if (gameTime < PHASE_2_TIME) return 1;
        if (gameTime < PHASE_3_TIME) return 2;
        if (gameTime < PHASE_4_TIME) return 3;
        return 4;
    }

    private void updatePhaseParams(int phase) {
        switch (phase) {
            case 1 -> { currentGravity = GRAVITY_PHASE_1; currentKillRadius = KILL_RADIUS_PHASE_1; currentHoleSize = HOLE_SIZE_PHASE_1; }
            case 2 -> { currentGravity = GRAVITY_PHASE_2; currentKillRadius = KILL_RADIUS_PHASE_2; currentHoleSize = HOLE_SIZE_PHASE_2; }
            case 3 -> { currentGravity = GRAVITY_PHASE_3; currentKillRadius = KILL_RADIUS_PHASE_3; currentHoleSize = HOLE_SIZE_PHASE_3; }
            case 4 -> { currentGravity = GRAVITY_PHASE_4; currentKillRadius = KILL_RADIUS_PHASE_4; currentHoleSize = HOLE_SIZE_PHASE_4; }
        }

        // Crecer la esfera gradualmente en cada fase
        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (world != null) {
            int targetSphereRadius = (int) currentHoleSize;
            // Solo crecer, nunca reducir
            if (gameTime == PHASE_2_TIME || gameTime == PHASE_3_TIME || gameTime == PHASE_4_TIME) {
                int oldRadius = switch (phase) {
                    case 2 -> (int) HOLE_SIZE_PHASE_1;
                    case 3 -> (int) HOLE_SIZE_PHASE_2;
                    case 4 -> (int) HOLE_SIZE_PHASE_3;
                    default -> 2;
                };
                growHoleSphere(world, targetSphereRadius, oldRadius);
            }
        }
    }

    private void announcePhase(int phase) {
        switch (phase) {
            case 1 -> {
                // Ya anunciado en startGameLogic
            }
            case 2 -> {
                broadcastGame("");
                broadcastGame("§5§l⚡ FASE 2: §dDESPERTAR §5§l⚡");
                broadcastGame("§7El agujero negro §ccrece§7. La gravedad §eaumenta§7.");
                broadcastGame("§7¡Los bloques empiezan a ser §4arrancados§7!");
                broadcastGame("");
                titleAlive("§5§l⚡ DESPERTAR", "§dEl agujero crece...");
                soundAll(Sound.ENTITY_WITHER_SPAWN, 0.7f, 0.5f);
            }
            case 3 -> {
                broadcastGame("");
                broadcastGame("§4§l💀 FASE 3: §cCOLAPSO §4§l💀");
                broadcastGame("§7La isla se §4desmorona§7. ¡La gravedad es §c§lBRUTAL§7!");
                broadcastGame("§7Bloques enteros §4vuelan §7hacia el agujero.");
                broadcastGame("");
                titleAlive("§4§l💀 COLAPSO", "§c¡La isla se destruye!");
                soundAll(Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.3f);
                if (statusBar != null) statusBar.setColor(BarColor.RED);
            }
            case 4 -> {
                broadcastGame("");
                broadcastGame("§0§l☠ FASE 4: §8§lSINGULARIDAD §0§l☠");
                broadcastGame("§4§l¡¡NADA ESCAPA!! §7¡Tormenta + gravedad extrema!");
                broadcastGame("§7El agujero §4§lCONSUME TODO§7. §c¡Sobrevive!");
                broadcastGame("");
                titleAlive("§0§l☠ SINGULARIDAD", "§4¡NADA ESCAPA!");
                soundAll(Sound.ENTITY_WITHER_DEATH, 1.0f, 0.3f);
                if (statusBar != null) statusBar.setColor(BarColor.WHITE);
            }
        }
    }

    // ═══════════════════════════════════════════
    //  GRAVITY ENGINE
    // ═══════════════════════════════════════════

    private void applyGravity() {
        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (world == null) return;
        Location center = new Location(world, 0, HOLE_CENTER_Y, 0);

        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;

            Vector toCenter = center.toVector().subtract(p.getLocation().toVector());
            double dist = toCenter.length();
            if (dist < 0.5) dist = 0.5;

            // Gravedad potente: base + componente inversa a la distancia
            // Cerca: fuerza brutal. Lejos: fuerza suave pero constante.
            double closeBoost = Math.max(0, 50.0 / (dist * dist)); // Muy fuerte cerca
            double farPull = currentGravity * 0.5; // Siempre tira un poco
            double strength = currentGravity * closeBoost + farPull;

            // Clamp para que no sea demasiado brutal y vueles atravesando todo
            strength = Math.min(strength, 2.5);

            Vector pull = toCenter.normalize().multiply(strength);
            p.setVelocity(p.getVelocity().add(pull));
        }
    }

    private void checkKillRadius() {
        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (world == null) return;
        Location center = new Location(world, 0, HOLE_CENTER_Y, 0);

        for (UUID uuid : new ArrayList<>(alivePlayers)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;

            // 3D distance al centro del agujero
            double dist = p.getLocation().toVector().distance(center.toVector());
            // También chequear distancia horizontal para jugadores que estén a la misma Y
            double hDist = horizontalDist(p.getLocation());
            double effectiveDist = Math.min(dist, hDist + Math.abs(p.getLocation().getY() - HOLE_CENTER_Y) * 0.5);

            if (effectiveDist <= currentKillRadius) {
                // Sucked into the black hole!
                p.sendTitle("§0§l☠", "§8Absorbido por el agujero negro", 5, 40, 10);
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.1f);

                // Efecto visual: partículas de muerte
                world.spawnParticle(Particle.LARGE_SMOKE, p.getLocation(), 50, 1, 1, 1, 0.1);
                world.spawnParticle(Particle.PORTAL, p.getLocation(), 100, 1, 1, 1, 1);

                eliminatePlayer(uuid);
            }
        }
    }

    // ═══════════════════════════════════════════
    //  VISUAL EFFECTS: Partículas del agujero negro
    // ═══════════════════════════════════════════

    private void startParticleEffects() {
        particleTaskId = new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!gameStarted) { cancel(); return; }
                World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
                if (world == null) return;

                tick++;
                double holeSz = currentHoleSize;

                // === DISCO DE ACRECIÓN (anillo giratorio de partículas) ===
                double ringRadius = holeSz + 3;
                int ringPoints = 40 + (int) (holeSz * 5);
                double spinOffset = tick * 0.15;

                for (int i = 0; i < ringPoints; i++) {
                    double angle = spinOffset + (2 * Math.PI / ringPoints) * i;
                    // Anillo inclinado (plano XZ con ligera inclinación)
                    double rx = ringRadius * Math.cos(angle);
                    double ry = Math.sin(angle * 2 + tick * 0.1) * 1.5; // Ondulación vertical
                    double rz = ringRadius * Math.sin(angle);

                    Location particleLoc = new Location(world, rx, HOLE_CENTER_Y + ry, rz);

                    if (i % 3 == 0) {
                        world.spawnParticle(Particle.SOUL_FIRE_FLAME, particleLoc, 1, 0.1, 0.1, 0.1, 0);
                    } else if (i % 3 == 1) {
                        world.spawnParticle(Particle.END_ROD, particleLoc, 1, 0.1, 0.1, 0.1, 0);
                    } else {
                        world.spawnParticle(Particle.DRAGON_BREATH, particleLoc, 1, 0.1, 0.1, 0.1, 0);
                    }
                }

                // === ESPIRAL DE SUCCIÓN (partículas cayendo hacia el centro) ===
                int spiralArms = 3;
                for (int arm = 0; arm < spiralArms; arm++) {
                    double armOffset = (2 * Math.PI / spiralArms) * arm;
                    for (int j = 0; j < 20; j++) {
                        double t = j / 20.0;
                        double spiralRadius = (holeSz + 10) * (1 - t);
                        double spiralAngle = armOffset + t * Math.PI * 4 + tick * 0.2;

                        double sx = spiralRadius * Math.cos(spiralAngle);
                        double sz = spiralRadius * Math.sin(spiralAngle);
                        double sy = HOLE_CENTER_Y + (1 - t) * 3 - t * 2;

                        Location sLoc = new Location(world, sx, sy, sz);
                        world.spawnParticle(Particle.PORTAL, sLoc, 1, 0.05, 0.05, 0.05, 0);
                    }
                }

                // === AURA OSCURA alrededor del agujero ===
                for (int i = 0; i < 10; i++) {
                    double px = (random.nextDouble() - 0.5) * holeSz * 3;
                    double py = (random.nextDouble() - 0.5) * holeSz * 3;
                    double pz = (random.nextDouble() - 0.5) * holeSz * 3;
                    Location auraLoc = new Location(world, px, HOLE_CENTER_Y + py, pz);
                    world.spawnParticle(Particle.LARGE_SMOKE, auraLoc, 1, 0, 0, 0, 0.02);
                }

                // === PULSO periódico (cada 3 segundos) ===
                if (tick % 60 == 0) { // Cada 3 segundos (~60 ticks a 20tps)
                    double pulseRadius = holeSz + 8;
                    for (int i = 0; i < 60; i++) {
                        double angle = (2 * Math.PI / 60) * i;
                        double px = pulseRadius * Math.cos(angle);
                        double pz = pulseRadius * Math.sin(angle);
                        Location pLoc = new Location(world, px, HOLE_CENTER_Y, pz);
                        world.spawnParticle(Particle.SONIC_BOOM, pLoc, 1, 0, 0, 0, 0);
                    }
                }

                // === Partículas de succión desde el suelo hacia el agujero ===
                if (tick % 5 == 0) {
                    for (int i = 0; i < 8; i++) {
                        double angle = random.nextDouble() * Math.PI * 2;
                        double dist = holeSz + 5 + random.nextDouble() * 20;
                        double px = dist * Math.cos(angle);
                        double pz = dist * Math.sin(angle);
                        int py = getHighestSolid(world, (int) px, (int) pz, ARENA_Y + 10);

                        if (py > 50) {
                            Location from = new Location(world, px, py + 1, pz);
                            // Partícula que "vuela" hacia el centro
                            Vector dir = new Location(world, 0, HOLE_CENTER_Y, 0).toVector()
                                    .subtract(from.toVector()).normalize().multiply(0.5);
                            world.spawnParticle(Particle.LARGE_SMOKE, from, 1,
                                    dir.getX(), dir.getY(), dir.getZ(), 0.05);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L).getTaskId(); // Cada tick para fluidez
    }

    // ═══════════════════════════════════════════
    //  BLOCK RIPPING: Arrancar bloques del suelo
    // ═══════════════════════════════════════════

    private void startBlockRipping() {
        blockRipTaskId = new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameStarted) { cancel(); return; }
                World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
                if (world == null) return;

                int phase = getCurrentPhase();
                if (phase < 2) return; // No arrancar bloques en fase 1

                int blocksToRip = switch (phase) {
                    case 2 -> 4;
                    case 3 -> 10;
                    case 4 -> 20;
                    default -> 0;
                };

                double maxRipDistance = switch (phase) {
                    case 2 -> 50;
                    case 3 -> 80;
                    case 4 -> ARENA_RADIUS;
                    default -> 0;
                };

                for (int i = 0; i < blocksToRip; i++) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    double dist = 8 + random.nextDouble() * maxRipDistance;
                    int bx = (int) (dist * Math.cos(angle));
                    int bz = (int) (dist * Math.sin(angle));
                    int by = getHighestSolid(world, bx, bz, ARENA_Y + 10);

                    if (by < 50) continue;

                    long key = ((long) bx << 32) | (bz & 0xFFFFFFFFL);
                    if (rippedBlocks.contains(key)) continue;

                    org.bukkit.block.Block block = world.getBlockAt(bx, by, bz);
                    if (block.getType() == Material.AIR || block.getType() == Material.CHEST) continue;
                    if (block.getType() == Material.BLACK_CONCRETE || block.getType() == Material.OBSIDIAN
                            || block.getType() == Material.CRYING_OBSIDIAN) continue;

                    Material ripMat = block.getType();
                    block.setType(Material.AIR);
                    rippedBlocks.add(key);

                    // Spawn FallingBlock volando hacia el agujero
                    try {
                        org.bukkit.entity.FallingBlock fb = world.spawnFallingBlock(
                                block.getLocation().add(0.5, 0.5, 0.5), ripMat.createBlockData());
                        fb.setDropItem(false);
                        fb.setHurtEntities(false);

                        // Velocidad hacia el centro del agujero
                        Vector toCenter = new Location(world, 0, HOLE_CENTER_Y, 0).toVector()
                                .subtract(fb.getLocation().toVector());
                        double d = toCenter.length();
                        if (d > 0) {
                            fb.setVelocity(toCenter.normalize().multiply(0.4 + phase * 0.15));
                        }

                        // Remover after 5 seconds
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (fb.isValid()) fb.remove();
                        }, 100L);
                    } catch (Exception ignored) {
                        // Algunos materiales no soportan FallingBlock
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 10L).getTaskId(); // Cada 0.5 segundos
    }

    // ═══════════════════════════════════════════
    //  AMBIENT SOUND
    // ═══════════════════════════════════════════

    private void startAmbientSound() {
        ambientSoundTaskId = new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameStarted) { cancel(); return; }
                int phase = getCurrentPhase();

                for (UUID uuid : alivePlayers) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null) continue;
                    double dist = horizontalDist(p.getLocation());

                    // Sonido ambiente que se intensifica al acercarse
                    float volume = (float) Math.max(0.1, Math.min(1.0, 1.0 - dist / 50.0));
                    float pitch = switch (phase) {
                        case 1 -> 1.5f;
                        case 2 -> 1.0f;
                        case 3 -> 0.6f;
                        default -> 0.3f;
                    };

                    p.playSound(new Location(p.getWorld(), 0, HOLE_CENTER_Y, 0),
                            Sound.BLOCK_PORTAL_AMBIENT, volume * 0.5f, pitch);

                    // Latido cardíaco cuando están cerca
                    if (dist < currentKillRadius + 10) {
                        p.playSound(p.getLocation(), Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.3f, 0.5f);
                    }
                }
            }
        }.runTaskTimer(plugin, 40L, 40L).getTaskId(); // Cada 2 segundos
    }

    // ═══════════════════════════════════════════
    //  LOOT DELIVERY
    // ═══════════════════════════════════════════

    private int getLootInterval() {
        int phase = getCurrentPhase();
        return switch (phase) {
            case 1 -> 6;
            case 2 -> 5;
            case 3 -> 4;
            default -> 3;
        };
    }

    private void deliverLoot() {
        soundAll(Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.5f);
        int phase = getCurrentPhase();

        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;

            ItemStack item = getRandomLootItem();
            // En fases avanzadas, dar items mejores
            if (phase >= 3 && random.nextInt(3) == 0) {
                item = getEpicLootItem();
            }
            if (phase >= 4 && random.nextInt(2) == 0) {
                item = getLegendaryLootItem();
            }

            p.getInventory().addItem(item);
        }

        // Eventos especiales periódicos
        if (lootRound % 8 == 0 && lootRound > 0) {
            int event = random.nextInt(3);
            switch (event) {
                case 0 -> {
                    broadcastGame("§5§l⚡ §d¡LLUVIA DE BLOQUES! §7Todos reciben bloques extra.");
                    soundAll(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                    for (UUID uuid : alivePlayers) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) {
                            p.getInventory().addItem(new ItemStack(Material.OAK_PLANKS, 16));
                            p.getInventory().addItem(new ItemStack(Material.COBBLESTONE, 16));
                        }
                    }
                }
                case 1 -> {
                    broadcastGame("§4§l💀 §c¡PULSO GRAVITATORIO! §7¡Atracción masiva durante 3 segundos!");
                    soundAll(Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 0.5f);
                    // Pulso: gravedad x3 durante 3 segundos
                    double savedGravity = currentGravity;
                    currentGravity *= 3;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> currentGravity = savedGravity, 60L);
                }
                case 2 -> {
                    broadcastGame("§6§l✦ §e¡BONANZA! §7Items épicos para todos.");
                    soundAll(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.5f);
                    for (UUID uuid : alivePlayers) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) {
                            p.getInventory().addItem(getEpicLootItem());
                            p.getInventory().addItem(getEpicLootItem());
                        }
                    }
                }
            }
        }
    }

    private ItemStack getRandomLootItem() {
        ItemStack[] pool = {
            // Bloques
            new ItemStack(Material.OAK_PLANKS, 4),
            new ItemStack(Material.OAK_PLANKS, 4),
            new ItemStack(Material.COBBLESTONE, 4),
            new ItemStack(Material.COBBLESTONE, 4),
            new ItemStack(Material.DIRT, 4),
            new ItemStack(Material.STONE, 4),
            new ItemStack(Material.SANDSTONE, 4),
            new ItemStack(Material.BIRCH_PLANKS, 4),
            new ItemStack(Material.GLASS, 4),
            // Armas
            new ItemStack(Material.WOODEN_SWORD),
            new ItemStack(Material.STONE_SWORD),
            new ItemStack(Material.BOW),
            new ItemStack(Material.ARROW, 3),
            new ItemStack(Material.ARROW, 3),
            new ItemStack(Material.CROSSBOW),
            new ItemStack(Material.FISHING_ROD),
            new ItemStack(Material.SNOWBALL, 4),
            new ItemStack(Material.EGG, 4),
            // Armadura
            new ItemStack(Material.LEATHER_HELMET),
            new ItemStack(Material.LEATHER_BOOTS),
            new ItemStack(Material.IRON_BOOTS),
            // Comida
            new ItemStack(Material.COOKED_BEEF, 3),
            new ItemStack(Material.BREAD, 3),
            new ItemStack(Material.GOLDEN_APPLE),
            // Utilidad
            new ItemStack(Material.SHIELD),
            new ItemStack(Material.ENDER_PEARL),
            new ItemStack(Material.COBWEB, 2),
            new ItemStack(Material.LADDER, 4),
        };
        return pool[random.nextInt(pool.length)].clone();
    }

    private ItemStack getEpicLootItem() {
        ItemStack[] pool = {
            new ItemStack(Material.IRON_SWORD),
            new ItemStack(Material.DIAMOND_SWORD),
            new ItemStack(Material.IRON_HELMET),
            new ItemStack(Material.IRON_CHESTPLATE),
            new ItemStack(Material.DIAMOND_BOOTS),
            new ItemStack(Material.ENDER_PEARL, 2),
            new ItemStack(Material.TNT, 3),
            new ItemStack(Material.FLINT_AND_STEEL),
            new ItemStack(Material.GOLDEN_APPLE, 2),
            new ItemStack(Material.TRIDENT),
            makePotion(Material.SPLASH_POTION, PotionEffectType.INSTANT_DAMAGE, 0, 1),
            makePotion(Material.POTION, PotionEffectType.STRENGTH, 0, 400),
            makePotion(Material.POTION, PotionEffectType.SPEED, 1, 300),
            makePotion(Material.POTION, PotionEffectType.REGENERATION, 0, 200),
        };
        return pool[random.nextInt(pool.length)].clone();
    }

    private ItemStack getLegendaryLootItem() {
        ItemStack[] pool = {
            new ItemStack(Material.NETHERITE_SWORD),
            new ItemStack(Material.DIAMOND_CHESTPLATE),
            new ItemStack(Material.DIAMOND_HELMET),
            new ItemStack(Material.ENCHANTED_GOLDEN_APPLE),
            new ItemStack(Material.TOTEM_OF_UNDYING),
            new ItemStack(Material.ENDER_PEARL, 4),
            makePotion(Material.POTION, PotionEffectType.STRENGTH, 1, 400),
            makePotion(Material.SPLASH_POTION, PotionEffectType.INSTANT_DAMAGE, 1, 1),
        };
        return pool[random.nextInt(pool.length)].clone();
    }

    // ═══════════════════════════════════════════
    //  TORMENTA (Fase 4)
    // ═══════════════════════════════════════════

    private void startStormBorder() {
        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (world == null) return;

        org.bukkit.WorldBorder border = world.getWorldBorder();
        border.setCenter(0, 0);
        border.setSize(STORM_INITIAL_RADIUS * 2);
        border.setWarningDistance(5);
        border.setWarningTime(0);
        border.setDamageAmount(0);
        border.setDamageBuffer(0);
        border.setSize(STORM_MIN_RADIUS * 2, STORM_SHRINK_DURATION);

        stormBorderTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!gameStarted || !stormActive) return;
            World w = Bukkit.getWorld(MiniGameWorld.getWorldName());
            if (w != null) {
                stormRadius = w.getWorldBorder().getSize() / 2.0;
            }
        }, 20L, 20L).getTaskId();

        broadcastGame("§4§l⚠ §c§lTORMENTA DE SANGRE §4§l⚠");
        broadcastGame("§7Una tormenta mortal cierra el mapa. §c¡Corre hacia el centro... si te atreves!");
        titleAlive("§4§lTORMENTA", "§cEl mapa se cierra... ¡pero el agujero crece!");
        soundAll(Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f);
    }

    // ═══════════════════════════════════════════
    //  BOSSBAR
    // ═══════════════════════════════════════════

    private void updateBossBar() {
        if (statusBar == null) return;
        int phase = getCurrentPhase();

        if (stormActive) {
            double progress = Math.max(0, Math.min(1, (stormRadius - STORM_MIN_RADIUS) / (STORM_INITIAL_RADIUS - STORM_MIN_RADIUS)));
            statusBar.setProgress(progress);
            statusBar.setTitle("§4§l☠ SINGULARIDAD §8| §fRadio: §c" + String.format("%.0f", stormRadius)
                    + " §8| §fAgujero: §5" + String.format("%.0f", currentKillRadius)
                    + " §8| §fVivos: §a" + alivePlayers.size());
        } else {
            int timeToStorm = STORM_START - gameTime;
            double progress = Math.max(0, (double) timeToStorm / STORM_START);
            statusBar.setProgress(progress);
            String phaseName = getPhaseNameColored(phase);
            statusBar.setTitle("§8§l🌑 " + phaseName + " §8| §fVivos: §a" + alivePlayers.size()
                    + " §8| §cTormenta: §f" + formatTime(timeToStorm));
        }
    }

    // ═══════════════════════════════════════════
    //  CLEANUP
    // ═══════════════════════════════════════════

    @Override
    public void onCleanup() {
        gameStarted = false;
        stormActive = false;
        rippedBlocks.clear();

        if (particleTaskId != -1) {
            Bukkit.getScheduler().cancelTask(particleTaskId);
            particleTaskId = -1;
        }
        if (blockRipTaskId != -1) {
            Bukkit.getScheduler().cancelTask(blockRipTaskId);
            blockRipTaskId = -1;
        }
        if (stormBorderTaskId != -1) {
            Bukkit.getScheduler().cancelTask(stormBorderTaskId);
            stormBorderTaskId = -1;
        }
        if (ambientSoundTaskId != -1) {
            Bukkit.getScheduler().cancelTask(ambientSoundTaskId);
            ambientSoundTaskId = -1;
        }

        // Reset WorldBorder
        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (world != null) {
            world.getWorldBorder().reset();
        }

        if (statusBar != null) {
            statusBar.removeAll();
            statusBar.setVisible(false);
            statusBar = null;
        }
    }

    // ═══════════════════════════════════════════
    //  UTILITY
    // ═══════════════════════════════════════════

    private double horizontalDist(Location loc) {
        return Math.sqrt(loc.getX() * loc.getX() + loc.getZ() * loc.getZ());
    }

    private String getPhaseNameColored(int phase) {
        return switch (phase) {
            case 1 -> "§aCalma";
            case 2 -> "§dDespertar";
            case 3 -> "§cColapso";
            case 4 -> "§4Singularidad";
            default -> "§8Agujero Negro";
        };
    }

    private String getPhaseNameShort(int phase) {
        return switch (phase) {
            case 1 -> "Calma";
            case 2 -> "Despertar";
            case 3 -> "Colapso";
            case 4 -> "Singularidad";
            default -> "???";
        };
    }

    private String formatTime(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    private ItemStack makePotion(Material potionType, PotionEffectType effect, int amplifier, int durationTicks) {
        ItemStack potion = new ItemStack(potionType);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        if (meta != null) {
            meta.addCustomEffect(new PotionEffect(effect, durationTicks, amplifier), true);
            String name = getPotionName(effect, amplifier);
            meta.setDisplayName(name);
            potion.setItemMeta(meta);
        }
        return potion;
    }

    private String getPotionName(PotionEffectType effect, int amplifier) {
        String level = amplifier > 0 ? " II" : "";
        if (effect.equals(PotionEffectType.INSTANT_DAMAGE)) return "§c§lPoción de Daño" + level;
        if (effect.equals(PotionEffectType.SLOWNESS)) return "§9Poción de Lentitud" + level;
        if (effect.equals(PotionEffectType.POISON)) return "§2Poción de Veneno" + level;
        if (effect.equals(PotionEffectType.SPEED)) return "§bPoción de Velocidad" + level;
        if (effect.equals(PotionEffectType.STRENGTH)) return "§4Poción de Fuerza" + level;
        if (effect.equals(PotionEffectType.REGENERATION)) return "§dPoción de Regeneración" + level;
        if (effect.equals(PotionEffectType.FIRE_RESISTANCE)) return "§6Poción Res. al Fuego";
        return "§5Poción Misteriosa";
    }

    // Flags para MiniGameListener
    public boolean isPvpGame() { return true; }
    public boolean allowsBlockPlace() { return true; }
    public boolean allowsBlockBreak() { return true; }
    public boolean allowsDrops() { return true; }
    public boolean allowsPickups() { return true; }
    public boolean allowsInventory() { return true; }
    public boolean allowsHunger() { return true; }
}
