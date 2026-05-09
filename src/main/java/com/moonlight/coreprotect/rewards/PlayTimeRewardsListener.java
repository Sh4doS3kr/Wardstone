package com.moonlight.coreprotect.rewards;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayTimeRewardsListener implements Listener {

    private final PlayTimeRewardsManager rewardsManager;

    public PlayTimeRewardsListener(PlayTimeRewardsManager rewardsManager) {
        this.rewardsManager = rewardsManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        rewardsManager.onPlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        rewardsManager.onPlayerQuit(event.getPlayer());
    }

    /**
     * Listener con MONITOR priority para cancelar eventos del plugin externo de playtime.
     * Se ejecuta después de todos los otros listeners (incluido el plugin externo),
     * anulando cualquier efecto que haya intentado hacer.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerQuitMonitor(PlayerQuitEvent event) {
        // Este listener se ejecuta después del plugin externo de playtime.
        // Al cancelar el evento, prevenimos que el plugin externo guarde su tiempo
        // o procese recompensas, forzando que solo Wardstone maneje el playtime.
        event.setQuitMessage(null); // Cancela cualquier mensaje de quit del plugin externo
    }
}
