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
 * GUI de "Mis Bounties" - muestra bounties puestas por el jugador y bounties sobre él.
 */
public class BountyMyGUI {

    static final String TITLE_PREFIX = SmallCaps.convert("§8§l☠ §b§lMis Bounties");

    public enum Tab {
        PUESTAS("Mis Bounties Puestas"),
        SOBRE_MI("Bounties Sobre Mí");

        private final String displayName;
        Tab(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    private static final int[] ITEM_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    public static String getTitle(Tab tab, int page) {
        return TITLE_PREFIX + " §7[" + (tab == Tab.PUESTAS ? "Puestas" : "Sobre mí") + "] §8(" + page + ")";
    }

    public static boolean isMyBountyGUI(String title) {
        return title != null && title.startsWith(TITLE_PREFIX);
    }

    public static Tab getTabFromTitle(String title) {
        if (title.contains("Sobre mí")) return Tab.SOBRE_MI;
        return Tab.PUESTAS;
    }

    public static int getPageFromTitle(String title) {
        try {
            String num = title.substring(title.lastIndexOf("(") + 1, title.length() - 1);
            return Integer.parseInt(num);
        } catch (Exception e) {
            return 1;
        }
    }

    public static void open(Player player, BountyManager manager, Tab tab, int page) {
        List<Bounty> bounties;
        if (tab == Tab.PUESTAS) {
            bounties = manager.getActiveBountiesPlacedBy(player.getUniqueId());
        } else {
            bounties = manager.getBountiesOnPlayer(player.getUniqueId());
        }

        int totalPages = Math.max(1, (int) Math.ceil(bounties.size() / (double) ITEM_SLOTS.length));
        page = Math.max(1, Math.min(page, totalPages));

        Inventory inv = Bukkit.createInventory(null, 54, getTitle(tab, page));

        // Bordes
        ItemStack border = BountyGUI.createItem(Material.RED_STAINED_GLASS_PANE, "§r");
        ItemStack borderDark = BountyGUI.createItem(Material.BLACK_STAINED_GLASS_PANE, "§r");
        for (int i = 0; i < 9; i++) inv.setItem(i, borderDark);
        for (int i = 45; i < 54; i++) inv.setItem(i, borderDark);
        inv.setItem(9, border); inv.setItem(18, border); inv.setItem(27, border); inv.setItem(36, border);
        inv.setItem(17, border); inv.setItem(26, border); inv.setItem(35, border); inv.setItem(44, border);

        // Tabs
        ItemStack tabPuestas = BountyGUI.createItem(
            tab == Tab.PUESTAS ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
            (tab == Tab.PUESTAS ? "§a§l" : "§7") + "Mis Bounties Puestas"
        );
        ItemMeta tpMeta = tabPuestas.getItemMeta();
        int placedCount = manager.getActiveBountiesPlacedBy(player.getUniqueId()).size();
        tpMeta.setLore(Arrays.asList(
            "§7Bounties que has puesto: §e" + placedCount,
            "",
            tab == Tab.PUESTAS ? "§a▶ Pestaña actual" : "§e▶ Click para ver"
        ));
        if (tab == Tab.PUESTAS) {
            tpMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
            tpMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        tabPuestas.setItemMeta(tpMeta);
        inv.setItem(2, tabPuestas);

        ItemStack tabSobreMi = BountyGUI.createItem(
            tab == Tab.SOBRE_MI ? Material.RED_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
            (tab == Tab.SOBRE_MI ? "§c§l" : "§7") + "Bounties Sobre Mí"
        );
        ItemMeta tsMeta = tabSobreMi.getItemMeta();
        int onMeCount = manager.getBountiesOnPlayer(player.getUniqueId()).size();
        double totalOnMe = manager.getTotalBountyOnPlayer(player.getUniqueId());
        tsMeta.setLore(Arrays.asList(
            "§7Bounties sobre ti: §c" + onMeCount,
            onMeCount > 0 ? "§7Total: §c$" + String.format("%,.0f", totalOnMe) : "§a¡Estás limpio!",
            "",
            tab == Tab.SOBRE_MI ? "§a▶ Pestaña actual" : "§e▶ Click para ver"
        ));
        if (tab == Tab.SOBRE_MI) {
            tsMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
            tsMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        tabSobreMi.setItemMeta(tsMeta);
        inv.setItem(6, tabSobreMi);

        // Items
        int startIndex = (page - 1) * ITEM_SLOTS.length;
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            int dataIndex = startIndex + i;
            if (dataIndex < bounties.size()) {
                Bounty b = bounties.get(dataIndex);
                inv.setItem(ITEM_SLOTS[i], createBountyItem(b, tab));
            }
        }

        // Navegación
        if (page > 1) {
            inv.setItem(45, BountyGUI.createNavItem(Material.ARROW, "§a◀ Página anterior", "§7Ir a página " + (page - 1)));
        }

        // Volver al Bounty Board
        ItemStack backBtn = BountyGUI.createItem(Material.DARK_OAK_DOOR, "§c§lVolver al Bounty Board");
        ItemMeta backMeta = backBtn.getItemMeta();
        backMeta.setLore(Collections.singletonList("§7Volver al panel principal"));
        backBtn.setItemMeta(backMeta);
        inv.setItem(49, backBtn);

        if (page < totalPages) {
            inv.setItem(53, BountyGUI.createNavItem(Material.ARROW, "§aPágina siguiente ▶", "§7Ir a página " + (page + 1)));
        }

        player.openInventory(inv);
    }

    private static ItemStack createBountyItem(Bounty bounty, Tab tab) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();

        if (tab == Tab.PUESTAS) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(bounty.getTarget()));
            meta.setDisplayName(SmallCaps.convert("§c§l☠ " + bounty.getTargetName()));
        } else {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(bounty.getPlacer()));
            meta.setDisplayName(SmallCaps.convert("§6§l⚔ Puesta por: §f" + bounty.getPlacerName()));
        }

        List<String> lore = new ArrayList<>();
        lore.add("§8§m                              ");
        lore.add("§7Recompensa: §a§l" + bounty.getFormattedAmount());
        if (tab == Tab.PUESTAS) {
            lore.add("§7Objetivo: §c" + bounty.getTargetName());
        } else {
            lore.add("§7Puesto por: §f" + bounty.getPlacerName());
        }
        lore.add("§7Expira en: " + bounty.getTimeRemaining());
        lore.add("§8§m                              ");

        if (tab == Tab.PUESTAS) {
            lore.add("§c▶ Click para cancelar §7(50% devuelto)");
        } else {
            lore.add("§7¡Cuidado! Alguien te busca...");
        }

        meta.setLore(lore);
        skull.setItemMeta(meta);
        return skull;
    }

    public static Bounty getBountyAtSlot(int slot, int page, List<Bounty> bounties) {
        int slotIndex = -1;
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            if (ITEM_SLOTS[i] == slot) {
                slotIndex = i;
                break;
            }
        }
        if (slotIndex == -1) return null;

        int dataIndex = (page - 1) * ITEM_SLOTS.length + slotIndex;
        if (dataIndex >= 0 && dataIndex < bounties.size()) {
            return bounties.get(dataIndex);
        }
        return null;
    }
}
