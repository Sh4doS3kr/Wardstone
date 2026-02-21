package com.moonlight.coreprotect.finishers;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class FinisherListener implements Listener {

    private final CoreProtectPlugin plugin;
    private final FinisherEffects effects;

    public FinisherListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        this.effects = new FinisherEffects(plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) return;

        FinisherManager manager = plugin.getFinisherManager();
        FinisherType finisher = manager.getSelectedFinisher(killer.getUniqueId());
        if (finisher == null) return;

        Location deathLoc = victim.getLocation();
        effects.play(finisher, deathLoc, killer);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (event.getView().getTitle() == null) return;
        if (!event.getView().getTitle().equals(FinisherGUI.TITLE)) return;

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        int slot = event.getRawSlot();
        FinisherManager manager = plugin.getFinisherManager();

        // Deselect button
        if (slot == 40) {
            manager.deselectFinisher(player.getUniqueId());
            player.sendMessage(ChatColor.YELLOW + "Has quitado tu finisher activo.");
            com.moonlight.coreprotect.effects.SoundManager.playGUIClick(player.getLocation());
            new FinisherGUI(plugin).open(player);
            return;
        }

        // Map slots to finisher types
        int[] slots = {11, 13, 15, 20, 24};
        FinisherType[] types = FinisherType.values();
        FinisherType type = null;
        for (int i = 0; i < slots.length && i < types.length; i++) {
            if (slot == slots[i]) {
                type = types[i];
                break;
            }
        }
        if (type == null) return;

        if (manager.isSelected(player.getUniqueId(), type)) {
            // Deselect
            manager.deselectFinisher(player.getUniqueId());
            player.sendMessage(ChatColor.YELLOW + "Has desequipado " + type.getDisplayName() + ChatColor.YELLOW + ".");
            com.moonlight.coreprotect.effects.SoundManager.playGUIClick(player.getLocation());
            new FinisherGUI(plugin).open(player);
        } else if (manager.ownsFinisher(player.getUniqueId(), type)) {
            // Select
            manager.selectFinisher(player.getUniqueId(), type);
            player.sendMessage(ChatColor.GREEN + "Has equipado " + type.getDisplayName() + ChatColor.GREEN + "!");
            com.moonlight.coreprotect.effects.SoundManager.playUpgradePurchased(player.getLocation());
            new FinisherGUI(plugin).open(player);
        } else {
            // Purchase
            double price = manager.getPrice(type);
            if (plugin.getEconomy().getBalance(player) < price) {
                player.sendMessage(ChatColor.RED + "No tienes suficiente dinero. Necesitas " + ChatColor.GOLD + "$" + String.format("%,.0f", price) + ChatColor.RED + ".");
                return;
            }
            plugin.getEconomy().withdrawPlayer(player, price);
            manager.purchaseFinisher(player.getUniqueId(), type);
            manager.selectFinisher(player.getUniqueId(), type);
            player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "¡Has comprado " + type.getDisplayName() + ChatColor.GREEN + "" + ChatColor.BOLD + "!");
            player.sendMessage(ChatColor.GRAY + "Se ha equipado automáticamente.");
            com.moonlight.coreprotect.effects.SoundManager.playUpgradePurchased(player.getLocation());
            new FinisherGUI(plugin).open(player);
        }
    }
}
