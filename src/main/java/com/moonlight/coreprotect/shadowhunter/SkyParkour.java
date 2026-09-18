package com.moonlight.coreprotect.shadowhunter;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Misión 5 — "El Jardín del Cielo"
 * 6 pruebas: Carta → Sendero de Pétalos → Puente Fantasma → Corredor del Viento
 * → Jardín de Memorias → Guardián del Jardín (mini-boss).
 * Recompensa: Último Suspiro.
 */
public class SkyParkour {

    public enum Phase {
        LETTER, PARKOUR, GHOST_BRIDGE, WIND, MEMORY, BOSS
    }

    // ==================== CARTA DEL ERRANTE ====================
    private static final String[] LETTER = {
            "§d§l✉ §7§lCarta del Errante",
            "",
            "§f  Si estás leyendo esto, es que ya no estoy.",
            "§f  Construí este jardín cuando entendí que",
            "§f  lo único que me quedaba era crear algo",
            "§f  que sobreviviera a mi nombre.",
            "",
            "§f  Cada prueba que dejé aquí es un trozo",
            "§f  de lo que fui. El sendero de pétalos,",
            "§f  porque siempre caminé entre flores.",
            "§f  El puente que desaparece, porque nada",
            "§f  de lo que toqué fue permanente.",
            "",
            "§f  Si llegas al final, encontrarás mi arco.",
            "§f  El último que hice. El mejor.",
            "",
            "§7§o  — El que una vez fue carpintero"
    };

    // Parkour islands: {dx, dz, width, depth}
    private static final int[][] PARKOUR_ISLANDS = {
            { 0, 0, 5, 5 },
            { 6, 3, 3, 3 },
            { 10, -1, 3, 3 },
            { 15, 2, 3, 3 },
            { 20, -2, 3, 3 },
            { 25, 1, 4, 4 },
    };

    private static final int GHOST_BRIDGE_LENGTH = 12;

    private static final Material[] MEMORY_COLORS = {
            Material.RED_WOOL, Material.BLUE_WOOL, Material.YELLOW_WOOL, Material.LIME_WOOL
    };

    // X-offset for each phase origin from skyBase
    private static final int[] PHASE_X = { 0, 12, 42, 62, 82, 102 };

    // ==================== CAMPOS ====================
    private final CoreProtectPlugin plugin;
    private final ShadowHunterManager manager;
    private final Player player;
    private final ShadowHunterNPC npc;
    private boolean active = false;
    private Phase currentPhase = Phase.LETTER;
    private Location skyBase;
    private Location playerOrigin;
    private Location phaseSpawn;
    private BukkitTask monitorTask;
    private BukkitTask ghostTask;
    private final List<Location> placedBlocks = new ArrayList<>();
    private final List<Entity> spawnedEntities = new ArrayList<>();

    // Memory sequence detection
    private Material lastWoolTouched = null;
    private long lastWoolTouchTime = 0;
    private static final long WOOL_TOUCH_COOLDOWN = 1500; // 5 seconds

    // Checkpoint system
    private Phase lastCheckpoint = Phase.LETTER;

    // Ghost bridge
    private final List<Location> ghostGroupA = new ArrayList<>();
    private final List<Location> ghostGroupB = new ArrayList<>();
    private boolean ghostShowA = true;
    private boolean ghostCycleActive = false;

    // Memory game
    private final List<Integer> memorySequence = new ArrayList<>();
    private int memoryStep = 0;
    private int memoryRound = 0;
    private boolean memoryInputEnabled = false;

    // Boss
    private LivingEntity bossEntity;

    public SkyParkour(CoreProtectPlugin plugin, ShadowHunterManager manager,
            Player player, ShadowHunterNPC npc) {
        this.plugin = plugin;
        this.manager = manager;
        this.player = player;
        this.npc = npc;
    }

    public boolean isActive() {
        return active;
    }

    // ==================== START ====================
    public void start() {
        active = true;
        playerOrigin = player.getLocation().clone();
        Location pLoc = player.getLocation();
        Vector fwd = pLoc.getDirection().setY(0).normalize();
        skyBase = new Location(pLoc.getWorld(),
                Math.floor(pLoc.getX() + fwd.getX() * 40), 220,
                Math.floor(pLoc.getZ() + fwd.getZ() * 40));

        if (player.getAllowFlight()) {
            player.setAllowFlight(false);
            player.setFlying(false);
        }

        buildAll();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active)
                return;
            enterPhase(Phase.LETTER);
        }, 5L);
    }

    // ==================== GESTIÓN DE FASES ====================
    private void enterPhase(Phase phase) {
        currentPhase = phase;
        switch (phase) {
            case LETTER:
                phaseSpawn = skyBase.clone().add(3, 1.5, 3);
                player.teleport(phaseSpawn);
                giveEffects();
                player.sendTitle("§d§l✦ El Jardín del Cielo ✦",
                        "§7Un lugar olvidado entre las nubes...", 10, 60, 20);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!active)
                        return;
                    player.sendMessage("");
                    for (String line : LETTER)
                        player.sendMessage(line);
                    player.sendMessage("");
                    player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 0.7f);
                    player.sendMessage(SmallCaps.convert("§d✦ §7Camina hacia el sendero de pétalos para comenzar..."));
                }, 40L);
                startMonitoring();
                break;

            case PARKOUR:
                phaseSpawn = getPhaseOrigin(1).clone().add(0, 1.5, 0);
                player.teleport(phaseSpawn);
                player.sendTitle("§d§lI. Sendero de Pétalos",
                        "§7Salta entre las islas del jardín", 10, 40, 10);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
                updateCheckpoint();
                break;

            case GHOST_BRIDGE:
                phaseSpawn = getPhaseOrigin(2).clone().add(1, 1.5, 1);
                player.teleport(phaseSpawn);
                player.sendTitle("§b§lII. Puente Fantasma",
                        "§7Los bloques aparecen y desaparecen...", 10, 40, 10);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.4f);
                if (!ghostCycleActive) {
                    startGhostCycle();
                }
                break;

            case WIND:
                phaseSpawn = getPhaseOrigin(3).clone().add(0, 1.5, 0);
                player.teleport(phaseSpawn);
                player.sendTitle("§a§lIII. Corredor del Viento",
                        "§7El aire te impulsará... ¡mantén el equilibrio!", 10, 60, 10);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.6f);
                player.sendMessage(SmallCaps.convert("§a✦ §7El viento te empujará hacia adelante. ¡No te caigas del camino!"));
                updateCheckpoint();
                break;

            case MEMORY:
                phaseSpawn = getPhaseOrigin(4).clone().add(2, 1.5, 2);
                player.teleport(phaseSpawn);
                player.sendTitle("§e§lIV. Jardín de Memorias",
                        "§7Repite la secuencia de colores...", 10, 40, 10);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.8f);
                if (memoryRound == 0) {
                    memoryRound = 0;
                    memorySequence.clear();
                }
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (active)
                        startMemoryRound();
                }, 40L);
                updateCheckpoint();
                break;

            case BOSS:
                phaseSpawn = getPhaseOrigin(5).clone().add(4, 1.5, 4);
                player.teleport(phaseSpawn);
                player.sendTitle("§c§lV. El Guardián del Jardín",
                        "§7La última prueba...", 10, 40, 10);
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.5f);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (active)
                        spawnBoss();
                }, 60L);
                break;
        }
    }

    // ==================== CONSTRUCCIÓN ====================
    private void buildAll() {
        World w = skyBase.getWorld();

        // Fase 0: Claro del jardín 7×7
        buildPlatform(w, skyBase.getBlockX(), skyBase.getBlockY(), skyBase.getBlockZ(),
                7, 7, Material.GRASS_BLOCK, Material.DIRT);
        for (int dx = -2; dx <= 2; dx += 4)
            for (int dz = -2; dz <= 2; dz += 4)
                setBlock(w, skyBase.getBlockX() + dx, skyBase.getBlockY() + 1,
                        skyBase.getBlockZ() + dz, Material.CHERRY_SAPLING);

        // Fase 1: Islas de parkour
        Location pk = getPhaseOrigin(1);
        for (int[] isl : PARKOUR_ISLANDS)
            buildPlatform(w, pk.getBlockX() + isl[0], pk.getBlockY(),
                    pk.getBlockZ() + isl[1], isl[2], isl[3],
                    Material.MOSS_BLOCK, Material.DIRT);
        setBlock(w, pk.getBlockX() + 29, pk.getBlockY() + 1,
                pk.getBlockZ() + 1, Material.SOUL_LANTERN);

        // Fase 2: Puente fantasma — plataformas de inicio/fin + segmentos fantasma
        Location gb = getPhaseOrigin(2);
        buildPlatform(w, gb.getBlockX(), gb.getBlockY(), gb.getBlockZ(),
                3, 3, Material.DEEPSLATE_TILES, Material.DEEPSLATE);
        buildPlatform(w, gb.getBlockX() + GHOST_BRIDGE_LENGTH + 4, gb.getBlockY(),
                gb.getBlockZ(), 3, 3, Material.DEEPSLATE_TILES, Material.DEEPSLATE);
        for (int i = 0; i < GHOST_BRIDGE_LENGTH; i++) {
            int bx = gb.getBlockX() + 2 + i;
            for (int bz = gb.getBlockZ() - 1; bz <= gb.getBlockZ() + 1; bz++) {
                Location loc = new Location(w, bx, gb.getBlockY(), bz);
                if (i % 4 < 2)
                    ghostGroupA.add(loc);
                else
                    ghostGroupB.add(loc);
            }
        }

        // Fase 3: Corredor del viento — camino estrecho 15 bloques
        Location wi = getPhaseOrigin(3);
        for (int i = 0; i < 15; i++) {
            setBlock(w, wi.getBlockX() + i, wi.getBlockY(), wi.getBlockZ(),
                    Material.END_STONE_BRICKS);
            setBlock(w, wi.getBlockX() + i, wi.getBlockY() - 1, wi.getBlockZ(),
                    Material.BLACKSTONE);
        }
        buildPlatform(w, wi.getBlockX() + 17, wi.getBlockY(), wi.getBlockZ(),
                3, 3, Material.END_STONE_BRICKS, Material.BLACKSTONE);

        // Fase 4: Plataforma de memoria 5×5 con 4 lanas de colores
        Location mm = getPhaseOrigin(4);
        buildPlatform(w, mm.getBlockX(), mm.getBlockY(), mm.getBlockZ(),
                5, 5, Material.QUARTZ_BLOCK, Material.SMOOTH_QUARTZ);
        int mx = mm.getBlockX(), my = mm.getBlockY(), mz = mm.getBlockZ();
        setBlock(w, mx - 1, my, mz - 1, Material.RED_WOOL);
        setBlock(w, mx + 1, my, mz - 1, Material.BLUE_WOOL);
        setBlock(w, mx - 1, my, mz + 1, Material.YELLOW_WOOL);
        setBlock(w, mx + 1, my, mz + 1, Material.LIME_WOOL);

        // Fase 5: Arena del boss 9×9 con barreras invisibles
        Location bb = getPhaseOrigin(5);
        buildPlatform(w, bb.getBlockX(), bb.getBlockY(), bb.getBlockZ(),
                9, 9, Material.CRYING_OBSIDIAN, Material.OBSIDIAN);

        // Barreras invisibles para que el boss no caiga — muro completo alrededor
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                if (Math.abs(x) == 5 || Math.abs(z) == 5) { // Solo bordes
                    // Techo
                    setBlock(w, bb.getBlockX() + x, bb.getBlockY() + 6, bb.getBlockZ() + z, Material.BARRIER);
                    // Paredes a nivel del suelo (altura 1-3) para que no se caiga
                    for (int h = 1; h <= 3; h++) {
                        setBlock(w, bb.getBlockX() + x, bb.getBlockY() + h, bb.getBlockZ() + z, Material.BARRIER);
                    }
                }
            }
        }

        for (int dx : new int[] { -4, 4 })
            for (int dz : new int[] { -4, 4 }) {
                for (int h = 1; h <= 4; h++)
                    setBlock(w, bb.getBlockX() + dx, bb.getBlockY() + h,
                            bb.getBlockZ() + dz, Material.PURPUR_PILLAR);
                setBlock(w, bb.getBlockX() + dx, bb.getBlockY() + 5,
                        bb.getBlockZ() + dz, Material.END_ROD);
            }
    }

    private void buildPlatform(World w, int cx, int cy, int cz,
            int width, int depth, Material surface, Material under) {
        int hw = width / 2, hd = depth / 2;
        for (int x = cx - hw; x <= cx + hw; x++)
            for (int z = cz - hd; z <= cz + hd; z++) {
                setBlock(w, x, cy, z, surface);
                setBlock(w, x, cy - 1, z, under);
            }
    }

    private void setBlock(World w, int x, int y, int z, Material mat) {
        Block b = w.getBlockAt(x, y, z);
        b.setType(mat);
        placedBlocks.add(b.getLocation());
    }

    // ==================== MONITOREO ====================
    private volatile boolean respawnCooldown = false;

    private void startMonitoring() {
        // Cancel any existing monitor task to prevent stacking
        if (monitorTask != null) {
            try { monitorTask.cancel(); } catch (Exception ignored) {}
        }
        final int fallY = (int) skyBase.getY() - 30;
        monitorTask = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!active || !player.isOnline()) {
                    cancel();
                    return;
                }
                Location pLoc = player.getLocation();

                if (pLoc.getY() < fallY) {
                    if (!respawnCooldown) {
                        respawnCooldown = true;
                        respawnAtPhase();
                        Bukkit.getScheduler().runTaskLater(plugin, () -> respawnCooldown = false, 20L);
                    }
                    ticks++;
                    return;
                }
                if (ticks % 200 == 0)
                    giveEffects();

                switch (currentPhase) {
                    case LETTER:
                        monitorLetter(pLoc);
                        break;
                    case PARKOUR:
                        monitorParkour(pLoc);
                        break;
                    case GHOST_BRIDGE:
                        monitorGhostBridge(pLoc);
                        break;
                    case WIND:
                        monitorWind(pLoc, ticks);
                        break;
                    case MEMORY:
                        monitorMemory(pLoc);
                        break;
                    case BOSS:
                        monitorBoss();
                        break;
                }

                if (ticks % 4 == 0) {
                    int n = currentPhase.ordinal();
                    com.moonlight.coreprotect.util.ActionBarUtil.send(player,
                                    "§d§lJardín del Cielo §f— Prueba " + n + "/"
                                            + (Phase.values().length - 1) + "  " + buildBar(n));
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0, 2);
    }

    // ==================== MONITORES POR FASE ====================
    private void monitorLetter(Location pLoc) {
        Location parkourStart = getPhaseOrigin(1).clone().add(0, 1, 0);
        if (pLoc.distanceSquared(parkourStart) < 36)
            enterPhase(Phase.PARKOUR);
    }

    private void monitorParkour(Location pLoc) {
        Location end = getPhaseOrigin(1).clone().add(29, 1, 1);
        if (pLoc.distanceSquared(end) < 16)
            enterPhase(Phase.GHOST_BRIDGE);
    }

    private void monitorGhostBridge(Location pLoc) {
        Location end = getPhaseOrigin(2).clone().add(GHOST_BRIDGE_LENGTH + 5, 1, 0);
        if (pLoc.distanceSquared(end) < 16) {
            if (ghostTask != null) {
                ghostTask.cancel();
                ghostTask = null;
            }
            // Restaurar todos los bloques fantasma a AIR
            for (Location l : ghostGroupA)
                l.getBlock().setType(Material.AIR);
            for (Location l : ghostGroupB)
                l.getBlock().setType(Material.AIR);
            enterPhase(Phase.WIND);
        }
    }

    private void monitorWind(Location pLoc, int ticks) {
        // Impulso de aire constante: ráfagas laterales que dificultan el paso
        Location wi = getPhaseOrigin(3);

        // Cada 10 ticks (~0.5s): ráfaga de viento que empuja
        if (ticks % 10 == 0) {
            double sideGust = (Math.random() - 0.5) * 0.8; // Ráfaga lateral aleatoria
            double upGust = 0.15 + Math.random() * 0.1; // Impulso hacia arriba

            // Si el jugador está agachado (shift), reducir las ráfagas laterales
            if (player.isSneaking()) {
                sideGust *= 0.3;
                upGust *= 0.5;
            }

            Vector push = new Vector(0, upGust, sideGust);
            player.setVelocity(player.getVelocity().add(push));

            // Partículas de viento
            pLoc.getWorld().spawnParticle(Particle.CLOUD, pLoc.clone().add(0, 1, 0),
                    5, 0.3, 0.3, 0.3, 0.05);
            pLoc.getWorld().spawnParticle(Particle.CHERRY_LEAVES, pLoc.clone().add(0, 0.5, 0),
                    3, 0.5, 0.3, 0.5, 0.02);
        }

        // Sonido de viento cada 2 segundos
        if (ticks % 40 == 0) {
            pLoc.getWorld().playSound(pLoc, Sound.ENTITY_BREEZE_WIND_BURST, 0.3f, 1.5f);
        }

        // Check end of wind corridor
        Location end = wi.clone().add(17, 1, 0);
        if (pLoc.distanceSquared(end) < 16)
            enterPhase(Phase.MEMORY);
    }

    private void monitorMemory(Location pLoc) {
        if (!memoryInputEnabled)
            return;
        Block below = pLoc.clone().subtract(0, 0.3, 0).getBlock();
        int colorIdx = -1;
        for (int i = 0; i < MEMORY_COLORS.length; i++)
            if (below.getType() == MEMORY_COLORS[i]) {
                colorIdx = i;
                break;
            }
        if (colorIdx < 0)
            return;

        // Prevent counting same wool multiple times
        Material currentWool = below.getType();
        long currentTime = System.currentTimeMillis();
        if (currentWool == lastWoolTouched && currentTime - lastWoolTouchTime < WOOL_TOUCH_COOLDOWN) {
            return; // Same wool, ignore for cooldown period
        }

        lastWoolTouched = currentWool;
        lastWoolTouchTime = currentTime;

        if (colorIdx == memorySequence.get(memoryStep)) {
            memoryStep++;
            player.playSound(pLoc, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f,
                    1.0f + memoryStep * 0.15f);
            pLoc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                    pLoc.clone().add(0, 1, 0), 8, 0.3, 0.3, 0.3, 0);

            if (memoryStep >= memorySequence.size()) {
                memoryInputEnabled = false;
                if (memoryRound >= 6) {
                    player.sendMessage(SmallCaps.convert("§e✦ §7¡Secuencia completa! Has superado la prueba."));
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (active)
                            enterPhase(Phase.BOSS);
                    }, 30L);
                } else {
                    player.sendMessage(SmallCaps.convert("§e✦ §7Ronda " + memoryRound + " superada."));
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (active)
                            startMemoryRound();
                    }, 30L);
                }
            }
        } else {
            memoryInputEnabled = false;
            player.playSound(pLoc, Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
            player.sendMessage(SmallCaps.convert("§c✦ §7Secuencia incorrecta. Reintentando..."));
            memoryStep = 0;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (active)
                    playMemorySequence();
            }, 40L);
        }
    }

    private void monitorBoss() {
        if (bossEntity != null && bossEntity.isDead()) {
            bossEntity = null;
            onBossDefeated();
        }
    }

    // ==================== PUENTE FANTASMA ====================
    private void startGhostCycle() {
        if (ghostCycleActive)
            return;
        ghostCycleActive = true;
        ghostShowA = true;
        updateGhostBlocks();
        ghostTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!active) {
                    cancel();
                    ghostCycleActive = false;
                    return;
                }
                ghostShowA = !ghostShowA;
                updateGhostBlocks();
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                        0.4f, ghostShowA ? 1.2f : 0.8f);
            }
        }.runTaskTimer(plugin, 40L, 40L);
    }

    private void updateGhostBlocks() {
        World w = skyBase.getWorld();

        // Limpiar todos los bloques primero
        for (Location loc : ghostGroupA) {
            loc.getBlock().setType(Material.AIR);
        }
        for (Location loc : ghostGroupB) {
            loc.getBlock().setType(Material.AIR);
        }

        // Mostrar solo el grupo correspondiente
        if (ghostShowA) {
            for (Location loc : ghostGroupA) {
                loc.getBlock().setType(Material.LIGHT_BLUE_STAINED_GLASS);
                w.spawnParticle(Particle.END_ROD,
                        loc.clone().add(0.5, 1.2, 0.5), 2, 0.2, 0.1, 0.2, 0.01);
            }
        } else {
            for (Location loc : ghostGroupB) {
                loc.getBlock().setType(Material.PINK_STAINED_GLASS);
                w.spawnParticle(Particle.END_ROD,
                        loc.clone().add(0.5, 1.2, 0.5), 2, 0.2, 0.1, 0.2, 0.01);
            }
        }
    }

    // ==================== JUEGO DE MEMORIA ====================
    private void startMemoryRound() {
        memoryRound++;
        memorySequence.add(new Random().nextInt(MEMORY_COLORS.length));
        memoryStep = 0;
        player.sendMessage(SmallCaps.convert("§e✦ §7Ronda §b" + memoryRound + "§7: observa la secuencia..."));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (active)
                playMemorySequence();
        }, 20L);
    }

    private void playMemorySequence() {
        memoryStep = 0;
        memoryInputEnabled = false;
        Location memBase = getPhaseOrigin(4);
        World w = skyBase.getWorld();

        for (int i = 0; i < memorySequence.size(); i++) {
            final int idx = memorySequence.get(i);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!active)
                    return;
                Location woolLoc = getMemoryWoolLoc(idx, memBase);
                w.spawnParticle(Particle.DUST, woolLoc.clone().add(0.5, 1.5, 0.5),
                        15, 0.3, 0.3, 0.3, 0,
                        getMemoryDust(idx));
                player.playSound(woolLoc, Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f,
                        0.8f + idx * 0.3f);
            }, 20L * i + 10L);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active)
                return;
            memoryInputEnabled = true;
            player.sendMessage(SmallCaps.convert("§e✦ §7¡Tu turno! Pisa los colores en orden."));
        }, 20L * memorySequence.size() + 30L);
    }

    private Location getMemoryWoolLoc(int idx, Location base) {
        int bx = base.getBlockX(), by = base.getBlockY(), bz = base.getBlockZ();
        switch (idx) {
            case 0:
                return new Location(skyBase.getWorld(), bx - 1, by, bz - 1);
            case 1:
                return new Location(skyBase.getWorld(), bx + 1, by, bz - 1);
            case 2:
                return new Location(skyBase.getWorld(), bx - 1, by, bz + 1);
            case 3:
                return new Location(skyBase.getWorld(), bx + 1, by, bz + 1);
            default:
                return base.clone();
        }
    }

    private Particle.DustOptions getMemoryDust(int idx) {
        switch (idx) {
            case 0:
                return new Particle.DustOptions(Color.RED, 1.5f);
            case 1:
                return new Particle.DustOptions(Color.BLUE, 1.5f);
            case 2:
                return new Particle.DustOptions(Color.YELLOW, 1.5f);
            case 3:
                return new Particle.DustOptions(Color.LIME, 1.5f);
            default:
                return new Particle.DustOptions(Color.WHITE, 1.5f);
        }
    }

    // ==================== BOSS ====================
    // ==================== BOSS ====================
    private void spawnBoss() {
        Location bossLoc = getPhaseOrigin(5).clone().add(0, 1.5, 0);
        World w = bossLoc.getWorld();
        w.spawnParticle(Particle.DUST, bossLoc, 30, 1, 1, 1, 0,
                new Particle.DustOptions(Color.fromRGB(200, 50, 80), 2.0f));
        w.playSound(bossLoc, Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.5f);

        Skeleton boss = w.spawn(bossLoc, Skeleton.class);
        boss.setCustomName(SmallCaps.convert("§d§lGuardián del Jardín"));
        boss.setCustomNameVisible(true);
        boss.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(200); // Vida aumentada de 150 a 200
        boss.setHealth(200);
        boss.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED).setBaseValue(0.40); // Más rápido
        boss.getEquipment().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
        boss.getEquipment().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
        boss.getEquipment().setBoots(new ItemStack(Material.DIAMOND_BOOTS));
        ItemStack bow = new ItemStack(Material.BOW);
        org.bukkit.inventory.meta.ItemMeta bowMeta = bow.getItemMeta();
        if (bowMeta != null) {
            bowMeta.addEnchant(org.bukkit.enchantments.Enchantment.KNOCKBACK, 10, true);
            bowMeta.addEnchant(org.bukkit.enchantments.Enchantment.POWER, 5, true); // Daño extra
            bow.setItemMeta(bowMeta);
        }
        boss.getEquipment().setItemInMainHand(bow);
        boss.getEquipment().setItemInMainHandDropChance(0.0f);
        boss.getEquipment().setHelmetDropChance(0.0f);
        boss.getEquipment().setChestplateDropChance(0.0f);
        boss.getEquipment().setBootsDropChance(0.0f);
        boss.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 999999, 1, false, false));
        boss.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 999999, 1, false, false));
        boss.setRemoveWhenFarAway(false); // Evitar despawn

        // Hacer que resista fuego temporalmente por si cae lava
        boss.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 999999, 0, false, false));

        bossEntity = boss;
        spawnedEntities.add(boss);
        player.sendMessage(SmallCaps.convert("§c§l✦ §7El Guardián del Jardín ha despertado. ¡Demuestra tu valía!"));
        player.sendMessage(SmallCaps.convert("§7Tiene §c200❤ §7y domina el viento de la arena."));

        // IA Avanzada del Boss: Fuerza ataque continuo y usa habilidades
        new BukkitRunnable() {
            int ticks = 0;
            Location arenaCenter = getPhaseOrigin(5).clone().add(0, 1.5, 0);

            @Override
            public void run() {
                if (!active || boss.isDead() || !player.isOnline()) {
                    cancel();
                    return;
                }

                // 1. FORZAR TARGET: Siempre intentar atacar al jugador, incluso si no puede
                // verlo
                boss.setTarget(player);

                // Si está demasiado lejos del centro de la arena, teletransportarlo al centro
                if (boss.getLocation().distanceSquared(arenaCenter) > 100) {
                    boss.teleport(arenaCenter);
                    w.spawnParticle(Particle.PORTAL, boss.getLocation(), 30, 0.5, 1, 0.5, 0.1);
                    w.playSound(boss.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                }

                ticks++;

                // 2. HABILIDADES ALEATORIAS
                if (ticks % 60 == 0) { // Cada 3 segundos evalúa hacer una habilidad
                    double rand = Math.random();

                    if (rand < 0.25) {
                        // Habilidad: Teletransporte Evasivo (se pone a la espalda o lado del jugador)
                        Vector offset = player.getLocation().getDirection().multiply(-3);
                        offset.setY(0); // Mantenerlo en el mismo nivel Y relativo
                        Location tpLoc = player.getLocation().add(offset);
                        // Asegurarse de que el Y esté sobre la plataforma
                        tpLoc.setY(arenaCenter.getY());

                        // Solo teletransportarse si está dentro de la arena
                        if (tpLoc.distanceSquared(arenaCenter) <= 25) {
                            boss.teleport(tpLoc);
                            w.spawnParticle(Particle.REVERSE_PORTAL, boss.getLocation(), 50, 0.5, 1, 0.5, 0.2);
                            w.playSound(boss.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.5f);
                            player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 1.0f);
                        }
                    } else if (rand < 0.5) {
                        // Habilidad: Disparo Explosivo (Lanza una flecha con rastro de partículas que
                        // explota superficialmente)
                        Arrow explosive = boss.launchProjectile(Arrow.class);
                        Vector dir = player.getEyeLocation().toVector().subtract(boss.getEyeLocation().toVector())
                                .normalize();
                        explosive.setVelocity(dir.multiply(2.0)); // Disparo rápido
                        explosive.setFireTicks(200);

                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (explosive.isDead() || explosive.isOnGround()) {
                                    w.createExplosion(explosive.getLocation(), 2.0f, false, false); // Explosión que no
                                                                                                    // rompe bloques
                                    explosive.remove();
                                    cancel();
                                } else {
                                    w.spawnParticle(Particle.FLAME, explosive.getLocation(), 2, 0, 0, 0, 0.05);
                                }
                            }
                        }.runTaskTimer(plugin, 1, 1);
                        w.playSound(boss.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 2.0f);
                        player.sendMessage(SmallCaps.convert("§c⚠ §7¡El Guardián lanza un disparo explosivo!"));
                    } else if (rand < 0.70) {
                        // Habilidad: Viento Huracanado (Empuja levemente al jugador y lo levanta)
                        Vector upDraft = new Vector(0, 0.8, 0);
                        player.setVelocity(player.getVelocity().add(upDraft));
                        w.spawnParticle(Particle.CLOUD, player.getLocation(), 20, 1, 0, 1, 0.1);
                        w.playSound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.0f, 0.8f);
                        player.sendMessage(SmallCaps.convert("§b🌀 §7El Guardián invoca una corriente ascendente..."));
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 10);
    }

    private void onBossDefeated() {
        player.sendTitle( SmallCaps.convert("§d§l✦ Victoria ✦"), SmallCaps.convert("§7El Guardián ha caído..."), 10, 60, 20);
        World w = player.getWorld();
        Location loc = player.getLocation();
        w.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1, 0), 60, 1, 1, 1, 0.5);
        w.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.9f);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active)
                return;
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("§d§l✉ §7§lÚltima página de la carta..."));
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("§f  \"Llegaste. Sabía que lo harías."));
            player.sendMessage(SmallCaps.convert("§f   Toma el arco. Es lo mejor que hice."));
            player.sendMessage(SmallCaps.convert("§f   Cuando lo uses, acuérdate de alguien"));
            player.sendMessage(SmallCaps.convert("§f   que quiso construir algo que durara.\""));
            player.sendMessage(SmallCaps.convert("§7§o  — El Errante"));
            player.sendMessage("");
            player.playSound(player.getLocation(), Sound.ENTITY_ALLAY_DEATH, 0.8f, 0.5f);
        }, 40L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (active)
                complete();
        }, 120L);
    }

    // ==================== COMPLETAR ====================
    private void complete() {
        active = false;
        World world = player.getWorld();
        Location loc = player.getLocation();
        world.spawnParticle(Particle.CHERRY_LEAVES, loc.clone().add(0, 2, 0), 60, 2, 2, 2, 0.1);
        world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 2, 0), 40, 1.5, 1.5, 1.5, 0.4);

        VoidBow.giveVoidBow(player, plugin);
        manager.onQuest5Completed(player);

        Bukkit.broadcastMessage("§d§l✦ §b" + player.getName()
                + " §fha completado §d§lEl Jardín del Cielo §fy obtenido §d§lÚltimo Suspiro§f!");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            cleanup();
            if (player.isOnline()) {
                player.teleport(playerOrigin);
                player.sendMessage(SmallCaps.convert("§d✦ §7Has regresado al suelo."));
            }
        }, 200L);
    }

    // ==================== CHECKPOINT SYSTEM ====================
    private void updateCheckpoint() {
        lastCheckpoint = currentPhase;
    }

    // ==================== RESPAWN / MUERTE ====================
    public void respawnAtPhase() {
        player.sendMessage(SmallCaps.convert("§c✦ §7Has caído. Volviendo al último checkpoint..."));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.8f, 1.0f);

        // Tip de Shift solo al morir en la fase del viento
        if (currentPhase == Phase.WIND) {
            player.sendMessage(SmallCaps.convert("§e💡 §7Consejo: mantener §e§lSHIFT §7presionado te estabiliza contra el viento."));
        }

        // Respawn at last checkpoint (or LETTER if none)
        Phase respawnPhase = lastCheckpoint != null ? lastCheckpoint : Phase.LETTER;

        // If current phase is BOSS, respawn in BOSS phase (not checkpoint)
        if (currentPhase == Phase.BOSS) {
            respawnPhase = Phase.BOSS;
        }

        // Set phase spawn without calling enterPhase to avoid double teleport + monitor stacking
        currentPhase = respawnPhase;
        switch (respawnPhase) {
            case LETTER:   phaseSpawn = skyBase.clone().add(3, 1.5, 3); break;
            case PARKOUR:  phaseSpawn = getPhaseOrigin(1).clone().add(0, 1.5, 0); break;
            case GHOST_BRIDGE: phaseSpawn = getPhaseOrigin(2).clone().add(1, 1.5, 1); break;
            case WIND:     phaseSpawn = getPhaseOrigin(3).clone().add(0, 1.5, 0); break;
            case MEMORY:   phaseSpawn = getPhaseOrigin(4).clone().add(2, 1.5, 2); break;
            case BOSS:     phaseSpawn = getPhaseOrigin(5).clone().add(4, 1.5, 4); break;
        }

        if (phaseSpawn != null) {
            player.teleport(phaseSpawn);
            player.setVelocity(new Vector(0, 0, 0));
            player.setFallDistance(0);
            giveEffects();
        }
    }

    public void onPlayerDeath() {
    }

    public Location getRespawnLocation() {
        return phaseSpawn != null ? phaseSpawn.clone() : skyBase.clone().add(0, 1.5, 0);
    }

    public Phase getCurrentPhase() {
        return currentPhase;
    }

    public Location getSkyBase() {
        return skyBase;
    }

    public void addPlacedBlock(Location loc) {
        placedBlocks.add(loc);
    }

    // ==================== LIMPIEZA ====================
    public void cleanup() {
        active = false;
        if (monitorTask != null) {
            monitorTask.cancel();
            monitorTask = null;
        }
        if (ghostTask != null) {
            ghostTask.cancel();
            ghostTask = null;
        }
        ghostCycleActive = false;
        player.removePotionEffect(PotionEffectType.SLOW_FALLING);
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);

        for (Entity e : spawnedEntities)
            if (!e.isDead())
                e.remove();
        spawnedEntities.clear();

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Location loc : placedBlocks)
                loc.getBlock().setType(Material.AIR);
            placedBlocks.clear();
        });

        // Clean ghost block locations too
        for (Location l : ghostGroupA)
            l.getBlock().setType(Material.AIR);
        for (Location l : ghostGroupB)
            l.getBlock().setType(Material.AIR);

        if (npc != null)
            npc.despawnWithEffect();
    }

    // ==================== UTILIDADES ====================
    private Location getPhaseOrigin(int phaseIndex) {
        return skyBase.clone().add(PHASE_X[phaseIndex], 0, 0);
    }

    private void giveEffects() {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,
                72000, 0, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST,
                72000, 0, false, false, false));
    }

    private String buildBar(int current) {
        int total = Phase.values().length;
        StringBuilder sb = new StringBuilder("§8[");
        for (int i = 0; i < total; i++) {
            if (i < current)
                sb.append("§d▌");
            else if (i == current)
                sb.append("§b§l▌");
            else
                sb.append("§8▌");
        }
        sb.append("§8]");
        return sb.toString();
    }

    public Location getPlayerOrigin() {
        return playerOrigin;
    }

    public boolean isBossEntity(Entity entity) {
        return bossEntity != null && bossEntity.equals(entity);
    }

    public boolean isSpawnedEntity(UUID entityId) {
        if (bossEntity != null && bossEntity.getUniqueId().equals(entityId)) return true;
        return spawnedEntities.stream().anyMatch(e -> e != null && !e.isDead() && e.getUniqueId().equals(entityId));
    }
}
