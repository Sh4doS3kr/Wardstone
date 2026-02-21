package com.moonlight.coreprotect.finishers;

import org.bukkit.*;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

public class FinisherEffects {

    private final Plugin plugin;

    public FinisherEffects(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Plays a finisher effect on the victim. Returns the total duration in ticks.
     */
    public int play(FinisherType type, Player victim, Player killer) {
        switch (type) {
            case THUNDER_JUDGMENT:  return playThunderJudgment(victim);
            case VOID_INVOCATION:  return playVoidInvocation(victim);
            case BLOOD_ERUPTION:   return playBloodEruption(victim);
            case SHATTERED_AMETHYST: return playShatteredAmethyst(victim);
            case ORBITAL_STRIKE:   return playOrbitalStrike(victim);
            default: return 40;
        }
    }

    // ========== HELPER: safe location from player ==========
    private Location vLoc(Player victim) {
        return victim.isOnline() ? victim.getLocation() : null;
    }

    // ========================================================================
    //  1. EL JUICIO DEL TRUENO  (~8 seconds = 160 ticks)
    //  Victim levitates slowly, electric sparks swirl, 7 lightning bolts
    //  with increasing intensity, final massive strike + copper explosion
    // ========================================================================
    private int playThunderJudgment(Player victim) {
        final int DURATION = 160; // 8 seconds
        Location origin = victim.getLocation().clone();
        World world = origin.getWorld();
        if (world == null) return 20;

        // Phase 1: Victim starts levitating (ticks 0-40, 2 seconds)
        victim.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 80, 1, false, false, false));
        world.playSound(origin, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 0.5f);

        // Continuous electric aura around victim
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t >= DURATION || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }

                // Swirling sparks
                double angle = t * 0.3;
                double r = 1.2 + Math.sin(t * 0.1) * 0.5;
                double x = Math.cos(angle) * r;
                double z = Math.sin(angle) * r;
                world.spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(x, 1, z), 5, 0.1, 0.3, 0.1, 0.02);
                world.spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(-x, 1.5, -z), 5, 0.1, 0.3, 0.1, 0.02);

                // Cloud base
                if (t % 6 == 0) {
                    world.spawnParticle(Particle.CLOUD, loc.clone().add(0, 0.3, 0), 8, 1.0, 0.2, 1.0, 0.01);
                }

                // Periodic crackle
                if (t % 20 == 0) {
                    world.playSound(loc, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.6f, 2.0f);
                }

                t += 2;
            }
        }.runTaskTimer(plugin, 0, 2);

        // Phase 2: Lightning bolts (7 total, spread over ticks 20-130)
        int[] boltTicks = {20, 40, 55, 70, 85, 100, 115};
        for (int i = 0; i < boltTicks.length; i++) {
            final int boltIndex = i;
            new BukkitRunnable() {
                @Override
                public void run() {
                    Location loc = vLoc(victim);
                    if (loc == null) return;
                    double ox = ThreadLocalRandom.current().nextDouble(-2.0, 2.0);
                    double oz = ThreadLocalRandom.current().nextDouble(-2.0, 2.0);
                    // Later bolts are closer to victim
                    double closeness = 1.0 - (boltIndex / 7.0);
                    Location strike = loc.clone().add(ox * closeness, 0, oz * closeness);
                    world.strikeLightningEffect(strike);
                    world.spawnParticle(Particle.ELECTRIC_SPARK, strike.clone().add(0, 1, 0),
                            30 + boltIndex * 10, 1.0, 1.5, 1.0, 0.05);
                }
            }.runTaskLater(plugin, boltTicks[i]);
        }

        // Phase 3: Remove levitation, add brief stronger levitation for drama
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!victim.isOnline()) return;
                victim.removePotionEffect(PotionEffectType.LEVITATION);
                victim.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 50, 3, false, false, false));
            }
        }.runTaskLater(plugin, 80);

        // Phase 4: FINAL massive strike directly on victim (tick 130)
        new BukkitRunnable() {
            @Override
            public void run() {
                Location loc = vLoc(victim);
                if (loc == null) return;
                world.strikeLightningEffect(loc);
                world.strikeLightningEffect(loc);
                world.spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(0, 1, 0), 150, 2.5, 2.0, 2.5, 0.1);
                world.spawnParticle(Particle.CLOUD, loc.clone().add(0, 0.5, 0), 60, 2.0, 1.0, 2.0, 0.05);
                world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 3, 0.5, 0.5, 0.5, 0);
                world.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 0.7f);

                // Copper blocks flying out
                for (int i = 0; i < 6; i++) {
                    FallingBlock fb = world.spawnFallingBlock(loc.clone().add(0, 1.5, 0),
                            Material.OXIDIZED_COPPER.createBlockData());
                    fb.setDropItem(false);
                    fb.setHurtEntities(false);
                    fb.setGravity(true);
                    double vx = ThreadLocalRandom.current().nextDouble(-0.6, 0.6);
                    double vy = ThreadLocalRandom.current().nextDouble(0.5, 1.0);
                    double vz = ThreadLocalRandom.current().nextDouble(-0.6, 0.6);
                    fb.setVelocity(new Vector(vx, vy, vz));
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (!fb.isDead()) {
                                world.spawnParticle(Particle.ELECTRIC_SPARK, fb.getLocation(), 15, 0.2, 0.2, 0.2, 0.03);
                                fb.remove();
                            }
                        }
                    }.runTaskLater(plugin, 50);
                }
            }
        }.runTaskLater(plugin, 130);

        return DURATION;
    }

    // ========================================================================
    //  2. INVOCACIÓN DEL VACÍO  (~9 seconds = 180 ticks)
    //  Dark vortex forms, victim pulled upward, portal particles spiral,
    //  dragon breath clouds, then implodes into ink
    // ========================================================================
    private int playVoidInvocation(Player victim) {
        final int DURATION = 180; // 9 seconds
        Location origin = victim.getLocation().clone();
        World world = origin.getWorld();
        if (world == null) return 20;

        world.playSound(origin, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.3f);
        world.playSound(origin, Sound.ENTITY_WARDEN_EMERGE, 0.8f, 0.5f);

        // Phase 1: Dark aura builds around victim (ticks 0-60)
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t >= 60 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }

                // Expanding spiral of dragon breath
                for (int i = 0; i < 3; i++) {
                    double angle = (t + i * 40) * 0.15;
                    double radius = 2.0 - (t * 0.02);
                    if (radius < 0.5) radius = 0.5;
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    world.spawnParticle(Particle.DRAGON_BREATH, loc.clone().add(x, 0.5 + t * 0.02, z),
                            4, 0.05, 0.1, 0.05, 0.005);
                }
                world.spawnParticle(Particle.PORTAL, loc.clone().add(0, 1, 0), 10, 0.8, 0.8, 0.8, 0.5);

                if (t % 15 == 0) {
                    world.playSound(loc, Sound.BLOCK_PORTAL_AMBIENT, 0.5f, 0.5f + (t * 0.02f));
                }
                t += 2;
            }
        }.runTaskTimer(plugin, 5, 2);

        // Phase 2: Victim starts levitating at tick 40
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!victim.isOnline()) return;
                victim.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 120, 2, false, false, false));
                Location loc = vLoc(victim);
                if (loc != null) world.playSound(loc, Sound.ENTITY_ENDERMAN_SCREAM, 0.6f, 0.3f);
            }
        }.runTaskLater(plugin, 40);

        // Phase 3: Intense vortex (ticks 60-150)
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t >= 90 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }

                // Multiple spiraling rings
                for (int ring = 0; ring < 4; ring++) {
                    double angle = (t * 0.4) + (ring * Math.PI / 2);
                    double radius = 1.5 - (t * 0.01);
                    if (radius < 0.3) radius = 0.3;
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    double y = ring * 0.4;
                    world.spawnParticle(Particle.DRAGON_BREATH, loc.clone().add(x, y, z),
                            3, 0.05, 0.05, 0.05, 0.002);
                    world.spawnParticle(Particle.SQUID_INK, loc.clone().add(-x, y + 0.5, -z),
                            2, 0.1, 0.1, 0.1, 0.01);
                }

                // Central darkness column
                world.spawnParticle(Particle.PORTAL, loc, 15, 0.3, 1.5, 0.3, 0.8);

                if (t % 10 == 0) {
                    world.playSound(loc, Sound.ENTITY_WARDEN_HEARTBEAT, 0.8f, 0.5f);
                    world.spawnParticle(Particle.SONIC_BOOM, loc, 1, 0, 0, 0, 0);
                }

                t += 2;
            }
        }.runTaskTimer(plugin, 60, 2);

        // Phase 4: IMPLOSION (tick 155)
        new BukkitRunnable() {
            @Override
            public void run() {
                Location loc = vLoc(victim);
                if (loc == null) return;

                // Massive ink explosion
                world.spawnParticle(Particle.SQUID_INK, loc, 120, 3.0, 3.0, 3.0, 0.08);
                world.spawnParticle(Particle.DRAGON_BREATH, loc, 80, 2.5, 2.5, 2.5, 0.05);
                world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 4, 1.0, 1.0, 1.0, 0);
                world.spawnParticle(Particle.PORTAL, loc, 200, 3.0, 3.0, 3.0, 1.0);
                world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.4f);
                world.playSound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 0.5f);
                world.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.3f);
            }
        }.runTaskLater(plugin, 155);

        return DURATION;
    }

    // ========================================================================
    //  3. ERUPCIÓN DE SANGRE  (~8 seconds = 160 ticks)
    //  Victim rises while blood particles rain, ground erupts with red,
    //  massive blood explosion with falling blocks
    // ========================================================================
    private int playBloodEruption(Player victim) {
        final int DURATION = 160; // 8 seconds
        Location origin = victim.getLocation().clone();
        World world = origin.getWorld();
        if (world == null) return 20;

        world.playSound(origin, Sound.ENTITY_WARDEN_EMERGE, 0.7f, 0.8f);

        // Phase 1: Blood pools around victim (ticks 0-40)
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t >= 40 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }

                // Expanding blood ring on the ground
                double radius = 0.5 + (t * 0.06);
                for (int i = 0; i < 8; i++) {
                    double angle = (Math.PI * 2 / 8) * i + (t * 0.1);
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    world.spawnParticle(Particle.DUST, loc.clone().add(x, 0.1, z), 3,
                            0.1, 0.02, 0.1, 0,
                            new Particle.DustOptions(Color.fromRGB(139, 0, 0), 2.0f));
                }
                if (t % 8 == 0) {
                    world.playSound(loc, Sound.BLOCK_HONEY_BLOCK_SLIDE, 0.7f, 0.3f);
                }
                t += 2;
            }
        }.runTaskTimer(plugin, 0, 2);

        // Phase 2: Victim starts levitating, blood fountain rises (tick 30)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!victim.isOnline()) return;
                victim.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 100, 1, false, false, false));
            }
        }.runTaskLater(plugin, 30);

        // Phase 2b: Blood fountain and rain (ticks 30-130)
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t >= 100 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }

                // Rising blood fountain from ground to victim
                double height = Math.min(t * 0.08, 5.0);
                for (double y = 0; y < height; y += 0.5) {
                    world.spawnParticle(Particle.DUST,
                            origin.clone().add(0, y, 0), 5,
                            0.2, 0.1, 0.2, 0,
                            new Particle.DustOptions(Color.fromRGB(180, 0, 0), 1.8f));
                }

                // Blood dripping off victim
                world.spawnParticle(Particle.DUST, loc.clone().add(0, 0.5, 0), 8,
                        0.6, 0.3, 0.6, 0,
                        new Particle.DustOptions(Color.RED, 1.2f));
                world.spawnParticle(Particle.DUST, loc.clone().add(0, -0.3, 0), 5,
                        0.4, 0.8, 0.4, 0,
                        new Particle.DustOptions(Color.fromRGB(100, 0, 0), 1.5f));

                if (t % 12 == 0) {
                    world.playSound(loc, Sound.ENTITY_SLIME_SQUISH, 0.8f, 0.5f);
                }

                t += 2;
            }
        }.runTaskTimer(plugin, 30, 2);

        // Phase 3: Falling blood blocks (ticks 50-130, staggered)
        new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                if (count >= 15 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }
                double ox = ThreadLocalRandom.current().nextDouble(-2.5, 2.5);
                double oz = ThreadLocalRandom.current().nextDouble(-2.5, 2.5);
                Location spawnLoc = loc.clone().add(ox, 4 + ThreadLocalRandom.current().nextDouble(3), oz);
                FallingBlock fb = world.spawnFallingBlock(spawnLoc, Material.RED_CONCRETE_POWDER.createBlockData());
                fb.setDropItem(false);
                fb.setHurtEntities(false);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!fb.isDead()) {
                            world.spawnParticle(Particle.DUST, fb.getLocation(), 12,
                                    0.3, 0.2, 0.3, 0,
                                    new Particle.DustOptions(Color.fromRGB(139, 0, 0), 1.5f));
                            fb.remove();
                        }
                    }
                }.runTaskLater(plugin, 45);
                count++;
            }
        }.runTaskTimer(plugin, 50, 5);

        // Phase 4: BLOOD EXPLOSION (tick 135)
        new BukkitRunnable() {
            @Override
            public void run() {
                Location loc = vLoc(victim);
                if (loc == null) return;
                world.spawnParticle(Particle.DUST, loc, 150, 3.0, 3.0, 3.0, 0,
                        new Particle.DustOptions(Color.RED, 2.5f));
                world.spawnParticle(Particle.DUST, loc, 100, 2.5, 2.5, 2.5, 0,
                        new Particle.DustOptions(Color.fromRGB(139, 0, 0), 2.0f));
                world.spawnParticle(Particle.DUST, loc, 80, 2.0, 2.0, 2.0, 0,
                        new Particle.DustOptions(Color.fromRGB(80, 0, 0), 1.8f));
                world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 2, 0.5, 0.5, 0.5, 0);
                world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.6f);
                world.playSound(loc, Sound.ENTITY_SLIME_SQUISH, 1.2f, 0.3f);
            }
        }.runTaskLater(plugin, 135);

        return DURATION;
    }

    // ========================================================================
    //  4. SHATTERED AMETHYST  (~8 seconds = 160 ticks)
    //  Crystal cage forms around victim, growing shards orbit,
    //  victim encased, then shatters in massive crystal explosion
    // ========================================================================
    private int playShatteredAmethyst(Player victim) {
        final int DURATION = 160; // 8 seconds
        Location origin = victim.getLocation().clone();
        World world = origin.getWorld();
        if (world == null) return 20;

        world.playSound(origin, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 0.5f);

        // Phase 1: Crystal cage forming (ticks 0-60)
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t >= 60 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }

                // Orbiting crystal particles (growing rings)
                int rings = 1 + t / 20;
                for (int ring = 0; ring < rings; ring++) {
                    double angle = (t * 0.25) + (ring * Math.PI / rings);
                    double radius = 2.0 - (t * 0.015);
                    if (radius < 0.8) radius = 0.8;
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    double y = ring * 0.7;
                    world.spawnParticle(Particle.DUST, loc.clone().add(x, y + 0.3, z), 6,
                            0.1, 0.15, 0.1, 0,
                            new Particle.DustOptions(Color.fromRGB(163, 73, 223), 1.8f));
                    world.spawnParticle(Particle.END_ROD, loc.clone().add(-x, y + 0.6, -z), 2,
                            0.05, 0.1, 0.05, 0.01);
                }

                if (t % 10 == 0) {
                    world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 0.8f + (t * 0.01f));
                }
                t += 2;
            }
        }.runTaskTimer(plugin, 0, 2);

        // Phase 2: Victim levitates slightly (tick 30)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!victim.isOnline()) return;
                victim.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 100, 1, false, false, false));
            }
        }.runTaskLater(plugin, 30);

        // Phase 3: Crystal encasement tightens (ticks 60-120)
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t >= 60 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }

                // Tight crystal shell around victim
                for (int i = 0; i < 12; i++) {
                    double angle = (Math.PI * 2 / 12) * i + (t * 0.15);
                    double r = 0.8 - (t * 0.005);
                    if (r < 0.3) r = 0.3;
                    double x = Math.cos(angle) * r;
                    double z = Math.sin(angle) * r;
                    double y = (i % 4) * 0.5;
                    world.spawnParticle(Particle.DUST, loc.clone().add(x, y, z), 3,
                            0.05, 0.05, 0.05, 0,
                            new Particle.DustOptions(Color.fromRGB(200, 150, 255), 1.5f));
                }

                // Humming intensifies
                if (t % 8 == 0) {
                    world.playSound(loc, Sound.BLOCK_AMETHYST_CLUSTER_STEP, 0.6f, 1.0f + (t * 0.015f));
                    world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 1, 0), 5, 0.4, 0.6, 0.4, 0.03);
                }
                t += 2;
            }
        }.runTaskTimer(plugin, 60, 2);

        // Phase 4: SHATTER (tick 125)
        new BukkitRunnable() {
            @Override
            public void run() {
                Location loc = vLoc(victim);
                if (loc == null) return;
                Location center = loc.clone().add(0, 1, 0);

                // Massive crystal explosion
                world.spawnParticle(Particle.DUST, center, 200, 3.0, 3.0, 3.0, 0,
                        new Particle.DustOptions(Color.fromRGB(163, 73, 223), 2.5f));
                world.spawnParticle(Particle.DUST, center, 120, 2.5, 2.5, 2.5, 0,
                        new Particle.DustOptions(Color.fromRGB(200, 150, 255), 2.0f));
                world.spawnParticle(Particle.END_ROD, center, 60, 2.0, 2.0, 2.0, 0.15);
                world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 2, 0.5, 0.5, 0.5, 0);
                world.playSound(loc, Sound.BLOCK_GLASS_BREAK, 1.5f, 0.5f);
                world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.5f, 0.6f);
                world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.5f);

                // Flying crystal shards (12 pieces)
                for (int i = 0; i < 12; i++) {
                    FallingBlock fb = world.spawnFallingBlock(center,
                            Material.AMETHYST_CLUSTER.createBlockData());
                    fb.setDropItem(false);
                    fb.setHurtEntities(false);
                    double vx = ThreadLocalRandom.current().nextDouble(-0.8, 0.8);
                    double vy = ThreadLocalRandom.current().nextDouble(0.4, 1.2);
                    double vz = ThreadLocalRandom.current().nextDouble(-0.8, 0.8);
                    fb.setVelocity(new Vector(vx, vy, vz));
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (!fb.isDead()) {
                                world.spawnParticle(Particle.DUST, fb.getLocation(), 15,
                                        0.2, 0.2, 0.2, 0,
                                        new Particle.DustOptions(Color.fromRGB(163, 73, 223), 1.0f));
                                fb.remove();
                            }
                        }
                    }.runTaskLater(plugin, 50);
                }
            }
        }.runTaskLater(plugin, 125);

        // Phase 5: Lingering sparkles (ticks 130-155)
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t >= 30) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }
                world.spawnParticle(Particle.DUST, loc.clone().add(0, 1, 0),
                        20 - t, 2.0, 2.0, 2.0, 0,
                        new Particle.DustOptions(Color.fromRGB(200, 150, 255), 0.8f));
                world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 1, 0),
                        5, 1.5, 1.5, 1.5, 0.02);
                t += 3;
            }
        }.runTaskTimer(plugin, 130, 3);

        return DURATION;
    }

    // ========================================================================
    //  5. ATAQUE ORBITAL  (~10 seconds = 200 ticks)
    //  Charging phase, target marker, beam descends from sky,
    //  victim rises to meet it, massive impact + fireworks
    // ========================================================================
    private int playOrbitalStrike(Player victim) {
        final int DURATION = 200; // 10 seconds
        Location origin = victim.getLocation().clone();
        World world = origin.getWorld();
        if (world == null) return 20;

        // Phase 1: Target marker on the ground (ticks 0-60)
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t >= 60 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }

                // Rotating crosshair on ground
                double radius = 2.5 - (t * 0.02);
                for (int i = 0; i < 4; i++) {
                    double angle = (Math.PI / 2) * i + (t * 0.1);
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    world.spawnParticle(Particle.DUST, loc.clone().add(x, 0.1, z), 3,
                            0.05, 0.01, 0.05, 0,
                            new Particle.DustOptions(Color.RED, 1.5f));
                }

                // Center glow
                world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 0.2, 0), 3, 0.1, 0.05, 0.1, 0.01);

                if (t % 15 == 0) {
                    world.playSound(loc, Sound.BLOCK_BEACON_AMBIENT, 0.6f, 1.5f + (t * 0.02f));
                }

                t += 2;
            }
        }.runTaskTimer(plugin, 0, 2);

        // Phase 2: Warning beam from sky starts forming (ticks 40-90)
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t >= 50 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }

                // Thin beam forming from sky
                double maxY = 20 + t * 0.3;
                for (double y = 0; y < maxY; y += 1.5) {
                    world.spawnParticle(Particle.END_ROD,
                            loc.clone().add(0, y, 0), 2, 0.05, 0.2, 0.05, 0.005);
                }

                if (t % 10 == 0) {
                    world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.8f);
                }
                t += 2;
            }
        }.runTaskTimer(plugin, 40, 2);

        // Phase 3: Victim starts levitating (tick 60)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!victim.isOnline()) return;
                victim.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 120, 2, false, false, false));
                Location loc = vLoc(victim);
                if (loc != null) {
                    world.playSound(loc, Sound.ITEM_TRIDENT_THUNDER, 0.8f, 1.5f);
                }
            }
        }.runTaskLater(plugin, 60);

        // Phase 4: Thick beam locks on victim (ticks 90-160)
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t >= 70 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }

                // Thick beam column around victim going up
                for (double y = -2; y < 30; y += 0.8) {
                    double wobble = Math.sin((y + t) * 0.3) * 0.15;
                    world.spawnParticle(Particle.END_ROD,
                            loc.clone().add(wobble, y, wobble), 2, 0.08, 0.1, 0.08, 0.005);
                    if (y < 3) {
                        world.spawnParticle(Particle.DUST,
                                loc.clone().add(0, y, 0), 2, 0.15, 0.1, 0.15, 0,
                                new Particle.DustOptions(Color.WHITE, 2.0f));
                    }
                }

                // Rings around beam
                if (t % 4 == 0) {
                    double ringY = (t % 20) * 0.5;
                    for (int i = 0; i < 8; i++) {
                        double angle = (Math.PI * 2 / 8) * i + (t * 0.2);
                        double r = 1.0;
                        world.spawnParticle(Particle.DUST,
                                loc.clone().add(Math.cos(angle) * r, ringY, Math.sin(angle) * r),
                                2, 0.05, 0.05, 0.05, 0,
                                new Particle.DustOptions(Color.YELLOW, 1.5f));
                    }
                }

                if (t % 8 == 0) {
                    world.playSound(loc, Sound.BLOCK_BEACON_AMBIENT, 0.7f, 2.0f);
                }

                t += 2;
            }
        }.runTaskTimer(plugin, 90, 2);

        // Phase 5: IMPACT EXPLOSION (tick 165)
        new BukkitRunnable() {
            @Override
            public void run() {
                Location loc = vLoc(victim);
                if (loc == null) return;

                // Massive impact
                world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 5, 1.0, 1.0, 1.0, 0);
                world.spawnParticle(Particle.END_ROD, loc, 200, 4.0, 4.0, 4.0, 0.2);
                world.spawnParticle(Particle.FLASH, loc, 4, 0, 0, 0, 0);
                world.spawnParticle(Particle.DUST, loc, 100, 3.0, 3.0, 3.0, 0,
                        new Particle.DustOptions(Color.WHITE, 3.0f));
                world.spawnParticle(Particle.DUST, loc, 80, 2.5, 2.5, 2.5, 0,
                        new Particle.DustOptions(Color.YELLOW, 2.0f));
                world.spawnParticle(Particle.DUST, loc, 60, 2.0, 2.0, 2.0, 0,
                        new Particle.DustOptions(Color.ORANGE, 1.5f));
                world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.8f);
                world.playSound(loc, Sound.ITEM_TRIDENT_THUNDER, 1.5f, 0.6f);
            }
        }.runTaskLater(plugin, 165);

        // Phase 6: Fireworks aftermath (ticks 170-195)
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t >= 30) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) loc = origin.clone().add(0, 3, 0);

                double ox = ThreadLocalRandom.current().nextDouble(-3, 3);
                double oy = ThreadLocalRandom.current().nextDouble(0, 4);
                double oz = ThreadLocalRandom.current().nextDouble(-3, 3);
                Location fwLoc = loc.clone().add(ox, oy, oz);

                world.spawnParticle(Particle.END_ROD, fwLoc, 15, 0.5, 0.5, 0.5, 0.08);
                world.spawnParticle(Particle.DUST, fwLoc, 10, 0.3, 0.3, 0.3, 0,
                        new Particle.DustOptions(Color.WHITE, 1.5f));

                if (t % 6 == 0) {
                    world.playSound(fwLoc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.7f, 1.0f + (t * 0.02f));
                    world.playSound(fwLoc, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.5f, 1.2f);
                }
                t += 3;
            }
        }.runTaskTimer(plugin, 170, 3);

        return DURATION;
    }
}
