package com.moonlight.coreprotect.achievements;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class AchievementManager {

    private final CoreProtectPlugin plugin;
    private final File jsonFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // playerUUID -> set of achieved achievement IDs
    private final Map<UUID, Set<String>> playerAchievements = new HashMap<>();

    // playerUUID -> stat -> value
    private final Map<UUID, Map<String, Integer>> playerStats = new HashMap<>();

    // All defined achievements
    private final List<Achievement> achievements = new ArrayList<>();

    public AchievementManager(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        this.jsonFile = new File(plugin.getDataFolder(), "achievements.json");
        registerAchievements();
        loadFromJson();
    }

    // ── JSON persistence ──────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void loadFromJson() {
        // Migrate from old YAML if JSON doesn't exist yet
        File oldYaml = new File(plugin.getDataFolder(), "achievements.yml");
        if (!jsonFile.exists() && oldYaml.exists()) {
            migrateFromYaml(oldYaml);
            return;
        }

        if (!jsonFile.exists())
            return;

        try (Reader reader = new InputStreamReader(new FileInputStream(jsonFile), StandardCharsets.UTF_8)) {
            Type rootType = new TypeToken<Map<String, Object>>() {
            }.getType();
            Map<String, Object> root = gson.fromJson(reader, rootType);
            if (root == null)
                return;

            // Load player achievements
            Object playersObj = root.get("players");
            if (playersObj instanceof Map) {
                Map<String, Object> playersMap = (Map<String, Object>) playersObj;
                for (Map.Entry<String, Object> entry : playersMap.entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(entry.getKey());
                        if (entry.getValue() instanceof List) {
                            List<String> list = (List<String>) entry.getValue();
                            playerAchievements.put(uuid, new HashSet<>(list));
                        }
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }

            // Load player stats
            Object statsObj = root.get("stats");
            if (statsObj instanceof Map) {
                Map<String, Object> statsMap = (Map<String, Object>) statsObj;
                for (Map.Entry<String, Object> entry : statsMap.entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(entry.getKey());
                        if (entry.getValue() instanceof Map) {
                            Map<String, Object> raw = (Map<String, Object>) entry.getValue();
                            Map<String, Integer> statMap = new HashMap<>();
                            for (Map.Entry<String, Object> s : raw.entrySet()) {
                                if (s.getValue() instanceof Number) {
                                    statMap.put(s.getKey(), ((Number) s.getValue()).intValue());
                                }
                            }
                            playerStats.put(uuid, statMap);
                        }
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error al cargar achievements.json: " + e.getMessage());
        }
    }

    public void savePlayerData() {
        Map<String, Object> root = new LinkedHashMap<>();

        // Players section
        Map<String, List<String>> playersMap = new LinkedHashMap<>();
        for (Map.Entry<UUID, Set<String>> entry : playerAchievements.entrySet()) {
            playersMap.put(entry.getKey().toString(), new ArrayList<>(entry.getValue()));
        }
        root.put("players", playersMap);

        // Stats section
        Map<String, Map<String, Integer>> statsMap = new LinkedHashMap<>();
        for (Map.Entry<UUID, Map<String, Integer>> entry : playerStats.entrySet()) {
            statsMap.put(entry.getKey().toString(), entry.getValue());
        }
        root.put("stats", statsMap);

        try {
            jsonFile.getParentFile().mkdirs();
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(jsonFile), StandardCharsets.UTF_8)) {
                gson.toJson(root, writer);
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Error al guardar achievements.json: " + e.getMessage());
        }
    }

    /** One-time migration from achievements.yml to JSON */
    private void migrateFromYaml(File yamlFile) {
        plugin.getLogger().info("Migrando achievements.yml -> achievements.json...");
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(yamlFile);

            // Migrate players
            ConfigurationSection players = yaml.getConfigurationSection("players");
            if (players != null) {
                for (String key : players.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(key);
                        List<String> achieved = players.getStringList(key);
                        playerAchievements.put(uuid, new HashSet<>(achieved));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }

            // Migrate stats
            ConfigurationSection stats = yaml.getConfigurationSection("stats");
            if (stats != null) {
                for (String uuidKey : stats.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidKey);
                        ConfigurationSection statSection = stats.getConfigurationSection(uuidKey);
                        if (statSection != null) {
                            Map<String, Integer> statMap = new HashMap<>();
                            for (String stat : statSection.getKeys(false)) {
                                statMap.put(stat, statSection.getInt(stat));
                            }
                            playerStats.put(uuid, statMap);
                        }
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }

            savePlayerData();
            // Rename old file so it's not picked up again
            yamlFile.renameTo(new File(yamlFile.getParentFile(), "achievements.yml.bak"));
            plugin.getLogger().info("Migración completada. Antiguo archivo renombrado a achievements.yml.bak");
        } catch (Exception e) {
            plugin.getLogger().severe("Error migrando YAML a JSON: " + e.getMessage());
        }
    }

    // ── Achievement registration ──────────────────────────────────────────

    private void registerAchievements() {
        // === PRIMEROS PASOS ===
        add("first_core", "Inquilino Nuevo", "Coloca tu primer núcleo");
        add("level_5", "Esto Va En Serio", "Mejora un núcleo a nivel 5");
        add("level_10", "Medio Camino al Cielo", "Mejora un núcleo a nivel 10");
        add("level_15", "Ya Casi Huele a Victoria", "Mejora un núcleo a nivel 15");
        add("level_20", "El Elegido", "Alcanza el nivel máximo (20)");

        // === GASTADOR ===
        add("first_upgrade", "Mi Primera Mejora", "Compra tu primera mejora");
        add("5_upgrades", "Comprador Compulsivo", "Compra 5 mejoras en total");
        add("10_upgrades", "Tarjeta de Crédito Infinita", "Compra 10 mejoras en total");
        add("all_upgrades", "Millonario del Núcleo", "Compra TODAS las mejoras en un núcleo");
        add("spend_100k", "Adiós Ahorros", "Gasta más de $100K en mejoras");
        add("spend_500k", "¿Quién Necesita Dinero?", "Gasta más de $500K en mejoras");
        add("spend_1m", "Bancarrota Feliz", "Gasta más de $1M en mejoras");

        // === SOCIAL ===
        add("first_member", "Necesito Amigos", "Invita a tu primer miembro");
        add("5_members", "Fundador de Clan", "Ten 5 miembros en tu zona");
        add("10_members", "Alcalde del Pueblo", "Ten 10 miembros en tu zona");
        add("invite_rejected", "Friendzoneado", "Intenta invitar a alguien que ya es miembro");

        // === TERRITORIAL ===
        add("2_cores", "Dos Casas, Un Dueño", "Coloca 2 núcleos");
        add("3_cores", "Magnate Inmobiliario", "Coloca 3 núcleos");
        add("5_cores", "Emperador Territorial", "Coloca 5 núcleos");
        add("big_zone", "Mi Jardín es Enorme", "Ten una zona de 80 bloques o más");
        add("huge_zone", "Google Maps Me Detecta", "Ten una zona de 120 bloques o más");
        add("max_zone", "La Muralla China v2", "Ten una zona de 160 bloques");

        // === DEFENSIVO ===
        add("buy_no_explosion", "A Prueba de Creepers", "Compra Anti-Explosión");
        add("buy_no_pvp", "Pacifista Profesional", "Compra Anti-PvP");
        add("buy_no_mobs", "Aracnofobia Curada", "Compra Anti-Mobs");
        add("buy_no_fall", "Superhéroe de Caída Libre", "Compra Sin Caída");
        add("buy_auto_heal", "Wolverine Modo Presupuesto", "Compra Auto-Curación");
        add("buy_speed", "Sonic el Erizo Bootleg", "Compra Velocidad");
        add("buy_no_hunger", "Buffet Infinito", "Compra Sin Hambre");
        add("buy_anti_enderman", "Manos Fuera, Flacucho", "Compra Anti-Enderman");
        add("buy_fixed_time", "Dios del Clima", "Compra Tiempo Fijo");
        add("buy_teleport", "Teletransporte Aprobado", "Compra Teletransporte de Núcleo");
        add("buy_resource_gen", "Empresario Pasivo", "Compra el Generador de Recursos");

        // === BOOST ===
        add("damage_max", "One Punch Man (Casi)", "Llega al nivel máximo de Daño");
        add("health_max", "Tanque Humano", "Llega al nivel máximo de Vida");
        add("both_max", "Literalmente Inmortal", "Maximiza Daño Y Vida");

        // === RAROS / GRACIOSOS ===
        add("break_own_core", "Autodestrucción Activada", "Rompe tu propio núcleo");
        add("move_core", "Mudanza Express", "Mueve un núcleo con mejoras");
        add("visit_other", "Turista Curioso", "Visita la zona de otro jugador");
        add("night_place", "Constructor Nocturno", "Coloca un núcleo de noche");
        add("nether_core", "Jugar con Fuego", "Coloca un núcleo en el Nether");
        add("end_core", "Al Fin del Mundo", "Coloca un núcleo en el End");
        add("rain_place", "Bajo la Lluvia", "Coloca un núcleo mientras llueve");
        add("sethome", "Hogar Dulce Hogar", "Establece tu primer home");
        add("use_tp", "Teletransportador Experto", "Usa /cores tp por primera vez");
        add("resource_collect", "Primer Sueldo Pasivo", "Recoge recursos del generador");
    }

    private void add(String id, String title, String description) {
        achievements.add(new Achievement(id, title, description));
    }

    // ── Public API (unchanged) ────────────────────────────────────────────

    public void loadPlayerData() {
        playerAchievements.clear();
        playerStats.clear();
        loadFromJson();
    }

    public boolean hasAchievement(UUID playerId, String achievementId) {
        Set<String> set = playerAchievements.get(playerId);
        return set != null && set.contains(achievementId);
    }

    public void grant(Player player, String achievementId) {
        if (hasAchievement(player.getUniqueId(), achievementId))
            return;

        Achievement achievement = getAchievement(achievementId);
        if (achievement == null)
            return;

        playerAchievements.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>()).add(achievementId);
        savePlayerData();

        // Vanilla-style chat announcement
        String message = ChatColor.YELLOW + player.getName() + ChatColor.WHITE + " ha conseguido el logro "
                + ChatColor.GREEN + "[" + achievement.getTitle() + "]";

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(message);
        }

        // Sound for the player
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

        // Description message (broadcast to ALL players)
        String descMessage = ChatColor.GRAY + "  → " + achievement.getDescription();
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(descMessage);
        }
    }

    public int getAchievementCount(UUID playerId) {
        Set<String> set = playerAchievements.get(playerId);
        return set != null ? set.size() : 0;
    }

    public int getTotalAchievements() {
        return achievements.size();
    }

    public List<Achievement> getAllAchievements() {
        return Collections.unmodifiableList(achievements);
    }

    public Set<String> getPlayerAchievements(UUID playerId) {
        return playerAchievements.getOrDefault(playerId, Collections.emptySet());
    }

    private Achievement getAchievement(String id) {
        for (Achievement a : achievements) {
            if (a.getId().equals(id))
                return a;
        }
        return null;
    }

    // === Tracking counters (stored per player) ===
    public int getPlayerStat(UUID playerId, String stat) {
        Map<String, Integer> stats = playerStats.get(playerId);
        if (stats == null)
            return 0;
        return stats.getOrDefault(stat, 0);
    }

    public void incrementStat(UUID playerId, String stat) {
        int val = getPlayerStat(playerId, stat) + 1;
        playerStats.computeIfAbsent(playerId, k -> new HashMap<>()).put(stat, val);
    }

    public void setPlayerStat(UUID playerId, String stat, int value) {
        playerStats.computeIfAbsent(playerId, k -> new HashMap<>()).put(stat, value);
    }

    // === Static inner class ===
    public static class Achievement {
        private final String id;
        private final String title;
        private final String description;

        public Achievement(String id, String title, String description) {
            this.id = id;
            this.title = title;
            this.description = description;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }
    }
}
