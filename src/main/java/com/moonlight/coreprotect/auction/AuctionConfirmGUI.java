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
import java.util.ArrayList;
import java.util.List;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * GUI de confirmación de compra de un item en la Auction House.
 */
public class AuctionConfirmGUI {

    static final String TITLE = SmallCaps.convert("§8§l✦ §a§lConfirmar Compra");

    public static boolean isConfirmGUI(String title) {
        return TITLE.equals(title);
    }

    /**
     * Abre la GUI de confirmación.
     */
    public static void open(Player player, AuctionItem auction) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);

        // Bordes
        ItemStack border = AuctionGUI.createItem(Material.BLACK_STAINED_GLASS_PANE, "§r");
        for (int i = 0; i < 27; i++) inv.setItem(i, border);

        // Item en el centro
        ItemStack display = auction.getItem().clone();
        ItemMeta displayMeta = display.getItemMeta();
        if (displayMeta != null) {
            List<String> lore = displayMeta.hasLore() ? new ArrayList<>(displayMeta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add("§8§m                              ");
            lore.add("§7Vendedor: §f" + auction.getSellerName());
            lore.add("§7Precio: §e§l" + auction.getFormattedPrice());
            lore.add("§7Cantidad: §f" + auction.getItem().getAmount());
            lore.add("§8§m                              ");
            displayMeta.setLore(lore);
            display.setItemMeta(displayMeta);
        }
        inv.setItem(13, display);

        // Confirmar
        ItemStack confirm = AuctionGUI.createItem(Material.LIME_CONCRETE, "§a§l✔ COMPRAR");
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.setLore(Arrays.asList(
            "§7Pagarás §e§l" + auction.getFormattedPrice(),
            "",
            "§a§l▶ Click para confirmar"
        ));
        confirmMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
        confirmMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        confirm.setItemMeta(confirmMeta);
        inv.setItem(11, confirm);

        // Cancelar
        ItemStack cancel = AuctionGUI.createItem(Material.RED_CONCRETE, "§c§l✖ CANCELAR");
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.setLore(Arrays.asList(
            "§7Volver al Auction House",
            "",
            "§c▶ Click para cancelar"
        ));
        cancel.setItemMeta(cancelMeta);
        inv.setItem(15, cancel);

        player.openInventory(inv);
    }
}
