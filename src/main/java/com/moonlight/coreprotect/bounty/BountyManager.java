package com.moonlight.coreprotect.bounty;

import com.moonlight.coreprotect.CoreProtectPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Gestiona todas las bounties: crear, cobrar, expirar, persistir.
 */
public class BountyManager {

    private final CoreProtectPlugin plugin;
    private final File dataFile;
    private final Map<UUID, Bounty> bounties = new ConcurrentHashMap<>();

    public enum SortType {
        RECIENTE("Más reciente"),
        MAYOR_PRECIO("Mayor precio"),
        MENOR_PRECIO("Menor precio");

        private final String displayName;
        SortType(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    public BountyManager(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "bounties.yml");
        loadData();

        // Auto-guardado cada 5 minutos
        Bukkit.getScheduler().runTaskTimer(plugin, this::saveData, 20L * 300, 20L * 300);
    }

    // ==================== CREAR BOUNTY ====================

    public BountyResult placeBounty(Player placer, Player target, double amount) {
        if (placer.getUniqueId().equals(target.getUniqueId())) {
            return BountyResult.error("§cNo puedes ponerte bounty a ti mismo.");
        }

        if (amount < Bounty.MIN_BOUNTY) {
            return BountyResult.error("§cLa bounty mínima es §e$" + String.format("%,.0f", Bounty.MIN_BOUNTY));
        }
        if (amount > Bounty.MAX_BOUNTY) {
            return BountyResult.error("§cLa bounty máxima es §e$" + String.format("%,.0f", Bounty.MAX_BOUNTY));
        }

        // Verificar límite de bounties activas por jugador
        long activeCount = getActiveBountiesPlacedBy(placer.getUniqueId()).size();
        if (activeCount >= Bounty.MAX_ACTIVE_BOUNTIES) {
            return BountyResult.error("§cYa tienes §e" + Bounty.MAX_ACTIVE_BOUNTIES + " §cbounties activas. Espera a que expiren o sean cobradas.");
        }

        // Verificar que no tenga ya una bounty sobre este jugador
        for (Bounty b : bounties.values()) {
            if (b.isActive() && b.getPlacer().equals(placer.getUniqueId()) && b.getTarget().equals(target.getUniqueId())) {
                return BountyResult.error("§cYa tienes una bounty activa sobre §e" + target.getName() + "§c.");
            }
        }

        // Verificar dinero
        Economy economy = plugin.getEconomy();
        if (economy == null) {
            return BountyResult.error("§cError de economía. Contacta a un admin.");
        }
        if (economy.getBalance(placer) < amount) {
            return BountyResult.error("§cNo tienes suficiente dinero. §fNecesitas §e$" + String.format("%,.0f", amount));
        }

        // Cobrar al que pone la bounty
        economy.withdrawPlayer(placer, amount);

        // Crear bounty
        UUID id = UUID.randomUUID();
        long now = System.currentTimeMillis();
        Bounty bounty = new Bounty(id, placer.getUniqueId(), placer.getName(),
                target.getUniqueId(), target.getName(), amount, now, now + Bounty.DEFAULT_DURATION);
        bounties.put(id, bounty);
        saveData();

        // Anunciar al servidor
        String msg = "§c§l☠ §e" + placer.getName() + " §fha puesto una bounty de §a" + bounty.getFormattedAmount()
                + " §fsobre §c" + target.getName() + "§f. §7¡Mátalo para cobrar!";
        Bukkit.broadcastMessage(msg);

        // Notificar al target si está online
        if (target.isOnline()) {
            target.sendMessage("");
            target.sendMessage(SmallCaps.convert("§4§l⚠ §c§l¡BOUNTY SOBRE TI! §4§l⚠"));
            target.sendMessage(SmallCaps.convert("§f" + placer.getName() + " §7ha puesto §a" + bounty.getFormattedAmount() + " §7por tu cabeza."));
            target.sendMessage(SmallCaps.convert("§7¡Cuidado! Cualquier jugador puede matarte para cobrarla."));
            target.sendMessage("");
            target.playSound(target.getLocation(), org.bukkit.Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.5f);
        }

        return BountyResult.success("§a§l✔ §fBounty de §a" + bounty.getFormattedAmount() + " §fcolocada sobre §c" + target.getName() + "§f.");
    }

    // ==================== COBRAR BOUNTY ====================

    /**
     * Llamar cuando un jugador mata a otro. Retorna la lista de bounties cobradas.
     */
    public List<Bounty> claimBounties(Player killer, Player victim) {
        List<Bounty> claimed = new ArrayList<>();
        Economy economy = plugin.getEconomy();
        if (economy == null) return claimed;

        for (Bounty bounty : bounties.values()) {
            if (bounty.isActive() && bounty.getTarget().equals(victim.getUniqueId())) {
                // No dejar que el que puso la bounty la cobre él mismo
                if (bounty.getPlacer().equals(killer.getUniqueId())) continue;

                bounty.setClaimed(true);
                bounty.setClaimedBy(killer.getUniqueId());
                bounty.setClaimedByName(killer.getName());
                bounty.setClaimedAt(System.currentTimeMillis());

                // Pagar al asesino
                economy.depositPlayer(killer, bounty.getAmount());
                claimed.add(bounty);
            }
        }

        if (!claimed.isEmpty()) {
            saveData();
        }

        return claimed;
    }

    // ==================== CANCELAR BOUNTY ====================

    public BountyResult cancelBounty(Player player, UUID bountyId) {
        Bounty bounty = bounties.get(bountyId);
        if (bounty == null) {
            return BountyResult.error("§cBounty no encontrada.");
        }
        if (!bounty.getPlacer().equals(player.getUniqueId()) && !player.hasPermission("wardstone.admin")) {
            return BountyResult.error("§cNo puedes cancelar esta bounty.");
        }
        if (!bounty.isActive()) {
            return BountyResult.error("§cEsta bounty ya no está activa.");
        }

        // Devolver solo el 50% (penalización por cancelar)
        Economy economy = plugin.getEconomy();
        if (economy != null) {
            double refund = bounty.getAmount() * 0.5;
            economy.depositPlayer(Bukkit.getOfflinePlayer(bounty.getPlacer()), refund);
            bounty.setExpired(true);
            saveData();
            return BountyResult.success("§a§l✔ §fBounty cancelada. §7Se te devolvió §e$" + String.format("%,.0f", refund) + " §7(50% penalización).");
        }

        bounty.setExpired(true);
        saveData();
        return BountyResult.success("§a§l✔ §fBounty cancelada.");
    }

    // ==================== CONSULTAS ====================

    public List<Bounty> getActiveBounties(SortType sort) {
        List<Bounty> result = bounties.values().stream()
                .filter(Bounty::isActive)
                .collect(Collectors.toList());

        switch (sort) {
            case RECIENTE:
                result.sort(Comparator.comparingLong(Bounty::getPlacedAt).reversed());
                break;
            case MAYOR_PRECIO:
                result.sort(Comparator.comparingDouble(Bounty::getAmount).reversed());
                break;
            case MENOR_PRECIO:
                result.sort(Comparator.comparingDouble(Bounty::getAmount));
                break;
        }

        return result;
    }

    public List<Bounty> getActiveBountiesPlacedBy(UUID playerId) {
        return bounties.values().stream()
                .filter(b -> b.isActive() && b.getPlacer().equals(playerId))
                .sorted(Comparator.comparingLong(Bounty::getPlacedAt).reversed())
                .collect(Collectors.toList());
    }

    public List<Bounty> getBountiesOnPlayer(UUID targetId) {
        return bounties.values().stream()
                .filter(b -> b.isActive() && b.getTarget().equals(targetId))
                .sorted(Comparator.comparingDouble(Bounty::getAmount).reversed())
                .collect(Collectors.toList());
    }

    public double getTotalBountyOnPlayer(UUID targetId) {
        return bounties.values().stream()
                .filter(b -> b.isActive() && b.getTarget().equals(targetId))
                .mapToDouble(Bounty::getAmount)
                .sum();
    }

    public int getTotalClaimedBy(UUID playerId) {
        return (int) bounties.values().stream()
                .filter(b -> b.isClaimed() && playerId.equals(b.getClaimedBy()))
                .count();
    }

    public double getTotalEarnedBy(UUID playerId) {
        return bounties.values().stream()
                .filter(b -> b.isClaimed() && playerId.equals(b.getClaimedBy()))
                .mapToDouble(Bounty::getAmount)
                .sum();
    }

    // ==================== TOP HUNTERS ====================

    public List<Map.Entry<String, Double>> getTopHunters(int limit) {
        Map<String, Double> earnings = new HashMap<>();
        for (Bounty b : bounties.values()) {
            if (b.isClaimed() && b.getClaimedByName() != null) {
                earnings.merge(b.getClaimedByName(), b.getAmount(), Double::sum);
            }
        }
        return earnings.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    // ==================== TOP WANTED ====================

    public List<Map.Entry<String, Double>> getTopWanted(int limit) {
        Map<String, Double> wanted = new HashMap<>();
        for (Bounty b : bounties.values()) {
            if (b.isActive()) {
                wanted.merge(b.getTargetName(), b.getAmount(), Double::sum);
            }
        }
        return wanted.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    // ==================== PERSISTENCIA ====================

    public void saveData() {
        FileConfiguration config = new YamlConfiguration();

        for (Map.Entry<UUID, Bounty> entry : bounties.entrySet()) {
            Bounty b = entry.getValue();
            String path = "bounties." + b.getId().toString();

            config.set(path + ".placer", b.getPlacer().toString());
            config.set(path + ".placerName", b.getPlacerName());
            config.set(path + ".target", b.getTarget().toString());
            config.set(path + ".targetName", b.getTargetName());
            config.set(path + ".amount", b.getAmount());
            config.set(path + ".placedAt", b.getPlacedAt());
            config.set(path + ".expiresAt", b.getExpiresAt());
            config.set(path + ".claimed", b.isClaimed());
            config.set(path + ".expired", b.isExpired());
            if (b.getClaimedBy() != null) {
                config.set(path + ".claimedBy", b.getClaimedBy().toString());
                config.set(path + ".claimedByName", b.getClaimedByName());
                config.set(path + ".claimedAt", b.getClaimedAt());
            }
        }

        try {
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("[Bounty] Error al guardar datos: " + e.getMessage());
        }
    }

    public void loadData() {
        if (!dataFile.exists()) return;

        FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection section = config.getConfigurationSection("bounties");
        if (section == null) return;

        bounties.clear();
        long purgeThreshold = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L);

        for (String key : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                UUID placer = UUID.fromString(section.getString(key + ".placer"));
                String placerName = section.getString(key + ".placerName", "???");
                UUID target = UUID.fromString(section.getString(key + ".target"));
                String targetName = section.getString(key + ".targetName", "???");
                double amount = section.getDouble(key + ".amount");
                long placedAt = section.getLong(key + ".placedAt");
                long expiresAt = section.getLong(key + ".expiresAt");
                boolean claimed = section.getBoolean(key + ".claimed");
                boolean expired = section.getBoolean(key + ".expired");

                // No cargar bounties viejas ya resueltas
                if ((claimed || expired) && placedAt < purgeThreshold) continue;

                Bounty bounty = new Bounty(id, placer, placerName, target, targetName, amount, placedAt, expiresAt);
                bounty.setClaimed(claimed);
                bounty.setExpired(expired);

                if (section.contains(key + ".claimedBy")) {
                    bounty.setClaimedBy(UUID.fromString(section.getString(key + ".claimedBy")));
                    bounty.setClaimedByName(section.getString(key + ".claimedByName"));
                    bounty.setClaimedAt(section.getLong(key + ".claimedAt"));
                }

                bounties.put(id, bounty);
            } catch (Exception e) {
                plugin.getLogger().warning("[Bounty] Error cargando bounty " + key + ": " + e.getMessage());
            }
        }

        plugin.getLogger().info("[Bounty] " + bounties.size() + " bounties cargadas.");
    }

    public void shutdown() {
        saveData();
    }

    // ==================== RESULTADO ====================

    public static class BountyResult {
        private final boolean success;
        private final String message;

        private BountyResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static BountyResult success(String message) { return new BountyResult(true, message); }
        public static BountyResult error(String message) { return new BountyResult(false, message); }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }
}
