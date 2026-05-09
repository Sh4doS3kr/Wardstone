package com.moonlight.coreprotect.essence;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Listener para "Colmillo Sísmico".
 *
 * Mecánica:
 * 1. Click Derecho → activa "carga sísmica" instantáneamente. Breve flash + partículas.
 * 2. Una tarea monitoriza al jugador: trackea la Y máxima y detecta cuándo aterriza.
 *    Al aterrizar, se genera el terremoto proporcional a la altura de caída.
 *    La caída propia no hace daño al usuario (se cancela el fall damage).
 * 3. Cooldown: 18 segundos.
 * 4. La carga dura máximo 6 segundos, luego se desactiva.
 * 5. Quita durabilidad por uso.
 */
public class EarthquakeSwordListener implements Listener {

    private final CoreProtectPlugin plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Set<UUID> chargedPlayers = new HashSet<>();
    // Players who are charged and need fall damage cancelled
    private final Set<UUID> cancelFallDamage = new HashSet<>();

    private static final long COOLDOWN_MS = 18_000L;
    private static final double MIN_FALL_HEIGHT = 3.0;
    private static final int DURABILITY_COST = 5;
    private static final int MAX_CHARGE_TICKS = 120; // 6 seconds

    public EarthquakeSwordListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    // ==================== ACTIVATION: Right Click ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!EarthquakeSword.isEarthquakeSword(held, plugin)) return;

        if (isRestricted(player)) return;

        UUID uid = player.getUniqueId();
        long now = System.currentTimeMillis();

        // Already charged — ignore (don't spam messages)
        if (chargedPlayers.contains(uid)) {
            event.setCancelled(true);
            return;
        }

        // Cooldown check
        if (cooldowns.containsKey(uid) && now < cooldowns.get(uid)) {
            long left = (cooldowns.get(uid) - now) / 1000;
            com.moonlight.coreprotect.util.ActionBarUtil.send(player,
                    "§c§l✖ §cEnfriamiento: §e" + left + "s");
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        // === ACTIVATE CHARGE ===
        chargedPlayers.add(uid);

        // Instant activation feedback
        World world = player.getWorld();
        Location pLoc = player.getLocation();
        player.sendTitle("", SmallCaps.convert("§6§l⚡ §eCarga Sísmica §7— ¡Salta y cae!"), 0, 30, 10);
        world.playSound(pLoc, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8f, 1.5f);
        world.playSound(pLoc, Sound.BLOCK_ANVIL_LAND, 0.3f, 1.5f);
        world.spawnParticle(Particle.DUST, pLoc.clone().add(0, 1, 0), 15, 0.4, 0.5, 0.4, 0,
                new Particle.DustOptions(Color.fromRGB(255, 165, 0), 1.2f));

        // === MONITORING TASK: tracks Y, detects landing, shows particles ===
        final double activationY = pLoc.getY();

        new BukkitRunnable() {
            int ticks = 0;
            double highestY = activationY;
            boolean wasFalling = false; // was the player falling last tick?
            boolean wasOnGround = player.isOnGround();

            @Override
            public void run() {
                // Player left or died
                if (!player.isOnline() || player.isDead()) {
                    cleanup();
                    cancel();
                    return;
                }

                // Charge expired
                if (ticks >= MAX_CHARGE_TICKS) {
                    cleanup();
                    com.moonlight.coreprotect.util.ActionBarUtil.send(player, "§c§l✖ §cCarga sísmica expirada.");
                    player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.6f, 1.5f);
                    cancel();
                    return;
                }

                // No longer charged (consumed or cancelled)
                if (!chargedPlayers.contains(uid)) {
                    cancel();
                    return;
                }

                // No longer holding the sword
                ItemStack current = player.getInventory().getItemInMainHand();
                if (!EarthquakeSword.isEarthquakeSword(current, plugin)) {
                    cleanup();
                    com.moonlight.coreprotect.util.ActionBarUtil.send(player, "§c§l✖ §cCarga cancelada §7(espada no equipada).");
                    cancel();
                    return;
                }

                Location loc = player.getLocation();
                double currentY = loc.getY();

                // Track highest Y
                if (currentY > highestY) {
                    highestY = currentY;
                }

                boolean onGround = player.isOnGround();
                boolean falling = player.getVelocity().getY() < -0.15;

                // Detect landing: was falling or in air, now on ground, and fell enough
                if (onGround && !wasOnGround && highestY - currentY >= MIN_FALL_HEIGHT) {
                    // LANDED! Trigger earthquake
                    double fallHeight = highestY - currentY;
                    chargedPlayers.remove(uid);
                    cancelFallDamage.add(uid); // flag to cancel upcoming fall damage event

                    // Remove flag after 500ms (fall damage event should fire within this window)
                    Bukkit.getScheduler().runTaskLater(plugin, () -> cancelFallDamage.remove(uid), 10L);

                    // Cooldown
                    cooldowns.put(uid, System.currentTimeMillis() + COOLDOWN_MS);

                    // Durability cost
                    org.bukkit.inventory.meta.Damageable dmg = (org.bukkit.inventory.meta.Damageable) current.getItemMeta();
                    int maxDur = current.getType().getMaxDurability();
                    dmg.setDamage(Math.min(dmg.getDamage() + DURABILITY_COST, maxDur));
                    current.setItemMeta(dmg);
                    if (dmg.getDamage() >= maxDur) {
                        player.getInventory().setItemInMainHand(null);
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                        com.moonlight.coreprotect.util.ActionBarUtil.send(player, "§c§l✖ §cTu Colmillo Sísmico se ha roto.");
                        cancel();
                        return;
                    }

                    performEarthquake(player, fallHeight);
                    cancel();
                    return;
                }

                // Detect landing without enough height (small fall)
                if (onGround && !wasOnGround && highestY - currentY > 0.5 && highestY - currentY < MIN_FALL_HEIGHT) {
                    // Small fall — don't trigger, but keep charge active
                    // Reset highestY to current so they can try again
                    highestY = currentY;
                }

                wasOnGround = onGround;
                wasFalling = falling;

                // === VISUAL: particles while charged ===
                World world = loc.getWorld();

                // Sword glow particles
                Location handLoc = loc.clone().add(
                        loc.getDirection().normalize().multiply(0.5)).add(0, 1.0, 0);
                world.spawnParticle(Particle.DUST, handLoc, 2,
                        0.1, 0.1, 0.1, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 165, 0), 0.7f));

                // Ground cracks every few ticks
                if (ticks % 8 == 0) {
                    for (int i = 0; i < 2; i++) {
                        double angle = Math.random() * Math.PI * 2;
                        double r = 0.3 + Math.random() * 1.0;
                        Location crackLoc = loc.clone().add(Math.cos(angle) * r, 0.1, Math.sin(angle) * r);
                        world.spawnParticle(Particle.BLOCK, crackLoc, 2, 0.1, 0.05, 0.1, 0,
                                Material.MAGMA_BLOCK.createBlockData());
                    }
                }

                // Vibration sound every second
                if (ticks % 20 == 0) {
                    player.playSound(loc, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.2f, 0.8f + ticks * 0.005f);
                }

                // ActionBar reminder
                if (ticks % 20 == 0) {
                    int secsLeft = (MAX_CHARGE_TICKS - ticks) / 20;
                    com.moonlight.coreprotect.util.ActionBarUtil.send(player,
                            "§6§l⚡ §eCarga Sísmica §7[" + secsLeft + "s] §7— ¡Salta y cae!");
                }

                ticks++;
            }

            private void cleanup() {
                chargedPlayers.remove(uid);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ==================== CANCEL FALL DAMAGE when earthquake triggers ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        UUID uid = player.getUniqueId();

        // Cancel fall damage if player just triggered earthquake
        if (cancelFallDamage.contains(uid)) {
            event.setCancelled(true);
            cancelFallDamage.remove(uid);
            return;
        }

        // Also cancel if still charged (they landed but monitoring task hasn't run yet)
        if (chargedPlayers.contains(uid)) {
            event.setCancelled(true);
        }
    }

    private void performEarthquake(Player player, double fallHeight) {
        World world = player.getWorld();
        Location impactLoc = player.getLocation().clone();

        // Scale everything by fall height
        // Tier 1: 3-5 blocks  → small (radius 4, dmg 4-6)
        // Tier 2: 5-10 blocks → medium (radius 6, dmg 6-10)
        // Tier 3: 10+ blocks  → MEGA (radius 9, dmg 10-16)
        double radius;
        double baseDamage;
        int tier;
        String tierName;
        Color tierColor;

        if (fallHeight >= 10) {
            tier = 3;
            radius = Math.min(9.0, 6.0 + (fallHeight - 10) * 0.3);
            baseDamage = Math.min(16.0, 10.0 + (fallHeight - 10) * 0.5);
            tierName = "§4§lMEGATERREMOTO";
            tierColor = Color.fromRGB(200, 30, 0);
        } else if (fallHeight >= 5) {
            tier = 2;
            radius = 4.0 + (fallHeight - 5) * 0.4;
            baseDamage = 6.0 + (fallHeight - 5) * 0.8;
            tierName = "§6§lTerremoto";
            tierColor = Color.fromRGB(255, 140, 0);
        } else {
            tier = 1;
            radius = 3.0 + (fallHeight - 3) * 0.5;
            baseDamage = 4.0 + (fallHeight - 3) * 1.0;
            tierName = "§e§lOnda Sísmica";
            tierColor = Color.fromRGB(255, 200, 50);
        }

        // === TITLE ===
        player.sendTitle(SmallCaps.convert(tierName), SmallCaps.convert("§7Caída: §e" + String.format("%.1f", fallHeight) + " bloques"), 0, 30, 10);
        com.moonlight.coreprotect.util.ActionBarUtil.send(player,
                "§6§l⚡ " + tierName + " §7— Radio: §e" + String.format("%.0f", radius) + " §7— Daño: §c" + String.format("%.0f", baseDamage));

        // === IMMEDIATE IMPACT ===
        // Camera shake effect via velocity nudge for nearby players
        for (Entity e : player.getNearbyEntities(radius, radius / 2, radius)) {
            if (!(e instanceof LivingEntity) || e == player) continue;
            if (e instanceof Player && isRestricted(player)) continue;

            LivingEntity le = (LivingEntity) e;
            double dist = le.getLocation().distance(impactLoc);
            if (dist > radius) continue;

            // Damage falloff: full at center, 30% at edge
            double falloff = 1.0 - (dist / radius) * 0.7;
            double damage = baseDamage * falloff;

            le.damage(damage, player);

            // Knockback (upward + outward)
            Vector dir = le.getLocation().toVector().subtract(impactLoc.toVector()).normalize();
            double kbStrength = 0.5 + (1.0 - dist / radius) * (0.3 + tier * 0.3);
            double kbY = 0.4 + tier * 0.15;
            le.setVelocity(dir.multiply(kbStrength).setY(kbY));
        }

        // === IMPACT SOUND ===
        switch (tier) {
            case 3:
                world.playSound(impactLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.4f);
                world.playSound(impactLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 0.5f);
                world.playSound(impactLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 0.5f);
                world.playSound(impactLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.5f);
                break;
            case 2:
                world.playSound(impactLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.6f);
                world.playSound(impactLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.5f, 0.7f);
                world.playSound(impactLoc, Sound.ENTITY_RAVAGER_ROAR, 0.6f, 0.8f);
                break;
            default:
                world.playSound(impactLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.8f);
                world.playSound(impactLoc, Sound.ENTITY_IRON_GOLEM_HURT, 0.8f, 0.5f);
                break;
        }
        world.playSound(impactLoc, Sound.BLOCK_ANVIL_LAND, 1.0f, 0.3f);

        // === SHOCKWAVE ANIMATION (expanding ring of particles) ===
        final double finalRadius = radius;
        final Color finalColor = tierColor;
        final int finalTier = tier;

        new BukkitRunnable() {
            double currentRadius = 0.5;
            int ticks = 0;

            @Override
            public void run() {
                if (currentRadius > finalRadius || ticks > 40) {
                    cancel();
                    return;
                }

                // Expanding ring of block cracks
                int points = (int) (currentRadius * 12);
                for (int i = 0; i < points; i++) {
                    double angle = (Math.PI * 2.0 / points) * i;
                    double x = Math.cos(angle) * currentRadius;
                    double z = Math.sin(angle) * currentRadius;
                    Location ringLoc = impactLoc.clone().add(x, 0.2, z);

                    // Ground debris
                    BlockData groundBlock = getGroundBlock(ringLoc);
                    world.spawnParticle(Particle.BLOCK, ringLoc, 3, 0.15, 0.3, 0.15, 0.1, groundBlock);

                    // Colored dust shockwave
                    world.spawnParticle(Particle.DUST, ringLoc.clone().add(0, 0.4, 0), 2, 0.1, 0.15, 0.1, 0,
                            new Particle.DustOptions(finalColor, 1.5f));
                }

                // Rumble sound for each expansion step
                if (ticks % 4 == 0) {
                    world.playSound(impactLoc, Sound.BLOCK_STONE_BREAK, 0.5f, 0.3f + (float) (currentRadius / finalRadius) * 0.5f);
                }

                currentRadius += 0.6 + (finalTier * 0.2);
                ticks += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);

        // === GROUND ERUPTION (pillars of debris at impact) ===
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks > 20) {
                    cancel();
                    return;
                }

                int pillars = 3 + finalTier * 2;
                for (int i = 0; i < pillars; i++) {
                    double angle = Math.random() * Math.PI * 2;
                    double r = Math.random() * finalRadius * 0.7;
                    Location pillarLoc = impactLoc.clone().add(Math.cos(angle) * r, 0.1, Math.sin(angle) * r);

                    BlockData ground = getGroundBlock(pillarLoc);
                    double height = 0.5 + Math.random() * (1.0 + finalTier * 0.5);

                    // Erupting pillar of ground
                    world.spawnParticle(Particle.BLOCK, pillarLoc.clone().add(0, height, 0),
                            5 + finalTier * 2, 0.2, height * 0.5, 0.2, 0.05, ground);

                    // Lava/magma particles for tier 3
                    if (finalTier >= 3 && Math.random() < 0.4) {
                        world.spawnParticle(Particle.LAVA, pillarLoc.clone().add(0, height, 0),
                                2, 0.2, 0.3, 0.2, 0);
                    }
                    if (finalTier >= 2 && Math.random() < 0.3) {
                        world.spawnParticle(Particle.FLAME, pillarLoc.clone().add(0, height * 0.5, 0),
                                3, 0.15, 0.2, 0.15, 0.02);
                    }
                }

                ticks += 2;
            }
        }.runTaskTimer(plugin, 2L, 2L);

        // === CRACK LINES radiating from center (for tier 2+) ===
        if (tier >= 2) {
            int numCracks = 4 + (tier - 2) * 4; // 4 for tier 2, 8 for tier 3
            for (int c = 0; c < numCracks; c++) {
                double crackAngle = (Math.PI * 2.0 / numCracks) * c + Math.random() * 0.3;
                final double ca = crackAngle;

                new BukkitRunnable() {
                    double dist = 0.5;
                    int step = 0;

                    @Override
                    public void run() {
                        if (dist > finalRadius * 0.8 || step > 15) {
                            cancel();
                            return;
                        }

                        double x = Math.cos(ca) * dist;
                        double z = Math.sin(ca) * dist;
                        Location crackLoc = impactLoc.clone().add(x, 0.15, z);

                        BlockData ground = getGroundBlock(crackLoc);
                        world.spawnParticle(Particle.BLOCK, crackLoc, 4, 0.05, 0.1, 0.05, 0, ground);
                        world.spawnParticle(Particle.DUST, crackLoc.clone().add(0, 0.2, 0), 2, 0.05, 0.05, 0.05, 0,
                                new Particle.DustOptions(finalColor, 1.0f));

                        // Small smoke
                        if (step % 2 == 0) {
                            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, crackLoc, 1, 0.05, 0.1, 0.05, 0.005);
                        }

                        dist += 0.5 + Math.random() * 0.3;
                        step++;
                    }
                }.runTaskTimer(plugin, c * 2L, 2L);
            }
        }

        // === CENTRAL IMPACT CRATER particles ===
        world.spawnParticle(Particle.EXPLOSION, impactLoc.clone().add(0, 0.5, 0), tier + 1, 0.5, 0.3, 0.5, 0);
        world.spawnParticle(Particle.DUST, impactLoc.clone().add(0, 0.3, 0), 30 + tier * 15, 1.5, 0.3, 1.5, 0,
                new Particle.DustOptions(tierColor, 2.0f));
        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, impactLoc.clone().add(0, 0.5, 0), 8 + tier * 4, 1, 0.5, 1, 0.02);

        // === FALLING BLOCK ERUPTION (phantom blocks fly up from the ground) ===
        // Wave 0: center burst (immediate)
        // Wave 1-3: expanding rings with delay
        int totalWaves = 1 + finalTier + (finalTier >= 3 ? 2 : 0); // tier 1=2, tier 2=3, tier 3=6
        for (int wave = 0; wave < totalWaves; wave++) {
            final int w = wave;
            final double waveRadius = (w == 0) ? 1.5 : (w * (finalRadius / totalWaves));
            final int blocksInWave;
            if (w == 0) {
                blocksInWave = (finalTier >= 3) ? 12 : (3 + finalTier * 2); // 12 center blocks for mega
            } else {
                blocksInWave = (finalTier >= 3) ? (int) (6 + waveRadius * 3) : (int) (4 + waveRadius * 2.5);
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (int i = 0; i < blocksInWave; i++) {
                    double angle = (Math.PI * 2.0 / blocksInWave) * i + Math.random() * 0.5;
                    double r = (w == 0) ? Math.random() * 1.5 : waveRadius + (Math.random() - 0.5) * 1.5;
                    double bx = impactLoc.getX() + Math.cos(angle) * r;
                    double bz = impactLoc.getZ() + Math.sin(angle) * r;

                    // Find the solid ground block at this position
                    Location surfaceLoc = new Location(world, bx, impactLoc.getY(), bz);
                    // Search up to 3 blocks down for solid ground
                    for (int dy = 0; dy >= -3; dy--) {
                        Location check = surfaceLoc.clone().add(0, dy, 0);
                        if (check.getBlock().getType().isSolid()) {
                            surfaceLoc = check;
                            break;
                        }
                    }

                    Material groundMat = surfaceLoc.getBlock().getType();
                    if (!groundMat.isSolid() || groundMat.isAir()) continue;
                    // Skip blocks that shouldn't be faked
                    if (groundMat == Material.BEDROCK || groundMat == Material.BARRIER
                            || groundMat == Material.SPAWNER || groundMat == Material.CHEST
                            || groundMat == Material.ENDER_CHEST || groundMat.name().contains("SHULKER")) continue;

                    BlockData blockData = groundMat.createBlockData();
                    // Spawn falling block slightly above the surface
                    Location spawnLoc = surfaceLoc.clone().add(0.5, 1.0, 0.5);

                    try {
                        FallingBlock fb = world.spawnFallingBlock(spawnLoc, blockData);
                        fb.setDropItem(false);
                        fb.setHurtEntities(false);
                        fb.setCancelDrop(true);
                        fb.setGravity(true);

                        // Launch velocity: upward + outward from center
                        double distFromCenter = Math.max(0.5, r);
                        double upVel;
                        if (finalTier >= 3) {
                            // MEGA: blocks fly very high
                            upVel = (w == 0)
                                    ? 1.0 + Math.random() * 0.8  // center: 1.0-1.8
                                    : 0.7 + Math.random() * 0.6; // rings: 0.7-1.3
                        } else {
                            upVel = 0.4 + Math.random() * (0.3 + finalTier * 0.2);
                            if (w == 0) {
                                upVel = 0.6 + Math.random() * (0.4 + finalTier * 0.3);
                            }
                        }
                        double outVel = (finalTier >= 3)
                                ? 0.1 + (distFromCenter / finalRadius) * 0.25
                                : 0.05 + (distFromCenter / finalRadius) * 0.15;
                        double vx = Math.cos(angle) * outVel;
                        double vz = Math.sin(angle) * outVel;
                        fb.setVelocity(new Vector(vx, upVel, vz));

                        // Auto-remove after 1.5-2.5 seconds so they don't place
                        int removeTicks = 30 + (int) (Math.random() * 20);
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (!fb.isDead()) {
                                // Poof particles where it disappears
                                Location fbLoc = fb.getLocation();
                                world.spawnParticle(Particle.BLOCK, fbLoc, 6, 0.2, 0.2, 0.2, 0.05, blockData);
                                world.spawnParticle(Particle.SMOKE, fbLoc, 2, 0.1, 0.1, 0.1, 0.02);
                                fb.remove();
                            }
                        }, removeTicks);
                    } catch (Exception ignored) {
                        // Silently ignore if block can't be spawned as falling
                    }
                }

                // Wave impact sound
                if (w > 0) {
                    world.playSound(impactLoc, Sound.BLOCK_STONE_BREAK, 0.6f, 0.4f + w * 0.15f);
                    world.playSound(impactLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.3f, 0.7f + w * 0.1f);
                }
            }, w * 3L); // Each wave 3 ticks (150ms) apart
        }

        // === TIER 3: Screen shake for nearby players (rapid velocity nudges) ===
        if (tier >= 3) {
            new BukkitRunnable() {
                int shakeTicks = 0;

                @Override
                public void run() {
                    if (shakeTicks >= 10) {
                        cancel();
                        return;
                    }
                    for (Entity e : player.getNearbyEntities(finalRadius, finalRadius / 2, finalRadius)) {
                        if (!(e instanceof Player) || e == player) continue;
                        Player nearby = (Player) e;
                        double dist = nearby.getLocation().distance(impactLoc);
                        if (dist > finalRadius) continue;
                        double shakePower = (1.0 - dist / finalRadius) * 0.08;
                        double sx = (Math.random() - 0.5) * shakePower;
                        double sz = (Math.random() - 0.5) * shakePower;
                        nearby.setVelocity(nearby.getVelocity().add(new Vector(sx, 0.02, sz)));
                    }
                    shakeTicks++;
                }
            }.runTaskTimer(plugin, 2L, 2L);
        }
    }

    private BlockData getGroundBlock(Location loc) {
        Location below = loc.clone();
        below.setY(Math.floor(below.getY()) - 1);
        Material mat = below.getBlock().getType();
        if (mat.isAir() || !mat.isSolid()) {
            return Material.STONE.createBlockData();
        }
        return mat.createBlockData();
    }

    private boolean isRestricted(Player player) {
        // Protección de nuevo jugador: < 3h de juego
        int NEW_PLAYER_TICKS = 3 * 60 * 60 * 20;
        if (player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) < NEW_PLAYER_TICKS
                && !player.hasPermission("wardstone.admin")) {
            return true;
        }
        Location loc = player.getLocation();
        if (player.getWorld().getName().equals("koth")) {
            com.moonlight.coreprotect.koth.KothManager km = plugin.getKothManager();
            if (km == null || !km.isActive() || km.getCurrentMode() != com.moonlight.coreprotect.koth.KothMode.VALE_TODO) return true;
        }
        if (plugin.getBossArenaManager() != null && plugin.getBossArenaManager().isInAnyArena(loc)) return true;
        if (plugin.getProtectionManager().isSpawnCore(loc)) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes usar habilidades en spawn."));
            return true;
        }
        com.moonlight.coreprotect.core.ProtectedRegion region = plugin.getProtectionManager().getRegionAt(loc);
        if (region != null && !region.canAccess(player.getUniqueId())) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes usar habilidades en zona protegida."));
            return true;
        }
        if (plugin.getAfkZoneManager() != null && plugin.getAfkZoneManager().isInZone(loc)) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes usar habilidades en zona AFK."));
            return true;
        }
        return false;
    }
}
