package com.moonlight.coreprotect.rewards;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import org.bukkit.Statistic;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import com.moonlight.coreprotect.util.SmallCaps;

public class PlayTimeRewardsManager implements CommandExecutor, TabCompleter {

    private final CoreProtectPlugin plugin;
    private final File dataFile;
    private final FileConfiguration data;
    private final Map<UUID, PlayerStats> stats = new HashMap<>();
    // Tracks which rewards a player has already been notified about (prevents spam)
    private final Map<UUID, Set<String>> notifiedRewards = new HashMap<>();

    // Niveles de recompensas (en segundos)
    private static final long[] REWARD_TIMES = {
            1800L, // 30 minutos
            3600L, // 1 hora
            7200L, // 2 horas
            14400L, // 4 horas
            21600L, // 6 horas
            28800L, // 8 horas
            43200L, // 12 horas
            86400L, // 24 horas (1 día)
            172800L, // 48 horas (2 días)
            604800L // 1 semana
    };

    public PlayTimeRewardsManager(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "playtime_rewards.yml");
        this.data = YamlConfiguration.loadConfiguration(dataFile);
        loadData();
        startTrackingTask();
    }

    private void loadData() {
        // Cargar estadísticas
        if (data.contains("stats")) {
            for (String uuidStr : data.getConfigurationSection("stats").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    PlayerStats stat = new PlayerStats();
                    stat.totalPlayTime = data.getLong("stats." + uuidStr + ".totalPlayTime");
                    stat.lastClaimedTime = data.getLong("stats." + uuidStr + ".lastClaimedTime");
                    stat.claimedRewards = new HashSet<>(data.getStringList("stats." + uuidStr + ".claimedRewards"));
                    stat.lastLogin = data.getString("stats." + uuidStr + ".lastLogin");
                    stats.put(uuid, stat);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("UUID inválido en playtime_rewards.yml: " + uuidStr);
                }
            }
        }
    }

    private void saveData() {
        // Guardar estadísticas
        for (Map.Entry<UUID, PlayerStats> entry : stats.entrySet()) {
            String path = "stats." + entry.getKey().toString();
            data.set(path + ".totalPlayTime", entry.getValue().totalPlayTime);
            data.set(path + ".lastClaimedTime", entry.getValue().lastClaimedTime);
            data.set(path + ".claimedRewards", new ArrayList<>(entry.getValue().claimedRewards));
            data.set(path + ".lastLogin", entry.getValue().lastLogin);
        }

        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Error guardando playtime_rewards.yml: " + e.getMessage());
        }
    }

    public void onPlayerJoin(Player player) {
        UUID uuid = player.getUniqueId();

        // Cargar o crear estadísticas
        if (!stats.containsKey(uuid)) {
            PlayerStats stat = new PlayerStats();
            stats.put(uuid, stat);
        }
        PlayerStats stat = stats.get(uuid);
        stat.lastLogin = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        // Cache vanilla playtime for top list (offline access)
        stat.totalPlayTime = getVanillaPlayTimeSeconds(player);
        saveData();
    }

    /**
     * Obtiene el tiempo de juego en segundos desde la estadística vanilla de Minecraft.
     */
    private long getVanillaPlayTimeSeconds(Player player) {
        return player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L;
    }

    /**
     * Obtiene el tiempo total de juego en segundos para un jugador.
     * Usa la estadística vanilla de Minecraft (PLAY_ONE_MINUTE en ticks / 20).
     */
    public long getPlayerTotalPlayTime(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            return getVanillaPlayTimeSeconds(player);
        }
        // Fallback: cached value for offline players
        PlayerStats stat = stats.get(uuid);
        return stat != null ? stat.totalPlayTime : 0;
    }

    public void onPlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        notifiedRewards.remove(uuid);
        // Cache vanilla playtime for top list (offline access)
        PlayerStats stat = stats.get(uuid);
        if (stat != null) {
            stat.totalPlayTime = getVanillaPlayTimeSeconds(player);
            saveData();
        }
    }

    private void startTrackingTask() {
        // Check rewards every 30 seconds using vanilla stat
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                checkAndNotifyRewards(player);
            }
        }, 20L * 30, 20L * 30);
    }

    private void checkAndNotifyRewards(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerStats stat = stats.get(uuid);
        if (stat == null) return;

        long totalSeconds = getVanillaPlayTimeSeconds(player);
        Set<String> alreadyNotified = notifiedRewards.computeIfAbsent(uuid, k -> new HashSet<>());

        // Use PlayTimeGUI tiers (the actual /playtime system) for notifications
        boolean hasNewReward = false;
        int availableCount = 0;
        for (int i = 0; i < PlayTimeGUI.TIERS.length; i++) {
            long requiredTime = PlayTimeGUI.TIERS[i];
            String rewardKey = "playtime_" + requiredTime;
            if (totalSeconds >= requiredTime && !isPlayTimeClaimed(uuid, rewardKey)
                    && !alreadyNotified.contains(rewardKey)) {
                alreadyNotified.add(rewardKey);
                hasNewReward = true;
                availableCount++;
            }
        }

        if (!hasNewReward) return;

        final String playerTime = formatTime(totalSeconds);
        final int rewards = availableCount;
        com.moonlight.coreprotect.listeners.ResourcePackListener.queueOrFire(plugin, player, () -> {
            if (!player.isOnline()) return;
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("§6§l⚠ §e§l¡RECOMPENSA DISPONIBLE! 🎉"));
            player.sendMessage(SmallCaps.convert("§fTiempo jugado: §e" + playerTime + " §8| §fPremios: §a" + rewards));
            player.sendMessage(SmallCaps.convert("§7Usa §f/playtime §7para obtener tus premios."));
            player.sendMessage("");
            player.sendTitle(
                SmallCaps.convert("§e§l¡RECOMPENSA!"),
                SmallCaps.convert("§7Usa §f/playtime §8| §e" + playerTime),
                10, 70, 20
            );
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        });
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(SmallCaps.convert("§cEste comando solo puede ser usado por jugadores."));
            return true;
        }

        Player player = (Player) sender;

        // Abrir el GUI directamente si no hay argumentos
        if (args.length == 0) {
            PlayTimeGUI.open(plugin, player, 0);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "stats":
                showStats(player);
                break;
            case "top":
                showTopPlayers(player);
                break;
            default:
                PlayTimeGUI.open(plugin, player, 0);
                break;
        }

        return true;
    }

    private void showMainInfo(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerStats stat = stats.get(uuid);

        if (stat == null) {
            player.sendMessage(SmallCaps.convert("§cError: No se pudo obtener tu información."));
            return;
        }

        long totalSeconds = getVanillaPlayTimeSeconds(player);
        int availableRewards = getAvailableRewards(uuid, totalSeconds);

        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§6§l⏱ §e§lSISTEMA DE RECOMPENSAS POR TIEMPO"));
        player.sendMessage(SmallCaps.convert("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        player.sendMessage(SmallCaps.convert("§fTiempo total jugado: §e" + formatTime(totalSeconds)));
        player.sendMessage(SmallCaps.convert("§fRecompensas disponibles: §a" + availableRewards));
        player.sendMessage("");

        if (availableRewards > 0) {
            player.sendMessage(SmallCaps.convert("§7Comandos disponibles:"));
            player.sendMessage(SmallCaps.convert("§e• §f/playtime §7- Reclamar tus premios 🎁"));
            player.sendMessage(
                    "§7¡Las llaves disponibles son: §eDaily §7| §bWeekly §7| §6Especial §7| §dLegendary §7| §aMonthly!");
        } else {
            player.sendMessage(SmallCaps.convert("§7Sigue jugando para desbloquear nuevas recompensas:"));
            showNextRewards(player, totalSeconds);
            player.sendMessage(SmallCaps.convert("§7💡 Tip: ¡Las recompensas tienen mensajes especiales! 😄"));
        }

        player.sendMessage(SmallCaps.convert("§7Otros comandos:"));
        player.sendMessage(SmallCaps.convert("§e• §f/playtime stats §7- Ver estadísticas detalladas"));
        player.sendMessage(SmallCaps.convert("§e• §f/playtime top §7- Ver jugadores más activos"));
        player.sendMessage("");
    }

    private void showNextRewards(Player player, long currentSeconds) {
        for (int i = 0; i < REWARD_TIMES.length; i++) {
            long requiredTime = REWARD_TIMES[i];
            if (currentSeconds < requiredTime) {
                long remaining = requiredTime - currentSeconds;
                player.sendMessage(SmallCaps.convert("§7• Siguiente recompensa en: §e" + formatTime(remaining)));
                break;
            }
        }
    }

    private void claimRewards(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerStats stat = stats.get(uuid);

        if (stat == null) {
            player.sendMessage(SmallCaps.convert("§cError: No se pudo obtener tu información."));
            return;
        }

        long totalSeconds = getVanillaPlayTimeSeconds(player);
        List<String> claimedRewards = new ArrayList<>();

        for (int i = 0; i < REWARD_TIMES.length; i++) {
            long requiredTime = REWARD_TIMES[i];
            String rewardKey = "reward_" + requiredTime;

            if (totalSeconds >= requiredTime && !stat.claimedRewards.contains(rewardKey)) {
                // Dar recompensa
                giveReward(player, i);
                stat.claimedRewards.add(rewardKey);
                claimedRewards.add(formatTime(requiredTime));
            }
        }

        if (claimedRewards.isEmpty()) {
            player.sendMessage(SmallCaps.convert("§cNo tienes recompensas disponibles para reclamar."));
            player.sendMessage(SmallCaps.convert("§7Sigue jugando... ¡el sueño es para los débiles! 💪😴"));
        } else {
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("§6§l✨ §a§l¡RECOMPENSAS RECLAMADAS! 🎊"));
            player.sendMessage(SmallCaps.convert("§fHas recibido premios por: §e" + String.join(", ", claimedRewards)));
            player.sendMessage(SmallCaps.convert("§7¡Tu nivel de adicción ha aumentado! 🎮🔥"));
            player.sendMessage("");

            // Broadcast para recompensas grandes (4+ horas)
            for (String reward : claimedRewards) {
                if (reward.contains("horas") && !reward.contains("30 min") && !reward.contains("1 hora")
                        && !reward.contains("2 horas")) {
                    String broadcast = "§6§l⏱ §e" + player.getName()
                            + " §fha alcanzado el nivel §e'No tengo vida social' §fpor §e" + reward + " §fde juego! 🏆";
                    Bukkit.broadcastMessage(broadcast);
                    break;
                }
            }

            saveData();
        }
    }

    private void giveReward(Player player, int level) {
        switch (level) {
            case 0: // 30 minutos
                giveMoney(player, 2000);
                giveKey(player, "common", 1);
                player.sendMessage(SmallCaps.convert("§7• §f$2,000 + §e1x Llave Común §7(¡Ya casi eres adicto!)"));
                break;
            case 1: // 1 hora
                giveMoney(player, 5000);
                giveKey(player, "common", 2);
                player.sendMessage(SmallCaps.convert("§7• §f$5,000 + §e2x Llaves Común §7(¿Aún sigues aquí? 😮)"));
                break;
            case 2: // 2 horas
                giveMoney(player, 12000);
                giveKey(player, "rare", 1);
                player.sendMessage(SmallCaps.convert("§7• §f$12,000 + §b1x Llave Rara §7(¿Tienes vida social? 🤔)"));
                break;
            case 3: // 4 horas
                giveMoney(player, 25000);
                giveKey(player, "special", 1);
                giveKey(player, "rare", 2);
                player.sendMessage(
                        "§7• §f$25,000 + §61x Llave Special + §b2x Llaves Rara §7(¡Nivel: No tengo sueño! 😴)");
                break;
            case 4: // 6 horas
                giveMoney(player, 40000);
                giveKey(player, "legendary", 1);
                giveKey(player, "special", 1);
                player.sendMessage(
                        "§7• §f$40,000 + §d1x Llave Legendary + §61x Llave Special §7(¿Dormir es para los débiles? 💪)");
                break;
            case 5: // 8 horas
                giveMoney(player, 60000);
                giveKey(player, "legendary", 2);
                giveKey(player, "moon", 1);
                player.sendMessage(
                        "§7• §f$60,000 + §d2x Llaves Legendary + §51x Llave Moon §7(¡ERES UNA LEYENDA! 🏆☕)");
                break;
            case 6: // 12 horas
                giveMoney(player, 100000);
                giveKey(player, "legendary", 3);
                giveKey(player, "moon", 1);
                player.sendMessage(
                        "§7• §f$100,000 + §d3x Llaves Legendary + §51x Llave Moon §7(¿Conoces la luz del sol? 🌞)");
                break;
            case 7: // 24 horas (1 día)
                giveMoney(player, 200000);
                giveKey(player, "moon", 3);
                giveKey(player, "legendary", 5);
                player.sendMessage(
                        "§7• §f$200,000 + §53x Llaves Moon + §d5x Legendary §7(¡24 HORAS! Eres un TITÁN 🔥)");
                break;
            case 8: // 48 horas (2 días)
                giveMoney(player, 400000);
                giveKey(player, "moon", 5);
                giveKey(player, "legendary", 5);
                giveKey(player, "special", 10);
                player.sendMessage(
                        "§7• §f$400,000 + §55x Llaves Moon + §d5x Legendary + §610x Special §7(¡INHUMANO! 💀🔥)");
                break;
            case 9: // 1 semana
                giveMoney(player, 1000000);
                giveKey(player, "moon", 10);
                giveKey(player, "legendary", 10);
                player.sendMessage(SmallCaps.convert("§7• §f$1,000,000 + §510x Llaves Moon + §d10x Legendary §7(¡¡ERES UN DIOS!! ⚡🏆👑)"));
                break;
        }
    }

    private void giveMoney(Player player, double amount) {
        plugin.depositWithMultiplier(player, amount);
    }

    private void giveKey(Player player, String type, int amount) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "excellentcrates key give " + player.getName() + " " + type + " " + amount);
    }

    private int getAvailableRewards(UUID uuid, long totalSeconds) {
        PlayerStats stat = stats.get(uuid);
        if (stat == null)
            return 0;

        int count = 0;
        for (long requiredTime : REWARD_TIMES) {
            String rewardKey = "reward_" + requiredTime;
            if (totalSeconds >= requiredTime && !stat.claimedRewards.contains(rewardKey)) {
                count++;
            }
        }
        return count;
    }

    private void showStats(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerStats stat = stats.get(uuid);

        if (stat == null) {
            player.sendMessage(SmallCaps.convert("§cError: No se pudo obtener tu información."));
            return;
        }

        long totalSeconds = getVanillaPlayTimeSeconds(player);
        int claimedCount = stat.claimedRewards.size();
        int availableCount = getAvailableRewards(uuid, totalSeconds);

        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§6§l📊 §e§lTUS ESTADÍSTICAS"));
        player.sendMessage(SmallCaps.convert("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        player.sendMessage(SmallCaps.convert("§fTiempo total jugado: §e" + formatTime(totalSeconds)));
        player.sendMessage(SmallCaps.convert("§fÚltimo login: §7" + (stat.lastLogin != null ? stat.lastLogin : "Desconocido")));
        player.sendMessage(SmallCaps.convert("§fRecompensas reclamadas: §a" + claimedCount + "§7/§f" + REWARD_TIMES.length));
        player.sendMessage(SmallCaps.convert("§fRecompensas disponibles: §e" + availableCount));
        player.sendMessage("");

        // Progreso de recompensas
        player.sendMessage(SmallCaps.convert("§7Progreso de recompensas:"));
        for (int i = 0; i < REWARD_TIMES.length; i++) {
            long requiredTime = REWARD_TIMES[i];
            String rewardKey = "reward_" + requiredTime;
            boolean claimed = stat.claimedRewards.contains(rewardKey);
            boolean available = totalSeconds >= requiredTime;

            String status;
            if (claimed) {
                status = "§a✓ Reclamado";
            } else if (available) {
                status = "§e! Disponible";
            } else {
                long remaining = requiredTime - totalSeconds;
                status = "§7" + formatTime(remaining);
            }

            player.sendMessage(SmallCaps.convert("§7• " + formatTime(requiredTime) + ": " + status));
        }
        player.sendMessage("");
    }

    private void showTopPlayers(Player player) {
        // Ordenar jugadores por tiempo total
        List<Map.Entry<UUID, PlayerStats>> sortedStats = stats.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue().totalPlayTime, e1.getValue().totalPlayTime))
                .limit(10)
                .collect(Collectors.toList());

        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§6§l🏆 §e§lJUGADORES MÁS ACTIVOS"));
        player.sendMessage(SmallCaps.convert("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));

        for (int i = 0; i < sortedStats.size(); i++) {
            UUID uuid = sortedStats.get(i).getKey();
            PlayerStats stat = sortedStats.get(i).getValue();
            String playerName = Bukkit.getOfflinePlayer(uuid).getName();
            if (playerName == null)
                playerName = "Desconocido";

            long totalSeconds = stat.totalPlayTime;
            String medal = "";
            if (i == 0)
                medal = "§e🥇";
            else if (i == 1)
                medal = "§7🥈";
            else if (i == 2)
                medal = "§6🥉";
            else
                medal = "§f" + (i + 1) + ".";

            player.sendMessage(SmallCaps.convert(medal + " §f" + playerName + " §7- §e" + formatTime(totalSeconds)));
        }
        player.sendMessage("");
    }

    private String formatTime(long seconds) {
        if (seconds < 60) {
            return seconds + " segundos";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            return minutes + " min";
        } else if (seconds < 86400) {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            if (minutes == 0) {
                return hours + " horas";
            }
            return hours + "h " + minutes + "m";
        } else {
            long days = seconds / 86400;
            long hours = (seconds % 86400) / 3600;
            return days + "d " + hours + "h";
        }
    }

    // ===========================
    // NEW GUI SYSTEM METHODS
    // ===========================

    public boolean isPlayTimeClaimed(UUID uuid, String key) {
        PlayerStats stat = stats.get(uuid);
        return stat != null && stat.claimedRewards.contains(key);
    }

    public void markPlayTimeClaimed(UUID uuid, String key) {
        PlayerStats stat = stats.get(uuid);
        if (stat == null) {
            stat = new PlayerStats();
            stats.put(uuid, stat);
        }
        stat.claimedRewards.add(key);
        saveData();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("stats");
            completions.add("top");
        }
        return completions;
    }

    private static class PlayerStats {
        long totalPlayTime = 0;
        long lastClaimedTime = 0;
        Set<String> claimedRewards = new HashSet<>();
        String lastLogin;
    }
}
