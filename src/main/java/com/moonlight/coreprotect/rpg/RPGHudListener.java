package com.moonlight.coreprotect.rpg;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RPGHudListener {

    private final CoreProtectPlugin plugin;
    private final Map<UUID, org.bukkit.boss.BossBar> manaBars = new HashMap<>();
    private final Map<UUID, org.bukkit.boss.BossBar> xpBars = new HashMap<>();

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
                    if (rp == null || rp.getRpgClass() == null) {
                        // Remove bars if player has no RPG data
                        removePlayerBars(player.getUniqueId());
                        continue;
                    }

                    updatePlayerBars(player, rp);
                }
            }
        }.runTaskTimer(plugin, 20L, 10L); // Every 0.5s
    }

    private void updatePlayerBars(Player player, RPGPlayer rp) {
        UUID uuid = player.getUniqueId();
        int level = rp.getLevel();
        double mana = rp.getMana();
        double maxMana = 100 + (rp.getStats().getIntelligence() * 5); // Calculate max mana
        double xp = rp.getXp();
        double xpNeeded = rp.getXpToNextLevel();
        double xpPct = xpNeeded > 0 ? (xp / xpNeeded) : 0;

        String className = rp.getRpgClass().getColor() + rp.getRpgClass().getDisplayName();

        // Update Mana Bar
        org.bukkit.boss.BossBar manaBar = manaBars.get(uuid);
        if (manaBar == null) {
            manaBar = Bukkit.createBossBar("", BarColor.BLUE, BarStyle.SOLID);
            manaBar.addPlayer(player);
            manaBars.put(uuid, manaBar);
        }

        double manaProgress = maxMana > 0 ? (double) mana / maxMana : 0;
        String manaTitle = ChatColor.BLUE + "✦ Maná: " + mana + "/" + maxMana + 
                          ChatColor.GRAY + " | " + ChatColor.YELLOW + "Lv" + level + " " + className;
        
        manaBar.setTitle(manaTitle);
        manaBar.setProgress(Math.max(0, Math.min(1, manaProgress)));

        // Update XP Bar
        org.bukkit.boss.BossBar xpBar = xpBars.get(uuid);
        if (xpBar == null) {
            xpBar = Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SOLID);
            xpBar.addPlayer(player);
            xpBars.put(uuid, xpBar);
        }

        String xpTitle = ChatColor.GREEN + "★ XP: " + String.format("%.1f", xpPct * 100) + "%" +
                        ChatColor.GRAY + " (" + String.format("%.0f", xp) + "/" + String.format("%.0f", xpNeeded) + ")";
        
        xpBar.setTitle(xpTitle);
        xpBar.setProgress(Math.max(0, Math.min(1, xpPct)));
    }

    private void removePlayerBars(UUID uuid) {
        org.bukkit.boss.BossBar manaBar = manaBars.remove(uuid);
        if (manaBar != null) {
            manaBar.removeAll();
        }

        org.bukkit.boss.BossBar xpBar = xpBars.remove(uuid);
        if (xpBar != null) {
            xpBar.removeAll();
        }
    }

    public void cleanup() {
        for (org.bukkit.boss.BossBar bar : manaBars.values()) {
            bar.removeAll();
        }
        for (org.bukkit.boss.BossBar bar : xpBars.values()) {
            bar.removeAll();
        }
        manaBars.clear();
        xpBars.clear();
    }
}
