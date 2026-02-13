package com.moonlight.coreprotect.evocore;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.evocore.metrics.MobMetrics;
import com.moonlight.coreprotect.evocore.metrics.ZoneMetrics;
import org.bukkit.entity.EntityType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;

public class BalanceEngine {

    private final CoreProtectPlugin plugin;
    private final DataCollector collector;
    private final AnalyticsEngine analytics;

    // Smoothing factor - prevents abrupt changes
    private static final double SMOOTH_FACTOR = 0.1;

    // Limits
    private static final double MAX_SPAWN_MOD = 1.5;
    private static final double MIN_SPAWN_MOD = 0.6;
    private static final double MAX_HEALTH_MOD = 1.5;
    private static final double MIN_HEALTH_MOD = 0.7;
    private static final double MAX_LOOT_MOD = 2.0;
    private static final double MIN_LOOT_MOD = 0.5;

    // Track consecutive overfarmed days per zone
    private final Map<UUID, Integer> overfarmedDays = new java.util.concurrent.ConcurrentHashMap<>();

    public BalanceEngine(CoreProtectPlugin plugin, DataCollector collector, AnalyticsEngine analytics) {
        this.plugin = plugin;
        this.collector = collector;
        this.analytics = analytics;
        startBalancing();
    }

    private void startBalancing() {
        // Adjust every 3 minutes
        new BukkitRunnable() {
            @Override
            public void run() {
                adjustZones();
                adjustMobs();
            }
        }.runTaskTimer(plugin, 20L * 60 * 3, 20L * 60 * 3);
    }

    private void adjustZones() {
        Map<UUID, AnalyticsEngine.ZoneStatus> statuses = analytics.getZoneStatuses();

        for (Map.Entry<UUID, AnalyticsEngine.ZoneStatus> entry : statuses.entrySet()) {
            ZoneMetrics zm = collector.getOrCreateZoneMetrics(entry.getKey());
            AnalyticsEngine.ZoneStatus status = entry.getValue();

            switch (status) {
                case OVERFARMED:
                    // Reduce spawns, increase mob health
                    smoothAdjust(zm, "spawnRate", zm.getSpawnRateModifier(), zm.getSpawnRateModifier() * 0.9);
                    smoothAdjust(zm, "mobHealth", zm.getMobHealthModifier(), zm.getMobHealthModifier() * 1.05);
                    smoothAdjust(zm, "loot", zm.getLootMultiplier(), zm.getLootMultiplier() * 0.95);

                    // Track consecutive overfarmed
                    overfarmedDays.merge(entry.getKey(), 1, Integer::sum);
                    break;

                case UNDERUSED:
                    // Boost spawns, increase loot
                    smoothAdjust(zm, "spawnRate", zm.getSpawnRateModifier(), zm.getSpawnRateModifier() * 1.15);
                    smoothAdjust(zm, "loot", zm.getLootMultiplier(), zm.getLootMultiplier() * 1.1);
                    overfarmedDays.remove(entry.getKey());
                    break;

                case HOTSPOT:
                    // Slight reduction, mobs more aggressive
                    smoothAdjust(zm, "spawnRate", zm.getSpawnRateModifier(), zm.getSpawnRateModifier() * 0.95);
                    smoothAdjust(zm, "mobHealth", zm.getMobHealthModifier(), zm.getMobHealthModifier() * 1.03);
                    break;

                case NORMAL:
                    // Gradually return to baseline
                    smoothAdjust(zm, "spawnRate", zm.getSpawnRateModifier(), 1.0);
                    smoothAdjust(zm, "mobHealth", zm.getMobHealthModifier(), 1.0);
                    smoothAdjust(zm, "loot", zm.getLootMultiplier(), 1.0);
                    overfarmedDays.remove(entry.getKey());
                    break;
            }
        }
    }

    private void adjustMobs() {
        Map<EntityType, String> statuses = analytics.getMobStatuses();

        for (Map.Entry<EntityType, String> entry : statuses.entrySet()) {
            MobMetrics mm = collector.getOrCreateMobMetrics(entry.getKey());
            String status = entry.getValue();

            switch (status) {
                case "OVERFARMED":
                    mm.setSpawnRateModifier(smooth(mm.getSpawnRateModifier(), mm.getSpawnRateModifier() * 0.9));
                    mm.setHealthModifier(smooth(mm.getHealthModifier(), mm.getHealthModifier() * 1.05));
                    break;

                case "TOO_EASY":
                    // Boss/mob dies too fast - buff it
                    mm.setHealthModifier(smooth(mm.getHealthModifier(), mm.getHealthModifier() * 1.1));
                    mm.setDamageModifier(smooth(mm.getDamageModifier(), mm.getDamageModifier() * 1.05));
                    break;

                case "AVOIDED":
                    // Nobody kills it - make loot better
                    mm.setLootQualityModifier(smooth(mm.getLootQualityModifier(), mm.getLootQualityModifier() * 1.1));
                    mm.setHealthModifier(smooth(mm.getHealthModifier(), mm.getHealthModifier() * 0.95));
                    break;

                case "NORMAL":
                    mm.setSpawnRateModifier(smooth(mm.getSpawnRateModifier(), 1.0));
                    mm.setHealthModifier(smooth(mm.getHealthModifier(), 1.0));
                    mm.setDamageModifier(smooth(mm.getDamageModifier(), 1.0));
                    mm.setLootQualityModifier(smooth(mm.getLootQualityModifier(), 1.0));
                    break;
            }
        }
    }

    private void smoothAdjust(ZoneMetrics zm, String type, double current, double target) {
        double newVal = smooth(current, target);
        switch (type) {
            case "spawnRate": zm.setSpawnRateModifier(newVal); break;
            case "mobHealth": zm.setMobHealthModifier(newVal); break;
            case "loot": zm.setLootMultiplier(newVal); break;
        }
    }

    private double smooth(double current, double target) {
        return current + (target - current) * SMOOTH_FACTOR;
    }

    public int getOverfarmedDays(UUID regionId) {
        return overfarmedDays.getOrDefault(regionId, 0);
    }
}
