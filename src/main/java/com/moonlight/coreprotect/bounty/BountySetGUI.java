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
import java.util.stream.Collectors;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * GUI para seleccionar jugador y precio al poner una bounty.
 * Fase 1: Seleccionar jugador (cabezas de jugadores online)
 * Fase 2: Seleccionar precio
 */
public class BountySetGUI {

    // === FASE 1: SELECCIONAR JUGADOR ===
    static final String SELECT_PLAYER_TITLE = SmallCaps.convert("§8§l☠ §6§lSeleccionar Objetivo");

    private static final int[] PLAYER_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    public static boolean isSelectPlayerGUI(String title) {
        return title != null && title.startsWith(SELECT_PLAYER_TITLE);
    }

    public static String getSelectPlayerTitle(int page) {
        return SELECT_PLAYER_TITLE + " §7(" + page + ")";
    }

    public static int getPageFromSelectTitle(String title) {
        try {
            String num = title.substring(title.lastIndexOf("(") + 1, title.length() - 1);
            return Integer.parseInt(num);
        } catch (Exception e) {
            return 1;
        }
    }

    /**
     * Abre la GUI de selección de jugador.
     */
    public static void openSelectPlayer(Player player, int page) {
        List<Player> onlinePlayers = Bukkit.getOnlinePlayers().stream()
            .filter(p -> !p.getUniqueId().equals(player.getUniqueId()))
            .sorted(Comparator.comparing(Player::getName))
            .collect(Collectors.toList());

        int totalPages = Math.max(1, (int) Math.ceil(onlinePlayers.size() / (double) PLAYER_SLOTS.length));
        page = Math.max(1, Math.min(page, totalPages));

        Inventory inv = Bukkit.createInventory(null, 54, getSelectPlayerTitle(page));

        // Bordes
        ItemStack border = BountyGUI.createItem(Material.RED_STAINED_GLASS_PANE, "§r");
        ItemStack borderDark = BountyGUI.createItem(Material.BLACK_STAINED_GLASS_PANE, "§r");
        for (int i = 0; i < 9; i++) inv.setItem(i, i % 2 == 0 ? borderDark : border);
        for (int i = 45; i < 54; i++) inv.setItem(i, borderDark);
        inv.setItem(9, border); inv.setItem(18, border); inv.setItem(27, border); inv.setItem(36, border);
        inv.setItem(17, border); inv.setItem(26, border); inv.setItem(35, border); inv.setItem(44, border);

        // Header
        ItemStack header = BountyGUI.createItem(Material.GOLDEN_SWORD, "§6§l⚔ Selecciona un Objetivo");
        ItemMeta headerMeta = header.getItemMeta();
        headerMeta.setLore(Arrays.asList(
            "§7Selecciona un jugador para",
            "§7ponerle una bounty.",
            "",
            "§7Jugadores online: §e" + onlinePlayers.size(),
            "§7Página: §f" + page + "§7/§f" + totalPages
        ));
        headerMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        header.setItemMeta(headerMeta);
        inv.setItem(4, header);

        // Jugadores online
        int startIndex = (page - 1) * PLAYER_SLOTS.length;
        for (int i = 0; i < PLAYER_SLOTS.length; i++) {
            int dataIndex = startIndex + i;
            if (dataIndex < onlinePlayers.size()) {
                Player target = onlinePlayers.get(dataIndex);
                inv.setItem(PLAYER_SLOTS[i], createPlayerHead(target));
            }
        }

        // Navegación
        if (page > 1) {
            inv.setItem(45, BountyGUI.createNavItem(Material.ARROW, "§a◀ Página anterior", "§7Ir a página " + (page - 1)));
        }

        // Volver
        ItemStack backBtn = BountyGUI.createItem(Material.BARRIER, "§c§lVolver");
        ItemMeta backMeta = backBtn.getItemMeta();
        backMeta.setLore(Collections.singletonList("§7Volver al Bounty Board"));
        backBtn.setItemMeta(backMeta);
        inv.setItem(49, backBtn);

        if (page < totalPages) {
            inv.setItem(53, BountyGUI.createNavItem(Material.ARROW, "§aPágina siguiente ▶", "§7Ir a página " + (page + 1)));
        }

        player.openInventory(inv);
    }

    private static ItemStack createPlayerHead(Player target) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        meta.setOwningPlayer(target);
        meta.setDisplayName(SmallCaps.convert("§e§l" + target.getName()));
        meta.setLore(Arrays.asList(
            "§8§m                        ",
            "§7Vida: §c" + String.format("%.0f", target.getHealth()) + "§7/§c" + String.format("%.0f", target.getMaxHealth()),
            "§7Distancia: §f" + (target.getWorld().equals(Bukkit.getWorlds().get(0)) ? "Overworld" : target.getWorld().getName()),
            "§8§m                        ",
            "§e▶ Click para poner bounty"
        ));
        skull.setItemMeta(meta);
        return skull;
    }

    /**
     * Devuelve el nombre del jugador en el slot clickeado.
     */
    public static String getPlayerNameAtSlot(int slot, int page) {
        int slotIndex = -1;
        for (int i = 0; i < PLAYER_SLOTS.length; i++) {
            if (PLAYER_SLOTS[i] == slot) {
                slotIndex = i;
                break;
            }
        }
        if (slotIndex == -1) return null;

        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        onlinePlayers.sort(Comparator.comparing(Player::getName));

        int dataIndex = (page - 1) * PLAYER_SLOTS.length + slotIndex;
        if (dataIndex >= 0 && dataIndex < onlinePlayers.size()) {
            return onlinePlayers.get(dataIndex).getName();
        }
        return null;
    }

    // === FASE 2: SELECCIONAR PRECIO ===
    static final String SELECT_PRICE_TITLE = SmallCaps.convert("§8§l☠ §e§lSeleccionar Precio");

    public static boolean isSelectPriceGUI(String title) {
        return title != null && title.startsWith(SELECT_PRICE_TITLE);
    }

    /**
     * Abre la GUI de selección de precio.
     */
    public static void openSelectPrice(Player player, String targetName) {
        Inventory inv = Bukkit.createInventory(null, 54, SELECT_PRICE_TITLE);

        // Bordes
        ItemStack border = BountyGUI.createItem(Material.ORANGE_STAINED_GLASS_PANE, "§r");
        ItemStack borderDark = BountyGUI.createItem(Material.BLACK_STAINED_GLASS_PANE, "§r");
        for (int i = 0; i < 9; i++) inv.setItem(i, i % 2 == 0 ? borderDark : border);
        for (int i = 45; i < 54; i++) inv.setItem(i, borderDark);
        inv.setItem(9, border); inv.setItem(18, border); inv.setItem(27, border); inv.setItem(36, border);
        inv.setItem(17, border); inv.setItem(26, border); inv.setItem(35, border); inv.setItem(44, border);

        // Header - Cabeza del target
        ItemStack targetHead = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) targetHead.getItemMeta();
        skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(targetName));
        skullMeta.setDisplayName(SmallCaps.convert("§c§l☠ Bounty sobre: §f" + targetName));
        skullMeta.setLore(Arrays.asList(
            "§8§m                              ",
            "§7Selecciona el precio de la bounty",
            "§7o escribe un precio personalizado.",
            "§8§m                              "
        ));
        targetHead.setItemMeta(skullMeta);
        inv.setItem(4, targetHead);

        // Precios predefinidos
        inv.setItem(10, createPriceItem(Material.IRON_NUGGET, 500, "§7"));
        inv.setItem(11, createPriceItem(Material.IRON_INGOT, 1000, "§f"));
        inv.setItem(12, createPriceItem(Material.GOLD_NUGGET, 2500, "§e"));
        inv.setItem(13, createPriceItem(Material.GOLD_INGOT, 5000, "§6"));
        inv.setItem(14, createPriceItem(Material.DIAMOND, 10000, "§b"));
        inv.setItem(15, createPriceItem(Material.EMERALD, 25000, "§a"));
        inv.setItem(16, createPriceItem(Material.NETHERITE_INGOT, 50000, "§4"));

        inv.setItem(19, createPriceItem(Material.IRON_BLOCK, 75000, "§7"));
        inv.setItem(20, createPriceItem(Material.GOLD_BLOCK, 100000, "§6"));
        inv.setItem(21, createPriceItem(Material.DIAMOND_BLOCK, 250000, "§b"));
        inv.setItem(22, createPriceItem(Material.EMERALD_BLOCK, 500000, "§a"));
        inv.setItem(23, createPriceItem(Material.NETHERITE_BLOCK, 1000000, "§4"));
        inv.setItem(24, createPriceItem(Material.BEACON, 2500000, "§d"));
        inv.setItem(25, createPriceItem(Material.NETHER_STAR, 5000000, "§e§l"));

        // Precio personalizado
        ItemStack customPrice = BountyGUI.createItem(Material.ANVIL, "§d§l✎ Precio Personalizado");
        ItemMeta customMeta = customPrice.getItemMeta();
        customMeta.setLore(Arrays.asList(
            "§7Escribe un precio exacto",
            "§7en el chat.",
            "",
            "§7Mín: §e$" + String.format("%,.0f", Bounty.MIN_BOUNTY),
            "§7Máx: §e$" + String.format("%,.0f", Bounty.MAX_BOUNTY),
            "§7Duración: §a§lPermanente",
            "",
            "§e▶ Click para escribir precio"
        ));
        customPrice.setItemMeta(customMeta);
        inv.setItem(31, customPrice);

        // Precio seleccionado (placeholder - se actualiza)
        ItemStack selected = BountyGUI.createItem(Material.GRAY_CONCRETE, "§7§lSelecciona un precio");
        ItemMeta selMeta = selected.getItemMeta();
        selMeta.setLore(Arrays.asList(
            "§7Elige un precio de arriba",
            "§7o escribe uno personalizado"
        ));
        selected.setItemMeta(selMeta);
        inv.setItem(40, selected);

        // Volver
        ItemStack backBtn = BountyGUI.createItem(Material.BARRIER, "§c§lVolver");
        ItemMeta backMeta = backBtn.getItemMeta();
        backMeta.setLore(Collections.singletonList("§7Volver a selección de jugador"));
        backBtn.setItemMeta(backMeta);
        inv.setItem(45, backBtn);

        // Confirmar (desactivado hasta seleccionar precio)
        ItemStack confirmBtn = BountyGUI.createItem(Material.GRAY_CONCRETE, "§7§lSelecciona un precio primero");
        inv.setItem(49, confirmBtn);

        player.openInventory(inv);
    }

    /**
     * Actualiza la GUI con el precio seleccionado.
     */
    public static void updateWithPrice(Inventory inv, double price, String targetName) {
        // Actualizar indicador de precio
        ItemStack selected = BountyGUI.createItem(Material.SUNFLOWER, "§a§lPrecio: §e$" + String.format("%,.0f", price));
        ItemMeta selMeta = selected.getItemMeta();
        selMeta.setLore(Arrays.asList(
            "§7Bounty sobre §c" + targetName,
            "§7Recompensa: §a$" + String.format("%,.0f", price)
        ));
        selMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
        selMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        selected.setItemMeta(selMeta);
        inv.setItem(40, selected);

        // Activar botón de confirmar
        ItemStack confirmBtn = BountyGUI.createItem(Material.LIME_CONCRETE, "§a§l✔ CONFIRMAR BOUNTY");
        ItemMeta confirmMeta = confirmBtn.getItemMeta();
        confirmMeta.setLore(Arrays.asList(
            "§7Se te cobrará §e$" + String.format("%,.0f", price),
            "§7Objetivo: §c" + targetName,
            "",
            "§a▶ Click para confirmar"
        ));
        confirmMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
        confirmMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        confirmBtn.setItemMeta(confirmMeta);
        inv.setItem(49, confirmBtn);
    }

    private static ItemStack createPriceItem(Material material, double price, String color) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(SmallCaps.convert(color + "$" + String.format("%,.0f", price)));
        meta.setLore(Arrays.asList(
            "§7Click para seleccionar",
            "§7este precio."
        ));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Devuelve el precio del slot clickeado en la GUI de precio, o -1 si no es un slot de precio.
     */
    public static double getPriceFromSlot(int slot) {
        switch (slot) {
            case 10: return 500;
            case 11: return 1000;
            case 12: return 2500;
            case 13: return 5000;
            case 14: return 10000;
            case 15: return 25000;
            case 16: return 50000;
            case 19: return 75000;
            case 20: return 100000;
            case 21: return 250000;
            case 22: return 500000;
            case 23: return 1000000;
            case 24: return 2500000;
            case 25: return 5000000;
            default: return -1;
        }
    }
}
