package com.moonlight.coreprotect.auction;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import net.milkbowl.vault.economy.Economy;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Gestiona todos los listings de la Auction House: crear, comprar, expirar, persistir.
 */
public class AuctionManager {

    private final CoreProtectPlugin plugin;
    private final File dataFile;
    private final Map<UUID, AuctionItem> listings = new ConcurrentHashMap<>();

    // Categorías de items
    public enum Category {
        TODOS("Todos", "§f"),
        ARMAS("Armas", "§c"),
        ARMADURA("Armadura", "§9"),
        HERRAMIENTAS("Herramientas", "§e"),
        BLOQUES("Bloques", "§a"),
        COMIDA("Comida", "§6"),
        POCIONES("Pociones", "§d"),
        ENCANTADOS("Encantados", "§b"),
        VARIOS("Varios", "§7");

        private final String displayName;
        private final String color;

        Category(String displayName, String color) {
            this.displayName = displayName;
            this.color = color;
        }

        public String getDisplayName() { return color + displayName; }
        public String getName() { return displayName; }
    }

    // Ordenamiento
    public enum SortType {
        RECIENTE("Más reciente"),
        PRECIO_ASC("Precio ↑"),
        PRECIO_DESC("Precio ↓"),
        EXPIRA_PRONTO("Expira pronto");

        private final String displayName;
        SortType(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    public AuctionManager(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "auction.yml");
        loadData();

        // Tarea periódica: expirar listings cada minuto
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkExpiredListings, 20L * 60, 20L * 60);

        // Auto-guardado cada 5 minutos
        Bukkit.getScheduler().runTaskTimer(plugin, this::saveData, 20L * 300, 20L * 300);
    }

    /**
     * Lista un item en la Auction House.
     */
    public AuctionResult listItem(Player seller, ItemStack item, double price) {
        if (item == null || item.getType().isAir()) {
            return AuctionResult.error("§cNo puedes vender aire.");
        }
        if (price < AuctionItem.MIN_PRICE) {
            return AuctionResult.error("§cEl precio mínimo es §e$" + String.format("%,.0f", AuctionItem.MIN_PRICE));
        }
        if (price > AuctionItem.MAX_PRICE) {
            return AuctionResult.error("§cEl precio máximo es §e$" + String.format("%,.0f", AuctionItem.MAX_PRICE));
        }

        long activeCount = getActiveListings(seller.getUniqueId()).size();
        if (activeCount >= AuctionItem.MAX_LISTINGS) {
            return AuctionResult.error("§cHas alcanzado el límite de §e" + AuctionItem.MAX_LISTINGS + " §clistings activos.");
        }

        UUID id = UUID.randomUUID();
        long now = System.currentTimeMillis();
        AuctionItem auction = new AuctionItem(
            id, seller.getUniqueId(), seller.getName(),
            item.clone(), price, now, now + AuctionItem.DEFAULT_DURATION
        );

        listings.put(id, auction);
        saveData();

        return AuctionResult.success("§a§l✔ §fItem listado por §e" + auction.getFormattedPrice() + " §f(§7impuesto " + (int)(AuctionItem.TAX_RATE * 100) + "% al vender§f)");
    }

    /**
     * Compra un item de la Auction House.
     */
    public AuctionResult buyItem(Player buyer, UUID listingId) {
        AuctionItem auction = listings.get(listingId);
        if (auction == null || !auction.isActive()) {
            return AuctionResult.error("§cEste item ya no está disponible.");
        }
        if (auction.getSeller().equals(buyer.getUniqueId())) {
            return AuctionResult.error("§cNo puedes comprar tu propio item.");
        }

        Economy economy = plugin.getEconomy();
        if (economy == null) {
            return AuctionResult.error("§cError de economía. Contacta a un admin.");
        }

        double price = auction.getPrice();
        if (economy.getBalance(buyer) < price) {
            return AuctionResult.error("§cNo tienes suficiente dinero. §fNecesitas §e" + auction.getFormattedPrice());
        }

        // Verificar espacio en inventario
        if (buyer.getInventory().firstEmpty() == -1) {
            return AuctionResult.error("§cNo tienes espacio en tu inventario.");
        }

        // Cobrar al comprador
        economy.withdrawPlayer(buyer, price);

        // Pagar al vendedor (con impuesto)
        double tax = price * AuctionItem.TAX_RATE;
        double sellerAmount = price - tax;
        economy.depositPlayer(Bukkit.getOfflinePlayer(auction.getSeller()), sellerAmount);

        // Dar item al comprador
        buyer.getInventory().addItem(auction.getItem().clone());

        // Marcar como vendido
        auction.setSold(true);
        auction.setCollected(true);
        saveData();

        // Notificar al vendedor si está online
        Player seller = Bukkit.getPlayer(auction.getSeller());
        if (seller != null && seller.isOnline()) {
            seller.sendMessage(SmallCaps.convert("§6§l$ §a¡Tu item se ha vendido! §f" + buyer.getName() + " §7compró por §e" + auction.getFormattedPrice()));
            seller.sendMessage(SmallCaps.convert("§6§l$ §fRecibiste §a$" + String.format("%,.0f", sellerAmount) + " §7(§c-" + String.format("%,.0f", tax) + " impuesto§7)"));
            seller.playSound(seller.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }

        return AuctionResult.success("§a§l✔ §f¡Compra exitosa! §ePagaste " + auction.getFormattedPrice());
    }

    /**
     * Cancela un listing y devuelve el item.
     */
    public AuctionResult cancelListing(Player player, UUID listingId) {
        AuctionItem auction = listings.get(listingId);
        if (auction == null) {
            return AuctionResult.error("§cListing no encontrado.");
        }
        if (!auction.getSeller().equals(player.getUniqueId()) && !player.hasPermission("coreprotect.admin")) {
            return AuctionResult.error("§cNo puedes cancelar este listing.");
        }
        if (auction.isSold()) {
            return AuctionResult.error("§cEste item ya fue vendido.");
        }

        if (player.getInventory().firstEmpty() == -1) {
            return AuctionResult.error("§cNo tienes espacio en tu inventario.");
        }

        player.getInventory().addItem(auction.getItem().clone());
        auction.setExpired(true);
        auction.setCollected(true);
        saveData();

        return AuctionResult.success("§a§l✔ §fListing cancelado. Item devuelto.");
    }

    /**
     * Recoge un item expirado o vendido.
     */
    public AuctionResult collectItem(Player player, UUID listingId) {
        AuctionItem auction = listings.get(listingId);
        if (auction == null) {
            return AuctionResult.error("§cListing no encontrado.");
        }
        if (!auction.getSeller().equals(player.getUniqueId())) {
            return AuctionResult.error("§cNo es tu listing.");
        }
        if (auction.isCollected()) {
            return AuctionResult.error("§cYa recogiste este item.");
        }
        if (player.getInventory().firstEmpty() == -1) {
            return AuctionResult.error("§cNo tienes espacio en tu inventario.");
        }

        // Si expiró sin venderse, devolver item
        if (!auction.isSold()) {
            player.getInventory().addItem(auction.getItem().clone());
        }

        auction.setCollected(true);
        saveData();

        return AuctionResult.success("§a§l✔ §fItem recogido.");
    }

    // ==================== CONSULTAS ====================

    /**
     * Obtiene listings activos con filtros.
     */
    public List<AuctionItem> getFilteredListings(Category category, SortType sort, String search) {
        List<AuctionItem> result = listings.values().stream()
            .filter(AuctionItem::isActive)
            .collect(Collectors.toList());

        // Filtrar por categoría
        if (category != Category.TODOS) {
            result = result.stream()
                .filter(a -> getCategory(a.getItem()) == category)
                .collect(Collectors.toList());
        }

        // Filtrar por búsqueda
        if (search != null && !search.isEmpty()) {
            String lower = search.toLowerCase();
            result = result.stream()
                .filter(a -> {
                    String itemName = getItemDisplayName(a.getItem()).toLowerCase();
                    String typeName = a.getItem().getType().name().toLowerCase().replace("_", " ");
                    return itemName.contains(lower) || typeName.contains(lower) || a.getSellerName().toLowerCase().contains(lower);
                })
                .collect(Collectors.toList());
        }

        // Ordenar
        switch (sort) {
            case RECIENTE:
                result.sort(Comparator.comparingLong(AuctionItem::getListedAt).reversed());
                break;
            case PRECIO_ASC:
                result.sort(Comparator.comparingDouble(AuctionItem::getPrice));
                break;
            case PRECIO_DESC:
                result.sort(Comparator.comparingDouble(AuctionItem::getPrice).reversed());
                break;
            case EXPIRA_PRONTO:
                result.sort(Comparator.comparingLong(AuctionItem::getExpiresAt));
                break;
        }

        return result;
    }

    /**
     * Listings activos de un jugador.
     */
    public List<AuctionItem> getActiveListings(UUID player) {
        return listings.values().stream()
            .filter(a -> a.getSeller().equals(player) && a.isActive())
            .sorted(Comparator.comparingLong(AuctionItem::getListedAt).reversed())
            .collect(Collectors.toList());
    }

    /**
     * Items expirados/vendidos pendientes de recoger.
     */
    public List<AuctionItem> getExpiredListings(UUID player) {
        return listings.values().stream()
            .filter(a -> a.getSeller().equals(player) && !a.isCollected() && (a.hasExpired() || a.isSold()))
            .filter(a -> !a.isSold()) // Solo items no vendidos que expiraron
            .sorted(Comparator.comparingLong(AuctionItem::getExpiresAt).reversed())
            .collect(Collectors.toList());
    }

    /**
     * Estadísticas del jugador.
     */
    public int getTotalSold(UUID player) {
        return (int) listings.values().stream()
            .filter(a -> a.getSeller().equals(player) && a.isSold())
            .count();
    }

    public double getTotalEarnings(UUID player) {
        return listings.values().stream()
            .filter(a -> a.getSeller().equals(player) && a.isSold())
            .mapToDouble(a -> a.getPrice() * (1 - AuctionItem.TAX_RATE))
            .sum();
    }

    // ==================== CATEGORÍAS ====================

    public static Category getCategory(ItemStack item) {
        String name = item.getType().name();

        // Encantados primero
        if (!item.getEnchantments().isEmpty()) return Category.ENCANTADOS;

        // Armas
        if (name.contains("SWORD") || name.contains("BOW") || name.contains("CROSSBOW") ||
            name.contains("TRIDENT") || name.equals("MACE") || name.contains("ARROW")) {
            return Category.ARMAS;
        }

        // Armadura
        if (name.contains("HELMET") || name.contains("CHESTPLATE") || name.contains("LEGGINGS") ||
            name.contains("BOOTS") || name.contains("SHIELD") || name.contains("ELYTRA")) {
            return Category.ARMADURA;
        }

        // Herramientas
        if (name.contains("PICKAXE") || name.contains("AXE") || name.contains("SHOVEL") ||
            name.contains("HOE") || name.contains("SHEARS") || name.contains("FISHING_ROD") ||
            name.contains("FLINT_AND_STEEL") || name.contains("BRUSH")) {
            return Category.HERRAMIENTAS;
        }

        // Comida
        if (name.contains("APPLE") || name.contains("BREAD") || name.contains("BEEF") ||
            name.contains("PORK") || name.contains("CHICKEN") || name.contains("MUTTON") ||
            name.contains("RABBIT") || name.contains("COD") || name.contains("SALMON") ||
            name.contains("COOKIE") || name.contains("MELON") || name.contains("CARROT") ||
            name.contains("POTATO") || name.contains("STEW") || name.contains("PIE") ||
            name.contains("CAKE") || name.contains("CHORUS_FRUIT") || name.contains("HONEY") ||
            name.equals("GOLDEN_APPLE") || name.equals("ENCHANTED_GOLDEN_APPLE")) {
            return Category.COMIDA;
        }

        // Pociones
        if (name.contains("POTION") || name.contains("SPLASH") || name.contains("LINGERING")) {
            return Category.POCIONES;
        }

        // Bloques
        if (item.getType().isBlock()) return Category.BLOQUES;

        return Category.VARIOS;
    }

    public static String getItemDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        // Formatear nombre del tipo
        String name = item.getType().name().toLowerCase().replace("_", " ");
        StringBuilder sb = new StringBuilder();
        for (String word : name.split(" ")) {
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    // ==================== EXPIRACIÓN ====================

    private void checkExpiredListings() {
        for (AuctionItem auction : listings.values()) {
            if (!auction.isExpired() && !auction.isSold() && auction.hasExpired()) {
                auction.setExpired(true);

                Player seller = Bukkit.getPlayer(auction.getSeller());
                if (seller != null && seller.isOnline()) {
                    seller.sendMessage(SmallCaps.convert("§6§l⏰ §cTu listing de §f" + getItemDisplayName(auction.getItem()) + " §cha expirado."));
                    seller.sendMessage(SmallCaps.convert("§7Usa §e/ah §7→ §fMis Subastas §7para recoger el item."));
                }
            }
        }
    }

    // ==================== PERSISTENCIA ====================

    public void saveData() {
        FileConfiguration config = new YamlConfiguration();

        for (Map.Entry<UUID, AuctionItem> entry : listings.entrySet()) {
            AuctionItem a = entry.getValue();
            String path = "listings." + a.getId().toString();

            config.set(path + ".seller", a.getSeller().toString());
            config.set(path + ".sellerName", a.getSellerName());
            config.set(path + ".item", a.getItem());
            config.set(path + ".price", a.getPrice());
            config.set(path + ".listedAt", a.getListedAt());
            config.set(path + ".expiresAt", a.getExpiresAt());
            config.set(path + ".sold", a.isSold());
            config.set(path + ".expired", a.isExpired());
            config.set(path + ".collected", a.isCollected());
        }

        try {
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("[AuctionHouse] Error al guardar datos: " + e.getMessage());
        }
    }

    public void loadData() {
        if (!dataFile.exists()) return;

        FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection section = config.getConfigurationSection("listings");
        if (section == null) return;

        listings.clear();
        long purgeThreshold = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L); // Purgar >7 días

        for (String key : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                UUID seller = UUID.fromString(section.getString(key + ".seller"));
                String sellerName = section.getString(key + ".sellerName", "???");
                ItemStack item = section.getItemStack(key + ".item");
                double price = section.getDouble(key + ".price");
                long listedAt = section.getLong(key + ".listedAt");
                long expiresAt = section.getLong(key + ".expiresAt");
                boolean sold = section.getBoolean(key + ".sold");
                boolean expired = section.getBoolean(key + ".expired");
                boolean collected = section.getBoolean(key + ".collected");

                // No cargar listings viejos ya recogidos
                if (collected && listedAt < purgeThreshold) continue;

                if (item == null) continue;

                AuctionItem auction = new AuctionItem(id, seller, sellerName, item, price, listedAt, expiresAt);
                auction.setSold(sold);
                auction.setExpired(expired);
                auction.setCollected(collected);
                listings.put(id, auction);
            } catch (Exception e) {
                plugin.getLogger().warning("[AuctionHouse] Error cargando listing " + key + ": " + e.getMessage());
            }
        }

        plugin.getLogger().info("[AuctionHouse] " + listings.size() + " listings cargados.");
    }

    /**
     * Limpieza al apagar.
     */
    public void shutdown() {
        saveData();
    }

    // ==================== RESULTADO ====================

    public static class AuctionResult {
        private final boolean success;
        private final String message;

        private AuctionResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static AuctionResult success(String message) { return new AuctionResult(true, message); }
        public static AuctionResult error(String message) { return new AuctionResult(false, message); }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }
}
