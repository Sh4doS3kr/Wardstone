package com.moonlight.coreprotect.koth;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.*;

/**
 * Scoreboard moderna y premium para el sistema KOTH.
 * Diseño inspirado en servidores competitivos pero con branding MoonKOTH.
 * 
 * PRIORIDAD MÁXIMA: Se re-aplica cada segundo para mantener
 * la scoreboard por encima de cualquier otro plugin.
 * 
 * Diseño visual:
 * ┌─────────────────────────┐
 * │ ⚔ MOONKOTH ⚔ │
 * │ │
 * │ ⏱ Tiempo Restante: │
 * │ 04:32 │
 * │ │
 * │ ⚔ Estado: │
 * │ Capturando │
 * │ │
 * │ ♛ Controlando: │
 * │ PlayerName │
 * │ │
 * │ ★ Tus Puntos: 15 │
 * │ ♦ Líder: Name (42) │
 * │ │
 * │ moonlightmc.xyz │
 * └─────────────────────────┘
 */
public class KothScoreboard {

    // Entradas invisibles únicas para cada línea del scoreboard
    private static final String[] INVISIBLE_ENTRIES = {
            "§0§r", "§1§r", "§2§r", "§3§r", "§4§r",
            "§5§r", "§6§r", "§7§r", "§8§r", "§9§r",
            "§a§r", "§b§r", "§c§r", "§d§r", "§e§r"
    };

    // Nombres de los teams para cada línea
    private static final String[] TEAM_NAMES = {
            "koth_00", "koth_01", "koth_02", "koth_03", "koth_04",
            "koth_05", "koth_06", "koth_07", "koth_08", "koth_09",
            "koth_10", "koth_11", "koth_12", "koth_13", "koth_14"
    };

    // Mapa para guardar scoreboards originales de los jugadores
    private final Map<UUID, Scoreboard> originalScoreboards = new HashMap<>();

    // Mapa de scoreboards KOTH activos por jugador
    private final Map<UUID, Scoreboard> kothScoreboards = new HashMap<>();

    /**
     * Aplica la scoreboard KOTH a un jugador.
     * Guarda su scoreboard original para restaurarla después.
     */
    public void applyScoreboard(Player player, int timeRemaining, String status,
            String statusColor, String controlling,
            int playerPoints, String leaderName, int leaderPoints,
            boolean inZone, int keepInvTime) {
        UUID uuid = player.getUniqueId();

        // Guardar scoreboard original (solo la primera vez)
        if (!originalScoreboards.containsKey(uuid)) {
            originalScoreboards.put(uuid, player.getScoreboard());
        }

        // Crear o reutilizar scoreboard
        Scoreboard board = kothScoreboards.get(uuid);
        if (board == null) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            kothScoreboards.put(uuid, board);
            initializeBoard(board);
        }

        // Formatear tiempo
        String timeFormatted = formatTime(timeRemaining);

        // Formatear barras de progreso para el tiempo
        String timeBar = createProgressBar(timeRemaining, 600); // Max 10 min

        // Actualizar líneas del scoreboard
        updateLine(board, 14, ""); // Separador superior
        updateLine(board, 13, "§e ⏱ §fTiempo Restante:");
        updateLine(board, 12, "§f   " + timeBar + " §a" + timeFormatted);
        updateLine(board, 11, ""); // Separador
        updateLine(board, 10,
                "§e 🛡 §fSeguridad (Inv): " + (keepInvTime > 0 ? "§a" + formatTime(keepInvTime) : "§cOFF"));
        updateLine(board, 9, "§e ⚔ §fEstado:");
        updateLine(board, 8, "§f   " + statusColor + status);
        updateLine(board, 7, ""); // Separador
        updateLine(board, 6, "§e ♛ §fControlando:");
        updateLine(board, 5,
                "§f   " + (controlling != null
                        ? "§b" + (controlling.length() > 16 ? controlling.substring(0, 16) : controlling)
                        : "§7¡Nadie!"));
        updateLine(board, 4, "§e ★ §fTus Puntos: §a§l" + playerPoints);
        updateLine(board, 3, "§e ♦ §fLíder: §6" +
                (leaderName != null
                        ? (leaderName.length() > 12 ? leaderName.substring(0, 12) : leaderName) + " §7(" + leaderPoints
                                + ")"
                        : "---"));
        updateLine(board, 2, ""); // Separador
        updateLine(board, 1, "§8 moonlightmc.xyz");

        // Aplicar con máxima prioridad
        player.setScoreboard(board);
    }

    /**
     * Aplica la scoreboard cooperativa: NPCs vs Humanos.
     */
    public void applyCoopScoreboard(Player player, int timeRemaining, String status,
            String statusColor, String controlling,
            int myPoints, int totalHumanPoints, int npcPoints,
            int remainingAttackers, int remainingCapturers, boolean inZone, int keepInvTime) {
        UUID uuid = player.getUniqueId();

        if (!originalScoreboards.containsKey(uuid)) {
            originalScoreboards.put(uuid, player.getScoreboard());
        }

        Scoreboard board = kothScoreboards.get(uuid);
        if (board == null) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            kothScoreboards.put(uuid, board);
            initializeBoard(board);
        }

        String timeFormatted = formatTime(timeRemaining);
        String timeBar = createProgressBar(timeRemaining, 600);
        
        // Quién va ganando
        String winning;
        if (totalHumanPoints > npcPoints) {
            winning = "§a§l¡HUMANOS!";
        } else if (npcPoints > totalHumanPoints) {
            winning = "§c§l¡NPCs!";
        } else {
            winning = "§e§lEMPATE";
        }

        updateLine(board, 14, ""); 
        updateLine(board, 13, "§e ⏱ §fTiempo: " + timeBar + " §a" + timeFormatted);
        updateLine(board, 12, "§e ⚔ §f" + statusColor + status + (controlling != null ? " §7- " + controlling : ""));
        updateLine(board, 11, ""); 
        updateLine(board, 10, "§a ☺ Humanos: §a§l" + totalHumanPoints + " pts");
        updateLine(board, 9,  "§c ☠ NPCs:    §c§l" + npcPoints + " pts");
        updateLine(board, 8, "§e ♛ Ganando: " + winning);
        updateLine(board, 7, ""); 
        updateLine(board, 6, "§e ★ §fTus Puntos: §a§l" + myPoints);
        updateLine(board, 5, "§e 🛡 §fSeguridad: " + (keepInvTime > 0 ? "§a" + formatTime(keepInvTime) : "§cOFF"));
        updateLine(board, 4, ""); 
        updateLine(board, 3, "§c ⚔ §fAtacantes: §c" + remainingAttackers);
        updateLine(board, 2, "§d 🎯 §fCapturadores: §d" + remainingCapturers);
        updateLine(board, 1, "§8 moonlightmc.xyz");

        player.setScoreboard(board);
    }

    /**
     * Inicializa la scoreboard con el objetivo y las entradas.
     */
    private void initializeBoard(Scoreboard board) {
        // Registrar objetivo con título premium
        Objective obj = board.registerNewObjective("moonkoth", "dummy",
                "§6§l⚔ §e§lMOON§6§lKOTH §e§l⚔");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Crear los 15 equipos y entradas
        for (int i = 0; i < 15; i++) {
            Team team = board.registerNewTeam(TEAM_NAMES[i]);
            team.addEntry(INVISIBLE_ENTRIES[i]);
            obj.getScore(INVISIBLE_ENTRIES[i]).setScore(i);
        }
    }

    /**
     * Actualiza una línea específica del scoreboard.
     * 
     * @param board Scoreboard a actualizar
     * @param score Número de línea (1-14, de abajo a arriba)
     * @param text  Texto a mostrar
     */
    private void updateLine(Scoreboard board, int score, String text) {
        if (score < 0 || score >= 15)
            return;

        Team team = board.getTeam(TEAM_NAMES[score]);
        if (team == null)
            return;

        // Dividir texto si es muy largo (máximo 64 chars por prefix)
        if (text.length() > 64) {
            team.setPrefix(text.substring(0, 64));
        } else {
            team.setPrefix(text);
        }
    }

    /**
     * Crea una barra de progreso visual con bloques Unicode.
     * 
     * @param current Valor actual
     * @param max     Valor máximo
     * @return Barra formateada
     */
    private String createProgressBar(int current, int max) {
        int totalBars = 20; // Número de bloques en la barra
        int filledBars = (int) Math.ceil(((double) current / Math.max(max, 1)) * totalBars);
        filledBars = Math.min(filledBars, totalBars);

        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < totalBars; i++) {
            if (i < filledBars) {
                bar.append("§a▌"); // Verde para lleno
            } else {
                bar.append("§8▌"); // Gris para vacío
            }
        }
        return bar.toString();
    }

    /**
     * Formatea el tiempo en mm:ss.
     */
    private String formatTime(int seconds) {
        int min = seconds / 60;
        int sec = seconds % 60;
        return String.format("%02d:%02d", min, sec);
    }

    /**
     * Restaura la scoreboard original de un jugador.
     */
    public void removeScoreboard(Player player) {
        UUID uuid = player.getUniqueId();

        // Restaurar scoreboard original
        Scoreboard original = originalScoreboards.remove(uuid);
        if (original != null) {
            player.setScoreboard(original);
        } else {
            // Si no hay original, poner la principal del servidor
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }

        // Limpiar scoreboard KOTH
        Scoreboard kothBoard = kothScoreboards.remove(uuid);
        if (kothBoard != null) {
            // Limpiar equipos
            for (String teamName : TEAM_NAMES) {
                Team team = kothBoard.getTeam(teamName);
                if (team != null) {
                    try {
                        team.unregister();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    /**
     * Limpia todas las scoreboards activas.
     */
    public void cleanup() {
        for (UUID uuid : new HashSet<>(kothScoreboards.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                removeScoreboard(player);
            }
        }
        originalScoreboards.clear();
        kothScoreboards.clear();
    }

    /**
     * Verifica si un jugador tiene la scoreboard KOTH activa.
     */
    public boolean hasScoreboard(Player player) {
        return kothScoreboards.containsKey(player.getUniqueId());
    }
}
