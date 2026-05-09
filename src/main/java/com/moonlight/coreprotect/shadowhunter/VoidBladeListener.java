package com.moonlight.coreprotect.shadowhunter;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Listener para las habilidades de la Hoja del Vacío.
 */
public class VoidBladeListener implements Listener {

    private final CoreProtectPlugin plugin;

    // Cooldowns (millis)
    private final Map<UUID, Long> shadowStrikeCooldown = new HashMap<>();
    private final Map<UUID, Long> voidFuryCooldown = new HashMap<>();

    private static final long SHADOW_STRIKE_CD = 8000; // 8 segundos
    private static final long VOID_FURY_CD = 20000; // 20 segundos

    public VoidBladeListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    // ==================== CLICK DERECHO: HABILIDADES ACTIVAS ====================

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!VoidBlade.isVoidBlade(item, plugin))
            return;

        // Bloquear habilidades de espada en KOTH (excepto VALE_TODO) y Boss
        com.moonlight.coreprotect.bossarena.BossArenaManager bossManager = plugin.getBossArenaManager();
        boolean inBoss = bossManager != null && bossManager.isInAnyArena(player.getLocation());
        if (inBoss) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes usar habilidades de esta espada aquí."));
            return;
        }
        if (player.getWorld().getName().equals("koth")) {
            com.moonlight.coreprotect.koth.KothManager km = plugin.getKothManager();
            if (km == null || !km.isActive() || km.getCurrentMode() != com.moonlight.coreprotect.koth.KothMode.VALE_TODO) {
                player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes usar habilidades de esta espada en el mundo KOTH."));
                return;
            }
        }
        
        // Bloquear habilidades en spawn, zona AFK y cualquier zona protegida (excepto en combate PvP)
        if (plugin.getProtectionManager().isSpawnCore(player.getLocation())) {
            if (!plugin.getCombatTagManager().isInCombat(player.getUniqueId())) {
                player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes usar habilidades en la zona protegida del spawn."));
                return;
            }
        }
        com.moonlight.coreprotect.core.ProtectedRegion vbRegion = plugin.getProtectionManager().getRegionAt(player.getLocation());
        if (vbRegion != null && !vbRegion.canAccess(player.getUniqueId())) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes usar habilidades en la zona protegida de otro jugador."));
            return;
        }
        if (plugin.getAfkZoneManager() != null && plugin.getAfkZoneManager().isInZone(player.getLocation())) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes usar habilidades en la zona AFK."));
            return;
        }

        // Prevenir colocar bloques/interactuar si es un bloque interactivo
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Material type = event.getClickedBlock().getType();
            if (type.isInteractable() && !player.isSneaking()) {
                return;
            }
            event.setCancelled(true);
        }

        if (player.isSneaking()) {
            // Shift + Click derecho: Furia del Vacío
            useVoidFury(player);
        } else {
            // Click derecho: Golpe Sombrío
            useShadowStrike(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (VoidBlade.isVoidBlade(item, plugin)) {
            // Simplemente dejar que ocurra la interacción sin disparar habilidades
            // No cancelamos el evento para permitir tradeos/interacciones
        }
    }

    // ==================== GOLPE SOMBRÍO ====================

    private void useShadowStrike(Player player) {
        UUID uid = player.getUniqueId();

        // Cooldown check
        long now = System.currentTimeMillis();
        Long lastUse = shadowStrikeCooldown.get(uid);
        if (lastUse != null) {
            long remaining = SHADOW_STRIKE_CD - (now - lastUse);
            if (remaining > 0) {
                player.sendTitle("",
                        "§b§lGolpe Sombrío §7- Cooldown: §e" + String.format("%.1f", remaining / 1000.0) + "s", 0, 20,
                        5);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
                return;
            }
        }

        shadowStrikeCooldown.put(uid, now);

        // Efecto de lanzamiento
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_SHOOT, 0.8f, 1.5f);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.2f);
        player.sendTitle("", SmallCaps.convert("§b§l⚡ Golpe Sombrío"), 0, 20, 5);

        // Proyectil de sombra (raycast manual)
        Vector direction = player.getEyeLocation().getDirection().normalize();
        Location start = player.getEyeLocation().clone();

        new BukkitRunnable() {
            Location current = start.clone();
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 40) { // Max 20 bloques de distancia
                    cancel();
                    // Impacto en el aire
                    impactEffect(current, null, player);
                    return;
                }

                // Mover el proyectil
                current.add(direction.clone().multiply(0.5));
                World world = current.getWorld();

                // Partículas del proyectil
                world.spawnParticle(Particle.DUST, current, 5, 0.15, 0.15, 0.15, 0,
                        new Particle.DustOptions(Color.fromRGB(50, 0, 80), 1.8f));
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, current, 2, 0.1, 0.1, 0.1, 0.01);
                world.spawnParticle(Particle.DUST, current, 3, 0.1, 0.1, 0.1, 0,
                        new Particle.DustOptions(Color.fromRGB(100, 0, 150), 1.2f));

                // Sonido trail
                if (ticks % 4 == 0) {
                    world.playSound(current, Sound.ENTITY_PHANTOM_FLAP, 0.3f, 1.5f);
                }

                // Check colisión con bloque sólido
                if (current.getBlock().getType().isSolid()) {
                    cancel();
                    impactEffect(current, null, player);
                    return;
                }

                for (Entity entity : world.getNearbyEntities(current, 1.5, 1.5, 1.5)) {
                    if (entity instanceof LivingEntity && !entity.equals(player)) {
                        LivingEntity target = (LivingEntity) entity;
                        if (target instanceof org.bukkit.entity.Villager
                                || target instanceof org.bukkit.entity.Endermite
                                || target instanceof org.bukkit.entity.ArmorStand)
                            continue;
                        cancel();

                        // Daño - resetear invulnerabilidad para que el daño se aplique
                        target.setNoDamageTicks(0);
                        target.damage(15, player);
                        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0)); // 3 segundos

                        impactEffect(current, target, player);
                        return;
                    }
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private void impactEffect(Location loc, LivingEntity hit, Player player) {
        World world = loc.getWorld();

        // Explosión de sombras
        world.spawnParticle(Particle.SMOKE, loc, 30, 0.5, 0.5, 0.5, 0.1);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 20, 0.5, 0.5, 0.5, 0.05);
        world.spawnParticle(Particle.DUST, loc, 15, 0.8, 0.8, 0.8, 0,
                new Particle.DustOptions(Color.fromRGB(80, 0, 120), 2.0f));
        world.playSound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.5f, 1.5f);

        if (hit != null) {
            player.sendTitle("",
                    "§b§l⚡ §fGolpe Sombrío §7→ §c" + (hit instanceof Player ? ((Player) hit).getName() : hit.getName()),
                    0, 30, 10);
        }
    }

    // ==================== FURIA DEL VACÍO ====================

    private void useVoidFury(Player player) {
        UUID uid = player.getUniqueId();

        // Cooldown check
        long now = System.currentTimeMillis();
        Long lastUse = voidFuryCooldown.get(uid);
        if (lastUse != null) {
            long remaining = VOID_FURY_CD - (now - lastUse);
            if (remaining > 0) {
                player.sendTitle("",
                        "§c§lFuria del Vacío §7- Cooldown: §e" + String.format("%.1f", remaining / 1000.0) + "s", 0, 20,
                        5);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
                return;
            }
        }

        voidFuryCooldown.put(uid, now);

        Location center = player.getLocation();
        World world = center.getWorld();

        player.sendTitle("", SmallCaps.convert("§c§l🔥 Furia del Vacío"), 0, 20, 5);
        player.playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.8f);

        // Animación de carga rápida
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 15) {
                    cancel();
                    releaseVoidFury(player);
                    return;
                }

                // Partículas de absorción
                double radius = 5.0 - (ticks * 0.3);
                for (int i = 0; i < 8; i++) {
                    double a = (Math.PI * 2 / 8) * i + ticks * 0.4;
                    world.spawnParticle(Particle.DUST,
                            center.clone().add(Math.cos(a) * radius, 0.8, Math.sin(a) * radius),
                            2, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(200, 0, 50), 1.5f));
                }

                if (ticks % 3 == 0) {
                    world.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.5f, 0.5f + ticks * 0.1f);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private void releaseVoidFury(Player player) {
        Location center = player.getLocation();
        World world = center.getWorld();

        // Onda expansiva visual
        new BukkitRunnable() {
            double radius = 0;

            @Override
            public void run() {
                if (radius > 6) {
                    cancel();
                    return;
                }

                for (int i = 0; i < 20; i++) {
                    double a = (Math.PI * 2 / 20) * i;
                    Location pLoc = center.clone().add(Math.cos(a) * radius, 0.5, Math.sin(a) * radius);
                    world.spawnParticle(Particle.DUST, pLoc, 2, 0, 0.2, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(180, 0, 40), 2.0f));
                    world.spawnParticle(Particle.SMOKE, pLoc, 1, 0.1, 0.1, 0.1, 0.01);
                }

                radius += 0.8;
            }
        }.runTaskTimer(plugin, 0, 1);

        // Sonido
        world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 1.2f);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.5f);

        // Daño AOE - usar posición actual del jugador
        Location damageCenter = player.getLocation();
        for (Entity entity : world.getNearbyEntities(damageCenter, 5, 5, 5)) {
            if (entity instanceof LivingEntity && !entity.equals(player)) {
                LivingEntity target = (LivingEntity) entity;
                if (target instanceof org.bukkit.entity.Villager || target instanceof org.bukkit.entity.Endermite
                        || target instanceof org.bukkit.entity.ArmorStand)
                    continue;
                // Resetear invulnerabilidad para que el daño se aplique
                target.setNoDamageTicks(0);
                target.damage(8, player);
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2)); // Slowness III 3s

                // Knockback
                Vector kb = target.getLocation().toVector().subtract(center.toVector()).normalize().multiply(1.2);
                kb.setY(0.5);
                target.setVelocity(kb);
            }
        }

        // Partículas de impacto central
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, center.clone().add(0, 1, 0), 40, 2, 1, 2, 0.1);
    }

    // ==================== SED DE SOMBRAS (PASIVA) ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null)
            return;

        Player killer = event.getEntity().getKiller();
        ItemStack weapon = killer.getInventory().getItemInMainHand();

        if (!VoidBlade.isVoidBlade(weapon, plugin))
            return;

        // Curar 4 HP (2 corazones)
        double maxHealth = killer.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        killer.setHealth(Math.min(killer.getHealth() + 4, maxHealth));

        // Speed I por 3 segundos
        killer.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 0));

        // Efecto visual
        Location loc = killer.getLocation();
        World world = loc.getWorld();
        world.spawnParticle(Particle.SOUL, loc.clone().add(0, 1, 0), 15, 0.5, 0.8, 0.5, 0.05);
        world.spawnParticle(Particle.DUST, loc.clone().add(0, 1.5, 0), 8, 0.4, 0.4, 0.4, 0,
                new Particle.DustOptions(Color.fromRGB(150, 0, 255), 1.2f));

        // Sonido de absorción
        world.playSound(loc, Sound.ENTITY_WITCH_DRINK, 0.5f, 1.5f);
        world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.3f, 1.0f);

        killer.sendTitle("", SmallCaps.convert("§5§l☠ Sed de Sombras §7→ §a+2❤ §7+ §bSpeed I"), 0, 30, 10);
    }

    // ==================== PARTÍCULAS AMBIENTALES ====================

    /**
     * Llamar en un timer global para mostrar partículas cuando el jugador tiene el
     * item en mano.
     */
    public void tickAmbientParticles() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (VoidBlade.isVoidBlade(item, plugin)) {
                Location loc = player.getLocation().add(0, 1.2, 0);

                // Partículas sutiles moradas
                loc.getWorld().spawnParticle(Particle.DUST,
                        loc.clone().add((Math.random() - 0.5) * 0.8, (Math.random() - 0.5) * 0.5,
                                (Math.random() - 0.5) * 0.8),
                        1, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(100, 0, 150), 0.6f));

                if (Math.random() < 0.1) {
                    loc.getWorld().spawnParticle(Particle.ENCHANT, loc, 2, 0.3, 0.3, 0.3, 0.5);
                }
            }
        }
    }
}
