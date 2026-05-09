package com.moonlight.coreprotect.bossarena;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * Cuboss-Slime (slot CUBOSS_SLIME).
 *
 * Implementacion minima: spawnea el MythicMob {@code cuboss_slime}
 * en la arena y delega TODA la logica (AI, skills, fases) al propio MythicMobs.
 * Este wrapper solo se encarga de:
 *  - Spawn en la posicion exacta de la arena
 *  - BossBar global con HP en vivo
 *  - Deteccion de muerte -> recompensas al ultimo golpeador
 *  - Timeout de seguridad
 *  - Cleanup al terminar
 */
public class CubossSlimeBoss implements Boss {

    // MythicMob ID a spawnear
    private static final String MYTHIC_MOB_ID = "Cuboss-Slime";

    // Mundo void dedicado para Cuboss (NO comparte con otros bosses)
    private static final String CUBOSS_WORLD_NAME = "cuboss_arena";
    private static final int ARENA_Y = 100;

    // Timeout de combate (30 min)
    private static final long BOSS_TIMEOUT_MS = 30 * 60 * 1000L;

    private final CoreProtectPlugin plugin;
    private final BossArenaManager manager;
    private final ArenaData arena;

    private LivingEntity bossEntity;
    private UUID bossUuid;
    private double maxHealth = 1.0;

    private BossBar globalBossBar;
    private BukkitTask mainLoop;
    private boolean active = false;
    private boolean deathHandled = false;

    private long spawnTimestamp;
    private Player lastHitter;

    // Guardar locations originales para restaurar antes de saveConfig
    private Location originalBossSpawn;
    private Location originalPlayerSpawn;

    public CubossSlimeBoss(CoreProtectPlugin plugin, BossArenaManager manager, ArenaData arena) {
        this.plugin = plugin;
        this.manager = manager;
        this.arena = arena;
    }

    // ==================== PREPARE (antes del countdown) ====================

    @Override
    public void prepareArena() {
        plugin.getLogger().info("[Cuboss] prepareArena() — creando mundo y arena...");

        // Crear/cargar mundo void dedicado
        World cubossWorld = getOrCreateCubossWorld();
        if (cubossWorld == null) {
            plugin.getLogger().severe("[Cuboss] No se pudo crear el mundo " + CUBOSS_WORLD_NAME);
            return;
        }

        Location arenaCenter = new Location(cubossWorld, 0, ARENA_Y, 0);

        // Forzar carga de chunks
        int cx = arenaCenter.getBlockX() >> 4;
        int cz = arenaCenter.getBlockZ() >> 4;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                cubossWorld.setChunkForceLoaded(cx + dx, cz + dz, true);
                cubossWorld.loadChunk(cx + dx, cz + dz, true);
            }
        }

        // Construir arena
        buildArena(arenaCenter);

        // Guardar locations originales ANTES de modificarlas
        originalBossSpawn = arena.getBossSpawn() != null ? arena.getBossSpawn().clone() : null;
        originalPlayerSpawn = arena.getPlayerSpawn() != null ? arena.getPlayerSpawn().clone() : null;

        // Actualizar ArenaData — los jugadores que hagan /boss iran aqui
        Location bossSpawnLoc = new Location(cubossWorld, 0.5, ARENA_Y, 0.5);
        Location playerSpawnLoc = new Location(cubossWorld, 20.5, ARENA_Y, 0.5);
        arena.setBossSpawn(bossSpawnLoc);
        arena.setPlayerSpawn(playerSpawnLoc);

        plugin.getLogger().info("[Cuboss] Mundo listo: " + CUBOSS_WORLD_NAME
                + " | Player spawn actualizado a cuboss_arena 15," + (ARENA_Y + 1) + ",0");
    }

    // ==================== SPAWN ====================

    @Override
    public void spawn() {
        active = true;
        spawnTimestamp = System.currentTimeMillis();

        // El mundo y la arena ya se prepararon en prepareArena()
        World cubossWorld = Bukkit.getWorld(CUBOSS_WORLD_NAME);
        if (cubossWorld == null) {
            plugin.getLogger().severe("[Cuboss] Mundo " + CUBOSS_WORLD_NAME + " no existe en spawn()!");
            active = false;
            manager.onBossDeath(null);
            return;
        }

        Location bossSpawnLoc = arena.getBossSpawn();
        Location safeLoc = getSafeLocation(bossSpawnLoc);

        // Spawn via MythicMobs
        bossEntity = spawnMythicMob(MYTHIC_MOB_ID, safeLoc);

        if (bossEntity == null) {
            plugin.getLogger().severe("[Cuboss] Fallo spawn del MythicMob '" + MYTHIC_MOB_ID + "'.");
            active = false;
            manager.onBossDeath(null);
            return;
        }

        bossUuid = bossEntity.getUniqueId();

        // Aumentar vida del boss
        org.bukkit.attribute.AttributeInstance maxHpAttr = bossEntity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
        if (maxHpAttr != null) {
            double currentMax = maxHpAttr.getBaseValue();
            if (currentMax < 2000) {
                maxHpAttr.setBaseValue(2000);
                bossEntity.setHealth(2000);
                plugin.getLogger().info("[Cuboss] HP aumentada de " + currentMax + " a 2000");
            }
        }
        maxHealth = bossEntity.getMaxHealth();

        // Aumentar daño de ataque masivamente
        org.bukkit.attribute.AttributeInstance atkAttr = bossEntity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE);
        if (atkAttr != null) {
            atkAttr.setBaseValue(50.0); // 25 corazones por golpe base
        }
        // Fuerza V permanente
        bossEntity.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.STRENGTH, Integer.MAX_VALUE, 4, false, false));

        bossEntity.teleport(safeLoc);

        // BossBar global (keyed)
        org.bukkit.NamespacedKey cubossKey = new org.bukkit.NamespacedKey(plugin, "boss_cuboss");
        Bukkit.removeBossBar(cubossKey);
        globalBossBar = Bukkit.createBossBar(cubossKey,
                SmallCaps.convert("§a§l⚔ Cuboss-Slime §8— §2Rey de los Slimes"),
                BarColor.GREEN, BarStyle.SEGMENTED_10);
        globalBossBar.setProgress(1.0);
        for (Player p : Bukkit.getOnlinePlayers()) {
            globalBossBar.addPlayer(p);
        }

        // Efectos de spawn
        cubossWorld.playSound(safeLoc, Sound.ENTITY_WITHER_SPAWN, 1.5f, 0.4f);
        cubossWorld.playSound(safeLoc, Sound.ENTITY_SLIME_SQUISH, 2.0f, 0.5f);

        plugin.getLogger().info("[Cuboss] Boss spawneado en mundo " + CUBOSS_WORLD_NAME
                + " (UUID " + bossUuid + ")");

        // Main loop -> solo deteccion de muerte + timeout + bossbar
        mainLoop = new BukkitRunnable() {
            @Override
            public void run() {
                if (!active) { cancel(); return; }

                // Timeout
                if (System.currentTimeMillis() - spawnTimestamp > BOSS_TIMEOUT_MS) {
                    plugin.getLogger().info("[Cuboss] Timeout de combate alcanzado, terminando.");
                    onTimeout();
                    cancel();
                    return;
                }

                // Deteccion de muerte / entidad invalida
                if (bossEntity == null || bossEntity.isDead() || !bossEntity.isValid()) {
                    onDeath();
                    cancel();
                    return;
                }

                // Actualizar bossbar con HP
                double hp = Math.max(0, bossEntity.getHealth());
                double pct = maxHealth > 0 ? hp / maxHealth : 0;
                if (globalBossBar != null) {
                    globalBossBar.setProgress(Math.min(1.0, Math.max(0.0, pct)));
                }
            }
        }.runTaskTimer(plugin, 20L, 10L); // cada 0.5 s
    }

    // ==================== MYTHIC MOBS SPAWN ====================

    /**
     * Spawn de un MythicMob usando la API de MythicBukkit via reflexion.
     * Si no hay plugin/API disponible, cae al comando {@code mm mobs spawn}.
     * Devuelve la LivingEntity spawneada o null si fallo.
     */
    private LivingEntity spawnMythicMob(String mobId, Location loc) {
        // Intento 1: API MythicBukkit via reflexion (version MythicMobs 5.x)
        try {
            Class<?> mb = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Object inst = mb.getMethod("inst").invoke(null);
            Object mobManager = mb.getMethod("getMobManager").invoke(inst);

            // Optional<MythicMob> getMythicMob(String)
            Object opt = mobManager.getClass().getMethod("getMythicMob", String.class).invoke(mobManager, mobId);
            if (opt instanceof java.util.Optional<?>) {
                java.util.Optional<?> o = (java.util.Optional<?>) opt;
                if (o.isEmpty()) {
                    plugin.getLogger().warning("[Cuboss] MythicMob '" + mobId + "' no encontrado en el registro.");
                    return null;
                }
                Object mythicMob = o.get();

                // AbstractLocation wrap
                Class<?> absLocCls = Class.forName("io.lumine.mythic.api.adapters.AbstractLocation");
                Class<?> bukkitAdapterCls = Class.forName("io.lumine.mythic.bukkit.BukkitAdapter");
                Object absLoc = bukkitAdapterCls.getMethod("adapt", Location.class).invoke(null, loc);

                // spawn(AbstractLocation, double level)
                Object activeMob = mythicMob.getClass()
                        .getMethod("spawn", absLocCls, double.class)
                        .invoke(mythicMob, absLoc, 1.0);
                if (activeMob == null) return null;

                // ActiveMob#getEntity() -> AbstractEntity ; adapt -> Bukkit Entity
                Object absEntity = activeMob.getClass().getMethod("getEntity").invoke(activeMob);
                Object bukkitEntity = bukkitAdapterCls.getMethod("adapt",
                                Class.forName("io.lumine.mythic.api.adapters.AbstractEntity"))
                        .invoke(null, absEntity);
                if (bukkitEntity instanceof LivingEntity) {
                    return (LivingEntity) bukkitEntity;
                }
            }
        } catch (ClassNotFoundException cnfe) {
            plugin.getLogger().info("[Cuboss] API MythicBukkit no disponible, usando comando.");
        } catch (Exception e) {
            plugin.getLogger().warning("[Cuboss] Error API MythicMobs: " + e.getMessage());
        }

        // Intento 2: fallback comando "mm mobs spawn"
        World world = loc.getWorld();
        if (world == null) return null;

        // Snapshot de entidades antes del spawn para detectar cual es la nueva
        java.util.Set<UUID> before = new java.util.HashSet<>();
        for (Entity e : world.getNearbyEntities(loc, 10, 10, 10)) {
            before.add(e.getUniqueId());
        }

        String cmd = "mm mobs spawn " + mobId + " 1 "
                + world.getName() + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ();
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);

        // Buscar la entidad recien spawneada
        for (Entity e : world.getNearbyEntities(loc, 10, 10, 10)) {
            if (before.contains(e.getUniqueId())) continue;
            if (e instanceof LivingEntity && !(e instanceof Player)) {
                return (LivingEntity) e;
            }
        }
        return null;
    }

    // ==================== DAMAGE / LAST HITTER ====================

    @Override
    public void onDamage(EntityDamageByEntityEvent event) {
        if (bossEntity == null) return;
        if (!event.getEntity().getUniqueId().equals(bossUuid)) return;

        Entity damager = event.getDamager();
        if (damager instanceof Player) {
            lastHitter = (Player) damager;
        } else if (damager instanceof org.bukkit.entity.Projectile) {
            Object shooter = ((org.bukkit.entity.Projectile) damager).getShooter();
            if (shooter instanceof Player) lastHitter = (Player) shooter;
        }
    }

    // ==================== DEATH ====================

    private void onDeath() {
        if (deathHandled) return;
        deathHandled = true;
        active = false;

        World world = arena.getBossSpawn() != null ? arena.getBossSpawn().getWorld() : null;

        // Anuncio
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(SmallCaps.convert("§a§l\u2694 \u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550 \u2694"));
        if (lastHitter != null) {
            Bukkit.broadcastMessage(SmallCaps.convert("§2§l  \u00a1CUBOSS-SLIME HA CAIDO!"));
            Bukkit.broadcastMessage(SmallCaps.convert("§7  Ultimo golpe: §e§l" + lastHitter.getName()));
        } else {
            Bukkit.broadcastMessage(SmallCaps.convert("§2§l  \u00a1CUBOSS-SLIME HA CAIDO!"));
        }
        Bukkit.broadcastMessage(SmallCaps.convert("§a§l\u2694 \u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550 \u2694"));
        Bukkit.broadcastMessage("");

        if (world != null) {
            for (Player p : world.getPlayers()) {
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 0.6f, 0.8f);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }
        }

        // Recompensas
        if (lastHitter != null && lastHitter.isOnline()) {
            giveRewards(lastHitter);
        }
        if (world != null) {
            for (Player p : world.getPlayers()) {
                if (p != lastHitter) giveParticipationReward(p);
            }
        }

        cleanupBossBar();
        teleportPlayersOut();
        restoreOriginalLocations();
        manager.onBossDeath(lastHitter);
    }

    private void onTimeout() {
        active = false;
        if (bossEntity != null && !bossEntity.isDead()) bossEntity.remove();
        cleanupBossBar();

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(SmallCaps.convert("§7§l\u2694 §aCuboss-Slime se ha retirado al no ser derrotado a tiempo."));
        Bukkit.broadcastMessage("");
        teleportPlayersOut();
        restoreOriginalLocations();
        manager.onBossDeath(null);
    }

    // ==================== REWARDS ====================

    private void giveRewards(Player player) {
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§a§l\u2694 \u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550 \u2694"));
        player.sendMessage(SmallCaps.convert("§2§l  \u00a1RECOMPENSA DEL ULTIMO GOLPE!"));
        player.sendMessage(SmallCaps.convert("§7  Has derrotado a §a§lCuboss-Slime§7."));
        player.sendMessage(SmallCaps.convert("§a§l\u2694 \u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550 \u2694"));
        player.sendMessage("");

        player.getInventory().addItem(
                new ItemStack(Material.NETHERITE_INGOT, 4),
                new ItemStack(Material.DIAMOND_BLOCK, 16),
                new ItemStack(Material.EMERALD_BLOCK, 32),
                new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 8),
                new ItemStack(Material.EXPERIENCE_BOTTLE, 64),
                new ItemStack(Material.EXPERIENCE_BOTTLE, 64),
                new ItemStack(Material.NETHER_STAR, 1),
                new ItemStack(Material.GOLDEN_APPLE, 32),
                new ItemStack(Material.SLIME_BLOCK, 64)
        );

        if (plugin.getEssenceManager() != null) {
            plugin.getEssenceManager().onBossKill(player);
        }

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "crates give " + player.getName() + " CubossKey 3");
    }

    private void giveParticipationReward(Player player) {
        player.sendMessage(SmallCaps.convert("§7Has participado en la batalla contra §a§lCuboss-Slime§7."));
        player.sendMessage(SmallCaps.convert("§7Recompensa de participacion recibida."));

        player.getInventory().addItem(
                new ItemStack(Material.DIAMOND, 8),
                new ItemStack(Material.GOLDEN_APPLE, 8),
                new ItemStack(Material.EXPERIENCE_BOTTLE, 32)
        );

        if (plugin.getEssenceManager() != null) {
            plugin.getEssenceManager().onBossParticipation(player);
        }
    }

    // ==================== CLEANUP ====================

    private void cleanupBossBar() {
        if (globalBossBar != null) {
            globalBossBar.removeAll();
            globalBossBar = null;
        }
    }

    @Override
    public void cleanup() {
        active = false;
        if (mainLoop != null) mainLoop.cancel();
        if (bossEntity != null && !bossEntity.isDead()) bossEntity.remove();
        cleanupBossBar();
        restoreOriginalLocations();
    }

    /**
     * Restaura las locations originales del ArenaData para que saveConfig
     * no corrompa la configuracion de la arena persistente.
     */
    private void restoreOriginalLocations() {
        if (originalBossSpawn != null) arena.setBossSpawn(originalBossSpawn);
        if (originalPlayerSpawn != null) arena.setPlayerSpawn(originalPlayerSpawn);
    }

    /**
     * Teleporta a todos los jugadores fuera del mundo de Cuboss
     * ANTES de restaurar locations y saveConfig.
     */
    private void teleportPlayersOut() {
        World cubossWorld = Bukkit.getWorld(CUBOSS_WORLD_NAME);
        if (cubossWorld == null) return;
        Location mainSpawn = Bukkit.getWorlds().get(0).getSpawnLocation();
        for (Player p : cubossWorld.getPlayers()) {
            p.teleport(mainSpawn);
            p.sendMessage(SmallCaps.convert("§7La arena de boss se ha cerrado."));
        }
    }

    // ==================== INTERFACE ====================

    @Override public boolean isActive() { return active; }
    @Override public String getBossName() { return "Cuboss-Slime"; }
    @Override public BossType getBossType() { return BossType.CUBOSS_SLIME; }
    @Override public Player getLastHitter() { return lastHitter; }

    @Override
    public boolean isBossEntity(Entity entity) {
        return bossUuid != null && entity != null && bossUuid.equals(entity.getUniqueId());
    }

    @Override
    public boolean isSummonedMob(Entity entity) {
        // MythicMobs gestiona sus propios adds; no los trackeamos.
        return false;
    }

    // ==================== SAFE LOCATION ====================

    private Location getSafeLocation(Location original) {
        World world = original.getWorld();
        if (world == null) return original.clone();
        Location checkLoc = original.clone();
        for (int i = 0; i < 15; i++) {
            Location above = checkLoc.clone().add(0, i, 0);
            Location twoAbove = checkLoc.clone().add(0, i + 1, 0);
            Location threeAbove = checkLoc.clone().add(0, i + 2, 0);
            if (above.getBlock().getType().isAir()
                    && twoAbove.getBlock().getType().isAir()
                    && threeAbove.getBlock().getType().isAir()) {
                Location below = above.clone().subtract(0, 1, 0);
                if (below.getBlock().getType().isSolid()) {
                    return above;
                }
            }
        }
        return original.clone();
    }

    // ==================== WORLD CREATION ====================

    /**
     * Crea o carga el mundo void dedicado para la arena de Cuboss.
     * Este mundo es independiente de cualquier otra arena de boss.
     */
    private World getOrCreateCubossWorld() {
        World world = Bukkit.getWorld(CUBOSS_WORLD_NAME);
        if (world != null) {
            plugin.getLogger().info("[Cuboss] Mundo " + CUBOSS_WORLD_NAME + " ya existe, reutilizando.");
            return world;
        }

        plugin.getLogger().info("[Cuboss] Creando mundo void dedicado: " + CUBOSS_WORLD_NAME);
        try {
            WorldCreator creator = new WorldCreator(CUBOSS_WORLD_NAME);
            creator.generator(new BossArenaManager.VoidChunkGenerator());
            creator.type(WorldType.FLAT);
            creator.generateStructures(false);
            creator.environment(World.Environment.NORMAL);
            world = creator.createWorld();

            if (world != null) {
                com.moonlight.coreprotect.util.GameRuleUtil.set(world, "doDaylightCycle", false);
                com.moonlight.coreprotect.util.GameRuleUtil.set(world, "doWeatherCycle", false);
                com.moonlight.coreprotect.util.GameRuleUtil.set(world, "doMobSpawning", false);
                com.moonlight.coreprotect.util.GameRuleUtil.set(world, "doFireTick", false);
                com.moonlight.coreprotect.util.GameRuleUtil.set(world, "mobGriefing", false);
                world.setTime(6000); // Mediodia permanente
                world.setDifficulty(org.bukkit.Difficulty.HARD);
                plugin.getLogger().info("[Cuboss] Mundo " + CUBOSS_WORLD_NAME + " creado exitosamente.");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("[Cuboss] Error creando mundo: " + e.getMessage());
        }
        return world;
    }

    // ==================== ARENA BUILDER ====================

    private static final int ARENA_RADIUS = 35;
    private static final int WALL_INNER_R = 32;
    private static final int WALL_OUTER_R = 34;
    private static final int WALL_HEIGHT = 8;
    private static final int PILLAR_HEIGHT = 12;

    /**
     * Construye una arena epica de Slime en el mundo void.
     * Coliseo circular grande y plano con tematica verde:
     *  - Suelo plano con anillos concentricos (emerald, lime concrete, prismarine)
     *  - Muros circulares con ventanales de cristal verde
     *  - 8 pilares grandes con faroles marinos
     *  - 4 mini-pilares interiores decorativos
     *  - Cornisa superior conectando todo
     */
    private void buildArena(Location center) {
        World w = center.getWorld();
        if (w == null) return;

        int cx = center.getBlockX();
        int baseY = center.getBlockY() - 1;
        int cz = center.getBlockZ();

        // Si ya esta construida, no repetir
        if (w.getBlockAt(cx, baseY, cz).getType() == Material.SEA_LANTERN) {
            plugin.getLogger().info("[Cuboss] Arena ya construida, omitiendo.");
            return;
        }

        plugin.getLogger().info("[Cuboss] Construyendo arena epica de Cuboss-Slime...");

        // ═══════ PASO 1: Limpiar zona (aire) ═══════
        for (int x = -ARENA_RADIUS - 2; x <= ARENA_RADIUS + 2; x++) {
            for (int z = -ARENA_RADIUS - 2; z <= ARENA_RADIUS + 2; z++) {
                for (int y = -3; y <= PILLAR_HEIGHT + 4; y++) {
                    w.getBlockAt(cx + x, baseY + y, cz + z).setType(Material.AIR);
                }
            }
        }

        // ═══════ PASO 2: Cimentacion + Suelo ═══════
        for (int x = -ARENA_RADIUS; x <= ARENA_RADIUS; x++) {
            for (int z = -ARENA_RADIUS; z <= ARENA_RADIUS; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > ARENA_RADIUS + 0.5) continue;

                // 2 capas de cimentacion solida
                w.getBlockAt(cx + x, baseY - 2, cz + z).setType(Material.DARK_PRISMARINE);
                w.getBlockAt(cx + x, baseY - 1, cz + z).setType(Material.PRISMARINE_BRICKS);

                // Suelo con patron de anillos concentricos
                Material floor = getFloorMaterial(x, z, dist);
                w.getBlockAt(cx + x, baseY, cz + z).setType(floor);
            }
        }

        // ═══════ PASO 3: Muros circulares con ventanas ═══════
        for (int x = -ARENA_RADIUS; x <= ARENA_RADIUS; x++) {
            for (int z = -ARENA_RADIUS; z <= ARENA_RADIUS; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist < WALL_INNER_R - 0.5 || dist > WALL_OUTER_R + 0.5) continue;

                double angDeg = Math.toDegrees(Math.atan2(z, x));
                if (angDeg < 0) angDeg += 360;
                double sectorAng = angDeg % 45;

                boolean pillarZone = sectorAng < 6 || sectorAng > 39;
                boolean windowZone = sectorAng > 14 && sectorAng < 31;

                int height = pillarZone ? PILLAR_HEIGHT : WALL_HEIGHT;

                for (int y = 1; y <= height; y++) {
                    Material mat;
                    if (pillarZone) {
                        mat = Material.DARK_PRISMARINE;
                        if (y == height) mat = Material.SEA_LANTERN;
                        if (y == height - 1) mat = Material.PRISMARINE;
                    } else {
                        mat = Material.PRISMARINE_BRICKS;
                        if (y == 1 || y == WALL_HEIGHT) mat = Material.DARK_PRISMARINE;
                        if (windowZone && y >= 3 && y <= 6) mat = Material.LIME_STAINED_GLASS;
                    }
                    w.getBlockAt(cx + x, baseY + y, cz + z).setType(mat);
                }
            }
        }

        // ═══════ PASO 4: Cornisa superior (conecta muros) ═══════
        for (int x = -ARENA_RADIUS; x <= ARENA_RADIUS; x++) {
            for (int z = -ARENA_RADIUS; z <= ARENA_RADIUS; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist >= WALL_INNER_R - 1 && dist <= WALL_OUTER_R + 1) {
                    w.getBlockAt(cx + x, baseY + WALL_HEIGHT + 1, cz + z).setType(Material.DARK_PRISMARINE);
                    // Segunda capa de cornisa con detalles
                    if (dist >= WALL_INNER_R && dist <= WALL_OUTER_R) {
                        w.getBlockAt(cx + x, baseY + WALL_HEIGHT + 2, cz + z).setType(Material.PRISMARINE);
                    }
                }
            }
        }

        // ═══════ PASO 5: Marca central del boss (plana, sin pedestal) ═══════
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (Math.abs(x) + Math.abs(z) <= 2) {
                    w.getBlockAt(cx + x, baseY, cz + z).setType(Material.EMERALD_BLOCK);
                }
            }
        }
        w.getBlockAt(cx, baseY, cz).setType(Material.SEA_LANTERN);

        // ═══════ PASO 6: 4 mini-pilares interiores con luz ═══════
        int[][] miniPillarPos = {{16, 0}, {-16, 0}, {0, 16}, {0, -16}};
        for (int[] pos : miniPillarPos) {
            int px = cx + pos[0];
            int pz = cz + pos[1];
            for (int y = 1; y <= 6; y++) {
                Material mat = Material.PRISMARINE;
                if (y == 1) mat = Material.DARK_PRISMARINE;
                if (y == 6) mat = Material.SEA_LANTERN;
                w.getBlockAt(px, baseY + y, pz).setType(mat);
            }
            // Base 3x3 del mini-pilar
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    w.getBlockAt(px + dx, baseY + 1, pz + dz).setType(Material.DARK_PRISMARINE);
                }
            }
        }

        // ═══════ PASO 7: 4 columnas decorativas diagonales ═══════
        int[][] diagPos = {{13, 13}, {-13, 13}, {13, -13}, {-13, -13}};
        for (int[] pos : diagPos) {
            int bx = cx + pos[0];
            int bz = cz + pos[1];
            for (int y = 1; y <= 4; y++) {
                w.getBlockAt(bx, baseY + y, bz).setType(Material.LIME_CONCRETE);
            }
            w.getBlockAt(bx, baseY + 5, bz).setType(Material.LIME_STAINED_GLASS);
            w.getBlockAt(bx, baseY + 6, bz).setType(Material.SEA_LANTERN);
        }

        // ═══════ PASO 8: Anillo decorativo en el suelo (r=24) ═══════
        for (int x = -26; x <= 26; x++) {
            for (int z = -26; z <= 26; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (Math.abs(dist - 24) < 0.6) {
                    w.getBlockAt(cx + x, baseY, cz + z).setType(Material.DARK_PRISMARINE);
                }
            }
        }

        // ═══════ PASO 9: Borde exterior decorativo (escalones) ═══════
        for (int x = -ARENA_RADIUS - 1; x <= ARENA_RADIUS + 1; x++) {
            for (int z = -ARENA_RADIUS - 1; z <= ARENA_RADIUS + 1; z++) {
                double dist = Math.sqrt(x * x + z * z);
                // Escalon exterior descendente
                if (dist > ARENA_RADIUS && dist <= ARENA_RADIUS + 1.5) {
                    w.getBlockAt(cx + x, baseY - 1, cz + z).setType(Material.DARK_PRISMARINE);
                }
            }
        }

        plugin.getLogger().info("[Cuboss] Arena epica construida exitosamente.");
    }

    /**
     * Devuelve el material del suelo segun la distancia al centro.
     * Patron: Emerald -> Slime -> Dark Prismarine -> Prismarine Bricks (con luces) -> Borde
     */
    private Material getFloorMaterial(int x, int z, double dist) {
        // Centro: esmeralda
        if (dist <= 3) return Material.EMERALD_BLOCK;
        // Anillo verde (lime concrete, NO slime block)
        if (dist <= 8) return Material.LIME_CONCRETE;
        // Borde del anillo verde
        if (dist <= 9.5) return Material.DARK_PRISMARINE;
        // Area principal de combate
        if (dist <= 30) {
            // Sea lanterns en lineas radiales (8 lineas, cada 45 grados)
            double ang = Math.toDegrees(Math.atan2(z, x));
            if (ang < 0) ang += 360;
            double sectorAng = ang % 45;
            if ((sectorAng < 2 || sectorAng > 43) && dist > 12 && ((int) dist) % 5 == 0) {
                return Material.SEA_LANTERN;
            }
            // Anillo decorativo intermedio
            if (Math.abs(dist - 20) < 0.6) return Material.DARK_PRISMARINE;
            return Material.PRISMARINE_BRICKS;
        }
        // Borde exterior
        if (dist <= WALL_INNER_R) return Material.DARK_PRISMARINE;
        return Material.PRISMARINE;
    }
}
