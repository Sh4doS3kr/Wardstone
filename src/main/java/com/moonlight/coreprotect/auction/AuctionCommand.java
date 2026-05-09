package com.moonlight.coreprotect.auction;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Comando /ah para abrir la Auction House.
 * Subcomandos: /ah, /ah sell, /ah mis, /ah search <query>
 */
public class AuctionCommand implements CommandExecutor, TabCompleter {

    private final CoreProtectPlugin plugin;
    private final AuctionManager manager;
    private final AuctionListener listener;

    public AuctionCommand(CoreProtectPlugin plugin, AuctionManager manager, AuctionListener listener) {
        this.plugin = plugin;
        this.manager = manager;
        this.listener = listener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(SmallCaps.convert("§cEste comando solo puede ser usado por jugadores."));
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            // Abrir GUI principal
            listener.openMainGUI(player, 1);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "sell":
            case "vender":
                AuctionSellGUI.open(player);
                break;

            case "mis":
            case "my":
            case "listings":
                AuctionMyListingsGUI.open(player, manager, AuctionMyListingsGUI.Tab.ACTIVOS, 1);
                break;

            case "expired":
            case "expirados":
                AuctionMyListingsGUI.open(player, manager, AuctionMyListingsGUI.Tab.EXPIRADOS, 1);
                break;

            case "search":
            case "buscar":
                if (args.length < 2) {
                    player.sendMessage(SmallCaps.convert("§cUso: /ah buscar <texto>"));
                    return true;
                }
                String query = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                AuctionGUI.open(player, manager, 1, AuctionManager.Category.TODOS, AuctionManager.SortType.RECIENTE, query);
                break;

            case "help":
            case "ayuda":
                sendHelp(player);
                break;

            default:
                player.sendMessage(SmallCaps.convert("§cSubcomando desconocido. Usa §e/ah ayuda §cpara ver los comandos."));
                break;
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§6§l✦ §e§lAuction House - Ayuda §6§l✦"));
        player.sendMessage(SmallCaps.convert("§8§m                                          "));
        player.sendMessage(SmallCaps.convert("§e/ah §7- Abrir la Auction House"));
        player.sendMessage(SmallCaps.convert("§e/ah vender §7- Abrir panel de venta"));
        player.sendMessage(SmallCaps.convert("§e/ah mis §7- Ver tus subastas activas"));
        player.sendMessage(SmallCaps.convert("§e/ah expirados §7- Ver items expirados"));
        player.sendMessage(SmallCaps.convert("§e/ah buscar <texto> §7- Buscar items"));
        player.sendMessage(SmallCaps.convert("§e/ah ayuda §7- Ver esta ayuda"));
        player.sendMessage(SmallCaps.convert("§8§m                                          "));
        player.sendMessage(SmallCaps.convert("§7Impuesto: §c" + (int)(AuctionItem.TAX_RATE * 100) + "% §7| Duración: §e48h §7| Max: §e" + AuctionItem.MAX_LISTINGS + " listings"));
        player.sendMessage("");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = Arrays.asList("vender", "mis", "expirados", "buscar", "ayuda");
            return subs.stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
