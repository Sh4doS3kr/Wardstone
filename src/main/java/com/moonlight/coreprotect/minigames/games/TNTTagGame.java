package com.moonlight.coreprotect.minigames.games;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.minigames.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;

/**
 * TNT Tag: Un jugador es "La Bomba". Tiene 30 segundos para pegarle a otro y pasarle la bomba.
 * Cuando el tiempo acaba, el que tiene la bomba explota.
 * Arena grande con casas. Bomba tiene glow rojo, demás verde.
 */
public class TNTTagGame extends MiniGame {

    private UUID bombHolder = null;
    private int roundTimer = 30;
    private int roundNumber = 0;
    private Scoreboard scoreboard;
    private Team bombTeam;
    private Team safeTeam;

    public TNTTagGame(CoreProtectPlugin plugin, MiniGameManager manager) {
        super(plugin, manager, MiniGameType.TNT_TAG);
    }

    @Override
    public void buildArena(World world) {
        ArenaBuilder.buildTNTTag(world);
    }

    @Override
    public List<Location> getSpawnLocations(World world) {
        return ArenaBuilder.getTNTTagSpawns(world);
    }

    @Override
    public void startGameLogic() {
        // Configurar scoreboard para glow effect
        scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        bombTeam = scoreboard.registerNewTeam("bomb");
        bombTeam.setColor(ChatColor.RED);
        bombTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
        
        safeTeam = scoreboard.registerNewTeam("safe");
        safeTeam.setColor(ChatColor.GREEN);
        safeTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);

        // Dar velocidad a todos
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.getInventory().clear();
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false));
                p.setScoreboard(scoreboard);
                safeTeam.addEntry(p.getName());
            }
        }

        // Empezar primera ronda
        startNewRound();
    }

    private void startNewRound() {
        if (alivePlayers.size() < 2) {
            checkWinCondition();
            return;
        }

        roundNumber++;
        roundTimer = Math.max(15, 30 - (roundNumber * 2)); // Cada ronda es más corta

        // Elegir bomba aleatoria
        List<UUID> alive = new ArrayList<>(alivePlayers);
        bombHolder = alive.get(new Random().nextInt(alive.size()));

        // Actualizar equipos
        bombTeam.getEntries().forEach(bombTeam::removeEntry);
        safeTeam.getEntries().forEach(safeTeam::removeEntry);

        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            
            if (uuid.equals(bombHolder)) {
                bombTeam.addEntry(p.getName());
                p.setGlowing(true);
                giveBombKit(p);
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 2, false, false));
                p.sendTitle("§c§l¡ERES LA BOMBA!", "§e¡Toca a alguien para pasarla!", 5, 30, 10);
                p.playSound(p.getLocation(), Sound.ENTITY_TNT_PRIMED, 1.0f, 1.0f);
            } else {
                safeTeam.addEntry(p.getName());
                p.setGlowing(true);
                giveSafeKit(p);
                p.removePotionEffect(PotionEffectType.SPEED);
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false));
            }
        }

        broadcastGame("");
        broadcastGame("§6§l💣 §e§lRONDA " + roundNumber + " §6§l💣");
        broadcastGame("§c" + Bukkit.getPlayer(bombHolder).getName() + " §7tiene la bomba. §e" + roundTimer + "s §7para pasarla.");
        broadcastGame("");
    }

    /**
     * Llamado cuando la bomba golpea a otro jugador.
     */
    public void passBomb(UUID from, UUID to) {
        if (!running || !from.equals(bombHolder) || !alivePlayers.contains(to)) return;

        Player oldBomb = Bukkit.getPlayer(from);
        Player newBomb = Bukkit.getPlayer(to);
        if (oldBomb == null || newBomb == null) return;

        bombHolder = to;

        // Actualizar equipos
        bombTeam.removeEntry(oldBomb.getName());
        safeTeam.addEntry(oldBomb.getName());
        safeTeam.removeEntry(newBomb.getName());
        bombTeam.addEntry(newBomb.getName());

        // Actualizar visual
        giveSafeKit(oldBomb);
        oldBomb.removePotionEffect(PotionEffectType.SPEED);
        oldBomb.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false));

        giveBombKit(newBomb);
        newBomb.removePotionEffect(PotionEffectType.SPEED);
        newBomb.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 2, false, false));

        newBomb.sendTitle("§c§l¡BOMBA!", "§e¡Pásala rápido!", 5, 20, 5);
        oldBomb.sendTitle("§a§l¡LIBRE!", "", 5, 15, 5);

        broadcastGame("§6§l💣 §c" + newBomb.getName() + " §7tiene la bomba ahora. §e" + roundTimer + "s");
        
        // Sonidos
        newBomb.playSound(newBomb.getLocation(), Sound.ENTITY_TNT_PRIMED, 1.0f, 1.0f);
        oldBomb.playSound(oldBomb.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 2.0f);
    }

    public UUID getBombHolder() {
        return bombHolder;
    }

    /**
     * Llamado cuando el portador de la bomba se desconecta.
     * Pasa la bomba a otro jugador aleatorio o termina la ronda.
     */
    public void onBombHolderQuit() {
        if (!running) return;
        if (alivePlayers.size() < 2) {
            checkWinCondition();
            return;
        }
        // Pasar la bomba a un jugador aleatorio
        List<UUID> alive = new ArrayList<>(alivePlayers);
        UUID newHolder = alive.get(new Random().nextInt(alive.size()));
        bombHolder = newHolder;

        // Actualizar equipos
        bombTeam.getEntries().forEach(bombTeam::removeEntry);
        safeTeam.getEntries().forEach(safeTeam::removeEntry);

        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            if (uuid.equals(bombHolder)) {
                bombTeam.addEntry(p.getName());
                p.setGlowing(true);
                giveBombKit(p);
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 2, false, false));
                p.sendTitle("§c§l¡BOMBA!", "§eEl portador anterior se fue. ¡Pásala!", 5, 30, 10);
            } else {
                safeTeam.addEntry(p.getName());
                p.setGlowing(true);
                giveSafeKit(p);
            }
        }
        Player newBomb = Bukkit.getPlayer(newHolder);
        broadcastGame("§6§l💣 §7El portador se fue. §c" + (newBomb != null ? newBomb.getName() : "???") + " §7tiene la bomba ahora.");
    }

    @Override
    public void onTick() {
        if (bombHolder == null) return;

        roundTimer--;

        // Sonidos de cuenta atrás
        if (roundTimer <= 10 && roundTimer > 0) {
            soundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, roundTimer <= 3 ? 2.0f : 1.0f);
            
            if (roundTimer <= 5) {
                Player bomb = Bukkit.getPlayer(bombHolder);
                if (bomb != null) {
                    // Partículas de explosión inminente
                    bomb.getWorld().spawnParticle(Particle.FLAME, bomb.getLocation().add(0, 1, 0), 
                        15, 0.5, 0.5, 0.5, 0.05);
                }
            }
        }

        // Mostrar countdown en título al portador
        if (roundTimer <= 10 && roundTimer > 0) {
            Player bomb = Bukkit.getPlayer(bombHolder);
            if (bomb != null) {
                String color = roundTimer <= 3 ? "§4" : roundTimer <= 5 ? "§c" : "§e";
                bomb.sendTitle(color + "§l" + roundTimer, "§c¡PÁSALA!", 0, 25, 0);
            }
        }

        // Timer info cada 10 segundos
        if (roundTimer > 0 && roundTimer % 10 == 0) {
            broadcastGame("§6§l💣 §7Quedan §e" + roundTimer + "s §7| Bomba: §c" + 
                (Bukkit.getPlayer(bombHolder) != null ? Bukkit.getPlayer(bombHolder).getName() : "???"));
        }

        // ¡BOOM!
        if (roundTimer <= 0) {
            Player bomb = Bukkit.getPlayer(bombHolder);
            if (bomb != null && bomb.isOnline()) {
                // Explosión visual
                bomb.getWorld().createExplosion(bomb.getLocation(), 0, false, false);
                bomb.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, bomb.getLocation(), 3, 1, 1, 1, 0);
                broadcastGame("§c§l💥 ¡BOOM! §f" + bomb.getName() + " §7ha explotado.");
                
                for (UUID uuid : players) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
                }
            }
            
            eliminatePlayer(bombHolder);
            bombHolder = null;

            // Si quedan jugadores, nueva ronda
            if (alivePlayers.size() >= 2) {
                Bukkit.getScheduler().runTaskLater(plugin, this::startNewRound, 60L);
            }
        }
    }

    private void giveBombKit(Player p) {
        p.getInventory().clear();
        p.getInventory().setHelmet(new ItemStack(Material.TNT));
        // Llenar inventario con TNTs visuales
        ItemStack visualTNT = new ItemStack(Material.TNT);
        ItemMeta tntMeta = visualTNT.getItemMeta();
        tntMeta.setDisplayName("§c§l💣 ¡BOMBA!");
        visualTNT.setItemMeta(tntMeta);
        for (int i = 0; i < 36; i++) {
            p.getInventory().setItem(i, visualTNT.clone());
        }
    }

    private void giveSafeKit(Player p) {
        p.getInventory().clear();
        p.getInventory().setHelmet(null);
    }

    @Override
    public void onCleanup() {
        // Limpiar glow y scoreboard
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.setGlowing(false);
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                p.removePotionEffect(PotionEffectType.SPEED);
                p.getInventory().setHelmet(null);
            }
        }
        if (bombTeam != null) try { bombTeam.unregister(); } catch (Exception ignored) {}
        if (safeTeam != null) try { safeTeam.unregister(); } catch (Exception ignored) {}
        bombHolder = null;
    }
}
