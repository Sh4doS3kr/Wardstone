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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;

/**
 * Escapa de la Bestia:
 * 1 jugador es la Bestia (Speed II, Strength, glow rojo).
 * El resto son supervivientes que deben activar 5 palancas repartidas por el laberinto
 * para abrir la puerta de salida y escapar.
 * La Bestia mata de 1 golpe. Los supervivientes tienen Speed I y un mapa del laberinto.
 * Arena: Laberinto gigante con cripta, alcantarillas, biblioteca, jardín muerto, torre.
 */
public class BeastEscapeGame extends MiniGame {

    private UUID beastUUID;
    private final Set<UUID> survivors = new LinkedHashSet<>();
    private final Set<Integer> activatedLevers = new HashSet<>();
    private static final int TOTAL_LEVERS = 5;
    private static final int TIME_LIMIT = 240; // 4 minutos
    private boolean exitOpen = false;
    private BossBar progressBar;
    private Location exitLocation;
    private final List<Location> leverLocations = new ArrayList<>();
    private Scoreboard scoreboard;
    private Team beastTeam;

    public BeastEscapeGame(CoreProtectPlugin plugin, MiniGameManager manager) {
        super(plugin, manager, MiniGameType.BEAST_ESCAPE);
    }

    @Override
    public void buildArena(World world) {
        ArenaBuilder.buildBeastEscape(world);
    }

    @Override
    public List<Location> getSpawnLocations(World world) {
        return ArenaBuilder.getBeastEscapeSpawns(world);
    }

    @Override
    public void startGameLogic() {
        if (alivePlayers.size() < 3) {
            broadcastGame("§c§l✖ §cNo hay suficientes jugadores. (Mínimo 3)");
            endGame(null);
            return;
        }

        List<UUID> playerList = new ArrayList<>(alivePlayers);
        Collections.shuffle(playerList);

        // El primer jugador es la Bestia
        beastUUID = playerList.get(0);
        for (int i = 1; i < playerList.size(); i++) {
            survivors.add(playerList.get(i));
        }

        World w = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (w == null) return;

        // Preparar localizaciones de palancas
        leverLocations.clear();
        leverLocations.addAll(ArenaBuilder.getBeastEscapeLeverLocations(w));
        exitLocation = ArenaBuilder.getBeastEscapeExitLocation(w);

        // Scoreboard para glow ROJO de la bestia (solo la bestia brilla)
        scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        beastTeam = scoreboard.registerNewTeam("beast");
        beastTeam.setColor(ChatColor.DARK_RED);
        beastTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);

        // Configurar Bestia
        Player beast = Bukkit.getPlayer(beastUUID);
        if (beast != null) {
            beast.sendTitle("§4§l¡ERES LA BESTIA!", "§c¡Caza a todos!", 10, 70, 20);
            beast.sendMessage("§4§lBESTIA §7- Mata a todos los supervivientes antes de que escapen.");
            beast.getInventory().clear();
            ItemStack axe = new ItemStack(Material.NETHERITE_AXE);
            ItemMeta axeMeta = axe.getItemMeta();
            axeMeta.setDisplayName("§4§l☠ Hacha de la Bestia");
            axeMeta.setUnbreakable(true);
            axe.setItemMeta(axeMeta);
            beast.getInventory().setItem(0, axe);
            beast.getInventory().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
            beast.getInventory().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
            beast.getInventory().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
            beast.getInventory().setBoots(new ItemStack(Material.NETHERITE_BOOTS));
            beast.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999, 1, false, false, true));
            beast.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 999999, 2, false, false, true));
            beast.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 999999, 1, false, false, true));
            beast.setGlowing(true);
            beastTeam.addEntry(beast.getName());
            beast.setScoreboard(scoreboard);
            beast.teleport(new Location(w, 0, 81, 0));
        }

        // Configurar supervivientes
        for (UUID uuid : survivors) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendTitle("§a§lSUPERVIVIENTE", "§7¡Activa 5 palancas y escapa!", 10, 70, 20);
                p.sendMessage("§a§lSUPERVIVIENTE §7- Encuentra y activa §e5 palancas §7para abrir la puerta de salida.");
                p.sendMessage("§7Mira tu barra de acción para ver la dirección a la palanca más cercana.");
                p.getInventory().clear();
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999, 0, false, false, true));
                p.setGlowing(false); // Solo la bestia brilla
                p.setScoreboard(scoreboard); // Para ver glow rojo de la bestia
            }
        }

        // La bestia empieza 5 segundos tarde (dar ventaja a supervivientes)
        if (beast != null) {
            beast.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 10, false, false, true));
            beast.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0, false, false, true));
            beast.sendMessage("§c§l¡5 SEGUNDOS DE VENTAJA! §7Los supervivientes corren...");
        }

        // BossBar con progreso de palancas
        progressBar = Bukkit.createBossBar(
            "§e§lPalancas: §f0/" + TOTAL_LEVERS + " §7| §cPuerta: §4CERRADA",
            BarColor.RED, BarStyle.SEGMENTED_6);
        progressBar.setProgress(0);
        progressBar.setVisible(true);
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) progressBar.addPlayer(p);
        }

        broadcastGame("");
        broadcastGame("§4§l☠ §c§lESCAPA DE LA BESTIA §4§l☠");
        broadcastGame("§7Supervivientes: §aActiva " + TOTAL_LEVERS + " palancas §7para abrir la puerta.");
        broadcastGame("§7La Bestia: §cCaza a todos antes de que escapen.");
        broadcastGame("§eLa bestia se libera en §c5 segundos§e...");
        broadcastGame("");
    }

    /**
     * Llamado cuando un superviviente interactúa con una palanca
     */
    public void onLeverActivated(Player player, Location leverLoc) {
        if (!running || !survivors.contains(player.getUniqueId())) return;
        if (beastUUID != null && player.getUniqueId().equals(beastUUID)) return;

        // Buscar la palanca más cercana
        for (int i = 0; i < leverLocations.size(); i++) {
            if (activatedLevers.contains(i)) continue;
            Location ll = leverLocations.get(i);
            if (ll.distanceSquared(leverLoc) < 9) { // Dentro de 3 bloques
                activatedLevers.add(i);

                broadcastGame("§e§l⚡ §f" + player.getName() + " §7activó una palanca! §e(" + activatedLevers.size() + "/" + TOTAL_LEVERS + ")");
                soundAll(Sound.BLOCK_LEVER_CLICK, 1.0f, 1.2f);

                updateProgressBar();

                if (activatedLevers.size() >= TOTAL_LEVERS) {
                    openExit();
                }
                return;
            }
        }
    }

    private void openExit() {
        exitOpen = true;
        broadcastGame("");
        broadcastGame("§a§l¡LA PUERTA SE HA ABIERTO! §f¡CORRED A LA SALIDA!");
        broadcastGame("");
        titleAlive("§a§l¡PUERTA ABIERTA!", "§7¡Corre a la salida!");
        soundAll(Sound.BLOCK_IRON_DOOR_OPEN, 1.0f, 0.5f);
        soundAll(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);

        // Abrir puerta física en la arena
        World w = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (w != null && exitLocation != null) {
            int ex = exitLocation.getBlockX(), ey = exitLocation.getBlockY(), ez = exitLocation.getBlockZ();
            // Limpiar la puerta y el área detrás (z a z-4)
            for (int z = ez - 4; z <= ez; z++) {
                for (int dy = 0; dy < 4; dy++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        w.getBlockAt(ex + dx, ey + dy, z).setType(Material.AIR);
                    }
                }
            }
            // Poner bloques brillantes para marcar la salida
            w.getBlockAt(ex, ey + 4, ez).setType(Material.GLOWSTONE);
            w.getBlockAt(ex - 1, ey + 4, ez).setType(Material.SEA_LANTERN);
            w.getBlockAt(ex + 1, ey + 4, ez).setType(Material.SEA_LANTERN);
            // Beacon de luz en la plataforma de salida
            w.getBlockAt(ex, ey - 1, ez - 2).setType(Material.GOLD_BLOCK);
            w.getBlockAt(ex, ey, ez - 2).setType(Material.BEACON);
        }

        updateProgressBar();
    }

    /**
     * Llamado cuando un superviviente llega a la zona de salida.
     * Usa coordenadas de bloque para evitar problemas con distanceSquared entre mundos.
     */
    public boolean isAtExit(Player player) {
        if (!exitOpen || exitLocation == null) return false;
        Location pLoc = player.getLocation();
        int px = pLoc.getBlockX(), pz = pLoc.getBlockZ();
        int ex = exitLocation.getBlockX(), ez = exitLocation.getBlockZ();
        // Detectar si el jugador está cerca de la puerta o en la plataforma de salida (z <= exit.z)
        return Math.abs(px - ex) <= 4 && pz <= ez + 2 && pz >= ez - 5;
    }

    private void updateProgressBar() {
        if (progressBar == null) return;
        double progress = (double) activatedLevers.size() / TOTAL_LEVERS;
        progressBar.setProgress(Math.min(1.0, progress));
        if (exitOpen) {
            progressBar.setTitle("§a§l¡PUERTA ABIERTA! §f¡Corre a la salida!");
            progressBar.setColor(BarColor.GREEN);
        } else {
            progressBar.setTitle("§e§lPalancas: §f" + activatedLevers.size() + "/" + TOTAL_LEVERS + " §7| §cPuerta: §4CERRADA");
        }
    }

    @Override
    public void onTick() {
        if (!running) return;

        World w = Bukkit.getWorld(MiniGameWorld.getWorldName());

        // === Impedir que la bestia use la salida ===
        if (exitOpen && beastUUID != null) {
            Player beast = Bukkit.getPlayer(beastUUID);
            if (beast != null && beast.isOnline() && exitLocation != null) {
                Location bLoc = beast.getLocation();
                int bx = bLoc.getBlockX(), bz = bLoc.getBlockZ();
                int ex = exitLocation.getBlockX(), ez = exitLocation.getBlockZ();
                // Si la bestia se acerca demasiado a la puerta, empujarla hacia atrás
                if (Math.abs(bx - ex) <= 3 && bz <= ez + 3 && bz >= ez - 2) {
                    beast.setVelocity(new org.bukkit.util.Vector(0, 0.3, 1.5)); // Empujar hacia dentro del laberinto
                    ActionBarUtil.send(beast, "§c§l¡NO PUEDES USAR LA SALIDA!");
                }
            }
        }

        // === Comprobar supervivientes que llegaron a la salida ===
        if (exitOpen) {
            for (UUID uuid : new ArrayList<>(survivors)) {
                // La bestia NO puede escapar
                if (uuid.equals(beastUUID)) continue;
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline() && isAtExit(p)) {
                    broadcastGame("§a§l✓ §f" + p.getName() + " §7¡ha escapado!");
                    p.sendTitle("§a§l¡ESCAPASTE!", "§7¡Has sobrevivido!", 10, 40, 10);
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                    survivors.remove(uuid);
                    alivePlayers.remove(uuid);
                    spectators.add(uuid);
                    p.setGameMode(GameMode.SPECTATOR);
                    p.teleport(new Location(w, 0, 100, 0));
                }
            }

            // Si no quedan supervivientes (todos escaparon o murieron)
            if (survivors.isEmpty()) {
                broadcastGame("§a§l¡TODOS HAN ESCAPADO! §fLos supervivientes ganan.");
                endGame(null);
                return;
            }
        }

        // Si todos los supervivientes han sido eliminados (no escaparon), gana la bestia
        if (survivors.isEmpty() && running) {
            Player beast = Bukkit.getPlayer(beastUUID);
            broadcastGame("§4§l☠ §c¡LA BESTIA HA GANADO! §fNo queda nadie.");
            endGame(beast);
            return;
        }

        // Mostrar dirección a palanca más cercana via action bar
        if (w != null) {
            for (UUID uuid : survivors) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline()) continue;
                Location target;
                String targetName;
                if (exitOpen && exitLocation != null) {
                    target = exitLocation;
                    targetName = "§a§lSALIDA";
                } else {
                    target = getNearestUnactivatedLever(p.getLocation());
                    targetName = "§e§lPALANCA";
                }
                if (target != null) {
                    String dirMsg = buildDirectionIndicator(p, target, targetName);
                    ActionBarUtil.send(p, dirMsg);
                }
            }
        }

        // Avisos de tiempo
        int remaining = TIME_LIMIT - gameTime;
        if (remaining == 60 || remaining == 30 || remaining == 10) {
            broadcastGame("§e§l⏳ §fTiempo restante: §c" + remaining + "s");
        }
        if (gameTime % 30 == 0 && gameTime > 0) {
            broadcastGame("§4§lBestia §7| §fSupervivientes: §a" + survivors.size() + " §7| §ePalancas: §f" + activatedLevers.size() + "/" + TOTAL_LEVERS + " §7| §e" + remaining + "s");
        }

        // Tiempo agotado
        if (gameTime >= TIME_LIMIT) {
            Player beast = Bukkit.getPlayer(beastUUID);
            broadcastGame("§4§l☠ §7¡Tiempo agotado! La bestia gana.");
            endGame(beast);
        }
    }

    private Location getNearestUnactivatedLever(Location from) {
        Location nearest = null;
        double minDist = Double.MAX_VALUE;
        for (int i = 0; i < leverLocations.size(); i++) {
            if (activatedLevers.contains(i)) continue;
            double dist = leverLocations.get(i).distanceSquared(from);
            if (dist < minDist) {
                minDist = dist;
                nearest = leverLocations.get(i);
            }
        }
        return nearest;
    }

    private String buildDirectionIndicator(Player player, Location target, String label) {
        Location pLoc = player.getLocation();
        double dx = target.getX() - pLoc.getX();
        double dz = target.getZ() - pLoc.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        // Ángulo hacia el objetivo
        double targetAngle = Math.toDegrees(Math.atan2(-dx, dz));
        double playerYaw = pLoc.getYaw() % 360;
        if (playerYaw < 0) playerYaw += 360;
        double relative = targetAngle - playerYaw;
        while (relative < -180) relative += 360;
        while (relative > 180) relative -= 360;
        // Flecha direccional
        String arrow;
        if (relative > -22.5 && relative <= 22.5) arrow = "§a⬆";
        else if (relative > 22.5 && relative <= 67.5) arrow = "§e⬉";
        else if (relative > 67.5 && relative <= 112.5) arrow = "§e⬅";
        else if (relative > 112.5 && relative <= 157.5) arrow = "§c⬋";
        else if (relative > 157.5 || relative <= -157.5) arrow = "§c⬇";
        else if (relative > -157.5 && relative <= -112.5) arrow = "§c⬊";
        else if (relative > -112.5 && relative <= -67.5) arrow = "§e➡";
        else arrow = "§e⬈";
        int dist = (int) Math.round(distance);
        return label + " " + arrow + " §f" + dist + "m";
    }

    public boolean isBeast(UUID uuid) {
        return uuid.equals(beastUUID);
    }

    public boolean isSurvivor(UUID uuid) {
        return survivors.contains(uuid);
    }

    /**
     * Eliminar superviviente silenciosamente (cuando se desconecta).
     * No llama a super.eliminatePlayer porque el jugador ya fue removido en onQuit.
     */
    public void eliminatePlayerSilent(UUID uuid) {
        survivors.remove(uuid);
    }

    @Override
    public void eliminatePlayer(UUID uuid) {
        // Si la bestia muere:
        if (uuid.equals(beastUUID)) {
            if (!exitOpen) {
                // Palancas NO activadas → supervivientes ganan automáticamente
                broadcastGame("§a§l¡LA BESTIA HA MUERTO! §fLos supervivientes ganan.");
                endGame(null);
            } else {
                // Palancas activadas → la bestia muere pero el juego sigue (supervivientes aún deben escapar)
                broadcastGame("§a§l☠ ¡La bestia ha caído! §7Pero aún debéis llegar a la salida...");
                alivePlayers.remove(uuid);
                spectators.add(uuid);
                Player beast = Bukkit.getPlayer(uuid);
                if (beast != null && beast.isOnline()) {
                    beast.setGlowing(false);
                    beast.setGameMode(GameMode.SPECTATOR);
                    World bw = Bukkit.getWorld(MiniGameWorld.getWorldName());
                    if (bw != null) beast.teleport(new Location(bw, 0, 100, 0));
                }
            }
            return;
        }
        // Superviviente eliminado
        survivors.remove(uuid);
        super.eliminatePlayer(uuid);
    }

    @Override
    public void checkWinCondition() {
        // Custom win logic in onTick
    }

    @Override
    public void onCleanup() {
        if (progressBar != null) {
            progressBar.removeAll();
            progressBar.setVisible(false);
        }
        activatedLevers.clear();
        survivors.clear();
        leverLocations.clear();
        exitOpen = false;
        // Remove beast glow and scoreboard
        if (beastUUID != null) {
            Player beast = Bukkit.getPlayer(beastUUID);
            if (beast != null && beast.isOnline()) {
                beast.setGlowing(false);
            }
        }
        // Restaurar scoreboard por defecto a todos
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.setGlowing(false);
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
        if (beastTeam != null) {
            try { beastTeam.unregister(); } catch (Exception ignored) {}
        }
    }
}
