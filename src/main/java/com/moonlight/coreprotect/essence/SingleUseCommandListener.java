package com.moonlight.coreprotect.essence;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.persistence.PersistentDataType;

public class SingleUseCommandListener implements Listener {
    private final CoreProtectPlugin plugin;
    private final NamespacedKey healKey;
    private final NamespacedKey feedKey;

    public SingleUseCommandListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        this.healKey = new NamespacedKey(plugin, "single_use_heal");
        this.feedKey = new NamespacedKey(plugin, "single_use_feed");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String[] args = event.getMessage().toLowerCase().split(" ");
        String cmd = args[0];
        Player player = event.getPlayer();

        if (cmd.equals("/heal")) {
            if (player.hasPermission("coreprotect.admin.heal")) return;

            Integer charges = player.getPersistentDataContainer().get(healKey, PersistentDataType.INTEGER);
            if (charges == null || charges <= 0) {
                player.sendMessage(SmallCaps.convert("§c§l✖ §cNo tienes permiso. Compra un uso de /heal en /esencias."));
                event.setCancelled(true);
                return;
            }

            event.setCancelled(true);
            
            int remaining = charges - 1;
            player.getPersistentDataContainer().set(healKey, PersistentDataType.INTEGER, remaining);

            player.setHealth(player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue());
            player.setFireTicks(0);
            
            player.sendMessage(SmallCaps.convert("§3§l✦ §c¡Vida restaurada! §7(Usos restantes: " + remaining + ")"));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
        }
        else if (cmd.equals("/feed")) {
            if (player.hasPermission("coreprotect.admin.feed")) return;

            Integer charges = player.getPersistentDataContainer().get(feedKey, PersistentDataType.INTEGER);
            if (charges == null || charges <= 0) {
                player.sendMessage(SmallCaps.convert("§c§l✖ §cNo tienes permiso. Compra un uso de /feed en /esencias."));
                event.setCancelled(true);
                return;
            }

            event.setCancelled(true);
            
            int remaining = charges - 1;
            player.getPersistentDataContainer().set(feedKey, PersistentDataType.INTEGER, remaining);

            player.setFoodLevel(20);
            player.setSaturation(20f);
            
            player.sendMessage(SmallCaps.convert("§3§l✦ §6¡Hambre saciada! §7(Usos restantes: " + remaining + ")"));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
        }
    }
}
