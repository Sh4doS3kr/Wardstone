package com.moonlight.coreprotect.gui;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.core.CoreLevel;
import com.moonlight.coreprotect.core.ProtectedRegion;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI that shows ALL core levels so the player can see upgrade path and costs.
 * Supports multiple pages (15 items per page in a 54-slot inventory).
 */
public class CoreUpgradePreviewGUI {

    private static final String GUI_TITLE_PREFIX = SmallCaps.convert(ChatColor.DARK_GRAY + "⬆ Niveles de Núcleo");
    private static final int GUI_SIZE = 54;
    private static final int ITEMS_PER_PAGE = 21; // slots 10-16, 19-25, 28-34

    private final CoreProtectPlugin plugin;

    public CoreUpgradePreviewGUI(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, ProtectedRegion region) {
        open(player, region, 1);
    }

    public void open(Player player, ProtectedRegion region, int page) {
        String title = GUI_TITLE_PREFIX + ChatColor.DARK_GRAY + " [" + page + "]";
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, title);

        // Glass border
        ItemStack glass = createGlass(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) inv.setItem(i, glass);
        for (int i = 45; i < 54; i++) inv.setItem(i, glass);
        for (int r = 0; r < 6; r++) {
            inv.setItem(r * 9, glass);
            inv.setItem(r * 9 + 8, glass);
        }

        // Info header (slot 4)
        ItemStack info = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(SmallCaps.convert(ChatColor.GREEN + "" + ChatColor.BOLD + "⬆ NIVELES DE NÚCLEO"));
        List<String> infoLore = new ArrayList<>();
        infoLore.add("");
        infoLore.add(ChatColor.GRAY + "Tu nivel actual: " + ChatColor.WHITE + region.getLevel());
        infoLore.add(ChatColor.GRAY + "Prestigio: " + ChatColor.WHITE + region.getPrestige());
        infoLore.add("");
        infoLore.add(ChatColor.YELLOW + "Haz click en un nivel para mejorar.");
        infoLore.add(ChatColor.GRAY + "Solo pagas la diferencia de precio.");
        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);
        inv.setItem(4, info);

        // Calculate which levels to show on this page
        int startLevel = (page - 1) * ITEMS_PER_PAGE + 1;
        int endLevel = Math.min(startLevel + ITEMS_PER_PAGE - 1, 30);

        int currentLevel = region.getLevel();
        CoreLevel currentCoreLevel = CoreLevel.fromConfig(plugin.getConfig(), currentLevel);
        double currentPrice = currentCoreLevel != null ? currentCoreLevel.getPrice() : 0;

        // Slots: row2 (10-16), row3 (19-25), row4 (28-34)
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

        int slotIndex = 0;
        for (int lvl = startLevel; lvl <= endLevel && slotIndex < slots.length; lvl++) {
            CoreLevel coreLevel = CoreLevel.fromConfig(plugin.getConfig(), lvl);
            if (coreLevel == null) continue;

            ItemStack item = new ItemStack(coreLevel.getMaterial());
            ItemMeta meta = item.getItemMeta();
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);

            double upgradeCost = coreLevel.getPrice() - currentPrice;
            if (upgradeCost < 0) upgradeCost = 0;

            boolean isCurrentLevel = (lvl == currentLevel);
            boolean isPastLevel = (lvl < currentLevel);
            boolean isNextLevel = (lvl == currentLevel + 1);

            // Display name
            if (isCurrentLevel) {
                meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "★ " + ChatColor.GREEN + "Nivel " + lvl + " — " + coreLevel.getName() + ChatColor.GREEN + " (ACTUAL)");
            } else if (isPastLevel) {
                meta.setDisplayName(ChatColor.DARK_GRAY + "✔ " + ChatColor.GRAY + ChatColor.STRIKETHROUGH + "Nivel " + lvl + " — " + ChatColor.stripColor(coreLevel.getName()));
            } else if (isNextLevel) {
                meta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + "⬆ " + ChatColor.YELLOW + "Nivel " + lvl + " — " + coreLevel.getName());
            } else {
                meta.setDisplayName(ChatColor.WHITE + "Nivel " + lvl + " — " + coreLevel.getName());
            }

            List<String> lore = new ArrayList<>();
            lore.add("");

            if (isPastLevel) {
                lore.add(ChatColor.DARK_GRAY + "Ya superado");
            } else if (isCurrentLevel) {
                lore.add(ChatColor.GREEN + "▸ Este es tu nivel actual");
                lore.add(ChatColor.GRAY + "Área: " + ChatColor.WHITE + coreLevel.getSize() + "x" + coreLevel.getSize());
            } else {
                // Future level
                lore.add(ChatColor.GRAY + "Área: " + ChatColor.WHITE + coreLevel.getSize() + "x" + coreLevel.getSize());
                lore.add(ChatColor.GRAY + "Precio total: " + ChatColor.GOLD + "$" + formatPrice(coreLevel.getPrice()));
                lore.add(ChatColor.GRAY + "Coste mejora: " + ChatColor.GREEN + "$" + formatPrice(upgradeCost));
                lore.add("");

                // Requirements
                boolean hasReqs = false;
                if (coreLevel.requiresPrestige()) {
                    ChatColor reqColor = region.getPrestige() >= coreLevel.getPrestigeRequired() ? ChatColor.GREEN : ChatColor.RED;
                    lore.add(reqColor + "★ Requiere Prestigio Core " + coreLevel.getPrestigeRomanNumeral());
                    hasReqs = true;
                }
                if (coreLevel.requiresGlobalPrestige()) {
                    int playerGP = 0;
                    if (plugin.getPrestigeManager() != null) {
                        playerGP = plugin.getPrestigeManager().getPrestige(player.getUniqueId());
                    }
                    ChatColor reqColor = playerGP >= coreLevel.getGlobalPrestigeRequired() ? ChatColor.GREEN : ChatColor.RED;
                    lore.add(reqColor + "✦ Requiere Prestigio Global " + coreLevel.getGlobalPrestigeRequired());
                    hasReqs = true;
                }
                if (coreLevel.isVip()) {
                    boolean hasVip = player.hasPermission(coreLevel.getVipPermission());
                    ChatColor reqColor = hasVip ? ChatColor.GREEN : ChatColor.RED;
                    lore.add(reqColor + "♦ Requiere rango " + coreLevel.getVipRank().toUpperCase());
                    hasReqs = true;
                }

                if (hasReqs) lore.add("");

                if (isNextLevel) {
                    lore.add(ChatColor.YELLOW + "▶ Click para mejorar a este nivel");
                } else {
                    lore.add(ChatColor.DARK_GRAY + "Mejora nivel a nivel para llegar aquí");
                }
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(slots[slotIndex], item);
            slotIndex++;
        }

        // Page navigation
        int totalPages = (int) Math.ceil(30.0 / ITEMS_PER_PAGE);
        if (page < totalPages) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = next.getItemMeta();
            nextMeta.setDisplayName(SmallCaps.convert(ChatColor.GREEN + "Siguiente ▶"));
            next.setItemMeta(nextMeta);
            inv.setItem(50, next);
        }
        if (page > 1) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prev.getItemMeta();
            prevMeta.setDisplayName(SmallCaps.convert(ChatColor.GREEN + "◀ Anterior"));
            prev.setItemMeta(prevMeta);
            inv.setItem(48, prev);
        }

        // Back button
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName(SmallCaps.convert(ChatColor.RED + "◀ Volver"));
        List<String> backLore = new ArrayList<>();
        backLore.add(SmallCaps.convert(ChatColor.GRAY + "Volver al panel principal"));
        backMeta.setLore(backLore);
        back.setItemMeta(backMeta);
        inv.setItem(49, back);

        player.openInventory(inv);
    }

    public static boolean isUpgradePreviewGUI(String title) {
        return title.contains(SmallCaps.convert("Niveles de Núcleo")) || title.contains("Niveles de N");
    }

    public static int getPageFromTitle(String title) {
        try {
            int start = title.lastIndexOf('[') + 1;
            int end = title.lastIndexOf(']');
            if (start > 0 && end > start) {
                return Integer.parseInt(title.substring(start, end).trim());
            }
        } catch (Exception e) {
            // ignore
        }
        return 1;
    }

    private ItemStack createGlass(Material mat) {
        ItemStack glass = new ItemStack(mat);
        ItemMeta gm = glass.getItemMeta();
        gm.setDisplayName(" ");
        glass.setItemMeta(gm);
        return glass;
    }

    private static String formatPrice(double price) {
        if (price >= 1_000_000) return String.format("%,.0f", price);
        if (price >= 1_000) return String.format("%,.0f", price);
        return String.valueOf((int) price);
    }
}
