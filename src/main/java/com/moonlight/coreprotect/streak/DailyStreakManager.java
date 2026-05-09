package com.moonlight.coreprotect.streak;

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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

public class DailyStreakManager {

    private final CoreProtectPlugin plugin;
    private final File dataFile;
    private FileConfiguration data;

    // UUID -> StreakData
    private final Map<UUID, StreakData> streaks = new HashMap<>();

    public static final String GUI_TITLE = "§8✦ §6§lRacha Diaria §8✦";
    public static final String TOP_TITLE = "§8✦ §e§lTop Rachas §8✦";
    private static final int GUI_SIZE = 54;
    private static final int MAX_DAY = 150;
    private static final int ITEMS_PER_PAGE = 28; // 4 rows x 7 columns

    public DailyStreakManager(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "daily_streaks.yml");
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
                StreakData sd = new StreakData();
                sd.currentStreak = data.getInt("players." + uuidStr + ".streak", 0);
                sd.bestStreak = data.getInt("players." + uuidStr + ".best", 0);
                sd.lastClaimEpoch = data.getLong("players." + uuidStr + ".lastClaim", 0);
                sd.claimedDays = new HashSet<>(data.getIntegerList("players." + uuidStr + ".claimed"));
                streaks.put(uuid, sd);
            } catch (Exception ignored) {}
        }
    }

    public void saveData() {
        for (Map.Entry<UUID, StreakData> e : streaks.entrySet()) {
            String path = "players." + e.getKey().toString();
            StreakData sd = e.getValue();
            data.set(path + ".streak", sd.currentStreak);
            data.set(path + ".best", sd.bestStreak);
            data.set(path + ".lastClaim", sd.lastClaimEpoch);
            data.set(path + ".claimed", new ArrayList<>(sd.claimedDays));
        }
        try {
            data.save(dataFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("Error guardando daily_streaks.yml: " + ex.getMessage());
        }
    }

    public void shutdown() {
        saveData();
    }

    // ===========================
    // STREAK LOGIC
    // ===========================

    public StreakData getStreak(UUID uuid) {
        return streaks.computeIfAbsent(uuid, k -> new StreakData());
    }

    public boolean canClaimToday(UUID uuid) {
        StreakData sd = getStreak(uuid);
        if (sd.lastClaimEpoch == 0) return true;
        LocalDate lastClaim = Instant.ofEpochMilli(sd.lastClaimEpoch).atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate today = LocalDate.now();
        return !lastClaim.equals(today);
    }

    public boolean isStreakBroken(UUID uuid) {
        StreakData sd = getStreak(uuid);
        if (sd.lastClaimEpoch == 0) return false;
        LocalDate lastClaim = Instant.ofEpochMilli(sd.lastClaimEpoch).atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate today = LocalDate.now();
        long daysDiff = java.time.temporal.ChronoUnit.DAYS.between(lastClaim, today);
        return daysDiff > 1;
    }

    public void claimDaily(Player player) {
        UUID uuid = player.getUniqueId();
        StreakData sd = getStreak(uuid);

        if (!canClaimToday(uuid)) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cYa has reclamado tu recompensa diaria hoy."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        // Check broken streak
        if (isStreakBroken(uuid)) {
            sd.currentStreak = 0;
            sd.claimedDays.clear();
        }

        sd.currentStreak++;
        if (sd.currentStreak > sd.bestStreak) sd.bestStreak = sd.currentStreak;
        sd.lastClaimEpoch = System.currentTimeMillis();

        int day = Math.min(sd.currentStreak, MAX_DAY);
        sd.claimedDays.add(day);

        // Give reward
        DailyReward reward = getRewardForDay(day);
        giveReward(player, reward, day);

        saveData();

        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§6§l⭐ §e§l¡RACHA DIARIA! §6Día " + day));
        player.sendMessage(SmallCaps.convert("§7Recompensa: " + reward.description));
        if (day % 30 == 0) {
            Bukkit.broadcastMessage(SmallCaps.convert("§6§l⭐ §e" + player.getName() + " §fha alcanzado §6" + day + " días §fde racha diaria! §e¡Increíble!"));
        }
        player.sendMessage("");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
    }

    // ===========================
    // REWARDS FOR EACH DAY
    // ===========================

    public DailyReward getRewardForDay(int day) {
        // Special milestone days
        if (day == 7) return new DailyReward(10000, 0, "rare", 2, 0, "§b2x Llave Rara + §f$10,000");
        if (day == 14) return new DailyReward(15000, 50, "rare", 3, 0, "§b3x Llave Rara + §a50 Esencias + §f$15,000");
        if (day == 21) return new DailyReward(20000, 0, "special", 1, 0, "§61x Llave Especial + §f$20,000");
        if (day == 30) return new DailyReward(30000, 100, "special", 2, 0, "§62x Llave Especial + §a100 Esencias + §f$30,000");
        if (day == 35) return new DailyReward(35000, 120, "special", 2, 0, "§62x Llave Especial + §a120 Esencias + §f$35,000 + §b✦ Vuelo 3d");
        if (day == 45) return new DailyReward(40000, 150, "legendary", 1, 0, "§d1x Llave Legendaria + §a150 Esencias + §f$40,000");
        if (day == 60) return new DailyReward(50000, 200, "legendary", 2, 0, "§d2x Llave Legendaria + §a200 Esencias + §f$50,000");
        if (day == 75) return new DailyReward(60000, 250, "legendary", 3, 0, "§d3x Llave Legendaria + §a250 Esencias + §f$60,000 + §b✦ Vuelo 7d");
        if (day == 90) return new DailyReward(80000, 300, "moon", 1, 0, "§51x Llave Moon + §a300 Esencias + §f$80,000 + §c§l⚔ Llave Espadas");
        if (day == 100) return new DailyReward(100000, 400, "moon", 2, 0, "§52x Llave Moon + §a400 Esencias + §f$100,000");
        if (day == 120) return new DailyReward(150000, 500, "moon", 3, 0, "§53x Llave Moon + §a500 Esencias + §f$150,000 + §c§l⚔ Llave Espadas + §b✦ Vuelo 14d");
        if (day == 150) return new DailyReward(300000, 1000, "moon", 5, 0, "§5§l5x Llave Moon + §a§l1000 Esencias + §f§l$300,000 + §c§l⚔ Llave Espadas");

        // Weekly milestones (every 7 days not already special)
        if (day % 7 == 0) {
            int tier = day / 7;
            int money = 5000 + tier * 2000;
            int essences = 20 + tier * 10;
            return new DailyReward(money, essences, "rare", 1, 0, "§b1x Llave Rara + §a" + essences + " Esencias + §f$" + String.format("%,d", money));
        }

        // Normal days
        if (day <= 10) return new DailyReward(1000, 0, "common", 1, 0, "§e1x Llave Común + §f$1,000");
        if (day <= 20) return new DailyReward(1500, 10, "common", 1, 0, "§e1x Llave Común + §a10 Esencias + §f$1,500");
        if (day <= 30) return new DailyReward(2000, 15, "common", 2, 0, "§e2x Llave Común + §a15 Esencias + §f$2,000");
        if (day <= 50) return new DailyReward(3000, 20, "common", 2, 0, "§e2x Llave Común + §a20 Esencias + §f$3,000");
        if (day <= 75) return new DailyReward(4000, 30, "rare", 1, 0, "§b1x Llave Rara + §a30 Esencias + §f$4,000");
        if (day <= 100) return new DailyReward(5000, 40, "rare", 1, 0, "§b1x Llave Rara + §a40 Esencias + §f$5,000");
        if (day <= 120) return new DailyReward(6000, 50, "special", 1, 0, "§61x Llave Especial + §a50 Esencias + §f$6,000");
        return new DailyReward(8000, 60, "special", 1, 0, "§61x Llave Especial + §a60 Esencias + §f$8,000");
    }

    private void giveReward(Player player, DailyReward reward, int day) {
        if (reward.money > 0) {
            plugin.depositWithMultiplier(player, reward.money);
        }
        if (reward.essences > 0 && plugin.getEssenceManager() != null) {
            plugin.getEssenceManager().addEssences(player.getUniqueId(), reward.essences);
        }
        if (reward.keyType != null && reward.keyAmount > 0) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "excellentcrates key give " + player.getName() + " " + reward.keyType + " " + reward.keyAmount);
        }
        // Llave de Espadas en días hito: 90, 120, 150
        if (day == 90 || day == 120 || day == 150) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "excellentcrates key give " + player.getName() + " llave_espadas 1");
            player.sendMessage(SmallCaps.convert("§c§l⚔ §f¡Has obtenido una §c§lLlave de Espadas§f! §7(Racha día " + day + ")"));
            Bukkit.broadcastMessage(SmallCaps.convert("§c§l⚔ §e" + player.getName() + " §fha obtenido una §c§lLlave de Espadas §fpor §e" + day + " §fdías de racha!"));
        }
        // Vuelo temporal en días hito: 35 (3d), 75 (7d), 120 (14d)
        if (day == 35) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "lp user " + player.getName() + " permission settemp essentials.fly true 3d");
            player.sendMessage(SmallCaps.convert("§b§l✦ §f¡Vuelo activado por §b3 días§f! §7(Racha día 35)"));
            Bukkit.broadcastMessage(SmallCaps.convert("§b§l✦ §e" + player.getName() + " §fha desbloqueado §b3 días de vuelo §fpor §e35 §fdías de racha!"));
        } else if (day == 75) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "lp user " + player.getName() + " permission settemp essentials.fly true 7d");
            player.sendMessage(SmallCaps.convert("§b§l✦ §f¡Vuelo activado por §b7 días§f! §7(Racha día 75)"));
            Bukkit.broadcastMessage(SmallCaps.convert("§b§l✦ §e" + player.getName() + " §fha desbloqueado §b7 días de vuelo §fpor §e75 §fdías de racha!"));
        } else if (day == 120) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "lp user " + player.getName() + " permission settemp essentials.fly true 14d");
            player.sendMessage(SmallCaps.convert("§b§l✦ §f¡Vuelo activado por §b14 días§f! §7(Racha día 120)"));
            Bukkit.broadcastMessage(SmallCaps.convert("§b§l✦ §e" + player.getName() + " §fha desbloqueado §b14 días de vuelo §fpor §e120 §fdías de racha!"));
        }
    }

    // ===========================
    // GUI
    // ===========================

    public void openGUI(Player player, int page) {
        UUID uuid = player.getUniqueId();
        StreakData sd = getStreak(uuid);

        // Check if streak is broken
        if (isStreakBroken(uuid)) {
            sd.currentStreak = 0;
            sd.claimedDays.clear();
        }

        int totalPages = (int) Math.ceil((double) MAX_DAY / ITEMS_PER_PAGE);
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        String title = GUI_TITLE + " §7(" + (page + 1) + "/" + totalPages + ")";
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, title);

        // Top border
        ItemStack border = createItem(Material.BLACK_STAINED_GLASS_PANE, "§r");
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        // Bottom border
        for (int i = 45; i < 54; i++) inv.setItem(i, border);
        // Side borders
        for (int row = 1; row <= 4; row++) {
            inv.setItem(row * 9, border);
            inv.setItem(row * 9 + 8, border);
        }

        // Player info head (slot 4)
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) head.getItemMeta();
        skullMeta.setOwningPlayer(player);
        skullMeta.setDisplayName("§6§l⭐ " + player.getName());
        List<String> headLore = new ArrayList<>();
        headLore.add("");
        headLore.add(SmallCaps.convert("§fRacha actual: §e" + sd.currentStreak + " días"));
        headLore.add(SmallCaps.convert("§fMejor racha: §6" + sd.bestStreak + " días"));
        headLore.add("");
        if (canClaimToday(uuid)) {
            headLore.add(SmallCaps.convert("§a§l▶ ¡Recompensa disponible!"));
        } else {
            headLore.add(SmallCaps.convert("§7Ya reclamado hoy. Vuelve mañana."));
        }
        headLore.add("");
        skullMeta.setLore(headLore);
        head.setItemMeta(skullMeta);
        inv.setItem(4, head);

        // Day items in slots: rows 1-4, columns 1-7
        int startDay = page * ITEMS_PER_PAGE + 1;
        int slot = 10; // First content slot (row 1, col 1)
        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            int day = startDay + i;
            if (day > MAX_DAY) break;

            int row = i / 7;
            int col = i % 7;
            int guiSlot = (row + 1) * 9 + (col + 1);

            DailyReward reward = getRewardForDay(day);
            boolean claimed = sd.claimedDays.contains(day);
            boolean isCurrentDay = (day == sd.currentStreak + 1) && canClaimToday(uuid);
            boolean isLocked = day > sd.currentStreak + 1;
            boolean isPast = day <= sd.currentStreak;

            Material mat;
            String statusPrefix;

            if (claimed || isPast) {
                mat = Material.LIME_STAINED_GLASS_PANE;
                statusPrefix = "§a§l✓ ";
            } else if (isCurrentDay) {
                mat = Material.SUNFLOWER;
                statusPrefix = "§e§l▶ ";
            } else if (day == sd.currentStreak + 1) {
                mat = Material.ORANGE_STAINED_GLASS_PANE;
                statusPrefix = "§6§l! ";
            } else {
                mat = Material.RED_STAINED_GLASS_PANE;
                statusPrefix = "§c";
            }

            // Special milestone materials
            if (!claimed && !isPast) {
                if (day == 30 || day == 60 || day == 90 || day == 120 || day == 150) mat = isCurrentDay ? Material.SUNFLOWER : Material.NETHER_STAR;
                else if (day % 7 == 0 && (isCurrentDay)) mat = Material.SUNFLOWER;
                else if (day % 7 == 0 && !isLocked) mat = Material.GOLD_INGOT;
            }
            if (isCurrentDay) mat = Material.SUNFLOWER;

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(statusPrefix + "§fDía " + day);
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(SmallCaps.convert("§7Recompensa:"));
            lore.add(SmallCaps.convert("§f" + reward.description));
            lore.add("");
            if (claimed || isPast) {
                lore.add(SmallCaps.convert("§a✓ Reclamado"));
            } else if (isCurrentDay) {
                lore.add(SmallCaps.convert("§e§l▶ ¡Click para reclamar!"));
            } else {
                int pct = day <= 0 ? 0 : Math.min(100, (sd.currentStreak * 100) / day);
                // Progress bar (10 chars)
                int filled = pct / 10;
                StringBuilder bar = new StringBuilder("§a");
                for (int b = 0; b < 10; b++) {
                    if (b == filled) bar.append("§c");
                    bar.append("■");
                }
                lore.add(SmallCaps.convert("§7Progreso: §f" + sd.currentStreak + "§7/§f" + day + " días §8(§e" + pct + "%§8)"));
                lore.add(bar.toString());
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(guiSlot, item);
        }

        // Claim all button (slot 49) if available
        if (canClaimToday(uuid)) {
            ItemStack claim = createItem(Material.EMERALD, "§a§l⭐ RECLAMAR RECOMPENSA DIARIA");
            ItemMeta claimMeta = claim.getItemMeta();
            DailyReward nextReward = getRewardForDay(Math.min(sd.currentStreak + 1, MAX_DAY));
            claimMeta.setLore(Arrays.asList(
                    "",
                    SmallCaps.convert("§7Día §e" + (sd.currentStreak + 1)),
                    SmallCaps.convert("§f" + nextReward.description),
                    "",
                    SmallCaps.convert("§a§l▶ Click para reclamar")
            ));
            claim.setItemMeta(claimMeta);
            inv.setItem(49, claim);
        } else {
            ItemStack noClaim = createItem(Material.BARRIER, "§c§lYa reclamado hoy");
            ItemMeta noMeta = noClaim.getItemMeta();
            noMeta.setLore(Arrays.asList("", SmallCaps.convert("§7Vuelve mañana para continuar tu racha.")));
            noClaim.setItemMeta(noMeta);
            inv.setItem(49, noClaim);
        }

        // Navigation
        if (page > 0) {
            ItemStack prev = createItem(Material.ARROW, "§e§l◀ Página anterior");
            inv.setItem(45, prev);
        }
        if (page < totalPages - 1) {
            ItemStack next = createItem(Material.ARROW, "§e§lPágina siguiente ▶");
            inv.setItem(53, next);
        }

        // Page indicator
        ItemStack pageItem = createItem(Material.PAPER, "§7Página " + (page + 1) + " / " + totalPages);
        inv.setItem(47, pageItem);

        // Top button (slot 51)
        ItemStack topBtn = createItem(Material.DIAMOND, "§e§l🏆 Top Rachas");
        ItemMeta topMeta = topBtn.getItemMeta();
        topMeta.setLore(Arrays.asList("", SmallCaps.convert("§7Ver los jugadores con mejor racha"), ""));
        topBtn.setItemMeta(topMeta);
        inv.setItem(51, topBtn);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.2f);
    }

    public void openTopGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, TOP_TITLE);

        ItemStack border = createItem(Material.BLACK_STAINED_GLASS_PANE, "§r");
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);

        inv.setItem(4, createItem(Material.DIAMOND, "§e§l🏆 Top Rachas Diarias"));

        // Sort by best streak descending
        List<Map.Entry<UUID, StreakData>> sorted = streaks.entrySet().stream()
                .filter(e -> e.getValue().bestStreak > 0)
                .sorted((a, b) -> {
                    int cmp = Integer.compare(b.getValue().bestStreak, a.getValue().bestStreak);
                    if (cmp != 0) return cmp;
                    return Integer.compare(b.getValue().currentStreak, a.getValue().currentStreak);
                })
                .limit(21)
                .collect(Collectors.toList());

        for (int i = 0; i < sorted.size(); i++) {
            UUID uuid = sorted.get(i).getKey();
            StreakData sd = sorted.get(i).getValue();
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            if (name == null) name = "???";

            int slot = 10 + i + (i / 7) * 2;
            if (slot >= 45) break;

            String medal = i == 0 ? "§e🥇" : i == 1 ? "§7🥈" : i == 2 ? "§6🥉" : "§f" + (i + 1) + ".";
            String streakColor = sd.bestStreak >= 100 ? "§6§l" : sd.bestStreak >= 50 ? "§e" : sd.bestStreak >= 20 ? "§a" : "§f";

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) head.getItemMeta();
            sm.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
            sm.setDisplayName(medal + " §f" + name);
            sm.setLore(Arrays.asList(
                    "",
                    SmallCaps.convert("§fMejor racha: " + streakColor + sd.bestStreak + " días"),
                    SmallCaps.convert("§fRacha actual: §e" + sd.currentStreak + " días"),
                    ""
            ));
            head.setItemMeta(sm);
            inv.setItem(slot, head);
        }

        if (sorted.isEmpty()) {
            inv.setItem(22, createItem(Material.BARRIER, SmallCaps.convert("§7Nadie ha comenzado una racha aún")));
        }

        inv.setItem(49, createItem(Material.ARROW, "§e§l◀ Volver"));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.2f);
    }

    private ItemStack createItem(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    // ===========================
    // INNER CLASSES
    // ===========================

    public static class StreakData {
        public int currentStreak = 0;
        public int bestStreak = 0;
        public long lastClaimEpoch = 0;
        public Set<Integer> claimedDays = new HashSet<>();
    }

    public static class DailyReward {
        public int money;
        public int essences;
        public String keyType;
        public int keyAmount;
        public int extraItems;
        public String description;

        public DailyReward(int money, int essences, String keyType, int keyAmount, int extraItems, String description) {
            this.money = money;
            this.essences = essences;
            this.keyType = keyType;
            this.keyAmount = keyAmount;
            this.extraItems = extraItems;
            this.description = description;
        }
    }
}
