package com.moonlight.coreprotect.rewards;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * CS:GO-style dopamine roulette for daily login and first-time players.
 * Opens automatically 5s after texture pack loads.
 */
public class DailyRouletteManager implements Listener {

    private final CoreProtectPlugin plugin;
    private final Set<UUID> activeRoulettes = new HashSet<>();
    private final Map<UUID, Long> lastClaimed = new HashMap<>();
    private final Set<UUID> firstTimers = new HashSet<>();
    private final File dataFile;
    private final Map<UUID, Runnable> onCompleteCallbacks = new HashMap<>();
    private final Set<UUID> fastModePlayers = new HashSet<>();

    public void setFastMode(UUID uuid, boolean fast) {
        if (fast) fastModePlayers.add(uuid); else fastModePlayers.remove(uuid);
    }

    public static final String TITLE = "§8✦ §e§l★ §6§lRULETA DIARIA §e§l★ §8✦";
    public static final String TITLE_FIRST = "§8✦ §b§l★ §d§l¡BIENVENIDO! §b§l★ §8✦";

    private static final long COOLDOWN_MS = 24 * 60 * 60 * 1000; // 24 hours

    public DailyRouletteManager(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "daily_roulette.yml");
        loadData();
    }

    // ═══════════════════════════════════════════════════════════════
    //  PERSISTENCE
    // ═══════════════════════════════════════════════════════════════

    private void loadData() {
        if (!dataFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        for (String key : yaml.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                lastClaimed.put(uuid, yaml.getLong(key));
            } catch (Exception ignored) {}
        }
    }

    public void saveData() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Long> e : lastClaimed.entrySet()) {
            yaml.set(e.getKey().toString(), e.getValue());
        }
        try {
            yaml.save(dataFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("[DailyRoulette] Error saving: " + ex.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  ELIGIBILITY CHECK
    // ═══════════════════════════════════════════════════════════════

    public boolean canClaim(UUID uuid) {
        Long last = lastClaimed.get(uuid);
        if (last == null) return true;
        return (System.currentTimeMillis() - last) >= COOLDOWN_MS;
    }

    public boolean isFirstTimer(Player player) {
        return !player.hasPlayedBefore() || player.getStatistic(Statistic.PLAY_ONE_MINUTE) < 200;
    }

    // ═══════════════════════════════════════════════════════════════
    //  AUTO-TRIGGER (called after resource pack loads)
    // ═══════════════════════════════════════════════════════════════

    public void tryOpenForPlayer(Player player) {
        if (!player.isOnline()) return;
        if (activeRoulettes.contains(player.getUniqueId())) return;
        if (!canClaim(player.getUniqueId())) return;

        // No abrir si está en minijuego, KOTH o en combate PvP
        String worldName = player.getWorld().getName().toLowerCase();
        if (worldName.equals("koth") || worldName.equals("minigames")) return;
        if (plugin.getMiniGameManager() != null && plugin.getMiniGameManager().isGameActive()
                && plugin.getMiniGameManager().getCurrentGame() != null
                && plugin.getMiniGameManager().getCurrentGame().getPlayers().contains(player.getUniqueId())) return;
        if (plugin.getCombatTagManager() != null && plugin.getCombatTagManager().isInCombat(player.getUniqueId())) return;
        if (plugin.getBossArenaManager() != null && plugin.getBossArenaManager().isInAnyArena(player.getLocation())) return;

        boolean first = isFirstTimer(player);

        // Delay 5s after resource pack loaded
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (activeRoulettes.contains(player.getUniqueId())) return;

            // Re-check: no abrir si entró en minijuego, KOTH o combate durante el delay
            String wn = player.getWorld().getName().toLowerCase();
            if (wn.equals("koth") || wn.equals("minigames")) return;
            if (plugin.getMiniGameManager() != null && plugin.getMiniGameManager().isGameActive()
                    && plugin.getMiniGameManager().getCurrentGame() != null
                    && plugin.getMiniGameManager().getCurrentGame().getPlayers().contains(player.getUniqueId())) return;
            if (plugin.getCombatTagManager() != null && plugin.getCombatTagManager().isInCombat(player.getUniqueId())) return;
            if (plugin.getBossArenaManager() != null && plugin.getBossArenaManager().isInAnyArena(player.getLocation())) return;

            if (first) {
                player.sendMessage("");
                player.sendMessage(SmallCaps.convert("§d§l✦ §b§l¡BIENVENIDO A MOONLIGHT! §d§l✦"));
                player.sendMessage(SmallCaps.convert("§7Tienes una §e§lRULETA DE BIENVENIDA §7esperándote..."));
                player.sendMessage("");
                player.sendTitle(
                    SmallCaps.convert("§d§l✦ §b§lBIENVENIDO §d§l✦"),
                    SmallCaps.convert("§e¡Gira la ruleta!"), 10, 50, 10);
            } else {
                player.sendMessage("");
                player.sendMessage(SmallCaps.convert("§6§l★ §e§l¡RULETA DIARIA DISPONIBLE! §6§l★"));
                player.sendMessage(SmallCaps.convert("§7Abriendo ruleta..."));
                player.sendMessage("");
                player.sendTitle(
                    SmallCaps.convert("§6§l★ §eRULETA DIARIA §6§l★"),
                    SmallCaps.convert("§f¡Gira y gana premios!"), 10, 50, 10);
            }
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && !activeRoulettes.contains(player.getUniqueId())) {
                    openRoulette(player, first);
                }
            }, 50L); // 2.5s dramatic pause
        }, 100L); // 5s after resource pack
    }

    // ═══════════════════════════════════════════════════════════════
    //  PRIZE POOL — MASSIVE (dopamine-inducing variety)
    // ═══════════════════════════════════════════════════════════════

    private record Prize(String name, Material icon, String color, int weight, String rarity) {}

    private List<Prize> getDailyPool() {
        List<Prize> pool = new ArrayList<>();
        // ── COMMON (gray) ──
        pool.add(new Prize("$1,000", Material.GOLD_NUGGET, "§e", 30, "§7"));
        pool.add(new Prize("$2,000", Material.GOLD_NUGGET, "§e", 25, "§7"));
        pool.add(new Prize("$3,000", Material.GOLD_NUGGET, "§e", 22, "§7"));
        pool.add(new Prize("5 Esencias", Material.AMETHYST_SHARD, "§d", 28, "§7"));
        pool.add(new Prize("10 Esencias", Material.AMETHYST_SHARD, "§d", 22, "§7"));
        pool.add(new Prize("8 Hierro", Material.IRON_INGOT, "§f", 25, "§7"));
        pool.add(new Prize("4 Oro", Material.GOLD_INGOT, "§6", 22, "§7"));
        pool.add(new Prize("16 Flechas", Material.ARROW, "§f", 25, "§7"));
        pool.add(new Prize("4 Bistec", Material.COOKED_BEEF, "§c", 25, "§7"));
        pool.add(new Prize("8 Pan", Material.BREAD, "§6", 25, "§7"));
        pool.add(new Prize("2 Manzanas Doradas", Material.GOLDEN_APPLE, "§e", 18, "§7"));
        pool.add(new Prize("32 Antorchas", Material.TORCH, "§e", 20, "§7"));
        pool.add(new Prize("1 Blaze Rod", Material.BLAZE_ROD, "§6", 18, "§7"));
        pool.add(new Prize("4 Cuero", Material.LEATHER, "§6", 20, "§7"));
        pool.add(new Prize("2 XP Bottles", Material.EXPERIENCE_BOTTLE, "§a", 22, "§7"));

        // ── UNCOMMON (green) ──
        pool.add(new Prize("$5,000", Material.GOLD_INGOT, "§e", 15, "§a"));
        pool.add(new Prize("$8,000", Material.GOLD_INGOT, "§6", 12, "§a"));
        pool.add(new Prize("20 Esencias", Material.AMETHYST_SHARD, "§d", 14, "§a"));
        pool.add(new Prize("30 Esencias", Material.AMETHYST_CLUSTER, "§d", 10, "§a"));
        pool.add(new Prize("4 Diamantes", Material.DIAMOND, "§b", 12, "§a"));
        pool.add(new Prize("2 Esmeraldas", Material.EMERALD, "§a", 14, "§a"));
        pool.add(new Prize("1 Libro de XP", Material.ENCHANTED_BOOK, "§d", 12, "§a"));
        pool.add(new Prize("Llave Common", Material.TRIPWIRE_HOOK, "§f", 14, "§a"));
        pool.add(new Prize("8 Obsidiana", Material.OBSIDIAN, "§5", 12, "§a"));
        pool.add(new Prize("4 Lapis Lazuli", Material.LAPIS_LAZULI, "§9", 14, "§a"));
        pool.add(new Prize("16 Redstone", Material.REDSTONE, "§c", 12, "§a"));
        pool.add(new Prize("4 Carbón", Material.COAL, "§8", 14, "§a"));
        pool.add(new Prize("1 Calabaza", Material.CARVED_PUMPKIN, "§6", 12, "§a"));
        pool.add(new Prize("1 Silla de Montar", Material.SADDLE, "§6", 10, "§a"));
        pool.add(new Prize("5 XP Bottles", Material.EXPERIENCE_BOTTLE, "§a", 14, "§a"));

        // ── RARE (blue) ──
        pool.add(new Prize("$15,000", Material.GOLD_BLOCK, "§6", 8, "§9"));
        pool.add(new Prize("$20,000", Material.GOLD_BLOCK, "§6", 6, "§9"));
        pool.add(new Prize("50 Esencias", Material.AMETHYST_BLOCK, "§5", 7, "§9"));
        pool.add(new Prize("8 Diamantes", Material.DIAMOND, "§b", 7, "§9"));
        pool.add(new Prize("Llave Rare", Material.TRIPWIRE_HOOK, "§9", 8, "§9"));
        pool.add(new Prize("1 Totem", Material.TOTEM_OF_UNDYING, "§6", 5, "§9"));
        pool.add(new Prize("4 Bloques de Hierro", Material.IRON_BLOCK, "§f", 8, "§9"));
        pool.add(new Prize("1 Tridente", Material.TRIDENT, "§b", 4, "§9"));
        pool.add(new Prize("10 XP Bottles", Material.EXPERIENCE_BOTTLE, "§a", 8, "§9"));
        pool.add(new Prize("1 Bloque de Diamante", Material.DIAMOND_BLOCK, "§b", 5, "§9"));
        pool.add(new Prize("2 Bloques de Oro", Material.GOLD_BLOCK, "§6", 7, "§9"));
        pool.add(new Prize("1 End Crystal", Material.END_CRYSTAL, "§d", 5, "§9"));
        pool.add(new Prize("3 Perlas de Ender", Material.ENDER_PEARL, "§5", 8, "§9"));
        pool.add(new Prize("1 Yunque", Material.ANVIL, "§7", 7, "§9"));

        // ── EPIC (purple) ──
        pool.add(new Prize("$50,000", Material.DIAMOND_BLOCK, "§b", 3, "§5"));
        pool.add(new Prize("100 Esencias", Material.AMETHYST_BLOCK, "§5", 3, "§5"));
        pool.add(new Prize("Llave Special", Material.TRIPWIRE_HOOK, "§a", 4, "§5"));
        pool.add(new Prize("1 Manzana Encantada", Material.ENCHANTED_GOLDEN_APPLE, "§6§l", 2, "§5"));
        pool.add(new Prize("16 Diamantes", Material.DIAMOND, "§b", 3, "§5"));
        pool.add(new Prize("1 Elytra", Material.ELYTRA, "§d", 1, "§5"));
        pool.add(new Prize("4 Bloques de Diamante", Material.DIAMOND_BLOCK, "§b", 2, "§5"));
        pool.add(new Prize("1 Beacon", Material.BEACON, "§e§l", 1, "§5"));
        pool.add(new Prize("32 XP Bottles", Material.EXPERIENCE_BOTTLE, "§a", 3, "§5"));
        pool.add(new Prize("1 Cabeza de Creeper", Material.CREEPER_HEAD, "§a", 2, "§5"));
        pool.add(new Prize("3 Bloques de Esmeralda", Material.EMERALD_BLOCK, "§a", 2, "§5"));

        // ── LEGENDARY (gold) ──
        pool.add(new Prize("$100,000", Material.NETHER_STAR, "§6§l", 1, "§6"));
        pool.add(new Prize("200 Esencias", Material.NETHER_STAR, "§5§l", 1, "§6"));
        pool.add(new Prize("Llave Legendary", Material.TRIPWIRE_HOOK, "§6§l", 1, "§6"));
        pool.add(new Prize("Llave Moon", Material.END_CRYSTAL, "§5§l", 1, "§6"));
        pool.add(new Prize("Vuelo 1 Día", Material.FEATHER, "§b§l", 1, "§6"));

        return pool;
    }

    private List<Prize> getFirstTimePool() {
        List<Prize> pool = getDailyPool();
        // First-timers get enhanced pool — add extra guaranteed good items
        pool.add(new Prize("$10,000 Bienvenida", Material.GOLD_BLOCK, "§e§l", 20, "§b"));
        pool.add(new Prize("25 Esencias Bienvenida", Material.AMETHYST_CLUSTER, "§d§l", 18, "§b"));
        pool.add(new Prize("Kit Inicio", Material.IRON_CHESTPLATE, "§f§l", 15, "§b"));
        pool.add(new Prize("6 Diamantes Bienvenida", Material.DIAMOND, "§b§l", 12, "§b"));
        pool.add(new Prize("Llave Bienvenida", Material.TRIPWIRE_HOOK, "§b§l", 12, "§b"));
        pool.add(new Prize("32 Bistec", Material.COOKED_BEEF, "§c§l", 15, "§b"));
        pool.add(new Prize("Pico Hierro", Material.IRON_PICKAXE, "§f§l", 15, "§b"));
        pool.add(new Prize("Espada Hierro", Material.IRON_SWORD, "§f§l", 15, "§b"));
        pool.add(new Prize("16 Manzanas Doradas", Material.GOLDEN_APPLE, "§e§l", 10, "§b"));
        pool.add(new Prize("10 XP Bottles", Material.EXPERIENCE_BOTTLE, "§a§l", 15, "§b"));
        return pool;
    }

    private Prize selectWinner(List<Prize> pool) {
        int totalWeight = pool.stream().mapToInt(Prize::weight).sum();
        int roll = new Random().nextInt(totalWeight);
        int cumulative = 0;
        for (Prize p : pool) {
            cumulative += p.weight;
            if (roll < cumulative) return p;
        }
        return pool.get(0);
    }

    // ═══════════════════════════════════════════════════════════════
    //  ROULETTE GUI — CS:GO STYLE
    // ═══════════════════════════════════════════════════════════════

    private void openRoulette(Player player, boolean firstTime) {
        UUID uid = player.getUniqueId();
        activeRoulettes.add(uid);

        String title = firstTime ? TITLE_FIRST : TITLE;
        Inventory inv = Bukkit.createInventory(null, 27, title);

        List<Prize> pool = firstTime ? getFirstTimePool() : getDailyPool();
        Prize winner = selectWinner(pool);
        Random rand = new Random();

        // ── TOP ROW: Decorative border with pulsing colors ──
        Material[] topColors = {
            Material.RED_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE,
            Material.YELLOW_STAINED_GLASS_PANE, Material.LIME_STAINED_GLASS_PANE,
            Material.RED_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE,
            Material.YELLOW_STAINED_GLASS_PANE, Material.LIME_STAINED_GLASS_PANE,
            Material.RED_STAINED_GLASS_PANE
        };
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, createGlass(topColors[i], "§r"));
        }
        // Center marker ▼
        inv.setItem(4, createGlass(Material.RED_STAINED_GLASS_PANE, "§c§l▼ §e§lPREMIO §c§l▼"));

        // ── BOTTOM ROW: Decorative border ──
        Material[] botColors = {
            Material.PURPLE_STAINED_GLASS_PANE, Material.MAGENTA_STAINED_GLASS_PANE,
            Material.PINK_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE,
            Material.PURPLE_STAINED_GLASS_PANE, Material.MAGENTA_STAINED_GLASS_PANE,
            Material.PINK_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE,
            Material.PURPLE_STAINED_GLASS_PANE
        };
        for (int i = 18; i < 27; i++) {
            inv.setItem(i, createGlass(botColors[i - 18], "§r"));
        }
        // Center marker ▲
        inv.setItem(22, createGlass(Material.RED_STAINED_GLASS_PANE, "§c§l▲ §e§lPREMIO §c§l▲"));

        // ── MIDDLE ROW: Initial random prizes ──
        for (int i = 9; i <= 17; i++) {
            inv.setItem(i, createPrizeItem(pool.get(rand.nextInt(pool.size()))));
        }

        player.openInventory(inv);

        // Dramatic intro sound
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 0.5f);
        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.8f, 1.0f);

        // Build sequence — winner lands at position totalShifts-5 (center slot 13)
        boolean fast = fastModePlayers.contains(uid);
        int totalShifts = fast ? 15 : 60;
        List<Prize> sequence = new ArrayList<>();
        for (int i = 0; i < totalShifts; i++) {
            if (i == totalShifts - 5) {
                sequence.add(winner);
            } else {
                sequence.add(pool.get(rand.nextInt(pool.size())));
            }
        }

        // Start the CS:GO-style scrolling animation
        new RouletteAnimation(player, inv, sequence, winner, firstTime, pool, fast).runTaskTimer(plugin, fast ? 1L : 3L, 1L);
    }

    // ═══════════════════════════════════════════════════════════════
    //  ANIMATION ENGINE
    // ═══════════════════════════════════════════════════════════════

    private class RouletteAnimation extends BukkitRunnable {
        private final Player player;
        private final Inventory inv;
        private final List<Prize> sequence;
        private final Prize winner;
        private final boolean firstTime;
        private final List<Prize> pool;
        private int tick = 0;
        private int step = 0;
        private int nextStepAt = 0;
        private final int totalSteps;
        private int borderFlash = 0;

        private final boolean fast;

        RouletteAnimation(Player player, Inventory inv, List<Prize> sequence,
                          Prize winner, boolean firstTime, List<Prize> pool, boolean fast) {
            this.player = player;
            this.inv = inv;
            this.sequence = sequence;
            this.winner = winner;
            this.firstTime = firstTime;
            this.pool = pool;
            this.totalSteps = sequence.size();
            this.fast = fast;
        }

        @Override
        public void run() {
            if (!player.isOnline() || !activeRoulettes.contains(player.getUniqueId())) {
                cancel();
                return;
            }

            tick++;

            // ── Border animation (flash colors every 2 ticks) ──
            if (tick % 2 == 0) {
                borderFlash++;
                animateBorders(inv, borderFlash);
            }

            if (tick < nextStepAt) return;

            // ── Shift middle row left (CS:GO scroll) ──
            for (int i = 9; i < 17; i++) {
                inv.setItem(i, inv.getItem(i + 1));
            }
            inv.setItem(17, createPrizeItem(sequence.get(step)));

            // ── SOUND DESIGN — increasing pitch + varying sounds ──
            float progress = (float) step / totalSteps;
            float pitch = 0.5f + progress * 1.5f;

            if (progress < 0.4f) {
                // Fast phase: rapid clicks
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, pitch);
            } else if (progress < 0.7f) {
                // Medium phase: pling sounds
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, pitch);
            } else if (progress < 0.9f) {
                // Slow phase: bell sounds with tension
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, pitch);
                if (step % 2 == 0) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.5f);
                }
            } else {
                // Final phase: dramatic chimes
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, pitch);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 1.8f);
                // Heartbeat effect
                if (step % 2 == 0) {
                    player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.6f, 1.2f);
                }
            }

            step++;
            nextStepAt = tick + getDelay(step, totalSteps);

            if (step >= totalSteps) {
                cancel();
                startReveal();
            }
        }

        private int getDelay(int step, int total) {
            float progress = (float) step / total;
            if (fast) {
                if (progress < 0.50) return 1;
                if (progress < 0.80) return 2;
                if (progress < 0.95) return 3;
                return 4;
            }
            if (progress < 0.30) return 1;   // Super fast
            if (progress < 0.50) return 2;   // Fast
            if (progress < 0.65) return 3;   // Medium
            if (progress < 0.78) return 4;   // Slowing
            if (progress < 0.87) return 6;   // Slow
            if (progress < 0.93) return 9;   // Very slow
            if (progress < 0.97) return 14;  // Crawling
            return 20;                        // Almost stopped
        }

        private void startReveal() {
            // ── DRAMATIC PAUSE ──
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;

                // Big reveal sound
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
                player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1.0f, 1.0f);

                // Flash animation
                flashWinner();
            }, fast ? 3L : 15L);
        }

        private void flashWinner() {
            new BukkitRunnable() {
                int flash = 0;
                final Material[] flashColors = {
                    Material.YELLOW_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE,
                    Material.LIME_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                    Material.MAGENTA_STAINED_GLASS_PANE, Material.RED_STAINED_GLASS_PANE
                };

                @Override
                public void run() {
                    if (!player.isOnline()) { cancel(); return; }

                    int maxFlashes = fast ? 4 : 10;
                    if (flash >= maxFlashes) {
                        cancel();
                        // Show final winner and give prize
                        inv.setItem(13, createPrizeItem(winner));
                        // Firework borders
                        for (int i = 0; i < 9; i++)
                            inv.setItem(i, createGlass(Material.YELLOW_STAINED_GLASS_PANE, "§e§l★ §6§lGANASTE §e§l★"));
                        for (int i = 18; i < 27; i++)
                            inv.setItem(i, createGlass(Material.YELLOW_STAINED_GLASS_PANE, "§e§l★ §6§lGANASTE §e§l★"));

                        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 1f);
                        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1f, 1.5f);
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);

                        Bukkit.getScheduler().runTaskLater(plugin, () -> givePrize(player, winner, firstTime), fast ? 5L : 30L);
                        return;
                    }

                    if (flash % 2 == 0) {
                        // Flash to colored glass
                        Material flashMat = flashColors[flash / 2 % flashColors.length];
                        inv.setItem(13, createGlass(flashMat, "§e§l★ §f§l??? §e§l★"));
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.0f + flash * 0.1f);
                    } else {
                        // Flash back to prize
                        inv.setItem(13, createPrizeItem(winner));
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f + flash * 0.05f);
                    }

                    // Flash borders too
                    for (int i = 0; i < 9; i++) {
                        Material m = flash % 2 == 0 ? Material.WHITE_STAINED_GLASS_PANE : Material.YELLOW_STAINED_GLASS_PANE;
                        inv.setItem(i, createGlass(m, "§r"));
                    }
                    for (int i = 18; i < 27; i++) {
                        Material m = flash % 2 == 0 ? Material.YELLOW_STAINED_GLASS_PANE : Material.WHITE_STAINED_GLASS_PANE;
                        inv.setItem(i, createGlass(m, "§r"));
                    }

                    flash++;
                }
            }.runTaskTimer(plugin, fast ? 1L : 3L, fast ? 2L : 4L);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  BORDER ANIMATION
    // ═══════════════════════════════════════════════════════════════

    private void animateBorders(Inventory inv, int frame) {
        Material[] cycle = {
            Material.RED_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE,
            Material.YELLOW_STAINED_GLASS_PANE, Material.LIME_STAINED_GLASS_PANE,
            Material.CYAN_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE,
            Material.BLUE_STAINED_GLASS_PANE, Material.PURPLE_STAINED_GLASS_PANE,
            Material.MAGENTA_STAINED_GLASS_PANE
        };
        for (int i = 0; i < 9; i++) {
            if (i == 4) continue; // Skip center marker
            inv.setItem(i, createGlass(cycle[(i + frame) % cycle.length], "§r"));
        }
        for (int i = 18; i < 27; i++) {
            if (i == 22) continue; // Skip center marker
            inv.setItem(i, createGlass(cycle[(i + frame + 4) % cycle.length], "§r"));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  GIVE PRIZE
    // ═══════════════════════════════════════════════════════════════

    private void givePrize(Player player, Prize prize, boolean firstTime) {
        UUID uid = player.getUniqueId();
        String pName = player.getName();
        String prizeName = prize.name;

        // Mark as claimed
        lastClaimed.put(uid, System.currentTimeMillis());
        saveData();

        // ── Deliver reward ──
        if (prizeName.contains("$")) {
            int amount = parseNumber(prizeName);
            if (plugin.getEconomy() != null) plugin.getEconomy().depositPlayer(player, amount);
        } else if (prizeName.contains("Esencias") || prizeName.contains("esencias")) {
            int amount = parseNumber(prizeName);
            if (plugin.getEssenceManager() != null)
                plugin.getEssenceManager().addEssences(uid, amount);
        } else if (prizeName.contains("Llave Common") || prizeName.contains("Llave Bienvenida")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "excellentcrates key give " + pName + " common 1");
        } else if (prizeName.contains("Llave Rare")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "excellentcrates key give " + pName + " rare 1");
        } else if (prizeName.contains("Llave Special")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "excellentcrates key give " + pName + " special 1");
        } else if (prizeName.contains("Llave Legendary")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "excellentcrates key give " + pName + " legendary 1");
        } else if (prizeName.contains("Llave Moon")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "excellentcrates key give " + pName + " moon 1");
        } else if (prizeName.contains("Vuelo")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "lp user " + pName + " permission settemp essentials.fly true 1d");
        } else if (prizeName.contains("Kit Inicio")) {
            giveStarterKit(player);
        } else {
            // Physical items
            givePhysicalItem(player, prize);
        }

        // ── Messages ──
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§e§l★ §6§l¡GANASTE! §f" + prize.color + prizeName + " §f" + prize.rarity + getRarityLabel(prize.rarity)));
        player.sendMessage("");

        if (firstTime) {
            Bukkit.broadcastMessage(SmallCaps.convert(
                "§d§l✦ §b" + pName + " §fha girado la §d§lRuleta de Bienvenida §fy ganó " + prize.color + prizeName + "§f!"));
        } else {
            Bukkit.broadcastMessage(SmallCaps.convert(
                "§6§l★ §e" + pName + " §fha girado la §e§lRuleta Diaria §fy ganó " + prize.color + prizeName + "§f!"));
        }

        // Celebration effects
        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST_FAR, 1f, 1f);
        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE_FAR, 1f, 1.2f);

        // Title de premio
        String titleColor = firstTime ? "§d§l✦ " : "§6§l★ ";
        player.sendTitle(
            SmallCaps.convert(titleColor + prize.color + prizeName + (firstTime ? " §d§l✦" : " §6§l★")),
            SmallCaps.convert("§f" + prize.rarity + getRarityLabel(prize.rarity)),
            5, 50, 10);

        // Close inventory FIRST, then remove active flag, then chain next
        Runnable onComplete = onCompleteCallbacks.remove(uid);
        boolean wasFast = fastModePlayers.remove(uid);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) player.closeInventory();
            // Remove AFTER closing so onInventoryClose doesn't interfere
            activeRoulettes.remove(uid);
            if (onComplete != null) {
                // Wait enough for the close to fully process before opening next
                Bukkit.getScheduler().runTaskLater(plugin, onComplete, wasFast ? 5L : 20L);
            }
        }, wasFast ? 8L : 40L);
    }

    private String getRarityLabel(String rarity) {
        return switch (rarity) {
            case "§7" -> "§8[Común]";
            case "§a" -> "§a[Poco Común]";
            case "§9" -> "§9[Raro]";
            case "§5" -> "§5[Épico]";
            case "§6" -> "§6§l[Legendario]";
            case "§b" -> "§b[Bienvenida]";
            default -> "";
        };
    }

    private void givePhysicalItem(Player player, Prize prize) {
        int amount = parseNumber(prize.name);
        if (amount <= 0) amount = 1;
        ItemStack item = new ItemStack(prize.icon, Math.min(amount, 64));
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        for (ItemStack left : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }

    private void giveStarterKit(Player player) {
        ItemStack[] kit = {
            new ItemStack(Material.IRON_SWORD),
            new ItemStack(Material.IRON_PICKAXE),
            new ItemStack(Material.IRON_AXE),
            new ItemStack(Material.IRON_CHESTPLATE),
            new ItemStack(Material.IRON_LEGGINGS),
            new ItemStack(Material.IRON_BOOTS),
            new ItemStack(Material.IRON_HELMET),
            new ItemStack(Material.COOKED_BEEF, 32),
            new ItemStack(Material.TORCH, 64),
            new ItemStack(Material.OAK_PLANKS, 32)
        };
        for (ItemStack item : kit) {
            HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            for (ItemStack left : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        }
    }

    private int parseNumber(String s) {
        try {
            String nums = s.replaceAll("[^0-9]", "");
            return nums.isEmpty() ? 1 : Integer.parseInt(nums);
        } catch (Exception e) { return 1; }
    }

    // ═══════════════════════════════════════════════════════════════
    //  AUTO-OPEN ON JOIN (queued after resource pack)
    // ═══════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!canClaim(player.getUniqueId())) return;

        // Mark as claimed immediately so it doesn't keep adding
        lastClaimed.put(player.getUniqueId(), System.currentTimeMillis());
        saveData();

        // Add to pending rewards instead of auto-opening
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            PendingRewardsManager prm = plugin.getPendingRewardsManager();
            if (prm != null) {
                prm.addDailyRoulette(player);
            }
        }, 100L); // 5s after join
    }

    /**
     * Opens a roulette from the pending rewards system, with a callback for chaining.
     */
    public void openRouletteFromPending(Player player, Runnable onComplete) {
        UUID uid = player.getUniqueId();
        if (activeRoulettes.contains(uid)) {
            if (onComplete != null) onComplete.run();
            return;
        }
        boolean first = isFirstTimer(player);
        if (onComplete != null) onCompleteCallbacks.put(uid, onComplete);
        openRoulette(player, first);
    }

    // ═══════════════════════════════════════════════════════════════
    //  EVENT PROTECTION
    // ═══════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (title.equals(TITLE) || title.equals(TITLE_FIRST)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        String title = event.getView().getTitle();
        if (title.equals(TITLE) || title.equals(TITLE_FIRST)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        String title = event.getView().getTitle();
        if (title.equals(TITLE) || title.equals(TITLE_FIRST)) {
            UUID uid = event.getPlayer().getUniqueId();
            if (activeRoulettes.contains(uid)) {
                // Re-open — can't close during animation
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Player p = Bukkit.getPlayer(uid);
                    if (p != null && p.isOnline() && activeRoulettes.contains(uid)) {
                        // Force reopen with new roulette
                        // (animation will continue on existing inventory reference)
                    }
                }, 1L);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════════

    private ItemStack createPrizeItem(Prize prize) {
        ItemStack item = new ItemStack(prize.icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(prize.color + prize.name);
            List<String> lore = new ArrayList<>();
            lore.add(prize.rarity + getRarityLabel(prize.rarity));
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createGlass(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }
}
