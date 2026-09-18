package com.moonlight.coreprotect.shadowhunter;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.Material;
import org.bukkit.Location;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Listener principal para todos los eventos de las misiones El Cazador de
 * Sombras y El Despertar del Abismo.
 */
public class ShadowHunterListener implements Listener {

    private final CoreProtectPlugin plugin;
    private final ShadowHunterManager manager;

    public ShadowHunterListener(CoreProtectPlugin plugin, ShadowHunterManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    // ==================== PROTECCIÓN DEL BOSS ELEGY ====================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onElegyBossEnvironmentalDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) return;
        org.bukkit.entity.Entity entity = event.getEntity();
        for (ElegyBoss boss : manager.getAllActiveBoss4()) {
            if (boss != null && boss.isActive() && boss.isBossEntity(entity)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFallingBlockLand(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock)) return;
        for (ElegyBoss boss : manager.getAllActiveBoss4()) {
            if (boss == null || !boss.isActive()) continue;
            org.bukkit.Location bossLoc = boss.getBossLocation();
            if (bossLoc != null && event.getBlock().getLocation().getWorld() == bossLoc.getWorld()
                    && event.getBlock().getLocation().distance(bossLoc) < 55) {
                event.setCancelled(true);
                event.getEntity().remove();
                return;
            }
        }
    }

    // ==================== INTERACCIÓN CON NPC ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();
        Entity entity = event.getRightClicked();

        // Quest 6: Right-click on lost souls to comfort them
        ShadowHunterManager.QuestState questState = manager.getState(uid);
        if (questState == ShadowHunterManager.QuestState.QUEST6_MEMORY_WALK) {
            MemoryWalk walk = manager.getMemoryWalk6(uid);
            if (walk != null && walk.isInSoulPhase()) {
                if (walk.tryComfortSoul(player)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        ShadowHunterNPC npc = manager.getNPC(uid);
        if (npc == null || npc.getVillager() == null)
            return;

        // Si clickea al NPC del Errante
        if (entity.getUniqueId().equals(npc.getVillager().getUniqueId())) {
            event.setCancelled(true);

            // Si el Errante está hablando, NO permitir interacción (no se puede saltar el diálogo)
            if (npc.isInDialogue()) {
                player.sendMessage(SmallCaps.convert("§5§l✦ §7El Errante está hablando... escúchale."));
                return;
            }

            // Mostrar lore del Errante
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("§8§m                                              "));
            player.sendMessage(SmallCaps.convert("  §5§l✦ El Errante §5§l✦"));
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("  §7Un ser enigmático que deambula entre"));
            player.sendMessage(SmallCaps.convert("  §7dimensiones, buscando guerreros dignos."));
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("  §8\"§7§oNadie sabe de dónde viene... pero todos"));
            player.sendMessage(SmallCaps.convert("  §7§olos que lo han seguido han cambiado para siempre.§8\""));
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("  §d§lOrigen: §7Desconocido"));
            player.sendMessage(SmallCaps.convert("  §d§lPropósito: §7Buscar al elegido"));
            player.sendMessage(SmallCaps.convert("  §d§lPoder: §7Manipulación de sombras"));
            player.sendMessage(SmallCaps.convert("§8§m                                              "));
            player.sendMessage("");
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 0.8f);

            if (questState == ShadowHunterManager.QuestState.NPC_SPAWNED
                    || questState == ShadowHunterManager.QuestState.QUEST2_NPC_SPAWNED
                    || questState == ShadowHunterManager.QuestState.QUEST3_NPC_SPAWNED
                    || questState == ShadowHunterManager.QuestState.QUEST4_NPC_SPAWNED
                    || questState == ShadowHunterManager.QuestState.QUEST5_NPC_SPAWNED
                    || questState == ShadowHunterManager.QuestState.QUEST6_NPC_SPAWNED) {
                // Mostrar opciones de aceptar/rechazar directamente
                npc.showDialogueOptions();
            }
        }
    }

    // ==================== PROTEGER NPC ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();

        // Proteger todos los NPCs del Errante
        if (entity instanceof Villager) {
            for (ShadowHunterNPC npc : getAllNPCs()) {
                if (npc.getVillager() != null && npc.getVillager().getUniqueId().equals(entity.getUniqueId())) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    private java.util.Collection<ShadowHunterNPC> getAllNPCs() {
        java.util.List<ShadowHunterNPC> npcs = new java.util.ArrayList<>();
        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            ShadowHunterNPC npc = manager.getNPC(p.getUniqueId());
            if (npc != null)
                npcs.add(npc);
        }
        return npcs;
    }

    // ==================== CHAT: ACEPTAR/RECHAZAR ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();

        // Bloquear chat de otros jugadores mientras el Errante habla
        // Si CUALQUIER otro jugador está en diálogo activo, silenciar el mensaje
        // globalmente
        // para no interrumpir la experiencia del diálogo
        ShadowHunterNPC ownNpc = manager.getNPC(uid);
        if (ownNpc != null && ownNpc.isInDialogue()) {
            String msg = event.getMessage().trim().toLowerCase();
            boolean isResponse = msg.equals("aceptar") || msg.equals("accept") || msg.equals("si")
                    || msg.equals("yes") || msg.equals("rechazar") || msg.equals("reject")
                    || msg.equals("no") || msg.equals("decline");
            boolean isSkip = msg.equals("saltar") || msg.equals("skip");
            if (isSkip) {
                event.setCancelled(true);
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> ownNpc.skipDialogue());
                return;
            }
            if (!isResponse) {
                event.setCancelled(true);
                org.bukkit.Bukkit.getScheduler().runTask(plugin,
                        () -> player.sendMessage(SmallCaps.convert("§8[§6Errante§8] §7Escribe §a\"aceptar\"§7, §c\"rechazar\"§7 o §e\"saltar\"§7.")));
                return;
            }
        }

        ShadowHunterManager.QuestState state = manager.getState(uid);

        // Misión 1
        if (state == ShadowHunterManager.QuestState.NPC_SPAWNED) {
            ShadowHunterNPC npc = manager.getNPC(uid);
            if (npc == null || !npc.isInDialogue())
                return;
            String msg = event.getMessage().trim().toLowerCase();
            if (msg.equals("aceptar") || msg.equals("accept") || msg.equals("si") || msg.equals("yes")) {
                event.setCancelled(true);
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    npc.cancelDialogue();
                    player.sendMessage(SmallCaps.convert("§5§l✦ §a§l¡Has aceptado el desafío!"));
                    player.sendMessage(SmallCaps.convert("§7Prepárate para la primera prueba..."));
                    manager.onDialogueAccepted(player);
                });
            } else if (msg.equals("rechazar") || msg.equals("reject") || msg.equals("no") || msg.equals("decline")) {
                event.setCancelled(true);
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    npc.cancelDialogue();
                    manager.onDialogueDeclined(player);
                });
            }
            return;
        }

        // Misión 2
        if (state == ShadowHunterManager.QuestState.QUEST2_NPC_SPAWNED) {
            ShadowHunterNPC npc = manager.getNPC(uid);
            if (npc == null || !npc.isInDialogue())
                return;
            String msg = event.getMessage().trim().toLowerCase();
            if (msg.equals("aceptar") || msg.equals("accept") || msg.equals("si") || msg.equals("yes")) {
                event.setCancelled(true);
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    npc.cancelDialogue();
                    player.sendMessage(SmallCaps.convert("§4§l✦ §a§l¡Has aceptado el desafío del Abismo!"));
                    player.sendMessage(SmallCaps.convert("§7Esto será mucho más peligroso..."));
                    manager.onQuest2DialogueAccepted(player);
                });
            } else if (msg.equals("rechazar") || msg.equals("reject") || msg.equals("no") || msg.equals("decline")) {
                event.setCancelled(true);
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    npc.cancelDialogue();
                    manager.onDialogueDeclined(player);
                });
            }
            return;
        }

        // Misión 3
        if (state == ShadowHunterManager.QuestState.QUEST3_NPC_SPAWNED) {
            ShadowHunterNPC npc = manager.getNPC(uid);
            if (npc == null || !npc.isInDialogue())
                return;
            String msg = event.getMessage().trim().toLowerCase();
            if (msg.equals("aceptar") || msg.equals("accept") || msg.equals("si") || msg.equals("yes")) {
                event.setCancelled(true);
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    npc.cancelDialogue();
                    player.sendMessage(SmallCaps.convert("§0§l✦ §a§l¡Has aceptado el desafío del Arquitecto!"));
                    player.sendMessage(SmallCaps.convert("§7Prepara tu mente..."));
                    manager.onQuest3DialogueAccepted(player);
                });
            } else if (msg.equals("rechazar") || msg.equals("reject") || msg.equals("no") || msg.equals("decline")) {
                event.setCancelled(true);
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    npc.cancelDialogue();
                    manager.onDialogueDeclined(player);
                });
            }
        }

        // Misión 4
        if (state == ShadowHunterManager.QuestState.QUEST4_NPC_SPAWNED) {
            ShadowHunterNPC npc = manager.getNPC(uid);
            if (npc == null || !npc.isInDialogue())
                return;
            String msg = event.getMessage().trim().toLowerCase();
            if (msg.equals("aceptar") || msg.equals("accept") || msg.equals("si") || msg.equals("yes")) {
                event.setCancelled(true);
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    npc.cancelDialogue();
                    player.sendMessage(SmallCaps.convert("§5§l✦ §a§l¡Has aceptado liberar al Errante!"));
                    player.sendMessage(SmallCaps.convert("§7Prepárate para enfrentar su pasado..."));
                    manager.onQuest4DialogueAccepted(player);
                });
            } else if (msg.equals("rechazar") || msg.equals("reject") || msg.equals("no") || msg.equals("decline")) {
                event.setCancelled(true);
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    npc.cancelDialogue();
                    manager.onDialogueDeclined(player);
                });
            }
        }

        // Misión 5
        if (state == ShadowHunterManager.QuestState.QUEST5_NPC_SPAWNED) {
            ShadowHunterNPC npc = manager.getNPC(uid);
            if (npc == null || !npc.isInDialogue())
                return;
            String msg = event.getMessage().trim().toLowerCase();
            if (msg.equals("aceptar") || msg.equals("accept") || msg.equals("si") || msg.equals("yes")) {
                event.setCancelled(true);
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    npc.cancelDialogue();
                    player.sendMessage(SmallCaps.convert("§d§l◈ §a§l¡Vas al Jardín del Cielo!"));
                    player.sendMessage(SmallCaps.convert("§7Supera las pruebas del jardín y derrota al Guardián..."));
                    manager.onQuest5DialogueAccepted(player);
                });
            } else if (msg.equals("rechazar") || msg.equals("reject") || msg.equals("no") || msg.equals("decline")) {
                event.setCancelled(true);
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    npc.cancelDialogue();
                    manager.onDialogueDeclined(player);
                });
            }
        }

        // Misión 6
        if (state == ShadowHunterManager.QuestState.QUEST6_NPC_SPAWNED) {
            ShadowHunterNPC npc = manager.getNPC(uid);
            if (npc == null || !npc.isInDialogue())
                return;
            String msg = event.getMessage().trim().toLowerCase();
            if (msg.equals("aceptar") || msg.equals("accept") || msg.equals("si") || msg.equals("yes")) {
                event.setCancelled(true);
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    npc.cancelDialogue();
                    player.sendMessage(SmallCaps.convert("§b§l♡ §a§l¡Ayudarás a Lyra a encontrar paz!"));
                    player.sendMessage(SmallCaps.convert("§7Camina por sus recuerdos y libera su dolor..."));
                    manager.onQuest6DialogueAccepted(player);
                });
            } else if (msg.equals("rechazar") || msg.equals("reject") || msg.equals("no") || msg.equals("decline")) {
                event.setCancelled(true);
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    npc.cancelDialogue();
                    manager.onDialogueDeclined(player);
                });
            }
        }

        // Misión 7
        if (state == ShadowHunterManager.QuestState.QUEST7_NPC_SPAWNED) {
            ShadowHunterNPC npc = manager.getNPC(uid);
            if (npc == null || !npc.isInDialogue())
                return;
            String msg = event.getMessage().trim().toLowerCase();
            if (msg.equals("aceptar") || msg.equals("accept") || msg.equals("si") || msg.equals("yes")) {
                event.setCancelled(true);
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    npc.cancelDialogue();
                    player.sendMessage(SmallCaps.convert("§4§l☠ §c§l¡Desciendes al Vientre del Vacío!"));
                    player.sendMessage(SmallCaps.convert("§4§oQue los dioses te amparen... si es que aún existen."));
                    manager.onQuest7DialogueAccepted(player);
                });
            } else if (msg.equals("rechazar") || msg.equals("reject") || msg.equals("no") || msg.equals("decline")) {
                event.setCancelled(true);
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    npc.cancelDialogue();
                    manager.onDialogueDeclined(player);
                });
            }
        }

        // Misión 8
        if (state == ShadowHunterManager.QuestState.QUEST8_NPC_SPAWNED) {
            ShadowHunterNPC npc = manager.getNPC(uid);
            if (npc == null || !npc.isInDialogue())
                return;
            String msg = event.getMessage().trim().toLowerCase();
            if (msg.equals("aceptar") || msg.equals("accept") || msg.equals("si") || msg.equals("yes")) {
                event.setCancelled(true);
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    npc.cancelDialogue();
                    player.sendMessage(SmallCaps.convert("§b§l❄ §3§l¡Te enfrentas al Heraldo del Hielo Eterno!"));
                    player.sendMessage(SmallCaps.convert("§3§oEl frío primordial te espera..."));
                    manager.onQuest8DialogueAccepted(player);
                });
            } else if (msg.equals("rechazar") || msg.equals("reject") || msg.equals("no") || msg.equals("decline")) {
                event.setCancelled(true);
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    npc.cancelDialogue();
                    manager.onDialogueDeclined(player);
                });
            }
        }

        // Misión 9
        if (state == ShadowHunterManager.QuestState.QUEST9_NPC_SPAWNED) {
            ShadowHunterNPC npc = manager.getNPC(uid);
            if (npc == null || !npc.isInDialogue())
                return;
            String msg = event.getMessage().trim().toLowerCase();
            if (msg.equals("aceptar") || msg.equals("accept") || msg.equals("si") || msg.equals("yes")) {
                event.setCancelled(true);
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    npc.cancelDialogue();
                    player.sendMessage(SmallCaps.convert("§e§l✦ §6§l¡Desafías al Centinela del Origen!"));
                    player.sendMessage(SmallCaps.convert("§e§oLa luz más pura te pondrá a prueba..."));
                    manager.onQuest9DialogueAccepted(player);
                });
            } else if (msg.equals("rechazar") || msg.equals("reject") || msg.equals("no") || msg.equals("decline")) {
                event.setCancelled(true);
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    npc.cancelDialogue();
                    manager.onDialogueDeclined(player);
                });
            }
        }

        // Misión 10
        if (state == ShadowHunterManager.QuestState.QUEST10_NPC_SPAWNED) {
            ShadowHunterNPC npc = manager.getNPC(uid);
            if (npc == null || !npc.isInDialogue() || !npc.isDialogueOptionsShown())
                return;
            String msg = event.getMessage().trim().toLowerCase();
            if (msg.equals("aceptar") || msg.equals("accept") || msg.equals("si") || msg.equals("yes")) {
                event.setCancelled(true);
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    npc.cancelDialogue();
                    player.sendMessage(SmallCaps.convert("§5§l✦ §7§l...Así sea."));
                    player.sendMessage(SmallCaps.convert("§7§oEl Errante cierra los ojos y asiente."));
                    manager.onQuest10DialogueAccepted(player);
                });
            } else if (msg.equals("rechazar") || msg.equals("reject") || msg.equals("no") || msg.equals("decline")) {
                event.setCancelled(true);
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    npc.cancelDialogue();
                    manager.onDialogueDeclined(player);
                });
            }
        }
    }

    // ==================== MUERTE DE MOBS (OLEADAS + BOSS) ====================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null)
            return;

        UUID uid = killer.getUniqueId();

        // Misión 1: Check combate de oleadas
        ShadowHunterCombat combat = manager.getCombat(uid);
        if (combat != null && combat.isActive() && combat.isMobFromQuest(entity.getUniqueId())) {
            // No dropear items de mobs de misión
            event.getDrops().clear();
            event.setDroppedExp(0);
            combat.onMobKilled(entity.getUniqueId());
            return;
        }

        // Misión 1: Check boss
        ShadowHunterBoss boss = manager.getBoss(uid);
        if (boss != null && boss.isActive()) {
            if (boss.isBossEntity(entity.getUniqueId())) {
                event.getDrops().clear();
                event.setDroppedExp(0);
                boss.onBossDeath();
                return;
            }
            if (boss.isSummonedMob(entity.getUniqueId())) {
                event.getDrops().clear();
                event.setDroppedExp(0);
                return;
            }
        }

        // Misión 2: Check combate de oleadas
        AbyssCombat combat2 = manager.getCombat2(uid);
        if (combat2 != null && combat2.isActive() && combat2.isMobFromQuest(entity.getUniqueId())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            combat2.onMobKilled(entity.getUniqueId());
            return;
        }

        // Misión 2: Check boss
        AbyssBoss boss2 = manager.getBoss2(uid);
        if (boss2 != null && boss2.isActive()) {
            if (boss2.isBossEntity(entity.getUniqueId())) {
                event.getDrops().clear();
                event.setDroppedExp(0);
                boss2.onBossDeath();
                return;
            }
            if (boss2.isSummonedMob(entity.getUniqueId())) {
                event.getDrops().clear();
                event.setDroppedExp(0);
                return;
            }
        }

        // Misión 3: Check boss
        VoidBoss boss3 = manager.getBoss3(uid);
        if (boss3 != null && boss3.isActive() && boss3.isBossEntity(entity)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            // Death handled internally by VoidBoss main loop
            return;
        }

        // Misión 4: Check boss
        ElegyBoss boss4 = manager.getBoss4(uid);
        if (boss4 != null && boss4.isActive()) {
            if (boss4.isBossEntity(entity.getUniqueId())) {
                event.getDrops().clear();
                event.setDroppedExp(0);
                boss4.onBossDeath();
                return;
            }
            if (boss4.isSummonedMob(entity.getUniqueId())) {
                event.getDrops().clear();
                event.setDroppedExp(0);
                return;
            }
        }

        // Misión 6: Check boss
        LyraBoss boss6 = manager.getBoss6(uid);
        if (boss6 != null && boss6.isActive()) {
            if (boss6.isBossEntity(entity.getUniqueId())) {
                event.getDrops().clear();
                event.setDroppedExp(0);
                // Death handled internally by LyraBoss
                return;
            }
            if (boss6.isSummonedMob(entity.getUniqueId())) {
                event.getDrops().clear();
                event.setDroppedExp(0);
                return;
            }
        }

        // Misión 8: Check boss
        FrostBoss boss8 = manager.getBoss8(uid);
        if (boss8 != null && boss8.isActive()) {
            if (boss8.isBossEntity(entity.getUniqueId())) {
                event.getDrops().clear();
                event.setDroppedExp(0);
                // Death handled internally by FrostBoss main loop
                return;
            }
            if (boss8.isSummonedMob(entity.getUniqueId())) {
                event.getDrops().clear();
                event.setDroppedExp(0);
                return;
            }
        }
    }

    // ==================== MISIÓN 4: INTERCEPTAR MUERTE DEL BOSS ====================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onElegyBossDamage(EntityDamageByEntityEvent event) {
        Player player = null;
        if (event.getDamager() instanceof Player) {
            player = (Player) event.getDamager();
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile) {
            org.bukkit.entity.Projectile proj = (org.bukkit.entity.Projectile) event.getDamager();
            if (proj.getShooter() instanceof Player) {
                player = (Player) proj.getShooter();
            }
        }
        if (player == null) return;
        UUID uid = player.getUniqueId();

        ElegyBoss boss4 = manager.getBoss4(uid);
        if (boss4 == null || !boss4.isActive() || !boss4.isBossEntity(event.getEntity())) return;

        LivingEntity bossEntity = (LivingEntity) event.getEntity();

        // Bloquear todo daño SOLO mientras la escena cinemática está activa
        if (boss4.isFinalSceneActive()) {
            event.setCancelled(true);
            bossEntity.setHealth(1);
            return;
        }

        // Si la escena ya terminó (finalSceneTriggered=true, finalSceneActive=false),
        // dejar pasar el daño para que el jugador pueda dar el golpe final
        if (boss4.isFinalSceneTriggered()) {
            return;
        }

        // Si el daño mataría al boss, reducir el daño para dejarlo en 1HP y disparar escena
        if (bossEntity.getHealth() - event.getFinalDamage() <= 1.0) {
            event.setCancelled(true);
            bossEntity.setHealth(1);
            boss4.onBossFatalDamage();
            return;
        }
    }

    // ==================== DAÑO AL BOSS ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player))
            return;
        Player player = (Player) event.getDamager();
        UUID uid = player.getUniqueId();

        // Misión 1 boss
        ShadowHunterBoss boss = manager.getBoss(uid);
        if (boss != null && boss.isActive() && event.getEntity() instanceof LivingEntity 
                && boss.isBossEntity(event.getEntity().getUniqueId())) {
            LivingEntity bossEntity = (LivingEntity) event.getEntity();
            double healthPercent = (bossEntity.getHealth() - event.getFinalDamage()) / bossEntity.getMaxHealth();
            ShadowHunterNPC npc = manager.getNPC(uid);
            if (npc != null) {
                if (healthPercent <= 0.75 && healthPercent > 0.70)
                    npc.say("\"\u00a1Sigue as\u00ed! Le est\u00e1s haciendo da\u00f1o.\"");
                else if (healthPercent <= 0.50 && healthPercent > 0.45)
                    npc.say("\"\u00a1La mitad! No bajes la guardia.\"");
                else if (healthPercent <= 0.15 && healthPercent > 0.10)
                    npc.say("\"\u00a7a\u00a1CASI! \u00a1Un poco m\u00e1s!\"");
            }
            return;
        }

        // Misión 2 boss
        AbyssBoss boss2 = manager.getBoss2(uid);
        if (boss2 != null && boss2.isActive() && event.getEntity() instanceof LivingEntity
                && boss2.isBossEntity(event.getEntity().getUniqueId())) {
            LivingEntity bossEntity = (LivingEntity) event.getEntity();
            double healthPercent = (bossEntity.getHealth() - event.getFinalDamage()) / bossEntity.getMaxHealth();
            ShadowHunterNPC npc = manager.getNPC(uid);
            if (npc != null) {
                if (healthPercent <= 0.80 && healthPercent > 0.75)
                    npc.say("\"\u00a1Le afectan tus golpes!\"");
                else if (healthPercent <= 0.50 && healthPercent > 0.45)
                    npc.say("\"\u00a1MITAD DE VIDA! \u00a1Sigue presionando!\"");
                else if (healthPercent <= 0.20 && healthPercent > 0.15)
                    npc.say("\"\u00a7c\u00a1CASI CAE! \u00a1\u00a1NO TE RINDAS!!\"");
            }
        }

        // Misión 3 boss
        VoidBoss boss3 = manager.getBoss3(uid);
        if (boss3 != null && boss3.isActive() && boss3.isBossEntity(event.getEntity())) {
            boss3.onBossDamaged();
        }

        // Misión 4 boss
        ElegyBoss boss4 = manager.getBoss4(uid);
        if (boss4 != null && boss4.isActive() && boss4.isBossEntity(event.getEntity())) {
            boss4.onBossDamaged();
        }

        // Misión 3 post-boss: fase 6 (almas) y fase 8 (boss final)
        VoidPostBoss postBoss3 = manager.getPostBoss3(uid);
        if (postBoss3 != null && postBoss3.isActive()) {
            // Fase 6: golpear ArmorStand (alma)
            if (postBoss3.getCurrentPhase() == 6 && event.getEntity() instanceof org.bukkit.entity.ArmorStand) {
                postBoss3.onSoulHit((org.bukkit.entity.ArmorStand) event.getEntity());
            }
            // Fase 8: daño al boss final renacido
            if (postBoss3.isFinalBoss(event.getEntity())) {
                postBoss3.onFinalBossDamaged();
            }
        }
    }

    // ==================== PLAYER DEATH ====================

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uid = player.getUniqueId();

        ShadowHunterBoss boss1 = manager.getBoss(uid);
        if (boss1 != null && boss1.isActive()) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);
            boss1.onPlayerDeath();
            player.sendMessage(SmallCaps.convert("§c§l✦ §cHas caído ante el Devorador. ¡Vuelves a la arena!"));
            if (boss1.getArenaCenter() != null) {
                manager.markDirectRespawn(uid, boss1.getArenaCenter());
            }
        }

        AbyssBoss boss2 = manager.getBoss2(uid);
        if (boss2 != null && boss2.isActive()) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);
            boss2.onPlayerDeath();
            player.sendMessage(SmallCaps.convert("§c§l✦ §cHas caído ante el Titán. ¡Vuelves a la arena!"));
            if (boss2.getArenaCenter() != null) {
                manager.markDirectRespawn(uid, boss2.getArenaCenter());
            }
        }

        VoidBoss boss3 = manager.getBoss3(uid);
        if (boss3 != null && boss3.isActive()) {
            // No resetear misión: respawnear dentro de la arena
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);
            player.sendMessage(SmallCaps.convert("§c§l✦ §cHas caído ante el Arquitecto. ¡Vuelves a la arena!"));
            // Marcar para respawn en arena
            manager.markArenaRespawn(uid, boss3.getArena());
        }

        VoidPostBoss postBoss3 = manager.getPostBoss3(uid);
        if (postBoss3 != null && postBoss3.isActive()) {
            // No resetear misión: respawnear dentro de la arena
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);
            player.sendMessage(SmallCaps.convert("§c§l✦ §cHas caído durante las pruebas. ¡Vuelves a la arena!"));
            manager.markArenaRespawn(uid, postBoss3.getArena());
        }

        ElegyBoss boss4 = manager.getBoss4(uid);
        if (boss4 != null && boss4.isActive()) {
            // Keep inventory on death during Quest 4 boss
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);
            boss4.onPlayerDeath();
        }

        // Misión 5: mantener inventario y respawnear en último checkpoint
        SkyParkour parkour5 = manager.getParkour5(uid);
        if (parkour5 != null && parkour5.isActive()) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);
            player.sendMessage(SmallCaps.convert("§c§l✖ §cHas caído. §fVolviendo al último checkpoint..."));
            manager.markDirectRespawn(uid, parkour5.getRespawnLocation());
        }
        
        // Misión 6: mantener inventario y respawnear en zona del boss
        LyraBoss boss6 = manager.getBoss6(uid);
        if (boss6 != null && boss6.isActive()) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);
            player.sendMessage(SmallCaps.convert("§c§l✦ §4Has caído ante La Culpa. §bVolverás a la batalla..."));
            boss6.onPlayerDeath();
            manager.markDirectRespawn(uid, boss6.getBossLocation());
        }

        // Misión 2 — exploración: respawnear en el centro del área
        AbyssExploration exploration2 = manager.getExploration(uid);
        if (exploration2 != null && exploration2.isActive() && exploration2.getExplorationCenter() != null) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);
            player.sendMessage(SmallCaps.convert("§c§l✦ §cHas caído. §fVuelves al área de exploración..."));
            manager.markDirectRespawn(uid, exploration2.getExplorationCenter());
        }

        // Misión 7: respawnear en la arena del boss del laberinto
        LabyrinthManager lab7 = manager.getLabyrinth7(uid);
        if (lab7 != null && lab7.isActive() && lab7.isBossPhase() && lab7.getBoss() != null && lab7.getBoss().getCenter() != null) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);
            player.sendMessage(SmallCaps.convert("§c§l✦ §cHas caído ante el Olvido. §f¡Vuelves a la arena!"));
            manager.markDirectRespawn(uid, lab7.getBoss().getCenter());
        }

        // Misión 8: respawnear en la arena del Hielo Eterno
        FrostBoss boss8 = manager.getBoss8(uid);
        if (boss8 != null && boss8.isActive() && boss8.getArenaCenter() != null) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);
            player.sendMessage(SmallCaps.convert("§c§l✦ §cHas caído ante el Hielo Eterno. §f¡Vuelves a la arena!"));
            manager.markDirectRespawn(uid, boss8.getArenaCenter());
        }

        // Misión 9: respawnear en la arena del Origen
        OriginBoss boss9 = manager.getBoss9(uid);
        if (boss9 != null && boss9.isActive() && boss9.getArenaCenter() != null) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);
            player.sendMessage(SmallCaps.convert("§c§l✦ §cHas caído ante el Origen. §f¡Vuelves a la arena!"));
            manager.markDirectRespawn(uid, boss9.getArenaCenter());
        }

        // Misión 10: respawnear en la arena del Errante Corrompido
        FinalBoss boss10 = manager.getBoss10(uid);
        if (boss10 != null && boss10.isActive() && boss10.getArenaCenter() != null) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);
            player.sendMessage(SmallCaps.convert("§c§l✦ §cHas caído ante el Errante. §f¡Vuelves a la batalla!"));
            manager.markDirectRespawn(uid, boss10.getArenaCenter());
        }

    }

    // ==================== RESPAWN EN ARENA ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();
        org.bukkit.Location arenaLoc = manager.getArenaRespawnLocation(uid);
        if (arenaLoc != null) {
            event.setRespawnLocation(arenaLoc);
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline())
                    return;
                player.sendTitle( SmallCaps.convert("§c§l✦ RESUCITADO ✦"), SmallCaps.convert("§7¡Sigue luchando! §e5s de gracia..."), 5, 40, 10);
                player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_TOTEM_USE, 0.8f, 1.2f);
                // 5 segundos de invulnerabilidad para recuperarse
                player.setInvulnerable(true);
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.RESISTANCE, 100, 255, false, false));
                org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        player.setInvulnerable(false);
                        player.removePotionEffect(org.bukkit.potion.PotionEffectType.RESISTANCE);
                        player.sendTitle("", SmallCaps.convert("§c§l¡Gracia terminada! ¡Lucha!"), 0, 30, 10);
                    }
                }, 100L); // 5 segundos = 100 ticks
            }, 5L);
        }
    }

    // ==================== CONEXIÓN ====================

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();
        ShadowHunterManager.QuestState state = manager.getState(uid);

        // Solo teleportar si el jugador está literalmente dentro de bloques sólidos
        // (asfixia real) o en un mundo de laberinto que debería haber sido limpiado
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            Location loc = player.getLocation();
            boolean unsafePosition = false;

            // Caso 1: mundo de laberinto huérfano
            if (loc.getWorld() != null && loc.getWorld().getName().startsWith("labyrinth_")) {
                unsafePosition = true;
            }

            // Caso 2: jugador literalmente dentro de bloques (asfixia)
            // Tanto el bloque en la cabeza (Y+1) como en el cuerpo (Y) deben ser sólidos
            if (!unsafePosition) {
                Material bodyBlock = loc.getBlock().getType();
                Material headBlock = loc.clone().add(0, 1, 0).getBlock().getType();
                if (bodyBlock.isSolid() && headBlock.isSolid()) {
                    unsafePosition = true;
                }
            }

            if (unsafePosition) {
                Location safeLoc = Bukkit.getWorlds().get(0).getSpawnLocation();
                safeLoc.setY(safeLoc.getY() + 2);
                player.teleport(safeLoc);
                player.sendMessage(SmallCaps.convert("§e§l✦ Teletransportado a una ubicación segura por seguridad."));
            }
        }, 20L); // Wait 1 second for player to fully load

        // Desactivar fly si está en fases de combate (Misión 1 o 2)
        if (state == ShadowHunterManager.QuestState.COMBAT_PHASE ||
                state == ShadowHunterManager.QuestState.RITUAL_PHASE ||
                state == ShadowHunterManager.QuestState.BOSS_PHASE ||
                state == ShadowHunterManager.QuestState.QUEST2_EXPLORATION ||
                state == ShadowHunterManager.QuestState.QUEST2_COMBAT ||
                state == ShadowHunterManager.QuestState.QUEST2_RITUAL ||
                state == ShadowHunterManager.QuestState.QUEST2_BOSS ||
                state == ShadowHunterManager.QuestState.QUEST3_TRIALS ||
                state == ShadowHunterManager.QuestState.QUEST3_BOSS ||
                state == ShadowHunterManager.QuestState.QUEST3_POSTBOSS ||
                state == ShadowHunterManager.QuestState.QUEST4_BOSS ||
                state == ShadowHunterManager.QuestState.QUEST5_PARKOUR ||
                state == ShadowHunterManager.QuestState.QUEST6_MEMORY_WALK ||
                state == ShadowHunterManager.QuestState.QUEST6_BOSS ||
                state == ShadowHunterManager.QuestState.QUEST7_LABYRINTH ||
                state == ShadowHunterManager.QuestState.QUEST7_BOSS ||
                state == ShadowHunterManager.QuestState.QUEST8_BOSS) {

            if (player.getAllowFlight()) {
                player.setAllowFlight(false);
                player.setFlying(false);
                player.sendMessage(SmallCaps.convert("§c§l⚔ §fNo puedes volar durante el combate con El Errante."));
            }
        }
    }

    // ==================== DESCONEXIÓN ====================

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uid = event.getPlayer().getUniqueId();
        ShadowHunterManager.QuestState state = manager.getState(uid);

        if (state != ShadowHunterManager.QuestState.NONE &&
                state != ShadowHunterManager.QuestState.COMPLETED &&
                state != ShadowHunterManager.QuestState.QUEST2_COMPLETED &&
                state != ShadowHunterManager.QuestState.QUEST3_COMPLETED &&
                state != ShadowHunterManager.QuestState.QUEST4_COMPLETED &&
                state != ShadowHunterManager.QuestState.QUEST5_COMPLETED &&
                state != ShadowHunterManager.QuestState.QUEST6_COMPLETED &&
                state != ShadowHunterManager.QuestState.QUEST7_COMPLETED &&
                state != ShadowHunterManager.QuestState.QUEST8_COMPLETED &&
                state != ShadowHunterManager.QuestState.QUEST9_COMPLETED &&
                state != ShadowHunterManager.QuestState.QUEST10_COMPLETED) {
            // Si estaba en fase de boss misión 1 y el boss ya murió
            if (state == ShadowHunterManager.QuestState.BOSS_PHASE) {
                ShadowHunterBoss boss = manager.getBoss(uid);
                if (boss != null && !boss.isActive()) {
                    manager.markCompleted(uid);
                    VoidBlade.giveVoidBlade(event.getPlayer(), plugin);
                }
            }
            // Si estaba en fase de boss misión 2 y el boss ya murió
            if (state == ShadowHunterManager.QuestState.QUEST2_BOSS) {
                AbyssBoss boss2 = manager.getBoss2(uid);
                if (boss2 != null && !boss2.isActive()) {
                    AbyssBlade.giveAbyssBlade(event.getPlayer(), plugin);
                }
            }
            // Si estaba en fase de boss misión 3, limpiar boss y arena
            if (state == ShadowHunterManager.QuestState.QUEST3_BOSS) {
                VoidBoss boss3 = manager.getBoss3(uid);
                if (boss3 != null) {
                    if (!boss3.isActive()) {
                        VoidPickaxe.giveVoidPickaxe(event.getPlayer(), plugin);
                    }
                    // Limpiar boss y destruir arena (expansión de dominio)
                    boss3.cleanup();
                }
            }
            // Si estaba en post-boss misión 3, limpiar
            if (state == ShadowHunterManager.QuestState.QUEST3_POSTBOSS) {
                VoidPostBoss postBoss3 = manager.getPostBoss3(uid);
                if (postBoss3 != null) {
                    postBoss3.cleanup();
                }
            }
            // Si estaba en fase de boss misión 4 y el boss ya murió
            if (state == ShadowHunterManager.QuestState.QUEST4_BOSS) {
                ElegyBoss boss4 = manager.getBoss4(uid);
                if (boss4 != null && !boss4.isActive()) {
                    VoidChestplate.giveVoidChestplate(event.getPlayer(), plugin);
                }
            }
            // Misión 5: limpiar parkour si se desconecta
            if (state == ShadowHunterManager.QuestState.QUEST5_PARKOUR) {
                SkyParkour parkour5 = manager.getParkour5(uid);
                if (parkour5 != null) {
                    parkour5.cleanup();
                }
            }
            // Misión 6: si el boss de Lyra ya murió, dar recompensa
            if (state == ShadowHunterManager.QuestState.QUEST6_BOSS) {
                LyraBoss boss6 = manager.getBoss6(uid);
                if (boss6 != null && !boss6.isActive()) {
                    VoidLeggings.giveVoidLeggings(event.getPlayer(), plugin);
                }
            }
            // Misión 7: limpiar laberinto si se desconecta + reward check
            if (state == ShadowHunterManager.QuestState.QUEST7_LABYRINTH || 
                state == ShadowHunterManager.QuestState.QUEST7_BOSS) {
                LabyrinthManager labyrinth = manager.getLabyrinth7(uid);
                if (labyrinth != null) {
                    labyrinth.cleanup();
                }
            }
            // Misión 8: si el boss de hielo ya murió, dar recompensa
            if (state == ShadowHunterManager.QuestState.QUEST8_BOSS) {
                FrostBoss boss8 = manager.getBoss8(uid);
                if (boss8 != null && !boss8.isActive()) {
                    VoidMace.giveVoidMace(event.getPlayer(), plugin);
                }
            }
            // Limpiar misión activa si se desconecta
            manager.cleanupPlayer(uid);
        }
    }

    // ==================== COMMAND BLOCKING DURING QUEST COMBAT ====================

    private static final Set<String> QUEST_ALLOWED_COMMANDS = new HashSet<>(Arrays.asList(
            "msg", "r", "reply", "tell", "whisper", "w", "errante"
    ));

    private boolean isInActiveQuestPhase(ShadowHunterManager.QuestState state) {
        return state == ShadowHunterManager.QuestState.COMBAT_PHASE
                || state == ShadowHunterManager.QuestState.RITUAL_PHASE
                || state == ShadowHunterManager.QuestState.BOSS_PHASE
                || state == ShadowHunterManager.QuestState.QUEST2_EXPLORATION
                || state == ShadowHunterManager.QuestState.QUEST2_COMBAT
                || state == ShadowHunterManager.QuestState.QUEST2_RITUAL
                || state == ShadowHunterManager.QuestState.QUEST2_BOSS
                || state == ShadowHunterManager.QuestState.QUEST3_TRIALS
                || state == ShadowHunterManager.QuestState.QUEST3_ARENA
                || state == ShadowHunterManager.QuestState.QUEST3_BOSS
                || state == ShadowHunterManager.QuestState.QUEST3_POSTBOSS
                || state == ShadowHunterManager.QuestState.QUEST4_BOSS
                || state == ShadowHunterManager.QuestState.QUEST5_PARKOUR
                || state == ShadowHunterManager.QuestState.QUEST6_MEMORY_WALK
                || state == ShadowHunterManager.QuestState.QUEST6_BOSS
                || state == ShadowHunterManager.QuestState.QUEST7_LABYRINTH
                || state == ShadowHunterManager.QuestState.QUEST7_BOSS
                || state == ShadowHunterManager.QuestState.QUEST8_BOSS
                || state == ShadowHunterManager.QuestState.QUEST9_BOSS
                || state == ShadowHunterManager.QuestState.QUEST10_BOSS;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onQuestCommandBlock(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("wardstone.admin")) return;

        ShadowHunterManager.QuestState state = manager.getState(player.getUniqueId());
        if (state == null || !isInActiveQuestPhase(state)) return;

        String base = event.getMessage().toLowerCase().split(" ")[0].replace("/", "");
        if (!QUEST_ALLOWED_COMMANDS.contains(base)) {
            event.setCancelled(true);
            player.sendMessage(SmallCaps.convert("§c§l⚔ §fNo puedes usar comandos durante una misión activa del Errante."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onQuestTeleportBlock(org.bukkit.event.player.PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("wardstone.admin")) return;

        ShadowHunterManager.QuestState state = manager.getState(player.getUniqueId());
        if (state == null || !isInActiveQuestPhase(state)) return;

        org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause == org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.ENDER_PEARL) return;
        if (player.hasMetadata("quest_teleport")) return;

        event.setCancelled(true);
        player.sendMessage(SmallCaps.convert("§c§l⚔ §fNo puedes teletransportarte durante una misión activa del Errante."));
    }

    // ==================== WORLD CHANGE BLOCKING ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldChange(org.bukkit.event.player.PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("wardstone.admin")) return;

        ShadowHunterManager.QuestState state = manager.getState(player.getUniqueId());
        if (state == null || !isInActiveQuestPhase(state)) return;

        // El jugador cambio de mundo durante una mision activa → devolverlo al mundo de origen
        org.bukkit.World fromWorld = event.getFrom();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.teleport(fromWorld.getSpawnLocation());
            player.sendMessage(SmallCaps.convert("§c§l⚔ §fNo puedes cambiar de mundo durante una misión del Errante."));
        }, 1L);
    }

    // ==================== NETHER/END BLOCKING ====================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerPortal(org.bukkit.event.player.PlayerPortalEvent event) {
        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();
        ShadowHunterManager.QuestState state = manager.getState(uid);

        // Bloquear portales si el jugador tiene una misión activa (no NONE ni COMPLETED)
        if (state != null && state != ShadowHunterManager.QuestState.NONE
                && state != ShadowHunterManager.QuestState.COMPLETED
                && state != ShadowHunterManager.QuestState.QUEST2_COMPLETED
                && state != ShadowHunterManager.QuestState.QUEST3_COMPLETED
                && state != ShadowHunterManager.QuestState.QUEST4_COMPLETED
                && state != ShadowHunterManager.QuestState.QUEST5_COMPLETED
                && state != ShadowHunterManager.QuestState.QUEST6_COMPLETED
                && state != ShadowHunterManager.QuestState.QUEST7_COMPLETED
                && state != ShadowHunterManager.QuestState.QUEST8_COMPLETED
                && state != ShadowHunterManager.QuestState.QUEST9_COMPLETED
                && state != ShadowHunterManager.QuestState.QUEST10_COMPLETED) {
            event.setCancelled(true);
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes usar portales durante una misión del Errante."));
            return;
        }

        // Bloquear entrada al Nether/End si tiene un boss activo
        if (manager.getBoss(uid) != null || manager.getBoss2(uid) != null
                || manager.getBoss3(uid) != null || manager.getBoss4(uid) != null
                || manager.getBoss6(uid) != null || manager.getBoss8(uid) != null
                || manager.getBoss9(uid) != null
                || manager.getBoss10(uid) != null) {
            event.setCancelled(true);
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes usar portales durante una batalla de boss."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPortal(org.bukkit.event.entity.EntityPortalEvent event) {
        Entity entity = event.getEntity();
        // Bloquear que cualquier mob de misión use un portal
        if (entity.getCustomName() != null) {
            String name = org.bukkit.ChatColor.stripColor(entity.getCustomName());
            if (name.contains("Última Memoria") || name.contains("Fragmento Olvidado")
                    || name.contains("Arquitecto") || name.contains("El Errante")
                    || name.contains("Abismo") || name.contains("Cazador")
                    || name.contains("Heraldo") || name.contains("Hielo Eterno")
                    || name.contains("Esbirro Glaciar")
                    || name.contains("Centinela") || name.contains("Origen")) {
                event.setCancelled(true);
                return;
            }
        }
        // También bloquear por UUID si es un boss conocido
        for (UUID uid : manager.getAllActiveBossPlayerIds()) {
            ElegyBoss b4 = manager.getBoss4(uid);
            if (b4 != null && (b4.isBossEntity(entity) || b4.isSummonedMob(entity.getUniqueId()))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ==================== BLOCK TELEPORT TO NETHER/END DURING BOSS ====================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTeleport(org.bukkit.event.player.PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("wardstone.admin")) return;

        UUID uid = player.getUniqueId();

        // Solo bloquear si el jugador tiene un boss activo del Errante
        boolean hasBoss = manager.getBoss(uid) != null || manager.getBoss2(uid) != null
                || manager.getBoss3(uid) != null || manager.getBoss4(uid) != null
                || manager.getBoss6(uid) != null || manager.getBoss8(uid) != null;
        if (!hasBoss) return;

        // Bloquear si el destino es Nether o End
        org.bukkit.World toWorld = event.getTo() != null ? event.getTo().getWorld() : null;
        if (toWorld != null) {
            org.bukkit.World.Environment env = toWorld.getEnvironment();
            if (env == org.bukkit.World.Environment.NETHER || env == org.bukkit.World.Environment.THE_END) {
                event.setCancelled(true);
                player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes ir al Nether ni al End durante una batalla de boss."));
                return;
            }
        }

        // Bloquear ender pearls que cambien de mundo
        if (event.getCause() == org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || event.getCause() == org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                || event.getCause() == org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.END_PORTAL
                || event.getCause() == org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.END_GATEWAY) {
            org.bukkit.World fromWorld = event.getFrom().getWorld();
            if (toWorld != null && fromWorld != null && !toWorld.getName().equals(fromWorld.getName())) {
                event.setCancelled(true);
                player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes cambiar de mundo durante una batalla de boss."));
            }
        }
    }

    // ==================== BLOCK BREAK: ARENA + TRIALS ====================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();

        // Misión 3: Trials (Prueba 4 - Memoria de colores) - interactuar con lana
        VoidTrials trials = manager.getTrials3(uid);
        if (trials != null && trials.isActive() && trials.getCurrentTrial() == 4 && trials.isMemoryInputPhase()) {
            org.bukkit.block.Block block = event.getBlock();
            if (block.getType().name().contains("WOOL")) {
                event.setCancelled(true);
                trials.onBlockHit(block.getType(), block.getLocation());
                return; // Sale sin mandar el mensaje de protección
            }
        }

        // Proteger globalmente cualquier bloque dentro del Dominio (VoidArena)
        // Los OPs pueden romper bloques libremente
        if (manager.isInsideAnyVoidArena(event.getBlock().getLocation()) && !player.isOp()) {
            event.setCancelled(true);
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes romper bloques en la Expansión de Dominio."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(org.bukkit.event.block.BlockPlaceEvent event) {
        // Los OPs pueden construir libremente
        if (manager.isInsideAnyVoidArena(event.getBlock().getLocation()) && !event.getPlayer().isOp()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes construir en la Expansión de Dominio."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaEntityExplode(org.bukkit.event.entity.EntityExplodeEvent event) {
        event.blockList().removeIf(block -> manager.isInsideAnyVoidArena(block.getLocation()));
    }

    // ==================== PREVENT BOSS HEALING ====================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossRegainHealth(org.bukkit.event.entity.EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return;
        UUID entityId = event.getEntity().getUniqueId();
        
        // Cancelar curación de TODOS los bosses de misiones
        for (ShadowHunterBoss b : manager.getAllActiveBoss1()) {
            if (b != null && b.isActive() && b.isBossEntity(entityId)) {
                event.setCancelled(true);
                return;
            }
        }
        for (AbyssBoss b : manager.getAllActiveBoss2()) {
            if (b != null && b.isActive() && b.isBossEntity(entityId)) {
                event.setCancelled(true);
                return;
            }
        }
        for (VoidBoss b : manager.getAllActiveBoss3()) {
            if (b != null && b.isActive() && (b.isBossEntity(entityId) || b.isSpawnedEntity(entityId))) {
                event.setCancelled(true);
                return;
            }
        }
        for (ElegyBoss b : manager.getAllActiveBoss4()) {
            if (b != null && b.isActive() && (b.isBossEntity(entityId) || b.isSummonedMob(entityId))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ==================== ELEGY BOSS: INTERCEPT ALL FATAL DAMAGE ====================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onElegyBossAnyDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) return; // Handled by onElegyBossDamage
        if (!(event.getEntity() instanceof LivingEntity)) return;
        
        for (ElegyBoss boss4 : manager.getAllActiveBoss4()) {
            if (boss4 == null || !boss4.isActive() || !boss4.isBossEntity(event.getEntity())) continue;
            
            LivingEntity bossEntity = (LivingEntity) event.getEntity();
            
            // Block all damage during/after final scene
            if (boss4.isFinalSceneActive() || boss4.isFinalSceneTriggered()) {
                event.setCancelled(true);
                bossEntity.setHealth(1);
                return;
            }
            
            // Intercept fatal damage from any source (fire, fall, suffocation, etc.)
            if (bossEntity.getHealth() - event.getFinalDamage() <= 1.0) {
                event.setCancelled(true);
                bossEntity.setHealth(1);
                boss4.onBossFatalDamage();
                return;
            }
        }
    }

    // ==================== MISSION 10 BOSS: PREVENT ENVIRONMENTAL DEATH ====================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFinalBossEnvironmentalDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;

        UUID entityId = event.getEntity().getUniqueId();
        for (UUID pid : manager.getAllActiveBossPlayerIds()) {
            FinalBoss boss10 = manager.getBoss10(pid);
            if (boss10 != null && boss10.isBossEntity(entityId)) {
                // Cancelar todo daño ambiental (sol, fuego, caída, ahogamiento, etc.)
                event.setCancelled(true);
                LivingEntity le = (LivingEntity) event.getEntity();
                if (le.getFireTicks() > 0) {
                    le.setFireTicks(0);
                }
                return;
            }
        }
    }

    // ==================== BOSS SUFFOCATION FIX ====================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossSuffocate(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.SUFFOCATION) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;
        
        UUID entityId = event.getEntity().getUniqueId();
        if (manager.isMissionMob(entityId)) {
            event.setCancelled(true);
            // Teleport to safe location (up)
            LivingEntity entity = (LivingEntity) event.getEntity();
            Location safeLoc = entity.getLocation().clone().add(0, 2, 0);
            // Find first non-solid block above
            for (int i = 0; i < 10; i++) {
                if (!safeLoc.getBlock().getType().isSolid()) {
                    entity.teleport(safeLoc);
                    return;
                }
                safeLoc.add(0, 1, 0);
            }
        }
    }

    // ==================== MISSION 5: SKELETON DEATH (clear drops) ====================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMission5BossDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        for (SkyParkour parkour : manager.getAllActiveParkour5()) {
            if (parkour != null && parkour.isActive() && parkour.isBossEntity(entity)) {
                event.getDrops().clear();
                event.setDroppedExp(0);
                return;
            }
        }
    }

    // ==================== BLOQUEAR MOBS DEL ERRANTE EN MINIJUEGOS ====================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMissionMobTeleport(org.bukkit.event.entity.EntityTeleportEvent event) {
        org.bukkit.entity.Entity entity = event.getEntity();
        
        // Verificar si es un mob de misión
        if (!manager.isMissionMob(entity.getUniqueId())) {
            return;
        }
        
        // Bloquear teletransporte al mundo de minijuegos
        if (event.getTo() != null && event.getTo().getWorld() != null 
                && event.getTo().getWorld().getName().equals("minigames")) {
            event.setCancelled(true);
            plugin.getLogger().info("[ShadowHunter] Bloqueado teletransporte de mob de misión al mundo de minijuegos");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMissionMobMove(org.bukkit.event.entity.EntityPortalEvent event) {
        org.bukkit.entity.Entity entity = event.getEntity();
        
        // Verificar si es un mob de misión
        if (!manager.isMissionMob(entity.getUniqueId())) {
            return;
        }
        
        // Bloquear entrada a minijuegos por portal
        if (event.getTo() != null && event.getTo().getWorld() != null 
                && event.getTo().getWorld().getName().equals("minigames")) {
            event.setCancelled(true);
            plugin.getLogger().info("[ShadowHunter] Bloqueado portal de mob de misión al mundo de minijuegos");
        }
    }

    // ==================== CLICK IZQUIERDO: ALMAS TRIBUNAL ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        org.bukkit.event.block.Action action = event.getAction();
        if (action != org.bukkit.event.block.Action.LEFT_CLICK_AIR
                && action != org.bukkit.event.block.Action.LEFT_CLICK_BLOCK)
            return;

        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();

        VoidPostBoss postBoss3 = manager.getPostBoss3(uid);
        if (postBoss3 == null || !postBoss3.isActive() || postBoss3.getCurrentPhase() != 6)
            return;

        // Buscar el ArmorStand (alma) más cercano a la línea de visión del jugador
        org.bukkit.Location eye = player.getEyeLocation();
        org.bukkit.util.Vector dir = eye.getDirection().normalize();
        Collection<org.bukkit.entity.Entity> nearby = player.getWorld().getNearbyEntities(eye, 8, 8, 8);
        ArmorStand closest = null;
        double closestDist = Double.MAX_VALUE;

        for (org.bukkit.entity.Entity e : nearby) {
            if (!(e instanceof ArmorStand))
                continue;
            // Verificar que es un alma del tribunal (tiene la clave NBT)
            ArmorStand as = (ArmorStand) e;
            byte c = as.getPersistentDataContainer().getOrDefault(
                    new NamespacedKey(plugin, "soul_corrupt"),
                    org.bukkit.persistence.PersistentDataType.BYTE, (byte) -1);
            if (c == -1)
                continue;

            // Calcular distancia perpendicular a la línea de visión
            org.bukkit.util.Vector toEntity = e.getLocation().add(0, 1, 0).toVector().subtract(eye.toVector());
            double along = toEntity.dot(dir);
            if (along < 0 || along > 8)
                continue; // detrás o demasiado lejos
            double perpDist = toEntity.clone().subtract(dir.clone().multiply(along)).length();

            if (perpDist < 1.5 && along < closestDist) {
                closestDist = along;
                closest = as;
            }
        }

        if (closest != null) {
            event.setCancelled(true);
            postBoss3.onSoulHit(closest);
        }
    }
}
