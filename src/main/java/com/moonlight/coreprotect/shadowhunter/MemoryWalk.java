package com.moonlight.coreprotect.shadowhunter;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Misión 6 — Fase de tareas únicas: "Camino de Recuerdos" + "Abrazo de Almas"
 *
 * FASE A — Camino de Recuerdos:
 *   Un sendero de soul-fire aparece bloque a bloque frente al jugador.
 *   A lo largo del camino, escenas fantasmales (armor stands) muestran
 *   memorias de la vida de Lyra. El jugador debe caminar a través de cada
 *   escena para "recoger" la memoria. Cada memoria revela un fragmento.
 *
 * FASE B — Abrazo de Almas:
 *   Entidades fantasmales tristes (allays) aparecen alrededor del jugador.
 *   No son hostiles. El jugador debe acercarse y hacer click derecho para
 *   "abrazarlas" y liberar su dolor. Sin combate, solo compasión.
 */
public class MemoryWalk {

    private final CoreProtectPlugin plugin;
    private final ShadowHunterManager manager;
    private final Player player;
    private final ShadowHunterNPC npc;

    private boolean active = false;
    private BukkitTask mainTask;
    private BukkitTask ambientTask;

    // Phase A: Memory Path
    private final List<Location> pathBlocks = new ArrayList<>();
    private final List<ArmorStand> memoryScenes = new ArrayList<>();
    private int pathIndex = 0;
    private int memoriesCollected = 0;
    private static final int TOTAL_MEMORIES = 6;
    private boolean phaseAComplete = false;
    private final Set<Integer> spawnedMemories = new HashSet<>();

    // Phase B: Soul Comfort
    private final List<Entity> lostSouls = new ArrayList<>();
    private int soulsComforted = 0;
    private static final int TOTAL_SOULS = 5;
    private boolean phaseBComplete = false;

    // Memory fragments — the story of Lyra
    private static final String[][] MEMORIES = {
            {"§b§l✦ Recuerdo I §7— §fLa Infancia",
                    "§7§o*Una niña pequeña corre por un campo de flores...*",
                    "§f\"¡Papá, papá! ¡Mira lo que encontré!\"",
                    "§7§o*Su padre sonríe. Todo era perfecto entonces.*"},
            {"§b§l✦ Recuerdo II §7— §fLa Promesa",
                    "§7§o*Un hombre con capa oscura se arrodilla ante su hija...*",
                    "§f\"Volveré pronto, Lyra. Te lo prometo.\"",
                    "§b\"¿Lo prometes de verdad, papá?\""},
            {"§b§l✦ Recuerdo III §7— §fLa Espera",
                    "§7§o*La niña mira por la ventana cada noche...*",
                    "§7§o*Pasan días. Semanas. Meses.*",
                    "§b\"Papá dijo que volvería... ¿verdad?\""},
            {"§b§l✦ Recuerdo IV §7— §fLos Susurros",
                    "§7§o*La oscuridad empieza a hablarle...*",
                    "§8\"Tu padre no va a volver. Te abandonó.\"",
                    "§b\"No... él prometió... él prometió...\""},
            {"§b§l✦ Recuerdo V §7— §fLa Soledad",
                    "§7§o*Lyra ya no sale de casa. Ya no come. Ya no duerme.*",
                    "§7§o*Los susurros son lo único que la acompaña.*",
                    "§8\"¿Para qué seguir si nadie te quiere?\""},
            {"§b§l✦ Recuerdo VI §7— §fEl Borde",
                    "§7§o*Lyra camina hasta el borde del mundo...*",
                    "§7§o*Mira hacia abajo. El vacío le devuelve la mirada.*",
                    "§b\"Ya no puedo más... Adiós, papá.\""}
    };

    // Soul fragments — what the lost souls say
    private static final String[][] SOUL_MESSAGES = {
            {"§b§l♡ Alma Perdida §7susurra:",
                    "§f\"Yo también esperé a alguien que nunca volvió...\"",
                    "§a§o*El alma se ilumina con tu abrazo y asciende en paz.*"},
            {"§b§l♡ Alma Perdida §7susurra:",
                    "§f\"El vacío no duele tanto como la soledad...\"",
                    "§a§o*Tu compasión libera su dolor eterno.*"},
            {"§b§l♡ Alma Perdida §7susurra:",
                    "§f\"Nadie vino a buscarme... hasta ahora.\"",
                    "§a§o*El alma sonríe por primera vez en siglos.*"},
            {"§b§l♡ Alma Perdida §7susurra:",
                    "§f\"Pensé que nadie me recordaba...\"",
                    "§a§o*Las lágrimas del alma se convierten en luz.*"},
            {"§b§l♡ Alma Perdida §7susurra:",
                    "§f\"Gracias por no mirar hacia otro lado...\"",
                    "§a§o*El alma se disuelve en estrellas, finalmente libre.*"}
    };

    public MemoryWalk(CoreProtectPlugin plugin, ShadowHunterManager manager, Player player, ShadowHunterNPC npc) {
        this.plugin = plugin;
        this.manager = manager;
        this.player = player;
        this.npc = npc;
    }

    public void start() {
        active = true;

        // Intro
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§b§l✦ §7═══════════════════════════════════"));
        player.sendMessage(SmallCaps.convert("§b§l  CAMINO DE RECUERDOS"));
        player.sendMessage(SmallCaps.convert("§7  Sigue el sendero de almas y recoge"));
        player.sendMessage(SmallCaps.convert("§7  los recuerdos de Lyra."));
        player.sendMessage(SmallCaps.convert("§b§l✦ §7═══════════════════════════════════"));
        player.sendMessage("");
        player.sendTitle(SmallCaps.convert("§b§lCamino de Recuerdos"), SmallCaps.convert("§7Sigue el sendero luminoso..."), 10, 60, 20);

        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 0.4f);
        player.playSound(player.getLocation(), Sound.ENTITY_ALLAY_AMBIENT_WITH_ITEM, 0.8f, 0.6f);

        // Disable fly
        if (player.getAllowFlight()) {
            player.setAllowFlight(false);
            player.setFlying(false);
        }

        // Generate the soul path
        Bukkit.getScheduler().runTaskLater(plugin, this::startPhaseA, 80L);
    }

    // ==================== PHASE A: MEMORY PATH ====================

    private void startPhaseA() {
        if (!active) return;

        Location start = player.getLocation().clone();
        World world = start.getWorld();

        // Generate a winding path of ~60 blocks
        generatePath(start);

        // Start the path illumination
        mainTask = new BukkitRunnable() {
            int tick = 0;
            int revealedUpTo = 0;
            int nextMemoryAt = 10; // First memory at block 10

            @Override
            public void run() {
                if (!active || !player.isOnline()) {
                    cancel();
                    return;
                }

                tick++;

                // Reveal path blocks gradually (1 every 4 ticks = 5/sec)
                if (tick % 4 == 0 && revealedUpTo < pathBlocks.size()) {
                    Location blockLoc = pathBlocks.get(revealedUpTo);
                    // Show soul fire particles on the path
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, blockLoc.clone().add(0.5, 0.1, 0.5),
                            5, 0.3, 0.05, 0.3, 0.01);
                    world.spawnParticle(Particle.DUST, blockLoc.clone().add(0.5, 0.2, 0.5),
                            3, 0.2, 0.1, 0.2, 0, new Particle.DustOptions(Color.fromRGB(100, 200, 255), 1.0f));
                    world.playSound(blockLoc, Sound.BLOCK_AMETHYST_BLOCK_STEP, 0.3f, 1.2f);
                    revealedUpTo++;
                }

                // Check if player walked through a memory point
                if (memoriesCollected < TOTAL_MEMORIES) {
                    int memoryBlockIndex = nextMemoryAt;
                    if (memoryBlockIndex < pathBlocks.size()) {
                        Location memLoc = pathBlocks.get(memoryBlockIndex);
                        // Increased detection range from 3 to 5 blocks for better detection
                        if (player.getLocation().distanceSquared(memLoc) < 25) { // Within 5 blocks
                            collectMemory(memoriesCollected, memLoc);
                            memoriesCollected++;
                            nextMemoryAt += 8 + (int)(Math.random() * 4); // Next memory 8-12 blocks later

                            if (memoriesCollected >= TOTAL_MEMORIES) {
                                // Phase A complete
                                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                    phaseAComplete = true;
                                    transitionToPhaseB();
                                }, 80L);
                            }
                        }
                    }
                }

                // Ambient path particles for revealed blocks
                if (tick % 10 == 0) {
                    for (int i = Math.max(0, revealedUpTo - 15); i < revealedUpTo && i < pathBlocks.size(); i++) {
                        Location bl = pathBlocks.get(i);
                        world.spawnParticle(Particle.SOUL_FIRE_FLAME, bl.clone().add(0.5, 0.05, 0.5),
                                1, 0.2, 0.02, 0.2, 0.005);
                    }
                }

                // Spawn memory scene armor stands ahead of player
                if (tick % 40 == 0 && memoriesCollected < TOTAL_MEMORIES) {
                    int nextMem = 10 + memoriesCollected * 10;
                    if (nextMem < pathBlocks.size() && nextMem <= revealedUpTo + 5) {
                        spawnMemoryScene(pathBlocks.get(nextMem), memoriesCollected);
                    }
                }

                // Guide arrow particles pointing to next path block
                if (tick % 20 == 0 && revealedUpTo > 0) {
                    int targetIdx = Math.min(revealedUpTo - 1, pathBlocks.size() - 1);
                    // Find nearest unvisited block
                    for (int i = 0; i < revealedUpTo && i < pathBlocks.size(); i++) {
                        if (player.getLocation().distanceSquared(pathBlocks.get(i)) > 4) {
                            targetIdx = i;
                            break;
                        }
                    }
                    Location target = pathBlocks.get(targetIdx);
                    Vector dir = target.toVector().subtract(player.getLocation().toVector()).normalize();
                    for (double d = 1; d < 3; d += 0.5) {
                        world.spawnParticle(Particle.END_ROD,
                                player.getLocation().add(0, 1.5, 0).add(dir.clone().multiply(d)),
                                1, 0, 0, 0, 0);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 1L);

        // Ambient task
        ambientTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!active || !player.isOnline()) {
                    cancel();
                    return;
                }
                World w = player.getWorld();
                Location pLoc = player.getLocation();
                // Ambient soul particles
                for (int i = 0; i < 3; i++) {
                    double a = Math.random() * Math.PI * 2;
                    double d = 3 + Math.random() * 5;
                    w.spawnParticle(Particle.SOUL, pLoc.clone().add(Math.cos(a) * d, 1 + Math.random() * 3, Math.sin(a) * d),
                            1, 0, 0.1, 0, 0.01);
                }
                // Ambient sound
                if (Math.random() < 0.3) {
                    w.playSound(pLoc, Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 0.2f, 0.4f + (float)(Math.random() * 0.3));
                }
            }
        }.runTaskTimer(plugin, 0L, 15L);
    }

    private void generatePath(Location start) {
        pathBlocks.clear();
        Location current = start.clone();
        World world = start.getWorld();
        double angle = Math.toRadians(player.getLocation().getYaw() + 90); // Forward direction

        for (int i = 0; i < 70; i++) {
            // Slightly curve the path
            angle += (Math.random() - 0.5) * 0.4;

            double dx = Math.cos(angle) * 1.5;
            double dz = Math.sin(angle) * 1.5;
            current = current.clone().add(dx, 0, dz);

            // Find ground level
            Block ground = world.getHighestBlockAt(current);
            Location pathLoc = ground.getLocation().clone();
            pathLoc.setY(ground.getY());

            // Validate location is safe (not water, lava, or void)
            Material groundType = ground.getType();
            if (groundType == Material.WATER || groundType == Material.LAVA || 
                groundType == Material.AIR || groundType == Material.CAVE_AIR || 
                groundType == Material.VOID_AIR || pathLoc.getY() < 0) {
                // Skip unsafe locations, try to find safer spot nearby
                boolean foundSafe = false;
                for (int attempt = 0; attempt < 8; attempt++) {
                    double offsetAngle = (Math.PI * 2 / 8) * attempt;
                    Location testLoc = pathLoc.clone().add(
                        Math.cos(offsetAngle) * 2, 0, Math.sin(offsetAngle) * 2);
                    Block testGround = world.getHighestBlockAt(testLoc);
                    Material testType = testGround.getType();
                    
                    if (testType != Material.WATER && testType != Material.LAVA && 
                        testType != Material.AIR && testType != Material.CAVE_AIR && 
                        testType != Material.VOID_AIR && testGround.getY() >= 0 &&
                        testType.isSolid()) {
                        pathLoc = testGround.getLocation().clone();
                        pathLoc.setY(testGround.getY());
                        current = pathLoc.clone();
                        foundSafe = true;
                        break;
                    }
                }
                if (!foundSafe) {
                    // Skip this block if no safe spot found
                    continue;
                }
            }

            pathBlocks.add(pathLoc);
        }
    }

    private void spawnMemoryScene(Location loc, int memoryIndex) {
        if (memoryIndex >= TOTAL_MEMORIES) return;
        if (spawnedMemories.contains(memoryIndex)) return; // Don't spawn duplicates
        World world = loc.getWorld();

        // Create a ghostly armor stand representing a memory scene
        ArmorStand stand = world.spawn(loc.clone().add(0.5, 0, 0.5), ArmorStand.class, as -> {
            as.setVisible(false);
            as.setGravity(false);
            as.setSmall(false);
            as.setMarker(false);
            as.setInvulnerable(true);
            as.setSilent(true);
            as.setCustomNameVisible(true);
            as.setGlowing(true);

            // Different names for different memories
            String[] sceneNames = {
                    "§b§l✦ §fRecuerdo Luminoso",
                    "§b§l✦ §fPromesa Olvidada",
                    "§b§l✦ §fEspera Eterna",
                    "§b§l✦ §8Susurros Oscuros",
                    "§b§l✦ §7Soledad Infinita",
                    "§b§l✦ §4El Borde del Mundo"
            };
            as.setCustomName(SmallCaps.convert(sceneNames[memoryIndex]));

            // Ghost armor (light blue leather)
            ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
            LeatherArmorMeta hm = (LeatherArmorMeta) helmet.getItemMeta();
            hm.setColor(Color.fromRGB(150, 200, 240));
            helmet.setItemMeta(hm);
            as.getEquipment().setHelmet(helmet);

            ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
            LeatherArmorMeta cm = (LeatherArmorMeta) chest.getItemMeta();
            cm.setColor(Color.fromRGB(130, 190, 230));
            chest.setItemMeta(cm);
            as.getEquipment().setChestplate(chest);

            as.addScoreboardTag("lyra_memory");
        });

        memoryScenes.add(stand);
        spawnedMemories.add(memoryIndex); // Track that this memory was spawned

        // Particles around the memory
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (!active || stand.isDead() || t > 600) {
                    cancel();
                    return;
                }
                world.spawnParticle(Particle.SOUL, stand.getLocation().add(0, 1.5, 0),
                        2, 0.3, 0.5, 0.3, 0.01);
                world.spawnParticle(Particle.DUST, stand.getLocation().add(0, 1, 0),
                        1, 0.3, 0.5, 0.3, 0,
                        new Particle.DustOptions(Color.fromRGB(150, 210, 255), 1.2f));
                t++;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void collectMemory(int index, Location loc) {
        if (index >= TOTAL_MEMORIES) return;
        World world = loc.getWorld();

        // Remove the armor stand scene
        for (Iterator<ArmorStand> it = memoryScenes.iterator(); it.hasNext(); ) {
            ArmorStand as = it.next();
            if (as.getLocation().distanceSquared(loc) < 16) {
                // Dissolve effect
                world.spawnParticle(Particle.SOUL, as.getLocation().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.05);
                world.spawnParticle(Particle.END_ROD, as.getLocation().add(0, 1, 0), 20, 0.3, 0.8, 0.3, 0.1);
                as.remove();
                it.remove();
            }
        }

        // Show memory text
        player.sendMessage("");
        for (String line : MEMORIES[index]) {
            player.sendMessage(SmallCaps.convert(line));
        }
        player.sendMessage(SmallCaps.convert("§8  [Recuerdo " + (index + 1) + "/" + TOTAL_MEMORIES + "]"));
        player.sendMessage("");

        // Effects
        world.spawnParticle(Particle.DUST, loc.clone().add(0, 1.5, 0), 40, 1, 1, 1, 0,
                new Particle.DustOptions(Color.fromRGB(100, 200, 255), 2.0f));
        world.spawnParticle(Particle.SOUL, loc.clone().add(0, 2, 0), 15, 0.5, 1, 0.5, 0.05);

        // Sound — increasingly sad
        float pitch = 1.2f - (index * 0.12f);
        world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, pitch);
        world.playSound(loc, Sound.ENTITY_ALLAY_AMBIENT_WITH_ITEM, 0.8f, pitch);

        // Title
        String[] memTitles = {"§b✦ La Infancia", "§b✦ La Promesa", "§7✦ La Espera",
                "§8✦ Los Susurros", "§7✦ La Soledad", "§4✦ El Borde"};
        player.sendTitle(SmallCaps.convert(memTitles[index]), SmallCaps.convert("§7Recuerdo " + (index + 1) + " de " + TOTAL_MEMORIES), 5, 40, 15);
    }

    // ==================== PHASE B: SOUL COMFORT ====================

    private void transitionToPhaseB() {
        if (!active) return;

        // Cancel phase A task
        if (mainTask != null) mainTask.cancel();

        // Clean path particles
        cleanMemoryScenes();

        // Transition message
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§b§l✦ §7═══════════════════════════════════"));
        player.sendMessage(SmallCaps.convert("§b§l  ABRAZO DE ALMAS"));
        player.sendMessage(SmallCaps.convert("§7  Almas perdidas vagan a tu alrededor."));
        player.sendMessage(SmallCaps.convert("§7  §bAcércate y haz click derecho §7para"));
        player.sendMessage(SmallCaps.convert("§7  abrazarlas y liberar su dolor."));
        player.sendMessage(SmallCaps.convert("§b§l✦ §7═══════════════════════════════════"));
        player.sendMessage("");
        player.sendTitle(SmallCaps.convert("§b§lAbrazo de Almas"), SmallCaps.convert("§7Consuela a las almas perdidas..."), 10, 60, 20);

        player.playSound(player.getLocation(), Sound.ENTITY_ALLAY_DEATH, 0.8f, 0.4f);

        // Spawn lost souls
        Bukkit.getScheduler().runTaskLater(plugin, this::spawnLostSouls, 60L);
    }

    private void spawnLostSouls() {
        if (!active) return;

        World world = player.getWorld();
        Location center = player.getLocation();
        
        plugin.getLogger().info("[MemoryWalk] Spawneando " + TOTAL_SOULS + " almas cerca de " + player.getName());

        int spawned = 0;
        int maxAttempts = TOTAL_SOULS * 20; // 20 intentos por alma
        
        for (int attempt = 0; attempt < maxAttempts && spawned < TOTAL_SOULS; attempt++) {
            // Radio de 15-50 bloques del jugador
            double angle = Math.random() * Math.PI * 2;
            double dist = 15 + Math.random() * 35; // 15-50 bloques
            
            Location soulLoc = center.clone().add(
                Math.cos(angle) * dist, 
                0, 
                Math.sin(angle) * dist
            );

            // Buscar superficie (bloque más alto)
            Block ground = world.getHighestBlockAt(soulLoc);
            Material groundType = ground.getType();
            
            // Verificar que sea una ubicación segura y sólida
            if (groundType == Material.WATER || groundType == Material.LAVA || 
                groundType == Material.AIR || groundType == Material.CAVE_AIR || 
                groundType == Material.VOID_AIR || ground.getY() < 0 || 
                !groundType.isSolid()) {
                continue; // Intentar otra ubicación
            }
            
            // Verificar que haya espacio arriba para el alma
            Block above1 = ground.getRelative(0, 1, 0);
            Block above2 = ground.getRelative(0, 2, 0);
            if (!above1.getType().isAir() || !above2.getType().isAir()) {
                continue; // No hay espacio, intentar otra ubicación
            }
            
            // Ubicación válida encontrada
            soulLoc = ground.getLocation().clone().add(0.5, 1, 0.5);

            // Spawn del Allay (alma perdida)
            Allay allay = world.spawn(soulLoc, Allay.class, a -> {
                a.setCustomName(SmallCaps.convert("§b§l♡ §7Alma Perdida"));
                a.setCustomNameVisible(true);
                a.setInvulnerable(true);
                a.setSilent(true);
                a.setGlowing(true);
                a.setAI(false); // Sin IA para que no se mueva
                a.setCanPickupItems(false);
                a.addScoreboardTag("lyra_soul");
                a.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 999999, 0, false, false));
            });

            lostSouls.add(allay);
            spawned++;
            
            // Efecto visual de spawn
            world.spawnParticle(Particle.SOUL, soulLoc, 20, 0.5, 0.5, 0.5, 0.05);
            world.spawnParticle(Particle.END_ROD, soulLoc, 10, 0.3, 0.5, 0.3, 0.1);
            world.playSound(soulLoc, Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 0.5f, 0.6f);
            
            plugin.getLogger().info("[MemoryWalk] Alma " + spawned + "/" + TOTAL_SOULS + " spawneada en " + 
                soulLoc.getBlockX() + ", " + soulLoc.getBlockY() + ", " + soulLoc.getBlockZ() + 
                " (distancia: " + String.format("%.1f", center.distance(soulLoc)) + " bloques)");
        }
        
        if (spawned < TOTAL_SOULS) {
            plugin.getLogger().warning("[MemoryWalk] Solo se spawnearon " + spawned + "/" + TOTAL_SOULS + " almas");
            player.sendMessage(SmallCaps.convert("§e⚠ Solo se encontraron " + spawned + " almas. Busca en un radio de 50 bloques."));
        } else {
            player.sendMessage(SmallCaps.convert("§b§l✦ §7" + TOTAL_SOULS + " almas perdidas aparecieron en un radio de 50 bloques."));
            plugin.getLogger().info("[MemoryWalk] Todas las almas spawneadas exitosamente");
        }

        // Main task for phase B
        mainTask = new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!active || !player.isOnline()) {
                    cancel();
                    return;
                }

                tick++;

                // Particles around each soul
                if (tick % 10 == 0) {
                    for (Entity soul : lostSouls) {
                        if (soul == null || soul.isDead()) continue;
                        world.spawnParticle(Particle.SOUL, soul.getLocation().add(0, 0.5, 0),
                                2, 0.2, 0.3, 0.2, 0.01);
                        world.spawnParticle(Particle.DRIPPING_WATER, soul.getLocation().add(0, 0.8, 0),
                                1, 0.1, 0.1, 0.1, 0);
                    }
                }

                // Check if all souls comforted
                if (soulsComforted >= TOTAL_SOULS && !phaseBComplete) {
                    phaseBComplete = true;
                    cancel();

                    player.sendMessage("");
                    player.sendMessage(SmallCaps.convert("§a§l✦ §fHas consolado a todas las almas."));
                    player.sendMessage(SmallCaps.convert("§7§oSus lágrimas se convierten en luz..."));
                    player.sendMessage("");
                    player.sendTitle(SmallCaps.convert("§a§lAlmas Liberadas"), SmallCaps.convert("§7Todas las almas descansan en paz..."), 10, 60, 20);

                    world.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
                    world.spawnParticle(Particle.SOUL, player.getLocation().add(0, 2, 0), 60, 3, 2, 3, 0.1);
                    world.spawnParticle(Particle.END_ROD, player.getLocation().add(0, 3, 0), 40, 2, 2, 2, 0.15);

                    // Proceed to boss after delay
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (active) {
                            manager.onQuest6TasksCompleted(player);
                        }
                    }, 100L);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Called by the listener when player right-clicks near a lost soul.
     */
    public boolean tryComfortSoul(Player player) {
        if (!active || phaseAComplete == false || phaseBComplete) return false;

        Location pLoc = player.getLocation();
        for (Iterator<Entity> it = lostSouls.iterator(); it.hasNext(); ) {
            Entity soul = it.next();
            if (soul == null || soul.isDead()) {
                it.remove();
                continue;
            }

            // Rango de detección aumentado a 10 bloques para facilitar la interacción
            if (soul.getLocation().distanceSquared(pLoc) < 100) { // Within 10 blocks
                comfortSoul(soul, soulsComforted);
                it.remove();
                soulsComforted++;
                return true;
            }
        }
        return false;
    }

    private void comfortSoul(Entity soul, int index) {
        World world = soul.getWorld();
        Location loc = soul.getLocation();

        // Show message
        if (index < SOUL_MESSAGES.length) {
            player.sendMessage("");
            for (String line : SOUL_MESSAGES[index]) {
                player.sendMessage(SmallCaps.convert(line));
            }
            player.sendMessage(SmallCaps.convert("§8  [Alma " + (index + 1) + "/" + TOTAL_SOULS + "]"));
            player.sendMessage("");
        }

        // Soul ascension effect
        new BukkitRunnable() {
            int t = 0;
            final Location startLoc = loc.clone();
            @Override
            public void run() {
                if (t >= 40) {
                    cancel();
                    return;
                }
                double y = t * 0.1;
                world.spawnParticle(Particle.SOUL, startLoc.clone().add(0, y, 0), 3, 0.2, 0.1, 0.2, 0.02);
                world.spawnParticle(Particle.END_ROD, startLoc.clone().add(0, y + 0.5, 0), 2, 0.1, 0.1, 0.1, 0.05);
                world.spawnParticle(Particle.DUST, startLoc.clone().add(0, y, 0), 2, 0.3, 0.1, 0.3, 0,
                        new Particle.DustOptions(Color.fromRGB(200, 230, 255), 1.5f));

                if (t == 0) {
                    world.playSound(startLoc, Sound.ENTITY_ALLAY_DEATH, 0.8f, 1.0f + index * 0.1f);
                    world.playSound(startLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 0.8f + index * 0.15f);
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Remove the entity
        soul.remove();

        // Title
        player.sendTitle("", SmallCaps.convert("§b♡ Alma " + (index + 1) + " de " + TOTAL_SOULS + " consolada"), 5, 30, 10);
    }

    public boolean isInSoulPhase() {
        return active && phaseAComplete && !phaseBComplete;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isSpawnedEntity(UUID entityId) {
        for (ArmorStand as : memoryScenes) {
            if (as != null && as.getUniqueId().equals(entityId)) return true;
        }
        for (Entity soul : lostSouls) {
            if (soul != null && soul.getUniqueId().equals(entityId)) return true;
        }
        return false;
    }

    // ==================== CLEANUP ====================

    public void cleanup() {
        active = false;
        if (mainTask != null) mainTask.cancel();
        if (ambientTask != null) ambientTask.cancel();
        cleanMemoryScenes();
        cleanLostSouls();
    }

    private void cleanMemoryScenes() {
        for (ArmorStand as : memoryScenes) {
            if (as != null && !as.isDead()) as.remove();
        }
        memoryScenes.clear();
        spawnedMemories.clear(); // Clear spawn tracking

        // Sweep by tag
        World world = player.getWorld();
        for (Entity e : world.getEntities()) {
            if (e instanceof ArmorStand && e.getScoreboardTags().contains("lyra_memory")) {
                e.remove();
            }
        }
    }

    private void cleanLostSouls() {
        for (Entity soul : lostSouls) {
            if (soul != null && !soul.isDead()) soul.remove();
        }
        lostSouls.clear();

        // Sweep by tag
        World world = player.getWorld();
        for (Entity e : world.getEntities()) {
            if (e.getScoreboardTags().contains("lyra_soul")) {
                e.remove();
            }
        }
    }
}
