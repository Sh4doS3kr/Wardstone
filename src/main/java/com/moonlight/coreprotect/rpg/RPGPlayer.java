package com.moonlight.coreprotect.rpg;

import java.util.*;

public class RPGPlayer {

    private final UUID uuid;
    private RPGClass rpgClass;
    private RPGSubclass subclass;
    private int level;
    private long xp;
    private RPGStats stats;
    private int skillPoints;
    private int abilityPoints;
    private double mana;
    private Set<String> unlockedAbilities;
    private Map<String, Long> cooldowns;
    private Map<String, Integer> reputation; // factionId -> rep
    private String linkCode; // 6-digit web link code
    private boolean classMastery; // unlocked at level 50
    private int totalMobKills;
    private int totalBossKills;
    private int totalDeaths;
    private int dungeonLevel;

    public RPGPlayer(UUID uuid) {
        this.uuid = uuid;
        this.rpgClass = null;
        this.subclass = null;
        this.level = 1;
        this.xp = 0;
        this.stats = new RPGStats();
        this.skillPoints = 0;
        this.abilityPoints = 0;
        this.mana = 100;
        this.unlockedAbilities = new HashSet<>();
        this.cooldowns = new HashMap<>();
        this.reputation = new HashMap<>();
        this.linkCode = null;
        this.classMastery = false;
        this.totalMobKills = 0;
        this.totalBossKills = 0;
        this.totalDeaths = 0;
        this.dungeonLevel = 0;
    }

    // XP required for a given level
    public static long xpForLevel(int level) {
        return (long)(100 * Math.pow(level, 1.5));
    }

    public long getXpToNextLevel() {
        return xpForLevel(level + 1);
    }

    public double getXpProgress() {
        long needed = getXpToNextLevel();
        return needed > 0 ? (double) xp / needed : 1.0;
    }

    public boolean addXp(long amount) {
        this.xp += amount;
        boolean leveled = false;
        while (xp >= getXpToNextLevel() && level < 100) {
            xp -= getXpToNextLevel();
            level++;
            skillPoints += 2;
            if (level % 5 == 0) abilityPoints++;
            if (level == 50 && !classMastery) classMastery = true;
            leveled = true;
        }
        if (level >= 100) xp = 0;
        return leveled;
    }

    public boolean hasAbility(String id) { return unlockedAbilities.contains(id); }
    public void unlockAbility(String id) { unlockedAbilities.add(id); }

    public boolean isOnCooldown(String abilityId) {
        Long cd = cooldowns.get(abilityId);
        return cd != null && System.currentTimeMillis() < cd;
    }

    public long getCooldownRemaining(String abilityId) {
        Long cd = cooldowns.get(abilityId);
        if (cd == null) return 0;
        long rem = cd - System.currentTimeMillis();
        return Math.max(0, rem);
    }

    public void setCooldown(String abilityId, long durationMs) {
        cooldowns.put(abilityId, System.currentTimeMillis() + durationMs);
    }

    public boolean useMana(double cost) {
        if (mana < cost) return false;
        mana -= cost;
        return true;
    }

    public void regenMana(double amount) {
        mana = Math.min(stats.getMaxMana(), mana + amount);
    }

    public int getReputation(String factionId) {
        return reputation.getOrDefault(factionId, 0);
    }

    public void addReputation(String factionId, int amount) {
        reputation.put(factionId, getReputation(factionId) + amount);
    }

    public String generateLinkCode() {
        Random r = new Random();
        this.linkCode = String.format("%06d", r.nextInt(1000000));
        return this.linkCode;
    }

    // Getters & Setters
    public UUID getUuid() { return uuid; }
    public RPGClass getRpgClass() { return rpgClass; }
    public void setRpgClass(RPGClass rpgClass) { this.rpgClass = rpgClass; }
    public RPGSubclass getSubclass() { return subclass; }
    public void setSubclass(RPGSubclass subclass) { this.subclass = subclass; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public long getXp() { return xp; }
    public void setXp(long xp) { this.xp = xp; }
    public RPGStats getStats() { return stats; }
    public void setStats(RPGStats stats) { this.stats = stats; }
    public int getSkillPoints() { return skillPoints; }
    public void setSkillPoints(int sp) { this.skillPoints = sp; }
    public int getAbilityPoints() { return abilityPoints; }
    public void setAbilityPoints(int ap) { this.abilityPoints = ap; }
    public double getMana() { return mana; }
    public void setMana(double mana) { this.mana = mana; }
    public Set<String> getUnlockedAbilities() { return unlockedAbilities; }
    public void setUnlockedAbilities(Set<String> a) { this.unlockedAbilities = a; }
    public Map<String, Integer> getReputationMap() { return reputation; }
    public void setReputationMap(Map<String, Integer> r) { this.reputation = r; }
    public String getLinkCode() { return linkCode; }
    public void setLinkCode(String code) { this.linkCode = code; }
    public boolean hasClassMastery() { return classMastery; }
    public void setClassMastery(boolean m) { this.classMastery = m; }
    public int getTotalMobKills() { return totalMobKills; }
    public void setTotalMobKills(int k) { this.totalMobKills = k; }
    public void incrementMobKills() { this.totalMobKills++; }
    public int getTotalBossKills() { return totalBossKills; }
    public void setTotalBossKills(int k) { this.totalBossKills = k; }
    public void incrementBossKills() { this.totalBossKills++; }
    public int getTotalDeaths() { return totalDeaths; }
    public void setTotalDeaths(int d) { this.totalDeaths = d; }
    public void incrementDeaths() { this.totalDeaths++; }
    public int getDungeonLevel() { return dungeonLevel; }
    public void setDungeonLevel(int d) { this.dungeonLevel = d; }

    public boolean hasClass() { return rpgClass != null; }
    public boolean hasSubclass() { return subclass != null; }
}
