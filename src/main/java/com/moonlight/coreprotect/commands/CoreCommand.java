package com.moonlight.coreprotect.commands;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.core.ProtectedRegion;
import com.moonlight.coreprotect.effects.SoundManager;
import com.moonlight.coreprotect.gui.ShopGUI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class CoreCommand implements CommandExecutor, TabCompleter {

    private final CoreProtectPlugin plugin;

    public CoreCommand(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessageManager().getMessage("errors.player-only"));
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("coreprotect.use")) {
            plugin.getMessageManager().send(player, "errors.no-permission");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("tienda") || args[0].equalsIgnoreCase("shop")) {
            openShop(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "info":
                showInfo(player);
                break;
            case "remove":
            case "eliminar":
                removeProtection(player);
                break;
            case "add":
            case "agregar":
                if (args.length < 2) {
                    plugin.getMessageManager().send(player, "commands.usage");
                    return true;
                }
                addMember(player, args[1]);
                break;
            case "kick":
            case "expulsar":
                if (args.length < 2) {
                    plugin.getMessageManager().send(player, "commands.usage");
                    return true;
                }
                removeMember(player, args[1]);
                break;
            case "home":
            case "casa":
                goHome(player);
                break;
            case "sethome":
            case "setcasa":
                setHome(player);
                break;
            case "tp":
            case "teleport":
                teleportToCores(player, args);
                break;
            case "help":
            case "ayuda":
                plugin.getMessageManager().sendList(player, "commands.help");
                break;
            default:
                plugin.getMessageManager().send(player, "commands.usage");
                break;
        }

        return true;
    }

    private void openShop(Player player) {
        new ShopGUI(plugin).open(player, 1);
    }

    private void showInfo(Player player) {
        List<ProtectedRegion> regions = plugin.getProtectionManager().getRegionsByOwner(player.getUniqueId());

        plugin.getMessageManager().sendRaw(player, "info.title");

        if (regions.isEmpty()) {
            plugin.getMessageManager().sendRaw(player, "info.no-protections");
        } else {
            for (ProtectedRegion region : regions) {
                plugin.getMessageManager().sendRaw(player, "info.entry",
                        "{world}", region.getWorldName(),
                        "{x}", String.valueOf(region.getCoreX()),
                        "{z}", String.valueOf(region.getCoreZ()),
                        "{level}", String.valueOf(region.getLevel()),
                        "{size}", String.valueOf(region.getSize()));
            }
        }

        plugin.getMessageManager().sendRaw(player, "info.footer");
    }

    private void removeProtection(Player player) {
        ProtectedRegion region = plugin.getProtectionManager().getRegionAt(player.getLocation());

        if (region == null) {
            plugin.getMessageManager().send(player, "protection.no-protection");
            return;
        }

        if (!region.getOwner().equals(player.getUniqueId()) && !player.hasPermission("coreprotect.admin")) {
            plugin.getMessageManager().send(player, "protection.not-owner");
            return;
        }

        // Eliminar el bloque del nucleo
        if (region.getCoreLocation() != null) {
            region.getCoreLocation().getBlock().breakNaturally();
        }

        plugin.getProtectionManager().removeRegion(region.getId());
        plugin.getDataManager().saveData();
        plugin.getMessageManager().send(player, "protection.removed");
        SoundManager.playCoreRemoved(player.getLocation());
    }

    private void addMember(Player player, String targetName) {
        ProtectedRegion region = plugin.getProtectionManager().getRegionAt(player.getLocation());

        if (region == null) {
            plugin.getMessageManager().send(player, "protection.no-protection");
            return;
        }

        if (!region.getOwner().equals(player.getUniqueId()) && !player.hasPermission("coreprotect.admin")) {
            plugin.getMessageManager().send(player, "protection.not-owner");
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (target.getUniqueId() == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            plugin.getMessageManager().send(player, "members.player-not-found");
            return;
        }

        if (region.isMember(target.getUniqueId())) {
            plugin.getMessageManager().send(player, "members.already-member");
            return;
        }

        region.addMember(target.getUniqueId());
        plugin.getDataManager().saveData();
        plugin.getMessageManager().send(player, "members.added", "{player}", targetName);
        SoundManager.playMemberAdded(player.getLocation());
    }

    private void removeMember(Player player, String targetName) {
        ProtectedRegion region = plugin.getProtectionManager().getRegionAt(player.getLocation());

        if (region == null) {
            plugin.getMessageManager().send(player, "protection.no-protection");
            return;
        }

        if (!region.getOwner().equals(player.getUniqueId()) && !player.hasPermission("coreprotect.admin")) {
            plugin.getMessageManager().send(player, "protection.not-owner");
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (!region.isMember(target.getUniqueId())) {
            plugin.getMessageManager().send(player, "members.not-member");
            return;
        }

        region.removeMember(target.getUniqueId());
        plugin.getDataManager().saveData();
        plugin.getMessageManager().send(player, "members.removed", "{player}", targetName);
        SoundManager.playMemberRemoved(player.getLocation());
    }

    private void goHome(Player player) {
        java.util.UUID homeRegionId = plugin.getDataManager().getPlayerHome(player.getUniqueId());

        if (homeRegionId == null) {
            plugin.getMessageManager().send(player, "home.no-home");
            return;
        }

        ProtectedRegion region = plugin.getProtectionManager().getRegion(homeRegionId);
        if (region == null) {
            plugin.getDataManager().removePlayerHome(player.getUniqueId());
            plugin.getDataManager().saveData();
            plugin.getMessageManager().send(player, "home.no-home");
            return;
        }

        Location coreLoc = region.getCoreLocation();
        if (coreLoc == null || coreLoc.getWorld() == null) {
            plugin.getMessageManager().send(player, "home.no-home");
            return;
        }

        // Teleport 1 block above the core
        Location tp = coreLoc.clone().add(0.5, 1, 0.5);
        player.teleport(tp);
        plugin.getMessageManager().send(player, "home.teleported");
        SoundManager.playCorePlaced(tp);
    }

    private void setHome(Player player) {
        ProtectedRegion region = plugin.getProtectionManager().getRegionAt(player.getLocation());

        if (region == null) {
            plugin.getMessageManager().send(player, "home.not-in-region");
            return;
        }

        if (!region.getOwner().equals(player.getUniqueId()) && !region.isMember(player.getUniqueId())) {
            plugin.getMessageManager().send(player, "home.not-member");
            return;
        }

        plugin.getDataManager().setPlayerHome(player.getUniqueId(), region.getId());
        plugin.getDataManager().saveData();

        String ownerName = Bukkit.getOfflinePlayer(region.getOwner()).getName();
        plugin.getMessageManager().send(player, "home.set",
                "{owner}", ownerName != null ? ownerName : "???");
        SoundManager.playGUIClick(player.getLocation());
        plugin.getAchievementListener().onSetHome(player);
    }

    private void teleportToCores(Player player, String[] args) {
        List<ProtectedRegion> regions = plugin.getProtectionManager().getRegionsByOwner(player.getUniqueId());
        List<ProtectedRegion> tpRegions = new ArrayList<>();
        for (ProtectedRegion r : regions) {
            if (r.isCoreTeleport()) tpRegions.add(r);
        }

        if (tpRegions.isEmpty()) {
            plugin.getMessageManager().send(player, "upgrades.no-teleport");
            return;
        }

        if (args.length < 2) {
            // List cores
            plugin.getMessageManager().sendRaw(player, "info.title");
            for (int i = 0; i < tpRegions.size(); i++) {
                ProtectedRegion r = tpRegions.get(i);
                player.sendMessage("\u00a78[\u00a7b" + (i + 1) + "\u00a78] \u00a7fNv." + r.getLevel()
                        + " \u00a77(" + r.getCoreX() + ", " + r.getCoreZ() + ")");
            }
            player.sendMessage("\u00a77Usa \u00a7f/cores tp <n\u00famero> \u00a77para teletransportarte.");
            return;
        }

        try {
            int index = Integer.parseInt(args[1]) - 1;
            if (index < 0 || index >= tpRegions.size()) {
                plugin.getMessageManager().send(player, "errors.invalid-level");
                return;
            }
            ProtectedRegion target = tpRegions.get(index);
            Location tp = target.getCoreLocation().clone().add(0.5, 1, 0.5);
            player.teleport(tp);
            plugin.getMessageManager().send(player, "home.teleported");
            SoundManager.playCorePlaced(tp);
            plugin.getAchievementListener().onUseTp(player);
        } catch (NumberFormatException e) {
            plugin.getMessageManager().send(player, "commands.usage");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            List<String> completions = Arrays.asList("tienda", "info", "remove", "add", "kick", "home", "sethome", "tp", "help");
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("kick"))) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
