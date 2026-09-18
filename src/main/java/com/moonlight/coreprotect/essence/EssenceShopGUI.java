package com.moonlight.coreprotect.essence;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.meta.BlockStateMeta;

import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Tienda de Esencias — GUI diamante 54 slots.
 * Kits: click derecho = preview, click izquierdo = comprar.
 * Kits con habilidades activas (Shift+RClick) gestionadas por KitAbilitiesListener.
 */
public class EssenceShopGUI implements Listener {

    static final String GUI_TITLE = SmallCaps.convert("§3§l✦ §b§lᴇꜱ§3§lᴇɴ§9§lᴄɪ§1§lᴀꜱ §3§l✦");
    static final String PREVIEW_PREFIX = SmallCaps.convert("§3Contenido: ");
    static final String CONFIRM_TITLE = SmallCaps.convert("§e§l¿Confirmar compra?");
    static final String SPAWNER_TITLE = SmallCaps.convert("§3§l✦ §4§lꜱᴘᴀᴡɴᴇʀꜱ §3§l✦");
    private static final int[] KIT_SLOTS = {28, 29, 30, 32, 33, 34};
    private final CoreProtectPlugin plugin;
    private final java.util.Map<java.util.UUID, PendingPurchase> pendingPurchases = new java.util.HashMap<>();

    public EssenceShopGUI(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    // ==================== PENDING PURCHASE DATA ====================

    private static class PendingPurchase {
        final int slot;
        final String itemName;
        final int cost;

        PendingPurchase(int slot, String itemName, int cost) {
            this.slot = slot;
            this.itemName = itemName;
            this.cost = cost;
        }
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, GUI_TITLE);
        EssenceManager mgr = plugin.getEssenceManager();
        int bal = mgr.getEssences(player.getUniqueId());

        // Fill all with cyan glass, inner area clear
        ItemStack border = gPane(Material.CYAN_STAINED_GLASS_PANE);
        ItemStack dark = gPane(Material.BLUE_STAINED_GLASS_PANE);
        for (int i = 0; i < 54; i++) inv.setItem(i, border);
        for (int row = 1; row <= 4; row++)
            for (int col = 1; col <= 7; col++)
                inv.setItem(row * 9 + col, null);

        // ===== ROW 0: BALANCE (slot 4) =====
        inv.setItem(4, createItem(Material.AMETHYST_CLUSTER, "§b§lTus Esencias: §f" + bal,
                "§7Gana esencias matando bosses,",
                "§7ganando KOTH, estando AFK,",
                "§7o convirtiendo dinero abajo.", "",
                "§b✦ §7Ender Dragon: §a+2  §b✦ §7Warden: §a+0.5",
                "§b✦ §7Boss Kill: §a+12  §b✦ §7KOTH: §a+8",
                "§b✦ §7Participar: §a+4  §b✦ §7AFK/h: §a+1"));

        // ===== ROW 1 — PIRÁMIDE: 3 items (slots 11,13,15) =====
        inv.setItem(11, shopItem(Material.GOLDEN_APPLE, "§c§l/heal §7— Curación", 25, bal,
                "§7Añade 1 uso del comando §c/heal§7.", "§7De un solo uso.", "§cCooldown de compra: 1 hora", "", "§9Coste: §f25 Esencias"));
        inv.setItem(13, shopItem(Material.COOKED_BEEF, "§6§l/feed §7— Hambre", 10, bal,
                "§7Añade 1 uso del comando §6/feed§7.", "§7De un solo uso.", "", "§9Coste: §f10 Esencias"));
        inv.setItem(15, shopItem(Material.ENDER_EYE, "§9§lNight Vision §7— 1h", 15, bal,
                "§7Visión nocturna 1 hora.", "", "§9Coste: §f15 Esencias"));

        // ===== ROW 2 — 5 items (slots 20,21, dark:22, 23,24) =====
        inv.setItem(20, shopItem(Material.SHIELD, "§7§lResistencia II §7— 10min", 25, bal,
                "§7Resistencia II durante 10 min.", "", "§9Coste: §f25 Esencias"));
        inv.setItem(21, shopItem(Material.BLAZE_POWDER, "§4§lFuerza II §7— 10min", 25, bal,
                "§7Fuerza II durante 10 min.", "", "§9Coste: §f25 Esencias"));
        inv.setItem(22, shopItem(Material.NETHERITE_SWORD, "§b§lFilo Fantasma", 120, bal,
                "§7Espada Netherite con habilidad §b§lDash§7.",
                "§7Click Derecho: §bEmbestida Fantasma§7.",
                "§7Daño bonus al impactar enemigos.",
                "§7Cooldown: §e1.75s", "", "§9Coste: §f120 Esencias"));
        inv.setItem(23, shopItem(Material.FEATHER, "§b§lVuelo 30 min", 150, bal,
                "§7Vuela durante 30 minutos.", "", "§9Coste: §f150 Esencias"));
        inv.setItem(24, shopItem(Material.ELYTRA, "§b§lVuelo 1 hora", 250, bal,
                "§7Vuela durante 1 hora.", "", "§9Coste: §f250 Esencias"));

        // ===== ROW 3 — ANCHA: 7 items (slots 28-34) — KITS =====
        inv.setItem(28, kitShopItem(Material.DIAMOND_CHESTPLATE, "§b§lKit Guerrero", 50, bal,
                "§7Diamante full Prot IV,",
                "§7espada Sharp V, pico Fortune III.", "",
                "§b§l⚔ HABILIDAD: §7Shift + Click Derecho:", "§7 • Grito de Guerra: §bKnockback AoE", "", "§9Coste: §f50 Esencias"));
        inv.setItem(29, kitShopItem(Material.NETHERITE_CHESTPLATE, "§d§lKit Esencia", 80, bal,
                "§7Netherite Mending full,",
                "§7espada, pico, hacha, god apples.", "",
                "§d§l✦ HABILIDAD: §7Shift + Click Derecho:", "§7 • Pulso Arcano: §dCuración + Resistencia", "", "§9Coste: §f80 Esencias"));
        inv.setItem(30, kitShopItem(Material.NETHERITE_SWORD, "§c§lKit PvP", 70, bal,
                "§7Netherite Prot IV, espada, crystals,",
                "§7obsidiana, totem.", "",
                "§c§l⚡ HABILIDAD: §7Shift + Click Derecho:", "§7 • Ráfaga Carmesí: §cDash + Daño de fuego", "", "§9Coste: §f70 Esencias"));
        inv.setItem(31, shopItem(Material.NETHERITE_SWORD, "§6§lColmillo Sísmico", 140, bal,
                "§7Espada con habilidad §6§lTerremoto§7.",
                "§7Click Derecho: §eCarga Sísmica§7.",
                "§7¡Salta y cae para desatar el terremoto!",
                "§7Más altura = más daño y radio.",
                "§7Cooldown: §e18s", "", "§9Coste: §f140 Esencias"));
        inv.setItem(32, kitShopItem(Material.TOTEM_OF_UNDYING, "§6§lKit Dios", 200, bal,
                "§7Netherite §6§lGOD§7, elytra, totem x2,",
                "§7arco OP, 16 god apples, cohetes.", "",
                "§6§l⚡ HABILIDAD: §7Shift + Click Derecho:", "§7 • Juicio Divino: §6Rayo encadenado", "", "§9Coste: §f200 Esencias"));
        inv.setItem(33, kitShopItem(Material.NETHERITE_PICKAXE, "§a§lKit Minero", 40, bal,
                "§7Pico/Hacha/Pala Netherite Mending,",
                "§7TNT, torches + Haste II 20min.", "", "§9Coste: §f40 Esencias"));
        inv.setItem(34, kitShopItem(Material.BRICKS, "§e§lKit Constructor", 30, bal,
                "§764x de 10 tipos de bloques.", "", "§9Coste: §f30 Esencias"));

        // ===== ROW 4 — 5 items (slots 38,39,40,41,42) — PREMIUM =====
        inv.setItem(38, shopItem(Material.ELYTRA, "§d§lElytra", 100, bal,
                "§7Elytra + Unbreaking III + 32 cohetes.", "", "§9Coste: §f100 Esencias"));
        inv.setItem(39, shopItem(Material.TOTEM_OF_UNDYING, "§6§lTotem x3", 50, bal,
                "§73 Totems of Undying.", "", "§9Coste: §f50 Esencias"));
        inv.setItem(40, shopItem(Material.ENCHANTED_GOLDEN_APPLE, "§d§l32x God Apples", 160, bal,
                "§732 Enchanted Golden Apples.", "", "§9Coste: §f160 Esencias"));
        inv.setItem(41, shopItem(Material.SHULKER_BOX, "§5§lShulker Box", 40, bal,
                "§71 Shulker Box.", "", "§9Coste: §f40 Esencias"));
        inv.setItem(42, shopItem(Material.BEACON, "§f§lBeacon", 90, bal,
                "§71 Beacon.", "", "§9Coste: §f90 Esencias"));

        // ===== ROW 5 — PUNTA INFERIOR: 3 items (slots 48,49,50) =====
        inv.setItem(47, createItem(Material.SPAWNER, "§4§lSpawners",
                "§7Compra spawners de mobs", "§7por esencias.", "", "§e▶ Click para abrir"));
        inv.setItem(48, shopItem(Material.TRIPWIRE_HOOK, "§6§lLlave Special", 35, bal,
                "§7x1 Llave Special.", "", "§9Coste: §f35 Esencias"));
        inv.setItem(49, createItem(Material.GOLD_BLOCK, "§e§lConvertir Dinero",
                "§7Convierte §e$20,000,000 §7en", "§b4 Esencias§7.", "", "§eClick para convertir"));
        inv.setItem(50, shopItem(Material.TRIPWIRE_HOOK, "§e§lLlave KOTH", 40, bal,
                "§7x1 Llave KOTH.", "", "§9Coste: §f40 Esencias"));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
    }

    // ==================== CONFIRMATION GUI ====================

    private void openConfirmation(Player player, int slot, String itemName, int cost) {
        Inventory inv = Bukkit.createInventory(null, 27, CONFIRM_TITLE);
        EssenceManager mgr = plugin.getEssenceManager();
        int balance = mgr.getEssences(player.getUniqueId());

        // Fill with gray glass
        ItemStack gray = gPane(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < 27; i++) inv.setItem(i, gray);

        // Item being purchased (center)
        inv.setItem(13, createItem(Material.PAPER, "§e§l" + itemName,
                "§7Coste: §b" + cost + " Esencias",
                "§7Tu balance: §b" + balance + " Esencias",
                "",
                "§7¿Estás seguro de esta compra?"));

        // GREEN WOOL - Confirm (left side)
        inv.setItem(11, createItem(Material.LIME_WOOL, "§a§l✓ CONFIRMAR",
                "§7Click para confirmar la compra."));

        // RED WOOL - Cancel (right side)
        inv.setItem(15, createItem(Material.RED_WOOL, "§c§l✖ CANCELAR",
                "§7Click para cancelar."));

        // Store pending purchase
        pendingPurchases.put(player.getUniqueId(), new PendingPurchase(slot, itemName, cost));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.0f);
    }

    // ==================== PREVIEW ====================

    private void openPreview(Player player, String kitName) {
        String title = PREVIEW_PREFIX + kitDisplayName(kitName);
        List<ItemStack> items = getKitPreviewItems(kitName);
        int size = Math.max(27, ((items.size() / 9) + 2) * 9);
        if (size > 54) size = 54;
        Inventory inv = Bukkit.createInventory(null, size, title);
        for (int i = 0; i < items.size() && i < size - 9; i++) inv.setItem(i, items.get(i));
        inv.setItem(size - 5, createItem(Material.ARROW, "§c§l← Volver", "§7Click para volver a la tienda."));
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.2f);
    }

    private String kitDisplayName(String kit) {
        switch (kit) { case "guerrero": return "§b§lKit Guerrero"; case "esencia": return "§d§lKit Esencia";
            case "pvp": return "§c§lKit PvP"; case "dios": return "§6§lKit Dios";
            case "minero": return "§a§lKit Minero"; case "constructor": return "§e§lKit Constructor"; default: return kit; }
    }

    private String slotToKitName(int slot) {
        switch (slot) { case 28: return "guerrero"; case 29: return "esencia"; case 30: return "pvp";
            case 32: return "dios"; case 33: return "minero"; case 34: return "constructor"; default: return null; }
    }

    private boolean isKitSlot(int slot) { for (int s : KIT_SLOTS) if (s == slot) return true; return false; }

    // ==================== CLICK HANDLER ====================

    public void handleClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        
        // Confirmation GUI
        if (title.equals(CONFIRM_TITLE)) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player)) return;
            Player player = (Player) event.getWhoClicked();
            int slot = event.getRawSlot();

            if (slot == 11) { // GREEN WOOL - Confirm
                PendingPurchase pending = pendingPurchases.remove(player.getUniqueId());
                if (pending != null) {
                    player.closeInventory();
                    // Execute the purchase
                    executePurchase(player, pending.slot);
                }
            } else if (slot == 15) { // RED WOOL - Cancel
                pendingPurchases.remove(player.getUniqueId());
                player.sendMessage(sc("§c§l✖ §cCompra cancelada."));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
                Bukkit.getScheduler().runTaskLater(plugin, () -> { if (player.isOnline()) open(player); }, 1L);
            }
            return;
        }
        
        // Preview GUI back button
        if (title.startsWith(PREVIEW_PREFIX)) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player)) return;
            Player player = (Player) event.getWhoClicked();
            if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.ARROW) open(player);
            return;
        }
        // Spawner sub-GUI
        if (title.equals(SPAWNER_TITLE)) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player)) return;
            Player p = (Player) event.getWhoClicked();
            int s = event.getRawSlot();
            if (s == 49) { open(p); return; } // Back button
            handleSpawnerClick(p, s);
            return;
        }
        if (!title.equals(GUI_TITLE)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        EssenceManager mgr = plugin.getEssenceManager();
        int slot = event.getRawSlot();

        // Kit slots: right-click = preview
        if (isKitSlot(slot) && (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT)) {
            String kitName = slotToKitName(slot);
            if (kitName != null) openPreview(player, kitName);
            return;
        }

        // Spawner shop button
        if (slot == 47) {
            openSpawnerShop(player);
            return;
        }

        // Show confirmation for all purchases
        String itemName = getItemName(slot);
        int cost = getItemCost(slot);
        
        if (itemName != null && cost > 0) {
            openConfirmation(player, slot, itemName, cost);
            return;
        }
        
        // Special case: money conversion (slot 49) - no confirmation needed
        switch (slot) {
            case 49: // Money conversion - direct action, no confirmation
                if (mgr.convertMoney(player)) {
                    mgr.saveData();
                    player.sendMessage(sc("§3§l✦ §aConversión exitosa: §c-$20,000,000 §a→ §b+4 Esencias"));
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> { if (player.isOnline()) open(player); }, 2L);
                } else {
                    player.sendMessage(sc("§c§l✖ §cNecesitas al menos §e$20,000,000 §cpara convertir."));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                }
                break;
        }
    }

    // ==================== PURCHASE EXECUTION ====================

    private void executePurchase(Player player, int slot) {
        EssenceManager mgr = plugin.getEssenceManager();
        
        switch (slot) {
            // Row 1: Commands (Pyramid)
            case 11: buyHeal(player, mgr); break;
            case 13: buyFeed(player, mgr); break;
            case 15: buyEffect(player, mgr, PotionEffectType.NIGHT_VISION, 72000, 0, 15, "Night Vision 1h"); break;
            // Row 2: Effects + Fly
            case 20: buyEffect(player, mgr, PotionEffectType.RESISTANCE, 12000, 1, 25, "Resistencia II 10min"); break;
            case 21: buyEffect(player, mgr, PotionEffectType.STRENGTH, 12000, 1, 25, "Fuerza II 10min"); break;
            case 22: buyDashSword(player, mgr); break;
            case 23: buyFly(player, mgr, 30, 150); break;
            case 24: buyFly(player, mgr, 60, 250); break;
            // Row 3: Kits
            case 28: buyKitGuerrero(player, mgr); break;
            case 29: buyKitEsencia(player, mgr); break;
            case 30: buyKitPvP(player, mgr); break;
            case 31: buyEarthquakeSword(player, mgr); break;
            case 32: buyKitDios(player, mgr); break;
            case 33: buyKitMinero(player, mgr); break;
            case 34: buyKitConstructor(player, mgr); break;
            // Row 4: Premium
            case 38: buyElytra(player, mgr); break;
            case 39: buyItem(player, mgr, new ItemStack(Material.TOTEM_OF_UNDYING, 3), 50, "Totem x3"); break;
            case 40: buyItem(player, mgr, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 32), 160, "32x God Apples"); break;
            case 41: buyItem(player, mgr, new ItemStack(Material.SHULKER_BOX, 1), 40, "Shulker Box"); break;
            case 42: buyItem(player, mgr, new ItemStack(Material.BEACON, 1), 90, "Beacon"); break;
            // Row 5: Keys
            case 48: buyCrateKey(player, mgr, "special", 35, "§6Special"); break;
            case 50: buyCrateKey(player, mgr, "koth", 40, "§eKOTH"); break;
        }
    }

    private String getItemName(int slot) {
        switch (slot) {
            case 11: return "/heal";
            case 13: return "/feed";
            case 15: return "Night Vision 1h";
            case 20: return "Resistencia II 10min";
            case 21: return "Fuerza II 10min";
            case 22: return "Filo Fantasma";
            case 23: return "Vuelo 30min";
            case 24: return "Vuelo 1h";
            case 28: return "Kit Guerrero";
            case 29: return "Kit Esencia";
            case 30: return "Kit PvP";
            case 31: return "Colmillo Sísmico";
            case 32: return "Kit Dios";
            case 33: return "Kit Minero";
            case 34: return "Kit Constructor";
            case 38: return "Elytra";
            case 39: return "Totem x3";
            case 40: return "32x God Apples";
            case 41: return "Shulker Box";
            case 42: return "Beacon";
            case 48: return "Llave Special";
            case 50: return "Llave KOTH";
            default: return null;
        }
    }

    private int getItemCost(int slot) {
        switch (slot) {
            case 11: return 25;
            case 13: return 10;
            case 15: return 15;
            case 20: return 25;
            case 21: return 25;
            case 22: return 120;
            case 23: return 150;
            case 24: return 250;
            case 28: return 50;
            case 29: return 80;
            case 30: return 70;
            case 31: return 140;
            case 32: return 200;
            case 33: return 40;
            case 34: return 30;
            case 38: return 100;
            case 39: return 50;
            case 40: return 160;
            case 41: return 40;
            case 42: return 90;
            case 48: return 35;
            case 50: return 40;
            default: return 0;
        }
    }

    // ==================== COMMANDS ====================

    private void buyHeal(Player player, EssenceManager mgr) {
        org.bukkit.NamespacedKey cooldownKey = new org.bukkit.NamespacedKey(plugin, "heal_cooldown");
        long current = System.currentTimeMillis();
        long cooldownExpires = player.getPersistentDataContainer().getOrDefault(cooldownKey, org.bukkit.persistence.PersistentDataType.LONG, 0L);
        
        if (current < cooldownExpires) {
            long left = (cooldownExpires - current) / 1000 / 60;
            player.sendMessage(sc("§c§l✖ §cDebes esperar §e" + left + " minutos §cpara volver a comprar /heal."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }

        if (!charge(player, mgr, 25)) return;
        
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "single_use_heal");
        int charges = player.getPersistentDataContainer().getOrDefault(key, org.bukkit.persistence.PersistentDataType.INTEGER, 0);
        player.getPersistentDataContainer().set(key, org.bukkit.persistence.PersistentDataType.INTEGER, charges + 1);
        
        // Establecer cooldown de 1 hora (3600000 ms)
        player.getPersistentDataContainer().set(cooldownKey, org.bukkit.persistence.PersistentDataType.LONG, current + 3600000L);
        
        player.sendMessage(sc("§3§l✦ §aHas comprado 1 uso de §c/heal§a."));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
    }

    private void buyFeed(Player player, EssenceManager mgr) {
        if (!charge(player, mgr, 10)) return;
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "single_use_feed");
        int charges = player.getPersistentDataContainer().getOrDefault(key, org.bukkit.persistence.PersistentDataType.INTEGER, 0);
        player.getPersistentDataContainer().set(key, org.bukkit.persistence.PersistentDataType.INTEGER, charges + 1);
        player.sendMessage(sc("§3§l✦ §aHas comprado 1 uso de §6/feed§a."));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
    }

    private void buyDashSword(Player player, EssenceManager mgr) {
        if (!hasSpace(player, 1)) return;
        if (!charge(player, mgr, 120)) return;
        player.closeInventory();
        player.getInventory().addItem(DashSword.create(plugin));
        player.sendMessage(sc("§3§l✦ §b¡Filo Fantasma recibido! §7Click Derecho para usar la Embestida Fantasma."));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
    }

    private void buyEarthquakeSword(Player player, EssenceManager mgr) {
        if (!hasSpace(player, 1)) return;
        if (!charge(player, mgr, 140)) return;
        player.closeInventory();
        player.getInventory().addItem(EarthquakeSword.create(plugin));
        player.sendMessage(sc("§3§l✦ §6¡Colmillo Sísmico recibido! §7Click Derecho para cargar, salta y cae."));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
    }

    private void buyEffect(Player player, EssenceManager mgr, PotionEffectType type, int ticks, int amp, int cost, String name) {
        if (!charge(player, mgr, cost)) return;
        player.addPotionEffect(new PotionEffect(type, ticks, amp, false, false, true));
        player.sendMessage(sc("§3§l✦ §a¡" + name + " activado!"));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
    }

    private void buyFly(Player player, EssenceManager mgr, int minutes, int cost) {
        if (!charge(player, mgr, cost)) return;
        player.closeInventory();
        // Grant essentials.fly via LuckPerms temp permission
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "lp user " + player.getName() + " permission settemp essentials.fly true " + minutes + "m");
        player.sendMessage(sc("§3§l✦ §b¡Vuelo activado por " + minutes + " min!"));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
    }

    private boolean hasSpace(Player player, int required) {
        int emptySlots = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType() == Material.AIR) emptySlots++;
        }
        if (emptySlots < required) {
            player.sendMessage(sc("§c§l✖ §cNo tienes espacio suficiente en tu inventario."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return false;
        }
        return true;
    }

    // ==================== KITS ====================

    private void buyKitGuerrero(Player player, EssenceManager mgr) {
        if (!hasSpace(player, 8)) return;
        if (!charge(player, mgr, 50)) return;
        player.closeInventory();
        player.getInventory().addItem(
            ench(new ItemStack(Material.DIAMOND_HELMET), "§b§lCasco Guerrero", Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3)),
            ench(new ItemStack(Material.DIAMOND_CHESTPLATE), "§b§lPeto Guerrero", Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3)),
            ench(new ItemStack(Material.DIAMOND_LEGGINGS), "§b§lPantalones Guerrero", Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3)),
            ench(new ItemStack(Material.DIAMOND_BOOTS), "§b§lBotas Guerrero", Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.FEATHER_FALLING, 4)),
            enchWithAbility(new ItemStack(Material.DIAMOND_SWORD), "§b§lEspada Guerrero", Map.of(Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 3, Enchantment.FIRE_ASPECT, 2), "§b§l⚔ HABILIDAD: §7Shift + Click Derecho", "§7 • Grito de Guerra: §bKnockback AoE", "§7Cooldown: §e25s"),
            ench(new ItemStack(Material.DIAMOND_PICKAXE), "§b§lPico Guerrero", Map.of(Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3, Enchantment.FORTUNE, 3)),
            ench(new ItemStack(Material.DIAMOND_AXE), "§b§lHacha Guerrero", Map.of(Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3)),
            new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 16), new ItemStack(Material.GOLDEN_APPLE, 32)
        );
        player.sendMessage(sc("§3§l✦ §b¡Kit Guerrero recibido!"));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
    }

    private void buyKitEsencia(Player player, EssenceManager mgr) {
        if (!hasSpace(player, 9)) return;
        if (!charge(player, mgr, 80)) return;
        player.closeInventory();
        player.getInventory().addItem(
            ench(new ItemStack(Material.NETHERITE_HELMET), "§d§lCasco Esencia", Map.of(Enchantment.PROTECTION, 5, Enchantment.UNBREAKING, 4, Enchantment.MENDING, 1, Enchantment.RESPIRATION, 3, Enchantment.AQUA_AFFINITY, 1)),
            ench(new ItemStack(Material.NETHERITE_CHESTPLATE), "§d§lPeto Esencia", Map.of(Enchantment.PROTECTION, 5, Enchantment.UNBREAKING, 4, Enchantment.MENDING, 1)),
            ench(new ItemStack(Material.NETHERITE_LEGGINGS), "§d§lPantalones Esencia", Map.of(Enchantment.PROTECTION, 5, Enchantment.UNBREAKING, 4, Enchantment.MENDING, 1, Enchantment.SWIFT_SNEAK, 3)),
            ench(new ItemStack(Material.NETHERITE_BOOTS), "§d§lBotas Esencia", Map.of(Enchantment.PROTECTION, 5, Enchantment.UNBREAKING, 4, Enchantment.MENDING, 1, Enchantment.FEATHER_FALLING, 4, Enchantment.DEPTH_STRIDER, 3)),
            enchWithAbility(new ItemStack(Material.NETHERITE_SWORD), "§d§lEspada Esencia", Map.of(Enchantment.SHARPNESS, 6, Enchantment.UNBREAKING, 4, Enchantment.MENDING, 1, Enchantment.FIRE_ASPECT, 2, Enchantment.LOOTING, 3), "§d§l✦ HABILIDAD: §7Shift + Click Derecho", "§7 • Pulso Arcano: §dCuración + Resistencia", "§7Cooldown: §e30s"),
            ench(new ItemStack(Material.NETHERITE_PICKAXE), "§d§lPico Esencia", Map.of(Enchantment.EFFICIENCY, 6, Enchantment.UNBREAKING, 4, Enchantment.MENDING, 1, Enchantment.FORTUNE, 4)),
            ench(new ItemStack(Material.NETHERITE_AXE), "§d§lHacha Esencia", Map.of(Enchantment.EFFICIENCY, 6, Enchantment.UNBREAKING, 4, Enchantment.MENDING, 1, Enchantment.SHARPNESS, 5)),
            ench(new ItemStack(Material.MACE), "§d§lMazo Esencia", Map.of(Enchantment.DENSITY, 6, Enchantment.UNBREAKING, 4, Enchantment.MENDING, 1, Enchantment.SWEEPING_EDGE, 4, Enchantment.WIND_BURST, 3)),
            new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 12), new ItemStack(Material.GOLDEN_APPLE, 48)
        );
        player.sendMessage(sc("§3§l✦ §d¡Kit Esencia recibido!"));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
    }

    private void buyKitPvP(Player player, EssenceManager mgr) {
        if (!hasSpace(player, 10)) return;
        if (!charge(player, mgr, 70)) return;
        player.closeInventory();
        player.getInventory().addItem(
            ench(new ItemStack(Material.NETHERITE_HELMET), "§c§lCasco PvP", Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1)),
            ench(new ItemStack(Material.NETHERITE_CHESTPLATE), "§c§lPeto PvP", Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1)),
            ench(new ItemStack(Material.NETHERITE_LEGGINGS), "§c§lPantalones PvP", Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1)),
            ench(new ItemStack(Material.NETHERITE_BOOTS), "§c§lBotas PvP", Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1, Enchantment.FEATHER_FALLING, 4)),
            enchWithAbility(new ItemStack(Material.NETHERITE_SWORD), "§c§lEspada PvP", Map.of(Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1, Enchantment.FIRE_ASPECT, 2), "§c§l⚡ HABILIDAD: §7Shift + Click Derecho", "§7 • Ráfaga Carmesí: §cDash + Daño de fuego", "§7Cooldown: §e20s"),
            new ItemStack(Material.END_CRYSTAL, 8), new ItemStack(Material.OBSIDIAN, 16),
            new ItemStack(Material.TOTEM_OF_UNDYING, 1), new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 8),
            new ItemStack(Material.GOLDEN_APPLE, 32)
        );
        player.sendMessage(sc("§3§l✦ §c¡Kit PvP recibido!"));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
    }

    private void buyKitDios(Player player, EssenceManager mgr) {
        if (!hasSpace(player, 12)) return;
        if (!charge(player, mgr, 200)) return;
        player.closeInventory();
        ItemStack elytra = new ItemStack(Material.ELYTRA);
        ItemMeta em = elytra.getItemMeta();
        em.setDisplayName(sc("§6§lElytra Divina"));
        em.addEnchant(Enchantment.UNBREAKING, 3, true);
        em.addEnchant(Enchantment.MENDING, 1, true);
        elytra.setItemMeta(em);
        player.getInventory().addItem(
            ench(new ItemStack(Material.NETHERITE_HELMET), "§6§lCasco Dios", Map.of(Enchantment.PROTECTION, 6, Enchantment.UNBREAKING, 5, Enchantment.MENDING, 1, Enchantment.RESPIRATION, 3, Enchantment.AQUA_AFFINITY, 1)),
            elytra,
            ench(new ItemStack(Material.NETHERITE_LEGGINGS), "§6§lPantalones Dios", Map.of(Enchantment.PROTECTION, 6, Enchantment.UNBREAKING, 5, Enchantment.MENDING, 1, Enchantment.SWIFT_SNEAK, 4)),
            ench(new ItemStack(Material.NETHERITE_BOOTS), "§6§lBotas Dios", Map.of(Enchantment.PROTECTION, 6, Enchantment.UNBREAKING, 5, Enchantment.MENDING, 1, Enchantment.FEATHER_FALLING, 4, Enchantment.DEPTH_STRIDER, 3, Enchantment.SOUL_SPEED, 4)),
            enchWithAbility(new ItemStack(Material.NETHERITE_SWORD), "§6§lEspada Dios", Map.of(Enchantment.SHARPNESS, 7, Enchantment.UNBREAKING, 5, Enchantment.MENDING, 1, Enchantment.FIRE_ASPECT, 3, Enchantment.SWEEPING_EDGE, 4, Enchantment.LOOTING, 4), "§6§l⚡ HABILIDAD: §7Shift + Click Derecho", "§7 • Juicio Divino: §6Rayo encadenado", "§7Cooldown: §e40s"),
            ench(new ItemStack(Material.NETHERITE_PICKAXE), "§6§lPico Dios", Map.of(Enchantment.EFFICIENCY, 7, Enchantment.UNBREAKING, 5, Enchantment.MENDING, 1, Enchantment.FORTUNE, 5)),
            ench(new ItemStack(Material.BOW), "§6§lArco Dios", Map.of(Enchantment.POWER, 6, Enchantment.UNBREAKING, 5, Enchantment.MENDING, 1, Enchantment.FLAME, 1, Enchantment.INFINITY, 1)),
            ench(new ItemStack(Material.MACE), "§6§lMazo Dios", Map.of(Enchantment.DENSITY, 7, Enchantment.UNBREAKING, 5, Enchantment.MENDING, 1, Enchantment.WIND_BURST, 3, Enchantment.LOOTING, 4)),
            new ItemStack(Material.TOTEM_OF_UNDYING, 3), new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 24),
            new ItemStack(Material.GOLDEN_APPLE, 64), new ItemStack(Material.FIREWORK_ROCKET, 64)
        );
        player.sendMessage(sc("§3§l✦ §6¡Kit Dios recibido!"));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
    }

    private void buyKitMinero(Player player, EssenceManager mgr) {
        if (!hasSpace(player, 7)) return;
        if (!charge(player, mgr, 40)) return;
        player.closeInventory();
        player.getInventory().addItem(
            ench(new ItemStack(Material.NETHERITE_PICKAXE), "§a§lPico Minero", Map.of(Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1, Enchantment.FORTUNE, 3)),
            ench(new ItemStack(Material.NETHERITE_AXE), "§a§lHacha Minero", Map.of(Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1)),
            ench(new ItemStack(Material.NETHERITE_SHOVEL), "§a§lPala Minero", Map.of(Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1)),
            new ItemStack(Material.TORCH, 64), new ItemStack(Material.RAIL, 32),
            new ItemStack(Material.TNT, 8), new ItemStack(Material.GOLDEN_APPLE, 16)
        );
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 24000, 1, false, false, true));
        player.sendMessage(sc("§3§l✦ §a¡Kit Minero recibido! §7+Haste II 20min"));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
    }

    private void buyKitConstructor(Player player, EssenceManager mgr) {
        if (!hasSpace(player, 10)) return;
        if (!charge(player, mgr, 30)) return;
        player.closeInventory();
        player.getInventory().addItem(
            new ItemStack(Material.BRICKS, 64), new ItemStack(Material.QUARTZ_BLOCK, 64),
            new ItemStack(Material.GLASS, 64), new ItemStack(Material.SMOOTH_STONE, 64),
            new ItemStack(Material.DARK_OAK_PLANKS, 64), new ItemStack(Material.POLISHED_DEEPSLATE, 64),
            new ItemStack(Material.IRON_BARS, 32), new ItemStack(Material.LANTERN, 32),
            new ItemStack(Material.COPPER_BLOCK, 32), new ItemStack(Material.MUD_BRICKS, 64)
        );
        player.sendMessage(sc("§3§l✦ §e¡Kit Constructor recibido!"));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
    }

    private void buyElytra(Player player, EssenceManager mgr) {
        if (!hasSpace(player, 2)) return;
        if (!charge(player, mgr, 100)) return;
        player.closeInventory();
        ItemStack elytra = new ItemStack(Material.ELYTRA);
        ItemMeta em = elytra.getItemMeta();
        em.setDisplayName(sc("§d§lElytra Esencial"));
        em.addEnchant(Enchantment.UNBREAKING, 3, true);
        elytra.setItemMeta(em);
        player.getInventory().addItem(elytra, new ItemStack(Material.FIREWORK_ROCKET, 32));
        player.sendMessage(sc("§3§l✦ §d¡Elytra + 32 cohetes!"));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
    }

    // ==================== KIT PREVIEW ITEMS ====================

    private List<ItemStack> getKitPreviewItems(String kit) {
        List<ItemStack> items = new ArrayList<>();
        switch (kit) {
            case "guerrero": items.addAll(Arrays.asList(
                ench(new ItemStack(Material.DIAMOND_HELMET), "§b§lCasco Guerrero", Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3)),
                ench(new ItemStack(Material.DIAMOND_CHESTPLATE), "§b§lPeto Guerrero", Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3)),
                ench(new ItemStack(Material.DIAMOND_LEGGINGS), "§b§lPantalones Guerrero", Map.of(Enchantment.PROTECTION, 4)),
                ench(new ItemStack(Material.DIAMOND_BOOTS), "§b§lBotas Guerrero", Map.of(Enchantment.PROTECTION, 4, Enchantment.FEATHER_FALLING, 4)),
                enchWithAbility(new ItemStack(Material.DIAMOND_SWORD), "§b§lEspada Guerrero", Map.of(Enchantment.SHARPNESS, 5, Enchantment.FIRE_ASPECT, 2), "§b§l⚔ HABILIDAD: §7Shift + Click Derecho", "§7 • Grito de Guerra: §bKnockback AoE", "§7Cooldown: §e25s"),
                ench(new ItemStack(Material.DIAMOND_PICKAXE), "§b§lPico Guerrero", Map.of(Enchantment.EFFICIENCY, 5, Enchantment.FORTUNE, 3)),
                ench(new ItemStack(Material.DIAMOND_AXE), "§b§lHacha Guerrero", Map.of(Enchantment.EFFICIENCY, 5)),
                new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 16), new ItemStack(Material.GOLDEN_APPLE, 32)
            )); break;
            case "esencia": items.addAll(Arrays.asList(
                ench(new ItemStack(Material.NETHERITE_HELMET), "§d§lCasco Esencia", Map.of(Enchantment.PROTECTION, 5, Enchantment.MENDING, 1, Enchantment.RESPIRATION, 3, Enchantment.AQUA_AFFINITY, 1)),
                ench(new ItemStack(Material.NETHERITE_CHESTPLATE), "§d§lPeto Esencia", Map.of(Enchantment.PROTECTION, 5, Enchantment.MENDING, 1)),
                ench(new ItemStack(Material.NETHERITE_LEGGINGS), "§d§lPantalones Esencia", Map.of(Enchantment.PROTECTION, 5, Enchantment.MENDING, 1, Enchantment.SWIFT_SNEAK, 3)),
                ench(new ItemStack(Material.NETHERITE_BOOTS), "§d§lBotas Esencia", Map.of(Enchantment.PROTECTION, 5, Enchantment.MENDING, 1, Enchantment.FEATHER_FALLING, 4, Enchantment.DEPTH_STRIDER, 3)),
                enchWithAbility(new ItemStack(Material.NETHERITE_SWORD), "§d§lEspada Esencia", Map.of(Enchantment.SHARPNESS, 6, Enchantment.FIRE_ASPECT, 2, Enchantment.LOOTING, 3), "§d§l✦ HABILIDAD: §7Shift + Click Derecho", "§7 • Pulso Arcano: §dCuración + Resistencia", "§7Cooldown: §e30s"),
                ench(new ItemStack(Material.NETHERITE_PICKAXE), "§d§lPico Esencia", Map.of(Enchantment.EFFICIENCY, 6, Enchantment.FORTUNE, 4)),
                ench(new ItemStack(Material.NETHERITE_AXE), "§d§lHacha Esencia", Map.of(Enchantment.EFFICIENCY, 6, Enchantment.SHARPNESS, 5)),
                ench(new ItemStack(Material.MACE), "§d§lMazo Esencia", Map.of(Enchantment.DENSITY, 6, Enchantment.MENDING, 1, Enchantment.SWEEPING_EDGE, 4, Enchantment.WIND_BURST, 3)),
                new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 12), new ItemStack(Material.GOLDEN_APPLE, 48)
            )); break;
            case "pvp": items.addAll(Arrays.asList(
                ench(new ItemStack(Material.NETHERITE_HELMET), "§c§lCasco PvP", Map.of(Enchantment.PROTECTION, 4, Enchantment.MENDING, 1, Enchantment.UNBREAKING, 3)),
                ench(new ItemStack(Material.NETHERITE_CHESTPLATE), "§c§lPeto PvP", Map.of(Enchantment.PROTECTION, 4, Enchantment.MENDING, 1, Enchantment.UNBREAKING, 3)),
                ench(new ItemStack(Material.NETHERITE_LEGGINGS), "§c§lPantalones PvP", Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3)),
                ench(new ItemStack(Material.NETHERITE_BOOTS), "§c§lBotas PvP", Map.of(Enchantment.PROTECTION, 4, Enchantment.FEATHER_FALLING, 4, Enchantment.UNBREAKING, 3)),
                enchWithAbility(new ItemStack(Material.NETHERITE_SWORD), "§c§lEspada PvP", Map.of(Enchantment.SHARPNESS, 5, Enchantment.FIRE_ASPECT, 2, Enchantment.UNBREAKING, 3), "§c§l⚡ HABILIDAD: §7Shift + Click Derecho", "§7 • Ráfaga Carmesí: §cDash + Daño de fuego", "§7Cooldown: §e20s"),
                new ItemStack(Material.END_CRYSTAL, 8), new ItemStack(Material.OBSIDIAN, 16),
                new ItemStack(Material.TOTEM_OF_UNDYING, 1), new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 8),
                createItem(Material.BLAZE_POWDER, "§c§l⚡ Ráfaga Carmesí", "§7Shift + Click Derecho con espada:", "§7Dash + Daño de fuego en línea", "§7Cooldown: §e20s")
            )); break;
            case "dios": items.addAll(Arrays.asList(
                ench(new ItemStack(Material.NETHERITE_HELMET), "§6§lCasco Dios", Map.of(Enchantment.PROTECTION, 6, Enchantment.MENDING, 1, Enchantment.RESPIRATION, 3, Enchantment.AQUA_AFFINITY, 1)),
                ench(new ItemStack(Material.NETHERITE_LEGGINGS), "§6§lPantalones Dios", Map.of(Enchantment.PROTECTION, 6, Enchantment.SWIFT_SNEAK, 4)),
                ench(new ItemStack(Material.NETHERITE_BOOTS), "§6§lBotas Dios", Map.of(Enchantment.PROTECTION, 6, Enchantment.FEATHER_FALLING, 4, Enchantment.DEPTH_STRIDER, 3, Enchantment.SOUL_SPEED, 4)),
                enchWithAbility(new ItemStack(Material.NETHERITE_SWORD), "§6§lEspada Dios", Map.of(Enchantment.SHARPNESS, 7, Enchantment.SWEEPING_EDGE, 4, Enchantment.LOOTING, 4), "§6§l⚡ HABILIDAD: §7Shift + Click Derecho", "§7 • Juicio Divino: §6Rayo encadenado", "§7Cooldown: §e40s"),
                ench(new ItemStack(Material.BOW), "§6§lArco Dios", Map.of(Enchantment.POWER, 6, Enchantment.FLAME, 1, Enchantment.INFINITY, 1)),
                ench(new ItemStack(Material.MACE), "§6§lMazo Dios", Map.of(Enchantment.DENSITY, 7, Enchantment.MENDING, 1, Enchantment.WIND_BURST, 3, Enchantment.LOOTING, 4)),
                new ItemStack(Material.ELYTRA), new ItemStack(Material.TOTEM_OF_UNDYING, 3),
                new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 24), new ItemStack(Material.FIREWORK_ROCKET, 64),
                createItem(Material.NETHER_STAR, "§6§l⚡ Juicio Divino", "§7Shift + Click Derecho con espada:", "§7Rayo encadenado a 30 bloques", "§7Cooldown: §e40s")
            )); break;
            case "minero": items.addAll(Arrays.asList(
                ench(new ItemStack(Material.NETHERITE_PICKAXE), "§a§lPico Minero", Map.of(Enchantment.EFFICIENCY, 5, Enchantment.FORTUNE, 3, Enchantment.MENDING, 1)),
                ench(new ItemStack(Material.NETHERITE_AXE), "§a§lHacha Minero", Map.of(Enchantment.EFFICIENCY, 5, Enchantment.MENDING, 1)),
                ench(new ItemStack(Material.NETHERITE_SHOVEL), "§a§lPala Minero", Map.of(Enchantment.EFFICIENCY, 5, Enchantment.MENDING, 1)),
                new ItemStack(Material.TORCH, 64), new ItemStack(Material.RAIL, 32), new ItemStack(Material.TNT, 8),
                createItem(Material.POTION, "§a+Haste II §7(20 min)")
            )); break;
            case "constructor": items.addAll(Arrays.asList(
                new ItemStack(Material.BRICKS, 64), new ItemStack(Material.QUARTZ_BLOCK, 64),
                new ItemStack(Material.GLASS, 64), new ItemStack(Material.SMOOTH_STONE, 64),
                new ItemStack(Material.DARK_OAK_PLANKS, 64), new ItemStack(Material.POLISHED_DEEPSLATE, 64),
                new ItemStack(Material.IRON_BARS, 32), new ItemStack(Material.LANTERN, 32),
                new ItemStack(Material.COPPER_BLOCK, 32), new ItemStack(Material.MUD_BRICKS, 64)
            )); break;
        }
        return items;
    }

    // ==================== SPAWNER SHOP ====================

    private static final Object[][] SPAWNER_DATA = {
        // { EntityType, display name, Material for icon, cost, slot }
        { EntityType.ZOMBIE,        "§2Zombie",         Material.ROTTEN_FLESH,      30, 10 },
        { EntityType.SKELETON,      "§f Esqueleto",     Material.BONE,              30, 11 },
        { EntityType.SPIDER,        "§8Araña",          Material.STRING,            25, 12 },
        { EntityType.CREEPER,       "§aCreeper",        Material.GUNPOWDER,         40, 13 },
        { EntityType.ENDERMAN,      "§5Enderman",       Material.ENDER_PEARL,       50, 14 },
        { EntityType.BLAZE,         "§6Blaze",          Material.BLAZE_ROD,         55, 15 },
        { EntityType.CAVE_SPIDER,   "§9Araña Cueva",    Material.FERMENTED_SPIDER_EYE, 35, 16 },
        { EntityType.WITCH,         "§5Bruja",          Material.GLASS_BOTTLE,      45, 19 },
        { EntityType.IRON_GOLEM,    "§7Gólem Hierro",   Material.IRON_BLOCK,        80, 20 },
        { EntityType.SLIME,         "§aSlime",          Material.SLIME_BALL,        40, 21 },
        { EntityType.MAGMA_CUBE,    "§cCubo Magma",     Material.MAGMA_CREAM,       40, 22 },
        { EntityType.PIGLIN,        "§6Piglin",         Material.GOLD_INGOT,        45, 23 },
        { EntityType.ZOMBIFIED_PIGLIN,"§eZombie Piglin", Material.GOLD_NUGGET,      50, 24 },
        { EntityType.DROWNED,       "§3Ahogado",        Material.TRIDENT,           45, 25 },
        { EntityType.COW,           "§fVaca",           Material.LEATHER,           20, 28 },
        { EntityType.PIG,           "§dCerdo",          Material.PORKCHOP,          20, 29 },
        { EntityType.SHEEP,         "§fOveja",          Material.WHITE_WOOL,        20, 30 },
        { EntityType.CHICKEN,       "§fGallina",        Material.FEATHER,           15, 31 },
        { EntityType.VILLAGER,      "§aAldeano",        Material.EMERALD,           65, 32 },
        { EntityType.WOLF,          "§fLobo",           Material.BONE,              35, 33 },
        { EntityType.MOOSHROOM,     "§cMooshroom",      Material.RED_MUSHROOM,      35, 34 },
    };

    private void openSpawnerShop(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, SPAWNER_TITLE);
        EssenceManager mgr = plugin.getEssenceManager();
        int bal = mgr.getEssences(player.getUniqueId());

        // Fill border
        ItemStack border = gPane(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < 54; i++) inv.setItem(i, border);
        for (int row = 1; row <= 3; row++)
            for (int col = 1; col <= 7; col++)
                inv.setItem(row * 9 + col, null);

        // Title
        inv.setItem(4, createItem(Material.SPAWNER, "§4§lTienda de Spawners",
                "§7Compra spawners de mobs.", "§7Se colocan como bloques spawner.",
                "", "§7Tu balance: §b" + bal + " Esencias"));

        // Spawner items
        for (Object[] data : SPAWNER_DATA) {
            EntityType type = (EntityType) data[0];
            String name = (String) data[1];
            Material icon = (Material) data[2];
            int cost = (int) data[3];
            int slot = (int) data[4];
            inv.setItem(slot, spawnerShopItem(icon, "§c§l" + name, type, cost, bal));
        }

        // Back button
        inv.setItem(49, createItem(Material.ARROW, "§c§l← Volver", "§7Click para volver a la tienda."));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 0.8f);
    }

    private void handleSpawnerClick(Player player, int slot) {
        for (Object[] data : SPAWNER_DATA) {
            int dataSlot = (int) data[4];
            if (dataSlot == slot) {
                EntityType type = (EntityType) data[0];
                String name = (String) data[1];
                int cost = (int) data[3];
                buySpawner(player, type, name, cost);
                return;
            }
        }
    }

    private void buySpawner(Player player, EntityType type, String name, int cost) {
        EssenceManager mgr = plugin.getEssenceManager();
        if (!hasSpace(player, 1)) return;
        if (!charge(player, mgr, cost)) return;
        player.closeInventory();
        ItemStack spawner = new ItemStack(Material.SPAWNER);
        BlockStateMeta meta = (BlockStateMeta) spawner.getItemMeta();
        CreatureSpawner cs = (CreatureSpawner) meta.getBlockState();
        cs.setSpawnedType(type);
        meta.setBlockState(cs);
        meta.setDisplayName(sc("§c§lSpawner de " + name));
        List<String> lore = new ArrayList<>();
        lore.add(sc("§7Tipo: §f" + formatEntityName(type)));
        lore.add(sc("§7Coloca este spawner para generar mobs."));
        meta.setLore(lore);
        spawner.setItemMeta(meta);
        player.getInventory().addItem(spawner);
        player.sendMessage(sc("§3§l✦ §a¡Spawner de §c" + name + " §arecibido!"));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
    }

    private ItemStack spawnerShopItem(Material icon, String name, EntityType type, int cost, int balance) {
        ItemStack i = new ItemStack(icon);
        ItemMeta m = i.getItemMeta();
        m.setDisplayName(sc(name));
        List<String> lore = new ArrayList<>();
        lore.add(sc("§7Spawner de " + formatEntityName(type)));
        lore.add("");
        lore.add(sc("§9Coste: §f" + cost + " Esencias"));
        lore.add(sc(balance >= cost ? "§a▶ Click para comprar" : "§c✖ No tienes suficientes esencias"));
        m.setLore(lore);
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        if (balance >= cost) { m.addEnchant(Enchantment.UNBREAKING, 1, true); m.addItemFlags(ItemFlag.HIDE_ENCHANTS); }
        i.setItemMeta(m);
        return i;
    }

    private String formatEntityName(EntityType type) {
        String name = type.name().toLowerCase().replace("_", " ");
        StringBuilder sb = new StringBuilder();
        for (String word : name.split(" ")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    // ==================== UTILITY ====================

    private boolean charge(Player player, EssenceManager mgr, int cost) {
        if (!mgr.hasEssences(player.getUniqueId(), cost)) {
            player.sendMessage(sc("§c§l✖ §cNecesitas §b" + cost + " Esencias§c. Tienes §b" + mgr.getEssences(player.getUniqueId()) + "§c."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return false;
        }
        mgr.removeEssences(player.getUniqueId(), cost);
        mgr.saveData();
        return true;
    }

    private void buyCrateKey(Player player, EssenceManager mgr, String keyType, int cost, String displayName) {
        if (!charge(player, mgr, cost)) return;
        player.closeInventory();
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "excellentcrates key give " + player.getName() + " " + keyType + " 1");
        player.sendMessage(sc("§3§l✦ §a¡Llave " + displayName + " §arecibida!"));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
    }

    private void buyItem(Player player, EssenceManager mgr, ItemStack item, int cost, String displayName) {
        if (!hasSpace(player, 1)) return;
        if (!charge(player, mgr, cost)) return;
        player.closeInventory();
        player.getInventory().addItem(item);
        player.sendMessage(sc("§3§l✦ §a¡" + displayName + " recibido!"));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
    }

    // ==================== ITEM BUILDERS ====================

    private static String sc(String s) { return SmallCaps.convert(s); }

    private ItemStack gPane(Material mat) {
        ItemStack i = new ItemStack(mat);
        ItemMeta m = i.getItemMeta();
        m.setDisplayName(" ");
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        i.setItemMeta(m);
        return i;
    }

    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack i = new ItemStack(material);
        ItemMeta m = i.getItemMeta();
        m.setDisplayName(sc(name));
        if (lore != null && lore.length > 0) {
            List<String> conv = new ArrayList<>();
            for (String l : lore) conv.add(sc(l));
            m.setLore(conv);
        }
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        i.setItemMeta(m);
        return i;
    }

    private ItemStack shopItem(Material material, String name, int cost, int balance, String... lore) {
        ItemStack i = new ItemStack(material);
        ItemMeta m = i.getItemMeta();
        m.setDisplayName(sc(name));
        List<String> fullLore = new ArrayList<>();
        for (String l : lore) fullLore.add(sc(l));
        fullLore.add(sc(balance >= cost ? "§a▶ Click para comprar" : "§c✖ No tienes suficientes esencias"));
        m.setLore(fullLore);
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        if (balance >= cost) { m.addEnchant(Enchantment.UNBREAKING, 1, true); m.addItemFlags(ItemFlag.HIDE_ENCHANTS); }
        i.setItemMeta(m);
        return i;
    }

    private ItemStack kitShopItem(Material material, String name, int cost, int balance, String... lore) {
        ItemStack i = new ItemStack(material);
        ItemMeta m = i.getItemMeta();
        m.setDisplayName(sc(name));
        List<String> fullLore = new ArrayList<>();
        for (String l : lore) fullLore.add(sc(l));
        fullLore.add(sc(balance >= cost ? "§a◀ Click izquierdo para comprar" : "§c✖ No tienes suficientes esencias"));
        fullLore.add(sc("§e▶ Click derecho para ver contenido"));
        m.setLore(fullLore);
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        if (balance >= cost) { m.addEnchant(Enchantment.UNBREAKING, 1, true); m.addItemFlags(ItemFlag.HIDE_ENCHANTS); }
        i.setItemMeta(m);
        return i;
    }

    private ItemStack ench(ItemStack item, String name, Map<Enchantment, Integer> enchants) {
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(sc(name));
        for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) m.addEnchant(e.getKey(), e.getValue(), true);
        item.setItemMeta(m);
        return item;
    }

    private ItemStack enchWithAbility(ItemStack item, String name, Map<Enchantment, Integer> enchants, String abilityName, String abilityDesc, String cooldown) {
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(sc(name));
        for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) m.addEnchant(e.getKey(), e.getValue(), true);
        
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(sc(abilityName));
        lore.add(sc(abilityDesc));
        lore.add(sc(cooldown));
        m.setLore(lore);
        
        item.setItemMeta(m);
        return item;
    }
}
