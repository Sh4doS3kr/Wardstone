package com.moonlight.coreprotect.koth;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Bloqueador TOTAL de habilidades en KOTH.
 * Desactiva TODAS las habilidades: Errante, premium, pasivas, activas, comandos, etc.
 */
public class KothAbilityBlocker implements Listener {

    private final CoreProtectPlugin plugin;
    private final KothManager kothManager;
    private final Set<UUID> playersInKoth = new HashSet<>();

    public KothAbilityBlocker(CoreProtectPlugin plugin, KothManager kothManager) {
        this.plugin = plugin;
        this.kothManager = kothManager;
        
        // Task para limpiar efectos y habilidades constantemente
        new BukkitRunnable() {
            @Override
            public void run() {
                tickAbilityBlocker();
            }
        }.runTaskTimer(plugin, 20L, 10L); // Cada 0.5 segundos
    }

    /**
     * Verifica si un jugador está en el mundo KOTH.
     */
    private boolean isInKothZone(Player player) {
        return player.getWorld().getName().equalsIgnoreCase("koth");
    }

    /**
     * Verifica si el modo actual es VALE_TODO (habilidades permitidas).
     */
    private boolean isValeTodo() {
        return kothManager.isActive() && kothManager.getCurrentMode() == KothMode.VALE_TODO;
    }

    /**
     * Task principal que elimina TODAS las habilidades en KOTH.
     */
    private void tickAbilityBlocker() {
        // En modo VALE_TODO no bloquear nada
        if (isValeTodo()) return;

        Set<UUID> currentKothPlayers = new HashSet<>();
        
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (isInKothZone(player) && player.getGameMode() == GameMode.SURVIVAL) {
                UUID uuid = player.getUniqueId();
                currentKothPlayers.add(uuid);
                
                // Si es nuevo jugador en KOTH, aplicar restricciones
                if (!playersInKoth.contains(uuid)) {
                    onEnterKoth(player);
                    playersInKoth.add(uuid);
                }
                
                // Eliminar TODOS los efectos de poción (excepto los básicos)
                stripAllEffects(player);
                
                // Desactivar fly
                disableFlight(player);
                
                // Eliminar buffs de armaduras especiales
                stripArmorBuffs(player);
                
            } else {
                // Si salió del KOTH, restaurar estado normal
                UUID uuid = player.getUniqueId();
                if (playersInKoth.contains(uuid)) {
                    onExitKoth(player);
                    playersInKoth.remove(uuid);
                }
            }
        }
    }

    /**
     * Se ejecuta cuando un jugador entra al mundo KOTH.
     */
    private void onEnterKoth(Player player) {
        player.sendMessage("§c§l⚔ §7Todas las habilidades han sido desactivadas en el mundo KOTH.");
        player.sendMessage("§7§oModo competitivo puro activado en todo el mundo.");
        
        // Eliminar todos los efectos inmediatamente
        stripAllEffects(player);
        disableFlight(player);
    }

    /**
     * Se ejecuta cuando un jugador sale del mundo KOTH.
     */
    private void onExitKoth(Player player) {
        player.sendMessage("§a§l⚔ §7Has salido del mundo KOTH. Habilidades restauradas.");
    }

    /**
     * Elimina TODOS los efectos de poción excepto los básicos.
     */
    private void stripAllEffects(Player player) {
        for (PotionEffect effect : player.getActivePotionEffects()) {
            // Permitir solo efectos básicos y de visión
            if (effect.getType() != PotionEffectType.NIGHT_VISION &&
                effect.getType() != PotionEffectType.SATURATION &&
                effect.getType() != PotionEffectType.HEALTH_BOOST) {
                player.removePotionEffect(effect.getType());
            }
        }
    }

    /**
     * Desactiva el vuelo completamente.
     */
    private void disableFlight(Player player) {
        if (player.getAllowFlight()) {
            player.setAllowFlight(false);
            player.setFlying(false);
            player.sendMessage("§c§l✖ §7Vuelo desactivado en el mundo KOTH.");
        }
    }

    /**
     * Elimina buffs de armaduras especiales (Errante, Demon, etc).
     */
    private void stripArmorBuffs(Player player) {
        // Eliminar buffs de armaduras especiales
        PotionEffectType[] armorEffects = {
            PotionEffectType.RESISTANCE,
            PotionEffectType.STRENGTH,
            PotionEffectType.SPEED,
            PotionEffectType.REGENERATION,
            PotionEffectType.HASTE,
            PotionEffectType.ABSORPTION
        };
        
        for (PotionEffectType effect : armorEffects) {
            if (player.hasPotionEffect(effect)) {
                player.removePotionEffect(effect);
            }
        }
    }

    // ==================== EVENT BLOCKERS ====================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (isValeTodo()) return;
        if (!(event.getEntity() instanceof Player) && !(event.getDamager() instanceof Player)) return;
        
        Player player = null;
        if (event.getEntity() instanceof Player) {
            player = (Player) event.getEntity();
        } else if (event.getDamager() instanceof Player) {
            player = (Player) event.getDamager();
        }
        
        if (player != null && isInKothZone(player)) {
            // Reducir daño a valores normales (sin buffs)
            if (event.getDamage() > 10.0) {
                event.setDamage(10.0);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (isValeTodo()) return;
        if (!(event.getEntity() instanceof Player)) return;
        
        Player player = (Player) event.getEntity();
        if (isInKothZone(player)) {
            // Bloquear aplicación de nuevos efectos (excepto visión)
            if (event.getAction() == EntityPotionEffectEvent.Action.ADDED &&
                event.getNewEffect() != null &&
                event.getNewEffect().getType() != PotionEffectType.NIGHT_VISION) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (isInKothZone(player)) {
            String command = event.getMessage().toLowerCase();
            
            // Bloquear comandos de habilidades y fly
            String[] blockedCommands = {
                "/fly", "/flight", "/god", "/heal", "/feed", "/speed",
                "/kit", "/knight", "/warper", "/tpa", "/tpahere",
                "/home", "/spawn", "/warp", "/back"
            };
            
            for (String blocked : blockedCommands) {
                if (command.startsWith(blocked)) {
                    event.setCancelled(true);
                    player.sendMessage("§c§l✖ §7Comandos de habilidades bloqueados en el mundo KOTH.");
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (isValeTodo()) return;
        Player player = event.getPlayer();
        if (isInKothZone(player)) {
            // Bloquear uso de items con habilidades especiales
            if (event.getItem() != null && event.getItem().hasItemMeta()) {
                String name = event.getItem().getItemMeta().getDisplayName();
                if (name != null) {
                    // Detectar items de Errante, Demon, etc
                    if (name.contains("Errante") || name.contains("Demon") || 
                        name.contains("Luna") || name.contains("Nova") || 
                        name.contains("Eclipse") || name.contains("Moonlord") ||
                        name.contains("Martillo del Cero") || name.contains("Vacío")) {
                        event.setCancelled(true);
                        player.sendMessage("§c§l✖ §7Items especiales bloqueados en el mundo KOTH.");
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (isInKothZone(player)) {
            // Permitir teletransportes forzados por nuestro propio plugin (ej: /koth salir o /wardstone kickallkoth)
            if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN || event.getCause() == PlayerTeleportEvent.TeleportCause.COMMAND) {
                return;
            }
            
            // No permitir teleportación desde el mundo KOTH por otros medios (perlas, etc)
            if (!event.getTo().getWorld().getName().equals("koth")) {
                event.setCancelled(true);
                player.sendMessage("§c§l✖ §7No puedes teletransportarte desde el mundo KOTH. Usa /koth salir.");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        // Solo verificar cambio de bloque (no cada tick)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        
        Player player = event.getPlayer();
        boolean wasInKoth = playersInKoth.contains(player.getUniqueId());
        boolean isInKoth = isInKothZone(player);
        
        // Detectar entrada/salida del mundo KOTH
        if (!wasInKoth && isInKoth) {
            onEnterKoth(player);
        } else if (wasInKoth && !isInKoth) {
            onExitKoth(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (isInKothZone(player)) {
            event.setCancelled(true);
            player.sendMessage("§c§l✖ §7No puedes colocar bloques en el mundo KOTH.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (isInKothZone(player)) {
            event.setCancelled(true);
            player.sendMessage("§c§l✖ §7No puedes romper bloques en el mundo KOTH.");
        }
    }
}
