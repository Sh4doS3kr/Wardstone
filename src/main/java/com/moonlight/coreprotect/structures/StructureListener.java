package com.moonlight.coreprotect.structures;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Listener para items de estructuras (esencias, llaves).
 * Maneja click derecho en items de loot de estructuras.
 */
public class StructureListener implements Listener {

    private final CoreProtectPlugin plugin;

    public StructureListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || !item.hasItemMeta()) return;

        StructureManager structMgr = plugin.getStructureManager();
        if (structMgr == null) return;

        // Handle essence items
        if (structMgr.handleEssenceItem(player, item)) {
            event.setCancelled(true);
            return;
        }

        // Handle key items
        if (structMgr.handleKeyItem(player, item)) {
            event.setCancelled(true);
        }
    }
}
