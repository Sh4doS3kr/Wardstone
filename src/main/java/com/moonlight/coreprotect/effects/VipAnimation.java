package com.moonlight.coreprotect.effects;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Firework;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;

public class VipAnimation {

    private final CoreProtectPlugin plugin;

    public VipAnimation(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    public void playVipPlacement(Location target, Material material, String vipRank, Runnable onComplete) {
        switch (vipRank.toLowerCase()) {
            case "luna":
                playLunaPlacement(target, material, onComplete);
                break;
            case "nova":
                playNovaPlacement(target, material, onComplete);
                break;
            case "eclipse":
                playEclipsePlacement(target, material, onComplete);
                break;
            case "moonlord":
                playMoonLordPlacement(target, material, onComplete);
                break;
            default:
                if (onComplete != null) onComplete.run();
                break;
        }
    }

    public void playVipUpgrade(Location center, Material oldMat, Material newMat, String vipRank, Runnable onComplete) {
        switch (vipRank.toLowerCase()) {
            case "luna":
                playLunaUpgrade(center, oldMat, newMat, onComplete);
                break;
            case "nova":
                playNovaUpgrade(center, oldMat, newMat, onComplete);
                break;
            case "eclipse":
                playEclipseUpgrade(center, oldMat, newMat, onComplete);
                break;
            case "moonlord":
                playMoonLordUpgrade(center, oldMat, newMat, onComplete);
                break;
            default:
                if (onComplete != null) onComplete.run();
                break;
        }
    }

    // ================================================================
    //  LUNA - Moonbeams, silver crescents, gentle celestial glow
    // ================================================================
    private void playLunaPlacement(Location target, Material material, Runnable onComplete) {
        Location start = target.clone().add(0.5, 25, 0.5);
        ArmorStand stand = spawnStand(start, material);

        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (stand.isDead() || !stand.isValid()) { cancel(); return; }
                Location current = stand.getLocation();

                if (current.getY() <= target.getY() + 0.5) {
                    stand.remove();
                    lunaImpact(target);
                    if (onComplete != null) onComplete.run();
                    cancel();
                    return;
                }

                // Gentle descent with oscillation
                double sway = Math.sin(tick * 0.15) * 0.3;
                current.subtract(0, 0.8, 0);
                current.add(sway, 0, Math.cos(tick * 0.15) * 0.3);
                stand.teleport(current);

                // Silver moonbeam trail
                Particle.DustOptions silver = new Particle.DustOptions(Color.fromRGB(220, 220, 255), 1.5f);
                target.getWorld().spawnParticle(Particle.DUST, current.clone().add(0, 2, 0), 3, 0.2, 0.3, 0.2, 0, silver);
                target.getWorld().spawnParticle(Particle.END_ROD, current.clone().add(0, 2.5, 0), 2, 0.1, 0.5, 0.1, 0);

                // Crescent moon particle shape rotating around
                double moonAngle = tick * 0.12;
                for (double a = -0.8; a <= 0.8; a += 0.2) {
                    double mx = Math.cos(moonAngle) * (1.5 + Math.cos(a) * 0.4);
                    double mz = Math.sin(moonAngle) * (1.5 + Math.cos(a) * 0.4);
                    double my = Math.sin(a) * 1.2;
                    Particle.DustOptions moonDust = new Particle.DustOptions(Color.fromRGB(200, 200, 240), 0.8f);
                    target.getWorld().spawnParticle(Particle.DUST, current.clone().add(mx, my + 1.5, mz), 1, 0, 0, 0, 0, moonDust);
                }

                // Floating star sparkles
                if (tick % 3 == 0) {
                    double sx = (Math.random() - 0.5) * 4;
                    double sy = Math.random() * 3;
                    double sz = (Math.random() - 0.5) * 4;
                    target.getWorld().spawnParticle(Particle.END_ROD, current.clone().add(sx, sy, sz), 1, 0, 0, 0, 0);
                }

                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void lunaImpact(Location target) {
        Location center = target.clone().add(0.5, 1, 0.5);
        Particle.DustOptions silver = new Particle.DustOptions(Color.fromRGB(200, 210, 255), 2.0f);

        // Silver shockwave rings
        for (double r = 0; r < 10; r += 0.5) {
            final double radius = r;
            new BukkitRunnable() {
                @Override
                public void run() {
                    for (int i = 0; i < 48; i++) {
                        double angle = Math.toRadians(i * 7.5);
                        target.getWorld().spawnParticle(Particle.DUST,
                                center.clone().add(Math.cos(angle) * radius, 0.2, Math.sin(angle) * radius),
                                1, 0, 0, 0, 0, silver);
                    }
                }
            }.runTaskLater(plugin, (long) (r * 2));
        }

        // Moonbeam pillars
        for (int p = 0; p < 6; p++) {
            double angle = Math.toRadians(p * 60);
            double px = Math.cos(angle) * 3;
            double pz = Math.sin(angle) * 3;
            for (int h = 0; h < 20; h++) {
                target.getWorld().spawnParticle(Particle.END_ROD,
                        center.clone().add(px, h * 0.5, pz), 2, 0.05, 0.05, 0.05, 0);
            }
        }

        target.getWorld().spawnParticle(Particle.END_ROD, center, 80, 1, 2, 1, 0.05);
        target.getWorld().playSound(target, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 0.8f);
        target.getWorld().playSound(target, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 0.6f);
        target.getWorld().playSound(target, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
    }

    // ================================================================
    //  NOVA - Supernova explosion, golden fire tornado, star bursts
    // ================================================================
    private void playNovaPlacement(Location target, Material material, Runnable onComplete) {
        Location start = target.clone().add(0.5, 30, 0.5);
        ArmorStand stand = spawnStand(start, material);

        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (stand.isDead() || !stand.isValid()) { cancel(); return; }
                Location current = stand.getLocation();

                if (current.getY() <= target.getY() + 0.5) {
                    stand.remove();
                    novaImpact(target);
                    if (onComplete != null) onComplete.run();
                    cancel();
                    return;
                }

                // Fast descent with fire trail
                current.subtract(0, 2.0, 0);
                Location rot = current.clone();
                rot.setYaw(rot.getYaw() + 30f);
                stand.teleport(rot);

                // Fire tornado spiral
                for (int s = 0; s < 3; s++) {
                    double spiralAngle = tick * 0.5 + s * (Math.PI * 2 / 3);
                    double spiralRadius = 1.0 + Math.sin(tick * 0.1) * 0.5;
                    double sx = Math.cos(spiralAngle) * spiralRadius;
                    double sz = Math.sin(spiralAngle) * spiralRadius;
                    target.getWorld().spawnParticle(Particle.FLAME, current.clone().add(sx, 2, sz), 2, 0.05, 0.1, 0.05, 0.02);
                }

                // Golden sparkle trail
                Particle.DustOptions gold = new Particle.DustOptions(Color.fromRGB(255, 200, 50), 1.8f);
                target.getWorld().spawnParticle(Particle.DUST, current.clone().add(0, 2, 0), 5, 0.3, 0.5, 0.3, 0, gold);
                target.getWorld().spawnParticle(Particle.LAVA, current.clone().add(0, 2.5, 0), 1, 0.2, 0.3, 0.2);

                // Star shape expanding outward every few ticks
                if (tick % 4 == 0) {
                    for (int p = 0; p < 5; p++) {
                        double starAngle = Math.toRadians(p * 72 + tick * 10);
                        double dist = 2.0;
                        target.getWorld().spawnParticle(Particle.FIREWORK,
                                current.clone().add(Math.cos(starAngle) * dist, 1, Math.sin(starAngle) * dist),
                                3, 0.1, 0.1, 0.1, 0.05);
                    }
                }

                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void novaImpact(Location target) {
        Location center = target.clone().add(0.5, 1, 0.5);

        // Massive golden explosion
        target.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center, 3);
        target.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, center, 200, 1, 2, 1, 0.5);
        target.getWorld().spawnParticle(Particle.FLAME, center, 100, 2, 1, 2, 0.15);

        // Star-shaped shockwave
        for (double r = 0; r < 12; r += 0.5) {
            final double radius = r;
            new BukkitRunnable() {
                @Override
                public void run() {
                    Particle.DustOptions gold = new Particle.DustOptions(Color.fromRGB(255, 180, 0), 2.0f);
                    for (int i = 0; i < 5; i++) {
                        double baseAngle = Math.toRadians(i * 72);
                        // Star points
                        double starR = radius * (1.0 + 0.4 * Math.cos(5 * Math.toRadians(i * 72 + radius * 15)));
                        double x = Math.cos(baseAngle) * starR;
                        double z = Math.sin(baseAngle) * starR;
                        target.getWorld().spawnParticle(Particle.DUST,
                                center.clone().add(x, 0.2, z), 2, 0.1, 0.1, 0.1, 0, gold);
                    }
                    // Regular ring too
                    for (int i = 0; i < 36; i++) {
                        double angle = Math.toRadians(i * 10);
                        target.getWorld().spawnParticle(Particle.FLAME,
                                center.clone().add(Math.cos(angle) * radius, 0.3, Math.sin(angle) * radius),
                                1, 0, 0, 0, 0);
                    }
                }
            }.runTaskLater(plugin, (long) (r * 1.5));
        }

        // Fireworks ring
        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(i * 45);
            Location fwLoc = center.clone().add(Math.cos(angle) * 4, 3, Math.sin(angle) * 4);
            spawnNovaFirework(fwLoc);
        }

        target.getWorld().playSound(target, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.6f);
        target.getWorld().playSound(target, Sound.ITEM_TRIDENT_THUNDER, 1.5f, 1.2f);
        target.getWorld().playSound(target, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 1.0f);
    }

    private void spawnNovaFirework(Location loc) {
        Firework fw = loc.getWorld().spawn(loc, Firework.class);
        FireworkMeta meta = fw.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
                .withColor(Color.YELLOW, Color.ORANGE, Color.WHITE)
                .withFade(Color.RED)
                .with(FireworkEffect.Type.STAR)
                .trail(true).flicker(true).build());
        meta.setPower(0);
        fw.setFireworkMeta(meta);
        new BukkitRunnable() { @Override public void run() { fw.detonate(); } }.runTaskLater(plugin, 2L);
    }

    // ================================================================
    //  ECLIPSE - Dark/light duality, solar corona, shadow waves
    // ================================================================
    private void playEclipsePlacement(Location target, Material material, Runnable onComplete) {
        Location start = target.clone().add(0.5, 25, 0.5);
        ArmorStand stand = spawnStand(start, material);

        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (stand.isDead() || !stand.isValid()) { cancel(); return; }
                Location current = stand.getLocation();

                if (current.getY() <= target.getY() + 0.5) {
                    stand.remove();
                    eclipseImpact(target);
                    if (onComplete != null) onComplete.run();
                    cancel();
                    return;
                }

                // Slow, ominous descent
                current.subtract(0, 1.0, 0);
                Location rot = current.clone();
                rot.setYaw(rot.getYaw() + 8f);
                stand.teleport(rot);

                // Eclipse corona - half light, half dark
                for (int i = 0; i < 24; i++) {
                    double angle = Math.toRadians(i * 15 + tick * 3);
                    double radius = 1.8 + Math.sin(tick * 0.1 + i) * 0.3;
                    double ex = Math.cos(angle) * radius;
                    double ez = Math.sin(angle) * radius;

                    Particle p = (i % 2 == 0) ? Particle.SOUL_FIRE_FLAME : Particle.END_ROD;
                    target.getWorld().spawnParticle(p, current.clone().add(ex, 1.5, ez), 1, 0, 0, 0, 0);
                }

                // Dark void core
                Particle.DustOptions darkPurple = new Particle.DustOptions(Color.fromRGB(40, 0, 60), 2.0f);
                target.getWorld().spawnParticle(Particle.DUST, current.clone().add(0, 1.5, 0), 4, 0.15, 0.15, 0.15, 0, darkPurple);

                // Shadow tendrils reaching down
                if (tick % 5 == 0) {
                    for (int t = 0; t < 3; t++) {
                        double tAngle = Math.random() * Math.PI * 2;
                        double tDist = Math.random() * 2.5;
                        for (double d = 0; d < 3; d += 0.3) {
                            Particle.DustOptions shadow = new Particle.DustOptions(Color.fromRGB(20, 0, 40), 1.2f);
                            target.getWorld().spawnParticle(Particle.DUST,
                                    current.clone().add(Math.cos(tAngle) * tDist, -d, Math.sin(tAngle) * tDist),
                                    1, 0, 0, 0, 0, shadow);
                        }
                    }
                }

                // Lightning flickers
                if (tick % 12 == 0 && tick > 10) {
                    target.getWorld().spawnParticle(Particle.FLASH, current.clone().add(0, 1.5, 0), 1, 0, 0, 0, 0);
                }

                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void eclipseImpact(Location target) {
        Location center = target.clone().add(0.5, 1, 0.5);

        // Dual shockwave - dark and light
        for (double r = 0; r < 10; r += 0.4) {
            final double radius = r;
            new BukkitRunnable() {
                @Override
                public void run() {
                    Particle.DustOptions dark = new Particle.DustOptions(Color.fromRGB(30, 0, 50), 2.0f);
                    Particle.DustOptions light = new Particle.DustOptions(Color.fromRGB(255, 255, 200), 1.5f);
                    for (int i = 0; i < 48; i++) {
                        double angle = Math.toRadians(i * 7.5);
                        double x = Math.cos(angle) * radius;
                        double z = Math.sin(angle) * radius;
                        Particle.DustOptions dust = (i % 2 == 0) ? dark : light;
                        target.getWorld().spawnParticle(Particle.DUST,
                                center.clone().add(x, 0.2, z), 1, 0, 0, 0, 0, dust);
                    }
                }
            }.runTaskLater(plugin, (long) (r * 2));
        }

        // Shadow pillars
        for (int p = 0; p < 8; p++) {
            double angle = Math.toRadians(p * 45);
            double px = Math.cos(angle) * 4;
            double pz = Math.sin(angle) * 4;
            Particle pillarType = (p % 2 == 0) ? Particle.SOUL_FIRE_FLAME : Particle.END_ROD;
            for (int h = 0; h < 25; h++) {
                target.getWorld().spawnParticle(pillarType,
                        center.clone().add(px, h * 0.4, pz), 2, 0.05, 0.05, 0.05, 0);
            }
        }

        // Central void burst
        target.getWorld().spawnParticle(Particle.DRAGON_BREATH, center, 80, 1.5, 1.5, 1.5, 0.05);
        target.getWorld().spawnParticle(Particle.WITCH, center, 60, 1, 2, 1, 0.1);

        // Lightning
        target.getWorld().strikeLightningEffect(center);

        // Eclipse-specific fireworks
        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(i * 60);
            Location fwLoc = center.clone().add(Math.cos(angle) * 3, 4, Math.sin(angle) * 3);
            spawnEclipseFirework(fwLoc);
        }

        target.getWorld().playSound(target, Sound.ENTITY_WITHER_SPAWN, 0.8f, 1.5f);
        target.getWorld().playSound(target, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 0.8f);
        target.getWorld().playSound(target, Sound.BLOCK_END_PORTAL_SPAWN, 1.0f, 1.2f);
    }

    private void spawnEclipseFirework(Location loc) {
        Firework fw = loc.getWorld().spawn(loc, Firework.class);
        FireworkMeta meta = fw.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
                .withColor(Color.PURPLE, Color.BLACK, Color.WHITE)
                .withFade(Color.FUCHSIA)
                .with(FireworkEffect.Type.BURST)
                .trail(true).flicker(true).build());
        meta.setPower(0);
        fw.setFireworkMeta(meta);
        new BukkitRunnable() { @Override public void run() { fw.detonate(); } }.runTaskLater(plugin, 2L);
    }

    // ================================================================
    //  MOONLORD - The ultimate: combines ALL effects amplified x3,
    //  divine vortex, double helix, lightning storm, screen-filling
    // ================================================================
    private void playMoonLordPlacement(Location target, Material material, Runnable onComplete) {
        Location start = target.clone().add(0.5, 35, 0.5);
        ArmorStand stand = spawnStand(start, material);

        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (stand.isDead() || !stand.isValid()) { cancel(); return; }
                Location current = stand.getLocation();

                if (current.getY() <= target.getY() + 0.5) {
                    stand.remove();
                    moonLordImpact(target);
                    if (onComplete != null) onComplete.run();
                    cancel();
                    return;
                }

                // Majestic slow descent
                double speed = 0.6 + (tick * 0.03);
                current.subtract(0, Math.min(speed, 2.0), 0);
                Location rot = current.clone();
                rot.setYaw(rot.getYaw() + 15f);
                stand.teleport(rot);

                // Divine triple helix
                for (int h = 0; h < 3; h++) {
                    double helixAngle = tick * 0.2 + h * (Math.PI * 2 / 3);
                    double helixR = 2.0;
                    for (double y = -2; y < 3; y += 0.3) {
                        double hx = Math.cos(helixAngle + y) * helixR;
                        double hz = Math.sin(helixAngle + y) * helixR;
                        Particle p;
                        if (h == 0) p = Particle.END_ROD;
                        else if (h == 1) p = Particle.SOUL_FIRE_FLAME;
                        else p = Particle.DRAGON_BREATH;
                        target.getWorld().spawnParticle(p, current.clone().add(hx, y + 1.5, hz), 1, 0, 0, 0, 0);
                    }
                }

                // Orbiting celestial bodies
                for (int orb = 0; orb < 4; orb++) {
                    double orbAngle = tick * 0.08 + orb * (Math.PI / 2);
                    double orbR = 3.0;
                    double ox = Math.cos(orbAngle) * orbR;
                    double oz = Math.sin(orbAngle) * orbR;
                    double oy = Math.sin(tick * 0.15 + orb) * 1.5;
                    target.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                            current.clone().add(ox, oy + 1.5, oz), 3, 0.1, 0.1, 0.1, 0.02);
                }

                // Cosmic dust rain
                Particle.DustOptions cosmic1 = new Particle.DustOptions(Color.fromRGB(180, 100, 255), 1.5f);
                Particle.DustOptions cosmic2 = new Particle.DustOptions(Color.fromRGB(100, 200, 255), 1.2f);
                target.getWorld().spawnParticle(Particle.DUST, current.clone().add(0, 2, 0), 5, 1.5, 1, 1.5, 0, cosmic1);
                target.getWorld().spawnParticle(Particle.DUST, current.clone().add(0, 2, 0), 3, 1, 0.5, 1, 0, cosmic2);

                // Lightning every 8 ticks
                if (tick % 8 == 0 && tick > 5) {
                    target.getWorld().strikeLightningEffect(
                            current.clone().add((Math.random() - 0.5) * 6, 0, (Math.random() - 0.5) * 6));
                }

                // Ominous sound buildup
                if (tick % 20 == 0) {
                    target.getWorld().playSound(current, Sound.BLOCK_BEACON_AMBIENT, 1.0f, 0.5f + tick * 0.02f);
                }

                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void moonLordImpact(Location target) {
        Location center = target.clone().add(0.5, 1, 0.5);

        // SCREEN-FILLING EXPLOSION
        target.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center, 5);
        target.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, center, 400, 3, 3, 3, 0.8);
        target.getWorld().spawnParticle(Particle.END_ROD, center, 200, 3, 4, 3, 0.15);
        target.getWorld().spawnParticle(Particle.DRAGON_BREATH, center, 150, 2, 2, 2, 0.1);
        target.getWorld().spawnParticle(Particle.FLASH, center, 8, 0, 0, 0, 0);

        // Triple-layer shockwave (purple, gold, white)
        Particle.DustOptions purple = new Particle.DustOptions(Color.fromRGB(150, 50, 255), 2.5f);
        Particle.DustOptions gold = new Particle.DustOptions(Color.fromRGB(255, 200, 50), 2.0f);
        Particle.DustOptions white = new Particle.DustOptions(Color.fromRGB(255, 255, 255), 1.8f);

        for (double r = 0; r < 16; r += 0.4) {
            final double radius = r;
            new BukkitRunnable() {
                @Override
                public void run() {
                    for (int i = 0; i < 60; i++) {
                        double angle = Math.toRadians(i * 6);
                        double x = Math.cos(angle) * radius;
                        double z = Math.sin(angle) * radius;
                        target.getWorld().spawnParticle(Particle.DUST,
                                center.clone().add(x, 0.2, z), 1, 0, 0, 0, 0, purple);
                        target.getWorld().spawnParticle(Particle.DUST,
                                center.clone().add(x, 0.5, z), 1, 0, 0, 0, 0, gold);
                        target.getWorld().spawnParticle(Particle.DUST,
                                center.clone().add(x, 0.8, z), 1, 0, 0, 0, 0, white);
                    }
                }
            }.runTaskLater(plugin, (long) (r * 1.5));
        }

        // 8 massive light pillars
        for (int p = 0; p < 8; p++) {
            double angle = Math.toRadians(p * 45);
            double px = Math.cos(angle) * 5;
            double pz = Math.sin(angle) * 5;
            for (int h = 0; h < 40; h++) {
                Particle pillar = (p % 2 == 0) ? Particle.END_ROD : Particle.SOUL_FIRE_FLAME;
                target.getWorld().spawnParticle(pillar,
                        center.clone().add(px, h * 0.5, pz), 3, 0.1, 0.1, 0.1, 0);
            }
        }

        // Firework storm - 16 fireworks in two rings
        for (int ring = 0; ring < 2; ring++) {
            for (int i = 0; i < 8; i++) {
                double angle = Math.toRadians(i * 45 + ring * 22.5);
                double dist = 4 + ring * 3;
                Location fwLoc = center.clone().add(Math.cos(angle) * dist, 3 + ring * 2, Math.sin(angle) * dist);
                spawnMoonLordFirework(fwLoc, ring);
            }
        }

        // Lightning storm
        for (int i = 0; i < 4; i++) {
            final int delay = i * 5;
            new BukkitRunnable() {
                @Override
                public void run() {
                    target.getWorld().strikeLightningEffect(
                            center.clone().add((Math.random() - 0.5) * 10, 0, (Math.random() - 0.5) * 10));
                }
            }.runTaskLater(plugin, delay);
        }

        // Sound barrage
        target.getWorld().playSound(target, Sound.ENTITY_ENDER_DRAGON_DEATH, 1.0f, 1.5f);
        target.getWorld().playSound(target, Sound.ITEM_TRIDENT_THUNDER, 2.0f, 0.8f);
        target.getWorld().playSound(target, Sound.UI_TOAST_CHALLENGE_COMPLETE, 2.0f, 1.0f);
        target.getWorld().playSound(target, Sound.ENTITY_WITHER_SPAWN, 0.5f, 2.0f);
        target.getWorld().playSound(target, Sound.BLOCK_END_PORTAL_SPAWN, 1.5f, 1.0f);
    }

    private void spawnMoonLordFirework(Location loc, int ring) {
        Firework fw = loc.getWorld().spawn(loc, Firework.class);
        FireworkMeta meta = fw.getFireworkMeta();
        FireworkEffect.Builder builder = FireworkEffect.builder();
        if (ring == 0) {
            builder.withColor(Color.PURPLE, Color.FUCHSIA, Color.WHITE);
            builder.withFade(Color.AQUA);
            builder.with(FireworkEffect.Type.BALL_LARGE);
        } else {
            builder.withColor(Color.AQUA, Color.YELLOW, Color.LIME);
            builder.withFade(Color.WHITE);
            builder.with(FireworkEffect.Type.STAR);
        }
        builder.trail(true).flicker(true);
        meta.addEffect(builder.build());
        meta.setPower(0);
        fw.setFireworkMeta(meta);
        new BukkitRunnable() { @Override public void run() { fw.detonate(); } }.runTaskLater(plugin, 3L);
    }

    // ================================================================
    //  VIP UPGRADE ANIMATIONS (triggered when upgrading TO a VIP core)
    // ================================================================

    private void playLunaUpgrade(Location center, Material oldMat, Material newMat, Runnable onComplete) {
        center.getBlock().setType(Material.AIR, false);
        ArmorStand stand = spawnStand(center.clone().add(0.5, -0.5, 0.5), oldMat);

        new BukkitRunnable() {
            int tick = 0;
            boolean transformed = false;
            @Override
            public void run() {
                if (tick >= 300 || stand.isDead()) {
                    if (!stand.isDead()) stand.remove();
                    lunaImpact(center);
                    new BukkitRunnable() { @Override public void run() {
                        center.getBlock().setType(newMat);
                        if (onComplete != null) onComplete.run();
                    }}.runTaskLater(plugin, 10L);
                    cancel(); return;
                }

                // Rise to height 5
                double targetH = Math.min(tick / 60.0, 1.0) * 5.0;
                Location loc = center.clone().add(0.5, -0.5 + targetH, 0.5);
                loc.setYaw(loc.getYaw() + 6f + tick * 0.05f);
                stand.teleport(loc);
                Location pc = center.clone().add(0.5, targetH + 0.5, 0.5);

                // Crescent orbits
                for (int i = 0; i < 2; i++) {
                    double moonA = tick * 0.1 + i * Math.PI;
                    double mr = 2.0;
                    for (double a = -0.6; a <= 0.6; a += 0.15) {
                        double mx = Math.cos(moonA) * (mr + Math.cos(a) * 0.3);
                        double mz = Math.sin(moonA) * (mr + Math.cos(a) * 0.3);
                        Particle.DustOptions moonD = new Particle.DustOptions(Color.fromRGB(210, 210, 255), 0.8f);
                        center.getWorld().spawnParticle(Particle.DUST, pc.clone().add(mx, Math.sin(a) * 0.8, mz), 1, 0, 0, 0, 0, moonD);
                    }
                }

                center.getWorld().spawnParticle(Particle.END_ROD, pc, 2, 0.3, 0.3, 0.3, 0.01);

                if (!transformed && tick >= 200) {
                    stand.setHelmet(new ItemStack(newMat));
                    transformed = true;
                    center.getWorld().spawnParticle(Particle.FLASH, pc, 2, 0, 0, 0, 0);
                    SoundManager.playUpgradeTransform(center);
                }

                if (tick % 40 == 0) SoundManager.playUpgradeAmbient(center, tick);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void playNovaUpgrade(Location center, Material oldMat, Material newMat, Runnable onComplete) {
        center.getBlock().setType(Material.AIR, false);
        ArmorStand stand = spawnStand(center.clone().add(0.5, -0.5, 0.5), oldMat);

        new BukkitRunnable() {
            int tick = 0;
            boolean transformed = false;
            @Override
            public void run() {
                if (tick >= 350 || stand.isDead()) {
                    if (!stand.isDead()) stand.remove();
                    novaImpact(center);
                    new BukkitRunnable() { @Override public void run() {
                        center.getBlock().setType(newMat);
                        if (onComplete != null) onComplete.run();
                    }}.runTaskLater(plugin, 10L);
                    cancel(); return;
                }

                double h = Math.min(tick / 70.0, 1.0) * 5.0;
                Location loc = center.clone().add(0.5, -0.5 + h, 0.5);
                loc.setYaw(loc.getYaw() + 10f + tick * 0.1f);
                stand.teleport(loc);
                Location pc = center.clone().add(0.5, h + 0.5, 0.5);

                // Fire tornado
                for (int s = 0; s < 4; s++) {
                    double sA = tick * 0.3 + s * (Math.PI / 2);
                    for (double y = -2; y < 2; y += 0.4) {
                        double sr = 1.5 + y * 0.3;
                        center.getWorld().spawnParticle(Particle.FLAME,
                                pc.clone().add(Math.cos(sA + y) * sr, y, Math.sin(sA + y) * sr),
                                1, 0, 0, 0, 0);
                    }
                }

                Particle.DustOptions gd = new Particle.DustOptions(Color.fromRGB(255, 200, 50), 1.5f);
                center.getWorld().spawnParticle(Particle.DUST, pc, 3, 0.4, 0.4, 0.4, 0, gd);

                if (!transformed && tick >= 230) {
                    stand.setHelmet(new ItemStack(newMat));
                    transformed = true;
                    center.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, pc, 80, 0.5, 0.5, 0.5, 0.3);
                    SoundManager.playUpgradeTransform(center);
                }

                if (tick % 40 == 0) SoundManager.playUpgradeAmbient(center, tick);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void playEclipseUpgrade(Location center, Material oldMat, Material newMat, Runnable onComplete) {
        center.getBlock().setType(Material.AIR, false);
        ArmorStand stand = spawnStand(center.clone().add(0.5, -0.5, 0.5), oldMat);

        new BukkitRunnable() {
            int tick = 0;
            boolean transformed = false;
            @Override
            public void run() {
                if (tick >= 350 || stand.isDead()) {
                    if (!stand.isDead()) stand.remove();
                    eclipseImpact(center);
                    new BukkitRunnable() { @Override public void run() {
                        center.getBlock().setType(newMat);
                        if (onComplete != null) onComplete.run();
                    }}.runTaskLater(plugin, 10L);
                    cancel(); return;
                }

                double h = Math.min(tick / 70.0, 1.0) * 5.0;
                Location loc = center.clone().add(0.5, -0.5 + h, 0.5);
                loc.setYaw(loc.getYaw() + 5f);
                stand.teleport(loc);
                Location pc = center.clone().add(0.5, h + 0.5, 0.5);

                // Eclipse corona
                for (int i = 0; i < 20; i++) {
                    double a = Math.toRadians(i * 18 + tick * 2);
                    double r = 2.0 + Math.sin(tick * 0.08 + i) * 0.4;
                    Particle p = (i % 2 == 0) ? Particle.SOUL_FIRE_FLAME : Particle.END_ROD;
                    center.getWorld().spawnParticle(p, pc.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r), 1, 0, 0, 0, 0);
                }

                // Dark core
                Particle.DustOptions dk = new Particle.DustOptions(Color.fromRGB(30, 0, 50), 2.0f);
                center.getWorld().spawnParticle(Particle.DUST, pc, 3, 0.2, 0.2, 0.2, 0, dk);

                if (tick % 20 == 0 && tick > 100) {
                    center.getWorld().strikeLightningEffect(pc.clone().add((Math.random() - 0.5) * 5, 0, (Math.random() - 0.5) * 5));
                }

                if (!transformed && tick >= 230) {
                    stand.setHelmet(new ItemStack(newMat));
                    transformed = true;
                    center.getWorld().spawnParticle(Particle.DRAGON_BREATH, pc, 60, 1, 1, 1, 0.05);
                    SoundManager.playUpgradeTransform(center);
                }

                if (tick % 40 == 0) SoundManager.playUpgradeAmbient(center, tick);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void playMoonLordUpgrade(Location center, Material oldMat, Material newMat, Runnable onComplete) {
        center.getBlock().setType(Material.AIR, false);
        ArmorStand stand = spawnStand(center.clone().add(0.5, -0.5, 0.5), oldMat);

        new BukkitRunnable() {
            int tick = 0;
            boolean transformed = false;
            @Override
            public void run() {
                if (tick >= 400 || stand.isDead()) {
                    if (!stand.isDead()) stand.remove();
                    moonLordImpact(center);
                    new BukkitRunnable() { @Override public void run() {
                        center.getBlock().setType(newMat);
                        if (onComplete != null) onComplete.run();
                    }}.runTaskLater(plugin, 10L);
                    cancel(); return;
                }

                double h = Math.min(tick / 80.0, 1.0) * 6.0;
                Location loc = center.clone().add(0.5, -0.5 + h, 0.5);
                loc.setYaw(loc.getYaw() + 12f + tick * 0.08f);
                stand.teleport(loc);
                Location pc = center.clone().add(0.5, h + 0.5, 0.5);

                // Triple divine helix
                for (int he = 0; he < 3; he++) {
                    double hA = tick * 0.15 + he * (Math.PI * 2 / 3);
                    for (double y = -3; y < 4; y += 0.3) {
                        double hx = Math.cos(hA + y) * 2.5;
                        double hz = Math.sin(hA + y) * 2.5;
                        Particle p = (he == 0) ? Particle.END_ROD : (he == 1) ? Particle.SOUL_FIRE_FLAME : Particle.DRAGON_BREATH;
                        center.getWorld().spawnParticle(p, pc.clone().add(hx, y, hz), 1, 0, 0, 0, 0);
                    }
                }

                // 4 orbiting celestial bodies
                for (int orb = 0; orb < 4; orb++) {
                    double orbA = tick * 0.06 + orb * (Math.PI / 2);
                    double oy = Math.sin(tick * 0.1 + orb) * 2;
                    center.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                            pc.clone().add(Math.cos(orbA) * 3.5, oy, Math.sin(orbA) * 3.5), 3, 0.1, 0.1, 0.1, 0.02);
                }

                // Cosmic dust
                Particle.DustOptions c1 = new Particle.DustOptions(Color.fromRGB(180, 100, 255), 1.5f);
                Particle.DustOptions c2 = new Particle.DustOptions(Color.fromRGB(100, 200, 255), 1.2f);
                center.getWorld().spawnParticle(Particle.DUST, pc, 4, 1.5, 1, 1.5, 0, c1);
                center.getWorld().spawnParticle(Particle.DUST, pc, 3, 1, 0.5, 1, 0, c2);

                // Lightning build-up
                if (tick % 10 == 0 && tick > 150) {
                    center.getWorld().strikeLightningEffect(pc.clone().add((Math.random() - 0.5) * 6, 0, (Math.random() - 0.5) * 6));
                }

                if (!transformed && tick >= 280) {
                    stand.setHelmet(new ItemStack(newMat));
                    transformed = true;
                    center.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, pc, 150, 1, 1, 1, 0.5);
                    center.getWorld().spawnParticle(Particle.FLASH, pc, 5, 0, 0, 0, 0);
                    SoundManager.playUpgradeTransform(center);
                }

                if (tick % 30 == 0) {
                    SoundManager.playUpgradeAmbient(center, tick);
                    center.getWorld().playSound(center, Sound.BLOCK_BEACON_AMBIENT, 1.0f, 0.5f + tick * 0.005f);
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ================================================================
    //  UTILITY
    // ================================================================
    private ArmorStand spawnStand(Location loc, Material material) {
        ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setSmall(false);
        stand.setHelmet(new ItemStack(material));
        stand.setInvulnerable(true);
        stand.setMarker(true);
        return stand;
    }
}
