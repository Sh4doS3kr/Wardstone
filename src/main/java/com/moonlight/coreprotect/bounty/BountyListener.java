package com.moonlight.coreprotect.bounty;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Listener para todas las interacciones del sistema de Bounties.
 */
public class BountyListener implements Listener {

    private final CoreProtectPlugin plugin;
    private final BountyManager manager;

    // Estado por jugador
    private final Map<UUID, BountyManager.SortType> playerSort = new ConcurrentHashMap<>();
    private final Map<UUID, String> selectedTarget = new ConcurrentHashMap<>();
    private final Map<UUID, Double> selectedPrice = new ConcurrentHashMap<>();
    private final Map<UUID, ChatInputType> awaitingChatInput = new ConcurrentHashMap<>();

    private enum ChatInputType {
        CUSTOM_PRICE
    }

    public BountyListener(CoreProtectPlugin plugin, BountyManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    // ==================== INVENTORY CLICK ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();

        if (BountyGUI.isBountyGUI(title)) {
            handleMainGUIClick(event, player, title);
            return;
        }
        if (BountySetGUI.isSelectPlayerGUI(title)) {
            handleSelectPlayerClick(event, player, title);
            return;
        }
        if (BountySetGUI.isSelectPriceGUI(title)) {
            handleSelectPriceClick(event, player);
            return;
        }
        if (BountyMyGUI.isMyBountyGUI(title)) {
            handleMyBountiesClick(event, player, title);
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        String title = event.getView().getTitle();
        if (BountyGUI.isBountyGUI(title) || BountySetGUI.isSelectPlayerGUI(title) ||
            BountySetGUI.isSelectPriceGUI(title) || BountyMyGUI.isMyBountyGUI(title)) {
            event.setCancelled(true);
        }
    }

    // ==================== MAIN GUI ====================

    private void handleMainGUIClick(InventoryClickEvent event, Player player, String title) {
        int slot = event.getRawSlot();
        if (slot >= 54) return;
        event.setCancelled(true);

        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
        if (isGlassPane(event.getCurrentItem().getType())) return;

        int page = BountyGUI.getPageFromTitle(title);
        UUID uid = player.getUniqueId();
        BountyManager.SortType sort = playerSort.getOrDefault(uid, BountyManager.SortType.RECIENTE);

        // Página anterior
        if (slot == 45) {
            playClick(player);
            openMainGUI(player, page - 1);
            return;
        }

        // Página siguiente
        if (slot == 53) {
            playClick(player);
            openMainGUI(player, page + 1);
            return;
        }

        // Ordenar
        if (slot == 47) {
            playClick(player);
            BountyManager.SortType[] sorts = BountyManager.SortType.values();
            int idx = (sort.ordinal() + 1) % sorts.length;
            playerSort.put(uid, sorts[idx]);
            openMainGUI(player, 1);
            return;
        }

        // Top Cazadores (slot 48) - solo informativo
        if (slot == 48) {
            playClick(player);
            return;
        }

        // Poner Bounty
        if (slot == 49) {
            playClick(player);
            selectedTarget.remove(uid);
            selectedPrice.remove(uid);
            BountySetGUI.openSelectPlayer(player, 1);
            return;
        }

        // Mis Bounties
        if (slot == 51) {
            playClick(player);
            BountyMyGUI.open(player, manager, BountyMyGUI.Tab.PUESTAS, 1);
            return;
        }

        // Top Buscados (slot 52) - solo informativo
        if (slot == 52) {
            playClick(player);
            return;
        }

        // Info (slot 4) - solo informativo
        if (slot == 4) return;

        // Click en una bounty
        Bounty bounty = BountyGUI.getBountyAtSlot(slot, page, manager, sort);
        if (bounty != null) {
            if (bounty.getPlacer().equals(uid)) {
                // Cancelar su propia bounty
                playClick(player);
                BountyManager.BountyResult result = manager.cancelBounty(player, bounty.getId());
                player.sendMessage(result.getMessage());
                if (result.isSuccess()) {
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
                }
                openMainGUI(player, page);
            }
            // Si no es suya, no hace nada (tiene que matar al target en el mundo)
        }
    }

    // ==================== SELECT PLAYER GUI ====================

    private void handleSelectPlayerClick(InventoryClickEvent event, Player player, String title) {
        int slot = event.getRawSlot();
        if (slot >= 54) return;
        event.setCancelled(true);

        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
        if (isGlassPane(event.getCurrentItem().getType())) return;

        int page = BountySetGUI.getPageFromSelectTitle(title);

        // Página anterior
        if (slot == 45) {
            playClick(player);
            BountySetGUI.openSelectPlayer(player, page - 1);
            return;
        }

        // Página siguiente
        if (slot == 53) {
            playClick(player);
            BountySetGUI.openSelectPlayer(player, page + 1);
            return;
        }

        // Volver
        if (slot == 49) {
            playClick(player);
            openMainGUI(player, 1);
            return;
        }

        // Header
        if (slot == 4) return;

        // Click en un jugador — leer el nombre directamente del item clickeado
        if (event.getCurrentItem().getType() == Material.PLAYER_HEAD) {
            org.bukkit.inventory.meta.SkullMeta skullMeta =
                    (org.bukkit.inventory.meta.SkullMeta) event.getCurrentItem().getItemMeta();
            if (skullMeta != null && skullMeta.getOwningPlayer() != null) {
                String targetName = skullMeta.getOwningPlayer().getName();
                if (targetName != null) {
                    playClick(player);
                    selectedTarget.put(player.getUniqueId(), targetName);
                    selectedPrice.remove(player.getUniqueId());
                    BountySetGUI.openSelectPrice(player, targetName);
                }
            }
        }
    }

    // ==================== SELECT PRICE GUI ====================

    private void handleSelectPriceClick(InventoryClickEvent event, Player player) {
        int slot = event.getRawSlot();
        if (slot >= 54) return;
        event.setCancelled(true);

        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
        if (isGlassPane(event.getCurrentItem().getType())) return;

        UUID uid = player.getUniqueId();
        String targetName = selectedTarget.get(uid);
        if (targetName == null) {
            player.closeInventory();
            return;
        }

        // Volver
        if (slot == 45) {
            playClick(player);
            selectedPrice.remove(uid);
            BountySetGUI.openSelectPlayer(player, 1);
            return;
        }

        // Precio personalizado
        if (slot == 31) {
            player.closeInventory();
            awaitingChatInput.put(uid, ChatInputType.CUSTOM_PRICE);
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("§c§l☠ §e§lPrecio de Bounty"));
            player.sendMessage(SmallCaps.convert("§7Escribe el precio en el chat (ej: §e15000§7)."));
            player.sendMessage("§7Mín: §e$" + String.format("%,.0f", Bounty.MIN_BOUNTY) +
                " §7| Máx: §e$" + String.format("%,.0f", Bounty.MAX_BOUNTY));
            player.sendMessage(SmallCaps.convert("§7Escribe §c\"cancelar\" §7para volver."));
            player.sendMessage("");
            return;
        }

        // Precio predefinido
        double price = BountySetGUI.getPriceFromSlot(slot);
        if (price > 0) {
            playClick(player);
            selectedPrice.put(uid, price);
            BountySetGUI.updateWithPrice(event.getView().getTopInventory(), price, targetName);
            return;
        }

        // Confirmar bounty
        if (slot == 49 && event.getCurrentItem().getType() == Material.LIME_CONCRETE) {
            Double selPrice = selectedPrice.get(uid);
            if (selPrice == null) {
                player.sendMessage(SmallCaps.convert("§c§l✖ §cSelecciona un precio primero."));
                return;
            }

            Player target = Bukkit.getPlayer(targetName);
            if (target == null || !target.isOnline()) {
                player.sendMessage(SmallCaps.convert("§c§l✖ §c" + targetName + " ya no está online."));
                selectedTarget.remove(uid);
                selectedPrice.remove(uid);
                Bukkit.getScheduler().runTaskLater(plugin, () -> openMainGUI(player, 1), 1L);
                return;
            }

            BountyManager.BountyResult result = manager.placeBounty(player, target, selPrice);
            player.sendMessage(result.getMessage());

            if (result.isSuccess()) {
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);
                // Efecto visual al poner bounty
                playBountyPlacedEffect(player);
            } else {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }

            selectedTarget.remove(uid);
            selectedPrice.remove(uid);
            Bukkit.getScheduler().runTaskLater(plugin, () -> openMainGUI(player, 1), 1L);
            return;
        }
    }

    // ==================== MY BOUNTIES GUI ====================

    private void handleMyBountiesClick(InventoryClickEvent event, Player player, String title) {
        int slot = event.getRawSlot();
        if (slot >= 54) return;
        event.setCancelled(true);

        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
        if (isGlassPane(event.getCurrentItem().getType())) return;

        BountyMyGUI.Tab tab = BountyMyGUI.getTabFromTitle(title);
        int page = BountyMyGUI.getPageFromTitle(title);

        // Tab Puestas
        if (slot == 2) {
            playClick(player);
            BountyMyGUI.open(player, manager, BountyMyGUI.Tab.PUESTAS, 1);
            return;
        }

        // Tab Sobre mí
        if (slot == 6) {
            playClick(player);
            BountyMyGUI.open(player, manager, BountyMyGUI.Tab.SOBRE_MI, 1);
            return;
        }

        // Volver
        if (slot == 49) {
            playClick(player);
            openMainGUI(player, 1);
            return;
        }

        // Página anterior
        if (slot == 45) {
            playClick(player);
            BountyMyGUI.open(player, manager, tab, page - 1);
            return;
        }

        // Página siguiente
        if (slot == 53) {
            playClick(player);
            BountyMyGUI.open(player, manager, tab, page + 1);
            return;
        }

        // Click en una bounty
        if (tab == BountyMyGUI.Tab.PUESTAS) {
            List<Bounty> bounties = manager.getActiveBountiesPlacedBy(player.getUniqueId());
            Bounty bounty = BountyMyGUI.getBountyAtSlot(slot, page, bounties);
            if (bounty != null) {
                playClick(player);
                BountyManager.BountyResult result = manager.cancelBounty(player, bounty.getId());
                player.sendMessage(result.getMessage());
                if (result.isSuccess()) {
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
                }
                BountyMyGUI.open(player, manager, tab, page);
            }
        }
        // Tab SOBRE_MI: solo informativo, no se puede cancelar bounties de otros
    }

    // ==================== CHAT INPUT ====================

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();
        ChatInputType inputType = awaitingChatInput.remove(uid);
        if (inputType == null) return;

        event.setCancelled(true);
        String message = event.getMessage().trim();

        if (message.equalsIgnoreCase("cancelar") || message.equalsIgnoreCase("cancel")) {
            String targetName = selectedTarget.get(uid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (targetName != null) {
                    BountySetGUI.openSelectPrice(player, targetName);
                } else {
                    openMainGUI(player, 1);
                }
            });
            return;
        }

        if (inputType == ChatInputType.CUSTOM_PRICE) {
            try {
                double price = Double.parseDouble(message.replace(",", "").replace("$", ""));
                if (price < Bounty.MIN_BOUNTY || price > Bounty.MAX_BOUNTY) {
                    player.sendMessage("§c§l✖ §cPrecio inválido. Mín: $" + String.format("%,.0f", Bounty.MIN_BOUNTY) +
                        " | Máx: $" + String.format("%,.0f", Bounty.MAX_BOUNTY));
                    String targetName = selectedTarget.get(uid);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (targetName != null) BountySetGUI.openSelectPrice(player, targetName);
                        else openMainGUI(player, 1);
                    });
                    return;
                }
                selectedPrice.put(uid, price);
                player.sendMessage(SmallCaps.convert("§a§l✔ §fPrecio establecido: §e$" + String.format("%,.0f", price)));
                String targetName = selectedTarget.get(uid);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (targetName != null) {
                        BountySetGUI.openSelectPrice(player, targetName);
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (player.getOpenInventory().getTitle().startsWith(BountySetGUI.SELECT_PRICE_TITLE)) {
                                BountySetGUI.updateWithPrice(player.getOpenInventory().getTopInventory(), price, targetName);
                            }
                        }, 2L);
                    } else {
                        openMainGUI(player, 1);
                    }
                });
            } catch (NumberFormatException e) {
                player.sendMessage(SmallCaps.convert("§c§l✖ §cEso no es un número válido. Intenta de nuevo."));
                String targetName = selectedTarget.get(uid);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (targetName != null) BountySetGUI.openSelectPrice(player, targetName);
                    else openMainGUI(player, 1);
                });
            }
        }
    }

    // ==================== MUERTE - COBRAR BOUNTIES ====================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) return;
        if (killer.getUniqueId().equals(victim.getUniqueId())) return;
        
        // No procesar bounties en el mundo de minijuegos (Murder Mystery, etc.)
        if (victim.getWorld().getName().equals("minigames")) {
            return;
        }

        List<Bounty> claimed = manager.claimBounties(killer, victim);
        if (claimed.isEmpty()) return;

        double totalAmount = claimed.stream().mapToDouble(Bounty::getAmount).sum();

        // === ANIMACIÓN ÉPICA DE BOUNTY COBRADA ===
        playBountyClaimAnimation(killer, victim, totalAmount);

        // Mensajes al asesino
        killer.sendMessage("");
        killer.sendMessage(SmallCaps.convert("§a§l$$$ §6§l¡BOUNTY COBRADA! §a§l$$$"));
        for (Bounty b : claimed) {
            killer.sendMessage(SmallCaps.convert("  §a+ §e" + b.getFormattedAmount() + " §7(puesta por §f" + b.getPlacerName() + "§7)"));
        }
        killer.sendMessage(SmallCaps.convert("§a§l  TOTAL: §e§l$" + String.format("%,.0f", totalAmount)));
        killer.sendMessage("");

        // Anuncio al servidor
        String announcement = "§4§l☠ §c" + killer.getName() + " §fha cobrado §a$" + String.format("%,.0f", totalAmount) +
            " §fde bounty al matar a §c" + victim.getName() + "§f!";
        Bukkit.broadcastMessage(announcement);

        // Notificar a los que pusieron las bounties
        for (Bounty b : claimed) {
            Player placer = Bukkit.getPlayer(b.getPlacer());
            if (placer != null && placer.isOnline() && !placer.getUniqueId().equals(killer.getUniqueId())) {
                placer.sendMessage(SmallCaps.convert("§6§l☠ §eTu bounty sobre §c" + victim.getName() + " §efue cobrada por §f" + killer.getName() + "§e."));
            }
        }
    }

    // ==================== ANIMACIONES ====================

    private void playBountyClaimAnimation(Player killer, Player victim, double totalAmount) {
        Location loc = victim.getLocation();
        World world = loc.getWorld();

        // Fase 1: Explosión de partículas doradas
        new BukkitRunnable() {
            int ticks = 0;
            public void run() {
                if (ticks >= 40) { cancel(); return; }

                // Espiral dorada ascendente
                double angle = ticks * 0.5;
                double radius = 2.0 - (ticks * 0.04);
                double x = Math.cos(angle) * radius;
                double z = Math.sin(angle) * radius;
                double y = ticks * 0.1;

                Location particleLoc = loc.clone().add(x, y, z);
                world.spawnParticle(Particle.END_ROD, particleLoc, 3, 0.1, 0.1, 0.1, 0.02);

                // Partículas de oro
                if (ticks % 3 == 0) {
                    world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1, 0),
                        10, 1.5, 1.5, 1.5, 0.3);
                }

                // Sonidos escalados
                if (ticks % 5 == 0) {
                    float pitch = 0.5f + (ticks / 40.0f) * 1.5f;
                    world.playSound(loc, Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, pitch);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0, 1);

        // Fase 2: Fuegos artificiales
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            spawnBountyFirework(loc.clone().add(0, 2, 0));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                spawnBountyFirework(loc.clone().add(2, 3, 0));
                spawnBountyFirework(loc.clone().add(-2, 3, 0));
            }, 5L);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                spawnBountyFirework(loc.clone().add(0, 4, 2));
                spawnBountyFirework(loc.clone().add(0, 4, -2));
            }, 10L);
        }, 20L);

        // Fase 3: Rayo de luz al asesino
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            world.strikeLightningEffect(killer.getLocation());
            killer.playSound(killer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

            // Partículas de celebración alrededor del asesino
            new BukkitRunnable() {
                int ticks = 0;
                public void run() {
                    if (ticks >= 60 || !killer.isOnline()) { cancel(); return; }

                    double angle = ticks * 0.3;
                    double x = Math.cos(angle) * 1.5;
                    double z = Math.sin(angle) * 1.5;

                    Location pLoc = killer.getLocation().add(x, 1.5, z);
                    world.spawnParticle(Particle.END_ROD, pLoc, 2, 0, 0, 0, 0.02);

                    if (ticks % 10 == 0) {
                        world.spawnParticle(Particle.TOTEM_OF_UNDYING, killer.getLocation().add(0, 2, 0),
                            5, 0.5, 0.5, 0.5, 0.1);
                    }

                    ticks++;
                }
            }.runTaskTimer(plugin, 0, 1);
        }, 30L);

        // Fase 4: Título al asesino
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (killer.isOnline()) {
                killer.sendTitle(
                    "§6§l¡BOUNTY COBRADA!",
                    "§a+$" + String.format("%,.0f", totalAmount) + " §7por matar a §c" + victim.getName(),
                    10, 60, 20
                );
            }
        }, 5L);
    }

    private void playBountyPlacedEffect(Player placer) {
        Location loc = placer.getLocation();
        World world = loc.getWorld();

        // Partículas de humo rojo
        new BukkitRunnable() {
            int ticks = 0;
            public void run() {
                if (ticks >= 20) { cancel(); return; }

                double angle = ticks * 0.6;
                for (int i = 0; i < 4; i++) {
                    double a = angle + (Math.PI / 2) * i;
                    double x = Math.cos(a) * 1.5;
                    double z = Math.sin(a) * 1.5;
                    world.spawnParticle(Particle.DUST,
                        loc.clone().add(x, 0.5 + ticks * 0.1, z),
                        3, 0.1, 0.1, 0.1, 0,
                        new Particle.DustOptions(Color.RED, 1.5f));
                }

                if (ticks % 5 == 0) {
                    world.playSound(loc, Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.5f + ticks * 0.05f);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private void spawnBountyFirework(Location loc) {
        Firework fw = (Firework) loc.getWorld().spawnEntity(loc, EntityType.FIREWORK_ROCKET);
        FireworkMeta meta = fw.getFireworkMeta();

        FireworkEffect effect = FireworkEffect.builder()
            .with(FireworkEffect.Type.STAR)
            .withColor(Color.ORANGE, Color.YELLOW, Color.RED)
            .withFade(Color.fromRGB(255, 215, 0))
            .withTrail()
            .withFlicker()
            .build();

        meta.addEffect(effect);
        meta.setPower(0);
        fw.setFireworkMeta(meta);

        // Detonar inmediatamente
        Bukkit.getScheduler().runTaskLater(plugin, fw::detonate, 2L);
    }

    // ==================== HELPERS ====================

    public void openMainGUI(Player player, int page) {
        UUID uid = player.getUniqueId();
        BountyManager.SortType sort = playerSort.getOrDefault(uid, BountyManager.SortType.RECIENTE);
        BountyGUI.open(player, manager, page, sort);
    }

    private boolean isGlassPane(Material material) {
        return material.name().contains("STAINED_GLASS_PANE");
    }

    private void playClick(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }
}
