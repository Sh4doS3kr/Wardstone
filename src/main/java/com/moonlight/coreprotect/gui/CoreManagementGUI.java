package com.moonlight.coreprotect.gui;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.core.CoreLevel;
import com.moonlight.coreprotect.core.CoreMaintenanceManager;
import com.moonlight.coreprotect.core.CorePrestigeManager;
import com.moonlight.coreprotect.core.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
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
import com.moonlight.coreprotect.util.SmallCaps;

public class CoreManagementGUI {

    static final String GUI_TITLE = SmallCaps.convert(ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "✦ Panel de Núcleo ✦");
    private static final int GUI_SIZE = 54;

    private final CoreProtectPlugin plugin;

    public CoreManagementGUI(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, ProtectedRegion region) {
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, GUI_TITLE);

        CoreLevel coreLevel = CoreLevel.fromConfig(plugin.getConfig(), region.getLevel());
        String ownerName = Bukkit.getOfflinePlayer(region.getOwner()).getName();
        if (ownerName == null) ownerName = "???";

        // ═══════════════════════════════════════
        // GLASS BACKGROUND
        // ═══════════════════════════════════════
        ItemStack dark = glass(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack mid = glass(Material.GRAY_STAINED_GLASS_PANE);
        ItemStack accent = glass(Material.CYAN_STAINED_GLASS_PANE);
        ItemStack purple = glass(Material.PURPLE_STAINED_GLASS_PANE);

        // Fill everything with dark glass first
        for (int i = 0; i < 54; i++) inv.setItem(i, dark);

        // Row 0: top accent border
        inv.setItem(0, accent); inv.setItem(1, purple); inv.setItem(2, accent);
        inv.setItem(3, dark);   inv.setItem(4, dark);   inv.setItem(5, dark);
        inv.setItem(6, accent); inv.setItem(7, purple); inv.setItem(8, accent);

        // Row 2: separator with pattern
        for (int i = 18; i < 27; i++) inv.setItem(i, mid);
        inv.setItem(18, dark); inv.setItem(22, accent); inv.setItem(26, dark);

        // Row 5: bottom accent border
        inv.setItem(45, accent); inv.setItem(46, purple); inv.setItem(47, accent);
        inv.setItem(48, dark); inv.setItem(49, dark); inv.setItem(50, dark);
        inv.setItem(51, accent); inv.setItem(52, purple); inv.setItem(53, accent);

        // ═══════════════════════════════════════
        // ROW 0-1: CORE HEADER
        // ═══════════════════════════════════════

        // Slot 4: Core display (main info block)
        ItemStack coreDisplay = new ItemStack(coreLevel.getMaterial());
        ItemMeta coreMeta = coreDisplay.getItemMeta();
        coreMeta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "✦ " + ChatColor.translateAlternateColorCodes('&', coreLevel.getName()) + ChatColor.AQUA + "" + ChatColor.BOLD + " ✦");
        coreMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        List<String> coreLore = new ArrayList<>();
        coreLore.add(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━━━━━");
        coreLore.add("");
        coreLore.add(ChatColor.DARK_GRAY + " ◆ " + ChatColor.GRAY + "Nivel: " + ChatColor.WHITE + region.getLevel() + ChatColor.DARK_GRAY + "/30 " + buildLevelBar(region.getLevel(), 30));
        coreLore.add(ChatColor.DARK_GRAY + " ◆ " + ChatColor.GRAY + "Área: " + ChatColor.AQUA + region.getSize() + "x" + region.getSize() + ChatColor.DARK_GRAY + " bloques");
        if (region.getPrestige() > 0) {
            coreLore.add(ChatColor.DARK_GRAY + " ◆ " + ChatColor.GRAY + "Prestigio: " + CorePrestigeManager.getPrestigeName(region.getPrestige()));
            coreLore.add(ChatColor.DARK_GRAY + " ◆ " + ChatColor.GRAY + "Área efectiva: " + ChatColor.GREEN + region.getEffectiveSize() + "x" + region.getEffectiveSize());
        }
        coreLore.add(ChatColor.DARK_GRAY + " ◆ " + ChatColor.GRAY + "Dueño: " + ChatColor.GOLD + ownerName);
        coreLore.add(ChatColor.DARK_GRAY + " ◆ " + ChatColor.GRAY + "Miembros: " + ChatColor.WHITE + region.getMembers().size());
        coreLore.add(ChatColor.DARK_GRAY + " ◆ " + ChatColor.GRAY + "Mejoras: " + ChatColor.GREEN + region.getActiveUpgradeCount() + ChatColor.DARK_GRAY + "/" + ChatColor.WHITE + region.getTotalUpgradeCount());
        if (plugin.getCoreMaintenanceManager() != null) {
            int totalCores = plugin.getCoreMaintenanceManager().getCoreCount(player.getUniqueId());
            int maxCores = plugin.getCoreMaintenanceManager().getMaxCores(player);
            double weeklyTax = totalCores * CoreMaintenanceManager.WEEKLY_TAX_PER_CORE;
            coreLore.add("");
            coreLore.add(ChatColor.DARK_GRAY + " ◆ " + ChatColor.GRAY + "Tus cores: " + ChatColor.YELLOW + totalCores + ChatColor.DARK_GRAY + "/" + ChatColor.WHITE + maxCores);
            coreLore.add(ChatColor.DARK_GRAY + " ◆ " + ChatColor.GRAY + "Impuesto: " + ChatColor.GOLD + "$" + CoreMaintenanceManager.formatMoney(weeklyTax) + ChatColor.DARK_GRAY + "/semana");
        }
        coreLore.add("");
        coreLore.add(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━━━━━");
        coreMeta.setLore(coreLore);
        coreDisplay.setItemMeta(coreMeta);
        inv.setItem(4, coreDisplay);

        // Slot 3 & 5: Decorative side items
        // Left: Owner skull
        ItemStack ownerHead = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) ownerHead.getItemMeta();
        skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(region.getOwner()));
        skullMeta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "♛ " + ChatColor.WHITE + ownerName);
        skullMeta.setLore(Arrays.asList("", ChatColor.GRAY + "Propietario del núcleo"));
        ownerHead.setItemMeta(skullMeta);
        inv.setItem(3, ownerHead);

        // Right: World info
        ItemStack worldInfo = new ItemStack(Material.COMPASS);
        ItemMeta worldMeta = worldInfo.getItemMeta();
        worldMeta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "⌖ " + ChatColor.WHITE + "Ubicación");
        worldMeta.setLore(Arrays.asList(
                "",
                ChatColor.DARK_GRAY + " ◆ " + ChatColor.GRAY + "Mundo: " + ChatColor.WHITE + region.getWorldName(),
                ChatColor.DARK_GRAY + " ◆ " + ChatColor.GRAY + "X: " + ChatColor.AQUA + region.getCoreX() + ChatColor.DARK_GRAY + " | " + ChatColor.GRAY + "Y: " + ChatColor.AQUA + region.getCoreY() + ChatColor.DARK_GRAY + " | " + ChatColor.GRAY + "Z: " + ChatColor.AQUA + region.getCoreZ(),
                ""
        ));
        worldInfo.setItemMeta(worldMeta);
        inv.setItem(5, worldInfo);

        // ═══════════════════════════════════════
        // ROW 1: MAIN ACTIONS (slots 10-16)
        // ═══════════════════════════════════════
        inv.setItem(9, dark); inv.setItem(17, dark);

        // Slot 10: UPGRADE / PRESTIGE / MAX — keep on slot 20 equivalent → moved to 11
        boolean canPrestige = region.getLevel() >= CorePrestigeManager.PRESTIGE_REQUIRED_LEVEL
                && region.getPrestige() < CorePrestigeManager.MAX_PRESTIGE;
        boolean isMaxLevel = region.getLevel() >= 30;

        // Determine if next level is accessible (no VIP/prestige block)
        boolean canUpgradeNext = false;
        boolean nextNeedsVip = false;
        boolean nextNeedsPrestige = false;
        CoreLevel nextLevel = null;
        if (!isMaxLevel) {
            nextLevel = CoreLevel.fromConfig(plugin.getConfig(), region.getLevel() + 1);
            if (nextLevel != null) {
                nextNeedsVip = nextLevel.isVip() && !player.hasPermission(nextLevel.getVipPermission());
                nextNeedsPrestige = nextLevel.requiresPrestige() && region.getPrestige() < nextLevel.getPrestigeRequired();
                canUpgradeNext = !nextNeedsVip && !nextNeedsPrestige;
            }
        }

        if (!isMaxLevel && canUpgradeNext) {
            // Can upgrade to next level — show upgrade button
            double upgradeCost = nextLevel.getPrice() - coreLevel.getPrice();
            ItemStack upgradeBtn = new ItemStack(Material.EXPERIENCE_BOTTLE);
            ItemMeta upgMeta = upgradeBtn.getItemMeta();
            upgMeta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "⬆ " + SmallCaps.convert("MEJORAR NÚCLEO"));
            List<String> upgLore = new ArrayList<>();
            upgLore.add(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━");
            upgLore.add("");
            upgLore.add(ChatColor.DARK_GRAY + " ◆ " + ChatColor.GRAY + "Actual: " + ChatColor.WHITE + "Nv. " + region.getLevel() + ChatColor.DARK_GRAY + " → " + ChatColor.AQUA + "Nv. " + (region.getLevel() + 1));
            upgLore.add(ChatColor.DARK_GRAY + " ◆ " + ChatColor.GRAY + "Área: " + ChatColor.WHITE + region.getSize() + ChatColor.DARK_GRAY + " → " + ChatColor.GREEN + nextLevel.getSize());
            upgLore.add(ChatColor.DARK_GRAY + " ◆ " + ChatColor.GRAY + "Bloque: " + ChatColor.WHITE + formatMaterial(nextLevel.getMaterial()));
            if (nextLevel.requiresGlobalPrestige()) {
                upgLore.add(ChatColor.LIGHT_PURPLE + " ✦ " + ChatColor.GRAY + "Requiere " + nextLevel.getGlobalPrestigeGlyph());
            }
            upgLore.add("");
            upgLore.add(ChatColor.DARK_GRAY + " ◆ " + ChatColor.GRAY + "Precio: " + ChatColor.GOLD + "$" + CoreUpgradesShopGUI.formatPrice(upgradeCost));
            upgLore.add("");
            upgLore.add(ChatColor.YELLOW + " ▶ Click izq. para mejorar");
            upgLore.add(ChatColor.AQUA + " ▶ Click der. ver todos los niveles");
            if (canPrestige) {
                upgLore.add(ChatColor.LIGHT_PURPLE + " ▶ Shift+Click para prestigiar");
            }
            upgLore.add(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━");
            upgMeta.setLore(upgLore);
            upgMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            upgradeBtn.setItemMeta(upgMeta);
            inv.setItem(11, upgradeBtn);
        } else if (canPrestige) {
            // Can't upgrade (VIP/prestige block or max normal levels) — show prestige button
            int nextPrestige = region.getPrestige() + 1;
            double prestigeCost = plugin.getCorePrestigeManager().getPrestigeCost(region.getPrestige());
            ItemStack prestigeBtn = new ItemStack(Material.DRAGON_EGG);
            ItemMeta pMeta = prestigeBtn.getItemMeta();
            pMeta.setDisplayName(CorePrestigeManager.getPrestigeColor(nextPrestige) + "" + ChatColor.BOLD + "✦ " + SmallCaps.convert("PRESTIGIAR NÚCLEO") + " ✦");
            List<String> pLore = new ArrayList<>();
            pLore.add(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━");
            pLore.add("");
            pLore.add(ChatColor.DARK_GRAY + " ◆ " + ChatColor.GRAY + "Actual: " + CorePrestigeManager.getPrestigeName(region.getPrestige()));
            pLore.add(ChatColor.DARK_GRAY + " ◆ " + ChatColor.GRAY + "Siguiente: " + CorePrestigeManager.getPrestigeName(nextPrestige));
            pLore.add("");
            pLore.add(ChatColor.DARK_GRAY + " ◆ " + ChatColor.GRAY + "Bonus: " + CorePrestigeManager.getPrestigeBonus(nextPrestige));
            pLore.add("");
            pLore.add(ChatColor.RED + " ⚠ " + ChatColor.GRAY + "Tu nivel se resetea a 1");
            pLore.add(ChatColor.RED + " ⚠ " + ChatColor.GRAY + "Las mejoras se mantienen");
            pLore.add(ChatColor.GREEN + " ✔ " + ChatColor.GRAY + "Desbloquea Núcleos Prestige " + nextPrestige);
            pLore.add("");
            pLore.add(ChatColor.DARK_GRAY + " ◆ " + ChatColor.GRAY + "Precio: " + ChatColor.GOLD + "$" + CoreUpgradesShopGUI.formatPrice(prestigeCost));
            pLore.add("");
            pLore.add(ChatColor.YELLOW + " ▶ Click para prestigiar");
            if (nextNeedsVip) {
                pLore.add("");
                pLore.add(ChatColor.RED + " ⚠ " + ChatColor.GRAY + "Siguiente nivel requiere rango VIP");
                pLore.add(ChatColor.GRAY + "   Prestigia para acceder a núcleos de prestigio");
            }
            pLore.add(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━");
            pMeta.setLore(pLore);
            prestigeBtn.setItemMeta(pMeta);
            inv.setItem(11, prestigeBtn);
        } else if (!isMaxLevel && (nextNeedsVip || nextNeedsPrestige)) {
            // Can't upgrade AND can't prestige — blocked
            ItemStack blockedBtn = new ItemStack(Material.BARRIER);
            ItemMeta bMeta = blockedBtn.getItemMeta();
            bMeta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "✖ " + SmallCaps.convert("NO PUEDES MEJORAR"));
            List<String> bLore = new ArrayList<>();
            bLore.add(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━");
            bLore.add("");
            if (nextNeedsVip) {
                bLore.add(ChatColor.RED + " El siguiente nivel requiere rango VIP.");
                bLore.add(ChatColor.GRAY + " Consíguelo en: " + ChatColor.YELLOW + "moonlightmc.tebex.io");
            }
            if (nextNeedsPrestige) {
                bLore.add(ChatColor.RED + " Requiere Prestigio Core " + nextLevel.getPrestigeRomanNumeral());
            }
            bLore.add("");
            bLore.add(ChatColor.AQUA + " ▶ Click der. ver todos los niveles");
            bLore.add(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━");
            bMeta.setLore(bLore);
            blockedBtn.setItemMeta(bMeta);
            inv.setItem(11, blockedBtn);
        } else {
            ItemStack maxBtn = new ItemStack(Material.NETHER_STAR);
            ItemMeta maxMeta = maxBtn.getItemMeta();
            maxMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "★ " + SmallCaps.convert("NIVEL MÁXIMO") + " ★");
            maxMeta.setLore(Arrays.asList(
                    ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━",
                    "",
                    ChatColor.DARK_GRAY + " ◆ " + ChatColor.GRAY + "Nivel: " + ChatColor.AQUA + "30" + ChatColor.DARK_GRAY + "/30",
                    ChatColor.DARK_GRAY + " ◆ " + ChatColor.GRAY + "Prestigio: " + CorePrestigeManager.getPrestigeName(region.getPrestige()),
                    ChatColor.DARK_GRAY + " ◆ " + ChatColor.GRAY + "Área: " + ChatColor.GREEN + region.getSize() + "x" + region.getSize(),
                    "",
                    ChatColor.GOLD + "" + ChatColor.BOLD + " ✦ " + ChatColor.YELLOW + "¡Eres imparable!",
                    "",
                    ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━"
            ));
            maxBtn.setItemMeta(maxMeta);
            inv.setItem(11, maxBtn);
        }

        // Slot 13: MEMBERS
        ItemStack membersBtn = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta membersMeta = membersBtn.getItemMeta();
        membersMeta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "♟ " + SmallCaps.convert("MIEMBROS"));
        List<String> membersLore = new ArrayList<>();
        membersLore.add(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━");
        membersLore.add("");
        membersLore.add(ChatColor.DARK_GRAY + " ◆ " + ChatColor.GRAY + "Total: " + ChatColor.WHITE + region.getMembers().size() + ChatColor.DARK_GRAY + " miembros");
        membersLore.add("");
        if (!region.getMembers().isEmpty()) {
            int shown = 0;
            for (UUID memberId : region.getMembers()) {
                if (shown >= 5) { membersLore.add(ChatColor.DARK_GRAY + "   ... y " + (region.getMembers().size() - 5) + " más"); break; }
                String name = Bukkit.getOfflinePlayer(memberId).getName();
                membersLore.add(ChatColor.DARK_GRAY + "   ▸ " + ChatColor.WHITE + (name != null ? name : "???"));
                shown++;
            }
            membersLore.add("");
        }
        membersLore.add(ChatColor.YELLOW + " ▶ Click para gestionar");
        membersLore.add(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━");
        membersMeta.setLore(membersLore);
        membersBtn.setItemMeta(membersMeta);
        inv.setItem(13, membersBtn);

        // Slot 15: UPGRADES SHOP
        ItemStack shopBtn = new ItemStack(Material.NETHER_STAR);
        ItemMeta shopMeta = shopBtn.getItemMeta();
        shopMeta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "✸ " + SmallCaps.convert("MEJORAS DE ZONA"));
        List<String> shopLore = new ArrayList<>();
        shopLore.add(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━");
        shopLore.add("");
        shopLore.add(ChatColor.DARK_GRAY + " ◆ " + ChatColor.GRAY + "Activas: " + ChatColor.GREEN + region.getActiveUpgradeCount() + ChatColor.DARK_GRAY + "/" + ChatColor.WHITE + region.getTotalUpgradeCount());
        shopLore.add("");
        shopLore.add(ChatColor.DARK_GRAY + "   ▸ " + ChatColor.WHITE + "Anti-Explosión, Anti-PvP");
        shopLore.add(ChatColor.DARK_GRAY + "   ▸ " + ChatColor.WHITE + "Boost de Daño y Vida");
        shopLore.add(ChatColor.DARK_GRAY + "   ▸ " + ChatColor.WHITE + "Vuelo, XP, Lucky Mining");
        shopLore.add(ChatColor.DARK_GRAY + "   ▸ " + ChatColor.WHITE + "Anti-Enderman, Beacon...");
        shopLore.add("");
        shopLore.add(ChatColor.YELLOW + " ▶ Click para ver tienda");
        shopLore.add(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━");
        shopMeta.setLore(shopLore);
        shopBtn.setItemMeta(shopMeta);
        inv.setItem(15, shopBtn);

        // ═══════════════════════════════════════
        // ROW 3: QUICK ACTIONS (slots 28-34)
        // ═══════════════════════════════════════

        // Slot 29: SETTINGS
        ItemStack settingsBtn = new ItemStack(Material.COMPARATOR);
        ItemMeta setMeta = settingsBtn.getItemMeta();
        setMeta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "⚙ " + SmallCaps.convert("CONFIGURACIÓN"));
        List<String> setLore = new ArrayList<>();
        setLore.add(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━");
        setLore.add("");
        setLore.add(ChatColor.DARK_GRAY + "   ▸ " + ChatColor.WHITE + "Nombre del territorio");
        setLore.add(ChatColor.DARK_GRAY + "   ▸ " + ChatColor.WHITE + "Modo de entrada");
        setLore.add(ChatColor.DARK_GRAY + "   ▸ " + ChatColor.WHITE + "Mensaje de bienvenida");
        setLore.add(ChatColor.DARK_GRAY + "   ▸ " + ChatColor.WHITE + "Transferir propiedad");
        setLore.add(ChatColor.DARK_GRAY + "   ▸ " + ChatColor.WHITE + "Ajustes avanzados");
        setLore.add("");
        setLore.add(ChatColor.YELLOW + " ▶ Click para abrir");
        setLore.add(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━");
        setMeta.setLore(setLore);
        settingsBtn.setItemMeta(setMeta);
        inv.setItem(29, settingsBtn);

        // Slot 31: VISUALS TOGGLE
        boolean visualsActive = plugin.getProtectionManager().isVisualActive(region.getId());
        ItemStack visualsBtn = new ItemStack(visualsActive ? Material.ENDER_EYE : Material.ENDER_PEARL);
        ItemMeta visMeta = visualsBtn.getItemMeta();
        visMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "◎ " + SmallCaps.convert("VISUALIZAR ZONA"));
        List<String> visLore = new ArrayList<>();
        visLore.add(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━");
        visLore.add("");
        visLore.add(ChatColor.DARK_GRAY + " ◆ " + ChatColor.GRAY + "Muestra los bordes de tu zona");
        visLore.add(ChatColor.DARK_GRAY + "   " + ChatColor.GRAY + "con partículas de colores.");
        visLore.add("");
        visLore.add(ChatColor.DARK_GRAY + " ◆ " + ChatColor.GRAY + "Estado: " + (visualsActive ? ChatColor.GREEN + "✔ ACTIVO" : ChatColor.RED + "✖ INACTIVO"));
        visLore.add("");
        visLore.add(ChatColor.YELLOW + " ▶ Click para " + (visualsActive ? "desactivar" : "activar"));
        visLore.add(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━");
        visMeta.setLore(visLore);
        visualsBtn.setItemMeta(visMeta);
        inv.setItem(31, visualsBtn);

        // Slot 33: TELEPORT TO CORE (if coreTeleport upgrade is unlocked)
        ItemStack tpBtn = new ItemStack(region.isCoreTeleport() ? Material.ENDER_PEARL : Material.FIREWORK_STAR);
        ItemMeta tpMeta = tpBtn.getItemMeta();
        if (region.isCoreTeleport()) {
            tpMeta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "⚡ " + SmallCaps.convert("TELETRANSPORTE"));
            tpMeta.setLore(Arrays.asList(
                    ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━",
                    "",
                    ChatColor.DARK_GRAY + " ◆ " + ChatColor.GRAY + "Viaja instantáneamente",
                    ChatColor.DARK_GRAY + "   " + ChatColor.GRAY + "a la ubicación de tu núcleo.",
                    "",
                    ChatColor.YELLOW + " ▶ Click para teletransportarte",
                    ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━"
            ));
        } else {
            tpMeta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "⚡ " + SmallCaps.convert("TELETRANSPORTE"));
            tpMeta.setLore(Arrays.asList(
                    ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━",
                    "",
                    ChatColor.RED + " ✖ " + ChatColor.GRAY + "Necesitas la mejora",
                    ChatColor.DARK_GRAY + "   " + ChatColor.WHITE + "\"Teletransporte\"",
                    ChatColor.DARK_GRAY + "   " + ChatColor.GRAY + "en la tienda de mejoras.",
                    "",
                    ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━"
            ));
        }
        tpBtn.setItemMeta(tpMeta);
        inv.setItem(33, tpBtn);

        // ═══════════════════════════════════════
        // ROW 4: UPGRADE STATUS DISPLAY (slots 37-43)
        // ═══════════════════════════════════════

        inv.setItem(37, upgradeIcon(Material.TNT, "Anti-Explosión",
                region.isNoExplosion(), "Protección contra explosiones", null, 0, 0));
        inv.setItem(38, upgradeIcon(Material.SHIELD, "Anti-PvP",
                region.isNoPvP(), "Bloquea daño entre jugadores", null, 0, 0));
        inv.setItem(39, upgradeIcon(Material.DIAMOND_SWORD, "Boost Daño",
                region.getDamageBoostLevel() > 0, "+5% daño por nivel", "nv", region.getDamageBoostLevel(), 5));
        inv.setItem(40, upgradeIcon(Material.ELYTRA, "Fly Zone",
                region.isFlyZone(), "Vuelo en tu territorio", null, 0, 0));
        inv.setItem(41, upgradeIcon(Material.GOLDEN_APPLE, "Boost Vida",
                region.getHealthBoostLevel() > 0, "+1❤ por nivel", "nv", region.getHealthBoostLevel(), 5));
        inv.setItem(42, upgradeIcon(Material.EXPERIENCE_BOTTLE, "XP Boost",
                region.getXpBoostLevel() > 0, "+10% XP por nivel", "nv", region.getXpBoostLevel(), 5));
        inv.setItem(43, upgradeIcon(Material.DIAMOND_PICKAXE, "Lucky Mining",
                region.getLuckyMiningLevel() > 0, "Doble drop al minar", "nv", region.getLuckyMiningLevel(), 3));

        // ═══════════════════════════════════════
        // ROW 5: MORE UPGRADES + CLOSE (slots 46-52)
        // ═══════════════════════════════════════

        inv.setItem(46, upgradeIcon(Material.FEATHER, "Sin Caída",
                region.isNoFallDamage(), "Sin daño por caída", null, 0, 0));
        inv.setItem(47, upgradeIcon(Material.WHEAT, "Auto-Replant",
                region.isAutoReplant(), "Replantado automático", null, 0, 0));
        inv.setItem(48, upgradeIcon(Material.BLAZE_POWDER, "Anti-Fuego",
                region.isAntiFireSpread(), "Sin propagación de fuego", null, 0, 0));
        inv.setItem(50, upgradeIcon(Material.PHANTOM_MEMBRANE, "Anti-Phantoms",
                region.isAntiPhantom(), "Phantoms no aparecen", null, 0, 0));
        inv.setItem(51, upgradeIcon(Material.BEACON, "Beacon Aura",
                region.isBeaconAura(), "Night Vision + Haste", null, 0, 0));
        inv.setItem(52, upgradeIcon(Material.ENDER_EYE, "Anti-Enderman",
                region.isAntiEnderman(), "Endermen no mueven bloques", null, 0, 0));

        // Slot 49: CLOSE button (center bottom)
        ItemStack closeBtn = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeBtn.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "✖ " + SmallCaps.convert("CERRAR"));
        closeMeta.setLore(Arrays.asList("", ChatColor.GRAY + "Click para cerrar el menú"));
        closeBtn.setItemMeta(closeMeta);
        inv.setItem(49, closeBtn);

        player.openInventory(inv);
    }

    // ═══════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════

    private ItemStack glass(Material material) {
        ItemStack g = new ItemStack(material);
        ItemMeta m = g.getItemMeta();
        m.setDisplayName(" ");
        g.setItemMeta(m);
        return g;
    }

    private ItemStack upgradeIcon(Material material, String name, boolean active, String desc, String levelTag, int current, int max) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        String prefix = active ? ChatColor.GREEN + "✔ " : ChatColor.DARK_GRAY + "✖ ";
        String nameColor = active ? ChatColor.GREEN + "" : ChatColor.DARK_GRAY + "";
        meta.setDisplayName(prefix + nameColor + name);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + desc);
        if (levelTag != null && max > 0) {
            lore.add("");
            lore.add(ChatColor.GRAY + "Nivel: " + buildProgressBar(current, max) + " " + ChatColor.WHITE + current + ChatColor.DARK_GRAY + "/" + max);
        }
        lore.add("");
        lore.add(active ? ChatColor.GREEN + "● Activo" : ChatColor.DARK_GRAY + "● No comprado");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String buildProgressBar(int current, int max) {
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < max; i++) {
            bar.append(i < current ? ChatColor.GREEN + "█" : ChatColor.DARK_GRAY + "█");
        }
        return bar.toString();
    }

    private String buildLevelBar(int current, int max) {
        StringBuilder bar = new StringBuilder(ChatColor.DARK_GRAY + "[");
        int filled = (int) Math.round((current / (double) max) * 10);
        for (int i = 0; i < 10; i++) {
            bar.append(i < filled ? ChatColor.AQUA + "▮" : ChatColor.DARK_GRAY + "▮");
        }
        bar.append(ChatColor.DARK_GRAY + "]");
        return bar.toString();
    }

    private String formatMaterial(Material material) {
        String name = material.name().replace("_", " ").toLowerCase();
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    public static boolean isManagementGUI(String title) {
        return title.contains(SmallCaps.convert("Panel de Núcleo"));
    }
}
