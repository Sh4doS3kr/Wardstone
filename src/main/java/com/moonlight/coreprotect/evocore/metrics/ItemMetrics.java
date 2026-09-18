package com.moonlight.coreprotect.evocore.metrics;

import org.bukkit.Material;

import java.util.concurrent.atomic.AtomicInteger;

public class ItemMetrics {

    private final Material material;
    private final AtomicInteger timesSold = new AtomicInteger(0);
    private final AtomicInteger timesBought = new AtomicInteger(0);
    private final AtomicInteger timesUsed = new AtomicInteger(0);
    private final AtomicInteger timesEquipped = new AtomicInteger(0);
    private double totalSoldValue = 0;
    private double totalBoughtValue = 0;

    // Dynamic pricing
    private double basePrice = 0;
    private double currentPrice = 0;
    private double demandScore = 0;
    private double supplyScore = 0;

    public ItemMetrics(Material material) {
        this.material = material;
    }

    public Material getMaterial() { return material; }

    public void recordSold(double price) {
        timesSold.incrementAndGet();
        totalSoldValue += price;
        supplyScore += 0.1;
    }

    public void recordBought(double price) {
        timesBought.incrementAndGet();
        totalBoughtValue += price;
        demandScore += 0.1;
    }

    public void recordUsed() { timesUsed.incrementAndGet(); }
    public void recordEquipped() { timesEquipped.incrementAndGet(); }

    public int getTimesSold() { return timesSold.get(); }
    public int getTimesBought() { return timesBought.get(); }
    public int getTimesUsed() { return timesUsed.get(); }
    public int getTimesEquipped() { return timesEquipped.get(); }
    public double getAvgSellPrice() { return timesSold.get() > 0 ? totalSoldValue / timesSold.get() : 0; }
    public double getAvgBuyPrice() { return timesBought.get() > 0 ? totalBoughtValue / timesBought.get() : 0; }
    public double getDemandScore() { return demandScore; }
    public double getSupplyScore() { return supplyScore; }

    public void setBasePrice(double p) { this.basePrice = p; }
    public double getBasePrice() { return basePrice; }
    public void setCurrentPrice(double p) { this.currentPrice = p; }
    public double getCurrentPrice() { return currentPrice; }

    public void decayScores() {
        demandScore *= 0.95;
        supplyScore *= 0.95;
    }

    public double calculateDynamicPrice(double equilibriumFactor) {
        if (basePrice <= 0) return currentPrice;
        double diff = demandScore - supplyScore;
        currentPrice = basePrice * (1.0 + diff / equilibriumFactor);
        currentPrice = Math.max(basePrice * 0.5, Math.min(basePrice * 3.0, currentPrice));
        return currentPrice;
    }
}
