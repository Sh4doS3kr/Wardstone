package com.moonlight.coreprotect.shadowhunter;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Gestiona las misiones "El Cazador de Sombras" (Misión 1) y "El Despertar del
 * Abismo" (Misión 2).
 * Misión 1: NONE → NPC_SPAWNED → COMBAT → RITUAL → BOSS → COMPLETED
 * Misión 2: QUEST2_NPC_SPAWNED → QUEST2_EXPLORATION → QUEST2_COMBAT →
 * QUEST2_RITUAL → QUEST2_BOSS → QUEST2_COMPLETED
 */
public class ShadowHunterManager {

    private final CoreProtectPlugin plugin;
    private final File dataFile;

    // Estado de misión por jugador
    private final Map<UUID, QuestState> questStates = new ConcurrentHashMap<>();
    // Cooldowns para jugadores que rechazaron (1 semana)
    private final Map<UUID, Long> declineCooldowns = new ConcurrentHashMap<>();
    // Jugadores que ya completaron la misión 1
    private final Set<UUID> completed = ConcurrentHashMap.newKeySet();
    // Jugadores que ya completaron la misión 2
    private final Set<UUID> completedQuest2 = ConcurrentHashMap.newKeySet();
    // NPCs activos por jugador
    private final Map<UUID, ShadowHunterNPC> activeNPCs = new ConcurrentHashMap<>();
    // Misión 1: Combates activos
    private final Map<UUID, ShadowHunterCombat> activeCombats = new ConcurrentHashMap<>();
    // Misión 1: Rituales activos
    private final Map<UUID, ShadowHunterRitual> activeRituals = new ConcurrentHashMap<>();
    // Misión 1: Boss fights activos
    private final Map<UUID, ShadowHunterBoss> activeBosses = new ConcurrentHashMap<>();
    // Misión 2: Exploraciones activas
    private final Map<UUID, AbyssExploration> activeExplorations = new ConcurrentHashMap<>();
    // Misión 2: Combates activos
    private final Map<UUID, AbyssCombat> activeCombats2 = new ConcurrentHashMap<>();
    // Misión 2: Rituales activos
    private final Map<UUID, AbyssRitual> activeRituals2 = new ConcurrentHashMap<>();
    // Misión 2: Boss fights activos
    private final Map<UUID, AbyssBoss> activeBosses2 = new ConcurrentHashMap<>();

    public static final long DECLINE_COOLDOWN = 12 * 60 * 60 * 1000L; // 12 horas

    // Misión 3: flag de activación y datos
    private boolean quest3Enabled = false;
    private final Set<UUID> completedQuest3 = ConcurrentHashMap.newKeySet();

    // Misión 4: flag de activación y datos
    private boolean quest4Enabled = false;
    private final Set<UUID> completedQuest4 = ConcurrentHashMap.newKeySet();
    private final Map<UUID, ElegyBoss> activeBosses4 = new ConcurrentHashMap<>();

    // Misión 5: flag de activación y datos
    private boolean quest5Enabled = false;
    private final Set<UUID> completedQuest5 = ConcurrentHashMap.newKeySet();
    private final Map<UUID, SkyParkour> activeParkours5 = new ConcurrentHashMap<>();

    // Misión 6: flag de activación y datos
    private boolean quest6Enabled = false;
    private final Set<UUID> completedQuest6 = ConcurrentHashMap.newKeySet();
    private final Map<UUID, MemoryWalk> activeMemoryWalks6 = new ConcurrentHashMap<>();
    private final Map<UUID, LyraBoss> activeBosses6 = new ConcurrentHashMap<>();

    // Misión 7: flag de activación y datos
    private boolean quest7Enabled = false;
    private final Set<UUID> completedQuest7 = ConcurrentHashMap.newKeySet();
    private final Map<UUID, LabyrinthManager> activeLabyrinths7 = new ConcurrentHashMap<>();

    // Misión 8: flag de activación y datos
    private boolean quest8Enabled = false;
    private final Set<UUID> completedQuest8 = ConcurrentHashMap.newKeySet();
    private final Map<UUID, FrostBoss> activeBosses8 = new ConcurrentHashMap<>();

    // Misión 9: flag de activación y datos
    private boolean quest9Enabled = false;
    private final Set<UUID> completedQuest9 = ConcurrentHashMap.newKeySet();
    private final Map<UUID, OriginBoss> activeBosses9 = new ConcurrentHashMap<>();

    // Misión 10: flag de activación y datos
    private boolean quest10Enabled = false;
    private final Set<UUID> completedQuest10 = ConcurrentHashMap.newKeySet();
    private final Map<UUID, FinalBoss> activeBosses10 = new ConcurrentHashMap<>();

    public Set<UUID> getCompletedQuest3() {
        return completedQuest3;
    }
    private final Map<UUID, VoidTrials> activeTrials3 = new ConcurrentHashMap<>();
    private final Map<UUID, VoidBoss> activeBosses3 = new ConcurrentHashMap<>();
    private final Map<UUID, VoidPostBoss> activePostBoss3 = new ConcurrentHashMap<>();

    // Tracking: jugadores que ya fueron visitados por el Errante para su misión
    // actual
    // Se limpia cuando completan la misión o si el NPC desaparece por rechazar
    private final Set<UUID> erranteVisitedThisMission = ConcurrentHashMap.newKeySet();

    // Respawn en arena: mapea jugador -> arena para respawnear dentro al morir
    private final Map<UUID, VoidArena> arenaRespawns = new ConcurrentHashMap<>();
    // Respawn directo por ubicación (para bosses sin arena como Quest 4)
    private final Map<UUID, org.bukkit.Location> directRespawns = new ConcurrentHashMap<>();

    public enum QuestState {
        NONE,
        NPC_SPAWNED,
        DIALOGUE,
        COMBAT_PHASE,
        RITUAL_PHASE,
        BOSS_PHASE,
        REWARD,
        COMPLETED,
        // Misión 2
        QUEST2_NPC_SPAWNED,
        QUEST2_EXPLORATION,
        QUEST2_COMBAT,
        QUEST2_RITUAL,
        QUEST2_BOSS,
        QUEST2_COMPLETED,
        // Misión 3
        QUEST3_NPC_SPAWNED,
        QUEST3_TRIALS,
        QUEST3_ARENA,
        QUEST3_BOSS,
        QUEST3_POSTBOSS,
        QUEST3_COMPLETED,
        // Misión 4
        QUEST4_NPC_SPAWNED,
        QUEST4_BOSS,
        QUEST4_COMPLETED,
        // Misión 5
        QUEST5_NPC_SPAWNED,
        QUEST5_PARKOUR,
        QUEST5_COMPLETED,
        // Misión 6
        QUEST6_NPC_SPAWNED,
        QUEST6_MEMORY_WALK,
        QUEST6_BOSS,
        QUEST6_COMPLETED,
        // Misión 7
        QUEST7_NPC_SPAWNED,
        QUEST7_LABYRINTH,
        QUEST7_BOSS,
        QUEST7_COMPLETED,
        // Misión 8
        QUEST8_NPC_SPAWNED,
        QUEST8_BOSS,
        QUEST8_COMPLETED,
        // Misión 9
        QUEST9_NPC_SPAWNED,
        QUEST9_BOSS,
        QUEST9_COMPLETED,
        // Misión 10
        QUEST10_NPC_SPAWNED,
        QUEST10_BOSS,
        QUEST10_COMPLETED
    }

    public ShadowHunterManager(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "shadowhunter.yml");
        loadData();

        // Enforce VoidArena Boundaries (nobody can enter, nobody can leave)
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeBosses3.isEmpty() && activePostBoss3.isEmpty()) return;
            for (Player p : Bukkit.getOnlinePlayers()) {
                activeBosses3.forEach((owner, boss) -> enforceVoidArenaBounds(owner, p, boss.getArena()));
                activePostBoss3.forEach((owner, post) -> enforceVoidArenaBounds(owner, p, post.getArena()));
            }
        }, 10L, 10L);

        // Tarea periódica: intentar spawnear NPCs a jugadores elegibles cada 5 min
        Bukkit.getScheduler().runTaskTimer(plugin, this::trySpawnNPCs, 20L * 60, 20L * 300);

        // Auto-guardado cada 5 minutos
        Bukkit.getScheduler().runTaskTimer(plugin, this::saveData, 20L * 300, 20L * 300);
        
        // Anti-agua para mobs de misiones (verificar cada segundo)
        Bukkit.getScheduler().runTaskTimer(plugin, this::preventMobsInWater, 20L, 20L);
    }
    
    private void preventMobsInWater() {
        // Early exit: skip entirely if no active bosses (99% of server time)
        if (activeBosses.isEmpty() && activeBosses2.isEmpty() && activeBosses3.isEmpty()
                && activePostBoss3.isEmpty() && activeBosses4.isEmpty() && activeBosses8.isEmpty()) {
            return;
        }

        // Collect only the actual boss/summoned entities — NO world.getEntities() scan
        java.util.List<org.bukkit.entity.Entity> toCheck = new java.util.ArrayList<>();
        for (ShadowHunterBoss boss : activeBosses.values()) {
            if (boss != null && boss.isActive()) boss.collectProtectedEntities(toCheck);
        }
        for (AbyssBoss boss : activeBosses2.values()) {
            if (boss != null && boss.isActive()) boss.collectProtectedEntities(toCheck);
        }
        for (VoidBoss boss : activeBosses3.values()) {
            if (boss != null && boss.isActive()) boss.collectProtectedEntities(toCheck);
        }
        for (VoidPostBoss pb : activePostBoss3.values()) {
            if (pb != null && pb.isActive()) pb.collectProtectedEntities(toCheck);
        }
        for (ElegyBoss boss : activeBosses4.values()) {
            if (boss != null && boss.isActive()) boss.collectProtectedEntities(toCheck);
        }
        for (FrostBoss boss : activeBosses8.values()) {
            if (boss != null && boss.isActive()) boss.collectProtectedEntities(toCheck);
        }

        for (org.bukkit.entity.Entity entity : toCheck) {
            checkAndTeleportFromWater(entity);
        }
    }
    
    private void checkAndTeleportFromWater(org.bukkit.entity.Entity entity) {
        if (entity == null || entity.isDead()) return;
        
        org.bukkit.Location loc = entity.getLocation();
        org.bukkit.Material blockType = loc.getBlock().getType();
        
        if (blockType == org.bukkit.Material.WATER || blockType == org.bukkit.Material.LAVA) {
            // Buscar bloque sólido cercano
            for (int y = 0; y <= 3; y++) {
                for (int x = -2; x <= 2; x++) {
                    for (int z = -2; z <= 2; z++) {
                        org.bukkit.Location check = loc.clone().add(x, y, z);
                        if (check.getBlock().getType().isSolid() && 
                            check.clone().add(0, 1, 0).getBlock().getType().isAir() &&
                            check.clone().add(0, 1, 0).getBlock().getType() != org.bukkit.Material.WATER &&
                            check.clone().add(0, 1, 0).getBlock().getType() != org.bukkit.Material.LAVA) {
                            entity.teleport(check.add(0.5, 1, 0.5));
                            return;
                        }
                    }
                }
            }
            // Si no encuentra bloque seguro, teletransportar arriba
            entity.teleport(loc.add(0, 3, 0));
        }
    }

    // ==================== SPAWN NPC ====================

    /**
     * Intenta spawnear NPCs a jugadores que no tienen misión activa ni cooldown.
     */
    private void trySpawnNPCs() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uid = player.getUniqueId();

            // No spawnear en Nether/End
            if (isNetherOrEnd(player.getWorld()))
                continue;
            // Ya tiene NPC o misión activa
            if (activeNPCs.containsKey(uid))
                continue;
            QuestState state = questStates.getOrDefault(uid, QuestState.NONE);
            if (state != QuestState.NONE && state != QuestState.COMPLETED && state != QuestState.QUEST2_COMPLETED
                    && state != QuestState.QUEST3_COMPLETED && state != QuestState.QUEST4_COMPLETED
                    && state != QuestState.QUEST5_COMPLETED && state != QuestState.QUEST6_COMPLETED
                    && state != QuestState.QUEST7_COMPLETED && state != QuestState.QUEST8_COMPLETED
                    && state != QuestState.QUEST9_COMPLETED
                    && state != QuestState.QUEST10_COMPLETED)
                continue;

            // El Errante solo visita UNA VEZ por misión pendiente
            if (erranteVisitedThisMission.contains(uid))
                continue;

            // En cooldown por haber rechazado
            Long cooldownEnd = declineCooldowns.get(uid);
            if (cooldownEnd != null && System.currentTimeMillis() < cooldownEnd)
                continue;

            // Random chance: 20% cada 5 min
            if (Math.random() > 0.20)
                continue;

            // Marcar como visitado para esta misión
            erranteVisitedThisMission.add(uid);

            // Determinar qué misión ofrecer
            if (!completed.contains(uid)) {
                // Misión 1: no completada aún
                spawnNPCFor(player, false);
            } else if (!completedQuest2.contains(uid)) {
                // Misión 1 completada, misión 2 no completada: ofrecer misión 2
                spawnNPCFor(player, true);
            } else if (quest3Enabled && !completedQuest3.contains(uid)) {
                // Misiones 1 y 2 completadas, misión 3 habilitada: ofrecer misión 3
                spawnNPCForQuest3(player);
            } else if (quest4Enabled && completedQuest3.contains(uid) && !completedQuest4.contains(uid)) {
                // Misiones 1-3 completadas, misión 4 habilitada: ofrecer misión 4
                spawnNPCForQuest4(player);
            } else if (quest5Enabled && completedQuest4.contains(uid) && !completedQuest5.contains(uid)) {
                // Misiones 1-4 completadas, misión 5 habilitada: ofrecer misión 5
                spawnNPCForQuest5(player);
            } else if (quest6Enabled && completedQuest5.contains(uid) && !completedQuest6.contains(uid)) {
                // Misiones 1-5 completadas, misión 6 habilitada: ofrecer misión 6
                spawnNPCForQuest6(player);
            } else if (quest7Enabled && completedQuest6.contains(uid) && !completedQuest7.contains(uid)) {
                // Misiones 1-6 completadas, misión 7 habilitada: ofrecer misión 7
                spawnNPCForQuest7(player);
            } else if (quest8Enabled && completedQuest7.contains(uid) && !completedQuest8.contains(uid)) {
                // Misiones 1-7 completadas, misión 8 habilitada: ofrecer misión 8
                spawnNPCForQuest8(player);
            } else if (quest9Enabled && completedQuest8.contains(uid) && !completedQuest9.contains(uid)) {
                // Misiones 1-8 completadas, misión 9 habilitada: ofrecer misión 9
                spawnNPCForQuest9(player);
            } else if (quest10Enabled && completedQuest9.contains(uid) && !completedQuest10.contains(uid)) {
                // Misiones 1-9 completadas, misión 10 habilitada: ofrecer misión 10
                spawnNPCForQuest10(player);
            }
            // Si todas completadas: no spawnear
        }
    }

    /**
     * Fuerza el spawn del NPC para un jugador (usado por comando admin).
     */
    public void forceSpawnNPC(Player target) {
        forceSpawnNPC(target, false);
    }
    
    /**
     * Fuerza el spawn del NPC para un jugador con opción de repetición.
     */
    public void forceSpawnNPC(Player target, boolean enableRepeat) {
        UUID uid = target.getUniqueId();
        if (!enableRepeat && completed.contains(uid)) {
            target.sendMessage(SmallCaps.convert("§c§l✖ §cYa has completado la Misión 1. No se puede repetir."));
            return;
        }
        cleanupPlayer(uid);
        declineCooldowns.remove(uid);
        spawnNPCFor(target, false);
    }

    /**
     * Fuerza el spawn del NPC para misión 2.
     */
    public void forceSpawnNPC2(Player target) {
        forceSpawnNPC2(target, false);
    }
    
    /**
     * Fuerza el spawn del NPC para misión 2 con opción de repetición.
     */
    public void forceSpawnNPC2(Player target, boolean enableRepeat) {
        UUID uid = target.getUniqueId();
        if (!enableRepeat && completedQuest2.contains(uid)) {
            target.sendMessage(SmallCaps.convert("§c§l✖ §cYa has completado la Misión 2. No se puede repetir."));
            return;
        }
        cleanupPlayer(uid);
        declineCooldowns.remove(uid);
        if (!completed.contains(uid))
            completed.add(uid); // Marcar quest 1 como completada
        spawnNPCFor(target, true);
    }

    private void spawnNPCFor(Player player, boolean isQuest2) {
        UUID uid = player.getUniqueId();
        ShadowHunterNPC npc = new ShadowHunterNPC(plugin, this, player);
        npc.setQuest2(isQuest2);
        activeNPCs.put(uid, npc);
        questStates.put(uid, isQuest2 ? QuestState.QUEST2_NPC_SPAWNED : QuestState.NPC_SPAWNED);
        npc.spawn();
    }

    private void spawnNPCForQuest3(Player player) {
        UUID uid = player.getUniqueId();
        ShadowHunterNPC npc = new ShadowHunterNPC(plugin, this, player);
        npc.setQuest3(true);
        activeNPCs.put(uid, npc);
        questStates.put(uid, QuestState.QUEST3_NPC_SPAWNED);
        npc.spawn();
    }

    /**
     * Fuerza el spawn del NPC para misión 3.
     */
    public void forceSpawnNPC3(Player target) {
        forceSpawnNPC3(target, false);
    }
    
    /**
     * Fuerza el spawn del NPC para misión 3 con opción de repetición.
     */
    public void forceSpawnNPC3(Player target, boolean enableRepeat) {
        UUID uid = target.getUniqueId();
        if (!enableRepeat && completedQuest3.contains(uid)) {
            target.sendMessage(SmallCaps.convert("§c§l✖ §cYa has completado la Misión 3. No se puede repetir."));
            return;
        }
        cleanupPlayer(uid);
        declineCooldowns.remove(uid);
        if (!completed.contains(uid))
            completed.add(uid);
        if (!completedQuest2.contains(uid))
            completedQuest2.add(uid);
        spawnNPCForQuest3(target);
    }

    private void spawnNPCForQuest4(Player player) {
        UUID uid = player.getUniqueId();
        ShadowHunterNPC npc = new ShadowHunterNPC(plugin, this, player);
        npc.setQuest4(true);
        activeNPCs.put(uid, npc);
        questStates.put(uid, QuestState.QUEST4_NPC_SPAWNED);
        npc.spawn();
    }

    public void forceSpawnNPC4(Player target) {
        forceSpawnNPC4(target, false);
    }

    public void forceSpawnNPC4(Player target, boolean enableRepeat) {
        UUID uid = target.getUniqueId();
        if (!enableRepeat && completedQuest4.contains(uid)) {
            target.sendMessage(SmallCaps.convert("§c§l✖ §cYa has completado la Misión 4. No se puede repetir."));
            return;
        }
        cleanupPlayer(uid);
        declineCooldowns.remove(uid);
        if (!completed.contains(uid)) completed.add(uid);
        if (!completedQuest2.contains(uid)) completedQuest2.add(uid);
        if (!completedQuest3.contains(uid)) completedQuest3.add(uid);
        spawnNPCForQuest4(target);
    }

    private void spawnNPCForQuest5(Player player) {
        UUID uid = player.getUniqueId();
        ShadowHunterNPC npc = new ShadowHunterNPC(plugin, this, player);
        npc.setQuest5(true);
        activeNPCs.put(uid, npc);
        questStates.put(uid, QuestState.QUEST5_NPC_SPAWNED);
        npc.spawn();
    }

    public void forceSpawnNPC5(Player target) {
        forceSpawnNPC5(target, false);
    }

    public void forceSpawnNPC5(Player target, boolean enableRepeat) {
        UUID uid = target.getUniqueId();
        if (!enableRepeat && completedQuest5.contains(uid)) {
            target.sendMessage(SmallCaps.convert("§c§l✖ §cYa has completado la Misión 5. No se puede repetir."));
            return;
        }
        cleanupPlayer(uid);
        declineCooldowns.remove(uid);
        if (!completed.contains(uid)) completed.add(uid);
        if (!completedQuest2.contains(uid)) completedQuest2.add(uid);
        if (!completedQuest3.contains(uid)) completedQuest3.add(uid);
        if (!completedQuest4.contains(uid)) completedQuest4.add(uid);
        spawnNPCForQuest5(target);
    }

    private void spawnNPCForQuest6(Player player) {
        UUID uid = player.getUniqueId();
        ShadowHunterNPC npc = new ShadowHunterNPC(plugin, this, player);
        npc.setQuest6(true);
        activeNPCs.put(uid, npc);
        questStates.put(uid, QuestState.QUEST6_NPC_SPAWNED);
        npc.spawn();
    }

    public void forceSpawnNPC6(Player target) {
        forceSpawnNPC6(target, false);
    }

    public void forceSpawnNPC6(Player target, boolean enableRepeat) {
        UUID uid = target.getUniqueId();
        if (!enableRepeat && completedQuest6.contains(uid)) {
            target.sendMessage(SmallCaps.convert("§c§l✖ §cYa has completado la Misión 6. No se puede repetir."));
            return;
        }
        cleanupPlayer(uid);
        declineCooldowns.remove(uid);
        if (!completed.contains(uid)) completed.add(uid);
        if (!completedQuest2.contains(uid)) completedQuest2.add(uid);
        if (!completedQuest3.contains(uid)) completedQuest3.add(uid);
        if (!completedQuest4.contains(uid)) completedQuest4.add(uid);
        if (!completedQuest5.contains(uid)) completedQuest5.add(uid);
        spawnNPCForQuest6(target);
    }

    private void spawnNPCForQuest7(Player player) {
        UUID uid = player.getUniqueId();
        ShadowHunterNPC npc = new ShadowHunterNPC(plugin, this, player);
        npc.setQuest7(true);
        activeNPCs.put(uid, npc);
        questStates.put(uid, QuestState.QUEST7_NPC_SPAWNED);
        npc.spawn();
    }

    public void forceSpawnNPC7(Player target) {
        forceSpawnNPC7(target, false);
    }

    public void forceSpawnNPC7(Player target, boolean enableRepeat) {
        UUID uid = target.getUniqueId();
        if (!enableRepeat && completedQuest7.contains(uid)) {
            target.sendMessage(SmallCaps.convert("§c§l✖ §cYa has completado la Misión 7. No se puede repetir."));
            return;
        }
        cleanupPlayer(uid);
        declineCooldowns.remove(uid);
        if (!completed.contains(uid)) completed.add(uid);
        if (!completedQuest2.contains(uid)) completedQuest2.add(uid);
        if (!completedQuest3.contains(uid)) completedQuest3.add(uid);
        if (!completedQuest4.contains(uid)) completedQuest4.add(uid);
        if (!completedQuest5.contains(uid)) completedQuest5.add(uid);
        if (!completedQuest6.contains(uid)) completedQuest6.add(uid);
        spawnNPCForQuest7(target);
    }

    private void spawnNPCForQuest8(Player player) {
        UUID uid = player.getUniqueId();
        ShadowHunterNPC npc = new ShadowHunterNPC(plugin, this, player);
        npc.setQuest8(true);
        activeNPCs.put(uid, npc);
        questStates.put(uid, QuestState.QUEST8_NPC_SPAWNED);
        npc.spawn();
    }

    public void forceSpawnNPC8(Player target) {
        forceSpawnNPC8(target, false);
    }

    public void forceSpawnNPC8(Player target, boolean enableRepeat) {
        UUID uid = target.getUniqueId();
        if (!enableRepeat && completedQuest8.contains(uid)) {
            target.sendMessage(SmallCaps.convert("§c§l✖ §cYa has completado la Misión 8. No se puede repetir."));
            return;
        }
        cleanupPlayer(uid);
        declineCooldowns.remove(uid);
        if (!completed.contains(uid)) completed.add(uid);
        if (!completedQuest2.contains(uid)) completedQuest2.add(uid);
        if (!completedQuest3.contains(uid)) completedQuest3.add(uid);
        if (!completedQuest4.contains(uid)) completedQuest4.add(uid);
        if (!completedQuest5.contains(uid)) completedQuest5.add(uid);
        if (!completedQuest6.contains(uid)) completedQuest6.add(uid);
        if (!completedQuest7.contains(uid)) completedQuest7.add(uid);
        spawnNPCForQuest8(target);
    }

    private void spawnNPCForQuest9(Player player) {
        UUID uid = player.getUniqueId();
        ShadowHunterNPC npc = new ShadowHunterNPC(plugin, this, player);
        npc.setQuest9(true);
        activeNPCs.put(uid, npc);
        questStates.put(uid, QuestState.QUEST9_NPC_SPAWNED);
        npc.spawn();
    }

    public void forceSpawnNPC9(Player target) {
        forceSpawnNPC9(target, false);
    }

    public void forceSpawnNPC9(Player target, boolean enableRepeat) {
        UUID uid = target.getUniqueId();
        if (!enableRepeat && completedQuest9.contains(uid)) {
            target.sendMessage(SmallCaps.convert("§c§l✖ §cYa has completado la Misión 9. No se puede repetir."));
            return;
        }
        cleanupPlayer(uid);
        declineCooldowns.remove(uid);
        if (!completed.contains(uid)) completed.add(uid);
        if (!completedQuest2.contains(uid)) completedQuest2.add(uid);
        if (!completedQuest3.contains(uid)) completedQuest3.add(uid);
        if (!completedQuest4.contains(uid)) completedQuest4.add(uid);
        if (!completedQuest5.contains(uid)) completedQuest5.add(uid);
        if (!completedQuest6.contains(uid)) completedQuest6.add(uid);
        if (!completedQuest7.contains(uid)) completedQuest7.add(uid);
        if (!completedQuest8.contains(uid)) completedQuest8.add(uid);
        spawnNPCForQuest9(target);
    }

    private void spawnNPCForQuest10(Player player) {
        UUID uid = player.getUniqueId();
        ShadowHunterNPC npc = new ShadowHunterNPC(plugin, this, player);
        npc.setQuest10(true);
        activeNPCs.put(uid, npc);
        questStates.put(uid, QuestState.QUEST10_NPC_SPAWNED);
        npc.spawn();
    }

    public void forceSpawnNPC10(Player target) {
        forceSpawnNPC10(target, false);
    }

    public void forceSpawnNPC10(Player target, boolean enableRepeat) {
        UUID uid = target.getUniqueId();
        if (!enableRepeat && completedQuest10.contains(uid)) {
            target.sendMessage(SmallCaps.convert("§c§l✖ §cYa has completado la Misión 10. No se puede repetir."));
            return;
        }
        cleanupPlayer(uid);
        declineCooldowns.remove(uid);
        if (!completed.contains(uid)) completed.add(uid);
        if (!completedQuest2.contains(uid)) completedQuest2.add(uid);
        if (!completedQuest3.contains(uid)) completedQuest3.add(uid);
        if (!completedQuest4.contains(uid)) completedQuest4.add(uid);
        if (!completedQuest5.contains(uid)) completedQuest5.add(uid);
        if (!completedQuest6.contains(uid)) completedQuest6.add(uid);
        if (!completedQuest7.contains(uid)) completedQuest7.add(uid);
        if (!completedQuest8.contains(uid)) completedQuest8.add(uid);
        if (!completedQuest9.contains(uid)) completedQuest9.add(uid);
        spawnNPCForQuest10(target);
    }

    // ==================== PROGRESIÓN ====================

    public void onDialogueAccepted(Player player) {
        UUID uid = player.getUniqueId();
        questStates.put(uid, QuestState.COMBAT_PHASE);

        // Desactivar fly
        if (player.getAllowFlight()) {
            player.setAllowFlight(false);
            player.setFlying(false);
            player.sendMessage(SmallCaps.convert("§c§l⚔ §fNo puedes volar durante el combate con El Errante."));
        }

        // Eliminar NPC
        ShadowHunterNPC npc = activeNPCs.get(uid);
        if (npc != null) {
            npc.cancelDespawnTimer(); // Cancelar timer de despawn
        }

        // Iniciar combate
        ShadowHunterCombat combat = new ShadowHunterCombat(plugin, this, player, npc);
        activeCombats.put(uid, combat);
        combat.start();
    }

    public void onDialogueDeclined(Player player) {
        UUID uid = player.getUniqueId();
        QuestState currentState = questStates.get(uid);
        
        declineCooldowns.put(uid, System.currentTimeMillis() + DECLINE_COOLDOWN);
        cleanupPlayer(uid);
        questStates.put(uid, QuestState.NONE);
        saveData();

        if (currentState == QuestState.QUEST5_NPC_SPAWNED) {
            player.sendMessage(SmallCaps.convert("§3§l◈ El Eco §7susurra: §f\"Volveré... cuando estés listo.\""));
        } else {
            player.sendMessage(SmallCaps.convert("§5§l✦ El Errante §7susurra: §f\"Volveré... cuando estés listo.\""));
        }
    }

    public void onCombatCompleted(Player player) {
        UUID uid = player.getUniqueId();
        activeCombats.remove(uid);
        questStates.put(uid, QuestState.RITUAL_PHASE);

        ShadowHunterNPC npc = activeNPCs.get(uid);

        // Iniciar ritual
        ShadowHunterRitual ritual = new ShadowHunterRitual(plugin, this, player, npc);
        activeRituals.put(uid, ritual);
        ritual.start();
    }

    public void onRitualCompleted(Player player) {
        UUID uid = player.getUniqueId();
        activeRituals.remove(uid);
        questStates.put(uid, QuestState.BOSS_PHASE);

        ShadowHunterNPC npc = activeNPCs.get(uid);

        // Iniciar boss fight
        ShadowHunterBoss boss = new ShadowHunterBoss(plugin, this, player, npc);
        activeBosses.put(uid, boss);
        boss.start();
    }

    public void onBossDefeated(Player player) {
        UUID uid = player.getUniqueId();
        activeBosses.remove(uid);
        questStates.put(uid, QuestState.COMPLETED);
        completed.add(uid);
        VoidBlade.giveVoidBlade(player, plugin);
        ShadowHunterNPC npc = activeNPCs.remove(uid);
        if (npc != null)
            npc.despawnWithEffect();
        saveData();
        Bukkit.broadcastMessage("§5§l✦ §d" + player.getName()
                + " §fha completado §5§lEl Cazador de Sombras §fy obtenido la §5§lHoja del Vacío§f!");
    }

    // ==================== MISIÓN 2: PROGRESIÓN ====================

    public void onQuest2DialogueAccepted(Player player) {
        UUID uid = player.getUniqueId();
        questStates.put(uid, QuestState.QUEST2_EXPLORATION);
        if (player.getAllowFlight()) {
            player.setAllowFlight(false);
            player.setFlying(false);
            player.sendMessage(SmallCaps.convert("§c§l⚔ §fNo puedes volar durante la misión del Abismo."));
        }
        ShadowHunterNPC npc = activeNPCs.get(uid);
        if (npc != null)
            npc.cancelDespawnTimer();
        AbyssExploration exploration = new AbyssExploration(plugin, this, player, npc);
        activeExplorations.put(uid, exploration);
        exploration.start();
    }

    public void onQuest2ExplorationCompleted(Player player) {
        UUID uid = player.getUniqueId();
        activeExplorations.remove(uid);
        questStates.put(uid, QuestState.QUEST2_COMBAT);
        ShadowHunterNPC npc = activeNPCs.get(uid);
        AbyssCombat combat = new AbyssCombat(plugin, this, player, npc);
        activeCombats2.put(uid, combat);
        combat.start();
    }

    public void onQuest2CombatCompleted(Player player) {
        UUID uid = player.getUniqueId();
        activeCombats2.remove(uid);
        questStates.put(uid, QuestState.QUEST2_RITUAL);
        ShadowHunterNPC npc = activeNPCs.get(uid);
        AbyssRitual ritual = new AbyssRitual(plugin, this, player, npc);
        activeRituals2.put(uid, ritual);
        ritual.start();
    }

    public void onQuest2RitualCompleted(Player player) {
        UUID uid = player.getUniqueId();
        activeRituals2.remove(uid);
        questStates.put(uid, QuestState.QUEST2_BOSS);
        ShadowHunterNPC npc = activeNPCs.get(uid);
        AbyssBoss boss = new AbyssBoss(plugin, this, player, npc);
        activeBosses2.put(uid, boss);
        boss.start();
    }

    public void onQuest2BossDefeated(Player player) {
        UUID uid = player.getUniqueId();
        activeBosses2.remove(uid);
        questStates.put(uid, QuestState.QUEST2_COMPLETED);
        completedQuest2.add(uid);
        erranteVisitedThisMission.remove(uid);
        AbyssBlade.giveAbyssBlade(player, plugin);
        ShadowHunterNPC npc = activeNPCs.remove(uid);
        if (npc != null)
            npc.despawnWithEffect();
        saveData();
        Bukkit.broadcastMessage("§4§l✦ §c" + player.getName()
                + " §fha completado §4§lEl Despertar del Abismo §fy obtenido la §4§lDevoradora de Almas§f!");
    }

    // ==================== MISIÓN 3: PROGRESIÓN ====================

    public void onQuest3DialogueAccepted(Player player) {
        UUID uid = player.getUniqueId();
        questStates.put(uid, QuestState.QUEST3_TRIALS);
        if (player.getAllowFlight()) {
            player.setAllowFlight(false);
            player.setFlying(false);
            player.sendMessage(SmallCaps.convert("§c§l⚔ §fNo puedes volar durante la misión del Arquitecto."));
        }
        ShadowHunterNPC npc = activeNPCs.get(uid);
        if (npc != null)
            npc.cancelDespawnTimer();
        VoidTrials trials = new VoidTrials(plugin, this, player, npc);
        activeTrials3.put(uid, trials);
        trials.start();
    }

    public void onQuest3TrialsCompleted(Player player) {
        UUID uid = player.getUniqueId();
        activeTrials3.remove(uid);
        questStates.put(uid, QuestState.QUEST3_BOSS);
        ShadowHunterNPC npc = activeNPCs.get(uid);
        VoidBoss boss = new VoidBoss(plugin, this, player, npc);
        activeBosses3.put(uid, boss);
        boss.start();
    }

    public void onQuest3BossPhaseDefeated(Player player, VoidArena arena) {
        UUID uid = player.getUniqueId();
        activeBosses3.remove(uid);
        questStates.put(uid, QuestState.QUEST3_POSTBOSS);
        ShadowHunterNPC npc = activeNPCs.get(uid);
        VoidPostBoss postBoss = new VoidPostBoss(plugin, this, player, npc, arena);
        activePostBoss3.put(uid, postBoss);
        postBoss.start();
    }

    public void onQuest3BossDefeated(Player player) {
        UUID uid = player.getUniqueId();
        activeBosses3.remove(uid);
        activePostBoss3.remove(uid);
        questStates.put(uid, QuestState.QUEST3_COMPLETED);
        completedQuest3.add(uid);
        erranteVisitedThisMission.remove(uid);
        VoidPickaxe.giveVoidPickaxe(player, plugin);
        ShadowHunterNPC npc = activeNPCs.remove(uid);
        if (npc != null)
            npc.despawnWithEffect();
        saveData();
        Bukkit.broadcastMessage("§0§l✦ §8" + player.getName()
                + " §fha completado §0§lEl Arquitecto del Vacío §fy obtenido la §0§lFractura del Vacío§f!");
    }

    // ==================== MISIÓN 5: PROGRESIÓN ====================

    public void onQuest5DialogueAccepted(Player player) {
        UUID uid = player.getUniqueId();
        questStates.put(uid, QuestState.QUEST5_PARKOUR);
        if (player.getAllowFlight()) {
            player.setAllowFlight(false);
            player.setFlying(false);
        }
        ShadowHunterNPC npc = activeNPCs.get(uid);
        if (npc != null) npc.cancelDespawnTimer();
        SkyParkour parkour = new SkyParkour(plugin, this, player, npc);
        activeParkours5.put(uid, parkour);
        
        // Register with listener for protection
        SkyParkourListener listener = plugin.getSkyParkourListener();
        if (listener != null) {
            listener.registerParkour(player, parkour);
        }
        
        parkour.start();
    }

    public void onQuest5Completed(Player player) {
        UUID uid = player.getUniqueId();
        activeParkours5.remove(uid);
        questStates.put(uid, QuestState.QUEST5_COMPLETED);
        completedQuest5.add(uid);
        erranteVisitedThisMission.remove(uid);
        activeNPCs.remove(uid); // NPC already despawned by SkyParkour.cleanup()
        saveData();
    }

    public void markQuest5Completed(UUID uid) {
        completedQuest5.add(uid);
        questStates.put(uid, QuestState.QUEST5_COMPLETED);
        saveData();
    }

    // ==================== MISIÓN 6: PROGRESIÓN ====================

    public void onQuest6DialogueAccepted(Player player) {
        UUID uid = player.getUniqueId();
        questStates.put(uid, QuestState.QUEST6_MEMORY_WALK);
        if (player.getAllowFlight()) {
            player.setAllowFlight(false);
            player.setFlying(false);
            player.sendMessage(SmallCaps.convert("§c§l⚔ §fNo puedes volar durante la misión de Lyra."));
        }
        ShadowHunterNPC npc = activeNPCs.get(uid);
        if (npc != null) npc.cancelDespawnTimer();
        MemoryWalk walk = new MemoryWalk(plugin, this, player, npc);
        activeMemoryWalks6.put(uid, walk);
        walk.start();
    }

    public void onQuest6TasksCompleted(Player player) {
        UUID uid = player.getUniqueId();
        activeMemoryWalks6.remove(uid);
        questStates.put(uid, QuestState.QUEST6_BOSS);
        ShadowHunterNPC npc = activeNPCs.get(uid);
        LyraBoss boss = new LyraBoss(plugin, this, player, npc);
        activeBosses6.put(uid, boss);
        boss.start();
    }

    public void onQuest6BossDefeated(Player player) {
        UUID uid = player.getUniqueId();
        activeBosses6.remove(uid);
        questStates.put(uid, QuestState.QUEST6_COMPLETED);
        completedQuest6.add(uid);
        erranteVisitedThisMission.remove(uid);
        saveData();
        VoidLeggings.giveVoidLeggings(player, plugin);
        ShadowHunterNPC npc = activeNPCs.remove(uid);
        if (npc != null) {
            // Despawn especial: Lyra asciende en paz
            if (npc.getVillager() != null && !npc.getVillager().isDead()) {
                org.bukkit.Location loc = npc.getVillager().getLocation();
                org.bukkit.World world = loc.getWorld();
                world.spawnParticle(org.bukkit.Particle.SOUL, loc.clone().add(0, 1, 0), 100, 0.5, 2, 0.5, 0.05);
                world.spawnParticle(org.bukkit.Particle.END_ROD, loc.clone().add(0, 2, 0), 60, 0.5, 3, 0.5, 0.15);
                world.spawnParticle(org.bukkit.Particle.DUST, loc.clone().add(0, 1, 0), 80, 1, 2, 1, 0,
                        new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(100, 200, 255), 2.5f));
                world.playSound(loc, org.bukkit.Sound.ENTITY_ALLAY_DEATH, 1.0f, 0.6f);
                world.playSound(loc, org.bukkit.Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.8f);
                world.playSound(loc, org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 0.5f);
            }
            npc.despawn();
        }
        saveData();
        Bukkit.broadcastMessage("§b§l✦ §d" + player.getName()
                + " §fha completado §b§lLágrimas del Vacío §fy obtenido las §b§lLágrimas de Lyra§f.");
        Bukkit.broadcastMessage(SmallCaps.convert("§7§oLyra finalmente descansa en paz..."));
    }

    // ==================== MISIÓN 4: PROGRESIÓN ====================

    public void onQuest4DialogueAccepted(Player player) {
        UUID uid = player.getUniqueId();
        questStates.put(uid, QuestState.QUEST4_BOSS);
        if (player.getAllowFlight()) {
            player.setAllowFlight(false);
            player.setFlying(false);
            player.sendMessage(SmallCaps.convert("§c§l⚔ §fNo puedes volar durante la última misión del Errante."));
        }
        ShadowHunterNPC npc = activeNPCs.get(uid);
        if (npc != null) npc.cancelDespawnTimer();
        ElegyBoss boss = new ElegyBoss(plugin, this, player, npc);
        activeBosses4.put(uid, boss);
        boss.start();
    }

    public void onQuest4BossDefeated(Player player) {
        UUID uid = player.getUniqueId();
        activeBosses4.remove(uid);
        questStates.put(uid, QuestState.QUEST4_COMPLETED);
        completedQuest4.add(uid);
        erranteVisitedThisMission.remove(uid);
        saveData(); // Guardar inmediatamente para evitar pérdida de progreso
        VoidChestplate.giveVoidChestplate(player, plugin);
        ShadowHunterNPC npc = activeNPCs.remove(uid);
        if (npc != null) {
            // Despawn especial triste: el Errante se desvanece para siempre
            if (npc.getVillager() != null && !npc.getVillager().isDead()) {
                org.bukkit.Location loc = npc.getVillager().getLocation();
                org.bukkit.World world = loc.getWorld();
                world.spawnParticle(org.bukkit.Particle.SOUL, loc.clone().add(0, 1, 0), 80, 0.5, 2, 0.5, 0.05);
                world.spawnParticle(org.bukkit.Particle.END_ROD, loc.clone().add(0, 2, 0), 40, 0.5, 2, 0.5, 0.1);
                world.spawnParticle(org.bukkit.Particle.DUST, loc.clone().add(0, 1, 0), 60, 1, 2, 1, 0,
                        new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(120, 50, 180), 2.5f));
                world.playSound(loc, org.bukkit.Sound.ENTITY_ALLAY_DEATH, 1.0f, 0.3f);
                world.playSound(loc, org.bukkit.Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.5f);
                world.playSound(loc, org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 0.3f);
            }
            npc.despawn();
        }
        saveData();
        Bukkit.broadcastMessage("§5§l✦ §d" + player.getName()
                + " §fha completado §5§lLa Elegía del Errante §fy obtenido la §5§lElegía del Errante§f.");
        Bukkit.broadcastMessage(SmallCaps.convert("§7§oEl Errante se ha desvanecido para siempre..."));
    }

    // ==================== MISIÓN 7: PROGRESIÓN ====================

    public void onQuest7DialogueAccepted(Player player) {
        UUID uid = player.getUniqueId();
        questStates.put(uid, QuestState.QUEST7_LABYRINTH);
        if (player.getAllowFlight()) {
            player.setAllowFlight(false);
            player.setFlying(false);
            player.sendMessage(SmallCaps.convert("§c§l⚔ §fNo puedes volar dentro del Vientre del Vacío."));
        }
        ShadowHunterNPC npc = activeNPCs.get(uid);
        if (npc != null) npc.cancelDespawnTimer();
        LabyrinthManager labyrinth = new LabyrinthManager(plugin, this, player, npc);
        activeLabyrinths7.put(uid, labyrinth);
        labyrinth.start();
    }

    public void onQuest7BossDefeated(Player player) {
        UUID uid = player.getUniqueId();
        activeLabyrinths7.remove(uid);
        questStates.put(uid, QuestState.QUEST7_COMPLETED);
        completedQuest7.add(uid);
        erranteVisitedThisMission.remove(uid);
        
        // Despawn the Errante NPC for this player
        ShadowHunterNPC npc = activeNPCs.remove(uid);
        if (npc != null) {
            npc.despawn();
        }
        
        saveData();
        Bukkit.broadcastMessage("§4§l☠ §c" + player.getName()
                + " §fha completado §4§lEl Vientre del Vacío §fy obtenido la §4§lPisada del Olvido§f!");
        Bukkit.broadcastMessage(SmallCaps.convert("§8§oEl Olvido ha sido destruido... las almas descansan."));
    }

    // ==================== MISIÓN 8: PROGRESIÓN ====================

    public void onQuest8DialogueAccepted(Player player) {
        UUID uid = player.getUniqueId();
        questStates.put(uid, QuestState.QUEST8_BOSS);
        if (player.getAllowFlight()) {
            player.setAllowFlight(false);
            player.setFlying(false);
            player.sendMessage(SmallCaps.convert("§c§l⚔ §fNo puedes volar durante el combate con el Heraldo."));
        }
        ShadowHunterNPC npc = activeNPCs.get(uid);
        if (npc != null) npc.cancelDespawnTimer();
        FrostBoss boss = new FrostBoss(plugin, this, player, npc);
        activeBosses8.put(uid, boss);
        boss.start();
    }

    public void onQuest8BossDefeated(Player player) {
        UUID uid = player.getUniqueId();
        activeBosses8.remove(uid);
        questStates.put(uid, QuestState.QUEST8_COMPLETED);
        completedQuest8.add(uid);
        erranteVisitedThisMission.remove(uid);
        saveData();
        VoidMace.giveVoidMace(player, plugin);
        ShadowHunterNPC npc = activeNPCs.remove(uid);
        if (npc != null) {
            if (npc.getVillager() != null && !npc.getVillager().isDead()) {
                org.bukkit.Location loc = npc.getVillager().getLocation();
                org.bukkit.World world = loc.getWorld();
                world.spawnParticle(org.bukkit.Particle.SNOWFLAKE, loc.clone().add(0, 1, 0), 100, 0.5, 2, 0.5, 0.1);
                world.spawnParticle(org.bukkit.Particle.END_ROD, loc.clone().add(0, 2, 0), 50, 0.5, 2, 0.5, 0.15);
                world.spawnParticle(org.bukkit.Particle.DUST, loc.clone().add(0, 1, 0), 80, 1, 2, 1, 0,
                        new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(150, 220, 255), 2.5f));
                world.playSound(loc, org.bukkit.Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f);
                world.playSound(loc, org.bukkit.Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.2f);
            }
            npc.despawn();
        }
        saveData();
        Bukkit.broadcastMessage("§b§l❄ §3" + player.getName()
                + " §fha completado §b§lEl Heraldo del Hielo Eterno §fy obtenido el §b§lMartillo del Cero Absoluto§f!");
        Bukkit.broadcastMessage(SmallCaps.convert("§3§oEl Frío Primordial ha sido domado para siempre..."));
    }

    // ==================== MISIÓN 9: PROGRESIÓN ====================

    public void onQuest9DialogueAccepted(Player player) {
        UUID uid = player.getUniqueId();
        questStates.put(uid, QuestState.QUEST9_BOSS);
        if (player.getAllowFlight()) {
            player.setAllowFlight(false);
            player.setFlying(false);
            player.sendMessage(SmallCaps.convert("§c§l⚔ §fNo puedes volar durante el combate con el Centinela."));
        }
        ShadowHunterNPC npc = activeNPCs.get(uid);
        if (npc != null) npc.cancelDespawnTimer();
        OriginBoss boss = new OriginBoss(plugin, this, player, npc);
        activeBosses9.put(uid, boss);
        boss.start();
    }

    public void onQuest9BossDefeated(Player player) {
        UUID uid = player.getUniqueId();
        activeBosses9.remove(uid);
        questStates.put(uid, QuestState.QUEST9_COMPLETED);
        completedQuest9.add(uid);
        erranteVisitedThisMission.remove(uid);
        saveData();
        WhiteHoleBlade.giveWhiteHoleBlade(player, plugin);
        ShadowHunterNPC npc = activeNPCs.remove(uid);
        if (npc != null) {
            if (npc.getVillager() != null && !npc.getVillager().isDead()) {
                org.bukkit.Location loc = npc.getVillager().getLocation();
                org.bukkit.World world = loc.getWorld();
                world.spawnParticle(org.bukkit.Particle.END_ROD, loc.clone().add(0, 1, 0), 150, 0.5, 2, 0.5, 0.2);
                world.spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 2, 0), 50, 0.5, 1, 0.5, 0.3);
                world.spawnParticle(org.bukkit.Particle.DUST, loc.clone().add(0, 1, 0), 100, 1, 2, 1, 0,
                        new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(255, 255, 200), 2.5f));
                world.playSound(loc, org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 2.0f);
                world.playSound(loc, org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
            }
            npc.despawn();
        }
        saveData();
        Bukkit.broadcastMessage("§e§l✦ §6" + player.getName()
                + " §fha completado §e§lEl Origen de la Luz §fy obtenido la §e§lEstrella del Origen§f!");
        Bukkit.broadcastMessage(SmallCaps.convert("§e§oDonde el Abismo devora, la Estrella renace..."));
    }

    // ==================== MISIÓN 10: PROGRESIÓN ====================

    public void onQuest10DialogueAccepted(Player player) {
        UUID uid = player.getUniqueId();
        questStates.put(uid, QuestState.QUEST10_BOSS);
        if (player.getAllowFlight()) {
            player.setAllowFlight(false);
            player.setFlying(false);
            player.sendMessage(SmallCaps.convert("§c§l⚔ §fNo puedes volar durante el último combate."));
        }
        ShadowHunterNPC npc = activeNPCs.get(uid);
        if (npc != null) npc.cancelDespawnTimer();
        FinalBoss boss = new FinalBoss(plugin, this, player, npc);
        activeBosses10.put(uid, boss);
        boss.start();
    }

    public void onQuest10BossDefeated(Player player) {
        UUID uid = player.getUniqueId();
        activeBosses10.remove(uid);
        questStates.put(uid, QuestState.QUEST10_COMPLETED);
        completedQuest10.add(uid);
        erranteVisitedThisMission.remove(uid);
        saveData();
        ErranteHelmet.giveErranteHelmet(player, plugin);
        ShadowHunterNPC npc = activeNPCs.remove(uid);
        if (npc != null) {
            npc.despawn();
        }
        saveData();
        Bukkit.broadcastMessage("§5§l✦ §d" + player.getName()
                + " §fha completado §5§lEl Fin §fy obtenido el §5§lCasco del Errante§f.");
        Bukkit.broadcastMessage(SmallCaps.convert("§7§oEl Errante descansa en paz... para siempre."));
        Bukkit.broadcastMessage(SmallCaps.convert("§5§o\"No me olvides... por favor.\""));
    }

    // ==================== CLEANUP ====================

    public void resetNPC(UUID uid) {
        ShadowHunterNPC npc = activeNPCs.remove(uid);
        if (npc != null) npc.despawn();
        // Reset state to allow natural re-spawn (keep quest progress)
        QuestState state = questStates.getOrDefault(uid, QuestState.NONE);
        if (state == QuestState.NPC_SPAWNED) questStates.put(uid, QuestState.NONE);
        else if (state == QuestState.QUEST2_NPC_SPAWNED) questStates.put(uid, QuestState.COMPLETED);
        else if (state == QuestState.QUEST3_NPC_SPAWNED) questStates.put(uid, QuestState.QUEST2_COMPLETED);
        else if (state == QuestState.QUEST4_NPC_SPAWNED) questStates.put(uid, QuestState.QUEST3_COMPLETED);
        else if (state == QuestState.QUEST5_NPC_SPAWNED) questStates.put(uid, QuestState.QUEST4_COMPLETED);
        else if (state == QuestState.QUEST6_NPC_SPAWNED) questStates.put(uid, QuestState.QUEST5_COMPLETED);
        else if (state == QuestState.QUEST7_NPC_SPAWNED) questStates.put(uid, QuestState.QUEST6_COMPLETED);
        else if (state == QuestState.QUEST8_NPC_SPAWNED) questStates.put(uid, QuestState.QUEST7_COMPLETED);
        else if (state == QuestState.QUEST9_NPC_SPAWNED) questStates.put(uid, QuestState.QUEST8_COMPLETED);
        else if (state == QuestState.QUEST10_NPC_SPAWNED) questStates.put(uid, QuestState.QUEST9_COMPLETED);
    }

    public void cleanupPlayer(UUID uid) {
        ShadowHunterNPC npc = activeNPCs.remove(uid);
        if (npc != null)
            npc.despawn();
        // Misión 1
        ShadowHunterCombat combat = activeCombats.remove(uid);
        if (combat != null)
            combat.cleanup();
        ShadowHunterRitual ritual = activeRituals.remove(uid);
        if (ritual != null)
            ritual.cleanup();
        ShadowHunterBoss boss = activeBosses.remove(uid);
        if (boss != null)
            boss.cleanup();
        // Misión 2
        AbyssExploration exploration = activeExplorations.remove(uid);
        if (exploration != null)
            exploration.cleanup();
        AbyssCombat combat2 = activeCombats2.remove(uid);
        if (combat2 != null)
            combat2.cleanup();
        AbyssRitual ritual2 = activeRituals2.remove(uid);
        if (ritual2 != null)
            ritual2.cleanup();
        AbyssBoss boss2 = activeBosses2.remove(uid);
        if (boss2 != null)
            boss2.cleanup();
        // Misión 3
        VoidTrials trials3 = activeTrials3.remove(uid);
        if (trials3 != null)
            trials3.cleanup();
        VoidBoss boss3 = activeBosses3.remove(uid);
        if (boss3 != null)
            boss3.cleanup();
        VoidPostBoss postBoss3 = activePostBoss3.remove(uid);
        if (postBoss3 != null)
            postBoss3.cleanup();
        // Misión 4
        ElegyBoss boss4 = activeBosses4.remove(uid);
        if (boss4 != null)
            boss4.cleanup();
        // Misión 5
        SkyParkour parkour5 = activeParkours5.remove(uid);
        if (parkour5 != null) {
            // Unregister from listener
            SkyParkourListener listener = plugin.getSkyParkourListener();
            if (listener != null) {
                listener.unregisterParkour(Bukkit.getPlayer(uid));
            }
            parkour5.cleanup();
        }
        // Misión 6
        MemoryWalk walk6 = activeMemoryWalks6.remove(uid);
        if (walk6 != null) walk6.cleanup();
        LyraBoss boss6 = activeBosses6.remove(uid);
        if (boss6 != null) boss6.cleanup();
        // Misión 7
        LabyrinthManager labyrinth7 = activeLabyrinths7.remove(uid);
        if (labyrinth7 != null) labyrinth7.cleanup();
        // Misión 8
        FrostBoss boss8 = activeBosses8.remove(uid);
        if (boss8 != null) boss8.cleanup();
        // Misión 9
        OriginBoss boss9 = activeBosses9.remove(uid);
        if (boss9 != null) boss9.cleanup();

        // Misión 10
        FinalBoss boss10 = activeBosses10.remove(uid);
        if (boss10 != null) boss10.cleanup();

        questStates.put(uid, QuestState.NONE);
    }

    // Limpiar trials y bosses de Mission 3
    public void cleanupQuest3(UUID uid) {
        VoidTrials trials = activeTrials3.remove(uid);
        if (trials != null)
            trials.cleanup();
        VoidBoss boss3 = activeBosses3.remove(uid);
        if (boss3 != null)
            boss3.cleanup();
        VoidPostBoss postBoss3 = activePostBoss3.remove(uid);
        if (postBoss3 != null)
            postBoss3.cleanup();
    }

    // Limpiar boss de Mission 4
    public void cleanupQuest4(UUID uid) {
        ElegyBoss boss4 = activeBosses4.remove(uid);
        if (boss4 != null)
            boss4.cleanup();
    }

    // ==================== GETTERS ====================

    public QuestState getState(UUID uid) {
        return questStates.getOrDefault(uid, QuestState.NONE);
    }

    public ShadowHunterNPC getNPC(UUID uid) {
        return activeNPCs.get(uid);
    }

    public ShadowHunterCombat getCombat(UUID uid) {
        return activeCombats.get(uid);
    }

    public ShadowHunterRitual getRitual(UUID uid) {
        return activeRituals.get(uid);
    }

    public ShadowHunterBoss getBoss(UUID uid) {
        return activeBosses.get(uid);
    }

    public boolean hasCompleted(UUID uid) {
        return completed.contains(uid);
    }

    public boolean hasCompletedQuest2(UUID uid) {
        return completedQuest2.contains(uid);
    }

    public void markCompleted(UUID uid) {
        completed.add(uid);
        questStates.put(uid, QuestState.COMPLETED);
        erranteVisitedThisMission.remove(uid);
        saveData();
    }

    public void markQuest2Completed(UUID uid) {
        if (!completed.contains(uid)) completed.add(uid);
        completedQuest2.add(uid);
        questStates.put(uid, QuestState.QUEST2_COMPLETED);
        erranteVisitedThisMission.remove(uid);
        cleanupPlayer(uid);
        saveData();
    }

    public void markQuest3Completed(UUID uid) {
        if (!completed.contains(uid)) completed.add(uid);
        if (!completedQuest2.contains(uid)) completedQuest2.add(uid);
        completedQuest3.add(uid);
        questStates.put(uid, QuestState.QUEST3_COMPLETED);
        erranteVisitedThisMission.remove(uid);
        cleanupPlayer(uid);
        saveData();
    }

    public void markQuest4Completed(UUID uid) {
        if (!completed.contains(uid)) completed.add(uid);
        if (!completedQuest2.contains(uid)) completedQuest2.add(uid);
        if (!completedQuest3.contains(uid)) completedQuest3.add(uid);
        completedQuest4.add(uid);
        questStates.put(uid, QuestState.QUEST4_COMPLETED);
        erranteVisitedThisMission.remove(uid);
        cleanupPlayer(uid);
        saveData();
    }

    // Misión 2 getters
    public AbyssExploration getExploration(UUID uid) {
        return activeExplorations.get(uid);
    }

    public AbyssCombat getCombat2(UUID uid) {
        return activeCombats2.get(uid);
    }

    public AbyssRitual getRitual2(UUID uid) {
        return activeRituals2.get(uid);
    }

    public AbyssBoss getBoss2(UUID uid) {
        return activeBosses2.get(uid);
    }

    public void addCooldown(UUID uid, long duration) {
        declineCooldowns.put(uid, System.currentTimeMillis() + duration);
        erranteVisitedThisMission.remove(uid);
        saveData();
    }

    // Misión 3 getters
    public VoidTrials getTrials3(UUID uid) {
        return activeTrials3.get(uid);
    }

    public VoidBoss getBoss3(UUID uid) {
        return activeBosses3.get(uid);
    }

    public VoidPostBoss getPostBoss3(UUID uid) {
        return activePostBoss3.get(uid);
    }

    public boolean hasCompletedQuest3(UUID uid) {
        return completedQuest3.contains(uid);
    }

    public boolean isQuest3Enabled() {
        return quest3Enabled;
    }

    // Misión 4 getters
    public ElegyBoss getBoss4(UUID uid) {
        return activeBosses4.get(uid);
    }

    public java.util.Collection<ElegyBoss> getAllActiveBoss4() {
        return activeBosses4.values();
    }

    public java.util.Collection<ShadowHunterBoss> getAllActiveBoss1() {
        return activeBosses.values();
    }

    public java.util.Collection<AbyssBoss> getAllActiveBoss2() {
        return activeBosses2.values();
    }

    public java.util.Collection<VoidBoss> getAllActiveBoss3() {
        return activeBosses3.values();
    }

    public java.util.Collection<VoidPostBoss> getAllActivePostBoss3() {
        return activePostBoss3.values();
    }

    public boolean hasCompletedQuest4(UUID uid) {
        return completedQuest4.contains(uid);
    }

    public boolean isQuest4Enabled() {
        return quest4Enabled;
    }

    public void setQuest4Enabled(boolean enabled) {
        this.quest4Enabled = enabled;
        saveData();
    }

    // Misión 5 getters
    public SkyParkour getParkour5(UUID uid) {
        return activeParkours5.get(uid);
    }

    public java.util.Collection<SkyParkour> getAllActiveParkour5() {
        return activeParkours5.values();
    }

    public boolean hasCompletedQuest5(UUID uid) {
        return completedQuest5.contains(uid);
    }

    public boolean isQuest5Enabled() {
        return quest5Enabled;
    }

    public void setQuest5Enabled(boolean enabled) {
        this.quest5Enabled = enabled;
        saveData();
    }

    // Misión 6 getters
    public MemoryWalk getMemoryWalk6(UUID uid) {
        return activeMemoryWalks6.get(uid);
    }

    public LyraBoss getBoss6(UUID uid) {
        return activeBosses6.get(uid);
    }

    public java.util.Collection<LyraBoss> getAllActiveBoss6() {
        return activeBosses6.values();
    }

    public boolean hasCompletedQuest6(UUID uid) {
        return completedQuest6.contains(uid);
    }

    public boolean isQuest6Enabled() {
        return quest6Enabled;
    }

    public void setQuest6Enabled(boolean enabled) {
        this.quest6Enabled = enabled;
        saveData();
    }

    public void markQuest6Completed(UUID uid) {
        if (!completed.contains(uid)) completed.add(uid);
        if (!completedQuest2.contains(uid)) completedQuest2.add(uid);
        if (!completedQuest3.contains(uid)) completedQuest3.add(uid);
        if (!completedQuest4.contains(uid)) completedQuest4.add(uid);
        if (!completedQuest5.contains(uid)) completedQuest5.add(uid);
        completedQuest6.add(uid);
        questStates.put(uid, QuestState.QUEST6_COMPLETED);
        erranteVisitedThisMission.remove(uid);
        cleanupPlayer(uid);
        saveData();
    }

    // Misión 7 getters
    public LabyrinthManager getLabyrinth7(UUID uid) {
        return activeLabyrinths7.get(uid);
    }

    public boolean hasCompletedQuest7(UUID uid) {
        return completedQuest7.contains(uid);
    }

    public boolean isQuest7Enabled() {
        return quest7Enabled;
    }

    public void setQuest7Enabled(boolean enabled) {
        this.quest7Enabled = enabled;
        saveData();
    }

    public void markQuest7Completed(UUID uid) {
        if (!completed.contains(uid)) completed.add(uid);
        if (!completedQuest2.contains(uid)) completedQuest2.add(uid);
        if (!completedQuest3.contains(uid)) completedQuest3.add(uid);
        if (!completedQuest4.contains(uid)) completedQuest4.add(uid);
        if (!completedQuest5.contains(uid)) completedQuest5.add(uid);
        if (!completedQuest6.contains(uid)) completedQuest6.add(uid);
        completedQuest7.add(uid);
        questStates.put(uid, QuestState.QUEST7_COMPLETED);
        erranteVisitedThisMission.remove(uid);
        cleanupPlayer(uid);
        saveData();
    }

    // Misión 8 getters
    public FrostBoss getBoss8(UUID uid) {
        return activeBosses8.get(uid);
    }

    public java.util.Collection<FrostBoss> getAllActiveBoss8() {
        return activeBosses8.values();
    }

    public boolean hasCompletedQuest8(UUID uid) {
        return completedQuest8.contains(uid);
    }

    public boolean isQuest8Enabled() {
        return quest8Enabled;
    }

    public void setQuest8Enabled(boolean enabled) {
        this.quest8Enabled = enabled;
        saveData();
    }

    public void markQuest8Completed(UUID uid) {
        if (!completed.contains(uid)) completed.add(uid);
        if (!completedQuest2.contains(uid)) completedQuest2.add(uid);
        if (!completedQuest3.contains(uid)) completedQuest3.add(uid);
        if (!completedQuest4.contains(uid)) completedQuest4.add(uid);
        if (!completedQuest5.contains(uid)) completedQuest5.add(uid);
        if (!completedQuest6.contains(uid)) completedQuest6.add(uid);
        if (!completedQuest7.contains(uid)) completedQuest7.add(uid);
        completedQuest8.add(uid);
        questStates.put(uid, QuestState.QUEST8_COMPLETED);
        erranteVisitedThisMission.remove(uid);
        cleanupPlayer(uid);
        saveData();
    }

    // Misión 9 getters
    public OriginBoss getBoss9(UUID uid) {
        return activeBosses9.get(uid);
    }

    public java.util.Collection<OriginBoss> getAllActiveBoss9() {
        return activeBosses9.values();
    }

    public boolean hasCompletedQuest9(UUID uid) {
        return completedQuest9.contains(uid);
    }

    public boolean isQuest9Enabled() {
        return quest9Enabled;
    }

    public void setQuest9Enabled(boolean enabled) {
        this.quest9Enabled = enabled;
        saveData();
    }

    public void markQuest9Completed(UUID uid) {
        if (!completed.contains(uid)) completed.add(uid);
        if (!completedQuest2.contains(uid)) completedQuest2.add(uid);
        if (!completedQuest3.contains(uid)) completedQuest3.add(uid);
        if (!completedQuest4.contains(uid)) completedQuest4.add(uid);
        if (!completedQuest5.contains(uid)) completedQuest5.add(uid);
        if (!completedQuest6.contains(uid)) completedQuest6.add(uid);
        if (!completedQuest7.contains(uid)) completedQuest7.add(uid);
        if (!completedQuest8.contains(uid)) completedQuest8.add(uid);
        completedQuest9.add(uid);
        questStates.put(uid, QuestState.QUEST9_COMPLETED);
        erranteVisitedThisMission.remove(uid);
        cleanupPlayer(uid);
        saveData();
    }

    // Misión 10 getters
    public FinalBoss getBoss10(UUID uid) {
        return activeBosses10.get(uid);
    }

    public java.util.Collection<FinalBoss> getAllActiveBoss10() {
        return activeBosses10.values();
    }

    public boolean hasCompletedQuest10(UUID uid) {
        return completedQuest10.contains(uid);
    }

    public boolean isQuest10Enabled() {
        return quest10Enabled;
    }

    public void setQuest10Enabled(boolean enabled) {
        this.quest10Enabled = enabled;
        saveData();
    }

    public void markQuest10Completed(UUID uid) {
        if (!completed.contains(uid)) completed.add(uid);
        if (!completedQuest2.contains(uid)) completedQuest2.add(uid);
        if (!completedQuest3.contains(uid)) completedQuest3.add(uid);
        if (!completedQuest4.contains(uid)) completedQuest4.add(uid);
        if (!completedQuest5.contains(uid)) completedQuest5.add(uid);
        if (!completedQuest6.contains(uid)) completedQuest6.add(uid);
        if (!completedQuest7.contains(uid)) completedQuest7.add(uid);
        if (!completedQuest8.contains(uid)) completedQuest8.add(uid);
        if (!completedQuest9.contains(uid)) completedQuest9.add(uid);
        completedQuest10.add(uid);
        questStates.put(uid, QuestState.QUEST10_COMPLETED);
        erranteVisitedThisMission.remove(uid);
        cleanupPlayer(uid);
        saveData();
    }

    /**
     * Estima cuándo visitará el Errante al jugador.
     * Basado en si ya fue visitado, si tiene cooldown, etc.
     */
    public String getNextErranteVisitEstimate(UUID uid) {
        // Si ya completó todas las misiones
        boolean allDone = completed.contains(uid) && completedQuest2.contains(uid)
                && (!quest3Enabled || completedQuest3.contains(uid))
                && (!quest4Enabled || completedQuest4.contains(uid))
                && (!quest5Enabled || completedQuest5.contains(uid))
                && (!quest6Enabled || completedQuest6.contains(uid))
                && (!quest7Enabled || completedQuest7.contains(uid))
                && (!quest8Enabled || completedQuest8.contains(uid))
                && (!quest9Enabled || completedQuest9.contains(uid))
                && (!quest10Enabled || completedQuest10.contains(uid));
        if (allDone)
            return "§a✔ Todas las misiones completadas";

        // Si tiene misión activa
        QuestState state = questStates.getOrDefault(uid, QuestState.NONE);
        if (state != QuestState.NONE && state != QuestState.COMPLETED && state != QuestState.QUEST2_COMPLETED)
            return "§e▶ Misión en curso";

        // Si ya fue visitado para esta misión y rechazó
        if (erranteVisitedThisMission.contains(uid)) {
            Long cooldownEnd = declineCooldowns.get(uid);
            if (cooldownEnd != null && System.currentTimeMillis() < cooldownEnd) {
                long secsLeft = (cooldownEnd - System.currentTimeMillis()) / 1000;
                long hours = secsLeft / 3600;
                long mins = (secsLeft % 3600) / 60;
                return "§c" + hours + "h " + mins + "m §7(rechazaste)";
            }
            return "§cYa te visitó. Espera a la próxima oportunidad.";
        }

        // En cooldown por rechazar
        Long cooldownEnd = declineCooldowns.get(uid);
        if (cooldownEnd != null && System.currentTimeMillis() < cooldownEnd) {
            long secsLeft = (cooldownEnd - System.currentTimeMillis()) / 1000;
            long hours = secsLeft / 3600;
            long mins = (secsLeft % 3600) / 60;
            return "§c" + hours + "h " + mins + "m §7(cooldown)";
        }

        // Elegible: 20% chance cada 5 min = promedio ~25 min
        return "§e~5-25 minutos §7(aleatorio)";
    }

    public void setQuest3Enabled(boolean enabled) {
        this.quest3Enabled = enabled;
        saveData();
    }

    public CoreProtectPlugin getPlugin() {
        return plugin;
    }

    /**
     * Devuelve todos los UUIDs de jugadores que tienen algún boss activo.
     */
    public java.util.Set<UUID> getAllActiveBossPlayerIds() {
        java.util.Set<UUID> ids = new java.util.HashSet<>();
        ids.addAll(activeBosses.keySet());
        ids.addAll(activeBosses2.keySet());
        ids.addAll(activeBosses3.keySet());
        ids.addAll(activeBosses4.keySet());
        ids.addAll(activeBosses6.keySet());
        for (Map.Entry<UUID, LabyrinthManager> entry : activeLabyrinths7.entrySet()) {
            if (entry.getValue().isBossPhase()) ids.add(entry.getKey());
        }
        ids.addAll(activeBosses8.keySet());
        ids.addAll(activeBosses10.keySet());
        return ids;
    }

    /**
     * Verifica si el mundo es Nether o End (no se pueden iniciar misiones ahí).
     */
    public static boolean isNetherOrEnd(org.bukkit.World world) {
        if (world == null) return false;
        return world.getEnvironment() == org.bukkit.World.Environment.NETHER
                || world.getEnvironment() == org.bukkit.World.Environment.THE_END;
    }

    public void markArenaRespawn(UUID uid, VoidArena arena) {
        if (arena != null)
            arenaRespawns.put(uid, arena);
    }
    
    public void markDirectRespawn(UUID uid, org.bukkit.Location location) {
        if (location != null)
            directRespawns.put(uid, location);
    }

    public org.bukkit.Location getArenaRespawnLocation(UUID uid) {
        // Primero verificar respawn directo (Quest 4)
        org.bukkit.Location directLoc = directRespawns.remove(uid);
        if (directLoc != null) {
            return directLoc;
        }
        
        // Luego verificar arena (Quest 3)
        VoidArena arena = arenaRespawns.remove(uid);
        if (arena != null && arena.isActive()) {
            return arena.getFloorCenter();
        }
        return null;
    }

    // ==================== VOID ARENA PROTECTION ====================

    public boolean isInsideAnyVoidArena(org.bukkit.Location loc) {
        for (VoidBoss boss : activeBosses3.values()) {
            if (boss.getArena() != null && boss.getArena().isActive() && boss.getArena().getCenter() != null) {
                if (loc.getWorld().equals(boss.getArena().getCenter().getWorld())
                        && loc.distance(boss.getArena().getCenter()) <= 15)
                    return true;
            }
        }
        for (VoidPostBoss pb : activePostBoss3.values()) {
            if (pb.getArena() != null && pb.getArena().isActive() && pb.getArena().getCenter() != null) {
                if (loc.getWorld().equals(pb.getArena().getCenter().getWorld())
                        && loc.distance(pb.getArena().getCenter()) <= 15)
                    return true;
            }
        }
        for (VoidPostBoss pb : activePostBoss3.values()) {
            if (pb.getArena() != null && pb.getArena().isActive() && pb.getArena().getCenter() != null) {
                if (loc.getWorld().equals(pb.getArena().getCenter().getWorld())
                        && loc.distance(pb.getArena().getCenter()) <= 15)
                    return true;
            }
        }
        return false;
    }

    private void enforceVoidArenaBounds(UUID owner, Player p, VoidArena arena) {
        if (arena == null || !arena.isActive() || arena.getCenter() == null)
            return;
        
        // Los OPs pueden entrar y salir libremente
        if (p.isOp()) return;
        
        org.bukkit.Location center = arena.getCenter();
        if (!p.getWorld().equals(center.getWorld()))
            return;

        double dist = p.getLocation().distance(center);

        if (p.getUniqueId().equals(owner)) {
            // El dueño NO PUEDE SALIR
            if (dist > 14) {
                p.teleport(arena.getFloorCenter());
                p.sendMessage(SmallCaps.convert("§c§l⚠ §cNo puedes escapar de la Expansión de Dominio."));
                p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            }
        } else {
            // Los demás NO PUEDEN ENTRAR
            if (dist <= 15) {
                // Expulsar al suelo
                org.bukkit.Location ground = center.clone();
                ground.setY(64);
                for (int y = 190; y > 0; y--) {
                    org.bukkit.block.Block b = center.getWorld().getBlockAt(ground.getBlockX(), y, ground.getBlockZ());
                    if (b.getType().isSolid() && b.getType() != org.bukkit.Material.BLACK_CONCRETE) {
                        ground.setY(y + 1);
                        break;
                    }
                }
                p.teleport(ground);
                p.sendMessage(SmallCaps.convert("§c§l⚠ §cHas sido expulsado de la Expansión de Dominio de otro jugador."));
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            }
        }
    }

    // ==================== MISSION MOB TRACKING ====================

    public boolean isMissionMob(UUID entityId) {
        // Checking NPCs
        for (ShadowHunterNPC npc : activeNPCs.values()) {
            if (npc.getVillager() != null && npc.getVillager().getUniqueId().equals(entityId))
                return true;
        }
        for (ShadowHunterCombat sc : activeCombats.values()) {
            if (sc.isMobFromQuest(entityId))
                return true;
        }
        for (ShadowHunterBoss sb : activeBosses.values()) {
            if (sb.isBossEntity(entityId) || sb.isSummonedMob(entityId))
                return true;
        }
        for (AbyssCombat ac : activeCombats2.values()) {
            if (ac.isMobFromQuest(entityId))
                return true;
        }
        for (AbyssBoss ab : activeBosses2.values()) {
            if (ab.isBossEntity(entityId) || ab.isSummonedMob(entityId))
                return true;
        }
        for (VoidBoss vb : activeBosses3.values()) {
            if (vb.isBossEntity(entityId) || vb.isSpawnedEntity(entityId))
                return true;
        }
        for (VoidPostBoss pb : activePostBoss3.values()) {
            if (pb.isBossEntity(entityId) || pb.isSpawnedEntity(entityId))
                return true;
        }
        for (ElegyBoss eb : activeBosses4.values()) {
            if (eb.isBossEntity(entityId) || eb.isSummonedMob(entityId))
                return true;
        }
        for (SkyParkour sp : activeParkours5.values()) {
            if (sp.isSpawnedEntity(entityId))
                return true;
        }
        for (LyraBoss lb : activeBosses6.values()) {
            if (lb.isBossEntity(entityId) || lb.isSummonedMob(entityId))
                return true;
        }
        for (MemoryWalk mw : activeMemoryWalks6.values()) {
            if (mw.isSpawnedEntity(entityId))
                return true;
        }
        for (FrostBoss fb : activeBosses8.values()) {
            if (fb.isBossEntity(entityId) || fb.isSummonedMob(entityId))
                return true;
        }
        for (FinalBoss fnb : activeBosses10.values()) {
            if (fnb.isBossEntity(entityId) || fnb.isSummonedMob(entityId))
                return true;
        }
        return false;
    } // ==================== PERSISTENCIA ====================

    public void saveData() {
        FileConfiguration config = new YamlConfiguration();

        for (UUID uid : completed) {
            config.set("completed." + uid.toString(), true);
        }

        for (UUID uid : completedQuest2) {
            config.set("completedQuest2." + uid.toString(), true);
        }

        for (Map.Entry<UUID, Long> entry : declineCooldowns.entrySet()) {
            config.set("cooldowns." + entry.getKey().toString(), entry.getValue());
        }

        for (UUID uid : completedQuest3) {
            config.set("completedQuest3." + uid.toString(), true);
        }
        config.set("quest3Enabled", quest3Enabled);

        for (UUID uid : completedQuest4) {
            config.set("completedQuest4." + uid.toString(), true);
        }
        config.set("quest4Enabled", quest4Enabled);

        for (UUID uid : completedQuest5) {
            config.set("completedQuest5." + uid.toString(), true);
        }
        config.set("quest5Enabled", quest5Enabled);

        for (UUID uid : completedQuest6) {
            config.set("completedQuest6." + uid.toString(), true);
        }
        config.set("quest6Enabled", quest6Enabled);

        for (UUID uid : completedQuest7) {
            config.set("completedQuest7." + uid.toString(), true);
        }
        config.set("quest7Enabled", quest7Enabled);

        for (UUID uid : completedQuest8) {
            config.set("completedQuest8." + uid.toString(), true);
        }
        config.set("quest8Enabled", quest8Enabled);

        for (UUID uid : completedQuest9) {
            config.set("completedQuest9." + uid.toString(), true);
        }
        config.set("quest9Enabled", quest9Enabled);

        for (UUID uid : completedQuest10) {
            config.set("completedQuest10." + uid.toString(), true);
        }
        config.set("quest10Enabled", quest10Enabled);

        try {
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("[ShadowHunter] Error al guardar datos: " + e.getMessage());
        }
    }

    // Añadir persistencia quest 3 en saveData
    private void saveQuest3Data(org.bukkit.configuration.file.FileConfiguration config) {
        for (UUID uid : completedQuest3) {
            config.set("completedQuest3." + uid.toString(), true);
        }
        config.set("quest3Enabled", quest3Enabled);
    }

    public void loadData() {
        if (!dataFile.exists())
            return;

        FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);

        // Completados
        if (config.contains("completed")) {
            for (String key : config.getConfigurationSection("completed").getKeys(false)) {
                try {
                    completed.add(UUID.fromString(key));
                } catch (Exception ignored) {
                }
            }
        }

        // Cooldowns
        if (config.contains("cooldowns")) {
            for (String key : config.getConfigurationSection("cooldowns").getKeys(false)) {
                try {
                    UUID uid = UUID.fromString(key);
                    long cooldown = config.getLong("cooldowns." + key);
                    if (System.currentTimeMillis() < cooldown) {
                        declineCooldowns.put(uid, cooldown);
                    }
                } catch (Exception ignored) {
                }
            }
        }

        // Completados Quest 2
        if (config.contains("completedQuest2")) {
            for (String key : config.getConfigurationSection("completedQuest2").getKeys(false)) {
                try {
                    completedQuest2.add(UUID.fromString(key));
                } catch (Exception ignored) {
                }
            }
        }

        // Completados Quest 3
        if (config.contains("completedQuest3")) {
            for (String key : config.getConfigurationSection("completedQuest3").getKeys(false)) {
                try {
                    completedQuest3.add(UUID.fromString(key));
                } catch (Exception ignored) {
                }
            }
        }

        // Completados Quest 4
        if (config.contains("completedQuest4")) {
            for (String key : config.getConfigurationSection("completedQuest4").getKeys(false)) {
                try {
                    completedQuest4.add(UUID.fromString(key));
                } catch (Exception ignored) {
                }
            }
        }

        // Completados Quest 5
        if (config.contains("completedQuest5")) {
            for (String key : config.getConfigurationSection("completedQuest5").getKeys(false)) {
                try {
                    completedQuest5.add(UUID.fromString(key));
                } catch (Exception ignored) {
                }
            }
        }

        // Completados Quest 6
        if (config.contains("completedQuest6")) {
            for (String key : config.getConfigurationSection("completedQuest6").getKeys(false)) {
                try {
                    completedQuest6.add(UUID.fromString(key));
                } catch (Exception ignored) {
                }
            }
        }

        // Completados Quest 7
        if (config.contains("completedQuest7")) {
            for (String key : config.getConfigurationSection("completedQuest7").getKeys(false)) {
                try {
                    completedQuest7.add(UUID.fromString(key));
                } catch (Exception ignored) {
                }
            }
        }

        // Completados Quest 8
        if (config.contains("completedQuest8")) {
            for (String key : config.getConfigurationSection("completedQuest8").getKeys(false)) {
                try {
                    completedQuest8.add(UUID.fromString(key));
                } catch (Exception ignored) {
                }
            }
        }

        // Completados Quest 9
        if (config.contains("completedQuest9")) {
            for (String key : config.getConfigurationSection("completedQuest9").getKeys(false)) {
                try {
                    completedQuest9.add(UUID.fromString(key));
                } catch (Exception ignored) {
                }
            }
        }

        // Completados Quest 10
        if (config.contains("completedQuest10")) {
            for (String key : config.getConfigurationSection("completedQuest10").getKeys(false)) {
                try {
                    completedQuest10.add(UUID.fromString(key));
                } catch (Exception ignored) {
                }
            }
        }

        plugin.getLogger().info("[ShadowHunter] Datos cargados: " + completed.size() + " M1, "
                + completedQuest2.size() + " M2, " + completedQuest3.size() + " M3, "
                + completedQuest4.size() + " M4, " + completedQuest5.size() + " M5, "
                + completedQuest6.size() + " M6, " + completedQuest7.size() + " M7, "
                + completedQuest8.size() + " M8, " + completedQuest9.size() + " M9, "
                + completedQuest10.size() + " M10.");

        // Cargar flags
        quest3Enabled = config.getBoolean("quest3Enabled", false);
        quest4Enabled = config.getBoolean("quest4Enabled", false);
        quest5Enabled = config.getBoolean("quest5Enabled", false);
        quest6Enabled = config.getBoolean("quest6Enabled", false);
        quest7Enabled = config.getBoolean("quest7Enabled", false);
        quest8Enabled = config.getBoolean("quest8Enabled", false);
        quest9Enabled = config.getBoolean("quest9Enabled", false);
        quest10Enabled = config.getBoolean("quest10Enabled", false);
    }

    public void shutdown() {
        // Limpiar todos los NPCs y combates activos
        for (UUID uid : new HashSet<>(activeNPCs.keySet())) {
            cleanupPlayer(uid);
        }
        saveData();
    }
}
