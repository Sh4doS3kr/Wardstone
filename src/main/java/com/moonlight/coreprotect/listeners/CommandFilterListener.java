package com.moonlight.coreprotect.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.util.Set;

/**
 * Filters the command list sent to players on login/tab-complete.
 * - Hides commands the player doesn't have permission for.
 * - Hides all namespaced commands (pluginname:command).
 * - Blocks /plugins, /pl, /ver, /version, etc. for non-ops.
 */
public class CommandFilterListener implements Listener {

    private static final Set<String> BLOCKED_COMMANDS = Set.of(
            "plugins", "pl", "ver", "version", "about", "icanhasbukkit",
            "bukkit", "paper", "spigot", "timings", "restart",
            "rl", "reload", "whitelist", "op", "deop", "ban", "pardon",
            "ban-ip", "banlist", "save-all", "save-on", "save-off", "stop"
    );

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommandSend(PlayerCommandSendEvent event) {
        Player player = event.getPlayer();
        if (player.isOp()) return;

        event.getCommands().removeIf(cmd -> {
            // Hide all namespaced commands (e.g. essentials:pay, bukkit:help)
            if (cmd.contains(":")) return true;

            // Hide blocked server/admin commands
            if (BLOCKED_COMMANDS.contains(cmd.toLowerCase())) return true;

            // Check permission via plugin command registry
            org.bukkit.command.PluginCommand pluginCmd = Bukkit.getPluginCommand(cmd);
            if (pluginCmd != null) {
                String perm = pluginCmd.getPermission();
                if (perm != null && !perm.isEmpty()) {
                    return !player.hasPermission(perm);
                }
            }

            return false;
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommandExecute(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.isOp()) return;

        String cmd = event.getMessage().toLowerCase().split("\\s+")[0].substring(1); // remove leading /
        // Also strip namespace
        if (cmd.contains(":")) cmd = cmd.substring(cmd.indexOf(':') + 1);

        if (BLOCKED_COMMANDS.contains(cmd)) {
            event.setCancelled(true);
            player.sendMessage("§cComando desconocido.");
        }
    }
}
