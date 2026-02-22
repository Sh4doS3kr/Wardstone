package com.moonlight.coreprotect.finishers;

import org.bukkit.*;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

public class FinisherEffects {

    private final Plugin plugin;
    public static final String GHOST_TAG = "finisher_ghost";

    public FinisherEffects(Plugin plugin) {
        this.plugin = plugin;
    }

    public int play(FinisherType type, Player victim, Player killer) {
        switch (type) {
            case THUNDER_JUDGMENT: return playThunder(victim);
            case VOID_INVOCATION: return playVoid(victim);
            case BLOOD_ERUPTION: return playBlood(victim);
            case SHATTERED_AMETHYST: return playAmethyst(victim);
            case ORBITAL_STRIKE: return playOrbital(victim);
            case HELLFIRE: return playHellfire(victim);
            case ICE_STORM: return playIce(victim);
            case DRAGON_WRATH: return playDragon(victim);
            case SOUL_VORTEX: return playSoulVortex(victim);
            case WITHER_STORM: return playWitherStorm(victim);
            case SCULK_RESONANCE: return playSculkResonance(victim);
            default: return 40;
        }
    }

    private Location vLoc(Player v) {
        return v.isOnline() ? v.getLocation() : null;
    }

    private ThreadLocalRandom rng() {
        return ThreadLocalRandom.current();
    }

    private FallingBlock spawnGhost(World w, Location loc, Material mat, double vx, double vy, double vz, int life) {
        FallingBlock fb = w.spawnFallingBlock(loc, mat.createBlockData());
        fb.setDropItem(false);
        fb.setHurtEntities(false);
        fb.setMetadata(GHOST_TAG, new FixedMetadataValue(plugin, true));
        fb.setVelocity(new Vector(vx, vy, vz));
        new BukkitRunnable() {
            @Override public void run() {
                if (!fb.isDead()) { w.spawnParticle(Particle.CLOUD, fb.getLocation(), 4, 0.2, 0.2, 0.2, 0.01); fb.remove(); }
            }
        }.runTaskLater(plugin, life);
        return fb;
    }

    private FallingBlock spawnGhostNG(World w, Location loc, Material mat, double vx, double vy, double vz, int life) {
        FallingBlock fb = w.spawnFallingBlock(loc, mat.createBlockData());
        fb.setDropItem(false);
        fb.setHurtEntities(false);
        fb.setGravity(false);
        fb.setMetadata(GHOST_TAG, new FixedMetadataValue(plugin, true));
        fb.setVelocity(new Vector(vx, vy, vz));
        new BukkitRunnable() {
            @Override public void run() {
                if (!fb.isDead()) { w.spawnParticle(Particle.CLOUD, fb.getLocation(), 3, 0.15, 0.15, 0.15, 0.01); fb.remove(); }
            }
        }.runTaskLater(plugin, life);
        return fb;
    }

    private void spawnGhostBoom(World w, Location loc, Material mat, double vx, double vy, double vz, int life) {
        FallingBlock fb = w.spawnFallingBlock(loc, mat.createBlockData());
        fb.setDropItem(false);
        fb.setHurtEntities(false);
        fb.setMetadata(GHOST_TAG, new FixedMetadataValue(plugin, true));
        fb.setVelocity(new Vector(vx, vy, vz));
        new BukkitRunnable() {
            @Override public void run() {
                if (!fb.isDead()) {
                    Location d = fb.getLocation();
                    w.spawnParticle(Particle.EXPLOSION_EMITTER, d, 1, 0.2, 0.2, 0.2, 0);
                    w.spawnParticle(Particle.CLOUD, d, 8, 0.4, 0.4, 0.4, 0.02);
                    w.playSound(d, Sound.ENTITY_GENERIC_EXPLODE, 0.3f, 1.5f);
                    fb.remove();
                }
            }
        }.runTaskLater(plugin, life);
    }

    // ========================================================================
    // 1. EL JUICIO DEL TRUENO — GROUND CAGE (10s = 200t)
    // ========================================================================
    private int playThunder(Player victim) {
        final int DUR = 200;
        Location origin = victim.getLocation().clone();
        World w = origin.getWorld();
        if (w == null) return 20;

        w.playSound(origin, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.2f, 0.3f);
        w.playSound(origin, Sound.ENTITY_WARDEN_EMERGE, 0.6f, 1.2f);

        // Phase 1: Electric cage pillars tighten (0-80)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 80 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }
                double r = 4.0 - t * 0.035;
                for (int i = 0; i < 24; i++) {
                    double a = (Math.PI * 2 / 24) * i + t * 0.1;
                    w.spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(Math.cos(a) * r, 0.15, Math.sin(a) * r), 5, 0.08, 0.04, 0.08, 0.02);
                }
                if (t % 4 == 0) {
                    for (int p = 0; p < 8; p++) {
                        double a = (Math.PI * 2 / 8) * p + t * 0.05;
                        for (double y = 0; y < 3.5; y += 0.5)
                            w.spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(Math.cos(a) * r, y, Math.sin(a) * r), 3, 0.04, 0.12, 0.04, 0.01);
                    }
                }
                if (t % 10 == 0) {
                    Material[] m = { Material.COPPER_BLOCK, Material.OXIDIZED_COPPER, Material.LIGHTNING_ROD };
                    for (int p = 0; p < 4; p++) {
                        double a = (Math.PI * 2 / 4) * p + t * 0.05;
                        spawnGhostNG(w, loc.clone().add(Math.cos(a) * r, rng().nextDouble(0.5, 3), Math.sin(a) * r), m[rng().nextInt(m.length)], 0, 0.01, 0, 55);
                    }
                }
                if (t % 12 == 0) w.playSound(loc, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8f, 1.5f + t * 0.01f);
                t += 2;
            }
        }.runTaskTimer(plugin, 0, 2);

        // Phase 2: 12 lightning strikes closing in
        int[] bolts = { 20, 32, 44, 56, 68, 80, 92, 105, 118, 130, 140, 150 };
        for (int i = 0; i < bolts.length; i++) {
            final int idx = i;
            new BukkitRunnable() {
                @Override public void run() {
                    Location loc = vLoc(victim);
                    if (loc == null) return;
                    double spread = 3.5 * (1.0 - idx / (double) bolts.length);
                    double a = rng().nextDouble(Math.PI * 2);
                    Location strike = loc.clone().add(Math.cos(a) * spread, 0, Math.sin(a) * spread);
                    w.strikeLightningEffect(strike);
                    w.spawnParticle(Particle.ELECTRIC_SPARK, strike.clone().add(0, 0.5, 0), 50 + idx * 5, 1.0, 1.5, 1.0, 0.08);
                    Material[] m = { Material.OXIDIZED_COPPER, Material.COPPER_BLOCK, Material.IRON_BLOCK };
                    for (int b = 0; b < 3 + idx / 2; b++)
                        spawnGhost(w, strike.clone().add(0, 0.5, 0), m[rng().nextInt(m.length)],
                                rng().nextDouble(-0.7, 0.7), rng().nextDouble(0.3, 0.9), rng().nextDouble(-0.7, 0.7), 48 + rng().nextInt(18));
                }
            }.runTaskLater(plugin, bolts[idx]);
        }

        // Phase 3: Storm cloud above (60-180)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 120 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }
                w.spawnParticle(Particle.CLOUD, loc.clone().add(0, 4, 0), 12, 2.5, 0.3, 2.5, 0.02);
                w.spawnParticle(Particle.DUST, loc.clone().add(0, 4.5, 0), 8, 3, 0.4, 3, 0, new Particle.DustOptions(Color.fromRGB(30, 30, 50), 3.5f));
                for (int s = 0; s < 3; s++) {
                    double sx = rng().nextDouble(-2.5, 2.5), sz = rng().nextDouble(-2.5, 2.5);
                    w.spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(sx, rng().nextDouble(0, 0.8), sz), 6, 0.15, 0.08, 0.15, 0.03);
                }
                if (t % 16 == 0) w.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 1.2f);
                t += 2;
            }
        }.runTaskTimer(plugin, 60, 2);

        // Phase 4: FINAL — 5x strike + ground debris ring (tick 170)
        new BukkitRunnable() {
            @Override public void run() {
                Location loc = vLoc(victim);
                if (loc == null) return;
                for (int i = 0; i < 5; i++) w.strikeLightningEffect(loc);
                w.spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(0, 0.5, 0), 500, 5, 2, 5, 0.2);
                w.spawnParticle(Particle.CLOUD, loc, 100, 3, 1.5, 3, 0.08);
                w.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 6, 1.5, 0.5, 1.5, 0);
                w.spawnParticle(Particle.FLASH, loc, 4, 0, 0, 0, 0);
                w.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 0.4f);
                w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.7f);
                Material[] deb = { Material.OXIDIZED_COPPER, Material.COPPER_BLOCK, Material.IRON_BLOCK, Material.GOLD_BLOCK, Material.LIGHTNING_ROD, Material.CHAIN };
                for (int b = 0; b < 35; b++) {
                    double a = (Math.PI * 2 / 35) * b;
                    double sp = 0.7 + rng().nextDouble(0.8);
                    spawnGhostBoom(w, loc.clone().add(0, 0.5, 0), deb[rng().nextInt(deb.length)],
                            Math.cos(a) * sp, rng().nextDouble(0.15, 0.5), Math.sin(a) * sp, 50 + rng().nextInt(20));
                }
            }
        }.runTaskLater(plugin, 170);

        // Phase 5: Aftershock sparks (175-195)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 25) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) loc = origin;
                w.spawnParticle(Particle.ELECTRIC_SPARK, loc, 40 - t * 2, 4, 0.5, 4, 0.05);
                if (t % 6 == 0) w.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5f, 1.5f);
                t += 3;
            }
        }.runTaskTimer(plugin, 175, 3);

        return DUR;
    }

    // ========================================================================
    // 2. INVOCACIÓN DEL VACÍO — GROUND SPIN VORTEX (10s = 200t)
    // ========================================================================
    private int playVoid(Player victim) {
        final int DUR = 200;
        Location origin = victim.getLocation().clone();
        World w = origin.getWorld();
        if (w == null) return 20;

        w.playSound(origin, Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f, 0.2f);
        w.playSound(origin, Sound.ENTITY_WARDEN_EMERGE, 1.0f, 0.4f);

        // Phase 1: Dark portal circle on ground (0-50)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 50 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }
                double r = 0.5 + t * 0.08;
                for (int i = 0; i < 20; i++) {
                    double a = (Math.PI * 2 / 20) * i + t * 0.15;
                    w.spawnParticle(Particle.DRAGON_BREATH, loc.clone().add(Math.cos(a) * r, 0.1, Math.sin(a) * r), 3, 0.04, 0.02, 0.04, 0.002);
                    w.spawnParticle(Particle.SQUID_INK, loc.clone().add(Math.cos(a) * r * 0.6, 0.15, Math.sin(a) * r * 0.6), 2, 0.05, 0.02, 0.05, 0.005);
                }
                w.spawnParticle(Particle.PORTAL, loc.clone().add(0, 0.3, 0), 15, r * 0.4, 0.1, r * 0.4, 0.5);
                if (t % 10 == 0) w.playSound(loc, Sound.BLOCK_PORTAL_AMBIENT, 0.7f, 0.3f + t * 0.02f);
                t += 2;
            }
        }.runTaskTimer(plugin, 0, 2);

        // Phase 2: Victim spins rapidly
        new BukkitRunnable() {
            int t = 0; float spin = 15f;
            @Override public void run() {
                if (t >= 150 || !victim.isOnline()) { cancel(); return; }
                Location loc = victim.getLocation();
                spin = Math.min(spin + 0.8f, 90f);
                loc.setYaw(loc.getYaw() + spin);
                victim.teleport(loc);
                if (t % 4 == 0) w.spawnParticle(Particle.PORTAL, loc.clone().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.6);
                t++;
            }
        }.runTaskTimer(plugin, 30, 1);

        // Phase 3: Vortex arms + blocks sucked in (40-170)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 130 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }
                for (int arm = 0; arm < 6; arm++) {
                    double a = t * 0.5 + arm * Math.PI / 3;
                    double maxR = 3.5 - t * 0.02; if (maxR < 0.3) maxR = 0.3;
                    for (double d = 0.3; d < maxR; d += 0.5)
                        w.spawnParticle(Particle.DRAGON_BREATH, loc.clone().add(Math.cos(a + d * 0.3) * d, 0.15, Math.sin(a + d * 0.3) * d), 2, 0.04, 0.02, 0.04, 0.002);
                }
                w.spawnParticle(Particle.SQUID_INK, loc.clone().add(0, 0.5, 0), 8, 0.2, 0.3, 0.2, 0.02);
                if (t % 8 == 0) {
                    Material[] m = { Material.OBSIDIAN, Material.CRYING_OBSIDIAN, Material.END_STONE, Material.PURPLE_CONCRETE, Material.BLACK_CONCRETE };
                    for (int b = 0; b < 4; b++) {
                        double a2 = rng().nextDouble(Math.PI * 2);
                        double dist = 3 + rng().nextDouble(2);
                        spawnGhost(w, loc.clone().add(Math.cos(a2) * dist, 0.5, Math.sin(a2) * dist), m[rng().nextInt(m.length)],
                                -Math.cos(a2) * 0.22, rng().nextDouble(0.05, 0.15), -Math.sin(a2) * 0.22, 35 + rng().nextInt(15));
                    }
                }
                if (t % 14 == 0) { w.playSound(loc, Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.4f); w.spawnParticle(Particle.SONIC_BOOM, loc, 1, 0, 0, 0, 0); }
                t += 2;
            }
        }.runTaskTimer(plugin, 40, 2);

        // Phase 4: IMPLOSION (tick 175)
        new BukkitRunnable() {
            @Override public void run() {
                Location loc = vLoc(victim);
                if (loc == null) return;
                w.spawnParticle(Particle.SQUID_INK, loc, 300, 5, 1.5, 5, 0.15);
                w.spawnParticle(Particle.DRAGON_BREATH, loc, 200, 4, 1, 4, 0.08);
                w.spawnParticle(Particle.PORTAL, loc, 500, 5, 2, 5, 2);
                w.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 5, 1, 0.5, 1, 0);
                w.spawnParticle(Particle.SONIC_BOOM, loc, 3, 1, 0.5, 1, 0);
                w.playSound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 0.3f);
                w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.4f);
                Material[] deb = { Material.OBSIDIAN, Material.CRYING_OBSIDIAN, Material.END_STONE, Material.BLACK_CONCRETE, Material.PURPLE_CONCRETE };
                for (int b = 0; b < 30; b++) {
                    double a = (Math.PI * 2 / 30) * b;
                    spawnGhostBoom(w, loc.clone().add(0, 0.5, 0), deb[rng().nextInt(deb.length)],
                            Math.cos(a) * rng().nextDouble(0.5, 1.2), rng().nextDouble(0.1, 0.4), Math.sin(a) * rng().nextDouble(0.5, 1.2), 50 + rng().nextInt(20));
                }
            }
        }.runTaskLater(plugin, 175);

        return DUR;
    }

    // ========================================================================
    // 3. ERUPCIÓN DE SANGRE — GROUND GEYSER (10s = 200t)
    // ========================================================================
    private int playBlood(Player victim) {
        final int DUR = 200;
        Location origin = victim.getLocation().clone();
        World w = origin.getWorld();
        if (w == null) return 20;

        w.playSound(origin, Sound.ENTITY_WARDEN_EMERGE, 0.8f, 0.7f);

        // Phase 1: Blood pool expanding (0-60)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 60 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }
                double r = 0.5 + t * 0.1;
                for (int i = 0; i < 24; i++) {
                    double a = (Math.PI * 2 / 24) * i;
                    for (double d = 0.3; d < r; d += 0.6)
                        w.spawnParticle(Particle.DUST, loc.clone().add(Math.cos(a) * d, 0.05, Math.sin(a) * d), 1,
                                0.06, 0.01, 0.06, 0, new Particle.DustOptions(Color.fromRGB(120 + rng().nextInt(60), 0, 0), 2.0f));
                }
                if (t % 6 == 0) {
                    w.playSound(loc, Sound.BLOCK_HONEY_BLOCK_SLIDE, 0.8f, 0.3f);
                    spawnGhost(w, loc.clone().add(rng().nextDouble(-r, r), 0, rng().nextDouble(-r, r)),
                            Material.RED_CONCRETE, 0, rng().nextDouble(0.1, 0.2), 0, 40);
                }
                t += 2;
            }
        }.runTaskTimer(plugin, 0, 2);

        // Phase 2: Geysers erupt in ring (40-150)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 110 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }
                if (t % 6 == 0) {
                    for (int g = 0; g < 6; g++) {
                        double a = (Math.PI * 2 / 6) * g + t * 0.04;
                        double gr = 2 + Math.sin(t * 0.05 + g) * 0.5;
                        Location gey = loc.clone().add(Math.cos(a) * gr, 0, Math.sin(a) * gr);
                        double h = 1.5 + Math.sin(t * 0.1 + g * 1.2) * 1.5;
                        for (double y = 0; y < h; y += 0.3)
                            w.spawnParticle(Particle.DUST, gey.clone().add(0, y, 0), 4, 0.06, 0.04, 0.06, 0,
                                    new Particle.DustOptions(Color.fromRGB(160, 0, 0), 2.0f));
                    }
                }
                w.spawnParticle(Particle.DUST, loc.clone().add(0, 0.8, 0), 10, 1.5, 0.3, 1.5, 0, new Particle.DustOptions(Color.RED, 1.5f));
                if (t % 10 == 0) {
                    Material[] m = { Material.RED_CONCRETE, Material.RED_CONCRETE_POWDER, Material.REDSTONE_BLOCK, Material.NETHER_WART_BLOCK };
                    for (int b = 0; b < 3; b++) {
                        double a = rng().nextDouble(Math.PI * 2);
                        double br = 1 + rng().nextDouble(2.5);
                        spawnGhost(w, loc.clone().add(Math.cos(a) * br, -0.5, Math.sin(a) * br), m[rng().nextInt(m.length)],
                                rng().nextDouble(-0.12, 0.12), rng().nextDouble(0.3, 0.8), rng().nextDouble(-0.12, 0.12), 55);
                    }
                }
                if (t % 12 == 0) w.playSound(loc, Sound.ENTITY_SLIME_SQUISH, 1.0f, 0.4f);
                t += 2;
            }
        }.runTaskTimer(plugin, 40, 2);

        // Phase 3: Blood wave rings (80-165)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 85 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }
                double wr = (t % 30) * 0.25;
                for (int i = 0; i < 20; i++) {
                    double a = (Math.PI * 2 / 20) * i;
                    w.spawnParticle(Particle.DUST, loc.clone().add(Math.cos(a) * wr, 0.1, Math.sin(a) * wr), 2,
                            0.04, 0.01, 0.04, 0, new Particle.DustOptions(Color.fromRGB(200, 0, 0), 1.8f));
                }
                t += 2;
            }
        }.runTaskTimer(plugin, 80, 2);

        // Phase 4: ERUPTION (tick 170)
        new BukkitRunnable() {
            @Override public void run() {
                Location loc = vLoc(victim);
                if (loc == null) return;
                w.spawnParticle(Particle.DUST, loc, 400, 5, 2, 5, 0, new Particle.DustOptions(Color.RED, 3.5f));
                w.spawnParticle(Particle.DUST, loc, 250, 4, 3, 4, 0, new Particle.DustOptions(Color.fromRGB(139, 0, 0), 2.5f));
                w.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 5, 1.5, 0.5, 1.5, 0);
                w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.5f);
                w.playSound(loc, Sound.ENTITY_SLIME_SQUISH, 1.5f, 0.2f);
                Material[] deb = { Material.RED_CONCRETE, Material.RED_CONCRETE_POWDER, Material.REDSTONE_BLOCK, Material.RED_WOOL, Material.NETHER_WART_BLOCK, Material.RED_MUSHROOM_BLOCK };
                for (int b = 0; b < 35; b++) {
                    double a = (Math.PI * 2 / 35) * b;
                    double sp = 0.5 + rng().nextDouble(0.7);
                    spawnGhostBoom(w, loc.clone().add(0, 0.3, 0), deb[rng().nextInt(deb.length)],
                            Math.cos(a) * sp, rng().nextDouble(0.15, 0.6), Math.sin(a) * sp, 50 + rng().nextInt(20));
                }
            }
        }.runTaskLater(plugin, 170);

        return DUR;
    }

    // ========================================================================
    // 4. SHATTERED AMETHYST — CRYSTAL PRISON (10s = 200t)
    // Crystals grow around victim like a cage, then shatter outward.
    // ========================================================================
    private int playAmethyst(Player victim) {
        final int DUR = 200;
        Location origin = victim.getLocation().clone();
        World w = origin.getWorld();
        if (w == null) return 20;

        w.playSound(origin, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.2f, 0.4f);
        w.playSound(origin, Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.8f);

        // Phase 1: Crystal pillars grow from ground (0-70)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 70 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }
                // Hexagonal crystal walls rising
                if (t % 6 == 0) {
                    Material[] m = { Material.AMETHYST_BLOCK, Material.AMETHYST_CLUSTER, Material.PURPUR_BLOCK, Material.PURPLE_STAINED_GLASS };
                    for (int p = 0; p < 6; p++) {
                        double a = (Math.PI * 2 / 6) * p;
                        double r = 2.0;
                        double yOff = (t / 6) * 0.5;
                        if (yOff > 3.5) yOff = 3.5;
                        spawnGhostNG(w, loc.clone().add(Math.cos(a) * r, yOff, Math.sin(a) * r), m[rng().nextInt(m.length)], 0, 0, 0, 120);
                    }
                }
                // Crystal dust spiraling
                for (int ring = 0; ring < 3; ring++) {
                    int pts = 8 + ring * 2;
                    for (int i = 0; i < pts; i++) {
                        double a = (Math.PI * 2 / pts) * i + t * (0.15 + ring * 0.08);
                        double r = 2.2 - ring * 0.3;
                        w.spawnParticle(Particle.DUST, loc.clone().add(Math.cos(a) * r, ring * 0.7 + 0.3, Math.sin(a) * r), 2,
                                0.06, 0.08, 0.06, 0, new Particle.DustOptions(Color.fromRGB(163, 73, 223), 1.8f));
                    }
                }
                if (t % 8 == 0) { w.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 0.6f + t * 0.01f); w.spawnParticle(Particle.END_ROD, loc.clone().add(0, 1, 0), 6, 1.0, 0.8, 1.0, 0.03); }
                t += 2;
            }
        }.runTaskTimer(plugin, 0, 2);

        // Phase 2: Crystal cage tightening + inner glow (70-155)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 85 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }
                double r = 2.0 - t * 0.015; if (r < 0.5) r = 0.5;
                for (int i = 0; i < 24; i++) {
                    double a = (Math.PI * 2 / 24) * i + t * 0.1;
                    double y = (i % 6) * 0.5;
                    w.spawnParticle(Particle.DUST, loc.clone().add(Math.cos(a) * r, y, Math.sin(a) * r), 3,
                            0.03, 0.03, 0.03, 0, new Particle.DustOptions(Color.fromRGB(200, 150, 255), 1.6f));
                }
                w.spawnParticle(Particle.END_ROD, loc.clone().add(0, 1, 0), 5, 0.4, 0.7, 0.4, 0.02);
                if (t % 12 == 0) {
                    spawnGhostNG(w, loc.clone().add(rng().nextDouble(-1, 1), rng().nextDouble(0.5, 2.5), rng().nextDouble(-1, 1)),
                            Material.AMETHYST_CLUSTER, 0, 0.02, 0, 50);
                }
                if (t % 6 == 0) w.playSound(loc, Sound.BLOCK_AMETHYST_CLUSTER_STEP, 0.7f, 0.8f + t * 0.01f);
                t += 2;
            }
        }.runTaskTimer(plugin, 70, 2);

        // Phase 3: SHATTER outward (tick 160)
        new BukkitRunnable() {
            @Override public void run() {
                Location loc = vLoc(victim);
                if (loc == null) return;
                Location c = loc.clone().add(0, 1, 0);
                w.spawnParticle(Particle.DUST, c, 400, 5, 5, 5, 0, new Particle.DustOptions(Color.fromRGB(163, 73, 223), 3.0f));
                w.spawnParticle(Particle.DUST, c, 250, 4, 4, 4, 0, new Particle.DustOptions(Color.fromRGB(200, 150, 255), 2.5f));
                w.spawnParticle(Particle.END_ROD, c, 120, 3.5, 3.5, 3.5, 0.2);
                w.spawnParticle(Particle.EXPLOSION_EMITTER, c, 4, 1, 1, 1, 0);
                w.spawnParticle(Particle.FLASH, c, 3, 0, 0, 0, 0);
                w.playSound(loc, Sound.BLOCK_GLASS_BREAK, 2.0f, 0.4f);
                w.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 2.0f, 0.5f);
                w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.5f);
                Material[] sh = { Material.AMETHYST_CLUSTER, Material.AMETHYST_BLOCK, Material.PURPUR_BLOCK, Material.PURPLE_STAINED_GLASS };
                for (int b = 0; b < 40; b++) {
                    double a = (Math.PI * 2 / 40) * b;
                    double sp = 0.6 + rng().nextDouble(1.0);
                    spawnGhostBoom(w, c, sh[rng().nextInt(sh.length)],
                            Math.cos(a) * sp, rng().nextDouble(0.2, 1.2), Math.sin(a) * sp, 48 + rng().nextInt(25));
                }
            }
        }.runTaskLater(plugin, 160);

        // Phase 4: Shimmer fade (165-195)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 35) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }
                w.spawnParticle(Particle.DUST, loc.clone().add(0, 1, 0), 25 - t, 3, 3, 3, 0, new Particle.DustOptions(Color.fromRGB(200, 150, 255), 1.0f));
                w.spawnParticle(Particle.END_ROD, loc.clone().add(0, 1, 0), 6, 2, 2, 2, 0.03);
                if (t % 8 == 0) w.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.5f);
                t += 3;
            }
        }.runTaskTimer(plugin, 165, 3);

        return DUR;
    }

    // ========================================================================
    // 5. ATAQUE ORBITAL — SKY BEAM (12s = 240t)
    // ========================================================================
    private int playOrbital(Player victim) {
        final int DUR = 240;
        Location origin = victim.getLocation().clone();
        World w = origin.getWorld();
        if (w == null) return 20;

        // Phase 1: Target crosshair on ground (0-70)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 70 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }
                double r = 3.0 - t * 0.025;
                for (int arm = 0; arm < 8; arm++) {
                    double a = (Math.PI / 4) * arm + t * 0.12;
                    for (double d = 0.3; d < r; d += 0.4)
                        w.spawnParticle(Particle.DUST, loc.clone().add(Math.cos(a) * d, 0.1, Math.sin(a) * d), 2, 0.03, 0.01, 0.03, 0, new Particle.DustOptions(Color.RED, 1.2f));
                }
                w.spawnParticle(Particle.END_ROD, loc.clone().add(0, 0.3, 0), 5, 0.15, 0.05, 0.15, 0.01);
                if (t % 12 == 0) { w.playSound(loc, Sound.BLOCK_BEACON_AMBIENT, 0.7f, 1.0f + t * 0.02f); w.playSound(loc, Sound.BLOCK_NOTE_BLOCK_PLING, 0.4f, 1.5f + t * 0.01f); }
                t += 2;
            }
        }.runTaskTimer(plugin, 0, 2);

        // Phase 2: Beam forming from sky (40-100)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 60 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }
                double maxY = 15 + t * 0.5;
                for (double y = 0; y < maxY; y += 1.0) {
                    double wb = Math.sin((y + t) * 0.4) * 0.1;
                    w.spawnParticle(Particle.END_ROD, loc.clone().add(wb, y, wb), 2, 0.04, 0.12, 0.04, 0.003);
                }
                if (t % 8 == 0) w.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.5f + t * 0.01f);
                if (t % 12 == 0)
                    spawnGhost(w, loc.clone().add(rng().nextDouble(-0.5, 0.5), 0, rng().nextDouble(-0.5, 0.5)),
                            Material.GLOWSTONE, 0, rng().nextDouble(0.3, 0.8), 0, 60);
                t += 2;
            }
        }.runTaskTimer(plugin, 40, 2);

        // Phase 3: Levitate (tick 70)
        new BukkitRunnable() {
            @Override public void run() {
                if (!victim.isOnline()) return;
                victim.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 160, 2, false, false, false));
                Location loc = vLoc(victim);
                if (loc != null) { w.playSound(loc, Sound.ITEM_TRIDENT_THUNDER, 1.0f, 1.5f); w.spawnParticle(Particle.FLASH, loc, 2, 0, 0, 0, 0); }
            }
        }.runTaskLater(plugin, 70);

        // Phase 4: Full beam + energy rings (100-200)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 100 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }
                for (double y = -2; y < 35; y += 0.6) {
                    double wb = Math.sin((y + t) * 0.25) * 0.12;
                    w.spawnParticle(Particle.END_ROD, loc.clone().add(wb, y, wb), 3, 0.1, 0.08, 0.1, 0.005);
                    if (y < 4 && t % 2 == 0) w.spawnParticle(Particle.DUST, loc.clone().add(0, y, 0), 3, 0.2, 0.08, 0.2, 0, new Particle.DustOptions(Color.WHITE, 2.5f));
                }
                if (t % 3 == 0) {
                    double ry = (t * 0.8) % 30;
                    for (int i = 0; i < 12; i++) {
                        double a = (Math.PI * 2 / 12) * i + t * 0.15;
                        w.spawnParticle(Particle.DUST, loc.clone().add(Math.cos(a) * 1.2, ry, Math.sin(a) * 1.2), 2, 0.04, 0.04, 0.04, 0, new Particle.DustOptions(Color.YELLOW, 1.5f));
                    }
                }
                if (t % 16 == 0) {
                    Material[] m = { Material.GLOWSTONE, Material.SEA_LANTERN, Material.GOLD_BLOCK };
                    double a = rng().nextDouble(Math.PI * 2);
                    spawnGhostNG(w, loc.clone().add(Math.cos(a) * 2, rng().nextDouble(0, 3), Math.sin(a) * 2), m[rng().nextInt(m.length)], 0, 0.1, 0, 50);
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
                w.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 8, 2, 2, 2, 0);
                w.spawnParticle(Particle.END_ROD, loc, 400, 6, 6, 6, 0.25);
                w.spawnParticle(Particle.FLASH, loc, 6, 1, 1, 1, 0);
                w.spawnParticle(Particle.DUST, loc, 200, 5, 5, 5, 0, new Particle.DustOptions(Color.WHITE, 3.5f));
                w.spawnParticle(Particle.DUST, loc, 150, 4, 4, 4, 0, new Particle.DustOptions(Color.YELLOW, 2.5f));
                w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.6f);
                w.playSound(loc, Sound.ITEM_TRIDENT_THUNDER, 2.0f, 0.5f);
                w.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 0.8f);
                Material[] deb = { Material.GLOWSTONE, Material.SEA_LANTERN, Material.GOLD_BLOCK, Material.QUARTZ_BLOCK, Material.WHITE_CONCRETE, Material.YELLOW_CONCRETE };
                for (int b = 0; b < 35; b++)
                    spawnGhostBoom(w, loc.clone().add(0, 1.5, 0), deb[rng().nextInt(deb.length)],
                            rng().nextDouble(-2, 2), rng().nextDouble(0.5, 2.5), rng().nextDouble(-2, 2), 55 + rng().nextInt(25));
            }
        }.runTaskLater(plugin, 205);

        // Phase 6: Fireworks (210-235)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 30) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) loc = origin.clone().add(0, 4, 0);
                for (int f = 0; f < 3; f++) {
                    Location fl = loc.clone().add(rng().nextDouble(-4, 4), rng().nextDouble(-1, 5), rng().nextDouble(-4, 4));
                    w.spawnParticle(Particle.END_ROD, fl, 20, 0.8, 0.8, 0.8, 0.1);
                }
                if (t % 4 == 0) { w.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8f, 0.8f + t * 0.03f); w.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.6f, 1.0f + t * 0.02f); }
                t += 3;
            }
        }.runTaskTimer(plugin, 210, 3);

        return DUR;
    }

    // ========================================================================
    // 6. INFIERNO DEMONÍACO — GROUND LAVA PILLARS (10s = 200t)
    // ========================================================================
    private int playHellfire(Player victim) {
        final int DUR = 200;
        Location origin = victim.getLocation().clone();
        World w = origin.getWorld();
        if (w == null) return 20;

        w.playSound(origin, Sound.ENTITY_BLAZE_AMBIENT, 1.2f, 0.3f);
        w.playSound(origin, Sound.ENTITY_WARDEN_EMERGE, 0.8f, 0.6f);

        // Phase 1: Expanding lava pool + fire ring (0-70)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 70 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }
                double r = 1 + t * 0.05;
                for (int i = 0; i < 16; i++) {
                    double a = (Math.PI * 2 / 16) * i + t * 0.08;
                    w.spawnParticle(Particle.FLAME, loc.clone().add(Math.cos(a) * r, 0.15, Math.sin(a) * r), 4, 0.06, 0.12, 0.06, 0.01);
                    w.spawnParticle(Particle.LAVA, loc.clone().add(Math.cos(a) * r * 0.5, 0.08, Math.sin(a) * r * 0.5), 1, 0.04, 0.02, 0.04, 0);
                }
                w.spawnParticle(Particle.DUST, loc.clone().add(0, 0.08, 0), 10, r * 0.5, 0.02, r * 0.5, 0, new Particle.DustOptions(Color.fromRGB(200, 50, 0), 2.0f));
                if (t % 8 == 0) {
                    Material[] m = { Material.MAGMA_BLOCK, Material.NETHERRACK, Material.NETHER_BRICKS };
                    for (int p = 0; p < 3; p++) {
                        double a2 = rng().nextDouble(Math.PI * 2);
                        double r2 = 1 + rng().nextDouble(2);
                        spawnGhost(w, loc.clone().add(Math.cos(a2) * r2, -0.5, Math.sin(a2) * r2), m[rng().nextInt(m.length)],
                                0, rng().nextDouble(0.3, 0.7), 0, 65);
                    }
                    w.playSound(loc, Sound.ENTITY_BLAZE_SHOOT, 0.6f, 0.5f);
                }
                t += 2;
            }
        }.runTaskTimer(plugin, 0, 2);

        // Phase 2: 8 fire pillars erupt around victim (50-160)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 110 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }
                // Fire pillars at 8 fixed points
                for (int p = 0; p < 8; p++) {
                    double a = (Math.PI * 2 / 8) * p + t * 0.02;
                    double pr = 2.5;
                    Location pil = loc.clone().add(Math.cos(a) * pr, 0, Math.sin(a) * pr);
                    double h = 1.5 + Math.sin(t * 0.12 + p * 0.8) * 2.0;
                    for (double y = 0; y < h; y += 0.35)
                        w.spawnParticle(Particle.FLAME, pil.clone().add(0, y, 0), 3, 0.06, 0.05, 0.06, 0.01);
                }
                w.spawnParticle(Particle.LAVA, loc, 3, 1.5, 0.3, 1.5, 0);
                w.spawnParticle(Particle.DUST, loc.clone().add(0, 0.5, 0), 8, 0.8, 0.5, 0.8, 0, new Particle.DustOptions(Color.fromRGB(255, 80, 0), 1.5f));
                if (t % 6 == 0) w.playSound(loc, Sound.BLOCK_FIRE_AMBIENT, 0.8f, 0.5f);
                if (t % 20 == 0) w.playSound(loc, Sound.ENTITY_BLAZE_AMBIENT, 0.6f, 0.4f);
                if (t % 12 == 0) {
                    Material[] m = { Material.MAGMA_BLOCK, Material.ORANGE_CONCRETE, Material.RED_CONCRETE };
                    double a = rng().nextDouble(Math.PI * 2);
                    double br = 1.5 + rng().nextDouble(1.5);
                    spawnGhost(w, loc.clone().add(Math.cos(a) * br, -0.5, Math.sin(a) * br), m[rng().nextInt(m.length)],
                            0, rng().nextDouble(0.35, 0.85), 0, 50);
                }
                t += 2;
            }
        }.runTaskTimer(plugin, 50, 2);

        // Phase 3: VOLCANIC ERUPTION on ground (tick 170)
        new BukkitRunnable() {
            @Override public void run() {
                Location loc = vLoc(victim);
                if (loc == null) return;
                w.spawnParticle(Particle.FLAME, loc, 400, 5, 2, 5, 0.15);
                w.spawnParticle(Particle.LAVA, loc, 100, 4, 1, 4, 0);
                w.spawnParticle(Particle.DUST, loc, 200, 4, 2, 4, 0, new Particle.DustOptions(Color.fromRGB(255, 60, 0), 3.0f));
                w.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 6, 1.5, 0.5, 1.5, 0);
                w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);
                w.playSound(loc, Sound.ENTITY_BLAZE_DEATH, 1.5f, 0.3f);
                Material[] deb = { Material.MAGMA_BLOCK, Material.NETHERRACK, Material.NETHER_BRICKS, Material.ORANGE_CONCRETE, Material.RED_CONCRETE };
                for (int b = 0; b < 40; b++) {
                    double a = (Math.PI * 2 / 40) * b;
                    double sp = 0.6 + rng().nextDouble(0.9);
                    spawnGhostBoom(w, loc.clone().add(0, 0.5, 0), deb[rng().nextInt(deb.length)],
                            Math.cos(a) * sp, rng().nextDouble(0.2, 0.8), Math.sin(a) * sp, 50 + rng().nextInt(25));
                }
            }
        }.runTaskLater(plugin, 170);

        return DUR;
    }

    // ========================================================================
    // 7. TORMENTA DE HIELO — GROUND ICE SPIKES (10s = 200t)
    // Ice spikes radiate outward from victim like the anime image.
    // ========================================================================
    private int playIce(Player victim) {
        final int DUR = 200;
        Location origin = victim.getLocation().clone();
        World w = origin.getWorld();
        if (w == null) return 20;

        w.playSound(origin, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.8f);
        w.playSound(origin, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0f, 0.5f);

        // Phase 1: Frost circle on ground (0-40)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 40 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }
                double r = 0.5 + t * 0.12;
                for (int i = 0; i < 24; i++) {
                    double a = (Math.PI * 2 / 24) * i;
                    w.spawnParticle(Particle.DUST, loc.clone().add(Math.cos(a) * r, 0.08, Math.sin(a) * r), 2,
                            0.06, 0.01, 0.06, 0, new Particle.DustOptions(Color.fromRGB(150, 220, 255), 2.0f));
                    w.spawnParticle(Particle.SNOWFLAKE, loc.clone().add(Math.cos(a) * r * 0.5, 0.3, Math.sin(a) * r * 0.5), 1, 0.08, 0.1, 0.08, 0.01);
                }
                if (t % 8 == 0) w.playSound(loc, Sound.BLOCK_GLASS_BREAK, 0.5f, 2.0f);
                t += 2;
            }
        }.runTaskTimer(plugin, 0, 2);

        // Phase 2: ICE SPIKES grow outward in 12 directions (30-120)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 90 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }
                // 12 spike lines growing outward
                double spikeLen = 0.5 + t * 0.08;
                for (int spike = 0; spike < 12; spike++) {
                    double a = (Math.PI * 2 / 12) * spike;
                    // Place ghost ice blocks along the spike line
                    if (t % 6 == 0) {
                        double dist = spikeLen - rng().nextDouble(0.5);
                        if (dist < 0.5) dist = 0.5;
                        Material[] m = { Material.BLUE_ICE, Material.PACKED_ICE, Material.ICE };
                        double spikeHeight = 0.5 + (spikeLen - dist) * 0.6 + rng().nextDouble(0.5);
                        spawnGhostNG(w, loc.clone().add(Math.cos(a) * dist, spikeHeight, Math.sin(a) * dist),
                                m[rng().nextInt(m.length)], 0, 0, 0, 100 + rng().nextInt(30));
                    }
                    // Particle trail along spikes
                    for (double d = 0.3; d < spikeLen; d += 0.5) {
                        double h = 0.3 + (spikeLen - d) * 0.5;
                        w.spawnParticle(Particle.DUST, loc.clone().add(Math.cos(a) * d, h, Math.sin(a) * d), 2,
                                0.04, 0.06, 0.04, 0, new Particle.DustOptions(Color.fromRGB(180, 230, 255), 1.6f));
                    }
                }
                // Central frost column
                w.spawnParticle(Particle.SNOWFLAKE, loc.clone().add(0, 1, 0), 15, 0.3, 1.5, 0.3, 0.02);
                w.spawnParticle(Particle.DUST, loc.clone().add(0, 0.5, 0), 10, 0.2, 0.8, 0.2, 0, new Particle.DustOptions(Color.fromRGB(200, 240, 255), 1.8f));
                if (t % 8 == 0) w.playSound(loc, Sound.ENTITY_PLAYER_HURT_FREEZE, 0.5f, 1.0f + t * 0.005f);
                t += 2;
            }
        }.runTaskTimer(plugin, 30, 2);

        // Phase 3: Blizzard swirl around victim (50-160)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 110 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }
                for (int arm = 0; arm < 5; arm++) {
                    double a = t * 0.35 + arm * Math.PI * 2 / 5;
                    double r = 1.2;
                    for (double y = 0; y < 2.5; y += 0.5)
                        w.spawnParticle(Particle.SNOWFLAKE, loc.clone().add(Math.cos(a + y * 0.4) * r, y, Math.sin(a + y * 0.4) * r), 2, 0.05, 0.06, 0.05, 0.008);
                }
                t += 2;
            }
        }.runTaskTimer(plugin, 50, 2);

        // Phase 4: SPIKE EXPLOSION — all spikes shatter outward (tick 165)
        new BukkitRunnable() {
            @Override public void run() {
                Location loc = vLoc(victim);
                if (loc == null) return;
                w.spawnParticle(Particle.SNOWFLAKE, loc, 300, 6, 2, 6, 0.15);
                w.spawnParticle(Particle.DUST, loc, 300, 6, 2, 6, 0, new Particle.DustOptions(Color.fromRGB(150, 220, 255), 3.0f));
                w.spawnParticle(Particle.DUST, loc, 200, 5, 1.5, 5, 0, new Particle.DustOptions(Color.fromRGB(80, 180, 255), 2.5f));
                w.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 4, 1.5, 0.5, 1.5, 0);
                w.spawnParticle(Particle.END_ROD, loc, 60, 4, 1, 4, 0.1);
                w.playSound(loc, Sound.BLOCK_GLASS_BREAK, 2.0f, 0.5f);
                w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.5f);
                w.playSound(loc, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.5f, 0.3f);
                Material[] deb = { Material.BLUE_ICE, Material.PACKED_ICE, Material.ICE, Material.LIGHT_BLUE_CONCRETE, Material.WHITE_CONCRETE };
                // Spikes shoot outward in 12 directions
                for (int spike = 0; spike < 12; spike++) {
                    double a = (Math.PI * 2 / 12) * spike;
                    for (int b = 0; b < 3; b++) {
                        double sp = 0.8 + rng().nextDouble(1.0);
                        spawnGhostBoom(w, loc.clone().add(Math.cos(a) * 0.5, 0.3 + b * 0.4, Math.sin(a) * 0.5), deb[rng().nextInt(deb.length)],
                                Math.cos(a) * sp, rng().nextDouble(0.05, 0.3), Math.sin(a) * sp, 45 + rng().nextInt(20));
                    }
                }
            }
        }.runTaskLater(plugin, 165);

        return DUR;
    }

    // ========================================================================
    // 8. IRA DEL DRAGÓN — SKY SPIRAL (12s = 240t)
    // ========================================================================
    private int playDragon(Player victim) {
        final int DUR = 240;
        Location origin = victim.getLocation().clone();
        World w = origin.getWorld();
        if (w == null) return 20;

        victim.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 200, 2, false, false, false));
        w.playSound(origin, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 0.5f);

        // Phase 1: Purple flames + end stone erupting (0-70)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 70 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }
                double r = 3.5 - t * 0.03;
                for (int i = 0; i < 16; i++) {
                    double a = (Math.PI * 2 / 16) * i + t * 0.08;
                    w.spawnParticle(Particle.DRAGON_BREATH, loc.clone().add(Math.cos(a) * r, 0.2, Math.sin(a) * r), 4, 0.06, 0.08, 0.06, 0.004);
                    w.spawnParticle(Particle.DUST, loc.clone().add(Math.cos(a) * r * 0.6, 0.3, Math.sin(a) * r * 0.6), 3, 0.08, 0.04, 0.08, 0, new Particle.DustOptions(Color.fromRGB(120, 0, 180), 2.0f));
                }
                if (t % 10 == 0) {
                    Material[] m = { Material.END_STONE, Material.PURPUR_BLOCK, Material.OBSIDIAN };
                    for (int p = 0; p < 3; p++) {
                        double a2 = rng().nextDouble(Math.PI * 2);
                        double r2 = 1.5 + rng().nextDouble(2);
                        spawnGhost(w, loc.clone().add(Math.cos(a2) * r2, -0.5, Math.sin(a2) * r2), m[rng().nextInt(m.length)], 0, rng().nextDouble(0.3, 0.6), 0, 70);
                    }
                    w.playSound(loc, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.6f, 0.5f);
                }
                t += 2;
            }
        }.runTaskTimer(plugin, 0, 2);

        // Phase 2: Dragon breath spirals (50-180)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 130 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }
                for (int arm = 0; arm < 4; arm++) {
                    double a = t * 0.35 + arm * Math.PI / 2;
                    double r = 2.0 - t * 0.008; if (r < 0.5) r = 0.5;
                    for (double y = 0; y < 3.5; y += 0.4)
                        w.spawnParticle(Particle.DRAGON_BREATH, loc.clone().add(Math.cos(a + y * 0.6) * r, y - 0.5, Math.sin(a + y * 0.6) * r), 4, 0.05, 0.06, 0.05, 0.003);
                }
                w.spawnParticle(Particle.DUST, loc, 12, 0.4, 1.5, 0.4, 0, new Particle.DustOptions(Color.fromRGB(150, 0, 220), 2.0f));
                w.spawnParticle(Particle.END_ROD, loc.clone().add(0, 1, 0), 4, 0.3, 0.6, 0.3, 0.02);
                if (t % 14 == 0) { w.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.4f, 0.8f + t * 0.005f); w.spawnParticle(Particle.SONIC_BOOM, loc, 1, 0, 0, 0, 0); }
                if (t % 12 == 0) {
                    Material[] m = { Material.END_STONE, Material.PURPUR_BLOCK, Material.PURPLE_CONCRETE };
                    for (int b = 0; b < 3; b++)
                        spawnGhost(w, loc.clone().add(rng().nextDouble(-2, 2), -2, rng().nextDouble(-2, 2)), m[rng().nextInt(m.length)],
                                rng().nextDouble(-0.1, 0.1), rng().nextDouble(0.3, 0.7), rng().nextDouble(-0.1, 0.1), 45);
                }
                t += 2;
            }
        }.runTaskTimer(plugin, 50, 2);

        // Phase 3: Stronger lev
        new BukkitRunnable() {
            @Override public void run() {
                if (!victim.isOnline()) return;
                victim.removePotionEffect(PotionEffectType.LEVITATION);
                victim.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 100, 4, false, false, false));
            }
        }.runTaskLater(plugin, 100);

        // Phase 4: EXPLOSION (tick 200)
        new BukkitRunnable() {
            @Override public void run() {
                Location loc = vLoc(victim);
                if (loc == null) return;
                w.spawnParticle(Particle.DRAGON_BREATH, loc, 400, 6, 6, 6, 0.12);
                w.spawnParticle(Particle.DUST, loc, 300, 5, 5, 5, 0, new Particle.DustOptions(Color.fromRGB(150, 0, 220), 3.0f));
                w.spawnParticle(Particle.DUST, loc, 200, 4, 4, 4, 0, new Particle.DustOptions(Color.fromRGB(200, 100, 255), 2.5f));
                w.spawnParticle(Particle.END_ROD, loc, 120, 4, 4, 4, 0.2);
                w.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 6, 2, 2, 2, 0);
                w.spawnParticle(Particle.SONIC_BOOM, loc, 3, 1, 1, 1, 0);
                w.playSound(loc, Sound.ENTITY_ENDER_DRAGON_DEATH, 1.5f, 0.8f);
                w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);
                Material[] deb = { Material.END_STONE, Material.PURPUR_BLOCK, Material.OBSIDIAN, Material.PURPLE_CONCRETE, Material.CRYING_OBSIDIAN };
                for (int b = 0; b < 40; b++)
                    spawnGhostBoom(w, loc.clone().add(0, 1, 0), deb[rng().nextInt(deb.length)],
                            rng().nextDouble(-2, 2), rng().nextDouble(0.5, 2.5), rng().nextDouble(-2, 2), 55 + rng().nextInt(25));
            }
        }.runTaskLater(plugin, 200);

        // Phase 5: Lingering dragon breath
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 35) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) loc = origin.clone().add(0, 5, 0);
                w.spawnParticle(Particle.DRAGON_BREATH, loc, 20 - t / 2, 3, 3, 3, 0.03);
                w.spawnParticle(Particle.END_ROD, loc, 5, 2, 2, 2, 0.02);
                if (t % 8 == 0) w.playSound(loc, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.4f, 1.2f);
                t += 3;
            }
        }.runTaskTimer(plugin, 205, 3);

        return DUR;
    }

    // ========================================================================
    // 9. SOUL VORTEX — GROUND SPIN (10s = 200t) [NEW]
    // Victim spins wildly many times. Soul particles drain upward.
    // ========================================================================
    private int playSoulVortex(Player victim) {
        final int DUR = 200;
        Location origin = victim.getLocation().clone();
        World w = origin.getWorld();
        if (w == null) return 20;

        w.playSound(origin, Sound.ENTITY_WARDEN_EMERGE, 1.0f, 0.5f);
        w.playSound(origin, Sound.BLOCK_SCULK_CATALYST_BLOOM, 1.0f, 0.6f);

        // Phase 1: Soul circle on ground (0-40)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 40 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }
                double r = 0.5 + t * 0.1;
                for (int i = 0; i < 20; i++) {
                    double a = (Math.PI * 2 / 20) * i + t * 0.12;
                    w.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(Math.cos(a) * r, 0.1, Math.sin(a) * r), 2, 0.05, 0.02, 0.05, 0.005);
                }
                w.spawnParticle(Particle.SOUL, loc.clone().add(0, 0.5, 0), 3, 0.3, 0.2, 0.3, 0.01);
                if (t % 10 == 0) w.playSound(loc, Sound.BLOCK_SOUL_SAND_STEP, 0.8f, 0.5f);
                t += 2;
            }
        }.runTaskTimer(plugin, 0, 2);

        // Phase 2: Victim spins WILDLY (many rotations)
        new BukkitRunnable() {
            int t = 0; float spin = 10f;
            @Override public void run() {
                if (t >= 160 || !victim.isOnline()) { cancel(); return; }
                Location loc = victim.getLocation();
                spin = Math.min(spin + 1.2f, 120f);
                loc.setYaw(loc.getYaw() + spin);
                loc.setPitch(Math.min(loc.getPitch() + 0.3f, 60f));
                victim.teleport(loc);
                if (t % 3 == 0) {
                    w.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, 1, 0), 5, 0.4, 0.5, 0.4, 0.02);
                    w.spawnParticle(Particle.SOUL, loc.clone().add(0, 1.5, 0), 2, 0.2, 0.3, 0.2, 0.05);
                }
                t++;
            }
        }.runTaskTimer(plugin, 25, 1);

        // Phase 3: Soul vortex arms + blocks orbiting (30-170)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 140 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }
                for (int arm = 0; arm < 4; arm++) {
                    double a = t * 0.6 + arm * Math.PI / 2;
                    double r = 2.0 - t * 0.008; if (r < 0.4) r = 0.4;
                    for (double d = 0.3; d < r; d += 0.4) {
                        w.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(Math.cos(a + d * 0.5) * d, 0.1 + d * 0.15, Math.sin(a + d * 0.5) * d), 2, 0.04, 0.03, 0.04, 0.003);
                    }
                }
                // Rising soul orbs
                if (t % 4 == 0) w.spawnParticle(Particle.SOUL, loc.clone().add(0, 2, 0), 4, 0.5, 0.5, 0.5, 0.08);
                if (t % 10 == 0) {
                    Material[] m = { Material.SOUL_SAND, Material.SOUL_SOIL, Material.SOUL_LANTERN };
                    for (int b = 0; b < 3; b++) {
                        double a2 = rng().nextDouble(Math.PI * 2);
                        double dist = 2 + rng().nextDouble(2);
                        spawnGhost(w, loc.clone().add(Math.cos(a2) * dist, 0.3, Math.sin(a2) * dist), m[rng().nextInt(m.length)],
                                -Math.cos(a2) * 0.18, rng().nextDouble(0.05, 0.2), -Math.sin(a2) * 0.18, 40 + rng().nextInt(15));
                    }
                }
                if (t % 16 == 0) { w.playSound(loc, Sound.ENTITY_WARDEN_HEARTBEAT, 0.8f, 0.6f); w.playSound(loc, Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.5f, 0.8f); }
                t += 2;
            }
        }.runTaskTimer(plugin, 30, 2);

        // Phase 4: SOUL EXPLOSION (tick 175)
        new BukkitRunnable() {
            @Override public void run() {
                Location loc = vLoc(victim);
                if (loc == null) return;
                w.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 400, 5, 2, 5, 0.2);
                w.spawnParticle(Particle.SOUL, loc, 200, 4, 3, 4, 0.15);
                w.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 5, 1.5, 0.5, 1.5, 0);
                w.spawnParticle(Particle.SONIC_BOOM, loc, 2, 0.5, 0.5, 0.5, 0);
                w.playSound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 0.5f);
                w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.6f);
                Material[] deb = { Material.SOUL_SAND, Material.SOUL_SOIL, Material.SOUL_LANTERN, Material.CYAN_CONCRETE, Material.LIGHT_BLUE_CONCRETE };
                for (int b = 0; b < 35; b++) {
                    double a = (Math.PI * 2 / 35) * b;
                    double sp = 0.6 + rng().nextDouble(0.8);
                    spawnGhostBoom(w, loc.clone().add(0, 0.5, 0), deb[rng().nextInt(deb.length)],
                            Math.cos(a) * sp, rng().nextDouble(0.1, 0.5), Math.sin(a) * sp, 50 + rng().nextInt(20));
                }
            }
        }.runTaskLater(plugin, 175);

        return DUR;
    }

    // ========================================================================
    // 10. WITHER STORM — GROUND DARK EXPLOSION (10s = 200t) [NEW]
    // Dark ground effect. Wither skulls orbit. Black blocks erupt.
    // ========================================================================
    private int playWitherStorm(Player victim) {
        final int DUR = 200;
        Location origin = victim.getLocation().clone();
        World w = origin.getWorld();
        if (w == null) return 20;

        w.playSound(origin, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f);

        // Phase 1: Dark expanding circle (0-60)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 60 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }
                double r = 0.5 + t * 0.08;
                for (int i = 0; i < 20; i++) {
                    double a = (Math.PI * 2 / 20) * i + t * 0.1;
                    w.spawnParticle(Particle.DUST, loc.clone().add(Math.cos(a) * r, 0.08, Math.sin(a) * r), 3,
                            0.06, 0.01, 0.06, 0, new Particle.DustOptions(Color.fromRGB(20, 20, 20), 2.5f));
                    w.spawnParticle(Particle.SQUID_INK, loc.clone().add(Math.cos(a) * r * 0.5, 0.15, Math.sin(a) * r * 0.5), 1, 0.04, 0.02, 0.04, 0.004);
                }
                if (t % 8 == 0) {
                    Material[] m = { Material.COAL_BLOCK, Material.BLACK_CONCRETE, Material.BLACKSTONE };
                    for (int b = 0; b < 3; b++) {
                        double a = rng().nextDouble(Math.PI * 2);
                        spawnGhost(w, loc.clone().add(Math.cos(a) * r, -0.5, Math.sin(a) * r), m[rng().nextInt(m.length)],
                                0, rng().nextDouble(0.2, 0.5), 0, 55);
                    }
                }
                if (t % 12 == 0) w.playSound(loc, Sound.ENTITY_WITHER_AMBIENT, 0.6f, 0.5f);
                t += 2;
            }
        }.runTaskTimer(plugin, 0, 2);

        // Phase 2: Wither skulls orbiting on ground (40-165)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 125 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }
                // 3 orbiting skull-like particle clusters
                for (int s = 0; s < 3; s++) {
                    double a = t * 0.4 + s * Math.PI * 2 / 3;
                    double r = 2.5 - t * 0.01; if (r < 1) r = 1;
                    Location skull = loc.clone().add(Math.cos(a) * r, 1.0, Math.sin(a) * r);
                    w.spawnParticle(Particle.DUST, skull, 8, 0.15, 0.15, 0.15, 0, new Particle.DustOptions(Color.BLACK, 2.0f));
                    w.spawnParticle(Particle.SQUID_INK, skull, 3, 0.1, 0.1, 0.1, 0.01);
                }
                // Dark pillar at center
                w.spawnParticle(Particle.SQUID_INK, loc.clone().add(0, 0.5, 0), 8, 0.15, 0.5, 0.15, 0.01);
                w.spawnParticle(Particle.DUST, loc.clone().add(0, 0.3, 0), 10, 0.3, 0.8, 0.3, 0, new Particle.DustOptions(Color.fromRGB(40, 0, 40), 2.0f));
                if (t % 12 == 0) {
                    Material[] m = { Material.COAL_BLOCK, Material.BLACK_CONCRETE, Material.BLACKSTONE, Material.WITHER_SKELETON_SKULL };
                    for (int b = 0; b < 2; b++) {
                        double a2 = rng().nextDouble(Math.PI * 2);
                        double dist = 1 + rng().nextDouble(2.5);
                        spawnGhost(w, loc.clone().add(Math.cos(a2) * dist, -0.3, Math.sin(a2) * dist), m[rng().nextInt(m.length)],
                                rng().nextDouble(-0.1, 0.1), rng().nextDouble(0.25, 0.65), rng().nextDouble(-0.1, 0.1), 50);
                    }
                }
                if (t % 16 == 0) w.playSound(loc, Sound.ENTITY_WITHER_SHOOT, 0.5f, 0.5f);
                t += 2;
            }
        }.runTaskTimer(plugin, 40, 2);

        // Phase 3: WITHER EXPLOSION (tick 170)
        new BukkitRunnable() {
            @Override public void run() {
                Location loc = vLoc(victim);
                if (loc == null) return;
                w.spawnParticle(Particle.SQUID_INK, loc, 400, 5, 2, 5, 0.2);
                w.spawnParticle(Particle.DUST, loc, 300, 5, 2, 5, 0, new Particle.DustOptions(Color.BLACK, 3.5f));
                w.spawnParticle(Particle.DUST, loc, 200, 4, 1.5, 4, 0, new Particle.DustOptions(Color.fromRGB(50, 0, 50), 2.5f));
                w.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 6, 1.5, 0.5, 1.5, 0);
                w.spawnParticle(Particle.SONIC_BOOM, loc, 2, 1, 0.5, 1, 0);
                w.playSound(loc, Sound.ENTITY_WITHER_DEATH, 1.5f, 0.6f);
                w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.4f);
                Material[] deb = { Material.COAL_BLOCK, Material.BLACK_CONCRETE, Material.BLACKSTONE, Material.WITHER_SKELETON_SKULL, Material.OBSIDIAN };
                for (int b = 0; b < 40; b++) {
                    double a = (Math.PI * 2 / 40) * b;
                    double sp = 0.6 + rng().nextDouble(0.9);
                    spawnGhostBoom(w, loc.clone().add(0, 0.5, 0), deb[rng().nextInt(deb.length)],
                            Math.cos(a) * sp, rng().nextDouble(0.15, 0.6), Math.sin(a) * sp, 50 + rng().nextInt(25));
                }
            }
        }.runTaskLater(plugin, 170);

        // Phase 4: Dark lingering (175-195)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 25) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) loc = origin;
                w.spawnParticle(Particle.SQUID_INK, loc, 20 - t, 3, 1, 3, 0.03);
                if (t % 8 == 0) w.playSound(loc, Sound.ENTITY_WITHER_AMBIENT, 0.3f, 0.8f);
                t += 3;
            }
        }.runTaskTimer(plugin, 175, 3);

        return DUR;
    }

    // ========================================================================
    // 11. SCULK RESONANCE — GROUND SONIC SHOCKWAVES (10s = 200t) [NEW]
    // Sculk tendrils spread. Sonic booms pulse outward. Ground-based.
    // ========================================================================
    private int playSculkResonance(Player victim) {
        final int DUR = 200;
        Location origin = victim.getLocation().clone();
        World w = origin.getWorld();
        if (w == null) return 20;

        w.playSound(origin, Sound.BLOCK_SCULK_CATALYST_BLOOM, 1.2f, 0.3f);
        w.playSound(origin, Sound.ENTITY_WARDEN_EMERGE, 0.8f, 0.8f);

        // Phase 1: Sculk tendrils spread on ground (0-70)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 70 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }
                // 8 tendril lines growing outward
                double len = 0.3 + t * 0.08;
                for (int td = 0; td < 8; td++) {
                    double a = (Math.PI * 2 / 8) * td + Math.sin(t * 0.05) * 0.2;
                    for (double d = 0; d < len; d += 0.4) {
                        w.spawnParticle(Particle.DUST, loc.clone().add(Math.cos(a) * d, 0.06, Math.sin(a) * d), 2,
                                0.05, 0.01, 0.05, 0, new Particle.DustOptions(Color.fromRGB(0, 50, 60), 2.2f));
                    }
                }
                // Sculk blocks emerging
                if (t % 8 == 0) {
                    Material[] m = { Material.SCULK, Material.SCULK_CATALYST, Material.SCULK_VEIN };
                    for (int b = 0; b < 3; b++) {
                        double a = rng().nextDouble(Math.PI * 2);
                        double dist = 0.5 + rng().nextDouble(len);
                        spawnGhostNG(w, loc.clone().add(Math.cos(a) * dist, rng().nextDouble(0, 0.5), Math.sin(a) * dist),
                                m[rng().nextInt(m.length)], 0, 0, 0, 80 + rng().nextInt(20));
                    }
                }
                if (t % 12 == 0) w.playSound(loc, Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.8f, 0.5f + t * 0.01f);
                t += 2;
            }
        }.runTaskTimer(plugin, 0, 2);

        // Phase 2: Sonic boom pulses (every ~20 ticks, expanding rings)
        int[] pulses = { 40, 60, 80, 100, 120, 140 };
        for (int i = 0; i < pulses.length; i++) {
            final int idx = i;
            new BukkitRunnable() {
                @Override public void run() {
                    Location loc = vLoc(victim);
                    if (loc == null) return;
                    w.spawnParticle(Particle.SONIC_BOOM, loc.clone().add(0, 0.5, 0), 1, 0, 0, 0, 0);
                    w.playSound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6f + idx * 0.1f, 0.5f + idx * 0.1f);
                    // Expanding ring
                    double ringR = 1.5 + idx * 0.8;
                    for (int p = 0; p < 24; p++) {
                        double a = (Math.PI * 2 / 24) * p;
                        w.spawnParticle(Particle.DUST, loc.clone().add(Math.cos(a) * ringR, 0.3, Math.sin(a) * ringR), 3,
                                0.08, 0.04, 0.08, 0, new Particle.DustOptions(Color.fromRGB(0, 80, 100), 2.0f));
                    }
                    // Block debris from shockwave
                    Material[] m = { Material.SCULK, Material.SCULK_CATALYST, Material.CYAN_CONCRETE };
                    for (int b = 0; b < 4 + idx; b++) {
                        double a = rng().nextDouble(Math.PI * 2);
                        spawnGhost(w, loc.clone().add(Math.cos(a) * ringR * 0.5, 0.3, Math.sin(a) * ringR * 0.5), m[rng().nextInt(m.length)],
                                Math.cos(a) * 0.3, rng().nextDouble(0.2, 0.6), Math.sin(a) * 0.3, 45);
                    }
                }
            }.runTaskLater(plugin, pulses[idx]);
        }

        // Phase 3: Dark sculk aura (50-165)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 115 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }
                w.spawnParticle(Particle.DUST, loc.clone().add(0, 0.5, 0), 10, 0.5, 0.5, 0.5, 0, new Particle.DustOptions(Color.fromRGB(0, 60, 70), 1.8f));
                w.spawnParticle(Particle.SCULK_CHARGE_POP, loc.clone().add(0, 0.3, 0), 5, 0.8, 0.3, 0.8, 0.01);
                if (t % 6 == 0) w.playSound(loc, Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.4f, 1.0f);
                t += 2;
            }
        }.runTaskTimer(plugin, 50, 2);

        // Phase 4: MEGA SONIC SHOCKWAVE (tick 170)
        new BukkitRunnable() {
            @Override public void run() {
                Location loc = vLoc(victim);
                if (loc == null) return;
                w.spawnParticle(Particle.SONIC_BOOM, loc.clone().add(0, 0.5, 0), 5, 1, 0.5, 1, 0);
                w.spawnParticle(Particle.SCULK_CHARGE_POP, loc, 300, 5, 1, 5, 0.1);
                w.spawnParticle(Particle.DUST, loc, 400, 5, 1.5, 5, 0, new Particle.DustOptions(Color.fromRGB(0, 80, 100), 3.0f));
                w.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 4, 1, 0.3, 1, 0);
                w.playSound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 0.3f);
                w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.5f);
                w.playSound(loc, Sound.BLOCK_SCULK_CATALYST_BLOOM, 1.5f, 0.4f);
                Material[] deb = { Material.SCULK, Material.SCULK_CATALYST, Material.CYAN_CONCRETE, Material.DARK_PRISMARINE, Material.PRISMARINE_BRICKS };
                for (int b = 0; b < 40; b++) {
                    double a = (Math.PI * 2 / 40) * b;
                    double sp = 0.7 + rng().nextDouble(1.0);
                    spawnGhostBoom(w, loc.clone().add(0, 0.3, 0), deb[rng().nextInt(deb.length)],
                            Math.cos(a) * sp, rng().nextDouble(0.1, 0.4), Math.sin(a) * sp, 50 + rng().nextInt(20));
                }
            }
        }.runTaskLater(plugin, 170);

        // Phase 5: Fading resonance (175-195)
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 25) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) loc = origin;
                w.spawnParticle(Particle.SCULK_CHARGE_POP, loc, 15 - t, 3, 0.5, 3, 0.02);
                if (t % 8 == 0) w.playSound(loc, Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.3f, 1.5f);
                t += 3;
            }
        }.runTaskTimer(plugin, 175, 3);

        return DUR;
    }

    // ========================================================================
    // TOTEM EXPLOSION (Immediate Feedback)
    // ========================================================================
    public void playTotemExplosion(Location loc) {
        World w = loc.getWorld();
        if (w == null) return;
        w.spawnParticle(Particle.EXPLOSION_EMITTER, loc.clone().add(0, 1, 0), 1, 0, 0, 0, 0);
        w.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1, 0), 100, 1.0, 1.0, 1.0, 0.5);
        w.spawnParticle(Particle.FLASH, loc.clone().add(0, 1, 0), 3, 0, 0, 0, 0);
        w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.5f);
        w.playSound(loc, Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
    }

    // ========================================================================
    // TOTEM COUNTER – ANIME BATTLE (5s = 100t)
    // ========================================================================
    public int playTotemCounter(Player victim, Player killer) {
        final int DUR = 100;
        Location origin = victim.getLocation().clone();
        World w = origin.getWorld();
        if (w == null) return 20;

        w.spawnParticle(Particle.TOTEM_OF_UNDYING, origin.clone().add(0, 1, 0), 150, 1.0, 1.5, 1.0, 0.5);
        w.spawnParticle(Particle.FLASH, origin.clone().add(0, 1, 0), 3, 0, 0, 0, 0);
        w.playSound(origin, Sound.ITEM_TOTEM_USE, 1.2f, 1.0f);

        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 20 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }
                double r = 1.2 + t * 0.05;
                for (int i = 0; i < 16; i++) {
                    double a = (Math.PI * 2 / 16) * i + t * 0.2;
                    w.spawnParticle(Particle.DUST, loc.clone().add(Math.cos(a) * r, 0.5 + t * 0.1, Math.sin(a) * r), 1,
                            0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(255, 223, 50), 1.5f));
                }
                if (t % 5 == 0) w.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.5f);
                t += 2;
            }
        }.runTaskTimer(plugin, 15, 2);

        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 30 || !victim.isOnline() || !killer.isOnline()) { cancel(); return; }
                Location vl = victim.getLocation();
                Location kl = killer.getLocation();
                Vector dir = vl.toVector().subtract(kl.toVector()).normalize();
                for (double d = 0; d < vl.distance(kl) && d < 6; d += 1.0) {
                    Location pt = kl.clone().add(dir.clone().multiply(d)).add(0, 1, 0);
                    w.spawnParticle(Particle.DUST, pt, 2, 0.1, 0.1, 0.1, 0, new Particle.DustOptions(Color.RED, 1.5f));
                }
                Vector rev = kl.toVector().subtract(vl.toVector()).normalize();
                for (double d = 0; d < 3; d += 0.8) {
                    Location pt = vl.clone().add(rev.clone().multiply(d)).add(0, 1, 0);
                    w.spawnParticle(Particle.DUST, pt, 2, 0.1, 0.1, 0.1, 0, new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.5f));
                }
                Location mid = vl.clone().add(kl).multiply(0.5).add(0, 1, 0);
                w.spawnParticle(Particle.ELECTRIC_SPARK, mid, 10, 0.3, 0.3, 0.3, 0.1);
                if (t % 6 == 0) w.playSound(mid, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.5f, 1.5f);
                t += 3;
            }
        }.runTaskTimer(plugin, 35, 3);

        new BukkitRunnable() {
            @Override public void run() {
                Location loc = vLoc(victim);
                if (loc == null) return;
                w.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1, 0), 200, 3.0, 2.0, 3.0, 0.3);
                w.spawnParticle(Particle.FLASH, loc.clone().add(0, 1, 0), 2, 0, 0, 0, 0);
                w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.2f);
                for (int i = 0; i < 32; i++) {
                    double a = (Math.PI * 2 / 32) * i;
                    w.spawnParticle(Particle.END_ROD, loc.clone().add(Math.cos(a) * 2, 0.5, Math.sin(a) * 2), 2, 0, 0, 0, 0.1);
                }
            }
        }.runTaskLater(plugin, 65);

        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 15 || !victim.isOnline()) { cancel(); return; }
                Location loc = vLoc(victim);
                if (loc == null) { cancel(); return; }
                w.spawnParticle(Particle.HEART, loc.clone().add(0, 2, 0), 2, 0.3, 0.2, 0.3, 0);
                w.spawnParticle(Particle.END_ROD, loc.clone().add(0, 1, 0), 3, 0.5, 1, 0.5, 0.05);
                t += 3;
            }
        }.runTaskTimer(plugin, 85, 3);

        return DUR;
    }
}
