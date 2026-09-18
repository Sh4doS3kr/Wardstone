package com.moonlight.coreprotect.streak;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DailyStreakListener implements Listener {

    private final CoreProtectPlugin plugin;
    private int reminderCycle = 0;

    // Mapa estático: UUID -> timestamp hasta el cual el action bar de racha tiene prioridad
    private static final Map<UUID, Long> actionBarOverride = new ConcurrentHashMap<>();
    private static final long OVERRIDE_DURATION_MS = 10000; // 10 segundos

    /**
     * Comprueba si un jugador tiene un action bar prioritario activo
     * (racha, vault warning, etc.) para que la zona no lo sobreescriba.
     */
    public static boolean hasActiveOverride(UUID uuid) {
        Long until = actionBarOverride.get(uuid);
        if (until == null) return false;
        if (System.currentTimeMillis() > until) {
            actionBarOverride.remove(uuid);
            return false;
        }
        return true;
    }

    /**
     * Marca el action bar de un jugador como prioritario durante la duración configurada.
     * Puede ser llamado desde otros sistemas (vault warnings, etc.)
     */
    public static void setActionBarOverride(UUID uuid, long durationMs) {
        actionBarOverride.put(uuid, System.currentTimeMillis() + durationMs);
    }

    private void setStreakBarActive(UUID uuid) {
        actionBarOverride.put(uuid, System.currentTimeMillis() + OVERRIDE_DURATION_MS);
    }

    public DailyStreakListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        startStreakReminder();
    }

    private void startStreakReminder() {
        new BukkitRunnable() {
            @Override
            public void run() {
                DailyStreakManager mgr = plugin.getDailyStreakManager();
                if (mgr == null) return;
                reminderCycle++;

                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!mgr.canClaimToday(player.getUniqueId())) continue;

                    DailyStreakManager.StreakData sd = mgr.getStreak(player.getUniqueId());

                    // Action bar cada 2 minutos — con override para que no se pise
                    String actionMsg = SmallCaps.convert("§6§l⭐ §e¡Racha diaria disponible! §7Usa §f/racha §7para reclamar §8(Racha: §e" + sd.currentStreak + "§8)");
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(actionMsg));
                    setStreakBarActive(player.getUniqueId());

                    // Chat cada ciclo (2 min) — recordatorio breve
                    player.sendMessage(SmallCaps.convert("§6§l⭐ §e¡Racha diaria disponible! §7Usa §f/racha §7(Racha: §e" + sd.currentStreak + "§7)"));

                    // Chat completo cada 10 minutos (cada 5 ciclos de 2 min)
                    if (reminderCycle % 5 == 0) {
                        boolean broken = mgr.isStreakBroken(player.getUniqueId());
                        player.sendMessage("");
                        player.sendMessage(SmallCaps.convert("§6§l⭐ §e§l¡RECUERDA RECLAMAR TU RACHA DIARIA!"));
                        if (broken) {
                            player.sendMessage(SmallCaps.convert("§c§l⚠ ¡Tu racha se romperá si no reclamas hoy!"));
                        } else {
                            player.sendMessage(SmallCaps.convert("§fRacha actual: §e" + sd.currentStreak + " días §7— §f¡No la pierdas!"));
                        }
                        player.sendMessage(SmallCaps.convert("§7Usa §f/racha §7para reclamar tu recompensa."));
                        player.sendMessage("");
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.7f, 1.5f);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L * 120, 20L * 120); // Cada 2 minutos
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        DailyStreakManager mgr = plugin.getDailyStreakManager();
        if (mgr == null) return;

        if (!mgr.canClaimToday(player.getUniqueId())) return;

        // Queue notification — fires after resource pack is loaded/declined
        com.moonlight.coreprotect.listeners.ResourcePackListener.queueOrFire(plugin, player, () -> {
            if (!player.isOnline()) return;
            DailyStreakManager.StreakData sd = mgr.getStreak(player.getUniqueId());
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("§6§l⭐ §e§l¡RACHA DIARIA DISPONIBLE!"));
            player.sendMessage(SmallCaps.convert("§fRacha actual: §e" + sd.currentStreak + " días"));
            if (mgr.isStreakBroken(player.getUniqueId())) {
                player.sendMessage(SmallCaps.convert("§c§l¡Tu racha se ha roto! §7Empieza de nuevo."));
            }
            player.sendMessage(SmallCaps.convert("§7Usa §f/racha §7para reclamar tu recompensa."));
            player.sendMessage("");
            player.sendTitle(
                SmallCaps.convert("§e§l¡RACHA DIARIA!"),
                SmallCaps.convert("§7Usa §f/racha §7para reclamar"),
                10, 70, 20
            );
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle() == null) return;
        String viewTitle = event.getView().getTitle();

        // Top Rachas GUI
        if (viewTitle.equals(DailyStreakManager.TOP_TITLE)) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player)) return;
            // Back button
            if (event.getRawSlot() == 49 && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.ARROW) {
                plugin.getDailyStreakManager().openGUI((Player) event.getWhoClicked(), 0);
            }
            return;
        }

        if (!viewTitle.startsWith(DailyStreakManager.GUI_TITLE)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        DailyStreakManager mgr = plugin.getDailyStreakManager();
        if (mgr == null) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        String title = event.getView().getTitle();

        // Parse current page from title
        int currentPage = 0;
        try {
            String pageStr = title.substring(title.lastIndexOf("(") + 1, title.lastIndexOf("/"));
            currentPage = Integer.parseInt(pageStr.trim()) - 1;
        } catch (Exception ignored) {}

        // Claim button (slot 49)
        if (slot == 49 && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.EMERALD) {
            player.closeInventory();
            mgr.claimDaily(player);
            return;
        }

        // Previous page (slot 45)
        if (slot == 45 && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.ARROW) {
            mgr.openGUI(player, currentPage - 1);
            return;
        }

        // Next page (slot 53)
        if (slot == 53 && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.ARROW) {
            mgr.openGUI(player, currentPage + 1);
            return;
        }

        // Top button (slot 51)
        if (slot == 51 && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.DIAMOND) {
            mgr.openTopGUI(player);
            return;
        }

        // Click on SUNFLOWER (current day) to claim
        if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.SUNFLOWER) {
            player.closeInventory();
            mgr.claimDaily(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTitle() != null &&
                (event.getView().getTitle().startsWith(DailyStreakManager.GUI_TITLE) ||
                 event.getView().getTitle().equals(DailyStreakManager.TOP_TITLE))) {
            event.setCancelled(true);
        }
    }
}
