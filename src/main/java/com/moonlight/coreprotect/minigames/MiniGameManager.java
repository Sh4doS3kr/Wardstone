package com.moonlight.coreprotect.minigames;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.minigames.games.*;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

/**
 * Manager principal del sistema de minijuegos.
 * - Scheduler automático cada 1 hora
 * - Countdown con avisos en chat
 * - Selección aleatoria con animación de ruleta
 * - Gestión de inventario (guardar/restaurar)
 * - Gestión de joins
 */
public class MiniGameManager {

    private final CoreProtectPlugin plugin;
    private final MiniGameWorld miniGameWorld;

    // Estado
    private MiniGame currentGame = null;
    private MiniGameType selectedType = null;
    private boolean gameActive = false;
    private boolean joinPhase = false;
    private final Set<UUID> joinedPlayers = new LinkedHashSet<>();
    private final Map<UUID, SavedPlayerData> savedInventories = new HashMap<>();
    private final Map<UUID, Location> returnLocations = new HashMap<>();

    // Scheduler
    private int schedulerTaskId = -1;
    private long countdownSeconds = 3600; // 1 hora
    private boolean rouletteRunning = false;

    // Countdown warning thresholds (seconds)
    private static final long[] WARNINGS = {2700, 1800, 900, 600, 300, 180, 60, 30, 15, 10, 5, 3, 2, 1};
    private final Set<Long> warnedAt = new HashSet<>();

    public MiniGameManager(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        this.miniGameWorld = new MiniGameWorld(plugin);

        // Crear mundo
        miniGameWorld.getOrCreateWorld();

        // Iniciar scheduler
        startScheduler();
    }

    // ==========================================
    // SCHEDULER (cada 1 hora)
    // ==========================================

    private void startScheduler() {
        schedulerTaskId = new BukkitRunnable() {
            @Override
            public void run() {
                if (gameActive || joinPhase || rouletteRunning) return;

                countdownSeconds--;

                // Avisos en chat
                for (long threshold : WARNINGS) {
                    if (countdownSeconds == threshold && !warnedAt.contains(threshold)) {
                        warnedAt.add(threshold);
                        broadcastCountdown(threshold);
                    }
                }

                // ¡Iniciar!
                if (countdownSeconds <= 0) {
                    countdownSeconds = 1800; // Resetear inmediatamente para evitar triggers multiples
                    warnedAt.clear();
                    startJoinPhase(null);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L).getTaskId();

        plugin.getLogger().info("[MiniGames] Scheduler iniciado. Próximo minijuego en 30 minutos.");
    }

    private void broadcastCountdown(long seconds) {
        String timeStr;
        String color;

        if (seconds >= 60) {
            long min = seconds / 60;
            timeStr = min + " minuto" + (min != 1 ? "s" : "");
            color = seconds > 300 ? "§e" : "§c";
        } else {
            timeStr = seconds + " segundo" + (seconds != 1 ? "s" : "");
            color = "§c§l";
        }

        String msg = SmallCaps.convert("§5§lMINIJUEGOS §7- §f¡Empieza en " + color + timeStr + "§f! §7Usa §e/join §7para unirte.");
        Bukkit.broadcastMessage(msg);

        // Sonido
        Sound sound = seconds <= 5 ? Sound.BLOCK_NOTE_BLOCK_PLING : 
                       seconds <= 30 ? Sound.BLOCK_NOTE_BLOCK_BELL : 
                       Sound.BLOCK_NOTE_BLOCK_CHIME;
        float pitch = seconds <= 3 ? 2.0f : seconds <= 10 ? 1.5f : 1.0f;

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), sound, 1.0f, pitch);
        }

        // Títulos en los últimos 10 segundos
        if (seconds <= 10) {
            String titleColor = seconds <= 3 ? "§c" : "§e";
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendTitle(titleColor + "§l" + seconds, "§7¡Minijuego próximo! §e/join", 0, 25, 5);
            }
        }
    }

    // ==========================================
    // JOIN PHASE
    // ==========================================

    public void startJoinPhase(MiniGameType forcedType) {
        if (gameActive || joinPhase) return;

        joinPhase = true;
        joinedPlayers.clear();
        selectedType = forcedType; // null = será aleatorio

        // Anunciar fase de unión (30 segundos)
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(SmallCaps.convert("§5§l▍ §e§l¡MINIJUEGO EN 30 SEGUNDOS!"));
        Bukkit.broadcastMessage(SmallCaps.convert("§5§l▍ §7Usa §e/join §7o §e/minijuegos §7para unirte."));
        Bukkit.broadcastMessage("");

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle("§5§lMINIJUEGO", "§7Usa §e/join §7para unirte", 10, 60, 10);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);
        }

        // Timer de 30 segundos con countdown
        new BukkitRunnable() {
            int count = 30;
            @Override
            public void run() {
                if (!joinPhase) { cancel(); return; }

                if (count <= 0) {
                    cancel();
                    // Comprobar jugadores mínimos
                    if (joinedPlayers.size() < 2) {
                        Bukkit.broadcastMessage(SmallCaps.convert("§c§l✖ §cNo hay suficientes jugadores (mínimo 2). Minijuego cancelado."));
                        joinPhase = false;
                        rouletteRunning = false;
                        countdownSeconds = 1800;
                        warnedAt.clear();
                        return;
                    }
                    startRouletteAnimation();
                    return;
                }

                if (count == 20 || count == 10 || count <= 5) {
                    Bukkit.broadcastMessage(SmallCaps.convert("§5§lMINIJUEGO §7en §e" + count + "s §7| §fJugadores: §a" + joinedPlayers.size() + " §7| §e/join"));
                }
                if (count <= 5) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, count == 1 ? 2.0f : 1.0f);
                    }
                }
                count--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // ==========================================
    // ROULETTE ANIMATION
    // ==========================================

    private void startRouletteAnimation() {
        rouletteRunning = true;
        joinPhase = false;

        MiniGameType finalType;
        if (selectedType != null) {
            finalType = selectedType;
        } else {
            // Selección aleatoria
            MiniGameType[] types = MiniGameType.values();
            finalType = types[new Random().nextInt(types.length)];
        }

        MiniGameType[] allTypes = MiniGameType.values();

        // Animación de ruleta: títulos rápidos que desaceleran
        new BukkitRunnable() {
            int tick = 0;
            int totalTicks = 40; // ~4 segundos de animación
            int currentIndex = new Random().nextInt(allTypes.length);

            @Override
            public void run() {
                if (tick >= totalTicks) {
                    cancel();
                    rouletteRunning = false;

                    // Mostrar resultado final
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendTitle(finalType.getDisplayName(), finalType.getDescription(), 10, 80, 20);
                        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                    }

                    Bukkit.broadcastMessage("");
                    Bukkit.broadcastMessage(SmallCaps.convert("§5§l▍ " + finalType.getDisplayName()));
                    Bukkit.broadcastMessage(SmallCaps.convert("§5§l▍ §7" + finalType.getDescription()));
                    Bukkit.broadcastMessage("");

                    // Iniciar el juego tras 3 segundos
                    Bukkit.getScheduler().runTaskLater(plugin, () -> startGame(finalType), 60L);
                    return;
                }

                // Velocidad: rápido al inicio, lento al final
                int delay;
                if (tick < 15) delay = 2;       // Muy rápido
                else if (tick < 25) delay = 4;   // Rápido
                else if (tick < 32) delay = 6;   // Medio
                else if (tick < 37) delay = 10;  // Lento
                else delay = 15;                  // Muy lento

                // Solo mostrar en ciertos ticks según la velocidad
                if (tick % (delay / 2 + 1) == 0) {
                    MiniGameType show;
                    if (tick >= totalTicks - 3) {
                        show = finalType; // Los últimos siempre muestran el final
                    } else {
                        show = allTypes[currentIndex % allTypes.length];
                        currentIndex++;
                    }

                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendTitle(show.getColor() + "§l» " + show.getDisplayName() + " §l«", "§7Seleccionando...", 0, 10, 0);
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.0f + (tick * 0.02f));
                    }
                }

                tick++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    // ==========================================
    // GAME START
    // ==========================================

    private void startGame(MiniGameType type) {
        if (gameActive) return;

        World world = miniGameWorld.getOrCreateWorld();
        if (world == null) {
            Bukkit.broadcastMessage("§c§l✖ Error al crear el mundo de minijuegos.");
            countdownSeconds = 3600;
            return;
        }

        // Limpiar arena anterior
        plugin.getLogger().info("[MiniGames] Limpiando arena anterior...");
        miniGameWorld.clearArena();

        // Guardar inventarios y posiciones
        for (UUID uuid : joinedPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                // NO quitar fly aquí - se quita DESPUÉS del TP en MiniGame
                savePlayerData(p);
            }
        }

        // Crear instancia del minijuego
        currentGame = createGame(type);
        if (currentGame == null) {
            Bukkit.broadcastMessage("§c§l✖ Error al crear el minijuego.");
            restoreAllData();
            countdownSeconds = 3600;
            return;
        }

        gameActive = true;
        selectedType = type;

        // Iniciar el juego
        currentGame.start(joinedPlayers);
        plugin.getLogger().info("[MiniGames] Minijuego " + type.name() + " iniciado con " + joinedPlayers.size() + " jugadores.");
    }

    private MiniGame createGame(MiniGameType type) {
        switch (type) {
            case TNT_RUN: return new TNTRunGame(plugin, this);
            case SPLEEF: return new SpleefGame(plugin, this);
            case TNT_TAG: return new TNTTagGame(plugin, this);
            case BLOCK_PARTY: return new BlockPartyGame(plugin, this);
            case OITC: return new OITCGame(plugin, this);
            case SUMO: return new SumoGame(plugin, this);
            case SNOWBALL_FIGHT: return new SnowballFightGame(plugin, this);
            case MUSICAL_CHAIRS: return new MusicalChairsGame(plugin, this);
            case PARKOUR_RACE: return new ParkourRaceGame(plugin, this);
            case FLOOR_IS_LAVA: return new FloorIsLavaGame(plugin, this);
            case BEAST_ESCAPE: return new BeastEscapeGame(plugin, this);
            case MURDER_MYSTERY: return new MurderMysteryGame(plugin, this);
            case RED_LIGHT_GREEN_LIGHT: return new RedLightGreenLightGame(plugin, this);
            case YES_NO: return new YesNoGame(plugin, this);
            case ANVIL_RAIN: return new AnvilRainGame(plugin, this);
            case FARM_HUNT: return new FarmHuntGame(plugin, this);
            case BUILD_BATTLE: return new BuildBattleGame(plugin, this);
            case BUILD_BATTLE_CLASSIC: return new BuildBattleClassicGame(plugin, this);
            case PILLARS_OF_FORTUNE: return new PillarsOfFortuneGame(plugin, this);
            case BLACK_HOLE: return new BlackHoleGame(plugin, this);
            case FAKE_DEATHMATCH: return new FakeDeathmatchGame(plugin, this);
            case EYE_OF_STORM: return new EyeOfStormGame(plugin, this);
            default: return null;
        }
    }

    // ==========================================
    // GAME END
    // ==========================================

    public void endCurrentGame() {
        gameActive = false;
        joinPhase = false;
        rouletteRunning = false;
        currentGame = null;
        selectedType = null;
        joinedPlayers.clear();

        // Resetear countdown para próximo minijuego
        countdownSeconds = 1800;
        warnedAt.clear();

        // Limpiar TODO del mundo de minijuegos después de un pequeño delay
        // para asegurar que todos los jugadores ya fueron teleportados
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            fullWorldCleanup();
        }, 40L);

        plugin.getLogger().info("[MiniGames] Minijuego terminado. Próximo en 30 minutos.");
    }

    /**
     * Limpia ABSOLUTAMENTE TODO del mundo de minijuegos:
     * bloques, entidades, items en el suelo, chunks force-loaded, etc.
     * La limpieza de bloques es asíncrona por batches (delegada a clearArena).
     */
    private void fullWorldCleanup() {
        World world = miniGameWorld.getWorld();
        if (world == null) return;

        plugin.getLogger().info("[MiniGames] Iniciando limpieza completa del mundo...");

        // 1. Eliminar TODAS las entidades y rescatar jugadores stuck
        int entityCount = 0;
        for (org.bukkit.entity.Entity entity : world.getEntities()) {
            if (entity instanceof Player) {
                Player p = (Player) entity;
                p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
                p.sendMessage(SmallCaps.convert("§c§l⚠ §7Teletransportado al spawn (limpieza de minijuegos)."));
                plugin.getLogger().warning("[MiniGames] Jugador stuck durante limpieza: " + p.getName());
            } else {
                entity.remove();
                entityCount++;
            }
        }

        // 2. Resetear WorldBorder por si algún minijuego lo usó
        try {
            world.getWorldBorder().reset();
        } catch (Exception ignored) {}

        // 3. Resetear clima y hora
        world.setStorm(false);
        world.setThundering(false);
        world.setTime(6000);

        // 4. Limpiar bloques de la arena (async por batches, limpia TODOS los chunks cargados)
        miniGameWorld.clearArena();

        plugin.getLogger().info("[MiniGames] Limpieza iniciada: " + entityCount + " entidades eliminadas. Bloques limpiándose async...");
    }

    public void teleportAllBack() {
        // Capturar datos del ganador pendiente ANTES de limpiar
        final UUID pendingWinner = (currentGame != null) ? currentGame.getPendingWinnerUUID() : null;
        final MiniGame gameRef = currentGame;

        Map<UUID, Location> pending = new HashMap<>(returnLocations);
        plugin.getLogger().info("[MiniGames] Iniciando teleportación de " + pending.size() + " jugadores");
        
        // Teleportación inicial
        Set<UUID> restoredPlayers = new HashSet<>();
        for (Map.Entry<UUID, Location> entry : pending.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null && p.isOnline()) {
                plugin.getLogger().info("[MiniGames] Teleportando a: " + p.getName() + " desde " + p.getWorld().getName());
                // Primero sacar de spectator y limpiar estado
                p.setGameMode(GameMode.SURVIVAL);
                p.setFallDistance(0);
                p.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                p.setHealth(p.getMaxHealth());
                p.setFireTicks(0);
                p.setGlowing(false);
                p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
                try { p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard()); } catch (Exception ignored) {}
                // Limpiar cursor (item seleccionado del inventario) para evitar llevar items del minijuego
                p.setItemOnCursor(null);
                p.closeInventory();
                // Teleportar al overworld
                p.teleport(entry.getValue());
                p.setFallDistance(0); // Resetear de nuevo DESPUÉS del TP
                p.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                restorePlayerData(p);
                p.setGameMode(GameMode.SURVIVAL);
                restoreFlightIfPermitted(p);
                // Quitar disfraz de LibsDisguises si quedó (FarmHunt)
                try {
                    if (me.libraryaddict.disguise.DisguiseAPI.isDisguised(p)) {
                        me.libraryaddict.disguise.DisguiseAPI.undisguiseToAll(p);
                    }
                } catch (Exception ignored) {}
                p.setPlayerListName(p.getName());
                p.sendMessage("§a§l✔ §7Teletransportado de vuelta al mundo principal.");
                restoredPlayers.add(entry.getKey());
            } else {
                plugin.getLogger().warning("[MiniGames] Jugador offline, datos guardados para reconexión: " + entry.getKey());
            }
        }

        // Dar recompensas al ganador 2 segundos DESPUÉS de teleportarlo al mundo normal
        if (pendingWinner != null && gameRef != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player winner = Bukkit.getPlayer(pendingWinner);
                if (winner != null && winner.isOnline()) {
                    gameRef.giveRewards(winner);
                    plugin.getLogger().info("[MiniGames] Recompensas entregadas a " + winner.getName() + " tras TP al mundo normal.");
                } else {
                    plugin.getLogger().warning("[MiniGames] Ganador offline, no se pudieron dar recompensas: " + pendingWinner);
                }
            }, 40L);
        }

        // Safety retry 1: tras 2 segundos
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            int stuckCount = 0;
            for (Map.Entry<UUID, Location> entry : pending.entrySet()) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p != null && p.isOnline() && p.getWorld().getName().equals(MiniGameWorld.getWorldName())) {
                    stuckCount++;
                    p.setGameMode(GameMode.SURVIVAL);
                    p.setFallDistance(0);
                    p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
                    p.setItemOnCursor(null);
                    p.closeInventory();
                    p.teleport(entry.getValue());
                    restorePlayerData(p);
                    restoreFlightIfPermitted(p);
                    p.sendMessage("§c§l⚠ §7Teletransporte de seguridad aplicado.");
                    plugin.getLogger().warning("[MiniGames] Safety TP 1: " + p.getName() + " seguía en mundo minijuegos.");
                }
            }
            if (stuckCount > 0) {
                plugin.getLogger().info("[MiniGames] " + stuckCount + " jugadores necesitaban safety TP 1");
            }
        }, 40L);

        // Safety retry 2: tras 5 segundos (más agresivo)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            int stuckCount = 0;
            for (Map.Entry<UUID, Location> entry : pending.entrySet()) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p != null && p.isOnline() && p.getWorld().getName().equals(MiniGameWorld.getWorldName())) {
                    stuckCount++;
                    // Forzar teleportación más agresiva
                    p.setGameMode(GameMode.SURVIVAL);
                    p.setFallDistance(0);
                    p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
                    p.setItemOnCursor(null);
                    p.closeInventory();
                    p.teleport(entry.getValue());
                    restorePlayerData(p);
                    restoreFlightIfPermitted(p);
                    // Si sigue stuck, teleportar al spawn del mundo principal
                    if (p.getWorld().getName().equals(MiniGameWorld.getWorldName())) {
                        Location spawn = Bukkit.getWorlds().get(0).getSpawnLocation();
                        p.teleport(spawn);
                        p.sendMessage("§c§l⚠ §7Teletransportado al spawn (fallback).");
                        plugin.getLogger().warning("[MiniGames] Safety TP 2 (fallback): " + p.getName() + " al spawn.");
                    }
                }
            }
            if (stuckCount > 0) {
                plugin.getLogger().info("[MiniGames] " + stuckCount + " jugadores necesitaban safety TP 2");
            }
        }, 100L);

        // Safety retry 3: tras 10 segundos (último recurso)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            int stuckCount = 0;
            for (Map.Entry<UUID, Location> entry : pending.entrySet()) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p != null && p.isOnline() && p.getWorld().getName().equals(MiniGameWorld.getWorldName())) {
                    stuckCount++;
                    // Último recurso: kick y teleport al reconectar
                    String name = p.getName();
                    p.kickPlayer("§c§l⚠ §7Teletransportando de vuelta... reconecta.");
                    plugin.getLogger().severe("[MiniGames] Safety TP 3 (kick): " + name + " seguía atascado.");
                }
            }
            if (stuckCount > 0) {
                plugin.getLogger().severe("[MiniGames] " + stuckCount + " jugadores necesitaron kick de seguridad");
            }
        }, 200L);

        // Solo limpiar datos de jugadores que fueron restaurados con éxito
        // Los jugadores offline conservan sus datos para cuando reconecten (onJoin)
        for (UUID restored : restoredPlayers) {
            returnLocations.remove(restored);
            savedInventories.remove(restored);
        }
        if (!savedInventories.isEmpty()) {
            plugin.getLogger().info("[MiniGames] " + savedInventories.size() + " jugadores offline conservan datos guardados para reconexión.");
            // Persistir datos a disco por si el servidor se reinicia
            savePendingDataToDisk();
        }
    }

    public void forceStop() {
        if (currentGame != null && currentGame.isRunning()) {
            currentGame.forceStop();
        } else if (joinPhase) {
            joinPhase = false;
            rouletteRunning = false;
            Bukkit.broadcastMessage(SmallCaps.convert("§c§l✖ §cMinijuego cancelado por un administrador."));
            countdownSeconds = 3600;
            warnedAt.clear();
        }
    }

    // ==========================================
    // JOIN/LEAVE
    // ==========================================

    public boolean joinPlayer(Player player) {
        if (!joinPhase && !gameActive) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo hay ningún minijuego activo en este momento."));
            player.sendMessage(SmallCaps.convert("§7  Próximo minijuego en: §e" + formatTime(countdownSeconds)));
            return false;
        }

        if (gameActive) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cEl minijuego ya ha empezado. Espera al próximo."));
            return false;
        }

        if (joinedPlayers.contains(player.getUniqueId())) {
            player.sendMessage(SmallCaps.convert("§e§l! §eYa estás registrado para el minijuego."));
            return false;
        }

        joinedPlayers.add(player.getUniqueId());
        
        // Desactivar fly al unirse al minijuego
        player.setAllowFlight(false);
        player.setFlying(false);
        player.sendMessage(SmallCaps.convert("§c§l✖ §cModo vuelo desactivado en minijuegos."));
        
        player.sendMessage(SmallCaps.convert("§a§l✔ §a¡Te has unido al minijuego! §7(" + joinedPlayers.size() + " jugadores)"));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);

        // Anunciar
        Bukkit.broadcastMessage(SmallCaps.convert("§5§lMINIJUEGOS §f" + player.getName() + " §7se ha unido. §8(§a" + joinedPlayers.size() + "§8)"));

        return true;
    }

    public void leavePlayer(Player player) {
        UUID uid = player.getUniqueId();

        // Bloquear salida si el jugador está vivo en una partida activa (no espectando)
        if (gameActive && currentGame != null
                && currentGame.getAlivePlayers().contains(uid)
                && !currentGame.getSpectators().contains(uid)) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes salir mientras estés vivo en un minijuego."));
            return;
        }

        if (joinedPlayers.remove(uid)) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cHas salido del minijuego."));
        }

        if (gameActive && currentGame != null) {
            currentGame.getAlivePlayers().remove(player.getUniqueId());
            currentGame.getPlayers().remove(player.getUniqueId());

            // Limpiar estado del jugador completamente
            player.setGameMode(GameMode.SURVIVAL);
            player.setFallDistance(0);
            player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
            player.setHealth(player.getMaxHealth());
            player.setFireTicks(0);
            player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));
            player.setAllowFlight(false);
            player.setFlying(false);

            // Restaurar datos (inventario, etc.)
            if (savedInventories.containsKey(player.getUniqueId())) {
                restorePlayerData(player);
            }
            // Teleportar de vuelta
            if (returnLocations.containsKey(player.getUniqueId())) {
                player.teleport(returnLocations.remove(player.getUniqueId()));
            } else {
                // Fallback: spawn del mundo principal
                player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            }
            // restorePlayerData ya restaura el gamemode guardado
            restoreFlightIfPermitted(player);

            // Verificar condición de victoria (si no quedan jugadores, terminar)
            currentGame.checkWinCondition();
        }
    }

    // ==========================================
    // INVENTORY SAVE/RESTORE
    // ==========================================

    private void savePlayerData(Player player) {
        SavedPlayerData data = new SavedPlayerData();
        data.inventory = player.getInventory().getContents().clone();
        data.armor = player.getInventory().getArmorContents().clone();
        data.offhand = player.getInventory().getItemInOffHand().clone();
        data.health = player.getHealth();
        data.food = player.getFoodLevel();
        data.saturation = player.getSaturation();
        data.exp = player.getExp();
        data.level = player.getLevel();
        data.gameMode = player.getGameMode();
        data.effects = new ArrayList<>(player.getActivePotionEffects());
        data.allowFlight = player.getAllowFlight();

        savedInventories.put(player.getUniqueId(), data);
        returnLocations.put(player.getUniqueId(), player.getLocation().clone());
        // Inventario y estado se limpian DESPUÉS del TP en MiniGame.teleportPlayersToArena
    }

    private void restorePlayerData(Player player) {
        SavedPlayerData data = savedInventories.get(player.getUniqueId());
        if (data == null) return;

        player.getInventory().clear();
        player.getInventory().setContents(data.inventory);
        player.getInventory().setArmorContents(data.armor);
        player.getInventory().setItemInOffHand(data.offhand);
        player.setHealth(Math.min(data.health, player.getMaxHealth()));
        player.setFoodLevel(data.food);
        player.setSaturation(data.saturation);
        player.setExp(data.exp);
        player.setLevel(data.level);
        player.setGameMode(data.gameMode);
        player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));
        data.effects.forEach(e -> player.addPotionEffect(e));
        player.setAllowFlight(data.allowFlight);

        savedInventories.remove(player.getUniqueId());
    }

    /**
     * Re-habilita el vuelo si el jugador tiene el permiso essentials.fly
     * (comprado con esencias via LuckPerms temp permission).
     * Llamar DESPUÉS de restaurar datos del minijuego.
     */
    public void restoreFlightIfPermitted(Player player) {
        if (player.hasPermission("essentials.fly")) {
            player.setAllowFlight(true);
        }
    }

    private void restoreAllData() {
        for (UUID uuid : new HashSet<>(savedInventories.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                restorePlayerData(p);
                Location ret = returnLocations.remove(uuid);
                if (ret != null) p.teleport(ret);
            }
        }
    }

    // ==========================================
    // DISK PERSISTENCE (para jugadores que crashean)
    // ==========================================

    private File getPendingDataFolder() {
        File folder = new File(plugin.getDataFolder(), "pending_minigame_data");
        if (!folder.exists()) folder.mkdirs();
        return folder;
    }

    /**
     * Guarda inventarios pendientes de jugadores offline a disco.
     * Usa serialización de Bukkit para ItemStacks.
     */
    public void savePendingDataToDisk() {
        File folder = getPendingDataFolder();
        for (Map.Entry<UUID, SavedPlayerData> entry : savedInventories.entrySet()) {
            UUID uuid = entry.getKey();
            SavedPlayerData data = entry.getValue();
            File file = new File(folder, uuid.toString() + ".dat");
            try (BukkitObjectOutputStream out = new BukkitObjectOutputStream(new FileOutputStream(file))) {
                // Inventario
                out.writeInt(data.inventory.length);
                for (ItemStack item : data.inventory) out.writeObject(item);
                // Armadura
                out.writeInt(data.armor.length);
                for (ItemStack item : data.armor) out.writeObject(item);
                // Offhand
                out.writeObject(data.offhand);
                // Stats
                out.writeDouble(data.health);
                out.writeInt(data.food);
                out.writeFloat(data.saturation);
                out.writeFloat(data.exp);
                out.writeInt(data.level);
                out.writeUTF(data.gameMode.name());
                // Return location
                Location ret = returnLocations.get(uuid);
                if (ret != null) {
                    out.writeBoolean(true);
                    out.writeUTF(ret.getWorld() != null ? ret.getWorld().getName() : "world");
                    out.writeDouble(ret.getX());
                    out.writeDouble(ret.getY());
                    out.writeDouble(ret.getZ());
                    out.writeFloat(ret.getYaw());
                    out.writeFloat(ret.getPitch());
                } else {
                    out.writeBoolean(false);
                }
                plugin.getLogger().info("[MiniGames] Datos de " + uuid + " guardados a disco.");
            } catch (Exception e) {
                plugin.getLogger().severe("[MiniGames] Error guardando datos de " + uuid + ": " + e.getMessage());
            }
        }
    }

    /**
     * Carga datos pendientes de disco para un jugador específico que reconecta.
     * Retorna true si se encontraron y restauraron datos.
     */
    public boolean loadAndRestoreFromDisk(Player player) {
        UUID uuid = player.getUniqueId();
        File file = new File(getPendingDataFolder(), uuid.toString() + ".dat");
        if (!file.exists()) return false;

        try (BukkitObjectInputStream in = new BukkitObjectInputStream(new FileInputStream(file))) {
            // Inventario
            int invLen = in.readInt();
            ItemStack[] inventory = new ItemStack[invLen];
            for (int i = 0; i < invLen; i++) inventory[i] = (ItemStack) in.readObject();
            // Armadura
            int armorLen = in.readInt();
            ItemStack[] armor = new ItemStack[armorLen];
            for (int i = 0; i < armorLen; i++) armor[i] = (ItemStack) in.readObject();
            // Offhand
            ItemStack offhand = (ItemStack) in.readObject();
            // Stats
            double health = in.readDouble();
            int food = in.readInt();
            float saturation = in.readFloat();
            float exp = in.readFloat();
            int level = in.readInt();
            GameMode gameMode = GameMode.valueOf(in.readUTF());
            // Return location
            Location returnLoc = null;
            if (in.readBoolean()) {
                String worldName = in.readUTF();
                double x = in.readDouble(), y = in.readDouble(), z = in.readDouble();
                float yaw = in.readFloat(), pitch = in.readFloat();
                World world = Bukkit.getWorld(worldName);
                if (world != null) returnLoc = new Location(world, x, y, z, yaw, pitch);
            }

            // Aplicar datos
            player.getInventory().clear();
            player.getInventory().setContents(inventory);
            player.getInventory().setArmorContents(armor);
            player.getInventory().setItemInOffHand(offhand);
            player.setHealth(Math.min(health, player.getMaxHealth()));
            player.setFoodLevel(food);
            player.setSaturation(saturation);
            player.setExp(exp);
            player.setLevel(level);
            player.setGameMode(gameMode);
            player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));

            if (returnLoc != null) {
                player.teleport(returnLoc);
            } else {
                player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            }

            restoreFlightIfPermitted(player);

            // Borrar archivo ya usado
            file.delete();
            plugin.getLogger().info("[MiniGames] Datos de " + player.getName() + " restaurados desde disco.");
            player.sendMessage("§a§l✔ §7Tu inventario ha sido restaurado tras la desconexión durante el minijuego.");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("[MiniGames] Error cargando datos de " + uuid + ": " + e.getMessage());
            file.delete(); // Borrar archivo corrupto
            return false;
        }
    }

    // ==========================================
    // GETTERS
    // ==========================================

    public MiniGame getCurrentGame() { return currentGame; }
    public boolean isGameActive() { return gameActive; }
    public boolean isJoinPhase() { return joinPhase; }
    public Set<UUID> getJoinedPlayers() { return joinedPlayers; }
    public long getCountdownSeconds() { return countdownSeconds; }
    public Map<UUID, Location> getReturnLocations() { return returnLocations; }
    public Map<UUID, SavedPlayerData> getSavedInventories() { return savedInventories; }

    public void setCountdownSeconds(long seconds) {
        this.countdownSeconds = seconds;
        this.warnedAt.clear();
    }

    // ==========================================
    // SHUTDOWN
    // ==========================================

    public void shutdown() {
        if (schedulerTaskId != -1) {
            try { Bukkit.getScheduler().cancelTask(schedulerTaskId); } catch (Exception ignored) {}
        }
        if (currentGame != null && currentGame.isRunning()) {
            currentGame.forceStop();
        }
        // Restaurar inventarios de jugadores online
        restoreAllData();
        // Persistir datos de jugadores offline a disco
        if (!savedInventories.isEmpty()) {
            plugin.getLogger().info("[MiniGames] Guardando datos de " + savedInventories.size() + " jugadores offline a disco (shutdown)...");
            savePendingDataToDisk();
        }
    }

    // ==========================================
    // UTILITIES
    // ==========================================

    public String formatTime(long totalSeconds) {
        if (totalSeconds < 0) return "N/A";
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + String.format("%02d", seconds) + "s";
        return seconds + "s";
    }

    // ==========================================
    // INNER CLASS: Saved Player Data
    // ==========================================

    public static class SavedPlayerData {
        public ItemStack[] inventory;
        public ItemStack[] armor;
        public ItemStack offhand;
        public double health;
        public int food;
        public float saturation;
        public float exp;
        public int level;
        public GameMode gameMode;
        public java.util.Collection<org.bukkit.potion.PotionEffect> effects;
        public boolean allowFlight;
    }
}
