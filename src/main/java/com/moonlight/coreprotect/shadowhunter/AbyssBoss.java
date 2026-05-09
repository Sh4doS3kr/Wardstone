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
 * Boss final de la Misión 2: "El Titán del Abismo"
 * 500 HP, 3 fases basadas en HP:
 * - Fase 1 (100%-60%): Melee + terremotos + columnas de sombra
 * - Fase 2 (60%-30%): Oscuridad + invocaciones + rayos + AoE oscuridad
 * - Fase 3 (30%-0%): FURIOSO - meteoritos, ondas de vacío, todo más rápido
 */
public class AbyssBoss {

    private final CoreProtectPlugin plugin;
    private final ShadowHunterManager manager;
    private final Player player;
    private final ShadowHunterNPC npc;

    private Location arenaCenter;
    private static final double ARENA_RADIUS = 45.0;

    private WitherSkeleton bossEntity;
    private BossBar bossBar;
    private BukkitTask aiTask;
    private BukkitTask arenaTask;
    private BukkitTask barrierTask;
    private BukkitTask trackTask;

    private final List<Entity> summonedMobs = new ArrayList<>();
    private final List<FallingBlock> fallingBlocks = new ArrayList<>();

    private boolean active = false;
    private int currentPhase = 1;
    private int attackCooldown = 0;
    private double bossMaxHealth = 500;
    private int freezeTicks = 0;

    public AbyssBoss(CoreProtectPlugin plugin, ShadowHunterManager manager, Player player, ShadowHunterNPC npc) {
        this.plugin = plugin;
        this.manager = manager;
        this.player = player;
        this.npc = npc;
    }

    public void start() {
        active = true;
        arenaCenter = player.getLocation().clone();

        Location npcLoc = arenaCenter.clone().add(ARENA_RADIUS - 2, 0, 0);
        npcLoc.setY(npcLoc.getWorld().getHighestBlockYAt(npcLoc) + 1);
        npc.moveToAndStay(npcLoc);

        npc.say("\"§4§l¡¿QUÉ HAS DESPERTADO?! §7Eso... eso es el Titán del Abismo...\"");
        npc.sayDelayed("\"§c¡Es ENORME! ¡No bajes la guardia ni un segundo!\"", 40L);

        Bukkit.getScheduler().runTaskLater(plugin, this::spawnBoss, 100L);
        startArenaVisuals();
        startBarrier();
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
        }.runTaskTimer(plugin, 40, 10);
    }

    // ==================== SPAWN ====================

    private void spawnBoss() {
        if (!active || !player.isOnline())
            return;

        Location spawnLoc = findSafeSpawnLocation();
        if (spawnLoc == null) {
            spawnLoc = player.getLocation().clone().add(0, 2, 0);
        }

        final Location finalLoc = spawnLoc;
        World world = spawnLoc.getWorld();

        // Animación de spawn épica (más larga)
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 80) {
                    cancel();
                    materializeBoss(finalLoc);
                    return;
                }

                // Grietas crecientes
                if (ticks < 40) {
                    for (int i = 0; i < 12; i++) {
                        double angle = (Math.PI * 2 / 12) * i;
                        double r = ticks * 0.25;
                        world.spawnParticle(Particle.DUST,
                                finalLoc.clone().add(Math.cos(angle) * r, 0.1, Math.sin(angle) * r),
                                2, 0.1, 0, 0.1, 0,
                                new Particle.DustOptions(Color.fromRGB(40, 0, 0), 2.5f));
                    }
                }

                // Temblor creciente
                if (ticks > 20 && ticks % 2 == 0) {
                    earthquakeEffect(0.2 + (ticks / 80.0) * 0.8);
                }

                // Columna de sombra
                if (ticks > 40) {
                    double height = (ticks - 40) * 0.15;
                    world.spawnParticle(Particle.SMOKE, finalLoc.clone().add(0, height, 0), 20, 0.8, 0.3, 0.8, 0.03);
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, finalLoc.clone().add(0, height, 0), 8, 0.5, 0.2, 0.5,
                            0.03);
                    world.spawnParticle(Particle.DUST, finalLoc.clone().add(0, height, 0), 5, 0.4, 0.2, 0.4, 0,
                            new Particle.DustOptions(Color.fromRGB(80, 0, 120), 2.0f));
                }

                if (ticks > 30 && ticks % 10 == 0) {
                    launchGroundBlocks(finalLoc, 5, 0.4);
                }

                if (ticks % 8 == 0) {
                    float pitch = 0.2f + (ticks / 80.0f) * 0.8f;
                    world.playSound(finalLoc, Sound.ENTITY_WITHER_SPAWN, 0.2f + ticks / 150.0f, pitch);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private void materializeBoss(Location loc) {
        if (!active)
            return;
        World world = loc.getWorld();

        // Explosión triple
        world.strikeLightningEffect(loc);
        world.strikeLightningEffect(loc.clone().add(5, 0, 0));
        world.strikeLightningEffect(loc.clone().add(-5, 0, 0));
        world.strikeLightningEffect(loc.clone().add(0, 0, 5));
        world.strikeLightningEffect(loc.clone().add(0, 0, -5));
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, 1, 0), 200, 3, 3, 3, 0.3);
        world.spawnParticle(Particle.SMOKE, loc.clone().add(0, 1, 0), 150, 3, 3, 3, 0.15);
        world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 0.3f);
        world.playSound(loc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f);

        bossEntity = (WitherSkeleton) world.spawnEntity(loc, EntityType.WITHER_SKELETON);
        if (bossEntity == null || bossEntity.isDead()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (active)
                    materializeBoss(loc);
            }, 20L);
            return;
        }

        bossEntity.setCustomName(SmallCaps.convert("§4§l§kaa§r §c§lTitán del Abismo §4§l§kaa"));
        bossEntity.setCustomNameVisible(true);
        bossEntity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(bossMaxHealth);
        bossEntity.setHealth(bossMaxHealth);
        bossEntity.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE).setBaseValue(10);
        bossEntity.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED).setBaseValue(0.28);
        bossEntity.getAttribute(org.bukkit.attribute.Attribute.KNOCKBACK_RESISTANCE).setBaseValue(0.9);
        bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 99999, 0, false, false));
        bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 99999, 0, false, false));

        // Equipamiento
        bossEntity.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_AXE));
        bossEntity.getEquipment().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
        ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta cMeta = (LeatherArmorMeta) chest.getItemMeta();
        cMeta.setColor(Color.fromRGB(30, 0, 50));
        chest.setItemMeta(cMeta);
        bossEntity.getEquipment().setChestplate(chest);
        bossEntity.getEquipment().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
        bossEntity.getEquipment().setBoots(new ItemStack(Material.NETHERITE_BOOTS));
        bossEntity.getEquipment().setItemInMainHandDropChance(0);
        bossEntity.getEquipment().setHelmetDropChance(0);
        bossEntity.getEquipment().setChestplateDropChance(0);
        bossEntity.getEquipment().setLeggingsDropChance(0);
        bossEntity.getEquipment().setBootsDropChance(0);
        bossEntity.setTarget(player);

        bossBar = Bukkit.createBossBar("§4§l☠ Titán del Abismo §8- §7Fase 1", BarColor.RED, BarStyle.SEGMENTED_20);
        bossBar.addPlayer(player);
        bossBar.setProgress(1.0);

        player.sendTitle( SmallCaps.convert("§4§lTITÁN DEL ABISMO"), SmallCaps.convert("§c¡Derrótalo para completar la misión!"), 10, 60, 20);
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
                        npc.say("\"§4¡El Titán vuelve a moverse! ¡Ten cuidado!\"");
                        bossEntity.setAI(true);
                        for (Entity mob : summonedMobs) {
                            if (mob instanceof LivingEntity && !mob.isDead()) {
                                ((LivingEntity) mob).setAI(true);
                            }
                        }
                    }
                    return; // Congelado
                }

                double healthPercent = bossEntity.getHealth() / bossMaxHealth;
                bossBar.setProgress(Math.max(0, Math.min(1, healthPercent)));

                // Transición de fases
                if (healthPercent <= 0.6 && currentPhase == 1) {
                    triggerPhase2();
                } else if (healthPercent <= 0.3 && currentPhase == 2) {
                    triggerPhase3();
                }

                // Aura del boss
                if (ticks % 3 == 0) {
                    Location bLoc = bossEntity.getLocation().add(0, 1.5, 0);
                    Color auraColor = currentPhase == 3 ? Color.fromRGB(200, 0, 0)
                            : currentPhase == 2 ? Color.fromRGB(80, 0, 120) : Color.fromRGB(40, 0, 60);
                    bLoc.getWorld().spawnParticle(Particle.DUST, bLoc, 3, 0.5, 0.5, 0.5, 0,
                            new Particle.DustOptions(auraColor, currentPhase == 3 ? 2.0f : 1.5f));
                    bLoc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, bLoc, 2, 0.4, 0.5, 0.4, 0.02);
                }

                // Ataques
                if (attackCooldown > 0) {
                    attackCooldown--;
                } else {
                    executePhaseAttack();
                    int cd = currentPhase == 3 ? 30 : currentPhase == 2 ? 50 : 70;
                    attackCooldown = cd;
                }

                if (ticks % 20 == 0)
                    bossEntity.setTarget(player);

                // Pathfinding
                if (ticks % 5 == 0) {
                    clearPathForMob(bossEntity);
                    for (Entity mob : summonedMobs) {
                        if (mob instanceof LivingEntity && !mob.isDead())
                            clearPathForMob((LivingEntity) mob);
                    }
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 20, 1);
    }

    private void executePhaseAttack() {
        if (bossEntity == null || bossEntity.isDead())
            return;
        double dist = bossEntity.getLocation().distance(player.getLocation());
        double roll = Math.random();

        switch (currentPhase) {
            case 1:
                if (dist < 5) {
                    if (roll < 0.5)
                        attackShockwave();
                    else
                        attackEarthquake();
                } else if (dist < 12) {
                    if (roll < 0.4)
                        attackShadowPillars();
                    else
                        attackEarthquake();
                } else {
                    if (roll < 0.5)
                        attackEarthquake();
                    else
                        attackTeleportSlam();
                }
                break;
            case 2:
                if (roll < 0.25)
                    attackShadowPillars();
                else if (roll < 0.45)
                    attackSummon();
                else if (roll < 0.65)
                    attackDarkBeam();
                else if (roll < 0.85)
                    attackEarthquake();
                else
                    attackShockwave();
                break;
            case 3:
                if (roll < 0.2)
                    attackMeteorStorm();
                else if (roll < 0.35)
                    attackVoidPull();
                else if (roll < 0.5)
                    attackShockwave();
                else if (roll < 0.65)
                    attackShadowPillars();
                else if (roll < 0.8)
                    attackTeleportSlam();
                else {
                    attackSummon();
                    attackEarthquake();
                }
                break;
        }
    }

    // ==================== FASES ====================

    private void triggerPhase2() {
        currentPhase = 2;
        World world = bossEntity.getWorld();
        Location bLoc = bossEntity.getLocation();

        bossBar.setTitle(SmallCaps.convert("§4§l☠ Titán del Abismo §8- §5§lFase 2: Oscuridad"));
        bossBar.setColor(BarColor.PURPLE);

        bossEntity.setCustomName(SmallCaps.convert("§5§l§kaa§r §4§lTitán Oscuro §5§l§kaa"));
        bossEntity.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE).setBaseValue(14);
        bossEntity.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED).setBaseValue(0.32);

        world.strikeLightningEffect(bLoc);
        world.spawnParticle(Particle.SOUL, bLoc.add(0, 1, 0), 80, 3, 2, 3, 0.1);
        world.spawnParticle(Particle.DUST, bLoc, 50, 3, 2, 3, 0,
                new Particle.DustOptions(Color.fromRGB(80, 0, 120), 2.0f));
        world.playSound(bLoc, Sound.ENTITY_WARDEN_ROAR, 1.0f, 0.5f);

        player.sendTitle( SmallCaps.convert("§5§lFASE 2: OSCURIDAD"), SmallCaps.convert("§7El Titán se fortalece..."), 10, 40, 10);
        npc.say("\"§5¡Se ha transformado! ¡Cuidado con los ataques oscuros!\"");

        // Efecto de oscuridad temporal
        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false));
    }

    private void triggerPhase3() {
        currentPhase = 3;
        World world = bossEntity.getWorld();
        Location bLoc = bossEntity.getLocation();

        bossBar.setTitle(SmallCaps.convert("§4§l☠ TITÁN FURIOSO §4§l☠"));
        bossBar.setColor(BarColor.WHITE);

        bossEntity.setCustomName(SmallCaps.convert("§4§l§kaa§r §c§lTITÁN FURIOSO §4§l§kaa"));
        bossEntity.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE).setBaseValue(18);
        bossEntity.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED).setBaseValue(0.38);
        bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 99999, 2, false, false));

        world.strikeLightningEffect(bLoc);
        world.strikeLightningEffect(bLoc.clone().add(3, 0, 3));
        world.strikeLightningEffect(bLoc.clone().add(-3, 0, -3));
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, bLoc.add(0, 1, 0), 150, 3, 3, 3, 0.4);
        world.playSound(bLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 0.2f);
        world.playSound(bLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.7f, 0.5f);

        earthquakeEffect(1.2);
        launchGroundBlocks(bLoc, 15, 0.8);

        player.sendTitle( SmallCaps.convert("§4§l¡¡FURIOSO!!"), SmallCaps.convert("§c¡FASE FINAL! ¡NO PARES!"), 10, 50, 10);
        npc.say("\"§4§l¡¡ESTÁ FURIOSO!! §c¡TODO O NADA! ¡NO TE DETENGAS!\"");
    }

    // ==================== ATAQUES ====================

    private void attackEarthquake() {
        if (bossEntity == null)
            return;
        Location bLoc = bossEntity.getLocation();
        World world = bLoc.getWorld();

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 50 || !active) {
                    cancel();
                    return;
                }
                earthquakeEffect(currentPhase == 3 ? 1.0 : currentPhase == 2 ? 0.7 : 0.5);
                if (ticks % 3 == 0) {
                    double radius = ticks * 0.6;
                    launchGroundBlocks(bLoc, (int) (radius * 0.5), currentPhase == 3 ? 0.7 : 0.4);
                    for (int i = 0; i < 20; i++) {
                        double a = (Math.PI * 2 / 20) * i;
                        world.spawnParticle(Particle.DUST,
                                bLoc.clone().add(Math.cos(a) * radius, 0.5, Math.sin(a) * radius),
                                2, 0.3, 0.2, 0.3, 0,
                                new Particle.DustOptions(Color.fromRGB(100, 80, 60), 2.0f));
                    }
                }
                if (ticks % 5 == 0)
                    world.playSound(bLoc, Sound.ENTITY_RAVAGER_ROAR, 0.8f, 0.5f);
                if (ticks % 8 == 0 && player.isOnGround() && player.getLocation().distance(bLoc) < 15) {
                    player.damage(currentPhase == 3 ? 8 : currentPhase == 2 ? 5 : 3, bossEntity);
                    player.setVelocity(new Vector(0, 0.7, 0));
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private void attackShadowPillars() {
        if (bossEntity == null)
            return;
        World world = player.getWorld();
        int count = currentPhase == 3 ? 8 : currentPhase == 2 ? 6 : 4;

        new BukkitRunnable() {
            int spawned = 0;

            @Override
            public void run() {
                if (spawned >= count || !active) {
                    cancel();
                    return;
                }
                Location target = player.getLocation().clone().add(
                        (Math.random() - 0.5) * 10, 0, (Math.random() - 0.5) * 10);
                target.setY(world.getHighestBlockYAt(target) + 1);

                // Warning
                new BukkitRunnable() {
                    int t = 0;

                    @Override
                    public void run() {
                        if (t >= 25 || !active) {
                            if (t >= 25)
                                impactPillar(target);
                            cancel();
                            return;
                        }
                        for (int i = 0; i < 12; i++) {
                            double a = (Math.PI * 2 / 12) * i;
                            world.spawnParticle(Particle.DUST,
                                    target.clone().add(Math.cos(a) * 1.5, 0.1, Math.sin(a) * 1.5),
                                    1, 0, 0, 0, 0, new Particle.DustOptions(Color.RED, 1.2f));
                        }
                        t++;
                    }
                }.runTaskTimer(plugin, 0, 1);
                spawned++;
            }
        }.runTaskTimer(plugin, 0, 6);
    }

    private void impactPillar(Location loc) {
        World world = loc.getWorld();
        for (int y = 0; y < 20; y++) {
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, y, 0), 10, 0.4, 0, 0.4, 0.03);
            world.spawnParticle(Particle.SMOKE, loc.clone().add(0, y, 0), 6, 0.5, 0, 0.5, 0.02);
        }
        world.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.9f, 0.5f);
        if (player.getLocation().distance(loc) < 3.0) {
            player.damage(currentPhase == 3 ? 10 : 6, bossEntity);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
        }
    }

    private void attackShockwave() {
        if (bossEntity == null)
            return;
        Location bLoc = bossEntity.getLocation();
        World world = bLoc.getWorld();

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t >= 20 || !active) {
                    if (t >= 20)
                        releaseShockwave(bLoc);
                    cancel();
                    return;
                }
                double r = 5.0 - (t * 0.2);
                for (int i = 0; i < 10; i++) {
                    double a = (Math.PI * 2 / 10) * i + t * 0.3;
                    world.spawnParticle(Particle.DUST,
                            bLoc.clone().add(Math.cos(a) * r, 1, Math.sin(a) * r), 2, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(150, 0, 0), 1.5f));
                }
                world.playSound(bLoc, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.5f, 0.5f + t * 0.05f);
                t++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private void releaseShockwave(Location center) {
        World world = center.getWorld();
        new BukkitRunnable() {
            double r = 0;

            @Override
            public void run() {
                if (r > 15 || !active) {
                    cancel();
                    return;
                }
                for (int i = 0; i < 30; i++) {
                    double a = (Math.PI * 2 / 30) * i;
                    Location pLoc = center.clone().add(Math.cos(a) * r, 0.5, Math.sin(a) * r);
                    world.spawnParticle(Particle.DUST, pLoc, 2, 0, 0.2, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(200, 0, 0), 2.5f));
                    world.spawnParticle(Particle.SMOKE, pLoc, 1, 0.1, 0.1, 0.1, 0.01);
                }
                r += 1.0;
            }
        }.runTaskTimer(plugin, 0, 1);

        world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 0.7f);
        if (player.getLocation().distance(center) < 12) {
            player.damage(currentPhase == 3 ? 12 : 8, bossEntity);
            Vector kb = player.getLocation().toVector().subtract(center.toVector()).normalize().multiply(2.5);
            kb.setY(0.9);
            player.setVelocity(kb);
        }
        launchGroundBlocks(center, 8, 0.6);
    }

    private void attackSummon() {
        if (bossEntity == null)
            return;

        summonedMobs.removeIf(Entity::isDead);
        if (summonedMobs.size() >= 5)
            return; // Limitar lag

        World world = bossEntity.getWorld();
        Location bLoc = bossEntity.getLocation();
        npc.say("\"§c¡Invocaciones! ¡Elimínalas rápido antes de que se acumulen!\"");

        int count = currentPhase == 3 ? 2 : 1;
        for (int i = 0; i < count; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Location loc = bLoc.clone().add((Math.random() - 0.5) * 8, 0, (Math.random() - 0.5) * 8);
                loc.setY(world.getHighestBlockYAt(loc) + 1);
                world.spawnParticle(Particle.SOUL, loc, 25, 0.5, 1, 0.5, 0.05);
                world.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 0.5f);

                Zombie shadow = (Zombie) world.spawnEntity(loc, EntityType.ZOMBIE);
                shadow.setCustomName(SmallCaps.convert("§8§lSombra del Abismo"));
                shadow.setCustomNameVisible(true);
                shadow.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(25);
                shadow.setHealth(25);
                shadow.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED).setBaseValue(0.35);
                shadow.setBaby(false);
                shadow.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 99999, 0, false, false));
                shadow.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 99999, 0, false, false));
                shadow.setTarget(player);
                summonedMobs.add(shadow);
            }, i * 12L);
        }
    }

    private void attackTeleportSlam() {
        if (bossEntity == null)
            return;
        World world = bossEntity.getWorld();
        Location playerLoc = player.getLocation().clone();

        world.spawnParticle(Particle.SMOKE, bossEntity.getLocation().add(0, 1, 0), 40, 0.5, 1, 0.5, 0.1);
        bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 35, 0, false, false));

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t >= 30 || !active) {
                    cancel();
                    if (t >= 30 && bossEntity != null && !bossEntity.isDead()) {
                        Location slam = playerLoc.clone();
                        slam.setY(world.getHighestBlockYAt(slam) + 1);
                        bossEntity.teleport(slam);
                        bossEntity.removePotionEffect(PotionEffectType.INVISIBILITY);
                        world.playSound(slam, Sound.ENTITY_RAVAGER_ROAR, 1.0f, 0.5f);
                        world.spawnParticle(Particle.SMOKE, slam, 60, 2, 0.5, 2, 0.1);
                        world.spawnParticle(Particle.SOUL_FIRE_FLAME, slam, 30, 2, 0.5, 2, 0.1);
                        launchGroundBlocks(slam, 8, 0.6);
                        earthquakeEffect(0.9);
                        if (player.getLocation().distance(slam) < 5) {
                            player.damage(currentPhase == 3 ? 14 : 9, bossEntity);
                            Vector kb = player.getLocation().toVector().subtract(slam.toVector()).normalize()
                                    .multiply(2.0);
                            kb.setY(0.7);
                            player.setVelocity(kb);
                        }
                    }
                    return;
                }
                // Warning circle
                for (int i = 0; i < 16; i++) {
                    double a = (Math.PI * 2 / 16) * i;
                    double r = 3.0 - (t * 0.08);
                    world.spawnParticle(Particle.DUST,
                            playerLoc.clone().add(Math.cos(a) * r, 0.1, Math.sin(a) * r),
                            1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(150, 0, 0), 1.5f));
                }
                if (t % 4 == 0)
                    world.playSound(playerLoc, Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.5f + t * 0.03f);
                t++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    // Ataque exclusivo Fase 2+: Rayo Oscuro (beam de partículas)
    private void attackDarkBeam() {
        if (bossEntity == null)
            return;
        World world = bossEntity.getWorld();
        npc.say("\"§5¡RAYO OSCURO! ¡MUÉVETE!\"");

        Location start = bossEntity.getLocation().add(0, 1.5, 0);
        Vector dir = player.getLocation().add(0, 1, 0).toVector().subtract(start.toVector()).normalize();

        // Charging
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t >= 25 || !active) {
                    cancel();
                    if (t >= 25)
                        fireBeam(start.clone(), dir.clone());
                    return;
                }
                world.spawnParticle(Particle.DUST, start, 5, 0.3, 0.3, 0.3, 0,
                        new Particle.DustOptions(Color.fromRGB(100, 0, 150), 2.0f + t * 0.05f));
                world.playSound(start, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.4f, 0.5f + t * 0.06f);
                t++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private void fireBeam(Location origin, Vector direction) {
        World world = origin.getWorld();
        world.playSound(origin, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 0.8f);

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t >= 5 || !active) {
                    cancel();
                    return;
                }
                // Draw beam
                for (double d = 0; d < 25; d += 0.5) {
                    Location point = origin.clone().add(direction.clone().multiply(d));
                    world.spawnParticle(Particle.DUST, point, 3, 0.15, 0.15, 0.15, 0,
                            new Particle.DustOptions(Color.fromRGB(80, 0, 150), 2.0f));
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, point, 1, 0.1, 0.1, 0.1, 0.01);
                    if (point.getBlock().getType().isSolid())
                        break;
                    if (player.getLocation().add(0, 1, 0).distance(point) < 1.5 && t == 0) {
                        player.damage(currentPhase == 3 ? 10 : 7, bossEntity);
                        player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0));
                    }
                }
                t++;
            }
        }.runTaskTimer(plugin, 0, 2);
    }

    // Ataque Fase 3: Meteoritos de Sombra
    private void attackMeteorStorm() {
        if (bossEntity == null)
            return;
        World world = player.getWorld();
        npc.say("\"§4§l¡¡METEORITOS!! ¡¡NO PARES DE MOVERTE!!\"");

        new BukkitRunnable() {
            int spawned = 0;

            @Override
            public void run() {
                if (spawned >= 10 || !active) {
                    cancel();
                    return;
                }
                Location target = player.getLocation().clone().add(
                        (Math.random() - 0.5) * 12, 0, (Math.random() - 0.5) * 12);
                target.setY(world.getHighestBlockYAt(target) + 1);

                // Meteor caída
                Location meteorStart = target.clone().add(0, 25, 0);
                new BukkitRunnable() {
                    int t = 0;

                    @Override
                    public void run() {
                        if (t >= 25 || !active) {
                            cancel();
                            if (t >= 25) {
                                // Impacto
                                world.spawnParticle(Particle.SOUL_FIRE_FLAME, target, 40, 1, 0.5, 1, 0.1);
                                world.spawnParticle(Particle.SMOKE, target, 30, 1, 0.5, 1, 0.1);
                                world.spawnParticle(Particle.END_ROD, target, 8, 0.3, 0.3, 0.3, 0.1);
                                world.playSound(target, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.8f);
                                launchGroundBlocks(target, 3, 0.4);
                                if (player.getLocation().distance(target) < 3.0) {
                                    player.damage(8, bossEntity);
                                }
                            }
                            return;
                        }
                        double progress = t / 25.0;
                        Location meteorPos = meteorStart.clone().add(0, -25 * progress, 0);
                        world.spawnParticle(Particle.DUST, meteorPos, 5, 0.3, 0.3, 0.3, 0,
                                new Particle.DustOptions(Color.fromRGB(200, 50, 0), 2.5f));
                        world.spawnParticle(Particle.SOUL_FIRE_FLAME, meteorPos, 3, 0.2, 0.2, 0.2, 0.02);
                        // Warning circle
                        for (int i = 0; i < 8; i++) {
                            double a = (Math.PI * 2 / 8) * i;
                            world.spawnParticle(Particle.DUST,
                                    target.clone().add(Math.cos(a) * 2, 0.1, Math.sin(a) * 2),
                                    1, 0, 0, 0, 0, new Particle.DustOptions(Color.RED, 1.0f));
                        }
                        t++;
                    }
                }.runTaskTimer(plugin, 0, 1);
                spawned++;
            }
        }.runTaskTimer(plugin, 0, 5);
    }

    // Ataque Fase 3: Succión hacia el boss
    private void attackVoidPull() {
        if (bossEntity == null)
            return;
        World world = bossEntity.getWorld();
        Location bLoc = bossEntity.getLocation();
        npc.say("\"§5¡ONDA DE VACÍO! ¡RESISTE!\"");

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t >= 40 || !active) {
                    cancel();
                    return;
                }
                // Visuales de succión
                for (int i = 0; i < 20; i++) {
                    double a = (Math.PI * 2 / 20) * i + t * 0.1;
                    double radius = 12.0 - (t * 0.2);
                    if (radius < 1)
                        radius = 1;
                    world.spawnParticle(Particle.DUST,
                            bLoc.clone().add(Math.cos(a) * radius, 1, Math.sin(a) * radius),
                            2, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(80, 0, 120), 1.5f));
                }
                // Pull player towards boss
                if (player.getLocation().distance(bLoc) < 15 && player.getLocation().distance(bLoc) > 2) {
                    Vector pull = bLoc.toVector().subtract(player.getLocation().toVector()).normalize().multiply(0.25);
                    player.setVelocity(player.getVelocity().add(pull));
                }
                // Damage when close
                if (t % 10 == 0 && player.getLocation().distance(bLoc) < 4) {
                    player.damage(5, bossEntity);
                }
                if (t % 5 == 0)
                    world.playSound(bLoc, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.5f, 0.3f + t * 0.02f);
                t++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    // ==================== BOSS DEATH / FREEZE ====================

    public void onPlayerDeath() {
        if (!active || bossEntity == null)
            return;
        freezeTicks = 15 * 20; // 15 segundos
        npc.say("\"§e¡Te han derribado! Tienes 15 segundos para recuperar tus cosas. ¡He paralizado al Titán!\"");
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

        // ANIMACIÓN DE MUERTE ULTRA ÉPICA
        new BukkitRunnable() {
            int phase = 0;

            @Override
            public void run() {
                switch (phase) {
                    case 0:
                        world.playSound(deathLoc, Sound.ENTITY_WARDEN_DEATH, 1.5f, 0.3f);
                        world.spawnParticle(Particle.SOUL, deathLoc.clone().add(0, 2, 0), 80, 2, 3, 2, 0.15);
                        break;
                    case 15:
                        // Implosión con todos los colores
                        for (int r = 15; r > 0; r--) {
                            final int radius = r;
                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                for (int i = 0; i < 24; i++) {
                                    double a = (Math.PI * 2 / 24) * i;
                                    Color c = Color.fromRGB(
                                            (int) (150 + Math.sin(a) * 100),
                                            0,
                                            (int) (150 + Math.cos(a) * 100));
                                    world.spawnParticle(Particle.DUST,
                                            deathLoc.clone().add(Math.cos(a) * radius, 1.5, Math.sin(a) * radius),
                                            2, 0, 0, 0, 0,
                                            new Particle.DustOptions(c, 2.5f));
                                }
                            }, (15 - radius) * 2L);
                        }
                        world.playSound(deathLoc, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.5f, 0.2f);
                        break;
                    case 40:
                        // MEGA explosión
                        world.spawnParticle(Particle.END_ROD, deathLoc.clone().add(0, 3, 0), 10, 0.5, 0.5, 0.5, 0.1);
                        world.spawnParticle(Particle.END_ROD, deathLoc.clone().add(0, 3, 0), 500, 5, 5, 5, 0.8);
                        world.spawnParticle(Particle.TOTEM_OF_UNDYING, deathLoc.clone().add(0, 3, 0), 300, 4, 4, 4,
                                1.0);
                        world.playSound(deathLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.5f);
                        world.playSound(deathLoc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 1.0f);
                        // Columna de luz al cielo
                        for (int y = 0; y < 60; y++) {
                            world.spawnParticle(Particle.END_ROD, deathLoc.clone().add(0, y, 0), 5, 0.3, 0, 0.3, 0.02);
                            if (y % 3 == 0) {
                                int colorIdx = y % 6;
                                Color[] cs = { Color.RED, Color.BLUE, Color.YELLOW, Color.GREEN, Color.WHITE,
                                        Color.PURPLE };
                                world.spawnParticle(Particle.DUST, deathLoc.clone().add(0, y, 0), 3, 0.2, 0, 0.2, 0,
                                        new Particle.DustOptions(cs[colorIdx], 2.0f));
                            }
                        }
                        earthquakeEffect(0.8);
                        launchGroundBlocks(deathLoc, 12, 0.6);
                        break;
                    case 55:
                        // Fuegos artificiales masivos
                        for (int i = 0; i < 8; i++) {
                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                Location fwLoc = deathLoc.clone().add(
                                        (Math.random() - 0.5) * 10, 2 + Math.random() * 5, (Math.random() - 0.5) * 10);
                                Firework fw = (Firework) world.spawnEntity(fwLoc, EntityType.FIREWORK_ROCKET);
                                org.bukkit.inventory.meta.FireworkMeta fwMeta = fw.getFireworkMeta();
                                fwMeta.addEffect(FireworkEffect.builder()
                                        .with(FireworkEffect.Type.BALL_LARGE)
                                        .withColor(Color.PURPLE, Color.FUCHSIA, Color.WHITE, Color.AQUA)
                                        .withFade(Color.fromRGB(100, 0, 150))
                                        .withTrail().withFlicker().build());
                                fwMeta.setPower(0);
                                fw.setFireworkMeta(fwMeta);
                                Bukkit.getScheduler().runTaskLater(plugin, fw::detonate, 3L);
                            }, i * 4L);
                        }
                        break;
                    case 70:
                        player.sendTitle( SmallCaps.convert("§5§l¡¡TITÁN DERROTADO!!"), SmallCaps.convert("§dHas conquistado el Abismo"), 10, 80, 20);
                        npc.say("\"§a§l¡¡INCREÍBLE!! §7Has derrotado al Titán... Eres un verdadero campeón.\"");
                        break;
                    case 100:
                        cancel();
                        if (bossBar != null)
                            bossBar.removeAll();
                        manager.onQuest2BossDefeated(player);
                        return;
                }
                phase++;
            }
        }.runTaskTimer(plugin, 0, 2);
    }

    // ==================== EFECTOS ====================

    private void earthquakeEffect(double intensity) {
        if (!player.isOnline())
            return;
        Vector shake = new Vector(
                (Math.random() - 0.5) * intensity * 0.15,
                Math.random() * intensity * 0.08,
                (Math.random() - 0.5) * intensity * 0.15);
        if (player.isOnGround())
            player.setVelocity(player.getVelocity().add(shake));
        player.playSound(player.getLocation(), Sound.ENTITY_RAVAGER_STEP, (float) intensity, 0.3f);
    }

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
            BlockData blockData = sourceBlock.getBlockData().clone();
            try {
                FallingBlock fb = world.spawnFallingBlock(blockLoc.clone().add(0.5, 1, 0.5), blockData);
                fb.setDropItem(false);
                fb.setHurtEntities(false);
                fb.setGravity(true);
                fb.setVelocity(new Vector(
                        (Math.random() - 0.5) * force,
                        0.3 + Math.random() * force,
                        (Math.random() - 0.5) * force));
                fallingBlocks.add(fb);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!fb.isDead())
                        fb.remove();
                    fallingBlocks.remove(fb);
                }, 60L);
            } catch (Exception ignored) {
            }
        }
    }

    private Location findSafeSpawnLocation() {
        World world = player.getWorld();
        for (int radius = 8; radius <= 15; radius++) {
            for (int angle = 0; angle < 360; angle += 45) {
                double rad = Math.toRadians(angle);
                Location test = new Location(world,
                        arenaCenter.getX() + Math.cos(rad) * radius,
                        arenaCenter.getY(),
                        arenaCenter.getZ() + Math.sin(rad) * radius);
                test.setY(world.getHighestBlockYAt(test) + 1);
                Material below = world.getBlockAt(test.clone().subtract(0, 1, 0)).getType();
                if (below != Material.WATER && below != Material.LAVA && !test.getBlock().getType().isSolid()) {
                    return test;
                }
            }
        }
        return null;
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
                for (int i = 0; i < 20; i++) {
                    double a = angle + (Math.PI * 2 / 20) * i;
                    Location pLoc = arenaCenter.clone().add(Math.cos(a) * ARENA_RADIUS, 0.5,
                            Math.sin(a) * ARENA_RADIUS);
                    pLoc.setY(world.getHighestBlockYAt(pLoc) + 0.5);
                    Color c = currentPhase == 3 ? Color.fromRGB(200, 0, 0)
                            : currentPhase == 2 ? Color.fromRGB(80, 0, 120) : Color.fromRGB(100, 0, 0);
                    world.spawnParticle(Particle.DUST, pLoc, 1, 0, 0.3, 0, 0,
                            new Particle.DustOptions(c, 1.5f));
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
                    player.sendMessage(SmallCaps.convert("§c§l⚠ §fNo puedes huir del Titán."));
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

    // ==================== PATHFINDING ====================

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
        if (feet.getBlock().getType().isSolid() && isBreakable(feet.getBlock().getType()))
            mineBlock(feet.getBlock());
        if (head.getBlock().getType().isSolid() && isBreakable(head.getBlock().getType()))
            mineBlock(head.getBlock());
    }

    private void mineBlock(Block block) {
        if (block.getType() == Material.AIR || block.getType() == Material.BEDROCK)
            return;
        BlockData origData = block.getBlockData().clone();
        Location loc = block.getLocation().add(0.5, 0.5, 0.5);
        block.getWorld().spawnParticle(Particle.BLOCK, loc, 12, 0.3, 0.3, 0.3, 0.1, origData);
        block.getWorld().playSound(loc, Sound.BLOCK_STONE_BREAK, 0.5f, 1.0f);
        block.setType(Material.AIR);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (block.getType() == Material.AIR)
                block.setBlockData(origData);
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
