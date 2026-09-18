package com.moonlight.coreprotect.minigames.games;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.minigames.*;
import com.moonlight.coreprotect.util.ActionBarUtil;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 🎭 FAKE DEATHMATCH
 *
 * Empieza como un PvP Deathmatch normal... pero la realidad se rompe.
 * Cada cierto tiempo, eventos caóticos cambian las reglas del juego:
 *
 * EVENTOS:
 * - GRAVEDAD INVERTIDA: Todos flotan hacia arriba durante unos segundos
 * - NIEBLA OSCURA: Blindness + partículas, no ves nada
 * - MUTACIÓN DEL MAPA: Bloques del suelo cambian, aparecen paredes/trampas
 * - RESURRECCIÓN: Un jugador eliminado vuelve a la vida
 * - SWAP DE INVENTARIOS: Los inventarios se intercambian aleatoriamente entre jugadores
 * - VELOCIDAD EXTREMA: Todos corren x3
 * - CÁMARA LENTA: Slowness extrema
 * - CUERPOS INTERCAMBIADOS: Se teletransportan entre sí (swap posiciones)
 * - LLUVIA DE TNT: TNT cae del cielo
 * - TAMAÑO ALTERADO: Salto extremo + lentitud (simula ser gigante)
 * - SUELO DE LAVA: El suelo se convierte en magma temporalmente
 * - ARMADURA ALEATORIA: Todos reciben armadura random (puede ser buena o basura)
 * - TODOS INVISIBLES: Invisibilidad total 10s
 * - BOXEO: Se quitan las armas, solo puños durante 15s
 *
 * El juego se siente cada vez más caótico e impredecible.
 * Último en pie gana.
 */
public class FakeDeathmatchGame extends MiniGame {

    // === ARENA ===
    private static final int ARENA_RADIUS = 80;
    private static final int ARENA_Y = 80;
    private static final int VOID_Y = 60;

    // === EVENTS ===
    private static final int FIRST_EVENT_AT = 7;
    private static final int EVENT_INTERVAL_MIN = 7;
    private static final int EVENT_INTERVAL_MAX = 7;

    // === STORM ===
    private static final int STORM_START = 90;
    private static final double STORM_INITIAL_RADIUS = 90.0;
    private static final double STORM_MIN_RADIUS = 5.0;
    private static final int STORM_SHRINK_DURATION = 180;
    private static final double STORM_DAMAGE = 4.0;

    // === STATE ===
    private BossBar statusBar;
    private final Random random = new Random();
    private boolean gameStarted = false;
    private boolean stormActive = false;
    private double stormRadius = STORM_INITIAL_RADIUS;
    private int nextEventAt;
    private int eventCount = 0;
    private String lastEventName = "§aPvP Normal";
    private int stormBorderTaskId = -1;
    private int fogTaskId = -1;

    // Para resurrección
    private final List<UUID> eliminatedPlayers = new ArrayList<>();
    // Para tracking de inventarios guardados (boxeo)
    private final Map<UUID, ItemStack[]> savedInventories = new HashMap<>();
    private final Map<UUID, ItemStack[]> savedArmor = new HashMap<>();

    // Event types
    private enum ChaosEvent {
        INVERTED_GRAVITY, DARK_FOG, MAP_MUTATION, RESURRECTION, INVENTORY_SWAP,
        EXTREME_SPEED, SLOW_MOTION, BODY_SWAP, TNT_RAIN, GIANT_MODE,
        LAVA_FLOOR, RANDOM_ARMOR, INVISIBILITY, BOXING_MATCH, RANDOM_EFFECTS,
        ANVIL_STORM, LIGHTNING_STRIKE, ZERO_GRAVITY, NAUSEA_CHAOS, FLOOR_DISAPPEAR,
        LAUNCH_ALL, FREEZE_ALL, RANDOM_TELEPORT, BOUNCY_FLOOR, WITHER_STORM,
        FIREBALL_RAIN, COBWEB_BOMB, HUNGER_GAMES, REVERSE_HEALTH, CHICKEN_MODE
    }

    public FakeDeathmatchGame(CoreProtectPlugin plugin, MiniGameManager manager) {
        super(plugin, manager, MiniGameType.FAKE_DEATHMATCH);
    }

    // ═══════════════════════════════════════════════════════════
    //  ARENA: Coliseo romano con plataformas multinivel
    // ═══════════════════════════════════════════════════════════

    @Override
    public void buildArena(World world) {
        int cx = 0, cz = 0;

        // === SUELO PRINCIPAL: Arena circular con patrón de gladiador ===
        for (int x = -ARENA_RADIUS; x <= ARENA_RADIUS; x++) {
            for (int z = -ARENA_RADIUS; z <= ARENA_RADIUS; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > ARENA_RADIUS) continue;

                // Patrón radial en el suelo
                Material floor;
                if (dist < 5) {
                    floor = Material.GOLD_BLOCK; // Centro dorado
                } else if ((int) dist % 6 < 3) {
                    floor = Material.POLISHED_DEEPSLATE;
                } else {
                    floor = Material.DEEPSLATE_BRICKS;
                }

                // Capa de suelo
                world.getBlockAt(cx + x, ARENA_Y, cz + z).setType(floor);
                // Capas bajo el suelo
                for (int y = ARENA_Y - 1; y >= ARENA_Y - 4; y--) {
                    world.getBlockAt(cx + x, y, cz + z).setType(Material.DEEPSLATE);
                }
            }
        }

        // === MUROS EXTERIORES (Coliseo) ===
        for (int angle = 0; angle < 360; angle++) {
            double rad = Math.toRadians(angle);
            int wx = cx + (int) (ARENA_RADIUS * Math.cos(rad));
            int wz = cz + (int) (ARENA_RADIUS * Math.sin(rad));
            for (int y = ARENA_Y + 1; y <= ARENA_Y + 6; y++) {
                Material wallMat;
                if (y == ARENA_Y + 6) {
                    wallMat = Material.POLISHED_BLACKSTONE_BRICK_WALL;
                } else if (y >= ARENA_Y + 4) {
                    wallMat = Material.CRACKED_POLISHED_BLACKSTONE_BRICKS;
                } else {
                    wallMat = Material.POLISHED_BLACKSTONE_BRICKS;
                }
                world.getBlockAt(wx, y, wz).setType(wallMat);
            }
            // Almenas cada 10 grados
            if (angle % 10 == 0) {
                world.getBlockAt(wx, ARENA_Y + 7, wz).setType(Material.POLISHED_BLACKSTONE_BRICK_WALL);
            }
        }

        // === PILARES INTERIORES (cobertura + estética) ===
        for (int i = 0; i < 8; i++) {
            double angle = (2 * Math.PI / 8) * i;
            int px = cx + (int) (50 * Math.cos(angle));
            int pz = cz + (int) (50 * Math.sin(angle));
            for (int y = ARENA_Y + 1; y <= ARENA_Y + 5; y++) {
                world.getBlockAt(px, y, pz).setType(Material.BLACKSTONE_WALL);
            }
            // Techo parcial sobre los pilares
            world.getBlockAt(px, ARENA_Y + 5, pz).setType(Material.POLISHED_BLACKSTONE_BRICK_SLAB);
            world.getBlockAt(px + 1, ARENA_Y + 5, pz).setType(Material.POLISHED_BLACKSTONE_BRICK_SLAB);
            world.getBlockAt(px - 1, ARENA_Y + 5, pz).setType(Material.POLISHED_BLACKSTONE_BRICK_SLAB);
            world.getBlockAt(px, ARENA_Y + 5, pz + 1).setType(Material.POLISHED_BLACKSTONE_BRICK_SLAB);
            world.getBlockAt(px, ARENA_Y + 5, pz - 1).setType(Material.POLISHED_BLACKSTONE_BRICK_SLAB);
        }

        // === PLATAFORMAS ELEVADAS (8 a distintas alturas) ===
        for (int i = 0; i < 8; i++) {
            double angle = (2 * Math.PI / 8) * i + Math.PI / 8;
            int px = cx + (int) (35 * Math.cos(angle));
            int pz = cz + (int) (35 * Math.sin(angle));
            int height = ARENA_Y + 3 + (i % 2);
            // Plataforma 3x3
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    world.getBlockAt(px + dx, height, pz + dz).setType(Material.DARK_OAK_SLAB);
                }
            }
            // Escalera de acceso
            world.getBlockAt(px + 2, ARENA_Y + 1, pz).setType(Material.DARK_OAK_STAIRS);
            world.getBlockAt(px + 2, ARENA_Y + 2, pz).setType(Material.DARK_OAK_STAIRS);
        }

        // === CENTRO: Plataforma elevada con fuego decorativo ===
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) + Math.abs(dz) <= 3) {
                    world.getBlockAt(cx + dx, ARENA_Y + 2, cz + dz).setType(Material.GILDED_BLACKSTONE);
                }
            }
        }
        // Antorchas de alma en esquinas
        world.getBlockAt(cx + 2, ARENA_Y + 3, cz + 2).setType(Material.SOUL_LANTERN);
        world.getBlockAt(cx - 2, ARENA_Y + 3, cz + 2).setType(Material.SOUL_LANTERN);
        world.getBlockAt(cx + 2, ARENA_Y + 3, cz - 2).setType(Material.SOUL_LANTERN);
        world.getBlockAt(cx - 2, ARENA_Y + 3, cz - 2).setType(Material.SOUL_LANTERN);

        // === ZONAS DE COBERTURA BAJA (muros parciales) ===
        for (int i = 0; i < 12; i++) {
            double angle = (2 * Math.PI / 12) * i + Math.PI / 6;
            int bx = cx + (int) (65 * Math.cos(angle));
            int bz = cz + (int) (65 * Math.sin(angle));
            // Muro bajo 3 bloques
            for (int dy = 1; dy <= 2; dy++) {
                world.getBlockAt(bx, ARENA_Y + dy, bz).setType(Material.STONE_BRICK_WALL);
                world.getBlockAt(bx + 1, ARENA_Y + dy, bz).setType(Material.STONE_BRICK_WALL);
                world.getBlockAt(bx - 1, ARENA_Y + dy, bz).setType(Material.STONE_BRICK_WALL);
            }
        }

        // === DECORACIÓN: Cadenas colgantes del techo invisible ===
        for (int i = 0; i < 20; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 10 + random.nextDouble() * 60;
            int chainX = cx + (int) (dist * Math.cos(angle));
            int chainZ = cz + (int) (dist * Math.sin(angle));
            int chainLen = 2 + random.nextInt(3);
            for (int y = 0; y < chainLen; y++) {
                world.getBlockAt(chainX, ARENA_Y + 8 + y, chainZ).setType(Material.IRON_BARS);
            }
        }

        // === REDSTONE LAMPS en los muros (decoración) ===
        for (int i = 0; i < 32; i++) {
            double angle = (2 * Math.PI / 32) * i;
            int lx = cx + (int) ((ARENA_RADIUS - 1) * Math.cos(angle));
            int lz = cz + (int) ((ARENA_RADIUS - 1) * Math.sin(angle));
            world.getBlockAt(lx, ARENA_Y + 3, lz).setType(Material.SOUL_LANTERN);
        }
    }

    // ═══════════════════════════════════════════
    //  SPAWN LOCATIONS
    // ═══════════════════════════════════════════

    @Override
    public List<Location> getSpawnLocations(World world) {
        List<Location> spawns = new ArrayList<>();
        int maxPlayers = 16;
        double spawnRadius = 60;

        for (int i = 0; i < maxPlayers; i++) {
            double angle = (2 * Math.PI / maxPlayers) * i;
            int sx = (int) (spawnRadius * Math.cos(angle));
            int sz = (int) (spawnRadius * Math.sin(angle));
            Location spawn = new Location(world, sx + 0.5, ARENA_Y + 1, sz + 0.5);
            spawn.setYaw((float) Math.toDegrees(-angle + Math.PI));
            spawns.add(spawn);
        }
        return spawns;
    }

    // ═══════════════════════════════════════════
    //  PRE-COUNTDOWN
    // ═══════════════════════════════════════════

    @Override
    protected void onPreCountdown() {
        // Nada especial
    }

    // ═══════════════════════════════════════════
    //  GAME START
    // ═══════════════════════════════════════════

    @Override
    public void startGameLogic() {
        gameStarted = true;
        nextEventAt = FIRST_EVENT_AT;

        statusBar = Bukkit.createBossBar("§c§l⚔ DEATHMATCH §c§l⚔", BarColor.RED, BarStyle.SOLID);
        statusBar.setProgress(1.0);
        statusBar.setVisible(true);
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) statusBar.addPlayer(p);
        }

        broadcastGame("");
        broadcastGame("§c§l⚔ §4§lDEATHMATCH §c§l⚔");
        broadcastGame("§7PvP libre. Último en pie gana.");
        broadcastGame("§7Que empiece la masacre...");
        broadcastGame("");

        // Kit inicial de PvP
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            p.getInventory().clear();
            giveStarterKit(p);
        }
    }

    private void giveStarterKit(Player p) {
        p.getInventory().setItem(0, new ItemStack(Material.IRON_SWORD));
        p.getInventory().setItem(1, new ItemStack(Material.BOW));
        p.getInventory().setItem(2, new ItemStack(Material.ARROW, 16));
        p.getInventory().setItem(3, new ItemStack(Material.GOLDEN_APPLE, 3));
        p.getInventory().setItem(4, new ItemStack(Material.COBBLESTONE, 16));
        p.getInventory().setItem(5, new ItemStack(Material.SHIELD));

        p.getInventory().setHelmet(new ItemStack(Material.IRON_HELMET));
        p.getInventory().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        p.getInventory().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
        p.getInventory().setBoots(new ItemStack(Material.IRON_BOOTS));
    }

    // ═══════════════════════════════════════════
    //  TICK (cada segundo)
    // ═══════════════════════════════════════════

    @Override
    public void onTick() {
        if (!gameStarted) return;

        // === Verificar caídas al vacío ===
        for (UUID uuid : new ArrayList<>(alivePlayers)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;
            if (p.getLocation().getY() < VOID_Y) {
                eliminatePlayer(uuid);
            }
        }

        // === EVENTO CAÓTICO ===
        if (gameTime >= nextEventAt && alivePlayers.size() > 1) {
            triggerRandomEvent();
            // Próximo evento: intervalo aleatorio decreciente con el tiempo
            nextEventAt = gameTime + 7;
        }

        // === TORMENTA ===
        if (gameTime == STORM_START) {
            stormActive = true;
            stormRadius = STORM_INITIAL_RADIUS;
            startStormBorder();
        }

        if (stormActive) {
            int stormTime = gameTime - STORM_START;
            double shrinkFraction = Math.min(1.0, (double) stormTime / STORM_SHRINK_DURATION);
            stormRadius = STORM_INITIAL_RADIUS - (STORM_INITIAL_RADIUS - STORM_MIN_RADIUS) * shrinkFraction;

            for (UUID uuid : new ArrayList<>(alivePlayers)) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline()) continue;
                double dist = horizontalDist(p.getLocation());
                if (dist > stormRadius) {
                    p.damage(STORM_DAMAGE);
                    p.playSound(p.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 0.5f);
                }
            }
        }

        // === BOSSBAR ===
        updateBossBar();

        // === ACTIONBAR ===
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            int nextIn = nextEventAt - gameTime;
            if (stormActive) {
                double dist = horizontalDist(p.getLocation());
                String color = dist > stormRadius ? "§c" : "§a";
                ActionBarUtil.send(p, "§4§l⚔ " + lastEventName + " §8| " + color + "Dist: " + String.format("%.0f", dist)
                        + " §8| §fVivos: §a" + alivePlayers.size());
            } else {
                ActionBarUtil.send(p, "§4§l⚔ " + lastEventName + " §8| §eEvento en §f" + nextIn + "s §8| §fVivos: §a" + alivePlayers.size());
            }
        }

        // === Info periódica ===
        if (gameTime % 30 == 0 && gameTime > 0) {
            broadcastGame("§c§l⚔ §fTiempo: §e" + formatTime(gameTime) + " §8| §fVivos: §a" + alivePlayers.size()
                    + " §8| §fEventos: §d" + eventCount);
        }
    }

    // ═══════════════════════════════════════════
    //  CHAOS EVENTS
    // ═══════════════════════════════════════════

    private void triggerRandomEvent() {
        eventCount++;
        ChaosEvent[] events = ChaosEvent.values();

        // Filtrar eventos según contexto
        List<ChaosEvent> available = new ArrayList<>(Arrays.asList(events));
        if (eliminatedPlayers.isEmpty()) available.remove(ChaosEvent.RESURRECTION);
        if (alivePlayers.size() < 3) available.remove(ChaosEvent.BODY_SWAP);
        if (alivePlayers.size() < 3) available.remove(ChaosEvent.INVENTORY_SWAP);

        ChaosEvent event = available.get(random.nextInt(available.size()));
        executeChaosEvent(event);
    }

    private void executeChaosEvent(ChaosEvent event) {
        switch (event) {
            case INVERTED_GRAVITY -> eventInvertedGravity();
            case DARK_FOG -> eventDarkFog();
            case MAP_MUTATION -> eventMapMutation();
            case RESURRECTION -> eventResurrection();
            case INVENTORY_SWAP -> eventInventorySwap();
            case EXTREME_SPEED -> eventExtremeSpeed();
            case SLOW_MOTION -> eventSlowMotion();
            case BODY_SWAP -> eventBodySwap();
            case TNT_RAIN -> eventTntRain();
            case GIANT_MODE -> eventGiantMode();
            case LAVA_FLOOR -> eventLavaFloor();
            case RANDOM_ARMOR -> eventRandomArmor();
            case INVISIBILITY -> eventInvisibility();
            case BOXING_MATCH -> eventBoxingMatch();
            case RANDOM_EFFECTS -> eventRandomEffects();
            case ANVIL_STORM -> eventAnvilStorm();
            case LIGHTNING_STRIKE -> eventLightningStrike();
            case ZERO_GRAVITY -> eventZeroGravity();
            case NAUSEA_CHAOS -> eventNauseaChaos();
            case FLOOR_DISAPPEAR -> eventFloorDisappear();
            case LAUNCH_ALL -> eventLaunchAll();
            case FREEZE_ALL -> eventFreezeAll();
            case RANDOM_TELEPORT -> eventRandomTeleport();
            case BOUNCY_FLOOR -> eventBouncyFloor();
            case WITHER_STORM -> eventWitherStorm();
            case FIREBALL_RAIN -> eventFireballRain();
            case COBWEB_BOMB -> eventCobwebBomb();
            case HUNGER_GAMES -> eventHungerGames();
            case REVERSE_HEALTH -> eventReverseHealth();
            case CHICKEN_MODE -> eventChickenMode();
        }
    }

    // --- GRAVEDAD INVERTIDA ---
    private void eventInvertedGravity() {
        lastEventName = "§5Gravedad Invertida";
        announceEvent("§5§l⬆ GRAVEDAD INVERTIDA", "§d¡Todos flotan durante 5 segundos!");
        soundAll(Sound.ENTITY_SHULKER_BULLET_HIT, 1.0f, 0.5f);

        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            p.setVelocity(p.getVelocity().add(new Vector(0, 1.5, 0)));
            p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 100, 2, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 160, 0, false, true));
        }
    }

    // --- NIEBLA OSCURA ---
    private void eventDarkFog() {
        lastEventName = "§8Niebla Oscura";
        announceEvent("§8§l🌫 NIEBLA OSCURA", "§7¡No puedes ver nada durante 8 segundos!");
        soundAll(Sound.ENTITY_PHANTOM_AMBIENT, 1.0f, 0.3f);

        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 160, 0, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 160, 0, false, false));
        }

        // Partículas de niebla
        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (world == null) return;
        fogTaskId = new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!gameStarted || ticks > 160) { cancel(); return; }
                ticks += 5;
                for (int i = 0; i < 50; i++) {
                    double x = (random.nextDouble() - 0.5) * ARENA_RADIUS * 2;
                    double z = (random.nextDouble() - 0.5) * ARENA_RADIUS * 2;
                    double y = ARENA_Y + 1 + random.nextDouble() * 4;
                    world.spawnParticle(Particle.LARGE_SMOKE, x, y, z, 1, 0.5, 0.2, 0.5, 0);
                }
            }
        }.runTaskTimer(plugin, 0L, 5L).getTaskId();
    }

    // --- MUTACIÓN DEL MAPA ---
    private void eventMapMutation() {
        lastEventName = "§6Mutación del Mapa";
        announceEvent("§6§l🔄 MUTACIÓN DEL MAPA", "§e¡El mapa cambia bajo tus pies!");
        soundAll(Sound.ENTITY_WARDEN_EMERGE, 0.8f, 1.5f);

        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (world == null) return;

        int mutation = random.nextInt(6);
        switch (mutation) {
            case 0 -> {
                // Paredes aleatorias surgen del suelo
                for (int i = 0; i < 20; i++) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    double dist = 8 + random.nextDouble() * 65;
                    int bx = (int) (dist * Math.cos(angle));
                    int bz = (int) (dist * Math.sin(angle));
                    for (int dy = 1; dy <= 4; dy++) {
                        world.getBlockAt(bx, ARENA_Y + dy, bz).setType(Material.DEEPSLATE_BRICKS);
                        world.getBlockAt(bx + 1, ARENA_Y + dy, bz).setType(Material.DEEPSLATE_BRICKS);
                        world.getBlockAt(bx - 1, ARENA_Y + dy, bz).setType(Material.DEEPSLATE_BRICKS);
                    }
                }
                broadcastGame("§6  §7→ Paredes surgieron del suelo.");
            }
            case 1 -> {
                // Agujeros en el suelo (todas las capas!)
                for (int i = 0; i < 15; i++) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    double dist = 8 + random.nextDouble() * 65;
                    int hx = (int) (dist * Math.cos(angle));
                    int hz = (int) (dist * Math.sin(angle));
                    int radius = 1 + random.nextInt(2);
                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            // Eliminar TODAS las capas (superficie + deepslate + dirt)
                            for (int dy = -4; dy <= 0; dy++) {
                                world.getBlockAt(hx + dx, ARENA_Y + dy, hz + dz).setType(Material.AIR);
                            }
                        }
                    }
                }
                broadcastGame("§6  §7→ ¡Cuidado! Agujeros al vacío.");
            }
            case 2 -> {
                // Plataformas flotantes
                for (int i = 0; i < 12; i++) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    double dist = 10 + random.nextDouble() * 60;
                    int px = (int) (dist * Math.cos(angle));
                    int pz = (int) (dist * Math.sin(angle));
                    int py = ARENA_Y + 4 + random.nextInt(4);
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            world.getBlockAt(px + dx, py, pz + dz).setType(Material.AMETHYST_BLOCK);
                        }
                    }
                }
                broadcastGame("§6  §7→ Plataformas flotantes aparecieron.");
            }
            case 3 -> {
                // Telarañas por todas partes
                for (int i = 0; i < 50; i++) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    double dist = 5 + random.nextDouble() * 70;
                    int wx = (int) (dist * Math.cos(angle));
                    int wz = (int) (dist * Math.sin(angle));
                    int wy = ARENA_Y + 1 + random.nextInt(2);
                    if (world.getBlockAt(wx, wy, wz).getType() == Material.AIR) {
                        world.getBlockAt(wx, wy, wz).setType(Material.COBWEB);
                    }
                }
                broadcastGame("§6  §7→ ¡Telarañas por todas partes!");
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    for (int x = -ARENA_RADIUS; x <= ARENA_RADIUS; x++) {
                        for (int z = -ARENA_RADIUS; z <= ARENA_RADIUS; z++) {
                            for (int y = ARENA_Y + 1; y <= ARENA_Y + 4; y++) {
                                if (world.getBlockAt(x, y, z).getType() == Material.COBWEB) {
                                    world.getBlockAt(x, y, z).setType(Material.AIR);
                                }
                            }
                        }
                    }
                }, 240L);
            }
            case 4 -> {
                // CAMBIO TOTAL DE SUELO: las 3 capas cambian de material
                Material[] surfaces = {Material.SAND, Material.RED_SAND, Material.ICE, Material.SOUL_SAND,
                        Material.HONEY_BLOCK, Material.SLIME_BLOCK, Material.PRISMARINE, Material.NETHER_BRICKS};
                Material newSurface = surfaces[random.nextInt(surfaces.length)];
                Material[] underMats = {Material.NETHERRACK, Material.END_STONE, Material.PACKED_ICE, Material.TERRACOTTA};
                Material newUnder = underMats[random.nextInt(underMats.length)];

                for (int x = -ARENA_RADIUS; x <= ARENA_RADIUS; x += 2) {
                    for (int z = -ARENA_RADIUS; z <= ARENA_RADIUS; z += 2) {
                        double d = Math.sqrt(x * x + z * z);
                        if (d > ARENA_RADIUS) continue;
                        // Cambiar superficie
                        if (world.getBlockAt(x, ARENA_Y, z).getType() != Material.AIR) {
                            world.getBlockAt(x, ARENA_Y, z).setType(newSurface);
                        }
                        // Cambiar capas bajo superficie
                        for (int dy = -1; dy >= -4; dy--) {
                            if (world.getBlockAt(x, ARENA_Y + dy, z).getType() != Material.AIR) {
                                world.getBlockAt(x, ARENA_Y + dy, z).setType(newUnder);
                            }
                        }
                    }
                }
                broadcastGame("§6  §7→ ¡El suelo entero ha mutado a §e" + newSurface.name() + "§7!");
            }
            case 5 -> {
                // Columnas de bloques aleatorios surgen por todo el mapa
                Material[] mats = {Material.OBSIDIAN, Material.GLOWSTONE, Material.CRYING_OBSIDIAN,
                        Material.COPPER_BLOCK, Material.AMETHYST_BLOCK, Material.PURPUR_BLOCK};
                for (int i = 0; i < 30; i++) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    double dist = 5 + random.nextDouble() * 70;
                    int cx = (int) (dist * Math.cos(angle));
                    int cz = (int) (dist * Math.sin(angle));
                    int colHeight = 3 + random.nextInt(6);
                    Material mat = mats[random.nextInt(mats.length)];
                    for (int dy = 1; dy <= colHeight; dy++) {
                        world.getBlockAt(cx, ARENA_Y + dy, cz).setType(mat);
                    }
                }
                broadcastGame("§6  §7→ ¡Columnas gigantes brotaron del suelo!");
            }
        }
    }

    // --- RESURRECCIÓN ---
    private void eventResurrection() {
        if (eliminatedPlayers.isEmpty()) {
            eventRandomEffects(); // Fallback
            return;
        }

        // Revivir al último eliminado
        UUID revived = eliminatedPlayers.remove(eliminatedPlayers.size() - 1);
        Player p = Bukkit.getPlayer(revived);
        if (p == null || !p.isOnline()) {
            eventRandomEffects();
            return;
        }

        lastEventName = "§a¡Resurrección!";
        announceEvent("§a§l💀→❤ RESURRECCIÓN", "§a¡" + p.getName() + " ha vuelto de la muerte!");
        soundAll(Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);

        // Revivir
        alivePlayers.add(revived);
        spectators.remove(revived);
        p.setGameMode(GameMode.SURVIVAL);
        p.setHealth(p.getMaxHealth());
        p.setFoodLevel(20);
        p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));

        // Teleportar a posición aleatoria de la arena
        double angle = random.nextDouble() * Math.PI * 2;
        double dist = 10 + random.nextDouble() * 15;
        Location respawnLoc = new Location(Bukkit.getWorld(MiniGameWorld.getWorldName()),
                dist * Math.cos(angle), ARENA_Y + 1, dist * Math.sin(angle));
        p.teleport(respawnLoc);

        // Dar kit reducido
        p.getInventory().clear();
        p.getInventory().setItem(0, new ItemStack(Material.STONE_SWORD));
        p.getInventory().setItem(1, new ItemStack(Material.GOLDEN_APPLE, 2));
        p.getInventory().setItem(2, new ItemStack(Material.COBBLESTONE, 8));
        p.getInventory().setHelmet(new ItemStack(Material.CHAINMAIL_HELMET));
        p.getInventory().setChestplate(new ItemStack(Material.CHAINMAIL_CHESTPLATE));

        // Invulnerabilidad 3 segundos
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 4, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0, false, true));

        p.sendTitle("§a§l¡REVIVIDO!", "§7Tienes 3s de invulnerabilidad", 5, 40, 10);
    }

    // --- INTERCAMBIO DE INVENTARIOS ---
    private void eventInventorySwap() {
        lastEventName = "§d¡Swap Inventarios!";
        announceEvent("§d§l🔀 SWAP DE INVENTARIOS", "§d¡Tu inventario ahora es de otro jugador!");
        soundAll(Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.5f);

        List<UUID> alive = new ArrayList<>(alivePlayers);
        if (alive.size() < 2) return;

        // Guardar todos los inventarios
        Map<UUID, ItemStack[]> contents = new LinkedHashMap<>();
        Map<UUID, ItemStack[]> armors = new LinkedHashMap<>();
        for (UUID uuid : alive) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            contents.put(uuid, p.getInventory().getContents().clone());
            armors.put(uuid, p.getInventory().getArmorContents().clone());
        }

        // Rotar inventarios
        List<UUID> keys = new ArrayList<>(contents.keySet());
        Collections.shuffle(keys, random);
        for (int i = 0; i < keys.size(); i++) {
            UUID receiver = keys.get(i);
            UUID donor = keys.get((i + 1) % keys.size());
            Player p = Bukkit.getPlayer(receiver);
            if (p == null) continue;
            p.getInventory().setContents(contents.get(donor));
            p.getInventory().setArmorContents(armors.get(donor));
            p.sendMessage("§d§l🔀 §7Tienes el inventario de §e" + (Bukkit.getPlayer(donor) != null ? Bukkit.getPlayer(donor).getName() : "???"));
        }
    }

    // --- VELOCIDAD EXTREMA ---
    private void eventExtremeSpeed() {
        lastEventName = "§b¡Velocidad Extrema!";
        announceEvent("§b§l⚡ VELOCIDAD EXTREMA", "§b¡Todos corren x3 durante 10 segundos!");
        soundAll(Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 2.0f);

        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 4, false, true));
        }
    }

    // --- CÁMARA LENTA ---
    private void eventSlowMotion() {
        lastEventName = "§7Cámara Lenta";
        announceEvent("§7§l🐌 CÁMARA LENTA", "§7Todo va lento durante 8 segundos...");
        soundAll(Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.1f);

        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 160, 3, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 160, 2, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 160, 128, false, true)); // Cant jump
        }
    }

    // --- INTERCAMBIO DE CUERPOS (teleport swap) ---
    private void eventBodySwap() {
        lastEventName = "§e¡Cuerpos Intercambiados!";
        announceEvent("§e§l🔄 CUERPOS INTERCAMBIADOS", "§e¡Te han teletransportado a la posición de otro!");
        soundAll(Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);

        List<UUID> alive = new ArrayList<>(alivePlayers);
        if (alive.size() < 2) return;

        Collections.shuffle(alive, random);
        // Swap en parejas
        for (int i = 0; i < alive.size() - 1; i += 2) {
            Player p1 = Bukkit.getPlayer(alive.get(i));
            Player p2 = Bukkit.getPlayer(alive.get(i + 1));
            if (p1 == null || p2 == null) continue;

            Location loc1 = p1.getLocation().clone();
            Location loc2 = p2.getLocation().clone();
            p1.teleport(loc2);
            p2.teleport(loc1);

            p1.sendMessage("§e§l🔄 §7Ahora estás donde estaba §f" + p2.getName());
            p2.sendMessage("§e§l🔄 §7Ahora estás donde estaba §f" + p1.getName());

            // Partículas
            p1.getWorld().spawnParticle(Particle.PORTAL, p1.getLocation(), 30, 0.5, 1, 0.5, 0.5);
            p2.getWorld().spawnParticle(Particle.PORTAL, p2.getLocation(), 30, 0.5, 1, 0.5, 0.5);
        }
    }

    // --- LLUVIA DE TNT ---
    private void eventTntRain() {
        lastEventName = "§4¡Lluvia de TNT!";
        announceEvent("§4§l💣 LLUVIA DE TNT", "§c¡TNT cae del cielo durante 6 segundos!");
        soundAll(Sound.ENTITY_CREEPER_PRIMED, 1.0f, 0.8f);

        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (world == null) return;

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!gameStarted || ticks > 120) { cancel(); return; }
                ticks += 10;

                for (int i = 0; i < 2; i++) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    double dist = random.nextDouble() * (ARENA_RADIUS - 5);
                    double x = dist * Math.cos(angle);
                    double z = dist * Math.sin(angle);
                    Location tntLoc = new Location(world, x, ARENA_Y + 20, z);
                    org.bukkit.entity.TNTPrimed tnt = world.spawn(tntLoc, org.bukkit.entity.TNTPrimed.class);
                    tnt.setFuseTicks(40 + random.nextInt(20));
                    tnt.setYield(2.0f); // Explosión pequeña
                    tnt.setIsIncendiary(false);
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    // --- MODO GIGANTE ---
    private void eventGiantMode() {
        lastEventName = "§6¡Modo Gigante!";
        announceEvent("§6§l🦶 MODO GIGANTE", "§6¡Super salto + lentitud! Te sientes enorme.");
        soundAll(Sound.ENTITY_IRON_GOLEM_STEP, 1.0f, 0.3f);

        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 200, 4, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 1, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 1, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 1, false, true));
        }
    }

    // --- SUELO DE LAVA ---
    private void eventLavaFloor() {
        lastEventName = "§4¡Suelo de Magma!";
        announceEvent("§4§l🔥 SUELO DE MAGMA", "§c¡El suelo quema! Salta sobre objetos o muere.");
        soundAll(Sound.BLOCK_LAVA_AMBIENT, 1.0f, 1.0f);

        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (world == null) return;

        // Reemplazar suelo con magma temporalmente
        List<Location> magmaBlocks = new ArrayList<>();
        for (int x = -ARENA_RADIUS; x <= ARENA_RADIUS; x += 2) {
            for (int z = -ARENA_RADIUS; z <= ARENA_RADIUS; z += 2) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > ARENA_RADIUS || dist < 5) continue;
                if (random.nextInt(3) == 0) { // Solo 33% del suelo
                    Material current = world.getBlockAt(x, ARENA_Y, z).getType();
                    if (current != Material.AIR && current != Material.MAGMA_BLOCK) {
                        world.getBlockAt(x, ARENA_Y, z).setType(Material.MAGMA_BLOCK);
                        magmaBlocks.add(new Location(world, x, ARENA_Y, z));
                    }
                }
            }
        }

        // Revertir después de 12 segundos
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Location loc : magmaBlocks) {
                if (loc.getBlock().getType() == Material.MAGMA_BLOCK) {
                    loc.getBlock().setType(Material.POLISHED_DEEPSLATE);
                }
            }
        }, 240L);
    }

    // --- ARMADURA ALEATORIA ---
    private void eventRandomArmor() {
        lastEventName = "§3Armadura Random";
        announceEvent("§3§l🛡 ARMADURA ALEATORIA", "§3¡Tu armadura cambió! Puede ser mejor... o peor.");
        soundAll(Sound.ITEM_ARMOR_EQUIP_IRON, 1.0f, 1.2f);

        Material[][] helmets = {{Material.LEATHER_HELMET}, {Material.GOLDEN_HELMET}, {Material.CHAINMAIL_HELMET},
                {Material.IRON_HELMET}, {Material.DIAMOND_HELMET}, {Material.NETHERITE_HELMET}, {Material.TURTLE_HELMET}};
        Material[][] chests = {{Material.LEATHER_CHESTPLATE}, {Material.GOLDEN_CHESTPLATE}, {Material.CHAINMAIL_CHESTPLATE},
                {Material.IRON_CHESTPLATE}, {Material.DIAMOND_CHESTPLATE}, {Material.NETHERITE_CHESTPLATE}};
        Material[][] legs = {{Material.LEATHER_LEGGINGS}, {Material.GOLDEN_LEGGINGS}, {Material.CHAINMAIL_LEGGINGS},
                {Material.IRON_LEGGINGS}, {Material.DIAMOND_LEGGINGS}, {Material.NETHERITE_LEGGINGS}};
        Material[][] boots = {{Material.LEATHER_BOOTS}, {Material.GOLDEN_BOOTS}, {Material.CHAINMAIL_BOOTS},
                {Material.IRON_BOOTS}, {Material.DIAMOND_BOOTS}, {Material.NETHERITE_BOOTS}};

        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            p.getInventory().setHelmet(new ItemStack(helmets[random.nextInt(helmets.length)][0]));
            p.getInventory().setChestplate(new ItemStack(chests[random.nextInt(chests.length)][0]));
            p.getInventory().setLeggings(new ItemStack(legs[random.nextInt(legs.length)][0]));
            p.getInventory().setBoots(new ItemStack(boots[random.nextInt(boots.length)][0]));
        }
    }

    // --- TODOS INVISIBLES ---
    private void eventInvisibility() {
        lastEventName = "§7¡Invisibilidad!";
        announceEvent("§7§l👻 INVISIBILIDAD TOTAL", "§7¡Todos invisibles 10 segundos! ¿Dónde están?");
        soundAll(Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 1.0f);

        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 200, 0, false, false));
            // Quitar armadura temporalmente para que sean realmente invisibles
            // (la armadura se sigue viendo)
        }
    }

    // --- BOXEO ---
    private void eventBoxingMatch() {
        lastEventName = "§6¡Boxeo!";
        announceEvent("§6§l🥊 BOXEO", "§6¡Solo puños durante 12 segundos! Sin armas ni armadura.");
        soundAll(Sound.ENTITY_IRON_GOLEM_ATTACK, 1.0f, 1.5f);

        // Guardar inventarios y quitar todo
        savedInventories.clear();
        savedArmor.clear();
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            savedInventories.put(uuid, p.getInventory().getContents().clone());
            savedArmor.put(uuid, p.getInventory().getArmorContents().clone());
            p.getInventory().clear();
            p.getInventory().setArmorContents(new ItemStack[4]);
            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 240, 3, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 240, 1, false, true));
        }

        // Devolver inventarios después de 12 segundos
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (UUID uuid : alivePlayers) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null) continue;
                if (savedInventories.containsKey(uuid)) {
                    p.getInventory().setContents(savedInventories.get(uuid));
                    p.getInventory().setArmorContents(savedArmor.get(uuid));
                }
            }
            savedInventories.clear();
            savedArmor.clear();
            broadcastGame("§6§l🥊 §7Boxeo terminado. Items devueltos.");
        }, 240L);
    }

    // --- EFECTOS ALEATORIOS ---
    private void eventRandomEffects() {
        lastEventName = "§5Pociones Locas";
        announceEvent("§5§l🧪 POCIONES LOCAS", "§5¡Cada jugador recibe un efecto aleatorio!");
        soundAll(Sound.ENTITY_SPLASH_POTION_BREAK, 1.0f, 1.2f);

        PotionEffectType[] goodEffects = {PotionEffectType.SPEED, PotionEffectType.STRENGTH,
                PotionEffectType.REGENERATION, PotionEffectType.RESISTANCE, PotionEffectType.FIRE_RESISTANCE,
                PotionEffectType.JUMP_BOOST, PotionEffectType.ABSORPTION};
        PotionEffectType[] badEffects = {PotionEffectType.SLOWNESS, PotionEffectType.WEAKNESS,
                PotionEffectType.POISON, PotionEffectType.HUNGER, PotionEffectType.MINING_FATIGUE,
                PotionEffectType.NAUSEA};

        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;

            // 60% bueno, 40% malo
            if (random.nextInt(10) < 6) {
                PotionEffectType effect = goodEffects[random.nextInt(goodEffects.length)];
                p.addPotionEffect(new PotionEffect(effect, 200, random.nextInt(2), false, true));
                p.sendMessage("§a§l✓ §7Efecto positivo: §a" + effect.getName());
            } else {
                PotionEffectType effect = badEffects[random.nextInt(badEffects.length)];
                p.addPotionEffect(new PotionEffect(effect, 160, random.nextInt(2), false, true));
                p.sendMessage("§c§l✖ §7Efecto negativo: §c" + effect.getName());
            }
        }
    }

    // --- LLUVIA DE YUNQUES ---
    private void eventAnvilStorm() {
        lastEventName = "§8¡Yunques!";
        announceEvent("§8§l⬇ LLUVIA DE YUNQUES", "§7¡Yunques caen del cielo! ¡Cuidado con tu cabeza!");
        soundAll(Sound.BLOCK_ANVIL_LAND, 1.0f, 0.5f);

        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (world == null) return;

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!gameStarted || ticks > 100) { cancel(); return; }
                ticks += 5;
                for (int i = 0; i < 3; i++) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    double dist = random.nextDouble() * (ARENA_RADIUS - 5);
                    Location loc = new Location(world, dist * Math.cos(angle), ARENA_Y + 25, dist * Math.sin(angle));
                    org.bukkit.entity.FallingBlock fb = world.spawnFallingBlock(loc, Material.ANVIL.createBlockData());
                    fb.setDropItem(false);
                    fb.setHurtEntities(true);
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    // --- RAYOS ---
    private void eventLightningStrike() {
        lastEventName = "§e¡Rayos!";
        announceEvent("§e§l⚡ TORMENTA ELÉCTRICA", "§e¡Rayos caen sobre jugadores aleatorios!");
        soundAll(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);

        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (world == null) return;

        new BukkitRunnable() {
            int strikes = 0;
            @Override
            public void run() {
                if (!gameStarted || strikes >= 8) { cancel(); return; }
                strikes++;
                List<UUID> alive = new ArrayList<>(alivePlayers);
                if (alive.isEmpty()) { cancel(); return; }
                UUID target = alive.get(random.nextInt(alive.size()));
                Player p = Bukkit.getPlayer(target);
                if (p != null && p.isOnline()) {
                    world.strikeLightning(p.getLocation());
                }
            }
        }.runTaskTimer(plugin, 10L, 15L);
    }

    // --- GRAVEDAD CERO ---
    private void eventZeroGravity() {
        lastEventName = "§f¡Gravedad Cero!";
        announceEvent("§f§l🌙 GRAVEDAD CERO", "§f¡Flotas sin control durante 6 segundos!");
        soundAll(Sound.ENTITY_BAT_TAKEOFF, 1.0f, 0.3f);

        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 60, 0, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 180, 0, false, true));
            p.setVelocity(new Vector(
                    (random.nextDouble() - 0.5) * 2,
                    1.0,
                    (random.nextDouble() - 0.5) * 2));
        }
    }

    // --- NAUSEA CAOS ---
    private void eventNauseaChaos() {
        lastEventName = "§2¡Nausea!";
        announceEvent("§2§l🤮 NAUSEA CAÓTICA", "§2¡Todo da vueltas! ¡No puedes apuntar!");
        soundAll(Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 1.0f, 0.5f);

        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 160, 1, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 160, 2, false, true));
            // Girar la cámara random
            Location loc = p.getLocation();
            loc.setYaw(loc.getYaw() + random.nextInt(180) - 90);
            p.teleport(loc);
        }
    }

    // --- SUELO DESAPARECE ---
    private void eventFloorDisappear() {
        lastEventName = "§c¡Suelo Eliminado!";
        announceEvent("§c§l💨 EL SUELO DESAPARECE", "§c¡Partes del suelo desaparecen! ¡Salta o muere!");
        soundAll(Sound.ENTITY_WITHER_BREAK_BLOCK, 1.0f, 1.5f);

        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (world == null) return;

        // Eliminar el 40% del suelo en parches grandes (TODAS las capas)
        for (int i = 0; i < 25; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 10 + random.nextDouble() * 65;
            int cx = (int) (dist * Math.cos(angle));
            int cz = (int) (dist * Math.sin(angle));
            int size = 2 + random.nextInt(3);
            for (int dx = -size; dx <= size; dx++) {
                for (int dz = -size; dz <= size; dz++) {
                    for (int dy = -4; dy <= 0; dy++) {
                        world.getBlockAt(cx + dx, ARENA_Y + dy, cz + dz).setType(Material.AIR);
                    }
                }
            }
        }
    }

    // --- LANZAR A TODOS ---
    private void eventLaunchAll() {
        lastEventName = "§b¡Lanzamiento!";
        announceEvent("§b§l🚀 LANZAMIENTO MASIVO", "§b¡Todos salen disparados en una dirección aleatoria!");
        soundAll(Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 0.8f);

        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            Vector launch = new Vector(
                    (random.nextDouble() - 0.5) * 4,
                    1.5 + random.nextDouble() * 2,
                    (random.nextDouble() - 0.5) * 4);
            p.setVelocity(launch);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 80, 0, false, true));
        }
    }

    // --- CONGELAR A TODOS ---
    private void eventFreezeAll() {
        lastEventName = "§b¡Congelados!";
        announceEvent("§b§l❄ CONGELACIÓN TOTAL", "§b¡No puedes moverte durante 4 segundos!");
        soundAll(Sound.BLOCK_GLASS_BREAK, 1.0f, 0.3f);

        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 127, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 80, 127, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 80, 128, false, true));
            // Efecto visual de hielo
            p.getWorld().spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.05);
        }
    }

    // --- TELEPORT ALEATORIO ---
    private void eventRandomTeleport() {
        lastEventName = "§d¡Teleport!";
        announceEvent("§d§l🌀 TELEPORT ALEATORIO", "§d¡Todos teletransportados a posiciones random!");
        soundAll(Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (world == null) return;

        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 10 + random.nextDouble() * 60;
            int tx = (int) (dist * Math.cos(angle));
            int tz = (int) (dist * Math.sin(angle));
            // Buscar bloque sólido
            int ty = ARENA_Y + 1;
            for (int y = ARENA_Y + 5; y >= ARENA_Y - 2; y--) {
                if (world.getBlockAt(tx, y, tz).getType() != Material.AIR) {
                    ty = y + 1;
                    break;
                }
            }
            p.teleport(new Location(world, tx + 0.5, ty, tz + 0.5, p.getLocation().getYaw(), p.getLocation().getPitch()));
            p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation(), 40, 0.5, 1, 0.5, 0.5);
        }
    }

    // --- SUELO REBOTANTE ---
    private void eventBouncyFloor() {
        lastEventName = "§a¡Suelo Rebotante!";
        announceEvent("§a§l🟢 SUELO REBOTANTE", "§a¡El suelo se convierte en slime! ¡Boing!");
        soundAll(Sound.ENTITY_SLIME_JUMP, 1.0f, 1.5f);

        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (world == null) return;

        List<Location> changed = new ArrayList<>();
        for (int x = -ARENA_RADIUS; x <= ARENA_RADIUS; x += 3) {
            for (int z = -ARENA_RADIUS; z <= ARENA_RADIUS; z += 3) {
                double d = Math.sqrt(x * x + z * z);
                if (d > ARENA_RADIUS) continue;
                if (world.getBlockAt(x, ARENA_Y, z).getType() != Material.AIR) {
                    changed.add(new Location(world, x, ARENA_Y, z));
                    world.getBlockAt(x, ARENA_Y, z).setType(Material.SLIME_BLOCK);
                }
            }
        }
        // Revertir después de 10 segundos
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Location loc : changed) {
                if (loc.getBlock().getType() == Material.SLIME_BLOCK) {
                    loc.getBlock().setType(Material.POLISHED_DEEPSLATE);
                }
            }
            broadcastGame("§a§l🟢 §7Suelo rebotante terminado.");
        }, 200L);
    }

    // --- TORMENTA WITHER ---
    private void eventWitherStorm() {
        lastEventName = "§0¡Wither!";
        announceEvent("§0§l💀 TORMENTA WITHER", "§8¡Todos envenenados con Wither durante 6 segundos!");
        soundAll(Sound.ENTITY_WITHER_AMBIENT, 1.0f, 0.5f);

        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 120, 1, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 120, 0, false, true));
            p.getWorld().spawnParticle(Particle.SMOKE, p.getLocation(), 20, 0.5, 1, 0.5, 0.05);
        }
    }

    // --- LLUVIA DE BOLAS DE FUEGO ---
    private void eventFireballRain() {
        lastEventName = "§6¡Bolas de Fuego!";
        announceEvent("§6§l🔥 LLUVIA DE FUEGO", "§c¡Bolas de fuego caen del cielo!");
        soundAll(Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.8f);

        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (world == null) return;

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!gameStarted || ticks > 100) { cancel(); return; }
                ticks += 10;
                for (int i = 0; i < 3; i++) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    double dist = random.nextDouble() * (ARENA_RADIUS - 5);
                    Location loc = new Location(world, dist * Math.cos(angle), ARENA_Y + 20, dist * Math.sin(angle));
                    org.bukkit.entity.Fireball fb = world.spawn(loc, org.bukkit.entity.Fireball.class);
                    fb.setDirection(new Vector(0, -1, 0));
                    fb.setYield(1.5f);
                    fb.setIsIncendiary(false);
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    // --- BOMBA DE TELARAÑAS (en jugadores) ---
    private void eventCobwebBomb() {
        lastEventName = "§f¡Trampa Web!";
        announceEvent("§f§l🕸 BOMBA DE TELARAÑAS", "§7¡Telarañas aparecen alrededor de cada jugador!");
        soundAll(Sound.ENTITY_SPIDER_AMBIENT, 1.0f, 0.5f);

        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (world == null) return;

        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            Location loc = p.getLocation();
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dy = 0; dy <= 2; dy++) {
                        if (random.nextInt(3) == 0) {
                            Location wl = loc.clone().add(dx, dy, dz);
                            if (wl.getBlock().getType() == Material.AIR) {
                                wl.getBlock().setType(Material.COBWEB);
                            }
                        }
                    }
                }
            }
        }
        // Limpiar después de 8 segundos
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (int x = -ARENA_RADIUS; x <= ARENA_RADIUS; x++) {
                for (int z = -ARENA_RADIUS; z <= ARENA_RADIUS; z++) {
                    for (int y = ARENA_Y + 1; y <= ARENA_Y + 5; y++) {
                        if (world.getBlockAt(x, y, z).getType() == Material.COBWEB) {
                            world.getBlockAt(x, y, z).setType(Material.AIR);
                        }
                    }
                }
            }
        }, 160L);
    }

    // --- HAMBRE EXTREMA ---
    private void eventHungerGames() {
        lastEventName = "§6¡Hambre Extrema!";
        announceEvent("§6§l🍖 HAMBRE EXTREMA", "§6¡Tu comida desaparece y tienes hambre máxima!");
        soundAll(Sound.ENTITY_PLAYER_BURP, 1.0f, 0.5f);

        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            p.setFoodLevel(1);
            p.setSaturation(0);
            p.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 200, 4, false, true));
            // Quitar toda la comida del inventario
            for (int slot = 0; slot < p.getInventory().getSize(); slot++) {
                ItemStack item = p.getInventory().getItem(slot);
                if (item != null && item.getType().isEdible()) {
                    p.getInventory().setItem(slot, null);
                }
            }
        }
    }

    // --- VIDA INVERTIDA ---
    private void eventReverseHealth() {
        lastEventName = "§c¡Vida Invertida!";
        announceEvent("§c§l💔 VIDA INVERTIDA", "§c¡Si tienes mucha vida, ahora tienes poca! Y viceversa.");
        soundAll(Sound.ENTITY_WITHER_SHOOT, 1.0f, 1.5f);

        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            double current = p.getHealth();
            double max = p.getMaxHealth();
            double inverted = max - current + 1;
            p.setHealth(Math.max(1, Math.min(max, inverted)));
            p.sendMessage("§c§l💔 §7Tu vida se invirtió: §c" + String.format("%.0f", current) + " §7→ §a" + String.format("%.0f", inverted));
        }
    }

    // --- MODO POLLO ---
    private void eventChickenMode() {
        lastEventName = "§e¡Modo Pollo!";
        announceEvent("§e§l🐔 MODO POLLO", "§e¡Todos se vuelven pollos! Débiles pero rápidos.");
        soundAll(Sound.ENTITY_CHICKEN_AMBIENT, 1.0f, 1.5f);

        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            // Quitar armadura temporalmente
            savedInventories.put(uuid, null); // Marker
            savedArmor.put(uuid, p.getInventory().getArmorContents().clone());
            p.getInventory().setArmorContents(new ItemStack[4]);
            // Efectos de pollo
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 160, 3, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 160, 2, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 160, 3, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 160, 0, false, true));
            // Dar huevos como arma
            p.getInventory().addItem(new ItemStack(Material.EGG, 16));
        }

        // Devolver armadura después de 8 segundos
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (UUID uuid : alivePlayers) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null) continue;
                if (savedArmor.containsKey(uuid)) {
                    p.getInventory().setArmorContents(savedArmor.get(uuid));
                }
            }
            savedArmor.clear();
            savedInventories.clear();
            broadcastGame("§e§l🐔 §7Modo pollo terminado.");
        }, 160L);
    }

    // ═══════════════════════════════════════════
    //  OVERRIDE: eliminatePlayer (track eliminated)
    // ═══════════════════════════════════════════

    @Override
    public void eliminatePlayer(UUID uuid) {
        eliminatedPlayers.add(uuid);
        super.eliminatePlayer(uuid);
    }

    // ═══════════════════════════════════════════
    //  STORM
    // ═══════════════════════════════════════════

    private void startStormBorder() {
        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (world == null) return;

        org.bukkit.WorldBorder border = world.getWorldBorder();
        border.setCenter(0, 0);
        border.setSize(STORM_INITIAL_RADIUS * 2);
        border.setWarningDistance(5);
        border.setWarningTime(0);
        border.setDamageAmount(0);
        border.setDamageBuffer(0);
        border.setSize(STORM_MIN_RADIUS * 2, STORM_SHRINK_DURATION);

        stormBorderTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!gameStarted || !stormActive) return;
            World w = Bukkit.getWorld(MiniGameWorld.getWorldName());
            if (w != null) stormRadius = w.getWorldBorder().getSize() / 2.0;
        }, 20L, 20L).getTaskId();

        broadcastGame("§4§l⚠ §c§lTORMENTA CAÓTICA §4§l⚠");
        broadcastGame("§7El mapa se cierra. §c¡Pelea o muere!");
        titleAlive("§4§lTORMENTA", "§c¡El mapa se cierra!");
        soundAll(Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f);
        if (statusBar != null) statusBar.setColor(BarColor.RED);
    }

    // ═══════════════════════════════════════════
    //  BOSSBAR
    // ═══════════════════════════════════════════

    private void updateBossBar() {
        if (statusBar == null) return;

        if (stormActive) {
            double progress = Math.max(0, Math.min(1, (stormRadius - STORM_MIN_RADIUS) / (STORM_INITIAL_RADIUS - STORM_MIN_RADIUS)));
            statusBar.setProgress(progress);
            statusBar.setTitle("§4§l⚔ DEATHMATCH §8| " + lastEventName
                    + " §8| §fRadio: §c" + String.format("%.0f", stormRadius)
                    + " §8| §fVivos: §a" + alivePlayers.size());
        } else {
            int nextIn = nextEventAt - gameTime;
            int timeToStorm = STORM_START - gameTime;
            double progress = Math.max(0, Math.min(1, (double) timeToStorm / STORM_START));
            statusBar.setProgress(progress);
            statusBar.setTitle("§c§l⚔ " + lastEventName
                    + " §8| §fVivos: §a" + alivePlayers.size()
                    + " §8| §eEvento: §f" + nextIn + "s");
        }
    }

    // ═══════════════════════════════════════════
    //  CLEANUP
    // ═══════════════════════════════════════════

    @Override
    public void onCleanup() {
        gameStarted = false;
        stormActive = false;
        eliminatedPlayers.clear();
        savedInventories.clear();
        savedArmor.clear();

        if (stormBorderTaskId != -1) {
            Bukkit.getScheduler().cancelTask(stormBorderTaskId);
            stormBorderTaskId = -1;
        }
        if (fogTaskId != -1) {
            Bukkit.getScheduler().cancelTask(fogTaskId);
            fogTaskId = -1;
        }

        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (world != null) world.getWorldBorder().reset();

        if (statusBar != null) {
            statusBar.removeAll();
            statusBar.setVisible(false);
            statusBar = null;
        }
    }

    // ═══════════════════════════════════════════
    //  UTILITY
    // ═══════════════════════════════════════════

    private void announceEvent(String title, String subtitle) {
        broadcastGame("");
        broadcastGame("§8§l━━━━━━━━━━━━━━━━━━━━━━━━━━");
        broadcastGame("  " + title);
        broadcastGame("  " + subtitle);
        broadcastGame("§8§l━━━━━━━━━━━━━━━━━━━━━━━━━━");
        broadcastGame("");
        titleAlive(title, subtitle);
    }

    private double horizontalDist(Location loc) {
        return Math.sqrt(loc.getX() * loc.getX() + loc.getZ() * loc.getZ());
    }

    private String formatTime(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    // Flags para MiniGameListener
    public boolean isPvpGame() { return true; }
    public boolean allowsBlockPlace() { return false; }
    public boolean allowsBlockBreak() { return false; }
    public boolean allowsDrops() { return true; }
    public boolean allowsPickups() { return true; }
    public boolean allowsInventory() { return true; }
    public boolean allowsHunger() { return true; }
}
