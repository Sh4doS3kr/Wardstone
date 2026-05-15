package com.moonlight.coreprotect.core;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

/**
 * Manages the Core Prestige system.
 * 
 * When a core reaches level 24 (max), the owner can prestige it:
 * - Resets core level to 1
 * - Grants permanent bonuses based on prestige level
 * - Adds exclusive particle effects around the core
 * 
 * Prestige Levels:
 * - Prestige I:   +5% zone size, silver aura
 * - Prestige II:  +10% zone size, golden aura
 * - Prestige III: +15% zone size, diamond aura + special effects
 */
public class CorePrestigeManager {

    private final CoreProtectPlugin plugin;
    public static final int MAX_PRESTIGE = 3;
    public static final int PRESTIGE_REQUIRED_LEVEL = 20;

    // Prestige costs (in-game currency)
    private static final double[] PRESTIGE_COSTS = {
            500_000,   // Prestige I
            1_500_000, // Prestige II
            5_000_000  // Prestige III
    };

    public CorePrestigeManager(CoreProtectPlugin plugin) {
        this.plugin = plugin;

        // Start particle effect task for prestiged cores
        startPrestigeParticleTask();
    }

    /**
     * Checks if a region can be prestiged.
     */
    public boolean canPrestige(ProtectedRegion region) {
        return region.getLevel() >= PRESTIGE_REQUIRED_LEVEL
                && region.getPrestige() < MAX_PRESTIGE;
    }

    /**
     * Gets the cost for the next prestige level.
     */
    public double getPrestigeCost(int currentPrestige) {
        if (currentPrestige >= MAX_PRESTIGE) return -1;
        return PRESTIGE_COSTS[currentPrestige];
    }

    /**
     * Gets the display name for a prestige level.
     */
    public static String getPrestigeName(int prestige) {
        switch (prestige) {
            case 1: return "§7§lPrestigio I";
            case 2: return "§6§lPrestigio II";
            case 3: return "§b§lPrestigio III";
            default: return "§fSin Prestigio";
        }
    }

    /**
     * Gets the color associated with a prestige level.
     */
    public static ChatColor getPrestigeColor(int prestige) {
        switch (prestige) {
            case 1: return ChatColor.GRAY;
            case 2: return ChatColor.GOLD;
            case 3: return ChatColor.AQUA;
            default: return ChatColor.WHITE;
        }
    }

    /**
     * Gets a description of the prestige bonus.
     */
    public static String getPrestigeBonus(int prestige) {
        switch (prestige) {
            case 1: return "§7+5% zona + aura plateada + §4Núcleos Infernal/Abismal";
            case 2: return "§6+10% zona + aura dorada + §eNúcleos Celestial/Atlantis";
            case 3: return "§b+15% zona + aura diamante + §dNúcleos Dragon/Divino";
            default: return "§7Ninguno";
        }
    }

    /**
     * Performs the prestige operation.
     */
    public boolean performPrestige(Player player, ProtectedRegion region) {
        if (!canPrestige(region)) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cEste núcleo no puede prestigiarse."));
            return false;
        }

        if (!region.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cSolo el dueño puede prestigiar el núcleo."));
            return false;
        }

        int nextPrestige = region.getPrestige() + 1;
        double cost = getPrestigeCost(region.getPrestige());

        if (plugin.getEconomy() == null || !plugin.getEconomy().has(player, cost)) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNecesitas §6$" + formatPrice(cost) + " §cpara prestigiar."));
            return false;
        }

        // Withdraw money
        plugin.getEconomy().withdrawPlayer(player, cost);

        // Reset level to 1
        CoreLevel level1 = CoreLevel.fromConfig(plugin.getConfig(), 1);
        if (level1 == null) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cError: no se encontró el nivel 1 en la config."));
            return false;
        }

        // Apply prestige
        region.setPrestige(nextPrestige);
        region.setLevel(1);
        region.setSize(level1.getSize());

        // Update core block
        Location coreLoc = region.getCoreLocation();
        if (coreLoc != null && coreLoc.getWorld() != null) {
            coreLoc.getBlock().setType(level1.getMaterial());
        }

        // Save data
        plugin.getDataManager().saveData();

        // Update BlueMap
        if (plugin.getBlueMapIntegration() != null) {
            plugin.getBlueMapIntegration().updateAllMarkers();
        }

        // Play prestige animation
        playPrestigeAnimation(player, coreLoc, nextPrestige);

        // Announce
        String prestigeName = getPrestigeName(nextPrestige);
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§a§l✦ ¡PRESTIGIO COMPLETADO! ✦"));
        player.sendMessage(SmallCaps.convert("§7  Nuevo prestigio: " + prestigeName));
        player.sendMessage(SmallCaps.convert("§7  Bonus: " + getPrestigeBonus(nextPrestige)));
        player.sendMessage(SmallCaps.convert("§7  Nivel reseteado a §f1§7. ¡Evoluciona de nuevo!"));
        player.sendMessage("");

        Bukkit.broadcastMessage(SmallCaps.convert("§a§l✦ §f" + player.getName() + " §7ha prestigiado su núcleo a " + prestigeName + "§7!"));

        return true;
    }

    /**
     * Plays the prestige animation.
     */
    private void playPrestigeAnimation(Player player, Location coreLoc, int prestige) {
        if (coreLoc == null || coreLoc.getWorld() == null) return;

        World world = coreLoc.getWorld();
        Location center = coreLoc.clone().add(0.5, 0.5, 0.5);

        // Color based on prestige level
        Color particleColor;
        switch (prestige) {
            case 1: particleColor = Color.fromRGB(192, 192, 192); break; // Silver
            case 2: particleColor = Color.fromRGB(255, 215, 0); break;   // Gold
            case 3: particleColor = Color.fromRGB(100, 200, 255); break;  // Diamond
            default: particleColor = Color.WHITE; break;
        }

        final Color finalColor = particleColor;

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 100) {
                    cancel();

                    // Final burst
                    world.spawnParticle(Particle.TOTEM_OF_UNDYING, center, 200, 2, 3, 2, 1.0);
                    world.spawnParticle(Particle.END_ROD, center, 100, 2, 3, 2, 0.3);
                    world.playSound(center, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                    world.playSound(center, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.5f);

                    player.sendTitle(
                            SmallCaps.convert(getPrestigeColor(prestige) + "§l✦ " + getPrestigeName(prestige) + " ✦"),
                            SmallCaps.convert("§7Tu núcleo ha sido prestigiado"),
                            10, 80, 20);
                    return;
                }

                double progress = ticks / 100.0;
                double angle = ticks * 0.2;
                double radius = 3.0 - progress * 2.5;
                double y = progress * 5;

                // Spiral
                for (int i = 0; i < 3; i++) {
                    double a = angle + (Math.PI * 2 / 3) * i;
                    Location p = center.clone().add(Math.cos(a) * radius, y * 0.3, Math.sin(a) * radius);
                    world.spawnParticle(Particle.DUST, p, 2, 0.05, 0.05, 0.05, 0,
                            new Particle.DustOptions(finalColor, 2.0f));
                }

                // Rising stars
                if (ticks % 5 == 0) {
                    world.spawnParticle(Particle.END_ROD, center.clone().add(0, y * 0.3, 0), 3, 0.5, 0.3, 0.5, 0.05);
                }

                // Sound
                if (ticks % 10 == 0) {
                    world.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 0.8f + (float) progress);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    /**
     * Starts the recurring particle effect task for prestiged cores.
     */
    private void startPrestigeParticleTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (plugin.getProtectionManager() == null) return;

                for (ProtectedRegion region : plugin.getProtectionManager().getAllRegions()) {
                    if (region.getPrestige() <= 0) continue;

                    Location coreLoc = region.getCoreLocation();
                    if (coreLoc == null || coreLoc.getWorld() == null) continue;
                    if (!coreLoc.getWorld().isChunkLoaded(coreLoc.getBlockX() >> 4, coreLoc.getBlockZ() >> 4)) continue;

                    // Only render if players are nearby (performance)
                    boolean hasNearbyPlayer = false;
                    for (Player p : coreLoc.getWorld().getPlayers()) {
                        if (p.getLocation().distanceSquared(coreLoc) < 2500) { // 50 blocks
                            hasNearbyPlayer = true;
                            break;
                        }
                    }
                    if (!hasNearbyPlayer) continue;

                    Location center = coreLoc.clone().add(0.5, 0.5, 0.5);
                    World world = center.getWorld();
                    int prestige = region.getPrestige();
                    long tick = world.getFullTime();

                    switch (prestige) {
                        case 1: // Silver aura - gentle floating particles
                            if (tick % 4 == 0) {
                                double angle = tick * 0.05;
                                Location p = center.clone().add(Math.cos(angle) * 1.5, Math.sin(tick * 0.03) * 0.5, Math.sin(angle) * 1.5);
                                world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0,
                                        new Particle.DustOptions(Color.fromRGB(192, 192, 192), 1.2f));
                            }
                            break;

                        case 2: // Golden aura - orbiting sparks
                            if (tick % 3 == 0) {
                                double angle = tick * 0.08;
                                for (int i = 0; i < 2; i++) {
                                    double a = angle + Math.PI * i;
                                    Location p = center.clone().add(Math.cos(a) * 2, Math.sin(tick * 0.04) * 0.8, Math.sin(a) * 2);
                                    world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0,
                                            new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.5f));
                                }
                                if (tick % 10 == 0) {
                                    world.spawnParticle(Particle.END_ROD, center.clone().add(0, 1.5, 0), 1, 0.5, 0.3, 0.5, 0.02);
                                }
                            }
                            break;

                        case 3: // Diamond aura - triple helix + sparkles
                            if (tick % 2 == 0) {
                                double angle = tick * 0.1;
                                for (int i = 0; i < 3; i++) {
                                    double a = angle + (Math.PI * 2 / 3) * i;
                                    double y = Math.sin(tick * 0.05 + i) * 1.0;
                                    Location p = center.clone().add(Math.cos(a) * 2.5, y + 0.5, Math.sin(a) * 2.5);
                                    world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0,
                                            new Particle.DustOptions(Color.fromRGB(100, 200, 255), 1.8f));
                                }
                                if (tick % 6 == 0) {
                                    world.spawnParticle(Particle.END_ROD, center.clone().add(0, 2, 0), 2, 1, 0.5, 1, 0.03);
                                    world.spawnParticle(Particle.FIREWORK, center.clone().add(0, 1, 0), 1, 0.8, 0.3, 0.8, 0.01);
                                }
                            }
                            break;
                    }
                }
            }
        }.runTaskTimer(plugin, 100L, 2L); // Every 2 ticks (10 times/sec)
    }

    private static String formatPrice(double price) {
        if (price >= 1_000_000) return String.format("%.1fM", price / 1_000_000);
        if (price >= 1_000) return String.format("%.1fK", price / 1_000);
        return String.valueOf((int) price);
    }
}
