package com.moonlight.coreprotect.minigames;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * /join y /minijuegos - Comando para unirse a minijuegos.
 * También muestra info si no hay minijuego activo.
 */
public class JoinCommand implements CommandExecutor, TabCompleter {

    private final CoreProtectPlugin plugin;

    public JoinCommand(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    private static String sc(String s) { return SmallCaps.convert(s); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cEste comando solo puede ser usado por jugadores.");
            return true;
        }

        Player player = (Player) sender;
        MiniGameManager manager = plugin.getMiniGameManager();

        if (manager == null) {
            player.sendMessage(sc("§c§l✖ §cSistema de minijuegos no disponible."));
            return true;
        }

        // Si hay argumento "salir"
        if (args.length > 0 && (args[0].equalsIgnoreCase("salir") || args[0].equalsIgnoreCase("leave"))) {
            manager.leavePlayer(player);
            return true;
        }

        // Si hay argumento "info"
        if (args.length > 0 && args[0].equalsIgnoreCase("info")) {
            showInfo(player, manager);
            return true;
        }

        // Intentar unirse
        if (manager.isJoinPhase()) {
            manager.joinPlayer(player);
        } else if (manager.isGameActive()) {
            player.sendMessage(sc("§c§l✖ §cEl minijuego ya ha empezado. Espera al próximo."));
            player.sendMessage(sc("§7  Próximo minijuego en: §e" + manager.formatTime(manager.getCountdownSeconds())));
        } else {
            // Mostrar info
            player.sendMessage("");
            player.sendMessage(sc("§5§lMINIJUEGOS"));
            player.sendMessage(sc("§7No hay minijuego activo en este momento."));
            player.sendMessage(sc("§7Próximo minijuego en: §e" + manager.formatTime(manager.getCountdownSeconds())));
            player.sendMessage("");
            player.sendMessage(sc("§7Minijuegos disponibles:"));
            for (MiniGameType type : MiniGameType.values()) {
                player.sendMessage(sc("§7 " + type.getColor() + "▸ " + type.getDisplayName()));
            }
            player.sendMessage("");
        }

        return true;
    }

    private void showInfo(Player player, MiniGameManager manager) {
        player.sendMessage("");
        player.sendMessage(sc("§5§lMINIJUEGOS - INFO"));
        player.sendMessage(sc("§7Estado: " + (manager.isGameActive() ? "§a§lEn curso" : manager.isJoinPhase() ? "§e§lAceptando jugadores" : "§7Esperando")));
        
        if (manager.getCurrentGame() != null) {
            player.sendMessage(sc("§7Minijuego actual: " + manager.getCurrentGame().getType().getDisplayName()));
            player.sendMessage(sc("§7Jugadores: §f" + manager.getCurrentGame().getPlayers().size()));
        }

        player.sendMessage(sc("§7Próximo minijuego: §e" + manager.formatTime(manager.getCountdownSeconds())));
        player.sendMessage("");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("info", "salir");
        }
        return new ArrayList<>();
    }
}
