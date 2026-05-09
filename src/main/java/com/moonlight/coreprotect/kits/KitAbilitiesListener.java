package com.moonlight.coreprotect.kits;

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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Habilidades activas (Shift + Click Derecho con espada) para todos los kits.
 * Kits: Guerrero, PvP, Esencia, Dios (EssenceShop) + Eclipse, Moonlord (KitsCommand).
 */
public class KitAbilitiesListener implements Listener {

    private final CoreProtectPlugin plugin;
    private final Map<String, Map<UUID, Long>> cooldowns = new HashMap<>();

    private static final long CD_GUERRERO = 25_000L;
    private static final long CD_PVP     = 20_000L;
    private static final long CD_ESENCIA = 30_000L;
    private static final long CD_DIOS    = 40_000L;
    private static final long CD_ECLIPSE = 30_000L;
    private static final long CD_MOONLORD = 35_000L;

    public KitAbilitiesListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        for (String k : new String[]{"guerrero","pvp","esencia","dios","eclipse","moonlord"})
            cooldowns.put(k, new HashMap<>());
    }

    // ==================== TRIGGER ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || !held.getType().name().contains("SWORD")) return;

        String kit = detectKit(player);
        if (kit == null) return;
        if (isRestricted(player)) return;

        UUID uid = player.getUniqueId();
        Map<UUID, Long> cd = cooldowns.get(kit);
        long now = System.currentTimeMillis();
        if (cd.containsKey(uid) && now < cd.get(uid)) {
            long left = (cd.get(uid) - now) / 1000;
            com.moonlight.coreprotect.util.ActionBarUtil.send(player, "§c§l✖ §cEnfriamiento: §e" + left + "s");
            return;
        }

        event.setCancelled(true);
        cd.put(uid, now + getCooldownMs(kit));

        switch (kit) {
            case "guerrero": abilityGuerrero(player); break;
            case "pvp":      abilityPvP(player); break;
            case "esencia":  abilityEsencia(player); break;
            case "dios":     abilityDios(player); break;
            case "eclipse":  abilityEclipse(player); break;
            case "moonlord": abilityMoonlord(player); break;
        }
    }

    // ==================== GUERRERO: Grito de Guerra ====================

    private void abilityGuerrero(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();

        for (Entity e : player.getNearbyEntities(8, 4, 8)) {
            if (!(e instanceof LivingEntity) || e == player) continue;
            LivingEntity le = (LivingEntity) e;
            Vector push = le.getLocation().toVector().subtract(loc.toVector()).normalize().multiply(1.8).setY(0.5);
            le.setVelocity(push);
            le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0, false, false, true));
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 160, 0, false, false, true));

        for (int i = 0; i < 24; i++) {
            double angle = (Math.PI * 2.0 / 24) * i;
            for (double r = 1; r <= 4; r += 1.5) {
                Location p = loc.clone().add(Math.cos(angle) * r, 0.3, Math.sin(angle) * r);
                world.spawnParticle(Particle.SWEEP_ATTACK, p, 1, 0, 0, 0, 0);
            }
        }
        world.spawnParticle(Particle.EXPLOSION, loc.clone().add(0, 1, 0), 5, 1, 0.5, 1, 0);
        world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.5f);
        world.playSound(loc, Sound.ENTITY_RAVAGER_ROAR, 0.8f, 1.2f);

        player.sendMessage(SmallCaps.convert("§b§l⚔ §7¡Grito de Guerra! §bEnemigos debilitados."));
        player.sendTitle(SmallCaps.convert("§b§l⚔"), SmallCaps.convert("§7Grito de Guerra"), 5, 20, 10);
    }

    // ==================== PVP: Ráfaga Carmesí ====================

    private void abilityPvP(Player player) {
        World world = player.getWorld();

        Vector dash = player.getLocation().getDirection().normalize().multiply(2.0).setY(0.3);
        player.setVelocity(dash);

        world.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.5f);
        player.sendMessage(SmallCaps.convert("§c§l⚡ §7¡Ráfaga Carmesí!"));
        player.sendTitle(SmallCaps.convert("§c§l⚡"), SmallCaps.convert("§7Ráfaga Carmesí"), 3, 15, 5);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ > 8 || !player.isOnline() || player.isDead()) {
                    Location end = player.getLocation();
                    world.spawnParticle(Particle.FLAME, end.clone().add(0, 1, 0), 30, 1.5, 0.8, 1.5, 0.05);
                    world.playSound(end, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.5f);
                    for (Entity e : player.getNearbyEntities(3, 2, 3)) {
                        if (e instanceof LivingEntity && e != player) {
                            ((LivingEntity) e).damage(6.0, player);
                            e.setFireTicks(60);
                        }
                    }
                    cancel();
                    return;
                }
                Location trail = player.getLocation().add(0, 1, 0);
                world.spawnParticle(Particle.DUST, trail, 8, 0.4, 0.5, 0.4, 0,
                    new Particle.DustOptions(Color.fromRGB(200, 0, 0), 1.5f));
                world.spawnParticle(Particle.FLAME, trail, 3, 0.2, 0.2, 0.2, 0.02);
                for (Entity e : player.getNearbyEntities(1.5, 1.5, 1.5)) {
                    if (e instanceof LivingEntity && e != player)
                        ((LivingEntity) e).damage(4.0, player);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // ==================== ESENCIA: Pulso Arcano ====================

    private void abilityEsencia(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();

        double maxHp = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
        player.setHealth(Math.min(maxHp, player.getHealth() + 8));

        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 160, 1, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 0, false, false, true));

        for (Entity e : player.getNearbyEntities(6, 3, 6)) {
            if (e instanceof LivingEntity && e != player) {
                ((LivingEntity) e).addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1, false, false, true));
                ((LivingEntity) e).addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0, false, false, true));
            }
        }

        for (int i = 0; i < 36; i++) {
            double angle = (Math.PI * 2.0 / 36) * i;
            double r = 3.0;
            Location p = loc.clone().add(Math.cos(angle) * r, 0.5 + (i * 0.05), Math.sin(angle) * r);
            world.spawnParticle(Particle.ENCHANT, p, 3, 0.1, 0.1, 0.1, 0.5);
            world.spawnParticle(Particle.END_ROD, p, 1, 0, 0, 0, 0.02);
        }
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.3);
        world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.5f);
        world.playSound(loc, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 0.8f, 1.2f);

        player.sendMessage(SmallCaps.convert("§d§l✦ §7¡Pulso Arcano! §dCuración + Resistencia."));
        player.sendTitle(SmallCaps.convert("§d§l✦"), SmallCaps.convert("§7Pulso Arcano"), 5, 20, 10);
    }

    // ==================== DIOS: Juicio Divino ====================

    private void abilityDios(Player player) {
        World world = player.getWorld();

        RayTraceResult result = world.rayTraceEntities(player.getEyeLocation(),
            player.getEyeLocation().getDirection(), 30,
            e -> e instanceof LivingEntity && e != player);

        if (result == null || !(result.getHitEntity() instanceof LivingEntity)) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §7No hay objetivo a la vista."));
            cooldowns.get("dios").remove(player.getUniqueId());
            return;
        }

        LivingEntity target = (LivingEntity) result.getHitEntity();
        Location tLoc = target.getLocation();

        world.strikeLightningEffect(tLoc);
        target.damage(12.0, player);
        target.setFireTicks(80);

        int chains = 0;
        for (Entity nearby : target.getNearbyEntities(5, 3, 5)) {
            if (chains >= 2) break;
            if (nearby instanceof LivingEntity && nearby != player && nearby != target) {
                LivingEntity chain = (LivingEntity) nearby;
                world.strikeLightningEffect(chain.getLocation());
                chain.damage(6.0, player);
                chain.setFireTicks(40);
                chains++;
            }
        }

        world.spawnParticle(Particle.END_ROD, tLoc.clone().add(0, 2, 0), 30, 1, 2, 1, 0.1);
        world.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 0.8f);

        player.sendMessage(SmallCaps.convert("§6§l⚡ §7¡Juicio Divino! §6Rayo encadenado."));
        player.sendTitle(SmallCaps.convert("§6§l⚡"), SmallCaps.convert("§7Juicio Divino"), 5, 20, 10);
    }

    // ==================== ECLIPSE: Llamarada Solar ====================

    private void abilityEclipse(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();

        for (Entity e : player.getNearbyEntities(6, 3, 6)) {
            if (!(e instanceof LivingEntity) || e == player) continue;
            LivingEntity le = (LivingEntity) e;
            le.damage(8.0, player);
            le.setFireTicks(100);
            le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false, true));
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 200, 0, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1, false, false, true));

        world.spawnParticle(Particle.FLAME, loc.clone().add(0, 1, 0), 80, 3, 1.5, 3, 0.05);
        world.spawnParticle(Particle.LAVA, loc.clone().add(0, 1, 0), 30, 2, 1, 2, 0);
        for (int i = 0; i < 16; i++) {
            double angle = (Math.PI * 2.0 / 16) * i;
            for (double r = 1; r <= 6; r += 2) {
                Location p = loc.clone().add(Math.cos(angle) * r, 0.3, Math.sin(angle) * r);
                world.spawnParticle(Particle.FLAME, p, 3, 0.1, 0.1, 0.1, 0.02);
                world.spawnParticle(Particle.DUST, p, 2, 0.1, 0.1, 0.1, 0,
                    new Particle.DustOptions(Color.fromRGB(255, 140, 0), 1.5f));
            }
        }

        world.playSound(loc, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.5f);
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.2f);
        world.playSound(loc, Sound.ITEM_FIRECHARGE_USE, 1.0f, 0.8f);

        player.sendMessage(SmallCaps.convert("§6§l☀ §7¡Llamarada Solar! §6Quemadura en área."));
        player.sendTitle(SmallCaps.convert("§6§l☀"), SmallCaps.convert("§7Llamarada Solar"), 5, 20, 10);
    }

    // ==================== MOONLORD: Impacto Lunar ====================

    private void abilityMoonlord(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();

        LivingEntity target = null;
        double minDist = 15;
        for (Entity e : player.getNearbyEntities(15, 8, 15)) {
            if (!(e instanceof LivingEntity) || e == player) continue;
            double dist = e.getLocation().distance(loc);
            if (dist < minDist) { minDist = dist; target = (LivingEntity) e; }
        }

        if (target == null) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §7No hay enemigos cerca."));
            cooldowns.get("moonlord").remove(player.getUniqueId());
            return;
        }

        Location tLoc = target.getLocation();
        Vector behind = tLoc.getDirection().normalize().multiply(-2);
        Location tpLoc = tLoc.clone().add(behind);
        tpLoc.setY(tLoc.getY());
        tpLoc.setYaw(tLoc.getYaw());
        tpLoc.setPitch(0);

        world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 1, 0), 20, 0.3, 0.8, 0.3, 0.1);
        world.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.5f);

        player.teleport(tpLoc);
        target.damage(10.0, player);

        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 80, 0, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 80, 1, false, false, true));

        for (Entity e : player.getNearbyEntities(5, 3, 5)) {
            if (e instanceof LivingEntity && e != player)
                ((LivingEntity) e).addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1, false, false, true));
        }

        world.spawnParticle(Particle.END_ROD, tpLoc.clone().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.1);
        world.spawnParticle(Particle.ENCHANT, tpLoc.clone().add(0, 1, 0), 40, 1, 1, 1, 1);
        world.spawnParticle(Particle.DUST, tLoc.clone().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0,
            new Particle.DustOptions(Color.fromRGB(180, 0, 255), 1.5f));
        world.playSound(tpLoc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.8f);
        world.playSound(tpLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.3f, 2.0f);

        player.sendMessage(SmallCaps.convert("§d§l☽ §7¡Impacto Lunar! §dTeletransporte + Invisibilidad."));
        player.sendTitle(SmallCaps.convert("§d§l☽"), SmallCaps.convert("§7Impacto Lunar"), 5, 20, 10);
    }

    // ==================== HELPERS ====================

    private String detectKit(Player player) {
        if (isWearingKit(player, "§d§l", "Moonlord")) return "moonlord";
        if (isWearingKit(player, "§6§l", "Eclipse"))  return "eclipse";
        if (isWearingKit(player, "§6§l", "Dios"))     return "dios";
        if (isWearingKit(player, "§d§l", "Esencia"))  return "esencia";
        if (isWearingKit(player, "§c§l", "PvP"))      return "pvp";
        if (isWearingKit(player, "§b§l", "Guerrero")) return "guerrero";
        return null;
    }

    private boolean isWearingKit(Player player, String colorPrefix, String kitName) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        if (armor == null) return false;
        String scName = SmallCaps.convert(kitName);
        int count = 0;
        for (ItemStack piece : armor) {
            if (piece != null && piece.hasItemMeta() && piece.getItemMeta().hasDisplayName()) {
                String name = piece.getItemMeta().getDisplayName();
                if (name.contains(colorPrefix) && name.contains(scName)) count++;
            }
        }
        return count >= 3;
    }

    private long getCooldownMs(String kit) {
        switch (kit) {
            case "guerrero": return CD_GUERRERO;
            case "pvp":      return CD_PVP;
            case "esencia":  return CD_ESENCIA;
            case "dios":     return CD_DIOS;
            case "eclipse":  return CD_ECLIPSE;
            case "moonlord": return CD_MOONLORD;
            default:         return 30_000L;
        }
    }

    private boolean isRestricted(Player player) {
        // Protección de nuevo jugador: < 3h de juego
        int NEW_PLAYER_TICKS = 3 * 60 * 60 * 20;
        if (player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) < NEW_PLAYER_TICKS
                && !player.hasPermission("wardstone.admin")) {
            player.sendMessage(SmallCaps.convert("§e§l🛡 §fTienes protección de nuevo jugador. §7No puedes usar habilidades."));
            return true;
        }
        Location loc = player.getLocation();
        if (player.getWorld().getName().equals("koth")) {
            // En VALE_TODO se permiten habilidades
            com.moonlight.coreprotect.koth.KothManager km = plugin.getKothManager();
            if (km != null && km.isActive() && km.getCurrentMode() == com.moonlight.coreprotect.koth.KothMode.VALE_TODO) {
                // Permitir
            } else {
                player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes usar habilidades aquí."));
                return true;
            }
        }
        if (plugin.getBossArenaManager() != null && plugin.getBossArenaManager().isInAnyArena(loc)) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes usar habilidades aquí."));
            return true;
        }
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
