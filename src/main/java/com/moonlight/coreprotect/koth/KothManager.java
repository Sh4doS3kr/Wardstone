package com.moonlight.coreprotect.koth;

import com.moonlight.coreprotect.CoreProtectPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Gestor principal del sistema MoonKOTH.
 * 
 * DISEÑO COMPLETO:
 * - Mundo vacío (void) dedicado para KOTH
 * - Sistema de PUNTOS: captura zona = +1pt/s, kill = +5pt (configurable)
 * - Scoreboard premium en tiempo real con máxima prioridad
 * - Scheduler automático basado en horas del config
 * - BossBar global mostrando estado del KOTH
 * - Recompensas: Vault money + ExcellentCrates + items
 * - Partículas delimitando la zona de captura
 * - PlaceholderAPI para integración con otros plugins
 * 
 * Optimización: BukkitRunnable cada 20 ticks (1 segundo)
 */
public class KothManager {

    private final CoreProtectPlugin plugin;
    private final KothWorld kothWorldManager;
    private final KothScoreboard scoreboard;
    private final KothParticles particles;

    // Estado del KOTH
    private KothZone currentZone;
    private BossBar bossBar;
    private boolean active = false;

    // Límites del mapa (distintos a la zona de captura)
    private int mapX1, mapZ1, mapX2, mapZ2;
    private boolean keepInvActive = false;
    private int keepInvDelaySeconds = 0;

    // Registro de bloques colocados durante KOTH para restauración
    private final Map<Location, Material> placedBlocks = new HashMap<>();

    // Sistema de puntos
    private final Map<UUID, Integer> playerPoints = new HashMap<>();
    private final Map<UUID, Integer> playerKills = new HashMap<>();
    private int npcPoints = 0; // Puntos de los NPCs en modo cooperativo

    // Ubicaciones de retorno de jugadores
    private final Map<UUID, Location> returnLocations = new HashMap<>();

    // Registro de entrada (para protección temporal en el spawn)
    private final Map<UUID, Long> entryTimestamps = new HashMap<>();

    // Spawn del KOTH (donde se teletransportan los jugadores)
    private Location kothSpawn;

    // Centro del KOTH (Faro)
    private Location kothCenter;

    // Tasks
    private BukkitRunnable mainTask;
    private int mainTaskId = -1;
    private BukkitRunnable scheduleTask;
    private int scheduleTaskId = -1;
    private BukkitRunnable blinkTimer;
    private int blinkTimerId = -1;
    private BukkitRunnable postKothTimer;
    private int postKothTimerId = -1;

    // Schedule
    private List<LocalTime> weekdayTimes = new ArrayList<>();
    private List<LocalTime> weekendTimes = new ArrayList<>();
    private boolean schedulerWarned5min = false;
    private boolean schedulerWarned1min = false;
    private boolean schedulerWarned10min = false;
    private LocalTime postponedTime = null;
    private long schedulerCountdown = -1; // Segundos hasta el próximo KOTH (-1 = no programado)
    private boolean showExclamation = true; // Para el parpadeo del placeholder
    private boolean postKothMode = false; // Modo post-KOTH (1 min sin PvP)
    private int postKothSecondsRemaining = 0; // Segundos restantes del modo post-KOTH
    private KothMode currentMode = KothMode.CLASICO; // Modo actual del KOTH
    private KothNPC npcManager; // Gestor de NPCs para modo cooperativo
    private KothBoss kothBoss; // Boss final modo cooperativo
    private boolean bossTriggered = false; // Para no spawnear el boss dos veces
    private int pendingCustomDuration = 0; // Duración personalizada (minutos), 0 = usar config

    private KothConfig kothConfig;

    public KothManager(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        this.kothWorldManager = new KothWorld(plugin);
        this.scoreboard = new KothScoreboard();
        this.particles = new KothParticles(plugin);
        this.npcManager = new KothNPC(plugin);
        this.kothBoss = new KothBoss(plugin);
        this.kothConfig = new KothConfig(plugin);

        // Asegurar que el mundo koth esté cargado ANTES de la configuración
        // Si no se carga el mundo aquí, Bukkit.getWorld() devolverá null al obtener el
        // spawn/centro
        this.kothWorldManager.getOrCreateWorld();

        // Cargar configuración
        loadConfig();

        // Iniciar scheduler automático
        startScheduler();
    }

    /**
     * Carga toda la configuración del KOTH desde config.yml.
     */
    public void loadConfig() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("koth");
        if (section == null)
            return;

        // Cargar posiciones desde koth.json (no se pierden con reload)
        int x1 = kothConfig.getZoneX1();
        int z1 = kothConfig.getZoneZ1();
        int x2 = kothConfig.getZoneX2();
        int z2 = kothConfig.getZoneZ2();
        int duration = section.getInt("duration-minutes", 10) * 60;

        currentZone = new KothZone(x1, z1, x2, z2, duration);

        // Cargar límites del mapa desde JSON
        mapX1 = kothConfig.getMapX1();
        mapZ1 = kothConfig.getMapZ1();
        mapX2 = kothConfig.getMapX2();
        mapZ2 = kothConfig.getMapZ2();

        // Cargar spawn desde JSON
        kothSpawn = kothConfig.getSpawn();

        // Cargar centro (Faro) desde JSON
        kothCenter = kothConfig.getCenter();

        // Cargar horarios
        weekdayTimes.clear();
        weekendTimes.clear();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("H:mm");

        List<String> wTimes = section.getStringList("schedule.weekday-times");
        for (String t : wTimes) {
            try {
                weekdayTimes.add(LocalTime.parse(t, formatter));
            } catch (Exception e) {
                plugin.getLogger().warning("[KOTH] Hora weekday inválida: " + t);
            }
        }

        List<String> weTimes = section.getStringList("schedule.weekend-times");
        for (String t : weTimes) {
            try {
                weekendTimes.add(LocalTime.parse(t, formatter));
            } catch (Exception e) {
                plugin.getLogger().warning("[KOTH] Hora weekend inválida: " + t);
            }
        }

        // Si no hay nuevos, cargar legacy
        if (weekdayTimes.isEmpty() && weekendTimes.isEmpty()) {
            List<String> legacy = section.getStringList("schedule.times");
            for (String t : legacy) {
                try {
                    weekdayTimes.add(LocalTime.parse(t, formatter));
                } catch (Exception e) {
                }
            }
        }

        // FALLBACK: Si no hay NINGÚN horario, usar horarios por defecto
        if (weekdayTimes.isEmpty() && weekendTimes.isEmpty()) {
            plugin.getLogger().warning("[KOTH] No se encontraron horarios en config.yml. Usando horarios por defecto.");
            // Lunes a Viernes: 19:00 y 23:00
            weekdayTimes.add(LocalTime.of(19, 0));
            weekdayTimes.add(LocalTime.of(23, 0));
            // Fin de semana: 21:00 y 01:01
            weekendTimes.add(LocalTime.of(21, 0));
            weekendTimes.add(LocalTime.of(1, 1));
        }

        plugin.getLogger().info(
                "[KOTH] Configuración cargada. Horarios weekday: " + weekdayTimes + " | weekend: " + weekendTimes);
    }

    // ==========================================
    // SETUP DEL MUNDO KOTH
    // ==========================================

    /**
     * Crea el mundo KOTH vacío.
     * 
     * @return true si se creó correctamente
     */
    public boolean setupWorld() {
        World world = kothWorldManager.getOrCreateWorld();
        return world != null;
    }

    /**
     * Establece el spawn del KOTH.
     */
    public void setSpawn(Location location) {
        this.kothSpawn = location;
        kothConfig.setSpawn(location);
    }

    /**
     * Establece la zona de captura del KOTH.
     */
    public void setZone(int x1, int z1, int x2, int z2) {
        if (currentZone != null) {
            currentZone.setCoords(x1, z1, x2, z2);
        } else {
            int duration = plugin.getConfig().getInt("koth.duration-minutes", 10) * 60;
            currentZone = new KothZone(x1, z1, x2, z2, duration);
        }

        kothConfig.setZone(x1, z1, x2, z2);
    }

    // ==========================================
    // INICIO Y FIN DEL KOTH
    // ==========================================

    /**
     * Selecciona un modo KOTH aleatorio con animación de ruleta.
     * Los modos giran rápido en el Title y van frenando hasta parar en el ganador.
     */
    private void selectRandomKothMode() {
        KothMode[] modes = KothMode.values();
        java.util.Random rand = new java.util.Random();
        currentMode = modes[rand.nextInt(modes.length)];

        // Configurar modo inmediatamente (el resto del startKoth necesita currentMode)
        setupModeSpecifics();

        // Construir secuencia de ruleta que termine en el ganador
        java.util.List<KothMode> sequence = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            sequence.add(modes[i % modes.length]);
        }
        // Ajustar para que el último sea el ganador
        while (sequence.get(sequence.size() - 1) != currentMode) {
            sequence.add(modes[sequence.size() % modes.length]);
        }

        int totalSteps = sequence.size();
        long cumulativeDelay = 0;

        for (int i = 0; i < totalSteps; i++) {
            final KothMode showMode = sequence.get(i);
            final boolean isFinal = (i == totalSteps - 1);
            final int step = i;
            final int total = totalSteps;

            // Delays crecientes: rápido al inicio, lento al final (efecto ruleta)
            int tickDelay;
            if (i < 8)
                tickDelay = 2; // Muy rápido
            else if (i < 12)
                tickDelay = 3; // Rápido
            else if (i < 15)
                tickDelay = 5; // Medio
            else if (i < 18)
                tickDelay = 8; // Lento
            else
                tickDelay = 14; // Muy lento (casi parando)

            cumulativeDelay += tickDelay;
            final long delay = cumulativeDelay;

            new BukkitRunnable() {
                @Override
                public void run() {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.sendTitle( SmallCaps.convert("§6§l⚔ RULETA KOTH ⚔"), showMode.getTitle(), 0, 15, 0);

                        if (isFinal) {
                            // Sonido final de selección
                            player.playSound(player.getLocation(),
                                    org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                        } else {
                            // Sonido de "click" de ruleta con pitch creciente
                            float pitch = 0.5f + (1.5f * step / total);
                            player.playSound(player.getLocation(),
                                    org.bukkit.Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, pitch);
                        }
                    }
                }
            }.runTaskLater(plugin, delay);
        }

        // Revelación final después de la animación
        final long revealDelay = cumulativeDelay + 30;
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendTitle(currentMode.getTitle(),
                            "§f" + currentMode.getDescription(), 10, 60, 20);
                    player.playSound(player.getLocation(),
                            org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                }

                Bukkit.broadcastMessage("");
                Bukkit.broadcastMessage(SmallCaps.convert("§6§l⚔ §e§lMODO DE KOTH SELECCIONADO"));
                Bukkit.broadcastMessage(SmallCaps.convert("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                Bukkit.broadcastMessage(SmallCaps.convert(currentMode.getTitle() + " §f- " + currentMode.getDescription()));
                Bukkit.broadcastMessage("");
            }
        }.runTaskLater(plugin, revealDelay);
    }

    /**
     * Configura aspectos específicos según el modo de KOTH.
     */
    private void setupModeSpecifics() {
        World kothWorld = kothWorldManager.getWorld();
        if (kothWorld == null)
            return;

        switch (currentMode) {
            case NOCTURNO:
                // Configurar noche y visión limitada
                kothWorld.setTime(18000); // Noche
                kothWorld.setStorm(true); // Tormenta para reducir visibilidad

                // Aplicar ceguera a todos los jugadores en el mundo KOTH
                for (Player player : kothWorld.getPlayers()) {
                    player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.BLINDNESS, 999999, 0, false, false));
                    player.sendMessage(SmallCaps.convert("§8§l⚔ §fVisión limitada activada en modo nocturno."));
                }
                break;

            case COOPERATIVO:
                // Configurar día claro para mejor visibilidad
                kothWorld.setTime(6000); // Mediodía
                kothWorld.setStorm(false);
                kothWorld.setThundering(false);
                break;

            case CLASICO:
                // Configuración normal
                kothWorld.setTime(6000); // Mediodía
                kothWorld.setStorm(false);
                kothWorld.setThundering(false);
                break;

            case VALE_TODO:
                // Todo vale: día claro, sin restricciones de items
                kothWorld.setTime(6000);
                kothWorld.setStorm(false);
                kothWorld.setThundering(false);
                break;
        }
    }

    /**
     * Inicia el evento KOTH con modo específico o aleatorio.
     * 
     * @param mode Modo específico o null para aleatorio
     * @return true si se inició correctamente
     */
    /**
     * Inicia el KOTH con duración personalizada.
     * 
     * @param mode                  Modo a usar (null = aleatorio)
     * @param customDurationMinutes Duración en minutos (<=0 = usar config)
     */
    public boolean startKoth(KothMode mode, int customDurationMinutes) {
        if (active)
            return false;
        this.pendingCustomDuration = customDurationMinutes;
        return startKoth(mode);
    }

    public boolean startKoth(KothMode mode) {
        if (active)
            return false;

        // Establecer modo (específico o aleatorio)
        if (mode != null) {
            currentMode = mode;
            // Mostrar title con el modo seleccionado
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendTitle( SmallCaps.convert("§6§lELIGIENDO KOTH"), SmallCaps.convert("§e" + currentMode.getDisplayName()), 10, 60, 10);
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
            }

            // Anunciar en chat
            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage(SmallCaps.convert("§6§l⚔ §e§lMODO DE KOTH SELECCIONADO"));
            Bukkit.broadcastMessage(SmallCaps.convert("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            Bukkit.broadcastMessage(SmallCaps.convert(currentMode.getTitle() + " §f- " + currentMode.getDescription()));
            Bukkit.broadcastMessage("");

            // Configuraciones específicas del modo
            setupModeSpecifics();
        } else {
            // Elegir modo aleatorio
            selectRandomKothMode();
        }

        // Verificar que el mundo existe
        World kothWorld = kothWorldManager.getWorld();
        if (kothWorld == null) {
            kothWorld = kothWorldManager.getOrCreateWorld();
            if (kothWorld == null)
                return false;
        }

        // Verificar spawn
        if (kothSpawn == null) {
            kothSpawn = new Location(kothWorld, 0, 65, 0);
        }
        // Asegurar que el spawn usa el mundo correcto
        kothSpawn.setWorld(kothWorld);

        // Resetear estado
        active = true;
        playerPoints.clear();
        playerKills.clear();
        npcPoints = 0;
        bossTriggered = false;
        schedulerWarned5min = false;
        schedulerWarned1min = false;
        schedulerWarned10min = false;
        placedBlocks.clear();

        // Configurar KeepInventory inicial (Modo Seguridad)
        int delayMin = plugin.getConfig().getInt("koth.mechanics.keep-inventory-delay", 1);
        keepInvDelaySeconds = delayMin * 60;
        keepInvActive = true;

        if (kothWorld != null) {
            kothWorld.setGameRule(GameRule.KEEP_INVENTORY, true);
        }

        // Resetear zona (usar duración personalizada si se indicó)
        int cfgMinutes = plugin.getConfig().getInt("koth.duration-minutes", 10);
        int durationMinutes = (pendingCustomDuration > 0) ? pendingCustomDuration : cfgMinutes;
        pendingCustomDuration = 0; // Consumir
        int duration = durationMinutes * 60;
        currentZone.setTotalDuration(duration);
        currentZone.reset();
        currentZone.setActive(true);

        // Crear BossBar global (keyed para que sobreviva reloads)
        String kothName = plugin.getConfig().getString("koth.name", "MoonKOTH");
        org.bukkit.NamespacedKey kothKey = new org.bukkit.NamespacedKey(plugin, "koth_main");
        Bukkit.removeBossBar(kothKey);
        bossBar = Bukkit.createBossBar(
                kothKey,
                "§6§l⚔ " + kothName + " §8| §a¡Evento activo! §7Usa §f/koth",
                BarColor.YELLOW, BarStyle.SEGMENTED_10);
        bossBar.setProgress(1.0);
        for (Player p : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(p);
        }

        // Iniciar partículas
        particles.start(currentZone);

        // Limpiar TODAS las criaturas del mundo KOTH
        npcManager.clearNPCs();
        npcManager.killAllMobs(kothWorld);
        npcManager.resetSpawnState();

        // PvP: solo desactivar en cooperativo, activar en clásico/nocturno
        if (currentMode == KothMode.COOPERATIVO) {
            kothWorld.setPVP(false);
        } else {
            kothWorld.setPVP(true);
        }

        // Broadcast global en TODOS los mundos
        String startMsg = plugin.getConfig().getString("koth.messages.start",
                "§6§l⚔ §e§lMOONKOTH §6§l⚔ §f¡El evento KOTH ha comenzado! §7Usa §f/koth §7para unirte.");
        startMsg = startMsg.replace("{name}", kothName)
                .replace("{duration}", String.valueOf(duration / 60));
        Bukkit.broadcastMessage(startMsg);

        // Aviso KeepInventory
        String warnMsg = plugin.getConfig().getString("koth.messages.keep-inv-warn",
                "§a§l✔ §f¡Modo de seguridad activado! Tienes §e{minutes} minutos §fde §aKeepInventory§f.");
        warnMsg = warnMsg.replace("{minutes}", String.valueOf(delayMin));
        Bukkit.broadcastMessage(warnMsg);

        // Sonido a todos
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);
        }

        // Iniciar tarea principal (cada 1 segundo)
        startMainTask();

        plugin.getLogger().info("[KOTH] Evento iniciado. Duración: " + (duration / 60) + " minutos.");
        return true;
    }

    /**
     * Detiene el evento KOTH forzosamente.
     */
    public void stopKoth() {
        if (!active)
            return;

        active = false;
        currentZone.setActive(false);

        // Parar tareas
        stopMainTask();
        particles.stop();

        // Quitar BossBar
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }

        // Quitar scoreboards
        scoreboard.cleanup();

        // Teletransportar jugadores de vuelta
        teleportAllBack();

        // Limpiar boss si sigue activo
        if (kothBoss != null && kothBoss.isActive()) {
            kothBoss.forceKill();
        }

        // Limpiar puntos
        playerPoints.clear();
        playerKills.clear();
        returnLocations.clear();
        entryTimestamps.clear();

        // Limpiar NPCs y criaturas
        npcManager.clearNPCs();
        World kothWorldClean = kothWorldManager.getWorld();
        if (kothWorldClean != null) {
            npcManager.killAllMobs(kothWorldClean);
        }

        // Resetear Faro
        updateBeaconColor();

        // Broadcast
        String stopMsg = plugin.getConfig().getString("koth.messages.stop",
                "§6§l⚔ §c§lMOONKOTH §6§l⚔ §fEl evento KOTH ha sido detenido.");
        Bukkit.broadcastMessage(stopMsg);

        plugin.getLogger().info("[KOTH] Evento detenido.");
    }

    /**
     * Finaliza el KOTH naturalmente (tiempo acabado) y da recompensas.
     */
    private void endKoth() {
        if (!active)
            return;

        // SIEMPRE limpiar NPCs al finalizar
        npcManager.clearNPCs();
        World kothWorldEnd = kothWorldManager.getWorld();
        if (kothWorldEnd != null) {
            npcManager.killAllMobs(kothWorldEnd);
        }

        // Determinar ganador según el modo
        Map.Entry<String, Integer> winner = null;
        String kothName = plugin.getConfig().getString("koth.name", "MoonKOTH");

        switch (currentMode) {
            case COOPERATIVO:
                // Comparar puntos: Humanos vs NPCs
                int totalHuman = getTotalHumanPoints();
                if (totalHuman > npcPoints) {
                    // Humanos ganan
                    giveCooperativeRewards();
                    Bukkit.broadcastMessage("§a§l⚔ §e§l¡VICTORIA COOPERATIVA! §a☺ Humanos §f" + totalHuman
                            + " pts §7vs §c☠ NPCs §f" + npcPoints + " pts");
                } else if (npcPoints > totalHuman) {
                    // NPCs ganan
                    Bukkit.broadcastMessage("§4§l💀 §c§l¡DERROTA! §cLos NPCs ganan §f" + npcPoints
                            + " pts §7vs §aHumanos §f" + totalHuman + " pts");
                } else {
                    // Empate
                    Bukkit.broadcastMessage(SmallCaps.convert("§e§l⚔ §e§l¡EMPATE! §fHumanos y NPCs con §e" + totalHuman + " pts"));
                }
                endKothCommon(null);
                return;

            case CLASICO:
            case NOCTURNO:
            case VALE_TODO:
                winner = getLeader();
                break;
        }

        if (winner != null) {
            // Dar recompensas al ganador
            Player winnerPlayer = Bukkit.getPlayer(winner.getKey());
            String winnerName = winnerPlayer != null ? winnerPlayer.getName() : winner.getKey();

            // Mensaje de victoria según el modo
            String winMsg;
            if (currentMode == KothMode.NOCTURNO) {
                winMsg = "§8§l⚔ §a§l¡GANADOR NOCTURNO! §f¡{player} domina la oscuridad de §e{name}§f! §8§l⚔";
            } else if (currentMode == KothMode.VALE_TODO) {
                winMsg = "§c§l⚔ §4§l¡GANADOR VALE TODO! §f¡{player} ha dominado sin reglas en §e{name}§f! §c§l⚔";
            } else {
                winMsg = plugin.getConfig().getString("koth.messages.captured",
                        "§6§l⚔ §a§l¡HABEMUS GANADOR! §f¡{player} se ha llevado la corona de §e{name}§f! §6§l⚔");
            }
            winMsg = winMsg.replace("{player}", winnerName)
                    .replace("{name}", kothName)
                    .replace("{points}", String.valueOf(winner.getValue()));
            Bukkit.broadcastMessage(winMsg);

            if (winnerPlayer != null && winnerPlayer.isOnline()) {
                giveRewards(winnerPlayer);
            }

            // También dar recompensas menores al top 2 y 3
            List<Map.Entry<String, Integer>> topPlayers = getTopPlayers(3);
            for (int i = 1; i < topPlayers.size(); i++) {
                Player topPlayer = Bukkit.getPlayer(topPlayers.get(i).getKey());
                if (topPlayer != null && topPlayer.isOnline()) {
                    giveRunnerUpRewards(topPlayer, i + 1);
                }
            }

            // Sonido de victoria
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }

            // Animación de victoria en el centro
            if (kothCenter != null) {
                new KothVictoryAnimation(plugin).play(kothCenter);
            }
        } else {
            String modeMsg = currentMode == KothMode.NOCTURNO ? "§6§l⚔ §7El KOTH §enocturno §7ha terminado sin ganador."
                    : "§6§l⚔ §7El KOTH §e" + kothName + " §7ha terminado sin ganador.";
            Bukkit.broadcastMessage(modeMsg);
        }

        endKothCommon(winner);
    }

    /**
     * Da recompensas cooperativas a todos los jugadores participantes.
     */
    private void giveCooperativeRewards() {
        World kothWorld = kothWorldManager.getWorld();
        if (kothWorld == null)
            return;

        for (Player player : kothWorld.getPlayers()) {
            if (player.isOnline() && player.getGameMode() == org.bukkit.GameMode.SURVIVAL) {
                // Recompensa base por participar
                giveRewards(player);

                // Mensaje personalizado
                player.sendMessage(SmallCaps.convert("§a§l⚔ §f¡Has contribuido a derrotar a los NPCs! §eRecompensa recibida."));
            }
        }
    }

    /**
     * Común para todos los finales de KOTH.
     */
    private void endKothCommon(Map.Entry<String, Integer> winner) {

        // Actualizar BossBar
        if (bossBar != null) {
            if (winner != null) {
                bossBar.setTitle(SmallCaps.convert("§6§l⚔ §a¡" + winner.getKey() + " ganó!"));
                bossBar.setColor(BarColor.GREEN);
            } else if (currentMode == KothMode.COOPERATIVO) {
                int totalH = getTotalHumanPoints();
                if (totalH > npcPoints) {
                    bossBar.setTitle(SmallCaps.convert("§6§l⚔ §a§l¡VICTORIA COOPERATIVA! §a☺ " + totalH + " §7vs §c☠ " + npcPoints));
                    bossBar.setColor(BarColor.GREEN);
                } else {
                    bossBar.setTitle(SmallCaps.convert("§6§l⚔ §c§l¡DERROTA! §cNPCs " + npcPoints + " §7vs §aHumanos " + totalH));
                    bossBar.setColor(BarColor.RED);
                }
            } else {
                bossBar.setTitle(SmallCaps.convert("§6§l⚔ §7Sin ganador"));
                bossBar.setColor(BarColor.WHITE);
            }
            bossBar.setProgress(0);
        }

        // Limpiar NPCs antes del post-KOTH
        npcManager.clearNPCs();
        World kothWorldPost = kothWorldManager.getWorld();
        if (kothWorldPost != null) {
            npcManager.killAllMobs(kothWorldPost);
        }

        // Siempre iniciar modo post-KOTH (1 minuto para recoger items)
        startPostKothMode();
    }

    /**
     * Inicia el modo post-KOTH: 1 minuto sin PvP para recoger items.
     */
    private void startPostKothMode() {
        postKothMode = true;
        postKothSecondsRemaining = 60; // 1 minuto

        // Desactivar PvP en post-KOTH para TODOS los modos (es tiempo de recoger items)
        World kothWorld = kothWorldManager.getWorld();
        if (kothWorld != null) {
            kothWorld.setPVP(false);
        }

        // Actualizar BossBar para mostrar cuenta regresiva
        if (bossBar != null) {
            bossBar.setTitle(SmallCaps.convert("§6§l⚔ ¡GANADOR! §8| §cPvP desactivado §8| §e60s para limpieza"));
            bossBar.setColor(BarColor.BLUE);
            bossBar.setProgress(1.0);
        }

        // Anunciar inicio del modo post-KOTH
        Bukkit.broadcastMessage(SmallCaps.convert("§6§l⚔ §e§lMODO POST-KOTH INICIADO"));
        Bukkit.broadcastMessage(SmallCaps.convert("§fTienes §e1 minuto §fpara recoger items sin PvP."));
        Bukkit.broadcastMessage(SmallCaps.convert("§7Después de 1 minuto, todos serán expulsados y el mundo se limpiará."));

        // Iniciar temporizador de 1 minuto
        postKothTimer = new BukkitRunnable() {
            @Override
            public void run() {
                postKothSecondsRemaining--;

                // Actualizar BossBar y scoreboard cada segundo
                updatePostKothDisplay();

                if (postKothSecondsRemaining <= 0) {
                    // Terminar modo post-KOTH y limpiar
                    endPostKothMode();
                    cancel();
                }
            }
        };
        postKothTimerId = postKothTimer.runTaskTimer(plugin, 20L, 20L).getTaskId(); // Cada segundo
    }

    /**
     * Actualiza la visualización del modo post-KOTH (BossBar y scoreboard).
     */
    private void updatePostKothDisplay() {
        // Actualizar BossBar
        if (bossBar != null) {
            String title = String.format("§6§l⚔ ¡GANADOR! §8| §cPvP desactivado §8| §e%ds para limpieza",
                    postKothSecondsRemaining);
            bossBar.setTitle(title);
            bossBar.setProgress((double) postKothSecondsRemaining / 60.0);
        }

        // Actualizar scoreboard para mostrar tiempo restante
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().getName().equals(KothWorld.getWorldName())) {
                // Mostrar en scoreboard personalizado
                player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
                org.bukkit.scoreboard.Scoreboard board = player.getScoreboard();
                org.bukkit.scoreboard.Objective objective = board.registerNewObjective("postkoth", "dummy",
                        "§6§lPOST-KOTH");
                objective.setDisplaySlot(org.bukkit.scoreboard.DisplaySlot.SIDEBAR);

                objective.getScore("§6§l⚔ POST-KOTH ⚔").setScore(10);
                objective.getScore("§f").setScore(9);
                objective.getScore("§eTiempo restante:").setScore(8);
                objective.getScore("§c" + postKothSecondsRemaining + "s").setScore(7);
                objective.getScore("§7").setScore(6);
                objective.getScore("§cPvP: §c§lDESACTIVADO").setScore(5);
                objective.getScore("§7").setScore(4);
                objective.getScore("§fRecoge tus items!").setScore(3);
                objective.getScore("§7").setScore(2);
                objective.getScore("§eMoonKOTH §7v1.0").setScore(1);
            }
        }
    }

    /**
     * Termina el modo post-KOTH, expulsa a todos y limpia el mundo.
     */
    private void endPostKothMode() {
        postKothMode = false;

        // Anunciar fin del modo post-KOTH
        Bukkit.broadcastMessage(SmallCaps.convert("§6§l⚔ §c§lMODO POST-KOTH TERMINADO"));
        Bukkit.broadcastMessage(SmallCaps.convert("§fExpulsando jugadores y limpiando el mundo..."));

        // Expulsar a todos los jugadores del mundo KOTH
        World kothWorld = kothWorldManager.getWorld();
        if (kothWorld != null) {
            for (Player player : kothWorld.getPlayers()) {
                // Teletransportar al spawn principal
                org.bukkit.Location spawn = Bukkit.getWorlds().get(0).getSpawnLocation();
                player.teleport(spawn);
                player.sendMessage(SmallCaps.convert("§6§l⚔ §fHas sido expulsado del mundo KOTH."));
            }

            // Eliminar todos los items del mundo KOTH
            for (org.bukkit.entity.Entity entity : kothWorld.getEntities()) {
                if (entity instanceof org.bukkit.entity.Item) {
                    entity.remove();
                }
            }

        }

        // Limpiar el KOTH completamente
        cleanupKoth();
    }

    /**
     * Verifica si un bloque es natural del KOTH (no debe ser eliminado).
     */
    private boolean isNaturalKothBlock(org.bukkit.Material material) {
        return material == org.bukkit.Material.BEDROCK ||
                material == org.bukkit.Material.BEACON ||
                material == org.bukkit.Material.IRON_BLOCK ||
                material == org.bukkit.Material.GOLD_BLOCK ||
                material == org.bukkit.Material.DIAMOND_BLOCK ||
                material == org.bukkit.Material.EMERALD_BLOCK;
    }

    /**
     * Limpia completamente el KOTH.
     */
    private void cleanupKoth() {
        active = false;
        if (currentZone != null) {
            currentZone.setActive(false);
        }
        stopMainTask();
        particles.stop();
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
        scoreboard.cleanup();
        teleportAllBack();
        playerPoints.clear();
        playerKills.clear();
        returnLocations.clear();
        entryTimestamps.clear();

        // SIEMPRE limpiar NPCs y criaturas
        npcManager.clearNPCs();
        World kothWorldCleanup = kothWorldManager.getWorld();
        if (kothWorldCleanup != null) {
            npcManager.killAllMobs(kothWorldCleanup);
            kothWorldCleanup.setPVP(true); // Restaurar PvP
        }

        // Resetear Faro
        updateBeaconColor();

        // Recalcular countdown para que el timer muestre el próximo evento correcto
        recalculateCountdown();
    }

    // ==========================================
    // TAREA PRINCIPAL (CADA 1 SEG)
    // ==========================================

    /**
     * Tarea principal del KOTH. Se ejecuta cada 20 ticks (1 segundo).
     * Gestiona: puntos, scoreboard, BossBar, detección de zona, tiempo.
     */
    private void startMainTask() {
        stopMainTask();

        mainTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!active || currentZone == null) {
                    cancel();
                    return;
                }

                // Tick del tiempo
                boolean timeUp = currentZone.tickTime();
                if (timeUp) {
                    endKoth();
                    cancel();
                    return;
                }

                // Manejar fin del modo de seguridad (KeepInventory)
                if (keepInvActive && keepInvDelaySeconds > 0) {
                    keepInvDelaySeconds--;
                    if (keepInvDelaySeconds <= 0) {
                        keepInvActive = false;
                        World world = kothWorldManager.getWorld();
                        if (world != null) {
                            world.setGameRule(GameRule.KEEP_INVENTORY, false);
                        }
                        String msg = plugin.getConfig().getString("koth.messages.keep-inv-off",
                                "§c§l⚠ §f¡Modo de seguridad terminado! §cKeepInventory §fdeshabilitado.");
                        Bukkit.broadcastMessage(msg);
                    }
                }

                // Detectar jugadores en la zona de captura
                Map<String, List<Player>> teamsInZone = getTeamsInZone();
                int timeRemaining = currentZone.getTimeRemaining();

                // === MODO COOPERATIVO: NPCs vs Humanos por puntos ===
                if (currentMode == KothMode.COOPERATIVO) {
                    // Contar humanos y NPCs en la zona
                    int humansInZone = 0;
                    for (List<Player> team : teamsInZone.values()) {
                        humansInZone += team.size();
                    }
                    int npcsInZone = npcManager.countCapturersInZone();
                    int capturePoints = plugin.getConfig().getInt("koth.points.per-second", 1);

                    if (humansInZone > 0) {
                        // HUMANOS capturando
                        if (currentZone.getState() != KothZone.KothState.CAPTURING ||
                                !"§aHumanos".equals(currentZone.getCapturingTeam())) {
                            broadcastToKothWorld("§6§l⚔ §a§l☺ Humanos §festán capturando la zona!");
                        }
                        currentZone.setState(KothZone.KothState.CAPTURING);
                        currentZone.setCapturingTeam("§aHumanos");
                        // Dar puntos a todos los humanos en zona
                        for (List<Player> team : teamsInZone.values()) {
                            for (Player p : team) {
                                addPoints(p, capturePoints);
                            }
                        }
                    } else if (npcsInZone > 0) {
                        // NPCs capturando
                        if (currentZone.getState() != KothZone.KothState.CAPTURING ||
                                !"§cNPCs".equals(currentZone.getCapturingTeam())) {
                            broadcastToKothWorld("§6§l⚔ §c§l☠ NPCs §festán capturando la zona! §7¡Detenlos!");
                        }
                        currentZone.setState(KothZone.KothState.CAPTURING);
                        currentZone.setCapturingTeam("§cNPCs");
                        npcPoints += capturePoints * npcsInZone;
                    } else {
                        // Nadie en zona
                        currentZone.setState(KothZone.KothState.IDLE);
                        currentZone.setCapturingTeam(null);
                    }

                } else {
                    // === MODOS CLASICO / NOCTURNO ===
                    if (teamsInZone.isEmpty()) {
                        currentZone.setState(KothZone.KothState.IDLE);
                        currentZone.setCapturingTeam(null);

                    } else if (teamsInZone.size() == 1) {
                        Map.Entry<String, List<Player>> entry = teamsInZone.entrySet().iterator().next();

                        if (entry.getValue().size() == 1) {
                            Player capturer = entry.getValue().iterator().next();
                            String capturerName = capturer.getName();

                            if (currentZone.getState() != KothZone.KothState.CAPTURING ||
                                    !capturerName.equals(currentZone.getCapturingTeam())) {
                                String msg = plugin.getConfig().getString("koth.messages.capturing",
                                        "§6§l⚔ §a{team} §festá capturando el KOTH.");
                                msg = msg.replace("{team}", capturerName);
                                broadcastToKothWorld(msg);
                            }

                            currentZone.setState(KothZone.KothState.CAPTURING);
                            currentZone.setCapturingTeam(capturerName);

                            int capturePointsNormal = plugin.getConfig().getInt("koth.points.per-second", 1);
                            for (Player p : entry.getValue()) {
                                addPoints(p, capturePointsNormal);
                            }
                        } else {
                            if (currentZone.getState() != KothZone.KothState.CONTESTED) {
                                String msg = plugin.getConfig().getString("koth.messages.contested",
                                        "§6§l⚔ §c§l¡HAY BOCHINCHE! §fMúltiples jugadores luchan por el KOTH!");
                                broadcastToKothWorld(msg);
                            }
                            currentZone.setState(KothZone.KothState.CONTESTED);
                        }
                    } else {
                        if (currentZone.getState() != KothZone.KothState.CONTESTED) {
                            String msg = plugin.getConfig().getString("koth.messages.contested",
                                    "§6§l⚔ §c§l¡HAY BOCHINCHE! §fMúltiples jugadores luchan por el KOTH!");
                            broadcastToKothWorld(msg);
                        }
                        currentZone.setState(KothZone.KothState.CONTESTED);
                    }
                } // fin else modos normales

                // Actualizar Faro y BossBar
                updateBeaconColor();
                updateBossBar(timeRemaining);

                // Actualizar scoreboard para cada jugador en el mundo KOTH
                updateAllScoreboards(timeRemaining);

                // Avisos de tiempo restante
                if (timeRemaining == 60) {
                    Bukkit.broadcastMessage(SmallCaps.convert("§6§l⚔ §c¡1 MINUTO §frestante en el KOTH!"));
                } else if (timeRemaining == 30) {
                    Bukkit.broadcastMessage(SmallCaps.convert("§6§l⚔ §c§l¡30 SEGUNDOS §frestantes!"));
                } else if (timeRemaining <= 5 && timeRemaining > 0) {
                    Bukkit.broadcastMessage(SmallCaps.convert("§6§l⚔ §c§l" + timeRemaining + "..."));
                }

                // Boss Final a 2 minutos en modo COOPERATIVO
                if (currentMode == KothMode.COOPERATIVO && timeRemaining == 120 && !bossTriggered) {
                    bossTriggered = true;
                    World bossWorld = kothWorldManager.getWorld();
                    double bossY = (kothSpawn != null) ? kothSpawn.getY() : 65;
                    Location zoneCenter = (bossWorld != null) ? new Location(bossWorld,
                            (currentZone.getX1() + currentZone.getX2()) / 2.0,
                            bossY,
                            (currentZone.getZ1() + currentZone.getZ2()) / 2.0) : null;
                    if (zoneCenter != null) {
                        kothBoss.setOnDeathCallback(() -> {
                            // Bonus de puntos al matar al boss
                            for (UUID uuid : playerPoints.keySet()) {
                                Player bp = Bukkit.getPlayer(uuid);
                                if (bp != null && bp.isOnline()) {
                                    addPoints(bp, 50);
                                    bp.sendMessage(SmallCaps.convert("§6§l⚔ §e+50 puntos §fpor derrotar al boss!"));
                                }
                            }
                        });
                        kothBoss.summon(zoneCenter, currentZone);
                    }
                }
            }
        };

        mainTaskId = mainTask.runTaskTimer(plugin, 20L, 20L).getTaskId();
    }

    private void stopMainTask() {
        if (mainTaskId != -1) {
            try {
                plugin.getServer().getScheduler().cancelTask(mainTaskId);
            } catch (Exception ignored) {
            }
            mainTaskId = -1;
        }
    }

    // ==========================================
    // SCHEDULER AUTOMÁTICO
    // ==========================================

    /**
     * Inicia el scheduler automático del KOTH.
     * Sistema basado en COUNTDOWN: calcula los segundos hasta el próximo evento,
     * los decrementa cada segundo, y GARANTIZA el inicio cuando llega a 0.
     */
    private void startScheduler() {
        // Siempre iniciar si hay horarios (del config o fallback de loadConfig)
        if (weekdayTimes.isEmpty() && weekendTimes.isEmpty()) {
            plugin.getLogger().info("[KOTH] Scheduler no iniciado: sin horarios definidos.");
            return;
        }

        plugin.getLogger().info("[KOTH] Horarios weekday: " + weekdayTimes);
        plugin.getLogger().info("[KOTH] Horarios weekend: " + weekendTimes);

        // Calcular countdown inicial
        recalculateCountdown();
        plugin.getLogger().info("[KOTH] Scheduler iniciado. Countdown: " + schedulerCountdown + "s");

        scheduleTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Si ya hay un KOTH activo, recalcular cuando termine
                if (active) {
                    schedulerWarned5min = false;
                    schedulerWarned1min = false;
                    schedulerWarned10min = false;
                    schedulerCountdown = -1;
                    return;
                }

                // Si no hay countdown activo (post-KOTH o sin horarios), recalcular
                if (schedulerCountdown < 0) {
                    recalculateCountdown();
                    if (schedulerCountdown < 0)
                        return; // Sin horarios
                }

                // Decrementar
                schedulerCountdown--;

                // === AVISOS ===
                if (schedulerCountdown == 600) { // 10 min
                    if (!schedulerWarned10min) {
                        schedulerWarned10min = true;
                        Bukkit.broadcastMessage(
                                "§6§l⚔ §e§lMOONKOTH §6§l⚔ §f¡Empieza en §e10 min§f! §7Usa §f/koth");
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 0.8f);
                        }
                    }
                } else if (schedulerCountdown == 300) { // 5 min
                    if (!schedulerWarned5min) {
                        schedulerWarned5min = true;
                        Bukkit.broadcastMessage(
                                "§6§l⚔ §e§lMOONKOTH §6§l⚔ §f¡Empieza en §e5 min§f! §7Usa §f/koth");
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
                        }
                    }
                } else if (schedulerCountdown == 60) { // 1 min
                    if (!schedulerWarned1min) {
                        schedulerWarned1min = true;
                        Bukkit.broadcastMessage(
                                "§6§l⚔ §e§lMOONKOTH §6§l⚔ §f¡Solo queda §c1 min§f! Prepárate con §f/koth");
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);
                        }
                    }
                }

                // === INICIO ===
                if (schedulerCountdown <= 0) {
                    schedulerWarned5min = false;
                    schedulerWarned1min = false;
                    schedulerWarned10min = false;

                    // Requisito de jugadores mínimos
                    int minPlayers = plugin.getConfig().getInt("koth.schedule.min-players", 5);
                    if (Bukkit.getOnlinePlayers().size() < minPlayers) {
                        postponedTime = LocalTime.now().plusMinutes(30);
                        schedulerCountdown = 30 * 60; // Re-intentar en 30 min
                        Bukkit.broadcastMessage(
                                "§6§l⚔ §cEl KOTH se ha pospuesto §e30m §chasta que haya al menos §e" + minPlayers
                                        + " §cpersonas online.");
                        plugin.getLogger().info("[KOTH] Pospuesto 30min por falta de jugadores ("
                                + Bukkit.getOnlinePlayers().size() + "/" + minPlayers + ")");
                        return;
                    }

                    postponedTime = null;
                    plugin.getLogger().info("[KOTH] ¡Countdown llegó a 0! Iniciando KOTH automáticamente...");
                    boolean started = startKoth(null);
                    if (started) {
                        plugin.getLogger().info("[KOTH] KOTH iniciado exitosamente por el scheduler.");
                    } else {
                        plugin.getLogger().warning("[KOTH] No se pudo iniciar el KOTH. Re-intentando en 60s...");
                        schedulerCountdown = 60; // Re-intentar en 1 min
                    }
                }
            }
        };

        // CADA 1 SEGUNDO (20 ticks) — preciso y confiable
        scheduleTaskId = scheduleTask.runTaskTimer(plugin, 20L, 20L).getTaskId();

        // Iniciar temporizador de parpadeo para exclamaciones (cada 5 segundos)
        startBlinkTimer();

        // Iniciar auto-guardado del mundo KOTH (cada 5 minutos)
        startWorldAutoSave();
    }

    /**
     * Calcula los segundos exactos hasta el próximo evento KOTH programado
     * y los guarda en schedulerCountdown.
     */
    private void recalculateCountdown() {
        LocalTime now = LocalTime.now();
        DayOfWeek today = java.time.LocalDate.now().getDayOfWeek();
        boolean isWeekend = (today == DayOfWeek.SATURDAY || today == DayOfWeek.SUNDAY);
        List<LocalTime> todayTimes = isWeekend ? weekendTimes : weekdayTimes;

        // Incluir pospuesto si existe
        List<LocalTime> checkTimes = new ArrayList<>(todayTimes);
        if (postponedTime != null) {
            checkTimes.add(postponedTime);
        }

        // Buscar el próximo horario HOY (que aún no haya pasado)
        LocalTime nextToday = null;
        for (LocalTime t : checkTimes) {
            if (t.isAfter(now)) {
                if (nextToday == null || t.isBefore(nextToday)) {
                    nextToday = t;
                }
            }
        }

        if (nextToday != null) {
            schedulerCountdown = java.time.Duration.between(now, nextToday).getSeconds();
            plugin.getLogger().info("[KOTH] Próximo KOTH hoy a las " + nextToday + " (en " + schedulerCountdown + "s)");
            return;
        }

        // No hay más hoy, calcular para mañana
        DayOfWeek tomorrow = today.plus(1);
        boolean tomorrowWeekend = (tomorrow == DayOfWeek.SATURDAY || tomorrow == DayOfWeek.SUNDAY);
        List<LocalTime> tomorrowTimes = tomorrowWeekend ? weekendTimes : weekdayTimes;

        if (tomorrowTimes.isEmpty()) {
            schedulerCountdown = -1;
            return;
        }

        LocalTime firstTomorrow = tomorrowTimes.stream().sorted().findFirst().orElse(null);
        if (firstTomorrow == null) {
            schedulerCountdown = -1;
            return;
        }

        long secsUntilMidnight = java.time.Duration.between(now, LocalTime.MAX).getSeconds() + 1;
        long secsFromMidnight = java.time.Duration.between(LocalTime.MIN, firstTomorrow).getSeconds();
        schedulerCountdown = secsUntilMidnight + secsFromMidnight;
        plugin.getLogger()
                .info("[KOTH] Próximo KOTH mañana a las " + firstTomorrow + " (en " + schedulerCountdown + "s)");
    }

    /**
     * Obtiene los segundos restantes del countdown del scheduler.
     * Usado por placeholders y getNextScheduledTime().
     */
    public long getSchedulerCountdown() {
        return schedulerCountdown;
    }

    private void stopScheduler() {
        if (scheduleTaskId != -1) {
            try {
                plugin.getServer().getScheduler().cancelTask(scheduleTaskId);
            } catch (Exception ignored) {
            }
            scheduleTaskId = -1;
        }

        // Detener temporizador de parpadeo
        if (blinkTimerId != -1) {
            try {
                plugin.getServer().getScheduler().cancelTask(blinkTimerId);
            } catch (Exception ignored) {
            }
            blinkTimerId = -1;
        }

        // Detener temporizador post-KOTH
        if (postKothTimerId != -1) {
            try {
                plugin.getServer().getScheduler().cancelTask(postKothTimerId);
            } catch (Exception ignored) {
            }
            postKothTimerId = -1;
        }
    }

    private void startBlinkTimer() {
        blinkTimer = new BukkitRunnable() {
            @Override
            public void run() {
                showExclamation = !showExclamation; // Cambiar estado cada 5 segundos
            }
        };
        blinkTimerId = blinkTimer.runTaskTimer(plugin, 20L * 5, 20L * 5).getTaskId(); // Cada 5 segundos (20 ticks = 1
                                                                                      // segundo)
    }

    private void startWorldAutoSave() {
        new BukkitRunnable() {
            @Override
            public void run() {
                World kothWorld = kothWorldManager.getWorld();
                if (kothWorld != null) {
                    kothWorld.save();
                    plugin.getLogger().info("[KOTH] Mundo KOTH guardado automáticamente.");
                }
            }
        }.runTaskTimer(plugin, 20L * 60 * 5, 20L * 60 * 5); // Cada 5 minutos
    }

    // ==========================================
    // SISTEMA DE PUNTOS
    // ==========================================

    /**
     * Añade puntos a un jugador.
     */
    public void addPoints(Player player, int points) {
        UUID uuid = player.getUniqueId();
        playerPoints.merge(uuid, points, Integer::sum);
    }

    /**
     * Registra un kill para un jugador.
     */
    public void addKill(Player player) {
        playerKills.merge(player.getUniqueId(), 1, Integer::sum);
    }

    /**
     * Obtiene los puntos de un jugador.
     */
    public int getPlayerPoints(Player player) {
        return playerPoints.getOrDefault(player.getUniqueId(), 0);
    }

    /**
     * Obtiene las kills de un jugador.
     */
    public int getPlayerKills(Player player) {
        return playerKills.getOrDefault(player.getUniqueId(), 0);
    }

    /**
     * Obtiene el líder actual (jugador con más puntos).
     * 
     * @return Entry con nombre del jugador y puntos, o null
     */
    public Map.Entry<String, Integer> getLeader() {
        return getTopPlayers(1).stream().findFirst().orElse(null);
    }

    /**
     * Obtiene los top N jugadores por puntos.
     * 
     * @return Lista ordenada de mayor a menor
     */
    public List<Map.Entry<String, Integer>> getTopPlayers(int n) {
        Map<String, Integer> namedPoints = new HashMap<>();
        for (Map.Entry<UUID, Integer> entry : playerPoints.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null) {
                namedPoints.put(p.getName(), entry.getValue());
            }
        }

        return namedPoints.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(n)
                .collect(Collectors.toList());
    }

    // ==========================================
    // DETECCIÓN DE EQUIPOS
    // ==========================================

    /**
     * Detecta qué equipos/jugadores hay en la zona de captura.
     */
    private Map<String, List<Player>> getTeamsInZone() {
        Map<String, List<Player>> teams = new HashMap<>();

        World kothWorld = Bukkit.getWorld(KothWorld.getWorldName());
        if (kothWorld == null)
            return teams;

        for (Player player : kothWorld.getPlayers()) {
            if (player.isDead())
                continue;
            if (!currentZone.contains(player.getLocation()))
                continue;
            // Solo contar si el jugador está entre Y=80 y Y=85
            double playerY = player.getLocation().getY();
            if (playerY < 80 || playerY > 85)
                continue;

            String teamName = getPlayerTeam(player);
            teams.computeIfAbsent(teamName, k -> new ArrayList<>()).add(player);
        }

        return teams;
    }

    /**
     * Obtiene el equipo de un jugador (scoreboard Team o nombre individual).
     */
    private String getPlayerTeam(Player player) {
        return player.getName();
    }

    // ==========================================
    // ACTUALIZACIÓN DE UI
    // ==========================================

    /**
     * Actualiza la BossBar global con información del KOTH.
     */
    private void updateBossBar(int timeRemaining) {
        if (bossBar == null)
            return;

        String kothName = plugin.getConfig().getString("koth.name", "MoonKOTH");
        String timeStr = getFormattedTimeRemaining();
        String stateStr;

        switch (currentZone.getState()) {
            case CAPTURING:
                stateStr = currentZone.getCapturingTeam() + " §fcapturando";
                bossBar.setColor(BarColor.GREEN);
                break;
            case CONTESTED:
                stateStr = "§c§l¡En disputa!";
                bossBar.setColor(BarColor.RED);
                break;
            default:
                stateStr = "§7Esperando";
                bossBar.setColor(BarColor.YELLOW);
                break;
        }

        if (currentMode == KothMode.COOPERATIVO) {
            // BossBar cooperativo: mostrar puntos de ambos equipos + /koth
            int totalHumanPoints = getTotalHumanPoints();
            String coopInfo = "§a☺ " + totalHumanPoints + " §8vs §c☠ " + npcPoints;
            String bossStatus = (kothBoss != null && kothBoss.isActive()) ? " §8| §4§l☠BOSS" : "";

            if (timeRemaining <= 600) {
                String secondsStr = String.format("%02d", timeRemaining % 60);
                bossBar.setTitle("§6§l⚔ " + kothName + " §8| " + stateStr + " §8| " + coopInfo + " §8| §c"
                        + (timeRemaining / 60) + ":" + secondsStr + bossStatus + " §8| §f/koth");
            } else {
                bossBar.setTitle("§6§l⚔ " + kothName + " §8| " + stateStr + " §8| " + coopInfo + " §8| §e⏱ " + timeStr
                        + bossStatus + " §8| §f/koth");
            }
        } else {
            // BossBar normal
            if (timeRemaining <= 600) {
                String secondsStr = String.format("%02d", timeRemaining % 60);
                bossBar.setTitle("§6§l⚔ " + kothName + " §8| " + stateStr + " §8| §c" + (timeRemaining / 60) + ":"
                        + secondsStr + " §8| §f/koth");
            } else {
                bossBar.setTitle(SmallCaps.convert("§6§l⚔ " + kothName + " §8| " + stateStr + " §8| §e⏱ " + timeStr + " §8| §f/koth"));
            }
        }

        bossBar.setProgress(Math.max(0, Math.min(1, (double) timeRemaining / currentZone.getTotalDuration())));

        // Asegurar nuevos jugadores
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!bossBar.getPlayers().contains(p)) {
                bossBar.addPlayer(p);
            }
        }
    }

    /**
     * Obtiene el total de puntos de todos los jugadores humanos (para cooperativo).
     */
    private int getTotalHumanPoints() {
        int total = 0;
        for (int pts : playerPoints.values()) {
            total += pts;
        }
        return total;
    }

    /**
     * Actualiza la scoreboard de todos los jugadores en el mundo KOTH.
     */
    private void updateAllScoreboards(int timeRemaining) {
        World kothWorld = Bukkit.getWorld(KothWorld.getWorldName());
        if (kothWorld == null)
            return;

        // Obtener estado
        String status;
        String statusColor;
        switch (currentZone.getState()) {
            case CAPTURING:
                status = "Capturando";
                statusColor = "§a";
                break;
            case CONTESTED:
                status = "En disputa";
                statusColor = "§c§l";
                break;
            default:
                status = "Esperando";
                statusColor = "§7";
                break;
        }

        String controlling = currentZone.getCapturingTeam();

        if (currentMode == KothMode.COOPERATIVO) {
            // Scoreboard cooperativo: NPCs vs Humanos
            int totalHumanPts = getTotalHumanPoints();
            int remainingNPCs = npcManager.getRemainingNPCs();
            int remainingCapturers = npcManager.getRemainingCapturers();
            int remainingAttackers = npcManager.getRemainingAttackers();

            for (Player player : kothWorld.getPlayers()) {
                int myPoints = getPlayerPoints(player);
                boolean inZone = currentZone.contains(player.getLocation());

                scoreboard.applyCoopScoreboard(player, timeRemaining, status, statusColor,
                        controlling, myPoints, totalHumanPts, npcPoints,
                        remainingAttackers, remainingCapturers, inZone, keepInvDelaySeconds);
            }
        } else {
            // Scoreboard normal
            Map.Entry<String, Integer> leader = getLeader();
            String leaderName = leader != null ? leader.getKey() : null;
            int leaderPoints = leader != null ? leader.getValue() : 0;

            for (Player player : kothWorld.getPlayers()) {
                int myPoints = getPlayerPoints(player);
                boolean inZone = currentZone.contains(player.getLocation());

                scoreboard.applyScoreboard(player, timeRemaining, status, statusColor,
                        controlling, myPoints, leaderName, leaderPoints, inZone, keepInvDelaySeconds);
            }
        }
    }

    // ==========================================
    // TELETRANSPORTE
    // ==========================================

    /**
     * Teletransporta un jugador al KOTH.
     * Guarda su ubicación de retorno.
     */
    public boolean teleportToKoth(Player player) {
        if (!active) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo hay ningún evento KOTH activo."));
            return false;
        }

        if (postKothMode) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cEl KOTH ha terminado. Espera a la limpieza."));
            return false;
        }

        // Bloquear jugadores nuevos con protección PvP (< 3h de juego)
        int NEW_PLAYER_TICKS = 3 * 60 * 60 * 20; // 3 horas en ticks
        int playtime = player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE);
        if (playtime < NEW_PLAYER_TICKS && !player.hasPermission("wardstone.admin")) {
            long minutesLeft = (NEW_PLAYER_TICKS - playtime) / 20 / 60;
            long hoursLeft = minutesLeft / 60;
            long minsLeft = minutesLeft % 60;
            player.sendMessage(com.moonlight.coreprotect.util.SmallCaps.convert(
                    "§e§l🛡 §fTienes protección de nuevo jugador. §7No puedes entrar al KOTH hasta que pase tu protección §7(§e" + hoursLeft + "h " + minsLeft + "m§7)."));
            return false;
        }

        World kothWorld = Bukkit.getWorld(KothWorld.getWorldName());
        if (kothWorld == null) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cEl mundo KOTH no está disponible."));
            return false;
        }

        // Guardar ubicación de retorno
        returnLocations.put(player.getUniqueId(), player.getLocation().clone());

        // Registrar tiempo de entrada para la protección del spawn
        entryTimestamps.put(player.getUniqueId(), System.currentTimeMillis());

        // Teletransportar
        Location destination = kothSpawn != null ? kothSpawn : new Location(kothWorld, 0, 65, 0);
        destination.setWorld(kothWorld);
        player.teleport(destination);

        player.sendMessage(SmallCaps.convert("§a§l✔ §a¡Teletransportado al KOTH!"));
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

        // Cooperativo: spawnear NPCs cuando entra el PRIMER jugador
        if (currentMode == KothMode.COOPERATIVO && !npcManager.hasSpawned()) {
            // Esperar 3 segundos para que el jugador cargue el mundo
            new org.bukkit.scheduler.BukkitRunnable() {
                @Override
                public void run() {
                    if (active && currentMode == KothMode.COOPERATIVO && !npcManager.hasSpawned()) {
                        int difficulty = Math.max(1, Math.min(Bukkit.getOnlinePlayers().size(), 5));
                        npcManager.spawnNPCs(kothCenter, difficulty, player);
                    }
                }
            }.runTaskLater(plugin, 60L); // 3 segundos
        }

        return true;
    }

    /**
     * Devuelve un jugador a su ubicación original.
     */
    public void teleportBack(Player player) {
        Location returnLoc = returnLocations.remove(player.getUniqueId());
        if (returnLoc != null && returnLoc.getWorld() != null) {
            player.teleport(returnLoc);
        } else {
            // Teleport al spawn del mundo principal
            World mainWorld = Bukkit.getWorlds().get(0);
            player.teleport(mainWorld.getSpawnLocation());
        }
        player.sendMessage(SmallCaps.convert("§a§l✔ §aHas salido del KOTH."));
    }

    /**
     * Teletransporta a todos los jugadores fuera del mundo KOTH.
     */
    private void teleportAllBack() {
        World kothWorld = Bukkit.getWorld(KothWorld.getWorldName());
        if (kothWorld == null)
            return;

        for (Player player : new ArrayList<>(kothWorld.getPlayers())) {
            teleportBack(player);
        }
    }

    /**
     * Teletransporta FORZOSAMENTE a todos los jugadores fuera del mundo KOTH (rescate).
     */
    public void forceKickAllPlayers() {
        World kothWorld = Bukkit.getWorld(KothWorld.getWorldName());
        if (kothWorld == null)
            return;

        World mainWorld = Bukkit.getWorlds().get(0);
        Location safeSpawn = mainWorld.getSpawnLocation();

        for (Player player : new ArrayList<>(kothWorld.getPlayers())) {
            if (player != null && player.isOnline()) {
                // Forzar TP independiente de returnLocations
                player.teleport(safeSpawn);
                player.sendMessage(com.moonlight.coreprotect.util.SmallCaps.convert("§c§l⚠ §cHas sido expulsado forzosamente del mundo KOTH."));
                
                // Limpiar su estado de KOTH
                returnLocations.remove(player.getUniqueId());
                playerPoints.remove(player.getUniqueId());
            }
        }
        plugin.getLogger().info("[KOTH] Se ha ejecutado una expulsión forzosa de todos los jugadores del mundo KOTH.");
    }

    // ==========================================
    // RECOMPENSAS
    // ==========================================

    /**
     * Da recompensas al ganador del KOTH.
     */
    private void giveRewards(Player player) {
        // Esencias reward
        if (plugin.getEssenceManager() != null) {
            plugin.getEssenceManager().onKothWin(player);
        }

        // 1 llave especial de ExcellentCrates
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "excellentcrates key give " + player.getName() + " special 1");

        // 1 llave KOTH de ExcellentCrates
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "excellentcrates key give " + player.getName() + " koth 1");

        // 10% chance de llave_espadas (muy rara)
        if (Math.random() < 0.10) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "excellentcrates key give " + player.getName() + " llave_espadas 1");
            player.sendMessage(SmallCaps.convert("§c§l⚔ §f¡Has obtenido una §c§lLlave de Espadas§f! §7(Muy rara)"));
            Bukkit.broadcastMessage(SmallCaps.convert("§c§l⚔ §e" + player.getName() + " §fha obtenido una §c§lLlave de Espadas §fal ganar el KOTH!"));
        }

        player.sendMessage(SmallCaps.convert("§6§l⚔ §a§l¡Felicidades! §f¡Has ganado el MoonKOTH! §e★"));
        player.sendMessage(SmallCaps.convert("§6§l⚔ §eRecompensas: §d8 Esencias §f+ §61x Special §f+ §bx1 KOTH"));
    }

    /**
     * Da recompensas menores a los puestos 2 y 3.
     */
    private void giveRunnerUpRewards(Player player, int position) {
        List<String> commands = plugin.getConfig().getStringList("koth.rewards.runnerup-commands");
        for (String cmd : commands) {
            String parsed = cmd.replace("{player}", player.getName())
                    .replace("{position}", String.valueOf(position));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }

        player.sendMessage(SmallCaps.convert("§6§l⚔ §e¡Has quedado en el puesto §f#" + position + " §edel MoonKOTH!"));
    }

    // ==========================================
    // UTILIDADES
    // ==========================================

    /**
     * Envía un mensaje solo a los jugadores en el mundo KOTH.
     */
    private void broadcastToKothWorld(String message) {
        World kothWorld = Bukkit.getWorld(KothWorld.getWorldName());
        if (kothWorld == null)
            return;
        for (Player p : kothWorld.getPlayers()) {
            p.sendMessage(message);
        }
    }

    /**
     * Obtiene el tiempo restante formateado (mm:ss).
     * Si quedan menos de 15 minutos, agrega una exclamación parpadeante.
     */
    public String getFormattedTimeRemaining() {
        if (currentZone == null)
            return "00:00";
        int remaining = currentZone.getTimeRemaining();
        String timeStr = String.format("%02d:%02d", remaining / 60, remaining % 60);

        // Si quedan menos de 15 minutos (900 segundos), agregar exclamación parpadeante
        if (remaining <= 900 && remaining > 0) {
            // Usar formato de parpadeo con colores alternados para mayor visibilidad
            String blinkWarning = "§c§l!§r " + timeStr + " §e§l!§r";
            return blinkWarning;
        }

        return timeStr;
    }

    /**
     * Obtiene la próxima hora programada del KOTH en formato de cuenta atrás.
     * Usa directamente el countdown del scheduler (preciso, se actualiza cada
     * segundo).
     */
    public String getNextScheduledTime() {
        // Si el KOTH está activo, mostrar eso en vez de un countdown
        if (active) {
            return "§a§l¡En curso!";
        }

        // Si countdown es -1, recalcular (solo ocurre una vez tras KOTH o al iniciar)
        if (schedulerCountdown < 0) {
            recalculateCountdown();
        }
        if (schedulerCountdown < 0)
            return "No programado";
        if (schedulerCountdown <= 0)
            return "§a¡Iniciando!";

        long totalSecondsRemaining = schedulerCountdown;

        long hours = totalSecondsRemaining / 3600;
        long minutes = (totalSecondsRemaining % 3600) / 60;
        long seconds = totalSecondsRemaining % 60;

        String timeStr;
        if (hours > 0) {
            timeStr = hours + "h " + minutes + "m " + seconds + "s";
        } else if (minutes > 0) {
            timeStr = minutes + "m " + seconds + "s";
        } else {
            timeStr = seconds + "s";
        }

        // Si quedan menos de 15 minutos, agregar exclamación parpadeante
        if (totalSecondsRemaining <= 900 && !active) {
            if (showExclamation) {
                return "§c§l!§r " + timeStr + " §e§l!§r";
            } else {
                return timeStr;
            }
        }

        return timeStr;
    }

    /**
     * Agrega un jugador a la BossBar (para join durante evento).
     */
    public void addPlayerToBossBar(Player player) {
        if (bossBar != null && active) {
            bossBar.addPlayer(player);
        }
    }

    public boolean isPostKothMode() {
        return postKothMode;
    }

    /**
     * Expulsa a un jugador que reconectó en el mundo KOTH cuando no debería estar ahí.
     */
    public void kickIfNotAllowed(Player player) {
        if (!player.getWorld().getName().equals(KothWorld.getWorldName())) return;
        if (active && !postKothMode) return; // KOTH activo y no en post-mode, permitido
        // Está en el mundo KOTH pero no debería: expulsar
        Location returnLoc = returnLocations.remove(player.getUniqueId());
        Location safe = (returnLoc != null && returnLoc.getWorld() != null) ? returnLoc : Bukkit.getWorlds().get(0).getSpawnLocation();
        player.teleport(safe);
        player.sendMessage(SmallCaps.convert("§c§l⚠ §cEl KOTH ha terminado. Has sido expulsado."));
    }

    /**
     * Obtiene la ubicación de spawn del KOTH.
     */
    public Location getKothSpawn() {
        return kothSpawn;
    }

    /**
     * Obtiene el scoreboard manager.
     */
    public KothScoreboard getScoreboard() {
        return scoreboard;
    }

    /**
     * Obtiene la zona actual.
     */
    public KothZone getCurrentZone() {
        return currentZone;
    }

    /**
     * Maneja cuando los jugadores derrotan a todos los NPCs (victoria cooperativa).
     */
    public void npcsDefeated() {
        if (!active || currentMode != KothMode.COOPERATIVO)
            return;

        Bukkit.broadcastMessage(SmallCaps.convert("§a§l✔ §e§l¡VICTORIA COOPERATIVA! §fTodos los NPCs han sido derrotados."));
        Bukkit.broadcastMessage(SmallCaps.convert("§7§lTodos los jugadores en el mundo KOTH reciben recompensa."));

        // Dar puntos a todos los jugadores presentes
        World kothWorld = kothWorldManager.getWorld();
        if (kothWorld != null) {
            for (Player p : kothWorld.getPlayers()) {
                addPoints(p, 50);
            }
        }
    }

    /**
     * Maneja cuando los NPCs capturan la zona.
     */
    public void npcsCapturedZone() {
        if (!active)
            return;

        // Limpiar NPCs
        if (currentMode == KothMode.COOPERATIVO) {
            npcManager.clearNPCs();
        }

        // Anunciar derrota
        Bukkit.broadcastMessage(SmallCaps.convert("§4§l💀 §c§l¡DERROTA COOPERATIVA! §4§l💀"));
        Bukkit.broadcastMessage(SmallCaps.convert("§7§lLos NPCs han capturado la zona. No hay recompensas esta vez."));

        // Finalizar el KOTH sin ganador
        endKothCommon(null);
    }

    /**
     * Obtiene el modo actual del KOTH.
     */
    public KothMode getCurrentMode() {
        return currentMode;
    }

    /**
     * Verifica si el KOTH está activo.
     */
    public boolean isActive() {
        return active;
    }

    public org.bukkit.boss.BossBar getBossBar() {
        return bossBar;
    }

    public Map<UUID, Long> getEntryTimestamps() {
        return entryTimestamps;
    }

    /**
     * Apagado limpio del sistema.
     */
    public void shutdown() {
        if (active) {
            stopKoth();
        }
        stopMainTask();
        stopScheduler();
        particles.stop();
        scoreboard.cleanup();
    }

    // === NUEVOS MÉTODOS MEJORAS ===

    /**
     * Registra un bloque colocado durante el KOTH para su restauración.
     */
    public void addPlacedBlock(Location loc, Material originalType) {
        placedBlocks.put(loc.clone(), originalType);
    }

    /**
     * Elimina un bloque del registro (si fue roto).
     */
    public void removePlacedBlock(Location loc) {
        placedBlocks.remove(loc);
    }

    /**
     * Verifica si un bloque fue colocado durante el KOTH.
     */
    public boolean isPlacedBlock(Location loc) {
        return placedBlocks.containsKey(loc);
    }

    /**
     * Restaura todos los bloques colocados durante el KOTH.
     */
    public void restoreAllBlocks() {
        for (Map.Entry<Location, Material> entry : placedBlocks.entrySet()) {
            Location loc = entry.getKey();
            Material originalType = entry.getValue();

            if (loc.getBlock().getType() != originalType) {
                loc.getBlock().setType(originalType);
            }
        }
        placedBlocks.clear();

        plugin.getLogger().info("[KOTH] Restaurados " + placedBlocks.size() + " bloques a su estado original.");
    }

    /**
     * Establece la ubicación del faro central.
     */
    public void setCenterKoth(Location loc) {
        this.kothCenter = loc.clone();
        kothConfig.setCenter(loc);

        // Generar base del faro (3x3 de bloques de hierro en Y-1) para activarlo
        org.bukkit.World world = loc.getWorld();
        if (world != null) {
            int bx = loc.getBlockX();
            int by = loc.getBlockY();
            int bz = loc.getBlockZ();
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    world.getBlockAt(bx + x, by - 1, bz + z).setType(org.bukkit.Material.IRON_BLOCK);
                }
            }
            // Colocar el propio faro si no está
            world.getBlockAt(loc).setType(org.bukkit.Material.BEACON);
        }
    }

    /**
     * Actualiza el color del faro según el estado del KOTH.
     */
    private void updateBeaconColor() {
        if (kothCenter == null || kothCenter.getWorld() == null)
            return;

        Location glassLoc = kothCenter.clone().add(0, 1, 0);
        Material color;

        switch (currentZone.getState()) {
            case CAPTURING:
                color = Material.LIME_STAINED_GLASS;
                break;
            case CONTESTED:
                color = Material.RED_STAINED_GLASS;
                break;
            case IDLE:
            default:
                color = Material.WHITE_STAINED_GLASS;
                break;
        }

        if (glassLoc.getBlock().getType() != color) {
            glassLoc.getBlock().setType(color);
        }
    }

    public boolean isInsideMap(Location loc) {
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        return x >= mapX1 && x <= mapX2 && z >= mapZ1 && z <= mapZ2;
    }

    public void setMap(int x1, int z1, int x2, int z2) {
        this.mapX1 = Math.min(x1, x2);
        this.mapZ1 = Math.min(z1, z2);
        this.mapX2 = Math.max(x1, x2);
        this.mapZ2 = Math.max(z1, z2);
        kothConfig.setMap(x1, z1, x2, z2);
    }

    public int getMapX1() {
        return mapX1;
    }

    public int getMapZ1() {
        return mapZ1;
    }

    public int getMapX2() {
        return mapX2;
    }

    public int getMapZ2() {
        return mapZ2;
    }

    /**
     * Verifica si un jugador está protegido en el área del spawn.
     * La protección solo dura unos segundos (configurables) tras entrar.
     */
    public boolean isProtectedAtSpawn(Player player) {
        if (!isAtSpawnArea(player.getLocation()))
            return false;

        Long entryTime = entryTimestamps.get(player.getUniqueId());
        if (entryTime == null)
            return false;

        int protectedSeconds = plugin.getConfig().getInt("koth.mechanics.spawn-protection-seconds", 15);
        long diff = (System.currentTimeMillis() - entryTime) / 1000;

        return diff < protectedSeconds;
    }

    /**
     * Verifica si una ubicación está dentro del área protegida del spawn (3x3x3).
     */
    public boolean isAtSpawnArea(Location loc) {
        if (kothSpawn == null || loc.getWorld() == null || !loc.getWorld().getName().equals(KothWorld.getWorldName()))
            return false;

        double dx = Math.abs(loc.getX() - kothSpawn.getX());
        double dy = Math.abs(loc.getY() - kothSpawn.getY());
        double dz = Math.abs(loc.getZ() - kothSpawn.getZ());

        // 1.5 de radio permite cubrir un cubo de 3x3x3 centrado en el spawn
        return dx <= 1.5 && dy <= 1.5 && dz <= 1.5;
    }
}
