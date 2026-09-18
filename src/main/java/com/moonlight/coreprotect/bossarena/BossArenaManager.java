package com.moonlight.coreprotect.bossarena;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Gestor principal del sistema de Boss Arena.
 * - Múltiples arenas (bossarena_1, bossarena_2, etc.)
 * - Selección aleatoria de arena al spawnear boss
 * - Spawn automático cada 4h con avisos
 * - Comando para forzar spawn
 */
public class BossArenaManager {

    private final CoreProtectPlugin plugin;
    private final File configFile;
    private final Map<String, ArenaData> arenas = new LinkedHashMap<>();

    // Estado del boss actual
    private Boss currentBoss;
    private ArenaData currentArena;
    private boolean bossActive = false;
    private long nextSpawnTime = 0;
    private boolean preparationPhase = false;

    // Confirmación para /boss
    private final Map<UUID, Long> pendingConfirmations = new HashMap<>();

    // Timer de spawn automático
    private BukkitTask spawnTimer;
    private static final long SPAWN_INTERVAL_MS = 4L * 60 * 60 * 1000; // 4 horas

    // Avisos antes del spawn (en milisegundos)
    private static final long[] WARN_TIMES = {
        15L * 60 * 1000, // 15 min
        10L * 60 * 1000, // 10 min
         5L * 60 * 1000, //  5 min
         3L * 60 * 1000, //  3 min
         1L * 60 * 1000, //  1 min
              30 * 1000,  // 30 seg
              15 * 1000,  // 15 seg
              10 * 1000   // 10 seg
    };
    private final Set<Long> warnsSent = new HashSet<>();

    // Setup interactivo
    private final Map<UUID, SetupSession> setupSessions = new HashMap<>();

    public BossArenaManager(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "bossarena.yml");
        loadConfig();
        startAutoSpawnTimer();
    }

    // ==================== CONFIGURACIÓN ====================

    private void loadConfig() {
        if (!configFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);

        ConfigurationSection arenasSection = yaml.getConfigurationSection("arenas");
        if (arenasSection == null) return;

        for (String worldName : arenasSection.getKeys(false)) {
            ConfigurationSection sec = arenasSection.getConfigurationSection(worldName);
            if (sec == null) continue;

            ArenaData data = new ArenaData(worldName);

            // Cargar mundo si no está cargado
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                WorldCreator creator = new WorldCreator(worldName);
                creator.generator(new VoidChunkGenerator());
                creator.type(WorldType.FLAT);
                creator.generateStructures(false);
                creator.environment(World.Environment.NORMAL);
                world = creator.createWorld();
                if (world != null) applyWorldSettings(world);
            }

            if (world == null) continue;

            if (sec.contains("boss-spawn"))
                data.setBossSpawn(deserializeLoc(sec.getConfigurationSection("boss-spawn"), world));
            if (sec.contains("player-spawn"))
                data.setPlayerSpawn(deserializeLoc(sec.getConfigurationSection("player-spawn"), world));
            if (sec.contains("bound-min"))
                data.setBoundMin(deserializeLoc(sec.getConfigurationSection("bound-min"), world));
            if (sec.contains("bound-max"))
                data.setBoundMax(deserializeLoc(sec.getConfigurationSection("bound-max"), world));
            if (sec.contains("boss-type"))
                data.setBossType(BossType.fromString(sec.getString("boss-type", "VORGATH")));

            arenas.put(worldName, data);
            plugin.getLogger().info("[BossArena] Arena cargada: " + worldName);
        }

        // Cargar nextSpawnTime
        if (yaml.contains("next-spawn-time")) {
            nextSpawnTime = yaml.getLong("next-spawn-time");
        }
    }

    public void saveConfig() {
        YamlConfiguration yaml = new YamlConfiguration();

        for (Map.Entry<String, ArenaData> entry : arenas.entrySet()) {
            String path = "arenas." + entry.getKey();
            ArenaData data = entry.getValue();

            if (data.getBossSpawn() != null) serializeLoc(yaml, path + ".boss-spawn", data.getBossSpawn());
            if (data.getPlayerSpawn() != null) serializeLoc(yaml, path + ".player-spawn", data.getPlayerSpawn());
            if (data.getBoundMin() != null) serializeLoc(yaml, path + ".bound-min", data.getBoundMin());
            if (data.getBoundMax() != null) serializeLoc(yaml, path + ".bound-max", data.getBoundMax());
            yaml.set(path + ".boss-type", data.getBossType().name());
        }

        yaml.set("next-spawn-time", nextSpawnTime);

        try {
            yaml.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[BossArena] Error guardando config: " + e.getMessage());
        }
    }

    private void serializeLoc(YamlConfiguration yaml, String path, Location loc) {
        yaml.set(path + ".x", loc.getX());
        yaml.set(path + ".y", loc.getY());
        yaml.set(path + ".z", loc.getZ());
        yaml.set(path + ".yaw", loc.getYaw());
        yaml.set(path + ".pitch", loc.getPitch());
    }

    private Location deserializeLoc(ConfigurationSection sec, World world) {
        if (sec == null) return null;
        return new Location(world, sec.getDouble("x"), sec.getDouble("y"), sec.getDouble("z"),
                (float) sec.getDouble("yaw"), (float) sec.getDouble("pitch"));
    }

    // ==================== CREAR ARENAS ====================

    public ArenaData createArena() {
        int nextId = arenas.size() + 1;
        String worldName = "bossarena_" + nextId;

        // Si ya existe, buscar el siguiente disponible
        while (arenas.containsKey(worldName) || Bukkit.getWorld(worldName) != null) {
            nextId++;
            worldName = "bossarena_" + nextId;
        }

        plugin.getLogger().info("[BossArena] Creando mundo: " + worldName);
        WorldCreator creator = new WorldCreator(worldName);
        creator.generator(new VoidChunkGenerator());
        creator.type(WorldType.FLAT);
        creator.generateStructures(false);
        creator.environment(World.Environment.NORMAL);

        World world = creator.createWorld();
        if (world == null) {
            plugin.getLogger().severe("[BossArena] Error creando mundo: " + worldName);
            return null;
        }

        applyWorldSettings(world);
        world.getBlockAt(0, 64, 0).setType(Material.BEDROCK);

        ArenaData data = new ArenaData(worldName);
        arenas.put(worldName, data);
        saveConfig();

        plugin.getLogger().info("[BossArena] Arena creada: " + worldName);
        return data;
    }

    private void applyWorldSettings(World world) {
        world.setPVP(true);
        world.setDifficulty(Difficulty.NORMAL);
        com.moonlight.coreprotect.util.GameRuleUtil.set(world, "doMobSpawning", false);
        com.moonlight.coreprotect.util.GameRuleUtil.set(world, "mobGriefing", false);
        com.moonlight.coreprotect.util.GameRuleUtil.set(world, "doFireTick", false);
        com.moonlight.coreprotect.util.GameRuleUtil.set(world, "doDaylightCycle", false);
        world.setTime(18000); // Noche para ambiente del Nether
        com.moonlight.coreprotect.util.GameRuleUtil.set(world, "doWeatherCycle", false);
        world.setStorm(false);
        world.setThundering(false);
        com.moonlight.coreprotect.util.GameRuleUtil.set(world, "keepInventory", false);
        com.moonlight.coreprotect.util.GameRuleUtil.set(world, "naturalRegeneration", true);
        com.moonlight.coreprotect.util.GameRuleUtil.set(world, "announceAdvancements", false);
        world.setSpawnLocation(0, 65, 0);
    }

    // ==================== SPAWN AUTOMÁTICO ====================

    private void startAutoSpawnTimer() {
        if (nextSpawnTime <= 0) {
            nextSpawnTime = System.currentTimeMillis() + SPAWN_INTERVAL_MS;
            saveConfig();
        }

        spawnTimer = new BukkitRunnable() {
            @Override
            public void run() {
                if (bossActive) return;
                if (arenas.isEmpty()) return;

                long now = System.currentTimeMillis();
                long remaining = nextSpawnTime - now;

                if (remaining <= 0) {
                    // ¡Spawnear!
                    spawnRandomBoss();
                    return;
                }

                // Enviar avisos
                for (long warnTime : WARN_TIMES) {
                    if (remaining <= warnTime && !warnsSent.contains(warnTime)) {
                        warnsSent.add(warnTime);
                        String timeStr = formatTime(warnTime);
                        Bukkit.broadcastMessage("");
                        Bukkit.broadcastMessage(SmallCaps.convert("§4§l⚔ §c§lBOSS ARENA §4§l⚔"));
                        Bukkit.broadcastMessage(SmallCaps.convert("§7En §e" + timeStr + " §7va a despertar un §c§lBoss§7."));
                        Bukkit.broadcastMessage(SmallCaps.convert("§7Usa §e/boss §7para ir a la arena."));
                        Bukkit.broadcastMessage("");

                        for (Player p : Bukkit.getOnlinePlayers()) {
                            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.3f, 0.5f);
                        }

                        // Al aviso de 1 minuto: limpiar la arena de entidades e ítems acumulados
                        if (warnTime == 1L * 60 * 1000) {
                            cleanAllArenas();
                        }
                        break;
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Cada segundo
    }

    public synchronized void spawnRandomBoss() {
        // Validación doble para evitar duplicación
        if (bossActive) {
            plugin.getLogger().warning("[BossArena] spawnRandomBoss() llamado con boss ya activo - ignorando");
            return;
        }

        List<ArenaData> configured = arenas.values().stream()
                .filter(a -> a.isConfigured())
                .toList();
        if (configured.isEmpty()) {
            plugin.getLogger().warning("[BossArena] No hay arenas configuradas.");
            return;
        }

        // Selección aleatoria
        ArenaData arena = configured.get(new Random().nextInt(configured.size()));
        plugin.getLogger().info("[BossArena] Spawneando boss en arena: " + arena.getWorldName());
        spawnBoss(arena);
    }

    /**
     * Limpia todas las arenas configuradas: elimina entidades (excepto jugadores) e ítems del suelo.
     */
    private void cleanAllArenas() {
        for (ArenaData a : arenas.values()) {
            if (!a.isConfigured()) continue;
            World w = a.getWorld();
            if (w == null) continue;
            String wn = w.getName().toLowerCase();
            // Seguridad: nunca limpiar mundos principales
            if (wn.equals("world") || wn.equals("world_nether") || wn.equals("world_the_end") || wn.equals("spawn")) continue;
            int count = 0;
            for (org.bukkit.entity.Entity e : w.getEntities()) {
                if (e instanceof Player) continue;
                e.remove();
                count++;
            }
            if (count > 0)
                plugin.getLogger().info("[BossArena] Pre-spawn cleanup en " + wn + ": " + count + " entidades/items eliminados.");
        }
    }

    /**
     * Fuerza la carga de los chunks alrededor de una ubicación para que las entidades
     * no sean descargadas cuando no hay jugadores en la arena.
     */
    private void forceLoadArenaChunks(World world, Location center, int radiusChunks) {
        int cx = center.getBlockX() >> 4;
        int cz = center.getBlockZ() >> 4;
        int total = 0;
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                world.setChunkForceLoaded(cx + dx, cz + dz, true);
                world.loadChunk(cx + dx, cz + dz, true); // Cargar síncronamente ahora mismo
                total++;
            }
        }
        plugin.getLogger().info("[BossArena] " + total + " chunks cargados forzadamente alrededor de (" + cx + "," + cz + ") radio=" + radiusChunks + " en " + world.getName());
    }

    /**
     * Libera los chunks forzados de carga alrededor de una ubicación.
     */
    private void unforceLoadArenaChunks(World world, Location center, int radiusChunks) {
        if (world == null || center == null) return;
        int cx = center.getBlockX() >> 4;
        int cz = center.getBlockZ() >> 4;
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                world.setChunkForceLoaded(cx + dx, cz + dz, false);
            }
        }
        plugin.getLogger().info("[BossArena] Chunks forzados liberados en " + world.getName());
    }

    public synchronized void spawnBoss(ArenaData arena) {
        if (bossActive) {
            plugin.getLogger().warning("[BossArena] Intento de spawn duplicado - boss ya activo");
            return;
        }

        // Asegurar mundo cargado
        World world = arena.getWorld();
        if (world == null) {
            plugin.getLogger().warning("[BossArena] Mundo no cargado: " + arena.getWorldName());
            return;
        }

        currentArena = arena;
        bossActive = true;
        preparationPhase = true;
        warnsSent.clear();

        BossType bossType = arena.getBossType();
        String bossColor = bossType.getColor();
        String bossDisplay = bossType.getDisplayName();
        String bossSubtitle = bossType.getSubtitle();

        // Crear el boss ANTES del countdown para que prepareArena() actualice locations
        plugin.getLogger().info("[BossArena] Creando boss tipo: " + bossType.name());
        switch (bossType) {
            case VORGATH:
                currentBoss = new VorgathBoss(plugin, BossArenaManager.this, arena);
                break;
            case GLACIUS:
                currentBoss = new GlaciusBoss(plugin, BossArenaManager.this, arena);
                break;
            case ABYSSAL_WARDEN:
                currentBoss = new AbyssalWardenBoss(plugin, BossArenaManager.this, arena);
                break;
            case APOCALYPSE_TYPE_A:
                currentBoss = new ApocalypseTypeABoss(plugin, BossArenaManager.this, arena);
                break;
            case CUBOSS_SLIME:
            default:
                currentBoss = new CubossSlimeBoss(plugin, BossArenaManager.this, arena);
                break;
        }

        // Preparar arena ANTES del countdown — crea mundo, construye arena, actualiza locations
        currentBoss.prepareArena();
        plugin.getLogger().info("[BossArena] Arena preparada. PlayerSpawn: " + arena.getPlayerSpawn());

        // Anuncio global — Fase de preparación
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(SmallCaps.convert(bossColor + "§l⚔ ═══════════════════════════════ ⚔"));
        Bukkit.broadcastMessage(SmallCaps.convert(bossColor + "§l    ¡" + bossType.getId().toUpperCase() + " ESTÁ DESPERTANDO!"));
        Bukkit.broadcastMessage(SmallCaps.convert("§7  El " + bossColor + bossSubtitle + " §7se prepara."));
        Bukkit.broadcastMessage(SmallCaps.convert("§7  Usa §e/boss §7para ir a la arena."));
        Bukkit.broadcastMessage(SmallCaps.convert("§7  §e§l¡30 SEGUNDOS PARA PREPARARSE!"));
        Bukkit.broadcastMessage(SmallCaps.convert("§7  §c§l¡EL ÚLTIMO GOLPE SE LLEVA TODO!"));
        Bukkit.broadcastMessage(SmallCaps.convert(bossColor + "§l⚔ ═══════════════════════════════ ⚔"));
        Bukkit.broadcastMessage("");

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 0.3f);
            p.sendTitle( SmallCaps.convert(bossColor + "§l⚔ " + bossType.getId().toUpperCase() + " ⚔"), SmallCaps.convert("§e¡30 segundos para prepararse!"), 10, 60, 20);
        }

        // Countdown de 30 segundos antes de spawn
        new org.bukkit.scheduler.BukkitRunnable() {
            int countdown = 30;
            @Override
            public void run() {
                if (!bossActive) { cancel(); return; }

                if (countdown == 20 || countdown == 10 || countdown == 5) {
                    Bukkit.broadcastMessage(SmallCaps.convert(bossColor + "§l⚔ §c" + bossType.getId() + " aparecerá en §e§l" + countdown + "s§c. §7Usa §e/boss §7para ir."));
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.0f);
                    }
                    
                    // Limpiar arena 5 segundos antes del spawn
                    if (countdown == 5) {
                        World arenaWorld = arena.getWorld();
                        if (arenaWorld != null) {
                            int cleanedCount = 0;
                            for (org.bukkit.entity.Entity e : arenaWorld.getEntities()) {
                                if (!(e instanceof org.bukkit.entity.Player)) {
                                    e.remove();
                                    cleanedCount++;
                                }
                            }
                            plugin.getLogger().info("[BossArena] Arena limpiada: " + cleanedCount + " entidades eliminadas antes del spawn");
                        }
                    }
                }

                if (countdown <= 3 && countdown > 0) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendTitle( SmallCaps.convert(bossColor + "§l" + countdown), SmallCaps.convert(bossColor + "¡" + bossType.getId() + " se acerca!"), 0, 25, 5);
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                    }
                }

                if (countdown <= 0) {
                    cancel();
                    preparationPhase = false;

                    Bukkit.broadcastMessage("");
                    Bukkit.broadcastMessage(SmallCaps.convert(bossColor + "§l⚔ §l¡" + bossType.getId().toUpperCase() + " HA DESPERTADO! " + bossColor + "§l⚔"));
                    Bukkit.broadcastMessage("");

                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendTitle( SmallCaps.convert(bossColor + "§l⚔ " + bossType.getId().toUpperCase() + " ⚔"), SmallCaps.convert(bossColor + "¡El boss ha despertado!"), 5, 40, 15);
                        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 0.3f);
                    }

                    // Boss ya fue creado y prepareArena() ya fue llamado antes del countdown
                    plugin.getLogger().info("[BossArena] Llamando spawn() en " + currentBoss.getBossName());

                    // Forzar carga de chunks ANTES del spawn para que el boss no muera sin jugadores
                    Location bossSpawn = arena.getBossSpawn();
                    if (bossSpawn != null && bossSpawn.getWorld() != null) {
                        forceLoadArenaChunks(bossSpawn.getWorld(), bossSpawn, 4);
                    }

                    currentBoss.spawn();
                    return;
                }

                countdown--;
            }
        }.runTaskTimer(plugin, 0L, 20L); // cada segundo
    }

    public void onBossDeath(Player lastHitter) {
        bossActive = false;

        // Guardar info del boss antes de limpiar
        String bossName = currentBoss != null ? currentBoss.getBossName().toUpperCase() : "BOSS";
        String color = currentBoss != null ? currentBoss.getBossType().getColor() : "§c";
        currentBoss = null;

        // Programar siguiente spawn
        nextSpawnTime = System.currentTimeMillis() + SPAWN_INTERVAL_MS;
        warnsSent.clear();
        saveConfig();

        // Anuncio
        if (lastHitter != null) {
            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage(SmallCaps.convert(color + "§l⚔ " + color + "§l" + bossName + " HA CAÍDO " + color + "§l⚔"));
            Bukkit.broadcastMessage(SmallCaps.convert("§7El último golpe fue de §e§l" + lastHitter.getName() + "§7."));
            Bukkit.broadcastMessage(SmallCaps.convert("§7¡Se ha llevado la " + color + "§lrecompensa máxima§7!"));
            Bukkit.broadcastMessage("");
        }

        // Reset arena después de 15 segundos
        if (currentArena != null) {
            ArenaData arena = currentArena;
            // Liberar chunks forzados al morir el boss
            Location bossSpawn = arena.getBossSpawn();
            World bossWorld = bossSpawn != null ? bossSpawn.getWorld() : arena.getWorld();
            if (bossSpawn != null && bossWorld != null) {
                unforceLoadArenaChunks(bossWorld, bossSpawn, 4);
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // Teletransportar a todos los jugadores fuera (usar mundo del bossSpawn)
                World world = bossSpawn != null && bossSpawn.getWorld() != null ? bossSpawn.getWorld() : arena.getWorld();
                if (world != null) {
                    for (Player p : world.getPlayers()) {
                        p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
                        p.sendMessage(SmallCaps.convert("§7La arena de boss se ha cerrado."));
                    }
                    
                    // Limpiar el mundo del boss de forma segura (solo mobs y entidades, NO jugadores)
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        // Seguridad: NO limpiar mundos principales por accidente
                        String name = world.getName().toLowerCase();
                        if (name.equals("world") || name.equals("world_nether") || name.equals("world_the_end") || name.equals("spawn")) {
                            plugin.getLogger().warning("[BossArena] Abortada limpieza masiva en mundo protegido: " + name);
                            return;
                        }

                        int count = 0;
                        for (org.bukkit.entity.Entity e : world.getEntities()) {
                            if (!(e instanceof org.bukkit.entity.Player)) {
                                e.remove();
                                count++;
                            }
                        }
                        plugin.getLogger().info("[BossArena] Limpieza completada en " + name + ": " + count + " entidades eliminadas.");
                    }, 20L); // 1 segundo después de sacar a los jugadores
                }
            }, 300L); // 15 segundos
        }
        currentArena = null;
    }

    public void forceSpawn() {
        if (bossActive) return;
        spawnRandomBoss();
    }

    public void forceSpawn(BossType type) {
        if (bossActive) return;

        // Filtrar arenas configuradas
        List<ArenaData> configured = new ArrayList<>();
        List<ArenaData> matching = new ArrayList<>();
        for (ArenaData a : arenas.values()) {
            if (a.isConfigured()) {
                configured.add(a);
                if (a.getBossType() == type) matching.add(a);
            }
        }
        if (configured.isEmpty()) {
            plugin.getLogger().warning("[BossArena] No hay arenas configuradas.");
            return;
        }

        // Preferir arenas que ya tengan ese boss configurado
        ArenaData arena;
        if (!matching.isEmpty()) {
            arena = matching.get(new Random().nextInt(matching.size()));
        } else {
            arena = configured.get(new Random().nextInt(configured.size()));
        }

        // Guardar tipo original y forzar el tipo pedido
        BossType original = arena.getBossType();
        arena.setBossType(type);
        spawnBoss(arena);
        // Restaurar tipo original para no alterar la config permanente
        arena.setBossType(original);
    }

    public void forceStop() {
        if (!bossActive || currentBoss == null) return;
        currentBoss.cleanup();
        bossActive = false;
        currentBoss = null;

        if (currentArena != null) {
            Location bs = currentArena.getBossSpawn();
            World world = bs != null && bs.getWorld() != null ? bs.getWorld() : currentArena.getWorld();
            if (world != null) {
                for (Player p : world.getPlayers()) {
                    p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
                    p.sendMessage(SmallCaps.convert("§7El boss ha sido eliminado por un administrador."));
                }
            }
        }
        currentArena = null;
        nextSpawnTime = System.currentTimeMillis() + SPAWN_INTERVAL_MS;
        warnsSent.clear();
        saveConfig();
    }

    // ==================== TELEPORT ====================

    public void teleportToArena(Player player) {
        if (!bossActive || currentArena == null) {
            // Mostrar tiempo restante
            if (nextSpawnTime > 0) {
                long remaining = nextSpawnTime - System.currentTimeMillis();
                if (remaining > 0) {
                    player.sendMessage(SmallCaps.convert("§4§l⚔ §cEl boss no ha despertado aún."));
                    player.sendMessage(SmallCaps.convert("§7Siguiente aparición en: §e" + formatTime(remaining)));
                    return;
                }
            }
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo hay ningún boss activo."));
            return;
        }

        Location spawn = currentArena.getPlayerSpawn();
        if (spawn == null) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cLa arena no tiene spawn configurado."));
            return;
        }

        // Si ya está en la arena, no pedir confirmación
        String playerWorld = player.getWorld().getName();
        boolean inArena = playerWorld.equals(currentArena.getWorldName())
                || (spawn.getWorld() != null && playerWorld.equals(spawn.getWorld().getName()));
        if (inArena) {
            player.sendMessage(SmallCaps.convert("§7Ya estás en la arena de boss."));
            return;
        }

        // Verificar si tiene confirmación pendiente
        UUID uuid = player.getUniqueId();
        Long confirmTime = pendingConfirmations.get(uuid);
        if (confirmTime != null && System.currentTimeMillis() - confirmTime < 30000) {
            // Ya confirmó, teletransportar
            pendingConfirmations.remove(uuid);
            player.teleport(spawn);
            String bColor = currentBoss != null ? currentBoss.getBossType().getColor() : "§c";
            String bDisplay = currentBoss != null ? currentBoss.getBossType().getDisplayName() : "§c§lBoss";
            player.sendMessage(SmallCaps.convert(bColor + "§l⚔ " + bColor + "¡Bienvenido a la arena de " + bDisplay + bColor + "!"));
            player.sendMessage(SmallCaps.convert("§7¡El §e§lúltimo golpe §7se lleva la recompensa!"));
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.3f, 0.8f);
            return;
        }

        // Teletransportar directamente sin confirmación
        player.teleport(spawn);
        String bColor = currentBoss != null ? currentBoss.getBossType().getColor() : "§c";
        String bDisplay = currentBoss != null ? currentBoss.getBossType().getDisplayName() : "§c§lBoss";
        player.sendMessage(SmallCaps.convert(bColor + "§l⚔ " + bColor + "¡Bienvenido a la arena de " + bDisplay + bColor + "!"));
        player.sendMessage(SmallCaps.convert("§7¡El §e§lúltimo golpe §7se lleva la recompensa!"));
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.3f, 0.8f);
    }

    public boolean isPreparationPhase() { return preparationPhase; }

    // ==================== UTILS ====================

    public static String formatTime(long millis) {
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) return hours + "h " + minutes + "m " + seconds + "s";
        if (minutes > 0) return minutes + "m " + seconds + "s";
        return seconds + "s";
    }

    // ==================== GETTERS ====================

    public CoreProtectPlugin getPlugin() { return plugin; }
    public Map<String, ArenaData> getArenas() { return arenas; }
    public boolean isBossActive() { return bossActive; }
    public Boss getCurrentBoss() { return currentBoss; }
    public ArenaData getCurrentArena() { return currentArena; }
    public long getNextSpawnTime() { return nextSpawnTime; }
    public Map<UUID, SetupSession> getSetupSessions() { return setupSessions; }

    public boolean isInAnyArena(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        String worldName = loc.getWorld().getName();
        // Mundos de arenas configuradas
        if (arenas.containsKey(worldName)) return true;
        // Mundos dedicados de bosses con arena propia
        if (worldName.equals("cuboss_arena") || worldName.equals("apocalypse_arena")) return true;
        return false;
    }

    public ArenaData getArenaByWorld(String worldName) {
        return arenas.get(worldName);
    }

    // ==================== VOID CHUNK GENERATOR ====================

    public static class VoidChunkGenerator extends ChunkGenerator {
        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateBedrock() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }
    }

    // ==================== SETUP SESSION ====================

    public static class SetupSession {
        public enum Step { BOSS_SPAWN, PLAYER_SPAWN, BOUND_MIN, BOUND_MAX, DONE }
        private final ArenaData arena;
        private Step currentStep = Step.BOSS_SPAWN;

        public SetupSession(ArenaData arena) {
            this.arena = arena;
        }

        public ArenaData getArena() { return arena; }
        public Step getCurrentStep() { return currentStep; }
        public void setCurrentStep(Step step) { this.currentStep = step; }
    }

    // ==================== PLACEHOLDER ====================

    public String getTimeUntilSpawn() {
        if (bossActive) return "§c¡ACTIVO!";
        if (nextSpawnTime <= 0) return "§7Desconocido";
        long remaining = nextSpawnTime - System.currentTimeMillis();
        if (remaining <= 0) return "§e¡Pronto!";
        return formatTime(remaining);
    }
}
