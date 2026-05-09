package com.moonlight.coreprotect.shadowhunter;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.*;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.UUID;

/**
 * Item legendario "Estrella del Origen" — recompensa de la Misión 9.
 * Contraparte luminosa de la Devoradora de Almas (Agujero Negro → Agujero Blanco).
 * Habilidades:
 * - Nova Radiante (Click derecho): proyectil de luz que explota en AOE, empuja y daña. CD: 10s
 * - Singularidad Blanca (Shift + Click derecho): zona de repulsión que expulsa, daña y cura aliados. CD: 25s
 * - Llama Eterna (Pasiva): cada kill otorga Regeneración III + Speed II 5s
 */
public class WhiteHoleBlade {

    public static final String WHITE_HOLE_KEY = "white_hole_blade";

    public static NamespacedKey getKey(CoreProtectPlugin plugin) {
        return new NamespacedKey(plugin, WHITE_HOLE_KEY);
    }

    /**
     * Crea y da la Estrella del Origen al jugador con animación épica.
     */
    public static void giveWhiteHoleBlade(Player player, CoreProtectPlugin plugin) {
        ItemStack blade = createWhiteHoleBlade(plugin);

        World world = player.getWorld();
        Location loc = player.getLocation();

        player.sendTitle(SmallCaps.convert("§f§l✦ Estrella del Origen ✦"), SmallCaps.convert("§7La luz que nació del vacío..."), 10, 80, 20);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 80) {
                    cancel();

                    if (player.getInventory().firstEmpty() != -1) {
                        player.getInventory().addItem(blade);
                    } else {
                        player.getWorld().dropItemNaturally(player.getLocation(), blade);
                    }

                    // Efecto final mega épico — explosión de luz blanca
                    world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 2, 0), 200, 2, 2, 2, 1.0);
                    world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 2, 0), 120, 2, 2, 2, 0.5);
                    world.spawnParticle(Particle.FLASH, loc.clone().add(0, 2, 0), 3, 0, 0, 0, 0);
                    world.spawnParticle(Particle.DUST, loc.clone().add(0, 2, 0), 80, 2, 2, 2, 0,
                            new Particle.DustOptions(Color.fromRGB(255, 255, 255), 3.0f));
                    world.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
                    world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 2.0f);
                    world.playSound(loc, Sound.BLOCK_END_PORTAL_SPAWN, 0.5f, 2.0f);

                    player.sendMessage("");
                    player.sendMessage(SmallCaps.convert("§f§l✦ §e§lHas obtenido: §f§lEstrella del Origen §f§l✦"));
                    player.sendMessage(SmallCaps.convert("§7  Click derecho: §eNova Radiante §7(proyectil explosivo)"));
                    player.sendMessage(SmallCaps.convert("§7  Shift + Click: §6Singularidad Blanca §7(repulsión masiva)"));
                    player.sendMessage(SmallCaps.convert("§7  Pasiva: §aLlama Eterna §7(regeneración + velocidad al matar)"));
                    player.sendMessage("");
                    return;
                }

                // Doble espiral ascendente blanca/dorada
                double angle1 = ticks * 0.25;
                double angle2 = angle1 + Math.PI;
                double radius = 1.8 - (ticks / 80.0) * 1.2;
                double y = ticks * 0.04 + 1;

                world.spawnParticle(Particle.DUST,
                        loc.clone().add(Math.cos(angle1) * radius, y, Math.sin(angle1) * radius), 3, 0.05, 0.05, 0.05, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 255, 220), 1.8f));
                world.spawnParticle(Particle.DUST,
                        loc.clone().add(Math.cos(angle2) * radius, y, Math.sin(angle2) * radius), 3, 0.05, 0.05, 0.05, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.8f));
                world.spawnParticle(Particle.END_ROD,
                        loc.clone().add(Math.cos(angle1) * radius, y, Math.sin(angle1) * radius), 1, 0, 0, 0, 0.005);

                if (ticks % 8 == 0) {
                    world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.0f + ticks * 0.015f);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    public static ItemStack createWhiteHoleBlade(CoreProtectPlugin plugin) {
        ItemStack blade = new ItemStack(Material.NETHERITE_AXE);
        ItemMeta meta = blade.getItemMeta();

        meta.setDisplayName(SmallCaps.convert("§f§l§k||§r §e§lEstrella del Origen §f§l§k||"));

        meta.setLore(Arrays.asList(
                "§8§m                              ",
                "§7\"Nacida donde termina el vacío\"",
                "§8§m                              ",
                "",
                "§e§l⚡ Habilidades:",
                "",
                "§e▸ Nova Radiante §7(Click derecho)",
                "§7  Proyectil de luz que explota en",
                "§7  AOE §e12 daño §7+ empuje + ceguera 2s.",
                "§7  Cura al lanzador §a4❤§7. Cooldown: §e10s",
                "",
                "§6▸ Singularidad Blanca §7(Shift + Click der.)",
                "§7  Zona de luz que repele enemigos,",
                "§7  §e8 daño§7/s + Slowness III + Glowing.",
                "§7  Cura aliados cercanos. Cooldown: §e25s",
                "",
                "§a▸ Llama Eterna §7(Pasiva)",
                "§7  Cada kill otorga §aRegen III §7+ §bSpeed II",
                "§7  por 5s. Reduce cooldowns en 3s.",
                "",
                "§8§m                              ",
                "§e§oDonde el Abismo devora, la Estrella renace..."));

        meta.addEnchant(Enchantment.SHARPNESS, 5, true);
        meta.addEnchant(Enchantment.UNBREAKING, 5, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        meta.getPersistentDataContainer().set(getKey(plugin), PersistentDataType.BYTE, (byte) 1);

        meta.addAttributeModifier(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE,
                new AttributeModifier(UUID.randomUUID(), "white_hole_damage", 9,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND));
        meta.addAttributeModifier(org.bukkit.attribute.Attribute.GENERIC_ATTACK_SPEED,
                new AttributeModifier(UUID.randomUUID(), "white_hole_speed", -2.4,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND));

        blade.setItemMeta(meta);
        return blade;
    }

    public static boolean isWhiteHoleBlade(ItemStack item, CoreProtectPlugin plugin) {
        if (item == null || item.getType() != Material.NETHERITE_AXE)
            return false;
        if (!item.hasItemMeta())
            return false;
        return item.getItemMeta().getPersistentDataContainer().has(getKey(plugin), PersistentDataType.BYTE);
    }
}
