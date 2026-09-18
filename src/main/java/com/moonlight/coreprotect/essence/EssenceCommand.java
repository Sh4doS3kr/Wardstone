package com.moonlight.coreprotect.essence;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.moonlight.coreprotect.util.SmallCaps;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * /esencias - Abre la tienda de esencias (GUI)
 * /esencias add <jugador> <cantidad> - Añadir esencias (OP/consola)
 * /esencias remove <jugador> <cantidad> - Quitar esencias (OP/consola)
 */
public class EssenceCommand implements CommandExecutor, TabCompleter {

    private static String sc(String s) { return SmallCaps.convert(s); }
    private final CoreProtectPlugin plugin;

    public EssenceCommand(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        EssenceManager manager = plugin.getEssenceManager();

        // /esencias add <jugador> <cantidad>
        if (args.length >= 3 && args[0].equalsIgnoreCase("add")) {
            if (!sender.hasPermission("coreprotect.admin") && !(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
                sender.sendMessage(sc("§c§l✖ §cNo tienes permiso."));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(sc("§c§l✖ §cJugador no encontrado: " + args[1]));
                return true;
            }
            int amount;
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(sc("§c§l✖ §cCantidad inválida."));
                return true;
            }
            manager.addEssences(target.getUniqueId(), amount);
            manager.saveData();
            sender.sendMessage(sc("§d§l✦ §a+" + amount + " Esencias §7a §f" + target.getName() + "§7. Total: §d" + manager.getEssences(target.getUniqueId())));
            target.sendMessage(sc("§d§l✦ §a+" + amount + " Esencias §7recibidas. Total: §d" + manager.getEssences(target.getUniqueId())));
            return true;
        }

        // /esencias remove <jugador> <cantidad>
        if (args.length >= 3 && args[0].equalsIgnoreCase("remove")) {
            if (!sender.hasPermission("coreprotect.admin") && !(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
                sender.sendMessage(sc("§c§l✖ §cNo tienes permiso."));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(sc("§c§l✖ §cJugador no encontrado: " + args[1]));
                return true;
            }
            int amount;
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(sc("§c§l✖ §cCantidad inválida."));
                return true;
            }
            manager.removeEssences(target.getUniqueId(), amount);
            manager.saveData();
            sender.sendMessage(sc("§d§l✦ §c-" + amount + " Esencias §7de §f" + target.getName() + "§7. Total: §d" + manager.getEssences(target.getUniqueId())));
            return true;
        }

        // Commands below require player
        if (!(sender instanceof Player)) {
            sender.sendMessage(sc("§cUso: /esencias add <jugador> <cantidad>"));
            return true;
        }

        Player player = (Player) sender;

        // /esencias (default) - Open shop GUI
        if (args.length == 0) {
            new EssenceShopGUI(plugin).open(player);
            return true;
        }

        // /esencias ayuda
        player.sendMessage("");
        player.sendMessage(sc("§d§l✦ §5§lESENCIAS §d§l✦"));
        player.sendMessage(sc("§7Saldo: §d" + manager.getEssences(player.getUniqueId()) + " Esencias"));
        player.sendMessage("");
        player.sendMessage(sc("§e/esencias §7— Abrir tienda de esencias"));
        player.sendMessage(sc("§7Puedes convertir dinero a esencias desde la tienda."));
        player.sendMessage("");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("coreprotect.admin")) {
                completions.add("add");
                completions.add("remove");
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove"))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                completions.add(p.getName());
            }
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove"))) {
            completions.addAll(Arrays.asList("1", "5", "10", "50"));
        }
        return completions;
    }
}
