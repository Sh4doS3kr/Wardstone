package com.moonlight.coreprotect.effects;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.core.CoreLevel;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.scheduler.BukkitRunnable;

public class CoreAnimation {

    private final CoreProtectPlugin plugin;

    public CoreAnimation(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    public void playActivationAnimation(Location center, CoreLevel level, Runnable onComplete) {
        new BukkitRunnable() {
            private int tick = 0;
            private final int duration = 100; // 5 segundos total

            @Override
            public void run() {
                if (tick >= duration) {
                    // Explosion final
                    playFinalExplosion(center, level);
                    SoundManager.playFinalExplosion(center);
                    if (onComplete != null) {
                        onComplete.run();
                    }
                    cancel();
                    return;
                }

                // Fase 1: Espiral ascendente (0-40 ticks)
                if (tick < 40) {
                    playSpiral(center, level, tick);
                    if (tick % 10 == 0) {
                        SoundManager.playActivationSound(center);
                    }
                }
                // Fase 2: Formacion de domo (40-80 ticks)
                else if (tick < 80) {
                    playDomeFormation(center, level, tick - 40);
                    if (tick % 20 == 0) {
                        SoundManager.playAmbientSound(center);
                    }
                }
                // Fase 3: Carga de energia (80-100 ticks)
                else {
                    playEnergyCharge(center, level, tick - 80);
                }

                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void playSpiral(Location center, CoreLevel level, int tick) {
        double height = tick * 0.1;
        double angle = tick * 0.3;
        double radius = 1.5;

        for (int i = 0; i < 4; i++) {
            double offsetAngle = angle + (Math.PI / 2 * i);
            double x = Math.cos(offsetAngle) * radius;
            double z = Math.sin(offsetAngle) * radius;

            Location particleLoc = center.clone().add(x, height, z);
            center.getWorld().spawnParticle(level.getParticle(), particleLoc, 2, 0.05, 0.05, 0.05, 0);
        }

        // Particulas centrales
        center.getWorld().spawnParticle(Particle.END_ROD, center.clone().add(0, height, 0), 1, 0.1, 0.1, 0.1, 0);
    }

    private void playDomeFormation(Location center, CoreLevel level, int phase) {
        double progress = phase / 40.0;
        int size = level.getSize() / 2;
        double currentRadius = size * progress;

        // Anillo horizontal expandiendose
        for (int i = 0; i < 36; i++) {
            double angle = Math.toRadians(i * 10);
            double x = Math.cos(angle) * currentRadius;
            double z = Math.sin(angle) * currentRadius;

            Location particleLoc = center.clone().add(x, 0.5, z);
            center.getWorld().spawnParticle(level.getParticle(), particleLoc, 1, 0, 0, 0, 0);
        }

        // Columnas en las esquinas
        if (phase % 5 == 0) {
            double cornerOffset = currentRadius * 0.7;
            for (int corner = 0; corner < 4; corner++) {
                double cx = (corner < 2 ? 1 : -1) * cornerOffset;
                double cz = (corner % 2 == 0 ? 1 : -1) * cornerOffset;
                for (int h = 0; h < 5; h++) {
                    Location columnLoc = center.clone().add(cx, h * 0.5, cz);
                    center.getWorld().spawnParticle(Particle.WITCH, columnLoc, 1, 0, 0, 0, 0);
                }
            }
        }
    }

    private void playEnergyCharge(Location center, CoreLevel level, int phase) {
        int size = level.getSize() / 2;

        // Particulas convergentes hacia el centro
        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(i * 45 + phase * 20);
            double distance = size - (phase * 0.5);

            if (distance > 0) {
                double x = Math.cos(angle) * distance;
                double z = Math.sin(angle) * distance;
                Location particleLoc = center.clone().add(x, 1, z);
                center.getWorld().spawnParticle(level.getParticle(), particleLoc, 3, 0.1, 0.1, 0.1, 0.05);
            }
        }

        // Nucleo brillante
        center.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, center.clone().add(0, 1, 0), 5 + phase, 0.2, 0.2, 0.2,
                0.02);
    }

    private void playFinalExplosion(Location center, CoreLevel level) {
        int size = level.getSize() / 2;

        // Explosion central grande
        center.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center.clone().add(0, 1, 0), 1);
        center.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, center.clone().add(0, 1, 0), 100, 0.5, 0.5, 0.5,
                0.3);
        center.getWorld().spawnParticle(Particle.END_ROD, center.clone().add(0, 1, 0), 50, 0.3, 0.3, 0.3, 0.2);

        // Anillo de explosion en el perimetro
        for (int i = 0; i < 72; i++) {
            double angle = Math.toRadians(i * 5);
            double x = Math.cos(angle) * size;
            double z = Math.sin(angle) * size;
            Location borderLoc = center.clone().add(x, 0.5, z);
            center.getWorld().spawnParticle(level.getParticle(), borderLoc, 10, 0.2, 0.5, 0.2, 0.05);
        }

        // Columnas de luz en las 4 esquinas
        for (int corner = 0; corner < 4; corner++) {
            double cx = (corner < 2 ? 1 : -1) * size;
            double cz = (corner % 2 == 0 ? 1 : -1) * size;
            for (int h = 0; h < 20; h++) {
                Location columnLoc = center.clone().add(cx, h * 0.5, cz);
                center.getWorld().spawnParticle(Particle.END_ROD, columnLoc, 2, 0.1, 0.1, 0.1, 0);
            }
        }
    }

    public void playPlacementAnimation(Location target, Material material, Runnable onComplete) {
        // "Rocket" logic
        Location startVal = target.clone().add(0.5, 20, 0.5); // Empezar 20 bloques arriba
        org.bukkit.entity.ArmorStand stand = target.getWorld().spawn(startVal, org.bukkit.entity.ArmorStand.class);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setSmall(false);
        stand.setHelmet(new org.bukkit.inventory.ItemStack(material));
        stand.setHeadPose(new org.bukkit.util.EulerAngle(0, 0, 0));

        new BukkitRunnable() {
            @Override
            public void run() {
                if (stand.isDead() || !stand.isValid()) {
                    cancel();
                    return;
                }

                Location current = stand.getLocation();

                // Si ha llegado (o pasado) al objetivo
                if (current.getY() <= target.getY() + 0.5) { // +0.5 ajuste visual para que no atraviese suelo
                    stand.remove();

                    // IMPACTO
                    target.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, target.clone().add(0.5, 1, 0.5), 1);
                    target.getWorld().spawnParticle(Particle.LAVA, target.clone().add(0.5, 1, 0.5), 30, 1, 0.5, 1);
                    target.getWorld().spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, target.clone().add(0.5, 1, 0.5), 20,
                            0.5, 1, 0.5, 0.1);
                    SoundManager.playFinalExplosion(target); // Reusamos el sonido fuerte

                    // Colocar bloque final
                    if (onComplete != null)
                        onComplete.run();

                    cancel();
                    return;
                }

                // Movimiento hacia abajo (muy rapido)
                current.subtract(0, 1.5, 0); // 1.5 bloques por tick = 30 bloques/segundo (aprox 20 bloques en <1s)
                stand.teleport(current);

                // Trail particles
                target.getWorld().spawnParticle(Particle.FLAME, current.clone().add(0, 2, 0), 2, 0.1, 0.5, 0.1, 0.05);
                target.getWorld().spawnParticle(Particle.CLOUD, current.clone().add(0, 2, 0), 2, 0.2, 0.5, 0.2, 0);

            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public void playBreakAnimation(Location center, Material material, Runnable onComplete) {
        // Usamos ArmorStand para rotacion controlada ("Drill effect")
        org.bukkit.entity.ArmorStand stand = center.getWorld().spawn(center.clone().add(0.5, -0.5, 0.5),
                org.bukkit.entity.ArmorStand.class);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setSmall(true); // Opcional, pero a veces ayuda con el offset
        stand.setHelmet(new org.bukkit.inventory.ItemStack(material));
        // Posicion inicial de la cabeza
        stand.setHeadPose(new org.bukkit.util.EulerAngle(0, 0, 0));

        new BukkitRunnable() {
            int ticks = 0;
            double rotation = 0;

            @Override
            public void run() {
                if (ticks >= 40) { // 2 segundos
                    stand.remove();
                    // Explosion final epica
                    center.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center.clone().add(0.5, 1, 0.5), 1);
                    center.getWorld().spawnParticle(Particle.LAVA, center.clone().add(0.5, 1, 0.5), 50, 0.5, 0.5, 0.5);
                    center.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, center.clone().add(0.5, 1, 0.5), 100, 1,
                            1, 1, 0.5);
                    SoundManager.playFinalExplosion(center);

                    if (onComplete != null) {
                        onComplete.run();
                    }
                    cancel();
                    return;
                }

                // Subir
                stand.teleport(stand.getLocation().add(0, 0.05, 0));

                // Rotar cabeza (Drill effect) - Girar en Y muy rapido
                // ArmorStand rotacion es (X, Y, Z) en radianes. O rotamos el entity entero.
                // Rotar el entity es mas facil para el item en la cabeza.
                Location loc = stand.getLocation();
                loc.setYaw(loc.getYaw() + 45f); // 45 grados por tick
                stand.teleport(loc);

                // Particulas espiral rodeando
                double y = ticks * 0.05;
                double r = 1.0;
                double angle = ticks * 0.5;
                double x = Math.cos(angle) * r;
                double z = Math.sin(angle) * r;
                center.getWorld().spawnParticle(Particle.FIREWORK, center.clone().add(0.5 + x, y, 0.5 + z), 1, 0,
                        0, 0, 0);
                center.getWorld().spawnParticle(Particle.FIREWORK, center.clone().add(0.5 - x, y, 0.5 - z), 1, 0,
                        0, 0, 0);

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * EPIC 20-second upgrade animation.
     * Phase 1 (0-80 ticks, 4s): Block rises and starts spinning slowly with particle trail
     * Phase 2 (80-160 ticks, 4s): Block spins faster, orbiting particle rings
     * Phase 3 (160-240 ticks, 4s): Energy beams converge, block glows brighter
     * Phase 4 (240-320 ticks, 4s): Material transforms, massive particle vortex
     * Phase 5 (320-400 ticks, 4s): Shockwave explosion, fireworks, block descends
     */
    public void playUpgradeAnimation(Location center, Material oldMaterial, Material newMaterial, Runnable onComplete) {
        // Remove the block temporarily
        center.getBlock().setType(Material.AIR, false);

        // Spawn ArmorStand with old material as helmet
        Location standLoc = center.clone().add(0.5, -0.5, 0.5);
        org.bukkit.entity.ArmorStand stand = center.getWorld().spawn(standLoc, org.bukkit.entity.ArmorStand.class);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setSmall(false);
        stand.setHelmet(new org.bukkit.inventory.ItemStack(oldMaterial));
        stand.setInvulnerable(true);
        stand.setMarker(true);

        new BukkitRunnable() {
            private int tick = 0;
            private final int totalDuration = 400; // 20 seconds
            private double currentHeight = 0;
            private float currentYaw = 0;
            private boolean materialChanged = false;

            @Override
            public void run() {
                if (tick >= totalDuration || stand.isDead() || !stand.isValid()) {
                    // FINAL: Remove stand, place new block, epic explosion
                    if (!stand.isDead()) stand.remove();
                    playUpgradeFinalExplosion(center, newMaterial);
                    SoundManager.playUpgradeComplete(center);

                    // Place the new block after a short delay
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            center.getBlock().setType(newMaterial);
                            if (onComplete != null) onComplete.run();
                        }
                    }.runTaskLater(plugin, 10L);

                    cancel();
                    return;
                }

                // === PHASE 1: LEVITATION (0-80 ticks) ===
                if (tick < 80) {
                    phaseOneLevitate(center, stand, tick);
                }
                // === PHASE 2: FAST SPIN + RINGS (80-160 ticks) ===
                else if (tick < 160) {
                    phaseTwoSpin(center, stand, tick - 80);
                }
                // === PHASE 3: ENERGY CONVERGENCE (160-240 ticks) ===
                else if (tick < 240) {
                    phaseThreeEnergy(center, stand, tick - 160);
                }
                // === PHASE 4: TRANSFORMATION VORTEX (240-320 ticks) ===
                else if (tick < 320) {
                    phaseFourTransform(center, stand, tick - 240, oldMaterial, newMaterial);
                    if (!materialChanged && tick >= 280) {
                        stand.setHelmet(new org.bukkit.inventory.ItemStack(newMaterial));
                        materialChanged = true;
                        SoundManager.playUpgradeTransform(center);
                        // Burst of particles on transform
                        center.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                                stand.getLocation().add(0, 1.5, 0), 80, 0.5, 0.5, 0.5, 0.3);
                        center.getWorld().spawnParticle(Particle.FLASH,
                                stand.getLocation().add(0, 1.5, 0), 3, 0, 0, 0, 0);
                    }
                }
                // === PHASE 5: DESCENT + FIREWORKS (320-400 ticks) ===
                else {
                    phaseFiveDescent(center, stand, tick - 320, newMaterial);
                }

                // Periodic sounds
                if (tick % 40 == 0 && tick < 320) {
                    SoundManager.playUpgradeAmbient(center, tick);
                }

                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void phaseOneLevitate(Location center, org.bukkit.entity.ArmorStand stand, int tick) {
        // Slowly rise 4 blocks over 80 ticks
        double targetHeight = (tick / 80.0) * 4.0;
        Location newLoc = center.clone().add(0.5, -0.5 + targetHeight, 0.5);

        // Slow rotation
        float yaw = tick * 4.5f; // 4.5 degrees per tick
        newLoc.setYaw(yaw);
        stand.teleport(newLoc);

        // Rising particle trail
        Location particleCenter = center.clone().add(0.5, targetHeight + 0.5, 0.5);

        // Spiral trail below
        for (int i = 0; i < 2; i++) {
            double angle = Math.toRadians(tick * 15 + i * 180);
            double radius = 0.8;
            double px = Math.cos(angle) * radius;
            double pz = Math.sin(angle) * radius;
            center.getWorld().spawnParticle(Particle.END_ROD,
                    particleCenter.clone().add(px, -0.5, pz), 1, 0, 0, 0, 0);
        }

        // Soft glow at block
        center.getWorld().spawnParticle(Particle.ENCHANT,
                particleCenter, 5, 0.3, 0.3, 0.3, 0.5);

        // Ground ring expanding
        if (tick % 4 == 0) {
            double ringRadius = (tick / 80.0) * 3.0;
            for (int i = 0; i < 24; i++) {
                double angle = Math.toRadians(i * 15);
                double gx = Math.cos(angle) * ringRadius;
                double gz = Math.sin(angle) * ringRadius;
                center.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,
                        center.clone().add(0.5 + gx, 0.1, 0.5 + gz), 1, 0, 0, 0, 0);
            }
        }
    }

    private void phaseTwoSpin(Location center, org.bukkit.entity.ArmorStand stand, int phase) {
        // Stay at height 4, spin progressively faster
        double height = 4.0;
        Location newLoc = center.clone().add(0.5, -0.5 + height, 0.5);

        // Accelerating rotation
        float spinSpeed = 9.0f + (phase * 0.3f); // Gets faster
        float yaw = stand.getLocation().getYaw() + spinSpeed;
        newLoc.setYaw(yaw);
        stand.teleport(newLoc);

        Location particleCenter = center.clone().add(0.5, height + 0.5, 0.5);

        // Orbiting rings (2 rings at different angles)
        for (int ring = 0; ring < 2; ring++) {
            double ringAngle = Math.toRadians(phase * 8 + ring * 90);
            double tiltAngle = Math.toRadians(phase * 3 + ring * 45);
            double radius = 1.5 + Math.sin(phase * 0.1) * 0.3;

            for (int i = 0; i < 12; i++) {
                double a = Math.toRadians(i * 30) + ringAngle;
                double px = Math.cos(a) * radius;
                double py = Math.sin(tiltAngle) * Math.sin(a) * 0.5;
                double pz = Math.sin(a) * radius;

                center.getWorld().spawnParticle(Particle.WITCH,
                        particleCenter.clone().add(px, py, pz), 1, 0, 0, 0, 0);
            }
        }

        // Electric sparks
        if (phase % 5 == 0) {
            double sparkAngle = Math.random() * Math.PI * 2;
            double sparkDist = Math.random() * 2.0;
            center.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                    particleCenter.clone().add(
                            Math.cos(sparkAngle) * sparkDist,
                            (Math.random() - 0.5) * 2,
                            Math.sin(sparkAngle) * sparkDist),
                    3, 0.1, 0.1, 0.1, 0.1);
        }

        // Vertical light beams from ground
        if (phase % 10 == 0) {
            for (int corner = 0; corner < 4; corner++) {
                double bx = (corner < 2 ? 1.5 : -1.5);
                double bz = (corner % 2 == 0 ? 1.5 : -1.5);
                for (int y = 0; y < 6; y++) {
                    center.getWorld().spawnParticle(Particle.END_ROD,
                            center.clone().add(0.5 + bx, y * 0.7, 0.5 + bz),
                            1, 0, 0, 0, 0);
                }
            }
        }
    }

    private void phaseThreeEnergy(Location center, org.bukkit.entity.ArmorStand stand, int phase) {
        // Hover and wobble, energy converges
        double height = 4.0 + Math.sin(phase * 0.15) * 0.3;
        Location newLoc = center.clone().add(0.5, -0.5 + height, 0.5);
        float yaw = stand.getLocation().getYaw() + 15f;
        newLoc.setYaw(yaw);
        stand.teleport(newLoc);

        Location particleCenter = center.clone().add(0.5, height + 0.5, 0.5);

        // Convergent beams from 8 directions
        double convergeDist = Math.max(0.5, 6.0 - (phase / 80.0) * 5.5);
        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(i * 45 + phase * 5);
            double px = Math.cos(angle) * convergeDist;
            double pz = Math.sin(angle) * convergeDist;

            center.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,
                    particleCenter.clone().add(px, 0, pz), 2, 0.05, 0.05, 0.05, 0);

            // Line of particles from far to center
            if (phase % 3 == 0) {
                for (double d = convergeDist; d > 0.5; d -= 0.5) {
                    double lx = Math.cos(angle) * d;
                    double lz = Math.sin(angle) * d;
                    center.getWorld().spawnParticle(Particle.ENCHANT,
                            particleCenter.clone().add(lx, 0, lz), 1, 0, 0, 0, 0);
                }
            }
        }

        // Growing core glow
        int glowIntensity = 3 + (phase / 10);
        center.getWorld().spawnParticle(Particle.END_ROD,
                particleCenter, glowIntensity, 0.3, 0.3, 0.3, 0.02);

        // DNA helix spiral
        double helixAngle1 = Math.toRadians(phase * 12);
        double helixAngle2 = helixAngle1 + Math.PI;
        double helixRadius = 1.2;
        for (double y = -2; y < 3; y += 0.4) {
            double h1x = Math.cos(helixAngle1 + y) * helixRadius;
            double h1z = Math.sin(helixAngle1 + y) * helixRadius;
            double h2x = Math.cos(helixAngle2 + y) * helixRadius;
            double h2z = Math.sin(helixAngle2 + y) * helixRadius;

            center.getWorld().spawnParticle(Particle.DRAGON_BREATH,
                    particleCenter.clone().add(h1x, y, h1z), 1, 0, 0, 0, 0);
            center.getWorld().spawnParticle(Particle.DRAGON_BREATH,
                    particleCenter.clone().add(h2x, y, h2z), 1, 0, 0, 0, 0);
        }
    }

    private void phaseFourTransform(Location center, org.bukkit.entity.ArmorStand stand, int phase,
                                     Material oldMat, Material newMat) {
        // Intense spinning at height, massive vortex
        double height = 4.0 + Math.sin(phase * 0.2) * 0.5;
        Location newLoc = center.clone().add(0.5, -0.5 + height, 0.5);
        float yaw = stand.getLocation().getYaw() + 25f; // Very fast
        newLoc.setYaw(yaw);
        stand.teleport(newLoc);

        Location particleCenter = center.clone().add(0.5, height + 0.5, 0.5);

        // Massive tornado/vortex
        for (double y = -3; y < 5; y += 0.3) {
            double vortexRadius = 0.3 + Math.abs(y) * 0.4;
            double vortexAngle = Math.toRadians(phase * 20 + y * 60);
            double vx = Math.cos(vortexAngle) * vortexRadius;
            double vz = Math.sin(vortexAngle) * vortexRadius;

            Particle vortexParticle = (y > 0) ? Particle.FLAME : Particle.SOUL_FIRE_FLAME;
            center.getWorld().spawnParticle(vortexParticle,
                    particleCenter.clone().add(vx, y, vz), 1, 0, 0, 0, 0);
        }

        // Intense core glow
        center.getWorld().spawnParticle(Particle.END_ROD,
                particleCenter, 8, 0.2, 0.2, 0.2, 0.05);

        // Shockwave rings at different heights
        if (phase % 8 == 0) {
            double ringY = (Math.random() - 0.5) * 4;
            for (int i = 0; i < 36; i++) {
                double angle = Math.toRadians(i * 10);
                double ringR = 2.0 + Math.random();
                center.getWorld().spawnParticle(Particle.FIREWORK,
                        particleCenter.clone().add(Math.cos(angle) * ringR, ringY, Math.sin(angle) * ringR),
                        1, 0, 0, 0, 0);
            }
        }

        // Lightning-like flashes
        if (phase % 15 == 0) {
            center.getWorld().spawnParticle(Particle.FLASH,
                    particleCenter, 2, 0.5, 0.5, 0.5, 0);
            center.getWorld().strikeLightningEffect(particleCenter);
        }
    }

    private void phaseFiveDescent(Location center, org.bukkit.entity.ArmorStand stand, int phase,
                                   Material newMaterial) {
        // Descend from 4 blocks back to 0 over 80 ticks
        double height = 4.0 * (1.0 - (phase / 80.0));
        if (height < 0) height = 0;
        Location newLoc = center.clone().add(0.5, -0.5 + height, 0.5);

        // Decelerating spin
        float spinSpeed = Math.max(2f, 20f - (phase * 0.2f));
        float yaw = stand.getLocation().getYaw() + spinSpeed;
        newLoc.setYaw(yaw);
        stand.teleport(newLoc);

        Location particleCenter = center.clone().add(0.5, height + 0.5, 0.5);

        // Trailing golden particles
        center.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                particleCenter, 3, 0.3, 0.5, 0.3, 0.1);

        // Expanding shockwave on ground
        if (phase % 5 == 0) {
            double ringRadius = (phase / 80.0) * 8.0;
            org.bukkit.Particle.DustOptions goldDust = new org.bukkit.Particle.DustOptions(
                    org.bukkit.Color.fromRGB(255, 215, 0), 1.2f);
            for (int i = 0; i < 48; i++) {
                double angle = Math.toRadians(i * 7.5);
                double gx = Math.cos(angle) * ringRadius;
                double gz = Math.sin(angle) * ringRadius;
                center.getWorld().spawnParticle(Particle.DUST,
                        center.clone().add(0.5 + gx, 0.2, 0.5 + gz), 1, 0, 0, 0, 0, goldDust);
            }
        }

        // Firework bursts
        if (phase % 20 == 0 && phase < 60) {
            spawnFireworkEffect(center.clone().add(
                    (Math.random() - 0.5) * 6, 3 + Math.random() * 3, (Math.random() - 0.5) * 6));
        }

        // Final settling particles
        if (phase > 60) {
            center.getWorld().spawnParticle(Particle.CLOUD,
                    center.clone().add(0.5, 0.5, 0.5), 3, 0.5, 0.2, 0.5, 0.02);
        }
    }

    private void playUpgradeFinalExplosion(Location center, Material newMaterial) {
        Location particleCenter = center.clone().add(0.5, 1, 0.5);

        // Massive totem burst
        center.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, particleCenter, 200, 1, 1.5, 1, 0.5);

        // Large explosion visual
        center.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, particleCenter, 2);

        // End rod shower
        center.getWorld().spawnParticle(Particle.END_ROD, particleCenter, 100, 2, 3, 2, 0.1);

        // Flash
        center.getWorld().spawnParticle(Particle.FLASH, particleCenter, 5, 0, 0, 0, 0);

        // Firework ring
        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(i * 45);
            Location fwLoc = center.clone().add(Math.cos(angle) * 3, 2 + Math.random() * 2, Math.sin(angle) * 3);
            spawnFireworkEffect(fwLoc);
        }

        // Ground shockwave
        for (double r = 0; r < 8; r += 0.5) {
            final double radius = r;
            new BukkitRunnable() {
                @Override
                public void run() {
                    org.bukkit.Particle.DustOptions dust = new org.bukkit.Particle.DustOptions(
                            org.bukkit.Color.fromRGB(0, 255, 255), 1.5f);
                    for (int i = 0; i < 36; i++) {
                        double angle = Math.toRadians(i * 10);
                        center.getWorld().spawnParticle(Particle.DUST,
                                center.clone().add(0.5 + Math.cos(angle) * radius, 0.3, 0.5 + Math.sin(angle) * radius),
                                1, 0, 0, 0, 0, dust);
                    }
                }
            }.runTaskLater(plugin, (long) (r * 2));
        }

        // 4 corner light pillars
        for (int corner = 0; corner < 4; corner++) {
            double cx = (corner < 2 ? 3 : -3);
            double cz = (corner % 2 == 0 ? 3 : -3);
            for (int h = 0; h < 30; h++) {
                center.getWorld().spawnParticle(Particle.END_ROD,
                        center.clone().add(0.5 + cx, h * 0.5, 0.5 + cz), 2, 0.1, 0.1, 0.1, 0);
            }
        }
    }

    private void spawnFireworkEffect(Location location) {
        org.bukkit.entity.Firework fw = location.getWorld().spawn(location, org.bukkit.entity.Firework.class);
        org.bukkit.inventory.meta.FireworkMeta fwMeta = fw.getFireworkMeta();

        org.bukkit.FireworkEffect.Builder builder = org.bukkit.FireworkEffect.builder();
        builder.withColor(org.bukkit.Color.AQUA, org.bukkit.Color.YELLOW, org.bukkit.Color.PURPLE);
        builder.withFade(org.bukkit.Color.WHITE);
        builder.with(org.bukkit.FireworkEffect.Type.BALL_LARGE);
        builder.trail(true);
        builder.flicker(true);

        fwMeta.addEffect(builder.build());
        fwMeta.setPower(0);
        fw.setFireworkMeta(fwMeta);

        // Detonar inmediatamente despues de 1 tick
        new BukkitRunnable() {
            @Override
            public void run() {
                fw.detonate();
            }
        }.runTaskLater(plugin, 2L);
    }

    public int playRegionDisplay(Location center, int size) {
        int radius = size / 2;
        org.bukkit.Particle.DustOptions dustOptions = new org.bukkit.Particle.DustOptions(org.bukkit.Color.RED, 0.5f);

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                // Esquinas (Red Dust)
                for (int corner = 0; corner < 4; corner++) {
                    double cx = (corner < 2 ? 1 : -1) * radius + (corner < 2 ? 0.5 : 0.5);
                    double cz = (corner % 2 == 0 ? 1 : -1) * radius + (corner % 2 == 0 ? 0.5 : 0.5);

                    Location loc = center.clone().add(cx, 0.5, cz);
                    center.getWorld().spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, dustOptions);
                }

                // Bordes (Red Dust) - Mas continuo mejor look, o espaciado?
                // Usuario pidio "fina" (thin). Espaciado da sensacion de linea punteada.
                // Mas denso = linea continua. Probemos espaciado 2.
                for (int i = 0; i <= size; i += 2) {
                    double d = i - radius + 0.5;

                    // X
                    center.getWorld().spawnParticle(Particle.DUST, center.clone().add(radius + 0.5, 0.5, d), 1, 0, 0,
                            0, 0, dustOptions);
                    center.getWorld().spawnParticle(Particle.DUST, center.clone().add(-radius + 0.5, 0.5, d), 1, 0,
                            0, 0, 0, dustOptions);
                    // Z
                    center.getWorld().spawnParticle(Particle.DUST, center.clone().add(d, 0.5, radius + 0.5), 1, 0, 0,
                            0, 0, dustOptions);
                    center.getWorld().spawnParticle(Particle.DUST, center.clone().add(d, 0.5, -radius + 0.5), 1, 0,
                            0, 0, 0, dustOptions);
                }
            }
        };

        task.runTaskTimer(plugin, 0L, 20L); // 1 vez por segundo
        return task.getTaskId();
    }
}
