package com.moonlight.coreprotect.shadowhunter;

import com.moonlight.coreprotect.CoreProtectPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Boss psicológico de la Misión 3: "El Arquitecto".
 * No pega fuerte pero manipula al jugador:
 * - Roba dinero cada 30s
 * - Crea clones falsos del jugador
 * - Mueve ítems del inventario
 * - Pone náusea/confusión
 * - Frases perturbadoras via titles
 * - Devuelve todo x2 al morir (25% vida)
 * - Se cura si el jugador no ataca por 10s
 */
public class VoidBoss {

    private final CoreProtectPlugin plugin;
    private final ShadowHunterManager manager;
    private final Player player;
    private final ShadowHunterNPC npc;
    private final VoidArena arena;

    private Zombie bossEntity;
    private boolean active = false;
    private BukkitRunnable mainLoop;
    private BukkitRunnable domainTask;
    private org.bukkit.boss.BossBar bossBar;
    private final List<Entity> spawnedEntities = new ArrayList<>();
    private int tickCount = 0;

    // Dinero robado
    private double totalMoneyStolen = 0;
    private long lastDamageTime;

    // Frases perturbadoras
    private static final String[] TAUNT_TITLES = {
            "§4¿De verdad crees que eres el héroe?",
            "§0Tu dinero se desvanece como tu determinación",
            "§4El vacío lo consume todo... especialmente la esperanza",
            "§0¿Cuánto más podrás aguantar antes de rendirte?",
            "§4Tu inventario es un reflejo de tu mente... caótico",
            "§0Nadie vendrá a salvarte aquí arriba",
            "§4¿Has pensado que tal vez la misión no vale la pena?",
            "§0Los que confiaron antes que tú... ya no existen",
            "§4Cada moneda robada es un trozo de tu voluntad",
            "§0Tus aliados te olvidarán... como el vacío te olvidará",
    };

    private static final String[] TAUNT_SUBTITLES = {
            "§7El Arquitecto susurra en tu mente...",
            "§7Su voz resuena en las paredes de hormigón...",
            "§7Una risa oscura llena la arena...",
            "§7Sientes un escalofrío recorrer tu espalda...",
            "§7El vacío te observa...",
    };

    private int tauntIndex = 0;

    public VoidBoss(CoreProtectPlugin plugin, ShadowHunterManager manager, Player player, ShadowHunterNPC npc) {
        this.plugin = plugin;
        this.manager = manager;
        this.player = player;
        this.npc = npc;
        this.arena = new VoidArena(plugin, player);
    }

    /**
     * Inicia el boss: construye arena, spawnea boss, inicia loop.
     */
    public void start() {
        active = true;
        lastDamageTime = System.currentTimeMillis();

        // Construir arena y luego spawnear boss
        // Construir arena y luego iniciar pruebas
        arena.build(() -> {
            if (!active)
                return;

            startDomainTrials(() -> {
                if (!active || !player.isOnline())
                    return;

                spawnBoss();
                startMainLoop();

                player.sendMessage("");
                player.sendMessage(SmallCaps.convert("§0§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
                player.sendMessage(SmallCaps.convert("  §0§l✦ EL ARQUITECTO DEL VACÍO ✦"));
                player.sendMessage("");
                player.sendMessage(SmallCaps.convert("  §7No te matará con fuerza bruta..."));
                player.sendMessage(SmallCaps.convert("  §7Jugará con tu §cdinero§7, tu §dinventario§7,"));
                player.sendMessage(SmallCaps.convert("  §7y tu §4mente§7."));
                player.sendMessage("");
                player.sendMessage(SmallCaps.convert("  §c⚠ §7¡No dejes de atacar o se curará!"));
                player.sendMessage(SmallCaps.convert("§0§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
                player.sendMessage("");
            });
        });
    }

    private void startDomainTrials(Runnable onComplete) {
        player.sendTitle( SmallCaps.convert("§0§l✦ SUPERVIVENCIA ✦"), SmallCaps.convert("§7El dominio te pondrá a prueba..."), 10, 60, 20);
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f);

        domainTask = new BukkitRunnable() {
            int ticks = 0;
            int phase = 1;

            @Override
            public void run() {
                if (!active || !player.isOnline()) {
                    cancel();
                    return;
                }

                if (phase == 1) { // Lluvia de Vacío (0 - 15s)
                    if (ticks == 0) {
                        player.sendMessage(SmallCaps.convert("§0§l✦ §c¡Lluvia de Vacío! ¡Esquiva las zonas rojas!"));
                        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 0.8f);
                    }
                    if (ticks % 15 == 0)
                        spawnVoidRain();
                    if (ticks > 300) {
                        phase = 2;
                        ticks = -1;
                    }
                } else if (phase == 2) { // Espejismos Sombríos (15s - 30s)
                    if (ticks == 0) {
                        player.sendMessage(SmallCaps.convert("§0§l✦ §c¡Espejismos Sombríos! ¡Destrúyelos!"));
                        player.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 0.5f, 0.5f);
                        spawnShadowClones();
                    }
                    if (ticks > 300) {
                        phase = 3;
                        ticks = -1;
                    }
                } else if (phase == 3) { // Suelo Inestable (30s - 45s)
                    if (ticks == 0) {
                        player.sendMessage(SmallCaps.convert("§0§l✦ §c¡Suelo Inestable! ¡Cuidado dónde pisas!"));
                        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.0f);
                    }
                    if (ticks % 10 == 0)
                        makeFloorUnstable();
                    if (ticks > 300) {
                        cancel();
                        onComplete.run();
                        return;
                    }
                }
                ticks++;
            }
        };
        domainTask.runTaskTimer(plugin, 60L, 1);
    }

    private void spawnVoidRain() {
        Location center = arena.getFloorCenter();
        if (center == null)
            return;
        double rx = (Math.random() - 0.5) * 16;
        double rz = (Math.random() - 0.5) * 16;
        Location target = center.clone().add(rx, 0.1, rz);

        // Avisar con partículas rojas
        target.getWorld().spawnParticle(Particle.DUST, target, 50, 1.5, 0.1, 1.5, 0,
                new Particle.DustOptions(Color.fromRGB(255, 0, 0), 2.0f));
        target.getWorld().playSound(target, Sound.ENTITY_TNT_PRIMED, 0.5f, 1.0f);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active)
                return;
            target.getWorld().spawnParticle(Particle.EXPLOSION, target, 2);
            target.getWorld().spawnParticle(Particle.DUST, target, 30, 2, 2, 2, 0,
                    new Particle.DustOptions(Color.fromRGB(0, 0, 0), 3.0f));
            target.getWorld().playSound(target, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
            if (player.getLocation().distance(target) < 3.5) {
                player.damage(10.0);
            }
        }, 30L);
    }

    private void spawnShadowClones() {
        Location center = arena.getFloorCenter();
        if (center == null)
            return;
        for (int i = 0; i < 6; i++) {
            Zombie shadow = center.getWorld().spawn(
                    center.clone().add((Math.random() - 0.5) * 12, 1, (Math.random() - 0.5) * 12), Zombie.class, z -> {
                        z.setBaby(true);
                        z.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).setBaseValue(1);
                        z.setHealth(1);
                        z.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.40);
                        z.getEquipment().setHelmet(new ItemStack(Material.WITHER_SKELETON_SKULL));
                        z.setInvisible(true);
                        z.setSilent(true);
                        z.setTarget(player);
                        z.setCustomName(SmallCaps.convert("§8Espejismo Sombrío"));
                    });
            spawnedEntities.add(shadow);
        }
    }

    private void makeFloorUnstable() {
        Location pLoc = player.getLocation();
        for (int i = 0; i < 3; i++) {
            Block b = pLoc.clone().add((Math.random() - 0.5) * 8, -0.5, (Math.random() - 0.5) * 8).getBlock();
            if (arena.isArenaBlock(b.getLocation()) && b.getType() == Material.BLACK_CONCRETE) {
                b.setType(Material.YELLOW_STAINED_GLASS); // Aviso
                player.playSound(b.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!active)
                        return;
                    if (b.getType() == Material.YELLOW_STAINED_GLASS) {
                        b.setType(Material.AIR);
                        b.getWorld().spawnParticle(Particle.SMOKE, b.getLocation().add(0.5, 0.5, 0.5), 10, 0.5, 0.5,
                                0.5, 0);
                    }
                    // Restaurar después de 2s
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (active && arena.isArenaBlock(b.getLocation())) {
                            if (b.getType() == Material.AIR) {
                                b.setType(Material.BLACK_CONCRETE);
                            }
                        }
                    }, 40L);
                }, 30L);
            }
        }
    }

    private void spawnBoss() {
        Location spawnLoc = arena.getFloorCenter();
        if (spawnLoc == null)
            return;
        spawnLoc = spawnLoc.clone().add(3, 0, 0);

        bossBar = Bukkit.createBossBar("§0§l✦ El Arquitecto del Vacío ✦", org.bukkit.boss.BarColor.PURPLE,
                org.bukkit.boss.BarStyle.SEGMENTED_10);
        bossBar.addPlayer(player);

        bossEntity = player.getWorld().spawn(spawnLoc, Zombie.class, z -> {
            z.setCustomName(SmallCaps.convert("§0§l✦ El Arquitecto §0§l✦"));
            z.setCustomNameVisible(true);
            z.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).setBaseValue(500);
            z.setHealth(500);
            z.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(10);
            z.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.32);
            z.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ARMOR).setBaseValue(14);
            z.setBaby(false);
            z.setTarget(player);
            z.setRemoveWhenFarAway(false);
            z.setPersistent(true);

            // Equipo visual oscuro
            z.getEquipment().setHelmet(new ItemStack(Material.WITHER_SKELETON_SKULL));
            z.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
            z.getEquipment().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
            z.getEquipment().setBoots(new ItemStack(Material.NETHERITE_BOOTS));
            z.getEquipment().setItemInMainHand(new ItemStack(Material.END_CRYSTAL));

            // No drop
            z.getEquipment().setHelmetDropChance(0);
            z.getEquipment().setChestplateDropChance(0);
            z.getEquipment().setLeggingsDropChance(0);
            z.getEquipment().setBootsDropChance(0);
            z.getEquipment().setItemInMainHandDropChance(0);

            // Efectos
            z.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 99999, 0, false, false));
            z.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 99999, 0, false, false));
        });
    }

    private void startMainLoop() {
        mainLoop = new BukkitRunnable() {
            @Override
            public void run() {
                if (!active || bossEntity == null || bossEntity.isDead()) {
                    cancel();
                    if (bossEntity != null && bossEntity.isDead()) {
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

                if (bossBar != null) {
                    double hp = bossEntity.getHealth() / bossEntity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getBaseValue();
                    bossBar.setProgress(Math.max(0.0, Math.min(1.0, hp)));
                }

                // Asegurar que el boss tenga al jugador como target
                bossEntity.setTarget(player);

                // Evitar que el boss o sus clones escapen (por si glitchean a través de la
                // esfera)
                Location center = arena.getFloorCenter();
                if (center != null) {
                    if (bossEntity.getLocation().distance(center) > 17) {
                        bossEntity.teleport(center);
                    }
                    for (Entity clone : spawnedEntities) {
                        if (clone != null && !clone.isDead() && clone.getLocation().distance(center) > 17) {
                            clone.teleport(center);
                        }
                    }
                }

                // === ROBO DE DINERO cada 30s ===
                if (tickCount % 600 == 0) {
                    stealMoney();
                }

                // === MANIPULACIÓN DE INVENTARIO cada 45s ===
                if (tickCount % 900 == 0) {
                    shuffleInventory();
                }

                // === NÁUSEA cada 60s (dura 5s) ===
                if (tickCount % 1200 == 0) {
                    applyConfusion();
                }

                // === CLON FALSO (duplicación) a 75% y 50% vida ===
                double healthPercent = bossEntity.getHealth()
                        / bossEntity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getBaseValue();
                if ((healthPercent <= 0.75 && tickCount == (int) (300 * 0.75 / healthPercent))
                        || tickCount == 800 || tickCount == 1400) {
                    if (tickCount == 800 || tickCount == 1400) {
                        spawnClone();
                    }
                }

                // === TAUNTS cada 20s ===
                if (tickCount % 400 == 0) {
                    showTaunt();
                }

                // === PARTÍCULAS OSCURAS alrededor del boss ===
                if (tickCount % 5 == 0) {
                    Location bLoc = bossEntity.getLocation().add(0, 1.5, 0);
                    bLoc.getWorld().spawnParticle(Particle.DUST, bLoc, 5, 0.5, 0.5, 0.5, 0,
                            new Particle.DustOptions(Color.fromRGB(10, 0, 20), 2.0f));
                    bLoc.getWorld().spawnParticle(Particle.SQUID_INK, bLoc, 2, 0.3, 0.3, 0.3, 0.01);

                    // Aura negra pulsante
                    if (tickCount % 20 == 0) {
                        for (int i = 0; i < 8; i++) {
                            double angle = Math.PI * 2 / 8 * i + tickCount * 0.05;
                            bLoc.getWorld().spawnParticle(Particle.DUST,
                                    bLoc.clone().add(Math.cos(angle) * 1.5, -0.5, Math.sin(angle) * 1.5),
                                    2, 0.1, 0.1, 0.1, 0,
                                    new Particle.DustOptions(Color.fromRGB(30, 0, 50), 1.5f));
                        }
                    }
                }

                // === DEVOLUCIÓN al 25% de vida ===
                if (healthPercent <= 0.25 && totalMoneyStolen > 0) {
                    returnMoney();
                }
            }
        };
        mainLoop.runTaskTimer(plugin, 0, 1);
    }

    // ==================== MECÁNICAS PSICOLÓGICAS ====================

    private void stealMoney() {
        Economy eco = plugin.getEconomy();
        if (eco == null)
            return;

        double balance = eco.getBalance(player);
        if (balance <= 0) {
            player.sendTitle( SmallCaps.convert("§4§l$0"), SmallCaps.convert("§7Ya no queda nada que robar..."), 5, 40, 10);
            return;
        }

        double stolen = Math.min(balance, 1000 + Math.random() * 4000); // 1k-5k
        eco.withdrawPlayer(player, stolen);
        totalMoneyStolen += stolen;

        player.sendTitle("§4§l-$" + String.format("%,.0f", stolen),
                "§7\"Tu riqueza me pertenece...\"", 5, 60, 10);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_SCREAM, 0.5f, 2.0f);
        player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1.0f, 0.5f);

        player.sendMessage("§4§l✦ §cEl Arquitecto te robó §e$" + String.format("%,.0f", stolen)
                + "§c. Total robado: §e$" + String.format("%,.0f", totalMoneyStolen));

        // Partículas de monedas desapareciendo
        Location loc = player.getLocation().add(0, 1.5, 0);
        loc.getWorld().spawnParticle(Particle.DUST, loc, 30, 0.5, 0.3, 0.5, 0,
                new Particle.DustOptions(Color.fromRGB(255, 200, 0), 2.0f));
    }

    private void shuffleInventory() {
        player.sendTitle( SmallCaps.convert("§4§l✦"), SmallCaps.convert("§7\"Tu mente es un caos... como tu inventario\""), 5, 50, 10);
        player.playSound(player.getLocation(), Sound.ENTITY_SHULKER_SHOOT, 0.8f, 0.5f);
        player.playSound(player.getLocation(), Sound.BLOCK_SHULKER_BOX_OPEN, 0.8f, 0.3f);

        // Intercambiar 5 pares de ítems aleatorios (no la main hand ni la armadura)
        Random rand = new Random();
        for (int i = 0; i < 5; i++) {
            int slot1 = rand.nextInt(36); // 0-35 = hotbar+inventario
            int slot2 = rand.nextInt(36);
            if (slot1 == slot2)
                continue;

            ItemStack item1 = player.getInventory().getItem(slot1);
            ItemStack item2 = player.getInventory().getItem(slot2);
            player.getInventory().setItem(slot1, item2);
            player.getInventory().setItem(slot2, item1);
        }

        player.sendMessage(SmallCaps.convert("§d§l✦ §7Tu inventario ha sido desordenado..."));
    }

    private void applyConfusion() {
        player.sendTitle( SmallCaps.convert("§0§l✦ DISTORSIÓN ✦"), SmallCaps.convert("§7\"¿Qué es arriba? ¿Qué es abajo?\""), 5, 40, 10);

        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 100, 0, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1, false, true));

        player.playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.6f, 0.5f);

        // Empuje aleatorio breve
        Vector push = new Vector(
                (Math.random() - 0.5) * 1.5,
                0.3,
                (Math.random() - 0.5) * 1.5);
        player.setVelocity(push);

        player.sendMessage(SmallCaps.convert("§0§l✦ §7El Arquitecto distorsiona tu percepción..."));
    }

    private void spawnClone() {
        Location center = arena.getFloorCenter();
        Location spawnLoc;
        if (center != null) {
            // Spawnear dentro de la arena, no relativo al jugador
            spawnLoc = center.clone().add((Math.random() - 0.5) * 12, 1, (Math.random() - 0.5) * 12);
        } else {
            spawnLoc = player.getLocation().clone().add(
                    (Math.random() - 0.5) * 6, 0, (Math.random() - 0.5) * 6);
        }

        player.sendTitle( SmallCaps.convert("§4§l✦ DOPPELGÄNGER ✦"), SmallCaps.convert("§7\"¿Quién es el verdadero tú?\""), 5, 50, 10);
        player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 1.5f);

        Zombie clone = player.getWorld().spawn(spawnLoc, Zombie.class, z -> {
            z.setCustomName(SmallCaps.convert("§c§l" + player.getName() + " §7(impostor)"));
            z.setCustomNameVisible(true);
            z.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).setBaseValue(100);
            z.setHealth(100);
            z.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(8);
            z.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.33);
            z.setBaby(false);
            z.setTarget(player);
            z.setRemoveWhenFarAway(false);
            z.setPersistent(true);

            // Aspecto similar al jugador
            z.getEquipment().setHelmet(new ItemStack(Material.PLAYER_HEAD));
            z.getEquipment().setChestplate(player.getInventory().getChestplate() != null
                    ? player.getInventory().getChestplate().clone()
                    : new ItemStack(Material.IRON_CHESTPLATE));
            z.getEquipment().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
            z.getEquipment().setBoots(new ItemStack(Material.IRON_BOOTS));

            z.getEquipment().setHelmetDropChance(0);
            z.getEquipment().setChestplateDropChance(0);
            z.getEquipment().setLeggingsDropChance(0);
            z.getEquipment().setBootsDropChance(0);
        });

        player.sendMessage(SmallCaps.convert("§c§l✦ §7Un clon tuyo ha aparecido... ¡Elimínalo!"));

        // Partículas de spawn del clon
        spawnLoc.getWorld().spawnParticle(Particle.DUST, spawnLoc.clone().add(0, 1, 0),
                30, 0.5, 0.5, 0.5, 0,
                new Particle.DustOptions(Color.fromRGB(200, 0, 0), 2.0f));
    }

    private void showTaunt() {
        String title = TAUNT_TITLES[tauntIndex % TAUNT_TITLES.length];
        String subtitle = TAUNT_SUBTITLES[tauntIndex % TAUNT_SUBTITLES.length];
        player.sendTitle(title, subtitle, 10, 60, 20);

        // Sonido escalofriante
        player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 0.6f, 0.5f);

        tauntIndex++;
    }

    private void returnMoney() {
        if (totalMoneyStolen <= 0)
            return;

        double returnAmount = totalMoneyStolen * 2; // DOBLE
        Economy eco = plugin.getEconomy();
        if (eco != null) {
            eco.depositPlayer(player, returnAmount);
        }

        player.sendTitle("§a§l+$" + String.format("%,.0f", returnAmount),
                "§7\"Solo era una prueba de fe...\"", 10, 80, 20);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.5f);

        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§a§l✦ §eEL ARQUITECTO DEVUELVE TODO x2 §a§l✦"));
        player.sendMessage("§f  → §a+$" + String.format("%,.0f", returnAmount)
                + " §7(robado: $" + String.format("%,.0f", totalMoneyStolen) + " × 2)");
        player.sendMessage(SmallCaps.convert("§7  §o\"Solo quería ver si te rendirías...\""));
        player.sendMessage("");

        Location loc = player.getLocation().add(0, 1.5, 0);
        loc.getWorld().spawnParticle(Particle.DUST, loc, 60, 1, 1, 1, 0,
                new Particle.DustOptions(Color.fromRGB(50, 255, 50), 2.5f));
        loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 40, 1, 1, 1, 0.5);

        totalMoneyStolen = 0; // Ya devuelto
    }

    /**
     * Llamado desde el listener cuando el boss recibe daño.
     */
    public void onBossDamaged() {
        lastDamageTime = System.currentTimeMillis();
    }

    private void onBossDefeated() {
        active = false;

        // Eliminar BossBar
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }

        // Cancelar tareas
        if (mainLoop != null) {
            try {
                mainLoop.cancel();
            } catch (Exception ignored) {
            }
        }
        if (domainTask != null) {
            try {
                domainTask.cancel();
            } catch (Exception ignored) {
            }
        }

        // Limpiar entidades spawneadas (clones, sombras)
        for (Entity e : spawnedEntities) {
            if (e != null && !e.isDead())
                e.remove();
        }
        spawnedEntities.clear();

        // Guardar ubicación del boss antes de eliminarlo
        Location loc = (bossEntity != null && !bossEntity.isDead()) ? bossEntity.getLocation() : player.getLocation();

        // Eliminar boss entity
        if (bossEntity != null && !bossEntity.isDead())
            bossEntity.remove();

        // Devolver dinero si queda algo
        if (totalMoneyStolen > 0) {
            returnMoney();
        }

        player.sendTitle("§a§l✦ EL ARQUITECTO DERROTADO ✦",
                "§7\"Has... demostrado tu voluntad...\"", 10, 100, 30);

        // Animación de victoria
        World world = loc.getWorld();

        // Explosion de partículas oscuras y doradas
        world.spawnParticle(Particle.DUST, loc.clone().add(0, 1.5, 0), 100, 2, 2, 2, 0,
                new Particle.DustOptions(Color.fromRGB(0, 0, 0), 3.0f));
        world.spawnParticle(Particle.DUST, loc.clone().add(0, 1.5, 0), 80, 2, 2, 2, 0,
                new Particle.DustOptions(Color.fromRGB(255, 200, 0), 2.0f));
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1.5, 0), 60, 1.5, 1.5, 1.5, 0.3);

        world.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_DEATH, 0.5f, 1.5f);
        world.playSound(loc, Sound.ENTITY_WARDEN_DEATH, 0.8f, 0.8f);

        // Iniciar fases post-boss (NO destruir arena, la necesitan las fases)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                manager.onQuest3BossPhaseDefeated(player, arena);
            }
        }, 80L);
    }

    public boolean isBossEntity(Entity entity) {
        return bossEntity != null && bossEntity.equals(entity);
    }

    public Entity getBossEntity() {
        return bossEntity;
    }

    public boolean isBossEntity(UUID entityId) {
        return bossEntity != null && bossEntity.getUniqueId().equals(entityId);
    }

    public boolean isSpawnedEntity(UUID entityId) {
        return spawnedEntities.stream().anyMatch(e -> e != null && !e.isDead() && e.getUniqueId().equals(entityId));
    }

    public void collectProtectedEntities(java.util.List<org.bukkit.entity.Entity> out) {
        if (bossEntity != null && !bossEntity.isDead()) out.add(bossEntity);
        for (org.bukkit.entity.Entity e : spawnedEntities) { if (e != null && !e.isDead()) out.add(e); }
    }

    public VoidArena getArena() {
        return arena;
    }

    public boolean isActive() {
        return active;
    }

    public void cleanup() {
        active = false;
        if (mainLoop != null) {
            try {
                mainLoop.cancel();
            } catch (Exception ignored) {
            }
        }
        if (bossEntity != null && !bossEntity.isDead()) {
            bossEntity.remove();
        }
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
        if (domainTask != null) {
            try {
                domainTask.cancel();
            } catch (Exception ignored) {
            }
        }
        for (Entity e : spawnedEntities) {
            if (e != null && !e.isDead())
                e.remove();
        }
        spawnedEntities.clear();

        // Destruir arena (expansión de dominio)
        if (arena != null && arena.isActive()) {
            arena.destroy();
        }

        // Devolver dinero robado
        if (totalMoneyStolen > 0) {
            Economy eco = plugin.getEconomy();
            if (eco != null && player.isOnline()) {
                eco.depositPlayer(player, totalMoneyStolen);
                player.sendMessage("§a§l✦ §7Tu dinero robado ($" + String.format("%,.0f", totalMoneyStolen)
                        + ") ha sido devuelto.");
            }
            totalMoneyStolen = 0;
        }
    }
}
