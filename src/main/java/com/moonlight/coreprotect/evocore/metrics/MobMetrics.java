package com.moonlight.coreprotect.evocore.metrics;

import org.bukkit.entity.EntityType;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class MobMetrics {

    private final EntityType mobType;
    private final AtomicInteger killsLastHour = new AtomicInteger(0);
    private final AtomicInteger killsLastDay = new AtomicInteger(0);
    private final AtomicLong totalKills = new AtomicLong(0);
    private long totalKillTimeMs = 0;
    private int killTimeCount = 0;
    private int avgPlayersInvolved = 1;

    // Balance modifiers
    private double spawnRateModifier = 1.0;
    private double healthModifier = 1.0;
    private double damageModifier = 1.0;
    private double lootQualityModifier = 1.0;

    private int killsThisHour = 0;
    private int killsToday = 0;

    public MobMetrics(EntityType mobType) {
        this.mobType = mobType;
    }

    public EntityType getMobType() { return mobType; }

    public void recordKill(long killTimeMs, int playersInvolved) {
        killsLastHour.incrementAndGet();
        killsLastDay.incrementAndGet();
        totalKills.incrementAndGet();
        killsThisHour++;
        killsToday++;
        totalKillTimeMs += killTimeMs;
        killTimeCount++;
        avgPlayersInvolved = (avgPlayersInvolved + playersInvolved) / 2;
    }

    public void resetHourly() {
        killsLastHour.set(killsThisHour);
        killsThisHour = 0;
    }

    public void resetDaily() {
        killsLastDay.set(killsToday);
        killsToday = 0;
    }

    public int getKillsLastHour() { return killsLastHour.get(); }
    public int getKillsLastDay() { return killsLastDay.get(); }
    public long getTotalKills() { return totalKills.get(); }
    public double getAvgKillTimeMs() { return killTimeCount > 0 ? (double) totalKillTimeMs / killTimeCount : 0; }
    public int getAvgPlayersInvolved() { return avgPlayersInvolved; }

    public double getSpawnRateModifier() { return spawnRateModifier; }
    public void setSpawnRateModifier(double v) { spawnRateModifier = Math.max(0.6, Math.min(1.5, v)); }
    public double getHealthModifier() { return healthModifier; }
    public void setHealthModifier(double v) { healthModifier = Math.max(0.7, Math.min(2.0, v)); }
    public double getDamageModifier() { return damageModifier; }
    public void setDamageModifier(double v) { damageModifier = Math.max(0.7, Math.min(1.5, v)); }
    public double getLootQualityModifier() { return lootQualityModifier; }
    public void setLootQualityModifier(double v) { lootQualityModifier = Math.max(0.5, Math.min(2.0, v)); }
}
