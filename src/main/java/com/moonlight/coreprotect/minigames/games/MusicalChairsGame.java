package com.moonlight.coreprotect.minigames.games;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.minigames.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Sillas Musicales - Musical Chairs
 * Rondas eliminatorias: suena música, jugadores corren en círculo,
 * cuando para la música deben sentarse en una vagoneta (silla).
 * Siempre hay 1 silla menos que jugadores. El que no se siente, eliminado.
 */
public class MusicalChairsGame extends MiniGame {

    private final Random random = new Random();
    private final List<Minecart> chairs = new CopyOnWriteArrayList<>();
    private int round = 0;
    private boolean musicPlaying = false;
    private int musicTaskId = -1;
    private int roundTaskId = -1;
    private boolean roundInProgress = false;

    // Bots
    private static final int BOT_COUNT = 5;
    private static final String[] BOT_NAMES = {"Carlos", "Luna", "Pablo", "Sofia", "Diego"};
    private final Map<UUID, Villager> botEntities = new HashMap<>();
    private final Set<UUID> botUUIDs = new HashSet<>();
    private int botMoveTaskId = -1;
    
    // Movement tracking for music phase
    private final Map<UUID, Location> lastMusicPositions = new HashMap<>();
    private final Map<UUID, Integer> stillnessTicks = new HashMap<>();
    private final Map<UUID, Integer> illegalSitAttempts = new HashMap<>();
    private int musicMovementTaskId = -1;
    private static final double MOVE_THRESHOLD = 0.3;
    private static final int MAX_STILLNESS_TICKS = 80; // 4 segundos
    private static final int MAX_SIT_ATTEMPTS = 3;

    // Sonidos de "música" - secuencia de notas
    private static final Sound[] MUSIC_NOTES = {
        Sound.BLOCK_NOTE_BLOCK_HARP, Sound.BLOCK_NOTE_BLOCK_BELL,
        Sound.BLOCK_NOTE_BLOCK_CHIME, Sound.BLOCK_NOTE_BLOCK_FLUTE,
        Sound.BLOCK_NOTE_BLOCK_GUITAR, Sound.BLOCK_NOTE_BLOCK_XYLOPHONE,
        Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE, Sound.BLOCK_NOTE_BLOCK_PLING
    };

    private static final float[] MELODY = {
        0.5f, 0.6f, 0.7f, 0.8f, 1.0f, 1.2f, 1.0f, 0.8f,
        0.6f, 0.8f, 1.0f, 1.2f, 1.4f, 1.6f, 1.4f, 1.2f,
        1.0f, 0.8f, 0.6f, 0.5f, 0.7f, 0.9f, 1.1f, 1.3f
    };

    public MusicalChairsGame(CoreProtectPlugin plugin, MiniGameManager manager) {
        super(plugin, manager, MiniGameType.MUSICAL_CHAIRS);
    }

    @Override
    public void buildArena(World world) {
        ArenaBuilder.buildMusicalChairs(world);
    }

    @Override
    public List<Location> getSpawnLocations(World world) {
        return ArenaBuilder.getMusicalChairsSpawns(world);
    }

    @Override
    public void startGameLogic() {
        round = 0;
        broadcastGame("§d§l♫ §e¡SILLAS MUSICALES! §7Cuando pare la música, ¡siéntate en una vagoneta!");
        broadcastGame("§d§l♫ §7Siempre hay 1 silla menos que jugadores. ¡No te quedes sin silla!");
        titleAlive("§d§l♫ SILLAS MUSICALES ♫", "§7¡Prepárate para correr!");

        // Spawnear bots
        spawnBots();

        // Dar speed a todos para que corran
        for (UUID uuid : alivePlayers) {
            if (botUUIDs.contains(uuid)) continue;
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.SPEED, 999999, 0, false, false, false));
            }
        }

        // Iniciar primera ronda tras 3 segundos
        Bukkit.getScheduler().runTaskLater(plugin, this::startNewRound, 60L);
    }

    private void spawnBots() {
        World w = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (w == null) return;

        for (int i = 0; i < BOT_COUNT; i++) {
            final int idx = i;
            double angle = (Math.PI * 2.0 / BOT_COUNT) * i;
            Location loc = new Location(w, Math.cos(angle) * 6 + 0.5, 81, Math.sin(angle) * 6 + 0.5);
            Villager bot = w.spawn(loc, Villager.class, v -> {
                v.setCustomName("§e§l" + BOT_NAMES[idx % BOT_NAMES.length] + " §7[BOT]");
                v.setCustomNameVisible(true);
                v.setAI(true);
                v.setSilent(true);
                v.setRemoveWhenFarAway(false);
                v.setPersistent(true);
                v.setInvulnerable(true);
                v.setProfession(Villager.Profession.values()[
                    (idx + 1) % Villager.Profession.values().length]);
                v.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.SPEED, 999999, 1, false, false, false));
            });
            UUID botId = bot.getUniqueId();
            botEntities.put(botId, bot);
            botUUIDs.add(botId);
            alivePlayers.add(botId);
            players.add(botId);
        }
        broadcastGame("§d§l♫ §7¡" + BOT_COUNT + " bots se han unido al juego!");
    }

    private void startNewRound() {
        if (!running || alivePlayers.size() <= 1) {
            checkWinCondition();
            return;
        }

        round++;
        roundInProgress = true;
        int chairCount = alivePlayers.size() - 1;

        broadcastGame("§d§l♫ §eRonda " + round + " §7| §fJugadores: §a" + alivePlayers.size() + " §7| §fSillas: §c" + chairCount);
        titleAlive("§d§lRonda " + round, "§eSillas: §c" + chairCount + " §7| §eJugadores: §a" + alivePlayers.size());

        // Limpiar sillas anteriores
        removeAllChairs();

        // Spawn nuevas sillas en círculo
        World w = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (w == null) return;

        List<Location> chairLocs = ArenaBuilder.getChairPositions(w, chairCount);
        for (Location loc : chairLocs) {
            try {
                Minecart cart = w.spawn(loc, Minecart.class);
                cart.setCustomName("§d§l♫ §eSilla §d§l♫");
                cart.setCustomNameVisible(true);
                cart.setGravity(true);
                cart.setMaxSpeed(0);
                cart.setSlowWhenEmpty(true);
                cart.setInvulnerable(true);
                chairs.add(cart);
            } catch (Exception e) {
                plugin.getLogger().warning("[MusicalChairs] Error spawning chair: " + e.getMessage());
            }
        }

        // Efectos visuales en sillas
        for (Minecart chair : chairs) {
            w.spawnParticle(Particle.NOTE, chair.getLocation().add(0, 1, 0), 5, 0.5, 0.5, 0.5, 0);
        }

        // Iniciar música
        startMusic(w);

        // La música durará entre 5 y 15 segundos (aleatorio)
        int musicDuration = 100 + random.nextInt(200); // 5-15 segundos en ticks

        // Parar música tras el tiempo
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!running || !roundInProgress) return;
            stopMusic();
            startSitPhase(w);
        }, musicDuration);
    }

    private void startMusic(World w) {
        musicPlaying = true;
        broadcastGame("§d§l♫ §a¡La música suena! §7¡Corre en círculo!");
        broadcastGame("§d§l♫ §c¡DEBES MOVERTE! §7Si te quedas quieto serás eliminado.");
        broadcastGame("§d§l♫ §c¡NO puedes sentarte durante la música!");

        // Velocidad extra durante música
        for (UUID uuid : alivePlayers) {
            if (botUUIDs.contains(uuid)) continue;
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.SPEED, 999999, 1, false, false, false));
                // Guardar posición inicial
                lastMusicPositions.put(uuid, p.getLocation().clone());
            }
        }

        // Task para verificar movimiento constante
        musicMovementTaskId = new BukkitRunnable() {
            @Override
            public void run() {
                if (!musicPlaying || !running) { cancel(); return; }

                for (UUID uuid : new ArrayList<>(alivePlayers)) {
                    if (botUUIDs.contains(uuid)) continue;
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null || !p.isOnline()) continue;

                    // Comprobar si está sentado (no permitido durante música)
                    if (p.isInsideVehicle() && p.getVehicle() instanceof Minecart) {
                        p.leaveVehicle();
                        int attempts = illegalSitAttempts.getOrDefault(uuid, 0) + 1;
                        illegalSitAttempts.put(uuid, attempts);
                        
                        if (attempts >= MAX_SIT_ATTEMPTS) {
                            // ELIMINACIÓN INSTANTÁNEA por intentar sentarse 3+ veces
                            p.sendTitle("§c§l¡ELIMINADO!", "§7Intentaste sentarte " + attempts + " veces durante la música", 10, 40, 10);
                            p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.5f);
                            w.spawnParticle(Particle.EXPLOSION, p.getLocation().add(0, 1, 0), 5, 0.5, 0.5, 0.5, 0);
                            broadcastGame("§c§l✖ §f" + p.getName() + " §7fue eliminado por intentar sentarse durante la música.");
                            eliminatePlayer(uuid);
                            continue;
                        }
                        
                        p.sendTitle("§c§l¡NO SENTARSE! (" + attempts + "/" + MAX_SIT_ATTEMPTS + ")", "§7No puedes sentarte durante la música", 5, 20, 5);
                        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        continue;
                    }

                    // Comprobar si se ha movido
                    Location last = lastMusicPositions.get(uuid);
                    if (last == null) {
                        stillnessTicks.put(uuid, 0);
                        continue;
                    }

                    double dx = Math.abs(p.getLocation().getX() - last.getX());
                    double dz = Math.abs(p.getLocation().getZ() - last.getZ());

                    if (dx < MOVE_THRESHOLD && dz < MOVE_THRESHOLD) {
                        // No se ha movido suficiente - incrementar contador
                        int ticks = stillnessTicks.getOrDefault(uuid, 0) + 10;
                        stillnessTicks.put(uuid, ticks);
                        
                        if (ticks >= MAX_STILLNESS_TICKS) {
                            // ELIMINACIÓN INSTANTÁNEA por estar quieto 4+ segundos
                            p.sendTitle("§c§l¡ELIMINADO!", "§7Estuviste quieto más de 4 segundos", 10, 40, 10);
                            p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.5f);
                            w.spawnParticle(Particle.EXPLOSION, p.getLocation().add(0, 1, 0), 5, 0.5, 0.5, 0.5, 0);
                            broadcastGame("§c§l✖ §f" + p.getName() + " §7fue eliminado por quedarse quieto.");
                            eliminatePlayer(uuid);
                            continue;
                        }
                        
                        int secondsLeft = (MAX_STILLNESS_TICKS - ticks) / 20;
                        p.sendTitle("§c§l¡QUIETO! (" + secondsLeft + "s)", "§7¡Debes moverte constantemente!", 5, 15, 5);
                        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 0.5f);
                        w.spawnParticle(Particle.SMOKE, p.getLocation().add(0, 1, 0), 5, 0.3, 0.3, 0.3, 0.02);
                    } else {
                        // Se movió - resetear contador
                        stillnessTicks.put(uuid, 0);
                    }

                    // Actualizar última posición
                    lastMusicPositions.put(uuid, p.getLocation().clone());
                }
            }
        }.runTaskTimer(plugin, 20L, 10L).getTaskId(); // Comprobar cada 0.5s

        // Reproducir notas musicales en bucle
        musicTaskId = new BukkitRunnable() {
            int noteIndex = 0;
            @Override
            public void run() {
                if (!musicPlaying || !running) { cancel(); return; }

                Sound note = MUSIC_NOTES[noteIndex % MUSIC_NOTES.length];
                float pitch = MELODY[noteIndex % MELODY.length];

                for (UUID uuid : players) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        p.playSound(p.getLocation(), note, 0.8f, pitch);
                    }
                }

                // Partículas de notas musicales en las sillas
                if (noteIndex % 3 == 0) {
                    for (Minecart chair : chairs) {
                        if (chair.isValid() && !chair.isDead()) {
                            w.spawnParticle(Particle.NOTE, chair.getLocation().add(0, 1.5, 0), 1, 0.3, 0.3, 0.3, 0);
                        }
                    }
                }

                // Partículas disco en el centro
                if (noteIndex % 5 == 0) {
                    w.spawnParticle(Particle.DUST, new Location(w, 0.5, 83, 0.5), 10, 2, 1, 2, 0,
                        new Particle.DustOptions(Color.fromRGB(
                            random.nextInt(256), random.nextInt(256), random.nextInt(256)), 1.5f));
                }

                noteIndex++;
            }
        }.runTaskTimer(plugin, 0L, 4L).getTaskId();
    }

    private void stopMusic() {
        musicPlaying = false;

        if (musicTaskId != -1) {
            try { Bukkit.getScheduler().cancelTask(musicTaskId); } catch (Exception ignored) {}
            musicTaskId = -1;
        }
        if (musicMovementTaskId != -1) {
            try { Bukkit.getScheduler().cancelTask(musicMovementTaskId); } catch (Exception ignored) {}
            musicMovementTaskId = -1;
        }

        // Limpiar posiciones de movimiento y contadores
        lastMusicPositions.clear();
        stillnessTicks.clear();
        illegalSitAttempts.clear();

        // Sonido de parada dramático
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.6f, 2.0f);
                p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 1.5f);
                p.removePotionEffect(org.bukkit.potion.PotionEffectType.SPEED);
            }
        }

        titleAlive("§c§l¡PARA LA MÚSICA!", "§e¡Busca una silla! ¡RÁPIDO!");
        broadcastGame("§c§l⚠ §c¡LA MÚSICA HA PARADO! §e¡Siéntate en una vagoneta!");
    }

    private void startSitPhase(World w) {
        // Dar 5 segundos para sentarse
        final Set<UUID> seated = new HashSet<>();

        // Bots intentan sentarse con un delay aleatorio (30-70 ticks)
        List<Minecart> availableChairs = new ArrayList<>(chairs);
        Collections.shuffle(availableChairs);
        int botsSitting = 0;
        for (UUID botId : new ArrayList<>(botUUIDs)) {
            if (!alivePlayers.contains(botId)) continue;
            Villager bot = botEntities.get(botId);
            if (bot == null || bot.isDead()) continue;
            if (botsSitting < availableChairs.size()) {
                final Minecart targetChair = availableChairs.get(botsSitting);
                final UUID fBotId = botId;
                int delay = 20 + random.nextInt(50); // 1-3.5s de delay
                botsSitting++;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!running || !roundInProgress) return;
                    if (targetChair.isDead() || !targetChair.isValid()) return;
                    Villager b = botEntities.get(fBotId);
                    if (b == null || b.isDead()) return;
                    if (!targetChair.getPassengers().isEmpty()) return;
                    b.teleport(targetChair.getLocation());
                    targetChair.addPassenger(b);
                    seated.add(fBotId);
                    w.spawnParticle(Particle.HAPPY_VILLAGER, b.getLocation().add(0, 1, 0), 5, 0.3, 0.3, 0.3, 0);
                }, delay);
            }
        }

        roundTaskId = new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!running || !roundInProgress) { cancel(); return; }

                // Comprobar quién está sentado en una vagoneta (jugadores reales)
                for (UUID uuid : new ArrayList<>(alivePlayers)) {
                    if (botUUIDs.contains(uuid)) continue;
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null || !p.isOnline()) continue;

                    if (p.isInsideVehicle() && p.getVehicle() instanceof Minecart) {
                        if (!seated.contains(uuid)) {
                            seated.add(uuid);
                            p.sendTitle("§a§l✔", "§a¡Estás sentado!", 5, 20, 5);
                            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
                            w.spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0);
                        }
                    }
                }

                // Countdown visual
                int secondsLeft = (100 - ticks) / 20;
                if (ticks % 20 == 0 && secondsLeft > 0 && secondsLeft <= 3) {
                    String color = secondsLeft == 1 ? "§c" : "§e";
                    for (UUID uuid : alivePlayers) {
                        if (!seated.contains(uuid)) {
                            Player p = Bukkit.getPlayer(uuid);
                            if (p != null && p.isOnline()) {
                                p.sendTitle(color + "§l" + secondsLeft, "§7¡Busca silla!", 0, 15, 5);
                                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, secondsLeft == 1 ? 2.0f : 1.0f);
                            }
                        }
                    }
                }

                ticks++;

                // Todos sentados o tiempo agotado (5 segundos)
                if (seated.size() >= chairs.size() || ticks >= 100) {
                    cancel();
                    roundTaskId = -1;
                    endRound(seated);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L).getTaskId();
    }

    private void endRound(Set<UUID> seated) {
        if (!running) return;
        roundInProgress = false;

        // Identificar jugadores eliminados (no sentados)
        List<UUID> eliminated = new ArrayList<>();
        for (UUID uuid : new ArrayList<>(alivePlayers)) {
            if (!seated.contains(uuid)) {
                eliminated.add(uuid);
            }
        }

        // Si nadie fue eliminado, eliminar uno aleatorio
        if (eliminated.isEmpty() && alivePlayers.size() > 1) {
            List<UUID> aliveList = new ArrayList<>(alivePlayers);
            UUID unlucky = aliveList.get(random.nextInt(aliveList.size()));
            eliminated.add(unlucky);
        }

        // Sacar a todos de las vagonetas
        for (UUID uuid : new ArrayList<>(alivePlayers)) {
            if (botUUIDs.contains(uuid)) {
                Villager bot = botEntities.get(uuid);
                if (bot != null && !bot.isDead() && bot.isInsideVehicle()) {
                    bot.leaveVehicle();
                }
            } else {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline() && p.isInsideVehicle()) {
                    p.leaveVehicle();
                }
            }
        }

        // Eliminar a los que no se sentaron
        for (UUID uuid : eliminated) {
            if (botUUIDs.contains(uuid)) {
                // Eliminar bot
                Villager bot = botEntities.get(uuid);
                String botName = bot != null ? bot.getCustomName() : "Bot";
                if (bot != null && !bot.isDead()) {
                    World w = Bukkit.getWorld(MiniGameWorld.getWorldName());
                    if (w != null) w.spawnParticle(Particle.SMOKE, bot.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.05);
                    bot.remove();
                }
                botEntities.remove(uuid);
                alivePlayers.remove(uuid);
                spectators.add(uuid);
                broadcastGame("§c§l✖ §f" + botName + " §7ha sido eliminado. §eQuedan §f" + alivePlayers.size());
                checkWinCondition();
            } else {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    p.sendTitle("§c§l¡SIN SILLA!", "§7No encontraste silla a tiempo", 10, 40, 10);
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    World w = Bukkit.getWorld(MiniGameWorld.getWorldName());
                    if (w != null) {
                        w.spawnParticle(Particle.SMOKE, p.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.05);
                    }
                }
                eliminatePlayer(uuid);
            }
        }

        // Limpiar sillas
        removeAllChairs();

        // Si aún hay suficientes jugadores, siguiente ronda
        if (alivePlayers.size() > 1 && running) {
            broadcastGame("§d§l♫ §7Siguiente ronda en 5 segundos...");
            Bukkit.getScheduler().runTaskLater(plugin, this::startNewRound, 100L);
        } else {
            checkWinCondition();
        }
    }

    private void removeAllChairs() {
        for (Minecart chair : chairs) {
            if (chair != null && !chair.isDead()) {
                // Sacar pasajeros antes de eliminar
                for (Entity passenger : chair.getPassengers()) {
                    chair.removePassenger(passenger);
                }
                chair.remove();
            }
        }
        chairs.clear();
    }

    @Override
    public void onTick() {
        // Comprobar jugadores caídos al vacío
        for (UUID uuid : new ArrayList<>(alivePlayers)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;
            if (p.getLocation().getY() < 70) {
                eliminatePlayer(uuid);
            }
        }

        // Info cada 30 segundos
        if (gameTime % 30 == 0 && gameTime > 0) {
            broadcastGame("§d§l♫ Sillas Musicales §7| §fRonda: §e" + round + " §7| §fVivos: §a" + alivePlayers.size());
        }

        // Timeout a los 5 minutos
        if (gameTime >= 300) {
            broadcastGame("§d§l♫ §7¡Tiempo agotado!");
            if (!alivePlayers.isEmpty()) {
                UUID winnerUUID = alivePlayers.iterator().next();
                endGame(Bukkit.getPlayer(winnerUUID));
            } else {
                endGame(null);
            }
        }
    }

    @Override
    public void checkWinCondition() {
        // Contar jugadores reales vivos
        int realAlive = 0;
        UUID lastReal = null;
        for (UUID uuid : alivePlayers) {
            if (!botUUIDs.contains(uuid)) {
                realAlive++;
                lastReal = uuid;
            }
        }
        // Si solo queda 1 jugador real (con o sin bots), gana
        if (realAlive <= 1 && running) {
            if (realAlive == 1) {
                endGame(Bukkit.getPlayer(lastReal));
            } else if (alivePlayers.size() <= 1) {
                endGame(null);
            } else {
                // Solo quedan bots, no hay ganador
                endGame(null);
            }
        }
    }

    @Override
    public void onCleanup() {
        musicPlaying = false;
        if (musicTaskId != -1) {
            try { Bukkit.getScheduler().cancelTask(musicTaskId); } catch (Exception ignored) {}
            musicTaskId = -1;
        }
        if (musicMovementTaskId != -1) {
            try { Bukkit.getScheduler().cancelTask(musicMovementTaskId); } catch (Exception ignored) {}
            musicMovementTaskId = -1;
        }
        if (roundTaskId != -1) {
            try { Bukkit.getScheduler().cancelTask(roundTaskId); } catch (Exception ignored) {}
            roundTaskId = -1;
        }
        if (botMoveTaskId != -1) {
            try { Bukkit.getScheduler().cancelTask(botMoveTaskId); } catch (Exception ignored) {}
            botMoveTaskId = -1;
        }
        removeAllChairs();

        // Limpiar bots
        for (Villager bot : botEntities.values()) {
            if (bot != null && !bot.isDead()) bot.remove();
        }
        botEntities.clear();
        botUUIDs.clear();
        lastMusicPositions.clear();
        stillnessTicks.clear();
        illegalSitAttempts.clear();

        // Limpiar vagonetas y villagers residuales
        World w = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (w != null) {
            w.getEntities().stream()
                .filter(e -> e instanceof Minecart || e instanceof Villager)
                .forEach(Entity::remove);
        }
    }
}
