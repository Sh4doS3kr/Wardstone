package com.moonlight.coreprotect.essence;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class EssenceListener implements Listener {

    private final CoreProtectPlugin plugin;
    private final EssenceShopGUI shopGUI;

    public EssenceListener(CoreProtectPlugin plugin, EssenceShopGUI shopGUI) {
        this.plugin = plugin;
        this.shopGUI = shopGUI;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getEssenceManager().onFirstJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        shopGUI.handleClick(event);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        String title = event.getView().getTitle();
        if (title.equals(EssenceShopGUI.GUI_TITLE) ||
            title.equals(EssenceShopGUI.CONFIRM_TITLE) ||
            title.startsWith(EssenceShopGUI.PREVIEW_PREFIX)) {
            event.setCancelled(true);
        }
    }
}
