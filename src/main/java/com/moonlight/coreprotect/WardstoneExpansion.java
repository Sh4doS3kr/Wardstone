package com.moonlight.coreprotect;

import com.moonlight.coreprotect.koth.KothManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

/**
 * PlaceholderAPI expansion para Wardstone.
 * Registra placeholders que se actualizan en tiempo real.
 *
 * Placeholders KOTH:
 *   %wardstone_koth_time%      → Tiempo formateado hasta el próximo KOTH (ej: "1h 39m")
 *   %wardstone_koth_status%    → "En curso" / "Inactivo" / "No programado"
 *   %wardstone_koth_next%      → Igual que koth_time pero con icono (ej: "⚔ KOTH 1h 39m")
 *   %wardstone_koth_countdown% → Segundos restantes como número
 *
 * Placeholders Boss Arena:
 *   %wardstone_boss_time%      → Tiempo hasta el próximo boss
 *   %wardstone_boss_next%      → Tiempo con icono (ej: "⚔ BOSS 2h 15m")
 *   %wardstone_boss_active%    → Si hay boss activo
 *
 * Placeholders Prestigio:
 *   %wardstone_prestigio%         → Número de prestigio del jugador (0-20)
 *   %wardstone_prestigio_titulo%  → Título con color del prestigio actual
 *   %wardstone_prestigio_color%   → Color del prestigio actual (para prefijos)
 *   %wardstone_rango%             → Tag corto con degradado (ej: MDR III, DIA, EST)
 */
public class WardstoneExpansion extends PlaceholderExpansion {

    private final CoreProtectPlugin plugin;

    public WardstoneExpansion(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "wardstone";
    }

    @Override
    public String getAuthor() {
        return "MoonlightMC";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        KothManager koth = plugin.getKothManager();
        if (koth == null) return "";

        switch (params.toLowerCase()) {
            case "koth_time": {
                if (koth.isActive()) return "¡En curso!";
                long secs = koth.getSchedulerCountdown();
                if (secs < 0) return "No programado";
                if (secs <= 0) return "¡Iniciando!";
                return formatTime(secs);
            }
            case "koth_status": {
                if (koth.isActive()) return "En curso";
                long secs = koth.getSchedulerCountdown();
                if (secs < 0) return "No programado";
                return "Inactivo";
            }
            case "koth_next": {
                if (koth.isActive()) return "§6§l⚔ §aKOTH ¡En curso!";
                long secs = koth.getSchedulerCountdown();
                if (secs < 0) return "";
                if (secs <= 0) return "§6§l⚔ §aKOTH ¡Iniciando!";
                String time = formatTime(secs);
                if (secs <= 300) return "§6§l⚔ §cKOTH §e" + time;
                if (secs <= 900) return "§6§l⚔ §eKOTH §f" + time;
                return "§6§l⚔ §7KOTH §f" + time;
            }
            case "koth_countdown": {
                if (koth.isActive()) return "0";
                long secs = koth.getSchedulerCountdown();
                return String.valueOf(Math.max(0, secs));
            }
            case "boss_time": {
                com.moonlight.coreprotect.bossarena.BossArenaManager bossManager = plugin.getBossArenaManager();
                if (bossManager == null) return "";
                return bossManager.getTimeUntilSpawn();
            }
            case "boss_next": {
                com.moonlight.coreprotect.bossarena.BossArenaManager bossManager = plugin.getBossArenaManager();
                if (bossManager == null) return "";
                if (bossManager.isBossActive()) return "§4§l⚔ §cVORGATH ¡Activo!";
                String time = bossManager.getTimeUntilSpawn();
                if (time.contains("ACTIVO")) return "§4§l⚔ §cVORGATH ¡Activo!";
                if (time.contains("Pronto")) return "§4§l⚔ §eVORGATH ¡Pronto!";
                return "§4§l⚔ §7VORGATH §f" + time;
            }
            case "boss_active": {
                com.moonlight.coreprotect.bossarena.BossArenaManager bossManager = plugin.getBossArenaManager();
                if (bossManager == null) return "§cNo";
                return bossManager.isBossActive() ? "§a§lSí" : "§cNo";
            }
            case "esencias": {
                if (player == null) return "0";
                com.moonlight.coreprotect.essence.EssenceManager em = plugin.getEssenceManager();
                if (em == null) return "0";
                return String.valueOf(em.getEssences(player.getUniqueId()));
            }
            case "prestigio": {
                if (player == null) return "0";
                com.moonlight.coreprotect.prestige.PrestigeManager pm = plugin.getPrestigeManager();
                if (pm == null) return "0";
                return String.valueOf(pm.getPrestige(player.getUniqueId()));
            }
            case "prestigio_titulo": {
                if (player == null) return "";
                com.moonlight.coreprotect.prestige.PrestigeManager pm = plugin.getPrestigeManager();
                if (pm == null) return "";
                return pm.getTitle(player.getUniqueId());
            }
            case "prestigio_color": {
                if (player == null) return "";
                com.moonlight.coreprotect.prestige.PrestigeManager pm = plugin.getPrestigeManager();
                if (pm == null) return "";
                return pm.getColor(player.getUniqueId());
            }
            case "prestigio_dmg":
            case "prestigio_mult_dinero": {
                if (player == null) return "+0%";
                com.moonlight.coreprotect.prestige.PrestigeManager pm = plugin.getPrestigeManager();
                if (pm == null) return "+0%";
                return String.format("+%.1f%%", pm.getData(player.getUniqueId()).prestige * 0.5);
            }
            case "prestigio_def":
            case "prestigio_mult_esencias": {
                if (player == null) return "+0%";
                com.moonlight.coreprotect.prestige.PrestigeManager pm = plugin.getPrestigeManager();
                if (pm == null) return "+0%";
                return String.format("+%.1f%%", pm.getData(player.getUniqueId()).prestige * 0.3);
            }
            case "prestigio_hp": {
                if (player == null) return "+0";
                com.moonlight.coreprotect.prestige.PrestigeManager pm = plugin.getPrestigeManager();
                if (pm == null) return "+0";
                int hearts = pm.getData(player.getUniqueId()).prestige / 10;
                return "+" + hearts + " ❤";
            }
            case "prestigio_luck": {
                if (player == null) return "+0%";
                com.moonlight.coreprotect.prestige.PrestigeManager pm = plugin.getPrestigeManager();
                if (pm == null) return "+0%";
                return String.format("+%.1f%%", pm.getData(player.getUniqueId()).prestige * 0.4);
            }
            case "prestigio_regen": {
                if (player == null) return "+0%";
                com.moonlight.coreprotect.prestige.PrestigeManager pm = plugin.getPrestigeManager();
                if (pm == null) return "+0%";
                return String.format("+%.1f%%", pm.getData(player.getUniqueId()).prestige * 0.15);
            }
            case "rango": {
                if (player == null) return "";
                com.moonlight.coreprotect.prestige.PrestigeManager pm = plugin.getPrestigeManager();
                if (pm == null) return "";
                return pm.getShortTag(player.getUniqueId());
            }
            default:
                return null;
        }
    }

    private String formatTime(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else if (minutes > 0) {
            return minutes + "m " + String.format("%02d", seconds) + "s";
        } else {
            return seconds + "s";
        }
    }
}
