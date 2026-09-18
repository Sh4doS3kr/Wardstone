package com.moonlight.coreprotect.rewards;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public class PlayTimeGUIListener implements Listener {

    private final CoreProtectPlugin plugin;

    public PlayTimeGUIListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle() == null) return;
        if (!event.getView().getTitle().startsWith(PlayTimeGUI.GUI_TITLE)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        String title = event.getView().getTitle();
        int currentPage = 0;
        try {
            String pageStr = title.substring(title.lastIndexOf("(") + 1, title.lastIndexOf("/"));
            currentPage = Integer.parseInt(pageStr.trim()) - 1;
        } catch (Exception ignored) {}

        // Previous page
        if (slot == 45 && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.ARROW) {
            PlayTimeGUI.open(plugin, player, currentPage - 1);
            return;
        }

        // Next page
        if (slot == 53 && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.ARROW) {
            PlayTimeGUI.open(plugin, player, currentPage + 1);
            return;
        }

        // Claim all
        if (slot == 49 && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.EMERALD) {
            player.closeInventory();
            PlayTimeGUI.claimAll(plugin, player);
            return;
        }

        // Click on individual reward (SUNFLOWER = available)
        if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.SUNFLOWER) {
            // Determine which tier this is
            int row = slot / 9 - 1;
            int col = slot % 9 - 1;
            if (row < 0 || col < 0 || row > 3 || col > 6) return;
            int index = currentPage * 28 + row * 7 + col;
            if (index >= 0 && index < PlayTimeGUI.TIERS.length) {
                PlayTimeGUI.claimReward(plugin, player, index);
                PlayTimeGUI.open(plugin, player, currentPage);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTitle() != null && event.getView().getTitle().startsWith(PlayTimeGUI.GUI_TITLE)) {
            event.setCancelled(true);
        }
    }
}
