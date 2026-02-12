package com.moonlight.coreprotect.gui;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.core.CoreLevel;
import com.moonlight.coreprotect.core.ProtectedRegion;
import com.moonlight.coreprotect.effects.CoreAnimation;
import com.moonlight.coreprotect.effects.SoundManager;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class GUIListener implements Listener {

    private final CoreProtectPlugin plugin;
    private final NamespacedKey coreKey;

    // Track which region a player is managing (regionId by player UUID)
    private static final Map<UUID, UUID> playerRegionMap = new HashMap<>();

    public GUIListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        this.coreKey = new NamespacedKey(plugin, "core_level");
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
            handleShopClick(event, player, title);
            return;
        }

        // === MANAGEMENT GUI ===
        if (CoreManagementGUI.isManagementGUI(title)) {
            handleManagementClick(event, player);
            return;
        }

        // === UPGRADES SHOP GUI ===
        if (CoreUpgradesShopGUI.isUpgradesShopGUI(title)) {
            handleUpgradesShopClick(event, player);
            return;
        }

        // === MEMBERS GUI ===
        if (CoreMembersGUI.isMembersGUI(title)) {
            handleMembersClick(event, player);
            return;
        }

        // === INVITE GUI ===
        if (CoreMembersGUI.isInviteGUI(title)) {
            handleInviteClick(event, player);
            return;
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
        if (event.getSlot() == 50 && currentPage < 2) {
            new ShopGUI(plugin).open(player, currentPage + 1);
            return;
        }
        if (clicked.getType() == Material.BOOK || clicked.getType() == Material.ARROW) return;

        int level = getLevelFromSlot(event.getSlot(), currentPage);
        if (level < 1 || level > 20) return;

        CoreLevel coreLevel = CoreLevel.fromConfig(plugin.getConfig(), level);
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

        // === PAGE 1 UPGRADES ===
        if (page == 1) {
            handlePage1Upgrades(slot, player, region);
        }
        // === PAGE 2 UPGRADES ===
        else if (page == 2) {
            handlePage2Upgrades(slot, player, region);
        }
    }

    private void handlePage1Upgrades(int slot, Player player, ProtectedRegion region) {
        // Slot 10: Anti-Explosion
        if (slot == 10 && !region.isNoExplosion()) {
            if (tryPurchase(player, CoreUpgradesShopGUI.PRICE_NO_EXPLOSION)) {
                region.setNoExplosion(true);
                plugin.getDataManager().saveData();
                plugin.getMessageManager().send(player, "upgrades.purchased", "{upgrade}", "Anti-Explosión");
                SoundManager.playUpgradePurchased(player.getLocation());
                plugin.getAchievementListener().onUpgradePurchased(player, region, "noExplosion", CoreUpgradesShopGUI.PRICE_NO_EXPLOSION);
                new CoreUpgradesShopGUI(plugin).open(player, region);
            }
            return;
        }
        if (slot == 12 && !region.isNoPvP()) {
            if (tryPurchase(player, CoreUpgradesShopGUI.PRICE_NO_PVP)) {
                region.setNoPvP(true);
                plugin.getDataManager().saveData();
                plugin.getMessageManager().send(player, "upgrades.purchased", "{upgrade}", "Anti-PvP");
                SoundManager.playUpgradePurchased(player.getLocation());
                plugin.getAchievementListener().onUpgradePurchased(player, region, "noPvP", CoreUpgradesShopGUI.PRICE_NO_PVP);
                new CoreUpgradesShopGUI(plugin).open(player, region);
            }
            return;
        }
        if (slot == 14 && !region.isNoMobSpawn()) {
            if (tryPurchase(player, CoreUpgradesShopGUI.PRICE_NO_MOB_SPAWN)) {
                region.setNoMobSpawn(true);
                plugin.getDataManager().saveData();
                plugin.getMessageManager().send(player, "upgrades.purchased", "{upgrade}", "Anti-Mobs");
                SoundManager.playUpgradePurchased(player.getLocation());
                plugin.getAchievementListener().onUpgradePurchased(player, region, "noMobSpawn", CoreUpgradesShopGUI.PRICE_NO_MOB_SPAWN);
                new CoreUpgradesShopGUI(plugin).open(player, region);
            }
            return;
        }
        if (slot == 16 && !region.isNoFallDamage()) {
            if (tryPurchase(player, CoreUpgradesShopGUI.PRICE_NO_FALL_DAMAGE)) {
                region.setNoFallDamage(true);
                plugin.getDataManager().saveData();
                plugin.getMessageManager().send(player, "upgrades.purchased", "{upgrade}", "Sin Caída");
                SoundManager.playUpgradePurchased(player.getLocation());
                plugin.getAchievementListener().onUpgradePurchased(player, region, "noFallDamage", CoreUpgradesShopGUI.PRICE_NO_FALL_DAMAGE);
                new CoreUpgradesShopGUI(plugin).open(player, region);
            }
            return;
        }
        if (slot == 19 && !region.isAutoHeal()) {
            if (tryPurchase(player, CoreUpgradesShopGUI.PRICE_AUTO_HEAL)) {
                region.setAutoHeal(true);
                plugin.getDataManager().saveData();
                plugin.getMessageManager().send(player, "upgrades.purchased", "{upgrade}", "Auto-Curación");
                SoundManager.playUpgradePurchased(player.getLocation());
                plugin.getAchievementListener().onUpgradePurchased(player, region, "autoHeal", CoreUpgradesShopGUI.PRICE_AUTO_HEAL);
                new CoreUpgradesShopGUI(plugin).open(player, region);
            }
            return;
        }
        if (slot == 21 && !region.isSpeedBoost()) {
            if (tryPurchase(player, CoreUpgradesShopGUI.PRICE_SPEED_BOOST)) {
                region.setSpeedBoost(true);
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

    private void handlePage2Upgrades(int slot, Player player, ProtectedRegion region) {
        if (slot == 10 && !region.isAntiEnderman()) {
            if (tryPurchase(player, CoreUpgradesShopGUI.PRICE_ANTI_ENDERMAN)) {
                region.setAntiEnderman(true);
                plugin.getDataManager().saveData();
                plugin.getMessageManager().send(player, "upgrades.purchased", "{upgrade}", "Anti-Enderman");
                SoundManager.playUpgradePurchased(player.getLocation());
                plugin.getAchievementListener().onUpgradePurchased(player, region, "antiEnderman", CoreUpgradesShopGUI.PRICE_ANTI_ENDERMAN);
                new CoreUpgradesShopGUI(plugin).open(player, region, 2);
            }
            return;
        }
        if (slot == 12 && !region.isNoHunger()) {
            if (tryPurchase(player, CoreUpgradesShopGUI.PRICE_NO_HUNGER)) {
                region.setNoHunger(true);
                plugin.getDataManager().saveData();
                plugin.getMessageManager().send(player, "upgrades.purchased", "{upgrade}", "Sin Hambre");
                SoundManager.playUpgradePurchased(player.getLocation());
                plugin.getAchievementListener().onUpgradePurchased(player, region, "noHunger", CoreUpgradesShopGUI.PRICE_NO_HUNGER);
                new CoreUpgradesShopGUI(plugin).open(player, region, 2);
            }
            return;
        }
        if (slot == 14) {
            if (region.getFixedTime() == 0) {
                if (tryPurchase(player, CoreUpgradesShopGUI.PRICE_FIXED_TIME)) {
                    region.setFixedTime(1);
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
        if (slot == 16 && !region.isCoreTeleport()) {
            if (tryPurchase(player, CoreUpgradesShopGUI.PRICE_CORE_TELEPORT)) {
                region.setCoreTeleport(true);
                plugin.getDataManager().saveData();
                plugin.getMessageManager().send(player, "upgrades.purchased", "{upgrade}", "Teletransporte");
                SoundManager.playUpgradePurchased(player.getLocation());
                plugin.getAchievementListener().onUpgradePurchased(player, region, "coreTeleport", CoreUpgradesShopGUI.PRICE_CORE_TELEPORT);
                new CoreUpgradesShopGUI(plugin).open(player, region, 2);
            }
            return;
        }
        if (slot == 22 && !region.isResourceGenerator()) {
            if (tryPurchase(player, CoreUpgradesShopGUI.PRICE_RESOURCE_GEN)) {
                region.setResourceGenerator(true);
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
        if (region.getLevel() >= 20) {
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

        // Play epic 20s upgrade animation (purely visual)
        CoreAnimation animation = new CoreAnimation(plugin);
        animation.playUpgradeAnimation(
                coreLoc,
                currentCoreLevel.getMaterial(),
                nextCoreLevel.getMaterial(),
                () -> {
                    // Unlock location after animation
                    plugin.getProtectionManager().unlockLocation(coreLoc);

                    if (player.isOnline()) {
                        plugin.getMessageManager().send(player, "upgrades.upgraded",
                                "{level}", String.valueOf(nextLevel),
                                "{core}", nextCoreLevel.getName());
                    }
                });
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
        return material.name().contains("STAINED_GLASS_PANE");
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
