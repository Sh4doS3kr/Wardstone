package com.moonlight.coreprotect.listeners;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Detecta cuando un jugador se acerca a un Cubee Dizzy (mareado)
 * y le muestra cómo craftear el Bocadillo Cubee para capturarlo.
 */
public class CubeeTutorialListener implements Listener {

    private final CoreProtectPlugin plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final long COOLDOWN_MS = 60000; // 1 minuto entre avisos
    private static final double DETECTION_RADIUS = 8.0; // bloques

    // Mobs que son cubees en estado Dizzy
    private static final String[] DIZZY_CUBEES = {
            "cubee-knocked_out-arctic_witch",
            "cubee-knocked_out-forest_keeper",
            "cubee-knocked_out-meowgician",
            "cubee-knocked_out-wishing_teddy"
    };

    public CubeeTutorialListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Solo si se movió realmente
        if (event.getFrom().distance(event.getTo()) < 0.1) return;

        // Cooldown para no spamear
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (cooldowns.containsKey(uuid) && now - cooldowns.get(uuid) < COOLDOWN_MS) {
            return;
        }

        Location loc = player.getLocation();
        boolean foundDizzyCubee = false;

        // Buscar cubees Dizzy cerca
        for (Entity entity : loc.getWorld().getNearbyEntities(loc, DETECTION_RADIUS, DETECTION_RADIUS, DETECTION_RADIUS)) {
            String type = entity.getType().name();
            // MythicMobs usa custom mobs, necesitamos verificar si es un MythicMob
            if (isMythicMobDizzyCubee(entity)) {
                foundDizzyCubee = true;
                break;
            }
        }

        if (foundDizzyCubee) {
            showTutorial(player);
            cooldowns.put(uuid, now);
        }
    }

    /**
     * Verifica si la entidad es un Cubee Dizzy de MythicMobs
     */
    private boolean isMythicMobDizzyCubee(Entity entity) {
        // MythicMobs guarda el tipo en metadata
        if (!entity.hasMetadata("MythicMobsInternalType")) return false;

        String mythicType = entity.getMetadata("MythicMobsInternalType").get(0).asString();
        for (String dizzy : DIZZY_CUBEES) {
            if (mythicType.equalsIgnoreCase(dizzy)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Muestra el tutorial de crafteo del Bocadillo Cubee
     */
    private void showTutorial(Player player) {
        player.sendMessage("");
        player.sendMessage("§b§l¡Has encontrado un Cubee Dizzy!");
        player.sendMessage("§7Este cubee está mareado y puedes capturarlo.");
        player.sendMessage("");
        player.sendMessage("§e§lCraftea un §bBocadillo Cubee §epara domarlo:");
        player.sendMessage("§7  §fS §7= Semillas de trigo");
        player.sendMessage("§7  §fP §7= Patata");
        player.sendMessage("");
        player.sendMessage("  §f  S  ");
        player.sendMessage("  §f SPS ");
        player.sendMessage("  §f  S  ");
        player.sendMessage("");
        player.sendMessage("§7Click derecho en el Cubee Dizzy con el Bocadillo.");
        player.sendMessage("");
    }
}
