package com.moonlight.coreprotect.shadowhunter;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Listener para la GUI del comando /errante.
 */
public class ErranteGUIListener implements Listener {

    private final CoreProtectPlugin plugin;

    public ErranteGUIListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle() == null)
            return;
        if (!event.getView().getTitle().equals(ErranteCommand.GUI_TITLE))
            return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player))
            return;
        Player player = (Player) event.getWhoClicked();

        int slot = event.getRawSlot();

        // GUI es solo para ver — no se pueden iniciar misiones desde aquí
        if (slot == 11 || slot == 13 || slot == 15) {
            player.sendMessage(SmallCaps.convert("§5§l✦ §7El Errante te visitará cuando lo considere. No puedes invocar misiones desde aquí."));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.5f, 0.8f);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTitle() == null)
            return;
        if (event.getView().getTitle().equals(ErranteCommand.GUI_TITLE)) {
            event.setCancelled(true);
        }
    }
}
