package com.moonlight.coreprotect.bossarena;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * Apocalypse Type A — Heraldo del Vacío.
 *
 * MythicMob: apocalypse_type_a
 * Mundo dedicado: apocalypse_arena (void, noche perpetua)
 * Arena: Coliseo oscuro con tematica del Vacío/End
 *   - Obsidiana, crying obsidian, purpur, end stone
 *   - Cristal morado, soul lanterns
 *   - Ambiente oscuro y amenazante
 */
public class ApocalypseTypeABoss implements Boss {

    private static final String MYTHIC_MOB_ID = "apocalypse_type_a";

    // Mundo void dedicado (NO comparte con otros bosses)
    private static final String WORLD_NAME = "apocalypse_arena";
    private static final int ARENA_Y = 100;

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

    public ApocalypseTypeABoss(CoreProtectPlugin plugin, BossArenaManager manager, ArenaData arena) {
        this.plugin = plugin;
        this.manager = manager;
        this.arena = arena;
    }

    // ==================== PREPARE (antes del countdown) ====================

    @Override
    public void prepareArena() {
        plugin.getLogger().info("[Apocalypse] prepareArena() — creando mundo y arena...");

        World apocWorld = getOrCreateWorld();
        if (apocWorld == null) {
            plugin.getLogger().severe("[Apocalypse] No se pudo crear el mundo " + WORLD_NAME);
            return;
        }

        Location arenaCenter = new Location(apocWorld, 0, ARENA_Y, 0);

        int cx = arenaCenter.getBlockX() >> 4;
        int cz = arenaCenter.getBlockZ() >> 4;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                apocWorld.setChunkForceLoaded(cx + dx, cz + dz, true);
                apocWorld.loadChunk(cx + dx, cz + dz, true);
            }
        }

        buildArena(arenaCenter);

        originalBossSpawn = arena.getBossSpawn() != null ? arena.getBossSpawn().clone() : null;
        originalPlayerSpawn = arena.getPlayerSpawn() != null ? arena.getPlayerSpawn().clone() : null;

        Location bossSpawnLoc = new Location(apocWorld, 0.5, ARENA_Y + 3, 0.5);
        Location playerSpawnLoc = new Location(apocWorld, 15.5, ARENA_Y + 1, 0.5);
        arena.setBossSpawn(bossSpawnLoc);
        arena.setPlayerSpawn(playerSpawnLoc);

        plugin.getLogger().info("[Apocalypse] Mundo listo: " + WORLD_NAME
                + " | Player spawn actualizado a apocalypse_arena 15," + (ARENA_Y + 1) + ",0");
    }

    // ==================== SPAWN ====================

    @Override
    public void spawn() {
        active = true;
        spawnTimestamp = System.currentTimeMillis();

        World apocWorld = Bukkit.getWorld(WORLD_NAME);
        if (apocWorld == null) {
            plugin.getLogger().severe("[Apocalypse] Mundo " + WORLD_NAME + " no existe en spawn()!");
            active = false;
            manager.onBossDeath(null);
            return;
        }

        Location bossSpawnLoc = arena.getBossSpawn();
        Location safeLoc = getSafeLocation(bossSpawnLoc);

        // Spawn via MythicMobs
        bossEntity = spawnMythicMob(MYTHIC_MOB_ID, safeLoc);

        if (bossEntity == null) {
            plugin.getLogger().severe("[Apocalypse] Fallo spawn del MythicMob '" + MYTHIC_MOB_ID + "'.");
            active = false;
            manager.onBossDeath(null);
            return;
        }

        bossUuid = bossEntity.getUniqueId();

        // Aumentar vida
        org.bukkit.attribute.AttributeInstance maxHpAttr = bossEntity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
        if (maxHpAttr != null) {
            double currentMax = maxHpAttr.getBaseValue();
            if (currentMax < 10000) {
                maxHpAttr.setBaseValue(10000);
                bossEntity.setHealth(10000);
                plugin.getLogger().info("[Apocalypse] HP aumentada de " + currentMax + " a 10000");
            }
        }
        maxHealth = bossEntity.getMaxHealth();

        // Aumentar daño de ataque masivamente
        org.bukkit.attribute.AttributeInstance atkAttr = bossEntity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE);
        if (atkAttr != null) {
            atkAttr.setBaseValue(60.0); // 30 corazones por golpe base
        }
        // Fuerza V permanente
        bossEntity.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.STRENGTH, Integer.MAX_VALUE, 4, false, false));

        bossEntity.teleport(safeLoc);

        // BossBar global — morada (keyed)
        org.bukkit.NamespacedKey apoKey = new org.bukkit.NamespacedKey(plugin, "boss_apocalypse");
        Bukkit.removeBossBar(apoKey);
        globalBossBar = Bukkit.createBossBar(apoKey,
                SmallCaps.convert("§5§l⚔ Apocalypse §8— §d Heraldo del Vacío"),
                BarColor.PURPLE, BarStyle.SEGMENTED_10);
        globalBossBar.setProgress(1.0);
        for (Player p : Bukkit.getOnlinePlayers()) {
            globalBossBar.addPlayer(p);
        }

        // Efectos de spawn oscuros
        apocWorld.playSound(safeLoc, Sound.ENTITY_WITHER_SPAWN, 1.5f, 0.3f);
        apocWorld.playSound(safeLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f);
        apocWorld.playSound(safeLoc, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 2.0f, 0.5f);

        plugin.getLogger().info("[Apocalypse] Boss spawneado en mundo " + WORLD_NAME
                + " (UUID " + bossUuid + ")");

        // Main loop
        mainLoop = new BukkitRunnable() {
            @Override
            public void run() {
                if (!active) { cancel(); return; }

                if (System.currentTimeMillis() - spawnTimestamp > BOSS_TIMEOUT_MS) {
                    plugin.getLogger().info("[Apocalypse] Timeout de combate alcanzado.");
                    onTimeout();
                    cancel();
                    return;
                }

                if (bossEntity == null || bossEntity.isDead() || !bossEntity.isValid()) {
                    onDeath();
                    cancel();
                    return;
                }

                double hp = Math.max(0, bossEntity.getHealth());
                double pct = maxHealth > 0 ? hp / maxHealth : 0;
                if (globalBossBar != null) {
                    globalBossBar.setProgress(Math.min(1.0, Math.max(0.0, pct)));
                }
            }
        }.runTaskTimer(plugin, 20L, 10L);
    }

    // ==================== MYTHIC MOBS SPAWN ====================

    private LivingEntity spawnMythicMob(String mobId, Location loc) {
        try {
            Class<?> mb = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Object inst = mb.getMethod("inst").invoke(null);
            Object mobManager = mb.getMethod("getMobManager").invoke(inst);

            Object opt = mobManager.getClass().getMethod("getMythicMob", String.class).invoke(mobManager, mobId);
            if (opt instanceof java.util.Optional<?>) {
                java.util.Optional<?> o = (java.util.Optional<?>) opt;
                if (o.isEmpty()) {
                    plugin.getLogger().warning("[Apocalypse] MythicMob '" + mobId + "' no encontrado.");
                    return null;
                }
                Object mythicMob = o.get();

                Class<?> absLocCls = Class.forName("io.lumine.mythic.api.adapters.AbstractLocation");
                Class<?> bukkitAdapterCls = Class.forName("io.lumine.mythic.bukkit.BukkitAdapter");
                Object absLoc = bukkitAdapterCls.getMethod("adapt", Location.class).invoke(null, loc);

                Object activeMob = mythicMob.getClass()
                        .getMethod("spawn", absLocCls, double.class)
                        .invoke(mythicMob, absLoc, 1.0);
                if (activeMob == null) return null;

                Object absEntity = activeMob.getClass().getMethod("getEntity").invoke(activeMob);
                Object bukkitEntity = bukkitAdapterCls.getMethod("adapt",
                                Class.forName("io.lumine.mythic.api.adapters.AbstractEntity"))
                        .invoke(null, absEntity);
                if (bukkitEntity instanceof LivingEntity) {
                    return (LivingEntity) bukkitEntity;
                }
            }
        } catch (ClassNotFoundException cnfe) {
            plugin.getLogger().info("[Apocalypse] API MythicBukkit no disponible, usando comando.");
        } catch (Exception e) {
            plugin.getLogger().warning("[Apocalypse] Error API MythicMobs: " + e.getMessage());
        }

        // Fallback: comando MM
        World world = loc.getWorld();
        if (world == null) return null;

        java.util.Set<UUID> before = new java.util.HashSet<>();
        for (Entity e : world.getNearbyEntities(loc, 10, 10, 10)) {
            before.add(e.getUniqueId());
        }

        String cmd = "mm mobs spawn " + mobId + " 1 "
                + world.getName() + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ();
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        } catch (Exception ignored) {}

        for (Entity e : world.getNearbyEntities(loc, 10, 10, 10)) {
            if (before.contains(e.getUniqueId())) continue;
            if (e instanceof LivingEntity && !(e instanceof Player)) {
                return (LivingEntity) e;
            }
        }

        // Fallback final: Warden vanilla como boss
        plugin.getLogger().warning("[Apocalypse] MythicMob no disponible, usando Warden vanilla.");
        LivingEntity warden = (LivingEntity) world.spawnEntity(loc, org.bukkit.entity.EntityType.WARDEN);
        warden.setCustomName(SmallCaps.convert("§5§l☠ Apocalypse §8— §dHeraldo del Vacío"));
        warden.setCustomNameVisible(true);
        warden.setRemoveWhenFarAway(false);
        return warden;
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

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(SmallCaps.convert("§5§l\u2694 \u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550 \u2694"));
        if (lastHitter != null) {
            Bukkit.broadcastMessage(SmallCaps.convert("§d§l  \u00a1APOCALYPSE HA CAIDO!"));
            Bukkit.broadcastMessage(SmallCaps.convert("§7  Ultimo golpe: §e§l" + lastHitter.getName()));
        } else {
            Bukkit.broadcastMessage(SmallCaps.convert("§d§l  \u00a1APOCALYPSE HA CAIDO!"));
        }
        Bukkit.broadcastMessage(SmallCaps.convert("§5§l\u2694 \u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550 \u2694"));
        Bukkit.broadcastMessage("");

        if (world != null) {
            for (Player p : world.getPlayers()) {
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 0.6f, 0.5f);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }
        }

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
        Bukkit.broadcastMessage(SmallCaps.convert("§7§l\u2694 §5Apocalypse se ha desvanecido en el vacío."));
        Bukkit.broadcastMessage("");
        teleportPlayersOut();
        restoreOriginalLocations();
        manager.onBossDeath(null);
    }

    // ==================== REWARDS ====================

    private void giveRewards(Player player) {
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§5§l\u2694 \u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550 \u2694"));
        player.sendMessage(SmallCaps.convert("§d§l  \u00a1RECOMPENSA DEL ULTIMO GOLPE!"));
        player.sendMessage(SmallCaps.convert("§7  Has derrotado a §5§lApocalypse§7."));
        player.sendMessage(SmallCaps.convert("§5§l\u2694 \u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550 \u2694"));
        player.sendMessage("");

        player.getInventory().addItem(
                new ItemStack(Material.NETHERITE_INGOT, 6),
                new ItemStack(Material.DIAMOND_BLOCK, 24),
                new ItemStack(Material.EMERALD_BLOCK, 48),
                new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 12),
                new ItemStack(Material.EXPERIENCE_BOTTLE, 64),
                new ItemStack(Material.EXPERIENCE_BOTTLE, 64),
                new ItemStack(Material.EXPERIENCE_BOTTLE, 64),
                new ItemStack(Material.NETHER_STAR, 2),
                new ItemStack(Material.GOLDEN_APPLE, 48),
                new ItemStack(Material.END_CRYSTAL, 4)
        );

        if (plugin.getEssenceManager() != null) {
            plugin.getEssenceManager().onBossKill(player);
        }

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "crates give " + player.getName() + " ApocalypseKey 3");
    }

    private void giveParticipationReward(Player player) {
        player.sendMessage(SmallCaps.convert("§7Has participado en la batalla contra §5§lApocalypse§7."));
        player.sendMessage(SmallCaps.convert("§7Recompensa de participacion recibida."));

        player.getInventory().addItem(
                new ItemStack(Material.DIAMOND, 12),
                new ItemStack(Material.GOLDEN_APPLE, 12),
                new ItemStack(Material.EXPERIENCE_BOTTLE, 48)
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

    private void restoreOriginalLocations() {
        if (originalBossSpawn != null) arena.setBossSpawn(originalBossSpawn);
        if (originalPlayerSpawn != null) arena.setPlayerSpawn(originalPlayerSpawn);
    }

    private void teleportPlayersOut() {
        World apocWorld = Bukkit.getWorld(WORLD_NAME);
        if (apocWorld == null) return;
        Location mainSpawn = Bukkit.getWorlds().get(0).getSpawnLocation();
        for (Player p : apocWorld.getPlayers()) {
            p.teleport(mainSpawn);
            p.sendMessage(SmallCaps.convert("§7La arena de boss se ha cerrado."));
        }
    }

    // ==================== INTERFACE ====================

    @Override public boolean isActive() { return active; }
    @Override public String getBossName() { return "Apocalypse"; }
    @Override public BossType getBossType() { return BossType.APOCALYPSE_TYPE_A; }
    @Override public Player getLastHitter() { return lastHitter; }

    @Override
    public boolean isBossEntity(Entity entity) {
        return bossUuid != null && entity != null && bossUuid.equals(entity.getUniqueId());
    }

    @Override
    public boolean isSummonedMob(Entity entity) {
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

    private World getOrCreateWorld() {
        World world = Bukkit.getWorld(WORLD_NAME);
        if (world != null) {
            plugin.getLogger().info("[Apocalypse] Mundo " + WORLD_NAME + " ya existe, reutilizando.");
            return world;
        }

        plugin.getLogger().info("[Apocalypse] Creando mundo void dedicado: " + WORLD_NAME);
        try {
            WorldCreator creator = new WorldCreator(WORLD_NAME);
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
                world.setTime(18000); // Noche perpetua
                world.setDifficulty(org.bukkit.Difficulty.HARD);
                plugin.getLogger().info("[Apocalypse] Mundo " + WORLD_NAME + " creado (THE_END, noche).");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("[Apocalypse] Error creando mundo: " + e.getMessage());
        }
        return world;
    }

    // ==================== ARENA BUILDER ====================

    private static final int ARENA_RADIUS = 28;
    private static final int WALL_INNER_R = 25;
    private static final int WALL_OUTER_R = 27;
    private static final int WALL_HEIGHT = 10;
    private static final int PILLAR_HEIGHT = 15;

    /**
     * Construye una arena oscura del Vacío.
     * Coliseo circular con tematica End/Nether oscura:
     *  - Suelo: obsidiana, crying obsidian, purpur, end stone bricks
     *  - Muros: deepslate bricks + cristal morado
     *  - Pilares: obsidiana + soul lanterns
     *  - Pedestal: crying obsidian + end portal frames
     *  - 4 piras de fuego de almas interiores
     *  - Iluminacion tenue con soul lanterns
     */
    private void buildArena(Location center) {
        World w = center.getWorld();
        if (w == null) return;

        int cx = center.getBlockX();
        int baseY = center.getBlockY() - 1;
        int cz = center.getBlockZ();

        // Si ya esta construida, no repetir
        if (w.getBlockAt(cx, baseY, cz).getType() == Material.CRYING_OBSIDIAN) {
            plugin.getLogger().info("[Apocalypse] Arena ya construida, omitiendo.");
            return;
        }

        plugin.getLogger().info("[Apocalypse] Construyendo arena del Vacío...");

        // ═══════ PASO 1: Limpiar zona ═══════
        for (int x = -ARENA_RADIUS - 2; x <= ARENA_RADIUS + 2; x++) {
            for (int z = -ARENA_RADIUS - 2; z <= ARENA_RADIUS + 2; z++) {
                for (int y = -3; y <= PILLAR_HEIGHT + 5; y++) {
                    w.getBlockAt(cx + x, baseY + y, cz + z).setType(Material.AIR);
                }
            }
        }

        // ═══════ PASO 2: Cimentacion + Suelo oscuro ═══════
        for (int x = -ARENA_RADIUS; x <= ARENA_RADIUS; x++) {
            for (int z = -ARENA_RADIUS; z <= ARENA_RADIUS; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > ARENA_RADIUS + 0.5) continue;

                // 2 capas de cimentacion
                w.getBlockAt(cx + x, baseY - 2, cz + z).setType(Material.OBSIDIAN);
                w.getBlockAt(cx + x, baseY - 1, cz + z).setType(Material.DEEPSLATE_BRICKS);

                // Suelo con patron oscuro
                Material floor = getFloorMaterial(x, z, dist);
                w.getBlockAt(cx + x, baseY, cz + z).setType(floor);
            }
        }

        // ═══════ PASO 3: Muros circulares con ventanales morados ═══════
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
                        // Pilares: obsidiana con crying obsidian y soul lanterns
                        mat = Material.OBSIDIAN;
                        if (y % 4 == 0) mat = Material.CRYING_OBSIDIAN;
                        if (y == height) mat = Material.SOUL_LANTERN;
                        if (y == height - 1) mat = Material.CRYING_OBSIDIAN;
                    } else {
                        // Muros: deepslate bricks con ventanas moradas
                        mat = Material.DEEPSLATE_BRICKS;
                        if (y == 1) mat = Material.POLISHED_DEEPSLATE;
                        if (y == WALL_HEIGHT) mat = Material.POLISHED_DEEPSLATE;
                        if (windowZone && y >= 3 && y <= 7) mat = Material.PURPLE_STAINED_GLASS;
                    }
                    w.getBlockAt(cx + x, baseY + y, cz + z).setType(mat);
                }
            }
        }

        // ═══════ PASO 4: Cornisa superior ═══════
        for (int x = -ARENA_RADIUS; x <= ARENA_RADIUS; x++) {
            for (int z = -ARENA_RADIUS; z <= ARENA_RADIUS; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist >= WALL_INNER_R - 1 && dist <= WALL_OUTER_R + 1) {
                    w.getBlockAt(cx + x, baseY + WALL_HEIGHT + 1, cz + z).setType(Material.POLISHED_DEEPSLATE);
                    if (dist >= WALL_INNER_R && dist <= WALL_OUTER_R) {
                        w.getBlockAt(cx + x, baseY + WALL_HEIGHT + 2, cz + z).setType(Material.DEEPSLATE_TILES);
                    }
                }
            }
        }

        // ═══════ PASO 5: Pedestal central del boss ═══════
        // Base hexagonal de crying obsidian
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                if (Math.abs(x) + Math.abs(z) <= 4) {
                    w.getBlockAt(cx + x, baseY + 1, cz + z).setType(Material.CRYING_OBSIDIAN);
                }
                if (Math.abs(x) + Math.abs(z) <= 3) {
                    w.getBlockAt(cx + x, baseY + 2, cz + z).setType(Material.OBSIDIAN);
                }
                if (Math.abs(x) + Math.abs(z) <= 1) {
                    w.getBlockAt(cx + x, baseY + 2, cz + z).setType(Material.CRYING_OBSIDIAN);
                }
            }
        }
        // End portal frames en las 4 esquinas del pedestal
        w.getBlockAt(cx + 3, baseY + 2, cz).setType(Material.END_PORTAL_FRAME);
        w.getBlockAt(cx - 3, baseY + 2, cz).setType(Material.END_PORTAL_FRAME);
        w.getBlockAt(cx, baseY + 2, cz + 3).setType(Material.END_PORTAL_FRAME);
        w.getBlockAt(cx, baseY + 2, cz - 3).setType(Material.END_PORTAL_FRAME);
        // Centro brillante
        w.getBlockAt(cx, baseY + 2, cz).setType(Material.SOUL_LANTERN);

        // ═══════ PASO 6: 4 piras de fuego de almas ═══════
        int[][] pyrePos = {{12, 0}, {-12, 0}, {0, 12}, {0, -12}};
        for (int[] pos : pyrePos) {
            int px = cx + pos[0];
            int pz = cz + pos[1];
            // Base 3x3 de soul sand
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    w.getBlockAt(px + dx, baseY + 1, pz + dz).setType(Material.SOUL_SOIL);
                }
            }
            // Pilar central con fuego de almas
            w.getBlockAt(px, baseY + 1, pz).setType(Material.OBSIDIAN);
            w.getBlockAt(px, baseY + 2, pz).setType(Material.OBSIDIAN);
            w.getBlockAt(px, baseY + 3, pz).setType(Material.CRYING_OBSIDIAN);
            w.getBlockAt(px, baseY + 4, pz).setType(Material.SOUL_LANTERN);
            // Soul fire alrededor
            w.getBlockAt(px + 1, baseY + 2, pz).setType(Material.SOUL_FIRE);
            w.getBlockAt(px - 1, baseY + 2, pz).setType(Material.SOUL_FIRE);
            w.getBlockAt(px, baseY + 2, pz + 1).setType(Material.SOUL_FIRE);
            w.getBlockAt(px, baseY + 2, pz - 1).setType(Material.SOUL_FIRE);
        }

        // ═══════ PASO 7: 4 obeliscos diagonales ═══════
        int[][] obeliskPos = {{10, 10}, {-10, 10}, {10, -10}, {-10, -10}};
        for (int[] pos : obeliskPos) {
            int bx = cx + pos[0];
            int bz = cz + pos[1];
            // Obelisco oscuro: deepslate + purpur
            for (int y = 1; y <= 7; y++) {
                Material mat = Material.POLISHED_DEEPSLATE;
                if (y == 4) mat = Material.PURPUR_BLOCK;
                if (y == 7) mat = Material.PURPUR_BLOCK;
                w.getBlockAt(bx, baseY + y, bz).setType(mat);
            }
            w.getBlockAt(bx, baseY + 8, bz).setType(Material.END_ROD);
            // Base 3x3
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    w.getBlockAt(bx + dx, baseY + 1, bz + dz).setType(Material.DEEPSLATE_TILES);
                }
            }
        }

        // ═══════ PASO 8: Anillo de end stone bricks en el suelo (r=18) ═══════
        for (int x = -20; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (Math.abs(dist - 18) < 0.6) {
                    w.getBlockAt(cx + x, baseY, cz + z).setType(Material.END_STONE_BRICKS);
                }
            }
        }

        // ═══════ PASO 9: Anillo de cadenas colgantes desde la cornisa ═══════
        for (int x = -ARENA_RADIUS; x <= ARENA_RADIUS; x++) {
            for (int z = -ARENA_RADIUS; z <= ARENA_RADIUS; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (Math.abs(dist - (WALL_INNER_R - 2)) < 0.6) {
                    double angDeg = Math.toDegrees(Math.atan2(z, x));
                    if (angDeg < 0) angDeg += 360;
                    int angInt = (int) angDeg;
                    // Cadenas cada ~15 grados
                    if (angInt % 15 < 2) {
                        for (int y = WALL_HEIGHT - 1; y >= WALL_HEIGHT - 4; y--) {
                            w.getBlockAt(cx + x, baseY + y, cz + z).setType(Material.CHAIN);
                        }
                        w.getBlockAt(cx + x, baseY + WALL_HEIGHT - 5, cz + z).setType(Material.SOUL_LANTERN);
                    }
                }
            }
        }

        // ═══════ PASO 10: Borde exterior escalonado ═══════
        for (int x = -ARENA_RADIUS - 1; x <= ARENA_RADIUS + 1; x++) {
            for (int z = -ARENA_RADIUS - 1; z <= ARENA_RADIUS + 1; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > ARENA_RADIUS && dist <= ARENA_RADIUS + 1.5) {
                    w.getBlockAt(cx + x, baseY - 1, cz + z).setType(Material.OBSIDIAN);
                }
            }
        }

        plugin.getLogger().info("[Apocalypse] Arena del Vacío construida exitosamente.");
    }

    /**
     * Material del suelo segun la distancia al centro.
     * Patron oscuro: Crying Obsidian -> Obsidian -> Deepslate Bricks -> End Stone Bricks -> Borde
     */
    private Material getFloorMaterial(int x, int z, double dist) {
        // Centro: crying obsidian
        if (dist <= 3) return Material.CRYING_OBSIDIAN;
        // Anillo de obsidiana
        if (dist <= 7) return Material.OBSIDIAN;
        // Borde interior
        if (dist <= 8.5) return Material.POLISHED_DEEPSLATE;
        // Area principal de combate
        if (dist <= 23) {
            // Soul lanterns en lineas radiales (8 lineas, cada 45 grados)
            double ang = Math.toDegrees(Math.atan2(z, x));
            if (ang < 0) ang += 360;
            double sectorAng = ang % 45;
            if ((sectorAng < 2 || sectorAng > 43) && dist > 10 && ((int) dist) % 5 == 0) {
                return Material.SOUL_LANTERN;
            }
            // Anillo decorativo intermedio
            if (Math.abs(dist - 15) < 0.6) return Material.POLISHED_DEEPSLATE;
            // Patron ajedrez sutil con deepslate tiles
            if ((x + z) % 6 == 0 && (x - z) % 6 == 0) return Material.DEEPSLATE_TILES;
            return Material.DEEPSLATE_BRICKS;
        }
        // Borde exterior
        if (dist <= WALL_INNER_R) return Material.POLISHED_DEEPSLATE;
        return Material.END_STONE_BRICKS;
    }
}
