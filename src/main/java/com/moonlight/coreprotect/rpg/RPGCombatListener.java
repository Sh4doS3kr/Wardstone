package com.moonlight.coreprotect.rpg;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.rpg.abilities.AbilityRegistry;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;

public class RPGCombatListener implements Listener {

    private final CoreProtectPlugin plugin;
    private final Random random = new Random();

    public RPGCombatListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    // Modify outgoing damage based on stats
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        // Player attacking
        Player attacker = null;
        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile) {
            Projectile proj = (Projectile) event.getDamager();
            if (proj.getShooter() instanceof Player) {
                attacker = (Player) proj.getShooter();
            }
        }

        if (attacker != null) {
            RPGPlayer rp = plugin.getRPGManager().getPlayer(attacker.getUniqueId());
            RPGStats stats = rp.getStats();
            double damage = event.getDamage();

            // Apply stat multipliers
            if (event.getDamager() instanceof Projectile) {
                damage *= stats.getRangedDamageMultiplier();
            } else {
                damage *= stats.getMeleeDamageMultiplier();
            }

            // Critical hit
            if (random.nextDouble() < stats.getCritChance()) {
                damage *= stats.getCritMultiplier();
                attacker.getWorld().spawnParticle(Particle.ENCHANTED_HIT,
                        event.getEntity().getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.1);
                attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1.2f);
                attacker.sendMessage(ChatColor.GOLD + "  ★ ¡CRÍTICO! x" +
                        String.format("%.1f", stats.getCritMultiplier()));
            }

            // Execute passive: +200% to low HP targets
            if (attacker.hasMetadata("rpg_execute") && event.getEntity() instanceof LivingEntity) {
                LivingEntity target = (LivingEntity) event.getEntity();
                double maxHp = target.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                if (target.getHealth() / maxHp < 0.3) {
                    damage *= 3.0;
                    attacker.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR,
                            target.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.1);
                }
            }

            // Stealth bonus damage
            if (attacker.hasMetadata("rpg_stealth")) {
                damage *= 2.0;
                attacker.removeMetadata("rpg_stealth", plugin);
                attacker.sendMessage(ChatColor.GRAY + "  ➤ ¡Ataque sigiloso! x2 daño");
            }

            // Backstab
            if (attacker.hasMetadata("rpg_backstab") && event.getEntity() instanceof LivingEntity) {
                LivingEntity target = (LivingEntity) event.getEntity();
                double dot = target.getLocation().getDirection().dot(attacker.getLocation().getDirection());
                if (dot > 0.5) { // Both facing same direction = behind
                    damage *= 3.0;
                    attacker.sendMessage(ChatColor.GRAY + "  ➤ ¡Puñalada trasera! x3 daño");
                    attacker.removeMetadata("rpg_backstab", plugin);
                }
            }

            // Divine strike
            if (attacker.hasMetadata("rpg_divine_strike")) {
                damage *= 2.0;
                attacker.removeMetadata("rpg_divine_strike", plugin);
                attacker.getWorld().spawnParticle(Particle.END_ROD,
                        event.getEntity().getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.05);
            }

            // Venom strike
            if (attacker.hasMetadata("rpg_venom") && event.getEntity() instanceof LivingEntity) {
                ((LivingEntity) event.getEntity()).addPotionEffect(
                        new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.POISON, 160, 1));
                attacker.removeMetadata("rpg_venom", plugin);
            }

            // Crusader strike: heal on hit
            if (attacker.hasMetadata("rpg_crusader")) {
                double maxHp = attacker.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                attacker.setHealth(Math.min(maxHp, attacker.getHealth() + damage * 0.3));
                attacker.removeMetadata("rpg_crusader", plugin);
            }

            // Lifesteal (bloodthirst)
            if (attacker.hasMetadata("rpg_lifesteal")) {
                MetadataValue meta = attacker.getMetadata("rpg_lifesteal").get(0);
                double percent = meta.asDouble();
                double maxHp = attacker.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                attacker.setHealth(Math.min(maxHp, attacker.getHealth() + damage * percent));
            }

            event.setDamage(damage);
        }

        // Player being attacked: VIT reduces damage
        if (event.getEntity() instanceof Player) {
            Player victim = (Player) event.getEntity();
            RPGPlayer rp = plugin.getRPGManager().getPlayer(victim.getUniqueId());

            // Mana shield: absorb with mana instead
            if (victim.hasMetadata("rpg_mana_shield")) {
                double manaCost = event.getDamage() * 2;
                if (rp.getMana() >= manaCost) {
                    rp.useMana(manaCost);
                    event.setDamage(0);
                    victim.getWorld().spawnParticle(Particle.ENCHANT,
                            victim.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.5);
                    return;
                } else {
                    // Partial absorption
                    double absorbed = rp.getMana() / 2;
                    rp.useMana(rp.getMana());
                    event.setDamage(event.getDamage() - absorbed);
                }
            }

            // Guardian angel: survive lethal damage
            if (victim.hasMetadata("rpg_guardian_angel")) {
                double afterDamage = victim.getHealth() - event.getFinalDamage();
                if (afterDamage <= 0) {
                    event.setDamage(0);
                    victim.setHealth(1.0);
                    victim.removeMetadata("rpg_guardian_angel", plugin);
                    victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1.5f);
                    victim.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                            victim.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.2);
                    victim.sendMessage(ChatColor.WHITE + "✦ ¡Tu ángel guardián te ha salvado!");
                }
            }
        }
    }

    // Explosive arrow handling
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onProjectileHit(org.bukkit.event.entity.ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow)) return;
        Arrow arrow = (Arrow) event.getEntity();
        if (!(arrow.getShooter() instanceof Player)) return;
        Player shooter = (Player) arrow.getShooter();

        if (shooter.hasMetadata("rpg_explosive_arrow")) {
            Location loc = arrow.getLocation();
            RPGPlayer rp = plugin.getRPGManager().getPlayer(shooter.getUniqueId());
            double dmg = 8 + rp.getStats().getDexterity() * 0.3;
            for (Entity e : loc.getWorld().getNearbyEntities(loc, 4, 4, 4)) {
                if (e instanceof LivingEntity && e != shooter) {
                    ((LivingEntity) e).damage(dmg, shooter);
                }
            }
            loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 2, 0, 0, 0, 0);
            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1f);
            shooter.removeMetadata("rpg_explosive_arrow", plugin);
        }
    }

    // XP from mob kills + loot bonus
    @EventHandler(priority = EventPriority.NORMAL)
    public void onMobDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) return;
        Player killer = event.getEntity().getKiller();
        RPGPlayer rp = plugin.getRPGManager().getPlayer(killer.getUniqueId());
        LivingEntity victim = event.getEntity();

        String mobType = victim.getType().name();
        long xpAmount = plugin.getRPGManager().getXpForMob(mobType);

        // Loot bonus from LUK
        double lootBonus = rp.getStats().getLootBonus();
        if (lootBonus > 1.0 && random.nextDouble() < (lootBonus - 1.0)) {
            // Double the drops
            for (int i = 0; i < event.getDrops().size(); i++) {
                org.bukkit.inventory.ItemStack drop = event.getDrops().get(i);
                drop.setAmount(drop.getAmount() + 1);
            }
            killer.sendMessage(ChatColor.GREEN + "  ♣ ¡Loot bonus! Drops extra");
        }

        // Grant XP
        rp.incrementMobKills();
        boolean leveled = rp.addXp(xpAmount);

        // Show XP gain
        killer.sendMessage(ChatColor.LIGHT_PURPLE + "  +" + xpAmount + " XP " +
                ChatColor.GRAY + "(" + rp.getXp() + "/" + rp.getXpToNextLevel() + ")");

        if (leveled) {
            playLevelUpEffect(killer, rp);
        }

        // Bonus XP drop
        event.setDroppedExp((int)(event.getDroppedExp() * (1.0 + rp.getLevel() * 0.01)));
    }

    // Death tracking
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        RPGPlayer rp = plugin.getRPGManager().getPlayer(event.getEntity().getUniqueId());
        rp.incrementDeaths();

        // Lose 10% of current level XP on death
        long xpLoss = (long)(rp.getXp() * 0.10);
        rp.setXp(Math.max(0, rp.getXp() - xpLoss));
        if (xpLoss > 0) {
            event.getEntity().sendMessage(ChatColor.RED + "  ☠ Perdiste " + xpLoss + " XP al morir.");
        }
    }

    // Set max health on join based on VIT
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        applyStatBonuses(player);

        // Mana regen task
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) { cancel(); return; }
                RPGPlayer rp = plugin.getRPGManager().getPlayer(player.getUniqueId());
                rp.regenMana(rp.getStats().getManaRegen());
            }
        }.runTaskTimer(plugin, 20L, 20L); // Every second
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyStatBonuses(event.getPlayer()), 5L);
    }

    public void applyStatBonuses(Player player) {
        RPGPlayer rp = plugin.getRPGManager().getPlayer(player.getUniqueId());
        RPGStats stats = rp.getStats();

        // Max health
        double baseHealth = 20.0;
        double bonusHealth = stats.getMaxHealthBonus();
        if (player.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(baseHealth + bonusHealth);
        }

        // Attack speed
        if (player.getAttribute(Attribute.GENERIC_ATTACK_SPEED) != null) {
            player.getAttribute(Attribute.GENERIC_ATTACK_SPEED).setBaseValue(4.0 + stats.getAttackSpeed() * 10);
        }
    }

    private void playLevelUpEffect(Player player, RPGPlayer rp) {
        Location loc = player.getLocation();
        World world = player.getWorld();

        // Sound
        world.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 1.2f);

        // Title
        String className = rp.hasClass() ? rp.getRpgClass().getColoredName() : ChatColor.GRAY + "Sin clase";
        player.sendTitle(
                ChatColor.GOLD + "⬆ ¡NIVEL " + rp.getLevel() + "!",
                className + ChatColor.GRAY + " | +" + 2 + " Skill Points",
                10, 40, 20);

        // Chat
        Bukkit.broadcastMessage(ChatColor.GOLD + "  ⬆ " + ChatColor.WHITE + player.getName() +
                ChatColor.GOLD + " ha subido a nivel " + ChatColor.YELLOW + rp.getLevel() +
                ChatColor.GOLD + "!");

        // Particles: spiral upward
        new BukkitRunnable() {
            double y = 0;
            int tick = 0;
            @Override
            public void run() {
                if (tick >= 20) { cancel(); return; }
                for (int i = 0; i < 8; i++) {
                    double angle = (tick * 18 + i * 45) * Math.PI / 180;
                    double x = Math.cos(angle) * 1.5;
                    double z = Math.sin(angle) * 1.5;
                    world.spawnParticle(Particle.TOTEM_OF_UNDYING,
                            loc.clone().add(x, y, z), 1, 0, 0, 0, 0);
                }
                y += 0.15;
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Firework
        org.bukkit.entity.Firework fw = (org.bukkit.entity.Firework) world.spawnEntity(loc, EntityType.FIREWORK_ROCKET);
        org.bukkit.FireworkEffect effect = org.bukkit.FireworkEffect.builder()
                .with(org.bukkit.FireworkEffect.Type.BALL_LARGE)
                .withColor(Color.fromRGB(255, 215, 0), Color.YELLOW, Color.WHITE)
                .withFade(Color.ORANGE)
                .flicker(true)
                .build();
        org.bukkit.inventory.meta.FireworkMeta fwm = fw.getFireworkMeta();
        fwm.addEffect(effect);
        fwm.setPower(0);
        fw.setFireworkMeta(fwm);
        Bukkit.getScheduler().runTaskLater(plugin, fw::detonate, 2L);
    }
}
