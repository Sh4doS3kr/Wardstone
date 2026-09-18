package com.moonlight.coreprotect.protection;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Bloquea comandos que revelan información de plugins (/pl, /plugins, /help, /?, /ver, /version, /about)
 * y filtra el tab-completion para que solo aparezcan comandos con permisos.
 */
public class CommandBlockerListener implements Listener {

    private final CoreProtectPlugin plugin;

    private static final Set<String> BLOCKED_COMMANDS = new HashSet<>(Arrays.asList(
        "pl", "plugins",
        "bukkit:pl", "bukkit:plugins",
        "help", "bukkit:help",
        "?", "bukkit:?",
        "ver", "version", "bukkit:ver", "bukkit:version",
        "about", "bukkit:about",
        "icanhasbukkit",
        "minecraft:help"
    ));

    public CommandBlockerListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Bloquea comandos de información de plugins para jugadores sin permiso de admin.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage().substring(1).trim(); // Quitar el /
        String baseCommand = message.split(" ")[0].toLowerCase();
        
        // Interceptar comandos /advancedban:* y ejecutarlos sin el prefijo
        if (baseCommand.startsWith("advancedban:")) {
            String newCommand = baseCommand.substring(12); // Remover "advancedban:"
            String[] parts = message.split(" ", 2);
            String finalCommand = "/" + newCommand + (parts.length > 1 ? " " + parts[1] : "");
            event.setCancelled(true);
            plugin.getServer().dispatchCommand(event.getPlayer(), newCommand + (parts.length > 1 ? " " + parts[1] : ""));
            return;
        }
        
        Player player = event.getPlayer();
        if (player.hasPermission("coreprotect.admin") || player.isOp()) return;

        if (BLOCKED_COMMANDS.contains(baseCommand)) {
            event.setCancelled(true);
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo tienes permiso para usar este comando."));
        }
    }

    /**
     * Filtra el tab-completion: solo muestra comandos para los que el jugador tiene permiso.
     * Esto evita que al pulsar / o Tab se vean todos los plugins/comandos del servidor.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommandSend(PlayerCommandSendEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("coreprotect.admin") || player.isOp()) return;

        // Eliminar comandos bloqueados del tab-completion
        event.getCommands().removeAll(BLOCKED_COMMANDS);

        // Eliminar comandos con prefijo de plugin (ej: bukkit:reload, essentials:help)
        // Solo dejar comandos que el jugador realmente puede usar
        event.getCommands().removeIf(cmd -> {
            // Quitar comandos con namespace de plugin que el jugador no debería ver
            if (cmd.contains(":")) {
                // Mantener minecraft: para comandos básicos que tengan permiso
                String namespace = cmd.split(":")[0].toLowerCase();
                if (!namespace.equals("minecraft")) {
                    return true; // Quitar todos los namespace excepto minecraft
                }
            }
            return false;
        });
    }
}
