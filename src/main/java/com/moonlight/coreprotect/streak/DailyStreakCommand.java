package com.moonlight.coreprotect.streak;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DailyStreakCommand implements CommandExecutor {

    private final CoreProtectPlugin plugin;

    public DailyStreakCommand(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cEste comando solo puede ser usado por jugadores.");
            return true;
        }

        Player player = (Player) sender;
        plugin.getDailyStreakManager().openGUI(player, 0);
        return true;
    }
}
