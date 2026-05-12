package com.moonlight.coreprotect.minigames.games;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.minigames.MiniGame;
import com.moonlight.coreprotect.minigames.MiniGameManager;
import com.moonlight.coreprotect.minigames.MiniGameType;
import com.moonlight.coreprotect.minigames.MiniGameWorld;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Pasa la Bomba: Los jugadores forman un círculo y deben pasar un "globo"
 * (slime ball que se infla) al jugador de su izquierda antes de que explote.
 * No pueden moverse, solo girar. Click derecho para pasar.
 */
public class PassTheBombGame extends MiniGame {

    private static final int ARENA_RADIUS = 8;
    private static final int ARENA_Y = 100;
    private static final int MIN_FUSE_TICKS = 40;   // 2 segundos mínimo
    private static final int MAX_FUSE_TICKS = 140;  // 7 segundos máximo

    // Orden circular de jugadores (por posición en el círculo)
    private final List<UUID> circleOrder = new ArrayList<>();
    // Quién tiene el globo actualmente
    private UUID bombHolder = null;
    // Ticks restantes para la explosión
    private int fuseTicksRemaining = 0;
    // Ronda actual
    private int round = 0;
    // Posiciones fijas de spawn (para congelar jugadores)
    private final Map<UUID, Location> fixedPositions = new HashMap<>();
    // Task de congelar movimiento
    private int freezeTaskId = -1;
    // Si el juego terminó
    private boolean ended = false;
    // Cooldown de pase (evita spam)
    private final Map<UUID, Long> passCooldown = new HashMap<>();
    private static final long PASS_COOLDOWN_MS = 500; // 0.5s entre intentos

    private final Random random = new Random();

    public PassTheBombGame(CoreProtectPlugin plugin, MiniGameManager manager) {
        super(plugin, manager, MiniGameType.PASS_THE_BOMB);
    }

    @Override
    public void buildArena(World world) {
        int cx = 0, cz = 0;
        // Plataforma circular de piedra
        for (int x = -ARENA_RADIUS - 2; x <= ARENA_RADIUS + 2; x++) {
            for (int z = -ARENA_RADIUS - 2; z <= ARENA_RADIUS + 2; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist <= ARENA_RADIUS + 2) {
                    Block b = world.getBlockAt(cx + x, ARENA_Y - 1, cz + z);
                    if (dist <= ARENA_RADIUS - 1) {
                        b.setType(Material.SMOOTH_QUARTZ, false);
                    } else if (dist <= ARENA_RADIUS + 1) {
                        b.setType(Material.LIGHT_GRAY_CONCRETE, false);
                    } else {
                        b.setType(Material.GRAY_CONCRETE, false);
                    }
                    // Limpiar aire encima
                    for (int y = 0; y < 5; y++) {
                        world.getBlockAt(cx + x, ARENA_Y + y, cz + z).setType(Material.AIR, false);
                    }
                }
            }
        }
        // Barrera invisible alrededor
        for (int x = -ARENA_RADIUS - 3; x <= ARENA_RADIUS + 3; x++) {
            for (int z = -ARENA_RADIUS - 3; z <= ARENA_RADIUS + 3; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > ARENA_RADIUS + 2 && dist <= ARENA_RADIUS + 3) {
                    for (int y = 0; y < 4; y++) {
                        world.getBlockAt(cx + x, ARENA_Y + y, cz + z).setType(Material.BARRIER, false);
                    }
                }
            }
        }
        // Decoración central
        world.getBlockAt(cx, ARENA_Y - 1, cz).setType(Material.GLOWSTONE, false);
    }

    @Override
    public List<Location> getSpawnLocations(World world) {
        List<Location> spawns = new ArrayList<>();
        int playerCount = players.size();
        double angleStep = (2 * Math.PI) / playerCount;
        double radius = Math.max(3.0, playerCount * 0.8);
        if (radius > ARENA_RADIUS - 1) radius = ARENA_RADIUS - 1;

        for (int i = 0; i < playerCount; i++) {
            double angle = angleStep * i;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location loc = new Location(world, x + 0.5, ARENA_Y, z + 0.5);
            // Orientar al centro
            loc.setYaw((float) Math.toDegrees(Math.atan2(-x, z)) + 180);
            loc.setPitch(0);
            spawns.add(loc);
        }
        return spawns;
    }

    @Override
    protected void onPreCountdown() {
        // Construir el orden circular basado en spawns ya asignados
        circleOrder.clear();
        circleOrder.addAll(alivePlayers);

        // Guardar posiciones fijas
        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        List<Location> spawns = getSpawnLocations(world);
        int idx = 0;
        for (UUID uuid : circleOrder) {
            fixedPositions.put(uuid, spawns.get(idx % spawns.size()));
            idx++;
        }
    }

    @Override
    public void startGameLogic() {
        // Iniciar congelamiento de posición
        startFreezeTask();
        // Dar instrucciones
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendMessage("");
                p.sendMessage("§c§l💣 PASA LA BOMBA §c§l💣");
                p.sendMessage("§7Tienes un globo que se infla. ¡Pásalo!");
                p.sendMessage("§e▶ GOLPEA §7al jugador de tu §aIZQUIERDA §7para pasarla.");
                p.sendMessage("§c▶ Si explota contigo, §c§l¡ELIMINADO!");
                p.sendMessage("§7Puedes §agirarte §7pero §cNO moverte§7. ¡Apunta bien!");
                p.sendMessage("");
            }
        }
        // Primera ronda
        startNewRound();
    }

    private void startNewRound() {
        if (ended || alivePlayers.size() <= 1) return;
        round++;

        // Elegir portador aleatorio entre los vivos
        List<UUID> alive = new ArrayList<>(alivePlayers);
        bombHolder = alive.get(random.nextInt(alive.size()));

        // Calcular tiempo de fusible (se acorta con las rondas)
        int maxTicks = Math.max(MIN_FUSE_TICKS, MAX_FUSE_TICKS - (round * 15));
        int minTicks = Math.max(MIN_FUSE_TICKS, maxTicks - 40);
        fuseTicksRemaining = minTicks + random.nextInt(maxTicks - minTicks + 1);

        // Dar el globo al portador
        giveBombItem(bombHolder);

        // Anunciar
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                Player holder = Bukkit.getPlayer(bombHolder);
                String holderName = holder != null ? holder.getName() : "???";
                p.sendTitle("§c§lRonda " + round, "§e" + holderName + " §7tiene la bomba!", 5, 30, 5);
                p.playSound(p.getLocation(), Sound.ENTITY_TNT_PRIMED, 0.7f, 1.2f);
            }
        }
    }

    private void giveBombItem(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p == null || !p.isOnline()) return;

        // Limpiar inventario
        for (UUID alive : alivePlayers) {
            Player ap = Bukkit.getPlayer(alive);
            if (ap != null && ap.isOnline()) {
                ap.getInventory().clear();
            }
        }

        // Dar fire charge como "bomba"
        ItemStack bomb = new ItemStack(Material.FIRE_CHARGE);
        ItemMeta meta = bomb.getItemMeta();
        meta.setDisplayName("§c§l💣 ¡BOMBA! §7(¡GOLPEA al de tu izquierda!)");
        meta.setLore(Arrays.asList("§e¡Golpea al jugador de tu izquierda para pasar!", "§cSi explota contigo, pierdes."));
        bomb.setItemMeta(meta);
        p.getInventory().setItem(0, bomb);
        p.getInventory().setHeldItemSlot(0);

        // Efecto visual
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
    }

    /**
     * Llamado cuando un jugador golpea a otro jugador (EntityDamageByEntity).
     * Verifica que el atacante tenga la bomba y el target sea el de su izquierda.
     */
    public void onPlayerHit(Player attacker, Player target) {
        if (ended) return;
        if (!attacker.getUniqueId().equals(bombHolder)) return;
        if (!alivePlayers.contains(attacker.getUniqueId())) return;
        if (!alivePlayers.contains(target.getUniqueId())) return;

        // Cooldown
        long now = System.currentTimeMillis();
        Long lastPass = passCooldown.get(attacker.getUniqueId());
        if (lastPass != null && now - lastPass < PASS_COOLDOWN_MS) return;
        passCooldown.put(attacker.getUniqueId(), now);

        // Verificar que el target es el jugador a la izquierda
        UUID expectedTarget = getPlayerToLeft(attacker.getUniqueId());
        if (expectedTarget == null) return;

        if (!target.getUniqueId().equals(expectedTarget)) {
            attacker.sendMessage("§c§l✖ §7¡Ese no es el de tu izquierda! Gírate y golpea al correcto.");
            attacker.playSound(attacker.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.2f);
            return;
        }

        // Transferir bomba
        attacker.getInventory().clear();
        attacker.playSound(attacker.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1.0f, 1.5f);

        // Partículas de pase
        Location from = attacker.getLocation().add(0, 1, 0);
        Location to = target.getLocation().add(0, 1, 0);
        from.getWorld().spawnParticle(Particle.FLAME, from, 5, 0.1, 0.1, 0.1, 0.05);
        to.getWorld().spawnParticle(Particle.FLAME, to, 5, 0.1, 0.1, 0.1, 0.05);

        bombHolder = target.getUniqueId();
        giveBombItem(target.getUniqueId());

        // Mensaje
        target.sendMessage("§c§l💣 §e¡Tienes la bomba! §7¡Golpea al de tu izquierda!");
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
    }

    /**
     * Verifica si un jugador es el bomb holder (para el listener).
     */
    public boolean isBombHolder(UUID uuid) {
        return uuid.equals(bombHolder);
    }

    /**
     * Obtiene el jugador a la izquierda en el círculo (siguiente vivo en orden).
     */
    private UUID getPlayerToLeft(UUID current) {
        // Encontrar posición actual en el círculo
        List<UUID> aliveInOrder = new ArrayList<>();
        for (UUID uuid : circleOrder) {
            if (alivePlayers.contains(uuid)) {
                aliveInOrder.add(uuid);
            }
        }
        if (aliveInOrder.size() <= 1) return null;

        int currentIdx = aliveInOrder.indexOf(current);
        if (currentIdx == -1) return null;

        // Siguiente en sentido horario (izquierda del jugador)
        int nextIdx = (currentIdx + 1) % aliveInOrder.size();
        return aliveInOrder.get(nextIdx);
    }

    @Override
    public void onTick() {
        if (ended || bombHolder == null) return;

        fuseTicksRemaining--;

        // Efecto de ticking para todos
        float pitch = 1.0f + (1.5f * (1.0f - (float) fuseTicksRemaining / MAX_FUSE_TICKS));
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                if (fuseTicksRemaining <= 5) {
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
                } else if (fuseTicksRemaining <= 20) {
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, pitch);
                } else if (gameTime % 2 == 0) {
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f, pitch);
                }
            }
        }

        // Mostrar action bar al portador
        Player holder = Bukkit.getPlayer(bombHolder);
        if (holder != null && holder.isOnline()) {
            int bars = Math.max(0, (fuseTicksRemaining * 20) / MAX_FUSE_TICKS);
            StringBuilder barStr = new StringBuilder("§c💣 ");
            for (int i = 0; i < 20; i++) {
                barStr.append(i < bars ? "§a█" : "§4█");
            }
            barStr.append(" §c💣");
            holder.sendActionBar(barStr.toString());

            // Partículas en el portador
            holder.getWorld().spawnParticle(Particle.SMOKE, holder.getLocation().add(0, 2.2, 0),
                    2, 0.1, 0.05, 0.1, 0.01);
        }

        // ¿Explotó?
        if (fuseTicksRemaining <= 0) {
            explodeBomb();
        }
    }

    private void explodeBomb() {
        if (ended) return;
        Player victim = Bukkit.getPlayer(bombHolder);
        if (victim == null || !victim.isOnline()) return;

        // Efecto de explosión
        Location loc = victim.getLocation().add(0, 1, 0);
        loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 3, 0.5, 0.5, 0.5, 0);
        loc.getWorld().spawnParticle(Particle.FLAME, loc, 30, 0.5, 0.5, 0.5, 0.15);
        loc.getWorld().spawnParticle(Particle.LAVA, loc, 15, 0.5, 0.5, 0.5, 0);
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.8f);

        // Anunciar eliminación
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendMessage("§c§l💥 §e" + victim.getName() + " §7¡ha explotado! §c§lELIMINADO");
                p.sendTitle("§c§l💥 BOOM!", "§e" + victim.getName() + " §7eliminado", 5, 30, 10);
            }
        }

        // Eliminar jugador
        victim.getInventory().clear();
        bombHolder = null;
        eliminatePlayer(victim.getUniqueId());
        fixedPositions.remove(victim.getUniqueId());

        // Verificar si queda alguien
        if (alivePlayers.size() <= 1) {
            ended = true;
            checkWinCondition();
            return;
        }

        // Recalcular posiciones del círculo (compactar)
        recalculateCirclePositions();

        // Siguiente ronda tras pausa
        new BukkitRunnable() {
            @Override
            public void run() {
                startNewRound();
            }
        }.runTaskLater(plugin, 60L); // 3 segundos de pausa
    }

    private void recalculateCirclePositions() {
        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (world == null) return;

        List<UUID> alive = new ArrayList<>();
        for (UUID uuid : circleOrder) {
            if (alivePlayers.contains(uuid)) {
                alive.add(uuid);
            }
        }

        int playerCount = alive.size();
        if (playerCount <= 1) return;

        double angleStep = (2 * Math.PI) / playerCount;
        double radius = Math.max(3.0, playerCount * 0.8);
        if (radius > ARENA_RADIUS - 1) radius = ARENA_RADIUS - 1;

        for (int i = 0; i < playerCount; i++) {
            double angle = angleStep * i;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location loc = new Location(world, x + 0.5, ARENA_Y, z + 0.5);
            loc.setYaw((float) Math.toDegrees(Math.atan2(-x, z)) + 180);
            loc.setPitch(0);
            fixedPositions.put(alive.get(i), loc);

            Player p = Bukkit.getPlayer(alive.get(i));
            if (p != null && p.isOnline()) {
                p.teleport(loc);
            }
        }
    }

    private void startFreezeTask() {
        freezeTaskId = new BukkitRunnable() {
            @Override
            public void run() {
                if (!running || ended) {
                    cancel();
                    return;
                }
                // Congelar posición (pero permitir rotación)
                for (UUID uuid : alivePlayers) {
                    Player p = Bukkit.getPlayer(uuid);
                    Location fixed = fixedPositions.get(uuid);
                    if (p != null && p.isOnline() && fixed != null) {
                        Location current = p.getLocation();
                        // Solo corregir si se movió de posición XZ
                        if (Math.abs(current.getX() - fixed.getX()) > 0.15
                                || Math.abs(current.getZ() - fixed.getZ()) > 0.15
                                || Math.abs(current.getY() - fixed.getY()) > 0.5) {
                            // Mantener la rotación del jugador
                            Location tp = fixed.clone();
                            tp.setYaw(current.getYaw());
                            tp.setPitch(current.getPitch());
                            p.teleport(tp);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 2L).getTaskId(); // Cada 2 ticks = muy suave
    }

    @Override
    public void onCleanup() {
        ended = true;
        if (freezeTaskId != -1) {
            Bukkit.getScheduler().cancelTask(freezeTaskId);
            freezeTaskId = -1;
        }
        circleOrder.clear();
        fixedPositions.clear();
        bombHolder = null;
    }
}
