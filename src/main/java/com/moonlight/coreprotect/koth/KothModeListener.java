package com.moonlight.coreprotect.koth;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.entity.Player;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Listener para manejar efectos específicos de los modos de KOTH.
 */
public class KothModeListener implements Listener {
    
    private final CoreProtectPlugin plugin;
    private final KothManager kothManager;
    
    public KothModeListener(CoreProtectPlugin plugin, KothManager kothManager) {
        this.plugin = plugin;
        this.kothManager = kothManager;
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        String fromWorld = event.getFrom().getName();
        String toWorld = player.getWorld().getName();
        
        // Si entra al mundo KOTH
        if (toWorld.equals("koth") && !fromWorld.equals("koth")) {
            handleKothEntry(player);
        }
        // Si sale del mundo KOTH
        else if (!toWorld.equals("koth") && fromWorld.equals("koth")) {
            handleKothExit(player);
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.isCancelled()) return;
        
        Player player = event.getPlayer();
        String fromWorld = event.getFrom().getWorld().getName();
        String toWorld = event.getTo().getWorld().getName();
        
        // Si entra al mundo KOTH por teleportación
        if (toWorld.equals("koth") && !fromWorld.equals("koth")) {
            handleKothEntry(player);
        }
        // Si sale del mundo KOTH por teleportación
        else if (!toWorld.equals("koth") && fromWorld.equals("koth")) {
            handleKothExit(player);
        }
    }
    
    /**
     * Maneja cuando un jugador entra al mundo KOTH.
     */
    private void handleKothEntry(Player player) {
        if (!kothManager.isActive()) return;
        
        KothMode currentMode = kothManager.getCurrentMode();
        
        switch (currentMode) {
            case NOCTURNO:
                // Aplicar ceguera para visión limitada
                if (!player.hasPotionEffect(PotionEffectType.BLINDNESS)) {
                    player.addPotionEffect(new PotionEffect(
                        PotionEffectType.BLINDNESS, 999999, 0, false, false));
                    player.sendMessage(SmallCaps.convert("§8§l⚔ §fVisión limitada activada en modo nocturno."));
                }
                
                // Dar una antorcha para ayudar un poco
                if (player.getInventory().contains(org.bukkit.Material.TORCH)) {
                    player.sendMessage(SmallCaps.convert("§8§l⚔ §7Usa antorchas para mejorar tu visibilidad."));
                }
                break;
                
            case COOPERATIVO:
                player.sendMessage(SmallCaps.convert("§a§l⚔ §fModo cooperativo: ¡Trabaja juntos para derrotar a los NPCs!"));
                break;
                
            case CLASICO:
                player.sendMessage(SmallCaps.convert("§6§l⚔ §fModo clásico: ¡Captura la zona contra otros jugadores!"));
                player.sendMessage(SmallCaps.convert("§7§l⚠ §7Elytras y mazo están §cprohibidos §7en este modo."));
                break;
                
            case VALE_TODO:
                player.sendMessage(SmallCaps.convert("§c§l⚔ §f¡Modo Vale Todo! §c¡Todo vale! §fElytras, mazo y más."));
                break;
        }
    }
    
    /**
     * Maneja cuando un jugador sale del mundo KOTH.
     */
    private void handleKothExit(Player player) {
        // Remover efectos de ceguera si salió del modo nocturno
        if (player.hasPotionEffect(PotionEffectType.BLINDNESS)) {
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            player.sendMessage(SmallCaps.convert("§f§l✨ §fVisión restaurada al salir del KOTH nocturno."));
        }
    }
    
    /**
     * Previene el consumo de leche en KOTH Nocturno.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        if (!kothManager.isActive()) return;
        if (kothManager.getCurrentMode() != KothMode.NOCTURNO) return;
        
        Player player = event.getPlayer();
        if (!player.getWorld().getName().equals("koth")) return;
        
        // Prevenir consumo de leche y leche de cubo
        if (event.getItem().getType() == Material.MILK_BUCKET) {
            event.setCancelled(true);
            player.sendMessage(SmallCaps.convert("§8§l⚔ §c§l✖ §7No puedes usar leche en el KOTH Nocturno. §8La oscuridad es parte del desafío."));
            
            // Efecto de sonido para indicar que no se puede usar
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }
}
