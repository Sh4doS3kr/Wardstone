package com.moonlight.coreprotect.kits;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Listener para proteger las armaduras del Kit Demon.
 * Previene crafteo y combinación, pero permite que se rompan con Unbreaking 4.
 */
public class KitDemonProtectionListener implements Listener {

    private final CoreProtectPlugin plugin;

    public KitDemonProtectionListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    // DISABLED: Kit Demon armor can now break with Unbreaking 4
    // @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    // public void onItemBreak(PlayerItemBreakEvent event) {
    //     ItemStack brokenItem = event.getBrokenItem();
    //     
    //     // Verificar si es una pieza del Kit Demon
    //     if (KitDemon.isDemonPiece(brokenItem, plugin)) {
    //         Player player = event.getPlayer();
    //         ItemMeta meta = brokenItem.getItemMeta();
    //         String itemName = meta != null ? meta.getDisplayName() : "Item Demoníaco";
    //         
    //         // Restaurar durabilidad al máximo inmediatamente
    //         brokenItem.setDurability((short) 0);
    //         
    //         // Mensaje de advertencia
    //         player.sendMessage(SmallCaps.convert("§4§l⚠ §c¡Protección Demoníaca! §7Tu " + itemName + " §7ha sido protegida de romperse."));
    //         
    //         // Efectos visuales y sonoros
    //         player.getWorld().spawnParticle(org.bukkit.Particle.DAMAGE_INDICATOR, player.getLocation(), 5, 0.5, 1, 0.5, 0.1);
    //         player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 0.5f, 1.0f);
    //     }
    // }

    // DISABLED: Allow combining Demon Kit items freely
    // @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    // public void onInventoryClick(InventoryClickEvent event) {
    //     // Prevenir que se puedan combinar items Demoníacos con otros
    //     if (event.getAction() == InventoryAction.SWAP_WITH_CURSOR || 
    //         event.getAction() == InventoryAction.PLACE_ALL || 
    //         event.getAction() == InventoryAction.PLACE_ONE ||
    //         event.getAction() == InventoryAction.PLACE_SOME) {
    //         
    //         ItemStack cursor = event.getCursor();
    //         ItemStack current = event.getCurrentItem();
    //         
    //         // Si intentan combinar un item Demoníaco con otro
    //         if ((cursor != null && KitDemon.isDemonPiece(cursor, plugin) && current != null && current.getAmount() > 1) ||
    //             (current != null && KitDemon.isDemonPiece(current, plugin) && cursor != null && cursor.getAmount() > 0)) {
    //             
    //             event.setCancelled(true);
    //             
    //             if (event.getWhoClicked() instanceof Player) {
    //                 Player player = (Player) event.getWhoClicked();
    //                 player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes combinar items del Kit Demon."));
    //                 player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
    //             }
    //         }
    //     }
    //     
    //     // Prevenir que se puedan mover items Demoníacos a ciertos slots peligrosos
    //     if (event.getSlotType() == InventoryType.SlotType.CRAFTING || 
    //         event.getSlotType() == InventoryType.SlotType.RESULT) {
    //         
    //         ItemStack item = event.getCurrentItem();
    //         if (item != null && KitDemon.isDemonPiece(item, plugin)) {
    //             event.setCancelled(true);
    //             
    //             if (event.getWhoClicked() instanceof Player) {
    //                 Player player = (Player) event.getWhoClicked();
    //                 player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes poner items del Kit Demon en el área de crafteo."));
    //                 player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
    //             }
    //         }
    //     }
    // }

    // DISABLED: Kit Demon armor can now break with Unbreaking 4
    // @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    // public void onEntityDamage(EntityDamageEvent event) {
    //     if (!(event.getEntity() instanceof Player)) return;
    //     
    //     Player player = (Player) event.getEntity();
    //     
    //     // Proteger armadura Demoníaca de perder durabilidad por daño
    //     // Esto se ejecuta después de que el daño se aplica para proteger la durabilidad
    //     new BukkitRunnable() {
    //         @Override
    //         public void run() {
    //             if (player.isOnline()) {
    //                 ItemStack[] armor = player.getInventory().getArmorContents();
    //                 for (ItemStack piece : armor) {
    //                     if (piece != null && KitDemon.isDemonPiece(piece, plugin)) {
    //                         // Restaurar durabilidad si se ha dañado
    //                         if (piece.getDurability() > 0) {
    //                             piece.setDurability((short) 0);
    //                         }
    //                     }
    //                 }
    //             }
    //         }
    //     }.runTaskLater(plugin, 1L); // Ejecutar en el siguiente tick para proteger después del daño
    // }
}
