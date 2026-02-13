package com.moonlight.coreprotect.commands;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.UUID;

public class LinkValidateCommand implements CommandExecutor {

    private final CoreProtectPlugin plugin;

    public LinkValidateCommand(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("ERROR:No code provided");
            return true;
        }

        String code = args[0].trim();

        if (code.length() != 6) {
            sender.sendMessage("ERROR:Invalid code format");
            return true;
        }

        UUID playerUuid = plugin.getRPGManager().validateLinkCode(code);

        if (playerUuid == null) {
            sender.sendMessage("ERROR:Code not found or expired");
            return true;
        }

        String playerName = Bukkit.getOfflinePlayer(playerUuid).getName();
        if (playerName == null) playerName = "Unknown";

        // Consume the code so it can't be reused
        plugin.getRPGManager().consumeLinkCode(code);

        sender.sendMessage("OK:" + playerName);
        return true;
    }
}
