package com.moonlight.coreprotect.shadowhunter;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Listener para las habilidades PASIVAS del Casco del Errante.
 *
 * - Memoria del Errante (Pasiva): Regeneración I permanente mientras llevas el casco
 * - Aura del Vacío (Pasiva): Mobs hostiles en 5 bloques → Lentitud + Debilidad
 * - Último Aliento (Pasiva): Al recibir daño letal, te salva con 4♥ + invulnerabilidad 3s (CD 5min)
 */
public class ErranteHelmetListener implements Listener {

    private final CoreProtectPlugin plugin;
    private final Map<UUID, Long> lastBreathCooldowns = new HashMap<>();

    private static final long LAST_BREATH_COOLDOWN_MS = 1800000L; // 30 minutos
    private static final double AURA_RADIUS = 5.0;

    public ErranteHelmetListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        startPassiveTask();
    }

    // ==================== ÚLTIMO ALIENTO (salvar de muerte) ====================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();

        ItemStack helmet = player.getInventory().getHelmet();
        if (!ErranteHelmet.isErranteHelmet(helmet, plugin)) return;

        // No funciona en zonas restringidas (KotH, spawn, etc.)
        if (isRestricted(player)) return;

        // Check if this damage would kill the player
        if (player.getHealth() - event.getFinalDamage() > 0) return;

        UUID uid = player.getUniqueId();
        long now = System.currentTimeMillis();

        // Cooldown check
        if (lastBreathCooldowns.containsKey(uid) && now < lastBreathCooldowns.get(uid)) return;

        // Save from death!
        event.setCancelled(true);
        lastBreathCooldowns.put(uid, now + LAST_BREATH_COOLDOWN_MS);

        player.setHealth(8.0); // 4 hearts

        // Effects
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 4, false, true, true)); // 3s invuln
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 2, false, true, true)); // 3s regen III
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 1, false, true, true)); // 10s absorption

        Location loc = player.getLocation().add(0, 1, 0);
        World world = player.getWorld();

        // Explosion of purple particles
        world.spawnParticle(Particle.DUST, loc, 80, 2.0, 1.5, 2.0, 0,
                new Particle.DustOptions(Color.fromRGB(130, 0, 200), 2.5f));
        world.spawnParticle(Particle.DUST, loc, 50, 1.5, 1.0, 1.5, 0,
                new Particle.DustOptions(Color.fromRGB(80, 0, 150), 1.8f));
        world.spawnParticle(Particle.END_ROD, loc, 30, 2.0, 1.5, 2.0, 0.15);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 40, 1.0, 1.0, 1.0, 0.5);

        // Sound
        world.playSound(loc, Sound.ITEM_TOTEM_USE, 0.8f, 0.6f);
        world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 0.4f);
        world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 0.5f);

        // Knockback nearby enemies
        for (Entity e : player.getNearbyEntities(3.5, 2, 3.5)) {
            if (e instanceof LivingEntity && !(e instanceof Player)) {
                org.bukkit.util.Vector kb = e.getLocation().toVector()
                        .subtract(player.getLocation().toVector()).normalize().multiply(1.5).setY(0.5);
                e.setVelocity(kb);
            }
        }

        player.sendTitle(SmallCaps.convert("§5§l✦"), SmallCaps.convert("§7El Errante te protege..."), 5, 40, 15);
        com.moonlight.coreprotect.util.ActionBarUtil.send(player,
                "§5§l✦ §dÚltimo Aliento §7— Cooldown: §e5 minutos");
    }

    // ==================== PASIVAS (Regeneración + Aura) ====================

    private void startPassiveTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    ItemStack helmet = player.getInventory().getHelmet();
                    if (!ErranteHelmet.isErranteHelmet(helmet, plugin)) continue;

                    // No funciona en zonas restringidas
                    if (isRestricted(player)) continue;

                    // === MEMORIA DEL ERRANTE: Regeneración I permanente ===
                    PotionEffect currentRegen = player.getPotionEffect(PotionEffectType.REGENERATION);
                    if (currentRegen == null || (currentRegen.getAmplifier() == 0 && currentRegen.getDuration() < 80)) {
                        player.addPotionEffect(new PotionEffect(
                                PotionEffectType.REGENERATION, 100, 0, false, false, true)); // 5s, refreshed every 3s
                    }

                    // === AURA DEL VACÍO: Lentitud + Debilidad a mobs cercanos ===
                    Location loc = player.getLocation();
                    World world = player.getWorld();
                    boolean affected = false;

                    for (Entity e : player.getNearbyEntities(AURA_RADIUS, AURA_RADIUS, AURA_RADIUS)) {
                        if (!(e instanceof Monster)) continue;
                        LivingEntity mob = (LivingEntity) e;

                        mob.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 0, false, false, false));
                        mob.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 0, false, false, false));
                        affected = true;
                    }

                    // Ambient particles
                    if (affected) {
                        world.spawnParticle(Particle.DUST, loc.clone().add(0, 1, 0), 5, 1.5, 0.5, 1.5, 0,
                                new Particle.DustOptions(Color.fromRGB(80, 0, 120), 1.0f));
                        world.spawnParticle(Particle.SCULK_SOUL, loc.clone().add(0, 0.5, 0), 2, 1.0, 0.3, 1.0, 0.01);
                    }
                }
            }
        }.runTaskTimer(plugin, 60L, 60L); // Every 3 seconds
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
        if (plugin.getProtectionManager().isSpawnCore(loc)) return true;
        if (plugin.getAfkZoneManager() != null && plugin.getAfkZoneManager().isInZone(loc)) return true;
        return false;
    }
}
