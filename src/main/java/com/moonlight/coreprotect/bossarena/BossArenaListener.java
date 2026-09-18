package com.moonlight.coreprotect.bossarena;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Listener de eventos para el sistema de Boss Arena.
 * - No romper bloques (construir sí, pero se limpia)
 * - No volar (fly/elytra) durante el combate
 * - No usar comandos durante el combate (/spawn, /home, /tpa, etc.)
 * - Gestión de daño al boss
 * - Respawn en la arena
 * - Tracking de bloques colocados por jugadores para reset
 */
public class BossArenaListener implements Listener {

    private final CoreProtectPlugin plugin;
    private final BossArenaManager manager;

    // Bloques colocados por jugadores (para limpiar después)
    private final Set<org.bukkit.Location> playerPlacedBlocks = new HashSet<>();

    public BossArenaListener(CoreProtectPlugin plugin, BossArenaManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    // ==================== PROTECCIÓN DE BLOQUES ====================

    /**
     * No se pueden romper bloques en la arena (excepto ops).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!manager.isInAnyArena(event.getBlock().getLocation())) return;

        // Permitir a ops romper cualquier bloque
        if (event.getPlayer().isOp()) {
            playerPlacedBlocks.remove(event.getBlock().getLocation());
            return;
        }

        // Solo permitir romper bloques colocados por jugadores
        if (playerPlacedBlocks.contains(event.getBlock().getLocation())) {
            playerPlacedBlocks.remove(event.getBlock().getLocation());
            return;
        }

        event.setCancelled(true);
        event.getPlayer().sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes romper bloques en la arena de boss."));
    }

    /**
     * PROHIBIDO construir o colocar bloques en la arena del boss.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!manager.isInAnyArena(event.getBlock().getLocation())) return;
        
        Player player = event.getPlayer();
        
        // Cancelar construcción de cualquier tipo
        event.setCancelled(true);
        
        // Mensaje específico para agua
        String blockName = event.getBlock().getType().name().toLowerCase();
        if (blockName.contains("water") || blockName.contains("bucket") || 
            event.getBlock().getType() == Material.WATER_BUCKET || 
            event.getBlock().getType() == Material.BUCKET) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes poner agua en la arena del boss."));
        } else {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes construir en la arena del boss."));
        }
        
        // Efecto sonoro
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
    }

    // ==================== BLOQUEAR PVP ====================

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPvP(EntityDamageByEntityEvent event) {
        // Solo nos interesa si la víctima es un jugador
        if (!(event.getEntity() instanceof Player)) return;

        // Verificar si estamos en un mundo de boss arena
        if (!manager.isInAnyArena(event.getEntity().getLocation())) return;

        // Detectar si el atacante es un jugador (directo o proyectil)
        Player attacker = null;
        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile) {
            org.bukkit.entity.Projectile proj = (org.bukkit.entity.Projectile) event.getDamager();
            if (proj.getShooter() instanceof Player) {
                attacker = (Player) proj.getShooter();
            }
        }

        if (attacker != null) {
            event.setCancelled(true);
            attacker.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes hacer PvP en la arena de boss."));
        }
    }

    // ==================== DAÑO AL BOSS ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBossDamage(EntityDamageByEntityEvent event) {
        Boss boss = manager.getCurrentBoss();
        if (boss == null || !boss.isActive()) return;

        Entity entity = event.getEntity();
        Entity damager = event.getDamager();

        // Evitar que el boss dañe a sus propios minions
        if (boss.isBossEntity(damager) && boss.isSummonedMob(entity)) {
            event.setCancelled(true);
            return;
        }
        // Evitar que los minions dañen al boss
        if (boss.isSummonedMob(damager) && boss.isBossEntity(entity)) {
            event.setCancelled(true);
            return;
        }
        // Evitar friendly fire entre minions
        if (boss.isSummonedMob(damager) && boss.isSummonedMob(entity)) {
            event.setCancelled(true);
            return;
        }

        if (!boss.isBossEntity(entity)) return;

        // Reducción de daño global del 30% (toma 70% del daño)
        event.setDamage(event.getDamage() * 0.7);
        boss.onDamage(event);
    }

    /**
     * Evitar que el boss (IronGolem) targeteé a sus propios minions por IA.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTarget(EntityTargetEvent event) {
        Boss boss = manager.getCurrentBoss();
        if (boss == null || !boss.isActive()) return;
        if (event.getTarget() == null) return;

        Entity attacker = event.getEntity();
        Entity target = event.getTarget();

        // Boss no puede targetear minions
        if (boss.isBossEntity(attacker) && boss.isSummonedMob(target)) {
            event.setCancelled(true);
            return;
        }
        // Minions no pueden targetear al boss
        if (boss.isSummonedMob(attacker) && boss.isBossEntity(target)) {
            event.setCancelled(true);
        }
    }

    /**
     * Evitar daño ambiental al boss (caída, ahogamiento, etc.)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossEnvironmentalDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) return;

        Boss boss = manager.getCurrentBoss();
        if (boss == null || !boss.isActive()) return;

        if (boss.isBossEntity(event.getEntity()) || boss.isSummonedMob(event.getEntity())) {
            EntityDamageEvent.DamageCause cause = event.getCause();
            if (cause == EntityDamageEvent.DamageCause.FALL ||
                cause == EntityDamageEvent.DamageCause.DROWNING ||
                cause == EntityDamageEvent.DamageCause.SUFFOCATION ||
                cause == EntityDamageEvent.DamageCause.FIRE ||
                cause == EntityDamageEvent.DamageCause.FIRE_TICK ||
                cause == EntityDamageEvent.DamageCause.LAVA) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Evitar que el boss dropee items normales al morir.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBossEntityDeath(EntityDeathEvent event) {
        Boss boss = manager.getCurrentBoss();
        if (boss == null) return;

        if (boss.isBossEntity(event.getEntity()) || boss.isSummonedMob(event.getEntity())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    // ==================== KEEPINVENTORY EN ARENA ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!manager.isInAnyArena(player.getLocation())) return;

        // Keepinventory: no perder items ni XP
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);

        String bossName = manager.getCurrentBoss() != null ? manager.getCurrentBoss().getBossName() : "el Boss";
        player.sendMessage(SmallCaps.convert("§c☠ §7Has muerto. §aInventario conservado."));
    }

    // ==================== RESPAWN EN ARENA ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!manager.isInAnyArena(player.getLocation())) return;

        if (manager.isBossActive() && manager.getCurrentArena() != null) {
            org.bukkit.Location spawn = manager.getCurrentArena().getPlayerSpawn();
            if (spawn != null) {
                event.setRespawnLocation(spawn);
            }
        }
    }

    // ==================== BOSS BAR AL ENTRAR ====================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        Boss boss = manager.getCurrentBoss();
        if (boss == null || !boss.isActive()) return;

        if (manager.isInAnyArena(player.getLocation())) {
            String color = boss.getBossType().getColor();
            player.sendMessage(SmallCaps.convert(color + "§l⚔ " + color + "¡Estás en la arena de " + boss.getBossType().getDisplayName() + color + "!"));
            player.sendMessage(SmallCaps.convert("§7¡El §e§lúltimo golpe §7se lleva la recompensa!"));
        }
    }

    // ==================== BLOQUEAR VUELO ====================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFly(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (player.isOp()) return;
        if (!manager.isBossActive()) return;
        if (!manager.isInAnyArena(player.getLocation())) return;

        event.setCancelled(true);
        player.setFlying(false);
        player.setAllowFlight(false);
        String bossName = manager.getCurrentBoss() != null ? manager.getCurrentBoss().getBossName() : "el Boss";
        player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes volar durante el combate con " + bossName + "."));
    }

    /**
     * Llamar periódicamente para quitar fly y elytra durante el combate.
     */
    public void tickAntifly() {
        if (!manager.isBossActive()) return;
        ArenaData arena = manager.getCurrentArena();
        if (arena == null || arena.getWorld() == null) return;

        for (Player p : arena.getWorld().getPlayers()) {
            if (p.isOp()) continue;

            // Quitar fly (pero permitir Elytra)
            if (p.isFlying() || p.getAllowFlight()) {
                if (p.getGameMode() != GameMode.CREATIVE) {
                    p.setFlying(false);
                    p.setAllowFlight(false);
                }
            }
        }
    }


    // ==================== BLOQUEAR COMANDOS ====================

    private static final List<String> BLOCKED_COMMANDS = Arrays.asList(
            "spawn", "home", "sethome", "tpa", "tpaccept", "tpahere", "tp",
            "warp", "back", "hub", "lobby", "server", "rtp", "wild",
            "essentials:spawn", "essentials:home", "essentials:tpa", "essentials:tp",
            "essentials:warp", "essentials:back"
    );

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.isOp()) return;
        if (!manager.isBossActive()) return;
        if (!manager.isInAnyArena(player.getLocation())) return;

        String message = event.getMessage().substring(1).trim();
        String baseCommand = message.split("\\s+")[0].toLowerCase();

        // Permitir /boss y /bossarena
        if (baseCommand.equals("boss") || baseCommand.equals("bossarena")) return;

        // Bloquear todos los comandos de teletransporte
        if (BLOCKED_COMMANDS.contains(baseCommand)) {
            event.setCancelled(true);
            String cmdBossName = manager.getCurrentBoss() != null ? manager.getCurrentBoss().getBossName() : "el Boss";
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes usar ese comando durante el combate con " + cmdBossName + "."));
            return;
        }
    }

    // ==================== LIMPIEZA DE BLOQUES ====================

    /**
     * Limpia todos los bloques colocados por jugadores en la arena.
     */
    public void cleanPlayerBlocks() {
        for (org.bukkit.Location loc : playerPlacedBlocks) {
            if (loc.getBlock() != null) {
                loc.getBlock().setType(Material.AIR);
            }
        }
        playerPlacedBlocks.clear();
    }
}
