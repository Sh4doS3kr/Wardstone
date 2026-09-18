package com.moonlight.coreprotect.finishers;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class FinisherManager {

    private final CoreProtectPlugin plugin;
    private final File dataFile;

    // playerUUID -> set of owned finisher IDs
    private final Map<UUID, Set<String>> ownedFinishers = new HashMap<>();
    // playerUUID -> currently selected finisher ID (null = none)
    private final Map<UUID, String> selectedFinisher = new HashMap<>();

    public FinisherManager(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "finishers.yml");
        loadData();
    }

    public void loadData() {
        if (!dataFile.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);

        ConfigurationSection playersSection = cfg.getConfigurationSection("players");
        if (playersSection == null) return;

        for (String key : playersSection.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                ConfigurationSection playerSection = playersSection.getConfigurationSection(key);
                if (playerSection == null) continue;

                List<String> owned = playerSection.getStringList("owned");
                ownedFinishers.put(uuid, new HashSet<>(owned));

                String selected = playerSection.getString("selected", null);
                if (selected != null && !selected.isEmpty()) {
                    selectedFinisher.put(uuid, selected);
                }
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void saveData() {
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Set<String>> entry : ownedFinishers.entrySet()) {
            String path = "players." + entry.getKey().toString();
            cfg.set(path + ".owned", new ArrayList<>(entry.getValue()));
            String sel = selectedFinisher.get(entry.getKey());
            cfg.set(path + ".selected", sel != null ? sel : "");
        }
        try {
            cfg.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Error guardando finishers.yml: " + e.getMessage());
        }
    }

    public boolean ownsFinisher(UUID player, FinisherType type) {
        Set<String> owned = ownedFinishers.get(player);
        return owned != null && owned.contains(type.getId());
    }

    public void purchaseFinisher(UUID player, FinisherType type) {
        ownedFinishers.computeIfAbsent(player, k -> new HashSet<>()).add(type.getId());
        saveData();
    }

    public FinisherType getSelectedFinisher(UUID player) {
        String id = selectedFinisher.get(player);
        if (id == null) return null;
        return FinisherType.fromId(id);
    }

    public void selectFinisher(UUID player, FinisherType type) {
        selectedFinisher.put(player, type.getId());
        saveData();
    }

    public void deselectFinisher(UUID player) {
        selectedFinisher.remove(player);
        saveData();
    }

    public boolean isSelected(UUID player, FinisherType type) {
        String sel = selectedFinisher.get(player);
        return sel != null && sel.equals(type.getId());
    }

    public double getPrice(FinisherType type) {
        return plugin.getConfig().getDouble("finishers.prices." + type.getId(), type.getDefaultPrice());
    }

    public Set<String> getOwnedFinisherIds(UUID player) {
        return ownedFinishers.getOrDefault(player, Collections.emptySet());
    }
}
