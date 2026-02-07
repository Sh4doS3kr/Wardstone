package com.moonlight.coreprotect.gui;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.core.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CoreUpgradesShopGUI {

    public static final String GUI_TITLE = ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "✦ Mejoras de Zona ✦";
    private static final int GUI_SIZE = 54;

    // Precios base (configurables via config)
    public static final double PRICE_NO_EXPLOSION = 50000;
    public static final double PRICE_NO_PVP = 75000;
    public static final double PRICE_DAMAGE_BOOST = 100000; // per level
    public static final double PRICE_HEALTH_BOOST = 100000; // per level
    public static final double PRICE_NO_MOB_SPAWN = 60000;
    public static final double PRICE_AUTO_HEAL = 80000;
    public static final double PRICE_SPEED_BOOST = 40000;
    public static final double PRICE_NO_FALL_DAMAGE = 30000;

    private final CoreProtectPlugin plugin;

    public CoreUpgradesShopGUI(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, ProtectedRegion region) {
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, GUI_TITLE);

        // === BORDERS ===
        ItemStack blackGlass = createGlass(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack redGlass = createGlass(Material.RED_STAINED_GLASS_PANE);
        ItemStack limeGlass = createGlass(Material.LIME_STAINED_GLASS_PANE);
        ItemStack grayGlass = createGlass(Material.GRAY_STAINED_GLASS_PANE);

        // Top row
        for (int i = 0; i < 9; i++) inv.setItem(i, blackGlass);
        inv.setItem(2, redGlass);
        inv.setItem(4, limeGlass);
        inv.setItem(6, redGlass);

        // Side borders
        for (int row = 1; row < 5; row++) {
            inv.setItem(row * 9, blackGlass);
            inv.setItem(row * 9 + 8, blackGlass);
        }

        // Bottom row
        for (int i = 45; i < 54; i++) inv.setItem(i, blackGlass);

        // Fill empty with gray glass
        for (int i = 9; i < 45; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, grayGlass);
        }

        // === TITLE ITEM (slot 4 stays as lime glass - already set) ===

        // === ROW 1: Toggle upgrades (slots 10-16 = positions in grid) ===
        // Slot 10: Anti-Explosion
        inv.setItem(10, createUpgradeItem(Material.TNT, "Anti-Explosión",
                region.isNoExplosion(), PRICE_NO_EXPLOSION,
                Arrays.asList(
                        ChatColor.GRAY + "Evita TODAS las explosiones",
                        ChatColor.GRAY + "dentro de tu zona protegida.",
                        "",
                        ChatColor.GRAY + "TNT, Creepers, Bolas de fuego...",
                        ChatColor.GRAY + "Nada puede destruir tu base!")));

        // Slot 12: Anti-PvP
        inv.setItem(12, createUpgradeItem(Material.IRON_SWORD, "Anti-PvP",
                region.isNoPvP(), PRICE_NO_PVP,
                Arrays.asList(
                        ChatColor.GRAY + "Los jugadores NO pueden",
                        ChatColor.GRAY + "hacerse daño entre sí",
                        ChatColor.GRAY + "dentro de tu zona.",
                        "",
                        ChatColor.GRAY + "Zona de paz total!")));

        // Slot 14: No Mob Spawn
        inv.setItem(14, createUpgradeItem(Material.ZOMBIE_HEAD, "Anti-Mobs",
                region.isNoMobSpawn(), PRICE_NO_MOB_SPAWN,
                Arrays.asList(
                        ChatColor.GRAY + "Evita el spawn de mobs",
                        ChatColor.GRAY + "hostiles en tu zona.",
                        "",
                        ChatColor.GRAY + "Zombies, Esqueletos, Creepers...",
                        ChatColor.GRAY + "¡No más sustos!")));

        // Slot 16: No Fall Damage
        inv.setItem(16, createUpgradeItem(Material.FEATHER, "Sin Caída",
                region.isNoFallDamage(), PRICE_NO_FALL_DAMAGE,
                Arrays.asList(
                        ChatColor.GRAY + "Elimina todo el daño",
                        ChatColor.GRAY + "por caída en tu zona.",
                        "",
                        ChatColor.GRAY + "Salta desde cualquier",
                        ChatColor.GRAY + "altura sin preocuparte!")));

        // === ROW 2: More toggle upgrades (slots 19-25) ===
        // Slot 19: Auto Heal
        inv.setItem(19, createUpgradeItem(Material.ENCHANTED_GOLDEN_APPLE, "Auto-Curación",
                region.isAutoHeal(), PRICE_AUTO_HEAL,
                Arrays.asList(
                        ChatColor.GRAY + "Regeneración pasiva lenta",
                        ChatColor.GRAY + "mientras estés en tu zona.",
                        "",
                        ChatColor.GRAY + "Recupera vida poco a poco",
                        ChatColor.GRAY + "sin necesidad de comida!")));

        // Slot 21: Speed Boost
        inv.setItem(21, createUpgradeItem(Material.SUGAR, "Velocidad",
                region.isSpeedBoost(), PRICE_SPEED_BOOST,
                Arrays.asList(
                        ChatColor.GRAY + "Velocidad de movimiento",
                        ChatColor.GRAY + "aumentada en tu zona.",
                        "",
                        ChatColor.GRAY + "Muévete más rápido",
                        ChatColor.GRAY + "dentro de tu territorio!")));

        // === ROW 2: Level upgrades (slots 23, 25) ===
        // Slot 23: Damage Boost
        inv.setItem(23, createLevelUpgradeItem(Material.DIAMOND_SWORD, "Boost de Daño",
                region.getDamageBoostLevel(), 5, PRICE_DAMAGE_BOOST,
                Arrays.asList(
                        ChatColor.GRAY + "+5% de daño por nivel",
                        ChatColor.GRAY + "al atacar dentro de tu zona.",
                        "",
                        ChatColor.GRAY + "Máximo: +25% daño extra")));

        // Slot 25: Health Boost
        inv.setItem(25, createLevelUpgradeItem(Material.GOLDEN_APPLE, "Boost de Vida",
                region.getHealthBoostLevel(), 5, PRICE_HEALTH_BOOST,
                Arrays.asList(
                        ChatColor.GRAY + "+2 corazones por nivel",
                        ChatColor.GRAY + "mientras estés en tu zona.",
                        "",
                        ChatColor.GRAY + "Máximo: +10 corazones extra")));

        // === INFO ITEM (slot 31) ===
        ItemStack infoItem = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Información");
        infoMeta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "Compra mejoras para potenciar",
                ChatColor.GRAY + "tu zona protegida.",
                "",
                ChatColor.GREEN + "Verde" + ChatColor.GRAY + " = Ya comprado",
                ChatColor.RED + "Rojo" + ChatColor.GRAY + " = Disponible",
                "",
                ChatColor.GRAY + "Las mejoras por nivel se",
                ChatColor.GRAY + "pueden subir hasta nivel 5."));
        infoItem.setItemMeta(infoMeta);
        inv.setItem(31, infoItem);

        // === BACK BUTTON (slot 49) ===
        ItemStack backBtn = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backBtn.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + "◀ VOLVER");
        backMeta.setLore(Arrays.asList("", ChatColor.GRAY + "Volver al panel principal"));
        backBtn.setItemMeta(backMeta);
        inv.setItem(49, backBtn);

        player.openInventory(inv);
    }

    private ItemStack createUpgradeItem(Material material, String name, boolean owned, double price, List<String> description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        if (owned) {
            meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "✔ " + name);
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.addAll(description);
            lore.add("");
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "✔ COMPRADO");
            meta.setLore(lore);
        } else {
            meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "✖ " + name);
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.addAll(description);
            lore.add("");
            lore.add(ChatColor.GRAY + "Precio: " + ChatColor.GOLD + "$" + formatPrice(price));
            lore.add("");
            lore.add(ChatColor.YELLOW + "▶ Click para comprar");
            meta.setLore(lore);
        }

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createLevelUpgradeItem(Material material, String name, int currentLevel, int maxLevel, double pricePerLevel, List<String> description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        boolean maxed = currentLevel >= maxLevel;
        String bar = buildProgressBar(currentLevel, maxLevel);

        if (maxed) {
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "★ " + name + " MÁXIMO");
        } else if (currentLevel > 0) {
            meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "✔ " + name + " Nv." + currentLevel);
        } else {
            meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "✖ " + name);
        }

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.addAll(description);
        lore.add("");
        lore.add(ChatColor.GRAY + "Nivel: " + bar + " " + ChatColor.WHITE + currentLevel + "/" + maxLevel);

        if (!maxed) {
            double nextPrice = pricePerLevel * (currentLevel + 1);
            lore.add("");
            lore.add(ChatColor.GRAY + "Siguiente nivel: " + ChatColor.GOLD + "$" + formatPrice(nextPrice));
            lore.add("");
            lore.add(ChatColor.YELLOW + "▶ Click para mejorar");
        } else {
            lore.add("");
            lore.add(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "★ NIVEL MÁXIMO ★");
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String buildProgressBar(int current, int max) {
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < max; i++) {
            if (i < current) {
                bar.append(ChatColor.GREEN + "■");
            } else {
                bar.append(ChatColor.DARK_GRAY + "■");
            }
        }
        return bar.toString();
    }

    private ItemStack createGlass(Material material) {
        ItemStack glass = new ItemStack(material);
        ItemMeta meta = glass.getItemMeta();
        meta.setDisplayName(" ");
        glass.setItemMeta(meta);
        return glass;
    }

    public static String formatPrice(double price) {
        if (price >= 1000000) {
            return String.format("%.1fM", price / 1000000);
        } else if (price >= 1000) {
            return String.format("%.1fK", price / 1000);
        }
        return String.valueOf((int) price);
    }

    public static boolean isUpgradesShopGUI(String title) {
        return title.contains("Mejoras de Zona");
    }
}
