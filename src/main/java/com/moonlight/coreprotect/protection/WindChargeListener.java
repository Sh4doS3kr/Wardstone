package com.moonlight.coreprotect.protection;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Listener para bloquear el uso de Wind Charges (cargas de viento) en zonas protegidas
 */
public class WindChargeListener implements Listener {
    
    private final CoreProtectPlugin plugin;
    
    public WindChargeListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWindChargeUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        // Verificar si es una carga de viento
        if (item != null && item.getType() == Material.WIND_CHARGE) {
            // Permitir si está en combate PvP
            if (plugin.getCombatTagManager().isInCombat(player.getUniqueId())) {
                return;
            }
            
            // Bloquear en zona interna del spawn (cristales rojos)
            if (plugin.getProtectionManager().isSpawnCore(player.getLocation())) {
                org.bukkit.World.Environment env = player.getWorld().getEnvironment();
                if (env != org.bukkit.World.Environment.NETHER && env != org.bukkit.World.Environment.THE_END) {
                    // Ignorar si por alguna razón es un mundo de boss arena
                    if (!player.getWorld().getName().toLowerCase().contains("bossarena")) {
                        event.setCancelled(true);
                        player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes usar cargas de viento en la zona protegida del spawn."));
                        return;
                    }
                }
            }
            
            // Bloquear en regiones protegidas de otros jugadores
            com.moonlight.coreprotect.core.ProtectedRegion region = 
                plugin.getProtectionManager().getRegionAt(player.getLocation());
            if (region != null && !region.canAccess(player.getUniqueId())) {
                event.setCancelled(true);
                player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes usar cargas de viento en la zona protegida de otro jugador."));
                return;
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        
        // Verificar si es una carga de viento
        if (projectile instanceof WindCharge) {
            Player player = null;
            if (projectile.getShooter() instanceof Player) {
                player = (Player) projectile.getShooter();
            }
            
            if (player != null) {
                // Permitir si está en combate PvP
                if (plugin.getCombatTagManager().isInCombat(player.getUniqueId())) {
                    return;
                }
                
                // Bloquear en zona interna del spawn (cristales rojos)
                if (plugin.getProtectionManager().isSpawnCore(player.getLocation())) {
                    org.bukkit.World.Environment env = player.getWorld().getEnvironment();
                    if (env != org.bukkit.World.Environment.NETHER && env != org.bukkit.World.Environment.THE_END) {
                        if (!player.getWorld().getName().toLowerCase().contains("bossarena")) {
                            event.setCancelled(true);
                            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes usar cargas de viento en la zona protegida del spawn."));
                            return;
                        }
                    }
                }
                
                // Bloquear en regiones protegidas de otros jugadores
                com.moonlight.coreprotect.core.ProtectedRegion region = 
                    plugin.getProtectionManager().getRegionAt(player.getLocation());
                if (region != null && !region.canAccess(player.getUniqueId())) {
                    event.setCancelled(true);
                    player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes usar cargas de viento en la zona protegida de otro jugador."));
                    return;
                }
            }
        }
    }
}
