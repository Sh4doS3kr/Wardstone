package com.moonlight.coreprotect.listeners;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sistema de cooldown para Manzanas Doradas Encantadas.
 * Cooldown = Duración del efecto de Resistencia (300s) + 10s = 310 segundos
 */
public class EnchantedGoldenAppleCooldown implements Listener {

    private final CoreProtectPlugin plugin;
    
    // Cooldown: 300 segundos (efecto de Resistencia) + 10 segundos = 310 segundos = 6200 ticks
    private static final long COOLDOWN_TICKS = 6200L;
    private static final long COOLDOWN_SECONDS = 310L;
    
    // Tracking de cooldowns por jugador
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public EnchantedGoldenAppleCooldown(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerConsumeEnchantedGoldenApple(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // Verificar si es una manzana dorada encantada
        if (item.getType() != Material.ENCHANTED_GOLDEN_APPLE) {
            return;
        }

        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        // Verificar si el jugador tiene cooldown activo
        if (cooldowns.containsKey(playerId)) {
            long lastUseTime = cooldowns.get(playerId);
            long timePassed = currentTime - lastUseTime;
            long cooldownMillis = COOLDOWN_SECONDS * 1000L;

            if (timePassed < cooldownMillis) {
                // Aún en cooldown - cancelar el consumo
                event.setCancelled(true);

                // Calcular tiempo restante
                long timeRemaining = (cooldownMillis - timePassed) / 1000L;
                int minutes = (int) (timeRemaining / 60);
                int seconds = (int) (timeRemaining % 60);

                // Notificar al jugador
                player.sendMessage("");
                player.sendMessage(SmallCaps.convert("§c§l⚠ COOLDOWN ACTIVO ⚠"));
                player.sendMessage(SmallCaps.convert("§7No puedes comer otra §6Manzana Dorada Encantada §7aún."));
                
                if (minutes > 0) {
                    player.sendMessage(SmallCaps.convert("§7Tiempo restante: §e" + minutes + "m " + seconds + "s"));
                } else {
                    player.sendMessage(SmallCaps.convert("§7Tiempo restante: §e" + seconds + "s"));
                }
                
                player.sendMessage("");

                // Sonido de error
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);

                return;
            }
        }

        // Permitir el consumo y registrar el tiempo
        cooldowns.put(playerId, currentTime);

        // Notificar al jugador que el cooldown ha comenzado
        player.sendMessage(SmallCaps.convert("§6§l✦ §eManzana Dorada Encantada consumida"));
        player.sendMessage(SmallCaps.convert("§7Cooldown: §e" + COOLDOWN_SECONDS + " segundos §7(5m 10s)"));
        
        // Sonido de confirmación
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 1.0f, 1.0f);
    }

    /**
     * Limpiar cooldowns de jugadores que se desconectan (opcional, para liberar memoria)
     */
    public void clearCooldown(UUID playerId) {
        cooldowns.remove(playerId);
    }

    /**
     * Obtener tiempo restante de cooldown en segundos
     */
    public long getRemainingCooldown(UUID playerId) {
        if (!cooldowns.containsKey(playerId)) {
            return 0;
        }

        long lastUseTime = cooldowns.get(playerId);
        long currentTime = System.currentTimeMillis();
        long timePassed = currentTime - lastUseTime;
        long cooldownMillis = COOLDOWN_SECONDS * 1000L;

        if (timePassed >= cooldownMillis) {
            return 0;
        }

        return (cooldownMillis - timePassed) / 1000L;
    }

    /**
     * Verificar si un jugador tiene cooldown activo
     */
    public boolean hasCooldown(UUID playerId) {
        return getRemainingCooldown(playerId) > 0;
    }
}
