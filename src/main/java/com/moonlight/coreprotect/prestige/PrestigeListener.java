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
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class PrestigeListener implements Listener {

    private final CoreProtectPlugin plugin;
    private final Map<UUID, PendingPrestige> pendingChat = new HashMap<>();
    public static final String CONFIRM_TITLE = "§8✦ §c§l¿Prestigiar? §8✦";

    private static class PendingPrestige {
        final long timestamp;
        int confirmationsLeft;
        final int totalRequired;
        PendingPrestige(long timestamp, int required) {
            this.timestamp = timestamp;
            this.confirmationsLeft = required;
            this.totalRequired = required;
        }
    }

    public PrestigeListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    // ===========================
    // GUI CLICKS
    // ===========================

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle() == null) return;
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        PrestigeManager mgr = plugin.getPrestigeManager();
        if (mgr == null) return;

        String title = event.getView().getTitle();

        // Main prestige GUI
        if (title.equals(PrestigeManager.GUI_TITLE)) {
            event.setCancelled(true);
            int slot = event.getRawSlot();

            // Top button (slot 49)
            if (slot == 49 && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.DIAMOND) {
                mgr.openTopGUI(player);
                return;
            }

            // Prestige button (slot 47) — opens confirmation GUI
            if (slot == 47 && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.NETHER_STAR) {
                openConfirmGUI(player);
                return;
            }

            // Weekly missions button (slot 51)
            if (slot == 51 && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.CLOCK) {
                PrestigeWeeklyManager weekly = plugin.getPrestigeWeeklyManager();
                if (weekly != null) weekly.openGUI(player);
                return;
            }

            // Page navigation arrows
            if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.ARROW) {
                int currentPage = mgr.getGUIPage(player.getUniqueId());
                if (slot == 45) { // Previous page
                    mgr.openMainGUI(player, currentPage - 1);
                } else if (slot == 53) { // Next page
                    mgr.openMainGUI(player, currentPage + 1);
                }
                return;
            }

            // Click on current prestige (GOLD_INGOT or NETHER_STAR for current high-tier) -> open missions
            if (event.getCurrentItem() != null &&
                    (event.getCurrentItem().getType() == Material.GOLD_INGOT ||
                     (event.getCurrentItem().getType() == Material.NETHER_STAR && slot != 47))) {
                mgr.openMissionsGUI(player);
            }
            return;
        }

        // Missions GUI
        if (title.equals(PrestigeManager.MISSIONS_TITLE)) {
            event.setCancelled(true);
            // Back button
            if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.ARROW) {
                mgr.openMainGUI(player, mgr.getGUIPage(player.getUniqueId()));
            }
            return;
        }

        // Top GUI
        if (title.equals(PrestigeManager.TOP_TITLE)) {
            event.setCancelled(true);
            if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.ARROW) {
                mgr.openMainGUI(player, mgr.getGUIPage(player.getUniqueId()));
            }
            return;
        }

        // Confirmation GUI
        if (title.equals(CONFIRM_TITLE)) {
            event.setCancelled(true);
            int slot = event.getRawSlot();

            // Confirm button (slot 11) — magma cream
            if (slot == 11 && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.MAGMA_CREAM) {
                UUID uuid = player.getUniqueId();
                int currentP = plugin.getPrestigeManager().getData(uuid).prestige;
                int requiredConfirms = (currentP == 0) ? 5 : 2;
                pendingChat.put(uuid, new PendingPrestige(System.currentTimeMillis(), requiredConfirms));
                player.closeInventory();

                double money = plugin.getEconomy() != null ? plugin.getEconomy().getBalance(player) : 0;
                int ess = plugin.getEssenceManager() != null ? plugin.getEssenceManager().getEssences(uuid) : 0;
                int nextP = currentP + 1;

                player.sendMessage("");
                player.sendMessage(SmallCaps.convert("§4§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
                player.sendMessage(SmallCaps.convert("  §4§l⚠ CONFIRMACIÓN DE PRESTIGIO ⚠"));
                player.sendMessage("");
                player.sendMessage(SmallCaps.convert("  §c§lESTO ES IRREVERSIBLE."));
                player.sendMessage(SmallCaps.convert("  §fPerderás §c$" + String.format("%,.0f", money) + " §fy §c" + ess + " esencias§f."));
                player.sendMessage("");
                player.sendMessage(SmallCaps.convert("  §e§lEscribe §6§lPRESTIGIAR §e§l" + requiredConfirms + " veces para confirmar."));
                player.sendMessage(SmallCaps.convert("  §7Tienes 60 segundos. Cualquier otra cosa cancela."));
                player.sendMessage(SmallCaps.convert("  §fConfirmación §e1§f/§6" + requiredConfirms));
                player.sendMessage(SmallCaps.convert("§4§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
                player.sendMessage("");
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);
                return;
            }

            // Cancel button (slot 15) — barrier
            if (slot == 15 && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.BARRIER) {
                player.closeInventory();
                player.sendMessage(SmallCaps.convert("§c§l✘ §fPrestigio cancelado."));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
                return;
            }
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTitle() == null) return;
        String title = event.getView().getTitle();
        if (title.equals(PrestigeManager.GUI_TITLE) ||
            title.equals(PrestigeManager.MISSIONS_TITLE) ||
            title.equals(PrestigeManager.TOP_TITLE) ||
            title.equals(CONFIRM_TITLE)) {
            event.setCancelled(true);
        }
    }

    // ===========================
    // CHAT CONFIRMATION
    // ===========================

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        PendingPrestige pending = pendingChat.get(uuid);
        if (pending == null) return;

        event.setCancelled(true);

        if (System.currentTimeMillis() - pending.timestamp > 60000) {
            pendingChat.remove(uuid);
            player.sendMessage(SmallCaps.convert("§c§l✘ §fTiempo agotado. Prestigio cancelado."));
            return;
        }

        String msg = event.getMessage().trim();
        if (msg.equals("PRESTIGIAR")) {
            pending.confirmationsLeft--;
            if (pending.confirmationsLeft <= 0) {
                pendingChat.remove(uuid);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    PrestigeManager mgr = plugin.getPrestigeManager();
                    if (mgr != null && mgr.canPrestige(uuid)) {
                        mgr.doPrestige(player);
                    } else {
                        player.sendMessage(SmallCaps.convert("§c§l✘ §fNo puedes prestigiar ahora."));
                    }
                });
            } else {
                int done = pending.totalRequired - pending.confirmationsLeft;
                player.sendMessage(SmallCaps.convert("§a§l✔ §fConfirmación §e" + done + "§f/§6" + pending.totalRequired + " §7— Escribe §ePRESTIGIAR §7de nuevo."));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
            }
        } else {
            pendingChat.remove(uuid);
            player.sendMessage(SmallCaps.convert("§c§l✘ §fPrestigio cancelado. No escribiste §ePRESTIGIAR§f."));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingChat.remove(event.getPlayer().getUniqueId());
    }

    // ===========================
    // CONFIRMATION GUI
    // ===========================

    private void openConfirmGUI(Player player) {
        UUID uuid = player.getUniqueId();
        PrestigeManager mgr = plugin.getPrestigeManager();
        int nextP = mgr.getData(uuid).prestige + 1;
        double money = plugin.getEconomy() != null ? plugin.getEconomy().getBalance(player) : 0;
        int ess = plugin.getEssenceManager() != null ? plugin.getEssenceManager().getEssences(uuid) : 0;

        Inventory inv = Bukkit.createInventory(null, 27, CONFIRM_TITLE);

        // Fill with red glass
        ItemStack red = pane(Material.RED_STAINED_GLASS_PANE);
        for (int i = 0; i < 27; i++) inv.setItem(i, red);

        // Info item (center top)
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName(SmallCaps.convert("§c§l⚠ AVISO IMPORTANTE ⚠"));
        im.setLore(Arrays.asList(
                "",
                SmallCaps.convert("§fAscender a §6§lPrestigio " + nextP),
                "",
                SmallCaps.convert("§c§l¡SE RESTABLECERÁ TODO!"),
                "",
                SmallCaps.convert("§f• Dinero: §c$" + String.format("%,.0f", money) + " → §4$0"),
                SmallCaps.convert("§f• Esencias: §c" + ess + " → §40"),
                "",
                SmallCaps.convert("§7Esta acción es irreversible.")
        ));
        info.setItemMeta(im);
        inv.setItem(4, info);

        // Confirm button (slot 11)
        ItemStack confirm = new ItemStack(Material.MAGMA_CREAM);
        ItemMeta cm = confirm.getItemMeta();
        cm.setDisplayName(SmallCaps.convert("§a§l✔ CONFIRMAR PRESTIGIO"));
        cm.setLore(Arrays.asList(
                "",
                SmallCaps.convert("§fClick para continuar."),
                SmallCaps.convert("§7Deberás escribir §ePRESTIGIAR"),
                SmallCaps.convert("§7en el chat después de esto.")
        ));
        confirm.setItemMeta(cm);
        inv.setItem(11, confirm);

        // Cancel button (slot 15)
        ItemStack cancel = new ItemStack(Material.BARRIER);
        ItemMeta bm = cancel.getItemMeta();
        bm.setDisplayName(SmallCaps.convert("§c§l✘ CANCELAR"));
        bm.setLore(Arrays.asList("", SmallCaps.convert("§7Click para cancelar.")));
        cancel.setItemMeta(bm);
        inv.setItem(15, cancel);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.5f);
    }

    private static ItemStack pane(Material mat) {
        ItemStack i = new ItemStack(mat);
        ItemMeta m = i.getItemMeta();
        m.setDisplayName(" ");
        i.setItemMeta(m);
        return i;
    }

    // ===========================
    // MISSION TRACKING
    // ===========================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        checkAndCompleteMissions(player.getUniqueId(), "mine", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) return;
        Player killer = event.getEntity().getKiller();

        if (event.getEntity() instanceof Player) {
            checkAndCompleteMissions(killer.getUniqueId(), "pvp_kill", 1);
        } else if (event.getEntity() instanceof org.bukkit.entity.Monster) {
            checkAndCompleteMissions(killer.getUniqueId(), "kill_mob", 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            checkAndCompleteMissions(event.getPlayer().getUniqueId(), "fish", 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerEat(PlayerItemConsumeEvent event) {
        Material type = event.getItem().getType();
        if (type.isEdible()) {
            checkAndCompleteMissions(event.getPlayer().getUniqueId(), "eat", 1);
        }
    }

    /**
     * Checks all missions of the player's current prestige level for the given type.
     * Uses persistent progress stored in the data file.
     */
    private void checkAndCompleteMissions(UUID uuid, String type, int increment) {
        PrestigeManager mgr = plugin.getPrestigeManager();
        if (mgr == null) return;
        PrestigeManager.PrestigeData pd = mgr.getData(uuid);
        List<PrestigeManager.PrestigeMission> missions = mgr.getMissionsForPrestige(pd.prestige);

        for (PrestigeManager.PrestigeMission m : missions) {
            if (!m.type.equals(type)) continue;
            if (pd.completedMissions.contains(m.id)) continue;

            // For cumulative stats, we check against existing server data
            long current = getCurrentStat(uuid, type);
            if (current >= m.target) {
                mgr.completeMission(uuid, m.id);
            }
        }
    }

    /**
     * Gets the current cumulative stat for a player.
     * This uses Bukkit statistics and plugin data where available.
     */
    private long getCurrentStat(UUID uuid, String type) {
        Player player = org.bukkit.Bukkit.getPlayer(uuid);
        if (player == null) return 0;

        switch (type) {
            case "mine":
                return player.getStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.STONE) +
                       player.getStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.DEEPSLATE) +
                       player.getStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.DIRT) +
                       player.getStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.COBBLESTONE) +
                       player.getStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.GRANITE) +
                       player.getStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.DIORITE) +
                       player.getStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.ANDESITE) +
                       player.getStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.SAND) +
                       player.getStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.GRAVEL) +
                       player.getStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.COAL_ORE) +
                       player.getStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.IRON_ORE) +
                       player.getStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.GOLD_ORE) +
                       player.getStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.DIAMOND_ORE) +
                       player.getStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.EMERALD_ORE) +
                       player.getStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.LAPIS_ORE) +
                       player.getStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.REDSTONE_ORE) +
                       player.getStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.COPPER_ORE) +
                       player.getStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.NETHERRACK) +
                       player.getStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.NETHER_GOLD_ORE) +
                       player.getStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.ANCIENT_DEBRIS) +
                       player.getStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.OAK_LOG) +
                       player.getStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.SPRUCE_LOG) +
                       player.getStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.BIRCH_LOG) +
                       player.getStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.DARK_OAK_LOG) +
                       player.getStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.DEEPSLATE_COAL_ORE) +
                       player.getStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.DEEPSLATE_IRON_ORE) +
                       player.getStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.DEEPSLATE_GOLD_ORE) +
                       player.getStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.DEEPSLATE_DIAMOND_ORE) +
                       player.getStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.DEEPSLATE_EMERALD_ORE) +
                       player.getStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.DEEPSLATE_LAPIS_ORE) +
                       player.getStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.DEEPSLATE_REDSTONE_ORE) +
                       player.getStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.DEEPSLATE_COPPER_ORE);
            case "kill_mob":
                return player.getStatistic(org.bukkit.Statistic.MOB_KILLS);
            case "pvp_kill":
                return player.getStatistic(org.bukkit.Statistic.PLAYER_KILLS);
            case "fish":
                return player.getStatistic(org.bukkit.Statistic.FISH_CAUGHT);
            case "playtime":
                if (plugin.getPlayTimeRewardsManager() != null) {
                    return plugin.getPlayTimeRewardsManager().getPlayerTotalPlayTime(uuid);
                }
                return player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) / 20;
            case "earn_money":
                // Approximate: can't track total earned, but check current balance as fallback
                if (plugin.getEconomy() != null) {
                    return (long) plugin.getEconomy().getBalance(player);
                }
                return 0;
            case "streak":
                if (plugin.getDailyStreakManager() != null) {
                    return plugin.getDailyStreakManager().getStreak(uuid).bestStreak;
                }
                return 0;
            case "koth_win":
                return 0;
            case "killstreak":
                return plugin.getPrestigeManager().getData(uuid).bestKillstreak;
            case "prestige_kill":
                return plugin.getPrestigeManager().getData(uuid).prestigeKills;
            case "biomes":
                return plugin.getPrestigeManager().getData(uuid).discoveredBiomes.size();
            case "survive":
                return player.getStatistic(org.bukkit.Statistic.TIME_SINCE_DEATH) / 20 / 60;
            case "donate":
                return plugin.getPrestigeManager().getData(uuid).totalDonated;
            case "kda": {
                int k = player.getStatistic(org.bukkit.Statistic.PLAYER_KILLS);
                int d = Math.max(1, player.getStatistic(org.bukkit.Statistic.DEATHS));
                return Math.round((double) k / d * 100);
            }
            // ── CO-OP mission types ──
            case "coop_mine":    return plugin.getPrestigeManager().getData(uuid).coopMineBlocks;
            case "coop_kill":    return plugin.getPrestigeManager().getData(uuid).coopKills;
            case "coop_assist":  return plugin.getPrestigeManager().getData(uuid).coopAssists;
            case "heal_ally":    return plugin.getPrestigeManager().getData(uuid).healedAllies;
            case "buddy_walk":   return plugin.getPrestigeManager().getData(uuid).buddyDistanceCm / 100;
            case "coop_fish":    return plugin.getPrestigeManager().getData(uuid).coopFished;
            case "coop_boss":    return plugin.getPrestigeManager().getData(uuid).coopBossKills;
            case "coop_enchant": return plugin.getPrestigeManager().getData(uuid).coopEnchants;
            case "sync_kill":    return plugin.getPrestigeManager().getData(uuid).syncKills;
            case "gift_unique":  return plugin.getPrestigeManager().getData(uuid).giftedUniquePlayers.size();
            case "eat":
                long eatTotal = 0;
                for (Material m : Material.values()) {
                    if (m.isEdible()) {
                        try {
                            eatTotal += player.getStatistic(org.bukkit.Statistic.USE_ITEM, m);
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
                return eatTotal;
            default:
                return 0;
        }
    }
}
