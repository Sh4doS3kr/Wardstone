package com.moonlight.coreprotect.essence;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Manager para el sistema de Esencias.
 * Esencias se ganan por: primer join, AFK por hora, matar boss, ganar KOTH.
 * Se gastan en: fly temporal, kits, crate keys, items.
 * Comando admin: /esencias add jugador cantidad
 * Conversión: 20M dinero -> 2 esencias
 */
public class EssenceManager {

    private final CoreProtectPlugin plugin;
    private final File dataFile;
    private final Map<UUID, Integer> essences = new HashMap<>();
    private final Map<UUID, Long> lastAfkReward = new HashMap<>();

    // Earning amounts
    public static final int FIRST_JOIN_REWARD = 2;
    public static final int HOURLY_AFK_REWARD = 1;
    public static final int BOSS_KILL_REWARD = 12;
    public static final int BOSS_PARTICIPATION_REWARD = 4;
    public static final int KOTH_WIN_REWARD = 8;
    public static final double MONEY_CONVERSION_COST = 20_000_000;
    public static final int MONEY_CONVERSION_AMOUNT = 4;

    public EssenceManager(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "essences.yml");
        loadData();

        // AFK reward ticker - check every 60 seconds
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickAfkRewards, 1200L, 1200L);
    }

    // ==================== DATA ====================

    public void loadData() {
        if (!dataFile.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        for (String key : cfg.getKeys(false)) {
            try {
                UUID uid = UUID.fromString(key);
                essences.put(uid, cfg.getInt(key));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void saveData() {
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> entry : essences.entrySet()) {
            cfg.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            cfg.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Error guardando essences.yml: " + e.getMessage());
        }
    }

    // ==================== CORE API ====================

    public int getEssences(UUID uid) {
        return essences.getOrDefault(uid, 0);
    }

    public void setEssences(UUID uid, int amount) {
        essences.put(uid, Math.max(0, amount));
    }

    public void addEssences(UUID uid, int amount) {
        essences.put(uid, getEssences(uid) + amount);
    }

    /**
     * Adds essences without applying any multiplier.
     * Used for transfers (trade, gift, refunds) where the exact amount must be preserved.
     */
    public void addEssencesRaw(UUID uid, int amount) {
        essences.put(uid, getEssences(uid) + amount);
    }

    public boolean removeEssences(UUID uid, int amount) {
        int current = getEssences(uid);
        if (current < amount) return false;
        essences.put(uid, current - amount);
        return true;
    }

    public boolean hasEssences(UUID uid, int amount) {
        return getEssences(uid) >= amount;
    }

    // ==================== EARNING ====================

    public void onFirstJoin(Player player) {
        UUID uid = player.getUniqueId();
        if (!player.hasPlayedBefore()) {
            addEssences(uid, FIRST_JOIN_REWARD);
            saveData();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage("");
                    player.sendMessage(SmallCaps.convert("§d§l✦ §5§lESENCIAS §d§l✦"));
                    player.sendMessage(SmallCaps.convert("§7Has recibido §d" + FIRST_JOIN_REWARD + " Esencias §7por tu primer ingreso."));
                    player.sendMessage(SmallCaps.convert("§7Usa §e/esencias §7para ver tu saldo y la tienda."));
                    player.sendMessage("");
                }
            }, 100L);
        }
    }

    public void onBossKill(Player player) {
        addEssences(player.getUniqueId(), BOSS_KILL_REWARD);
        saveData();
        player.sendMessage(SmallCaps.convert("§d§l✦ §a+" + BOSS_KILL_REWARD + " Esencias §7por matar al boss."));
    }

    public void onBossParticipation(Player player) {
        addEssences(player.getUniqueId(), BOSS_PARTICIPATION_REWARD);
        saveData();
        player.sendMessage(SmallCaps.convert("§d§l✦ §a+" + BOSS_PARTICIPATION_REWARD + " Esencias §7por participación."));
    }

    public void onKothWin(Player player) {
        addEssences(player.getUniqueId(), KOTH_WIN_REWARD);
        saveData();
        player.sendMessage(SmallCaps.convert("§d§l✦ §a+" + KOTH_WIN_REWARD + " Esencias §7por ganar el KOTH."));
    }

    /**
     * Intenta convertir 20M de dinero en 4 esencias.
     * @return true si la conversión fue exitosa
     */
    public boolean convertMoney(Player player) {
        net.milkbowl.vault.economy.Economy econ = plugin.getEconomy();
        if (econ == null) return false;

        double balance = econ.getBalance(player);
        if (balance < MONEY_CONVERSION_COST) return false;

        econ.withdrawPlayer(player, MONEY_CONVERSION_COST);
        addEssences(player.getUniqueId(), MONEY_CONVERSION_AMOUNT);
        saveData();
        return true;
    }

    // ==================== AFK REWARDS ====================

    private void tickAfkRewards() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uid = player.getUniqueId();
            // Check if player is in AFK zone
            if (plugin.getAfkZoneManager() != null && plugin.getAfkZoneManager().isInZone(player.getLocation())) {
                // First time seen in AFK zone → initialize to now so they wait a full hour
                if (!lastAfkReward.containsKey(uid)) {
                    lastAfkReward.put(uid, now);
                    continue;
                }
                long lastReward = lastAfkReward.get(uid);
                // 1 hour = 3600000ms
                if (now - lastReward >= 3600000L) {
                    lastAfkReward.put(uid, now);
                    addEssences(uid, HOURLY_AFK_REWARD);
                    saveData();
                    player.sendMessage(SmallCaps.convert("§d§l✦ §a+" + HOURLY_AFK_REWARD + " Esencia §7por hora AFK."));
                }
            }
        }
    }

    // ==================== SHUTDOWN ====================

    public void shutdown() {
        saveData();
    }
}
