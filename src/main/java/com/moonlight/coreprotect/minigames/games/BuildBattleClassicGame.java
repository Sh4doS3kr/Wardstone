package com.moonlight.coreprotect.minigames.games;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.minigames.MiniGame;
import com.moonlight.coreprotect.minigames.MiniGameManager;
import com.moonlight.coreprotect.minigames.MiniGameType;
import com.moonlight.coreprotect.minigames.MiniGameWorld;
import com.moonlight.coreprotect.util.ActionBarUtil;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Build Battle Clásico — estilo Hypixel.
 *
 * Fases:
 *  1. THEME_VOTE   — Panel con 4 temáticas aleatorias; jugadores votan clickando un papel.
 *  2. BUILD        — 450 s en creativo para construir en su parcela gigante.
 *  3. VOTING       — Todos visitan cada build; votan con items de inventario.
 *  4. RESULTS      — Se muestran puntuaciones y el ganador.
 *
 * Puntos por voto:
 *   SUPER CACA = 1 | CACA = 2 | OK = 4 | BIEN = 8 | EXCELENTE = 16 | GENIAL = 18
 */
public class BuildBattleClassicGame extends MiniGame {

    // ==================== CONSTANTS ====================

    private static final int PLOT_SIZE = 51;
    private static final int PLOT_HEIGHT = 30;
    private static final int ARENA_Y = 65;
    private static final int PLOT_SPACING = 80;
    private static final int WALL_THICKNESS = 3;
    private static final String VOTE_GUI_TITLE = "§b§l⭐ Vota la Construcción ⭐";

    private static final int THEME_VOTE_TIME = 15;   // seconds
    private static final int BUILD_TIME = 450;         // seconds
    private static final int VOTE_TIME_PER_BUILD = 20; // seconds per build

    // Vote item slots (hotbar 0-5)
    private static final int VOTE_SUPER_CACA = 0;
    private static final int VOTE_CACA = 1;
    private static final int VOTE_OK = 2;
    private static final int VOTE_BIEN = 3;
    private static final int VOTE_EXCELENTE = 4;
    private static final int VOTE_GENIAL = 5;

    // Point values matching Hypixel
    private static final int[] VOTE_POINTS = {1, 2, 4, 8, 16, 18};
    private static final String[] VOTE_NAMES = {
            "§4§lSUPER CACA", "§c§lCACA", "§e§lOK", "§a§lBIEN", "§b§lEXCELENTE", "§d§lGENIAL"
    };
    private static final Material[] VOTE_MATERIALS = {
            Material.BROWN_DYE, Material.RED_DYE, Material.YELLOW_DYE,
            Material.LIME_DYE, Material.LIGHT_BLUE_DYE, Material.MAGENTA_DYE
    };

    // ==================== THEMES ====================

    private static final String[][] THEME_POOL = {
            {"Castillo", "Nave Espacial", "Dragón", "Jardín Encantado"},
            {"Volcán", "Ciudad Futurista", "Barco Pirata", "Montaña Rusa"},
            {"Casa del Árbol", "Robot Gigante", "Acuario", "Estadio"},
            {"Templo Azteca", "Tren", "Faro en Isla", "Granja"},
            {"Iglesia", "Submarino", "Torre Medieval", "Parque de Diversiones"},
            {"Puente Colgante", "Cascada", "Laboratorio", "Biblioteca"},
            {"Pizza", "Sushi", "Helado Gigante", "Hamburguesa"},
            {"Astronauta", "Dinosaurio", "Unicornio", "Phoenix"},
            {"Arena Gladiador", "Bosque Mágico", "Cueva de Cristales", "Oasis"},
            {"Cine", "Supermercado", "Hospital", "Aeropuerto"},
            {"Portal Dimensional", "Reloj de Arena", "Cofre del Tesoro", "Globo Aerostático"},
            {"Iglú", "Pirámide", "Coliseo Romano", "Pagoda Japonesa"},
            {"Pastel de Cumpleaños", "Navidad", "Halloween", "San Valentín"},
            {"Creeper Gigante", "Enderman", "Wither", "Ender Dragon"},
            {"Guitarra", "Piano", "Batería", "Violín"},
    };

    // ==================== STATE ====================

    private enum Phase { THEME_VOTE, BUILD, VOTING, RESULTS }

    private Phase currentPhase = Phase.THEME_VOTE;
    private int phaseTimer = 0;

    // Theme voting
    private String[] currentThemeOptions = new String[4];
    private final Map<UUID, Integer> themeVotes = new HashMap<>(); // player -> option index (0-3)
    private String selectedTheme = "";

    // Plots
    private final Map<UUID, PlotArea> playerPlots = new LinkedHashMap<>();
    private final List<UUID> plotOrder = new ArrayList<>(); // iteration order for voting

    // Voting
    private int currentVotingIndex = -1; // which build we're visiting
    private UUID currentVotingTarget = null;
    private final Map<UUID, Map<UUID, Integer>> allVotes = new HashMap<>(); // builder -> (voter -> points)
    private final Map<UUID, Integer> totalScores = new LinkedHashMap<>();

    // Track if panel has been opened
    private boolean themeInventoryGiven = false;

    // ==================== CONSTRUCTOR ====================

    public BuildBattleClassicGame(CoreProtectPlugin plugin, MiniGameManager manager) {
        super(plugin, manager, MiniGameType.BUILD_BATTLE_CLASSIC);
    }

    // ==================== PUBLIC API ====================

    public boolean isInsidePlayerPlot(UUID uuid, Location loc) {
        PlotArea plot = playerPlots.get(uuid);
        if (plot == null) return false;
        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();
        return bx >= plot.x && bx < plot.x + plot.size
                && by >= plot.y && by < plot.y + plot.height
                && bz >= plot.z && bz < plot.z + plot.size;
    }

    public boolean isBuildingAllowed() {
        return currentPhase == Phase.BUILD;
    }

    public boolean isVotingPhase() {
        return currentPhase == Phase.VOTING;
    }

    public Phase getCurrentPhase() {
        return currentPhase;
    }

    /**
     * Called from MiniGameListener when a player clicks a vote item in hotbar.
     */
    public void handleVoteClick(Player player, int slot) {
        if (currentPhase == Phase.THEME_VOTE) {
            handleThemeVoteClick(player, slot);
            return;
        }
    }

    /**
     * Called from MiniGameListener when a player clicks inside the vote GUI.
     */
    public void handleVoteGUIClick(Player player, int rawSlot) {
        if (currentPhase != Phase.VOTING || currentVotingTarget == null) return;
        if (player.getUniqueId().equals(currentVotingTarget)) return;

        // Map GUI slots to vote indices
        int voteIndex = guiSlotToVoteIndex(rawSlot);
        if (voteIndex < 0 || voteIndex >= VOTE_POINTS.length) return;

        UUID voterId = player.getUniqueId();
        Map<UUID, Integer> votes = allVotes.get(currentVotingTarget);
        if (votes == null) return;

        votes.put(voterId, VOTE_POINTS[voteIndex]);
        player.sendMessage(SmallCaps.convert("§a§l✔ §7Votaste: " + VOTE_NAMES[voteIndex] + " §7(" + VOTE_POINTS[voteIndex] + " pts)"));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
        player.closeInventory();
        player.sendTitle(VOTE_NAMES[voteIndex], "§7+" + VOTE_POINTS[voteIndex] + " puntos", 5, 20, 5);
    }

    /**
     * Returns true if the given inventory title matches our vote GUI.
     */
    public boolean isVoteGUI(String title) {
        return VOTE_GUI_TITLE.equals(title);
    }

    private int guiSlotToVoteIndex(int rawSlot) {
        // Row layout in a 27-slot chest (3 rows of 9):
        //   Row 1 (slots 0-8):  decorative
        //   Row 2 (slots 9-17): vote items at slots 10,11,12,13,14,15
        //   Row 3 (slots 18-26): decorative
        switch (rawSlot) {
            case 10: return 0; // SUPER CACA
            case 11: return 1; // CACA
            case 12: return 2; // OK
            case 13: return 3; // BIEN
            case 14: return 4; // EXCELENTE
            case 15: return 5; // GENIAL
            default: return -1;
        }
    }

    // ==================== ARENA ====================

    @Override
    public void buildArena(World world) {
        int totalPlots = 12;
        int plotsPerRow = 4;

        int totalWidth = plotsPerRow * (PLOT_SIZE + PLOT_SPACING);
        int rows = (totalPlots + plotsPerRow - 1) / plotsPerRow;
        int totalDepth = rows * (PLOT_SIZE + PLOT_SPACING);
        int startX = -totalWidth / 2;
        int startZ = -totalDepth / 2;

        // Build each plot (clear only per-plot area to avoid massive lag)
        for (int i = 0; i < totalPlots; i++) {
            int row = i / plotsPerRow;
            int col = i % plotsPerRow;
            int px = startX + col * (PLOT_SIZE + PLOT_SPACING);
            int pz = startZ + row * (PLOT_SIZE + PLOT_SPACING);

            // Clear plot area + walls
            int margin = WALL_THICKNESS + 2;
            for (int x = px - margin; x <= px + PLOT_SIZE + margin; x++) {
                for (int z = pz - margin; z <= pz + PLOT_SIZE + margin; z++) {
                    for (int y = ARENA_Y - 2; y <= ARENA_Y + PLOT_HEIGHT + 10; y++) {
                        world.getBlockAt(x, y, z).setType(Material.AIR);
                    }
                }
            }

            buildPlot(world, px, pz);
        }
    }

    private void buildPlot(World world, int ox, int oz) {
        int wallH = PLOT_HEIGHT + 5;
        int outerEnd = PLOT_SIZE + WALL_THICKNESS; // outer edge offset

        // ---- Floor ----
        // Inner floor — smooth quartz
        for (int x = ox; x < ox + PLOT_SIZE; x++) {
            for (int z = oz; z < oz + PLOT_SIZE; z++) {
                world.getBlockAt(x, ARENA_Y - 1, z).setType(Material.SMOOTH_QUARTZ);
            }
        }
        // Floor border ring (1-block accent)
        for (int x = ox - 1; x <= ox + PLOT_SIZE; x++) {
            world.getBlockAt(x, ARENA_Y - 1, oz - 1).setType(Material.POLISHED_DEEPSLATE);
            world.getBlockAt(x, ARENA_Y - 1, oz + PLOT_SIZE).setType(Material.POLISHED_DEEPSLATE);
        }
        for (int z = oz; z < oz + PLOT_SIZE; z++) {
            world.getBlockAt(ox - 1, ARENA_Y - 1, z).setType(Material.POLISHED_DEEPSLATE);
            world.getBlockAt(ox + PLOT_SIZE, ARENA_Y - 1, z).setType(Material.POLISHED_DEEPSLATE);
        }
        // Floor under walls
        for (int x = ox - WALL_THICKNESS; x <= ox + PLOT_SIZE + WALL_THICKNESS - 1; x++) {
            for (int z = oz - WALL_THICKNESS; z <= oz + PLOT_SIZE + WALL_THICKNESS - 1; z++) {
                boolean insidePlot = x >= ox && x < ox + PLOT_SIZE && z >= oz && z < oz + PLOT_SIZE;
                boolean insideBorder = x >= ox - 1 && x <= ox + PLOT_SIZE && z >= oz - 1 && z <= oz + PLOT_SIZE;
                if (!insidePlot && !insideBorder) {
                    world.getBlockAt(x, ARENA_Y - 1, z).setType(Material.POLISHED_BLACKSTONE);
                }
            }
        }

        // ---- Corner lights (sea lantern pillars) ----
        int[][] corners = {
                {ox - 1, oz - 1}, {ox + PLOT_SIZE, oz - 1},
                {ox - 1, oz + PLOT_SIZE}, {ox + PLOT_SIZE, oz + PLOT_SIZE}
        };
        for (int[] c : corners) {
            world.getBlockAt(c[0], ARENA_Y - 1, c[1]).setType(Material.SEA_LANTERN);
            world.getBlockAt(c[0], ARENA_Y, c[1]).setType(Material.SEA_LANTERN);
        }

        // ---- Decorated thick walls ----
        for (int y = ARENA_Y; y <= ARENA_Y + wallH; y++) {
            Material wallMat = getWallMaterial(y - ARENA_Y, wallH);
            for (int t = 0; t < WALL_THICKNESS; t++) {
                // North wall (z = oz - 1 - t)
                for (int x = ox - WALL_THICKNESS; x <= ox + PLOT_SIZE + WALL_THICKNESS - 1; x++) {
                    world.getBlockAt(x, y, oz - 1 - t).setType(wallMat);
                }
                // South wall (z = oz + PLOT_SIZE + t)
                for (int x = ox - WALL_THICKNESS; x <= ox + PLOT_SIZE + WALL_THICKNESS - 1; x++) {
                    world.getBlockAt(x, y, oz + PLOT_SIZE + t).setType(wallMat);
                }
                // West wall (x = ox - 1 - t)
                for (int z = oz - 1; z <= oz + PLOT_SIZE; z++) {
                    world.getBlockAt(ox - 1 - t, y, z).setType(wallMat);
                }
                // East wall (x = ox + PLOT_SIZE + t)
                for (int z = oz - 1; z <= oz + PLOT_SIZE; z++) {
                    world.getBlockAt(ox + PLOT_SIZE + t, y, z).setType(wallMat);
                }
            }
        }

        // ---- Wall decoration: vertical pillar accents every 10 blocks ----
        for (int side = 0; side < 4; side++) {
            for (int i = 0; i < PLOT_SIZE; i += 10) {
                for (int y = ARENA_Y; y <= ARENA_Y + wallH; y++) {
                    Material accent = (y - ARENA_Y) % 4 == 0 ? Material.POLISHED_BLACKSTONE : Material.DEEPSLATE_BRICKS;
                    switch (side) {
                        case 0: world.getBlockAt(ox + i, y, oz - 1).setType(accent); break;
                        case 1: world.getBlockAt(ox + i, y, oz + PLOT_SIZE).setType(accent); break;
                        case 2: world.getBlockAt(ox - 1, y, oz + i).setType(accent); break;
                        case 3: world.getBlockAt(ox + PLOT_SIZE, y, oz + i).setType(accent); break;
                    }
                }
            }
        }

        // ---- Wall top trim (crown) ----
        for (int x = ox - WALL_THICKNESS; x <= ox + PLOT_SIZE + WALL_THICKNESS - 1; x++) {
            for (int z = oz - WALL_THICKNESS; z <= oz + PLOT_SIZE + WALL_THICKNESS - 1; z++) {
                boolean isWall = x < ox || x >= ox + PLOT_SIZE || z < oz || z >= oz + PLOT_SIZE;
                if (isWall) {
                    world.getBlockAt(x, ARENA_Y + wallH + 1, z).setType(Material.POLISHED_BLACKSTONE_SLAB);
                }
            }
        }

        // ---- Ceiling barrier (invisible, above open sky area) ----
        for (int x = ox; x < ox + PLOT_SIZE; x++) {
            for (int z = oz; z < oz + PLOT_SIZE; z++) {
                world.getBlockAt(x, ARENA_Y + wallH + 2, z).setType(Material.BARRIER);
            }
        }

        // ---- Sea lantern lights along top of inner walls (every 5 blocks) ----
        for (int i = 0; i < PLOT_SIZE; i += 5) {
            world.getBlockAt(ox + i, ARENA_Y + wallH, oz - 1).setType(Material.SEA_LANTERN);
            world.getBlockAt(ox + i, ARENA_Y + wallH, oz + PLOT_SIZE).setType(Material.SEA_LANTERN);
            world.getBlockAt(ox - 1, ARENA_Y + wallH, oz + i).setType(Material.SEA_LANTERN);
            world.getBlockAt(ox + PLOT_SIZE, ARENA_Y + wallH, oz + i).setType(Material.SEA_LANTERN);
        }
    }

    /**
     * Returns the wall material for a given height layer, creating a gradient effect.
     */
    private Material getWallMaterial(int layer, int maxH) {
        double ratio = (double) layer / maxH;
        if (ratio < 0.1) return Material.POLISHED_BLACKSTONE;
        if (ratio < 0.3) return Material.DEEPSLATE_BRICKS;
        if (ratio < 0.5) return Material.POLISHED_DEEPSLATE;
        if (ratio < 0.7) return Material.DEEPSLATE_TILES;
        if (ratio < 0.85) return Material.SMOOTH_STONE;
        return Material.QUARTZ_BRICKS;
    }

    // ==================== SPAWNS ====================

    @Override
    public List<Location> getSpawnLocations(World world) {
        List<Location> spawns = new ArrayList<>();
        int totalPlots = 12;
        int plotsPerRow = 4;
        int totalWidth = plotsPerRow * (PLOT_SIZE + PLOT_SPACING);
        int rows = (totalPlots + plotsPerRow - 1) / plotsPerRow;
        int totalDepth = rows * (PLOT_SIZE + PLOT_SPACING);
        int startX = -totalWidth / 2;
        int startZ = -totalDepth / 2;

        for (int i = 0; i < totalPlots; i++) {
            int row = i / plotsPerRow;
            int col = i % plotsPerRow;
            int px = startX + col * (PLOT_SIZE + PLOT_SPACING);
            int pz = startZ + row * (PLOT_SIZE + PLOT_SPACING);
            spawns.add(new Location(world, px + PLOT_SIZE / 2.0 + 0.5, ARENA_Y, pz + PLOT_SIZE / 2.0 + 0.5));
        }
        return spawns;
    }

    // ==================== GAME START ====================

    @Override
    public void startGameLogic() {
        // Assign plots to players
        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        List<Location> spawns = getSpawnLocations(world);
        int idx = 0;
        plotOrder.clear();
        for (UUID uuid : alivePlayers) {
            if (idx >= spawns.size()) break;
            Location spawn = spawns.get(idx);
            int plotsPerRow = 4;
            int totalWidth = plotsPerRow * (PLOT_SIZE + PLOT_SPACING);
            int rows = 3;
            int totalDepth = rows * (PLOT_SIZE + PLOT_SPACING);
            int startX = -totalWidth / 2;
            int startZ = -totalDepth / 2;
            int row = idx / plotsPerRow;
            int col = idx % plotsPerRow;
            int px = startX + col * (PLOT_SIZE + PLOT_SPACING);
            int pz = startZ + row * (PLOT_SIZE + PLOT_SPACING);

            playerPlots.put(uuid, new PlotArea(px, ARENA_Y, pz, PLOT_SIZE, PLOT_HEIGHT));
            plotOrder.add(uuid);
            idx++;
        }

        // Start theme voting phase
        startThemeVotePhase();
    }

    // ==================== PHASE 1: THEME VOTING ====================

    private void startThemeVotePhase() {
        currentPhase = Phase.THEME_VOTE;
        phaseTimer = THEME_VOTE_TIME;
        themeVotes.clear();
        themeInventoryGiven = false;

        // Pick 4 random themes
        String[][] pool = THEME_POOL;
        String[] chosen = pool[new Random().nextInt(pool.length)];
        System.arraycopy(chosen, 0, currentThemeOptions, 0, 4);

        broadcastGame("");
        broadcastGame(SmallCaps.convert("§b§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        broadcastGame(SmallCaps.convert("§b§l  🎨 BUILD BATTLE CLÁSICO"));
        broadcastGame(SmallCaps.convert("§7  ¡Vota una temática! Tienes §e" + THEME_VOTE_TIME + "s"));
        broadcastGame(SmallCaps.convert("§b§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        broadcastGame("");

        titleAll("§b§l🎨 VOTA UNA TEMÁTICA", "§7Elige en tu inventario");
        soundAll(Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);

        // Give theme voting items
        giveThemeVotingItems();
    }

    private void giveThemeVotingItems() {
        themeInventoryGiven = true;
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;
            p.getInventory().clear();
            p.setGameMode(GameMode.ADVENTURE);

            for (int i = 0; i < 4; i++) {
                ItemStack paper = new ItemStack(Material.PAPER);
                ItemMeta meta = paper.getItemMeta();
                meta.setDisplayName("§e§l" + (i + 1) + ". §f" + currentThemeOptions[i]);
                meta.setLore(Arrays.asList("§7Click para votar por:", "§b" + currentThemeOptions[i]));
                paper.setItemMeta(meta);
                p.getInventory().setItem(i + 2, paper); // slots 2-5 (center of hotbar)
            }
        }
    }

    private void handleThemeVoteClick(Player player, int slot) {
        // Slots 2-5 map to theme options 0-3
        int option = slot - 2;
        if (option < 0 || option > 3) return;

        UUID uuid = player.getUniqueId();
        themeVotes.put(uuid, option);
        player.sendMessage(SmallCaps.convert("§a§l✔ §7Has votado por: §b" + currentThemeOptions[option]));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
    }

    private String resolveThemeVote() {
        int[] counts = new int[4];
        for (int vote : themeVotes.values()) {
            if (vote >= 0 && vote < 4) counts[vote]++;
        }
        int maxVotes = -1;
        List<Integer> winners = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            if (counts[i] > maxVotes) {
                maxVotes = counts[i];
                winners.clear();
                winners.add(i);
            } else if (counts[i] == maxVotes) {
                winners.add(i);
            }
        }
        int winnerIdx = winners.get(new Random().nextInt(winners.size()));
        return currentThemeOptions[winnerIdx];
    }

    // ==================== PHASE 2: BUILD ====================

    private void startBuildPhase() {
        selectedTheme = resolveThemeVote();
        currentPhase = Phase.BUILD;
        phaseTimer = BUILD_TIME;

        broadcastGame("");
        broadcastGame(SmallCaps.convert("§b§l  🎨 TEMÁTICA: §e§l" + selectedTheme.toUpperCase()));
        broadcastGame(SmallCaps.convert("§7  ¡Tienes §e" + BUILD_TIME + " segundos §7para construir!"));
        broadcastGame("");

        titleAll("§e§l🔨 ¡CONSTRUYE!", "§7Temática: §b" + selectedTheme + " §7| §e" + (BUILD_TIME / 60) + " min");
        soundAll(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f);

        // Set creative mode and TP to plots
        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;
            PlotArea plot = playerPlots.get(uuid);
            if (plot != null && world != null) {
                p.getInventory().clear();
                p.setGameMode(GameMode.CREATIVE);
                p.setAllowFlight(true);
                p.setFlying(true);
                p.teleport(new Location(world,
                        plot.x + plot.size / 2.0 + 0.5,
                        ARENA_Y + 1,
                        plot.z + plot.size / 2.0 + 0.5));
            }
        }
    }

    // ==================== PHASE 3: VOTING ====================

    private void startVotingPhase() {
        currentPhase = Phase.VOTING;
        currentVotingIndex = -1;
        allVotes.clear();
        totalScores.clear();

        for (UUID uuid : plotOrder) {
            totalScores.put(uuid, 0);
        }

        broadcastGame("");
        broadcastGame(SmallCaps.convert("§c§l⏱ ¡TIEMPO! §7Se acabó la construcción."));
        broadcastGame(SmallCaps.convert("§b§l  📊 FASE DE VOTACIÓN"));
        broadcastGame(SmallCaps.convert("§7  Visita cada construcción y vota con los items de tu inventario."));
        broadcastGame("");

        titleAll("§b§l📊 VOTACIÓN", "§7Vota las construcciones de los demás");
        soundAll(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);

        // Switch all players to adventure mode
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;
            p.setGameMode(GameMode.ADVENTURE);
            p.setAllowFlight(true);
            p.setFlying(true);
            p.getInventory().clear();
        }

        // Start visiting first build after 2 seconds
        Bukkit.getScheduler().runTaskLater(plugin, this::visitNextBuild, 40L);
    }

    private void visitNextBuild() {
        if (!running) return;

        currentVotingIndex++;

        // Skip to next plot that has a valid player
        while (currentVotingIndex < plotOrder.size()) {
            UUID target = plotOrder.get(currentVotingIndex);
            if (alivePlayers.contains(target)) break;
            currentVotingIndex++;
        }

        if (currentVotingIndex >= plotOrder.size()) {
            // All builds visited — show results
            startResultsPhase();
            return;
        }

        currentVotingTarget = plotOrder.get(currentVotingIndex);
        allVotes.put(currentVotingTarget, new HashMap<>());
        phaseTimer = VOTE_TIME_PER_BUILD;

        Player builder = Bukkit.getPlayer(currentVotingTarget);
        String builderName = builder != null ? builder.getName() : "???";

        broadcastGame(SmallCaps.convert("§b§l📊 §7Visitando la construcción de §e" + builderName
                + " §7(" + (currentVotingIndex + 1) + "/" + plotOrder.size() + ")"));

        // TP everyone to the builder's plot
        PlotArea plot = playerPlots.get(currentVotingTarget);
        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (plot != null && world != null) {
            Location viewLoc = new Location(world,
                    plot.x + plot.size / 2.0 + 0.5,
                    ARENA_Y + plot.height / 2.0,
                    plot.z + 2,
                    0, 25); // face south into the plot
            for (UUID uuid : alivePlayers) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline()) continue;
                p.teleport(viewLoc);
                p.setAllowFlight(true);
                p.setFlying(true);

                // Open vote GUI (except for the builder)
                p.getInventory().clear();
                if (!uuid.equals(currentVotingTarget)) {
                    // Give a nether star to re-open the GUI
                    ItemStack voteStar = new ItemStack(Material.NETHER_STAR);
                    ItemMeta vsMeta = voteStar.getItemMeta();
                    vsMeta.setDisplayName("§b§l⭐ Abrir Panel de Votación");
                    vsMeta.setLore(Arrays.asList("§7Click derecho para votar"));
                    voteStar.setItemMeta(vsMeta);
                    p.getInventory().setItem(4, voteStar);
                    // Auto-open GUI after short delay
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (p.isOnline() && currentPhase == Phase.VOTING) openVoteGUI(p);
                    }, 10L);
                } else {
                    p.sendMessage(SmallCaps.convert("§e§l⭐ §7¡Es tu construcción! Esperando votos..."));
                }
            }
        }

        titleAll("§e" + builderName, "§7Vota su construcción | §b" + selectedTheme);
        soundAll(Sound.BLOCK_NOTE_BLOCK_HARP, 1.0f, 1.5f);
    }

    public void openVoteGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, VOTE_GUI_TITLE);

        // Fill borders with gray glass
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bMeta = border.getItemMeta();
        bMeta.setDisplayName(" ");
        border.setItemMeta(bMeta);
        for (int i = 0; i < 27; i++) gui.setItem(i, border);

        // Place vote items in row 2 (slots 10-15)
        for (int i = 0; i < VOTE_NAMES.length; i++) {
            ItemStack item = new ItemStack(VOTE_MATERIALS[i]);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(VOTE_NAMES[i]);
            meta.setLore(Arrays.asList(
                    "",
                    "§7Puntos: §e" + VOTE_POINTS[i],
                    "",
                    "§a▶ Click para votar"
            ));
            item.setItemMeta(meta);
            gui.setItem(10 + i, item);
        }

        // Theme indicator in slot 4
        ItemStack themeItem = new ItemStack(Material.PAINTING);
        ItemMeta tMeta = themeItem.getItemMeta();
        tMeta.setDisplayName("§b§l🎨 Temática: §e" + selectedTheme);
        Player builder = Bukkit.getPlayer(currentVotingTarget);
        String bName = builder != null ? builder.getName() : "???";
        tMeta.setLore(Arrays.asList("§7Construcción de §f" + bName));
        themeItem.setItemMeta(tMeta);
        gui.setItem(4, themeItem);

        player.openInventory(gui);
    }

    // ==================== PHASE 4: RESULTS ====================

    private void startResultsPhase() {
        currentPhase = Phase.RESULTS;
        currentVotingTarget = null;

        // Calculate total scores
        for (Map.Entry<UUID, Map<UUID, Integer>> entry : allVotes.entrySet()) {
            UUID builder = entry.getKey();
            int total = 0;
            for (int pts : entry.getValue().values()) {
                total += pts;
            }
            totalScores.put(builder, total);
        }

        // Sort by score (highest first)
        List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(totalScores.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        // Display results
        broadcastGame("");
        broadcastGame(SmallCaps.convert("§b§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        broadcastGame(SmallCaps.convert("§b§l  🏆 RESULTADOS FINALES"));
        broadcastGame(SmallCaps.convert("§7  Temática: §b" + selectedTheme));
        broadcastGame(SmallCaps.convert("§b§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));

        int rank = 1;
        for (Map.Entry<UUID, Integer> entry : sorted) {
            Player p = Bukkit.getPlayer(entry.getKey());
            String name = p != null ? p.getName() : "???";
            String medal;
            switch (rank) {
                case 1: medal = "§6§l🥇"; break;
                case 2: medal = "§f🥈"; break;
                case 3: medal = "§c🥉"; break;
                default: medal = "§7§l" + rank + "."; break;
            }
            broadcastGame("  " + medal + " §f" + name + " §7- §e" + entry.getValue() + " pts");
            rank++;
        }
        broadcastGame("");

        // Announce winner
        if (!sorted.isEmpty()) {
            UUID winnerUUID = sorted.get(0).getKey();
            Player winner = Bukkit.getPlayer(winnerUUID);
            if (winner != null) {
                titleAll("§6§l🏆 ¡" + winner.getName() + " GANA!", "§e" + sorted.get(0).getValue() + " puntos §7| §b" + selectedTheme);
                soundAll(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

                // TP everyone to winner's plot for a final look
                PlotArea winPlot = playerPlots.get(winnerUUID);
                World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
                if (winPlot != null && world != null) {
                    Location viewLoc = new Location(world,
                            winPlot.x + winPlot.size / 2.0 + 0.5,
                            ARENA_Y + winPlot.height - 2,
                            winPlot.z - 3,
                            0, 25);
                    for (UUID uuid : alivePlayers) {
                        Player pl = Bukkit.getPlayer(uuid);
                        if (pl != null && pl.isOnline()) {
                            pl.teleport(viewLoc);
                            pl.getInventory().clear();
                        }
                    }
                }

                // Fireworks
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (winner.isOnline()) {
                        Location loc = winner.getLocation();
                        for (int i = 0; i < 5; i++) {
                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                if (winner.isOnline()) {
                                    loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 2, 0), 50, 2, 2, 2, 0.1);
                                }
                            }, i * 10L);
                        }
                    }
                }, 20L);

                // End game with winner after 8 seconds
                Bukkit.getScheduler().runTaskLater(plugin, () -> endGame(winner), 160L);
            } else {
                endGame(null);
            }
        } else {
            endGame(null);
        }
    }

    // ==================== TICK ====================

    @Override
    public void onTick() {
        phaseTimer--;

        switch (currentPhase) {
            case THEME_VOTE:
                tickThemeVote();
                break;
            case BUILD:
                tickBuild();
                break;
            case VOTING:
                tickVoting();
                break;
            case RESULTS:
                // Handled by scheduled tasks
                break;
        }
    }

    private void tickThemeVote() {
        // Re-give items if player somehow lost them
        if (themeInventoryGiven && phaseTimer % 3 == 0) {
            for (UUID uuid : alivePlayers) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline() && p.getInventory().getItem(2) == null) {
                    giveThemeVotingItems();
                    break;
                }
            }
        }

        // Actionbar
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                String color = phaseTimer <= 5 ? "§c" : "§e";
                int voted = themeVotes.size();
                ActionBarUtil.send(p, "§b§l🎨 VOTA §7| " + color + phaseTimer + "s §7| §aVotos: " + voted + "/" + alivePlayers.size());
            }
        }

        if (phaseTimer <= 5 && phaseTimer > 0) {
            soundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 2.0f);
        }

        if (phaseTimer <= 0) {
            startBuildPhase();
        }
    }

    private void tickBuild() {
        // Timer warnings
        if (phaseTimer == 300 || phaseTimer == 180 || phaseTimer == 60 || phaseTimer == 30 || phaseTimer == 10 || phaseTimer <= 5) {
            String color = phaseTimer <= 5 ? "§c§l" : phaseTimer <= 10 ? "§c" : "§e";
            String timeStr;
            if (phaseTimer >= 60) {
                timeStr = (phaseTimer / 60) + " min" + (phaseTimer % 60 > 0 ? " " + (phaseTimer % 60) + "s" : "");
            } else {
                timeStr = phaseTimer + "s";
            }
            broadcastGame(SmallCaps.convert("§b§l🔨 §7Quedan " + color + timeStr));
            if (phaseTimer <= 5) {
                soundAll(Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, phaseTimer == 0 ? 2.0f : 1.5f);
            }
        }

        // Actionbar
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                String timerColor = phaseTimer <= 10 ? "§c" : phaseTimer <= 30 ? "§e" : "§a";
                int min = phaseTimer / 60;
                int sec = phaseTimer % 60;
                ActionBarUtil.send(p, timerColor + "⏱ " + min + ":" + String.format("%02d", sec) + " §7| §b🎨 " + selectedTheme);
            }
        }

        if (phaseTimer <= 0) {
            startVotingPhase();
        }
    }

    private void tickVoting() {
        if (currentVotingTarget == null) return;

        // Actionbar
        Player builder = Bukkit.getPlayer(currentVotingTarget);
        String builderName = builder != null ? builder.getName() : "???";
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                String color = phaseTimer <= 5 ? "§c" : "§e";
                ActionBarUtil.send(p, "§b§l📊 §7Construcción de §e" + builderName + " §7| " + color + phaseTimer + "s");
            }
        }

        if (phaseTimer <= 3 && phaseTimer > 0) {
            soundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 2.0f);
        }

        if (phaseTimer <= 0) {
            // Auto-vote default (OK = 4 pts) for players who didn't vote
            Map<UUID, Integer> votes = allVotes.get(currentVotingTarget);
            if (votes != null) {
                for (UUID uuid : alivePlayers) {
                    if (!uuid.equals(currentVotingTarget) && !votes.containsKey(uuid)) {
                        votes.put(uuid, VOTE_POINTS[VOTE_OK]); // default OK
                    }
                }
            }
            visitNextBuild();
        }
    }

    // ==================== CLEANUP ====================

    @Override
    public void onCleanup() {
        currentPhase = Phase.RESULTS;
        playerPlots.clear();
        plotOrder.clear();
        allVotes.clear();
        totalScores.clear();
        themeVotes.clear();
    }

    @Override
    public void checkWinCondition() {
        // Don't end game from player elimination — this game has its own flow
    }

    @Override
    protected void onPreCountdown() {
        // Freeze players during countdown
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.setGameMode(GameMode.ADVENTURE);
                p.getInventory().clear();
            }
        }
    }

    // ==================== INNER CLASSES ====================

    public static class PlotArea {
        public final int x, y, z;
        public final int size, height;

        PlotArea(int x, int y, int z, int size, int height) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.size = size;
            this.height = height;
        }
    }
}
