package com.moonlight.coreprotect.rewards;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ruleta automática cada 30 minutos de tiempo conectado seguido.
 * Avisa 30s antes, se abre sola, animación CS:GO.
 * Recompensas balanceadas (menores que la diaria).
 */
public class PlaytimeRouletteManager implements Listener {

    private final CoreProtectPlugin plugin;
    private final Map<UUID, Long> sessionStart = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> roulettesGiven = new ConcurrentHashMap<>();
    private final Set<UUID> activeRoulettes = ConcurrentHashMap.newKeySet();
    private final Set<UUID> warned = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Runnable> onCompleteCallbacks = new ConcurrentHashMap<>();
    private final Set<UUID> fastModePlayers = ConcurrentHashMap.newKeySet();

    public void setFastMode(UUID uuid, boolean fast) {
        if (fast) fastModePlayers.add(uuid); else fastModePlayers.remove(uuid);
    }

    public static final String TITLE = "§8✦ §a§l★ §2§lRULETA DE CONEXIÓN §a§l★ §8✦";
    private static final long INTERVAL_MS = 30 * 60 * 1000L; // 30 min
    private static final long WARNING_MS = 30 * 1000L; // aviso 30s antes

    public PlaytimeRouletteManager(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        // Tick every 10 seconds to check players
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickCheck, 200L, 200L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID uid = event.getPlayer().getUniqueId();
        sessionStart.put(uid, System.currentTimeMillis());
        roulettesGiven.put(uid, 0);
        warned.remove(uid);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uid = event.getPlayer().getUniqueId();
        sessionStart.remove(uid);
        roulettesGiven.remove(uid);
        warned.remove(uid);
        activeRoulettes.remove(uid);
    }

    private void tickCheck() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uid = player.getUniqueId();
            Long start = sessionStart.get(uid);
            if (start == null) {
                sessionStart.put(uid, now);
                roulettesGiven.put(uid, 0);
                continue;
            }
            if (activeRoulettes.contains(uid)) continue;

            long elapsed = now - start;
            int given = roulettesGiven.getOrDefault(uid, 0);
            long nextRouletteAt = (given + 1) * INTERVAL_MS;
            long warningAt = nextRouletteAt - WARNING_MS;

            // Time to add pending roulette
            if (elapsed >= nextRouletteAt) {
                roulettesGiven.put(uid, given + 1);
                warned.remove(uid);
                // Add to pending instead of auto-opening
                PendingRewardsManager prm = plugin.getPendingRewardsManager();
                if (prm != null) {
                    prm.addPlaytimeRoulette(player);
                }
            }
        }
    }

    private String formatMinutes(long ms) {
        long min = ms / 60000;
        return min + " min";
    }

    // ═══════════════════════════════════════════════════════════════
    //  PRIZE POOL — balanced (lower than daily)
    // ═══════════════════════════════════════════════════════════════

    private record Prize(String name, Material icon, String color, int weight, String rarity) {}

    private List<Prize> getPool() {
        List<Prize> pool = new ArrayList<>();
        // ── COMMON ──
        pool.add(new Prize("$500", Material.GOLD_NUGGET, "§e", 35, "§7"));
        pool.add(new Prize("$1,000", Material.GOLD_NUGGET, "§e", 28, "§7"));
        pool.add(new Prize("$1,500", Material.GOLD_NUGGET, "§e", 22, "§7"));
        pool.add(new Prize("3 Esencias", Material.AMETHYST_SHARD, "§d", 30, "§7"));
        pool.add(new Prize("5 Esencias", Material.AMETHYST_SHARD, "§d", 25, "§7"));
        pool.add(new Prize("4 Hierro", Material.IRON_INGOT, "§f", 25, "§7"));
        pool.add(new Prize("2 Oro", Material.GOLD_INGOT, "§6", 22, "§7"));
        pool.add(new Prize("8 Flechas", Material.ARROW, "§f", 25, "§7"));
        pool.add(new Prize("4 Bistec", Material.COOKED_BEEF, "§c", 25, "§7"));
        pool.add(new Prize("1 Manzana Dorada", Material.GOLDEN_APPLE, "§e", 18, "§7"));
        pool.add(new Prize("1 XP Bottle", Material.EXPERIENCE_BOTTLE, "§a", 25, "§7"));

        // ── UNCOMMON ──
        pool.add(new Prize("$3,000", Material.GOLD_INGOT, "§e", 14, "§a"));
        pool.add(new Prize("$5,000", Material.GOLD_INGOT, "§6", 10, "§a"));
        pool.add(new Prize("10 Esencias", Material.AMETHYST_SHARD, "§d", 14, "§a"));
        pool.add(new Prize("15 Esencias", Material.AMETHYST_CLUSTER, "§d", 10, "§a"));
        pool.add(new Prize("2 Diamantes", Material.DIAMOND, "§b", 10, "§a"));
        pool.add(new Prize("Llave Common", Material.TRIPWIRE_HOOK, "§f", 12, "§a"));
        pool.add(new Prize("4 Obsidiana", Material.OBSIDIAN, "§5", 12, "§a"));
        pool.add(new Prize("3 XP Bottles", Material.EXPERIENCE_BOTTLE, "§a", 14, "§a"));
        pool.add(new Prize("2 Perlas de Ender", Material.ENDER_PEARL, "§5", 10, "§a"));

        // ── RARE ──
        pool.add(new Prize("$10,000", Material.GOLD_BLOCK, "§6", 6, "§9"));
        pool.add(new Prize("25 Esencias", Material.AMETHYST_BLOCK, "§5", 6, "§9"));
        pool.add(new Prize("4 Diamantes", Material.DIAMOND, "§b", 5, "§9"));
        pool.add(new Prize("Llave Rare", Material.TRIPWIRE_HOOK, "§9", 5, "§9"));
        pool.add(new Prize("1 Totem", Material.TOTEM_OF_UNDYING, "§6", 3, "§9"));
        pool.add(new Prize("5 XP Bottles", Material.EXPERIENCE_BOTTLE, "§a", 6, "§9"));

        // ── EPIC ──
        pool.add(new Prize("$25,000", Material.DIAMOND_BLOCK, "§b", 2, "§5"));
        pool.add(new Prize("50 Esencias", Material.AMETHYST_BLOCK, "§5", 2, "§5"));
        pool.add(new Prize("Llave Special", Material.TRIPWIRE_HOOK, "§a", 2, "§5"));
        pool.add(new Prize("8 Diamantes", Material.DIAMOND, "§b", 2, "§5"));

        // ── LEGENDARY ──
        pool.add(new Prize("$50,000", Material.NETHER_STAR, "§6§l", 1, "§6"));
        pool.add(new Prize("100 Esencias", Material.NETHER_STAR, "§5§l", 1, "§6"));
        pool.add(new Prize("Llave Legendary", Material.TRIPWIRE_HOOK, "§6§l", 1, "§6"));

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
    //  ROULETTE GUI — dopaminergic CS:GO style
    // ═══════════════════════════════════════════════════════════════

    // Maps player -> their current roulette inventory (for reopen on close)
    private final Map<UUID, Inventory> rouletteInventories = new ConcurrentHashMap<>();

    private void openRoulette(Player player) {
        UUID uid = player.getUniqueId();
        if (activeRoulettes.contains(uid)) return;
        activeRoulettes.add(uid);

        Inventory inv = Bukkit.createInventory(null, 27, TITLE);
        rouletteInventories.put(uid, inv);
        List<Prize> pool = getPool();
        Prize winner = selectWinner(pool);
        Random rand = new Random();

        // ── Build strip: total scroll steps, winner guaranteed at center ──
        // We'll scroll through exactly TOTAL_STEPS positions.
        // At the end, slot 13 (middle row center) shows strip[finalOffset + 4].
        // So we place the winner at finalOffset + 4.
        final boolean fast = fastModePlayers.contains(uid);
        final int TOTAL_STEPS = fast ? 12 : 45; // number of scroll movements
        final int STRIP_LEN = TOTAL_STEPS + 9; // need 9 visible at final offset
        Prize[] strip = new Prize[STRIP_LEN];
        int winnerIdx = TOTAL_STEPS - 1 + 4; // so at final offset (TOTAL_STEPS-1), center = winnerIdx
        strip[winnerIdx] = winner;

        // Fill rest with weighted random, but add near-misses near the end
        for (int i = 0; i < STRIP_LEN; i++) {
            if (strip[i] == null) {
                // Near-misses: 2-3 positions before winner, place rarer items
                if (i >= winnerIdx - 3 && i < winnerIdx) {
                    // Pick something exciting but not the winner
                    List<Prize> rarePool = pool.stream()
                            .filter(p -> p.rarity.equals("§9") || p.rarity.equals("§5") || p.rarity.equals("§6"))
                            .collect(java.util.stream.Collectors.toList());
                    if (!rarePool.isEmpty()) {
                        strip[i] = rarePool.get(rand.nextInt(rarePool.size()));
                    } else {
                        strip[i] = pool.get(rand.nextInt(pool.size()));
                    }
                } else {
                    strip[i] = pool.get(rand.nextInt(pool.size()));
                }
            }
        }

        // ── Pre-calculate tick schedule: each entry = tick at which scroll N happens ──
        // Fast start → smooth deceleration → dramatic last ticks
        int[] scrollTicks = new int[TOTAL_STEPS];
        int t = 0;
        for (int s = 0; s < TOTAL_STEPS; s++) {
            double progress = (double) s / TOTAL_STEPS;
            int delay;
            if (fast) {
                if (progress < 0.50) delay = 1;
                else if (progress < 0.80) delay = 2;
                else if (progress < 0.95) delay = 3;
                else delay = 4;
            } else {
                if (progress < 0.35) delay = 1;
                else if (progress < 0.55) delay = 2;
                else if (progress < 0.70) delay = 3;
                else if (progress < 0.82) delay = 5;
                else if (progress < 0.90) delay = 8;
                else if (progress < 0.96) delay = 12;
                else delay = 18;
            }
            scrollTicks[s] = t;
            t += delay;
        }
        final int TOTAL_ANIM_TICKS = t + (fast ? 5 : 25); // extra ticks for celebration

        // ── Setup GUI decoration ──
        setupDecoration(inv, false);

        // Empty middle row initially
        for (int i = 9; i < 18; i++) inv.setItem(i, null);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.5f);
        player.sendTitle("", SmallCaps.convert("§a§l★ §e¡Ruleta girando! §a§l★"), 5, 40, 10);

        // ── Animation runnable ──
        new BukkitRunnable() {
            int tick = 0;
            int currentStep = 0;
            boolean celebrated = false;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    activeRoulettes.remove(uid);
                    rouletteInventories.remove(uid);
                    cancel();
                    return;
                }

                // Process scrolls
                while (currentStep < TOTAL_STEPS && scrollTicks[currentStep] <= tick) {
                    // Update middle row
                    for (int i = 0; i < 9; i++) {
                        int idx = currentStep + i;
                        if (idx < STRIP_LEN) {
                            inv.setItem(9 + i, createPrizeItem(strip[idx]));
                        }
                    }

                    // Sound — escalating pitch and changing instrument
                    double prog = (double) currentStep / TOTAL_STEPS;
                    if (prog < 0.70) {
                        float pitch = 0.5f + (float) prog * 1.5f;
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f, pitch);
                    } else if (prog < 0.90) {
                        float pitch = 1.0f + (float) (prog - 0.70) * 3.0f;
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, pitch);
                    } else {
                        // Last few: dramatic bell sounds
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.8f);
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.4f, 1.5f);
                    }

                    // Pulsing border effect near end
                    if (prog > 0.80) {
                        boolean gold = currentStep % 2 == 0;
                        Material borderMat = gold ? Material.YELLOW_STAINED_GLASS_PANE : Material.ORANGE_STAINED_GLASS_PANE;
                        for (int i = 0; i < 9; i++) {
                            inv.setItem(i, createGlass(borderMat, "§r"));
                            inv.setItem(18 + i, createGlass(borderMat, "§r"));
                        }
                        inv.setItem(4, createGlass(Material.RED_STAINED_GLASS_PANE, "§c§l▼ §e§lPREMIO §c§l▼"));
                        inv.setItem(22, createGlass(Material.RED_STAINED_GLASS_PANE, "§c§l▲ §e§lPREMIO §c§l▲"));
                    }

                    currentStep++;
                }

                // ── Celebration phase ──
                if (currentStep >= TOTAL_STEPS && !celebrated) {
                    celebrated = true;

                    // Golden border
                    setupDecoration(inv, true);

                    // Highlight winner slot with enchant glow effect
                    ItemStack winnerItem = createPrizeItem(winner);
                    ItemMeta wm = winnerItem.getItemMeta();
                    wm.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                    wm.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                    List<String> lore = new ArrayList<>();
                    lore.add("");
                    lore.add(winner.rarity + "Rareza: " + getRarityName(winner.rarity));
                    lore.add("");
                    lore.add("§a§l▶ §f§l¡TU PREMIO! §a§l◀");
                    lore.add("");
                    wm.setLore(lore);
                    wm.setDisplayName("§6§l✦ " + winner.color + "§l" + winner.name + " §6§l✦");
                    winnerItem.setItemMeta(wm);
                    inv.setItem(13, winnerItem);

                    // Sounds
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.2f);
                    player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1.0f, 1.0f);

                    // Title flash
                    player.sendTitle(
                        SmallCaps.convert(winner.rarity + "§l¡" + winner.name + "!"),
                        SmallCaps.convert("§a§l★ §ePremio de Conexión §a§l★"),
                        5, 50, 15
                    );

                    // Chat
                    player.sendMessage("");
                    player.sendMessage(SmallCaps.convert("§6§l✦✦✦ §e§l¡PREMIO DE CONEXIÓN! §6§l✦✦✦"));
                    player.sendMessage(SmallCaps.convert("§7Has ganado: " + winner.rarity + "§l" + winner.name));
                    player.sendMessage("");

                    givePrize(player, winner);
                }

                // Sparkle effect after celebration
                if (celebrated && tick % 5 == 0 && tick < TOTAL_ANIM_TICKS) {
                    boolean flash = ((tick / 5) % 2 == 0);
                    Material m = flash ? Material.YELLOW_STAINED_GLASS_PANE : Material.GOLD_BLOCK;
                    for (int i = 0; i < 9; i++) {
                        if (i != 4) {
                            inv.setItem(i, createGlass(m, "§6§l✦"));
                            inv.setItem(18 + i, createGlass(m, "§6§l✦"));
                        }
                    }
                }

                tick++;
                if (tick >= TOTAL_ANIM_TICKS) {
                    cancel();
                    boolean wasFast = fastModePlayers.remove(uid);
                    // Close inventory FIRST, then remove flag, then chain
                    if (player.isOnline()) player.closeInventory();
                    activeRoulettes.remove(uid);
                    rouletteInventories.remove(uid);
                    Runnable callback = onCompleteCallbacks.remove(uid);
                    if (callback != null) {
                        Bukkit.getScheduler().runTaskLater(plugin, callback, wasFast ? 5L : 20L);
                    }
                }
            }
        }.runTaskTimer(plugin, 5L, 1L);
    }

    private void setupDecoration(Inventory inv, boolean golden) {
        if (golden) {
            for (int i = 0; i < 9; i++) {
                inv.setItem(i, createGlass(Material.YELLOW_STAINED_GLASS_PANE, "§6§l✦"));
                inv.setItem(18 + i, createGlass(Material.YELLOW_STAINED_GLASS_PANE, "§6§l✦"));
            }
        } else {
            Material[] colors = {
                Material.LIME_STAINED_GLASS_PANE, Material.GREEN_STAINED_GLASS_PANE,
                Material.LIME_STAINED_GLASS_PANE, Material.GREEN_STAINED_GLASS_PANE,
                Material.LIME_STAINED_GLASS_PANE, Material.GREEN_STAINED_GLASS_PANE,
                Material.LIME_STAINED_GLASS_PANE, Material.GREEN_STAINED_GLASS_PANE,
                Material.LIME_STAINED_GLASS_PANE
            };
            for (int i = 0; i < 9; i++) {
                inv.setItem(i, createGlass(colors[i], "§r"));
                inv.setItem(18 + i, createGlass(colors[i], "§r"));
            }
        }
        inv.setItem(4, createGlass(Material.RED_STAINED_GLASS_PANE, "§c§l▼ §e§lPREMIO §c§l▼"));
        inv.setItem(22, createGlass(Material.RED_STAINED_GLASS_PANE, "§c§l▲ §e§lPREMIO §c§l▲"));
    }

    // ═══════════════════════════════════════════════════════════════
    //  GIVE PRIZE
    // ═══════════════════════════════════════════════════════════════

    private void givePrize(Player player, Prize prize) {
        String pName = player.getName();
        UUID uid = player.getUniqueId();
        String name = prize.name;

        if (name.startsWith("$")) {
            int amount = Integer.parseInt(name.replace("$", "").replace(",", "").replace(".", ""));
            plugin.depositWithMultiplier(player, amount);
        } else if (name.contains("Esencia")) {
            int amount = Integer.parseInt(name.split(" ")[0]);
            if (plugin.getEssenceManager() != null)
                plugin.getEssenceManager().addEssences(uid, amount);
        } else if (name.contains("Llave Common") || name.contains("Llave Bienvenida")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "excellentcrates key give " + pName + " common 1");
        } else if (name.contains("Llave Rare")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "excellentcrates key give " + pName + " rare 1");
        } else if (name.contains("Llave Special")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "excellentcrates key give " + pName + " special 1");
        } else if (name.contains("Llave Legendary")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "excellentcrates key give " + pName + " legendary 1");
        } else if (name.contains("Llave Moon")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "excellentcrates key give " + pName + " moon 1");
        } else {
            // Item prizes
            Material mat = prize.icon;
            int qty = 1;
            String n = name;
            try {
                String[] parts = n.split(" ", 2);
                qty = Integer.parseInt(parts[0]);
            } catch (Exception ignored) {}
            if (mat != Material.TRIPWIRE_HOOK && mat != Material.GOLD_NUGGET && mat != Material.NETHER_STAR
                    && mat != Material.AMETHYST_SHARD && mat != Material.AMETHYST_CLUSTER && mat != Material.AMETHYST_BLOCK) {
                ItemStack item = new ItemStack(mat, qty);
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                for (ItemStack drop : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
            }
        }
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
        if (onComplete != null) onCompleteCallbacks.put(uid, onComplete);
        openRoulette(player);
    }

    // ═══════════════════════════════════════════════════════════════
    //  EVENTS — block interaction during roulette
    // ═══════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals(TITLE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTitle().equals(TITLE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().equals(TITLE)) {
            UUID uid = event.getPlayer().getUniqueId();
            if (activeRoulettes.contains(uid)) {
                // Reopen the same inventory if they close during animation
                Inventory inv = rouletteInventories.get(uid);
                if (inv != null) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        Player p = Bukkit.getPlayer(uid);
                        if (p != null && p.isOnline() && activeRoulettes.contains(uid)) {
                            p.openInventory(inv);
                        }
                    }, 1L);
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  UTILITY
    // ═══════════════════════════════════════════════════════════════

    private ItemStack createGlass(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPrizeItem(Prize p) {
        ItemStack item = new ItemStack(p.icon);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(p.color + p.name);
        meta.setLore(Arrays.asList("", p.rarity + "Rareza: " + getRarityName(p.rarity)));
        item.setItemMeta(meta);
        return item;
    }

    private String getRarityName(String color) {
        if (color.equals("§7")) return "§7Común";
        if (color.equals("§a")) return "§aPoco Común";
        if (color.equals("§9")) return "§9Raro";
        if (color.equals("§5")) return "§5Épico";
        if (color.equals("§6")) return "§6§lLegendario";
        return "§7Común";
    }
}
