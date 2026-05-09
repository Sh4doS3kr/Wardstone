package com.moonlight.coreprotect.auction;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Representa un item listado en la Auction House.
 */
public class AuctionItem {

    private final UUID id;
    private final UUID seller;
    private final String sellerName;
    private final ItemStack item;
    private final double price;
    private final long listedAt;
    private final long expiresAt;
    private boolean sold;
    private boolean expired;
    private boolean collected;

    // Duración por defecto: 48 horas
    public static final long DEFAULT_DURATION = 48 * 60 * 60 * 1000L;
    // Tasa de impuesto al vender (5%)
    public static final double TAX_RATE = 0.05;
    // Máximo de listings activos por jugador
    public static final int MAX_LISTINGS = 28;
    // Precio mínimo
    public static final double MIN_PRICE = 10;
    // Precio máximo
    public static final double MAX_PRICE = 100_000_000;

    public AuctionItem(UUID id, UUID seller, String sellerName, ItemStack item, double price, long listedAt, long expiresAt) {
        this.id = id;
        this.seller = seller;
        this.sellerName = sellerName;
        this.item = item;
        this.price = price;
        this.listedAt = listedAt;
        this.expiresAt = expiresAt;
        this.sold = false;
        this.expired = false;
        this.collected = false;
    }

    public UUID getId() { return id; }
    public UUID getSeller() { return seller; }
    public String getSellerName() { return sellerName; }
    public ItemStack getItem() { return item; }
    public double getPrice() { return price; }
    public long getListedAt() { return listedAt; }
    public long getExpiresAt() { return expiresAt; }
    public boolean isSold() { return sold; }
    public boolean isExpired() { return expired; }
    public boolean isCollected() { return collected; }

    public void setSold(boolean sold) { this.sold = sold; }
    public void setExpired(boolean expired) { this.expired = expired; }
    public void setCollected(boolean collected) { this.collected = collected; }

    /**
     * Comprueba si el listing ha expirado.
     */
    public boolean hasExpired() {
        return System.currentTimeMillis() >= expiresAt;
    }

    /**
     * Devuelve si el listing está activo (no vendido, no expirado, no recogido).
     */
    public boolean isActive() {
        return !sold && !expired && !hasExpired();
    }

    /**
     * Devuelve el tiempo restante formateado.
     */
    public String getTimeRemaining() {
        long remaining = expiresAt - System.currentTimeMillis();
        if (remaining <= 0) return "§cExpirado";

        long hours = remaining / (60 * 60 * 1000);
        long minutes = (remaining % (60 * 60 * 1000)) / (60 * 1000);

        if (hours > 0) {
            return "§e" + hours + "h " + minutes + "m";
        }
        return "§e" + minutes + "m";
    }

    /**
     * Precio formateado.
     */
    public String getFormattedPrice() {
        if (price >= 1_000_000) {
            return String.format("$%.1fM", price / 1_000_000.0);
        } else if (price >= 1_000) {
            return String.format("$%.1fK", price / 1_000.0);
        }
        return String.format("$%,.0f", price);
    }
}
