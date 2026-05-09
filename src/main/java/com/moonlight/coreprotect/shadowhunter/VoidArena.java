package com.moonlight.coreprotect.shadowhunter;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Gestiona la arena en el cielo — esfera de hormigón negro a Y=200.
 * La esfera es irrompible hasta que el boss muera.
 * Se genera al iniciar el boss y se destruye al terminar.
 */
public class VoidArena {

    private final CoreProtectPlugin plugin;
    private final Player player;
    private final Set<Location> arenaBlocks = new HashSet<>();
    private Location center;
    private boolean active = false;
    private static final int RADIUS = 12;
    private static final int ARENA_Y = 200;

    // Locaciones de protección
    private static final Set<UUID> protectedArenas = new HashSet<>();

    public VoidArena(CoreProtectPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    /**
     * Genera la esfera de hormigón negro en el cielo y teletransporta al jugador.
     */
    public void build(Runnable onComplete) {
        World world = player.getWorld();
        center = findSafeArenaLocation(player.getLocation().getBlockX(), player.getLocation().getBlockZ(), world);

        active = true;
        protectedArenas.add(player.getUniqueId());

        // Efecto de ascensión dramático
        player.sendTitle( SmallCaps.convert("§0§l⬆ ASCENSIÓN ⬆"), SmallCaps.convert("§7El vacío te reclama..."), 10, 60, 20);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 0.5f);
        player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 0.6f, 2.0f);

        // Expandir dominio: partículas negras subiendo desde el jugador
        new BukkitRunnable() {
            int ticks = 0;
            double y = player.getLocation().getY();

            @Override
            public void run() {
                if (ticks >= 80) {
                    cancel();
                    // Construir esfera
                    buildSphere(onComplete);
                    return;
                }

                Location pLoc = player.getLocation();
                World w = pLoc.getWorld();

                // Partículas de dominio oscuro expandiéndose
                double progress = ticks / 80.0;
                double r = 2.0 + progress * 8.0;
                for (int i = 0; i < 12; i++) {
                    double angle = Math.PI * 2.0 / 12 * i + ticks * 0.1;
                    double px = Math.cos(angle) * r * progress;
                    double pz = Math.sin(angle) * r * progress;
                    w.spawnParticle(Particle.DUST,
                            pLoc.clone().add(px, 1 + progress * 3, pz), 2, 0.1, 0.1, 0.1, 0,
                            new Particle.DustOptions(Color.fromRGB(5, 5, 5), 3.0f));
                    w.spawnParticle(Particle.SQUID_INK,
                            pLoc.clone().add(px * 0.5, 0.5 + progress * 2, pz * 0.5), 1, 0.1, 0.1, 0.1, 0.02);
                }

                // Sonido creciente
                if (ticks % 10 == 0) {
                    w.playSound(pLoc, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.5f, 0.3f + (float) progress);
                }

                // Elevar al jugador progresivamente en los últimos ticks
                if (ticks >= 60) {
                    player.setVelocity(player.getVelocity().setY(1.2));
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private void buildSphere(Runnable onComplete) {
        World world = center.getWorld();

        // Generar esfera hueca de hormigón negro
        new BukkitRunnable() {
            int layer = -RADIUS;

            @Override
            public void run() {
                if (layer > RADIUS) {
                    cancel();

                    // Llenar interior con aire (asegurar espacio libre)
                    for (int x = -(RADIUS - 2); x <= (RADIUS - 2); x++) {
                        for (int y2 = -(RADIUS - 2); y2 <= (RADIUS - 2); y2++) {
                            for (int z = -(RADIUS - 2); z <= (RADIUS - 2); z++) {
                                double dist = Math.sqrt(x * x + y2 * y2 + z * z);
                                if (dist < RADIUS - 1.5) {
                                    Block b = world.getBlockAt(
                                            center.getBlockX() + x,
                                            center.getBlockY() + y2,
                                            center.getBlockZ() + z);
                                    if (b.getType() == Material.BLACK_CONCRETE)
                                        continue;
                                    b.setType(Material.AIR);
                                }
                            }
                        }
                    }

                    // Suelo: plataforma interior
                    for (int x = -(RADIUS - 3); x <= (RADIUS - 3); x++) {
                        for (int z = -(RADIUS - 3); z <= (RADIUS - 3); z++) {
                            double dist = Math.sqrt(x * x + z * z);
                            if (dist < RADIUS - 2) {
                                Block floor = world.getBlockAt(
                                        center.getBlockX() + x,
                                        center.getBlockY() - RADIUS + 2,
                                        center.getBlockZ() + z);
                                floor.setType(Material.BLACK_CONCRETE);
                                arenaBlocks.add(floor.getLocation());
                            }
                        }
                    }

                    // Luz interior: techo (existente)
                    for (int dx = -3; dx <= 3; dx += 3) {
                        for (int dz = -3; dz <= 3; dz += 3) {
                            Block light = world.getBlockAt(
                                    center.getBlockX() + dx,
                                    center.getBlockY() + RADIUS - 3,
                                    center.getBlockZ() + dz);
                            light.setType(Material.SEA_LANTERN);
                            arenaBlocks.add(light.getLocation());
                        }
                    }

                    // Luz adicional: anillo de luces a media altura (paredes)
                    int midY = center.getBlockY();
                    int lowY = center.getBlockY() - RADIUS + 4; // cerca del suelo
                    for (int angle = 0; angle < 360; angle += 45) {
                        double rad = Math.toRadians(angle);
                        int lx = center.getBlockX() + (int)(Math.cos(rad) * (RADIUS - 2));
                        int lz = center.getBlockZ() + (int)(Math.sin(rad) * (RADIUS - 2));
                        // Luz a media altura
                        Block midLight = world.getBlockAt(lx, midY, lz);
                        midLight.setType(Material.SEA_LANTERN);
                        arenaBlocks.add(midLight.getLocation());
                        // Luz cerca del suelo
                        Block lowLight = world.getBlockAt(lx, lowY, lz);
                        lowLight.setType(Material.SEA_LANTERN);
                        arenaBlocks.add(lowLight.getLocation());
                    }

                    // Luz adicional: 4 luces en el suelo (incrustadas)
                    for (int dx = -4; dx <= 4; dx += 8) {
                        for (int dz = -4; dz <= 4; dz += 8) {
                            Block floorLight = world.getBlockAt(
                                    center.getBlockX() + dx,
                                    center.getBlockY() - RADIUS + 2,
                                    center.getBlockZ() + dz);
                            floorLight.setType(Material.SEA_LANTERN);
                            arenaBlocks.add(floorLight.getLocation());
                        }
                    }

                    // Teletransportar jugador al centro de la arena
                    Location tp = center.clone().add(0, -(RADIUS - 4), 0);
                    tp.setPitch(0);
                    tp.setYaw(player.getLocation().getYaw());
                    player.setMetadata("quest_teleport", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                    player.teleport(tp);
                    player.removeMetadata("quest_teleport", plugin);

                    player.sendTitle( SmallCaps.convert("§0§l✦ LA ARENA DEL VACÍO ✦"), SmallCaps.convert("§7No hay escapatoria..."), 10, 60, 20);
                    world.playSound(center, Sound.ENTITY_WARDEN_EMERGE, 1.0f, 0.5f);
                    world.playSound(center, Sound.BLOCK_END_PORTAL_SPAWN, 0.5f, 1.5f);

                    if (onComplete != null) {
                        Bukkit.getScheduler().runTaskLater(plugin, onComplete, 40L);
                    }
                    return;
                }

                // Construir una capa de la esfera
                for (int x = -RADIUS; x <= RADIUS; x++) {
                    for (int z = -RADIUS; z <= RADIUS; z++) {
                        double dist = Math.sqrt(x * x + layer * layer + z * z);
                        if (dist >= RADIUS - 1 && dist <= RADIUS) {
                            Block b = world.getBlockAt(
                                    center.getBlockX() + x,
                                    center.getBlockY() + layer,
                                    center.getBlockZ() + z);
                            b.setType(Material.BLACK_CONCRETE);
                            arenaBlocks.add(b.getLocation());
                        }
                    }
                }

                // Sonido de construcción
                if (layer % 3 == 0) {
                    world.playSound(center.clone().add(0, layer, 0),
                            Sound.BLOCK_DEEPSLATE_PLACE, 0.3f, 0.5f);
                }

                layer++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    /**
     * Destruye la arena con efecto de colapso.
     */
    public void destroy() {
        if (!active)
            return;
        active = false;
        protectedArenas.remove(player.getUniqueId());

        World world = center.getWorld();

        player.sendTitle( SmallCaps.convert("§7§l⬇ DESCENSO ⬇"), SmallCaps.convert("§7El vacío te libera..."), 10, 40, 20);

        // Teletransportar jugador de vuelta al suelo primero
        // Buscar suelo seguro debajo de la arena
        Location ground = player.getLocation().clone();
        ground.setY(64);
        for (int y = 200; y > 0; y--) {
            Block b = world.getBlockAt(ground.getBlockX(), y, ground.getBlockZ());
            if (b.getType().isSolid() && b.getType() != Material.BLACK_CONCRETE) {
                ground.setY(y + 1);
                break;
            }
        }
        player.setMetadata("quest_teleport", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
        player.teleport(ground);
        player.removeMetadata("quest_teleport", plugin);

        // Destruir la esfera con efecto de colapso
        new BukkitRunnable() {
            int step = 0;
            List<Location> toRemove = new ArrayList<>(arenaBlocks);

            @Override
            public void run() {
                int batchSize = Math.min(100, toRemove.size() - step * 100);
                if (batchSize <= 0) {
                    cancel();
                    arenaBlocks.clear();
                    return;
                }

                for (int i = 0; i < batchSize; i++) {
                    int idx = step * 100 + i;
                    if (idx >= toRemove.size())
                        break;
                    Location loc = toRemove.get(idx);
                    Block b = loc.getBlock();
                    if (b.getType() == Material.BLACK_CONCRETE || b.getType() == Material.SEA_LANTERN) {
                        b.setType(Material.AIR);
                        world.spawnParticle(Particle.SMOKE, loc.clone().add(0.5, 0.5, 0.5),
                                2, 0.2, 0.2, 0.2, 0.02);
                    }
                }

                step++;
            }
        }.runTaskTimer(plugin, 20L, 2L);
    }

    public Location getCenter() {
        return center;
    }

    public Location getFloorCenter() {
        if (center == null)
            return null;
        return center.clone().add(0, -(RADIUS - 4), 0);
    }

    public boolean isActive() {
        return active;
    }

    public boolean isArenaBlock(Location loc) {
        return arenaBlocks.contains(loc);
    }

    public static boolean isPlayerInProtectedArena(UUID uid) {
        return protectedArenas.contains(uid);
    }

    /**
     * Encuentra una ubicación segura para la arena que no colisione con otras arenas.
     */
    private Location findSafeArenaLocation(int startX, int startZ, World world) {
        // Distancia mínima entre arenas para evitar colisiones
        final int MIN_DISTANCE = (RADIUS + 5) * 2; // 34 bloques de separación
        
        // Buscar en espiral desde la posición original
        for (int radius = 0; radius <= 200; radius += 10) {
            for (int angle = 0; angle < 360; angle += 30) {
                double rad = Math.toRadians(angle);
                int x = startX + (int)(Math.cos(rad) * radius);
                int z = startZ + (int)(Math.sin(rad) * radius);
                
                Location candidate = new Location(world, x, ARENA_Y, z);
                
                // Verificar que no esté demasiado cerca de otras arenas
                boolean safe = true;
                for (UUID otherUid : protectedArenas) {
                    if (otherUid.equals(player.getUniqueId())) continue;
                    
                    // Aquí necesitaríamos acceso a las otras arenas activas
                    // Por ahora usamos una heurística simple
                    if (Math.abs(candidate.getBlockX() - startX) < MIN_DISTANCE && 
                        Math.abs(candidate.getBlockZ() - startZ) < MIN_DISTANCE) {
                        safe = false;
                        break;
                    }
                }
                
                if (safe) {
                    return candidate;
                }
            }
        }
        
        // Fallback: posición original con offset
        return new Location(world, startX + 50, ARENA_Y, startZ + 50);
    }

    public void cleanup() {
        if (!active)
            return;
        active = false;
        protectedArenas.remove(player.getUniqueId());
        World world = center != null ? center.getWorld() : null;
        if (world != null) {
            for (Location loc : arenaBlocks) {
                Block b = loc.getBlock();
                if (b.getType() == Material.BLACK_CONCRETE || b.getType() == Material.SEA_LANTERN) {
                    b.setType(Material.AIR);
                }
            }
        }
        arenaBlocks.clear();
    }
}
