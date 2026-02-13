package com.moonlight.coreprotect.rpg;

import java.util.HashMap;
import java.util.Map;

public class RPGStats {

    private int strength;     // STR → + melee damage
    private int dexterity;    // DEX → + ranged damage, + attack speed
    private int intelligence; // INT → + magic damage, + mana regen
    private int vitality;     // VIT → + HP, + HP regen
    private int wisdom;       // WIS → + max mana, + cooldown reduction
    private int luck;         // LUK → + crit chance, + loot bonus

    public RPGStats() {
        this.strength = 5;
        this.dexterity = 5;
        this.intelligence = 5;
        this.vitality = 5;
        this.wisdom = 5;
        this.luck = 5;
    }

    public RPGStats(int str, int dex, int intel, int vit, int wis, int luk) {
        this.strength = str;
        this.dexterity = dex;
        this.intelligence = intel;
        this.vitality = vit;
        this.wisdom = wis;
        this.luck = luk;
    }

    // Derived stats
    public double getMeleeDamageMultiplier() { return 1.0 + (strength * 0.02); }
    public double getRangedDamageMultiplier() { return 1.0 + (dexterity * 0.02); }
    public double getMagicDamageMultiplier() { return 1.0 + (intelligence * 0.025); }
    public double getMaxHealthBonus() { return vitality * 2.0; }
    public double getHealthRegen() { return vitality * 0.1; }
    public double getMaxMana() { return 100 + (wisdom * 5.0); }
    public double getManaRegen() { return 2.0 + (wisdom * 0.3) + (intelligence * 0.1); }
    public double getCooldownReduction() { return Math.min(0.40, wisdom * 0.008); }
    public double getCritChance() { return Math.min(0.50, 0.05 + (luck * 0.01)); }
    public double getCritMultiplier() { return 1.5 + (luck * 0.01); }
    public double getLootBonus() { return 1.0 + (luck * 0.02); }
    public double getAttackSpeed() { return Math.min(0.30, dexterity * 0.005); }

    public int getTotal() { return strength + dexterity + intelligence + vitality + wisdom + luck; }

    // Getters & setters
    public int getStrength() { return strength; }
    public void setStrength(int v) { this.strength = v; }
    public int getDexterity() { return dexterity; }
    public void setDexterity(int v) { this.dexterity = v; }
    public int getIntelligence() { return intelligence; }
    public void setIntelligence(int v) { this.intelligence = v; }
    public int getVitality() { return vitality; }
    public void setVitality(int v) { this.vitality = v; }
    public int getWisdom() { return wisdom; }
    public void setWisdom(int v) { this.wisdom = v; }
    public int getLuck() { return luck; }
    public void setLuck(int v) { this.luck = v; }

    public void addStat(String stat, int amount) {
        switch (stat.toUpperCase()) {
            case "STR": strength += amount; break;
            case "DEX": dexterity += amount; break;
            case "INT": intelligence += amount; break;
            case "VIT": vitality += amount; break;
            case "WIS": wisdom += amount; break;
            case "LUK": luck += amount; break;
        }
    }

    public int getStat(String stat) {
        switch (stat.toUpperCase()) {
            case "STR": return strength;
            case "DEX": return dexterity;
            case "INT": return intelligence;
            case "VIT": return vitality;
            case "WIS": return wisdom;
            case "LUK": return luck;
            default: return 0;
        }
    }

    public Map<String, Integer> toMap() {
        Map<String, Integer> map = new HashMap<>();
        map.put("STR", strength);
        map.put("DEX", dexterity);
        map.put("INT", intelligence);
        map.put("VIT", vitality);
        map.put("WIS", wisdom);
        map.put("LUK", luck);
        return map;
    }

    public static RPGStats fromMap(Map<String, Object> map) {
        return new RPGStats(
            getInt(map, "STR", 5), getInt(map, "DEX", 5),
            getInt(map, "INT", 5), getInt(map, "VIT", 5),
            getInt(map, "WIS", 5), getInt(map, "LUK", 5)
        );
    }

    private static int getInt(Map<String, Object> map, String key, int def) {
        Object val = map.get(key);
        return val instanceof Number ? ((Number) val).intValue() : def;
    }
}
