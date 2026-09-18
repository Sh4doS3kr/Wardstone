package com.moonlight.coreprotect.shadowhunter;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Listener para proteger el Jardín del Cielo y manejar eventos específicos
 */
public class SkyParkourListener implements Listener {
    
    private final CoreProtectPlugin plugin;
    private final Map<UUID, SkyParkour> activeParkours = new HashMap<>();
    
    public SkyParkourListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }
    
    public void registerParkour(Player player, SkyParkour parkour) {
        activeParkours.put(player.getUniqueId(), parkour);
    }
    
    public void unregisterParkour(Player player) {
        activeParkours.remove(player.getUniqueId());
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        SkyParkour parkour = activeParkours.get(player.getUniqueId());
        
        if (parkour != null && parkour.isActive()) {
            // Allow building only in LETTER phase
            if (parkour.getCurrentPhase() != SkyParkour.Phase.LETTER) {
                event.setCancelled(true);
                player.sendMessage(SmallCaps.convert("§c✦ §7No puedes construir aquí excepto en la fase inicial."));
                return;
            }
            
            // In LETTER phase, allow building but blocks will be cleaned up
            Location blockLoc = event.getBlock().getLocation();
            parkour.addPlacedBlock(blockLoc);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        SkyParkour parkour = activeParkours.get(player.getUniqueId());
        
        if (parkour != null && parkour.isActive()) {
            // Never allow breaking blocks in Sky Parkour
            event.setCancelled(true);
            player.sendMessage(SmallCaps.convert("§c✦ §7No puedes romper bloques en el Jardín del Cielo."));
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        SkyParkour parkour = activeParkours.get(player.getUniqueId());
        
        if (parkour != null && parkour.isActive()) {
            // Check if player fell off the parkour
            Location from = event.getFrom();
            Location to = event.getTo();
            
            // Only check Y coordinate change (falling)
            if (to.getY() < from.getY() && to.getY() < parkour.getSkyBase().getY() - 10) {
                // Player fell too far, respawn at beginning
                parkour.respawnAtPhase();
            }
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        
        Player player = (Player) event.getEntity();
        SkyParkour parkour = activeParkours.get(player.getUniqueId());
        
        if (parkour != null && parkour.isActive()) {
            // Cancel fall damage in Sky Parkour
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                event.setCancelled(true);
                // Instead of canceling, respawn at beginning
                parkour.respawnAtPhase();
            }
        }
    }
}
