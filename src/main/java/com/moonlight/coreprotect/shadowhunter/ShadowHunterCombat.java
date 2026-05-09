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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Fase de combate: 3 oleadas de mobs custom con arena de partículas.
 */
public class ShadowHunterCombat {

    private final CoreProtectPlugin plugin;
    private final ShadowHunterManager manager;
    private final Player player;
    private final ShadowHunterNPC npc;

    private Location arenaCenter;
    private static final double ARENA_RADIUS = 35.0;

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

    public ShadowHunterCombat(CoreProtectPlugin plugin, ShadowHunterManager manager, Player player,
            ShadowHunterNPC npc) {
        this.plugin = plugin;
        this.manager = manager;
        this.player = player;
        this.npc = npc;
    }

    public void start() {
        active = true;
        arenaCenter = player.getLocation().clone();

        // Mover NPC al borde de la arena
        double angle = Math.random() * Math.PI * 2;
        Location npcLoc = arenaCenter.clone().add(Math.cos(angle) * (ARENA_RADIUS - 2), 0,
                Math.sin(angle) * (ARENA_RADIUS - 2));
        npcLoc.setY(npcLoc.getWorld().getHighestBlockYAt(npcLoc) + 1);
        npc.moveToAndStay(npcLoc);

        // BossBar
        bossBar = Bukkit.createBossBar("§5§l☠ El Cazador de Sombras §8- §7Preparándose...", BarColor.PURPLE,
                BarStyle.SEGMENTED_10);
        bossBar.addPlayer(player);
        bossBar.setProgress(1.0);

        // Diálogo
        npc.say("\"Las sombras te pondrán a prueba. Sobrevive.\"");

        // Crear arena visual
        startArenaParticles();
        startBarrier();

        // Tarea periódica: limpiar mobs muertos/desaparecidos + glowing
        startMobTracker();

        // Iniciar oleada 1 tras 3 segundos
        Bukkit.getScheduler().runTaskLater(plugin, () -> startWave(1), 60L);
    }

    // ==================== OLEADAS ====================

    private void startWave(int wave) {
        if (!active || !player.isOnline())
            return;

        currentWave = wave;
        killedInWave = 0;

        switch (wave) {
            case 1:
                bossBar.setTitle(SmallCaps.convert("§5§l☠ Oleada 1/3 §8- §4Sombras del Abismo"));
                bossBar.setColor(BarColor.RED);
                totalMobsInWave = 5;
                npc.say("\"Primera oleada... ¡Prepárate!\"");
                spawnWave1();
                break;
            case 2:
                bossBar.setTitle(SmallCaps.convert("§5§l☠ Oleada 2/3 §8- §5Arqueros Espectrales"));
                bossBar.setColor(BarColor.PURPLE);
                totalMobsInWave = 4;
                npc.say("\"Más fuertes... ¡Cuidado con sus flechas!\"");
                spawnWave2();
                break;
            case 3:
                bossBar.setTitle(SmallCaps.convert("§5§l☠ Oleada 3/3 §8- §0§lEl Heraldo del Vacío"));
                bossBar.setColor(BarColor.WHITE);
                totalMobsInWave = 1;
                npc.say("\"§c¡La última prueba! ¡No te confíes!\"");
                spawnWave3();
                break;
        }

        updateBossBar();
    }

    private void spawnWave1() {
        // 5 Zombies rápidos con armadura oscura
        for (int i = 0; i < 5; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Location loc = getRandomArenaLoc();
                spawnEffect(loc);

                Zombie zombie = (Zombie) loc.getWorld().spawnEntity(loc, EntityType.ZOMBIE);
                zombie.setCustomName(SmallCaps.convert("§4§lSombra del Abismo"));
                zombie.setCustomNameVisible(true);
                zombie.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).setBaseValue(30);
                zombie.setHealth(30);
                zombie.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.35);
                zombie.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 99999, 1, false, false));
                zombie.setBaby(false);
                zombie.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 99999, 0, false, false));
                zombie.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 99999, 0, false, false));
                zombie.setRemoveWhenFarAway(false);

                // Armadura oscura
                equipDarkArmor(zombie);

                zombie.setTarget(player);
                spawnedMobs.add(zombie);
                mobUUIDs.add(zombie.getUniqueId());
            }, i * 15L); // Spawn escalonado
        }
    }

    private void spawnWave2() {
        // 4 Esqueletos con Power
        for (int i = 0; i < 4; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Location loc = getRandomArenaLoc();
                spawnEffect(loc);

                Skeleton skeleton = (Skeleton) loc.getWorld().spawnEntity(loc, EntityType.SKELETON);
                skeleton.setCustomName(SmallCaps.convert("§5§lArquero Espectral"));
                skeleton.setCustomNameVisible(true);
                skeleton.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).setBaseValue(40);
                skeleton.setHealth(40);

                // Arco potente
                ItemStack bow = new ItemStack(Material.BOW);
                skeleton.getEquipment().setItemInMainHand(bow);

                skeleton.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 99999, 0, false, false));
                skeleton.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 99999, 0, false, false));
                skeleton.setRemoveWhenFarAway(false);
                equipDarkArmor(skeleton);
                skeleton.setTarget(player);
                spawnedMobs.add(skeleton);
                mobUUIDs.add(skeleton.getUniqueId());
            }, i * 20L);
        }
    }

    private void spawnWave3() {
        // 1 Wither Skeleton boss
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Location loc = arenaCenter.clone().add(0, 0, 5);
            loc.setY(loc.getWorld().getHighestBlockYAt(loc) + 1);

            // Efectos épicos de spawn
            World world = loc.getWorld();
            world.strikeLightningEffect(loc);
            world.spawnParticle(Particle.SMOKE, loc, 80, 1, 2, 1, 0.1);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 40, 1, 1, 1, 0.1);
            world.playSound(loc, Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.2f);

            WitherSkeleton boss = (WitherSkeleton) world.spawnEntity(loc, EntityType.WITHER_SKELETON);
            boss.setCustomName(SmallCaps.convert("§0§l§kaa§r §4§lEl Heraldo del Vacío §0§l§kaa"));
            boss.setCustomNameVisible(true);
            boss.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).setBaseValue(100);
            boss.setHealth(100);
            boss.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(10);
            boss.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.30);
            boss.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 99999, 1, false, false));
            boss.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 99999, 0, false, false));
            boss.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 99999, 0, false, false));
            boss.setRemoveWhenFarAway(false);

            // Espada de netherite
            boss.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
            equipDarkArmor(boss);
            boss.getEquipment().setHelmet(new ItemStack(Material.NETHERITE_HELMET));

            boss.setTarget(player);
            spawnedMobs.add(boss);
            mobUUIDs.add(boss.getUniqueId());
        }, 30L);
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

                // Limpiar mobs muertos o desaparecidos que no se contaron
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

                // Cada 5 segundos: aplicar Glowing a los mobs restantes para que se vean
                if (tickCount % 100 == 0 && !mobUUIDs.isEmpty()) {
                    for (UUID mobId : mobUUIDs) {
                        Entity mob = Bukkit.getEntity(mobId);
                        if (mob instanceof LivingEntity && !mob.isDead()) {
                            ((LivingEntity) mob).addPotionEffect(
                                    new PotionEffect(PotionEffectType.GLOWING, 120, 0, false, false));
                        }
                    }
                }

                // Romper bloques entre cada mob y el jugador para que puedan llegar
                for (UUID mobId : new ArrayList<>(mobUUIDs)) {
                    Entity mob = Bukkit.getEntity(mobId);
                    if (mob instanceof LivingEntity && !mob.isDead()) {
                        clearPathForMob((LivingEntity) mob);
                    }
                }

                tickCount++;
            }
        }.runTaskTimer(plugin, 40, 20); // check cada segundo
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

        // Efecto de kill
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);

        if (killedInWave >= totalMobsInWave) {
            // Oleada completada
            onWaveCompleted();
        }
    }

    private void onWaveCompleted() {
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.5f);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 30, 1, 1, 1, 0.3);

        if (currentWave < 3) {
            bossBar.setTitle(SmallCaps.convert("§a§l✔ Oleada " + currentWave + " completada! §7Siguiente en 5s..."));
            bossBar.setProgress(1.0);
            npc.sayDelayed("\"Bien hecho... pero no ha terminado.\"", 20L);

            // Curar al jugador parcialmente entre oleadas
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
                    player.setHealth(Math.min(player.getHealth() + maxHealth * 0.3, maxHealth));
                    player.sendMessage(SmallCaps.convert("§a§l❤ §fRecuperaste algo de vida."));
                }
            }, 40L);

            // Siguiente oleada
            Bukkit.getScheduler().runTaskLater(plugin, () -> startWave(currentWave + 1), 100L);
        } else {
            // Todas las oleadas completadas
            onAllWavesCompleted();
        }
    }

    private void onAllWavesCompleted() {
        bossBar.setTitle(SmallCaps.convert("§a§l✔ ¡TODAS LAS OLEADAS SUPERADAS!"));
        bossBar.setColor(BarColor.GREEN);
        bossBar.setProgress(1.0);

        // Efecto de victoria épico
        World world = player.getWorld();
        Location loc = player.getLocation();
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 2, 0), 100, 2, 2, 2, 0.5);
        world.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        player.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

        player.sendTitle( SmallCaps.convert("§a§l¡OLEADAS SUPERADAS!"), SmallCaps.convert("§7Preparando el ritual..."), 10, 60, 20);

        npc.sayDelayed("\"Impresionante... Ahora viene la verdadera prueba.\"", 40L);

        // Limpiar y avanzar
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (arenaTask != null)
                arenaTask.cancel();
            if (barrierTask != null)
                barrierTask.cancel();
            bossBar.removeAll();
            active = false;
            manager.onCombatCompleted(player);
        }, 120L);
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

                // Círculo de partículas rojas
                for (int i = 0; i < 12; i++) {
                    double a = angle + (Math.PI * 2 / 12) * i;
                    double x = Math.cos(a) * ARENA_RADIUS;
                    double z = Math.sin(a) * ARENA_RADIUS;
                    Location pLoc = arenaCenter.clone().add(x, 0.5, z);
                    pLoc.setY(world.getHighestBlockYAt(pLoc) + 0.5);

                    world.spawnParticle(Particle.DUST, pLoc, 2, 0, 0.3, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(150, 0, 0), 1.5f));
                }

                // Pilares de fuego en esquinas
                if ((int) (angle * 10) % 40 == 0) {
                    for (int i = 0; i < 4; i++) {
                        double a = (Math.PI / 2) * i;
                        Location pillar = arenaCenter.clone().add(Math.cos(a) * ARENA_RADIUS, 0,
                                Math.sin(a) * ARENA_RADIUS);
                        pillar.setY(world.getHighestBlockYAt(pillar) + 0.5);
                        world.spawnParticle(Particle.SOUL_FIRE_FLAME, pillar, 5, 0.2, 1, 0.2, 0.02);
                    }
                }

                angle += 0.05;
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

                // Si el jugador sale de la arena, teletransportar de vuelta
                if (player.getLocation().distance(arenaCenter) > ARENA_RADIUS) {
                    player.teleport(arenaCenter);
                    player.sendMessage(SmallCaps.convert("§c§l⚠ §fNo puedes salir de la arena durante el combate."));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                    player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation(), 20, 0.5, 1, 0.5, 0.5);
                }

                // Asegurar que los mobs tampoco salgan de la arena
                for (Entity mob : spawnedMobs) {
                    if (mob != null && !mob.isDead() && mob.getLocation().getWorld().equals(arenaCenter.getWorld())) {
                        if (mob.getLocation().distance(arenaCenter) > ARENA_RADIUS) {
                            mob.teleport(arenaCenter);
                            mob.getWorld().spawnParticle(Particle.PORTAL, mob.getLocation(), 20, 0.5, 1, 0.5, 0.5);
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
        world.spawnParticle(Particle.SMOKE, loc, 20, 0.5, 1, 0.5, 0.05);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 10, 0.3, 0.5, 0.3, 0.02);
        world.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 0.8f);
    }

    private void equipDarkArmor(LivingEntity entity) {
        ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta cMeta = (LeatherArmorMeta) chest.getItemMeta();
        cMeta.setColor(Color.fromRGB(20, 0, 40));
        chest.setItemMeta(cMeta);

        ItemStack legs = new ItemStack(Material.LEATHER_LEGGINGS);
        LeatherArmorMeta lMeta = (LeatherArmorMeta) legs.getItemMeta();
        lMeta.setColor(Color.fromRGB(15, 0, 30));
        legs.setItemMeta(lMeta);

        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);
        LeatherArmorMeta bMeta = (LeatherArmorMeta) boots.getItemMeta();
        bMeta.setColor(Color.fromRGB(10, 0, 20));
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

    // ==================== MOB PATHFINDING ====================

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

        // Comprobar 2 bloques delante del mob (pies y cabeza)
        Location ahead = mobLoc.clone().add(dir.clone().multiply(1.0));
        Location feet = ahead.clone();
        feet.setY(mobLoc.getY());
        Location head = feet.clone().add(0, 1, 0);

        if (feet.getBlock().getType().isSolid() && isBreakable(feet.getBlock().getType())) {
            mineBlock(feet.getBlock());
        }
        if (head.getBlock().getType().isSolid() && isBreakable(head.getBlock().getType())) {
            mineBlock(head.getBlock());
        }

        // Si el jugador está abajo, picar hacia abajo
        if (playerLoc.getY() < mobLoc.getY() - 1) {
            Location below = mobLoc.clone().add(dir.getX() * 0.5, -1, dir.getZ() * 0.5);
            if (below.getBlock().getType().isSolid() && isBreakable(below.getBlock().getType())) {
                mineBlock(below.getBlock());
            }
        }
        // Si el jugador está arriba, picar hacia arriba
        if (playerLoc.getY() > mobLoc.getY() + 2) {
            Location above = mobLoc.clone().add(dir.getX() * 0.5, 2, dir.getZ() * 0.5);
            if (above.getBlock().getType().isSolid() && isBreakable(above.getBlock().getType())) {
                mineBlock(above.getBlock());
            }
        }
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
            if (block.getType() == Material.AIR) {
                block.setBlockData(originalData);
            }
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
