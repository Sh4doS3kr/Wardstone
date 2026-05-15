package com.moonlight.coreprotect.protection;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.core.ProtectedRegion;
import com.moonlight.coreprotect.gui.CoreSettingsGUI;
import com.moonlight.coreprotect.gui.GUIListener;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Handles chat input for core settings (rename, welcome message, transfer ownership).
 */
public class CoreSettingsChatListener implements Listener {

    private final CoreProtectPlugin plugin;

    public CoreSettingsChatListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String inputType = GUIListener.getPendingInput(player.getUniqueId());
        if (inputType == null) return;

        event.setCancelled(true);
        String message = event.getMessage().trim();

        if (message.equalsIgnoreCase("cancelar") || message.equalsIgnoreCase("cancel")) {
            GUIListener.removePendingInput(player.getUniqueId());
            player.sendMessage(SmallCaps.convert("§c§l✦ §7Acción cancelada."));
            return;
        }

        UUID regionId = GUIListener.getPlayerRegion(player.getUniqueId());
        if (regionId == null) {
            GUIListener.removePendingInput(player.getUniqueId());
            player.sendMessage(SmallCaps.convert("§c§l✦ §cError: no se encontró tu territorio."));
            return;
        }

        ProtectedRegion region = plugin.getProtectionManager().getRegion(regionId);
        if (region == null) {
            GUIListener.removePendingInput(player.getUniqueId());
            player.sendMessage(SmallCaps.convert("§c§l✦ §cError: territorio no encontrado."));
            return;
        }

        if (!region.getOwner().equals(player.getUniqueId())) {
            GUIListener.removePendingInput(player.getUniqueId());
            player.sendMessage(SmallCaps.convert("§c§l✦ §cNo eres el dueño de este territorio."));
            return;
        }

        switch (inputType) {
            case "rename":
                handleRename(player, region, message);
                break;
            case "welcome":
                handleWelcome(player, region, message);
                break;
            case "transfer":
                handleTransfer(player, region, message);
                break;
            case "ban":
                handleBan(player, region, message);
                break;
            case "prestige":
                handlePrestigeConfirm(player, region, message);
                break;
        }

        GUIListener.removePendingInput(player.getUniqueId());
    }

    private void handleRename(Player player, ProtectedRegion region, String name) {
        if (name.length() > 32) {
            player.sendMessage(SmallCaps.convert("§c§l✦ §cEl nombre no puede tener más de 32 caracteres."));
            return;
        }
        if (name.length() < 2) {
            player.sendMessage(SmallCaps.convert("§c§l✦ §cEl nombre debe tener al menos 2 caracteres."));
            return;
        }

        // Strip color codes for safety
        String clean = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', name));
        region.setCoreName(clean);
        plugin.getDataManager().saveData();
        player.sendMessage(SmallCaps.convert("§a§l✦ §aTerritorio renombrado a: §f" + clean));

        // Reopen settings GUI on main thread
        Bukkit.getScheduler().runTask(plugin, () -> new CoreSettingsGUI(plugin).open(player, region));
    }

    private void handleWelcome(Player player, ProtectedRegion region, String message) {
        if (message.length() > 64) {
            player.sendMessage(SmallCaps.convert("§c§l✦ §cEl mensaje no puede tener más de 64 caracteres."));
            return;
        }

        region.setWelcomeMessage(message);
        plugin.getDataManager().saveData();
        player.sendMessage(SmallCaps.convert("§a§l✦ §aMensaje de bienvenida establecido: §f\"" + message + "\""));

        Bukkit.getScheduler().runTask(plugin, () -> new CoreSettingsGUI(plugin).open(player, region));
    }

    private void handleTransfer(Player player, ProtectedRegion region, String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            player.sendMessage(SmallCaps.convert("§c§l✦ §cJugador no encontrado o no está online."));
            return;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(SmallCaps.convert("§c§l✦ §cNo puedes transferirte a ti mismo."));
            return;
        }

        if (!region.isMember(target.getUniqueId())) {
            player.sendMessage(SmallCaps.convert("§c§l✦ §cEl jugador debe ser miembro de tu territorio."));
            return;
        }

        // Perform transfer
        UUID oldOwner = region.getOwner();
        region.setOwner(target.getUniqueId());
        region.removeMember(target.getUniqueId());
        region.addMember(oldOwner);
        plugin.getDataManager().saveData();

        player.sendMessage(SmallCaps.convert("§a§l✦ §aTerritorio transferido a §f" + target.getName() + "§a."));
        target.sendMessage(SmallCaps.convert("§a§l✦ §f" + player.getName() + " §ate ha transferido su territorio."));
    }

    @SuppressWarnings("deprecation")
    private void handleBan(Player player, ProtectedRegion region, String targetName) {
        org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (target == null || target.getName() == null) {
            player.sendMessage(SmallCaps.convert("§c§l✦ §cJugador no encontrado."));
            return;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(SmallCaps.convert("§c§l✦ §cNo puedes banearte a ti mismo."));
            return;
        }

        if (region.isBanned(target.getUniqueId())) {
            // Unban
            region.unbanPlayer(target.getUniqueId());
            plugin.getDataManager().saveData();
            player.sendMessage(SmallCaps.convert("§a§l✦ §f" + target.getName() + " §aha sido desbaneado de tu territorio."));
        } else {
            // Ban
            region.banPlayer(target.getUniqueId());
            plugin.getDataManager().saveData();
            player.sendMessage(SmallCaps.convert("§c§l✦ §f" + target.getName() + " §cha sido baneado de tu territorio."));
            // If the target is online and inside the zone, kick them out
            Player onlineTarget = target.getPlayer();
            if (onlineTarget != null && onlineTarget.isOnline()) {
                if (region.contains(onlineTarget.getLocation())) {
                    org.bukkit.Location coreLoc = region.getCoreLocation();
                    if (coreLoc != null) {
                        // Teleport them out (above the core block + some distance)
                        onlineTarget.teleport(coreLoc.add(0, 2, region.getSize() / 2 + 2));
                    }
                    onlineTarget.sendMessage(SmallCaps.convert("§c§l✦ §cHas sido baneado del territorio de §f" + player.getName() + "§c."));
                }
            }
        }

        Bukkit.getScheduler().runTask(plugin, () -> new CoreSettingsGUI(plugin).open(player, region));
    }

    private void handlePrestigeConfirm(Player player, ProtectedRegion region, String message) {
        String input = message.trim().toLowerCase();
        if (input.equals("confirmar")) {
            if (plugin.getCorePrestigeManager() != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getCorePrestigeManager().performPrestige(player, region);
                });
            }
        } else {
            player.sendMessage(SmallCaps.convert("§c§l✦ §7Prestigio §ccancelado§7. Tu núcleo no ha sido modificado."));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        GUIListener.removePendingInput(event.getPlayer().getUniqueId());
    }
}
