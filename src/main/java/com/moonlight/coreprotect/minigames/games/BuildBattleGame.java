package com.moonlight.coreprotect.minigames.games;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.minigames.MiniGame;
import com.moonlight.coreprotect.minigames.MiniGameManager;
import com.moonlight.coreprotect.minigames.MiniGameType;
import com.moonlight.coreprotect.minigames.MiniGameWorld;
import com.moonlight.coreprotect.util.ActionBarUtil;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Build Battle: Replica la figura modelo en tu parcela.
 * - Arena central con modelo a copiar
 * - Parcelas individuales alrededor
 * - 120 segundos por ronda para replicar
 * - Sistema de comparación de similitud
 * - Torneo: se elimina al peor cada ronda
 * - Figuras del mundo real con variaciones
 */
public class BuildBattleGame extends MiniGame {

    // Plot configuration
    private static final int PLOT_SIZE = 7;        // 7x7 build area
    private static final int PLOT_HEIGHT = 7;       // max build height
    private static final int PLOT_SPACING = 4;      // gap between plots
    private static final int MODEL_SIZE = 7;        // model display area
    private static final int ROUND_TIME = 120;      // seconds per round

    // Arena layout
    private static final int ARENA_Y = 65;
    private static final int MODEL_CENTER_X = 0;
    private static final int MODEL_CENTER_Z = 0;

    // Memorize phase
    private static final int MEMORIZE_TIME = 10;   // seconds to memorize

    // State
    private int roundNumber = 0;
    private int roundTimer = 0;
    private boolean roundActive = false;
    private boolean memorizePhase = false;          // true during 10s memorize
    private boolean buildingAllowed = false;         // true only during build phase
    private final List<UUID> tournamentPlayers = new ArrayList<>();
    private final Map<UUID, PlotArea> playerPlots = new LinkedHashMap<>();
    private Material[][][] currentModel = null;
    private String currentModelName = "";
    private int modelIndex = 0;

    // Plot positions
    private final List<Location> plotOrigins = new ArrayList<>();

    public BuildBattleGame(CoreProtectPlugin plugin, MiniGameManager manager) {
        super(plugin, manager, MiniGameType.BUILD_BATTLE);
    }

    // ==================== PUBLIC API FOR LISTENER ====================

    /**
     * Checks if a block location is inside the given player's plot.
     */
    public boolean isInsidePlayerPlot(UUID uuid, Location loc) {
        PlotArea plot = playerPlots.get(uuid);
        if (plot == null) return false;
        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();
        return bx >= plot.x && bx < plot.x + plot.width
                && by >= plot.y && by < plot.y + plot.height
                && bz >= plot.z && bz < plot.z + plot.width;
    }

    /**
     * Whether players are currently allowed to build.
     */
    public boolean isBuildingAllowed() {
        return buildingAllowed;
    }

    /**
     * Whether we are in the memorize phase (no building).
     */
    public boolean isMemorizePhase() {
        return memorizePhase;
    }

    // ==================== ARENA ====================

    @Override
    public void buildArena(World world) {
        // Clear large area
        for (int x = -60; x <= 60; x++) {
            for (int z = -60; z <= 60; z++) {
                for (int y = ARENA_Y - 5; y <= ARENA_Y + 25; y++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR);
                }
            }
        }

        // Build floor (decorated stone brick pattern)
        for (int x = -55; x <= 55; x++) {
            for (int z = -55; z <= 55; z++) {
                Material floor;
                if ((Math.abs(x) + Math.abs(z)) % 2 == 0) {
                    floor = Material.SMOOTH_STONE;
                } else {
                    floor = Material.STONE_BRICKS;
                }
                world.getBlockAt(x, ARENA_Y - 1, z).setType(floor);
            }
        }

        // Calculate plot positions in a circle around the center
        calculatePlotPositions();

        // Build plot platforms
        for (Location origin : plotOrigins) {
            buildPlot(world, origin);
        }

        // Decorative pillars around arena
        buildDecorations(world);
    }

    private void calculatePlotPositions() {
        calculatePlotPositions(8); // default
    }

    private void calculatePlotPositions(int count) {
        plotOrigins.clear();
        // Distribute plots in concentric rings; first ring at radius 20, next at 40, etc.
        int perRing = 8;
        int baseRadius = 20;
        int placed = 0;
        int ring = 0;
        while (placed < count) {
            int radius = baseRadius + ring * 20;
            int slotsThisRing = Math.min(perRing + ring * 4, count - placed);
            for (int i = 0; i < slotsThisRing; i++) {
                double angle = (Math.PI * 2.0 / slotsThisRing) * i;
                int px = (int) (MODEL_CENTER_X + Math.cos(angle) * radius) - PLOT_SIZE / 2;
                int pz = (int) (MODEL_CENTER_Z + Math.sin(angle) * radius) - PLOT_SIZE / 2;
                plotOrigins.add(new Location(null, px, ARENA_Y, pz));
                placed++;
            }
            ring++;
        }
    }

    private void buildPlot(World world, Location origin) {
        int ox = origin.getBlockX();
        int oz = origin.getBlockZ();

        // Floor — deepslate border + white concrete interior
        for (int x = ox - 1; x <= ox + PLOT_SIZE; x++) {
            for (int z = oz - 1; z <= oz + PLOT_SIZE; z++) {
                if (x == ox - 1 || x == ox + PLOT_SIZE || z == oz - 1 || z == oz + PLOT_SIZE) {
                    world.getBlockAt(x, ARENA_Y - 1, z).setType(Material.POLISHED_DEEPSLATE);
                } else {
                    world.getBlockAt(x, ARENA_Y - 1, z).setType(Material.WHITE_CONCRETE);
                }
            }
        }

        // Opaque walls (BLACK_CONCRETE) + BARRIER outside so players can't see or escape
        int wallHeight = PLOT_HEIGHT + 4; // extra height above build area for flying
        for (int y = ARENA_Y; y <= ARENA_Y + wallHeight; y++) {
            // North & South walls
            for (int x = ox - 1; x <= ox + PLOT_SIZE; x++) {
                world.getBlockAt(x, y, oz - 1).setType(Material.BLACK_CONCRETE);
                world.getBlockAt(x, y, oz - 2).setType(Material.BARRIER);
                world.getBlockAt(x, y, oz + PLOT_SIZE).setType(Material.BLACK_CONCRETE);
                world.getBlockAt(x, y, oz + PLOT_SIZE + 1).setType(Material.BARRIER);
            }
            // West & East walls
            for (int z = oz; z < oz + PLOT_SIZE; z++) {
                world.getBlockAt(ox - 1, y, z).setType(Material.BLACK_CONCRETE);
                world.getBlockAt(ox - 2, y, z).setType(Material.BARRIER);
                world.getBlockAt(ox + PLOT_SIZE, y, z).setType(Material.BLACK_CONCRETE);
                world.getBlockAt(ox + PLOT_SIZE + 1, y, z).setType(Material.BARRIER);
            }
        }

        // Opaque ceiling (BLACK_CONCRETE) + BARRIER above
        for (int x = ox - 1; x <= ox + PLOT_SIZE; x++) {
            for (int z = oz - 1; z <= oz + PLOT_SIZE; z++) {
                world.getBlockAt(x, ARENA_Y + wallHeight + 1, z).setType(Material.BLACK_CONCRETE);
                world.getBlockAt(x, ARENA_Y + wallHeight + 2, z).setType(Material.BARRIER);
            }
        }

        // Light in corners (on the floor border, visible)
        world.getBlockAt(ox - 1, ARENA_Y - 1, oz - 1).setType(Material.GLOWSTONE);
        world.getBlockAt(ox + PLOT_SIZE, ARENA_Y - 1, oz - 1).setType(Material.GLOWSTONE);
        world.getBlockAt(ox - 1, ARENA_Y - 1, oz + PLOT_SIZE).setType(Material.GLOWSTONE);
        world.getBlockAt(ox + PLOT_SIZE, ARENA_Y - 1, oz + PLOT_SIZE).setType(Material.GLOWSTONE);
    }

    /**
     * Converts plot walls + ceiling between BLACK_CONCRETE (opaque) and BARRIER (transparent).
     * Used to reveal builds during evaluation.
     */
    private void setPlotWallsTransparent(World world, boolean transparent) {
        Material from = transparent ? Material.BLACK_CONCRETE : Material.BARRIER;
        Material to = transparent ? Material.BARRIER : Material.BLACK_CONCRETE;
        int wallHeight = PLOT_HEIGHT + 4;
        for (Location origin : plotOrigins) {
            int ox = origin.getBlockX();
            int oz = origin.getBlockZ();
            for (int y = ARENA_Y; y <= ARENA_Y + wallHeight; y++) {
                for (int x = ox - 1; x <= ox + PLOT_SIZE; x++) {
                    if (world.getBlockAt(x, y, oz - 1).getType() == from)
                        world.getBlockAt(x, y, oz - 1).setType(to);
                    if (world.getBlockAt(x, y, oz + PLOT_SIZE).getType() == from)
                        world.getBlockAt(x, y, oz + PLOT_SIZE).setType(to);
                }
                for (int z = oz; z < oz + PLOT_SIZE; z++) {
                    if (world.getBlockAt(ox - 1, y, z).getType() == from)
                        world.getBlockAt(ox - 1, y, z).setType(to);
                    if (world.getBlockAt(ox + PLOT_SIZE, y, z).getType() == from)
                        world.getBlockAt(ox + PLOT_SIZE, y, z).setType(to);
                }
            }
            // Ceiling
            for (int x = ox - 1; x <= ox + PLOT_SIZE; x++) {
                for (int z = oz - 1; z <= oz + PLOT_SIZE; z++) {
                    if (world.getBlockAt(x, ARENA_Y + wallHeight + 1, z).getType() == from)
                        world.getBlockAt(x, ARENA_Y + wallHeight + 1, z).setType(to);
                }
            }
        }
    }

    private void buildDecorations(World world) {
        // Four pillars at corners of the arena
        int[][] corners = {{-35, -35}, {35, -35}, {-35, 35}, {35, 35}};
        for (int[] corner : corners) {
            for (int y = ARENA_Y; y <= ARENA_Y + 12; y++) {
                world.getBlockAt(corner[0], y, corner[1]).setType(Material.QUARTZ_PILLAR);
            }
            world.getBlockAt(corner[0], ARENA_Y + 13, corner[1]).setType(Material.SEA_LANTERN);
        }
    }

    // ==================== SPAWNS ====================

    @Override
    public List<Location> getSpawnLocations(World world) {
        List<Location> spawns = new ArrayList<>();
        for (Location origin : plotOrigins) {
            spawns.add(new Location(world,
                    origin.getBlockX() + PLOT_SIZE / 2.0 + 0.5,
                    ARENA_Y,
                    origin.getBlockZ() + PLOT_SIZE / 2.0 + 0.5));
        }
        return spawns;
    }

    // ==================== GAME LOGIC ====================

    @Override
    public void startGameLogic() {
        // Initialize tournament players
        tournamentPlayers.clear();
        tournamentPlayers.addAll(alivePlayers);

        // Generate enough plots for all players
        if (tournamentPlayers.size() > plotOrigins.size()) {
            calculatePlotPositions(tournamentPlayers.size());
            World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
            if (world != null) {
                for (Location origin : plotOrigins) {
                    buildPlot(world, origin);
                }
            }
        }

        // Assign plots — every player gets one
        int idx = 0;
        for (UUID uuid : tournamentPlayers) {
            if (idx < plotOrigins.size()) {
                Location plotOrigin = plotOrigins.get(idx);
                playerPlots.put(uuid, new PlotArea(
                        plotOrigin.getBlockX(), ARENA_Y, plotOrigin.getBlockZ(),
                        PLOT_SIZE, PLOT_HEIGHT));
            }
            idx++;
        }

        // Set creative mode for all players
        for (UUID uuid : tournamentPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;
            p.setGameMode(GameMode.CREATIVE);
            p.getInventory().clear();
        }

        // Start first round — fresh random shuffle every game
        modelIndex = 0;
        shuffledModels = null;
        shuffledIndex = 0;
        startNextRound();
    }

    /**
     * Gives each player the exact blocks used in the current model (64 of each unique material).
     */
    private void giveModelBlocks() {
        if (currentModel == null) return;

        // Count unique materials in the model
        Set<Material> modelMaterials = new LinkedHashSet<>();
        for (int dx = 0; dx < MODEL_SIZE; dx++)
            for (int dy = 0; dy < PLOT_HEIGHT; dy++)
                for (int dz = 0; dz < MODEL_SIZE; dz++) {
                    Material mat = currentModel[dx][dy][dz];
                    if (mat != null && mat != Material.AIR) {
                        modelMaterials.add(mat);
                    }
                }

        for (UUID uuid : tournamentPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;
            p.getInventory().clear();
            int slot = 0;
            for (Material mat : modelMaterials) {
                if (slot >= 36) break;
                p.getInventory().setItem(slot, new ItemStack(mat, 64));
                slot++;
            }
        }
    }

    private void startNextRound() {
        if (tournamentPlayers.size() <= 1) {
            // Winner determined
            if (!tournamentPlayers.isEmpty()) {
                Player winner = Bukkit.getPlayer(tournamentPlayers.get(0));
                endGame(winner);
            } else {
                endGame(null);
            }
            return;
        }

        roundNumber++;
        roundActive = true;
        memorizePhase = true;
        buildingAllowed = false;

        // Clear all plots
        clearAllPlots();

        // Select model
        currentModel = selectModel(modelIndex);
        modelIndex++;

        // Place model inside EACH player's plot for memorization
        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (world != null) {
            for (UUID uuid : tournamentPlayers) {
                PlotArea plot = playerPlots.get(uuid);
                if (plot != null) {
                    placeModelInPlot(world, plot);
                }
            }
        }

        // Announce round
        String roundName = getRoundName();
        broadcastGame("");
        broadcastGame(SmallCaps.convert("§a§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        broadcastGame(SmallCaps.convert("§a§l  🔨 " + roundName + " §a§l🔨"));
        broadcastGame(SmallCaps.convert("§7  Figura: §e" + currentModelName));
        broadcastGame(SmallCaps.convert("§7  ¡Memoriza! §e" + MEMORIZE_TIME + "s §7para observar, luego §e" + ROUND_TIME + "s §7para construir"));
        broadcastGame(SmallCaps.convert("§7  Jugadores restantes: §a" + tournamentPlayers.size()));
        broadcastGame(SmallCaps.convert("§a§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        broadcastGame("");

        titleAll("§e§l� MEMORIZA", "§7Figura: §e" + currentModelName + " §7- §e" + MEMORIZE_TIME + "s");
        soundAll(Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);

        // SPECTATOR mode first, then TP to their plot (above model to observe)
        for (UUID uuid : tournamentPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            PlotArea plot = playerPlots.get(uuid);
            if (p != null && p.isOnline() && plot != null && world != null) {
                p.getInventory().clear();
                p.setGameMode(GameMode.SPECTATOR);
                p.teleport(new Location(world,
                        plot.x + PLOT_SIZE / 2.0 + 0.5,
                        ARENA_Y + PLOT_HEIGHT + 2,
                        plot.z + PLOT_SIZE / 2.0 + 0.5));
            }
        }

        // Start memorize countdown, then transition to build phase
        roundTimer = MEMORIZE_TIME;
        new BukkitRunnable() {
            int memTimer = MEMORIZE_TIME;

            @Override
            public void run() {
                if (!running || !roundActive) {
                    cancel();
                    return;
                }

                memTimer--;

                // Actionbar countdown during memorize
                for (UUID uuid : tournamentPlayers) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        String color = memTimer <= 3 ? "§c" : "§e";
                        ActionBarUtil.send(p, "§e§l👁 MEMORIZA §7| " + color + memTimer + "s §7restantes");
                    }
                }

                if (memTimer <= 3 && memTimer > 0) {
                    soundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 2.0f);
                }

                if (memTimer <= 0) {
                    cancel();
                    // Transition to build phase
                    startBuildPhase();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /**
     * Called after memorize phase ends: clears model from plots, gives blocks, starts build timer.
     */
    private void startBuildPhase() {
        memorizePhase = false;
        buildingAllowed = true;
        roundTimer = ROUND_TIME;

        // Clear all plots (remove model)
        clearAllPlots();

        // Give model-specific blocks
        giveModelBlocks();

        // TP players back to their plot and switch to creative for building
        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        for (UUID uuid : tournamentPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;
            PlotArea plot = playerPlots.get(uuid);
            if (plot != null && world != null) {
                p.setGameMode(GameMode.CREATIVE);
                p.teleport(new Location(world,
                        plot.x + PLOT_SIZE / 2.0 + 0.5,
                        ARENA_Y + 1,
                        plot.z + PLOT_SIZE / 2.0 + 0.5));
            } else {
                p.setGameMode(GameMode.CREATIVE);
            }
        }

        titleAll("§a§l🔨 ¡CONSTRUYE!", "§7Tienes §e" + ROUND_TIME + "s §7para replicar la figura");
        soundAll(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f);
    }

    private String getRoundName() {
        int remaining = tournamentPlayers.size();
        if (remaining <= 2) return "§e§lFINAL";
        if (remaining <= 4) return "§6§lSEMIFINAL";
        return "§a§lRONDA " + roundNumber;
    }

    // ==================== TICK ====================

    @Override
    public void onTick() {
        if (!roundActive) return;
        // During memorize phase, the BukkitRunnable handles timing — skip here
        if (memorizePhase) return;
        if (!buildingAllowed) return;

        roundTimer--;

        // Timer warnings
        if (roundTimer == 60 || roundTimer == 30 || roundTimer == 10 || roundTimer <= 5) {
            String color = roundTimer <= 5 ? "§c§l" : roundTimer <= 10 ? "§c" : "§e";
            broadcastGame(SmallCaps.convert("§a§l🔨 §7Quedan " + color + roundTimer + "s"));
            if (roundTimer <= 5) {
                soundAll(Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, roundTimer == 0 ? 2.0f : 1.5f);
            }
        }

        // Actionbar timer
        for (UUID uuid : tournamentPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                String timerColor = roundTimer <= 10 ? "§c" : roundTimer <= 30 ? "§e" : "§a";
                ActionBarUtil.send(p, timerColor + "⏱ " + roundTimer + "s §7| §a🔨 " + currentModelName);
            }
        }

        if (roundTimer <= 0) {
            endRound();
            return;
        }

        // Check if any player has 100% similarity — end round immediately
        if (roundTimer % 5 == 0) { // Check every 5 seconds to avoid lag
            World w = Bukkit.getWorld(MiniGameWorld.getWorldName());
            if (w != null) {
                for (UUID uuid : tournamentPlayers) {
                    PlotArea plot = playerPlots.get(uuid);
                    if (plot != null) {
                        double score = calculateSimilarity(w, plot);
                        if (score >= 0.99) {
                            Player perfectP = Bukkit.getPlayer(uuid);
                            String pName = perfectP != null ? perfectP.getName() : "???";
                            broadcastGame(SmallCaps.convert("§a§l✨ ¡" + pName + " completó la figura al 100%! ✨"));
                            soundAll(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1.5f);
                            endRound();
                            return;
                        }
                    }
                }
            }
        }
    }

    private void endRound() {
        roundActive = false;
        buildingAllowed = false;
        memorizePhase = false;

        broadcastGame(SmallCaps.convert("§c§l⏱ ¡TIEMPO! §7Evaluando construcciones..."));
        soundAll(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);

        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());

        // Make walls transparent (BARRIER) so everyone can see all builds
        if (world != null) {
            setPlotWallsTransparent(world, true);
        }

        // Switch players to adventure mode to prevent further building
        for (UUID uuid : tournamentPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.setGameMode(GameMode.ADVENTURE);
                p.setAllowFlight(true);
                p.setFlying(true);
                p.getInventory().clear();
            }
        }

        // Calculate scores
        Map<UUID, Double> scores = new LinkedHashMap<>();
        for (UUID uuid : tournamentPlayers) {
            PlotArea plot = playerPlots.get(uuid);
            if (plot != null && world != null) {
                double score = calculateSimilarity(world, plot);
                scores.put(uuid, score);
            } else {
                scores.put(uuid, 0.0);
            }
        }

        // Sort by score (highest first)
        List<Map.Entry<UUID, Double>> sorted = new ArrayList<>(scores.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        // Display scores
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            broadcastGame("");
            broadcastGame(SmallCaps.convert("§a§l🏆 RESULTADOS DE LA RONDA:"));
            int rank = 1;
            for (Map.Entry<UUID, Double> entry : sorted) {
                Player p = Bukkit.getPlayer(entry.getKey());
                String name = p != null ? p.getName() : "???";
                String medal = rank == 1 ? "§6§l🥇" : rank == 2 ? "§f🥈" : rank == 3 ? "§c🥉" : "§7§l" + rank + ".";
                int pct = (int) (entry.getValue() * 100);
                String pctColor = pct >= 80 ? "§a" : pct >= 50 ? "§e" : "§c";
                broadcastGame("  " + medal + " §f" + name + " §7- " + pctColor + pct + "% §7similitud");
                rank++;
            }
            broadcastGame("");
        }, 20L);

        // Eliminate lowest scorer(s) after 5 seconds
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!running) return;

            if (sorted.size() >= 2) {
                // Mass elimination: if 6+ players have 0%, eliminate all of them
                List<UUID> zeroPlayers = new ArrayList<>();
                for (Map.Entry<UUID, Double> entry : sorted) {
                    if ((int) (entry.getValue() * 100) == 0) {
                        zeroPlayers.add(entry.getKey());
                    }
                }
                if (zeroPlayers.size() >= 2) {
                    broadcastGame(SmallCaps.convert("§c§l☠ ¡ELIMINACIÓN MASIVA! §7" + zeroPlayers.size() + " jugadores con 0% eliminados."));
                    soundAll(Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.5f);
                    for (UUID zeroUUID : zeroPlayers) {
                        tournamentPlayers.remove(zeroUUID);
                        eliminatePlayer(zeroUUID);
                    }

                    // Restore opaque walls before next round
                    World w = Bukkit.getWorld(MiniGameWorld.getWorldName());
                    if (w != null) setPlotWallsTransparent(w, false);

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!running) return;
                        startNextRound();
                    }, 100L);
                    return;
                }

                // Check for tiebreaker: if the 2 worst players have the same score
                Map.Entry<UUID, Double> worst = sorted.get(sorted.size() - 1);
                Map.Entry<UUID, Double> secondWorst = sorted.get(sorted.size() - 2);
                double worstScore = worst.getValue();
                double secondWorstScore = secondWorst.getValue();

                if (Math.abs(worstScore - secondWorstScore) < 0.01) {
                    // TIEBREAKER — do an extra round, no elimination
                    broadcastGame(SmallCaps.convert("§e§l⚡ ¡EMPATE! §7Los jugadores empatados jugarán una ronda de desempate."));
                    soundAll(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);

                    // Restore opaque walls for the next round
                    World w = Bukkit.getWorld(MiniGameWorld.getWorldName());
                    if (w != null) setPlotWallsTransparent(w, false);

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!running) return;
                        startNextRound();
                    }, 100L);
                    return;
                }

                // Normal elimination: remove worst player
                UUID eliminatedUUID = worst.getKey();
                Player eliminated = Bukkit.getPlayer(eliminatedUUID);
                String eName = eliminated != null ? eliminated.getName() : "???";

                broadcastGame(SmallCaps.convert("§c§l✖ §f" + eName + " §7ha sido eliminado con §c"
                        + (int) (worst.getValue() * 100) + "% §7de similitud."));

                tournamentPlayers.remove(eliminatedUUID);
                eliminatePlayer(eliminatedUUID);

                soundAll(Sound.ENTITY_BLAZE_DEATH, 0.5f, 0.5f);
            }

            // Restore opaque walls before next round
            World w = Bukkit.getWorld(MiniGameWorld.getWorldName());
            if (w != null) setPlotWallsTransparent(w, false);

            // Start next round after 5 more seconds
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!running) return;
                startNextRound();
            }, 100L);
        }, 100L);
    }

    // ==================== SIMILARITY SCORING ====================

    private double calculateSimilarity(World world, PlotArea plot) {
        if (currentModel == null) return 0;

        int totalBlocks = 0;
        int matchingBlocks = 0;

        for (int dx = 0; dx < MODEL_SIZE && dx < PLOT_SIZE; dx++) {
            for (int dy = 0; dy < PLOT_HEIGHT; dy++) {
                for (int dz = 0; dz < MODEL_SIZE && dz < PLOT_SIZE; dz++) {
                    Material modelMat = currentModel[dx][dy][dz];
                    if (modelMat == null || modelMat == Material.AIR) continue;

                    totalBlocks++;

                    Block playerBlock = world.getBlockAt(
                            plot.x + dx,
                            plot.y + dy,
                            plot.z + dz);

                    if (playerBlock.getType() == modelMat) {
                        matchingBlocks++;
                    } else if (isSimilarBlock(modelMat, playerBlock.getType())) {
                        matchingBlocks++; // Accept similar blocks (e.g., color variants)
                    }
                }
            }
        }

        // Also penalize extra blocks placed where model has air
        int extraBlocks = 0;
        for (int dx = 0; dx < PLOT_SIZE; dx++) {
            for (int dy = 0; dy < PLOT_HEIGHT; dy++) {
                for (int dz = 0; dz < PLOT_SIZE; dz++) {
                    Material modelMat = (dx < MODEL_SIZE && dz < MODEL_SIZE) ?
                            currentModel[dx][dy][dz] : null;
                    if (modelMat == null || modelMat == Material.AIR) {
                        Block b = world.getBlockAt(plot.x + dx, plot.y + dy, plot.z + dz);
                        if (b.getType() != Material.AIR) {
                            extraBlocks++;
                        }
                    }
                }
            }
        }

        if (totalBlocks == 0) return 0;

        double accuracy = (double) matchingBlocks / totalBlocks;
        double penalty = Math.min(0.3, extraBlocks * 0.01); // Max 30% penalty for extra blocks
        return Math.max(0, accuracy - penalty);
    }

    private boolean isSimilarBlock(Material expected, Material actual) {
        // Accept same color family (e.g., white_wool and white_concrete both count)
        String e = expected.name();
        String a = actual.name();

        // Same color prefix match
        String[] colors = {"WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE", "YELLOW", "LIME",
                "PINK", "GRAY", "LIGHT_GRAY", "CYAN", "PURPLE", "BLUE",
                "BROWN", "GREEN", "RED", "BLACK"};

        for (String color : colors) {
            if (e.startsWith(color) && a.startsWith(color)) return true;
        }

        return false;
    }

    // ==================== MODELS ====================

    private List<ModelDefinition> shuffledModels = null;
    private int shuffledIndex = 0;

    private Material[][][] selectModel(int index) {
        // Shuffle all models once per game, pick sequentially from shuffled list (no repeats)
        if (shuffledModels == null) {
            shuffledModels = getModels();
            java.util.Collections.shuffle(shuffledModels);
            shuffledIndex = 0;
        }
        if (shuffledIndex >= shuffledModels.size()) {
            java.util.Collections.shuffle(shuffledModels);
            shuffledIndex = 0;
        }
        ModelDefinition model = shuffledModels.get(shuffledIndex++);
        currentModelName = model.name;

        // Apply random variation
        if (Math.random() < 0.3) {
            model = applyVariation(model);
        }

        return model.blocks;
    }

    /**
     * Places the current model inside a player's plot area.
     */
    private void placeModelInPlot(World world, PlotArea plot) {
        if (world == null || currentModel == null) return;

        for (int dx = 0; dx < MODEL_SIZE && dx < PLOT_SIZE; dx++) {
            for (int dy = 0; dy < PLOT_HEIGHT; dy++) {
                for (int dz = 0; dz < MODEL_SIZE && dz < PLOT_SIZE; dz++) {
                    Material mat = currentModel[dx][dy][dz];
                    if (mat != null && mat != Material.AIR) {
                        world.getBlockAt(plot.x + dx, plot.y + dy, plot.z + dz).setType(mat);
                    }
                }
            }
        }
    }

    private void clearAllPlots() {
        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (world == null) return;

        for (PlotArea plot : playerPlots.values()) {
            for (int dx = 0; dx < PLOT_SIZE; dx++) {
                for (int dy = 0; dy < PLOT_HEIGHT; dy++) {
                    for (int dz = 0; dz < PLOT_SIZE; dz++) {
                        world.getBlockAt(plot.x + dx, plot.y + dy, plot.z + dz).setType(Material.AIR);
                    }
                }
            }
        }
    }

    // ==================== FIGURE DEFINITIONS ====================

    private List<ModelDefinition> getModels() {
        List<ModelDefinition> models = new ArrayList<>();

        models.add(createHouse());
        models.add(createTree());
        models.add(createHeart());
        models.add(createCreeperFace());
        models.add(createSword());
        models.add(createStar());
        models.add(createMushroom());
        models.add(createTower());
        models.add(createCactus());
        models.add(createSnowman());
        models.add(createDiamond());
        models.add(createCross());
        models.add(createPyramid());
        models.add(createArch());
        models.add(createSkull());
        models.add(createFlower());
        models.add(createLighthouse());
        models.add(createChest());
        models.add(createCake());
        models.add(createCrown());
        models.add(createRocket());
        models.add(createAnchor());
        models.add(createFish());
        models.add(createBell());
        models.add(createTNT());
        models.add(createCandle());
        models.add(createChair());
        models.add(createSpiral());
        models.add(createWell());
        models.add(createPumpkin());

        return models;
    }

    // --- Original 8 models ---

    private ModelDefinition createHouse() {
        Material[][][] b = new Material[7][7][7];
        Material wall = Material.OAK_PLANKS; Material roof = Material.RED_CONCRETE;
        Material floor = Material.STONE; Material window = Material.LIGHT_BLUE_CONCRETE;
        for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) b[x][0][z] = floor;
        for (int y = 1; y <= 3; y++) {
            for (int x = 1; x <= 5; x++) { b[x][y][1] = wall; b[x][y][5] = wall; }
            for (int z = 1; z <= 5; z++) { b[1][y][z] = wall; b[5][y][z] = wall; }
        }
        b[3][2][1] = window; b[3][2][5] = window; b[1][2][3] = window; b[5][2][3] = window;
        b[3][1][1] = Material.AIR;
        for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) b[x][4][z] = roof;
        for (int x = 2; x <= 4; x++) for (int z = 2; z <= 4; z++) b[x][5][z] = roof;
        b[3][6][3] = roof;
        return new ModelDefinition("Casa", b);
    }

    private ModelDefinition createTree() {
        Material[][][] b = new Material[7][7][7];
        Material trunk = Material.OAK_LOG; Material leaves = Material.LIME_CONCRETE;
        for (int y = 0; y <= 3; y++) b[3][y][3] = trunk;
        for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) { b[x][3][z] = leaves; b[x][4][z] = leaves; }
        for (int x = 2; x <= 4; x++) for (int z = 2; z <= 4; z++) b[x][5][z] = leaves;
        b[3][6][3] = leaves;
        b[1][3][1] = null; b[1][3][5] = null; b[5][3][1] = null; b[5][3][5] = null;
        b[1][4][1] = null; b[1][4][5] = null; b[5][4][1] = null; b[5][4][5] = null;
        return new ModelDefinition("Árbol", b);
    }

    private ModelDefinition createHeart() {
        Material[][][] b = new Material[7][7][7];
        Material h = Material.RED_CONCRETE; int z = 3;
        b[1][3][z]=h; b[2][4][z]=h; b[2][3][z]=h; b[0][3][z]=h; b[0][2][z]=h; b[1][4][z]=h;
        b[3][3][z]=h; b[3][2][z]=h; b[3][1][z]=h; b[4][2][z]=h; b[4][3][z]=h; b[5][4][z]=h;
        b[5][3][z]=h; b[6][3][z]=h; b[6][2][z]=h; b[2][2][z]=h; b[4][4][z]=h;
        b[1][2][z]=h; b[5][2][z]=h; b[2][1][z]=h; b[4][1][z]=h; b[3][0][z]=h;
        for (int x = 0; x < 7; x++) for (int y = 0; y < 7; y++)
            if (b[x][y][z] == h) { b[x][y][z-1] = h; b[x][y][z+1] = h; }
        return new ModelDefinition("Corazón", b);
    }

    private ModelDefinition createCreeperFace() {
        Material[][][] b = new Material[7][7][7];
        Material g = Material.LIME_CONCRETE; Material d = Material.GREEN_CONCRETE; int z = 3;
        for (int x = 0; x < 7; x++) for (int y = 0; y < 7; y++) b[x][y][z] = g;
        b[1][5][z]=d; b[2][5][z]=d; b[4][5][z]=d; b[5][5][z]=d;
        b[1][4][z]=d; b[2][4][z]=d; b[4][4][z]=d; b[5][4][z]=d;
        b[3][3][z]=d; b[2][2][z]=d; b[3][2][z]=d; b[4][2][z]=d;
        b[2][1][z]=d; b[4][1][z]=d; b[2][0][z]=d; b[4][0][z]=d; b[3][1][z]=d;
        return new ModelDefinition("Cara de Creeper", b);
    }

    private ModelDefinition createSword() {
        Material[][][] b = new Material[7][7][7];
        Material bl = Material.LIGHT_GRAY_CONCRETE; Material ha = Material.BROWN_CONCRETE;
        Material gu = Material.YELLOW_CONCRETE; int z = 3;
        b[3][6][z]=bl; b[3][5][z]=bl; b[3][4][z]=bl; b[3][3][z]=bl;
        b[2][5][z]=bl; b[4][5][z]=bl; b[2][4][z]=bl; b[4][4][z]=bl; b[2][3][z]=bl; b[4][3][z]=bl;
        for (int x = 1; x <= 5; x++) b[x][2][z] = gu;
        b[3][1][z]=ha; b[3][0][z]=ha;
        return new ModelDefinition("Espada", b);
    }

    private ModelDefinition createStar() {
        Material[][][] b = new Material[7][7][7];
        Material s = Material.YELLOW_CONCRETE; int z = 3;
        b[3][6][z]=s;
        b[2][5][z]=s; b[3][5][z]=s; b[4][5][z]=s;
        for (int x = 0; x < 7; x++) b[x][4][z] = s;
        b[1][3][z]=s; b[2][3][z]=s; b[3][3][z]=s; b[4][3][z]=s; b[5][3][z]=s;
        b[1][2][z]=s; b[2][2][z]=s; b[4][2][z]=s; b[5][2][z]=s;
        b[0][1][z]=s; b[1][1][z]=s; b[5][1][z]=s; b[6][1][z]=s;
        b[0][0][z]=s; b[6][0][z]=s;
        return new ModelDefinition("Estrella", b);
    }

    private ModelDefinition createMushroom() {
        Material[][][] b = new Material[7][7][7];
        Material st = Material.WHITE_CONCRETE; Material cap = Material.RED_CONCRETE;
        for (int y = 0; y <= 2; y++) { b[3][y][3]=st; b[2][y][3]=st; b[4][y][3]=st; b[3][y][2]=st; b[3][y][4]=st; }
        for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) { b[x][3][z] = cap; b[x][4][z] = cap; }
        for (int x = 2; x <= 4; x++) for (int z = 2; z <= 4; z++) b[x][5][z] = cap;
        b[2][4][1]=st; b[4][4][1]=st; b[1][4][3]=st; b[5][4][3]=st; b[2][4][5]=st; b[4][4][5]=st; b[3][5][3]=st;
        return new ModelDefinition("Hongo", b);
    }

    private ModelDefinition createTower() {
        Material[][][] b = new Material[7][7][7];
        Material s = Material.STONE_BRICKS; Material w = Material.LIGHT_BLUE_CONCRETE; Material r = Material.PURPLE_CONCRETE;
        for (int x = 2; x <= 4; x++) for (int z = 2; z <= 4; z++) for (int y = 0; y <= 4; y++) b[x][y][z] = s;
        for (int y = 0; y <= 4; y++) b[3][y][3] = Material.AIR;
        b[3][2][2]=w; b[3][2][4]=w; b[2][2][3]=w; b[4][2][3]=w; b[3][4][2]=w; b[3][4][4]=w;
        b[2][5][2]=s; b[4][5][2]=s; b[2][5][4]=s; b[4][5][4]=s;
        b[3][5][2]=s; b[3][5][4]=s; b[2][5][3]=s; b[4][5][3]=s;
        b[3][6][3] = r;
        return new ModelDefinition("Torre", b);
    }

    // --- 22 New models ---

    private ModelDefinition createCactus() {
        Material[][][] b = new Material[7][7][7];
        Material c = Material.GREEN_CONCRETE; Material s = Material.SAND;
        for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) b[x][0][z] = s;
        for (int y = 1; y <= 5; y++) b[3][y][3] = c;
        b[1][3][3]=c; b[2][3][3]=c; b[1][4][3]=c;
        b[5][4][3]=c; b[4][4][3]=c; b[5][5][3]=c;
        b[3][6][3] = c;
        return new ModelDefinition("Cactus", b);
    }

    private ModelDefinition createSnowman() {
        Material[][][] b = new Material[7][7][7];
        Material w = Material.WHITE_CONCRETE; Material o = Material.ORANGE_CONCRETE;
        Material bl = Material.BLACK_CONCRETE;
        // Bottom ball
        for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) { b[x][0][z] = w; b[x][1][z] = w; }
        for (int x = 2; x <= 4; x++) for (int z = 2; z <= 4; z++) b[x][2][z] = w;
        // Middle ball
        for (int x = 2; x <= 4; x++) for (int z = 2; z <= 4; z++) { b[x][3][z] = w; }
        // Head
        for (int x = 2; x <= 4; x++) for (int z = 2; z <= 4; z++) { b[x][4][z] = w; b[x][5][z] = w; }
        b[3][6][3] = w;
        // Eyes and nose
        b[2][5][2]=bl; b[4][5][2]=bl; b[3][4][2]=o;
        // Buttons
        b[3][3][2]=bl; b[3][1][2]=bl;
        return new ModelDefinition("Muñeco de Nieve", b);
    }

    private ModelDefinition createDiamond() {
        Material[][][] b = new Material[7][7][7];
        Material d = Material.DIAMOND_BLOCK; int z = 3;
        b[3][0][z]=d;
        b[2][1][z]=d; b[3][1][z]=d; b[4][1][z]=d;
        b[1][2][z]=d; b[2][2][z]=d; b[3][2][z]=d; b[4][2][z]=d; b[5][2][z]=d;
        b[0][3][z]=d; b[1][3][z]=d; b[2][3][z]=d; b[3][3][z]=d; b[4][3][z]=d; b[5][3][z]=d; b[6][3][z]=d;
        b[1][4][z]=d; b[2][4][z]=d; b[3][4][z]=d; b[4][4][z]=d; b[5][4][z]=d;
        b[2][5][z]=d; b[3][5][z]=d; b[4][5][z]=d;
        b[3][6][z]=d;
        for (int x = 0; x < 7; x++) for (int y = 0; y < 7; y++)
            if (b[x][y][z] == d) { b[x][y][z-1] = d; b[x][y][z+1] = d; }
        return new ModelDefinition("Diamante", b);
    }

    private ModelDefinition createCross() {
        Material[][][] b = new Material[7][7][7];
        Material w = Material.OAK_PLANKS; int z = 3;
        for (int y = 0; y <= 6; y++) b[3][y][z] = w;
        for (int x = 1; x <= 5; x++) b[x][4][z] = w;
        // Depth
        for (int x = 0; x < 7; x++) for (int y = 0; y < 7; y++)
            if (b[x][y][z] == w) b[x][y][z-1] = w;
        return new ModelDefinition("Cruz", b);
    }

    private ModelDefinition createPyramid() {
        Material[][][] b = new Material[7][7][7];
        Material s = Material.SANDSTONE; Material g = Material.GOLD_BLOCK;
        for (int x = 0; x < 7; x++) for (int z = 0; z < 7; z++) b[x][0][z] = s;
        for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) b[x][1][z] = s;
        for (int x = 2; x <= 4; x++) for (int z = 2; z <= 4; z++) b[x][2][z] = s;
        b[3][3][3] = g;
        return new ModelDefinition("Pirámide", b);
    }

    private ModelDefinition createArch() {
        Material[][][] b = new Material[7][7][7];
        Material s = Material.STONE_BRICKS;
        for (int y = 0; y <= 5; y++) { b[1][y][3] = s; b[5][y][3] = s; }
        b[2][5][3]=s; b[3][6][3]=s; b[4][5][3]=s;
        b[2][6][3]=s; b[4][6][3]=s;
        // Depth
        for (int x = 0; x < 7; x++) for (int y = 0; y < 7; y++)
            if (b[x][y][3] == s) { b[x][y][2] = s; b[x][y][4] = s; }
        return new ModelDefinition("Arco", b);
    }

    private ModelDefinition createSkull() {
        Material[][][] b = new Material[7][7][7];
        Material w = Material.WHITE_CONCRETE; Material bl = Material.BLACK_CONCRETE; int z = 3;
        for (int x = 1; x <= 5; x++) for (int y = 2; y <= 5; y++) b[x][y][z] = w;
        b[0][3][z]=w; b[0][4][z]=w; b[6][3][z]=w; b[6][4][z]=w;
        b[1][6][z]=w; b[2][6][z]=w; b[3][6][z]=w; b[4][6][z]=w; b[5][6][z]=w;
        // Eyes
        b[2][4][z]=bl; b[4][4][z]=bl;
        // Nose
        b[3][3][z]=bl;
        // Teeth
        b[2][2][z]=bl; b[4][2][z]=bl;
        b[2][1][z]=w; b[3][1][z]=w; b[4][1][z]=w;
        return new ModelDefinition("Calavera", b);
    }

    private ModelDefinition createFlower() {
        Material[][][] b = new Material[7][7][7];
        Material stem = Material.GREEN_CONCRETE; Material pet = Material.PINK_CONCRETE;
        Material cen = Material.YELLOW_CONCRETE;
        for (int y = 0; y <= 3; y++) b[3][y][3] = stem;
        b[2][1][3]=stem; b[4][1][3]=stem; // leaves
        // Petals
        b[3][5][3]=pet; b[2][4][3]=pet; b[4][4][3]=pet; b[3][4][2]=pet; b[3][4][4]=pet;
        b[1][4][3]=pet; b[5][4][3]=pet; b[3][4][1]=pet; b[3][4][5]=pet;
        b[3][6][3]=pet; b[2][5][3]=pet; b[4][5][3]=pet; b[3][5][2]=pet; b[3][5][4]=pet;
        // Center
        b[3][4][3]=cen; b[3][5][3]=cen;
        return new ModelDefinition("Flor", b);
    }

    private ModelDefinition createLighthouse() {
        Material[][][] b = new Material[7][7][7];
        Material w = Material.WHITE_CONCRETE; Material r = Material.RED_CONCRETE;
        Material y = Material.YELLOW_CONCRETE; Material g = Material.GRAY_CONCRETE;
        // Base
        for (int x = 2; x <= 4; x++) for (int z = 2; z <= 4; z++) b[x][0][z] = g;
        // Tower alternating colors
        for (int ly = 1; ly <= 4; ly++) {
            Material m = (ly % 2 == 1) ? w : r;
            for (int x = 2; x <= 4; x++) for (int z = 2; z <= 4; z++) b[x][ly][z] = m;
        }
        // Light
        b[3][5][3]=y; b[2][5][3]=y; b[4][5][3]=y; b[3][5][2]=y; b[3][5][4]=y;
        b[3][6][3]=g;
        return new ModelDefinition("Faro", b);
    }

    private ModelDefinition createChest() {
        Material[][][] b = new Material[7][7][7];
        Material w = Material.OAK_PLANKS; Material d = Material.DARK_OAK_PLANKS;
        Material ir = Material.IRON_BLOCK;
        for (int x = 1; x <= 5; x++) for (int z = 2; z <= 4; z++) { b[x][0][z]=w; b[x][1][z]=w; b[x][2][z]=w; }
        for (int x = 1; x <= 5; x++) for (int z = 2; z <= 4; z++) b[x][3][z] = d;
        // Lock
        b[3][2][2] = ir; b[3][3][2] = ir;
        // Lid border
        for (int x = 1; x <= 5; x++) { b[x][3][2]=d; b[x][3][4]=d; }
        return new ModelDefinition("Cofre", b);
    }

    private ModelDefinition createCake() {
        Material[][][] b = new Material[7][7][7];
        Material cake = Material.WHITE_CONCRETE; Material top = Material.RED_CONCRETE;
        Material choc = Material.BROWN_CONCRETE;
        // Bottom layer
        for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) { b[x][0][z]=choc; b[x][1][z]=cake; }
        // Top layer (smaller)
        for (int x = 2; x <= 4; x++) for (int z = 2; z <= 4; z++) { b[x][2][z]=choc; b[x][3][z]=cake; }
        // Cherry on top
        b[3][4][3] = top;
        return new ModelDefinition("Pastel", b);
    }

    private ModelDefinition createCrown() {
        Material[][][] b = new Material[7][7][7];
        Material g = Material.GOLD_BLOCK; Material gem = Material.DIAMOND_BLOCK;
        Material r = Material.RED_CONCRETE;
        // Base ring
        for (int x = 1; x <= 5; x++) { b[x][0][2]=g; b[x][0][4]=g; b[x][1][2]=g; b[x][1][4]=g; }
        for (int z = 2; z <= 4; z++) { b[1][0][z]=g; b[5][0][z]=g; b[1][1][z]=g; b[5][1][z]=g; }
        // Points
        b[1][2][3]=g; b[1][3][3]=g;
        b[3][2][2]=g; b[3][3][2]=g; b[3][4][2]=g;
        b[5][2][3]=g; b[5][3][3]=g;
        b[3][2][4]=g; b[3][3][4]=g; b[3][4][4]=g;
        // Gems
        b[1][2][3]=gem; b[5][2][3]=gem;
        b[3][3][2]=r; b[3][3][4]=r;
        return new ModelDefinition("Corona", b);
    }

    private ModelDefinition createRocket() {
        Material[][][] b = new Material[7][7][7];
        Material body = Material.WHITE_CONCRETE; Material nose = Material.RED_CONCRETE;
        Material fin = Material.GRAY_CONCRETE; Material fire = Material.ORANGE_CONCRETE;
        // Body
        for (int y = 2; y <= 5; y++) { b[3][y][3]=body; b[2][y][3]=body; b[4][y][3]=body; b[3][y][2]=body; b[3][y][4]=body; }
        // Nose cone
        b[3][6][3]=nose; b[3][5][3]=nose;
        // Fins
        b[1][2][3]=fin; b[5][2][3]=fin; b[3][2][1]=fin; b[3][2][5]=fin;
        b[1][1][3]=fin; b[5][1][3]=fin; b[3][1][1]=fin; b[3][1][5]=fin;
        // Fire
        b[3][0][3]=fire; b[2][0][3]=fire; b[4][0][3]=fire; b[3][0][2]=fire; b[3][0][4]=fire;
        return new ModelDefinition("Cohete", b);
    }

    private ModelDefinition createAnchor() {
        Material[][][] b = new Material[7][7][7];
        Material ir = Material.IRON_BLOCK; int z = 3;
        // Vertical shaft
        for (int y = 1; y <= 5; y++) b[3][y][z] = ir;
        // Top ring
        b[2][6][z]=ir; b[3][6][z]=ir; b[4][6][z]=ir;
        b[2][5][z]=ir; b[4][5][z]=ir;
        // Horizontal bar
        for (int x = 0; x <= 6; x++) b[x][2][z] = ir;
        // Curved bottom
        b[0][2][z]=ir; b[0][1][z]=ir; b[1][0][z]=ir; b[2][0][z]=ir;
        b[6][2][z]=ir; b[6][1][z]=ir; b[5][0][z]=ir; b[4][0][z]=ir;
        return new ModelDefinition("Ancla", b);
    }

    private ModelDefinition createFish() {
        Material[][][] b = new Material[7][7][7];
        Material f = Material.ORANGE_CONCRETE; Material e = Material.BLACK_CONCRETE;
        Material t = Material.YELLOW_CONCRETE; int z = 3;
        // Body
        b[2][3][z]=f; b[3][3][z]=f; b[4][3][z]=f;
        b[1][2][z]=f; b[2][2][z]=f; b[3][2][z]=f; b[4][2][z]=f; b[5][2][z]=f;
        b[2][1][z]=f; b[3][1][z]=f; b[4][1][z]=f;
        b[3][4][z]=f;
        // Eye
        b[4][3][z]=e;
        // Tail
        b[0][3][z]=t; b[0][1][z]=t; b[0][2][z]=t;
        b[1][3][z]=f;
        // Fin
        b[3][4][z]=f; b[2][4][z]=f;
        // Mouth
        b[5][2][z]=f; b[6][2][z]=f;
        return new ModelDefinition("Pez", b);
    }

    private ModelDefinition createBell() {
        Material[][][] b = new Material[7][7][7];
        Material g = Material.GOLD_BLOCK; Material d = Material.DARK_OAK_PLANKS;
        // Top bar
        for (int x = 2; x <= 4; x++) b[x][6][3] = d;
        // Bell body
        b[3][5][3]=g;
        for (int x = 2; x <= 4; x++) for (int z = 2; z <= 4; z++) b[x][4][z] = g;
        for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) b[x][3][z] = g;
        for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) b[x][2][z] = g;
        for (int x = 0; x <= 6; x++) for (int z = 1; z <= 5; z++) b[x][1][z] = g;
        for (int x = 1; x <= 5; x++) { b[x][1][0]=g; b[x][1][6]=g; }
        // Clapper
        b[3][0][3] = Material.IRON_BLOCK;
        return new ModelDefinition("Campana", b);
    }

    private ModelDefinition createTNT() {
        Material[][][] b = new Material[7][7][7];
        Material r = Material.RED_CONCRETE; Material w = Material.WHITE_CONCRETE;
        Material d = Material.GRAY_CONCRETE;
        // TNT block (3D)
        for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) {
            b[x][0][z] = r; b[x][4][z] = r;
            b[x][1][z] = r; b[x][3][z] = r;
            b[x][2][z] = w; // White band
        }
        // Top fuse
        b[3][5][3] = d; b[3][6][3] = Material.BLACK_CONCRETE;
        return new ModelDefinition("TNT", b);
    }

    private ModelDefinition createCandle() {
        Material[][][] b = new Material[7][7][7];
        Material wax = Material.WHITE_CONCRETE; Material wick = Material.BLACK_CONCRETE;
        Material flame = Material.ORANGE_CONCRETE; Material base = Material.GRAY_CONCRETE;
        // Base plate
        for (int x = 2; x <= 4; x++) for (int z = 2; z <= 4; z++) b[x][0][z] = base;
        // Candle body
        for (int y = 1; y <= 4; y++) { b[3][y][3]=wax; b[2][y][3]=wax; b[4][y][3]=wax; b[3][y][2]=wax; b[3][y][4]=wax; }
        // Wick + flame
        b[3][5][3]=wick; b[3][6][3]=flame;
        // Wax drips
        b[2][3][2]=wax; b[4][2][4]=wax;
        return new ModelDefinition("Vela", b);
    }

    private ModelDefinition createChair() {
        Material[][][] b = new Material[7][7][7];
        Material w = Material.OAK_PLANKS; Material c = Material.RED_CONCRETE;
        // Legs
        b[1][0][2]=w; b[5][0][2]=w; b[1][0][4]=w; b[5][0][4]=w;
        // Seat
        for (int x = 1; x <= 5; x++) for (int z = 2; z <= 4; z++) b[x][1][z] = c;
        // Backrest
        for (int x = 1; x <= 5; x++) for (int y = 2; y <= 4; y++) b[x][y][4] = w;
        // Armrests
        b[1][2][2]=w; b[1][2][3]=w; b[5][2][2]=w; b[5][2][3]=w;
        return new ModelDefinition("Silla", b);
    }

    private ModelDefinition createSpiral() {
        Material[][][] b = new Material[7][7][7];
        Material s = Material.PURPLE_CONCRETE; Material g = Material.MAGENTA_CONCRETE;
        // Spiral staircase going up
        b[3][0][1]=s; b[4][0][1]=s;
        b[5][1][3]=s; b[5][1][2]=s;
        b[3][2][5]=s; b[4][2][5]=s;
        b[1][3][3]=s; b[1][3][4]=s;
        b[3][4][1]=s; b[2][4][1]=s;
        b[5][5][3]=s; b[5][5][4]=s;
        b[3][6][5]=g; b[4][6][5]=g;
        // Central pillar
        for (int y = 0; y <= 6; y++) b[3][y][3] = g;
        return new ModelDefinition("Espiral", b);
    }

    private ModelDefinition createWell() {
        Material[][][] b = new Material[7][7][7];
        Material s = Material.STONE_BRICKS; Material w = Material.BLUE_CONCRETE;
        Material wo = Material.OAK_PLANKS;
        // Base ring
        for (int x = 1; x <= 5; x++) { b[x][0][1]=s; b[x][0][5]=s; }
        for (int z = 1; z <= 5; z++) { b[1][0][z]=s; b[5][0][z]=s; }
        for (int x = 1; x <= 5; x++) { b[x][1][1]=s; b[x][1][5]=s; }
        for (int z = 1; z <= 5; z++) { b[1][1][z]=s; b[5][1][z]=s; }
        // Water
        for (int x = 2; x <= 4; x++) for (int z = 2; z <= 4; z++) b[x][0][z] = w;
        // Pillars
        b[1][2][1]=wo; b[1][3][1]=wo; b[5][2][5]=wo; b[5][3][5]=wo;
        b[5][2][1]=wo; b[5][3][1]=wo; b[1][2][5]=wo; b[1][3][5]=wo;
        // Roof
        for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) b[x][4][z] = wo;
        b[3][5][3]=wo;
        return new ModelDefinition("Pozo", b);
    }

    private ModelDefinition createPumpkin() {
        Material[][][] b = new Material[7][7][7];
        Material o = Material.ORANGE_CONCRETE; Material bl = Material.BLACK_CONCRETE;
        Material g = Material.GREEN_CONCRETE;
        // Pumpkin body
        for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) for (int y = 0; y <= 3; y++) b[x][y][z] = o;
        // Remove corners for round shape
        for (int y = 0; y <= 3; y++) { b[1][y][1]=null; b[5][y][1]=null; b[1][y][5]=null; b[5][y][5]=null; }
        // Face (front)
        b[2][2][1]=bl; b[4][2][1]=bl; // Eyes
        b[2][1][1]=bl; b[3][1][1]=bl; b[4][1][1]=bl; // Mouth
        b[3][2][1]=bl; // Nose
        // Stem
        b[3][4][3]=g; b[3][5][3]=g;
        return new ModelDefinition("Calabaza", b);
    }

    private ModelDefinition applyVariation(ModelDefinition model) {
        // Random color swap variation
        Material[][] swaps = {
                {Material.RED_CONCRETE, Material.ORANGE_CONCRETE},
                {Material.LIME_CONCRETE, Material.GREEN_CONCRETE},
                {Material.OAK_PLANKS, Material.BIRCH_PLANKS},
                {Material.YELLOW_CONCRETE, Material.GOLD_BLOCK},
                {Material.LIGHT_BLUE_CONCRETE, Material.CYAN_CONCRETE},
                {Material.PURPLE_CONCRETE, Material.MAGENTA_CONCRETE},
                {Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS},
                {Material.WHITE_CONCRETE, Material.LIGHT_GRAY_CONCRETE},
        };

        Material[] swap = swaps[(int) (Math.random() * swaps.length)];
        Material from = swap[0];
        Material to = swap[1];

        Material[][][] newBlocks = new Material[7][7][7];
        for (int x = 0; x < 7; x++)
            for (int y = 0; y < 7; y++)
                for (int z = 0; z < 7; z++) {
                    Material m = model.blocks[x][y][z];
                    newBlocks[x][y][z] = (m == from) ? to : m;
                }

        return new ModelDefinition(model.name + " §7(variante)", newBlocks);
    }

    // ==================== CLEANUP ====================

    @Override
    public void onCleanup() {
        roundActive = false;
        memorizePhase = false;
        buildingAllowed = false;
        tournamentPlayers.clear();
        playerPlots.clear();
    }

    @Override
    public void checkWinCondition() {
        // Override: don't end game on player elimination, tournament handles it
        if (tournamentPlayers.size() <= 1 && running && !roundActive) {
            if (!tournamentPlayers.isEmpty()) {
                Player winner = Bukkit.getPlayer(tournamentPlayers.get(0));
                endGame(winner);
            } else {
                endGame(null);
            }
        }
    }

    // ==================== INNER CLASSES ====================

    private static class PlotArea {
        final int x, y, z;
        final int width, height;

        PlotArea(int x, int y, int z, int width, int height) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.width = width;
            this.height = height;
        }
    }

    private static class ModelDefinition {
        final String name;
        final Material[][][] blocks;

        ModelDefinition(String name, Material[][][] blocks) {
            this.name = name;
            this.blocks = blocks;
        }
    }
}
