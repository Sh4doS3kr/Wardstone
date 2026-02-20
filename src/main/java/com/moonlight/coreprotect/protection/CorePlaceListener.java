package com.moonlight.coreprotect.protection;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.core.CoreLevel;
import com.moonlight.coreprotect.core.ProtectedRegion;
import com.moonlight.coreprotect.effects.CoreAnimation;
import com.moonlight.coreprotect.effects.SoundManager;
import com.moonlight.coreprotect.effects.VipAnimation;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class CorePlaceListener implements Listener {

    private final CoreProtectPlugin plugin;
    // Fixed namespace so items work regardless of plugin display name
    private static final NamespacedKey CORE_KEY = new NamespacedKey("coreprotect", "core_level");
    private static final NamespacedKey CORE_KEY_LEGACY = new NamespacedKey("wardstone", "core_level");
    private static final NamespacedKey UPGRADES_KEY = new NamespacedKey("coreprotect", "core_upgrades");
    private static final NamespacedKey UPGRADES_KEY_LEGACY = new NamespacedKey("wardstone", "core_upgrades");
    private static final NamespacedKey MEMBERS_KEY = new NamespacedKey("coreprotect", "core_members");
    private static final NamespacedKey MEMBERS_KEY_LEGACY = new NamespacedKey("wardstone", "core_members");

    public CorePlaceListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCorePlaced(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemInHand();

        if (!item.hasItemMeta()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        // Support both old (coreprotect:) and new (wardstone:) namespaces
        NamespacedKey activeKey = container.has(CORE_KEY, PersistentDataType.INTEGER) ? CORE_KEY
                : container.has(CORE_KEY_LEGACY, PersistentDataType.INTEGER) ? CORE_KEY_LEGACY : null;
        if (activeKey == null) {
            return;
        }

        int level = container.get(activeKey, PersistentDataType.INTEGER);
        CoreLevel coreLevel = CoreLevel.fromConfig(plugin.getConfig(), level);

        // VIP permission check
        if (coreLevel.isVip() && !player.hasPermission(coreLevel.getVipPermission())) {
            player.sendMessage(org.bukkit.ChatColor.RED + "Necesitas el rango " + org.bukkit.ChatColor.LIGHT_PURPLE +
                    org.bukkit.ChatColor.BOLD + coreLevel.getVipRank().toUpperCase() + org.bukkit.ChatColor.RED +
                    " para colocar este nucleo.");
            player.sendMessage(org.bukkit.ChatColor.GRAY + "Consiguelo en: " + org.bukkit.ChatColor.YELLOW + "moonlightmc.tebex.io");
            SoundManager.playProtectionDenied(player.getLocation());
            event.setCancelled(true);
            return;
        }

        // Verificar si el area esta libre
        if (!plugin.getProtectionManager().canPlaceCore(event.getBlock().getLocation(), coreLevel.getSize())) {
            plugin.getMessageManager().send(player, "protection.area-protected");
            SoundManager.playProtectionDenied(player.getLocation());
            event.setCancelled(true);
            return;
        }

        // Read saved upgrades from item (support both namespaces)
        String upgradeData = container.has(UPGRADES_KEY, PersistentDataType.STRING)
                ? container.get(UPGRADES_KEY, PersistentDataType.STRING)
                : container.has(UPGRADES_KEY_LEGACY, PersistentDataType.STRING)
                ? container.get(UPGRADES_KEY_LEGACY, PersistentDataType.STRING)
                : null;
        String membersData = container.has(MEMBERS_KEY, PersistentDataType.STRING)
                ? container.get(MEMBERS_KEY, PersistentDataType.STRING)
                : container.has(MEMBERS_KEY_LEGACY, PersistentDataType.STRING)
                ? container.get(MEMBERS_KEY_LEGACY, PersistentDataType.STRING)
                : null;

        // Colocar el bloque y comenzar animacion
        SoundManager.playCorePlaced(event.getBlock().getLocation());
        plugin.getMessageManager().send(player, "protection.creating");

        // Lock the core block during construction animation so it can't be broken
        plugin.getProtectionManager().lockLocation(event.getBlock().getLocation());

        // Ejecutar animacion y crear proteccion al terminar
        Runnable onRegionCreated = () -> {
                    // Unlock the core block after animation
                    plugin.getProtectionManager().unlockLocation(event.getBlock().getLocation());
                    // Crear la region protegida
                    ProtectedRegion region = new ProtectedRegion(
                            player.getUniqueId(),
                            event.getBlock().getLocation(),
                            level,
                            coreLevel.getSize());

                    // Restore upgrades if the core had them
                    if (upgradeData != null) {
                        try {
                            String[] parts = upgradeData.split(",");
                            if (parts.length >= 8) {
                                region.setNoExplosion(parts[0].equals("1"));
                                region.setNoPvP(parts[1].equals("1"));
                                region.setDamageBoostLevel(Integer.parseInt(parts[2]));
                                region.setHealthBoostLevel(Integer.parseInt(parts[3]));
                                region.setNoMobSpawn(parts[4].equals("1"));
                                region.setAutoHeal(parts[5].equals("1"));
                                region.setSpeedBoost(parts[6].equals("1"));
                                region.setNoFallDamage(parts[7].equals("1"));
                            }
                            if (parts.length >= 13) {
                                region.setAntiEnderman(parts[8].equals("1"));
                                region.setResourceGenerator(parts[9].equals("1"));
                                region.setFixedTime(Integer.parseInt(parts[10]));
                                region.setCoreTeleport(parts[11].equals("1"));
                                region.setNoHunger(parts[12].equals("1"));
                            }
                            if (parts.length >= 14) {
                                region.setAntiPhantom(parts[13].equals("1"));
                            }
                        } catch (Exception ignored) {
                        }
                    }

                    // Restore members if the core had them
                    if (membersData != null && !membersData.isEmpty()) {
                        try {
                            for (String uuidStr : membersData.split(";")) {
                                region.addMember(java.util.UUID.fromString(uuidStr.trim()));
                            }
                        } catch (Exception ignored) {
                        }
                    }

                    plugin.getProtectionManager().addRegion(region);
                    plugin.getDataManager().saveData();

                    if (player.isOnline()) {
                        plugin.getMessageManager().send(player, "protection.created");
                        plugin.getAchievementListener().onCorePlaced(player, region);
                        if (upgradeData != null) {
                            plugin.getAchievementListener().onCoreMoved(player);
                        }
                    }
                };

        // Use VIP animation if it's a VIP core, otherwise standard
        if (coreLevel.isVip()) {
            VipAnimation vipAnim = new VipAnimation(plugin);
            vipAnim.playVipPlacement(event.getBlock().getLocation(), coreLevel.getMaterial(),
                    coreLevel.getVipRank(), onRegionCreated);
        } else {
            CoreAnimation animation = new CoreAnimation(plugin);
            animation.playActivationAnimation(
                    event.getBlock().getLocation().add(0.5, 0.5, 0.5),
                    coreLevel, onRegionCreated);
        }
    }
}
