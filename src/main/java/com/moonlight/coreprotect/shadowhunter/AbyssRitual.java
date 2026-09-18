package com.moonlight.coreprotect.shadowhunter;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Fase 3 de la Misión 2: Ritual de los 6 Pilares Elementales.
 * Pilares en hexágono que deben activarse EN ORDEN (el NPC da pistas).
 */
public class AbyssRitual {

    private final CoreProtectPlugin plugin;
    private final ShadowHunterManager manager;
    private final Player player;
    private final ShadowHunterNPC npc;

    private Location ritualCenter;
    private boolean active = false;
    private boolean channeling = false;
    private int channelProgress = 0;
    private static final int CHANNEL_REQUIRED = 300; // 15 segundos

    private BukkitTask ritualTask;
    private BukkitTask channelTask;
    private BukkitTask detectionTask;

    // 6 pilares en hexágono
    private final Location[] pillarLocations = new Location[6];
    private final boolean[] pillarActivated = new boolean[6];
    private int pillarsActivated = 0;

    // Orden correcto de activación (se genera aleatoriamente)
    private int[] correctOrder;
    private int currentOrderIndex = 0;

    // Bloques colocados (para restaurar)
    private final Map<Location, BlockData> originalBlocks = new HashMap<>();

    // Colores y elementos
    private static final Color[] PILLAR_COLORS = {
            Color.fromRGB(255, 50, 0), // Fuego
            Color.fromRGB(0, 150, 255), // Hielo
            Color.fromRGB(255, 255, 0), // Rayo
            Color.fromRGB(0, 180, 50), // Tierra
            Color.fromRGB(200, 200, 255), // Viento
            Color.fromRGB(80, 0, 150) // Vacío
    };
    private static final String[] PILLAR_NAMES = {
            "§c§lFuego", "§b§lHielo", "§e§lRayo", "§2§lTierra", "§f§lViento", "§5§lVacío"
    };
    private static final String[] PILLAR_HINTS = {
            "\"§7El calor del §c§lFuego§7 debe arder primero...\"",
            "\"§7Ahora el §b§lHielo§7 debe enfriar las llamas...\"",
            "\"§7El §e§lRayo§7 ilumina el camino...\"",
            "\"§7La §2§lTierra§7 da estabilidad al poder...\"",
            "\"§7El §f§lViento§7 transporta la energía...\"",
            "\"§7Y el §5§lVacío§7 consume todo para renacer.\""
    };

    public AbyssRitual(CoreProtectPlugin plugin, ShadowHunterManager manager, Player player, ShadowHunterNPC npc) {
        this.plugin = plugin;
        this.manager = manager;
        this.player = player;
        this.npc = npc;
    }

    public void start() {
        active = true;
        ritualCenter = player.getLocation().clone();
        ritualCenter.setY(ritualCenter.getWorld().getHighestBlockYAt(ritualCenter) + 1);

        // Generar orden aleatorio
        generateRandomOrder();

        // NPC
        Location npcLoc = ritualCenter.clone().add(7, 0, 0);
        npcLoc.setY(npcLoc.getWorld().getHighestBlockYAt(npcLoc) + 1);
        npc.moveToAndStay(npcLoc);

        // Configurar pilares en hexágono
        double pillarDist = 8.0;
        for (int i = 0; i < 6; i++) {
            double angle = (Math.PI * 2 / 6) * i - Math.PI / 2;
            pillarLocations[i] = ritualCenter.clone().add(Math.cos(angle) * pillarDist, 0,
                    Math.sin(angle) * pillarDist);
            pillarLocations[i].setY(ritualCenter.getWorld().getHighestBlockYAt(pillarLocations[i]) + 1);
            pillarActivated[i] = false;
        }

        // Colocar bloques físicos
        placePillarBlocks();

        // Diálogo
        npc.say("\"§dEste ritual es diferente... 6 pilares elementales.\"");
        npc.sayDelayed("\"§7Debes activarlos en el §corden correcto§7... o sufrirás las consecuencias.\"", 60L);
        npc.sayDelayed("\"§7Escucha mis pistas para saber cuál activar.\"", 120L);
        // Dar primera pista
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            npc.say(PILLAR_HINTS[correctOrder[0]]);
        }, 180L);

        // Efectos visuales
        startRitualVisuals();
        startPillarDetection();

        player.sendTitle( SmallCaps.convert("§5§lRITUAL DEL ABISMO"), SmallCaps.convert("§7Activa los 6 pilares en orden"), 10, 60, 20);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.5f);
    }

    // ==================== ORDEN ====================

    private void generateRandomOrder() {
        // Generar permutación aleatoria de 0-5
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < 6; i++)
            order.add(i);
        Collections.shuffle(order);
        correctOrder = order.stream().mapToInt(Integer::intValue).toArray();
    }

    // ==================== PILARES ====================

    private void placePillarBlocks() {
        World world = ritualCenter.getWorld();
        for (int i = 0; i < 6; i++) {
            Location base = pillarLocations[i].clone();
            for (int y = 0; y < 4; y++) {
                Location blockLoc = base.clone().add(0, y, 0);
                Block block = world.getBlockAt(blockLoc);
                originalBlocks.put(blockLoc.clone(), block.getBlockData().clone());
                block.setType(Material.END_ROD);
            }
            world.spawnParticle(Particle.END_ROD, base.clone().add(0, 2, 0), 20, 0.3, 1, 0.3, 0.05);
            world.playSound(base, Sound.BLOCK_AMETHYST_CLUSTER_PLACE, 1.0f, 0.5f + i * 0.15f);
        }
    }

    private void startPillarDetection() {
        detectionTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!active || !player.isOnline() || channeling) {
                    cancel();
                    return;
                }

                for (int i = 0; i < 6; i++) {
                    if (pillarActivated[i])
                        continue;
                    if (player.getLocation().distance(pillarLocations[i]) < 2.5) {
                        attemptActivatePillar(i);
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 5);
    }

    private void attemptActivatePillar(int index) {
        if (correctOrder[currentOrderIndex] == index) {
            // ¡Correcto!
            activatePillar(index);
        } else {
            // ¡Incorrecto! Penalización
            wrongPillar(index);
        }
    }

    private void activatePillar(int index) {
        pillarActivated[index] = true;
        pillarsActivated++;
        currentOrderIndex++;

        Location loc = pillarLocations[index];
        World world = loc.getWorld();

        // Cambiar bloques a SOUL_LANTERN
        Location base = loc.clone();
        for (int y = 0; y < 4; y++) {
            world.getBlockAt(base.clone().add(0, y, 0)).setType(Material.SOUL_LANTERN);
        }

        // Columna de partículas épica
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 50) {
                    cancel();
                    return;
                }
                double y = ticks * 0.25;
                world.spawnParticle(Particle.DUST, loc.clone().add(0, y, 0), 10, 0.3, 0.1, 0.3, 0,
                        new Particle.DustOptions(PILLAR_COLORS[index], 3.0f));
                world.spawnParticle(Particle.END_ROD, loc.clone().add(0, y, 0), 3, 0.1, 0.1, 0.1, 0.03);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, y, 0), 2, 0.2, 0.1, 0.2, 0.02);
                // Espiral
                double a = ticks * 0.3;
                world.spawnParticle(Particle.DUST,
                        loc.clone().add(Math.cos(a) * 0.8, y * 0.8, Math.sin(a) * 0.8), 2, 0, 0, 0, 0,
                        new Particle.DustOptions(PILLAR_COLORS[index], 1.5f));
                ticks++;
            }
        }.runTaskTimer(plugin, 0, 1);

        world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.7f + (pillarsActivated * 0.15f));
        world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.5f, 1.0f + (pillarsActivated * 0.2f));
        world.strikeLightningEffect(loc);

        player.sendMessage(SmallCaps.convert("§5§l✦ §fPilar " + PILLAR_NAMES[index] + " §a§l¡CORRECTO! §7(" + pillarsActivated + "/6)"));
        player.sendTitle( SmallCaps.convert("§a✔ " + PILLAR_NAMES[index]), SmallCaps.convert("§7" + pillarsActivated + "/6 pilares"), 5, 30, 10);

        // Beam al centro
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 30) {
                    cancel();
                    return;
                }
                double progress = ticks / 30.0;
                double x = loc.getX() + (ritualCenter.getX() - loc.getX()) * progress;
                double y = loc.getY() + 2 + Math.sin(progress * Math.PI) * 4;
                double z = loc.getZ() + (ritualCenter.getZ() - loc.getZ()) * progress;
                world.spawnParticle(Particle.DUST, new Location(world, x, y, z), 5, 0.1, 0.1, 0.1, 0,
                        new Particle.DustOptions(PILLAR_COLORS[index], 2.0f));
                world.spawnParticle(Particle.END_ROD, new Location(world, x, y, z), 1, 0, 0, 0, 0.01);
                ticks++;
            }
        }.runTaskTimer(plugin, 5, 1);

        if (pillarsActivated < 6) {
            // Dar pista del siguiente pilar
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                npc.say(PILLAR_HINTS[correctOrder[currentOrderIndex]]);
            }, 40L);
        } else {
            npc.sayDelayed("\"§d§l¡TODOS LOS PILARES! §7Ve al centro... la canalización será intensa.\"", 20L);
            if (detectionTask != null)
                detectionTask.cancel();
            Bukkit.getScheduler().runTaskLater(plugin, this::startChanneling, 80L);
        }
    }

    private void wrongPillar(int index) {
        Location loc = pillarLocations[index];
        World world = loc.getWorld();

        // Penalización: daño + knockback
        player.damage(4);
        org.bukkit.util.Vector kb = player.getLocation().toVector().subtract(loc.toVector()).normalize().multiply(1.5);
        kb.setY(0.6);
        player.setVelocity(kb);

        // Efectos de error
        world.spawnParticle(Particle.SMOKE, loc, 30, 0.5, 1, 0.5, 0.1);
        world.spawnParticle(Particle.DUST, loc.clone().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0,
                new Particle.DustOptions(Color.RED, 2.0f));
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 0.5f);
        world.playSound(loc, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.3f);

        player.sendMessage(SmallCaps.convert("§c§l✖ §c¡Pilar incorrecto! §7Ese no es el siguiente."));
        player.sendTitle( SmallCaps.convert("§c§l✖ INCORRECTO"), SmallCaps.convert("§7Escucha las pistas del Errante"), 5, 30, 10);

        // Repetir pista
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            npc.say("\"§c¡No! §7" + PILLAR_HINTS[correctOrder[currentOrderIndex]].replace("\"", ""));
        }, 30L);
    }

    // ==================== CANALIZACIÓN ====================

    private void startChanneling() {
        channeling = true;
        channelProgress = 0;

        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§5§l✦ §d§l¡CANALIZACIÓN SUPREMA! §fQuédate quieto en el centro."));
        player.sendMessage(SmallCaps.convert("§7No te muevas durante 15 segundos..."));
        player.sendMessage("");

        channelTask = new BukkitRunnable() {
            Location lastLoc = player.getLocation().clone();

            @Override
            public void run() {
                if (!active || !player.isOnline()) {
                    cancel();
                    return;
                }

                Location currentLoc = player.getLocation();
                if (currentLoc.getBlockX() != lastLoc.getBlockX() ||
                        currentLoc.getBlockZ() != lastLoc.getBlockZ()) {
                    channelProgress = 0;
                    lastLoc = currentLoc.clone();
                    player.sendMessage(SmallCaps.convert("§c§l⚠ §c¡Te moviste! La canalización se reinició."));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                    return;
                }

                channelProgress++;
                double progress = (double) channelProgress / CHANNEL_REQUIRED;
                World world = ritualCenter.getWorld();

                // 6 espirales convergentes desde los pilares
                for (int i = 0; i < 6; i++) {
                    if (!pillarActivated[i])
                        continue;
                    double angle = channelProgress * 0.08 + (Math.PI / 3) * i;
                    double radius = 8.0 * (1.0 - progress);
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    double y = 1 + progress * 5;

                    world.spawnParticle(Particle.DUST,
                            ritualCenter.clone().add(x, y, z), 3, 0.1, 0.1, 0.1, 0,
                            new Particle.DustOptions(PILLAR_COLORS[i], 2.0f + (float) progress));
                }

                // Círculo central intensificándose
                if (channelProgress % 4 == 0) {
                    double circleR = 3.0 * (1.0 - progress * 0.6);
                    for (int i = 0; i < 12; i++) {
                        double a = (Math.PI * 2 / 12) * i + channelProgress * 0.04;
                        world.spawnParticle(Particle.END_ROD,
                                ritualCenter.clone().add(Math.cos(a) * circleR, 0.5 + progress * 3,
                                        Math.sin(a) * circleR),
                                1, 0, 0, 0, 0.01);
                    }
                }

                // Sonidos crecientes
                if (channelProgress % 15 == 0) {
                    float pitch = 0.4f + (float) progress * 1.6f;
                    world.playSound(ritualCenter, Sound.BLOCK_BEACON_AMBIENT, 1.0f, pitch);
                }

                // Columna de energía progresiva
                if (progress > 0.5 && channelProgress % 3 == 0) {
                    double h = (progress - 0.5) * 2 * 20;
                    for (int y = 0; y < h; y += 2) {
                        world.spawnParticle(Particle.DUST, ritualCenter.clone().add(0, y, 0), 2, 0.2, 0, 0.2, 0,
                                new Particle.DustOptions(Color.fromRGB(150, 0, 255), 1.5f));
                    }
                }

                // Barra de progreso
                int bars = (int) (progress * 20);
                StringBuilder progressBar = new StringBuilder("§5§l");
                for (int i = 0; i < 20; i++) {
                    progressBar.append(i < bars ? "§d█" : "§8█");
                }
                player.sendTitle("", SmallCaps.convert(progressBar + " §f" + (int) (progress * 100) + "%"), 0, 5, 0);

                if (channelProgress >= CHANNEL_REQUIRED) {
                    cancel();
                    onChannelingComplete();
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private void onChannelingComplete() {
        channeling = false;
        active = false;
        World world = ritualCenter.getWorld();

        // EXPLOSIÓN FINAL SUPREMA
        new BukkitRunnable() {
            int phase = 0;

            @Override
            public void run() {
                switch (phase) {
                    case 0:
                        world.spawnParticle(Particle.END_ROD, ritualCenter.clone().add(0, 3, 0), 10, 0.5, 0.5, 0.5, 0.1);
                        world.spawnParticle(Particle.END_ROD, ritualCenter.clone().add(0, 3, 0), 300, 4, 4, 4, 0.8);
                        world.playSound(ritualCenter, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.5f);
                        world.playSound(ritualCenter, Sound.BLOCK_BEACON_ACTIVATE, 2.0f, 1.0f);
                        break;
                    case 8:
                        // Columna de luz multicolor
                        for (int y = 0; y < 40; y++) {
                            int colorIdx = y % 6;
                            world.spawnParticle(Particle.DUST, ritualCenter.clone().add(0, y, 0), 5, 0.3, 0, 0.3, 0,
                                    new Particle.DustOptions(PILLAR_COLORS[colorIdx], 2.0f));
                            world.spawnParticle(Particle.END_ROD, ritualCenter.clone().add(0, y, 0), 2, 0.2, 0, 0.2,
                                    0.01);
                        }
                        world.playSound(ritualCenter, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 2.0f);
                        break;
                    case 20:
                        // Onda expansiva con todos los colores
                        for (int i = 0; i < 60; i++) {
                            double a = (Math.PI * 2 / 60) * i;
                            for (int r = 1; r <= 12; r++) {
                                int colorIdx = r % 6;
                                world.spawnParticle(Particle.DUST,
                                        ritualCenter.clone().add(Math.cos(a) * r, 1.5, Math.sin(a) * r),
                                        1, 0, 0, 0, 0,
                                        new Particle.DustOptions(PILLAR_COLORS[colorIdx], 2.0f));
                            }
                        }
                        world.playSound(ritualCenter, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 1.5f);
                        break;
                    case 35:
                        player.sendTitle( SmallCaps.convert("§5§l¡RITUAL COMPLETADO!"), SmallCaps.convert("§4§lAlgo terrible despierta..."), 10, 60, 20);
                        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                        npc.say("\"§4§l¡¿QUÉ HAS HECHO?! §7Algo monstruoso viene... ¡PREPÁRATE!\"");
                        break;
                    case 55:
                        // Efectos de Terror y Oscuridad
                        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.BLINDNESS, 120, 0));
                        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.DARKNESS, 120, 0));
                        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.LEVITATION, 30, 0));
                        world.playSound(ritualCenter, Sound.ENTITY_WARDEN_HEARTBEAT, 2.0f, 0.5f);
                        world.spawnParticle(Particle.SMOKE, ritualCenter, 200, 8, 2, 8, 0.1);
                        break;
                    case 70:
                        player.sendTitle( SmallCaps.convert("§4§l... EL ABISMO SE ABRE ..."), SmallCaps.convert("§cNo hay escapatoria"), 10, 50, 10);
                        world.playSound(ritualCenter, Sound.ENTITY_PHANTOM_SWOOP, 2.0f, 0.5f);
                        for (int i = 0; i < 40; i++) {
                            world.spawnParticle(Particle.SOUL, ritualCenter.clone().add((Math.random() - 0.5) * 15,
                                    Math.random() * 8, (Math.random() - 0.5) * 15), 5, 0, 0, 0, 0.1);
                        }
                        break;
                    case 85:
                        world.playSound(ritualCenter, Sound.ENTITY_WARDEN_HEARTBEAT, 2.0f, 0.6f);
                        world.strikeLightningEffect(ritualCenter.clone().add(8, 0, 8));
                        world.strikeLightningEffect(ritualCenter.clone().add(-8, 0, -8));
                        world.spawnParticle(Particle.SOUL_FIRE_FLAME, ritualCenter, 150, 5, 1, 5, 0.2);
                        break;
                    case 100:
                        world.playSound(ritualCenter, Sound.ENTITY_WARDEN_HEARTBEAT, 2.0f, 0.7f);
                        player.setVelocity(new org.bukkit.util.Vector(0, -0.6, 0)); // Caída súbita
                        npc.say("\"§4§l¡¡LA REALIDAD SE ESTÁ DESGARRANDO!!\"");
                        break;
                    case 115:
                        world.playSound(ritualCenter, Sound.ENTITY_WARDEN_HEARTBEAT, 2.0f, 0.8f);
                        player.sendTitle( SmallCaps.convert("§0§l■ ■ ■"), "", 0, 40, 10);
                        world.playSound(ritualCenter, Sound.AMBIENT_CAVE, 2.0f, 0.5f);
                        break;
                    case 130:
                        world.playSound(ritualCenter, Sound.ENTITY_WARDEN_EMERGE, 2.0f, 0.5f);
                        world.spawnParticle(Particle.END_ROD, ritualCenter.clone().add(0, 2, 0), 10, 0.5, 0.5, 0.5, 0.1);
                        break;
                    case 145:
                        cancel();
                        restoreBlocks();
                        manager.onQuest2RitualCompleted(player);
                        return;
                }
                phase++;
            }
        }.runTaskTimer(plugin, 0, 2);
    }

    // ==================== VISUALES ====================

    private void startRitualVisuals() {
        ritualTask = new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!active || !player.isOnline()) {
                    cancel();
                    return;
                }
                World world = ritualCenter.getWorld();

                for (int i = 0; i < 6; i++) {
                    Location loc = pillarLocations[i];
                    if (!pillarActivated[i]) {
                        // Pilar inactivo: partículas grandes + espiral
                        for (int y = 0; y < 5; y++) {
                            world.spawnParticle(Particle.DUST, loc.clone().add(0, y + 0.5, 0), 3, 0.2, 0.2, 0.2, 0,
                                    new Particle.DustOptions(PILLAR_COLORS[i], 2.0f));
                        }
                        double angle = tick * 0.12 + (Math.PI / 3) * i;
                        world.spawnParticle(Particle.END_ROD,
                                loc.clone().add(Math.cos(angle) * 0.7, 2.5 + Math.sin(tick * 0.1) * 0.5,
                                        Math.sin(angle) * 0.7),
                                1, 0, 0, 0, 0.005);
                        world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, 0.5, 0), 2, 0.3, 0.1, 0.3,
                                0.01);
                        world.spawnParticle(Particle.ENCHANT, loc.clone().add(0, 3, 0), 3, 0.5, 0.5, 0.5, 0.8);

                        // Nombre flotante visible (solo el correcto tiene una pulsación extra)
                        if (i == correctOrder[currentOrderIndex] && tick % 4 == 0) {
                            world.spawnParticle(Particle.DUST, loc.clone().add(0, 4.5, 0), 5, 0.1, 0.1, 0.1, 0,
                                    new Particle.DustOptions(Color.WHITE, 1.5f));
                        }
                    } else {
                        // Pilar activado: columna intensa
                        for (int y = 0; y < 8; y++) {
                            world.spawnParticle(Particle.DUST, loc.clone().add(0, y, 0), 3, 0.1, 0, 0.1, 0,
                                    new Particle.DustOptions(PILLAR_COLORS[i], 2.5f));
                        }
                        world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, 4, 0), 3, 0.2, 0.5, 0.2, 0.02);
                    }
                }

                // Hexágono en el centro
                for (int i = 0; i < 24; i++) {
                    double a = (Math.PI * 2 / 24) * i;
                    world.spawnParticle(Particle.DUST,
                            ritualCenter.clone().add(Math.cos(a) * 3, 0.2, Math.sin(a) * 3),
                            1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(120, 0, 180), 1.0f));
                }

                tick++;
            }
        }.runTaskTimer(plugin, 20, 5);
    }

    private void restoreBlocks() {
        for (Map.Entry<Location, BlockData> entry : originalBlocks.entrySet()) {
            Block block = entry.getKey().getBlock();
            block.setBlockData(entry.getValue());
        }
        originalBlocks.clear();
    }

    // ==================== GETTERS ====================

    public boolean isActive() {
        return active;
    }

    public boolean isChanneling() {
        return channeling;
    }

    public void cleanup() {
        active = false;
        channeling = false;
        if (ritualTask != null)
            ritualTask.cancel();
        if (channelTask != null)
            channelTask.cancel();
        if (detectionTask != null)
            detectionTask.cancel();
        restoreBlocks();
    }
}
