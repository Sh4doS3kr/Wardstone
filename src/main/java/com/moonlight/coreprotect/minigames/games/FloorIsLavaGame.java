package com.moonlight.coreprotect.minigames.games;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.minigames.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * El Suelo es Lava - Floor is Lava
 * Arena con plataformas y bloques que van desapareciendo progresivamente.
 * Los jugadores deben saltar entre plataformas para sobrevivir.
 * Caer a la lava = eliminado. Último en pie gana.
 * 4 fases de destrucción creciente con power-ups ocasionales.
 */
public class FloorIsLavaGame extends MiniGame {

    private final Random random = new Random();
    private int destroyTaskId = -1;
    private int phase = 0;
    private final List<Location> platformBlocks = new ArrayList<>();
    private int totalBlocks = 0;
    private static final int MAX_TIME = 180;

    public FloorIsLavaGame(CoreProtectPlugin plugin, MiniGameManager manager) {
        super(plugin, manager, MiniGameType.FLOOR_IS_LAVA);
    }

    @Override
    public void buildArena(World world) {
        int baseY = 85;
        int lavaY = 75;

        // Lava en el fondo
        for (int x = -30; x <= 30; x++) {
            for (int z = -30; z <= 30; z++) {
                for (int y = lavaY - 1; y <= lavaY; y++) {
                    world.getBlockAt(x, y, z).setType(Material.LAVA);
                }
                world.getBlockAt(x, lavaY - 2, z).setType(Material.BEDROCK);
            }
        }

        // Plataforma central grande
        buildPlatform(world, 0, baseY, 0, 4, Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS);

        // Plataformas medianas alrededor
        int[][] mediumPlatforms = {
            {12, 0}, {-12, 0}, {0, 12}, {0, -12},
            {9, 9}, {-9, 9}, {9, -9}, {-9, -9}
        };
        Material[] platMats = {
            Material.OAK_PLANKS, Material.BIRCH_PLANKS, Material.SPRUCE_PLANKS,
            Material.JUNGLE_PLANKS, Material.ACACIA_PLANKS, Material.DARK_OAK_PLANKS,
            Material.CRIMSON_PLANKS, Material.WARPED_PLANKS
        };
        for (int i = 0; i < mediumPlatforms.length; i++) {
            int py = baseY + random.nextInt(3) - 1;
            buildPlatform(world, mediumPlatforms[i][0], py, mediumPlatforms[i][1], 3, platMats[i], platMats[i]);
        }

        // Plataformas pequeñas (puentes entre las grandes)
        for (int i = 0; i < 20; i++) {
            int px = random.nextInt(40) - 20;
            int pz = random.nextInt(40) - 20;
            int py = baseY + random.nextInt(4) - 1;
            if (Math.abs(px) < 5 && Math.abs(pz) < 5) continue;
            int size = 1 + random.nextInt(2);
            Material mat = Material.COBBLESTONE;
            if (random.nextBoolean()) mat = Material.STONE;
            if (random.nextInt(3) == 0) mat = Material.DEEPSLATE_BRICKS;
            buildPlatform(world, px, py, pz, size, mat, mat);
        }

        // Pilares decorativos
        for (int i = 0; i < 8; i++) {
            int px = random.nextInt(40) - 20;
            int pz = random.nextInt(40) - 20;
            for (int y = lavaY; y <= baseY + 2; y++) {
                world.getBlockAt(px, y, pz).setType(Material.BASALT);
                platformBlocks.add(new Location(world, px, y, pz));
            }
        }

        // Paredes de vidrio
        for (int x = -30; x <= 30; x++) {
            for (int y = lavaY; y <= baseY + 8; y++) {
                world.getBlockAt(x, y, -30).setType(Material.RED_STAINED_GLASS);
                world.getBlockAt(x, y, 30).setType(Material.RED_STAINED_GLASS);
            }
        }
        for (int z = -30; z <= 30; z++) {
            for (int y = lavaY; y <= baseY + 8; y++) {
                world.getBlockAt(-30, y, z).setType(Material.RED_STAINED_GLASS);
                world.getBlockAt(30, y, z).setType(Material.RED_STAINED_GLASS);
            }
        }

        totalBlocks = platformBlocks.size();
    }

    private void buildPlatform(World world, int cx, int cy, int cz, int radius, Material main, Material accent) {
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                double dist = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                if (dist <= radius + 0.5) {
                    Material mat = (random.nextInt(3) == 0) ? accent : main;
                    world.getBlockAt(x, cy, z).setType(mat);
                    platformBlocks.add(new Location(world, x, cy, z));
                }
            }
        }
    }

    @Override
    public List<Location> getSpawnLocations(World world) {
        List<Location> spawns = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            double angle = (Math.PI * 2.0 / 24) * i;
            spawns.add(new Location(world, Math.cos(angle) * 3 + 0.5, 86, Math.sin(angle) * 3 + 0.5));
        }
        return spawns;
    }

    @Override
    public void startGameLogic() {
        broadcastGame("§6§l🌋 §e¡EL SUELO ES LAVA! §7Las plataformas se destruyen progresivamente.");
        broadcastGame("§6§l🌋 §c¡Caer a la lava = ELIMINADO! §7Último en pie gana.");
        broadcastGame("§6§l🌋 §74 fases de destrucción. §e¡Salta para sobrevivir!");
        titleAlive("§6§l🌋 EL SUELO ES LAVA", "§7¡No caigas!");

        // Dar speed y jump boost
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.JUMP_BOOST, 999999, 0, false, false, false));
            }
        }

        // Task de destrucción de bloques
        destroyTaskId = new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (!running) { cancel(); return; }
                tick++;

                int destroyCount;
                int freq;

                if (gameTime < 30) {
                    // Fase 1: lento
                    destroyCount = 1;
                    freq = 15;
                } else if (gameTime < 60) {
                    // Fase 2: moderado
                    destroyCount = 2;
                    freq = 10;
                } else if (gameTime < 100) {
                    // Fase 3: rápido
                    destroyCount = 4;
                    freq = 6;
                } else {
                    // Fase 4: caos
                    destroyCount = 8;
                    freq = 3;
                }

                if (tick % freq != 0) return;

                World w = Bukkit.getWorld(MiniGameWorld.getWorldName());
                if (w == null) return;

                int destroyed = 0;
                Collections.shuffle(platformBlocks);
                Iterator<Location> it = platformBlocks.iterator();
                while (it.hasNext() && destroyed < destroyCount) {
                    Location loc = it.next();
                    if (w.getBlockAt(loc).getType() != Material.AIR &&
                        w.getBlockAt(loc).getType() != Material.LAVA &&
                        w.getBlockAt(loc).getType() != Material.BEDROCK &&
                        w.getBlockAt(loc).getType() != Material.RED_STAINED_GLASS) {

                        // Efecto de advertencia (parpadeo)
                        Location fLoc = loc.clone();
                        w.getBlockAt(fLoc).setType(Material.REDSTONE_BLOCK);
                        w.spawnParticle(Particle.SMOKE, fLoc.clone().add(0.5, 1, 0.5), 5, 0.3, 0.3, 0.3, 0.01);
                        w.playSound(fLoc, Sound.BLOCK_FIRE_EXTINGUISH, 0.3f, 1.5f);

                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (w.getBlockAt(fLoc).getType() == Material.REDSTONE_BLOCK) {
                                w.getBlockAt(fLoc).setType(Material.AIR);
                                w.spawnParticle(Particle.LAVA, fLoc.clone().add(0.5, 0.5, 0.5), 8, 0.3, 0.3, 0.3, 0);
                            }
                        }, 20L);

                        it.remove();
                        destroyed++;
                    }
                }
            }
        }.runTaskTimer(plugin, 100L, 1L).getTaskId();
    }

    @Override
    public void onTick() {
        World w = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (w == null) return;

        // Comprobar fases
        int newPhase;
        if (gameTime < 30) newPhase = 0;
        else if (gameTime < 60) newPhase = 1;
        else if (gameTime < 100) newPhase = 2;
        else newPhase = 3;

        if (newPhase != phase) {
            phase = newPhase;
            String[] phaseNames = {
                "§a§lFase 1: §7Calentamiento",
                "§e§lFase 2: §7Se acelera",
                "§c§lFase 3: §7Destrucción",
                "§4§lFase 4: §4CAOS TOTAL"
            };
            broadcastGame("§6§l🌋 " + phaseNames[phase]);
            titleAlive(phaseNames[phase], "§7¡Cuidado con las plataformas!");
            soundAll(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.5f);

            // Jump boost extra en fases avanzadas
            if (phase >= 2) {
                for (UUID uuid : alivePlayers) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.JUMP_BOOST, 999999, 1, false, false, false));
                    }
                }
            }
        }

        // Comprobar jugadores en lava o caídos
        for (UUID uuid : new ArrayList<>(alivePlayers)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;

            Location loc = p.getLocation();
            // En lava o debajo de las plataformas
            if (loc.getY() < 77 || loc.getBlock().getType() == Material.LAVA) {
                p.sendTitle("§c§l🌋 ¡LAVA!", "§7Te quemaste", 10, 30, 10);
                eliminatePlayer(uuid);
            }
        }

        // Info cada 15s
        if (gameTime % 15 == 0 && gameTime > 0) {
            int remaining = platformBlocks.size();
            int percent = totalBlocks > 0 ? (remaining * 100 / totalBlocks) : 0;
            broadcastGame("§6§l🌋 §7| §fFase: §e" + (phase + 1) + "/4 §7| §fVivos: §a" + alivePlayers.size() + 
                " §7| §fSuelo: §c" + percent + "% §7| §e" + gameTime + "s");
        }

        // Timeout
        if (gameTime >= MAX_TIME) {
            broadcastGame("§6§l🌋 §7¡Tiempo agotado!");
            if (!alivePlayers.isEmpty()) {
                UUID winner = alivePlayers.iterator().next();
                endGame(Bukkit.getPlayer(winner));
            } else {
                endGame(null);
            }
        }
    }

    @Override
    public void onCleanup() {
        if (destroyTaskId != -1) {
            try { Bukkit.getScheduler().cancelTask(destroyTaskId); } catch (Exception ignored) {}
            destroyTaskId = -1;
        }
        platformBlocks.clear();
    }
}
