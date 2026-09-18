package com.moonlight.coreprotect.commands;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * /artistdagu - Da materiales de construccion basicos (no OP).
 * Solo se puede usar una vez por jugador.
 */
public class ArtistDaguCommand implements CommandExecutor {

    private final CoreProtectPlugin plugin;
    private final File usedFile;
    private final Set<UUID> usedPlayers = new HashSet<>();

    public ArtistDaguCommand(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        this.usedFile = new File(plugin.getDataFolder(), "artistdagu_used.yml");
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
            plugin.getLogger().severe("Error guardando artistdagu_used.yml: " + e.getMessage());
        }
    }

    private boolean hasInventorySpace(Player player, int slots) {
        int emptySlots = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR) {
                emptySlots++;
                if (emptySlots >= slots) return true;
            }
        }
        return false;
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
            player.sendMessage(SmallCaps.convert(ChatColor.RED + "Ya has reclamado tu kit de ArtistDagu. Solo se puede usar una vez."));
            return true;
        }

        giveReward(player);
        return true;
    }
    
    private void giveReward(Player player) {
        if (usedPlayers.contains(player.getUniqueId())) {
            return;
        }
        
        if (!hasInventorySpace(player, 23)) {
            player.sendMessage(SmallCaps.convert("\u00a7c\u00a7l\u2716 \u00a7cTu inventario esta lleno. Libera espacio para recibir el kit."));
            return;
        }

        usedPlayers.add(player.getUniqueId());
        saveUsed();

        // Kit ArtistDagu completo: herramientas + bloques de construccion
        player.getInventory().addItem(
            // Herramientas
            new ItemStack(Material.DIAMOND_PICKAXE),
            new ItemStack(Material.DIAMOND_AXE),
            new ItemStack(Material.DIAMOND_SHOVEL),
            new ItemStack(Material.BOW),
            new ItemStack(Material.ARROW, 64),
            // Moss blocks
            new ItemStack(Material.MOSS_BLOCK, 64),
            new ItemStack(Material.MOSS_BLOCK, 64),
            new ItemStack(Material.MOSS_BLOCK, 64),
            new ItemStack(Material.MOSS_BLOCK, 64),
            // Stone variants
            new ItemStack(Material.STONE, 64),
            new ItemStack(Material.COBBLESTONE, 64),
            new ItemStack(Material.ANDESITE, 64),
            new ItemStack(Material.DIORITE, 64),
            new ItemStack(Material.GRANITE, 64),
            new ItemStack(Material.COARSE_DIRT, 64),
            new ItemStack(Material.DEEPSLATE, 64),
            new ItemStack(Material.COBBLED_DEEPSLATE, 64),
            // More building blocks
            new ItemStack(Material.SMOOTH_STONE, 64),
            new ItemStack(Material.STONE_BRICKS, 64),
            new ItemStack(Material.POLISHED_ANDESITE, 64),
            new ItemStack(Material.POLISHED_DIORITE, 64),
            new ItemStack(Material.POLISHED_GRANITE, 64)
        );

        player.sendMessage("");
        player.sendMessage(SmallCaps.convert(ChatColor.GREEN + "" + ChatColor.BOLD + "!Kit de construccion de ArtistDagu reclamado!"));
        player.sendMessage(SmallCaps.convert(ChatColor.GRAY + "Has recibido herramientas y bloques de construccion."));
        player.sendMessage("");

        // Broadcast
        String broadcast = "\n" + ChatColor.GOLD + "  \u2694 " + ChatColor.YELLOW + "" + ChatColor.BOLD + player.getName()
                + ChatColor.GREEN + " ha reclamado su kit de ArtistDagu!"
                + "\n" + ChatColor.GRAY + "  \u2517 " + ChatColor.WHITE + "Materiales de construccion basicos"
                + "\n" + ChatColor.DARK_GRAY + "  Usa " + ChatColor.YELLOW + "/artistdagu" + ChatColor.DARK_GRAY + " para reclamar el tuyo!\n";
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(broadcast);
        }
    }
}
