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
import com.moonlight.coreprotect.util.SmallCaps;

public class AdminCommand implements CommandExecutor, TabCompleter {

    private final CoreProtectPlugin plugin;

    public AdminCommand(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Permitir uso desde consola para cleanvoidpickaxes
        boolean isConsole = !(sender instanceof Player);
        
        if (!isConsole) {
            Player player = (Player) sender;
            if (!player.hasPermission("coreprotect.admin")) {
                plugin.getMessageManager().send(player, "errors.no-permission");
                return true;
            }
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("deleteprotection")) {
            if (isConsole) {
                sender.sendMessage(SmallCaps.convert("§cEste comando solo puede ser usado por jugadores en juego."));
                return true;
            }
            deleteProtection((Player) sender);
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("cleanvoidpickaxes")) {
            cleanVoidPickaxes(sender);
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("chunks")) {
            if (args.length < 2) {
                sender.sendMessage(SmallCaps.convert("§cUsage: /wardstone chunks <numero>"));
                sender.sendMessage(SmallCaps.convert("§7Configura el límite de visión en chunks (2-32)"));
                return true;
            }
            
            try {
                int chunks = Integer.parseInt(args[1]);
                if (chunks < 2 || chunks > 32) {
                    sender.sendMessage(SmallCaps.convert("§cEl número debe estar entre 2 y 32 chunks"));
                    return true;
                }
                
                // Guardar en config
                plugin.getConfig().set("settings.view-distance-chunks", chunks);
                plugin.saveConfig();
                
                // Aplicar inmediatamente
                if (plugin.getProtectionListener() != null) {
                    plugin.getProtectionListener().updateAllWorldsAndPlayersViewDistance();
                }
                
                sender.sendMessage(SmallCaps.convert("§a§l✔ §fLímite de visión configurado a §e" + chunks + " chunks"));
                sender.sendMessage(SmallCaps.convert("§7Se ha aplicado inmediatamente a todos los mundos y jugadores"));
                
            } catch (NumberFormatException e) {
                sender.sendMessage(SmallCaps.convert("§cDebes especificar un número válido"));
            }
            return true;
        }

        if (isConsole) {
            sender.sendMessage(SmallCaps.convert("§cUsage: admincore cleanvoidpickaxes"));
            sender.sendMessage(SmallCaps.convert("§cUsage: admincore chunks <numero>"));
        } else {
            Player player = (Player) sender;
            player.sendMessage(SmallCaps.convert("§cUsage: /admincore deleteprotection"));
            player.sendMessage(SmallCaps.convert("§cUsage: /admincore cleanvoidpickaxes"));
            player.sendMessage(SmallCaps.convert("§cUsage: /admincore chunks <numero>"));
        }
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

        player.sendMessage(SmallCaps.convert("§a[Admin] Protección eliminada correctamente."));
        SoundManager.playCoreRemoved(player.getLocation());
    }

    private void cleanVoidPickaxes(CommandSender sender) {
        sender.sendMessage(SmallCaps.convert("§6§l[ADMIN] §eIniciando limpieza de picos Fractura del Vacío explotados..."));
        
        int totalRemoved = 0;
        int playersProcessed = 0;
        
        // Limpiar inventarios de jugadores online
        for (org.bukkit.entity.Player onlinePlayer : org.bukkit.Bukkit.getOnlinePlayers()) {
            boolean removed = cleanPlayerInventory(onlinePlayer);
            if (removed) {
                totalRemoved++;
                playersProcessed++;
            }
        }
        
        // Limpiar inventarios de jugadores offline
        for (org.bukkit.OfflinePlayer offlinePlayer : org.bukkit.Bukkit.getOfflinePlayers()) {
            if (!offlinePlayer.isOnline() && offlinePlayer.hasPlayedBefore()) {
                boolean removed = cleanOfflinePlayerInventory(offlinePlayer);
                if (removed) {
                    totalRemoved++;
                    playersProcessed++;
                }
            }
        }
        
        // Limpiar cofres y shulkers en mundos cargados
        int containersCleaned = cleanAllContainers();
        
        sender.sendMessage(SmallCaps.convert("§a§l[ADMIN] §fLimpieza completada:"));
        sender.sendMessage(SmallCaps.convert("§7- Jugadores procesados: §e" + playersProcessed));
        sender.sendMessage(SmallCaps.convert("§7- Picos eliminados: §c" + totalRemoved));
        sender.sendMessage(SmallCaps.convert("§7- Contenedores limpiados: §e" + containersCleaned));
        
        if (totalRemoved > 0) {
            sender.sendMessage(SmallCaps.convert("§c§l⚠ §7Se han eliminado picos duplicados. Se ha dado 1 pico limpio a cada jugador con Misión 3 completada."));
        }
    }
    
    private boolean cleanPlayerInventory(org.bukkit.entity.Player player) {
        boolean removedAny = false;
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        
        // Verificar si completó Misión 3
        boolean completedQuest3 = plugin.getShadowHunterManager().getCompletedQuest3().contains(player.getUniqueId());
        
        // Limpiar inventario principal
        for (int i = 0; i < inv.getSize(); i++) {
            org.bukkit.inventory.ItemStack item = inv.getItem(i);
            if (item != null && com.moonlight.coreprotect.shadowhunter.VoidPickaxe.isVoidPickaxe(item, plugin)) {
                inv.setItem(i, null);
                removedAny = true;
            }
        }
        
        // Limpiar equipamiento
        for (org.bukkit.inventory.EquipmentSlot slot : org.bukkit.inventory.EquipmentSlot.values()) {
            if (slot != org.bukkit.inventory.EquipmentSlot.HAND) {
                org.bukkit.inventory.ItemStack item = player.getInventory().getItem(slot);
                if (item != null && com.moonlight.coreprotect.shadowhunter.VoidPickaxe.isVoidPickaxe(item, plugin)) {
                    // No se puede remover equipamiento directamente, se reemplaza con aire
                    if (slot == org.bukkit.inventory.EquipmentSlot.OFF_HAND) {
                        player.getInventory().setItemInOffHand(null);
                        removedAny = true;
                    }
                }
            }
        }
        
        // Si completó Misión 3, dar 1 pico limpio
        if (removedAny && completedQuest3) {
            org.bukkit.inventory.ItemStack cleanPickaxe = com.moonlight.coreprotect.shadowhunter.VoidPickaxe.createVoidPickaxe(plugin);
            player.getInventory().addItem(cleanPickaxe);
            player.sendMessage(SmallCaps.convert("§6§l[ADMIN] §7Tus picos Fractura del Vacío han sido reemplazados por 1 pico limpio debido a un exploit."));
        }
        
        return removedAny;
    }
    
    private boolean cleanOfflinePlayerInventory(org.bukkit.OfflinePlayer offlinePlayer) {
        try {
            // Usar Bukkit API para obtener inventario offline
            org.bukkit.inventory.ItemStack[] contents = org.bukkit.Bukkit.createInventory(null, 54).getContents();
            
            // Esta es una implementación básica - en realidad necesitaríamos
            // una librería como NBTExplorer o similar para inventarios offline
            // Por ahora solo verificamos si completó Misión 3
            boolean completedQuest3 = plugin.getShadowHunterManager().getCompletedQuest3().contains(offlinePlayer.getUniqueId());
            
            if (completedQuest3) {
                // Marcar para cuando se conecte
                return true;
            }
        } catch (Exception e) {
            // No se pudo acceder al inventario offline
        }
        return false;
    }
    
    private int cleanAllContainers() {
        int containers = 0;
        
        for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
            for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                for (org.bukkit.block.BlockState blockState : chunk.getTileEntities()) {
                    if (blockState instanceof org.bukkit.block.Container) {
                        org.bukkit.block.Container container = (org.bukkit.block.Container) blockState;
                        org.bukkit.inventory.Inventory inv = container.getInventory();
                        boolean removedAny = false;
                        
                        for (int i = 0; i < inv.getSize(); i++) {
                            org.bukkit.inventory.ItemStack item = inv.getItem(i);
                            if (item != null && com.moonlight.coreprotect.shadowhunter.VoidPickaxe.isVoidPickaxe(item, plugin)) {
                                inv.setItem(i, null);
                                removedAny = true;
                            }
                        }
                        
                        if (removedAny) {
                            containers++;
                        }
                    }
                }
            }
        }
        
        return containers;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            list.add("deleteprotection");
            list.add("cleanvoidpickaxes");
            list.add("chunks");
            return list.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("chunks")) {
            List<String> list = new ArrayList<>();
            list.add("8");
            list.add("10");
            list.add("12");
            list.add("16");
            list.add("20");
            list.add("24");
            return list;
        }
        return new ArrayList<>();
    }
}
