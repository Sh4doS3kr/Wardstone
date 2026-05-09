package com.moonlight.coreprotect.minigames.games;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.minigames.*;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * OITC (One in the Chamber): Todos tienen un arco, una espada de madera y 1 sola flecha.
 * Un flechazo = Kill instantánea. Si matas a alguien, recuperas tu flecha. Si fallas, toca ir a espada.
 * El primero en llegar a 15 kills o el que más kills tenga tras 5 minutos gana.
 */
public class OITCGame extends MiniGame {

    private final Map<UUID, Integer> kills = new HashMap<>();
    private final Set<UUID> invincible = new HashSet<>();
    private static final int KILLS_TO_WIN = 15;
    private static final int TIME_LIMIT = 300; // 5 minutos

    public OITCGame(CoreProtectPlugin plugin, MiniGameManager manager) {
        super(plugin, manager, MiniGameType.OITC);
    }

    @Override
    public void buildArena(World world) {
        ArenaBuilder.buildOITC(world);
    }

    @Override
    public List<Location> getSpawnLocations(World world) {
        return ArenaBuilder.getOITCSpawns(world);
    }

    @Override
    public void startGameLogic() {
        for (UUID uuid : alivePlayers) {
            kills.put(uuid, 0);
            giveKit(uuid);
        }
    }

    public void giveKit(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p == null || !p.isOnline()) return;

        p.getInventory().clear();

        // Espada de madera
        ItemStack sword = new ItemStack(Material.WOODEN_SWORD);
        ItemMeta swordMeta = sword.getItemMeta();
        swordMeta.setDisplayName("§e§lEspada OITC");
        swordMeta.setUnbreakable(true);
        sword.setItemMeta(swordMeta);

        // Arco
        ItemStack bow = new ItemStack(Material.BOW);
        ItemMeta bowMeta = bow.getItemMeta();
        bowMeta.setDisplayName("§b§l⏐ Arco OITC ⏐");
        bowMeta.addEnchant(Enchantment.INFINITY, 1, true);
        bowMeta.setUnbreakable(true);
        bow.setItemMeta(bowMeta);

        // 1 flecha
        ItemStack arrow = new ItemStack(Material.ARROW, 1);

        p.getInventory().setItem(0, sword);
        p.getInventory().setItem(1, bow);
        p.getInventory().setItem(9, arrow); // Slot de flecha
    }

    /**
     * Llamado cuando un jugador mata a otro.
     */
    public void onKill(UUID killer, UUID victim) {
        if (!running) return;

        int newKills = kills.getOrDefault(killer, 0) + 1;
        kills.put(killer, newKills);

        Player killerPlayer = Bukkit.getPlayer(killer);
        Player victimPlayer = Bukkit.getPlayer(victim);

        if (killerPlayer != null) {
            killerPlayer.sendTitle("§a§l+" + newKills, "§7kills", 0, 15, 5);
            killerPlayer.playSound(killerPlayer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
            
            // Dar flecha al killer
            killerPlayer.getInventory().addItem(new ItemStack(Material.ARROW, 1));
        }

        broadcastGame("§b§l⏐ §f" + (killerPlayer != null ? killerPlayer.getName() : "???") + 
            " §7mató a §c" + (victimPlayer != null ? victimPlayer.getName() : "???") + 
            " §8(§e" + newKills + "/" + KILLS_TO_WIN + "§8)");

        // Respawnear víctima lejos de todos + 3s invencible
        if (victimPlayer != null && victimPlayer.isOnline()) {
            World w = Bukkit.getWorld(MiniGameWorld.getWorldName());
            if (w != null) {
                Location spawn = getFarthestSpawn(w);
                victimPlayer.teleport(spawn);
                victimPlayer.setHealth(victimPlayer.getMaxHealth());
                giveKit(victim);
                // 3 segundos de invencibilidad
                invincible.add(victim);
                victimPlayer.sendTitle("§a§l¡RESPAWN!", "§e3s de invencibilidad", 0, 40, 10);
                victimPlayer.setGlowing(true);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    invincible.remove(victim);
                    Player p = Bukkit.getPlayer(victim);
                    if (p != null && p.isOnline()) {
                        p.setGlowing(false);
                        p.sendTitle("", "§c¡Ya eres vulnerable!", 0, 20, 5);
                    }
                }, 60L); // 3 segundos
            }
        }

        // Comprobar victoria
        if (newKills >= KILLS_TO_WIN) {
            endGame(killerPlayer);
        }
    }

    /**
     * Busca el spawn más lejano de todos los jugadores vivos.
     */
    private Location getFarthestSpawn(World w) {
        List<Location> spawns = getSpawnLocations(w);
        Location best = spawns.get(0);
        double bestDist = -1;

        for (Location spawn : spawns) {
            double minDist = Double.MAX_VALUE;
            for (UUID uuid : alivePlayers) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline() && p.getWorld().equals(w)) {
                    double dist = p.getLocation().distanceSquared(spawn);
                    if (dist < minDist) minDist = dist;
                }
            }
            if (minDist > bestDist) {
                bestDist = minDist;
                best = spawn;
            }
        }
        return best;
    }

    public boolean isInvincible(UUID uuid) {
        return invincible.contains(uuid);
    }

    @Override
    public void onTick() {
        // Tiempo límite
        if (gameTime >= TIME_LIMIT) {
            // Ganador = el que más kills tenga
            UUID bestPlayer = null;
            int bestKills = 0;
            for (Map.Entry<UUID, Integer> entry : kills.entrySet()) {
                if (entry.getValue() > bestKills) {
                    bestKills = entry.getValue();
                    bestPlayer = entry.getKey();
                }
            }
            Player winner = bestPlayer != null ? Bukkit.getPlayer(bestPlayer) : null;
            endGame(winner);
            return;
        }

        // Info cada 30 segundos
        if (gameTime % 30 == 0 && gameTime > 0) {
            int remaining = TIME_LIMIT - gameTime;
            broadcastGame("§b§lOITC §7| §fTiempo: §e" + remaining + "s §7| §fJugadores: §a" + alivePlayers.size());
            
            // Mostrar top 3
            List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(kills.entrySet());
            sorted.sort((a, b) -> b.getValue() - a.getValue());
            StringBuilder top = new StringBuilder("§7Top: ");
            for (int i = 0; i < Math.min(3, sorted.size()); i++) {
                Player p = Bukkit.getPlayer(sorted.get(i).getKey());
                if (p != null) {
                    top.append(i == 0 ? "§e" : "§7").append(p.getName())
                        .append(" §f(").append(sorted.get(i).getValue()).append(") ");
                }
            }
            broadcastGame(top.toString());
        }

        // Aviso de 1 minuto
        if (gameTime == TIME_LIMIT - 60) {
            titleAlive("§c§l¡1 MINUTO!", "§7El que más kills tenga gana");
            soundAll(Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
        }
    }

    @Override
    public void checkWinCondition() {
        // En OITC no se elimina por morir, se respawnea
    }

    @Override
    public void onCleanup() {
        kills.clear();
        invincible.clear();
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.setGlowing(false);
            }
        }
    }
}
