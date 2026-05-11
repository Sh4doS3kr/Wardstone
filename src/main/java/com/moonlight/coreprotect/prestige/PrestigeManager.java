package com.moonlight.coreprotect.prestige;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class PrestigeManager {

    private final CoreProtectPlugin plugin;
    private final File dataFile;
    private FileConfiguration data;
    private final Map<UUID, PrestigeData> players = new HashMap<>();

    public static final String GUI_TITLE = "§8✦ §c§lPrestigios §8✦";
    public static final String MISSIONS_TITLE = "§8✦ §c§lMisiones de Prestigio §8✦";
    public static final String TOP_TITLE = "§8✦ §e§lTop Prestigios §8✦";
    public static final String STAFF_MISSIONS_PREFIX = "§8[STAFF] §c§lMisiones P";
    public static final int MAX_PRESTIGE = 70;

    // Nexo glyph tags for rank icons — Nexo replaces <glyph:rankN> in packets
    private static final String[] RANK_ICONS;
    static {
        RANK_ICONS = new String[MAX_PRESTIGE + 1];
        RANK_ICONS[0] = "<glyph:rank1>"; // sin rango = same icon as rank1 (1.png)
        for (int i = 1; i <= MAX_PRESTIGE; i++) {
            RANK_ICONS[i] = "<glyph:rank" + i + ">";
        }
    }

    // Tier names for GUI display (not shown in chat — only the icon is)
    // Copper 1-10, Iron 11-20, Gold 21-30, Lapis 31-40, Emerald 41-50, Diamond 51-60, Amethyst 61-70
    private static String getTierName(int prestige) {
        if (prestige <= 0) return "§7Sin Rango";
        if (prestige <= 10) return "§6Cobre " + toRoman(prestige);
        if (prestige <= 20) return "§fHierro " + toRoman(prestige - 10);
        if (prestige <= 30) return "§eOro " + toRoman(prestige - 20);
        if (prestige <= 40) return "§9Lapis " + toRoman(prestige - 30);
        if (prestige <= 50) return "§aEsmeralda " + toRoman(prestige - 40);
        if (prestige <= 60) return "§bDiamante " + toRoman(prestige - 50);
        return "§dAmatista " + toRoman(prestige - 60);
    }

    private static String toRoman(int n) {
        String[] r = {"I","II","III","IV","V","VI","VII","VIII","IX","X"};
        return n >= 1 && n <= 10 ? r[n - 1] : String.valueOf(n);
    }

    // Primary color per tier
    private static String getTierColor(int prestige) {
        if (prestige <= 0) return "§7";
        if (prestige <= 10) return "§6";
        if (prestige <= 20) return "§f";
        if (prestige <= 30) return "§e";
        if (prestige <= 40) return "§9";
        if (prestige <= 50) return "§a";
        if (prestige <= 60) return "§b";
        return "§d";
    }

    public PrestigeManager(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "prestiges.yml");
        this.data = YamlConfiguration.loadConfiguration(dataFile);
        loadData();
    }

    // ===========================
    // DATA
    // ===========================

    private void loadData() {
        if (!data.contains("players")) return;
        for (String uuidStr : data.getConfigurationSection("players").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                PrestigeData pd = new PrestigeData();
                pd.prestige = data.getInt("players." + uuidStr + ".prestige", 1);
                pd.completedMissions = new HashSet<>(data.getStringList("players." + uuidStr + ".completed"));
                pd.bestKillstreak = data.getInt("players." + uuidStr + ".bestKillstreak", 0);
                pd.prestigeKills = data.getInt("players." + uuidStr + ".prestigeKills", 0);
                pd.discoveredBiomes = new HashSet<>(data.getStringList("players." + uuidStr + ".biomes"));
                pd.totalDonated = data.getLong("players." + uuidStr + ".totalDonated", 0);
                // Co-op fields
                pd.coopMineBlocks = data.getLong("players." + uuidStr + ".coopMineBlocks", 0);
                pd.coopKills = data.getLong("players." + uuidStr + ".coopKills", 0);
                pd.coopAssists = data.getLong("players." + uuidStr + ".coopAssists", 0);
                pd.healedAllies = data.getLong("players." + uuidStr + ".healedAllies", 0);
                pd.buddyDistanceCm = data.getLong("players." + uuidStr + ".buddyDistanceCm", 0);
                pd.coopFished = data.getLong("players." + uuidStr + ".coopFished", 0);
                pd.coopBossKills = data.getLong("players." + uuidStr + ".coopBossKills", 0);
                pd.coopEnchants = data.getLong("players." + uuidStr + ".coopEnchants", 0);
                pd.syncKills = data.getLong("players." + uuidStr + ".syncKills", 0);
                pd.giftedUniquePlayers = new HashSet<>(data.getStringList("players." + uuidStr + ".giftedUnique"));
                pd.extraWalkCm = data.getLong("players." + uuidStr + ".extraWalkCm", 0);
                players.put(uuid, pd);
            } catch (Exception ignored) {}
        }
    }

    public void saveData() {
        for (Map.Entry<UUID, PrestigeData> e : players.entrySet()) {
            String path = "players." + e.getKey().toString();
            data.set(path + ".prestige", e.getValue().prestige);
            data.set(path + ".completed", new ArrayList<>(e.getValue().completedMissions));
            data.set(path + ".bestKillstreak", e.getValue().bestKillstreak);
            data.set(path + ".prestigeKills", e.getValue().prestigeKills);
            data.set(path + ".biomes", new ArrayList<>(e.getValue().discoveredBiomes));
            data.set(path + ".totalDonated", e.getValue().totalDonated);
            // Co-op fields
            data.set(path + ".coopMineBlocks", e.getValue().coopMineBlocks);
            data.set(path + ".coopKills", e.getValue().coopKills);
            data.set(path + ".coopAssists", e.getValue().coopAssists);
            data.set(path + ".healedAllies", e.getValue().healedAllies);
            data.set(path + ".buddyDistanceCm", e.getValue().buddyDistanceCm);
            data.set(path + ".coopFished", e.getValue().coopFished);
            data.set(path + ".coopBossKills", e.getValue().coopBossKills);
            data.set(path + ".coopEnchants", e.getValue().coopEnchants);
            data.set(path + ".syncKills", e.getValue().syncKills);
            data.set(path + ".giftedUnique", new ArrayList<>(e.getValue().giftedUniquePlayers));
            data.set(path + ".extraWalkCm", e.getValue().extraWalkCm);
        }
        try { data.save(dataFile); }
        catch (IOException e) { plugin.getLogger().severe("Error guardando prestiges.yml: " + e.getMessage()); }
    }

    public void shutdown() { saveData(); }

    public PrestigeData getData(UUID uuid) {
        return players.computeIfAbsent(uuid, k -> new PrestigeData());
    }

    public int getPrestige(UUID uuid) { return getData(uuid).prestige; }

    public String getTitle(UUID uuid) {
        int p = Math.min(getPrestige(uuid), MAX_PRESTIGE);
        return RANK_ICONS[p];
    }

    public String getColor(UUID uuid) {
        int p = Math.min(getPrestige(uuid), MAX_PRESTIGE);
        return getTierColor(p);
    }

    public String getShortTag(UUID uuid) {
        int p = Math.min(getPrestige(uuid), MAX_PRESTIGE);
        return RANK_ICONS[p];
    }

    // ===========================
    // MISSIONS
    // ===========================

    public List<PrestigeMission> getMissionsForPrestige(int prestige) {
        List<PrestigeMission> missions = new ArrayList<>();
        int p = prestige + 1; // The NEXT prestige to earn

        // ══════════════════════════════════════
        // P1-29: PROGRESIÓN TRANQUILA (lineal)
        // ══════════════════════════════════════
        if (p <= 29) {
            double s = 1.0 + (p - 1) * 0.3; // suave: P1=x1, P10=x3.7, P20=x6.7, P29=x9.4

            missions.add(new PrestigeMission("mine_" + p, "Minar " + formatNumber(Math.round(500 * s)) + " bloques", "mine", Math.round(500 * s)));
            missions.add(new PrestigeMission("walk_" + p, "Caminar " + formatNumber(Math.round(1000 * s)) + " bloques", "walk", Math.round(1000 * s)));

            if (p >= 3)  missions.add(new PrestigeMission("place_" + p, "Colocar " + formatNumber(Math.round(200 * s)) + " bloques", "place", Math.round(200 * s)));
            if (p >= 5)  missions.add(new PrestigeMission("enchant_" + p, "Encantar " + formatNumber(Math.round(3 * s)) + " items", "enchant", Math.round(3 * s)));
            if (p >= 8)  missions.add(new PrestigeMission("mine_dia_" + p, "Minar " + formatNumber(Math.round(5 * s)) + " diamantes", "mine_diamond", Math.round(5 * s)));
            if (p >= 10) missions.add(new PrestigeMission("fish_" + p, "Pescar " + formatNumber(Math.round(5 * s)) + " peces", "fish", Math.round(5 * s)));
            if (p >= 12) missions.add(new PrestigeMission("trade_" + p, "Comerciar " + formatNumber(Math.round(5 * s)) + " veces con aldeanos", "trade", Math.round(5 * s)));
            if (p >= 15) missions.add(new PrestigeMission("craft_" + p, "Craftear " + formatNumber(Math.round(3 * s)) + " items de diamante", "craft", Math.round(3 * s)));
            if (p >= 18) missions.add(new PrestigeMission("brew_" + p, "Preparar " + formatNumber(Math.round(5 * s)) + " pociones", "brew", Math.round(5 * s)));
            if (p >= 20) missions.add(new PrestigeMission("sprint_" + p, "Correr " + formatNumber(Math.round(2000 * s)) + " bloques", "sprint", Math.round(2000 * s)));
            if (p >= 22) missions.add(new PrestigeMission("elytra_" + p, "Volar " + formatNumber(Math.round(2000 * s)) + " bloques con Elytra", "fly_elytra", Math.round(2000 * s)));
            if (p >= 25) missions.add(new PrestigeMission("eat_" + p, "Comer " + formatNumber(Math.round(30 * s)) + " alimentos", "eat", Math.round(30 * s)));
            if (p >= 28) missions.add(new PrestigeMission("swim_" + p, "Nadar " + formatNumber(Math.round(300 * s)) + " bloques", "swim", Math.round(300 * s)));

            return missions;
        }

        // ══════════════════════════════════════════════
        // P30-39: TRANSICIÓN DURA — misiones custom
        // ══════════════════════════════════════════════
        if (p <= 39) {
            double s = 1.0 + (p - 1) * 0.5; // más agresivo: P30=x15.5, P35=x18, P39=x20

            // ── Base siempre ──
            missions.add(new PrestigeMission("mine_" + p, "Minar " + formatNumber(Math.round(800 * s)) + " bloques", "mine", Math.round(800 * s)));
            missions.add(new PrestigeMission("kill_" + p, "Matar " + formatNumber(Math.round(80 * s)) + " mobs", "kill_mob", Math.round(80 * s)));
            missions.add(new PrestigeMission("mine_dia_" + p, "Minar " + formatNumber(Math.round(10 * s)) + " diamantes", "mine_diamond", Math.round(10 * s)));
            missions.add(new PrestigeMission("enchant_" + p, "Encantar " + formatNumber(Math.round(8 * s)) + " items", "enchant", Math.round(8 * s)));
            missions.add(new PrestigeMission("craft_" + p, "Craftear " + formatNumber(Math.round(5 * s)) + " items de diamante/netherita", "craft", Math.round(5 * s)));
            missions.add(new PrestigeMission("dmg_" + p, "Hacer " + formatNumber(Math.round(800 * s)) + " de daño total", "damage_dealt", Math.round(800 * s)));

            // ── Custom P30+ ──
            missions.add(new PrestigeMission("mine_neth_" + p, "Minar " + formatNumber(Math.round(3 * s)) + " Ancient Debris", "mine_netherite", Math.round(3 * s)));
            missions.add(new PrestigeMission("elytra_" + p, "Volar " + formatNumber(Math.round(3000 * s)) + " bloques con Elytra", "fly_elytra", Math.round(3000 * s)));
            missions.add(new PrestigeMission("fish_" + p, "Pescar " + formatNumber(Math.round(8 * s)) + " peces", "fish", Math.round(8 * s)));
            missions.add(new PrestigeMission("eat_" + p, "Comer " + formatNumber(Math.round(50 * s)) + " alimentos", "eat", Math.round(50 * s)));
            missions.add(new PrestigeMission("swim_" + p, "Nadar " + formatNumber(Math.round(500 * s)) + " bloques", "swim", Math.round(500 * s)));

            // ── Custom P32+: Resistencia ──
            if (p >= 32) missions.add(new PrestigeMission("tank_" + p, "Recibir " + formatNumber(Math.round(600 * s)) + " de daño", "damage_taken", Math.round(600 * s)));

            // ── Custom P33+: Racha diaria ──
            if (p >= 33) {
                int streakTarget = Math.min(15, 3 + (p - 33));
                missions.add(new PrestigeMission("streak_" + p, "Racha diaria de " + streakTarget + " días", "streak", streakTarget));
            }

            // ── Custom P35+: Matar Wither ──
            if (p >= 35) missions.add(new PrestigeMission("wither_" + p, "Matar al Wither 1 vez", "kill_wither", 1));

            // ── Custom P36+: Tiempo jugado ──
            if (p >= 36) {
                long playTarget = 3600L * (2 + (p - 36) * 2); // 2h, 4h, 6h, 8h
                missions.add(new PrestigeMission("play_" + p, "Jugar " + formatTime(playTarget), "playtime", playTarget));
            }

            // ── Custom P37+: Dinero ──
            if (p >= 37) {
                missions.add(new PrestigeMission("money_" + p, "Tener $" + formatNumber(Math.round(5000 * s)), "earn_money", Math.round(5000 * s)));
            }

            // ── Custom P38+: Matar Ender Dragon ──
            if (p >= 38) missions.add(new PrestigeMission("dragon_" + p, "Matar al Ender Dragon 1 vez", "kill_ender_dragon", 1));

            return missions;
        }

        // ══════════════════════════════════════════════
        // P70 — DESAFÍO FINAL (difícil pero humano)
        // ══════════════════════════════════════════════
        if (p == 70) {
            missions.add(new PrestigeMission("mine_70", "Minar 50,000 bloques", "mine", 50000));
            missions.add(new PrestigeMission("kill_70", "Matar 5,000 mobs", "kill_mob", 5000));
            missions.add(new PrestigeMission("mine_dia_70", "Minar 500 diamantes", "mine_diamond", 500));
            missions.add(new PrestigeMission("mine_neth_70", "Minar 300 Ancient Debris", "mine_netherite", 300));
            missions.add(new PrestigeMission("enchant_70", "Encantar 200 items", "enchant", 200));
            missions.add(new PrestigeMission("craft_70", "Craftear 100 items de diamante/netherita", "craft", 100));
            missions.add(new PrestigeMission("dmg_70", "Hacer 50,000 de daño total", "damage_dealt", 50000));
            missions.add(new PrestigeMission("tank_70", "Recibir 30,000 de daño", "damage_taken", 30000));
            missions.add(new PrestigeMission("dragon_70", "Matar al Ender Dragon 3 veces", "kill_ender_dragon", 3));
            missions.add(new PrestigeMission("wither_70", "Matar al Wither 5 veces", "kill_wither", 5));
            missions.add(new PrestigeMission("streak_70", "Racha diaria de 45 días", "streak", 45));
            missions.add(new PrestigeMission("play_70", "Jugar " + formatTime(1814400L), "playtime", 1814400L)); // 21 días
            missions.add(new PrestigeMission("money_70", "Tener $500,000", "earn_money", 500000));
            missions.add(new PrestigeMission("elytra_70", "Volar 200,000 bloques con Elytra", "fly_elytra", 200000));
            missions.add(new PrestigeMission("fish_70", "Pescar 300 peces", "fish", 300));
            missions.add(new PrestigeMission("swim_70", "Nadar 20,000 bloques", "swim", 20000));
            missions.add(new PrestigeMission("eat_70", "Comer 500 alimentos", "eat", 500));
            missions.add(new PrestigeMission("sprint_70", "Correr 100,000 bloques", "sprint", 100000));
            missions.add(new PrestigeMission("donate_70", "Donar $100,000 a jugadores con /pay", "donate", 100000));
            missions.add(new PrestigeMission("biome_70", "Descubrir 30 biomas distintos", "biomes", 30));
            missions.add(new PrestigeMission("survive_70", "Sobrevivir 180 minutos sin morir", "survive", 180));
            // ── CO-OP Missions P70 ──
            missions.add(new PrestigeMission("coop_mine_70", "§d⛏ [CO-OP] §fMinar 20,000 bloques junto a un amigo", "coop_mine", 20000));
            missions.add(new PrestigeMission("coop_kill_70", "§d⚔ [CO-OP] §fEliminar 1,200 mobs junto a un amigo", "coop_kill", 1200));
            missions.add(new PrestigeMission("coop_assist_70", "§d🤝 [CO-OP] §fAsistir en 500 kills entre dos", "coop_assist", 500));
            missions.add(new PrestigeMission("sync_kill_70", "§d⚡ [CO-OP] §fKills sincronizados 200 veces con un amigo", "sync_kill", 200));
            missions.add(new PrestigeMission("coop_boss_70", "§d💀 [CO-OP] §fMatar al Wither o Dragón con amigos 5 veces", "coop_boss", 5));
            missions.add(new PrestigeMission("gift_unique_70", "§d🎁 [CO-OP] §fRegalar dinero a 20 jugadores distintos con /pay", "gift_unique", 20));
            return missions;
        }

        // ══════════════════════════════════════
        // P40-69 — MISIONES BRUTALES + CUSTOM
        // ══════════════════════════════════════
        double hardScale = Math.pow(1.15, p - 40); // exponencial fuerte desde P40
        double tierMult = 1.0;
        if (p > 50) tierMult = 1.5;    // Diamante+
        if (p > 60) tierMult = 1.8;    // Amatista
        double s = hardScale * tierMult;

        // ── Base (siempre) ──
        missions.add(new PrestigeMission("mine_" + p, "Minar " + formatNumber(Math.round(8000 * s)) + " bloques", "mine", Math.round(8000 * s)));
        missions.add(new PrestigeMission("kill_" + p, "Matar " + formatNumber(Math.round(800 * s)) + " mobs", "kill_mob", Math.round(800 * s)));
        missions.add(new PrestigeMission("walk_" + p, "Caminar " + formatNumber(Math.round(80000 * s)) + " bloques", "walk", Math.round(80000 * s)));
        missions.add(new PrestigeMission("mine_dia_" + p, "Minar " + formatNumber(Math.round(80 * s)) + " diamantes", "mine_diamond", Math.round(80 * s)));
        missions.add(new PrestigeMission("mine_neth_" + p, "Minar " + formatNumber(Math.round(10 * s)) + " Ancient Debris", "mine_netherite", Math.round(10 * s)));
        missions.add(new PrestigeMission("enchant_" + p, "Encantar " + formatNumber(Math.round(40 * s)) + " items", "enchant", Math.round(40 * s)));

        // ── Custom: Sprint extremo ──
        missions.add(new PrestigeMission("sprint_" + p, "Correr " + formatNumber(Math.round(60000 * s)) + " bloques", "sprint", Math.round(60000 * s)));

        // ── Custom: Crafting de élite ──
        if (p >= 42) {
            missions.add(new PrestigeMission("craft_" + p, "Craftear " + formatNumber(Math.round(20 * s)) + " items de diamante/netherita", "craft", Math.round(20 * s)));
        }

        // ── Custom: Daño total ──
        if (p >= 43) {
            missions.add(new PrestigeMission("dmg_" + p, "Hacer " + formatNumber(Math.round(3000 * s)) + " de daño total", "damage_dealt", Math.round(3000 * s)));
        }

        // ── Custom: Preparar pociones ──
        if (p >= 44) {
            missions.add(new PrestigeMission("brew_" + p, "Preparar " + formatNumber(Math.round(30 * s)) + " pociones", "brew", Math.round(30 * s)));
        }

        // ── Custom: Comerciar con aldeanos ──
        if (p >= 47) {
            missions.add(new PrestigeMission("trade_" + p, "Comerciar " + formatNumber(Math.round(50 * s)) + " veces con aldeanos", "trade", Math.round(50 * s)));
        }

        // ── Custom: Colocar bloques ──
        if (p >= 50) {
            missions.add(new PrestigeMission("place_" + p, "Colocar " + formatNumber(Math.round(5000 * s)) + " bloques", "place", Math.round(5000 * s)));
        }

        // ── Custom: Matar Endermen ──
        if (p >= 52) {
            missions.add(new PrestigeMission("kill_enderman_" + p, "Matar " + formatNumber(Math.round(40 * s)) + " Endermen", "kill_enderman", Math.round(40 * s)));
        }

        // ── Custom: Matar Blazes ──
        if (p >= 56) {
            missions.add(new PrestigeMission("kill_blaze_" + p, "Matar " + formatNumber(Math.round(60 * s)) + " Blazes", "kill_blaze", Math.round(60 * s)));
        }

        // ── Custom: Usar Tótems ──
        if (p >= 63) {
            missions.add(new PrestigeMission("totem_" + p, "Usar " + formatNumber(Math.round(3 * s)) + " Tótems de Inmortalidad", "totem", Math.round(3 * s)));
        }

        // ── Custom: Racha diaria ──
        if (p >= 45) {
            int streakTarget = Math.min(60, 5 + (p - 45));
            missions.add(new PrestigeMission("streak_" + p, "Racha diaria de " + streakTarget + " días", "streak", streakTarget));
        }

        // ── Custom: Pesca masiva ──
        if (p >= 46) {
            missions.add(new PrestigeMission("fish_" + p, "Pescar " + formatNumber(Math.round(80 * s)) + " peces", "fish", Math.round(80 * s)));
        }

        // ── Custom: Resistencia ──
        if (p >= 48) {
            missions.add(new PrestigeMission("tank_" + p, "Recibir " + formatNumber(Math.round(5000 * s)) + " de daño", "damage_taken", Math.round(5000 * s)));
        }

        // ── Custom: Matar Wither ──
        if (p >= 50) {
            int witherTarget = Math.max(1, (p - 49));
            missions.add(new PrestigeMission("wither_" + p, "Matar al Wither " + witherTarget + (witherTarget > 1 ? " veces" : " vez"), "kill_wither", witherTarget));
        }

        // ── Custom: Nadar ──
        if (p >= 50) {
            missions.add(new PrestigeMission("swim_" + p, "Nadar " + formatNumber(Math.round(15000 * s)) + " bloques", "swim", Math.round(15000 * s)));
        }

        // ── Custom: Tiempo de juego ──
        if (p >= 52) {
            long playTarget = 86400L * Math.max(1, (p - 51));
            missions.add(new PrestigeMission("play_" + p, "Jugar " + formatTime(playTarget), "playtime", playTarget));
        }

        // ── Custom: Dinero acumulado ──
        if (p >= 53) {
            missions.add(new PrestigeMission("money_" + p, "Tener $" + formatNumber(Math.round(150000 * s)), "earn_money", Math.round(150000 * s)));
        }

        // ── Custom: Vuelo con Elytra ──
        if (p >= 55) {
            missions.add(new PrestigeMission("elytra_" + p, "Volar " + formatNumber(Math.round(30000 * s)) + " bloques con Elytra", "fly_elytra", Math.round(30000 * s)));
        }

        // ── Custom: Matar Ender Dragon ──
        if (p >= 58) {
            int dragonTarget = Math.max(1, (p - 57));
            missions.add(new PrestigeMission("dragon_" + p, "Matar al Ender Dragon " + dragonTarget + (dragonTarget > 1 ? " veces" : " vez"), "kill_ender_dragon", dragonTarget));
        }

        // ── Custom: Comer masivo ──
        if (p >= 55) {
            missions.add(new PrestigeMission("eat_" + p, "Comer " + formatNumber(Math.round(300 * s)) + " alimentos", "eat", Math.round(300 * s)));
        }

        // ── Custom: Muertes (sacrificio) ──
        if (p >= 60) {
            missions.add(new PrestigeMission("deaths_" + p, "Morir " + formatNumber(Math.round(80 * s)) + " veces", "deaths", Math.round(80 * s)));
        }

        // ── Custom: Agacharse (sigilo) ──
        if (p >= 62) {
            missions.add(new PrestigeMission("crouch_" + p, "Agacharse " + formatNumber(Math.round(8000 * s)) + " bloques", "crouch", Math.round(8000 * s)));
        }

        // ── Custom: Saltos extremos ──
        if (p >= 65) {
            missions.add(new PrestigeMission("jump_" + p, "Saltar " + formatNumber(Math.round(80000 * s)) + " veces", "jump", Math.round(80000 * s)));
        }

        // ── Custom: Donar dinero a jugadores ──
        if (p >= 42) {
            long donateTarget = Math.round(5000 * s);
            missions.add(new PrestigeMission("donate_" + p, "Donar $" + formatNumber(donateTarget) + " a jugadores con /pay", "donate", donateTarget));
        }

        // ── Custom: Descubrir biomas ──
        if (p >= 45) {
            int biomeTarget = Math.min(30, 8 + (p - 45));
            missions.add(new PrestigeMission("biome_" + p, "Descubrir " + biomeTarget + " biomas distintos", "biomes", biomeTarget));
        }

        // ── Custom: Sobrevivir sin morir ──
        if (p >= 50) {
            int surviveMin = Math.min(240, 30 + (p - 50) * 10);
            missions.add(new PrestigeMission("survive_" + p, "Sobrevivir " + surviveMin + " minutos sin morir", "survive", surviveMin));
        }

        // ── CO-OP Missions P40+ ──
        missions.add(new PrestigeMission("coop_mine_" + p, "§d⛏ [CO-OP] §fMinar " + formatNumber(Math.round(3000 * s)) + " bloques junto a un amigo", "coop_mine", Math.round(3000 * s)));
        missions.add(new PrestigeMission("coop_kill_" + p, "§d⚔ [CO-OP] §fEliminar " + formatNumber(Math.round(120 * s)) + " mobs junto a un amigo", "coop_kill", Math.round(120 * s)));
        missions.add(new PrestigeMission("coop_assist_" + p, "§d🤝 [CO-OP] §fAsistir en " + formatNumber(Math.round(60 * s)) + " kills entre dos", "coop_assist", Math.round(60 * s)));
        missions.add(new PrestigeMission("buddy_walk_" + p, "§d🚶 [CO-OP] §fExplorar " + formatNumber(Math.round(10000 * s)) + " bloques junto a un amigo", "buddy_walk", Math.round(10000 * s)));
        if (p >= 42) missions.add(new PrestigeMission("coop_enchant_" + p, "§d✨ [CO-OP] §fEncantar " + formatNumber(Math.round(10 * s)) + " items junto a un amigo", "coop_enchant", Math.round(10 * s)));
        if (p >= 45) missions.add(new PrestigeMission("sync_kill_" + p, "§d⚡ [CO-OP] §fKills sincronizados " + formatNumber(Math.round(30 * s)) + " veces con un amigo", "sync_kill", Math.round(30 * s)));
        if (p >= 48) missions.add(new PrestigeMission("coop_fish_" + p, "§d🎣 [CO-OP] §fPescar " + formatNumber(Math.round(20 * s)) + " peces junto a un amigo", "coop_fish", Math.round(20 * s)));
        if (p >= 50) missions.add(new PrestigeMission("coop_boss_" + p, "§d💀 [CO-OP] §fMatar al Wither o Dragón con amigos " + Math.max(1, (p - 49)) + (p > 50 ? " veces" : " vez"), "coop_boss", Math.max(1, (p - 49))));
        if (p >= 50) missions.add(new PrestigeMission("heal_ally_" + p, "§d❤ [CO-OP] §fCurar a amigos " + formatNumber(Math.round(30 * s)) + " veces con pociones splash", "heal_ally", Math.round(30 * s)));
        if (p >= 55) {
            int giftTarget = Math.min(20, 5 + (p - 55));
            missions.add(new PrestigeMission("gift_unique_" + p, "§d🎁 [CO-OP] §fRegalar dinero a " + giftTarget + " jugadores distintos con /pay", "gift_unique", giftTarget));
        }

        return missions;
    }

    private static boolean isCoopMissionType(String type) {
        return type.startsWith("coop_") || type.equals("heal_ally")
                || type.equals("buddy_walk") || type.equals("sync_kill")
                || type.equals("gift_unique");
    }

    private static String getCoopHint(String type) {
        return switch (type) {
            case "coop_mine"    -> "Mina bloques mientras un amigo esté a menos de 20 bloques de ti.";
            case "coop_kill"    -> "Mata mobs mientras un amigo esté a menos de 20 bloques de ti.";
            case "coop_assist"  -> "Tú y un amigo debéis dañar al mismo mob antes de que muera.";
            case "heal_ally"    -> "Lanza pociones splash de curación que afecten a otro jugador.";
            case "buddy_walk"   -> "Camina o corre mientras un amigo esté a menos de 30 bloques.";
            case "coop_fish"    -> "Pesca mientras un amigo esté a menos de 20 bloques de ti.";
            case "coop_enchant" -> "Encanta items mientras un amigo esté a menos de 20 bloques.";
            case "sync_kill"    -> "Mata un mob justo cuando un amigo cercano (<20 bloques) también mata uno en menos de 5 segundos.";
            case "coop_boss"    -> "Mata al Wither o Ender Dragon con amigos a menos de 50 bloques.";
            case "gift_unique"  -> "Usa /pay para enviar dinero a jugadores distintos.";
            default             -> "Necesitas un amigo cerca para completarla.";
        };
    }

    private String formatTime(long seconds) {
        if (seconds >= 86400) return String.format("%.0f días", seconds / 86400.0);
        if (seconds >= 3600) return String.format("%.0f horas", seconds / 3600.0);
        return seconds + " segundos";
    }

    public PrestigeReward getRewardForPrestige(int prestige) {
        if (prestige < 1 || prestige > MAX_PRESTIGE)
            return new PrestigeReward(0, 0, "common", 0, RANK_ICONS[0]);

        // Money: starts at 5000, scales smoothly — P40+ massive boost
        int money;
        if (prestige <= 39) {
            money = (int)(5000 * Math.pow(1.08, prestige - 1));
        } else {
            money = (int)(5000 * Math.pow(1.08, 38) * Math.pow(1.15, prestige - 39));
        }

        // Essences: P1-39 normal, P40+ boosted
        int essences;
        if (prestige <= 39) {
            essences = 3 + (prestige * 2);
        } else {
            essences = 81 + (prestige - 39) * 8; // 81 base + 8 per level
        }

        // Key type progresses through tiers
        String keyType;
        int keyAmount;
        if (prestige <= 10) { keyType = "common"; keyAmount = 1 + prestige / 4; }
        else if (prestige <= 20) { keyType = "rare"; keyAmount = 1 + (prestige - 10) / 4; }
        else if (prestige <= 35) { keyType = "special"; keyAmount = 1 + (prestige - 20) / 5; }
        else if (prestige <= 50) { keyType = "legendary"; keyAmount = 1 + (prestige - 35) / 5; }
        else { keyType = "moon"; keyAmount = 1 + (prestige - 50) / 3; } // más llaves moon

        return new PrestigeReward(money, essences, keyType, keyAmount, RANK_ICONS[prestige]);
    }

    /**
     * Returns a list of bonus reward descriptions for the GUI tooltip.
     * Does NOT execute them — doPrestige handles execution.
     */
    public static List<String> getBonusDescriptions(int prestige) {
        List<String> bonuses = new ArrayList<>();
        // Passive abilities unlocked at milestones
        if (prestige == 10) bonuses.add("§a⚡ §f+Velocidad de minado §7(Haste I, permanente)");
        if (prestige == 20) bonuses.add("§a🌾 §fDoble cosecha §7(10% prob, permanente)");
        if (prestige == 30) bonuses.add("§6🔥 §fAuto-smelt hierro/oro/cobre §7(permanente)");
        if (prestige == 40) bonuses.add("§d💗 §fRegeneración fuera de combate §7(permanente)");
        if (prestige == 50) bonuses.add("§b🪶 §fResistencia a caída -50% §7(permanente)");
        if (prestige == 60) bonuses.add("§e✦ §fDoble XP de mobs §7(permanente)");
        if (prestige == 70) bonuses.add("§6§l★ §fTodas las habilidades pasivas §7(combinadas)");
        // Fly: solo 1 vez por tier milestone
        if (prestige == 40) bonuses.add("§b✦ §fVuelo por §b3 días §7(único)");
        if (prestige == 50) bonuses.add("§b✦ §fVuelo por §b7 días §7(único)");
        if (prestige == 60) bonuses.add("§b✦ §fVuelo por §b14 días §7(único)");
        // P42+: Llave de Espadas garantizada
        if (prestige >= 42) bonuses.add("§c⚔ §fLlave de Espadas §7(garantizada)");
        // P45+: Extra llaves
        if (prestige >= 45) bonuses.add("§e🗝 §f3x Llaves " + (prestige >= 55 ? "Moon" : "Legendary") + " extra");
        // P48+: Esencias bonus
        if (prestige >= 48) bonuses.add("§d✦ §f+" + (25 + (prestige - 48) * 5) + " Esencias bonus");
        // P50+: Dinero bonus masivo
        if (prestige >= 50) bonuses.add("§6💰 §f+$" + String.format("%,d", 50000 + (prestige - 50) * 10000) + " bonus");
        // P55+: Kit VIP temporal
        if (prestige >= 55) bonuses.add("§a✦ §fKit VIP temporal §a3 días");
        // P60+: Doble llaves moon
        if (prestige >= 60) bonuses.add("§5✦ §f2x Llaves Moon extra");
        // P65+: Llave de Espadas x2
        if (prestige >= 65) bonuses.add("§c⚔ §f2x Llaves de Espadas");
        // P70: MEGA reward
        if (prestige == 70) {
            bonuses.add("§b§l✦ §fVuelo §b§lPERMANENTE");
            bonuses.add("§6§l★ §e§lRECOMPENSA LEGENDARIA §6§l★");
        }
        return bonuses;
    }

    // Check if player has completed all missions for next prestige
    public boolean canPrestige(UUID uuid) {
        PrestigeData pd = getData(uuid);
        if (pd.prestige >= MAX_PRESTIGE) return false;
        List<PrestigeMission> missions = getMissionsForPrestige(pd.prestige);
        for (PrestigeMission m : missions) {
            if (!pd.completedMissions.contains(m.id)) return false;
        }
        return true;
    }

    public void doPrestige(Player player) {
        UUID uuid = player.getUniqueId();
        PrestigeData pd = getData(uuid);
        if (!canPrestige(uuid)) return;

        // Reset money and essences to 0 BEFORE giving rewards
        double oldMoney = 0;
        int oldEssences = 0;
        if (plugin.getEconomy() != null) {
            oldMoney = plugin.getEconomy().getBalance(player);
            plugin.getEconomy().withdrawPlayer(player, oldMoney);
        }
        if (plugin.getEssenceManager() != null) {
            oldEssences = plugin.getEssenceManager().getEssences(uuid);
            plugin.getEssenceManager().setEssences(uuid, 0);
        }

        pd.prestige++;
        pd.completedMissions.clear();
        PrestigeReward reward = getRewardForPrestige(pd.prestige);

        // Give base rewards AFTER reset
        if (reward.money > 0 && plugin.getEconomy() != null) plugin.getEconomy().depositPlayer(player, reward.money);
        if (reward.essences > 0 && plugin.getEssenceManager() != null) plugin.getEssenceManager().addEssences(uuid, reward.essences);
        if (reward.keyAmount > 0) Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "excellentcrates key give " + player.getName() + " " + reward.keyType + " " + reward.keyAmount);

        String pName = player.getName();
        int pLevel = pd.prestige;

        // Llave de Espadas: garantizada P42+, 20% chance P30-41
        if (pLevel >= 42) {
            int swordKeys = pLevel >= 65 ? 2 : 1;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "excellentcrates key give " + pName + " llave_espadas " + swordKeys);
            player.sendMessage(SmallCaps.convert("§c§l⚔ §f¡Has obtenido " + swordKeys + "x §c§lLlave de Espadas§f! §7(Prestigio)"));
            Bukkit.broadcastMessage(SmallCaps.convert("§c§l⚔ §e" + pName + " §fha obtenido §c" + swordKeys + "x Llave de Espadas §fpor prestigio " + getTitle(uuid) + "§f!"));
        } else if (pLevel >= 30 && Math.random() < 0.20) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "excellentcrates key give " + pName + " llave_espadas 1");
            player.sendMessage(SmallCaps.convert("§c§l⚔ §f¡Has obtenido una §c§lLlave de Espadas§f! §7(Suerte)"));
        }

        // ═══ BONUS REWARDS P40+ ═══

        // Fly temporal via LuckPerms — solo 1 vez por tier milestone
        if (pLevel == 60) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "lp user " + pName + " permission settemp essentials.fly true 14d");
            player.sendMessage(SmallCaps.convert("§b§l✦ §f¡Vuelo activado por §b14 días§f! §7(Prestigio 60)"));
            Bukkit.broadcastMessage(SmallCaps.convert("§b§l✦ §e" + pName + " §fha desbloqueado §b14 días de vuelo §fpor prestigio §e60§f!"));
        } else if (pLevel == 50) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "lp user " + pName + " permission settemp essentials.fly true 7d");
            player.sendMessage(SmallCaps.convert("§b§l✦ §f¡Vuelo activado por §b7 días§f! §7(Prestigio 50)"));
            Bukkit.broadcastMessage(SmallCaps.convert("§b§l✦ §e" + pName + " §fha desbloqueado §b7 días de vuelo §fpor prestigio §e50§f!"));
        } else if (pLevel == 40) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "lp user " + pName + " permission settemp essentials.fly true 3d");
            player.sendMessage(SmallCaps.convert("§b§l✦ §f¡Vuelo activado por §b3 días§f! §7(Prestigio 40)"));
            Bukkit.broadcastMessage(SmallCaps.convert("§b§l✦ §e" + pName + " §fha desbloqueado §b3 días de vuelo §fpor prestigio §e40§f!"));
        }

        // Extra llaves P45+
        if (pLevel >= 55) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "excellentcrates key give " + pName + " moon 3");
            player.sendMessage(SmallCaps.convert("§e§l🗝 §f¡3x Llaves §dMoon §fde bonus!"));
        } else if (pLevel >= 45) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "excellentcrates key give " + pName + " legendary 3");
            player.sendMessage(SmallCaps.convert("§e§l🗝 §f¡3x Llaves §6Legendary §fde bonus!"));
        }

        // Esencias bonus P48+
        if (pLevel >= 48 && plugin.getEssenceManager() != null) {
            int bonusEss = 25 + (pLevel - 48) * 5;
            plugin.getEssenceManager().addEssences(uuid, bonusEss);
            player.sendMessage(SmallCaps.convert("§d§l✦ §f+" + bonusEss + " Esencias bonus"));
        }

        // Dinero bonus masivo P50+
        if (pLevel >= 50 && plugin.getEconomy() != null) {
            int bonusMoney = 50000 + (pLevel - 50) * 10000;
            plugin.getEconomy().depositPlayer(player, bonusMoney);
            player.sendMessage(SmallCaps.convert("§6§l💰 §f+$" + String.format("%,d", bonusMoney) + " de bonus"));
        }

        // Kit VIP temporal P55+
        if (pLevel >= 55) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "lp user " + pName + " parent addtemp vip 3d");
            player.sendMessage(SmallCaps.convert("§a§l✦ §f¡Kit VIP temporal por §a3 días§f!"));
        }

        // Extra llaves Moon P60+
        if (pLevel >= 60) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "excellentcrates key give " + pName + " moon 2");
            player.sendMessage(SmallCaps.convert("§5§l✦ §f¡2x Llaves §dMoon §fextra!"));
        }

        // P70 MEGA REWARD
        if (pLevel == 70) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "excellentcrates key give " + pName + " llave_espadas 5");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "excellentcrates key give " + pName + " moon 10");
            // Fly PERMANENTE
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "lp user " + pName + " permission set essentials.fly true");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "lp user " + pName + " parent addtemp vip 30d");
            if (plugin.getEconomy() != null) plugin.getEconomy().depositPlayer(player, 500000);
            if (plugin.getEssenceManager() != null) plugin.getEssenceManager().addEssences(uuid, 500);
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("§6§l★ §e§l¡RECOMPENSA LEGENDARIA P70! §6§l★"));
            player.sendMessage(SmallCaps.convert("§f  5x Llave de Espadas, 10x Moon, $500,000"));
            player.sendMessage(SmallCaps.convert("§f  500 Esencias, §b§lFly PERMANENTE§f, 30d VIP"));
            player.sendMessage("");
            Bukkit.broadcastMessage(SmallCaps.convert("§6§l★ §e" + pName + " §fha alcanzado §6§lPRESTIGIO MÁXIMO §e§lP70§f! §6§l★"));
        }

        saveData();

        // Notify player about reset
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§c§l! §fTu dinero y esencias han sido restablecidos a 0."));
        player.sendMessage(SmallCaps.convert("§7Dinero perdido: §f$" + String.format("%,.0f", oldMoney)));
        player.sendMessage(SmallCaps.convert("§7Esencias perdidas: §f" + oldEssences));
        player.sendMessage(SmallCaps.convert("§aRecompensa de prestigio depositada."));
        player.sendMessage("");

        // Broadcast
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(SmallCaps.convert("§6§l PRESTIGIO"));
        Bukkit.broadcastMessage(SmallCaps.convert("§f" + pName + " §7ha alcanzado el prestigio " + getTitle(uuid) + "§7!"));
        Bukkit.broadcastMessage("");

        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);

        // Open roulette bonus after a short delay
        PrestigeRouletteManager roulette = plugin.getPrestigeRouletteManager();
        if (roulette != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) roulette.openRoulette(player, pLevel);
            }, 40L);
        }
    }

    // Complete a mission (called from listeners)
    public void completeMission(UUID uuid, String missionId) {
        PrestigeData pd = getData(uuid);
        if (pd.completedMissions.contains(missionId)) return;
        pd.completedMissions.add(missionId);
        saveData();

        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            player.sendMessage(SmallCaps.convert("§a§l✔ §fMisión de prestigio completada: §e" + missionId));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);

            if (canPrestige(uuid)) {
                player.sendMessage(SmallCaps.convert("§6§l⭐ §e¡Todas las misiones completas! Usa §f/prestigio §epara ascender."));
            }
        }
    }

    /**
     * Gets the current progress for a mission type using Bukkit statistics.
     */
    public long getMissionProgress(UUID uuid, String type) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return 0;

        switch (type) {
            case "mine":
                long total = 0;
                Material[] ores = {
                    Material.STONE, Material.DEEPSLATE, Material.DIRT, Material.COBBLESTONE,
                    Material.GRANITE, Material.DIORITE, Material.ANDESITE, Material.SAND,
                    Material.GRAVEL, Material.COAL_ORE, Material.IRON_ORE, Material.GOLD_ORE,
                    Material.DIAMOND_ORE, Material.EMERALD_ORE, Material.LAPIS_ORE,
                    Material.REDSTONE_ORE, Material.COPPER_ORE, Material.NETHERRACK,
                    Material.NETHER_GOLD_ORE, Material.ANCIENT_DEBRIS,
                    Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.DARK_OAK_LOG,
                    Material.DEEPSLATE_COAL_ORE, Material.DEEPSLATE_IRON_ORE, Material.DEEPSLATE_GOLD_ORE,
                    Material.DEEPSLATE_DIAMOND_ORE, Material.DEEPSLATE_EMERALD_ORE,
                    Material.DEEPSLATE_LAPIS_ORE, Material.DEEPSLATE_REDSTONE_ORE, Material.DEEPSLATE_COPPER_ORE
                };
                for (Material m : ores) total += player.getStatistic(org.bukkit.Statistic.MINE_BLOCK, m);
                return total;
            case "kill_mob":
                return player.getStatistic(org.bukkit.Statistic.MOB_KILLS);
            case "pvp_kill":
                return player.getStatistic(org.bukkit.Statistic.PLAYER_KILLS);
            case "fish":
                return player.getStatistic(org.bukkit.Statistic.FISH_CAUGHT);
            case "playtime":
                if (plugin.getPlayTimeRewardsManager() != null) {
                    return plugin.getPlayTimeRewardsManager().getPlayerTotalPlayTime(uuid);
                }
                return player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) / 20;
            case "earn_money":
                if (plugin.getEconomy() != null) return (long) plugin.getEconomy().getBalance(player);
                return 0;
            case "streak":
                if (plugin.getDailyStreakManager() != null) return plugin.getDailyStreakManager().getStreak(uuid).bestStreak;
                return 0;
            case "koth_win":
                return 0;
            case "jump":
                return player.getStatistic(org.bukkit.Statistic.JUMP);
            case "walk":
                // WALK_ONE_CM returns centimeters, convert to blocks
                // Also includes extra distance from ice/piston movement
                return player.getStatistic(org.bukkit.Statistic.WALK_ONE_CM) / 100
                        + getData(uuid).extraWalkCm / 100;
            case "sprint":
                return player.getStatistic(org.bukkit.Statistic.SPRINT_ONE_CM) / 100;
            case "crouch":
                return player.getStatistic(org.bukkit.Statistic.CROUCH_ONE_CM) / 100;
            case "damage_dealt":
                return player.getStatistic(org.bukkit.Statistic.DAMAGE_DEALT) / 10;
            case "damage_taken":
                return player.getStatistic(org.bukkit.Statistic.DAMAGE_TAKEN) / 10;
            case "deaths":
                return player.getStatistic(org.bukkit.Statistic.DEATHS);
            case "eat":
                long eatTotal = 0;
                for (Material food : Material.values()) {
                    if (food.isEdible()) {
                        try {
                            eatTotal += player.getStatistic(org.bukkit.Statistic.USE_ITEM, food);
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
                return eatTotal;
            case "craft":
                return player.getStatistic(org.bukkit.Statistic.CRAFT_ITEM, Material.DIAMOND_SWORD)
                     + player.getStatistic(org.bukkit.Statistic.CRAFT_ITEM, Material.DIAMOND_PICKAXE)
                     + player.getStatistic(org.bukkit.Statistic.CRAFT_ITEM, Material.DIAMOND_AXE)
                     + player.getStatistic(org.bukkit.Statistic.CRAFT_ITEM, Material.DIAMOND_CHESTPLATE)
                     + player.getStatistic(org.bukkit.Statistic.CRAFT_ITEM, Material.DIAMOND_HELMET)
                     + player.getStatistic(org.bukkit.Statistic.CRAFT_ITEM, Material.DIAMOND_LEGGINGS)
                     + player.getStatistic(org.bukkit.Statistic.CRAFT_ITEM, Material.DIAMOND_BOOTS)
                     + player.getStatistic(org.bukkit.Statistic.CRAFT_ITEM, Material.NETHERITE_INGOT);
            case "enchant":
                return player.getStatistic(org.bukkit.Statistic.ITEM_ENCHANTED);
            case "mine_diamond":
                return player.getStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.DIAMOND_ORE)
                     + player.getStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.DEEPSLATE_DIAMOND_ORE);
            case "mine_netherite":
                return player.getStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.ANCIENT_DEBRIS);
            case "kill_ender_dragon":
                return player.getStatistic(org.bukkit.Statistic.KILL_ENTITY, org.bukkit.entity.EntityType.ENDER_DRAGON);
            case "kill_wither":
                return player.getStatistic(org.bukkit.Statistic.KILL_ENTITY, org.bukkit.entity.EntityType.WITHER);
            case "swim":
                return player.getStatistic(org.bukkit.Statistic.SWIM_ONE_CM) / 100;
            case "fly_elytra":
                return player.getStatistic(org.bukkit.Statistic.AVIATE_ONE_CM) / 100;
            case "brew":
                return player.getStatistic(org.bukkit.Statistic.BREWINGSTAND_INTERACTION);
            case "trade":
                return player.getStatistic(org.bukkit.Statistic.TRADED_WITH_VILLAGER);
            case "place":
                long placeTotal = 0;
                for (Material m : Material.values()) {
                    if (m.isBlock()) {
                        try {
                            placeTotal += player.getStatistic(org.bukkit.Statistic.USE_ITEM, m);
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
                return placeTotal;
            case "kill_enderman":
                return player.getStatistic(org.bukkit.Statistic.KILL_ENTITY, org.bukkit.entity.EntityType.ENDERMAN);
            case "kill_blaze":
                return player.getStatistic(org.bukkit.Statistic.KILL_ENTITY, org.bukkit.entity.EntityType.BLAZE);
            case "totem":
                return player.getStatistic(org.bukkit.Statistic.USE_ITEM, Material.TOTEM_OF_UNDYING);
            case "killstreak":
                return getData(uuid).bestKillstreak;
            case "prestige_kill":
                return getData(uuid).prestigeKills;
            case "biomes":
                return getData(uuid).discoveredBiomes.size();
            case "survive":
                return player.getStatistic(org.bukkit.Statistic.TIME_SINCE_DEATH) / 20 / 60; // ticks -> minutes
            case "donate":
                return getData(uuid).totalDonated;
            case "kda": {
                int pvpK = player.getStatistic(org.bukkit.Statistic.PLAYER_KILLS);
                int pvpD = Math.max(1, player.getStatistic(org.bukkit.Statistic.DEATHS));
                return Math.round((double) pvpK / pvpD * 100);
            }
            // ── CO-OP mission types ──
            case "coop_mine":    return getData(uuid).coopMineBlocks;
            case "coop_kill":    return getData(uuid).coopKills;
            case "coop_assist":  return getData(uuid).coopAssists;
            case "heal_ally":    return getData(uuid).healedAllies;
            case "buddy_walk":   return getData(uuid).buddyDistanceCm / 100; // cm → blocks
            case "coop_fish":    return getData(uuid).coopFished;
            case "coop_boss":    return getData(uuid).coopBossKills;
            case "coop_enchant": return getData(uuid).coopEnchants;
            case "sync_kill":    return getData(uuid).syncKills;
            case "gift_unique":  return getData(uuid).giftedUniquePlayers.size();
            default:
                return 0;
        }
    }

    // ===========================
    // GUI
    // ===========================

    // Pagination: stores current page per player
    private final Map<UUID, Integer> guiPage = new HashMap<>();
    private static final int ITEMS_PER_PAGE = 28; // 4 rows x 7 columns

    public void openMainGUI(Player player) { openMainGUI(player, 0); }

    public void openMainGUI(Player player, int page) {
        UUID uuid = player.getUniqueId();
        PrestigeData pd = getData(uuid);
        guiPage.put(uuid, page);

        // Auto-complete missions that are already met
        if (pd.prestige < MAX_PRESTIGE) {
            boolean changed = false;
            List<PrestigeMission> currentMissions = getMissionsForPrestige(pd.prestige);
            for (PrestigeMission m : currentMissions) {
                if (!pd.completedMissions.contains(m.id)) {
                    long current = getMissionProgress(uuid, m.type);
                    if (current >= m.target) {
                        pd.completedMissions.add(m.id);
                        changed = true;
                    }
                }
            }
            if (changed) saveData();
        }

        int totalPages = (int) Math.ceil((double) MAX_PRESTIGE / ITEMS_PER_PAGE);
        page = Math.max(0, Math.min(page, totalPages - 1));

        Inventory inv = Bukkit.createInventory(null, 54, GUI_TITLE);

        // Borders
        ItemStack border = createItem(Material.BLACK_STAINED_GLASS_PANE, "§r");
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);
        for (int row = 1; row <= 4; row++) {
            inv.setItem(row * 9, border);
            inv.setItem(row * 9 + 8, border);
        }

        // Player head with rank icon
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        sm.setOwningPlayer(player);
        sm.setDisplayName(getColor(uuid) + "§l" + player.getName());
        List<String> headLore = new ArrayList<>();
        headLore.add("");
        headLore.add(SmallCaps.convert("§fPrestigio: " + getColor(uuid) + pd.prestige + " " + getTitle(uuid)));
        headLore.add(SmallCaps.convert("§fRango: " + getTierName(pd.prestige)));
        headLore.add("");
        headLore.add(SmallCaps.convert("§7§lTus Stats:"));
        headLore.add(SmallCaps.convert("§f  §c⚔ Daño: " + getDamageDisplay(uuid)));
        headLore.add(SmallCaps.convert("§f  §9🛡 Defensa: " + getDefenseDisplay(uuid)));
        headLore.add(SmallCaps.convert("§f  §d❤ Vida: " + getHealthDisplay(uuid)));
        headLore.add(SmallCaps.convert("§f  §a🍀 Suerte: " + getLuckDisplay(uuid)));
        headLore.add(SmallCaps.convert("§f  §e💫 Regen: " + getRegenDisplay(uuid)));
        headLore.add("");
        sm.setLore(headLore);
        head.setItemMeta(sm);
        inv.setItem(4, head);

        // Prestige items for this page
        int startP = page * ITEMS_PER_PAGE + 1;
        int endP = Math.min(startP + ITEMS_PER_PAGE - 1, MAX_PRESTIGE);

        for (int p = startP; p <= endP; p++) {
            int idx = p - startP;
            int row = idx / 7;
            int col = idx % 7;
            int slot = (row + 1) * 9 + (col + 1);

            boolean achieved = pd.prestige >= p;
            boolean isCurrent = pd.prestige == p - 1;

            Material mat;
            String prefix;
            if (achieved) {
                mat = Material.LIME_STAINED_GLASS_PANE;
                prefix = "§a§l✓ ";
            } else if (isCurrent) {
                mat = Material.GOLD_INGOT;
                prefix = "§e§l▶ ";
            } else {
                mat = Material.RED_STAINED_GLASS_PANE;
                prefix = "§c";
            }

            // Current prestige: nether star to stand out
            if (isCurrent) mat = Material.NETHER_STAR;

            PrestigeReward reward = getRewardForPrestige(p);
            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(prefix + "Prestigio " + p + " " + RANK_ICONS[p] + " §8— " + getTierName(p));
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(SmallCaps.convert("§7Icono: ") + RANK_ICONS[p]);
            lore.add(SmallCaps.convert("§7Recompensa:"));
            lore.add(SmallCaps.convert("§f$" + String.format("%,d", reward.money) + " + " + reward.essences + " Esencias"));
            lore.add(SmallCaps.convert("§f" + reward.keyAmount + "x Llave " + capitalize(reward.keyType)));
            lore.add("");
            lore.add(SmallCaps.convert("§7§lStats al llegar:"));
            lore.add(SmallCaps.convert("§f  §c⚔ Daño: " + getDamageDisplayStatic(p) + "  §9🛡 Defensa: " + getDefenseDisplayStatic(p)));
            lore.add(SmallCaps.convert("§f  §d❤ Vida: " + getHealthDisplayStatic(p) + "  §a🍀 Suerte: " + getLuckDisplayStatic(p)));
            lore.add(SmallCaps.convert("§f  §e💫 Regen: " + getRegenDisplayStatic(p)));
            List<String> bonuses = getBonusDescriptions(p);
            if (!bonuses.isEmpty()) {
                lore.add("");
                lore.add(SmallCaps.convert("§6§lBonus:"));
                for (String b : bonuses) lore.add(SmallCaps.convert(b));
            }
            lore.add("");
            if (achieved) {
                lore.add(SmallCaps.convert("§a✓ Completado"));
            } else if (isCurrent) {
                List<PrestigeMission> missions = getMissionsForPrestige(pd.prestige);
                int done = 0;
                for (PrestigeMission m : missions) {
                    if (pd.completedMissions.contains(m.id)) done++;
                }
                int pct = missions.isEmpty() ? 0 : (done * 100) / missions.size();
                lore.add(SmallCaps.convert("§eMisiones: §f" + done + "§7/§f" + missions.size() + " §8(§e" + pct + "%§8)"));
                lore.add(SmallCaps.convert("§e§l▶ Click para ver misiones"));
            } else {
                lore.add(SmallCaps.convert("§cBloqueado"));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(slot, item);
        }

        // Page navigation
        if (page > 0) {
            ItemStack prev = createItem(Material.ARROW, "§e§l◀ Página anterior");
            ItemMeta pm = prev.getItemMeta();
            pm.setLore(Arrays.asList("", SmallCaps.convert("§7Página " + page + "/" + totalPages)));
            prev.setItemMeta(pm);
            inv.setItem(45, prev);
        }
        if (page < totalPages - 1) {
            ItemStack next = createItem(Material.ARROW, "§e§lPágina siguiente ▶");
            ItemMeta nm = next.getItemMeta();
            nm.setLore(Arrays.asList("", SmallCaps.convert("§7Página " + (page + 2) + "/" + totalPages)));
            next.setItemMeta(nm);
            inv.setItem(53, next);
        }

        // Top button
        ItemStack topItem = createItem(Material.DIAMOND, "§e§l🏆 Top Prestigios");
        ItemMeta topMeta = topItem.getItemMeta();
        topMeta.setLore(Arrays.asList("", SmallCaps.convert("§7Ver ranking de prestigios")));
        topItem.setItemMeta(topMeta);
        inv.setItem(49, topItem);

        // Prestige button
        if (canPrestige(uuid)) {
            ItemStack prestigeBtn = createItem(Material.NETHER_STAR, "§6§l ASCENDER DE PRESTIGIO");
            ItemMeta pbm = prestigeBtn.getItemMeta();
            List<String> pLore = new ArrayList<>();
            pLore.add("");
            pLore.add(SmallCaps.convert("§a§l▶ Click para ascender a Prestigio " + (pd.prestige + 1)));
            pLore.add(SmallCaps.convert("§7Requiere doble confirmación en el chat"));
            pLore.add("");
            pLore.add(SmallCaps.convert("§c§lAVISO:"));
            pLore.add(SmallCaps.convert("§f• Tu §cdinero §fse restablecerá a §4$0"));
            pLore.add(SmallCaps.convert("§f• Tus §cesencias §fse restablecerán a §40"));
            pLore.add("");
            pbm.setLore(pLore);
            prestigeBtn.setItemMeta(pbm);
            inv.setItem(47, prestigeBtn);
        }

        // Weekly missions button (slot 51) — only for P30+
        if (pd.prestige >= 30) {
            ItemStack weeklyBtn = createItem(Material.CLOCK, "§b§l📅 Misiones Semanales");
            ItemMeta wm = weeklyBtn.getItemMeta();
            wm.setLore(Arrays.asList("", SmallCaps.convert("§7Misiones rotativas P30+"), SmallCaps.convert("§7Cambian cada semana"), ""));
            weeklyBtn.setItemMeta(wm);
            inv.setItem(51, weeklyBtn);
        }

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.2f);
    }

    public int getGUIPage(UUID uuid) { return guiPage.getOrDefault(uuid, 0); }

    public void openMissionsGUI(Player player) {
        UUID uuid = player.getUniqueId();
        PrestigeData pd = getData(uuid);
        List<PrestigeMission> missions = getMissionsForPrestige(pd.prestige);

        int rows = (int) Math.ceil((double) missions.size() / 7.0) + 2; // +2 for top/bottom borders
        int size = Math.max(27, Math.min(54, rows * 9));
        Inventory inv = Bukkit.createInventory(null, size, MISSIONS_TITLE);

        // Borders
        ItemStack border = createItem(Material.BLACK_STAINED_GLASS_PANE, "§r");
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = size - 9; i < size; i++) inv.setItem(i, border);

        // Info
        ItemStack info = createItem(Material.BOOK, "§c§lPrestigio " + (pd.prestige + 1) + " — Misiones");
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setLore(Arrays.asList(
                "",
                SmallCaps.convert("§7Completa todas las misiones para"),
                SmallCaps.convert("§7ascender al rango: " + getTierName(Math.min(pd.prestige + 1, MAX_PRESTIGE)) + " " + RANK_ICONS[Math.min(pd.prestige + 1, MAX_PRESTIGE)]),
                ""
        ));
        info.setItemMeta(infoMeta);
        inv.setItem(4, info);

        // Auto-complete missions that are already met
        boolean changed = false;
        for (PrestigeMission m : missions) {
            if (!pd.completedMissions.contains(m.id)) {
                long current = getMissionProgress(uuid, m.type);
                if (current >= m.target) {
                    pd.completedMissions.add(m.id);
                    changed = true;
                }
            }
        }
        if (changed) saveData();

        // Mission items
        for (int i = 0; i < missions.size(); i++) {
            PrestigeMission m = missions.get(i);
            boolean done = pd.completedMissions.contains(m.id);

            int slot = 10 + i + (i / 7) * 2;
            if (slot >= size - 9) break;

            boolean isCoop = isCoopMissionType(m.type);
            Material mat = done ? Material.LIME_CONCRETE : (isCoop ? Material.MAGENTA_CONCRETE : Material.RED_CONCRETE);
            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName((done ? "§a§l✓ " : "§c§l✖ ") + "§f" + m.description);
            List<String> lore = new ArrayList<>();
            lore.add("");
            if (done) {
                lore.add(SmallCaps.convert("§a✓ Completado"));
            } else {
                long current = getMissionProgress(uuid, m.type);
                long target = m.target;
                int pct = target <= 0 ? 0 : (int) Math.min(100, (current * 100) / target);
                String currentFmt = formatNumber(current);
                String targetFmt = formatNumber(target);

                // Progress bar (10 chars)
                int filled = pct / 10;
                StringBuilder bar = new StringBuilder("§a");
                for (int b = 0; b < 10; b++) {
                    if (b == filled) bar.append("§c");
                    bar.append("■");
                }

                lore.add(SmallCaps.convert("§7Progreso: §f" + currentFmt + "§7/§f" + targetFmt + " §8(§e" + pct + "%§8)"));
                lore.add(bar.toString());
            }
            // CO-OP mission explanation
            if (isCoop) {
                lore.add("");
                lore.add(SmallCaps.convert("§d§l⚡ MISIÓN COOPERATIVA"));
                lore.add(SmallCaps.convert("§7" + getCoopHint(m.type)));
            }
            lore.add("");
            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(slot, item);
        }

        // Back button
        inv.setItem(size - 5, createItem(Material.ARROW, "§e§l◀ Volver"));

        player.openInventory(inv);
    }

    /**
     * Abre un menú de misiones de prestigio de un jugador TARGET para que lo vea un STAFF.
     * Muestra el progreso en tiempo real de las misiones del prestigio actual del target.
     */
    public void openStaffMissionsGUI(Player staff, UUID targetUUID, String targetName) {
        PrestigeData pd = getData(targetUUID);
        int prest = pd.prestige;
        List<PrestigeMission> missions = getMissionsForPrestige(prest);

        int rows = (int) Math.ceil((double) missions.size() / 7.0) + 2;
        int size = Math.max(27, Math.min(54, rows * 9));
        String title = STAFF_MISSIONS_PREFIX + (prest + 1) + " §8— §f" + targetName;
        Inventory inv = Bukkit.createInventory(null, size, title);

        // Borders
        ItemStack border = createItem(Material.BLACK_STAINED_GLASS_PANE, "§r");
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = size - 9; i < size; i++) inv.setItem(i, border);

        // Info header
        ItemStack info = createItem(Material.BOOK, "§c§lPrestigio " + (prest + 1) + " — Misiones de §f" + targetName);
        ItemMeta infoMeta = info.getItemMeta();
        long totalDone = missions.stream().filter(m -> pd.completedMissions.contains(m.id)).count();
        infoMeta.setLore(Arrays.asList(
                "",
                SmallCaps.convert("§7Prestigio actual: §d§l" + prest),
                SmallCaps.convert("§7Siguiente: " + getTierName(Math.min(prest + 1, MAX_PRESTIGE))),
                SmallCaps.convert("§7Misiones: §a" + totalDone + "§7/§f" + missions.size()),
                "",
                SmallCaps.convert("§8Datos en tiempo real")
        ));
        info.setItemMeta(infoMeta);
        inv.setItem(4, info);

        // Mission items
        for (int i = 0; i < missions.size(); i++) {
            PrestigeMission m = missions.get(i);
            boolean done = pd.completedMissions.contains(m.id);

            int slot = 10 + i + (i / 7) * 2;
            if (slot >= size - 9) break;

            boolean isCoop = isCoopMissionType(m.type);
            Material mat = done ? Material.LIME_CONCRETE : (isCoop ? Material.MAGENTA_CONCRETE : Material.RED_CONCRETE);
            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName((done ? "§a§l✓ " : "§c§l✖ ") + "§f" + m.description);
            List<String> lore = new ArrayList<>();
            lore.add("");
            if (done) {
                lore.add(SmallCaps.convert("§a✓ Completado"));
            } else {
                long current = getMissionProgress(targetUUID, m.type);
                long target = m.target;
                int pct = target <= 0 ? 0 : (int) Math.min(100, (current * 100) / target);
                String currentFmt = formatNumber(current);
                String targetFmt = formatNumber(target);

                int filled = pct / 10;
                StringBuilder bar = new StringBuilder("§a");
                for (int b = 0; b < 10; b++) {
                    if (b == filled) bar.append("§c");
                    bar.append("■");
                }

                lore.add(SmallCaps.convert("§7Progreso: §f" + currentFmt + "§7/§f" + targetFmt + " §8(§e" + pct + "%§8)"));
                lore.add(bar.toString());
            }
            if (isCoop) {
                lore.add("");
                lore.add(SmallCaps.convert("§d§l⚡ MISIÓN COOPERATIVA"));
                lore.add(SmallCaps.convert("§7" + getCoopHint(m.type)));
            }
            lore.add("");
            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(slot, item);
        }

        // Back button
        inv.setItem(size - 5, createItem(Material.ARROW, "§e§l◀ Volver al perfil"));

        staff.openInventory(inv);
    }

    public void openTopGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, TOP_TITLE);

        ItemStack border = createItem(Material.BLACK_STAINED_GLASS_PANE, "§r");
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);

        inv.setItem(4, createItem(Material.DIAMOND, "§e§l🏆 Top Prestigios"));

        List<Map.Entry<UUID, PrestigeData>> sorted = players.entrySet().stream()
                .filter(e -> e.getValue().prestige > 0)
                .sorted((a, b) -> Integer.compare(b.getValue().prestige, a.getValue().prestige))
                .limit(21)
                .collect(Collectors.toList());

        for (int i = 0; i < sorted.size(); i++) {
            UUID uuid = sorted.get(i).getKey();
            PrestigeData pd = sorted.get(i).getValue();
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            if (name == null) name = "???";

            int slot = 10 + i + (i / 7) * 2;
            if (slot >= 45) break;

            String medal = i == 0 ? "§e🥇" : i == 1 ? "§7🥈" : i == 2 ? "§6🥉" : "§f" + (i + 1) + ".";

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) head.getItemMeta();
            sm.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
            sm.setDisplayName(medal + " " + getColor(uuid) + name);
            sm.setLore(Arrays.asList(
                    "",
                    SmallCaps.convert("§fPrestigio: " + getColor(uuid) + pd.prestige + " " + getTitle(uuid)),
                    SmallCaps.convert("§fRango: " + getTierName(pd.prestige)),
                    ""
            ));
            head.setItemMeta(sm);
            inv.setItem(slot, head);
        }

        if (sorted.isEmpty()) {
            inv.setItem(22, createItem(Material.BARRIER, "§7Nadie ha alcanzado un prestigio aún"));
        }

        inv.setItem(49, createItem(Material.ARROW, "§e§l◀ Volver"));

        player.openInventory(inv);
    }

    private ItemStack createItem(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private String formatNumber(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format("%.1fK", n / 1_000.0);
        return String.valueOf(n);
    }

    // ===========================
    // PRESTIGE STATS
    // ===========================

    // ⚔ Damage bonus: +0.5% per prestige level → P70 = +35% extra damage
    public double getDamageMultiplier(UUID uuid) {
        int p = getData(uuid).prestige;
        if (p <= 0) return 1.0;
        return 1.0 + (p * 0.005);
    }

    // 🛡 Defense: +0.3% damage reduction per level → P70 = -21% damage taken
    public double getDefenseMultiplier(UUID uuid) {
        int p = getData(uuid).prestige;
        if (p <= 0) return 1.0;
        return 1.0 - (p * 0.003);
    }

    // ❤ Health bonus: +2 HP (1 heart) per 10 prestige levels → P70 = +14 HP (7 hearts)
    public double getHealthBonus(UUID uuid) {
        int p = getData(uuid).prestige;
        if (p <= 0) return 0;
        return (p / 10) * 2.0;
    }

    // 🍀 Luck: +0.4% per level → P70 = +28% better loot/drops chance
    public double getLuckBonus(UUID uuid) {
        int p = getData(uuid).prestige;
        if (p <= 0) return 0;
        return p * 0.4;
    }

    // ⚡ Speed: +0.2% per level → P70 = +14% movement speed
    public double getSpeedBonus(UUID uuid) {
        int p = getData(uuid).prestige;
        if (p <= 0) return 0;
        return p * 0.2;
    }

    // 💫 Regen: +0.15% per level → P70 = +10.5% faster natural regen
    public double getRegenBonus(UUID uuid) {
        int p = getData(uuid).prestige;
        if (p <= 0) return 0;
        return p * 0.15;
    }

    // ── Display methods ──

    public String getDamageDisplay(UUID uuid) {
        int p = getData(uuid).prestige;
        if (p == 0) return "§7+0%";
        return "§c+" + String.format("%.1f%%", (p * 0.5));
    }

    public String getDefenseDisplay(UUID uuid) {
        int p = getData(uuid).prestige;
        if (p == 0) return "§7+0%";
        return "§9+" + String.format("%.1f%%", (p * 0.3));
    }

    public String getHealthDisplay(UUID uuid) {
        int p = getData(uuid).prestige;
        if (p == 0) return "§7+0";
        int hearts = p / 10;
        return "§d+" + hearts + " ❤";
    }

    public String getLuckDisplay(UUID uuid) {
        int p = getData(uuid).prestige;
        if (p == 0) return "§7+0%";
        return "§a+" + String.format("%.1f%%", (p * 0.4));
    }

    public String getSpeedDisplay(UUID uuid) {
        int p = getData(uuid).prestige;
        if (p == 0) return "§7+0%";
        return "§b+" + String.format("%.1f%%", (p * 0.2));
    }

    public String getRegenDisplay(UUID uuid) {
        int p = getData(uuid).prestige;
        if (p == 0) return "§7+0%";
        return "§e+" + String.format("%.1f%%", (p * 0.15));
    }

    // Static display for GUI tooltips (by prestige level)
    public static String getDamageDisplayStatic(int p) {
        if (p <= 0) return "§7+0%";
        return "§c+" + String.format("%.1f%%", (p * 0.5));
    }
    public static String getDefenseDisplayStatic(int p) {
        if (p <= 0) return "§7+0%";
        return "§9+" + String.format("%.1f%%", (p * 0.3));
    }
    public static String getHealthDisplayStatic(int p) {
        if (p <= 0) return "§7+0";
        return "§d+" + (p / 10) + " ❤";
    }
    public static String getLuckDisplayStatic(int p) {
        if (p <= 0) return "§7+0%";
        return "§a+" + String.format("%.1f%%", (p * 0.4));
    }
    public static String getSpeedDisplayStatic(int p) {
        if (p <= 0) return "§7+0%";
        return "§b+" + String.format("%.1f%%", (p * 0.2));
    }
    public static String getRegenDisplayStatic(int p) {
        if (p <= 0) return "§7+0%";
        return "§e+" + String.format("%.1f%%", (p * 0.15));
    }

    // Legacy compatibility — returns 1.0 (no multiplier)
    public double getMoneyMultiplier(UUID uuid) { return 1.0; }
    public double getEssenceMultiplier(UUID uuid) { return 1.0; }
    public String getMoneyMultiplierDisplay(UUID uuid) { return getDamageDisplay(uuid); }
    public String getEssenceMultiplierDisplay(UUID uuid) { return getDefenseDisplay(uuid); }

    // ===========================
    // INNER CLASSES
    // ===========================

    public static class PrestigeData {
        public int prestige = 1;
        public Set<String> completedMissions = new HashSet<>();
        // Custom tracking for special missions
        public int bestKillstreak = 0;
        public int currentKillstreak = 0; // not saved, runtime only
        public int prestigeKills = 0;
        public Set<String> discoveredBiomes = new HashSet<>();
        public long totalDonated = 0;
        // Co-op mission tracking
        public long coopMineBlocks = 0;
        public long coopKills = 0;
        public long coopAssists = 0;
        public long healedAllies = 0;
        public long buddyDistanceCm = 0;
        public long coopFished = 0;
        public long coopBossKills = 0;
        public long coopEnchants = 0;
        public long syncKills = 0;
        public Set<String> giftedUniquePlayers = new HashSet<>();
        // Extra walk distance from ice/piston movement (cm)
        public long extraWalkCm = 0;
    }

    public static class PrestigeMission {
        public String id;
        public String description;
        public String type;
        public long target;

        public PrestigeMission(String id, String description, String type, long target) {
            this.id = id;
            this.description = description;
            this.type = type;
            this.target = target;
        }
    }

    public static class PrestigeReward {
        public int money;
        public int essences;
        public String keyType;
        public int keyAmount;
        public String title;

        public PrestigeReward(int money, int essences, String keyType, int keyAmount, String title) {
            this.money = money;
            this.essences = essences;
            this.keyType = keyType;
            this.keyAmount = keyAmount;
            this.title = title;
        }
    }
}
