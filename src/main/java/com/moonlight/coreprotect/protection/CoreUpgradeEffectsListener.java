package com.moonlight.coreprotect.protection;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.core.ProtectedRegion;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

/**
 * Listener para los efectos de las nuevas mejoras de core (Huge Update).
 * - XP Boost: +10% XP por nivel
 * - Crop Growth: Acelera cultivos
 * - Fly Zone: Vuelo para miembros
 * - Auto-Replant: Replantado automático al cosechar
 * - Lucky Mining: Chance de doble drop
 * - Beacon Aura: Night Vision + Haste
 * - Anti-Fire Spread: Bloquea propagación de fuego
 * - Mob Repeller: Empuja mobs hostiles fuera
 */
public class CoreUpgradeEffectsListener implements Listener {

    private final CoreProtectPlugin plugin;
    private final Set<UUID> flyingInZone = new HashSet<>();
    private final Map<UUID, UUID> lastRegion = new HashMap<>(); // track zone transitions

    public CoreUpgradeEffectsListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        startBeaconAuraTask();
        startMobRepellerTask();
        startFlyZoneTask();
        startCropGrowthTask();
    }

    // ═══════════════════════════════════════
    // XP BOOST - Multiplicador de experiencia
    // ═══════════════════════════════════════
    @EventHandler(priority = EventPriority.HIGH)
    public void onXpGain(PlayerExpChangeEvent event) {
        Player player = event.getPlayer();
        ProtectedRegion region = plugin.getProtectionManager().getRegionAt(player.getLocation());
        if (region == null) return;
        if (!region.canAccess(player.getUniqueId())) return;
        if (region.getXpBoostLevel() <= 0) return;

        int original = event.getAmount();
        int boosted = (int) Math.ceil(original * region.getXpBoostMultiplier());
        event.setAmount(boosted);
    }

    // ═══════════════════════════════════════
    // AUTO-REPLANT - Replantado automático
    // ═══════════════════════════════════════
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        ProtectedRegion region = plugin.getProtectionManager().getRegionAt(block.getLocation());
        if (region == null) return;
        if (!region.isAutoReplant()) return;
        if (!region.canAccess(player.getUniqueId())) return;

        Material type = block.getType();
        if (!isMatureCrop(block)) return;

        Material seedType = getSeedType(type);
        if (seedType == null) return;

        // Replant con 1 tick de delay
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (block.getType() == Material.AIR) {
                block.setType(type);
                // Reset age to 0
                if (block.getBlockData() instanceof Ageable) {
                    Ageable data = (Ageable) block.getBlockData();
                    data.setAge(0);
                    block.setBlockData(data);
                }
            }
        }, 1L);
    }

    // ═══════════════════════════════════════
    // LUCKY MINING - Doble drop chance
    // ═══════════════════════════════════════
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMiningBlock(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        if (player.getGameMode() == GameMode.CREATIVE) return;

        ProtectedRegion region = plugin.getProtectionManager().getRegionAt(block.getLocation());
        if (region == null) return;
        if (region.getLuckyMiningLevel() <= 0) return;
        if (!region.canAccess(player.getUniqueId())) return;

        if (!isOre(block.getType())) return;

        double chance = region.getLuckyMiningChance();
        if (Math.random() < chance) {
            // Drop extra items
            Collection<ItemStack> drops = block.getDrops(player.getInventory().getItemInMainHand());
            for (ItemStack drop : drops) {
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), drop.clone());
            }
            // Visual feedback
            block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, block.getLocation().add(0.5, 0.5, 0.5), 5, 0.3, 0.3, 0.3, 0);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
        }
    }

    // ═══════════════════════════════════════
    // ANTI-FIRE SPREAD - No propagación de fuego
    // ═══════════════════════════════════════
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFireSpread(BlockSpreadEvent event) {
        if (event.getSource().getType() != Material.FIRE) return;
        ProtectedRegion region = plugin.getProtectionManager().getRegionAt(event.getBlock().getLocation());
        if (region != null && (region.isAntiFireSpread() || !region.isAllowFireSpread())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (event.getCause() == BlockIgniteEvent.IgniteCause.SPREAD || 
            event.getCause() == BlockIgniteEvent.IgniteCause.LAVA) {
            ProtectedRegion region = plugin.getProtectionManager().getRegionAt(event.getBlock().getLocation());
            if (region != null && (region.isAntiFireSpread() || !region.isAllowFireSpread())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        ProtectedRegion region = plugin.getProtectionManager().getRegionAt(event.getBlock().getLocation());
        if (region != null && (region.isAntiFireSpread() || !region.isAllowFireSpread())) {
            event.setCancelled(true);
        }
    }

    // ═══════════════════════════════════════
    // ZONE ENTRY: Fly Zone + Entry Mode + Welcome Message
    // ═══════════════════════════════════════
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY()) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (player.hasPermission("wardstone.admin")) {
            // Admins bypass entry mode but still get fly
            handleFlyZone(player, plugin.getProtectionManager().getRegionAt(event.getTo()));
            return;
        }

        ProtectedRegion regionTo = plugin.getProtectionManager().getRegionAt(event.getTo());
        ProtectedRegion regionFrom = plugin.getProtectionManager().getRegionAt(event.getFrom());

        UUID prevRegionId = lastRegion.get(uuid);
        UUID curRegionId = regionTo != null ? regionTo.getId() : null;

        // === ENTRY MODE ENFORCEMENT ===
        if (regionTo != null && !regionTo.canAccess(uuid)) {
            int entryMode = regionTo.getEntryMode();
            // Mode 1: Members only — block non-members
            if (entryMode == 1) {
                event.setCancelled(true);
                player.sendMessage(SmallCaps.convert("§c§l✦ §cEsta zona es de solo miembros."));
                return;
            }
            // Mode 2: Ban list — block banned players
            if (entryMode == 2 && regionTo.isBanned(uuid)) {
                event.setCancelled(true);
                player.sendMessage(SmallCaps.convert("§c§l✦ §cEstás baneado de esta zona."));
                return;
            }
        }

        // === ZONE TRANSITION: Welcome message ===
        boolean enteringNewZone = (curRegionId != null && !curRegionId.equals(prevRegionId));
        if (enteringNewZone && regionTo != null) {
            // Show welcome message
            if (regionTo.getWelcomeMessage() != null && !regionTo.getWelcomeMessage().isEmpty()) {
                player.sendMessage(SmallCaps.convert("§a§l✦ §f" + regionTo.getWelcomeMessage()));
            }
            // Show zone name in action bar
            String zoneName = regionTo.getDisplayName();
            String ownerName = Bukkit.getOfflinePlayer(regionTo.getOwner()).getName();
            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    new net.md_5.bungee.api.chat.TextComponent("§b✦ " + zoneName + " §7— §f" + (ownerName != null ? ownerName : "???")));
        }
        lastRegion.put(uuid, curRegionId);

        // === FLY ZONE ===
        if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
            handleFlyZone(player, regionTo);
        }
    }

    private void handleFlyZone(Player player, ProtectedRegion region) {
        UUID uuid = player.getUniqueId();
        if (region != null && region.isFlyZone() && region.canAccess(uuid)) {
            if (!flyingInZone.contains(uuid)) {
                flyingInZone.add(uuid);
                player.setAllowFlight(true);
                player.sendMessage(SmallCaps.convert("§a§l✦ §aVuelo activado §7en esta zona. §c($750/s al volar)"));
                player.playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 0.5f, 1.2f);
            }
        } else {
            if (flyingInZone.contains(uuid)) {
                flyingInZone.remove(uuid);
                if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
                    player.setAllowFlight(false);
                    player.setFlying(false);
                    player.sendMessage(SmallCaps.convert("§c§l✦ §cVuelo desactivado §7fuera de la zona."));
                }
            }
        }
    }

    // ═══════════════════════════════════════
    // BEACON AURA - Night Vision + Haste
    // ═══════════════════════════════════════
    private void startBeaconAuraTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    ProtectedRegion region = plugin.getProtectionManager().getRegionAt(player.getLocation());
                    if (region != null && region.isBeaconAura() && region.canAccess(player.getUniqueId())) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 300, 0, true, false, true));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 100, 0, true, false, true));
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 60L);
    }

    // ═══════════════════════════════════════
    // MOB REPELLER - Empuja mobs hostiles fuera
    // ═══════════════════════════════════════
    private void startMobRepellerTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (ProtectedRegion region : plugin.getProtectionManager().getAllRegions()) {
                    if (!region.isMobRepeller()) continue;
                    Location center = region.getCoreLocation();
                    if (center == null || center.getWorld() == null) continue;
                    if (!center.getWorld().isChunkLoaded(center.getBlockX() >> 4, center.getBlockZ() >> 4)) continue;

                    int radius = region.getEffectiveSize() / 2;
                    Collection<Entity> entities = center.getWorld().getNearbyEntities(center, radius, 64, radius);
                    for (Entity entity : entities) {
                        if (entity instanceof Monster && region.contains(entity.getLocation())) {
                            // Push mob away from center
                            org.bukkit.util.Vector direction = entity.getLocation().toVector()
                                    .subtract(center.toVector()).normalize().multiply(1.5);
                            direction.setY(0.3);
                            entity.setVelocity(direction);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 40L, 40L); // Every 2 seconds
    }

    // ═══════════════════════════════════════
    // FLY ZONE TASK - Charge $50/sec + cleanup
    // ═══════════════════════════════════════
    private void startFlyZoneTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                Iterator<UUID> it = flyingInZone.iterator();
                while (it.hasNext()) {
                    UUID uuid = it.next();
                    Player player = Bukkit.getPlayer(uuid);
                    if (player == null || !player.isOnline()) {
                        it.remove();
                        continue;
                    }
                    // Only charge if actually flying (in the air)
                    if (!player.isFlying()) continue;
                    double cost = com.moonlight.coreprotect.gui.CoreUpgradesShopGUI.FLY_COST_PER_SECOND;
                    if (plugin.getEconomy() != null && plugin.getEconomy().has(player, cost)) {
                        plugin.getEconomy().withdrawPlayer(player, cost);
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                                new TextComponent("§6✦ §eVuelo: §c-$" + (int) cost + "/s §8| §7Saldo: §a$" +
                                        com.moonlight.coreprotect.core.CoreMaintenanceManager.formatMoney(plugin.getEconomy().getBalance(player))));
                    } else {
                        // Can't pay — disable flight
                        it.remove();
                        player.setAllowFlight(false);
                        player.setFlying(false);
                        player.sendMessage(SmallCaps.convert("§c§l✦ §cSin fondos para volar. §7Necesitas §c$" + (int) cost + "/s§7."));
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 0.8f);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // every second (20 ticks)
    }

    // ═══════════════════════════════════════
    // CROP GROWTH TASK - Accelerates crop growth
    // ═══════════════════════════════════════
    private void startCropGrowthTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (ProtectedRegion region : plugin.getProtectionManager().getAllRegions()) {
                    if (region.getCropGrowthLevel() <= 0) continue;
                    Location center = region.getCoreLocation();
                    if (center == null || center.getWorld() == null) continue;
                    if (!center.getWorld().isChunkLoaded(center.getBlockX() >> 4, center.getBlockZ() >> 4)) continue;

                    int radius = region.getEffectiveSize() / 2;
                    int bonusTicks = region.getCropGrowthLevel(); // 1-3 extra growth ticks

                    // Random crop boost within radius (not every block, performance)
                    Random rand = new Random();
                    for (int attempt = 0; attempt < bonusTicks * 3; attempt++) {
                        int rx = center.getBlockX() + rand.nextInt(radius * 2 + 1) - radius;
                        int rz = center.getBlockZ() + rand.nextInt(radius * 2 + 1) - radius;
                        // Search Y for crops
                        for (int y = center.getBlockY() - 10; y < center.getBlockY() + 20; y++) {
                            Block block = center.getWorld().getBlockAt(rx, y, rz);
                            if (block.getBlockData() instanceof Ageable) {
                                Ageable age = (Ageable) block.getBlockData();
                                if (age.getAge() < age.getMaximumAge()) {
                                    age.setAge(Math.min(age.getAge() + 1, age.getMaximumAge()));
                                    block.setBlockData(age);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 600L, 600L); // Every 30 seconds
    }

    // ═══════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════
    public void handlePlayerQuit(UUID uuid) {
        flyingInZone.remove(uuid);
    }

    private boolean isMatureCrop(Block block) {
        if (!(block.getBlockData() instanceof Ageable)) return false;
        Ageable age = (Ageable) block.getBlockData();
        return age.getAge() >= age.getMaximumAge();
    }

    private Material getSeedType(Material crop) {
        switch (crop) {
            case WHEAT: return Material.WHEAT_SEEDS;
            case CARROTS: return Material.CARROT;
            case POTATOES: return Material.POTATO;
            case BEETROOTS: return Material.BEETROOT_SEEDS;
            case NETHER_WART: return Material.NETHER_WART;
            default: return null;
        }
    }

    private boolean isOre(Material material) {
        switch (material) {
            case COAL_ORE:
            case DEEPSLATE_COAL_ORE:
            case IRON_ORE:
            case DEEPSLATE_IRON_ORE:
            case GOLD_ORE:
            case DEEPSLATE_GOLD_ORE:
            case DIAMOND_ORE:
            case DEEPSLATE_DIAMOND_ORE:
            case EMERALD_ORE:
            case DEEPSLATE_EMERALD_ORE:
            case LAPIS_ORE:
            case DEEPSLATE_LAPIS_ORE:
            case REDSTONE_ORE:
            case DEEPSLATE_REDSTONE_ORE:
            case COPPER_ORE:
            case DEEPSLATE_COPPER_ORE:
            case NETHER_GOLD_ORE:
            case NETHER_QUARTZ_ORE:
                return true;
            default:
                return false;
        }
    }
}
