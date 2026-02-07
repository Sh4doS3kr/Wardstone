package com.moonlight.coreprotect.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ProtectedRegion {

    private final UUID id;
    private final UUID owner;
    private final String worldName;
    private final int coreX;
    private final int coreY;
    private final int coreZ;
    private int level;
    private int size;
    private final List<UUID> members;
    private final long createdAt;

    // Upgrade flags
    private boolean noExplosion;
    private boolean noPvP;
    private int damageBoostLevel; // 0-5, each +5% damage
    private int healthBoostLevel; // 0-5, each +2 hearts
    private boolean noMobSpawn;
    private boolean autoHeal;
    private boolean speedBoost;
    private boolean noFallDamage;

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
        this.createdAt = System.currentTimeMillis();
        this.noExplosion = false;
        this.noPvP = false;
        this.damageBoostLevel = 0;
        this.healthBoostLevel = 0;
        this.noMobSpawn = false;
        this.autoHeal = false;
        this.speedBoost = false;
        this.noFallDamage = false;
    }

    public ProtectedRegion(UUID id, UUID owner, String worldName, int coreX, int coreY, int coreZ,
            int level, int size, List<UUID> members, long createdAt,
            boolean noExplosion, boolean noPvP, int damageBoostLevel, int healthBoostLevel,
            boolean noMobSpawn, boolean autoHeal, boolean speedBoost, boolean noFallDamage) {
        this.id = id;
        this.owner = owner;
        this.worldName = worldName;
        this.coreX = coreX;
        this.coreY = coreY;
        this.coreZ = coreZ;
        this.level = level;
        this.size = size;
        this.members = members;
        this.createdAt = createdAt;
        this.noExplosion = noExplosion;
        this.noPvP = noPvP;
        this.damageBoostLevel = damageBoostLevel;
        this.healthBoostLevel = healthBoostLevel;
        this.noMobSpawn = noMobSpawn;
        this.autoHeal = autoHeal;
        this.speedBoost = speedBoost;
        this.noFallDamage = noFallDamage;
    }

    public boolean contains(Location location) {
        if (!location.getWorld().getName().equals(worldName)) {
            return false;
        }

        int halfSize = size / 2;
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
        return count;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwner() {
        return owner;
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
