package com.moonlight.coreprotect.koth;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Boss Final del modo COOPERATIVO.
 * Aparece a los 2 minutos restantes con efectos sísmicos, invocaciones y múltiples fases.
 */
public class KothBoss {

    private final CoreProtectPlugin plugin;
    private final Random random = new Random();

    private LivingEntity bossEntity;
    private BossBar bossBossBar;
    private BukkitRunnable bossTask;
    private boolean active = false;
    private int phase = 1; // 1 = normal, 2 = enraged (<50% HP), 3 = final (<20%)
    private final List<LivingEntity> minions = new ArrayList<>();

    // Callback para notificar al KothManager cuando el boss muere
    private Runnable onDeathCallback;

    public KothBoss(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isActive() { return active; }
    public LivingEntity getBossEntity() { return bossEntity; }

    public void setOnDeathCallback(Runnable callback) {
        this.onDeathCallback = callback;
    }

    // ==========================================
    // INVOCACIÓN DEL BOSS
    // ==========================================

    /**
     * Invoca el boss con animación sísmica completa en la zona dada.
     */
    public void summon(Location zoneCenterRaw, KothZone zone) {
        if (active) return;

        // Buscar ubicación segura en el borde de la zona
        Location spawnLoc = findBossSpawnLocation(zoneCenterRaw, zone);
        if (spawnLoc == null) spawnLoc = zoneCenterRaw.clone().add(0, 1, 0);

        final Location finalLoc = spawnLoc;

        // === FASE 0: Terremoto de aviso (5 segundos antes) ===
        broadcastBossWarning();
        earthquakeEffect(finalLoc, 5);

        // Invocar boss después del terremoto
        new BukkitRunnable() {
            @Override
            public void run() {
                spawnBossEntity(finalLoc);
                startBossAI(zoneCenterRaw, zone);
            }
        }.runTaskLater(plugin, 100L); // 5 segundos
    }

    private void broadcastBossWarning() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle( SmallCaps.convert("§4§l☠ BOSS FINAL ☠"), SmallCaps.convert("§c¡El Guardián del KOTH despierta!"), 10, 60, 20);
            p.sendMessage("");
            p.sendMessage(SmallCaps.convert("§4§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            p.sendMessage(SmallCaps.convert("§c§l  ☠  ¡EL GUARDIÁN DEL KOTH HA DESPERTADO!  ☠"));
            p.sendMessage(SmallCaps.convert("§7  El boss final ha aparecido en el campo de batalla."));
            p.sendMessage(SmallCaps.convert("§7  ¡Derrótalo antes de que acabe el tiempo!"));
            p.sendMessage(SmallCaps.convert("§4§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            p.sendMessage("");
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f);
        }
    }

    private void earthquakeEffect(Location center, int durationSeconds) {
        World world = center.getWorld();
        if (world == null) return;

        new BukkitRunnable() {
            int ticks = 0;
            final int total = durationSeconds * 4; // cada 5 ticks

            @Override
            public void run() {
                if (ticks >= total) { cancel(); return; }
                ticks++;

                // Partículas sísmicas en el suelo
                for (int i = 0; i < 20; i++) {
                    double angle = random.nextDouble() * 2 * Math.PI;
                    double radius = 2 + random.nextDouble() * 12;
                    double x = center.getX() + Math.cos(angle) * radius;
                    double z = center.getZ() + Math.sin(angle) * radius;
                    Location pLoc = new Location(world, x, center.getY(), z);
                    world.spawnParticle(Particle.FALLING_DUST, pLoc, 8, 0.5, 0.1, 0.5, 0.05,
                            Material.STONE.createBlockData());
                    world.spawnParticle(Particle.LARGE_SMOKE, pLoc, 3, 0.3, 0.5, 0.3, 0.02);
                }

                // Columna ascendente de fuego
                for (int y = 0; y < 6; y++) {
                    Location flameLoc = center.clone().add(
                            random.nextDouble() * 4 - 2, y * 0.8, random.nextDouble() * 4 - 2);
                    world.spawnParticle(Particle.FLAME, flameLoc, 5, 0.3, 0.1, 0.3, 0.05);
                    world.spawnParticle(Particle.LAVA, flameLoc, 2, 0.2, 0.1, 0.2, 0.01);
                }

                // Sacudir a jugadores en el mundo KOTH
                for (Player p : world.getPlayers()) {
                    if (p.getGameMode() == GameMode.SURVIVAL) {
                        // Efecto de temblor: pequeño impulso aleatorio
                        double vx = (random.nextDouble() - 0.5) * 0.3;
                        double vz = (random.nextDouble() - 0.5) * 0.3;
                        p.setVelocity(p.getVelocity().add(new org.bukkit.util.Vector(vx, 0.05, vz)));
                    }
                }

                // Sonido sísmico
                if (ticks % 2 == 0) {
                    world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 0.3f);
                    world.playSound(center, Sound.BLOCK_GRAVEL_BREAK, 1.0f, 0.5f);
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void spawnBossEntity(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;

        // Boss: Wither Skeleton gigante
        LivingEntity boss = (LivingEntity) world.spawnEntity(loc, EntityType.WITHER_SKELETON);

        // Configurar atributos del boss
        try {
            boss.getAttribute(Attribute.MAX_HEALTH).setBaseValue(150.0);
            boss.setHealth(150.0);
            boss.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(10.0);
            boss.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.35);
            boss.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(0.8);
            boss.getAttribute(Attribute.ARMOR).setBaseValue(6.0);
        } catch (Exception e) {
            boss.setHealth(150.0);
        }

        boss.setCustomName(SmallCaps.convert("§4§l☠ §c§lGUARDIÁN DEL KOTH §4§l☠"));
        boss.setCustomNameVisible(true);
        boss.setRemoveWhenFarAway(false);

        // Efectos
        boss.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 999999, 0, false, false));
        boss.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 999999, 0, false, false));

        // Equipar con netherite
        if (boss.getEquipment() != null) {
            boss.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
            boss.getEquipment().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
            boss.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
            boss.getEquipment().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
            boss.getEquipment().setBoots(new ItemStack(Material.NETHERITE_BOOTS));
            boss.getEquipment().setItemInMainHandDropChance(0f);
            boss.getEquipment().setHelmetDropChance(0f);
            boss.getEquipment().setChestplateDropChance(0f);
            boss.getEquipment().setLeggingsDropChance(0f);
            boss.getEquipment().setBootsDropChance(0f);
        }

        // Metadata para identificarlo
        boss.setMetadata("koth_boss", new org.bukkit.metadata.FixedMetadataValue(plugin, true));

        this.bossEntity = boss;
        this.active = true;
        this.phase = 1;

        // BossBar dedicada al boss (keyed para sobrevivir reloads)
        org.bukkit.NamespacedKey bossKey = new org.bukkit.NamespacedKey(plugin, "koth_boss");
        Bukkit.removeBossBar(bossKey);
        bossBossBar = Bukkit.createBossBar(
                bossKey,
                "§4§l☠ §c§lGUARDIÁN DEL KOTH §4§l☠  §8│  §cFASE 1",
                BarColor.RED, BarStyle.SEGMENTED_20);
        bossBossBar.setProgress(1.0);
        for (Player p : Bukkit.getOnlinePlayers()) {
            bossBossBar.addPlayer(p);
        }

        // Explosión de spawneo
        world.strikeLightningEffect(loc);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1, 0), 100, 1, 2, 1, 0.5);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 3, 0.5, 0.5, 0.5, 0);
        world.playSound(loc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.7f);

        plugin.getLogger().info("[KOTH] Boss invocado en " + loc);
    }

    // ==========================================
    // IA DEL BOSS
    // ==========================================

    private void startBossAI(Location zoneCenter, KothZone zone) {
        bossTask = new BukkitRunnable() {
            int tickCount = 0;

            @Override
            public void run() {
                if (bossEntity == null || bossEntity.isDead() || !bossEntity.isValid()) {
                    onBossDeath();
                    cancel();
                    return;
                }

                tickCount++;
                double maxHp = 150.0;
                try { maxHp = bossEntity.getAttribute(Attribute.MAX_HEALTH).getValue(); } catch (Exception ignored) {}
                double hpPercent = bossEntity.getHealth() / maxHp;

                // Actualizar BossBar
                updateBossBar(hpPercent);

                // Detectar cambio de fase
                checkPhaseTransition(hpPercent);

                // Habilidades cada N segundos
                if (tickCount % 5 == 0) {
                    bossAuraParticles();
                }
                if (tickCount % 10 == 0) {
                    summonMinions(zoneCenter);
                }
                if (tickCount % 15 == 0) {
                    earthquakeStrike(bossEntity.getLocation());
                }
                if (tickCount % 20 == 0 && phase >= 2) {
                    fireStorm(bossEntity.getLocation());
                }
                if (tickCount % 25 == 0 && phase >= 3) {
                    deathNova(bossEntity.getLocation());
                }

                // Asegurar que el boss sigue en la zona (teleport si está muy lejos)
                if (zoneCenter != null && bossEntity.getLocation().distance(zoneCenter) > 30) {
                    bossEntity.teleport(zoneCenter.clone().add(0, 1, 0));
                }

                // Agregar nuevos jugadores a la bossbar
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (bossBossBar != null && !bossBossBar.getPlayers().contains(p)) {
                        bossBossBar.addPlayer(p);
                    }
                }
            }
        };
        bossTask.runTaskTimer(plugin, 0L, 20L);
    }

    private void updateBossBar(double hpPercent) {
        if (bossBossBar == null) return;
        bossBossBar.setProgress(Math.max(0, Math.min(1, hpPercent)));

        String phaseLabel;
        BarColor color;
        if (hpPercent > 0.5) {
            phaseLabel = "§cFASE 1";
            color = BarColor.RED;
        } else if (hpPercent > 0.2) {
            phaseLabel = "§4§lFASE 2 §c§l— ¡ENFURECIDO!";
            color = BarColor.PURPLE;
        } else {
            phaseLabel = "§0§lFASE FINAL §4§l— ¡AGONIZANDO!";
            color = BarColor.WHITE;
        }

        double maxHp = 150.0;
        try { maxHp = bossEntity.getAttribute(Attribute.MAX_HEALTH).getValue(); } catch (Exception ignored) {}
        int hpDisplay = (int) bossEntity.getHealth();
        int maxDisplay = (int) maxHp;

        bossBossBar.setTitle(SmallCaps.convert(String.format("§4§l☠ §c§lGUARDIÁN §4§l☠  §8│  %s  §8│  §c%d§8/§c%d❤", phaseLabel, hpDisplay, maxDisplay)));
        bossBossBar.setColor(color);
    }

    private void checkPhaseTransition(double hpPercent) {
        int newPhase = hpPercent > 0.5 ? 1 : (hpPercent > 0.2 ? 2 : 3);
        if (newPhase == phase) return;

        phase = newPhase;
        World world = bossEntity.getWorld();

        if (phase == 2) {
            // FASE 2: ENFURECIDO
            try {
                bossEntity.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.45);
                bossEntity.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(16.0);
            } catch (Exception ignored) {}
            bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999, 2, false, false));
            bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 999999, 1, false, false));

            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendTitle( SmallCaps.convert("§4§l⚠ FASE 2"), SmallCaps.convert("§c¡El Guardián está ENFURECIDO!"), 5, 40, 10);
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_HURT, 1.0f, 0.6f);
            }
            earthquakeEffect(bossEntity.getLocation(), 3);
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, bossEntity.getLocation().add(0, 2, 0), 60, 1, 1, 1, 0.3);

        } else if (phase == 3) {
            // FASE 3: AGONIZANDO
            try {
                bossEntity.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(20.0);
                bossEntity.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
            } catch (Exception ignored) {}
            bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999, 3, false, false));

            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendTitle( SmallCaps.convert("§0§l💀 FASE FINAL"), SmallCaps.convert("§4¡El Guardián agoniza! ¡AHORA!"), 5, 50, 10);
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_HURT, 1.0f, 0.5f);
            }
            // Invocar mini-jefes adicionales
            summonEliteMobs(bossEntity.getLocation());
        }
    }

    // ==========================================
    // HABILIDADES
    // ==========================================

    private void bossAuraParticles() {
        if (bossEntity == null || !bossEntity.isValid()) return;
        Location loc = bossEntity.getLocation().add(0, 1, 0);
        World world = loc.getWorld();

        // Aura de partículas alrededor del boss
        for (int i = 0; i < 16; i++) {
            double angle = (2 * Math.PI / 16) * i;
            double radius = 2.0 + (phase - 1) * 0.5;
            double x = loc.getX() + Math.cos(angle) * radius;
            double z = loc.getZ() + Math.sin(angle) * radius;
            Location pLoc = new Location(world, x, loc.getY(), z);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, pLoc, 2, 0.1, 0.5, 0.1, 0.02);
        }

        // Partículas sobre la cabeza según fase
        if (phase == 1) {
            world.spawnParticle(Particle.FLAME, loc.clone().add(0, 1, 0), 5, 0.5, 0.3, 0.5, 0.05);
        } else if (phase == 2) {
            world.spawnParticle(Particle.WITCH, loc.clone().add(0, 1, 0), 8, 0.5, 0.3, 0.5, 0.1);
        } else {
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1, 0), 12, 0.5, 0.5, 0.5, 0.2);
        }
    }

    private void summonMinions(Location center) {
        if (bossEntity == null || !bossEntity.isValid()) return;
        World world = center.getWorld();
        if (world == null) return;

        // Limpiar minions muertos
        minions.removeIf(m -> m.isDead() || !m.isValid());

        // Máximo 6 minions activos
        int minionCount = phase == 1 ? 2 : (phase == 2 ? 3 : 4);
        if (minions.size() >= minionCount * 2) return;

        EntityType[] types = {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.HUSK};
        for (int i = 0; i < minionCount; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double dist = 5 + random.nextDouble() * 5;
            Location mLoc = bossEntity.getLocation().clone().add(
                    Math.cos(angle) * dist, 0, Math.sin(angle) * dist);

            try {
                EntityType type = types[random.nextInt(types.length)];
                LivingEntity minion = (LivingEntity) world.spawnEntity(mLoc, type);
                minion.setCustomName(SmallCaps.convert("§c§lServidor del Guardián"));
                minion.setCustomNameVisible(true);
                minion.setMetadata("koth_minion", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                try {
                    minion.getAttribute(Attribute.MAX_HEALTH).setBaseValue(30.0 + phase * 10);
                    minion.setHealth(minion.getAttribute(Attribute.MAX_HEALTH).getValue());
                    minion.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(5.0 + phase * 2);
                } catch (Exception ignored) {}
                minion.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999, 1, false, false));
                minion.setRemoveWhenFarAway(false);
                minions.add(minion);

                world.spawnParticle(Particle.LARGE_SMOKE, mLoc.clone().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.05);
            } catch (Exception ignored) {}
        }

        world.playSound(bossEntity.getLocation(), Sound.ENTITY_ZOMBIE_AMBIENT, 0.8f, 0.5f);
    }

    private void summonEliteMobs(Location center) {
        World world = center.getWorld();
        if (world == null) return;

        EntityType[] elites = {EntityType.VINDICATOR, EntityType.PILLAGER, EntityType.WITCH};
        for (int i = 0; i < 3; i++) {
            double angle = (2 * Math.PI / 3) * i;
            Location eLoc = center.clone().add(Math.cos(angle) * 6, 0, Math.sin(angle) * 6);
            try {
                LivingEntity elite = (LivingEntity) world.spawnEntity(eLoc, elites[i]);
                elite.setCustomName(SmallCaps.convert("§4§l⚔ Élite del Guardián"));
                elite.setCustomNameVisible(true);
                elite.setGlowing(true);
                elite.setMetadata("koth_minion", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                try {
                    elite.getAttribute(Attribute.MAX_HEALTH).setBaseValue(80.0);
                    elite.setHealth(80.0);
                    elite.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(10.0);
                } catch (Exception ignored) {}
                elite.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999, 1, false, false));
                elite.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 999999, 0, false, false));
                minions.add(elite);

                world.strikeLightningEffect(eLoc);
            } catch (Exception ignored) {}
        }
        world.playSound(center, Sound.ENTITY_WITHER_SPAWN, 0.8f, 1.2f);
    }

    private void earthquakeStrike(Location center) {
        World world = center.getWorld();
        if (world == null) return;

        // Sacudir jugadores en radio 15
        for (Player p : world.getPlayers()) {
            if (p.getGameMode() != GameMode.SURVIVAL) continue;
            double dist = p.getLocation().distance(center);
            if (dist > 15) continue;
            double force = (1 - dist / 15.0) * 0.4;
            double vx = (random.nextDouble() - 0.5) * force * 2;
            double vz = (random.nextDouble() - 0.5) * force * 2;
            p.setVelocity(p.getVelocity().add(new org.bukkit.util.Vector(vx, force * 0.5, vz)));
        }

        // Crack en el suelo
        for (int i = 0; i < 30; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double radius = random.nextDouble() * 10;
            Location crack = center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            world.spawnParticle(Particle.FALLING_DUST, crack, 10, 0.5, 0.2, 0.5, 0.1,
                    Material.DEEPSLATE.createBlockData());
        }
        world.spawnParticle(Particle.EXPLOSION, center, 5, 1, 0.5, 1, 0);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.4f);
        world.playSound(center, Sound.BLOCK_STONE_BREAK, 1.0f, 0.6f);
    }

    private void fireStorm(Location center) {
        World world = center.getWorld();
        if (world == null) return;

        // Lluvia de rayos decorativos en espiral
        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                if (step >= 8) { cancel(); return; }
                double angle = (2 * Math.PI / 8) * step;
                double radius = 8.0;
                Location strikeLoc = center.clone().add(
                        Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
                world.strikeLightningEffect(strikeLoc);
                world.spawnParticle(Particle.FLAME, strikeLoc, 20, 0.5, 1.0, 0.5, 0.1);
                world.playSound(strikeLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.2f);
                step++;
            }
        }.runTaskTimer(plugin, 0L, 4L);

        for (Player p : world.getPlayers()) {
            if (p.getGameMode() != GameMode.SURVIVAL) continue;
            if (p.getLocation().distance(center) < 12) {
                p.sendMessage(SmallCaps.convert("§c§l⚡ §cEl Guardián lanza una tormenta de fuego!"));
            }
        }
    }

    private void deathNova(Location center) {
        World world = center.getWorld();
        if (world == null) return;

        // Explosión de partículas masiva
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, center.clone().add(0, 1, 0), 200, 2, 2, 2, 0.5);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 8, 2, 1, 2, 0);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, center.clone().add(0, 1, 0), 60, 3, 2, 3, 0.2);

        world.playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.4f);
        world.playSound(center, Sound.ENTITY_WITHER_DEATH, 0.6f, 0.5f);

        for (Player p : world.getPlayers()) {
            if (p.getGameMode() != GameMode.SURVIVAL) continue;
            double dist = p.getLocation().distance(center);
            if (dist < 8) {
                p.sendTitle( SmallCaps.convert("§4§l⚠"), SmallCaps.convert("§c¡NOVA DE MUERTE!"), 3, 20, 5);
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f);
            }
        }
    }

    // ==========================================
    // MUERTE DEL BOSS
    // ==========================================

    private void onBossDeath() {
        active = false;

        // Limpiar todos los minions
        for (LivingEntity minion : minions) {
            if (minion.isValid()) {
                minion.getWorld().spawnParticle(Particle.EXPLOSION,
                        minion.getLocation().add(0, 1, 0), 5, 0.3, 0.5, 0.3, 0);
                minion.remove();
            }
        }
        minions.clear();

        // Limpiar BossBar del boss
        if (bossBossBar != null) {
            bossBossBar.removeAll();
            bossBossBar = null;
        }

        // Anuncio épico
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle( SmallCaps.convert("§a§l🏆 ¡BOSS DERROTADO!"), SmallCaps.convert("§e¡El Guardián del KOTH ha caído!"), 10, 80, 20);
            p.sendMessage("");
            p.sendMessage(SmallCaps.convert("§a§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            p.sendMessage(SmallCaps.convert("§6§l  ⚔  ¡EL GUARDIÁN DEL KOTH HA SIDO DERROTADO!  ⚔"));
            p.sendMessage(SmallCaps.convert("§7  ¡Los jugadores han triunfado sobre el boss final!"));
            p.sendMessage(SmallCaps.convert("§a§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            p.sendMessage("");
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }

        if (onDeathCallback != null) {
            onDeathCallback.run();
        }

        plugin.getLogger().info("[KOTH] Boss derrotado.");
    }

    /**
     * Fuerza la eliminación del boss (cuando termina el KOTH).
     */
    public void forceKill() {
        if (bossTask != null) { bossTask.cancel(); bossTask = null; }
        if (bossBossBar != null) { bossBossBar.removeAll(); bossBossBar = null; }

        for (LivingEntity minion : minions) {
            if (minion.isValid()) minion.remove();
        }
        minions.clear();

        if (bossEntity != null && bossEntity.isValid()) {
            bossEntity.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER,
                    bossEntity.getLocation().add(0, 1, 0), 10, 1, 1, 1, 0);
            bossEntity.remove();
        }

        bossEntity = null;
        active = false;
    }

    // ==========================================
    // HELPERS
    // ==========================================

    /**
     * Busca una ubicación en el borde de la zona para spawnear el boss.
     */
    private Location findBossSpawnLocation(Location center, KothZone zone) {
        if (zone == null) return null;
        World world = center.getWorld();
        if (world == null) return null;

        // Spawnear en el borde norte de la zona (en Z mínima)
        int bossX = (zone.getX1() + zone.getX2()) / 2;
        int bossZ = zone.getZ1() - 5; // 5 bloques fuera de la zona
        int bossY = (int) center.getY();

        // Buscar suelo sólido
        for (int dy = 0; dy <= 10; dy++) {
            Location ground = new Location(world, bossX, bossY - dy, bossZ);
            if (ground.getBlock().getType().isSolid()) {
                return new Location(world, bossX + 0.5, bossY - dy + 1, bossZ + 0.5);
            }
        }

        return center.clone().add(0, 1, 0);
    }

    public BossBar getBossBossBar() { return bossBossBar; }
}
