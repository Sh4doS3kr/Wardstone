package com.moonlight.coreprotect.finishers;

import org.bukkit.*;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

public class FinisherEffects {

    private final Plugin plugin;

    public FinisherEffects(Plugin plugin) {
        this.plugin = plugin;
    }

    public void play(FinisherType type, Location loc, Player killer) {
        switch (type) {
            case THUNDER_JUDGMENT:
                playThunderJudgment(loc);
                break;
            case VOID_INVOCATION:
                playVoidInvocation(loc);
                break;
            case BLOOD_ERUPTION:
                playBloodEruption(loc);
                break;
            case SHATTERED_AMETHYST:
                playShatteredAmethyst(loc);
                break;
            case ORBITAL_STRIKE:
                playOrbitalStrike(loc);
                break;
        }
    }

    // ========== 1. EL JUICIO DEL TRUENO ==========
    private void playThunderJudgment(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;

        // 3 lightning bolts staggered
        for (int i = 0; i < 3; i++) {
            final int delay = i * 8; // 0.4s apart
            new BukkitRunnable() {
                @Override
                public void run() {
                    double ox = ThreadLocalRandom.current().nextDouble(-1.0, 1.0);
                    double oz = ThreadLocalRandom.current().nextDouble(-1.0, 1.0);
                    Location strike = loc.clone().add(ox, 0, oz);
                    world.strikeLightningEffect(strike);
                }
            }.runTaskLater(plugin, delay);
        }

        // Electric spark particles after bolts
        new BukkitRunnable() {
            @Override
            public void run() {
                world.spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(0, 1, 0), 80, 1.5, 1.0, 1.5, 0.05);
                world.spawnParticle(Particle.CLOUD, loc.clone().add(0, 0.5, 0), 30, 1.0, 0.5, 1.0, 0.02);
                world.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.2f);
            }
        }.runTaskLater(plugin, 28);

        // Copper block that vanishes
        new BukkitRunnable() {
            @Override
            public void run() {
                Location blockLoc = loc.clone().add(0, 0.5, 0);
                FallingBlock fb = world.spawnFallingBlock(blockLoc, Material.OXIDIZED_COPPER.createBlockData());
                fb.setDropItem(false);
                fb.setVelocity(new Vector(0, 0.15, 0));
                fb.setGravity(false);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!fb.isDead()) {
                            world.spawnParticle(Particle.ELECTRIC_SPARK, fb.getLocation(), 20, 0.3, 0.3, 0.3, 0.02);
                            fb.remove();
                        }
                    }
                }.runTaskLater(plugin, 40);
            }
        }.runTaskLater(plugin, 30);
    }

    // ========== 2. INVOCACIÓN DEL VACÍO ==========
    private void playVoidInvocation(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;

        world.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);

        // Spinning vortex particles
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 40) {
                    // Final explosion
                    world.spawnParticle(Particle.SQUID_INK, loc.clone().add(0, 2, 0), 60, 1.5, 1.5, 1.5, 0.05);
                    world.spawnParticle(Particle.EXPLOSION_EMITTER, loc.clone().add(0, 2, 0), 2, 0, 0, 0, 0);
                    world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 0.6f);
                    cancel();
                    return;
                }

                double angle = ticks * 0.5;
                double radius = 1.5 - (ticks * 0.03);
                if (radius < 0.3) radius = 0.3;
                double x = Math.cos(angle) * radius;
                double z = Math.sin(angle) * radius;
                double y = ticks * 0.075;

                Location partLoc = loc.clone().add(x, y, z);
                world.spawnParticle(Particle.DRAGON_BREATH, partLoc, 5, 0.1, 0.1, 0.1, 0.01);
                world.spawnParticle(Particle.PORTAL, partLoc, 8, 0.2, 0.2, 0.2, 0.3);

                if (ticks % 10 == 0) {
                    world.playSound(loc, Sound.BLOCK_PORTAL_AMBIENT, 0.4f, 1.5f);
                }

                ticks += 2;
            }
        }.runTaskTimer(plugin, 5, 2);
    }

    // ========== 3. ERUPCIÓN DE SANGRE ==========
    private void playBloodEruption(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;

        world.playSound(loc, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.0f, 0.7f);

        // Rising blood fountain
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 30) {
                    cancel();
                    return;
                }

                double y = ticks * 0.15;
                world.spawnParticle(Particle.DUST, loc.clone().add(0, y, 0), 15,
                        0.3, 0.2, 0.3, 0,
                        new Particle.DustOptions(Color.fromRGB(139, 0, 0), 1.5f));
                world.spawnParticle(Particle.DUST, loc.clone().add(0, y, 0), 10,
                        0.5, 0.3, 0.5, 0,
                        new Particle.DustOptions(Color.RED, 1.0f));

                ticks += 2;
            }
        }.runTaskTimer(plugin, 0, 2);

        // Falling blood drops (falling blocks)
        new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                if (count >= 6) {
                    cancel();
                    return;
                }
                double ox = ThreadLocalRandom.current().nextDouble(-1.5, 1.5);
                double oz = ThreadLocalRandom.current().nextDouble(-1.5, 1.5);
                Location spawnLoc = loc.clone().add(ox, 3 + ThreadLocalRandom.current().nextDouble(2), oz);
                FallingBlock fb = world.spawnFallingBlock(spawnLoc, Material.RED_CONCRETE_POWDER.createBlockData());
                fb.setDropItem(false);
                fb.setHurtEntities(false);
                // Remove after 2 seconds if still alive
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!fb.isDead()) {
                            world.spawnParticle(Particle.DUST, fb.getLocation(), 8,
                                    0.2, 0.1, 0.2, 0,
                                    new Particle.DustOptions(Color.fromRGB(139, 0, 0), 1.2f));
                            fb.remove();
                        }
                    }
                }.runTaskLater(plugin, 40);
                count++;
            }
        }.runTaskTimer(plugin, 5, 6);

        // Splatter sound
        new BukkitRunnable() {
            @Override
            public void run() {
                world.playSound(loc, Sound.ENTITY_SLIME_SQUISH, 1.0f, 0.5f);
            }
        }.runTaskLater(plugin, 25);
    }

    // ========== 4. SHATTERED AMETHYST ==========
    private void playShatteredAmethyst(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;

        world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.0f, 0.8f);
        world.playSound(loc, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.5f);

        // Crystal explosion particles
        Location center = loc.clone().add(0, 1, 0);
        world.spawnParticle(Particle.DUST, center, 60,
                1.0, 1.0, 1.0, 0,
                new Particle.DustOptions(Color.fromRGB(163, 73, 223), 1.8f));
        world.spawnParticle(Particle.DUST, center, 40,
                0.8, 0.8, 0.8, 0,
                new Particle.DustOptions(Color.fromRGB(200, 150, 255), 1.2f));
        world.spawnParticle(Particle.END_ROD, center, 25, 0.5, 0.5, 0.5, 0.08);

        // Flying amethyst shards (falling blocks)
        for (int i = 0; i < 8; i++) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    FallingBlock fb = world.spawnFallingBlock(center, Material.AMETHYST_CLUSTER.createBlockData());
                    fb.setDropItem(false);
                    fb.setHurtEntities(false);
                    double vx = ThreadLocalRandom.current().nextDouble(-0.5, 0.5);
                    double vy = ThreadLocalRandom.current().nextDouble(0.3, 0.7);
                    double vz = ThreadLocalRandom.current().nextDouble(-0.5, 0.5);
                    fb.setVelocity(new Vector(vx, vy, vz));

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (!fb.isDead()) {
                                world.spawnParticle(Particle.DUST, fb.getLocation(), 10,
                                        0.2, 0.2, 0.2, 0,
                                        new Particle.DustOptions(Color.fromRGB(163, 73, 223), 1.0f));
                                fb.remove();
                            }
                        }
                    }.runTaskLater(plugin, 40);
                }
            }.runTaskLater(plugin, i * 2);
        }

        // Secondary crystal sounds staggered
        new BukkitRunnable() {
            @Override
            public void run() {
                world.playSound(loc, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0f, 1.2f);
                world.spawnParticle(Particle.DUST, center, 30,
                        1.5, 1.0, 1.5, 0,
                        new Particle.DustOptions(Color.fromRGB(200, 150, 255), 0.8f));
            }
        }.runTaskLater(plugin, 15);
    }

    // ========== 5. ATAQUE ORBITAL ==========
    private void playOrbitalStrike(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;

        Location impact = loc.clone().add(0, 1, 0);
        Location sky = loc.clone().add(0, 25, 0);

        // Beam descending from sky
        new BukkitRunnable() {
            int step = 0;
            @Override
            public void run() {
                if (step >= 20) {
                    cancel();
                    // Impact!
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            world.spawnParticle(Particle.EXPLOSION_EMITTER, impact, 3, 0.5, 0.5, 0.5, 0);
                            world.spawnParticle(Particle.END_ROD, impact, 80, 2.0, 1.5, 2.0, 0.15);
                            world.spawnParticle(Particle.FLASH, impact, 2, 0, 0, 0, 0);
                            world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);

                            // Firework particles
                            world.spawnParticle(Particle.DUST, impact, 50,
                                    2.0, 2.0, 2.0, 0,
                                    new Particle.DustOptions(Color.WHITE, 2.0f));
                            world.spawnParticle(Particle.DUST, impact, 40,
                                    1.5, 1.5, 1.5, 0,
                                    new Particle.DustOptions(Color.YELLOW, 1.5f));

                            // Firework sound
                            world.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 0.8f);
                            world.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.8f, 1.0f);
                        }
                    }.runTaskLater(plugin, 2);
                    return;
                }

                double y = sky.getY() - (step * (sky.getY() - impact.getY()) / 20.0);
                Location beamPoint = new Location(world, loc.getX(), y, loc.getZ());
                world.spawnParticle(Particle.END_ROD, beamPoint, 8, 0.15, 0.1, 0.15, 0.01);
                world.spawnParticle(Particle.DUST, beamPoint, 5,
                        0.1, 0.1, 0.1, 0,
                        new Particle.DustOptions(Color.WHITE, 2.0f));

                if (step % 5 == 0) {
                    world.playSound(beamPoint, Sound.BLOCK_BEACON_AMBIENT, 0.5f, 2.0f);
                }

                step++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }
}
