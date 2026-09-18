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
import org.bukkit.event.entity.PlayerDeathEvent;
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
import com.moonlight.coreprotect.util.SmallCaps;

public class FinisherListener implements Listener {

    private final CoreProtectPlugin plugin;
    private final FinisherEffects effects;
    private final Map<UUID, org.bukkit.Location> activeFinisherLocations = new HashMap<>();
    private final Set<UUID> beingFinished = new HashSet<>();
    private final Set<UUID> finisherKillInProgress = new HashSet<>();
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
        
        // Clean up ghost blocks if player was being finished
        if (beingFinished.contains(uuid) || activeFinisherLocations.containsKey(uuid)) {
            // Immediate cleanup of ghost blocks
            effects.immediateGhostCleanup(activeFinisherLocations.get(uuid));
            activeFinisherLocations.remove(uuid);
        }
        
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
    
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();
        
        // Clean up ghost blocks immediately when player dies
        if (activeFinisherLocations.containsKey(uuid)) {
            effects.immediateGhostCleanup(activeFinisherLocations.get(uuid));
            activeFinisherLocations.remove(uuid);
        }
        
        // Also clean up if they were being finished
        beingFinished.remove(uuid);
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
            
            // Deshabilitar finishers en el mundo KOTH (excepto VALE_TODO) y minijuegos
            if (attacker.getWorld().getName().equals("minigames")) return;
            if (attacker.getWorld().getName().equals("koth")) {
                com.moonlight.coreprotect.koth.KothManager km = plugin.getKothManager();
                if (km == null || !km.isActive() || km.getCurrentMode() != com.moonlight.coreprotect.koth.KothMode.VALE_TODO) return;
            }
        }

        if (!(event.getEntity() instanceof Player))
            return;
        Player victim = (Player) event.getEntity();

        // Deshabilitar finishers en el mundo KOTH (excepto VALE_TODO) y minijuegos (víctima también)
        if (victim.getWorld().getName().equals("minigames")) return;
        if (victim.getWorld().getName().equals("koth")) {
            com.moonlight.coreprotect.koth.KothManager km = plugin.getKothManager();
            if (km == null || !km.isActive() || km.getCurrentMode() != com.moonlight.coreprotect.koth.KothMode.VALE_TODO) return;
        }

        // Already in a finisher animation
        if (beingFinished.contains(victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // Don't re-trigger finisher on the lethal damage from a finisher kill
        if (finisherKillInProgress.contains(victim.getUniqueId())) {
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
        final Player finisherKiller = killer;

        // Freeze victim completamente
        victim.setHealth(1.0);
        victim.setInvulnerable(true);
        victim.setWalkSpeed(0f);
        victim.setFlySpeed(0f);
        victim.setGlowing(true);
        victim.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        if (victim.isGliding()) victim.setGliding(false);
        if (victim.isFlying()) { victim.setFlying(false); victim.setAllowFlight(false); }
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

        // Register finisher location for cleanup
        activeFinisherLocations.put(victim.getUniqueId(), victim.getLocation().clone());
        
        int durationTicks = effects.play(finisher, victim, killer);

        // Continuous cleanup during APOCALYPSE finisher to prevent block accumulation
        if (finisher == FinisherType.APOCALYPSE) {
            new BukkitRunnable() {
                int t = 0;
                public void run() {
                    if (t >= durationTicks || !victim.isOnline() || !beingFinished.contains(victim.getUniqueId())) {
                        cancel();
                        return;
                    }
                    // Cleanup continuo cada 10 ticks para prevenir acumulación
                    effects.immediateGhostCleanup(victim.getLocation());
                    t += 10;
                }
            }.runTaskTimer(plugin, 20, 10); // Empezar en tick 20, cada 10 ticks
        }

        // Freeze loop — skip for spinning finishers (their spin teleport handles positioning)
        boolean isSpinFinisher = finisher == FinisherType.VOID_INVOCATION || finisher == FinisherType.SOUL_VORTEX
                || finisher == FinisherType.ORBITAL_STRIKE || finisher == FinisherType.DRAGON_WRATH
                || finisher == FinisherType.APOCALYPSE;
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
                    // Cancelar elytra y vuelo continuamente
                    if (victim.isGliding()) victim.setGliding(false);
                    if (victim.isFlying()) { victim.setFlying(false); victim.setAllowFlight(false); }
                    // Anular velocidad cada tick
                    victim.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                    // Teleportar a posición exacta (XYZ) si se ha movido
                    org.bukkit.Location current = victim.getLocation();
                    if (Math.abs(current.getX() - freezeLoc.getX()) > 0.1
                            || Math.abs(current.getY() - freezeLoc.getY()) > 0.3
                            || Math.abs(current.getZ() - freezeLoc.getZ()) > 0.1) {
                        org.bukkit.Location tp = freezeLoc.clone();
                        tp.setYaw(current.getYaw());
                        tp.setPitch(current.getPitch());
                        victim.teleport(tp);
                    }
                    t += 2;
                }
            }.runTaskTimer(plugin, 0, 2);
        }

        // Kill after animation — use damage(amount, killer) so Bukkit registers the killer
        // This fixes bounties, kill stats, and any other death listener that checks getKiller()
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!victim.isOnline() || !beingFinished.contains(victim.getUniqueId()))
                    return;

                beingFinished.remove(victim.getUniqueId());
                activeFinisherLocations.remove(victim.getUniqueId());
                victim.setInvulnerable(false);
                victim.setWalkSpeed(0.2f);
                victim.setFlySpeed(0.1f);
                victim.setGlowing(false);
                
                // Clear ALL potion effects to prevent particle persistence bug
                for (org.bukkit.potion.PotionEffect effect : victim.getActivePotionEffects()) {
                    victim.removePotionEffect(effect.getType());
                }

                // Deal lethal damage FROM the killer so getKiller() works for bounties etc.
                if (finisherKiller != null && finisherKiller.isOnline()) {
                    finisherKillInProgress.add(victim.getUniqueId());
                    victim.damage(1000.0, finisherKiller);
                    finisherKillInProgress.remove(victim.getUniqueId());
                } else {
                    victim.setHealth(0);
                }
                
                // Force death if still alive
                if (victim.getHealth() > 0) {
                    victim.setHealth(0);
                }
                
                // Remove invulnerability
                victim.setInvulnerable(false);
                
                // Immediate cleanup of all ghost blocks and particles after death
                effects.immediateGhostCleanup(activeFinisherLocations.get(victim.getUniqueId()));
                activeFinisherLocations.remove(victim.getUniqueId());
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

        // Deselect button
        if (slot == 49) {
            manager.deselectFinisher(player.getUniqueId());
            player.sendMessage(SmallCaps.convert(ChatColor.YELLOW + "Has quitado tu finisher activo."));
            com.moonlight.coreprotect.effects.SoundManager.playGUIClick(player.getLocation());
            new FinisherGUI(plugin).open(player);
            return;
        }

        // Map slots to finisher types
        int[] slots = { 10, 12, 14, 16, 19, 21, 23, 25, 28, 30, 32, 34 };
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

        // Right-click = preview (no purchase needed)
        if (event.isRightClick()) {
            long now = System.currentTimeMillis();
            Long lastUse = testCooldowns.get(player.getUniqueId());
            if (lastUse != null && now - lastUse < 60000) {
                long remaining = (60000 - (now - lastUse)) / 1000;
                player.sendMessage(SmallCaps.convert(ChatColor.RED + "Debes esperar " + ChatColor.GOLD + remaining + "s" + ChatColor.RED + " para volver a probar."));
                return;
            }
            if (beingFinished.contains(player.getUniqueId())) {
                player.sendMessage(SmallCaps.convert(ChatColor.RED + "Ya estás en una animación."));
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

            final FinisherType previewType = type;
            
            // Register preview location for cleanup
            activeFinisherLocations.put(player.getUniqueId(), player.getLocation().clone());
            
            player.sendMessage(SmallCaps.convert(ChatColor.GOLD + "⚡ " + ChatColor.YELLOW + "Probando " + previewType.getDisplayName() + ChatColor.YELLOW + "..."));
            int dur = effects.play(previewType, player, player);

            boolean skipFreeze = previewType == FinisherType.VOID_INVOCATION || previewType == FinisherType.SOUL_VORTEX
                    || previewType == FinisherType.ORBITAL_STRIKE || previewType == FinisherType.DRAGON_WRATH
                    || previewType == FinisherType.APOCALYPSE;
            if (!skipFreeze) {
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

            // Cleanup that runs multiple sweeps to catch late-arriving effects from delayed tasks
            final UUID previewUuid = player.getUniqueId();
            new BukkitRunnable() {
                int sweeps = 0;
                boolean messageSent = false;
                @Override public void run() {
                    if (!player.isOnline()) { cancel(); beingFinished.remove(previewUuid); return; }
                    // Full state restore every sweep
                    beingFinished.remove(previewUuid);
                    activeFinisherLocations.remove(previewUuid);
                    player.setInvulnerable(false);
                    player.setWalkSpeed(0.2f);
                    player.setFlySpeed(0.1f);
                    player.setGlowing(false);
                    player.removePotionEffect(PotionEffectType.SLOWNESS);
                    player.removePotionEffect(PotionEffectType.JUMP_BOOST);
                    player.removePotionEffect(PotionEffectType.LEVITATION);
                    player.removePotionEffect(PotionEffectType.BLINDNESS);
                    player.setFallDistance(0);
                    if (!messageSent) {
                        player.sendMessage(SmallCaps.convert(ChatColor.GREEN + "✔ Prueba completada. No has recibido daño."));
                        messageSent = true;
                    }
                    sweeps++;
                    if (sweeps >= 8) cancel(); // 8 sweeps * 20 ticks = 8 seconds of cleanup
                }
            }.runTaskTimer(plugin, dur + 5, 20); // First at dur+5, then every 1 second for 8 seconds
            return;
        }

        // Left-click = normal buy/equip/deselect
        if (manager.isSelected(player.getUniqueId(), type)) {
            // Deselect
            manager.deselectFinisher(player.getUniqueId());
            player.sendMessage(SmallCaps.convert(ChatColor.YELLOW + "Has desequipado " + type.getDisplayName() + ChatColor.YELLOW + "."));
            com.moonlight.coreprotect.effects.SoundManager.playGUIClick(player.getLocation());
            new FinisherGUI(plugin).open(player);
        } else if (manager.ownsFinisher(player.getUniqueId(), type)) {
            // Select
            manager.selectFinisher(player.getUniqueId(), type);
            player.sendMessage(SmallCaps.convert(ChatColor.GREEN + "Has equipado " + type.getDisplayName() + ChatColor.GREEN + "!"));
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
            player.sendMessage(SmallCaps.convert(ChatColor.GRAY + "Se ha equipado automáticamente."));
            com.moonlight.coreprotect.effects.SoundManager.playUpgradePurchased(player.getLocation());
            new FinisherGUI(plugin).open(player);
        }
    }
}
