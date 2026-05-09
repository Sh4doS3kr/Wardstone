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

/**
 * Listener para las habilidades de la Estrella del Origen (Agujero Blanco).
 */
public class WhiteHoleBladeListener implements Listener {

    private final CoreProtectPlugin plugin;

    private final Map<UUID, Long> novaCooldown = new HashMap<>();
    private final Map<UUID, Long> singularityCooldown = new HashMap<>();

    private static final long NOVA_CD = 10000; // 10 segundos
    private static final long SINGULARITY_CD = 25000; // 25 segundos

    public WhiteHoleBladeListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    // ==================== CLICK DERECHO: HABILIDADES ACTIVAS ====================

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!WhiteHoleBlade.isWhiteHoleBlade(item, plugin))
            return;

        // Bloquear habilidades en KOTH (excepto VALE_TODO) y Boss
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

        // Bloquear en spawn, AFK y zonas protegidas ajenas
        if (plugin.getProtectionManager().isSpawnCore(player.getLocation())) {
            if (!plugin.getCombatTagManager().isInCombat(player.getUniqueId())) {
                player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes usar habilidades en la zona protegida del spawn."));
                return;
            }
        }
        com.moonlight.coreprotect.core.ProtectedRegion region = plugin.getProtectionManager().getRegionAt(player.getLocation());
        if (region != null && !region.canAccess(player.getUniqueId())) {
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
            useWhiteSingularity(player);
        } else {
            useRadiantNova(player);
        }
    }

    // ==================== NOVA RADIANTE (CLICK DERECHO) ====================

    private void useRadiantNova(Player player) {
        UUID uid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastUse = novaCooldown.get(uid);
        if (lastUse != null) {
            long remaining = NOVA_CD - (now - lastUse);
            if (remaining > 0) {
                player.sendTitle("",
                        "§e§lNova Radiante §7- Cooldown: §e" + String.format("%.1f", remaining / 1000.0) + "s", 0, 20, 5);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
                return;
            }
        }
        novaCooldown.put(uid, now);

        player.sendTitle("", SmallCaps.convert("§e§l☀ Nova Radiante"), 0, 20, 5);
        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.5f);
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 1.0f, 1.5f);

        Location start = player.getEyeLocation();
        Vector dir = start.getDirection().normalize();
        World world = player.getWorld();

        new BukkitRunnable() {
            double distance = 0;
            Location current = start.clone();

            @Override
            public void run() {
                if (distance > 25 || !player.isOnline()) {
                    // Explota al final del rango
                    explodeNova(current, world, player);
                    cancel();
                    return;
                }

                current.add(dir.clone().multiply(1.5));
                distance += 1.5;

                // Partículas del proyectil — luz blanca/dorada
                world.spawnParticle(Particle.DUST, current, 5, 0.1, 0.1, 0.1, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 255, 200), 1.5f));
                world.spawnParticle(Particle.END_ROD, current, 2, 0.1, 0.1, 0.1, 0.02);

                // Colisión con entidades
                for (Entity e : world.getNearbyEntities(current, 1.5, 1.5, 1.5)) {
                    if (e instanceof LivingEntity && !e.equals(player)) {
                        LivingEntity target = (LivingEntity) e;
                        if (target instanceof org.bukkit.entity.Villager
                                || target instanceof org.bukkit.entity.ArmorStand)
                            continue;

                        cancel();
                        explodeNova(current, world, player);
                        return;
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private void explodeNova(Location center, World world, Player player) {
        // Explosión de luz
        world.spawnParticle(Particle.FLASH, center, 2, 0, 0, 0, 0, Color.fromRGB(255, 255, 255));
        world.spawnParticle(Particle.END_ROD, center, 80, 3, 3, 3, 0.3);
        world.spawnParticle(Particle.DUST, center, 60, 3, 3, 3, 0,
                new Particle.DustOptions(Color.fromRGB(255, 255, 255), 2.5f));
        world.spawnParticle(Particle.DUST, center, 40, 2, 2, 2, 0,
                new Particle.DustOptions(Color.fromRGB(255, 215, 0), 2.0f));
        world.playSound(center, Sound.ENTITY_FIREWORK_ROCKET_BLAST_FAR, 1.5f, 1.0f);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.5f);

        for (Entity e : world.getNearbyEntities(center, 6, 6, 6)) {
            if (e instanceof LivingEntity && !e.equals(player)) {
                LivingEntity t = (LivingEntity) e;
                if (t instanceof org.bukkit.entity.Villager || t instanceof org.bukkit.entity.ArmorStand)
                    continue;

                t.setNoDamageTicks(0);
                t.damage(22, player);
                t.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));

                // Empujar fuertemente LEJOS del centro (opuesto al agujero negro)
                Vector push = t.getLocation().toVector().subtract(center.toVector()).normalize().multiply(2.5);
                push.setY(0.6);
                t.setVelocity(push);
            }
        }

        // Curar al lanzador
        double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
        player.setHealth(Math.min(player.getHealth() + 8.0, maxHealth));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 2.0f);
    }

    // ==================== SINGULARIDAD BLANCA (SHIFT + CLICK DERECHO) ====================

    private void useWhiteSingularity(Player player) {
        UUID uid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastUse = singularityCooldown.get(uid);
        if (lastUse != null) {
            long remaining = SINGULARITY_CD - (now - lastUse);
            if (remaining > 0) {
                player.sendTitle("",
                        "§6§lSingularidad §7- Cooldown: §e" + String.format("%.1f", remaining / 1000.0) + "s", 0, 20, 5);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
                return;
            }
        }
        singularityCooldown.put(uid, now);

        org.bukkit.block.Block targetBlock = player.getTargetBlockExact(10);
        Location center = (targetBlock != null) ? targetBlock.getLocation().add(0.5, 1.5, 0.5)
                : player.getLocation().add(player.getLocation().getDirection().multiply(8)).add(0, 1, 0);
        World world = center.getWorld();

        player.sendTitle("", SmallCaps.convert("§6§l🌟 Singularidad Blanca"), 0, 20, 5);
        world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 2.0f);
        world.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 1.5f);

        // Zona de luz que repele durante 4 segundos
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 80) { // 4 segundos
                    cancel();
                    // Explosión final de luz
                    world.spawnParticle(Particle.FLASH, center, 3, 0, 0, 0, 0);
                    world.spawnParticle(Particle.END_ROD, center, 150, 4, 4, 4, 0.3);
                    world.spawnParticle(Particle.DUST, center, 200, 5, 5, 5, 0,
                            new Particle.DustOptions(Color.fromRGB(255, 255, 255), 3.0f));
                    world.playSound(center, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 2.0f, 1.0f);
                    world.playSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 1.5f, 1.5f);

                    // Daño final a enemigos + curación final a aliados
                    for (Entity e : world.getNearbyEntities(center, 9, 9, 9)) {
                        if (e instanceof LivingEntity && !e.equals(player)) {
                            LivingEntity t = (LivingEntity) e;
                            if (t instanceof org.bukkit.entity.Villager || t instanceof org.bukkit.entity.ArmorStand)
                                continue;

                            t.setNoDamageTicks(0);
                            t.damage(20, player);
                            t.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0));
                            Vector kb = t.getLocation().toVector().subtract(center.toVector()).normalize().multiply(2.0)
                                    .setY(0.8);
                            t.setVelocity(kb);
                        }
                    }
                    return;
                }

                // Efecto visual de agujero blanco — anillo de luz expansivo
                double r1 = (ticks % 30) * 0.2;
                for (int i = 0; i < 8; i++) {
                    double angle = Math.random() * Math.PI * 2;
                    Location pLoc = center.clone().add(Math.cos(angle) * r1, (Math.random() - 0.5) * 3,
                            Math.sin(angle) * r1);
                    Vector v = pLoc.toVector().subtract(center.toVector()).normalize().multiply(0.15);
                    world.spawnParticle(Particle.DUST, pLoc, 0, v.getX(), v.getY(), v.getZ(), 1,
                            new Particle.DustOptions(Color.fromRGB(255, 255, 230), 1.5f));
                    world.spawnParticle(Particle.END_ROD, pLoc, 0, v.getX() * 2, v.getY() * 2, v.getZ() * 2, 1);
                }

                // Centro brillante
                world.spawnParticle(Particle.DUST, center, 10, 0.5, 0.5, 0.5, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 255, 255), 3.0f));

                // Repulsión constante (opuesto a succión del agujero negro)
                for (Entity e : world.getNearbyEntities(center, 9, 9, 9)) {
                    if (e instanceof LivingEntity && !e.equals(player)) {
                        LivingEntity t = (LivingEntity) e;
                        if (t instanceof org.bukkit.entity.Villager || t instanceof org.bukkit.entity.ArmorStand)
                            continue;

                        // Repeler LEJOS del centro
                        Vector repel = t.getLocation().toVector().subtract(center.toVector()).normalize().multiply(0.35);
                        t.setVelocity(t.getVelocity().add(repel));

                        if (ticks % 10 == 0) {
                            t.damage(3, player);
                            t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 2));
                            world.playSound(t.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.3f, 1.5f);
                        }
                    }
                }

                if (ticks % 20 == 0)
                    world.playSound(center, Sound.BLOCK_BEACON_AMBIENT, 2.0f, 1.5f);
                ticks++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    // ==================== LLAMA ETERNA (PASIVA) ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null)
            return;
        Player killer = event.getEntity().getKiller();
        ItemStack weapon = killer.getInventory().getItemInMainHand();

        if (!WhiteHoleBlade.isWhiteHoleBlade(weapon, plugin))
            return;

        // Buffs de luz
        killer.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 2)); // Regen III 5s
        killer.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1)); // Speed II 5s

        // Reducción de cooldown en ambas habilidades por kill (3 segundos)
        UUID uid = killer.getUniqueId();
        if (novaCooldown.containsKey(uid))
            novaCooldown.put(uid, novaCooldown.get(uid) - 3000);
        if (singularityCooldown.containsKey(uid))
            singularityCooldown.put(uid, singularityCooldown.get(uid) - 3000);

        // Efecto visual — anillo de luz dorada
        Location loc = killer.getLocation().add(0, 1, 0);
        World world = loc.getWorld();
        for (double a = 0; a < Math.PI * 2; a += Math.PI / 8) {
            world.spawnParticle(Particle.DUST, loc.clone().add(Math.cos(a) * 1.5, 0, Math.sin(a) * 1.5), 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(255, 215, 0), 2.0f));
        }

        world.playSound(loc, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.8f, 1.5f);
        killer.sendTitle("", SmallCaps.convert("§e§l☀ Llama Eterna §7→ §aRegen+Speed"), 0, 20, 10);
    }

    // ==================== PARTÍCULAS AMBIENTALES ====================

    public void tickAmbientParticles() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (WhiteHoleBlade.isWhiteHoleBlade(item, plugin)) {
                Location loc = player.getLocation().add(0, 1.2, 0);

                // Partículas blancas/doradas suaves
                loc.getWorld().spawnParticle(Particle.DUST,
                        loc.clone().add((Math.random() - 0.5) * 0.8, (Math.random() - 0.5) * 0.5,
                                (Math.random() - 0.5) * 0.8),
                        1, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 230, 150), 0.7f));

                if (Math.random() < 0.15) {
                    loc.getWorld().spawnParticle(Particle.END_ROD, loc, 1, 0.3, 0.3, 0.3, 0.01);
                }
                if (Math.random() < 0.08) {
                    loc.getWorld().spawnParticle(Particle.DUST, loc, 1, 0.2, 0.2, 0.2, 0,
                            new Particle.DustOptions(Color.fromRGB(255, 255, 255), 0.5f));
                }
            }
        }
    }
}
