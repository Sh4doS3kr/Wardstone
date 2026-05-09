package com.moonlight.coreprotect.kits;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Listener que aplica penalización de durabilidad a items del Kit Demon
 * cuando son usados por jugadores que no son los dueños originales.
 */
public class DemonDurabilityPenaltyListener implements Listener {

    private final CoreProtectPlugin plugin;

    public DemonDurabilityPenaltyListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Verifica si un jugador es el dueño original de un item Demon.
     */
    private boolean isOwner(ItemStack item, Player player) {
        if (!KitDemon.isDemonPiece(item, plugin)) return true; // Si no es item Demon, no aplicar penalización

        String ownerUuid = item.getItemMeta().getPersistentDataContainer()
                .get(KitDemon.getDemonOwnerKey(plugin), org.bukkit.persistence.PersistentDataType.STRING);
        
        if (ownerUuid == null) return true; // Si no tiene owner, no aplicar penalización
        
        return ownerUuid.equals(player.getUniqueId().toString());
    }

    /**
     * Aplica penalización de durabilidad extra cuando un item Demon es dañado por no-dueño.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();
        Player player = event.getPlayer();

        if (KitDemon.isDemonPiece(item, plugin) && !isOwner(item, player)) {
            // Penalización: 3x más durabilidad perdida
            int originalDamage = event.getDamage();
            int penaltyDamage = originalDamage * 3; // 3x más daño
            
            // Asegurar que no exceda la durabilidad máxima
            short maxDurability = item.getType().getMaxDurability();
            short currentDurability = item.getDurability();
            short newDurability = (short) Math.min(maxDurability, currentDurability + penaltyDamage);
            
            item.setDurability(newDurability);
            
            // Efectos visuales y sonoros para indicar penalización
            if (Math.random() < 0.3) { // 30% de chance de efectos
                player.getWorld().spawnParticle(org.bukkit.Particle.SMOKE, 
                    player.getLocation().add(0, 1, 0), 5, 0.2, 0.2, 0.2, 0.05);
                player.playSound(player.getLocation(), 
                    org.bukkit.Sound.ENTITY_ITEM_BREAK, 0.3f, 0.8f);
            }
            
            // Mensaje de advertencia ocasional
            if (Math.random() < 0.1) { // 10% de chance
                player.sendMessage("§c§l⚠ §7Este item Demoníaco no te pertenece. §cSe desgasta 3x más rápido.");
            }
            
            // Romper el item si está muy dañado
            if (newDurability >= maxDurability * 0.9) { // 90% de durabilidad
                if (Math.random() < 0.4) { // 40% de chance de romperse
                    item.setAmount(0);
                    player.getWorld().spawnParticle(org.bukkit.Particle.FLAME, 
                        player.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.1);
                    player.playSound(player.getLocation(), 
                        org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 0.5f);
                    player.sendMessage("§c§l✖ §7El item Demoníaco ajeno se ha roto por sobrecarga.");
                }
            }
        }
    }

    /**
     * Penalización extra cuando se ataca con arma Demon ajena.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        
        Player player = (Player) event.getDamager();
        ItemStack weapon = player.getInventory().getItemInMainHand();
        
        if (KitDemon.isDemonPiece(weapon, plugin) && !isOwner(weapon, player)) {
            // Daño reducido para no-dueños (50% menos efectivo)
            event.setDamage(event.getDamage() * 0.5);
            
            // Penalización de durabilidad adicional
            if (Math.random() < 0.5) { // 50% de chance de daño extra
                short currentDurability = weapon.getDurability();
                short maxDurability = weapon.getType().getMaxDurability();
                short newDurability = (short) Math.min(maxDurability, currentDurability + 2);
                weapon.setDurability(newDurability);
                
                // Efecto visual
                player.getWorld().spawnParticle(org.bukkit.Particle.SMOKE, 
                    player.getLocation().add(0, 1, 0), 3, 0.1, 0.1, 0.1, 0.02);
            }
            
            // Mensaje de advertencia
            if (Math.random() < 0.05) { // 5% de chance
                player.sendMessage("§c§l⚠ §7El arma Demoníaca ajena es §c50% menos efectiva §7y se desgasta rápido.");
            }
        }
    }

    /**
     * Penalización cuando se mina con herramientas Demon ajenas.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        
        if (KitDemon.isDemonPiece(tool, plugin) && !isOwner(tool, player)) {
            // Velocidad de minería reducida (efecto negativo)
            // Esto se logra aplicando un efecto temporal de minería lenta
            if (Math.random() < 0.3) { // 30% de chance
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.SLOWNESS, 40, 0, false, false));
            }
            
            // Penalización de durabilidad extra
            if (Math.random() < 0.4) { // 40% de chance de daño extra
                short currentDurability = tool.getDurability();
                short maxDurability = tool.getType().getMaxDurability();
                short newDurability = (short) Math.min(maxDurability, currentDurability + 1);
                tool.setDurability(newDurability);
                
                // Efecto visual
                player.getWorld().spawnParticle(org.bukkit.Particle.SMOKE, 
                    event.getBlock().getLocation().add(0.5, 0.5, 0.5), 2, 0.1, 0.1, 0.1, 0.01);
            }
            
            // Mensaje de advertencia
            if (Math.random() < 0.05) { // 5% de chance
                player.sendMessage("§c§l⚠ §7La herramienta Demoníaca ajena es §cmenos eficiente §7y se desgasta rápido.");
            }
        }
    }

    /**
     * Previene que los items Demon ajenos sean reparados en yunque.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().getType().toString().contains("ANVIL")) return;
        
        ItemStack currentItem = event.getCurrentItem();
        if (currentItem == null || !KitDemon.isDemonPiece(currentItem, plugin)) return;
        
        Player player = (Player) event.getWhoClicked();
        if (!isOwner(currentItem, player)) {
            event.setCancelled(true);
            player.sendMessage("§c§l✖ §7No puedes reparar items Demoníacos ajenos en el yunque.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }
}
