package com.moonlight.coreprotect.rpg.gui;

import com.moonlight.coreprotect.rpg.RPGClass;
import com.moonlight.coreprotect.rpg.RPGPlayer;
import com.moonlight.coreprotect.rpg.RPGSubclass;
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

public class ClassSelectionGUI {

    public static final String CLASS_TITLE = ChatColor.DARK_PURPLE + "⚔ Elige tu Clase ⚔";
    public static final String SUBCLASS_TITLE_PREFIX = ChatColor.DARK_PURPLE + "⚔ Subclases de ";

    public static void openClassSelection(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, CLASS_TITLE);

        // Fill border with glass
        ItemStack border = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, border);

        // 5 classes at slots 11-15
        int slot = 11;
        for (RPGClass rc : RPGClass.values()) {
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GRAY + rc.getDescription());
            lore.add("");
            lore.add(ChatColor.YELLOW + "Subclases:");
            for (String subId : rc.getSubclassIds()) {
                RPGSubclass sub = RPGSubclass.fromId(subId);
                if (sub != null) {
                    lore.add(ChatColor.GRAY + "  ▸ " + sub.getColor() + sub.getDisplayName());
                }
            }
            lore.add("");
            lore.add(ChatColor.GREEN + "▶ Click para elegir");

            inv.setItem(slot, createItem(rc.getIcon(), rc.getColor() + "" + ChatColor.BOLD + rc.getDisplayName(), lore));
            slot++;
        }

        player.openInventory(inv);
    }

    public static void openSubclassSelection(Player player, RPGClass rpgClass) {
        String title = SUBCLASS_TITLE_PREFIX + rpgClass.getColoredName();
        Inventory inv = Bukkit.createInventory(null, 27, title);

        ItemStack border = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, border);

        RPGSubclass[] subs = RPGSubclass.getSubclassesFor(rpgClass);
        int[] slots = {11, 13, 15};
        for (int i = 0; i < subs.length && i < 3; i++) {
            RPGSubclass sub = subs[i];
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GRAY + sub.getDescription());
            lore.add("");
            lore.add(ChatColor.YELLOW + "Habilidades:");
            for (String aId : sub.getAbilityIds()) {
                lore.add(ChatColor.GRAY + "  ▸ " + ChatColor.WHITE + formatAbilityName(aId));
            }
            lore.add("");
            lore.add(ChatColor.GREEN + "▶ Click para elegir");

            inv.setItem(slots[i], createItem(sub.getIcon(), sub.getColor() + "" + ChatColor.BOLD + sub.getDisplayName(), lore));
        }

        // Back button
        inv.setItem(22, createItem(Material.ARROW, ChatColor.RED + "« Volver",
                Arrays.asList("", ChatColor.GRAY + "Volver a selección de clase")));

        player.openInventory(inv);
    }

    private static String formatAbilityName(String id) {
        String[] parts = id.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(p.substring(0, 1).toUpperCase()).append(p.substring(1));
        }
        return sb.toString();
    }

    private static ItemStack createItem(Material mat, String name) {
        return createItem(mat, name, null);
    }

    private static ItemStack createItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null) meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
