package com.moonlight.coreprotect.afkzone;

import java.util.List;

/**
 * Representa una recompensa de la zona AFK.
 * Puede ser dinero, items o una llave (comando de crates).
 */
public class AfkReward {

    private final String rarityDisplay;
    private final double minMoney;
    private final double maxMoney;
    private final List<String> items; // formato "MATERIAL:cantidad"
    private final String keyCommand; // comando para dar llave (null = no es llave)

    public AfkReward(String rarityDisplay, double minMoney, double maxMoney, List<String> items) {
        this(rarityDisplay, minMoney, maxMoney, items, null);
    }

    public AfkReward(String rarityDisplay, double minMoney, double maxMoney, List<String> items, String keyCommand) {
        this.rarityDisplay = rarityDisplay;
        this.minMoney = minMoney;
        this.maxMoney = maxMoney;
        this.items = items;
        this.keyCommand = keyCommand;
    }

    public boolean isMoney() {
        return (items == null || items.isEmpty()) && keyCommand == null;
    }

    public boolean isKey() {
        return keyCommand != null;
    }

    public double rollMoney() {
        if (minMoney >= maxMoney)
            return minMoney;
        return minMoney + Math.random() * (maxMoney - minMoney);
    }

    public String getRarityDisplay() {
        return rarityDisplay;
    }

    public List<String> getItems() {
        return items;
    }

    public double getMinMoney() {
        return minMoney;
    }

    public double getMaxMoney() {
        return maxMoney;
    }

    public String getKeyCommand() {
        return keyCommand;
    }
}
