package com.moonlight.coreprotect.kits;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Listener de habilidades pasivas del Owner Kit.
 * Absolutamente roto. Sin restricciones.
 */
public class OwnerKitListener implements Listener {

    private final CoreProtectPlugin plugin;
    private final Map<UUID, Long> juicioCooldowns = new HashMap<>();
    private static final long JUICIO_CD_MS = 10_000L;

    public OwnerKitListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    // ==================== INVULNERABILIDAD DIVINA (1+ pieza): Inmune a TODO ====================

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!player.isOp()) return;

        int pieces = OwnerKitCommand.getOwnerPieceCount(player, plugin);
        if (pieces < 1) return;

        // Cancelar TODO el daño excepto void y /kill
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.KILL) return;

        event.setCancelled(true);
        player.setFireTicks(0);

        // Partícula sutil de absorción
        if (Math.random() < 0.1 && event.getCause() != EntityDamageEvent.DamageCause.STARVATION) {
            Location loc = player.getLocation().add(0, 1, 0);
            loc.getWorld().spawnParticle(Particle.DUST, loc, 5, 0.4, 0.6, 0.4, 0,
                    new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.0f));
        }
    }

    // ==================== GOLPE DIVINO: Rayo al golpear + daño masivo ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!player.isOp()) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        int pieces = OwnerKitCommand.getOwnerPieceCount(player, plugin);
        if (pieces < 1) return;

        // No afectar a otros jugadores con owner kit
        if (target instanceof Player targetPlayer) {
            if (OwnerKitCommand.getOwnerPieceCount(targetPlayer, plugin) > 0) return;
        }

        // Rayo sobre el enemigo
        Location targetLoc = target.getLocation();
        target.getWorld().strikeLightningEffect(targetLoc);

        // Partículas doradas de impacto
        target.getWorld().spawnParticle(Particle.DUST, targetLoc.clone().add(0, 1, 0), 20, 0.5, 1, 0.5, 0,
                new Particle.DustOptions(Color.fromRGB(255, 215, 0), 2.0f));
        target.getWorld().spawnParticle(Particle.END_ROD, targetLoc.clone().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.05);

        // Daño extra masivo (los mobs no-jugador mueren instantáneamente)
        if (!(target instanceof Player)) {
            target.setHealth(0);
        } else {
            event.setDamage(event.getDamage() * 3.0);
        }
    }

    // ==================== JUICIO FINAL: Habilidad activa (Shift + Click Derecho con espada) ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwordAbility(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (!player.isOp()) return;
        if (!player.isSneaking()) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.NETHERITE_SWORD) return;
        if (!OwnerKitCommand.isOwnerPiece(item, plugin)) return;

        UUID uid = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (juicioCooldowns.containsKey(uid) && now < juicioCooldowns.get(uid)) {
            long left = (juicioCooldowns.get(uid) - now) / 1000;
            com.moonlight.coreprotect.util.ActionBarUtil.send(player,
                    "§6§l⭐ §cEnfriamiento: §e" + left + "s");
            return;
        }

        juicioCooldowns.put(uid, now + JUICIO_CD_MS);

        Location center = player.getLocation();
        World world = center.getWorld();

        // Efecto: el jugador se eleva
        player.setVelocity(new org.bukkit.util.Vector(0, 1.5, 0));
        player.sendMessage(SmallCaps.convert("§6§l⭐ §e¡¡JUICIO FINAL ACTIVADO!!"));

        // Sonido épico
        world.playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.3f);
        world.playSound(center, Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.5f);
        world.playSound(center, Sound.BLOCK_END_PORTAL_SPAWN, 1.0f, 0.5f);

        // Onda de partículas doradas
        world.spawnParticle(Particle.DUST, center.clone().add(0, 2, 0), 300, 5, 3, 5, 0,
                new Particle.DustOptions(Color.fromRGB(255, 215, 0), 3.0f));
        world.spawnParticle(Particle.END_ROD, center.clone().add(0, 3, 0), 200, 5, 5, 5, 0.2);
        world.spawnParticle(Particle.FLASH, center.clone().add(0, 5, 0), 5, 0, 0, 0, 0);

        // Tormenta de rayos progresiva
        new BukkitRunnable() {
            int wave = 0;
            @Override
            public void run() {
                if (wave >= 6 || !player.isOnline()) {
                    cancel();
                    return;
                }

                double radius = 5 + wave * 5; // 5, 10, 15, 20, 25, 30
                int strikes = 4 + wave * 2; // 4, 6, 8, 10, 12, 14

                for (int i = 0; i < strikes; i++) {
                    double angle = (Math.PI * 2.0 / strikes) * i + Math.random() * 0.5;
                    double dist = radius * (0.5 + Math.random() * 0.5);
                    Location strike = center.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);

                    // Ajustar Y al suelo
                    strike.setY(world.getHighestBlockYAt(strike.getBlockX(), strike.getBlockZ()));

                    world.strikeLightningEffect(strike);

                    // Daño a entidades en radio de cada rayo
                    for (Entity e : world.getNearbyEntities(strike, 3, 3, 3)) {
                        if (e instanceof Player p && p.equals(player)) continue;
                        if (e instanceof Player p && OwnerKitCommand.getOwnerPieceCount(p, plugin) > 0) continue;
                        if (e instanceof LivingEntity living) {
                            living.damage(100.0, player);
                            living.setFireTicks(200);
                        }
                    }
                }

                // Partículas de onda
                for (int i = 0; i < 36; i++) {
                    double a = (Math.PI * 2.0 / 36) * i;
                    Location p = center.clone().add(Math.cos(a) * radius, 1, Math.sin(a) * radius);
                    world.spawnParticle(Particle.DUST, p, 3, 0.2, 0.5, 0.2, 0,
                            new Particle.DustOptions(Color.fromRGB(255, 200, 50), 2.0f));
                }

                world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 0.5f + wave * 0.2f);
                wave++;
            }
        }.runTaskTimer(plugin, 5L, 8L);
    }

    // ==================== TICK: Buffs permanentes + Aura + Regen + Vuelo ====================

    /**
     * Llamar cada 20 ticks (1 segundo) desde el plugin principal.
     */
    public void tickPassives() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isOp()) continue;

            int pieces = OwnerKitCommand.getOwnerPieceCount(player, plugin);
            if (pieces < 1) continue;

            // Quitar fuego siempre
            if (player.getFireTicks() > 0) player.setFireTicks(0);

            // === 1+ piezas: Partículas ambientales doradas ===
            Location loc = player.getLocation().add(0, 0.5, 0);
            World world = loc.getWorld();

            if (Math.random() < 0.15) {
                world.spawnParticle(Particle.END_ROD,
                        loc.clone().add((Math.random() - 0.5) * 0.8, Math.random() * 2, (Math.random() - 0.5) * 0.8),
                        1, 0, 0.02, 0, 0.003);
            }
            if (Math.random() < 0.1) {
                world.spawnParticle(Particle.DUST,
                        loc.clone().add((Math.random() - 0.5) * 0.5, Math.random() * 1.8, (Math.random() - 0.5) * 0.5),
                        1, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 215, 0), 0.8f));
            }

            // === 2+ piezas: Aura Aniquiladora — matar mobs hostiles en 25 bloques ===
            if (pieces >= 2) {
                for (Entity e : player.getNearbyEntities(25, 25, 25)) {
                    if (e instanceof Monster monster) {
                        // Partícula de desintegración
                        Location mLoc = monster.getLocation().add(0, 1, 0);
                        world.spawnParticle(Particle.DUST, mLoc, 8, 0.3, 0.5, 0.3, 0,
                                new Particle.DustOptions(Color.fromRGB(255, 200, 50), 1.5f));
                        world.spawnParticle(Particle.END_ROD, mLoc, 5, 0.2, 0.3, 0.2, 0.05);
                        monster.setHealth(0);
                    }
                }

                // Anillo dorado sutil
                if (Math.random() < 0.3) {
                    for (int i = 0; i < 12; i++) {
                        double angle = (Math.PI * 2.0 / 12) * i + (System.currentTimeMillis() / 1000.0);
                        Location ring = loc.clone().add(Math.cos(angle) * 2, 0.1, Math.sin(angle) * 2);
                        world.spawnParticle(Particle.DUST, ring, 1, 0, 0, 0, 0,
                                new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.2f));
                    }
                }
            }

            // === 3+ piezas: Poder Absoluto — Buffs permanentes ===
            if (pieces >= 3) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 4, true, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 2, true, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 3, true, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 40, 2, true, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 400, 0, true, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 40, 0, true, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 40, 0, true, false, false));
            }

            // === 4 piezas: Regeneración Infinita + Vuelo + Saturación ===
            if (pieces >= 4) {
                // Curación completa instantánea
                double maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                if (player.getHealth() < maxHealth) {
                    player.setHealth(maxHealth);
                }

                // Saturación infinita
                player.setFoodLevel(20);
                player.setSaturation(20f);

                // Vuelo permanente
                if (!player.getAllowFlight()) {
                    player.setAllowFlight(true);
                }

                // Partículas extra de divinidad
                if (Math.random() < 0.15) {
                    world.spawnParticle(Particle.TOTEM_OF_UNDYING,
                            player.getLocation().add(0, 2.5, 0), 3, 0.3, 0.3, 0.3, 0.02);
                }

                // Columna de luz cada 5 segundos
                if (System.currentTimeMillis() % 5000 < 1000) {
                    for (int y = 0; y < 6; y++) {
                        world.spawnParticle(Particle.END_ROD,
                                player.getLocation().add(0, y, 0), 3, 0.1, 0.2, 0.1, 0.01);
                    }
                }
            }
        }
    }
}
