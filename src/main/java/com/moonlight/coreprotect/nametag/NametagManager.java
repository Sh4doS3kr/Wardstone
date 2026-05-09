package com.moonlight.coreprotect.nametag;

import com.moonlight.coreprotect.CoreProtectPlugin;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.*;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NametagManager implements Listener {

    private final CoreProtectPlugin plugin;
    private final Map<UUID, TextDisplay> tags = new HashMap<>();
    private int tick = 0;

    // Multi-color gradients by tier (basic §codes that TextDisplay renders)
    private static final String[][] TIER_GRADIENT = {
        {"§6", "§e", "§f"},         // Copper (P1-10): gold → yellow → white
        {"§f", "§7", "§f"},         // Iron (P11-20): white → gray → white
        {"§e", "§6", "§e"},         // Gold (P21-30): yellow → gold → yellow
        {"§9", "§b", "§9"},         // Lapis (P31-40): blue → aqua → blue
        {"§a", "§2", "§a"},         // Emerald (P41-50): green → dark_green → green
        {"§b", "§3", "§b"},         // Diamond (P51-60): aqua → dark_aqua → aqua
        {"§d", "§5", "§d"},         // Amethyst (P61-70): pink → purple → pink
    };

    // Rainbow cycle for max rank (P70)
    private static final String[] RAINBOW = {"§c", "§6", "§e", "§a", "§b", "§9", "§d"};

    public NametagManager(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Clean orphaned entities from crashes/restarts
        for (World w : Bukkit.getWorlds()) {
            for (TextDisplay td : w.getEntitiesByClass(TextDisplay.class)) {
                if (td.getScoreboardTags().contains("ws_nametag")) td.remove();
            }
        }

        hideAllVanillaNametags();
        for (Player p : Bukkit.getOnlinePlayers()) createTag(p);

        // Main loop: teleport every tick for smooth tracking, text every 2s
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 0, 1);
    }

    // ===========================
    // VANILLA NAMETAG HIDING
    // ===========================

    private void hideAllVanillaNametags() {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();

        // Set NEVER + clear prefix/suffix on whatever team each player is in (including LuckPerms teams)
        for (Player p : Bukkit.getOnlinePlayers()) {
            Team t = sb.getEntryTeam(p.getName());
            if (t != null) {
                t.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
                t.setPrefix("");
                t.setSuffix("");
            } else {
                Team ws = sb.getTeam("ws_nametag");
                if (ws == null) {
                    ws = sb.registerNewTeam("ws_nametag");
                    ws.setAllowFriendlyFire(true);
                    ws.setCanSeeFriendlyInvisibles(false);
                }
                ws.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
                ws.setPrefix("");
                ws.setSuffix("");
                ws.addEntry(p.getName());
            }
        }
    }

    // ===========================
    // UPDATE LOOP
    // ===========================

    private void updateAll() {
        tick++;
        boolean updateText = (tick % 40 == 0);

        // Re-enforce hidden vanilla nametags every 0.5s (overrides LuckPerms)
        if (tick % 10 == 0) hideAllVanillaNametags();

        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID uuid = p.getUniqueId();
            TextDisplay td = tags.get(uuid);

            if (td == null || !td.isValid() || td.getWorld() != p.getWorld()) {
                createTag(p);
                td = tags.get(uuid);
            }
            if (td == null) continue;

            td.teleport(p.getLocation().add(0, 2.3, 0));
            if (updateText) td.setText(buildText(p));
        }
    }

    // ===========================
    // CREATE / REMOVE
    // ===========================

    public void createTag(Player p) {
        removeTag(p.getUniqueId());

        // Ensure player's vanilla nametag is hidden + no prefix/suffix bleed
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team t = sb.getEntryTeam(p.getName());
        if (t != null) {
            t.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
            t.setPrefix("");
            t.setSuffix("");
        } else {
            Team ws = sb.getTeam("ws_nametag");
            if (ws == null) {
                ws = sb.registerNewTeam("ws_nametag");
                ws.setAllowFriendlyFire(true);
            }
            ws.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
            ws.setPrefix("");
            ws.setSuffix("");
            ws.addEntry(p.getName());
        }

        String text = buildText(p);

        TextDisplay td = p.getWorld().spawn(p.getLocation().add(0, 2.3, 0), TextDisplay.class, d -> {
            d.setText(text);
            d.setBillboard(Display.Billboard.CENTER);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setShadowed(true);
            d.setSeeThrough(false);
            d.setPersistent(false);
            d.setTeleportDuration(3);
            d.addScoreboardTag("ws_nametag");
        });

        tags.put(p.getUniqueId(), td);

        // Hide nametag from the player themselves
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && td.isValid()) p.hideEntity(plugin, td);
        }, 2);
    }

    public void removeTag(UUID uuid) {
        TextDisplay td = tags.remove(uuid);
        if (td != null && td.isValid()) td.remove();
    }

    // ===========================
    // EVENTS (HIGHEST PRIORITY)
    // ===========================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (e.getPlayer().isOnline()) {
                hideAllVanillaNametags();
                createTag(e.getPlayer());
            }
        }, 5);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent e) {
        removeTag(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (e.getPlayer().isOnline()) createTag(e.getPlayer());
        }, 3);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (e.getPlayer().isOnline()) createTag(e.getPlayer());
        }, 5);
    }

    // ===========================
    // TEXT BUILDING
    // ===========================

    private String buildText(Player p) {
        UUID uuid = p.getUniqueId();

        // ── Line 1: LuckPerms rank ──
        String prefix = "";
        try { prefix = PlaceholderAPI.setPlaceholders(p, "%luckperms_prefix%"); }
        catch (Exception ignored) {}
        if (prefix == null || prefix.isEmpty() || prefix.contains("%")) prefix = "§7Jugador";
        prefix = stripHexCodes(prefix);

        // ── Line 2: Prestige | Name ──
        int prestige = 0;
        String prestigeColor = "§7";
        String prestigeTag = "§8N/A";
        if (plugin.getPrestigeManager() != null) {
            prestige = plugin.getPrestigeManager().getPrestige(uuid);
            prestigeColor = plugin.getPrestigeManager().getColor(uuid);
            if (prestige > 0) {
                prestigeTag = stripHexCodes(plugin.getPrestigeManager().getTitle(uuid));
            }
        }

        String formattedName = formatName(p.getName(), prestige, prestigeColor);

        // ── Line 3: ❤ HP | ⏰ Playtime | $ Money ──
        int hp = (int) Math.ceil(p.getHealth());
        long playSec = plugin.getPlayTimeRewardsManager() != null
            ? plugin.getPlayTimeRewardsManager().getPlayerTotalPlayTime(uuid) : 0;
        double balance = plugin.getEconomy() != null ? plugin.getEconomy().getBalance(p) : 0;

        return prefix + "\n"
             + prestigeTag + " §7| " + formattedName + "\n"
             + "§c❤ §f" + hp + " §8| §b⏰ §f" + formatTime(playSec) + " §8| §a$ §f" + formatMoney(balance);
    }

    // ===========================
    // NAME FORMATTING & GRADIENTS
    // ===========================

    private String formatName(String name, int prestige, String color) {
        if (prestige <= 0) return "§f" + name;
        if (prestige == 70) return rainbowName(name); // Max rank = rainbow

        // Determine tier (0=Copper, 1=Iron, ..., 6=Amethyst)
        int tierIdx = Math.min((prestige - 1) / 10, TIER_GRADIENT.length - 1);

        // Tiers above Copper (P11+) get gradient + bold
        if (prestige > 10) {
            return multiColorName(name, TIER_GRADIENT[tierIdx], true);
        }
        // Copper tier: simple color
        return color + name;
    }

    private String multiColorName(String text, String[] colors, boolean bold) {
        if (text.isEmpty()) return text;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            float pos = text.length() == 1 ? 0f : (float) i / (text.length() - 1);
            int colorIdx = Math.min((int) (pos * colors.length), colors.length - 1);
            sb.append(colors[colorIdx]);
            if (bold) sb.append("§l");
            sb.append(text.charAt(i));
        }
        return sb.toString();
    }

    private String rainbowName(String text) {
        if (text.isEmpty()) return text;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            sb.append(RAINBOW[i % RAINBOW.length]).append("§l").append(text.charAt(i));
        }
        return sb.toString();
    }

    // ===========================
    // UTILS
    // ===========================

    private String stripHexCodes(String text) {
        // Remove §x§R§R§G§G§B§B patterns that TextDisplay can't render
        return text.replaceAll("§x(§[0-9a-fA-F]){6}", "");
    }

    private String formatTime(long sec) {
        if (sec < 3600) return (sec / 60) + "m";
        long h = sec / 3600;
        return h < 100 ? h + "h" : (h / 24) + "d";
    }

    private String formatMoney(double amt) {
        if (amt >= 1_000_000) return String.format("%.1fM", amt / 1_000_000);
        if (amt >= 1_000) return String.format("%.1fK", amt / 1_000);
        return String.format("%.0f", amt);
    }

    // ===========================
    // SHUTDOWN
    // ===========================

    public void shutdown() {
        for (TextDisplay td : tags.values()) {
            if (td != null && td.isValid()) td.remove();
        }
        tags.clear();

        for (World w : Bukkit.getWorlds()) {
            for (TextDisplay td : w.getEntitiesByClass(TextDisplay.class)) {
                if (td.getScoreboardTags().contains("ws_nametag")) td.remove();
            }
        }
    }
}
