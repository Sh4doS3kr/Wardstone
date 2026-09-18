package com.moonlight.coreprotect.minigames.games;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.minigames.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Carrera Parkour - Parkour Race
 * Recorrido de parkour generado proceduralmente.
 * El primero en llegar al final gana. Si nadie llega en 3 minutos, gana el más avanzado.
 * Checkpoints cada sección. Caer = volver al último checkpoint.
 */
public class ParkourRaceGame extends MiniGame {

    private final Random random = new Random();
    private final Map<UUID, Integer> playerCheckpoints = new HashMap<>();
    private final Map<UUID, Location> checkpointLocations = new HashMap<>();
    private final List<Location> checkpoints = new ArrayList<>();
    private Location finishLocation;
    private static final int TOTAL_SECTIONS = 8;
    private static final int MAX_TIME = 180;

    public ParkourRaceGame(CoreProtectPlugin plugin, MiniGameManager manager) {
        super(plugin, manager, MiniGameType.PARKOUR_RACE);
    }

    @Override
    public void buildArena(World world) {
        buildParkourCourse(world);
    }

    @Override
    public List<Location> getSpawnLocations(World world) {
        List<Location> spawns = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            spawns.add(new Location(world, 0.5 + (i % 5) * 1.2 - 3, 81, 0.5 + (i / 5) * 1.2 - 2));
        }
        return spawns;
    }

    private void buildParkourCourse(World world) {
        // Plataforma de inicio
        for (int x = -4; x <= 4; x++) {
            for (int z = -3; z <= 3; z++) {
                world.getBlockAt(x, 80, z).setType(Material.QUARTZ_BLOCK);
                world.getBlockAt(x, 79, z).setType(Material.QUARTZ_BLOCK);
            }
        }

        // Cartel de inicio
        world.getBlockAt(0, 82, -3).setType(Material.GLOWSTONE);

        int currentX = 6;
        int currentY = 80;
        int currentZ = 0;

        Material[] platformMats = {
            Material.ORANGE_CONCRETE, Material.YELLOW_CONCRETE, Material.LIME_CONCRETE,
            Material.CYAN_CONCRETE, Material.BLUE_CONCRETE, Material.PURPLE_CONCRETE,
            Material.MAGENTA_CONCRETE, Material.RED_CONCRETE
        };

        for (int section = 0; section < TOTAL_SECTIONS; section++) {
            Material mat = platformMats[section % platformMats.length];
            int jumps = 5 + random.nextInt(4); // 5-8 saltos por sección

            for (int j = 0; j < jumps; j++) {
                // Dirección aleatoria del siguiente salto
                int dx = 2 + random.nextInt(3); // 2-4 bloques adelante (desafiante)
                int dz = random.nextInt(7) - 3; // -3 a 3 lateral (más variación)
                int dy = random.nextInt(4) - 1; // -1 a 2 vertical (requiere precisión)

                currentX += dx;
                currentZ += dz;
                currentY += dy;
                if (currentY < 75) currentY = 75;
                if (currentY > 95) currentY = 95;

                // Plataforma de 1-2 bloques (requiere precisión)
                int platSize = (j == jumps - 1) ? 2 : (random.nextInt(3) == 0 ? 2 : 1);

                for (int px = 0; px < platSize; px++) {
                    for (int pz = 0; pz < platSize; pz++) {
                        world.getBlockAt(currentX + px, currentY, currentZ + pz).setType(mat);
                    }
                }

                // Obstáculos ocasionales
                if (random.nextInt(4) == 0 && section >= 2) {
                    // Pared que hay que esquivar
                    world.getBlockAt(currentX, currentY + 1, currentZ).setType(Material.IRON_BARS);
                }

                // Saltos especiales en secciones avanzadas
                if (section >= 4 && random.nextInt(3) == 0) {
                    // Slime block (trampolín) - menos frecuente, más desafiante
                    world.getBlockAt(currentX, currentY, currentZ).setType(Material.SLIME_BLOCK);
                }
                
                // Plataformas de honey block para control de caída (más raras)
                if (section >= 6 && random.nextInt(5) == 0) {
                    world.getBlockAt(currentX, currentY, currentZ).setType(Material.HONEY_BLOCK);
                }
                
                // Obstáculos adicionales en secciones avanzadas
                if (section >= 3 && random.nextInt(5) == 0) {
                    // Valla que requiere saltar por encima
                    world.getBlockAt(currentX, currentY + 1, currentZ).setType(Material.OAK_FENCE);
                }
            }

            // Checkpoint al final de cada sección
            for (int cx = -1; cx <= 1; cx++) {
                for (int cz = -1; cz <= 1; cz++) {
                    world.getBlockAt(currentX + cx, currentY, currentZ + cz).setType(Material.GOLD_BLOCK);
                }
            }
            world.getBlockAt(currentX, currentY + 1, currentZ).setType(Material.BEACON);
            Location cp = new Location(world, currentX + 0.5, currentY + 1, currentZ + 0.5);
            checkpoints.add(cp);

            // Avanzar para la siguiente sección
            currentX += 4;
        }

        // Plataforma final
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                world.getBlockAt(currentX + x, currentY, currentZ + z).setType(Material.DIAMOND_BLOCK);
            }
        }
        world.getBlockAt(currentX, currentY + 1, currentZ).setType(Material.BEACON);
        world.getBlockAt(currentX, currentY + 2, currentZ).setType(Material.GLOWSTONE);
        finishLocation = new Location(world, currentX + 0.5, currentY + 1, currentZ + 0.5);
    }

    @Override
    public void startGameLogic() {
        broadcastGame("§a§l🏃 §e¡CARRERA PARKOUR! §7¡El primero en llegar a la meta gana!");
        broadcastGame("§a§l🏃 §7" + TOTAL_SECTIONS + " secciones con checkpoints. ¡Si caes, vuelves al checkpoint!");
        broadcastGame("§a§l🏃 §7Tiempo máximo: §c" + MAX_TIME + " segundos");
        broadcastGame("§a§l🏃 §b¡Jump Boost III activado! §7¡Salta súper alto!");
        titleAlive("§a§l🏃 PARKOUR RACE 🏃", "§7¡Corre y salta!");

        for (UUID uuid : alivePlayers) {
            playerCheckpoints.put(uuid, -1);
            checkpointLocations.put(uuid, getSpawnLocations(Bukkit.getWorld(MiniGameWorld.getWorldName())).get(0));
            
            // Dar Jump Boost III para hacer los saltos más fáciles
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.JUMP_BOOST, 999999, 2, false, false, false)); // Nivel 2 = Jump Boost III
            }
        }
    }

    @Override
    public void onTick() {
        World w = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (w == null) return;

        for (UUID uuid : new ArrayList<>(alivePlayers)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;

            // Comprobar si ha caído
            if (p.getLocation().getY() < 65) {
                Location cp = checkpointLocations.get(uuid);
                if (cp != null) {
                    p.teleport(cp);
                    p.sendTitle("§c§l¡CAÍSTE!", "§7Vuelves al checkpoint", 5, 20, 5);
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.0f);
                } else {
                    p.teleport(getSpawnLocations(w).get(0));
                }
                p.setFallDistance(0);
                continue;
            }

            // Comprobar checkpoints
            for (int i = 0; i < checkpoints.size(); i++) {
                Location cp = checkpoints.get(i);
                int current = playerCheckpoints.getOrDefault(uuid, -1);
                if (i > current && p.getLocation().distance(cp) < 3.0) {
                    playerCheckpoints.put(uuid, i);
                    checkpointLocations.put(uuid, cp.clone());
                    p.sendTitle("§6§l✔ Checkpoint " + (i + 1) + "/" + TOTAL_SECTIONS, "", 5, 20, 5);
                    p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                    w.spawnParticle(Particle.TOTEM_OF_UNDYING, cp, 15, 0.5, 0.5, 0.5, 0.1);
                    broadcastGame("§a§l🏃 §f" + p.getName() + " §7alcanzó checkpoint §e" + (i + 1) + "/" + TOTAL_SECTIONS);
                }
            }

            // Comprobar meta
            if (finishLocation != null && p.getLocation().distance(finishLocation) < 3.5) {
                broadcastGame("§a§l🏃 §e§l¡" + p.getName() + " ha llegado a la META!");
                endGame(p);
                return;
            }
        }

        // Info cada 15s
        if (gameTime % 15 == 0 && gameTime > 0) {
            StringBuilder sb = new StringBuilder("§a§lParkour §7| ");
            for (UUID uuid : alivePlayers) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null) continue;
                int cp = playerCheckpoints.getOrDefault(uuid, -1) + 1;
                sb.append("§f").append(p.getName()).append(": §e").append(cp).append("/").append(TOTAL_SECTIONS).append(" §7| ");
            }
            sb.append("§e").append(gameTime).append("s/").append(MAX_TIME).append("s");
            broadcastGame(sb.toString());
        }

        // Timeout
        if (gameTime >= MAX_TIME) {
            broadcastGame("§a§l🏃 §7¡Tiempo agotado! Gana el más avanzado.");
            UUID bestPlayer = null;
            int bestCp = -1;
            for (UUID uuid : alivePlayers) {
                int cp = playerCheckpoints.getOrDefault(uuid, -1);
                if (cp > bestCp) {
                    bestCp = cp;
                    bestPlayer = uuid;
                }
            }
            endGame(bestPlayer != null ? Bukkit.getPlayer(bestPlayer) : null);
        }
    }

    @Override
    public void onCleanup() {
        playerCheckpoints.clear();
        checkpointLocations.clear();
        checkpoints.clear();
        finishLocation = null;
    }
}
