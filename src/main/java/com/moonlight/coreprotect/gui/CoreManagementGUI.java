package com.moonlight.coreprotect.gui;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.core.CoreLevel;
import com.moonlight.coreprotect.core.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class CoreManagementGUI {

    public static final String GUI_TITLE = ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "✦ Panel de Núcleo ✦";
    private static final int GUI_SIZE = 54;

    private final CoreProtectPlugin plugin;

    public CoreManagementGUI(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, ProtectedRegion region) {
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, GUI_TITLE);

        // === BORDERS ===
        ItemStack blackGlass = createGlass(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack grayGlass = createGlass(Material.GRAY_STAINED_GLASS_PANE);
        ItemStack cyanGlass = createGlass(Material.CYAN_STAINED_GLASS_PANE);

        // Top row - decorative pattern
        for (int i = 0; i < 9; i++) inv.setItem(i, blackGlass);
        inv.setItem(1, cyanGlass);
        inv.setItem(3, cyanGlass);
        inv.setItem(5, cyanGlass);
        inv.setItem(7, cyanGlass);

        // Side borders
        for (int row = 1; row < 5; row++) {
            inv.setItem(row * 9, blackGlass);
            inv.setItem(row * 9 + 8, blackGlass);
        }

        // Middle separator row
        for (int i = 27; i < 36; i++) inv.setItem(i, grayGlass);
        inv.setItem(27, blackGlass);
        inv.setItem(31, cyanGlass);
        inv.setItem(35, blackGlass);

        // Bottom row
        for (int i = 45; i < 54; i++) inv.setItem(i, blackGlass);
        inv.setItem(46, cyanGlass);
        inv.setItem(48, cyanGlass);
        inv.setItem(50, cyanGlass);
        inv.setItem(52, cyanGlass);

        // Fill remaining empty slots in rows 4-5 with gray glass
        for (int i = 36; i < 45; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, grayGlass);
        }

        // === CORE INFO (Center top - slot 4) ===
        CoreLevel coreLevel = CoreLevel.fromConfig(plugin.getConfig(), region.getLevel());
        ItemStack coreDisplay = new ItemStack(coreLevel.getMaterial());
        ItemMeta coreMeta = coreDisplay.getItemMeta();
        coreMeta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + coreLevel.getName());
        coreMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        List<String> coreLore = new ArrayList<>();
        coreLore.add("");
        coreLore.add(ChatColor.DARK_GRAY + "▸ " + ChatColor.GRAY + "Nivel: " + ChatColor.WHITE + region.getLevel() + ChatColor.DARK_GRAY + "/20");
        coreLore.add(ChatColor.DARK_GRAY + "▸ " + ChatColor.GRAY + "Área: " + ChatColor.WHITE + region.getSize() + "x" + region.getSize());
        coreLore.add(ChatColor.DARK_GRAY + "▸ " + ChatColor.GRAY + "Miembros: " + ChatColor.WHITE + region.getMembers().size());
        coreLore.add(ChatColor.DARK_GRAY + "▸ " + ChatColor.GRAY + "Mejoras: " + ChatColor.WHITE + region.getActiveUpgradeCount() + "/8");
        String ownerName = Bukkit.getOfflinePlayer(region.getOwner()).getName();
        coreLore.add(ChatColor.DARK_GRAY + "▸ " + ChatColor.GRAY + "Dueño: " + ChatColor.GOLD + (ownerName != null ? ownerName : "???"));
        coreLore.add("");
        coreMeta.setLore(coreLore);
        coreDisplay.setItemMeta(coreMeta);
        inv.setItem(4, coreDisplay);

        // === UPGRADE BUTTON (slot 20) ===
        if (region.getLevel() < 24) {
            CoreLevel nextLevel = CoreLevel.fromConfig(plugin.getConfig(), region.getLevel() + 1);
            ItemStack upgradeBtn = new ItemStack(Material.BEACON);
            ItemMeta upgMeta = upgradeBtn.getItemMeta();
            upgMeta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "⬆ MEJORAR NÚCLEO");
            List<String> upgLore = new ArrayList<>();
            upgLore.add("");
            upgLore.add(ChatColor.GRAY + "Nivel actual: " + ChatColor.WHITE + region.getLevel());
            upgLore.add(ChatColor.GRAY + "Siguiente nivel: " + ChatColor.AQUA + (region.getLevel() + 1));
            upgLore.add("");
            upgLore.add(ChatColor.GRAY + "Nueva área: " + ChatColor.WHITE + nextLevel.getSize() + "x" + nextLevel.getSize());
            upgLore.add(ChatColor.GRAY + "Nuevo bloque: " + ChatColor.WHITE + formatMaterial(nextLevel.getMaterial()));
            upgLore.add("");
            double upgradeCost = nextLevel.getPrice() - coreLevel.getPrice();
            upgLore.add(ChatColor.GRAY + "Precio: " + ChatColor.GOLD + "$" + CoreUpgradesShopGUI.formatPrice(upgradeCost));
            upgLore.add("");
            upgLore.add(ChatColor.YELLOW + "▶ Click para mejorar");
            upgMeta.setLore(upgLore);
            upgradeBtn.setItemMeta(upgMeta);
            inv.setItem(20, upgradeBtn);
        } else {
            ItemStack maxBtn = new ItemStack(Material.NETHER_STAR);
            ItemMeta maxMeta = maxBtn.getItemMeta();
            maxMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "★ NIVEL MÁXIMO ★");
            maxMeta.setLore(Arrays.asList("", ChatColor.GRAY + "Tu núcleo está al máximo!", ChatColor.GOLD + "¡Eres imparable!"));
            maxBtn.setItemMeta(maxMeta);
            inv.setItem(20, maxBtn);
        }

        // === MEMBERS BUTTON (slot 22) ===
        ItemStack membersBtn = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta membersMeta = membersBtn.getItemMeta();
        membersMeta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "👥 MIEMBROS");
        List<String> membersLore = new ArrayList<>();
        membersLore.add("");
        membersLore.add(ChatColor.GRAY + "Miembros actuales: " + ChatColor.WHITE + region.getMembers().size());
        membersLore.add("");
        if (!region.getMembers().isEmpty()) {
            for (UUID memberId : region.getMembers()) {
                String name = Bukkit.getOfflinePlayer(memberId).getName();
                membersLore.add(ChatColor.DARK_GRAY + "  ▸ " + ChatColor.WHITE + (name != null ? name : "???"));
            }
            membersLore.add("");
        }
        membersLore.add(ChatColor.YELLOW + "▶ Click para gestionar");
        membersMeta.setLore(membersLore);
        membersBtn.setItemMeta(membersMeta);
        inv.setItem(22, membersBtn);

        // === UPGRADES SHOP BUTTON (slot 24) ===
        ItemStack shopBtn = new ItemStack(Material.NETHER_STAR);
        ItemMeta shopMeta = shopBtn.getItemMeta();
        shopMeta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "🛒 MEJORAS DE ZONA");
        List<String> shopLore = new ArrayList<>();
        shopLore.add("");
        shopLore.add(ChatColor.GRAY + "Mejoras activas: " + ChatColor.GREEN + region.getActiveUpgradeCount() + ChatColor.DARK_GRAY + "/8");
        shopLore.add("");
        shopLore.add(ChatColor.GRAY + "Compra mejoras para tu zona:");
        shopLore.add(ChatColor.DARK_GRAY + "  ▸ " + ChatColor.WHITE + "Anti-Explosión");
        shopLore.add(ChatColor.DARK_GRAY + "  ▸ " + ChatColor.WHITE + "Anti-PvP");
        shopLore.add(ChatColor.DARK_GRAY + "  ▸ " + ChatColor.WHITE + "Boost de Daño");
        shopLore.add(ChatColor.DARK_GRAY + "  ▸ " + ChatColor.WHITE + "Boost de Vida");
        shopLore.add(ChatColor.DARK_GRAY + "  ▸ " + ChatColor.WHITE + "y más...");
        shopLore.add("");
        shopLore.add(ChatColor.YELLOW + "▶ Click para ver tienda");
        shopMeta.setLore(shopLore);
        shopBtn.setItemMeta(shopMeta);
        inv.setItem(24, shopBtn);

        // === ACTIVE UPGRADES DISPLAY (Row 4: slots 37-43) ===
        // Slot 37: Anti-Explosion
        inv.setItem(37, createUpgradeStatus(Material.TNT, "Anti-Explosión",
                region.isNoExplosion(), "Sin explosiones en tu zona"));

        // Slot 38: Anti-PvP
        inv.setItem(38, createUpgradeStatus(Material.IRON_SWORD, "Anti-PvP",
                region.isNoPvP(), "Los jugadores no se hacen daño"));

        // Slot 39: Damage Boost
        inv.setItem(39, createUpgradeLevelStatus(Material.DIAMOND_SWORD, "Boost de Daño",
                region.getDamageBoostLevel(), 5, "+5% daño por nivel"));

        // Slot 40: Health Boost
        inv.setItem(40, createUpgradeLevelStatus(Material.GOLDEN_APPLE, "Boost de Vida",
                region.getHealthBoostLevel(), 5, "+2 corazones por nivel"));

        // Slot 41: No Mob Spawn
        inv.setItem(41, createUpgradeStatus(Material.ZOMBIE_HEAD, "Anti-Mobs",
                region.isNoMobSpawn(), "Sin mobs hostiles"));

        // Slot 42: Auto Heal
        inv.setItem(42, createUpgradeStatus(Material.ENCHANTED_GOLDEN_APPLE, "Auto-Curación",
                region.isAutoHeal(), "Regeneración lenta en tu zona"));

        // Slot 43: Speed Boost
        inv.setItem(43, createUpgradeStatus(Material.SUGAR, "Velocidad",
                region.isSpeedBoost(), "Velocidad extra en tu zona"));

        // Row 4 slot 36 (left border)
        inv.setItem(36, blackGlass);
        inv.setItem(44, blackGlass);

        // === VISUALS TOGGLE (slot 47) ===
        boolean visualsActive = plugin.getProtectionManager().isVisualActive(region.getId());
        ItemStack visualsBtn = new ItemStack(visualsActive ? Material.ENDER_EYE : Material.ENDER_PEARL);
        ItemMeta visMeta = visualsBtn.getItemMeta();
        visMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "👁 VISUALIZAR ZONA");
        List<String> visLore = new ArrayList<>();
        visLore.add("");
        visLore.add(ChatColor.GRAY + "Muestra los bordes de tu zona");
        visLore.add(ChatColor.GRAY + "con partículas rojas.");
        visLore.add("");
        visLore.add(ChatColor.GRAY + "Estado: " + (visualsActive ? ChatColor.GREEN + "✔ ACTIVO" : ChatColor.RED + "✖ INACTIVO"));
        visLore.add("");
        visLore.add(ChatColor.YELLOW + "▶ Click para " + (visualsActive ? "desactivar" : "activar"));
        visMeta.setLore(visLore);
        visualsBtn.setItemMeta(visMeta);
        inv.setItem(47, visualsBtn);

        // === NO FALL DAMAGE (slot 49) ===
        inv.setItem(49, createUpgradeStatus(Material.FEATHER, "Sin Caída",
                region.isNoFallDamage(), "Sin daño por caída"));

        // === CLOSE BUTTON (slot 51) ===
        ItemStack closeBtn = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeBtn.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "✖ CERRAR");
        closeBtn.setItemMeta(closeMeta);
        inv.setItem(51, closeBtn);

        player.openInventory(inv);
    }

    private ItemStack createGlass(Material material) {
        ItemStack glass = new ItemStack(material);
        ItemMeta meta = glass.getItemMeta();
        meta.setDisplayName(" ");
        glass.setItemMeta(meta);
        return glass;
    }

    private ItemStack createUpgradeStatus(Material material, String name, boolean active, String desc) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName((active ? ChatColor.GREEN + "✔ " : ChatColor.RED + "✖ ") + ChatColor.WHITE + name);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + desc,
                "",
                active ? ChatColor.GREEN + "ACTIVO" : ChatColor.RED + "NO COMPRADO"));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createUpgradeLevelStatus(Material material, String name, int currentLevel, int maxLevel, String desc) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        boolean active = currentLevel > 0;
        meta.setDisplayName((active ? ChatColor.GREEN + "✔ " : ChatColor.RED + "✖ ") + ChatColor.WHITE + name);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        String bar = buildProgressBar(currentLevel, maxLevel);
        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + desc,
                "",
                ChatColor.GRAY + "Nivel: " + bar + " " + ChatColor.WHITE + currentLevel + "/" + maxLevel,
                "",
                active ? ChatColor.GREEN + "ACTIVO" : ChatColor.RED + "NO COMPRADO"));
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

    private String formatMaterial(Material material) {
        return material.name().replace("_", " ").toLowerCase();
    }

    public static boolean isManagementGUI(String title) {
        return title.contains("Panel de N\u00facleo");
    }
}
