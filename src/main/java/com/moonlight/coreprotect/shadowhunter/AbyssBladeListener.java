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
 * Listener para las habilidades de la Devoradora de Almas.
 */
public class AbyssBladeListener implements Listener {

    private final CoreProtectPlugin plugin;

    private final Map<UUID, Long> tearCooldown = new HashMap<>();
    private final Map<UUID, Long> roarCooldown = new HashMap<>();

    private static final long TEAR_CD = 10000; // 10 segundos
    private static final long ROAR_CD = 25000; // 25 segundos

    public AbyssBladeListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    // ==================== CLICK DERECHO: HABILIDADES ACTIVAS ====================

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!AbyssBlade.isAbyssBlade(item, plugin))
            return;

        // Bloquear habilidades de hacha en KOTH (excepto VALE_TODO) y Boss
        com.moonlight.coreprotect.bossarena.BossArenaManager bossManager = plugin.getBossArenaManager();
        boolean inBoss = bossManager != null && bossManager.isInAnyArena(player.getLocation());
        if (inBoss) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes usar habilidades de esta hacha aquí."));
            return;
        }
        if (player.getWorld().getName().equals("koth")) {
            com.moonlight.coreprotect.koth.KothManager km = plugin.getKothManager();
            if (km == null || !km.isActive() || km.getCurrentMode() != com.moonlight.coreprotect.koth.KothMode.VALE_TODO) {
                player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes usar habilidades de esta hacha en el mundo KOTH."));
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
        com.moonlight.coreprotect.core.ProtectedRegion abRegion = plugin.getProtectionManager().getRegionAt(player.getLocation());
        if (abRegion != null && !abRegion.canAccess(player.getUniqueId())) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes usar habilidades en la zona protegida de otro jugador."));
            return;
        }
        if (plugin.getAfkZoneManager() != null && plugin.getAfkZoneManager().isInZone(player.getLocation())) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes usar habilidades en la zona AFK."));
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Material type = event.getClickedBlock().getType();
            if (type.isInteractable() && !player.isSneaking()) {
                return;
            }
            event.setCancelled(true);
        }

        if (player.isSneaking()) {
            useVoidRoar(player);
        } else {
            useAbyssTear(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (AbyssBlade.isAbyssBlade(item, plugin)) {
            // Dejar que ocurra la interacción (tradeo, etc.)
        }
    }

    // ==================== ANCLA DE ALMAS (CLICK DERECHO) ====================

    private void useAbyssTear(Player player) { // Mantengo el nombre del método llamado arriba
        UUID uid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastUse = tearCooldown.get(uid);
        if (lastUse != null) {
            long remaining = TEAR_CD - (now - lastUse);
            if (remaining > 0) {
                player.sendTitle("",
                        "§c§lAncla de Almas §7- Cooldown: §e" + String.format("%.1f", remaining / 1000.0) + "s", 0, 20,
                        5);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
                return;
            }
        }
        tearCooldown.put(uid, now);

        player.sendTitle("", SmallCaps.convert("§4§l⛓ Ancla de Almas"), 0, 20, 5);
        player.playSound(player.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1.0f, 0.5f);
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 1.0f, 0.8f);

        Location start = player.getEyeLocation();
        Vector dir = start.getDirection().normalize();
        World world = player.getWorld();

        new BukkitRunnable() {
            double distance = 0;
            Location current = start.clone();

            @Override
            public void run() {
                if (distance > 25 || !player.isOnline()) {
                    cancel();
                    return;
                }

                current.add(dir.clone().multiply(1.5));
                distance += 1.5;

                // Partículas del gancho
                world.spawnParticle(Particle.DUST, current, 5, 0.1, 0.1, 0.1, 0,
                        new Particle.DustOptions(Color.fromRGB(150, 0, 0), 1.5f));
                world.spawnParticle(Particle.SOUL, current, 2, 0.1, 0.1, 0.1, 0.05);

                for (Entity e : world.getNearbyEntities(current, 1.5, 1.5, 1.5)) {
                    if (e instanceof LivingEntity && !e.equals(player)) {
                        LivingEntity target = (LivingEntity) e;
                        if (target instanceof org.bukkit.entity.Villager
                                || target instanceof org.bukkit.entity.Endermite
                                || target instanceof org.bukkit.entity.ArmorStand)
                            continue;

                        cancel();
                        world.playSound(target.getLocation(), Sound.ENTITY_IRON_GOLEM_REPAIR, 1.0f, 0.5f);
                        world.spawnParticle(Particle.SOUL_FIRE_FLAME, target.getLocation().add(0, 1, 0), 30, 0.5, 0.5,
                                0.5, 0.1);

                        // Atraer fuertemente al jugador
                        Vector pull = player.getLocation().toVector().subtract(target.getLocation().toVector())
                                .normalize().multiply(2.5);
                        pull.setY(0.5);
                        target.setVelocity(pull);

                        target.setNoDamageTicks(0);
                        target.damage(22, player);
                        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 4));

                        // Curar al jugador
                        double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
                        player.setHealth(Math.min(player.getHealth() + 8.0, maxHealth));
                        player.playSound(player.getLocation(), Sound.ENTITY_WITCH_DRINK, 0.5f, 1.5f);
                        return;
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    // ==================== SINGULARIDAD ABISMAL (SHIFT + CLICK DERECHO)
    // ====================

    private void useVoidRoar(Player player) { // Mantengo el nombre
        UUID uid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastUse = roarCooldown.get(uid);
        if (lastUse != null) {
            long remaining = ROAR_CD - (now - lastUse);
            if (remaining > 0) {
                player.sendTitle("",
                        "§4§lSingularidad §7- Cooldown: §e" + String.format("%.1f", remaining / 1000.0) + "s", 0, 20,
                        5);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
                return;
            }
        }
        roarCooldown.put(uid, now);

        org.bukkit.block.Block targetBlock = player.getTargetBlockExact(10);
        Location center = (targetBlock != null) ? targetBlock.getLocation().add(0.5, 1.5, 0.5)
                : player.getLocation().add(player.getLocation().getDirection().multiply(8)).add(0, 1, 0);
        World world = center.getWorld();

        player.sendTitle("", SmallCaps.convert("§0§l🌌 Singularidad Abismal"), 0, 20, 5);
        world.playSound(center, Sound.BLOCK_PORTAL_TRIGGER, 1.0f, 2.0f);
        world.playSound(center, Sound.ENTITY_WARDEN_EMERGE, 1.0f, 0.5f);

        // Agujero negro que succiona por 4 segundos
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 80) { // 4 segundos
                    cancel();
                    // Explosión final masiva
                    world.spawnParticle(Particle.EXPLOSION, center, 3);
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, center, 100, 3, 3, 3, 0.2);
                    world.spawnParticle(Particle.DUST, center, 150, 4, 4, 4, 0,
                            new Particle.DustOptions(Color.fromRGB(50, 0, 100), 3.0f));
                    world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 0.8f);
                    world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.5f);

                    for (Entity e : world.getNearbyEntities(center, 9, 9, 9)) {
                        if (e instanceof LivingEntity && !e.equals(player)) {
                            LivingEntity t = (LivingEntity) e;
                            if (t instanceof org.bukkit.entity.Villager || t instanceof org.bukkit.entity.Endermite
                                    || t instanceof org.bukkit.entity.ArmorStand)
                                continue;
                            t.setNoDamageTicks(0);
                            t.damage(28, player);
                            t.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                            Vector kb = t.getLocation().toVector().subtract(center.toVector()).normalize().multiply(1.5)
                                    .setY(0.6);
                            t.setVelocity(kb);
                        }
                    }
                    return;
                }

                // Efecto visual de agujero negro atrayendo luz
                double r1 = 6.0 - (ticks % 30) * 0.2;
                for (int i = 0; i < 8; i++) {
                    double angle = Math.random() * Math.PI * 2;
                    Location pLoc = center.clone().add(Math.cos(angle) * r1, (Math.random() - 0.5) * 3,
                            Math.sin(angle) * r1);
                    Vector v = center.toVector().subtract(pLoc.toVector()).normalize().multiply(0.2);
                    world.spawnParticle(Particle.DUST, pLoc, 0, v.getX(), v.getY(), v.getZ(), 1,
                            new Particle.DustOptions(Color.fromRGB(10, 0, 30), 1.5f));
                    world.spawnParticle(Particle.SOUL, pLoc, 0, v.getX() * 2, v.getY() * 2, v.getZ() * 2, 1);
                }

                // Centro denso
                world.spawnParticle(Particle.DUST, center, 10, 0.5, 0.5, 0.5, 0,
                        new Particle.DustOptions(Color.fromRGB(0, 0, 0), 3.0f));

                // Succión constante cada tick (radio 9 bloques)
                for (Entity e : world.getNearbyEntities(center, 9, 9, 9)) {
                    if (e instanceof LivingEntity && !e.equals(player)) {
                        LivingEntity t = (LivingEntity) e;
                        if (t instanceof org.bukkit.entity.Villager || t instanceof org.bukkit.entity.Endermite
                                || t instanceof org.bukkit.entity.ArmorStand)
                            continue;

                        Vector suck = center.toVector().subtract(t.getLocation().toVector()).normalize().multiply(0.35); // Succión
                                                                                                                         // OP
                        t.setVelocity(t.getVelocity().add(suck));

                        if (ticks % 10 == 0) { // Menos spam de daño, cada 0.5s hace daño wither directo
                            t.damage(3, player);
                            world.playSound(t.getLocation(), Sound.ENTITY_WITHER_HURT, 0.3f, 1.5f);
                        }
                    }
                }

                if (ticks % 20 == 0)
                    world.playSound(center, Sound.BLOCK_BEACON_AMBIENT, 2.0f, 0.5f);
                ticks++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    // ==================== ALMA HAMBRIENTA (PASIVA) ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null)
            return;
        Player killer = event.getEntity().getKiller();
        ItemStack weapon = killer.getInventory().getItemInMainHand();

        if (!AbyssBlade.isAbyssBlade(weapon, plugin))
            return;

        // Buffs muy OP de tanque
        killer.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 1)); // Resistencia II 10s
        killer.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 1)); // Absorción II 10s

        // Reducción de cooldown en ambas habilidades por kill (3 segundos)
        UUID uid = killer.getUniqueId();
        if (tearCooldown.containsKey(uid))
            tearCooldown.put(uid, tearCooldown.get(uid) - 3000);
        if (roarCooldown.containsKey(uid))
            roarCooldown.put(uid, roarCooldown.get(uid) - 3000);

        // Efecto visual Nova Escudo
        Location loc = killer.getLocation().add(0, 1, 0);
        World world = loc.getWorld();
        for (double a = 0; a < Math.PI * 2; a += Math.PI / 8) {
            world.spawnParticle(Particle.DUST, loc.clone().add(Math.cos(a) * 1.5, 0, Math.sin(a) * 1.5), 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(150, 0, 50), 2.0f));
        }

        world.playSound(loc, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.8f, 1.2f);
        killer.sendTitle("", SmallCaps.convert("§4§l☠ Bastión Abismal §7→ §aResist+Absorb"), 0, 20, 10);
    }

    // ==================== PARTÍCULAS AMBIENTALES ====================

    public void tickAmbientParticles() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (AbyssBlade.isAbyssBlade(item, plugin)) {
                Location loc = player.getLocation().add(0, 1.2, 0);

                // Partículas rojas/moradas más intensas
                loc.getWorld().spawnParticle(Particle.DUST,
                        loc.clone().add((Math.random() - 0.5) * 0.8, (Math.random() - 0.5) * 0.5,
                                (Math.random() - 0.5) * 0.8),
                        1, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(180, 0, 50), 0.7f));

                if (Math.random() < 0.15) {
                    loc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 1, 0.3, 0.3, 0.3, 0.01);
                }
                if (Math.random() < 0.08) {
                    loc.getWorld().spawnParticle(Particle.SOUL, loc, 1, 0.2, 0.2, 0.2, 0.02);
                }
            }
        }
    }
}
