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
 * Fase 2 de la Misión 2: 5 oleadas de combate intenso.
 * Más mobs, más tipos, más difícil que la misión 1.
 */
public class AbyssCombat {

    private final CoreProtectPlugin plugin;
    private final ShadowHunterManager manager;
    private final Player player;
    private final ShadowHunterNPC npc;

    private Location arenaCenter;
    private static final double ARENA_RADIUS = 40.0;

    private BossBar bossBar;
    private BukkitTask arenaTask;
    private BukkitTask barrierTask;
    private BukkitTask trackTask;
    private final List<Entity> spawnedMobs = new ArrayList<>();
    private final List<UUID> mobUUIDs = new ArrayList<>();

    private int currentWave = 0;
    private int totalMobsInWave = 0;
    private int killedInWave = 0;
    private boolean active = false;

    public AbyssCombat(CoreProtectPlugin plugin, ShadowHunterManager manager, Player player, ShadowHunterNPC npc) {
        this.plugin = plugin;
        this.manager = manager;
        this.player = player;
        this.npc = npc;
    }

    public void start() {
        active = true;
        arenaCenter = player.getLocation().clone();

        // NPC al borde
        double angle = Math.random() * Math.PI * 2;
        Location npcLoc = arenaCenter.clone().add(Math.cos(angle) * (ARENA_RADIUS - 2), 0,
                Math.sin(angle) * (ARENA_RADIUS - 2));
        npcLoc.setY(npcLoc.getWorld().getHighestBlockYAt(npcLoc) + 1);
        npc.moveToAndStay(npcLoc);

        // BossBar
        bossBar = Bukkit.createBossBar("§4§l☠ Las Legiones del Abismo §8- §7Preparándose...", BarColor.RED,
                BarStyle.SEGMENTED_12);
        bossBar.addPlayer(player);
        bossBar.setProgress(1.0);

        npc.say("\"§4Las fuerzas del Abismo te pondrán a prueba. 5 oleadas... sobrevive.\"");

        player.sendTitle( SmallCaps.convert("§4§lLEGIONES DEL ABISMO"), SmallCaps.convert("§c5 oleadas de combate"), 10, 60, 20);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.5f);

        startArenaParticles();
        startBarrier();
        startMobTracker();

        Bukkit.getScheduler().runTaskLater(plugin, () -> startWave(1), 80L);
    }

    // ==================== OLEADAS ====================

    private void startWave(int wave) {
        if (!active || !player.isOnline())
            return;
        currentWave = wave;
        killedInWave = 0;

        String[] waveNames = {
                "§4Sombras del Abismo",
                "§5Arqueros del Vacío",
                "§0Endermen Corruptos",
                "§6Vindicadores del Caos",
                "§c§lCentinela del Abismo"
        };

        bossBar.setTitle(SmallCaps.convert("§4§l☠ Oleada " + wave + "/5 §8- " + waveNames[wave - 1]));
        bossBar.setColor(wave == 5 ? BarColor.WHITE : BarColor.RED);

        switch (wave) {
            case 1:
                spawnWave1();
                totalMobsInWave = 6;
                break;
            case 2:
                spawnWave2();
                totalMobsInWave = 5;
                break;
            case 3:
                spawnWave3();
                totalMobsInWave = 6;
                break;
            case 4:
                spawnWave4();
                totalMobsInWave = 5;
                break;
            case 5:
                spawnWave5();
                totalMobsInWave = 1;
                break;
        }

        npc.say("\"§c¡Oleada " + wave + "! " + waveNames[wave - 1] + "§c!\"");
        updateBossBar();
    }

    // Oleada 1: 6 Zombies rápidos con aura oscura
    private void spawnWave1() {
        for (int i = 0; i < 6; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Location loc = getRandomArenaLoc();
                spawnEffect(loc);
                Zombie z = (Zombie) loc.getWorld().spawnEntity(loc, EntityType.ZOMBIE);
                z.setCustomName(SmallCaps.convert("§4§lSombra Abismal"));
                z.setCustomNameVisible(true);
                z.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(35);
                z.setHealth(35);
                z.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED).setBaseValue(0.35);
                z.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 99999, 1, false, false));
                z.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 99999, 0, false, false));
                z.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 99999, 0, false, false));
                z.setBaby(false);
                z.setRemoveWhenFarAway(false);
                equipDarkArmor(z, Color.fromRGB(30, 0, 60));
                z.setTarget(player);
                spawnedMobs.add(z);
                mobUUIDs.add(z.getUniqueId());
            }, i * 12L);
        }
    }

    // Oleada 2: 5 Esqueletos con flechas de fuego
    private void spawnWave2() {
        for (int i = 0; i < 5; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Location loc = getRandomArenaLoc();
                spawnEffect(loc);
                Skeleton s = (Skeleton) loc.getWorld().spawnEntity(loc, EntityType.SKELETON);
                s.setCustomName(SmallCaps.convert("§5§lArquero del Vacío"));
                s.setCustomNameVisible(true);
                s.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(45);
                s.setHealth(45);
                s.getEquipment().setItemInMainHand(new ItemStack(Material.BOW));
                s.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 99999, 0, false, false));
                s.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 99999, 0, false, false));
                s.setRemoveWhenFarAway(false);
                equipDarkArmor(s, Color.fromRGB(50, 0, 80));
                s.setTarget(player);
                spawnedMobs.add(s);
                mobUUIDs.add(s.getUniqueId());
            }, i * 18L);
        }
    }

    // Oleada 3: 4 Endermites agresivos + 2 Arañas de Caverna
    private void spawnWave3() {
        // 4 Zombies como Endermen Corruptos (no podemos hacer enderman target fácil)
        for (int i = 0; i < 4; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Location loc = getRandomArenaLoc();
                spawnEffect(loc);
                loc.getWorld().spawnParticle(Particle.PORTAL, loc, 40, 0.5, 1, 0.5, 0.5);
                Zombie z = (Zombie) loc.getWorld().spawnEntity(loc, EntityType.ZOMBIE);
                z.setCustomName(SmallCaps.convert("§0§lEnderman Corrupto"));
                z.setCustomNameVisible(true);
                z.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(50);
                z.setHealth(50);
                z.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE).setBaseValue(8);
                z.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED).setBaseValue(0.38);
                z.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 99999, 2, false, false));
                z.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 99999, 0, false, false));
                z.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 99999, 0, false, false));
                z.setBaby(false);
                z.setRemoveWhenFarAway(false);
                equipDarkArmor(z, Color.fromRGB(10, 0, 20));
                z.setTarget(player);
                spawnedMobs.add(z);
                mobUUIDs.add(z.getUniqueId());
            }, i * 15L);
        }
        // 2 Arañas
        for (int i = 0; i < 2; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Location loc = getRandomArenaLoc();
                spawnEffect(loc);
                CaveSpider spider = (CaveSpider) loc.getWorld().spawnEntity(loc, EntityType.CAVE_SPIDER);
                spider.setCustomName(SmallCaps.convert("§8§lAraña del Abismo"));
                spider.setCustomNameVisible(true);
                spider.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(30);
                spider.setHealth(30);
                spider.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 99999, 2, false, false));
                spider.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 99999, 0, false, false));
                spider.setRemoveWhenFarAway(false);
                spider.setTarget(player);
                spawnedMobs.add(spider);
                mobUUIDs.add(spider.getUniqueId());
            }, 60L + i * 15L);
        }
    }

    // Oleada 4: 3 Vindicadores + 2 Phantoms
    private void spawnWave4() {
        for (int i = 0; i < 3; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Location loc = getRandomArenaLoc();
                spawnEffect(loc);
                loc.getWorld().strikeLightningEffect(loc);
                Vindicator v = (Vindicator) loc.getWorld().spawnEntity(loc, EntityType.VINDICATOR);
                v.setCustomName(SmallCaps.convert("§6§lVindicador del Caos"));
                v.setCustomNameVisible(true);
                v.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(55);
                v.setHealth(55);
                v.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE).setBaseValue(10);
                v.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 99999, 0, false, false));
                v.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 99999, 0, false, false));
                v.setRemoveWhenFarAway(false);
                v.setTarget(player);
                spawnedMobs.add(v);
                mobUUIDs.add(v.getUniqueId());
            }, i * 20L);
        }
        // 2 Phantoms
        for (int i = 0; i < 2; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Location loc = arenaCenter.clone().add(0, 10, 0);
                loc.getWorld().spawnParticle(Particle.SOUL, loc, 20, 1, 1, 1, 0.05);
                Phantom p = (Phantom) loc.getWorld().spawnEntity(loc, EntityType.PHANTOM);
                p.setCustomName(SmallCaps.convert("§8§lPhantom Abismal"));
                p.setCustomNameVisible(true);
                p.setSize(3);
                p.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 99999, 0, false, false));
                p.setRemoveWhenFarAway(false);
                spawnedMobs.add(p);
                mobUUIDs.add(p.getUniqueId());
            }, 60L + i * 20L);
        }
    }

    // Oleada 5: Mini-boss Ravager
    private void spawnWave5() {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Location loc = arenaCenter.clone().add(0, 0, 8);
            loc.setY(loc.getWorld().getHighestBlockYAt(loc) + 1);
            World world = loc.getWorld();

            // Spawn épico
            world.strikeLightningEffect(loc);
            world.strikeLightningEffect(loc.clone().add(3, 0, 0));
            world.strikeLightningEffect(loc.clone().add(-3, 0, 0));
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 100, 2, 2, 2, 0.2);
            world.spawnParticle(Particle.SMOKE, loc, 80, 2, 2, 2, 0.1);
            world.playSound(loc, Sound.ENTITY_WITHER_SPAWN, 0.8f, 0.5f);
            world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 0.6f);

            Ravager r = (Ravager) world.spawnEntity(loc, EntityType.RAVAGER);
            r.setCustomName(SmallCaps.convert("§c§l§kaa§r §4§lCentinela del Abismo §c§l§kaa"));
            r.setCustomNameVisible(true);
            r.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(200);
            r.setHealth(200);
            r.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE).setBaseValue(12);
            r.getAttribute(org.bukkit.attribute.Attribute.KNOCKBACK_RESISTANCE).setBaseValue(0.9);
            r.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 99999, 0, false, false));
            r.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 99999, 0, false, false));
            r.setRemoveWhenFarAway(false);
            r.setTarget(player);
            spawnedMobs.add(r);
            mobUUIDs.add(r.getUniqueId());

            player.sendTitle( SmallCaps.convert("§4§lCENTINELA DEL ABISMO"), SmallCaps.convert("§c¡Oleada final!"), 10, 40, 10);
        }, 40L);
    }

    // ==================== MOB KILLED ====================

    public void onMobKilled(UUID mobId) {
        if (!active)
            return;
        if (!mobUUIDs.contains(mobId))
            return;
        mobUUIDs.remove(mobId);
        killedInWave++;
        updateBossBar();

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);

        if (killedInWave >= totalMobsInWave) {
            onWaveCompleted();
        }
    }

    private void onWaveCompleted() {
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.5f);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 40, 1, 1, 1, 0.3);

        if (currentWave < 5) {
            bossBar.setTitle(SmallCaps.convert("§a§l✔ Oleada " + currentWave + " completada! §7Siguiente en 6s..."));
            bossBar.setProgress(1.0);
            npc.sayDelayed("\"Bien hecho... pero se acercan más.\"", 20L);

            // Curación parcial
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
                    player.setHealth(Math.min(player.getHealth() + maxHealth * 0.25, maxHealth));
                    player.sendMessage(SmallCaps.convert("§a§l❤ §fRecuperaste algo de vida."));
                }
            }, 40L);

            Bukkit.getScheduler().runTaskLater(plugin, () -> startWave(currentWave + 1), 120L);
        } else {
            onAllWavesCompleted();
        }
    }

    private void onAllWavesCompleted() {
        bossBar.setTitle(SmallCaps.convert("§a§l✔ ¡TODAS LAS OLEADAS SUPERADAS!"));
        bossBar.setColor(BarColor.GREEN);
        bossBar.setProgress(1.0);

        World world = player.getWorld();
        Location loc = player.getLocation();
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 2, 0), 150, 3, 3, 3, 0.5);
        world.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        player.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

        player.sendTitle( SmallCaps.convert("§a§l¡5 OLEADAS SUPERADAS!"), SmallCaps.convert("§7Preparando el ritual del Abismo..."), 10, 60, 20);
        npc.sayDelayed("\"§aImpresionante... Ahora viene el ritual. Será diferente esta vez.\"", 40L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            cleanup();
            manager.onQuest2CombatCompleted(player);
        }, 120L);
    }

    // ==================== MOB TRACKER ====================

    private void startMobTracker() {
        trackTask = new BukkitRunnable() {
            int tickCount = 0;

            @Override
            public void run() {
                if (!active || !player.isOnline()) {
                    cancel();
                    return;
                }

                List<UUID> toRemove = new ArrayList<>();
                for (UUID mobId : mobUUIDs) {
                    Entity mob = Bukkit.getEntity(mobId);
                    if (mob == null || mob.isDead()) {
                        toRemove.add(mobId);
                    }
                }
                for (UUID dead : toRemove) {
                    mobUUIDs.remove(dead);
                    killedInWave++;
                }
                if (!toRemove.isEmpty()) {
                    updateBossBar();
                    if (killedInWave >= totalMobsInWave) {
                        onWaveCompleted();
                        return;
                    }
                }

                // Glowing periódico
                if (tickCount % 100 == 0 && !mobUUIDs.isEmpty()) {
                    for (UUID mobId : mobUUIDs) {
                        Entity mob = Bukkit.getEntity(mobId);
                        if (mob instanceof LivingEntity && !mob.isDead()) {
                            ((LivingEntity) mob).addPotionEffect(
                                    new PotionEffect(PotionEffectType.GLOWING, 120, 0, false, false));
                        }
                    }
                }

                // Pathfinding
                for (UUID mobId : new ArrayList<>(mobUUIDs)) {
                    Entity mob = Bukkit.getEntity(mobId);
                    if (mob instanceof LivingEntity && !mob.isDead()) {
                        clearPathForMob((LivingEntity) mob);
                    }
                }

                tickCount++;
            }
        }.runTaskTimer(plugin, 40, 20);
    }

    // ==================== ARENA ====================

    private void startArenaParticles() {
        arenaTask = new BukkitRunnable() {
            double angle = 0;

            @Override
            public void run() {
                if (!active || !player.isOnline()) {
                    cancel();
                    return;
                }
                World world = arenaCenter.getWorld();

                // Círculo de partículas
                for (int i = 0; i < 16; i++) {
                    double a = angle + (Math.PI * 2 / 16) * i;
                    double x = Math.cos(a) * ARENA_RADIUS;
                    double z = Math.sin(a) * ARENA_RADIUS;
                    Location pLoc = arenaCenter.clone().add(x, 0.5, z);
                    pLoc.setY(world.getHighestBlockYAt(pLoc) + 0.5);
                    world.spawnParticle(Particle.DUST, pLoc, 2, 0, 0.3, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(150, 0, 50), 1.5f));
                }

                // Pilares de soul fire
                if ((int) (angle * 10) % 30 == 0) {
                    for (int i = 0; i < 4; i++) {
                        double a = (Math.PI / 2) * i;
                        Location pillar = arenaCenter.clone().add(Math.cos(a) * ARENA_RADIUS, 0,
                                Math.sin(a) * ARENA_RADIUS);
                        pillar.setY(world.getHighestBlockYAt(pillar) + 0.5);
                        world.spawnParticle(Particle.SOUL_FIRE_FLAME, pillar, 8, 0.2, 1.5, 0.2, 0.02);
                    }
                }

                angle += 0.04;
            }
        }.runTaskTimer(plugin, 0, 4);
    }

    private void startBarrier() {
        barrierTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!active || !player.isOnline()) {
                    cancel();
                    return;
                }
                if (player.getLocation().distance(arenaCenter) > ARENA_RADIUS) {
                    player.teleport(arenaCenter);
                    player.sendMessage(SmallCaps.convert("§c§l⚠ §fNo puedes salir de la arena durante el combate."));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                }

                // Evitar que los mobs salgan de la arena
                for (Entity mob : spawnedMobs) {
                    if (mob != null && !mob.isDead() && mob.getLocation().getWorld().equals(arenaCenter.getWorld())) {
                        if (mob.getLocation().distance(arenaCenter) > ARENA_RADIUS) {
                            mob.teleport(arenaCenter);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 10);
    }

    // ==================== HELPERS ====================

    private Location getRandomArenaLoc() {
        double angle = Math.random() * Math.PI * 2;
        double radius = 3 + Math.random() * (ARENA_RADIUS - 5);
        Location loc = arenaCenter.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
        loc.setY(loc.getWorld().getHighestBlockYAt(loc) + 1);
        return loc;
    }

    private void spawnEffect(Location loc) {
        World world = loc.getWorld();
        world.spawnParticle(Particle.SMOKE, loc, 25, 0.5, 1, 0.5, 0.05);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 15, 0.3, 0.5, 0.3, 0.03);
        world.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 0.6f);
    }

    private void equipDarkArmor(LivingEntity entity, Color color) {
        ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta cMeta = (LeatherArmorMeta) chest.getItemMeta();
        cMeta.setColor(color);
        chest.setItemMeta(cMeta);
        ItemStack legs = new ItemStack(Material.LEATHER_LEGGINGS);
        LeatherArmorMeta lMeta = (LeatherArmorMeta) legs.getItemMeta();
        lMeta.setColor(color.mixColors(Color.BLACK));
        legs.setItemMeta(lMeta);
        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);
        LeatherArmorMeta bMeta = (LeatherArmorMeta) boots.getItemMeta();
        bMeta.setColor(color.mixColors(Color.BLACK));
        boots.setItemMeta(bMeta);

        entity.getEquipment().setChestplate(chest);
        entity.getEquipment().setLeggings(legs);
        entity.getEquipment().setBoots(boots);
        entity.getEquipment().setChestplateDropChance(0);
        entity.getEquipment().setLeggingsDropChance(0);
        entity.getEquipment().setBootsDropChance(0);
        entity.getEquipment().setItemInMainHandDropChance(0);
    }

    private void updateBossBar() {
        if (bossBar == null)
            return;
        double progress = totalMobsInWave > 0 ? (double) killedInWave / totalMobsInWave : 0;
        bossBar.setProgress(Math.min(1.0, Math.max(0, progress)));
    }

    private void clearPathForMob(LivingEntity mob) {
        if (!player.isOnline())
            return;
        Location mobLoc = mob.getLocation();
        Location playerLoc = player.getLocation();
        if (!mobLoc.getWorld().equals(playerLoc.getWorld()))
            return;

        // Natación: si está en agua/lava, subir a la superficie
        if (mobLoc.getBlock().isLiquid()) {
            mob.setVelocity(mob.getVelocity().add(new Vector(0, 0.15, 0)));
        }

        Vector dir = playerLoc.toVector().subtract(mobLoc.toVector()).normalize();
        Location feet = mobLoc.clone().add(dir.clone().multiply(1.0));
        feet.setY(mobLoc.getY());
        Location head = feet.clone().add(0, 1, 0);
        if (feet.getBlock().getType().isSolid() && isBreakable(feet.getBlock().getType()))
            mineBlock(feet.getBlock());
        if (head.getBlock().getType().isSolid() && isBreakable(head.getBlock().getType()))
            mineBlock(head.getBlock());
    }

    private void mineBlock(org.bukkit.block.Block block) {
        if (block.getType() == Material.AIR || block.getType() == Material.BEDROCK)
            return;
        org.bukkit.block.data.BlockData originalData = block.getBlockData().clone();
        Location loc = block.getLocation().add(0.5, 0.5, 0.5);
        block.getWorld().spawnParticle(Particle.BLOCK, loc, 12, 0.3, 0.3, 0.3, 0.1, originalData);
        block.getWorld().playSound(loc, Sound.BLOCK_STONE_BREAK, 0.5f, 1.0f);
        block.setType(Material.AIR);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (block.getType() == Material.AIR)
                block.setBlockData(originalData);
        }, 20L * 30);
    }

    private boolean isBreakable(Material mat) {
        if (mat == Material.AIR || mat == Material.CAVE_AIR || mat == Material.VOID_AIR)
            return false;
        if (mat == Material.BEDROCK || mat == Material.OBSIDIAN || mat == Material.CRYING_OBSIDIAN)
            return false;
        if (mat == Material.REINFORCED_DEEPSLATE || mat == Material.BARRIER)
            return false;
        if (mat == Material.END_PORTAL || mat == Material.END_PORTAL_FRAME || mat == Material.NETHER_PORTAL)
            return false;
        if (mat == Material.COMMAND_BLOCK || mat == Material.CHAIN_COMMAND_BLOCK
                || mat == Material.REPEATING_COMMAND_BLOCK)
            return false;
        if (mat.name().contains("CHEST") || mat.name().contains("SHULKER"))
            return false;
        return true;
    }

    public boolean isMobFromQuest(UUID entityId) {
        return mobUUIDs.contains(entityId);
    }

    public boolean isActive() {
        return active;
    }

    public void cleanup() {
        active = false;
        if (arenaTask != null)
            arenaTask.cancel();
        if (barrierTask != null)
            barrierTask.cancel();
        if (trackTask != null)
            trackTask.cancel();
        if (bossBar != null)
            bossBar.removeAll();
        for (Entity mob : spawnedMobs) {
            if (mob != null && !mob.isDead())
                mob.remove();
        }
        spawnedMobs.clear();
        mobUUIDs.clear();
    }
}
