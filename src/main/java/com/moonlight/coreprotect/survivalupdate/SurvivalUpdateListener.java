package com.moonlight.coreprotect.survivalupdate;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

/**
 * Listener del sistema Survival Update:
 *  - Intercepta End Portal en el lobby → RTP al mundo survival
 *  - Intercepta Nether Portal en el lobby
 *  - Intercepta /rtp del plugin externo → redirige al mundo survival
 *  - Maneja join/respawn → enviar al lobby spawn
 *  - Protección total del lobby: no romper, no construir, no interactuar, no PvP, no daño
 */
public class SurvivalUpdateListener implements Listener {

    private final CoreProtectPlugin plugin;
    private final SurvivalUpdateManager manager;

    private static final String SURVIVAL_WORLD = "world";

    public SurvivalUpdateListener(CoreProtectPlugin plugin, SurvivalUpdateManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    // ==================== END PORTAL → RTP ====================

    /**
     * Intercepta cuando un jugador entra en un End Portal.
     * Si está en el mundo lobby y cerca de un portal RTP registrado,
     * cancela el viaje al End y lo manda a coordenadas aleatorias en "world".
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        World fromWorld = player.getWorld();

        // Solo interceptar en el mundo lobby
        if (!manager.isLobbyWorld(fromWorld)) return;

        // ═══ END PORTAL → RTP ═══
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            // Verificar si está cerca de un portal RTP registrado
            if (manager.isNearRtpPortal(player.getLocation())) {
                event.setCancelled(true);

                // Efecto visual y sonido antes del TP
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);
                player.sendTitle(
                        SmallCaps.convert("§a§l✦ TELETRANSPORTE ✦"),
                        SmallCaps.convert("§7Buscando ubicación segura..."),
                        10, 40, 10);

                // RTP asíncrono con delay para el efecto
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Location rtpLoc = manager.getRandomRtpLocation();
                    if (rtpLoc == null) {
                        player.sendMessage(SmallCaps.convert("§c§l✖ §cError al encontrar una ubicación segura. Intenta de nuevo."));
                        return;
                    }

                    player.teleport(rtpLoc);
                    player.playSound(rtpLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
                    player.playSound(rtpLoc, Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.5f);
                    player.sendTitle(
                            SmallCaps.convert("§a§l✦ SURVIVAL ✦"),
                            SmallCaps.convert("§7¡Explora el mundo!"),
                            5, 30, 10);
                    player.sendMessage(SmallCaps.convert("§a§l✔ §aTeletransportado a §e" +
                            rtpLoc.getBlockX() + "§a, §e" + rtpLoc.getBlockY() + "§a, §e" + rtpLoc.getBlockZ()));

                    plugin.getLogger().info("[SurvivalUpdate] RTP: " + player.getName() + " → " +
                            rtpLoc.getBlockX() + ", " + rtpLoc.getBlockY() + ", " + rtpLoc.getBlockZ());
                }, 15L); // 0.75 segundos de delay para el efecto

                return;
            }
        }

    }

    // ==================== INTERCEPTAR /RTP DE PLUGIN EXTERNO ====================

    /**
     * Si un plugin externo hace /rtp y teletransporta al jugador a algún lugar
     * del mundo lobby, lo redirigimos al mundo survival en las mismas coords.
     * Si el jugador está en el lobby y es teleportado por PLUGIN a coords del lobby,
     * redirigir al mundo survival.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        // Solo interceptar teleports causados por plugins o comandos
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN
                && event.getCause() != PlayerTeleportEvent.TeleportCause.COMMAND) return;

        Location to = event.getTo();
        if (to == null || to.getWorld() == null) return;

        Player player = event.getPlayer();

        // Si el jugador está en el lobby y se intenta tp a coordenadas del lobby
        // (probablemente /rtp), redirigir al mundo survival con las mismas coords
        if (manager.isLobbyWorld(to.getWorld())) {
            // Detectar si es un RTP (distancia > 100 del spawn o del jugador)
            Location lobbySpawn = manager.getLobbySpawn();
            double distFromSpawn = lobbySpawn != null ? to.distanceSquared(lobbySpawn) : 10000;
            double distFromPlayer = player.getLocation().distanceSquared(to);

            // Si es un teleport lejano (probablemente /rtp), redirigir al survival
            if (distFromPlayer > 10000 || distFromSpawn > 10000) { // > 100 bloques
                World survivalWorld = Bukkit.getWorld(SURVIVAL_WORLD);
                if (survivalWorld != null) {
                    Location redirected = new Location(survivalWorld, to.getX(), to.getY(), to.getZ(), to.getYaw(), to.getPitch());
                    // Encontrar Y seguro en el mundo survival
                    int safeY = survivalWorld.getHighestBlockYAt(redirected.getBlockX(), redirected.getBlockZ());
                    redirected.setY(safeY + 1);
                    event.setTo(redirected);
                    plugin.getLogger().info("[SurvivalUpdate] RTP redirect: " + player.getName()
                            + " lobby → world " + redirected.getBlockX() + ", " + redirected.getBlockY() + ", " + redirected.getBlockZ());
                }
            }
        }
    }

    // ==================== PROTECCIÓN DEL LOBBY ====================

    /**
     * No se pueden romper bloques en el lobby (excepto OPs).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!manager.isLobbyWorld(event.getBlock().getWorld())) return;
        if (event.getPlayer().isOp()) return;
        event.setCancelled(true);
    }

    /**
     * No se pueden colocar bloques en el lobby (excepto OPs).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!manager.isLobbyWorld(event.getBlock().getWorld())) return;
        if (event.getPlayer().isOp()) return;
        event.setCancelled(true);
    }

    /**
     * No se puede interactuar con bloques en el lobby (excepto OPs).
     * Permite interacción con End Portal Frames y Nether Portals (para entrar).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        if (!manager.isLobbyWorld(event.getClickedBlock().getWorld())) return;
        if (event.getPlayer().isOp()) return;

        Material type = event.getClickedBlock().getType();
        // Permitir interactuar con portales
        if (type == Material.END_PORTAL || type == Material.END_PORTAL_FRAME || type == Material.NETHER_PORTAL) return;

        event.setCancelled(true);
    }

    /**
     * No se puede interactuar con entidades en el lobby (excepto OPs).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!manager.isLobbyWorld(event.getPlayer().getWorld())) return;
        if (event.getPlayer().isOp()) return;
        event.setCancelled(true);
    }

    /**
     * No hay daño de ningún tipo en el lobby.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDamage(EntityDamageEvent event) {
        if (!manager.isLobbyWorld(event.getEntity().getWorld())) return;
        event.setCancelled(true);
    }

    /**
     * No hay PvP ni daño entre entidades en el lobby.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!manager.isLobbyWorld(event.getEntity().getWorld())) return;
        event.setCancelled(true);
    }

    /**
     * No se pueden tirar items en el lobby (excepto OPs).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDropItem(PlayerDropItemEvent event) {
        if (!manager.isLobbyWorld(event.getPlayer().getWorld())) return;
        if (event.getPlayer().isOp()) return;
        event.setCancelled(true);
    }

    /**
     * No hay hambre en el lobby.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (!manager.isLobbyWorld(event.getEntity().getWorld())) return;
        event.setCancelled(true);
        ((Player) event.getEntity()).setFoodLevel(20);
        ((Player) event.getEntity()).setSaturation(20f);
    }

    /**
     * No hay explosiones en el lobby.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (!manager.isLobbyWorld(event.getEntity().getWorld())) return;
        event.setCancelled(true);
        event.blockList().clear();
    }

    /**
     * No se pueden usar cubos en el lobby (excepto OPs).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!manager.isLobbyWorld(event.getPlayer().getWorld())) return;
        if (event.getPlayer().isOp()) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!manager.isLobbyWorld(event.getPlayer().getWorld())) return;
        if (event.getPlayer().isOp()) return;
        event.setCancelled(true);
    }

    /**
     * No se spawnean mobs en el lobby.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!manager.isLobbyWorld(event.getEntity().getWorld())) return;
        // Permitir mobs custom (MythicMobs, NPCs)
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) return;
        event.setCancelled(true);
    }

    // ==================== LAUNCHER PADS (SLIME BLOCKS) ====================

    private final java.util.Set<java.util.UUID> flyingPlayers = new java.util.HashSet<>();

    /**
     * Cuando un jugador camina sobre un Slime Block registrado como launcher pad,
     * inicia un vuelo guiado hacia las coordenadas destino con partículas y sonido
     * durante todo el trayecto. El jugador vuela suavemente hasta llegar.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Solo detectar cambio de bloque (optimización)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        Player player = event.getPlayer();
        if (!manager.isLobbyWorld(player.getWorld())) return;
        if (flyingPlayers.contains(player.getUniqueId())) return;

        Location target = manager.getLauncherTarget();
        if (target == null) return;

        if (manager.isOnLauncherPad(player.getLocation())) {
            launchPlayer(player, target);
        }
    }

    /**
     * Vuelo guiado: eleva al jugador, lo impulsa en arco hacia el destino,
     * y lo deposita suavemente. Partículas y sonido durante todo el trayecto.
     */
    private void launchPlayer(Player player, Location target) {
        flyingPlayers.add(player.getUniqueId());

        Location from = player.getLocation().clone();

        // Sonido de lanzamiento
        player.playSound(from, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 2.0f, 0.8f);
        player.playSound(from, Sound.BLOCK_PISTON_EXTEND, 2.0f, 1.5f);
        player.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, from.clone().add(0, 0.5, 0), 40, 0.5, 0.2, 0.5, 0.15);
        player.getWorld().spawnParticle(org.bukkit.Particle.FIREWORK, from.clone().add(0, 0.5, 0), 20, 0.3, 0.2, 0.3, 0.08);

        // Altura máxima del arco: punto medio + 15 bloques por encima del más alto
        double peakY = Math.max(from.getY(), target.getY()) + 20;

        // Calcular duración del vuelo en ticks (velocidad constante ~1.5 bloques/tick)
        double totalDist = from.distance(target);
        int totalTicks = Math.max(20, (int) (totalDist / 1.5));

        // Tarea repetitiva que mueve al jugador cada tick
        final int[] tickCounter = {0};
        org.bukkit.scheduler.BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                flyingPlayers.remove(player.getUniqueId());
                return;
            }

            tickCounter[0]++;
            double progress = (double) tickCounter[0] / totalTicks; // 0.0 → 1.0

            if (progress >= 1.0) {
                // Llegó al destino
                Location finalLoc = target.clone();
                finalLoc.setWorld(player.getWorld());
                finalLoc.setYaw(player.getLocation().getYaw());
                finalLoc.setPitch(player.getLocation().getPitch());
                player.teleport(finalLoc);
                player.setVelocity(new Vector(0, -0.5, 0)); // Aterrizaje suave
                player.setFallDistance(0);

                // Efectos de aterrizaje
                player.playSound(finalLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.5f);
                player.playSound(finalLoc, Sound.BLOCK_SLIME_BLOCK_FALL, 1.0f, 0.8f);
                player.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, finalLoc, 30, 0.8, 0.3, 0.8, 0.05);

                flyingPlayers.remove(player.getUniqueId());
                return;
            }

            // Interpolación de posición con arco parabólico en Y
            double x = from.getX() + (target.getX() - from.getX()) * progress;
            double z = from.getZ() + (target.getZ() - from.getZ()) * progress;

            // Y con arco: parábola que sube al medio y baja al final
            // peak en progress=0.4 para un arco natural
            double arcProgress = progress;
            double arcHeight = -4.0 * (arcProgress - 0.4) * (arcProgress - 0.4) + 4.0 * 0.16; // max = 0.64
            double baseY = from.getY() + (target.getY() - from.getY()) * progress;
            double y = baseY + arcHeight * (peakY - Math.max(from.getY(), target.getY()));

            Location flyPos = new Location(player.getWorld(), x, y, z,
                    player.getLocation().getYaw(), player.getLocation().getPitch());
            player.teleport(flyPos);
            player.setFallDistance(0);
            player.setVelocity(new Vector(0, 0, 0)); // Evitar gravedad

            // Partículas durante el vuelo (cada 2 ticks)
            if (tickCounter[0] % 2 == 0) {
                player.getWorld().spawnParticle(org.bukkit.Particle.FIREWORK, flyPos, 3, 0.2, 0.1, 0.2, 0.01);
                player.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, flyPos.clone().add(0, -0.5, 0), 2, 0.1, 0.05, 0.1, 0.01);
            }

            // Sonido de viento cada 5 ticks
            if (tickCounter[0] % 5 == 0) {
                player.playSound(flyPos, Sound.ENTITY_BREEZE_WIND_BURST, 0.3f, 1.5f);
            }
        }, 1L, 1L);

        // Seguridad: cancelar después de máximo 10 segundos
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (flyingPlayers.contains(player.getUniqueId())) {
                task.cancel();
                flyingPlayers.remove(player.getUniqueId());
                player.setFallDistance(0);
            }
        }, 200L);

        // Cancelar la tarea cuando el progress llegue a 1.0
        Bukkit.getScheduler().runTaskLater(plugin, task::cancel, totalTicks + 5L);
    }

    /**
     * Cancelar daño de caída para jugadores que acaban de ser lanzados.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLauncherFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        Player player = (Player) event.getEntity();
        if (!manager.isLobbyWorld(player.getWorld())) return;
        // Cancelar fall damage en el lobby siempre (ya está onDamage pero por seguridad)
        event.setCancelled(true);
    }

    // ==================== JOIN → LOBBY SPAWN ====================

    /**
     * Al unirse, si hay lobby configurado, enviarlo al spawn del lobby.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!manager.isSetupComplete()) return;

        Location lobbySpawn = manager.getLobbySpawn();
        if (lobbySpawn == null || lobbySpawn.getWorld() == null) return;

        Player player = event.getPlayer();

        // Solo teletransportar si no está ya en el lobby
        if (!manager.isLobbyWorld(player.getWorld())) {
            // Teleportar al lobby con un tick de delay para evitar problemas
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.teleport(lobbySpawn);
                }
            }, 5L);
        }
    }

    // ==================== RESPAWN → LOBBY SPAWN ====================

    /**
     * Al morir y respawnear, enviar al spawn del lobby.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!manager.isSetupComplete()) return;

        Location lobbySpawn = manager.getLobbySpawn();
        if (lobbySpawn == null || lobbySpawn.getWorld() == null) return;

        // Si estaba en el mundo survival, respawnear en el lobby
        if (event.getPlayer().getWorld().getName().equals(SURVIVAL_WORLD)) {
            event.setRespawnLocation(lobbySpawn);
        }
    }
}
