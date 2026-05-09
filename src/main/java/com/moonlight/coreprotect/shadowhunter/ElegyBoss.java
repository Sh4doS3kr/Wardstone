package com.moonlight.coreprotect.shadowhunter;

import com.moonlight.coreprotect.CoreProtectPlugin;
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
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Boss de la Misión 4: "La Última Memoria del Errante"
 * Un boss super OP pero no imposible. Es la última memoria del Errante,
 * un guerrero corrompido por el Vacío que debe ser liberado.
 * 
 * 800 HP, ataques devastadores, fases múltiples.
 * El jugador respawnea en la arena si muere (keep inventory).
 */
public class ElegyBoss {

    private final CoreProtectPlugin plugin;
    private final ShadowHunterManager manager;
    private final Player player;
    private final ShadowHunterNPC npc;

    private boolean active = false;
    private Zombie bossEntity;
    private BossBar bossBar;
    private BukkitRunnable mainLoop;
    private int tickCount = 0;
    private int phase = 1; // 1, 2, 3
    private long lastDamageTime;
    private Location arenaCenter;
    private boolean finalSceneTriggered = false; // Final confrontation at 1 HP
    private boolean finalSceneActive = false; // True while the final scene is playing

    // Summoned mobs
    private final List<Entity> summonedMobs = new ArrayList<>();

    // Ability cooldowns
    private int soulOrbitCD = 0;
    private int risingBlocksCD = 0;
    private int voidTornadoCD = 0;
    private int voidBeamCD = 0;
    private int tntRainCD = 0;
    private int blockCastleCD = 0;
    private int soulGeysersCD = 0;
    private int shadowDiveCD = 0;
    private int shadowClonesCD = 0;
    private int starfallCD = 0;
    private int floatingDebrisCD = 0;
    private int memoryCollapseCD = 0;
    private int witherNovaCD = 0;
    private int voidRiftCD = 0;
    private int flyTickCD = 0;

    // Taunts
    private final String[] phase1Taunts = {
            "§5\"¿Me recuerdas...? Yo era como tú...\"",
            "§5\"Cada golpe que me das... me recuerda lo que fui.\"",
            "§5\"El Vacío me consumió... no dejes que te consuma a ti.\"",
            "§5\"Lucha... pero no olvides por qué luchas.\""
    };
    private final String[] phase2Taunts = {
            "§c\"¡El dolor! ¡Siento... siento otra vez!\"",
            "§c\"¿Por qué luchas? ¡Ya no queda nada por salvar!\"",
            "§c\"Yo construí mundos enteros... y el Vacío los destruyó todos.\"",
            "§c\"Si me derrotas... al menos seré libre.\""
    };
    private final String[] phase3Taunts = {
            "§4\"¡ESTE ES MI ÚLTIMO ALIENTO!\"",
            "§4\"¡NO QUIERO DESAPARECER!\"",
            "§4\"¡PERO... QUIZÁS... ES MEJOR ASÍ!\"",
            "§d\"Gracias... por venir a liberarme.\""
    };
    private int tauntIndex = 0;

    public ElegyBoss(CoreProtectPlugin plugin, ShadowHunterManager manager, Player player, ShadowHunterNPC npc) {
        this.plugin = plugin;
        this.manager = manager;
        this.player = player;
        this.npc = npc;
    }

    public void start() {
        active = true;
        lastDamageTime = System.currentTimeMillis();

        // Intro dramática antes de spawnear al boss
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§5§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        player.sendMessage(SmallCaps.convert("  §5§l✦ LA ÚLTIMA MEMORIA DEL ERRANTE ✦"));
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("  §7Lo que ves ante ti es lo que"));
        player.sendMessage(SmallCaps.convert("  §7el Errante fue antes del Vacío..."));
        player.sendMessage(SmallCaps.convert("  §7Un guerrero consumido por la oscuridad."));
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("  §c§l⚠ ¡500 HP! ¡Múltiples fases!"));
        player.sendMessage(SmallCaps.convert("  §e⚠ Si mueres, respawneas aquí."));
        player.sendMessage(SmallCaps.convert("§5§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        player.sendMessage("");

        player.sendTitle( SmallCaps.convert("§5§l✦ FASE 1 ✦"), SmallCaps.convert("§7La Última Memoria despierta..."), 10, 80, 20);
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.8f, 0.5f);

        // Disable flight
        if (player.getAllowFlight()) {
            player.setAllowFlight(false);
            player.setFlying(false);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            spawnBoss();
            startMainLoop();
        }, 80L);
    }

    private void spawnBoss() {
        Location spawnLoc = player.getLocation().clone().add(
                player.getLocation().getDirection().normalize().multiply(5));
        spawnLoc.setY(player.getLocation().getY());
        
        // Guardar ubicación de la arena para respawns
        arenaCenter = spawnLoc.clone();

        // Efecto de aparición
        World world = spawnLoc.getWorld();
        world.spawnParticle(Particle.DUST, spawnLoc.clone().add(0, 1, 0), 20, 2, 2, 2, 0,
                new Particle.DustOptions(Color.fromRGB(120, 50, 180), 3.0f));
        world.spawnParticle(Particle.SOUL, spawnLoc.clone().add(0, 1, 0), 8, 1, 2, 1, 0.1);
        world.playSound(spawnLoc, Sound.ENTITY_WARDEN_EMERGE, 1.0f, 0.5f);

        bossBar = Bukkit.createBossBar("§5§l✦ La Última Memoria del Errante ✦ §7[FASE 1]",
                BarColor.PURPLE, BarStyle.SEGMENTED_10);
        bossBar.addPlayer(player);

        bossEntity = player.getWorld().spawn(spawnLoc, Zombie.class, z -> {
            z.setCustomName(SmallCaps.convert("§5§l✦ La Última Memoria §5§l✦"));
            z.setCustomNameVisible(true);
            z.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).setBaseValue(500);
            z.setHealth(500);
            z.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(6);
            z.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.30);
            z.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ARMOR).setBaseValue(8);
            z.getAttribute(org.bukkit.attribute.Attribute.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(0.4);
            z.setBaby(false);
            z.setTarget(player);
            z.setRemoveWhenFarAway(false);
            z.setPersistent(true);

            // Equipo visual: armadura de cuero morada/oscura (como el Errante)
            ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
            LeatherArmorMeta hMeta = (LeatherArmorMeta) helmet.getItemMeta();
            hMeta.setColor(Color.fromRGB(60, 0, 100));
            helmet.setItemMeta(hMeta);

            ItemStack chest = new ItemStack(Material.NETHERITE_CHESTPLATE);
            ItemStack legs = new ItemStack(Material.NETHERITE_LEGGINGS);
            ItemStack boots = new ItemStack(Material.NETHERITE_BOOTS);

            z.getEquipment().setHelmet(helmet);
            z.getEquipment().setChestplate(chest);
            z.getEquipment().setLeggings(legs);
            z.getEquipment().setBoots(boots);
            z.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));

            z.getEquipment().setHelmetDropChance(0);
            z.getEquipment().setChestplateDropChance(0);
            z.getEquipment().setLeggingsDropChance(0);
            z.getEquipment().setBootsDropChance(0);
            z.getEquipment().setItemInMainHandDropChance(0);

            z.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 99999, 0, false, false));
            z.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 99999, 0, false, false));
            z.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 99999, 0, false, false));
            // Prevent fall damage
            try {
                z.getAttribute(Attribute.GENERIC_SAFE_FALL_DISTANCE).setBaseValue(999);
            } catch (Exception ignored) {}
        });
    }

    private void startMainLoop() {
        mainLoop = new BukkitRunnable() {
            @Override
            public void run() {
                if (!active || bossEntity == null || bossEntity.isDead()) {
                    cancel();
                    // Only complete the quest if the fatal-damage flow was triggered
                    if (bossEntity != null && bossEntity.isDead() && finalSceneTriggered) {
                        onBossDefeated();
                    }
                    return;
                }

                if (!player.isOnline()) {
                    cancel();
                    cleanup();
                    return;
                }

                tickCount++;

                // SAFETY NET: si la vida del boss baja a 1 o menos y la escena final no se ha disparado, forzar
                if (!finalSceneTriggered && bossEntity.getHealth() <= 1.5) {
                    bossEntity.setHealth(1);
                    onBossFatalDamage();
                    return;
                }

                // Update boss bar
                if (bossBar != null) {
                    double hp = bossEntity.getHealth() / bossEntity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getBaseValue();
                    bossBar.setProgress(Math.max(0, Math.min(1, hp)));
                }

                // Si la escena final está activa, mantener al boss en 1HP y solo partículas ambientales
                if (finalSceneActive) {
                    bossEntity.setHealth(1);
                    if (tickCount % 5 == 0 && bossEntity != null && !bossEntity.isDead()) {
                        Location bLoc = bossEntity.getLocation().add(0, 1.5, 0);
                        bLoc.getWorld().spawnParticle(Particle.SOUL, bLoc, 3, 0.3, 0.5, 0.3, 0.02);
                        bLoc.getWorld().spawnParticle(Particle.DUST, bLoc, 2, 0.5, 0.8, 0.5, 0,
                                new Particle.DustOptions(Color.fromRGB(200, 100, 255), 1.5f));
                    }
                    return; // No hacer nada más durante la escena final
                }

                // Phase transitions
                double healthPercent = bossEntity.getHealth() / bossEntity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getBaseValue();
                if (healthPercent <= 0.30 && phase < 3) {
                    enterPhase3();
                } else if (healthPercent <= 0.60 && phase < 2) {
                    enterPhase2();
                }

                // Anti-trap: si el boss está lejos del jugador, se teletransporta a él
                if (player.isOnline() && !player.isDead()) {
                    double bossPlayerDist = bossEntity.getLocation().distance(player.getLocation());
                    if (bossPlayerDist > 20 && tickCount % 60 == 0) {
                        // TP al jugador si está atrapado o demasiado lejos
                        Location tpLoc = player.getLocation().clone()
                                .add(player.getLocation().getDirection().normalize().multiply(3));
                        tpLoc.setY(player.getLocation().getY());
                        Location oldLoc = bossEntity.getLocation();
                        oldLoc.getWorld().spawnParticle(Particle.PORTAL, oldLoc.clone().add(0, 1, 0), 40, 0.5, 1, 0.5, 1);
                        oldLoc.getWorld().playSound(oldLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);
                        bossEntity.teleport(tpLoc);
                        tpLoc.getWorld().spawnParticle(Particle.PORTAL, tpLoc.clone().add(0, 1, 0), 40, 0.5, 1, 0.5, 1);
                        player.sendMessage(SmallCaps.convert("§5§l✦ §5La Memoria se libera de su trampa y aparece ante ti."));
                    }
                    // También TP si no puede moverse (pathfinding bloqueado)
                    if (bossPlayerDist > 12 && tickCount % 100 == 0
                            && bossEntity.getVelocity().lengthSquared() < 0.001) {
                        Location tpLoc = player.getLocation().clone()
                                .add(player.getLocation().getDirection().normalize().multiply(2));
                        tpLoc.setY(player.getLocation().getY());
                        bossEntity.teleport(tpLoc);
                        tpLoc.getWorld().spawnParticle(Particle.PORTAL, tpLoc.clone().add(0, 1, 0), 30, 0.5, 1, 0.5, 1);
                    }
                }

                // Boss abilities based on phase
                tickAbilities();

                // Occasional flying (Phase 2+)
                if (phase >= 2 && flyTickCD <= 0 && !finalSceneActive && Math.random() < 0.007) {
                    bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 50, 2, false, false));
                    flyTickCD = 300 + (int)(Math.random() * 200);
                    Location bLoc2 = bossEntity.getLocation();
                    bLoc2.getWorld().spawnParticle(Particle.END_ROD, bLoc2.clone().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.12);
                    bLoc2.getWorld().playSound(bLoc2, Sound.ENTITY_PHANTOM_FLAP, 0.6f, 0.4f);
                }

                // Decrement cooldowns
                if (soulOrbitCD > 0) soulOrbitCD--;
                if (risingBlocksCD > 0) risingBlocksCD--;
                if (voidTornadoCD > 0) voidTornadoCD--;
                if (voidBeamCD > 0) voidBeamCD--;
                if (tntRainCD > 0) tntRainCD--;
                if (blockCastleCD > 0) blockCastleCD--;
                if (soulGeysersCD > 0) soulGeysersCD--;
                if (shadowDiveCD > 0) shadowDiveCD--;
                if (shadowClonesCD > 0) shadowClonesCD--;
                if (starfallCD > 0) starfallCD--;
                if (floatingDebrisCD > 0) floatingDebrisCD--;
                if (memoryCollapseCD > 0) memoryCollapseCD--;
                if (witherNovaCD > 0) witherNovaCD--;
                if (voidRiftCD > 0) voidRiftCD--;
                if (flyTickCD > 0) flyTickCD--;

                // Ambient particles around boss
                if (tickCount % 10 == 0 && bossEntity != null && !bossEntity.isDead()) {
                    Location bLoc = bossEntity.getLocation().add(0, 1.5, 0);
                    Color particleColor = phase == 3 ? Color.fromRGB(200, 0, 50) :
                            (phase == 2 ? Color.fromRGB(180, 0, 100) : Color.fromRGB(120, 50, 180));
                    bLoc.getWorld().spawnParticle(Particle.DUST, bLoc, 2, 0.5, 0.8, 0.5, 0,
                            new Particle.DustOptions(particleColor, 1.5f));
                }

                // Taunts every ~15 seconds
                if (tickCount % 300 == 0) {
                    String[] taunts = phase == 3 ? phase3Taunts : (phase == 2 ? phase2Taunts : phase1Taunts);
                    if (npc != null) {
                        npc.say(taunts[tauntIndex % taunts.length]);
                        tauntIndex++;
                    }
                }

                // Keep boss targeting player
                if (tickCount % 40 == 0 && bossEntity.getTarget() != player) {
                    bossEntity.setTarget(player);
                }

                // Clean dead summoned mobs
                summonedMobs.removeIf(e -> e == null || e.isDead());
            }
        };
        mainLoop.runTaskTimer(plugin, 0, 1);
    }

    private void tickAbilities() {
        if (!player.isOnline() || player.isDead()) return;
        double dist = bossEntity.getLocation().distance(player.getLocation());

        // === FASE 1 ===
        if (phase >= 1) {
            if (soulOrbitCD <= 0) {
                soulOrbit(); soulOrbitCD = 160 + (int)(Math.random() * 100);
            }
            if (risingBlocksCD <= 0 && dist < 20) {
                risingBlocks(); risingBlocksCD = 180 + (int)(Math.random() * 100);
            }
            if (voidTornadoCD <= 0 && dist < 22) {
                voidTornado(); voidTornadoCD = 200 + (int)(Math.random() * 120);
            }
            if (voidBeamCD <= 0 && dist > 4 && dist < 28) {
                voidBeam(); voidBeamCD = 150 + (int)(Math.random() * 100);
            }
        }

        // === FASE 2 ===
        if (phase >= 2) {
            if (tntRainCD <= 0) {
                tntRain(); tntRainCD = 220 + (int)(Math.random() * 140);
            }
            if (blockCastleCD <= 0 && dist < 22) {
                blockCastle(); blockCastleCD = 290 + (int)(Math.random() * 150);
            }
            if (soulGeysersCD <= 0 && dist < 20) {
                soulGeysers(); soulGeysersCD = 200 + (int)(Math.random() * 100);
            }
            if (shadowDiveCD <= 0) {
                shadowDive(); shadowDiveCD = 250 + (int)(Math.random() * 130);
            }
            if (shadowClonesCD <= 0) {
                shadowClones(); shadowClonesCD = 260 + (int)(Math.random() * 120);
            }
        }

        // === FASE 3 ===
        if (phase >= 3) {
            if (starfallCD <= 0) {
                starfall(); starfallCD = 240 + (int)(Math.random() * 130);
            }
            if (floatingDebrisCD <= 0) {
                floatingDebris(); floatingDebrisCD = 200 + (int)(Math.random() * 100);
            }
            if (memoryCollapseCD <= 0) {
                memoryCollapse(); memoryCollapseCD = 380 + (int)(Math.random() * 200);
            }
            if (witherNovaCD <= 0 && dist < 22) {
                witherNova(); witherNovaCD = 220 + (int)(Math.random() * 120);
            }
            if (voidRiftCD <= 0) {
                voidRift(); voidRiftCD = 200 + (int)(Math.random() * 100);
            }
        }
    }

    // ==================== BOSS ABILITIES ====================

    private void soulOrbit() {
        Location center = bossEntity.getLocation().clone().add(0, 1, 0);
        World world = center.getWorld();
        world.playSound(center, Sound.PARTICLE_SOUL_ESCAPE, 0.6f, 0.5f);
        new BukkitRunnable() {
            double angle = 0;
            int ticks = 0;
            boolean pulsed = false;
            @Override
            public void run() {
                if (!active || ticks > 80 || bossEntity == null || bossEntity.isDead()) { cancel(); return; }
                for (int i = 0; i < 4; i++) {
                    double a = angle + (Math.PI * 2 / 4) * i;
                    double height = Math.sin(angle * 2 + (Math.PI / 4) * i) * 1.2;
                    Location orbLoc = bossEntity.getLocation().add(Math.cos(a) * 3.5, 1.5 + height, Math.sin(a) * 3.5);
                    world.spawnParticle(Particle.DUST, orbLoc, 1, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(100 + i * 30, 0, 220), 1.3f));
                }
                if (!pulsed && ticks == 60) {
                    pulsed = true;
                    world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.4f, 0.8f);
                    new BukkitRunnable() {
                        double r = 0;
                        @Override
                        public void run() {
                            if (r > 10) { cancel(); return; }
                            for (double a = 0; a < Math.PI * 2; a += 0.5) {
                                Location pLoc = bossEntity.getLocation().clone().add(Math.cos(a) * r, 1, Math.sin(a) * r);
                                world.spawnParticle(Particle.DUST, pLoc, 1, 0, 0, 0, 0,
                                    new Particle.DustOptions(Color.fromRGB(180, 0, 255), 1.5f));
                            }
                            r += 1.5;
                        }
                    }.runTaskTimer(plugin, 0, 1);
                    if (player.isOnline() && !player.isDead() && player.getLocation().distance(bossEntity.getLocation()) < 9) {
                        player.damage(7, bossEntity);
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                    }
                }
                angle += 0.12;
                ticks++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private void risingBlocks() {
        Location center = player.getLocation().clone();
        World world = center.getWorld();
        world.playSound(center, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.8f, 0.3f);
        List<FallingBlock> blocks = new ArrayList<>();
        int count = 10;
        Material[] mats = {Material.OBSIDIAN, Material.AMETHYST_BLOCK, Material.PURPUR_BLOCK, Material.CRYING_OBSIDIAN, Material.BLACKSTONE};
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2.0 / count) * i;
            double radius = 2.0 + Math.random() * 3.0;
            Location spawnLoc = center.clone().add(Math.cos(angle) * radius, -1, Math.sin(angle) * radius);
            Material mat = mats[i % mats.length];
            FallingBlock fb = world.spawnFallingBlock(spawnLoc, mat.createBlockData());
            fb.setDropItem(false);
            fb.setHurtEntities(false);
            fb.setVelocity(new Vector(0, 0.4 + Math.random() * 0.2, 0));
            blocks.add(fb);
            world.spawnParticle(Particle.DUST, spawnLoc.clone().add(0, 1, 0), 1, 0.1, 0.1, 0.1, 0,
                new Particle.DustOptions(Color.fromRGB(160, 0, 200), 1.5f));
        }
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t >= 40 || !active) {
                    cancel();
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!active) return;
                        for (FallingBlock fb : blocks) {
                            if (!fb.isDead()) {
                                fb.setHurtEntities(true);
                                Vector dir = player.getLocation().add(0, 1, 0).toVector()
                                    .subtract(fb.getLocation().toVector()).normalize().multiply(0.55).setY(0.5);
                                fb.setVelocity(dir);
                            }
                        }
                        world.playSound(center, Sound.ENTITY_WARDEN_ROAR, 0.4f, 0.9f);
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            for (FallingBlock fb : blocks) if (!fb.isDead()) fb.remove();
                        }, 60L);
                    }, 2L);
                    return;
                }
                for (FallingBlock fb : blocks) {
                    if (!fb.isDead()) {
                        fb.setVelocity(new Vector(fb.getVelocity().getX() * 0.1, 0.06, fb.getVelocity().getZ() * 0.1));
                        world.spawnParticle(Particle.DUST, fb.getLocation(), 1, 0.1, 0.1, 0.1, 0,
                            new Particle.DustOptions(Color.fromRGB(100, 0, 180), 0.9f));
                    }
                }
                t++;
            }
        }.runTaskTimer(plugin, 12, 1);
    }

    private void voidTornado() {
        Location center = bossEntity.getLocation().clone();
        World world = center.getWorld();
        world.playSound(center, Sound.BLOCK_PORTAL_TRIGGER, 0.8f, 0.4f);
        new BukkitRunnable() {
            double angle = 0;
            int ticks = 0;
            @Override
            public void run() {
                if (!active || ticks > 120 || bossEntity == null || bossEntity.isDead()) { cancel(); return; }
                for (int arm = 0; arm < 3; arm++) {
                    double armAngle = angle + (Math.PI * 2 / 3) * arm;
                    for (int layer = 0; layer < 12; layer += 2) {
                        double layerAngle = armAngle + layer * 0.35;
                        double r = 1.8 + layer * 0.12;
                        double y = layer * 0.35;
                        Color c = Color.fromRGB(50 + layer * 12, 0, 220 - layer * 8);
                        Location pLoc = center.clone().add(Math.cos(layerAngle) * r, y, Math.sin(layerAngle) * r);
                        world.spawnParticle(Particle.DUST, pLoc, 1, 0, 0, 0, 0, new Particle.DustOptions(c, 1.4f));
                    }
                }
                if (ticks % 3 == 0 && player.isOnline() && !player.isDead()) {
                    double dist = player.getLocation().distance(center);
                    if (dist < 14 && dist > 1.5) {
                        Vector pull = center.toVector().subtract(player.getLocation().toVector()).normalize().multiply(0.18);
                        player.setVelocity(player.getVelocity().add(pull));
                    }
                    if (dist < 2.5) player.damage(4, bossEntity);
                }
                if (ticks % 30 == 0) world.playSound(center, Sound.BLOCK_PORTAL_TRIGGER, 0.3f, 0.5f);
                angle += 0.16;
                ticks++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private void voidBeam() {
        World world = bossEntity.getWorld();
        world.playSound(bossEntity.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.7f, 0.5f);
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (!active || t >= 25 || bossEntity == null || bossEntity.isDead()) { cancel(); return; }
                Location from = bossEntity.getLocation().add(0, 1.5, 0);
                if (!player.isOnline()) { cancel(); return; }
                Location to = player.getLocation().add(0, 1, 0);
                Vector dir = to.toVector().subtract(from.toVector());
                double len = dir.length();
                if (len < 0.1) { t++; return; }
                dir.normalize();
                Color beamColor = t < 15 ? Color.fromRGB(100, 200, 255) : Color.fromRGB(255, Math.min(255, 50 + t * 8), 50);
                for (double d = 0; d < len; d += 0.7) {
                    Location pt = from.clone().add(dir.clone().multiply(d));
                    world.spawnParticle(Particle.DUST, pt, 1, 0.05, 0.05, 0.05, 0,
                        new Particle.DustOptions(beamColor, t < 15 ? 1.0f : 1.4f));
                }
                if (t == 15) {
                    world.playSound(from, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 1.3f);
                    world.spawnParticle(Particle.SOUL, to, 5, 0.5, 1, 0.5, 0.05);
                    if (!player.isDead() && player.getLocation().distance(bossEntity.getLocation()) < 22) {
                        player.damage(11, bossEntity);
                    }
                }
                t++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private void tntRain() {
        Location playerLoc = player.getLocation().clone();
        World world = playerLoc.getWorld();
        world.playSound(playerLoc, Sound.ENTITY_WITHER_SHOOT, 0.7f, 0.5f);
        int count = phase == 3 ? 9 : 5;
        for (int i = 0; i < count; i++) {
            final int idx = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!active || !player.isOnline()) return;
                Location target = player.getLocation().clone().add((Math.random() - 0.5) * 12, 0, (Math.random() - 0.5) * 12);
                simulateTntBomb(target, world);
            }, idx * 14L);
        }
    }

    private void simulateTntBomb(Location target, World world) {
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t >= 20) { cancel(); return; }
                double r = 2.5 - t * 0.05;
                for (double a = 0; a < Math.PI * 2; a += 0.8) {
                    world.spawnParticle(Particle.DUST,
                        target.clone().add(Math.cos(a) * r, 0.05, Math.sin(a) * r),
                        1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(255, 50 + t * 5, 0), 1.3f));
                }
                t++;
            }
        }.runTaskTimer(plugin, 0, 1);
        Location spawnLoc = target.clone().add((Math.random() - 0.5) * 2, 15, (Math.random() - 0.5) * 2);
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t >= 22 || !active) {
                    cancel();
                    world.spawnParticle(Particle.EXPLOSION, target.clone().add(0, 1, 0), 2, 0.5, 0.5, 0.5, 0.2);
                    world.spawnParticle(Particle.FLAME, target.clone().add(0, 1, 0), 8, 1.2, 0.8, 1.2, 0.06);
                    world.spawnParticle(Particle.DUST, target.clone().add(0, 1.5, 0), 5, 1.5, 1.5, 1.5, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 100, 0), 2.5f));
                    world.playSound(target, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.85f);
                    if (player.isOnline() && !player.isDead() && player.getLocation().distance(target) < 4.5) {
                        player.damage(10, bossEntity);
                        Vector kb = player.getLocation().toVector().subtract(target.toVector()).normalize().multiply(0.9).setY(0.6);
                        player.setVelocity(kb);
                    }
                    return;
                }
                double progress = (double) t / 22;
                double y = spawnLoc.getY() - (spawnLoc.getY() - target.getY()) * progress;
                double x = spawnLoc.getX() + (target.getX() - spawnLoc.getX()) * progress;
                double z = spawnLoc.getZ() + (target.getZ() - spawnLoc.getZ()) * progress;
                Location current = new Location(world, x, y, z);
                world.spawnParticle(Particle.FLAME, current, 2, 0.15, 0.15, 0.15, 0.03);
                world.spawnParticle(Particle.DUST, current, 1, 0.2, 0.2, 0.2, 0,
                    new Particle.DustOptions(Color.fromRGB(255, 80, 0), 1.8f));
                t++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private void blockCastle() {
        Location center = player.getLocation().clone();
        World world = center.getWorld();
        world.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.9f, 0.3f);
        world.playSound(center, Sound.ENTITY_WARDEN_ROAR, 0.5f, 0.4f);
        List<FallingBlock> allBlocks = new ArrayList<>();
        Material[][] pillarMats = {
            {Material.AMETHYST_BLOCK, Material.PURPLE_STAINED_GLASS, Material.OBSIDIAN, Material.CRYING_OBSIDIAN, Material.AMETHYST_BLOCK},
            {Material.OBSIDIAN, Material.AMETHYST_BLOCK, Material.PURPLE_STAINED_GLASS, Material.AMETHYST_BLOCK, Material.OBSIDIAN},
            {Material.CRYING_OBSIDIAN, Material.OBSIDIAN, Material.AMETHYST_BLOCK, Material.PURPLE_STAINED_GLASS, Material.CRYING_OBSIDIAN},
            {Material.PURPLE_STAINED_GLASS, Material.CRYING_OBSIDIAN, Material.OBSIDIAN, Material.AMETHYST_BLOCK, Material.PURPLE_STAINED_GLASS}
        };
        for (int corner = 0; corner < 4; corner++) {
            double angle = (Math.PI / 2) * corner + Math.PI / 4;
            Location cornerBase = center.clone().add(Math.cos(angle) * 4, 0, Math.sin(angle) * 4);
            for (int h = 0; h < 5; h++) {
                FallingBlock fb = world.spawnFallingBlock(cornerBase.clone().add(0, 14 + h, 0), pillarMats[corner][h].createBlockData());
                fb.setDropItem(false);
                fb.setHurtEntities(false);
                fb.setVelocity(new Vector(0, -0.4, 0));
                allBlocks.add(fb);
            }
        }
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t >= 100 || !active) {
                    cancel();
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        for (FallingBlock fb : allBlocks) {
                            if (!fb.isDead()) {
                                Vector collapse = center.toVector().subtract(fb.getLocation().toVector()).normalize().multiply(0.4).setY(-0.3);
                                fb.setVelocity(collapse);
                                fb.setHurtEntities(true);
                            }
                        }
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            for (FallingBlock fb : allBlocks) if (!fb.isDead()) fb.remove();
                        }, 40L);
                    }, 5L);
                    return;
                }
                for (FallingBlock fb : allBlocks) {
                    if (!fb.isDead()) {
                        double vy = fb.getVelocity().getY();
                        if (Math.abs(vy) > 0.04) {
                            fb.setVelocity(fb.getVelocity().multiply(0.7).setY(vy * 0.6));
                        } else {
                            fb.setVelocity(new Vector(0, 0.04, 0));
                        }
                        if (t % 4 == 0) {
                            world.spawnParticle(Particle.DUST, fb.getLocation(), 1, 0.1, 0.1, 0.1, 0,
                                new Particle.DustOptions(Color.fromRGB(140, 0, 220), 0.8f));
                        }
                    }
                }
                t++;
            }
        }.runTaskTimer(plugin, 3, 1);
    }

    private void soulGeysers() {
        Location center = player.getLocation().clone();
        World world = center.getWorld();
        world.playSound(center, Sound.BLOCK_SOUL_SAND_HIT, 1.0f, 0.4f);
        int geysers = phase >= 3 ? 8 : 6;
        Material[] geyserMats = {Material.SOUL_SAND, Material.SOUL_SOIL};
        for (int i = 0; i < geysers; i++) {
            final int idx = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!active || !player.isOnline()) return;
                double angle = (Math.PI * 2.0 / geysers) * idx + Math.random() * 0.3;
                double radius = 3.0 + Math.random() * 2.0;
                Location geyserBase = center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
                for (double a = 0; a < Math.PI * 2; a += 0.4) {
                    world.spawnParticle(Particle.SOUL, geyserBase.clone().add(Math.cos(a) * 1.2, 0.1, Math.sin(a) * 1.2), 1, 0, 0, 0, 0.01);
                }
                world.playSound(geyserBase, Sound.BLOCK_SOUL_SAND_BREAK, 0.7f, 0.5f);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!active) return;
                    List<FallingBlock> gBlocks = new ArrayList<>();
                    for (int h = 0; h < 6; h++) {
                        FallingBlock fb = world.spawnFallingBlock(geyserBase.clone().add(0, h * 0.2, 0), geyserMats[h % geyserMats.length].createBlockData());
                        fb.setDropItem(false);
                        fb.setHurtEntities(false);
                        fb.setVelocity(new Vector(0, 0.8 - h * 0.06, 0));
                        gBlocks.add(fb);
                    }
                    new BukkitRunnable() {
                        int t = 0;
                        @Override
                        public void run() {
                            if (t >= 12) { cancel(); return; }
                            world.spawnParticle(Particle.SOUL, geyserBase.clone().add(0, 1 + t * 0.4, 0), 6, 0.3, 0.3, 0.3, 0.06);
                            t++;
                        }
                    }.runTaskTimer(plugin, 0, 1);
                    world.playSound(geyserBase, Sound.PARTICLE_SOUL_ESCAPE, 0.7f, 0.6f);
                    if (player.isOnline() && !player.isDead() && player.getLocation().distance(geyserBase) < 2.5) {
                        player.damage(6, bossEntity);
                        player.setVelocity(player.getVelocity().setY(0.9));
                    }
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        for (FallingBlock fb : gBlocks) if (!fb.isDead()) fb.remove();
                    }, 50L);
                }, 15L);
            }, idx * 10L);
        }
    }

    private void shadowDive() {
        if (bossEntity == null || bossEntity.isDead()) return;
        Location bLoc = bossEntity.getLocation().clone();
        World world = bLoc.getWorld();
        world.playSound(bLoc, Sound.ENTITY_PHANTOM_SWOOP, 0.9f, 0.5f);
        world.playSound(bLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 0.3f);
        bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 40, 4, false, false));
        bossEntity.setVelocity(new Vector(0, 1.4, 0));
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t >= 35 || bossEntity == null || bossEntity.isDead()) { cancel(); return; }
                Location loc = bossEntity.getLocation();
                world.spawnParticle(Particle.SOUL, loc, 6, 0.3, 0.3, 0.3, 0.04);
                world.spawnParticle(Particle.DUST, loc, 4, 0.3, 0.3, 0.3, 0,
                    new Particle.DustOptions(Color.fromRGB(140, 0, 200), 1.5f));
                world.spawnParticle(Particle.END_ROD, loc, 2, 0.2, 0.2, 0.2, 0.06);
                t++;
            }
        }.runTaskTimer(plugin, 0, 1);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || bossEntity == null || bossEntity.isDead()) return;
            bossEntity.removePotionEffect(PotionEffectType.LEVITATION);
            if (player.isOnline()) {
                Vector dive = player.getLocation().toVector().subtract(bossEntity.getLocation().toVector()).normalize().multiply(1.6);
                dive.setY(-0.9);
                bossEntity.setVelocity(dive);
            }
            new BukkitRunnable() {
                int t = 0;
                @Override
                public void run() {
                    if (t >= 22 || bossEntity == null || bossEntity.isDead()) { cancel(); return; }
                    Location loc = bossEntity.getLocation();
                    world.spawnParticle(Particle.FLAME, loc, 10, 0.3, 0.3, 0.3, 0.05);
                    world.spawnParticle(Particle.END_ROD, loc, 5, 0.2, 0.2, 0.2, 0.1);
                    world.spawnParticle(Particle.DUST, loc, 6, 0.4, 0.4, 0.4, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 50, 50), 1.8f));
                    t++;
                }
            }.runTaskTimer(plugin, 0, 1);
        }, 40L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || bossEntity == null || bossEntity.isDead()) return;
            Location impactLoc = bossEntity.getLocation().clone();
            world.playSound(impactLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 0.6f);
            world.strikeLightningEffect(impactLoc);
            new BukkitRunnable() {
                double r = 0;
                @Override
                public void run() {
                    if (r > 12) { cancel(); return; }
                    for (double a = 0; a < Math.PI * 2; a += 0.18) {
                        world.spawnParticle(Particle.DUST, impactLoc.clone().add(Math.cos(a) * r, 0.3, Math.sin(a) * r),
                            1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(200, 0, 50), 2.2f));
                        world.spawnParticle(Particle.SOUL, impactLoc.clone().add(Math.cos(a) * r, 0.5, Math.sin(a) * r),
                            1, 0, 0.3, 0, 0.02);
                    }
                    r += 1.6;
                }
            }.runTaskTimer(plugin, 0, 1);
            if (player.isOnline() && !player.isDead() && player.getLocation().distance(impactLoc) < 8) {
                player.damage(13, bossEntity);
                Vector kb = player.getLocation().toVector().subtract(impactLoc.toVector()).normalize().multiply(1.3).setY(0.9);
                player.setVelocity(kb);
            }
        }, 68L);
    }

    private void shadowClones() {
        Location bLoc = bossEntity.getLocation().clone();
        World world = bLoc.getWorld();
        world.playSound(bLoc, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.8f, 0.7f);
        int cloneCount = phase >= 3 ? 4 : 2;
        for (int i = 0; i < cloneCount; i++) {
            double angle = (Math.PI * 2.0 / cloneCount) * i;
            Location spawnLoc = bLoc.clone().add(Math.cos(angle) * 3, 0, Math.sin(angle) * 3);
            world.spawnParticle(Particle.SOUL, spawnLoc.clone().add(0, 1, 0), 20, 0.4, 1, 0.4, 0.04);
            world.spawnParticle(Particle.DUST, spawnLoc.clone().add(0, 1, 0), 15, 0.5, 1, 0.5, 0,
                new Particle.DustOptions(Color.fromRGB(30, 0, 60), 1.5f));
            Zombie clone = world.spawn(spawnLoc, Zombie.class, z -> {
                z.setCustomName(SmallCaps.convert("§8§lFragmento Olvidado"));
                z.setCustomNameVisible(true);
                z.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).setBaseValue(40);
                z.setHealth(40);
                z.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(5);
                z.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.30);
                z.setBaby(false);
                z.setTarget(player);
                z.setRemoveWhenFarAway(false);
                ItemStack cloneHelmet = new ItemStack(Material.LEATHER_HELMET);
                LeatherArmorMeta cMeta = (LeatherArmorMeta) cloneHelmet.getItemMeta();
                cMeta.setColor(Color.fromRGB(20, 0, 30));
                cloneHelmet.setItemMeta(cMeta);
                z.getEquipment().setHelmet(cloneHelmet);
                z.getEquipment().setHelmetDropChance(0);
                z.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 99999, 1, false, false));
            });
            summonedMobs.add(clone);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!clone.isDead()) {
                    clone.getWorld().spawnParticle(Particle.SMOKE, clone.getLocation().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0.05);
                    clone.remove();
                }
            }, 300L);
        }
    }

    // ==================== PHASE 3 ABILITIES ====================

    private void starfall() {
        Location playerLoc = player.getLocation().clone();
        World world = playerLoc.getWorld();
        world.playSound(playerLoc, Sound.ENTITY_WITHER_SHOOT, 0.5f, 0.4f);
        int count = 14;
        for (int i = 0; i < count; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!active || !player.isOnline()) return;
                Location target = player.getLocation().clone().add((Math.random() - 0.5) * 14, 0, (Math.random() - 0.5) * 14);
                Location spawnLoc = target.clone().add((Math.random() - 0.5) * 3, 20, (Math.random() - 0.5) * 3);
                Material[] mats = {Material.END_STONE, Material.PURPUR_BLOCK, Material.BLACKSTONE, Material.AMETHYST_BLOCK, Material.OBSIDIAN};
                FallingBlock fb = world.spawnFallingBlock(spawnLoc, mats[(int)(Math.random() * mats.length)].createBlockData());
                fb.setDropItem(false);
                fb.setHurtEntities(true);
                BukkitRunnable trail = new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (fb.isDead() || !active) { cancel(); return; }
                        world.spawnParticle(Particle.DUST, fb.getLocation(), 4, 0.2, 0.2, 0.2, 0,
                            new Particle.DustOptions(Color.fromRGB(160, 50, 220), 1.4f));
                        world.spawnParticle(Particle.SOUL, fb.getLocation(), 2, 0.1, 0.1, 0.1, 0.02);
                        world.spawnParticle(Particle.END_ROD, fb.getLocation(), 1, 0.1, 0.1, 0.1, 0.05);
                    }
                };
                trail.runTaskTimer(plugin, 0, 1);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    trail.cancel();
                    if (!fb.isDead()) fb.remove();
                    world.spawnParticle(Particle.EXPLOSION, target.clone().add(0, 1, 0), 4, 0.3, 0.3, 0.3, 0.1);
                    world.spawnParticle(Particle.SOUL, target.clone().add(0, 0.5, 0), 20, 1.2, 0.5, 1.2, 0.05);
                    world.spawnParticle(Particle.DUST, target.clone().add(0, 0.5, 0), 15, 1, 0.5, 1, 0,
                        new Particle.DustOptions(Color.fromRGB(180, 0, 255), 2.0f));
                    world.playSound(target, Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.3f);
                    if (player.isOnline() && !player.isDead() && player.getLocation().distance(target) < 3.5) {
                        player.damage(8, bossEntity);
                    }
                }, 55L);
            }, i * 10L);
        }
    }

    private void floatingDebris() {
        Location bLoc = bossEntity.getLocation().clone();
        World world = bLoc.getWorld();
        world.playSound(bLoc, Sound.ENTITY_WARDEN_ROAR, 0.7f, 0.6f);
        world.strikeLightningEffect(bLoc);
        Material[] mats = {Material.OBSIDIAN, Material.AMETHYST_BLOCK, Material.BLACKSTONE, Material.PURPUR_BLOCK, Material.CRYING_OBSIDIAN};
        for (int i = 0; i < 18; i++) {
            Material mat = mats[i % mats.length];
            FallingBlock fb = world.spawnFallingBlock(bossEntity.getLocation().add(0, 1.5, 0), mat.createBlockData());
            fb.setDropItem(false);
            fb.setHurtEntities(true);
            double angle = Math.random() * Math.PI * 2;
            double speed = 0.5 + Math.random() * 0.9;
            fb.setVelocity(new Vector(Math.cos(angle) * speed, 0.7 + Math.random() * 0.8, Math.sin(angle) * speed));
            new BukkitRunnable() {
                int t = 0;
                @Override
                public void run() {
                    if (t >= 45 || fb.isDead() || !active) {
                        if (!fb.isDead()) fb.remove();
                        cancel(); return;
                    }
                    Color c = Color.fromRGB(Math.min(255, 80 + t * 3), 0, Math.max(0, 200 - t * 2));
                    world.spawnParticle(Particle.DUST, fb.getLocation(), 2, 0.1, 0.1, 0.1, 0, new Particle.DustOptions(c, 1.1f));
                    world.spawnParticle(Particle.SOUL, fb.getLocation(), 1, 0.1, 0.1, 0.1, 0.02);
                    t++;
                }
            }.runTaskTimer(plugin, 0, 1);
        }
        for (double a = 0; a < Math.PI * 2; a += 0.2) {
            Location ring = bLoc.clone().add(Math.cos(a) * 2, 1, Math.sin(a) * 2);
            world.spawnParticle(Particle.END_ROD, ring, 3, 0.1, 0.3, 0.1, 0.15);
        }
    }

    private void memoryCollapse() {
        if (bossEntity == null || bossEntity.isDead()) return;
        Location bLoc = bossEntity.getLocation().clone();
        World world = bLoc.getWorld();
        new BukkitRunnable() {
            double angle = 0;
            int t = 0;
            @Override
            public void run() {
                if (t >= 80 || !active || bossEntity == null || bossEntity.isDead()) { cancel(); return; }
                for (int arm = 0; arm < 6; arm++) {
                    double a = angle + (Math.PI / 3) * arm;
                    double r = 2.5 + Math.sin(angle * 3) * 0.5;
                    Location pt = bossEntity.getLocation().add(Math.cos(a) * r, 1.5, Math.sin(a) * r);
                    world.spawnParticle(Particle.END_ROD, pt, 2, 0.1, 0.2, 0.1, 0.06);
                    world.spawnParticle(Particle.DUST, pt, 2, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(255, Math.min(255, 50 + t * 2), 50), 1.8f));
                }
                if (t % 20 == 0) {
                    world.playSound(bossEntity.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.5f, 0.5f + t * 0.005f);
                }
                angle += 0.22;
                t++;
            }
        }.runTaskTimer(plugin, 0, 1);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || bossEntity == null || bossEntity.isDead()) return;
            Location center = bossEntity.getLocation().clone();
            world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 0.35f);
            world.playSound(center, Sound.ENTITY_WARDEN_ROAR, 0.9f, 0.3f);
            world.strikeLightningEffect(center);
            Material[] mats = {Material.OBSIDIAN, Material.AMETHYST_BLOCK, Material.PURPUR_BLOCK, Material.END_STONE, Material.CRYING_OBSIDIAN};
            for (int i = 0; i < 24; i++) {
                Material mat = mats[i % mats.length];
                FallingBlock fb = world.spawnFallingBlock(center.clone().add(0, 2, 0), mat.createBlockData());
                fb.setDropItem(false);
                fb.setHurtEntities(true);
                double a = (Math.PI * 2 / 24.0) * i + Math.random() * 0.2;
                double pitch = 0.3 + Math.random() * 0.7;
                fb.setVelocity(new Vector(Math.cos(a) * pitch, 0.9 + Math.random() * 0.7, Math.sin(a) * pitch));
                Bukkit.getScheduler().runTaskLater(plugin, () -> { if (!fb.isDead()) fb.remove(); }, 80L);
            }
            for (int e = 0; e < 8; e++) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!active || !player.isOnline()) return;
                    Location expLoc = center.clone().add((Math.random() - 0.5) * 18, 0.5, (Math.random() - 0.5) * 18);
                    world.spawnParticle(Particle.EXPLOSION, expLoc, 1, 0.2, 0.2, 0.2, 0.1);
                    world.spawnParticle(Particle.FLAME, expLoc, 5, 1.2, 0.5, 1.2, 0.07);
                    world.spawnParticle(Particle.DUST, expLoc, 4, 1.5, 1, 1.5, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 50, 0), 2.0f));
                    world.playSound(expLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 0.75f + (float) Math.random() * 0.5f);
                    if (player.isOnline() && !player.isDead() && player.getLocation().distance(expLoc) < 4.5) {
                        player.damage(9, bossEntity);
                    }
                }, e * 8L);
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline() || player.isDead()) return;
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 80, 0));
                for (int i = 0; i < 4; i++) {
                    double a = (Math.PI / 2) * i;
                    for (double r = 0; r < 20; r += 2.0) {
                        world.spawnParticle(Particle.SOUL, center.clone().add(Math.cos(a) * r, 1, Math.sin(a) * r), 1, 0, 0.3, 0, 0.03);
                    }
                }
            }, 20L);
        }, 80L);
    }

    private void witherNova() {
        Location bLoc = bossEntity.getLocation().clone();
        World world = bLoc.getWorld();
        world.playSound(bLoc, Sound.ENTITY_WITHER_SHOOT, 0.9f, 0.5f);
        world.playSound(bLoc, Sound.ENTITY_WITHER_AMBIENT, 0.6f, 0.4f);
        new BukkitRunnable() {
            double radius = 0.5;
            @Override
            public void run() {
                if (radius > 16 || !active || bossEntity == null || bossEntity.isDead()) { cancel(); return; }
                for (int arm = 0; arm < 4; arm++) {
                    double a = (Math.PI * 2 / 4) * arm;
                    Location armLoc = bLoc.clone().add(Math.cos(a) * radius, 1, Math.sin(a) * radius);
                    world.spawnParticle(Particle.DUST, armLoc, 2, 0.1, 0.2, 0.1, 0,
                        new Particle.DustOptions(Color.fromRGB(20, 20, 20), 2.0f));
                }
                if (player.isOnline() && !player.isDead()) {
                    double playerDist = player.getLocation().distance(bLoc);
                    if (radius >= playerDist - 1.2 && radius <= playerDist + 1.2) {
                        player.damage(8, bossEntity);
                        player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, phase == 3 ? 2 : 1));
                    }
                }
                radius += 1.3;
            }
        }.runTaskTimer(plugin, 0, 2);
    }

    private void voidRift() {
        if (!player.isOnline()) return;
        Location riftLoc = player.getLocation().clone().add((Math.random() - 0.5) * 8, 0, (Math.random() - 0.5) * 8);
        World world = riftLoc.getWorld();
        world.playSound(riftLoc, Sound.BLOCK_PORTAL_TRIGGER, 0.8f, 0.5f);
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 100 || !active || bossEntity == null || bossEntity.isDead()) { cancel(); return; }
                for (double a = 0; a < Math.PI * 2; a += 0.5) {
                    double rx = Math.cos(a) * 1.5;
                    double ry = Math.sin(a) * 2.5;
                    Location pLoc = riftLoc.clone().add(rx, ry + 1.5, 0);
                    world.spawnParticle(Particle.PORTAL, pLoc, 1, 0.1, 0.1, 0.1, 0.5);
                    world.spawnParticle(Particle.DUST, pLoc, 1, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(100, 0, 180), 1.2f));
                }
                if (ticks % 4 == 0 && player.isOnline() && !player.isDead()) {
                    double dist = player.getLocation().distance(riftLoc);
                    if (dist < 10) {
                        Vector pull = riftLoc.toVector().subtract(player.getLocation().toVector()).normalize().multiply(0.15);
                        player.setVelocity(player.getVelocity().add(pull));
                    }
                    if (dist < 2) {
                        player.damage(5, bossEntity);
                        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 0));
                    }
                }
                if (ticks % 25 == 0) {
                    world.playSound(riftLoc, Sound.BLOCK_PORTAL_TRAVEL, 0.25f, 0.5f);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }


    // ==================== FATAL DAMAGE INTERCEPT ====================

    /**
     * Llamado desde el listener cuando el daño mataría al boss.
     * Cancela la muerte, pone HP a 1 y dispara la escena final.
     */
    public void onBossFatalDamage() {
        if (finalSceneTriggered) return;
        finalSceneTriggered = true;
        finalSceneActive = true;

        // Forzar HP a 1
        bossEntity.setHealth(1);
        bossEntity.setInvulnerable(true);
        bossEntity.setAI(false);
        player.setInvulnerable(true);

        // Eliminar todos los mobs invocados
        cleanupSummons();

        triggerFinalConfrontation();
    }

    public boolean isFinalSceneTriggered() { return finalSceneTriggered; }
    public boolean isFinalSceneActive() { return finalSceneActive; }

    // ==================== ESCENA FINAL: CARA A CARA ====================

    private void triggerFinalConfrontation() {
        final Location bLoc = bossEntity.getLocation().clone();
        final World world = bLoc.getWorld();

        // --- Posicionar cara a cara ---
        Location ecoLoc = bLoc.clone();
        Vector ecoToPlayer = player.getLocation().toVector().subtract(ecoLoc.toVector()).normalize();
        Location erranteLoc = ecoLoc.clone().add(ecoToPlayer.clone().multiply(3));
        erranteLoc.setY(ecoLoc.getY());
        ecoLoc.setDirection(erranteLoc.toVector().subtract(ecoLoc.toVector()));
        erranteLoc.setDirection(ecoLoc.toVector().subtract(erranteLoc.toVector()));
        bossEntity.teleport(ecoLoc);
        if (npc != null && npc.getVillager() != null && !npc.getVillager().isDead()) {
            npc.getVillager().teleport(erranteLoc);
            npc.getVillager().setAI(false);
        }

        // BossBar
        if (bossBar != null) {
            bossBar.setTitle(SmallCaps.convert("§5§l✦ La Última Memoria — §d§l1 HP §5§l✦"));
            bossBar.setColor(BarColor.PURPLE);
            bossBar.setProgress(0.002);
        }

        // Efecto épico inicial
        world.spawnParticle(Particle.SOUL, bLoc.clone().add(0, 1.5, 0), 100, 2.5, 3, 2.5, 0.06);
        world.spawnParticle(Particle.END_ROD, bLoc.clone().add(0, 2, 0), 70, 2, 3, 2, 0.12);
        world.spawnParticle(Particle.DUST, bLoc.clone().add(0, 1, 0), 50, 2, 2, 2, 0,
                new Particle.DustOptions(Color.fromRGB(200, 100, 255), 3.0f));
        world.playSound(bLoc, Sound.ENTITY_WARDEN_DEATH, 0.7f, 0.3f);
        world.playSound(bLoc, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 0.2f);
        world.strikeLightningEffect(bLoc);

        // t=20 — Título
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendTitle( SmallCaps.convert("§5§l✦ LA MEMORIA SE DETIENE ✦"), SmallCaps.convert("§7Su espada cae al suelo..."), 10, 80, 30);
        }, 20L);

        // Hilo continuo de partículas: línea de almas entre boss y NPC
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!active || !finalSceneActive || ticks > 2900 || bossEntity == null || bossEntity.isDead()) { cancel(); return; }
                Location eLoc = bossEntity.getLocation().add(0, 1.2, 0);
                Location nLoc = (npc != null && npc.getVillager() != null && !npc.getVillager().isDead())
                        ? npc.getVillager().getLocation().add(0, 1.2, 0) : eLoc.clone().add(2, 0, 0);
                Vector dir = nLoc.toVector().subtract(eLoc.toVector());
                double len = dir.length();
                dir.normalize();
                for (double d = 0; d < len; d += 0.4) {
                    world.spawnParticle(Particle.DUST, eLoc.clone().add(dir.clone().multiply(d)), 1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(180, 80, 220), 0.8f));
                }
                if (ticks % 12 == 0) {
                    world.spawnParticle(Particle.SOUL, eLoc.clone().add(0, 0.5, 0), 2, 0.2, 0.3, 0.2, 0.02);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 30L, 2L);

        // === DIÁLOGO LÍNEA POR LÍNEA ===

        // t=50 — Separador inicial
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("§5§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
            player.sendMessage("");
            world.playSound(bLoc, Sound.ENTITY_IRON_GOLEM_DAMAGE, 0.6f, 0.3f);
        }, 50L);

        // t=100
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage(SmallCaps.convert("  §5§lLa Última Memoria §7deja caer su espada."));
            world.spawnParticle(Particle.SMOKE, bLoc.clone().add(0, 0.5, 0), 20, 0.3, 0.1, 0.3, 0.02);
        }, 100L);

        // t=150
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage(SmallCaps.convert("  §7Su cuerpo tiembla."));
        }, 150L);

        // t=200
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage(SmallCaps.convert("  §7Sus ojos, vacíos, buscan algo que perdieron hace mucho tiempo."));
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.6f, 0.4f);
        }, 200L);

        // t=270 — Primera línea del eco
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("  §d§o\"...¿Cuánto tiempo ha pasado?\""));
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.8f, 0.5f);
        }, 270L);

        // t=330
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage(SmallCaps.convert("  §d§o\"Ya no recuerdo mi nombre.\""));
            world.spawnParticle(Particle.DRIPPING_WATER, bLoc.clone().add(0, 1.8, 0), 4, 0.1, 0, 0.1, 0);
        }, 330L);

        // t=390
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage(SmallCaps.convert("  §d§o\"Ya no recuerdo la luz del sol.\""));
            world.spawnParticle(Particle.DRIPPING_WATER, bLoc.clone().add(0, 1.8, 0), 4, 0.1, 0, 0.1, 0);
        }, 390L);

        // t=450
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage(SmallCaps.convert("  §d§o\"Ya no recuerdo... a mis amigos.\""));
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.5f, 0.3f);
        }, 450L);

        // t=530
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("  §d§o\"Solo recuerdo... una promesa.\""));
        }, 530L);

        // t=590
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage(SmallCaps.convert("  §d§o\"Que protegería a los que amaba.\""));
        }, 590L);

        // t=650
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage(SmallCaps.convert("  §d§o\"Que no me rendiría jamás.\""));
            world.spawnParticle(Particle.SOUL, bLoc.clone().add(0, 1, 0), 20, 0.4, 0.8, 0.4, 0.03);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.6f, 0.4f);
        }, 650L);

        // t=730 — El eco mira al Errante
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("  §7La Memoria levanta la mirada hacia el Errante."));
            world.spawnParticle(Particle.DUST, bLoc.clone().add(0, 2, 0), 25, 0.5, 0.8, 0.5, 0,
                    new Particle.DustOptions(Color.fromRGB(200, 100, 255), 2.0f));
            player.playSound(player.getLocation(), Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 0.5f, 0.3f);
        }, 730L);

        // t=790
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage(SmallCaps.convert("  §7Dos versiones del mismo ser, cara a cara."));
        }, 790L);

        // t=850
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage(SmallCaps.convert("  §7El silencio pesa más que cualquier batalla."));
        }, 850L);

        // t=930 — El eco habla al Errante
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("  §5§lLA MEMORIA §7susurra:"));
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.3f);
        }, 930L);

        // t=990
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("  §d§o\"¿Me reconoces...?\""));
        }, 990L);

        // t=1050
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage(SmallCaps.convert("  §d§o\"Soy lo que fuiste antes de que el Vacío te encontrara.\""));
        }, 1050L);

        // t=1110
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage(SmallCaps.convert("  §d§o\"Tenía tu risa.\""));
        }, 1110L);

        // t=1160
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage(SmallCaps.convert("  §d§o\"Tu valor. Tu esperanza.\""));
            world.spawnParticle(Particle.SOUL, bLoc.clone().add(0, 1, 0), 25, 0.4, 0.8, 0.4, 0.03);
        }, 1160L);

        // t=1220
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage(SmallCaps.convert("  §d§o\"El Vacío me lo quitó todo... y me dejó aquí.\""));
            world.spawnParticle(Particle.DRIPPING_WATER, bLoc.clone().add(0, 1.8, 0), 8, 0.1, 0, 0.1, 0);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.7f, 0.3f);
        }, 1220L);

        // t=1290
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage(SmallCaps.convert("  §d§o\"Repitiendo esta pelea.\""));
        }, 1290L);

        // t=1340
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage(SmallCaps.convert("  §d§o\"Eternamente.\""));
        }, 1340L);

        // t=1390
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage(SmallCaps.convert("  §d§o\"Solo.\""));
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.4f);
        }, 1390L);

        // t=1470 — El Errante responde
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("  §5§lEl Errante §7da un paso hacia la Memoria."));
            player.playSound(player.getLocation(), Sound.ENTITY_ALLAY_HURT, 0.3f, 0.5f);
        }, 1470L);

        // t=1530
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage(SmallCaps.convert("  §7Su mano tiembla."));
            Location nLoc = (npc != null && npc.getVillager() != null) ? npc.getVillager().getLocation() : bLoc;
            world.spawnParticle(Particle.DRIPPING_WATER, nLoc.clone().add(0, 1.8, 0), 4, 0.1, 0, 0.1, 0);
        }, 1530L);

        // t=1600
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage("");
            if (npc != null) npc.say("§d\"...Te recuerdo.\"");
            player.playSound(player.getLocation(), Sound.ENTITY_ALLAY_HURT, 0.3f, 0.5f);
        }, 1600L);

        // t=1660
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            if (npc != null) npc.say("§d\"Eras yo.\"");
        }, 1660L);

        // t=1720
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            if (npc != null) npc.say("§d\"La mejor parte de mí.\"");
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.5f, 0.5f);
        }, 1720L);

        // t=1790
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            if (npc != null) npc.say("§d\"La parte que no se rindió cuando todo se volvió oscuridad.\"");
        }, 1790L);

        // t=1860
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            if (npc != null) npc.say("§d\"Y te fallé.\"");
            Location nLoc = (npc != null && npc.getVillager() != null) ? npc.getVillager().getLocation() : bLoc;
            world.spawnParticle(Particle.DRIPPING_WATER, nLoc.clone().add(0, 1.8, 0), 6, 0.1, 0, 0.1, 0);
        }, 1860L);

        // t=1920
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            if (npc != null) npc.say("§d\"Te dejé aquí, atrapada en esta memoria.\"");
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.5f);
        }, 1920L);

        // t=2010 — La Memoria acepta su destino
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("  §7La Memoria sonríe por primera vez."));
            world.spawnParticle(Particle.END_ROD, bLoc.clone().add(0, 2, 0), 25, 0.4, 0.8, 0.4, 0.1);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 0.8f);
        }, 2010L);

        // t=2070
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage(SmallCaps.convert("  §7Una sonrisa triste."));
        }, 2070L);

        // t=2120
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage(SmallCaps.convert("  §7Pero... real."));
        }, 2120L);

        // t=2190
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("  §d§o\"No me fallaste.\""));
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.5f);
        }, 2190L);

        // t=2250
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage(SmallCaps.convert("  §d§o\"Me diste algo que el Vacío nunca pudo destruir.\""));
        }, 2250L);

        // t=2310
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage(SmallCaps.convert("  §d§o\"Un recuerdo.\""));
        }, 2310L);

        // t=2360
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage(SmallCaps.convert("  §d§o\"De lo que era ser... alguien.\""));
            world.spawnParticle(Particle.SOUL, bLoc.clone().add(0, 1, 0), 50, 0.5, 2, 0.5, 0.03);
        }, 2360L);

        // t=2430
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("  §d§o\"Pero ya es hora de descansar.\""));
            player.playSound(player.getLocation(), Sound.ENTITY_ALLAY_DEATH, 0.5f, 0.3f);
        }, 2430L);

        // t=2490
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage(SmallCaps.convert("  §d§o\"Déjame ir.\""));
        }, 2490L);

        // t=2540
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage(SmallCaps.convert("  §d§o\"Por favor.\""));
            world.spawnParticle(Particle.DRIPPING_WATER, bLoc.clone().add(0, 1.8, 0), 10, 0.1, 0, 0.1, 0);
        }, 2540L);

        // t=2600
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage(SmallCaps.convert("  §d§o\"Libérame de este bucle eterno.\""));
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 0.3f);
        }, 2600L);

        // t=2680 — El Errante se despide
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage("");
            if (npc != null) npc.say("§d\"...Adiós.\"");
            player.playSound(player.getLocation(), Sound.ENTITY_ALLAY_HURT, 0.4f, 0.4f);
        }, 2680L);

        // t=2740
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            if (npc != null) npc.say("§d\"Viejo amigo.\"");
        }, 2740L);

        // t=2800
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            if (npc != null) npc.say("§d\"Fuiste lo mejor de mí.\"");
            world.spawnParticle(Particle.END_ROD, bLoc.clone().add(0, 1, 0), 25, 0.8, 1.5, 0.8, 0.06);
        }, 2800L);

        // t=2860
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            if (npc != null) npc.say("§d\"Y ahora... serás libre.\"");
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 0.6f);
        }, 2860L);

        // t=2940 — La Memoria mira al jugador
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("  §7La Última Memoria te mira directamente."));
            world.spawnParticle(Particle.DUST, bLoc.clone().add(0, 2, 0), 30, 0.6, 1, 0.6, 0,
                    new Particle.DustOptions(Color.fromRGB(200, 100, 255), 2.2f));
        }, 2940L);

        // t=3000
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage(SmallCaps.convert("  §7Sus ojos ya no están vacíos."));
        }, 3000L);

        // t=3050
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage(SmallCaps.convert("  §7Hay... paz."));
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.3f);
        }, 3050L);

        // t=3120
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("  §d§o\"Cazador...\""));
        }, 3120L);

        // t=3180
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage(SmallCaps.convert("  §d§o\"Gracias por llegar hasta aquí.\""));
        }, 3180L);

        // t=3240
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage(SmallCaps.convert("  §d§o\"Ahora, da el último golpe.\""));
        }, 3240L);

        // t=3300
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage(SmallCaps.convert("  §d§o\"No dudes.\""));
        }, 3300L);

        // t=3360
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage(SmallCaps.convert("  §d§o\"Cada segundo que paso aquí...\""));
        }, 3360L);

        // t=3420
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;
            player.sendMessage(SmallCaps.convert("  §d§o\"...duele.\""));
            player.playSound(player.getLocation(), Sound.ENTITY_ALLAY_DEATH, 0.6f, 0.3f);
            world.spawnParticle(Particle.SOUL, bLoc.clone().add(0, 1.5, 0), 60, 0.6, 2, 0.6, 0.04);
        }, 3420L);

        // t=3510 — Activar golpe final
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline()) return;

            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("§5§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("  §c§l  ✦ LA MEMORIA BAJA SU GUARDIA ✦"));
            player.sendMessage(SmallCaps.convert("  §7  Su armadura se agrieta. La oscuridad se disipa."));
            player.sendMessage(SmallCaps.convert("  §7  Esperando su liberación."));
            player.sendMessage(SmallCaps.convert("  §e§l  ¡Dale el golpe final!"));
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("§5§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
            player.sendMessage("");

            Location currentBLoc = bossEntity.getLocation();
            world.spawnParticle(Particle.DUST, currentBLoc.clone().add(0, 1, 0), 60, 1, 1.5, 1, 0,
                    new Particle.DustOptions(Color.fromRGB(255, 50, 50), 2.2f));
            world.spawnParticle(Particle.SMOKE, currentBLoc.clone().add(0, 1, 0), 35, 0.5, 1, 0.5, 0.05);
            world.spawnParticle(Particle.END_ROD, currentBLoc.clone().add(0, 2, 0), 50, 1, 2, 1, 0.12);
            world.strikeLightningEffect(currentBLoc);
            player.playSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_DAMAGE, 0.8f, 0.5f);
            player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 0.8f);
            player.sendTitle( SmallCaps.convert("§c§l✦ ¡GOLPE FINAL! ✦"), SmallCaps.convert("§7Libera a la Memoria de su tormento..."), 5, 120, 25);

            finalSceneActive = false;
            bossEntity.setInvulnerable(false);
            player.setInvulnerable(false);
            bossEntity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ARMOR).setBaseValue(0);
            bossEntity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(0);
            bossEntity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0);
            bossEntity.setCustomName(SmallCaps.convert("§5§l✦ La Última Memoria §d§l[LIBERACIÓN] §5§l✦"));

            if (npc != null && npc.getVillager() != null && !npc.getVillager().isDead()) {
                npc.getVillager().setAI(true);
            }
            if (bossBar != null) {
                bossBar.setTitle(SmallCaps.convert("§d§l✦ Libera a la Última Memoria ✦"));
                bossBar.setColor(BarColor.PINK);
            }
        }, 3510L);
    }

    // ==================== PHASE TRANSITIONS ====================

    private void enterPhase2() {
        phase = 2;
        player.sendTitle( SmallCaps.convert("§c§l✦ FASE 2 ✦"), SmallCaps.convert("§7\"¡El dolor me hace más fuerte!\""), 10, 60, 20);
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 1.0f, 0.3f);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 0.5f);

        if (bossBar != null) {
            bossBar.setTitle(SmallCaps.convert("§c§l✦ La Última Memoria del Errante ✦ §7[FASE 2]"));
            bossBar.setColor(BarColor.RED);
        }

        // Boost stats (menos daño, más habilidades)
        bossEntity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(12);
        bossEntity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.32);

        // Shockwave effect
        Location loc = bossEntity.getLocation();
        loc.getWorld().spawnParticle(Particle.EXPLOSION, loc.clone().add(0, 1, 0), 10, 2, 2, 2, 0.5);
        loc.getWorld().spawnParticle(Particle.DUST, loc.clone().add(0, 1, 0), 60, 3, 3, 3, 0,
                new Particle.DustOptions(Color.fromRGB(200, 0, 50), 3.0f));

        if (npc != null) npc.say("§c\"¡AAAARGH! ¡Recuerdo... recuerdo TODO! ¡El dolor!\"");
    }

    private void enterPhase3() {
        phase = 3;
        player.sendTitle( SmallCaps.convert("§4§l✦ FASE FINAL ✦"), SmallCaps.convert("§c\"¡LIBÉRAME DE ESTE TORMENTO!\""), 10, 80, 20);
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.8f, 0.3f);
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_ROAR, 0.6f, 0.5f);

        if (bossBar != null) {
            bossBar.setTitle(SmallCaps.convert("§4§l✦ La Última Memoria — FASE FINAL ✦"));
            bossBar.setColor(BarColor.WHITE);
        }

        // Max stats (menos daño, más velocidad y habilidades)
        bossEntity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(14);
        bossEntity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.34);
        bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 99999, 0, false, false));

        // Mega shockwave
        Location loc = bossEntity.getLocation();
        loc.getWorld().spawnParticle(Particle.EXPLOSION, loc.clone().add(0, 1, 0), 20, 3, 3, 3, 1);
        loc.getWorld().spawnParticle(Particle.SOUL, loc.clone().add(0, 2, 0), 80, 3, 3, 3, 0.1);
        loc.getWorld().strikeLightningEffect(loc);

        if (npc != null) npc.say("§4\"¡ACABA CONMIGO! ¡POR FAVOR! ¡ES LO ÚNICO QUE PIDO!\"");
    }

    // ==================== EVENTS ====================

    public void onBossDamaged() {
        lastDamageTime = System.currentTimeMillis();
    }

    public void onBossDeath() {
        onBossDefeated();
    }

    private void onBossDefeated() {
        active = false;
        if (mainLoop != null) mainLoop.cancel();
        cleanupSummons();

        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }

        // Dramatic death sequence
        Location deathLoc = bossEntity != null ? bossEntity.getLocation() : player.getLocation();
        World world = deathLoc.getWorld();

        world.spawnParticle(Particle.SOUL, deathLoc.clone().add(0, 1, 0), 100, 2, 3, 2, 0.1);
        world.spawnParticle(Particle.DUST, deathLoc.clone().add(0, 2, 0), 80, 2, 2, 2, 0,
                new Particle.DustOptions(Color.fromRGB(120, 50, 180), 3.0f));
        world.spawnParticle(Particle.END_ROD, deathLoc.clone().add(0, 2, 0), 60, 1, 3, 1, 0.5);
        world.playSound(deathLoc, Sound.ENTITY_WARDEN_DEATH, 0.8f, 0.5f);
        world.playSound(deathLoc, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.5f);

        // Asegurar que el NPC del Errante esté vivo y cerca para el diálogo post-boss
        // Si fue eliminado durante la pelea (chunk unload, plugin de limpieza, etc.), re-spawnearlo
        Location npcSpawnLoc = deathLoc.clone().add(2, 0, 2);
        if (npc != null) {
            npc.ensureAlive(npcSpawnLoc);
        }

        // Sad post-boss dialogue
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            player.sendTitle( SmallCaps.convert("§5§l✦ LA MEMORIA SE DESVANECE ✦"), SmallCaps.convert("§7\"Gracias... por liberarme...\""), 10, 100, 30);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 0.3f);
        }, 40L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (npc != null) npc.say("§d\"...Ese era yo. Lo que el Vacío me convirtió.\"");
        }, 100L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (npc != null) npc.say("§d\"Gracias por liberarme de ese tormento eterno.\"");
        }, 180L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (npc != null) npc.say("§d\"Antes de irme... quiero darte algo. Mi armadura.\"");
        }, 260L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (npc != null) npc.say("§d\"Es lo único que me queda de quien fui. Cuídala.\"");
        }, 340L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("§5§l✦ El Errante §7susurra por última vez:"));
            player.sendMessage(SmallCaps.convert("§d§o\"No me olvides...\""));
            player.sendMessage("");
            player.playSound(player.getLocation(), Sound.ENTITY_ALLAY_DEATH, 0.8f, 0.5f);
        }, 420L);

        // Complete quest after dialogue
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            manager.onQuest4BossDefeated(player);
        }, 500L);
    }

    public void onPlayerDeath() {
        // Don't cleanup - player respawns near boss
        player.sendMessage(SmallCaps.convert("§c§l✦ §cHas caído ante la Última Memoria. ¡Vuelves a la lucha!"));
        
        // Marcar respawn cerca del boss
        if (bossEntity != null && !bossEntity.isDead()) {
            Location respawnLoc = bossEntity.getLocation().clone().add(0, 0.5, 0);
            manager.markDirectRespawn(player.getUniqueId(), respawnLoc);
        } else if (arenaCenter != null) {
            // Si el boss murió, respawnear en el centro de la arena
            manager.markDirectRespawn(player.getUniqueId(), arenaCenter.clone().add(0, 0.5, 0));
        }
    }

    // ==================== CLEANUP ====================

    private void cleanupSummons() {
        for (Entity e : summonedMobs) {
            if (e != null && !e.isDead()) e.remove();
        }
        summonedMobs.clear();
    }

    public void cleanup() {
        active = false;
        if (mainLoop != null) mainLoop.cancel();
        cleanupSummons();
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
        if (bossEntity != null && !bossEntity.isDead()) {
            bossEntity.remove();
        }
    }

    // ==================== GETTERS ====================

    public boolean isActive() { return active; }

    public boolean isBossEntity(UUID entityId) {
        return bossEntity != null && bossEntity.getUniqueId().equals(entityId);
    }

    public boolean isBossEntity(Entity entity) {
        return bossEntity != null && bossEntity.equals(entity);
    }

    public boolean isSummonedMob(UUID entityId) {
        return summonedMobs.stream().anyMatch(e -> e != null && !e.isDead() && e.getUniqueId().equals(entityId));
    }

    public void collectProtectedEntities(java.util.List<org.bukkit.entity.Entity> out) {
        if (bossEntity != null && !bossEntity.isDead()) out.add(bossEntity);
        for (org.bukkit.entity.Entity e : summonedMobs) { if (e != null && !e.isDead()) out.add(e); }
    }

    public Location getBossLocation() {
        return bossEntity != null ? bossEntity.getLocation() : null;
    }
}
