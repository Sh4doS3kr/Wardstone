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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * /trade <jugador> — Sistema de intercambio en tiempo real.
 * GUI dividida: lado izquierdo = tú, lado derecho = el otro.
 * Soporta dinero y esencias.
 * 10 segundos de confirmación antes de ejecutar el trade.
 */
public class TradeCommand implements CommandExecutor, TabCompleter, Listener {

    private final CoreProtectPlugin plugin;

    // Pending trade requests: target UUID -> requester UUID
    private final Map<UUID, UUID> pendingRequests = new ConcurrentHashMap<>();
    private final Map<UUID, Long> requestTimestamps = new ConcurrentHashMap<>();

    // Active trade sessions: both players map to the same TradeSession
    private final Map<UUID, TradeSession> activeTrades = new ConcurrentHashMap<>();

    // Chat input mode for money/essences
    private final Map<UUID, String> chatMode = new ConcurrentHashMap<>(); // "money" or "essences"

    public static final String TITLE_PREFIX = "§8§l⇄ §6§lTrade: §e";
    private static final long REQUEST_TIMEOUT = 30_000L; // 30s to accept
    private static final int CONFIRM_SECONDS = 10;

    public TradeCommand(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        // Cleanup expired requests every 10s
        Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupExpired, 200L, 200L);
    }

    private static String sc(String s) { return SmallCaps.convert(s); }

    // ═══════════════════════════════════════════════════════════════
    //  COMMAND
    // ═══════════════════════════════════════════════════════════════

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSolo jugadores.");
            return true;
        }
        UUID uid = player.getUniqueId();

        if (args.length < 1) {
            player.sendMessage(sc("§c§l✖ §cUso: /trade <jugador>"));
            player.sendMessage(sc("§7Envía una solicitud de intercambio."));
            return true;
        }

        // Accept pending trade
        if (args[0].equalsIgnoreCase("aceptar") || args[0].equalsIgnoreCase("accept")) {
            UUID requester = pendingRequests.get(uid);
            if (requester == null) {
                player.sendMessage(sc("§c§l✖ §cNo tienes solicitudes de trade pendientes."));
                return true;
            }
            Player req = Bukkit.getPlayer(requester);
            if (req == null || !req.isOnline()) {
                player.sendMessage(sc("§c§l✖ §cEl jugador ya no está conectado."));
                pendingRequests.remove(uid);
                return true;
            }
            pendingRequests.remove(uid);
            requestTimestamps.remove(uid);
            startTrade(req, player);
            return true;
        }

        // Deny
        if (args[0].equalsIgnoreCase("rechazar") || args[0].equalsIgnoreCase("deny")) {
            UUID requester = pendingRequests.remove(uid);
            requestTimestamps.remove(uid);
            if (requester != null) {
                Player req = Bukkit.getPlayer(requester);
                if (req != null) req.sendMessage(sc("§c§l✖ §e" + player.getName() + " §cha rechazado tu solicitud de trade."));
            }
            player.sendMessage(sc("§c§l✖ §7Solicitud de trade rechazada."));
            return true;
        }

        // Send request
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(sc("§c§l✖ §cJugador no encontrado o no conectado."));
            return true;
        }
        if (target.getUniqueId().equals(uid)) {
            player.sendMessage(sc("§c§l✖ §cNo puedes hacer trade contigo mismo."));
            return true;
        }
        if (activeTrades.containsKey(uid)) {
            player.sendMessage(sc("§c§l✖ §cYa estás en un trade."));
            return true;
        }
        if (activeTrades.containsKey(target.getUniqueId())) {
            player.sendMessage(sc("§c§l✖ §c" + target.getName() + " ya está en un trade."));
            return true;
        }

        // Check if target already has pending from this player
        if (pendingRequests.containsKey(target.getUniqueId()) &&
                pendingRequests.get(target.getUniqueId()).equals(uid)) {
            player.sendMessage(sc("§c§l✖ §cYa has enviado una solicitud a " + target.getName() + "."));
            return true;
        }

        pendingRequests.put(target.getUniqueId(), uid);
        requestTimestamps.put(target.getUniqueId(), System.currentTimeMillis());

        player.sendMessage(sc("§a§l✔ §7Solicitud de trade enviada a §e" + target.getName() + "§7. Tiene 30s."));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f);

        target.sendMessage("");
        target.sendMessage(sc("§6§l⇄ §e§l¡SOLICITUD DE TRADE! §6§l⇄"));
        target.sendMessage(sc("§f" + player.getName() + " §7quiere hacer trade contigo."));
        target.sendMessage(sc("§a/trade aceptar §7— para aceptar"));
        target.sendMessage(sc("§c/trade rechazar §7— para rechazar"));
        target.sendMessage(sc("§7Tienes §e30 segundos§7."));
        target.sendMessage("");
        target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            List<String> completions = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
            completions.add("aceptar");
            completions.add("rechazar");
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    // ═══════════════════════════════════════════════════════════════
    //  START TRADE — open GUI for both
    // ═══════════════════════════════════════════════════════════════

    private void startTrade(Player p1, Player p2) {
        TradeSession session = new TradeSession(p1.getUniqueId(), p2.getUniqueId(),
                p1.getName(), p2.getName());
        activeTrades.put(p1.getUniqueId(), session);
        activeTrades.put(p2.getUniqueId(), session);

        openTradeGUI(p1, session);
        openTradeGUI(p2, session);

        p1.sendMessage(sc("§a§l✔ §7Trade iniciado con §e" + p2.getName()));
        p2.sendMessage(sc("§a§l✔ §7Trade iniciado con §e" + p1.getName()));
    }

    /*
     * Layout (54 slots, 6 rows):
     * Row 0:    [glass] [glass] [glass] [glass] [divider] [glass] [glass] [glass] [glass]
     * Row 1-3:  [P1 items: 0-11]   [divider]   [P2 items: shown read-only]
     * Row 4:    [money] [ess] [glass] [glass] [divider] [glass] [glass] [status] [status]
     * Row 5:    [glass] [confirm] [glass] [glass] [divider] [glass] [glass] [confirm] [glass]
     *
     * Simplified layout:
     * Left (cols 0-3): Player's own items (12 slots across 3 rows)
     * Col 4: Divider
     * Right (cols 5-8): Other player's items (read-only)
     * Bottom controls
     */
    private static final int[] P1_SLOTS = {9, 10, 11, 12, 18, 19, 20, 21, 27, 28, 29, 30};
    private static final int[] P2_DISPLAY = {14, 15, 16, 17, 23, 24, 25, 26, 32, 33, 34, 35};
    private static final int DIVIDER_COL = 4;
    private static final int SLOT_MY_MONEY = 36;
    private static final int SLOT_MY_ESSENCES = 37;
    private static final int SLOT_MY_CONFIRM = 46;
    private static final int SLOT_OTHER_MONEY = 44;
    private static final int SLOT_OTHER_ESSENCES = 43;
    private static final int SLOT_OTHER_CONFIRM = 52;

    private void openTradeGUI(Player player, TradeSession session) {
        boolean isP1 = player.getUniqueId().equals(session.p1);
        String otherName = isP1 ? session.p2Name : session.p1Name;
        String title = TITLE_PREFIX + otherName;
        Inventory inv = Bukkit.createInventory(null, 54, title);

        // Fill with glass
        ItemStack glass = ci(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, glass);

        // Divider column (col 4)
        ItemStack divider = ci(Material.BLACK_STAINED_GLASS_PANE, "§8│");
        for (int row = 0; row < 6; row++) {
            inv.setItem(row * 9 + DIVIDER_COL, divider);
        }

        // Headers
        inv.setItem(1, ci(Material.PLAYER_HEAD, "§a§lTus ítems"));
        inv.setItem(7, ci(Material.PLAYER_HEAD, "§e§l" + otherName));

        // Open P1 item slots (empty)
        for (int slot : P1_SLOTS) {
            inv.setItem(slot, null); // empty = player can place items
        }

        // P2 display slots: show as glass (no items yet)
        ItemStack locked = ci(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§7(vacío)");
        for (int slot : P2_DISPLAY) {
            inv.setItem(slot, locked);
        }

        // Controls
        inv.setItem(SLOT_MY_MONEY, ci(Material.GOLD_INGOT, "§e§l+ Dinero", "§7Click para ofrecer dinero", "§7Ofrecido: §e$0"));
        inv.setItem(SLOT_MY_ESSENCES, ci(Material.AMETHYST_SHARD, "§d§l+ Esencias", "§7Click para ofrecer esencias", "§7Ofrecidas: §d0"));
        inv.setItem(SLOT_MY_CONFIRM, ci(Material.RED_WOOL, "§c§lNO LISTO", "§7Click cuando estés listo"));
        inv.setItem(SLOT_OTHER_MONEY, ci(Material.GOLD_NUGGET, "§7Dinero del otro: §e$0"));
        inv.setItem(SLOT_OTHER_ESSENCES, ci(Material.AMETHYST_SHARD, "§7Esencias del otro: §d0"));
        inv.setItem(SLOT_OTHER_CONFIRM, ci(Material.RED_WOOL, "§c§lNO LISTO", "§7Esperando al otro..."));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.0f);
    }

    // ═══════════════════════════════════════════════════════════════
    //  EVENTS
    // ═══════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (!title.startsWith(TITLE_PREFIX)) return;

        UUID uid = player.getUniqueId();
        TradeSession session = activeTrades.get(uid);
        if (session == null) return;

        int slot = event.getRawSlot();
        boolean isP1 = uid.equals(session.p1);

        // During countdown — only allow clicking confirm button to cancel
        if (session.countingDown) {
            event.setCancelled(true);
            if (slot == SLOT_MY_CONFIRM) {
                // Un-ready this player to cancel the countdown
                if (isP1) session.p1Ready = false;
                else session.p2Ready = false;
                player.sendMessage(sc("§c§l✖ §7Has cancelado la confirmación."));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            }
            return;
        }

        // Allow interaction with own item slots
        boolean isMySlot = false;
        for (int s : P1_SLOTS) {
            if (slot == s) { isMySlot = true; break; }
        }

        if (isMySlot) {
            // Reset confirmations when items change
            session.p1Ready = false;
            session.p2Ready = false;
            // Update will happen on next tick
            Bukkit.getScheduler().runTaskLater(plugin, () -> syncGUIs(session), 1L);
            return;
        }

        // Block all other top-inventory clicks
        if (slot >= 0 && slot < 54) {
            event.setCancelled(true);

            // Handle button clicks
            if (slot == SLOT_MY_MONEY) {
                chatMode.put(uid, "money");
                player.closeInventory();
                player.sendMessage(sc("§e§l\uD83D\uDCB0 §7Escribe la cantidad de §edinero §7a ofrecer (o §ccancelar§7):"));
            } else if (slot == SLOT_MY_ESSENCES) {
                chatMode.put(uid, "essences");
                player.closeInventory();
                player.sendMessage(sc("§d§l✦ §7Escribe la cantidad de §desencias §7a ofrecer (o §ccancelar§7):"));
            } else if (slot == SLOT_MY_CONFIRM) {
                toggleReady(player, session, isP1);
            }
        }

        // Player inventory shift-click
        if (slot >= 54 && event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            // Allow shift-clicking into trade slots
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!event.getView().getTitle().startsWith(TITLE_PREFIX)) return;
        TradeSession session = activeTrades.get(event.getWhoClicked().getUniqueId());
        if (session != null && session.countingDown) {
            event.setCancelled(true);
            return;
        }
        for (int slot : event.getRawSlots()) {
            boolean allowed = false;
            for (int s : P1_SLOTS) {
                if (slot == s) { allowed = true; break; }
            }
            if (!allowed && slot < 54) {
                event.setCancelled(true);
                return;
            }
        }
        // Reset confirmations on drag
        if (session != null) {
            session.p1Ready = false;
            session.p2Ready = false;
            Bukkit.getScheduler().runTaskLater(plugin, () -> syncGUIs(session), 1L);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        UUID uid = player.getUniqueId();
        if (!event.getView().getTitle().startsWith(TITLE_PREFIX)) return;

        // If in chat mode, don't cancel
        if (chatMode.containsKey(uid)) return;

        TradeSession session = activeTrades.get(uid);
        if (session == null) return;
        // Si cierra el inventario durante countdown, cancelar el trade
        if (session.countingDown) {
            cancelTrade(session, player.getName() + " cerró el trade.");
            return;
        }
        if (session.completed) return;

        // Cancel trade
        cancelTrade(session, player.getName() + " cerró el trade.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uid = event.getPlayer().getUniqueId();
        pendingRequests.remove(uid);
        requestTimestamps.remove(uid);
        chatMode.remove(uid);

        TradeSession session = activeTrades.get(uid);
        if (session != null && !session.completed) {
            cancelTrade(session, event.getPlayer().getName() + " se desconectó.");
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();
        String mode = chatMode.get(uid);
        if (mode == null) return;

        event.setCancelled(true);
        String msg = event.getMessage().trim().toLowerCase();

        if (msg.equals("cancelar") || msg.equals("cancel")) {
            chatMode.remove(uid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                TradeSession session = activeTrades.get(uid);
                if (session != null) reopenTradeGUI(player, session);
            });
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(msg.replace(",", "").replace(".", ""));
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            player.sendMessage(sc("§c§l✖ §cCantidad inválida."));
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            TradeSession session = activeTrades.get(uid);
            if (session == null) { chatMode.remove(uid); return; }
            boolean isP1 = uid.equals(session.p1);

            if (mode.equals("money")) {
                if (plugin.getEconomy() == null) {
                    player.sendMessage(sc("§c§l✖ §cEconomía no disponible."));
                    chatMode.remove(uid);
                    return;
                }
                // Refund previous offer
                int prev = isP1 ? session.p1Money : session.p2Money;
                if (prev > 0) plugin.getEconomy().depositPlayer(player, prev);

                double balance = plugin.getEconomy().getBalance(player);
                if (balance < amount) {
                    player.sendMessage(sc("§c§l✖ §cNo tienes suficiente. Balance: §e$" + String.format("%,.0f", balance)));
                    // Re-withdraw previous
                    if (prev > 0) plugin.getEconomy().withdrawPlayer(player, prev);
                    chatMode.remove(uid);
                    reopenTradeGUI(player, session);
                    return;
                }
                plugin.getEconomy().withdrawPlayer(player, amount);
                if (isP1) session.p1Money = amount; else session.p2Money = amount;
                player.sendMessage(sc("§a§l✔ §7Ofreces §e$" + String.format("%,d", amount) + " §7en el trade."));
            } else {
                if (plugin.getEssenceManager() == null) {
                    player.sendMessage(sc("§c§l✖ §cEsencias no disponible."));
                    chatMode.remove(uid);
                    return;
                }
                int prev = isP1 ? session.p1Essences : session.p2Essences;
                if (prev > 0) {
                    plugin.getEssenceManager().addEssencesRaw(uid, prev);
                }
                int current = plugin.getEssenceManager().getEssences(uid);
                if (current < amount) {
                    player.sendMessage(sc("§c§l✖ §cNo tienes suficientes. Tienes: §d" + current));
                    if (prev > 0) plugin.getEssenceManager().removeEssences(uid, prev);
                    chatMode.remove(uid);
                    reopenTradeGUI(player, session);
                    return;
                }
                plugin.getEssenceManager().removeEssences(uid, amount);
                plugin.getEssenceManager().saveData();
                if (isP1) session.p1Essences = amount; else session.p2Essences = amount;
                player.sendMessage(sc("§a§l✔ §7Ofreces §d" + amount + " esencias §7en el trade."));
            }

            // Reset confirmations
            session.p1Ready = false;
            session.p2Ready = false;

            chatMode.remove(uid);
            reopenTradeGUI(player, session);
            // Sync other player's GUI too
            syncGUIs(session);
        });
    }

    // ═══════════════════════════════════════════════════════════════
    //  TOGGLE READY / COUNTDOWN
    // ═══════════════════════════════════════════════════════════════

    private void toggleReady(Player player, TradeSession session, boolean isP1) {
        if (isP1) session.p1Ready = !session.p1Ready;
        else session.p2Ready = !session.p2Ready;

        syncGUIs(session);

        if (session.p1Ready && session.p2Ready) {
            startCountdown(session);
        }
    }

    private void startCountdown(TradeSession session) {
        session.countingDown = true;
        session.countdown = CONFIRM_SECONDS;

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!session.countingDown || session.completed) {
                    cancel();
                    return;
                }

                Player p1 = Bukkit.getPlayer(session.p1);
                Player p2 = Bukkit.getPlayer(session.p2);

                if (p1 == null || !p1.isOnline() || p2 == null || !p2.isOnline()) {
                    cancelTrade(session, "Un jugador se desconectó.");
                    cancel();
                    return;
                }

                if (!session.p1Ready || !session.p2Ready) {
                    session.countingDown = false;
                    syncGUIs(session);
                    p1.sendMessage(sc("§c§l✖ §7Confirmación cancelada."));
                    p2.sendMessage(sc("§c§l✖ §7Confirmación cancelada."));
                    cancel();
                    return;
                }

                if (session.countdown <= 0) {
                    executeTrade(session);
                    cancel();
                    return;
                }

                String msg = sc("§e§l⇄ §7Trade en §e" + session.countdown + "s§7...");
                p1.sendMessage(msg);
                p2.sendMessage(msg);
                p1.playSound(p1.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.0f);
                p2.playSound(p2.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.0f);

                // Update confirm buttons with countdown
                syncGUIs(session);
                session.countdown--;
            }
        }.runTaskTimer(plugin, 0L, 20L); // every second
    }

    // ═══════════════════════════════════════════════════════════════
    //  EXECUTE TRADE
    // ═══════════════════════════════════════════════════════════════

    private void executeTrade(TradeSession session) {
        session.completed = true;
        session.countingDown = false;

        Player p1 = Bukkit.getPlayer(session.p1);
        Player p2 = Bukkit.getPlayer(session.p2);

        if (p1 == null || p2 == null) {
            cancelTrade(session, "Un jugador se desconectó.");
            return;
        }

        // Collect items from P1's trade slots
        List<ItemStack> p1Items = collectItems(p1, session);
        List<ItemStack> p2Items = collectItems(p2, session);

        // Close inventories
        p1.closeInventory();
        p2.closeInventory();

        // Give P1's items to P2 and vice versa
        giveItems(p2, p1Items);
        giveItems(p1, p2Items);

        // Transfer money
        if (session.p1Money > 0 && plugin.getEconomy() != null) {
            // Already withdrawn from P1, deposit to P2
            plugin.getEconomy().depositPlayer(p2, session.p1Money);
        }
        if (session.p2Money > 0 && plugin.getEconomy() != null) {
            plugin.getEconomy().depositPlayer(p1, session.p2Money);
        }

        // Transfer essences
        if (session.p1Essences > 0 && plugin.getEssenceManager() != null) {
            plugin.getEssenceManager().addEssencesRaw(session.p2, session.p1Essences);
        }
        if (session.p2Essences > 0 && plugin.getEssenceManager() != null) {
            plugin.getEssenceManager().addEssencesRaw(session.p1, session.p2Essences);
        }
        if (plugin.getEssenceManager() != null) plugin.getEssenceManager().saveData();

        // Cleanup
        activeTrades.remove(session.p1);
        activeTrades.remove(session.p2);

        // Success messages
        p1.playSound(p1.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        p2.playSound(p2.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        p1.sendMessage(sc("§a§l✔ §f¡Trade completado con §e" + session.p2Name + "§f!"));
        p2.sendMessage(sc("§a§l✔ §f¡Trade completado con §e" + session.p1Name + "§f!"));
    }

    private List<ItemStack> collectItems(Player player, TradeSession session) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        List<ItemStack> items = new ArrayList<>();
        for (int slot : P1_SLOTS) {
            ItemStack item = inv.getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                items.add(item.clone());
            }
        }
        return items;
    }

    private void giveItems(Player player, List<ItemStack> items) {
        boolean dropped = false;
        for (ItemStack item : items) {
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
                dropped = true;
            }
        }
        if (dropped) {
            player.sendMessage(sc("§e§l⚠ §cTu inventario estaba lleno. Algunos ítems se han tirado al suelo."));
            player.sendMessage(sc("§7¡Recógelos antes de que desaparezcan!"));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.5f);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  CANCEL TRADE
    // ═══════════════════════════════════════════════════════════════

    private void cancelTrade(TradeSession session, String reason) {
        if (session.completed) return;
        session.completed = true;
        session.countingDown = false;

        // Return items and money/essences
        Player p1 = Bukkit.getPlayer(session.p1);
        Player p2 = Bukkit.getPlayer(session.p2);

        if (p1 != null && p1.isOnline()) {
            returnTradeItems(p1);
            if (session.p1Money > 0 && plugin.getEconomy() != null)
                plugin.getEconomy().depositPlayer(p1, session.p1Money);
            if (session.p1Essences > 0 && plugin.getEssenceManager() != null) {
                plugin.getEssenceManager().addEssencesRaw(session.p1, session.p1Essences);
            }
            p1.sendMessage(sc("§c§l✖ §7Trade cancelado: " + reason));
            p1.playSound(p1.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            p1.closeInventory();
        }
        if (p2 != null && p2.isOnline()) {
            returnTradeItems(p2);
            if (session.p2Money > 0 && plugin.getEconomy() != null)
                plugin.getEconomy().depositPlayer(p2, session.p2Money);
            if (session.p2Essences > 0 && plugin.getEssenceManager() != null) {
                plugin.getEssenceManager().addEssencesRaw(session.p2, session.p2Essences);
            }
            p2.sendMessage(sc("§c§l✖ §7Trade cancelado: " + reason));
            p2.playSound(p2.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            p2.closeInventory();
        }

        if (plugin.getEssenceManager() != null) plugin.getEssenceManager().saveData();

        activeTrades.remove(session.p1);
        activeTrades.remove(session.p2);
    }

    private void returnTradeItems(Player player) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        if (inv == null) return;
        for (int slot : P1_SLOTS) {
            ItemStack item = inv.getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                for (ItemStack drop : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
                inv.setItem(slot, null);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  SYNC GUIS — update both players' views
    // ═══════════════════════════════════════════════════════════════

    private void syncGUIs(TradeSession session) {
        Player p1 = Bukkit.getPlayer(session.p1);
        Player p2 = Bukkit.getPlayer(session.p2);
        if (p1 == null || p2 == null) return;

        Inventory inv1 = p1.getOpenInventory().getTopInventory();
        Inventory inv2 = p2.getOpenInventory().getTopInventory();

        if (inv1 == null || inv2 == null) return;

        // Show P1's items on P2's right side and vice versa
        for (int i = 0; i < P1_SLOTS.length; i++) {
            ItemStack p1Item = inv1.getItem(P1_SLOTS[i]);
            ItemStack p2Item = inv2.getItem(P1_SLOTS[i]);

            // P2 sees P1's items on the right
            inv2.setItem(P2_DISPLAY[i], p1Item != null && p1Item.getType() != Material.AIR
                    ? p1Item.clone() : ci(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§7(vacío)"));
            // P1 sees P2's items on the right
            inv1.setItem(P2_DISPLAY[i], p2Item != null && p2Item.getType() != Material.AIR
                    ? p2Item.clone() : ci(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§7(vacío)"));
        }

        // Update money/essences display
        inv1.setItem(SLOT_MY_MONEY, ci(Material.GOLD_INGOT, "§e§l+ Dinero",
                "§7Click para ofrecer dinero", "§7Ofrecido: §e$" + String.format("%,d", session.p1Money)));
        inv1.setItem(SLOT_MY_ESSENCES, ci(Material.AMETHYST_SHARD, "§d§l+ Esencias",
                "§7Click para ofrecer esencias", "§7Ofrecidas: §d" + session.p1Essences));
        inv1.setItem(SLOT_OTHER_MONEY, ci(Material.GOLD_NUGGET,
                "§7Dinero del otro: §e$" + String.format("%,d", session.p2Money)));
        inv1.setItem(SLOT_OTHER_ESSENCES, ci(Material.AMETHYST_SHARD,
                "§7Esencias del otro: §d" + session.p2Essences));

        inv2.setItem(SLOT_MY_MONEY, ci(Material.GOLD_INGOT, "§e§l+ Dinero",
                "§7Click para ofrecer dinero", "§7Ofrecido: §e$" + String.format("%,d", session.p2Money)));
        inv2.setItem(SLOT_MY_ESSENCES, ci(Material.AMETHYST_SHARD, "§d§l+ Esencias",
                "§7Click para ofrecer esencias", "§7Ofrecidas: §d" + session.p2Essences));
        inv2.setItem(SLOT_OTHER_MONEY, ci(Material.GOLD_NUGGET,
                "§7Dinero del otro: §e$" + String.format("%,d", session.p1Money)));
        inv2.setItem(SLOT_OTHER_ESSENCES, ci(Material.AMETHYST_SHARD,
                "§7Esencias del otro: §d" + session.p1Essences));

        // Confirm buttons
        String countdownStr = session.countingDown ? " §e(" + session.countdown + "s)" : "";
        inv1.setItem(SLOT_MY_CONFIRM, session.p1Ready
                ? ci(Material.LIME_WOOL, "§a§l✔ LISTO" + countdownStr, "§7Click para cancelar")
                : ci(Material.RED_WOOL, "§c§lNO LISTO", "§7Click cuando estés listo"));
        inv1.setItem(SLOT_OTHER_CONFIRM, session.p2Ready
                ? ci(Material.LIME_WOOL, "§a§l✔ LISTO" + countdownStr)
                : ci(Material.RED_WOOL, "§c§lNO LISTO", "§7Esperando..."));

        inv2.setItem(SLOT_MY_CONFIRM, session.p2Ready
                ? ci(Material.LIME_WOOL, "§a§l✔ LISTO" + countdownStr, "§7Click para cancelar")
                : ci(Material.RED_WOOL, "§c§lNO LISTO", "§7Click cuando estés listo"));
        inv2.setItem(SLOT_OTHER_CONFIRM, session.p1Ready
                ? ci(Material.LIME_WOOL, "§a§l✔ LISTO" + countdownStr)
                : ci(Material.RED_WOOL, "§c§lNO LISTO", "§7Esperando..."));
    }

    private void reopenTradeGUI(Player player, TradeSession session) {
        boolean isP1 = player.getUniqueId().equals(session.p1);
        String otherName = isP1 ? session.p2Name : session.p1Name;
        String title = TITLE_PREFIX + otherName;
        Inventory inv = Bukkit.createInventory(null, 54, title);

        ItemStack glass = ci(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, glass);

        ItemStack divider = ci(Material.BLACK_STAINED_GLASS_PANE, "§8│");
        for (int row = 0; row < 6; row++) inv.setItem(row * 9 + DIVIDER_COL, divider);

        inv.setItem(1, ci(Material.PLAYER_HEAD, "§a§lTus ítems"));
        inv.setItem(7, ci(Material.PLAYER_HEAD, "§e§l" + otherName));

        // Restore stored items
        ItemStack[] stored = isP1 ? session.p1StoredItems : session.p2StoredItems;
        for (int i = 0; i < P1_SLOTS.length; i++) {
            inv.setItem(P1_SLOTS[i], stored[i]);
        }

        ItemStack locked = ci(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§7(vacío)");
        for (int slot : P2_DISPLAY) inv.setItem(slot, locked);

        inv.setItem(SLOT_MY_MONEY, ci(Material.GOLD_INGOT, "§e§l+ Dinero", "§7Ofrecido: §e$" +
                String.format("%,d", isP1 ? session.p1Money : session.p2Money)));
        inv.setItem(SLOT_MY_ESSENCES, ci(Material.AMETHYST_SHARD, "§d§l+ Esencias", "§7Ofrecidas: §d" +
                (isP1 ? session.p1Essences : session.p2Essences)));
        inv.setItem(SLOT_MY_CONFIRM, ci(Material.RED_WOOL, "§c§lNO LISTO"));
        inv.setItem(SLOT_OTHER_MONEY, ci(Material.GOLD_NUGGET, "§7Dinero del otro: §e$0"));
        inv.setItem(SLOT_OTHER_ESSENCES, ci(Material.AMETHYST_SHARD, "§7Esencias del otro: §d0"));
        inv.setItem(SLOT_OTHER_CONFIRM, ci(Material.RED_WOOL, "§c§lNO LISTO"));

        player.openInventory(inv);
        Bukkit.getScheduler().runTaskLater(plugin, () -> syncGUIs(session), 2L);
    }

    // ═══════════════════════════════════════════════════════════════
    //  UTILITY
    // ═══════════════════════════════════════════════════════════════

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = requestTimestamps.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            if (now - entry.getValue() > REQUEST_TIMEOUT) {
                UUID target = entry.getKey();
                UUID requester = pendingRequests.remove(target);
                it.remove();
                Player t = Bukkit.getPlayer(target);
                if (t != null) t.sendMessage(sc("§7Solicitud de trade expirada."));
                if (requester != null) {
                    Player r = Bukkit.getPlayer(requester);
                    if (r != null) r.sendMessage(sc("§7Tu solicitud de trade ha expirado."));
                }
            }
        }
    }

    private ItemStack ci(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore.length > 0) meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    // ═══════════════════════════════════════════════════════════════
    //  DATA CLASS
    // ═══════════════════════════════════════════════════════════════

    static class TradeSession {
        final UUID p1, p2;
        final String p1Name, p2Name;
        int p1Money = 0, p2Money = 0;
        int p1Essences = 0, p2Essences = 0;
        boolean p1Ready = false, p2Ready = false;
        boolean countingDown = false;
        boolean completed = false;
        int countdown = CONFIRM_SECONDS;
        ItemStack[] p1StoredItems = new ItemStack[12];
        ItemStack[] p2StoredItems = new ItemStack[12];

        TradeSession(UUID p1, UUID p2, String p1Name, String p2Name) {
            this.p1 = p1;
            this.p2 = p2;
            this.p1Name = p1Name;
            this.p2Name = p2Name;
        }
    }
}
