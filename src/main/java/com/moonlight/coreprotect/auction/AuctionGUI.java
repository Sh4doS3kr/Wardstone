package com.moonlight.coreprotect.auction;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * GUI principal de la Auction House: navegar, filtrar, comprar.
 * Layout 54 slots (6 filas):
 * - Fila 0 (0-8): Header decorativo + info
 * - Filas 1-4 (9-44): Items del AH (28 slots de items)
 * - Fila 5 (45-53): Navegación, filtros, acciones
 */
public class AuctionGUI {

    static final String TITLE_PREFIX = SmallCaps.convert("§8§l✦ §6§lAuction House");
    private static final int ITEMS_PER_PAGE = 28;

    // Slots de items: filas 1-4, columnas 1-7 (excluyendo bordes)
    private static final int[] ITEM_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    public static String getTitle(int page) {
        return TITLE_PREFIX + " §7(Pág. " + page + ")";
    }

    public static boolean isAuctionGUI(String title) {
        return title != null && title.startsWith(TITLE_PREFIX);
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
     * Abre la GUI principal del AH.
     */
    public static void open(Player player, AuctionManager manager, int page,
                            AuctionManager.Category category, AuctionManager.SortType sort, String search) {
        List<AuctionItem> items = manager.getFilteredListings(category, sort, search);
        int totalPages = Math.max(1, (int) Math.ceil(items.size() / (double) ITEMS_PER_PAGE));
        page = Math.max(1, Math.min(page, totalPages));

        Inventory inv = Bukkit.createInventory(null, 54, getTitle(page));

        // === Bordes decorativos ===
        ItemStack border = createItem(Material.BLACK_STAINED_GLASS_PANE, "§r");
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);
        inv.setItem(9, border); inv.setItem(18, border); inv.setItem(27, border); inv.setItem(36, border);
        inv.setItem(17, border); inv.setItem(26, border); inv.setItem(35, border); inv.setItem(44, border);

        // === Header ===
        inv.setItem(4, createInfoItem(items.size(), page, totalPages, category, sort, search));

        // === Items del AH ===
        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            int dataIndex = startIndex + i;
            if (dataIndex < items.size()) {
                inv.setItem(ITEM_SLOTS[i], createAuctionDisplay(items.get(dataIndex), player));
            }
        }

        // === Barra inferior: Navegación y acciones ===

        // Página anterior
        if (page > 1) {
            inv.setItem(45, createNavItem(Material.ARROW, "§a◀ Página anterior", "§7Ir a página " + (page - 1)));
        }

        // Categoría
        inv.setItem(47, createCategoryItem(category));

        // Ordenar
        inv.setItem(48, createSortItem(sort));

        // Vender item
        ItemStack sellBtn = createItem(Material.GOLD_INGOT, "§e§l$ Vender Item");
        ItemMeta sellMeta = sellBtn.getItemMeta();
        sellMeta.setLore(Arrays.asList(
            "§7Pon un item a la venta",
            "§7en la Auction House.",
            "",
            "§e▶ Click para vender"
        ));
        sellBtn.setItemMeta(sellMeta);
        inv.setItem(49, sellBtn);

        // Mis subastas
        ItemStack myBtn = createItem(Material.CHEST, "§b§lMis Subastas");
        ItemMeta myMeta = myBtn.getItemMeta();
        int activeCount = manager.getActiveListings(player.getUniqueId()).size();
        int expiredCount = manager.getExpiredListings(player.getUniqueId()).size();
        myMeta.setLore(Arrays.asList(
            "§7Ver tus listings activos",
            "§7y recoger items expirados.",
            "",
            "§aActivos: §f" + activeCount,
            "§cExpirados: §f" + expiredCount,
            "",
            "§e▶ Click para ver"
        ));
        myBtn.setItemMeta(myMeta);
        inv.setItem(51, myBtn);

        // Buscar
        ItemStack searchBtn = createItem(Material.OAK_SIGN, "§f§l🔍 Buscar");
        ItemMeta searchMeta = searchBtn.getItemMeta();
        List<String> searchLore = new ArrayList<>();
        searchLore.add("§7Busca por nombre de item");
        searchLore.add("§7o nombre de vendedor.");
        if (search != null && !search.isEmpty()) {
            searchLore.add("");
            searchLore.add("§eBúsqueda actual: §f" + search);
            searchLore.add("§c▶ Click para limpiar");
        } else {
            searchLore.add("");
            searchLore.add("§e▶ Click para buscar");
        }
        searchMeta.setLore(searchLore);
        searchBtn.setItemMeta(searchMeta);
        inv.setItem(52, searchBtn);

        // Página siguiente
        if (page < totalPages) {
            inv.setItem(53, createNavItem(Material.ARROW, "§aPágina siguiente ▶", "§7Ir a página " + (page + 1)));
        }

        player.openInventory(inv);
    }

    /**
     * Crea el display de un AuctionItem para la GUI.
     */
    private static ItemStack createAuctionDisplay(AuctionItem auction, Player viewer) {
        ItemStack display = auction.getItem().clone();
        ItemMeta meta = display.getItemMeta();
        if (meta == null) return display;

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add("§8§m                              ");
        lore.add("§7Vendedor: §f" + auction.getSellerName());
        lore.add("§7Precio: §e§l" + auction.getFormattedPrice());
        lore.add("§7Expira: " + auction.getTimeRemaining());
        lore.add("§7Cantidad: §f" + auction.getItem().getAmount());
        lore.add("§8§m                              ");

        if (auction.getSeller().equals(viewer.getUniqueId())) {
            lore.add("§c▶ Click para cancelar");
        } else {
            lore.add("§a▶ Click izquierdo para comprar");
        }

        meta.setLore(lore);
        display.setItemMeta(meta);
        return display;
    }

    private static ItemStack createInfoItem(int totalItems, int page, int totalPages,
                                            AuctionManager.Category category, AuctionManager.SortType sort, String search) {
        ItemStack item = createItem(Material.NETHER_STAR, "§6§l✦ Auction House §6✦");
        ItemMeta meta = item.getItemMeta();
        List<String> lore = new ArrayList<>();
        lore.add("§8§m                              ");
        lore.add("§7Items en venta: §e" + totalItems);
        lore.add("§7Página: §f" + page + "§7/§f" + totalPages);
        lore.add("§7Categoría: " + category.getDisplayName());
        lore.add("§7Orden: §f" + sort.getDisplayName());
        if (search != null && !search.isEmpty()) {
            lore.add("§7Búsqueda: §f" + search);
        }
        lore.add("§8§m                              ");
        lore.add("§7Impuesto de venta: §c" + (int)(AuctionItem.TAX_RATE * 100) + "%");
        lore.add("§7Duración: §e48 horas");
        lore.add("§7Max listings: §e" + AuctionItem.MAX_LISTINGS);
        meta.setLore(lore);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createCategoryItem(AuctionManager.Category current) {
        ItemStack item = createItem(Material.COMPASS, "§d§lCategoría: " + current.getDisplayName());
        ItemMeta meta = item.getItemMeta();
        List<String> lore = new ArrayList<>();
        for (AuctionManager.Category cat : AuctionManager.Category.values()) {
            String prefix = cat == current ? "§a§l▸ " : "§7  ";
            lore.add(prefix + cat.getDisplayName());
        }
        lore.add("");
        lore.add("§e▶ Click para cambiar");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createSortItem(AuctionManager.SortType current) {
        ItemStack item = createItem(Material.HOPPER, "§e§lOrdenar: §f" + current.getDisplayName());
        ItemMeta meta = item.getItemMeta();
        List<String> lore = new ArrayList<>();
        for (AuctionManager.SortType sort : AuctionManager.SortType.values()) {
            String prefix = sort == current ? "§a§l▸ " : "§7  ";
            lore.add(prefix + "§f" + sort.getDisplayName());
        }
        lore.add("");
        lore.add("§e▶ Click para cambiar");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ==================== HELPERS ====================

    static ItemStack createItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    static ItemStack createNavItem(Material material, String name, String loreText) {
        ItemStack item = createItem(material, name);
        ItemMeta meta = item.getItemMeta();
        meta.setLore(Collections.singletonList(loreText));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Devuelve el AuctionItem correspondiente al slot clickeado.
     */
    public static AuctionItem getItemAtSlot(int slot, int page, AuctionManager manager,
                                            AuctionManager.Category category, AuctionManager.SortType sort, String search) {
        int slotIndex = -1;
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            if (ITEM_SLOTS[i] == slot) {
                slotIndex = i;
                break;
            }
        }
        if (slotIndex == -1) return null;

        List<AuctionItem> items = manager.getFilteredListings(category, sort, search);
        int dataIndex = (page - 1) * ITEMS_PER_PAGE + slotIndex;
        if (dataIndex >= 0 && dataIndex < items.size()) {
            return items.get(dataIndex);
        }
        return null;
    }
}
