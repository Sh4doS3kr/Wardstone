package com.moonlight.coreprotect.bounty;

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
 * GUI principal de Bounties.
 * Layout 54 slots (6 filas):
 * - Fila 0 (0-8): Header decorativo + info
 * - Filas 1-4 (9-44): Bounties activas (28 slots)
 * - Fila 5 (45-53): Navegación, acciones
 */
public class BountyGUI {

    static final String TITLE_PREFIX = SmallCaps.convert("§8§l☠ §c§lBounty Board");
    private static final int ITEMS_PER_PAGE = 28;

    private static final int[] ITEM_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    public static String getTitle(int page) {
        return TITLE_PREFIX + " §7(Pág. " + page + ")";
    }

    public static boolean isBountyGUI(String title) {
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
     * Abre la GUI principal del Bounty Board.
     */
    public static void open(Player player, BountyManager manager, int page, BountyManager.SortType sort) {
        List<Bounty> bounties = manager.getActiveBounties(sort);
        int totalPages = Math.max(1, (int) Math.ceil(bounties.size() / (double) ITEMS_PER_PAGE));
        page = Math.max(1, Math.min(page, totalPages));

        Inventory inv = Bukkit.createInventory(null, 54, getTitle(page));

        // === Bordes decorativos ===
        ItemStack border = createItem(Material.RED_STAINED_GLASS_PANE, "§r");
        ItemStack borderDark = createItem(Material.BLACK_STAINED_GLASS_PANE, "§r");
        for (int i = 0; i < 9; i++) inv.setItem(i, i % 2 == 0 ? borderDark : border);
        for (int i = 45; i < 54; i++) inv.setItem(i, borderDark);
        inv.setItem(9, border); inv.setItem(18, border); inv.setItem(27, border); inv.setItem(36, border);
        inv.setItem(17, border); inv.setItem(26, border); inv.setItem(35, border); inv.setItem(44, border);

        // === Header ===
        inv.setItem(4, createInfoItem(bounties.size(), page, totalPages, sort, manager, player));

        // === Bounties ===
        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            int dataIndex = startIndex + i;
            if (dataIndex < bounties.size()) {
                inv.setItem(ITEM_SLOTS[i], createBountyDisplay(bounties.get(dataIndex), player));
            }
        }

        // === Barra inferior ===

        // Página anterior
        if (page > 1) {
            inv.setItem(45, createNavItem(Material.ARROW, "§a◀ Página anterior", "§7Ir a página " + (page - 1)));
        }

        // Ordenar
        inv.setItem(47, createSortItem(sort));

        // Poner Bounty
        ItemStack placeBtn = createItem(Material.GOLD_BLOCK, "§6§l$ Poner Bounty");
        ItemMeta placeMeta = placeBtn.getItemMeta();
        placeMeta.setLore(Arrays.asList(
            "§7Pon una recompensa por la",
            "§7cabeza de otro jugador.",
            "",
            "§7Mín: §e$" + String.format("%,.0f", Bounty.MIN_BOUNTY),
            "§7Máx: §e$" + String.format("%,.0f", Bounty.MAX_BOUNTY),
            "§7Duración: §a§lPermanente",
            "",
            "§e▶ Click para poner bounty"
        ));
        placeMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
        placeMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        placeBtn.setItemMeta(placeMeta);
        inv.setItem(49, placeBtn);

        // Mis Bounties
        ItemStack myBtn = createItem(Material.WRITABLE_BOOK, "§b§lMis Bounties");
        ItemMeta myMeta = myBtn.getItemMeta();
        int placed = manager.getActiveBountiesPlacedBy(player.getUniqueId()).size();
        int onMe = manager.getBountiesOnPlayer(player.getUniqueId()).size();
        double totalOnMe = manager.getTotalBountyOnPlayer(player.getUniqueId());
        myMeta.setLore(Arrays.asList(
            "§7Tus bounties activas: §e" + placed + "§7/§e" + Bounty.MAX_ACTIVE_BOUNTIES,
            "§7Bounties sobre ti: §c" + onMe,
            onMe > 0 ? "§7Total sobre ti: §c$" + String.format("%,.0f", totalOnMe) : "§a¡Estás limpio!",
            "",
            "§e▶ Click para ver tus bounties"
        ));
        myBtn.setItemMeta(myMeta);
        inv.setItem(51, myBtn);

        // Top Cazadores
        ItemStack topBtn = createItem(Material.DIAMOND_SWORD, "§d§l⚔ Top Cazadores");
        ItemMeta topMeta = topBtn.getItemMeta();
        List<String> topLore = new ArrayList<>();
        topLore.add("§7Los mejores cazadores de bounties:");
        topLore.add("");
        List<Map.Entry<String, Double>> topHunters = manager.getTopHunters(5);
        if (topHunters.isEmpty()) {
            topLore.add("§7  Aún no hay cazadores...");
        } else {
            String[] medals = {"§6§l① ", "§f§l② ", "§c§l③ ", "§7④ ", "§7⑤ "};
            for (int i = 0; i < topHunters.size(); i++) {
                Map.Entry<String, Double> e = topHunters.get(i);
                topLore.add(medals[i] + "§f" + e.getKey() + " §7- §a$" + String.format("%,.0f", e.getValue()));
            }
        }
        topLore.add("");
        int myClaimed = manager.getTotalClaimedBy(player.getUniqueId());
        double myEarned = manager.getTotalEarnedBy(player.getUniqueId());
        topLore.add("§7Tus cobros: §e" + myClaimed + " §7(§a$" + String.format("%,.0f", myEarned) + "§7)");
        topMeta.setLore(topLore);
        topMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        topBtn.setItemMeta(topMeta);
        inv.setItem(48, topBtn);

        // Top Buscados
        ItemStack wantedBtn = createItem(Material.SKELETON_SKULL, "§c§l☠ Más Buscados");
        ItemMeta wantedMeta = wantedBtn.getItemMeta();
        List<String> wantedLore = new ArrayList<>();
        wantedLore.add("§7Los jugadores con más bounties:");
        wantedLore.add("");
        List<Map.Entry<String, Double>> topWanted = manager.getTopWanted(5);
        if (topWanted.isEmpty()) {
            wantedLore.add("§7  Nadie está buscado...");
        } else {
            String[] skulls = {"§4§l☠ ", "§c§l☠ ", "§6§l☠ ", "§7☠ ", "§7☠ "};
            for (int i = 0; i < topWanted.size(); i++) {
                Map.Entry<String, Double> e = topWanted.get(i);
                wantedLore.add(skulls[i] + "§f" + e.getKey() + " §7- §c$" + String.format("%,.0f", e.getValue()));
            }
        }
        wantedMeta.setLore(wantedLore);
        wantedBtn.setItemMeta(wantedMeta);
        inv.setItem(52, wantedBtn);

        // Página siguiente
        if (page < totalPages) {
            inv.setItem(53, createNavItem(Material.ARROW, "§aPágina siguiente ▶", "§7Ir a página " + (page + 1)));
        }

        player.openInventory(inv);
    }

    /**
     * Crea el display de una Bounty para la GUI.
     */
    private static ItemStack createBountyDisplay(Bounty bounty, Player viewer) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();

        skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(bounty.getTarget()));
        skullMeta.setDisplayName(SmallCaps.convert("§c§l☠ " + bounty.getTargetName()));

        List<String> lore = new ArrayList<>();
        lore.add("§8§m                              ");
        lore.add("§7Recompensa: §a§l" + bounty.getFormattedAmount());
        lore.add("§7Puesto por: §f" + bounty.getPlacerName());
        lore.add("§7Expira en: " + bounty.getTimeRemaining());
        lore.add("§8§m                              ");

        if (bounty.getPlacer().equals(viewer.getUniqueId())) {
            lore.add("§c▶ Click para cancelar §7(50% devuelto)");
        } else {
            lore.add("§7¡Mata a §c" + bounty.getTargetName() + " §7para cobrar!");
        }

        skullMeta.setLore(lore);
        skull.setItemMeta(skullMeta);
        return skull;
    }

    private static ItemStack createInfoItem(int totalBounties, int page, int totalPages,
                                            BountyManager.SortType sort, BountyManager manager, Player player) {
        ItemStack item = createItem(Material.NETHER_STAR, "§c§l☠ Bounty Board §c☠");
        ItemMeta meta = item.getItemMeta();
        List<String> lore = new ArrayList<>();
        lore.add("§8§m                              ");
        lore.add("§7Bounties activas: §e" + totalBounties);
        lore.add("§7Página: §f" + page + "§7/§f" + totalPages);
        lore.add("§7Orden: §f" + sort.getDisplayName());
        lore.add("§8§m                              ");
        lore.add("§7Mata a jugadores con bounty");
        lore.add("§7para cobrar su recompensa.");
        lore.add("");
        lore.add("§7Duración: §a§lPermanente");
        lore.add("§7Cancelar: §c50% penalización");
        meta.setLore(lore);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createSortItem(BountyManager.SortType current) {
        ItemStack item = createItem(Material.HOPPER, "§e§lOrdenar: §f" + current.getDisplayName());
        ItemMeta meta = item.getItemMeta();
        List<String> lore = new ArrayList<>();
        for (BountyManager.SortType sort : BountyManager.SortType.values()) {
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
     * Devuelve la Bounty correspondiente al slot clickeado.
     */
    public static Bounty getBountyAtSlot(int slot, int page, BountyManager manager, BountyManager.SortType sort) {
        int slotIndex = -1;
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            if (ITEM_SLOTS[i] == slot) {
                slotIndex = i;
                break;
            }
        }
        if (slotIndex == -1) return null;

        List<Bounty> bounties = manager.getActiveBounties(sort);
        int dataIndex = (page - 1) * ITEMS_PER_PAGE + slotIndex;
        if (dataIndex >= 0 && dataIndex < bounties.size()) {
            return bounties.get(dataIndex);
        }
        return null;
    }

    private static boolean isGlassPane(Material material) {
        return material.name().contains("STAINED_GLASS_PANE");
    }
}
