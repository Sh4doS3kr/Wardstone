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
 * Boss de la Misión 9: "El Centinela del Origen"
 *
 * Contraparte luminosa del Heraldo del Hielo.
 * Antes del Vacío y del Frío, existía la Luz Primordial.
 * El Centinela es su guardián, y solo entregará la Estrella del Origen
 * a quien demuestre ser digno de portar ambas fuerzas.
 *
 * 2500 HP, 3 fases: Aurora, Supernova, Singularidad.
 * Ataques de luz, rayos solares, novas, prismas, y explosiones estelares.
 * Recompensa: Estrella del Origen (hacha White Hole).
 */
public class OriginBoss {

    private final CoreProtectPlugin plugin;
    private final ShadowHunterManager manager;
    private final Player player;
    private final ShadowHunterNPC npc;

    private boolean active = false;
    private Zombie bossEntity;
    private BossBar bossBar;
    private BukkitRunnable mainLoop;
    private int tickCount = 0;
    private int phase = 1;
    private long lastDamageTime;
    private Location arenaCenter;

    private final List<Entity> summonedMobs = new ArrayList<>();
    private final Set<UUID> spawnedEntityIds = new HashSet<>();

    // Ability cooldowns (in ticks)
    private int solarBeamCD = 0;
    private int novaCD = 0;
    private int prismCD = 0;
    private int summonCD = 0;
    private int blindingFlashCD = 0;
    private int starRainCD = 0;
    private int solarEclipseCD = 0;
    private int photonLanceCD = 0;
    private int singularityPulseCD = 0;

    private final String[] phase1Taunts = {
            "§e\"La Luz existía antes que la oscuridad... antes que el Vacío.\"",
            "§e\"Tus reliquias son fragmentos de destrucción. Yo soy creación pura.\"",
            "§e\"Cada estrella que ves en el cielo... lleva mi esencia.\"",
            "§e\"No te odio, cazador. Solo necesito saber si eres digno.\""
    };
    private final String[] phase2Taunts = {
            "§6\"¡Siente el calor de mil soles!\"",
            "§6\"¡La Luz no destruye! ¡LA LUZ TRANSFORMA!\"",
            "§6\"¡Cada rayo que esquivas, te hace más fuerte!\"",
            "§6\"¡Tu determinación brilla casi tanto como yo!\""
    };
    private final String[] phase3Taunts = {
            "§f\"¡SINGULARIDAD! ¡EL ORIGEN DE TODO!\"",
            "§f\"¡SOY LA PRIMERA CHISPA DEL UNIVERSO!\"",
            "§f\"¡NADA PUEDE APAGAR LA LUZ PRIMORDIAL!\"",
            "§6\"Tu... tu luz... es tan brillante como la mía... imposible...\""
    };
    private int tauntIndex = 0;

    public OriginBoss(CoreProtectPlugin plugin, ShadowHunterManager manager, Player player, ShadowHunterNPC npc) {
        this.plugin = plugin;
        this.manager = manager;
        this.player = player;
        this.npc = npc;
    }

    public void start() {
        active = true;
        lastDamageTime = System.currentTimeMillis();

        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§e§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        player.sendMessage(SmallCaps.convert("  §e§l☆ EL CENTINELA DEL ORIGEN ☆"));
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("  §7Antes del Vacío, antes del Frío,"));
        player.sendMessage(SmallCaps.convert("  §7existía la Luz Primordial. Su guardián"));
        player.sendMessage(SmallCaps.convert("  §7ha esperado eones por alguien digno."));
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("  §c§l⚠ ¡2048 HP! ¡3 Fases de luz!"));
        player.sendMessage(SmallCaps.convert("  §e⚠ Si mueres, respawneas aquí."));
        player.sendMessage(SmallCaps.convert("§e§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        player.sendMessage("");

        player.sendTitle(SmallCaps.convert("§e§l☆ FASE 1 ☆"), SmallCaps.convert("§6La Aurora despierta..."), 10, 80, 20);
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.8f, 1.5f);

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
        arenaCenter = spawnLoc.clone();

        World world = spawnLoc.getWorld();
        world.spawnParticle(Particle.END_ROD, spawnLoc.clone().add(0, 1, 0), 100, 3, 3, 3, 0.2);
        world.spawnParticle(Particle.DUST, spawnLoc.clone().add(0, 2, 0), 60, 2, 2, 2, 0,
                new Particle.DustOptions(Color.fromRGB(255, 255, 200), 3.0f));
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, spawnLoc.clone().add(0, 2, 0), 3, 0, 0, 0, 0);
        world.playSound(spawnLoc, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 2.0f);
        world.playSound(spawnLoc, Sound.BLOCK_END_PORTAL_SPAWN, 0.5f, 2.0f);

        bossBar = Bukkit.createBossBar("§e§l☆ Centinela del Origen ☆ §7[FASE 1]",
                BarColor.YELLOW, BarStyle.SEGMENTED_10);
        bossBar.addPlayer(player);

        bossEntity = player.getWorld().spawn(spawnLoc, Zombie.class, z -> {
            z.setCustomName(SmallCaps.convert("§e§l☆ Centinela del Origen §e§l☆"));
            z.setCustomNameVisible(true);
            z.setAdult();
            z.setCanPickupItems(false);
            z.setRemoveWhenFarAway(false);
            z.setPersistent(true);
            z.addScoreboardTag("wardstone_mission_mob");
            z.addScoreboardTag("origin_boss");

            z.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(2048);
            z.setHealth(2048);
            z.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(16);
            z.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.30);
            z.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(0.95);
            z.getAttribute(Attribute.GENERIC_ARMOR).setBaseValue(18);

            z.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 99999, 0, false, false));

            equipBoss(z);
        });

        spawnedEntityIds.add(bossEntity.getUniqueId());
        manager.markDirectRespawn(player.getUniqueId(), arenaCenter.clone());
    }

    private void equipBoss(Zombie z) {
        Color lightGold = Color.fromRGB(255, 230, 150);
        Color pureWhite = Color.fromRGB(255, 255, 230);

        ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
        LeatherArmorMeta hm = (LeatherArmorMeta) helmet.getItemMeta();
        hm.setColor(pureWhite);
        helmet.setItemMeta(hm);

        ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta cm = (LeatherArmorMeta) chest.getItemMeta();
        cm.setColor(lightGold);
        chest.setItemMeta(cm);

        ItemStack legs = new ItemStack(Material.LEATHER_LEGGINGS);
        LeatherArmorMeta lm = (LeatherArmorMeta) legs.getItemMeta();
        lm.setColor(pureWhite);
        legs.setItemMeta(lm);

        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);
        LeatherArmorMeta bm = (LeatherArmorMeta) boots.getItemMeta();
        bm.setColor(lightGold);
        boots.setItemMeta(bm);

        z.getEquipment().setHelmet(helmet);
        z.getEquipment().setChestplate(chest);
        z.getEquipment().setLeggings(legs);
        z.getEquipment().setBoots(boots);
        z.getEquipment().setItemInMainHand(new ItemStack(Material.GOLDEN_AXE));
    }

    // ==================== MAIN LOOP ====================

    private void startMainLoop() {
        mainLoop = new BukkitRunnable() {
            @Override
            public void run() {
                if (!active || bossEntity == null || bossEntity.isDead() || !player.isOnline()) {
                    cancel();
                    if (active && bossEntity != null && bossEntity.isDead()) {
                        onBossDefeated();
                    }
                    return;
                }

                tickCount++;
                lastDamageTime = System.currentTimeMillis();

                double hp = bossEntity.getHealth();
                double maxHp = bossEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
                bossBar.setProgress(Math.max(0, Math.min(1, hp / maxHp)));

                // Phase transitions
                double pct = hp / maxHp;
                if (phase == 1 && pct <= 0.60) {
                    phase = 2;
                    enterPhase2();
                } else if (phase == 2 && pct <= 0.25) {
                    phase = 3;
                    enterPhase3();
                }

                // Ambient light particles around boss
                if (tickCount % 5 == 0) {
                    Location bossLoc = bossEntity.getLocation().add(0, 1.5, 0);
                    bossLoc.getWorld().spawnParticle(Particle.DUST, bossLoc, 3, 0.5, 0.5, 0.5, 0,
                            new Particle.DustOptions(Color.fromRGB(255, 255, 200), 1.0f));
                    if (phase >= 2) {
                        bossLoc.getWorld().spawnParticle(Particle.END_ROD, bossLoc, 2, 0.5, 0.5, 0.5, 0.02);
                    }
                }

                // Decrease CDs
                if (solarBeamCD > 0) solarBeamCD--;
                if (novaCD > 0) novaCD--;
                if (prismCD > 0) prismCD--;
                if (summonCD > 0) summonCD--;
                if (blindingFlashCD > 0) blindingFlashCD--;
                if (starRainCD > 0) starRainCD--;
                if (solarEclipseCD > 0) solarEclipseCD--;
                if (photonLanceCD > 0) photonLanceCD--;
                if (singularityPulseCD > 0) singularityPulseCD--;

                // Taunts every ~12 seconds
                if (tickCount % 240 == 0) {
                    String[] taunts = phase == 3 ? phase3Taunts : phase == 2 ? phase2Taunts : phase1Taunts;
                    player.sendMessage(SmallCaps.convert("§e§l☆ " + taunts[tauntIndex % taunts.length]));
                    tauntIndex++;
                }

                // Phase 1 abilities
                if (phase >= 1) {
                    if (solarBeamCD <= 0 && tickCount % 60 == 10) {
                        solarBeam();
                        solarBeamCD = 100;
                    }
                    if (novaCD <= 0 && tickCount % 80 == 30) {
                        lightNova();
                        novaCD = 120;
                    }
                }
                // Phase 2 abilities
                if (phase >= 2) {
                    if (prismCD <= 0 && tickCount % 100 == 50) {
                        prismBarrage();
                        prismCD = 140;
                    }
                    if (blindingFlashCD <= 0 && tickCount % 120 == 70) {
                        blindingFlash();
                        blindingFlashCD = 160;
                    }
                    if (summonCD <= 0 && tickCount % 200 == 100) {
                        summonLightMinions();
                        summonCD = 300;
                    }
                }
                // Phase 3 abilities
                if (phase >= 3) {
                    if (starRainCD <= 0 && tickCount % 140 == 0) {
                        starRain();
                        starRainCD = 200;
                    }
                    if (photonLanceCD <= 0 && tickCount % 100 == 60) {
                        photonLance();
                        photonLanceCD = 150;
                    }
                    if (singularityPulseCD <= 0 && tickCount % 180 == 90) {
                        singularityPulse();
                        singularityPulseCD = 250;
                    }
                }

                // Chase player if too far
                if (bossEntity.getLocation().distanceSquared(player.getLocation()) > 900) {
                    bossEntity.teleport(player.getLocation().add(
                            player.getLocation().getDirection().normalize().multiply(3)));
                    bossEntity.getWorld().spawnParticle(Particle.END_ROD,
                            bossEntity.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
                }
            }
        };
        mainLoop.runTaskTimer(plugin, 0, 1);
    }

    // ==================== ABILITIES ====================

    /**
     * Solar Beam: Fires a beam of light at the player.
     */
    private void solarBeam() {
        if (!active || bossEntity == null || bossEntity.isDead()) return;
        World world = bossEntity.getWorld();
        Location start = bossEntity.getEyeLocation();
        Vector dir = player.getLocation().add(0, 1, 0).toVector().subtract(start.toVector()).normalize();

        player.sendMessage(SmallCaps.convert("§e§l☆ §c¡RAYO SOLAR! §7¡Apártate!"));
        world.playSound(start, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.5f);

        new BukkitRunnable() {
            double dist = 0;
            Location current = start.clone();
            @Override
            public void run() {
                if (dist > 30 || !active || bossEntity == null || bossEntity.isDead()) {
                    cancel();
                    return;
                }
                current.add(dir.clone().multiply(2));
                dist += 2;

                world.spawnParticle(Particle.DUST, current, 8, 0.2, 0.2, 0.2, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 255, 100), 2.0f));
                world.spawnParticle(Particle.END_ROD, current, 2, 0.1, 0.1, 0.1, 0.01);

                for (Entity e : world.getNearbyEntities(current, 2, 2, 2)) {
                    if (e.equals(player)) {
                        double dmg = phase == 3 ? 16 : phase == 2 ? 12 : 8;
                        player.damage(dmg, bossEntity);
                        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0));
                        cancel();
                        return;
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    /**
     * Light Nova: Expanding ring of light that damages and pushes away.
     */
    private void lightNova() {
        if (!active || bossEntity == null || bossEntity.isDead()) return;
        World world = bossEntity.getWorld();
        Location center = bossEntity.getLocation().add(0, 1, 0);

        player.sendMessage(SmallCaps.convert("§e§l☆ §c¡NOVA DE LUZ! §7¡Aléjate del Centinela!"));
        world.playSound(center, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.5f, 1.0f);

        new BukkitRunnable() {
            double radius = 1;
            @Override
            public void run() {
                if (radius > 12 || !active) {
                    cancel();
                    return;
                }
                for (int i = 0; i < 16; i++) {
                    double angle = (Math.PI * 2 / 16) * i;
                    Location pLoc = center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
                    world.spawnParticle(Particle.DUST, pLoc, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(255, 255, 150), 2.0f));
                }

                if (player.getLocation().distanceSquared(center) <= radius * radius + 4
                        && player.getLocation().distanceSquared(center) >= (radius - 2) * (radius - 2)) {
                    double dmg = phase == 3 ? 14 : phase == 2 ? 10 : 7;
                    player.damage(dmg, bossEntity);
                    Vector push = player.getLocation().toVector().subtract(center.toVector()).normalize().multiply(1.5);
                    push.setY(0.5);
                    player.setVelocity(push);
                }
                radius += 1.5;
            }
        }.runTaskTimer(plugin, 0, 2);
    }

    /**
     * Prism Barrage: Multiple light projectiles fired in a spread.
     */
    private void prismBarrage() {
        if (!active || bossEntity == null || bossEntity.isDead()) return;
        World world = bossEntity.getWorld();
        Location start = bossEntity.getEyeLocation();

        player.sendMessage(SmallCaps.convert("§6§l☆ §c¡RÁFAGA DE PRISMAS! §7¡Esquiva los destellos!"));
        world.playSound(start, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.5f, 1.5f);

        int projectiles = phase == 3 ? 7 : 5;
        Vector baseDir = player.getLocation().add(0, 1, 0).toVector().subtract(start.toVector()).normalize();

        for (int i = 0; i < projectiles; i++) {
            double spread = (i - projectiles / 2.0) * 0.15;
            Vector dir = baseDir.clone().rotateAroundY(spread).normalize();
            int delay = i * 3;

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!active || bossEntity == null || bossEntity.isDead()) return;
                new BukkitRunnable() {
                    double dist = 0;
                    Location current = start.clone();
                    @Override
                    public void run() {
                        if (dist > 25 || !active) { cancel(); return; }
                        current.add(dir.clone().multiply(1.5));
                        dist += 1.5;

                        Color c = Math.random() > 0.5 ? Color.fromRGB(255, 215, 0) : Color.fromRGB(255, 255, 200);
                        world.spawnParticle(Particle.DUST, current, 3, 0.1, 0.1, 0.1, 0,
                                new Particle.DustOptions(c, 1.2f));

                        for (Entity e : world.getNearbyEntities(current, 1.5, 1.5, 1.5)) {
                            if (e.equals(player)) {
                                player.damage(phase == 3 ? 8 : 5, bossEntity);
                                cancel();
                                return;
                            }
                        }
                    }
                }.runTaskTimer(plugin, 0, 1);
            }, delay);
        }
    }

    /**
     * Blinding Flash: Blinds the player and deals damage.
     */
    private void blindingFlash() {
        if (!active || bossEntity == null || bossEntity.isDead()) return;
        World world = bossEntity.getWorld();
        Location bossLoc = bossEntity.getLocation();

        player.sendMessage(SmallCaps.convert("§f§l☆ §c¡DESTELLO CEGADOR!"));
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, bossLoc.clone().add(0, 2, 0), 5, 0, 0, 0, 0);
        world.spawnParticle(Particle.END_ROD, bossLoc.clone().add(0, 2, 0), 80, 4, 4, 4, 0.3);
        world.playSound(bossLoc, Sound.ENTITY_FIREWORK_ROCKET_BLAST_FAR, 2.0f, 1.0f);

        if (player.getLocation().distanceSquared(bossLoc) < 225) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, phase == 3 ? 80 : 50, 0));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
            player.damage(phase == 3 ? 10 : 6, bossEntity);
        }
    }

    /**
     * Summon Light Minions: Spawns light-themed mobs.
     */
    private void summonLightMinions() {
        summonedMobs.removeIf(e -> e == null || e.isDead());
        if (summonedMobs.size() >= 4) return;

        World world = bossEntity.getWorld();
        Location bossLoc = bossEntity.getLocation();
        int count = phase == 3 ? 3 : 2;

        player.sendMessage(SmallCaps.convert("§e§l☆ §7¡Centinelas de Luz emergen del brillo!"));

        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2 / count) * i;
            Location spawnLoc = bossLoc.clone().add(Math.cos(angle) * 3, 0, Math.sin(angle) * 3);
            spawnLoc.setY(bossLoc.getY());

            world.spawnParticle(Particle.END_ROD, spawnLoc.clone().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0.1);

            Skeleton minion = world.spawn(spawnLoc, Skeleton.class, s -> {
                s.setCustomName(SmallCaps.convert("§e☆ Centinela Menor"));
                s.setCustomNameVisible(true);
                s.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(60);
                s.setHealth(60);
                s.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(8);
                s.setRemoveWhenFarAway(false);
                s.setPersistent(true);
                s.addScoreboardTag("wardstone_mission_mob");
                s.addScoreboardTag("origin_minion");
                s.setTarget(player);
                s.getEquipment().setItemInMainHand(new ItemStack(Material.GOLDEN_SWORD));

                Color gold = Color.fromRGB(255, 215, 100);
                ItemStack helm = new ItemStack(Material.LEATHER_HELMET);
                LeatherArmorMeta hm = (LeatherArmorMeta) helm.getItemMeta();
                hm.setColor(gold);
                helm.setItemMeta(hm);
                s.getEquipment().setHelmet(helm);

                ItemStack ch = new ItemStack(Material.LEATHER_CHESTPLATE);
                LeatherArmorMeta cm = (LeatherArmorMeta) ch.getItemMeta();
                cm.setColor(gold);
                ch.setItemMeta(cm);
                s.getEquipment().setChestplate(ch);
            });
            summonedMobs.add(minion);
            spawnedEntityIds.add(minion.getUniqueId());
        }
    }

    /**
     * Star Rain: Stars fall from the sky around the player (Phase 3).
     */
    private void starRain() {
        if (!active || bossEntity == null || bossEntity.isDead()) return;
        World world = bossEntity.getWorld();

        player.sendMessage(SmallCaps.convert("§f§l☆ §c¡LLUVIA DE ESTRELLAS! §7¡No pares de moverte!"));
        world.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1.5f, 1.5f);

        int meteors = phase == 3 ? 12 : 8;
        new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                if (count >= meteors || !active || bossEntity == null || bossEntity.isDead()) {
                    cancel();
                    return;
                }

                Location target = player.getLocation().clone().add(
                        (Math.random() - 0.5) * 10, 0, (Math.random() - 0.5) * 10);
                Location from = target.clone().add(0, 15, 0);

                // Falling star effect
                new BukkitRunnable() {
                    Location pos = from.clone();
                    @Override
                    public void run() {
                        pos.add(0, -1.5, 0);
                        world.spawnParticle(Particle.DUST, pos, 5, 0.2, 0.2, 0.2, 0,
                                new Particle.DustOptions(Color.fromRGB(255, 255, 100), 2.0f));
                        world.spawnParticle(Particle.END_ROD, pos, 1, 0, 0, 0, 0.01);

                        if (pos.getY() <= target.getY()) {
                            cancel();
                            // Impact
                            world.spawnParticle(Particle.TOTEM_OF_UNDYING, target, 1, 0, 0, 0, 0);
                            world.spawnParticle(Particle.DUST, target, 20, 2, 1, 2, 0,
                                    new Particle.DustOptions(Color.fromRGB(255, 215, 0), 2.0f));
                            world.playSound(target, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.5f);

                            if (player.getLocation().distanceSquared(target) < 12) {
                                player.damage(phase == 3 ? 10 : 7, bossEntity);
                                Vector kb = player.getLocation().toVector().subtract(target.toVector()).normalize().multiply(0.8);
                                kb.setY(0.4);
                                player.setVelocity(kb);
                            }
                        }
                    }
                }.runTaskTimer(plugin, 0, 1);

                count++;
            }
        }.runTaskTimer(plugin, 0, 6);
    }

    /**
     * Photon Lance: A focused beam that follows the player briefly.
     */
    private void photonLance() {
        if (!active || bossEntity == null || bossEntity.isDead()) return;
        World world = bossEntity.getWorld();

        player.sendMessage(SmallCaps.convert("§6§l☆ §c¡LANZA DE FOTONES! §7¡Corre!"));
        world.playSound(bossEntity.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 2.0f, 2.0f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 40 || !active || bossEntity == null || bossEntity.isDead()) {
                    cancel();
                    return;
                }
                Location start = bossEntity.getEyeLocation();
                Vector dir = player.getLocation().add(0, 1, 0).toVector().subtract(start.toVector()).normalize();

                for (double d = 0; d < 20; d += 0.5) {
                    Location point = start.clone().add(dir.clone().multiply(d));
                    world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(255, 200, 50), 1.5f));
                }

                if (ticks % 5 == 0) {
                    for (double d = 0; d < 20; d += 1) {
                        Location point = start.clone().add(dir.clone().multiply(d));
                        for (Entity e : world.getNearbyEntities(point, 1.2, 1.2, 1.2)) {
                            if (e.equals(player)) {
                                player.damage(5, bossEntity);
                                break;
                            }
                        }
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 10, 1);
    }

    /**
     * Singularity Pulse: Creates a radiant singularity that pulls then explodes (Phase 3).
     */
    private void singularityPulse() {
        if (!active || bossEntity == null || bossEntity.isDead()) return;
        World world = bossEntity.getWorld();
        Location center = player.getLocation().clone();

        player.sendMessage(SmallCaps.convert("§f§l☆ §c¡PULSO DE SINGULARIDAD! §7¡Una estrella colapsa sobre ti!"));
        world.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.5f, 2.0f);

        // Pull phase (2 seconds)
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 40 || !active) {
                    cancel();

                    // Explosion
                    world.spawnParticle(Particle.TOTEM_OF_UNDYING, center, 3, 0, 0, 0, 0);
                    world.spawnParticle(Particle.END_ROD, center, 150, 5, 5, 5, 0.5);
                    world.spawnParticle(Particle.TOTEM_OF_UNDYING, center, 80, 3, 3, 3, 0.5);
                    world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.5f);

                    if (player.getLocation().distanceSquared(center) < 64) {
                        player.damage(18, bossEntity);
                        Vector push = player.getLocation().toVector().subtract(center.toVector()).normalize().multiply(2.5);
                        push.setY(1.0);
                        player.setVelocity(push);
                    }
                    return;
                }

                // Visual — collapsing ring
                double r = 8 - (ticks / 5.0);
                if (r < 0.5) r = 0.5;
                for (int i = 0; i < 12; i++) {
                    double angle = Math.random() * Math.PI * 2;
                    world.spawnParticle(Particle.DUST,
                            center.clone().add(Math.cos(angle) * r, Math.random() * 3, Math.sin(angle) * r),
                            1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(255, 255, 255), 2.0f));
                }
                world.spawnParticle(Particle.DUST, center, 5, 0.3, 0.3, 0.3, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 215, 0), 3.0f));

                // Pull player toward center
                if (player.getLocation().distanceSquared(center) > 4) {
                    Vector pull = center.toVector().subtract(player.getLocation().toVector()).normalize().multiply(0.25);
                    player.setVelocity(player.getVelocity().add(pull));
                }

                if (ticks % 10 == 0) {
                    world.playSound(center, Sound.BLOCK_BEACON_AMBIENT, 1.0f, 2.0f);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    // ==================== PHASE TRANSITIONS ====================

    private void enterPhase2() {
        bossBar.setTitle("§6§l☆ Centinela del Origen ☆ §7[FASE 2]");
        bossBar.setColor(BarColor.PINK);
        player.sendTitle(SmallCaps.convert("§6§l☆ FASE 2 ☆"), SmallCaps.convert("§e¡Supernova!"), 10, 60, 20);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1.5f);

        World world = bossEntity.getWorld();
        Location loc = bossEntity.getLocation();
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 2, 0), 5, 0, 0, 0, 0);
        world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 2, 0), 100, 5, 5, 5, 0.3);

        bossEntity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(20);
        bossEntity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.34);
        bossEntity.getAttribute(Attribute.GENERIC_ARMOR).setBaseValue(22);
        bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 99999, 1, false, false));

        player.sendMessage(SmallCaps.convert("§6§l☆ §c¡El Centinela arde con la fuerza de una supernova!"));
    }

    private void enterPhase3() {
        bossBar.setTitle("§f§l☆ Centinela del Origen ☆ §7[FASE 3]");
        bossBar.setColor(BarColor.WHITE);
        player.sendTitle(SmallCaps.convert("§f§l☆ FASE 3 ☆"), SmallCaps.convert("§e¡SINGULARIDAD!"), 10, 60, 20);
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.8f, 2.0f);

        World world = bossEntity.getWorld();
        Location loc = bossEntity.getLocation();
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 2, 0), 10, 0, 0, 0, 0);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 2, 0), 200, 5, 5, 5, 1.0);
        world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 2, 0), 150, 5, 5, 5, 0.5);

        bossEntity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(24);
        bossEntity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.38);
        bossEntity.getAttribute(Attribute.GENERIC_ARMOR).setBaseValue(25);
        bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 99999, 2, false, false));
        bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 99999, 1, false, false));

        player.sendMessage(SmallCaps.convert("§f§l☆ §c¡SINGULARIDAD! ¡El Centinela desata la Luz Primordial!"));
    }

    // ==================== BOSS DEFEATED ====================

    private void onBossDefeated() {
        active = false;

        if (mainLoop != null) {
            try { mainLoop.cancel(); } catch (Exception ignored) {}
        }

        if (bossBar != null) {
            bossBar.removeAll();
        }

        for (Entity e : summonedMobs) {
            if (e != null && !e.isDead()) e.remove();
        }
        summonedMobs.clear();

        if (player.isOnline()) {
            World world = player.getWorld();
            Location loc = bossEntity != null ? bossEntity.getLocation() : player.getLocation();

            // Light explosion
            world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 2, 0), 300, 5, 5, 5, 0.5);
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 2, 0), 200, 3, 3, 3, 1.0);
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 2, 0), 10, 0, 0, 0, 0);
            world.spawnParticle(Particle.DUST, loc.clone().add(0, 2, 0), 100, 5, 5, 5, 0,
                    new Particle.DustOptions(Color.fromRGB(255, 255, 200), 3.0f));

            world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.5f);
            world.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            world.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.5f);

            player.sendTitle(SmallCaps.convert("§e§l☆ DERROTADO ☆"), SmallCaps.convert("§6La Luz Primordial se entrega..."), 10, 100, 30);

            // Dialogue after defeat
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                player.sendMessage("");
                player.sendMessage(SmallCaps.convert("§e§l☆ §6El Centinela se disuelve en partículas de luz."));
                player.sendMessage(SmallCaps.convert("§e§o\"Eres... digno. Más que digno.\""));
                player.sendMessage(SmallCaps.convert("§e§o\"La Devoradora de Almas y la Estrella del Origen...\""));
                player.sendMessage(SmallCaps.convert("§e§o\"Oscuridad y Luz. Destrucción y Creación. En equilibrio.\""));
                player.sendMessage(SmallCaps.convert("§7§o*De la luz que queda del Centinela,"));
                player.sendMessage(SmallCaps.convert("§7§o emerge un hacha radiante, pulsando con"));
                player.sendMessage(SmallCaps.convert("§7§o la energía de la primera estrella del universo.*"));
                player.sendMessage("");
            }, 60L);

            // Complete quest and give reward
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                manager.onQuest9BossDefeated(player);
            }, 140L);
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
    }
}
