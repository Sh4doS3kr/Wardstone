package com.moonlight.coreprotect.evocore.metrics;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ZoneMetrics {

    private final UUID regionId;
    private final AtomicInteger killsLastHour = new AtomicInteger(0);
    private final AtomicInteger killsLastDay = new AtomicInteger(0);
    private final AtomicLong totalKills = new AtomicLong(0);
    private final AtomicInteger playersInZone = new AtomicInteger(0);
    private final AtomicLong totalPlayerTime = new AtomicLong(0); // ticks
    private final AtomicInteger resourcesCollected = new AtomicInteger(0);
    private final AtomicInteger chestsOpened = new AtomicInteger(0);
    private final AtomicInteger blocksPlaced = new AtomicInteger(0);
    private final AtomicInteger blocksBroken = new AtomicInteger(0);

    // Balance modifiers (applied by BalanceEngine)
    private double spawnRateModifier = 1.0;
    private double lootMultiplier = 1.0;
    private double mobHealthModifier = 1.0;
    private double pressureIndex = 0.0;

    // Hourly/daily tracking
    private int killsThisHour = 0;
    private int killsToday = 0;
    private long lastHourReset = System.currentTimeMillis();
    private long lastDayReset = System.currentTimeMillis();

    public ZoneMetrics(UUID regionId) {
        this.regionId = regionId;
    }

    public UUID getRegionId() { return regionId; }

    public void recordKill() {
        killsLastHour.incrementAndGet();
        killsLastDay.incrementAndGet();
        totalKills.incrementAndGet();
        killsThisHour++;
        killsToday++;
    }

    public void recordPlayerEnter() { playersInZone.incrementAndGet(); }
    public void recordPlayerLeave() { playersInZone.decrementAndGet(); if (playersInZone.get() < 0) playersInZone.set(0); }
    public void addPlayerTime(long ticks) { totalPlayerTime.addAndGet(ticks); }
    public void recordResourceCollected() { resourcesCollected.incrementAndGet(); }
    public void recordChestOpened() { chestsOpened.incrementAndGet(); }
    public void recordBlockPlaced() { blocksPlaced.incrementAndGet(); }
    public void recordBlockBroken() { blocksBroken.incrementAndGet(); }

    public void resetHourly() {
        killsLastHour.set(killsThisHour);
        killsThisHour = 0;
        lastHourReset = System.currentTimeMillis();
    }

    public void resetDaily() {
        killsLastDay.set(killsToday);
        killsToday = 0;
        lastDayReset = System.currentTimeMillis();
    }

    // Getters
    public int getKillsLastHour() { return killsLastHour.get(); }
    public int getKillsLastDay() { return killsLastDay.get(); }
    public long getTotalKills() { return totalKills.get(); }
    public int getPlayersInZone() { return playersInZone.get(); }
    public long getTotalPlayerTime() { return totalPlayerTime.get(); }
    public int getResourcesCollected() { return resourcesCollected.get(); }
    public int getChestsOpened() { return chestsOpened.get(); }
    public int getBlocksPlaced() { return blocksPlaced.get(); }
    public int getBlocksBroken() { return blocksBroken.get(); }

    public double getSpawnRateModifier() { return spawnRateModifier; }
    public void setSpawnRateModifier(double v) { spawnRateModifier = Math.max(0.6, Math.min(1.5, v)); }
    public double getLootMultiplier() { return lootMultiplier; }
    public void setLootMultiplier(double v) { lootMultiplier = Math.max(0.5, Math.min(2.0, v)); }
    public double getMobHealthModifier() { return mobHealthModifier; }
    public void setMobHealthModifier(double v) { mobHealthModifier = Math.max(0.7, Math.min(1.5, v)); }
    public double getPressureIndex() { return pressureIndex; }
    public void setPressureIndex(double v) { pressureIndex = v; }

    public double calculatePressureIndex() {
        double killPressure = killsLastHour.get() / 20.0;
        double playerPressure = playersInZone.get() / 5.0;
        double resourcePressure = resourcesCollected.get() / 50.0;
        pressureIndex = (killPressure * 0.4) + (playerPressure * 0.3) + (resourcePressure * 0.3);
        return pressureIndex;
    }
}
