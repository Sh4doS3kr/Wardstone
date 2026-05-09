package com.moonlight.coreprotect.bossarena;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Glacius — Emperador del Invierno Eterno
 *
 * Boss épico de hielo con 3 fases, bloques fantasma, y mecánicas de congelación.
 * El ÚLTIMO GOLPE determina quién se lleva el premio.
 *
 * FASE 1 (100%-60%): Escarcha Primordial
 *   - Ice Spike Barrage: Columnas de hielo fantasma que brotan del suelo
 *   - Frost Nova: Onda de hielo AoE que congela y ralentiza
 *   - Summon: Invoca Strays espectrales
 *   - Hail Storm: Lluvia de bloques de hielo fantasma desde el cielo
 *
 * FASE 2 (60%-30%): Ventisca Eterna
 *   - Blizzard: Tormenta de nieve que daña y ciega
 *   - Ice Prison: Encarcela a un jugador en hielo fantasma
 *   - Glacial Lance: Lanza de hielo dirigida al jugador más lejano
 *   - Frost Wall: Muro de hielo fantasma que avanza
 *
 * FASE 3 (30%-0%): Corazón del Glaciar
 *   - Absolute Zero: Todo el suelo se congela + slow extremo
 *   - Crystal Storm: Lluvia masiva de cristales de hielo
 *   - Ice Clone: Crea clones de hielo que explotan
 *   - Permafrost: Aura constante de daño por frío
 */
public class GlaciusBoss implements Boss {

    private final CoreProtectPlugin plugin;
    private final BossArenaManager manager;
    private final ArenaData arena;

    private IronGolem bossEntity;
    private BossBar bossBar;
    private BossBar globalBossBar;
    private BukkitTask mainLoop;
    private boolean active = false;
    private int tickCount = 0;
    private int phase = 1;
    private int highestPhaseReached = 1;

    // Timer de 45 minutos
    private static final long BOSS_TIMEOUT_MS = 45 * 60 * 1000L;
    private long spawnTimestamp;

    // Último jugador que golpeó al boss
    private Player lastHitter;

    // Mobs invocados
    private final List<Entity> summonedMobs = new ArrayList<>();

    // Bloques fantasma activos (para limpiar)
    private final List<PhantomBlock> phantomBlocks = new ArrayList<>();

    // Cooldowns de habilidades (en ticks del loop, cada 5 game ticks)
    private int iceSpikeCD = 0;
    private int frostNovaCD = 0;
    private int summonCD = 0;
    private int hailStormCD = 0;
    private int blizzardCD = 0;
    private int icePrisonCD = 0;
    private int glacialLanceCD = 0;
    private int frostWallCD = 0;
    private int absoluteZeroCD = 0;
    private int crystalStormCD = 0;
    private int iceCloneCD = 0;

    // Cooldown global: solo 1 habilidad por ciclo, mínimo 1s entre habilidades
    private int globalAbilityCD = 0;

    // HP del boss (reducido para evitar bugs)
    private static final double MAX_HEALTH = 2048.0;

    // Tracking de unreachable para salto vertical
    private int unreachableTicks = 0;

    // Bloque fantasma temporal usando ArmorStand (no-solid, traversable)
    private static class PhantomBlock {
        final ArmorStand stand;
        final long expireTime;

        PhantomBlock(ArmorStand stand, long durationTicks) {
            this.stand = stand;
            this.expireTime = System.currentTimeMillis() + durationTicks * 50;
        }

        boolean isExpired() {
            return System.currentTimeMillis() >= expireTime;
        }

        void remove() {
            if (stand != null && !stand.isDead()) {
                stand.getWorld().spawnParticle(Particle.SNOWFLAKE,
                        stand.getLocation().add(0, 0.5, 0), 3, 0.2, 0.2, 0.2, 0.01);
                stand.remove();
            }
        }
    }

    /**
     * Spawns a non-solid phantom ice block using an invisible ArmorStand with a block helmet.
     * Players can walk through these.
     */
    private PhantomBlock spawnPhantomIceBlock(Location loc, Material material, long durationTicks) {
        World world = loc.getWorld();
        // Center the armor stand in the block
        Location standLoc = loc.getBlock().getLocation().add(0.5, -0.5, 0.5);
        ArmorStand stand = world.spawn(standLoc, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setGravity(false);
            as.setSmall(false);
            as.setMarker(true); // No collision box
            as.setInvulnerable(true);
            as.setSilent(true);
            as.setCustomNameVisible(false);
            as.getEquipment().setHelmet(new ItemStack(material));
            as.setRemoveWhenFarAway(false);
            as.addScoreboardTag("glacius_phantom");
        });
        PhantomBlock pb = new PhantomBlock(stand, durationTicks);
        phantomBlocks.add(pb);
        return pb;
    }

    public GlaciusBoss(CoreProtectPlugin plugin, BossArenaManager manager, ArenaData arena) {
        this.plugin = plugin;
        this.manager = manager;
        this.arena = arena;
    }

    // ==================== SPAWN ====================

    @Override
    public void spawn() {
        Location spawnLoc = arena.getBossSpawn();
        World world = spawnLoc.getWorld();

        // Efecto de despertar glacial
        awakening(spawnLoc, world);

        // Spawnear boss directamente en ubicación segura
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active) {
                plugin.getLogger().warning("[Glacius] Spawn cancelado: active=false");
                return;
            }
            
            // PROTECCIÓN ANTI-DUPLICADO: Si ya existe un bossEntity, no spawnear otro
            if (bossEntity != null && !bossEntity.isDead()) {
                plugin.getLogger().warning("[Glacius] Intento de spawn duplicado bloqueado - boss ya existe");
                return;
            }

            try {
                // Encontrar ubicación segura primero
                Location safeLoc = getSafeLocation(spawnLoc);
                plugin.getLogger().info("[Glacius] Spawneando Iron Golem en ubicación segura: " + safeLoc);
                
                bossEntity = world.spawn(safeLoc, IronGolem.class, golem -> {
                    golem.setCustomName(SmallCaps.convert("§b§l❄ Glacius §8- §3Emperador del Invierno Eterno §b§l❄"));
                    golem.setCustomNameVisible(true);
                    golem.setRemoveWhenFarAway(false);
                    golem.setPersistent(true);
                    golem.setSilent(false); // No silencioso para que se escuche

                    // NO usar setPlayerCreated(false) - causa despawn del golem
                    // El golem se mantiene como "player created" para evitar despawn natural

                    // Atributos con manejo de errores mejorado
                    try {
                        org.bukkit.attribute.AttributeInstance maxHealth = golem.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
                        if (maxHealth != null) {
                            maxHealth.setBaseValue(MAX_HEALTH);
                            golem.setHealth(MAX_HEALTH);
                            plugin.getLogger().info("[Glacius] HP establecido: " + MAX_HEALTH);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("[Glacius] MAX_HEALTH falló: " + e.getMessage());
                        try {
                            golem.setHealth(2048.0); // Fallback
                        } catch (Exception e2) {
                            plugin.getLogger().severe("[Glacius] No se pudo establecer HP: " + e2.getMessage());
                        }
                    }
                    
                    try { 
                        org.bukkit.attribute.AttributeInstance attackDamage = golem.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE);
                        if (attackDamage != null) attackDamage.setBaseValue(22);
                    } catch (Exception e) {
                        plugin.getLogger().warning("[Glacius] ATTACK_DAMAGE falló: " + e.getMessage());
                    }
                    
                    try { 
                        org.bukkit.attribute.AttributeInstance movementSpeed = golem.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED);
                        if (movementSpeed != null) movementSpeed.setBaseValue(0.30);
                    } catch (Exception e) {
                        plugin.getLogger().warning("[Glacius] MOVEMENT_SPEED falló: " + e.getMessage());
                    }
                    
                    try { 
                        org.bukkit.attribute.AttributeInstance armor = golem.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ARMOR);
                        if (armor != null) armor.setBaseValue(10);
                    } catch (Exception e) {
                        plugin.getLogger().warning("[Glacius] ARMOR falló: " + e.getMessage());
                    }
                    
                    try { 
                        org.bukkit.attribute.AttributeInstance armorToughness = golem.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ARMOR_TOUGHNESS);
                        if (armorToughness != null) armorToughness.setBaseValue(2.0);
                    } catch (Exception e) {
                        plugin.getLogger().warning("[Glacius] ARMOR_TOUGHNESS falló: " + e.getMessage());
                    }
                    
                    try { 
                        org.bukkit.attribute.AttributeInstance knockbackResistance = golem.getAttribute(org.bukkit.attribute.Attribute.GENERIC_KNOCKBACK_RESISTANCE);
                        if (knockbackResistance != null) knockbackResistance.setBaseValue(0.0);
                    } catch (Exception e) {
                        plugin.getLogger().warning("[Glacius] KNOCKBACK_RESISTANCE falló: " + e.getMessage());
                    }
                    
                    try {
                        org.bukkit.attribute.AttributeInstance scale = golem.getAttribute(org.bukkit.attribute.Attribute.GENERIC_SCALE);
                        if (scale != null) scale.setBaseValue(1.5); // Tamaño 1.5x desde el inicio
                    } catch (Exception e) {
                        plugin.getLogger().warning("[Glacius] SCALE falló: " + e.getMessage());
                    }

                    // Equipo
                    golem.getEquipment().setHelmet(new ItemStack(Material.BLUE_ICE));
                    golem.getEquipment().setHelmetDropChance(0);
                    golem.getEquipment().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
                    golem.getEquipment().setItemInMainHandDropChance(0);

                    golem.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 999999, 0, false, false));
                    golem.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 999999, 0, false, false));
                    golem.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 999999, 0, false, false));
                    golem.setAI(true); // Activar IA inmediatamente
                });

                // Efectos de spawn
                world.spawnParticle(Particle.SNOWFLAKE, safeLoc.clone().add(0, 1, 0), 30, 1.5, 1.5, 1.5, 0.1);
                world.spawnParticle(Particle.EXPLOSION, safeLoc.clone().add(0, 1, 0), 10, 1, 1, 1, 0.5);
                world.playSound(safeLoc, Sound.ENTITY_IRON_GOLEM_HURT, 1.5f, 0.5f);
                world.playSound(safeLoc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.3f);
                
                plugin.getLogger().info("[Glacius] Spawn completado exitosamente.");

            } catch (Exception e) {
                plugin.getLogger().severe("[Glacius] ERROR al spawnear: " + e.getMessage());
                e.printStackTrace();
                return;
            }

            // Timer empieza al spawnear
            spawnTimestamp = System.currentTimeMillis();

            org.bukkit.NamespacedKey glaciusKey = new org.bukkit.NamespacedKey(plugin, "boss_glacius");
            Bukkit.removeBossBar(glaciusKey);
            bossBar = Bukkit.createBossBar(glaciusKey, "§b§l❄ Glacius §8- §3Fase 1: Escarcha Primordial",
                    BarColor.BLUE, BarStyle.SEGMENTED_20);
            bossBar.setVisible(true);
            for (Player p : world.getPlayers()) bossBar.addPlayer(p);

            plugin.getLogger().info("[Glacius] BossBar creada. Iniciando main loop...");
            startMainLoop();

        }, 100L);

        active = true;
    }

    private void awakening(Location loc, World world) {
        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (tick >= 100) { cancel(); return; }

                // Espiral de partículas de hielo
                double angle = tick * 0.3;
                double radius = 1.0 + tick * 0.05;
                double x = Math.cos(angle) * radius;
                double z = Math.sin(angle) * radius;
                Location particleLoc = loc.clone().add(x, tick * 0.05, z);

                world.spawnParticle(Particle.DUST, particleLoc, 5, 0.1, 0.1, 0.1, 0,
                        new Particle.DustOptions(Color.fromRGB(150, 220, 255), 2.0f));
                world.spawnParticle(Particle.SNOWFLAKE, particleLoc, 3, 0.1, 0.1, 0.1, 0.01);

                // Columna de cristal
                if (tick % 5 == 0) {
                    world.spawnParticle(Particle.END_ROD, loc.clone().add(0, tick * 0.05, 0), 5, 0.3, 0.3, 0.3, 0.01);
                    world.spawnParticle(Particle.SNOWFLAKE, loc.clone().add(0, 0.5, 0), 10, 0.5, 1, 0.5, 0.02);
                }

                // Ondas de escarcha en el suelo
                if (tick % 10 == 0) {
                    double r = tick * 0.1;
                    for (int i = 0; i < 20; i++) {
                        double a = (Math.PI * 2.0 / 20) * i;
                        Location ring = loc.clone().add(Math.cos(a) * r, 0.1, Math.sin(a) * r);
                        world.spawnParticle(Particle.DUST, ring, 1, 0, 0, 0, 0,
                                new Particle.DustOptions(Color.fromRGB(200, 240, 255), 1.5f));
                    }
                }

                // Sonidos progresivos
                if (tick == 0) world.playSound(loc, Sound.BLOCK_GLASS_BREAK, 2.0f, 0.3f);
                if (tick == 30) world.playSound(loc, Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 2.0f, 1.5f);
                if (tick == 60) world.playSound(loc, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 2.0f, 1.8f);
                if (tick == 80) world.playSound(loc, Sound.ENTITY_WITHER_SPAWN, 2.0f, 1.5f);

                // Explosión de cristal final
                if (tick == 95) {
                    world.spawnParticle(Particle.EXPLOSION, loc.clone().add(0, 2, 0), 3, 1, 1, 1, 0);
                    world.spawnParticle(Particle.DUST, loc.clone().add(0, 2, 0), 80, 3, 3, 3, 0,
                            new Particle.DustOptions(Color.fromRGB(180, 230, 255), 3.0f));
                    world.spawnParticle(Particle.SNOWFLAKE, loc.clone().add(0, 2, 0), 60, 3, 3, 3, 0.15);
                    world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 2, 0), 40, 2, 2, 2, 0.1);
                    world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 1.5f);

                    for (Player p : world.getPlayers()) {
                        p.sendTitle( SmallCaps.convert("§b§l❄ GLACIUS ❄"), SmallCaps.convert("§3\"¡El invierno eterno ha llegado!\""), 5, 40, 15);
                    }
                }

                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ==================== MAIN LOOP ====================

    private void startMainLoop() {
        mainLoop = new BukkitRunnable() {
            @Override
            public void run() {
                if (!active || bossEntity == null || bossEntity.isDead()) {
                    if (active) onDeath();
                    cancel();
                    return;
                }

                tickCount++;

                // ANTI-ASFIXIA: Verificar cada segundo si el boss está bajo tierra
                if (tickCount % 20 == 0) {
                    checkAndFixBossLocation();
                }

                // Verificar timeout
                long elapsed = System.currentTimeMillis() - spawnTimestamp;
                if (elapsed >= BOSS_TIMEOUT_MS) {
                    onTimeout();
                    cancel();
                    return;
                }

                double hpPercent = bossEntity.getHealth() / MAX_HEALTH;
                int newPhase = hpPercent > 0.6 ? 1 : hpPercent > 0.3 ? 2 : 3;
                phase = Math.max(phase, newPhase);
                if (phase > highestPhaseReached) {
                    highestPhaseReached = phase;
                    onPhaseChange();
                }

                // Decrementar cooldowns
                iceSpikeCD = Math.max(0, iceSpikeCD - 1);
                frostNovaCD = Math.max(0, frostNovaCD - 1);
                summonCD = Math.max(0, summonCD - 1);
                hailStormCD = Math.max(0, hailStormCD - 1);
                blizzardCD = Math.max(0, blizzardCD - 1);
                icePrisonCD = Math.max(0, icePrisonCD - 1);
                glacialLanceCD = Math.max(0, glacialLanceCD - 1);
                frostWallCD = Math.max(0, frostWallCD - 1);
                absoluteZeroCD = Math.max(0, absoluteZeroCD - 1);
                crystalStormCD = Math.max(0, crystalStormCD - 1);
                iceCloneCD = Math.max(0, iceCloneCD - 1);
                globalAbilityCD = Math.max(0, globalAbilityCD - 1);

                updateBossBar(hpPercent);
                ambientParticles();
                retargetNearest();
                cleanExpiredPhantomBlocks();

                switch (phase) {
                    case 1: tickPhase1(); break;
                    case 2: tickPhase2(); break;
                    case 3: tickPhase3(); break;
                }

                summonedMobs.removeIf(e -> e.isDead() || !e.isValid());
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    // ==================== FASES ====================

    private void onPhaseChange() {
        World world = bossEntity.getWorld();
        Location loc = bossEntity.getLocation();

        switch (phase) {
            case 2:
                bossBar.setTitle(SmallCaps.convert("§b§l❄ Glacius §8- §9Fase 2: Ventisca Eterna"));
                bossBar.setColor(BarColor.WHITE);
                bossEntity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(26);
                bossEntity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.30);

                world.spawnParticle(Particle.DUST, loc.clone().add(0, 2, 0), 60, 3, 2, 3, 0,
                        new Particle.DustOptions(Color.fromRGB(200, 230, 255), 2.5f));
                world.spawnParticle(Particle.SNOWFLAKE, loc.clone().add(0, 1, 0), 50, 3, 2, 3, 0.1);
                world.playSound(loc, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 2.0f, 1.5f);

                for (Player p : world.getPlayers()) {
                    p.sendTitle( SmallCaps.convert("§9§lFASE 2"), SmallCaps.convert("§b\"¡La ventisca os sepultará!\""), 5, 40, 15);
                    p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.5f);
                }
                break;

            case 3:
                bossBar.setTitle(SmallCaps.convert("§b§l❄ Glacius §8- §f§lFase 3: Corazón del Glaciar"));
                bossBar.setColor(BarColor.WHITE);
                bossEntity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(32);
                bossEntity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.34);
                bossEntity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(1.0);
                bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999, 1, false, false));

                world.spawnParticle(Particle.DUST, loc.clone().add(0, 2, 0), 100, 4, 3, 4, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 255, 255), 3.0f));
                world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 1, 0), 60, 3, 2, 3, 0.08);
                world.spawnParticle(Particle.EXPLOSION, loc.clone().add(0, 2, 0), 3, 1, 1, 1, 0);
                world.playSound(loc, Sound.ENTITY_WITHER_SPAWN, 2.0f, 1.8f);

                for (Player p : world.getPlayers()) {
                    p.sendTitle( SmallCaps.convert("§f§l❄ FASE FINAL ❄"), SmallCaps.convert("§b\"¡CERO ABSOLUTO!\""), 5, 50, 20);
                    p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.8f);
                }
                break;
        }
    }

    /**
     * Intenta usar una habilidad. Si el globalAbilityCD está activo, no permite.
     * Al usar una habilidad, establece un gap mínimo de 4 ticks (~1s).
     */
    private boolean tryAbility(Runnable ability, int[] cdRef, int cdValue) {
        if (globalAbilityCD > 0) return false;
        ability.run();
        cdRef[0] = cdValue;
        globalAbilityCD = 4; // 4 loop ticks × 5 game ticks = 20 game ticks = 1s entre habilidades
        return true;
    }

    private void tickPhase1() {
        if (globalAbilityCD > 0) return;
        // Ice Spike Barrage cada 6s
        if (iceSpikeCD <= 0 && Math.random() < 0.4) {
            iceSpikeBarrage(); iceSpikeCD = 24; globalAbilityCD = 4; return;
        }
        // Frost Nova cada 10s
        if (frostNovaCD <= 0 && Math.random() < 0.3) {
            frostNova(); frostNovaCD = 40; globalAbilityCD = 4; return;
        }
        // Hail Storm cada 12s
        if (hailStormCD <= 0 && Math.random() < 0.3) {
            hailStorm(); hailStormCD = 48; globalAbilityCD = 4; return;
        }
        // Summon cada 20s
        if (summonCD <= 0 && Math.random() < 0.25) {
            summonFrostMinions(); summonCD = 80; globalAbilityCD = 4; return;
        }
    }

    private void tickPhase2() {
        if (globalAbilityCD > 0) return;
        // Fase 2 habilidades propias primero (rotación aleatoria con fase 1)
        if (Math.random() < 0.5) {
            // Intentar habilidades de fase 2 primero
            if (blizzardCD <= 0 && Math.random() < 0.3) {
                blizzard(); blizzardCD = 60; globalAbilityCD = 4; return;
            }
            if (icePrisonCD <= 0 && Math.random() < 0.25) {
                icePrison(); icePrisonCD = 48; globalAbilityCD = 4; return;
            }
            if (glacialLanceCD <= 0 && Math.random() < 0.35) {
                glacialLance(); glacialLanceCD = 32; globalAbilityCD = 4; return;
            }
            if (frostWallCD <= 0 && Math.random() < 0.25) {
                frostWall(); frostWallCD = 60; globalAbilityCD = 4; return;
            }
            // Fallback a fase 1
            tickPhase1();
        } else {
            // Intentar fase 1 primero, luego fase 2
            tickPhase1();
            if (globalAbilityCD > 0) return;
            if (blizzardCD <= 0 && Math.random() < 0.3) {
                blizzard(); blizzardCD = 60; globalAbilityCD = 4; return;
            }
            if (glacialLanceCD <= 0 && Math.random() < 0.35) {
                glacialLance(); glacialLanceCD = 32; globalAbilityCD = 4; return;
            }
            if (icePrisonCD <= 0 && Math.random() < 0.25) {
                icePrison(); icePrisonCD = 48; globalAbilityCD = 4; return;
            }
            if (frostWallCD <= 0 && Math.random() < 0.25) {
                frostWall(); frostWallCD = 60; globalAbilityCD = 4; return;
            }
        }
    }

    private void tickPhase3() {
        if (globalAbilityCD > 0) return;
        // Aura de Permafrost (daño constante, no consume global CD)
        if (tickCount % 4 == 0) {
            permafrostAura();
        }
        // Fase 3 habilidades propias con rotación
        if (Math.random() < 0.5) {
            if (absoluteZeroCD <= 0 && Math.random() < 0.3) {
                absoluteZero(); absoluteZeroCD = 80; globalAbilityCD = 5; return;
            }
            if (crystalStormCD <= 0 && Math.random() < 0.35) {
                crystalStorm(); crystalStormCD = 40; globalAbilityCD = 4; return;
            }
            if (iceCloneCD <= 0 && Math.random() < 0.2) {
                iceClone(); iceCloneCD = 72; globalAbilityCD = 4; return;
            }
            // Fallback a fase 2
            tickPhase2();
        } else {
            tickPhase2();
            if (globalAbilityCD > 0) return;
            if (crystalStormCD <= 0 && Math.random() < 0.35) {
                crystalStorm(); crystalStormCD = 40; globalAbilityCD = 4; return;
            }
            if (absoluteZeroCD <= 0 && Math.random() < 0.3) {
                absoluteZero(); absoluteZeroCD = 80; globalAbilityCD = 5; return;
            }
            if (iceCloneCD <= 0 && Math.random() < 0.2) {
                iceClone(); iceCloneCD = 72; globalAbilityCD = 4; return;
            }
        }
    }

    // ==================== HABILIDADES FASE 1 ====================

    /**
     * Ice Spike Barrage: Columnas de hielo fantasma brotan del suelo bajo los jugadores.
     */
    private void iceSpikeBarrage() {
        World world = bossEntity.getWorld();
        Location bossLoc = bossEntity.getLocation();
        world.playSound(bossLoc, Sound.BLOCK_GLASS_BREAK, 2.0f, 0.5f);

        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(bossLoc) > 900) continue; // 30 bloques

            Location target = p.getLocation().clone();

            // Aviso: partículas en el suelo 0.5s antes
            new BukkitRunnable() {
                int t = 0;
                @Override
                public void run() {
                    if (t < 10) {
                        // Círculo de aviso en el suelo
                        for (int i = 0; i < 12; i++) {
                            double a = (Math.PI * 2.0 / 12) * i;
                            Location warn = target.clone().add(Math.cos(a) * 1.0, 0.1, Math.sin(a) * 1.0);
                            world.spawnParticle(Particle.DUST, warn, 1, 0, 0, 0, 0,
                                    new Particle.DustOptions(Color.fromRGB(150, 220, 255), 1.0f));
                        }
                    } else if (t == 10) {
                        // ¡SPIKE! Columna de bloques fantasma
                        spawnIceSpike(target, world, 5);
                        world.playSound(target, Sound.BLOCK_GLASS_BREAK, 1.5f, 1.2f);

                        // Daño a jugadores en el área
                        for (Player victim : world.getPlayers()) {
                            if (victim.getLocation().distanceSquared(target) < 4) { // 2 bloques
                                victim.damage(8, bossEntity);
                                victim.setVelocity(new Vector(0, 0.6, 0)); // Lanzar al aire
                                victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, true));
                            }
                        }
                    } else {
                        cancel();
                    }
                    t++;
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }
    }

    /**
     * Genera una columna de ice spike usando bloques fantasma.
     */
    private void spawnIceSpike(Location base, World world, int height) {
        Material[] iceMaterials = {Material.BLUE_ICE, Material.PACKED_ICE, Material.ICE, Material.SNOW_BLOCK};

        for (int y = 0; y < height; y++) {
            Location blockLoc = base.clone().add(0, y, 0);
            Material iceMat = iceMaterials[Math.min(y, iceMaterials.length - 1)];

            int finalY = y;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!active) return;
                spawnPhantomIceBlock(blockLoc, iceMat, 60 + finalY * 5);
                world.spawnParticle(Particle.SNOWFLAKE, blockLoc.clone().add(0.5, 0.5, 0.5), 5, 0.2, 0.2, 0.2, 0.01);
            }, y * 2L);
        }
    }

    /**
     * Frost Nova: Onda expansiva de hielo que congela y ralentiza.
     */
    private void frostNova() {
        World world = bossEntity.getWorld();
        Location center = bossEntity.getLocation();
        world.playSound(center, Sound.ENTITY_PLAYER_HURT_FREEZE, 2.0f, 0.5f);

        new BukkitRunnable() {
            int radius = 0;
            @Override
            public void run() {
                if (radius > 10 || !active) { cancel(); return; }

                // Anillo expandiéndose
                for (int i = 0; i < 24; i++) {
                    double angle = (Math.PI * 2.0 / 24) * i;
                    Location ring = center.clone().add(Math.cos(angle) * radius, 0.2, Math.sin(angle) * radius);
                    world.spawnParticle(Particle.DUST, ring, 2, 0.1, 0.1, 0.1, 0,
                            new Particle.DustOptions(Color.fromRGB(180, 230, 255), 2.0f));
                    world.spawnParticle(Particle.SNOWFLAKE, ring, 1, 0.1, 0.3, 0.1, 0.01);

                    // Bloques fantasma de hielo en el suelo (ArmorStand, traversable)
                    if (i % 3 == 0) {
                        Block ground = ring.getBlock();
                        if (ground.getType() != Material.AIR) {
                            spawnPhantomIceBlock(ground.getRelative(0, 1, 0).getLocation(), Material.SNOW_BLOCK, 80);
                        }
                    }
                }

                // Daño y slow a jugadores en el radio
                for (Player p : world.getPlayers()) {
                    double dist = p.getLocation().distance(center);
                    if (dist >= radius - 1 && dist <= radius + 1) {
                        p.damage(6, bossEntity);
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2, false, true));
                        p.setFreezeTicks(100);
                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0f, 1.0f);
                    }
                }

                world.playSound(center, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.5f + radius * 0.05f);
                radius += 2;
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    /**
     * Hail Storm: Lluvia de bloques de hielo fantasma desde el cielo.
     */
    private void hailStorm() {
        World world = bossEntity.getWorld();
        Location center = bossEntity.getLocation();
        world.playSound(center, Sound.ENTITY_ENDER_DRAGON_FLAP, 2.0f, 1.5f);

        for (Player p : world.getPlayers()) {
            p.sendMessage(SmallCaps.convert("§b§l❄ §3¡Tormenta de granizo!"));
        }

        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t >= 40 || !active) { cancel(); return; }

                // 2-3 bloques de hielo cayendo cada tick
                for (int i = 0; i < 2 + (int)(Math.random() * 2); i++) {
                    double rx = center.getX() + (Math.random() - 0.5) * 16;
                    double rz = center.getZ() + (Math.random() - 0.5) * 16;
                    Location dropLoc = new Location(world, rx, center.getY() + 12, rz);

                    // FallingBlock de hielo
                    FallingBlock ice = world.spawnFallingBlock(dropLoc,
                            Material.PACKED_ICE.createBlockData());
                    ice.setDropItem(false);
                    ice.setHurtEntities(true);
                    ice.setVelocity(new Vector(0, -0.5, 0));

                    // Partículas de estela
                    world.spawnParticle(Particle.SNOWFLAKE, dropLoc, 5, 0.2, 0.2, 0.2, 0.05);
                }

                if (t % 5 == 0) {
                    world.playSound(center, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.8f);
                }

                t++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /**
     * Summon Frost Minions: Invoca Strays espectrales.
     */
    private void summonFrostMinions() {
        World world = bossEntity.getWorld();
        Location bossLoc = bossEntity.getLocation();

        int count = phase == 1 ? 2 : phase == 2 ? 3 : 4;

        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2.0 / count) * i;
            Location spawnLoc = bossLoc.clone().add(Math.cos(angle) * 3, 0, Math.sin(angle) * 3);

            // Encontrar suelo seguro
            Block ground = spawnLoc.getBlock();
            for (int tries = 0; tries < 5; tries++) {
                if (ground.getType() == Material.AIR && ground.getRelative(0, -1, 0).getType().isSolid()) break;
                ground = ground.getRelative(0, ground.getType() == Material.AIR ? -1 : 1, 0);
            }
            Location safeLoc = ground.getLocation().add(0.5, 0, 0.5);

            // Efecto de spawn
            world.spawnParticle(Particle.SNOWFLAKE, safeLoc.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.05);
            world.spawnParticle(Particle.END_ROD, safeLoc.clone().add(0, 1, 0), 10, 0.2, 0.5, 0.2, 0.02);

            Stray minion = world.spawn(safeLoc, Stray.class, s -> {
                s.setCustomName(SmallCaps.convert("§b❄ §3Espectro de Hielo §b❄"));
                s.setCustomNameVisible(true);
                s.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).setBaseValue(40 + phase * 10);
                s.setHealth(40 + phase * 10);
                s.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(5 + phase * 2);
                s.setRemoveWhenFarAway(false);
                s.setPersistent(true);
                s.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
                s.getEquipment().setItemInMainHandDropChance(0);
            });
            summonedMobs.add(minion);
        }

        world.playSound(bossLoc, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 2.0f, 1.5f);
        for (Player p : world.getPlayers()) {
            p.sendMessage(SmallCaps.convert("§b§l❄ §3Glacius invoca espectros de hielo!"));
        }
    }

    // ==================== HABILIDADES FASE 2 ====================

    /**
     * Blizzard: Tormenta de nieve que daña y ciega.
     */
    private void blizzard() {
        World world = bossEntity.getWorld();
        Location center = bossEntity.getLocation();

        for (Player p : world.getPlayers()) {
            p.sendTitle("", SmallCaps.convert("§b§l¡VENTISCA!"), 0, 30, 10);
        }

        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t >= 60 || !active) { cancel(); return; }

                // Partículas de nieve masivas
                world.spawnParticle(Particle.SNOWFLAKE, center.clone().add(0, 5, 0), 40, 10, 3, 10, 0.1);
                world.spawnParticle(Particle.DUST, center.clone().add(0, 3, 0), 20, 8, 2, 8, 0,
                        new Particle.DustOptions(Color.fromRGB(220, 240, 255), 1.5f));

                // Daño y efectos cada segundo
                if (t % 20 == 0) {
                    for (Player p : world.getPlayers()) {
                        if (p.getLocation().distanceSquared(center) < 400) { // 20 bloques
                            p.damage(4, bossEntity);
                            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, true));
                            p.setFreezeTicks(Math.min(p.getFreezeTicks() + 60, 200));
                        }
                    }
                    world.playSound(center, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.5f, 0.5f);
                }

                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Ice Prison: Encarcela a un jugador en una jaula de hielo fantasma.
     */
    private void icePrison() {
        World world = bossEntity.getWorld();
        Player target = getRandomNearbyPlayer(15);
        if (target == null) return;

        Location loc = target.getLocation().clone();
        world.playSound(loc, Sound.BLOCK_GLASS_PLACE, 2.0f, 0.3f);
        target.sendMessage(SmallCaps.convert("§b§l❄ §c¡Glacius te ha atrapado en hielo!"));

        // Crear prisión de hielo fantasma alrededor del jugador (ArmorStand, traversable)
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (Math.abs(x) == 1 || Math.abs(z) == 1 || y == 2) {
                        if (x == 0 && z == 0 && y < 2) continue;
                        Location blockLoc = loc.clone().add(x, y, z);
                        spawnPhantomIceBlock(blockLoc, Material.BLUE_ICE, 80);
                    }
                }
            }
        }

        // Slowness extremo al prisionero
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 5, false, true));
        target.setFreezeTicks(200);

        // Partículas en la prisión
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t >= 80 || !active) { cancel(); return; }
                world.spawnParticle(Particle.SNOWFLAKE, loc.clone().add(0, 1, 0), 5, 1, 1, 1, 0.01);
                world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 1, 0), 2, 0.5, 0.5, 0.5, 0.01);
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Glacial Lance: Una línea de ice spikes dirigida a un jugador.
     */
    private void glacialLance() {
        World world = bossEntity.getWorld();
        Player target = getRandomNearbyPlayer(25);
        if (target == null) return;

        Location start = bossEntity.getLocation().clone();
        Vector direction = target.getLocation().toVector().subtract(start.toVector()).normalize();

        world.playSound(start, Sound.ENTITY_EVOKER_CAST_SPELL, 2.0f, 1.5f);

        for (Player p : world.getPlayers()) {
            p.sendMessage(SmallCaps.convert("§b§l❄ §3¡Lanza glacial!"));
        }

        new BukkitRunnable() {
            int dist = 0;
            @Override
            public void run() {
                if (dist >= 20 || !active) { cancel(); return; }

                Location spikeLoc = start.clone().add(direction.clone().multiply(dist));
                spawnIceSpike(spikeLoc, world, 3 + (int)(Math.random() * 3));

                // Daño en el camino
                for (Player p : world.getPlayers()) {
                    if (p.getLocation().distanceSquared(spikeLoc) < 4) {
                        p.damage(10, bossEntity);
                        p.setVelocity(direction.clone().multiply(0.8).setY(0.4));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 2, false, true));
                    }
                }

                world.playSound(spikeLoc, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.0f + dist * 0.03f);
                dist += 2;
            }
        }.runTaskTimer(plugin, 5L, 3L); // Pequeño delay + avance rápido
    }

    /**
     * Frost Wall: Muro de hielo fantasma que avanza hacia los jugadores.
     */
    private void frostWall() {
        World world = bossEntity.getWorld();
        Player target = getRandomNearbyPlayer(20);
        if (target == null) return;

        Location start = bossEntity.getLocation().clone();
        Vector dir = target.getLocation().toVector().subtract(start.toVector()).normalize();
        // Perpendicular para el ancho del muro
        Vector perp = new Vector(-dir.getZ(), 0, dir.getX()).normalize();

        world.playSound(start, Sound.BLOCK_GLASS_BREAK, 2.0f, 0.3f);

        new BukkitRunnable() {
            int step = 0;
            @Override
            public void run() {
                if (step >= 15 || !active) { cancel(); return; }

                // Muro de 7 bloques de ancho x 3 de alto
                for (int w = -3; w <= 3; w++) {
                    Location wallBase = start.clone()
                            .add(dir.clone().multiply(step * 2))
                            .add(perp.clone().multiply(w));

                    for (int y = 0; y < 3; y++) {
                        Location blockLoc = wallBase.clone().add(0, y, 0);
                        spawnPhantomIceBlock(blockLoc, y == 2 ? Material.ICE : Material.PACKED_ICE, 40 + step * 3);
                        world.spawnParticle(Particle.SNOWFLAKE, blockLoc.clone().add(0.5, 0.5, 0.5),
                                3, 0.2, 0.2, 0.2, 0.01);
                    }

                    // Daño por aplastamiento
                    for (Player p : world.getPlayers()) {
                        if (p.getLocation().distanceSquared(wallBase) < 4) {
                            p.damage(8, bossEntity);
                            p.setVelocity(dir.clone().multiply(1.2).setY(0.3));
                        }
                    }
                }

                world.playSound(start.clone().add(dir.clone().multiply(step * 2)),
                        Sound.BLOCK_GLASS_BREAK, 1.0f, 0.8f);
                step++;
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    // ==================== HABILIDADES FASE 3 ====================

    /**
     * Absolute Zero: Congela todo el suelo + slow extremo.
     */
    private void absoluteZero() {
        World world = bossEntity.getWorld();
        Location center = bossEntity.getLocation();

        for (Player p : world.getPlayers()) {
            p.sendTitle( SmallCaps.convert("§f§l❄ CERO ABSOLUTO ❄"), SmallCaps.convert("§b¡Todo se congela!"), 5, 40, 15);
            p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 1.5f);
        }

        // Onda de congelación desde el centro
        new BukkitRunnable() {
            int radius = 0;
            @Override
            public void run() {
                if (radius > 15 || !active) { cancel(); return; }

                for (int i = 0; i < 36; i++) {
                    double angle = (Math.PI * 2.0 / 36) * i;
                    int bx = (int)(center.getX() + Math.cos(angle) * radius);
                    int bz = (int)(center.getZ() + Math.sin(angle) * radius);

                    // Encontrar el bloque superior
                    Block surface = world.getHighestBlockAt(bx, bz);
                    Block above = surface.getRelative(0, 1, 0);

                    spawnPhantomIceBlock(above.getLocation(), radius % 3 == 0 ? Material.BLUE_ICE : Material.PACKED_ICE, 120);
                }

                // Efectos a jugadores alcanzados
                for (Player p : world.getPlayers()) {
                    double dist = p.getLocation().distance(center);
                    if (dist >= radius - 2 && dist <= radius + 2) {
                        p.damage(6, bossEntity);
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 3, false, true));
                        p.setFreezeTicks(200);
                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0f, 0.5f);
                    }
                }

                world.playSound(center, Sound.BLOCK_GLASS_BREAK, 1.5f, 0.3f + radius * 0.05f);
                radius += 2;
            }
        }.runTaskTimer(plugin, 10L, 2L);
    }

    /**
     * Crystal Storm: Lluvia masiva de cristales de hielo (ice spikes por todas partes).
     */
    private void crystalStorm() {
        World world = bossEntity.getWorld();
        Location center = bossEntity.getLocation();

        for (Player p : world.getPlayers()) {
            p.sendMessage(SmallCaps.convert("§f§l❄ §b¡Tormenta de cristales!"));
        }

        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t >= 30 || !active) { cancel(); return; }

                // 3-4 ice spikes aleatorios cada tick
                for (int i = 0; i < 3 + (int)(Math.random() * 2); i++) {
                    double rx = center.getX() + (Math.random() - 0.5) * 20;
                    double rz = center.getZ() + (Math.random() - 0.5) * 20;
                    Location spikeLoc = new Location(world, rx, center.getY(), rz);
                    spawnIceSpike(spikeLoc, world, 2 + (int)(Math.random() * 4));

                    // Daño
                    for (Player p : world.getPlayers()) {
                        if (p.getLocation().distanceSquared(spikeLoc) < 4) {
                            p.damage(7, bossEntity);
                            p.setVelocity(new Vector(0, 0.5, 0));
                        }
                    }
                }

                world.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.5f, 0.5f + t * 0.02f);
                t++;
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    /**
     * Ice Clone: Crea un clon de hielo del boss que explota al acercarse.
     */
    private void iceClone() {
        World world = bossEntity.getWorld();
        Location bossLoc = bossEntity.getLocation();
        Player target = getRandomNearbyPlayer(20);
        if (target == null) return;

        // Spawn del clon
        Stray clone = world.spawn(bossLoc.clone().add(3, 0, 3), Stray.class, s -> {
            s.setCustomName(SmallCaps.convert("§b❄ §f§lClon de Hielo §b❄"));
            s.setCustomNameVisible(true);
            s.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).setBaseValue(1);
            s.setHealth(1);
            s.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.35);
            s.setRemoveWhenFarAway(false);
            s.setPersistent(true);
            s.setTarget(target);

            // Armadura de hielo brillante
            ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
            LeatherArmorMeta hm = (LeatherArmorMeta) helmet.getItemMeta();
            hm.setColor(Color.fromRGB(200, 240, 255));
            helmet.setItemMeta(hm);
            s.getEquipment().setHelmet(helmet);
            s.getEquipment().setHelmetDropChance(0);
            s.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 999999, 0, false, false));
            s.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999, 2, false, false));
        });
        summonedMobs.add(clone);

        world.spawnParticle(Particle.END_ROD, bossLoc.clone().add(3, 1, 3), 20, 0.3, 0.5, 0.3, 0.05);
        world.playSound(bossLoc, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 2.0f, 1.5f);

        // Detector de proximidad: si se acerca a un jugador, explota
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (clone.isDead() || !clone.isValid() || t >= 200 || !active) {
                    if (!clone.isDead() && clone.isValid()) {
                        iceExplosion(clone.getLocation(), world);
                        clone.remove();
                    }
                    cancel();
                    return;
                }

                // Partículas de aura
                world.spawnParticle(Particle.SNOWFLAKE, clone.getLocation().add(0, 1, 0), 3, 0.3, 0.5, 0.3, 0.02);

                // Verificar proximidad
                for (Player p : world.getPlayers()) {
                    if (p.getLocation().distanceSquared(clone.getLocation()) < 9) { // 3 bloques
                        iceExplosion(clone.getLocation(), world);
                        clone.remove();
                        cancel();
                        return;
                    }
                }

                t++;
            }
        }.runTaskTimer(plugin, 10L, 2L);
    }

    /**
     * Explosión de hielo (usada por clones y muerte).
     */
    private void iceExplosion(Location loc, World world) {
        world.spawnParticle(Particle.EXPLOSION, loc.clone().add(0, 1, 0), 3, 0.5, 0.5, 0.5, 0);
        world.spawnParticle(Particle.SNOWFLAKE, loc.clone().add(0, 1, 0), 50, 2, 2, 2, 0.15);
        world.spawnParticle(Particle.DUST, loc.clone().add(0, 1, 0), 30, 2, 2, 2, 0,
                new Particle.DustOptions(Color.fromRGB(180, 230, 255), 2.5f));
        world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 1, 0), 20, 2, 2, 2, 0.1);
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.5f);
        world.playSound(loc, Sound.BLOCK_GLASS_BREAK, 2.0f, 0.5f);

        // Spikes alrededor
        for (int i = 0; i < 6; i++) {
            double angle = (Math.PI * 2.0 / 6) * i;
            Location spikeLoc = loc.clone().add(Math.cos(angle) * 2, 0, Math.sin(angle) * 2);
            spawnIceSpike(spikeLoc, world, 2 + (int)(Math.random() * 2));
        }

        // Daño
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(loc) < 25) { // 5 bloques
                p.damage(12, bossEntity);
                Vector kb = p.getLocation().toVector().subtract(loc.toVector()).normalize().multiply(1.2);
                kb.setY(0.5);
                p.setVelocity(kb);
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 3, false, true));
                p.setFreezeTicks(160);
            }
        }
    }

    /**
     * Permafrost Aura: Daño constante por frío a jugadores cercanos.
     */
    private void permafrostAura() {
        if (bossEntity == null || bossEntity.isDead()) return;
        World world = bossEntity.getWorld();
        Location loc = bossEntity.getLocation();

        // Aura visual
        for (int i = 0; i < 16; i++) {
            double angle = (Math.PI * 2.0 / 16) * i + tickCount * 0.1;
            Location aura = loc.clone().add(Math.cos(angle) * 4, 0.5 + Math.sin(tickCount * 0.2) * 0.5, Math.sin(angle) * 4);
            world.spawnParticle(Particle.DUST, aura, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(100, 180, 255), 1.5f));
        }

        // Daño y slow
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(loc) < 36) { // 6 bloques
                p.damage(3, bossEntity);
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 1, false, false));
                p.setFreezeTicks(Math.min(p.getFreezeTicks() + 20, 140));
            }
        }
    }

    // ==================== PARTÍCULAS AMBIENTALES ====================

    private void ambientParticles() {
        if (bossEntity == null || bossEntity.isDead()) return;
        World world = bossEntity.getWorld();
        Location loc = bossEntity.getLocation().add(0, 1.5, 0);

        switch (phase) {
            case 1:
                world.spawnParticle(Particle.SNOWFLAKE, loc, 3, 0.5, 0.8, 0.5, 0.02);
                world.spawnParticle(Particle.DUST, loc, 2, 0.4, 0.6, 0.4, 0,
                        new Particle.DustOptions(Color.fromRGB(150, 220, 255), 1.2f));
                break;
            case 2:
                world.spawnParticle(Particle.SNOWFLAKE, loc, 5, 0.8, 1, 0.8, 0.03);
                world.spawnParticle(Particle.END_ROD, loc, 2, 0.5, 0.8, 0.5, 0.01);
                world.spawnParticle(Particle.DUST, loc, 3, 0.6, 0.8, 0.6, 0,
                        new Particle.DustOptions(Color.fromRGB(200, 230, 255), 1.5f));
                break;
            case 3:
                world.spawnParticle(Particle.SNOWFLAKE, loc, 8, 1, 1.2, 1, 0.05);
                world.spawnParticle(Particle.END_ROD, loc, 4, 0.8, 1, 0.8, 0.02);
                world.spawnParticle(Particle.DUST, loc, 5, 0.8, 1, 0.8, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 255, 255), 2.0f));
                if (tickCount % 2 == 0) {
                    world.spawnParticle(Particle.SNOWFLAKE, loc, 3, 0.5, 0.8, 0.5, 0.03);
                }
                break;
        }
    }

    // ==================== TARGETING ====================

    private int meleeCD = 0;

    private void retargetNearest() {
        if (bossEntity == null || bossEntity.isDead()) return;
        Player nearest = null;
        double minDist = Double.MAX_VALUE;
        for (Player p : bossEntity.getWorld().getPlayers()) {
            if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE) continue;
            double dist = p.getLocation().distance(bossEntity.getLocation());
            if (dist < minDist) {
                minDist = dist;
                nearest = p;
            }
        }
        if (nearest == null) return;

        // Forzar al golem a perseguir al jugador
        bossEntity.setTarget(nearest);
        // Empujar al golem hacia el jugador si está lejos
        if (minDist > 3.5 && minDist < 40) {
            Vector dir = nearest.getLocation().toVector().subtract(bossEntity.getLocation().toVector()).normalize();
            double speed = 0.35 + (phase * 0.05);
            Vector currentVel = bossEntity.getVelocity();
            bossEntity.setVelocity(new Vector(dir.getX() * speed, currentVel.getY(), dir.getZ() * speed));
        }

        Location bossLoc = bossEntity.getLocation();
        Location targetLoc = nearest.getLocation();

        // Ataque melee manual — el Iron Golem no ataca jugadores por defecto
        meleeCD = Math.max(0, meleeCD - 1);
        if (minDist < 3.5 && meleeCD <= 0) {
            meleeCD = 4; // ~1 segundo entre golpes (loop cada 5 ticks)
            double damage = 10 + phase * 4; // 14/18/22 por fase
            nearest.damage(damage, bossEntity);

            // Knockback glacial
            Vector kb = nearest.getLocation().toVector().subtract(bossLoc.toVector()).normalize().multiply(0.8).setY(0.4);
            nearest.setVelocity(kb);

            // Efecto visual de golpe
            World world = bossEntity.getWorld();
            world.spawnParticle(Particle.SNOWFLAKE, nearest.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.1);
            world.spawnParticle(Particle.BLOCK, nearest.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0,
                    Material.BLUE_ICE.createBlockData());
            world.playSound(bossLoc, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.5f, 0.7f);
            world.playSound(bossLoc, Sound.BLOCK_GLASS_BREAK, 0.8f, 1.5f);

            // Slow al golpear
            nearest.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, true));
        }

        // Persecución vertical
        double dy = targetLoc.getY() - bossLoc.getY();
        double horizontalDist = Math.sqrt(Math.pow(targetLoc.getX() - bossLoc.getX(), 2)
                + Math.pow(targetLoc.getZ() - bossLoc.getZ(), 2));

        if (dy > 3.0) {
            unreachableTicks++;

            if (unreachableTicks % 8 == 1) {
                Vector direction = targetLoc.toVector().subtract(bossLoc.toVector()).normalize();
                double launchY = Math.min(1.2, dy * 0.15);
                double launchH = Math.min(0.8, horizontalDist * 0.1);
                bossEntity.setVelocity(new Vector(direction.getX() * launchH, launchY, direction.getZ() * launchH));

                World world = bossEntity.getWorld();
                world.spawnParticle(Particle.SNOWFLAKE, bossLoc.clone().add(0, 0.5, 0), 15, 0.3, 0.1, 0.3, 0.05);
                world.spawnParticle(Particle.END_ROD, bossLoc.clone().add(0, 0.5, 0), 8, 0.3, 0.1, 0.3, 0.02);
                world.playSound(bossLoc, Sound.ENTITY_PHANTOM_FLAP, 0.8f, 1.5f);
            }

            if (unreachableTicks >= 40) {
                Location tpLoc = targetLoc.clone().add(0, -1, 0);
                bossEntity.teleport(tpLoc);
                unreachableTicks = 0;

                World world = bossEntity.getWorld();
                world.spawnParticle(Particle.SNOWFLAKE, tpLoc.clone().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.1);
                world.spawnParticle(Particle.END_ROD, tpLoc.clone().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0.03);
                world.playSound(tpLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 1.5f);

                for (Player p : world.getPlayers()) {
                    if (p.getLocation().distanceSquared(tpLoc) < 400) {
                        p.sendMessage(SmallCaps.convert("§b§l❄ §3¡Glacius no puede ser evitado tan fácilmente!"));
                    }
                }
            }
        } else {
            unreachableTicks = 0;
        }
    }

    // ==================== BOSS BAR ====================

    private void updateBossBar(double hpPercent) {
        if (bossBar == null) return;
        double progress = Math.max(0, Math.min(1, hpPercent));
        bossBar.setProgress(progress);

        long elapsed = System.currentTimeMillis() - spawnTimestamp;
        long remaining = BOSS_TIMEOUT_MS - elapsed;
        String timeStr = formatTime(remaining);
        String timeColor = remaining < 300_000 ? "§c" : remaining < 600_000 ? "§e" : "§a";

        String phaseText = phase == 1 ? "§3Fase 1: Escarcha Primordial" :
                phase == 2 ? "§9Fase 2: Ventisca Eterna" : "§f§lFase 3: Corazón del Glaciar";
        int hpPct = (int)(hpPercent * 100);
        bossBar.setTitle(SmallCaps.convert("§b§l❄ Glacius §8- " + phaseText + " §8| " + timeColor + "⏱ " + timeStr));

        World arenaWorld = bossEntity.getWorld();
        for (Player p : arenaWorld.getPlayers()) {
            if (!bossBar.getPlayers().contains(p)) bossBar.addPlayer(p);
            if (globalBossBar != null) globalBossBar.removePlayer(p);
        }

        if (globalBossBar == null) {
            org.bukkit.NamespacedKey glaciusGlobalKey = new org.bukkit.NamespacedKey(plugin, "boss_glacius_global");
            Bukkit.removeBossBar(glaciusGlobalKey);
            globalBossBar = Bukkit.createBossBar(glaciusGlobalKey,
                    "§b§l❄ GLACIUS §8| §3¡Usa /boss para unirte!",
                    BarColor.BLUE, BarStyle.SOLID);
            globalBossBar.setVisible(true);
        }

        String phaseShort = phase == 1 ? "§3Fase 1" : phase == 2 ? "§9Fase 2" : "§fFase 3";
        globalBossBar.setTitle(SmallCaps.convert("§b§l❄ GLACIUS §8| " + phaseShort + " §8| §fHP: §b" + hpPct + "% §8| " + timeColor + "⏱ " + timeStr + " §8| §e/boss"));
        globalBossBar.setProgress(progress);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().equals(arenaWorld)) continue;
            if (!globalBossBar.getPlayers().contains(p)) globalBossBar.addPlayer(p);
        }

        for (Player p : new ArrayList<>(bossBar.getPlayers())) {
            if (!p.isOnline() || !p.getWorld().equals(arenaWorld)) bossBar.removePlayer(p);
        }
        for (Player p : new ArrayList<>(globalBossBar.getPlayers())) {
            if (!p.isOnline() || p.getWorld().equals(arenaWorld)) globalBossBar.removePlayer(p);
        }
    }

    private String formatTime(long ms) {
        if (ms <= 0) return "0:00";
        long totalSec = ms / 1000;
        long min = totalSec / 60;
        long sec = totalSec % 60;
        return min + ":" + String.format("%02d", sec);
    }

    // ==================== TIMEOUT ====================

    private void onTimeout() {
        active = false;
        World world = arena.getBossSpawn().getWorld();
        Location loc = bossEntity != null ? bossEntity.getLocation() : arena.getBossSpawn();

        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (tick >= 60) {
                    if (bossEntity != null && !bossEntity.isDead()) bossEntity.remove();
                    cancel();
                    return;
                }

                double angle = tick * 0.4;
                double r = 2.0 - tick * 0.03;
                Location spiral = loc.clone().add(Math.cos(angle) * r, tick * 0.08, Math.sin(angle) * r);
                world.spawnParticle(Particle.SNOWFLAKE, spiral, 5, 0.1, 0.1, 0.1, 0.02);
                world.spawnParticle(Particle.DUST, spiral, 3, 0.1, 0.1, 0.1, 0,
                        new Particle.DustOptions(Color.fromRGB(150, 220, 255), 2.0f));

                if (tick == 0) {
                    world.playSound(loc, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 2.0f, 1.5f);
                    if (bossEntity != null) {
                        bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 10, false, false));
                        bossEntity.setAI(false);
                    }
                }
                if (tick == 30) world.playSound(loc, Sound.BLOCK_PORTAL_TRAVEL, 1.5f, 1.5f);
                if (tick == 55) {
                    world.spawnParticle(Particle.SNOWFLAKE, loc.clone().add(0, 2, 0), 60, 3, 3, 3, 0.15);
                    world.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 2.0f, 1.5f);
                }

                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        cleanupBossBars();
        cleanupSummons(world);
        cleanAllPhantomBlocks();

        for (Player p : world.getPlayers()) {
            p.sendTitle( SmallCaps.convert("§b§l⏱ TIEMPO AGOTADO"), SmallCaps.convert("§3Glacius se ha retirado al Glaciar Eterno..."), 5, 60, 20);
        }

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(SmallCaps.convert("§b§l⏱ ═══════════════════════════════ ⏱"));
        Bukkit.broadcastMessage(SmallCaps.convert("§3§l    ¡EL TIEMPO SE HA AGOTADO!"));
        Bukkit.broadcastMessage(SmallCaps.convert("§7  Nadie pudo derrotar a §b§lGlacius§7."));
        Bukkit.broadcastMessage(SmallCaps.convert("§7  Se ha retirado al §bGlaciar Eterno§7..."));
        Bukkit.broadcastMessage(SmallCaps.convert("§b§l⏱ ═══════════════════════════════ ⏱"));
        Bukkit.broadcastMessage("");

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.5f, 1.5f);
        }

        manager.onBossDeath(null);
    }

    // ==================== MUERTE ÉPICA ====================

    private void onDeath() {
        active = false;
        World world = arena.getBossSpawn().getWorld();
        Location deathLoc = bossEntity != null ? bossEntity.getLocation() : arena.getBossSpawn();

        if (bossEntity != null && !bossEntity.isDead()) {
            bossEntity.setAI(false);
            bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 255, false, false));
            bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0, false, false));
        }

        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (tick >= 120) {
                    if (bossEntity != null && !bossEntity.isDead()) bossEntity.remove();
                    cancel();

                    if (lastHitter != null && lastHitter.isOnline()) {
                        giveRewards(lastHitter);
                    }
                    for (Player p : world.getPlayers()) {
                        if (p != lastHitter) {
                            giveParticipationReward(p);
                        }
                    }
                    manager.onBossDeath(lastHitter);
                    return;
                }

                // FASE 1 (0-30): Cristales brotando del boss y agrietándose
                if (tick < 30) {
                    for (int i = 0; i < 4; i++) {
                        double angle = Math.random() * Math.PI * 2;
                        double dist = Math.random() * (tick * 0.1);
                        Location crack = deathLoc.clone().add(Math.cos(angle) * dist, Math.random() * 2, Math.sin(angle) * dist);
                        world.spawnParticle(Particle.DUST, crack, 2, 0.05, 0.05, 0.05, 0,
                                new Particle.DustOptions(Color.fromRGB(150, 220, 255), 1.5f));
                        world.spawnParticle(Particle.END_ROD, crack, 1, 0.05, 0.05, 0.05, 0);
                    }
                    if (tick % 5 == 0) {
                        world.playSound(deathLoc, Sound.BLOCK_GLASS_BREAK, 1.5f, 0.5f + tick * 0.03f);
                    }
                    if (tick == 0) {
                        for (Player p : world.getPlayers()) {
                            p.sendTitle("", SmallCaps.convert("§b§l\"No... el hielo eterno... se quiebra...\""), 0, 40, 10);
                        }
                    }
                }

                // FASE 2 (30-70): Columna de cristales ascendente
                if (tick >= 30 && tick < 70) {
                    int t = tick - 30;
                    double height = t * 0.15;

                    for (double y = 0; y < height; y += 0.5) {
                        double spiralAngle = y * 3 + t * 0.2;
                        double spiralR = 1.5 - y * 0.03;
                        Location crystalLoc = deathLoc.clone().add(
                                Math.cos(spiralAngle) * spiralR, y, Math.sin(spiralAngle) * spiralR);
                        world.spawnParticle(Particle.SNOWFLAKE, crystalLoc, 1, 0.05, 0.05, 0.05, 0.01);
                        world.spawnParticle(Particle.END_ROD, crystalLoc, 1, 0.05, 0.05, 0.05, 0);
                    }

                    if (t % 3 == 0) {
                        double ringR = 1 + t * 0.1;
                        for (int i = 0; i < 24; i++) {
                            double a = (Math.PI * 2.0 / 24) * i;
                            Location ring = deathLoc.clone().add(Math.cos(a) * ringR, 0.1, Math.sin(a) * ringR);
                            world.spawnParticle(Particle.SNOWFLAKE, ring, 1, 0, 0, 0, 0.01);
                        }
                    }

                    if (t % 10 == 0) {
                        world.playSound(deathLoc, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.5f, 0.3f + t * 0.02f);
                    }
                    if (tick == 45) {
                        for (Player p : world.getPlayers()) {
                            p.sendTitle("", SmallCaps.convert("§f§l\"¡VOLVERÉ... CON EL INVIERNO ETERNO!\""), 0, 40, 10);
                        }
                    }
                }

                // FASE 3 (70-100): Implosión glacial
                if (tick >= 70 && tick < 100) {
                    int t = tick - 70;
                    for (int i = 0; i < 8; i++) {
                        double angle = Math.random() * Math.PI * 2;
                        double dist = 5.0 - t * 0.15;
                        if (dist < 0.5) dist = 0.5;
                        Location from = deathLoc.clone().add(Math.cos(angle) * dist, 1 + Math.random() * 3, Math.sin(angle) * dist);
                        world.spawnParticle(Particle.DUST, from, 1, 0, 0, 0, 0,
                                new Particle.DustOptions(Color.fromRGB(100, 180, 255), 2.0f));
                        world.spawnParticle(Particle.END_ROD, from, 1, 0, 0, 0, 0.5);
                    }
                    if (t % 4 == 0) {
                        world.playSound(deathLoc, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.5f + t * 0.03f);
                    }
                }

                // FASE 4 (100-120): Gran explosión glacial
                if (tick == 100) {
                    world.spawnParticle(Particle.EXPLOSION_EMITTER, deathLoc.clone().add(0, 2, 0), 3, 0.5, 0.5, 0.5, 0);
                    world.spawnParticle(Particle.EXPLOSION, deathLoc.clone().add(0, 2, 0), 10, 3, 3, 3, 0);
                    world.spawnParticle(Particle.DUST, deathLoc.clone().add(0, 2, 0), 200, 6, 4, 6, 0,
                            new Particle.DustOptions(Color.fromRGB(180, 230, 255), 4.0f));
                    world.spawnParticle(Particle.SNOWFLAKE, deathLoc.clone().add(0, 2, 0), 150, 5, 4, 5, 0.2);
                    world.spawnParticle(Particle.END_ROD, deathLoc.clone().add(0, 2, 0), 80, 4, 3, 4, 0.15);

                    world.playSound(deathLoc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 1.5f);
                    world.playSound(deathLoc, Sound.ENTITY_WITHER_DEATH, 2.0f, 1.5f);
                    world.playSound(deathLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 1.5f);
                    world.playSound(deathLoc, Sound.BLOCK_GLASS_BREAK, 2.0f, 0.3f);

                    for (Player p : world.getPlayers()) {
                        if (p.getLocation().distanceSquared(deathLoc) < 100) {
                            Vector kb = p.getLocation().toVector().subtract(deathLoc.toVector()).normalize().multiply(1.5);
                            kb.setY(0.6);
                            p.setVelocity(kb);
                        }
                    }

                    for (Player p : world.getPlayers()) {
                        p.sendTitle("§b§l❄ GLACIUS HA CAÍDO ❄",
                                "§7Último golpe: §e§l" + (lastHitter != null ? lastHitter.getName() : "N/A"), 5, 80, 30);
                    }

                    // Ice spikes decorativos alrededor del punto de muerte
                    for (int i = 0; i < 8; i++) {
                        double angle = (Math.PI * 2.0 / 8) * i;
                        Location spikeLoc = deathLoc.clone().add(Math.cos(angle) * 3, 0, Math.sin(angle) * 3);
                        spawnIceSpike(spikeLoc, world, 3 + (int)(Math.random() * 3));
                    }
                }

                // Lluvia de nieve post-explosión
                if (tick > 100 && tick < 120) {
                    world.spawnParticle(Particle.SNOWFLAKE, deathLoc.clone().add(0, 8, 0), 20, 6, 2, 6, 0.05);
                    world.spawnParticle(Particle.END_ROD, deathLoc.clone().add(0, 6, 0), 5, 4, 2, 4, 0.02);
                }

                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        cleanupBossBars();
        cleanupSummons(world);
        // Phantom blocks se limpian solos

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(SmallCaps.convert("§b§l❄ ═══════════════════════════════ ❄"));
        Bukkit.broadcastMessage(SmallCaps.convert("§3§l    ¡GLACIUS HA CAÍDO!"));
        Bukkit.broadcastMessage(SmallCaps.convert("§7  Último golpe: §e§l" + (lastHitter != null ? lastHitter.getName() : "N/A")));
        Bukkit.broadcastMessage(SmallCaps.convert("§b§l❄ ═══════════════════════════════ ❄"));
        Bukkit.broadcastMessage("");

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        }
    }

    // ==================== RECOMPENSAS ====================

    private void giveRewards(Player player) {
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§b§l❄ ═══════════════════════════ ❄"));
        player.sendMessage(SmallCaps.convert("§3§l  ¡RECOMPENSA DEL ÚLTIMO GOLPE!"));
        player.sendMessage(SmallCaps.convert("§7  Has derrotado a §b§lGlacius§7."));
        player.sendMessage(SmallCaps.convert("§b§l❄ ═══════════════════════════ ❄"));
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
                new ItemStack(Material.BLUE_ICE, 64)
        );

        // Esencias reward
        if (plugin.getEssenceManager() != null) {
            plugin.getEssenceManager().onBossKill(player);
        }

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "crates give " + player.getName() + " GlaciusKey 3");
    }

    private void giveParticipationReward(Player player) {
        player.sendMessage(SmallCaps.convert("§7Has participado en la batalla contra §b§lGlacius§7."));
        player.sendMessage(SmallCaps.convert("§7Recompensa de participación recibida."));

        player.getInventory().addItem(
                new ItemStack(Material.DIAMOND, 8),
                new ItemStack(Material.GOLDEN_APPLE, 8),
                new ItemStack(Material.EXPERIENCE_BOTTLE, 32)
        );

        // Esencias reward
        if (plugin.getEssenceManager() != null) {
            plugin.getEssenceManager().onBossParticipation(player);
        }
    }

    // ==================== UTILS ====================

    private Player getRandomNearbyPlayer(double range) {
        if (bossEntity == null) return null;
        List<Player> nearby = new ArrayList<>();
        for (Player p : bossEntity.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(bossEntity.getLocation()) < range * range) {
                nearby.add(p);
            }
        }
        if (nearby.isEmpty()) return null;
        return nearby.get(new Random().nextInt(nearby.size()));
    }

    private void cleanExpiredPhantomBlocks() {
        Iterator<PhantomBlock> it = phantomBlocks.iterator();
        while (it.hasNext()) {
            PhantomBlock pb = it.next();
            if (pb.isExpired() || pb.stand == null || pb.stand.isDead()) {
                pb.remove();
                it.remove();
            }
        }
    }

    private void cleanAllPhantomBlocks() {
        for (PhantomBlock pb : phantomBlocks) {
            pb.remove();
        }
        phantomBlocks.clear();
        // Limpieza inmediata por tag en el mundo
        sweepPhantomStands();
        // Limpieza de seguridad repetida: 1s, 5s y 15s después
        Bukkit.getScheduler().runTaskLater(plugin, this::sweepPhantomStands, 20L);
        Bukkit.getScheduler().runTaskLater(plugin, this::sweepPhantomStands, 100L);
        Bukkit.getScheduler().runTaskLater(plugin, this::sweepPhantomStands, 300L);
    }

    private void sweepPhantomStands() {
        World world = arena.getBossSpawn().getWorld();
        if (world == null) return;
        for (Entity e : world.getEntities()) {
            if (e instanceof ArmorStand && e.getScoreboardTags().contains("glacius_phantom")) {
                e.remove();
            }
        }
    }

    private void cleanupBossBars() {
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar.setVisible(false);
        }
        if (globalBossBar != null) {
            globalBossBar.removeAll();
            globalBossBar.setVisible(false);
            globalBossBar = null;
        }
    }

    private void cleanupSummons(World world) {
        for (Entity e : summonedMobs) {
            if (e != null && !e.isDead()) {
                world.spawnParticle(Particle.SNOWFLAKE, e.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.03);
                e.remove();
            }
        }
        summonedMobs.clear();
    }

    // ==================== DAMAGE + INTERFACE ====================

    @Override
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();

        // Ignorar daño de mobs invocados por el boss
        if (isSummonedMob(damager)) {
            event.setCancelled(true);
            return;
        }
        // Ignorar daño de proyectiles lanzados por mobs invocados
        if (damager instanceof Projectile) {
            Projectile proj = (Projectile) damager;
            if (proj.getShooter() instanceof Entity && isSummonedMob((Entity) proj.getShooter())) {
                event.setCancelled(true);
                return;
            }
            if (proj.getShooter() instanceof Player) {
                lastHitter = (Player) proj.getShooter();
            }
            return;
        }

        if (damager instanceof Player) {
            lastHitter = (Player) damager;
        }
    }

    // ==================== CLEANUP ====================

    @Override
    public void cleanup() {
        active = false;
        if (mainLoop != null) mainLoop.cancel();
        if (bossEntity != null && !bossEntity.isDead()) bossEntity.remove();
        World world = arena.getBossSpawn().getWorld();
        if (world != null) cleanupSummons(world);
        cleanupBossBars();
        cleanAllPhantomBlocks();
    }

    // ==================== GETTERS ====================

    @Override public boolean isActive() { return active; }
    @Override public String getBossName() { return "Glacius"; }
    @Override public BossType getBossType() { return BossType.GLACIUS; }
    public IronGolem getBossEntity() { return bossEntity; }
    @Override public Player getLastHitter() { return lastHitter; }

    @Override
    public boolean isBossEntity(Entity entity) {
        return bossEntity != null && entity.getUniqueId().equals(bossEntity.getUniqueId());
    }

    @Override
    public boolean isSummonedMob(Entity entity) {
        for (Entity e : summonedMobs) {
            if (e.getUniqueId().equals(entity.getUniqueId())) return true;
        }
        return false;
    }

    private void checkAndFixBossLocation() {
        if (bossEntity == null || bossEntity.isDead()) return;
        Location bossLoc = bossEntity.getLocation();
        Location headLoc = bossLoc.clone().add(0, 1, 0);
        Location feetLoc = bossLoc.clone();
        boolean headInSolid = headLoc.getBlock().getType().isSolid();
        boolean feetInSolid = feetLoc.getBlock().getType().isSolid();
        if (headInSolid || feetInSolid) {
            plugin.getLogger().warning("[Glacius] ¡EMERGENCIA! Boss detectado bajo tierra en Y=" + bossLoc.getBlockY());
            Location safeLoc = getSafeLocation(bossLoc);
            bossEntity.teleport(safeLoc);
            World world = safeLoc.getWorld();
            world.spawnParticle(Particle.PORTAL, safeLoc, 100, 1, 1, 1, 0.5);
            world.spawnParticle(Particle.END_ROD, safeLoc, 3, 0, 0, 0, 0);
            world.playSound(safeLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 0.8f);
            plugin.getLogger().info("[Glacius] Boss teleportado a ubicación segura Y=" + safeLoc.getBlockY());
        }
    }

    private Location getSafeLocation(Location original) {
        World world = original.getWorld();
        Location checkLoc = original.clone();
        for (int i = 0; i < 10; i++) {
            Location above = checkLoc.clone().add(0, i, 0);
            Location twoAbove = checkLoc.clone().add(0, i + 1, 0);
            Location threeAbove = checkLoc.clone().add(0, i + 2, 0);
            if (above.getBlock().getType().isAir() && twoAbove.getBlock().getType().isAir() && threeAbove.getBlock().getType().isAir()) {
                Location below = above.clone().subtract(0, 1, 0);
                if (below.getBlock().getType().isSolid()) {
                    plugin.getLogger().info("[Glacius] Ubicación segura encontrada en Y=" + above.getBlockY());
                    return above;
                }
            }
        }
        plugin.getLogger().warning("[Glacius] No se encontró ubicación segura, usando ubicación ajustada");
        Location fallback = original.clone();
        fallback.setY(world.getHighestBlockYAt(original) + 1);
        return fallback;
    }
}
