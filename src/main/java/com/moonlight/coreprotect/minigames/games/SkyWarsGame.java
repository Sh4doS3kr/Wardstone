package com.moonlight.coreprotect.minigames.games;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.minigames.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * SkyWars: Islas flotantes en el vacío. Cada jugador spawna en su isla con un cofre de loot.
 * Isla central grande con mejor loot. Bridgea, lootea, pelea. Último en pie gana.
 *
 * Mecánicas:
 * - 12 islas de jugador en círculo (radio 60) a Y=100
 * - 1 isla central grande con loot épico + mesa de encantamientos
 * - 4 islas intermedias con loot mid-tier
 * - Jaulas de cristal durante countdown (30s gracia)
 * - Cofres se rellenan a los 3:00 y 6:00
 * - Caer al vacío (Y < 50) = muerte
 * - Tiempo límite: 10 minutos
 */
public class SkyWarsGame extends MiniGame {

    // === CONSTANTES ===
    private static final int ISLAND_Y = 100;
    private static final int ISLAND_RADIUS = 80; // Distancia de islas al centro
    private static final int PLAYER_ISLAND_SIZE = 12; // Radio de isla de jugador
    private static final int CENTER_ISLAND_SIZE = 16; // Radio de isla central
    private static final int MID_ISLAND_SIZE = 7; // Radio de islas intermedias
    private static final int MID_ISLAND_RADIUS = 45; // Distancia de islas mid al centro
    private static final int VOID_KILL_Y = 50;
    private static final int CAGE_HEIGHT = 5; // Altura de la jaula sobre la isla
    private static final int GRACE_PERIOD = 30; // Segundos de gracia
    private static final int REFILL_TIME_1 = 180; // 3:00
    private static final int REFILL_TIME_2 = 360; // 6:00
    private static final int TIME_LIMIT = 600; // 10 minutos
    private static final int MAX_ISLANDS = 12;

    // === STATE ===
    private final Random random = new Random();
    private boolean graceActive = true;
    private final List<Location> islandCenters = new ArrayList<>();
    private final List<Location> midIslandCenters = new ArrayList<>();
    private final List<Location> allChestLocations = new ArrayList<>();
    private Runnable onArenaBuiltCallback = null;

    public SkyWarsGame(CoreProtectPlugin plugin, MiniGameManager manager) {
        super(plugin, manager, MiniGameType.SKYWARS);
    }

    public boolean isGraceActive() {
        return graceActive;
    }

    // =========================================================
    // START — Override para construir async con progress bar
    // =========================================================
    @Override
    public void start(Set<UUID> joinedPlayers) {
        this.players.addAll(joinedPlayers);
        this.alivePlayers.addAll(joinedPlayers);
        this.running = true;
        this.gameTime = 0;

        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (world == null) return;

        // Teleportar a ubicación segura mientras se construye
        Location safeWait = new Location(world, 0.5, ISLAND_Y + 30, 0.5);
        for (UUID uuid : joinedPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.teleport(safeWait);
                p.setGameMode(GameMode.SPECTATOR);
                p.setAllowFlight(true);
                p.setFlying(true);
                p.sendMessage("§b§l⏳ §7Construyendo islas de SkyWars...");
            }
        }

        // Callback cuando la arena termine
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

            // Teleportar jugadores a sus islas
            teleportPlayersToArena(world, spawns);

            // Construir jaulas de cristal alrededor de cada jugador
            buildCages(world, spawns);

            onPreCountdown();

            // Safety TP retry
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (UUID uuid : players) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline() && !p.getWorld().getName().equals(MiniGameWorld.getWorldName())) {
                        int idx = new ArrayList<>(players).indexOf(uuid);
                        Location spawn = spawns.get(idx % spawns.size());
                        spawn.getChunk().load(true);
                        p.teleport(spawn);
                        p.setGameMode(GameMode.SURVIVAL);
                        p.setFallDistance(0);
                    }
                }
            }, 20L);

            // Countdown (5 segundos)
            new BukkitRunnable() {
                int count = 5;
                @Override
                public void run() {
                    if (!running) { cancel(); return; }
                    if (count <= 0) {
                        cancel();
                        // Liberar force-load
                        for (Location spawn : spawns) {
                            world.getChunkAt(spawn).setForceLoaded(false);
                        }
                        world.getChunkAt(0, 0).setForceLoaded(false);

                        // Romper jaulas
                        removeCages(world, spawns);

                        for (UUID uuid : players) {
                            Player p = Bukkit.getPlayer(uuid);
                            if (p != null && p.isOnline()) {
                                p.sendTitle("§a§l¡GO!", "§7¡Lootea y sobrevive!", 5, 20, 5);
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

        // Construir arena async
        buildArena(world);
    }

    // =========================================================
    // BUILD ARENA — Islas flotantes async con progress bar
    // =========================================================
    @Override
    public void buildArena(World world) {
        int numPlayers = Math.min(players.size(), MAX_ISLANDS);

        // Calcular centros de islas de jugador
        islandCenters.clear();
        for (int i = 0; i < MAX_ISLANDS; i++) {
            double angle = (2 * Math.PI * i) / MAX_ISLANDS;
            int ix = (int) (Math.cos(angle) * ISLAND_RADIUS);
            int iz = (int) (Math.sin(angle) * ISLAND_RADIUS);
            islandCenters.add(new Location(world, ix, ISLAND_Y, iz));
        }

        // 4 islas intermedias (entre centro y outer ring)
        midIslandCenters.clear();
        for (int i = 0; i < 4; i++) {
            double angle = (2 * Math.PI * i) / 4 + Math.PI / 4; // Offset 45 grados
            int ix = (int) (Math.cos(angle) * MID_ISLAND_RADIUS);
            int iz = (int) (Math.sin(angle) * MID_ISLAND_RADIUS);
            midIslandCenters.add(new Location(world, ix, ISLAND_Y, iz));
        }

        // Total tasks: center(1) + playerIslands(12) + midIslands(4) = 17
        int totalTasks = 1 + MAX_ISLANDS + 4;

        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                if (!running) { cancel(); return; }

                long start = System.currentTimeMillis();

                while (step < totalTasks && System.currentTimeMillis() - start < 45) {
                    if (step == 0) {
                        // Isla central
                        buildCenterIsland(world);
                    } else if (step <= MAX_ISLANDS) {
                        // Islas de jugador
                        Location center = islandCenters.get(step - 1);
                        buildPlayerIsland(world, center.getBlockX(), center.getBlockZ());
                    } else {
                        // Islas intermedias
                        int midIdx = step - MAX_ISLANDS - 1;
                        Location center = midIslandCenters.get(midIdx);
                        buildMidIsland(world, center.getBlockX(), center.getBlockZ());
                    }
                    step++;

                    // Progress bar
                    int pct = (step * 100) / totalTasks;
                    sendBuildProgress(pct);
                }

                if (step >= totalTasks) {
                    cancel();
                    // Clear titles
                    for (UUID uuid : players) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null && p.isOnline()) {
                            p.sendTitle("", "", 0, 1, 0);
                        }
                    }
                    if (onArenaBuiltCallback != null) {
                        Bukkit.getScheduler().runTask(plugin, onArenaBuiltCallback);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 2L);
    }

    private void sendBuildProgress(int pct) {
        int bars = 20;
        int filled = (pct * bars) / 100;
        StringBuilder sb = new StringBuilder("§b");
        for (int i = 0; i < bars; i++) {
            sb.append(i < filled ? "§b█" : "§7░");
        }
        String title = "§b§lSKYWARS §7| " + sb + " §f" + pct + "%";
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendTitle(title, "§7Construyendo islas...", 0, 40, 0);
            }
        }
    }

    // =========================================================
    // ISLAND BUILDERS
    // =========================================================

    private void buildPlayerIsland(World world, int cx, int cz) {
        int r = PLAYER_ISLAND_SIZE;
        int baseY = ISLAND_Y;

        // === TERRENO PRINCIPAL ===
        // Forma elíptica con ruido orgánico para bordes irregulares
        for (int x = -r - 1; x <= r + 1; x++) {
            for (int z = -r - 1; z <= r + 1; z++) {
                double dist = Math.sqrt(x * x + z * z);
                // Ruido suave para bordes orgánicos
                double edgeNoise = Math.sin(x * 0.4 + z * 0.3) * 0.6
                        + Math.cos(x * 0.7 - z * 0.5) * 0.4;
                double effectiveR = r + edgeNoise;
                if (dist > effectiveR) continue;

                // Capa superior: grass
                world.getBlockAt(cx + x, baseY, cz + z).setType(Material.GRASS_BLOCK, false);

                // 3 capas de dirt
                for (int dy = 1; dy <= 3; dy++) {
                    world.getBlockAt(cx + x, baseY - dy, cz + z).setType(Material.DIRT, false);
                }

                // Stone con forma cónica descendente (hasta 10 bloques abajo)
                for (int dy = 4; dy <= 12; dy++) {
                    double coneR = effectiveR * (1.0 - (dy - 3.0) / 10.0);
                    if (dist <= coneR) {
                        Material stoneMat = Material.STONE;
                        if (random.nextInt(5) == 0) stoneMat = Material.COBBLESTONE;
                        if (random.nextInt(8) == 0) stoneMat = Material.ANDESITE;
                        if (dy >= 10 && random.nextInt(3) == 0) stoneMat = Material.DEEPSLATE;
                        world.getBlockAt(cx + x, baseY - dy, cz + z).setType(stoneMat, false);
                    }
                }

                // Caminos de tierra/path aleatorios
                if (dist < r - 2 && random.nextInt(20) == 0) {
                    world.getBlockAt(cx + x, baseY, cz + z).setType(Material.DIRT_PATH, false);
                }
                // Piedra expuesta en bordes
                if (dist > effectiveR - 1.5 && random.nextInt(3) == 0) {
                    world.getBlockAt(cx + x, baseY, cz + z).setType(Material.STONE, false);
                }
            }
        }

        // === ELEVACIÓN DE TERRENO — pequeñas colinas ===
        for (int h = 0; h < 2; h++) {
            int hx = cx - 4 + random.nextInt(9);
            int hz = cz - 4 + random.nextInt(9);
            int hillR = 2 + random.nextInt(2);
            for (int x = -hillR; x <= hillR; x++) {
                for (int z = -hillR; z <= hillR; z++) {
                    if (Math.sqrt(x * x + z * z) <= hillR) {
                        Block below = world.getBlockAt(hx + x, baseY, hz + z);
                        if (below.getType() == Material.GRASS_BLOCK || below.getType() == Material.DIRT_PATH) {
                            world.getBlockAt(hx + x, baseY + 1, hz + z).setType(Material.GRASS_BLOCK, false);
                            below.setType(Material.DIRT, false);
                        }
                    }
                }
            }
        }

        // === ÁRBOLES (LEJOS del centro para no molestar al spawn) ===
        int[][] treeOffsets = {{-6, -5}, {5, -6}, {-5, 6}, {7, 4}};
        for (int[] off : treeOffsets) {
            int tx = cx + off[0] + random.nextInt(3) - 1;
            int tz = cz + off[1] + random.nextInt(3) - 1;
            double dist = Math.sqrt((tx - cx) * (tx - cx) + (tz - cz) * (tz - cz));
            if (dist < r - 1 && dist > 3) {
                // Encontrar Y correcto (puede haber colina)
                int ty = baseY + 1;
                if (world.getBlockAt(tx, baseY + 1, tz).getType() == Material.GRASS_BLOCK) ty = baseY + 2;
                buildSmallTree(world, tx, ty, tz);
            }
        }

        // === 5 COFRES por isla distribuidos ===
        placeIslandChest(world, cx + 3, baseY, cz - 2, true);
        placeIslandChest(world, cx - 3, baseY, cz + 3, true);
        placeIslandChest(world, cx + 5, baseY, cz + 4, true);
        placeIslandChest(world, cx - 5, baseY, cz - 4, true);
        placeIslandChest(world, cx, baseY, cz + 6, true);

        // === DECORACIÓN: flores, hierba, arbustos ===
        Material[] flora = {Material.SHORT_GRASS, Material.SHORT_GRASS, Material.POPPY, Material.DANDELION,
                Material.CORNFLOWER, Material.AZURE_BLUET, Material.OXEYE_DAISY, Material.FERN};
        for (int i = 0; i < 20; i++) {
            int fx = cx - r + 2 + random.nextInt(r * 2 - 3);
            int fz = cz - r + 2 + random.nextInt(r * 2 - 3);
            for (int fy = baseY + 1; fy >= baseY; fy--) {
                Block above = world.getBlockAt(fx, fy + 1, fz);
                Block below = world.getBlockAt(fx, fy, fz);
                if (above.getType() == Material.AIR && below.getType() == Material.GRASS_BLOCK) {
                    above.setType(flora[random.nextInt(flora.length)], false);
                    break;
                }
            }
        }

        // === DETALLES ESTRUCTURALES: mini-ruina o pozo ===
        if (random.nextInt(2) == 0) {
            // Pozo de agua
            int px = cx + (random.nextBoolean() ? 4 : -4);
            int pz = cz + (random.nextBoolean() ? 4 : -4);
            Block base = world.getBlockAt(px, baseY, pz);
            if (base.getType() == Material.GRASS_BLOCK) {
                world.getBlockAt(px, baseY, pz).setType(Material.COBBLESTONE, false);
                world.getBlockAt(px + 1, baseY + 1, pz).setType(Material.COBBLESTONE_WALL, false);
                world.getBlockAt(px - 1, baseY + 1, pz).setType(Material.COBBLESTONE_WALL, false);
                world.getBlockAt(px, baseY + 1, pz + 1).setType(Material.COBBLESTONE_WALL, false);
                world.getBlockAt(px, baseY + 1, pz - 1).setType(Material.COBBLESTONE_WALL, false);
            }
        } else {
            // Mini fogata
            int fx = cx + (random.nextBoolean() ? 5 : -5);
            int fz = cz + (random.nextBoolean() ? 3 : -3);
            Block base = world.getBlockAt(fx, baseY, fz);
            if (base.getType() == Material.GRASS_BLOCK) {
                world.getBlockAt(fx, baseY + 1, fz).setType(Material.CAMPFIRE, false);
                world.getBlockAt(fx, baseY, fz).setType(Material.STONE, false);
            }
        }
    }

    private void placeIslandChest(World world, int x, int baseY, int z, boolean playerTier) {
        // Buscar superficie correcta
        int cy = baseY + 1;
        Block ground = world.getBlockAt(x, baseY, z);
        if (ground.getType() == Material.AIR) return;
        if (world.getBlockAt(x, baseY + 1, z).getType() == Material.GRASS_BLOCK) cy = baseY + 2;

        Block chestBlock = world.getBlockAt(x, cy, z);
        if (chestBlock.getType() != Material.AIR) return;
        chestBlock.setType(Material.CHEST, false);
        allChestLocations.add(chestBlock.getLocation());
        if (chestBlock.getState() instanceof Chest chest) {
            if (playerTier) fillPlayerChest(chest.getInventory());
            else fillMidChest(chest.getInventory());
        }
    }

    private void buildCenterIsland(World world) {
        int r = CENTER_ISLAND_SIZE;
        int baseY = ISLAND_Y;

        // === TERRENO PRINCIPAL ===
        for (int x = -r - 1; x <= r + 1; x++) {
            for (int z = -r - 1; z <= r + 1; z++) {
                double dist = Math.sqrt(x * x + z * z);
                double edgeNoise = Math.sin(x * 0.3 + z * 0.2) * 0.8
                        + Math.cos(x * 0.5 - z * 0.4) * 0.6;
                double effectiveR = r + edgeNoise;
                if (dist > effectiveR) continue;

                world.getBlockAt(x, baseY, z).setType(Material.GRASS_BLOCK, false);
                for (int dy = 1; dy <= 3; dy++) {
                    world.getBlockAt(x, baseY - dy, z).setType(Material.DIRT, false);
                }
                for (int dy = 4; dy <= 14; dy++) {
                    double coneR = effectiveR * (1.0 - (dy - 3.0) / 12.0);
                    if (dist <= coneR) {
                        Material stoneMat = Material.STONE;
                        if (random.nextInt(4) == 0) stoneMat = Material.COBBLESTONE;
                        if (random.nextInt(6) == 0) stoneMat = Material.ANDESITE;
                        if (dy >= 10 && random.nextInt(3) == 0) stoneMat = Material.DEEPSLATE;
                        world.getBlockAt(x, baseY - dy, z).setType(stoneMat, false);
                    }
                }

                // Borde de piedra decorativo
                if (dist > effectiveR - 2 && random.nextInt(3) == 0) {
                    world.getBlockAt(x, baseY, z).setType(Material.COBBLESTONE, false);
                }
            }
        }

        // === ESTRUCTURA CENTRAL: plataforma elevada con mesa de encantamientos ===
        // Pequeña plataforma de piedra 5x5 elevada 1 bloque
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                world.getBlockAt(x, baseY + 1, z).setType(Material.STONE_BRICKS, false);
                if (Math.abs(x) == 2 || Math.abs(z) == 2) {
                    if (random.nextInt(3) == 0) {
                        world.getBlockAt(x, baseY + 1, z).setType(Material.MOSSY_STONE_BRICKS, false);
                    }
                }
            }
        }
        // Esquinas con pilares de 2 bloques
        int[][] corners = {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}};
        for (int[] c : corners) {
            world.getBlockAt(c[0], baseY + 2, c[1]).setType(Material.STONE_BRICK_WALL, false);
        }

        world.getBlockAt(0, baseY + 2, 0).setType(Material.ENCHANTING_TABLE, false);
        // Librerías alrededor
        int[][] libraryPos = {{-1, -1}, {0, -1}, {1, -1}, {-1, 1}, {0, 1}, {1, 1}, {-1, 0}, {1, 0}};
        for (int[] pos : libraryPos) {
            world.getBlockAt(pos[0], baseY + 2, pos[1]).setType(Material.BOOKSHELF, false);
        }

        // === 8 COFRES ÉPICOS distribuidos alrededor ===
        int[][] chestPositions = {{6, 0}, {-6, 0}, {0, 6}, {0, -6}, {5, 5}, {-5, -5}, {-5, 5}, {5, -5}};
        for (int[] pos : chestPositions) {
            Block chestBlock = world.getBlockAt(pos[0], baseY + 1, pos[1]);
            if (chestBlock.getType() == Material.AIR) {
                chestBlock.setType(Material.CHEST, false);
                allChestLocations.add(chestBlock.getLocation());
                if (chestBlock.getState() instanceof Chest chest) {
                    fillCenterChest(chest.getInventory());
                }
            }
        }

        // === ÁRBOLES grandes en las esquinas ===
        int[][] treePosCenter = {{-9, -8}, {9, 8}, {-8, 9}, {8, -9}, {-10, 3}, {10, -3}};
        for (int[] tp : treePosCenter) {
            int tx = tp[0], tz = tp[1];
            double dist = Math.sqrt(tx * tx + tz * tz);
            if (dist < r) {
                buildSmallTree(world, tx, baseY + 1, tz);
            }
        }

        // === DECORACIÓN ===
        Material[] flora = {Material.SHORT_GRASS, Material.POPPY, Material.DANDELION, Material.FERN,
                Material.CORNFLOWER, Material.AZURE_BLUET, Material.OXEYE_DAISY};
        for (int i = 0; i < 30; i++) {
            int fx = -r + 2 + random.nextInt(r * 2 - 3);
            int fz = -r + 2 + random.nextInt(r * 2 - 3);
            Block above = world.getBlockAt(fx, baseY + 1, fz);
            Block below = world.getBlockAt(fx, baseY, fz);
            if (above.getType() == Material.AIR && below.getType() == Material.GRASS_BLOCK) {
                above.setType(flora[random.nextInt(flora.length)], false);
            }
        }

        // Crafting tables + anvil
        world.getBlockAt(4, baseY + 1, 3).setType(Material.CRAFTING_TABLE, false);
        world.getBlockAt(-4, baseY + 1, -3).setType(Material.ANVIL, false);
    }

    private void buildMidIsland(World world, int cx, int cz) {
        int r = MID_ISLAND_SIZE;
        int baseY = ISLAND_Y;

        for (int x = -r - 1; x <= r + 1; x++) {
            for (int z = -r - 1; z <= r + 1; z++) {
                double dist = Math.sqrt(x * x + z * z);
                double edgeNoise = Math.sin(x * 0.5 + z * 0.4) * 0.5;
                double effectiveR = r + edgeNoise;
                if (dist > effectiveR) continue;

                world.getBlockAt(cx + x, baseY, cz + z).setType(Material.GRASS_BLOCK, false);
                world.getBlockAt(cx + x, baseY - 1, cz + z).setType(Material.DIRT, false);
                world.getBlockAt(cx + x, baseY - 2, cz + z).setType(Material.DIRT, false);

                for (int dy = 3; dy <= 8; dy++) {
                    double coneR = effectiveR * (1.0 - (dy - 2.0) / 7.0);
                    if (dist <= coneR) {
                        Material mat = random.nextInt(4) == 0 ? Material.COBBLESTONE : Material.STONE;
                        world.getBlockAt(cx + x, baseY - dy, cz + z).setType(mat, false);
                    }
                }

                if (dist > effectiveR - 1.5 && random.nextInt(3) == 0) {
                    world.getBlockAt(cx + x, baseY, cz + z).setType(Material.STONE, false);
                }
            }
        }

        // 3 cofres mid-tier
        Block chestBlock1 = world.getBlockAt(cx + 2, baseY + 1, cz);
        chestBlock1.setType(Material.CHEST, false);
        allChestLocations.add(chestBlock1.getLocation());
        if (chestBlock1.getState() instanceof Chest chest) {
            fillMidChest(chest.getInventory());
        }
        Block chestBlock2 = world.getBlockAt(cx - 2, baseY + 1, cz);
        chestBlock2.setType(Material.CHEST, false);
        allChestLocations.add(chestBlock2.getLocation());
        if (chestBlock2.getState() instanceof Chest chest) {
            fillMidChest(chest.getInventory());
        }
        Block chestBlock3 = world.getBlockAt(cx, baseY + 1, cz - 3);
        chestBlock3.setType(Material.CHEST, false);
        allChestLocations.add(chestBlock3.getLocation());
        if (chestBlock3.getState() instanceof Chest chest) {
            fillMidChest(chest.getInventory());
        }

        // Árbol
        buildSmallTree(world, cx, baseY + 1, cz + 3);

        // Detalles
        world.getBlockAt(cx + 1, baseY + 1, cz + 1).setType(Material.CRAFTING_TABLE, false);

        // Flores
        Material[] flora = {Material.SHORT_GRASS, Material.POPPY, Material.DANDELION, Material.FERN};
        for (int i = 0; i < 8; i++) {
            int fx = cx - r + 1 + random.nextInt(r * 2 - 1);
            int fz = cz - r + 1 + random.nextInt(r * 2 - 1);
            Block above = world.getBlockAt(fx, baseY + 1, fz);
            Block below = world.getBlockAt(fx, baseY, fz);
            if (above.getType() == Material.AIR && below.getType() == Material.GRASS_BLOCK) {
                above.setType(flora[random.nextInt(flora.length)], false);
            }
        }
    }

    private void buildSmallTree(World world, int tx, int baseY, int tz) {
        Material logType = random.nextBoolean() ? Material.OAK_LOG : Material.BIRCH_LOG;
        Material leafType = logType == Material.OAK_LOG ? Material.OAK_LEAVES : Material.BIRCH_LEAVES;

        int trunkHeight = 4 + random.nextInt(2);
        for (int y = 0; y < trunkHeight; y++) {
            world.getBlockAt(tx, baseY + y, tz).setType(logType, false);
        }

        // Copa del árbol
        int leafStart = trunkHeight - 2;
        for (int y = leafStart; y <= trunkHeight + 1; y++) {
            int leafR = (y <= trunkHeight - 1) ? 2 : 1;
            for (int x = -leafR; x <= leafR; x++) {
                for (int z = -leafR; z <= leafR; z++) {
                    if (x == 0 && z == 0 && y < trunkHeight) continue;
                    double d = Math.sqrt(x * x + z * z);
                    if (d <= leafR + 0.5 && random.nextInt(5) > 0) {
                        Block b = world.getBlockAt(tx + x, baseY + y, tz + z);
                        if (b.getType() == Material.AIR) {
                            BlockData data = Bukkit.createBlockData(leafType);
                            if (data instanceof Leaves leafData) {
                                leafData.setPersistent(true);
                                leafData.setDistance(1);
                                data = leafData;
                            }
                            b.setBlockData(data, false);
                        }
                    }
                }
            }
        }
    }

    // =========================================================
    // JAULAS DE CRISTAL FLOTANTES
    // =========================================================
    private void buildCages(World world, List<Location> spawns) {
        for (Location spawn : spawns) {
            int sx = spawn.getBlockX();
            int sy = spawn.getBlockY(); // Ya está a ISLAND_Y + 1 + CAGE_HEIGHT
            int sz = spawn.getBlockZ();
            // Jaula 3x3 flotante: suelo, paredes, techo
            // Interior libre: x=0,z=0 en y=0 y y=1 (espacio para el jugador)
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 2; y++) {
                    for (int z = -1; z <= 1; z++) {
                        boolean isInterior = (Math.abs(x) <= 0 && Math.abs(z) <= 0 && y >= 0 && y <= 1);
                        if (isInterior) continue;
                        boolean isEdge = (Math.abs(x) == 1 || Math.abs(z) == 1 || y == -1 || y == 2);
                        if (isEdge) {
                            Block b = world.getBlockAt(sx + x, sy + y, sz + z);
                            b.setType(Material.GLASS, false);
                        }
                    }
                }
            }
        }
    }

    private void removeCages(World world, List<Location> spawns) {
        for (Location spawn : spawns) {
            int sx = spawn.getBlockX();
            int sy = spawn.getBlockY();
            int sz = spawn.getBlockZ();
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 2; y++) {
                    for (int z = -1; z <= 1; z++) {
                        Block b = world.getBlockAt(sx + x, sy + y, sz + z);
                        if (b.getType() == Material.GLASS) {
                            b.setType(Material.AIR, false);
                        }
                    }
                }
            }
        }
    }

    // =========================================================
    // SPAWN LOCATIONS
    // =========================================================
    @Override
    public List<Location> getSpawnLocations(World world) {
        List<Location> spawns = new ArrayList<>();
        int numPlayers = Math.min(players.size(), MAX_ISLANDS);
        for (int i = 0; i < numPlayers; i++) {
            Location center = islandCenters.get(i);
            // Spawn DENTRO de la jaula flotante (CAGE_HEIGHT bloques sobre la isla)
            spawns.add(new Location(world, center.getBlockX() + 0.5, ISLAND_Y + 1.0 + CAGE_HEIGHT, center.getBlockZ() + 0.5));
        }
        return spawns;
    }

    // =========================================================
    // GAME LOGIC
    // =========================================================
    @Override
    public void startGameLogic() {
        graceActive = true;
    }

    @Override
    public void onTick() {
        // Gracia
        if (graceActive && gameTime >= GRACE_PERIOD) {
            graceActive = false;
            broadcastGame("§c§l⚔ §e¡PvP ACTIVADO! §7¡A pelear!");
            titleAll("§c§l⚔ PvP ACTIVADO", "§7¡A pelear!");
            soundAll(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);
        }

        // Aviso de gracia
        if (graceActive && (GRACE_PERIOD - gameTime) <= 10 && (GRACE_PERIOD - gameTime) > 0) {
            int remaining = GRACE_PERIOD - gameTime;
            broadcastGame("§e§l⏳ §7PvP se activa en §e" + remaining + "s");
        }

        // Refill 1
        if (gameTime == REFILL_TIME_1) {
            refillAllChests();
            broadcastGame("§6§l✦ §e¡Los cofres se han rellenado! §7(Refill 1/2)");
            titleAll("§6§l✦ REFILL", "§7¡Los cofres se han rellenado!");
            soundAll(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        }

        // Refill 2
        if (gameTime == REFILL_TIME_2) {
            refillAllChests();
            broadcastGame("§6§l✦ §e¡Los cofres se han rellenado! §7(Refill 2/2)");
            titleAll("§6§l✦ REFILL FINAL", "§7¡Último refill! Loot épico.");
            soundAll(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f);
        }

        // Void kill check
        for (UUID uuid : new ArrayList<>(alivePlayers)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline() && p.getLocation().getY() < VOID_KILL_Y) {
                p.setHealth(0);
            }
        }

        // Time limit
        if (gameTime >= TIME_LIMIT) {
            // Ganador = el que más vida tiene
            Player best = null;
            double bestHealth = -1;
            for (UUID uuid : alivePlayers) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    if (p.getHealth() > bestHealth) {
                        bestHealth = p.getHealth();
                        best = p;
                    }
                }
            }
            broadcastGame("§c§l⏰ §7¡Tiempo agotado! Gana el jugador con más vida.");
            endGame(best);
            return;
        }

        // Info periódica
        if (gameTime % 30 == 0 && gameTime > 0) {
            int remaining = TIME_LIMIT - gameTime;
            int min = remaining / 60;
            int sec = remaining % 60;
            broadcastGame("§b§lSKYWARS §7| §fTiempo: §e" + min + ":" + String.format("%02d", sec) +
                    " §7| §fVivos: §a" + alivePlayers.size());
        }

        // Avisos de tiempo
        if (gameTime == TIME_LIMIT - 60) {
            titleAlive("§c§l¡1 MINUTO!", "§7¡El tiempo se acaba!");
            soundAll(Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
        }
        if (gameTime == TIME_LIMIT - 30) {
            titleAlive("§c§l¡30 SEGUNDOS!", "§7¡Lucha o muere!");
            soundAll(Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.5f);
        }

        // Actionbar
        showActionBar();
    }

    private void showActionBar() {
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                int remaining = TIME_LIMIT - gameTime;
                int min = remaining / 60;
                int sec = remaining % 60;
                String grace = graceActive ? " §e§l⏳ GRACIA" : " §c§l⚔ PvP";
                p.sendActionBar("§b§lSKYWARS" + grace + " §7| §fVivos: §a" + alivePlayers.size() +
                        " §7| §fTiempo: §e" + min + ":" + String.format("%02d", sec));
            }
        }
    }

    // =========================================================
    // LOOT SYSTEM
    // =========================================================

    private void fillPlayerChest(Inventory inv) {
        inv.clear();
        List<ItemStack> pool = new ArrayList<>();

        // Armas básicas
        pool.add(new ItemStack(Material.STONE_SWORD));
        pool.add(new ItemStack(Material.STONE_AXE));
        pool.add(new ItemStack(Material.WOODEN_SWORD));

        // Armadura básica
        pool.add(new ItemStack(Material.LEATHER_HELMET));
        pool.add(new ItemStack(Material.LEATHER_CHESTPLATE));
        pool.add(new ItemStack(Material.LEATHER_LEGGINGS));
        pool.add(new ItemStack(Material.LEATHER_BOOTS));
        pool.add(new ItemStack(Material.CHAINMAIL_HELMET));
        pool.add(new ItemStack(Material.CHAINMAIL_BOOTS));

        // Bloques para bridgear
        pool.add(new ItemStack(Material.OAK_PLANKS, 32 + random.nextInt(32)));
        pool.add(new ItemStack(Material.COBBLESTONE, 32 + random.nextInt(32)));

        // Arco + flechas
        pool.add(new ItemStack(Material.BOW));
        pool.add(new ItemStack(Material.ARROW, 5 + random.nextInt(8)));

        // Comida
        pool.add(new ItemStack(Material.COOKED_BEEF, 3 + random.nextInt(5)));
        pool.add(new ItemStack(Material.GOLDEN_APPLE));

        // Utilidades
        pool.add(new ItemStack(Material.ENDER_PEARL, 1));
        pool.add(new ItemStack(Material.COBWEB, 2 + random.nextInt(3)));
        pool.add(new ItemStack(Material.WATER_BUCKET));

        // Seleccionar 5-7 items
        Collections.shuffle(pool, random);
        Set<Integer> usedSlots = new HashSet<>();
        int numItems = 5 + random.nextInt(3);
        for (int i = 0; i < Math.min(numItems, pool.size()); i++) {
            int slot = random.nextInt(27);
            while (usedSlots.contains(slot)) slot = random.nextInt(27);
            usedSlots.add(slot);
            inv.setItem(slot, pool.get(i));
        }
    }

    private void fillMidChest(Inventory inv) {
        inv.clear();
        List<ItemStack> pool = new ArrayList<>();

        // Armas medio tier
        pool.add(new ItemStack(Material.IRON_SWORD));
        pool.add(new ItemStack(Material.IRON_AXE));
        pool.add(new ItemStack(Material.BOW));
        pool.add(new ItemStack(Material.CROSSBOW));

        // Armadura
        pool.add(new ItemStack(Material.IRON_HELMET));
        pool.add(new ItemStack(Material.IRON_CHESTPLATE));
        pool.add(new ItemStack(Material.IRON_LEGGINGS));
        pool.add(new ItemStack(Material.IRON_BOOTS));
        pool.add(new ItemStack(Material.CHAINMAIL_CHESTPLATE));

        // Bloques
        pool.add(new ItemStack(Material.OAK_PLANKS, 48 + random.nextInt(32)));
        pool.add(new ItemStack(Material.COBBLESTONE, 48 + random.nextInt(32)));

        // Flechas
        pool.add(new ItemStack(Material.ARROW, 12 + random.nextInt(8)));

        // Comida
        pool.add(new ItemStack(Material.COOKED_BEEF, 8 + random.nextInt(8)));
        pool.add(new ItemStack(Material.GOLDEN_APPLE, 2));

        // Utilidades
        pool.add(new ItemStack(Material.ENDER_PEARL, 2));
        pool.add(new ItemStack(Material.TNT, 2 + random.nextInt(3)));
        pool.add(new ItemStack(Material.FLINT_AND_STEEL));
        pool.add(new ItemStack(Material.LAVA_BUCKET));
        pool.add(new ItemStack(Material.WATER_BUCKET));

        // Poción
        pool.add(makeVanillaPotion(Material.POTION, PotionType.HEALING));

        Collections.shuffle(pool, random);
        Set<Integer> usedSlots = new HashSet<>();
        int numItems = 6 + random.nextInt(3);
        for (int i = 0; i < Math.min(numItems, pool.size()); i++) {
            int slot = random.nextInt(27);
            while (usedSlots.contains(slot)) slot = random.nextInt(27);
            usedSlots.add(slot);
            inv.setItem(slot, pool.get(i));
        }
    }

    private void fillCenterChest(Inventory inv) {
        inv.clear();
        List<ItemStack> pool = new ArrayList<>();

        // Armas épicas
        pool.add(enchant(new ItemStack(Material.DIAMOND_SWORD), Enchantment.SHARPNESS, 1 + random.nextInt(2)));
        pool.add(enchant(new ItemStack(Material.IRON_SWORD), Enchantment.SHARPNESS, 2 + random.nextInt(2)));
        pool.add(enchant(new ItemStack(Material.BOW), Enchantment.POWER, 2 + random.nextInt(2)));
        pool.add(enchant(new ItemStack(Material.CROSSBOW), Enchantment.QUICK_CHARGE, 2));

        // Armadura diamante
        pool.add(enchant(new ItemStack(Material.DIAMOND_HELMET), Enchantment.PROTECTION, 1 + random.nextInt(2)));
        pool.add(enchant(new ItemStack(Material.DIAMOND_CHESTPLATE), Enchantment.PROTECTION, 1 + random.nextInt(2)));
        pool.add(enchant(new ItemStack(Material.DIAMOND_LEGGINGS), Enchantment.PROTECTION, 1 + random.nextInt(2)));
        pool.add(enchant(new ItemStack(Material.DIAMOND_BOOTS), Enchantment.PROTECTION, 1 + random.nextInt(2)));

        // Armadura hierro encantada
        pool.add(enchant(new ItemStack(Material.IRON_CHESTPLATE), Enchantment.PROTECTION, 3));
        pool.add(enchant(new ItemStack(Material.IRON_LEGGINGS), Enchantment.PROTECTION, 3));

        // Bloques premium
        pool.add(new ItemStack(Material.OBSIDIAN, 4 + random.nextInt(4)));
        pool.add(new ItemStack(Material.END_STONE, 32 + random.nextInt(32)));

        // Flechas muchas
        pool.add(new ItemStack(Material.ARROW, 24 + random.nextInt(16)));

        // Comida top
        pool.add(new ItemStack(Material.GOLDEN_APPLE, 2 + random.nextInt(3)));
        pool.add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE));
        pool.add(new ItemStack(Material.COOKED_BEEF, 16));

        // Utilidades premium
        pool.add(new ItemStack(Material.ENDER_PEARL, 3 + random.nextInt(3)));
        pool.add(new ItemStack(Material.TNT, 4 + random.nextInt(4)));
        pool.add(new ItemStack(Material.FLINT_AND_STEEL));
        pool.add(new ItemStack(Material.TOTEM_OF_UNDYING));
        pool.add(new ItemStack(Material.SHIELD));

        // Pociones
        pool.add(makeVanillaPotion(Material.POTION, PotionType.STRONG_HEALING));
        pool.add(makeVanillaPotion(Material.SPLASH_POTION, PotionType.STRONG_HEALING));
        pool.add(makeVanillaPotion(Material.POTION, PotionType.LONG_STRENGTH));
        pool.add(makeVanillaPotion(Material.POTION, PotionType.LONG_SWIFTNESS));

        Collections.shuffle(pool, random);
        Set<Integer> usedSlots = new HashSet<>();
        int numItems = 7 + random.nextInt(4);
        for (int i = 0; i < Math.min(numItems, pool.size()); i++) {
            int slot = random.nextInt(27);
            while (usedSlots.contains(slot)) slot = random.nextInt(27);
            usedSlots.add(slot);
            inv.setItem(slot, pool.get(i));
        }
    }

    private void refillAllChests() {
        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (world == null) return;

        for (Location loc : allChestLocations) {
            Block b = world.getBlockAt(loc);
            if (b.getType() == Material.CHEST && b.getState() instanceof Chest chest) {
                // Determinar tipo según distancia al centro
                double dist = Math.sqrt(loc.getBlockX() * loc.getBlockX() + loc.getBlockZ() * loc.getBlockZ());
                if (dist < 15) {
                    fillCenterChest(chest.getInventory());
                } else if (dist < 45) {
                    fillMidChest(chest.getInventory());
                } else {
                    fillPlayerChest(chest.getInventory());
                }
            }
        }
    }

    // =========================================================
    // UTILIDADES
    // =========================================================

    private ItemStack enchant(ItemStack item, Enchantment ench, int level) {
        item.addUnsafeEnchantment(ench, level);
        return item;
    }

    private ItemStack makeVanillaPotion(Material type, PotionType potionType) {
        ItemStack potion = new ItemStack(type);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        if (meta != null) {
            meta.setBasePotionType(potionType);
            potion.setItemMeta(meta);
        }
        return potion;
    }

    // =========================================================
    // CLEANUP
    // =========================================================
    @Override
    public void onCleanup() {
        islandCenters.clear();
        midIslandCenters.clear();
        allChestLocations.clear();
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.setGlowing(false);
            }
        }
    }
}
