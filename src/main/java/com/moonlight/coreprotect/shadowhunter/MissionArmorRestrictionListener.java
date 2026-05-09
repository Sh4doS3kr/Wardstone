package com.moonlight.coreprotect.shadowhunter;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Restricts players to only equip one mission armor piece at a time.
 * Mission armor pieces: VoidBlade, VoidPickaxe, VoidChestplate, VoidLeggings, VoidBoots, AbyssBlade
 */
public class MissionArmorRestrictionListener implements Listener {

    private final CoreProtectPlugin plugin;

    public MissionArmorRestrictionListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isMissionArmor(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        String displayName = item.getItemMeta().getDisplayName();
        if (displayName == null) return false;
        
        return displayName.contains("Lágrimas de Lyra") || // VoidLeggings
               displayName.contains("Pisada del Olvido") || // VoidBoots
               displayName.contains("Corazón del Abismo") || // VoidChestplate
               displayName.contains("Velo del Vacío") || // VoidBlade
               displayName.contains("Pico del Olvido") || // VoidPickaxe
               displayName.contains("Espada del Abismo"); // AbyssBlade
    }

    private boolean hasMissionArmorEquipped(Player player) {
        PlayerInventory inv = player.getInventory();
        
        // Check armor slots
        ItemStack[] armor = inv.getArmorContents();
        for (ItemStack piece : armor) {
            if (isMissionArmor(piece)) return true;
        }
        
        // Check main hand and off hand
        if (isMissionArmor(inv.getItemInMainHand())) return true;
        if (isMissionArmor(inv.getItemInOffHand())) return true;
        
        return false;
    }

    private int getMissionArmorCount(Player player) {
        int count = 0;
        PlayerInventory inv = player.getInventory();
        
        // Check armor slots
        ItemStack[] armor = inv.getArmorContents();
        for (ItemStack piece : armor) {
            if (isMissionArmor(piece)) count++;
        }
        
        // Check main hand and off hand
        if (isMissionArmor(inv.getItemInMainHand())) count++;
        if (isMissionArmor(inv.getItemInOffHand())) count++;
        
        return count;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        
        // Only check armor slots and hotbar
        if (event.getSlotType() != org.bukkit.event.inventory.InventoryType.SlotType.ARMOR &&
            event.getSlotType() != org.bukkit.event.inventory.InventoryType.SlotType.QUICKBAR &&
            event.getSlotType() != org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER) {
            return;
        }

        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();

        // Check if trying to equip mission armor
        if (isMissionArmor(cursor) || isMissionArmor(current)) {
            // Temporarily allow the click to check final state
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (getMissionArmorCount(player) > 1) {
                    // Remove excess mission armor and return to inventory
                    PlayerInventory inv = player.getInventory();
                    ItemStack[] armor = inv.getArmorContents();
                    
                    for (int i = 0; i < armor.length; i++) {
                        if (isMissionArmor(armor[i])) {
                            // Check if this is the second+ piece
                            boolean hasOther = false;
                            for (int j = 0; j < armor.length; j++) {
                                if (i != j && isMissionArmor(armor[j])) {
                                    hasOther = true;
                                    break;
                                }
                            }
                            if (hasOther || isMissionArmor(inv.getItemInMainHand()) || isMissionArmor(inv.getItemInOffHand())) {
                                // Remove this piece and add to inventory
                                ItemStack removed = armor[i];
                                armor[i] = new ItemStack(Material.AIR);
                                inv.setArmorContents(armor);
                                inv.addItem(removed);
                                player.sendMessage("§4§l✖ §cSolo puedes equipar UNA pieza de armadura de misión a la vez.");
                                break;
                            }
                        }
                    }
                    
                    // Check main hand
                    if (isMissionArmor(inv.getItemInMainHand())) {
                        boolean hasOther = false;
                        for (ItemStack piece : inv.getArmorContents()) {
                            if (isMissionArmor(piece)) {
                                hasOther = true;
                                break;
                            }
                        }
                        if (hasOther || isMissionArmor(inv.getItemInOffHand())) {
                            ItemStack removed = inv.getItemInMainHand();
                            inv.setItemInMainHand(new ItemStack(Material.AIR));
                            inv.addItem(removed);
                            player.sendMessage("§4§l✖ §cSolo puedes equipar UNA pieza de armadura de misión a la vez.");
                        }
                    }
                    
                    // Check off hand
                    if (isMissionArmor(inv.getItemInOffHand())) {
                        boolean hasOther = false;
                        for (ItemStack piece : inv.getArmorContents()) {
                            if (isMissionArmor(piece)) {
                                hasOther = true;
                                break;
                            }
                        }
                        if (hasOther || isMissionArmor(inv.getItemInMainHand())) {
                            ItemStack removed = inv.getItemInOffHand();
                            inv.setItemInOffHand(new ItemStack(Material.AIR));
                            inv.addItem(removed);
                            player.sendMessage("§4§l✖ §cSolo puedes equipar UNA pieza de armadura de misión a la vez.");
                        }
                    }
                }
            }, 1L);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        
        // Check if dragging mission armor
        if (isMissionArmor(event.getOldCursor())) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (getMissionArmorCount(player) > 1) {
                    player.sendMessage("§4§l✖ §cSolo puedes equipar UNA pieza de armadura de misión a la vez.");
                    // Force inventory refresh to remove excess
                    player.updateInventory();
                }
            }, 1L);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
        
        if (isMissionArmor(newItem) && hasMissionArmorEquipped(player)) {
            event.setCancelled(true);
            player.sendMessage("§4§l✖ §cSolo puedes equipar UNA pieza de armadura de misión a la vez.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (isMissionArmor(item) && hasMissionArmorEquipped(player)) {
            event.setCancelled(true);
            player.sendMessage("§4§l✖ §cSolo puedes equipar UNA pieza de armadura de misión a la vez.");
        }
    }
}
