package com.moonlight.coreprotect.rpg;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RPGManager {

    private final CoreProtectPlugin plugin;
    private final Map<UUID, RPGPlayer> players = new ConcurrentHashMap<>();
    private final File dataFile;
    private YamlConfiguration dataConfig;

    // Web link codes: code -> playerUUID
    private final Map<String, UUID> activeLinkCodes = new ConcurrentHashMap<>();

    public RPGManager(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "rpg_data.yml");
        loadAll();
    }

    public RPGPlayer getPlayer(UUID uuid) {
        return players.computeIfAbsent(uuid, RPGPlayer::new);
    }

    public RPGPlayer getPlayerIfLoaded(UUID uuid) {
        return players.get(uuid);
    }

    public Collection<RPGPlayer> getAllPlayers() {
        return players.values();
    }

    // XP amounts by mob type
    public long getXpForMob(String mobType) {
        switch (mobType.toUpperCase()) {
            case "ZOMBIE": case "HUSK": case "DROWNED": return 15;
            case "SKELETON": case "STRAY": return 18;
            case "SPIDER": case "CAVE_SPIDER": return 14;
            case "CREEPER": return 25;
            case "WITCH": return 35;
            case "ENDERMAN": return 40;
            case "BLAZE": return 45;
            case "GHAST": return 50;
            case "WITHER_SKELETON": return 55;
            case "PIGLIN_BRUTE": return 50;
            case "RAVAGER": return 80;
            case "EVOKER": return 60;
            case "VINDICATOR": return 40;
            case "PHANTOM": return 30;
            case "GUARDIAN": case "ELDER_GUARDIAN": return 65;
            case "WARDEN": return 200;
            case "ENDER_DRAGON": return 5000;
            case "WITHER": return 3000;
            default: return 10;
        }
    }

    // Link code management for web
    public String generateLinkCode(UUID uuid) {
        RPGPlayer rp = getPlayer(uuid);
        // Remove old code if exists
        if (rp.getLinkCode() != null) {
            activeLinkCodes.remove(rp.getLinkCode());
        }
        String code = rp.generateLinkCode();
        activeLinkCodes.put(code, uuid);
        return code;
    }

    public UUID validateLinkCode(String code) {
        return activeLinkCodes.get(code);
    }

    public void consumeLinkCode(String code) {
        UUID uuid = activeLinkCodes.remove(code);
        if (uuid != null) {
            RPGPlayer rp = getPlayerIfLoaded(uuid);
            if (rp != null) rp.setLinkCode(null);
        }
    }

    // Leaderboard
    public List<RPGPlayer> getTopPlayers(int limit) {
        List<RPGPlayer> sorted = new ArrayList<>(players.values());
        sorted.sort((a, b) -> {
            if (b.getLevel() != a.getLevel()) return Integer.compare(b.getLevel(), a.getLevel());
            return Long.compare(b.getXp(), a.getXp());
        });
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }

    // Save/Load
    public void saveAll() {
        dataConfig = new YamlConfiguration();
        for (Map.Entry<UUID, RPGPlayer> entry : players.entrySet()) {
            String path = entry.getKey().toString();
            RPGPlayer rp = entry.getValue();

            dataConfig.set(path + ".class", rp.getRpgClass() != null ? rp.getRpgClass().name() : null);
            dataConfig.set(path + ".subclass", rp.getSubclass() != null ? rp.getSubclass().name() : null);
            dataConfig.set(path + ".level", rp.getLevel());
            dataConfig.set(path + ".xp", rp.getXp());
            dataConfig.set(path + ".skillPoints", rp.getSkillPoints());
            dataConfig.set(path + ".abilityPoints", rp.getAbilityPoints());
            dataConfig.set(path + ".mana", rp.getMana());
            dataConfig.set(path + ".classMastery", rp.hasClassMastery());
            dataConfig.set(path + ".mobKills", rp.getTotalMobKills());
            dataConfig.set(path + ".bossKills", rp.getTotalBossKills());
            dataConfig.set(path + ".deaths", rp.getTotalDeaths());
            dataConfig.set(path + ".dungeonLevel", rp.getDungeonLevel());

            // Stats
            Map<String, Integer> statsMap = rp.getStats().toMap();
            for (Map.Entry<String, Integer> s : statsMap.entrySet()) {
                dataConfig.set(path + ".stats." + s.getKey(), s.getValue());
            }

            // Abilities
            dataConfig.set(path + ".abilities", new ArrayList<>(rp.getUnlockedAbilities()));

            // Reputation
            for (Map.Entry<String, Integer> rep : rp.getReputationMap().entrySet()) {
                dataConfig.set(path + ".reputation." + rep.getKey(), rep.getValue());
            }
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save RPG data: " + e.getMessage());
        }
    }

    public void loadAll() {
        if (!dataFile.exists()) {
            dataConfig = new YamlConfiguration();
            return;
        }

        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        for (String uuidStr : dataConfig.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                RPGPlayer rp = new RPGPlayer(uuid);

                String className = dataConfig.getString(uuidStr + ".class");
                if (className != null) {
                    try { rp.setRpgClass(RPGClass.valueOf(className)); } catch (Exception ignored) {}
                }

                String subName = dataConfig.getString(uuidStr + ".subclass");
                if (subName != null) {
                    try { rp.setSubclass(RPGSubclass.valueOf(subName)); } catch (Exception ignored) {}
                }

                rp.setLevel(dataConfig.getInt(uuidStr + ".level", 1));
                rp.setXp(dataConfig.getLong(uuidStr + ".xp", 0));
                rp.setSkillPoints(dataConfig.getInt(uuidStr + ".skillPoints", 0));
                rp.setAbilityPoints(dataConfig.getInt(uuidStr + ".abilityPoints", 0));
                rp.setMana(dataConfig.getDouble(uuidStr + ".mana", 100));
                rp.setClassMastery(dataConfig.getBoolean(uuidStr + ".classMastery", false));
                rp.setTotalMobKills(dataConfig.getInt(uuidStr + ".mobKills", 0));
                rp.setTotalBossKills(dataConfig.getInt(uuidStr + ".bossKills", 0));
                rp.setTotalDeaths(dataConfig.getInt(uuidStr + ".deaths", 0));
                rp.setDungeonLevel(dataConfig.getInt(uuidStr + ".dungeonLevel", 0));

                // Stats
                ConfigurationSection statsSection = dataConfig.getConfigurationSection(uuidStr + ".stats");
                if (statsSection != null) {
                    Map<String, Object> sm = new HashMap<>();
                    for (String key : statsSection.getKeys(false)) {
                        sm.put(key, statsSection.getInt(key, 5));
                    }
                    rp.setStats(RPGStats.fromMap(sm));
                }

                // Abilities
                List<String> abilities = dataConfig.getStringList(uuidStr + ".abilities");
                rp.setUnlockedAbilities(new HashSet<>(abilities));

                // Reputation
                ConfigurationSection repSection = dataConfig.getConfigurationSection(uuidStr + ".reputation");
                if (repSection != null) {
                    Map<String, Integer> repMap = new HashMap<>();
                    for (String key : repSection.getKeys(false)) {
                        repMap.put(key, repSection.getInt(key, 0));
                    }
                    rp.setReputationMap(repMap);
                }

                players.put(uuid, rp);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load RPG data for: " + uuidStr);
            }
        }

        plugin.getLogger().info("Loaded " + players.size() + " RPG player profiles.");
    }

    public CoreProtectPlugin getPlugin() { return plugin; }
}
