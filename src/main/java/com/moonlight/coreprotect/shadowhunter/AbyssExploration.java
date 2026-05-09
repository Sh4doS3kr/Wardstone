package com.moonlight.coreprotect.shadowhunter;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Fase 1 de la Misión 2: Búsqueda de 5 Fragmentos del Abismo.
 * Los fragmentos son ArmorStands invisibles con partículas épicas.
 */
public class AbyssExploration {

    private final CoreProtectPlugin plugin;
    private final ShadowHunterManager manager;
    private final Player player;
    private final ShadowHunterNPC npc;

    private Location explorationCenter;
    private static final double AREA_RADIUS = 50.0;
    private static final int TOTAL_FRAGMENTS = 5;

    private BossBar bossBar;
    private BukkitTask particleTask;
    private BukkitTask detectionTask;
    private BukkitTask barrierTask;

    private final List<ArmorStand> fragmentStands = new ArrayList<>();
    private final List<Location> fragmentLocations = new ArrayList<>();
    private final boolean[] fragmentCollected = new boolean[TOTAL_FRAGMENTS];
    private int fragmentsFound = 0;
    private boolean active = false;

    // Colores y nombres de cada fragmento
    private static final Color[] FRAG_COLORS = {
            Color.fromRGB(0, 200, 255), // Azul hielo
            Color.fromRGB(200, 0, 255), // Violeta
            Color.fromRGB(255, 50, 0), // Rojo fuego
            Color.fromRGB(0, 255, 100), // Verde esmeralda
            Color.fromRGB(255, 200, 0) // Dorado
    };
    private static final String[] FRAG_NAMES = {
            "§b§lFragmento de Hielo",
            "§5§lFragmento del Vacío",
            "§c§lFragmento de Fuego",
            "§a§lFragmento de Vida",
            "§6§lFragmento Solar"
    };

    public AbyssExploration(CoreProtectPlugin plugin, ShadowHunterManager manager, Player player, ShadowHunterNPC npc) {
        this.plugin = plugin;
        this.manager = manager;
        this.player = player;
        this.npc = npc;
    }

    public void start() {
        active = true;
        explorationCenter = player.getLocation().clone();

        // BossBar
        bossBar = Bukkit.createBossBar("§5§l✦ Fragmentos del Abismo §8- §70/" + TOTAL_FRAGMENTS, BarColor.PURPLE,
                BarStyle.SEGMENTED_6);
        bossBar.addPlayer(player);
        bossBar.setProgress(0);

        // Posicionar NPC
        Location npcLoc = explorationCenter.clone().add(3, 0, 0);
        npcLoc.setY(npcLoc.getWorld().getHighestBlockYAt(npcLoc) + 1);
        npc.moveToAndStay(npcLoc);

        // Diálogo
        npc.say("\"§dEl Abismo dejó fragmentos de su poder esparcidos por la zona...\"");
        npc.sayDelayed("\"§7Busca los §d5 Fragmentos del Abismo§7. Sentirás su energía al acercarte.\"", 60L);
        npc.sayDelayed("\"§7Sigue las partículas... te guiarán.\"", 120L);

        // Crear fragmentos
        spawnFragments();

        // Iniciar sistemas
        startParticles();
        startDetection();
        startBarrier();

        // Mensaje de inicio épico
        player.sendTitle( SmallCaps.convert("§5§lBÚSQUEDA DEL ABISMO"), SmallCaps.convert("§7Encuentra los 5 fragmentos"), 10, 60, 20);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.6f);
    }

    // ==================== SPAWN FRAGMENTOS ====================

    private void spawnFragments() {
        World world = explorationCenter.getWorld();
        Random random = new Random();

        for (int i = 0; i < TOTAL_FRAGMENTS; i++) {
            Location loc = findSafeFragmentLocation(random);
            if (loc == null) {
                // Fallback: posición simple
                double angle = (Math.PI * 2 / TOTAL_FRAGMENTS) * i;
                double radius = 15 + random.nextInt(25);
                loc = explorationCenter.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
                loc.setY(world.getHighestBlockYAt(loc) + 1.5);
            }

            fragmentLocations.add(loc.clone());
            fragmentCollected[i] = false;

            // Crear ArmorStand invisible como marcador
            ArmorStand stand = world.spawn(loc, ArmorStand.class);
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSmall(true);
            stand.setMarker(true);
            stand.setCustomName(SmallCaps.convert("§5Fragment_" + i));
            stand.setCustomNameVisible(false);
            fragmentStands.add(stand);
        }
    }

    private Location findSafeFragmentLocation(Random random) {
        World world = explorationCenter.getWorld();
        for (int attempt = 0; attempt < 20; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = 12 + random.nextDouble() * (AREA_RADIUS - 15);
            Location loc = explorationCenter.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            int y = world.getHighestBlockYAt(loc);
            loc.setY(y + 1.5);

            Material below = world.getBlockAt(loc.clone().subtract(0, 1.5, 0)).getType();
            if (below != Material.WATER && below != Material.LAVA && below.isSolid()) {
                return loc;
            }
        }
        return null;
    }

    // ==================== DETECCIÓN ====================

    private void startDetection() {
        detectionTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!active || !player.isOnline()) {
                    cancel();
                    return;
                }

                for (int i = 0; i < TOTAL_FRAGMENTS; i++) {
                    if (fragmentCollected[i])
                        continue;

                    Location fragLoc = fragmentLocations.get(i);
                    double dist = player.getLocation().distance(fragLoc);

                    // Pista de proximidad
                    if (dist < 15 && dist > 3) {
                        // Sonido sutil de guía
                        float pitch = (float) (2.0 - (dist / 15.0) * 1.5);
                        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.3f, pitch);
                    }

                    // Recoger fragmento
                    if (dist < 2.5) {
                        collectFragment(i);
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 10);
    }

    private void collectFragment(int index) {
        fragmentCollected[index] = true;
        fragmentsFound++;

        Location loc = fragmentLocations.get(index);
        World world = loc.getWorld();

        // Eliminar ArmorStand
        ArmorStand stand = fragmentStands.get(index);
        if (stand != null && !stand.isDead())
            stand.remove();

        // === EFECTO ÉPICO DE RECOGIDA ===

        // Implosión de partículas
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 20) {
                    cancel();
                    return;
                }
                double radius = 3.0 - (ticks * 0.15);
                for (int i = 0; i < 12; i++) {
                    double a = (Math.PI * 2 / 12) * i + ticks * 0.3;
                    world.spawnParticle(Particle.DUST,
                            loc.clone().add(Math.cos(a) * radius, 0.5 + ticks * 0.1, Math.sin(a) * radius),
                            2, 0, 0, 0, 0,
                            new Particle.DustOptions(FRAG_COLORS[index], 2.0f));
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0, 1);

        // Explosión de absorción final
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            world.spawnParticle(Particle.END_ROD, loc, 50, 0.5, 0.5, 0.5, 0.3);
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.3);
            world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f + (fragmentsFound * 0.2f));
            world.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
        }, 20L);

        // Mensaje
        player.sendMessage(
                "§5§l✦ §f¡" + FRAG_NAMES[index] + " §fencontrado! §7(" + fragmentsFound + "/" + TOTAL_FRAGMENTS + ")");
        player.sendTitle(FRAG_NAMES[index], SmallCaps.convert("§7" + fragmentsFound + "/" + TOTAL_FRAGMENTS + " fragmentos"), 5, 40, 10);

        // Actualizar BossBar
        bossBar.setTitle(SmallCaps.convert("§5§l✦ Fragmentos del Abismo §8- §f" + fragmentsFound + "/" + TOTAL_FRAGMENTS));
        bossBar.setProgress((double) fragmentsFound / TOTAL_FRAGMENTS);

        // Comentarios del NPC según progreso
        switch (fragmentsFound) {
            case 1:
                npc.sayDelayed("\"§7Bien... puedo sentir su energía. Quedan 4.\"", 20L);
                break;
            case 2:
                npc.sayDelayed("\"§7El Abismo resuena... sigue buscando.\"", 20L);
                break;
            case 3:
                npc.sayDelayed("\"§d¡Tres! La energía se acumula...\"", 20L);
                break;
            case 4:
                npc.sayDelayed("\"§d¡Casi! ¡Un fragmento más!\"", 20L);
                break;
            case 5:
                npc.sayDelayed("\"§a§l¡TODOS LOS FRAGMENTOS! §7La energía del Abismo es tuya.\"", 20L);
                Bukkit.getScheduler().runTaskLater(plugin, this::onAllFragmentsCollected, 80L);
                break;
        }
    }

    private void onAllFragmentsCollected() {
        if (!active)
            return;

        World world = player.getWorld();
        Location loc = player.getLocation();

        // Efecto épico de completar
        bossBar.setTitle(SmallCaps.convert("§a§l✔ ¡TODOS LOS FRAGMENTOS REUNIDOS!"));
        bossBar.setColor(BarColor.GREEN);
        bossBar.setProgress(1.0);

        // Columna de energía
        for (int y = 0; y < 30; y++) {
            final int yy = y;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                world.spawnParticle(Particle.END_ROD, loc.clone().add(0, yy, 0), 5, 0.3, 0, 0.3, 0.02);
                world.spawnParticle(Particle.DUST, loc.clone().add(0, yy, 0), 3, 0.2, 0, 0.2, 0,
                        new Particle.DustOptions(Color.fromRGB(150, 0, 255), 2.0f));
            }, yy);
        }

        world.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 2.0f);
        player.sendTitle( SmallCaps.convert("§a§l¡FRAGMENTOS REUNIDOS!"), SmallCaps.convert("§7El poder del Abismo te espera..."), 10, 60, 20);

        // Transición
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            cleanup();
            manager.onQuest2ExplorationCompleted(player);
        }, 100L);
    }

    // ==================== PARTÍCULAS ====================

    private void startParticles() {
        particleTask = new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!active || !player.isOnline()) {
                    cancel();
                    return;
                }

                World world = explorationCenter.getWorld();

                for (int i = 0; i < TOTAL_FRAGMENTS; i++) {
                    if (fragmentCollected[i])
                        continue;

                    Location loc = fragmentLocations.get(i);
                    Color color = FRAG_COLORS[i];

                    // Orbe flotante principal
                    double bobY = Math.sin(tick * 0.1 + i) * 0.3;
                    world.spawnParticle(Particle.DUST, loc.clone().add(0, bobY, 0), 5, 0.15, 0.15, 0.15, 0,
                            new Particle.DustOptions(color, 2.5f));

                    // Halo giratorio
                    double angle = tick * 0.15 + (Math.PI * 2 / TOTAL_FRAGMENTS) * i;
                    double haloX = Math.cos(angle) * 0.6;
                    double haloZ = Math.sin(angle) * 0.6;
                    world.spawnParticle(Particle.END_ROD, loc.clone().add(haloX, bobY + 0.3, haloZ), 1, 0, 0, 0, 0.005);

                    // Columna de luz sutil ascendente cada pocos ticks
                    if (tick % 4 == 0) {
                        world.spawnParticle(Particle.DUST, loc.clone().add(0, 3, 0), 2, 0.1, 1, 0.1, 0,
                                new Particle.DustOptions(color, 1.0f));
                        world.spawnParticle(Particle.ENCHANT, loc.clone().add(0, 2, 0), 3, 0.3, 0.5, 0.3, 0.5);
                    }

                    // Trail de guía hacia el jugador (solo si está relativamente cerca)
                    double distToPlayer = player.getLocation().distance(loc);
                    if (distToPlayer < 25 && tick % 6 == 0) {
                        // Partícula entre el fragmento y el jugador
                        Location mid = loc.clone().add(
                                (player.getLocation().getX() - loc.getX()) * 0.3,
                                (player.getLocation().getY() - loc.getY()) * 0.3 + 1,
                                (player.getLocation().getZ() - loc.getZ()) * 0.3);
                        world.spawnParticle(Particle.DUST, mid, 1, 0.5, 0.5, 0.5, 0,
                                new Particle.DustOptions(color, 0.8f));
                    }
                }

                tick++;
            }
        }.runTaskTimer(plugin, 0, 3);
    }

    // ==================== BARRERA ====================

    private void startBarrier() {
        barrierTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!active || !player.isOnline()) {
                    cancel();
                    return;
                }

                if (player.getLocation().distance(explorationCenter) > AREA_RADIUS) {
                    player.teleport(explorationCenter);
                    player.sendMessage(SmallCaps.convert("§c§l⚠ §fNo puedes alejarte tanto durante la exploración."));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                }
            }
        }.runTaskTimer(plugin, 0, 20);
    }

    // ==================== GETTERS / CLEANUP ====================

    public boolean isActive() {
        return active;
    }

    public Location getExplorationCenter() {
        return explorationCenter;
    }

    public void cleanup() {
        active = false;
        if (particleTask != null)
            particleTask.cancel();
        if (detectionTask != null)
            detectionTask.cancel();
        if (barrierTask != null)
            barrierTask.cancel();
        if (bossBar != null)
            bossBar.removeAll();
        for (ArmorStand stand : fragmentStands) {
            if (stand != null && !stand.isDead())
                stand.remove();
        }
        fragmentStands.clear();
    }
}
