package com.moonlight.coreprotect.listeners;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.prestige.PrestigeManager;
import org.bukkit.*;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.meta.FireworkMeta;

public class JoinQuitMessageListener implements Listener {

    private final CoreProtectPlugin plugin;

    public JoinQuitMessageListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    private String getPrestigeTag(java.util.UUID uuid) {
        PrestigeManager mgr = plugin.getPrestigeManager();
        if (mgr == null) return "";
        int prestige = mgr.getData(uuid).prestige;
        if (prestige <= 0) return "";
        return " " + mgr.getTitle(uuid);
    }

    private int getPrestige(java.util.UUID uuid) {
        PrestigeManager mgr = plugin.getPrestigeManager();
        if (mgr == null) return 0;
        return mgr.getData(uuid).prestige;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent e) {
        e.setJoinMessage(null);
        Player player = e.getPlayer();
        String tag = getPrestigeTag(player.getUniqueId());
        Bukkit.broadcastMessage("§a[+] §f" + player.getName() + tag);

        // New player welcome message (first time joining)
        if (!player.hasPlayedBefore()) {
            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage("§e§l  ⭐ §6§l¡NUEVO JUGADOR! §e§l⭐");
            Bukkit.broadcastMessage("§f  " + player.getName() + " §7se une por primera vez.");
            Bukkit.broadcastMessage("§a  ¡Dadle la bienvenida! §2\uD83C\uDF89");
            Bukkit.broadcastMessage("");
            // Welcome sound for everyone
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.playSound(online.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.5f);
            }
        }

        // Login effects based on prestige tier
        int prestige = getPrestige(player.getUniqueId());
        if (prestige > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> spawnLoginEffect(player, prestige), 10L);
        }

        // Core maintenance notifications
        if (plugin.getCoreMaintenanceManager() != null) {
            plugin.getCoreMaintenanceManager().onPlayerJoin(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent e) {
        e.setQuitMessage(null);
        String tag = getPrestigeTag(e.getPlayer().getUniqueId());
        Bukkit.broadcastMessage("§c[-] §f" + e.getPlayer().getName() + tag);
    }

    private void spawnLoginEffect(Player player, int prestige) {
        if (!player.isOnline()) return;
        Location loc = player.getLocation();
        World world = loc.getWorld();

        if (prestige >= 70) {
            // P70: Fuegos artificiales épicos + totem + título
            player.sendTitle("§6§l★ LEYENDA ★", "§fPrestigio §e§l70", 10, 60, 20);
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1, 0), 100, 1, 1, 1, 0.5);
            world.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 1f);
            for (int i = 0; i < 3; i++) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> spawnFirework(loc, Color.YELLOW, Color.ORANGE, FireworkEffect.Type.STAR), i * 8L);
            }
        } else if (prestige >= 61) {
            // Netherita: Firework + witch particles
            world.spawnParticle(Particle.WITCH, loc.clone().add(0, 1, 0), 50, 1.5, 1, 1.5, 0.1);
            world.playSound(loc, Sound.ENTITY_WITHER_SPAWN, 0.3f, 1.5f);
            spawnFirework(loc, Color.PURPLE, Color.BLACK, FireworkEffect.Type.BALL_LARGE);
        } else if (prestige >= 51) {
            // Amatista: Dragon breath + firework
            world.spawnParticle(Particle.DRAGON_BREATH, loc.clone().add(0, 1, 0), 40, 1.2, 0.8, 1.2, 0.05);
            world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.3f, 1.5f);
            spawnFirework(loc, Color.PURPLE, Color.FUCHSIA, FireworkEffect.Type.BURST);
        } else if (prestige >= 41) {
            // Diamante: Soul fire + sound
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, 1, 0), 35, 1, 0.8, 1, 0.05);
            world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.2f);
            spawnFirework(loc, Color.AQUA, Color.BLUE, FireworkEffect.Type.BALL);
        } else if (prestige >= 31) {
            // Esmeralda: Green burst + sound
            world.spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0, 1, 0), 30, 1, 0.6, 1, 0.1);
            world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.5f);
        } else if (prestige >= 21) {
            // Oro: End rod sparkles + sound
            world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 1, 0), 20, 0.8, 0.5, 0.8, 0.03);
            world.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.5f);
        } else if (prestige >= 11) {
            // Hierro: Crit sparkles + sound
            world.spawnParticle(Particle.CRIT, loc.clone().add(0, 1, 0), 15, 0.5, 0.3, 0.5, 0.1);
            world.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.2f);
        } else {
            // Cobre: Small flame + pling
            world.spawnParticle(Particle.FLAME, loc.clone().add(0, 1, 0), 10, 0.3, 0.2, 0.3, 0.02);
            world.playSound(loc, Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.5f);
        }
    }

    private void spawnFirework(Location loc, Color primary, Color fade, FireworkEffect.Type type) {
        Firework fw = loc.getWorld().spawn(loc, Firework.class);
        FireworkMeta meta = fw.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
                .with(type)
                .withColor(primary)
                .withFade(fade)
                .flicker(true)
                .trail(true)
                .build());
        meta.setPower(0);
        fw.setFireworkMeta(meta);
        Bukkit.getScheduler().runTaskLater(plugin, fw::detonate, 2L);
    }
}
