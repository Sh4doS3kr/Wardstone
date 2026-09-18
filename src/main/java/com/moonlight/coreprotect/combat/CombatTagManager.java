package com.moonlight.coreprotect.combat;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.moonlight.coreprotect.util.SmallCaps;

public class CombatTagManager {

    private final CoreProtectPlugin plugin;
    private final Map<UUID, CombatData> combatants = new ConcurrentHashMap<>();
    private static final int COMBAT_DURATION_SECONDS = 30;
    private static final double MAX_DISTANCE = 250.0;
    private static final double TARGET_DISTANCE = 200.0;
    private static final double BARRIER_PUSHBACK = 2.5;
    private static final double KNOCKBACK_FORCE = 4.0;

    // Comandos permitidos durante PvP
    private static final Set<String> ALLOWED_COMMANDS = new HashSet<>(Arrays.asList(
            "msg", "r", "reply", "tell", "whisper"
    ));

    // Mensajes humilladores al huir del PvP
    private static final String[] SHAME_MESSAGES = {
            "§c§l💀 §f{player} §cprefirió morir antes que pelear. §7¡Cobarde!",
            "§c§l💀 §f{player} §cintentó huir del combate... §7¡Y lo pagó con su vida!",
            "§c§l💀 §f{player} §cse desconectó en pleno PvP. §7¡Vergüenza!",
            "§c§l💀 §f{player} §cno aguantó la presión. §7¡Gallina! 🐔",
            "§c§l💀 §f{player} §cpensó que podía escapar... §7§l¡PENSÓ MAL!",
            "§c§l💀 §f{player} §chuyó del combate como un covarde. §7¡RIP! ⚰️"
    };

    private BukkitRunnable tickTask;

    public CombatTagManager(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        startTickTask();
    }

    /**
     * Marca a dos jugadores como en combate PvP.
     */
    public void tagCombat(Player attacker, Player victim) {
        long now = System.currentTimeMillis();
        long expiry = now + (COMBAT_DURATION_SECONDS * 1000L);

        // Crear o actualizar datos del atacante
        CombatData attackerData = combatants.get(attacker.getUniqueId());
        if (attackerData == null) {
            attackerData = new CombatData(victim.getUniqueId(), expiry);
            BossBar bar = Bukkit.createBossBar("§c§l⚔ PvP §8| §f" + COMBAT_DURATION_SECONDS + "s §8| §cvs §f" + victim.getName(),
                    BarColor.RED, BarStyle.SOLID);
            bar.addPlayer(attacker);
            attackerData.bossBar = bar;
            combatants.put(attacker.getUniqueId(), attackerData);

            // Notificar inicio de combate
            attacker.sendMessage(SmallCaps.convert("§c§l⚔ §f¡Estás en combate con §e" + victim.getName() + "§f! §7No huyas."));
            attacker.playSound(attacker.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);

            // Deshabilitar vuelo
            if (attacker.getAllowFlight() && attacker.getGameMode() != GameMode.CREATIVE) {
                attacker.setFlying(false);
                attacker.setAllowFlight(false);
                attacker.sendMessage(SmallCaps.convert("§c§l⚔ §fVuelo §cdeshabilitado §fdurante el combate."));
            }

        } else {
            attackerData.opponent = victim.getUniqueId();
            attackerData.expiryTime = expiry;
        }

        // Crear o actualizar datos de la víctima
        CombatData victimData = combatants.get(victim.getUniqueId());
        if (victimData == null) {
            victimData = new CombatData(attacker.getUniqueId(), expiry);
            BossBar bar = Bukkit.createBossBar("§c§l⚔ PvP §8| §f" + COMBAT_DURATION_SECONDS + "s §8| §cvs §f" + attacker.getName(),
                    BarColor.RED, BarStyle.SOLID);
            bar.addPlayer(victim);
            victimData.bossBar = bar;
            combatants.put(victim.getUniqueId(), victimData);

            // Notificar inicio de combate
            victim.sendMessage(SmallCaps.convert("§c§l⚔ §f¡Estás en combate con §e" + attacker.getName() + "§f! §7No huyas."));
            victim.playSound(victim.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);

            // Deshabilitar vuelo
            if (victim.getAllowFlight() && victim.getGameMode() != GameMode.CREATIVE) {
                victim.setFlying(false);
                victim.setAllowFlight(false);
                victim.sendMessage(SmallCaps.convert("§c§l⚔ §fVuelo §cdeshabilitado §fdurante el combate."));
            }

        } else {
            victimData.opponent = attacker.getUniqueId();
            victimData.expiryTime = expiry;
        }
    }

    /**
     * Verifica si un jugador está en combate.
     */
    public boolean isInCombat(UUID uuid) {
        return combatants.containsKey(uuid);
    }

    /**
     * Obtiene el oponente de un jugador en combate.
     */
    public UUID getOpponent(UUID uuid) {
        CombatData data = combatants.get(uuid);
        return data != null ? data.opponent : null;
    }

    /**
     * Verifica si un comando está permitido durante PvP.
     */
    public boolean isCommandAllowed(String command) {
        String base = command.toLowerCase().split(" ")[0].replace("/", "");
        return ALLOWED_COMMANDS.contains(base);
    }

    /**
     * Maneja la desconexión de un jugador en combate (combat log).
     */
    public void handleCombatLog(Player player) {
        CombatData data = combatants.get(player.getUniqueId());
        if (data == null) return;

        // Mensaje humillador
        String shameMsg = SHAME_MESSAGES[new Random().nextInt(SHAME_MESSAGES.length)]
                .replace("{player}", player.getName());
        Bukkit.broadcastMessage(shameMsg);

        // Animación de muerte: rayo + partículas
        Location loc = player.getLocation();
        loc.getWorld().strikeLightningEffect(loc);
        loc.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc, 50, 0.5, 1, 0.5, 0.1);
        loc.getWorld().spawnParticle(Particle.LAVA, loc, 30, 0.5, 0.5, 0.5, 0);
        loc.getWorld().playSound(loc, Sound.ENTITY_WITHER_DEATH, 0.8f, 0.5f);

        // Matar al jugador
        player.setHealth(0);

        // Limpiar combate
        removeCombat(player.getUniqueId());
    }

    /**
     * Maneja el movimiento: barrera a 200 bloques del oponente con teletransporte si están muy lejos.
     */
    public boolean handleMovement(Player player) {
        CombatData data = combatants.get(player.getUniqueId());
        if (data == null) return false;

        Player opponent = Bukkit.getPlayer(data.opponent);
        if (opponent == null || !opponent.isOnline()) return false;

        double distance = player.getLocation().distance(opponent.getLocation());
        
        // Si está por encima de 200 bloques, teletransportar en lugar de knockback
        if (distance > MAX_DISTANCE) {
            applyTeleport(player, opponent, distance);
            return true;
        }
        // Si está entre 150-200 bloques, aplicar knockback gradual para acercar
        else if (distance > TARGET_DISTANCE) {
            applyGradualKnockback(player, opponent, distance);
            return true;
        }
        
        return false;
    }
    
    /**
     * Aplica teletransporte cuando el jugador está muy lejos (>200m)
     */
    private void applyTeleport(Player player, Player opponent, double distance) {
        Location playerLoc = player.getLocation();
        Location opponentLoc = opponent.getLocation();

        // Calcular posición a 150 bloques del oponente
        double dx = playerLoc.getX() - opponentLoc.getX();
        double dz = playerLoc.getZ() - opponentLoc.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);

        if (len > 0) {
            dx /= len;
            dz /= len;
        }

        // Teletransportar a 150 bloques del oponente
        Location teleportLoc = opponentLoc.clone().add(dx * TARGET_DISTANCE, 0, dz * TARGET_DISTANCE);
        teleportLoc.setYaw(playerLoc.getYaw());
        teleportLoc.setPitch(playerLoc.getPitch());
        
        // Asegurar que el terreno sea seguro
        teleportLoc = findSafeLocation(teleportLoc);
        
        player.teleport(teleportLoc);

        // Efectos de teletransporte
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.0f);
        player.sendMessage(SmallCaps.convert("§c§l⚔ §f¡Demasiado lejos! §eTeletransportándote hacia el combate."));
        
        // Partículas de teletransporte
        player.getWorld().spawnParticle(Particle.END_ROD, playerLoc, 20, 1, 1, 1, 0.1);
        player.getWorld().spawnParticle(Particle.PORTAL, teleportLoc, 15, 1, 1, 1, 0.1);
        
        // Notificar al oponente
        opponent.sendMessage(SmallCaps.convert("§e§l⚔ §fTu oponente fue teletransportado hacia ti."));
    }
    
    /**
     * Encuentra una ubicación segura para el teletransporte
     */
    private Location findSafeLocation(Location loc) {
        World world = loc.getWorld();
        
        // Buscar terreno seguro desde la altura del jugador hacia abajo
        for (int y = loc.getBlockY() + 5; y >= Math.max(0, loc.getBlockY() - 10); y--) {
            Location testLoc = loc.clone();
            testLoc.setY(y);
            
            // Verificar si hay espacio para el jugador
            if (isSafeLocation(world, testLoc)) {
                return testLoc;
            }
        }
        
        // Si no se encuentra ubicación segura, usar la original
        return loc;
    }
    
    /**
     * Verifica si una ubicación es segura para el jugador
     */
    private boolean isSafeLocation(World world, Location loc) {
        // Verificar que los bloques no sean sólidos
        org.bukkit.Material head = world.getBlockAt(loc.clone().add(0, 1, 0)).getType();
        org.bukkit.Material body = world.getBlockAt(loc).getType();
        org.bukkit.Material feet = world.getBlockAt(loc.clone().add(0, -1, 0)).getType();
        
        // La cabeza y el cuerpo deben ser no sólidos, los pies deben ser sólidos
        return !head.isSolid() && !body.isSolid() && feet.isSolid();
    }
    
    /**
     * Aplica un knockback suave cuando está entre 150-200m
     */
    private void applyGradualKnockback(Player player, Player opponent, double distance) {
        Location playerLoc = player.getLocation();
        Location opponentLoc = opponent.getLocation();

        // Calcular dirección hacia el oponente
        double dx = opponentLoc.getX() - playerLoc.getX();
        double dz = opponentLoc.getZ() - playerLoc.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);

        if (len > 0) {
            dx /= len;
            dz /= len;
        }

        // Calcular fuerza de knockback basada en la distancia
        double distanceRatio = (distance - TARGET_DISTANCE) / (MAX_DISTANCE - TARGET_DISTANCE);
        double force = 0.5 + (distanceRatio * 2.0); // Entre 0.5 y 2.5 de fuerza
        
        // Aplicar knockback suave pero constante
        org.bukkit.util.Vector knockback = new org.bukkit.util.Vector(dx * force, 0.2, dz * force);
        player.setVelocity(knockback);

        // Efectos sutiles
        if (System.currentTimeMillis() % 1000 < 100) { // Mensaje cada segundo aproximadamente
            player.sendMessage(SmallCaps.convert("§e§l⚔ §fAcercándote al combate... §7(" + String.format("%.0f", distance) + "m)"));
        }
        
        // Partículas sutiles cada cierta distancia
        if (distance > 180) {
            Location barrierLoc = player.getLocation().add(0, 1, 0);
            player.getWorld().spawnParticle(Particle.CRIT, barrierLoc, 5, 0.5, 0.5, 0.5, 0.1);
        }
    }

    /**
     * Verifica si un jugador puede volar (bloquea fly en combate, permite elytra).
     */
    public boolean canFly(Player player) {
        if (!isInCombat(player.getUniqueId())) return true;
        // Permitir elytra gliding
        return player.isGliding();
    }

    /**
     * Remueve a un jugador del combate de forma limpia.
     */
    public void removeCombat(UUID uuid) {
        CombatData data = combatants.remove(uuid);
        if (data != null) {
            if (data.bossBar != null) {
                data.bossBar.removeAll();
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.removePotionEffect(PotionEffectType.GLOWING);
                player.sendMessage(SmallCaps.convert("§a§l✔ §fYa no estás en combate."));
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
        }
    }

    /**
     * Tarea principal que actualiza bossbars y expira combates cada tick (1 segundo).
     */
    private void startTickTask() {
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                Iterator<Map.Entry<UUID, CombatData>> it = combatants.entrySet().iterator();

                while (it.hasNext()) {
                    Map.Entry<UUID, CombatData> entry = it.next();
                    UUID uuid = entry.getKey();
                    CombatData data = entry.getValue();

                    // Expiró el combate
                    if (now >= data.expiryTime) {
                        if (data.bossBar != null) data.bossBar.removeAll();
                        Player player = Bukkit.getPlayer(uuid);
                        if (player != null && player.isOnline()) {
                            player.removePotionEffect(PotionEffectType.GLOWING);
                            player.sendMessage(SmallCaps.convert("§a§l✔ §fCombate terminado. Ya puedes moverte libremente."));
                            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                        }
                        it.remove();
                        continue;
                    }

                    // Actualizar BossBar
                    Player player = Bukkit.getPlayer(uuid);
                    Player opponent = Bukkit.getPlayer(data.opponent);
                    if (player == null || !player.isOnline()) {
                        if (data.bossBar != null) data.bossBar.removeAll();
                        it.remove();
                        continue;
                    }

                    int secondsLeft = (int) ((data.expiryTime - now) / 1000);
                    double progress = (double) secondsLeft / COMBAT_DURATION_SECONDS;
                    progress = Math.max(0, Math.min(1, progress));

                    String opponentName = opponent != null ? opponent.getName() : "???";
                    double dist = opponent != null ? player.getLocation().distance(opponent.getLocation()) : 0;

                    data.bossBar.setTitle("§c§l⚔ PvP §8| §f" + secondsLeft + "s §8| §cvs §f" + opponentName
                            + " §8| §7" + String.format("%.0f", dist) + "m");
                    data.bossBar.setProgress(progress);

                    // Color según tiempo restante
                    if (secondsLeft <= 5) {
                        data.bossBar.setColor(BarColor.GREEN);
                    } else if (secondsLeft <= 15) {
                        data.bossBar.setColor(BarColor.YELLOW);
                    } else {
                        data.bossBar.setColor(BarColor.RED);
                    }

                    // Bloquear fly cada tick
                    if (player.isFlying() && !player.isGliding() && player.getGameMode() != GameMode.CREATIVE
                            && player.getGameMode() != GameMode.SPECTATOR) {
                        player.setFlying(false);
                        player.setAllowFlight(false);
                    }
                }
            }
        };
        tickTask.runTaskTimer(plugin, 20L, 20L);
    }

    /**
     * Limpia todo al desactivar el plugin.
     */
    public void cleanup() {
        if (tickTask != null) tickTask.cancel();
        for (Map.Entry<UUID, CombatData> entry : combatants.entrySet()) {
            CombatData data = entry.getValue();
            if (data.bossBar != null) data.bossBar.removeAll();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.removePotionEffect(PotionEffectType.GLOWING);
            }
        }
        combatants.clear();
    }

    /**
     * Datos internos de combate por jugador.
     */
    private static class CombatData {
        UUID opponent;
        long expiryTime;
        BossBar bossBar;

        CombatData(UUID opponent, long expiryTime) {
            this.opponent = opponent;
            this.expiryTime = expiryTime;
        }
    }
}
