package com.moonlight.coreprotect.commands;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.core.CoreLevel;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * /claimcore - Permite a jugadores con rango VIP (luna, nova, eclipse, moonlord)
 * reclamar su núcleo de rango correspondiente de forma gratuita (una vez por rango).
 */
public class ClaimCoreCommand implements CommandExecutor {

    private final CoreProtectPlugin plugin;
    private final File claimedFile;
    // Map<UUID, Set<String>> - cada jugador tiene un set de rangos ya reclamados
    private final Map<UUID, Set<String>> claimedCores = new HashMap<>();

    // Mapeo rango -> nivel de core
    private static final Map<String, Integer> RANK_TO_LEVEL = new LinkedHashMap<>();
    static {
        RANK_TO_LEVEL.put("luna", 21);
        RANK_TO_LEVEL.put("nova", 22);
        RANK_TO_LEVEL.put("eclipse", 23);
        RANK_TO_LEVEL.put("moonlord", 24);
    }

    public ClaimCoreCommand(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        this.claimedFile = new File(plugin.getDataFolder(), "claimed_cores.yml");
        loadClaimed();
    }

    private void loadClaimed() {
        if (!claimedFile.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(claimedFile);
        for (String uuidStr : cfg.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                List<String> ranks = cfg.getStringList(uuidStr);
                claimedCores.put(uuid, new HashSet<>(ranks));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void saveClaimed() {
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Set<String>> entry : claimedCores.entrySet()) {
            cfg.set(entry.getKey().toString(), new ArrayList<>(entry.getValue()));
        }
        try {
            cfg.save(claimedFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Error guardando claimed_cores.yml: " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(SmallCaps.convert(ChatColor.RED + "Solo los jugadores pueden usar este comando."));
            return true;
        }

        Player player = (Player) sender;

        // Buscar qué rangos VIP tiene el jugador
        // Comprobamos múltiples permisos: wardstone.vip.X, group.X, essentials.kits.X
        // LuckPerms asigna automáticamente "group.<nombre>" a los miembros del grupo
        List<String> availableRanks = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : RANK_TO_LEVEL.entrySet()) {
            String rank = entry.getKey();
            if (player.hasPermission("wardstone.vip." + rank)
                    || player.hasPermission("group." + rank)
                    || player.hasPermission("essentials.kits." + rank)) {
                availableRanks.add(rank);
            }
        }

        if (availableRanks.isEmpty()) {
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo tienes ningún rango VIP."));
            player.sendMessage(SmallCaps.convert("§7Consigue tu rango en: §emoonlightmc.tebex.io"));
            player.sendMessage("");
            return true;
        }

        // Filtrar los que ya reclamó
        Set<String> alreadyClaimed = claimedCores.getOrDefault(player.getUniqueId(), Collections.emptySet());
        List<String> claimableRanks = new ArrayList<>();
        for (String rank : availableRanks) {
            if (!alreadyClaimed.contains(rank)) {
                claimableRanks.add(rank);
            }
        }

        if (claimableRanks.isEmpty()) {
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("§c§l✖ §cYa has reclamado todos tus núcleos de rango."));
            player.sendMessage(SmallCaps.convert("§7Rangos reclamados: §e" + String.join(", ", alreadyClaimed)));
            player.sendMessage("");
            return true;
        }

        // Verificar espacio en inventario
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cTu inventario está lleno. Libera espacio para reclamar tu núcleo."));
            return true;
        }

        // Dar todos los cores reclamables
        int given = 0;
        for (String rank : claimableRanks) {
            // Verificar espacio para cada core
            if (player.getInventory().firstEmpty() == -1) {
                player.sendMessage(SmallCaps.convert("§e§l⚠ §7Inventario lleno. Algunos núcleos no se pudieron entregar."));
                break;
            }

            int level = RANK_TO_LEVEL.get(rank);
            CoreLevel coreLevel = CoreLevel.fromConfig(plugin.getConfig(), level);
            if (coreLevel == null) continue;

            ItemStack coreItem = coreLevel.toItemStack();
            player.getInventory().addItem(coreItem);

            // Marcar como reclamado
            claimedCores.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>()).add(rank);
            given++;

            player.sendMessage(SmallCaps.convert("§a§l✔ §fNúcleo " + ChatColor.translateAlternateColorCodes('&', coreLevel.getName())
                    + " §freclamado."));
        }

        if (given > 0) {
            saveClaimed();
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("§d§l★ §f¡Núcleo" + (given > 1 ? "s" : "") + " de rango reclamado" + (given > 1 ? "s" : "") + "!"));
            player.sendMessage(SmallCaps.convert("§7Colócalo en el suelo para activar tu protección VIP."));
            player.sendMessage("");

            // Broadcast
            String rankNames = claimableRanks.stream()
                    .limit(given)
                    .map(r -> {
                        CoreLevel cl = CoreLevel.fromConfig(plugin.getConfig(), RANK_TO_LEVEL.get(r));
                        return cl != null ? ChatColor.translateAlternateColorCodes('&', cl.getVipGradientName()) : r;
                    })
                    .collect(Collectors.joining("§7, "));

            for (Player online : Bukkit.getOnlinePlayers()) {
                online.sendMessage(SmallCaps.convert("§d§l★ §f" + player.getName() + " §7ha reclamado su núcleo §f" + rankNames + "§7."));
            }
        }

        return true;
    }
}
