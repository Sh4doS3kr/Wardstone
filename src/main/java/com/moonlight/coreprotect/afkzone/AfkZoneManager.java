package com.moonlight.coreprotect.afkzone;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Gestiona la zona AFK: definición, persistencia y recompensas.
 */
public class AfkZoneManager {

    private final JavaPlugin plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;

    // Zona definida por dos esquinas
    private String worldName;
    private double x1, y1, z1;
    private double x2, y2, z2;
    private boolean zoneEnabled = false;

    // Tiempo entre recompensas (en segundos)
    public static final int REWARD_INTERVAL_SECONDS = 300; // 5 minutos

    // Recompensas por rareza
    public static final Map<AfkRewardRarity, List<AfkReward>> REWARDS = new LinkedHashMap<>();

    static {
        // COMUN (55%) — dinero decente o llave common
        List<AfkReward> common = new ArrayList<>();
        common.add(new AfkReward("§f§lCOMÚN", 1000, 3000, null));
        common.add(new AfkReward("§f§lCOMÚN", 0, 0, null,
                "excellentcrates key give {player} common 1"));
        common.add(new AfkReward("§f§lCOMÚN", 1500, 2500, null));
        REWARDS.put(AfkRewardRarity.COMUN, common);

        // POCO COMUN (25%) — más dinero o llave common + diamantes
        List<AfkReward> uncommon = new ArrayList<>();
        uncommon.add(new AfkReward("§a§lPOCO COMÚN", 3000, 6000, null));
        uncommon.add(new AfkReward("§a§lPOCO COMÚN", 0, 0, null,
                "excellentcrates key give {player} common 2"));
        uncommon.add(new AfkReward("§a§lPOCO COMÚN", 2000, 4000, buildItems("DIAMOND:2")));
        REWARDS.put(AfkRewardRarity.POCO_COMUN, uncommon);

        // RARO (13%) — buen dinero o llave rare
        List<AfkReward> rare = new ArrayList<>();
        rare.add(new AfkReward("§b§lRARO", 6000, 12000, null));
        rare.add(new AfkReward("§b§lRARO", 0, 0, null,
                "excellentcrates key give {player} rare 1"));
        rare.add(new AfkReward("§b§lRARO", 3000, 6000, buildItems("DIAMOND:4", "EMERALD:4")));
        REWARDS.put(AfkRewardRarity.RARO, rare);

        // EPICO (5%) — mucho dinero o llave special
        List<AfkReward> epic = new ArrayList<>();
        epic.add(new AfkReward("§d§lÉPICO", 12000, 25000, null));
        epic.add(new AfkReward("§d§lÉPICO", 0, 0, null,
                "excellentcrates key give {player} special 1"));
        epic.add(new AfkReward("§d§lÉPICO", 5000, 10000, buildItems("DIAMOND:6", "NETHERITE_SCRAP:1")));
        REWARDS.put(AfkRewardRarity.EPICO, epic);

        // LEGENDARIO (2%) — jackpot: dinero o llave legendary
        List<AfkReward> legendary = new ArrayList<>();
        legendary.add(new AfkReward("§6§lLEGENDARIO", 25000, 50000, null));
        legendary.add(new AfkReward("§6§lLEGENDARIO", 0, 0, null,
                "excellentcrates key give {player} legendary 1"));
        legendary.add(new AfkReward("§6§lLEGENDARIO", 10000, 20000,
                buildItems("NETHERITE_INGOT:1", "DIAMOND:8")));
        REWARDS.put(AfkRewardRarity.LEGENDARIO, legendary);
    }

    private static List<String> buildItems(String... entries) {
        return Arrays.asList(entries);
    }

    public AfkZoneManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "afkzone.yml");
        load();
    }

    public void load() {
        if (!dataFile.exists())
            return;
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        if (!dataConfig.contains("zone.world"))
            return;

        worldName = dataConfig.getString("zone.world");
        x1 = dataConfig.getDouble("zone.x1");
        y1 = dataConfig.getDouble("zone.y1");
        z1 = dataConfig.getDouble("zone.z1");
        x2 = dataConfig.getDouble("zone.x2");
        y2 = dataConfig.getDouble("zone.y2");
        z2 = dataConfig.getDouble("zone.z2");
        zoneEnabled = dataConfig.getBoolean("zone.enabled", true);
    }

    public void save() {
        if (dataConfig == null)
            dataConfig = new YamlConfiguration();
        dataConfig.set("zone.world", worldName);
        dataConfig.set("zone.x1", x1);
        dataConfig.set("zone.y1", y1);
        dataConfig.set("zone.z1", z1);
        dataConfig.set("zone.x2", x2);
        dataConfig.set("zone.y2", y2);
        dataConfig.set("zone.z2", z2);
        dataConfig.set("zone.enabled", zoneEnabled);
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[AfkZone] No se pudo guardar afkzone.yml: " + e.getMessage());
        }
    }

    public void setZone(Location pos1, Location pos2) {
        this.worldName = pos1.getWorld().getName();
        this.x1 = Math.min(pos1.getX(), pos2.getX());
        this.y1 = Math.min(pos1.getY(), pos2.getY());
        this.z1 = Math.min(pos1.getZ(), pos2.getZ());
        this.x2 = Math.max(pos1.getX(), pos2.getX());
        this.y2 = Math.max(pos1.getY(), pos2.getY());
        this.z2 = Math.max(pos1.getZ(), pos2.getZ());
        this.zoneEnabled = true;
        save();
    }

    public boolean isInZone(Location loc) {
        if (!zoneEnabled || worldName == null)
            return false;
        World w = Bukkit.getWorld(worldName);
        if (w == null || !loc.getWorld().getName().equals(worldName))
            return false;
        double x = loc.getX(), y = loc.getY(), z = loc.getZ();
        return x >= x1 && x <= x2 && y >= y1 && y <= y2 && z >= z1 && z <= z2;
    }

    public boolean isZoneEnabled() {
        return zoneEnabled;
    }

    public boolean isZoneDefined() {
        return worldName != null;
    }

    public String getZoneInfo() {
        if (!isZoneDefined())
            return "§cNo definida";
        return String.format("§7(%s) §f%.0f,%.0f,%.0f §7→ §f%.0f,%.0f,%.0f",
                worldName, x1, y1, z1, x2, y2, z2);
    }

    /**
     * Sortea una rareza basada en probabilidades.
     */
    public static AfkRewardRarity rollRarity() {
        double roll = Math.random() * 100;
        if (roll < 2)
            return AfkRewardRarity.LEGENDARIO;
        if (roll < 7)
            return AfkRewardRarity.EPICO;
        if (roll < 20)
            return AfkRewardRarity.RARO;
        if (roll < 45)
            return AfkRewardRarity.POCO_COMUN;
        return AfkRewardRarity.COMUN;
    }

    /**
     * Obtiene una recompensa aleatoria de una rareza.
     */
    public static AfkReward getRandomReward(AfkRewardRarity rarity) {
        List<AfkReward> list = REWARDS.get(rarity);
        return list.get((int) (Math.random() * list.size()));
    }
}
