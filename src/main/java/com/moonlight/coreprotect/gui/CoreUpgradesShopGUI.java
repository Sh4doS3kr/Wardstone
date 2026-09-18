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
import com.moonlight.coreprotect.util.SmallCaps;

public class CoreUpgradesShopGUI {

    static final String GUI_TITLE_BASE = SmallCaps.convert(ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "✦ Mejoras de Zona");
    public static final String GUI_TITLE = GUI_TITLE_BASE + " ✦";
    public static final String GUI_TITLE_P2 = GUI_TITLE_BASE + " (2) ✦";
    public static final String GUI_TITLE_P3 = GUI_TITLE_BASE + " (3) ✦";
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
    public static final double PRICE_ANTI_ENDERMAN = 35000;
    public static final double PRICE_RESOURCE_GEN = 250000;
    public static final double PRICE_FIXED_TIME = 90000;
    public static final double PRICE_CORE_TELEPORT = 120000;
    public static final double PRICE_NO_HUNGER = 45000;
    public static final double PRICE_ANTI_PHANTOM = 55000;
    // New upgrades (Huge Update)
    public static final double PRICE_XP_BOOST = 80000; // per level
    public static final double PRICE_CROP_GROWTH = 60000; // per level
    public static final double PRICE_FLY_ZONE = 5000000; // very expensive endgame
    public static final double FLY_COST_PER_SECOND = 750; // $750 per second of flight
    public static final double PRICE_AUTO_REPLANT = 75000;
    public static final double PRICE_LUCKY_MINING = 120000; // per level
    public static final double PRICE_BEACON_AURA = 150000;
    public static final double PRICE_ANTI_FIRE = 40000;
    public static final double PRICE_MOB_REPELLER = 200000;

    private final CoreProtectPlugin plugin;

    public CoreUpgradesShopGUI(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, ProtectedRegion region) {
        open(player, region, 1);
    }

    public void open(Player player, ProtectedRegion region, int page) {
        if (page == 2) {
            openPage2(player, region);
            return;
        }
        // ═══════════════════════════════════════
        // PAGE 1: Protection + Boosts (14 items total)
        // ═══════════════════════════════════════
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, GUI_TITLE);

        ItemStack dark = createGlass(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack accent = createGlass(Material.CYAN_STAINED_GLASS_PANE);
        ItemStack mid = createGlass(Material.GRAY_STAINED_GLASS_PANE);

        // Fill all with dark glass
        for (int i = 0; i < 54; i++) inv.setItem(i, dark);
        // Row 0 accent pattern
        inv.setItem(0, accent); inv.setItem(2, accent); inv.setItem(4, accent); inv.setItem(6, accent); inv.setItem(8, accent);
        // Fill inner rows with mid glass
        for (int i = 9; i < 45; i++) {
            if (i % 9 == 0 || i % 9 == 8) inv.setItem(i, dark);
            else inv.setItem(i, mid);
        }
        // Row 5 accent pattern
        inv.setItem(45, accent); inv.setItem(47, accent); inv.setItem(53, accent); inv.setItem(51, accent);

        // ═══ ROW 1: Protection upgrades (7 items) ═══
        inv.setItem(10, createUpgradeItem(Material.TNT, "Anti-Explosión",
                region.isNoExplosion(), region.isUnlocked("noExplosion"), PRICE_NO_EXPLOSION,
                Arrays.asList(ChatColor.GRAY + "Evita TODAS las explosiones", ChatColor.GRAY + "dentro de tu zona protegida.",
                        "", ChatColor.GRAY + "TNT, Creepers, Bolas de fuego...")));
        inv.setItem(11, createUpgradeItem(Material.ZOMBIE_HEAD, "Anti-Mobs",
                region.isNoMobSpawn(), region.isUnlocked("noMobSpawn"), PRICE_NO_MOB_SPAWN,
                Arrays.asList(ChatColor.GRAY + "Evita el spawn de mobs", ChatColor.GRAY + "hostiles en tu zona.",
                        "", ChatColor.GRAY + "Zombies, Esqueletos, Creepers...")));
        inv.setItem(12, createUpgradeItem(Material.FEATHER, "Sin Caída",
                region.isNoFallDamage(), region.isUnlocked("noFallDamage"), PRICE_NO_FALL_DAMAGE,
                Arrays.asList(ChatColor.GRAY + "Elimina todo el daño", ChatColor.GRAY + "por caída en tu zona.")));
        inv.setItem(13, createUpgradeItem(Material.COOKED_BEEF, "Sin Hambre",
                region.isNoHunger(), region.isUnlocked("noHunger"), PRICE_NO_HUNGER,
                Arrays.asList(ChatColor.GRAY + "No pierdes hambre mientras", ChatColor.GRAY + "estés en tu zona.")));
        inv.setItem(14, createUpgradeItem(Material.ENDER_PEARL, "Anti-Enderman",
                region.isAntiEnderman(), region.isUnlocked("antiEnderman"), PRICE_ANTI_ENDERMAN,
                Arrays.asList(ChatColor.GRAY + "Los Endermen no pueden", ChatColor.GRAY + "mover bloques en tu zona.")));
        inv.setItem(15, createUpgradeItem(Material.PHANTOM_MEMBRANE, "Anti-Phantoms",
                region.isAntiPhantom(), region.isUnlocked("antiPhantom"), PRICE_ANTI_PHANTOM,
                Arrays.asList(ChatColor.GRAY + "Los Phantoms NO pueden", ChatColor.GRAY + "aparecer en tu zona.")));
        inv.setItem(16, createUpgradeItem(Material.LAVA_BUCKET, "Anti-Fuego",
                region.isAntiFireSpread(), region.isUnlocked("antiFireSpread"), PRICE_ANTI_FIRE,
                Arrays.asList(ChatColor.GRAY + "Bloquea la propagación de", ChatColor.GRAY + "fuego y lava en tu zona.")));

        // ═══ ROW 2: Passive upgrades (7 items) ═══
        inv.setItem(19, createUpgradeItem(Material.ENCHANTED_GOLDEN_APPLE, "Auto-Curación",
                region.isAutoHeal(), region.isUnlocked("autoHeal"), PRICE_AUTO_HEAL,
                Arrays.asList(ChatColor.GRAY + "Regeneración pasiva lenta", ChatColor.GRAY + "mientras estés en tu zona.")));
        inv.setItem(20, createUpgradeItem(Material.SUGAR, "Velocidad",
                region.isSpeedBoost(), region.isUnlocked("speedBoost"), PRICE_SPEED_BOOST,
                Arrays.asList(ChatColor.GRAY + "Velocidad de movimiento", ChatColor.GRAY + "aumentada en tu zona.")));
        inv.setItem(21, createLevelUpgradeItem(Material.DIAMOND_SWORD, "Boost de Daño",
                region.getDamageBoostLevel(), 5, PRICE_DAMAGE_BOOST,
                Arrays.asList(ChatColor.GRAY + "+5% de daño por nivel", "", ChatColor.GRAY + "Máximo: +25% daño extra")));
        inv.setItem(22, createLevelUpgradeItem(Material.GOLDEN_APPLE, "Boost de Vida",
                region.getHealthBoostLevel(), 5, PRICE_HEALTH_BOOST,
                Arrays.asList(ChatColor.GRAY + "+2 corazones por nivel", "", ChatColor.GRAY + "Máximo: +10 corazones extra")));
        inv.setItem(23, createLevelUpgradeItem(Material.EXPERIENCE_BOTTLE, "XP Boost",
                region.getXpBoostLevel(), 5, PRICE_XP_BOOST,
                Arrays.asList(ChatColor.GRAY + "+10% XP por nivel", "", ChatColor.GRAY + "Máximo: +50% XP extra")));
        inv.setItem(24, createLevelUpgradeItem(Material.DIAMOND_PICKAXE, "Lucky Mining",
                region.getLuckyMiningLevel(), 3, PRICE_LUCKY_MINING,
                Arrays.asList(ChatColor.GRAY + "+10% doble drop por nivel", "", ChatColor.GRAY + "Máximo: +30% doble drop")));
        inv.setItem(25, createLevelUpgradeItem(Material.BONE_MEAL, "Crecimiento de Cultivos",
                region.getCropGrowthLevel(), 3, PRICE_CROP_GROWTH,
                Arrays.asList(ChatColor.GRAY + "+25% velocidad de cultivos", "", ChatColor.GRAY + "Máximo: +75% más rápido")));

        // ═══ ROW 3: Info ═══
        int active = region.getActiveUpgradeCount();
        int total = region.getTotalUpgradeCount();
        ItemStack infoItem = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName(SmallCaps.convert(ChatColor.GOLD + "" + ChatColor.BOLD + "✦ Información"));
        infoMeta.setLore(Arrays.asList(
                ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━",
                "",
                ChatColor.GRAY + "Mejoras activas: " + ChatColor.GREEN + active + ChatColor.DARK_GRAY + "/" + ChatColor.WHITE + total,
                "",
                ChatColor.GREEN + "Verde" + ChatColor.GRAY + " = Comprado / Activo",
                ChatColor.GOLD + "Dorado" + ChatColor.GRAY + " = Desactivado",
                ChatColor.RED + "Rojo" + ChatColor.GRAY + " = No comprado",
                "",
                ChatColor.DARK_RED + "Shift+Click" + ChatColor.GRAY + " para desactivar",
                ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━"));
        infoItem.setItemMeta(infoMeta);
        inv.setItem(31, infoItem);

        // === NEXT PAGE (slot 50) ===
        ItemStack nextBtn = new ItemStack(Material.ARROW);
        ItemMeta nextMeta = nextBtn.getItemMeta();
        nextMeta.setDisplayName(SmallCaps.convert(ChatColor.GREEN + "" + ChatColor.BOLD + "Página 2 ▶"));
        nextMeta.setLore(SmallCaps.convertList(Arrays.asList("", ChatColor.GRAY + "Mejoras premium y avanzadas")));
        nextBtn.setItemMeta(nextMeta);
        inv.setItem(50, nextBtn);

        // === BACK BUTTON (slot 49) ===
        ItemStack backBtn = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backBtn.getItemMeta();
        backMeta.setDisplayName(SmallCaps.convert(ChatColor.YELLOW + "" + ChatColor.BOLD + "◀ VOLVER"));
        backMeta.setLore(SmallCaps.convertList(Arrays.asList("", ChatColor.GRAY + "Volver al panel principal")));
        backBtn.setItemMeta(backMeta);
        inv.setItem(49, backBtn);

        player.openInventory(inv);
    }

    // ═══════════════════════════════════════
    // PAGE 2: Premium / Advanced (6 items)
    // ═══════════════════════════════════════
    private void openPage2(Player player, ProtectedRegion region) {
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, GUI_TITLE_P2);

        ItemStack dark = createGlass(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack accent = createGlass(Material.PURPLE_STAINED_GLASS_PANE);
        ItemStack mid = createGlass(Material.GRAY_STAINED_GLASS_PANE);

        for (int i = 0; i < 54; i++) inv.setItem(i, dark);
        inv.setItem(0, accent); inv.setItem(2, accent); inv.setItem(4, accent); inv.setItem(6, accent); inv.setItem(8, accent);
        for (int i = 9; i < 45; i++) {
            if (i % 9 == 0 || i % 9 == 8) inv.setItem(i, dark);
            else inv.setItem(i, mid);
        }
        inv.setItem(45, accent); inv.setItem(47, accent); inv.setItem(53, accent); inv.setItem(51, accent);

        // ═══ ROW 1: Premium upgrades ═══
        // Fly Zone — EN MANTENIMIENTO
        ItemStack flyItem = new ItemStack(Material.ELYTRA);
        ItemMeta flyMeta = flyItem.getItemMeta();
        flyMeta.setDisplayName(SmallCaps.convert(ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "✖ Fly Zone " + ChatColor.DARK_RED + "(Mantenimiento)"));
        flyMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        flyMeta.setLore(Arrays.asList(
                ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━",
                "",
                ChatColor.RED + "⚠ EN MANTENIMIENTO ⚠",
                "",
                ChatColor.GRAY + "Esta mejora está siendo",
                ChatColor.GRAY + "balanceada por el equipo.",
                "",
                ChatColor.GRAY + "Los miembros podrán VOLAR",
                ChatColor.GRAY + "dentro de tu zona protegida.",
                "",
                ChatColor.DARK_GRAY + "Precio estimado: " + ChatColor.GOLD + "$" + formatPrice(PRICE_FLY_ZONE),
                ChatColor.DARK_GRAY + "Coste por segundo: " + ChatColor.GOLD + "$" + (int) FLY_COST_PER_SECOND + "/s",
                "",
                ChatColor.RED + "No disponible temporalmente.",
                ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━"));
        flyItem.setItemMeta(flyMeta);
        inv.setItem(10, flyItem);
        inv.setItem(11, createUpgradeItem(Material.ENDER_EYE, "Teletransporte",
                region.isCoreTeleport(), region.isUnlocked("coreTeleport"), PRICE_CORE_TELEPORT,
                Arrays.asList(ChatColor.GRAY + "Teletranspórtate al núcleo", ChatColor.GRAY + "desde el panel de gestión.",
                        "", ChatColor.GRAY + "¡Viaja al instante!")));
        inv.setItem(12, createUpgradeItem(Material.BEACON, "Aura de Faro",
                region.isBeaconAura(), region.isUnlocked("beaconAura"), PRICE_BEACON_AURA,
                Arrays.asList(ChatColor.GRAY + "Visión Nocturna + Prisa", ChatColor.GRAY + "para miembros en tu zona.",
                        "", ChatColor.GRAY + "¡Como un faro permanente!")));
        inv.setItem(13, createUpgradeItem(Material.WHEAT, "Auto-Replant",
                region.isAutoReplant(), region.isUnlocked("autoReplant"), PRICE_AUTO_REPLANT,
                Arrays.asList(ChatColor.GRAY + "Los cultivos se replantan", ChatColor.GRAY + "automáticamente al cosechar.",
                        "", ChatColor.GRAY + "Trigo, zanahorias, patatas...")));
        inv.setItem(14, createUpgradeItem(Material.DIAMOND, "Generador de Recursos",
                region.isResourceGenerator(), region.isUnlocked("resourceGenerator"), PRICE_RESOURCE_GEN,
                Arrays.asList(ChatColor.GRAY + "Genera recursos automáticamente", ChatColor.GRAY + "en un cofre junto al núcleo.",
                        "", ChatColor.GRAY + "Hierro, oro, diamantes...")));

        // Slot 15: Fixed Time
        boolean hasFixedTime = region.getFixedTime() > 0;
        String timeLabel = hasFixedTime ? (region.getFixedTime() == 1 ? "Tiempo Fijo: Día" : "Tiempo Fijo: Noche") : "Tiempo Fijo";
        inv.setItem(15, createUpgradeItem(Material.CLOCK, timeLabel,
                hasFixedTime, region.isUnlocked("fixedTime"), PRICE_FIXED_TIME,
                Arrays.asList(ChatColor.GRAY + "Fija la hora del día dentro", ChatColor.GRAY + "de tu zona protegida.",
                        "", ChatColor.GRAY + "Click = Día / Alterna noche",
                        ChatColor.GRAY + "Shift+Click = Desactivar")));

        // Info
        ItemStack infoItem = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName(SmallCaps.convert(ChatColor.GOLD + "" + ChatColor.BOLD + "✦ Mejoras Premium"));
        infoMeta.setLore(Arrays.asList(
                ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━",
                "",
                ChatColor.GRAY + "Mejoras avanzadas y premium",
                ChatColor.GRAY + "para potenciar tu zona al máximo.",
                "",
                ChatColor.GOLD + "★" + ChatColor.GRAY + " Algunas requieren Prestigio",
                ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━"));
        infoItem.setItemMeta(infoMeta);
        inv.setItem(31, infoItem);

        // Prev page (slot 48)
        ItemStack prevBtn = new ItemStack(Material.ARROW);
        ItemMeta prevMeta = prevBtn.getItemMeta();
        prevMeta.setDisplayName(SmallCaps.convert(ChatColor.GREEN + "" + ChatColor.BOLD + "◀ Página 1"));
        prevMeta.setLore(SmallCaps.convertList(Arrays.asList("", ChatColor.GRAY + "Volver a mejoras básicas")));
        prevBtn.setItemMeta(prevMeta);
        inv.setItem(48, prevBtn);

        // Back to management (slot 49)
        ItemStack backBtn = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backBtn.getItemMeta();
        backMeta.setDisplayName(SmallCaps.convert(ChatColor.YELLOW + "" + ChatColor.BOLD + "◀ VOLVER"));
        backMeta.setLore(SmallCaps.convertList(Arrays.asList("", ChatColor.GRAY + "Volver al panel principal")));
        backBtn.setItemMeta(backMeta);
        inv.setItem(49, backBtn);

        player.openInventory(inv);
    }

    private ItemStack createUpgradeItem(Material material, String name, boolean active, boolean unlocked, double price, List<String> description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        if (active) {
            meta.setDisplayName(SmallCaps.convert(ChatColor.GREEN + "" + ChatColor.BOLD + "✔ " + name));
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.addAll(description);
            lore.add("");
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "✔ ACTIVO");
            lore.add(ChatColor.DARK_RED + "" + ChatColor.ITALIC + "Shift+Click para desactivar");
            meta.setLore(lore);
        } else if (unlocked) {
            meta.setDisplayName(SmallCaps.convert(ChatColor.GOLD + "" + ChatColor.BOLD + "⏸ " + name + " (Desactivado)"));
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.addAll(description);
            lore.add("");
            lore.add(ChatColor.GOLD + "" + ChatColor.BOLD + "⏸ DESACTIVADO");
            lore.add(ChatColor.YELLOW + "▶ Click para reactivar (gratis)");
            meta.setLore(lore);
        } else {
            meta.setDisplayName(SmallCaps.convert(ChatColor.RED + "" + ChatColor.BOLD + "✖ " + name));
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
            meta.setDisplayName(SmallCaps.convert(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "★ " + name + " MÁXIMO"));
        } else if (currentLevel > 0) {
            meta.setDisplayName(SmallCaps.convert(ChatColor.GREEN + "" + ChatColor.BOLD + "✔ " + name + " Nv." + currentLevel));
        } else {
            meta.setDisplayName(SmallCaps.convert(ChatColor.RED + "" + ChatColor.BOLD + "✖ " + name));
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
        meta.setDisplayName(SmallCaps.convert(" "));
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
        return title.contains(SmallCaps.convert("Mejoras de Zona"));
    }

    public static int getPageFromTitle(String title) {
        if (title.contains("(3)")) return 3;
        if (title.contains("(2)")) return 2;
        return 1;
    }
}
