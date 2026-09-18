package com.moonlight.coreprotect.warps;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import com.moonlight.coreprotect.warps.PlayerWarpManager.PlayerWarp;
import com.moonlight.coreprotect.warps.PlayerWarpManager.SortMode;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerWarpGUI implements Listener {

    // GUI title prefixes for each panel
    private static final String T_BROWSE   = sc("§3§l✦ §b§lᴘʟᴀʏᴇʀ §3§lᴡᴀʀᴘꜱ §3§l✦");
    private static final String T_DETAIL   = sc("§3§l✦ §e§lᴅᴇᴛᴀʟʟᴇ §3§lᴡᴀʀᴘ §3§l✦");
    private static final String T_MYWARPS  = sc("§3§l✦ §a§lᴍɪꜱ §3§lᴡᴀʀᴘꜱ §3§l✦");
    private static final String T_EDIT     = sc("§3§l✦ §6§lᴇᴅɪᴛᴀʀ §3§lᴡᴀʀᴘ §3§l✦");
    private static final String T_ADMIN    = sc("§3§l✦ §c§lᴀᴅᴍɪɴ §3§lᴘᴀɴᴇʟ §3§l✦");

    private static final int WARPS_PER_PAGE = 28;
    private final CoreProtectPlugin plugin;

    // Per-player state
    private final Map<UUID, Integer> playerPages = new ConcurrentHashMap<>();
    private final Map<UUID, SortMode> playerSort = new ConcurrentHashMap<>();
    private final Map<UUID, String> viewingWarp = new ConcurrentHashMap<>();     // warp name being viewed
    private final Map<UUID, String> editingWarp = new ConcurrentHashMap<>();     // warp name being edited
    private final Map<UUID, String> chatInputMode = new ConcurrentHashMap<>();   // "price:warpname" or "desc:warpname" or "create"
    private final Map<UUID, Integer> pendingTeleports = new ConcurrentHashMap<>();

    public PlayerWarpGUI(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    // ========================================================================================
    //  1. BROWSE GUI — Main warp list
    // ========================================================================================

    public void openBrowse(Player player, int page) {
        PlayerWarpManager mgr = plugin.getPlayerWarpManager();
        SortMode sort = playerSort.getOrDefault(player.getUniqueId(), SortMode.VISITS);
        List<PlayerWarp> warps = mgr.getWarpsSorted(sort);

        int totalPages = Math.max(1, (int) Math.ceil((double) warps.size() / WARPS_PER_PAGE));
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;
        playerPages.put(player.getUniqueId(), page);

        Inventory inv = Bukkit.createInventory(null, 54, T_BROWSE + " §8(" + (page + 1) + "/" + totalPages + ")");
        fillBorder(inv, Material.CYAN_STAINED_GLASS_PANE);

        // Warp items
        int start = page * WARPS_PER_PAGE;
        int idx = 0;
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                int i = start + idx++;
                if (i < warps.size()) inv.setItem(row * 9 + col, browseWarpItem(warps.get(i)));
            }
        }

        // Header info
        int myCount = mgr.getWarpsByOwner(player.getUniqueId()).size();
        int maxW = mgr.getMaxWarps(player);
        inv.setItem(4, item(Material.ENDER_EYE, "§b§lPlayer Warps",
                "§7Total: §f" + warps.size() + " warps",
                "§7Tus warps: §f" + myCount + "§7/§f" + maxW,
                "§7Orden: §e" + sortName(sort), "",
                "§7Click en un warp para ver detalles."));

        // Bottom bar
        if (page > 0) inv.setItem(45, item(Material.ARROW, "§a← Anterior"));
        if (page < totalPages - 1) inv.setItem(53, item(Material.ARROW, "§a→ Siguiente"));

        // Sort buttons
        inv.setItem(46, item(sort == SortMode.VISITS ? Material.LIME_DYE : Material.GRAY_DYE,
                "§e§lOrdenar: Visitas", "§7Más visitados primero.", sort == SortMode.VISITS ? "§a▶ Seleccionado" : "§7Click para seleccionar"));
        inv.setItem(47, item(sort == SortMode.RATING ? Material.LIME_DYE : Material.GRAY_DYE,
                "§e§lOrdenar: Valoración", "§7Mejor valorados primero.", sort == SortMode.RATING ? "§a▶ Seleccionado" : "§7Click para seleccionar"));
        inv.setItem(48, item(sort == SortMode.NEWEST ? Material.LIME_DYE : Material.GRAY_DYE,
                "§e§lOrdenar: Nuevos", "§7Más recientes primero.", sort == SortMode.NEWEST ? "§a▶ Seleccionado" : "§7Click para seleccionar"));

        // My Warps
        inv.setItem(49, item(Material.PLAYER_HEAD, "§a§lMis Warps", "§7Gestiona tus warps."));
        // Create
        inv.setItem(50, item(Material.NETHER_STAR, "§b§lCrear Warp",
                "§7Crea una warp en tu posición.", "§7Tus warps: §f" + myCount + "§7/§f" + maxW));
        // Admin
        if (player.hasPermission("coreprotect.admin")) {
            inv.setItem(51, item(Material.COMMAND_BLOCK, "§c§lAdmin Panel", "§7Administra todas las warps."));
        }

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
    }

    private ItemStack browseWarpItem(PlayerWarp w) {
        ItemStack i = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skull = (SkullMeta) i.getItemMeta();
        skull.setOwningPlayer(Bukkit.getOfflinePlayer(w.owner));
        skull.setDisplayName(sc("§e§l" + w.name));
        List<String> lore = new ArrayList<>();
        lore.add(sc("§7Dueño: §f" + w.ownerName));
        if (!w.description.isEmpty()) lore.add(sc("§7" + w.description));
        lore.add(sc("§7" + w.getStarsDisplay() + " §8(" + String.format("%.1f", w.getAverageRating()) + " - " + w.getRatingCount() + " votos)"));
        lore.add(sc("§7Visitas: §f" + w.visits));
        lore.add(sc(w.price > 0 ? "§6Precio: §e$" + money(w.price) : "§aGratis"));
        lore.add(""); lore.add(sc("§a▶ Click para ver detalles"));
        skull.setLore(lore);
        i.setItemMeta(skull);
        return i;
    }

    // ========================================================================================
    //  2. DETAIL GUI — View one warp, rate, teleport
    // ========================================================================================

    public void openDetail(Player player, String warpName) {
        PlayerWarp w = plugin.getPlayerWarpManager().getWarp(warpName);
        if (w == null) { player.sendMessage(sc("§c§l✖ §cEsa warp ya no existe.")); return; }
        viewingWarp.put(player.getUniqueId(), w.name);

        Inventory inv = Bukkit.createInventory(null, 54, T_DETAIL);
        fillBorder(inv, Material.LIGHT_BLUE_STAINED_GLASS_PANE);

        // Row 0: Warp head
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta hm = (SkullMeta) head.getItemMeta();
        hm.setOwningPlayer(Bukkit.getOfflinePlayer(w.owner));
        hm.setDisplayName(sc("§e§l" + w.name));
        List<String> headLore = new ArrayList<>();
        headLore.add(sc("§7Dueño: §f" + w.ownerName));
        if (!w.description.isEmpty()) { headLore.add(""); headLore.add(sc("§f\"" + w.description + "\"")); }
        headLore.add(""); headLore.add(sc("§7Mundo: §f" + w.location.getWorld().getName()));
        headLore.add(sc("§7Coords: §f" + w.location.getBlockX() + ", " + w.location.getBlockY() + ", " + w.location.getBlockZ()));
        headLore.add(sc("§7Visitas: §f" + w.visits));
        headLore.add(sc("§7Creada: §f" + daysAgo(w.created) + " días"));
        hm.setLore(headLore);
        head.setItemMeta(hm);
        inv.setItem(4, head);

        // Row 1: Rating stars
        int playerRating = w.ratings.getOrDefault(player.getUniqueId(), 0);
        double avg = w.getAverageRating();
        for (int s = 1; s <= 5; s++) {
            boolean filled = s <= playerRating;
            Material starMat = filled ? Material.GOLD_NUGGET : Material.IRON_NUGGET;
            String starColor = filled ? "§e" : "§7";
            inv.setItem(10 + s, item(starMat, starColor + "§l" + starsOf(s),
                    s <= playerRating ? "§aTu valoración actual" : "§7Click para valorar con " + s + " estrella" + (s > 1 ? "s" : ""),
                    "", "§7Media: " + w.getStarsDisplay() + " §8(" + String.format("%.1f", avg) + " - " + w.getRatingCount() + " votos)"));
        }
        inv.setItem(17, item(Material.SUNFLOWER, "§e§lValoración Media",
                w.getStarsDisplay(), "§f" + String.format("%.1f", avg) + " §7de 5",
                "§7" + w.getRatingCount() + " valoraciones totales"));

        // Row 2-3: Teleport + price
        String priceText = w.price > 0 ? "§6Precio: §e$" + money(w.price) : "§aGratis";
        boolean isOwner = w.owner.equals(player.getUniqueId());
        String tpExtra = isOwner ? "§7(Es tu warp — gratis)" : priceText;
        inv.setItem(22, item(Material.ENDER_PEARL, "§a§l✦ Teletransportarse",
                "§7Viaja a esta warp.", "", tpExtra, "", "§a▶ Click para viajar"));

        // Row 4: Owner/Admin actions
        if (isOwner) {
            inv.setItem(28, item(Material.WRITABLE_BOOK, "§6§lEditar Warp", "§7Cambia precio, descripción, etc."));
            inv.setItem(34, item(Material.BARRIER, "§c§lEliminar Warp", "§cClick para eliminar tu warp.", "§7Esta acción es permanente."));
        } else if (player.hasPermission("coreprotect.admin")) {
            inv.setItem(28, item(Material.WRITABLE_BOOK, "§6§lEditar (Admin)", "§7Edita esta warp como admin."));
            inv.setItem(34, item(Material.BARRIER, "§c§lEliminar (Admin)", "§cClick para eliminar esta warp."));
        }

        // Back button
        inv.setItem(49, item(Material.ARROW, "§c§l← Volver", "§7Volver a la lista de warps."));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.2f);
    }

    // ========================================================================================
    //  3. MY WARPS GUI — Player's own warps
    // ========================================================================================

    public void openMyWarps(Player player) {
        PlayerWarpManager mgr = plugin.getPlayerWarpManager();
        List<PlayerWarp> myWarps = mgr.getWarpsByOwner(player.getUniqueId());
        int maxW = mgr.getMaxWarps(player);

        Inventory inv = Bukkit.createInventory(null, 54, T_MYWARPS);
        fillBorder(inv, Material.LIME_STAINED_GLASS_PANE);

        inv.setItem(4, item(Material.ENDER_EYE, "§a§lMis Warps",
                "§7Tienes §f" + myWarps.size() + "§7/§f" + maxW + " §7warps.",
                "", "§7Click en un warp para editarlo."));

        int idx = 0;
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                if (idx < myWarps.size()) {
                    PlayerWarp w = myWarps.get(idx);
                    ItemStack wi = new ItemStack(Material.PLAYER_HEAD);
                    SkullMeta sm = (SkullMeta) wi.getItemMeta();
                    sm.setOwningPlayer(Bukkit.getOfflinePlayer(w.owner));
                    sm.setDisplayName(sc("§e§l" + w.name));
                    List<String> lore = new ArrayList<>();
                    lore.add(sc(w.getStarsDisplay() + " §8(" + String.format("%.1f", w.getAverageRating()) + ")"));
                    lore.add(sc("§7Visitas: §f" + w.visits));
                    lore.add(sc(w.price > 0 ? "§6Precio: §e$" + money(w.price) : "§aGratis"));
                    if (!w.description.isEmpty()) lore.add(sc("§7" + w.description));
                    double earned = w.price * w.visits;
                    if (earned > 0) lore.add(sc("§7Ganancias totales: §e$" + money(earned)));
                    lore.add(""); lore.add(sc("§a▶ Click para editar"));
                    sm.setLore(lore);
                    wi.setItemMeta(sm);
                    inv.setItem(row * 9 + col, wi);
                }
                idx++;
            }
        }

        // Create button
        if (myWarps.size() < maxW) {
            inv.setItem(50, item(Material.NETHER_STAR, "§b§l+ Crear Warp",
                    "§7Crea una nueva warp aquí.", "§7Escribe el nombre en el chat."));
        } else {
            inv.setItem(50, item(Material.BARRIER, "§c§lLímite alcanzado",
                    "§7Ya tienes §f" + maxW + " §7warps.", "§7Elimina una para crear otra."));
        }

        inv.setItem(49, item(Material.ARROW, "§c§l← Volver", "§7Volver a la lista."));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.2f);
    }

    // ========================================================================================
    //  4. EDIT GUI — Edit warp settings (price, description, relocate)
    // ========================================================================================

    public void openEdit(Player player, String warpName) {
        PlayerWarp w = plugin.getPlayerWarpManager().getWarp(warpName);
        if (w == null) return;
        editingWarp.put(player.getUniqueId(), w.name);

        Inventory inv = Bukkit.createInventory(null, 54, T_EDIT);
        fillBorder(inv, Material.ORANGE_STAINED_GLASS_PANE);

        // Header — warp info
        inv.setItem(4, item(Material.ENDER_EYE, "§6§lEditando: §e" + w.name,
                "§7Dueño: §f" + w.ownerName,
                "§7Visitas: §f" + w.visits,
                w.getStarsDisplay() + " §8(" + String.format("%.1f", w.getAverageRating()) + ")"));

        // Price section
        inv.setItem(19, item(Material.GOLD_INGOT, "§6§lPrecio actual: §e$" + money(w.price),
                "§7Los jugadores pagan esto para viajar.", "§7Tú recibes el dinero.", "",
                "§e▶ Click para cambiar el precio", "§7(Escribe el nuevo precio en el chat)"));

        // Description section
        String descDisplay = w.description.isEmpty() ? "§7Sin descripción" : "§f\"" + w.description + "\"";
        inv.setItem(21, item(Material.WRITABLE_BOOK, "§b§lDescripción",
                descDisplay, "",
                "§e▶ Click para cambiar", "§7(Escribe la descripción en el chat)"));

        // Relocate
        inv.setItem(23, item(Material.COMPASS, "§d§lReubicar Warp",
                "§7Mueve la warp a tu posición actual.", "",
                "§e▶ Click para reubicar"));

        // Stats
        double totalEarned = w.price * w.visits;
        inv.setItem(25, item(Material.PAPER, "§f§lEstadísticas",
                "§7Visitas totales: §f" + w.visits,
                "§7Valoración: " + w.getStarsDisplay() + " §7(" + w.getRatingCount() + " votos)",
                "§7Ganancias estimadas: §e$" + money(totalEarned)));

        // Delete
        inv.setItem(40, item(Material.BARRIER, "§c§lEliminar Warp",
                "§cEsta acción es permanente.", "§c¡No se puede deshacer!", "",
                "§c▶ Shift+Click para eliminar"));

        // Back
        inv.setItem(49, item(Material.ARROW, "§c§l← Volver", "§7Volver a mis warps."));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.5f);
    }

    // ========================================================================================
    //  5. ADMIN GUI — Full admin panel
    // ========================================================================================

    public void openAdmin(Player player, int page) {
        if (!player.hasPermission("coreprotect.admin")) return;
        PlayerWarpManager mgr = plugin.getPlayerWarpManager();
        List<PlayerWarp> warps = mgr.getWarpsSorted(SortMode.VISITS);
        playerPages.put(player.getUniqueId(), page);

        int totalPages = Math.max(1, (int) Math.ceil((double) warps.size() / WARPS_PER_PAGE));
        if (page >= totalPages) page = totalPages - 1;

        Inventory inv = Bukkit.createInventory(null, 54, T_ADMIN + " §8(" + (page + 1) + "/" + totalPages + ")");
        fillBorder(inv, Material.RED_STAINED_GLASS_PANE);

        // Stats header
        inv.setItem(4, item(Material.COMMAND_BLOCK, "§c§lAdmin — Player Warps",
                "§7Total warps: §f" + mgr.getTotalWarps(),
                "§7Visitas totales: §f" + mgr.getTotalVisits(),
                "", "§7Click en un warp para gestionar.",
                "§c§lShift+Click §cpara eliminar rápido."));

        // Warp list
        int start = page * WARPS_PER_PAGE;
        int idx = 0;
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                int i = start + idx++;
                if (i < warps.size()) {
                    PlayerWarp w = warps.get(i);
                    ItemStack wi = new ItemStack(Material.PLAYER_HEAD);
                    SkullMeta sm = (SkullMeta) wi.getItemMeta();
                    sm.setOwningPlayer(Bukkit.getOfflinePlayer(w.owner));
                    sm.setDisplayName(sc("§e" + w.name + " §7[" + w.ownerName + "]"));
                    List<String> lore = new ArrayList<>();
                    lore.add(sc(w.getStarsDisplay() + " §7(" + String.format("%.1f", w.getAverageRating()) + ")"));
                    lore.add(sc("§7Visitas: §f" + w.visits + " §7| Precio: §e$" + money(w.price)));
                    lore.add(sc("§7Mundo: §f" + w.location.getWorld().getName()));
                    lore.add(sc("§7XYZ: §f" + w.location.getBlockX() + " " + w.location.getBlockY() + " " + w.location.getBlockZ()));
                    if (!w.description.isEmpty()) lore.add(sc("§7\"" + w.description + "\""));
                    lore.add(""); lore.add(sc("§a▶ Click: ver/editar"));
                    lore.add(sc("§c✖ Shift+Click: eliminar"));
                    sm.setLore(lore);
                    wi.setItemMeta(sm);
                    inv.setItem(row * 9 + col, wi);
                }
            }
        }

        if (page > 0) inv.setItem(45, item(Material.ARROW, "§a← Anterior"));
        if (page < totalPages - 1) inv.setItem(53, item(Material.ARROW, "§a→ Siguiente"));
        inv.setItem(49, item(Material.ARROW, "§c§l← Volver", "§7Volver a la lista principal."));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
    }

    // ========================================================================================
    //  CLICK HANDLERS
    // ========================================================================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        if (title.startsWith(T_BROWSE))   { event.setCancelled(true); handleBrowseClick(player, slot, event, title); return; }
        if (title.startsWith(T_DETAIL))   { event.setCancelled(true); handleDetailClick(player, slot, event); return; }
        if (title.startsWith(T_MYWARPS))  { event.setCancelled(true); handleMyWarpsClick(player, slot, event); return; }
        if (title.startsWith(T_EDIT))     { event.setCancelled(true); handleEditClick(player, slot, event); return; }
        if (title.startsWith(T_ADMIN))    { event.setCancelled(true); handleAdminClick(player, slot, event, title); return; }
    }

    // --- BROWSE clicks ---
    private void handleBrowseClick(Player player, int slot, InventoryClickEvent event, String title) {
        int page = playerPages.getOrDefault(player.getUniqueId(), 0);
        SortMode sort = playerSort.getOrDefault(player.getUniqueId(), SortMode.VISITS);
        List<PlayerWarp> warps = plugin.getPlayerWarpManager().getWarpsSorted(sort);

        if (slot == 45) { openBrowse(player, page - 1); return; }
        if (slot == 53) { openBrowse(player, page + 1); return; }
        if (slot == 46) { playerSort.put(player.getUniqueId(), SortMode.VISITS); openBrowse(player, 0); return; }
        if (slot == 47) { playerSort.put(player.getUniqueId(), SortMode.RATING); openBrowse(player, 0); return; }
        if (slot == 48) { playerSort.put(player.getUniqueId(), SortMode.NEWEST); openBrowse(player, 0); return; }
        if (slot == 49) { openMyWarps(player); return; }
        if (slot == 50) { startCreateWarp(player); return; }
        if (slot == 51 && player.hasPermission("coreprotect.admin")) { openAdmin(player, 0); return; }

        // Warp item
        PlayerWarp w = getWarpAtSlot(warps, page, slot);
        if (w != null) openDetail(player, w.name);
    }

    // --- DETAIL clicks ---
    private void handleDetailClick(Player player, int slot, InventoryClickEvent event) {
        String warpName = viewingWarp.get(player.getUniqueId());
        if (warpName == null) return;
        PlayerWarp w = plugin.getPlayerWarpManager().getWarp(warpName);
        if (w == null) { player.closeInventory(); return; }

        // Rating stars (slots 11-15)
        if (slot >= 11 && slot <= 15) {
            int stars = slot - 10;
            if (w.owner.equals(player.getUniqueId())) {
                player.sendMessage(sc("§c§l✖ §cNo puedes valorar tu propia warp."));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            } else {
                plugin.getPlayerWarpManager().addRating(warpName, player.getUniqueId(), stars);
                player.sendMessage(sc("§e§l★ §fHas valorado §e" + warpName + " §fcon §e" + stars + " estrella" + (stars > 1 ? "s" : "") + "§f."));
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
            }
            openDetail(player, warpName); // Refresh
            return;
        }

        // Teleport (slot 22)
        if (slot == 22) {
            player.closeInventory();
            startTeleport(player, w);
            return;
        }

        // Edit (slot 28)
        if (slot == 28 && (w.owner.equals(player.getUniqueId()) || player.hasPermission("coreprotect.admin"))) {
            openEdit(player, warpName);
            return;
        }

        // Delete (slot 34)
        if (slot == 34 && (w.owner.equals(player.getUniqueId()) || player.hasPermission("coreprotect.admin"))) {
            plugin.getPlayerWarpManager().deleteWarp(player, warpName);
            player.sendMessage(sc("§c§l✖ §cWarp '§f" + warpName + "§c' eliminada."));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 1.0f);
            openBrowse(player, 0);
            return;
        }

        // Back (slot 49)
        if (slot == 49) { openBrowse(player, playerPages.getOrDefault(player.getUniqueId(), 0)); }
    }

    // --- MY WARPS clicks ---
    private void handleMyWarpsClick(Player player, int slot, InventoryClickEvent event) {
        List<PlayerWarp> myWarps = plugin.getPlayerWarpManager().getWarpsByOwner(player.getUniqueId());

        if (slot == 49) { openBrowse(player, 0); return; }
        if (slot == 50) { startCreateWarp(player); return; }

        // Warp item
        PlayerWarp w = getWarpAtSlot(myWarps, 0, slot);
        if (w != null) openEdit(player, w.name);
    }

    // --- EDIT clicks ---
    private void handleEditClick(Player player, int slot, InventoryClickEvent event) {
        String warpName = editingWarp.get(player.getUniqueId());
        if (warpName == null) return;
        PlayerWarp w = plugin.getPlayerWarpManager().getWarp(warpName);
        if (w == null) { player.closeInventory(); return; }

        // Set price (slot 19)
        if (slot == 19) {
            chatInputMode.put(player.getUniqueId(), "price:" + warpName);
            player.closeInventory();
            player.sendMessage("");
            player.sendMessage(sc("§6§l✎ §eEscribe el nuevo precio en el chat."));
            player.sendMessage(sc("§7Escribe §f0 §7para hacerla gratis, o §fcancelar §7para cancelar."));
            player.sendMessage("");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 2.0f);
            return;
        }

        // Set description (slot 21)
        if (slot == 21) {
            chatInputMode.put(player.getUniqueId(), "desc:" + warpName);
            player.closeInventory();
            player.sendMessage("");
            player.sendMessage(sc("§b§l✎ §bEscribe la nueva descripción (máx 60 chars)."));
            player.sendMessage(sc("§7Escribe §fborrar §7para quitar la descripción, o §fcancelar §7para cancelar."));
            player.sendMessage("");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 2.0f);
            return;
        }

        // Relocate (slot 23)
        if (slot == 23) {
            plugin.getPlayerWarpManager().relocateWarp(warpName, player.getLocation());
            player.sendMessage(sc("§d§l✦ §aWarp reubicada a tu posición actual."));
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.5f);
            openEdit(player, warpName);
            return;
        }

        // Delete (slot 40 with shift)
        if (slot == 40 && event.isShiftClick()) {
            plugin.getPlayerWarpManager().deleteWarp(player, warpName);
            player.sendMessage(sc("§c§l✖ §cWarp '§f" + warpName + "§c' eliminada."));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 1.0f);
            openMyWarps(player);
            return;
        }

        // Back (slot 49)
        if (slot == 49) {
            if (player.hasPermission("coreprotect.admin") && !w.owner.equals(player.getUniqueId())) {
                openAdmin(player, 0);
            } else {
                openMyWarps(player);
            }
        }
    }

    // --- ADMIN clicks ---
    private void handleAdminClick(Player player, int slot, InventoryClickEvent event, String title) {
        int page = playerPages.getOrDefault(player.getUniqueId(), 0);
        List<PlayerWarp> warps = plugin.getPlayerWarpManager().getWarpsSorted(SortMode.VISITS);

        if (slot == 45) { openAdmin(player, page - 1); return; }
        if (slot == 53) { openAdmin(player, page + 1); return; }
        if (slot == 49) { openBrowse(player, 0); return; }

        PlayerWarp w = getWarpAtSlot(warps, page, slot);
        if (w != null) {
            if (event.isShiftClick()) {
                plugin.getPlayerWarpManager().adminDeleteWarp(w.name);
                player.sendMessage(sc("§c§l✖ §c[Admin] Warp '§f" + w.name + "§c' de §f" + w.ownerName + " §celiminada."));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 1.0f);
                openAdmin(player, page);
            } else {
                openEdit(player, w.name);
            }
        }
    }

    // ========================================================================================
    //  CHAT INPUT HANDLER
    // ========================================================================================

    @EventHandler(priority = EventPriority.LOW)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String mode = chatInputMode.remove(player.getUniqueId());
        if (mode == null) return;

        event.setCancelled(true);
        String msg = event.getMessage().trim();

        if (msg.equalsIgnoreCase("cancelar") || msg.equalsIgnoreCase("cancel")) {
            player.sendMessage(sc("§7Cancelado."));
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (mode.startsWith("price:") || mode.startsWith("desc:")) {
                    openEdit(player, mode.split(":", 2)[1]);
                } else if (mode.equals("create")) {
                    openMyWarps(player);
                }
            });
            return;
        }

        if (mode.startsWith("price:")) {
            String warpName = mode.split(":", 2)[1];
            try {
                double price = Double.parseDouble(msg.replace(",", "."));
                if (price < 0) price = 0;
                if (price > 10000000) price = 10000000;
                plugin.getPlayerWarpManager().setPrice(warpName, price);
                double finalPrice = price;
                player.sendMessage(sc("§6§l✦ §aPrecio actualizado a §e$" + money(price) + "§a."));
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
                Bukkit.getScheduler().runTask(plugin, () -> openEdit(player, warpName));
            } catch (NumberFormatException e) {
                player.sendMessage(sc("§c§l✖ §cNúmero inválido. Cancelado."));
                Bukkit.getScheduler().runTask(plugin, () -> openEdit(player, warpName));
            }
            return;
        }

        if (mode.startsWith("desc:")) {
            String warpName = mode.split(":", 2)[1];
            if (msg.equalsIgnoreCase("borrar") || msg.equalsIgnoreCase("delete") || msg.equalsIgnoreCase("clear")) {
                plugin.getPlayerWarpManager().setDescription(warpName, "");
                player.sendMessage(sc("§b§l✦ §aDescripción eliminada."));
            } else {
                plugin.getPlayerWarpManager().setDescription(warpName, msg);
                player.sendMessage(sc("§b§l✦ §aDescripción actualizada."));
            }
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
            Bukkit.getScheduler().runTask(plugin, () -> openEdit(player, warpName));
            return;
        }

        if (mode.equals("create")) {
            String name = msg.toLowerCase().replaceAll("[^a-z0-9_-]", "");
            if (name.isEmpty() || name.length() > 16) {
                player.sendMessage(sc("§c§l✖ §cNombre inválido (1-16 chars, solo letras/números/-/_)."));
                Bukkit.getScheduler().runTask(plugin, () -> openMyWarps(player));
                return;
            }
            if (name.equals("set") || name.equals("delete") || name.equals("admin")) {
                player.sendMessage(sc("§c§l✖ §cNombre reservado."));
                Bukkit.getScheduler().runTask(plugin, () -> openMyWarps(player));
                return;
            }
            PlayerWarpManager mgr = plugin.getPlayerWarpManager();
            if (mgr.warpExists(name)) {
                player.sendMessage(sc("§c§l✖ §cYa existe una warp con ese nombre."));
                Bukkit.getScheduler().runTask(plugin, () -> openMyWarps(player));
                return;
            }
            if (!mgr.canCreateWarp(player)) {
                player.sendMessage(sc("§c§l✖ §cLímite de warps alcanzado."));
                Bukkit.getScheduler().runTask(plugin, () -> openMyWarps(player));
                return;
            }
            if (mgr.createWarp(player, name)) {
                player.sendMessage(sc("§b§l✦ §a¡Warp '§e" + name + "§a' creada!"));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
                Bukkit.getScheduler().runTask(plugin, () -> openEdit(player, name));
            } else {
                player.sendMessage(sc("§c§l✖ §cNo se pudo crear la warp."));
                Bukkit.getScheduler().runTask(plugin, () -> openMyWarps(player));
            }
        }
    }

    private void startCreateWarp(Player player) {
        PlayerWarpManager mgr = plugin.getPlayerWarpManager();
        if (!mgr.canCreateWarp(player)) {
            player.sendMessage(sc("§c§l✖ §cLímite de warps alcanzado. Elimina una primero."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            return;
        }
        chatInputMode.put(player.getUniqueId(), "create");
        player.closeInventory();
        player.sendMessage("");
        player.sendMessage(sc("§b§l✦ §bEscribe el nombre de la nueva warp en el chat."));
        player.sendMessage(sc("§7(1-16 caracteres, solo letras, números, - y _)"));
        player.sendMessage(sc("§7Escribe §fcancelar §7para cancelar."));
        player.sendMessage("");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 2.0f);
    }

    // ========================================================================================
    //  TELEPORT (with cost)
    // ========================================================================================

    public void startTeleport(Player player, PlayerWarp warp) {
        // Cancel existing
        Integer existing = pendingTeleports.remove(player.getUniqueId());
        if (existing != null) Bukkit.getScheduler().cancelTask(existing);

        // Check & charge travel cost
        boolean isOwner = warp.owner.equals(player.getUniqueId());
        double cost = isOwner ? 0 : warp.price;

        if (cost > 0) {
            Economy econ = plugin.getEconomy();
            if (econ == null || !econ.has(player, cost)) {
                player.sendMessage(sc("§c§l✖ §cNecesitas §e$" + money(cost) + " §cpara viajar a esta warp."));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
                return;
            }
            econ.withdrawPlayer(player, cost);
            // Pay the owner
            OfflinePlayer owner = Bukkit.getOfflinePlayer(warp.owner);
            econ.depositPlayer(owner, cost);
            player.sendMessage(sc("§6§l$ §7Se te ha cobrado §e$" + money(cost) + " §7(va a §f" + warp.ownerName + "§7)."));
            Player ownerOnline = Bukkit.getPlayer(warp.owner);
            if (ownerOnline != null && ownerOnline.isOnline()) {
                ownerOnline.sendMessage(sc("§6§l$ §e" + player.getName() + " §7visitó tu warp §e" + warp.name + "§7. §a+$" + money(cost)));
                ownerOnline.playSound(ownerOnline.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
            }
        }

        player.sendMessage(sc("§b§l✦ §7Teletransportando a §e" + warp.name + " §7en §f3s§7... ¡No te muevas!"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 2.0f);

        final Location startLoc = player.getLocation().clone();
        final double refundCost = cost;
        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingTeleports.remove(player.getUniqueId());
            if (!player.isOnline()) return;
            if (player.getLocation().distanceSquared(startLoc) > 1.0) {
                player.sendMessage(sc("§c§l✖ §cTeletransporte cancelado — te moviste."));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
                // Refund
                if (refundCost > 0) {
                    Economy econ = plugin.getEconomy();
                    if (econ != null) {
                        econ.depositPlayer(player, refundCost);
                        econ.withdrawPlayer(Bukkit.getOfflinePlayer(warp.owner), refundCost);
                        player.sendMessage(sc("§6§l$ §7Dinero devuelto: §e$" + money(refundCost)));
                    }
                }
                return;
            }
            player.teleport(warp.location);
            plugin.getPlayerWarpManager().incrementVisits(warp.name);
            player.sendMessage(sc("§b§l✦ §aTeletransportado a §e" + warp.name + " §7(de §f" + warp.ownerName + "§7)"));
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
        }, 60L).getTaskId();
        pendingTeleports.put(player.getUniqueId(), taskId);
    }

    public void cancelTeleport(UUID uuid) {
        Integer taskId = pendingTeleports.remove(uuid);
        if (taskId != null) Bukkit.getScheduler().cancelTask(taskId);
    }

    // ========================================================================================
    //  UTILITY
    // ========================================================================================

    private static String sc(String s) { return SmallCaps.convert(s); }

    private static String money(double d) {
        if (d == (long) d) return String.format("%,d", (long) d);
        return String.format("%,.2f", d);
    }

    private static String sortName(SortMode m) {
        switch (m) {
            case RATING: return "Valoración";
            case NEWEST: return "Más nuevos";
            case CHEAPEST: return "Más baratos";
            default: return "Más visitados";
        }
    }

    private static String starsOf(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append("★");
        return sb.toString();
    }

    private static long daysAgo(long timestamp) {
        return Math.max(0, (System.currentTimeMillis() - timestamp) / (1000L * 60 * 60 * 24));
    }

    private PlayerWarp getWarpAtSlot(List<PlayerWarp> warps, int page, int slot) {
        int row = slot / 9;
        int col = slot % 9;
        if (row < 1 || row > 4 || col < 1 || col > 7) return null;
        int idx = page * WARPS_PER_PAGE + (row - 1) * 7 + (col - 1);
        return (idx >= 0 && idx < warps.size()) ? warps.get(idx) : null;
    }

    private void fillBorder(Inventory inv, Material borderMat) {
        ItemStack border = glassPane(borderMat);
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);
        for (int row = 1; row <= 4; row++) { inv.setItem(row * 9, border); inv.setItem(row * 9 + 8, border); }
    }

    private ItemStack glassPane(Material mat) {
        ItemStack i = new ItemStack(mat);
        ItemMeta m = i.getItemMeta();
        m.setDisplayName(" ");
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        i.setItemMeta(m);
        return i;
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack i = new ItemStack(material);
        ItemMeta m = i.getItemMeta();
        m.setDisplayName(sc(name));
        if (lore != null && lore.length > 0) {
            List<String> conv = new ArrayList<>();
            for (String l : lore) conv.add(sc(l));
            m.setLore(conv);
        }
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        i.setItemMeta(m);
        return i;
    }
}
