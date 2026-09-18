package com.moonlight.coreprotect.prestige;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Spawns walking particles based on prestige tier.
 * Cobre=chispas, Hierro=crit, Oro=estrellas, Esmeralda=verde,
 * Diamante=llamas azules, Amatista=dragón, Netherita=bruja, P70=tótem
 */
public class PrestigeParticleTask extends BukkitRunnable {

    private final CoreProtectPlugin plugin;
    private final Map<UUID, Location> lastPos = new HashMap<>();

    public PrestigeParticleTask(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        this.runTaskTimer(plugin, 40L, 4L);
    }

    @Override
    public void run() {
        PrestigeManager mgr = plugin.getPrestigeManager();
        if (mgr == null) return;
        long tick = System.currentTimeMillis();

        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID uid = p.getUniqueId();
            Location current = p.getLocation();

            // No mostrar partículas si está en staff mode (NookureStaff)
            if (p.hasMetadata("vanished")) continue;

            // No mostrar partículas en minijuegos ni en KotH
            String worldName = p.getWorld().getName();
            if (worldName.equals("minigames") || worldName.equals("koth")) continue;

            int prestige = mgr.getData(uid).prestige;
            if (prestige <= 0) continue;

            // ── Ambient AURA for P21+ (Oro+) — orbiting particles, always visible ──
            if (prestige >= 21) {
                Particle aura = getParticle(prestige);
                double angle = (tick / 120.0) % (2 * Math.PI);
                double radius = prestige >= 51 ? 1.0 : 0.75;
                int points = prestige >= 41 ? 3 : 2;
                for (int i = 0; i < points; i++) {
                    double a = angle + (i * (2 * Math.PI / points));
                    double x = Math.cos(a) * radius;
                    double z = Math.sin(a) * radius;
                    double y = 0.6 + Math.sin(a * 2) * 0.25;
                    p.getWorld().spawnParticle(aura, current.clone().add(x, y, z),
                            1, 0, 0, 0);
                }
            }

            // ── Walking particles (all tiers, only when moving) ──
            Location prev = lastPos.put(uid, current.clone());
            if (prev == null || !prev.getWorld().equals(current.getWorld())) continue;
            double distSq = prev.distanceSquared(current);
            if (distSq < 0.02 || distSq > 64) continue;

            Particle particle = getParticle(prestige);
            int count = prestige >= 50 ? 4 : 2;
            p.getWorld().spawnParticle(particle, current.clone().add(0, 0.15, 0),
                    count, 0.25, 0.05, 0.25);
        }
    }

    private Particle getParticle(int prestige) {
        if (prestige >= 70) return Particle.TOTEM_OF_UNDYING;
        if (prestige >= 61) return Particle.WITCH;
        if (prestige >= 51) return Particle.DRAGON_BREATH;
        if (prestige >= 41) return Particle.SOUL_FIRE_FLAME;
        if (prestige >= 31) return Particle.HAPPY_VILLAGER;
        if (prestige >= 21) return Particle.END_ROD;
        if (prestige >= 11) return Particle.CRIT;
        return Particle.FLAME;
    }

    public void cleanup(UUID uid) {
        lastPos.remove(uid);
    }
}
