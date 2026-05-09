package com.moonlight.coreprotect.shadowhunter;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Boss final de la Misión 7: "El Olvido" — entidad primordial que habita el Vientre del Vacío.
 * 
 * Fases:
 * 1. Fase de Despertar (100%-70% HP): Ataques básicos + sombras
 * 2. Fase de Hambre (70%-40% HP): Ataques más agresivos + drenaje de vida
 * 3. Fase de Desesperación (40%-0% HP): Todas las mecánicas + furia
 * 
 * Mecánicas:
 * - Ondas de oscuridad (daño AoE)
 * - Invocación de Olvidados (minions)
 * - Grito del Vacío (stun + daño)
 * - Pulso de Olvido (teleport del jugador random)
 * - Fase final: furia total
 */
public class LabyrinthBoss {

    private final CoreProtectPlugin plugin;
    private final ShadowHunterManager manager;
    private final LabyrinthManager labyrinthManager;
    private final Player player;
    private final Location center;

    // Boss entity
    private Warden bossEntity;
    private UUID bossEntityId;
    private boolean alive = false;
    private boolean active = false;

    // Stats
    private static final double MAX_HEALTH = 500.0;
    private int phase = 1; // 1, 2, 3

    // Tasks
    private BukkitTask mainTask;
    private BukkitTask atmosphereTask;

    // Summoned mobs
    private final List<Entity> summonedMobs = new ArrayList<>();
    private final Set<UUID> summonedMobIds = new HashSet<>();
    private static final int MAX_SUMMONS = 2;

    // Cooldowns
    private long lastDarkWave = 0;
    private long lastSummon = 0;
    private long lastScream = 0;
    private long lastPulse = 0;
    private long lastDialogue = 0;

    // Boss dialogue
    private static final String[][] PHASE_DIALOGUES = {
        { // Phase 1
            "§4§l☠ El Olvido: §c§o\"¿Otro insecto que cree poder desafiarme?\"",
            "§4§l☠ El Olvido: §c§o\"He devorado mundos enteros. Tú no eres nada.\"",
            "§4§l☠ El Olvido: §c§o\"El Errante huyó de mí. Tú no podrás.\"",
            "§4§l☠ El Olvido: §c§o\"Puedo saborear tu miedo...\"",
        },
        { // Phase 2
            "§4§l☠ El Olvido: §c§o\"¡¿Cómo osas herirme?!\"",
            "§4§l☠ El Olvido: §c§o\"Lyra también luchó... antes de ser consumida.\"",
            "§4§l☠ El Olvido: §c§o\"Tu alma será deliciosa...\"",
            "§4§l☠ El Olvido: §c§o\"¡SIENTE EL HAMBRE DEL VACÍO!\"",
        },
        { // Phase 3
            "§4§l☠ El Olvido: §c§o\"¡¡NO!! ¡¡ESTO NO PUEDE SER!!\"",
            "§4§l☠ El Olvido: §c§o\"¡¡YO SOY EL VACÍO!! ¡¡YO SOY ETERNO!!\"",
            "§4§l☠ El Olvido: §c§o\"¡¡TE LLEVARÉ CONMIGO AL ABISMO!!\"",
            "§4§l☠ El Olvido: §c§o\"¡¡SI CAIGO... TODO CAE CONMIGO!!\"",
        }
    };

    private int[] dialogueIndex = {0, 0, 0};

    public LabyrinthBoss(CoreProtectPlugin plugin, ShadowHunterManager manager,
                         LabyrinthManager labyrinthManager, Player player, Location center) {
        this.plugin = plugin;
        this.manager = manager;
        this.labyrinthManager = labyrinthManager;
        this.player = player;
        this.center = center;
    }

    // ==================== START ====================

    public void start() {
        active = true;

        // Spawn boss with dramatic effect
        spawnBossEntity();

        // Start main combat loop
        startMainLoop();
        startAtmosphere();
    }

    private void spawnBossEntity() {
        World w = center.getWorld();
        Location spawnLoc = center.clone().add(0, 0, 5);

        // Pre-spawn effects
        w.spawnParticle(Particle.DUST, spawnLoc.clone().add(0, 2, 0), 100, 2, 3, 2, 0,
                new Particle.DustOptions(Color.fromRGB(30, 0, 0), 4.0f));
        w.spawnParticle(Particle.SCULK_SOUL, spawnLoc.clone().add(0, 1, 0), 50, 2, 2, 2, 0.1);
        w.spawnParticle(Particle.SMOKE, spawnLoc.clone().add(0, 2, 0), 80, 2, 3, 2, 0.15);
        w.playSound(spawnLoc, Sound.ENTITY_WARDEN_EMERGE, 2.0f, 0.3f);

        // Spawn Warden as boss entity
        try {
            bossEntity = (Warden) w.spawnEntity(spawnLoc, EntityType.WARDEN);
            bossEntity.setCustomName("§4§l§k||§r §c§lEL OLVIDO §4§l§k||");
            bossEntity.setCustomNameVisible(true);
            bossEntity.setSilent(false);
            bossEntity.setRemoveWhenFarAway(false);

            // Set health
            bossEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(MAX_HEALTH);
            bossEntity.setHealth(MAX_HEALTH);

            // Buffs
            bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 999999, 0, false, false, false));
            bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 999999, 1, false, false, false));

            bossEntityId = bossEntity.getUniqueId();
            alive = true;

            // Target the player
            bossEntity.setTarget(player);
        } catch (Exception e) {
            plugin.getLogger().severe("[LabyrinthBoss] Failed to spawn boss: " + e.getMessage());
            // Fallback: use Wither Skeleton
            spawnFallbackBoss(spawnLoc);
        }
    }

    private void spawnFallbackBoss(Location spawnLoc) {
        World w = spawnLoc.getWorld();
        WitherSkeleton fallback = (WitherSkeleton) w.spawnEntity(spawnLoc, EntityType.WITHER_SKELETON);
        fallback.setCustomName("§4§l§k||§r §c§lEL OLVIDO §4§l§k||");
        fallback.setCustomNameVisible(true);
        fallback.setSilent(false);
        fallback.setRemoveWhenFarAway(false);
        fallback.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(MAX_HEALTH);
        fallback.setHealth(MAX_HEALTH);
        fallback.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 999999, 0, false, false, false));
        fallback.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 999999, 1, false, false, false));
        fallback.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999, 1, false, false, false));
        fallback.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 999999, 2, false, false, false));
        fallback.getEquipment().clear();
        fallback.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
        fallback.setTarget(player);

        // Store as generic LivingEntity reference
        bossEntityId = fallback.getUniqueId();
        alive = true;
    }

    // ==================== MAIN COMBAT LOOP ====================

    private void startMainLoop() {
        final Random rng = new Random();
        mainTask = new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (!active || !alive || !player.isOnline()) { cancel(); return; }

                // Check if boss is dead
                LivingEntity bossLiving = getBossLiving();
                if (bossLiving == null || bossLiving.isDead()) {
                    onBossDeath();
                    cancel();
                    return;
                }

                tick++;
                double healthPercent = bossLiving.getHealth() / MAX_HEALTH;
                long now = System.currentTimeMillis();

                // Update phase
                int newPhase = healthPercent > 0.7 ? 1 : (healthPercent > 0.4 ? 2 : 3);
                if (newPhase != phase) {
                    phase = newPhase;
                    onPhaseChange(newPhase);
                }

                // Update boss name with health bar
                updateBossName(bossLiving, healthPercent);

                // Ensure boss targets player
                if (tick % 20 == 0 && bossLiving instanceof Mob) {
                    ((Mob) bossLiving).setTarget(player);
                }

                // === ABILITIES ===

                // Dark Wave (AoE damage)
                long waveCd = phase == 3 ? 4000 : (phase == 2 ? 6000 : 8000);
                if (now - lastDarkWave > waveCd && tick % 40 == 0) {
                    castDarkWave(bossLiving, rng);
                    lastDarkWave = now;
                }

                // Summon Olvidados
                long summonCd = phase == 3 ? 60000 : 120000; // 1 minute phase 3, 2 minutes phase 1-2
                if (now - lastSummon > summonCd && tick % 100 == 0 && summonedMobs.size() < MAX_SUMMONS) {
                    summonOlvidados(bossLiving.getLocation(), rng);
                    lastSummon = now;
                }

                // Void Scream (stun)
                long screamCd = phase == 3 ? 10000 : 18000;
                if (now - lastScream > screamCd && tick % 80 == 0 && phase >= 2) {
                    castVoidScream(bossLiving);
                    lastScream = now;
                }

                // Oblivion Pulse (teleport player randomly in boss room)
                if (now - lastPulse > 20000 && tick % 100 == 0 && phase >= 3) {
                    castOblivionPulse(rng);
                    lastPulse = now;
                }

                // Dialogue
                if (now - lastDialogue > 12000 && tick % 60 == 0) {
                    sayDialogue();
                    lastDialogue = now;
                }

                // Boss particles
                if (tick % 3 == 0) {
                    Location bLoc = bossLiving.getLocation().add(0, 1.5, 0);
                    bLoc.getWorld().spawnParticle(Particle.DUST, bLoc, 3, 0.5, 1, 0.5, 0,
                            new Particle.DustOptions(Color.fromRGB(40, 0, 0), 2.0f));
                    if (phase >= 2) {
                        bLoc.getWorld().spawnParticle(Particle.SCULK_SOUL, bLoc, 1, 0.3, 0.5, 0.3, 0.03);
                    }
                    if (phase >= 3) {
                        bLoc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, bLoc, 2, 0.5, 0.5, 0.5, 0.03);
                    }
                }

                // Clean dead summons
                summonedMobs.removeIf(e -> {
                    if (e == null || e.isDead() || !e.isValid()) {
                        if (e != null) summonedMobIds.remove(e.getUniqueId());
                        return true;
                    }
                    return false;
                });

                // Phase 3: regeneration
                if (phase == 3 && tick % 40 == 0) {
                    double heal = Math.min(5.0, MAX_HEALTH - bossLiving.getHealth());
                    if (heal > 0) bossLiving.setHealth(bossLiving.getHealth() + heal);
                }

                // Prevent player from leaving boss room
                if (tick % 10 == 0) {
                    double dist = player.getLocation().distanceSquared(center);
                    if (dist > 400) { // > 20 blocks
                        Vector pull = center.toVector().subtract(player.getLocation().toVector()).normalize().multiply(0.5);
                        player.setVelocity(pull);
                        player.sendMessage(SmallCaps.convert("§4§oEl Olvido no te dejará escapar..."));
                    }
                }
            }
        }.runTaskTimer(plugin, 10L, 1L);
    }

    // ==================== ABILITIES ====================

    private void castDarkWave(LivingEntity bossLiving, Random rng) {
        Location bLoc = bossLiving.getLocation();
        World w = bLoc.getWorld();

        w.playSound(bLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 0.5f);
        player.sendMessage(SmallCaps.convert("§4§l☠ §c§oEl Olvido lanza una Onda de Oscuridad..."));

        // Expanding ring of particles
        new BukkitRunnable() {
            double radius = 1;
            @Override
            public void run() {
                if (radius > 12 || !active) { cancel(); return; }
                for (int i = 0; i < 24; i++) {
                    double angle = (Math.PI * 2 / 24) * i;
                    Location pLoc = bLoc.clone().add(Math.cos(angle) * radius, 0.5, Math.sin(angle) * radius);
                    w.spawnParticle(Particle.DUST, pLoc, 2, 0.1, 0.1, 0.1, 0,
                            new Particle.DustOptions(Color.fromRGB(50, 0, 60), 2.5f));
                    w.spawnParticle(Particle.SCULK_SOUL, pLoc, 1, 0.05, 0.1, 0.05, 0.01);
                }
                // Damage if player is in ring
                double pDist = player.getLocation().distance(bLoc);
                if (Math.abs(pDist - radius) < 2.0) {
                    double dmg = phase == 3 ? 6.0 : (phase == 2 ? 4.0 : 2.5);
                    player.damage(dmg);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, false, false));
                }
                radius += 1.5;
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    private void summonOlvidados(Location loc, Random rng) {
        World w = loc.getWorld();
        player.sendMessage(SmallCaps.convert("§4§l☠ §c§oEl Olvido invoca a los Olvidados..."));
        w.playSound(loc, Sound.ENTITY_WARDEN_EMERGE, 1.0f, 0.5f);

        int count = phase >= 3 ? 3 : 2;
        for (int i = 0; i < count; i++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            Location sLoc = loc.clone().add(Math.cos(angle) * 5, 0, Math.sin(angle) * 5);

            try {
                Zombie minion = (Zombie) w.spawnEntity(sLoc, EntityType.ZOMBIE);
                minion.setCustomName("§8§l§k|§r §4Olvidado §8§l§k|");
                minion.setCustomNameVisible(false);
                minion.setBaby(false);
                minion.getEquipment().clear();
                minion.setSilent(true);
                minion.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(30);
                minion.setHealth(30);
                minion.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999, 1, false, false, false));
                minion.setTarget(player);
                minion.setRemoveWhenFarAway(false);

                summonedMobs.add(minion);
                summonedMobIds.add(minion.getUniqueId());

                w.spawnParticle(Particle.SMOKE, sLoc.clone().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.05);
            } catch (Exception ignored) {}
        }
    }

    private void castVoidScream(LivingEntity bossLiving) {
        Location bLoc = bossLiving.getLocation();
        World w = bLoc.getWorld();

        player.sendMessage(SmallCaps.convert("§4§l☠ §c§o¡EL OLVIDO GRITA!"));
        w.playSound(bLoc, Sound.ENTITY_WARDEN_ROAR, 2.0f, 0.2f);
        w.playSound(bLoc, Sound.ENTITY_GHAST_SCREAM, 1.5f, 0.3f);
        w.playSound(bLoc, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 1.5f, 0.5f);

        // Stun + damage
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 80, 0, false, false, false));
        player.damage(phase == 3 ? 5.0 : 3.0);

        // Particles
        w.spawnParticle(Particle.DUST, bLoc.clone().add(0, 2, 0), 80, 4, 3, 4, 0,
                new Particle.DustOptions(Color.fromRGB(80, 0, 0), 3.0f));
        w.spawnParticle(Particle.SCULK_SOUL, bLoc.clone().add(0, 2, 0), 40, 4, 3, 4, 0.1);

        // Title
        player.sendTitle("§4§l§kXXXX", SmallCaps.convert("§c§oEl grito perfora tu alma..."), 0, 30, 10);
    }

    private void castOblivionPulse(Random rng) {
        player.sendMessage(SmallCaps.convert("§4§l☠ §c§oEl Olvido distorsiona la realidad..."));
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 0.3f);

        // Teleport player to random location in boss room
        double angle = rng.nextDouble() * Math.PI * 2;
        double dist = 3 + rng.nextDouble() * 8;
        Location newLoc = center.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
        newLoc.setYaw(rng.nextFloat() * 360);
        player.teleport(newLoc);

        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20, 0, false, false, false));
        player.playSound(newLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);
        player.getWorld().spawnParticle(Particle.DUST, newLoc.clone().add(0, 1, 0), 30, 1, 1, 1, 0,
                new Particle.DustOptions(Color.fromRGB(60, 0, 80), 2.0f));

        player.sendTitle("", SmallCaps.convert("§4§o¿Dónde estás ahora?"), 0, 30, 5);
    }

    // ==================== PHASE CHANGE ====================

    private void onPhaseChange(int newPhase) {
        World w = center.getWorld();

        if (newPhase == 2) {
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("§4§l☠ §8━━━━━━━━━━━━━━━━━━━━ §4§l☠"));
            player.sendMessage(SmallCaps.convert("§c§l⚠ §4§lFASE 2: EL HAMBRE"));
            player.sendMessage(SmallCaps.convert("§4§oEl Olvido se enfurece. Su hambre crece."));
            player.sendMessage(SmallCaps.convert("§4§l☠ §8━━━━━━━━━━━━━━━━━━━━ §4§l☠"));
            player.sendMessage("");

            player.sendTitle("§4§lFASE 2", SmallCaps.convert("§c§oEl Hambre del Vacío"), 5, 40, 10);
            w.playSound(center, Sound.ENTITY_WARDEN_ROAR, 2.0f, 0.3f);
            w.playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 0.5f);
            w.spawnParticle(Particle.DUST, center.clone().add(0, 3, 0), 100, 5, 3, 5, 0,
                    new Particle.DustOptions(Color.fromRGB(80, 0, 0), 3.0f));

            // Buff boss
            LivingEntity b = getBossLiving();
            if (b != null) {
                b.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999, 0, false, false, false));
                b.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 999999, 1, false, false, false));
            }
        }

        if (newPhase == 3) {
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("§4§l☠ §8━━━━━━━━━━━━━━━━━━━━ §4§l☠"));
            player.sendMessage(SmallCaps.convert("§c§l⚠ §4§lFASE 3: DESESPERACIÓN"));
            player.sendMessage(SmallCaps.convert("§4§l§oEL OLVIDO PIERDE EL CONTROL."));
            player.sendMessage(SmallCaps.convert("§4§l☠ §8━━━━━━━━━━━━━━━━━━━━ §4§l☠"));
            player.sendMessage("");

            player.sendTitle("§4§l§kXX§r §4§lFASE FINAL §4§l§kXX", SmallCaps.convert("§c§oDesesperación Total"), 5, 50, 10);
            w.playSound(center, Sound.ENTITY_WARDEN_ROAR, 2.0f, 0.1f);
            w.playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.3f);
            w.playSound(center, Sound.BLOCK_END_PORTAL_SPAWN, 1.0f, 0.5f);

            w.spawnParticle(Particle.DUST, center.clone().add(0, 3, 0), 150, 6, 4, 6, 0,
                    new Particle.DustOptions(Color.fromRGB(100, 0, 0), 4.0f));
            w.spawnParticle(Particle.SOUL_FIRE_FLAME, center.clone().add(0, 2, 0), 60, 5, 3, 5, 0.1);

            // Full buffs
            LivingEntity b = getBossLiving();
            if (b != null) {
                b.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999, 1, false, false, false));
                b.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 999999, 2, false, false, false));
                b.removePotionEffect(PotionEffectType.RESISTANCE);
            }
        }
    }

    // ==================== BOSS DEATH ====================

    private void onBossDeath() {
        alive = false;
        active = false;

        if (mainTask != null) mainTask.cancel();
        if (atmosphereTask != null) atmosphereTask.cancel();

        // Remove summons
        for (Entity e : summonedMobs) {
            if (e != null && !e.isDead()) {
                e.getWorld().spawnParticle(Particle.SMOKE, e.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.1);
                summonedMobIds.remove(e.getUniqueId());
                e.remove();
            }
        }
        summonedMobs.clear();

        // Death sequence
        World w = center.getWorld();
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§4§l☠ El Olvido: §c§o\"No... no... ¡¡NOOO!!\""));
        player.sendMessage(SmallCaps.convert("§4§l☠ El Olvido: §c§o\"¡¡Yo soy el vacío!! ¡¡Yo soy ETERNO!!\""));
        player.sendMessage(SmallCaps.convert("§4§l☠ El Olvido: §c§o\"Tú... tú no eres... como los demás...\""));
        player.sendMessage(SmallCaps.convert("§8§o...el Olvido se desmorona en ceniza y sombra..."));
        player.sendMessage("");

        w.playSound(center, Sound.ENTITY_WARDEN_DEATH, 2.0f, 0.3f);
        w.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.5f);
        w.playSound(center, Sound.ENTITY_ENDER_DRAGON_DEATH, 1.0f, 0.5f);

        // Massive particle explosion
        w.spawnParticle(Particle.DUST, center.clone().add(0, 3, 0), 300, 5, 5, 5, 0,
                new Particle.DustOptions(Color.fromRGB(80, 0, 0), 4.0f));
        w.spawnParticle(Particle.SMOKE, center.clone().add(0, 3, 0), 200, 5, 5, 5, 0.2);
        w.spawnParticle(Particle.SOUL, center.clone().add(0, 3, 0), 100, 5, 5, 5, 0.15);
        w.spawnParticle(Particle.SCULK_SOUL, center.clone().add(0, 3, 0), 80, 5, 5, 5, 0.1);

        player.sendTitle("§a§l✦ §fVICTORIA §a§l✦",
                SmallCaps.convert("§7§oEl Olvido ha sido destruido."), 10, 80, 20);

        // Notify manager after delay for dramatic effect
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (labyrinthManager != null) labyrinthManager.onBossDefeated();
        }, 80L);
    }

    // ==================== ATMOSPHERE ====================

    private void startAtmosphere() {
        final Random rng = new Random();
        atmosphereTask = new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (!active || !player.isOnline()) { cancel(); return; }
                tick++;

                World w = center.getWorld();

                // Boss room ambient particles
                if (tick % 5 == 0) {
                    for (int i = 0; i < 5; i++) {
                        Location pLoc = center.clone().add(
                                rng.nextGaussian() * 10, rng.nextDouble() * 5 + 1, rng.nextGaussian() * 10);
                        w.spawnParticle(Particle.DUST, pLoc, 1, 0, 0, 0, 0,
                                new Particle.DustOptions(Color.fromRGB(20, 0, 30), 1.5f));
                    }
                    if (rng.nextInt(3) == 0) {
                        w.spawnParticle(Particle.SCULK_SOUL,
                                center.clone().add(rng.nextGaussian() * 8, 0.5, rng.nextGaussian() * 8),
                                1, 0, 0.2, 0, 0.03);
                    }
                }

                // Heartbeat (faster in later phases)
                int heartbeatInterval = phase == 3 ? 20 : (phase == 2 ? 30 : 40);
                if (tick % heartbeatInterval == 0) {
                    player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT,
                            0.5f + phase * 0.15f, 0.5f + phase * 0.15f);
                }
            }
        }.runTaskTimer(plugin, 5L, 1L);
    }

    // ==================== DIALOGUE ====================

    private void sayDialogue() {
        int pi = phase - 1;
        if (pi < 0 || pi >= PHASE_DIALOGUES.length) return;
        int idx = dialogueIndex[pi] % PHASE_DIALOGUES[pi].length;
        player.sendMessage(SmallCaps.convert(PHASE_DIALOGUES[pi][idx]));
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_AMBIENT, 0.8f, 0.4f);
        dialogueIndex[pi]++;
    }

    // ==================== BOSS NAME ====================

    private void updateBossName(LivingEntity bossLiving, double percent) {
        int bars = 30;
        int filled = (int) (percent * bars);
        StringBuilder sb = new StringBuilder();
        sb.append("§4§l§k|§r §c§lEL OLVIDO §4§l§k|§r §8[");
        for (int i = 0; i < bars; i++) {
            if (i < filled) {
                if (percent > 0.7) sb.append("§a|");
                else if (percent > 0.4) sb.append("§e|");
                else sb.append("§c|");
            } else {
                sb.append("§8|");
            }
        }
        sb.append("§8] §f").append(String.format("%.0f", bossLiving.getHealth()))
                .append("§7/§f").append(String.format("%.0f", MAX_HEALTH));
        bossLiving.setCustomName(sb.toString());
    }

    // ==================== HELPERS ====================

    private LivingEntity getBossLiving() {
        if (bossEntity != null && !bossEntity.isDead()) return bossEntity;
        // Try to find by UUID
        if (bossEntityId != null && center.getWorld() != null) {
            for (Entity e : center.getWorld().getEntities()) {
                if (e.getUniqueId().equals(bossEntityId) && e instanceof LivingEntity) {
                    return (LivingEntity) e;
                }
            }
        }
        return null;
    }

    public boolean isBossEntity(UUID entityId) {
        return bossEntityId != null && bossEntityId.equals(entityId);
    }

    public boolean isSummonedMob(UUID entityId) {
        return summonedMobIds.contains(entityId);
    }

    public boolean isAlive() { return alive; }
    public boolean isActive() { return active; }
    public Location getCenter() { return center; }

    // ==================== DAMAGE CALLBACK ====================

    public void onBossDamaged(double damage) {
        // Phase 3: counterattack on hit
        if (phase == 3 && Math.random() < 0.25) {
            player.damage(1.5);
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.5f, 0.8f);
            player.sendMessage(SmallCaps.convert("§4§o...el Olvido devuelve tu dolor..."));
        }
    }

    // ==================== CLEANUP ====================

    public void cleanup() {
        active = false;
        alive = false;
        if (mainTask != null) mainTask.cancel();
        if (atmosphereTask != null) atmosphereTask.cancel();

        for (Entity e : summonedMobs) {
            if (e != null && !e.isDead()) {
                summonedMobIds.remove(e.getUniqueId());
                e.remove();
            }
        }
        summonedMobs.clear();

        LivingEntity b = getBossLiving();
        if (b != null && !b.isDead()) b.remove();
    }
}
