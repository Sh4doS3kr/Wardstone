package com.moonlight.coreprotect.minigames.games;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.minigames.*;
import com.moonlight.coreprotect.util.ActionBarUtil;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Luz Roja, Luz Verde (Red Light Green Light):
 * Los jugadores empiezan en un extremo de un pasillo largo.
 * Deben llegar al otro extremo (meta).
 * - LUZ VERDE: Pueden moverse libremente.
 * - LUZ ROJA: Si se mueven, son eliminados.
 * Fases progresivas cada vez más difíciles con timings random y trolleos.
 * 0.1s de gracia al cambiar a rojo para que te dé tiempo a parar.
 * SOLO gana quien llega al final. Si todos mueren, nadie gana.
 * Congelación sin pociones: usa teleport-back por tick.
 */
public class RedLightGreenLightGame extends MiniGame {

    private boolean greenLight = true;
    private boolean frozen = true;
    private boolean graceActive = false;
    private int currentRound = 0;
    private int currentPhase = 1;
    private BossBar statusBar;
    private final Map<UUID, Location> lastPositions = new HashMap<>();
    private final Map<UUID, Location> frozenPositions = new HashMap<>();
    private BukkitRunnable freezeTask = null;
    private static final int TIME_LIMIT = 180;
    private static final double FINISH_LINE_Z = -45;
    private static final double START_LINE_Z = 45;
    private static final double MOVEMENT_THRESHOLD = 0.15;
    private final Random random = new Random();

    public RedLightGreenLightGame(CoreProtectPlugin plugin, MiniGameManager manager) {
        super(plugin, manager, MiniGameType.RED_LIGHT_GREEN_LIGHT);
    }

    @Override
    public void buildArena(World world) {
        ArenaBuilder.buildRedLightGreenLight(world);
    }

    @Override
    public List<Location> getSpawnLocations(World world) {
        return ArenaBuilder.getRedLightGreenLightSpawns(world);
    }

    @Override
    protected void onPreCountdown() {
        // Congelar jugadores INMEDIATAMENTE al ser teleportados, ANTES del countdown 5,4,3,2,1
        frozen = true;
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                frozenPositions.put(uuid, p.getLocation().clone());
            }
        }
        startFreezeTask();
    }

    @Override
    public void startGameLogic() {
        greenLight = false;
        frozen = true;
        graceActive = false;
        currentRound = 0;
        currentPhase = 1;

        // BossBar
        statusBar = Bukkit.createBossBar("§e§lPREPARADOS...", BarColor.YELLOW, BarStyle.SOLID);
        statusBar.setProgress(1.0);
        statusBar.setVisible(true);

        // Re-freeze y guardar posiciones
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                statusBar.addPlayer(p);
                frozenPositions.put(uuid, p.getLocation().clone());
                lastPositions.put(uuid, p.getLocation().clone());
            }
        }

        broadcastGame("");
        broadcastGame("§a§l🟢 §f§lLUZ ROJA, LUZ VERDE §c§l🔴");
        broadcastGame("§7¡Corre cuando sea §aLUZ VERDE§7!");
        broadcastGame("§7¡Quieto cuando sea §cLUZ ROJA§7! Si te mueves, §c¡ELIMINADO!");
        broadcastGame("§7Llega a la §emeta §7para ganar. §cSi no llegas, no ganas.");
        broadcastGame("");

        // Descongelar después de 2 segundos e iniciar primera fase verde
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!running) return;
            frozen = false;
            stopFreezeTask();
            startGreenPhase(getGreenDuration());
        }, 40L);
    }

    private void startFreezeTask() {
        stopFreezeTask();
        freezeTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!running || !frozen) {
                    cancel();
                    return;
                }
                // Cada tick, forzar a los jugadores a quedarse en su posición
                for (UUID uuid : alivePlayers) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null || !p.isOnline()) continue;
                    Location frozenLoc = frozenPositions.get(uuid);
                    if (frozenLoc == null) continue;
                    Location current = p.getLocation();
                    double dx = Math.abs(current.getX() - frozenLoc.getX());
                    double dz = Math.abs(current.getZ() - frozenLoc.getZ());
                    if (dx > 0.05 || dz > 0.05) {
                        Location tp = frozenLoc.clone();
                        tp.setYaw(current.getYaw());
                        tp.setPitch(current.getPitch());
                        p.teleport(tp);
                    }
                    p.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                }
            }
        };
        freezeTask.runTaskTimer(plugin, 0L, 1L);
    }

    private void stopFreezeTask() {
        if (freezeTask != null) {
            try { freezeTask.cancel(); } catch (Exception ignored) {}
            freezeTask = null;
        }
    }

    /**
     * Calcula la duración de luz verde según la fase actual.
     * Fase 1: 3-4 segundos (normal).
     * Fase 2+: Entre 1s y 3s, random y caótico.
     */
    private int getGreenDuration() {
        if (currentPhase <= 1) {
            return 60 + random.nextInt(20); // 3-4s
        }
        // Fases avanzadas: 1s-3s (20-60 ticks)
        return 20 + random.nextInt(41);
    }

    /**
     * Calcula la duración de luz roja según la fase actual.
     * Fase 1: 2-4 segundos.
     * Fase 2+: Entre 1s y 3s, random.
     */
    private int getRedDuration() {
        if (currentPhase <= 1) {
            return 40 + random.nextInt(40); // 2-4s
        }
        // Fases avanzadas: 1s-3s (20-60 ticks)
        return 20 + random.nextInt(41);
    }

    private void startGreenPhase(int durationTicks) {
        if (!running) return;
        greenLight = true;
        graceActive = false;
        currentRound++;

        // Avanzar de fase cada 5 rondas
        currentPhase = 1 + (currentRound / 5);

        statusBar.setTitle("§a§l¡LUZ VERDE! §f¡CORRE!");
        statusBar.setColor(BarColor.GREEN);
        statusBar.setProgress(1.0);

        // En fase 2+ a veces falsas alarmas de título
        if (currentPhase >= 2 && random.nextInt(5) == 0 && durationTicks > 20) {
            // Trolleo: mostrar brevemente "LUZ ROJA" pero NO es roja de verdad
            titleAlive("§c§l¡LUZ RO...!", "§a§l¡Jaja! Sigue corriendo");
            soundAll(Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
        } else {
            titleAlive("§a§l¡LUZ VERDE!", "§7¡Corre!");
        }
        soundAll(Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 2.0f);

        // Actualizar posiciones al inicio de verde
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) lastPositions.put(uuid, p.getLocation().clone());
        }

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!running) { cancel(); return; }
                ticks++;
                double progress = Math.max(0, 1.0 - ((double) ticks / durationTicks));
                statusBar.setProgress(progress);

                if (ticks >= durationTicks) {
                    cancel();
                    if (running && !alivePlayers.isEmpty()) startRedPhase();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void startRedPhase() {
        if (!running) return;
        greenLight = false;
        graceActive = true; // 0.1s de gracia (2 ticks)

        int redDuration = getRedDuration();

        statusBar.setTitle("§c§l¡LUZ ROJA! §f¡QUIETO!");
        statusBar.setColor(BarColor.RED);
        statusBar.setProgress(1.0);

        titleAlive("§c§l¡LUZ ROJA!", "§7¡No te muevas!");
        soundAll(Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.8f, 2.0f);

        // Gracia de 8 ticks (0.4s) antes de empezar a comprobar movimiento
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!running) return;
            graceActive = false;
            // Guardar posiciones DESPUÉS de la gracia (posición donde se pararon)
            for (UUID uuid : alivePlayers) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) lastPositions.put(uuid, p.getLocation().clone());
            }
        }, 8L);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!running) { cancel(); return; }
                ticks++;
                double progress = Math.max(0, 1.0 - ((double) ticks / redDuration));
                statusBar.setProgress(progress);

                // Comprobar movimiento cada 4 ticks (después de la gracia)
                if (ticks % 4 == 0 && !graceActive) {
                    checkMovement();
                }

                if (ticks >= redDuration) {
                    cancel();
                    if (running && !alivePlayers.isEmpty()) {
                        int greenDuration = getGreenDuration();
                        startGreenPhase(greenDuration);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void checkMovement() {
        if (greenLight || graceActive || frozen) return;
        for (UUID uuid : new ArrayList<>(alivePlayers)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;

            Location last = lastPositions.get(uuid);
            if (last == null) continue;

            Location current = p.getLocation();
            double dx = Math.abs(current.getX() - last.getX());
            double dz = Math.abs(current.getZ() - last.getZ());
            double movement = Math.sqrt(dx * dx + dz * dz);

            if (movement > MOVEMENT_THRESHOLD) {
                broadcastGame("§c§l🔴 §f" + p.getName() + " §c¡se movió! §7Eliminado.");
                p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.0f, 1.0f);
                eliminatePlayer(uuid);
            }
        }
    }

    @Override
    public void onTick() {
        if (!running) return;

        // Si están congelados, forzar que no se muevan
        if (frozen) {
            for (UUID uuid : alivePlayers) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    ActionBarUtil.send(p, "§e§l¡PREPARADOS! §7No te muevas todavía...");
                }
            }
            return;
        }

        // Comprobar si alguien llegó a la meta
        for (UUID uuid : new ArrayList<>(alivePlayers)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;

            double z = p.getLocation().getZ();
            if (z <= FINISH_LINE_Z) {
                broadcastGame("§a§l🏆 §f" + p.getName() + " §a¡ha llegado a la meta!");
                endGame(p);
                return;
            }
        }

        // Si no queda nadie vivo, nadie gana
        if (alivePlayers.isEmpty()) {
            broadcastGame("§c§l☠ §7¡Todos han sido eliminados! §cNadie ha ganado.");
            endGame(null);
            return;
        }

        // Mostrar progreso en action bar
        if (!frozen && greenLight) {
            for (UUID uuid : alivePlayers) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null) continue;
                double z = p.getLocation().getZ();
                double totalDist = START_LINE_Z - FINISH_LINE_Z;
                double progress = Math.max(0, Math.min(100, ((START_LINE_Z - z) / totalDist) * 100));
                ActionBarUtil.send(p, "§a§l🟢 VERDE §7| §fProgreso: §e" + (int) progress + "% §7| §fFase: §c" + currentPhase);
            }
        }

        // Info cada 30 sec
        if (gameTime % 30 == 0 && gameTime > 0) {
            int remaining = TIME_LIMIT - gameTime;
            broadcastGame("§a§lLuz Roja/Verde §7| §fFase: §c" + currentPhase + " §7| §fVivos: §a" + alivePlayers.size() + " §7| §e" + remaining + "s");
        }

        // Anunciar fase de trolleo
        if (currentRound == 5 && gameTime > 0) {
            broadcastGame("§c§l⚠ §e§lFASE 2 §c§l⚠ §7¡Los tiempos se vuelven LOCOS!");
        }
        if (currentRound == 10) {
            broadcastGame("§4§l⚠ §c§lFASE 3 §4§l⚠ §7¡Caos total! ¡Suerte!");
        }

        // Tiempo límite — nadie gana si no llegan
        if (gameTime >= TIME_LIMIT) {
            broadcastGame("§e§l⏳ §7¡Tiempo agotado! §cNadie llegó a la meta. Nadie gana.");
            endGame(null);
        }
    }

    @Override
    public void checkWinCondition() {
        // Solo se gana llegando a la meta - no usar lógica genérica
        if (alivePlayers.isEmpty() && running) {
            broadcastGame("§c§l☠ §7¡Todos eliminados! §cNadie ha ganado.");
            endGame(null);
        }
    }

    @Override
    public void onCleanup() {
        stopFreezeTask();
        if (statusBar != null) {
            statusBar.removeAll();
            statusBar.setVisible(false);
        }
        lastPositions.clear();
        frozenPositions.clear();
    }
}
