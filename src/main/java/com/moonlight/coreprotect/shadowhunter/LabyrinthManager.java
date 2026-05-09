package com.moonlight.coreprotect.shadowhunter;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Controla la experiencia de terror dentro del laberinto para la Misión 7.
 */
public class LabyrinthManager {

    private final CoreProtectPlugin plugin;
    private final ShadowHunterManager manager;
    private final Player player;
    private final ShadowHunterNPC npc;

    private LabyrinthWorld labyrinthWorld;
    private LabyrinthBoss boss;

    private boolean active = false;
    private boolean bossPhase = false;
    private int keysCollected = 0;
    private static final int KEYS_REQUIRED = 3;
    private final Set<Integer> collectedKeys = new HashSet<>();

    private BukkitTask ambientTask, horrorTask, chaseTask, progressTask, whisperTask, heartbeatTask, rushTask;
    private final List<Entity> shadowEntities = new ArrayList<>();
    private final Set<UUID> spawnedEntityIds = new HashSet<>();
    private static final int MAX_SHADOWS = 2;

    private long lastJumpscare = 0;
    private static final long JUMPSCARE_COOLDOWN = 6_000L; // 6s cooldown (was 15s)
    private int whisperIndex = 0;
    private Location originalLocation;

    private static final String[] WHISPERS = {
        "§4§o...puedes oír cómo respira...",
        "§8§o...algo te sigue... no mires atrás...",
        "§4§o...las paredes se mueven cuando no miras...",
        "§8§o...¿esos pasos son tuyos...?",
        "§4§o...ella cayó aquí... y nunca dejó de caer...",
        "§8§o...el vacío tiene hambre...",
        "§4§o...no estás solo... nunca lo estuviste...",
        "§8§o...¿recuerdas tu nombre? él ya no...",
        "§4§o...cada paso te aleja más de la salida...",
        "§8§o...los olvidados también fueron valientes...",
        "§4§o...la oscuridad te conoce... sabe tu nombre...",
        "§8§o...no te detengas... si te detienes te encuentra...",
        "§4§o...Lyra gritó durante días antes de callar...",
        "§8§o...el Errante huyó de aquí... tú no podrás...",
        "§4§o...las almas aquí no descansan... lloran...",
        "§8§o...¿sientes esa brisa? no hay viento aquí abajo...",
        "§4§o...algo se mueve entre las paredes...",
        "§8§o...el que te mira no tiene ojos...",
        "§4§o...cada alma que consume le hace más fuerte...",
        "§8§o...puedes escuchar el llanto de los niños...",
        "§4§o...la última persona que entró aquí sigue caminando...",
        "§8§o...no confíes en la luz... no es real...",
        "§4§o...el suelo bajo tus pies está hecho de huesos...",
        "§8§o...cuando parpadeas, algo se acerca...",
    };

    private static final String[] JUMPSCARE_TITLES = {
        "§4§l§kXX§r §4§lDETRÁS DE TI §4§l§kXX",
        "§0§l§kXXX§r §4§lNO MIRES §0§l§kXXX",
        "§4§l§kXX§r §c§lTE ENCONTRÉ §4§l§kXX",
        "§4§l☠ §cCORRE §4§l☠",
        "§0§l§kXXXXX§r §4§l¿POR QUÉ TE DETIENES? §0§l§kXXXXX",
        "§4§l§kXX§r §c§l...AQUÍ ESTOY... §4§l§kXX",
    };

    private static final String[] JUMPSCARE_SUBTITLES = {
        "§8§oNo estás solo...", "§8§oPuedo verte...",
        "§8§oSiempre estuve aquí...", "§8§oNunca saldrás...",
        "§8§oEres mío...", "§8§oNo hay salida...",
    };

    public LabyrinthManager(CoreProtectPlugin plugin, ShadowHunterManager manager,
                            Player player, ShadowHunterNPC npc) {
        this.plugin = plugin;
        this.manager = manager;
        this.player = player;
        this.npc = npc;
    }

    // ==================== START ====================

    public void start() {
        active = true;
        originalLocation = player.getLocation().clone();

        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§4§l☠ §c§lMISIÓN 7: EL VIENTRE DEL VACÍO §4§l☠"));
        player.sendMessage(SmallCaps.convert("§8§m                                              "));
        player.sendMessage(SmallCaps.convert("§4§oEl suelo se abre bajo tus pies..."));
        player.sendMessage(SmallCaps.convert("§4§oCaes... caes... caes..."));
        player.sendMessage("");

        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 200, 0, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 4, false, false, false));
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_ROAR, 1.0f, 0.3f);
        player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 1.0f, 0.5f);
        player.sendTitle("§4§l§kXXX", SmallCaps.convert("§8§oDescendiendo al vacío..."), 20, 60, 20);

        labyrinthWorld = new LabyrinthWorld(plugin, player.getUniqueId());
        labyrinthWorld.createWorld(() -> {
            if (!active || !player.isOnline()) {
                plugin.getLogger().warning("[Labyrinth] Cancelled start - active=" + active + ", online=" + player.isOnline());
                return;
            }

            org.bukkit.World labWorld = labyrinthWorld.getWorld();
            if (labWorld == null) {
                plugin.getLogger().severe("[Labyrinth] World is null after creation for " + player.getName());
                cleanup();
                return;
            }
            plugin.getLogger().info("[Labyrinth] World created: " + labWorld.getName() + " for " + player.getName());

            Location startLoc = labyrinthWorld.getStartLocation();
            if (startLoc == null) {
                plugin.getLogger().severe("[Labyrinth] startLocation is null for " + player.getName());
                cleanup();
                return;
            }
            plugin.getLogger().info("[Labyrinth] Start location: " + startLoc.getWorld().getName() + " at " + startLoc.getBlockX() + "," + startLoc.getBlockY() + "," + startLoc.getBlockZ());

            // Ensure chunk is loaded before teleporting
            labWorld.setSpawnLocation(startLoc.getBlockX(), startLoc.getBlockY(), startLoc.getBlockZ());
            org.bukkit.Chunk chunk = labWorld.getChunkAt(startLoc);
            chunk.setForceLoaded(true);
            chunk.load(true);

            plugin.getLogger().info("[Labyrinth] Chunk loaded at " + chunk.getX() + "," + chunk.getZ());

            // Teleport with a tick delay to ensure chunk is ready
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!active || !player.isOnline()) {
                    plugin.getLogger().warning("[Labyrinth] Teleport cancelled - active=" + active + ", online=" + player.isOnline());
                    return;
                }

                plugin.getLogger().info("[Labyrinth] Attempting teleport for " + player.getName() + " to " + startLoc.getWorld().getName());
                boolean success = player.teleport(startLoc);
                plugin.getLogger().info("[Labyrinth] Teleport result: " + success + ", player now in: " + player.getWorld().getName());

                if (!success) {
                    plugin.getLogger().warning("[Labyrinth] Teleport failed for " + player.getName() + ", retrying...");
                    // Retry once
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!active || !player.isOnline()) return;
                        boolean retrySuccess = player.teleport(startLoc);
                        plugin.getLogger().info("[Labyrinth] Retry teleport result: " + retrySuccess + ", player now in: " + player.getWorld().getName());
                    }, 10L);
                }

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!active || !player.isOnline()) return;

                    // Verify player is in the right world
                    if (!player.getWorld().getName().equals(labWorld.getName())) {
                        plugin.getLogger().severe("[Labyrinth] Player " + player.getName() + " is still in wrong world: " + player.getWorld().getName() + " instead of " + labWorld.getName());
                        // Force teleport again
                        player.teleport(startLoc);
                        plugin.getLogger().info("[Labyrinth] Force teleport executed, player now in: " + player.getWorld().getName());
                    }

                    player.removePotionEffect(PotionEffectType.BLINDNESS);
                    player.removePotionEffect(PotionEffectType.SLOWNESS);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 999999, 0, false, false, false));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 999999, 0, false, false, false));

                    sendEntranceLore();
                    startAmbientSounds();
                    startHorrorSystem();
                    startChaseSystem();
                    startProgressTracker();
                    startWhispers();
                    startHeartbeat();

                    player.sendMessage(SmallCaps.convert("§7§oObjetivo: Encuentra §c3 fragmentos de alma §7§opara abrir el camino al centro."));
                    player.sendMessage("");
                }, 40L);
            }, 5L); // Increased from 2L to 5L for more time
        });
    }

    // ==================== ENTRANCE LORE ====================

    private void sendEntranceLore() {
        String[] lore = {
            "§4§l☠ §8━━━━━━━━━━━━━━━━━━━━━━━━━━━━ §4§l☠",
            "", "§4§oHas descendido al Vientre del Vacío.",
            "§8§oEste lugar existía antes que el mundo.",
            "§8§oAntes que la luz. Antes que el tiempo.",
            "", "§4§oAquí cayó Lyra cuando se dejó caer.",
            "§4§oAquí nació la oscuridad que corrompió al Errante.",
            "§4§oAquí habita §c§l§oEl Olvido§4§o.",
            "", "§8§oLas almas que entraron antes que tú",
            "§8§ovagan como sombras. §4Los Olvidados§8§o.",
            "§c§oEncuentra los §4§l3 fragmentos de alma§c§o.",
            "§c§oRecógelos y enfréntalo... o quédate aquí para siempre.",
            "", "§4§l☠ §8━━━━━━━━━━━━━━━━━━━━━━━━━━━━ §4§l☠",
        };
        new BukkitRunnable() {
            int i = 0;
            @Override public void run() {
                if (!active || !player.isOnline() || i >= lore.length) { cancel(); return; }
                player.sendMessage(SmallCaps.convert(lore[i]));
                if (lore[i].contains("Olvido") || lore[i].contains("Lyra"))
                    player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.6f, 0.4f);
                else if (!lore[i].isEmpty())
                    player.playSound(player.getLocation(), Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.2f, 0.6f);
                i++;
            }
        }.runTaskTimer(plugin, 40L, 30L);
    }

    // ==================== AMBIENT SOUNDS ====================

    private void startAmbientSounds() {
        final Random rng = new Random();
        ambientTask = new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (!active || !player.isOnline()) { cancel(); return; }
                Location loc = player.getLocation();
                tick++;

                if (tick % (60 + rng.nextInt(60)) == 0) {
                    Sound[] sounds = { Sound.AMBIENT_CAVE, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD,
                        Sound.AMBIENT_SOUL_SAND_VALLEY_ADDITIONS, Sound.AMBIENT_BASALT_DELTAS_MOOD,
                        Sound.ENTITY_WARDEN_NEARBY_CLOSER, Sound.ENTITY_WARDEN_NEARBY_CLOSEST };
                    double angle = rng.nextDouble() * Math.PI * 2;
                    Location soundLoc = loc.clone().add(Math.cos(angle) * 6, 0, Math.sin(angle) * 6);
                    player.playSound(soundLoc, sounds[rng.nextInt(sounds.length)], 0.6f, 0.3f + rng.nextFloat() * 0.7f);
                }

                if (tick % (80 + rng.nextInt(80)) == 0) {
                    Vector behind = loc.getDirection().multiply(-1).normalize().multiply(4);
                    player.playSound(loc.clone().add(behind), Sound.ENTITY_WITHER_SKELETON_STEP, 0.4f, 0.5f);
                }

                if (tick % (300 + rng.nextInt(300)) == 0) {
                    Sound[] screams = { Sound.ENTITY_GHAST_SCREAM, Sound.ENTITY_VEX_DEATH, Sound.ENTITY_WARDEN_DEATH };
                    player.playSound(loc.clone().add(rng.nextGaussian() * 15, 0, rng.nextGaussian() * 15),
                            screams[rng.nextInt(screams.length)], 0.3f, 0.3f + rng.nextFloat() * 0.4f);
                }

                if (tick % 5 == 0) {
                    for (int i = 0; i < 3; i++) {
                        Location pLoc = loc.clone().add(rng.nextGaussian() * 4, rng.nextDouble() * 3, rng.nextGaussian() * 4);
                        loc.getWorld().spawnParticle(Particle.DUST, pLoc, 1, 0, 0, 0, 0,
                                new Particle.DustOptions(Color.fromRGB(15, 0, 25), 1.5f));
                    }
                    if (rng.nextInt(4) == 0)
                        loc.getWorld().spawnParticle(Particle.SCULK_SOUL,
                                loc.clone().add(rng.nextGaussian() * 3, 0.1, rng.nextGaussian() * 3), 1, 0, 0.1, 0, 0.02);
                }
            }
        }.runTaskTimer(plugin, 20L, 1L);
    }

    // ==================== HORROR / JUMPSCARE SYSTEM ====================
    // Sistema de terror INTENSO: entidades que corren hacia ti, sustos visuales,
    // apariciones cuando se quita la oscuridad, phantoms, rush masivos.

    private void startHorrorSystem() {
        final Random rng = new Random();
        horrorTask = new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (!active || !player.isOnline() || bossPhase) { cancel(); return; }
                tick++;
                Location pLoc = player.getLocation();

                // Horror rooms trigger instantly when nearby
                if (tick % 15 == 0 && labyrinthWorld != null) {
                    for (Location horror : labyrinthWorld.getHorrorLocations()) {
                        if (horror.getWorld() != null && pLoc.distanceSquared(horror) < 49) {
                            triggerHorrorEvent(pLoc, rng);
                            break;
                        }
                    }
                }

                // Random scare every 15-25 seconds (300-500 ticks) - reduced frequency
                int scareInterval = 300 + rng.nextInt(200);
                if (tick % scareInterval == 0) {
                    double chance = 0.20 + (keysCollected * 0.10); // 20%-50% chance
                    if (rng.nextDouble() < chance) {
                        int type = rng.nextInt(8);
                        switch (type) {
                            case 0: case 1: spawnRushingEntity(pLoc, rng); break;
                            case 2: triggerDarknessLiftScare(pLoc, rng); break;
                            case 3: spawnFaceJumpscare(pLoc, rng); break;
                            case 4: spawnPhantomSwoop(pLoc, rng); break;
                            case 5: triggerJumpscare(pLoc, rng); break;
                            case 6: spawnMassRush(pLoc, rng); break;
                            case 7: spawnCeilingScare(pLoc, rng); break;
                        }
                    }
                }

                // Brief shadow figures appearing in the distance every 12-18 seconds
                if (tick % (240 + rng.nextInt(120)) == 0) {
                    spawnBriefShadowFigure(pLoc, rng);
                }

                // Random footsteps behind you every 8 seconds
                if (tick % 160 == 0) {
                    Vector behind = pLoc.getDirection().multiply(-1).normalize().multiply(3 + rng.nextInt(4));
                    Location stepLoc = pLoc.clone().add(behind);
                    Sound[] steps = { Sound.ENTITY_WITHER_SKELETON_STEP, Sound.ENTITY_ZOMBIE_STEP,
                            Sound.ENTITY_IRON_GOLEM_STEP, Sound.ENTITY_ENDERMAN_TELEPORT };
                    player.playSound(stepLoc, steps[rng.nextInt(steps.length)], 0.5f, 0.3f + rng.nextFloat() * 0.4f);
                }
            }
        }.runTaskTimer(plugin, 80L, 1L);

        // Separate task for RUSH scares — runs independently every 12-18 seconds
        startRushScareLoop();
    }

    // === RUSH SCARE LOOP: periodic rushing entity from the dark ===
    private void startRushScareLoop() {
        final Random rng = new Random();
        rushTask = new BukkitRunnable() {
            @Override public void run() {
                if (!active || !player.isOnline() || bossPhase) { cancel(); return; }
                if (rng.nextInt(3) < 2) { // 66% chance each cycle
                    spawnRushingEntity(player.getLocation(), rng);
                }
            }
        }.runTaskTimer(plugin, 600L, 600L + new Random().nextInt(400)); // every 30-50 seconds
    }

    // === RUSHING ENTITY: spawns far away, charges at MAX SPEED, vanishes on arrival ===
    private void spawnRushingEntity(Location pLoc, Random rng) {
        long now = System.currentTimeMillis();
        if (now - lastJumpscare < 8000) return; // min 8s between scares
        lastJumpscare = now;
        World w = pLoc.getWorld();
        if (w == null) return;

        // Spawn 14-20 blocks away in the direction the player ISN'T looking
        double angle;
        if (rng.nextBoolean()) {
            // Behind
            Vector behind = pLoc.getDirection().multiply(-1).normalize();
            angle = Math.atan2(behind.getZ(), behind.getX()) + (rng.nextGaussian() * 0.5);
        } else {
            // Random side
            angle = rng.nextDouble() * Math.PI * 2;
        }
        double dist = 14 + rng.nextInt(7);
        Location spawnLoc = pLoc.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
        spawnLoc.setY(LabyrinthWorld.WALL_MIN_Y);

        try {
            // Spawn the rusher
            Zombie rusher = (Zombie) w.spawnEntity(spawnLoc, EntityType.ZOMBIE);
            rusher.setSilent(false); // Let player hear footsteps
            rusher.setCustomName("§4§l§k||||||||");
            rusher.setCustomNameVisible(true);
            rusher.setBaby(false);
            rusher.getEquipment().clear();
            rusher.setCanPickupItems(false);
            rusher.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 8, false, false, false)); // Speed IX = INSANE
            rusher.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 200, 0, false, false, false));
            rusher.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(1);
            rusher.setHealth(1);
            rusher.setInvulnerable(true);
            rusher.setTarget(player);
            rusher.addScoreboardTag("labyrinth_rusher");
            spawnedEntityIds.add(rusher.getUniqueId());

            // Trail particles behind it
            w.spawnParticle(Particle.DUST, spawnLoc.clone().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0,
                    new Particle.DustOptions(Color.fromRGB(40, 0, 0), 3.0f));
            player.playSound(spawnLoc, Sound.ENTITY_WARDEN_EMERGE, 0.6f, 0.2f);

            // Track it — if it gets close, trigger full jumpscare + vanish
            new BukkitRunnable() {
                int ticks = 0;
                @Override public void run() {
                    if (!active || rusher.isDead() || !rusher.isValid() || ticks > 80) {
                        if (!rusher.isDead() && rusher.isValid()) {
                            w.spawnParticle(Particle.SMOKE, rusher.getLocation().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0.1);
                            spawnedEntityIds.remove(rusher.getUniqueId());
                            rusher.remove();
                        }
                        cancel(); return;
                    }
                    ticks++;

                    // Smoke trail
                    w.spawnParticle(Particle.DUST, rusher.getLocation().add(0, 1.2, 0), 4, 0.15, 0.4, 0.15, 0,
                            new Particle.DustOptions(Color.fromRGB(20, 0, 20), 2.0f));
                    w.spawnParticle(Particle.SCULK_SOUL, rusher.getLocation().add(0, 0.5, 0), 1, 0.1, 0.1, 0.1, 0.01);

                    double distSq = rusher.getLocation().distanceSquared(player.getLocation());
                    if (distSq < 6) {
                        // ARRIVED — full jumpscare
                        int idx = rng.nextInt(JUMPSCARE_TITLES.length);
                        player.sendTitle(JUMPSCARE_TITLES[idx], JUMPSCARE_SUBTITLES[idx], 0, 20, 5);
                        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_ROAR, 1.5f, 0.15f);
                        player.playSound(player.getLocation(), Sound.ENTITY_GHAST_SCREAM, 1.2f, 0.4f);
                        player.playSound(player.getLocation(), Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 1.0f, 0.5f);
                        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20, 0, false, false, false));
                        player.damage(3.0);

                        w.spawnParticle(Particle.DUST, rusher.getLocation().add(0, 1, 0), 50, 1, 1.5, 1, 0,
                                new Particle.DustOptions(Color.fromRGB(50, 0, 0), 3.5f));
                        w.spawnParticle(Particle.SMOKE, rusher.getLocation().add(0, 1, 0), 40, 0.5, 1, 0.5, 0.2);

                        spawnedEntityIds.remove(rusher.getUniqueId());
                        rusher.remove();
                        cancel();
                    }
                }
            }.runTaskTimer(plugin, 1L, 1L);
        } catch (Exception ignored) {}
    }

    // === DARKNESS LIFT SCARE: remove darkness briefly, entity RIGHT in your face ===
    private void triggerDarknessLiftScare(Location pLoc, Random rng) {
        long now = System.currentTimeMillis();
        if (now - lastJumpscare < JUMPSCARE_COOLDOWN) return;
        lastJumpscare = now;
        World w = pLoc.getWorld();
        if (w == null) return;

        // Remove darkness briefly
        player.removePotionEffect(PotionEffectType.DARKNESS);

        // Spawn an armor stand RIGHT in the player's face
        Vector front = pLoc.getDirection().normalize().multiply(1.5);
        Location faceLoc = pLoc.clone().add(front);
        faceLoc.setY(pLoc.getY());
        // Make it look at the player
        Vector toPlayer = pLoc.toVector().subtract(faceLoc.toVector()).normalize();
        faceLoc.setDirection(toPlayer);

        try {
            ArmorStand face = (ArmorStand) w.spawnEntity(faceLoc, EntityType.ARMOR_STAND);
            face.setVisible(false);
            face.setGravity(false);
            face.setInvulnerable(true);
            face.setCustomName("§4§l§k||§r §c§lTE VEO §4§l§k||");
            face.setCustomNameVisible(true);
            face.setHelmet(new ItemStack(Material.DRAGON_HEAD));
            face.addScoreboardTag("labyrinth_scare");
            spawnedEntityIds.add(face.getUniqueId());

            // Sound
            player.playSound(pLoc, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.5f, 0.3f);
            player.playSound(pLoc, Sound.ENTITY_ENDERMAN_STARE, 1.0f, 0.2f);

            w.spawnParticle(Particle.DUST, faceLoc.clone().add(0, 1.5, 0), 30, 0.3, 0.3, 0.3, 0,
                    new Particle.DustOptions(Color.fromRGB(80, 0, 0), 3.0f));

            // After 15 ticks: restore darkness, remove face, flash blindness
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && active) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 999999, 0, false, false, false));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 10, 0, false, false, false));
                    player.sendTitle("§4§l§kXXXXX", "§c§o...siempre estuve aquí...", 0, 25, 5);
                    player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_ROAR, 1.0f, 0.2f);
                }
                if (!face.isDead()) {
                    w.spawnParticle(Particle.SMOKE, face.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.1);
                    spawnedEntityIds.remove(face.getUniqueId());
                    face.remove();
                }
            }, 15L);
        } catch (Exception ignored) {
            // Restore darkness if spawn failed
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 999999, 0, false, false, false));
        }
    }

    // === FACE JUMPSCARE: entity spawns DIRECTLY in front, stares at you ===
    private void spawnFaceJumpscare(Location pLoc, Random rng) {
        long now = System.currentTimeMillis();
        if (now - lastJumpscare < JUMPSCARE_COOLDOWN) return;
        lastJumpscare = now;
        World w = pLoc.getWorld();
        if (w == null) return;

        Vector front = pLoc.getDirection().normalize().multiply(2);
        Location spawnLoc = pLoc.clone().add(front);
        spawnLoc.setY(pLoc.getY());

        try {
            Zombie face = (Zombie) w.spawnEntity(spawnLoc, EntityType.ZOMBIE);
            face.setSilent(true);
            face.setBaby(false);
            face.setAI(false);
            face.setInvulnerable(true);
            face.getEquipment().clear();
            face.getEquipment().setHelmet(new ItemStack(Material.WITHER_SKELETON_SKULL));
            face.setCustomName("§4§l☠");
            face.setCustomNameVisible(true);
            face.addScoreboardTag("labyrinth_scare");
            spawnedEntityIds.add(face.getUniqueId());

            // Look at player
            Vector look = pLoc.toVector().subtract(spawnLoc.toVector()).normalize();
            Location faceLook = face.getLocation();
            faceLook.setDirection(look);
            face.teleport(faceLook);

            // INTENSE sound burst
            player.playSound(pLoc, Sound.ENTITY_WARDEN_ROAR, 2.0f, 0.1f);
            player.playSound(pLoc, Sound.ENTITY_GHAST_SCREAM, 1.5f, 0.3f);
            player.playSound(pLoc, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 1.5f, 0.4f);

            int idx = rng.nextInt(JUMPSCARE_TITLES.length);
            player.sendTitle(JUMPSCARE_TITLES[idx], JUMPSCARE_SUBTITLES[idx], 0, 20, 3);
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 15, 0, false, false, false));

            w.spawnParticle(Particle.DUST, spawnLoc.clone().add(0, 1, 0), 60, 0.5, 1, 0.5, 0,
                    new Particle.DustOptions(Color.fromRGB(60, 0, 0), 3.5f));
            w.spawnParticle(Particle.SMOKE, spawnLoc.clone().add(0, 1, 0), 40, 0.3, 0.8, 0.3, 0.15);

            // Remove after 1 second with smoke
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!face.isDead()) {
                    w.spawnParticle(Particle.SMOKE, face.getLocation().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.1);
                    spawnedEntityIds.remove(face.getUniqueId());
                    face.remove();
                }
            }, 20L);
        } catch (Exception ignored) {}
    }

    // === PHANTOM SWOOP: phantom dives silently past the player ===
    private void spawnPhantomSwoop(Location pLoc, Random rng) {
        World w = pLoc.getWorld();
        if (w == null) return;

        Location spawnLoc = pLoc.clone().add(rng.nextGaussian() * 3, 3, rng.nextGaussian() * 3);
        try {
            Phantom phantom = (Phantom) w.spawnEntity(spawnLoc, EntityType.PHANTOM);
            phantom.setSilent(true);
            phantom.setSize(3 + rng.nextInt(3));
            phantom.setInvulnerable(true);
            phantom.setTarget(player);
            phantom.addScoreboardTag("labyrinth_scare");
            spawnedEntityIds.add(phantom.getUniqueId());

            player.playSound(pLoc.clone().add(0, 3, 0), Sound.ENTITY_PHANTOM_FLAP, 0.8f, 0.3f);

            // Remove after 3 seconds
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!phantom.isDead()) {
                    w.spawnParticle(Particle.SMOKE, phantom.getLocation(), 15, 0.5, 0.5, 0.5, 0.1);
                    spawnedEntityIds.remove(phantom.getUniqueId());
                    phantom.remove();
                }
            }, 60L);
        } catch (Exception ignored) {}
    }

    // === MASS RUSH: 4-6 entities spawn in a circle and ALL rush toward you ===
    private void spawnMassRush(Location pLoc, Random rng) {
        long now = System.currentTimeMillis();
        if (now - lastJumpscare < JUMPSCARE_COOLDOWN) return;
        lastJumpscare = now;
        World w = pLoc.getWorld();
        if (w == null) return;

        int count = 4 + rng.nextInt(3);
        player.playSound(pLoc, Sound.ENTITY_WARDEN_NEARBY_CLOSEST, 1.5f, 0.2f);
        player.sendMessage(SmallCaps.convert("§4§l§kXXX §r§4§oNO NO NO NO NO §4§l§kXXX"));

        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2 / count) * i;
            double dist = 10 + rng.nextInt(5);
            Location sLoc = pLoc.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
            sLoc.setY(LabyrinthWorld.WALL_MIN_Y);

            final int idx = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!active || !player.isOnline()) return;
                try {
                    Zombie rusher = (Zombie) w.spawnEntity(sLoc, EntityType.ZOMBIE);
                    rusher.setSilent(true);
                    rusher.setBaby(rng.nextBoolean());
                    rusher.setCustomName("§4§l§k|||||");
                    rusher.setCustomNameVisible(true);
                    rusher.getEquipment().clear();
                    rusher.setCanPickupItems(false);
                    rusher.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 10, false, false, false));
                    rusher.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(1);
                    rusher.setHealth(1);
                    rusher.setInvulnerable(true);
                    rusher.setTarget(player);
                    rusher.addScoreboardTag("labyrinth_rusher");
                    spawnedEntityIds.add(rusher.getUniqueId());

                    // Auto-remove after 4 seconds
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!rusher.isDead() && rusher.isValid()) {
                            w.spawnParticle(Particle.SMOKE, rusher.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.1);
                            spawnedEntityIds.remove(rusher.getUniqueId());
                            rusher.remove();
                        }
                    }, 80L);
                } catch (Exception ignored) {}
            }, idx * 3L); // Stagger spawns slightly
        }
    }

    // === CEILING SCARE: spider drops from ceiling right above you ===
    private void spawnCeilingScare(Location pLoc, Random rng) {
        World w = pLoc.getWorld();
        if (w == null) return;

        Location above = pLoc.clone().add(rng.nextGaussian() * 0.5, 3, rng.nextGaussian() * 0.5);
        try {
            Spider spider = (Spider) w.spawnEntity(above, EntityType.SPIDER);
            spider.setSilent(false);
            spider.setInvulnerable(true);
            spider.setTarget(player);
            spider.addScoreboardTag("labyrinth_scare");
            spawnedEntityIds.add(spider.getUniqueId());

            player.playSound(pLoc.clone().add(0, 2, 0), Sound.ENTITY_SPIDER_AMBIENT, 1.0f, 0.3f);
            player.sendMessage(SmallCaps.convert("§4§o...algo cae del techo..."));

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!spider.isDead()) {
                    w.spawnParticle(Particle.SMOKE, spider.getLocation(), 15, 0.3, 0.3, 0.3, 0.1);
                    spawnedEntityIds.remove(spider.getUniqueId());
                    spider.remove();
                }
            }, 60L);
        } catch (Exception ignored) {}
    }

    private void triggerHorrorEvent(Location pLoc, Random rng) {
        long now = System.currentTimeMillis();
        if (now - lastJumpscare < JUMPSCARE_COOLDOWN) return;
        lastJumpscare = now;
        World w = pLoc.getWorld();

        switch (rng.nextInt(7)) {
            case 0: // Sudden darkness + scream + rushing entity
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0, false, false, false));
                player.playSound(pLoc, Sound.ENTITY_GHAST_SCREAM, 1.0f, 0.3f);
                w.spawnParticle(Particle.DUST, pLoc.clone().add(0, 1, 0), 50, 2, 2, 2, 0,
                        new Particle.DustOptions(Color.fromRGB(50, 0, 0), 3.0f));
                player.sendMessage(SmallCaps.convert("§4§l§kXXX §r§4§oALGO TE HA TOCADO §4§l§kXXX"));
                spawnRushingEntity(pLoc, rng);
                break;
            case 1: // Face jumpscare
                spawnFaceJumpscare(pLoc, rng);
                break;
            case 2: // Darkness lift scare
                triggerDarknessLiftScare(pLoc, rng);
                break;
            case 3: // Screen shake + mass rush
                player.playSound(pLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.7f, 0.3f);
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 0, false, false, false));
                player.sendMessage(SmallCaps.convert("§4§o...las paredes tiemblan... VIENEN..."));
                spawnMassRush(pLoc, rng);
                break;
            case 4: // Damage + phantom swoop
                player.damage(4.0);
                player.playSound(pLoc, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 0.5f);
                player.sendTitle("", SmallCaps.convert("§4§o...no debiste venir aquí..."), 0, 40, 10);
                spawnPhantomSwoop(pLoc, rng);
                break;
            case 5: // Rushing entity from behind
                spawnRushingEntity(pLoc, rng);
                player.playSound(pLoc, Sound.ENTITY_WARDEN_EMERGE, 0.8f, 0.5f);
                break;
            case 6: // Ceiling spider + face scare combo
                spawnCeilingScare(pLoc, rng);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (active && player.isOnline()) spawnFaceJumpscare(player.getLocation(), rng);
                }, 30L);
                break;
        }
    }

    private void triggerJumpscare(Location pLoc, Random rng) {
        long now = System.currentTimeMillis();
        if (now - lastJumpscare < JUMPSCARE_COOLDOWN / 2) return;
        lastJumpscare = now;

        int idx = rng.nextInt(JUMPSCARE_TITLES.length);
        player.sendTitle(JUMPSCARE_TITLES[idx], JUMPSCARE_SUBTITLES[idx], 0, 25, 5);
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 15, 0, false, false, false));
        player.playSound(pLoc, Sound.ENTITY_WARDEN_ROAR, 1.5f, 0.2f);
        player.playSound(pLoc, Sound.ENTITY_GHAST_SCREAM, 1.0f, 0.5f);
        player.playSound(pLoc, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 1.0f, 0.5f);

        // ALSO spawn a rushing entity for visual impact
        spawnRushingEntity(pLoc, rng);
    }

    private void spawnBriefShadowFigure(Location pLoc, Random rng) {
        World w = pLoc.getWorld();
        if (w == null) return;

        // Spawn in random direction 5-8 blocks away
        double angle = rng.nextDouble() * Math.PI * 2;
        double dist = 5 + rng.nextInt(4);
        Location spawnLoc = pLoc.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
        spawnLoc.setY(pLoc.getY());

        try {
            Zombie shadow = (Zombie) w.spawnEntity(spawnLoc, EntityType.ZOMBIE);
            shadow.setSilent(true);
            shadow.setCustomName("§4§l§k||§r §4Los Olvidados §4§l§k||");
            shadow.setCustomNameVisible(false);
            shadow.setBaby(false);
            shadow.getEquipment().clear();
            shadow.setAI(false);
            shadow.setInvulnerable(true);
            shadow.addScoreboardTag("labyrinth_scare");
            spawnedEntityIds.add(shadow.getUniqueId());

            w.spawnParticle(Particle.DUST, spawnLoc.clone().add(0, 1, 0), 20, 0.3, 0.8, 0.3, 0,
                    new Particle.DustOptions(Color.fromRGB(0, 0, 0), 2.5f));
            player.playSound(spawnLoc, Sound.ENTITY_ENDERMAN_STARE, 0.4f, 0.3f);

            // Remove after 1.5 seconds with smoke
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!shadow.isDead()) {
                    w.spawnParticle(Particle.SMOKE, shadow.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.05);
                    spawnedEntityIds.remove(shadow.getUniqueId());
                    shadow.remove();
                }
            }, 30L);
        } catch (Exception ignored) {}
    }

    // ==================== CHASE SYSTEM (Los Olvidados) ====================
    // Persistent chasers that hunt the player through the labyrinth

    private void startChaseSystem() {
        final Random rng = new Random();
        chaseTask = new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (!active || !player.isOnline() || bossPhase) { cancel(); return; }
                tick++;

                shadowEntities.removeIf(e -> e == null || e.isDead() || !e.isValid());
                Location pLoc = player.getLocation();

                // Chase rooms trigger spawn
                if (tick % 200 == 0 && labyrinthWorld != null && shadowEntities.size() < MAX_SHADOWS) {
                    for (Location chase : labyrinthWorld.getChaseLocations()) {
                        if (chase.getWorld() != null && pLoc.distanceSquared(chase) < 81) {
                            spawnChaseShadow(chase, rng);
                            break;
                        }
                    }
                }

                // Periodic shadow spawning — much less frequent
                int spawnInterval = 600 - (keysCollected * 50); // 600 -> 350 ticks (10-6 seconds)
                if (tick % Math.max(spawnInterval, 300) == 0 && shadowEntities.size() < MAX_SHADOWS) {
                    double a = rng.nextDouble() * Math.PI * 2;
                    Location sLoc = pLoc.clone().add(Math.cos(a) * 15, 0, Math.sin(a) * 15);
                    sLoc.setY(LabyrinthWorld.WALL_MIN_Y);
                    if (labyrinthWorld != null && labyrinthWorld.getWorld() != null) {
                        spawnChaseShadow(sLoc, rng);
                    }
                }

                // Update shadow AI: chase the player aggressively
                if (tick % 3 == 0) {
                    for (Entity shadow : new ArrayList<>(shadowEntities)) {
                        if (shadow instanceof Mob) {
                            Mob mob = (Mob) shadow;
                            mob.setTarget(player);
                            double dist = mob.getLocation().distanceSquared(pLoc);

                            // Particles
                            if (tick % 6 == 0) {
                                mob.getWorld().spawnParticle(Particle.DUST,
                                    mob.getLocation().add(0, 1, 0), 3, 0.2, 0.5, 0.2, 0,
                                    new Particle.DustOptions(Color.fromRGB(10, 0, 15), 1.8f));
                                mob.getWorld().spawnParticle(Particle.SCULK_SOUL,
                                    mob.getLocation().add(0, 0.5, 0), 1, 0.2, 0.2, 0.2, 0.02);
                            }

                            // If close: damage, jumpscare, despawn
                            if (dist < 5) {
                                player.damage(6.0);
                                int idx = rng.nextInt(JUMPSCARE_TITLES.length);
                                player.sendTitle(JUMPSCARE_TITLES[idx], JUMPSCARE_SUBTITLES[idx], 0, 15, 3);
                                player.playSound(pLoc, Sound.ENTITY_WARDEN_ROAR, 1.0f, 0.3f);
                                mob.getWorld().spawnParticle(Particle.SMOKE,
                                    mob.getLocation().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.1);
                                spawnedEntityIds.remove(mob.getUniqueId());
                                shadowEntities.remove(shadow);
                                mob.remove();
                            }

                            // Despawn if too far
                            if (dist > 1200) {
                                spawnedEntityIds.remove(mob.getUniqueId());
                                shadowEntities.remove(shadow);
                                mob.remove();
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 40L, 1L);
    }

    private void spawnChaseShadow(Location loc, Random rng) {
        World w = loc.getWorld();
        if (w == null) return;

        try {
            WitherSkeleton shadow = (WitherSkeleton) w.spawnEntity(loc, EntityType.WITHER_SKELETON);
            shadow.setSilent(true);
            shadow.setCustomName("§4§l§k|§r §8Olvidado §4§l§k|");
            shadow.setCustomNameVisible(false);
            shadow.getEquipment().clear();
            shadow.getEquipment().setHelmet(new ItemStack(Material.CARVED_PUMPKIN));
            shadow.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999, 2, false, false, false)); // Speed III
            shadow.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 999999, 0, false, false, false));
            shadow.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(40);
            shadow.setHealth(40);
            shadow.setTarget(player);
            shadow.setRemoveWhenFarAway(false);
            shadow.addScoreboardTag("labyrinth_chaser");

            spawnedEntityIds.add(shadow.getUniqueId());
            shadowEntities.add(shadow);

            w.spawnParticle(Particle.DUST, loc.clone().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0,
                    new Particle.DustOptions(Color.fromRGB(0, 0, 0), 2.0f));
            player.playSound(loc, Sound.ENTITY_WARDEN_EMERGE, 0.5f, 0.3f);
        } catch (Exception ignored) {}
    }

    // ==================== PROGRESS TRACKER (Keys) ====================

    private void startProgressTracker() {
        progressTask = new BukkitRunnable() {
            @Override public void run() {
                if (!active || !player.isOnline() || bossPhase) { cancel(); return; }
                if (labyrinthWorld == null) return;

                Location pLoc = player.getLocation();
                List<Location> keys = labyrinthWorld.getKeyLocations();

                for (int i = 0; i < keys.size(); i++) {
                    if (collectedKeys.contains(i)) continue;
                    Location keyLoc = keys.get(i);
                    if (keyLoc.getWorld() == null) continue;

                    double distSq = pLoc.distanceSquared(keyLoc);

                    // Bright particles visible from far away (30 blocks)
                    if (distSq < 900) {
                        keyLoc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,
                            keyLoc.clone().add(0, 2, 0), 8, 0.3, 0.8, 0.3, 0.03);
                        keyLoc.getWorld().spawnParticle(Particle.DUST,
                            keyLoc.clone().add(0, 1.5, 0), 5, 0.4, 0.6, 0.4, 0,
                            new Particle.DustOptions(Color.fromRGB(255, 50, 50), 2.5f));
                        keyLoc.getWorld().spawnParticle(Particle.DUST,
                            keyLoc.clone().add(0, 2.5, 0), 3, 0.2, 0.3, 0.2, 0,
                            new Particle.DustOptions(Color.fromRGB(0, 200, 255), 2.0f));
                    }

                    // Hint when getting close (10 blocks)
                    if (distSq < 100 && distSq > 12) {
                        player.playSound(keyLoc, Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.3f, 1.2f);
                    }

                    // Collect key — 3.5 block radius (distSq < 12)
                    if (distSq < 12) {
                        collectKey(i, keyLoc);
                    }
                }

                // Check if near boss room with all keys
                Location bossCenter = labyrinthWorld.getBossRoomCenter();
                if (bossCenter != null && bossCenter.getWorld() != null && keysCollected >= KEYS_REQUIRED) {
                    if (pLoc.distanceSquared(bossCenter) < 100 && !bossPhase) {
                        startBossPhase();
                    }
                }

                // Action bar: progress
                String bar = "§4☠ §7Fragmentos: ";
                for (int i = 0; i < KEYS_REQUIRED; i++) {
                    bar += (i < keysCollected) ? "§a⬟ " : "§8⬡ ";
                }
                if (keysCollected >= KEYS_REQUIRED) bar += "§c| §4Ve al centro del laberinto";
                com.moonlight.coreprotect.util.ActionBarUtil.send(player, bar);
            }
        }.runTaskTimer(plugin, 40L, 10L);
    }

    private void collectKey(int index, Location keyLoc) {
        if (collectedKeys.contains(index)) return;
        collectedKeys.add(index);
        keysCollected++;

        World w = keyLoc.getWorld();
        w.spawnParticle(Particle.SOUL_FIRE_FLAME, keyLoc.clone().add(0, 1.5, 0), 60, 1, 1.5, 1, 0.1);
        w.spawnParticle(Particle.DUST, keyLoc.clone().add(0, 1.5, 0), 40, 1, 1, 1, 0,
                new Particle.DustOptions(Color.fromRGB(0, 200, 255), 2.5f));
        w.playSound(keyLoc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
        w.playSound(keyLoc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);

        // Remove key room marker blocks: soul_fire (Y=60), shroomlight (Y=61, Y=62)
        int bx = keyLoc.getBlockX();
        int bz = keyLoc.getBlockZ();
        for (int y = LabyrinthWorld.FLOOR_Y + 1; y <= LabyrinthWorld.FLOOR_Y + 3; y++) {
            w.getBlockAt(bx, y, bz).setType(Material.AIR, false);
        }

        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§b§l✦ §fFragmento de Alma recogido §7(" + keysCollected + "/" + KEYS_REQUIRED + ")"));

        String[] keyLore = {
            "§4§o\"Este fragmento grita. Puedes sentir el dolor de quien fue antes de ser consumido.\"",
            "§4§o\"El fragmento late como un corazón. Cada latido es un nombre que ya nadie recuerda.\"",
            "§4§o\"El último fragmento. Puedes ver una cara en su interior. Te está mirando.\"",
        };
        if (keysCollected <= keyLore.length)
            player.sendMessage(SmallCaps.convert(keyLore[keysCollected - 1]));

        if (keysCollected >= KEYS_REQUIRED) {
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("§c§l⚠ §4§lTodos los fragmentos reunidos."));
            player.sendMessage(SmallCaps.convert("§4§oEl laberinto tiembla. §c§oEl Olvido te espera..."));
            player.sendMessage("");
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_ROAR, 1.5f, 0.2f);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f);
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 0, false, false, false));

            // Teleport to boss arena after 3 seconds
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                teleportToBossArena();
            }, 60L);
        } else {
            player.sendMessage("");
        }
    }

    private void teleportToBossArena() {
        if (!player.isOnline()) return;
        
        World world = player.getWorld();
        
        // Create boss arena location (far away from labyrinth)
        Location arenaCenter = new Location(world, 10000, 65, 10000); // Far away coordinates
        
        // Create a 50x50 black cube arena
        int arenaSize = 25; // 25 blocks radius = 50x50 total
        int floorY = 64;
        int ceilingY = 80;
        
        // Build black cube arena
        for (int x = -arenaSize; x <= arenaSize; x++) {
            for (int z = -arenaSize; z <= arenaSize; z++) {
                // Floor
                Location floorLoc = arenaCenter.clone().add(x, floorY - arenaCenter.getY(), z);
                floorLoc.getBlock().setType(Material.BLACK_CONCRETE);
                
                // Ceiling
                Location ceilingLoc = arenaCenter.clone().add(x, ceilingY - arenaCenter.getY(), z);
                ceilingLoc.getBlock().setType(Material.BLACK_CONCRETE);
                
                // Walls (only on perimeter)
                if (Math.abs(x) == arenaSize || Math.abs(z) == arenaSize) {
                    for (int y = floorY; y <= ceilingY; y++) {
                        Location wallLoc = arenaCenter.clone().add(x, y - arenaCenter.getY(), z);
                        wallLoc.getBlock().setType(Material.BLACK_CONCRETE);
                    }
                }
            }
        }
        
        // Add horror decorations
        Random rng = new Random();
        for (int i = 0; i < 20; i++) {
            int x = rng.nextInt(arenaSize * 2) - arenaSize;
            int z = rng.nextInt(arenaSize * 2) - arenaSize;
            // Don't place on edges
            if (Math.abs(x) < arenaSize - 1 && Math.abs(z) < arenaSize - 1) {
                Location decorLoc = arenaCenter.clone().add(x, floorY + 1 - arenaCenter.getY(), z);
                
                // Random horror decorations
                int decorType = rng.nextInt(4);
                switch (decorType) {
                    case 0:
                        decorLoc.getBlock().setType(Material.SOUL_SAND);
                        break;
                    case 1:
                        decorLoc.getBlock().setType(Material.SCULK);
                        break;
                    case 2:
                        decorLoc.getBlock().setType(Material.SCULK_VEIN);
                        break;
                    case 3:
                        // Soul fire
                        Location fireLoc = decorLoc.clone().add(0, 1, 0);
                        if (fireLoc.getBlock().getType() == Material.AIR) {
                            fireLoc.getBlock().setType(Material.SOUL_FIRE);
                        }
                        break;
                }
            }
        }
        
        // Add chains hanging from ceiling
        for (int i = 0; i < 10; i++) {
            int x = rng.nextInt(arenaSize * 2) - arenaSize;
            int z = rng.nextInt(arenaSize * 2) - arenaSize;
            if (Math.abs(x) < arenaSize - 2 && Math.abs(z) < arenaSize - 2) {
                for (int y = ceilingY - 1; y > floorY + 3; y -= 2) {
                    Location chainLoc = arenaCenter.clone().add(x, y - arenaCenter.getY(), z);
                    chainLoc.getBlock().setType(Material.CHAIN);
                }
            }
        }
        
        // Teleport player to arena center
        Location teleportLoc = arenaCenter.clone().add(0, floorY + 1 - arenaCenter.getY(), 0);
        player.teleport(teleportLoc);
        
        // Atmospheric effects
        player.playSound(teleportLoc, Sound.ENTITY_WITHER_SPAWN, 2.0f, 0.5f);
        player.playSound(teleportLoc, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 1.5f, 0.3f);
        
        // Start boss fight
        bossPhase = true;
        startBossPhase(arenaCenter);
        
        player.sendMessage(SmallCaps.convert("§4§l☠ §c§lHas entrado en la Cámara del Olvido."));
        player.sendMessage(SmallCaps.convert("§4§oNo hay salida. Solo la victoria... o la aniquilación."));
    }

    // ==================== WHISPER SYSTEM ====================

    private void startWhispers() {
        final Random rng = new Random();
        whisperTask = new BukkitRunnable() {
            @Override public void run() {
                if (!active || !player.isOnline() || bossPhase) { cancel(); return; }
                String whisper = WHISPERS[whisperIndex % WHISPERS.length];
                player.sendMessage(SmallCaps.convert(whisper));
                player.playSound(player.getLocation(), Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.15f, 0.4f + rng.nextFloat() * 0.3f);
                whisperIndex++;
            }
        }.runTaskTimer(plugin, 200L, 160L + new Random().nextInt(80)); // Every 8-12 seconds
    }

    // ==================== HEARTBEAT ====================

    private void startHeartbeat() {
        heartbeatTask = new BukkitRunnable() {
            int beat = 0;
            @Override public void run() {
                if (!active || !player.isOnline()) { cancel(); return; }
                beat++;
                // Heartbeat gets faster as keys are collected
                float speed = 1.0f - (keysCollected * 0.15f);
                if (bossPhase) speed = 0.4f;
                player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT,
                        0.3f + (keysCollected * 0.1f), speed);
            }
        }.runTaskTimer(plugin, 60L, 40L);
    }

    // ==================== BOSS PHASE ====================

    private void startBossPhase() {
        bossPhase = true;

        // Remove all shadows
        for (Entity e : shadowEntities) {
            if (e != null && !e.isDead()) {
                e.getWorld().spawnParticle(Particle.SMOKE, e.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.1);
                spawnedEntityIds.remove(e.getUniqueId());
                e.remove();
            }
        }
        shadowEntities.clear();

        // Cancel maze tasks
        if (chaseTask != null) chaseTask.cancel();
        if (horrorTask != null) horrorTask.cancel();
        if (whisperTask != null) whisperTask.cancel();
        if (rushTask != null) rushTask.cancel();

        // Dramatic entrance
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§4§l☠ §8━━━━━━━━━━━━━━━━━━━━━━━━━━━━ §4§l☠"));
        player.sendMessage(SmallCaps.convert("§4§oEl laberinto se detiene."));
        player.sendMessage(SmallCaps.convert("§4§oTodo queda en silencio."));
        player.sendMessage(SmallCaps.convert("§8§oY entonces... lo sientes."));
        player.sendMessage(SmallCaps.convert("§4§l§oEL OLVIDO DESPIERTA."));
        player.sendMessage(SmallCaps.convert("§4§l☠ §8━━━━━━━━━━━━━━━━━━━━━━━━━━━━ §4§l☠"));
        player.sendMessage("");

        player.sendTitle("§4§l☠ EL OLVIDO ☠", SmallCaps.convert("§8Lo que devora todo recuerdo..."), 10, 60, 20);
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_ROAR, 2.0f, 0.1f);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 0.3f);
        player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 1.0f, 0.5f);

        // Teleport to boss room
        Location bossCenter = labyrinthWorld.getBossRoomCenter();
        player.teleport(bossCenter);

        // Start boss after dramatic pause
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            boss = new LabyrinthBoss(plugin, manager, this, player, bossCenter);
            boss.start();
        }, 80L);
    }

    private void startBossPhase(Location arenaCenter) {
        bossPhase = true;

        // Remove all shadows
        for (Entity e : shadowEntities) {
            if (e != null && !e.isDead()) {
                e.getWorld().spawnParticle(Particle.SMOKE, e.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.1);
                spawnedEntityIds.remove(e.getUniqueId());
                e.remove();
            }
        }
        shadowEntities.clear();

        // Cancel maze tasks
        if (chaseTask != null) chaseTask.cancel();
        if (horrorTask != null) horrorTask.cancel();
        if (whisperTask != null) whisperTask.cancel();
        if (rushTask != null) rushTask.cancel();

        // Dramatic entrance
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§4§l☠ §8━━━━━━━━━━━━━━━━━━━━━━━━━━━━ §4§l☠"));
        player.sendMessage(SmallCaps.convert("§4§oEl laberinto se detiene."));
        player.sendMessage(SmallCaps.convert("§4§oTodo queda en silencio."));
        player.sendMessage(SmallCaps.convert("§8§oY entonces... lo sientes."));
        player.sendMessage(SmallCaps.convert("§4§l§oEL OLVIDO DESPIERTA."));
        player.sendMessage(SmallCaps.convert("§4§l☠ §8━━━━━━━━━━━━━━━━━━━━━━━━━━━━ §4§l☠"));
        player.sendMessage("");

        player.sendTitle("§4§l☠ EL OLVIDO ☠", SmallCaps.convert("§8Lo que devora todo recuerdo..."), 10, 60, 20);
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_ROAR, 2.0f, 0.1f);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 0.3f);
        player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 1.0f, 0.5f);

        // Teleport to custom arena center
        player.teleport(arenaCenter);

        // Start boss after dramatic pause
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            boss = new LabyrinthBoss(plugin, manager, this, player, arenaCenter);
            boss.start();
        }, 80L);
    }

    // ==================== BOSS CALLBACKS ====================

    public void onBossDefeated() {
        bossPhase = false;

        // Victory sequence
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§4§l☠ §8━━━━━━━━━━━━━━━━━━━━━━━━━━━━ §4§l☠"));
        player.sendMessage(SmallCaps.convert("§a§l✦ §f§lEl Olvido ha sido destruido."));
        player.sendMessage(SmallCaps.convert("§7§oLas paredes del laberinto se desmoronan..."));
        player.sendMessage(SmallCaps.convert("§7§oLas almas de los Olvidados ascienden, libres al fin."));
        player.sendMessage(SmallCaps.convert("§4§l☠ §8━━━━━━━━━━━━━━━━━━━━━━━━━━━━ §4§l☠"));
        player.sendMessage("");

        player.removePotionEffect(PotionEffectType.DARKNESS);
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);

        World w = player.getWorld();
        Location loc = player.getLocation();
        w.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 2, 0), 200, 3, 3, 3, 1.0);
        w.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f);

        // Teleport back and give reward after delay
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (originalLocation != null && originalLocation.getWorld() != null) {
                player.teleport(originalLocation);
            } else {
                player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            }

            // Give boots reward
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                VoidBoots.giveVoidBoots(player, plugin);

                // Notify manager
                manager.onQuest7BossDefeated(player);

                // Delete labyrinth world
                if (labyrinthWorld != null) labyrinthWorld.deleteWorld();
            }, 40L);
        }, 100L);
    }

    // ==================== CLEANUP ====================

    public void cleanup() {
        active = false;
        if (ambientTask != null) ambientTask.cancel();
        if (horrorTask != null) horrorTask.cancel();
        if (chaseTask != null) chaseTask.cancel();
        if (progressTask != null) progressTask.cancel();
        if (whisperTask != null) whisperTask.cancel();
        if (heartbeatTask != null) heartbeatTask.cancel();
        if (rushTask != null) rushTask.cancel();

        // Remove shadows
        for (Entity e : shadowEntities) {
            if (e != null && !e.isDead()) {
                spawnedEntityIds.remove(e.getUniqueId());
                e.remove();
            }
        }
        shadowEntities.clear();

        // Cleanup boss
        if (boss != null) boss.cleanup();

        // Remove effects and sounds
        if (player.isOnline()) {
            // Stop all playing sounds
            player.stopSound("minecraft:entity.wither.ambient");
            player.stopSound("minecraft:entity.wither.death");
            player.stopSound("minecraft:entity.ghast.scream");
            player.stopSound("minecraft:entity.enderman.scream");
            player.stopSound("minecraft:entity.vindicator.celebrate");
            player.stopSound("minecraft:entity.warden.nearby_closest");
            player.stopSound("minecraft:entity.warden.heartbeat");
            player.stopSound("minecraft:block.sculk_sensor.clicking");
            player.stopSound("minecraft:entity.zombie.step");
            player.stopSound("minecraft:entity.wither_skeleton.step");
            player.stopSound("minecraft:entity.iron_golem.step");
            player.stopSound("minecraft:entity.enderman.teleport");
            
            // Remove potion effects
            player.removePotionEffect(PotionEffectType.DARKNESS);
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            player.removePotionEffect(PotionEffectType.NAUSEA);

            // Teleport back if still in labyrinth world
            if (labyrinthWorld != null && labyrinthWorld.getWorld() != null
                    && player.getWorld().equals(labyrinthWorld.getWorld())) {
                if (originalLocation != null && originalLocation.getWorld() != null)
                    player.teleport(originalLocation);
                else
                    player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            }
        }

        // Delete world
        if (labyrinthWorld != null) labyrinthWorld.deleteWorld();
    }

    // ==================== GETTERS ====================

    public boolean isActive() { return active; }
    public boolean isBossPhase() { return bossPhase; }
    public LabyrinthBoss getBoss() { return boss; }
    public LabyrinthWorld getLabyrinthWorld() { return labyrinthWorld; }

    public boolean isSpawnedEntity(UUID entityId) {
        if (spawnedEntityIds.contains(entityId)) return true;
        if (boss != null && boss.isBossEntity(entityId)) return true;
        if (boss != null && boss.isSummonedMob(entityId)) return true;
        return false;
    }
}
