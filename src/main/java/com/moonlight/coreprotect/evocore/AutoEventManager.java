package com.moonlight.coreprotect.evocore;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.core.ProtectedRegion;
import com.moonlight.coreprotect.evocore.metrics.ZoneMetrics;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class AutoEventManager {

    private final CoreProtectPlugin plugin;
    private final DataCollector collector;
    private final AnalyticsEngine analytics;
    private final BalanceEngine balance;
    private final Random random = new Random();

    // Active events
    private final Set<UUID> activeInvasions = new HashSet<>();
    private final Set<UUID> activeTreasures = new HashSet<>();

    // Cooldowns (prevent spam)
    private final Map<UUID, Long> eventCooldowns = new HashMap<>();
    private static final long EVENT_COOLDOWN_MS = 1000L * 60 * 30; // 30 min cooldown per zone

    public AutoEventManager(CoreProtectPlugin plugin, DataCollector collector,
                            AnalyticsEngine analytics, BalanceEngine balance) {
        this.plugin = plugin;
        this.collector = collector;
        this.analytics = analytics;
        this.balance = balance;
        startEventChecker();
    }

    private void startEventChecker() {
        // Check every 5 minutes
        new BukkitRunnable() {
            @Override
            public void run() {
                checkAndTriggerEvents();
            }
        }.runTaskTimer(plugin, 20L * 60 * 5, 20L * 60 * 5);
    }

    private void checkAndTriggerEvents() {
        for (ProtectedRegion region : plugin.getProtectionManager().getAllRegions()) {
            UUID id = region.getId();
            if (isOnCooldown(id)) continue;

            AnalyticsEngine.ZoneStatus status = analytics.getZoneStatus(id);
            int overfarmedDays = balance.getOverfarmedDays(id);

            // Overfarmed 3+ cycles -> Dark Invasion
            if (status == AnalyticsEngine.ZoneStatus.OVERFARMED && overfarmedDays >= 3) {
                triggerDarkInvasion(region);
                setCooldown(id);
            }
            // Underused zone -> Hidden Treasure
            else if (status == AnalyticsEngine.ZoneStatus.UNDERUSED) {
                triggerHiddenTreasure(region);
                setCooldown(id);
            }
        }
    }

    private void triggerDarkInvasion(ProtectedRegion region) {
        Location coreLoc = region.getCoreLocation();
        if (coreLoc == null || coreLoc.getWorld() == null) return;

        Player owner = Bukkit.getPlayer(region.getOwner());
        if (owner == null || !owner.isOnline()) return;

        activeInvasions.add(region.getId());
        World world = coreLoc.getWorld();

        // Announce
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "⚠ Una invasión oscura ha comenzado en la zona de "
                    + owner.getName() + "...");
        }
        owner.playSound(owner.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.8f, 0.6f);

        // Spawn wave of tough mobs
        int halfSize = region.getSize() / 2;
        int mobCount = 12 + random.nextInt(8);

        for (int i = 0; i < mobCount; i++) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    double angle = random.nextDouble() * 2 * Math.PI;
                    int dist = 3 + random.nextInt(Math.max(1, halfSize));
                    int x = coreLoc.getBlockX() + (int)(Math.cos(angle) * dist);
                    int z = coreLoc.getBlockZ() + (int)(Math.sin(angle) * dist);

                    Chunk chunk = world.getChunkAt(x >> 4, z >> 4);
                    if (!chunk.isLoaded()) chunk.load();

                    int y = world.getHighestBlockYAt(x, z) + 1;
                    Location spawnLoc = new Location(world, x + 0.5, y, z + 0.5);

                    EntityType[] types = {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.WITCH,
                            EntityType.VINDICATOR, EntityType.PILLAGER};
                    EntityType type = types[random.nextInt(types.length)];

                    Entity mob = world.spawnEntity(spawnLoc, type);
                    if (mob instanceof LivingEntity) {
                        LivingEntity living = (LivingEntity) mob;
                        living.setCustomName(ChatColor.DARK_RED + "☠ Invasor Oscuro");
                        living.setCustomNameVisible(true);
                        living.setRemoveWhenFarAway(false);
                    }
                }
            }.runTaskLater(plugin, i * 10L); // Stagger spawns
        }

        // End invasion after 2 minutes
        new BukkitRunnable() {
            @Override
            public void run() {
                activeInvasions.remove(region.getId());
            }
        }.runTaskLater(plugin, 20L * 60 * 2);
    }

    private void triggerHiddenTreasure(ProtectedRegion region) {
        Location coreLoc = region.getCoreLocation();
        if (coreLoc == null || coreLoc.getWorld() == null) return;

        World world = coreLoc.getWorld();
        int halfSize = region.getSize() / 2;

        // Find a spot to place a chest
        for (int attempt = 0; attempt < 20; attempt++) {
            int x = coreLoc.getBlockX() + random.nextInt(halfSize * 2) - halfSize;
            int z = coreLoc.getBlockZ() + random.nextInt(halfSize * 2) - halfSize;
            int y = world.getHighestBlockYAt(x, z) + 1;

            Block block = world.getBlockAt(x, y, z);
            if (block.getType() == Material.AIR) {
                block.setType(Material.CHEST);
                if (block.getState() instanceof Chest) {
                    Chest chest = (Chest) block.getState();
                    fillTreasureChest(chest);
                    activeTreasures.add(region.getId());

                    // Announce to nearby players
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.getWorld().equals(world) && p.getLocation().distanceSquared(coreLoc) < 10000) {
                            p.sendMessage(ChatColor.GOLD + "✦ " + ChatColor.YELLOW
                                    + "¡Un cofre del tesoro ha aparecido en una zona olvidada!");
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.5f);
                        }
                    }

                    // Remove chest after 10 minutes if not looted
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (block.getType() == Material.CHEST) {
                                block.setType(Material.AIR);
                            }
                            activeTreasures.remove(region.getId());
                        }
                    }.runTaskLater(plugin, 20L * 60 * 10);
                    return;
                }
            }
        }
    }

    private void fillTreasureChest(Chest chest) {
        ItemStack[] loot = {
            new ItemStack(Material.DIAMOND, 1 + random.nextInt(3)),
            new ItemStack(Material.EMERALD, 2 + random.nextInt(5)),
            new ItemStack(Material.GOLD_INGOT, 3 + random.nextInt(8)),
            new ItemStack(Material.IRON_INGOT, 5 + random.nextInt(10)),
            new ItemStack(Material.GOLDEN_APPLE, 1 + random.nextInt(2)),
            new ItemStack(Material.EXPERIENCE_BOTTLE, 5 + random.nextInt(15)),
            new ItemStack(Material.ENDER_PEARL, 1 + random.nextInt(4)),
            createRandomEnchantedBook(),
            new ItemStack(Material.NAME_TAG, 1),
            new ItemStack(Material.SADDLE, 1)
        };

        // Place 3-6 random items
        int itemCount = 3 + random.nextInt(4);
        List<ItemStack> pool = new ArrayList<>(Arrays.asList(loot));
        Collections.shuffle(pool);
        for (int i = 0; i < Math.min(itemCount, pool.size()); i++) {
            int slot = random.nextInt(27);
            chest.getInventory().setItem(slot, pool.get(i));
        }
    }

    private ItemStack createRandomEnchantedBook() {
        Enchantment[] enchants = {
            Enchantment.SHARPNESS, Enchantment.SMITE, Enchantment.BANE_OF_ARTHROPODS,
            Enchantment.EFFICIENCY, Enchantment.UNBREAKING, Enchantment.FORTUNE,
            Enchantment.PROTECTION, Enchantment.FIRE_PROTECTION, Enchantment.BLAST_PROTECTION,
            Enchantment.PROJECTILE_PROTECTION, Enchantment.FEATHER_FALLING,
            Enchantment.POWER, Enchantment.PUNCH, Enchantment.FLAME,
            Enchantment.LOOTING, Enchantment.SILK_TOUCH, Enchantment.RESPIRATION,
            Enchantment.AQUA_AFFINITY, Enchantment.THORNS, Enchantment.MENDING
        };
        Enchantment chosen = enchants[random.nextInt(enchants.length)];
        int level = 1 + random.nextInt(chosen.getMaxLevel());
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        meta.addStoredEnchant(chosen, level, true);
        book.setItemMeta(meta);
        return book;
    }

    private boolean isOnCooldown(UUID regionId) {
        Long last = eventCooldowns.get(regionId);
        return last != null && System.currentTimeMillis() - last < EVENT_COOLDOWN_MS;
    }

    private void setCooldown(UUID regionId) {
        eventCooldowns.put(regionId, System.currentTimeMillis());
    }

    public boolean isInvasionActive(UUID regionId) { return activeInvasions.contains(regionId); }
    public boolean isTreasureActive(UUID regionId) { return activeTreasures.contains(regionId); }
    public int getActiveInvasionCount() { return activeInvasions.size(); }
    public int getActiveTreasureCount() { return activeTreasures.size(); }
}
