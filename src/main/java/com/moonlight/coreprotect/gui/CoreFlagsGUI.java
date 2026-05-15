package com.moonlight.coreprotect.gui;

import com.moonlight.coreprotect.CoreProtectPlugin;
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
 * GUI de Ajustes Avanzados — banderas de protección toggleables por el dueño.
 */
public class CoreFlagsGUI {

    static final String GUI_TITLE = SmallCaps.convert(ChatColor.DARK_GRAY + "⚙ Ajustes Avanzados");
    private static final int GUI_SIZE = 54;

    private final CoreProtectPlugin plugin;

    public CoreFlagsGUI(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, ProtectedRegion region) {
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, GUI_TITLE);

        // Glass border
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta gm = glass.getItemMeta();
        gm.setDisplayName(" ");
        glass.setItemMeta(gm);
        for (int i = 0; i < 9; i++) inv.setItem(i, glass);
        for (int i = 45; i < 54; i++) inv.setItem(i, glass);
        for (int i = 0; i < 6; i++) { inv.setItem(i * 9, glass); inv.setItem(i * 9 + 8, glass); }

        // === INFO HEADER (slot 4) ===
        ItemStack info = new ItemStack(Material.COMPARATOR);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(SmallCaps.convert(ChatColor.GOLD + "" + ChatColor.BOLD + "⚙ AJUSTES AVANZADOS"));
        List<String> infoLore = new ArrayList<>();
        infoLore.add("");
        infoLore.add(ChatColor.GRAY + "Controla qué se permite dentro");
        infoLore.add(ChatColor.GRAY + "de tu territorio.");
        infoLore.add("");
        infoLore.add(ChatColor.GREEN + "Verde" + ChatColor.GRAY + " = Permitido");
        infoLore.add(ChatColor.RED + "Rojo" + ChatColor.GRAY + " = Bloqueado");
        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);
        inv.setItem(4, info);

        // === FLAGS ===
        // Slot 19: PvP — always toggleable
        inv.setItem(19, createFlag(Material.DIAMOND_SWORD,
                "PvP entre jugadores",
                region.isAllowPvP(), true,
                "Los jugadores pueden atacarse",
                "Los jugadores NO pueden atacarse",
                "Viene DESACTIVADO por defecto"));

        // Slot 20: Explosiones — requires Anti-Explosión upgrade
        inv.setItem(20, createFlag(Material.TNT,
                "Explosiones",
                region.isAllowExplosions(), region.isUnlocked("noExplosion"),
                "TNT y Creepers hacen daño",
                "Sin explosiones en tu zona",
                null));

        // Slot 21: Spawn de Mobs — requires Anti-Mobs upgrade
        inv.setItem(21, createFlag(Material.ZOMBIE_HEAD,
                "Spawn de Mobs Hostiles",
                region.isAllowMobSpawn(), region.isUnlocked("noMobSpawn"),
                "Mobs hostiles aparecen normal",
                "Sin mobs hostiles en tu zona",
                null));

        // Slot 22: Daño por Caída — requires Sin Caída upgrade
        inv.setItem(22, createFlag(Material.FEATHER,
                "Daño por Caída",
                region.isAllowFallDamage(), region.isUnlocked("noFallDamage"),
                "Te haces daño al caer",
                "Sin daño por caída",
                null));

        // Slot 23: Hambre — requires Sin Hambre upgrade
        inv.setItem(23, createFlag(Material.COOKED_BEEF,
                "Hambre",
                region.isAllowHunger(), region.isUnlocked("noHunger"),
                "Se pierde hambre normal",
                "Sin pérdida de hambre",
                null));

        // Slot 24: Propagación de Fuego — requires Anti-Fuego upgrade
        inv.setItem(24, createFlag(Material.FLINT_AND_STEEL,
                "Propagación de Fuego",
                region.isAllowFireSpread(), region.isUnlocked("antiFireSpread"),
                "El fuego se propaga normal",
                "El fuego no se propaga",
                null));

        // Slot 25: Habilidades — always toggleable
        inv.setItem(25, createFlag(Material.BLAZE_POWDER,
                "Uso de Habilidades",
                region.isAllowAbilities(), true,
                "Se pueden usar habilidades",
                "Habilidades bloqueadas en zona",
                "Ender pearls, dash, etc."));

        // === BACK BUTTON (slot 49) ===
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName(SmallCaps.convert(ChatColor.RED + "← Volver"));
        back.setItemMeta(backMeta);
        inv.setItem(49, back);

        player.openInventory(inv);
    }

    private ItemStack createFlag(Material material, String name, boolean enabled, boolean unlocked,
                                  String descEnabled, String descDisabled, String extra) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);

        List<String> lore = new ArrayList<>();

        if (!unlocked) {
            // Locked — upgrade not purchased
            meta.setDisplayName(SmallCaps.convert(ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "🔒 " + name));
            lore.add("");
            lore.add(ChatColor.DARK_GRAY + "Estado: " + ChatColor.RED + "✖ BLOQUEADO");
            lore.add("");
            lore.add(ChatColor.RED + "Compra la mejora correspondiente");
            lore.add(ChatColor.RED + "en la Tienda de Mejoras para");
            lore.add(ChatColor.RED + "poder ajustar esta bandera.");
        } else {
            String status = enabled ? (ChatColor.GREEN + "✔ PERMITIDO") : (ChatColor.RED + "✖ BLOQUEADO");
            meta.setDisplayName(SmallCaps.convert((enabled ? ChatColor.GREEN : ChatColor.RED) + "" + ChatColor.BOLD + name));
            lore.add("");
            lore.add(ChatColor.GRAY + "Estado: " + status);
            lore.add("");
            if (enabled) {
                lore.add(ChatColor.WHITE + "▸ " + descEnabled);
            } else {
                lore.add(ChatColor.WHITE + "▸ " + descDisabled);
            }
            if (extra != null) {
                lore.add(ChatColor.DARK_GRAY + "  " + extra);
            }
            lore.add("");
            lore.add(ChatColor.YELLOW + "▶ Click para " + (enabled ? "bloquear" : "permitir"));
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isFlagsGUI(String title) {
        return title.contains("Ajustes Avanzados") || title.equals(GUI_TITLE);
    }
}
