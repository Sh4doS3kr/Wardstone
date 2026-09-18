package com.moonlight.coreprotect.shadowhunter;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Boss de la Misión 10: "El Fin"
 *
 * El Errante, corrompido por la oscuridad que combatió durante milenios,
 * pide al jugador que lo mate antes de que la corrupción lo consuma.
 *
 * Duración EXACTA: 5 minutos 20 segundos (320 segundos).
 * El boss NO PUEDE morir antes ni después de 320s.
 *
 * 4 fases temporales:
 *   Fase 1 (0-80s):   La Corrupción Despierta — HP no baja de 75%
 *   Fase 2 (80-170s):  Los Recuerdos           — HP no baja de 50%
 *   Fase 3 (170-260s): La Última Voluntad       — HP no baja de 25%
 *   Fase 4 (260-320s): El Adiós                 — HP no baja de 1
 *   A los 320s exactos: muerte programática.
 *
 * Recompensa: Casco del Errante (netherite helmet OP).
 */
public class FinalBoss {

    private final CoreProtectPlugin plugin;
    private final ShadowHunterManager manager;
    private final Player player;
    private final ShadowHunterNPC npc;

    private boolean active = false;
    private Zombie bossEntity;
    private BossBar bossBar;
    private BukkitRunnable mainLoop;
    private int tickCount = 0; // cada iteración = 10 game ticks (0.5s)
    private int phase = 1;
    private Location arenaCenter;

    private final List<Entity> summonedMobs = new ArrayList<>();
    private final Set<UUID> spawnedEntityIds = new HashSet<>();

    // Constantes de tiempo (en iteraciones de mainLoop, cada 10 ticks = 0.5s)
    private static final int PHASE_2_ITER = 160;   // 80s
    private static final int PHASE_3_ITER = 340;   // 170s
    private static final int PHASE_4_ITER = 520;   // 260s
    private static final int KILL_ITER = 640;       // 320s = 5:20

    private static final double MAX_HP = 2048;
    private static final double PHASE_1_MIN = MAX_HP * 0.75;
    private static final double PHASE_2_MIN = MAX_HP * 0.50;
    private static final double PHASE_3_MIN = MAX_HP * 0.25;
    private static final double PHASE_4_MIN = 1.0;

    // Ability cooldowns
    private int shadowPulseCD = 0;
    private int corruptionWaveCD = 0;
    private int memoryEchoCD = 0;
    private int voidRiftCD = 0;
    private int despairNovaCD = 0;

    // Diálogo indices
    private int dialogueIdx = 0;

    private final String[][] phaseDialogues = {
            // Fase 1: La Corrupción Despierta
            {
                    "§5§o\"Perdóname... ya no puedo controlarla.\"",
                    "§5§o\"¿Sabes lo peor? Puedo sentir cómo me borra... por dentro.\"",
                    "§5§o\"Tuve un nombre una vez... ya casi no lo recuerdo.\"",
                    "§5§o\"Cada misión que te di... era un trozo de mí que quería que sobreviviera.\""
            },
            // Fase 2: Los Recuerdos
            {
                    "§d§o\"¿Recuerdas cuando nos conocimos? Yo estaba tan cansado...\"",
                    "§d§o\"Lyra solía decirme que sonriera más. Ya ni recuerdo cómo.\"",
                    "§d§o\"Tenía una casa, ¿sabes? Con un jardín. Lyra plantaba flores.\"",
                    "§d§o\"A veces sueño con aquel jardín... y despierto aquí, solo.\""
            },
            // Fase 3: La Última Voluntad
            {
                    "§4§l\"¡¡ESTÁ BORRÁNDOME!! ¡¡YA NO SÉ QUIÉN SOY!!\"",
                    "§4§l\"¡Si no me detienes... destruiré todo lo que protegí!\"",
                    "§4§l\"¡¡Por favor!! ¡¡Antes de que olvide por qué te elegí!!\"",
                    "§4§l\"¡¡Lyra... perdóname... PERDÓNAME!!\""
            },
            // Fase 4: El Adiós
            {
                    "§7§o\"Ah... puedo ver de nuevo. La corrupción... se va.\"",
                    "§7§o\"Escucho su voz... Lyra me está llamando.\"",
                    "§7§o\"Cuida este mundo... yo ya no puedo.\"",
                    "§7§o\"¿Me recordarás? ...¿Recordarás mi nombre?\""
            }
    };

    public FinalBoss(CoreProtectPlugin plugin, ShadowHunterManager manager, Player player, ShadowHunterNPC npc) {
        this.plugin = plugin;
        this.manager = manager;
        this.player = player;
        this.npc = npc;
    }

    // ==================== INICIO ====================

    public void start() {
        active = true;

        // Reproducir audio custom de Nexo (5:20 de duración)
        player.playSound(player.getLocation(), "nexo:music.errante_fin",
                SoundCategory.MASTER, 1.0f, 1.0f);

        // Subtítulos con frases famosas (sin títulos, solo subtítulos)
        player.sendTitle("", SmallCaps.convert("§7§o\"Lo que una vez amamos, nunca lo perdemos del todo.\""), 10, 80, 20);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendTitle("", SmallCaps.convert("§7§o\"Los muertos solo mueren cuando los olvidamos.\""), 10, 80, 20);
        }, 100L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendTitle("", SmallCaps.convert("§7§o\"Ojalá hubiera tenido tiempo de decirte mi nombre.\""), 10, 80, 20);
        }, 200L);

        // Historia breve en el chat
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            sendIntroStory();
        }, 60L);

        // Desactivar fly
        if (player.getAllowFlight()) {
            player.setAllowFlight(false);
            player.setFlying(false);
            player.sendMessage(SmallCaps.convert("§c§l⚔ §fNo puedes volar durante el último combate."));
        }

        // Spawn del boss 15 segundos después (para que la intro se disfrute)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            spawnBoss();
            startMainLoop();
        }, 300L); // 15s
    }

    private void sendIntroStory() {
        String[] lines = {
            "",
            "§5§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
            "          §5§l✦ MISIÓN FINAL: EL FIN ✦",
            "",
            "  §7Nunca te dijo su nombre.",
            "  §7En diez misiones juntos, nunca lo hizo.",
            "",
            "  §7Solo sabes que un día, hace milenios,",
            "  §7un hombre dejó atrás su hogar, su jardín,",
            "  §7y a la mujer que amaba... para salvar un mundo",
            "  §7que nunca le dio las gracias.",
            "",
            "  §7Caminó solo durante siglos.",
            "  §7Hasta que te encontró a ti.",
            "",
            "  §5§oY ahora la corrupción que combatió toda su vida",
            "  §5§ofinalmente lo ha alcanzado por dentro.",
            "",
            "  §cEstá perdiendo sus recuerdos. Su voz. Su nombre.",
            "  §cY te pide una última cosa:",
            "",
            "  §4§l  Que termines con su sufrimiento.",
            "",
            "  §c§l⚠ Duración: 5 minutos 20 segundos.",
            "  §e⚠ Si mueres, respawneas aquí.",
            "§5§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
            ""
        };

        for (int i = 0; i < lines.length; i++) {
            final String line = lines[i];
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!active || !player.isOnline()) return;
                player.sendMessage(line.isEmpty() ? "" : SmallCaps.convert(line));
            }, i * 25L); // ~1.25s entre cada línea
        }
    }

    // ==================== SPAWN BOSS ====================

    private void spawnBoss() {
        Location spawnLoc = player.getLocation().clone().add(
                player.getLocation().getDirection().normalize().multiply(5));
        spawnLoc.setY(player.getLocation().getY());
        arenaCenter = spawnLoc.clone();

        World world = spawnLoc.getWorld();
        // Efecto de corrupción
        world.spawnParticle(Particle.SMOKE, spawnLoc.clone().add(0, 1, 0), 100, 3, 3, 3, 0.1);
        world.spawnParticle(Particle.PORTAL, spawnLoc.clone().add(0, 2, 0), 200, 2, 2, 2, 1.0);
        world.spawnParticle(Particle.DUST, spawnLoc.clone().add(0, 1, 0), 80, 2, 2, 2, 0,
                new Particle.DustOptions(Color.fromRGB(100, 0, 150), 3.0f));
        world.playSound(spawnLoc, Sound.ENTITY_WITHER_SPAWN, 0.8f, 0.3f);
        world.playSound(spawnLoc, Sound.ENTITY_WARDEN_EMERGE, 1.0f, 0.5f);

        bossBar = Bukkit.createBossBar(SmallCaps.convert("§5§l✦ El Errante — Corrompido ✦ §7[FASE 1: La Corrupción]"),
                BarColor.PURPLE, BarStyle.SEGMENTED_10);
        bossBar.addPlayer(player);

        bossEntity = player.getWorld().spawn(spawnLoc, Zombie.class, z -> {
            z.setCustomName(SmallCaps.convert("§5§l✦ El Errante §4§l[Corrompido] §5§l✦"));
            z.setCustomNameVisible(true);
            z.setAdult();
            z.setCanPickupItems(false);
            z.setRemoveWhenFarAway(false);
            z.setPersistent(true);
            z.addScoreboardTag("wardstone_mission_mob");
            z.addScoreboardTag("final_boss");

            z.getAttribute(Attribute.MAX_HEALTH).setBaseValue(MAX_HP);
            z.setHealth(MAX_HP);
            z.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(14);
            z.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.28);
            z.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(0.95);
            z.getAttribute(Attribute.ARMOR).setBaseValue(16);

            z.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 99999, 0, false, false));
            z.setVisualFire(false);

            equipBoss(z, 1);
        });

        spawnedEntityIds.add(bossEntity.getUniqueId());
        manager.markDirectRespawn(player.getUniqueId(), arenaCenter.clone());

        player.sendTitle("", SmallCaps.convert("§5FASE 1 — La Corrupción Despierta"), 10, 60, 20);
    }

    private void equipBoss(Zombie z, int bossPhase) {
        Color primary, secondary;
        if (bossPhase <= 1) {
            primary = Color.fromRGB(60, 0, 80);
            secondary = Color.fromRGB(40, 0, 60);
        } else if (bossPhase == 2) {
            primary = Color.fromRGB(120, 50, 180);
            secondary = Color.fromRGB(80, 20, 120);
        } else if (bossPhase == 3) {
            primary = Color.fromRGB(150, 0, 0);
            secondary = Color.fromRGB(80, 0, 0);
        } else {
            primary = Color.fromRGB(200, 200, 220);
            secondary = Color.fromRGB(150, 150, 170);
        }

        z.getEquipment().setHelmet(colorLeather(Material.LEATHER_HELMET, primary));
        z.getEquipment().setChestplate(colorLeather(Material.LEATHER_CHESTPLATE, secondary));
        z.getEquipment().setLeggings(colorLeather(Material.LEATHER_LEGGINGS, primary));
        z.getEquipment().setBoots(colorLeather(Material.LEATHER_BOOTS, secondary));
        z.getEquipment().setItemInMainHand(new ItemStack(
                bossPhase == 3 ? Material.NETHERITE_SWORD : Material.NETHERITE_AXE));
    }

    private ItemStack colorLeather(Material mat, Color color) {
        ItemStack item = new ItemStack(mat);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setColor(color);
        item.setItemMeta(meta);
        return item;
    }

    // ==================== MAIN LOOP ====================

    private void startMainLoop() {
        mainLoop = new BukkitRunnable() {
            @Override
            public void run() {
                if (!active || bossEntity == null || !player.isOnline()) {
                    cancel();
                    return;
                }

                // Si el boss murió por algo externo, respawnearlo (no puede morir antes de tiempo)
                if (bossEntity.isDead()) {
                    respawnBoss();
                    return;
                }

                // Prevenir quemaduras del sol: apagar fuego y mantener HP mínima
                if (bossEntity.getFireTicks() > 0) {
                    bossEntity.setFireTicks(0);
                    bossEntity.setVisualFire(false);
                }

                tickCount++;

                // ===== KILL EXACTO A 320s =====
                if (tickCount >= KILL_ITER) {
                    cancel();
                    onTimedDeath();
                    return;
                }

                // ===== HP CLAMPING (no puede morir antes de tiempo) =====
                clampHealth();

                // ===== BOSSBAR =====
                double hp = bossEntity.getHealth();
                double maxHp = bossEntity.getAttribute(Attribute.MAX_HEALTH).getBaseValue();
                bossBar.setProgress(Math.max(0, Math.min(1, hp / maxHp)));

                // ===== PHASE TRANSITIONS =====
                if (phase == 1 && tickCount >= PHASE_2_ITER) {
                    transitionToPhase2();
                } else if (phase == 2 && tickCount >= PHASE_3_ITER) {
                    transitionToPhase3();
                } else if (phase == 3 && tickCount >= PHASE_4_ITER) {
                    transitionToPhase4();
                }

                // ===== DIALOGUE (cada ~20 segundos) =====
                if (tickCount % 40 == 0) {
                    sendPhaseDialogue();
                }

                // ===== ABILITIES =====
                tickAbilities();

                // ===== AMBIENT PARTICLES =====
                if (tickCount % 4 == 0) {
                    ambientParticles();
                }

                // ===== COOLDOWN TICKS =====
                if (shadowPulseCD > 0) shadowPulseCD--;
                if (corruptionWaveCD > 0) corruptionWaveCD--;
                if (memoryEchoCD > 0) memoryEchoCD--;
                if (voidRiftCD > 0) voidRiftCD--;
                if (despairNovaCD > 0) despairNovaCD--;

                // Teletransportar boss si está muy lejos del jugador
                if (bossEntity.getLocation().distanceSquared(player.getLocation()) > 2500) { // 50 bloques
                    bossEntity.teleport(player.getLocation().add(
                            player.getLocation().getDirection().normalize().multiply(3)));
                }
            }
        };
        mainLoop.runTaskTimer(plugin, 10L, 10L);
    }

    private void clampHealth() {
        if (bossEntity == null || bossEntity.isDead()) return;

        // Target HP decreases linearly over the full fight duration
        // At tick 0 = MAX_HP, at KILL_ITER = 1.0
        double progress = Math.min(1.0, (double) tickCount / KILL_ITER);
        double targetHp = MAX_HP * (1.0 - progress * 0.99); // Goes from MAX_HP to ~1% over 320s

        // Phase-based floor (hard minimum to prevent premature death)
        double minHp;
        if (phase == 1) minHp = PHASE_1_MIN;
        else if (phase == 2) minHp = PHASE_2_MIN;
        else if (phase == 3) minHp = PHASE_3_MIN;
        else minHp = PHASE_4_MIN;

        // Gradually drain HP so the bar visibly decreases
        double currentHp = bossEntity.getHealth();
        if (currentHp > targetHp) {
            // Smoothly drain toward target (remove ~5% of difference per tick)
            double newHp = currentHp - (currentHp - targetHp) * 0.08;
            bossEntity.setHealth(Math.max(minHp, newHp));
        }

        // Hard floor: can't die before time
        if (bossEntity.getHealth() < minHp) {
            bossEntity.setHealth(minHp);
        }
    }

    private void respawnBoss() {
        Location loc = arenaCenter != null ? arenaCenter : player.getLocation();
        bossEntity = player.getWorld().spawn(loc, Zombie.class, z -> {
            z.setCustomName(SmallCaps.convert("§5§l✦ El Errante §4§l[Corrompido] §5§l✦"));
            z.setCustomNameVisible(true);
            z.setAdult();
            z.setCanPickupItems(false);
            z.setRemoveWhenFarAway(false);
            z.setPersistent(true);
            z.addScoreboardTag("wardstone_mission_mob");
            z.addScoreboardTag("final_boss");
            z.getAttribute(Attribute.MAX_HEALTH).setBaseValue(MAX_HP);
            z.setHealth(Math.max(PHASE_4_MIN, MAX_HP * 0.5));
            z.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(14 + (phase * 2));
            z.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.28 + (phase * 0.02));
            z.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(0.95);
            z.getAttribute(Attribute.ARMOR).setBaseValue(16);
            z.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 99999, 0, false, false));
            equipBoss(z, phase);
        });
        spawnedEntityIds.add(bossEntity.getUniqueId());
        player.sendMessage(SmallCaps.convert("§4§l✦ §cLa corrupción lo regenera... no puedes matarlo aún."));
    }

    // ==================== PHASE TRANSITIONS ====================

    private void transitionToPhase2() {
        phase = 2;
        dialogueIdx = 0;
        World world = bossEntity.getWorld();
        Location loc = bossEntity.getLocation();

        // Droppear HP a 50% para la transición
        bossEntity.setHealth(PHASE_2_MIN);

        world.spawnParticle(Particle.SOUL, loc.clone().add(0, 2, 0), 100, 3, 3, 3, 0.1);
        world.spawnParticle(Particle.DUST, loc.clone().add(0, 1, 0), 80, 2, 2, 2, 0,
                new Particle.DustOptions(Color.fromRGB(180, 100, 255), 2.5f));
        world.playSound(loc, Sound.ENTITY_ALLAY_DEATH, 1.0f, 0.3f);
        world.playSound(loc, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 0.5f);

        bossBar.setTitle(SmallCaps.convert("§d§l✦ El Errante — Los Recuerdos ✦ §7[FASE 2]"));
        bossBar.setColor(BarColor.PINK);

        equipBoss(bossEntity, 2);
        bossEntity.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(18);
        bossEntity.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.32);

        player.sendTitle("", SmallCaps.convert("§d§oFASE 2 — Los Recuerdos"), 10, 60, 20);
        player.sendMessage(SmallCaps.convert("§d§l✦ §7Los ojos del Errante se llenan de lágrimas..."));
        player.sendMessage(SmallCaps.convert("§d§o\"Los recuerdos... son lo último que me queda.\""));
    }

    private void transitionToPhase3() {
        phase = 3;
        dialogueIdx = 0;
        World world = bossEntity.getWorld();
        Location loc = bossEntity.getLocation();

        bossEntity.setHealth(PHASE_3_MIN);

        world.spawnParticle(Particle.SMOKE, loc.clone().add(0, 2, 0), 200, 4, 4, 4, 0.2);
        world.spawnParticle(Particle.DUST, loc.clone().add(0, 1, 0), 100, 3, 3, 3, 0,
                new Particle.DustOptions(Color.fromRGB(200, 0, 0), 3.0f));
        world.playSound(loc, Sound.ENTITY_WARDEN_ROAR, 1.0f, 0.5f);
        world.playSound(loc, Sound.ENTITY_WITHER_AMBIENT, 0.8f, 0.3f);
        world.strikeLightningEffect(loc);

        bossBar.setTitle(SmallCaps.convert("§4§l✦ EL ERRANTE — LA ÚLTIMA VOLUNTAD ✦ §7[FASE 3]"));
        bossBar.setColor(BarColor.RED);

        equipBoss(bossEntity, 3);
        bossEntity.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(22);
        bossEntity.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.36);
        bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 99999, 1, false, false));
        bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 99999, 1, false, false));

        player.sendTitle("", SmallCaps.convert("§4§lFASE 3 — La Última Voluntad"), 10, 60, 20);
        player.sendMessage(SmallCaps.convert("§4§l✦ §c¡LA CORRUPCIÓN TOMA EL CONTROL TOTAL!"));
        player.sendMessage(SmallCaps.convert("§4§l\"¡¡MÁTAME AHORA!! ¡¡ANTES DE QUE SEA TARDE!!\""));
    }

    private void transitionToPhase4() {
        phase = 4;
        dialogueIdx = 0;
        World world = bossEntity.getWorld();
        Location loc = bossEntity.getLocation();

        bossEntity.setHealth(PHASE_4_MIN + 100);

        // La corrupción se disipa lentamente
        world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 2, 0), 60, 2, 3, 2, 0.1);
        world.spawnParticle(Particle.SOUL, loc.clone().add(0, 1, 0), 40, 1, 2, 1, 0.05);
        world.spawnParticle(Particle.DUST, loc.clone().add(0, 1, 0), 100, 2, 2, 2, 0,
                new Particle.DustOptions(Color.fromRGB(200, 200, 220), 2.5f));
        world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 0.3f);
        world.playSound(loc, Sound.ENTITY_ALLAY_DEATH, 1.0f, 0.5f);

        bossBar.setTitle(SmallCaps.convert("§7§l✦ El Errante — El Adiós ✦ §7[FASE FINAL]"));
        bossBar.setColor(BarColor.WHITE);

        equipBoss(bossEntity, 4);
        // Se debilita enormemente
        bossEntity.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(6);
        bossEntity.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.18);
        bossEntity.getAttribute(Attribute.ARMOR).setBaseValue(4);
        // Quitar buffs
        bossEntity.removePotionEffect(PotionEffectType.SPEED);
        bossEntity.removePotionEffect(PotionEffectType.STRENGTH);
        bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 99999, 1, false, false));
        bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 99999, 0, false, false));

        player.sendTitle("", SmallCaps.convert("§7§oFASE FINAL — El Adiós"), 10, 80, 20);
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§7§l✦ §7La corrupción se retira... por un momento."));
        player.sendMessage(SmallCaps.convert("§7§o*El Errante te mira con ojos claros por primera vez.*"));
        player.sendMessage(SmallCaps.convert("§7§o\"Ya casi... termina. Gracias por estar aquí.\""));
        player.sendMessage("");
    }

    // ==================== MUERTE PROGRAMÁTICA A 320s ====================

    private void onTimedDeath() {
        active = false;

        if (mainLoop != null) {
            try { mainLoop.cancel(); } catch (Exception ignored) {}
        }

        World world = player.getWorld();
        Location deathLoc = bossEntity != null && !bossEntity.isDead()
                ? bossEntity.getLocation() : player.getLocation();

        // Matar al boss
        if (bossEntity != null && !bossEntity.isDead()) {
            bossEntity.setHealth(0);
        }

        // Limpiar summoned mobs
        for (Entity e : summonedMobs) {
            if (e != null && !e.isDead()) e.remove();
        }
        summonedMobs.clear();

        // Efectos de muerte: las partículas del alma del Errante ascienden
        world.spawnParticle(Particle.SOUL, deathLoc.clone().add(0, 1, 0), 200, 1, 3, 1, 0.08);
        world.spawnParticle(Particle.END_ROD, deathLoc.clone().add(0, 2, 0), 150, 2, 4, 2, 0.2);
        world.spawnParticle(Particle.DUST, deathLoc.clone().add(0, 1, 0), 100, 2, 3, 2, 0,
                new Particle.DustOptions(Color.fromRGB(120, 60, 200), 3.0f));
        world.playSound(deathLoc, Sound.ENTITY_ALLAY_DEATH, 1.0f, 0.3f);
        world.playSound(deathLoc, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.3f);
        world.playSound(deathLoc, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 0.3f);

        // Subtítulo final
        player.sendTitle("", SmallCaps.convert("§7§o\"No me olvides... por favor.\""), 10, 120, 40);

        // Diálogo de despedida — Parte 1: La caída
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("§5§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("  §7§o*El Errante cae de rodillas.*"));
            player.sendMessage(SmallCaps.convert("  §7§o*La armadura oscura se resquebraja.*"));
            player.sendMessage(SmallCaps.convert("  §7§o*La corrupción se disuelve como ceniza al viento,*"));
            player.sendMessage(SmallCaps.convert("  §7§o*revelando un rostro cansado, viejo... humano.*"));
            player.sendMessage("");
        }, 40L);

        // Parte 2: La revelación
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.sendMessage(SmallCaps.convert("  §7§o*Te mira con ojos que por primera vez*"));
            player.sendMessage(SmallCaps.convert("  §7§o*no son violetas. Son marrones. Humanos.*"));
            player.sendMessage(SmallCaps.convert("  §7§o*Y sonríe. Por primera y última vez.*"));
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("  §f§l✦ §7\"Lo hiciste... gracias.\""));
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("  §7§o*Con manos temblorosas, se quita el casco*"));
            player.sendMessage(SmallCaps.convert("  §7§o*y te lo ofrece. Pesa más de lo que parece.*"));
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("  §f§l✦ §7\"Esto es todo lo que queda de mí."));
            player.sendMessage(SmallCaps.convert("  §7  Mi casco. Mis recuerdos. Mi nombre.\""));
            player.sendMessage("");
        }, 100L);

        // Parte 3: El nombre
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.sendMessage(SmallCaps.convert("  §7§o*Tose. Le cuesta hablar.*"));
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("  §f§l✦ §7\"Nunca te dije mi nombre, ¿verdad?\""));
            player.sendMessage(SmallCaps.convert("  §f§l✦ §7\"Llevé tanto tiempo siendo 'El Errante'"));
            player.sendMessage(SmallCaps.convert("  §7  que casi lo olvido yo también.\""));
            player.sendMessage("");
        }, 160L);

        // Parte 4: La revelación del nombre
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.sendTitle("", SmallCaps.convert("§d§o\"Me llamo Elián.\""), 20, 100, 30);
            player.sendMessage(SmallCaps.convert("  §d§l✦ §d§o\"Me llamo Elián.\""));
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("  §d§l✦ §7\"Lyra me llamaba Eli."));
            player.sendMessage(SmallCaps.convert("  §7  Decía que sonaba a luz de atardecer.\""));
            player.sendMessage("");
            World w1 = player.getWorld();
            w1.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 0.5f);
        }, 220L);

        // Parte 5: La despedida
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.sendMessage(SmallCaps.convert("  §7§o*Sus ojos se llenan de lágrimas.*"));
            player.sendMessage(SmallCaps.convert("  §7§o*Mira hacia arriba, como si viera algo*"));
            player.sendMessage(SmallCaps.convert("  §7§o*que tú no puedes ver.*"));
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("  §d§l✦ §d§o\"Lyra... ya puedo verte.\""));
            player.sendMessage(SmallCaps.convert("  §d§l✦ §d§o\"Perdóname por haber tardado tanto.\""));
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("  §7§o*Elián cierra los ojos.*"));
            player.sendMessage(SmallCaps.convert("  §7§o*Y sonríe.*"));
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("  §7§o*Su cuerpo se convierte en miles de partículas*"));
            player.sendMessage(SmallCaps.convert("  §7§o*doradas y violetas que ascienden despacio,*"));
            player.sendMessage(SmallCaps.convert("  §7§o*como luciérnagas en una noche de verano.*"));
            player.sendMessage(SmallCaps.convert("  §7§o*Donde estaba él, solo queda su casco.*"));
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("  §7§o*Y silencio.*"));
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("§5§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
            player.sendMessage("");

            // Partículas de ascensión masivas
            World w = player.getWorld();
            Location loc = deathLoc.clone();
            for (int i = 0; i < 15; i++) {
                final int fi = i;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    w.spawnParticle(Particle.END_ROD, loc.clone().add(0, fi * 2, 0), 60, 1.5, 0.5, 1.5, 0.03);
                    w.spawnParticle(Particle.SOUL, loc.clone().add(0, fi * 2 + 1, 0), 40, 0.8, 0.5, 0.8, 0.02);
                    w.spawnParticle(Particle.DUST, loc.clone().add(0, fi * 2, 0), 20, 1, 0.5, 1, 0,
                            new Particle.DustOptions(Color.fromRGB(255, 200, 80), 1.5f));
                }, fi * 5L);
            }
        }, 300L);

        // Título final
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.sendTitle(SmallCaps.convert("§d§lElián"),
                    SmallCaps.convert("§7El Errante descansa en paz."), 30, 140, 40);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f);
        }, 380L);

        // Dar recompensa y completar quest
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (bossBar != null) bossBar.removeAll();
            manager.onQuest10BossDefeated(player);
        }, 440L);
    }

    // ==================== ABILITIES ====================

    private void tickAbilities() {
        if (bossEntity == null || bossEntity.isDead()) return;
        double dist = bossEntity.getLocation().distanceSquared(player.getLocation());

        if (phase == 1) {
            // Pulso de sombra
            if (shadowPulseCD <= 0 && dist < 100) {
                shadowPulse();
                shadowPulseCD = 16; // 8s
            }
        } else if (phase == 2) {
            // Eco de memoria (slow + partículas)
            if (memoryEchoCD <= 0 && dist < 225) {
                memoryEcho();
                memoryEchoCD = 14; // 7s
            }
            if (shadowPulseCD <= 0 && dist < 100) {
                shadowPulse();
                shadowPulseCD = 20;
            }
        } else if (phase == 3) {
            // Fase más agresiva
            if (corruptionWaveCD <= 0) {
                corruptionWave();
                corruptionWaveCD = 10; // 5s
            }
            if (voidRiftCD <= 0 && dist < 400) {
                voidRift();
                voidRiftCD = 16;
            }
            if (despairNovaCD <= 0 && dist < 64) {
                despairNova();
                despairNovaCD = 20;
            }
        }
        // Fase 4: casi no ataca, está muriendo
    }

    private void shadowPulse() {
        World world = bossEntity.getWorld();
        Location loc = bossEntity.getLocation().add(0, 1, 0);
        world.spawnParticle(Particle.DUST, loc, 60, 4, 2, 4, 0,
                new Particle.DustOptions(Color.fromRGB(80, 0, 120), 2.0f));
        world.playSound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.3f, 0.5f);

        if (player.getLocation().distanceSquared(loc) < 36) {
            player.damage(6.0, bossEntity);
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false));
        }
    }

    private void memoryEcho() {
        World world = bossEntity.getWorld();
        Location loc = player.getLocation().add(0, 1, 0);
        world.spawnParticle(Particle.SOUL, loc, 40, 2, 1, 2, 0.05);
        world.spawnParticle(Particle.END_ROD, loc, 20, 1, 1, 1, 0.02);
        world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 0.5f);

        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 60, 0, false, false));
    }

    private void corruptionWave() {
        World world = bossEntity.getWorld();
        Location center = bossEntity.getLocation().add(0, 0.5, 0);

        // Onda expansiva de corrupción
        for (int angle = 0; angle < 360; angle += 15) {
            double rad = Math.toRadians(angle);
            for (double dist = 1; dist <= 8; dist += 1.5) {
                Location p = center.clone().add(Math.cos(rad) * dist, 0, Math.sin(rad) * dist);
                world.spawnParticle(Particle.DUST, p, 2, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(150, 0, 0), 1.5f));
            }
        }
        world.playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 0.3f);

        if (player.getLocation().distanceSquared(center) < 100) {
            player.damage(8.0, bossEntity);
            Vector knockback = player.getLocation().toVector().subtract(center.toVector()).normalize().multiply(1.2);
            knockback.setY(0.4);
            player.setVelocity(knockback);
        }
    }

    private void voidRift() {
        Location target = player.getLocation().clone();
        World world = target.getWorld();

        world.spawnParticle(Particle.PORTAL, target.clone().add(0, 1, 0), 100, 1, 2, 1, 0.5);
        world.playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.3f);

        // Daño retrasado
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            world.spawnParticle(Particle.DUST, target.clone().add(0, 1, 0), 80, 2, 2, 2, 0,
                    new Particle.DustOptions(Color.fromRGB(0, 0, 0), 2.5f));
            world.playSound(target, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 0.5f);
            if (player.getLocation().distanceSquared(target) < 16) {
                player.damage(10.0, bossEntity);
            }
        }, 30L);
    }

    private void despairNova() {
        World world = bossEntity.getWorld();
        Location loc = bossEntity.getLocation().add(0, 1, 0);

        world.spawnParticle(Particle.SMOKE, loc, 100, 3, 3, 3, 0.1);
        world.spawnParticle(Particle.DUST, loc, 80, 4, 4, 4, 0,
                new Particle.DustOptions(Color.fromRGB(50, 0, 50), 3.0f));
        world.playSound(loc, Sound.ENTITY_WITHER_SHOOT, 0.8f, 0.3f);

        player.damage(12.0, bossEntity);
        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 80, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0, false, false));
    }

    // ==================== DIALOGUE ====================

    private void sendPhaseDialogue() {
        int pIdx = phase - 1;
        if (pIdx < 0 || pIdx >= phaseDialogues.length) return;
        String[] lines = phaseDialogues[pIdx];
        if (dialogueIdx >= lines.length) dialogueIdx = 0;

        String line = lines[dialogueIdx];
        player.sendMessage(SmallCaps.convert("§5§l✦ §7El Errante: " + line));

        // También como subtítulo
        player.sendTitle("", SmallCaps.convert(line), 5, 60, 15);

        dialogueIdx++;
    }

    // ==================== AMBIENT PARTICLES ====================

    private void ambientParticles() {
        if (bossEntity == null || bossEntity.isDead()) return;
        World world = bossEntity.getWorld();
        Location loc = bossEntity.getLocation().add(0, 1, 0);

        if (phase == 1) {
            world.spawnParticle(Particle.DUST, loc, 5, 1, 1, 1, 0,
                    new Particle.DustOptions(Color.fromRGB(80, 0, 120), 1.5f));
            world.spawnParticle(Particle.SMOKE, loc, 3, 0.5, 0.5, 0.5, 0.02);
        } else if (phase == 2) {
            world.spawnParticle(Particle.SOUL, loc, 3, 0.5, 1, 0.5, 0.02);
            world.spawnParticle(Particle.END_ROD, loc, 2, 0.3, 0.5, 0.3, 0.01);
        } else if (phase == 3) {
            world.spawnParticle(Particle.DUST, loc, 8, 1.5, 1.5, 1.5, 0,
                    new Particle.DustOptions(Color.fromRGB(200, 0, 0), 2.0f));
            world.spawnParticle(Particle.SMOKE, loc, 5, 1, 1, 1, 0.05);
        } else {
            world.spawnParticle(Particle.END_ROD, loc, 4, 0.5, 1.5, 0.5, 0.03);
            world.spawnParticle(Particle.DUST, loc, 3, 0.5, 1, 0.5, 0,
                    new Particle.DustOptions(Color.fromRGB(200, 200, 220), 1.5f));
        }
    }

    // ==================== UTILITY ====================

    public boolean isActive() { return active; }
    public boolean isBossEntity(UUID id) { return bossEntity != null && bossEntity.getUniqueId().equals(id); }
    public boolean isSummonedMob(UUID id) { return spawnedEntityIds.contains(id); }
    public Location getArenaCenter() { return arenaCenter; }

    public void collectProtectedEntities(List<Entity> list) {
        if (bossEntity != null && !bossEntity.isDead()) list.add(bossEntity);
        for (Entity e : summonedMobs) {
            if (e != null && !e.isDead()) list.add(e);
        }
    }

    public void cleanup() {
        active = false;
        if (mainLoop != null) {
            try { mainLoop.cancel(); } catch (Exception ignored) {}
        }
        if (bossBar != null) {
            bossBar.removeAll();
        }
        if (bossEntity != null && !bossEntity.isDead()) {
            bossEntity.remove();
        }
        for (Entity e : summonedMobs) {
            if (e != null && !e.isDead()) e.remove();
        }
        summonedMobs.clear();
        spawnedEntityIds.clear();
        // Parar audio custom
        if (player != null && player.isOnline()) {
            player.stopSound("nexo:music.errante_fin", SoundCategory.MASTER);
        }
    }
}
