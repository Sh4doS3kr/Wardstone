package com.moonlight.coreprotect.rpg.bosses;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.rpg.RPGPlayer;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

public class BossManager implements Listener {

    private final CoreProtectPlugin plugin;
    private final Map<UUID, ActiveBoss> activeBosses = new HashMap<>();
    private final Random random = new Random();

    // Boss definitions
    private static final String[][] BOSSES = {
        // {name, day (1=MON..7=SUN), color, entityType, hp, lootType}
        {"Dragon Lord", "7", "RED", "ZOMBIE", "500", "WEAPON"},        // Sunday
        {"Lich King", "3", "BLUE", "SKELETON", "400", "MAGE"},         // Wednesday
        {"Demon Lord", "5", "DARK_RED", "PIGLIN_BRUTE", "450", "WARRIOR"} // Friday
    };

    public BossManager(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        startBossScheduler();
    }

    private void startBossScheduler() {
        // Check every 5 minutes if a boss should spawn
        new BukkitRunnable() {
            @Override
            public void run() {
                if (activeBosses.isEmpty() && shouldSpawnBoss()) {
                    spawnTodaysBoss();
                }
            }
        }.runTaskTimer(plugin, 20L * 60 * 5, 20L * 60 * 5);
    }

    private boolean shouldSpawnBoss() {
        DayOfWeek today = LocalDate.now().getDayOfWeek();
        for (String[] boss : BOSSES) {
            int day = Integer.parseInt(boss[1]);
            if (today.getValue() == day) return true;
        }
        return false;
    }

    public void spawnTodaysBoss() {
        DayOfWeek today = LocalDate.now().getDayOfWeek();
        for (String[] bossData : BOSSES) {
            int day = Integer.parseInt(bossData[1]);
            if (today.getValue() != day) continue;

            // Find a good spawn location near spawn
            World world = Bukkit.getWorlds().get(0);
            Location spawn = world.getSpawnLocation();
            Location bossLoc = spawn.clone().add(random.nextInt(40) - 20, 0, random.nextInt(40) - 20);
            bossLoc.setY(world.getHighestBlockYAt(bossLoc.getBlockX(), bossLoc.getBlockZ()) + 1);

            spawnBoss(bossData[0], bossLoc, Double.parseDouble(bossData[4]),
                    BarColor.valueOf(bossData[2]), bossData[5]);
            break;
        }
    }

    public void spawnBoss(String name, Location loc, double maxHp, BarColor barColor, String lootType) {
        World world = loc.getWorld();
        if (world == null) return;

        // Spawn a giant zombie as the boss
        Zombie boss = (Zombie) world.spawnEntity(loc, EntityType.ZOMBIE);
        boss.setCustomName(ChatColor.DARK_RED + "☠ " + ChatColor.BOLD + name + ChatColor.DARK_RED + " ☠");
        boss.setCustomNameVisible(true);
        boss.setRemoveWhenFarAway(false);
        boss.setCanPickupItems(false);

        // Set health
        if (boss.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            boss.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(maxHp);
            boss.setHealth(maxHp);
        }
        if (boss.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) != null) {
            boss.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(15);
        }
        if (boss.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) {
            boss.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.3);
        }
        if (boss.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE) != null) {
            boss.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(0.8);
        }

        // Gear
        boss.getEquipment().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
        boss.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
        boss.getEquipment().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
        boss.getEquipment().setBoots(new ItemStack(Material.NETHERITE_BOOTS));
        boss.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
        boss.getEquipment().setHelmetDropChance(0f);
        boss.getEquipment().setChestplateDropChance(0f);
        boss.getEquipment().setLeggingsDropChance(0f);
        boss.getEquipment().setBootsDropChance(0f);
        boss.getEquipment().setItemInMainHandDropChance(0f);

        // Boss effects
        boss.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
        boss.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false));

        // BossBar
        BossBar bossBar = Bukkit.createBossBar(
                ChatColor.DARK_RED + "☠ " + name + " ☠ " + ChatColor.RED + (int) maxHp + " HP",
                barColor, BarStyle.SEGMENTED_20);
        bossBar.setProgress(1.0);

        // Add all online players
        for (Player p : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(p);
        }

        ActiveBoss activeBoss = new ActiveBoss(boss.getUniqueId(), name, bossBar, maxHp, lootType, new HashSet<>());
        activeBosses.put(boss.getUniqueId(), activeBoss);

        // Announce
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.DARK_RED + "═══════════════════════════════");
        Bukkit.broadcastMessage(ChatColor.RED + "  ☠ " + ChatColor.BOLD + "WORLD BOSS: " + name.toUpperCase() + ChatColor.RED + " ☠");
        Bukkit.broadcastMessage(ChatColor.GRAY + "  ¡Ha aparecido cerca del spawn!");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "  Recompensa: " + ChatColor.WHITE + "Legendary " + lootType + " gear + XP");
        Bukkit.broadcastMessage(ChatColor.DARK_RED + "═══════════════════════════════");
        Bukkit.broadcastMessage("");

        // Play sound for all
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 0.5f);
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 2f, 0.8f);
        }

        // Boss abilities every 15 seconds
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!activeBosses.containsKey(boss.getUniqueId()) || boss.isDead()) {
                    cancel();
                    return;
                }
                doBossAbility(boss, name);
            }
        }.runTaskTimer(plugin, 20L * 15, 20L * 15);

        // Particle aura
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!activeBosses.containsKey(boss.getUniqueId()) || boss.isDead()) {
                    cancel();
                    return;
                }
                Location bl = boss.getLocation().add(0, 1, 0);
                world.spawnParticle(Particle.FLAME, bl, 15, 0.5, 1, 0.5, 0.02);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, bl, 10, 0.3, 0.8, 0.3, 0.01);
            }
        }.runTaskTimer(plugin, 0L, 5L);

        // Timeout: 10 minutes
        new BukkitRunnable() {
            @Override
            public void run() {
                ActiveBoss ab = activeBosses.remove(boss.getUniqueId());
                if (ab != null) {
                    ab.getBossBar().removeAll();
                    if (!boss.isDead()) boss.remove();
                    Bukkit.broadcastMessage(ChatColor.GRAY + "  ☠ " + name + " se ha retirado... Nadie pudo derrotarlo.");
                }
            }
        }.runTaskLater(plugin, 20L * 60 * 10);
    }

    private void doBossAbility(Zombie boss, String name) {
        Location loc = boss.getLocation();
        World world = loc.getWorld();

        int ability = random.nextInt(3);
        switch (ability) {
            case 0: // Ground slam
                for (Entity e : boss.getNearbyEntities(8, 4, 8)) {
                    if (e instanceof Player) {
                        Player p = (Player) e;
                        p.damage(8, boss);
                        p.setVelocity(p.getLocation().toVector().subtract(loc.toVector()).normalize().multiply(1.5).setY(0.8));
                        p.sendMessage(ChatColor.RED + "  ☠ " + name + " usa ¡GOLPE TERRESTRE!");
                    }
                }
                world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 3, 2, 0.5, 2, 0);
                world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.5f);
                break;

            case 1: // Summon minions
                for (int i = 0; i < 4; i++) {
                    Location sl = loc.clone().add(random.nextInt(6) - 3, 0, random.nextInt(6) - 3);
                    sl.setY(world.getHighestBlockYAt(sl.getBlockX(), sl.getBlockZ()) + 1);
                    Zombie minion = (Zombie) world.spawnEntity(sl, EntityType.ZOMBIE);
                    minion.setCustomName(ChatColor.RED + "Esbirro de " + name);
                    minion.setCustomNameVisible(true);
                    minion.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
                    minion.getEquipment().setHelmetDropChance(0f);
                    new BukkitRunnable() { public void run() { if (!minion.isDead()) minion.remove(); } }
                            .runTaskLater(plugin, 20L * 30);
                }
                world.playSound(loc, Sound.ENTITY_WITHER_SPAWN, 0.8f, 1.5f);
                Bukkit.broadcastMessage(ChatColor.RED + "  ☠ " + name + " ¡INVOCA ESBIRROS!");
                break;

            case 2: // Dark aura - poison all nearby
                for (Entity e : boss.getNearbyEntities(12, 6, 12)) {
                    if (e instanceof Player) {
                        Player p = (Player) e;
                        p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 1));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
                        p.sendMessage(ChatColor.DARK_PURPLE + "  ☠ " + name + " usa ¡AURA OSCURA!");
                    }
                }
                world.spawnParticle(Particle.DUST, loc.add(0, 1, 0), 50, 6, 2, 6, 0,
                        new Particle.DustOptions(Color.fromRGB(80, 0, 80), 2f));
                world.playSound(loc, Sound.ENTITY_WITHER_AMBIENT, 2f, 0.5f);
                break;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBossDamage(EntityDamageByEntityEvent event) {
        ActiveBoss ab = activeBosses.get(event.getEntity().getUniqueId());
        if (ab == null) return;

        Player attacker = null;
        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile && ((Projectile) event.getDamager()).getShooter() instanceof Player) {
            attacker = (Player) ((Projectile) event.getDamager()).getShooter();
        }

        if (attacker != null) {
            ab.getDamagers().add(attacker.getUniqueId());
        }

        // Update boss bar
        LivingEntity boss = (LivingEntity) event.getEntity();
        double hp = boss.getHealth() - event.getFinalDamage();
        double maxHp = ab.getMaxHp();
        double progress = Math.max(0, Math.min(1, hp / maxHp));
        ab.getBossBar().setProgress(progress);
        ab.getBossBar().setTitle(ChatColor.DARK_RED + "☠ " + ab.getName() + " ☠ " + ChatColor.RED + (int) Math.max(0, hp) + " HP");
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBossDeath(EntityDeathEvent event) {
        ActiveBoss ab = activeBosses.remove(event.getEntity().getUniqueId());
        if (ab == null) return;

        ab.getBossBar().removeAll();
        event.getDrops().clear();
        event.setDroppedExp(500);

        Location loc = event.getEntity().getLocation();
        World world = loc.getWorld();

        // Announce
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.GOLD + "═══════════════════════════════");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "  ⚔ " + ChatColor.BOLD + ab.getName().toUpperCase() + " HA SIDO DERROTADO! ⚔");
        Bukkit.broadcastMessage(ChatColor.GRAY + "  " + ab.getDamagers().size() + " jugadores participaron");
        Bukkit.broadcastMessage(ChatColor.GOLD + "═══════════════════════════════");

        // Reward all participants
        for (UUID damagerUuid : ab.getDamagers()) {
            Player p = Bukkit.getPlayer(damagerUuid);
            if (p != null && p.isOnline()) {
                RPGPlayer rp = plugin.getRPGManager().getPlayer(p.getUniqueId());

                // XP reward
                long xpReward = 500 + rp.getLevel() * 20L;
                boolean leveled = rp.addXp(xpReward);
                rp.incrementBossKills();

                // Money reward
                double money = 2000 + random.nextInt(3000);
                plugin.getEconomy().depositPlayer(p, money);

                // Drop legendary loot
                ItemStack loot = generateBossLoot(ab.getName(), ab.getLootType());
                p.getInventory().addItem(loot);

                p.sendMessage(ChatColor.GOLD + "  ★ Recompensa: " + ChatColor.LIGHT_PURPLE + "+" + xpReward + " XP" +
                        ChatColor.GOLD + " | " + ChatColor.GREEN + "+$" + (int) money +
                        ChatColor.GOLD + " | " + ChatColor.YELLOW + loot.getItemMeta().getDisplayName());
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 1f);

                if (leveled) {
                    p.sendTitle(ChatColor.GOLD + "⬆ ¡NIVEL " + rp.getLevel() + "!", "", 10, 40, 20);
                }

                // Achievement
                plugin.getAchievementManager().grant(p, "kill_boss");
                if (rp.getTotalBossKills() >= 10) {
                    plugin.getAchievementManager().grant(p, "kill_10_bosses");
                }
            }
        }

        plugin.getRPGManager().saveAll();

        // Victory effects
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.add(0, 2, 0), 200, 3, 3, 3, 0.5);
        world.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 2f, 1f);

        // Fireworks
        for (int i = 0; i < 5; i++) {
            Location fwLoc = loc.clone().add(random.nextInt(10) - 5, 2, random.nextInt(10) - 5);
            Firework fw = (Firework) world.spawnEntity(fwLoc, EntityType.FIREWORK_ROCKET);
            org.bukkit.FireworkEffect effect = org.bukkit.FireworkEffect.builder()
                    .with(org.bukkit.FireworkEffect.Type.BALL_LARGE)
                    .withColor(Color.fromRGB(255, 215, 0), Color.RED, Color.PURPLE)
                    .withFade(Color.WHITE)
                    .flicker(true).trail(true).build();
            org.bukkit.inventory.meta.FireworkMeta fwm = fw.getFireworkMeta();
            fwm.addEffect(effect);
            fwm.setPower(1);
            fw.setFireworkMeta(fwm);
        }
    }

    private ItemStack generateBossLoot(String bossName, String lootType) {
        Material mat;
        String name;
        List<String> lore = new ArrayList<>();

        switch (lootType) {
            case "WEAPON":
                mat = random.nextBoolean() ? Material.NETHERITE_SWORD : Material.NETHERITE_AXE;
                name = ChatColor.GOLD + "" + ChatColor.BOLD + "⚔ Espada del " + bossName;
                lore.add(ChatColor.DARK_PURPLE + "LEGENDARIO");
                lore.add("");
                lore.add(ChatColor.RED + "+15 STR");
                lore.add(ChatColor.GREEN + "+8 DEX");
                lore.add(ChatColor.GOLD + "+10% Crit Chance");
                break;
            case "MAGE":
                mat = Material.BLAZE_ROD;
                name = ChatColor.AQUA + "" + ChatColor.BOLD + "✦ Bastón del " + bossName;
                lore.add(ChatColor.DARK_PURPLE + "LEGENDARIO");
                lore.add("");
                lore.add(ChatColor.AQUA + "+20 INT");
                lore.add(ChatColor.BLUE + "+12 WIS");
                lore.add(ChatColor.LIGHT_PURPLE + "+15% Mana Regen");
                break;
            default: // WARRIOR
                mat = random.nextBoolean() ? Material.NETHERITE_CHESTPLATE : Material.NETHERITE_HELMET;
                name = ChatColor.RED + "" + ChatColor.BOLD + "☠ Armadura del " + bossName;
                lore.add(ChatColor.DARK_PURPLE + "LEGENDARIO");
                lore.add("");
                lore.add(ChatColor.GOLD + "+15 VIT");
                lore.add(ChatColor.RED + "+10 STR");
                lore.add(ChatColor.GREEN + "+5% Damage Reduction");
                break;
        }

        lore.add("");
        lore.add(ChatColor.GRAY + "Dropeado por " + bossName);
        lore.add(ChatColor.DARK_GRAY + "World Boss Loot");

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 5, true);
        meta.addEnchant(org.bukkit.enchantments.Enchantment.SHARPNESS, 7, true);
        item.setItemMeta(meta);
        return item;
    }

    // Force spawn a boss (admin command)
    public void forceSpawnBoss(String name) {
        for (String[] bossData : BOSSES) {
            if (bossData[0].equalsIgnoreCase(name)) {
                World world = Bukkit.getWorlds().get(0);
                Location spawn = world.getSpawnLocation().add(random.nextInt(30) - 15, 0, random.nextInt(30) - 15);
                spawn.setY(world.getHighestBlockYAt(spawn.getBlockX(), spawn.getBlockZ()) + 1);
                spawnBoss(bossData[0], spawn, Double.parseDouble(bossData[4]),
                        BarColor.valueOf(bossData[2]), bossData[5]);
                return;
            }
        }
    }

    public boolean hasBossActive() { return !activeBosses.isEmpty(); }

    // Inner class
    private static class ActiveBoss {
        private final UUID entityUuid;
        private final String name;
        private final BossBar bossBar;
        private final double maxHp;
        private final String lootType;
        private final Set<UUID> damagers;

        public ActiveBoss(UUID entityUuid, String name, BossBar bossBar, double maxHp, String lootType, Set<UUID> damagers) {
            this.entityUuid = entityUuid;
            this.name = name;
            this.bossBar = bossBar;
            this.maxHp = maxHp;
            this.lootType = lootType;
            this.damagers = damagers;
        }

        public UUID getEntityUuid() { return entityUuid; }
        public String getName() { return name; }
        public BossBar getBossBar() { return bossBar; }
        public double getMaxHp() { return maxHp; }
        public String getLootType() { return lootType; }
        public Set<UUID> getDamagers() { return damagers; }
    }
}
