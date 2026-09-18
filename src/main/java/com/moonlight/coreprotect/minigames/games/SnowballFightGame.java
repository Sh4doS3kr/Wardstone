package com.moonlight.coreprotect.minigames.games;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.minigames.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Bolas de Nieve (Snowball Fight):
 * Todos los jugadores reciben bolas de nieve infinitas.
 * Cada bola de nieve que impacta hace 2 corazones de daño.
 * Último en pie gana. Arena nevada con cobertura.
 */
public class SnowballFightGame extends MiniGame {

    private static final int TIME_LIMIT = 180; // 3 minutos
    private static final double SNOWBALL_DAMAGE = 4.0; // 2 corazones
    private final Map<UUID, Integer> kills = new HashMap<>();

    public SnowballFightGame(CoreProtectPlugin plugin, MiniGameManager manager) {
        super(plugin, manager, MiniGameType.SNOWBALL_FIGHT);
    }

    @Override
    public void buildArena(World world) {
        ArenaBuilder.buildSnowballFight(world);
    }

    @Override
    public List<Location> getSpawnLocations(World world) {
        return ArenaBuilder.getSnowballFightSpawns(world);
    }

    @Override
    public void startGameLogic() {
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            giveKit(p);
            kills.put(uuid, 0);
        }

        broadcastGame("");
        broadcastGame("§b§l❄ §f§lBOLAS DE NIEVE §b§l❄");
        broadcastGame("§7Cada bola de nieve hace §c2 corazones §7de daño.");
        broadcastGame("§7¡Último en pie gana! Tienes §e3 minutos§7.");
        broadcastGame("");
    }

    private void giveKit(Player p) {
        p.getInventory().clear();
        // Bolas de nieve en todo el hotbar
        ItemStack snowballs = new ItemStack(Material.SNOWBALL, 16);
        ItemMeta meta = snowballs.getItemMeta();
        meta.setDisplayName("§b§l❄ Bola de Nieve");
        snowballs.setItemMeta(meta);
        for (int i = 0; i < 9; i++) {
            p.getInventory().setItem(i, snowballs.clone());
        }
        // Armadura de cuero azul
        ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
        org.bukkit.inventory.meta.LeatherArmorMeta hMeta = (org.bukkit.inventory.meta.LeatherArmorMeta) helmet.getItemMeta();
        hMeta.setColor(Color.AQUA);
        hMeta.setUnbreakable(true);
        helmet.setItemMeta(hMeta);
        p.getInventory().setHelmet(helmet);

        ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
        org.bukkit.inventory.meta.LeatherArmorMeta cMeta = (org.bukkit.inventory.meta.LeatherArmorMeta) chest.getItemMeta();
        cMeta.setColor(Color.AQUA);
        cMeta.setUnbreakable(true);
        chest.setItemMeta(cMeta);
        p.getInventory().setChestplate(chest);

        ItemStack legs = new ItemStack(Material.LEATHER_LEGGINGS);
        org.bukkit.inventory.meta.LeatherArmorMeta lMeta = (org.bukkit.inventory.meta.LeatherArmorMeta) legs.getItemMeta();
        lMeta.setColor(Color.AQUA);
        lMeta.setUnbreakable(true);
        legs.setItemMeta(lMeta);
        p.getInventory().setLeggings(legs);

        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);
        org.bukkit.inventory.meta.LeatherArmorMeta bMeta = (org.bukkit.inventory.meta.LeatherArmorMeta) boots.getItemMeta();
        bMeta.setColor(Color.AQUA);
        bMeta.setUnbreakable(true);
        boots.setItemMeta(bMeta);
        p.getInventory().setBoots(boots);
    }

    /**
     * Llamado desde MiniGameListener cuando una bola de nieve impacta a un jugador.
     */
    public void onSnowballHit(Player shooter, Player victim) {
        if (!running) return;
        if (!alivePlayers.contains(shooter.getUniqueId()) || !alivePlayers.contains(victim.getUniqueId())) return;

        victim.damage(SNOWBALL_DAMAGE);
        victim.getWorld().spawnParticle(Particle.SNOWFLAKE, victim.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.05);
        shooter.playSound(shooter.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 0.8f, 1.5f);

        // Si murió
        if (victim.getHealth() <= 0 || victim.isDead()) {
            kills.merge(shooter.getUniqueId(), 1, Integer::sum);
            broadcastGame("§b§l❄ §f" + shooter.getName() + " §7eliminó a §c" + victim.getName() + 
                " §8(§e" + kills.getOrDefault(shooter.getUniqueId(), 0) + " kills§8)");
        }

        // Reponer bolas de nieve al tirador
        refillSnowballs(shooter);
    }

    private void refillSnowballs(Player p) {
        for (int i = 0; i < 9; i++) {
            ItemStack item = p.getInventory().getItem(i);
            if (item == null || item.getType() == Material.AIR || item.getType() == Material.SNOWBALL) {
                ItemStack snowballs = new ItemStack(Material.SNOWBALL, 16);
                ItemMeta meta = snowballs.getItemMeta();
                meta.setDisplayName("§b§l❄ Bola de Nieve");
                snowballs.setItemMeta(meta);
                p.getInventory().setItem(i, snowballs);
            }
        }
    }

    @Override
    public void onTick() {
        // Reponer bolas de nieve cada 3 segundos
        if (gameTime % 3 == 0) {
            for (UUID uuid : alivePlayers) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    refillSnowballs(p);
                }
            }
        }

        // Info cada 30 segundos
        if (gameTime % 30 == 0 && gameTime > 0) {
            int remaining = TIME_LIMIT - gameTime;
            broadcastGame("§b§l❄ §7Tiempo: §e" + remaining + "s §7| §fVivos: §a" + alivePlayers.size());
        }

        // Tiempo límite
        if (gameTime >= TIME_LIMIT) {
            // El jugador con más kills gana
            UUID bestPlayer = null;
            int bestKills = -1;
            for (UUID uuid : alivePlayers) {
                int k = kills.getOrDefault(uuid, 0);
                if (k > bestKills) {
                    bestKills = k;
                    bestPlayer = uuid;
                }
            }
            if (bestPlayer != null) {
                Player winner = Bukkit.getPlayer(bestPlayer);
                broadcastGame("§b§l❄ §e¡Tiempo agotado! §f" + (winner != null ? winner.getName() : "???") + 
                    " §7gana con §e" + bestKills + " kills§7.");
                endGame(winner);
            } else {
                endGame(null);
            }
        }
    }

    @Override
    public void checkWinCondition() {
        if (alivePlayers.size() <= 1 && running) {
            if (alivePlayers.size() == 1) {
                UUID winnerUUID = alivePlayers.iterator().next();
                Player winner = Bukkit.getPlayer(winnerUUID);
                int k = kills.getOrDefault(winnerUUID, 0);
                broadcastGame("§b§l❄ §f" + (winner != null ? winner.getName() : "???") + 
                    " §7es el último en pie con §e" + k + " kills§7.");
                endGame(winner);
            } else {
                endGame(null);
            }
        }
    }

    @Override
    public void onCleanup() {
        kills.clear();
    }
}
