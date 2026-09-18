package com.moonlight.coreprotect.essence;

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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Listener para la habilidad activa de "Filo Fantasma".
 * Click Derecho: Dash rápido con partículas, animación de agacharse,
 * y daño bonus si impacta a una entidad durante el dash.
 * Cooldown: 1.75 segundos.
 */
public class DashSwordListener implements Listener {

    private final CoreProtectPlugin plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final long COOLDOWN_MS = 1750L; // 1.75 seconds
    private static final double DASH_SPEED = 2.5;
    private static final double DASH_BONUS_DAMAGE = 6.0;
    private static final double DASH_TRAIL_DAMAGE = 3.0;
    private static final int DASH_TICKS = 6; // ~0.3 seconds of dash

    public DashSwordListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!DashSword.isDashSword(held, plugin)) return;

        // Check restricted zones
        if (isRestricted(player)) return;

        UUID uid = player.getUniqueId();
        long now = System.currentTimeMillis();

        // Cooldown check
        if (cooldowns.containsKey(uid) && now < cooldowns.get(uid)) {
            double left = (cooldowns.get(uid) - now) / 1000.0;
            com.moonlight.coreprotect.util.ActionBarUtil.send(player,
                    "§c§l✖ §cEnfriamiento: §e" + String.format("%.1f", left) + "s");
            return;
        }

        event.setCancelled(true);
        cooldowns.put(uid, now + COOLDOWN_MS);

        // Durability cost per use
        org.bukkit.inventory.meta.Damageable dmg = (org.bukkit.inventory.meta.Damageable) held.getItemMeta();
        int maxDur = held.getType().getMaxDurability();
        dmg.setDamage(Math.min(dmg.getDamage() + 3, maxDur));
        held.setItemMeta(dmg);
        if (dmg.getDamage() >= maxDur) {
            player.getInventory().setItemInMainHand(null);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            com.moonlight.coreprotect.util.ActionBarUtil.send(player, "§c§l✖ §cTu Filo Fantasma se ha roto.");
            return;
        }

        performDash(player);
    }

    private void performDash(Player player) {
        World world = player.getWorld();
        Location startLoc = player.getLocation().clone();

        // === INITIAL EFFECTS ===
        // Sound: woosh
        world.playSound(startLoc, Sound.ENTITY_BREEZE_WIND_BURST, 1.0f, 1.4f);
        world.playSound(startLoc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.8f);

        // Initial burst particles at feet
        world.spawnParticle(Particle.SWEEP_ATTACK, startLoc.clone().add(0, 0.5, 0), 3, 0.3, 0.2, 0.3, 0);
        world.spawnParticle(Particle.DUST, startLoc.clone().add(0, 0.5, 0), 15, 0.5, 0.3, 0.5, 0,
                new Particle.DustOptions(Color.fromRGB(0, 200, 255), 1.5f));

        // === LAUNCH DASH ===
        Vector direction = player.getLocation().getDirection().normalize();
        Vector dash = direction.clone().multiply(DASH_SPEED).setY(Math.max(direction.getY() * 0.5, 0.15));
        player.setVelocity(dash);

        // === SNEAK ANIMATION (toggle sneak for visual effect) ===
        player.setSneaking(true);

        // Title flash
        player.sendTitle(SmallCaps.convert("§b§l⚡"), "", 0, 8, 3);

        // === DASH TRAIL TASK ===
        Set<UUID> alreadyHit = new HashSet<>();

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= DASH_TICKS || !player.isOnline() || player.isDead()) {
                    // === END OF DASH ===
                    player.setSneaking(false);

                    Location endLoc = player.getLocation();

                    // Final impact particles
                    world.spawnParticle(Particle.DUST, endLoc.clone().add(0, 1, 0), 25, 1.0, 0.8, 1.0, 0,
                            new Particle.DustOptions(Color.fromRGB(0, 150, 255), 2.0f));
                    world.spawnParticle(Particle.DUST, endLoc.clone().add(0, 1, 0), 15, 0.8, 0.6, 0.8, 0,
                            new Particle.DustOptions(Color.fromRGB(100, 220, 255), 1.2f));
                    world.spawnParticle(Particle.SWEEP_ATTACK, endLoc.clone().add(0, 0.8, 0), 5, 0.5, 0.3, 0.5, 0);
                    world.spawnParticle(Particle.CLOUD, endLoc.clone().add(0, 0.3, 0), 8, 0.4, 0.2, 0.4, 0.02);

                    // End sound
                    world.playSound(endLoc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.8f, 1.2f);

                    // Bonus damage on nearby entities at end of dash
                    for (Entity e : player.getNearbyEntities(2.0, 1.5, 2.0)) {
                        if (!(e instanceof LivingEntity) || e == player) continue;
                        if (alreadyHit.contains(e.getUniqueId())) continue;
                        if (e instanceof Player && isRestricted(player)) continue;

                        LivingEntity le = (LivingEntity) e;
                        alreadyHit.add(e.getUniqueId());
                        le.damage(DASH_BONUS_DAMAGE, player);

                        // Impact particles on hit entity
                        Location hitLoc = le.getLocation().add(0, 1, 0);
                        world.spawnParticle(Particle.DUST, hitLoc, 20, 0.3, 0.5, 0.3, 0,
                                new Particle.DustOptions(Color.fromRGB(255, 50, 50), 1.8f));
                        world.spawnParticle(Particle.DAMAGE_INDICATOR, hitLoc, 8, 0.3, 0.3, 0.3, 0.1);
                        world.spawnParticle(Particle.CRIT, hitLoc, 12, 0.3, 0.5, 0.3, 0.2);
                        world.playSound(hitLoc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.8f);
                        world.playSound(hitLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.3f, 2.0f);

                        // Knockback the hit entity
                        Vector kb = direction.clone().multiply(0.8).setY(0.3);
                        le.setVelocity(kb);

                        com.moonlight.coreprotect.util.ActionBarUtil.send(player,
                                "§b§l⚡ §a¡Impacto! §c+" + (int) DASH_BONUS_DAMAGE + " daño bonus");
                    }

                    // If no one was hit
                    if (alreadyHit.isEmpty()) {
                        com.moonlight.coreprotect.util.ActionBarUtil.send(player,
                                "§b§l⚡ §7Embestida Fantasma");
                    }

                    cancel();
                    return;
                }

                // === DURING DASH ===
                Location trailLoc = player.getLocation();

                // Trail particles - ghostly blue streak
                world.spawnParticle(Particle.DUST, trailLoc.clone().add(0, 0.5, 0), 6, 0.3, 0.4, 0.3, 0,
                        new Particle.DustOptions(Color.fromRGB(0, 180, 255), 1.3f));
                world.spawnParticle(Particle.DUST, trailLoc.clone().add(0, 1.0, 0), 4, 0.2, 0.3, 0.2, 0,
                        new Particle.DustOptions(Color.fromRGB(150, 230, 255), 0.9f));

                // Soul-like wisps
                world.spawnParticle(Particle.SCULK_SOUL, trailLoc.clone().add(0, 0.8, 0), 2, 0.2, 0.3, 0.2, 0.02);

                // Speed lines (end rod streaks)
                world.spawnParticle(Particle.END_ROD, trailLoc.clone().add(0, 1.2, 0), 2, 0.1, 0.2, 0.1, 0.01);

                // Ground dust
                if (ticks % 2 == 0) {
                    world.spawnParticle(Particle.CLOUD, trailLoc.clone().add(0, 0.1, 0), 3, 0.2, 0.05, 0.2, 0.01);
                }

                // Trail sound
                if (ticks % 2 == 0) {
                    world.playSound(trailLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.5f + ticks * 0.1f);
                }

                // During-dash hit detection (trail damage)
                for (Entity e : player.getNearbyEntities(1.5, 1.5, 1.5)) {
                    if (!(e instanceof LivingEntity) || e == player) continue;
                    if (alreadyHit.contains(e.getUniqueId())) continue;
                    if (e instanceof Player && isRestricted(player)) continue;

                    LivingEntity le = (LivingEntity) e;
                    alreadyHit.add(e.getUniqueId());

                    // Trail hit: lower damage than end-of-dash impact
                    le.damage(DASH_TRAIL_DAMAGE + DASH_BONUS_DAMAGE, player);

                    // Impact particles
                    Location hitLoc = le.getLocation().add(0, 1, 0);
                    world.spawnParticle(Particle.DUST, hitLoc, 15, 0.3, 0.5, 0.3, 0,
                            new Particle.DustOptions(Color.fromRGB(255, 80, 80), 1.5f));
                    world.spawnParticle(Particle.CRIT, hitLoc, 10, 0.3, 0.4, 0.3, 0.15);
                    world.spawnParticle(Particle.DAMAGE_INDICATOR, hitLoc, 6, 0.2, 0.3, 0.2, 0.1);
                    world.playSound(hitLoc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.9f);

                    // Knockback
                    Vector kb = direction.clone().multiply(0.6).setY(0.25);
                    le.setVelocity(kb);

                    com.moonlight.coreprotect.util.ActionBarUtil.send(player,
                            "§b§l⚡ §a¡Impacto! §c+" + (int) (DASH_TRAIL_DAMAGE + DASH_BONUS_DAMAGE) + " daño bonus");
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
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
