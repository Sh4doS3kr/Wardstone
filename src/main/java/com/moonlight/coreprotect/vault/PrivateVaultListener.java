package com.moonlight.coreprotect.vault;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.streak.DailyStreakListener;
import com.moonlight.coreprotect.util.SmallCaps;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Listener que guarda el contenido del Private Vault cuando el jugador cierra
 * el inventario.
 */
public class PrivateVaultListener implements Listener {

    private final CoreProtectPlugin plugin;

    private int warningCycle = 0;

    // Tracking para evitar loops al reabrir el selector
    private final Set<UUID> reopeningSelector = new HashSet<>();

    public PrivateVaultListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        startVaultWarningTask();
    }

    /**
     * Tarea periodica que avisa a jugadores con vaults en riesgo.
     * - Cada 2 min: action bar warning
     * - Cada 10 min: bossbar 30s + chat warning
     * Tambien verifica deadlines expiradas.
     */
    private void startVaultWarningTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                PrivateVaultManager mgr = plugin.getPrivateVaultManager();
                if (mgr == null) return;

                // Verificar deadlines expiradas
                mgr.checkExpiredDeadlines();

                warningCycle++;

                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!mgr.hasVaultsAtRisk(player.getUniqueId())) continue;

                    long deadline = mgr.getDeadline(player.getUniqueId());
                    if (deadline <= 0) continue;
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0) continue;

                    String timeLeft = formatTimeLeftShort(remaining);

                    // Action bar repetido durante 30s (se re-envia cada segundo para que no se borre)
                    String abMsg = SmallCaps.convert("\u00a7c\u00a7l\u26a0 \u00a7eTienes items en vaults bloqueados. \u00a77Retiralos antes de \u00a7c" + timeLeft + " \u00a77o seran eliminados.");
                    DailyStreakListener.setActionBarOverride(player.getUniqueId(), 30000);
                    startRepeatingActionBar(player, abMsg, 30);

                    // BossBar + Chat cada 10 min (cada 5 ciclos)
                    if (warningCycle % 5 == 0) {
                        // Chat
                        player.sendMessage("");
                        player.sendMessage(SmallCaps.convert("\u00a7c\u00a7l\u26a0 \u00a7e\u00a7lAVISO: PRIVATE VAULTS EN RIESGO"));
                        player.sendMessage(SmallCaps.convert("\u00a77Tienes items en vaults que requieren mas prestigio."));
                        player.sendMessage(SmallCaps.convert("\u00a77Retiralos o seran \u00a7c\u00a7lELIMINADOS \u00a77en \u00a7c" + timeLeft + "\u00a77."));
                        player.sendMessage(SmallCaps.convert("\u00a77Usa \u00a7f/pv \u00a77para ver tus vaults."));
                        player.sendMessage("");
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.6f, 0.8f);

                        // BossBar temporal (30 segundos)
                        showVaultBossBar(player, timeLeft);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L * 120, 20L * 120); // Cada 2 minutos
    }

    /**
     * Re-envia un mensaje de action bar cada segundo durante los segundos indicados.
     */
    private void startRepeatingActionBar(Player player, String message, int durationSeconds) {
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!player.isOnline() || ticks >= durationSeconds) {
                    cancel();
                    return;
                }
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 20L); // cada segundo
    }

    private void showVaultBossBar(Player player, String timeLeft) {
        BossBar bar = Bukkit.createBossBar(
                SmallCaps.convert("\u00a7c\u00a7l\u26a0 \u00a7fTienes items en vaults bloqueados. \u00a7cRetiralos en \u00a7f" + timeLeft + " \u00a7co seran eliminados."),
                BarColor.RED, BarStyle.SOLID);
        bar.addPlayer(player);
        bar.setVisible(true);

        // Remover despues de 30 segundos
        new BukkitRunnable() {
            @Override
            public void run() {
                bar.removeAll();
                bar.setVisible(false);
            }
        }.runTaskLater(plugin, 20L * 30);
    }

    private String formatTimeLeftShort(long ms) {
        if (ms <= 0) return "EXPIRADO";
        long days = ms / (24 * 60 * 60 * 1000);
        long hours = (ms % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000);
        if (days > 0) return days + "d " + hours + "h";
        long minutes = (ms % (60 * 60 * 1000)) / (60 * 1000);
        return hours + "h " + minutes + "m";
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player))
            return;
        Player player = (Player) event.getPlayer();

        String title = event.getView().getTitle();
        PrivateVaultManager manager = plugin.getPrivateVaultManager();

        // Si se cierra el selector, no reabrir
        if (title.equals(PrivateVaultManager.SELECTOR_TITLE)) {
            return;
        }

        int vaultNumber = manager.getVaultNumberFromTitle(title);

        if (vaultNumber < 1)
            return;

        // Guardar contenido del vault
        manager.saveVault(player.getUniqueId(), vaultNumber, event.getInventory());

        // Si estamos reabriendo el selector, no hacer nada extra
        if (reopeningSelector.contains(player.getUniqueId())) {
            return;
        }

        // Reabrir el menu de vaults con 1 tick de delay
        reopeningSelector.add(player.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            reopeningSelector.remove(player.getUniqueId());
            if (player.isOnline()) {
                manager.openVaultSelector(player);
            }
        }, 1L);
    }

    @EventHandler
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (event.getView().getTitle() == null)
            return;
        if (!event.getView().getTitle().equals(PrivateVaultManager.SELECTOR_TITLE))
            return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player))
            return;
        Player player = (Player) event.getWhoClicked();
        org.bukkit.inventory.ItemStack clicked = event.getCurrentItem();

        if (clicked == null || clicked.getType() == org.bukkit.Material.AIR)
            return;

        int slot = event.getRawSlot();
        PrivateVaultManager manager = plugin.getPrivateVaultManager();
        
        // Usar el mapping centralizado del manager
        int vaultNumber = manager.getVaultNumberFromSlot(slot);
        
        if (vaultNumber != -1) {
            // Right-click en vault bloqueado con items (CHEST) -> empaquetar en shulkers
            if (event.getClick() == ClickType.RIGHT && clicked.getType() == org.bukkit.Material.CHEST) {
                // Verificar si este vault esta bloqueado (solo empaquetar bloqueados)
                if (manager.isVaultBlocked(player, vaultNumber)) {
                    player.closeInventory();
                    manager.packVaultToShulkers(player, vaultNumber);
                    // Reabrir selector despues de empaquetar
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline()) {
                            manager.openVaultSelector(player);
                        }
                    }, 2L);
                    return;
                }
            }

            // Vault desbloqueado - abrir (CHEST = desbloqueado, incluyendo los de rango con items)
            if (clicked.getType() == org.bukkit.Material.CHEST) {
                player.closeInventory();
                manager.openVault(player, vaultNumber);
                return;
            }
            
            // Vault comprable con esencias+prestigio
            if (clicked.getType() == org.bukkit.Material.LIME_STAINED_GLASS_PANE || 
                clicked.getType() == org.bukkit.Material.ORANGE_STAINED_GLASS_PANE) {
                
                if (manager.purchaseSlot(player)) {
                    org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline()) {
                            manager.openVaultSelector(player);
                        }
                    }, 2L);
                } else {
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                }
                return;
            }
            
            // Vault bloqueado (barrier = no tiene rango ni items)
            if (clicked.getType() == org.bukkit.Material.BARRIER) {
                if (vaultNumber >= 2 && vaultNumber <= 5) {
                    player.sendMessage(SmallCaps.convert("\u00a7c\u00a7l\u2716 \u00a7cNecesitas un rango VIP para este vault."));
                    player.sendMessage(SmallCaps.convert("\u00a77Compra tu rango en \u00a7e/tienda\u00a77."));
                } else {
                    player.sendMessage(SmallCaps.convert("\u00a7c\u00a7l\u2716 \u00a7cEste vault esta bloqueado."));
                }
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        String title = event.getView().getTitle();
        if (title != null && title.equals(PrivateVaultManager.SELECTOR_TITLE)) {
            event.setCancelled(true);
        }
    }
}
