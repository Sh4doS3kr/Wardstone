package com.moonlight.coreprotect.shadowhunter;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
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

/**
 * Boss de la Misión 6: "La Culpa"
 * La manifestación de la culpa que consumió a Lyra y al Errante.
 * Un Wither Skeleton espectral con ataques basados en soul-fire,
 * visiones fantasmales y ataques emocionales.
 *
 * 2 fases:
 *   Fase 1 (100%-40%): Remordimiento — ataques moderados, soul beams, phantom clones
 *   Fase 2 (40%-0%): Desesperación — ataques agresivos, vision teleport, rain of tears
 */
public class LyraBoss {

    private final CoreProtectPlugin plugin;
    private final ShadowHunterManager manager;
    private final Player player;
    private final ShadowHunterNPC npc;

    private LivingEntity bossEntity;
    private BossBar bossBar;
    private boolean active = false;
    private BukkitTask mainLoop;
    private int tickCount = 0;
    private int phase = 1;

    private static final double MAX_HEALTH = 800.0;

    // Cooldowns
    private int soulBeamCD = 0;
    private int phantomCloneCD = 0;
    private int griefWaveCD = 0;
    private int visionTeleportCD = 0;
    private int tearRainCD = 0;
    private int globalCD = 0;

    // Summoned entities
    private final List<Entity> summonedMobs = new ArrayList<>();

    public LyraBoss(CoreProtectPlugin plugin, ShadowHunterManager manager, Player player, ShadowHunterNPC npc) {
        this.plugin = plugin;
        this.manager = manager;
        this.player = player;
        this.npc = npc;
    }

    public void start() {
        active = true;
        Location spawnLoc = player.getLocation().clone().add(
                player.getLocation().getDirection().multiply(8).setY(0));

        // Find ground
        Block ground = spawnLoc.getWorld().getHighestBlockAt(spawnLoc);
        spawnLoc.setY(ground.getY() + 1);

        World world = spawnLoc.getWorld();

        // Dramatic intro
        player.sendTitle(SmallCaps.convert("§4§l☠ LA CULPA ☠"), SmallCaps.convert("§7\"Todo es tu culpa... todo es MI culpa...\""), 10, 80, 20);
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§4§l☠ §c§lLa Culpa §7emerge de las sombras..."));
        player.sendMessage(SmallCaps.convert("§7§o*La manifestación de todo el dolor que destruyó a Lyra.*"));
        player.sendMessage("");

        world.playSound(spawnLoc, Sound.ENTITY_WITHER_SPAWN, 1.5f, 0.3f);
        world.playSound(spawnLoc, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 1.0f, 0.5f);

        // Spawn animation
        final Location finalSpawn = spawnLoc.clone();

        // Pre-spawn particles
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t >= 60 || !active) {
                    cancel();
                    if (active) spawnBoss(finalSpawn);
                    return;
                }

                // Soul vortex
                for (int i = 0; i < 4; i++) {
                    double angle = (Math.PI * 2 / 4) * i + t * 0.15;
                    double radius = 3.0 - (t / 60.0) * 2.5;
                    double y = t * 0.05;
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME,
                            finalSpawn.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius),
                            2, 0.05, 0.05, 0.05, 0.01);
                }

                // Dark dust
                world.spawnParticle(Particle.DUST, finalSpawn.clone().add(0, 1, 0),
                        5, 1, 1, 1, 0, new Particle.DustOptions(Color.fromRGB(30, 0, 0), 2.0f));

                if (t % 15 == 0) {
                    world.playSound(finalSpawn, Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.3f);
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void spawnBoss(Location loc) {
        World world = loc.getWorld();

        bossEntity = (LivingEntity) world.spawnEntity(loc, EntityType.WITHER_SKELETON);
        bossEntity.setCustomName(SmallCaps.convert("§4§l☠ La Culpa §8— §cRemordimiento Eterno §4§l☠"));
        bossEntity.setCustomNameVisible(true);
        bossEntity.setRemoveWhenFarAway(false);
        bossEntity.setSilent(true);

        try { bossEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(MAX_HEALTH); bossEntity.setHealth(MAX_HEALTH); } catch (Exception e) {}
        try { bossEntity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(14); } catch (Exception e) {}
        try { bossEntity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.28); } catch (Exception e) {}
        try { bossEntity.getAttribute(Attribute.GENERIC_ARMOR).setBaseValue(6); } catch (Exception e) {}
        try { bossEntity.getAttribute(Attribute.GENERIC_ARMOR_TOUGHNESS).setBaseValue(0.0); } catch (Exception e) {}
        try { bossEntity.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(0.0); } catch (Exception e) {}

        bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 999999, 0, false, false));
        bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 999999, 0, false, false));

        // Spectral armor (dark blue/black)
        ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
        LeatherArmorMeta hm = (LeatherArmorMeta) helmet.getItemMeta();
        hm.setColor(Color.fromRGB(20, 10, 40));
        helmet.setItemMeta(hm);
        bossEntity.getEquipment().setHelmet(helmet);

        ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta cm = (LeatherArmorMeta) chest.getItemMeta();
        cm.setColor(Color.fromRGB(15, 5, 30));
        chest.setItemMeta(cm);
        bossEntity.getEquipment().setChestplate(chest);

        ItemStack legs = new ItemStack(Material.LEATHER_LEGGINGS);
        LeatherArmorMeta lm = (LeatherArmorMeta) legs.getItemMeta();
        lm.setColor(Color.fromRGB(10, 0, 20));
        legs.setItemMeta(lm);
        bossEntity.getEquipment().setLeggings(legs);

        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);
        LeatherArmorMeta bm = (LeatherArmorMeta) boots.getItemMeta();
        bm.setColor(Color.fromRGB(5, 0, 10));
        boots.setItemMeta(bm);
        bossEntity.getEquipment().setBoots(boots);

        bossEntity.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
        bossEntity.getEquipment().setHelmetDropChance(0);
        bossEntity.getEquipment().setChestplateDropChance(0);
        bossEntity.getEquipment().setLeggingsDropChance(0);
        bossEntity.getEquipment().setBootsDropChance(0);
        bossEntity.getEquipment().setItemInMainHandDropChance(0);

        // Boss bar
        bossBar = Bukkit.createBossBar(
                SmallCaps.convert("§4§l☠ La Culpa §8— §cFase 1: Remordimiento"),
                BarColor.RED, BarStyle.SEGMENTED_10);
        bossBar.addPlayer(player);
        bossBar.setVisible(true);

        // Mark respawn
        manager.markDirectRespawn(player.getUniqueId(), loc.clone());

        // Start main loop
        startMainLoop();
    }

    private void startMainLoop() {
        mainLoop = new BukkitRunnable() {
            @Override
            public void run() {
                if (!active || bossEntity == null || bossEntity.isDead() || !player.isOnline()) {
                    if (active && (bossEntity == null || bossEntity.isDead())) {
                        onBossDefeated();
                    }
                    cancel();
                    return;
                }

                tickCount++;
                double hpPercent = bossEntity.getHealth() / MAX_HEALTH;

                // Update boss bar
                bossBar.setProgress(Math.max(0, Math.min(1, hpPercent)));
                int hp = (int) bossEntity.getHealth();
                int maxHp = (int) MAX_HEALTH;

                // Phase transition
                int newPhase = hpPercent > 0.4 ? 1 : 2;
                if (newPhase != phase) {
                    phase = newPhase;
                    onPhaseChange();
                }

                if (phase == 1) {
                    bossBar.setTitle(SmallCaps.convert("§4§l☠ La Culpa §8— §cFase 1: Remordimiento §8│ §c" + hp + "§8/§c" + maxHp));
                } else {
                    bossBar.setTitle(SmallCaps.convert("§0§l☠ La Culpa §8— §4§lFase 2: Desesperación §8│ §4" + hp + "§8/§4" + maxHp));
                }

                // Cooldown ticks
                soulBeamCD = Math.max(0, soulBeamCD - 1);
                phantomCloneCD = Math.max(0, phantomCloneCD - 1);
                griefWaveCD = Math.max(0, griefWaveCD - 1);
                visionTeleportCD = Math.max(0, visionTeleportCD - 1);
                tearRainCD = Math.max(0, tearRainCD - 1);
                globalCD = Math.max(0, globalCD - 1);

                // Abilities
                if (tickCount % 4 == 0) {
                    if (phase == 1) tickPhase1();
                    else tickPhase2();
                }

                // Ambient particles
                if (tickCount % 5 == 0) ambientParticles();

                // Target player
                if (tickCount % 20 == 0) targetPlayer();

                // Clean dead summons
                summonedMobs.removeIf(e -> e == null || e.isDead());
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ==================== PHASES ====================

    private void onPhaseChange() {
        World world = bossEntity.getWorld();
        Location loc = bossEntity.getLocation();

        bossBar.setTitle(SmallCaps.convert("§0§l☠ La Culpa §8— §4§lFase 2: Desesperación"));
        bossBar.setColor(BarColor.WHITE);
        bossEntity.setCustomName(SmallCaps.convert("§0§l☠ La Culpa §8— §4Desesperación Infinita §0§l☠"));

        try { bossEntity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(20); } catch (Exception e) {}
        try { bossEntity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.34); } catch (Exception e) {}
        bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999, 1, false, false));

        // EPIC Phase 2 transition particles
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, 2, 0), 150, 4, 3, 4, 0.15);
        world.spawnParticle(Particle.DUST, loc.clone().add(0, 1, 0), 100, 4, 3, 4, 0,
                new Particle.DustOptions(Color.fromRGB(80, 0, 0), 3.5f));
        world.spawnParticle(Particle.SOUL, loc.clone().add(0, 1, 0), 80, 3, 2, 3, 0.1);
        world.spawnParticle(Particle.DRIPPING_WATER, loc.clone().add(0, 3, 0), 60, 2, 2, 2, 0);
        world.spawnParticle(Particle.ENCHANT, loc.clone().add(0, 0.5, 0), 100, 3, 1, 3, 0.2);
        world.spawnParticle(Particle.END_ROD, loc, 10, 0.5, 0.5, 0.5, 0.1);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 2, 0, 0, 0, 0);
        
        // Epic sound effects
        world.playSound(loc, Sound.ENTITY_WITHER_HURT, 2.0f, 0.3f);
        world.playSound(loc, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 1.5f, 0.3f);
        world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.4f);
        world.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 0.5f);

        // Continuous Phase 2 particle aura
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (!active || phase != 2 || bossEntity.isDead()) { cancel(); return; }
                Location bLoc = bossEntity.getLocation();
                
                // Rotating soul fire ring
                for (int i = 0; i < 12; i++) {
                    double angle = (Math.PI * 2 / 12) * i + t * 0.15;
                    double radius = 2.5;
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME,
                            bLoc.clone().add(Math.cos(angle) * radius, 1, Math.sin(angle) * radius),
                            1, 0.1, 0.1, 0.1, 0.01);
                }
                
                // Dripping tears above boss
                if (t % 3 == 0) {
                    world.spawnParticle(Particle.DRIPPING_WATER, bLoc.clone().add(0, 3, 0), 3, 0.5, 0.1, 0.5, 0);
                }
                
                // Dark dust aura
                if (t % 2 == 0) {
                    world.spawnParticle(Particle.DUST, bLoc.clone().add(0, 1.5, 0), 5, 1, 1, 1, 0,
                            new Particle.DustOptions(Color.fromRGB(60, 0, 0), 1.5f));
                }
                
                t++;
            }
        }.runTaskTimer(plugin, 20L, 2L);

        player.sendTitle(SmallCaps.convert("§4§l☠ FASE 2 ☠"), SmallCaps.convert("§c\"¡NO PUEDES HUIR DE LA CULPA!\""), 5, 50, 20);
        player.sendMessage(SmallCaps.convert("§4§l☠ §cLa Culpa se desespera..."));
        player.sendMessage(SmallCaps.convert("§4\"¡TODO ES CULPA TUYA! ¡NUNCA DEBISTE ABANDONARLA!\""));
    }

    private void tickPhase1() {
        if (globalCD > 0) return;
        if (soulBeamCD <= 0 && Math.random() < 0.35) { soulBeam(); soulBeamCD = 28; globalCD = 5; return; }
        if (phantomCloneCD <= 0 && Math.random() < 0.25) { phantomClones(); phantomCloneCD = 60; globalCD = 6; return; }
        if (griefWaveCD <= 0 && Math.random() < 0.3) { griefWave(); griefWaveCD = 40; globalCD = 5; return; }
    }

    private void tickPhase2() {
        if (globalCD > 0) return;
        if (visionTeleportCD <= 0 && Math.random() < 0.3) { visionTeleport(); visionTeleportCD = 48; globalCD = 6; return; }
        if (tearRainCD <= 0 && Math.random() < 0.25) { tearRain(); tearRainCD = 56; globalCD = 6; return; }
        if (soulBeamCD <= 0 && Math.random() < 0.4) { soulBeam(); soulBeamCD = 20; globalCD = 4; return; }
        if (griefWaveCD <= 0 && Math.random() < 0.35) { griefWave(); griefWaveCD = 32; globalCD = 4; return; }
    }

    // ==================== ABILITIES ====================

    /**
     * Soul Beam: A beam of soul fire towards the player.
     */
    private void soulBeam() {
        World world = bossEntity.getWorld();
        Location origin = bossEntity.getLocation().add(0, 1.5, 0);
        Location target = player.getLocation().add(0, 1, 0);
        Vector dir = target.toVector().subtract(origin.toVector()).normalize();

        world.playSound(origin, Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.3f);

        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t >= 20 || !active) { cancel(); return; }

                for (double d = 0; d < 1.5; d += 0.3) {
                    double dist = t * 1.5 + d;
                    Location beamLoc = origin.clone().add(dir.clone().multiply(dist));
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, beamLoc, 3, 0.1, 0.1, 0.1, 0.01);
                    world.spawnParticle(Particle.DUST, beamLoc, 2, 0.1, 0.1, 0.1, 0,
                            new Particle.DustOptions(Color.fromRGB(40, 180, 200), 1.5f));

                    // Check hit
                    if (player.getLocation().add(0, 1, 0).distanceSquared(beamLoc) < 2.5) {
                        player.damage(8, bossEntity);
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, true));
                        world.playSound(beamLoc, Sound.ENTITY_PLAYER_HURT_ON_FIRE, 1.0f, 0.5f);
                        cancel();
                        return;
                    }
                }

                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Phantom Clones: Visual particle effect only, no mob spawning.
     */
    private void phantomClones() {
        World world = bossEntity.getWorld();
        Location center = player.getLocation();

        player.sendMessage(SmallCaps.convert("§4§l☠ §8\"La culpa te rodea... pero no puede tocarte...\""));
        world.playSound(center, Sound.ENTITY_PHANTOM_AMBIENT, 1.5f, 0.5f);

        // Visual phantom clones only - no actual mobs
        for (int i = 0; i < 3; i++) {
            double angle = (Math.PI * 2 / 3) * i;
            Location cloneLoc = center.clone().add(Math.cos(angle) * 5, 0, Math.sin(angle) * 5);
            Block ground = world.getHighestBlockAt(cloneLoc);
            cloneLoc.setY(ground.getY() + 1);

            // Particle effects only
            world.spawnParticle(Particle.SOUL, cloneLoc.add(0, 1, 0), 30, 0.5, 1, 0.5, 0.05);
            world.spawnParticle(Particle.SMOKE, cloneLoc, 20, 0.3, 0.8, 0.3, 0.02);
            world.spawnParticle(Particle.ENCHANT, cloneLoc, 15, 0.4, 0.6, 0.4, 0.1);
        }
    }

    /**
     * Grief Wave: AoE pulse of sadness that damages and slows.
     */
    private void griefWave() {
        World world = bossEntity.getWorld();
        Location center = bossEntity.getLocation();

        world.playSound(center, Sound.ENTITY_WARDEN_HEARTBEAT, 2.0f, 0.3f);
        player.sendMessage(SmallCaps.convert("§4§l☠ §7\"El dolor nunca se olvida...\""));

        new BukkitRunnable() {
            double radius = 0;
            @Override
            public void run() {
                if (radius > 12 || !active) { cancel(); return; }

                for (int i = 0; i < 24; i++) {
                    double angle = (Math.PI * 2 / 24) * i;
                    Location p = center.clone().add(Math.cos(angle) * radius, 0.5, Math.sin(angle) * radius);
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, p, 1, 0, 0, 0, 0);
                    world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(100, 150, 200), 1.2f));
                }

                double dist = player.getLocation().distance(center);
                if (dist >= radius - 1.5 && dist <= radius + 1.5) {
                    player.damage(6, bossEntity);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, false, true));
                }

                radius += 1.5;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private boolean visionActive = false;

    /**
     * Vision Teleport (Phase 2): Teleports the player briefly to a "vision" location,
     * showing them a flash of Lyra's last moments, then returns them.
     */
    private void visionTeleport() {
        if (visionActive) return; // Prevent infinite loop
        visionActive = true;

        World world = bossEntity.getWorld();
        Location originalLoc = player.getLocation().clone();

        player.sendMessage(SmallCaps.convert("§4§l☠ §c\"¡MIRA LO QUE PROVOCASTE!\""));
        world.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 0.3f);

        // Teleport player up high (vision of the edge)
        Location visionLoc = originalLoc.clone().add(0, 30, 0);

        player.teleport(visionLoc);
        // NO slow falling - let them fall naturally
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0, false, true));
        player.sendTitle(SmallCaps.convert("§4§lVISIÓN"), SmallCaps.convert("§c\"Así se sintió Lyra... al borde...\""), 5, 40, 10);

        // Surround with soul particles
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t >= 60 || !active || !player.isOnline()) { cancel(); return; }
                Location pLoc = player.getLocation();
                for (int i = 0; i < 8; i++) {
                    double a = (Math.PI * 2 / 8) * i + t * 0.1;
                    world.spawnParticle(Particle.SOUL,
                            pLoc.clone().add(Math.cos(a) * 2, 0, Math.sin(a) * 2),
                            1, 0, 0.5, 0, 0.02);
                }
                if (t % 20 == 0) {
                    world.playSound(pLoc, Sound.ENTITY_ALLAY_DEATH, 0.5f, 0.3f);
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Damage during vision
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (active && player.isOnline()) {
                player.damage(10, bossEntity);
            }
        }, 30L);

        // Reset vision flag after cooldown
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            visionActive = false;
        }, 100L);
    }

    /**
     * Tear Rain (Phase 2): Rain of soul projectiles from the sky.
     */
    private void tearRain() {
        World world = bossEntity.getWorld();
        Location center = player.getLocation();

        player.sendMessage(SmallCaps.convert("§4§l☠ §b\"Las lágrimas que nunca derramaste por ella...\""));
        world.playSound(center, Sound.WEATHER_RAIN_ABOVE, 2.0f, 0.3f);

        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t >= 60 || !active) { cancel(); return; }

                // Drop 3-4 tear projectiles per tick
                if (t % 3 == 0) {
                    for (int i = 0; i < 3; i++) {
                        double ox = (Math.random() - 0.5) * 12;
                        double oz = (Math.random() - 0.5) * 12;
                        Location dropLoc = center.clone().add(ox, 15, oz);

                        world.spawnParticle(Particle.DRIPPING_WATER, dropLoc, 3, 0.2, 0, 0.2, 0);
                        world.spawnParticle(Particle.SOUL, dropLoc, 1, 0.1, 0, 0.1, 0.02);

                        // Trail falling
                        final Location fall = dropLoc.clone();
                        new BukkitRunnable() {
                            int ft = 0;
                            @Override
                            public void run() {
                                if (ft >= 15 || !active) { cancel(); return; }
                                fall.subtract(0, 1, 0);
                                world.spawnParticle(Particle.DUST, fall, 2, 0.1, 0.1, 0.1, 0,
                                        new Particle.DustOptions(Color.fromRGB(100, 180, 220), 1.0f));

                                if (player.getLocation().distanceSquared(fall) < 4) {
                                    player.damage(4, bossEntity);
                                    cancel();
                                }
                                ft++;
                            }
                        }.runTaskTimer(plugin, 0L, 1L);
                    }
                }

                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ==================== AMBIENT + TARGETING ====================

    private void ambientParticles() {
        if (bossEntity == null || bossEntity.isDead()) return;
        World world = bossEntity.getWorld();
        Location loc = bossEntity.getLocation().add(0, 1.5, 0);

        // Soul fire aura
        for (int i = 0; i < 3; i++) {
            double a = Math.random() * Math.PI * 2;
            double r = 0.5 + Math.random() * 0.5;
            world.spawnParticle(Particle.SOUL_FIRE_FLAME,
                    loc.clone().add(Math.cos(a) * r, Math.random() * 0.5, Math.sin(a) * r),
                    1, 0, 0.1, 0, 0.01);
        }

        // Dark aura in phase 2
        if (phase == 2) {
            world.spawnParticle(Particle.DUST, loc, 3, 0.5, 0.8, 0.5, 0,
                    new Particle.DustOptions(Color.fromRGB(50, 0, 0), 1.5f));
        }
    }

    private void targetPlayer() {
        if (bossEntity == null || bossEntity.isDead()) return;
        if (!player.isOnline()) return;

        if (bossEntity instanceof Mob) {
            ((Mob) bossEntity).setTarget(player);
        }

        // If too far, teleport
        if (bossEntity.getLocation().distanceSquared(player.getLocation()) > 900) {
            Location behind = player.getLocation().clone().subtract(
                    player.getLocation().getDirection().multiply(5).setY(0));
            bossEntity.teleport(behind);
            bossEntity.getWorld().spawnParticle(Particle.SOUL, behind.add(0, 1, 0), 20, 0.5, 1, 0.5, 0.05);
            bossEntity.getWorld().playSound(behind, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);
        }
    }

    // ==================== DEFEAT ====================

    private void onBossDefeated() {
        active = false;
        if (mainLoop != null) mainLoop.cancel();
        if (bossBar != null) { bossBar.removeAll(); bossBar.setVisible(false); }

        World world = player.getWorld();
        Location loc = player.getLocation();

        // Clean summons
        for (Entity e : summonedMobs) {
            if (e != null && !e.isDead()) {
                world.spawnParticle(Particle.SOUL, e.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.05);
                e.remove();
            }
        }
        summonedMobs.clear();

        // Sweep tags
        for (Entity e : world.getEntities()) {
            if (e.getScoreboardTags().contains("lyra_boss_summon")) e.remove();
        }

        // Death animation
        player.sendTitle(SmallCaps.convert("§b§l✦ La Culpa se Desvanece ✦"),
                SmallCaps.convert("§7\"Al fin... puedo descansar...\""), 10, 80, 30);

        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t >= 100) {
                    cancel();

                    // Final emotional scene
                    player.sendMessage("");
                    player.sendMessage(SmallCaps.convert("§b§l✦ §7═══════════════════════════════════"));
                    player.sendMessage(SmallCaps.convert("§7§o*La culpa se disuelve en mil fragmentos de luz...*"));
                    player.sendMessage(SmallCaps.convert("§7§o*Entre las cenizas, una voz suave susurra:*"));
                    player.sendMessage(SmallCaps.convert("§b\"Gracias... por recordarla. Gracias por no olvidar.\""));
                    player.sendMessage(SmallCaps.convert("§7§o*Una lágrima cae al suelo y se convierte en luz.*"));
                    player.sendMessage(SmallCaps.convert("§b§l✦ §7═══════════════════════════════════"));
                    player.sendMessage("");

                    world.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f);
                    world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 2, 0), 200, 3, 3, 3, 1.0);

                    // Notify manager
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        manager.onQuest6BossDefeated(player);
                    }, 60L);
                    return;
                }

                // Soul ascension animation
                double progress = t / 100.0;
                for (int i = 0; i < 4; i++) {
                    double a = (Math.PI * 2 / 4) * i + t * 0.1;
                    double r = 2 - progress * 1.5;
                    double y = progress * 5;
                    world.spawnParticle(Particle.SOUL, loc.clone().add(Math.cos(a) * r, y, Math.sin(a) * r),
                            1, 0, 0.1, 0, 0.02);
                    world.spawnParticle(Particle.END_ROD, loc.clone().add(Math.cos(a + Math.PI) * r, y + 0.5, Math.sin(a + Math.PI) * r),
                            1, 0, 0, 0, 0.05);
                }

                if (t % 12 == 0) {
                    world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 0.5f + (float) progress * 0.8f);
                }

                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ==================== GETTERS ====================

    public boolean isActive() { return active; }

    public boolean isBossEntity(UUID entityId) {
        return bossEntity != null && bossEntity.getUniqueId().equals(entityId);
    }

    public boolean isSummonedMob(UUID entityId) {
        for (Entity e : summonedMobs) {
            if (e != null && e.getUniqueId().equals(entityId)) return true;
        }
        return false;
    }

    public LivingEntity getBossEntity() { return bossEntity; }
    
    public void onPlayerDeath() {
        // Player died during boss fight
        player.sendMessage(SmallCaps.convert("§4§l☠ §c\"Ni siquiera la muerte te librará de mí...\""));
    }
    
    public Location getBossLocation() {
        if (bossEntity != null && !bossEntity.isDead()) {
            return bossEntity.getLocation().clone();
        }
        return player.getLocation().clone();
    }

    // ==================== CLEANUP ====================

    public void cleanup() {
        active = false;
        if (mainLoop != null) mainLoop.cancel();
        if (bossBar != null) { bossBar.removeAll(); bossBar.setVisible(false); }
        if (bossEntity != null && !bossEntity.isDead()) bossEntity.remove();

        for (Entity e : summonedMobs) {
            if (e != null && !e.isDead()) e.remove();
        }
        summonedMobs.clear();

        // Sweep tags
        World world = player.getWorld();
        for (Entity e : world.getEntities()) {
            if (e.getScoreboardTags().contains("lyra_boss_summon")) e.remove();
        }
    }
}
