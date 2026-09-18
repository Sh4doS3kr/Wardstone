package com.moonlight.coreprotect.vault;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Comando /pv [número] — abre un Private Vault.
 */
public class PrivateVaultCommand implements CommandExecutor, TabCompleter {

    private final CoreProtectPlugin plugin;

    public PrivateVaultCommand(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(SmallCaps.convert("§cEste comando solo puede ser usado por jugadores."));
            return true;
        }

        Player player = (Player) sender;
        PrivateVaultManager vaultManager = plugin.getPrivateVaultManager();

        if (args.length == 0) {
            vaultManager.openVaultSelector(player);
            return true;
        }

        int vaultNumber;
        try {
            vaultNumber = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cUsa: §f/pv [número]"));
            return true;
        }

        vaultManager.openVault(player, vaultNumber);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1 && sender instanceof Player) {
            Player player = (Player) sender;
            int max = plugin.getPrivateVaultManager().getMaxVaults(player);
            for (int i = 1; i <= max; i++) {
                completions.add(String.valueOf(i));
            }
        }
        return completions;
    }
}
