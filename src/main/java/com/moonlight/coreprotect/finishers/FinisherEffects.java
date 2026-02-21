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

    public int play(FinisherType type, Player victim, Player killer) {
        switch (type) {
            case THUNDER_JUDGMENT:  return playThunder(victim);
            case VOID_INVOCATION:  return playVoid(victim);
            case BLOOD_ERUPTION:   return playBlood(victim);
            case SHATTERED_AMETHYST: return playAmethyst(victim);
            case ORBITAL_STRIKE:   return playOrbital(victim);
            default: return 40;
        }
    }

    private Location vLoc(Player v) { return v.isOnline() ? v.getLocation() : null; }
    private ThreadLocalRandom rng() { return ThreadLocalRandom.current(); }

    private void spawnBlock(World w, Location loc, Material mat, double vx, double vy, double vz, int lifetime) {
        FallingBlock fb = w.spawnFallingBlock(loc, mat.createBlockData());
        fb.setDropItem(false);
        fb.setHurtEntities(false);
        fb.setVelocity(new Vector(vx, vy, vz));
        new BukkitRunnable() {
            @Override public void run() { if (!fb.isDead()) fb.remove(); }
        }.runTaskLater(plugin, lifetime);
    }

    // ========================================================================
    //  1. EL JUICIO DEL TRUENO  (10s = 200 ticks)
    // ========================================================================
    private int playThunder(Player victim) {
        final int DUR = 200;
        Location origin = victim.getLocation().clone();
        World w = origin.getWorld();
        if (w == null) return 20;

        // Levitate victim
        victim.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 160, 1, false, false, false));
        w.playSound(origin, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.4f);
        w.playSound(origin, Sound.ENTITY_WARDEN_EMERGE, 0.6f, 1.5f);

        // Phase 1: Constant electric storm aura (0-200)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= DUR || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }

                // 3 spinning electric rings
                for (int ring = 0; ring < 3; ring++) {
                    double a = t * (0.3 + ring * 0.15) + ring * 2.0;
                    double r = 1.5 + Math.sin(t * 0.08 + ring) * 0.6;
                    double x = Math.cos(a) * r;
                    double z = Math.sin(a) * r;
                    double y = ring * 0.6 + 0.3;
                    w.spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(x, y, z), 8, 0.15, 0.2, 0.15, 0.03);
                    w.spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(-x, y + 0.5, -z), 6, 0.1, 0.3, 0.1, 0.02);
                }

                // Storm clouds
                if (t % 4 == 0) {
                    w.spawnParticle(Particle.CLOUD, loc.clone().add(0, -0.5, 0), 15, 2.0, 0.3, 2.0, 0.02);
                    w.spawnParticle(Particle.DUST, loc.clone().add(0, 3, 0), 8, 2.5, 0.5, 2.5, 0,
                            new Particle.DustOptions(Color.fromRGB(40, 40, 60), 3.0f));
                }

                // Random sparks flying off
                if (t % 3 == 0) {
                    double sx = rng().nextDouble(-2, 2), sz = rng().nextDouble(-2, 2);
                    w.spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(sx, rng().nextDouble(0, 2.5), sz),
                            12, 0.3, 0.3, 0.3, 0.06);
                }

                if (t % 15 == 0) w.playSound(loc, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8f, 2.0f);
                t += 2;
            }
        }.runTaskTimer(plugin, 0, 2);

        // Phase 2: 10 lightning bolts (ticks 20-160, closer and closer)
        int[] bolts = {20, 35, 48, 60, 72, 85, 100, 115, 130, 145};
        for (int i = 0; i < bolts.length; i++) {
            final int idx = i;
            new BukkitRunnable() {
                @Override public void run() {
                    Location loc = vLoc(victim);
                    if (loc == null) return;
                    double spread = 3.0 * (1.0 - idx / 10.0);
                    double ox = rng().nextDouble(-spread, spread);
                    double oz = rng().nextDouble(-spread, spread);
                    Location strike = loc.clone().add(ox, 0, oz);
                    w.strikeLightningEffect(strike);
                    w.spawnParticle(Particle.ELECTRIC_SPARK, strike.clone().add(0, 1, 0),
                            40 + idx * 8, 1.5, 2.0, 1.5, 0.08);

                    // Flying copper/iron blocks from each bolt
                    Material[] mats = {Material.OXIDIZED_COPPER, Material.COPPER_BLOCK, Material.LIGHTNING_ROD};
                    for (int b = 0; b < 2 + idx / 3; b++) {
                        spawnBlock(w, strike.clone().add(0, 1, 0),
                                mats[rng().nextInt(mats.length)],
                                rng().nextDouble(-0.7, 0.7), rng().nextDouble(0.5, 1.2), rng().nextDouble(-0.7, 0.7),
                                50 + rng().nextInt(20));
                    }
                }
            }.runTaskLater(plugin, bolts[i]);
        }

        // Phase 3: Stronger levitation at midpoint
        new BukkitRunnable() {
            @Override public void run() {
                if (!victim.isOnline()) return;
                victim.removePotionEffect(PotionEffectType.LEVITATION);
                victim.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 80, 3, false, false, false));
            }
        }.runTaskLater(plugin, 100);

        // Phase 4: FINAL triple strike (tick 160)
        new BukkitRunnable() {
            @Override public void run() {
                Location loc = vLoc(victim);
                if (loc == null) return;
                for (int i = 0; i < 3; i++) w.strikeLightningEffect(loc);
                w.spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(0, 1, 0), 300, 4.0, 3.0, 4.0, 0.15);
                w.spawnParticle(Particle.CLOUD, loc, 100, 3.0, 2.0, 3.0, 0.08);
                w.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 5, 1.0, 1.0, 1.0, 0);
                w.spawnParticle(Particle.FLASH, loc, 3, 0, 0, 0, 0);
                w.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 0.5f);
                w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.8f);

                // Massive block explosion
                Material[] debris = {Material.OXIDIZED_COPPER, Material.COPPER_BLOCK, Material.IRON_BLOCK,
                        Material.GOLD_BLOCK, Material.LIGHTNING_ROD};
                for (int b = 0; b < 15; b++) {
                    spawnBlock(w, loc.clone().add(0, 2, 0), debris[rng().nextInt(debris.length)],
                            rng().nextDouble(-1.2, 1.2), rng().nextDouble(0.6, 1.8), rng().nextDouble(-1.2, 1.2),
                            55 + rng().nextInt(25));
                }
            }
        }.runTaskLater(plugin, 160);

        // Phase 5: Aftershock sparks (165-195)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 35) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) loc = origin.clone().add(0, 3, 0);
                w.spawnParticle(Particle.ELECTRIC_SPARK, loc, 30 - t, 3.0, 3.0, 3.0, 0.05);
                if (t % 8 == 0) w.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5f, 1.5f);
                t += 3;
            }
        }.runTaskTimer(plugin, 165, 3);

        return DUR;
    }

    // ========================================================================
    //  2. INVOCACIÓN DEL VACÍO  (10s = 200 ticks)
    // ========================================================================
    private int playVoid(Player victim) {
        final int DUR = 200;
        Location origin = victim.getLocation().clone();
        World w = origin.getWorld();
        if (w == null) return 20;

        w.playSound(origin, Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f, 0.2f);
        w.playSound(origin, Sound.ENTITY_WARDEN_EMERGE, 1.0f, 0.4f);

        // Phase 1: Dark ground circle + rising darkness (0-60)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 60 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }

                double radius = 3.0 - (t * 0.03);
                for (int i = 0; i < 16; i++) {
                    double a = (Math.PI * 2 / 16) * i + (t * 0.08);
                    double x = Math.cos(a) * radius, z = Math.sin(a) * radius;
                    w.spawnParticle(Particle.DRAGON_BREATH, loc.clone().add(x, 0.1, z), 3, 0.05, 0.05, 0.05, 0.003);
                    w.spawnParticle(Particle.SQUID_INK, loc.clone().add(x * 0.5, 0.3 + t * 0.03, z * 0.5),
                            2, 0.1, 0.15, 0.1, 0.008);
                }
                w.spawnParticle(Particle.PORTAL, loc.clone().add(0, 1, 0), 20, 1.0, 1.0, 1.0, 0.8);

                // Obsidian blocks rising from ground
                if (t % 12 == 0) {
                    double bx = rng().nextDouble(-2, 2), bz = rng().nextDouble(-2, 2);
                    spawnBlock(w, loc.clone().add(bx, -0.5, bz), Material.OBSIDIAN,
                            0, rng().nextDouble(0.3, 0.6), 0, 50);
                }

                if (t % 12 == 0) w.playSound(loc, Sound.BLOCK_PORTAL_AMBIENT, 0.6f, 0.3f + t * 0.015f);
                t += 2;
            }
        }.runTaskTimer(plugin, 0, 2);

        // Phase 2: Victim levitates (tick 40)
        new BukkitRunnable() {
            @Override public void run() {
                if (!victim.isOnline()) return;
                victim.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 140, 2, false, false, false));
                Location loc = vLoc(victim);
                if (loc != null) {
                    w.playSound(loc, Sound.ENTITY_ENDERMAN_SCREAM, 0.8f, 0.2f);
                    w.spawnParticle(Particle.SONIC_BOOM, loc, 1, 0, 0, 0, 0);
                }
            }
        }.runTaskLater(plugin, 40);

        // Phase 3: Intense multi-spiral vortex + flying end stone (60-170)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 110 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }

                // 6 spiraling arms
                for (int arm = 0; arm < 6; arm++) {
                    double a = (t * 0.5) + (arm * Math.PI / 3);
                    double r = 2.0 - (t * 0.012);
                    if (r < 0.4) r = 0.4;
                    double x = Math.cos(a) * r, z = Math.sin(a) * r;
                    double y = (arm % 3) * 0.5 + Math.sin(t * 0.1) * 0.3;
                    w.spawnParticle(Particle.DRAGON_BREATH, loc.clone().add(x, y, z), 5, 0.08, 0.08, 0.08, 0.003);
                    w.spawnParticle(Particle.SQUID_INK, loc.clone().add(-x, y + 0.8, -z), 3, 0.1, 0.1, 0.1, 0.015);
                }

                // Central void column
                w.spawnParticle(Particle.PORTAL, loc, 25, 0.4, 2.0, 0.4, 1.0);
                w.spawnParticle(Particle.DUST, loc, 8, 0.3, 1.5, 0.3, 0,
                        new Particle.DustOptions(Color.fromRGB(20, 0, 40), 2.5f));

                // Heartbeat + sonic boom
                if (t % 16 == 0) {
                    w.playSound(loc, Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.4f);
                    w.spawnParticle(Particle.SONIC_BOOM, loc, 1, 0, 0, 0, 0);
                }

                // Flying end stone blocks being sucked in
                if (t % 10 == 0) {
                    Material[] mats = {Material.END_STONE, Material.OBSIDIAN, Material.CRYING_OBSIDIAN, Material.PURPLE_CONCRETE};
                    for (int b = 0; b < 3; b++) {
                        double bx = rng().nextDouble(-3, 3), bz = rng().nextDouble(-3, 3);
                        spawnBlock(w, loc.clone().add(bx, -2, bz), mats[rng().nextInt(mats.length)],
                                -bx * 0.1, rng().nextDouble(0.3, 0.7), -bz * 0.1, 40);
                    }
                }

                t += 2;
            }
        }.runTaskTimer(plugin, 60, 2);

        // Phase 4: IMPLOSION (tick 175)
        new BukkitRunnable() {
            @Override public void run() {
                Location loc = vLoc(victim);
                if (loc == null) return;

                w.spawnParticle(Particle.SQUID_INK, loc, 250, 5.0, 5.0, 5.0, 0.12);
                w.spawnParticle(Particle.DRAGON_BREATH, loc, 150, 4.0, 4.0, 4.0, 0.08);
                w.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 6, 1.5, 1.5, 1.5, 0);
                w.spawnParticle(Particle.PORTAL, loc, 400, 5.0, 5.0, 5.0, 1.5);
                w.spawnParticle(Particle.SONIC_BOOM, loc, 3, 1, 1, 1, 0);
                w.playSound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 0.3f);
                w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.3f);
                w.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 0.2f);

                // Debris explosion
                Material[] debris = {Material.END_STONE, Material.OBSIDIAN, Material.CRYING_OBSIDIAN,
                        Material.PURPLE_CONCRETE, Material.BLACK_CONCRETE};
                for (int b = 0; b < 20; b++) {
                    spawnBlock(w, loc.clone().add(0, 1, 0), debris[rng().nextInt(debris.length)],
                            rng().nextDouble(-1.5, 1.5), rng().nextDouble(0.5, 2.0), rng().nextDouble(-1.5, 1.5),
                            50 + rng().nextInt(30));
                }
            }
        }.runTaskLater(plugin, 175);

        return DUR;
    }

    // ========================================================================
    //  3. ERUPCIÓN DE SANGRE  (10s = 200 ticks)
    // ========================================================================
    private int playBlood(Player victim) {
        final int DUR = 200;
        Location origin = victim.getLocation().clone();
        World w = origin.getWorld();
        if (w == null) return 20;

        w.playSound(origin, Sound.ENTITY_WARDEN_EMERGE, 0.8f, 0.7f);
        w.playSound(origin, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.6f, 0.5f);

        // Phase 1: Blood pool expanding + ground cracks (0-50)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 50 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }

                double radius = 0.5 + t * 0.08;
                for (int i = 0; i < 16; i++) {
                    double a = (Math.PI * 2 / 16) * i + (t * 0.12);
                    double x = Math.cos(a) * radius, z = Math.sin(a) * radius;
                    w.spawnParticle(Particle.DUST, loc.clone().add(x, 0.05, z), 4,
                            0.12, 0.01, 0.12, 0,
                            new Particle.DustOptions(Color.fromRGB(120, 0, 0), 2.5f));
                }
                // Inner pool
                w.spawnParticle(Particle.DUST, loc.clone().add(0, 0.1, 0), 12,
                        radius * 0.5, 0.02, radius * 0.5, 0,
                        new Particle.DustOptions(Color.fromRGB(180, 0, 0), 2.0f));

                if (t % 6 == 0) {
                    w.playSound(loc, Sound.BLOCK_HONEY_BLOCK_SLIDE, 0.8f, 0.3f);
                    // Red blocks popping from ground
                    spawnBlock(w, loc.clone().add(rng().nextDouble(-radius, radius), 0, rng().nextDouble(-radius, radius)),
                            Material.RED_CONCRETE, 0, rng().nextDouble(0.15, 0.35), 0, 35);
                }
                t += 2;
            }
        }.runTaskTimer(plugin, 0, 2);

        // Phase 2: Victim levitates (tick 35)
        new BukkitRunnable() {
            @Override public void run() {
                if (!victim.isOnline()) return;
                victim.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 130, 1, false, false, false));
                w.playSound(origin, Sound.ENTITY_GHAST_SCREAM, 0.5f, 0.5f);
            }
        }.runTaskLater(plugin, 35);

        // Phase 3: Blood fountain + continuous drip rain (40-160)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 120 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }

                // Fountain from ground
                double h = Math.min(t * 0.1, 6.0);
                for (double y = 0; y < h; y += 0.4) {
                    double wobble = Math.sin(y * 2 + t * 0.3) * 0.15;
                    w.spawnParticle(Particle.DUST, origin.clone().add(wobble, y, wobble), 6,
                            0.15, 0.08, 0.15, 0,
                            new Particle.DustOptions(Color.fromRGB(160, 0, 0), 2.0f));
                }

                // Blood raining from victim
                w.spawnParticle(Particle.DUST, loc.clone().add(0, 0.5, 0), 15,
                        0.8, 0.4, 0.8, 0, new Particle.DustOptions(Color.RED, 1.5f));
                w.spawnParticle(Particle.DUST, loc.clone().add(0, -0.5, 0), 10,
                        0.5, 1.2, 0.5, 0, new Particle.DustOptions(Color.fromRGB(80, 0, 0), 1.8f));

                // Spraying outward
                if (t % 4 == 0) {
                    for (int s = 0; s < 4; s++) {
                        double sx = rng().nextDouble(-1.5, 1.5), sz = rng().nextDouble(-1.5, 1.5);
                        w.spawnParticle(Particle.DUST, loc.clone().add(sx, rng().nextDouble(-1, 2), sz), 5,
                                0.2, 0.2, 0.2, 0, new Particle.DustOptions(Color.fromRGB(139, 0, 0), 1.3f));
                    }
                }

                if (t % 10 == 0) w.playSound(loc, Sound.ENTITY_SLIME_SQUISH, 1.0f, 0.4f);
                t += 2;
            }
        }.runTaskTimer(plugin, 40, 2);

        // Phase 4: Falling blood blocks rain (50-160)
        new BukkitRunnable() {
            int c = 0;
            @Override public void run() {
                if (c >= 30 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }
                Material[] mats = {Material.RED_CONCRETE_POWDER, Material.RED_CONCRETE, Material.RED_WOOL, Material.REDSTONE_BLOCK};
                for (int b = 0; b < 2; b++) {
                    double ox = rng().nextDouble(-3, 3), oz = rng().nextDouble(-3, 3);
                    spawnBlock(w, loc.clone().add(ox, 5 + rng().nextDouble(4), oz),
                            mats[rng().nextInt(mats.length)], 0, 0, 0, 50 + rng().nextInt(20));
                }
                c++;
            }
        }.runTaskTimer(plugin, 50, 4);

        // Phase 5: BLOOD EXPLOSION (tick 170)
        new BukkitRunnable() {
            @Override public void run() {
                Location loc = vLoc(victim);
                if (loc == null) return;
                w.spawnParticle(Particle.DUST, loc, 300, 5.0, 5.0, 5.0, 0,
                        new Particle.DustOptions(Color.RED, 3.0f));
                w.spawnParticle(Particle.DUST, loc, 200, 4.0, 4.0, 4.0, 0,
                        new Particle.DustOptions(Color.fromRGB(139, 0, 0), 2.5f));
                w.spawnParticle(Particle.DUST, loc, 150, 3.0, 3.0, 3.0, 0,
                        new Particle.DustOptions(Color.fromRGB(60, 0, 0), 2.0f));
                w.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 4, 1, 1, 1, 0);
                w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.5f);
                w.playSound(loc, Sound.ENTITY_SLIME_SQUISH, 1.5f, 0.2f);

                // Massive debris
                Material[] debris = {Material.RED_CONCRETE, Material.RED_CONCRETE_POWDER,
                        Material.REDSTONE_BLOCK, Material.RED_WOOL, Material.NETHER_WART_BLOCK};
                for (int b = 0; b < 20; b++) {
                    spawnBlock(w, loc.clone().add(0, 1, 0), debris[rng().nextInt(debris.length)],
                            rng().nextDouble(-1.5, 1.5), rng().nextDouble(0.5, 2.0), rng().nextDouble(-1.5, 1.5),
                            55 + rng().nextInt(25));
                }
            }
        }.runTaskLater(plugin, 170);

        return DUR;
    }

    // ========================================================================
    //  4. SHATTERED AMETHYST  (10s = 200 ticks)
    // ========================================================================
    private int playAmethyst(Player victim) {
        final int DUR = 200;
        Location origin = victim.getLocation().clone();
        World w = origin.getWorld();
        if (w == null) return 20;

        w.playSound(origin, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.2f, 0.4f);
        w.playSound(origin, Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.8f);

        // Phase 1: Crystal pillars rise from ground (0-60)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 60 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }

                // Rising crystal blocks around the victim
                if (t % 8 == 0) {
                    Material[] mats = {Material.AMETHYST_BLOCK, Material.AMETHYST_CLUSTER, Material.PURPUR_BLOCK};
                    for (int p = 0; p < 3; p++) {
                        double a = rng().nextDouble(Math.PI * 2);
                        double r = 1.5 + rng().nextDouble(1.5);
                        spawnBlock(w, loc.clone().add(Math.cos(a) * r, -1, Math.sin(a) * r),
                                mats[rng().nextInt(mats.length)],
                                0, rng().nextDouble(0.2, 0.5), 0, 80);
                    }
                }

                // Orbiting crystal dust (3 rings tightening)
                for (int ring = 0; ring < 3; ring++) {
                    int points = 6 + ring * 2;
                    for (int i = 0; i < points; i++) {
                        double a = (Math.PI * 2 / points) * i + (t * (0.2 + ring * 0.1));
                        double r = 2.5 - t * 0.025 - ring * 0.3;
                        if (r < 0.5) r = 0.5;
                        double x = Math.cos(a) * r, z = Math.sin(a) * r;
                        double y = ring * 0.8 + 0.3;
                        w.spawnParticle(Particle.DUST, loc.clone().add(x, y, z), 3,
                                0.08, 0.1, 0.08, 0,
                                new Particle.DustOptions(Color.fromRGB(163, 73, 223), 1.8f));
                    }
                }

                if (t % 8 == 0) {
                    w.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 0.6f + t * 0.012f);
                    w.spawnParticle(Particle.END_ROD, loc.clone().add(0, 1, 0), 8, 1.0, 0.8, 1.0, 0.03);
                }
                t += 2;
            }
        }.runTaskTimer(plugin, 0, 2);

        // Phase 2: Victim levitates (tick 30)
        new BukkitRunnable() {
            @Override public void run() {
                if (!victim.isOnline()) return;
                victim.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 140, 1, false, false, false));
            }
        }.runTaskLater(plugin, 30);

        // Phase 3: Crystal encasement (60-150)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 90 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }

                // Dense crystal shell tightening
                for (int i = 0; i < 20; i++) {
                    double a = (Math.PI * 2 / 20) * i + (t * 0.12);
                    double r = 1.0 - t * 0.006;
                    if (r < 0.25) r = 0.25;
                    double x = Math.cos(a) * r, z = Math.sin(a) * r;
                    double y = (i % 5) * 0.45;
                    w.spawnParticle(Particle.DUST, loc.clone().add(x, y, z), 4,
                            0.04, 0.04, 0.04, 0,
                            new Particle.DustOptions(Color.fromRGB(200, 150, 255), 1.6f));
                }
                w.spawnParticle(Particle.END_ROD, loc.clone().add(0, 1, 0), 6, 0.5, 0.8, 0.5, 0.02);

                // Floating crystal blocks orbiting
                if (t % 14 == 0) {
                    double a = rng().nextDouble(Math.PI * 2);
                    double r2 = 1.0 + rng().nextDouble(0.5);
                    FallingBlock fb = w.spawnFallingBlock(
                            loc.clone().add(Math.cos(a) * r2, rng().nextDouble(0, 2), Math.sin(a) * r2),
                            Material.AMETHYST_CLUSTER.createBlockData());
                    fb.setDropItem(false);
                    fb.setHurtEntities(false);
                    fb.setGravity(false);
                    fb.setVelocity(new Vector(0, 0.05, 0));
                    new BukkitRunnable() {
                        @Override public void run() {
                            if (!fb.isDead()) {
                                w.spawnParticle(Particle.DUST, fb.getLocation(), 10, 0.2, 0.2, 0.2, 0,
                                        new Particle.DustOptions(Color.fromRGB(163, 73, 223), 1.2f));
                                fb.remove();
                            }
                        }
                    }.runTaskLater(plugin, 55);
                }

                // Building humming
                if (t % 6 == 0) {
                    w.playSound(loc, Sound.BLOCK_AMETHYST_CLUSTER_STEP, 0.7f, 0.8f + t * 0.012f);
                }
                t += 2;
            }
        }.runTaskTimer(plugin, 60, 2);

        // Phase 4: SHATTER (tick 155)
        new BukkitRunnable() {
            @Override public void run() {
                Location loc = vLoc(victim);
                if (loc == null) return;
                Location c = loc.clone().add(0, 1, 0);

                w.spawnParticle(Particle.DUST, c, 400, 5.0, 5.0, 5.0, 0,
                        new Particle.DustOptions(Color.fromRGB(163, 73, 223), 3.0f));
                w.spawnParticle(Particle.DUST, c, 250, 4.0, 4.0, 4.0, 0,
                        new Particle.DustOptions(Color.fromRGB(200, 150, 255), 2.5f));
                w.spawnParticle(Particle.END_ROD, c, 120, 3.5, 3.5, 3.5, 0.2);
                w.spawnParticle(Particle.EXPLOSION_EMITTER, c, 4, 1.0, 1.0, 1.0, 0);
                w.spawnParticle(Particle.FLASH, c, 3, 0, 0, 0, 0);
                w.playSound(loc, Sound.BLOCK_GLASS_BREAK, 2.0f, 0.4f);
                w.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 2.0f, 0.5f);
                w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.5f);

                // 25 flying crystal shards
                Material[] shards = {Material.AMETHYST_CLUSTER, Material.AMETHYST_BLOCK,
                        Material.PURPUR_BLOCK, Material.PURPLE_STAINED_GLASS};
                for (int b = 0; b < 25; b++) {
                    spawnBlock(w, c, shards[rng().nextInt(shards.length)],
                            rng().nextDouble(-1.5, 1.5), rng().nextDouble(0.4, 2.0), rng().nextDouble(-1.5, 1.5),
                            50 + rng().nextInt(30));
                }
            }
        }.runTaskLater(plugin, 155);

        // Phase 5: Lingering shimmer (160-195)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 40) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }
                w.spawnParticle(Particle.DUST, loc.clone().add(0, 1, 0), 25 - t / 2, 3.0, 3.0, 3.0, 0,
                        new Particle.DustOptions(Color.fromRGB(200, 150, 255), 1.0f));
                w.spawnParticle(Particle.END_ROD, loc.clone().add(0, 1, 0), 8, 2.0, 2.0, 2.0, 0.03);
                if (t % 8 == 0) w.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.5f);
                t += 3;
            }
        }.runTaskTimer(plugin, 160, 3);

        return DUR;
    }

    // ========================================================================
    //  5. ATAQUE ORBITAL  (12s = 240 ticks)
    // ========================================================================
    private int playOrbital(Player victim) {
        final int DUR = 240;
        Location origin = victim.getLocation().clone();
        World w = origin.getWorld();
        if (w == null) return 20;

        // Phase 1: Target lock-on crosshair (0-70)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 70 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }

                // Rotating crosshair with 8 arms
                double radius = 3.0 - t * 0.025;
                for (int arm = 0; arm < 8; arm++) {
                    double a = (Math.PI / 4) * arm + t * 0.12;
                    for (double d = 0.3; d < radius; d += 0.4) {
                        w.spawnParticle(Particle.DUST, loc.clone().add(Math.cos(a) * d, 0.1, Math.sin(a) * d),
                                2, 0.03, 0.01, 0.03, 0,
                                new Particle.DustOptions(Color.RED, 1.2f));
                    }
                }
                // Center pulse
                w.spawnParticle(Particle.END_ROD, loc.clone().add(0, 0.3, 0), 5, 0.15, 0.05, 0.15, 0.01);
                w.spawnParticle(Particle.DUST, loc.clone().add(0, 0.2, 0), 8, 0.3, 0.05, 0.3, 0,
                        new Particle.DustOptions(Color.ORANGE, 1.5f));

                if (t % 12 == 0) {
                    w.playSound(loc, Sound.BLOCK_BEACON_AMBIENT, 0.7f, 1.0f + t * 0.02f);
                    w.playSound(loc, Sound.BLOCK_NOTE_BLOCK_PLING, 0.4f, 1.5f + t * 0.01f);
                }
                t += 2;
            }
        }.runTaskTimer(plugin, 0, 2);

        // Phase 2: Warning beam forming from sky (40-100)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 60 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }

                double maxY = 15 + t * 0.5;
                for (double y = 0; y < maxY; y += 1.0) {
                    double wobble = Math.sin((y + t) * 0.4) * 0.1;
                    w.spawnParticle(Particle.END_ROD, loc.clone().add(wobble, y, wobble),
                            2, 0.04, 0.15, 0.04, 0.003);
                }
                if (t % 8 == 0) w.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.5f + t * 0.01f);

                // Glowstone blocks rising to form beam
                if (t % 12 == 0) {
                    spawnBlock(w, loc.clone().add(rng().nextDouble(-0.5, 0.5), 0, rng().nextDouble(-0.5, 0.5)),
                            Material.GLOWSTONE, 0, rng().nextDouble(0.3, 0.8), 0, 60);
                }
                t += 2;
            }
        }.runTaskTimer(plugin, 40, 2);

        // Phase 3: Victim levitates (tick 70)
        new BukkitRunnable() {
            @Override public void run() {
                if (!victim.isOnline()) return;
                victim.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 160, 2, false, false, false));
                Location loc = vLoc(victim);
                if (loc != null) {
                    w.playSound(loc, Sound.ITEM_TRIDENT_THUNDER, 1.0f, 1.5f);
                    w.spawnParticle(Particle.FLASH, loc, 2, 0, 0, 0, 0);
                }
            }
        }.runTaskLater(plugin, 70);

        // Phase 4: Full beam locked on + energy rings (100-200)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 100 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }

                // Thick beam
                for (double y = -2; y < 35; y += 0.6) {
                    double wobble = Math.sin((y + t) * 0.25) * 0.12;
                    w.spawnParticle(Particle.END_ROD, loc.clone().add(wobble, y, wobble),
                            3, 0.1, 0.08, 0.1, 0.005);
                    if (y < 4 && t % 2 == 0) {
                        w.spawnParticle(Particle.DUST, loc.clone().add(0, y, 0),
                                3, 0.2, 0.08, 0.2, 0,
                                new Particle.DustOptions(Color.WHITE, 2.5f));
                    }
                }

                // Ascending energy rings
                if (t % 3 == 0) {
                    double ringY = (t * 0.8) % 30;
                    for (int i = 0; i < 12; i++) {
                        double a = (Math.PI * 2 / 12) * i + t * 0.15;
                        double r = 1.2;
                        w.spawnParticle(Particle.DUST,
                                loc.clone().add(Math.cos(a) * r, ringY, Math.sin(a) * r),
                                2, 0.04, 0.04, 0.04, 0,
                                new Particle.DustOptions(Color.YELLOW, 1.5f));
                    }
                }

                // Orbiting energy blocks
                if (t % 16 == 0) {
                    Material[] mats = {Material.GLOWSTONE, Material.SEA_LANTERN, Material.GOLD_BLOCK};
                    double a = rng().nextDouble(Math.PI * 2);
                    FallingBlock fb = w.spawnFallingBlock(
                            loc.clone().add(Math.cos(a) * 2, rng().nextDouble(0, 3), Math.sin(a) * 2),
                            mats[rng().nextInt(mats.length)].createBlockData());
                    fb.setDropItem(false);
                    fb.setHurtEntities(false);
                    fb.setGravity(false);
                    fb.setVelocity(new Vector(0, 0.1, 0));
                    new BukkitRunnable() {
                        @Override public void run() {
                            if (!fb.isDead()) {
                                w.spawnParticle(Particle.END_ROD, fb.getLocation(), 10, 0.2, 0.2, 0.2, 0.03);
                                fb.remove();
                            }
                        }
                    }.runTaskLater(plugin, 50);
                }

                if (t % 6 == 0) w.playSound(loc, Sound.BLOCK_BEACON_AMBIENT, 0.8f, 2.0f);
                t += 2;
            }
        }.runTaskTimer(plugin, 100, 2);

        // Phase 5: IMPACT (tick 205)
        new BukkitRunnable() {
            @Override public void run() {
                Location loc = vLoc(victim);
                if (loc == null) return;

                w.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 8, 2.0, 2.0, 2.0, 0);
                w.spawnParticle(Particle.END_ROD, loc, 400, 6.0, 6.0, 6.0, 0.25);
                w.spawnParticle(Particle.FLASH, loc, 6, 1, 1, 1, 0);
                w.spawnParticle(Particle.DUST, loc, 200, 5.0, 5.0, 5.0, 0,
                        new Particle.DustOptions(Color.WHITE, 3.5f));
                w.spawnParticle(Particle.DUST, loc, 150, 4.0, 4.0, 4.0, 0,
                        new Particle.DustOptions(Color.YELLOW, 2.5f));
                w.spawnParticle(Particle.DUST, loc, 100, 3.0, 3.0, 3.0, 0,
                        new Particle.DustOptions(Color.ORANGE, 2.0f));
                w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.6f);
                w.playSound(loc, Sound.ITEM_TRIDENT_THUNDER, 2.0f, 0.5f);
                w.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 0.8f);

                // Massive block debris
                Material[] debris = {Material.GLOWSTONE, Material.SEA_LANTERN, Material.GOLD_BLOCK,
                        Material.QUARTZ_BLOCK, Material.WHITE_CONCRETE, Material.YELLOW_CONCRETE};
                for (int b = 0; b < 30; b++) {
                    spawnBlock(w, loc.clone().add(0, 1.5, 0), debris[rng().nextInt(debris.length)],
                            rng().nextDouble(-2.0, 2.0), rng().nextDouble(0.5, 2.5), rng().nextDouble(-2.0, 2.0),
                            55 + rng().nextInt(30));
                }
            }
        }.runTaskLater(plugin, 205);

        // Phase 6: Firework aftermath (210-235)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 30) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) loc = origin.clone().add(0, 4, 0);

                for (int fw = 0; fw < 3; fw++) {
                    double ox = rng().nextDouble(-4, 4), oy = rng().nextDouble(-1, 5), oz = rng().nextDouble(-4, 4);
                    Location fwLoc = loc.clone().add(ox, oy, oz);
                    w.spawnParticle(Particle.END_ROD, fwLoc, 20, 0.8, 0.8, 0.8, 0.1);
                    w.spawnParticle(Particle.DUST, fwLoc, 12, 0.5, 0.5, 0.5, 0,
                            new Particle.DustOptions(Color.WHITE, 1.8f));
                }

                if (t % 4 == 0) {
                    w.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8f, 0.8f + t * 0.03f);
                    w.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.6f, 1.0f + t * 0.02f);
                }
                t += 3;
            }
        }.runTaskTimer(plugin, 210, 3);

        return DUR;
    }
}
