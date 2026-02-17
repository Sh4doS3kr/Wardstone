package com.moonlight.coreprotect.protection;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.core.ProtectedRegion;
import com.moonlight.coreprotect.effects.SoundManager;
import com.moonlight.coreprotect.gui.CoreManagementGUI;
import com.moonlight.coreprotect.gui.GUIListener;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.Material;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.Location;
import java.util.UUID;

public class ProtectionListener implements Listener {

    private final CoreProtectPlugin plugin;

    public ProtectionListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        if (plugin.getProtectionManager().isLocked(event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }
        // Insta-Break logic for cores
        Player player = event.getPlayer();
        ProtectedRegion coreRegion = plugin.getProtectionManager().getRegionByCore(event.getBlock().getLocation());

        if (coreRegion != null) {
            // Check permission/ownership
            if (!plugin.getConfig().getBoolean("settings.allow-core-break", false)
                    && !coreRegion.getOwner().equals(player.getUniqueId())) {
                return;
            }

            if (coreRegion.getOwner().equals(player.getUniqueId())) {
                event.setCancelled(true);
                event.setInstaBreak(true);
                handleCoreBreak(player, coreRegion, event.getBlock());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (plugin.getProtectionManager().isLocked(event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }
        Player player = event.getPlayer();

        if (player.hasPermission("coreprotect.admin")) {
            return;
        }

        ProtectedRegion region = plugin.getProtectionManager().getRegionAt(event.getBlock().getLocation());
        if (region == null) {
            return;
        }

        ProtectedRegion coreRegion = plugin.getProtectionManager().getRegionByCore(event.getBlock().getLocation());
        if (coreRegion != null) {
            if (!plugin.getConfig().getBoolean("settings.allow-core-break", false)) {
                plugin.getMessageManager().send(player, "protection.cannot-break");
                SoundManager.playProtectionDenied(player.getLocation());
                event.setCancelled(true);
                return;
            }
            if (coreRegion.getOwner().equals(player.getUniqueId())) {
                event.setCancelled(true);
                handleCoreBreak(player, coreRegion, event.getBlock());
                return;
            }
        }

        if (!region.canAccess(player.getUniqueId())) {
            plugin.getMessageManager().send(player, "protection.cannot-break");
            SoundManager.playProtectionDenied(player.getLocation());
            event.setCancelled(true);
        }
    }

    private void handleCoreBreak(Player player, ProtectedRegion coreRegion, org.bukkit.block.Block block) {
        if (block.getType() == Material.AIR)
            return;

        Material material = block.getType();
        block.setType(Material.AIR);

        com.moonlight.coreprotect.effects.CoreAnimation animation = new com.moonlight.coreprotect.effects.CoreAnimation(
                plugin);
        animation.playBreakAnimation(block.getLocation(), material, () -> {
            if (plugin.getProtectionManager().getRegion(coreRegion.getId()) != null) {
                plugin.getProtectionManager().removeRegion(coreRegion.getId());
                plugin.getMessageManager().send(player, "protection.removed");
                if (player.isOnline()) {
                    plugin.getAchievementListener().onCoreBreak(player);
                }

                com.moonlight.coreprotect.core.CoreLevel level = com.moonlight.coreprotect.core.CoreLevel
                        .fromConfig(plugin.getConfig(), coreRegion.getLevel());
                org.bukkit.inventory.ItemStack dropItem;
                if (level != null) {
                    dropItem = level.toItemStack();
                } else {
                    dropItem = new org.bukkit.inventory.ItemStack(material);
                }

                // Store upgrades + members in the dropped item
                org.bukkit.inventory.meta.ItemMeta meta = dropItem.getItemMeta();
                if (meta != null) {
                    org.bukkit.persistence.PersistentDataContainer pdc = meta.getPersistentDataContainer();
                    org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey("coreprotect", "core_upgrades");
                    StringBuilder data = new StringBuilder();
                    data.append(coreRegion.isNoExplosion() ? "1" : "0").append(",");
                    data.append(coreRegion.isNoPvP() ? "1" : "0").append(",");
                    data.append(coreRegion.getDamageBoostLevel()).append(",");
                    data.append(coreRegion.getHealthBoostLevel()).append(",");
                    data.append(coreRegion.isNoMobSpawn() ? "1" : "0").append(",");
                    data.append(coreRegion.isAutoHeal() ? "1" : "0").append(",");
                    data.append(coreRegion.isSpeedBoost() ? "1" : "0").append(",");
                    data.append(coreRegion.isNoFallDamage() ? "1" : "0").append(",");
                    data.append(coreRegion.isAntiEnderman() ? "1" : "0").append(",");
                    data.append(coreRegion.isResourceGenerator() ? "1" : "0").append(",");
                    data.append(coreRegion.getFixedTime()).append(",");
                    data.append(coreRegion.isCoreTeleport() ? "1" : "0").append(",");
                    data.append(coreRegion.isNoHunger() ? "1" : "0");
                    pdc.set(key, org.bukkit.persistence.PersistentDataType.STRING, data.toString());

                    // Store members
                    if (!coreRegion.getMembers().isEmpty()) {
                        org.bukkit.NamespacedKey membersKey = new org.bukkit.NamespacedKey("coreprotect", "core_members");
                        StringBuilder membersData = new StringBuilder();
                        for (UUID member : coreRegion.getMembers()) {
                            if (membersData.length() > 0) membersData.append(";");
                            membersData.append(member.toString());
                        }
                        pdc.set(membersKey, org.bukkit.persistence.PersistentDataType.STRING, membersData.toString());
                    }

                    dropItem.setItemMeta(meta);
                }

                block.getWorld().dropItemNaturally(block.getLocation(), dropItem);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlacePost(BlockPlaceEvent event) {
        if (event.getItemInHand().hasItemMeta() &&
                event.getItemInHand().getItemMeta().getPersistentDataContainer().has(
                        new org.bukkit.NamespacedKey("coreprotect", "core_level"),
                        org.bukkit.persistence.PersistentDataType.INTEGER)) {

            org.bukkit.block.Block block = event.getBlock();
            Material finalMaterial = block.getType();

            plugin.getProtectionManager().lockLocation(block.getLocation());
            block.setType(Material.AIR, false);

            new com.moonlight.coreprotect.effects.CoreAnimation(plugin).playPlacementAnimation(block.getLocation(),
                    finalMaterial, () -> {
                        block.setType(finalMaterial);
                        plugin.getProtectionManager().unlockLocation(block.getLocation());
                    });
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("coreprotect.admin"))
            return;

        ProtectedRegion region = plugin.getProtectionManager().getRegionAt(event.getBlock().getLocation());
        if (region != null && !region.canAccess(player.getUniqueId())) {
            plugin.getMessageManager().send(player, "protection.cannot-build");
            SoundManager.playProtectionDenied(player.getLocation());
            event.setCancelled(true);
        }
    }

    // === RIGHT-CLICK CORE -> OPEN MANAGEMENT GUI ===
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null)
            return;
        Player player = event.getPlayer();

        if (plugin.getProtectionManager().isLocked(event.getClickedBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }

        // Right-click on core -> Open Management GUI
        ProtectedRegion interactCore = plugin.getProtectionManager()
                .getRegionByCore(event.getClickedBlock().getLocation());
        if (interactCore != null && event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            if (interactCore.canAccess(player.getUniqueId())) {
                event.setCancelled(true);
                // Track which region this player is managing
                GUIListener.setPlayerRegion(player.getUniqueId(), interactCore.getId());
                SoundManager.playGUIOpen(player.getLocation());
                new CoreManagementGUI(plugin).open(player, interactCore);
                return;
            }
        }

        // Block crop trampling (farmland + turtle eggs)
        if (event.getAction() == org.bukkit.event.block.Action.PHYSICAL) {
            Material blockType = event.getClickedBlock().getType();
            if (blockType == Material.FARMLAND || blockType == Material.TURTLE_EGG) {
                ProtectedRegion cropRegion = plugin.getProtectionManager().getRegionAt(event.getClickedBlock().getLocation());
                if (cropRegion != null) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        if (player.hasPermission("coreprotect.admin"))
            return;

        // Bloquear contenedores
        if (!isContainer(event.getClickedBlock().getType()))
            return;

        ProtectedRegion region = plugin.getProtectionManager().getRegionAt(event.getClickedBlock().getLocation());
        if (region == null)
            return;

        if (!region.canAccess(player.getUniqueId())) {
            plugin.getMessageManager().send(player, "protection.cannot-interact");
            SoundManager.playProtectionDenied(player.getLocation());
            event.setCancelled(true);
        }
    }

    // === EXPLOSION PROTECTION (default + enhanced with upgrade) ===
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> {
            ProtectedRegion region = plugin.getProtectionManager().getRegionAt(block.getLocation());
            return region != null;
        });
    }

    // === PVP PROTECTION (Anti-PvP upgrade) ===
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player victim = (Player) event.getEntity();

        // Find attacker (could be direct or projectile)
        Player attacker = null;
        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile) {
            org.bukkit.entity.Projectile projectile = (org.bukkit.entity.Projectile) event.getDamager();
            if (projectile.getShooter() instanceof Player) {
                attacker = (Player) projectile.getShooter();
            }
        }

        if (attacker == null) {
            // Non-player damage to player in region -> apply damage boost defense if applicable
            ProtectedRegion victimRegion = plugin.getProtectionManager().getRegionAt(victim.getLocation());
            if (victimRegion != null && victimRegion.canAccess(victim.getUniqueId())) {
                // Damage boost applies to outgoing damage, not incoming (handled below)
            }
            return;
        }

        // Block PvP in any protected region
        ProtectedRegion victimRegion = plugin.getProtectionManager().getRegionAt(victim.getLocation());
        if (victimRegion != null) {
            event.setCancelled(true);
            plugin.getMessageManager().send(attacker, "upgrades.no-pvp-zone");
            return;
        }

        ProtectedRegion attackerRegion = plugin.getProtectionManager().getRegionAt(attacker.getLocation());
        if (attackerRegion != null) {
            event.setCancelled(true);
            plugin.getMessageManager().send(attacker, "upgrades.no-pvp-zone");
            return;
        }

        // === DAMAGE BOOST (attacker in own region with damage boost) ===
        if (attackerRegion != null && attackerRegion.canAccess(attacker.getUniqueId())
                && attackerRegion.getDamageBoostLevel() > 0) {
            double multiplier = attackerRegion.getDamageBoostMultiplier();
            event.setDamage(event.getDamage() * multiplier);
        }
    }

    // === FALL DAMAGE PROTECTION ===
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();

        ProtectedRegion region = plugin.getProtectionManager().getRegionAt(player.getLocation());
        if (region == null) return;

        if (!region.canAccess(player.getUniqueId())) return;

        // No fall damage
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && region.isNoFallDamage()) {
            event.setCancelled(true);
        }

        // No explosion damage to members
        if (region.isNoExplosion() &&
                (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION ||
                 event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION)) {
            event.setCancelled(true);
        }
    }

    // === MOB SPAWN PREVENTION ===
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM ||
            event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
            return; // Don't block manual spawns
        }

        if (!(event.getEntity() instanceof org.bukkit.entity.Monster)) return;

        ProtectedRegion region = plugin.getProtectionManager().getRegionAt(event.getLocation());
        if (region != null && region.isNoMobSpawn()) {
            event.setCancelled(true);
        }
    }

    // === ANTI-ENDERMAN (block pickup/place) ===
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(org.bukkit.event.entity.EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Enderman) {
            ProtectedRegion region = plugin.getProtectionManager().getRegionAt(event.getBlock().getLocation());
            if (region != null && region.isAntiEnderman()) {
                event.setCancelled(true);
            }
        }
    }

    // === NO HUNGER ===
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFoodLevelChange(org.bukkit.event.entity.FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (event.getFoodLevel() < player.getFoodLevel()) {
            ProtectedRegion region = plugin.getProtectionManager().getRegionAt(player.getLocation());
            if (region != null && region.isNoHunger() && region.canAccess(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    // === BUCKET PROTECTION (lava/water placement) ===
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("coreprotect.admin")) return;

        ProtectedRegion region = plugin.getProtectionManager().getRegionAt(event.getBlock().getLocation());
        if (region != null && !region.canAccess(player.getUniqueId())) {
            plugin.getMessageManager().send(player, "protection.cannot-build");
            SoundManager.playProtectionDenied(player.getLocation());
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("coreprotect.admin")) return;

        ProtectedRegion region = plugin.getProtectionManager().getRegionAt(event.getBlock().getLocation());
        if (region != null && !region.canAccess(player.getUniqueId())) {
            plugin.getMessageManager().send(player, "protection.cannot-break");
            SoundManager.playProtectionDenied(player.getLocation());
            event.setCancelled(true);
        }
    }

    // === LIQUID FLOW PROTECTION (prevent lava/water flowing into protected zones) ===
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLiquidFlow(BlockFromToEvent event) {
        ProtectedRegion fromRegion = plugin.getProtectionManager().getRegionAt(event.getBlock().getLocation());
        ProtectedRegion toRegion = plugin.getProtectionManager().getRegionAt(event.getToBlock().getLocation());

        // Block liquid flowing INTO a protected region from outside
        if (toRegion != null && fromRegion == null) {
            event.setCancelled(true);
        }
        // Block liquid flowing into a DIFFERENT protected region
        if (toRegion != null && fromRegion != null && !toRegion.getId().equals(fromRegion.getId())) {
            event.setCancelled(true);
        }
    }

    // Buff effects (speed, auto-heal, health boost) are now handled by
    // ProtectionManager.startBuffTask() repeating task to prevent flickering

    private boolean isContainer(org.bukkit.Material material) {
        switch (material) {
            case CHEST:
            case TRAPPED_CHEST:
            case BARREL:
            case SHULKER_BOX:
            case WHITE_SHULKER_BOX:
            case ORANGE_SHULKER_BOX:
            case MAGENTA_SHULKER_BOX:
            case LIGHT_BLUE_SHULKER_BOX:
            case YELLOW_SHULKER_BOX:
            case LIME_SHULKER_BOX:
            case PINK_SHULKER_BOX:
            case GRAY_SHULKER_BOX:
            case LIGHT_GRAY_SHULKER_BOX:
            case CYAN_SHULKER_BOX:
            case PURPLE_SHULKER_BOX:
            case BLUE_SHULKER_BOX:
            case BROWN_SHULKER_BOX:
            case GREEN_SHULKER_BOX:
            case RED_SHULKER_BOX:
            case BLACK_SHULKER_BOX:
            case FURNACE:
            case BLAST_FURNACE:
            case SMOKER:
            case HOPPER:
            case DISPENSER:
            case DROPPER:
            case BREWING_STAND:
                return true;
            default:
                return false;
        }
    }
}
