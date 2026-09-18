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
import com.moonlight.coreprotect.util.SmallCaps;

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
            // Permitir ejecucion desde consola para el sistema de Discord
            if (args.length > 0) {
                Player target = Bukkit.getPlayer(args[0]);
                if (target != null) {
                    giveReward(target);
                    return true;
                }
            }
            sender.sendMessage(SmallCaps.convert(ChatColor.RED + "Solo los jugadores pueden usar este comando."));
            return true;
        }

        Player player = (Player) sender;

        if (usedPlayers.contains(player.getUniqueId())) {
            player.sendMessage(SmallCaps.convert(ChatColor.RED + "Ya has reclamado tu recompensa. Solo se puede usar una vez."));
            return true;
        }

        giveReward(player);
        return true;
    }
    
    private void giveReward(Player player) {
        if (usedPlayers.contains(player.getUniqueId())) {
            return;
        }
        
        usedPlayers.add(player.getUniqueId());
        saveUsed();

        String name = player.getName();
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "excellentcrates key give " + name + " legendary 2");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "excellentcrates key give " + name + " special 5");

        player.sendMessage(SmallCaps.convert(ChatColor.GREEN + "" + ChatColor.BOLD + "!Recompensa reclamada!"));
        player.sendMessage(SmallCaps.convert(ChatColor.GRAY + "Has recibido " + ChatColor.GOLD + "2 llaves Legendary" + ChatColor.GRAY + " y " + ChatColor.AQUA + "5 llaves Special" + ChatColor.GRAY + "."));

        // Broadcast to all players
        String broadcast = "\n" + ChatColor.GOLD + "  \u2605 " + ChatColor.YELLOW + "" + ChatColor.BOLD + player.getName()
                + ChatColor.GREEN + " ha reclamado su recompensa especial!"
                + "\n" + ChatColor.GRAY + "  \u2517 " + ChatColor.GOLD + "2x Legendary" + ChatColor.GRAY + " + " + ChatColor.AQUA + "5x Especial"
                + "\n" + ChatColor.DARK_GRAY + "  Usa " + ChatColor.YELLOW + "/gaspi" + ChatColor.DARK_GRAY + " para reclamar la tuya!\n";
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(broadcast);
        }
    }
}
