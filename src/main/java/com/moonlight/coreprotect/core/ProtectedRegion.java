package com.moonlight.coreprotect.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ProtectedRegion {

    private final UUID id;
    private UUID owner;
    private final String worldName;
    private final int coreX;
    private final int coreY;
    private final int coreZ;
    private int level;
    private int size;
    private final List<UUID> members;
    private final List<UUID> bannedPlayers;
    private final long createdAt;

    // Tracks which boolean upgrades have been purchased (even if currently disabled)
    private final Set<String> unlockedUpgrades = new HashSet<>();

    // Upgrade flags
    private boolean noExplosion;
    private boolean noPvP;
    private int damageBoostLevel; // 0-5, each +5% damage
    private int healthBoostLevel; // 0-5, each +2 hearts
    private boolean noMobSpawn;
    private boolean autoHeal;
    private boolean speedBoost;
    private boolean noFallDamage;
    private boolean antiEnderman;
    private boolean resourceGenerator;
    private int fixedTime; // 0=off, 1=day, 2=night
    private boolean coreTeleport;
    private boolean noHunger;
    private boolean antiPhantom;

    // === NEW UPGRADES (Huge Update) ===
    private int xpBoostLevel; // 0-5, each +10% XP
    private int cropGrowthLevel; // 0-3, each +25% faster crops
    private boolean flyZone; // Members can fly inside
    private boolean autoReplant; // Crops auto-replant on harvest
    private int luckyMiningLevel; // 0-3, each +10% double drop chance
    private boolean beaconAura; // Night Vision + Haste for members
    private boolean antiFireSpread; // Fire cannot spread
    private boolean mobRepeller; // Hostile mobs pushed out of boundaries

    // === CORE SETTINGS ===
    private String coreName; // Custom name for the core/territory
    private int entryMode; // 0=open, 1=members-only, 2=banned-only (block banned)
    private String welcomeMessage; // Custom welcome message shown on entry

    // === ADVANCED FLAGS (owner-toggleable) ===
    private boolean allowPvP = false;      // PvP OFF by default
    private boolean allowExplosions = false; // Explosions OFF by default
    private boolean allowMobSpawn = true;   // Mobs spawn by default (until anti-mob upgrade)
    private boolean allowFallDamage = true;  // Fall damage ON by default
    private boolean allowHunger = true;      // Hunger ON by default
    private boolean allowFireSpread = true;  // Fire spreads by default
    private boolean allowAbilities = true;   // Players can use abilities by default

    // Prestige system (post level 24)
    private int prestige = 0; // 0 = no prestige, 1 = Prestige I, 2 = Prestige II, etc.

    public ProtectedRegion(UUID owner, Location coreLocation, int level, int size) {
        this.id = UUID.randomUUID();
        this.owner = owner;
        this.worldName = coreLocation.getWorld().getName();
        this.coreX = coreLocation.getBlockX();
        this.coreY = coreLocation.getBlockY();
        this.coreZ = coreLocation.getBlockZ();
        this.level = level;
        this.size = size;
        this.members = new ArrayList<>();
        this.bannedPlayers = new ArrayList<>();
        this.createdAt = System.currentTimeMillis();
        this.noExplosion = false;
        this.noPvP = true; // PvP OFF by default
        this.damageBoostLevel = 0;
        this.healthBoostLevel = 0;
        this.noMobSpawn = false;
        this.autoHeal = false;
        this.speedBoost = false;
        this.noFallDamage = false;
        this.antiEnderman = false;
        this.resourceGenerator = false;
        this.fixedTime = 0;
        this.coreTeleport = false;
        this.noHunger = false;
        this.antiPhantom = false;
        // New upgrades
        this.xpBoostLevel = 0;
        this.cropGrowthLevel = 0;
        this.flyZone = false;
        this.autoReplant = false;
        this.luckyMiningLevel = 0;
        this.beaconAura = false;
        this.antiFireSpread = false;
        this.mobRepeller = false;
        // Settings
        this.coreName = null;
        this.entryMode = 0;
        this.welcomeMessage = null;
    }

    public ProtectedRegion(UUID id, UUID owner, String worldName, int coreX, int coreY, int coreZ,
            int level, int size, List<UUID> members, long createdAt,
            boolean noExplosion, boolean noPvP, int damageBoostLevel, int healthBoostLevel,
            boolean noMobSpawn, boolean autoHeal, boolean speedBoost, boolean noFallDamage,
            boolean antiEnderman, boolean resourceGenerator, int fixedTime,
            boolean coreTeleport, boolean noHunger, boolean antiPhantom) {
        this.id = id;
        this.owner = owner;
        this.worldName = worldName;
        this.coreX = coreX;
        this.coreY = coreY;
        this.coreZ = coreZ;
        this.level = level;
        this.size = size;
        this.members = members;
        this.bannedPlayers = new ArrayList<>();
        this.createdAt = createdAt;
        this.noExplosion = noExplosion;
        this.noPvP = noPvP;
        this.damageBoostLevel = damageBoostLevel;
        this.healthBoostLevel = healthBoostLevel;
        this.noMobSpawn = noMobSpawn;
        this.autoHeal = autoHeal;
        this.speedBoost = speedBoost;
        this.noFallDamage = noFallDamage;
        this.antiEnderman = antiEnderman;
        this.resourceGenerator = resourceGenerator;
        this.fixedTime = fixedTime;
        this.coreTeleport = coreTeleport;
        this.noHunger = noHunger;
        this.antiPhantom = antiPhantom;
    }

    public boolean contains(Location location) {
        if (!location.getWorld().getName().equals(worldName)) {
            return false;
        }

        int effectiveSize = getEffectiveSize();
        int halfSize = effectiveSize / 2;
        int minX = coreX - halfSize;
        int maxX = coreX + halfSize;
        int minZ = coreZ - halfSize;
        int maxZ = coreZ + halfSize;

        int x = location.getBlockX();
        int z = location.getBlockZ();

        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public boolean canAccess(UUID player) {
        return owner.equals(player) || members.contains(player);
    }

    public boolean isBanned(UUID player) {
        return bannedPlayers.contains(player);
    }

    public void banPlayer(UUID player) {
        if (!bannedPlayers.contains(player)) {
            bannedPlayers.add(player);
        }
        members.remove(player);
    }

    public void unbanPlayer(UUID player) {
        bannedPlayers.remove(player);
    }

    public List<UUID> getBannedPlayers() {
        return new ArrayList<>(bannedPlayers);
    }

    public void setBannedPlayers(List<UUID> banned) {
        bannedPlayers.clear();
        bannedPlayers.addAll(banned);
    }

    public void addMember(UUID player) {
        if (!members.contains(player)) {
            members.add(player);
        }
    }

    public void removeMember(UUID player) {
        members.remove(player);
    }

    public boolean isMember(UUID player) {
        return members.contains(player);
    }

    public Location getCoreLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null)
            return null;
        return new Location(world, coreX, coreY, coreZ);
    }

    // --- Level upgrade ---
    public void setLevel(int level) {
        this.level = level;
    }

    public void setSize(int size) {
        this.size = size;
    }

    // --- Prestige ---
    public int getPrestige() {
        return prestige;
    }

    public void setPrestige(int prestige) {
        this.prestige = prestige;
    }

    /**
     * Returns the prestige zone size bonus (percentage).
     * Prestige 1: +5%, Prestige 2: +10%, Prestige 3: +15%
     */
    public double getPrestigeSizeMultiplier() {
        return 1.0 + (prestige * 0.05);
    }

    /**
     * Returns the effective size including prestige bonus.
     */
    public int getEffectiveSize() {
        return (int) Math.round(size * getPrestigeSizeMultiplier());
    }

    // --- Upgrade flag getters/setters ---
    public boolean isNoExplosion() {
        return noExplosion;
    }

    public void setNoExplosion(boolean noExplosion) {
        this.noExplosion = noExplosion;
    }

    public boolean isNoPvP() {
        return noPvP;
    }

    public void setNoPvP(boolean noPvP) {
        this.noPvP = noPvP;
    }

    public int getDamageBoostLevel() {
        return damageBoostLevel;
    }

    public void setDamageBoostLevel(int damageBoostLevel) {
        this.damageBoostLevel = Math.min(damageBoostLevel, 5);
    }

    public double getDamageBoostMultiplier() {
        return 1.0 + (damageBoostLevel * 0.05);
    }

    public int getHealthBoostLevel() {
        return healthBoostLevel;
    }

    public void setHealthBoostLevel(int healthBoostLevel) {
        this.healthBoostLevel = Math.min(healthBoostLevel, 5);
    }

    public double getExtraHealth() {
        return healthBoostLevel * 4.0; // 2 hearts = 4 HP per level
    }

    public boolean isNoMobSpawn() {
        return noMobSpawn;
    }

    public void setNoMobSpawn(boolean noMobSpawn) {
        this.noMobSpawn = noMobSpawn;
    }

    public boolean isAutoHeal() {
        return autoHeal;
    }

    public void setAutoHeal(boolean autoHeal) {
        this.autoHeal = autoHeal;
    }

    public boolean isSpeedBoost() {
        return speedBoost;
    }

    public void setSpeedBoost(boolean speedBoost) {
        this.speedBoost = speedBoost;
    }

    public boolean isNoFallDamage() {
        return noFallDamage;
    }

    public void setNoFallDamage(boolean noFallDamage) {
        this.noFallDamage = noFallDamage;
    }

    // New upgrade getters/setters
    public boolean isAntiEnderman() { return antiEnderman; }
    public void setAntiEnderman(boolean antiEnderman) { this.antiEnderman = antiEnderman; }

    public boolean isResourceGenerator() { return resourceGenerator; }
    public void setResourceGenerator(boolean resourceGenerator) { this.resourceGenerator = resourceGenerator; }

    public int getFixedTime() { return fixedTime; }
    public void setFixedTime(int fixedTime) { this.fixedTime = fixedTime; }

    public boolean isCoreTeleport() { return coreTeleport; }
    public void setCoreTeleport(boolean coreTeleport) { this.coreTeleport = coreTeleport; }

    public boolean isNoHunger() { return noHunger; }
    public void setNoHunger(boolean noHunger) { this.noHunger = noHunger; }

    public boolean isAntiPhantom() { return antiPhantom; }
    public void setAntiPhantom(boolean antiPhantom) { this.antiPhantom = antiPhantom; }

    // === NEW UPGRADE GETTERS/SETTERS ===
    public int getXpBoostLevel() { return xpBoostLevel; }
    public void setXpBoostLevel(int level) { this.xpBoostLevel = Math.min(level, 5); }
    public double getXpBoostMultiplier() { return 1.0 + (xpBoostLevel * 0.10); }

    public int getCropGrowthLevel() { return cropGrowthLevel; }
    public void setCropGrowthLevel(int level) { this.cropGrowthLevel = Math.min(level, 3); }
    public double getCropGrowthMultiplier() { return 1.0 + (cropGrowthLevel * 0.25); }

    public boolean isFlyZone() { return flyZone; }
    public void setFlyZone(boolean flyZone) { this.flyZone = flyZone; }

    public boolean isAutoReplant() { return autoReplant; }
    public void setAutoReplant(boolean autoReplant) { this.autoReplant = autoReplant; }

    public int getLuckyMiningLevel() { return luckyMiningLevel; }
    public void setLuckyMiningLevel(int level) { this.luckyMiningLevel = Math.min(level, 3); }
    public double getLuckyMiningChance() { return luckyMiningLevel * 0.10; } // 10% per level

    public boolean isBeaconAura() { return beaconAura; }
    public void setBeaconAura(boolean beaconAura) { this.beaconAura = beaconAura; }

    public boolean isAntiFireSpread() { return antiFireSpread; }
    public void setAntiFireSpread(boolean antiFireSpread) { this.antiFireSpread = antiFireSpread; }

    public boolean isMobRepeller() { return mobRepeller; }
    public void setMobRepeller(boolean mobRepeller) { this.mobRepeller = mobRepeller; }

    // === CORE SETTINGS GETTERS/SETTERS ===
    public String getCoreName() { return coreName; }
    public void setCoreName(String name) { this.coreName = name; }
    public String getDisplayName() { return coreName != null ? coreName : "Núcleo Nv." + level; }

    public int getEntryMode() { return entryMode; }
    public void setEntryMode(int mode) { this.entryMode = Math.max(0, Math.min(mode, 2)); }

    public String getWelcomeMessage() { return welcomeMessage; }
    public void setWelcomeMessage(String msg) { this.welcomeMessage = msg; }

    // === ADVANCED FLAGS GETTERS/SETTERS ===
    public boolean isAllowPvP() { return allowPvP; }
    public void setAllowPvP(boolean v) { this.allowPvP = v; }

    public boolean isAllowExplosions() { return allowExplosions; }
    public void setAllowExplosions(boolean v) { this.allowExplosions = v; }

    public boolean isAllowMobSpawn() { return allowMobSpawn; }
    public void setAllowMobSpawn(boolean v) { this.allowMobSpawn = v; }

    public boolean isAllowFallDamage() { return allowFallDamage; }
    public void setAllowFallDamage(boolean v) { this.allowFallDamage = v; }

    public boolean isAllowHunger() { return allowHunger; }
    public void setAllowHunger(boolean v) { this.allowHunger = v; }

    public boolean isAllowFireSpread() { return allowFireSpread; }
    public void setAllowFireSpread(boolean v) { this.allowFireSpread = v; }

    public boolean isAllowAbilities() { return allowAbilities; }
    public void setAllowAbilities(boolean v) { this.allowAbilities = v; }

    public boolean isUnlocked(String upgradeId) { return unlockedUpgrades.contains(upgradeId); }
    public void unlockUpgrade(String upgradeId) { unlockedUpgrades.add(upgradeId); }
    public Set<String> getUnlockedUpgrades() { return new HashSet<>(unlockedUpgrades); }
    public void setUnlockedUpgrades(Set<String> upgrades) { unlockedUpgrades.clear(); unlockedUpgrades.addAll(upgrades); }

    public int getActiveUpgradeCount() {
        int count = 0;
        if (noExplosion) count++;
        if (noPvP) count++;
        if (damageBoostLevel > 0) count++;
        if (healthBoostLevel > 0) count++;
        if (noMobSpawn) count++;
        if (autoHeal) count++;
        if (speedBoost) count++;
        if (noFallDamage) count++;
        if (antiEnderman) count++;
        if (resourceGenerator) count++;
        if (fixedTime > 0) count++;
        if (coreTeleport) count++;
        if (noHunger) count++;
        if (antiPhantom) count++;
        // New upgrades
        if (xpBoostLevel > 0) count++;
        if (cropGrowthLevel > 0) count++;
        if (flyZone) count++;
        if (autoReplant) count++;
        if (luckyMiningLevel > 0) count++;
        if (beaconAura) count++;
        if (antiFireSpread) count++;
        if (mobRepeller) count++;
        return count;
    }

    public int getTotalUpgradeCount() { return 22; } // Total available upgrades

    public UUID getId() {
        return id;
    }

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getCoreX() {
        return coreX;
    }

    public int getCoreY() {
        return coreY;
    }

    public int getCoreZ() {
        return coreZ;
    }

    public int getLevel() {
        return level;
    }

    public int getSize() {
        return size;
    }

    public List<UUID> getMembers() {
        return new ArrayList<>(members);
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
