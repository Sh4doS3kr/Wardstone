package com.moonlight.coreprotect.achievements;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.core.ProtectedRegion;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.*;

public class AchievementListener implements Listener {

    private final CoreProtectPlugin plugin;
    private final Set<UUID> visitTracked = new HashSet<>();

    public AchievementListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        AchievementManager am = plugin.getAchievementManager();

        ProtectedRegion region = plugin.getProtectionManager().getRegionAt(player.getLocation());
        if (region == null) return;

        // "Turista Curioso" - visit another player's zone
        if (!region.getOwner().equals(playerId) && !visitTracked.contains(playerId)) {
            visitTracked.add(playerId);
            am.grant(player, "visit_other");
        }
    }

    // Called externally when a core is placed
    public void onCorePlaced(Player player, ProtectedRegion region) {
        AchievementManager am = plugin.getAchievementManager();

        // Count player cores
        int coreCount = plugin.getProtectionManager().getRegionsByOwner(player.getUniqueId()).size();

        am.grant(player, "first_core");
        if (coreCount >= 2) am.grant(player, "2_cores");
        if (coreCount >= 3) am.grant(player, "3_cores");
        if (coreCount >= 5) am.grant(player, "5_cores");

        // Night placement
        long time = player.getWorld().getTime();
        if (time >= 13000 && time <= 23000) {
            am.grant(player, "night_place");
        }

        // Rain placement
        if (player.getWorld().hasStorm()) {
            am.grant(player, "rain_place");
        }

        // Nether / End placement
        String worldName = player.getWorld().getName().toLowerCase();
        if (worldName.contains("nether")) {
            am.grant(player, "nether_core");
        }
        if (worldName.contains("the_end") || worldName.contains("end")) {
            am.grant(player, "end_core");
        }
    }

    // Called when a core is upgraded
    public void onCoreUpgraded(Player player, ProtectedRegion region) {
        AchievementManager am = plugin.getAchievementManager();
        int level = region.getLevel();

        if (level >= 5) am.grant(player, "level_5");
        if (level >= 10) am.grant(player, "level_10");
        if (level >= 15) am.grant(player, "level_15");
        if (level >= 20) am.grant(player, "level_20");

        // Zone size achievements
        if (region.getSize() >= 80) am.grant(player, "big_zone");
        if (region.getSize() >= 120) am.grant(player, "huge_zone");
        if (region.getSize() >= 160) am.grant(player, "max_zone");
    }

    // Called when an upgrade is purchased
    public void onUpgradePurchased(Player player, ProtectedRegion region, String upgradeId, double price) {
        AchievementManager am = plugin.getAchievementManager();

        // First upgrade
        am.grant(player, "first_upgrade");

        // Track spending and upgrade count
        am.incrementStat(player.getUniqueId(), "upgrades_bought");
        int totalUpgrades = am.getPlayerStat(player.getUniqueId(), "upgrades_bought");
        if (totalUpgrades >= 5) am.grant(player, "5_upgrades");
        if (totalUpgrades >= 10) am.grant(player, "10_upgrades");

        // Track spending
        double prevSpend = am.getPlayerStat(player.getUniqueId(), "total_spent");
        am.setPlayerStat(player.getUniqueId(), "total_spent", (int)(prevSpend + price));
        double totalSpent = prevSpend + price;
        if (totalSpent >= 100000) am.grant(player, "spend_100k");
        if (totalSpent >= 500000) am.grant(player, "spend_500k");
        if (totalSpent >= 1000000) am.grant(player, "spend_1m");

        // Specific upgrade achievements
        switch (upgradeId) {
            case "noExplosion": am.grant(player, "buy_no_explosion"); break;
            case "noPvP": am.grant(player, "buy_no_pvp"); break;
            case "noMobSpawn": am.grant(player, "buy_no_mobs"); break;
            case "noFallDamage": am.grant(player, "buy_no_fall"); break;
            case "autoHeal": am.grant(player, "buy_auto_heal"); break;
            case "speedBoost": am.grant(player, "buy_speed"); break;
            case "noHunger": am.grant(player, "buy_no_hunger"); break;
            case "antiEnderman": am.grant(player, "buy_anti_enderman"); break;
            case "fixedTime": am.grant(player, "buy_fixed_time"); break;
            case "coreTeleport": am.grant(player, "buy_teleport"); break;
            case "resourceGen": am.grant(player, "buy_resource_gen"); break;
        }

        // Damage/Health max
        if (region.getDamageBoostLevel() >= 5) am.grant(player, "damage_max");
        if (region.getHealthBoostLevel() >= 5) am.grant(player, "health_max");
        if (region.getDamageBoostLevel() >= 5 && region.getHealthBoostLevel() >= 5) {
            am.grant(player, "both_max");
        }

        // All upgrades
        if (region.getActiveUpgradeCount() >= 13) {
            am.grant(player, "all_upgrades");
        }

        am.savePlayerData();
    }

    // Called when core is broken by owner
    public void onCoreBreak(Player player) {
        plugin.getAchievementManager().grant(player, "break_own_core");
    }

    // Called when a core with upgrades is placed (moved)
    public void onCoreMoved(Player player) {
        plugin.getAchievementManager().grant(player, "move_core");
    }

    // Called when sethome is used
    public void onSetHome(Player player) {
        plugin.getAchievementManager().grant(player, "sethome");
    }

    // Called when /cores tp is used
    public void onUseTp(Player player) {
        plugin.getAchievementManager().grant(player, "use_tp");
    }

    // Called when a member is invited
    public void onMemberAdded(Player player, ProtectedRegion region) {
        AchievementManager am = plugin.getAchievementManager();
        am.grant(player, "first_member");
        int memberCount = region.getMembers().size();
        if (memberCount >= 5) am.grant(player, "5_members");
        if (memberCount >= 10) am.grant(player, "10_members");
    }

    // Called when trying to invite an existing member
    public void onAlreadyMember(Player player) {
        plugin.getAchievementManager().grant(player, "invite_rejected");
    }
}
