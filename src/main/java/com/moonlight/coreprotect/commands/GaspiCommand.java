package com.moonlight.coreprotect.commands;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class GaspiCommand implements CommandExecutor {

    private final CoreProtectPlugin plugin;
    private final File usedFile;
    private final Set<UUID> usedPlayers = new HashSet<>();

    public GaspiCommand(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        this.usedFile = new File(plugin.getDataFolder(), "gaspi_used.yml");
        loadUsed();
    }

    private void loadUsed() {
        if (!usedFile.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(usedFile);
        List<String> list = cfg.getStringList("used");
        for (String s : list) {
            try { usedPlayers.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
        }
    }

    private void saveUsed() {
        FileConfiguration cfg = new YamlConfiguration();
        cfg.set("used", usedPlayers.stream().map(UUID::toString).collect(java.util.stream.Collectors.toList()));
        try { cfg.save(usedFile); } catch (IOException e) {
            plugin.getLogger().severe("Error guardando gaspi_used.yml: " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Solo los jugadores pueden usar este comando.");
            return true;
        }

        Player player = (Player) sender;

        if (usedPlayers.contains(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Ya has reclamado tu recompensa. Solo se puede usar una vez.");
            return true;
        }

        usedPlayers.add(player.getUniqueId());
        saveUsed();

        String name = player.getName();
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "excellentcrates key give " + name + " legendary 2");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "excellentcrates key give " + name + " especial 5");

        player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "¡Recompensa reclamada!");
        player.sendMessage(ChatColor.GRAY + "Has recibido " + ChatColor.GOLD + "2 llaves Legendary" + ChatColor.GRAY + " y " + ChatColor.AQUA + "5 llaves Especial" + ChatColor.GRAY + ".");

        return true;
    }
}
