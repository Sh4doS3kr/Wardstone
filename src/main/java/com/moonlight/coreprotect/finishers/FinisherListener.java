package com.moonlight.coreprotect.finishers;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class FinisherListener implements Listener {

    private final CoreProtectPlugin plugin;
    private final FinisherEffects effects;
    private final Set<UUID> beingFinished = new HashSet<>();

    public FinisherListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        this.effects = new FinisherEffects(plugin);
    }

    public boolean isBeingFinished(UUID uuid) {
        return beingFinished.contains(uuid);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player victim = (Player) event.getEntity();
        if (beingFinished.contains(victim.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerAttacked(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player victim = (Player) event.getEntity();

        // Already in a finisher animation
        if (beingFinished.contains(victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // Get the actual killer (could be arrow, etc.)
        Player killer = null;
        if (event.getDamager() instanceof Player) {
            killer = (Player) event.getDamager();
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile) {
            org.bukkit.entity.Projectile proj = (org.bukkit.entity.Projectile) event.getDamager();
            if (proj.getShooter() instanceof Player) {
                killer = (Player) proj.getShooter();
            }
        }
        if (killer == null) return;

        // Check if this hit would kill the victim
        double finalDamage = event.getFinalDamage();
        if (victim.getHealth() - finalDamage > 0) return;

        // Check if killer has a finisher
        FinisherManager manager = plugin.getFinisherManager();
        FinisherType finisher = manager.getSelectedFinisher(killer.getUniqueId());
        if (finisher == null) return;

        // === CANCEL DEATH, START FINISHER ===
        event.setCancelled(true);
        beingFinished.add(victim.getUniqueId());

        // Freeze the victim completely
        victim.setHealth(1.0);
        victim.setInvulnerable(true);
        victim.setWalkSpeed(0f);
        victim.setFlySpeed(0f);
        victim.setGlowing(true);
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 600, 255, false, false, false));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 600, 200, false, false, false));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false, false));

        // Announce finisher
        String line = ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";
        String finisherMsg = "\n" + line
                + "\n" + ChatColor.DARK_RED + "  ☠ " + ChatColor.RED + "" + ChatColor.BOLD + killer.getName()
                + ChatColor.GRAY + " ejecuta a " + ChatColor.RED + "" + ChatColor.BOLD + victim.getName()
                + "\n" + ChatColor.GRAY + "  con " + finisher.getDisplayName()
                + "\n" + line + "\n";
        for (Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
            online.sendMessage(finisherMsg);
        }

        // Get animation duration and play
        int durationTicks = effects.play(finisher, victim, killer);

        // Freeze loop: teleport victim back to origin every 2 ticks so they literally cannot move
        final org.bukkit.Location freezeLoc = victim.getLocation().clone();
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t >= durationTicks || !victim.isOnline() || !beingFinished.contains(victim.getUniqueId())) {
                    cancel();
                    return;
                }
                // Keep yaw/pitch but lock XZ position (Y is controlled by levitation)
                org.bukkit.Location current = victim.getLocation();
                if (Math.abs(current.getX() - freezeLoc.getX()) > 0.15
                        || Math.abs(current.getZ() - freezeLoc.getZ()) > 0.15) {
                    org.bukkit.Location tp = freezeLoc.clone();
                    tp.setY(current.getY());
                    tp.setYaw(current.getYaw());
                    tp.setPitch(current.getPitch());
                    victim.teleport(tp);
                }
                t += 2;
            }
        }.runTaskTimer(plugin, 0, 2);

        // After animation completes, kill the victim
        new BukkitRunnable() {
            @Override
            public void run() {
                beingFinished.remove(victim.getUniqueId());
                victim.setInvulnerable(false);
                victim.setWalkSpeed(0.2f);
                victim.setFlySpeed(0.1f);
                victim.setGlowing(false);
                victim.removePotionEffect(PotionEffectType.SLOWNESS);
                victim.removePotionEffect(PotionEffectType.JUMP_BOOST);
                victim.removePotionEffect(PotionEffectType.LEVITATION);
                victim.removePotionEffect(PotionEffectType.BLINDNESS);

                // Kill the victim
                victim.setHealth(0);
            }
        }.runTaskLater(plugin, durationTicks + 5);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (event.getView().getTitle() == null) return;
        if (!event.getView().getTitle().equals(FinisherGUI.TITLE)) return;

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        int slot = event.getRawSlot();
        FinisherManager manager = plugin.getFinisherManager();

        // Deselect button
        if (slot == 40) {
            manager.deselectFinisher(player.getUniqueId());
            player.sendMessage(ChatColor.YELLOW + "Has quitado tu finisher activo.");
            com.moonlight.coreprotect.effects.SoundManager.playGUIClick(player.getLocation());
            new FinisherGUI(plugin).open(player);
            return;
        }

        // Map slots to finisher types
        int[] slots = {11, 13, 15, 20, 24};
        FinisherType[] types = FinisherType.values();
        FinisherType type = null;
        for (int i = 0; i < slots.length && i < types.length; i++) {
            if (slot == slots[i]) {
                type = types[i];
                break;
            }
        }
        if (type == null) return;

        if (manager.isSelected(player.getUniqueId(), type)) {
            // Deselect
            manager.deselectFinisher(player.getUniqueId());
            player.sendMessage(ChatColor.YELLOW + "Has desequipado " + type.getDisplayName() + ChatColor.YELLOW + ".");
            com.moonlight.coreprotect.effects.SoundManager.playGUIClick(player.getLocation());
            new FinisherGUI(plugin).open(player);
        } else if (manager.ownsFinisher(player.getUniqueId(), type)) {
            // Select
            manager.selectFinisher(player.getUniqueId(), type);
            player.sendMessage(ChatColor.GREEN + "Has equipado " + type.getDisplayName() + ChatColor.GREEN + "!");
            com.moonlight.coreprotect.effects.SoundManager.playUpgradePurchased(player.getLocation());
            new FinisherGUI(plugin).open(player);
        } else {
            // Purchase
            double price = manager.getPrice(type);
            if (plugin.getEconomy().getBalance(player) < price) {
                player.sendMessage(ChatColor.RED + "No tienes suficiente dinero. Necesitas " + ChatColor.GOLD + "$" + String.format("%,.0f", price) + ChatColor.RED + ".");
                return;
            }
            plugin.getEconomy().withdrawPlayer(player, price);
            manager.purchaseFinisher(player.getUniqueId(), type);
            manager.selectFinisher(player.getUniqueId(), type);
            player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "¡Has comprado " + type.getDisplayName() + ChatColor.GREEN + "" + ChatColor.BOLD + "!");
            player.sendMessage(ChatColor.GRAY + "Se ha equipado automáticamente.");
            com.moonlight.coreprotect.effects.SoundManager.playUpgradePurchased(player.getLocation());
            new FinisherGUI(plugin).open(player);
        }
    }
}
