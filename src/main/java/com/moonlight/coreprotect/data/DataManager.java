package com.moonlight.coreprotect.data;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.core.ProtectedRegion;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DataManager {

    private final CoreProtectPlugin plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;
    private final Map<UUID, UUID> playerHomes = new HashMap<>(); // playerUUID -> regionUUID

    public DataManager(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        loadConfig();
    }

    private void loadConfig() {
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("No se pudo crear el archivo de datos: " + e.getMessage());
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void loadData() {
        plugin.getProtectionManager().clearRegions();

        ConfigurationSection regionsSection = dataConfig.getConfigurationSection("regions");
        if (regionsSection == null) {
            return;
        }

        for (String key : regionsSection.getKeys(false)) {
            try {
                ConfigurationSection regionSection = regionsSection.getConfigurationSection(key);
                if (regionSection == null)
                    continue;

                UUID id = UUID.fromString(key);
                UUID owner = UUID.fromString(regionSection.getString("owner"));
                String worldName = regionSection.getString("world");
                int coreX = regionSection.getInt("coreX");
                int coreY = regionSection.getInt("coreY");
                int coreZ = regionSection.getInt("coreZ");
                int level = regionSection.getInt("level");
                int size = regionSection.getInt("size");
                long createdAt = regionSection.getLong("createdAt", System.currentTimeMillis());

                List<UUID> members = new ArrayList<>();
                List<String> memberStrings = regionSection.getStringList("members");
                for (String memberStr : memberStrings) {
                    try {
                        members.add(UUID.fromString(memberStr));
                    } catch (IllegalArgumentException ignored) {
                    }
                }

                // Load upgrade flags
                boolean noExplosion = regionSection.getBoolean("upgrades.noExplosion", false);
                boolean noPvP = regionSection.getBoolean("upgrades.noPvP", false);
                int damageBoostLevel = regionSection.getInt("upgrades.damageBoostLevel", 0);
                int healthBoostLevel = regionSection.getInt("upgrades.healthBoostLevel", 0);
                boolean noMobSpawn = regionSection.getBoolean("upgrades.noMobSpawn", false);
                boolean autoHeal = regionSection.getBoolean("upgrades.autoHeal", false);
                boolean speedBoost = regionSection.getBoolean("upgrades.speedBoost", false);
                boolean noFallDamage = regionSection.getBoolean("upgrades.noFallDamage", false);

                ProtectedRegion region = new ProtectedRegion(
                        id, owner, worldName, coreX, coreY, coreZ, level, size, members, createdAt,
                        noExplosion, noPvP, damageBoostLevel, healthBoostLevel,
                        noMobSpawn, autoHeal, speedBoost, noFallDamage);

                plugin.getProtectionManager().addRegion(region);
            } catch (Exception e) {
                plugin.getLogger().warning("Error al cargar region " + key + ": " + e.getMessage());
            }
        }

        plugin.getLogger().info("Cargadas " + plugin.getProtectionManager().getRegionCount() + " regiones protegidas");

        // Load homes
        playerHomes.clear();
        ConfigurationSection homesSection = dataConfig.getConfigurationSection("homes");
        if (homesSection != null) {
            for (String key : homesSection.getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(key);
                    UUID regionId = UUID.fromString(homesSection.getString(key));
                    playerHomes.put(playerId, regionId);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    public void saveData() {
        dataConfig.set("regions", null);

        for (ProtectedRegion region : plugin.getProtectionManager().getAllRegions()) {
            String path = "regions." + region.getId().toString();

            dataConfig.set(path + ".owner", region.getOwner().toString());
            dataConfig.set(path + ".world", region.getWorldName());
            dataConfig.set(path + ".coreX", region.getCoreX());
            dataConfig.set(path + ".coreY", region.getCoreY());
            dataConfig.set(path + ".coreZ", region.getCoreZ());
            dataConfig.set(path + ".level", region.getLevel());
            dataConfig.set(path + ".size", region.getSize());
            dataConfig.set(path + ".createdAt", region.getCreatedAt());

            List<String> memberStrings = new ArrayList<>();
            for (UUID member : region.getMembers()) {
                memberStrings.add(member.toString());
            }
            dataConfig.set(path + ".members", memberStrings);

            // Save upgrade flags
            dataConfig.set(path + ".upgrades.noExplosion", region.isNoExplosion());
            dataConfig.set(path + ".upgrades.noPvP", region.isNoPvP());
            dataConfig.set(path + ".upgrades.damageBoostLevel", region.getDamageBoostLevel());
            dataConfig.set(path + ".upgrades.healthBoostLevel", region.getHealthBoostLevel());
            dataConfig.set(path + ".upgrades.noMobSpawn", region.isNoMobSpawn());
            dataConfig.set(path + ".upgrades.autoHeal", region.isAutoHeal());
            dataConfig.set(path + ".upgrades.speedBoost", region.isSpeedBoost());
            dataConfig.set(path + ".upgrades.noFallDamage", region.isNoFallDamage());
        }

        // Save homes
        dataConfig.set("homes", null);
        for (Map.Entry<UUID, UUID> entry : playerHomes.entrySet()) {
            dataConfig.set("homes." + entry.getKey().toString(), entry.getValue().toString());
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Error al guardar datos: " + e.getMessage());
        }
    }

    public UUID getPlayerHome(UUID playerId) {
        return playerHomes.get(playerId);
    }

    public void setPlayerHome(UUID playerId, UUID regionId) {
        playerHomes.put(playerId, regionId);
    }

    public void removePlayerHome(UUID playerId) {
        playerHomes.remove(playerId);
    }
}
