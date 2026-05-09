package com.moonlight.coreprotect.prestige;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Animated roulette that opens when a player prestiges.
 * Scrolls through prizes and lands on a random reward.
 */
public class PrestigeRouletteManager implements Listener {

    private final CoreProtectPlugin plugin;
    private final Set<UUID> activeRoulettes = new HashSet<>();
    public static final String ROULETTE_TITLE = "§8✦ §6§lRuleta de Prestigio §8✦";

    public PrestigeRouletteManager(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    // ═══════════════════════════════════════
    // PRIZE DEFINITIONS
    // ═══════════════════════════════════════

    private record Prize(String name, Material icon, String color, int weight) {}

    private List<Prize> getPrizePool(int prestige) {
        List<Prize> pool = new ArrayList<>();
        // Common prizes (always available)
        pool.add(new Prize("$5,000", Material.GOLD_NUGGET, "§e", 25));
        pool.add(new Prize("20 Esencias", Material.AMETHYST_SHARD, "§d", 25));
        pool.add(new Prize("Llave Common", Material.TRIPWIRE_HOOK, "§f", 20));

        // Scaling prizes
        if (prestige >= 10) {
            pool.add(new Prize("$15,000", Material.GOLD_INGOT, "§e", 15));
            pool.add(new Prize("50 Esencias", Material.AMETHYST_CLUSTER, "§d", 15));
            pool.add(new Prize("Llave Rare", Material.TRIPWIRE_HOOK, "§9", 12));
        }
        if (prestige >= 20) {
            pool.add(new Prize("$30,000", Material.GOLD_BLOCK, "§6", 10));
            pool.add(new Prize("100 Esencias", Material.AMETHYST_BLOCK, "§5", 10));
            pool.add(new Prize("Llave Special", Material.TRIPWIRE_HOOK, "§a", 8));
        }
        if (prestige >= 35) {
            pool.add(new Prize("$50,000", Material.DIAMOND, "§b", 8));
            pool.add(new Prize("200 Esencias", Material.DIAMOND_BLOCK, "§b", 6));
            pool.add(new Prize("Llave Legendary", Material.TRIPWIRE_HOOK, "§6", 5));
        }
        if (prestige >= 50) {
            pool.add(new Prize("Llave Moon", Material.END_CRYSTAL, "§5", 3));
            pool.add(new Prize("Vuelo 1 día", Material.FEATHER, "§b", 2));
        }
        if (prestige >= 60) {
            pool.add(new Prize("Llave Espadas", Material.DIAMOND_SWORD, "§c", 2));
            pool.add(new Prize("500 Esencias", Material.NETHER_STAR, "§e", 2));
        }

        // Jackpot (rare)
        pool.add(new Prize("§l¡JACKPOT! §r§ex2 Recompensa", Material.NETHER_STAR, "§6§l", 1));

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

    // ═══════════════════════════════════════
    // ROULETTE ANIMATION
    // ═══════════════════════════════════════

    public void openRoulette(Player player, int prestige) {
        openRouletteInternal(player, prestige, 1);
    }

    private void openRouletteInternal(Player player, int prestige, int rollNumber) {
        UUID uid = player.getUniqueId();
        activeRoulettes.add(uid);

        Inventory inv = Bukkit.createInventory(null, 27, ROULETTE_TITLE);

        // P30+: Gold enhanced borders / Normal: black
        boolean enhanced = prestige >= 30;
        Material borderMat = enhanced ? Material.YELLOW_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE;
        ItemStack borderPane = createGlass(borderMat, enhanced ? "§6§l✦" : "§r");
        for (int i = 0; i < 9; i++) inv.setItem(i, borderPane);
        for (int i = 18; i < 27; i++) inv.setItem(i, borderPane);

        // Markers
        String markerLabel = rollNumber > 1 ? "§6§l▼ ¡DOBLE! ▼" : "§c§l▼ PREMIO ▼";
        String markerLabelUp = rollNumber > 1 ? "§6§l▲ ¡DOBLE! ▲" : "§c§l▲ PREMIO ▲";
        inv.setItem(4, createGlass(Material.RED_STAINED_GLASS_PANE, markerLabel));
        inv.setItem(22, createGlass(Material.RED_STAINED_GLASS_PANE, markerLabelUp));

        // Fill middle row with random prizes initially
        List<Prize> pool = getPrizePool(prestige);
        Prize winner = selectWinner(pool);
        Random rand = new Random();

        for (int i = 9; i <= 17; i++) {
            inv.setItem(i, createPrizeItem(pool.get(rand.nextInt(pool.size()))));
        }

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);

        // P30+ enhanced: longer animation (55 shifts vs 45)
        int totalShifts = enhanced ? 55 : 45;
        List<Prize> sequence = new ArrayList<>();
        for (int i = 0; i < totalShifts; i++) {
            if (i == totalShifts - 5) {
                sequence.add(winner); // will land in slot 13
            } else {
                sequence.add(pool.get(rand.nextInt(pool.size())));
            }
        }

        // Start animation
        new RouletteAnimation(player, inv, sequence, winner, prestige, rollNumber).runTaskTimer(plugin, 2L, 1L);
    }

    private class RouletteAnimation extends BukkitRunnable {
        private final Player player;
        private final Inventory inv;
        private final List<Prize> sequence;
        private final Prize winner;
        private final int prestige;
        private int tick = 0;
        private int step = 0;
        private int nextStepAt = 0;
        private final int totalSteps;

        private final int rollNumber;

        RouletteAnimation(Player player, Inventory inv, List<Prize> sequence, Prize winner, int prestige, int rollNumber) {
            this.player = player;
            this.inv = inv;
            this.sequence = sequence;
            this.winner = winner;
            this.prestige = prestige;
            this.totalSteps = sequence.size();
            this.rollNumber = rollNumber;
        }

        @Override
        public void run() {
            if (!player.isOnline() || !activeRoulettes.contains(player.getUniqueId())) {
                cancel();
                return;
            }

            tick++;
            if (tick < nextStepAt) return;

            // Shift middle row left
            for (int i = 9; i < 17; i++) {
                inv.setItem(i, inv.getItem(i + 1));
            }
            inv.setItem(17, createPrizeItem(sequence.get(step)));

            // Sound
            float pitch = 1.0f + (step / (float) totalSteps) * 0.5f;
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, pitch);

            step++;
            nextStepAt = tick + getDelay(step, totalSteps);

            if (step >= totalSteps) {
                // Animation done — flash winner
                cancel();
                flashWinner();
            }
        }

        private int getDelay(int step, int total) {
            float progress = (float) step / total;
            if (progress < 0.45) return 1;
            if (progress < 0.65) return 2;
            if (progress < 0.80) return 3;
            if (progress < 0.90) return 5;
            if (progress < 0.96) return 8;
            return 13;
        }

        private void flashWinner() {
            // Flash the center item
            new BukkitRunnable() {
                int flash = 0;
                @Override
                public void run() {
                    if (!player.isOnline()) { cancel(); return; }
                    if (flash >= 6) {
                        // Give prize
                        cancel();
                        givePrize(player, winner, prestige, rollNumber);
                        return;
                    }

                    if (flash % 2 == 0) {
                        inv.setItem(13, createGlass(Material.YELLOW_STAINED_GLASS_PANE, "§e§l★ ★ ★"));
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
                    } else {
                        inv.setItem(13, createPrizeItem(winner));
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.0f);
                    }
                    flash++;
                }
            }.runTaskTimer(plugin, 5L, 5L);
        }
    }

    // ═══════════════════════════════════════
    // GIVE PRIZE
    // ═══════════════════════════════════════

    private void givePrize(Player player, Prize prize, int prestige, int rollNumber) {
        String pName = player.getName();
        String prizeName = prize.name;

        if (prizeName.contains("$")) {
            int amount = parseMoneyAmount(prizeName);
            if (plugin.getEconomy() != null) plugin.getEconomy().depositPlayer(player, amount);
        } else if (prizeName.contains("Esencias")) {
            int amount = parseNumber(prizeName);
            if (plugin.getEssenceManager() != null)
                plugin.getEssenceManager().addEssences(player.getUniqueId(), amount);
        } else if (prizeName.contains("Llave Common")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "excellentcrates key give " + pName + " common 1");
        } else if (prizeName.contains("Llave Rare")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "excellentcrates key give " + pName + " rare 1");
        } else if (prizeName.contains("Llave Special")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "excellentcrates key give " + pName + " special 1");
        } else if (prizeName.contains("Llave Legendary")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "excellentcrates key give " + pName + " legendary 1");
        } else if (prizeName.contains("Llave Moon")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "excellentcrates key give " + pName + " moon 1");
        } else if (prizeName.contains("Llave Espadas")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "excellentcrates key give " + pName + " llave_espadas 1");
        } else if (prizeName.contains("Vuelo")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "lp user " + pName + " permission settemp essentials.fly true 1d");
        } else if (prizeName.contains("JACKPOT")) {
            // Double the base reward
            PrestigeManager.PrestigeReward reward = plugin.getPrestigeManager().getRewardForPrestige(prestige);
            if (plugin.getEconomy() != null) plugin.getEconomy().depositPlayer(player, reward.money);
            if (plugin.getEssenceManager() != null)
                plugin.getEssenceManager().addEssences(player.getUniqueId(), reward.essences);
            if (reward.keyAmount > 0) Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "excellentcrates key give " + pName + " " + reward.keyType + " " + reward.keyAmount);
        }

        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§6§l★ §eRuleta: §f¡Has ganado " + prize.color + prizeName + "§f!"));
        player.sendMessage("");
        String rollLabel = rollNumber > 1 ? " §7(2ª tirada)" : "";
        Bukkit.broadcastMessage(SmallCaps.convert("§6§l★ §e" + pName + " §fha ganado " + prize.color + prizeName + " §fen la ruleta de prestigio!" + rollLabel));

        // P50+: Double roulette — schedule second roll
        if (prestige >= 50 && rollNumber == 1) {
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("§6§l★ §e§l¡DOBLE RULETA! §fTienes otra tirada..."));
            player.sendMessage("");
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                activeRoulettes.remove(player.getUniqueId());
                if (player.isOnline()) openRouletteInternal(player, prestige, 2);
            }, 50L);
        } else {
            activeRoulettes.remove(player.getUniqueId());
            Bukkit.getScheduler().runTaskLater(plugin, (Runnable) player::closeInventory, 20L);
        }
    }

    private int parseMoneyAmount(String s) {
        try { return Integer.parseInt(s.replaceAll("[^0-9]", "")); }
        catch (Exception e) { return 5000; }
    }
    private int parseNumber(String s) {
        try { return Integer.parseInt(s.replaceAll("[^0-9]", "")); }
        catch (Exception e) { return 20; }
    }

    // ═══════════════════════════════════════
    // EVENT PROTECTION (cancel clicks during roulette)
    // ═══════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRouletteClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals(ROULETTE_TITLE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRouletteDrag(InventoryDragEvent event) {
        if (event.getView().getTitle().equals(ROULETTE_TITLE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onRouletteClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().equals(ROULETTE_TITLE)) {
            UUID uid = event.getPlayer().getUniqueId();
            if (activeRoulettes.contains(uid)) {
                // Re-open if animation still running
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Player p = Bukkit.getPlayer(uid);
                    if (p != null && p.isOnline() && activeRoulettes.contains(uid)) {
                        // Let them close after animation is done
                    }
                }, 1L);
            }
        }
    }

    // ═══════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════

    private ItemStack createPrizeItem(Prize prize) {
        ItemStack item = new ItemStack(prize.icon);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(prize.color + prize.name);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createGlass(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }
}
