package com.moonlight.coreprotect.bossarena;

import com.moonlight.coreprotect.CoreProtectPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

/**
 * Placeholders para Boss Arena:
 * %bossarena_time%         → Tiempo hasta el próximo boss
 * %bossarena_active%       → Si hay boss activo (Sí/No)
 * %bossarena_arena%        → Arena actual
 * 
 * También disponible en wardstone:
 * %wardstone_koth_next%    → Tiempo hasta el próximo boss (alias)
 */
public class BossArenaPlaceholder extends PlaceholderExpansion {

    private final CoreProtectPlugin plugin;

    public BossArenaPlaceholder(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "bossarena";
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
        BossArenaManager manager = plugin.getBossArenaManager();
        if (manager == null) return "";

        switch (params.toLowerCase()) {
            case "time":
                return manager.getTimeUntilSpawn();
            case "active":
                return manager.isBossActive() ? "§a§lSí" : "§cNo";
            case "arena":
                if (manager.isBossActive() && manager.getCurrentArena() != null) {
                    return manager.getCurrentArena().getWorldName();
                }
                return "§7Ninguna";
            default:
                return null;
        }
    }
}
