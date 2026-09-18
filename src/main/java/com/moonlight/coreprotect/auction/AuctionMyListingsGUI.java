package com.moonlight.coreprotect.auction;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * GUI de "Mis Subastas": ver listings activos, recoger expirados.
 * Layout 54 slots:
 * - Fila 0: Header + stats
 * - Filas 1-4: Items del jugador (28 slots)
 * - Fila 5: Navegación
 */
public class AuctionMyListingsGUI {

    static final String TITLE_PREFIX = SmallCaps.convert("§8§l✦ §b§lMis Subastas");
    private static final int ITEMS_PER_PAGE = 28;

    private static final int[] ITEM_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    // Pestañas
    public enum Tab {
        ACTIVOS("Activos", "§a"),
        EXPIRADOS("Expirados", "§c");

        private final String name;
        private final String color;
        Tab(String name, String color) { this.name = name; this.color = color; }
        public String getDisplayName() { return color + name; }
    }

    public static String getTitle(Tab tab, int page) {
        return TITLE_PREFIX + " §7" + tab.getDisplayName() + " §7(Pág. " + page + ")";
    }

    public static boolean isMyListingsGUI(String title) {
        return title != null && title.startsWith(TITLE_PREFIX);
    }

    public static Tab getTabFromTitle(String title) {
        if (title.contains("§c")) return Tab.EXPIRADOS;
        return Tab.ACTIVOS;
    }

    public static int getPageFromTitle(String title) {
        try {
            String num = title.substring(title.lastIndexOf(" ") + 1, title.length() - 1);
            return Integer.parseInt(num);
        } catch (Exception e) {
            return 1;
        }
    }

    /**
     * Abre la GUI de mis subastas.
     */
    public static void open(Player player, AuctionManager manager, Tab tab, int page) {
        List<AuctionItem> items;
        if (tab == Tab.ACTIVOS) {
            items = manager.getActiveListings(player.getUniqueId());
        } else {
            items = manager.getExpiredListings(player.getUniqueId());
        }

        int totalPages = Math.max(1, (int) Math.ceil(items.size() / (double) ITEMS_PER_PAGE));
        page = Math.max(1, Math.min(page, totalPages));

        Inventory inv = Bukkit.createInventory(null, 54, getTitle(tab, page));

        // Bordes
        ItemStack border = AuctionGUI.createItem(Material.BLACK_STAINED_GLASS_PANE, "§r");
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);
        inv.setItem(9, border); inv.setItem(18, border); inv.setItem(27, border); inv.setItem(36, border);
        inv.setItem(17, border); inv.setItem(26, border); inv.setItem(35, border); inv.setItem(44, border);

        // Header con stats
        ItemStack stats = AuctionGUI.createItem(Material.PLAYER_HEAD, "§b§l✦ Mis Subastas");
        ItemMeta statsMeta = stats.getItemMeta();
        int activeCount = manager.getActiveListings(player.getUniqueId()).size();
        int expiredCount = manager.getExpiredListings(player.getUniqueId()).size();
        int totalSold = manager.getTotalSold(player.getUniqueId());
        double earnings = manager.getTotalEarnings(player.getUniqueId());
        statsMeta.setLore(Arrays.asList(
            "§8§m                              ",
            "§aListings activos: §f" + activeCount + "§7/" + AuctionItem.MAX_LISTINGS,
            "§cItems expirados: §f" + expiredCount,
            "§eItems vendidos: §f" + totalSold,
            "§6Ganancias totales: §f$" + String.format("%,.0f", earnings),
            "§8§m                              "
        ));
        stats.setItemMeta(statsMeta);
        inv.setItem(4, stats);

        // Pestañas
        ItemStack activeTab = AuctionGUI.createItem(
            tab == Tab.ACTIVOS ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
            (tab == Tab.ACTIVOS ? "§a§l▸ " : "§7") + "Activos"
        );
        ItemMeta activeTabMeta = activeTab.getItemMeta();
        activeTabMeta.setLore(SmallCaps.convertList(Arrays.asList("§7Ver listings activos", "§e▶ Click para cambiar")));
        if (tab == Tab.ACTIVOS) {
            activeTabMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
            activeTabMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        activeTab.setItemMeta(activeTabMeta);
        inv.setItem(2, activeTab);

        ItemStack expiredTab = AuctionGUI.createItem(
            tab == Tab.EXPIRADOS ? Material.RED_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
            (tab == Tab.EXPIRADOS ? "§c§l▸ " : "§7") + "Expirados"
        );
        ItemMeta expiredTabMeta = expiredTab.getItemMeta();
        expiredTabMeta.setLore(SmallCaps.convertList(Arrays.asList("§7Ver items expirados", "§7para recoger", "§e▶ Click para cambiar")));
        if (tab == Tab.EXPIRADOS) {
            expiredTabMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
            expiredTabMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        expiredTab.setItemMeta(expiredTabMeta);
        inv.setItem(6, expiredTab);

        // Items
        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            int dataIndex = startIndex + i;
            if (dataIndex < items.size()) {
                inv.setItem(ITEM_SLOTS[i], createMyItemDisplay(items.get(dataIndex), tab));
            }
        }

        // Navegación
        if (page > 1) {
            inv.setItem(45, AuctionGUI.createNavItem(Material.ARROW, "§a◀ Página anterior", "§7Ir a página " + (page - 1)));
        }

        // Volver al AH
        ItemStack back = AuctionGUI.createItem(Material.ARROW, "§f§lVolver al Auction House");
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setLore(SmallCaps.convertList(Arrays.asList("§e▶ Click para volver")));
        back.setItemMeta(backMeta);
        inv.setItem(49, back);

        if (page < totalPages) {
            inv.setItem(53, AuctionGUI.createNavItem(Material.ARROW, "§aPágina siguiente ▶", "§7Ir a página " + (page + 1)));
        }

        // Recoger todos (solo en pestaña expirados)
        if (tab == Tab.EXPIRADOS && !items.isEmpty()) {
            ItemStack collectAll = AuctionGUI.createItem(Material.HOPPER, "§e§lRecoger todos");
            ItemMeta collectMeta = collectAll.getItemMeta();
            collectMeta.setLore(Arrays.asList(
                "§7Recoge todos los items",
                "§7expirados a tu inventario.",
                "",
                "§e▶ Click para recoger"
            ));
            collectAll.setItemMeta(collectMeta);
            inv.setItem(51, collectAll);
        }

        player.openInventory(inv);
    }

    /**
     * Crea el display de un item del jugador.
     */
    private static ItemStack createMyItemDisplay(AuctionItem auction, Tab tab) {
        ItemStack display = auction.getItem().clone();
        ItemMeta meta = display.getItemMeta();
        if (meta == null) return display;

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add("§8§m                              ");
        lore.add("§7Precio: §e§l" + auction.getFormattedPrice());

        if (tab == Tab.ACTIVOS) {
            lore.add("§7Expira: " + auction.getTimeRemaining());
            lore.add("§8§m                              ");
            lore.add("§c▶ Click para cancelar y recoger");
        } else {
            lore.add("§7Estado: §cExpirado");
            lore.add("§8§m                              ");
            lore.add("§a▶ Click para recoger");
        }

        meta.setLore(lore);
        display.setItemMeta(meta);
        return display;
    }

    /**
     * Obtiene el AuctionItem correspondiente al slot.
     */
    public static AuctionItem getItemAtSlot(int slot, int page, List<AuctionItem> items) {
        int slotIndex = -1;
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            if (ITEM_SLOTS[i] == slot) {
                slotIndex = i;
                break;
            }
        }
        if (slotIndex == -1) return null;

        int dataIndex = (page - 1) * ITEMS_PER_PAGE + slotIndex;
        if (dataIndex >= 0 && dataIndex < items.size()) {
            return items.get(dataIndex);
        }
        return null;
    }
}
