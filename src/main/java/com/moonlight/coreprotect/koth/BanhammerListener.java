package com.moonlight.coreprotect.koth;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Listener para el Banhammer (click derecho = ban 10s) y bloqueo de login temp-ban.
 */
public class BanhammerListener implements Listener {

    private final CoreProtectPlugin plugin;
    private final NamespacedKey banhammerKey;

    public BanhammerListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        this.banhammerKey = new NamespacedKey(plugin, "banhammer");
    }

    private boolean isBanhammer(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(banhammerKey, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!isBanhammer(hand)) return;

        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof Player target)) return;

        event.setCancelled(true);

        KothCommand cmd = plugin.getKothCommand();
        if (cmd != null) {
            cmd.handleBanhammerHit(player, target);
            // Non-OP players lose the banhammer after one use
            if (!player.isOp()) {
                player.getInventory().setItemInMainHand(null);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player target)) return;

        ItemStack hand = attacker.getInventory().getItemInMainHand();
        if (!isBanhammer(hand)) return;

        event.setCancelled(true);

        KothCommand cmd = plugin.getKothCommand();
        if (cmd != null) {
            cmd.handleBanhammerHit(attacker, target);
            // Non-OP players lose the banhammer after one use
            if (!attacker.isOp()) {
                attacker.getInventory().setItemInMainHand(null);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(PlayerLoginEvent event) {
        if (KothCommand.isTempBanned(event.getPlayer().getUniqueId())) {
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED,
                    "§c§l☠ §4§lBANHAMMER §c§l☠\n\n§fTodavía estás baneado.\n§7Espera unos segundos e intenta de nuevo.");
        }
    }
}
