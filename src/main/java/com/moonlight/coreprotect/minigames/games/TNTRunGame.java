package com.moonlight.coreprotect.minigames.games;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.minigames.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * TNT Run: Correr sin parar. El bloque que pisas desaparece a los 0.5 segundos.
 * El último en caer al vacío, gana.
 */
public class TNTRunGame extends MiniGame {

    private final Map<Location, Long> scheduledRemovals = new HashMap<>();
    private int removalTaskId = -1;

    public TNTRunGame(CoreProtectPlugin plugin, MiniGameManager manager) {
        super(plugin, manager, MiniGameType.TNT_RUN);
    }

    @Override
    public void buildArena(World world) {
        ArenaBuilder.buildTNTRun(world);
    }

    @Override
    public List<Location> getSpawnLocations(World world) {
        return ArenaBuilder.getTNTRunSpawns(world);
    }

    @Override
    public void startGameLogic() {
        // Tarea rápida: comprobar bloques bajo los pies cada 2 ticks
        removalTaskId = new BukkitRunnable() {
            @Override
            public void run() {
                if (!running) {
                    cancel();
                    return;
                }

                long now = System.currentTimeMillis();

                // Programar bloques bajo los pies de los jugadores
                // Hitbox del jugador = 0.6 bloques de ancho, comprobar las 4 esquinas
                final double HITBOX = 0.3;
                for (UUID uuid : new ArrayList<>(alivePlayers)) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null || !p.isOnline()) continue;

                    double px = p.getLocation().getX();
                    double py = p.getLocation().getY();
                    double pz = p.getLocation().getZ();

                    // Comprobar todas las posiciones que el hitbox del jugador toca
                    double[] offsets = {-HITBOX, HITBOX};
                    for (double dx : offsets) {
                        for (double dz : offsets) {
                            Location below = new Location(p.getWorld(), px + dx, py - 1, pz + dz);
                            Location blockLoc = below.getBlock().getLocation();
                            Material type = below.getBlock().getType();

                            if (type != Material.AIR && type != Material.LAVA && type != Material.OBSIDIAN) {
                                if (!scheduledRemovals.containsKey(blockLoc)) {
                                    scheduledRemovals.put(blockLoc, now + 500); // 0.5 segundos
                                }
                            }
                        }
                    }

                    // Comprobar si cayó al vacío (debajo de Y=55)
                    if (p.getLocation().getY() < 55) {
                        eliminatePlayer(uuid);
                    }
                }

                // Procesar eliminaciones programadas
                Iterator<Map.Entry<Location, Long>> it = scheduledRemovals.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<Location, Long> entry = it.next();
                    if (now >= entry.getValue()) {
                        Location loc = entry.getKey();
                        if (loc.getBlock().getType() != Material.AIR) {
                            // Efecto de partículas
                            loc.getWorld().spawnParticle(Particle.BLOCK, 
                                loc.clone().add(0.5, 0.5, 0.5), 10, 
                                0.3, 0.3, 0.3, 0,
                                loc.getBlock().getBlockData());
                            loc.getBlock().setType(Material.AIR);
                            loc.getWorld().playSound(loc, Sound.BLOCK_SAND_BREAK, 0.3f, 1.5f);
                        }
                        // También quitar el soporte
                        Location support = loc.clone().subtract(0, 1, 0);
                        if (support.getBlock().getType() != Material.AIR && 
                            support.getBlock().getType() != Material.LAVA &&
                            support.getBlock().getType() != Material.OBSIDIAN) {
                            support.getBlock().setType(Material.AIR);
                        }
                        it.remove();
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L).getTaskId();
    }

    @Override
    public void onTick() {
        // Broadcast tiempo cada 30 segundos
        if (gameTime % 30 == 0 && gameTime > 0) {
            broadcastGame("§c§lTNT Run §7| §fTiempo: §e" + gameTime + "s §7| §fVivos: §a" + alivePlayers.size());
        }

        // Si pasan 5 minutos, empezar a eliminar capas
        if (gameTime == 300) {
            broadcastGame("§c§l⚠ §e¡Las plataformas se destruyen más rápido!");
            titleAlive("§c§l¡PELIGRO!", "§eLas plataformas se debilitan");
        }
    }

    @Override
    public void onCleanup() {
        scheduledRemovals.clear();
        if (removalTaskId != -1) {
            try { Bukkit.getScheduler().cancelTask(removalTaskId); } catch (Exception ignored) {}
            removalTaskId = -1;
        }
    }
}
