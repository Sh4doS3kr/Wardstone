package com.moonlight.coreprotect.commands;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.core.ProtectedRegion;
import com.moonlight.coreprotect.effects.SoundManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AdminCommand implements CommandExecutor, TabCompleter {

    private final CoreProtectPlugin plugin;

    public AdminCommand(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessageManager().getMessage("errors.player-only"));
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("coreprotect.admin")) {
            plugin.getMessageManager().send(player, "errors.no-permission");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("deleteprotection")) {
            deleteProtection(player);
            return true;
        }

        player.sendMessage("§cUsage: /admincore deleteprotection");
        return true;
    }

    private void deleteProtection(Player player) {
        ProtectedRegion region = plugin.getProtectionManager().getRegionAt(player.getLocation());

        if (region == null) {
            plugin.getMessageManager().send(player, "protection.no-protection");
            return;
        }

        // Eliminar bloque
        if (region.getCoreLocation() != null) {
            region.getCoreLocation().getBlock().breakNaturally();
            // Drop core item? Maybe not for admin delete.
        }

        plugin.getProtectionManager().removeRegion(region.getId());
        plugin.getDataManager().saveData();

        player.sendMessage("§a[Admin] Protección eliminada correctamente.");
        SoundManager.playCoreRemoved(player.getLocation());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            list.add("deleteprotection");
            return list.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
