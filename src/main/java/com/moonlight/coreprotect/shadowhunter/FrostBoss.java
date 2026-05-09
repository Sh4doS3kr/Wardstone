package com.moonlight.coreprotect.shadowhunter;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Boss de la Misión 8: "El Heraldo del Hielo Eterno"
 * 
 * Historia: Tras completar las 7 misiones, el Errante revela una última verdad:
 * antes de que el Vacío existiera, existía el Frío Primordial — una entidad
 * que congelaba dimensiones enteras. El Heraldo del Hielo Eterno fue su campeón,
 * y ha despertado al sentir que alguien reúne las reliquias del Vacío.
 * 
 * 1200 HP, 3 fases, ataques de hielo, mazazo aéreo, invocaciones glaciares.
 * Recompensa: Martillo del Cero Absoluto (Mace con freeze).
 */
public class FrostBoss {

    private final CoreProtectPlugin plugin;
    private final ShadowHunterManager manager;
    private final Player player;
    private final ShadowHunterNPC npc;

    private boolean active = false;
    private Zombie bossEntity;
    private BossBar bossBar;
    private BukkitRunnable mainLoop;
    private int tickCount = 0;
    private int phase = 1;
    private long lastDamageTime;
    private Location arenaCenter;

    private final List<Entity> summonedMobs = new ArrayList<>();
    private final Set<UUID> spawnedEntityIds = new HashSet<>();

    // Ability cooldowns (in ticks)
    private int iceSpikesCD = 0;
    private int blizzardCD = 0;
    private int frostNovaCD = 0;
    private int iceWallCD = 0;
    private int summonCD = 0;
    private int avalancheCD = 0;
    private int cryoBeamCD = 0;
    private int maceSlamCD = 0;
    private int icePrisonCD = 0;
    private int frostMeteorCD = 0;
    private int permafrostCD = 0;
    private int soulDrainCD = 0;
    private boolean isSlamming = false;

    private final String[] phase1Taunts = {
            "§b\"El frío existía antes que el Vacío... antes que todo.\"",
            "§b\"Tus reliquias del Vacío no te salvarán del hielo eterno.\"",
            "§b\"Cada dimensión que toqué... quedó en silencio.\"",
            "§b\"El Errante huyó de mí. Tú no podrás.\""
    };
    private final String[] phase2Taunts = {
            "§3\"¡Siente cómo el frío se mete en tus huesos!\"",
            "§3\"¡El hielo no muere! ¡El hielo ESPERA!\"",
            "§3\"¡Cada cristal que rompes, crecen dos más!\"",
            "§3\"¡Tu calor se apaga... puedo sentirlo!\""
    };
    private final String[] phase3Taunts = {
            "§f\"¡ABSOLUTOCEROABSOLUTOCEROABSOLUTOCERO!\"",
            "§f\"¡TODO SERÁ HIELO! ¡TODO SERÁ SILENCIO!\"",
            "§f\"¡EL FRÍO PRIMORDIAL NO PUEDE SER DESTRUIDO!\"",
            "§3\"No... el calor de tu determinación... es...imposible...\""
    };
    private int tauntIndex = 0;

    public FrostBoss(CoreProtectPlugin plugin, ShadowHunterManager manager, Player player, ShadowHunterNPC npc) {
        this.plugin = plugin;
        this.manager = manager;
        this.player = player;
        this.npc = npc;
    }

    public void start() {
        active = true;
        lastDamageTime = System.currentTimeMillis();

        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§b§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        player.sendMessage(SmallCaps.convert("  §b§l❄ EL HERALDO DEL HIELO ETERNO ❄"));
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("  §7Antes del Vacío, existía el Frío"));
        player.sendMessage(SmallCaps.convert("  §7Primordial. Su campeón ha despertado"));
        player.sendMessage(SmallCaps.convert("  §7al sentir las reliquias reunidas."));
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("  §c§l⚠ ¡2000 HP! ¡3 Fases de hielo!"));
        player.sendMessage(SmallCaps.convert("  §e⚠ Si mueres, respawneas aquí."));
        player.sendMessage(SmallCaps.convert("§b§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        player.sendMessage("");

        player.sendTitle(SmallCaps.convert("§b§l❄ FASE 1 ❄"), SmallCaps.convert("§3El Heraldo despierta..."), 10, 80, 20);
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.8f, 1.2f);

        if (player.getAllowFlight()) {
            player.setAllowFlight(false);
            player.setFlying(false);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            spawnBoss();
            startMainLoop();
        }, 80L);
    }

    private void spawnBoss() {
        Location spawnLoc = player.getLocation().clone().add(
                player.getLocation().getDirection().normalize().multiply(5));
        spawnLoc.setY(player.getLocation().getY());
        arenaCenter = spawnLoc.clone();

        World world = spawnLoc.getWorld();
        world.spawnParticle(Particle.SNOWFLAKE, spawnLoc.clone().add(0, 1, 0), 100, 3, 3, 3, 0.1);
        world.spawnParticle(Particle.DUST, spawnLoc.clone().add(0, 2, 0), 40, 2, 2, 2, 0,
                new Particle.DustOptions(Color.fromRGB(150, 210, 255), 3.0f));
        world.playSound(spawnLoc, Sound.ENTITY_WARDEN_EMERGE, 1.0f, 1.5f);
        world.playSound(spawnLoc, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f);

        bossBar = Bukkit.createBossBar("§b§l❄ Heraldo del Hielo Eterno ❄ §7[FASE 1]",
                BarColor.BLUE, BarStyle.SEGMENTED_10);
        bossBar.addPlayer(player);

        bossEntity = player.getWorld().spawn(spawnLoc, Zombie.class, z -> {
            z.setCustomName(SmallCaps.convert("§b§l❄ Heraldo del Hielo Eterno §b§l❄"));
            z.setCustomNameVisible(true);
            z.setAdult();
            z.getEquipment().setHelmet(new org.bukkit.inventory.ItemStack(org.bukkit.Material.LEATHER_HELMET));
            z.setCanPickupItems(false);
            z.setRemoveWhenFarAway(false);
            z.setPersistent(true);
            z.addScoreboardTag("wardstone_mission_mob");
            z.addScoreboardTag("frost_boss");

            z.getAttribute(Attribute.MAX_HEALTH).setBaseValue(2000);
            z.setHealth(2000);
            z.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(18);
            z.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.32);
            z.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(0.95);
            z.getAttribute(Attribute.ARMOR).setBaseValue(20);

            z.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 99999, 0, false, false));

            // Frost armor
            equipBoss(z);
        });

        spawnedEntityIds.add(bossEntity.getUniqueId());

        // Register for arena respawn
        manager.markDirectRespawn(player.getUniqueId(), arenaCenter.clone());
    }

    private void equipBoss(Zombie z) {
        Color iceColor = Color.fromRGB(150, 210, 255);
        Color frostColor = Color.fromRGB(100, 180, 240);

        ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
        LeatherArmorMeta hm = (LeatherArmorMeta) helmet.getItemMeta();
        hm.setColor(iceColor);
        helmet.setItemMeta(hm);

        ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta cm = (LeatherArmorMeta) chest.getItemMeta();
        cm.setColor(frostColor);
        chest.setItemMeta(cm);

        ItemStack legs = new ItemStack(Material.LEATHER_LEGGINGS);
        LeatherArmorMeta lm = (LeatherArmorMeta) legs.getItemMeta();
        lm.setColor(iceColor);
        legs.setItemMeta(lm);

        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);
        LeatherArmorMeta bm = (LeatherArmorMeta) boots.getItemMeta();
        bm.setColor(frostColor);
        boots.setItemMeta(bm);

        z.getEquipment().setHelmet(helmet);
        z.getEquipment().setChestplate(chest);
        z.getEquipment().setLeggings(legs);
        z.getEquipment().setBoots(boots);
        z.getEquipment().setItemInMainHand(new ItemStack(Material.MACE));

        z.getEquipment().setHelmetDropChance(0);
        z.getEquipment().setChestplateDropChance(0);
        z.getEquipment().setLeggingsDropChance(0);
        z.getEquipment().setBootsDropChance(0);
        z.getEquipment().setItemInMainHandDropChance(0);
    }

    private void startMainLoop() {
        mainLoop = new BukkitRunnable() {
            @Override
            public void run() {
                if (!active || bossEntity == null || bossEntity.isDead() || !player.isOnline()) {
                    cancel();
                    if (active && bossEntity != null && bossEntity.isDead()) {
                        onBossDefeated();
                    }
                    return;
                }

                tickCount++;
                lastDamageTime = System.currentTimeMillis();

                // Update boss bar
                double hp = bossEntity.getHealth();
                double maxHp = bossEntity.getAttribute(Attribute.MAX_HEALTH).getBaseValue();
                bossBar.setProgress(Math.max(0, Math.min(1, hp / maxHp)));

                // Phase transitions
                double hpPercent = hp / maxHp;
                if (phase == 1 && hpPercent <= 0.6) {
                    transitionToPhase2();
                } else if (phase == 2 && hpPercent <= 0.25) {
                    transitionToPhase3();
                }

                // Reduce cooldowns
                if (iceSpikesCD > 0) iceSpikesCD--;
                if (blizzardCD > 0) blizzardCD--;
                if (frostNovaCD > 0) frostNovaCD--;
                if (iceWallCD > 0) iceWallCD--;
                if (summonCD > 0) summonCD--;
                if (avalancheCD > 0) avalancheCD--;
                if (cryoBeamCD > 0) cryoBeamCD--;
                if (maceSlamCD > 0) maceSlamCD--;
                if (icePrisonCD > 0) icePrisonCD--;
                if (frostMeteorCD > 0) frostMeteorCD--;
                if (permafrostCD > 0) permafrostCD--;
                if (soulDrainCD > 0) soulDrainCD--;

                // Ambient frost particles
                Location bossLoc = bossEntity.getLocation().add(0, 1, 0);
                bossLoc.getWorld().spawnParticle(Particle.SNOWFLAKE, bossLoc, 3, 0.5, 0.5, 0.5, 0.02);

                // Chase player
                double dist = bossEntity.getLocation().distance(player.getLocation());
                if (dist > 3) {
                    try {
                        Object pathfinder = bossEntity.getClass().getMethod("getPathfinder").invoke(bossEntity);
                        pathfinder.getClass().getMethod("moveTo", Location.class).invoke(pathfinder, player.getLocation());
                    } catch (Exception e) {
                        Vector dir = player.getLocation().toVector().subtract(bossEntity.getLocation().toVector()).normalize();
                        double speed = phase == 3 ? 0.4 : (phase == 2 ? 0.35 : 0.3);
                        bossEntity.setVelocity(new Vector(dir.getX() * speed, bossEntity.getVelocity().getY(), dir.getZ() * speed));
                    }
                }

                // Abilities based on phase
                executeAbilities();

                // Taunts every 30 seconds
                if (tickCount % 30 == 0) {
                    String[] taunts = phase == 1 ? phase1Taunts : phase == 2 ? phase2Taunts : phase3Taunts;
                    player.sendMessage(SmallCaps.convert(taunts[tauntIndex % taunts.length]));
                    tauntIndex++;
                }

                // Respawn anchor
                manager.markDirectRespawn(player.getUniqueId(), arenaCenter.clone());
            }
        };
        mainLoop.runTaskTimer(plugin, 0, 20); // Every second
    }

    private void executeAbilities() {
        if (isSlamming) return; // Don't use other abilities while mid-slam

        double dist = bossEntity.getLocation().distance(player.getLocation());

        // Mace slam COMBO — signature attack, all phases, priority when in range
        if (maceSlamCD <= 0 && dist < 12 && dist > 2) {
            int comboCount = phase == 3 ? 10 : phase == 2 ? 6 : 3;
            maceSlamCombo(comboCount);
            maceSlamCD = phase == 3 ? 8 : phase == 2 ? 14 : 18;
            return;
        }

        // Phase 1 abilities
        if (iceSpikesCD <= 0 && dist < 10) {
            iceSpikesAttack();
            iceSpikesCD = phase == 3 ? 3 : phase == 2 ? 5 : 8;
        } else if (frostNovaCD <= 0 && dist < 5) {
            frostNova();
            frostNovaCD = phase == 3 ? 6 : phase == 2 ? 9 : 12;
        }

        // Ice Prison — traps the player in ice (all phases)
        if (icePrisonCD <= 0 && dist < 10 && dist > 3) {
            icePrison();
            icePrisonCD = phase == 3 ? 10 : phase == 2 ? 15 : 20;
        }

        // Phase 2+ abilities
        if (phase >= 2) {
            if (blizzardCD <= 0 && dist < 12) {
                blizzard();
                blizzardCD = phase == 3 ? 7 : 12;
            }
            if (summonCD <= 0) {
                summonIceMinions();
                summonCD = phase == 3 ? 10 : 18;
            }
            // Frost Meteor Rain — rains ice meteors around the player
            if (frostMeteorCD <= 0 && dist < 15) {
                frostMeteorRain();
                frostMeteorCD = phase == 3 ? 8 : 14;
            }
            // Soul Drain — drains health from the player and heals the boss
            if (soulDrainCD <= 0 && dist < 8) {
                soulDrain();
                soulDrainCD = phase == 3 ? 10 : 16;
            }
        }

        // Phase 3 abilities
        if (phase >= 3) {
            if (avalancheCD <= 0 && dist < 8) {
                avalanche();
                avalancheCD = 8;
            }
            if (cryoBeamCD <= 0 && dist < 15) {
                cryoBeam();
                cryoBeamCD = 6;
            }
            // Permafrost Aura — constant damage if too close
            if (permafrostCD <= 0 && dist < 4) {
                permafrostAura();
                permafrostCD = 5;
            }
        }
    }

    // ==================== ABILITIES ====================

    /**
     * Mace Slam Combo: Performs multiple rapid slams in sequence.
     * Phase 1: 3 slams, Phase 2: 6 slams, Phase 3: 10 slams.
     * Each slam targets the player's current position.
     */
    private void maceSlamCombo(int count) {
        if (bossEntity == null || bossEntity.isDead()) return;

        String warning = phase == 3 ? "§f§l❄ ¡COMBO DE MAZAZOS DEL CERO ABSOLUTO! §c¡" + count + " IMPACTOS!"
                        : phase == 2 ? "§3§l❄ ¡COMBO GLACIAR! §c¡" + count + " mazazos!"
                        : "§b§l❄ ¡MAZAZOS DE HIELO! §c¡" + count + " impactos!";
        player.sendMessage(SmallCaps.convert(warning));

        // Schedule rapid slams — one every 10 ticks (0.5s) = 5s for 10 slams
        new BukkitRunnable() {
            int slamsDone = 0;
            @Override
            public void run() {
                if (!active || bossEntity == null || bossEntity.isDead() || slamsDone >= count) {
                    cancel();
                    return;
                }
                maceSlam();
                slamsDone++;
            }
        }.runTaskTimer(plugin, 0, 10); // 0.5s between each slam
    }

    /**
     * Mace Slam: The boss launches itself high into the air, then crashes down
     * on the player's position dealing massive AOE damage with frost effects.
     */
    private void maceSlam() {
        if (bossEntity == null || bossEntity.isDead()) return;
        isSlamming = true;

        World world = bossEntity.getWorld();
        Location bossLoc = bossEntity.getLocation().clone();

        // Warning message
        String warning = phase == 3 ? "§f§l❄ ¡MAZAZO DEL CERO ABSOLUTO!" :
                          phase == 2 ? "§3§l❄ ¡MAZAZO GLACIAR!" :
                                       "§b§l❄ ¡MAZAZO DE HIELO!";
        player.sendMessage(SmallCaps.convert(warning + " §c¡ESQUIVA!"));

        // Phase 1: Launch boss UP
        double launchHeight = phase == 3 ? 15 : phase == 2 ? 12 : 10;
        bossEntity.setVelocity(new Vector(0, launchHeight * 0.2, 0));
        bossEntity.setAI(false); // Freeze AI during slam
        bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 0, false, false));

        world.playSound(bossLoc, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.5f, 0.5f);
        world.playSound(bossLoc, Sound.ENTITY_BREEZE_JUMP, 1.0f, 0.7f);

        // Phase 2: Hover at peak, track player, then slam down (after 1.5s)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || bossEntity == null || bossEntity.isDead()) {
                isSlamming = false;
                if (bossEntity != null && !bossEntity.isDead()) bossEntity.setAI(true);
                return;
            }

            // Remove slow falling for the slam
            bossEntity.removePotionEffect(PotionEffectType.SLOW_FALLING);

            // Target: player's current position
            Location targetLoc = player.getLocation().clone();

            // Show warning on ground: red particles in a circle
            for (int angle = 0; angle < 360; angle += 8) {
                double rad = Math.toRadians(angle);
                double slamRadius = phase == 3 ? 5.0 : phase == 2 ? 4.0 : 3.5;
                Location ring = targetLoc.clone().add(Math.cos(rad) * slamRadius, 0.1, Math.sin(rad) * slamRadius);
                world.spawnParticle(Particle.DUST, ring, 2, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 50, 50), 1.5f));
            }
            world.playSound(targetLoc, Sound.BLOCK_NOTE_BLOCK_BASS, 1.5f, 0.5f);

            // Show boss falling trail particles
            Location bossAir = bossEntity.getLocation().clone();
            world.spawnParticle(Particle.SNOWFLAKE, bossAir, 30, 1, 1, 1, 0.1);
            world.spawnParticle(Particle.END_ROD, bossAir, 15, 0.5, 0.5, 0.5, 0.05);

            // Phase 3: Teleport boss above target and slam down (after 0.5s more)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!active || bossEntity == null || bossEntity.isDead()) {
                    isSlamming = false;
                    if (bossEntity != null && !bossEntity.isDead()) bossEntity.setAI(true);
                    return;
                }

                // Teleport boss above the target
                Location aboveTarget = targetLoc.clone().add(0, launchHeight, 0);
                bossEntity.teleport(aboveTarget);
                bossEntity.setFallDistance(0);

                // SLAM DOWN — fast velocity, reapply each tick to fight drag
                bossEntity.setVelocity(new Vector(0, -3.5, 0));

                // Trail particles while falling
                new BukkitRunnable() {
                    int fallTicks = 0;
                    @Override
                    public void run() {
                        if (!active || bossEntity == null || bossEntity.isDead()) {
                            cancel();
                            isSlamming = false;
                            if (bossEntity != null && !bossEntity.isDead()) bossEntity.setAI(true);
                            return;
                        }

                        // Re-apply downward velocity to fight air drag
                        if (fallTicks < 40) {
                            bossEntity.setVelocity(new Vector(0, -3.0, 0));
                        }

                        Location trailLoc = bossEntity.getLocation().clone().add(0, 1, 0);
                        world.spawnParticle(Particle.SNOWFLAKE, trailLoc, 8, 0.3, 0.3, 0.3, 0.05);
                        world.spawnParticle(Particle.DUST, trailLoc, 5, 0.2, 0.2, 0.2, 0,
                                new Particle.DustOptions(Color.fromRGB(150, 210, 255), 2.0f));

                        // Check if boss has landed (on or near ground)
                        if (bossEntity.isOnGround() || bossEntity.getLocation().getY() <= targetLoc.getY() + 1.5) {
                            cancel();
                            slamImpact(targetLoc);
                            return;
                        }

                        fallTicks++;

                        // Safety: if still hasn't landed after 60 ticks (3s), force impact
                        if (fallTicks >= 60) {
                            cancel();
                            bossEntity.teleport(targetLoc.clone().add(0, 0.5, 0));
                            slamImpact(targetLoc);
                        }
                    }
                }.runTaskTimer(plugin, 1, 1);

            }, 10L); // 0.5s after hover
        }, 30L); // 1.5s ascent
    }

    /**
     * Impact effect when the mace slam lands.
     */
    private void slamImpact(Location impactLoc) {
        if (!active || bossEntity == null || bossEntity.isDead()) {
            isSlamming = false;
            return;
        }

        World world = impactLoc.getWorld();
        bossEntity.setAI(true);
        isSlamming = false;

        // Teleport boss to impact point to ensure it lands properly
        bossEntity.teleport(impactLoc.clone().add(0, 0.5, 0));
        bossEntity.setVelocity(new Vector(0, 0, 0));
        bossEntity.setFallDistance(0);

        // Visual: massive frost crater explosion
        double impactRadius = phase == 3 ? 5.0 : phase == 2 ? 4.0 : 3.5;
        world.spawnParticle(Particle.EXPLOSION, impactLoc.clone().add(0, 1, 0), 3, 1, 0.5, 1, 0);
        world.spawnParticle(Particle.SNOWFLAKE, impactLoc.clone().add(0, 0.5, 0), 150, impactRadius, 0.5, impactRadius, 0.15);
        world.spawnParticle(Particle.DUST, impactLoc.clone().add(0, 1, 0), 80, impactRadius, 1, impactRadius, 0,
                new Particle.DustOptions(Color.fromRGB(150, 210, 255), 3.0f));
        world.spawnParticle(Particle.END_ROD, impactLoc.clone().add(0, 0.5, 0), 40, impactRadius * 0.5, 1, impactRadius * 0.5, 0.1);

        // Expanding frost ring on ground
        new BukkitRunnable() {
            int ring = 0;
            @Override
            public void run() {
                if (ring > (int) impactRadius + 2 || !active) { cancel(); return; }
                for (int angle = 0; angle < 360; angle += 6) {
                    double rad = Math.toRadians(angle);
                    Location p = impactLoc.clone().add(Math.cos(rad) * ring, 0.2, Math.sin(rad) * ring);
                    world.spawnParticle(Particle.SNOWFLAKE, p, 1, 0, 0, 0, 0);
                }
                ring++;
            }
        }.runTaskTimer(plugin, 0, 2);

        // Sound effects: big impact
        world.playSound(impactLoc, Sound.ENTITY_IRON_GOLEM_DEATH, 1.5f, 0.4f);
        world.playSound(impactLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.7f);
        world.playSound(impactLoc, Sound.BLOCK_ANVIL_LAND, 1.2f, 0.5f);
        world.playSound(impactLoc, Sound.ENTITY_BREEZE_LAND, 1.0f, 0.6f);

        // Damage calculation — MASSIVE damage in later phases
        double slamDamage = phase == 3 ? 50 : phase == 2 ? 35 : 25;
        double dist = player.getLocation().distance(impactLoc);

        if (dist < impactRadius) {
            // Direct hit or close — full damage + effects
            double falloff = 1.0 - (dist / impactRadius) * 0.3; // 70-100% based on distance
            player.damage(slamDamage * falloff, bossEntity);

            // Knockback — mostly vertical (slam pushes DOWN not sideways)
            Vector kb = player.getLocation().toVector().subtract(impactLoc.toVector()).normalize();
            kb.setX(kb.getX() * 0.3);
            kb.setZ(kb.getZ() * 0.3);
            kb.setY(0.15);
            player.setVelocity(kb);

            // Freeze + slowness
            player.setFreezeTicks(player.getMaxFreezeTicks());
            int slowDuration = phase == 3 ? 120 : phase == 2 ? 80 : 60;
            int slowLevel = phase == 3 ? 4 : phase == 2 ? 3 : 2;
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowDuration, slowLevel, false, true));

            // Screen shake effect via damage tint title
            player.sendTitle("", "§c§l💥", 0, 5, 5);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0f, 0.5f);
        }
    }

    private void iceSpikesAttack() {
        World world = bossEntity.getWorld();
        Location playerLoc = player.getLocation();

        player.sendMessage(SmallCaps.convert("§b§l❄ §3El Heraldo invoca picos de hielo bajo tus pies..."));

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 5 || !active) { cancel(); return; }

                Location target = player.getLocation();
                // Spawn ice spikes (visual + damage)
                for (int i = 0; i < 3; i++) {
                    double angle = Math.random() * Math.PI * 2;
                    double r = Math.random() * 2;
                    Location spikeLoc = target.clone().add(Math.cos(angle) * r, 0, Math.sin(angle) * r);
                    int groundY = world.getHighestBlockYAt(spikeLoc);
                    spikeLoc.setY(groundY + 1);

                    // Particles: ice column
                    for (double y = 0; y < 3; y += 0.3) {
                        world.spawnParticle(Particle.DUST, spikeLoc.clone().add(0, y, 0), 3, 0.1, 0.1, 0.1, 0,
                                new Particle.DustOptions(Color.fromRGB(180, 230, 255), 2.0f));
                    }
                    world.spawnParticle(Particle.SNOWFLAKE, spikeLoc, 10, 0.3, 1.5, 0.3, 0.05);
                }

                // Damage player if nearby
                if (player.getLocation().distance(target) < 2.5) {
                    player.damage(phase == 3 ? 16 : phase == 2 ? 12 : 8, bossEntity);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2, false, true));
                }

                world.playSound(target, Sound.BLOCK_GLASS_BREAK, 0.8f, 1.5f);
                ticks++;
            }
        }.runTaskTimer(plugin, 0, 5);
    }

    private void frostNova() {
        World world = bossEntity.getWorld();
        Location center = bossEntity.getLocation();

        player.sendMessage(SmallCaps.convert("§b§l❄ §fNova de Escarcha — ¡Aléjate!"));

        // Expanding ring of frost
        new BukkitRunnable() {
            int radius = 0;
            @Override
            public void run() {
                if (radius > 6 || !active) { cancel(); return; }

                for (int angle = 0; angle < 360; angle += 10) {
                    double rad = Math.toRadians(angle);
                    Location p = center.clone().add(Math.cos(rad) * radius, 0.5, Math.sin(rad) * radius);
                    world.spawnParticle(Particle.SNOWFLAKE, p, 2, 0.1, 0.2, 0.1, 0.01);
                    world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(200, 240, 255), 1.5f));
                }

                // Damage in ring
                if (player.getLocation().distance(center) <= radius + 1 && player.getLocation().distance(center) >= radius - 1) {
                    player.damage(phase >= 3 ? 20 : phase >= 2 ? 14 : 10, bossEntity);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 3, false, true));
                    player.setFreezeTicks(player.getMaxFreezeTicks());
                }

                world.playSound(center, Sound.BLOCK_POWDER_SNOW_STEP, 0.6f, 0.5f + radius * 0.1f);
                radius++;
            }
        }.runTaskTimer(plugin, 10, 3);
    }

    private void blizzard() {
        World world = bossEntity.getWorld();
        Location center = player.getLocation();

        player.sendMessage(SmallCaps.convert("§3§l❄ §7¡Ventisca! ¡La visión se nubla!"));

        // Blindness + particles
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, false));

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 60 || !active || !player.isOnline()) { cancel(); return; }

                Location loc = player.getLocation();
                world.spawnParticle(Particle.SNOWFLAKE, loc.clone().add(0, 2, 0), 20, 4, 3, 4, 0.1);
                world.spawnParticle(Particle.DUST, loc.clone().add(0, 1.5, 0), 10, 3, 2, 3, 0,
                        new Particle.DustOptions(Color.fromRGB(220, 240, 255), 1.0f));

                if (ticks % 10 == 0) {
                    world.playSound(loc, Sound.ENTITY_PLAYER_HURT_FREEZE, 0.4f, 0.8f);
                }

                // Tick damage
                if (ticks % 10 == 0) {
                    player.damage(phase == 3 ? 6 : phase == 2 ? 4 : 3, bossEntity);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private void summonIceMinions() {
        // Clean old summons
        summonedMobs.removeIf(e -> e == null || e.isDead());
        if (summonedMobs.size() >= 4) return;

        World world = bossEntity.getWorld();
        Location bossLoc = bossEntity.getLocation();
        int count = phase == 3 ? 3 : 2;

        player.sendMessage(SmallCaps.convert("§b§l❄ §7El Heraldo invoca esbirros de hielo..."));

        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2 / count) * i;
            Location spawnLoc = bossLoc.clone().add(Math.cos(angle) * 3, 0, Math.sin(angle) * 3);

            Stray stray = world.spawn(spawnLoc, Stray.class, s -> {
                s.setCustomName("§b❄ Esbirro Glaciar");
                s.setCustomNameVisible(true);
                s.setRemoveWhenFarAway(false);
                s.setPersistent(true);
                s.addScoreboardTag("wardstone_mission_mob");
                s.addScoreboardTag("frost_minion");

                s.getAttribute(Attribute.MAX_HEALTH).setBaseValue(phase == 3 ? 40 : 25);
                s.setHealth(phase == 3 ? 40 : 25);
                s.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(phase == 3 ? 6 : 4);

                s.setTarget(player);
            });

            summonedMobs.add(stray);
            spawnedEntityIds.add(stray.getUniqueId());

            world.spawnParticle(Particle.SNOWFLAKE, spawnLoc.clone().add(0, 1, 0), 20, 0.5, 1, 0.5, 0.05);
            world.playSound(spawnLoc, Sound.ENTITY_STRAY_AMBIENT, 1.0f, 0.8f);
        }
    }

    private void avalanche() {
        World world = bossEntity.getWorld();
        Vector dir = player.getLocation().toVector().subtract(bossEntity.getLocation().toVector()).normalize();
        Location start = bossEntity.getLocation().clone().add(0, 1, 0);

        player.sendMessage(SmallCaps.convert("§f§l❄ §b¡AVALANCHA! ¡ESQUIVA!"));

        new BukkitRunnable() {
            int distance = 0;
            @Override
            public void run() {
                if (distance > 12 || !active) { cancel(); return; }

                Location waveLoc = start.clone().add(dir.clone().multiply(distance));

                // Wide wave of particles
                Vector perp = new Vector(-dir.getZ(), 0, dir.getX());
                for (double w = -3; w <= 3; w += 0.5) {
                    Location p = waveLoc.clone().add(perp.clone().multiply(w));
                    world.spawnParticle(Particle.SNOWFLAKE, p, 5, 0.3, 0.5, 0.3, 0.05);
                    world.spawnParticle(Particle.DUST, p, 3, 0.2, 0.3, 0.2, 0,
                            new Particle.DustOptions(Color.fromRGB(200, 230, 255), 2.0f));
                }

                // Damage if player is in path
                if (player.getLocation().distance(waveLoc) < 3.5) {
                    player.damage(phase == 3 ? 22 : 16, bossEntity);
                    player.setVelocity(dir.clone().multiply(1.8).setY(0.6));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 4, false, true));
                }

                world.playSound(waveLoc, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.6f, 0.5f);
                distance += 2;
            }
        }.runTaskTimer(plugin, 5, 2);
    }

    private void cryoBeam() {
        World world = bossEntity.getWorld();
        Location start = bossEntity.getEyeLocation();
        Vector dir = player.getEyeLocation().toVector().subtract(start.toVector()).normalize();

        player.sendMessage(SmallCaps.convert("§b§l❄ §3Rayo Criogénico — ¡MUÉVETE!"));

        // Warning line
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active) return;

            for (double d = 0; d < 15; d += 0.5) {
                Location point = start.clone().add(dir.clone().multiply(d));
                world.spawnParticle(Particle.DUST, point, 3, 0.1, 0.1, 0.1, 0,
                        new Particle.DustOptions(Color.fromRGB(100, 200, 255), 2.5f));
                world.spawnParticle(Particle.SNOWFLAKE, point, 2, 0.1, 0.1, 0.1, 0.01);

                if (player.getLocation().distance(point) < 1.5) {
                    player.damage(phase == 3 ? 28 : phase == 2 ? 22 : 18, bossEntity);
                    player.setFreezeTicks(player.getMaxFreezeTicks());
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 4, false, true));
                    break;
                }
            }

            world.playSound(start, Sound.ENTITY_GUARDIAN_ATTACK, 1.0f, 1.5f);
            world.playSound(start, Sound.BLOCK_GLASS_BREAK, 0.8f, 0.8f);
        }, 15L); // 0.75s delay for dodge window
    }

    /**
     * Ice Prison: Surrounds the player with ice blocks, trapping them momentarily.
     */
    private void icePrison() {
        if (!active || bossEntity == null || bossEntity.isDead()) return;
        World world = bossEntity.getWorld();
        Location loc = player.getLocation().clone();

        player.sendMessage(SmallCaps.convert("§b§l❄ §f¡PRISIÓN DE HIELO! §7¡Estás atrapado!"));
        world.playSound(loc, Sound.BLOCK_GLASS_PLACE, 1.5f, 0.5f);

        // Place ice blocks around the player
        List<Location> iceBlocks = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                for (int dy = 0; dy <= 2; dy++) {
                    Location iceLoc = loc.clone().add(dx, dy, dz);
                    if (iceLoc.getBlock().getType() == Material.AIR) {
                        iceLoc.getBlock().setType(Material.ICE);
                        iceBlocks.add(iceLoc);
                    }
                }
            }
        }
        // Ceiling
        Location ceil = loc.clone().add(0, 2, 0);
        if (ceil.getBlock().getType() == Material.AIR) {
            ceil.getBlock().setType(Material.ICE);
            iceBlocks.add(ceil);
        }

        // Damage while trapped
        player.damage(phase == 3 ? 15 : phase == 2 ? 10 : 6, bossEntity);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 5, false, true));
        player.setFreezeTicks(player.getMaxFreezeTicks());

        // Remove ice after delay
        int duration = phase == 3 ? 50 : phase == 2 ? 40 : 30;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Location bl : iceBlocks) {
                if (bl.getBlock().getType() == Material.ICE) {
                    bl.getBlock().setType(Material.AIR);
                    world.spawnParticle(Particle.SNOWFLAKE, bl.clone().add(0.5, 0.5, 0.5), 5, 0.2, 0.2, 0.2, 0.05);
                }
            }
            world.playSound(loc, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.5f);
        }, duration);
    }

    /**
     * Frost Meteor Rain: Rains ice meteors from the sky around the player.
     */
    private void frostMeteorRain() {
        if (!active || bossEntity == null || bossEntity.isDead()) return;
        World world = bossEntity.getWorld();

        player.sendMessage(SmallCaps.convert("§3§l❄ §c¡LLUVIA DE METEOROS DE HIELO! §7¡No pares de moverte!"));
        world.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.5f);

        int meteorCount = phase == 3 ? 20 : phase == 2 ? 12 : 8;

        new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                if (count >= meteorCount || !active || bossEntity == null || bossEntity.isDead()) {
                    cancel();
                    return;
                }

                // Target near player with random offset
                Location target = player.getLocation().clone().add(
                        (Math.random() - 0.5) * 8,
                        0,
                        (Math.random() - 0.5) * 8);
                Location sky = target.clone().add(0, 15, 0);

                // Trail from sky to ground
                new BukkitRunnable() {
                    double y = sky.getY();
                    @Override
                    public void run() {
                        if (y <= target.getY() || !active) {
                            cancel();
                            // Impact
                            world.spawnParticle(Particle.EXPLOSION, target.clone().add(0, 1, 0), 1, 0, 0, 0, 0);
                            world.spawnParticle(Particle.SNOWFLAKE, target.clone().add(0, 0.5, 0), 30, 2, 0.5, 2, 0.1);
                            world.playSound(target, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.2f);

                            if (player.getLocation().distance(target) < 3.0) {
                                player.damage(phase == 3 ? 18 : phase == 2 ? 14 : 10, bossEntity);
                                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 2, false, true));
                            }
                            return;
                        }
                        Location trail = target.clone();
                        trail.setY(y);
                        world.spawnParticle(Particle.DUST, trail, 5, 0.2, 0.2, 0.2, 0,
                                new Particle.DustOptions(Color.fromRGB(100, 180, 255), 2.5f));
                        world.spawnParticle(Particle.SNOWFLAKE, trail, 3, 0.1, 0.1, 0.1, 0.02);
                        y -= 3;
                    }
                }.runTaskTimer(plugin, 0, 1);

                count++;
            }
        }.runTaskTimer(plugin, 0, phase == 3 ? 3 : 5);
    }

    /**
     * Soul Drain: Channels energy from the player, dealing damage and healing the boss.
     */
    private void soulDrain() {
        if (!active || bossEntity == null || bossEntity.isDead()) return;
        World world = bossEntity.getWorld();

        player.sendMessage(SmallCaps.convert("§5§l❄ §d¡DRENAJE DE ALMA! §7¡El Heraldo absorbe tu vida!"));
        world.playSound(bossEntity.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 0.5f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 8 || !active || bossEntity == null || bossEntity.isDead() || !player.isOnline()) {
                    cancel();
                    return;
                }

                Location bossLoc = bossEntity.getLocation().add(0, 1.5, 0);
                Location playerLoc = player.getLocation().add(0, 1, 0);

                // Beam particles from player to boss
                Vector dir = bossLoc.toVector().subtract(playerLoc.toVector());
                double len = dir.length();
                dir.normalize();
                for (double d = 0; d < len; d += 0.5) {
                    Location point = playerLoc.clone().add(dir.clone().multiply(d));
                    world.spawnParticle(Particle.DUST, point, 2, 0.05, 0.05, 0.05, 0,
                            new Particle.DustOptions(Color.fromRGB(200, 50, 50), 1.5f));
                    world.spawnParticle(Particle.DUST, point, 1, 0.05, 0.05, 0.05, 0,
                            new Particle.DustOptions(Color.fromRGB(100, 0, 200), 1.0f));
                }

                // Damage player, heal boss
                double drainDmg = phase == 3 ? 8 : phase == 2 ? 6 : 4;
                player.damage(drainDmg, bossEntity);

                double maxHp = bossEntity.getAttribute(Attribute.MAX_HEALTH).getBaseValue();
                double newHp = Math.min(maxHp, bossEntity.getHealth() + drainDmg * 2);
                bossEntity.setHealth(newHp);

                world.playSound(playerLoc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4f, 0.5f);
                player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 1, false, true));

                ticks++;
            }
        }.runTaskTimer(plugin, 0, 5); // every 0.25s for 2s
    }

    /**
     * Permafrost Aura: Deals heavy constant damage if the player is too close to the boss.
     * Phase 3 only.
     */
    private void permafrostAura() {
        if (!active || bossEntity == null || bossEntity.isDead()) return;
        World world = bossEntity.getWorld();
        Location bossLoc = bossEntity.getLocation();

        // Visual: frost aura pulse
        for (int angle = 0; angle < 360; angle += 15) {
            double rad = Math.toRadians(angle);
            for (double r = 1; r <= 4; r += 0.5) {
                Location p = bossLoc.clone().add(Math.cos(rad) * r, 0.3, Math.sin(rad) * r);
                world.spawnParticle(Particle.SNOWFLAKE, p, 1, 0, 0, 0, 0);
            }
        }
        world.spawnParticle(Particle.DUST, bossLoc.clone().add(0, 1, 0), 30, 3, 1, 3, 0,
                new Particle.DustOptions(Color.fromRGB(220, 240, 255), 2.0f));

        double dist = player.getLocation().distance(bossLoc);
        if (dist < 4) {
            player.damage(15, bossEntity);
            player.setFreezeTicks(player.getMaxFreezeTicks());
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 5, false, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 60, 2, false, true));
            world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0f, 0.3f);
        }
    }

    // ==================== PHASE TRANSITIONS ====================

    private void transitionToPhase2() {
        phase = 2;
        bossBar.setTitle("§3§l❄ Heraldo del Hielo Eterno ❄ §7[FASE 2]");
        bossBar.setColor(BarColor.WHITE);

        player.sendTitle(SmallCaps.convert("§3§l❄ FASE 2 ❄"), SmallCaps.convert("§7El hielo se intensifica..."), 10, 60, 20);
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_ROAR, 0.6f, 1.5f);

        World world = bossEntity.getWorld();
        Location loc = bossEntity.getLocation();
        world.spawnParticle(Particle.SNOWFLAKE, loc.clone().add(0, 2, 0), 100, 3, 3, 3, 0.15);
        world.playSound(loc, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.3f);

        // Speed up boss
        bossEntity.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.36);
        bossEntity.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(22);

        player.sendMessage(SmallCaps.convert("§3§l❄ §7El Heraldo se enfurece. La temperatura baja aún más..."));
    }

    private void transitionToPhase3() {
        phase = 3;
        bossBar.setTitle("§f§l❄ CERO ABSOLUTO ❄ §c[FASE FINAL]");
        bossBar.setColor(BarColor.RED);

        player.sendTitle(SmallCaps.convert("§f§l❄ CERO ABSOLUTO ❄"), SmallCaps.convert("§c¡Fase final!"), 10, 60, 20);
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.5f);

        World world = bossEntity.getWorld();
        Location loc = bossEntity.getLocation();
        world.spawnParticle(Particle.SNOWFLAKE, loc.clone().add(0, 3, 0), 200, 5, 5, 5, 0.2);
        world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 2, 0), 60, 3, 3, 3, 0.15);
        world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.5f);

        // Boss becomes extremely fast and strong
        bossEntity.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.42);
        bossEntity.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(28);
        bossEntity.getAttribute(Attribute.ARMOR).setBaseValue(25);
        bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 99999, 2, false, false));
        bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 99999, 1, false, false));

        player.sendMessage(SmallCaps.convert("§f§l❄ §c¡CERO ABSOLUTO! ¡El Heraldo desata todo su poder!"));
    }

    // ==================== BOSS DEFEATED ====================

    private void onBossDefeated() {
        active = false;

        // Cancel main loop
        if (mainLoop != null) {
            try { mainLoop.cancel(); } catch (Exception ignored) {}
        }

        // Remove boss bar
        if (bossBar != null) {
            bossBar.removeAll();
        }

        // Cleanup summons
        for (Entity e : summonedMobs) {
            if (e != null && !e.isDead()) e.remove();
        }
        summonedMobs.clear();

        // Dramatic death scene
        if (player.isOnline()) {
            World world = player.getWorld();
            Location loc = bossEntity != null ? bossEntity.getLocation() : player.getLocation();

            // Ice explosion
            world.spawnParticle(Particle.SNOWFLAKE, loc.clone().add(0, 2, 0), 300, 5, 5, 5, 0.3);
            world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 3, 0), 100, 3, 3, 3, 0.2);
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 2, 0), 150, 2, 2, 2, 0.8);
            world.spawnParticle(Particle.DUST, loc.clone().add(0, 2, 0), 80, 3, 3, 3, 0,
                    new Particle.DustOptions(Color.fromRGB(150, 220, 255), 3.0f));

            world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.5f);
            world.playSound(loc, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f);
            world.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.5f);

            player.sendTitle(SmallCaps.convert("§b§l❄ DERROTADO ❄"), SmallCaps.convert("§3El Frío Primordial se desvanece..."), 10, 100, 30);

            // Dialogue after defeat
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                player.sendMessage("");
                player.sendMessage(SmallCaps.convert("§b§l❄ §3El Heraldo se desmorona en cristales de hielo."));
                player.sendMessage(SmallCaps.convert("§3§o\"El frío... se va. Por primera vez en eones..."));
                player.sendMessage(SmallCaps.convert("§3§o siento... calor. Gracias, guerrero.\""));
                player.sendMessage(SmallCaps.convert("§7§o*De los restos cristalinos del Heraldo,"));
                player.sendMessage(SmallCaps.convert("§7§o emerge un martillo brillante con el poder"));
                player.sendMessage(SmallCaps.convert("§7§o del hielo eterno forjado en su interior.*"));
                player.sendMessage("");
            }, 60L);

            // Complete quest and give reward
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                manager.onQuest8BossDefeated(player);
            }, 140L);
        }
    }

    // ==================== UTILITY ====================

    public boolean isActive() { return active; }
    public boolean isBossEntity(UUID id) { return bossEntity != null && bossEntity.getUniqueId().equals(id); }
    public boolean isSummonedMob(UUID id) { return spawnedEntityIds.contains(id); }
    public Location getArenaCenter() { return arenaCenter; }

    public void collectProtectedEntities(List<Entity> list) {
        if (bossEntity != null && !bossEntity.isDead()) list.add(bossEntity);
        for (Entity e : summonedMobs) {
            if (e != null && !e.isDead()) list.add(e);
        }
    }

    public void cleanup() {
        active = false;
        if (mainLoop != null) {
            try { mainLoop.cancel(); } catch (Exception ignored) {}
        }
        if (bossBar != null) {
            bossBar.removeAll();
        }
        if (bossEntity != null && !bossEntity.isDead()) {
            bossEntity.remove();
        }
        for (Entity e : summonedMobs) {
            if (e != null && !e.isDead()) e.remove();
        }
        summonedMobs.clear();
        spawnedEntityIds.clear();
    }
}
