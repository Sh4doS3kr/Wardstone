package com.moonlight.coreprotect.warps;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PlayerWarpManager {

    private final CoreProtectPlugin plugin;
    private final File dataFile;
    private final Map<String, PlayerWarp> warps = new ConcurrentHashMap<>();

    public PlayerWarpManager(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "playerwarps.yml");
        loadData();
    }

    // ==================== DATA ====================

    public void loadData() {
        warps.clear();
        if (!dataFile.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection sec = cfg.getConfigurationSection("warps");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            try {
                ConfigurationSection ws = sec.getConfigurationSection(key);
                if (ws == null) continue;
                UUID owner = UUID.fromString(ws.getString("owner", ""));
                String ownerName = ws.getString("ownerName", "???");
                String worldName = ws.getString("world", "world");
                World world = Bukkit.getWorld(worldName);
                if (world == null) continue;
                double x = ws.getDouble("x");
                double y = ws.getDouble("y");
                double z = ws.getDouble("z");
                float yaw = (float) ws.getDouble("yaw");
                float pitch = (float) ws.getDouble("pitch");
                Location loc = new Location(world, x, y, z, yaw, pitch);
                long created = ws.getLong("created", System.currentTimeMillis());
                int visits = ws.getInt("visits", 0);
                double price = ws.getDouble("price", 0);
                String description = ws.getString("description", "");

                // Load ratings
                Map<UUID, Integer> ratings = new HashMap<>();
                ConfigurationSection ratingSec = ws.getConfigurationSection("ratings");
                if (ratingSec != null) {
                    for (String uuid : ratingSec.getKeys(false)) {
                        try {
                            ratings.put(UUID.fromString(uuid), ratingSec.getInt(uuid));
                        } catch (Exception ignored) {}
                    }
                }

                PlayerWarp warp = new PlayerWarp(key.toLowerCase(), owner, ownerName, loc, created, visits, price, description, ratings);
                warps.put(key.toLowerCase(), warp);
            } catch (Exception e) {
                plugin.getLogger().warning("[PlayerWarps] Error cargando warp '" + key + "': " + e.getMessage());
            }
        }
        plugin.getLogger().info("[PlayerWarps] Cargadas " + warps.size() + " warps de jugadores.");
    }

    public void saveData() {
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<String, PlayerWarp> entry : warps.entrySet()) {
            PlayerWarp w = entry.getValue();
            String path = "warps." + w.name;
            cfg.set(path + ".owner", w.owner.toString());
            cfg.set(path + ".ownerName", w.ownerName);
            cfg.set(path + ".world", w.location.getWorld().getName());
            cfg.set(path + ".x", w.location.getX());
            cfg.set(path + ".y", w.location.getY());
            cfg.set(path + ".z", w.location.getZ());
            cfg.set(path + ".yaw", w.location.getYaw());
            cfg.set(path + ".pitch", w.location.getPitch());
            cfg.set(path + ".created", w.created);
            cfg.set(path + ".visits", w.visits);
            cfg.set(path + ".price", w.price);
            cfg.set(path + ".description", w.description);
            for (Map.Entry<UUID, Integer> r : w.ratings.entrySet()) {
                cfg.set(path + ".ratings." + r.getKey().toString(), r.getValue());
            }
        }
        try {
            cfg.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("[PlayerWarps] Error guardando playerwarps.yml: " + e.getMessage());
        }
    }

    // ==================== API ====================

    public PlayerWarp getWarp(String name) {
        return warps.get(name.toLowerCase());
    }

    public Collection<PlayerWarp> getAllWarps() {
        return warps.values();
    }

    public List<PlayerWarp> getWarpsSorted(SortMode mode) {
        List<PlayerWarp> list = new ArrayList<>(warps.values());
        switch (mode) {
            case RATING:
                list.sort((a, b) -> Double.compare(b.getAverageRating(), a.getAverageRating()));
                break;
            case NEWEST:
                list.sort((a, b) -> Long.compare(b.created, a.created));
                break;
            case CHEAPEST:
                list.sort((a, b) -> Double.compare(a.price, b.price));
                break;
            case VISITS:
            default:
                list.sort((a, b) -> Integer.compare(b.visits, a.visits));
                break;
        }
        return list;
    }

    public List<PlayerWarp> getWarpsByOwner(UUID owner) {
        List<PlayerWarp> list = new ArrayList<>();
        for (PlayerWarp w : warps.values()) {
            if (w.owner.equals(owner)) list.add(w);
        }
        return list;
    }

    public int getMaxWarps(Player player) {
        if (player.hasPermission("coreprotect.admin")) return 10;
        if (player.hasPermission("wardstone.vip.moonlord")) return 5;
        if (player.hasPermission("wardstone.vip.eclipse")) return 4;
        if (player.hasPermission("wardstone.vip.nova")) return 3;
        if (player.hasPermission("wardstone.vip.luna")) return 2;
        return 1;
    }

    public boolean canCreateWarp(Player player) {
        return getWarpsByOwner(player.getUniqueId()).size() < getMaxWarps(player);
    }

    public boolean warpExists(String name) {
        return warps.containsKey(name.toLowerCase());
    }

    public boolean createWarp(Player player, String name) {
        if (warpExists(name)) return false;
        if (!canCreateWarp(player)) return false;
        String cleanName = name.toLowerCase().replaceAll("[^a-z0-9_-]", "");
        if (cleanName.isEmpty() || cleanName.length() > 16) return false;
        PlayerWarp warp = new PlayerWarp(cleanName, player.getUniqueId(), player.getName(),
                player.getLocation().clone(), System.currentTimeMillis(), 0, 0, "", new HashMap<>());
        warps.put(cleanName, warp);
        saveData();
        return true;
    }

    public boolean deleteWarp(Player player, String name) {
        PlayerWarp warp = warps.get(name.toLowerCase());
        if (warp == null) return false;
        if (!warp.owner.equals(player.getUniqueId()) && !player.hasPermission("coreprotect.admin")) return false;
        warps.remove(name.toLowerCase());
        saveData();
        return true;
    }

    public boolean adminDeleteWarp(String name) {
        if (warps.remove(name.toLowerCase()) != null) { saveData(); return true; }
        return false;
    }

    public void incrementVisits(String name) {
        PlayerWarp warp = warps.get(name.toLowerCase());
        if (warp != null) { warp.visits++; saveData(); }
    }

    public void setPrice(String name, double price) {
        PlayerWarp warp = warps.get(name.toLowerCase());
        if (warp != null) { warp.price = Math.max(0, price); saveData(); }
    }

    public void setDescription(String name, String desc) {
        PlayerWarp warp = warps.get(name.toLowerCase());
        if (warp != null) {
            warp.description = desc.length() > 60 ? desc.substring(0, 60) : desc;
            saveData();
        }
    }

    public void addRating(String warpName, UUID player, int stars) {
        PlayerWarp warp = warps.get(warpName.toLowerCase());
        if (warp != null && stars >= 1 && stars <= 5) {
            warp.ratings.put(player, stars);
            saveData();
        }
    }

    public void relocateWarp(String name, Location loc) {
        PlayerWarp warp = warps.get(name.toLowerCase());
        if (warp != null) {
            warp.location.setX(loc.getX());
            warp.location.setY(loc.getY());
            warp.location.setZ(loc.getZ());
            warp.location.setYaw(loc.getYaw());
            warp.location.setPitch(loc.getPitch());
            saveData();
        }
    }

    public int getTotalWarps() { return warps.size(); }

    public int getTotalVisits() { return warps.values().stream().mapToInt(w -> w.visits).sum(); }

    // ==================== SORT MODE ====================

    public enum SortMode { VISITS, RATING, NEWEST, CHEAPEST }

    // ==================== DATA CLASS ====================

    public static class PlayerWarp {
        public final String name;
        public final UUID owner;
        public final String ownerName;
        public final Location location;
        public final long created;
        public int visits;
        public double price;
        public String description;
        public final Map<UUID, Integer> ratings;

        public PlayerWarp(String name, UUID owner, String ownerName, Location location,
                          long created, int visits, double price, String description, Map<UUID, Integer> ratings) {
            this.name = name;
            this.owner = owner;
            this.ownerName = ownerName;
            this.location = location;
            this.created = created;
            this.visits = visits;
            this.price = price;
            this.description = description;
            this.ratings = ratings;
        }

        public double getAverageRating() {
            if (ratings.isEmpty()) return 0;
            return ratings.values().stream().mapToInt(Integer::intValue).average().orElse(0);
        }

        public int getRatingCount() { return ratings.size(); }

        public String getStarsDisplay() {
            double avg = getAverageRating();
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= 5; i++) {
                sb.append(i <= Math.round(avg) ? "§e★" : "§7☆");
            }
            return sb.toString();
        }
    }

    public void shutdown() {
        saveData();
    }
}
