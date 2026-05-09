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
import java.util.stream.Collectors;

/**
 * /wardgames - Comando de administración de minijuegos.
 *   /wardgames forcestart [minijuego] - Forzar inicio (aleatorio o específico)
 *   /wardgames stop - Detener minijuego actual
 *   /wardgames info - Información del estado
 *   /wardgames settime <segundos> - Establecer countdown
 *   /wardgames list - Lista de minijuegos
 */
public class MiniGameCommand implements CommandExecutor, TabCompleter {

    private final CoreProtectPlugin plugin;

    public MiniGameCommand(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    private static String sc(String s) { return SmallCaps.convert(s); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MiniGameManager manager = plugin.getMiniGameManager();
        if (manager == null) {
            sender.sendMessage(sc("§c§l✖ §cSistema de minijuegos no disponible."));
            return true;
        }

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "forcestart":
                handleForceStart(sender, args, manager);
                break;
            case "stop":
                handleStop(sender, manager);
                break;
            case "info":
                handleInfo(sender, manager);
                break;
            case "settime":
                handleSetTime(sender, args, manager);
                break;
            case "list":
                handleList(sender);
                break;
            default:
                showHelp(sender);
                break;
        }
        return true;
    }

    private void handleForceStart(CommandSender sender, String[] args, MiniGameManager manager) {
        if (!sender.hasPermission("coreprotect.admin")) {
            sender.sendMessage(sc("§c§l✖ §cNo tienes permiso."));
            return;
        }

        if (manager.isGameActive()) {
            sender.sendMessage(sc("§c§l✖ §cYa hay un minijuego activo. Usa §f/wardgames stop §cprimero."));
            return;
        }

        MiniGameType type = null;
        if (args.length >= 2) {
            type = MiniGameType.fromString(args[1]);
            if (type == null) {
                sender.sendMessage(sc("§c§l✖ §cMinijuego no encontrado: §f" + args[1]));
                sender.sendMessage(sc("§7  Minijuegos disponibles:"));
                for (MiniGameType t : MiniGameType.values()) {
                    sender.sendMessage(sc("§7  - §f" + t.name() + " §7" + t.getDisplayName()));
                }
                return;
            }
        }

        manager.startJoinPhase(type);
        String typeName = type != null ? type.getDisplayName() : "§dAleatorio";
        sender.sendMessage(sc("§a§l✔ §aFase de unión iniciada. Minijuego: " + typeName));
    }

    private void handleStop(CommandSender sender, MiniGameManager manager) {
        if (!sender.hasPermission("coreprotect.admin")) {
            sender.sendMessage(sc("§c§l✖ §cNo tienes permiso."));
            return;
        }

        if (!manager.isGameActive() && !manager.isJoinPhase()) {
            sender.sendMessage(sc("§c§l✖ §cNo hay minijuego activo."));
            return;
        }

        manager.forceStop();
        sender.sendMessage(sc("§a§l✔ §aMinijuego detenido."));
    }

    private void handleInfo(CommandSender sender, MiniGameManager manager) {
        sender.sendMessage("");
        sender.sendMessage(sc("§5§lMINIJUEGOS - INFO"));
        sender.sendMessage(sc("§7Estado: " + (manager.isGameActive() ? "§a§lActivo" : manager.isJoinPhase() ? "§e§lFase de unión" : "§7Inactivo")));
        
        if (manager.getCurrentGame() != null) {
            sender.sendMessage(sc("§7Minijuego: " + manager.getCurrentGame().getType().getDisplayName()));
            sender.sendMessage(sc("§7Jugadores: §f" + manager.getCurrentGame().getPlayers().size()));
            sender.sendMessage(sc("§7Vivos: §a" + manager.getCurrentGame().getAlivePlayers().size()));
        }

        sender.sendMessage(sc("§7Próximo minijuego: §e" + manager.formatTime(manager.getCountdownSeconds())));
        sender.sendMessage(sc("§7Jugadores en cola: §f" + manager.getJoinedPlayers().size()));
        sender.sendMessage("");
    }

    private void handleSetTime(CommandSender sender, String[] args, MiniGameManager manager) {
        if (!sender.hasPermission("coreprotect.admin")) {
            sender.sendMessage(sc("§c§l✖ §cNo tienes permiso."));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(sc("§c§l✖ §cUso: §f/wardgames settime <segundos>"));
            return;
        }

        try {
            long seconds = Long.parseLong(args[1]);
            if (seconds < 0) seconds = 0;
            manager.setCountdownSeconds(seconds);
            sender.sendMessage(sc("§a§l✔ §aCountdown establecido a §e" + manager.formatTime(seconds)));
        } catch (NumberFormatException e) {
            sender.sendMessage(sc("§c§l✖ §cNúmero inválido."));
        }
    }

    private void handleList(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(sc("§5§lMINIJUEGOS DISPONIBLES"));
        for (MiniGameType type : MiniGameType.values()) {
            sender.sendMessage(sc("§7 " + type.getColor() + "▸ " + type.getDisplayName() + " §7- " + type.getDescription()));
        }
        sender.sendMessage("");
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(sc("§5§lWARDGAMES"));
        sender.sendMessage(sc("§e/wardgames forcestart §7[minijuego] §8- Iniciar minijuego"));
        sender.sendMessage(sc("§e/wardgames stop §8- Detener minijuego"));
        sender.sendMessage(sc("§e/wardgames info §8- Ver estado"));
        sender.sendMessage(sc("§e/wardgames settime §7<segundos> §8- Cambiar countdown"));
        sender.sendMessage(sc("§e/wardgames list §8- Ver minijuegos"));
        sender.sendMessage("");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(Arrays.asList("forcestart", "stop", "info", "settime", "list"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("forcestart")) {
            completions.addAll(Arrays.stream(MiniGameType.values())
                .map(t -> t.name().toLowerCase())
                .collect(Collectors.toList()));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("settime")) {
            completions.addAll(Arrays.asList("60", "300", "600", "1800", "3600"));
        }
        
        String input = args[args.length - 1].toLowerCase();
        return completions.stream().filter(s -> s.toLowerCase().startsWith(input)).collect(Collectors.toList());
    }
}
