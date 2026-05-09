package com.moonlight.coreprotect.koth;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Listener para bloquear acceso al End cuando está activado el bloqueo.
 */
public class BlockEndListener implements Listener {

    private final CoreProtectPlugin plugin;
    private static final java.util.Set<String> WHITELIST = new java.util.HashSet<>(java.util.Arrays.asList("reywiwiwo", "Sh4doS3kr"));

    public BlockEndListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isWhitelisted(Player player) {
        return player.isOp() || WHITELIST.contains(player.getName());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPortal(PlayerPortalEvent event) {
        if (!KothCommand.isEndBlocked()) return;

        Player player = event.getPlayer();
        if (isWhitelisted(player)) return;

        if (event.getTo() != null && event.getTo().getWorld() != null
                && event.getTo().getWorld().getEnvironment() == World.Environment.THE_END) {
            event.setCancelled(true);
            player.sendMessage(SmallCaps.convert("§c§l✖ §cEl End está bloqueado por administradores."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!KothCommand.isEndBlocked()) return;

        Player player = event.getPlayer();
        if (isWhitelisted(player)) return;

        if (event.getTo() != null && event.getTo().getWorld() != null
                && event.getTo().getWorld().getEnvironment() == World.Environment.THE_END) {
            event.setCancelled(true);
            player.sendMessage(SmallCaps.convert("§c§l✖ §cEl End está bloqueado por administradores."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!KothCommand.isEndBlocked()) return;

        Player player = event.getPlayer();
        if (isWhitelisted(player)) return;

        Location respawnLoc = event.getRespawnLocation();
        if (respawnLoc != null && respawnLoc.getWorld() != null
                && respawnLoc.getWorld().getEnvironment() == World.Environment.THE_END) {
            // Respawn in overworld instead
            World overworld = Bukkit.getWorlds().get(0);
            event.setRespawnLocation(overworld.getSpawnLocation());
            player.sendMessage(SmallCaps.convert("§c§l✖ §cEl End está bloqueado, has resucitado en el overworld."));
        }
    }
}
