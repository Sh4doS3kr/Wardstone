package com.moonlight.coreprotect.achievements;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class AchievementManager {

    private final CoreProtectPlugin plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;

    // playerUUID -> set of achieved achievement IDs
    private final Map<UUID, Set<String>> playerAchievements = new HashMap<>();

    // All defined achievements
    private final List<Achievement> achievements = new ArrayList<>();

    public AchievementManager(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "achievements.yml");
        loadConfig();
        registerAchievements();
    }

    private void loadConfig() {
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("No se pudo crear achievements.yml: " + e.getMessage());
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

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
        add("survive_raid", "¡No Pasarán!", "Sobrevive a una oleada de mobs");
        add("survive_5_raids", "Veterano de Guerra", "Sobrevive a 5 oleadas");
        add("survive_10_raids", "Leyenda del Servidor", "Sobrevive a 10 oleadas");
    }

    private void add(String id, String title, String description) {
        achievements.add(new Achievement(id, title, description));
    }

    public void loadPlayerData() {
        playerAchievements.clear();
        ConfigurationSection section = dataConfig.getConfigurationSection("players");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(key);
                List<String> achieved = section.getStringList(key);
                playerAchievements.put(playerId, new HashSet<>(achieved));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void savePlayerData() {
        dataConfig.set("players", null);
        for (Map.Entry<UUID, Set<String>> entry : playerAchievements.entrySet()) {
            dataConfig.set("players." + entry.getKey().toString(), new ArrayList<>(entry.getValue()));
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Error al guardar achievements: " + e.getMessage());
        }
    }

    public boolean hasAchievement(UUID playerId, String achievementId) {
        Set<String> set = playerAchievements.get(playerId);
        return set != null && set.contains(achievementId);
    }

    public void grant(Player player, String achievementId) {
        if (hasAchievement(player.getUniqueId(), achievementId)) return;

        Achievement achievement = getAchievement(achievementId);
        if (achievement == null) return;

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

        // Personal description message
        player.sendMessage(ChatColor.GRAY + "  → " + achievement.getDescription());
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
            if (a.getId().equals(id)) return a;
        }
        return null;
    }

    // === Tracking counters (stored per player) ===
    public int getPlayerStat(UUID playerId, String stat) {
        return dataConfig.getInt("stats." + playerId.toString() + "." + stat, 0);
    }

    public void incrementStat(UUID playerId, String stat) {
        int val = getPlayerStat(playerId, stat) + 1;
        dataConfig.set("stats." + playerId.toString() + "." + stat, val);
    }

    public void setPlayerStat(UUID playerId, String stat, int value) {
        dataConfig.set("stats." + playerId.toString() + "." + stat, value);
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

        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
    }
}
