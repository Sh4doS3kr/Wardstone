package com.moonlight.coreprotect.auction;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * GUI para vender items en la Auction House.
 * El jugador coloca un item en el slot central y elige el precio.
 * Layout 54 slots:
 * - Slot 22: Slot para colocar el item a vender
 * - Slots de precios: botones predefinidos + personalizado
 * - Confirmar / Cancelar
 */
public class AuctionSellGUI {

    static final String TITLE = SmallCaps.convert("§8§l✦ §e§lVender Item");

    // Slot donde el jugador coloca el item
    public static final int ITEM_SLOT = 22;

    // Precios predefinidos
    private static final double[] PRESET_PRICES = {100, 500, 1000, 5000, 10000, 50000, 100000, 500000};

    public static boolean isSellGUI(String title) {
        return TITLE.equals(title);
    }

    /**
     * Abre la GUI de venta.
     */
    public static void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);

        // Bordes
        ItemStack border = AuctionGUI.createItem(Material.BLACK_STAINED_GLASS_PANE, "§r");
        ItemStack goldBorder = AuctionGUI.createItem(Material.ORANGE_STAINED_GLASS_PANE, "§r");
        for (int i = 0; i < 54; i++) inv.setItem(i, border);

        // Marco dorado alrededor del slot de item
        int[] goldSlots = {12, 13, 14, 21, 23, 30, 31, 32};
        for (int s : goldSlots) inv.setItem(s, goldBorder);

        // Slot del item (vacío, el jugador lo coloca)
        inv.setItem(ITEM_SLOT, null);

        // Info
        ItemStack info = AuctionGUI.createItem(Material.BOOK, "§6§l¿Cómo vender?");
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setLore(Arrays.asList(
            "§8§m                              ",
            "§e1. §fColoca el item en el slot central",
            "§e2. §fElige un precio predefinido",
            "§e   §fo usa el §aAnvil §fpara precio custom",
            "§e3. §fConfirma la venta",
            "§8§m                              ",
            "§7Impuesto: §c" + (int)(AuctionItem.TAX_RATE * 100) + "% §7al venderse",
            "§7Duración: §e48 horas",
            "§7Si no se vende, puedes recogerlo"
        ));
        info.setItemMeta(infoMeta);
        inv.setItem(4, info);

        // Precios predefinidos (fila inferior)
        int[] priceSlots = {37, 38, 39, 40, 41, 42, 43, 44};
        for (int i = 0; i < PRESET_PRICES.length && i < priceSlots.length; i++) {
            inv.setItem(priceSlots[i], createPriceButton(PRESET_PRICES[i], false));
        }

        // Precio personalizado
        ItemStack customPrice = AuctionGUI.createItem(Material.ANVIL, "§a§lPrecio personalizado");
        ItemMeta customMeta = customPrice.getItemMeta();
        customMeta.setLore(Arrays.asList(
            "§7Escribe el precio exacto",
            "§7que quieras en el chat.",
            "",
            "§e▶ Click para escribir precio"
        ));
        customPrice.setItemMeta(customMeta);
        inv.setItem(36, customPrice);

        // Cancelar
        ItemStack cancel = AuctionGUI.createItem(Material.BARRIER, "§c§lCancelar");
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.setLore(SmallCaps.convertList(Arrays.asList("§7Volver al Auction House")));
        cancel.setItemMeta(cancelMeta);
        inv.setItem(45, cancel);

        player.openInventory(inv);
    }

    /**
     * Crea un botón de precio.
     */
    public static ItemStack createPriceButton(double price, boolean selected) {
        Material mat = selected ? Material.LIME_STAINED_GLASS_PANE : Material.GOLD_NUGGET;
        String formattedPrice;
        if (price >= 1_000_000) {
            formattedPrice = String.format("$%.1fM", price / 1_000_000.0);
        } else if (price >= 1_000) {
            formattedPrice = String.format("$%.1fK", price / 1_000.0);
        } else {
            formattedPrice = String.format("$%,.0f", price);
        }

        ItemStack item = AuctionGUI.createItem(mat, (selected ? "§a§l✔ " : "§e§l") + formattedPrice);
        ItemMeta meta = item.getItemMeta();
        if (selected) {
            meta.setLore(SmallCaps.convertList(Arrays.asList("§a§lPrecio seleccionado", "", "§a▶ Click en Confirmar para listar")));
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        } else {
            meta.setLore(SmallCaps.convertList(Arrays.asList("§7Click para seleccionar", "§7este precio")));
        }
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Actualiza la GUI con un precio seleccionado, mostrando botón de confirmar.
     */
    public static void updateWithPrice(Inventory inv, double selectedPrice) {
        // Mostrar botón de confirmar
        ItemStack confirm = AuctionGUI.createItem(Material.LIME_CONCRETE, "§a§l✔ CONFIRMAR VENTA");
        ItemMeta confirmMeta = confirm.getItemMeta();

        String formattedPrice;
        if (selectedPrice >= 1_000_000) {
            formattedPrice = String.format("$%,.0f", selectedPrice);
        } else if (selectedPrice >= 1_000) {
            formattedPrice = String.format("$%,.0f", selectedPrice);
        } else {
            formattedPrice = String.format("$%,.0f", selectedPrice);
        }

        double tax = selectedPrice * AuctionItem.TAX_RATE;
        double youGet = selectedPrice - tax;

        confirmMeta.setLore(Arrays.asList(
            "§8§m                              ",
            "§7Precio: §e§l" + formattedPrice,
            "§7Impuesto (§c" + (int)(AuctionItem.TAX_RATE * 100) + "%§7): §c-$" + String.format("%,.0f", tax),
            "§7Recibirás: §a$" + String.format("%,.0f", youGet),
            "§8§m                              ",
            "§a§l▶ Click para confirmar"
        ));
        confirmMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
        confirmMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        confirm.setItemMeta(confirmMeta);
        inv.setItem(49, confirm);

        // Actualizar botones de precio para marcar el seleccionado
        double[] presets = {100, 500, 1000, 5000, 10000, 50000, 100000, 500000};
        int[] priceSlots = {37, 38, 39, 40, 41, 42, 43, 44};
        for (int i = 0; i < presets.length; i++) {
            inv.setItem(priceSlots[i], createPriceButton(presets[i], Math.abs(presets[i] - selectedPrice) < 0.01));
        }
    }

    /**
     * Extrae el precio de un botón de precio.
     */
    public static double getPriceFromSlot(int slot) {
        int[] priceSlots = {37, 38, 39, 40, 41, 42, 43, 44};
        for (int i = 0; i < priceSlots.length; i++) {
            if (priceSlots[i] == slot) {
                return PRESET_PRICES[i];
            }
        }
        return -1;
    }
}
