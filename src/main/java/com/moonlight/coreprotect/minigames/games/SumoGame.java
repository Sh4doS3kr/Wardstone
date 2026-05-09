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
 * Sumo (Knockback FFA): Una plataforma pequeña en el vacío.
 * Todos tienen un palo con Empuje II (Knockback). El objetivo es tirar a los demás fuera.
 */
public class SumoGame extends MiniGame {

    public SumoGame(CoreProtectPlugin plugin, MiniGameManager manager) {
        super(plugin, manager, MiniGameType.SUMO);
    }

    @Override
    public void buildArena(World world) {
        ArenaBuilder.buildSumo(world);
    }

    @Override
    public List<Location> getSpawnLocations(World world) {
        return ArenaBuilder.getSumoSpawns(world);
    }

    @Override
    public void startGameLogic() {
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;

            p.getInventory().clear();

            // Palo con Knockback II
            ItemStack stick = new ItemStack(Material.STICK);
            ItemMeta meta = stick.getItemMeta();
            meta.setDisplayName("§e§l⚔ Palo de Sumo ⚔");
            meta.addEnchant(Enchantment.KNOCKBACK, 2, true);
            meta.setUnbreakable(true);
            stick.setItemMeta(meta);

            p.getInventory().setItem(0, stick);
            p.setHealth(p.getMaxHealth());
        }
    }

    @Override
    public void onTick() {
        // Comprobar jugadores que cayeron al vacío (debajo de Y=90)
        for (UUID uuid : new ArrayList<>(alivePlayers)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;
            if (p.getLocation().getY() < 90) {
                eliminatePlayer(uuid);
            }
        }

        // Info cada 15 segundos
        if (gameTime % 15 == 0 && gameTime > 0) {
            broadcastGame("§e§lSumo §7| §fTiempo: §e" + gameTime + "s §7| §fVivos: §a" + alivePlayers.size());
        }

        // A los 2 minutos, encoger plataforma
        if (gameTime == 120) {
            broadcastGame("§c§l⚠ §e¡La plataforma se encoge!");
            titleAlive("§c§l¡PELIGRO!", "§eLa plataforma se reduce");
            shrinkPlatform(11);
        }

        // A los 3.5 minutos, encoger más
        if (gameTime == 210) {
            broadcastGame("§c§l⚠ §c¡La plataforma es mínima!");
            titleAlive("§4§l¡FINAL!", "§cPlataforma mínima");
            shrinkPlatform(8);
        }
    }

    private void shrinkPlatform(int newRadius) {
        World w = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (w == null) return;

        int baseY = 100;
        for (int x = -15; x <= 15; x++) {
            for (int z = -15; z <= 15; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > newRadius && w.getBlockAt(x, baseY, z).getType() != Material.AIR) {
                    w.getBlockAt(x, baseY, z).setType(Material.AIR);
                    w.spawnParticle(Particle.BLOCK, x + 0.5, baseY + 0.5, z + 0.5, 5,
                        0.3, 0.3, 0.3, 0, Material.SANDSTONE.createBlockData());
                }
            }
        }
        soundAll(Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.5f);
    }

    @Override
    public void onCleanup() {
        // Nada extra
    }
}
