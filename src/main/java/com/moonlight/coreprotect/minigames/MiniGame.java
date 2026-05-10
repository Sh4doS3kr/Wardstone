package com.moonlight.coreprotect.minigames;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Clase base abstracta para todos los minijuegos.
 * Cada minijuego implementa su propia lógica, arena y mecánicas.
 */
public abstract class MiniGame {

    protected final CoreProtectPlugin plugin;
    protected final MiniGameManager manager;
    protected final MiniGameType type;
    protected final Set<UUID> players = new LinkedHashSet<>();
    protected final Set<UUID> alivePlayers = new LinkedHashSet<>();
    protected final Set<UUID> spectators = new LinkedHashSet<>();
    public Set<UUID> getSpectators() { return spectators; }
    protected boolean running = false;
    protected int taskId = -1;
    protected int gameTime = 0;
    public int getGameTime() { return gameTime; }
    protected Location spawnCenter;
    protected UUID pendingWinnerUUID = null;
    private List<Location> cachedSpawns = null;

    public MiniGame(CoreProtectPlugin plugin, MiniGameManager manager, MiniGameType type) {
        this.plugin = plugin;
        this.manager = manager;
        this.type = type;
    }

    public MiniGameType getType() { return type; }
    public boolean isRunning() { return running; }
    public Set<UUID> getPlayers() { return players; }
    public Set<UUID> getAlivePlayers() { return alivePlayers; }

    /**
     * Construye la arena del minijuego en el mundo.
     */
    public abstract void buildArena(World world);

    /**
     * Obtiene las posiciones de spawn para los jugadores.
     */
    public abstract List<Location> getSpawnLocations(World world);

    /**
     * Inicia la lógica del minijuego (llamado después del countdown de inicio).
     */
    public abstract void startGameLogic();

    /**
     * Hook llamado justo después de teleportar jugadores a la arena, ANTES del countdown.
     * Los juegos pueden sobreescribirlo para congelar jugadores, etc.
     */
    protected void onPreCountdown() { }

    /**
     * Lógica ejecutada cada tick (1 segundo).
     */
    public abstract void onTick();

    /**
     * Limpieza al terminar el juego.
     */
    public abstract void onCleanup();

    /**
     * Maneja la eliminación de un jugador.
     */
    public void eliminatePlayer(UUID uuid) {
        alivePlayers.remove(uuid);
        spectators.add(uuid);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null && p.isOnline()) {
            p.sendTitle("§c§l¡ELIMINADO!", "§7Ahora eres espectador", 10, 40, 10);
            p.setGameMode(org.bukkit.GameMode.SPECTATOR);
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_WITHER_DEATH, 0.5f, 1.5f);
            // Teleportar al centro de la arena para que no se quede en el vacío
            World w = Bukkit.getWorld(MiniGameWorld.getWorldName());
            if (w != null) {
                p.teleport(new Location(w, 0, 85, 0));
            }
        }
        
        broadcastGame("§c§l✖ §f" + (p != null ? p.getName() : "???") + " §7ha sido eliminado. §eQuedan §f" + alivePlayers.size());
        
        checkWinCondition();
    }

    /**
     * Verifica si hay un ganador (último en pie).
     */
    public void checkWinCondition() {
        if (alivePlayers.size() <= 1 && running) {
            if (alivePlayers.size() == 1) {
                UUID winnerUUID = alivePlayers.iterator().next();
                Player winner = Bukkit.getPlayer(winnerUUID);
                endGame(winner);
            } else {
                endGame(null);
            }
        }
    }

    /**
     * Inicia el minijuego completo (arena + countdown + juego).
     */
    public void start(Set<UUID> joinedPlayers) {
        this.players.addAll(joinedPlayers);
        this.alivePlayers.addAll(joinedPlayers);
        this.running = true;
        this.gameTime = 0;

        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (world == null) return;

        // Teletransportar jugadores a una ubicación segura ANTES de construir
        // Esto previene que caigan al vacío si el mapa tarda en construirse (TPS bajos)
        Location safeWaitLocation = new Location(world, 0.5, 120, 0.5);
        for (UUID uuid : joinedPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.teleport(safeWaitLocation);
                p.setGameMode(org.bukkit.GameMode.SPECTATOR);
                p.setAllowFlight(true);
                p.setFlying(true);
                p.sendMessage("§e§l⏳ §7Construyendo arena, espera...");
            }
        }

        // Construir arena
        buildArena(world);

        // Pre-cargar chunks y obtener spawns
        List<Location> spawns = getSpawnLocations(world);
        cachedSpawns = spawns;
        // Force-load todos los chunks de spawn con plugin ticket (no se descargan)
        for (Location spawn : spawns) {
            world.getChunkAt(spawn).load(true);
            world.getChunkAt(spawn).setForceLoaded(true);
        }
        // Cargar chunk central también
        world.getChunkAt(0, 0).load(true);
        world.getChunkAt(0, 0).setForceLoaded(true);

        // Esperar 5 segundos para que la arena se construya completamente (especialmente con TPS bajos)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Verificar chunks una vez más antes de TP
            for (Location spawn : spawns) {
                if (!world.getChunkAt(spawn).isLoaded()) {
                    world.getChunkAt(spawn).load(true);
                }
            }

            // Arena construida, ahora sí teletransportar a los spawns reales
            teleportPlayersToArena(world, spawns);
            onPreCountdown();

            // Safety retry: 1 segundo después, re-TP a quien no esté en el mundo
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (UUID uuid : players) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline() && !p.getWorld().getName().equals(MiniGameWorld.getWorldName())) {
                        int idx2 = new ArrayList<>(players).indexOf(uuid);
                        Location spawn = spawns.get(idx2 % spawns.size());
                        spawn.getChunk().load(true);
                        p.teleport(spawn);
                        p.setGameMode(org.bukkit.GameMode.SURVIVAL);
                        p.setFallDistance(0);
                        plugin.getLogger().warning("[MiniGames] Safety TP (start): " + p.getName() + " no estaba en mundo minijuegos.");
                    }
                }
            }, 20L);

            // Countdown de inicio (5 segundos)
            new BukkitRunnable() {
                int count = 5;
                @Override
                public void run() {
                    if (!running) { cancel(); return; }
                    if (count <= 0) {
                        cancel();
                        // Liberar force-load de chunks
                        for (Location spawn : spawns) {
                            world.getChunkAt(spawn).setForceLoaded(false);
                        }
                        world.getChunkAt(0, 0).setForceLoaded(false);

                        for (UUID uuid : players) {
                            Player p = Bukkit.getPlayer(uuid);
                            if (p != null && p.isOnline()) {
                                p.sendTitle("§a§l¡GO!", "", 5, 20, 5);
                                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);
                            }
                        }
                        startGameLogic();
                        startTickTask();
                        return;
                    }

                    String color = count <= 2 ? "§c" : count <= 4 ? "§e" : "§a";
                    for (UUID uuid : players) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null && p.isOnline()) {
                            p.sendTitle(color + "§l" + count, "§7Prepárate...", 5, 15, 5);
                            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, count == 1 ? 2.0f : 1.0f);
                        }
                    }
                    count--;
                }
            }.runTaskTimer(plugin, 0L, 20L);
        }, 100L);
    }

    private void teleportPlayersToArena(World world, List<Location> spawns) {
        int idx = 0;
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                // Cerrar cualquier panel/inventario abierto (tienda, cofre, etc.)
                p.closeInventory();

                Location spawn = spawns.get(idx % spawns.size());
                spawn.getChunk().load(true);

                // Quitar fly ANTES del TP para evitar caídas en el mundo anterior
                p.setAllowFlight(false);
                p.setFlying(false);
                p.setFallDistance(0);

                // Teleportar
                p.teleport(spawn);

                // Limpiar inventario DESPUÉS del TP (evita exploit de dropear items)
                p.getInventory().clear();
                p.getInventory().setArmorContents(new org.bukkit.inventory.ItemStack[4]);
                p.getInventory().setItemInOffHand(null);

                // Reset estado completo
                p.setGameMode(org.bukkit.GameMode.SURVIVAL);
                p.setHealth(p.getMaxHealth());
                p.setFoodLevel(20);
                p.setSaturation(20f);
                p.setFireTicks(0);
                p.setFallDistance(0);
                p.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
                idx++;
            }
        }
    }

    private void startTickTask() {
        taskId = new BukkitRunnable() {
            @Override
            public void run() {
                if (!running) {
                    cancel();
                    return;
                }
                gameTime++;
                checkPlayersInCorrectWorld();
                onTick();
            }
        }.runTaskTimer(plugin, 20L, 20L).getTaskId();
    }

    /**
     * Bug 5 fix: Si un participante vivo está en el mundo normal (ej: anticheat lo teletransportó),
     * forzar TP de vuelta al mundo de minijuegos.
     */
    private void checkPlayersInCorrectWorld() {
        for (UUID uuid : new ArrayList<>(alivePlayers)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;
            if (!p.getWorld().getName().equals(MiniGameWorld.getWorldName())) {
                // Jugador está fuera del mundo de minijuegos, forzar TP
                World mgWorld = Bukkit.getWorld(MiniGameWorld.getWorldName());
                if (mgWorld != null && cachedSpawns != null && !cachedSpawns.isEmpty()) {
                    int idx = new ArrayList<>(players).indexOf(uuid);
                    Location spawn = cachedSpawns.get(Math.max(0, idx) % cachedSpawns.size());
                    spawn.getChunk().load(true);
                    p.teleport(spawn);
                    p.setFallDistance(0);
                    p.setGameMode(org.bukkit.GameMode.SURVIVAL);
                    p.sendMessage("§c§l⚠ §7Has sido teletransportado de vuelta al minijuego.");
                    plugin.getLogger().warning("[MiniGames] Ghost TP fix: " + p.getName() + " estaba en " + p.getWorld().getName());
                } else {
                    // Fallback: TP al centro del mundo de minijuegos
                    if (mgWorld != null) {
                        p.teleport(new Location(mgWorld, 0, 85, 0));
                        p.setFallDistance(0);
                    }
                }
            }
        }
    }

    /**
     * Finaliza el juego y da recompensas.
     */
    public void endGame(Player winner) {
        running = false;
        
        if (taskId != -1) {
            try { Bukkit.getScheduler().cancelTask(taskId); } catch (Exception ignored) {}
            taskId = -1;
        }

        if (winner != null) {
            // Guardar UUID del ganador para dar recompensas DESPUÉS del TP
            pendingWinnerUUID = winner.getUniqueId();

            // Anunciar ganador
            String msg = type.getColor() + "§l⚔ §e§l¡" + winner.getName() + " HA GANADO " + type.getDisplayName() + "§e§l! §" + type.getColor() + "§l⚔";
            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            Bukkit.broadcastMessage(msg);
            Bukkit.broadcastMessage("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            Bukkit.broadcastMessage("");

            // Title a todos
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendTitle(type.getColor() + "§l¡VICTORIA!", "§e" + winner.getName() + " §7gana " + type.getDisplayName(), 10, 60, 20);
                p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }

            // NO dar recompensas aquí — se darán después del TP al mundo normal
        } else {
            pendingWinnerUUID = null;
            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            Bukkit.broadcastMessage(type.getColor() + "§l⚔ §7El minijuego " + type.getDisplayName() + " §7ha terminado sin ganador.");
            Bukkit.broadcastMessage("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            Bukkit.broadcastMessage("");
        }

        // Devolver jugadores tras 5 segundos, recompensas se dan DESPUÉS del TP
        new BukkitRunnable() {
            @Override
            public void run() {
                onCleanup();
                manager.teleportAllBack();
                manager.endCurrentGame();
            }
        }.runTaskLater(plugin, 100L);
    }

    /**
     * Da recompensas al ganador: 6 Esencias, dinero e items.
     */
    /**
     * Da recompensas al ganador. Package-private para que MiniGameManager pueda llamarlo
     * DESPUÉS de teleportar al jugador de vuelta al mundo normal.
     */
    void giveRewards(Player winner) {
        plugin.getLogger().info("[MiniGames] Giving rewards to: " + winner.getName());
        
        // 1 Esencia
        if (plugin.getEssenceManager() != null) {
            plugin.getEssenceManager().addEssences(winner.getUniqueId(), 1);
            plugin.getEssenceManager().saveData();
            winner.sendMessage(com.moonlight.coreprotect.util.SmallCaps.convert("§a§l+1 Esencia §7por ganar el minijuego."));
            plugin.getLogger().info("[MiniGames] Gave 1 essence to " + winner.getName());
        }

        // Dinero (25,000)
        if (plugin.getEconomy() != null) {
            plugin.depositWithMultiplier(winner, 25000);
            winner.sendMessage(com.moonlight.coreprotect.util.SmallCaps.convert("§6§l$ §a+$25,000 §7por ganar el minijuego."));
            plugin.getLogger().info("[MiniGames] Gave $25,000 to " + winner.getName());
        }

        // Items: 3 bloques de diamante + 1 llave común
        org.bukkit.inventory.ItemStack diamonds = new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND_BLOCK, 3);
        
        // Asegurar espacio en inventario
        java.util.Map<Integer, org.bukkit.inventory.ItemStack> remaining = winner.getInventory().addItem(diamonds);
        if (!remaining.isEmpty()) {
            // Si no hay espacio, tirar los items en el suelo
            for (org.bukkit.inventory.ItemStack item : remaining.values()) {
                winner.getWorld().dropItemNaturally(winner.getLocation(), item);
            }
            winner.sendMessage("§c§l⚠ §7Tu inventario estaba lleno. Los items han caído al suelo.");
        }
        
        // Dar una llave común ejecutando comando
        String keyCommand = "excellentcrates key give " + winner.getName() + " common 1";
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), keyCommand);
        plugin.getLogger().info("[MiniGames] Executed key command: " + keyCommand);
        
        winner.sendMessage(com.moonlight.coreprotect.util.SmallCaps.convert("§b§l+3 Bloques de Diamante §7+ §e1 Llave Común"));
        plugin.getLogger().info("[MiniGames] Rewards given successfully to " + winner.getName());
    }

    /**
     * Permite dar recompensas a múltiples jugadores de forma diferida (para juegos en equipo).
     */
    protected void giveDelayedRewards(List<UUID> winners, long delayTicks) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (UUID uuid : winners) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    giveRewards(p);
                }
            }
        }, delayTicks);
    }

    public UUID getPendingWinnerUUID() {
        return pendingWinnerUUID;
    }

    public MiniGameType getGameType() {
        return type;
    }

    /**
     * Fuerza el fin del juego.
     */
    public void forceStop() {
        running = false;
        if (taskId != -1) {
            try { Bukkit.getScheduler().cancelTask(taskId); } catch (Exception ignored) {}
            taskId = -1;
        }
        onCleanup();
        manager.teleportAllBack();
        manager.endCurrentGame();
        Bukkit.broadcastMessage(type.getColor() + "§l⚔ §cEl minijuego " + type.getDisplayName() + " §cha sido detenido forzosamente.");
    }

    public void broadcastGame(String message) {
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendMessage(message);
            }
        }
    }

    protected void titleAll(String title, String subtitle) {
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendTitle(title, subtitle, 5, 30, 5);
            }
        }
    }

    protected void titleAlive(String title, String subtitle) {
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendTitle(title, subtitle, 5, 30, 5);
            }
        }
    }

    protected void soundAll(org.bukkit.Sound sound, float volume, float pitch) {
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.playSound(p.getLocation(), sound, volume, pitch);
            }
        }
    }
}
