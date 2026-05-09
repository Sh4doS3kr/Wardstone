package com.moonlight.coreprotect.shadowhunter;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Listener para las habilidades pasivas de "Pisada del Olvido" (Botas del Vacío).
 * - Paso del Vacío: Inmunidad total al daño por caída
 * - Celeridad Sombría: Speed II permanente (tick)
 * - Huella del Olvido: Partículas de sombra al caminar (tick)
 * - Anclaje Dimensional: Knockback resistance ya está en los atributos del item
 */
public class VoidBootsListener implements Listener {

    private final CoreProtectPlugin plugin;

    public VoidBootsListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    // ==================== PASO DEL VACÍO: Inmunidad a caída ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;

        Player player = (Player) event.getEntity();
        ItemStack boots = player.getInventory().getBoots();
        if (!VoidBoots.isVoidBoots(boots, plugin)) return;

        event.setCancelled(true);

        // Costo de durabilidad por usar inmunidad a caída (5-10 puntos dependiendo del daño)
        double fallDamage = event.getDamage();
        int durabilityCost = (int) Math.min(10, Math.max(5, fallDamage * 2));
        if (boots.getDurability() + durabilityCost <= boots.getType().getMaxDurability()) {
            boots.setDurability((short) (boots.getDurability() + durabilityCost));
            
            // Efecto visual de desgaste
            Location loc = player.getLocation();
            loc.getWorld().spawnParticle(Particle.DUST, loc, 8, 0.5, 0.1, 0.5, 0,
                    new Particle.DustOptions(Color.fromRGB(30, 0, 50), 1.2f));
            loc.getWorld().spawnParticle(Particle.SMOKE, loc, 5, 0.3, 0.05, 0.3, 0.01);
            loc.getWorld().playSound(loc, Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.3f, 0.5f);
            
            // Mensaje de desgaste si la durabilidad es baja
            if (boots.getDurability() > boots.getType().getMaxDurability() * 0.7) {
                com.moonlight.coreprotect.util.ActionBarUtil.send(player, "§8§oLas botas se desgastan por el impacto...");
            }
        } else {
            // No hay suficiente durabilidad, permitir daño
            event.setCancelled(false);
            player.sendMessage("§c§l⚠ §7Las botas están demasiado desgastadas para protegerte de la caída.");
        }
    }

    // ==================== ANCLAJE DIMENSIONAL: Costo de durabilidad por knockback ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK && 
            event.getCause() != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION &&
            event.getCause() != EntityDamageEvent.DamageCause.PROJECTILE) return;

        Player player = (Player) event.getEntity();
        ItemStack boots = player.getInventory().getBoots();
        if (!VoidBoots.isVoidBoots(boots, plugin)) return;

        // Costo de durabilidad por usar anti-knockback (2-4 puntos por golpe)
        int durabilityCost = 2 + (int)(Math.random() * 3); // 2-4 puntos aleatorios
        
        if (boots.getDurability() + durabilityCost <= boots.getType().getMaxDurability()) {
            boots.setDurability((short) (boots.getDurability() + durabilityCost));
            
            // Efecto visual sutil de anclaje
            Location loc = player.getLocation();
            loc.getWorld().spawnParticle(Particle.DUST, loc.clone().add(0, 0.5, 0), 4, 0.3, 0.3, 0.3, 0,
                    new Particle.DustOptions(Color.fromRGB(60, 0, 80), 1.0f));
            loc.getWorld().playSound(loc, Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.2f, 0.8f);
            
            // Mensaje de desgaste si la durabilidad es baja
            if (boots.getDurability() > boots.getType().getMaxDurability() * 0.7) {
                com.moonlight.coreprotect.util.ActionBarUtil.send(player, "§8§oEl anclaje dimensional desgasta las botas...");
            }
        }
        // Si no hay durabilidad, el knockback ya no funcionará debido a que las botas estarán rotas
    }

    // ==================== TICK: Speed II + Partículas + Protección de durabilidad ====================

    /**
     * Llamar cada 20 ticks (1 segundo) desde el plugin principal.
     */
    public void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack boots = player.getInventory().getBoots();
            if (!VoidBoots.isVoidBoots(boots, plugin)) continue;

            // Celeridad Sombría: Speed II permanente
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, true, false, false));

            // Quitar fuego (sinergia con el set)
            if (player.getFireTicks() > 0) {
                player.setFireTicks(0);
            }

            // Huella del Olvido: partículas sombrías al caminar
            if (player.getVelocity().lengthSquared() > 0.01) {
                Location loc = player.getLocation();
                World world = loc.getWorld();

                // Partículas oscuras en los pies
                if (Math.random() < 0.4) {
                    world.spawnParticle(Particle.DUST,
                            loc.clone().add((Math.random() - 0.5) * 0.3, 0.05, (Math.random() - 0.5) * 0.3),
                            2, 0.1, 0.02, 0.1, 0,
                            new Particle.DustOptions(Color.fromRGB(20, 0, 30), 0.8f));
                }

                // Sculk soul ocasional
                if (Math.random() < 0.15) {
                    world.spawnParticle(Particle.SCULK_SOUL,
                            loc.clone().add((Math.random() - 0.5) * 0.4, 0.1, (Math.random() - 0.5) * 0.4),
                            1, 0, 0.05, 0, 0.01);
                }

                // Humo sutil
                if (Math.random() < 0.2) {
                    world.spawnParticle(Particle.SMOKE,
                            loc.clone().add((Math.random() - 0.5) * 0.3, 0.05, (Math.random() - 0.5) * 0.3),
                            1, 0, 0.01, 0, 0.003);
                }
            }
        }
    }
}
