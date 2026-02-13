package com.moonlight.coreprotect.rpg.abilities;

import com.moonlight.coreprotect.rpg.RPGPlayer;
import com.moonlight.coreprotect.rpg.RPGSubclass;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class AbilityRegistry {

    private final Map<String, Ability> abilities = new LinkedHashMap<>();
    private Plugin plugin;

    public void registerAll(Plugin plugin) {
        this.plugin = plugin;

        // ========== WARRIOR - TANK ==========
        register(new SimpleAbility("taunt", "Provocar", "Atrae a todos los mobs cercanos hacia ti",
                Material.SHIELD, RPGSubclass.TANK, 20, 15000, 5, 1, ChatColor.DARK_RED) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                for (Entity e : player.getNearbyEntities(10, 10, 10)) {
                    if (e instanceof Mob) {
                        ((Mob) e).setTarget(player);
                    }
                }
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.5f, 0.8f);
                spawnCircleParticles(player.getLocation(), Particle.ANGRY_VILLAGER, 3.0, 20);
            }
        });
        register(new SimpleAbility("shield_wall", "Muro de Escudos", "Reduces daño 80% durante 5s",
                Material.IRON_CHESTPLATE, RPGSubclass.TANK, 40, 30000, 15, 1, ChatColor.GRAY) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 3));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 2f, 0.5f);
                spawnCircleParticles(player.getLocation(), Particle.ENCHANT, 2.0, 30);
            }
        });
        register(new SimpleAbility("last_stand", "Última Posición", "Te cura al 50% HP y resiste la muerte 3s",
                Material.TOTEM_OF_UNDYING, RPGSubclass.TANK, 60, 120000, 30, 2, ChatColor.GOLD) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                double maxHp = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                player.setHealth(Math.min(maxHp, maxHp * 0.5));
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 60, 4));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 2));
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.5f, 1.0f);
                spawnCircleParticles(player.getLocation(), Particle.TOTEM_OF_UNDYING, 2.0, 50);
            }
        });

        // ========== WARRIOR - DPS ==========
        register(new SimpleAbility("whirlwind", "Torbellino", "Daña a todos los enemigos cercanos en área",
                Material.IRON_SWORD, RPGSubclass.DPS, 25, 8000, 5, 1, ChatColor.RED) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                double dmg = 4 + rp.getStats().getStrength() * 0.3;
                for (Entity e : player.getNearbyEntities(4, 2, 4)) {
                    if (e instanceof LivingEntity && e != player) {
                        ((LivingEntity) e).damage(dmg, player);
                    }
                }
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.5f, 1.2f);
                spawnCircleParticles(player.getLocation(), Particle.SWEEP_ATTACK, 4.0, 20);
            }
        });
        register(new SimpleAbility("execute", "Ejecutar", "Golpe letal: +200% daño a enemigos bajo 30% HP",
                Material.NETHERITE_SWORD, RPGSubclass.DPS, 30, 12000, 15, 1, ChatColor.DARK_RED) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 60, 3));
                player.setMetadata("rpg_execute", new FixedMetadataValue(plugin, true));
                new BukkitRunnable() { public void run() { player.removeMetadata("rpg_execute", plugin); } }
                    .runTaskLater(plugin, 60L);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_BREAK_BLOCK, 0.8f, 1.5f);
                player.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, player.getLocation().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0.1);
            }
        });
        register(new SimpleAbility("battle_cry", "Grito de Guerra", "Buff de fuerza y velocidad para ti y aliados cercanos",
                Material.GOAT_HORN, RPGSubclass.DPS, 35, 45000, 25, 2, ChatColor.RED) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                for (Entity e : player.getNearbyEntities(10, 5, 10)) {
                    if (e instanceof Player) {
                        Player p = (Player) e;
                        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 1));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1));
                        p.sendMessage(ChatColor.RED + "⚔ ¡El grito de guerra de " + player.getName() + " te fortalece!");
                    }
                }
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 2));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1));
                player.getWorld().playSound(player.getLocation(), Sound.EVENT_RAID_HORN, 2f, 1.2f);
                spawnCircleParticles(player.getLocation(), Particle.FLAME, 5.0, 30);
            }
        });

        // ========== WARRIOR - BERSERKER ==========
        register(new SimpleAbility("rage_mode", "Modo Furia", "Menos vida = más daño. +50% daño, -20% defensa 10s",
                Material.NETHERITE_AXE, RPGSubclass.BERSERKER, 30, 25000, 5, 1, ChatColor.DARK_RED) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 2));
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 200, 1));
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1.5f);
                player.getWorld().spawnParticle(Particle.LAVA, player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0);
            }
        });
        register(new SimpleAbility("bloodthirst", "Sed de Sangre", "Cura un 15% del daño que hagas durante 8s",
                Material.REDSTONE, RPGSubclass.BERSERKER, 25, 20000, 15, 1, ChatColor.DARK_RED) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                player.setMetadata("rpg_lifesteal", new FixedMetadataValue(plugin, 0.15));
                new BukkitRunnable() { public void run() { player.removeMetadata("rpg_lifesteal", plugin); } }
                    .runTaskLater(plugin, 160L);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.8f, 1.5f);
                player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0,
                        new Particle.DustOptions(Color.RED, 1.5f));
            }
        });
        register(new SimpleAbility("unstoppable", "Imparable", "Inmune a knockback y slowness 10s, +30% velocidad",
                Material.NETHER_STAR, RPGSubclass.BERSERKER, 50, 60000, 30, 2, ChatColor.DARK_RED) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 2));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 200, 0));
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 2f);
                spawnCircleParticles(player.getLocation(), Particle.SOUL_FIRE_FLAME, 3.0, 30);
            }
        });

        // ========== MAGE - ELEMENTAL ==========
        register(new SimpleAbility("fireball", "Bola de Fuego", "Lanza una bola de fuego explosiva",
                Material.FIRE_CHARGE, RPGSubclass.ELEMENTAL, 25, 5000, 5, 1, ChatColor.RED) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                Fireball fb = player.launchProjectile(Fireball.class);
                fb.setYield(1.5f);
                fb.setIsIncendiary(true);
                fb.setMetadata("rpg_fireball", new FixedMetadataValue(plugin, rp.getStats().getIntelligence()));
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.5f, 1f);
            }
        });
        register(new SimpleAbility("lightning_chain", "Cadena de Rayos", "Invoca rayos sobre enemigos cercanos",
                Material.TRIDENT, RPGSubclass.ELEMENTAL, 40, 15000, 15, 1, ChatColor.AQUA) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                int count = 0;
                double dmg = 6 + rp.getStats().getIntelligence() * 0.4;
                for (Entity e : player.getNearbyEntities(8, 5, 8)) {
                    if (e instanceof LivingEntity && e != player && count < 3) {
                        player.getWorld().strikeLightningEffect(e.getLocation());
                        ((LivingEntity) e).damage(dmg, player);
                        count++;
                    }
                }
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 1.2f);
            }
        });
        register(new SimpleAbility("ice_prison", "Prisión de Hielo", "Congela a enemigos cercanos durante 4s",
                Material.PACKED_ICE, RPGSubclass.ELEMENTAL, 45, 25000, 25, 2, ChatColor.AQUA) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                for (Entity e : player.getNearbyEntities(6, 3, 6)) {
                    if (e instanceof LivingEntity && e != player) {
                        LivingEntity le = (LivingEntity) e;
                        le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 100));
                        le.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 80, 5));
                        le.getWorld().spawnParticle(Particle.SNOWFLAKE, le.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.02);
                    }
                }
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.5f, 0.5f);
                spawnCircleParticles(player.getLocation(), Particle.SNOWFLAKE, 6.0, 40);
            }
        });

        // ========== MAGE - HEALER ==========
        register(new SimpleAbility("holy_light", "Luz Sagrada", "Cura 6 corazones a ti o un aliado cercano",
                Material.GLOWSTONE_DUST, RPGSubclass.HEALER, 30, 8000, 5, 1, ChatColor.WHITE) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                double heal = 12 + rp.getStats().getIntelligence() * 0.3;
                double maxHp = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                player.setHealth(Math.min(maxHp, player.getHealth() + heal));
                player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 2, 0), 10, 0.5, 0.5, 0.5, 0);
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.5f, 2f);
            }
        });
        register(new SimpleAbility("group_heal", "Curación Grupal", "Cura a todos los aliados en 8 bloques",
                Material.GOLDEN_APPLE, RPGSubclass.HEALER, 50, 20000, 15, 1, ChatColor.WHITE) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                double heal = 8 + rp.getStats().getIntelligence() * 0.2;
                for (Entity e : player.getNearbyEntities(8, 5, 8)) {
                    if (e instanceof Player) {
                        Player p = (Player) e;
                        double maxHp = p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                        p.setHealth(Math.min(maxHp, p.getHealth() + heal));
                        p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 2, 0), 5, 0.3, 0.3, 0.3, 0);
                        p.sendMessage(ChatColor.GREEN + "❤ " + player.getName() + " te ha curado!");
                    }
                }
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.5f);
                spawnCircleParticles(player.getLocation(), Particle.HEART, 8.0, 20);
            }
        });
        register(new SimpleAbility("divine_shield", "Escudo Divino", "Escudo que absorbe 10 corazones de daño",
                Material.ENCHANTED_GOLDEN_APPLE, RPGSubclass.HEALER, 60, 60000, 30, 2, ChatColor.GOLD) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 4));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1.5f);
                spawnCircleParticles(player.getLocation(), Particle.END_ROD, 2.0, 30);
            }
        });

        // ========== MAGE - ARCANE ==========
        register(new SimpleAbility("teleport", "Teleporte Arcano", "Teleporte 15 bloques en la dirección que miras",
                Material.ENDER_PEARL, RPGSubclass.ARCANE, 20, 6000, 5, 1, ChatColor.DARK_PURPLE) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                Location loc = player.getLocation();
                Vector dir = loc.getDirection().normalize().multiply(15);
                Location target = loc.add(dir);
                target.setY(player.getWorld().getHighestBlockYAt(target) + 1);
                player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation(), 40, 0.5, 1, 0.5, 0.5);
                player.teleport(target);
                player.getWorld().spawnParticle(Particle.PORTAL, target, 40, 0.5, 1, 0.5, 0.5);
                player.getWorld().playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 1.2f);
            }
        });
        register(new SimpleAbility("mana_shield", "Escudo de Maná", "Absorbe daño usando maná en vez de vida 10s",
                Material.LAPIS_LAZULI, RPGSubclass.ARCANE, 10, 30000, 15, 1, ChatColor.BLUE) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                player.setMetadata("rpg_mana_shield", new FixedMetadataValue(plugin, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0));
                new BukkitRunnable() { public void run() { player.removeMetadata("rpg_mana_shield", plugin); } }
                    .runTaskLater(plugin, 200L);
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.5f, 1.5f);
                spawnCircleParticles(player.getLocation(), Particle.ENCHANT, 2.0, 40);
            }
        });
        register(new SimpleAbility("time_warp", "Distorsión Temporal", "Ralentiza el tiempo para todos menos tú 5s",
                Material.CLOCK, RPGSubclass.ARCANE, 70, 90000, 30, 2, ChatColor.DARK_PURPLE) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                for (Entity e : player.getNearbyEntities(12, 5, 12)) {
                    if (e instanceof LivingEntity && e != player) {
                        ((LivingEntity) e).addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 3));
                        ((LivingEntity) e).addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 100, 2));
                    }
                }
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 2));
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 0.5f, 2f);
                spawnCircleParticles(player.getLocation(), Particle.PORTAL, 12.0, 60);
            }
        });

        // ========== ARCHER - RANGER ==========
        register(new SimpleAbility("multishot", "Multidisparo", "Dispara 5 flechas en abanico",
                Material.ARROW, RPGSubclass.RANGER, 20, 8000, 5, 1, ChatColor.GREEN) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                Vector base = player.getLocation().getDirection().normalize();
                for (int i = -2; i <= 2; i++) {
                    double angle = Math.toRadians(i * 10);
                    double cos = Math.cos(angle), sin = Math.sin(angle);
                    Vector dir = new Vector(base.getX() * cos - base.getZ() * sin, base.getY(), base.getX() * sin + base.getZ() * cos);
                    Arrow arrow = player.launchProjectile(Arrow.class);
                    arrow.setVelocity(dir.multiply(2.5));
                    arrow.setDamage(3 + rp.getStats().getDexterity() * 0.15);
                }
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.5f, 1.2f);
            }
        });
        register(new SimpleAbility("volley", "Lluvia de Flechas", "Lluvia de flechas en una zona",
                Material.SPECTRAL_ARROW, RPGSubclass.RANGER, 35, 18000, 15, 1, ChatColor.GREEN) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                Location target = player.getTargetBlockExact(30) != null ?
                        player.getTargetBlockExact(30).getLocation().add(0, 10, 0) :
                        player.getLocation().add(player.getLocation().getDirection().multiply(15)).add(0, 10, 0);
                Random rand = new Random();
                new BukkitRunnable() {
                    int count = 0;
                    public void run() {
                        if (count >= 12) { cancel(); return; }
                        Location spawn = target.clone().add(rand.nextInt(6) - 3, 0, rand.nextInt(6) - 3);
                        Arrow arrow = player.getWorld().spawnArrow(spawn, new Vector(0, -2, 0), 1.5f, 2f);
                        arrow.setShooter(player);
                        arrow.setDamage(2 + rp.getStats().getDexterity() * 0.1);
                        count++;
                    }
                }.runTaskTimer(plugin, 0L, 2L);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 2f, 0.8f);
            }
        });
        register(new SimpleAbility("explosive_arrow", "Flecha Explosiva", "Tu siguiente flecha explota al impactar",
                Material.TNT_MINECART, RPGSubclass.RANGER, 40, 25000, 25, 2, ChatColor.RED) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                player.setMetadata("rpg_explosive_arrow", new FixedMetadataValue(plugin, rp.getStats().getDexterity()));
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_TNT_PRIMED, 1f, 1.5f);
                player.sendMessage(ChatColor.RED + "  ➤ Tu siguiente flecha será explosiva!");
            }
        });

        // ========== ARCHER - HUNTER ==========
        register(new SimpleAbility("pet_wolf", "Lobo Compañero", "Invoca un lobo que lucha por ti 30s",
                Material.WOLF_SPAWN_EGG, RPGSubclass.HUNTER, 30, 45000, 5, 1, ChatColor.DARK_GREEN) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                Wolf wolf = (Wolf) player.getWorld().spawnEntity(player.getLocation(), EntityType.WOLF);
                wolf.setOwner(player);
                wolf.setCustomName(ChatColor.GREEN + "Lobo de " + player.getName());
                wolf.setCustomNameVisible(true);
                wolf.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 600, 1));
                wolf.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 1));
                new BukkitRunnable() { public void run() { if (!wolf.isDead()) wolf.remove(); } }
                    .runTaskLater(plugin, 600L);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WOLF_HOWL, 1.5f, 1f);
            }
        });
        register(new SimpleAbility("track", "Rastrear", "Marca al jugador/mob más cercano con Glowing",
                Material.COMPASS, RPGSubclass.HUNTER, 15, 20000, 10, 1, ChatColor.DARK_GREEN) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                Entity nearest = null;
                double nearestDist = 50;
                for (Entity e : player.getNearbyEntities(50, 20, 50)) {
                    if (e instanceof LivingEntity && e != player) {
                        double d = e.getLocation().distance(player.getLocation());
                        if (d < nearestDist) { nearest = e; nearestDist = d; }
                    }
                }
                if (nearest != null) {
                    ((LivingEntity) nearest).addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0));
                    player.sendMessage(ChatColor.GREEN + "  ➤ Rastreado: " + nearest.getName() + " (" + (int)nearestDist + " bloques)");
                    player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.5f, 2f);
                }
            }
        });
        register(new SimpleAbility("beast_master", "Maestro de Bestias", "Invoca 3 lobos feroces que atacan en grupo",
                Material.BONE, RPGSubclass.HUNTER, 55, 90000, 30, 2, ChatColor.DARK_GREEN) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                for (int i = 0; i < 3; i++) {
                    Location sl = player.getLocation().add(new Random().nextInt(3) - 1, 0, new Random().nextInt(3) - 1);
                    Wolf wolf = (Wolf) player.getWorld().spawnEntity(sl, EntityType.WOLF);
                    wolf.setOwner(player);
                    wolf.setCustomName(ChatColor.RED + "Bestia Feroz");
                    wolf.setCustomNameVisible(true);
                    wolf.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 400, 2));
                    wolf.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 400, 2));
                    new BukkitRunnable() { public void run() { if (!wolf.isDead()) wolf.remove(); } }
                        .runTaskLater(plugin, 400L);
                }
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WOLF_HOWL, 2f, 0.8f);
            }
        });

        // ========== ARCHER - ASSASSIN ==========
        register(new SimpleAbility("stealth", "Sigilo", "Te vuelves invisible 8s. Primer ataque hace x2 daño",
                Material.GLASS_PANE, RPGSubclass.ASSASSIN, 25, 20000, 5, 1, ChatColor.GRAY) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 160, 0));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 160, 1));
                player.setMetadata("rpg_stealth", new FixedMetadataValue(plugin, true));
                new BukkitRunnable() { public void run() { player.removeMetadata("rpg_stealth", plugin); } }
                    .runTaskLater(plugin, 160L);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 0.5f, 2f);
                player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation(), 20, 0.3, 0.5, 0.3, 0.02);
            }
        });
        register(new SimpleAbility("backstab", "Puñalada Trasera", "Daño x3 si atacas por la espalda",
                Material.IRON_SWORD, RPGSubclass.ASSASSIN, 20, 10000, 15, 1, ChatColor.GRAY) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                player.setMetadata("rpg_backstab", new FixedMetadataValue(plugin, true));
                new BukkitRunnable() { public void run() { player.removeMetadata("rpg_backstab", plugin); } }
                    .runTaskLater(plugin, 100L);
                player.sendMessage(ChatColor.GRAY + "  ➤ Tu siguiente ataque por la espalda hará x3 daño!");
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PHANTOM_BITE, 1f, 1.5f);
            }
        });
        register(new SimpleAbility("smoke_bomb", "Bomba de Humo", "Ceguera a enemigos cercanos 4s + invisibilidad",
                Material.FIREWORK_STAR, RPGSubclass.ASSASSIN, 35, 30000, 25, 2, ChatColor.DARK_GRAY) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                for (Entity e : player.getNearbyEntities(5, 3, 5)) {
                    if (e instanceof LivingEntity && e != player) {
                        ((LivingEntity) e).addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0));
                        ((LivingEntity) e).addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1));
                    }
                }
                player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 80, 0));
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1f, 2f);
                player.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, player.getLocation().add(0, 1, 0), 50, 2, 1, 2, 0.01);
            }
        });

        // ========== ROGUE - THIEF ==========
        register(new SimpleAbility("pickpocket", "Carterista", "Roba un item aleatorio del inventario de un jugador cercano",
                Material.GOLD_NUGGET, RPGSubclass.THIEF, 20, 30000, 5, 1, ChatColor.YELLOW) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                Player target = null;
                double minDist = 3;
                for (Entity e : player.getNearbyEntities(3, 2, 3)) {
                    if (e instanceof Player && e != player) {
                        target = (Player) e;
                        break;
                    }
                }
                if (target != null) {
                    Random r = new Random();
                    int slot = r.nextInt(36);
                    org.bukkit.inventory.ItemStack item = target.getInventory().getItem(slot);
                    if (item != null && item.getType() != Material.AIR) {
                        target.getInventory().setItem(slot, null);
                        player.getInventory().addItem(item);
                        player.sendMessage(ChatColor.YELLOW + "  ➤ ¡Robaste " + item.getType().name() + " a " + target.getName() + "!");
                        target.sendMessage(ChatColor.RED + "  ➤ ¡" + player.getName() + " te ha robado algo!");
                    } else {
                        player.sendMessage(ChatColor.GRAY + "  ➤ No encontraste nada que robar...");
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "  ➤ No hay nadie lo suficientemente cerca.");
                }
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.5f, 0.8f);
            }
        });
        register(new SimpleAbility("lockpick", "Ganzúa", "Abre cualquier cofre protegido durante 5s",
                Material.TRIPWIRE_HOOK, RPGSubclass.THIEF, 15, 45000, 15, 1, ChatColor.YELLOW) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                player.setMetadata("rpg_lockpick", new FixedMetadataValue(plugin, true));
                new BukkitRunnable() { public void run() { player.removeMetadata("rpg_lockpick", plugin); } }
                    .runTaskLater(plugin, 100L);
                player.sendMessage(ChatColor.YELLOW + "  ➤ Ganzúa activa 5s - abre cualquier cofre!");
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 1f, 2f);
            }
        });
        register(new SimpleAbility("disguise", "Disfraz", "Te vuelves invisible y tu nombre se oculta 15s",
                Material.CARVED_PUMPKIN, RPGSubclass.THIEF, 40, 60000, 25, 2, ChatColor.YELLOW) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 300, 0));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 300, 0));
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1f);
                player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation(), 30, 0.5, 1, 0.5, 0.02);
            }
        });

        // ========== ROGUE - SABOTEUR ==========
        register(new SimpleAbility("trap_mastery", "Trampa Maestra", "Coloca una trampa invisible que daña y ralentiza",
                Material.STONE_PRESSURE_PLATE, RPGSubclass.SABOTEUR, 25, 15000, 5, 1, ChatColor.DARK_GRAY) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                Location loc = player.getLocation().getBlock().getLocation();
                player.setMetadata("rpg_trap_loc", new FixedMetadataValue(plugin, loc));
                // Invisible trap - check nearby entities
                new BukkitRunnable() {
                    int ticks = 0;
                    public void run() {
                        ticks++;
                        if (ticks > 600) { cancel(); return; }
                        for (Entity e : loc.getWorld().getNearbyEntities(loc.add(0.5, 0.5, 0.5), 1.5, 1, 1.5)) {
                            if (e instanceof LivingEntity && e != player) {
                                ((LivingEntity) e).damage(6, player);
                                ((LivingEntity) e).addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2));
                                loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 5, 0.3, 0.3, 0.3, 0);
                                loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.5f);
                                cancel();
                                return;
                            }
                        }
                    }
                }.runTaskTimer(plugin, 20L, 10L);
                player.sendMessage(ChatColor.DARK_GRAY + "  ➤ Trampa colocada!");
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_TRIPWIRE_CLICK_ON, 1f, 1f);
            }
        });
        register(new SimpleAbility("demolition", "Demolición", "Crea una explosión que no daña bloques",
                Material.TNT, RPGSubclass.SABOTEUR, 35, 20000, 15, 1, ChatColor.DARK_GRAY) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                Location loc = player.getTargetBlockExact(10) != null ?
                        player.getTargetBlockExact(10).getLocation() : player.getLocation().add(player.getLocation().getDirection().multiply(5));
                double dmg = 8 + rp.getStats().getDexterity() * 0.3;
                for (Entity e : loc.getWorld().getNearbyEntities(loc, 4, 4, 4)) {
                    if (e instanceof LivingEntity && e != player) {
                        ((LivingEntity) e).damage(dmg, player);
                    }
                }
                loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 3, 0, 0, 0, 0);
                loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.8f);
            }
        });
        register(new SimpleAbility("sabotage", "Sabotaje", "Desarma al objetivo, tirando su arma al suelo",
                Material.SHEARS, RPGSubclass.SABOTEUR, 30, 45000, 25, 2, ChatColor.DARK_GRAY) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                for (Entity e : player.getNearbyEntities(5, 3, 5)) {
                    if (e instanceof Player && e != player) {
                        Player target = (Player) e;
                        org.bukkit.inventory.ItemStack held = target.getInventory().getItemInMainHand();
                        if (held.getType() != Material.AIR) {
                            target.getInventory().setItemInMainHand(null);
                            target.getWorld().dropItemNaturally(target.getLocation(), held);
                            target.sendMessage(ChatColor.RED + "  ➤ ¡" + player.getName() + " te ha desarmado!");
                            player.sendMessage(ChatColor.YELLOW + "  ➤ ¡" + target.getName() + " desarmado!");
                        }
                        break;
                    }
                }
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.5f, 1f);
            }
        });

        // ========== ROGUE - POISON ==========
        register(new SimpleAbility("venom_strike", "Golpe Venenoso", "Tu siguiente ataque envenena al objetivo 8s",
                Material.SPIDER_EYE, RPGSubclass.POISON, 15, 8000, 5, 1, ChatColor.DARK_GREEN) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                player.setMetadata("rpg_venom", new FixedMetadataValue(plugin, true));
                new BukkitRunnable() { public void run() { player.removeMetadata("rpg_venom", plugin); } }
                    .runTaskLater(plugin, 200L);
                player.sendMessage(ChatColor.DARK_GREEN + "  ➤ Tu siguiente ataque envenenará!");
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 1f, 1.5f);
            }
        });
        register(new SimpleAbility("poison_cloud", "Nube Venenosa", "Crea una nube de veneno en una zona",
                Material.FERMENTED_SPIDER_EYE, RPGSubclass.POISON, 35, 20000, 15, 1, ChatColor.DARK_GREEN) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                Location loc = player.getTargetBlockExact(15) != null ?
                        player.getTargetBlockExact(15).getLocation().add(0, 1, 0) :
                        player.getLocation().add(player.getLocation().getDirection().multiply(8));
                new BukkitRunnable() {
                    int ticks = 0;
                    public void run() {
                        ticks++;
                        if (ticks > 10) { cancel(); return; }
                        loc.getWorld().spawnParticle(Particle.DUST, loc, 30, 3, 1, 3, 0,
                                new Particle.DustOptions(Color.GREEN, 2f));
                        for (Entity e : loc.getWorld().getNearbyEntities(loc, 3, 2, 3)) {
                            if (e instanceof LivingEntity && e != player) {
                                ((LivingEntity) e).addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 1));
                                ((LivingEntity) e).addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 40, 0));
                            }
                        }
                    }
                }.runTaskTimer(plugin, 0L, 10L);
                player.getWorld().playSound(loc, Sound.ENTITY_GENERIC_DRINK, 1.5f, 0.8f);
            }
        });
        register(new SimpleAbility("antidote", "Antídoto", "Elimina todos los efectos negativos y cura",
                Material.MILK_BUCKET, RPGSubclass.POISON, 25, 30000, 25, 2, ChatColor.GREEN) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                for (PotionEffect effect : player.getActivePotionEffects()) {
                    PotionEffectType type = effect.getType();
                    if (type.equals(PotionEffectType.POISON) || type.equals(PotionEffectType.WITHER) ||
                        type.equals(PotionEffectType.BLINDNESS) || type.equals(PotionEffectType.NAUSEA) ||
                        type.equals(PotionEffectType.SLOWNESS) || type.equals(PotionEffectType.WEAKNESS) ||
                        type.equals(PotionEffectType.HUNGER) || type.equals(PotionEffectType.MINING_FATIGUE)) {
                        player.removePotionEffect(type);
                    }
                }
                double maxHp = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                player.setHealth(Math.min(maxHp, player.getHealth() + 8));
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1.5f, 1.2f);
                player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 2, 0), 10, 0.5, 0.3, 0.5, 0);
            }
        });

        // ========== PALADIN - HOLY ==========
        register(new SimpleAbility("divine_strike", "Golpe Divino", "Tu siguiente ataque hace +100% daño sagrado",
                Material.BLAZE_POWDER, RPGSubclass.HOLY, 25, 10000, 5, 1, ChatColor.GOLD) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                player.setMetadata("rpg_divine_strike", new FixedMetadataValue(plugin, true));
                new BukkitRunnable() { public void run() { player.removeMetadata("rpg_divine_strike", plugin); } }
                    .runTaskLater(plugin, 100L);
                player.sendMessage(ChatColor.GOLD + "  ➤ ¡Tu siguiente golpe será divino!");
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 2f);
                player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 2, 0), 15, 0.3, 0.3, 0.3, 0.05);
            }
        });
        register(new SimpleAbility("blessing", "Bendición", "Buff de regeneración y resistencia a todos los cercanos",
                Material.ENCHANTED_BOOK, RPGSubclass.HOLY, 40, 30000, 15, 1, ChatColor.GOLD) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 0));
                for (Entity e : player.getNearbyEntities(8, 4, 8)) {
                    if (e instanceof Player) {
                        ((Player) e).addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 1));
                        ((Player) e).addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 0));
                        ((Player) e).sendMessage(ChatColor.GOLD + "✦ La bendición de " + player.getName() + " te envuelve!");
                    }
                }
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.5f);
                spawnCircleParticles(player.getLocation(), Particle.END_ROD, 8.0, 30);
            }
        });
        register(new SimpleAbility("resurrection", "Resurrección", "Revive a un jugador cercano muerto (TP al punto de muerte)",
                Material.TOTEM_OF_UNDYING, RPGSubclass.HOLY, 80, 300000, 30, 2, ChatColor.GOLD) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                // Full heal self and nearest player
                double maxHp = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                player.setHealth(maxHp);
                player.setFoodLevel(20);
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 2));
                Player nearest = null;
                double minDist = 10;
                for (Entity e : player.getNearbyEntities(10, 5, 10)) {
                    if (e instanceof Player && e != player) {
                        double d = e.getLocation().distance(player.getLocation());
                        if (d < minDist) { nearest = (Player) e; minDist = d; }
                    }
                }
                if (nearest != null) {
                    double nMax = nearest.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                    nearest.setHealth(nMax);
                    nearest.setFoodLevel(20);
                    nearest.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 2));
                    nearest.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 2));
                    nearest.sendMessage(ChatColor.GOLD + "✦ ¡" + player.getName() + " te ha resucitado!");
                }
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 2f, 1f);
                spawnCircleParticles(player.getLocation(), Particle.TOTEM_OF_UNDYING, 3.0, 50);
            }
        });

        // ========== PALADIN - RETRIBUTION ==========
        register(new SimpleAbility("hammer_of_justice", "Martillo de Justicia", "Stun al objetivo 3s con daño alto",
                Material.GOLDEN_AXE, RPGSubclass.RETRIBUTION, 30, 12000, 5, 1, ChatColor.GOLD) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                for (Entity e : player.getNearbyEntities(4, 2, 4)) {
                    if (e instanceof LivingEntity && e != player) {
                        LivingEntity le = (LivingEntity) e;
                        le.damage(8 + rp.getStats().getStrength() * 0.2, player);
                        le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 100));
                        le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                        le.getWorld().spawnParticle(Particle.ENCHANTED_HIT, le.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.1);
                        break; // Only hit first target
                    }
                }
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 1.5f, 0.8f);
            }
        });
        register(new SimpleAbility("crusader_strike", "Golpe del Cruzado", "Golpe potente que cura al atacante",
                Material.GOLDEN_SWORD, RPGSubclass.RETRIBUTION, 25, 8000, 15, 1, ChatColor.GOLD) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                player.setMetadata("rpg_crusader", new FixedMetadataValue(plugin, true));
                new BukkitRunnable() { public void run() { player.removeMetadata("rpg_crusader", plugin); } }
                    .runTaskLater(plugin, 100L);
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 60, 2));
                player.sendMessage(ChatColor.GOLD + "  ➤ ¡Tu siguiente ataque te curará!");
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.5f, 0.8f);
            }
        });
        register(new SimpleAbility("holy_wrath", "Furia Sagrada", "Explosión divina masiva de daño sagrado en área",
                Material.NETHER_STAR, RPGSubclass.RETRIBUTION, 60, 60000, 30, 2, ChatColor.GOLD) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                double dmg = 12 + rp.getStats().getStrength() * 0.4 + rp.getStats().getIntelligence() * 0.2;
                for (Entity e : player.getNearbyEntities(8, 4, 8)) {
                    if (e instanceof LivingEntity && e != player) {
                        ((LivingEntity) e).damage(dmg, player);
                        e.getWorld().spawnParticle(Particle.END_ROD, e.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.1);
                    }
                }
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 1.5f);
                spawnCircleParticles(player.getLocation(), Particle.END_ROD, 8.0, 50);
                player.getWorld().spawnParticle(Particle.FLASH, player.getLocation().add(0, 2, 0), 1, 0, 0, 0, 0);
            }
        });

        // ========== PALADIN - PROTECTION ==========
        register(new SimpleAbility("divine_protection", "Protección Divina", "Invulnerable 3s pero no puedes atacar",
                Material.IRON_CHESTPLATE, RPGSubclass.PROTECTION, 30, 30000, 5, 1, ChatColor.WHITE) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 100));
                player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 100));
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 2f, 1.5f);
                spawnCircleParticles(player.getLocation(), Particle.END_ROD, 1.5, 20);
            }
        });
        register(new SimpleAbility("guardian_angel", "Ángel Guardián", "El siguiente golpe letal te deja con 1HP en su lugar",
                Material.FEATHER, RPGSubclass.PROTECTION, 50, 120000, 20, 1, ChatColor.WHITE) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                player.setMetadata("rpg_guardian_angel", new FixedMetadataValue(plugin, true));
                player.sendMessage(ChatColor.WHITE + "  ➤ Un ángel guardián te protege del próximo golpe letal.");
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.5f, 2f);
                player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 2.5, 0), 20, 0.3, 0.3, 0.3, 0.02);
            }
        });
        register(new SimpleAbility("holy_barrier", "Barrera Sagrada", "Escudo de absorción para todo el grupo",
                Material.BEACON, RPGSubclass.PROTECTION, 65, 90000, 30, 2, ChatColor.WHITE) {
            @Override
            public void execute(Player player, RPGPlayer rp) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 300, 3));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 300, 1));
                for (Entity e : player.getNearbyEntities(10, 5, 10)) {
                    if (e instanceof Player) {
                        ((Player) e).addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 300, 2));
                        ((Player) e).addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 300, 0));
                        ((Player) e).sendMessage(ChatColor.WHITE + "✦ La barrera sagrada de " + player.getName() + " te protege!");
                    }
                }
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 2f, 1.2f);
                spawnCircleParticles(player.getLocation(), Particle.END_ROD, 10.0, 50);
            }
        });
    }

    private void register(Ability ability) {
        abilities.put(ability.getId(), ability);
    }

    public Ability getAbility(String id) { return abilities.get(id); }
    public Collection<Ability> getAllAbilities() { return abilities.values(); }

    public List<Ability> getAbilitiesForSubclass(RPGSubclass subclass) {
        List<Ability> list = new ArrayList<>();
        for (Ability a : abilities.values()) {
            if (a.getSubclass() == subclass) list.add(a);
        }
        return list;
    }

    // Helper for circle particles
    static void spawnCircleParticles(Location center, Particle particle, double radius, int count) {
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            center.getWorld().spawnParticle(particle, x, center.getY() + 0.5, z, 1, 0, 0, 0, 0);
        }
    }

    // Abstract helper class to reduce boilerplate
    private static abstract class SimpleAbility extends Ability {
        public SimpleAbility(String id, String displayName, String description, Material icon,
                             RPGSubclass subclass, double manaCost, long cooldownMs,
                             int requiredLevel, int abilityCost, ChatColor color) {
            super(id, displayName, description, icon, subclass, manaCost, cooldownMs,
                    requiredLevel, abilityCost, color);
        }
    }
}
