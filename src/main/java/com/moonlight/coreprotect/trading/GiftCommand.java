package com.moonlight.coreprotect.trading;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * /gift <jugador> — Abre un inventario vacío donde puedes meter ítems.
 * Al cerrar, se le envían los ítems al jugador destino.
 * También se puede añadir dinero y esencias con botones.
 * Funciona aunque el destino esté AFK o desconectado (pending gifts).
 */
public class GiftCommand implements CommandExecutor, TabCompleter, Listener {

    private final CoreProtectPlugin plugin;
    public static final String GUI_TITLE_PREFIX = "§8§l\uD83C\uDF81 §6Regalo para: §e";
    private static final int GUI_SIZE = 54; // 6 rows
    // Slots 0-44: item slots (45 slots for items)
    // Row 6 (45-53): controls
    private static final int SLOT_MONEY = 46;
    private static final int SLOT_ESSENCES = 48;
    private static final int SLOT_CONFIRM = 50;
    private static final int SLOT_CANCEL = 52;

    private final Map<UUID, GiftSession> sessions = new ConcurrentHashMap<>();
    // Pending gifts for offline players: UUID -> list of PendingGift
    private final Map<UUID, List<PendingGift>> pendingGifts = new ConcurrentHashMap<>();
    private final File dataFile;

    // Chat input mode
    private final Map<UUID, String> chatInputMode = new ConcurrentHashMap<>(); // "money" or "essences"

    public GiftCommand(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "pending_gifts.yml");
        loadPendingGifts();
    }

    private static String sc(String s) { return SmallCaps.convert(s); }

    // ═══════════════════════════════════════════════════════════════
    //  COMMAND
    // ═══════════════════════════════════════════════════════════════

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(sc("§cSolo jugadores."));
            return true;
        }

        if (args.length < 1) {
            // Show pending gifts for this player
            showPendingGifts(player);
            return true;
        }

        // /gift reclamar — claim pending gifts
        if (args[0].equalsIgnoreCase("reclamar") || args[0].equalsIgnoreCase("claim")) {
            claimAllGifts(player);
            return true;
        }

        String targetName = args[0];
        // Target can be offline — we accept name
        Player onlineTarget = Bukkit.getPlayerExact(targetName);
        @SuppressWarnings("deprecation")
        OfflinePlayer target = onlineTarget != null ? onlineTarget : Bukkit.getOfflinePlayer(targetName);
        if (target == null || (!target.isOnline() && !target.hasPlayedBefore())) {
            player.sendMessage(sc("§c§l✖ §cJugador no encontrado: " + targetName));
            return true;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(sc("§c§l✖ §cNo puedes regalarte a ti mismo."));
            return true;
        }

        if (sessions.containsKey(player.getUniqueId())) {
            player.sendMessage(sc("§c§l✖ §cYa tienes un regalo abierto."));
            return true;
        }

        openGiftGUI(player, target);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            completions.add("reclamar");
            for (Player p : Bukkit.getOnlinePlayers()) completions.add(p.getName());
            return completions.stream()
                    .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    // ═══════════════════════════════════════════════════════════════
    //  GUI
    // ═══════════════════════════════════════════════════════════════

    private void openGiftGUI(Player sender, OfflinePlayer target) {
        String title = GUI_TITLE_PREFIX + target.getName();
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, title);

        GiftSession session = new GiftSession(target.getUniqueId(), target.getName());
        sessions.put(sender.getUniqueId(), session);

        // Bottom row: controls
        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) inv.setItem(i, glass);

        inv.setItem(SLOT_MONEY, createItem(Material.GOLD_INGOT, "§e§l+ Añadir Dinero",
                "§7Click para añadir dinero al regalo.", "§7Cantidad actual: §e$" + session.money));
        inv.setItem(SLOT_ESSENCES, createItem(Material.AMETHYST_SHARD, "§d§l+ Añadir Esencias",
                "§7Click para añadir esencias al regalo.", "§7Cantidad actual: §d" + session.essences));
        inv.setItem(SLOT_CONFIRM, createItem(Material.LIME_WOOL, "§a§l✔ ENVIAR REGALO",
                "§7Envía todo el contenido a §e" + target.getName()));
        inv.setItem(SLOT_CANCEL, createItem(Material.RED_WOOL, "§c§l✖ CANCELAR",
                "§7Cancela y devuelve tus ítems."));

        sender.openInventory(inv);
        sender.playSound(sender.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.2f);
    }

    // ═══════════════════════════════════════════════════════════════
    //  EVENTS
    // ═══════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (!title.startsWith(GUI_TITLE_PREFIX)) return;

        UUID uid = player.getUniqueId();
        GiftSession session = sessions.get(uid);
        if (session == null) return;

        int slot = event.getRawSlot();

        // Allow placing items in slots 0-44
        if (slot >= 0 && slot < 45) return; // let them place/remove items

        // Block bottom row interaction (except our buttons)
        if (slot >= 45 && slot < 54) {
            event.setCancelled(true);

            if (slot == SLOT_MONEY) {
                chatInputMode.put(uid, "money");
                player.closeInventory();
                player.sendMessage("");
                player.sendMessage(sc("§e§l\uD83D\uDCB0 §7Escribe la cantidad de §edinero §7a añadir (o §ccancelar§7):"));
                player.sendMessage("");
            } else if (slot == SLOT_ESSENCES) {
                chatInputMode.put(uid, "essences");
                player.closeInventory();
                player.sendMessage("");
                player.sendMessage(sc("§d§l✦ §7Escribe la cantidad de §desencias §7a añadir (o §ccancelar§7):"));
                player.sendMessage("");
            } else if (slot == SLOT_CONFIRM) {
                confirmGift(player, session, event.getInventory());
            } else if (slot == SLOT_CANCEL) {
                cancelGift(player, event.getInventory());
            }
        }

        // Block shifting items into bottom row
        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY && slot >= GUI_SIZE) {
            // Player inventory shift-click — allow it to go to gift area
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().startsWith(GUI_TITLE_PREFIX)) return;
        // Block drags into bottom row
        for (int slot : event.getRawSlots()) {
            if (slot >= 45 && slot < 54) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        UUID uid = player.getUniqueId();
        if (!event.getView().getTitle().startsWith(GUI_TITLE_PREFIX)) return;

        // If in chat input mode, don't cancel — they need to type
        if (chatInputMode.containsKey(uid)) return;

        GiftSession session = sessions.get(uid);
        if (session == null) return;

        // If session still exists and not confirmed, return items
        if (!session.confirmed) {
            returnItems(player, event.getInventory());
            sessions.remove(uid);
            // Refund money/essences
            if (session.money > 0 && plugin.getEconomy() != null) {
                plugin.getEconomy().depositPlayer(player, session.money);
            }
            if (session.essences > 0 && plugin.getEssenceManager() != null) {
                plugin.getEssenceManager().addEssencesRaw(uid, session.essences);
                plugin.getEssenceManager().saveData();
            }
            player.sendMessage(sc("§c§l✖ §7Regalo cancelado. Tus ítems han sido devueltos."));
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();
        String mode = chatInputMode.get(uid);
        if (mode == null) return;

        event.setCancelled(true);
        String msg = event.getMessage().trim().toLowerCase();

        if (msg.equals("cancelar") || msg.equals("cancel")) {
            chatInputMode.remove(uid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                GiftSession session = sessions.get(uid);
                if (session != null) {
                    openGiftGUIFromSession(player, session);
                }
            });
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(msg.replace(",", "").replace(".", ""));
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            player.sendMessage(sc("§c§l✖ §cCantidad inválida. Escribe un número o §ccancelar§7."));
            return;
        }

        GiftSession session = sessions.get(uid);
        if (session == null) {
            chatInputMode.remove(uid);
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (mode.equals("money")) {
                if (plugin.getEconomy() == null) {
                    player.sendMessage(sc("§c§l✖ §cEconomía no disponible."));
                    chatInputMode.remove(uid);
                    return;
                }
                double balance = plugin.getEconomy().getBalance(player);
                if (balance < amount) {
                    player.sendMessage(sc("§c§l✖ §cNo tienes suficiente dinero. Balance: §e$" + String.format("%,.0f", balance)));
                    chatInputMode.remove(uid);
                    openGiftGUIFromSession(player, session);
                    return;
                }
                plugin.getEconomy().withdrawPlayer(player, amount);
                session.money += amount;
                player.sendMessage(sc("§a§l✔ §e$" + String.format("%,d", amount) + " §7añadido al regalo."));
            } else if (mode.equals("essences")) {
                if (plugin.getEssenceManager() == null) {
                    player.sendMessage(sc("§c§l✖ §cEsencias no disponible."));
                    chatInputMode.remove(uid);
                    return;
                }
                int current = plugin.getEssenceManager().getEssences(uid);
                if (current < amount) {
                    player.sendMessage(sc("§c§l✖ §cNo tienes suficientes esencias. Tienes: §d" + current));
                    chatInputMode.remove(uid);
                    openGiftGUIFromSession(player, session);
                    return;
                }
                plugin.getEssenceManager().removeEssences(uid, amount);
                plugin.getEssenceManager().saveData();
                session.essences += amount;
                player.sendMessage(sc("§a§l✔ §d" + amount + " esencias §7añadidas al regalo."));
            }

            chatInputMode.remove(uid);
            openGiftGUIFromSession(player, session);
        });
    }

    // Re-open gift GUI with stored items
    private void openGiftGUIFromSession(Player player, GiftSession session) {
        String title = GUI_TITLE_PREFIX + session.targetName;
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, title);

        // Restore items
        for (int i = 0; i < session.storedItems.length; i++) {
            if (session.storedItems[i] != null) {
                inv.setItem(i, session.storedItems[i]);
            }
        }

        // Controls
        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) inv.setItem(i, glass);

        inv.setItem(SLOT_MONEY, createItem(Material.GOLD_INGOT, "§e§l+ Añadir Dinero",
                "§7Click para añadir dinero.", "§7Incluido: §e$" + String.format("%,d", session.money)));
        inv.setItem(SLOT_ESSENCES, createItem(Material.AMETHYST_SHARD, "§d§l+ Añadir Esencias",
                "§7Click para añadir esencias.", "§7Incluidas: §d" + session.essences));
        inv.setItem(SLOT_CONFIRM, createItem(Material.LIME_WOOL, "§a§l✔ ENVIAR REGALO",
                "§7Envía todo a §e" + session.targetName));
        inv.setItem(SLOT_CANCEL, createItem(Material.RED_WOOL, "§c§l✖ CANCELAR",
                "§7Cancela y devuelve tus ítems."));

        player.openInventory(inv);
    }

    @EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();
        List<PendingGift> gifts = pendingGifts.get(uid);
        if (gifts != null && !gifts.isEmpty()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                int count = gifts.size();
                player.sendMessage("");
                player.sendMessage(sc("§6§l\uD83C\uDF81 §e§l¡Tienes " + count + " regalo" + (count > 1 ? "s" : "") + " pendiente" + (count > 1 ? "s" : "") + "! §6§l\uD83C\uDF81"));
                player.sendMessage(sc("§7Usa §a/gift §7para verlos o §a/gift reclamar §7para recibirlos."));
                player.sendMessage("");
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }, 80L); // 4 seconds after join
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  CONFIRM / CANCEL
    // ═══════════════════════════════════════════════════════════════

    private void confirmGift(Player sender, GiftSession session, Inventory inv) {
        session.confirmed = true;

        // Collect items from slots 0-44
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                items.add(item.clone());
                inv.setItem(i, null);
            }
        }

        if (items.isEmpty() && session.money <= 0 && session.essences <= 0) {
            sender.sendMessage(sc("§c§l✖ §cEl regalo está vacío."));
            session.confirmed = false;
            return;
        }

        sessions.remove(sender.getUniqueId());
        sender.closeInventory();

        PendingGift gift = new PendingGift(sender.getName(), items, session.money, session.essences);

        Player target = Bukkit.getPlayer(session.targetUid);
        if (target != null && target.isOnline()) {
            deliverGift(target, gift);
        } else {
            // Store for when they come online
            pendingGifts.computeIfAbsent(session.targetUid, k -> new ArrayList<>()).add(gift);
            savePendingGifts();
        }

        // Confirmation to sender
        sender.playSound(sender.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
        sender.sendMessage("");
        sender.sendMessage(sc("§a§l✔ §f¡Regalo enviado a §e" + session.targetName + "§f!"));
        if (!items.isEmpty()) sender.sendMessage(sc("  §7Ítems: §f" + items.size()));
        if (session.money > 0) sender.sendMessage(sc("  §7Dinero: §e$" + String.format("%,d", session.money)));
        if (session.essences > 0) sender.sendMessage(sc("  §7Esencias: §d" + session.essences));
        if (target == null || !target.isOnline()) {
            sender.sendMessage(sc("  §7§o(Lo recibirá cuando se conecte)"));
        }
        sender.sendMessage("");
    }

    private void cancelGift(Player sender, Inventory inv) {
        GiftSession session = sessions.remove(sender.getUniqueId());
        if (session == null) return;

        returnItems(sender, inv);

        // Refund money/essences
        if (session.money > 0 && plugin.getEconomy() != null) {
            plugin.getEconomy().depositPlayer(sender, session.money);
        }
        if (session.essences > 0 && plugin.getEssenceManager() != null) {
            plugin.getEssenceManager().addEssencesRaw(sender.getUniqueId(), session.essences);
            plugin.getEssenceManager().saveData();
        }

        sender.closeInventory();
        sender.sendMessage(sc("§c§l✖ §7Regalo cancelado. Todo ha sido devuelto."));
        sender.playSound(sender.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
    }

    private void returnItems(Player player, Inventory inv) {
        for (int i = 0; i < 45; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                for (ItemStack drop : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
                inv.setItem(i, null);
            }
        }
    }

    private void deliverGift(Player target, PendingGift gift) {
        target.sendMessage("");
        target.sendMessage(sc("§6§l\uD83C\uDF81 §e§l¡HAS RECIBIDO UN REGALO! §6§l\uD83C\uDF81"));
        target.sendMessage(sc("§7De: §f" + gift.senderName));

        if (gift.money > 0 && plugin.getEconomy() != null) {
            plugin.getEconomy().depositPlayer(target, gift.money);
            target.sendMessage(sc("  §7Dinero: §e$" + String.format("%,d", gift.money)));
        }
        if (gift.essences > 0 && plugin.getEssenceManager() != null) {
            plugin.getEssenceManager().addEssencesRaw(target.getUniqueId(), gift.essences);
            plugin.getEssenceManager().saveData();
            target.sendMessage(sc("  §7Esencias: §d" + gift.essences));
        }
        if (!gift.items.isEmpty()) {
            int given = 0;
            for (ItemStack item : gift.items) {
                HashMap<Integer, ItemStack> leftover = target.getInventory().addItem(item);
                for (ItemStack drop : leftover.values()) {
                    target.getWorld().dropItemNaturally(target.getLocation(), drop);
                }
                given++;
            }
            target.sendMessage(sc("  §7Ítems: §f" + given));
        }
        target.sendMessage("");
        target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
        target.sendTitle("", sc("§6§l\uD83C\uDF81 §eRegalo de " + gift.senderName), 10, 60, 20);
    }

    // ═══════════════════════════════════════════════════════════════
    //  UTILITY
    // ═══════════════════════════════════════════════════════════════

    private ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore.length > 0) meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    // ═══════════════════════════════════════════════════════════════
    //  DATA CLASSES
    // ═══════════════════════════════════════════════════════════════

    static class GiftSession {
        final UUID targetUid;
        final String targetName;
        int money = 0;
        int essences = 0;
        boolean confirmed = false;
        ItemStack[] storedItems = new ItemStack[45];

        GiftSession(UUID targetUid, String targetName) {
            this.targetUid = targetUid;
            this.targetName = targetName;
        }
    }

    static class PendingGift {
        final String senderName;
        final List<ItemStack> items;
        final int money;
        final int essences;

        PendingGift(String senderName, List<ItemStack> items, int money, int essences) {
            this.senderName = senderName;
            this.items = items;
            this.money = money;
            this.essences = essences;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  VIEW PENDING GIFTS
    // ═══════════════════════════════════════════════════════════════

    private void showPendingGifts(Player player) {
        UUID uid = player.getUniqueId();
        List<PendingGift> gifts = pendingGifts.get(uid);

        player.sendMessage("");
        player.sendMessage(sc("§6§l\uD83C\uDF81 §e§lREGALOS §6§l\uD83C\uDF81"));
        player.sendMessage("");

        if (gifts == null || gifts.isEmpty()) {
            player.sendMessage(sc("§7No tienes regalos pendientes."));
            player.sendMessage("");
            player.sendMessage(sc("§7Para enviar un regalo: §e/gift <jugador>"));
            player.sendMessage("");
            return;
        }

        player.sendMessage(sc("§7Tienes §e" + gifts.size() + " regalo" + (gifts.size() > 1 ? "s" : "") + " §7pendiente" + (gifts.size() > 1 ? "s" : "") + ":"));
        player.sendMessage("");
        for (int i = 0; i < gifts.size(); i++) {
            PendingGift g = gifts.get(i);
            StringBuilder desc = new StringBuilder();
            desc.append("§f").append(i + 1).append(". §7De §e").append(g.senderName).append("§7: ");
            List<String> parts = new ArrayList<>();
            if (!g.items.isEmpty()) parts.add("§f" + g.items.size() + " ítems");
            if (g.money > 0) parts.add("§e$" + String.format("%,d", g.money));
            if (g.essences > 0) parts.add("§d" + g.essences + " esencias");
            desc.append(String.join("§7, ", parts));
            player.sendMessage(sc(desc.toString()));
        }
        player.sendMessage("");
        player.sendMessage(sc("§a/gift reclamar §7— reclamar todos los regalos"));
        player.sendMessage(sc("§7Para enviar: §e/gift <jugador>"));
        player.sendMessage("");
    }

    private void claimAllGifts(Player player) {
        UUID uid = player.getUniqueId();
        List<PendingGift> gifts = pendingGifts.remove(uid);
        if (gifts == null || gifts.isEmpty()) {
            player.sendMessage(sc("§c§l✖ §7No tienes regalos pendientes."));
            return;
        }
        for (PendingGift gift : gifts) {
            deliverGift(player, gift);
        }
        savePendingGifts();
        player.sendMessage(sc("§a§l✔ §f¡" + gifts.size() + " regalo" + (gifts.size() > 1 ? "s" : "") + " reclamado" + (gifts.size() > 1 ? "s" : "") + "!"));
    }

    // ═══════════════════════════════════════════════════════════════
    //  PERSISTENCE
    // ═══════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private void loadPendingGifts() {
        if (!dataFile.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        for (String uuidStr : cfg.getKeys(false)) {
            try {
                UUID uid = UUID.fromString(uuidStr);
                List<Map<?, ?>> giftList = cfg.getMapList(uuidStr);
                List<PendingGift> gifts = new ArrayList<>();
                for (Map<?, ?> map : giftList) {
                    String senderName = map.containsKey("sender") ? (String) map.get("sender") : "???";
                    int money = map.containsKey("money") ? ((Number) map.get("money")).intValue() : 0;
                    int essences = map.containsKey("essences") ? ((Number) map.get("essences")).intValue() : 0;
                    List<ItemStack> items = new ArrayList<>();
                    if (map.containsKey("items")) {
                        List<?> itemList = (List<?>) map.get("items");
                        for (Object obj : itemList) {
                            if (obj instanceof Map) {
                                try {
                                    items.add(ItemStack.deserialize((Map<String, Object>) obj));
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                    gifts.add(new PendingGift(senderName, items, money, essences));
                }
                if (!gifts.isEmpty()) pendingGifts.put(uid, gifts);
            } catch (Exception ignored) {}
        }
        plugin.getLogger().info("[Gift] Cargados " + pendingGifts.size() + " jugadores con regalos pendientes.");
    }

    public void savePendingGifts() {
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, List<PendingGift>> entry : pendingGifts.entrySet()) {
            List<Map<String, Object>> giftList = new ArrayList<>();
            for (PendingGift gift : entry.getValue()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("sender", gift.senderName);
                map.put("money", gift.money);
                map.put("essences", gift.essences);
                List<Map<String, Object>> items = new ArrayList<>();
                for (ItemStack item : gift.items) {
                    items.add(item.serialize());
                }
                map.put("items", items);
                giftList.add(map);
            }
            cfg.set(entry.getKey().toString(), giftList);
        }
        try {
            cfg.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("[Gift] Error guardando pending_gifts.yml: " + e.getMessage());
        }
    }

    public void shutdown() {
        savePendingGifts();
    }
}
