package com.moonlight.coreprotect.kits;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import com.moonlight.coreprotect.core.ProtectedRegion;
import com.moonlight.coreprotect.protection.ProtectionManager;

import java.util.*;

/**
 * Listener de habilidades del Kit Estelar (YT Secret Kit).
 *
 * Habilidades:
 *  1p - Eco: 3er golpe consecutivo al mismo objetivo = +1♥ extra + mini-knockback
 *  2p - Refracción: Si devuelves un golpe en 0.6s tras recibirlo = +30% daño (parry)
 *  3p - Resonancia Astral: Al bajar de 30% HP = pulso Slowness I enemigos + Speed I. CD 45s
 *  4p - Constelación: Quieto 2s = Resistencia I. Moverte la quita.
 *  Espada - Corte Estelar: Shift+Click Dcho = proyectil que explota 2.5♥ + KB. CD 12s
 */
public class KitYTSecretListener implements Listener {

    private final CoreProtectPlugin plugin;

    // === 1) ECO — combo tracker ===
    private final Map<UUID, UUID> lastTarget = new HashMap<>();       // attacker → target
    private final Map<UUID, Integer> comboCount = new HashMap<>();    // attacker → hits

    // === 2) REFRACCIÓN — parry window ===
    private final Map<UUID, Long> lastDamageTaken = new HashMap<>();  // defender → time hit received

    // === 3) RESONANCIA ASTRAL ===
    private final Map<UUID, Long> resonanceCooldown = new HashMap<>();
    private static final long RESONANCE_CD = 45_000L;

    // === 4) CONSTELACIÓN — standing still tracker ===
    private final Map<UUID, Location> lastKnownPos = new HashMap<>();
    private final Map<UUID, Long> standingSince = new HashMap<>();
    private final Set<UUID> hasResistance = new HashSet<>();

    // === 5) CORTE ESTELAR ===
    private final Map<UUID, Long> slashCooldown = new HashMap<>();
    private final Set<UUID> activeProjectiles = new HashSet<>();      // snowball entity UUIDs
    private static final long SLASH_CD = 12_000L;

    public KitYTSecretListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        // Tick Constelación every second
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickConstellation, 20L, 20L);
    }

    // === ZONE CHECK ===

    private boolean isProtectedZone(Location loc) {
        ProtectionManager protMgr = plugin.getProtectionManager();
        if (protMgr != null) {
            if (protMgr.isSpawnCore(loc)) return true;
            ProtectedRegion region = protMgr.getRegionAt(loc);
            if (region != null) return true;
        }
        return false;
    }

    // === HELPERS ===

    private int countPieces(Player p) {
        int count = 0;
        for (ItemStack armor : p.getInventory().getArmorContents()) {
            if (isYTPiece(armor)) count++;
        }
        return count;
    }

    private boolean isYTPiece(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(KitYTSecret.getYTKey(plugin), PersistentDataType.BYTE);
    }

    private boolean isYTSword(ItemStack item) {
        if (item == null || item.getType() != Material.DIAMOND_SWORD) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(KitYTSecret.getYTKey(plugin), PersistentDataType.BYTE);
    }

    private boolean isOwner(ItemStack item, Player p) {
        if (item == null || !item.hasItemMeta()) return false;
        String owner = item.getItemMeta().getPersistentDataContainer()
                .get(KitYTSecret.getYTOwnerKey(plugin), PersistentDataType.STRING);
        return p.getUniqueId().toString().equals(owner);
    }

    // ═══════════════════════════════════════════════════════════
    // 1) ECO — 3rd consecutive hit on same target: +2 dmg + KB
    // ═══════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.NORMAL)
    public void onHitEco(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;

        Player attacker = (Player) event.getDamager();
        if (countPieces(attacker) < 1) return;

        UUID aUid = attacker.getUniqueId();
        UUID tUid = event.getEntity().getUniqueId();
        LivingEntity target = (LivingEntity) event.getEntity();

        // Track consecutive hits on same target
        UUID prevTarget = lastTarget.get(aUid);
        if (prevTarget != null && prevTarget.equals(tUid)) {
            int hits = comboCount.getOrDefault(aUid, 0) + 1;
            comboCount.put(aUid, hits);
            if (hits >= 3) {
                comboCount.put(aUid, 0);
                // No bonus damage/KB in protected zones
                if (isProtectedZone(attacker.getLocation()) || isProtectedZone(target.getLocation())) return;
                // Bonus: +2 damage (1 heart) and mini-knockback
                event.setDamage(event.getDamage() + 2.0);
                Vector kb = attacker.getLocation().getDirection().normalize().multiply(0.5).setY(0.3);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!target.isDead()) target.setVelocity(target.getVelocity().add(kb));
                }, 1L);
                target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0),
                        12, 0.3, 0.3, 0.3, 0.1);
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.8f, 1.3f);
            }
        } else {
            lastTarget.put(aUid, tUid);
            comboCount.put(aUid, 1);
        }

        // Also feed Refracción for the target (if they are a player with 2p)
        if (target instanceof Player) {
            lastDamageTaken.put(target.getUniqueId(), System.currentTimeMillis());
        }

        // Check Resonancia Astral for the target
        if (target instanceof Player) {
            checkResonance((Player) target);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 2) REFRACCIÓN — parry: hit back within 0.6s = +30% damage
    // ═══════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH)
    public void onHitRefraction(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;

        Player attacker = (Player) event.getDamager();
        if (countPieces(attacker) < 2) return;

        UUID aUid = attacker.getUniqueId();
        Long lastHit = lastDamageTaken.get(aUid);
        if (lastHit == null) return;

        long elapsed = System.currentTimeMillis() - lastHit;
        if (elapsed <= 600) { // 0.6 seconds
            lastDamageTaken.remove(aUid);
            // No parry bonus in protected zones
            if (isProtectedZone(attacker.getLocation())) return;
            double bonus = event.getDamage() * 0.30;
            event.setDamage(event.getDamage() + bonus);
            attacker.getWorld().spawnParticle(Particle.ENCHANT, attacker.getLocation().add(0, 1, 0),
                    15, 0.5, 0.5, 0.5, 0.5);
            attacker.getWorld().playSound(attacker.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.5f);
        }
    }

    // Also track when the player themselves take damage (for parry window)
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTakeDamageForParry(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player victim = (Player) event.getEntity();
        if (countPieces(victim) < 2) return;
        lastDamageTaken.put(victim.getUniqueId(), System.currentTimeMillis());

        // Check resonance on any damage taken
        checkResonance(victim);
    }

    // ═══════════════════════════════════════════════════════════
    // 3) RESONANCIA ASTRAL — below 30% HP: pulse slow enemies + speed self
    // ═══════════════════════════════════════════════════════════

    private void checkResonance(Player p) {
        if (countPieces(p) < 3) return;
        if (p.getHealth() > p.getMaxHealth() * 0.30) return; // not below 30%

        UUID uid = p.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = resonanceCooldown.get(uid);
        if (last != null && now - last < RESONANCE_CD) return;

        resonanceCooldown.put(uid, now);

        // Slow nearby enemies (skip protected zones)
        for (Entity nearby : p.getNearbyEntities(6, 3, 6)) {
            if (nearby instanceof LivingEntity && nearby != p) {
                if (nearby instanceof Player && isProtectedZone(nearby.getLocation())) continue;
                ((LivingEntity) nearby).addPotionEffect(
                        new PotionEffect(PotionEffectType.SLOWNESS, 60, 0, false, true)); // 3s Slowness I
            }
        }
        // Speed self
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 0, false, true)); // 3s Speed I

        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0),
                25, 3, 1, 3, 0.05);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.5f);
        p.sendMessage(SmallCaps.convert("§b§l✧ §fResonancia Astral §7activada. §e45s §7de enfriamiento."));
    }

    // ═══════════════════════════════════════════════════════════
    // 4) CONSTELACIÓN — standing still 2s = Resistance I
    // ═══════════════════════════════════════════════════════════

    @EventHandler
    public void onMoveConstellation(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return; // ignore head rotation
        Player p = event.getPlayer();
        UUID uid = p.getUniqueId();

        // Reset standing timer on movement
        standingSince.remove(uid);
        lastKnownPos.remove(uid);

        // Remove resistance if they had it
        if (hasResistance.remove(uid)) {
            p.removePotionEffect(PotionEffectType.RESISTANCE);
        }
    }

    private void tickConstellation() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (countPieces(p) < 4) continue;
            UUID uid = p.getUniqueId();

            Location current = p.getLocation();
            Location last = lastKnownPos.get(uid);

            if (last != null && last.getWorld().equals(current.getWorld())
                    && last.distanceSquared(current) < 0.01) {
                // Still in same spot
                Long since = standingSince.get(uid);
                if (since != null && System.currentTimeMillis() - since >= 2000 && !hasResistance.contains(uid)) {
                    // Grant Resistance I (infinite, removed on move)
                    hasResistance.add(uid);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0, false, true));
                    p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 2, 0),
                            10, 0.3, 0.3, 0.3, 0.02);
                    p.sendMessage(SmallCaps.convert("§b§l✧ §fConstelación §7activada — Resistencia I."));
                }
            } else {
                // Started standing
                lastKnownPos.put(uid, current.clone());
                standingSince.put(uid, System.currentTimeMillis());
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 5) CORTE ESTELAR — Shift + Right Click: shoot projectile
    // ═══════════════════════════════════════════════════════════

    @EventHandler
    public void onSlash(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        Player p = event.getPlayer();
        if (!p.isSneaking()) return;

        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!isYTSword(hand)) return;
        if (!isOwner(hand, p)) return;

        UUID uid = p.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = slashCooldown.get(uid);
        if (last != null && now - last < SLASH_CD) {
            long rem = (SLASH_CD - (now - last)) / 1000;
            p.sendMessage(SmallCaps.convert("§c§l✖ §cCorte Estelar en enfriamiento: §e" + rem + "s"));
            return;
        }

        slashCooldown.put(uid, now);

        // Launch a snowball as projectile
        Snowball projectile = p.launchProjectile(Snowball.class);
        projectile.setVelocity(p.getLocation().getDirection().multiply(2.0));
        activeProjectiles.add(projectile.getUniqueId());

        // Trail particles
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (projectile.isDead() || !projectile.isValid()) {
                task.cancel();
                return;
            }
            projectile.getWorld().spawnParticle(Particle.END_ROD, projectile.getLocation(),
                    3, 0.05, 0.05, 0.05, 0.01);
        }, 1L, 1L);

        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDER_PEARL_THROW, 1f, 1.5f);
        p.sendMessage(SmallCaps.convert("§b§l✧ §fCorte Estelar §7lanzado."));
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball)) return;
        Snowball snowball = (Snowball) event.getEntity();
        if (!activeProjectiles.remove(snowball.getUniqueId())) return;
        if (!(snowball.getShooter() instanceof Player)) return;

        Player shooter = (Player) snowball.getShooter();
        Location impact = snowball.getLocation();

        // Explosion particles (no block damage)
        impact.getWorld().spawnParticle(Particle.FLASH, impact, 1);
        impact.getWorld().spawnParticle(Particle.END_ROD, impact, 20, 1, 1, 1, 0.1);
        impact.getWorld().spawnParticle(Particle.CRIT, impact, 15, 0.5, 0.5, 0.5, 0.2);
        impact.getWorld().playSound(impact, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 1.2f);

        // Damage + knockback in radius (skip protected zones)
        for (Entity nearby : impact.getWorld().getNearbyEntities(impact, 2.5, 2.5, 2.5)) {
            if (nearby instanceof LivingEntity && nearby != shooter) {
                if (nearby instanceof Player && isProtectedZone(nearby.getLocation())) continue;
                if (isProtectedZone(impact)) continue;
                LivingEntity target = (LivingEntity) nearby;
                target.damage(5.0, shooter); // 2.5 hearts
                Vector kb = target.getLocation().toVector().subtract(impact.toVector()).normalize().multiply(0.6).setY(0.3);
                target.setVelocity(target.getVelocity().add(kb));
            }
        }
    }

    // === Cleanup on quit ===
    public void cleanup(UUID uid) {
        lastTarget.remove(uid);
        comboCount.remove(uid);
        lastDamageTaken.remove(uid);
        resonanceCooldown.remove(uid);
        lastKnownPos.remove(uid);
        standingSince.remove(uid);
        hasResistance.remove(uid);
        slashCooldown.remove(uid);
    }
}
