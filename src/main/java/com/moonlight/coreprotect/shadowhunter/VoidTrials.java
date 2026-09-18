package com.moonlight.coreprotect.shadowhunter;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Fase 1 de la Misión 3: Las 4 Pruebas del Arquitecto.
 * 1. Prueba de la Oscuridad — encontrar cristales de eco con sonidos
 * 2. Prueba del Laberinto — laberinto invisible con partículas guía
 * 3. Prueba de Confianza — tirar el mejor item al suelo
 * 4. Prueba de Memoria — secuencia de colores
 */
public class VoidTrials {

    private final CoreProtectPlugin plugin;
    private final ShadowHunterManager manager;
    private final Player player;
    private final ShadowHunterNPC npc;

    private int currentTrial = 0;
    private boolean active = false;
    private BukkitRunnable currentTask;

    // Prueba 1: cristales
    private final List<ArmorStand> echoCrystals = new ArrayList<>();
    private int crystalsFound = 0;

    // Prueba 3: confianza
    private ItemStack storedItem;

    // Prueba 4: memoria
    private final List<Material> colorSequence = new ArrayList<>();
    private int memoryRound = 0;
    private int memoryIndex = 0;
    private boolean memoryInputPhase = false;
    private final List<Location> colorBlockLocations = new ArrayList<>();

    private static final Material[] WOOL_COLORS = {
            Material.RED_WOOL, Material.BLUE_WOOL, Material.YELLOW_WOOL,
            Material.GREEN_WOOL, Material.ORANGE_WOOL
    };

    public VoidTrials(CoreProtectPlugin plugin, ShadowHunterManager manager, Player player, ShadowHunterNPC npc) {
        this.plugin = plugin;
        this.manager = manager;
        this.player = player;
        this.npc = npc;
    }

    public void start() {
        active = true;

        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§0§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        player.sendMessage(SmallCaps.convert("  §0§l✦ LAS PRUEBAS DEL ARQUITECTO ✦"));
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("  §c§l⚠ AVISO IMPORTANTE ⚠"));
        player.sendMessage(SmallCaps.convert("  §7Esta misión requiere tener las §cPartículas activadas"));
        player.sendMessage(SmallCaps.convert("  §7al máximo en tus ajustes de video de Minecraft."));
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("  §7El Errante ha revelado su verdadera forma."));
        player.sendMessage(SmallCaps.convert("  §7Para obtener su última reliquia, debes"));
        player.sendMessage(SmallCaps.convert("  §7superar §f4 pruebas §7de voluntad..."));
        player.sendMessage(SmallCaps.convert("§0§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        player.sendMessage("");

        player.sendTitle( SmallCaps.convert("§0§l✦ PRUEBA 1 ✦"), SmallCaps.convert("§7La Oscuridad"), 10, 60, 20);
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_AMBIENT, 0.8f, 0.5f);

        Bukkit.getScheduler().runTaskLater(plugin, this::startTrial1, 80L);
    }

    // ==================== PRUEBA 1: LA OSCURIDAD ====================

    private void startTrial1() {
        if (!active)
            return;
        currentTrial = 1;
        crystalsFound = 0;

        if (npc != null)
            npc.say("\"Cierra los ojos... y escucha. Los cristales te llaman.\"");

        // Cegar al jugador
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 99999, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 99999, 0, false, false));

        // Spawn 5 cristales de eco (armor stands invisibles con partículas)
        Location center = player.getLocation();
        Random rand = new Random();

        for (int i = 0; i < 5; i++) {
            double dx = (rand.nextDouble() - 0.5) * 20;
            double dz = (rand.nextDouble() - 0.5) * 20;
            Location loc = center.clone().add(dx, 0, dz);

            // Asegurar que está sobre el suelo
            loc.setY(center.getWorld().getHighestBlockYAt(loc) + 1);

            ArmorStand crystal = center.getWorld().spawn(loc, ArmorStand.class, as -> {
                as.setVisible(false);
                as.setSmall(true);
                as.setGravity(false);
                as.setMarker(false); // No marker para que tenga hitbox
                as.setCustomName(SmallCaps.convert("§b§lCristal de Eco §7[" + (echoCrystals.size() + 1) + "/5]"));
                as.setCustomNameVisible(false);
                as.setInvulnerable(true);
            });

            echoCrystals.add(crystal);
        }

        player.sendMessage(SmallCaps.convert("§b§l✦ §7Encuentra los §b5 Cristales de Eco §7siguiendo los sonidos."));
        player.sendMessage(SmallCaps.convert("§7§oAcércate a menos de 3 bloques de cada cristal..."));

        // Tarea: partículas y sonidos guía desde los cristales + detección
        currentTask = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!active || crystalsFound >= 5 || ticks > 1200) { // 1 min max
                    cancel();
                    if (crystalsFound >= 5) {
                        completeTrial1();
                    } else if (ticks > 1200) {
                        player.sendMessage(SmallCaps.convert("§c§l✖ §cTiempo agotado. Reiniciando prueba..."));
                        cleanupTrial1();
                        Bukkit.getScheduler().runTaskLater(plugin, () -> startTrial1(), 40L);
                    }
                    return;
                }

                // Partículas en los cristales y sonido guía
                for (ArmorStand crystal : echoCrystals) {
                    if (crystal.isDead())
                        continue;
                    Location loc = crystal.getLocation();

                    // Partículas visibles (brillan en la oscuridad)
                    loc.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0, 0.5, 0),
                            3, 0.15, 0.3, 0.15, 0.01);

                    // Sonido que aumenta cuanto más cerca estés
                    double dist = loc.distance(player.getLocation());
                    if (dist < 15 && ticks % 20 == 0) {
                        float volume = (float) Math.max(0.1, 1.0 - dist / 15.0);
                        float pitch = (float) Math.max(0.5, 2.0 - dist / 10.0);
                        player.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, volume, pitch);
                    }

                    // Detección: si el jugador está a menos de 3 bloques
                    if (dist < 3.0) {
                        crystalsFound++;
                        crystal.remove();
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
                        player.sendTitle("", SmallCaps.convert("§b§l✦ §fCristal §b" + crystalsFound + "§f/5 §b§l✦"), 0, 30, 10);

                        loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 20, 0.3, 0.3, 0.3, 0.1);
                    }
                }

                ticks++;
            }
        };
        currentTask.runTaskTimer(plugin, 0, 1);
    }

    private void completeTrial1() {
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        cleanupTrial1();

        player.sendTitle( SmallCaps.convert("§a§l✔ PRUEBA 1 SUPERADA"), SmallCaps.convert("§7La Oscuridad no te detuvo"), 10, 60, 20);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        if (npc != null)
            npc.say("\"Puedes ver sin ojos... Impresionante.\"");

        Bukkit.getScheduler().runTaskLater(plugin, this::startTrial2, 80L);
    }

    private void cleanupTrial1() {
        for (ArmorStand as : echoCrystals) {
            if (!as.isDead())
                as.remove();
        }
        echoCrystals.clear();
    }

    // ==================== PRUEBA 2: EL LABERINTO ====================

    private void startTrial2() {
        if (!active)
            return;
        currentTrial = 2;

        player.sendTitle( SmallCaps.convert("§0§l✦ PRUEBA 2 ✦"), SmallCaps.convert("§7El Laberinto de Espejos"), 10, 60, 20);
        if (npc != null)
            npc.say("\"Lo que ves no es lo que hay. Solo las partículas dicen la verdad.\"");

        player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 0.5f, 1.5f);

        Location center = player.getLocation().clone();
        World world = center.getWorld();

        // Generar laberinto con barreras invisibles 11x11
        List<Location> barriers = new ArrayList<>();
        Location mazeOrigin = center.clone().add(-5, 0, -5);
        int[][] maze = generateSimpleMaze();

        // Entrada: fila 0, columna 5 (hay un 0 en maze[0][5])
        // Meta: fila 10, columna 5 (hay un 0 en maze[10][5])
        Location entrance = mazeOrigin.clone().add(5.5, 0, 0.5);
        Location goal = mazeOrigin.clone().add(5.5, 0, 10.5);

        for (int x = 0; x < 11; x++) {
            for (int z = 0; z < 11; z++) {
                if (maze[x][z] == 1) {
                    Location wallLoc = mazeOrigin.clone().add(x, 0, z);
                    for (int dy = 0; dy < 3; dy++) {
                        Block b = world.getBlockAt(wallLoc.getBlockX(), wallLoc.getBlockY() + dy,
                                wallLoc.getBlockZ());
                        if (b.getType().isAir()) {
                            b.setType(Material.BARRIER);
                            barriers.add(b.getLocation());
                        }
                    }
                }
            }
        }

        // Teleportar al jugador a la entrada del laberinto
        Location tpLoc = entrance.clone();
        tpLoc.setY(tpLoc.getY() + 1);
        tpLoc.setYaw(player.getLocation().getYaw());
        tpLoc.setPitch(player.getLocation().getPitch());
        player.teleport(tpLoc);

        player.sendMessage(SmallCaps.convert("§7§l✦ §7Alcanza la §b✦ marca dorada §7al otro extremo del laberinto. §e60 segundos§7."));
        player.sendMessage(SmallCaps.convert("§7Las §fpartículas en el suelo §7marcan el camino..."));

        // Partículas guía en el camino correcto + detección de llegada
        currentTask = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!active || ticks > 1200) {
                    cancel();
                    // Limpiar barreras
                    for (Location loc : barriers) {
                        loc.getBlock().setType(Material.AIR);
                    }
                    if (ticks > 1200) {
                        player.sendMessage(SmallCaps.convert("§c§l✖ §cTiempo agotado. Reiniciando prueba..."));
                        Bukkit.getScheduler().runTaskLater(plugin, () -> startTrial2(), 40L);
                    }
                    return;
                }

                // Partículas guía en el suelo del laberinto
                if (ticks % 10 == 0) {
                    for (int x = 0; x < 11; x++) {
                        for (int z = 0; z < 11; z++) {
                            if (maze[x][z] == 0) {
                                Location pathLoc = mazeOrigin.clone().add(x + 0.5, 0.1, z + 0.5);
                                if (pathLoc.distance(player.getLocation()) < 8) {
                                    world.spawnParticle(Particle.DUST, pathLoc, 1, 0.1, 0, 0.1, 0,
                                            new Particle.DustOptions(Color.fromRGB(0, 200, 255), 0.8f));
                                }
                            }
                        }
                    }
                }

                // Partículas del objetivo (más intensas)
                if (ticks % 5 == 0) {
                    world.spawnParticle(Particle.END_ROD, goal.clone().add(0.5, 1, 0.5),
                            5, 0.2, 0.5, 0.2, 0.02);
                }

                // Check si llegó al objetivo
                if (player.getLocation().distance(goal) < 2.5) {
                    cancel();
                    // Limpiar
                    for (Location loc : barriers) {
                        loc.getBlock().setType(Material.AIR);
                    }
                    completeTrial2();
                }

                ticks++;
            }
        };
        currentTask.runTaskTimer(plugin, 0, 1);
    }

    private int[][] generateSimpleMaze() {
        // Laberinto sencillo predefinido 11x11 (0=camino, 1=pared)
        return new int[][] {
                { 1, 1, 1, 1, 1, 0, 1, 1, 1, 1, 1 },
                { 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1 },
                { 1, 0, 1, 0, 1, 1, 1, 1, 1, 0, 1 },
                { 1, 0, 1, 0, 0, 0, 0, 0, 1, 0, 1 },
                { 1, 0, 1, 1, 1, 1, 1, 0, 1, 0, 1 },
                { 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1 },
                { 1, 0, 1, 1, 1, 0, 1, 1, 1, 0, 1 },
                { 1, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1 },
                { 1, 0, 1, 0, 1, 1, 1, 0, 1, 1, 1 },
                { 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1 },
                { 1, 1, 1, 1, 1, 0, 1, 1, 1, 1, 1 },
        };
    }

    private void completeTrial2() {
        player.sendTitle( SmallCaps.convert("§a§l✔ PRUEBA 2 SUPERADA"), SmallCaps.convert("§7Pudiste ver a través del espejismo"), 10, 60, 20);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        if (npc != null)
            npc.say("\"La ilusión no te engañó. Interesante.\"");

        Bukkit.getScheduler().runTaskLater(plugin, this::startTrial3, 80L);
    }

    // ==================== PRUEBA 3: CONFIANZA ====================

    private void startTrial3() {
        if (!active)
            return;
        currentTrial = 3;

        player.sendTitle( SmallCaps.convert("§0§l✦ PRUEBA 3 ✦"), SmallCaps.convert("§7La Confianza"), 10, 60, 20);
        player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 0.8f);

        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§0§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        player.sendMessage(SmallCaps.convert("  §c§l⚠ PRUEBA DE CONFIANZA ⚠"));
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("  §7El Errante exige que §cdropees tu mejor arma§7."));
        player.sendMessage(SmallCaps.convert("  §7Tira el ítem que tengas en la mano al suelo."));
        player.sendMessage(SmallCaps.convert("  §e§oTienes 30 segundos..."));
        player.sendMessage(SmallCaps.convert("§0§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        player.sendMessage("");

        if (npc != null)
            npc.say("\"¿Confías en mí? Demuéstralo. Suelta lo que más valoras.\"");

        // Detectar drop de item
        currentTask = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!active || ticks > 600) { // 30 seg
                    cancel();
                    if (ticks > 600) {
                        player.sendMessage(SmallCaps.convert("§c§l✖ §cNo confiaste. La prueba se reinicia..."));
                        if (npc != null)
                            npc.say("\"Tu falta de confianza te define...\"");
                        Bukkit.getScheduler().runTaskLater(plugin, () -> startTrial3(), 60L);
                    }
                    return;
                }

                // Check si el jugador tiró algo
                player.getWorld().getNearbyEntities(player.getLocation(), 5, 3, 5).stream()
                        .filter(e -> e instanceof org.bukkit.entity.Item)
                        .map(e -> (org.bukkit.entity.Item) e)
                        .filter(item -> item.getThrower() != null && item.getThrower().equals(player.getUniqueId()))
                        .findFirst()
                        .ifPresent(droppedItem -> {
                            cancel();
                            storedItem = droppedItem.getItemStack().clone();
                            droppedItem.remove();

                            // Devolver el item
                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                player.getInventory().addItem(storedItem);
                                player.sendTitle("§a§l✔ PRUEBA 3 SUPERADA", "§7Tu confianza fue recompensada", 10, 60,
                                        20);
                                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
                                if (npc != null)
                                    npc.say("\"Confiaste... y fuiste recompensado. Tu ítem ha sido devuelto.\"");

                                Bukkit.getScheduler().runTaskLater(plugin, () -> startTrial4(), 80L);
                            }, 40L);
                        });

                // Sonido de presión cada 5 seg
                if (ticks % 100 == 0 && ticks > 0) {
                    int segsLeft = (600 - ticks) / 20;
                    player.sendTitle("", SmallCaps.convert("§c§l" + segsLeft + "s §7restantes..."), 0, 25, 5);
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
                }

                ticks++;
            }
        };
        currentTask.runTaskTimer(plugin, 0, 1);
    }

    // ==================== PRUEBA 4: MEMORIA ====================

    private void startTrial4() {
        if (!active)
            return;
        currentTrial = 4;
        memoryRound = 0;
        colorSequence.clear();

        player.sendTitle( SmallCaps.convert("§0§l✦ PRUEBA 4 ✦"), SmallCaps.convert("§7La Memoria"), 10, 60, 20);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);

        if (npc != null)
            npc.say("\"¿Puedes recordar lo que ves? Observa y repite.\"");

        // Colocar 5 bloques de lana en una fila delante del jugador
        Location start = player.getLocation().clone().add(
                player.getLocation().getDirection().normalize().multiply(3));
        
        Vector right = player.getLocation().getDirection().normalize()
                .crossProduct(new Vector(0, 1, 0)).normalize();

        colorBlockLocations.clear();
        for (int i = 0; i < 5; i++) {
            Location loc = start.clone().add(right.clone().multiply((i - 2) * 2));
            // SIEMPRE colocar en la superficie, nunca bajo tierra
            int surfaceY = loc.getWorld().getHighestBlockYAt(loc) + 1;
            loc.setY(surfaceY);
            loc.getBlock().setType(Material.WHITE_WOOL);
            colorBlockLocations.add(loc.clone());
        }

        player.sendMessage(SmallCaps.convert("§7Observa la secuencia de colores y §egolpea los bloques §7en el mismo orden."));
        player.sendMessage(SmallCaps.convert("§7Ronda §e1§7/3 — §f3 colores§7."));

        Bukkit.getScheduler().runTaskLater(plugin, this::nextMemoryRound, 60L);
    }

    private void nextMemoryRound() {
        if (!active)
            return;
        memoryRound++;
        memoryIndex = 0;
        memoryInputPhase = false;

        int count = memoryRound + 2; // 3, 4, 5 colores

        // Añadir nuevos colores a la secuencia
        Random rand = new Random();
        while (colorSequence.size() < count) {
            colorSequence.add(WOOL_COLORS[rand.nextInt(WOOL_COLORS.length)]);
        }

        // Mostrar la secuencia
        player.sendMessage(SmallCaps.convert("§e§l¡Observa! §7Secuencia de §f" + count + " §7colores:"));

        // Reset bloques a blanco
        for (Location loc : colorBlockLocations) {
            loc.getBlock().setType(Material.WHITE_WOOL);
        }

        // Mostrar uno por uno
        for (int i = 0; i < count; i++) {
            final int idx = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!active)
                    return;

                // Resetear el anterior
                if (idx > 0 && idx - 1 < colorBlockLocations.size()) {
                    colorBlockLocations
                            .get(idx > colorBlockLocations.size() - 1 ? colorBlockLocations.size() - 1 : idx - 1)
                            .getBlock().setType(Material.WHITE_WOOL);
                }

                // Mostrar el actual
                int blockIdx = idx % colorBlockLocations.size();
                Material wool = colorSequence.get(idx);
                colorBlockLocations.get(blockIdx).getBlock().setType(wool);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f,
                        0.5f + idx * 0.2f);
            }, 30L + idx * 25L);
        }

        // Después de mostrar, iniciar fase de input
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active)
                return;
            for (Location loc : colorBlockLocations) {
                loc.getBlock().setType(Material.WHITE_WOOL);
            }

            // Colocar los 5 colores disponibles para golpear
            for (int i = 0; i < 5 && i < colorBlockLocations.size(); i++) {
                colorBlockLocations.get(i).getBlock().setType(WOOL_COLORS[i]);
            }

            memoryInputPhase = true;
            memoryIndex = 0;
            player.sendMessage(SmallCaps.convert("§a§l¡Tu turno! §7Rompe los bloques en el orden correcto."));
        }, 30L + count * 25L + 20L);
    }

    /**
     * Llamado desde el listener cuando el jugador rompe un bloque de lana durante
     * la prueba 4.
     */
    public boolean onBlockHit(Material woolType, Location loc) {
        if (!active || currentTrial != 4 || !memoryInputPhase)
            return false;

        // Verificar si es un bloque del puzzle
        boolean isMemoryBlock = false;
        for (Location bl : colorBlockLocations) {
            if (bl.getBlockX() == loc.getBlockX() && bl.getBlockY() == loc.getBlockY()
                    && bl.getBlockZ() == loc.getBlockZ()) {
                isMemoryBlock = true;
                break;
            }
        }
        if (!isMemoryBlock)
            return false;

        // Verificar el color correcto
        if (memoryIndex < colorSequence.size() && woolType == colorSequence.get(memoryIndex)) {
            memoryIndex++;
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.5f);

            // Restaurar el bloque (no romperlo)
            Bukkit.getScheduler().runTaskLater(plugin, () -> loc.getBlock().setType(woolType), 1L);

            if (memoryIndex >= colorSequence.size()) {
                // Ronda completada
                if (memoryRound >= 3) {
                    // ¡Prueba 4 completada!
                    completeTrial4();
                } else {
                    player.sendMessage(SmallCaps.convert("§a§l✔ §aRonda " + memoryRound + " completada."));
                    player.sendMessage(SmallCaps.convert("§7Ronda §e" + (memoryRound + 1) + "§7/3..."));
                    Bukkit.getScheduler().runTaskLater(plugin, this::nextMemoryRound, 40L);
                }
            }
            return true;
        } else {
            // Error — reiniciar ronda y mostrar secuencia otra vez
            player.sendMessage(SmallCaps.convert("§c§l✖ §c¡Color incorrecto! Volviendo a mostrar la secuencia..."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);

            // Restar 1 a memoryRound para que nextMemoryRound vuelva a jugar la misma ronda
            // y tamaño, pero genere nueva secuencia
            memoryRound--;

            // Restaurar bloque
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                loc.getBlock().setType(woolType);
                nextMemoryRound();
            }, 20L);
            return true;
        }
    }

    private void completeTrial4() {
        memoryInputPhase = false;

        // Limpiar bloques de colores
        for (Location loc : colorBlockLocations) {
            loc.getBlock().setType(Material.AIR);
        }
        colorBlockLocations.clear();
        colorSequence.clear();

        player.sendTitle( SmallCaps.convert("§a§l✔ PRUEBA 4 SUPERADA"), SmallCaps.convert("§7Tu mente es afilada como una hoja"), 10, 60, 20);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        if (npc != null)
            npc.say("\"Todas las pruebas... superadas. Ahora viene lo verdadero.\"");

        // Todas las pruebas completadas
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active)
                return;
            player.sendTitle( SmallCaps.convert("§0§l✦ PRUEBAS SUPERADAS ✦"), SmallCaps.convert("§7El Arquitecto te espera..."), 10, 80, 20);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (active)
                    manager.onQuest3TrialsCompleted(player);
            }, 80L);
        }, 60L);
    }

    public boolean isActive() {
        return active;
    }

    public int getCurrentTrial() {
        return currentTrial;
    }

    public boolean isMemoryInputPhase() {
        return memoryInputPhase;
    }

    public void cleanup() {
        active = false;
        if (currentTask != null) {
            try {
                currentTask.cancel();
            } catch (Exception ignored) {
            }
        }
        cleanupTrial1();
        for (Location loc : colorBlockLocations) {
            if (loc.getBlock().getType().name().contains("WOOL")) {
                loc.getBlock().setType(Material.AIR);
            }
        }
        colorBlockLocations.clear();
        colorSequence.clear();

        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
    }
}
