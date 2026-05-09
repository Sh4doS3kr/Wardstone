package com.moonlight.coreprotect.bossarena;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Datos de configuración de una arena de boss.
 * Cada arena tiene su propio mundo (bossarena_1, bossarena_2, etc.)
 */
public class ArenaData {

    private final String worldName;
    private Location bossSpawn;
    private Location playerSpawn;
    private Location boundMin;
    private Location boundMax;
    private BossType bossType = BossType.CUBOSS_SLIME;

    public ArenaData(String worldName) {
        this.worldName = worldName;
    }

    public String getWorldName() {
        return worldName;
    }

    public World getWorld() {
        return Bukkit.getWorld(worldName);
    }

    public Location getBossSpawn() {
        return bossSpawn;
    }

    public void setBossSpawn(Location bossSpawn) {
        this.bossSpawn = bossSpawn;
    }

    public Location getPlayerSpawn() {
        return playerSpawn;
    }

    public void setPlayerSpawn(Location playerSpawn) {
        this.playerSpawn = playerSpawn;
    }

    public Location getBoundMin() {
        return boundMin;
    }

    public void setBoundMin(Location boundMin) {
        this.boundMin = boundMin;
    }

    public Location getBoundMax() {
        return boundMax;
    }

    public void setBoundMax(Location boundMax) {
        this.boundMax = boundMax;
    }

    public boolean isConfigured() {
        return bossSpawn != null && playerSpawn != null && boundMin != null && boundMax != null;
    }

    public BossType getBossType() {
        return bossType;
    }

    public void setBossType(BossType bossType) {
        this.bossType = bossType;
    }

    public boolean isInBounds(Location loc) {
        if (boundMin == null || boundMax == null) return false;
        if (!loc.getWorld().getName().equals(worldName)) return false;
        double x = loc.getX(), y = loc.getY(), z = loc.getZ();
        return x >= Math.min(boundMin.getX(), boundMax.getX()) && x <= Math.max(boundMin.getX(), boundMax.getX())
            && y >= Math.min(boundMin.getY(), boundMax.getY()) && y <= Math.max(boundMin.getY(), boundMax.getY())
            && z >= Math.min(boundMin.getZ(), boundMax.getZ()) && z <= Math.max(boundMin.getZ(), boundMax.getZ());
    }
}
