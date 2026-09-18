package com.moonlight.coreprotect.shadowhunter;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.koth.KothManager;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Listener para "Último Suspiro" (Misión 5).
 * Habilidades:
 * - Lluvia Celestial (pasiva): al disparar, chance de invocar 5 flechas extra del cielo.
 * - Paso del Viento (activa): Shift + Disparar → dash 10 bloques + invulnerabilidad. CD 18s.
 * - Raíces del Cielo (pasiva): Regen I + Velocidad I + pétalos mientras sostienes el arco.
 */
public class VoidBowListener implements Listener {

    private final CoreProtectPlugin plugin;
    private final Map<UUID, Long> dashCD = new HashMap<>();
    private final Map<UUID, Long> rainCD = new HashMap<>();

    public VoidBowListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    /** Llamado periódicamente para aplicar la pasiva: Regen I + Velocidad I + pétalos de cerezo. */
    public void tickPassive() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (VoidBow.isVoidBow(hand, plugin)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 0, false, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0, false, false, false));
                // Pétalos de cerezo alrededor del jugador
                player.getWorld().spawnParticle(Particle.CHERRY_LEAVES,
                        player.getLocation().add(0, 1.2, 0),
                        3, 0.6, 0.4, 0.6, 0.01);
            }
        }
    }

    // ==================== DISPARO DEL ARCO ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();

        ItemStack bow = event.getBow();
        if (!VoidBow.isVoidBow(bow, plugin)) return;

        // Check if player is in KOTH world - prevent bow usage (excepto VALE_TODO)
        if (player.getWorld().getName().equalsIgnoreCase("koth")) {
            com.moonlight.coreprotect.koth.KothManager km = plugin.getKothManager();
            if (km == null || !km.isActive() || km.getCurrentMode() != com.moonlight.coreprotect.koth.KothMode.VALE_TODO) {
                event.setCancelled(true);
                com.moonlight.coreprotect.util.ActionBarUtil.send(player, "§c§l✖ §7No puedes usar el arco de la Misión 5 en el mundo KOTH");
                return;
            }
        }

        UUID uid = player.getUniqueId();
        long now = System.currentTimeMillis();

        // ==================== SHIFT + DISPARAR: PASO DEL VIENTO ====================
        if (player.isSneaking()) {
            // Cancelar la flecha normal
            event.setCancelled(true);

            long cd = dashCD.getOrDefault(uid, 0L);
            if (now < cd) {
                long left = (cd - now) / 1000 + 1;
                com.moonlight.coreprotect.util.ActionBarUtil.send(player, "§b▸ Paso del Viento §7— §cCooldown: §e" + left + "s");
                return;
            }

            useWindStep(player);
            dashCD.put(uid, now + 18000L);
            return;
        }

        // ==================== DISPARO NORMAL: LLUVIA CELESTIAL (PASIVA) ====================
        // Al disparar normalmente, activar lluvia celestial automáticamente
        long cd = rainCD.getOrDefault(uid, 0L);
        if (now >= cd) {
            // Activar lluvia celestial
            Entity projectile = event.getProjectile();
            if (projectile instanceof Arrow) {
                Arrow arrow = (Arrow) projectile;
                fireCelestialRain(player, arrow);
                rainCD.put(uid, now + 12000L);
                com.moonlight.coreprotect.util.ActionBarUtil.send(player, "§d✦ §7Lluvia Celestial activada.");
            }
        }
    }

    // ==================== LLUVIA CELESTIAL (PASIVA AL DISPARAR) ====================

    private void fireCelestialRain(Player player, Arrow originalArrow) {
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);

        // Tras 15 ticks, 5 flechas caen del cielo sobre donde impactó la flecha original
        new BukkitRunnable() {
            @Override
            public void run() {
                // Usar la posición actual de la flecha como punto objetivo
                Location target;
                if (originalArrow.isDead() || originalArrow.isOnGround()) {
                    target = originalArrow.getLocation();
                } else {
                    target = originalArrow.getLocation();
                }

                world.playSound(target, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.4f, 1.8f);
                world.spawnParticle(Particle.CHERRY_LEAVES, target.clone().add(0, 15, 0),
                        30, 2, 1, 2, 0.1);

                for (int i = 0; i < 5; i++) {
                    double ox = (Math.random() - 0.5) * 4;
                    double oz = (Math.random() - 0.5) * 4;
                    Location spawn = target.clone().add(ox, 15, oz);
                    Arrow arrow = world.spawnArrow(spawn,
                            new org.bukkit.util.Vector(ox * 0.02, -2.5, oz * 0.02),
                            2.0f, 2.0f);
                    arrow.setShooter(player);
                    arrow.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
                    arrow.setDamage(10.0);
                    arrow.setFireTicks(100);
                    arrow.addScoreboardTag("ultimo_suspiro_rain");

                    // Estela de pétalos en cada flecha
                    final Arrow fa = arrow;
                    new BukkitRunnable() {
                        int t = 0;
                        @Override
                        public void run() {
                            if (t > 60 || fa.isDead() || fa.isOnGround()) { cancel(); return; }
                            world.spawnParticle(Particle.DUST, fa.getLocation(), 2, 0.05, 0.05, 0.05, 0,
                                    new Particle.DustOptions(Color.fromRGB(255, 180, 220), 1.0f));
                            world.spawnParticle(Particle.CHERRY_LEAVES, fa.getLocation(), 1, 0.1, 0.1, 0.1, 0.01);
                            t++;
                        }
                    }.runTaskTimer(plugin, 0, 1);
                }
            }
        }.runTaskLater(plugin, 15L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRainArrowHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Arrow)) return;
        Arrow arrow = (Arrow) event.getDamager();
        if (!arrow.getScoreboardTags().contains("ultimo_suspiro_rain")) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;

        LivingEntity target = (LivingEntity) event.getEntity();
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1, false, true, true));

        Location hitLoc = target.getLocation().add(0, 1, 0);
        target.getWorld().spawnParticle(Particle.DUST, hitLoc, 20,
                0.5, 0.8, 0.5, 0,
                new Particle.DustOptions(Color.fromRGB(255, 150, 200), 1.5f));
        target.getWorld().spawnParticle(Particle.CHERRY_LEAVES, hitLoc, 10, 0.4, 0.6, 0.4, 0.05);
        target.getWorld().playSound(hitLoc, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.8f, 1.0f);
    }

    // ==================== PASO DEL VIENTO (SHIFT + DISPARAR) ====================

    private void useWindStep(Player player) {
        World world = player.getWorld();
        Location from = player.getLocation().clone();
        org.bukkit.util.Vector dir = from.getDirection().setY(0).normalize();

        world.playSound(from, Sound.ENTITY_BREEZE_WIND_BURST, 1.0f, 1.2f);
        world.playSound(from, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 1.5f);

        // Invulnerabilidad y whitelist de teleport durante el dash
        player.setInvulnerable(true);
        player.setMetadata("bow_dash", new org.bukkit.metadata.FixedMetadataValue(plugin, true));

        // Dash: mover al jugador 10 bloques en la dirección que mira
        new BukkitRunnable() {
            int step = 0;
            @Override
            public void run() {
                if (step >= 10 || !player.isOnline()) {
                    cancel();
                    player.setInvulnerable(false);
                    player.removeMetadata("bow_dash", plugin);
                    world.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.6f, 1.5f);
                    return;
                }

                Location current = player.getLocation();
                Location next = current.clone().add(dir.clone().multiply(1.0));
                next.setYaw(current.getYaw());
                next.setPitch(current.getPitch());

                // Verificar que no choque con bloques sólidos
                if (next.getBlock().getType().isSolid()) {
                    cancel();
                    player.setInvulnerable(false);
                    player.removeMetadata("bow_dash", plugin);
                    return;
                }

                player.teleport(next);
                player.setFallDistance(0);

                // Rastro de pétalos y partículas
                world.spawnParticle(Particle.CHERRY_LEAVES, current.clone().add(0, 0.5, 0),
                        8, 0.3, 0.3, 0.3, 0.05);
                world.spawnParticle(Particle.DUST, current.clone().add(0, 1, 0),
                        4, 0.2, 0.5, 0.2, 0,
                        new Particle.DustOptions(Color.fromRGB(180, 255, 220), 1.2f));
                world.spawnParticle(Particle.END_ROD, current.clone().add(0, 0.5, 0),
                        2, 0.2, 0.2, 0.2, 0.02);

                step++;
            }
        }.runTaskTimer(plugin, 0, 1);

        com.moonlight.coreprotect.util.ActionBarUtil.send(player, "§b✦ §7Paso del Viento.");
    }

    // ==================== LIMPIEZA ====================

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uid = event.getPlayer().getUniqueId();
        rainCD.remove(uid);
        dashCD.remove(uid);
    }
}
