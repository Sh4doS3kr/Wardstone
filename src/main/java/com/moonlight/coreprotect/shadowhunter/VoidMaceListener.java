package com.moonlight.coreprotect.shadowhunter;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener para el Martillo del Cero Absoluto (Misión 8).
 * Habilidades:
 * - Congelación Absoluta (Click derecho): freeze 1.25s. CD: 12s
 * - Impacto Glacial (Shift + Click derecho): AOE 6 bloques. CD: 25s
 * - Aliento Gélido (Pasiva): Slowness I 1.5s en cada golpe
 * - Voluntad de Hielo (Pasiva): inmune a freeze de polvo de nieve
 */
public class VoidMaceListener implements Listener {

    private final CoreProtectPlugin plugin;
    private final Map<UUID, Long> freezeCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> impactCooldown = new ConcurrentHashMap<>();
    private final Set<UUID> frozenPlayers = ConcurrentHashMap.newKeySet();

    private static final long FREEZE_CD = 12000L;
    private static final long IMPACT_CD = 25000L;
    private static final long FREEZE_DURATION_TICKS = 25L; // 1.25 seconds = 25 ticks

    public VoidMaceListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isFrozen(UUID uuid) {
        return frozenPlayers.contains(uuid);
    }

    // ==================== CLICK ABILITIES ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!VoidMace.isVoidMace(item, plugin)) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        // Bloquear habilidades en KOTH (excepto VALE_TODO) y minijuegos
        String worldName = player.getWorld().getName().toLowerCase();
        if (worldName.equals("minigames")) {
            player.sendMessage("§c§l✖ §7Habilidades del mazo desactivadas en este mundo.");
            return;
        }
        if (worldName.equals("koth")) {
            com.moonlight.coreprotect.koth.KothManager km = plugin.getKothManager();
            if (km == null || !km.isActive() || km.getCurrentMode() != com.moonlight.coreprotect.koth.KothMode.VALE_TODO) {
                player.sendMessage("§c§l✖ §7Habilidades del mazo desactivadas en este mundo.");
                return;
            }
        }

        if (player.isSneaking()) {
            // Shift + Click derecho = Impacto Glacial (AOE)
            handleImpactGlacial(player);
        } else {
            // Click derecho = Congelación Absoluta
            handleFreezeAbility(player);
        }
    }

    // ==================== CONGELACIÓN ABSOLUTA ====================

    private void handleFreezeAbility(Player player) {
        long now = System.currentTimeMillis();
        Long lastUse = freezeCooldown.get(player.getUniqueId());
        if (lastUse != null && now - lastUse < FREEZE_CD) {
            long remaining = (FREEZE_CD - (now - lastUse)) / 1000;
            player.sendMessage(SmallCaps.convert("§b§l❄ §7Congelación Absoluta en cooldown: §e" + remaining + "s"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
            return;
        }

        // Find nearest enemy within 5 blocks
        Player target = null;
        double closest = 5.0;
        for (Entity e : player.getNearbyEntities(5, 5, 5)) {
            if (e instanceof Player && !e.equals(player)) {
                double dist = e.getLocation().distance(player.getLocation());
                if (dist < closest) {
                    closest = dist;
                    target = (Player) e;
                }
            }
        }

        if (target == null) {
            player.sendMessage(SmallCaps.convert("§b§l❄ §7No hay enemigos cerca para congelar."));
            return;
        }

        freezeCooldown.put(player.getUniqueId(), now);
        final Player victim = target;

        // Freeze the target
        freezePlayer(victim, player);

        player.sendMessage(SmallCaps.convert("§b§l❄ §f" + victim.getName() + " §7ha sido §bcongelado §7durante §e1.25s§7."));
        player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 1.5f);
    }

    private void freezePlayer(Player victim, Player attacker) {
        if (frozenPlayers.contains(victim.getUniqueId())) return;

        frozenPlayers.add(victim.getUniqueId());
        Location freezeLoc = victim.getLocation().clone();

        // Visual: ice cage effect
        victim.sendTitle("§b§l❄ CONGELADO ❄", "§7No puedes moverte...", 0, 30, 5);
        victim.playSound(victim.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f);
        victim.playSound(victim.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0f, 0.8f);

        // Apply effects
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) FREEZE_DURATION_TICKS + 5, 127, false, false, false));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, (int) FREEZE_DURATION_TICKS + 5, 128, false, false, false));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, (int) FREEZE_DURATION_TICKS + 5, 127, false, false, false));

        // Particle effect during freeze
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= FREEZE_DURATION_TICKS || !victim.isOnline()) {
                    cancel();
                    unfreezePlayer(victim);
                    return;
                }

                Location loc = victim.getLocation().add(0, 1, 0);
                World world = loc.getWorld();

                // Ice particles around victim
                world.spawnParticle(Particle.SNOWFLAKE, loc, 8, 0.5, 0.8, 0.5, 0.02);
                world.spawnParticle(Particle.DUST, loc, 5, 0.4, 0.6, 0.4, 0,
                        new Particle.DustOptions(Color.fromRGB(150, 210, 255), 1.5f));
                world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 0.5, 0), 2, 0.3, 0.3, 0.3, 0.01);

                // Ice cage visual (blocks of ice particles at corners)
                if (ticks % 5 == 0) {
                    double r = 0.6;
                    for (int i = 0; i < 4; i++) {
                        double angle = (Math.PI / 2) * i + ticks * 0.1;
                        Location corner = victim.getLocation().clone().add(Math.cos(angle) * r, 0, Math.sin(angle) * r);
                        for (double y = 0; y <= 2.0; y += 0.5) {
                            world.spawnParticle(Particle.DUST, corner.clone().add(0, y, 0), 1, 0, 0, 0, 0,
                                    new Particle.DustOptions(Color.fromRGB(180, 230, 255), 2.0f));
                        }
                    }
                }

                // Sound
                if (ticks % 10 == 0) {
                    world.playSound(loc, Sound.BLOCK_POWDER_SNOW_STEP, 0.5f, 0.5f);
                }

                // Force position
                if (victim.isOnline()) {
                    victim.teleport(freezeLoc);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private void unfreezePlayer(Player victim) {
        frozenPlayers.remove(victim.getUniqueId());
        if (!victim.isOnline()) return;

        victim.removePotionEffect(PotionEffectType.SLOWNESS);
        victim.removePotionEffect(PotionEffectType.JUMP_BOOST);
        victim.removePotionEffect(PotionEffectType.MINING_FATIGUE);

        victim.sendTitle("", "§a§lDescongelado", 0, 15, 5);
        victim.playSound(victim.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.8f, 1.5f);
        victim.getWorld().spawnParticle(Particle.SNOWFLAKE, victim.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
    }

    // ==================== IMPACTO GLACIAL (AOE) ====================

    private void handleImpactGlacial(Player player) {
        long now = System.currentTimeMillis();
        Long lastUse = impactCooldown.get(player.getUniqueId());
        if (lastUse != null && now - lastUse < IMPACT_CD) {
            long remaining = (IMPACT_CD - (now - lastUse)) / 1000;
            player.sendMessage(SmallCaps.convert("§3§l❄ §7Impacto Glacial en cooldown: §e" + remaining + "s"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
            return;
        }

        impactCooldown.put(player.getUniqueId(), now);
        Location center = player.getLocation();
        World world = center.getWorld();

        // Visual: expanding ice wave
        new BukkitRunnable() {
            int radius = 0;

            @Override
            public void run() {
                if (radius > 6) {
                    cancel();
                    return;
                }

                // Expanding ring of ice particles
                for (int angle = 0; angle < 360; angle += 15) {
                    double rad = Math.toRadians(angle);
                    double x = Math.cos(rad) * radius;
                    double z = Math.sin(rad) * radius;
                    Location particleLoc = center.clone().add(x, 0.5, z);
                    world.spawnParticle(Particle.SNOWFLAKE, particleLoc, 3, 0.1, 0.2, 0.1, 0.01);
                    world.spawnParticle(Particle.DUST, particleLoc, 2, 0.1, 0.1, 0.1, 0,
                            new Particle.DustOptions(Color.fromRGB(100, 200, 255), 1.5f));
                }

                // Place snow layer on ground (temporary)
                if (radius > 0) {
                    for (int angle = 0; angle < 360; angle += 30) {
                        double rad = Math.toRadians(angle);
                        int bx = center.getBlockX() + (int) (Math.cos(rad) * radius);
                        int bz = center.getBlockZ() + (int) (Math.sin(rad) * radius);
                        int by = world.getHighestBlockYAt(bx, bz);
                        Location blockLoc = new Location(world, bx, by + 1, bz);
                        if (blockLoc.getBlock().getType() == Material.AIR) {
                            blockLoc.getBlock().setType(Material.SNOW);
                            // Remove snow after 5 seconds
                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                if (blockLoc.getBlock().getType() == Material.SNOW) {
                                    blockLoc.getBlock().setType(Material.AIR);
                                }
                            }, 100L);
                        }
                    }
                }

                radius++;
            }
        }.runTaskTimer(plugin, 0, 3);

        // Damage and slow nearby enemies
        for (Entity e : player.getNearbyEntities(6, 4, 6)) {
            if (e instanceof LivingEntity && !e.equals(player)) {
                LivingEntity target = (LivingEntity) e;
                target.damage(10, player);
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 3, false, true, true));
                target.getWorld().spawnParticle(Particle.SNOWFLAKE, target.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.05);

                if (target instanceof Player) {
                    ((Player) target).sendTitle("§b§l❄", "§3Impacto Glacial", 5, 20, 5);
                }
            }
        }

        // Sound effects
        world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 1.5f);
        world.playSound(center, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f);
        world.playSound(center, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0f, 0.5f);

        player.sendMessage(SmallCaps.convert("§3§l❄ §bImpacto Glacial §7desatado. El suelo tiembla de frío."));
    }

    // ==================== PASIVA: ALIENTO GÉLIDO ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;

        Player player = (Player) event.getDamager();
        LivingEntity target = (LivingEntity) event.getEntity();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!VoidMace.isVoidMace(item, plugin)) return;

        // Sin pasivas en KOTH (excepto VALE_TODO) / minijuegos
        String worldName = player.getWorld().getName().toLowerCase();
        if (worldName.equals("minigames")) return;
        if (worldName.equals("koth")) {
            com.moonlight.coreprotect.koth.KothManager km = plugin.getKothManager();
            if (km == null || !km.isActive() || km.getCurrentMode() != com.moonlight.coreprotect.koth.KothMode.VALE_TODO) return;
        }

        // Aliento Gélido: Slowness I 1.5s on each hit
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 0, false, true, true));

        // Frost particles on hit
        Location hitLoc = target.getLocation().add(0, 1, 0);
        target.getWorld().spawnParticle(Particle.SNOWFLAKE, hitLoc, 10, 0.3, 0.5, 0.3, 0.03);
        target.getWorld().spawnParticle(Particle.DUST, hitLoc, 5, 0.3, 0.3, 0.3, 0,
                new Particle.DustOptions(Color.fromRGB(180, 220, 255), 1.2f));
        target.getWorld().playSound(hitLoc, Sound.BLOCK_POWDER_SNOW_STEP, 0.4f, 1.2f);
    }

    // ==================== PASIVA: VOLUNTAD DE HIELO ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFreezeDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FREEZE) return;
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        if (VoidMace.isVoidMace(mainHand, plugin) || VoidMace.isVoidMace(offHand, plugin)) {
            event.setCancelled(true);
            player.setFreezeTicks(0);
        }
    }

    // ==================== PREVENT FROZEN PLAYER MOVEMENT ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFrozenPlayerMove(PlayerMoveEvent event) {
        if (!frozenPlayers.contains(event.getPlayer().getUniqueId())) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        // Allow looking around but not moving
        if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
            event.setTo(from);
        }
    }
}
