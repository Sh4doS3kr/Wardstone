package com.moonlight.coreprotect.evocore;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.evocore.metrics.ItemMetrics;
import com.moonlight.coreprotect.evocore.metrics.MobMetrics;
import com.moonlight.coreprotect.evocore.metrics.ZoneMetrics;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class AnalyticsEngine {

    public enum ZoneStatus { NORMAL, OVERFARMED, UNDERUSED, HOTSPOT }
    public enum MetaStatus { NORMAL, META_DOMINANT, UNDERUSED }

    private final CoreProtectPlugin plugin;
    private final DataCollector collector;

    // Analysis results
    private final Map<UUID, ZoneStatus> zoneStatuses = new HashMap<>();
    private final Map<EntityType, String> mobStatuses = new HashMap<>();
    private final Map<Material, MetaStatus> itemStatuses = new HashMap<>();
    private final List<String> alerts = new ArrayList<>();

    // Thresholds
    private static final double OVERFARMED_MULTIPLIER = 1.5;
    private static final double UNDERUSED_THRESHOLD = 0.3;
    private static final double META_DOMINANT_MULTIPLIER = 2.0;
    private static final int EXPECTED_KILLS_PER_HOUR = 20;
    private static final long MIN_PLAYER_TIME_THRESHOLD = 20L * 60 * 5; // 5 min in ticks

    public AnalyticsEngine(CoreProtectPlugin plugin, DataCollector collector) {
        this.plugin = plugin;
        this.collector = collector;
        startAnalysis();
    }

    private void startAnalysis() {
        // Run analysis every 2 minutes
        new BukkitRunnable() {
            @Override
            public void run() {
                analyze();
            }
        }.runTaskTimerAsynchronously(plugin, 20L * 60 * 2, 20L * 60 * 2);
    }

    public synchronized void analyze() {
        alerts.clear();
        analyzeZones();
        analyzeMobs();
        analyzeItems();
    }

    private void analyzeZones() {
        Map<UUID, ZoneMetrics> zones = collector.getAllZoneMetrics();
        if (zones.isEmpty()) return;

        // Calculate average kills across all zones
        double avgKills = zones.values().stream()
                .mapToInt(ZoneMetrics::getKillsLastHour)
                .average().orElse(EXPECTED_KILLS_PER_HOUR);

        for (Map.Entry<UUID, ZoneMetrics> entry : zones.entrySet()) {
            ZoneMetrics zm = entry.getValue();
            zm.calculatePressureIndex();

            int kills = zm.getKillsLastHour();
            long playerTime = zm.getTotalPlayerTime();

            if (kills > avgKills * OVERFARMED_MULTIPLIER) {
                zoneStatuses.put(entry.getKey(), ZoneStatus.OVERFARMED);
                alerts.add("Zona " + entry.getKey().toString().substring(0, 8) + " sobreexplotada (" + kills + " kills/h)");
            } else if (playerTime < MIN_PLAYER_TIME_THRESHOLD && kills < avgKills * UNDERUSED_THRESHOLD) {
                zoneStatuses.put(entry.getKey(), ZoneStatus.UNDERUSED);
            } else if (zm.getPressureIndex() > 0.8) {
                zoneStatuses.put(entry.getKey(), ZoneStatus.HOTSPOT);
                alerts.add("Zona " + entry.getKey().toString().substring(0, 8) + " es un hotspot (RPI: " + String.format("%.2f", zm.getPressureIndex()) + ")");
            } else {
                zoneStatuses.put(entry.getKey(), ZoneStatus.NORMAL);
            }
        }
    }

    private void analyzeMobs() {
        Map<EntityType, MobMetrics> mobs = collector.getAllMobMetrics();
        if (mobs.isEmpty()) return;

        double avgKills = mobs.values().stream()
                .mapToInt(MobMetrics::getKillsLastHour)
                .average().orElse(10);

        for (Map.Entry<EntityType, MobMetrics> entry : mobs.entrySet()) {
            MobMetrics mm = entry.getValue();
            if (mm.getKillsLastHour() > avgKills * OVERFARMED_MULTIPLIER) {
                mobStatuses.put(entry.getKey(), "OVERFARMED");
                alerts.add(entry.getKey().name() + " sobreexplotado (" + mm.getKillsLastHour() + " kills/h)");
            } else if (mm.getAvgKillTimeMs() < 2000 && mm.getKillsLastHour() > 5) {
                mobStatuses.put(entry.getKey(), "TOO_EASY");
                alerts.add(entry.getKey().name() + " muere demasiado rápido (avg " + (int) mm.getAvgKillTimeMs() + "ms)");
            } else if (mm.getKillsLastHour() == 0 && mm.getKillsLastDay() > 0) {
                mobStatuses.put(entry.getKey(), "AVOIDED");
            } else {
                mobStatuses.put(entry.getKey(), "NORMAL");
            }
        }
    }

    private void analyzeItems() {
        Map<Material, ItemMetrics> items = collector.getAllItemMetrics();
        if (items.isEmpty()) return;

        double avgUsage = items.values().stream()
                .mapToInt(ItemMetrics::getTimesEquipped)
                .average().orElse(1);

        for (Map.Entry<Material, ItemMetrics> entry : items.entrySet()) {
            ItemMetrics im = entry.getValue();
            if (im.getTimesEquipped() > avgUsage * META_DOMINANT_MULTIPLIER && avgUsage > 0) {
                itemStatuses.put(entry.getKey(), MetaStatus.META_DOMINANT);
                alerts.add(entry.getKey().name() + " es META DOMINANTE (" + im.getTimesEquipped() + " usos)");
            } else if (im.getTimesEquipped() < avgUsage * UNDERUSED_THRESHOLD) {
                itemStatuses.put(entry.getKey(), MetaStatus.UNDERUSED);
            } else {
                itemStatuses.put(entry.getKey(), MetaStatus.NORMAL);
            }
        }
    }

    // --- Getters for API/Web ---
    public synchronized Map<UUID, ZoneStatus> getZoneStatuses() { return new HashMap<>(zoneStatuses); }
    public synchronized Map<EntityType, String> getMobStatuses() { return new HashMap<>(mobStatuses); }
    public synchronized Map<Material, MetaStatus> getItemStatuses() { return new HashMap<>(itemStatuses); }
    public synchronized List<String> getAlerts() { return new ArrayList<>(alerts); }

    public ZoneStatus getZoneStatus(UUID regionId) {
        return zoneStatuses.getOrDefault(regionId, ZoneStatus.NORMAL);
    }
}
