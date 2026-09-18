package com.moonlight.coreprotect.shadowhunter;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.koth.KothManager;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Listener para las habilidades pasivas de la Elegía del Errante (peto).
 * TODAS las habilidades son PASIVAS:
 * - Manto de Sombras: 15% esquivar ataques
 * - Susurro del Errante: regen bajo 50% HP
 * - Eco Protector: Resistencia I permanente
 * - Voluntad Inquebrantable: inmunidad a Wither/Weakness
 */
public class VoidChestplateListener implements Listener {

    private final CoreProtectPlugin plugin;
    private final Map<UUID, Long> dodgeMsgCooldown = new HashMap<>();
    private final Map<UUID, Long> debuffMsgCooldown = new HashMap<>();
    private static final long MSG_COOLDOWN_MS = 5000;

    public VoidChestplateListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isWearingVoidChestplate(Player player) {
        ItemStack chest = player.getInventory().getChestplate();
        return VoidChestplate.isVoidChestplate(chest, plugin);
    }

    private boolean isInKothZone(Player player) {
        if (!player.getWorld().getName().equalsIgnoreCase("koth")) return false;
        // En VALE_TODO se permiten habilidades
        com.moonlight.coreprotect.koth.KothManager km = plugin.getKothManager();
        if (km != null && km.isActive() && km.getCurrentMode() == com.moonlight.coreprotect.koth.KothMode.VALE_TODO) return false;
        return true;
    }

    // ==================== MANTO DE SOMBRAS (15% esquivar) ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDodge(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player))
            return;

        Player player = (Player) event.getEntity();
        if (!isWearingVoidChestplate(player))
            return;

        // Disable in KOTH world
        if (isInKothZone(player)) return;

        // 15% de esquivar
        if (Math.random() < 0.15) {
            event.setCancelled(true);

            Location loc = player.getLocation();
            loc.getWorld().spawnParticle(Particle.DUST, loc.clone().add(0, 1, 0), 15, 0.5, 0.8, 0.5, 0,
                    new Particle.DustOptions(Color.fromRGB(40, 40, 60), 1.5f));
            loc.getWorld().spawnParticle(Particle.SMOKE, loc.clone().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.02);
            player.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.3f, 1.8f);

            long now = System.currentTimeMillis();
            if (now - dodgeMsgCooldown.getOrDefault(player.getUniqueId(), 0L) >= MSG_COOLDOWN_MS) {
                dodgeMsgCooldown.put(player.getUniqueId(), now);
                player.sendTitle("", SmallCaps.convert("§8§lEsquivado"), 0, 15, 5);
            }
        }
    }

    // ==================== PASIVAS CONTINUAS (tick) ====================

    /**
     * Llamar cada 20 ticks (1 segundo) desde el plugin principal.
     * Maneja: Eco Protector, Susurro del Errante, Voluntad Inquebrantable, partículas.
     */
    public void tickPassives() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isWearingVoidChestplate(player))
                continue;

            // Disable all abilities in KOTH world
            if (isInKothZone(player)) continue;

            // Eco Protector: Resistencia I permanente
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 0, false, false, false));

            // Susurro del Errante: Regeneración I cuando HP < 50%
            double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
            if (player.getHealth() < maxHealth * 0.5) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 0, false, false, false));

                // Partículas sutiles de curación
                if (Math.random() < 0.3) {
                    Location loc = player.getLocation().add(0, 1.2, 0);
                    loc.getWorld().spawnParticle(Particle.DUST, loc, 2, 0.3, 0.3, 0.3, 0,
                            new Particle.DustOptions(Color.fromRGB(180, 100, 220), 0.6f));
                }
            }

            // Voluntad Inquebrantable: eliminar Wither y Weakness
            boolean cleansed = false;
            if (player.hasPotionEffect(PotionEffectType.WITHER)) {
                player.removePotionEffect(PotionEffectType.WITHER);
                cleansed = true;
            }
            if (player.hasPotionEffect(PotionEffectType.WEAKNESS)) {
                player.removePotionEffect(PotionEffectType.WEAKNESS);
                cleansed = true;
            }
            if (cleansed) {
                long now = System.currentTimeMillis();
                if (now - debuffMsgCooldown.getOrDefault(player.getUniqueId(), 0L) >= MSG_COOLDOWN_MS) {
                    debuffMsgCooldown.put(player.getUniqueId(), now);
                    player.sendMessage(SmallCaps.convert("§e§l✦ §7Voluntad Inquebrantable ha disipado debuffs."));
                }
            }

            // Partículas ambientales sutiles
            if (Math.random() < 0.15) {
                Location loc = player.getLocation().add(0, 1.0, 0);
                loc.getWorld().spawnParticle(Particle.SOUL,
                        loc.clone().add((Math.random() - 0.5) * 0.4, (Math.random() - 0.5) * 0.3,
                                (Math.random() - 0.5) * 0.4),
                        1, 0, 0.05, 0, 0.005);
            }
        }
    }
}
