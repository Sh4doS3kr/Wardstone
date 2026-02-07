package com.moonlight.coreprotect.gui;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.core.CoreLevel;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ShopGUI {

    private static final String GUI_TITLE = ChatColor.DARK_GRAY + "Tienda de Nucleos";
    private static final int GUI_SIZE = 54;

    private final CoreProtectPlugin plugin;

    public ShopGUI(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, int page) {
        Inventory inventory = Bukkit.createInventory(null, GUI_SIZE, GUI_TITLE + " - Pagina " + page);

        // Decoracion superior
        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, glass);
        }

        // Slots validos (7x4 grid central)
        int[] validSlots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        // Nucleos
        int itemsPerPage = validSlots.length; // 28
        int startLevel = (page - 1) * itemsPerPage + 1;

        // Indice para recorrer validSlots
        int slotIndex = 0;

        for (int level = startLevel; level <= Math.min(startLevel + itemsPerPage - 1, 20); level++) {
            // Seguridad para no salir del array
            if (slotIndex >= validSlots.length)
                break;

            CoreLevel coreLevel = CoreLevel.fromConfig(plugin.getConfig(), level);
            if (coreLevel != null) {
                ItemStack coreItem = coreLevel.toItemStack();
                inventory.setItem(validSlots[slotIndex], coreItem);
                slotIndex++;
            }
        }

        // Decoracion lateral
        for (int i = 0; i < 6; i++) {
            inventory.setItem(i * 9, glass);
            inventory.setItem(i * 9 + 8, glass);
        }

        // Decoracion inferior
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, glass);
        }

        // Navegacion
        if (page > 1) {
            ItemStack prevPage = createItem(Material.ARROW, ChatColor.GREEN + "Pagina Anterior");
            inventory.setItem(48, prevPage);
        }

        // Informacion
        ItemStack info = createInfoItem();
        inventory.setItem(49, info);

        if (page < 2) {
            ItemStack nextPage = createItem(Material.ARROW, ChatColor.GREEN + "Pagina Siguiente");
            inventory.setItem(50, nextPage);
        }

        player.openInventory(inventory);
    }

    private ItemStack createItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createInfoItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Informacion");
        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "Compra un nucleo y colocalo",
                ChatColor.GRAY + "en el mundo para proteger",
                ChatColor.GRAY + "un area a tu alrededor.",
                "",
                ChatColor.GRAY + "Cuanto mayor sea el nivel,",
                ChatColor.GRAY + "mayor sera el area protegida."));
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isShopGUI(String title) {
        return title.startsWith(GUI_TITLE);
    }

    public static int getPageFromTitle(String title) {
        try {
            String[] parts = title.split("Pagina ");
            if (parts.length > 1) {
                return Integer.parseInt(parts[1].trim());
            }
        } catch (NumberFormatException ignored) {
        }
        return 1;
    }
}
