package com.moonlight.coreprotect.shadowhunter;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.UUID;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Item legendario "Devoradora de Almas" - recompensa de la misión El Despertar
 * del Abismo.
 * Habilidades:
 * - Desgarro Abismal (Click derecho): dash + barrido AOE 6 bloques, 12 daño +
 * Wither 3s. CD: 10s
 * - Rugido del Vacío (Shift + Click derecho): AOE 7 bloques, 10 daño + Weakness
 * II + levitation 1s. CD: 25s
 * - Alma Hambrienta (Pasiva): cada kill regenera 3❤ + Strength I 5s
 */
public class AbyssBlade {

    public static final String ABYSS_BLADE_KEY = "abyss_blade";

    public static NamespacedKey getKey(CoreProtectPlugin plugin) {
        return new NamespacedKey(plugin, ABYSS_BLADE_KEY);
    }

    /**
     * Crea y da la Devoradora de Almas al jugador con animación épica.
     */
    public static void giveAbyssBlade(Player player, CoreProtectPlugin plugin) {
        ItemStack blade = createAbyssBlade(plugin);

        World world = player.getWorld();
        Location loc = player.getLocation();

        player.sendTitle( SmallCaps.convert("§4§l✦ Devoradora de Almas ✦"), SmallCaps.convert("§7Un arma que consume el vacío..."), 10, 80, 20);

        new org.bukkit.scheduler.BukkitRunnable() {
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

                    // Efecto final mega épico
                    world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 2, 0), 120, 1.5, 1.5, 1.5, 0.8);
                    world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 2, 0), 60, 1, 1, 1, 0.3);
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, 1, 0), 40, 1, 1, 1, 0.1);
                    world.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                    world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
                    world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.3f, 2.0f);

                    player.sendMessage("");
                    player.sendMessage(SmallCaps.convert("§4§l✦ §c§lHas obtenido: §4§lDevoradora de Almas §4§l✦"));
                    player.sendMessage(SmallCaps.convert("§7  Click derecho: §cDesgarro Abismal §7(dash + barrido)"));
                    player.sendMessage(SmallCaps.convert("§7  Shift + Click: §4Rugido del Vacío §7(AOE masivo)"));
                    player.sendMessage(SmallCaps.convert("§7  Pasiva: §5Alma Hambrienta §7(cura + fuerza al matar)"));
                    player.sendMessage("");
                    return;
                }

                // Doble espiral ascendente roja/morada
                double angle1 = ticks * 0.25;
                double angle2 = angle1 + Math.PI;
                double radius = 1.8 - (ticks / 80.0) * 1.2;
                double y = ticks * 0.04 + 1;

                world.spawnParticle(Particle.DUST,
                        loc.clone().add(Math.cos(angle1) * radius, y, Math.sin(angle1) * radius), 3, 0.05, 0.05, 0.05,
                        0,
                        new Particle.DustOptions(Color.fromRGB(200, 0, 0), 1.8f));
                world.spawnParticle(Particle.DUST,
                        loc.clone().add(Math.cos(angle2) * radius, y, Math.sin(angle2) * radius), 3, 0.05, 0.05, 0.05,
                        0,
                        new Particle.DustOptions(Color.fromRGB(80, 0, 150), 1.8f));
                world.spawnParticle(Particle.SOUL_FIRE_FLAME,
                        loc.clone().add(Math.cos(angle1) * radius, y, Math.sin(angle1) * radius), 1, 0, 0, 0, 0.005);

                if (ticks % 8 == 0) {
                    world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 0.5f + ticks * 0.02f);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    public static ItemStack createAbyssBlade(CoreProtectPlugin plugin) {
        ItemStack blade = new ItemStack(Material.NETHERITE_AXE);
        ItemMeta meta = blade.getItemMeta();

        meta.setDisplayName(SmallCaps.convert("§4§l§k||§r §c§lDevoradora de Almas §4§l§k||"));

        meta.setLore(Arrays.asList(
                "§8§m                              ",
                "§7\"Nacida del corazón del Abismo\"",
                "§8§m                              ",
                "",
                "§c§l⚡ Habilidades:",
                "",
                "§c▸ Desgarro Abismal §7(Click derecho)",
                "§7  Dash corto + barrido AOE que",
                "§7  hace §c12 daño §7y aplica Wither 3s.",
                "§7  Cooldown: §e10s",
                "",
                "§4▸ Rugido del Vacío §7(Shift + Click der.)",
                "§7  AOE de 7 bloques que hace",
                "§7  §c10 daño §7+ Weakness II + Levitation.",
                "§7  Cooldown: §e25s",
                "",
                "§5▸ Alma Hambrienta §7(Pasiva)",
                "§7  Cada kill te cura §a3❤ §7y te da",
                "§7  Strength I por 5s.",
                "",
                "§8§m                              ",
                "§4§oEl Abismo nunca olvida..."));

        meta.addEnchant(Enchantment.SHARPNESS, 5, true);
        meta.addEnchant(Enchantment.UNBREAKING, 5, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        meta.getPersistentDataContainer().set(getKey(plugin), PersistentDataType.BYTE, (byte) 1);

        meta.addAttributeModifier(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE,
                new AttributeModifier(UUID.randomUUID(), "abyss_blade_damage", 9,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND));
        meta.addAttributeModifier(org.bukkit.attribute.Attribute.GENERIC_ATTACK_SPEED,
                new AttributeModifier(UUID.randomUUID(), "abyss_blade_speed", -2.4,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND));

        blade.setItemMeta(meta);
        return blade;
    }

    public static boolean isAbyssBlade(ItemStack item, CoreProtectPlugin plugin) {
        if (item == null || item.getType() != Material.NETHERITE_AXE)
            return false;
        if (!item.hasItemMeta())
            return false;
        return item.getItemMeta().getPersistentDataContainer().has(getKey(plugin), PersistentDataType.BYTE);
    }
}
