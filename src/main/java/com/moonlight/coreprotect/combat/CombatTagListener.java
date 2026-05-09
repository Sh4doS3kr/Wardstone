package com.moonlight.coreprotect.combat;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import com.moonlight.coreprotect.util.SmallCaps;

public class CombatTagListener implements Listener {

    private final CoreProtectPlugin plugin;
    private final CombatTagManager manager;

    public CombatTagListener(CoreProtectPlugin plugin, CombatTagManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    /**
     * Detecta PvP y tagea a ambos jugadores.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPvPDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player victim = (Player) event.getEntity();

        Player attacker = null;
        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile) {
            Projectile proj = (Projectile) event.getDamager();
            if (proj.getShooter() instanceof Player) {
                attacker = (Player) proj.getShooter();
            }
        }

        if (attacker == null || attacker.equals(victim)) return;

        // No tagear en mundo KOTH ni minijuegos (tienen su propio sistema)
        String worldName = victim.getWorld().getName();
        if (worldName.equals("moonkoth") || worldName.equals("koth_world") || worldName.equals("minigames")) return;

        // No tagear si alguno de los dos está en una zona protegida con NoPvP
        // (Fix para Wind Burst de maza que causa daño indirecto en zonas seguras)
        com.moonlight.coreprotect.core.ProtectedRegion victimRegion =
                plugin.getProtectionManager().getRegionAt(victim.getLocation());
        if (victimRegion != null && victimRegion.isNoPvP()) return;

        com.moonlight.coreprotect.core.ProtectedRegion attackerRegion =
                plugin.getProtectionManager().getRegionAt(attacker.getLocation());
        if (attackerRegion != null && attackerRegion.isNoPvP()) return;

        // No tagear en spawn core (zona segura), pero SÍ en el anillo PvP
        if (plugin.getProtectionManager().isSpawnCore(victim.getLocation())) return;
        if (plugin.getProtectionManager().isSpawnCore(attacker.getLocation())) return;

        manager.tagCombat(attacker, victim);
    }

    /**
     * Combat log: si un jugador se desconecta en combate, muere.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (manager.isInCombat(player.getUniqueId())) {
            manager.handleCombatLog(player);
        }
    }

    /**
     * Bloquear comandos durante PvP (excepto los permitidos).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!manager.isInCombat(player.getUniqueId())) return;

        // Admins pueden usar comandos
        if (player.hasPermission("wardstone.admin")) return;

        String command = event.getMessage();
        if (!manager.isCommandAllowed(command)) {
            event.setCancelled(true);
            player.sendMessage(SmallCaps.convert("§c§l⚔ §fNo puedes usar comandos durante el combate PvP."));
        }
    }

    /**
     * Barrera de 75 bloques: no alejarse del oponente.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Solo chequear si se movió de bloque (no solo girar la cabeza)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()) {
            return;
        }

        Player player = event.getPlayer();
        if (!manager.isInCombat(player.getUniqueId())) return;

        manager.handleMovement(player);
    }

    /**
     * Bloquear /fly y toggle flight durante combate.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!manager.isInCombat(player.getUniqueId())) return;

        // Permitir elytra gliding
        if (player.isGliding()) return;

        event.setCancelled(true);
        player.setFlying(false);
        player.setAllowFlight(false);
        player.sendMessage(SmallCaps.convert("§c§l⚔ §fNo puedes volar durante el combate PvP. §7(Elytras sí permitidas)"));
    }

    /**
     * Si un jugador muere, limpiar su combate y el de su oponente.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        if (!manager.isInCombat(dead.getUniqueId())) return;

        java.util.UUID opponentId = manager.getOpponent(dead.getUniqueId());
        manager.removeCombat(dead.getUniqueId());
        if (opponentId != null) {
            manager.removeCombat(opponentId);
        }
    }

    /**
     * Bloquear teletransporte durante combate (ender pearls ya dañan, pero portales etc).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (!manager.isInCombat(player.getUniqueId())) return;

        // Permitir ender pearls (son parte del PvP)
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) return;
        
        // Permitir teletransportes de finishers (usados para efectos visuales)
        if (player.hasMetadata("finisher_teleport")) return;

        // Admins pueden teleportarse
        if (player.hasPermission("wardstone.admin")) return;

        // Bloquear otros teleportes
        event.setCancelled(true);
        player.sendMessage(SmallCaps.convert("§c§l⚔ §fNo puedes teletransportarte durante el combate PvP."));
    }
}
