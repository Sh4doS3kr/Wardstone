package com.moonlight.coreprotect.rewards;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class PlayTimeGUI {

    public static final String GUI_TITLE = "§8✦ §b§lTiempo Jugado §8✦";
    private static final int GUI_SIZE = 54;

    // Reward tiers: seconds required
    public static final long[] TIERS = {
            900L,     // 15 min
            1800L,    // 30 min
            3600L,    // 1h
            5400L,    // 1h 30m
            7200L,    // 2h
            10800L,   // 3h
            14400L,   // 4h
            18000L,   // 5h
            21600L,   // 6h
            28800L,   // 8h
            36000L,   // 10h
            43200L,   // 12h
            57600L,   // 16h
            72000L,   // 20h
            86400L,   // 1 día
            129600L,  // 1.5 días
            172800L,  // 2 días
            259200L,  // 3 días
            345600L,  // 4 días
            432000L,  // 5 días
            518400L,  // 6 días
            604800L,  // 1 semana
            864000L,  // 10 días
            1209600L, // 2 semanas
            1814400L, // 3 semanas
            2592000L, // 1 mes
            5184000L, // 2 meses
            7776000L, // 3 meses
    };

    private static final int ITEMS_PER_PAGE = 28;

    public static String[][] REWARDS = {
            {"$500", "common", "1", "0", "null"},        // 15 min
            {"$2,000", "common", "1", "0", "null"},      // 30 min
            {"$5,000", "common", "2", "0", "null"},      // 1h
            {"$7,000", "common", "2", "2", "IRON_INGOT:16"},     // 1h 30m
            {"$10,000", "rare", "1", "3", "GOLD_INGOT:8"},      // 2h
            {"$15,000", "rare", "1", "5", "DIAMOND:4"},      // 3h
            {"$25,000", "rare", "2", "7", "IRON_INGOT:32"},      // 4h
            {"$30,000", "special", "1", "10", "GOLD_INGOT:16"},   // 5h
            {"$40,000", "special", "1", "12", "DIAMOND:8"},   // 6h
            {"$50,000", "special", "2", "15", "IRON_INGOT:64"},   // 8h
            {"$60,000", "legendary", "1", "18", "GOLD_INGOT:32"}, // 10h
            {"$75,000", "legendary", "1", "20", "DIAMOND:16"},// 12h
            {"$90,000", "legendary", "2", "25", "EMERALD:8"},// 16h
            {"$110,000", "legendary", "2", "30", "DIAMOND:24"},// 20h
            {"$150,000", "legendary", "3", "35", "GOLD_INGOT:64"},// 1 día
            {"$200,000", "moon", "1", "40", "DIAMOND:32"},    // 1.5 días
            {"$250,000", "moon", "1", "50", "EMERALD:16"},    // 2 días
            {"$300,000", "moon", "2", "60", "DIAMOND:48"},    // 3 días
            {"$350,000", "moon", "2", "70", "GOLD_INGOT:128"},    // 4 días
            {"$400,000", "moon", "3", "80", "DIAMOND:64"},    // 5 días
            {"$450,000", "moon", "3", "90", "EMERALD:32"},    // 6 días
            {"$500,000", "moon", "4", "100", "DIAMOND:80"},    // 1 semana
            {"$600,000", "moon", "5", "120", "GOLD_INGOT:256"},    // 10 días
            {"$750,000", "moon", "5", "150", "DIAMOND:96"},    // 2 semanas
            {"$900,000", "moon", "7", "180", "EMERALD:64"},   // 3 semanas
            {"$1,000,000", "moon", "10", "200", "DIAMOND:128"},// 1 mes
            {"$2,000,000", "moon", "15", "300", "GOLD_INGOT:512"},// 2 meses
            {"$5,000,000", "moon", "20", "500", "DIAMOND:256"},// 3 meses
    };

    public static void open(CoreProtectPlugin plugin, Player player, int page) {
        PlayTimeRewardsManager mgr = plugin.getPlayTimeRewardsManager();
        if (mgr == null) return;

        UUID uuid = player.getUniqueId();
        long totalSeconds = mgr.getPlayerTotalPlayTime(uuid);

        int totalPages = (int) Math.ceil((double) TIERS.length / ITEMS_PER_PAGE);
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        String title = GUI_TITLE + " §7(" + (page + 1) + "/" + totalPages + ")";
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, title);

        // Borders
        ItemStack border = createItem(Material.BLACK_STAINED_GLASS_PANE, "§r");
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);
        for (int row = 1; row <= 4; row++) {
            inv.setItem(row * 9, border);
            inv.setItem(row * 9 + 8, border);
        }

        // Player head with stats (slot 4)
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) head.getItemMeta();
        skullMeta.setOwningPlayer(player);
        skullMeta.setDisplayName("§b§l⏱ " + player.getName());
        List<String> headLore = new ArrayList<>();
        headLore.add("");
        headLore.add(SmallCaps.convert("§fTiempo total: §e" + formatTime(totalSeconds)));
        int claimed = 0;
        int available = 0;
        for (int i = 0; i < TIERS.length; i++) {
            if (mgr.isPlayTimeClaimed(uuid, "playtime_" + TIERS[i])) claimed++;
            else if (totalSeconds >= TIERS[i]) available++;
        }
        headLore.add(SmallCaps.convert("§fReclamadas: §a" + claimed + "§7/§f" + TIERS.length));
        headLore.add(SmallCaps.convert("§fDisponibles: §e" + available));
        headLore.add("");
        skullMeta.setLore(headLore);
        head.setItemMeta(skullMeta);
        inv.setItem(4, head);

        // Reward items
        int start = page * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            int idx = start + i;
            if (idx >= TIERS.length) break;

            int row = i / 7;
            int col = i % 7;
            int guiSlot = (row + 1) * 9 + (col + 1);

            long req = TIERS[idx];
            boolean isClaimed = mgr.isPlayTimeClaimed(uuid, "playtime_" + req);
            boolean isAvailable = totalSeconds >= req && !isClaimed;
            boolean locked = totalSeconds < req;

            Material mat;
            String prefix;
            if (isClaimed) {
                mat = Material.LIME_STAINED_GLASS_PANE;
                prefix = "§a§l✓ ";
            } else if (isAvailable) {
                mat = Material.SUNFLOWER;
                prefix = "§e§l▶ ";
            } else {
                mat = Material.GRAY_STAINED_GLASS_PANE;
                prefix = "§7§l✖ ";
            }

            // Milestone materials
            if (!isClaimed && !isAvailable) {
                if (req >= 2592000L) mat = Material.NETHER_STAR;
                else if (req >= 604800L) mat = Material.DIAMOND;
                else if (req >= 86400L) mat = Material.GOLD_INGOT;
            }
            if (isAvailable) mat = Material.SUNFLOWER;

            String[] rewardData = REWARDS[idx];
            String keyName = getKeyDisplayName(rewardData[1]);

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(prefix + "§f" + formatTime(req));
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(SmallCaps.convert("§7Recompensa:"));
            lore.add(SmallCaps.convert("§f" + rewardData[0] + " + " + keyName + " x" + rewardData[2]));
            if (Integer.parseInt(rewardData[3]) > 0) {
                lore.add(SmallCaps.convert("§a" + rewardData[3] + " Esencias"));
            }
            if (rewardData.length > 4 && !rewardData[4].equals("null")) {
                String[] itemParts = rewardData[4].split(":");
                if (itemParts.length == 2) {
                    try {
                        Material itemMat = Material.valueOf(itemParts[0]);
                        int amount = Integer.parseInt(itemParts[1]);
                        lore.add(SmallCaps.convert("§f" + amount + "x " + itemMat.name().replace("_", " ").toLowerCase()));
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
            // Mostrar Llave de Espadas en hitos de 1 mes y 3 meses
            if (req == 2592000L || req == 7776000L) {
                lore.add(SmallCaps.convert("§c§l⚔ 1x Llave de Espadas"));
            }
            lore.add("");
            if (isClaimed) {
                lore.add(SmallCaps.convert("§a✓ Reclamado"));
            } else if (isAvailable) {
                lore.add(SmallCaps.convert("§e§l▶ ¡Click para reclamar!"));
            } else {
                long remaining = req - totalSeconds;
                lore.add(SmallCaps.convert("§7Faltan: §f" + formatTime(remaining)));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(guiSlot, item);
        }

        // Navigation
        if (page > 0) {
            inv.setItem(45, createItem(Material.ARROW, "§e§l◀ Página anterior"));
        }
        if (page < totalPages - 1) {
            inv.setItem(53, createItem(Material.ARROW, "§e§lPágina siguiente ▶"));
        }

        // Claim all button
        if (available > 0) {
            ItemStack claimAll = createItem(Material.EMERALD, "§a§l⭐ RECLAMAR TODO (" + available + ")");
            ItemMeta cm = claimAll.getItemMeta();
            cm.setLore(Arrays.asList("", SmallCaps.convert("§a§l▶ Click para reclamar todas las pendientes")));
            claimAll.setItemMeta(cm);
            inv.setItem(49, claimAll);
        }

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.2f);
    }

    public static void claimReward(CoreProtectPlugin plugin, Player player, int tierIndex) {
        PlayTimeRewardsManager mgr = plugin.getPlayTimeRewardsManager();
        if (mgr == null) return;
        UUID uuid = player.getUniqueId();
        long totalSeconds = mgr.getPlayerTotalPlayTime(uuid);

        if (tierIndex < 0 || tierIndex >= TIERS.length) return;
        long req = TIERS[tierIndex];
        String key = "playtime_" + req;
        if (totalSeconds < req || mgr.isPlayTimeClaimed(uuid, key)) return;

        String[] rd = REWARDS[tierIndex];
        int money = parseMoneyString(rd[0]);
        String keyType = rd[1];
        int keyAmount = Integer.parseInt(rd[2]);
        int essences = Integer.parseInt(rd[3]);

        if (money > 0) {
            plugin.depositWithMultiplier(player, money);
        }
        if (keyAmount > 0) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "excellentcrates key give " + player.getName() + " " + keyType + " " + keyAmount);
        }
        if (essences > 0 && plugin.getEssenceManager() != null) {
            plugin.getEssenceManager().addEssences(uuid, essences);
            plugin.getEssenceManager().saveData();
            player.sendMessage(SmallCaps.convert("§d§l✦ §a+" + essences + " Esencias"));
        }

        // Dar item custom si existe (rd[4])
        if (rd.length > 4 && !rd[4].equals("null")) {
            String[] itemParts = rd[4].split(":");
            if (itemParts.length == 2) {
                try {
                    Material mat = Material.valueOf(itemParts[0]);
                    int amount = Integer.parseInt(itemParts[1]);
                    ItemStack item = new ItemStack(mat, amount);
                    player.getInventory().addItem(item);
                    player.sendMessage(SmallCaps.convert("§7• §f" + amount + "x " + mat.name().replace("_", " ").toLowerCase()));
                } catch (Exception e) {
                    plugin.getLogger().warning("Error al dar item de playtime: " + rd[4]);
                }
            }
        }

        // Llave de Espadas en hitos de playtime: 1 mes y 3 meses
        if (req == 2592000L || req == 7776000L) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "excellentcrates key give " + player.getName() + " llave_espadas 1");
            player.sendMessage(SmallCaps.convert("§c§l⚔ §f¡Has obtenido una §c§lLlave de Espadas§f! §7(Tiempo jugado)"));
            Bukkit.broadcastMessage(SmallCaps.convert("§c§l⚔ §e" + player.getName() + " §fha obtenido una §c§lLlave de Espadas §fpor §e" + formatTime(req) + " §fde tiempo jugado!"));
        }

        mgr.markPlayTimeClaimed(uuid, key);

        player.sendMessage(SmallCaps.convert("§a§l✔ §fRecompensa de §e" + formatTime(req) + " §freclamada!"));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
    }

    public static void claimAll(CoreProtectPlugin plugin, Player player) {
        PlayTimeRewardsManager mgr = plugin.getPlayTimeRewardsManager();
        if (mgr == null) return;
        UUID uuid = player.getUniqueId();
        long totalSeconds = mgr.getPlayerTotalPlayTime(uuid);
        int count = 0;

        for (int i = 0; i < TIERS.length; i++) {
            long req = TIERS[i];
            String key = "playtime_" + req;
            if (totalSeconds >= req && !mgr.isPlayTimeClaimed(uuid, key)) {
                String[] rd = REWARDS[i];
                int money = parseMoneyString(rd[0]);
                String keyType = rd[1];
                int keyAmount = Integer.parseInt(rd[2]);
                int essences = Integer.parseInt(rd[3]);

                if (money > 0) plugin.depositWithMultiplier(player, money);
                if (keyAmount > 0) Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        "excellentcrates key give " + player.getName() + " " + keyType + " " + keyAmount);
                if (essences > 0 && plugin.getEssenceManager() != null) {
                    plugin.getEssenceManager().addEssences(uuid, essences);
                    plugin.getEssenceManager().saveData();
                }

                // Dar item custom si existe (rd[4])
                if (rd.length > 4 && !rd[4].equals("null")) {
                    String[] itemParts = rd[4].split(":");
                    if (itemParts.length == 2) {
                        try {
                            Material mat = Material.valueOf(itemParts[0]);
                            int amount = Integer.parseInt(itemParts[1]);
                            ItemStack item = new ItemStack(mat, amount);
                            player.getInventory().addItem(item);
                        } catch (Exception e) {
                            plugin.getLogger().warning("Error al dar item de playtime: " + rd[4]);
                        }
                    }
                }

                // Llave de Espadas en hitos de playtime: 1 mes y 3 meses
                if (req == 2592000L || req == 7776000L) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                            "excellentcrates key give " + player.getName() + " llave_espadas 1");
                    player.sendMessage(SmallCaps.convert("§c§l⚔ §f¡Has obtenido una §c§lLlave de Espadas§f! §7(Tiempo jugado)"));
                    Bukkit.broadcastMessage(SmallCaps.convert("§c§l⚔ §e" + player.getName() + " §fha obtenido una §c§lLlave de Espadas §fpor §e" + formatTime(req) + " §fde tiempo jugado!"));
                }

                mgr.markPlayTimeClaimed(uuid, key);
                count++;
            }
        }

        if (count > 0) {
            player.sendMessage(SmallCaps.convert("§a§l✔ §f¡" + count + " recompensas reclamadas!"));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        } else {
            player.sendMessage(SmallCaps.convert("§cNo tienes recompensas pendientes."));
        }
    }

    private static int parseMoneyString(String s) {
        return Integer.parseInt(s.replace("$", "").replace(",", "").replace(".", ""));
    }

    private static String getKeyDisplayName(String type) {
        switch (type) {
            case "common": return "§eLlave Común";
            case "rare": return "§bLlave Rara";
            case "special": return "§6Llave Especial";
            case "legendary": return "§dLlave Legendaria";
            case "moon": return "§5Llave Moon";
            default: return "§fLlave";
        }
    }

    static String formatTime(long seconds) {
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + " min";
        if (seconds < 86400) {
            long h = seconds / 3600;
            long m = (seconds % 3600) / 60;
            return m == 0 ? h + "h" : h + "h " + m + "m";
        }
        long d = seconds / 86400;
        long h = (seconds % 86400) / 3600;
        return h == 0 ? d + "d" : d + "d " + h + "h";
    }

    private static ItemStack createItem(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }
}
