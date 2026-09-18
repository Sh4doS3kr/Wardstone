package com.moonlight.coreprotect.commands;

import com.moonlight.coreprotect.gui.PetsInfoGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Comando /petsinfo - Muestra un GUI con información sobre Cubees
 */
public class PetsInfoCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cEste comando solo puede ser usado por jugadores.");
            return true;
        }

        Player player = (Player) sender;
        PetsInfoGUI.open(player);
        return true;
    }
}
