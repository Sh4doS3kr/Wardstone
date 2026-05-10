package com.moonlight.coreprotect.gui;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.core.CoreLevel;
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
            CoreUpgradesShopGUI.isUpgradesShopGUI(title) ||
            CoreMembersGUI.isMembersGUI(title) ||
            CoreMembersGUI.isInviteGUI(title) ||
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
        if (level < 1 || level > 24) return;

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

        // Slot 20: UPGRADE CORE
        if (slot == 20 && clicked.getType() == Material.BEACON) {
            SoundManager.playGUIClick(player.getLocation());
            processUpgradeCore(player, region);
            return;
        }

        // Slot 20: PRESTIGE CORE (Dragon Egg)
        if (slot == 20 && clicked.getType() == Material.DRAGON_EGG) {
            SoundManager.playGUIClick(player.getLocation());
            player.closeInventory();
            if (plugin.getCorePrestigeManager() != null) {
                plugin.getCorePrestigeManager().performPrestige(player, region);
            }
            return;
        }

        // Slot 22: MEMBERS
        if (slot == 22) {
            SoundManager.playGUIClick(player.getLocation());
            new CoreMembersGUI(plugin).open(player, region);
            return;
        }

        // Slot 24: UPGRADES SHOP
        if (slot == 24 && clicked.getType() == Material.NETHER_STAR) {
            SoundManager.playGUIClick(player.getLocation());
            new CoreUpgradesShopGUI(plugin).open(player, region);
            return;
        }

        // Slot 47: VISUALS TOGGLE
        if (slot == 47) {
            SoundManager.playGUIClick(player.getLocation());
            toggleVisuals(player, region);
            new CoreManagementGUI(plugin).open(player, region);
            return;
        }

        // Slot 51: CLOSE
        if (slot == 51) {
            player.closeInventory();
            return;
        }
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

        if (!region.getOwner().equals(player.getUniqueId())) {
            plugin.getMessageManager().send(player, "protection.not-owner");
            player.closeInventory();
            return;
        }

        int slot = event.getSlot();
        String title = event.getView().getTitle();
        int page = CoreUpgradesShopGUI.getPageFromTitle(title);

        // Page navigation
        if (slot == 50 && page == 1 && clicked.getType() == Material.ARROW) {
            SoundManager.playGUIClick(player.getLocation());
            new CoreUpgradesShopGUI(plugin).open(player, region, 2);
            return;
        }
        if (slot == 48 && page == 2 && clicked.getType() == Material.ARROW) {
            SoundManager.playGUIClick(player.getLocation());
            new CoreUpgradesShopGUI(plugin).open(player, region, 1);
            return;
        }

        // Back to management
        if (slot == 49) {
            if (clicked.getType() == Material.ARROW || clicked.getType() == Material.BARRIER) {
                SoundManager.playGUIClick(player.getLocation());
                new CoreManagementGUI(plugin).open(player, region);
                return;
            }
        }

        boolean shift = event.isShiftClick();

        // === PAGE 1 UPGRADES ===
        if (page == 1) {
            handlePage1Upgrades(slot, player, region, shift);
        }
        // === PAGE 2 UPGRADES ===
        else if (page == 2) {
            handlePage2Upgrades(slot, player, region, shift);
        }
    }

    private void handlePage1Upgrades(int slot, Player player, ProtectedRegion region, boolean shift) {

        // Slot 10: Anti-Explosion
        if (slot == 10) {
            if (shift && region.isNoExplosion()) {
                region.setNoExplosion(false);
                plugin.getDataManager().saveData();
                player.sendMessage(SmallCaps.convert(ChatColor.YELLOW + "Anti-Explosión " + ChatColor.RED + "desactivado."));
                SoundManager.playGUIClick(player.getLocation());
                new CoreUpgradesShopGUI(plugin).open(player, region, 1);
                return;
            }
        }
        if (slot == 10 && !region.isNoExplosion()) {
            if (region.isUnlocked("noExplosion")) {
                region.setNoExplosion(true);
                plugin.getDataManager().saveData();
                player.sendMessage(SmallCaps.convert(ChatColor.GREEN + "Anti-Explosión reactivado."));
                SoundManager.playUpgradePurchased(player.getLocation());
                new CoreUpgradesShopGUI(plugin).open(player, region, 1);
            } else if (tryPurchase(player, CoreUpgradesShopGUI.PRICE_NO_EXPLOSION)) {
                region.setNoExplosion(true);
                region.unlockUpgrade("noExplosion");
                plugin.getDataManager().saveData();
                plugin.getMessageManager().send(player, "upgrades.purchased", "{upgrade}", "Anti-Explosión");
                SoundManager.playUpgradePurchased(player.getLocation());
                plugin.getAchievementListener().onUpgradePurchased(player, region, "noExplosion", CoreUpgradesShopGUI.PRICE_NO_EXPLOSION);
                new CoreUpgradesShopGUI(plugin).open(player, region);
            }
            return;
        }
        // Slot 12: Anti-PvP — siempre activo, no interactuable
        if (slot == 12) {
            player.sendMessage(SmallCaps.convert(ChatColor.GREEN + "✔ " + ChatColor.GRAY + "El PvP está siempre desactivado en protecciones."));
            SoundManager.playGUIClick(player.getLocation());
            return;
        }
        if (slot == 14) {
            if (shift && region.isNoMobSpawn()) {
                region.setNoMobSpawn(false);
                plugin.getDataManager().saveData();
                player.sendMessage(SmallCaps.convert(ChatColor.YELLOW + "Anti-Mobs " + ChatColor.RED + "desactivado."));
                SoundManager.playGUIClick(player.getLocation());
                new CoreUpgradesShopGUI(plugin).open(player, region, 1);
                return;
            }
        }
        if (slot == 14 && !region.isNoMobSpawn()) {
            if (region.isUnlocked("noMobSpawn")) {
                region.setNoMobSpawn(true);
                plugin.getDataManager().saveData();
                player.sendMessage(SmallCaps.convert(ChatColor.GREEN + "Anti-Mobs reactivado."));
                SoundManager.playUpgradePurchased(player.getLocation());
                new CoreUpgradesShopGUI(plugin).open(player, region, 1);
            } else if (tryPurchase(player, CoreUpgradesShopGUI.PRICE_NO_MOB_SPAWN)) {
                region.setNoMobSpawn(true);
                region.unlockUpgrade("noMobSpawn");
                plugin.getDataManager().saveData();
                plugin.getMessageManager().send(player, "upgrades.purchased", "{upgrade}", "Anti-Mobs");
                SoundManager.playUpgradePurchased(player.getLocation());
                plugin.getAchievementListener().onUpgradePurchased(player, region, "noMobSpawn", CoreUpgradesShopGUI.PRICE_NO_MOB_SPAWN);
                new CoreUpgradesShopGUI(plugin).open(player, region);
            }
            return;
        }
        if (slot == 16) {
            if (shift && region.isNoFallDamage()) {
                region.setNoFallDamage(false);
                plugin.getDataManager().saveData();
                player.sendMessage(SmallCaps.convert(ChatColor.YELLOW + "Sin Caída " + ChatColor.RED + "desactivado."));
                SoundManager.playGUIClick(player.getLocation());
                new CoreUpgradesShopGUI(plugin).open(player, region, 1);
                return;
            }
        }
        if (slot == 16 && !region.isNoFallDamage()) {
            if (region.isUnlocked("noFallDamage")) {
                region.setNoFallDamage(true);
                plugin.getDataManager().saveData();
                player.sendMessage(SmallCaps.convert(ChatColor.GREEN + "Sin Caída reactivado."));
                SoundManager.playUpgradePurchased(player.getLocation());
                new CoreUpgradesShopGUI(plugin).open(player, region, 1);
            } else if (tryPurchase(player, CoreUpgradesShopGUI.PRICE_NO_FALL_DAMAGE)) {
                region.setNoFallDamage(true);
                region.unlockUpgrade("noFallDamage");
                plugin.getDataManager().saveData();
                plugin.getMessageManager().send(player, "upgrades.purchased", "{upgrade}", "Sin Caída");
                SoundManager.playUpgradePurchased(player.getLocation());
                plugin.getAchievementListener().onUpgradePurchased(player, region, "noFallDamage", CoreUpgradesShopGUI.PRICE_NO_FALL_DAMAGE);
                new CoreUpgradesShopGUI(plugin).open(player, region);
            }
            return;
        }
        if (slot == 19) {
            if (shift && region.isAutoHeal()) {
                region.setAutoHeal(false);
                plugin.getDataManager().saveData();
                player.sendMessage(SmallCaps.convert(ChatColor.YELLOW + "Auto-Curación " + ChatColor.RED + "desactivado."));
                SoundManager.playGUIClick(player.getLocation());
                new CoreUpgradesShopGUI(plugin).open(player, region, 1);
                return;
            }
        }
        if (slot == 19 && !region.isAutoHeal()) {
            if (region.isUnlocked("autoHeal")) {
                region.setAutoHeal(true);
                plugin.getDataManager().saveData();
                player.sendMessage(SmallCaps.convert(ChatColor.GREEN + "Auto-Curación reactivado."));
                SoundManager.playUpgradePurchased(player.getLocation());
                new CoreUpgradesShopGUI(plugin).open(player, region, 1);
            } else if (tryPurchase(player, CoreUpgradesShopGUI.PRICE_AUTO_HEAL)) {
                region.setAutoHeal(true);
                region.unlockUpgrade("autoHeal");
                plugin.getDataManager().saveData();
                plugin.getMessageManager().send(player, "upgrades.purchased", "{upgrade}", "Auto-Curación");
                SoundManager.playUpgradePurchased(player.getLocation());
                plugin.getAchievementListener().onUpgradePurchased(player, region, "autoHeal", CoreUpgradesShopGUI.PRICE_AUTO_HEAL);
                new CoreUpgradesShopGUI(plugin).open(player, region);
            }
            return;
        }
        if (slot == 21) {
            if (shift && region.isSpeedBoost()) {
                region.setSpeedBoost(false);
                plugin.getDataManager().saveData();
                player.sendMessage(SmallCaps.convert(ChatColor.YELLOW + "Velocidad " + ChatColor.RED + "desactivado."));
                SoundManager.playGUIClick(player.getLocation());
                new CoreUpgradesShopGUI(plugin).open(player, region, 1);
                return;
            }
        }
        if (slot == 21 && !region.isSpeedBoost()) {
            if (region.isUnlocked("speedBoost")) {
                region.setSpeedBoost(true);
                plugin.getDataManager().saveData();
                player.sendMessage(SmallCaps.convert(ChatColor.GREEN + "Velocidad reactivado."));
                SoundManager.playUpgradePurchased(player.getLocation());
                new CoreUpgradesShopGUI(plugin).open(player, region, 1);
            } else if (tryPurchase(player, CoreUpgradesShopGUI.PRICE_SPEED_BOOST)) {
                region.setSpeedBoost(true);
                region.unlockUpgrade("speedBoost");
                plugin.getDataManager().saveData();
                plugin.getMessageManager().send(player, "upgrades.purchased", "{upgrade}", "Velocidad");
                SoundManager.playUpgradePurchased(player.getLocation());
                plugin.getAchievementListener().onUpgradePurchased(player, region, "speedBoost", CoreUpgradesShopGUI.PRICE_SPEED_BOOST);
                new CoreUpgradesShopGUI(plugin).open(player, region);
            }
            return;
        }
        if (slot == 23 && region.getDamageBoostLevel() < 5) {
            double price = CoreUpgradesShopGUI.PRICE_DAMAGE_BOOST * (region.getDamageBoostLevel() + 1);
            if (tryPurchase(player, price)) {
                region.setDamageBoostLevel(region.getDamageBoostLevel() + 1);
                plugin.getDataManager().saveData();
                plugin.getMessageManager().send(player, "upgrades.leveled",
                        "{upgrade}", "Boost de Daño", "{level}", String.valueOf(region.getDamageBoostLevel()));
                SoundManager.playUpgradePurchased(player.getLocation());
                plugin.getAchievementListener().onUpgradePurchased(player, region, "damageBoost", price);
                new CoreUpgradesShopGUI(plugin).open(player, region);
            }
            return;
        }
        if (slot == 25 && region.getHealthBoostLevel() < 5) {
            double price = CoreUpgradesShopGUI.PRICE_HEALTH_BOOST * (region.getHealthBoostLevel() + 1);
            if (tryPurchase(player, price)) {
                region.setHealthBoostLevel(region.getHealthBoostLevel() + 1);
                plugin.getDataManager().saveData();
                plugin.getMessageManager().send(player, "upgrades.leveled",
                        "{upgrade}", "Boost de Vida", "{level}", String.valueOf(region.getHealthBoostLevel()));
                SoundManager.playUpgradePurchased(player.getLocation());
                plugin.getAchievementListener().onUpgradePurchased(player, region, "healthBoost", price);
                new CoreUpgradesShopGUI(plugin).open(player, region);
            }
            return;
        }
    }

    private void handlePage2Upgrades(int slot, Player player, ProtectedRegion region, boolean shift) {

        if (slot == 10) {
            if (shift && region.isAntiEnderman()) {
                region.setAntiEnderman(false);
                plugin.getDataManager().saveData();
                player.sendMessage(SmallCaps.convert(ChatColor.YELLOW + "Anti-Enderman " + ChatColor.RED + "desactivado."));
                SoundManager.playGUIClick(player.getLocation());
                new CoreUpgradesShopGUI(plugin).open(player, region, 2);
                return;
            }
        }
        if (slot == 10 && !region.isAntiEnderman()) {
            if (region.isUnlocked("antiEnderman")) {
                region.setAntiEnderman(true);
                plugin.getDataManager().saveData();
                player.sendMessage(SmallCaps.convert(ChatColor.GREEN + "Anti-Enderman reactivado."));
                SoundManager.playUpgradePurchased(player.getLocation());
                new CoreUpgradesShopGUI(plugin).open(player, region, 2);
            } else if (tryPurchase(player, CoreUpgradesShopGUI.PRICE_ANTI_ENDERMAN)) {
                region.setAntiEnderman(true);
                region.unlockUpgrade("antiEnderman");
                plugin.getDataManager().saveData();
                plugin.getMessageManager().send(player, "upgrades.purchased", "{upgrade}", "Anti-Enderman");
                SoundManager.playUpgradePurchased(player.getLocation());
                plugin.getAchievementListener().onUpgradePurchased(player, region, "antiEnderman", CoreUpgradesShopGUI.PRICE_ANTI_ENDERMAN);
                new CoreUpgradesShopGUI(plugin).open(player, region, 2);
            }
            return;
        }
        if (slot == 12) {
            if (shift && region.isNoHunger()) {
                region.setNoHunger(false);
                plugin.getDataManager().saveData();
                player.sendMessage(SmallCaps.convert(ChatColor.YELLOW + "Sin Hambre " + ChatColor.RED + "desactivado."));
                SoundManager.playGUIClick(player.getLocation());
                new CoreUpgradesShopGUI(plugin).open(player, region, 2);
                return;
            }
        }
        if (slot == 12 && !region.isNoHunger()) {
            if (region.isUnlocked("noHunger")) {
                region.setNoHunger(true);
                plugin.getDataManager().saveData();
                player.sendMessage(SmallCaps.convert(ChatColor.GREEN + "Sin Hambre reactivado."));
                SoundManager.playUpgradePurchased(player.getLocation());
                new CoreUpgradesShopGUI(plugin).open(player, region, 2);
            } else if (tryPurchase(player, CoreUpgradesShopGUI.PRICE_NO_HUNGER)) {
                region.setNoHunger(true);
                region.unlockUpgrade("noHunger");
                plugin.getDataManager().saveData();
                plugin.getMessageManager().send(player, "upgrades.purchased", "{upgrade}", "Sin Hambre");
                SoundManager.playUpgradePurchased(player.getLocation());
                plugin.getAchievementListener().onUpgradePurchased(player, region, "noHunger", CoreUpgradesShopGUI.PRICE_NO_HUNGER);
                new CoreUpgradesShopGUI(plugin).open(player, region, 2);
            }
            return;
        }
        if (slot == 14) {
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
        if (slot == 16) {
            if (shift && region.isCoreTeleport()) {
                region.setCoreTeleport(false);
                plugin.getDataManager().saveData();
                player.sendMessage(SmallCaps.convert(ChatColor.YELLOW + "Teletransporte " + ChatColor.RED + "desactivado."));
                SoundManager.playGUIClick(player.getLocation());
                new CoreUpgradesShopGUI(plugin).open(player, region, 2);
                return;
            }
        }
        if (slot == 16 && !region.isCoreTeleport()) {
            if (region.isUnlocked("coreTeleport")) {
                region.setCoreTeleport(true);
                plugin.getDataManager().saveData();
                player.sendMessage(SmallCaps.convert(ChatColor.GREEN + "Teletransporte reactivado."));
                SoundManager.playUpgradePurchased(player.getLocation());
                new CoreUpgradesShopGUI(plugin).open(player, region, 2);
            } else if (tryPurchase(player, CoreUpgradesShopGUI.PRICE_CORE_TELEPORT)) {
                region.setCoreTeleport(true);
                region.unlockUpgrade("coreTeleport");
                plugin.getDataManager().saveData();
                plugin.getMessageManager().send(player, "upgrades.purchased", "{upgrade}", "Teletransporte");
                SoundManager.playUpgradePurchased(player.getLocation());
                plugin.getAchievementListener().onUpgradePurchased(player, region, "coreTeleport", CoreUpgradesShopGUI.PRICE_CORE_TELEPORT);
                new CoreUpgradesShopGUI(plugin).open(player, region, 2);
            }
            return;
        }
        if (slot == 19) {
            if (shift && region.isAntiPhantom()) {
                region.setAntiPhantom(false);
                plugin.getDataManager().saveData();
                player.sendMessage(SmallCaps.convert(ChatColor.YELLOW + "Anti-Phantoms " + ChatColor.RED + "desactivado."));
                SoundManager.playGUIClick(player.getLocation());
                new CoreUpgradesShopGUI(plugin).open(player, region, 2);
                return;
            }
        }
        if (slot == 19 && !region.isAntiPhantom()) {
            if (region.isUnlocked("antiPhantom")) {
                region.setAntiPhantom(true);
                plugin.getDataManager().saveData();
                player.sendMessage(SmallCaps.convert(ChatColor.GREEN + "Anti-Phantoms reactivado."));
                SoundManager.playUpgradePurchased(player.getLocation());
                new CoreUpgradesShopGUI(plugin).open(player, region, 2);
            } else if (tryPurchase(player, CoreUpgradesShopGUI.PRICE_ANTI_PHANTOM)) {
                region.setAntiPhantom(true);
                region.unlockUpgrade("antiPhantom");
                plugin.getDataManager().saveData();
                plugin.getMessageManager().send(player, "upgrades.purchased", "{upgrade}", "Anti-Phantoms");
                SoundManager.playUpgradePurchased(player.getLocation());
                plugin.getAchievementListener().onUpgradePurchased(player, region, "antiPhantom", CoreUpgradesShopGUI.PRICE_ANTI_PHANTOM);
                new CoreUpgradesShopGUI(plugin).open(player, region, 2);
            }
            return;
        }
        if (slot == 22) {
            if (region.isUnlocked("resourceGenerator")) {
                // Ya comprado: toggle on/off sin cobrar
                if (region.isResourceGenerator()) {
                    region.setResourceGenerator(false);
                    plugin.getDataManager().saveData();
                    player.sendMessage(SmallCaps.convert(ChatColor.YELLOW + "Generador de Recursos " + ChatColor.RED + "desactivado."));
                    SoundManager.playGUIClick(player.getLocation());
                } else {
                    region.setResourceGenerator(true);
                    plugin.getDataManager().saveData();
                    player.sendMessage(SmallCaps.convert(ChatColor.GREEN + "Generador de Recursos " + ChatColor.GREEN + "reactivado."));
                    SoundManager.playUpgradePurchased(player.getLocation());
                }
                new CoreUpgradesShopGUI(plugin).open(player, region, 2);
            } else if (tryPurchase(player, CoreUpgradesShopGUI.PRICE_RESOURCE_GEN)) {
                // Primera compra
                region.setResourceGenerator(true);
                region.unlockUpgrade("resourceGenerator");
                plugin.getDataManager().saveData();
                plugin.getMessageManager().send(player, "upgrades.purchased", "{upgrade}", "Generador de Recursos");
                SoundManager.playUpgradePurchased(player.getLocation());
                plugin.getAchievementListener().onUpgradePurchased(player, region, "resourceGen", CoreUpgradesShopGUI.PRICE_RESOURCE_GEN);
                new CoreUpgradesShopGUI(plugin).open(player, region, 2);
            }
            return;
        }
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

    private void processUpgradeCore(Player player, ProtectedRegion region) {
        if (region.getLevel() >= 24) {
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
               material == Material.LIME_STAINED_GLASS ||
               material == Material.BARRIER; // Also protect close buttons from being taken
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
