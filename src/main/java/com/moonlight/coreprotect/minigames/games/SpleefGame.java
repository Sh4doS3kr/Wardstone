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
 * Spleef: Todos tienen una pala con Eficiencia en una plataforma de nieve.
 * Rompe la nieve debajo de los rivales para que caigan. 3 CAPAS de nieve separadas por 7 de aire.
 * Cada 30 segundos el círculo se cierra (se reduce el radio).
 * Los jugadores cerca del borde son avisados antes de que se cierre.
 */
public class SpleefGame extends MiniGame {

    private static final int ARENA_RADIUS = 25;
    private static final int SHRINK_INTERVAL = 30; // cada 30 segundos
    private static final int SHRINK_AMOUNT = 2;    // bloques que se reduce el radio
    private static final int WARNING_SECONDS = 5;  // aviso 5 segundos antes
    private static final int WARNING_DISTANCE = 3; // distancia al nuevo borde para avisar

    private int currentRadius = ARENA_RADIUS;      // radio actual del círculo
    private boolean warningSent = false;            // si ya se envió el aviso de esta fase

    public SpleefGame(CoreProtectPlugin plugin, MiniGameManager manager) {
        super(plugin, manager, MiniGameType.SPLEEF);
    }

    @Override
    public void buildArena(World world) {
        ArenaBuilder.buildSpleef(world);
    }

    @Override
    public List<Location> getSpawnLocations(World world) {
        return ArenaBuilder.getSpleefSpawns(world);
    }

    @Override
    public void startGameLogic() {
        // Dar pala con eficiencia a todos
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.getInventory().clear();
                ItemStack shovel = new ItemStack(Material.DIAMOND_SHOVEL);
                ItemMeta meta = shovel.getItemMeta();
                meta.setDisplayName("§b§l❄ Pala de Spleef ❄");
                meta.addEnchant(Enchantment.EFFICIENCY, 5, true);
                meta.setUnbreakable(true);
                shovel.setItemMeta(meta);
                p.getInventory().addItem(shovel);

                // Bolas de nieve para empujar rivales
                p.getInventory().setItem(1, new ItemStack(Material.SNOWBALL, 16));
            }
        }
    }

    @Override
    public void onTick() {
        World w = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (w == null) return;

        // Comprobar jugadores que cayeron al agua o tocaron agua
        for (UUID uuid : new ArrayList<>(alivePlayers)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;

            Location loc = p.getLocation();
            // Eliminar si está en agua o cayó por debajo de las capas
            if (loc.getBlock().getType() == Material.WATER ||
                w.getBlockAt(loc.getBlockX(), loc.getBlockY() - 1, loc.getBlockZ()).getType() == Material.WATER ||
                loc.getY() < 75) { // Agua está en Y=74, eliminar si cae por debajo
                eliminatePlayer(uuid);
                p.sendTitle("§c§l¡CAÍSTE!", "§7Tocaste el agua", 5, 20, 5);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);
            }
        }

        // ============================================
        // CIERRE DE CÍRCULO cada 30 segundos
        // ============================================

        // Aviso: 5 segundos antes de que se cierre
        int nextShrinkTime = getNextShrinkTime();
        if (nextShrinkTime > 0 && gameTime == nextShrinkTime - WARNING_SECONDS && !warningSent) {
            warningSent = true;
            int nextRadius = currentRadius - SHRINK_AMOUNT;
            if (nextRadius >= 5) {
                broadcastGame("§c§l⚠ §e¡El círculo se cierra en " + WARNING_SECONDS + " segundos! §7Radio: §f" + currentRadius + " → " + nextRadius);

                // Avisar a jugadores cerca del borde
                warnPlayersNearEdge(nextRadius);
            }
        }

        // Ejecutar shrink
        if (gameTime > 0 && gameTime % SHRINK_INTERVAL == 0 && currentRadius > 5) {
            shrinkCircle(w);
            warningSent = false; // Reset para la próxima fase
        }

        // Info cada 30 segundos + reponer bolas de nieve
        if (gameTime % 30 == 0 && gameTime > 0) {
            broadcastGame("§f§lSpleef §7| §fTiempo: §e" + gameTime + "s §7| §fVivos: §a" + alivePlayers.size() + " §7| §fRadio: §c" + currentRadius);
            for (UUID uuid : alivePlayers) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    p.getInventory().setItem(1, new ItemStack(Material.SNOWBALL, 16));
                    p.sendMessage("§b❄ §fBolas de nieve recargadas!");
                }
            }
        }

        // A los 3 minutos, eliminar la capa superior automáticamente (Y=90)
        if (gameTime == 180) {
            broadcastGame("§c§l⚠ §e¡La capa superior se desmorona!");
            titleAlive("§c§l¡CUIDADO!", "§eLa capa superior desaparece");
            if (w != null) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    for (int x = -ARENA_RADIUS; x <= ARENA_RADIUS; x++) {
                        for (int z = -ARENA_RADIUS; z <= ARENA_RADIUS; z++) {
                            double dist = Math.sqrt(x * x + z * z);
                            if (dist <= ARENA_RADIUS) {
                                w.getBlockAt(x, 90, z).setType(Material.AIR);
                            }
                        }
                    }
                }, 60L);
            }
        }

        // A los 5 minutos, eliminar la capa del medio (Y=86)
        if (gameTime == 300) {
            broadcastGame("§c§l⚠ §c¡La capa del medio se desmorona!");
            titleAlive("§4§l¡PELIGRO!", "§cSolo queda la capa inferior");
            if (w != null) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    for (int x = -ARENA_RADIUS; x <= ARENA_RADIUS; x++) {
                        for (int z = -ARENA_RADIUS; z <= ARENA_RADIUS; z++) {
                            double dist = Math.sqrt(x * x + z * z);
                            if (dist <= ARENA_RADIUS) {
                                w.getBlockAt(x, 86, z).setType(Material.AIR);
                            }
                        }
                    }
                }, 60L);
            }
        }
    }

    /**
     * Obtiene el segundo exacto del próximo shrink.
     */
    private int getNextShrinkTime() {
        // El próximo shrink es el siguiente múltiplo de SHRINK_INTERVAL después de gameTime
        int nextMultiple = ((gameTime / SHRINK_INTERVAL) + 1) * SHRINK_INTERVAL;
        return nextMultiple;
    }

    /**
     * Avisa a los jugadores que están cerca del nuevo borde del círculo.
     */
    private void warnPlayersNearEdge(int nextRadius) {
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;

            double distFromCenter = Math.sqrt(p.getLocation().getX() * p.getLocation().getX() +
                                              p.getLocation().getZ() * p.getLocation().getZ());

            // Si está a WARNING_DISTANCE bloques del nuevo borde o fuera de él
            if (distFromCenter >= nextRadius - WARNING_DISTANCE) {
                p.sendTitle("§c§l⚠ ¡CUIDADO!", "§eEstás cerca del borde. ¡Muévete al centro!", 5, 40, 10);
                p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.8f, 1.5f);
                p.sendMessage("§c§l⚠ §c¡Estás muy cerca del borde! §7¡El círculo se cierra pronto!");
            }
        }
    }

    /**
     * Cierra el círculo eliminando los bloques de nieve del anillo exterior.
     */
    private void shrinkCircle(World w) {
        int previousRadius = currentRadius;
        currentRadius -= SHRINK_AMOUNT;
        if (currentRadius < 3) currentRadius = 3; // Mínimo radio

        broadcastGame("§c§l⚠ §4¡El círculo se ha cerrado! §7Radio: §f" + previousRadius + " → " + currentRadius);
        titleAlive("§c§l¡CÍRCULO CERRADO!", "§7Radio: §c" + currentRadius + " bloques");
        soundAll(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.8f);

        // Eliminar bloques de nieve fuera del nuevo radio en las 3 capas
        int[] layerYs = {90, 86, 82}; // Y de cada capa
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (int layerY : layerYs) {
                for (int x = -ARENA_RADIUS; x <= ARENA_RADIUS; x++) {
                    for (int z = -ARENA_RADIUS; z <= ARENA_RADIUS; z++) {
                        double dist = Math.sqrt(x * x + z * z);
                        // Solo eliminar bloques fuera del nuevo radio pero dentro del anterior
                        if (dist > currentRadius && dist <= previousRadius) {
                            if (w.getBlockAt(x, layerY, z).getType() == Material.SNOW_BLOCK) {
                                w.getBlockAt(x, layerY, z).setType(Material.AIR);
                                // Partículas decorativas
                                if (dist % 2 < 1) {
                                    w.spawnParticle(Particle.CLOUD, x + 0.5, layerY + 0.5, z + 0.5, 3, 0.2, 0.2, 0.2, 0.01);
                                }
                            }
                        }
                    }
                }
            }
            plugin.getLogger().info("[MiniGames] Spleef circle shrunk: " + previousRadius + " -> " + currentRadius);
        }, 10L);
    }

    @Override
    public void onCleanup() {
        currentRadius = ARENA_RADIUS;
        warningSent = false;
    }
}
