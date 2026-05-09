package com.moonlight.coreprotect.protection;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Gestiona la no-colisión de jugadores dentro de la zona protegida del spawn.
 * Previene que los jugadores se empujen entre sí hacia la zona PvP.
 */
public class SpawnCollisionManager implements Listener {
    
    private final CoreProtectPlugin plugin;
    private final ProtectionManager protectionManager;
    private Team noCollisionTeam;
    private final Set<UUID> playersInNoCollision = new HashSet<>();
    
    // Coordenadas de la zona protegida del spawn (mismo que isSpawnProtected)
    private static final double MIN_X = -101;
    private static final double MAX_X = 101;
    private static final double MIN_Z = -66;
    private static final double MAX_Z = 136;
    
    public SpawnCollisionManager(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        this.protectionManager = plugin.getProtectionManager();
        setupTeam();
        startUpdateTask();
    }
    
    /**
     * Configura el team de scoreboard para no-colisión
     */
    private void setupTeam() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        
        // Eliminar team existente si existe
        Team existingTeam = scoreboard.getTeam("spawn_nocollision");
        if (existingTeam != null) {
            existingTeam.unregister();
        }
        
        // Crear nuevo team
        noCollisionTeam = scoreboard.registerNewTeam("spawn_nocollision");
        noCollisionTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        
        plugin.getLogger().info("[SpawnCollision] Team de no-colisión creado");
    }
    
    /**
     * Inicia la tarea que actualiza constantemente qué jugadores están en la zona protegida
     */
    private void startUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updatePlayerCollision(player);
                }
            }
        }.runTaskTimer(plugin, 0L, 5L); // Cada 0.25 segundos (5 ticks)
    }
    
    /**
     * Actualiza el estado de colisión de un jugador basado en su ubicación
     */
    private void updatePlayerCollision(Player player) {
        if (!player.isOnline()) return;
        
        Location loc = player.getLocation();
        boolean isInSpawn = isInSpawnProtectedZone(loc);
        UUID uuid = player.getUniqueId();
        
        if (isInSpawn && !playersInNoCollision.contains(uuid)) {
            // Jugador entró a la zona protegida - añadir al team
            addPlayerToNoCollision(player);
        } else if (!isInSpawn && playersInNoCollision.contains(uuid)) {
            // Jugador salió de la zona protegida - remover del team
            removePlayerFromNoCollision(player);
        }
    }
    
    /**
     * Añade un jugador al team de no-colisión
     */
    private void addPlayerToNoCollision(Player player) {
        if (noCollisionTeam == null) {
            setupTeam(); // Re-crear team si fue eliminado
        }
        
        try {
            noCollisionTeam.addEntry(player.getName());
            playersInNoCollision.add(player.getUniqueId());
        } catch (Exception e) {
            plugin.getLogger().warning("[SpawnCollision] Error añadiendo jugador al team: " + e.getMessage());
        }
    }
    
    /**
     * Remueve un jugador del team de no-colisión
     */
    private void removePlayerFromNoCollision(Player player) {
        if (noCollisionTeam == null) return;
        
        try {
            noCollisionTeam.removeEntry(player.getName());
            playersInNoCollision.remove(player.getUniqueId());
        } catch (Exception e) {
            plugin.getLogger().warning("[SpawnCollision] Error removiendo jugador del team: " + e.getMessage());
        }
    }
    
    /**
     * Verifica si una ubicación está dentro de la zona protegida del spawn
     */
    private boolean isInSpawnProtectedZone(Location loc) {
        if (loc.getWorld() == null) return false;
        
        // Ignorar mundos especiales
        String worldName = loc.getWorld().getName().toLowerCase();
        if (worldName.contains("bossarena") || worldName.equals("minigames")) {
            return false;
        }
        
        double x = loc.getX();
        double z = loc.getZ();
        
        return (x >= MIN_X && x <= MAX_X) && (z >= MIN_Z && z <= MAX_Z);
    }
    
    /**
     * Limpia el jugador cuando se desconecta
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removePlayerFromNoCollision(event.getPlayer());
    }
    
    /**
     * Limpia todos los jugadores y el team al desactivar el plugin
     */
    public void cleanup() {
        if (noCollisionTeam != null) {
            try {
                noCollisionTeam.unregister();
            } catch (Exception e) {
                plugin.getLogger().warning("[SpawnCollision] Error al limpiar team: " + e.getMessage());
            }
        }
        playersInNoCollision.clear();
    }
}
