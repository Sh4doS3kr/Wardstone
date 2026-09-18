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

public class ZatreCommand implements CommandExecutor {

    private final CoreProtectPlugin plugin;
    private final File usedFile;
    private final Set<UUID> usedPlayers = new HashSet<>();

    public ZatreCommand(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        this.usedFile = new File(plugin.getDataFolder(), "zatre_used.yml");
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
            plugin.getLogger().severe("Error guardando zatre_used.yml: " + e.getMessage());
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

    private void giveDiamondKit(Player player) {
        // Verificar espacio en inventario
        if (!hasInventorySpace(player, 8)) {
            player.sendMessage(SmallCaps.convert(ChatColor.RED + "Necesitas al menos 8 espacios vacios en tu inventario para recibir el kit."));
            return;
        }
        
        // Kit de diamante completo
        ItemStack helmet = new ItemStack(Material.DIAMOND_HELMET);
        ItemStack chestplate = new ItemStack(Material.DIAMOND_CHESTPLATE);
        ItemStack leggings = new ItemStack(Material.DIAMOND_LEGGINGS);
        ItemStack boots = new ItemStack(Material.DIAMOND_BOOTS);
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        ItemStack pickaxe = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemStack axe = new ItemStack(Material.DIAMOND_AXE);
        ItemStack shovel = new ItemStack(Material.DIAMOND_SHOVEL);
        
        // Check if inventory has space (9 items needed)
        int emptySlots = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) {
                emptySlots++;
            }
        }
        if (emptySlots < 9) {
            player.sendMessage(SmallCaps.convert("\u00a7c\u00a7l\u2716 \u00a7cTu inventario esta lleno. Libera espacio para recibir el kit."));
            return;
        }
        
        // Dar items al jugador
        player.getInventory().addItem(helmet);
        player.getInventory().addItem(chestplate);
        player.getInventory().addItem(leggings);
        player.getInventory().addItem(boots);
        player.getInventory().addItem(sword);
        player.getInventory().addItem(pickaxe);
        player.getInventory().addItem(axe);
        player.getInventory().addItem(shovel);
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
            player.sendMessage(SmallCaps.convert(ChatColor.RED + "Ya has reclamado tu kit de Zatre. Solo se puede usar una vez."));
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
        
        // Dar kit de diamante
        giveDiamondKit(player);
        
        // Dar key especial de ExcellentCrates
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "excellentcrates key give " + name + " special 1");

        player.sendMessage(SmallCaps.convert(ChatColor.GREEN + "" + ChatColor.BOLD + "!Kit de Zatre reclamado!"));
        player.sendMessage(SmallCaps.convert(ChatColor.GRAY + "Has recibido " + ChatColor.AQUA + "kit de diamante completo" + ChatColor.GRAY + " y " + ChatColor.GOLD + "1 llave Special" + ChatColor.GRAY + "."));

        // Broadcast to all players
        String broadcast = "\n" + ChatColor.GOLD + "  \u2694 " + ChatColor.YELLOW + "" + ChatColor.BOLD + player.getName()
                + ChatColor.GREEN + " ha reclamado su kit de Zatre!"
                + "\n" + ChatColor.GRAY + "  \u2517 " + ChatColor.AQUA + "Kit Diamante Completo" + ChatColor.GRAY + " + " + ChatColor.GOLD + "1x Especial"
                + "\n" + ChatColor.DARK_GRAY + "  Usa " + ChatColor.YELLOW + "/zatre" + ChatColor.DARK_GRAY + " para reclamar el tuyo!\n";
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(broadcast);
        }
    }
}
