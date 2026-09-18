package com.moonlight.coreprotect.bounty;

import java.util.UUID;

/**
 * Representa una bounty activa sobre un jugador.
 */
public class Bounty {

    public static final double MIN_BOUNTY = 500;
    public static final double MAX_BOUNTY = 10_000_000;
    public static final int MAX_ACTIVE_BOUNTIES = 3; // por jugador que pone bounties
    public static final long DEFAULT_DURATION = Long.MAX_VALUE; // No expira nunca

    private final UUID id;
    private final UUID placer;       // Quien puso la bounty
    private final String placerName;
    private final UUID target;       // Objetivo de la bounty
    private final String targetName;
    private final double amount;
    private final long placedAt;
    private final long expiresAt;

    private boolean claimed;         // Fue cobrada (alguien mató al target)
    private UUID claimedBy;          // Quien la cobró
    private String claimedByName;
    private long claimedAt;
    private boolean expired;

    public Bounty(UUID id, UUID placer, String placerName, UUID target, String targetName,
                  double amount, long placedAt, long expiresAt) {
        this.id = id;
        this.placer = placer;
        this.placerName = placerName;
        this.target = target;
        this.targetName = targetName;
        this.amount = amount;
        this.placedAt = placedAt;
        this.expiresAt = expiresAt;
        this.claimed = false;
        this.expired = false;
    }

    // === Estado ===

    public boolean isActive() {
        return !claimed && !expired;
    }

    public boolean hasExpired() {
        return false; // Las bounties no expiran nunca
    }

    public String getFormattedAmount() {
        return "$" + String.format("%,.0f", amount);
    }

    public String getTimeRemaining() {
        if (claimed) return "§a§lCOBRADA";
        if (expired) return "§c§lCANCELADA";
        return "§a§lPERMANENTE";
    }

    // === Getters y Setters ===

    public UUID getId() { return id; }
    public UUID getPlacer() { return placer; }
    public String getPlacerName() { return placerName; }
    public UUID getTarget() { return target; }
    public String getTargetName() { return targetName; }
    public double getAmount() { return amount; }
    public long getPlacedAt() { return placedAt; }
    public long getExpiresAt() { return expiresAt; }

    public boolean isClaimed() { return claimed; }
    public void setClaimed(boolean claimed) { this.claimed = claimed; }
    public UUID getClaimedBy() { return claimedBy; }
    public void setClaimedBy(UUID claimedBy) { this.claimedBy = claimedBy; }
    public String getClaimedByName() { return claimedByName; }
    public void setClaimedByName(String claimedByName) { this.claimedByName = claimedByName; }
    public long getClaimedAt() { return claimedAt; }
    public void setClaimedAt(long claimedAt) { this.claimedAt = claimedAt; }
    public boolean isExpired() { return expired; }
    public void setExpired(boolean expired) { this.expired = expired; }
}
