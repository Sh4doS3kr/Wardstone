package com.moonlight.coreprotect.bossarena;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
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
 * Vorgath - Señor del Bosque Carmesí
 *
 * Boss épico con 3 fases, partículas increíbles y mecánicas únicas.
 * El ÚLTIMO GOLPE (last hit) determina quién se lleva el premio.
 *
 * FASE 1 (100%-60%): Furia Carmesí
 *   - Eruption: Columnas de fuego del suelo
 *   - Crimson Slam: Salto + onda expansiva
 *   - Summon: Invoca Piglins espectrales
 *
 * FASE 2 (60%-30%): Tormenta de Esporas
 *   - Spore Rain: Lluvia de bloques fantasma + daño
 *   - Wither Breath: Cono de wither
 *   - Crimson Tendrils: Zarcillos que atraen jugadores
 *
 * FASE 3 (30%-0%): Ira del Vacío
 *   - Blood Moon: Pantalla roja + daño AoE
 *   - Soul Harvest: Drena vida de todos los cercanos
 *   - Summon Elite: Invoca un Brute especial
 *   - Enrage: Velocidad y daño aumentados
 */
public class VorgathBoss implements Boss {

    private final CoreProtectPlugin plugin;
    private final BossArenaManager manager;
    private final ArenaData arena;

    private Zombie bossEntity;
    private BossBar bossBar;
    private BossBar globalBossBar;
    private BukkitTask mainLoop;
    private boolean active = false;
    private int tickCount = 0;
    private int phase = 1;
    private int highestPhaseReached = 1;

    // Timer de 45 minutos
    private static final long BOSS_TIMEOUT_MS = 45 * 60 * 1000L; // 45 minutos
    private long spawnTimestamp;

    // Último jugador que golpeó al boss
    private Player lastHitter;

    // Mobs invocados
    private final List<Entity> summonedMobs = new ArrayList<>();

    // Cooldowns de habilidades (en ticks)
    private int eruptionCD = 0;
    private int slamCD = 0;
    private int summonCD = 0;
    private int sporeCD = 0;
    private int witherBreathCD = 0;
    private int tendrilCD = 0;
    private int bloodMoonCD = 0;
    private int soulHarvestCD = 0;
    private int eliteSummonCD = 0;
    // Nuevas habilidades
    private int infernoWallCD = 0;
    private int crimsonMeteorCD = 0;
    private int phantomChargeCD = 0;
    private int netherGeyserCD = 0;
    private int voidRiftCD = 0;

    // Cooldown global: solo 1 habilidad por ciclo, mínimo 1s entre habilidades
    private int globalAbilityCD = 0;

    // HP del boss (reducido para evitar bugs)
    private static final double MAX_HEALTH = 2048.0;

    public VorgathBoss(CoreProtectPlugin plugin, BossArenaManager manager, ArenaData arena) {
        this.plugin = plugin;
        this.manager = manager;
        this.arena = arena;
    }

    // ==================== SPAWN ====================

    public void spawn() {
        Location spawnLoc = arena.getBossSpawn();
        World world = spawnLoc.getWorld();

        // Efecto de despertar épico
        awakening(spawnLoc, world);

        // Spawnear boss directamente en ubicación segura
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active) return;
            
            // PROTECCIÓN ANTI-DUPLICADO: Si ya existe un bossEntity, no spawnear otro
            if (bossEntity != null && !bossEntity.isDead()) {
                plugin.getLogger().warning("[Vorgath] Intento de spawn duplicado bloqueado - boss ya existe");
                return;
            }

            // Encontrar ubicación segura primero
            Location safeLoc = getSafeLocation(spawnLoc);

            bossEntity = world.spawn(safeLoc, Zombie.class, z -> {
                z.setCustomName(SmallCaps.convert("§4§l⚔ Vorgath §8- §cSeñor del Bosque Carmesí §4§l⚔"));
                z.setCustomNameVisible(true);
                z.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).setBaseValue(MAX_HEALTH);
                z.setHealth(MAX_HEALTH);
                z.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(16);
                z.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.26);
                z.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ARMOR).setBaseValue(8);
                z.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ARMOR_TOUGHNESS).setBaseValue(2.0);
                z.getAttribute(org.bukkit.attribute.Attribute.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(0.0);
                z.getAttribute(org.bukkit.attribute.Attribute.GENERIC_SCALE).setBaseValue(2.0); // Tamaño doble desde el inicio
                z.setBaby(false);
                z.setRemoveWhenFarAway(false);
                z.setPersistent(true);
                z.setSilent(false); // No silencioso para que se escuche

                // Armadura carmesí
                ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
                org.bukkit.inventory.meta.LeatherArmorMeta helmetMeta = (org.bukkit.inventory.meta.LeatherArmorMeta) helmet.getItemMeta();
                helmetMeta.setColor(Color.fromRGB(139, 0, 0));
                helmetMeta.setDisplayName(SmallCaps.convert("§4Corona de Vorgath"));
                helmetMeta.addEnchant(org.bukkit.enchantments.Enchantment.PROTECTION, 4, true);
                helmetMeta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 5, true);
                helmet.setItemMeta(helmetMeta);
                z.getEquipment().setHelmet(helmet);
                z.getEquipment().setHelmetDropChance(0);

                // Peto de obsidiana con runas
                ItemStack chestplate = new ItemStack(Material.LEATHER_CHESTPLATE);
                org.bukkit.inventory.meta.LeatherArmorMeta chestMeta = (org.bukkit.inventory.meta.LeatherArmorMeta) chestplate.getItemMeta();
                chestMeta.setColor(Color.fromRGB(80, 0, 0));
                chestMeta.setDisplayName(SmallCaps.convert("§4Pecho de Vorgath"));
                chestMeta.addEnchant(org.bukkit.enchantments.Enchantment.PROTECTION, 4, true);
                chestMeta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 5, true);
                chestplate.setItemMeta(chestMeta);
                z.getEquipment().setChestplate(chestplate);
                z.getEquipment().setChestplateDropChance(0);

                // Grebas de sangre
                ItemStack leggings = new ItemStack(Material.LEATHER_LEGGINGS);
                org.bukkit.inventory.meta.LeatherArmorMeta leggingsMeta = (org.bukkit.inventory.meta.LeatherArmorMeta) leggings.getItemMeta();
                leggingsMeta.setColor(Color.fromRGB(120, 0, 0));
                leggingsMeta.setDisplayName(SmallCaps.convert("§4Grebas de Vorgath"));
                leggingsMeta.addEnchant(org.bukkit.enchantments.Enchantment.PROTECTION, 4, true);
                leggingsMeta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 5, true);
                leggings.setItemMeta(leggingsMeta);
                z.getEquipment().setLeggings(leggings);
                z.getEquipment().setLeggingsDropChance(0);

                // Botas de ceniza
                ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);
                org.bukkit.inventory.meta.LeatherArmorMeta bootsMeta = (org.bukkit.inventory.meta.LeatherArmorMeta) boots.getItemMeta();
                bootsMeta.setColor(Color.fromRGB(60, 0, 0));
                bootsMeta.setDisplayName(SmallCaps.convert("§4Botas de Vorgath"));
                bootsMeta.addEnchant(org.bukkit.enchantments.Enchantment.PROTECTION, 4, true);
                bootsMeta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 5, true);
                boots.setItemMeta(bootsMeta);
                z.getEquipment().setBoots(boots);
                z.getEquipment().setBootsDropChance(0);

                // Espada carmesí
                ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
                org.bukkit.inventory.meta.ItemMeta swordMeta = sword.getItemMeta();
                swordMeta.setDisplayName(SmallCaps.convert("§4§lEspada Carmesí de Vorgath"));
                swordMeta.addEnchant(org.bukkit.enchantments.Enchantment.SHARPNESS, 6, true);
                swordMeta.addEnchant(org.bukkit.enchantments.Enchantment.FIRE_ASPECT, 3, true);
                swordMeta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 5, true);
                sword.setItemMeta(swordMeta);
                z.getEquipment().setItemInMainHand(sword);
                z.getEquipment().setItemInMainHandDropChance(0);

                z.setAI(true); // Activar IA inmediatamente
            });

            // Efectos de spawn
            world.spawnParticle(Particle.EXPLOSION, safeLoc.clone().add(0, 1, 0), 10, 1, 1, 1, 0.5);
            world.spawnParticle(Particle.FLAME, safeLoc.clone().add(0, 1, 0), 30, 1.5, 1.5, 1.5, 0.2);
            world.playSound(safeLoc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f);
            world.playSound(safeLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.3f);

            // Timer empieza al spawnear
            spawnTimestamp = System.currentTimeMillis();

            // BossBar (keyed)
            org.bukkit.NamespacedKey vorgathKey = new org.bukkit.NamespacedKey(plugin, "boss_vorgath");
            Bukkit.removeBossBar(vorgathKey);
            bossBar = Bukkit.createBossBar(vorgathKey, "§4§l⚔ Vorgath §8- §cFase 1: Furia Carmesí",
                    BarColor.RED, BarStyle.SEGMENTED_20);
            bossBar.setVisible(true);
            for (Player p : world.getPlayers()) bossBar.addPlayer(p);

            // Main loop
            startMainLoop();

        }, 60L); // 3 segundos para el efecto de despertar

        active = true;
    }

    private void awakening(Location loc, World world) {
        // Efecto épico de despertar
        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (tick >= 100) { cancel(); return; }

                // Espiral de partículas carmesí
                double angle = tick * 0.3;
                double radius = 1.0 + tick * 0.05;
                double x = Math.cos(angle) * radius;
                double z = Math.sin(angle) * radius;
                Location particleLoc = loc.clone().add(x, tick * 0.05, z);

                world.spawnParticle(Particle.DUST, particleLoc, 5, 0.1, 0.1, 0.1, 0,
                        new Particle.DustOptions(Color.fromRGB(180, 20, 20), 2.0f));
                world.spawnParticle(Particle.FLAME, particleLoc, 2, 0.1, 0.1, 0.1, 0.01);

                // Columna central
                if (tick % 5 == 0) {
                    world.spawnParticle(Particle.LAVA, loc.clone().add(0, tick * 0.05, 0), 3, 0.3, 0.3, 0.3, 0);
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, 0.5, 0), 8, 0.5, 1, 0.5, 0.02);
                }

                // Ondas de energía en el suelo
                if (tick % 10 == 0) {
                    double r = tick * 0.1;
                    for (int i = 0; i < 20; i++) {
                        double a = (Math.PI * 2.0 / 20) * i;
                        Location ring = loc.clone().add(Math.cos(a) * r, 0.1, Math.sin(a) * r);
                        world.spawnParticle(Particle.DUST, ring, 1, 0, 0, 0, 0,
                                new Particle.DustOptions(Color.fromRGB(200, 50, 0), 1.5f));
                    }
                }

                // Sonidos progresivos
                if (tick == 0) world.playSound(loc, Sound.AMBIENT_NETHER_WASTES_MOOD, 2.0f, 0.3f);
                if (tick == 30) world.playSound(loc, Sound.ENTITY_WITHER_AMBIENT, 2.0f, 0.5f);
                if (tick == 60) world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.4f);
                if (tick == 80) world.playSound(loc, Sound.ENTITY_WITHER_SPAWN, 2.0f, 0.6f);

                // Explosión final de partículas
                if (tick == 95) {
                    world.spawnParticle(Particle.EXPLOSION, loc.clone().add(0, 2, 0), 5, 1, 1, 1, 0);
                    world.spawnParticle(Particle.DUST, loc.clone().add(0, 2, 0), 80, 3, 3, 3, 0,
                            new Particle.DustOptions(Color.fromRGB(255, 30, 0), 3.0f));
                    world.spawnParticle(Particle.FLAME, loc.clone().add(0, 2, 0), 50, 2, 2, 2, 0.1);
                    world.spawnParticle(Particle.LAVA, loc.clone().add(0, 2, 0), 30, 2, 2, 2, 0);
                    world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);

                    for (Player p : world.getPlayers()) {
                        p.sendTitle( SmallCaps.convert("§4§l⚔ VORGATH ⚔"), SmallCaps.convert("§c\"¡Arderéis en el Bosque Carmesí!\""), 5, 40, 15);
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

                // Verificar timeout de 45 minutos
                long elapsed = System.currentTimeMillis() - spawnTimestamp;
                if (elapsed >= BOSS_TIMEOUT_MS) {
                    onTimeout();
                    cancel();
                    return;
                }

                // Actualizar fase (solo avanza, nunca retrocede)
                double hpPercent = bossEntity.getHealth() / MAX_HEALTH;
                int newPhase = hpPercent > 0.6 ? 1 : hpPercent > 0.3 ? 2 : 3;
                phase = Math.max(phase, newPhase);
                if (phase > highestPhaseReached) {
                    highestPhaseReached = phase;
                    onPhaseChange();
                }

                // Decrementar cooldowns
                eruptionCD = Math.max(0, eruptionCD - 1);
                slamCD = Math.max(0, slamCD - 1);
                summonCD = Math.max(0, summonCD - 1);
                sporeCD = Math.max(0, sporeCD - 1);
                witherBreathCD = Math.max(0, witherBreathCD - 1);
                tendrilCD = Math.max(0, tendrilCD - 1);
                bloodMoonCD = Math.max(0, bloodMoonCD - 1);
                soulHarvestCD = Math.max(0, soulHarvestCD - 1);
                eliteSummonCD = Math.max(0, eliteSummonCD - 1);
                infernoWallCD = Math.max(0, infernoWallCD - 1);
                crimsonMeteorCD = Math.max(0, crimsonMeteorCD - 1);
                phantomChargeCD = Math.max(0, phantomChargeCD - 1);
                netherGeyserCD = Math.max(0, netherGeyserCD - 1);
                voidRiftCD = Math.max(0, voidRiftCD - 1);
                globalAbilityCD = Math.max(0, globalAbilityCD - 1);

                // Actualizar BossBar
                updateBossBar(hpPercent);

                // Partículas ambientales
                ambientParticles();

                // Targeting
                retargetNearest();

                // Ejecutar habilidades según fase
                switch (phase) {
                    case 1: tickPhase1(); break;
                    case 2: tickPhase2(); break;
                    case 3: tickPhase3(); break;
                }

                // Limpiar mobs muertos
                summonedMobs.removeIf(e -> e.isDead() || !e.isValid());
            }
        }.runTaskTimer(plugin, 0L, 5L); // Cada 5 ticks
    }

    // ==================== FASES ====================

    private void onPhaseChange() {
        World world = bossEntity.getWorld();
        Location loc = bossEntity.getLocation();

        switch (phase) {
            case 2:
                bossBar.setTitle(SmallCaps.convert("§4§l⚔ Vorgath §8- §5Fase 2: Tormenta de Esporas"));
                bossBar.setColor(BarColor.PURPLE);
                bossEntity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(22);
                bossEntity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.28);

                // Efecto de transición
                world.spawnParticle(Particle.DUST, loc.clone().add(0, 2, 0), 60, 3, 2, 3, 0,
                        new Particle.DustOptions(Color.fromRGB(130, 0, 180), 2.5f));
                world.spawnParticle(Particle.SMOKE, loc.clone().add(0, 1, 0), 40, 2, 1, 2, 0.05);
                world.playSound(loc, Sound.ENTITY_WITHER_HURT, 2.0f, 0.3f);

                for (Player p : world.getPlayers()) {
                    p.sendTitle( SmallCaps.convert("§5§lFASE 2"), SmallCaps.convert("§d\"¡Las esporas os consumirán!\""), 5, 40, 15);
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 0.4f);
                }
                break;

            case 3:
                bossBar.setTitle(SmallCaps.convert("§4§l⚔ Vorgath §8- §0Fase 3: Ira del Vacío"));
                bossBar.setColor(BarColor.WHITE);
                bossEntity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(28);
                bossEntity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.32);
                bossEntity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(0.0);
                bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999, 1, false, false));

                // Efecto de transición épica
                world.spawnParticle(Particle.DUST, loc.clone().add(0, 2, 0), 100, 4, 3, 4, 0,
                        new Particle.DustOptions(Color.fromRGB(30, 0, 0), 3.0f));
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, 1, 0), 60, 3, 2, 3, 0.05);
                world.spawnParticle(Particle.EXPLOSION, loc.clone().add(0, 2, 0), 3, 1, 1, 1, 0);
                world.playSound(loc, Sound.ENTITY_WITHER_SPAWN, 2.0f, 0.3f);

                for (Player p : world.getPlayers()) {
                    p.sendTitle( SmallCaps.convert("§4§lFASE FINAL"), SmallCaps.convert("§c\"¡NO HAY ESCAPATORIA!\""), 5, 50, 20);
                    p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 0.3f);
                }
                break;
        }
    }

    private void tickPhase1() {
        if (globalAbilityCD > 0) return;
        // Eruption cada 6s
        if (eruptionCD <= 0 && Math.random() < 0.4) {
            eruption(); eruptionCD = 24; globalAbilityCD = 4; return;
        }
        // Crimson Slam cada 10s
        if (slamCD <= 0 && Math.random() < 0.3) {
            crimsonSlam(); slamCD = 40; globalAbilityCD = 4; return;
        }
        // Inferno Wall cada 12s
        if (infernoWallCD <= 0 && Math.random() < 0.3) {
            infernoWall(); infernoWallCD = 48; globalAbilityCD = 4; return;
        }
        // Nether Geyser cada 10s
        if (netherGeyserCD <= 0 && Math.random() < 0.3) {
            netherGeyser(); netherGeyserCD = 40; globalAbilityCD = 4; return;
        }
        // Summon cada 20s
        if (summonCD <= 0 && summonedMobs.size() < 6) {
            summonCrimsonMinions(); summonCD = 80; globalAbilityCD = 4; return;
        }
    }

    private void tickPhase2() {
        if (globalAbilityCD > 0) return;
        // Rotación aleatoria entre fase 2 y fase 1
        if (Math.random() < 0.5) {
            // Fase 2 primero
            if (sporeCD <= 0 && Math.random() < 0.4) {
                sporeRain(); sporeCD = 32; globalAbilityCD = 4; return;
            }
            if (witherBreathCD <= 0 && Math.random() < 0.4) {
                witherBreath(); witherBreathCD = 24; globalAbilityCD = 4; return;
            }
            if (tendrilCD <= 0 && Math.random() < 0.3) {
                crimsonTendrils(); tendrilCD = 48; globalAbilityCD = 4; return;
            }
            if (phantomChargeCD <= 0 && Math.random() < 0.35) {
                phantomCharge(); phantomChargeCD = 40; globalAbilityCD = 4; return;
            }
            if (infernoWallCD <= 0 && Math.random() < 0.25) {
                infernoWall(); infernoWallCD = 60; globalAbilityCD = 4; return;
            }
            if (summonCD <= 0 && summonedMobs.size() < 8) {
                summonCrimsonMinions(); summonCD = 60; globalAbilityCD = 4; return;
            }
            // Fallback a fase 1
            tickPhase1();
        } else {
            // Fase 1 primero, luego fase 2
            tickPhase1();
            if (globalAbilityCD > 0) return;
            if (witherBreathCD <= 0 && Math.random() < 0.4) {
                witherBreath(); witherBreathCD = 24; globalAbilityCD = 4; return;
            }
            if (sporeCD <= 0 && Math.random() < 0.4) {
                sporeRain(); sporeCD = 32; globalAbilityCD = 4; return;
            }
            if (phantomChargeCD <= 0 && Math.random() < 0.35) {
                phantomCharge(); phantomChargeCD = 40; globalAbilityCD = 4; return;
            }
            if (tendrilCD <= 0 && Math.random() < 0.3) {
                crimsonTendrils(); tendrilCD = 48; globalAbilityCD = 4; return;
            }
        }
    }

    private void tickPhase3() {
        if (globalAbilityCD > 0) return;
        // Rotación aleatoria de todas las habilidades de fase 3 + herencia
        if (Math.random() < 0.5) {
            // Fase 3 primero
            if (bloodMoonCD <= 0 && Math.random() < 0.3) {
                bloodMoon(); bloodMoonCD = 60; globalAbilityCD = 5; return;
            }
            if (soulHarvestCD <= 0 && Math.random() < 0.5) {
                soulHarvest(); soulHarvestCD = 32; globalAbilityCD = 4; return;
            }
            if (crimsonMeteorCD <= 0 && Math.random() < 0.4) {
                crimsonMeteor(); crimsonMeteorCD = 40; globalAbilityCD = 4; return;
            }
            if (voidRiftCD <= 0 && Math.random() < 0.3) {
                voidRift(); voidRiftCD = 60; globalAbilityCD = 5; return;
            }
            if (phantomChargeCD <= 0 && Math.random() < 0.4) {
                phantomCharge(); phantomChargeCD = 30; globalAbilityCD = 4; return;
            }
            if (eliteSummonCD <= 0 && summonedMobs.size() < 4) {
                summonElite(); eliteSummonCD = 100; globalAbilityCD = 4; return;
            }
            if (eruptionCD <= 0 && Math.random() < 0.5) {
                eruption(); eruptionCD = 16; globalAbilityCD = 4; return;
            }
            if (slamCD <= 0 && Math.random() < 0.4) {
                crimsonSlam(); slamCD = 30; globalAbilityCD = 4; return;
            }
        } else {
            // Fase 2 primero, luego fase 3
            tickPhase2();
            if (globalAbilityCD > 0) return;
            if (crimsonMeteorCD <= 0 && Math.random() < 0.4) {
                crimsonMeteor(); crimsonMeteorCD = 40; globalAbilityCD = 4; return;
            }
            if (bloodMoonCD <= 0 && Math.random() < 0.3) {
                bloodMoon(); bloodMoonCD = 60; globalAbilityCD = 5; return;
            }
            if (soulHarvestCD <= 0 && Math.random() < 0.5) {
                soulHarvest(); soulHarvestCD = 32; globalAbilityCD = 4; return;
            }
            if (voidRiftCD <= 0 && Math.random() < 0.3) {
                voidRift(); voidRiftCD = 60; globalAbilityCD = 5; return;
            }
            if (eliteSummonCD <= 0 && summonedMobs.size() < 4) {
                summonElite(); eliteSummonCD = 100; globalAbilityCD = 4; return;
            }
        }
    }

    // ==================== HABILIDADES FASE 1 ====================

    private void eruption() {
        World world = bossEntity.getWorld();
        Location bLoc = bossEntity.getLocation();

        world.playSound(bLoc, Sound.ENTITY_BLAZE_SHOOT, 2.0f, 0.5f);

        // Columnas de fuego en la posición de jugadores cercanos
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distance(bLoc) > 30) continue;

            Location target = p.getLocation().clone();
            // Indicador 1 segundo antes
            for (int i = 0; i < 20; i++) {
                double angle = (Math.PI * 2.0 / 20) * i;
                Location indicator = target.clone().add(Math.cos(angle) * 1.5, 0.1, Math.sin(angle) * 1.5);
                world.spawnParticle(Particle.DUST, indicator, 1, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 80, 0), 1.2f));
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!active) return;
                // Columna de fuego
                for (double y = 0; y < 5; y += 0.5) {
                    world.spawnParticle(Particle.FLAME, target.clone().add(0, y, 0), 8, 0.5, 0.1, 0.5, 0.02);
                    world.spawnParticle(Particle.LAVA, target.clone().add(0, y, 0), 2, 0.3, 0.1, 0.3, 0);
                }
                world.spawnParticle(Particle.DUST, target.clone().add(0, 2, 0), 20, 0.8, 1, 0.8, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 100, 0), 2.0f));
                world.playSound(target, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);

                // Daño a jugadores en el área
                for (Player nearby : world.getPlayers()) {
                    if (nearby.getLocation().distance(target) <= 2.0) {
                        nearby.damage(8.0, bossEntity);
                        nearby.setFireTicks(40);
                    }
                }
            }, 20L);
        }
    }

    private void crimsonSlam() {
        if (bossEntity.getTarget() == null) return;
        World world = bossEntity.getWorld();
        Location bLoc = bossEntity.getLocation();

        // Salto
        bossEntity.setVelocity(new Vector(0, 1.2, 0));
        world.playSound(bLoc, Sound.ENTITY_IRON_GOLEM_ATTACK, 2.0f, 0.5f);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || bossEntity == null || bossEntity.isDead()) return;
            Location landLoc = bossEntity.getLocation();

            // Onda expansiva
            world.spawnParticle(Particle.EXPLOSION, landLoc, 3, 1, 0.5, 1, 0);
            world.spawnParticle(Particle.DUST, landLoc, 50, 4, 0.5, 4, 0,
                    new Particle.DustOptions(Color.fromRGB(180, 20, 0), 2.0f));
            world.spawnParticle(Particle.FLAME, landLoc, 30, 3, 0.3, 3, 0.05);

            // Bloques fantasma
            for (int i = 0; i < 15; i++) {
                double angle = Math.random() * Math.PI * 2;
                double dist = 1 + Math.random() * 4;
                Location blockLoc = landLoc.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
                blockLoc.setY(landLoc.getY());
                for (Player p : world.getPlayers()) {
                    p.sendBlockChange(blockLoc, Material.CRIMSON_NYLIUM.createBlockData());
                }
                // Revertir después de 3s
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    for (Player p : world.getPlayers()) {
                        p.sendBlockChange(blockLoc, blockLoc.getBlock().getBlockData());
                    }
                }, 60L);
            }

            world.playSound(landLoc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);

            for (Player p : world.getPlayers()) {
                if (p.getLocation().distance(landLoc) <= 5.0) {
                    p.damage(12.0, bossEntity);
                    Vector knockback = p.getLocation().toVector().subtract(landLoc.toVector()).normalize().multiply(1.5).setY(0.5);
                    p.setVelocity(knockback);
                }
            }
        }, 15L);
    }

    /**
     * Busca una ubicación segura cerca de una posición base.
     * Evita agua, lava, bloques sólidos encima, y asegura suelo sólido.
     */
    private Location findSafeLocation(Location base, double radius) {
        World world = base.getWorld();
        for (int attempt = 0; attempt < 15; attempt++) {
            double angle = Math.random() * Math.PI * 2;
            double dist = 2 + Math.random() * radius;
            double x = base.getX() + Math.cos(angle) * dist;
            double z = base.getZ() + Math.sin(angle) * dist;
            // Buscar suelo sólido
            Location check = new Location(world, x, base.getY() + 3, z);
            for (int y = 0; y < 8; y++) {
                Location feet = check.clone().add(0, -y, 0);
                Material below = feet.clone().add(0, -1, 0).getBlock().getType();
                Material atFeet = feet.getBlock().getType();
                Material atHead = feet.clone().add(0, 1, 0).getBlock().getType();
                if (below.isSolid() && !below.name().contains("WATER") && !below.name().contains("LAVA")
                        && !atFeet.isSolid() && !atHead.isSolid()
                        && atFeet != Material.WATER && atFeet != Material.LAVA
                        && atHead != Material.WATER && atHead != Material.LAVA) {
                    return feet;
                }
            }
        }
        // Fallback: posición del boss
        return base.clone();
    }

    private void summonCrimsonMinions() {
        World world = bossEntity.getWorld();
        Location bLoc = bossEntity.getLocation();
        int count = phase >= 3 ? 3 : 2;

        world.playSound(bLoc, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 2.0f, 0.5f);

        for (int i = 0; i < count; i++) {
            Location spawnLoc = findSafeLocation(bLoc, 4);

            world.spawnParticle(Particle.CRIMSON_SPORE, spawnLoc, 20, 0.5, 1, 0.5, 0);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, spawnLoc, 10, 0.3, 0.5, 0.3, 0.02);

            Zombie minion = world.spawn(spawnLoc, Zombie.class, z -> {
                z.setCustomName(SmallCaps.convert("§c§lEspectro Carmesí"));
                z.setCustomNameVisible(true);
                z.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).setBaseValue(60);
                z.setHealth(60);
                z.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(6);
                z.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.28);
                z.setBaby(false);
                z.setRemoveWhenFarAway(false);
                z.setPersistent(true);

                ItemStack mHelmet = new ItemStack(Material.LEATHER_HELMET);
                LeatherArmorMeta mm = (LeatherArmorMeta) mHelmet.getItemMeta();
                mm.setColor(Color.fromRGB(150, 20, 20));
                mHelmet.setItemMeta(mm);
                z.getEquipment().setHelmet(mHelmet);
                z.getEquipment().setHelmetDropChance(0);
                z.getEquipment().setItemInMainHand(new ItemStack(Material.CRIMSON_FUNGUS));
                z.getEquipment().setItemInMainHandDropChance(0);

                z.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 99999, 0, false, false));
                z.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 99999, 0, false, false));
            });
            summonedMobs.add(minion);

            // Auto-remove after 30s
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!minion.isDead()) {
                    world.spawnParticle(Particle.SMOKE, minion.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.03);
                    minion.remove();
                }
            }, 600L);
        }
    }

    // ==================== HABILIDADES FASE 2 ====================

    private void sporeRain() {
        World world = bossEntity.getWorld();
        Location bLoc = bossEntity.getLocation();

        world.playSound(bLoc, Sound.BLOCK_SCULK_SPREAD, 2.0f, 0.5f);

        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (tick >= 40 || !active) { cancel(); return; }

                for (int i = 0; i < 3; i++) {
                    double x = bLoc.getX() + (Math.random() - 0.5) * 16;
                    double z = bLoc.getZ() + (Math.random() - 0.5) * 16;
                    Location rainLoc = new Location(world, x, bLoc.getY() + 10, z);

                    // Partículas cayendo
                    world.spawnParticle(Particle.DUST, rainLoc, 3, 0.2, 0.2, 0.2, 0,
                            new Particle.DustOptions(Color.fromRGB(100, 0, 130), 1.8f));
                    world.spawnParticle(Particle.CRIMSON_SPORE, rainLoc, 5, 0.5, 3, 0.5, 0);

                    // Bloque fantasma al impactar
                    Location groundLoc = new Location(world, x, bLoc.getY(), z);
                    for (Player p : world.getPlayers()) {
                        p.sendBlockChange(groundLoc, Material.NETHER_WART_BLOCK.createBlockData());
                    }
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        for (Player p : world.getPlayers()) {
                            p.sendBlockChange(groundLoc, groundLoc.getBlock().getBlockData());
                        }
                    }, 40L);
                }

                // Daño a jugadores bajo la lluvia
                if (tick % 5 == 0) {
                    for (Player p : world.getPlayers()) {
                        if (p.getLocation().distance(bLoc) <= 10) {
                            p.damage(3.0, bossEntity);
                            p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 40, 0, false, false));
                        }
                    }
                }

                tick++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void witherBreath() {
        if (bossEntity.getTarget() == null) return;
        World world = bossEntity.getWorld();
        Location bLoc = bossEntity.getLocation().add(0, 1.5, 0);
        Vector direction = bossEntity.getTarget().getLocation().toVector().subtract(bLoc.toVector()).normalize();

        world.playSound(bLoc, Sound.ENTITY_WITHER_SHOOT, 2.0f, 0.5f);

        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (tick >= 20 || !active) { cancel(); return; }

                for (int i = 0; i < 3; i++) {
                    double dist = tick * 0.8 + i * 0.3;
                    double spread = dist * 0.3;
                    Location particleLoc = bLoc.clone().add(
                            direction.getX() * dist + (Math.random() - 0.5) * spread,
                            direction.getY() * dist + (Math.random() - 0.5) * spread * 0.5,
                            direction.getZ() * dist + (Math.random() - 0.5) * spread
                    );
                    world.spawnParticle(Particle.DUST, particleLoc, 2, 0.1, 0.1, 0.1, 0,
                            new Particle.DustOptions(Color.fromRGB(30, 0, 30), 1.5f));
                    world.spawnParticle(Particle.SMOKE, particleLoc, 1, 0.1, 0.1, 0.1, 0.01);
                }

                // Daño
                double range = tick * 0.8;
                for (Player p : world.getPlayers()) {
                    Location pLoc = p.getLocation().add(0, 1, 0);
                    Vector toPlayer = pLoc.toVector().subtract(bLoc.toVector());
                    double dot = toPlayer.normalize().dot(direction);
                    if (dot > 0.6 && toPlayer.length() <= range + 2 && toPlayer.length() >= range - 2) {
                        p.damage(5.0, bossEntity);
                        p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 1, false, false));
                    }
                }

                tick++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void crimsonTendrils() {
        World world = bossEntity.getWorld();
        Location bLoc = bossEntity.getLocation();

        world.playSound(bLoc, Sound.ENTITY_VEX_CHARGE, 2.0f, 0.3f);

        // Zarcillos que atraen jugadores hacia el boss
        for (Player p : world.getPlayers()) {
            double dist = p.getLocation().distance(bLoc);
            if (dist > 20 || dist < 3) continue;

            // Línea de partículas del boss al jugador
            Vector dir = p.getLocation().toVector().subtract(bLoc.toVector()).normalize();
            for (double d = 0; d < dist; d += 0.5) {
                Location lineLoc = bLoc.clone().add(dir.clone().multiply(d)).add(0, 1, 0);
                world.spawnParticle(Particle.DUST, lineLoc, 1, 0.1, 0.1, 0.1, 0,
                        new Particle.DustOptions(Color.fromRGB(200, 30, 30), 1.0f));
            }

            // Atraer al jugador
            Vector pull = bLoc.toVector().subtract(p.getLocation().toVector()).normalize().multiply(0.8);
            pull.setY(0.2);
            p.setVelocity(pull);
            p.sendMessage(SmallCaps.convert("§4§l✦ §c¡Los zarcillos carmesí te atraen hacia Vorgath!"));
            p.playSound(p.getLocation(), Sound.BLOCK_VINE_BREAK, 1.0f, 0.5f);
        }
    }

    // ==================== HABILIDADES FASE 3 ====================

    private void bloodMoon() {
        World world = bossEntity.getWorld();
        Location bLoc = bossEntity.getLocation();

        world.playSound(bLoc, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 2.0f, 0.3f);

        for (Player p : world.getPlayers()) {
            p.sendTitle( SmallCaps.convert("§4§l☽ LUNA SANGRIENTA ☾"), "", 5, 30, 10);
            p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false));
        }

        // Pulso de daño AoE
        new BukkitRunnable() {
            int pulse = 0;
            @Override
            public void run() {
                if (pulse >= 3 || !active) { cancel(); return; }

                Location loc = bossEntity.getLocation();
                double radius = 5 + pulse * 3;

                // Anillo expandiéndose
                for (int i = 0; i < 40; i++) {
                    double angle = (Math.PI * 2.0 / 40) * i;
                    Location ringLoc = loc.clone().add(Math.cos(angle) * radius, 0.5, Math.sin(angle) * radius);
                    world.spawnParticle(Particle.DUST, ringLoc, 2, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(180, 0, 0), 2.0f));
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, ringLoc, 1, 0, 0.2, 0, 0.01);
                }

                // Daño en el anillo
                for (Player p : world.getPlayers()) {
                    double dist = p.getLocation().distance(loc);
                    if (dist <= radius + 1 && dist >= radius - 2) {
                        p.damage(6.0, bossEntity);
                    }
                }

                world.playSound(loc, Sound.ENTITY_WITHER_HURT, 1.0f, 0.3f);
                pulse++;
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void soulHarvest() {
        World world = bossEntity.getWorld();
        Location bLoc = bossEntity.getLocation();

        world.playSound(bLoc, Sound.ENTITY_WARDEN_HEARTBEAT, 2.0f, 0.5f);

        // Drena vida de todos los jugadores cercanos
        double totalDrained = 0;
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distance(bLoc) > 12) continue;

            double drain = 4.0;
            p.damage(drain, bossEntity);
            totalDrained += drain;

            // Partícula de alma del jugador al boss
            Vector dir = bLoc.toVector().subtract(p.getLocation().toVector()).normalize();
            for (double d = 0; d < p.getLocation().distance(bLoc); d += 0.5) {
                Location soulLoc = p.getLocation().clone().add(0, 1, 0).add(dir.clone().multiply(d));
                world.spawnParticle(Particle.SOUL, soulLoc, 1, 0.1, 0.1, 0.1, 0);
            }

            p.playSound(p.getLocation(), Sound.ENTITY_VEX_HURT, 0.5f, 0.3f);
        }

        // Boss ya no se cura (deshabilitado)
        // if (totalDrained > 0 && bossEntity.getHealth() < MAX_HEALTH) {
        //     double newHp = Math.min(MAX_HEALTH, bossEntity.getHealth() + totalDrained * 2);
        //     bossEntity.setHealth(newHp);
        // 
        //     world.spawnParticle(Particle.DUST, bLoc.clone().add(0, 2, 0), 30, 1, 1, 1, 0,
        //             new Particle.DustOptions(Color.fromRGB(0, 200, 0), 1.5f));
        // }
    }

    private void summonElite() {
        World world = bossEntity.getWorld();
        Location bLoc = bossEntity.getLocation();

        world.playSound(bLoc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.8f);

        Location spawnLoc = findSafeLocation(bLoc, 5);
        world.spawnParticle(Particle.EXPLOSION, spawnLoc.clone().add(0, 1, 0), 2, 0.5, 0.5, 0.5, 0);
        world.spawnParticle(Particle.DUST, spawnLoc.clone().add(0, 1, 0), 30, 1, 1, 1, 0,
                new Particle.DustOptions(Color.fromRGB(100, 0, 0), 2.5f));

        Zombie elite = world.spawn(spawnLoc, Zombie.class, z -> {
            z.setCustomName(SmallCaps.convert("§4§l✦ Bruto Carmesí ✦"));
            z.setCustomNameVisible(true);
            z.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).setBaseValue(150);
            z.setHealth(150);
            z.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(10);
            z.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.24);
            z.getAttribute(org.bukkit.attribute.Attribute.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(0.6);
            z.setBaby(false);
            z.setRemoveWhenFarAway(false);
            z.setPersistent(true);

            ItemStack eHelmet = new ItemStack(Material.NETHERITE_HELMET);
            z.getEquipment().setHelmet(eHelmet);
            z.getEquipment().setHelmetDropChance(0);
            z.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
            z.getEquipment().setItemInMainHandDropChance(0);

            z.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 99999, 0, false, false));
            z.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 99999, 0, false, false));
            z.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 99999, 0, false, false));
        });
        summonedMobs.add(elite);

        // Auto-remove after 45s
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!elite.isDead()) {
                world.spawnParticle(Particle.SOUL, elite.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.03);
                elite.remove();
            }
        }, 900L);
    }

    // ==================== NUEVAS HABILIDADES ====================

    /**
     * Inferno Wall: Crea un muro de fuego que avanza hacia los jugadores.
     */
    private void infernoWall() {
        if (bossEntity.getTarget() == null) return;
        World world = bossEntity.getWorld();
        Location bLoc = bossEntity.getLocation();
        Vector dir = bossEntity.getTarget().getLocation().toVector().subtract(bLoc.toVector()).normalize();
        // Dirección perpendicular para el muro
        Vector perp = new Vector(-dir.getZ(), 0, dir.getX());

        world.playSound(bLoc, Sound.ENTITY_BLAZE_AMBIENT, 2.0f, 0.3f);

        for (Player p : world.getPlayers()) {
            if (p.getLocation().distance(bLoc) <= 25) {
                p.sendMessage(SmallCaps.convert("§4§l✦ §c¡Vorgath invoca un Muro Infernal!"));
            }
        }

        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (tick >= 15 || !active) { cancel(); return; }

                double advance = tick * 1.2;
                // Dibujar muro de 10 bloques de ancho
                for (double w = -5; w <= 5; w += 0.5) {
                    Location wallLoc = bLoc.clone().add(
                            dir.getX() * advance + perp.getX() * w,
                            0.5,
                            dir.getZ() * advance + perp.getZ() * w
                    );
                    world.spawnParticle(Particle.FLAME, wallLoc, 2, 0.1, 0.5, 0.1, 0.02);
                    world.spawnParticle(Particle.DUST, wallLoc.clone().add(0, 0.5, 0), 1, 0.05, 0.3, 0.05, 0,
                            new Particle.DustOptions(Color.fromRGB(255, 100, 0), 1.5f));
                }

                // Daño a jugadores en la línea del muro
                for (Player p : world.getPlayers()) {
                    Location pLoc = p.getLocation();
                    Vector toPlayer = pLoc.toVector().subtract(bLoc.toVector());
                    double projForward = toPlayer.dot(dir);
                    double projSide = Math.abs(toPlayer.dot(perp));
                    if (Math.abs(projForward - advance) < 1.5 && projSide < 6) {
                        p.damage(7.0, bossEntity);
                        p.setFireTicks(60);
                        Vector push = dir.clone().multiply(0.8).setY(0.4);
                        p.setVelocity(push);
                    }
                }

                tick++;
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    /**
     * Nether Geyser: Géiseres de lava brotan del suelo en posiciones aleatorias.
     */
    private void netherGeyser() {
        World world = bossEntity.getWorld();
        Location bLoc = bossEntity.getLocation();

        world.playSound(bLoc, Sound.BLOCK_LAVA_POP, 2.0f, 0.5f);

        // 4-6 géiseres en posiciones aleatorias
        int geysers = 4 + (int)(Math.random() * 3);
        for (int g = 0; g < geysers; g++) {
            double angle = Math.random() * Math.PI * 2;
            double dist = 3 + Math.random() * 8;
            Location geyserLoc = bLoc.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);

            // Indicador previo
            for (int i = 0; i < 12; i++) {
                double a = (Math.PI * 2.0 / 12) * i;
                Location ind = geyserLoc.clone().add(Math.cos(a) * 1.0, 0.1, Math.sin(a) * 1.0);
                world.spawnParticle(Particle.DUST, ind, 1, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 60, 0), 1.0f));
            }

            // Erupción retrasada
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!active) return;
                // Columna de lava
                for (double y = 0; y < 6; y += 0.4) {
                    world.spawnParticle(Particle.LAVA, geyserLoc.clone().add(0, y, 0), 3, 0.3, 0.1, 0.3, 0);
                    world.spawnParticle(Particle.DUST, geyserLoc.clone().add(0, y, 0), 2, 0.4, 0.1, 0.4, 0,
                            new Particle.DustOptions(Color.fromRGB(255, 80, 0), 1.8f));
                }
                world.spawnParticle(Particle.FLAME, geyserLoc.clone().add(0, 3, 0), 15, 0.5, 1, 0.5, 0.05);
                world.playSound(geyserLoc, Sound.BLOCK_LAVA_EXTINGUISH, 1.5f, 0.5f);

                for (Player p : world.getPlayers()) {
                    if (p.getLocation().distance(geyserLoc) <= 2.5) {
                        p.damage(6.0, bossEntity);
                        p.setFireTicks(40);
                        p.setVelocity(new Vector(0, 0.8, 0));
                    }
                }
            }, 25L);
        }
    }

    /**
     * Phantom Charge: Vorgath se teletransporta detrás de un jugador y golpea.
     */
    private void phantomCharge() {
        World world = bossEntity.getWorld();
        List<Player> targets = new ArrayList<>();
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distance(bossEntity.getLocation()) <= 20) {
                targets.add(p);
            }
        }
        if (targets.isEmpty()) return;

        Player target = targets.get((int)(Math.random() * targets.size()));
        Location bLoc = bossEntity.getLocation();

        // Efecto de desvanecimiento
        world.spawnParticle(Particle.DUST, bLoc.clone().add(0, 1, 0), 30, 0.5, 1, 0.5, 0,
                new Particle.DustOptions(Color.fromRGB(80, 0, 0), 2.0f));
        world.spawnParticle(Particle.SMOKE, bLoc.clone().add(0, 1, 0), 20, 0.5, 0.8, 0.5, 0.03);
        world.playSound(bLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 2.0f, 0.3f);

        // Mensaje de warning
        target.sendMessage(SmallCaps.convert("§4§l✦ §c¡Vorgath aparece detrás de ti!"));
        target.playSound(target.getLocation(), Sound.ENTITY_WARDEN_NEARBY_CLOSER, 1.0f, 0.5f);

        // Teletransportar detrás del jugador
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || bossEntity == null || bossEntity.isDead()) return;

            Vector behind = target.getLocation().getDirection().multiply(-2);
            Location behindLoc = target.getLocation().add(behind);
            behindLoc.setY(target.getLocation().getY());
            Location safeLoc = findSafeLocation(behindLoc, 2);

            bossEntity.teleport(safeLoc);

            // Efecto de aparición
            world.spawnParticle(Particle.DUST, safeLoc.clone().add(0, 1, 0), 25, 0.5, 1, 0.5, 0,
                    new Particle.DustOptions(Color.fromRGB(200, 0, 0), 2.5f));
            world.spawnParticle(Particle.FLAME, safeLoc.clone().add(0, 0.5, 0), 15, 0.5, 0.5, 0.5, 0.03);
            world.playSound(safeLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 2.0f, 0.5f);

            // Golpe fuerte
            target.damage(10.0, bossEntity);
            Vector push = target.getLocation().toVector().subtract(safeLoc.toVector()).normalize().multiply(1.0).setY(0.3);
            target.setVelocity(push);
        }, 10L);
    }

    /**
     * Crimson Meteor: Lluvias de meteoritos carmesí caen sobre la arena.
     */
    private void crimsonMeteor() {
        World world = bossEntity.getWorld();
        Location bLoc = bossEntity.getLocation();

        world.playSound(bLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 0.3f);

        for (Player p : world.getPlayers()) {
            if (p.getLocation().distance(bLoc) <= 25) {
                p.sendTitle("", SmallCaps.convert("§4¡Meteoritos Carmesí!"), 5, 20, 5);
            }
        }

        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (tick >= 30 || !active) { cancel(); return; }

                if (tick % 3 == 0) {
                    // 2 meteoritos por oleada
                    for (int m = 0; m < 2; m++) {
                        double x = bLoc.getX() + (Math.random() - 0.5) * 20;
                        double z = bLoc.getZ() + (Math.random() - 0.5) * 20;
                        Location impactLoc = new Location(world, x, bLoc.getY(), z);

                        // Estela del meteorito cayendo
                        for (double y = 8; y >= 0; y -= 0.5) {
                            Location trail = impactLoc.clone().add((8 - y) * 0.3, y, (8 - y) * 0.2);
                            world.spawnParticle(Particle.DUST, trail, 1, 0.1, 0.1, 0.1, 0,
                                    new Particle.DustOptions(Color.fromRGB(255, 50 + (int)(y * 15), 0), 1.5f));
                            world.spawnParticle(Particle.FLAME, trail, 1, 0.1, 0.1, 0.1, 0.01);
                        }

                        // Impacto
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (!active) return;
                            world.spawnParticle(Particle.EXPLOSION, impactLoc, 1, 0.5, 0.5, 0.5, 0);
                            world.spawnParticle(Particle.DUST, impactLoc, 15, 1.5, 0.5, 1.5, 0,
                                    new Particle.DustOptions(Color.fromRGB(200, 30, 0), 2.0f));
                            world.spawnParticle(Particle.LAVA, impactLoc, 5, 0.5, 0.2, 0.5, 0);
                            world.playSound(impactLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.6f);

                            for (Player p : world.getPlayers()) {
                                if (p.getLocation().distance(impactLoc) <= 3.0) {
                                    p.damage(8.0, bossEntity);
                                    p.setFireTicks(60);
                                }
                            }
                        }, 8L);
                    }
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    /**
     * Void Rift: Abre un portal oscuro que succiona jugadores y los daña.
     */
    private void voidRift() {
        World world = bossEntity.getWorld();
        Location bLoc = bossEntity.getLocation();

        // Posición del rift: aleatoria cerca del centro
        double rx = bLoc.getX() + (Math.random() - 0.5) * 10;
        double rz = bLoc.getZ() + (Math.random() - 0.5) * 10;
        Location riftLoc = new Location(world, rx, bLoc.getY() + 3, rz);

        world.playSound(riftLoc, Sound.BLOCK_PORTAL_AMBIENT, 2.0f, 0.3f);
        world.playSound(riftLoc, Sound.ENTITY_WARDEN_EMERGE, 1.0f, 0.3f);

        for (Player p : world.getPlayers()) {
            if (p.getLocation().distance(riftLoc) <= 20) {
                p.sendMessage(SmallCaps.convert("§0§l✦ §8¡Una fisura del vacío se abre!"));
            }
        }

        new BukkitRunnable() {
            int tick = 0;
            double riftRadius = 0.5;
            @Override
            public void run() {
                if (tick >= 60 || !active) {
                    cancel();
                    // Efecto de cierre
                    world.spawnParticle(Particle.EXPLOSION, riftLoc, 2, 0.5, 0.5, 0.5, 0);
                    world.playSound(riftLoc, Sound.BLOCK_PORTAL_TRAVEL, 0.5f, 2.0f);
                    return;
                }

                // Expandir el rift
                if (tick < 20) riftRadius = 0.5 + tick * 0.15;

                // Partículas del portal oscuro (espiral)
                for (int i = 0; i < 15; i++) {
                    double angle = (Math.PI * 2.0 / 15) * i + tick * 0.2;
                    double r = riftRadius * (0.5 + Math.random() * 0.5);
                    Location particleLoc = riftLoc.clone().add(
                            Math.cos(angle) * r,
                            (Math.random() - 0.5) * 0.8,
                            Math.sin(angle) * r
                    );
                    world.spawnParticle(Particle.DUST, particleLoc, 1, 0.05, 0.05, 0.05, 0,
                            new Particle.DustOptions(Color.fromRGB(20, 0, 40), 1.8f));
                    world.spawnParticle(Particle.PORTAL, particleLoc, 1, 0.1, 0.1, 0.1, 0.5);
                }

                // Succionar y dañar cada segundo
                if (tick % 5 == 0) {
                    for (Player p : world.getPlayers()) {
                        double dist = p.getLocation().distance(riftLoc);
                        if (dist <= 8) {
                            // Succionar hacia el rift
                            Vector pull = riftLoc.toVector().subtract(p.getLocation().toVector()).normalize();
                            double force = (8 - dist) / 8.0 * 0.6;
                            pull.multiply(force).setY(Math.max(pull.getY(), -0.1));
                            p.setVelocity(p.getVelocity().add(pull));

                            // Daño si está muy cerca
                            if (dist <= 3) {
                                p.damage(5.0, bossEntity);
                            }
                        }
                    }
                }

                // Sonido periódico
                if (tick % 10 == 0) {
                    world.playSound(riftLoc, Sound.BLOCK_PORTAL_AMBIENT, 1.0f, 0.3f + (float)(tick * 0.01));
                }

                tick++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    // ==================== AMBIENTALES ====================

    private void ambientParticles() {
        if (bossEntity == null || bossEntity.isDead()) return;
        Location loc = bossEntity.getLocation().add(0, 1.5, 0);
        World world = bossEntity.getWorld();

        // Aura carmesí constante
        world.spawnParticle(Particle.DUST, loc, 3, 0.5, 0.8, 0.5, 0,
                new Particle.DustOptions(Color.fromRGB(180 + (int)(Math.random() * 50), 10, 10), 1.2f));
        world.spawnParticle(Particle.CRIMSON_SPORE, loc, 2, 1, 1, 1, 0);

        // Partículas según fase
        switch (phase) {
            case 1:
                if (tickCount % 4 == 0) {
                    world.spawnParticle(Particle.FLAME, loc, 1, 0.3, 0.5, 0.3, 0.01);
                }
                break;
            case 2:
                world.spawnParticle(Particle.DUST, loc, 2, 0.8, 1, 0.8, 0,
                        new Particle.DustOptions(Color.fromRGB(100, 0, 160), 1.0f));
                if (tickCount % 4 == 0) {
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 1, 0.4, 0.6, 0.4, 0.01);
                }
                break;
            case 3:
                world.spawnParticle(Particle.DUST, loc, 4, 0.6, 1, 0.6, 0,
                        new Particle.DustOptions(Color.fromRGB(50, 0, 0), 1.5f));
                world.spawnParticle(Particle.SOUL, loc, 1, 0.5, 0.8, 0.5, 0.02);
                if (tickCount % 2 == 0) {
                    world.spawnParticle(Particle.FLAME, loc, 2, 0.3, 0.5, 0.3, 0.02);
                }
                break;
        }
    }

    // Ticks que lleva sin poder alcanzar al target (para teleport de emergencia)
    private int unreachableTicks = 0;

    private void retargetNearest() {
        if (bossEntity == null || bossEntity.isDead()) return;
        Player nearest = null;
        double minDist = Double.MAX_VALUE;
        for (Player p : bossEntity.getWorld().getPlayers()) {
            double dist = p.getLocation().distance(bossEntity.getLocation());
            if (dist < minDist) {
                minDist = dist;
                nearest = p;
            }
        }
        if (nearest == null) return;
        bossEntity.setTarget(nearest);

        Location bossLoc = bossEntity.getLocation();
        Location targetLoc = nearest.getLocation();
        double dy = targetLoc.getY() - bossLoc.getY();
        double horizontalDist = Math.sqrt(Math.pow(targetLoc.getX() - bossLoc.getX(), 2)
                + Math.pow(targetLoc.getZ() - bossLoc.getZ(), 2));

        // Si el jugador está 3+ bloques por encima
        if (dy > 3.0) {
            unreachableTicks++;

            // Cada 2s (8 ticks del loop @5t): salto hacia el jugador
            if (unreachableTicks % 8 == 1) {
                Vector direction = targetLoc.toVector().subtract(bossLoc.toVector()).normalize();
                double launchY = Math.min(1.2, dy * 0.15);
                double launchH = Math.min(0.8, horizontalDist * 0.1);
                bossEntity.setVelocity(new Vector(direction.getX() * launchH, launchY, direction.getZ() * launchH));

                // Efecto de salto
                World world = bossEntity.getWorld();
                world.spawnParticle(Particle.FLAME, bossLoc.clone().add(0, 0.5, 0), 15, 0.3, 0.1, 0.3, 0.05);
                world.spawnParticle(Particle.DUST, bossLoc.clone().add(0, 0.5, 0), 8, 0.3, 0.1, 0.3, 0,
                        new Particle.DustOptions(Color.fromRGB(200, 30, 0), 1.5f));
                world.playSound(bossLoc, Sound.ENTITY_RAVAGER_ATTACK, 0.8f, 0.5f);
            }

            // Si lleva 10+ segundos sin alcanzarlo (~40 ticks del loop): teleport
            if (unreachableTicks >= 40) {
                Location tpLoc = targetLoc.clone().add(0, -1, 0);
                bossEntity.teleport(tpLoc);
                unreachableTicks = 0;

                World world = bossEntity.getWorld();
                world.spawnParticle(Particle.SMOKE, tpLoc.clone().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.05);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, tpLoc.clone().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0.03);
                world.playSound(tpLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 0.5f);

                for (Player p : world.getPlayers()) {
                    if (p.getLocation().distanceSquared(tpLoc) < 400) {
                        p.sendMessage(SmallCaps.convert("§4§l⚔ §c¡Vorgath no puede ser esquivado tan fácilmente!"));
                    }
                }
            }
        } else {
            unreachableTicks = 0;
        }
    }

    private void updateBossBar(double hpPercent) {
        if (bossBar == null) return;
        double progress = Math.max(0, Math.min(1, hpPercent));
        bossBar.setProgress(progress);

        // Calcular tiempo restante
        long elapsed = System.currentTimeMillis() - spawnTimestamp;
        long remaining = BOSS_TIMEOUT_MS - elapsed;
        String timeStr = formatTime(remaining);
        String timeColor = remaining < 300_000 ? "§c" : remaining < 600_000 ? "§e" : "§a"; // rojo <5min, amarillo <10min

        // Titulo de la BossBar de arena con timer
        String phaseText = phase == 1 ? "§cFase 1: Furia Carmesí" : phase == 2 ? "§5Fase 2: Tormenta de Esporas" : "§0Fase 3: Ira del Vacío";
        int hpPct = (int)(hpPercent * 100);
        bossBar.setTitle(SmallCaps.convert("§4§l⚔ Vorgath §8- " + phaseText + " §8| " + timeColor + "⏱ " + timeStr));

        // BossBar detallada para jugadores en la arena
        World arenaWorld = bossEntity.getWorld();
        for (Player p : arenaWorld.getPlayers()) {
            if (!bossBar.getPlayers().contains(p)) bossBar.addPlayer(p);
            // Quitar de la global si está en la arena
            if (globalBossBar != null) globalBossBar.removePlayer(p);
        }

        // BossBar global para jugadores en otros mundos
        if (globalBossBar == null) {
            org.bukkit.NamespacedKey vorgathGlobalKey = new org.bukkit.NamespacedKey(plugin, "boss_vorgath_global");
            Bukkit.removeBossBar(vorgathGlobalKey);
            globalBossBar = Bukkit.createBossBar(vorgathGlobalKey,
                    "§4§l⚔ VORGATH §8| §c¡Usa /boss para unirte!",
                    BarColor.RED, BarStyle.SOLID);
            globalBossBar.setVisible(true);
        }

        String phaseShort = phase == 1 ? "§cFase 1" : phase == 2 ? "§5Fase 2" : "§0Fase 3";
        globalBossBar.setTitle(SmallCaps.convert("§4§l⚔ VORGATH §8| " + phaseShort + " §8| §fHP: §c" + hpPct + "% §8| " + timeColor + "⏱ " + timeStr + " §8| §e/boss"));
        globalBossBar.setProgress(progress);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().equals(arenaWorld)) continue;
            if (!globalBossBar.getPlayers().contains(p)) globalBossBar.addPlayer(p);
        }

        // Limpiar jugadores offline de ambas barras
        for (Player p : new java.util.ArrayList<>(bossBar.getPlayers())) {
            if (!p.isOnline() || !p.getWorld().equals(arenaWorld)) bossBar.removePlayer(p);
        }
        for (Player p : new java.util.ArrayList<>(globalBossBar.getPlayers())) {
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

    // ==================== DAÑO Y MUERTE ====================

    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            lastHitter = (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile) {
            Projectile proj = (Projectile) event.getDamager();
            if (proj.getShooter() instanceof Player) {
                lastHitter = (Player) proj.getShooter();
            }
        }
    }

    /**
     * Timeout: el boss no fue derrotado en 45 minutos.
     */
    private void onTimeout() {
        active = false;
        World world = arena.getBossSpawn().getWorld();
        Location loc = bossEntity != null ? bossEntity.getLocation() : arena.getBossSpawn();

        // Efecto de desvanecimiento
        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (tick >= 60) {
                    // Eliminar boss
                    if (bossEntity != null && !bossEntity.isDead()) bossEntity.remove();
                    cancel();
                    return;
                }

                // Espirales de humo ascendente
                double angle = tick * 0.4;
                double r = 2.0 - tick * 0.03;
                Location spiral = loc.clone().add(Math.cos(angle) * r, tick * 0.08, Math.sin(angle) * r);
                world.spawnParticle(Particle.SMOKE, spiral, 5, 0.1, 0.1, 0.1, 0.02);
                world.spawnParticle(Particle.DUST, spiral, 3, 0.1, 0.1, 0.1, 0,
                        new Particle.DustOptions(Color.fromRGB(80, 0, 0), 2.0f));

                if (tick == 0) {
                    world.playSound(loc, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 2.0f, 0.3f);
                    if (bossEntity != null) {
                        bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 10, false, false));
                        bossEntity.setAI(false);
                    }
                }
                if (tick == 30) world.playSound(loc, Sound.BLOCK_PORTAL_TRAVEL, 1.5f, 0.3f);
                if (tick == 55) {
                    world.spawnParticle(Particle.DUST, loc.clone().add(0, 2, 0), 60, 3, 3, 3, 0,
                            new Particle.DustOptions(Color.fromRGB(30, 0, 30), 3.0f));
                    world.spawnParticle(Particle.SMOKE, loc.clone().add(0, 2, 0), 40, 2, 2, 2, 0.05);
                    world.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 2.0f, 0.3f);
                }

                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Limpiar todo
        cleanupBossBars();
        cleanupSummons(world);

        // Mensajes
        for (Player p : world.getPlayers()) {
            p.sendTitle( SmallCaps.convert("§4§l⏱ TIEMPO AGOTADO"), SmallCaps.convert("§7Vorgath se ha retirado al Bosque Carmesí..."), 5, 60, 20);
        }

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(SmallCaps.convert("§4§l⏱ ═══════════════════════════════ ⏱"));
        Bukkit.broadcastMessage(SmallCaps.convert("§c§l    ¡EL TIEMPO SE HA AGOTADO!"));
        Bukkit.broadcastMessage(SmallCaps.convert("§7  Nadie pudo derrotar a §4§lVorgath§7."));
        Bukkit.broadcastMessage(SmallCaps.convert("§7  Se ha retirado al §4Bosque Carmesí§7..."));
        Bukkit.broadcastMessage(SmallCaps.convert("§4§l⏱ ═══════════════════════════════ ⏱"));
        Bukkit.broadcastMessage("");

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.5f, 0.5f);
        }

        manager.onBossDeath(null);
    }

    /**
     * Muerte épica con animación completa.
     */
    private void onDeath() {
        active = false;
        World world = arena.getBossSpawn().getWorld();
        Location deathLoc = bossEntity != null ? bossEntity.getLocation() : arena.getBossSpawn();

        // Congelar boss si aún existe
        if (bossEntity != null && !bossEntity.isDead()) {
            bossEntity.setAI(false);
            bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 255, false, false));
            bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0, false, false));
        }

        // Animación de muerte épica en fases
        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (tick >= 120) {
                    // Eliminar boss al final de la animación
                    if (bossEntity != null && !bossEntity.isDead()) bossEntity.remove();
                    cancel();

                    // Dar recompensas después de la animación
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

                // === FASE 1 (tick 0-30): Grietas de energía ===
                if (tick < 30) {
                    // Grietas carmesí brotando del boss
                    for (int i = 0; i < 3; i++) {
                        double angle = Math.random() * Math.PI * 2;
                        double dist = Math.random() * (tick * 0.1);
                        Location crack = deathLoc.clone().add(Math.cos(angle) * dist, Math.random() * 2, Math.sin(angle) * dist);
                        world.spawnParticle(Particle.DUST, crack, 2, 0.05, 0.05, 0.05, 0,
                                new Particle.DustOptions(Color.fromRGB(255, 50 + (int)(Math.random() * 50), 0), 1.5f));
                    }
                    if (tick % 5 == 0) {
                        world.playSound(deathLoc, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.5f, 0.5f + tick * 0.02f);
                    }
                    if (tick == 0) {
                        for (Player p : world.getPlayers()) {
                            p.sendTitle("", SmallCaps.convert("§c§l\"No... esto no puede ser...\""), 0, 40, 10);
                        }
                    }
                }

                // === FASE 2 (tick 30-70): Columna de almas ascendente ===
                if (tick >= 30 && tick < 70) {
                    int t = tick - 30;
                    double height = t * 0.15;

                    // Columna de almas
                    for (double y = 0; y < height; y += 0.5) {
                        double spiralAngle = y * 3 + t * 0.2;
                        double spiralR = 1.5 - y * 0.03;
                        Location soulLoc = deathLoc.clone().add(
                                Math.cos(spiralAngle) * spiralR, y, Math.sin(spiralAngle) * spiralR);
                        world.spawnParticle(Particle.SOUL, soulLoc, 1, 0.05, 0.05, 0.05, 0.01);
                        world.spawnParticle(Particle.SOUL_FIRE_FLAME, soulLoc, 1, 0.05, 0.05, 0.05, 0);
                    }

                    // Anillo de fuego en el suelo expandiéndose
                    if (t % 3 == 0) {
                        double ringR = 1 + t * 0.1;
                        for (int i = 0; i < 24; i++) {
                            double a = (Math.PI * 2.0 / 24) * i;
                            Location ring = deathLoc.clone().add(Math.cos(a) * ringR, 0.1, Math.sin(a) * ringR);
                            world.spawnParticle(Particle.FLAME, ring, 1, 0, 0, 0, 0.01);
                        }
                    }

                    if (t % 10 == 0) {
                        world.playSound(deathLoc, Sound.ENTITY_BLAZE_DEATH, 1.5f, 0.3f + t * 0.01f);
                    }
                    if (tick == 45) {
                        for (Player p : world.getPlayers()) {
                            p.sendTitle("", SmallCaps.convert("§4§l\"¡VOLVERÉ... MÁS FUERTE!\""), 0, 40, 10);
                        }
                    }
                }

                // === FASE 3 (tick 70-100): Implosión ===
                if (tick >= 70 && tick < 100) {
                    int t = tick - 70;
                    // Partículas siendo succionadas hacia el centro
                    for (int i = 0; i < 8; i++) {
                        double angle = Math.random() * Math.PI * 2;
                        double dist = 5.0 - t * 0.15;
                        if (dist < 0.5) dist = 0.5;
                        Location from = deathLoc.clone().add(Math.cos(angle) * dist, 1 + Math.random() * 3, Math.sin(angle) * dist);
                        world.spawnParticle(Particle.DUST, from, 1, 0, 0, 0, 0,
                                new Particle.DustOptions(Color.fromRGB(150, 0, 0), 2.0f));
                        world.spawnParticle(Particle.ENCHANT, from, 2, 0, 0, 0, 1.0);
                    }

                    if (t % 4 == 0) {
                        world.playSound(deathLoc, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.3f + t * 0.03f);
                    }
                }

                // === FASE 4 (tick 100-120): Gran explosión final ===
                if (tick == 100) {
                    // BOOM
                    world.spawnParticle(Particle.EXPLOSION_EMITTER, deathLoc.clone().add(0, 2, 0), 3, 0.5, 0.5, 0.5, 0);
                    world.spawnParticle(Particle.EXPLOSION, deathLoc.clone().add(0, 2, 0), 15, 3, 3, 3, 0);
                    world.spawnParticle(Particle.DUST, deathLoc.clone().add(0, 2, 0), 200, 6, 4, 6, 0,
                            new Particle.DustOptions(Color.fromRGB(255, 30, 0), 4.0f));
                    world.spawnParticle(Particle.FLAME, deathLoc.clone().add(0, 2, 0), 100, 4, 3, 4, 0.15);
                    world.spawnParticle(Particle.SOUL, deathLoc.clone().add(0, 2, 0), 80, 5, 5, 5, 0.1);
                    world.spawnParticle(Particle.LAVA, deathLoc.clone().add(0, 2, 0), 60, 4, 3, 4, 0);

                    world.playSound(deathLoc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.3f);
                    world.playSound(deathLoc, Sound.ENTITY_WITHER_DEATH, 2.0f, 0.3f);
                    world.playSound(deathLoc, Sound.ENTITY_ENDER_DRAGON_DEATH, 2.0f, 0.5f);
                    world.playSound(deathLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 0.5f);

                    // Knockback a todos los jugadores cercanos
                    for (Player p : world.getPlayers()) {
                        if (p.getLocation().distanceSquared(deathLoc) < 100) { // 10 bloques
                            Vector kb = p.getLocation().toVector().subtract(deathLoc.toVector()).normalize().multiply(1.5);
                            kb.setY(0.6);
                            p.setVelocity(kb);
                        }
                    }

                    for (Player p : world.getPlayers()) {
                        p.sendTitle("§4§l⚔ VORGATH HA CAÍDO ⚔",
                                "§7Último golpe: §e§l" + (lastHitter != null ? lastHitter.getName() : "N/A"), 5, 80, 30);
                    }
                }

                // Lluvia de partículas post-explosión
                if (tick > 100 && tick < 120) {
                    int t = tick - 100;
                    for (int i = 0; i < 5; i++) {
                        Location fall = deathLoc.clone().add(
                                (Math.random() - 0.5) * 8, 5 + Math.random() * 3 - t * 0.3, (Math.random() - 0.5) * 8);
                        world.spawnParticle(Particle.FALLING_LAVA, fall, 1, 0, 0, 0, 0);
                        world.spawnParticle(Particle.DUST, fall, 1, 0, 0, 0, 0,
                                new Particle.DustOptions(Color.fromRGB(200, 30, 0), 1.5f));
                    }
                }

                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Limpiar BossBars y summons inmediatamente
        cleanupBossBars();
        cleanupSummons(world);

        // Anunciar a todos los jugadores del servidor
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(SmallCaps.convert("§4§l⚔ ═══════════════════════════════ ⚔"));
        Bukkit.broadcastMessage(SmallCaps.convert("§c§l    ¡VORGATH HA CAÍDO!"));
        Bukkit.broadcastMessage(SmallCaps.convert("§7  Último golpe: §e§l" + (lastHitter != null ? lastHitter.getName() : "N/A")));
        Bukkit.broadcastMessage(SmallCaps.convert("§4§l⚔ ═══════════════════════════════ ⚔"));
        Bukkit.broadcastMessage("");

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
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
                world.spawnParticle(Particle.SMOKE, e.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.03);
                e.remove();
            }
        }
        summonedMobs.clear();
    }

    private void giveRewards(Player player) {
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§4§l⚔ ═══════════════════════════ ⚔"));
        player.sendMessage(SmallCaps.convert("§c§l  ¡RECOMPENSA DEL ÚLTIMO GOLPE!"));
        player.sendMessage(SmallCaps.convert("§7  Has derrotado a §4§lVorgath§7."));
        player.sendMessage(SmallCaps.convert("§4§l⚔ ═══════════════════════════ ⚔"));
        player.sendMessage("");

        // Items de recompensa
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

        // Esencias reward
        if (plugin.getEssenceManager() != null) {
            plugin.getEssenceManager().onBossKill(player);
        }

        // Ejecutar comando de crate key
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "crates give " + player.getName() + " VorgathKey 3");
    }

    private void giveParticipationReward(Player player) {
        player.sendMessage(SmallCaps.convert("§7Has participado en la batalla contra §4§lVorgath§7."));
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

    // ==================== CLEANUP ====================

    public void cleanup() {
        active = false;
        if (mainLoop != null) mainLoop.cancel();
        if (bossEntity != null && !bossEntity.isDead()) bossEntity.remove();
        World world = arena.getBossSpawn().getWorld();
        if (world != null) cleanupSummons(world);
        cleanupBossBars();
    }

    // ==================== GETTERS ====================

    @Override public boolean isActive() { return active; }
    @Override public String getBossName() { return "Vorgath"; }
    @Override public BossType getBossType() { return BossType.VORGATH; }
    public Zombie getBossEntity() { return bossEntity; }
    @Override public Player getLastHitter() { return lastHitter; }

    public boolean isBossEntity(Entity entity) {
        return bossEntity != null && entity.getUniqueId().equals(bossEntity.getUniqueId());
    }

    public boolean isSummonedMob(Entity entity) {
        for (Entity e : summonedMobs) {
            if (e.getUniqueId().equals(entity.getUniqueId())) return true;
        }
        return false;
    }

    /**
     * Verifica cada segundo si el boss está bajo tierra o asfixiándose.
     * Si se detecta, lo teleporta a un lugar seguro inmediatamente.
     */
    private void checkAndFixBossLocation() {
        if (bossEntity == null || bossEntity.isDead()) return;
        
        Location bossLoc = bossEntity.getLocation();
        Location headLoc = bossLoc.clone().add(0, 1, 0);
        Location feetLoc = bossLoc.clone();
        
        // Verificar si la cabeza o los pies están dentro de un bloque sólido
        boolean headInSolid = headLoc.getBlock().getType().isSolid();
        boolean feetInSolid = feetLoc.getBlock().getType().isSolid();
        
        if (headInSolid || feetInSolid) {
            // Boss está bajo tierra o dentro de un bloque - EMERGENCIA
            plugin.getLogger().warning("[VorgathBoss] ¡EMERGENCIA! Boss detectado bajo tierra en Y=" + bossLoc.getBlockY());
            
            Location safeLoc = getSafeLocation(bossLoc);
            bossEntity.teleport(safeLoc);
            
            // Efectos visuales de teleport de emergencia
            World world = safeLoc.getWorld();
            world.spawnParticle(Particle.PORTAL, safeLoc, 100, 1, 1, 1, 0.5);
            world.spawnParticle(Particle.END_ROD, safeLoc, 3, 0, 0, 0, 0);
            world.playSound(safeLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 0.8f);
            
            plugin.getLogger().info("[VorgathBoss] Boss teleportado a ubicación segura Y=" + safeLoc.getBlockY());
        }
    }

    /**
     * Encuentra una ubicación segura para el boss, asegurándose de que no esté bajo tierra.
     * Busca hacia arriba hasta encontrar un bloque de aire con espacio suficiente.
     */
    private Location getSafeLocation(Location original) {
        World world = original.getWorld();
        Location checkLoc = original.clone();
        
        // Buscar hacia arriba hasta encontrar un lugar seguro (máximo 10 bloques)
        for (int i = 0; i < 10; i++) {
            Location above = checkLoc.clone().add(0, i, 0);
            Location twoAbove = checkLoc.clone().add(0, i + 1, 0);
            Location threeAbove = checkLoc.clone().add(0, i + 2, 0);
            
            // Verificar que hay 3 bloques de aire (espacio para el zombie)
            if (above.getBlock().getType().isAir() && 
                twoAbove.getBlock().getType().isAir() && 
                threeAbove.getBlock().getType().isAir()) {
                
                // Verificar que hay un bloque sólido debajo
                Location below = above.clone().subtract(0, 1, 0);
                if (below.getBlock().getType().isSolid()) {
                    plugin.getLogger().info("[VorgathBoss] Ubicación segura encontrada en Y=" + above.getBlockY());
                    return above;
                }
            }
        }
        
        // Si no se encuentra lugar seguro, usar la ubicación original pero ajustada
        plugin.getLogger().warning("[VorgathBoss] No se encontró ubicación segura, usando ubicación ajustada");
        Location fallback = original.clone();
        fallback.setY(world.getHighestBlockYAt(original) + 1);
        return fallback;
    }
}
