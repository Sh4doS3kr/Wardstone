package com.moonlight.coreprotect.rpg;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class RPGHudListener {

    private final CoreProtectPlugin plugin;

    public RPGHudListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        startHudTask();
    }

    private void startHudTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    RPGPlayer rp = plugin.getRPGManager().getPlayer(player.getUniqueId());
                    if (!rp.hasClass()) continue;

                    RPGStats stats = rp.getStats();
                    int mana = (int) rp.getMana();
                    int maxMana = (int) stats.getMaxMana();
                    int level = rp.getLevel();
                    double xpPct = rp.getXpProgress() * 100;

                    String manaBar = buildBar(mana, maxMana, ChatColor.BLUE, ChatColor.DARK_GRAY, 10);
                    String xpBar = buildBar((int)(rp.getXpProgress() * 100), 100, ChatColor.GREEN, ChatColor.DARK_GRAY, 10);

                    String className = rp.getRpgClass().getColor() + rp.getRpgClass().getDisplayName();

                    String hud = ChatColor.GRAY + "⚔ " + className + ChatColor.GRAY + " Lv" + ChatColor.YELLOW + level +
                            ChatColor.GRAY + " | " + ChatColor.BLUE + "✦" + manaBar + ChatColor.BLUE + " " + mana +
                            ChatColor.GRAY + " | " + ChatColor.GREEN + "★" + xpBar + ChatColor.GREEN + " " +
                            String.format("%.0f", xpPct) + "%";

                    player.sendTitle("", hud, 0, 15, 5);
                }
            }
        }.runTaskTimer(plugin, 20L, 10L); // Every 0.5s
    }

    private String buildBar(int current, int max, ChatColor filled, ChatColor empty, int length) {
        int fill = max > 0 ? (int)((double) current / max * length) : 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(i < fill ? filled + "▌" : empty + "▌");
        }
        return sb.toString();
    }
}
