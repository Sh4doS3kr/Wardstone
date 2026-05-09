package com.moonlight.coreprotect.shadowhunter;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Listener para las habilidades pasivas de "Lágrimas de Lyra".
 * Implementa Reflejo Etéreo, Lágrimas Sanadoras, Velo del Vacío y Consuelo Eterno.
 */
public class VoidLeggingsListener implements Listener {
    
    private final CoreProtectPlugin plugin;
    private final Map<UUID, BukkitTask> speedTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> regenTasks = new HashMap<>();
    private final Map<UUID, Long> reflectMsgCooldown = new HashMap<>();
    private final Map<UUID, Long> cleanseMsgCooldown = new HashMap<>();
    private final Map<UUID, Long> regenMsgCooldown = new HashMap<>();
    private static final long MSG_COOLDOWN_MS = 5000;
    // Cache: which players are wearing void leggings (refreshed every second via task)
    private final java.util.Set<UUID> wearingLeggings = java.util.concurrent.ConcurrentHashMap.newKeySet();
    
    public VoidLeggingsListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        // Periodic task to cache who is wearing void leggings + check rain/water healing
        // This replaces the expensive onPlayerMove check
        new BukkitRunnable() {
            @Override
            public void run() {
                wearingLeggings.clear();
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    if (VoidLeggings.isVoidLeggings(p.getInventory().getLeggings(), plugin)) {
                        wearingLeggings.add(p.getUniqueId());
                        checkHealingConditions(p);
                    } else {
                        // Stop regen if they took off leggings
                        if (regenTasks.containsKey(p.getUniqueId())) {
                            stopRegenEffect(p);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Every 1 second — not every move event
    }
    
    private boolean hasVoidLeggings(Player player) {
        return wearingLeggings.contains(player.getUniqueId());
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        
        Player player = (Player) event.getEntity();
        
        if (!hasVoidLeggings(player)) return;

        // Reflejo Etéreo: 15% de probabilidad de reducir daño a la mitad
        if (Math.random() < 0.15) { // 15% chance
            double originalDamage = event.getDamage();
            event.setDamage(originalDamage * 0.5); // Reduce damage to half
            
            // Visual effect
            player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.1);
            player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0,
                    new Particle.DustOptions(Color.fromRGB(100, 180, 220), 1.5f));
            player.playSound(player.getLocation(), Sound.ENTITY_ALLAY_AMBIENT_WITH_ITEM, 0.5f, 1.2f);
            
            long now = System.currentTimeMillis();
            if (now - reflectMsgCooldown.getOrDefault(player.getUniqueId(), 0L) >= MSG_COOLDOWN_MS) {
                reflectMsgCooldown.put(player.getUniqueId(), now);
                player.sendMessage(SmallCaps.convert("§b§l✦ §7Reflejo Etéreo activado §8- §7Daño reducido"));
            }
        }
        
        // Consuelo Eterno: Inmunidad a Slowness y Mining Fatigue
        if (event.getCause() == EntityDamageEvent.DamageCause.WITHER) {
            event.setCancelled(true);
            long now2 = System.currentTimeMillis();
            if (now2 - cleanseMsgCooldown.getOrDefault(player.getUniqueId(), 0L) >= MSG_COOLDOWN_MS) {
                cleanseMsgCooldown.put(player.getUniqueId(), now2);
                player.sendMessage(SmallCaps.convert("§b§l✦ §7Consuelo Eterno ha disipado §cWither§7."));
            }
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        
        Player player = event.getPlayer();
        if (!hasVoidLeggings(player)) return;
        
        // Velo del Vacío: Speed I mientras está agachado
        startSpeedEffect(player);
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only check when sneaking state changes (not every movement)
        if (event.getPlayer().isSneaking() == event.getPlayer().isSneaking()) return;
        
        Player player = event.getPlayer();
        if (!hasVoidLeggings(player)) return;
        
        if (player.isSneaking()) {
            // Velo del Vacío: Speed I mientras está agachado
            startSpeedEffect(player);
        } else {
            // Detener efecto al dejar de agacharse
            stopSpeedEffect(player);
        }
    }
    
    // Healing check moved to periodic task — NO MORE onPlayerMove
    private void checkHealingConditions(Player player) {
        UUID uid = player.getUniqueId();
        boolean shouldHeal = false;
        
        // Check if in rain
        if (player.getWorld().hasStorm() && player.getLocation().getBlock().getLightFromSky() == 15) {
            shouldHeal = true;
        }
        // Check if in water
        else if (player.getLocation().getBlock().getType() == Material.WATER ||
                 player.getLocation().add(0, 1, 0).getBlock().getType() == Material.WATER) {
            shouldHeal = true;
        }
        
        if (shouldHeal && !regenTasks.containsKey(uid)) {
            startRegenEffect(player);
        } else if (!shouldHeal && regenTasks.containsKey(uid)) {
            stopRegenEffect(player);
        }
    }
    
    private void startSpeedEffect(Player player) {
        UUID uid = player.getUniqueId();
        
        // Cancel existing task if any
        stopSpeedEffect(player);
        
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !player.isSneaking() || 
                    !VoidLeggings.isVoidLeggings(player.getInventory().getLeggings(), plugin)) {
                    stopSpeedEffect(player);
                    return;
                }
                
                // Apply Speed I effect
                if (!player.hasPotionEffect(PotionEffectType.SPEED)) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0, false, false, false));
                }
                
                // Visual effect
                if (Math.random() < 0.3) {
                    player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 0.5, 0), 1, 0.2, 0.2, 0.2, 0.01);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // Every 1 second
        
        speedTasks.put(uid, task);
    }
    
    private void stopSpeedEffect(Player player) {
        UUID uid = player.getUniqueId();
        BukkitTask task = speedTasks.remove(uid);
        if (task != null) {
            task.cancel();
        }
    }
    
    private void startRegenEffect(Player player) {
        UUID uid = player.getUniqueId();
        
        // Cancel existing task if any
        stopRegenEffect(player);
        
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || 
                    !VoidLeggings.isVoidLeggings(player.getInventory().getLeggings(), plugin)) {
                    stopRegenEffect(player);
                    return;
                }
                
                // Check still in rain/water
                boolean shouldContinue = false;
                if (player.getWorld().hasStorm() && player.getLocation().getBlock().getLightFromSky() == 15) {
                    shouldContinue = true;
                } else if (player.getLocation().getBlock().getType() == Material.WATER ||
                           player.getLocation().add(0, 1, 0).getBlock().getType() == Material.WATER) {
                    shouldContinue = true;
                }
                
                if (!shouldContinue) {
                    stopRegenEffect(player);
                    return;
                }
                
                // Apply Regeneration I effect
                if (!player.hasPotionEffect(PotionEffectType.REGENERATION)) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 0, false, false, false));
                }
                
                // Visual effect
                if (Math.random() < 0.4) { // 40% chance per tick for particles
                    player.getWorld().spawnParticle(Particle.DRIPPING_WATER, player.getLocation().add(0, 2, 0), 2, 0.3, 0.1, 0.3, 0);
                    player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0), 3, 0.2, 0.3, 0.2, 0,
                            new Particle.DustOptions(Color.fromRGB(100, 180, 220), 1.0f));
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // Every 1 second
        
        regenTasks.put(uid, task);
        
        long now = System.currentTimeMillis();
        if (now - regenMsgCooldown.getOrDefault(uid, 0L) >= MSG_COOLDOWN_MS) {
            regenMsgCooldown.put(uid, now);
            player.sendMessage(SmallCaps.convert("§b§l✦ §7Lágrimas Sanadoras activadas"));
        }
    }
    
    private void stopRegenEffect(Player player) {
        UUID uid = player.getUniqueId();
        BukkitTask task = regenTasks.remove(uid);
        if (task != null) {
            task.cancel();
        }
    }
    
    public void cleanup() {
        // Cancel all running tasks
        for (BukkitTask task : speedTasks.values()) {
            task.cancel();
        }
        for (BukkitTask task : regenTasks.values()) {
            task.cancel();
        }
        speedTasks.clear();
        regenTasks.clear();
    }
}
