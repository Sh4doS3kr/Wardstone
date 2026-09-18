package com.moonlight.coreprotect.shadowhunter;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
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
 * Mini Boss final: "El Devorador de Sombras"
 * Ataques:
 * - Terremoto: sacude la pantalla, levanta bloques falsos
 * - Columnas de sombra: pilares de partículas que dañan
 * - Onda de choque: knockback + daño AOE
 * - Invocación: spawna sombras menores
 * - Fase de rabia cuando baja de 30% HP
 *
 * NO destruye bloques reales - usa FallingBlock para simular levantar el suelo.
 */
public class ShadowHunterBoss {

    private final CoreProtectPlugin plugin;
    private final ShadowHunterManager manager;
    private final Player player;
    private final ShadowHunterNPC npc;

    private Location arenaCenter;
    private static final double ARENA_RADIUS = 40.0;

    private WitherSkeleton bossEntity;
    private BossBar bossBar;
    private BukkitTask aiTask;
    private BukkitTask arenaTask;
    private BukkitTask barrierTask;
    private BukkitTask trackTask;

    private final List<Entity> summonedMobs = new ArrayList<>();
    private final List<FallingBlock> fallingBlocks = new ArrayList<>();

    private boolean active = false;
    private boolean enraged = false;
    private int attackCooldown = 0;
    private double bossMaxHealth = 350;
    private int freezeTicks = 0;

    public ShadowHunterBoss(CoreProtectPlugin plugin, ShadowHunterManager manager, Player player, ShadowHunterNPC npc) {
        this.plugin = plugin;
        this.manager = manager;
        this.player = player;
        this.npc = npc;
    }

    public void start() {
        active = true;
        arenaCenter = player.getLocation().clone();

        // Posicionar NPC
        Location npcLoc = arenaCenter.clone().add(ARENA_RADIUS - 2, 0, 0);
        npcLoc.setY(npcLoc.getWorld().getHighestBlockYAt(npcLoc) + 1);
        npc.moveToAndStay(npcLoc);

        // Diálogo intro
        npc.say("\"§4¡CUIDADO! El ritual ha despertado algo terrible...\"");
        npc.sayDelayed("\"§cEl Devorador de Sombras... ¡Prepárate para luchar!\"", 40L);

        // Spawn del boss con intro
        Bukkit.getScheduler().runTaskLater(plugin, this::spawnBoss, 80L);

        // Arena visual
        startArenaVisuals();
        startBarrier();

        // Tracker: detecta si el boss muere por cualquier causa
        startBossTracker();
    }

    private void startBossTracker() {
        trackTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!active) {
                    cancel();
                    return;
                }
                if (bossEntity != null && bossEntity.isDead()) {
                    cancel();
                    onBossDeath();
                }
            }
        }.runTaskTimer(plugin, 40, 10); // check cada 0.5s
    }

    // ==================== SPAWN BOSS ====================

    private Location findSafeBossSpawnLocation() {
        World world = player.getWorld();

        // Intentar varias ubicaciones en círculo alrededor del jugador
        for (int radius = 8; radius <= 15; radius++) {
            for (int angle = 0; angle < 360; angle += 45) {
                double radians = Math.toRadians(angle);
                double x = arenaCenter.getX() + Math.cos(radians) * radius;
                double z = arenaCenter.getZ() + Math.sin(radians) * radius;

                // Encontrar Y seguro
                Location testLoc = new Location(world, x, arenaCenter.getY(), z);

                // Buscar el bloque sólido más alto
                int groundY = world.getHighestBlockYAt(testLoc);
                testLoc.setY(groundY + 1); // Spawnear 1 bloque arriba del suelo

                // Validar que sea una ubicación segura
                if (isSafeBossLocation(testLoc)) {
                    return testLoc;
                }

                // También probar 2 bloques arriba
                testLoc.setY(groundY + 2);
                if (isSafeBossLocation(testLoc)) {
                    return testLoc;
                }
            }
        }

        return null; // No se encontró ubicación segura
    }

    private boolean isSafeBossLocation(Location loc) {
        World world = loc.getWorld();

        // Verificar que no esté en agua/lava
        org.bukkit.Material blockBelow = world.getBlockAt(loc.clone().subtract(0, 1, 0)).getType();
        org.bukkit.Material blockAt = world.getBlockAt(loc).getType();
        org.bukkit.Material blockAbove = world.getBlockAt(loc.clone().add(0, 1, 0)).getType();

        // No spawnear en agua, lava, o dentro de bloques sólidos
        if (blockBelow == org.bukkit.Material.WATER || blockBelow == org.bukkit.Material.LAVA)
            return false;
        if (blockAt.isSolid())
            return false;
        if (blockAbove.isSolid())
            return false;

        // Verificar que haya suficiente espacio (2 bloques de altura)
        if (world.getBlockAt(loc.clone().add(0, 2, 0)).getType().isSolid())
            return false;

        // Verificar que no esté demasiado bajo (evitar ahogamiento)
        if (loc.getY() < world.getSeaLevel() - 5)
            return false;

        return true;
    }

    private void spawnBoss() {
        if (!active || !player.isOnline())
            return;

        // Encontrar una ubicación segura para spawnear el boss
        Location spawnLoc = findSafeBossSpawnLocation();
        if (spawnLoc == null) {
            // Fallback: spawnear directamente en el jugador si no hay ubicación segura
            spawnLoc = player.getLocation().clone().add(0, 2, 0);
            plugin.getLogger().warning("[ShadowHunter] No se encontró ubicación segura para el boss, usando fallback");
        }

        final Location finalSpawnLoc = spawnLoc; // Make effectively final for inner class
        World world = spawnLoc.getWorld();

        // Animación de spawn épica
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 60) {
                    cancel();
                    materializedBoss(finalSpawnLoc);
                    return;
                }

                // Grietas en el suelo (partículas)
                if (ticks < 30) {
                    for (int i = 0; i < 8; i++) {
                        double angle = (Math.PI * 2 / 8) * i;
                        double r = ticks * 0.2;
                        world.spawnParticle(Particle.DUST,
                                finalSpawnLoc.clone().add(Math.cos(angle) * r, 0.1, Math.sin(angle) * r),
                                2, 0.1, 0, 0.1, 0,
                                new Particle.DustOptions(Color.fromRGB(50, 0, 0), 2.0f));
                    }
                }

                // Temblor (sacudir pantalla)
                if (ticks > 15 && ticks % 3 == 0) {
                    earthquakeEffect(0.3 + (ticks / 60.0) * 0.5);
                }

                // Columna de sombra ascendente
                if (ticks > 30) {
                    double height = (ticks - 30) * 0.15;
                    world.spawnParticle(Particle.SMOKE, finalSpawnLoc.clone().add(0, height, 0),
                            15, 0.5, 0.3, 0.5, 0.02);
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, finalSpawnLoc.clone().add(0, height, 0),
                            5, 0.3, 0.2, 0.3, 0.02);
                }

                // Levantar bloques falsos del suelo
                if (ticks > 20 && ticks % 8 == 0) {
                    launchGroundBlocks(finalSpawnLoc, 4, 0.3);
                }

                // Sonidos crecientes
                if (ticks % 10 == 0) {
                    float pitch = 0.3f + (ticks / 60.0f) * 0.7f;
                    world.playSound(finalSpawnLoc, Sound.ENTITY_WITHER_SPAWN, 0.3f + ticks / 100.0f, pitch);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private void materializedBoss(Location loc) {
        if (!active)
            return;
        World world = loc.getWorld();

        // Explosión final de spawn
        world.strikeLightningEffect(loc);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, 1, 0), 100, 2, 2, 2, 0.2);
        world.spawnParticle(Particle.SMOKE, loc.clone().add(0, 1, 0), 80, 2, 2, 2, 0.1);
        world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f);

        // Crear boss entity
        bossEntity = (WitherSkeleton) world.spawnEntity(loc, EntityType.WITHER_SKELETON);

        // Validar que el boss se creó correctamente
        if (bossEntity == null || bossEntity.isDead()) {
            plugin.getLogger().severe("[ShadowHunter] Error al crear el boss entity, intentando respawn...");
            // Intentar respawnear en 1 segundo
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (active) {
                    materializedBoss(loc);
                }
            }, 20L);
            return;
        }

        bossEntity.setCustomName(SmallCaps.convert("§4§l§kaa§r §c§lDevorador de Sombras §4§l§kaa"));
        bossEntity.setCustomNameVisible(true);
        bossEntity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(bossMaxHealth);
        bossEntity.setHealth(bossMaxHealth);
        bossEntity.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE).setBaseValue(8);
        bossEntity.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED).setBaseValue(0.28);
        bossEntity.getAttribute(org.bukkit.attribute.Attribute.KNOCKBACK_RESISTANCE).setBaseValue(0.8);
        bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 99999, 0, false, false));
        bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 99999, 0, false, false));

        // Equipamiento
        bossEntity.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_AXE));
        bossEntity.getEquipment().setHelmet(new ItemStack(Material.NETHERITE_HELMET));

        ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta cMeta = (LeatherArmorMeta) chest.getItemMeta();
        cMeta.setColor(Color.fromRGB(40, 0, 0));
        chest.setItemMeta(cMeta);
        bossEntity.getEquipment().setChestplate(chest);

        bossEntity.getEquipment().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
        bossEntity.getEquipment().setBoots(new ItemStack(Material.NETHERITE_BOOTS));

        // No dropear equipo
        bossEntity.getEquipment().setItemInMainHandDropChance(0);
        bossEntity.getEquipment().setHelmetDropChance(0);
        bossEntity.getEquipment().setChestplateDropChance(0);
        bossEntity.getEquipment().setLeggingsDropChance(0);
        bossEntity.getEquipment().setBootsDropChance(0);

        bossEntity.setTarget(player);

        // BossBar
        bossBar = Bukkit.createBossBar("§4§l☠ Devorador de Sombras", BarColor.RED, BarStyle.SEGMENTED_10);
        bossBar.addPlayer(player);
        bossBar.setProgress(1.0);

        player.sendTitle( SmallCaps.convert("§4§lDEVORADOR DE SOMBRAS"), SmallCaps.convert("§c¡Derrótalo para completar la misión!"), 10, 60, 20);

        // Iniciar AI del boss
        startBossAI();
    }

    // ==================== BOSS AI ====================

    private void startBossAI() {
        aiTask = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!active || bossEntity == null || bossEntity.isDead() || !player.isOnline()) {
                    cancel();
                    return;
                }

                if (freezeTicks > 0) {
                    freezeTicks--;
                    if (freezeTicks == 0) {
                        npc.say("\"¡Se acabó el tiempo! ¡El Devorador vuelve a cazar!\"");
                        bossEntity.setAI(true);
                        for (Entity mob : summonedMobs) {
                            if (mob instanceof LivingEntity && !mob.isDead()) {
                                ((LivingEntity) mob).setAI(true);
                            }
                        }
                    }
                    return;
                }

                // Update bossbar
                double healthPercent = bossEntity.getHealth() / bossMaxHealth;
                bossBar.setProgress(Math.max(0, Math.min(1, healthPercent)));

                // Fase de rabia a 30% HP
                if (healthPercent <= 0.3 && !enraged) {
                    triggerEnrage();
                }

                // Aura oscura permanente
                if (ticks % 4 == 0) {
                    Location bLoc = bossEntity.getLocation().add(0, 1.5, 0);
                    bLoc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, bLoc, 3, 0.4, 0.6, 0.4, 0.02);
                    bLoc.getWorld().spawnParticle(Particle.DUST, bLoc, 2, 0.5, 0.5, 0.5, 0,
                            new Particle.DustOptions(Color.fromRGB(80, 0, 0), 1.5f));
                }

                // Ataques especiales
                if (attackCooldown > 0) {
                    attackCooldown--;
                } else {
                    executeRandomAttack();
                    attackCooldown = enraged ? 40 : 80; // Más rápido en rabia
                }

                // Target update
                if (ticks % 20 == 0) {
                    bossEntity.setTarget(player);
                }

                // Romper bloques en el camino del boss y sus minions
                if (ticks % 5 == 0) {
                    clearPathForMob(bossEntity);
                    for (Entity minion : summonedMobs) {
                        if (minion instanceof LivingEntity && !minion.isDead()) {
                            clearPathForMob((LivingEntity) minion);
                        }
                    }
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 20, 1);
    }

    private void executeRandomAttack() {
        if (bossEntity == null || bossEntity.isDead())
            return;

        double distance = bossEntity.getLocation().distance(player.getLocation());
        double roll = Math.random();

        if (distance < 5) {
            // Cerca: onda de choque o terremoto
            if (roll < 0.5) {
                attackShockwave();
            } else {
                attackEarthquake();
            }
        } else if (distance < 12) {
            // Media: columnas o invocar
            if (roll < 0.4) {
                attackShadowPillars();
            } else if (roll < 0.7) {
                attackEarthquake();
            } else {
                attackSummon();
            }
        } else {
            // Lejos: terremoto grande o teletransporte
            if (roll < 0.5) {
                attackEarthquake();
            } else {
                attackTeleportSlam();
            }
        }
    }

    // ==================== ATAQUES ====================

    /**
     * TERREMOTO: sacude, levanta bloques del suelo, daño AOE
     */
    private void attackEarthquake() {
        if (bossEntity == null)
            return;
        Location bLoc = bossEntity.getLocation();
        World world = bLoc.getWorld();

        npc.say("\"§c¡TERREMOTO! ¡SALTA!\"");

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 40 || !active) {
                    cancel();
                    return;
                }

                // Sacudir pantalla
                earthquakeEffect(enraged ? 0.8 : 0.5);

                // Levantar bloques del suelo en onda expansiva
                if (ticks % 4 == 0) {
                    double radius = ticks * 0.5;
                    launchGroundBlocks(bLoc, (int) (radius * 0.8), enraged ? 0.6 : 0.35);

                    // Partículas de polvo
                    for (int i = 0; i < 16; i++) {
                        double a = (Math.PI * 2 / 16) * i;
                        world.spawnParticle(Particle.DUST,
                                bLoc.clone().add(Math.cos(a) * radius, 0.5, Math.sin(a) * radius),
                                3, 0.3, 0.2, 0.3, 0,
                                new Particle.DustOptions(Color.fromRGB(100, 80, 60), 2.0f));
                    }
                }

                // Sonido
                if (ticks % 5 == 0) {
                    world.playSound(bLoc, Sound.ENTITY_RAVAGER_ROAR, 0.8f, 0.5f);
                }

                // Daño si el jugador está en el suelo
                if (ticks % 10 == 0 && player.isOnGround() && player.getLocation().distance(bLoc) < 12) {
                    player.damage(enraged ? 6 : 3, bossEntity);
                    player.setVelocity(new Vector(0, 0.6, 0));
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    /**
     * COLUMNAS DE SOMBRA: pilares de partículas que caen del cielo y dañan
     */
    private void attackShadowPillars() {
        if (bossEntity == null)
            return;
        World world = player.getWorld();

        int pillarCount = enraged ? 6 : 4;

        new BukkitRunnable() {
            int spawned = 0;

            @Override
            public void run() {
                if (spawned >= pillarCount || !active) {
                    cancel();
                    return;
                }

                // Posición del pilar: cerca del jugador
                Location target = player.getLocation().clone().add(
                        (Math.random() - 0.5) * 8,
                        0,
                        (Math.random() - 0.5) * 8);
                target.setY(world.getHighestBlockYAt(target) + 1);

                // Indicador: círculo rojo en el suelo (1.5 segundos de warning)
                Location warningLoc = target.clone();
                new BukkitRunnable() {
                    int ticks = 0;

                    @Override
                    public void run() {
                        if (ticks >= 30 || !active) {
                            if (ticks >= 30) {
                                // IMPACTO
                                impactShadowPillar(warningLoc);
                            }
                            cancel();
                            return;
                        }

                        // Círculo de warning
                        for (int i = 0; i < 12; i++) {
                            double a = (Math.PI * 2 / 12) * i;
                            world.spawnParticle(Particle.DUST,
                                    warningLoc.clone().add(Math.cos(a) * 1.5, 0.1, Math.sin(a) * 1.5),
                                    1, 0, 0, 0, 0,
                                    new Particle.DustOptions(Color.RED, 1.0f));
                        }

                        ticks++;
                    }
                }.runTaskTimer(plugin, 0, 1);

                spawned++;
            }
        }.runTaskTimer(plugin, 0, 8);
    }

    private void impactShadowPillar(Location loc) {
        World world = loc.getWorld();

        // Columna de partículas
        for (int y = 0; y < 15; y++) {
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, y, 0), 8, 0.3, 0, 0.3, 0.02);
            world.spawnParticle(Particle.SMOKE, loc.clone().add(0, y, 0), 5, 0.4, 0, 0.4, 0.01);
        }

        world.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8f, 0.6f);

        // Daño si el jugador está cerca
        if (player.getLocation().distance(loc) < 2.5) {
            player.damage(enraged ? 8 : 5, bossEntity);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
        }
    }

    /**
     * ONDA DE CHOQUE: knockback masivo + daño
     */
    private void attackShockwave() {
        if (bossEntity == null)
            return;
        Location bLoc = bossEntity.getLocation();
        World world = bLoc.getWorld();

        // Animación de carga
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 20 || !active) {
                    if (ticks >= 20)
                        releaseShockwave(bLoc);
                    cancel();
                    return;
                }

                // Absorber partículas
                double radius = 5.0 - (ticks * 0.2);
                for (int i = 0; i < 8; i++) {
                    double a = (Math.PI * 2 / 8) * i + ticks * 0.3;
                    world.spawnParticle(Particle.DUST,
                            bLoc.clone().add(Math.cos(a) * radius, 1, Math.sin(a) * radius),
                            2, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(150, 0, 0), 1.5f));
                }

                world.playSound(bLoc, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.5f, 0.5f + ticks * 0.05f);
                ticks++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private void releaseShockwave(Location center) {
        World world = center.getWorld();

        // Onda expansiva visual
        new BukkitRunnable() {
            double radius = 0;

            @Override
            public void run() {
                if (radius > 12 || !active) {
                    cancel();
                    return;
                }

                for (int i = 0; i < 24; i++) {
                    double a = (Math.PI * 2 / 24) * i;
                    Location pLoc = center.clone().add(Math.cos(a) * radius, 0.5, Math.sin(a) * radius);
                    world.spawnParticle(Particle.DUST, pLoc, 2, 0, 0.2, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(200, 0, 0), 2.0f));
                    world.spawnParticle(Particle.SMOKE, pLoc, 1, 0.1, 0.1, 0.1, 0.01);
                }

                radius += 1.0;
            }
        }.runTaskTimer(plugin, 0, 1);

        world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 0.8f);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.6f);

        // Daño y knockback
        if (player.getLocation().distance(center) < 10) {
            player.damage(enraged ? 10 : 6, bossEntity);
            Vector kb = player.getLocation().toVector().subtract(center.toVector()).normalize().multiply(2.0);
            kb.setY(0.8);
            player.setVelocity(kb);
        }

        // Levantar bloques
        launchGroundBlocks(center, 6, 0.5);
    }

    /**
     * INVOCAR: spawna 2-3 sombras menores
     */
    private void attackSummon() {
        if (bossEntity == null)
            return;

        summonedMobs.removeIf(Entity::isDead);
        if (summonedMobs.size() >= 4)
            return; // Limitar lag

        World world = bossEntity.getWorld();
        Location bLoc = bossEntity.getLocation();

        npc.say("\"¡Está invocando sombras! ¡Elimínalas rápido!\"");

        int count = enraged ? 2 : 1;
        for (int i = 0; i < count; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Location loc = bLoc.clone().add((Math.random() - 0.5) * 6, 0, (Math.random() - 0.5) * 6);
                loc.setY(world.getHighestBlockYAt(loc) + 1);

                world.spawnParticle(Particle.SMOKE, loc, 20, 0.5, 1, 0.5, 0.05);
                world.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 0.7f);

                Zombie shadow = (Zombie) world.spawnEntity(loc, EntityType.ZOMBIE);
                shadow.setCustomName(SmallCaps.convert("§8§lSombra Menor"));
                shadow.setCustomNameVisible(true);
                shadow.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(20);
                shadow.setHealth(20);
                shadow.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED).setBaseValue(0.32);
                shadow.setBaby(false);
                shadow.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 99999, 0, false, false));
                shadow.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 99999, 0, false, false));
                shadow.setTarget(player);

                summonedMobs.add(shadow);
            }, i * 10L);
        }
    }

    /**
     * TELEPORT SLAM: se teletransporta sobre el jugador y cae con impacto
     */
    private void attackTeleportSlam() {
        if (bossEntity == null)
            return;
        World world = bossEntity.getWorld();
        Location playerLoc = player.getLocation().clone();

        // Desaparecer
        world.spawnParticle(Particle.SMOKE, bossEntity.getLocation().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.1);
        bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 30, 0, false, false));

        // Warning en la posición del jugador
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 25 || !active) {
                    cancel();
                    if (ticks >= 25 && bossEntity != null && !bossEntity.isDead()) {
                        // SLAM
                        Location slamLoc = playerLoc.clone();
                        slamLoc.setY(world.getHighestBlockYAt(slamLoc) + 1);
                        bossEntity.teleport(slamLoc);
                        bossEntity.removePotionEffect(PotionEffectType.INVISIBILITY);

                        world.playSound(slamLoc, Sound.ENTITY_RAVAGER_ROAR, 1.0f, 0.6f);
                        world.spawnParticle(Particle.SMOKE, slamLoc, 50, 2, 0.5, 2, 0.1);
                        launchGroundBlocks(slamLoc, 5, 0.5);
                        earthquakeEffect(0.7);

                        if (player.getLocation().distance(slamLoc) < 4) {
                            player.damage(enraged ? 12 : 7, bossEntity);
                            Vector kb = player.getLocation().toVector().subtract(slamLoc.toVector()).normalize()
                                    .multiply(1.5);
                            kb.setY(0.6);
                            player.setVelocity(kb);
                        }
                    }
                    return;
                }

                // Sombra circular warning
                for (int i = 0; i < 12; i++) {
                    double a = (Math.PI * 2 / 12) * i;
                    double r = 2.5 - (ticks * 0.08);
                    world.spawnParticle(Particle.DUST,
                            playerLoc.clone().add(Math.cos(a) * r, 0.1, Math.sin(a) * r),
                            1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(100, 0, 0), 1.5f));
                }

                if (ticks % 5 == 0) {
                    world.playSound(playerLoc, Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.5f + ticks * 0.04f);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    // ==================== ENRAGE ====================

    private void triggerEnrage() {
        enraged = true;
        World world = bossEntity.getWorld();
        Location bLoc = bossEntity.getLocation();

        bossBar.setTitle(SmallCaps.convert("§4§l☠ Devorador de Sombras §c§l[FURIOSO]"));
        bossBar.setColor(BarColor.WHITE);

        bossEntity.setCustomName(SmallCaps.convert("§4§l§kaa§r §4§lDEVORADOR FURIOSO §4§l§kaa"));
        bossEntity.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE).setBaseValue(12);
        bossEntity.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED).setBaseValue(0.35);
        bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 99999, 1, false, false));

        // Animación de rabia
        world.strikeLightningEffect(bLoc);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, bLoc.clone().add(0, 1, 0), 100, 2, 2, 2, 0.3);
        world.playSound(bLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.3f);
        world.playSound(bLoc, Sound.ENTITY_WITHER_SPAWN, 0.5f, 0.5f);

        earthquakeEffect(1.0);
        launchGroundBlocks(bLoc, 10, 0.7);

        player.sendTitle( SmallCaps.convert("§4§l¡FURIOSO!"), SmallCaps.convert("§cEl Devorador entra en fase de rabia"), 10, 40, 10);
        npc.say("\"§4¡ESTÁ FURIOSO! ¡CUIDADO! ¡No pares de moverte!\"");
    }

    // ==================== BOSS DEATH / FREEZE ====================

    public void onPlayerDeath() {
        if (!active || bossEntity == null)
            return;
        freezeTicks = 15 * 20; // 15 segundos
        npc.say("\"§e¡Te han derribado! Tienes 15 segundos para recuperar tus cosas. ¡He paralizado al Devorador!\"");
        bossEntity.setAI(false);
        for (Entity mob : summonedMobs) {
            if (mob instanceof LivingEntity && !mob.isDead()) {
                ((LivingEntity) mob).setAI(false);
            }
        }
    }

    public void onBossDeath() {
        if (!active)
            return;
        active = false;

        World world = player.getWorld();
        Location deathLoc = bossEntity != null ? bossEntity.getLocation() : arenaCenter;

        // Limpiar
        if (aiTask != null)
            aiTask.cancel();
        if (arenaTask != null)
            arenaTask.cancel();
        if (barrierTask != null)
            barrierTask.cancel();
        for (Entity mob : summonedMobs) {
            if (mob != null && !mob.isDead())
                mob.remove();
        }

        // Animación de muerte ÉPICA
        new BukkitRunnable() {
            int phase = 0;

            @Override
            public void run() {
                switch (phase) {
                    case 0:
                        // Congelación
                        world.playSound(deathLoc, Sound.ENTITY_WARDEN_DEATH, 1.0f, 0.5f);
                        world.spawnParticle(Particle.SOUL, deathLoc.clone().add(0, 2, 0), 50, 1, 2, 1, 0.1);
                        break;
                    case 10:
                        // Implosión
                        for (int r = 10; r > 0; r--) {
                            final int radius = r;
                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                for (int i = 0; i < 16; i++) {
                                    double a = (Math.PI * 2 / 16) * i;
                                    world.spawnParticle(Particle.DUST,
                                            deathLoc.clone().add(Math.cos(a) * radius, 1, Math.sin(a) * radius),
                                            2, 0, 0, 0, 0,
                                            new Particle.DustOptions(Color.fromRGB(100, 0, 150), 2.0f));
                                }
                            }, (10 - radius) * 2L);
                        }
                        world.playSound(deathLoc, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.0f, 0.3f);
                        break;
                    case 30:
                        // Explosión de luz
                        world.spawnParticle(Particle.END_ROD, deathLoc.clone().add(0, 2, 0), 10, 0.5, 0.5, 0.5, 0.1);
                        world.spawnParticle(Particle.END_ROD, deathLoc.clone().add(0, 2, 0), 300, 4, 4, 4, 0.5);
                        world.spawnParticle(Particle.TOTEM_OF_UNDYING, deathLoc.clone().add(0, 2, 0), 200, 3, 3, 3,
                                0.8);
                        world.playSound(deathLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.5f);
                        world.playSound(deathLoc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

                        // Columna de luz al cielo
                        for (int y = 0; y < 50; y++) {
                            world.spawnParticle(Particle.END_ROD, deathLoc.clone().add(0, y, 0), 3, 0.2, 0, 0.2, 0.01);
                        }

                        earthquakeEffect(0.6);
                        launchGroundBlocks(deathLoc, 8, 0.4);
                        break;
                    case 40:
                        // Fuegos artificiales
                        for (int i = 0; i < 5; i++) {
                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                Location fwLoc = deathLoc.clone().add(
                                        (Math.random() - 0.5) * 8,
                                        2 + Math.random() * 3,
                                        (Math.random() - 0.5) * 8);
                                Firework fw = (Firework) world.spawnEntity(fwLoc, EntityType.FIREWORK_ROCKET);
                                org.bukkit.inventory.meta.FireworkMeta fwMeta = fw.getFireworkMeta();
                                fwMeta.addEffect(org.bukkit.FireworkEffect.builder()
                                        .with(org.bukkit.FireworkEffect.Type.BALL_LARGE)
                                        .withColor(Color.PURPLE, Color.FUCHSIA, Color.WHITE)
                                        .withFade(Color.fromRGB(100, 0, 150))
                                        .withTrail().withFlicker()
                                        .build());
                                fwMeta.setPower(0);
                                fw.setFireworkMeta(fwMeta);
                                Bukkit.getScheduler().runTaskLater(plugin, fw::detonate, 2L);
                            }, i * 5L);
                        }
                        break;
                    case 50:
                        player.sendTitle( SmallCaps.convert("§5§l¡BOSS DERROTADO!"), SmallCaps.convert("§dHas demostrado tu valía"), 10, 60, 20);
                        npc.say("\"§a¡INCREÍBLE! Has derrotado al Devorador... Mereces tu recompensa.\"");
                        break;
                    case 80:
                        cancel();
                        if (bossBar != null)
                            bossBar.removeAll();
                        manager.onBossDefeated(player);
                        return;
                }
                phase++;
            }
        }.runTaskTimer(plugin, 0, 2);
    }

    // ==================== EFECTOS ====================

    /**
     * Efecto de terremoto: sacudir pantalla del jugador usando daño del mundo
     */
    private void earthquakeEffect(double intensity) {
        if (!player.isOnline())
            return;

        // Sacudir con velocity sutil
        Vector shake = new Vector(
                (Math.random() - 0.5) * intensity * 0.15,
                Math.random() * intensity * 0.08,
                (Math.random() - 0.5) * intensity * 0.15);
        // Solo aplicar si está en el suelo para no romper saltos
        if (player.isOnGround()) {
            player.setVelocity(player.getVelocity().add(shake));
        }

        // Sonido de terremoto
        player.playSound(player.getLocation(), Sound.ENTITY_RAVAGER_STEP, (float) intensity, 0.3f);
    }

    /**
     * Levantar bloques falsos del suelo (FallingBlock que desaparecen)
     * NO destruye bloques reales.
     */
    private void launchGroundBlocks(Location center, int count, double force) {
        World world = center.getWorld();

        for (int i = 0; i < count; i++) {
            double angle = Math.random() * Math.PI * 2;
            double dist = 1 + Math.random() * 4;
            Location blockLoc = center.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
            blockLoc.setY(world.getHighestBlockYAt(blockLoc));

            Block sourceBlock = world.getBlockAt(blockLoc);
            if (sourceBlock.getType() == Material.AIR || sourceBlock.getType() == Material.BEDROCK)
                continue;

            // Crear FallingBlock con el mismo tipo visual
            BlockData blockData = sourceBlock.getBlockData().clone();
            try {
                FallingBlock fb = world.spawnFallingBlock(
                        blockLoc.clone().add(0.5, 1, 0.5),
                        blockData);
                fb.setDropItem(false);
                fb.setHurtEntities(false);
                fb.setGravity(true);

                // Lanzar hacia arriba con velocidad aleatoria
                double vx = (Math.random() - 0.5) * force;
                double vy = 0.3 + Math.random() * force;
                double vz = (Math.random() - 0.5) * force;
                fb.setVelocity(new Vector(vx, vy, vz));

                fallingBlocks.add(fb);

                // Remover el FallingBlock tras 3 segundos para que no deje bloques
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!fb.isDead())
                        fb.remove();
                    fallingBlocks.remove(fb);
                }, 60L);
            } catch (Exception ignored) {
                // Algunos bloques no se pueden crear como FallingBlock
            }
        }
    }

    // ==================== ARENA ====================

    private void startArenaVisuals() {
        arenaTask = new BukkitRunnable() {
            double angle = 0;

            @Override
            public void run() {
                if (!active || !player.isOnline()) {
                    cancel();
                    return;
                }

                World world = arenaCenter.getWorld();

                for (int i = 0; i < 16; i++) {
                    double a = angle + (Math.PI * 2 / 16) * i;
                    double x = Math.cos(a) * ARENA_RADIUS;
                    double z = Math.sin(a) * ARENA_RADIUS;
                    Location pLoc = arenaCenter.clone().add(x, 0.5, z);
                    pLoc.setY(world.getHighestBlockYAt(pLoc) + 0.5);

                    world.spawnParticle(Particle.DUST, pLoc, 1, 0, 0.3, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(100, 0, 0), 1.5f));
                }

                angle += 0.03;
            }
        }.runTaskTimer(plugin, 0, 5);
    }

    private void startBarrier() {
        barrierTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!active || !player.isOnline()) {
                    cancel();
                    return;
                }

                if (player.getLocation().distance(arenaCenter) > ARENA_RADIUS) {
                    player.teleport(arenaCenter);
                    player.sendMessage(SmallCaps.convert("§c§l⚠ §fNo puedes huir del Devorador."));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                }

                // Evitar que el boss salga de la arena
                if (bossEntity != null && !bossEntity.isDead()
                        && bossEntity.getLocation().getWorld().equals(arenaCenter.getWorld())) {
                    if (bossEntity.getLocation().distance(arenaCenter) > ARENA_RADIUS) {
                        bossEntity.teleport(arenaCenter);
                    }
                }

                // Evitar que los minions salgan de la arena
                for (Entity mob : summonedMobs) {
                    if (mob != null && !mob.isDead() && mob.getLocation().getWorld().equals(arenaCenter.getWorld())) {
                        if (mob.getLocation().distance(arenaCenter) > ARENA_RADIUS) {
                            mob.teleport(arenaCenter);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 10);
    }

    // ==================== GETTERS ====================

    public boolean isActive() {
        return active;
    }

    public Location getArenaCenter() {
        return arenaCenter;
    }

    public boolean isBossEntity(UUID entityId) {
        return bossEntity != null && bossEntity.getUniqueId().equals(entityId);
    }

    public void collectProtectedEntities(java.util.List<org.bukkit.entity.Entity> out) {
        if (bossEntity != null && !bossEntity.isDead()) out.add(bossEntity);
        for (Entity e : summonedMobs) { if (e != null && !e.isDead()) out.add(e); }
    }

    public boolean isSummonedMob(UUID entityId) {
        return summonedMobs.stream().anyMatch(e -> e.getUniqueId().equals(entityId));
    }

    // ==================== MOB PATHFINDING ====================

    private void clearPathForMob(LivingEntity mob) {
        if (!player.isOnline())
            return;
        Location mobLoc = mob.getLocation();

        // Natación: si está en agua/lava, subir a la superficie
        if (mobLoc.getBlock().isLiquid()) {
            mob.setVelocity(mob.getVelocity().add(new Vector(0, 0.15, 0)));
        }

        Location playerLoc = player.getLocation();
        if (!mobLoc.getWorld().equals(playerLoc.getWorld()))
            return;

        Vector dir = playerLoc.toVector().subtract(mobLoc.toVector()).normalize();

        Location feet = mobLoc.clone().add(dir.clone().multiply(1.0));
        feet.setY(mobLoc.getY());
        Location head = feet.clone().add(0, 1, 0);

        if (feet.getBlock().getType().isSolid() && isBreakable(feet.getBlock().getType())) {
            mineBlock(feet.getBlock());
        }
        if (head.getBlock().getType().isSolid() && isBreakable(head.getBlock().getType())) {
            mineBlock(head.getBlock());
        }

        if (playerLoc.getY() < mobLoc.getY() - 1) {
            Location below = mobLoc.clone().add(dir.getX() * 0.5, -1, dir.getZ() * 0.5);
            if (below.getBlock().getType().isSolid() && isBreakable(below.getBlock().getType())) {
                mineBlock(below.getBlock());
            }
        }
        if (playerLoc.getY() > mobLoc.getY() + 2) {
            Location above = mobLoc.clone().add(dir.getX() * 0.5, 2, dir.getZ() * 0.5);
            if (above.getBlock().getType().isSolid() && isBreakable(above.getBlock().getType())) {
                mineBlock(above.getBlock());
            }
        }
    }

    private void mineBlock(org.bukkit.block.Block block) {
        if (block.getType() == Material.AIR || block.getType() == Material.BEDROCK)
            return;
        org.bukkit.block.data.BlockData originalData = block.getBlockData().clone();
        Location loc = block.getLocation().add(0.5, 0.5, 0.5);

        block.getWorld().spawnParticle(Particle.BLOCK, loc, 12, 0.3, 0.3, 0.3, 0.1, originalData);
        block.getWorld().playSound(loc, Sound.BLOCK_STONE_BREAK, 0.5f, 1.0f);
        block.setType(Material.AIR);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (block.getType() == Material.AIR) {
                block.setBlockData(originalData);
            }
        }, 20L * 30);
    }

    private boolean isBreakable(Material mat) {
        if (mat == Material.AIR || mat == Material.CAVE_AIR || mat == Material.VOID_AIR)
            return false;
        if (mat == Material.BEDROCK || mat == Material.OBSIDIAN || mat == Material.CRYING_OBSIDIAN)
            return false;
        if (mat == Material.REINFORCED_DEEPSLATE || mat == Material.BARRIER)
            return false;
        if (mat == Material.END_PORTAL || mat == Material.END_PORTAL_FRAME || mat == Material.NETHER_PORTAL)
            return false;
        if (mat == Material.COMMAND_BLOCK || mat == Material.CHAIN_COMMAND_BLOCK
                || mat == Material.REPEATING_COMMAND_BLOCK)
            return false;
        if (mat.name().contains("CHEST") || mat.name().contains("SHULKER"))
            return false;
        return true;
    }

    public void cleanup() {
        active = false;
        if (aiTask != null)
            aiTask.cancel();
        if (arenaTask != null)
            arenaTask.cancel();
        if (barrierTask != null)
            barrierTask.cancel();
        if (trackTask != null)
            trackTask.cancel();
        if (bossBar != null)
            bossBar.removeAll();
        if (bossEntity != null && !bossEntity.isDead())
            bossEntity.remove();
        for (Entity mob : summonedMobs) {
            if (mob != null && !mob.isDead())
                mob.remove();
        }
        for (FallingBlock fb : fallingBlocks) {
            if (fb != null && !fb.isDead())
                fb.remove();
        }
        summonedMobs.clear();
        fallingBlocks.clear();
    }
}
