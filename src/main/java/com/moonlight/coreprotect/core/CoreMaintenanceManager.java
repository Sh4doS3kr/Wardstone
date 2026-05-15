package com.moonlight.coreprotect.core;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.effects.SoundManager;
import com.moonlight.coreprotect.util.SmallCaps;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Gestiona:
 * 1. Límite de cores por jugador (basado en rango)
 * 2. Impuesto semanal de mantenimiento ($2500/semana por core)
 * 3. Sistema de avisos y eliminación automática
 * 4. Exención para nuevos jugadores (1 semana gratis)
 */
public class CoreMaintenanceManager {

    private final CoreProtectPlugin plugin;

    // === CORE LIMITS ===
    private static final int BASE_LIMIT = 10;
    private static final Map<String, Integer> RANK_BONUS = new LinkedHashMap<>();

    static {
        RANK_BONUS.put("luna", 5);      // 10 + 5 = 15
        RANK_BONUS.put("nova", 10);     // 10 + 10 = 20
        RANK_BONUS.put("eclipse", 15);  // 10 + 15 = 25
        RANK_BONUS.put("moonlord", 20); // 10 + 20 = 30
    }

    // === MAINTENANCE ===
    public static final double WEEKLY_TAX_PER_CORE = 2500.0;
    private static final long ONE_WEEK_MS = 7L * 24 * 60 * 60 * 1000;
    private static final long ONE_DAY_MS = 24L * 60 * 60 * 1000;
    private static final int GRACE_PERIOD_DAYS = 7;

    // Track last tax collection time and delinquent warnings
    // Key: player UUID, Value: timestamp of last successful payment
    private final Map<UUID, Long> lastPayment = new HashMap<>();
    // Key: player UUID, Value: timestamp when warnings started (couldn't pay)
    private final Map<UUID, Long> warningStart = new HashMap<>();

    public CoreMaintenanceManager(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        loadPaymentData();
        startMaintenanceTask();
    }

    // ═══════════════════════════════════════
    // CORE LIMIT SYSTEM
    // ═══════════════════════════════════════

    /**
     * Gets the maximum number of cores a player can have.
     */
    public int getMaxCores(Player player) {
        if (player.hasPermission("coreprotect.admin")) return Integer.MAX_VALUE;

        int bonus = 0;
        for (Map.Entry<String, Integer> entry : RANK_BONUS.entrySet()) {
            String rank = entry.getKey();
            if (player.hasPermission("wardstone.vip." + rank)
                    || player.hasPermission("group." + rank)
                    || player.hasPermission("essentials.kits." + rank)) {
                bonus = Math.max(bonus, entry.getValue());
            }
        }
        return BASE_LIMIT + bonus;
    }

    /**
     * Gets the maximum cores for an offline player (checks all permissions).
     */
    public int getMaxCores(OfflinePlayer player) {
        if (player.isOnline()) return getMaxCores(player.getPlayer());
        // Offline: assume base limit (can't check permissions offline reliably)
        return BASE_LIMIT;
    }

    /**
     * Gets the current number of cores a player owns.
     */
    public int getCoreCount(UUID playerId) {
        return plugin.getProtectionManager().getRegionsByOwner(playerId).size();
    }

    /**
     * Checks if a player can place a new core.
     */
    public boolean canPlaceCore(Player player) {
        int current = getCoreCount(player.getUniqueId());
        int max = getMaxCores(player);
        return current < max;
    }

    /**
     * Gets the rank name that gives the bonus.
     */
    public String getRankName(Player player) {
        String best = null;
        int bestBonus = 0;
        for (Map.Entry<String, Integer> entry : RANK_BONUS.entrySet()) {
            String rank = entry.getKey();
            if (player.hasPermission("wardstone.vip." + rank)
                    || player.hasPermission("group." + rank)
                    || player.hasPermission("essentials.kits." + rank)) {
                if (entry.getValue() > bestBonus) {
                    bestBonus = entry.getValue();
                    best = rank;
                }
            }
        }
        return best;
    }

    // ═══════════════════════════════════════
    // MAINTENANCE TAX SYSTEM
    // ═══════════════════════════════════════

    /**
     * Checks if a player is a new player (less than 1 week since first join).
     * New players are exempt from maintenance tax.
     */
    public boolean isNewPlayer(OfflinePlayer player) {
        long firstPlayed = player.getFirstPlayed();
        if (firstPlayed <= 0) return true; // Never joined = exempt
        return (System.currentTimeMillis() - firstPlayed) < ONE_WEEK_MS;
    }

    /**
     * Gets the total weekly tax for a player based on their core count.
     */
    public double getWeeklyTax(UUID playerId) {
        int cores = getCoreCount(playerId);
        return cores * WEEKLY_TAX_PER_CORE;
    }

    /**
     * Main maintenance task — runs daily to check and collect taxes.
     */
    private void startMaintenanceTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                processAllMaintenance();
            }
        }.runTaskTimer(plugin, 20L * 60 * 5, 20L * 60 * 60 * 24); // First run after 5 min, then every 24h
    }

    private void processAllMaintenance() {
        Set<UUID> allOwners = new HashSet<>();
        for (ProtectedRegion region : plugin.getProtectionManager().getAllRegions()) {
            allOwners.add(region.getOwner());
        }

        for (UUID ownerId : allOwners) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(ownerId);

            // Skip new players (first week free)
            if (isNewPlayer(offlinePlayer)) continue;

            int coreCount = getCoreCount(ownerId);
            if (coreCount <= 0) continue;

            double totalTax = coreCount * WEEKLY_TAX_PER_CORE;

            // Check if it's time to charge (weekly)
            long lastPaid = lastPayment.getOrDefault(ownerId, 0L);
            if (System.currentTimeMillis() - lastPaid < ONE_WEEK_MS) continue; // Not due yet

            Economy economy = plugin.getEconomy();
            if (economy == null) return;

            double balance = economy.getBalance(offlinePlayer);

            if (balance >= totalTax) {
                // Can pay — collect tax
                economy.withdrawPlayer(offlinePlayer, totalTax);
                lastPayment.put(ownerId, System.currentTimeMillis());
                warningStart.remove(ownerId); // Clear any warnings

                // Notify if online
                Player onlinePlayer = offlinePlayer.getPlayer();
                if (onlinePlayer != null && onlinePlayer.isOnline()) {
                    onlinePlayer.sendMessage(SmallCaps.convert(
                            "§e§l💰 §7Impuesto semanal: §c-$" + formatMoney(totalTax) +
                                    " §7(" + coreCount + " cores × $" + formatMoney(WEEKLY_TAX_PER_CORE) + ")"));
                }
                savePaymentData();
            } else {
                // Cannot pay — start or continue warning period
                if (!warningStart.containsKey(ownerId)) {
                    warningStart.put(ownerId, System.currentTimeMillis());
                    savePaymentData();
                }

                long warningSince = warningStart.get(ownerId);
                long daysSinceWarning = (System.currentTimeMillis() - warningSince) / ONE_DAY_MS;

                if (daysSinceWarning >= GRACE_PERIOD_DAYS) {
                    // Grace period expired — delete newest core
                    deleteNewestCore(ownerId);
                    warningStart.remove(ownerId); // Reset warning for next cycle
                    savePaymentData();
                } else {
                    // Send warning to online player
                    Player onlinePlayer = offlinePlayer.getPlayer();
                    if (onlinePlayer != null && onlinePlayer.isOnline()) {
                        long daysLeft = GRACE_PERIOD_DAYS - daysSinceWarning;
                        onlinePlayer.sendMessage("");
                        onlinePlayer.sendMessage(SmallCaps.convert(
                                "§c§l⚠ §c¡NO TIENES SALDO PARA EL MANTENIMIENTO!"));
                        onlinePlayer.sendMessage(SmallCaps.convert(
                                "§7Debes: §e$" + formatMoney(totalTax) +
                                        " §7| Tu saldo: §c$" + formatMoney(balance)));
                        onlinePlayer.sendMessage(SmallCaps.convert(
                                "§7Tienes §e" + daysLeft + " días §7para pagar o se eliminará un core."));
                        onlinePlayer.sendMessage("");
                    }
                }
            }
        }
    }

    /**
     * Deletes the newest core owned by a player.
     */
    private void deleteNewestCore(UUID ownerId) {
        List<ProtectedRegion> regions = plugin.getProtectionManager().getRegionsByOwner(ownerId);
        if (regions.isEmpty()) return;

        // Find the newest core (highest createdAt)
        ProtectedRegion newest = regions.get(0);
        for (ProtectedRegion r : regions) {
            if (r.getCreatedAt() > newest.getCreatedAt()) {
                newest = r;
            }
        }

        // Remove the core block
        org.bukkit.Location coreLoc = newest.getCoreLocation();
        if (coreLoc != null && coreLoc.getWorld() != null) {
            coreLoc.getBlock().setType(Material.AIR);
        }

        // Remove from system
        plugin.getProtectionManager().removeRegion(newest.getId());
        plugin.getDataManager().saveData();

        // Notify player if online
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(ownerId);
        Player onlinePlayer = offlinePlayer.getPlayer();
        if (onlinePlayer != null && onlinePlayer.isOnline()) {
            onlinePlayer.sendMessage("");
            onlinePlayer.sendMessage(SmallCaps.convert(
                    "§c§l⚠ §4§lCORE ELIMINADO POR FALTA DE PAGO"));
            onlinePlayer.sendMessage(SmallCaps.convert(
                    "§7Se eliminó tu core más reciente por no pagar mantenimiento."));
            onlinePlayer.sendMessage(SmallCaps.convert(
                    "§7Asegúrate de tener saldo para mantener tus territorios."));
            onlinePlayer.sendMessage("");
            SoundManager.playProtectionDenied(onlinePlayer.getLocation());
        }

        plugin.getLogger().info("[Mantenimiento] Core eliminado de " +
                (offlinePlayer.getName() != null ? offlinePlayer.getName() : ownerId) +
                " por falta de pago.");
    }

    // ═══════════════════════════════════════
    // LOGIN NOTIFICATION
    // ═══════════════════════════════════════

    /**
     * Called on player join to show maintenance status.
     */
    public void onPlayerJoin(Player player) {
        UUID uuid = player.getUniqueId();
        int cores = getCoreCount(uuid);
        if (cores <= 0) return;
        if (isNewPlayer(player)) return;

        // Check if they have pending warnings
        if (warningStart.containsKey(uuid)) {
            long warningSince = warningStart.get(uuid);
            long daysLeft = GRACE_PERIOD_DAYS - ((System.currentTimeMillis() - warningSince) / ONE_DAY_MS);
            double tax = getWeeklyTax(uuid);
            double balance = plugin.getEconomy() != null ? plugin.getEconomy().getBalance(player) : 0;

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.sendMessage("");
                player.sendMessage(SmallCaps.convert("§c§l⚠ AVISO DE MANTENIMIENTO ⚠"));
                player.sendMessage(SmallCaps.convert("§7No tienes saldo para pagar §e$" + formatMoney(tax) + " §7de mantenimiento."));
                player.sendMessage(SmallCaps.convert("§7Tu saldo: §c$" + formatMoney(balance)));
                player.sendMessage(SmallCaps.convert("§7Quedan §e" + Math.max(0, daysLeft) + " días §7antes de eliminar un core."));
                player.sendMessage("");
            }, 60L); // 3 seconds after join
        }
    }

    // ═══════════════════════════════════════
    // PERSISTENCE
    // ═══════════════════════════════════════

    private void loadPaymentData() {
        org.bukkit.configuration.file.FileConfiguration config = plugin.getDataManager().getDataConfig();
        if (config == null) return;

        org.bukkit.configuration.ConfigurationSection payments = config.getConfigurationSection("maintenance.payments");
        if (payments != null) {
            for (String key : payments.getKeys(false)) {
                try {
                    lastPayment.put(UUID.fromString(key), payments.getLong(key));
                } catch (Exception ignored) {}
            }
        }

        org.bukkit.configuration.ConfigurationSection warnings = config.getConfigurationSection("maintenance.warnings");
        if (warnings != null) {
            for (String key : warnings.getKeys(false)) {
                try {
                    warningStart.put(UUID.fromString(key), warnings.getLong(key));
                } catch (Exception ignored) {}
            }
        }
    }

    public void savePaymentData() {
        org.bukkit.configuration.file.FileConfiguration config = plugin.getDataManager().getDataConfig();
        if (config == null) return;

        config.set("maintenance", null); // Clear old data
        for (Map.Entry<UUID, Long> entry : lastPayment.entrySet()) {
            config.set("maintenance.payments." + entry.getKey().toString(), entry.getValue());
        }
        for (Map.Entry<UUID, Long> entry : warningStart.entrySet()) {
            config.set("maintenance.warnings." + entry.getKey().toString(), entry.getValue());
        }
    }

    // ═══════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════

    public static String formatMoney(double amount) {
        if (amount >= 1_000_000) return String.format("%.1fM", amount / 1_000_000);
        if (amount >= 1_000) return String.format("%,.0f", amount);
        return String.valueOf((int) amount);
    }

    public int getBaseLimit() { return BASE_LIMIT; }
    public Map<String, Integer> getRankBonuses() { return RANK_BONUS; }
}
