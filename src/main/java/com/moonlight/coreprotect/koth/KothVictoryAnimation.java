package com.moonlight.coreprotect.koth;

import org.bukkit.*;
import org.bukkit.entity.FallingBlock;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Animación espectacular para el ganador del KOTH.
 * Inspirada en los efectos de FinisherEffects.java.
 */
public class KothVictoryAnimation {

    private final Plugin plugin;
    public static final String VICTORY_TAG = "koth_victory_ghost";

    public KothVictoryAnimation(Plugin plugin) {
        this.plugin = plugin;
    }

    public void play(Location center) {
        World world = center.getWorld();
        if (world == null)
            return;

        // Sonidos iniciales de victoria
        world.playSound(center, Sound.UI_TOAST_CHALLENGE_COMPLETE, 2.0f, 1.0f);
        world.playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.5f);
        world.playSound(center, Sound.ENTITY_PLAYER_LEVELUP, 1.5f, 0.5f);

        // Fase 1: Espirales ascendentes de partículas
        playSpirals(center, world);

        // Fase 2: Fuegos artificiales y explosiones de color
        playFireworkShow(center, world);

        // Fase 3: Lluvia de bloques de tesoro (Falling Blocks)
        playTreasureRain(center, world);

        // Fase 4: Explosión final (Supernova)
        new BukkitRunnable() {
            @Override
            public void run() {
                playFinalBlast(center, world);
            }
        }.runTaskLater(plugin, 100);
    }

    private void playSpirals(Location center, World world) {
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t > 120) {
                    cancel();
                    return;
                }

                // Doble espiral
                for (int i = 0; i < 2; i++) {
                    double angle = t * 0.2 + (i * Math.PI);
                    double radius = 0.5 + t * 0.05;
                    if (radius > 8)
                        radius = 8;

                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    double y = t * 0.1;

                    Location pLoc = center.clone().add(x, y, z);
                    world.spawnParticle(Particle.TOTEM_OF_UNDYING, pLoc, 3);
                    world.spawnParticle(Particle.END_ROD, pLoc, 1, 0.05, 0.05, 0.05, 0.01);

                    if (t % 10 == 0) {
                        world.spawnParticle(Particle.END_ROD, pLoc, 5, 0.2, 0.2, 0.2, 0.05);
                        world.playSound(pLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 0.8f + (t * 0.01f));
                    }
                }
                t += 2;
            }
        }.runTaskTimer(plugin, 0, 2);
    }

    private void playFireworkShow(Location center, World world) {
        for (int i = 0; i < 8; i++) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    double rx = ThreadLocalRandom.current().nextDouble(-6, 6);
                    double rz = ThreadLocalRandom.current().nextDouble(-6, 6);
                    double ry = 5 + ThreadLocalRandom.current().nextDouble(10);
                    Location fireworkLoc = center.clone().add(rx, ry, rz);

                    world.spawnParticle(Particle.EXPLOSION_EMITTER, fireworkLoc, 1, 0, 0, 0, 0);
                    world.spawnParticle(Particle.FIREWORK, fireworkLoc, 100, 1.0, 1.0, 1.0, 0.15);
                    world.spawnParticle(Particle.GLOW, fireworkLoc, 50, 1.5, 1.5, 1.5, 0.1);

                    world.playSound(fireworkLoc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 2.0f,
                            0.8f + ThreadLocalRandom.current().nextFloat() * 0.4f);
                    world.playSound(fireworkLoc, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1.5f, 1.0f);
                }
            }.runTaskLater(plugin, 20 + i * 12);
        }
    }

    private void playTreasureRain(Location center, World world) {
        Material[] materials = {
                Material.GOLD_BLOCK, Material.DIAMOND_BLOCK, Material.EMERALD_BLOCK,
                Material.NETHERITE_BLOCK, Material.LAPIS_BLOCK, Material.AMETHYST_BLOCK,
                Material.SEA_LANTERN, Material.BEACON
        };

        for (int i = 0; i < 40; i++) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    Material mat = materials[ThreadLocalRandom.current().nextInt(materials.length)];
                    double rx = ThreadLocalRandom.current().nextDouble(-10, 10);
                    double rz = ThreadLocalRandom.current().nextDouble(-10, 10);
                    double ry = 15 + ThreadLocalRandom.current().nextDouble(10);

                    Location spawnLoc = center.clone().add(rx, ry, rz);
                    FallingBlock fb = world.spawnFallingBlock(spawnLoc, mat.createBlockData());
                    fb.setDropItem(false);
                    fb.setHurtEntities(false);
                    fb.setMetadata(VICTORY_TAG, new FixedMetadataValue(plugin, true));
                    fb.setVelocity(new Vector(
                            ThreadLocalRandom.current().nextDouble(-0.2, 0.2),
                            -0.4,
                            ThreadLocalRandom.current().nextDouble(-0.2, 0.2)));

                    // Desaparecer con partículas al tocar suelo (o después de un tiempo)
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (!fb.isDead()) {
                                world.spawnParticle(Particle.CLOUD, fb.getLocation(), 10, 0.3, 0.3, 0.3, 0.05);
                                fb.remove();
                            }
                        }
                    }.runTaskLater(plugin, 40 + ThreadLocalRandom.current().nextInt(40));
                }
            }.runTaskLater(plugin, 40 + ThreadLocalRandom.current().nextInt(80));
        }
    }

    private void playFinalBlast(Location center, World world) {
        Location blastLoc = center.clone().add(0, 5, 0);

        // Gran explosión visual
        try {
            world.spawnParticle(Particle.EXPLOSION_EMITTER, blastLoc, 10, 2, 2, 2, 0);
            world.spawnParticle(Particle.END_ROD, blastLoc, 10, 0.5, 0.5, 0.5, 0.1);
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, blastLoc, 500, 5, 5, 5, 0.5);
            world.spawnParticle(Particle.END_ROD, blastLoc, 200, 6, 6, 6, 0.2);
            
            // DUST particle with proper DustOptions
            Particle.DustOptions dustOpts = new Particle.DustOptions(org.bukkit.Color.YELLOW, 3.0f);
            world.spawnParticle(Particle.DUST, blastLoc, 300, 7, 7, 7, 0, dustOpts);
        } catch (Exception e) {
            // Fallback: use safe particles if DUST fails
            world.spawnParticle(Particle.END_ROD, blastLoc, 300, 7, 7, 7, 0.2);
        }

        // Sonidos finales
        world.playSound(blastLoc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);
        world.playSound(blastLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 0.8f);
        world.playSound(blastLoc, Sound.ITEM_TOTEM_USE, 2.0f, 1.0f);

        // Rayos de adorno
        for (int i = 0; i < 4; i++) {
            double rx = ThreadLocalRandom.current().nextDouble(-8, 8);
            double rz = ThreadLocalRandom.current().nextDouble(-8, 8);
            world.strikeLightningEffect(center.clone().add(rx, 0, rz));
        }
    }
}
