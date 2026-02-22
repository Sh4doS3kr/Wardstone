package com.moonlight.coreprotect.finishers;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FinisherListener implements Listener {

    private final CoreProtectPlugin plugin;
    private final FinisherEffects effects;
    private final Set<UUID> beingFinished = new HashSet<>();
    private final Map<UUID, Long> testCooldowns = new HashMap<>();

    public FinisherListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        this.effects = new FinisherEffects(plugin);
    }

    public boolean isBeingFinished(UUID uuid) {
        return beingFinished.contains(uuid);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        // If player was being finished, clean up their state immediately
        if (beingFinished.contains(uuid)) {
            beingFinished.remove(uuid);
            
            // Remove all effects and invulnerability
            player.setInvulnerable(false);
            player.setWalkSpeed(0.2f);
            player.setFlySpeed(0.1f);
            player.setGlowing(false);
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            player.removePotionEffect(PotionEffectType.JUMP_BOOST);
            player.removePotionEffect(PotionEffectType.LEVITATION);
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            
            // Kill them instantly so they don't rejoin invulnerable
            player.setHealth(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockForm(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof FallingBlock) {
            FallingBlock fb = (FallingBlock) event.getEntity();
            if (!fb.getDropItem()) {
                event.setCancelled(true);
                fb.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player))
            return;
        Player victim = (Player) event.getEntity();
        if (beingFinished.contains(victim.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerAttacked(EntityDamageByEntityEvent event) {
        // Block victim from attacking during finisher
        if (event.getDamager() instanceof Player) {
            Player attacker = (Player) event.getDamager();
            if (beingFinished.contains(attacker.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
        }

        if (!(event.getEntity() instanceof Player))
            return;
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
        if (killer == null)
            return;

        // Check if this hit would kill the victim
        double finalDamage = event.getFinalDamage();
        if (victim.getHealth() - finalDamage > 0)
            return;

        // Check if killer has a finisher
        FinisherManager manager = plugin.getFinisherManager();
        FinisherType finisher = manager.getSelectedFinisher(killer.getUniqueId());
        if (finisher == null)
            return;

        // Check for Totem of Undying BEFORE starting any animation
        boolean hasTotem = false;
        boolean inMainHand = false;
        org.bukkit.inventory.ItemStack mainHand = victim.getInventory().getItemInMainHand();
        org.bukkit.inventory.ItemStack offHand = victim.getInventory().getItemInOffHand();

        if (mainHand != null && mainHand.getType() == org.bukkit.Material.TOTEM_OF_UNDYING) {
            hasTotem = true;
            inMainHand = true;
        } else if (offHand != null && offHand.getType() == org.bukkit.Material.TOTEM_OF_UNDYING) {
            hasTotem = true;
            inMainHand = false;
        }

        if (hasTotem) {
            // === IMMEDIATE TOTEM COUNTER (PvP Optimization) ===
            event.setCancelled(true);
            beingFinished.add(victim.getUniqueId());

            // Play immediate explosion feedback
            effects.playTotemExplosion(victim.getLocation());

            // Consume the totem
            if (inMainHand) {
                mainHand.setAmount(mainHand.getAmount() - 1);
            } else {
                offHand.setAmount(offHand.getAmount() - 1);
            }

            // Start accelerated counter animation (100 ticks = 5s)
            int counterDuration = effects.playTotemCounter(victim, killer);

            // Anime chat messages
            String sep = ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";
            String vName = ChatColor.GOLD + "" + ChatColor.BOLD + victim.getName();
            String kName = ChatColor.RED + "" + ChatColor.BOLD + killer.getName();
            String msg1 = "\n" + sep + "\n"
                    + ChatColor.GOLD + "  ✦ " + ChatColor.YELLOW + "¡El Tótem de " + vName + ChatColor.YELLOW
                    + " se ACTIVA!"
                    + "\n" + sep + "\n";
            for (Player p : org.bukkit.Bukkit.getOnlinePlayers())
                p.sendMessage(msg1);

            // Final message after duration
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!victim.isOnline())
                        return;
                    String msg2 = "\n" + sep + "\n"
                            + ChatColor.RED + "  ☄ " + kName + ChatColor.GRAY + " no pudo matar a " + vName
                            + "\n" + ChatColor.GOLD + "  ¡LA BATALLA CONTINÚA!"
                            + "\n" + sep + "\n";
                    for (Player p : org.bukkit.Bukkit.getOnlinePlayers())
                        p.sendMessage(msg2);

                    beingFinished.remove(victim.getUniqueId());
                    victim.setInvulnerable(false);
                    victim.setWalkSpeed(0.2f);
                    victim.setFlySpeed(0.1f);
                    victim.setGlowing(false);
                    victim.removePotionEffect(PotionEffectType.SLOWNESS);
                    victim.removePotionEffect(PotionEffectType.JUMP_BOOST);
                    victim.removePotionEffect(PotionEffectType.LEVITATION);
                    victim.removePotionEffect(PotionEffectType.BLINDNESS);

                    // Restore to half health — SURVIVED
                    double maxHp = victim.getMaxHealth();
                    victim.setHealth(Math.min(maxHp, maxHp / 2.0));
                    victim.setAbsorptionAmount(4.0);
                }
            }.runTaskLater(plugin, counterDuration);

            return;
        }

        // === NO TOTEM: PLAY NORMAL FINISHER ===
        event.setCancelled(true);
        beingFinished.add(victim.getUniqueId());

        // Freeze victim
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
        for (Player online : org.bukkit.Bukkit.getOnlinePlayers())
            online.sendMessage(finisherMsg);

        int durationTicks = effects.play(finisher, victim, killer);

        // Freeze loop — skip for spinning finishers (their spin teleport handles positioning)
        boolean isSpinFinisher = finisher == FinisherType.VOID_INVOCATION || finisher == FinisherType.SOUL_VORTEX;
        if (!isSpinFinisher) {
            final org.bukkit.Location freezeLoc = victim.getLocation().clone();
            new BukkitRunnable() {
                int t = 0;

                @Override
                public void run() {
                    if (t >= durationTicks || !victim.isOnline() || !beingFinished.contains(victim.getUniqueId())) {
                        cancel();
                        return;
                    }
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
        }

        // Kill after animation
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!victim.isOnline() || !beingFinished.contains(victim.getUniqueId()))
                    return;

                beingFinished.remove(victim.getUniqueId());
                victim.setInvulnerable(false);
                victim.setWalkSpeed(0.2f);
                victim.setFlySpeed(0.1f);
                victim.setGlowing(false);
                victim.removePotionEffect(PotionEffectType.SLOWNESS);
                victim.removePotionEffect(PotionEffectType.JUMP_BOOST);
                victim.removePotionEffect(PotionEffectType.LEVITATION);
                victim.removePotionEffect(PotionEffectType.BLINDNESS);

                victim.setHealth(0);
            }
        }.runTaskLater(plugin, durationTicks + 5);

    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player))
            return;
        if (event.getView().getTitle() == null)
            return;
        if (!event.getView().getTitle().equals(FinisherGUI.TITLE))
            return;

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta())
            return;

        int slot = event.getRawSlot();
        FinisherManager manager = plugin.getFinisherManager();

        // Test button
        if (slot == 46) {
            FinisherType selected = manager.getSelectedFinisher(player.getUniqueId());
            if (selected == null) {
                player.sendMessage(ChatColor.RED + "No tienes ningún finisher equipado para probar.");
                return;
            }
            long now = System.currentTimeMillis();
            Long lastUse = testCooldowns.get(player.getUniqueId());
            if (lastUse != null && now - lastUse < 60000) {
                long remaining = (60000 - (now - lastUse)) / 1000;
                player.sendMessage(ChatColor.RED + "Debes esperar " + ChatColor.GOLD + remaining + "s" + ChatColor.RED + " para volver a probar.");
                return;
            }
            if (beingFinished.contains(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "Ya estás en una animación.");
                return;
            }
            testCooldowns.put(player.getUniqueId(), now);
            player.closeInventory();
            beingFinished.add(player.getUniqueId());

            player.setInvulnerable(true);
            player.setWalkSpeed(0f);
            player.setFlySpeed(0f);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 600, 255, false, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 600, 200, false, false, false));

            player.sendMessage(ChatColor.GOLD + "⚡ " + ChatColor.YELLOW + "Probando " + selected.getDisplayName() + ChatColor.YELLOW + "...");
            int dur = effects.play(selected, player, player);

            boolean isTestSpin = selected == FinisherType.VOID_INVOCATION || selected == FinisherType.SOUL_VORTEX;
            if (!isTestSpin) {
                final org.bukkit.Location freezeLoc = player.getLocation().clone();
                new BukkitRunnable() {
                    int t = 0;
                    @Override public void run() {
                        if (t >= dur || !player.isOnline() || !beingFinished.contains(player.getUniqueId())) { cancel(); return; }
                        org.bukkit.Location cur = player.getLocation();
                        if (Math.abs(cur.getX() - freezeLoc.getX()) > 0.15 || Math.abs(cur.getZ() - freezeLoc.getZ()) > 0.15) {
                            org.bukkit.Location tp = freezeLoc.clone(); tp.setY(cur.getY()); tp.setYaw(cur.getYaw()); tp.setPitch(cur.getPitch()); player.teleport(tp);
                        }
                        t += 2;
                    }
                }.runTaskTimer(plugin, 0, 2);
            }

            new BukkitRunnable() {
                @Override public void run() {
                    if (!player.isOnline()) return;
                    beingFinished.remove(player.getUniqueId());
                    player.setInvulnerable(false);
                    player.setWalkSpeed(0.2f);
                    player.setFlySpeed(0.1f);
                    player.setGlowing(false);
                    player.removePotionEffect(PotionEffectType.SLOWNESS);
                    player.removePotionEffect(PotionEffectType.JUMP_BOOST);
                    player.removePotionEffect(PotionEffectType.LEVITATION);
                    player.removePotionEffect(PotionEffectType.BLINDNESS);
                    player.sendMessage(ChatColor.GREEN + "✔ Prueba completada. No has recibido daño.");
                }
            }.runTaskLater(plugin, dur + 5);
            return;
        }

        // Deselect button
        if (slot == 49) {
            manager.deselectFinisher(player.getUniqueId());
            player.sendMessage(ChatColor.YELLOW + "Has quitado tu finisher activo.");
            com.moonlight.coreprotect.effects.SoundManager.playGUIClick(player.getLocation());
            new FinisherGUI(plugin).open(player);
            return;
        }

        // Map slots to finisher types
        int[] slots = { 10, 12, 14, 16, 19, 21, 23, 25, 28, 30, 32 };
        FinisherType[] types = FinisherType.values();
        FinisherType type = null;
        for (int i = 0; i < slots.length && i < types.length; i++) {
            if (slot == slots[i]) {
                type = types[i];
                break;
            }
        }
        if (type == null)
            return;

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
                player.sendMessage(ChatColor.RED + "No tienes suficiente dinero. Necesitas " + ChatColor.GOLD + "$"
                        + String.format("%,.0f", price) + ChatColor.RED + ".");
                return;
            }
            plugin.getEconomy().withdrawPlayer(player, price);
            manager.purchaseFinisher(player.getUniqueId(), type);
            manager.selectFinisher(player.getUniqueId(), type);
            player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "¡Has comprado " + type.getDisplayName()
                    + ChatColor.GREEN + "" + ChatColor.BOLD + "!");
            player.sendMessage(ChatColor.GRAY + "Se ha equipado automáticamente.");
            com.moonlight.coreprotect.effects.SoundManager.playUpgradePurchased(player.getLocation());
            new FinisherGUI(plugin).open(player);
        }
    }
}
