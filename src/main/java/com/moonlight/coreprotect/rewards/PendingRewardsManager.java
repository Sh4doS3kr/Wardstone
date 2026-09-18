package com.moonlight.coreprotect.rewards;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
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
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages pending roulettes and scratch cards.
 * Instead of auto-opening, rewards accumulate and players open them via /ruleta.
 * Shows a menu with pending rewards and options to open one by one or all at once.
 */
public class PendingRewardsManager implements Listener, CommandExecutor {

    private final CoreProtectPlugin plugin;
    private final File dataFile;

    // Pending counts per player
    private final Map<UUID, Integer> pendingDailyRoulettes = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> pendingPlaytimeRoulettes = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> pendingScratchCards = new ConcurrentHashMap<>();

    // Track if a player is currently opening a sequential batch
    private final Set<UUID> openingSequence = ConcurrentHashMap.newKeySet();
    // Track if a player has the /ruleta menu open
    private final Set<UUID> menuOpen = ConcurrentHashMap.newKeySet();

    public static final String MENU_TITLE = "§8✦ §6§l★ §e§lMIS RULETAS §6§l★ §8✦";

    public PendingRewardsManager(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "pending_rewards.yml");
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
                pendingDailyRoulettes.put(uuid, yaml.getInt(key + ".daily", 0));
                pendingPlaytimeRoulettes.put(uuid, yaml.getInt(key + ".playtime", 0));
                pendingScratchCards.put(uuid, yaml.getInt(key + ".scratch", 0));
            } catch (Exception ignored) {}
        }
    }

    public void saveData() {
        YamlConfiguration yaml = new YamlConfiguration();
        Set<UUID> allUuids = new HashSet<>();
        allUuids.addAll(pendingDailyRoulettes.keySet());
        allUuids.addAll(pendingPlaytimeRoulettes.keySet());
        allUuids.addAll(pendingScratchCards.keySet());
        for (UUID uuid : allUuids) {
            int daily = pendingDailyRoulettes.getOrDefault(uuid, 0);
            int playtime = pendingPlaytimeRoulettes.getOrDefault(uuid, 0);
            int scratch = pendingScratchCards.getOrDefault(uuid, 0);
            if (daily > 0 || playtime > 0 || scratch > 0) {
                yaml.set(uuid.toString() + ".daily", daily);
                yaml.set(uuid.toString() + ".playtime", playtime);
                yaml.set(uuid.toString() + ".scratch", scratch);
            }
        }
        try {
            yaml.save(dataFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("[PendingRewards] Error saving: " + ex.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  ADD PENDING REWARDS
    // ═══════════════════════════════════════════════════════════════

    public void addDailyRoulette(Player player) {
        UUID uid = player.getUniqueId();
        pendingDailyRoulettes.merge(uid, 1, Integer::sum);
        saveData();
        notifyPlayer(player);
    }

    public void addPlaytimeRoulette(Player player) {
        UUID uid = player.getUniqueId();
        pendingPlaytimeRoulettes.merge(uid, 1, Integer::sum);
        saveData();
        notifyPlayer(player);
    }

    public void addScratchCard(Player player) {
        UUID uid = player.getUniqueId();
        pendingScratchCards.merge(uid, 1, Integer::sum);
        saveData();
        notifyPlayer(player);
    }

    public int getTotalPending(UUID uid) {
        return pendingDailyRoulettes.getOrDefault(uid, 0)
             + pendingPlaytimeRoulettes.getOrDefault(uid, 0)
             + pendingScratchCards.getOrDefault(uid, 0);
    }

    // ═══════════════════════════════════════════════════════════════
    //  CONSUME (take one pending reward)
    // ═══════════════════════════════════════════════════════════════

    /** Returns "daily", "playtime", "scratch", or null if none pending */
    public String consumeNext(UUID uid) {
        if (pendingDailyRoulettes.getOrDefault(uid, 0) > 0) {
            pendingDailyRoulettes.merge(uid, -1, Integer::sum);
            if (pendingDailyRoulettes.getOrDefault(uid, 0) <= 0) pendingDailyRoulettes.remove(uid);
            saveData();
            return "daily";
        }
        if (pendingPlaytimeRoulettes.getOrDefault(uid, 0) > 0) {
            pendingPlaytimeRoulettes.merge(uid, -1, Integer::sum);
            if (pendingPlaytimeRoulettes.getOrDefault(uid, 0) <= 0) pendingPlaytimeRoulettes.remove(uid);
            saveData();
            return "playtime";
        }
        if (pendingScratchCards.getOrDefault(uid, 0) > 0) {
            pendingScratchCards.merge(uid, -1, Integer::sum);
            if (pendingScratchCards.getOrDefault(uid, 0) <= 0) pendingScratchCards.remove(uid);
            saveData();
            return "scratch";
        }
        return null;
    }

    public String consumeType(UUID uid, String type) {
        switch (type) {
            case "daily":
                if (pendingDailyRoulettes.getOrDefault(uid, 0) > 0) {
                    pendingDailyRoulettes.merge(uid, -1, Integer::sum);
                    if (pendingDailyRoulettes.getOrDefault(uid, 0) <= 0) pendingDailyRoulettes.remove(uid);
                    saveData();
                    return "daily";
                }
                break;
            case "playtime":
                if (pendingPlaytimeRoulettes.getOrDefault(uid, 0) > 0) {
                    pendingPlaytimeRoulettes.merge(uid, -1, Integer::sum);
                    if (pendingPlaytimeRoulettes.getOrDefault(uid, 0) <= 0) pendingPlaytimeRoulettes.remove(uid);
                    saveData();
                    return "playtime";
                }
                break;
            case "scratch":
                if (pendingScratchCards.getOrDefault(uid, 0) > 0) {
                    pendingScratchCards.merge(uid, -1, Integer::sum);
                    if (pendingScratchCards.getOrDefault(uid, 0) <= 0) pendingScratchCards.remove(uid);
                    saveData();
                    return "scratch";
                }
                break;
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════
    //  NOTIFICATION
    // ═══════════════════════════════════════════════════════════════

    private void notifyPlayer(Player player) {
        int total = getTotalPending(player.getUniqueId());
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§6§l★ §e§l¡TIENES " + total + " RECOMPENSA" + (total > 1 ? "S" : "") + " PENDIENTE" + (total > 1 ? "S" : "") + "! §6§l★"));
        player.sendMessage(SmallCaps.convert("§7Usa §f/ruleta §7para abrirlas."));
        player.sendMessage("");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
    }

    // ═══════════════════════════════════════════════════════════════
    //  COMMAND /ruleta
    // ═══════════════════════════════════════════════════════════════

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSolo jugadores.");
            return true;
        }

        UUID uid = player.getUniqueId();
        int total = getTotalPending(uid);

        if (total <= 0) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo tienes ruletas ni rascas pendientes."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return true;
        }

        openMenu(player);
        return true;
    }

    // ═══════════════════════════════════════════════════════════════
    //  MENU GUI
    // ═══════════════════════════════════════════════════════════════

    private void openMenu(Player player) {
        UUID uid = player.getUniqueId();
        menuOpen.add(uid);

        int daily = pendingDailyRoulettes.getOrDefault(uid, 0);
        int playtime = pendingPlaytimeRoulettes.getOrDefault(uid, 0);
        int scratch = pendingScratchCards.getOrDefault(uid, 0);
        int total = daily + playtime + scratch;

        Inventory inv = Bukkit.createInventory(null, 27, MENU_TITLE);

        // Border
        ItemStack border = createGlass(Material.ORANGE_STAINED_GLASS_PANE, "§r");
        for (int i = 0; i < 27; i++) inv.setItem(i, border);

        // ── Slot 10: Ruleta Diaria ──
        if (daily > 0) {
            ItemStack item = new ItemStack(Material.SUNFLOWER, Math.min(daily, 64));
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§6§l★ Ruleta Diaria §7(x" + daily + ")");
            meta.setLore(List.of(
                "",
                "§7Tienes §e" + daily + " §7ruleta" + (daily > 1 ? "s" : "") + " diaria" + (daily > 1 ? "s" : ""),
                "",
                "§aClick izquierdo §7→ Abrir 1",
                "§eClick derecho §7→ Abrir todas",
                ""
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
            inv.setItem(11, item);
        } else {
            ItemStack item = new ItemStack(Material.GRAY_DYE);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§8Ruleta Diaria §7(0)");
            meta.setLore(List.of("", "§7No tienes ruletas diarias", ""));
            item.setItemMeta(meta);
            inv.setItem(11, item);
        }

        // ── Slot 13: Ruleta de Conexión ──
        if (playtime > 0) {
            ItemStack item = new ItemStack(Material.CLOCK, Math.min(playtime, 64));
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§a§l★ Ruleta de Conexión §7(x" + playtime + ")");
            meta.setLore(List.of(
                "",
                "§7Tienes §e" + playtime + " §7ruleta" + (playtime > 1 ? "s" : "") + " de conexión",
                "",
                "§aClick izquierdo §7→ Abrir 1",
                "§eClick derecho §7→ Abrir todas",
                ""
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
            inv.setItem(13, item);
        } else {
            ItemStack item = new ItemStack(Material.GRAY_DYE);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§8Ruleta de Conexión §7(0)");
            meta.setLore(List.of("", "§7No tienes ruletas de conexión", ""));
            item.setItemMeta(meta);
            inv.setItem(13, item);
        }

        // ── Slot 15: Rasca y Gana ──
        if (scratch > 0) {
            ItemStack item = new ItemStack(Material.PAPER, Math.min(scratch, 64));
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§e§l🎟 Rasca y Gana §7(x" + scratch + ")");
            meta.setLore(List.of(
                "",
                "§7Tienes §e" + scratch + " §7rasca" + (scratch > 1 ? "s" : ""),
                "",
                "§aClick izquierdo §7→ Abrir 1",
                "§eClick derecho §7→ Abrir todos",
                ""
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
            inv.setItem(15, item);
        } else {
            ItemStack item = new ItemStack(Material.GRAY_DYE);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§8Rasca y Gana §7(0)");
            meta.setLore(List.of("", "§7No tienes rascas y gana", ""));
            item.setItemMeta(meta);
            inv.setItem(15, item);
        }

        // ── Slot 22: Abrir TODAS ──
        if (total > 0) {
            ItemStack allBtn = new ItemStack(Material.NETHER_STAR);
            ItemMeta meta = allBtn.getItemMeta();
            meta.setDisplayName("§d§l✦ ABRIR TODAS §7(" + total + ") §d§l✦");
            meta.setLore(List.of(
                "",
                "§7Abre todas tus recompensas",
                "§7una detrás de otra.",
                "",
                "§d§lClick para abrir todo",
                ""
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            allBtn.setItemMeta(meta);
            inv.setItem(22, allBtn);
        }

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.5f);
    }

    // ═══════════════════════════════════════════════════════════════
    //  CLICK HANDLER
    // ═══════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(MENU_TITLE)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID uid = player.getUniqueId();

        int slot = event.getRawSlot();
        boolean rightClick = event.isRightClick();

        switch (slot) {
            case 11 -> { // Ruleta Diaria
                if (pendingDailyRoulettes.getOrDefault(uid, 0) <= 0) return;
                player.closeInventory();
                menuOpen.remove(uid);
                if (rightClick) {
                    openAllOfType(player, "daily");
                } else {
                    openOneOfType(player, "daily");
                }
            }
            case 13 -> { // Ruleta de Conexión
                if (pendingPlaytimeRoulettes.getOrDefault(uid, 0) <= 0) return;
                player.closeInventory();
                menuOpen.remove(uid);
                if (rightClick) {
                    openAllOfType(player, "playtime");
                } else {
                    openOneOfType(player, "playtime");
                }
            }
            case 15 -> { // Rasca y Gana
                if (pendingScratchCards.getOrDefault(uid, 0) <= 0) return;
                player.closeInventory();
                menuOpen.remove(uid);
                if (rightClick) {
                    openAllOfType(player, "scratch");
                } else {
                    openOneOfType(player, "scratch");
                }
            }
            case 22 -> { // Abrir TODAS
                if (getTotalPending(uid) <= 0) return;
                player.closeInventory();
                menuOpen.remove(uid);
                openAllSequential(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTitle().equals(MENU_TITLE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMenuClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().equals(MENU_TITLE)) {
            menuOpen.remove(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uid = event.getPlayer().getUniqueId();
        menuOpen.remove(uid);
        openingSequence.remove(uid);
    }

    // ═══════════════════════════════════════════════════════════════
    //  OPEN ONE / ALL
    // ═══════════════════════════════════════════════════════════════

    private void openOneOfType(Player player, String type) {
        UUID uid = player.getUniqueId();
        String consumed = consumeType(uid, type);
        if (consumed == null) return;
        openReward(player, consumed, () -> {
            // After this one finishes, re-open menu if they have more
            if (getTotalPending(uid) > 0) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) openMenu(player);
                }, 20L);
            }
        }, false);
    }

    private void openAllOfType(Player player, String type) {
        UUID uid = player.getUniqueId();
        openingSequence.add(uid);
        openNextOfType(player, type);
    }

    private void openNextOfType(Player player, String type) {
        UUID uid = player.getUniqueId();
        if (!player.isOnline() || !openingSequence.contains(uid)) {
            openingSequence.remove(uid);
            return;
        }

        String consumed = consumeType(uid, type);
        if (consumed == null) {
            openingSequence.remove(uid);
            // Re-open menu if more pending of other types
            if (getTotalPending(uid) > 0) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) openMenu(player);
                }, 20L);
            }
            return;
        }

        openReward(player, consumed, () -> {
            // Chain next one after delay (fast: 5 ticks, normal: 30 ticks)
            Bukkit.getScheduler().runTaskLater(plugin, () -> openNextOfType(player, type), 5L);
        }, true);
    }

    private void openAllSequential(Player player) {
        UUID uid = player.getUniqueId();
        openingSequence.add(uid);
        openNextAny(player);
    }

    private void openNextAny(Player player) {
        UUID uid = player.getUniqueId();
        if (!player.isOnline() || !openingSequence.contains(uid)) {
            openingSequence.remove(uid);
            return;
        }

        String consumed = consumeNext(uid);
        if (consumed == null) {
            openingSequence.remove(uid);
            player.sendMessage(SmallCaps.convert("§a§l✔ §f¡Has abierto todas tus recompensas!"));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.5f);
            return;
        }

        openReward(player, consumed, () -> {
            Bukkit.getScheduler().runTaskLater(plugin, () -> openNextAny(player), 5L);
        }, true);
    }

    // ═══════════════════════════════════════════════════════════════
    //  OPEN REWARD (delegates to existing managers)
    // ═══════════════════════════════════════════════════════════════

    private void openReward(Player player, String type, Runnable onComplete, boolean fast) {
        UUID uid = player.getUniqueId();
        switch (type) {
            case "daily" -> {
                DailyRouletteManager drm = plugin.getDailyRouletteManager();
                if (drm != null) {
                    if (fast) drm.setFastMode(uid, true);
                    drm.openRouletteFromPending(player, onComplete);
                } else {
                    onComplete.run();
                }
            }
            case "playtime" -> {
                PlaytimeRouletteManager prm = plugin.getPlaytimeRouletteManager();
                if (prm != null) {
                    if (fast) prm.setFastMode(uid, true);
                    prm.openRouletteFromPending(player, onComplete);
                } else {
                    onComplete.run();
                }
            }
            case "scratch" -> {
                if (plugin.getScratchCardManager() != null) {
                    plugin.getScratchCardManager().openCardFromPending(player, onComplete, fast);
                } else {
                    onComplete.run();
                }
            }
            default -> onComplete.run();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  JOIN NOTIFICATION
    // ═══════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            int total = getTotalPending(uid);
            if (total > 0) {
                notifyPlayer(player);
            }
        }, 100L); // 5 seconds after join
    }

    public boolean isInSequence(UUID uid) {
        return openingSequence.contains(uid);
    }

    // ═══════════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════════

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
