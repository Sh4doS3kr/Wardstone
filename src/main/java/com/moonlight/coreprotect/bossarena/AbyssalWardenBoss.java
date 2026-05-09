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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * Warlord Kragan (slot ABYSSAL_WARDEN).
 *
 * Implementacion minima: spawnea el MythicMob {@code warlord_kragan_the_bloodfury}
 * en la arena y delega TODA la logica (AI, skills, fases) al propio MythicMobs.
 * Este wrapper solo se encarga de:
 *  - Spawn en la posicion exacta de la arena
 *  - BossBar global con HP en vivo
 *  - Deteccion de muerte -> recompensas al ultimo golpeador
 *  - Timeout de seguridad
 *  - Cleanup al terminar
 */
public class AbyssalWardenBoss implements Boss {

    // MythicMob ID a spawnear
    private static final String MYTHIC_MOB_ID = "warlord_kragan_the_bloodfury";

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

    public AbyssalWardenBoss(CoreProtectPlugin plugin, BossArenaManager manager, ArenaData arena) {
        this.plugin = plugin;
        this.manager = manager;
        this.arena = arena;
    }

    // ==================== SPAWN ====================

    @Override
    public void spawn() {
        Location spawnLoc = arena.getBossSpawn();
        if (spawnLoc == null || spawnLoc.getWorld() == null) {
            plugin.getLogger().severe("[Kragan] Arena sin bossSpawn configurado.");
            return;
        }

        active = true;
        spawnTimestamp = System.currentTimeMillis();

        // Spawn via MythicMobs (API reflexion -> fallback a comando)
        bossEntity = spawnMythicMob(MYTHIC_MOB_ID, spawnLoc);

        if (bossEntity == null) {
            plugin.getLogger().severe("[Kragan] Fallo spawn del MythicMob '" + MYTHIC_MOB_ID + "'.");
            active = false;
            manager.onBossDeath(null);
            return;
        }

        bossUuid = bossEntity.getUniqueId();
        maxHealth = bossEntity.getMaxHealth();

        // Aumentar daño de ataque
        org.bukkit.attribute.AttributeInstance atkAttr = bossEntity.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE);
        if (atkAttr != null) {
            atkAttr.setBaseValue(25.0);
        }
        // Fuerza III permanente
        bossEntity.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.STRENGTH, Integer.MAX_VALUE, 2, false, false));

        // Teleportar a la posicion exacta por si MythicMobs lo movio
        bossEntity.teleport(spawnLoc);

        // BossBar global (keyed)
        org.bukkit.NamespacedKey kraganKey = new org.bukkit.NamespacedKey(plugin, "boss_kragan");
        Bukkit.removeBossBar(kraganKey);
        globalBossBar = Bukkit.createBossBar(kraganKey,
                SmallCaps.convert("§4§l⚔ Warlord Kragan §8— §cSeñor de la Furia Sangrienta"),
                BarColor.RED, BarStyle.SEGMENTED_10);
        globalBossBar.setProgress(1.0);
        for (Player p : Bukkit.getOnlinePlayers()) {
            globalBossBar.addPlayer(p);
        }

        // Efectos de spawn
        World world = spawnLoc.getWorld();
        world.playSound(spawnLoc, Sound.ENTITY_WITHER_SPAWN, 1.5f, 0.4f);
        world.playSound(spawnLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.6f);

        plugin.getLogger().info("[Kragan] Boss spawneado en "
                + spawnLoc.getBlockX() + ", " + spawnLoc.getBlockY() + ", " + spawnLoc.getBlockZ()
                + " (UUID " + bossUuid + ")");

        // Main loop -> solo deteccion de muerte + timeout + bossbar
        mainLoop = new BukkitRunnable() {
            @Override
            public void run() {
                if (!active) { cancel(); return; }

                // Timeout
                if (System.currentTimeMillis() - spawnTimestamp > BOSS_TIMEOUT_MS) {
                    plugin.getLogger().info("[Kragan] Timeout de combate alcanzado, terminando.");
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
                    plugin.getLogger().warning("[Kragan] MythicMob '" + mobId + "' no encontrado en el registro.");
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
            plugin.getLogger().info("[Kragan] API MythicBukkit no disponible, usando comando.");
        } catch (Exception e) {
            plugin.getLogger().warning("[Kragan] Error API MythicMobs: " + e.getMessage());
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
        Bukkit.broadcastMessage(SmallCaps.convert("§4§l⚔ ═══════════════════════════ ⚔"));
        if (lastHitter != null) {
            Bukkit.broadcastMessage(SmallCaps.convert("§c§l  ¡WARLORD KRAGAN HA CAIDO!"));
            Bukkit.broadcastMessage(SmallCaps.convert("§7  Ultimo golpe: §e§l" + lastHitter.getName()));
        } else {
            Bukkit.broadcastMessage(SmallCaps.convert("§c§l  ¡WARLORD KRAGAN HA CAIDO!"));
        }
        Bukkit.broadcastMessage(SmallCaps.convert("§4§l⚔ ═══════════════════════════ ⚔"));
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
        manager.onBossDeath(lastHitter);
    }

    private void onTimeout() {
        active = false;
        if (bossEntity != null && !bossEntity.isDead()) bossEntity.remove();
        cleanupBossBar();

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(SmallCaps.convert("§7§l⚔ §cWarlord Kragan se ha retirado al no ser derrotado a tiempo."));
        Bukkit.broadcastMessage("");
        manager.onBossDeath(null);
    }

    // ==================== REWARDS ====================

    private void giveRewards(Player player) {
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§4§l⚔ ═══════════════════════════ ⚔"));
        player.sendMessage(SmallCaps.convert("§c§l  ¡RECOMPENSA DEL ULTIMO GOLPE!"));
        player.sendMessage(SmallCaps.convert("§7  Has derrotado a §4§lWarlord Kragan§7."));
        player.sendMessage(SmallCaps.convert("§4§l⚔ ═══════════════════════════ ⚔"));
        player.sendMessage("");

        player.getInventory().addItem(
                new ItemStack(Material.NETHERITE_INGOT, 4),
                new ItemStack(Material.DIAMOND_BLOCK, 16),
                new ItemStack(Material.EMERALD_BLOCK, 32),
                new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 8),
                new ItemStack(Material.EXPERIENCE_BOTTLE, 64),
                new ItemStack(Material.EXPERIENCE_BOTTLE, 64),
                new ItemStack(Material.NETHER_STAR, 1),
                new ItemStack(Material.GOLDEN_APPLE, 32)
        );

        if (plugin.getEssenceManager() != null) {
            plugin.getEssenceManager().onBossKill(player);
        }

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "crates give " + player.getName() + " KraganKey 3");
    }

    private void giveParticipationReward(Player player) {
        player.sendMessage(SmallCaps.convert("§7Has participado en la batalla contra §4§lWarlord Kragan§7."));
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
    }

    // ==================== INTERFACE ====================

    @Override public boolean isActive() { return active; }
    @Override public String getBossName() { return "Warlord Kragan"; }
    @Override public BossType getBossType() { return BossType.ABYSSAL_WARDEN; }
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
}
