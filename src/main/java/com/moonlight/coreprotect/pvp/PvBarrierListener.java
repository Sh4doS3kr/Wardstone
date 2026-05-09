package com.moonlight.coreprotect.pvp;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;

public class PvBarrierListener implements Listener {
    private final JavaPlugin plugin;
    private final Map<Player, BarrierWall> playerWalls = new HashMap<>();
    private final Set<Player> warnedPlayers = new HashSet<>();
    
    // Coordenadas del área prohibida (expandida 1 bloque hacia afuera)
    private final double minX = -76;
    private final double maxX = 76;
    private final double minZ = -41;
    private final double maxZ = 111;
    
    public PvBarrierListener(JavaPlugin plugin) {
        this.plugin = plugin;
        // Iniciar tarea para actualizar las paredes
        startWallUpdateTask();
    }
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // BARRERA TEMPORALMENTE DESACTIVADA PARA PROBAR SI CAUSA ROTACIÓN DE ESCALERAS
        // SOLO BLOQUEAR MOVIMIENTO SIN EFECTOS VISUALES
        
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return; // No se movió realmente
        }
        
        Player player = event.getPlayer();
        
        // Verificar si está en PvP
        if (!isInPvP(player)) {
            return;
        }
        
        Location from = event.getFrom();
        Location to = event.getTo();
        
        // BLOQUEAR MOVIMIENTO SIN BARRERA VISUAL
        if (wouldCrossBarrier(from, to)) {
            // Cancelar inmediatamente
            event.setCancelled(true);
            
            // Empujar hacia atrás con knockback
            applyKnockback(player, from, to);
            
            // Efectos mínimos (sin partículas ni bloques)
            player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.5f, 1.0f);
            player.sendTitle( SmallCaps.convert("§c§l¡ÁREA RESTRINGIDA!"), SmallCaps.convert("§fNo puedes acceder en PvP"), 5, 20, 5);
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removePlayerWall(event.getPlayer());
        warnedPlayers.remove(event.getPlayer());
    }
    
    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        
        // Solo afectar a jugadores en PvP
        if (!isInPvP(player)) return;
        
        Location from = event.getFrom();
        Location to = event.getTo();
        
        // Bloquear teletransporte si cruza la barrera
        if (wouldCrossBarrier(from, to)) {
            event.setCancelled(true);
            
            // Mostrar barrera
            showBarrierWall(player);
            
            // Efectos
            player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f);
            player.sendTitle( SmallCaps.convert("§c§l¡TELEPORTE BLOQUEADO!"), SmallCaps.convert("§fNo puedes teletransportarte al área restringida"), 10, 30, 10);
        }
    }
    
        
    private boolean isInPvP(Player player) {
        // Verificar si el jugador está en combate
        return player.hasMetadata("pvp_combat") || 
               player.hasMetadata("combattag") ||
               CoreProtectPlugin.getInstance().getCombatTagManager().isInCombat(player.getUniqueId());
    }
    
    /**
     * Verifica si el movimiento cruzaría la barrera (pre-chequeo)
     */
    private boolean wouldCrossBarrier(Location from, Location to) {
        // Si ya está dentro del área prohibida, permitir salir
        if (isInsideRestrictedArea(from)) {
            return false;
        }
        
        // Si intenta entrar al área prohibida
        return isInsideRestrictedArea(to);
    }
    
    private boolean isTryingToCrossBarrier(Location from, Location to) {
        return wouldCrossBarrier(from, to);
    }
    
    private boolean isInsideRestrictedArea(Location loc) {
        return loc.getX() >= minX && loc.getX() <= maxX &&
               loc.getZ() >= minZ && loc.getZ() <= maxZ;
    }
    
    private boolean isNearBarrier(Location loc, double distance) {
        return loc.getX() >= minX - distance && loc.getX() <= maxX + distance &&
               loc.getZ() >= minZ - distance && loc.getZ() <= maxZ + distance;
    }
    
    /**
     * Aplica un knockback fuerte al jugador
     */
    private void applyKnockback(Player player, Location from, Location to) {
        // Calcular dirección de knockback (alejarse del área restringida)
        double dx = from.getX() - to.getX();
        double dz = from.getZ() - to.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        
        if (length > 0) {
            // Normalizar y aplicar fuerza de knockback
            double knockbackForce = 3.5; // Knockback fuerte
            dx = (dx / length) * knockbackForce;
            dz = (dz / length) * knockbackForce;
            
            // Aplicar velocidad de knockback
            player.setVelocity(new org.bukkit.util.Vector(dx, 0.3, dz));
            
            // Pequeña elevación para hacer más efectivo
            player.setVelocity(player.getVelocity().setY(0.5));
        }
    }
    
    private boolean isNearBarrier(Location loc) {
        return isNearBarrier(loc, 5);
    }
    
    private void showBarrierWall(Player player) {
        // Crear o actualizar la pared del jugador
        BarrierWall wall = playerWalls.computeIfAbsent(player, p -> new BarrierWall(p));
        wall.update();
    }
    
    private void removePlayerWall(Player player) {
        BarrierWall wall = playerWalls.remove(player);
        if (wall != null) {
            wall.hide();
        }
    }
    
    private void startWallUpdateTask() {
        // TAREA DESACTIVADA TEMPORALMENTE - SIN EFECTOS VISUALES DE BARRERA
        // Solo limpieza de jugadores que salen de PvP
        new BukkitRunnable() {
            @Override
            public void run() {
                // Limpiar barreras de jugadores que ya no están en PvP
                playerWalls.entrySet().removeIf(entry -> {
                    Player player = entry.getKey();
                    if (!player.isOnline() || !isInPvP(player)) {
                        entry.getValue().hide();
                        return true;
                    }
                    return false;
                });
            }
        }.runTaskTimer(plugin, 20L, 20L); // Cada segundo para limpieza
    }
    
    /**
     * Clase interna para manejar la pared de cristal de un jugador
     */
    private class BarrierWall {
        private final Player player;
        private final Set<Location> barrierBlocks = new HashSet<>();
        private long lastUpdate = 0;
        
        public BarrierWall(Player player) {
            this.player = player;
        }
        
        public void update() {
            long now = System.currentTimeMillis();
            if (now - lastUpdate < 20) return; // Actualizar ultra rápido (20ms) para elytras
            lastUpdate = now;
            
            Location loc = player.getLocation();
            
            // Crear nuevo set de bloques necesarios
            Set<Location> neededBlocks = new HashSet<>();
            
            // PRIMERO: Verificar si hay escaleras cerca del jugador
            if (hasStairsNearbyPlayer(loc)) {
                // Si hay escaleras cerca, no mostrar ninguna barrera
                return;
            }
            
            // Determinar qué bordes mostrar
            if (isInsideRestrictedArea(loc)) {
                // Está dentro, mostrar todos los bordes
                neededBlocks.addAll(getAllBorderBlocks(loc));
            } else {
                // Está fuera, mostrar solo el borde que está intentando cruzar
                neededBlocks.addAll(getNearestBorderBlocks(loc));
            }
            
            // Solo actualizar bloques que cambiaron
            // Quitar bloques que ya no se necesitan
            Set<Location> toRemove = new HashSet<>(barrierBlocks);
            toRemove.removeAll(neededBlocks);
            for (Location locToRemove : toRemove) {
                player.sendBlockChange(locToRemove, Material.AIR.createBlockData());
                barrierBlocks.remove(locToRemove);
            }
            
            // Añadir nuevos bloques necesarios
            for (Location locToAdd : neededBlocks) {
                if (!barrierBlocks.contains(locToAdd)) {
                    addBarrierBlock(locToAdd);
                }
            }
        }
        
        public void hide() {
            // Enviar paquetes para "romper" los bloques de cristal
            for (Location blockLoc : barrierBlocks) {
                // Enviar múltiples veces para asegurar que se limpie
                player.sendBlockChange(blockLoc, Material.AIR.createBlockData());
                // También forzar actualización del bloque real
                Material realBlock = player.getWorld().getBlockAt(blockLoc).getType();
                player.sendBlockChange(blockLoc, realBlock.createBlockData());
            }
            barrierBlocks.clear();
            
            // Forzar limpieza completa con un pequeño retraso
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // Enviar paquete de limpieza masiva
                Location playerLoc = player.getLocation();
                int radius = 30;
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        for (int y = -5; y <= 10; y++) {
                            Location checkLoc = playerLoc.clone().add(x, y, z);
                            if (isNearBarrier(checkLoc, 5)) {
                                Material realBlock = player.getWorld().getBlockAt(checkLoc).getType();
                                player.sendBlockChange(checkLoc, realBlock.createBlockData());
                            }
                        }
                    }
                }
            }, 1L);
        }
        
        private Set<Location> getAllBorderBlocks(Location loc) {
            Set<Location> blocks = new HashSet<>();
            int height = 2; // Altura de la pared (más pequeña)
            int baseY = loc.getBlockY();
            
            // Borde X = -75 (oeste)
            for (int z = (int)minZ; z <= (int)maxZ; z++) {
                for (int y = 0; y < height; y++) {
                    Location blockLoc = new Location(player.getWorld(), minX, baseY + y + 1, z);
                    blocks.add(blockLoc);
                }
            }
            
            // Borde X = 75 (este)
            for (int z = (int)minZ; z <= (int)maxZ; z++) {
                for (int y = 0; y < height; y++) {
                    Location blockLoc = new Location(player.getWorld(), maxX, baseY + y + 1, z);
                    blocks.add(blockLoc);
                }
            }
            
            // Borde Z = -40 (sur)
            for (int x = (int)minX; x <= (int)maxX; x++) {
                for (int y = 0; y < height; y++) {
                    Location blockLoc = new Location(player.getWorld(), x, baseY + y + 1, minZ);
                    blocks.add(blockLoc);
                }
            }
            
            // Borde Z = 110 (norte)
            for (int x = (int)minX; x <= (int)maxX; x++) {
                for (int y = 0; y < height; y++) {
                    Location blockLoc = new Location(player.getWorld(), x, baseY + y + 1, maxZ);
                    blocks.add(blockLoc);
                }
            }
            
            return blocks;
        }
        
                
        private Set<Location> getNearestBorderBlocks(Location loc) {
            Set<Location> blocks = new HashSet<>();
            
            // Calcular distancia a cada borde
            double distWest = Math.abs(loc.getX() - minX);
            double distEast = Math.abs(loc.getX() - maxX);
            double distSouth = Math.abs(loc.getZ() - minZ);
            double distNorth = Math.abs(loc.getZ() - maxZ);
            
            // Encontrar el borde más cercano
            double minDist = Math.min(Math.min(distWest, distEast), Math.min(distSouth, distNorth));
            
            int height = 2; // Altura de la pared (más pequeña)
            int baseY = loc.getBlockY();
            
            if (minDist == distWest) {
                // Borde oeste - 5x5 (±3 bloques)
                int zStart = Math.max((int)minZ, (int)loc.getZ() - 3);
                int zEnd = Math.min((int)maxZ, (int)loc.getZ() + 3);
                for (int z = zStart; z <= zEnd; z++) {
                    for (int y = 0; y < height; y++) {
                        Location blockLoc = new Location(player.getWorld(), minX, baseY + y + 1, z);
                        blocks.add(blockLoc);
                    }
                }
            } else if (minDist == distEast) {
                // Borde este - 5x5 (±3 bloques)
                int zStart = Math.max((int)minZ, (int)loc.getZ() - 3);
                int zEnd = Math.min((int)maxZ, (int)loc.getZ() + 3);
                for (int z = zStart; z <= zEnd; z++) {
                    for (int y = 0; y < height; y++) {
                        Location blockLoc = new Location(player.getWorld(), maxX, baseY + y + 1, z);
                        blocks.add(blockLoc);
                    }
                }
            } else if (minDist == distSouth) {
                // Borde sur - 5x5 (±3 bloques)
                int xStart = Math.max((int)minX, (int)loc.getX() - 3);
                int xEnd = Math.min((int)maxX, (int)loc.getX() + 3);
                for (int x = xStart; x <= xEnd; x++) {
                    for (int y = 0; y < height; y++) {
                        Location blockLoc = new Location(player.getWorld(), x, baseY + y + 1, minZ);
                        blocks.add(blockLoc);
                    }
                }
            } else {
                // Borde norte - 5x5 (±3 bloques)
                int xStart = Math.max((int)minX, (int)loc.getX() - 3);
                int xEnd = Math.min((int)maxX, (int)loc.getX() + 3);
                for (int x = xStart; x <= xEnd; x++) {
                    for (int y = 0; y < height; y++) {
                        Location blockLoc = new Location(player.getWorld(), x, baseY + y + 1, maxZ);
                        blocks.add(blockLoc);
                    }
                }
            }
            
            return blocks;
        }
        
        private void showNearestBorder(Location loc) {
            for (Location blockLoc : getNearestBorderBlocks(loc)) {
                addBarrierBlock(blockLoc);
            }
        }
        
        private void addBarrierBlock(Location loc) {
            // Verificación AGRESIVA: No mostrar barrera si hay escaleras cerca
            if (hasStairsNearby(loc)) {
                return;
            }
            
            // Verificar que no haya un bloque sólido real allí
            Material realBlock = player.getWorld().getBlockAt(loc).getType();
            
            // NO mostrar barrera si hay escaleras (doble verificación)
            if (isStairBlock(realBlock)) {
                return;
            }
            
            if (realBlock.isSolid() && realBlock != Material.AIR) {
                // No mostrar barrera donde hay bloques reales
                return;
            }
            
            // Siempre usar cristal rojo, nunca BARRIER
            player.sendBlockChange(loc, Material.RED_STAINED_GLASS.createBlockData());
            barrierBlocks.add(loc);
            
            // Añadir partículas para efecto visual
            if (Math.random() < 0.3) {
                player.spawnParticle(Particle.DUST, loc.clone().add(0.5, 0.5, 0.5), 
                    1, 0.3, 0.3, 0.3, new Particle.DustOptions(org.bukkit.Color.RED, 1.0f));
            }
            
            // No romper bloques existentes - solo enviar paquete visual
            // No usar sonido de colocar para no confundir
        }
        
        /**
         * Verificación si hay escaleras cerca del jugador (10x10x10)
         */
        private boolean hasStairsNearbyPlayer(Location loc) {
            // Revisar 10x10x10 alrededor del jugador
            for (int x = -5; x <= 5; x++) {
                for (int y = -5; y <= 5; y++) {
                    for (int z = -5; z <= 5; z++) {
                        Location checkLoc = loc.clone().add(x, y, z);
                        Material block = player.getWorld().getBlockAt(checkLoc).getType();
                        if (isStairBlock(block)) {
                            return true; // Hay escaleras cerca
                        }
                    }
                }
            }
            return false; // No hay escaleras cerca
        }
        
        /**
         * Verificación AGRESIVA: Revisa si hay escaleras cerca (3x3x3)
         */
        private boolean hasStairsNearby(Location loc) {
            // Revisar 3x3x3 alrededor de la ubicación
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        Location checkLoc = loc.clone().add(x, y, z);
                        Material block = player.getWorld().getBlockAt(checkLoc).getType();
                        if (isStairBlock(block)) {
                            return true; // Hay escaleras cerca
                        }
                    }
                }
            }
            return false; // No hay escaleras cerca
        }
        
        /**
         * Verifica si un material es una escalera
         */
        private boolean isStairBlock(Material material) {
            String name = material.name();
            return name.endsWith("_STAIRS") || name.endsWith("_STAIR");
        }
    }
}
