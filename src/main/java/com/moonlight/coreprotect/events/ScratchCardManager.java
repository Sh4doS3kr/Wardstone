package com.moonlight.coreprotect.events;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Scratch card (Rasca y Gana) system.
 * Players randomly earn scratch cards from killing mobs and mining.
 * 3x3 grid — reveal all 9 tiles. Match 3+ of the same = win that prize.
 */
public class ScratchCardManager implements Listener {

    private final CoreProtectPlugin plugin;
    private final Map<UUID, ScratchSession> activeSessions = new HashMap<>();
    private final Set<UUID> cooldowns = new HashSet<>();
    private final Map<UUID, Runnable> onCompleteCallbacks = new HashMap<>();
    private final Set<UUID> autoRevealPlayers = new HashSet<>();

    public static final String TITLE = "§8✦ §e§l🎟 RASCA Y GANA §8✦";

    // Chance to earn a scratch card (1/CHANCE per action)
    private static final int CHANCE_MOB_KILL = 80;   // ~1.25% per mob kill
    private static final int CHANCE_MINING = 200;     // ~0.5% per block mined

    public ScratchCardManager(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    // ═══════════════════════════════════════════════════════════════
    //  PRIZE POOL
    // ═══════════════════════════════════════════════════════════════

    private record Prize(String name, Material icon, String color, int weight, String rarity) {}

    private static final List<Prize> PRIZE_POOL = List.of(
            // Common
            new Prize("$500", Material.GOLD_NUGGET, "§e", 30, "§7"),
            new Prize("$1,000", Material.GOLD_NUGGET, "§e", 25, "§7"),
            new Prize("5 Esencias", Material.AMETHYST_SHARD, "§d", 25, "§7"),
            new Prize("4 Hierro", Material.IRON_INGOT, "§f", 22, "§7"),
            new Prize("8 Pan", Material.BREAD, "§6", 20, "§7"),
            new Prize("2 Oro", Material.GOLD_INGOT, "§6", 20, "§7"),
            new Prize("1 XP Bottle", Material.EXPERIENCE_BOTTLE, "§a", 22, "§7"),

            // Uncommon
            new Prize("$3,000", Material.GOLD_INGOT, "§e", 12, "§a"),
            new Prize("15 Esencias", Material.AMETHYST_SHARD, "§d", 12, "§a"),
            new Prize("2 Diamantes", Material.DIAMOND, "§b", 10, "§a"),
            new Prize("Llave Common", Material.TRIPWIRE_HOOK, "§f", 10, "§a"),
            new Prize("5 XP Bottles", Material.EXPERIENCE_BOTTLE, "§a", 10, "§a"),

            // Rare
            new Prize("$10,000", Material.GOLD_BLOCK, "§6", 5, "§9"),
            new Prize("30 Esencias", Material.AMETHYST_CLUSTER, "§d", 5, "§9"),
            new Prize("4 Diamantes", Material.DIAMOND, "§b", 4, "§9"),
            new Prize("Llave Rare", Material.TRIPWIRE_HOOK, "§9", 4, "§9"),

            // Epic
            new Prize("$25,000", Material.DIAMOND_BLOCK, "§b", 2, "§5"),
            new Prize("50 Esencias", Material.AMETHYST_BLOCK, "§5", 2, "§5"),
            new Prize("Llave Special", Material.TRIPWIRE_HOOK, "§a", 2, "§5"),

            // Legendary
            new Prize("$50,000", Material.NETHER_STAR, "§6§l", 1, "§6"),
            new Prize("Llave Legendary", Material.TRIPWIRE_HOOK, "§6§l", 1, "§6")
    );

    private Prize randomPrize(Random rand) {
        int totalWeight = PRIZE_POOL.stream().mapToInt(Prize::weight).sum();
        int roll = rand.nextInt(totalWeight);
        int cum = 0;
        for (Prize p : PRIZE_POOL) {
            cum += p.weight;
            if (roll < cum) return p;
        }
        return PRIZE_POOL.get(0);
    }

    // ═══════════════════════════════════════════════════════════════
    //  SESSION
    // ═══════════════════════════════════════════════════════════════

    private static class ScratchSession {
        final Prize[] grid = new Prize[9]; // 3x3 prizes behind tiles
        final boolean[] revealed = new boolean[9];
        int revealedCount = 0;
        boolean finished = false;

        ScratchSession(List<Prize> pool, Random rand) {
            // Build grid — some duplicates for matching
            // Pick 4-5 distinct prizes, fill 9 slots
            List<Prize> chosen = new ArrayList<>();
            int distinctCount = 3 + rand.nextInt(3); // 3 to 5 distinct prizes
            Prize[] distincts = new Prize[distinctCount];
            for (int i = 0; i < distinctCount; i++) {
                int totalWeight = pool.stream().mapToInt(Prize::weight).sum();
                int roll = rand.nextInt(totalWeight);
                int cum = 0;
                for (Prize p : pool) {
                    cum += p.weight;
                    if (roll < cum) { distincts[i] = p; break; }
                }
                if (distincts[i] == null) distincts[i] = pool.get(0);
            }

            // Fill 9 slots, ensuring at least one triple exists
            // First, place a guaranteed triple of distincts[0]
            grid[0] = distincts[0];
            grid[1] = distincts[0];
            grid[2] = distincts[0];
            // Fill rest randomly from distincts
            for (int i = 3; i < 9; i++) {
                grid[i] = distincts[rand.nextInt(distinctCount)];
            }
            // Shuffle
            for (int i = 8; i > 0; i--) {
                int j = rand.nextInt(i + 1);
                Prize tmp = grid[i];
                grid[i] = grid[j];
                grid[j] = tmp;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  OPEN SCRATCH CARD
    // ═══════════════════════════════════════════════════════════════

    public void openCard(Player player) {
        UUID uid = player.getUniqueId();
        if (activeSessions.containsKey(uid)) return;

        ScratchSession session = new ScratchSession(PRIZE_POOL, new Random());
        activeSessions.put(uid, session);

        Inventory inv = Bukkit.createInventory(null, 27, TITLE);

        // ── Borders ──
        ItemStack border = createGlass(Material.YELLOW_STAINED_GLASS_PANE, "§e§l🎟 §6§lRasca y Gana §e§l🎟");
        for (int i = 0; i < 27; i++) inv.setItem(i, border);

        // ── 3x3 grid in center (slots: 10,11,12 / 13,14,15 / 16 mapped differently) ──
        // Actually use slots: row1=3,4,5 | row2=12,13,14 | row3=21,22,23
        // Better layout: center 3x3 in a 27-slot inv
        // Slots 10,11,12 (row 2 left-center)
        // Slots 13,14,15 (row 2 right — nope)
        // Let's use: row0=1,2,3 | row1=10,11,12 | row2=19,20,21 — no
        // Best: 3,4,5 | 12,13,14 | 21,22,23
        int[] gridSlots = {3, 4, 5, 12, 13, 14, 21, 22, 23};

        for (int i = 0; i < 9; i++) {
            inv.setItem(gridSlots[i], createHiddenTile(i + 1));
        }

        // ── Info ──
        ItemStack info = createGlass(Material.PAPER, "§e§lℹ §fRasca las 9 casillas");
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setLore(List.of(
                    "§7Haz clic en las casillas grises",
                    "§7para revelar los premios.",
                    "",
                    "§e¡Encuentra 3 iguales para ganar!"
            ));
            info.setItemMeta(infoMeta);
        }
        inv.setItem(0, info);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.5f);
        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.6f, 1.0f);
    }

    private static final int[] GRID_SLOTS = {3, 4, 5, 12, 13, 14, 21, 22, 23};

    private int getGridIndex(int slot) {
        for (int i = 0; i < GRID_SLOTS.length; i++) {
            if (GRID_SLOTS[i] == slot) return i;
        }
        return -1;
    }

    // ═══════════════════════════════════════════════════════════════
    //  CLICK HANDLER — REVEAL TILES
    // ═══════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(TITLE)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID uid = player.getUniqueId();
        ScratchSession session = activeSessions.get(uid);
        if (session == null || session.finished) return;

        // Bloquear clics manuales si está en auto-reveal
        if (autoRevealPlayers.contains(uid)) return;

        int gridIdx = getGridIndex(event.getRawSlot());
        if (gridIdx < 0) return; // Clicked border
        if (session.revealed[gridIdx]) return; // Already revealed

        // ── Reveal tile ──
        session.revealed[gridIdx] = true;
        session.revealedCount++;

        Prize prize = session.grid[gridIdx];
        Inventory inv = event.getInventory();

        // Animated reveal: briefly flash white, then show prize
        inv.setItem(GRID_SLOTS[gridIdx], createGlass(Material.WHITE_STAINED_GLASS_PANE, "§f§l?"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.0f + session.revealedCount * 0.1f);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            inv.setItem(GRID_SLOTS[gridIdx], createPrizeItem(prize));

            // Sound based on rarity
            switch (prize.rarity) {
                case "§6" -> {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
                    player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1f, 1f);
                }
                case "§5" -> player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
                case "§9" -> player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.5f);
                case "§a" -> player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.3f);
                default -> player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.0f);
            }

            // Check if all revealed
            if (session.revealedCount >= 9) {
                session.finished = true;
                Bukkit.getScheduler().runTaskLater(plugin, () -> finishCard(player, session, inv), 15L);
            }
        }, 4L);
    }

    // ═══════════════════════════════════════════════════════════════
    //  FINISH — FIND MATCHES & GIVE PRIZES
    // ═══════════════════════════════════════════════════════════════

    private void finishCard(Player player, ScratchSession session, Inventory inv) {
        if (!player.isOnline()) return;

        // Count occurrences of each prize
        Map<String, Integer> counts = new HashMap<>();
        Map<String, Prize> prizeMap = new HashMap<>();
        for (Prize p : session.grid) {
            counts.merge(p.name, 1, Integer::sum);
            prizeMap.put(p.name, p);
        }

        // Find best match (3+)
        Prize bestMatch = null;
        int bestCount = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() >= 3 && e.getValue() > bestCount) {
                bestCount = e.getValue();
                bestMatch = prizeMap.get(e.getKey());
            }
            // If same count, pick higher rarity
            if (e.getValue() >= 3 && e.getValue() == bestCount && bestMatch != null) {
                Prize candidate = prizeMap.get(e.getKey());
                if (getRarityRank(candidate.rarity) > getRarityRank(bestMatch.rarity)) {
                    bestMatch = candidate;
                }
            }
        }

        if (bestMatch == null) {
            // Shouldn't happen (we guarantee a triple), but fallback
            bestMatch = session.grid[0];
            bestCount = 3;
        }

        // ── Flash matching tiles ──
        final Prize winnerPrize = bestMatch;
        final int matchCount = bestCount;

        // Highlight matching tiles
        for (int i = 0; i < 9; i++) {
            if (session.grid[i].name.equals(winnerPrize.name)) {
                inv.setItem(GRID_SLOTS[i], createWinnerTile(winnerPrize, matchCount));
            }
        }

        // Flash animation
        new BukkitRunnable() {
            int flash = 0;
            @Override
            public void run() {
                if (!player.isOnline()) { cancel(); return; }
                if (flash >= 8) {
                    cancel();
                    giveReward(player, winnerPrize, matchCount);
                    return;
                }

                for (int i = 0; i < 9; i++) {
                    if (session.grid[i].name.equals(winnerPrize.name)) {
                        if (flash % 2 == 0) {
                            inv.setItem(GRID_SLOTS[i], createGlass(Material.LIME_STAINED_GLASS_PANE, "§a§l★ §e§l¡MATCH! §a§l★"));
                        } else {
                            inv.setItem(GRID_SLOTS[i], createWinnerTile(winnerPrize, matchCount));
                        }
                    }
                }

                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.0f + flash * 0.15f);
                flash++;
            }
        }.runTaskTimer(plugin, 3L, 5L);
    }

    private void giveReward(Player player, Prize prize, int matchCount) {
        UUID uid = player.getUniqueId();
        String pName = player.getName();
        String prizeName = prize.name;

        // Multiplier for match count: 3=x1, 4=x2, 5+=x3
        int multiplier = matchCount <= 3 ? 1 : matchCount <= 4 ? 2 : 3;
        String multLabel = multiplier > 1 ? " §6§l(x" + multiplier + ")" : "";

        // Deliver reward
        if (prizeName.contains("$")) {
            int amount = parseNumber(prizeName) * multiplier;
            if (plugin.getEconomy() != null) plugin.getEconomy().depositPlayer(player, amount);
        } else if (prizeName.contains("Esencias") || prizeName.contains("esencias")) {
            int amount = parseNumber(prizeName) * multiplier;
            if (plugin.getEssenceManager() != null)
                plugin.getEssenceManager().addEssences(uid, amount);
        } else if (prizeName.contains("Llave Common")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "excellentcrates key give " + pName + " common " + multiplier);
        } else if (prizeName.contains("Llave Rare")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "excellentcrates key give " + pName + " rare " + multiplier);
        } else if (prizeName.contains("Llave Special")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "excellentcrates key give " + pName + " special " + multiplier);
        } else if (prizeName.contains("Llave Legendary")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "excellentcrates key give " + pName + " legendary " + multiplier);
        } else {
            // Physical items
            int amount = parseNumber(prizeName) * multiplier;
            if (amount <= 0) amount = 1;
            ItemStack item = new ItemStack(prize.icon, Math.min(amount, 64));
            HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            for (ItemStack left : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        }

        // Messages
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§e§l🎟 §6§l¡RASCA Y GANA! §f" + matchCount + " iguales → " + prize.color + prizeName + multLabel));
        player.sendMessage("");
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 1f);

        // Broadcast if rare+
        if (prize.rarity.equals("§5") || prize.rarity.equals("§6")) {
            Bukkit.broadcastMessage(SmallCaps.convert(
                    "§e§l🎟 §f" + pName + " §7ha ganado " + prize.color + prizeName + multLabel + " §7en un Rasca y Gana!"));
        }

        // Cleanup
        activeSessions.remove(uid);
        Runnable callback = onCompleteCallbacks.remove(uid);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) player.closeInventory();
            if (callback != null) {
                Bukkit.getScheduler().runTaskLater(plugin, callback, 10L);
            }
        }, 30L);
    }

    // ═══════════════════════════════════════════════════════════════
    //  EARNING SCRATCH CARDS — random drops
    // ═══════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobKill(EntityDeathEvent event) {
        if (event instanceof PlayerDeathEvent) return;
        if (event.getEntity().getKiller() == null) return;
        Player player = event.getEntity().getKiller();
        tryAwardCard(player, CHANCE_MOB_KILL);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Material type = event.getBlock().getType();
        // Only count "real" mining — ores and stone variants
        if (type == Material.STONE || type == Material.DEEPSLATE || type == Material.COAL_ORE
                || type == Material.IRON_ORE || type == Material.GOLD_ORE || type == Material.DIAMOND_ORE
                || type == Material.EMERALD_ORE || type == Material.LAPIS_ORE || type == Material.REDSTONE_ORE
                || type == Material.COPPER_ORE || type == Material.DEEPSLATE_COAL_ORE
                || type == Material.DEEPSLATE_IRON_ORE || type == Material.DEEPSLATE_GOLD_ORE
                || type == Material.DEEPSLATE_DIAMOND_ORE || type == Material.DEEPSLATE_EMERALD_ORE
                || type == Material.DEEPSLATE_LAPIS_ORE || type == Material.DEEPSLATE_REDSTONE_ORE
                || type == Material.DEEPSLATE_COPPER_ORE || type == Material.NETHER_QUARTZ_ORE
                || type == Material.NETHER_GOLD_ORE || type == Material.ANCIENT_DEBRIS) {
            tryAwardCard(event.getPlayer(), CHANCE_MINING);
        }
    }

    private void tryAwardCard(Player player, int chance) {
        UUID uid = player.getUniqueId();
        if (cooldowns.contains(uid)) return;
        if (activeSessions.containsKey(uid)) return;

        if (new Random().nextInt(chance) != 0) return;

        // Award! Apply 5-minute cooldown
        cooldowns.add(uid);
        Bukkit.getScheduler().runTaskLater(plugin, () -> cooldowns.remove(uid), 5 * 60 * 20L);

        // Add to pending instead of auto-opening
        com.moonlight.coreprotect.rewards.PendingRewardsManager prm = plugin.getPendingRewardsManager();
        if (prm != null) {
            prm.addScratchCard(player);
        }
    }

    /**
     * Opens a scratch card from the pending rewards system, with a callback for chaining.
     */
    public void openCardFromPending(Player player, Runnable onComplete) {
        openCardFromPending(player, onComplete, false);
    }

    public void openCardFromPending(Player player, Runnable onComplete, boolean autoReveal) {
        UUID uid = player.getUniqueId();
        if (activeSessions.containsKey(uid)) {
            if (onComplete != null) onComplete.run();
            return;
        }
        if (onComplete != null) onCompleteCallbacks.put(uid, onComplete);
        if (autoReveal) autoRevealPlayers.add(uid);
        openCard(player);

        // Si es auto-reveal, revelar casillas una por una automáticamente
        if (autoReveal) {
            ScratchSession session = activeSessions.get(uid);
            if (session != null) {
                startAutoReveal(player, session);
            }
        }
    }

    private void startAutoReveal(Player player, ScratchSession session) {
        UUID uid = player.getUniqueId();
        // Generar orden aleatorio de casillas
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < 9; i++) order.add(i);
        Collections.shuffle(order);

        new BukkitRunnable() {
            int idx = 0;
            @Override
            public void run() {
                if (!player.isOnline() || !activeSessions.containsKey(uid) || session.finished) {
                    autoRevealPlayers.remove(uid);
                    cancel();
                    return;
                }

                if (idx >= order.size()) {
                    autoRevealPlayers.remove(uid);
                    cancel();
                    return;
                }

                int gridIdx = order.get(idx);
                if (session.revealed[gridIdx]) {
                    idx++;
                    return; // ya revelada, pasar a la siguiente en el próximo tick
                }

                // Revelar esta casilla
                session.revealed[gridIdx] = true;
                session.revealedCount++;
                Prize prize = session.grid[gridIdx];

                Inventory inv = player.getOpenInventory().getTopInventory();
                inv.setItem(GRID_SLOTS[gridIdx], createGlass(Material.WHITE_STAINED_GLASS_PANE, "§f§l?"));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.0f + session.revealedCount * 0.1f);

                final int gIdx = gridIdx;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!player.isOnline()) return;
                    inv.setItem(GRID_SLOTS[gIdx], createPrizeItem(prize));

                    switch (prize.rarity) {
                        case "§6" -> {
                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
                            player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1f, 1f);
                        }
                        case "§5" -> player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
                        case "§9" -> player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.5f);
                        case "§a" -> player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.3f);
                        default -> player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.0f);
                    }

                    if (session.revealedCount >= 9) {
                        session.finished = true;
                        Bukkit.getScheduler().runTaskLater(plugin, () -> finishCard(player, session, inv), 15L);
                    }
                }, 4L);

                idx++;
            }
        }.runTaskTimer(plugin, 10L, 8L); // 10 ticks inicio, 8 ticks entre cada casilla
    }

    // ═══════════════════════════════════════════════════════════════
    //  EVENT PROTECTION
    // ═══════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTitle().equals(TITLE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!event.getView().getTitle().equals(TITLE)) return;
        UUID uid = event.getPlayer().getUniqueId();
        ScratchSession session = activeSessions.get(uid);
        if (session != null && !session.finished) {
            // Re-open if not finished
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player p = Bukkit.getPlayer(uid);
                if (p != null && p.isOnline() && activeSessions.containsKey(uid)) {
                    // Rebuild and reopen the inventory
                    ScratchSession s = activeSessions.get(uid);
                    if (s != null && !s.finished) {
                        Inventory inv = Bukkit.createInventory(null, 27, TITLE);
                        ItemStack border = createGlass(Material.YELLOW_STAINED_GLASS_PANE, "§e§l🎟 §6§lRasca y Gana §e§l🎟");
                        for (int i = 0; i < 27; i++) inv.setItem(i, border);
                        for (int i = 0; i < 9; i++) {
                            if (s.revealed[i]) {
                                inv.setItem(GRID_SLOTS[i], createPrizeItem(s.grid[i]));
                            } else {
                                inv.setItem(GRID_SLOTS[i], createHiddenTile(i + 1));
                            }
                        }
                        p.openInventory(inv);
                    }
                }
            }, 1L);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════════

    private ItemStack createHiddenTile(int number) {
        ItemStack item = new ItemStack(Material.GRAY_CONCRETE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§7§l? §8Casilla " + number + " §7§l?");
            meta.setLore(List.of("", "§eHaz clic para rascar", ""));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createPrizeItem(Prize prize) {
        ItemStack item = new ItemStack(prize.icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(prize.color + prize.name);
            meta.setLore(List.of(prize.rarity + getRarityLabel(prize.rarity)));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createWinnerTile(Prize prize, int matchCount) {
        ItemStack item = new ItemStack(prize.icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§a§l★ " + prize.color + prize.name + " §a§l★");
            meta.setLore(List.of(
                    prize.rarity + getRarityLabel(prize.rarity),
                    "",
                    "§a§l¡" + matchCount + " IGUALES!"
            ));
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

    private String getRarityLabel(String rarity) {
        return switch (rarity) {
            case "§7" -> "§8[Común]";
            case "§a" -> "§a[Poco Común]";
            case "§9" -> "§9[Raro]";
            case "§5" -> "§5[Épico]";
            case "§6" -> "§6§l[Legendario]";
            default -> "";
        };
    }

    private int getRarityRank(String rarity) {
        return switch (rarity) {
            case "§7" -> 0;
            case "§a" -> 1;
            case "§9" -> 2;
            case "§5" -> 3;
            case "§6" -> 4;
            default -> 0;
        };
    }

    private int parseNumber(String s) {
        try {
            String nums = s.replaceAll("[^0-9]", "");
            return nums.isEmpty() ? 1 : Integer.parseInt(nums);
        } catch (Exception e) { return 1; }
    }
}
