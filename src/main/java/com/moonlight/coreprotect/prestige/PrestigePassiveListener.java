package com.moonlight.coreprotect.prestige;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Passive abilities unlocked at prestige milestones:
 * P10: +Velocidad de minado (Haste I)
 * P20: Doble drop de crops (10% prob)
 * P30: Auto-smelt hierro/oro/cobre
 * P40: Regeneración lenta fuera de combate
 * P50: Resistencia a caída reducida (-50%)
 * P60: Doble XP de mobs
 * P70: Todas las anteriores (natural, cumple todos los >= checks)
 */
public class PrestigePassiveListener implements Listener {

    private final CoreProtectPlugin plugin;
    private final Map<UUID, Long> lastDamageTime = new HashMap<>();

    public PrestigePassiveListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        startPassiveTasks();
    }

    private int getPrestige(Player player) {
        PrestigeManager mgr = plugin.getPrestigeManager();
        if (mgr == null) return 0;
        return mgr.getData(player.getUniqueId()).prestige;
    }

    // ═══════════════════════════════════════
    // P10: Haste I while holding pickaxe
    // P40: Regen I when out of combat (5s)
    // ═══════════════════════════════════════
    private void startPassiveTasks() {
        // Every 2 seconds (40 ticks)
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();

            for (Player player : Bukkit.getOnlinePlayers()) {
                int prestige = getPrestige(player);
                if (prestige <= 0) continue;

                // P10+: Haste I if holding pickaxe
                if (prestige >= 10) {
                    Material hand = player.getInventory().getItemInMainHand().getType();
                    if (hand.name().contains("PICKAXE")) {
                        player.addPotionEffect(new PotionEffect(
                                PotionEffectType.HASTE, 60, 0, true, false, false));
                    }
                }

                // P40+: Regen I when out of combat (no damage for 5 seconds)
                if (prestige >= 40) {
                    Long lastHit = lastDamageTime.get(player.getUniqueId());
                    boolean outOfCombat = lastHit == null || (now - lastHit) > 5000;
                    if (outOfCombat && player.getHealth() < player.getMaxHealth()) {
                        player.addPotionEffect(new PotionEffect(
                                PotionEffectType.REGENERATION, 60, 0, true, false, false));
                    }
                }
            }
        }, 40L, 40L);
    }

    // Track when players take damage for combat tag
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageTrack(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            lastDamageTime.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    // ═══════════════════════════════════════
    // PRESTIGE COMBAT STATS: Damage bonus
    // Player deals extra damage based on prestige
    // ═══════════════════════════════════════
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDealDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        // No aplicar bonus si está en staff mode
        if (player.hasMetadata("vanished")) return;
        // No aplicar bonus si la víctima es staff
        if (event.getEntity() instanceof Player victim && victim.hasMetadata("vanished")) return;
        PrestigeManager mgr = plugin.getPrestigeManager();
        if (mgr == null) return;
        double mult = mgr.getDamageMultiplier(player.getUniqueId());
        if (mult > 1.0) {
            event.setDamage(event.getDamage() * mult);
        }
    }

    // ═══════════════════════════════════════
    // PRESTIGE COMBAT STATS: Defense bonus
    // Player takes reduced damage based on prestige
    // Also includes P50 fall damage reduction
    // ═══════════════════════════════════════
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTakeDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        // No aplicar defensa si está en staff mode
        if (player.hasMetadata("vanished")) return;
        int prestige = getPrestige(player);
        if (prestige <= 0) return;

        PrestigeManager mgr = plugin.getPrestigeManager();
        if (mgr == null) return;

        // Apply defense reduction
        double defMult = mgr.getDefenseMultiplier(player.getUniqueId());
        event.setDamage(event.getDamage() * defMult);

        // P50+: Extra fall damage reduction (-50% on top of defense)
        if (prestige >= 50 && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setDamage(event.getDamage() * 0.5);
        }
    }

    // ═══════════════════════════════════════
    // PRESTIGE COMBAT STATS: Health bonus
    // +2 HP (1 heart) per 10 prestige levels
    // ═══════════════════════════════════════
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyHealthBonus(event.getPlayer()), 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        resetHealthBonus(event.getPlayer());
    }

    private static final UUID HEALTH_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID LUCK_UUID   = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");

    public void applyAllStats(Player player) {
        if (!player.isOnline()) return;
        PrestigeManager mgr = plugin.getPrestigeManager();
        if (mgr == null) return;
        UUID uuid = player.getUniqueId();

        // ❤ Health: +2 HP per 10 levels
        double healthBonus = mgr.getHealthBonus(uuid);
        applyModifier(player, Attribute.MAX_HEALTH, "prestige_health", HEALTH_UUID, healthBonus);

        // 🍀 Luck: direct luck attribute bonus
        double luckBonus = mgr.getLuckBonus(uuid) * 0.01; // scale to attribute range (0-1 ish)
        applyModifier(player, Attribute.LUCK, "prestige_luck", LUCK_UUID, luckBonus);
    }

    public void applyHealthBonus(Player player) {
        applyAllStats(player);
    }

    private void applyModifier(Player player, Attribute attribute, String name, UUID modUUID, double value) {
        AttributeInstance attr = player.getAttribute(attribute);
        if (attr == null) return;
        // Remove old modifier
        attr.getModifiers().stream()
                .filter(m -> m.getName().equals(name))
                .forEach(attr::removeModifier);
        // Add new if value > 0
        if (value > 0) {
            attr.addModifier(new org.bukkit.attribute.AttributeModifier(
                    modUUID, name, value,
                    org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    public void resetAllStats(Player player) {
        removeModifier(player, Attribute.MAX_HEALTH, "prestige_health");
        removeModifier(player, Attribute.LUCK, "prestige_luck");
    }

    public void resetHealthBonus(Player player) {
        resetAllStats(player);
    }

    private void removeModifier(Player player, Attribute attribute, String name) {
        AttributeInstance attr = player.getAttribute(attribute);
        if (attr == null) return;
        attr.getModifiers().stream()
                .filter(m -> m.getName().equals(name))
                .forEach(attr::removeModifier);
    }

    public void refreshAllHealthBonuses() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyAllStats(player);
        }
    }

    // ═══════════════════════════════════════
    // P20: Double crop drops (10% chance)
    // P30: Auto-smelt iron/gold/copper
    // ═══════════════════════════════════════
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockDrop(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        int prestige = getPrestige(player);
        if (prestige <= 0) return;

        Material blockType = event.getBlockState().getType();

        // P30+: Auto-smelt ores
        if (prestige >= 30) {
            for (Item item : event.getItems()) {
                ItemStack stack = item.getItemStack();
                Material smelted = getSmeltedVersion(stack.getType());
                if (smelted != null) {
                    stack.setType(smelted);
                    item.setItemStack(stack);
                }
            }
        }

        // P20+: Double crop drops (10% chance)
        if (prestige >= 20 && isCrop(blockType)) {
            if (Math.random() < 0.10) {
                for (Item item : event.getItems()) {
                    ItemStack stack = item.getItemStack();
                    stack.setAmount(stack.getAmount() * 2);
                    item.setItemStack(stack);
                }
                player.sendMessage("§a§l✦ §f¡Cosecha doble! §7(Prestigio P20+)");
            }
        }
    }

    // ═══════════════════════════════════════
    // P60: Double XP from mob kills
    // ═══════════════════════════════════════
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        if (getPrestige(killer) >= 60) {
            event.setDroppedExp(event.getDroppedExp() * 2);
        }
    }

    // ═══════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════

    private Material getSmeltedVersion(Material raw) {
        return switch (raw) {
            case RAW_IRON -> Material.IRON_INGOT;
            case RAW_GOLD -> Material.GOLD_INGOT;
            case RAW_COPPER -> Material.COPPER_INGOT;
            default -> null;
        };
    }

    private boolean isCrop(Material mat) {
        return mat == Material.WHEAT || mat == Material.CARROTS ||
               mat == Material.POTATOES || mat == Material.BEETROOTS ||
               mat == Material.NETHER_WART || mat == Material.MELON ||
               mat == Material.PUMPKIN || mat == Material.COCOA ||
               mat == Material.SUGAR_CANE;
    }

    public void cleanup(UUID uid) {
        lastDamageTime.remove(uid);
    }
}
