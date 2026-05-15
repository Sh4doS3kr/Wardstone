package com.moonlight.coreprotect.gui;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.core.CoreLevel;
import com.moonlight.coreprotect.core.CorePrestigeManager;
import com.moonlight.coreprotect.core.ProtectedRegion;
import com.moonlight.coreprotect.effects.CoreAnimation;
import com.moonlight.coreprotect.effects.SoundManager;
import com.moonlight.coreprotect.effects.VipAnimation;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import com.moonlight.coreprotect.util.SmallCaps;

public class GUIListener implements Listener {

    private final CoreProtectPlugin plugin;
    // Fixed namespace so items work regardless of plugin display name
    private final NamespacedKey coreKey = new NamespacedKey("coreprotect", "core_level");

    // Track which region a player is managing (regionId by player UUID)
    private static final Map<UUID, UUID> playerRegionMap = new HashMap<>();

    // Track chat input for settings (rename, welcome message)
    private static final Map<UUID, String> pendingChatInput = new HashMap<>(); // UUID -> "rename" or "welcome"

    public GUIListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    public static void setPlayerRegion(UUID playerId, UUID regionId) {
        playerRegionMap.put(playerId, regionId);
    }

    public static UUID getPlayerRegion(UUID playerId) {
        return playerRegionMap.get(playerId);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player))
            return;

        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();

        // === SHOP GUI ===
        if (ShopGUI.isShopGUI(title)) {
            event.setCancelled(true); // Cancel first to prevent any item movement
            handleShopClick(event, player, title);
            return;
        }

        // === MANAGEMENT GUI ===
        if (CoreManagementGUI.isManagementGUI(title)) {
            event.setCancelled(true); // Cancel first to prevent any item movement
            handleManagementClick(event, player);
            return;
        }

        // === UPGRADE PREVIEW GUI ===
        if (CoreUpgradePreviewGUI.isUpgradePreviewGUI(title)) {
            event.setCancelled(true);
            handleUpgradePreviewClick(event, player);
            return;
        }

        // === UPGRADES SHOP GUI ===
        if (CoreUpgradesShopGUI.isUpgradesShopGUI(title)) {
            event.setCancelled(true); // Cancel first to prevent any item movement
            handleUpgradesShopClick(event, player);
            return;
        }

        // === MEMBERS GUI ===
        if (CoreMembersGUI.isMembersGUI(title)) {
            event.setCancelled(true); // Cancel first to prevent any item movement
            handleMembersClick(event, player);
            return;
        }

        // === INVITE GUI ===
        if (CoreMembersGUI.isInviteGUI(title)) {
            event.setCancelled(true); // Cancel first to prevent any item movement
            handleInviteClick(event, player);
            return;
        }

        // === SETTINGS GUI ===
        if (CoreSettingsGUI.isSettingsGUI(title)) {
            event.setCancelled(true);
            handleSettingsClick(event, player);
            return;
        }

        // === FLAGS GUI ===
        if (CoreFlagsGUI.isFlagsGUI(title)) {
            event.setCancelled(true);
            handleFlagsClick(event, player);
            return;
        }

        // === PETS INFO GUI ===
        if (title.equals(PetsInfoGUI.INVENTORY_TITLE)) {
            event.setCancelled(true); // Prevent taking items
            return;
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player))
            return;

        String title = event.getView().getTitle();
        
        // Cancel any drag operations in GUI inventories
        if (ShopGUI.isShopGUI(title) ||
            CoreManagementGUI.isManagementGUI(title) ||
            CoreUpgradePreviewGUI.isUpgradePreviewGUI(title) ||
            CoreUpgradesShopGUI.isUpgradesShopGUI(title) ||
            CoreMembersGUI.isMembersGUI(title) ||
            CoreMembersGUI.isInviteGUI(title) ||
            CoreSettingsGUI.isSettingsGUI(title) ||
            CoreFlagsGUI.isFlagsGUI(title) ||
            title.equals(PetsInfoGUI.INVENTORY_TITLE)) {
            event.setCancelled(true);
        }
    }

    // ===================== SHOP GUI =====================
    private void handleShopClick(InventoryClickEvent event, Player player, String title) {
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (isGlassPane(clicked.getType())) return;

        int currentPage = ShopGUI.getPageFromTitle(title);

        if (event.getSlot() == 48 && currentPage > 1) {
            new ShopGUI(plugin).open(player, currentPage - 1);
            return;
        }
        if (event.getSlot() == 50) {
            new ShopGUI(plugin).open(player, currentPage + 1);
            return;
        }
        if (clicked.getType() == Material.BOOK || clicked.getType() == Material.ARROW) return;

        int level = getLevelFromSlot(event.getSlot(), currentPage);
        if (level < 1 || level > 30) return;

        CoreLevel coreLevel = CoreLevel.fromConfig(plugin.getConfig(), level);
        if (coreLevel == null) return;
        processCorePurchase(player, coreLevel);
    }

    // ===================== MANAGEMENT GUI =====================
    private void handleManagementClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (isGlassPane(clicked.getType())) return;

        ProtectedRegion region = getPlayerManagedRegion(player);
        if (region == null) {
            player.closeInventory();
            return;
        }

        int slot = event.getSlot();

        // Slot 11: UPGRADE CORE (Experience Bottle)
        // Left-click → upgrade, Right-click → preview, Shift+Click → prestige (if available)
        if (slot == 11 && clicked.getType() == Material.EXPERIENCE_BOTTLE) {
            SoundManager.playGUIClick(player.getLocation());
            if (event.isShiftClick() && plugin.getCorePrestigeManager() != null && plugin.getCorePrestigeManager().canPrestige(region)) {
                triggerPrestigeConfirmation(player, region);
            } else if (event.isRightClick()) {
                new CoreUpgradePreviewGUI(plugin).open(player, region);
            } else {
                processUpgradeCore(player, region);
            }
            return;
        }

        // Slot 11: BLOCKED (Barrier) — right-click to see all levels
        if (slot == 11 && clicked.getType() == Material.BARRIER) {
            SoundManager.playGUIClick(player.getLocation());
            if (event.isRightClick()) {
                new CoreUpgradePreviewGUI(plugin).open(player, region);
            }
            return;
        }

        // Slot 11: PRESTIGE CORE (Dragon Egg) — with confirmation warning
        if (slot == 11 && clicked.getType() == Material.DRAGON_EGG) {
            SoundManager.playGUIClick(player.getLocation());
            triggerPrestigeConfirmation(player, region);
            return;
        }

        // Slot 13: MEMBERS
        if (slot == 13) {
            SoundManager.playGUIClick(player.getLocation());
            new CoreMembersGUI(plugin).open(player, region);
            return;
        }

        // Slot 15: UPGRADES SHOP
        if (slot == 15 && clicked.getType() == Material.NETHER_STAR) {
            SoundManager.playGUIClick(player.getLocation());
            new CoreUpgradesShopGUI(plugin).open(player, region);
            return;
        }

        // Slot 29: SETTINGS
        if (slot == 29 && clicked.getType() == Material.COMPARATOR) {
            SoundManager.playGUIClick(player.getLocation());
            new CoreSettingsGUI(plugin).open(player, region);
            return;
        }

        // Slot 31: VISUALS TOGGLE
        if (slot == 31) {
            SoundManager.playGUIClick(player.getLocation());
            toggleVisuals(player, region);
            new CoreManagementGUI(plugin).open(player, region);
            return;
        }

        // Slot 33: TELEPORT TO CORE
        if (slot == 33 && region.isCoreTeleport()) {
            SoundManager.playGUIClick(player.getLocation());
            player.closeInventory();
            org.bukkit.Location coreLoc = region.getCoreLocation();
            if (coreLoc != null) {
                player.teleport(coreLoc.add(0.5, 1, 0.5));
                player.sendMessage(SmallCaps.convert("§a§l⚡ §aTeletransportado a tu núcleo."));
            }
            return;
        }

        // Slot 49: CLOSE
        if (slot == 49) {
            player.closeInventory();
            return;
        }
    }

    // ===================== UPGRADE PREVIEW GUI =====================
    private void handleUpgradePreviewClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (isGlassPane(clicked.getType())) return;

        ProtectedRegion region = getPlayerManagedRegion(player);
        if (region == null) {
            player.closeInventory();
            return;
        }

        int slot = event.getSlot();
        String title = event.getView().getTitle();
        int page = CoreUpgradePreviewGUI.getPageFromTitle(title);

        // Back button
        if (slot == 49 && (clicked.getType() == Material.BARRIER || clicked.getType() == Material.ARROW)) {
            SoundManager.playGUIClick(player.getLocation());
            new CoreManagementGUI(plugin).open(player, region);
            return;
        }

        // Page navigation
        if (slot == 50 && clicked.getType() == Material.ARROW) {
            SoundManager.playGUIClick(player.getLocation());
            new CoreUpgradePreviewGUI(plugin).open(player, region, page + 1);
            return;
        }
        if (slot == 48 && clicked.getType() == Material.ARROW) {
            SoundManager.playGUIClick(player.getLocation());
            new CoreUpgradePreviewGUI(plugin).open(player, region, page - 1);
            return;
        }

        // Determine which level was clicked
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        int slotIndex = -1;
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) { slotIndex = i; break; }
        }
        if (slotIndex < 0) return;

        int clickedLevel = (page - 1) * 21 + slotIndex + 1;
        int nextLevel = region.getLevel() + 1;

        // Only allow upgrading to the NEXT level
        if (clickedLevel != nextLevel) {
            if (clickedLevel <= region.getLevel()) {
                player.sendMessage(SmallCaps.convert("§7Ya has superado este nivel."));
            } else {
                player.sendMessage(SmallCaps.convert("§c§l✖ §cDebes mejorar nivel a nivel. Tu siguiente nivel es §e" + nextLevel + "§c."));
            }
            SoundManager.playGUIClick(player.getLocation());
            return;
        }

        // Process upgrade
        SoundManager.playGUIClick(player.getLocation());
        processUpgradeCore(player, region);
    }

    // ===================== UPGRADES SHOP GUI =====================
    private void handleUpgradesShopClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (isGlassPane(clicked.getType())) return;

        ProtectedRegion region = getPlayerManagedRegion(player);
        if (region == null) {
            player.closeInventory();
            return;
        }

        int slot = event.getSlot();
        String title = event.getView().getTitle();
        int page = CoreUpgradesShopGUI.getPageFromTitle(title);

        // Back to management (available for everyone)
        if (slot == 49) {
            if (clicked.getType() == Material.ARROW || clicked.getType() == Material.BARRIER) {
                SoundManager.playGUIClick(player.getLocation());
                new CoreManagementGUI(plugin).open(player, region);
                return;
            }
        }

        // Page navigation
        if (slot == 50 && clicked.getType() == Material.ARROW) {
            SoundManager.playGUIClick(player.getLocation());
            new CoreUpgradesShopGUI(plugin).open(player, region, page + 1);
            return;
        }
        if (slot == 48 && clicked.getType() == Material.ARROW) {
            SoundManager.playGUIClick(player.getLocation());
            new CoreUpgradesShopGUI(plugin).open(player, region, page - 1);
            return;
        }

        if (!region.getOwner().equals(player.getUniqueId())) {
            plugin.getMessageManager().send(player, "protection.not-owner");
            player.closeInventory();
            return;
        }

        boolean shift = event.isShiftClick();

        // === PAGE 1 UPGRADES ===
        if (page == 1) {
            handlePage1Upgrades(slot, player, region, shift);
        }
        // === PAGE 2 UPGRADES (Premium) ===
        else if (page == 2) {
            handlePage2Upgrades(slot, player, region, shift);
        }
    }

    private void handlePage1Upgrades(int slot, Player player, ProtectedRegion region, boolean shift) {
        // Row 1: Protection toggles (slots 10-16)
        // 10=Anti-Explosion, 11=Anti-Mobs, 12=Sin Caída, 13=Sin Hambre, 14=Anti-Enderman, 15=Anti-Phantoms, 16=Anti-Fuego
        if (slot == 10) { handleToggleUpgrade(player, region, shift, 1, "noExplosion", region.isNoExplosion(), region.isUnlocked("noExplosion"), CoreUpgradesShopGUI.PRICE_NO_EXPLOSION, "Anti-Explosión", r -> r.setNoExplosion(true), r -> r.setNoExplosion(false)); return; }
        if (slot == 11) { handleToggleUpgrade(player, region, shift, 1, "noMobSpawn", region.isNoMobSpawn(), region.isUnlocked("noMobSpawn"), CoreUpgradesShopGUI.PRICE_NO_MOB_SPAWN, "Anti-Mobs", r -> r.setNoMobSpawn(true), r -> r.setNoMobSpawn(false)); return; }
        if (slot == 12) { handleToggleUpgrade(player, region, shift, 1, "noFallDamage", region.isNoFallDamage(), region.isUnlocked("noFallDamage"), CoreUpgradesShopGUI.PRICE_NO_FALL_DAMAGE, "Sin Caída", r -> r.setNoFallDamage(true), r -> r.setNoFallDamage(false)); return; }
        if (slot == 13) { handleToggleUpgrade(player, region, shift, 1, "noHunger", region.isNoHunger(), region.isUnlocked("noHunger"), CoreUpgradesShopGUI.PRICE_NO_HUNGER, "Sin Hambre", r -> r.setNoHunger(true), r -> r.setNoHunger(false)); return; }
        if (slot == 14) { handleToggleUpgrade(player, region, shift, 1, "antiEnderman", region.isAntiEnderman(), region.isUnlocked("antiEnderman"), CoreUpgradesShopGUI.PRICE_ANTI_ENDERMAN, "Anti-Enderman", r -> r.setAntiEnderman(true), r -> r.setAntiEnderman(false)); return; }
        if (slot == 15) { handleToggleUpgrade(player, region, shift, 1, "antiPhantom", region.isAntiPhantom(), region.isUnlocked("antiPhantom"), CoreUpgradesShopGUI.PRICE_ANTI_PHANTOM, "Anti-Phantoms", r -> r.setAntiPhantom(true), r -> r.setAntiPhantom(false)); return; }
        if (slot == 16) { handleToggleUpgrade(player, region, shift, 1, "antiFireSpread", region.isAntiFireSpread(), region.isUnlocked("antiFireSpread"), CoreUpgradesShopGUI.PRICE_ANTI_FIRE, "Anti-Fuego", r -> r.setAntiFireSpread(true), r -> r.setAntiFireSpread(false)); return; }

        // Row 2: Passive / leveled (slots 19-25)
        // 19=Auto-Curación, 20=Velocidad, 21=Boost Daño, 22=Boost Vida, 23=XP Boost, 24=Lucky Mining, 25=Crecimiento
        if (slot == 19) { handleToggleUpgrade(player, region, shift, 1, "autoHeal", region.isAutoHeal(), region.isUnlocked("autoHeal"), CoreUpgradesShopGUI.PRICE_AUTO_HEAL, "Auto-Curación", r -> r.setAutoHeal(true), r -> r.setAutoHeal(false)); return; }
        if (slot == 20) { handleToggleUpgrade(player, region, shift, 1, "speedBoost", region.isSpeedBoost(), region.isUnlocked("speedBoost"), CoreUpgradesShopGUI.PRICE_SPEED_BOOST, "Velocidad", r -> r.setSpeedBoost(true), r -> r.setSpeedBoost(false)); return; }
        if (slot == 21 && region.getDamageBoostLevel() < 5) {
            double price = CoreUpgradesShopGUI.PRICE_DAMAGE_BOOST * (region.getDamageBoostLevel() + 1);
            if (tryPurchase(player, price)) {
                region.setDamageBoostLevel(region.getDamageBoostLevel() + 1);
                plugin.getDataManager().saveData();
                plugin.getMessageManager().send(player, "upgrades.leveled", "{upgrade}", "Boost de Daño", "{level}", String.valueOf(region.getDamageBoostLevel()));
                SoundManager.playUpgradePurchased(player.getLocation());
                plugin.getAchievementListener().onUpgradePurchased(player, region, "damageBoost", price);
                new CoreUpgradesShopGUI(plugin).open(player, region, 1);
            }
            return;
        }
        if (slot == 22 && region.getHealthBoostLevel() < 5) {
            double price = CoreUpgradesShopGUI.PRICE_HEALTH_BOOST * (region.getHealthBoostLevel() + 1);
            if (tryPurchase(player, price)) {
                region.setHealthBoostLevel(region.getHealthBoostLevel() + 1);
                plugin.getDataManager().saveData();
                plugin.getMessageManager().send(player, "upgrades.leveled", "{upgrade}", "Boost de Vida", "{level}", String.valueOf(region.getHealthBoostLevel()));
                SoundManager.playUpgradePurchased(player.getLocation());
                plugin.getAchievementListener().onUpgradePurchased(player, region, "healthBoost", price);
                new CoreUpgradesShopGUI(plugin).open(player, region, 1);
            }
            return;
        }
        if (slot == 23 && region.getXpBoostLevel() < 5) {
            double price = CoreUpgradesShopGUI.PRICE_XP_BOOST * (region.getXpBoostLevel() + 1);
            if (tryPurchase(player, price)) {
                region.setXpBoostLevel(region.getXpBoostLevel() + 1);
                plugin.getDataManager().saveData();
                plugin.getMessageManager().send(player, "upgrades.leveled", "{upgrade}", "XP Boost", "{level}", String.valueOf(region.getXpBoostLevel()));
                SoundManager.playUpgradePurchased(player.getLocation());
                plugin.getAchievementListener().onUpgradePurchased(player, region, "xpBoost", price);
                new CoreUpgradesShopGUI(plugin).open(player, region, 1);
            }
            return;
        }
        if (slot == 24 && region.getLuckyMiningLevel() < 3) {
            double price = CoreUpgradesShopGUI.PRICE_LUCKY_MINING * (region.getLuckyMiningLevel() + 1);
            if (tryPurchase(player, price)) {
                region.setLuckyMiningLevel(region.getLuckyMiningLevel() + 1);
                plugin.getDataManager().saveData();
                plugin.getMessageManager().send(player, "upgrades.leveled", "{upgrade}", "Lucky Mining", "{level}", String.valueOf(region.getLuckyMiningLevel()));
                SoundManager.playUpgradePurchased(player.getLocation());
                plugin.getAchievementListener().onUpgradePurchased(player, region, "luckyMining", price);
                new CoreUpgradesShopGUI(plugin).open(player, region, 1);
            }
            return;
        }
        if (slot == 25 && region.getCropGrowthLevel() < 3) {
            double price = CoreUpgradesShopGUI.PRICE_CROP_GROWTH * (region.getCropGrowthLevel() + 1);
            if (tryPurchase(player, price)) {
                region.setCropGrowthLevel(region.getCropGrowthLevel() + 1);
                plugin.getDataManager().saveData();
                plugin.getMessageManager().send(player, "upgrades.leveled", "{upgrade}", "Crecimiento de Cultivos", "{level}", String.valueOf(region.getCropGrowthLevel()));
                SoundManager.playUpgradePurchased(player.getLocation());
                plugin.getAchievementListener().onUpgradePurchased(player, region, "cropGrowth", price);
                new CoreUpgradesShopGUI(plugin).open(player, region, 1);
            }
            return;
        }
    }

    // Helper for toggle upgrades to reduce code duplication
    private void handleToggleUpgrade(Player player, ProtectedRegion region, boolean shift, int page,
                                     String key, boolean active, boolean unlocked, double price, String name,
                                     java.util.function.Consumer<ProtectedRegion> enable,
                                     java.util.function.Consumer<ProtectedRegion> disable) {
        if (shift && active) {
            disable.accept(region);
            plugin.getDataManager().saveData();
            player.sendMessage(SmallCaps.convert(ChatColor.YELLOW + name + " " + ChatColor.RED + "desactivado."));
            SoundManager.playGUIClick(player.getLocation());
            new CoreUpgradesShopGUI(plugin).open(player, region, page);
            return;
        }
        if (!active) {
            if (unlocked) {
                enable.accept(region);
                plugin.getDataManager().saveData();
                player.sendMessage(SmallCaps.convert(ChatColor.GREEN + name + " reactivado."));
                SoundManager.playUpgradePurchased(player.getLocation());
                new CoreUpgradesShopGUI(plugin).open(player, region, page);
            } else if (tryPurchase(player, price)) {
                enable.accept(region);
                region.unlockUpgrade(key);
                plugin.getDataManager().saveData();
                plugin.getMessageManager().send(player, "upgrades.purchased", "{upgrade}", name);
                SoundManager.playUpgradePurchased(player.getLocation());
                plugin.getAchievementListener().onUpgradePurchased(player, region, key, price);
                new CoreUpgradesShopGUI(plugin).open(player, region, page);
            }
        }
    }

    private void handlePage2Upgrades(int slot, Player player, ProtectedRegion region, boolean shift) {
        // Page 2 new layout: 10=Fly Zone, 11=Teletransporte, 12=Beacon Aura, 13=Auto-Replant, 14=Generador, 15=Tiempo Fijo

        // Slot 10: Fly Zone — EN MANTENIMIENTO
        if (slot == 10) {
            player.sendMessage(SmallCaps.convert(ChatColor.RED + "§l⚠ §c" + "Fly Zone está en mantenimiento. Se está balanceando esta mejora."));
            SoundManager.playUpgradeDenied(player.getLocation());
            return;
        }

        // Slot 11: Teletransporte
        if (slot == 11) { handleToggleUpgrade(player, region, shift, 2, "coreTeleport", region.isCoreTeleport(), region.isUnlocked("coreTeleport"), CoreUpgradesShopGUI.PRICE_CORE_TELEPORT, "Teletransporte", r -> r.setCoreTeleport(true), r -> r.setCoreTeleport(false)); return; }

        // Slot 12: Beacon Aura
        if (slot == 12) { handleToggleUpgrade(player, region, shift, 2, "beaconAura", region.isBeaconAura(), region.isUnlocked("beaconAura"), CoreUpgradesShopGUI.PRICE_BEACON_AURA, "Aura de Faro", r -> r.setBeaconAura(true), r -> r.setBeaconAura(false)); return; }

        // Slot 13: Auto-Replant
        if (slot == 13) { handleToggleUpgrade(player, region, shift, 2, "autoReplant", region.isAutoReplant(), region.isUnlocked("autoReplant"), CoreUpgradesShopGUI.PRICE_AUTO_REPLANT, "Auto-Replant", r -> r.setAutoReplant(true), r -> r.setAutoReplant(false)); return; }

        // Slot 14: Generador de Recursos
        if (slot == 14) { handleToggleUpgrade(player, region, shift, 2, "resourceGenerator", region.isResourceGenerator(), region.isUnlocked("resourceGenerator"), CoreUpgradesShopGUI.PRICE_RESOURCE_GEN, "Generador de Recursos", r -> r.setResourceGenerator(true), r -> r.setResourceGenerator(false)); return; }

        // Slot 15: Tiempo Fijo (special toggle: day/night cycle)
        if (slot == 15) {
            if (shift && region.getFixedTime() > 0) {
                region.setFixedTime(0);
                plugin.getDataManager().saveData();
                player.sendMessage(SmallCaps.convert(ChatColor.YELLOW + "Tiempo Fijo " + ChatColor.RED + "desactivado. " + ChatColor.GRAY + "(ciclo normal)"));
                SoundManager.playGUIClick(player.getLocation());
                new CoreUpgradesShopGUI(plugin).open(player, region, 2);
                return;
            }
            if (region.getFixedTime() == 0) {
                if (region.isUnlocked("fixedTime")) {
                    region.setFixedTime(1);
                    plugin.getDataManager().saveData();
                    player.sendMessage(SmallCaps.convert(ChatColor.GREEN + "Tiempo Fijo reactivado (Día)."));
                    SoundManager.playUpgradePurchased(player.getLocation());
                    new CoreUpgradesShopGUI(plugin).open(player, region, 2);
                } else if (tryPurchase(player, CoreUpgradesShopGUI.PRICE_FIXED_TIME)) {
                    region.setFixedTime(1);
                    region.unlockUpgrade("fixedTime");
                    plugin.getDataManager().saveData();
                    plugin.getMessageManager().send(player, "upgrades.purchased", "{upgrade}", "Tiempo Fijo (Día)");
                    SoundManager.playUpgradePurchased(player.getLocation());
                    plugin.getAchievementListener().onUpgradePurchased(player, region, "fixedTime", CoreUpgradesShopGUI.PRICE_FIXED_TIME);
                    new CoreUpgradesShopGUI(plugin).open(player, region, 2);
                }
            } else {
                region.setFixedTime(region.getFixedTime() == 1 ? 2 : 1);
                plugin.getDataManager().saveData();
                String mode = region.getFixedTime() == 1 ? "Día" : "Noche";
                plugin.getMessageManager().send(player, "upgrades.purchased", "{upgrade}", "Tiempo Fijo (" + mode + ")");
                SoundManager.playGUIClick(player.getLocation());
                new CoreUpgradesShopGUI(plugin).open(player, region, 2);
            }
            return;
        }
    }

    // ===================== SETTINGS GUI =====================
    private void handleSettingsClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (isGlassPane(clicked.getType())) return;

        ProtectedRegion region = getPlayerManagedRegion(player);
        if (region == null) {
            player.closeInventory();
            return;
        }

        boolean isOwner = region.getOwner().equals(player.getUniqueId());
        int slot = event.getSlot();

        // Slot 10: Rename
        if (slot == 10 && isOwner) {
            SoundManager.playGUIClick(player.getLocation());
            player.closeInventory();
            pendingChatInput.put(player.getUniqueId(), "rename");
            player.sendMessage(SmallCaps.convert("§a§l✦ §aEscribe el nuevo nombre §7de tu territorio en el chat."));
            player.sendMessage(SmallCaps.convert("§7Escribe §c'cancelar' §7para cancelar."));
            return;
        }

        // Slot 12: Entry Mode cycle
        if (slot == 12 && isOwner) {
            SoundManager.playGUIClick(player.getLocation());
            int newMode = (region.getEntryMode() + 1) % 3;
            region.setEntryMode(newMode);
            plugin.getDataManager().saveData();
            String[] modeNames = {"§aAbierto", "§cSolo Miembros", "§6Lista Negra"};
            player.sendMessage(SmallCaps.convert("§a§l✦ §7Modo de entrada cambiado a: " + modeNames[newMode]));
            new CoreSettingsGUI(plugin).open(player, region);
            return;
        }

        // Slot 14: Welcome Message
        if (slot == 14 && isOwner) {
            if (event.isShiftClick()) {
                // Clear welcome message
                region.setWelcomeMessage(null);
                plugin.getDataManager().saveData();
                player.sendMessage(SmallCaps.convert("§a§l✦ §7Mensaje de bienvenida §celiminado."));
                SoundManager.playGUIClick(player.getLocation());
                new CoreSettingsGUI(plugin).open(player, region);
            } else {
                player.closeInventory();
                pendingChatInput.put(player.getUniqueId(), "welcome");
                player.sendMessage(SmallCaps.convert("§a§l✦ §aEscribe el mensaje de bienvenida §7en el chat."));
                player.sendMessage(SmallCaps.convert("§7Escribe §c'cancelar' §7para cancelar."));
            }
            return;
        }

        // Slot 16: Transfer Ownership
        if (slot == 16 && isOwner && clicked.getType() == Material.GOLD_INGOT) {
            SoundManager.playGUIClick(player.getLocation());
            player.closeInventory();
            pendingChatInput.put(player.getUniqueId(), "transfer");
            player.sendMessage(SmallCaps.convert("§c§l⚠ §cEscribe el nombre del jugador §7al que quieres transferir."));
            player.sendMessage(SmallCaps.convert("§7Escribe §c'cancelar' §7para cancelar."));
            return;
        }

        // Slot 20: Banned Players
        if (slot == 20 && clicked.getType() == Material.BARRIER) {
            SoundManager.playGUIClick(player.getLocation());
            if (isOwner) {
                player.closeInventory();
                pendingChatInput.put(player.getUniqueId(), "ban");
                player.sendMessage(SmallCaps.convert("§c§l✦ §cEscribe el nombre del jugador §7para banear/desbanear."));
                player.sendMessage(SmallCaps.convert("§7Si ya está baneado, se desbaneará."));
                player.sendMessage(SmallCaps.convert("§7Escribe §c'cancelar' §7para cancelar."));
            } else {
                player.sendMessage(SmallCaps.convert("§c✖ Solo el dueño puede gestionar bans."));
            }
            return;
        }

        // Slot 30: Advanced Flags
        if (slot == 30 && isOwner && clicked.getType() == Material.REPEATER) {
            SoundManager.playGUIClick(player.getLocation());
            new CoreFlagsGUI(plugin).open(player, region);
            return;
        }

        // Slot 24: Delete Core
        if (slot == 24 && isOwner && event.isShiftClick() && clicked.getType() == Material.TNT) {
            SoundManager.playGUIClick(player.getLocation());
            player.closeInventory();
            Location coreLoc = region.getCoreLocation();
            if (coreLoc != null) {
                coreLoc.getBlock().setType(Material.AIR);
            }
            plugin.getProtectionManager().removeRegion(region.getId());
            plugin.getDataManager().saveData();
            player.sendMessage(SmallCaps.convert("§c§l✦ §cTerritorio eliminado permanentemente."));
            return;
        }

        // Slot 49: Back
        if (slot == 49 && clicked.getType() == Material.ARROW) {
            SoundManager.playGUIClick(player.getLocation());
            new CoreManagementGUI(plugin).open(player, region);
            return;
        }
    }

    // ===================== FLAGS GUI =====================
    private void handleFlagsClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (isGlassPane(clicked.getType())) return;

        ProtectedRegion region = getPlayerManagedRegion(player);
        if (region == null) {
            player.closeInventory();
            return;
        }

        if (!region.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cSolo el dueño puede cambiar los ajustes."));
            return;
        }

        int slot = event.getSlot();
        boolean changed = false;

        String reqMsg = SmallCaps.convert("§c§l✖ §cCompra la mejora primero en la §eTienda de Mejoras§c.");
        switch (slot) {
            case 19: // PvP — always toggleable
                region.setAllowPvP(!region.isAllowPvP());
                changed = true;
                break;
            case 20: // Explosiones — requires Anti-Explosión upgrade
                if (!region.isUnlocked("noExplosion")) { player.sendMessage(reqMsg); SoundManager.playUpgradeDenied(player.getLocation()); return; }
                region.setAllowExplosions(!region.isAllowExplosions());
                changed = true;
                break;
            case 21: // Mob Spawn — requires Anti-Mobs upgrade
                if (!region.isUnlocked("noMobSpawn")) { player.sendMessage(reqMsg); SoundManager.playUpgradeDenied(player.getLocation()); return; }
                region.setAllowMobSpawn(!region.isAllowMobSpawn());
                changed = true;
                break;
            case 22: // Fall Damage — requires Sin Caída upgrade
                if (!region.isUnlocked("noFallDamage")) { player.sendMessage(reqMsg); SoundManager.playUpgradeDenied(player.getLocation()); return; }
                region.setAllowFallDamage(!region.isAllowFallDamage());
                changed = true;
                break;
            case 23: // Hambre — requires Sin Hambre upgrade
                if (!region.isUnlocked("noHunger")) { player.sendMessage(reqMsg); SoundManager.playUpgradeDenied(player.getLocation()); return; }
                region.setAllowHunger(!region.isAllowHunger());
                changed = true;
                break;
            case 24: // Fire Spread — requires Anti-Fuego upgrade
                if (!region.isUnlocked("antiFireSpread")) { player.sendMessage(reqMsg); SoundManager.playUpgradeDenied(player.getLocation()); return; }
                region.setAllowFireSpread(!region.isAllowFireSpread());
                changed = true;
                break;
            case 25: // Habilidades — always toggleable
                region.setAllowAbilities(!region.isAllowAbilities());
                changed = true;
                break;
            case 49: // Back
                SoundManager.playGUIClick(player.getLocation());
                new CoreSettingsGUI(plugin).open(player, region);
                return;
        }

        if (changed) {
            SoundManager.playGUIClick(player.getLocation());
            plugin.getDataManager().saveData();
            new CoreFlagsGUI(plugin).open(player, region);
        }
    }

    // === Chat input handler (called from AsyncPlayerChatEvent listener) ===
    public static String getPendingInput(UUID playerId) {
        return pendingChatInput.get(playerId);
    }

    public static void removePendingInput(UUID playerId) {
        pendingChatInput.remove(playerId);
    }

    // ===================== MEMBERS GUI =====================
    private void handleMembersClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (isGlassPane(clicked.getType())) return;

        ProtectedRegion region = getPlayerManagedRegion(player);
        if (region == null) {
            player.closeInventory();
            return;
        }

        int slot = event.getSlot();

        // BACK button
        if (slot == 49 && clicked.getType() == Material.ARROW) {
            SoundManager.playGUIClick(player.getLocation());
            new CoreManagementGUI(plugin).open(player, region);
            return;
        }

        // INVITE button
        if (slot == 40 && clicked.getType() == Material.EMERALD) {
            SoundManager.playGUIClick(player.getLocation());
            new CoreMembersGUI(plugin).openInviteMenu(player, region);
            return;
        }

        // Click on member head = kick
        if (clicked.getType() == Material.PLAYER_HEAD && region.getOwner().equals(player.getUniqueId())) {
            SkullMeta skullMeta = (SkullMeta) clicked.getItemMeta();
            if (skullMeta.getOwningPlayer() != null) {
                UUID memberId = skullMeta.getOwningPlayer().getUniqueId();
                region.removeMember(memberId);
                plugin.getDataManager().saveData();
                String memberName = skullMeta.getOwningPlayer().getName();
                plugin.getMessageManager().send(player, "members.removed", "{player}", memberName != null ? memberName : "???");
                SoundManager.playMemberRemoved(player.getLocation());
                new CoreMembersGUI(plugin).open(player, region);
            }
        }
    }

    // ===================== INVITE GUI =====================
    private void handleInviteClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (isGlassPane(clicked.getType())) return;

        ProtectedRegion region = getPlayerManagedRegion(player);
        if (region == null) {
            player.closeInventory();
            return;
        }

        int slot = event.getSlot();

        // BACK button
        if (slot == 49 && clicked.getType() == Material.ARROW) {
            SoundManager.playGUIClick(player.getLocation());
            new CoreMembersGUI(plugin).open(player, region);
            return;
        }

        // Click on player head = invite
        if (clicked.getType() == Material.PLAYER_HEAD && region.getOwner().equals(player.getUniqueId())) {
            SkullMeta skullMeta = (SkullMeta) clicked.getItemMeta();
            if (skullMeta.getOwningPlayer() != null) {
                UUID targetId = skullMeta.getOwningPlayer().getUniqueId();
                if (!region.isMember(targetId)) {
                    region.addMember(targetId);
                    plugin.getDataManager().saveData();
                    String targetName = skullMeta.getOwningPlayer().getName();
                    plugin.getMessageManager().send(player, "members.added", "{player}", targetName != null ? targetName : "???");
                    SoundManager.playMemberAdded(player.getLocation());

                    // Notify the invited player
                    Player target = Bukkit.getPlayer(targetId);
                    if (target != null && target.isOnline()) {
                        plugin.getMessageManager().send(target, "members.invited-you", "{player}", player.getName());
                    }
                }
                new CoreMembersGUI(plugin).open(player, region);
            }
        }
    }

    // ===================== HELPERS =====================

    private void triggerPrestigeConfirmation(Player player, ProtectedRegion region) {
        if (plugin.getCorePrestigeManager() == null || !plugin.getCorePrestigeManager().canPrestige(region)) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes prestigiar este núcleo."));
            return;
        }
        int nextPrestige = region.getPrestige() + 1;
        double cost = plugin.getCorePrestigeManager().getPrestigeCost(region.getPrestige());
        player.closeInventory();
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§c§l⚠ ═══════════════════════════════ ⚠"));
        player.sendMessage(SmallCaps.convert("§c§l  ¡¡ATENCIÓN!! PRESTIGIAR NÚCLEO"));
        player.sendMessage(SmallCaps.convert("§c§l⚠ ═══════════════════════════════ ⚠"));
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§7  Prestigio actual: " + CorePrestigeManager.getPrestigeName(region.getPrestige())));
        player.sendMessage(SmallCaps.convert("§7  Nuevo prestigio:  " + CorePrestigeManager.getPrestigeName(nextPrestige)));
        player.sendMessage(SmallCaps.convert("§7  Coste: §6$" + String.format("%,.0f", cost)));
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§c§l  ⚠ TU NÚCLEO VOLVERÁ A NIVEL 1 ⚠"));
        player.sendMessage(SmallCaps.convert("§c  Perderás TODO el progreso de nivel."));
        player.sendMessage(SmallCaps.convert("§c  El tamaño de zona se reducirá al del nivel 1."));
        player.sendMessage(SmallCaps.convert("§c  Las mejoras compradas se MANTIENEN."));
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§7  Bonus: " + CorePrestigeManager.getPrestigeBonus(nextPrestige)));
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§a  Escribe §a§l'confirmar' §apara prestigiar."));
        player.sendMessage(SmallCaps.convert("§7  Escribe §c'cancelar' §7para cancelar."));
        player.sendMessage("");
        pendingChatInput.put(player.getUniqueId(), "prestige");
    }

    private void processUpgradeCore(Player player, ProtectedRegion region) {
        if (region.getLevel() >= 30) {
            plugin.getMessageManager().send(player, "upgrades.max-level");
            return;
        }

        if (!region.getOwner().equals(player.getUniqueId())) {
            plugin.getMessageManager().send(player, "protection.not-owner");
            return;
        }

        int nextLevel = region.getLevel() + 1;
        CoreLevel nextCoreLevel = CoreLevel.fromConfig(plugin.getConfig(), nextLevel);
        CoreLevel currentCoreLevel = CoreLevel.fromConfig(plugin.getConfig(), region.getLevel());
        if (nextCoreLevel == null) {
            plugin.getMessageManager().send(player, "upgrades.max-level");
            return;
        }

        // Overlap check: verify the new larger area doesn't overlap another protection
        if (!plugin.getProtectionManager().canUpgradeCore(region, nextCoreLevel.getSize())) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes mejorar: la nueva área solaparía otra protección o el spawn."));
            player.sendMessage(SmallCaps.convert("§7Necesitas más espacio libre alrededor de tu núcleo."));
            SoundManager.playUpgradeDenied(player.getLocation());
            player.closeInventory();
            return;
        }

        // Prestige requirement check
        if (nextCoreLevel.requiresPrestige() && region.getPrestige() < nextCoreLevel.getPrestigeRequired()) {
            player.sendMessage(ChatColor.RED + "Necesitas " + ChatColor.DARK_RED + ChatColor.BOLD +
                    "Prestigio Core " + nextCoreLevel.getPrestigeRomanNumeral() + ChatColor.RED + " para mejorar a este núcleo.");
            player.sendMessage(com.moonlight.coreprotect.util.SmallCaps.convert(ChatColor.GRAY + "Alcanza nivel 20 y usa el botón de prestigio."));
            SoundManager.playUpgradeDenied(player.getLocation());
            player.closeInventory();
            return;
        }

        // Global prestige requirement check (/prestige)
        if (nextCoreLevel.requiresGlobalPrestige()) {
            int playerGlobalPrestige = 0;
            if (plugin.getPrestigeManager() != null) {
                playerGlobalPrestige = plugin.getPrestigeManager().getPrestige(player.getUniqueId());
            }
            if (playerGlobalPrestige < nextCoreLevel.getGlobalPrestigeRequired()) {
                player.sendMessage(ChatColor.LIGHT_PURPLE + "Necesitas " +
                        nextCoreLevel.getGlobalPrestigeGlyph() + ChatColor.LIGHT_PURPLE + " para mejorar a este núcleo.");
                player.sendMessage(SmallCaps.convert(ChatColor.GRAY + "Usa /prestige para subir de prestigio."));
                SoundManager.playUpgradeDenied(player.getLocation());
                player.closeInventory();
                return;
            }
        }

        // VIP permission check for upgrading to VIP cores
        if (nextCoreLevel.isVip() && !player.hasPermission(nextCoreLevel.getVipPermission())) {
            player.sendMessage(ChatColor.RED + "Necesitas el rango " + ChatColor.LIGHT_PURPLE + ChatColor.BOLD +
                    nextCoreLevel.getVipRank().toUpperCase() + ChatColor.RED + " para mejorar a este nucleo.");
            player.sendMessage(SmallCaps.convert(ChatColor.GRAY + "Consiguelo en: " + ChatColor.YELLOW + "moonlightmc.tebex.io"));
            SoundManager.playUpgradeDenied(player.getLocation());
            player.closeInventory();
            return;
        }
        Economy economy = plugin.getEconomy();

        double upgradeCost = nextCoreLevel.getPrice() - currentCoreLevel.getPrice();
        if (upgradeCost < 0) upgradeCost = 0;

        if (!economy.has(player, upgradeCost)) {
            plugin.getMessageManager().send(player, "shop.no-money",
                    "{price}", String.valueOf((int) upgradeCost));
            player.closeInventory();
            return;
        }

        // Withdraw money (difference only)
        economy.withdrawPlayer(player, upgradeCost);
        player.closeInventory();

        // Update region data IMMEDIATELY (don't wait for animation)
        Location coreLoc = region.getCoreLocation();
        region.setLevel(nextLevel);
        region.setSize(nextCoreLevel.getSize());
        plugin.getDataManager().saveData();

        // Update BlueMap marker with new size
        if (plugin.getBlueMapIntegration() != null) {
            plugin.getBlueMapIntegration().updateAllMarkers();
        }

        // Achievement trigger
        plugin.getAchievementListener().onCoreUpgraded(player, region);

        // Lock the core location during animation
        plugin.getProtectionManager().lockLocation(coreLoc);

        plugin.getMessageManager().send(player, "upgrades.upgrading");

        // Play upgrade animation (VIP or standard)
        Runnable onAnimComplete = () -> {
            plugin.getProtectionManager().unlockLocation(coreLoc);
            if (player.isOnline()) {
                plugin.getMessageManager().send(player, "upgrades.upgraded",
                        "{level}", String.valueOf(nextLevel),
                        "{core}", nextCoreLevel.getName());
            }
        };

        if (nextCoreLevel.isVip()) {
            VipAnimation vipAnim = new VipAnimation(plugin);
            vipAnim.playVipUpgrade(coreLoc, currentCoreLevel.getMaterial(),
                    nextCoreLevel.getMaterial(), nextCoreLevel.getVipRank(), onAnimComplete);
        } else {
            CoreAnimation animation = new CoreAnimation(plugin);
            animation.playUpgradeAnimation(coreLoc, currentCoreLevel.getMaterial(),
                    nextCoreLevel.getMaterial(), onAnimComplete);
        }
    }

    private void toggleVisuals(Player player, ProtectedRegion region) {
        UUID regionId = region.getId();
        if (plugin.getProtectionManager().isVisualActive(regionId)) {
            Integer taskId = plugin.getProtectionManager().getVisualTask(regionId);
            if (taskId != null) {
                Bukkit.getScheduler().cancelTask(taskId);
            }
            plugin.getProtectionManager().removeVisualTask(regionId);
            plugin.getMessageManager().send(player, "upgrades.visuals-off");
        } else {
            int id = new CoreAnimation(plugin)
                    .playRegionDisplay(region.getCoreLocation(), region.getSize());
            plugin.getProtectionManager().addVisualTask(regionId, id);
            plugin.getMessageManager().send(player, "upgrades.visuals-on");
        }
    }

    private boolean tryPurchase(Player player, double price) {
        Economy economy = plugin.getEconomy();
        if (!economy.has(player, price)) {
            plugin.getMessageManager().send(player, "shop.no-money",
                    "{price}", String.valueOf((int) price));
            SoundManager.playUpgradeDenied(player.getLocation());
            return false;
        }
        economy.withdrawPlayer(player, price);
        return true;
    }

    private ProtectedRegion getPlayerManagedRegion(Player player) {
        UUID regionId = playerRegionMap.get(player.getUniqueId());
        if (regionId == null) return null;
        return plugin.getProtectionManager().getRegion(regionId);
    }

    private boolean isGlassPane(Material material) {
        return material.name().contains("STAINED_GLASS_PANE") || 
               material.name().contains("GLASS_PANE") ||
               material == Material.BLACK_STAINED_GLASS ||
               material == Material.GRAY_STAINED_GLASS ||
               material == Material.WHITE_STAINED_GLASS ||
               material == Material.LIGHT_GRAY_STAINED_GLASS ||
               material == Material.CYAN_STAINED_GLASS ||
               material == Material.PURPLE_STAINED_GLASS ||
               material == Material.RED_STAINED_GLASS ||
               material == Material.LIME_STAINED_GLASS;
    }

    private int getLevelFromSlot(int slot, int page) {
        int[] validSlots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        int itemsPerPage = validSlots.length;

        for (int i = 0; i < validSlots.length; i++) {
            if (validSlots[i] == slot) {
                int level = ((page - 1) * itemsPerPage) + 1 + i;
                return level;
            }
        }
        return -1;
    }

    private void processCorePurchase(Player player, CoreLevel coreLevel) {
        // Prestige requirement check (for shop purchase)
        if (coreLevel.requiresPrestige()) {
            // Check if the player has a region with enough prestige
            java.util.List<ProtectedRegion> owned = plugin.getProtectionManager().getRegionsByOwner(player.getUniqueId());
            ProtectedRegion existingRegion = owned.isEmpty() ? null : owned.get(0);
            int playerPrestige = existingRegion != null ? existingRegion.getPrestige() : 0;
            if (playerPrestige < coreLevel.getPrestigeRequired()) {
                player.sendMessage(ChatColor.RED + "Necesitas " + ChatColor.DARK_RED + ChatColor.BOLD +
                        "Prestigio Core " + coreLevel.getPrestigeRomanNumeral() + ChatColor.RED + " para comprar este núcleo.");
                player.sendMessage(SmallCaps.convert(ChatColor.GRAY + "Alcanza nivel 20 y usa el botón de prestigio."));
                SoundManager.playUpgradeDenied(player.getLocation());
                player.closeInventory();
                return;
            }
        }

        // Global prestige requirement check (/prestige) for shop purchase
        if (coreLevel.requiresGlobalPrestige()) {
            int playerGlobalPrestige = 0;
            if (plugin.getPrestigeManager() != null) {
                playerGlobalPrestige = plugin.getPrestigeManager().getPrestige(player.getUniqueId());
            }
            if (playerGlobalPrestige < coreLevel.getGlobalPrestigeRequired()) {
                player.sendMessage(ChatColor.LIGHT_PURPLE + "Necesitas " +
                        coreLevel.getGlobalPrestigeGlyph() + ChatColor.LIGHT_PURPLE + " para comprar este núcleo.");
                player.sendMessage(SmallCaps.convert(ChatColor.GRAY + "Usa /prestige para subir de prestigio."));
                SoundManager.playUpgradeDenied(player.getLocation());
                player.closeInventory();
                return;
            }
        }

        // Core limit warning (informational — actual enforcement is at placement)
        if (plugin.getCoreMaintenanceManager() != null && !player.hasPermission("coreprotect.admin")) {
            int max = plugin.getCoreMaintenanceManager().getMaxCores(player);
            int current = plugin.getCoreMaintenanceManager().getCoreCount(player.getUniqueId());
            if (current >= max) {
                player.sendMessage(SmallCaps.convert(ChatColor.YELLOW + "⚠ " + ChatColor.GRAY + "Tienes " +
                        ChatColor.YELLOW + current + "/" + max + ChatColor.GRAY + " cores. No podrás colocar más hasta eliminar uno."));
            }
        }

        // VIP permission check
        if (coreLevel.isVip() && !player.hasPermission(coreLevel.getVipPermission())) {
            player.sendMessage(ChatColor.RED + "Necesitas el rango " + ChatColor.LIGHT_PURPLE + ChatColor.BOLD +
                    coreLevel.getVipRank().toUpperCase() + ChatColor.RED + " para comprar este nucleo.");
            player.sendMessage(SmallCaps.convert(ChatColor.GRAY + "Consiguelo en: " + ChatColor.YELLOW + "moonlightmc.tebex.io"));
            SoundManager.playUpgradeDenied(player.getLocation());
            player.closeInventory();
            return;
        }

        Economy economy = plugin.getEconomy();

        if (!economy.has(player, coreLevel.getPrice())) {
            plugin.getMessageManager().send(player, "shop.no-money",
                    "{price}", String.valueOf((int) coreLevel.getPrice()));
            player.closeInventory();
            return;
        }

        if (player.getInventory().firstEmpty() == -1) {
            plugin.getMessageManager().send(player, "shop.inventory-full");
            player.closeInventory();
            return;
        }

        economy.withdrawPlayer(player, coreLevel.getPrice());

        ItemStack coreItem = coreLevel.toItemStack();
        player.getInventory().addItem(coreItem);

        plugin.getMessageManager().send(player, "shop.purchased",
                "{core}", coreLevel.getName(),
                "{price}", String.valueOf((int) coreLevel.getPrice()));

        player.closeInventory();
    }
}
