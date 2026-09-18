package com.moonlight.coreprotect.shadowhunter;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * 8 fases post-boss de la Misión 3: "El Arquitecto del Vacío".
 */
public class VoidPostBoss {

    private final CoreProtectPlugin plugin;
    private final ShadowHunterManager manager;
    private final Player player;
    private final ShadowHunterNPC npc;
    private final VoidArena arena;

    private boolean active = false;
    private int currentPhase = 0;
    private BukkitRunnable currentTask;
    private final List<Entity> spawnedEntities = new ArrayList<>();
    private final List<Location> placedBlocks = new ArrayList<>();
    private org.bukkit.boss.BossBar phaseBar;

    // Fase 2
    private int knightX, knightZ, boardMoves;
    private final Set<String> visitedTiles = new HashSet<>();
    // Fase 3
    private final List<ItemStack> auctionedItems = new ArrayList<>();
    private int auctionTasksDone;
    // Fase 4
    private int pillarsCompleted;
    // Fase 6
    private int soulsJudged, soulsCorrect;
    private boolean waitingForSoul = false;
    private boolean isTransitioning = false;
    // Fase 7
    private int altarsPurified;
    private final Map<Location, Boolean> altarStates = new HashMap<>();
    // Fase 8
    private Zombie finalBoss;
    private long lastFinalDamage;

    public VoidPostBoss(CoreProtectPlugin plugin, ShadowHunterManager manager,
            Player player, ShadowHunterNPC npc, VoidArena arena) {
        this.plugin = plugin;
        this.manager = manager;
        this.player = player;
        this.npc = npc;
        this.arena = arena;
    }

    public void start() {
        active = true;
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§0§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        player.sendMessage(SmallCaps.convert("  §4§l✦ EL ARQUITECTO HA CAÍDO... ✦"));
        player.sendMessage(SmallCaps.convert("  §7Pero su §0dominio §7no se desvanece."));
        player.sendMessage(SmallCaps.convert("  §c§lQuedan 8 pruebas más..."));
        player.sendMessage(SmallCaps.convert("§0§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.8f, 0.3f);
        Bukkit.getScheduler().runTaskLater(plugin, this::startPhase1, 100L);
    }

    private void createPhaseBar(String title, org.bukkit.boss.BarColor color) {
        removePhaseBar();
        phaseBar = Bukkit.createBossBar(title, color, org.bukkit.boss.BarStyle.SOLID);
        phaseBar.addPlayer(player);
        phaseBar.setProgress(1.0);
    }

    private void removePhaseBar() {
        if (phaseBar != null) {
            phaseBar.removeAll();
            phaseBar = null;
        }
    }

    private void cleanupEntities() {
        for (Entity e : spawnedEntities) {
            if (e != null && !e.isDead())
                e.remove();
        }
        spawnedEntities.clear();
    }

    // ============ FASE 1: EL ECO DEL ARQUITECTO ============
    private void startPhase1() {
        if (!active)
            return;
        currentPhase = 1;
        createPhaseBar("§0§l✦ Fase 1/8: El Eco del Arquitecto ✦", org.bukkit.boss.BarColor.WHITE);
        player.sendTitle( SmallCaps.convert("§0§l✦ FASE 1 ✦"), SmallCaps.convert("§7El Eco del Arquitecto"), 10, 60, 20);
        player.sendMessage(SmallCaps.convert("§0§l✦ §c¡Esquiva ataques espectrales §e45s§c!"));
        player.playSound(player.getLocation(), Sound.ENTITY_GHAST_SCREAM, 0.6f, 0.5f);
        if (npc != null)
            npc.say("\"¿Creíste que morir sería suficiente?\"");
        Location fc = arena.getFloorCenter();
        if (fc == null) {
            completePhase(1);
            return;
        }

        currentTask = new BukkitRunnable() {
            int t = 0;
            double angle = 0;

            public void run() {
                if (!active || !player.isOnline()) {
                    cancel();
                    return;
                }
                if (t >= 900) {
                    cancel();
                    completePhase(1);
                    return;
                }
                if (phaseBar != null)
                    phaseBar.setProgress(1.0 - (double) t / 900);
                Location c = arena.getFloorCenter();
                if (c == null) {
                    cancel();
                    return;
                }
                angle += 0.08;
                double r = 6 + Math.sin(t * 0.05) * 2;
                Location echo = c.clone().add(Math.cos(angle) * r, 2, Math.sin(angle) * r);
                if (t % 2 == 0) {
                    echo.getWorld().spawnParticle(Particle.DUST, echo, 8, 0.3, 0.5, 0.3, 0,
                            new Particle.DustOptions(Color.fromRGB(50, 0, 80), 2.5f));
                    echo.getWorld().spawnParticle(Particle.SOUL, echo, 3, 0.2, 0.3, 0.2, 0.02);
                }
                if (t % 30 == 0 && t > 20)
                    fireProjectile(echo);
                if (t % 200 == 0 && t > 0)
                    shockwave(c);
                if (t % 160 == 0 && t > 40)
                    voidPillar(player.getLocation().clone());
                t++;
            }
        };
        currentTask.runTaskTimer(plugin, 0, 1);
    }

    private void fireProjectile(Location from) {
        Vector dir = player.getLocation().add(0, 1, 0).toVector().subtract(from.toVector()).normalize().multiply(0.6);
        World w = from.getWorld();
        w.playSound(from, Sound.ENTITY_BLAZE_SHOOT, 0.4f, 0.5f);
        new BukkitRunnable() {
            Location pos = from.clone();
            int life = 0;

            public void run() {
                if (!active || life > 40) {
                    cancel();
                    return;
                }
                pos.add(dir);
                w.spawnParticle(Particle.DUST, pos, 3, 0.1, 0.1, 0.1, 0,
                        new Particle.DustOptions(Color.fromRGB(80, 0, 120), 1.8f));
                if (pos.distance(player.getLocation().add(0, 1, 0)) < 1.8) {
                    player.damage(8.0);
                    cancel();
                }
                life++;
            }
        }.runTaskTimer(plugin, 5, 1);
    }

    private void shockwave(Location center) {
        player.sendTitle("", SmallCaps.convert("§c§l¡ONDA! §7¡Salta!"), 0, 25, 5);
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 1.2f);
        new BukkitRunnable() {
            double r = 1;

            public void run() {
                if (!active || r > 14) {
                    cancel();
                    return;
                }
                for (int i = 0; i < 20; i++) {
                    double a = Math.PI * 2 / 20 * i;
                    center.getWorld().spawnParticle(Particle.DUST,
                            center.clone().add(Math.cos(a) * r, 0.5, Math.sin(a) * r), 2, 0.1, 0.1, 0.1, 0,
                            new Particle.DustOptions(Color.fromRGB(100, 0, 0), 2.0f));
                }
                if (Math.abs(player.getLocation().distance(center) - r) < 2.0
                        && Math.abs(player.getLocation().getY() - center.getY()) < 2)
                    player.damage(10.0);
                r += 0.8;
            }
        }.runTaskTimer(plugin, 0, 2);
    }

    private void voidPillar(Location target) {
        World w = target.getWorld();
        w.spawnParticle(Particle.DUST, target.clone().add(0, 0.2, 0), 40, 1.5, 0.1, 1.5, 0,
                new Particle.DustOptions(Color.fromRGB(255, 50, 50), 2.0f));
        w.playSound(target, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.5f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active)
                return;
            for (double y = 0; y < 8; y += 0.5)
                w.spawnParticle(Particle.DUST, target.clone().add(0, y, 0), 5, 0.3, 0.1, 0.3, 0,
                        new Particle.DustOptions(Color.fromRGB(0, 0, 0), 3.0f));
            w.playSound(target, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 1.5f);
            if (player.getLocation().distance(target) < 2.5) {
                player.damage(12.0);
                player.setVelocity(new Vector(0, 1.0, 0));
            }
        }, 25L);
    }

    // ============ FASE 2: EL TABLERO DEL DESTINO ============
    private static final int BOARD_SIZE = 6; // 6x6 tablero (cabe en arena de radio 10)
    private int boardOriginX, boardOriginY, boardOriginZ; // Coordenadas enteras del tablero
    private int targetTileX = -1, targetTileZ = -1; // Casilla dorada objetivo

    private void startPhase2() {
        if (!active)
            return;
        currentPhase = 2;
        boardMoves = 0;
        visitedTiles.clear();
        createPhaseBar("§e§l✦ Fase 2/8: El Tablero del Destino ✦", org.bukkit.boss.BarColor.YELLOW);
        player.sendTitle( SmallCaps.convert("§e§l✦ FASE 2 ✦"), SmallCaps.convert("§7El Tablero del Destino"), 10, 60, 20);
        player.sendMessage(
                "§e§l✦ §7Muévete como un §ecaballo de ajedrez§7 en forma de §eL§7. Pisa §e6 casillas doradas§7. §e90s§7!");
        player.sendMessage(SmallCaps.convert("§7§l→ §7Movimiento en L: §e2 casillas en una dirección§7 + §e1 casilla perpendicular§7."));
        player.sendMessage(SmallCaps.convert("§7§l→ §7Ejemplos: avanza 2 al norte y 1 al este, o avanza 1 al sur y 2 al oeste."));
        if (npc != null)
            npc.say("\"Cada paso en el tablero importa.\"");
        Location fc = arena.getFloorCenter();
        if (fc == null) {
            completePhase(2);
            return;
        }

        // Tablero 6x6, cada casilla = 1 bloque. Centrado en el suelo, 1 bloque ENCIMA
        // del piso
        boardOriginX = fc.getBlockX() - BOARD_SIZE / 2;
        boardOriginY = fc.getBlockY(); // Encima del suelo
        boardOriginZ = fc.getBlockZ() - BOARD_SIZE / 2;

        // Construir tablero encima del suelo (no reemplazar el piso)
        World w = fc.getWorld();
        for (int x = 0; x < BOARD_SIZE; x++) {
            for (int z = 0; z < BOARD_SIZE; z++) {
                Material mat = (x + z) % 2 == 0 ? Material.WHITE_CONCRETE : Material.BLACK_CONCRETE;
                Location bl = new Location(w, boardOriginX + x, boardOriginY, boardOriginZ + z);
                bl.getBlock().setType(mat);
                placedBlocks.add(bl.clone());
            }
        }

        // Poner barreras alrededor para que el jugador no caiga
        for (int x = -1; x <= BOARD_SIZE; x++) {
            for (int z = -1; z <= BOARD_SIZE; z++) {
                if (x >= 0 && x < BOARD_SIZE && z >= 0 && z < BOARD_SIZE)
                    continue;
                Location barrier = new Location(w, boardOriginX + x, boardOriginY, boardOriginZ + z);
                barrier.getBlock().setType(Material.BARRIER);
                placedBlocks.add(barrier.clone());
                Location barrier2 = new Location(w, boardOriginX + x, boardOriginY + 1, boardOriginZ + z);
                barrier2.getBlock().setType(Material.BARRIER);
                placedBlocks.add(barrier2.clone());
            }
        }

        knightX = 0;
        knightZ = 0;
        player.teleport(new Location(w, boardOriginX + 0.5, boardOriginY + 1, boardOriginZ + 0.5,
                player.getLocation().getYaw(), player.getLocation().getPitch()));
        setBoardTile(0, 0, Material.LIME_CONCRETE);
        visitedTiles.add("0,0");
        placeBoardTarget();

        currentTask = new BukkitRunnable() {
            int t = 0;

            public void run() {
                if (!active || !player.isOnline()) {
                    cancel();
                    return;
                }
                if (boardMoves >= 6) {
                    cancel();
                    completePhase(2);
                    return;
                }
                if (t > 1800) {
                    cancel();
                    player.sendMessage(SmallCaps.convert("§c§l✖ §cTiempo agotado."));
                    clearBoard();
                    Bukkit.getScheduler().runTaskLater(plugin, () -> startPhase2(), 40L);
                    return;
                }
                if (phaseBar != null)
                    phaseBar.setProgress(Math.max(0, 1.0 - (double) t / 1800));

                // Detectar posición del jugador en el tablero (coordenadas enteras)
                Location pLoc = player.getLocation();
                int px = pLoc.getBlockX() - boardOriginX;
                int pz = pLoc.getBlockZ() - boardOriginZ;

                // Verificar que está dentro del tablero y se movió a otra casilla
                if (px >= 0 && px < BOARD_SIZE && pz >= 0 && pz < BOARD_SIZE
                        && (px != knightX || pz != knightZ)) {
                    int dx = Math.abs(px - knightX);
                    int dz = Math.abs(pz - knightZ);
                    // Movimiento de caballo: L (2+1 o 1+2)
                    if ((dx == 2 && dz == 1) || (dx == 1 && dz == 2)) {
                        setBoardTile(knightX, knightZ, Material.GRAY_CONCRETE);
                        knightX = px;
                        knightZ = pz;
                        visitedTiles.add(px + "," + pz);

                        if (px == targetTileX && pz == targetTileZ) {
                            // Acertó la casilla dorada
                            boardMoves++;
                            player.playSound(pLoc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
                            player.sendTitle("", SmallCaps.convert("§e§l✦ " + boardMoves + "/6"), 0, 25, 5);
                            setBoardTile(px, pz, Material.LIME_CONCRETE);
                            if (boardMoves < 6)
                                placeBoardTarget();
                        } else {
                            setBoardTile(px, pz, Material.LIME_CONCRETE);
                        }
                    }
                }

                // Partículas sobre la casilla dorada
                if (targetTileX >= 0 && t % 5 == 0) {
                    Location targetLoc = new Location(pLoc.getWorld(),
                            boardOriginX + targetTileX + 0.5, boardOriginY + 1.5, boardOriginZ + targetTileZ + 0.5);
                    targetLoc.getWorld().spawnParticle(Particle.END_ROD, targetLoc, 3, 0.2, 0.3, 0.2, 0.02);
                }
                t++;
            }
        };
        currentTask.runTaskTimer(plugin, 0, 1);
    }

    private void setBoardTile(int x, int z, Material mat) {
        Location bl = new Location(player.getWorld(), boardOriginX + x, boardOriginY, boardOriginZ + z);
        bl.getBlock().setType(mat);
    }

    private void placeBoardTarget() {
        List<int[]> valid = new ArrayList<>();
        for (int x = 0; x < BOARD_SIZE; x++)
            for (int z = 0; z < BOARD_SIZE; z++)
                if (!visitedTiles.contains(x + "," + z))
                    valid.add(new int[] { x, z });
        if (valid.isEmpty()) {
            boardMoves = 6;
            return;
        }
        int[] c = valid.get(new Random().nextInt(valid.size()));
        targetTileX = c[0];
        targetTileZ = c[1];
        setBoardTile(c[0], c[1], Material.GOLD_BLOCK);
        player.sendMessage(SmallCaps.convert("§e§l✦ §7Nueva casilla dorada. ¡Muévete en §eL§7! (2+1 o 1+2 bloques)"));
    }

    private void clearBoard() {
        for (Location l : placedBlocks) {
            if (l.getBlock().getType() != Material.AIR)
                l.getBlock().setType(Material.AIR);
        }
        placedBlocks.clear();
        targetTileX = -1;
        targetTileZ = -1;
    }

    // ============ FASE 3: LA SUBASTA DEL VACÍO ============
    private void startPhase3() {
        if (!active)
            return;
        currentPhase = 3;
        auctionTasksDone = 0;
        auctionedItems.clear();
        createPhaseBar("§c§l✦ Fase 3/8: La Subasta del Vacío ✦", org.bukkit.boss.BarColor.RED);
        player.sendTitle( SmallCaps.convert("§c§l✦ FASE 3 ✦"), SmallCaps.convert("§7La Subasta del Vacío"), 10, 60, 20);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);

        Random rand = new Random();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            ItemStack it = player.getInventory().getItem(i);
            if (it != null && it.getType() != Material.AIR)
                slots.add(i);
        }
        Collections.shuffle(slots, rand);
        int toTake = Math.min(3, slots.size());
        for (int i = 0; i < toTake; i++) {
            int slot = slots.get(i);
            ItemStack it = player.getInventory().getItem(slot);
            if (it != null) {
                auctionedItems.add(it.clone());
                player.getInventory().setItem(slot, null);
            }
        }
        if (auctionedItems.isEmpty()) {
            completePhase(3);
            return;
        }
        player.sendMessage(
                "§c§l✦ §7El vacío confiscó §e" + auctionedItems.size() + " §7ítems. ¡Mata oleadas para recuperarlos!");
        if (npc != null)
            npc.say("\"Tus posesiones ya no te pertenecen... gánalas.\"");
        Bukkit.getScheduler().runTaskLater(plugin, this::spawnAuctionWave, 60L);
    }

    private void spawnAuctionWave() {
        if (!active || auctionTasksDone >= auctionedItems.size()) {
            completePhase(3);
            return;
        }
        Location fc = arena.getFloorCenter();
        if (fc == null) {
            completePhase(3);
            return;
        }
        player.sendMessage(SmallCaps.convert("§e§l✦ §7Oleada §e" + (auctionTasksDone + 1) + "§7/§e" + auctionedItems.size()));
        int count = 5 + auctionTasksDone * 3;
        double arenaR = 8.0; // radio seguro dentro de la arena
        for (int i = 0; i < count; i++) {
            double angle = Math.random() * Math.PI * 2;
            double dist = Math.random() * arenaR;
            Location sl = fc.clone().add(Math.cos(angle) * dist, 1, Math.sin(angle) * dist);
            // Asegurar que no spawnea dentro de un bloque sólido
            while (sl.getBlock().getType().isSolid())
                sl.add(0, 1, 0);
            EntityType type = i % 3 == 0 ? EntityType.SKELETON : (i % 3 == 1 ? EntityType.ZOMBIE : EntityType.SPIDER);
            LivingEntity mob = (LivingEntity) fc.getWorld().spawnEntity(sl, type);
            mob.setCustomName(SmallCaps.convert("§8Guardián del Vacío"));
            mob.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(40);
            mob.setHealth(40);
            if (mob instanceof Mob)
                ((Mob) mob).setTarget(player);
            mob.setRemoveWhenFarAway(false);
            // Sin drops
            mob.getEquipment().clear();
            mob.getEquipment().setBootsDropChance(0);
            mob.getEquipment().setLeggingsDropChance(0);
            mob.getEquipment().setChestplateDropChance(0);
            mob.getEquipment().setHelmetDropChance(0);
            mob.getEquipment().setItemInMainHandDropChance(0);
            mob.getEquipment().setItemInOffHandDropChance(0);
            spawnedEntities.add(mob);
        }
        currentTask = new BukkitRunnable() {
            int t = 0;

            public void run() {
                if (!active || !player.isOnline()) {
                    cancel();
                    return;
                }
                if (t > 1200) {
                    cancel();
                    cleanupEntities();
                    player.sendMessage(SmallCaps.convert("§c§l✖ §cLento. Reintento..."));
                    Bukkit.getScheduler().runTaskLater(plugin, () -> spawnAuctionWave(), 40L);
                    return;
                }
                long alive = spawnedEntities.stream().filter(e -> !e.isDead() && e.isValid()).count();
                if (alive == 0) {
                    cancel();
                    if (auctionTasksDone < auctionedItems.size()) {
                        ItemStack ret = auctionedItems.get(auctionTasksDone);
                        player.getInventory().addItem(ret);
                        player.sendMessage(SmallCaps.convert("§a§l✦ §7¡Recuperaste: §f" + ret.getType().name() + "§7!"));
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1.2f);
                        auctionTasksDone++;
                    }
                    spawnedEntities.clear();
                    if (phaseBar != null && auctionedItems.size() > 0)
                        phaseBar.setProgress((double) auctionTasksDone / auctionedItems.size());
                    Bukkit.getScheduler().runTaskLater(plugin, () -> spawnAuctionWave(), 40L);
                }
                t++;
            }
        };
        currentTask.runTaskTimer(plugin, 0, 5);
    }

    // ============ FASE 4: LOS PILARES DE VOLUNTAD ============
    private void startPhase4() {
        if (!active)
            return;
        currentPhase = 4;
        pillarsCompleted = 0;
        createPhaseBar("§d§l✦ Fase 4/8: Los Pilares de Voluntad ✦", org.bukkit.boss.BarColor.PINK);
        player.sendTitle( SmallCaps.convert("§d§l✦ FASE 4 ✦"), SmallCaps.convert("§7Los Pilares de Voluntad"), 10, 60, 20);
        player.sendMessage(SmallCaps.convert("§d§l✦ §74 pilares: §cResistir 15s§7, §aMatar centinela§7, §bInmóvil 10s§7, §eSaltar 15x"));
        if (npc != null)
            npc.say("\"Voluntad, fuerza, paciencia, agilidad.\"");
        Location fc = arena.getFloorCenter();
        if (fc == null) {
            completePhase(4);
            return;
        }
        // Pilares 1 bloque por encima del suelo para no romper el piso de la arena
        Location[] pLocs = { fc.clone().add(5, 1, 5), fc.clone().add(-5, 1, 5), fc.clone().add(5, 1, -5),
                fc.clone().add(-5, 1, -5) };
        Material[] mats = { Material.RED_CONCRETE, Material.LIME_CONCRETE, Material.LIGHT_BLUE_CONCRETE,
                Material.YELLOW_CONCRETE };
        for (int i = 0; i < 4; i++)
            for (int y = 0; y < 3; y++) {
                Location bl = pLocs[i].clone().add(0, y, 0);
                bl.getBlock().setType(mats[i]);
                placedBlocks.add(bl.clone());
            }

        final boolean[] done = new boolean[4];
        final int[] p1t = { 0 }, p3t = { 0 }, p4j = { 0 };
        final boolean[] p2s = { false }, jumpCounted = { false };
        final Location[] last = { player.getLocation().clone() };

        currentTask = new BukkitRunnable() {
            int t = 0;

            public void run() {
                if (!active || !player.isOnline()) {
                    cancel();
                    return;
                }
                if (pillarsCompleted >= 4) {
                    cancel();
                    completePhase(4);
                    return;
                }
                if (t > 3600) {
                    cancel();
                    cleanPillars();
                    Bukkit.getScheduler().runTaskLater(plugin, () -> startPhase4(), 40L);
                    return;
                }
                Location pLoc = player.getLocation();

                // P1: ROJO — Resistir 15s
                if (!done[0]) {
                    if (pLoc.distance(pLocs[0]) < 3.5) {
                        p1t[0]++;
                        if (p1t[0] % 20 == 0)
                            player.damage(4.0);
                        if (p1t[0] >= 300) {
                            done[0] = true;
                            pillarsCompleted++;
                            markPillar(pLocs[0]);
                            player.sendMessage(SmallCaps.convert("§a§l✔ §cRojo §aOK §7(" + pillarsCompleted + "/4)"));
                        }
                    } else
                        p1t[0] = Math.max(0, p1t[0] - 2);
                }
                // P2: VERDE — Matar centinela
                if (!done[1] && pLoc.distance(pLocs[1]) < 3.5 && !p2s[0]) {
                    p2s[0] = true;
                    // Spawnear el centinela a 3 bloques del pilar para evitar sofocación
                    Location sentinelSpawn = pLocs[1].clone().add(3, 0, 0);
                    while (sentinelSpawn.getBlock().getType().isSolid())
                        sentinelSpawn.add(0, 1, 0);
                    Zombie z = pLoc.getWorld().spawn(sentinelSpawn, Zombie.class, m -> {
                        m.setCustomName(SmallCaps.convert("§a§lCentinela"));
                        m.setCustomNameVisible(true);
                        m.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(150);
                        m.setHealth(150);
                        m.setTarget(player);
                        m.setBaby(false);
                        m.getEquipment().setHelmet(new ItemStack(Material.LIME_STAINED_GLASS));
                        m.getEquipment().setHelmetDropChance(0);
                        m.getEquipment().setItemInMainHandDropChance(0);
                        m.getEquipment().setItemInOffHandDropChance(0);
                    });
                    spawnedEntities.add(z);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!done[1] && !z.isDead()) {
                            z.remove();
                            p2s[0] = false;
                        }
                    }, 200L);
                }
                if (!done[1] && p2s[0]) {
                    boolean dead = spawnedEntities.stream()
                            .filter(e -> e.getCustomName() != null && e.getCustomName().contains("Centinela"))
                            .allMatch(Entity::isDead);
                    if (dead) {
                        done[1] = true;
                        pillarsCompleted++;
                        markPillar(pLocs[1]);
                        player.sendMessage(SmallCaps.convert("§a§l✔ §aVerde §aOK §7(" + pillarsCompleted + "/4)"));
                    }
                }
                // P3: AZUL — Inmóvil 10s
                if (!done[2] && pLoc.distance(pLocs[2]) < 3.5) {
                    if (pLoc.distance(last[0]) < 0.15) {
                        p3t[0]++;
                        if (p3t[0] % 40 == 0)
                            player.sendTitle("", SmallCaps.convert("§b" + (10 - p3t[0] / 20) + "s"), 0, 25, 5);
                    } else {
                        if (p3t[0] > 20)
                            player.playSound(pLoc, Sound.ENTITY_VILLAGER_NO, 0.3f, 0.8f);
                        p3t[0] = 0;
                    }
                    if (p3t[0] >= 200) {
                        done[2] = true;
                        pillarsCompleted++;
                        markPillar(pLocs[2]);
                        player.sendMessage(SmallCaps.convert("§a§l✔ §bAzul §aOK §7(" + pillarsCompleted + "/4)"));
                    }
                }
                // P4: DORADO — Saltar 15x
                // Detectar salto: el jugador pasa de estar en el suelo a estar en el aire
                if (!done[3] && pLoc.distance(pLocs[3]) < 4) {
                    boolean onGround = player.isOnGround();
                    if (!onGround && last[0] != null) {
                        // Verifica si en el tick anterior estaba en el suelo (flanco de subida)
                        boolean wasOnGround = last[0].getBlock().getRelative(org.bukkit.block.BlockFace.DOWN).getType()
                                .isSolid()
                                || last[0].subtract(0, 0.1, 0).getBlock().getType().isSolid();
                        if (player.getVelocity().getY() > 0.1) {
                            // Solo contar si el player tiene velocidad hacia arriba (salto real)
                            if (!jumpCounted[0]) {
                                jumpCounted[0] = true;
                                p4j[0]++;
                                player.sendTitle("", SmallCaps.convert("§e" + p4j[0] + "/15"), 0, 15, 5);
                            }
                        } else {
                            jumpCounted[0] = false;
                        }
                    } else if (onGround) {
                        jumpCounted[0] = false;
                    }
                    if (p4j[0] >= 15) {
                        done[3] = true;
                        pillarsCompleted++;
                        markPillar(pLocs[3]);
                        player.sendMessage(SmallCaps.convert("§a§l✔ §eDorado §aOK §7(" + pillarsCompleted + "/4)"));
                    }
                }
                last[0] = pLoc.clone();
                if (t % 10 == 0) {
                    Color[] cs = { Color.RED, Color.LIME, Color.AQUA, Color.YELLOW };
                    for (int i = 0; i < 4; i++)
                        if (!done[i])
                            pLocs[i].getWorld().spawnParticle(Particle.DUST, pLocs[i].clone().add(0, 3.5, 0), 5, 0.3,
                                    0.3, 0.3, 0, new Particle.DustOptions(cs[i], 1.5f));
                }
                t++;
            }
        };
        currentTask.runTaskTimer(plugin, 0, 1);
    }

    private void markPillar(Location l) {
        for (int y = 0; y < 3; y++)
            l.clone().add(0, y, 0).getBlock().setType(Material.EMERALD_BLOCK);
        l.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, l.clone().add(0, 2, 0), 20, 0.5, 0.5, 0.5, 0.2);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
    }

    private void cleanPillars() {
        for (Location l : placedBlocks) {
            Material t = l.getBlock().getType();
            if (t == Material.RED_CONCRETE || t == Material.LIME_CONCRETE || t == Material.LIGHT_BLUE_CONCRETE
                    || t == Material.YELLOW_CONCRETE || t == Material.EMERALD_BLOCK)
                l.getBlock().setType(Material.AIR);
        }
        placedBlocks.clear();
        cleanupEntities();
    }

    // ============ FASE 5: EL LABERINTO DE SOMBRAS ============
    private void startPhase5() {
        if (!active)
            return;
        currentPhase = 5;
        createPhaseBar("§3§l✦ Fase 5/8: Laberinto de Sombras ✦", org.bukkit.boss.BarColor.BLUE);
        player.sendTitle( SmallCaps.convert("§3§l✦ FASE 5 ✦"), SmallCaps.convert("§7Laberinto de Sombras"), 10, 60, 20);
        player.sendMessage(SmallCaps.convert("§3§l✦ §7Encuentra §b5 cristales§7 ocultos en la oscuridad. §e60s§7!"));
        if (npc != null)
            npc.say("\"La oscuridad oculta secretos. Encuéntralos.\"");
        Location fc = arena.getFloorCenter();
        if (fc == null) {
            completePhase(5);
            return;
        }

        // Crear 5 cristales (sea lanterns) en posiciones aleatorias
        List<Location> crystals = new ArrayList<>();
        Random rand = new Random();
        for (int i = 0; i < 5; i++) {
            Location cLoc = fc.clone().add((rand.nextDouble() - 0.5) * 16, rand.nextInt(3) + 1,
                    (rand.nextDouble() - 0.5) * 16);
            cLoc.getBlock().setType(Material.SEA_LANTERN);
            placedBlocks.add(cLoc.clone());
            crystals.add(cLoc.clone());
        }

        final int[] found = { 0 };
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 99999, 0, false, false));

        currentTask = new BukkitRunnable() {
            int t = 0;

            public void run() {
                if (!active || !player.isOnline()) {
                    cancel();
                    return;
                }
                if (found[0] >= 5) {
                    cancel();
                    completePhase(5);
                    return;
                }
                if (t > 1200) {
                    cancel();
                    cleanMaze();
                    Bukkit.getScheduler().runTaskLater(plugin, () -> startPhase5(), 40L);
                    return;
                }
                if (phaseBar != null)
                    phaseBar.setProgress((double) found[0] / 5);

                // Detectar si el jugador está cerca de un cristal
                for (int i = 0; i < crystals.size(); i++) {
                    Location cLoc = crystals.get(i);
                    if (cLoc != null && player.getLocation().distance(cLoc) < 2.5) {
                        crystals.set(i, null);
                        found[0]++;
                        cLoc.getBlock().setType(Material.GLOWSTONE);
                        player.sendTitle("", SmallCaps.convert("§b§l✦ " + found[0] + "/5 Cristales"), 0, 25, 5);
                        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.5f);
                        player.getWorld().spawnParticle(Particle.END_ROD, cLoc.clone().add(0.5, 0.5, 0.5), 30, 0.5, 0.5,
                                0.5, 0.1);
                    }
                }

                // Partículas sutiles cerca de cristales no encontrados
                if (t % 20 == 0) {
                    for (Location cLoc : crystals) {
                        if (cLoc != null) {
                            cLoc.getWorld().spawnParticle(Particle.SOUL, cLoc.clone().add(0.5, 0.5, 0.5), 2, 0.3, 0.3,
                                    0.3, 0.01);
                        }
                    }
                }
                t++;
            }
        };
        currentTask.runTaskTimer(plugin, 0, 1);
    }

    private void cleanMaze() {
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        for (Location l : placedBlocks) {
            Material t = l.getBlock().getType();
            if (t == Material.SEA_LANTERN || t == Material.GLOWSTONE)
                l.getBlock().setType(Material.AIR);
        }
        placedBlocks.clear();
    }

    // ============ FASE 6: TRIBUNAL DE LAS ALMAS ============
    private void startPhase6() {
        if (!active)
            return;
        currentPhase = 6;
        soulsJudged = 0;
        soulsCorrect = 0;
        createPhaseBar("§f§l✦ Fase 6/8: Tribunal de las Almas ✦", org.bukkit.boss.BarColor.WHITE);
        player.sendTitle( SmallCaps.convert("§f§l✦ FASE 6 ✦"), SmallCaps.convert("§7Tribunal de las Almas"), 10, 60, 20);
        player.sendMessage(SmallCaps.convert("§f§l✦ §7Golpea §0negras§7 (corruptas). NO golpees §fblancas§7. 12 almas, acierta 9."));
        if (npc != null)
            npc.say("\"¿Distingues la luz de la oscuridad?\"");
        spawnSoul();
    }

    private void spawnSoul() {
        if (!active || currentPhase != 6 || soulsJudged >= 12) {
            if (active && currentPhase == 6 && soulsJudged >= 12) {
                if (soulsCorrect >= 9)
                    completePhase(6);
                else {
                    player.sendMessage(SmallCaps.convert("§c✖ Solo " + soulsCorrect + "/9. Reintento..."));
                    Bukkit.getScheduler().runTaskLater(plugin, this::startPhase6, 60L);
                }
            }
            return;
        }
        waitingForSoul = false;
        Location fc = arena.getFloorCenter();
        if (fc == null) {
            completePhase(6);
            return;
        }
        boolean corrupt = Math.random() < 0.5;
        Location sl = fc.clone().add((Math.random() - 0.5) * 10, 0.5, (Math.random() - 0.5) * 10);
        ArmorStand soul = sl.getWorld().spawn(sl, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setGravity(false);
            as.setMarker(false);
            as.setCustomNameVisible(true);
            if (corrupt) {
                as.setCustomName(SmallCaps.convert("§0§l☠ Alma Corrupta ☠"));
                as.getEquipment().setHelmet(new ItemStack(Material.WITHER_SKELETON_SKULL));
            } else {
                as.setCustomName(SmallCaps.convert("§f§l✦ Alma Inocente ✦"));
                as.getEquipment().setHelmet(new ItemStack(Material.SKELETON_SKULL));
            }
            as.getPersistentDataContainer().set(new NamespacedKey(plugin, "soul_corrupt"),
                    org.bukkit.persistence.PersistentDataType.BYTE, corrupt ? (byte) 1 : (byte) 0);
        });
        spawnedEntities.add(soul);
        new BukkitRunnable() {
            int t = 0;

            public void run() {
                if (!active || soul.isDead() || t > 160) {
                    cancel();
                    if (!soul.isDead() && !waitingForSoul && currentPhase == 6) {
                        waitingForSoul = true;
                        soul.remove();
                        soulsJudged++;
                        if (corrupt)
                            player.sendMessage(SmallCaps.convert("§c✖ ¡Escapó un alma corrupta!"));
                        else {
                            soulsCorrect++;
                            player.sendMessage(SmallCaps.convert("§a✔ Inocente perdonada."));
                        }
                        player.sendMessage(SmallCaps.convert("§7 " + soulsJudged + "/12 | Aciertos: §a" + soulsCorrect));
                        if (phaseBar != null)
                            phaseBar.setProgress((double) soulsJudged / 12);
                        Bukkit.getScheduler().runTaskLater(plugin, () -> spawnSoul(), 20L);
                    }
                    return;
                }
                if (t % 3 == 0) {
                    Color c = corrupt ? Color.fromRGB(20, 0, 20) : Color.fromRGB(230, 230, 255);
                    soul.getWorld().spawnParticle(Particle.DUST, soul.getLocation().add(0, 1, 0), 3, 0.2, 0.3, 0.2, 0,
                            new Particle.DustOptions(c, 1.5f));
                }
                t++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    public boolean onSoulHit(ArmorStand soul) {
        if (!active || currentPhase != 6 || waitingForSoul)
            return false;
        byte c = soul.getPersistentDataContainer().getOrDefault(new NamespacedKey(plugin, "soul_corrupt"),
                org.bukkit.persistence.PersistentDataType.BYTE, (byte) -1);
        if (c == -1)
            return false;

        waitingForSoul = true;
        soul.remove();
        soulsJudged++;
        if (c == 1) {
            soulsCorrect++;
            player.sendMessage(SmallCaps.convert("§a✔ ¡Corrupta destruida!"));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        } else {
            player.sendMessage(SmallCaps.convert("§c✖ ¡Era inocente!"));
            player.damage(4.0);
        }
        player.sendMessage(SmallCaps.convert("§7 " + soulsJudged + "/12 | Aciertos: §a" + soulsCorrect));
        if (phaseBar != null)
            phaseBar.setProgress((double) soulsJudged / 12);
        Bukkit.getScheduler().runTaskLater(plugin, this::spawnSoul, 20L);
        return true;
    }

    // ============ FASE 7: LA CORRUPCIÓN FINAL ============
    private void startPhase7() {
        if (!active)
            return;
        currentPhase = 7;
        altarsPurified = 0;
        altarStates.clear();
        createPhaseBar("§5§l✦ Fase 7/8: Corrupción Final ✦", org.bukkit.boss.BarColor.PURPLE);
        player.sendTitle( SmallCaps.convert("§5§l✦ FASE 7 ✦"), SmallCaps.convert("§7La Corrupción Final"), 10, 60, 20);
        player.sendMessage(SmallCaps.convert("§5§l✦ §7Purifica §e4 altares §7(quédate cerca 8s). ¡Oleadas te atacarán!"));
        if (npc != null)
            npc.say("\"La corrupción se extiende. Solo tú puedes detenerla.\"");
        Location fc = arena.getFloorCenter();
        if (fc == null) {
            completePhase(7);
            return;
        }
        // Altares 1 bloque por encima del suelo para no romper el piso de la arena
        Location[] als = { fc.clone().add(6, 1, 0), fc.clone().add(-6, 1, 0), fc.clone().add(0, 1, 6),
                fc.clone().add(0, 1, -6) };
        Map<Location, Integer> prog = new HashMap<>();
        for (Location l : als) {
            l.getBlock().setType(Material.CRYING_OBSIDIAN);
            l.clone().add(0, 1, 0).getBlock().setType(Material.SOUL_LANTERN);
            placedBlocks.add(l.clone());
            placedBlocks.add(l.clone().add(0, 1, 0));
            altarStates.put(l.clone(), false);
            prog.put(l.clone(), 0);
        }

        currentTask = new BukkitRunnable() {
            int t = 0;

            public void run() {
                if (!active || !player.isOnline()) {
                    cancel();
                    return;
                }
                if (altarsPurified >= 4) {
                    cancel();
                    completePhase(7);
                    return;
                }
                if (t > 3600) {
                    cancel();
                    cleanCorruption();
                    Bukkit.getScheduler().runTaskLater(plugin, () -> startPhase7(), 40L);
                    return;
                }
                if (phaseBar != null)
                    phaseBar.setProgress((double) altarsPurified / 4);
                for (Map.Entry<Location, Integer> e : prog.entrySet()) {
                    if (altarStates.getOrDefault(e.getKey(), false))
                        continue;
                    if (player.getLocation().distance(e.getKey()) < 3) {
                        e.setValue(e.getValue() + 1);
                        if (e.getValue() % 5 == 0)
                            e.getKey().getWorld().spawnParticle(Particle.DUST, e.getKey().clone().add(0, 1.5, 0), 3,
                                    0.3, 0.3, 0.3, 0, new Particle.DustOptions(Color.fromRGB(180, 0, 255), 1.5f));
                        if (e.getValue() >= 160) {
                            altarStates.put(e.getKey(), true);
                            altarsPurified++;
                            e.getKey().getBlock().setType(Material.DIAMOND_BLOCK);
                            player.sendMessage(SmallCaps.convert("§a✔ Altar purificado (" + altarsPurified + "/4)"));
                            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.5f);
                        }
                    } else
                        e.setValue(Math.max(0, e.getValue() - 1));
                }
                if (t % 100 == 0) {
                    Location spfc = arena.getFloorCenter();
                    if (spfc != null)
                        for (int i = 0; i < 2 + altarsPurified; i++) {
                            double ang = Math.random() * Math.PI * 2;
                            double dist = Math.random() * 8.0;
                            Location sl = spfc.clone().add(Math.cos(ang) * dist, 1, Math.sin(ang) * dist);
                            while (sl.getBlock().getType().isSolid())
                                sl.add(0, 1, 0);
                            Zombie z = sl.getWorld().spawn(sl, Zombie.class, m -> {
                                m.setCustomName(SmallCaps.convert("§5Corrupción"));
                                m.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(30);
                                m.setHealth(30);
                                m.setTarget(player);
                                m.getEquipment().setHelmet(new ItemStack(Material.PURPLE_STAINED_GLASS));
                                m.getEquipment().setHelmetDropChance(0);
                                m.getEquipment().setItemInMainHandDropChance(0);
                                m.getEquipment().setItemInOffHandDropChance(0);
                                m.getEquipment().setBootsDropChance(0);
                                m.getEquipment().setLeggingsDropChance(0);
                                m.getEquipment().setChestplateDropChance(0);
                            });
                            spawnedEntities.add(z);
                        }
                }
                t++;
            }
        };
        currentTask.runTaskTimer(plugin, 0, 1);
    }

    private void cleanCorruption() {
        for (Location l : placedBlocks) {
            Material t = l.getBlock().getType();
            if (t == Material.CRYING_OBSIDIAN || t == Material.SOUL_LANTERN || t == Material.DIAMOND_BLOCK)
                l.getBlock().setType(Material.AIR);
        }
        placedBlocks.clear();
        cleanupEntities();
    }

    // ============ FASE 8: RENACIMIENTO — BOSS FINAL ============
    private void startPhase8() {
        if (!active)
            return;
        currentPhase = 8;
        lastFinalDamage = System.currentTimeMillis();
        createPhaseBar("§4§l✦ Fase 8/8: RENACIMIENTO ✦", org.bukkit.boss.BarColor.RED);
        player.sendTitle( SmallCaps.convert("§4§l✦ FASE FINAL ✦"), SmallCaps.convert("§7El Arquitecto Renace"), 10, 80, 20);
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.3f);
        if (npc != null)
            npc.say("\"¡¡IMPOSIBLE!! ¡¡EL ARQUITECTO RENACE!!\"");
        Location fc = arena.getFloorCenter();
        if (fc == null) {
            onAllPhasesComplete();
            return;
        }
        World w = fc.getWorld();
        w.spawnParticle(Particle.DUST, fc.clone().add(0, 2, 0), 80, 3, 3, 3, 0,
                new Particle.DustOptions(Color.fromRGB(100, 0, 0), 3.0f));
        w.spawnParticle(Particle.SQUID_INK, fc.clone().add(0, 2, 0), 30, 2, 2, 2, 0.1);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active)
                return;
            finalBoss = player.getWorld().spawn(fc.clone().add(3, 1, 0), Zombie.class, z -> {
                z.setCustomName(SmallCaps.convert("§4§l✦ Arquitecto Renacido §4§l✦"));
                z.setCustomNameVisible(true);
                z.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(300);
                z.setHealth(300);
                z.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE).setBaseValue(8);
                z.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED).setBaseValue(0.34);
                z.getAttribute(org.bukkit.attribute.Attribute.ARMOR).setBaseValue(8);
                z.setBaby(false);
                z.setTarget(player);
                z.setRemoveWhenFarAway(false);
                z.setPersistent(true);
                z.getEquipment().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
                z.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
                z.getEquipment().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
                z.getEquipment().setBoots(new ItemStack(Material.NETHERITE_BOOTS));
                z.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
                z.getEquipment().setHelmetDropChance(0);
                z.getEquipment().setChestplateDropChance(0);
                z.getEquipment().setLeggingsDropChance(0);
                z.getEquipment().setBootsDropChance(0);
                z.getEquipment().setItemInMainHandDropChance(0);
                z.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 99999, 0, false, false));
            });
            startFinalLoop();
        }, 80L);
    }

    private void startFinalLoop() {
        currentTask = new BukkitRunnable() {
            int t = 0;

            public void run() {
                if (!active || finalBoss == null || finalBoss.isDead()) {
                    cancel();
                    if (finalBoss != null && finalBoss.isDead())
                        onAllPhasesComplete();
                    return;
                }
                if (!player.isOnline()) {
                    cancel();
                    cleanup();
                    return;
                }
                t++;
                finalBoss.setTarget(player);
                double hp = finalBoss.getHealth() / finalBoss.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getBaseValue();
                if (phaseBar != null)
                    phaseBar.setProgress(Math.max(0, Math.min(1, hp)));
                if (System.currentTimeMillis() - lastFinalDamage > 15000) {
                    finalBoss.setHealth(Math.min(300, finalBoss.getHealth() + 0.8));
                    if (t % 40 == 0) {
                        player.sendTitle("", SmallCaps.convert("§c§l¡Se cura! §7¡ATACA!"), 0, 25, 5);
                        player.playSound(player.getLocation(), Sound.ENTITY_WITCH_DRINK, 0.8f, 1.0f);
                    }
                }
                if (t % 40 == 0)
                    fireProjectile(finalBoss.getLocation().add(0, 2, 0));
                if (t % 240 == 0 && t > 0)
                    shockwave(finalBoss.getLocation());
                if (t % 120 == 0 && t > 60)
                    voidPillar(player.getLocation().clone());
                if (t % 5 == 0) {
                    Location bl = finalBoss.getLocation().add(0, 1.5, 0);
                    bl.getWorld().spawnParticle(Particle.DUST, bl, 5, 0.5, 0.5, 0.5, 0,
                            new Particle.DustOptions(Color.fromRGB(100, 0, 0), 2.5f));
                    bl.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, bl, 2, 0.3, 0.3, 0.3, 0.02);
                }
                if (t % 300 == 0) {
                    String[] taunts = { "§4¡¡NO PUEDES MATARME DOS VECES!!", "§4¡¡SOY ETERNO!!",
                            "§4¡¡EL VACÍO ES MI HOGAR!!" };
                    player.sendTitle(taunts[t / 300 % taunts.length], SmallCaps.convert("§7El Arquitecto Renacido ruge..."), 10, 50, 10);
                }
            }
        };
        currentTask.runTaskTimer(plugin, 0, 1);
    }

    public void onFinalBossDamaged() {
        lastFinalDamage = System.currentTimeMillis();
    }

    // ============ COMPLETAR FASES ============
    private void completePhase(int phase) {
        if (isTransitioning || currentPhase != phase)
            return;
        isTransitioning = true;
        removePhaseBar();
        if (phase == 2)
            clearBoard();
        if (phase == 3) {
            cleanupEntities();
            for (int i = auctionTasksDone; i < auctionedItems.size(); i++)
                player.getInventory().addItem(auctionedItems.get(i));
        }
        if (phase == 4)
            cleanPillars();
        if (phase == 5)
            cleanMaze();
        if (phase == 6)
            cleanupEntities();
        if (phase == 7)
            cleanCorruption();
        if (phase == 8) {
            // Clean up finalBoss if phase 8 is somehow completed early
            if (finalBoss != null && !finalBoss.isDead()) {
                finalBoss.remove();
                finalBoss = null;
            }
        }
        player.sendTitle( SmallCaps.convert("§a§l✔ FASE " + phase + " SUPERADA"), SmallCaps.convert("§7Avanzando..."), 10, 60, 20);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f + phase * 0.05f);
        Runnable next = null;
        switch (phase) {
            case 1:
                next = this::startPhase2;
                break;
            case 2:
                next = this::startPhase3;
                break;
            case 3:
                next = this::startPhase4;
                break;
            case 4:
                next = this::startPhase5;
                break;
            case 5:
                next = this::startPhase6;
                break;
            case 6:
                next = this::startPhase7;
                break;
            case 7:
                if (npc != null)
                    npc.say("\"Queda una última prueba...\"");
                player.sendMessage(SmallCaps.convert("§4§l✦ FASE FINAL: El Arquitecto... renace."));
                next = this::startPhase8;
                break;
        }
        if (next != null) {
            Runnable finalNext = next;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                isTransitioning = false;
                finalNext.run();
            }, 80L);
        } else {
            isTransitioning = false;
        }
    }

    private void onAllPhasesComplete() {
        active = false;
        removePhaseBar();
        // Clean up finalBoss properly
        if (finalBoss != null && !finalBoss.isDead()) {
            finalBoss.remove();
            finalBoss = null;
        }
        cleanupEntities();
        player.sendTitle( SmallCaps.convert("§6§l✦ MISIÓN 3 COMPLETADA ✦"), SmallCaps.convert("§7Has conquistado al Arquitecto del Vacío"), 10, 100, 30);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 0.5f, 1.5f);
        Location loc = player.getLocation();
        loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 2, 0), 120, 2, 2, 2, 0.5);
        loc.getWorld().spawnParticle(Particle.DUST, loc.clone().add(0, 2, 0), 80, 2, 2, 2, 0,
                new Particle.DustOptions(Color.fromRGB(255, 200, 0), 3.0f));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            arena.destroy();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline())
                    manager.onQuest3BossDefeated(player);
            }, 60L);
        }, 60L);
    }

    // ============ UTILIDADES ============
    public boolean isFinalBoss(Entity entity) {
        return finalBoss != null && finalBoss.equals(entity);
    }

    public Entity getFinalBoss() {
        return finalBoss;
    }

    public boolean isBossEntity(UUID entityId) {
        return finalBoss != null && finalBoss.getUniqueId().equals(entityId);
    }

    public boolean isSpawnedEntity(UUID entityId) {
        return spawnedEntities.stream().anyMatch(e -> e != null && !e.isDead() && e.getUniqueId().equals(entityId));
    }

    public void collectProtectedEntities(java.util.List<org.bukkit.entity.Entity> out) {
        if (finalBoss != null && !finalBoss.isDead()) out.add(finalBoss);
        for (org.bukkit.entity.Entity e : spawnedEntities) { if (e != null && !e.isDead()) out.add(e); }
    }

    public boolean isActive() {
        return active;
    }

    public int getCurrentPhase() {
        return currentPhase;
    }

    public VoidArena getArena() {
        return arena;
    }

    public void cleanup() {
        active = false;
        removePhaseBar();
        if (currentTask != null)
            try {
                currentTask.cancel();
            } catch (Exception ignored) {
            }
        cleanupEntities();
        if (finalBoss != null && !finalBoss.isDead())
            finalBoss.remove();
        player.removePotionEffect(PotionEffectType.LEVITATION);
        player.removePotionEffect(PotionEffectType.SLOW_FALLING);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        // Limpiar bloques colocados por las fases SIN tocar el piso de la arena
        // (BLACK_CONCRETE)
        for (Location l : placedBlocks) {
            Material t = l.getBlock().getType();
            if (t != Material.AIR && t != Material.BLACK_CONCRETE)
                l.getBlock().setType(Material.AIR);
        }
        placedBlocks.clear();
        
        // Destruir arena (expansión de dominio)
        if (arena != null && arena.isActive()) {
            arena.destroy();
        }
        
        // Devolver items confiscados
        for (int i = auctionTasksDone; i < auctionedItems.size(); i++) {
            if (player.isOnline())
                player.getInventory().addItem(auctionedItems.get(i));
        }
    }
}
