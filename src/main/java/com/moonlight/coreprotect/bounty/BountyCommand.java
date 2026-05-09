package com.moonlight.coreprotect.bounty;

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
 * Comando /bounty para abrir el panel de Bounties.
 */
public class BountyCommand implements CommandExecutor, TabCompleter {

    private final CoreProtectPlugin plugin;
    private final BountyManager manager;
    private final BountyListener listener;

    public BountyCommand(CoreProtectPlugin plugin, BountyManager manager, BountyListener listener) {
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
            listener.openMainGUI(player, 1);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "mis":
            case "my":
                BountyMyGUI.open(player, manager, BountyMyGUI.Tab.PUESTAS, 1);
                break;

            case "poner":
            case "set":
            case "place":
                BountySetGUI.openSelectPlayer(player, 1);
                break;

            case "top":
            case "ranking":
                sendTop(player);
                break;

            case "help":
            case "ayuda":
                sendHelp(player);
                break;

            default:
                player.sendMessage(SmallCaps.convert("§cSubcomando desconocido. Usa §e/bounty ayuda §cpara ver los comandos."));
                break;
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§c§l☠ §e§lBounty System - Ayuda §c§l☠"));
        player.sendMessage(SmallCaps.convert("§8§m                                          "));
        player.sendMessage(SmallCaps.convert("§e/bounty §7- Abrir el Bounty Board"));
        player.sendMessage(SmallCaps.convert("§e/bounty poner §7- Poner una bounty"));
        player.sendMessage(SmallCaps.convert("§e/bounty mis §7- Ver tus bounties"));
        player.sendMessage(SmallCaps.convert("§e/bounty top §7- Ver top cazadores"));
        player.sendMessage(SmallCaps.convert("§e/bounty ayuda §7- Ver esta ayuda"));
        player.sendMessage(SmallCaps.convert("§8§m                                          "));
        player.sendMessage(SmallCaps.convert("§7Duración: §a§lPermanente §7| Cancelar: §c50% devuelto"));
        player.sendMessage("");
    }

    private void sendTop(Player player) {
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§c§l☠ §6§lTop Cazadores de Bounties §c§l☠"));
        player.sendMessage(SmallCaps.convert("§8§m                                          "));

        List<java.util.Map.Entry<String, Double>> topHunters = manager.getTopHunters(10);
        if (topHunters.isEmpty()) {
            player.sendMessage(SmallCaps.convert("§7  Aún no hay cazadores..."));
        } else {
            String[] medals = {"§6§l① ", "§f§l② ", "§c§l③ ", "§7④ ", "§7⑤ ", "§7⑥ ", "§7⑦ ", "§7⑧ ", "§7⑨ ", "§7⑩ "};
            for (int i = 0; i < topHunters.size(); i++) {
                java.util.Map.Entry<String, Double> e = topHunters.get(i);
                player.sendMessage(SmallCaps.convert(medals[i] + "§f" + e.getKey() + " §7- §a$" + String.format("%,.0f", e.getValue())));
            }
        }

        player.sendMessage(SmallCaps.convert("§8§m                                          "));

        int myClaimed = manager.getTotalClaimedBy(player.getUniqueId());
        double myEarned = manager.getTotalEarnedBy(player.getUniqueId());
        player.sendMessage(SmallCaps.convert("§7Tu ranking: §e" + myClaimed + " bounties cobradas §7(§a$" + String.format("%,.0f", myEarned) + "§7)"));
        player.sendMessage("");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = Arrays.asList("poner", "mis", "top", "ayuda");
            return subs.stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
