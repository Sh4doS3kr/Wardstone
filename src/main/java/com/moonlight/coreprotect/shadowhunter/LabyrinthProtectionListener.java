package com.moonlight.coreprotect.shadowhunter;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Protege los bloques del laberinto de Misión 7 para que no se puedan romper ni colocar.
 * También fuerza el respawn dentro del laberinto al morir.
 */
public class LabyrinthProtectionListener implements Listener {

    private final CoreProtectPlugin plugin;
    private final Map<UUID, Location> deathLocations = new HashMap<>();

    public LabyrinthProtectionListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isLabyrinthWorld(World world) {
        if (world == null) return false;
        String worldName = world.getName();
        return worldName.startsWith("labyrinth_");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isLabyrinthWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§4§l✖ §cNo puedes romper bloques en el Vientre del Vacío.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isLabyrinthWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§4§l✖ §cNo puedes colocar bloques en el Vientre del Vacío.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (isLabyrinthWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.getLocation() != null && isLabyrinthWorld(event.getLocation().getWorld())) {
            event.setCancelled(true);
        }
    }

    // Save death location when dying inside the labyrinth
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (isLabyrinthWorld(player.getWorld())) {
            deathLocations.put(player.getUniqueId(), player.getLocation().clone());
            // Keep inventory is already set via gamerule, but ensure no drops
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.getDrops().clear();
            event.setDroppedExp(0);
            // Custom death message
            event.setDeathMessage(null);
            player.sendMessage("§4§l☠ §c§oLa oscuridad te consume... pero no te deja escapar.");
        }
    }

    // Force respawn inside the labyrinth
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();
        Location deathLoc = deathLocations.remove(uid);

        if (deathLoc != null && deathLoc.getWorld() != null && isLabyrinthWorld(deathLoc.getWorld())) {
            // Respawn at the death location inside the labyrinth
            event.setRespawnLocation(deathLoc);

            // Re-apply labyrinth effects after a tick (post-respawn)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 999999, 0, false, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 999999, 0, false, false, false));
                player.sendMessage("§4§o...el Vientre del Vacío no deja ir a sus presas...");
                player.sendMessage("§8§o...levántate... sigue caminando... o quédate aquí para siempre...");
            }, 2L);
        }
    }
}
