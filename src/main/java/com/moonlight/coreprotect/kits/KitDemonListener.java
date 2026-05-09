package com.moonlight.coreprotect.kits;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.protection.ProtectionManager;
import com.moonlight.coreprotect.core.ProtectedRegion;
import com.moonlight.coreprotect.koth.KothManager;
import com.moonlight.coreprotect.koth.KothCommand;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Listener de habilidades pasivas del GKit Demon.
 * Las habilidades se activan al llevar piezas del set.
 *
 * - Alma Ardiente (1+ piezas): Inmunidad a fuego y lava
 * - Sed de Sangre (2+ piezas): Lifesteal 15% al golpear
 * - Aura Infernal (3+ piezas): Enemigos cercanos reciben Slowness + daño periódico
 * - Pacto Demoníaco (4 piezas): Al bajar de 20% HP → ráfaga de Fuerza II + Regen III (cooldown 60s)
 */
public class KitDemonListener implements Listener {

    private final CoreProtectPlugin plugin;

    // Cooldown del Pacto Demoníaco (60 segundos)
    private final Map<UUID, Long> pactCooldowns = new HashMap<>();
    private static final long PACT_COOLDOWN_MS = 60_000L;

    // Marca Demoniaca
    private final Map<UUID, UUID> marcaByAttacker = new HashMap<>();  // attacker → marked target
    private final Map<UUID, Long> marcaExpiry     = new HashMap<>();  // marked target → expiry
    private final Map<UUID, Long> marcaCooldowns  = new HashMap<>();  // attacker → next mark available
    private static final long MARCA_DURATION_MS = 8_000L;
    private static final long MARCA_CD_MS       = 15_000L;

    // Cooldown de Espada (Furia Demoníaca)
    private final Map<UUID, Long> furiaCooldowns = new HashMap<>();
    private static final long FURIA_CD_MS = 15_000L;

    public KitDemonListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    // ==================== STAFF MODE CHECK (NookureStaff) ====================

    private boolean isInStaffMode(Player player) {
        return player.hasMetadata("vanished");
    }

    // ==================== PROTECCIÓN SPAWN ====================

    private boolean isProtectedZone(Location loc) {
        ProtectionManager protMgr = plugin.getProtectionManager();
        if (protMgr != null) {
            if (protMgr.isSpawnCore(loc)) return true;
            ProtectedRegion region = protMgr.getRegionAt(loc);
            if (region != null) return true;
        }
        return false;
    }

    private boolean isProtectedZone(Player player) {
        return isProtectedZone(player.getLocation());
    }

    // ==================== NEW PLAYER PROTECTION ====================

    private boolean isNewPlayer(Player player) {
        int NEW_PLAYER_TICKS = 3 * 60 * 60 * 20;
        return player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) < NEW_PLAYER_TICKS
                && !player.hasPermission("wardstone.admin");
    }

    // ==================== KOTH ZONE CHECK ====================

    private boolean isInKothZone(Player player) {
        KothManager kothManager = plugin.getKothManager();
        if (kothManager == null || !kothManager.isActive()) return false;
        if (kothManager.getCurrentZone() == null) return false;
        return kothManager.getCurrentZone().contains(player.getLocation());
    }

    // ==================== ALMA ARDIENTE (1+ piezas): Inmunidad a fuego/lava ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFireDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();

        // Ignorar jugadores en staff mode
        if (isInStaffMode(player)) return;
        if (isNewPlayer(player)) return;

        int pieces = KitDemon.getDemonPieceCount(player, plugin);
        if (pieces < 1) return;

        // Disable in KOTH
        if (isInKothZone(player)) return;

        // Disable during end event
        if (KothCommand.isEndEventActive()) return;

        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.FIRE ||
            cause == EntityDamageEvent.DamageCause.FIRE_TICK ||
            cause == EntityDamageEvent.DamageCause.LAVA ||
            cause == EntityDamageEvent.DamageCause.HOT_FLOOR) {

            event.setCancelled(true);
            player.setFireTicks(0);

            // Partículas sutiles de llama absorbida
            if (Math.random() < 0.2) {
                Location loc = player.getLocation().add(0, 1, 0);
                loc.getWorld().spawnParticle(Particle.DUST, loc, 3, 0.3, 0.5, 0.3, 0,
                        new Particle.DustOptions(Color.fromRGB(200, 50, 0), 0.8f));
                loc.getWorld().spawnParticle(Particle.SMOKE, loc, 2, 0.2, 0.3, 0.2, 0.01);
            }
        }
    }

    // ==================== MARCA DEMONIACA: boost de daño (HIGH) ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMarcaBoost(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;
        Player attacker = (Player) event.getDamager();

        // Ignorar si atacante o víctima están en staff mode
        if (isInStaffMode(attacker)) return;
        if (isNewPlayer(attacker)) return;
        if (event.getEntity() instanceof Player && isInStaffMode((Player) event.getEntity())) return;

        if (KitDemon.getDemonPieceCount(attacker, plugin) < 2) return;

        // Disable in KOTH
        if (isInKothZone(attacker)) return;

        // Disable during end event
        if (KothCommand.isEndEventActive()) return;

        UUID targetId = event.getEntity().getUniqueId();
        UUID markedId = marcaByAttacker.get(attacker.getUniqueId());
        if (markedId == null || !markedId.equals(targetId)) return;

        Long expiry = marcaExpiry.get(targetId);
        if (expiry == null || System.currentTimeMillis() > expiry) {
            marcaByAttacker.remove(attacker.getUniqueId());
            marcaExpiry.remove(targetId);
            return;
        }

        event.setDamage(event.getDamage() * 1.35);
        Location tl = event.getEntity().getLocation().add(0, 1, 0);
        tl.getWorld().spawnParticle(Particle.DUST, tl, 6, 0.3, 0.5, 0.3, 0,
                new Particle.DustOptions(Color.fromRGB(200, 0, 30), 1.4f));
        tl.getWorld().spawnParticle(Particle.SOUL, tl, 4, 0.2, 0.3, 0.2, 0.02);
    }

    // ==================== SED DE SANGRE (2+ piezas): Lifesteal 20% + Marca ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;

        Player attacker = (Player) event.getDamager();

        // Ignorar si atacante o víctima están en staff mode
        if (isInStaffMode(attacker)) return;
        if (isNewPlayer(attacker)) return;
        if (event.getEntity() instanceof Player && isInStaffMode((Player) event.getEntity())) return;

        int pieces = KitDemon.getDemonPieceCount(attacker, plugin);
        if (pieces < 2) return;

        // Disable in KOTH
        if (isInKothZone(attacker)) return;

        // Disable during end event
        if (KothCommand.isEndEventActive()) return;

        LivingEntity target = (LivingEntity) event.getEntity();
        UUID attackerId = attacker.getUniqueId();
        long now = System.currentTimeMillis();

        // === MARCA DEMONIACA: aplicar marca ===
        if (now >= marcaCooldowns.getOrDefault(attackerId, 0L)) {
            UUID oldMark = marcaByAttacker.get(attackerId);
            if (oldMark != null) marcaExpiry.remove(oldMark);

            marcaByAttacker.put(attackerId, target.getUniqueId());
            marcaExpiry.put(target.getUniqueId(), now + MARCA_DURATION_MS);
            marcaCooldowns.put(attackerId, now + MARCA_CD_MS);

            Location tl = target.getLocation().add(0, 1.5, 0);
            tl.getWorld().spawnParticle(Particle.SOUL, tl, 14, 0.3, 0.6, 0.3, 0.04);
            tl.getWorld().spawnParticle(Particle.DUST, tl, 10, 0.25, 0.6, 0.25, 0,
                    new Particle.DustOptions(Color.fromRGB(210, 0, 40), 1.6f));
            tl.getWorld().spawnParticle(Particle.SMALL_FLAME, tl, 6, 0.2, 0.3, 0.2, 0.02);
            tl.getWorld().playSound(tl, Sound.ENTITY_BLAZE_SHOOT, 0.5f, 1.8f);

            com.moonlight.coreprotect.util.ActionBarUtil.send(attacker, "\u00a7c\u2666 \u00a77Marca Demoníaca aplicada. \u00a7c+35%\u00a77 daño durante \u00a7c8s\u00a77.");
            if (target instanceof Player)
                com.moonlight.coreprotect.util.ActionBarUtil.send((Player) target, "\u00a7c\u2666 \u00a77Estás marcado por el demonio...");
        }

        // === SED DE SANGRE: lifesteal 20% ===
        double damage = event.getFinalDamage();
        double heal = damage * 0.20;
        double maxHealth = attacker.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        attacker.setHealth(Math.min(maxHealth, attacker.getHealth() + heal));

        Location victimLoc = target.getLocation().add(0, 1, 0);
        Location attackerLoc = attacker.getLocation().add(0, 1, 0);
        victimLoc.getWorld().spawnParticle(Particle.DUST, victimLoc, 6, 0.3, 0.3, 0.3, 0,
                new Particle.DustOptions(Color.fromRGB(150, 0, 0), 1.0f));
        attackerLoc.getWorld().spawnParticle(Particle.DUST, attackerLoc, 3, 0.2, 0.3, 0.2, 0,
                new Particle.DustOptions(Color.fromRGB(80, 0, 0), 0.6f));
    }

    // ==================== CELERIDAD AL MATAR (2+ piezas) ====================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        // Ignorar si está en staff mode
        if (isInStaffMode(killer)) return;
        if (isNewPlayer(killer)) return;

        if (KitDemon.getDemonPieceCount(killer, plugin) < 2) return;

        // Disable in KOTH
        if (isInKothZone(killer)) return;

        // Disable during end event
        if (KothCommand.isEndEventActive()) return;

        killer.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 100, 0, false, false, true));
        Location kl = killer.getLocation().add(0, 1, 0);
        kl.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, kl, 6, 0.3, 0.3, 0.3, 0.03);
        kl.getWorld().spawnParticle(Particle.DUST, kl, 4, 0.2, 0.4, 0.2, 0,
                new Particle.DustOptions(Color.fromRGB(180, 0, 0), 1.0f));

        // Limpiar marca del muerto
        UUID deadId = event.getEntity().getUniqueId();
        marcaExpiry.remove(deadId);
        marcaByAttacker.entrySet().removeIf(e -> e.getValue().equals(deadId));
    }

    // ==================== AURA INFERNAL (3+ piezas): Slowness + daño periódico ====================

    /**
     * Llamar cada 40 ticks (2 segundos) desde el plugin principal.
     */
    public void tickAuraInfernal() {
        // Disable during end event
        if (KothCommand.isEndEventActive()) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            // Ignorar jugadores en staff mode
            if (isInStaffMode(player)) continue;
            if (isNewPlayer(player)) continue;

            int pieces = KitDemon.getDemonPieceCount(player, plugin);
            if (pieces < 3) continue;

            // Disable in KOTH
            if (isInKothZone(player)) continue;

            Location loc = player.getLocation();
            World world = loc.getWorld();

            // No hacer daño si el jugador con demon kit está en zona protegida
            boolean playerInProtected = isProtectedZone(player);

            // Partículas de aura demoníaca alrededor del jugador
            for (int i = 0; i < 8; i++) {
                double angle = (Math.PI * 2.0 / 8) * i + (System.currentTimeMillis() / 500.0);
                double radius = 3.5;
                Location particleLoc = loc.clone().add(Math.cos(angle) * radius, 0.3, Math.sin(angle) * radius);
                world.spawnParticle(Particle.DUST, particleLoc, 1, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(180, 30, 0), 1.2f));
                world.spawnParticle(Particle.SMALL_FLAME, particleLoc, 1, 0.05, 0.05, 0.05, 0.005);
            }

            // Si el jugador con demon kit está en zona protegida, no hacer daño a nadie
            if (playerInProtected) continue;

            // Afectar entidades cercanas (4 bloques)
            for (Entity nearby : player.getNearbyEntities(4, 3, 4)) {
                if (!(nearby instanceof LivingEntity)) continue;
                if (nearby instanceof Player) {
                    Player target = (Player) nearby;
                    // No afectar a staff en staff mode
                    if (isInStaffMode(target)) continue;
                    // No afectar a otros con demon kit
                    if (KitDemon.getDemonPieceCount(target, plugin) >= 3) continue;
                    // No afectar a jugadores en zona protegida
                    if (isProtectedZone(target.getLocation())) continue;
                }

                LivingEntity living = (LivingEntity) nearby;

                // No afectar si el objetivo está en zona protegida
                if (isProtectedZone(living.getLocation())) continue;

                // CRITICAL: No afectar mobs en protecciones de otros jugadores
                ProtectionManager protMgr = plugin.getProtectionManager();
                if (protMgr != null) {
                    ProtectedRegion region = protMgr.getRegionAt(living.getLocation());
                    if (region != null && !region.canAccess(player.getUniqueId())) {
                        // Mob está en protección de otro jugador — no dañar
                        continue;
                    }
                }

                // Marchitamiento II + Lentitud I por 3 segundos
                living.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 1, false, false, false));
                living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0, false, false, false));

                // Daño menor periódico (1 corazón)
                living.damage(2.0, player);

                // Partículas en el afectado
                Location eLoc = living.getLocation().add(0, 1, 0);
                world.spawnParticle(Particle.DUST, eLoc, 3, 0.2, 0.3, 0.2, 0,
                        new Particle.DustOptions(Color.fromRGB(100, 10, 0), 0.8f));
            }
        }
    }

    // ==================== PACTO DEMONÍACO (4 piezas): Ráfaga al borde de la muerte ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLowHealth(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();

        // Ignorar si está en staff mode
        if (isInStaffMode(player)) return;
        if (isNewPlayer(player)) return;

        int pieces = KitDemon.getDemonPieceCount(player, plugin);
        if (pieces < 4) return;

        // No activar en zona protegida
        if (isProtectedZone(player)) return;

        // Disable in KOTH
        if (isInKothZone(player)) return;

        // Disable during end event
        if (KothCommand.isEndEventActive()) return;

        double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        double healthAfter = player.getHealth() - event.getFinalDamage();

        // Activar cuando baja de 20% HP
        if (healthAfter > 0 && healthAfter <= maxHealth * 0.20) {
            UUID uid = player.getUniqueId();

            // Verificar cooldown
            if (pactCooldowns.containsKey(uid)) {
                long remaining = PACT_COOLDOWN_MS - (System.currentTimeMillis() - pactCooldowns.get(uid));
                if (remaining > 0) return;
            }

            // Activar Pacto Demoníaco
            pactCooldowns.put(uid, System.currentTimeMillis());

            // Efectos de poder
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 2, false, false, true));     // Fuerza III - 10s
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 160, 2, false, false, true)); // Regen III - 8s
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 2, false, false, true));   // Resistencia III - 5s
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 1, false, false, true));         // Velocidad II - 6s
            player.setInvulnerable(true);
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) player.setInvulnerable(false);
            }, 40L); // 2s de invulnerabilidad

            // Efecto visual épico
            Location loc = player.getLocation();
            World world = loc.getWorld();

            // Explosión de llamas demoníacas
            world.spawnParticle(Particle.DUST, loc.clone().add(0, 1.5, 0), 40, 1.5, 1.5, 1.5, 0,
                    new Particle.DustOptions(Color.fromRGB(200, 0, 0), 2.5f));
            world.spawnParticle(Particle.FLAME, loc.clone().add(0, 1, 0), 30, 1, 1, 1, 0.08);
            world.spawnParticle(Particle.LAVA, loc.clone().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, 2, 0), 20, 0.8, 0.8, 0.8, 0.05);

            // Anillo de fuego en el suelo
            for (int i = 0; i < 20; i++) {
                double angle = (Math.PI * 2.0 / 20) * i;
                Location ring = loc.clone().add(Math.cos(angle) * 2.5, 0.1, Math.sin(angle) * 2.5);
                world.spawnParticle(Particle.FLAME, ring, 2, 0.05, 0.05, 0.05, 0.01);
                world.spawnParticle(Particle.DUST, ring, 1, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 60, 0), 1.5f));
            }

            // Sonidos
            world.playSound(loc, Sound.ENTITY_WITHER_AMBIENT, 0.8f, 0.3f);
            world.playSound(loc, Sound.ENTITY_BLAZE_AMBIENT, 1.0f, 0.5f);
            world.playSound(loc, Sound.ENTITY_GHAST_SCREAM, 0.4f, 0.3f);

            // Títulos y mensajes
            player.sendTitle( SmallCaps.convert("§4§l☠ PACTO DEMONÍACO ☠"), SmallCaps.convert("§c¡El demonio interior despierta!"), 5, 30, 10);
            player.sendMessage(SmallCaps.convert("§4§l☠ §cEl Pacto Demoníaco se ha activado. §7(60s cooldown)"));

            // Empujar a enemigos cercanos
            for (Entity nearby : player.getNearbyEntities(4, 3, 4)) {
                if (nearby instanceof LivingEntity && nearby != player) {
                    // No empujar a staff en staff mode
                    if (nearby instanceof Player && isInStaffMode((Player) nearby)) continue;
                    // No empujar a jugadores en zona protegida
                    if (nearby instanceof Player && isProtectedZone(nearby.getLocation())) continue;
                    // No empujar si el objetivo está en zona protegida
                    if (isProtectedZone(nearby.getLocation())) continue;
                    
                    // CRITICAL: No afectar mobs en protecciones de otros jugadores
                    ProtectionManager protMgr = plugin.getProtectionManager();
                    if (protMgr != null) {
                        ProtectedRegion region = protMgr.getRegionAt(nearby.getLocation());
                        if (region != null && !region.canAccess(player.getUniqueId())) {
                            continue;
                        }
                    }
                    
                    org.bukkit.util.Vector push = nearby.getLocation().toVector()
                            .subtract(loc.toVector()).normalize().multiply(1.2).setY(0.4);
                    nearby.setVelocity(push);
                }
            }
        }
    }

    // ==================== PARTÍCULAS AMBIENTALES ====================

    /**
     * Llamar cada 20 ticks (1 segundo) desde el plugin principal.
     * Partículas ambientales sutiles y quitar fuego.
     */
    public void tickAmbient() {
        // Disable during end event
        if (KothCommand.isEndEventActive()) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            // Ignorar jugadores en staff mode
            if (isInStaffMode(player)) continue;
            if (isNewPlayer(player)) continue;

            int pieces = KitDemon.getDemonPieceCount(player, plugin);
            if (pieces < 1) continue;

            // Disable all abilities in KOTH — remove effects actively to prevent flickering
            if (isInKothZone(player)) {
                player.removePotionEffect(PotionEffectType.SPEED);
                player.removePotionEffect(PotionEffectType.STRENGTH);
                continue;
            }

            // Quitar fuego siempre
            if (player.getFireTicks() > 0) {
                player.setFireTicks(0);
            }

            // Partículas sutiles según piezas
            Location loc = player.getLocation().add(0, 0.5, 0);
            World world = loc.getWorld();

            if (Math.random() < 0.2) {
                world.spawnParticle(Particle.SMALL_FLAME,
                        loc.clone().add((Math.random() - 0.5) * 0.6, Math.random() * 1.5, (Math.random() - 0.5) * 0.6),
                        1, 0, 0.02, 0, 0.005);
            }

            if (pieces >= 2 && Math.random() < 0.15) {
                world.spawnParticle(Particle.DUST,
                        loc.clone().add((Math.random() - 0.5) * 0.4, Math.random() * 1.8, (Math.random() - 0.5) * 0.4),
                        1, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(150, 0, 0), 0.6f));
            }

            if (pieces >= 4 && Math.random() < 0.1) {
                world.spawnParticle(Particle.SOUL_FIRE_FLAME,
                        loc.clone().add((Math.random() - 0.5) * 0.5, Math.random() * 1.5, (Math.random() - 0.5) * 0.5),
                        1, 0, 0.01, 0, 0.003);
            }

            // Pasivas específicas de piezas
            ItemStack chest = player.getInventory().getChestplate();
            if (chest != null && KitDemon.isDemonPiece(chest, plugin)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 1, true, false, false)); // Fuerz II
            }
            
            ItemStack boots = player.getInventory().getBoots();
            if (boots != null && KitDemon.isDemonPiece(boots, plugin)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, true, false, false)); // Vel II
            }
        }
    }

    // ==================== FURIA DEMONÍACA (Habilidad Activa Espada) ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwordAbility(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (!player.isSneaking()) return;

        // Ignorar si está en staff mode
        if (isInStaffMode(player)) return;
        if (isNewPlayer(player)) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.NETHERITE_SWORD || !KitDemon.isDemonPiece(item, plugin)) return;

        // Verificar si está en zona protegida
        if (isProtectedZone(player)) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes usar habilidades del Kit Demon en zona protegida."));
            event.setCancelled(true);
            return;
        }

        // Disable in KOTH
        if (isInKothZone(player)) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes usar habilidades del Kit Demon en KOTH."));
            event.setCancelled(true);
            return;
        }

        // Disable during end event
        if (KothCommand.isEndEventActive()) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes usar habilidades del Kit Demon durante el evento de fin."));
            event.setCancelled(true);
            return;
        }

        UUID uid = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (furiaCooldowns.containsKey(uid) && now < furiaCooldowns.get(uid)) {
            long left = (furiaCooldowns.get(uid) - now) / 1000;
            com.moonlight.coreprotect.util.ActionBarUtil.send(player, "§c§l✖ §cEnfriamiento de Furia Demoníaca: §e" + left + "s");
            return;
        }

        furiaCooldowns.put(uid, now + FURIA_CD_MS);
        
        // Efecto visual y de sonido inicial
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.2f);
        player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.5f);

        // Dash hacia donde mira
        org.bukkit.util.Vector dash = player.getLocation().getDirection().normalize().multiply(1.5).setY(0.4);
        player.setVelocity(dash);

        player.sendMessage(SmallCaps.convert("§4§l☠ §c¡Has desatado la Furia Demoníaca!"));

        // Tarea repetitiva durante el dash (aprox. medio segundo)
        new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ > 10 || player.isDead() || !player.isOnline()) {
                    // Cierre del dash: explosión de área
                    Location endLoc = player.getLocation();
                    endLoc.getWorld().spawnParticle(Particle.FLAME, endLoc, 50, 2, 1, 2, 0.1);
                    endLoc.getWorld().spawnParticle(Particle.LAVA, endLoc, 20, 1, 1, 1, 0);
                    endLoc.getWorld().playSound(endLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.5f);
                    
                    // Daño y fuego en área final
                    for (Entity e : player.getNearbyEntities(3, 2, 3)) {
                        if (e instanceof LivingEntity && e != player) {
                            LivingEntity le = (LivingEntity) e;
                            // No dañar a staff en staff mode
                            if (e instanceof Player && isInStaffMode((Player) e)) continue;
                            // No dañar a jugadores en zona protegida
                            if (e instanceof Player && isProtectedZone(e.getLocation())) continue;
                            // No dañar si el objetivo está en zona protegida
                            if (isProtectedZone(e.getLocation())) continue;
                            
                            // CRITICAL: No afectar mobs en protecciones de otros jugadores
                            ProtectionManager protMgr = plugin.getProtectionManager();
                            if (protMgr != null) {
                                ProtectedRegion region = protMgr.getRegionAt(e.getLocation());
                                if (region != null && !region.canAccess(player.getUniqueId())) {
                                    continue;
                                }
                            }
                            
                            le.damage(8.0, player);
                            le.setFireTicks(100); // 5 segundos quemándose
                        }
                    }
                    cancel();
                    return;
                }
                
                Location loc = player.getLocation().add(0, 1, 0);
                loc.getWorld().spawnParticle(Particle.SMALL_FLAME, loc, 10, 0.5, 0.5, 0.5, 0.05);
                loc.getWorld().spawnParticle(Particle.DUST, loc, 5, 0.4, 0.4, 0.4, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 50, 0), 1.5f));
                
                for (Entity e : player.getNearbyEntities(1.5, 1.5, 1.5)) {
                    if (e instanceof LivingEntity && e != player) {
                        // No dañar a staff en staff mode
                        if (e instanceof Player && isInStaffMode((Player) e)) continue;
                        // No dañar a jugadores en zona protegida
                        if (e instanceof Player && isProtectedZone(e.getLocation())) continue;
                        // No dañar si el objetivo está en zona protegida
                        if (isProtectedZone(e.getLocation())) continue;
                        
                        // CRITICAL: No afectar mobs en protecciones de otros jugadores
                        ProtectionManager protMgr = plugin.getProtectionManager();
                        if (protMgr != null) {
                            ProtectedRegion region = protMgr.getRegionAt(e.getLocation());
                            if (region != null && !region.canAccess(player.getUniqueId())) {
                                continue;
                            }
                        }
                        
                        ((LivingEntity) e).damage(4.0, player);
                        e.setFireTicks(60);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

}
