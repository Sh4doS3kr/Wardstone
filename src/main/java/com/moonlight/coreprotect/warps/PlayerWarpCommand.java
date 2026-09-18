package com.moonlight.coreprotect.warps;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PlayerWarpCommand implements CommandExecutor, TabCompleter {

    private final CoreProtectPlugin plugin;

    public PlayerWarpCommand(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cSolo jugadores.");
            return true;
        }
        Player player = (Player) sender;
        PlayerWarpManager mgr = plugin.getPlayerWarpManager();

        // /pwarps — open GUI
        if (label.equalsIgnoreCase("pwarps") || label.equalsIgnoreCase("playerwarps")) {
            plugin.getPlayerWarpGUI().openBrowse(player, 0);
            return true;
        }

        // /pwarp (no args) — open GUI
        if (args.length == 0) {
            plugin.getPlayerWarpGUI().openBrowse(player, 0);
            return true;
        }

        String sub = args[0].toLowerCase();

        // /pwarp set <name>
        if (sub.equals("set") || sub.equals("crear") || sub.equals("create")) {
            if (args.length < 2) {
                player.sendMessage(sc("§c§l✖ §cUso: /pwarp set <nombre>"));
                return true;
            }
            String name = args[1].toLowerCase().replaceAll("[^a-z0-9_-]", "");
            if (name.isEmpty()) {
                player.sendMessage(sc("§c§l✖ §cNombre inválido. Solo letras, números, guiones y guiones bajos."));
                return true;
            }
            if (name.length() > 16) {
                player.sendMessage(sc("§c§l✖ §cEl nombre no puede tener más de 16 caracteres."));
                return true;
            }
            if (mgr.warpExists(name)) {
                player.sendMessage(sc("§c§l✖ §cYa existe una warp con ese nombre."));
                return true;
            }
            if (!mgr.canCreateWarp(player)) {
                int max = mgr.getMaxWarps(player);
                player.sendMessage(sc("§c§l✖ §cYa tienes el máximo de warps (§f" + max + "§c). Elimina una para crear otra."));
                return true;
            }
            if (mgr.createWarp(player, name)) {
                player.sendMessage(sc("§b§l✦ §aWarp '§e" + name + "§a' creada. Usa §e/pwarps §apara gestionarla."));
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
                plugin.getPlayerWarpGUI().openEdit(player, name);
            } else {
                player.sendMessage(sc("§c§l✖ §cNo se pudo crear la warp."));
            }
            return true;
        }

        // /pwarp delete <name>
        if (sub.equals("delete") || sub.equals("eliminar") || sub.equals("remove") || sub.equals("del")) {
            if (args.length < 2) {
                player.sendMessage(sc("§c§l✖ §cUso: /pwarp delete <nombre>"));
                return true;
            }
            String name = args[1].toLowerCase();
            if (mgr.deleteWarp(player, name)) {
                player.sendMessage(sc("§c§l✖ §cWarp '§f" + name + "§c' eliminada."));
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 0.8f, 1.0f);
            } else {
                player.sendMessage(sc("§c§l✖ §cNo tienes una warp con ese nombre o no existe."));
            }
            return true;
        }

        // /pwarp admin — admin panel
        if (sub.equals("admin") && player.hasPermission("coreprotect.admin")) {
            plugin.getPlayerWarpGUI().openAdmin(player, 0);
            return true;
        }

        // /pwarp <name> — open detail panel
        String warpName = sub;
        PlayerWarpManager.PlayerWarp warp = mgr.getWarp(warpName);
        if (warp == null) {
            player.sendMessage(sc("§c§l✖ §cNo existe la warp '§f" + warpName + "§c'."));
            player.sendMessage(sc("§7Usa §e/pwarps §7para ver todas las warps disponibles."));
            return true;
        }
        plugin.getPlayerWarpGUI().openDetail(player, warp.name);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        PlayerWarpManager mgr = plugin.getPlayerWarpManager();
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("set");
            completions.add("delete");
            if (sender.hasPermission("coreprotect.admin")) completions.add("admin");
            // Also add warp names for quick tp
            for (PlayerWarpManager.PlayerWarp w : mgr.getAllWarps()) {
                completions.add(w.name);
            }
            return filter(completions, args[0]);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("delete") || sub.equals("eliminar") || sub.equals("remove") || sub.equals("del")) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    for (PlayerWarpManager.PlayerWarp w : mgr.getWarpsByOwner(player.getUniqueId())) {
                        completions.add(w.name);
                    }
                    if (player.hasPermission("coreprotect.admin")) {
                        for (PlayerWarpManager.PlayerWarp w : mgr.getAllWarps()) {
                            if (!completions.contains(w.name)) completions.add(w.name);
                        }
                    }
                }
                return filter(completions, args[1]);
            }
        }

        return completions;
    }

    private List<String> filter(List<String> list, String prefix) {
        return list.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }

    private static String sc(String s) { return SmallCaps.convert(s); }
}
