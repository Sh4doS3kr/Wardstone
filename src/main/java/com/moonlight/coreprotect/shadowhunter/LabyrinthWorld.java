package com.moonlight.coreprotect.shadowhunter;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.GameRuleUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.*;

/**
 * Genera un mundo temporal vacío con un laberinto gigante aleatorio para la Misión 7.
 * El laberinto usa recursive backtracking (DFS) para generar caminos perfectos,
 * luego añade salas especiales: salas de horror, salas de llaves, sala del boss.
 * 
 * Estructura:
 * - Tamaño: 21x21 celdas (cada celda = 4 bloques = 84x84 bloques total)
 * - Paredes: Deepslate Tiles / Black Concrete
 * - Suelo: Deepslate
 * - Techo: Sculk (efecto de humedad/oscuridad)
 * - Altura del pasillo: 4 bloques (Y=60 a Y=64 paredes, Y=59 suelo, Y=64 techo)
 * - Sin luz: oscuridad absoluta
 * - 3 salas de llaves (fragmentos de alma)
 * - Salas de horror con jumpscares
 * - Sala del boss al centro
 */
public class LabyrinthWorld {

    private final CoreProtectPlugin plugin;
    private final UUID playerId;
    private final String worldName;
    private World world;

    // Maze configuration
    public static final int MAZE_SIZE = 21; // Must be odd for proper maze generation
    public static final int CELL_SIZE = 4;  // Blocks per cell
    public static final int MAZE_BLOCKS = MAZE_SIZE * CELL_SIZE;
    public static final int FLOOR_Y = 59;
    public static final int WALL_MIN_Y = 60;
    public static final int WALL_MAX_Y = 64;
    public static final int CEILING_Y = 64;
    public static final int PASSAGE_HEIGHT = 4; // 60,61,62,63

    // Maze data: true = passage, false = wall
    private boolean[][] maze;

    // Special locations (in cell coordinates)
    private int startCellX, startCellZ;
    private int bossCellX, bossCellZ;
    private final List<int[]> keyCells = new ArrayList<>();
    private final List<int[]> horrorCells = new ArrayList<>();
    private final List<int[]> chaseCells = new ArrayList<>();

    // World block coordinates of special locations
    private Location startLocation;
    private Location bossRoomCenter;
    private final List<Location> keyLocations = new ArrayList<>();
    private final List<Location> horrorLocations = new ArrayList<>();
    private final List<Location> chaseLocations = new ArrayList<>();

    private boolean generated = false;
    private boolean deleted = false;

    // Maze origin offset in world coordinates
    private static final int ORIGIN_X = 0;
    private static final int ORIGIN_Z = 0;

    public LabyrinthWorld(CoreProtectPlugin plugin, UUID playerId) {
        this.plugin = plugin;
        this.playerId = playerId;
        this.worldName = "labyrinth_" + playerId.toString().substring(0, 8);
    }

    // ==================== WORLD CREATION ====================

    public void createWorld(Runnable onComplete) {
        // Delete if world already exists
        World existing = Bukkit.getWorld(worldName);
        if (existing != null) {
            // Teleport any remaining players out first
            for (Player p : existing.getPlayers()) {
                p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            }
            Bukkit.unloadWorld(existing, false);
        }
        // Delete leftover world folder to avoid corruption
        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
        if (worldFolder.exists()) {
            deleteDirectory(worldFolder);
            plugin.getLogger().info("[Labyrinth] Deleted old world folder: " + worldName);
        }

        // Create void world
        WorldCreator creator = new WorldCreator(worldName);
        creator.type(WorldType.FLAT);
        creator.generator(new VoidGenerator());
        creator.generateStructures(false);
        creator.environment(World.Environment.NORMAL);

        try {
            world = creator.createWorld();
        } catch (Exception e) {
            plugin.getLogger().severe("[Labyrinth] Exception creating world: " + e.getMessage());
            e.printStackTrace();
        }
        if (world == null) {
            plugin.getLogger().severe("[Labyrinth] Failed to create world: " + worldName);
            if (onComplete != null) {
                plugin.getLogger().severe("[Labyrinth] Cannot proceed - world creation failed.");
            }
            return;
        }
        plugin.getLogger().info("[Labyrinth] World created successfully: " + worldName);

        // World settings for horror
        world.setDifficulty(Difficulty.HARD);
        GameRuleUtil.set(world, "doDaylightCycle", false);
        GameRuleUtil.set(world, "doWeatherCycle", false);
        GameRuleUtil.set(world, "doMobSpawning", false);
        GameRuleUtil.set(world, "mobGriefing", false);
        GameRuleUtil.set(world, "keepInventory", true);
        GameRuleUtil.set(world, "doFireTick", false);
        GameRuleUtil.set(world, "announceAdvancements", false);
        GameRuleUtil.set(world, "showDeathMessages", false);
        GameRuleUtil.set(world, "doImmediateRespawn", true);
        world.setTime(18000); // Midnight
        world.setStorm(false);

        // Generate maze data
        generateMaze();

        // Build maze asynchronously in batches
        buildMaze(onComplete);
    }

    // ==================== MAZE GENERATION (Recursive Backtracking) ====================

    private void generateMaze() {
        maze = new boolean[MAZE_SIZE][MAZE_SIZE];
        // Initialize all as walls (false)
        for (boolean[] row : maze) Arrays.fill(row, false);

        // Start from (1,1) — always odd coordinates for passages
        startCellX = 1;
        startCellZ = 1;

        // Recursive backtracking using explicit stack to avoid StackOverflow
        Stack<int[]> stack = new Stack<>();
        maze[startCellX][startCellZ] = true;
        stack.push(new int[]{startCellX, startCellZ});

        Random rng = new Random();
        int[][] directions = {{0, 2}, {0, -2}, {2, 0}, {-2, 0}};

        int farthestDist = 0;
        bossCellX = MAZE_SIZE / 2;
        bossCellZ = MAZE_SIZE / 2;
        // Ensure boss cell is odd
        if (bossCellX % 2 == 0) bossCellX++;
        if (bossCellZ % 2 == 0) bossCellZ++;

        List<int[]> deadEnds = new ArrayList<>();

        while (!stack.isEmpty()) {
            int[] current = stack.peek();
            int cx = current[0], cz = current[1];

            // Shuffle directions
            List<int[]> shuffled = new ArrayList<>(Arrays.asList(directions));
            Collections.shuffle(shuffled, rng);

            boolean found = false;
            for (int[] dir : shuffled) {
                int nx = cx + dir[0];
                int nz = cz + dir[1];

                if (nx > 0 && nx < MAZE_SIZE - 1 && nz > 0 && nz < MAZE_SIZE - 1 && !maze[nx][nz]) {
                    // Carve passage
                    maze[cx + dir[0] / 2][cz + dir[1] / 2] = true;
                    maze[nx][nz] = true;
                    stack.push(new int[]{nx, nz});
                    found = true;

                    // Track farthest cell from start for boss room
                    int dist = Math.abs(nx - startCellX) + Math.abs(nz - startCellZ);
                    if (dist > farthestDist) {
                        farthestDist = dist;
                        bossCellX = nx;
                        bossCellZ = nz;
                    }
                    break;
                }
            }

            if (!found) {
                // Dead end — mark as potential special room
                deadEnds.add(new int[]{cx, cz});
                stack.pop();
            }
        }

        // Force boss room: carve a 5x5 area at boss cell
        carveBossRoom();

        // Select 3 key locations from dead ends (far from start, far from each other)
        selectKeyLocations(deadEnds);

        // Select horror/jumpscare locations
        selectHorrorLocations(deadEnds);

        // Select chase trigger locations (NPCs that chase the player)
        selectChaseLocations(deadEnds);
        
        // Validate connectivity and fix any isolated areas
        validateAndFixConnectivity();
    }

    private void carveBossRoom() {
        // Carve a 3x3 cell area centered on the boss cell (12x12 blocks for 20x20 maze)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int x = bossCellX + dx;
                int z = bossCellZ + dz;
                if (x > 0 && x < MAZE_SIZE - 1 && z > 0 && z < MAZE_SIZE - 1) {
                    maze[x][z] = true;
                }
            }
        }

        // Create entrance passage - ensure proper connection to maze passages
        // Connect from south side by carving a path to the nearest existing passage
        int entranceX = bossCellX;
        int entranceZ = bossCellZ - 2; // Start 2 cells south of boss room
        
        // Carve entrance corridor (1 cell wide, extending until it hits an existing passage)
        for (int z = entranceZ; z >= 0; z--) {
            maze[entranceX][z] = true;
            // Stop if we've reached an existing passage (not the boss room itself)
            if (z < entranceZ && maze[entranceX][z-1] && z-1 != bossCellZ) {
                break;
            }
        }
        
        // Also carve sideways connections to ensure accessibility
        maze[entranceX - 1][entranceZ] = true;
        maze[entranceX + 1][entranceZ] = true;
    }

    private void validateAndFixConnectivity() {
        // Flood fill from start position to find all reachable passages
        boolean[][] reachable = new boolean[MAZE_SIZE][MAZE_SIZE];
        Queue<int[]> queue = new LinkedList<>();
        
        // Start from the start position
        queue.add(new int[]{startCellX, startCellZ});
        reachable[startCellX][startCellZ] = true;
        
        int[][] directions = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
        
        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int cx = current[0], cz = current[1];
            
            for (int[] dir : directions) {
                int nx = cx + dir[0];
                int nz = cz + dir[1];
                
                if (nx >= 0 && nx < MAZE_SIZE && nz >= 0 && nz < MAZE_SIZE 
                    && maze[nx][nz] && !reachable[nx][nz]) {
                    reachable[nx][nz] = true;
                    queue.add(new int[]{nx, nz});
                }
            }
        }
        
        // Find any unreachable passages and connect them
        for (int x = 1; x < MAZE_SIZE - 1; x += 2) {
            for (int z = 1; z < MAZE_SIZE - 1; z += 2) {
                if (maze[x][z] && !reachable[x][z]) {
                    // Connect this isolated passage to the nearest reachable passage
                    connectIsolatedPassage(x, z, reachable);
                }
            }
        }
    }
    
    private void connectIsolatedPassage(int isolatedX, int isolatedZ, boolean[][] reachable) {
        // Find the nearest reachable passage and create a path
        int[][] directions = {{0, 2}, {0, -2}, {2, 0}, {-2, 0}};
        
        for (int[] dir : directions) {
            int nx = isolatedX + dir[0];
            int nz = isolatedZ + dir[1];
            
            if (nx > 0 && nx < MAZE_SIZE - 1 && nz > 0 && nz < MAZE_SIZE - 1) {
                if (reachable[nx][nz]) {
                    // Create a path to connect them
                    maze[isolatedX + dir[0]/2][isolatedZ + dir[1]/2] = true;
                    maze[nx][nz] = true;
                    return;
                }
            }
        }
        
        // If no direct connection available, create a path to the nearest passage
        int nearestX = startCellX;
        int nearestZ = startCellZ;
        double minDist = Double.MAX_VALUE;
        
        for (int x = 0; x < MAZE_SIZE; x++) {
            for (int z = 0; z < MAZE_SIZE; z++) {
                if (reachable[x][z]) {
                    double dist = Math.sqrt(Math.pow(x - isolatedX, 2) + Math.pow(z - isolatedZ, 2));
                    if (dist < minDist) {
                        minDist = dist;
                        nearestX = x;
                        nearestZ = z;
                    }
                }
            }
        }
        
        // Create a simple path toward the nearest reachable passage
        int dx = Integer.compare(nearestX, isolatedX);
        int dz = Integer.compare(nearestZ, isolatedZ);
        
        int currentX = isolatedX;
        int currentZ = isolatedZ;
        
        while (currentX != nearestX || currentZ != nearestZ) {
            if (currentX != nearestX) {
                currentX += dx;
                if (currentX > 0 && currentX < MAZE_SIZE - 1) {
                    maze[currentX][currentZ] = true;
                }
            }
            if (currentZ != nearestZ) {
                currentZ += dz;
                if (currentZ > 0 && currentZ < MAZE_SIZE - 1) {
                    maze[currentX][currentZ] = true;
                }
            }
        }
    }

    private void selectKeyLocations(List<int[]> deadEnds) {
        // Sort dead ends by distance from start (farthest first)
        deadEnds.sort((a, b) -> {
            int distA = Math.abs(a[0] - startCellX) + Math.abs(a[1] - startCellZ);
            int distB = Math.abs(b[0] - startCellX) + Math.abs(b[1] - startCellZ);
            return distB - distA;
        });

        // Remove dead ends too close to boss room
        deadEnds.removeIf(de ->
                Math.abs(de[0] - bossCellX) <= 2 && Math.abs(de[1] - bossCellZ) <= 2);

        // Pick 3 keys spread across different quadrants
        int[][] quadrants = {
                {0, 0, MAZE_SIZE / 2, MAZE_SIZE / 2},              // Top-left
                {MAZE_SIZE / 2, 0, MAZE_SIZE, MAZE_SIZE / 2},      // Top-right
                {0, MAZE_SIZE / 2, MAZE_SIZE / 2, MAZE_SIZE},      // Bottom-left
                {MAZE_SIZE / 2, MAZE_SIZE / 2, MAZE_SIZE, MAZE_SIZE} // Bottom-right
        };

        List<Integer> quadOrder = Arrays.asList(0, 1, 2, 3);
        Collections.shuffle(quadOrder);

        for (int qi = 0; qi < Math.min(3, quadOrder.size()); qi++) {
            int[] q = quadrants[quadOrder.get(qi)];
            for (int[] de : deadEnds) {
                if (de[0] >= q[0] && de[0] < q[2] && de[1] >= q[1] && de[1] < q[3]) {
                    // Check not too close to other keys
                    boolean tooClose = false;
                    for (int[] k : keyCells) {
                        if (Math.abs(de[0] - k[0]) + Math.abs(de[1] - k[1]) < 10) {
                            tooClose = true;
                            break;
                        }
                    }
                    if (!tooClose) {
                        keyCells.add(de);
                        break;
                    }
                }
            }
        }

        // If we couldn't fill 3 keys, add from remaining dead ends
        for (int[] de : deadEnds) {
            if (keyCells.size() >= 3) break;
            boolean isDuplicate = false;
            for (int[] k : keyCells) {
                if (k[0] == de[0] && k[1] == de[1]) {
                    isDuplicate = true;
                    break;
                }
            }
            if (!isDuplicate) keyCells.add(de);
        }
    }

    private void selectHorrorLocations(List<int[]> deadEnds) {
        // Select ~12 horror rooms spread around the maze
        Random rng = new Random();
        List<int[]> candidates = new ArrayList<>(deadEnds);
        // Remove cells used for keys
        candidates.removeAll(keyCells);
        Collections.shuffle(candidates, rng);

        int count = 0;
        for (int[] cell : candidates) {
            if (count >= 6) break; // Reduced from 12 to 6 for smaller maze
            // Not too close to start
            if (Math.abs(cell[0] - startCellX) + Math.abs(cell[1] - startCellZ) < 3) continue;
            // Not too close to boss
            if (Math.abs(cell[0] - bossCellX) + Math.abs(cell[1] - bossCellZ) < 2) continue;
            horrorCells.add(cell);
            count++;
        }
    }

    private void selectChaseLocations(List<int[]> deadEnds) {
        // Select ~8 chase trigger points in corridors (not dead ends)
        Random rng = new Random();
        List<int[]> corridors = new ArrayList<>();

        for (int x = 1; x < MAZE_SIZE - 1; x += 2) {
            for (int z = 1; z < MAZE_SIZE - 1; z += 2) {
                if (!maze[x][z]) continue;
                // Count open neighbors
                int neighbors = 0;
                if (x > 1 && maze[x - 2][z]) neighbors++;
                if (x < MAZE_SIZE - 2 && maze[x + 2][z]) neighbors++;
                if (z > 1 && maze[x][z - 2]) neighbors++;
                if (z < MAZE_SIZE - 2 && maze[x][z + 2]) neighbors++;
                if (neighbors >= 2) corridors.add(new int[]{x, z}); // Junction or corridor
            }
        }

        Collections.shuffle(corridors, rng);
        int count = 0;
        for (int[] cell : corridors) {
            if (count >= 4) break; // Reduced from 8 to 4 for smaller maze
            if (Math.abs(cell[0] - startCellX) + Math.abs(cell[1] - startCellZ) < 4) continue;
            if (Math.abs(cell[0] - bossCellX) + Math.abs(cell[1] - bossCellZ) < 2) continue;
            chaseCells.add(cell);
            count++;
        }
    }

    // ==================== MAZE BUILDING ====================

    private void buildMaze(Runnable onComplete) {
        // Pre-compute all block placements
        List<int[]> blockPlacements = new ArrayList<>(); // x, y, z, materialId

        // Material IDs: 0=deepslate, 1=deepslate_tiles, 2=black_concrete, 3=sculk, 4=soul_sand
        // 5=soul_lantern, 6=redstone_block (key marker), 7=red_nether_bricks (horror)
        // 8=crying_obsidian (boss room), 9=air

        for (int mx = 0; mx < MAZE_SIZE; mx++) {
            for (int mz = 0; mz < MAZE_SIZE; mz++) {
                int worldX = ORIGIN_X + mx * CELL_SIZE;
                int worldZ = ORIGIN_Z + mz * CELL_SIZE;

                if (maze[mx][mz]) {
                    // PASSAGE: floor + ceiling, no walls in passage area
                    for (int dx = 0; dx < CELL_SIZE; dx++) {
                        for (int dz = 0; dz < CELL_SIZE; dz++) {
                            int bx = worldX + dx;
                            int bz = worldZ + dz;

                            // Floor
                            blockPlacements.add(new int[]{bx, FLOOR_Y, bz, 0}); // deepslate

                            // Ceiling
                            blockPlacements.add(new int[]{bx, CEILING_Y, bz, 3}); // sculk

                            // Clear passage (air)
                            for (int y = WALL_MIN_Y; y < CEILING_Y; y++) {
                                blockPlacements.add(new int[]{bx, y, bz, 9}); // air
                            }
                        }
                    }
                } else {
                    // WALL: solid blocks
                    for (int dx = 0; dx < CELL_SIZE; dx++) {
                        for (int dz = 0; dz < CELL_SIZE; dz++) {
                            int bx = worldX + dx;
                            int bz = worldZ + dz;

                            // Floor
                            blockPlacements.add(new int[]{bx, FLOOR_Y, bz, 0});

                            // Walls (varied materials)
                            for (int y = WALL_MIN_Y; y <= CEILING_Y; y++) {
                                if (y == CEILING_Y) {
                                    blockPlacements.add(new int[]{bx, y, bz, 3}); // sculk ceiling
                                } else if (y == WALL_MIN_Y) {
                                    blockPlacements.add(new int[]{bx, y, bz, 1}); // deepslate_tiles base
                                } else {
                                    // Alternate between deepslate_tiles and black_concrete
                                    blockPlacements.add(new int[]{bx, y, bz,
                                            ((bx + bz) % 3 == 0) ? 2 : 1});
                                }
                            }
                        }
                    }
                }
            }
        }

        // Build outer walls (extra layer to prevent escape)
        for (int x = ORIGIN_X - CELL_SIZE; x <= ORIGIN_X + MAZE_BLOCKS + CELL_SIZE; x++) {
            for (int z : new int[]{ORIGIN_Z - CELL_SIZE, ORIGIN_Z + MAZE_BLOCKS}) {
                for (int y = FLOOR_Y; y <= CEILING_Y + 2; y++) {
                    blockPlacements.add(new int[]{x, y, z, 2});
                }
            }
        }
        for (int z = ORIGIN_Z - CELL_SIZE; z <= ORIGIN_Z + MAZE_BLOCKS + CELL_SIZE; z++) {
            for (int x : new int[]{ORIGIN_X - CELL_SIZE, ORIGIN_X + MAZE_BLOCKS}) {
                for (int y = FLOOR_Y; y <= CEILING_Y + 2; y++) {
                    blockPlacements.add(new int[]{x, y, z, 2});
                }
            }
        }

        // Add special room decorations to placements
        addBossRoomDecorations(blockPlacements);
        addKeyRoomDecorations(blockPlacements);
        addHorrorRoomDecorations(blockPlacements);

        // Build in batches of 8000 blocks per tick
        final int BATCH_SIZE = 8000;
        final int totalBlocks = blockPlacements.size();

        new BukkitRunnable() {
            int index = 0;

            @Override
            public void run() {
                if (world == null || deleted) {
                    cancel();
                    return;
                }

                int end = Math.min(index + BATCH_SIZE, totalBlocks);
                for (int i = index; i < end; i++) {
                    int[] bp = blockPlacements.get(i);
                    Block block = world.getBlockAt(bp[0], bp[1], bp[2]);
                    Material mat = getMaterial(bp[3]);
                    if (mat != null) {
                        block.setType(mat, false);
                    }
                }

                index = end;

                if (index >= totalBlocks) {
                    cancel();
                    generated = true;

                    // Compute world locations
                    computeLocations();

                    plugin.getLogger().info("[Labyrinth] Maze built: " + totalBlocks + " blocks in world " + worldName);

                    if (onComplete != null) {
                        Bukkit.getScheduler().runTask(plugin, onComplete);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private Material getMaterial(int id) {
        switch (id) {
            case 0: return Material.DEEPSLATE;
            case 1: return Material.DEEPSLATE_TILES;
            case 2: return Material.BLACK_CONCRETE;
            case 3: return Material.SCULK;
            case 4: return Material.SOUL_SAND;
            case 5: return Material.SOUL_LANTERN;
            case 6: return Material.SOUL_FIRE;
            case 7: return Material.RED_NETHER_BRICKS;
            case 8: return Material.CRYING_OBSIDIAN;
            case 9: return Material.AIR;
            case 10: return Material.CRIMSON_NYLIUM;
            case 11: return Material.SHROOMLIGHT;
            case 12: return Material.CRIMSON_STEM;
            default: return Material.DEEPSLATE_TILES;
        }
    }

    private void addBossRoomDecorations(List<int[]> placements) {
        int cx = ORIGIN_X + bossCellX * CELL_SIZE + CELL_SIZE / 2;
        int cz = ORIGIN_Z + bossCellZ * CELL_SIZE + CELL_SIZE / 2;
        int BOSS_CEILING = CEILING_Y + 3; // Y=67 (reduced for smaller room)

        // Boss room: 3x3 cells = 12x12 blocks, decorated with crying obsidian
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                int bx = cx + dx;
                int bz = cz + dz;

                boolean isEdge = Math.abs(dx) == 6 || Math.abs(dz) == 6;
                boolean isBorder = Math.abs(dx) >= 5 || Math.abs(dz) >= 5;

                if (isEdge) {
                    // WALLS around boss room perimeter — go up to raised ceiling
                    for (int y = WALL_MIN_Y; y < BOSS_CEILING; y++) {
                        placements.add(new int[]{bx, y, bz, 1}); // deepslate_tiles walls
                    }
                    placements.add(new int[]{bx, BOSS_CEILING, bz, 3}); // sculk ceiling
                    // Soul lanterns on the inside of walls
                    if ((dx + dz) % 4 == 0) {
                        int lx = bx + (dx == 6 ? -1 : dx == -6 ? 1 : 0);
                        int lz = bz + (dz == 6 ? -1 : dz == -6 ? 1 : 0);
                        placements.add(new int[]{lx, WALL_MIN_Y + 2, lz, 5}); // soul_lantern
                    }
                } else {
                    // Interior: raise ceiling
                    for (int y = CEILING_Y; y < BOSS_CEILING; y++) {
                        placements.add(new int[]{bx, y, bz, 9}); // air
                    }
                    placements.add(new int[]{bx, BOSS_CEILING, bz, 3}); // sculk ceiling higher
                }

                // Floor: crying obsidian pattern
                if (!isBorder && (Math.abs(dx) + Math.abs(dz)) % 3 == 0) {
                    placements.add(new int[]{bx, FLOOR_Y, bz, 8}); // crying_obsidian
                }

                // Soul sand border ring
                if (isBorder && !isEdge) {
                    placements.add(new int[]{bx, FLOOR_Y, bz, 4}); // soul_sand
                }
            }
        }

        // Add extra wall ring OUTSIDE the boss room (1 block thick) going up to boss ceiling
        // This seals the gap between normal maze ceiling (Y=64) and boss room ceiling (Y=67)
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                if (Math.abs(dx) != 7 && Math.abs(dz) != 7) continue; // only outer ring
                int bx = cx + dx;
                int bz = cz + dz;
                for (int y = CEILING_Y; y <= BOSS_CEILING; y++) {
                    placements.add(new int[]{bx, y, bz, 2}); // black_concrete
                }
            }
        }
    }

    private void addKeyRoomDecorations(List<int[]> placements) {
        for (int[] cell : keyCells) {
            int cx = ORIGIN_X + cell[0] * CELL_SIZE + CELL_SIZE / 2;
            int cz = ORIGIN_Z + cell[1] * CELL_SIZE + CELL_SIZE / 2;

            // Crimson nylium floor (clearly visible crimson color) — 5x5 area
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    placements.add(new int[]{cx + dx, FLOOR_Y, cz + dz, 10}); // crimson_nylium
                }
            }

            // Soul sand under the center for soul fire support
            placements.add(new int[]{cx, FLOOR_Y, cz, 4}); // soul_sand

            // Soul fire on top (visible flame)
            placements.add(new int[]{cx, FLOOR_Y + 1, cz, 6}); // soul_fire

            // SHROOMLIGHT beacon pillar — very bright and visible from far away
            placements.add(new int[]{cx, FLOOR_Y + 2, cz, 11}); // shroomlight at Y=61
            placements.add(new int[]{cx, FLOOR_Y + 3, cz, 11}); // shroomlight at Y=62

            // Crimson stem pillars at corners (visible markers)
            for (int[] corner : new int[][]{{-2, -2}, {-2, 2}, {2, -2}, {2, 2}}) {
                placements.add(new int[]{cx + corner[0], WALL_MIN_Y, cz + corner[1], 12}); // crimson_stem
                placements.add(new int[]{cx + corner[0], WALL_MIN_Y + 1, cz + corner[1], 12}); // crimson_stem
            }
        }
    }

    private void addHorrorRoomDecorations(List<int[]> placements) {
        for (int[] cell : horrorCells) {
            int cx = ORIGIN_X + cell[0] * CELL_SIZE + CELL_SIZE / 2;
            int cz = ORIGIN_Z + cell[1] * CELL_SIZE + CELL_SIZE / 2;

            // Red nether brick accents
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    placements.add(new int[]{cx + dx, FLOOR_Y, cz + dz, 7}); // red_nether_bricks floor
                }
            }
        }
    }

    // ==================== LOCATION COMPUTATION ====================

    private void computeLocations() {
        // Start location
        int sx = ORIGIN_X + startCellX * CELL_SIZE + CELL_SIZE / 2;
        int sz = ORIGIN_Z + startCellZ * CELL_SIZE + CELL_SIZE / 2;
        startLocation = new Location(world, sx + 0.5, WALL_MIN_Y, sz + 0.5);

        // Boss room center
        int bx = ORIGIN_X + bossCellX * CELL_SIZE + CELL_SIZE / 2;
        int bz = ORIGIN_Z + bossCellZ * CELL_SIZE + CELL_SIZE / 2;
        bossRoomCenter = new Location(world, bx + 0.5, WALL_MIN_Y, bz + 0.5);

        // Key locations
        for (int[] cell : keyCells) {
            int kx = ORIGIN_X + cell[0] * CELL_SIZE + CELL_SIZE / 2;
            int kz = ORIGIN_Z + cell[1] * CELL_SIZE + CELL_SIZE / 2;
            keyLocations.add(new Location(world, kx + 0.5, WALL_MIN_Y, kz + 0.5));
        }

        // Horror locations
        for (int[] cell : horrorCells) {
            int hx = ORIGIN_X + cell[0] * CELL_SIZE + CELL_SIZE / 2;
            int hz = ORIGIN_Z + cell[1] * CELL_SIZE + CELL_SIZE / 2;
            horrorLocations.add(new Location(world, hx + 0.5, WALL_MIN_Y, hz + 0.5));
        }

        // Chase locations
        for (int[] cell : chaseCells) {
            int chx = ORIGIN_X + cell[0] * CELL_SIZE + CELL_SIZE / 2;
            int chz = ORIGIN_Z + cell[1] * CELL_SIZE + CELL_SIZE / 2;
            chaseLocations.add(new Location(world, chx + 0.5, WALL_MIN_Y, chz + 0.5));
        }
    }

    // ==================== WORLD DELETION ====================

    public void deleteWorld() {
        if (deleted) return;
        deleted = true;

        if (world != null) {
            try {
                // Teleport any remaining players out
                for (org.bukkit.entity.Player p : world.getPlayers()) {
                    p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
                }

                // Remove all entities safely
                try {
                    world.getEntities().forEach(e -> {
                        try { e.remove(); } catch (Exception ignored) {}
                    });
                } catch (Exception ignored) {}

                // Wait a tick before unloading to ensure cleanup
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    try {
                        if (Bukkit.getWorld(worldName) != null) {
                            Bukkit.unloadWorld(world, false);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("[Labyrinth] Error unloading world: " + e.getMessage());
                    }
                }, 1L);
            } catch (Exception e) {
                plugin.getLogger().severe("[Labyrinth] Error during world cleanup: " + e.getMessage());
            }
        }

        // Delete world folder asynchronously with delay
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Wait 2 seconds for world to fully unload
                Thread.sleep(2000);
                
                File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
                if (worldFolder.exists()) {
                    deleteDirectory(worldFolder);
                    plugin.getLogger().info("[Labyrinth] World deleted: " + worldName);
                }
            } catch (Exception e) {
                plugin.getLogger().severe("[Labyrinth] Error deleting world folder: " + e.getMessage());
            }
        });
    }

    private void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }

    // ==================== VOID CHUNK GENERATOR ====================

    public static class VoidGenerator extends ChunkGenerator {
        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateBedrock() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }
    }

    // ==================== GETTERS ====================

    public World getWorld() { return world; }
    public String getWorldName() { return worldName; }
    public boolean isGenerated() { return generated; }
    public boolean isDeleted() { return deleted; }
    public Location getStartLocation() { return startLocation; }
    public Location getBossRoomCenter() { return bossRoomCenter; }
    public List<Location> getKeyLocations() { return keyLocations; }
    public List<Location> getHorrorLocations() { return horrorLocations; }
    public List<Location> getChaseLocations() { return chaseLocations; }
    public boolean[][] getMaze() { return maze; }
}
