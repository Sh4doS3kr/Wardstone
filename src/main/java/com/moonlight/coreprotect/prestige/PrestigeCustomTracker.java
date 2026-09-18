package com.moonlight.coreprotect.prestige;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.UUID;

/**
 * Tracks custom prestige mission stats:
 * - Killstreak (best PvP killstreak in a single life)
 * - Prestige kills (killing players with higher prestige)
 * - Biome discovery (unique biomes visited)
 */
public class PrestigeCustomTracker implements Listener {

    private final CoreProtectPlugin plugin;

    public PrestigeCustomTracker(CoreProtectPlugin plugin) {
        this.plugin = plugin;

        // Periodic biome check every 10 seconds (200 ticks)
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkBiomes, 200L, 200L);
    }

    // ═══════════════════════════════════════
    // DONATION TRACKING (/pay)
    // ═══════════════════════════════════════

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPayCommand(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled()) return;
        String msg = event.getMessage().toLowerCase();
        if (!msg.startsWith("/pay ")) return;

        String[] parts = msg.split("\\s+");
        if (parts.length < 3) return;

        Player sender = event.getPlayer();
        if (plugin.getEconomy() == null) return;

        double balanceBefore = plugin.getEconomy().getBalance(sender);

        // Check after 2 ticks if balance actually decreased (command succeeded)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!sender.isOnline()) return;
            double balanceAfter = plugin.getEconomy().getBalance(sender);
            double donated = balanceBefore - balanceAfter;
            if (donated > 0) {
                PrestigeManager mgr = plugin.getPrestigeManager();
                if (mgr == null) return;
                PrestigeManager.PrestigeData pd = mgr.getData(sender.getUniqueId());
                pd.totalDonated += (long) donated;
                sender.sendMessage(SmallCaps.convert("\u00a7a\u00a7l\u2726 \u00a7fDonaci\u00f3n registrada: \u00a7e$" + String.format("%,.0f", donated) + " \u00a77(Total: $" + String.format("%,.0f", (double) pd.totalDonated) + ")"));
            }
        }, 3L);
    }

    // ═══════════════════════════════════════
    // KILLSTREAK + PRESTIGE KILLS
    // ═══════════════════════════════════════

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPvPKill(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        PrestigeManager mgr = plugin.getPrestigeManager();
        if (mgr == null) return;

        UUID killerUuid = killer.getUniqueId();
        PrestigeManager.PrestigeData killerData = mgr.getData(killerUuid);

        // Increment killstreak
        killerData.currentKillstreak++;
        if (killerData.currentKillstreak > killerData.bestKillstreak) {
            killerData.bestKillstreak = killerData.currentKillstreak;
            if (killerData.currentKillstreak >= 3 && killerData.currentKillstreak % 3 == 0) {
                killer.sendMessage(SmallCaps.convert("§c§l🔥 §fKillstreak: §e" + killerData.currentKillstreak + " kills! §7(Mejor: " + killerData.bestKillstreak + ")"));
            }
        }

        // Check if victim has higher prestige
        PrestigeManager.PrestigeData victimData = mgr.getData(victim.getUniqueId());
        if (victimData.prestige > killerData.prestige) {
            killerData.prestigeKills++;
            killer.sendMessage(SmallCaps.convert("§6§l⚔ §f¡Has matado a un jugador de mayor prestigio! §7(" + killerData.prestigeKills + " total)"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        PrestigeManager mgr = plugin.getPrestigeManager();
        if (mgr == null) return;

        // Reset killstreak on death
        PrestigeManager.PrestigeData pd = mgr.getData(event.getEntity().getUniqueId());
        if (pd.currentKillstreak >= 3) {
            event.getEntity().sendMessage(SmallCaps.convert("§c§l✘ §fKillstreak de §e" + pd.currentKillstreak + " §fperdido."));
        }
        pd.currentKillstreak = 0;
    }

    // ═══════════════════════════════════════
    // BIOME DISCOVERY
    // ═══════════════════════════════════════

    private void checkBiomes() {
        PrestigeManager mgr = plugin.getPrestigeManager();
        if (mgr == null) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            PrestigeManager.PrestigeData pd = mgr.getData(player.getUniqueId());
            if (pd.prestige <= 0) continue;

            String biome = player.getLocation().getBlock().getBiome().name();
            if (pd.discoveredBiomes.add(biome)) {
                player.sendMessage(SmallCaps.convert("§a§l🌍 §fNuevo bioma descubierto: §e" + formatBiomeName(biome) + " §7(" + pd.discoveredBiomes.size() + " total)"));
            }
        }
    }

    private String formatBiomeName(String biome) {
        String[] parts = biome.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(part.substring(0, 1).toUpperCase()).append(part.substring(1));
        }
        return sb.toString();
    }
}
