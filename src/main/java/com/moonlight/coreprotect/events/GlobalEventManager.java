package com.moonlight.coreprotect.events;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Random global server events every 30 minutes, lasting 2m30s.
 * Displayed via BossBar with animated countdown.
 */
public class GlobalEventManager implements Listener {

    private final CoreProtectPlugin plugin;
    private BossBar bossBar;
    private BukkitTask eventTimer;
    private BukkitTask countdownTask;
    private EventType activeEvent = null;
    private long eventEndTime = 0;

    private static final long EVENT_INTERVAL_TICKS = 50 * 60 * 20;  // 50 minutes
    private static final long EVENT_DURATION_TICKS = 150 * 20;       // 2 minutes 30 seconds
    private static final long EVENT_DURATION_MS = 150 * 1000;

    // ═══════════════════════════════════════════════════════════════
    //  EVENT TYPES
    // ═══════════════════════════════════════════════════════════════

    public enum EventType {
        DOUBLE_MONEY("§e§l💰 x2 DINERO", "§eTodo el dinero ganado se duplica", BarColor.YELLOW,
                Sound.ENTITY_EXPERIENCE_ORB_PICKUP, "§e💰"),
        DOUBLE_ESSENCES("§d§l✦ x2 ESENCIAS", "§dTodas las esencias obtenidas se duplican", BarColor.PINK,
                Sound.BLOCK_AMETHYST_BLOCK_RESONATE, "§d✦"),
        DOUBLE_XP("§a§l⚡ x2 EXPERIENCIA", "§aToda la XP ganada se duplica", BarColor.GREEN,
                Sound.ENTITY_PLAYER_LEVELUP, "§a⚡"),
        LUCKY_MINING("§b§l⛏ MINERÍA AFORTUNADA", "§bMayor probabilidad de diamantes y esmeraldas al minar", BarColor.BLUE,
                Sound.BLOCK_AMETHYST_CLUSTER_BREAK, "§b⛏"),
        MOB_GOLD_RUSH("§6§l☠ FIEBRE DEL ORO", "§6Los mobs sueltan dinero extra al morir", BarColor.YELLOW,
                Sound.ENTITY_ZOMBIE_VILLAGER_CURE, "§6☠"),
        SPEED_BOOST("§f§l💨 VELOCIDAD", "§fTodos los jugadores obtienen Speed II", BarColor.WHITE,
                Sound.ENTITY_HORSE_GALLOP, "§f💨"),
        REGENERATION("§c§l❤ REGENERACIÓN", "§cTodos los jugadores obtienen Regeneración I", BarColor.RED,
                Sound.ENTITY_GENERIC_DRINK, "§c❤"),
        HASTE_FURY("§6§l🔥 FURIA MINERA", "§6Todos obtienen Haste II — ¡a picar!", BarColor.YELLOW,
                Sound.BLOCK_BEACON_ACTIVATE, "§6🔥"),
        NIGHT_VISION("§9§l👁 VISIÓN NOCTURNA", "§9Todos ven en la oscuridad", BarColor.PURPLE,
                Sound.BLOCK_BEACON_AMBIENT, "§9👁"),
        JUMP_BOOST("§a§l🐇 SALTO POTENCIADO", "§aTodos saltan más alto", BarColor.GREEN,
                Sound.ENTITY_RABBIT_JUMP, "§a🐇"),
        STRENGTH("§4§l⚔ FUERZA BRUTA", "§4Todos obtienen Strength I — ¡a pelear!", BarColor.RED,
                Sound.ENTITY_ENDER_DRAGON_GROWL, "§4⚔"),
        RESISTANCE("§8§l🛡 PIEL DE HIERRO", "§8Todos obtienen Resistance I", BarColor.WHITE,
                Sound.ITEM_ARMOR_EQUIP_NETHERITE, "§8🛡");

        final String displayName;
        final String description;
        final BarColor barColor;
        final Sound startSound;
        final String icon;

        EventType(String displayName, String description, BarColor barColor, Sound startSound, String icon) {
            this.displayName = displayName;
            this.description = description;
            this.barColor = barColor;
            this.startSound = startSound;
            this.icon = icon;
        }

        public String getDisplayName() { return displayName; }
    }

    private static final EventType[] ALL_EVENTS = EventType.values();
    private EventType lastEvent = null;

    public GlobalEventManager(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        startScheduler();
    }

    // ═══════════════════════════════════════════════════════════════
    //  SCHEDULER
    // ═══════════════════════════════════════════════════════════════

    private void startScheduler() {
        // First event 5 min after startup, then every 30 min
        eventTimer = new BukkitRunnable() {
            boolean first = true;
            @Override
            public void run() {
                if (Bukkit.getOnlinePlayers().isEmpty()) return;
                startRandomEvent();
            }
        }.runTaskTimer(plugin, 5 * 60 * 20, EVENT_INTERVAL_TICKS);
    }

    private void startRandomEvent() {
        // Pick random event (avoid repeating last)
        EventType chosen;
        do {
            chosen = ALL_EVENTS[new Random().nextInt(ALL_EVENTS.length)];
        } while (chosen == lastEvent && ALL_EVENTS.length > 1);
        lastEvent = chosen;

        startEvent(chosen);
    }

    /**
     * Force-start a specific event (admin command).
     * If an event is already active, it ends first.
     */
    public void forceStartEvent(EventType type) {
        if (activeEvent != null) {
            endEvent();
        }
        lastEvent = type;
        startEvent(type);
    }

    // ═══════════════════════════════════════════════════════════════
    //  EVENT LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    private void startEvent(EventType event) {
        activeEvent = event;
        eventEndTime = System.currentTimeMillis() + EVENT_DURATION_MS;

        // ── Countdown announcement (5 seconds before) ──
        announceCountdown(event);
    }

    private void announceCountdown(EventType event) {
        // 5-second dramatic countdown
        new BukkitRunnable() {
            int count = 5;
            @Override
            public void run() {
                if (count <= 0) {
                    cancel();
                    activateEvent(event);
                    return;
                }

                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendTitle("§6§l" + count, SmallCaps.convert("§7Evento global en..."), 0, 25, 5);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f + (5 - count) * 0.2f);
                }
                count--;
            }
        }.runTaskTimer(plugin, 0, 20L);
    }

    private void activateEvent(EventType event) {
        // ── Create BossBar ──
        bossBar = Bukkit.createBossBar(
                event.displayName + " §8— §72:30",
                event.barColor,
                BarStyle.SEGMENTED_10
        );
        bossBar.setProgress(1.0);

        // Add all online players
        for (Player p : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(p);
        }

        // ── Broadcast start ──
        String border = "§8§m                                                            ";
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage("");
            p.sendMessage(border);
            p.sendMessage("");
            p.sendMessage("  " + event.displayName);
            p.sendMessage("  " + event.description);
            p.sendMessage("  §7Duración: §f2 minutos 30 segundos");
            p.sendMessage("");
            p.sendMessage(border);
            p.sendMessage("");

            p.sendTitle(event.icon + " " + event.displayName + " " + event.icon,
                    event.description, 10, 70, 20);
            p.playSound(p.getLocation(), event.startSound, 1f, 1.0f);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.5f);
            p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.8f, 1.2f);
        }

        // ── Apply potion effects if applicable ──
        applyEffects(event);

        // ── Countdown timer (updates boss bar every second) ──
        countdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                long remaining = eventEndTime - System.currentTimeMillis();
                if (remaining <= 0 || activeEvent == null) {
                    cancel();
                    endEvent();
                    return;
                }

                double progress = (double) remaining / EVENT_DURATION_MS;
                bossBar.setProgress(Math.max(0, Math.min(1, progress)));

                int seconds = (int) (remaining / 1000);
                int min = seconds / 60;
                int sec = seconds % 60;
                bossBar.setTitle(event.displayName + " §8— §7" + min + ":" + String.format("%02d", sec));

                // Warning at 30s and 10s
                if (seconds == 30) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendMessage(SmallCaps.convert(event.icon + " §7El evento " + event.displayName + " §7termina en §e30s§7..."));
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f);
                    }
                } else if (seconds == 10) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendMessage(SmallCaps.convert(event.icon + " §c¡10 segundos! " + event.displayName + " §cestá por terminar..."));
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2.0f);
                    }
                }

                // Pulse bar color in last 10s
                if (seconds <= 10 && seconds % 2 == 0) {
                    bossBar.setColor(BarColor.RED);
                } else if (seconds <= 10) {
                    bossBar.setColor(event.barColor);
                }
            }
        }.runTaskTimer(plugin, 0, 20L);
    }

    private void endEvent() {
        EventType ended = activeEvent;
        activeEvent = null;
        eventEndTime = 0;

        // Remove boss bar
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }

        // Cancel countdown
        if (countdownTask != null) {
            try { countdownTask.cancel(); } catch (Exception ignored) {}
            countdownTask = null;
        }

        // Announce end
        if (ended != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(SmallCaps.convert("§8§l✦ " + ended.displayName + " §7ha terminado."));
                p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.0f);
            }
        }
    }

    private void applyEffects(EventType event) {
        org.bukkit.potion.PotionEffectType effectType = null;
        int amplifier = 0;

        switch (event) {
            case SPEED_BOOST:
                effectType = org.bukkit.potion.PotionEffectType.SPEED;
                amplifier = 1;
                break;
            case REGENERATION:
                effectType = org.bukkit.potion.PotionEffectType.REGENERATION;
                amplifier = 0;
                break;
            case HASTE_FURY:
                effectType = org.bukkit.potion.PotionEffectType.HASTE;
                amplifier = 1;
                break;
            case NIGHT_VISION:
                effectType = org.bukkit.potion.PotionEffectType.NIGHT_VISION;
                amplifier = 0;
                break;
            case JUMP_BOOST:
                effectType = org.bukkit.potion.PotionEffectType.JUMP_BOOST;
                amplifier = 1;
                break;
            case STRENGTH:
                effectType = org.bukkit.potion.PotionEffectType.STRENGTH;
                amplifier = 0;
                break;
            case RESISTANCE:
                effectType = org.bukkit.potion.PotionEffectType.RESISTANCE;
                amplifier = 0;
                break;
            default:
                break;
        }

        if (effectType != null) {
            int durationTicks = (int) (EVENT_DURATION_TICKS + 40); // +2s buffer
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        effectType, durationTicks, amplifier, false, true, true));
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  MULTIPLIER HOOKS — other systems check these
    // ═══════════════════════════════════════════════════════════════

    public boolean isActive() {
        return activeEvent != null;
    }

    public boolean isActive(EventType type) {
        return activeEvent == type;
    }

    public EventType getActiveEvent() {
        return activeEvent;
    }

    // ═══════════════════════════════════════════════════════════════
    //  EVENT LISTENERS — apply multiplier effects
    // ═══════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerXp(PlayerExpChangeEvent event) {
        if (activeEvent == EventType.DOUBLE_XP) {
            event.setAmount(event.getAmount() * 2);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobKill(EntityDeathEvent event) {
        if (event instanceof PlayerDeathEvent) return;
        if (event.getEntity().getKiller() == null) return;
        Player killer = event.getEntity().getKiller();

        // MOB_GOLD_RUSH: mobs drop money
        if (activeEvent == EventType.MOB_GOLD_RUSH) {
            int baseMoney = 5 + new Random().nextInt(20);
            if (plugin.getEconomy() != null) {
                plugin.getEconomy().depositPlayer(killer, baseMoney);
                com.moonlight.coreprotect.util.ActionBarUtil.send(killer,
                        "§6§l+$" + baseMoney + " §8(Fiebre del Oro)");
            }
        }

        // DOUBLE_MONEY: double essence drops from mobs
        if (activeEvent == EventType.DOUBLE_ESSENCES) {
            if (plugin.getEssenceManager() != null) {
                plugin.getEssenceManager().addEssences(killer.getUniqueId(), 1);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (activeEvent == EventType.LUCKY_MINING) {
            Material type = event.getBlock().getType();
            if (type == Material.STONE || type == Material.DEEPSLATE || type == Material.ANDESITE
                    || type == Material.DIORITE || type == Material.GRANITE || type == Material.TUFF) {
                // 2% chance to drop a diamond or emerald
                if (new Random().nextInt(50) == 0) {
                    Material drop = new Random().nextBoolean() ? Material.DIAMOND : Material.EMERALD;
                    event.getBlock().getWorld().dropItemNaturally(
                            event.getBlock().getLocation(),
                            new org.bukkit.inventory.ItemStack(drop));
                    Player p = event.getPlayer();
                    p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
                    com.moonlight.coreprotect.util.ActionBarUtil.send(p,
                            "§b§l⛏ §f¡" + (drop == Material.DIAMOND ? "Diamante" : "Esmeralda") + " encontrada! §8(Minería Afortunada)");
                }
            }
        }
    }

    // ── Player join/quit: add/remove from boss bar ──

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (bossBar != null && activeEvent != null) {
            bossBar.addPlayer(event.getPlayer());
            // Give active potion effects to joining player
            applyEffectsToPlayer(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (bossBar != null) {
            bossBar.removePlayer(event.getPlayer());
        }
    }

    private void applyEffectsToPlayer(Player player) {
        if (activeEvent == null) return;
        long remaining = eventEndTime - System.currentTimeMillis();
        if (remaining <= 0) return;
        int ticksLeft = (int) (remaining / 50);

        org.bukkit.potion.PotionEffectType effectType = null;
        int amplifier = 0;

        switch (activeEvent) {
            case SPEED_BOOST: effectType = org.bukkit.potion.PotionEffectType.SPEED; amplifier = 1; break;
            case REGENERATION: effectType = org.bukkit.potion.PotionEffectType.REGENERATION; break;
            case HASTE_FURY: effectType = org.bukkit.potion.PotionEffectType.HASTE; amplifier = 1; break;
            case NIGHT_VISION: effectType = org.bukkit.potion.PotionEffectType.NIGHT_VISION; break;
            case JUMP_BOOST: effectType = org.bukkit.potion.PotionEffectType.JUMP_BOOST; amplifier = 1; break;
            case STRENGTH: effectType = org.bukkit.potion.PotionEffectType.STRENGTH; break;
            case RESISTANCE: effectType = org.bukkit.potion.PotionEffectType.RESISTANCE; break;
            default: break;
        }

        if (effectType != null) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    effectType, ticksLeft, amplifier, false, true, true));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  SHUTDOWN
    // ═══════════════════════════════════════════════════════════════

    public void shutdown() {
        if (eventTimer != null) {
            try { eventTimer.cancel(); } catch (Exception ignored) {}
        }
        if (countdownTask != null) {
            try { countdownTask.cancel(); } catch (Exception ignored) {}
        }
        if (bossBar != null) {
            bossBar.removeAll();
        }
        activeEvent = null;
    }
}
