package com.moonlight.coreprotect.minigames.games;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.minigames.*;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * BlockParty (Color Match) con fases, BossBar y inventario visual.
 * El suelo es de colores. Pisa el color correcto antes de que el tiempo acabe.
 * Tu inventario se llena con el color que tienes que pisar.
 * BossBar muestra el color, el tiempo restante y la fase.
 */
public class BlockPartyGame extends MiniGame {

    private int roundNumber = 0;
    private Material currentColor = null;
    private boolean roundActive = false;
    private int roundTaskId = -1;
    private int bossBarTaskId = -1;
    private BossBar bossBar;
    private final Random random = new Random();
    private int currentPhase = 1;

    // Fases: cada fase reduce el tiempo y cambia la dificultad
    // {ronda_inicio, tiempo_reaccion_ticks, colores_en_suelo}
    private static final int[][] PHASE_CONFIG = {
        {1,  60, 5},   // Fase 1: 3s, 5 colores
        {5,  50, 6},   // Fase 2: 2.5s, 6 colores
        {10, 40, 7},   // Fase 3: 2s, 7 colores
        {15, 30, 8},   // Fase 4: 1.5s, 8 colores
        {20, 20, 9},   // Fase 5: 1s, 9 colores (casi imposible)
    };

    public BlockPartyGame(CoreProtectPlugin plugin, MiniGameManager manager) {
        super(plugin, manager, MiniGameType.BLOCK_PARTY);
    }

    @Override
    public void buildArena(World world) {
        ArenaBuilder.buildBlockParty(world);
    }

    @Override
    public List<Location> getSpawnLocations(World world) {
        return ArenaBuilder.getBlockPartySpawns(world);
    }

    @Override
    public void startGameLogic() {
        bossBar = Bukkit.createBossBar("§d§lBlock Party", BarColor.PINK, BarStyle.SOLID);
        bossBar.setVisible(true);
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) bossBar.addPlayer(p);
        }
        Bukkit.getScheduler().runTaskLater(plugin, this::startRound, 40L);
    }

    private int getReactionTime() {
        for (int i = PHASE_CONFIG.length - 1; i >= 0; i--) {
            if (roundNumber >= PHASE_CONFIG[i][0]) return PHASE_CONFIG[i][1];
        }
        return 60;
    }

    private int getColorCount() {
        for (int i = PHASE_CONFIG.length - 1; i >= 0; i--) {
            if (roundNumber >= PHASE_CONFIG[i][0]) return PHASE_CONFIG[i][2];
        }
        return 5;
    }

    private int getPhase() {
        for (int i = PHASE_CONFIG.length - 1; i >= 0; i--) {
            if (roundNumber >= PHASE_CONFIG[i][0]) return i + 1;
        }
        return 1;
    }

    private void startRound() {
        if (!running || alivePlayers.size() < 2) {
            checkWinCondition();
            return;
        }

        roundNumber++;
        roundActive = true;
        int newPhase = getPhase();

        // Anunciar cambio de fase
        if (newPhase != currentPhase) {
            currentPhase = newPhase;
            broadcastGame("§d§l♫ §e§lFASE " + currentPhase + " §d§l♫ §7¡Más rápido!");
            titleAlive("§d§lFASE " + currentPhase, "§7¡Cada vez más difícil!");
            soundAll(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);
        }

        World w = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (w == null) return;

        Material[] allColors = ArenaBuilder.getBlockPartyColors();
        int colorCount = Math.min(getColorCount(), allColors.length);
        // Seleccionar subset de colores para esta ronda
        List<Material> colorList = new ArrayList<>(Arrays.asList(allColors));
        Collections.shuffle(colorList);
        Material[] roundColors = colorList.subList(0, colorCount).toArray(new Material[0]);

        int radius = 20;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist <= radius) {
                    w.getBlockAt(x, 80, z).setType(roundColors[random.nextInt(roundColors.length)]);
                }
            }
        }

        currentColor = roundColors[random.nextInt(roundColors.length)];
        String colorName = ArenaBuilder.getColorName(currentColor);
        int reactionTime = getReactionTime();
        double reactionSeconds = reactionTime / 20.0;

        // BossBar con color y timer
        BarColor barColor = getBarColor(currentColor);
        bossBar.setColor(barColor);
        bossBar.setTitle("§lRonda " + roundNumber + " §r§7| §fPisa: " + colorName + " §7| §e" + String.format("%.1f", reactionSeconds) + "s");
        bossBar.setProgress(1.0);

        // Llenar inventario con el color a pisar
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.getInventory().clear();
                ItemStack colorBlock = new ItemStack(currentColor, 1);
                for (int i = 0; i < 36; i++) {
                    p.getInventory().setItem(i, colorBlock.clone());
                }
                p.getInventory().setHelmet(new ItemStack(currentColor));
                p.sendTitle(colorName, "§7Ronda " + roundNumber + " | Fase " + currentPhase, 0, reactionTime + 10, 5);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
            }
        }

        // BossBar countdown animación
        final long startTime = System.currentTimeMillis();
        final long durationMs = reactionTime * 50L;
        bossBarTaskId = new BukkitRunnable() {
            @Override
            public void run() {
                if (!running || !roundActive) { cancel(); return; }
                long elapsed = System.currentTimeMillis() - startTime;
                double progress = Math.max(0, 1.0 - ((double) elapsed / durationMs));
                bossBar.setProgress(progress);
                if (progress <= 0.3) {
                    bossBar.setColor(BarColor.RED);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L).getTaskId();

        // Timer: eliminar bloques incorrectos
        roundTaskId = new BukkitRunnable() {
            @Override
            public void run() {
                if (!running) return;
                roundActive = false;
                if (bossBarTaskId != -1) {
                    try { Bukkit.getScheduler().cancelTask(bossBarTaskId); } catch (Exception ignored) {}
                }

                bossBar.setProgress(0);
                bossBar.setTitle("§c§l¡TIEMPO!");
                bossBar.setColor(BarColor.RED);

                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        double dist = Math.sqrt(x * x + z * z);
                        if (dist <= radius) {
                            if (w.getBlockAt(x, 80, z).getType() != currentColor) {
                                w.getBlockAt(x, 80, z).setType(Material.AIR);
                            }
                        }
                    }
                }

                soundAll(Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.5f);

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    for (UUID uuid : new ArrayList<>(alivePlayers)) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p == null || !p.isOnline()) continue;
                        if (p.getLocation().getY() < 78) {
                            eliminatePlayer(uuid);
                        }
                    }

                    if (alivePlayers.size() >= 2 && running) {
                        Bukkit.getScheduler().runTaskLater(plugin, () -> startRound(), 50L);
                    } else {
                        checkWinCondition();
                    }
                }, 30L);
            }
        }.runTaskLater(plugin, reactionTime).getTaskId();
    }

    private BarColor getBarColor(Material mat) {
        String name = mat.name().toLowerCase();
        if (name.contains("red")) return BarColor.RED;
        if (name.contains("blue")) return BarColor.BLUE;
        if (name.contains("green") || name.contains("lime")) return BarColor.GREEN;
        if (name.contains("yellow")) return BarColor.YELLOW;
        if (name.contains("pink") || name.contains("magenta")) return BarColor.PINK;
        if (name.contains("purple")) return BarColor.PURPLE;
        if (name.contains("white")) return BarColor.WHITE;
        return BarColor.PINK;
    }

    @Override
    public void onTick() {
        if (roundActive) {
            for (UUID uuid : new ArrayList<>(alivePlayers)) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline()) continue;
                if (p.getLocation().getY() < 75) {
                    eliminatePlayer(uuid);
                }
            }
        }
    }

    @Override
    public void onCleanup() {
        if (roundTaskId != -1) {
            try { Bukkit.getScheduler().cancelTask(roundTaskId); } catch (Exception ignored) {}
            roundTaskId = -1;
        }
        if (bossBarTaskId != -1) {
            try { Bukkit.getScheduler().cancelTask(bossBarTaskId); } catch (Exception ignored) {}
            bossBarTaskId = -1;
        }
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar.setVisible(false);
        }
    }
}
