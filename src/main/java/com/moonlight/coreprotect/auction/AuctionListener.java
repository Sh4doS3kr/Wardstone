package com.moonlight.coreprotect.auction;

import com.moonlight.coreprotect.CoreProtectPlugin;
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
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Listener para todas las interacciones de la Auction House.
 */
public class AuctionListener implements Listener {

    private final CoreProtectPlugin plugin;
    private final AuctionManager manager;

    // Estado por jugador: categoría, orden, búsqueda, página
    private final Map<UUID, AuctionManager.Category> playerCategory = new ConcurrentHashMap<>();
    private final Map<UUID, AuctionManager.SortType> playerSort = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerSearch = new ConcurrentHashMap<>();

    // Precio seleccionado en GUI de venta
    private final Map<UUID, Double> selectedPrice = new ConcurrentHashMap<>();

    // Jugadores esperando input de chat (precio custom o búsqueda)
    private final Map<UUID, ChatInputType> awaitingChatInput = new ConcurrentHashMap<>();

    // Item pendiente de confirmar compra
    private final Map<UUID, UUID> pendingPurchase = new ConcurrentHashMap<>();

    private enum ChatInputType {
        CUSTOM_PRICE,
        SEARCH
    }

    public AuctionListener(CoreProtectPlugin plugin, AuctionManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    // ==================== INVENTORY CLICK ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();

        if (AuctionGUI.isAuctionGUI(title)) {
            handleMainGUIClick(event, player, title);
            return;
        }
        if (AuctionSellGUI.isSellGUI(title)) {
            handleSellGUIClick(event, player);
            return;
        }
        if (AuctionConfirmGUI.isConfirmGUI(title)) {
            handleConfirmGUIClick(event, player);
            return;
        }
        if (AuctionMyListingsGUI.isMyListingsGUI(title)) {
            handleMyListingsGUIClick(event, player, title);
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        String title = event.getView().getTitle();
        // Bloquear drag en todas las GUIs de AH excepto el slot de item en sell
        if (AuctionGUI.isAuctionGUI(title) || AuctionConfirmGUI.isConfirmGUI(title) ||
            AuctionMyListingsGUI.isMyListingsGUI(title)) {
            event.setCancelled(true);
        }
        if (AuctionSellGUI.isSellGUI(title)) {
            // Permitir drag solo en el slot de item
            for (int slot : event.getRawSlots()) {
                if (slot != AuctionSellGUI.ITEM_SLOT) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    // ==================== MAIN GUI ====================

    private void handleMainGUIClick(InventoryClickEvent event, Player player, String title) {
        int slot = event.getRawSlot();

        // Permitir clicks en inventario del jugador
        if (slot >= 54) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (isGlassPane(clicked.getType())) return;

        int page = AuctionGUI.getPageFromTitle(title);
        UUID uid = player.getUniqueId();
        AuctionManager.Category category = playerCategory.getOrDefault(uid, AuctionManager.Category.TODOS);
        AuctionManager.SortType sort = playerSort.getOrDefault(uid, AuctionManager.SortType.RECIENTE);
        String search = playerSearch.get(uid);

        // Página anterior
        if (slot == 45) {
            playClick(player);
            openMainGUI(player, page - 1);
            return;
        }

        // Página siguiente
        if (slot == 53) {
            playClick(player);
            openMainGUI(player, page + 1);
            return;
        }

        // Categoría
        if (slot == 47) {
            playClick(player);
            AuctionManager.Category[] cats = AuctionManager.Category.values();
            int idx = (category.ordinal() + 1) % cats.length;
            playerCategory.put(uid, cats[idx]);
            openMainGUI(player, 1);
            return;
        }

        // Ordenar
        if (slot == 48) {
            playClick(player);
            AuctionManager.SortType[] sorts = AuctionManager.SortType.values();
            int idx = (sort.ordinal() + 1) % sorts.length;
            playerSort.put(uid, sorts[idx]);
            openMainGUI(player, page);
            return;
        }

        // Vender
        if (slot == 49) {
            playClick(player);
            selectedPrice.remove(uid);
            AuctionSellGUI.open(player);
            return;
        }

        // Mis subastas
        if (slot == 51) {
            playClick(player);
            AuctionMyListingsGUI.open(player, manager, AuctionMyListingsGUI.Tab.ACTIVOS, 1);
            return;
        }

        // Buscar
        if (slot == 52) {
            if (search != null && !search.isEmpty()) {
                // Limpiar búsqueda
                playerSearch.remove(uid);
                playClick(player);
                openMainGUI(player, 1);
            } else {
                // Iniciar búsqueda
                player.closeInventory();
                awaitingChatInput.put(uid, ChatInputType.SEARCH);
                player.sendMessage("");
                player.sendMessage(SmallCaps.convert("§6§l✦ §e§lAuction House - Búsqueda"));
                player.sendMessage(SmallCaps.convert("§7Escribe en el chat lo que quieres buscar."));
                player.sendMessage(SmallCaps.convert("§7Escribe §c\"cancelar\" §7para volver."));
                player.sendMessage("");
            }
            return;
        }

        // Info item (slot 4)
        if (slot == 4) return;

        // Click en un item del AH
        AuctionItem auction = AuctionGUI.getItemAtSlot(slot, page, manager, category, sort, search);
        if (auction != null) {
            if (auction.getSeller().equals(uid)) {
                // Es su propio item -> cancelar
                playClick(player);
                AuctionManager.AuctionResult result = manager.cancelListing(player, auction.getId());
                player.sendMessage(result.getMessage());
                if (result.isSuccess()) {
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
                }
                openMainGUI(player, page);
            } else {
                // Comprar -> abrir confirmación
                playClick(player);
                pendingPurchase.put(uid, auction.getId());
                AuctionConfirmGUI.open(player, auction);
            }
        }
    }

    // ==================== SELL GUI ====================

    private void handleSellGUIClick(InventoryClickEvent event, Player player) {
        int slot = event.getRawSlot();
        UUID uid = player.getUniqueId();

        // Permitir poner/quitar item en el slot central y en el inventario del jugador
        if (slot == AuctionSellGUI.ITEM_SLOT) {
            // Permitir interacción con el slot del item
            return;
        }

        // Permitir clicks en inventario del jugador (shift-click para poner item)
        if (slot >= 54) {
            // Si hace shift-click, permitir que ponga el item en el slot 22
            if (event.isShiftClick()) {
                event.setCancelled(true);
                ItemStack clickedItem = event.getCurrentItem();
                if (clickedItem != null && !clickedItem.getType().isAir()) {
                    ItemStack inSlot = event.getView().getTopInventory().getItem(AuctionSellGUI.ITEM_SLOT);
                    if (inSlot == null || inSlot.getType().isAir()) {
                        event.getView().getTopInventory().setItem(AuctionSellGUI.ITEM_SLOT, clickedItem.clone());
                        player.getInventory().setItem(event.getSlot(), null);
                    }
                }
            }
            return;
        }

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // Cancelar
        if (slot == 45) {
            playClick(player);
            // Devolver item si hay uno
            returnSellItem(event, player);
            selectedPrice.remove(uid);
            openMainGUI(player, 1);
            return;
        }

        // Precio predefinido
        double price = AuctionSellGUI.getPriceFromSlot(slot);
        if (price > 0) {
            playClick(player);
            selectedPrice.put(uid, price);
            AuctionSellGUI.updateWithPrice(event.getView().getTopInventory(), price);
            return;
        }

        // Precio personalizado (anvil)
        if (slot == 36) {
            player.closeInventory();
            awaitingChatInput.put(uid, ChatInputType.CUSTOM_PRICE);
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("§6§l✦ §e§lPrecio personalizado"));
            player.sendMessage(SmallCaps.convert("§7Escribe el precio en el chat (ej: §e15000§7)."));
            player.sendMessage("§7Mín: §e$" + String.format("%,.0f", AuctionItem.MIN_PRICE) +
                " §7| Máx: §e$" + String.format("%,.0f", AuctionItem.MAX_PRICE));
            player.sendMessage(SmallCaps.convert("§7Escribe §c\"cancelar\" §7para volver."));
            player.sendMessage("");
            return;
        }

        // Confirmar venta
        if (slot == 49 && clicked.getType() == Material.LIME_CONCRETE) {
            Double selPrice = selectedPrice.get(uid);
            if (selPrice == null) {
                player.sendMessage(SmallCaps.convert("§c§l✖ §cSelecciona un precio primero."));
                return;
            }

            ItemStack sellItem = event.getView().getTopInventory().getItem(AuctionSellGUI.ITEM_SLOT);
            if (sellItem == null || sellItem.getType().isAir()) {
                player.sendMessage(SmallCaps.convert("§c§l✖ §cColoca un item para vender."));
                return;
            }

            // Listar el item
            AuctionManager.AuctionResult result = manager.listItem(player, sellItem, selPrice);
            player.sendMessage(result.getMessage());

            if (result.isSuccess()) {
                // Quitar el item del slot (ya fue registrado)
                event.getView().getTopInventory().setItem(AuctionSellGUI.ITEM_SLOT, null);
                selectedPrice.remove(uid);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                // Volver al AH
                Bukkit.getScheduler().runTaskLater(plugin, () -> openMainGUI(player, 1), 1L);
            }
            return;
        }
    }

    // ==================== CONFIRM GUI ====================

    private void handleConfirmGUIClick(InventoryClickEvent event, Player player) {
        int slot = event.getRawSlot();
        event.setCancelled(true);

        UUID uid = player.getUniqueId();
        UUID listingId = pendingPurchase.get(uid);
        if (listingId == null) {
            player.closeInventory();
            return;
        }

        // Confirmar
        if (slot == 11) {
            AuctionManager.AuctionResult result = manager.buyItem(player, listingId);
            player.sendMessage(result.getMessage());
            pendingPurchase.remove(uid);

            if (result.isSuccess()) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
            } else {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> openMainGUI(player, 1), 1L);
            return;
        }

        // Cancelar
        if (slot == 15) {
            playClick(player);
            pendingPurchase.remove(uid);
            openMainGUI(player, 1);
            return;
        }
    }

    // ==================== MY LISTINGS GUI ====================

    private void handleMyListingsGUIClick(InventoryClickEvent event, Player player, String title) {
        int slot = event.getRawSlot();
        if (slot >= 54) return;
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (isGlassPane(clicked.getType())) return;

        AuctionMyListingsGUI.Tab tab = AuctionMyListingsGUI.getTabFromTitle(title);
        int page = AuctionMyListingsGUI.getPageFromTitle(title);

        // Pestaña Activos
        if (slot == 2) {
            playClick(player);
            AuctionMyListingsGUI.open(player, manager, AuctionMyListingsGUI.Tab.ACTIVOS, 1);
            return;
        }

        // Pestaña Expirados
        if (slot == 6) {
            playClick(player);
            AuctionMyListingsGUI.open(player, manager, AuctionMyListingsGUI.Tab.EXPIRADOS, 1);
            return;
        }

        // Volver al AH
        if (slot == 49) {
            playClick(player);
            openMainGUI(player, 1);
            return;
        }

        // Página anterior
        if (slot == 45) {
            playClick(player);
            AuctionMyListingsGUI.open(player, manager, tab, page - 1);
            return;
        }

        // Página siguiente
        if (slot == 53) {
            playClick(player);
            AuctionMyListingsGUI.open(player, manager, tab, page + 1);
            return;
        }

        // Recoger todos
        if (slot == 51 && tab == AuctionMyListingsGUI.Tab.EXPIRADOS) {
            List<AuctionItem> expired = manager.getExpiredListings(player.getUniqueId());
            int collected = 0;
            for (AuctionItem item : expired) {
                if (player.getInventory().firstEmpty() == -1) break;
                AuctionManager.AuctionResult result = manager.collectItem(player, item.getId());
                if (result.isSuccess()) collected++;
            }
            if (collected > 0) {
                player.sendMessage(SmallCaps.convert("§a§l✔ §fRecogiste §e" + collected + " §fitems."));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
            } else {
                player.sendMessage(SmallCaps.convert("§c§l✖ §cNo se pudo recoger ningún item."));
            }
            AuctionMyListingsGUI.open(player, manager, tab, page);
            return;
        }

        // Click en un item
        List<AuctionItem> items;
        if (tab == AuctionMyListingsGUI.Tab.ACTIVOS) {
            items = manager.getActiveListings(player.getUniqueId());
        } else {
            items = manager.getExpiredListings(player.getUniqueId());
        }

        AuctionItem auction = AuctionMyListingsGUI.getItemAtSlot(slot, page, items);
        if (auction != null) {
            if (tab == AuctionMyListingsGUI.Tab.ACTIVOS) {
                // Cancelar listing
                AuctionManager.AuctionResult result = manager.cancelListing(player, auction.getId());
                player.sendMessage(result.getMessage());
                if (result.isSuccess()) {
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
                }
            } else {
                // Recoger item expirado
                AuctionManager.AuctionResult result = manager.collectItem(player, auction.getId());
                player.sendMessage(result.getMessage());
                if (result.isSuccess()) {
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
                }
            }
            AuctionMyListingsGUI.open(player, manager, tab, page);
        }
    }

    // ==================== CHAT INPUT ====================

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();
        ChatInputType inputType = awaitingChatInput.remove(uid);
        if (inputType == null) return;

        event.setCancelled(true);
        String message = event.getMessage().trim();

        if (message.equalsIgnoreCase("cancelar") || message.equalsIgnoreCase("cancel")) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (inputType == ChatInputType.CUSTOM_PRICE) {
                    AuctionSellGUI.open(player);
                } else {
                    openMainGUI(player, 1);
                }
            });
            return;
        }

        if (inputType == ChatInputType.CUSTOM_PRICE) {
            try {
                double price = Double.parseDouble(message.replace(",", "").replace("$", ""));
                if (price < AuctionItem.MIN_PRICE || price > AuctionItem.MAX_PRICE) {
                    player.sendMessage("§c§l✖ §cPrecio inválido. Mín: $" + String.format("%,.0f", AuctionItem.MIN_PRICE) +
                        " | Máx: $" + String.format("%,.0f", AuctionItem.MAX_PRICE));
                    Bukkit.getScheduler().runTask(plugin, () -> AuctionSellGUI.open(player));
                    return;
                }
                selectedPrice.put(uid, price);
                player.sendMessage(SmallCaps.convert("§a§l✔ §fPrecio establecido: §e$" + String.format("%,.0f", price)));
                Bukkit.getScheduler().runTask(plugin, () -> {
                    AuctionSellGUI.open(player);
                    // Actualizar precio después de abrir
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.getOpenInventory().getTitle().equals(AuctionSellGUI.TITLE)) {
                            AuctionSellGUI.updateWithPrice(player.getOpenInventory().getTopInventory(), price);
                        }
                    }, 2L);
                });
            } catch (NumberFormatException e) {
                player.sendMessage(SmallCaps.convert("§c§l✖ §cEso no es un número válido. Intenta de nuevo."));
                Bukkit.getScheduler().runTask(plugin, () -> AuctionSellGUI.open(player));
            }
        } else if (inputType == ChatInputType.SEARCH) {
            playerSearch.put(uid, message);
            player.sendMessage(SmallCaps.convert("§a§l✔ §fBuscando: §e" + message));
            Bukkit.getScheduler().runTask(plugin, () -> openMainGUI(player, 1));
        }
    }

    // ==================== INVENTORY CLOSE ====================

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        String title = event.getView().getTitle();

        // Si cierra la GUI de venta, devolver el item si hay uno
        if (AuctionSellGUI.isSellGUI(title)) {
            ItemStack sellItem = event.getInventory().getItem(AuctionSellGUI.ITEM_SLOT);
            if (sellItem != null && !sellItem.getType().isAir()) {
                // Solo devolver si no está esperando chat input
                if (!awaitingChatInput.containsKey(player.getUniqueId())) {
                    if (player.getInventory().firstEmpty() != -1) {
                        player.getInventory().addItem(sellItem);
                    } else {
                        player.getWorld().dropItemNaturally(player.getLocation(), sellItem);
                    }
                }
            }
        }
    }

    // ==================== HELPERS ====================

    public void openMainGUI(Player player, int page) {
        UUID uid = player.getUniqueId();
        AuctionManager.Category category = playerCategory.getOrDefault(uid, AuctionManager.Category.TODOS);
        AuctionManager.SortType sort = playerSort.getOrDefault(uid, AuctionManager.SortType.RECIENTE);
        String search = playerSearch.get(uid);
        AuctionGUI.open(player, manager, page, category, sort, search);
    }

    private void returnSellItem(InventoryClickEvent event, Player player) {
        ItemStack sellItem = event.getView().getTopInventory().getItem(AuctionSellGUI.ITEM_SLOT);
        if (sellItem != null && !sellItem.getType().isAir()) {
            if (player.getInventory().firstEmpty() != -1) {
                player.getInventory().addItem(sellItem);
            } else {
                player.getWorld().dropItemNaturally(player.getLocation(), sellItem);
            }
            event.getView().getTopInventory().setItem(AuctionSellGUI.ITEM_SLOT, null);
        }
    }

    private boolean isGlassPane(Material material) {
        return material.name().contains("STAINED_GLASS_PANE");
    }

    private void playClick(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }
}
