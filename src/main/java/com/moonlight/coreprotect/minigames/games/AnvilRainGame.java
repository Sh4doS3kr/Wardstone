package com.moonlight.coreprotect.minigames.games;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.minigames.*;
import com.moonlight.coreprotect.util.ActionBarUtil;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * LLUVIA DE YUNQUES (Anvil Rain):
 * Los jugadores están en una plataforma circular.
 * Yunques y bloques peligrosos caen del cielo constantemente.
 * Marcadores de sombra (bloques rojos) aparecen en el suelo 1s antes del impacto.
 * Si un yunque te golpea o caes de la plataforma: ELIMINADO.
 *
 * Progresión:
 * - Fase 1 (0-30s): Yunques lentos, pocos, advertencias largas
 * - Fase 2 (30-60s): Más yunques, más rápido, plataforma se encoge
 * - Fase 3 (60-90s): Lluvia intensa, eventos especiales (MEGA YUNQUE, BARRAGE)
 * - Fase 4 (90s+): CAOS TOTAL, sin piedad
 *
 * Eventos especiales:
 * - MEGA ANVIL: Yunque gigante que cubre área 5x5
 * - TNT BARRAGE: Explosiones en cadena por la plataforma
 * - LIGHTNING STORM: Rayos aleatorios en la plataforma
 * - SHRINK: La plataforma se encoge drásticamente
 *
 * Último en pie gana. Si nadie sobrevive, nadie gana.
 */
public class AnvilRainGame extends MiniGame {

    private static final int BASE_Y = 80;
    private static final int INITIAL_RADIUS = 25;
    private static final int SPAWN_HEIGHT = 35;
    private static final int TIME_LIMIT = 180;

    private int currentRadius;
    private BossBar statusBar;
    private BukkitRunnable spawnTask = null;
    private BukkitRunnable cleanupTask = null;
    private final Random random = new Random();
    private final Set<Location> warningBlocks = new HashSet<>();
    private int phase = 1;
    private boolean eventActive = false;

    public AnvilRainGame(CoreProtectPlugin plugin, MiniGameManager manager) {
        super(plugin, manager, MiniGameType.ANVIL_RAIN);
    }

    @Override
    public void buildArena(World world) {
        currentRadius = INITIAL_RADIUS;

        // Plataforma circular de piedra con borde decorativo
        for (int x = -currentRadius - 2; x <= currentRadius + 2; x++) {
            for (int z = -currentRadius - 2; z <= currentRadius + 2; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist <= currentRadius) {
                    // Patrón de suelo variado
                    if ((x + z) % 4 == 0) {
                        world.getBlockAt(x, BASE_Y, z).setType(Material.POLISHED_DEEPSLATE);
                    } else if ((x + z) % 4 == 1) {
                        world.getBlockAt(x, BASE_Y, z).setType(Material.DEEPSLATE_BRICKS);
                    } else if ((x + z) % 4 == 2) {
                        world.getBlockAt(x, BASE_Y, z).setType(Material.DEEPSLATE_TILES);
                    } else {
                        world.getBlockAt(x, BASE_Y, z).setType(Material.COBBLED_DEEPSLATE);
                    }
                    // Soporte debajo
                    world.getBlockAt(x, BASE_Y - 1, z).setType(Material.BEDROCK);
                } else if (dist <= currentRadius + 2) {
                    // Borde de obsidiana
                    world.getBlockAt(x, BASE_Y, z).setType(Material.CRYING_OBSIDIAN);
                    world.getBlockAt(x, BASE_Y - 1, z).setType(Material.BEDROCK);
                }
            }
        }

        // Pilares decorativos con cadenas
        for (int angle = 0; angle < 360; angle += 30) {
            double rad = Math.toRadians(angle);
            int px = (int) ((currentRadius + 3) * Math.cos(rad));
            int pz = (int) ((currentRadius + 3) * Math.sin(rad));
            for (int y = BASE_Y; y <= BASE_Y + 8; y++) {
                world.getBlockAt(px, y, pz).setType(Material.BLACKSTONE);
            }
            world.getBlockAt(px, BASE_Y + 9, pz).setType(Material.SOUL_LANTERN);
            // Cadenas colgando
            world.getBlockAt(px, BASE_Y + 7, pz).setType(Material.IRON_BARS);
            world.getBlockAt(px, BASE_Y + 8, pz).setType(Material.IRON_BARS);
        }

        // Lava debajo para muerte
        for (int x = -currentRadius - 10; x <= currentRadius + 10; x++) {
            for (int z = -currentRadius - 10; z <= currentRadius + 10; z++) {
                world.getBlockAt(x, BASE_Y - 5, z).setType(Material.LAVA);
                world.getBlockAt(x, BASE_Y - 6, z).setType(Material.OBSIDIAN);
            }
        }
    }

    @Override
    public List<Location> getSpawnLocations(World world) {
        List<Location> spawns = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            double angle = (2 * Math.PI / 24) * i;
            int x = (int) (15 * Math.cos(angle));
            int z = (int) (15 * Math.sin(angle));
            spawns.add(new Location(world, x + 0.5, BASE_Y + 1, z + 0.5));
        }
        return spawns;
    }

    @Override
    public void startGameLogic() {
        statusBar = Bukkit.createBossBar("§4§l☠ LLUVIA DE YUNQUES §4§l☠", BarColor.RED, BarStyle.SOLID);
        statusBar.setProgress(1.0);
        statusBar.setVisible(true);
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) statusBar.addPlayer(p);
        }

        broadcastGame("");
        broadcastGame("§4§l☠ §c§lLLUVIA DE YUNQUES §4§l☠");
        broadcastGame("§7¡Esquiva los yunques que caen del cielo!");
        broadcastGame("§7Las §cmarcas rojas §7en el suelo avisan dónde caerá");
        broadcastGame("§7Cada vez es §c§lMÁS INTENSO§7. ¡Último en pie gana!");
        broadcastGame("");

        startSpawnTask();
        startCleanupTask();
    }

    private void startSpawnTask() {
        spawnTask = new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (!running) { cancel(); return; }
                tick++;

                // Calcular fase
                int seconds = tick / 20;
                if (seconds < 30) phase = 1;
                else if (seconds < 60) phase = 2;
                else if (seconds < 90) phase = 3;
                else phase = 4;

                // Frecuencia de spawn basada en fase
                int spawnInterval = getSpawnInterval();
                if (tick % spawnInterval == 0) {
                    int count = getAnvilCount();
                    for (int i = 0; i < count; i++) {
                        spawnAnvilWithWarning();
                    }
                }

                // Eventos especiales
                if (!eventActive && phase >= 2) {
                    if (tick % (20 * 15) == 0 && phase == 2) triggerRandomEvent();
                    else if (tick % (20 * 10) == 0 && phase == 3) triggerRandomEvent();
                    else if (tick % (20 * 7) == 0 && phase >= 4) triggerRandomEvent();
                }
            }
        };
        spawnTask.runTaskTimer(plugin, 40L, 1L);
    }

    private int getSpawnInterval() {
        switch (phase) {
            case 1: return 15; // cada 0.75s
            case 2: return 10; // cada 0.5s
            case 3: return 6;  // cada 0.3s
            case 4: return 3;  // cada 0.15s
            default: return 15;
        }
    }

    private int getAnvilCount() {
        switch (phase) {
            case 1: return 1;
            case 2: return 1 + random.nextInt(2);  // 1-2
            case 3: return 2 + random.nextInt(2);  // 2-3
            case 4: return 3 + random.nextInt(3);  // 3-5
            default: return 1;
        }
    }

    private void spawnAnvilWithWarning() {
        World w = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (w == null) return;

        // Posición aleatoria dentro de la plataforma
        double angle = random.nextDouble() * 2 * Math.PI;
        double dist = random.nextDouble() * (currentRadius - 1);
        int x = (int) (dist * Math.cos(angle));
        int z = (int) (dist * Math.sin(angle));

        // Verificar que está dentro de la plataforma
        if (Math.sqrt(x * x + z * z) > currentRadius) return;

        // Warning: colocar marca roja en el suelo
        Location warningLoc = new Location(w, x, BASE_Y, z);
        Material originalBlock = w.getBlockAt(x, BASE_Y, z).getType();
        if (originalBlock == Material.AIR || originalBlock == Material.LAVA) return;

        w.getBlockAt(x, BASE_Y, z).setType(Material.REDSTONE_BLOCK);
        warningBlocks.add(warningLoc);

        // Partículas de humo en la marca
        w.spawnParticle(Particle.SMOKE, x + 0.5, BASE_Y + 1.2, z + 0.5, 5, 0.3, 0.1, 0.3, 0.01);

        // Tiempo de aviso según fase
        int warningTicks = getWarningTime();

        // Spawn del yunque después del aviso
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!running) return;

            // Restaurar bloque original
            if (warningBlocks.remove(warningLoc)) {
                w.getBlockAt(x, BASE_Y, z).setType(originalBlock);
            }

            // Elegir tipo de proyectil
            Material projectile = getRandomProjectile();

            // Crear FallingBlock desde arriba
            Location spawnLoc = new Location(w, x + 0.5, BASE_Y + SPAWN_HEIGHT, z + 0.5);
            FallingBlock fb = w.spawnFallingBlock(spawnLoc, projectile.createBlockData());
            fb.setDropItem(false);
            fb.setHurtEntities(true);
            fb.setMaxDamage(40);
            fb.setVelocity(new Vector(0, -1.5, 0));

            // Sonido de caída
            w.playSound(new Location(w, x + 0.5, BASE_Y + 5, z + 0.5),
                    Sound.ENTITY_ITEM_PICKUP, 0.3f, 0.5f);

            // Programar impacto y daño por área
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!running) return;
                // Limpiar el FallingBlock si aún existe
                if (!fb.isDead()) fb.remove();

                // Efecto de impacto
                w.playSound(new Location(w, x + 0.5, BASE_Y + 1, z + 0.5),
                        Sound.ENTITY_IRON_GOLEM_DEATH, 0.8f, 0.5f);
                w.spawnParticle(Particle.EXPLOSION, x + 0.5, BASE_Y + 1, z + 0.5, 3, 0.5, 0.3, 0.5, 0);

                // Limpiar bloques de yunque que quedaron en el suelo
                for (int dy = 1; dy <= 3; dy++) {
                    Material at = w.getBlockAt(x, BASE_Y + dy, z).getType();
                    if (at == Material.ANVIL || at == Material.CHIPPED_ANVIL
                            || at == Material.DAMAGED_ANVIL || at == Material.IRON_BLOCK
                            || at == Material.NETHERITE_BLOCK) {
                        w.getBlockAt(x, BASE_Y + dy, z).setType(Material.AIR);
                    }
                }

                // Comprobar jugadores en zona de impacto
                checkImpactDamage(x, z, 1.8);
            }, 15L); // ~0.75s para caer

        }, warningTicks);
    }

    private int getWarningTime() {
        switch (phase) {
            case 1: return 25; // 1.25s
            case 2: return 18; // 0.9s
            case 3: return 12; // 0.6s
            case 4: return 8;  // 0.4s
            default: return 25;
        }
    }

    private Material getRandomProjectile() {
        int r = random.nextInt(10);
        if (r < 6) return Material.ANVIL;
        if (r < 8) return Material.IRON_BLOCK;
        return Material.NETHERITE_BLOCK;
    }

    private void checkImpactDamage(int x, int z, double radius) {
        for (UUID uuid : new ArrayList<>(alivePlayers)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;

            double dx = p.getLocation().getX() - (x + 0.5);
            double dz = p.getLocation().getZ() - (z + 0.5);
            double dist = Math.sqrt(dx * dx + dz * dz);

            // Solo afectar si están a la altura de la plataforma
            if (dist <= radius && p.getLocation().getY() < BASE_Y + 4) {
                // Golpe directo = eliminación
                if (dist <= 0.8) {
                    p.sendMessage("§4§l☠ §c¡Un yunque te ha aplastado!");
                    p.playSound(p.getLocation(), Sound.ENTITY_IRON_GOLEM_DEATH, 1.0f, 0.3f);
                    eliminatePlayer(uuid);
                } else {
                    // Knockback si estás cerca
                    Vector kb = p.getLocation().toVector()
                            .subtract(new Vector(x + 0.5, p.getLocation().getY(), z + 0.5))
                            .normalize().multiply(1.2).setY(0.4);
                    p.setVelocity(kb);
                    p.damage(4.0);
                    p.playSound(p.getLocation(), Sound.ENTITY_IRON_GOLEM_HURT, 0.8f, 1.0f);
                }
            }
        }
    }

    // ==========================================
    // EVENTOS ESPECIALES
    // ==========================================

    private void triggerRandomEvent() {
        if (eventActive) return;
        int event = random.nextInt(8);
        switch (event) {
            case 0: eventMegaAnvil(); break;
            case 1: eventTntBarrage(); break;
            case 2: eventLightningStorm(); break;
            case 3: eventShrink(); break;
            case 4: eventEarthquake(); break;
            case 5: eventBlackout(); break;
            case 6: eventFloorCrack(); break;
            case 7: eventGravitySurge(); break;
        }
    }

    private void eventMegaAnvil() {
        eventActive = true;
        broadcastGame("§4§l⚠ §c§lMEGA YUNQUE §4§l⚠ §7¡Cuidado con la zona roja!");
        soundAll(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 0.5f);
        titleAlive("§4§l⚠ MEGA YUNQUE ⚠", "§c¡CORRE!");

        World w = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (w == null) { eventActive = false; return; }

        // Posición aleatoria
        double angle = random.nextDouble() * 2 * Math.PI;
        double dist = random.nextDouble() * (currentRadius - 6);
        int cx = (int) (dist * Math.cos(angle));
        int cz = (int) (dist * Math.sin(angle));
        int impactRadius = 5;

        // Marca grande de warning
        for (int dx = -impactRadius; dx <= impactRadius; dx++) {
            for (int dz = -impactRadius; dz <= impactRadius; dz++) {
                if (Math.sqrt(dx * dx + dz * dz) <= impactRadius) {
                    int bx = cx + dx, bz = cz + dz;
                    if (Math.sqrt(bx * bx + bz * bz) <= currentRadius) {
                        w.getBlockAt(bx, BASE_Y, bz).setType(Material.REDSTONE_BLOCK);
                    }
                }
            }
        }

        // Impacto después de 2 segundos
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!running) { eventActive = false; return; }

            // Restaurar suelo y hacer impacto
            for (int dx = -impactRadius; dx <= impactRadius; dx++) {
                for (int dz = -impactRadius; dz <= impactRadius; dz++) {
                    if (Math.sqrt(dx * dx + dz * dz) <= impactRadius) {
                        int bx = cx + dx, bz = cz + dz;
                        if (Math.sqrt(bx * bx + bz * bz) <= currentRadius) {
                            w.getBlockAt(bx, BASE_Y, bz).setType(Material.DEEPSLATE_BRICKS);
                            w.spawnParticle(Particle.EXPLOSION, bx + 0.5, BASE_Y + 1, bz + 0.5,
                                    1, 0, 0, 0, 0);
                        }
                    }
                }
            }

            w.playSound(new Location(w, cx, BASE_Y, cz), Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);
            w.playSound(new Location(w, cx, BASE_Y, cz), Sound.ENTITY_IRON_GOLEM_DEATH, 2.0f, 0.3f);

            // Daño a jugadores en la zona
            for (UUID uuid : new ArrayList<>(alivePlayers)) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline()) continue;
                double playerDist = Math.sqrt(
                        Math.pow(p.getLocation().getX() - cx, 2) +
                        Math.pow(p.getLocation().getZ() - cz, 2));
                if (playerDist <= impactRadius + 1 && p.getLocation().getY() < BASE_Y + 4) {
                    p.sendMessage("§4§l☠ §c¡El MEGA YUNQUE te aplastó!");
                    eliminatePlayer(uuid);
                }
            }

            eventActive = false;
        }, 40L);
    }

    private void eventTntBarrage() {
        eventActive = true;
        broadcastGame("§c§l💥 §e§lBARRAGE DE TNT §c§l💥 §7¡Explosiones por toda la plataforma!");
        soundAll(Sound.ENTITY_TNT_PRIMED, 1.0f, 1.5f);
        titleAlive("§c§l💥 TNT BARRAGE 💥", "§7¡Esquiva las explosiones!");

        World w = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (w == null) { eventActive = false; return; }

        // 8 explosiones en cadena
        new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                if (!running || count >= 8) {
                    eventActive = false;
                    cancel();
                    return;
                }
                double angle = random.nextDouble() * 2 * Math.PI;
                double dist = random.nextDouble() * (currentRadius - 2);
                int ex = (int) (dist * Math.cos(angle));
                int ez = (int) (dist * Math.sin(angle));

                // Explosión sin destruir bloques
                w.createExplosion(new Location(w, ex + 0.5, BASE_Y + 1, ez + 0.5),
                        3.0f, false, false);

                // Knockback y daño extra
                for (UUID uuid : new ArrayList<>(alivePlayers)) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null) continue;
                    double d = p.getLocation().distance(new Location(w, ex + 0.5, BASE_Y + 1, ez + 0.5));
                    if (d <= 4) {
                        Vector kb = p.getLocation().toVector()
                                .subtract(new Vector(ex + 0.5, p.getLocation().getY(), ez + 0.5))
                                .normalize().multiply(1.5).setY(0.6);
                        p.setVelocity(kb);
                    }
                }
                count++;
            }
        }.runTaskTimer(plugin, 10L, 8L);
    }

    private void eventLightningStorm() {
        eventActive = true;
        broadcastGame("§e§l⚡ §f§lTORMENTA ELÉCTRICA §e§l⚡ §7¡Rayos aleatorios!");
        soundAll(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
        titleAlive("§e§l⚡ TORMENTA ⚡", "§7¡Cuidado con los rayos!");

        World w = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (w == null) { eventActive = false; return; }

        // 12 rayos en 4 segundos
        new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                if (!running || count >= 12) {
                    eventActive = false;
                    cancel();
                    return;
                }
                double angle = random.nextDouble() * 2 * Math.PI;
                double dist = random.nextDouble() * currentRadius;
                int lx = (int) (dist * Math.cos(angle));
                int lz = (int) (dist * Math.sin(angle));

                Location strike = new Location(w, lx + 0.5, BASE_Y + 1, lz + 0.5);
                w.strikeLightningEffect(strike);

                // Daño a jugadores cercanos
                for (UUID uuid : new ArrayList<>(alivePlayers)) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null) continue;
                    if (p.getLocation().distance(strike) <= 2.5) {
                        p.damage(6.0);
                        p.setFireTicks(40);
                        Vector kb = p.getLocation().toVector()
                                .subtract(strike.toVector()).normalize().multiply(0.8).setY(0.5);
                        p.setVelocity(kb);
                    }
                }
                count++;
            }
        }.runTaskTimer(plugin, 5L, 7L);
    }

    private void eventShrink() {
        if (currentRadius <= 10) {
            eventActive = false;
            return;
        }
        eventActive = true;
        int shrinkAmount = phase >= 3 ? 4 : 3;
        int newRadius = Math.max(8, currentRadius - shrinkAmount);

        broadcastGame("§4§l⚠ §c§l¡LA PLATAFORMA SE ENCOGE! §4§l⚠");
        soundAll(Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.5f);
        titleAlive("§4§l⚠ ENCOGIENDO ⚠", "§c¡La plataforma se reduce!");

        World w = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (w == null) { eventActive = false; return; }

        // Destruir borde gradualmente
        new BukkitRunnable() {
            int r = currentRadius;
            @Override
            public void run() {
                if (!running || r <= newRadius) {
                    currentRadius = newRadius;
                    eventActive = false;
                    cancel();
                    return;
                }
                // Eliminar anillo exterior
                for (int x = -r - 2; x <= r + 2; x++) {
                    for (int z = -r - 2; z <= r + 2; z++) {
                        double dist = Math.sqrt(x * x + z * z);
                        if (dist > r - 1 && dist <= r + 2) {
                            w.getBlockAt(x, BASE_Y, z).setType(Material.AIR);
                            w.getBlockAt(x, BASE_Y - 1, z).setType(Material.AIR);
                            if (random.nextInt(3) == 0) {
                                w.spawnParticle(Particle.BLOCK,
                                        x + 0.5, BASE_Y + 0.5, z + 0.5,
                                        3, 0.2, 0.2, 0.2, 0,
                                        Material.DEEPSLATE_BRICKS.createBlockData());
                            }
                        }
                    }
                }
                w.playSound(new Location(w, 0, BASE_Y, 0), Sound.BLOCK_STONE_BREAK, 1.0f, 0.8f);
                r--;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void eventEarthquake() {
        eventActive = true;
        broadcastGame("§6§l🌋 §e§lTERREMOTO §6§l🌋 §7¡La plataforma tiembla!");
        soundAll(Sound.ENTITY_RAVAGER_STUNNED, 1.0f, 0.5f);
        titleAlive("§6§l🌋 TERREMOTO 🌋", "§e¡Aguanta el equilibrio!");

        // 5 oleadas de sacudidas durante 3 segundos
        new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                if (!running || count >= 5) {
                    eventActive = false;
                    cancel();
                    return;
                }
                for (UUID uuid : new ArrayList<>(alivePlayers)) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null || !p.isOnline()) continue;
                    // Sacudida aleatoria violenta en cualquier dirección
                    double vx = (random.nextDouble() - 0.5) * 2.0;
                    double vy = 0.4 + random.nextDouble() * 0.4;
                    double vz = (random.nextDouble() - 0.5) * 2.0;
                    p.setVelocity(new Vector(vx, vy, vz));
                    p.playSound(p.getLocation(), Sound.BLOCK_STONE_BREAK, 0.6f, 0.6f);
                }
                World w = Bukkit.getWorld(MiniGameWorld.getWorldName());
                if (w != null) {
                    w.playSound(new Location(w, 0, BASE_Y, 0), Sound.ENTITY_RAVAGER_ATTACK, 1.5f, 0.4f);
                    // Partículas de polvo en la plataforma
                    for (int i = 0; i < 20; i++) {
                        double rx = (random.nextDouble() - 0.5) * currentRadius * 2;
                        double rz = (random.nextDouble() - 0.5) * currentRadius * 2;
                        w.spawnParticle(Particle.BLOCK, rx, BASE_Y + 0.5, rz, 3, 0, 0, 0, 0.01,
                                Material.COBBLED_DEEPSLATE.createBlockData());
                    }
                }
                count++;
            }
        }.runTaskTimer(plugin, 0L, 12L);
    }

    private void eventBlackout() {
        eventActive = true;
        broadcastGame("§8§l🕶 §0§lAPAGÓN §8§l🕶 §7¡No puedes ver nada!");
        soundAll(Sound.AMBIENT_CAVE, 1.0f, 0.3f);
        titleAlive("§8§l🕶 APAGÓN 🕶", "§7¡Esquiva sin ver!");

        World w = Bukkit.getWorld(MiniGameWorld.getWorldName());

        // Apagar glowstone del techo (poner obsidiana temporal)
        if (w != null) {
            for (int x = -INITIAL_RADIUS - 2; x <= INITIAL_RADIUS + 2; x++) {
                for (int z = -INITIAL_RADIUS - 2; z <= INITIAL_RADIUS + 2; z++) {
                    if (w.getBlockAt(x, BASE_Y + 12, z).getType() == Material.GLOWSTONE) {
                        w.getBlockAt(x, BASE_Y + 12, z).setType(Material.OBSIDIAN);
                    }
                }
            }
        }

        // Ceguera a todos los jugadores durante 5 segundos
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.BLINDNESS, 100, 0, false, false, true));
            }
        }

        // Restaurar luces después de 5 segundos
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (w != null) {
                for (int x = -INITIAL_RADIUS - 2; x <= INITIAL_RADIUS + 2; x++) {
                    for (int z = -INITIAL_RADIUS - 2; z <= INITIAL_RADIUS + 2; z++) {
                        if (w.getBlockAt(x, BASE_Y + 12, z).getType() == Material.OBSIDIAN) {
                            w.getBlockAt(x, BASE_Y + 12, z).setType(
                                    (x + z) % 3 == 0 ? Material.GLOWSTONE : Material.BLACK_CONCRETE);
                        }
                    }
                }
            }
            broadcastGame("§e§l💡 §7¡Las luces vuelven!");
            eventActive = false;
        }, 100L);
    }

    private void eventFloorCrack() {
        eventActive = true;
        broadcastGame("§c§l⚠ §6§lSUELO AGRIETADO §c§l⚠ §7¡El suelo se rompe bajo tus pies!");
        soundAll(Sound.BLOCK_STONE_BREAK, 1.0f, 0.5f);
        titleAlive("§c§l⚠ SUELO AGRIETADO ⚠", "§6¡Muévete rápido!");

        World w = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (w == null) { eventActive = false; return; }

        // Eliminar ~30% de los bloques del suelo aleatoriamente
        List<int[]> cracked = new ArrayList<>();
        for (int x = -currentRadius + 1; x < currentRadius; x++) {
            for (int z = -currentRadius + 1; z < currentRadius; z++) {
                if (Math.sqrt(x * x + z * z) <= currentRadius - 1 && random.nextInt(4) == 0) {
                    Material original = w.getBlockAt(x, BASE_Y, z).getType();
                    if (original != Material.AIR && original != Material.LAVA) {
                        w.getBlockAt(x, BASE_Y, z).setType(Material.AIR);
                        cracked.add(new int[]{x, z});
                        w.spawnParticle(Particle.BLOCK, x + 0.5, BASE_Y + 0.5, z + 0.5,
                                4, 0.2, 0.1, 0.2, 0,
                                Material.DEEPSLATE_BRICKS.createBlockData());
                    }
                }
            }
        }

        // Restaurar suelo después de 3 segundos
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!running) { eventActive = false; return; }
            for (int[] pos : cracked) {
                if (Math.sqrt(pos[0] * pos[0] + pos[1] * pos[1]) <= currentRadius) {
                    w.getBlockAt(pos[0], BASE_Y, pos[1]).setType(Material.DEEPSLATE_BRICKS);
                }
            }
            broadcastGame("§a§l✔ §7¡El suelo se ha restaurado!");
            eventActive = false;
        }, 60L);
    }

    private void eventGravitySurge() {
        eventActive = true;
        broadcastGame("§b§l🚀 §f§lSURGE DE GRAVEDAD §b§l🚀 §7¡Salís disparados hacia arriba!");
        soundAll(Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 0.5f);
        titleAlive("§b§l🚀 SURGE DE GRAVEDAD 🚀", "§f¡Aguanta el aterrizaje!");

        // Lanzar a todos hacia arriba
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.setVelocity(new Vector(
                        (random.nextDouble() - 0.5) * 0.5,
                        2.5 + random.nextDouble() * 1.0,
                        (random.nextDouble() - 0.5) * 0.5));
                p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 2.0f);
            }
        }

        // Durante la caída: lluvia masiva de yunques
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!running) { eventActive = false; return; }
            broadcastGame("§c§l☠ §7¡LLUVIA MASIVA mientras caéis!");
            soundAll(Sound.ENTITY_IRON_GOLEM_DEATH, 1.0f, 0.7f);
            // 15 yunques de golpe
            for (int i = 0; i < 15; i++) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> spawnAnvilWithWarning(), (long)(random.nextInt(15)));
            }
            eventActive = false;
        }, 30L);
    }

    // ==========================================
    // CLEANUP TASK - Limpia FallingBlocks y bloques residuales
    // ==========================================

    private void startCleanupTask() {
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!running) { cancel(); return; }
                World w = Bukkit.getWorld(MiniGameWorld.getWorldName());
                if (w == null) return;

                // Limpiar FallingBlocks que se quedaron parados
                w.getEntitiesByClass(FallingBlock.class).forEach(fb -> {
                    if (fb.getTicksLived() > 100) fb.remove();
                });

                // Limpiar bloques de yunque/hierro que quedaron sobre la plataforma
                for (int x = -INITIAL_RADIUS; x <= INITIAL_RADIUS; x++) {
                    for (int z = -INITIAL_RADIUS; z <= INITIAL_RADIUS; z++) {
                        for (int y = BASE_Y + 1; y <= BASE_Y + 5; y++) {
                            Material mat = w.getBlockAt(x, y, z).getType();
                            if (mat == Material.ANVIL || mat == Material.CHIPPED_ANVIL
                                    || mat == Material.DAMAGED_ANVIL || mat == Material.IRON_BLOCK
                                    || mat == Material.NETHERITE_BLOCK) {
                                w.getBlockAt(x, y, z).setType(Material.AIR);
                            }
                        }
                    }
                }
            }
        };
        cleanupTask.runTaskTimer(plugin, 40L, 40L);
    }

    @Override
    public void onTick() {
        if (!running) return;

        // Comprobar caída de jugadores
        for (UUID uuid : new ArrayList<>(alivePlayers)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;
            if (p.getLocation().getY() < BASE_Y - 2) {
                p.sendMessage("§4§l☠ §c¡Caíste de la plataforma!");
                eliminatePlayer(uuid);
            }
        }

        // BossBar y fase
        int remaining = TIME_LIMIT - gameTime;
        if (remaining <= 0) {
            broadcastGame("§e§l⏳ §7¡Tiempo agotado! Nadie gana.");
            endGame(null);
            return;
        }

        String phaseText;
        BarColor barColor;
        switch (phase) {
            case 1: phaseText = "§a§lFASE 1"; barColor = BarColor.GREEN; break;
            case 2: phaseText = "§e§lFASE 2"; barColor = BarColor.YELLOW; break;
            case 3: phaseText = "§c§lFASE 3"; barColor = BarColor.RED; break;
            default: phaseText = "§4§lCAOS TOTAL"; barColor = BarColor.RED; break;
        }

        statusBar.setTitle("§4☠ " + phaseText + " §7| §fVivos: §a" + alivePlayers.size()
                + " §7| §e" + remaining + "s §7| §fRadio: §c" + currentRadius);
        statusBar.setColor(barColor);
        statusBar.setProgress(Math.max(0, (double) remaining / TIME_LIMIT));

        // Anuncios de fase
        if (gameTime == 30) {
            broadcastGame("§e§l⚠ §c§lFASE 2 §e§l⚠ §7¡Más yunques, más rápido!");
            soundAll(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.0f);
            titleAlive("§e§lFASE 2", "§7¡Se pone serio!");
        } else if (gameTime == 60) {
            broadcastGame("§c§l⚠ §4§lFASE 3 §c§l⚠ §7¡INTENSO! ¡Eventos especiales!");
            soundAll(Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.0f);
            titleAlive("§c§lFASE 3", "§4¡Sin piedad!");
        } else if (gameTime == 90) {
            broadcastGame("§4§l☠ §4§lCAOS TOTAL §4§l☠ §7¡SOBREVIVE SI PUEDES!");
            soundAll(Sound.ENTITY_WITHER_DEATH, 0.8f, 0.5f);
            titleAlive("§4§l☠ CAOS TOTAL ☠", "§c¡Buena suerte!");
        }

        // Info cada 30 sec
        if (gameTime % 30 == 0 && gameTime > 0) {
            broadcastGame("§4§lLluvia de Yunques §7| " + phaseText
                    + " §7| §fVivos: §a" + alivePlayers.size()
                    + " §7| §e" + remaining + "s");
        }

        // Action bar
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            ActionBarUtil.send(p, "§4☠ " + phaseText
                    + " §7| §fRadio: §c" + currentRadius
                    + " §7| §e" + remaining + "s");
        }
    }

    @Override
    public void onCleanup() {
        if (spawnTask != null) {
            try { spawnTask.cancel(); } catch (Exception ignored) {}
        }
        if (cleanupTask != null) {
            try { cleanupTask.cancel(); } catch (Exception ignored) {}
        }
        if (statusBar != null) {
            statusBar.removeAll();
            statusBar.setVisible(false);
        }
        warningBlocks.clear();

        // Limpiar todos los FallingBlocks
        World w = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (w != null) {
            w.getEntitiesByClass(FallingBlock.class).forEach(FallingBlock::remove);
        }
    }
}
