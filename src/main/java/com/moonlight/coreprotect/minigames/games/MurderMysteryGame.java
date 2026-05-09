package com.moonlight.coreprotect.minigames.games;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.minigames.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;

import java.util.*;

/**
 * Murder Mystery:
 * 1 Asesino, 1 Sheriff, resto Inocentes.
 * - Asesino: Tiene espada de hierro, 1-hit kill. Gana si mata a todos.
 * - Sheriff: Tiene arco infinito, 1-hit kill. Pierde/Muere si mata a un inocente.
 * - Inocentes: Deben sobrevivir. Aparece oro; con 10 oros consiguen un arco con 1 flecha.
 */
public class MurderMysteryGame extends MiniGame {

    private UUID murdererUUID;
    private UUID sheriffUUID;
    private final Set<UUID> innocents = new HashSet<>();
    private final Map<UUID, Integer> playerGold = new HashMap<>();
    
    // Team para ocultar nametags
    private org.bukkit.scoreboard.Team hiddenNametagsTeam;
    
    // BossBar para el tiempo
    private org.bukkit.boss.BossBar timerBar;

    // Oros soltados en el suelo
    private final Set<org.bukkit.entity.Item> droppedGold = new HashSet<>();

    private static final int GOLD_REQUIRED_FOR_BOW = 10;
    private static final int GAME_DURATION_SECONDS = 300; // 5 minutos de tiempo límite
    private static final Random RANDOM = new Random();

    public MurderMysteryGame(CoreProtectPlugin plugin, MiniGameManager manager) {
        super(plugin, manager, MiniGameType.MURDER_MYSTERY);
    }

    @Override
    public void buildArena(World world) {
        ArenaBuilder.buildMurderMystery(world);
    }

    @Override
    public List<Location> getSpawnLocations(World world) {
        return ArenaBuilder.getMurderMysterySpawns(world);
    }

    @Override
    public void startGameLogic() {
        if (alivePlayers.size() < 3) {
            broadcastGame("§c§l✖ §cNo hay suficientes jugadores para Murder Mystery. (Mínimo 3)");
            endGame(null); // Terminar sin ganador
            return;
        }

        List<UUID> players = new ArrayList<>(alivePlayers);
        Collections.shuffle(players);

        // Asignar roles
        murdererUUID = players.get(0);
        sheriffUUID = players.get(1);

        for (int i = 2; i < players.size(); i++) {
            innocents.add(players.get(i));
        }

        playerGold.clear();

        // Configurar Scoreboard custom para ocultar nametags por encima de todo
        org.bukkit.scoreboard.ScoreboardManager sm = Bukkit.getScoreboardManager();
        if (sm != null) {
            org.bukkit.scoreboard.Scoreboard gameScoreboard = sm.getNewScoreboard();
            hiddenNametagsTeam = gameScoreboard.registerNewTeam("mm_hidden");
            hiddenNametagsTeam.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY, org.bukkit.scoreboard.Team.OptionStatus.NEVER);
        }

        // Equipar e informar
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.getInventory().clear();
                // Limpiar TODOS los efectos de pociones (invisibilidad, etc.)
                for (org.bukkit.potion.PotionEffect effect : p.getActivePotionEffects()) {
                    p.removePotionEffect(effect.getType());
                }
                playerGold.put(uuid, 0);

                if (uuid.equals(murdererUUID)) {
                    p.sendTitle("§c§lASESINO", "§7Mata a todos en secreto", 10, 70, 20);
                    p.sendMessage("§c§lEres el ASESINO. §7Oculta tu espada y acaba con todos.");
                    giveMurdererWeapon(p);
                } else if (uuid.equals(sheriffUUID)) {
                    p.sendTitle("§b§lSHERIFF", "§7Encuentra y elimina al asesino", 10, 70, 20);
                    p.sendMessage("§b§lEres el SHERIFF. §7Protege a los inocentes. Si matas a un inocente, tú también morirás.");
                    giveSheriffWeapon(p);
                } else {
                    p.sendTitle("§a§lINOCENTE", "§7Sobrevive y recoge oro", 10, 70, 20);
                    p.sendMessage("§a§lEres un INOCENTE. §7Sobrevive. Si consigues " + GOLD_REQUIRED_FOR_BOW + " oros obtendrás un arco.");
                }
                
                
                // Añadir al team para ocultar nametags y forzar scoreboard
                if (hiddenNametagsTeam != null) {
                    p.setScoreboard(hiddenNametagsTeam.getScoreboard());
                    hiddenNametagsTeam.addEntry(p.getName());
                }
            }
        }

        // Inicializar BossBar
        timerBar = Bukkit.createBossBar("§e§lTIEMPO RESTANTE", BarColor.YELLOW, BarStyle.SOLID);
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) timerBar.addPlayer(p);
        }
        timerBar.setVisible(true);
    }

    @Override
    public void onTick() {
        if (!running) return;

        World w = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (w == null) return;

        // Limpiar oros eliminados de la lista
        droppedGold.removeIf(item -> !item.isValid() || item.isDead());

        // Aparecer oro cada pocos segundos (1 oro cada 3 segundos)
        if (gameTime % 3 == 0) {
            spawnGoldItem(w);
        }

        // Action bars con información de roles y progreso
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                if (uuid.equals(murdererUUID)) {
                    com.moonlight.coreprotect.util.ActionBarUtil.send(p, "§c§lROL: ASESINO §8| §fObjetivo: Matar a todos");
                } else if (uuid.equals(sheriffUUID)) {
                    com.moonlight.coreprotect.util.ActionBarUtil.send(p, "§b§lROL: SHERIFF §8| §fObjetivo: Matar al asesino");
                } else {
                    int gold = playerGold.getOrDefault(uuid, 0);
                    com.moonlight.coreprotect.util.ActionBarUtil.send(p, "§a§lROL: INOCENTE §8| §eOro recogido: " + gold + "/" + GOLD_REQUIRED_FOR_BOW);
                }
            }
        }

        int remainingSeconds = GAME_DURATION_SECONDS - gameTime;
        
        // Actualizar BossBar
        if (timerBar != null) {
            double progress = Math.max(0.0, Math.min(1.0, (double) remainingSeconds / GAME_DURATION_SECONDS));
            timerBar.setProgress(progress);
            String timeStr = String.format("%02d:%02d", remainingSeconds / 60, remainingSeconds % 60);
            timerBar.setTitle("§e§lTIEMPO: §f§l" + timeStr);
            
            // Cambiar color según urgencia
            if (remainingSeconds <= 30) timerBar.setColor(BarColor.RED);
            else if (remainingSeconds <= 60) timerBar.setColor(BarColor.PINK);
        }

        // Avisos de tiempo
        if (remainingSeconds == 60 || remainingSeconds == 30 || remainingSeconds <= 10) {
            broadcastGame("§e§l⏳ §fTiempo restante: §c" + remainingSeconds + " segundos");
        }

        // Comprobar victoria por tiempo (Ganan los Inocentes)
        if (remainingSeconds <= 0) {
            broadcastGame("§a§l¡SE HA ACABADO EL TIEMPO! §fEl asesino no logró matar a todos.");
            innocentsWin();
            return;
        }

        checkWinConditions();
    }

    @Override
    public void eliminatePlayer(UUID uuid) {
        alivePlayers.remove(uuid);
        spectators.add(uuid);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null && p.isOnline()) {
            p.sendTitle("§c§l¡ELIMINADO!", "§7Ahora eres espectador", 10, 40, 10);
            p.setGameMode(org.bukkit.GameMode.SPECTATOR);
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_WITHER_DEATH, 0.5f, 1.5f);
            World w = Bukkit.getWorld(MiniGameWorld.getWorldName());
            if (w != null) {
                p.teleport(new Location(w, 0, 85, 0));
            }
        }
        // SIN BROADCAST PARA MURDER MYSTERY
        checkWinConditions();
    }

    private void checkWinConditions() {
        if (!running) return;

        boolean murdererAlive = alivePlayers.contains(murdererUUID);
        
        // Si el asesino está muerto, ganan los inocentes
        if (!murdererAlive) {
            broadcastGame("§a§l¡EL ASESINO HA MUERTO! §fLos inocentes y el sheriff ganan la partida.");
            innocentsWin();
            return;
        }

        // Si solo queda el asesino vivo (o si no quedan inocentes ni sheriff)
        List<UUID> others = new ArrayList<>(alivePlayers);
        others.remove(murdererUUID);
        
        if (others.isEmpty()) {
            broadcastGame("§c§l¡EL ASESINO HA GANADO! §fNo quedan supervivientes.");
            endGame(Bukkit.getPlayer(murdererUUID));
        }
    }

    private void innocentsWin() {
        endGame(null); // Se dan recompensas por separado
        
        List<UUID> winners = new ArrayList<>();
        // Puedes repartir recompensas a todos los inocentes vivos + sheriff
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && !uuid.equals(murdererUUID)) { // Si no es el asesino
                winners.add(uuid);
            }
        }
        
        // Retraso de 140 ticks (100 para volver al overworld + 40 de safety compensación)
        giveDelayedRewards(winners, 140L);
    }

    // ===================================
    // API PUBLICA PARA EL LISTENER
    // ===================================

    public boolean isMurderer(UUID uuid) {
        return uuid.equals(murdererUUID);
    }

    public boolean isSheriff(UUID uuid) {
        return uuid.equals(sheriffUUID);
    }

    public boolean isInnocent(UUID uuid) {
        return innocents.contains(uuid);
    }

    /**
     * Da un arco y 1 flecha al jugador.
     */
    public void giveBow(Player player) {
        ItemStack bow = new ItemStack(Material.BOW);
        ItemMeta meta = bow.getItemMeta();
        meta.setDisplayName("§b§lArco del Héroe");
        meta.setUnbreakable(true);
        bow.setItemMeta(meta);
        
        player.getInventory().addItem(bow);
        player.getInventory().setItem(9, new ItemStack(Material.ARROW, 1)); // Flecha
        
        player.sendTitle("§b§l¡ARMA CONSEGUIDA!", "§7Tienes un arco, mata al asesino.", 5, 40, 5);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
    }

    public void pickupGold(Player player, org.bukkit.entity.Item goldItem) {
        if (isInnocent(player.getUniqueId()) || isSheriff(player.getUniqueId())) {
            UUID uid = player.getUniqueId();
            int currentGold = playerGold.getOrDefault(uid, 0) + 1;
            
            goldItem.remove();
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

            if (isInnocent(uid)) {
                playerGold.put(uid, currentGold);
                if (currentGold >= GOLD_REQUIRED_FOR_BOW) {
                    playerGold.put(uid, 0);
                    // 10 oros = 1 esencia + arco
                    giveBow(player);
                    // Dar 1 esencia
                    if (plugin.getEssenceManager() != null) {
                        plugin.getEssenceManager().addEssences(uid, 1);
                        player.sendMessage("§d§l✦ §a+1 Esencia §7por recoger " + GOLD_REQUIRED_FOR_BOW + " oros.");
                    }
                }
            }
        }
    }

    public void spawnDroppedBow(Location loc) {
        ItemStack bow = new ItemStack(Material.BOW);
        ItemMeta meta = bow.getItemMeta();
        meta.setDisplayName("§b§lArco de Sheriff Perdido");
        meta.setUnbreakable(true);
        bow.setItemMeta(meta);
        
        org.bukkit.entity.Item dropped = loc.getWorld().dropItem(loc, bow);
        dropped.setGlowing(true);
        
        broadcastGame("§b§l¡El Sheriff ha muerto! ¡Su arco está en el suelo!");
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (alivePlayers.contains(p.getUniqueId())) {
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.0f);
            }
        }
    }

    public void pickupSheriffBow(Player player, org.bukkit.entity.Item bowItem) {
        if (isInnocent(player.getUniqueId())) {
            bowItem.remove();
            giveBow(player);
            // Cambiar su rol a "Héroe/Sheriff"? Para simplificar, le damos el arco.
            broadcastGame("§e§l¡Un Inocente ha recogido el arco!");
        }
    }

    // ===================================
    // METODOS INTERNOS
    // ===================================

    private void giveMurdererWeapon(Player p) {
        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = sword.getItemMeta();
        meta.setDisplayName("§c§lCuchilla del Asesino");
        meta.setUnbreakable(true);
        sword.setItemMeta(meta);
        p.getInventory().setItem(1, sword);
    }

    private void giveSheriffWeapon(Player p) {
        ItemStack bow = new ItemStack(Material.BOW);
        ItemMeta meta = bow.getItemMeta();
        meta.setDisplayName("§b§lArco del Sheriff");
        meta.setUnbreakable(true);
        meta.addEnchant(org.bukkit.enchantments.Enchantment.INFINITY, 1, true);
        bow.setItemMeta(meta);
        
        p.getInventory().setItem(1, bow);
        p.getInventory().setItem(9, new ItemStack(Material.ARROW, 1)); // Solo 1 flecha, tiene infinidad
    }

    private void spawnGoldItem(World w) {
        if (alivePlayers.isEmpty()) return;
        
        // Escoger un jugador vivo al azar y spawnear cerca de él
        List<UUID> aliveList = new ArrayList<>(alivePlayers);
        UUID randomPlayer = aliveList.get(RANDOM.nextInt(aliveList.size()));
        Player p = Bukkit.getPlayer(randomPlayer);
        
        if (p != null && p.isOnline()) {
            Location pLoc = p.getLocation();
            int offsetX = (RANDOM.nextInt(15) - 7);
            int offsetZ = (RANDOM.nextInt(15) - 7);
            
            int rx = pLoc.getBlockX() + offsetX;
            int rz = pLoc.getBlockZ() + offsetZ;
            
            // Comenzar desde la posición del jugador y buscar hacia abajo para encontrar aire sobre suelo
            // Esto evita que el oro aparezca en el techo
            int startY = pLoc.getBlockY();
            int y = startY;
            boolean found = false;
            
            // Buscar hacia abajo hasta 10 bloques
            for (int i = 0; i < 10; i++) {
                if (w.getBlockAt(rx, y, rz).getType() == Material.AIR && w.getBlockAt(rx, y - 1, rz).getType().isSolid()) {
                    found = true;
                    break;
                }
                y--;
            }
            
            // Si no encontró hacia abajo, buscar hacia arriba (por si está bajo tierra)
            if (!found) {
                y = startY;
                for (int i = 0; i < 5; i++) {
                    if (w.getBlockAt(rx, y, rz).getType() == Material.AIR && w.getBlockAt(rx, y - 1, rz).getType().isSolid()) {
                        found = true;
                        break;
                    }
                    y++;
                }
            }
            
            if (found) {
                Location loc = new Location(w, rx + 0.5, y + 0.5, rz + 0.5);
                org.bukkit.entity.Item gold = w.dropItem(loc, new ItemStack(Material.GOLD_INGOT));
                gold.setPickupDelay(10); // Medio segundo antes de que pueda recogerse
                droppedGold.add(gold);
            }
        }
    }

    @Override
    public void onCleanup() {
        for (org.bukkit.entity.Item item : droppedGold) {
            if (item != null && item.isValid()) {
                item.remove();
            }
        }
        droppedGold.clear();
        
        // Quitar a los jugadores del team y restaurar scoreboard principal
        org.bukkit.scoreboard.ScoreboardManager sm = Bukkit.getScoreboardManager();
        if (hiddenNametagsTeam != null && sm != null) {
            for (UUID uuid : players) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    hiddenNametagsTeam.removeEntry(p.getName());
                    p.setScoreboard(sm.getMainScoreboard());
                }
            }
        }

        if (timerBar != null) {
            timerBar.removeAll();
            timerBar.setVisible(false);
            timerBar = null;
        }
    }
}
