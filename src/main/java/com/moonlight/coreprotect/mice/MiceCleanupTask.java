package com.moonlight.coreprotect.mice;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Limpia ratones de MythicMobs periódicamente para evitar lag.
 * - Máximo global de ratones: 40
 * - Si no hay jugadores cerca (64 bloques), el ratón se elimina
 * - Se ejecuta cada 30 segundos
 */
public class MiceCleanupTask {

    private static final int MAX_RATS_TOTAL = 40;
    private static final double NO_PLAYER_RANGE = 64.0;
    private static final NamespacedKey MYTHIC_KEY = new NamespacedKey("mythicmobs", "type");

    public static void start(CoreProtectPlugin plugin) {
        new BukkitRunnable() {
            @Override
            public void run() {
                List<Entity> allRats = new ArrayList<>();

                for (World world : Bukkit.getWorlds()) {
                    if (world.getName().equals("minigames")) continue;

                    for (Entity entity : world.getEntities()) {
                        if (!(entity instanceof LivingEntity)) continue;
                        if (isRat(entity)) {
                            allRats.add(entity);
                        }
                    }
                }

                if (allRats.isEmpty()) return;

                int removed = 0;

                // 1. Eliminar ratones sin jugadores cerca
                for (Entity rat : new ArrayList<>(allRats)) {
                    if (!rat.isValid()) continue;
                    boolean playerNearby = rat.getWorld().getPlayers().stream()
                            .anyMatch(p -> p.getLocation().distanceSquared(rat.getLocation()) < NO_PLAYER_RANGE * NO_PLAYER_RANGE);
                    if (!playerNearby) {
                        rat.remove();
                        allRats.remove(rat);
                        removed++;
                    }
                }

                // 2. Si todavía hay demasiados, eliminar los más viejos
                if (allRats.size() > MAX_RATS_TOTAL) {
                    int toRemove = allRats.size() - MAX_RATS_TOTAL;
                    // Ordenar por tick de existencia (los más viejos primero)
                    allRats.sort((a, b) -> Integer.compare(b.getTicksLived(), a.getTicksLived()));
                    for (int i = 0; i < toRemove && i < allRats.size(); i++) {
                        Entity rat = allRats.get(i);
                        if (rat.isValid()) {
                            rat.remove();
                            removed++;
                        }
                    }
                }

                if (removed > 0) {
                    plugin.getLogger().info("[MiceCleanup] Eliminados " + removed + " ratones. Restantes: " + (allRats.size() - removed));
                }
            }
        }.runTaskTimer(plugin, 600L, 600L); // cada 30 segundos
    }

    private static boolean isRat(Entity entity) {
        // Verificar por PersistentDataContainer de MythicMobs
        if (entity instanceof LivingEntity living) {
            PersistentDataContainer pdc = living.getPersistentDataContainer();
            if (pdc.has(MYTHIC_KEY, PersistentDataType.STRING)) {
                String type = pdc.get(MYTHIC_KEY, PersistentDataType.STRING);
                if (type != null) {
                    String lower = type.toLowerCase();
                    if (lower.contains("rat") || lower.contains("raton") || lower.contains("ratón")
                            || lower.contains("mouse") || lower.contains("mice")) {
                        return true;
                    }
                }
            }
            // Fallback: verificar custom name
            String name = living.getCustomName();
            if (name != null) {
                String lower = name.toLowerCase().replaceAll("§.", "");
                if (lower.contains("rat") || lower.contains("ratón") || lower.contains("raton")
                        || lower.contains("mouse")) {
                    return true;
                }
            }
        }
        return false;
    }
}
