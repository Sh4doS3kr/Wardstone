package com.moonlight.coreprotect.finishers;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class FinisherGUI {

    public static final String TITLE = ChatColor.DARK_RED + "" + ChatColor.BOLD + "☠ " +
            ChatColor.WHITE + "" + ChatColor.BOLD + "Death Finishers";

    private final CoreProtectPlugin plugin;
    private static final DecimalFormat PRICE_FORMAT = new DecimalFormat("#,###");

    public FinisherGUI(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);
        UUID uuid = player.getUniqueId();
        FinisherManager manager = plugin.getFinisherManager();

        // Border
        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.setDisplayName(" ");
        border.setItemMeta(borderMeta);
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);
        for (int row = 1; row < 5; row++) {
            inv.setItem(row * 9, border);
            inv.setItem(row * 9 + 8, border);
        }

        // Fill middle with gray glass
        ItemStack fill = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillMeta = fill.getItemMeta();
        fillMeta.setDisplayName(" ");
        fill.setItemMeta(fillMeta);
        for (int i = 9; i < 45; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, fill);
        }

        // Title item
        ItemStack titleItem = new ItemStack(Material.WITHER_SKELETON_SKULL);
        ItemMeta titleMeta = titleItem.getItemMeta();
        titleMeta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "☠ DEATH FINISHERS");
        titleMeta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "Efectos épicos al eliminar",
                ChatColor.GRAY + "a otros jugadores en PvP.",
                "",
                ChatColor.DARK_GRAY + "Compra y equipa tu favorito!"
        ));
        titleItem.setItemMeta(titleMeta);
        inv.setItem(4, titleItem);

        // Finisher items: row 1 (slots 10-16), row 2 (slots 19-25), row 3 (slots 28-34)
        int[] slots = {10, 12, 14, 16, 19, 21, 23, 25, 28, 30, 32};
        FinisherType[] types = FinisherType.values();

        for (int i = 0; i < types.length && i < slots.length; i++) {
            inv.setItem(slots[i], createFinisherItem(types[i], uuid, manager));
        }

        // Deselect button
        ItemStack deselect = new ItemStack(Material.BARRIER);
        ItemMeta deselectMeta = deselect.getItemMeta();
        deselectMeta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "✖ Quitar Finisher");
        deselectMeta.setLore(Arrays.asList("", ChatColor.GRAY + "Click para no usar ningún finisher."));
        deselect.setItemMeta(deselectMeta);
        inv.setItem(49, deselect);

        player.openInventory(inv);
    }

    private ItemStack createFinisherItem(FinisherType type, UUID player, FinisherManager manager) {
        boolean owned = manager.ownsFinisher(player, type);
        boolean selected = manager.isSelected(player, type);
        double price = manager.getPrice(type);

        ItemStack item = new ItemStack(type.getIcon());
        ItemMeta meta = item.getItemMeta();
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.addAll(Arrays.asList(type.getDescription()));
        lore.add("");

        if (selected) {
            meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "✔ " + type.getDisplayName());
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "✔ EQUIPADO");
            lore.add(ChatColor.DARK_GRAY + "Click para desequipar");
            meta.setEnchantmentGlintOverride(true);
        } else if (owned) {
            meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "★ " + type.getDisplayName());
            lore.add(ChatColor.AQUA + "" + ChatColor.BOLD + "★ DESBLOQUEADO");
            lore.add(ChatColor.YELLOW + "▶ Click para equipar");
        } else {
            meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "✖ " + type.getDisplayName());
            lore.add(ChatColor.GRAY + "Precio: " + ChatColor.GOLD + "$" + PRICE_FORMAT.format(price));
            lore.add("");
            lore.add(ChatColor.YELLOW + "▶ Click para comprar");
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
