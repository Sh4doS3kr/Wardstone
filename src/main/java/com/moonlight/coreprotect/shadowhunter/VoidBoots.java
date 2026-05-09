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
 * Item legendario "Pisada del Olvido" — recompensa de la Misión 7: El Vientre del Vacío.
 * Botas de Netherite con habilidades pasivas:
 * - Paso del Vacío: Inmunidad total al daño por caída
 * - Celeridad Sombría: Speed II permanente
 * - Huella del Olvido: Partículas de sombra al caminar
 * - Anclaje Dimensional: Inmunidad a knockback
 */
public class VoidBoots {

    public static final String VOID_BOOTS_KEY = "void_boots";

    public static NamespacedKey getKey(CoreProtectPlugin plugin) {
        return new NamespacedKey(plugin, VOID_BOOTS_KEY);
    }

    public static void giveVoidBoots(Player player, CoreProtectPlugin plugin) {
        ItemStack boots = createVoidBoots(plugin);

        World world = player.getWorld();
        Location loc = player.getLocation();

        player.sendTitle(
                SmallCaps.convert("§4§l☠ Pisada del Olvido ☠"),
                SmallCaps.convert("§8El vacío se aferra a tus pies para siempre..."),
                10, 100, 30);

        new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 120) {
                    cancel();

                    if (player.getInventory().firstEmpty() != -1) {
                        player.getInventory().addItem(boots);
                    } else {
                        player.getWorld().dropItemNaturally(player.getLocation(), boots);
                    }

                    // Efecto final: explosión de oscuridad
                    world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 2, 0), 200, 2, 2, 2, 1.0);
                    world.spawnParticle(Particle.SOUL, loc.clone().add(0, 1, 0), 120, 2, 3, 2, 0.15);
                    world.spawnParticle(Particle.DUST, loc.clone().add(0, 1, 0), 150, 3, 3, 3, 0,
                            new Particle.DustOptions(Color.fromRGB(20, 0, 30), 3.5f));
                    world.spawnParticle(Particle.SMOKE, loc.clone().add(0, 2, 0), 100, 2, 2, 2, 0.15);
                    world.spawnParticle(Particle.SCULK_SOUL, loc.clone().add(0, 1, 0), 60, 2, 2, 2, 0.1);

                    world.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.5f);
                    world.playSound(loc, Sound.ENTITY_WARDEN_ROAR, 0.8f, 0.3f);
                    world.playSound(loc, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.6f, 0.4f);
                    world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.4f);

                    player.sendMessage("");
                    player.sendMessage(SmallCaps.convert("§4§l☠ §c§lHas obtenido: §4§lPisada del Olvido §4§l☠"));
                    player.sendMessage(SmallCaps.convert("§7  §4▸ Paso del Vacío §7(Inmune a daño por caída)"));
                    player.sendMessage(SmallCaps.convert("§7  §8▸ Celeridad Sombría §7(Speed II permanente)"));
                    player.sendMessage(SmallCaps.convert("§7  §5▸ Huella del Olvido §7(Partículas de sombra)"));
                    player.sendMessage(SmallCaps.convert("§7  §6▸ Anclaje Dimensional §7(Inmune a knockback)"));
                    player.sendMessage("");
                    player.sendMessage(SmallCaps.convert("§4§o\"Lo que pisa el Olvido... nunca vuelve a crecer.\""));
                    player.sendMessage("");
                    return;
                }

                // Animación: vórtice oscuro que sube desde el suelo
                double progress = ticks / 120.0;
                double angle1 = ticks * 0.2;
                double angle2 = angle1 + Math.PI;
                double angle3 = angle1 + Math.PI * 0.5;
                double radius = 3.0 - progress * 2.5;
                double y = ticks * 0.02;

                // Espiral negra/morada (oscuridad)
                world.spawnParticle(Particle.DUST,
                        loc.clone().add(Math.cos(angle1) * radius, y, Math.sin(angle1) * radius),
                        3, 0.05, 0.05, 0.05, 0,
                        new Particle.DustOptions(Color.fromRGB(20, 0, 40), 2.5f));

                // Espiral roja (sangre del vacío)
                world.spawnParticle(Particle.DUST,
                        loc.clone().add(Math.cos(angle2) * radius, y, Math.sin(angle2) * radius),
                        3, 0.05, 0.05, 0.05, 0,
                        new Particle.DustOptions(Color.fromRGB(80, 0, 0), 2.0f));

                // Espiral gris (ceniza)
                world.spawnParticle(Particle.DUST,
                        loc.clone().add(Math.cos(angle3) * radius, y * 0.8, Math.sin(angle3) * radius),
                        2, 0.05, 0.05, 0.05, 0,
                        new Particle.DustOptions(Color.fromRGB(40, 40, 40), 1.8f));

                // Humo subiendo desde el suelo
                if (ticks % 2 == 0) {
                    world.spawnParticle(Particle.SMOKE,
                            loc.clone().add((Math.random() - 0.5) * 3, 0.1, (Math.random() - 0.5) * 3),
                            2, 0.1, 0.3, 0.1, 0.01);
                }

                // Almas del vacío ascendiendo
                if (ticks % 4 == 0) {
                    world.spawnParticle(Particle.SCULK_SOUL,
                            loc.clone().add((Math.random() - 0.5) * 2, y * 0.5, (Math.random() - 0.5) * 2),
                            1, 0, 0.2, 0, 0.03);
                }

                // Sonido sombrío periódico
                if (ticks % 20 == 0) {
                    world.playSound(loc, Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.3f, 0.3f + (float) progress * 0.5f);
                }

                if (ticks % 30 == 0) {
                    world.playSound(loc, Sound.ENTITY_WARDEN_HEARTBEAT, 0.5f, 0.5f);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    public static ItemStack createVoidBoots(CoreProtectPlugin plugin) {
        ItemStack boots = new ItemStack(Material.NETHERITE_BOOTS);
        ItemMeta meta = boots.getItemMeta();

        meta.setDisplayName(SmallCaps.convert("§4§l§k||§r §4§lPisada del Olvido §4§l§k||"));

        meta.setLore(Arrays.asList(
                "§8§m                              ",
                "§4\"Donde pisa el Olvido, la tierra muere.",
                "§4 Donde caminas tú... la tierra tiembla.\"",
                "§8§m                              ",
                "",
                "§4§l☠ Habilidades Pasivas:",
                "",
                "§4▸ Paso del Vacío §7(Pasiva)",
                "§7  §aInmunidad total §7al daño por",
                "§7  §ecaída§7. El vacío te sostiene.",
                "",
                "§8▸ Celeridad Sombría §7(Pasiva)",
                "§7  §aSpeed II §7permanente. Te mueves",
                "§7  como las §8sombras§7.",
                "",
                "§5▸ Huella del Olvido §7(Pasiva)",
                "§7  Dejas partículas de §5oscuridad",
                "§7  a tu paso. §8Los muertos te siguen§7.",
                "",
                "§6▸ Anclaje Dimensional §7(Pasiva)",
                "§7  §aInmunidad total §7al §cknockback§7.",
                "§7  Nada te mueve de tu lugar.",
                "",
                "§8§m                              ",
                "§4§lSet del Vacío §7(7/7)",
                "§8▸ §5Hoja del Vacío §7(Espada)",
                "§8▸ §4Devoradora de Almas §7(Hacha)",
                "§8▸ §0Fractura del Vacío §7(Pico)",
                "§8▸ §5Elegía del Errante §7(Peto)",
                "§8▸ §3Último Suspiro §7(Arco)",
                "§8▸ §bLágrimas de Lyra §7(Pantalones)",
                "§8▸ §4Pisada del Olvido §7(Botas) §a✔",
                "§8§m                              ",
                "§4§o\"Lo que pisa el Olvido... nunca vuelve a crecer.\""));

        meta.addEnchant(Enchantment.PROTECTION, 5, true);
        meta.addEnchant(Enchantment.FEATHER_FALLING, 5, true);
        meta.addEnchant(Enchantment.UNBREAKING, 10, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.addEnchant(Enchantment.DEPTH_STRIDER, 3, true);
        meta.addEnchant(Enchantment.SOUL_SPEED, 3, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        meta.getPersistentDataContainer().set(getKey(plugin), PersistentDataType.BYTE, (byte) 1);

        // Armor attributes
        meta.addAttributeModifier(Attribute.GENERIC_ARMOR,
                new AttributeModifier(UUID.randomUUID(), "void_boots_armor", 5,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.FEET));
        meta.addAttributeModifier(Attribute.GENERIC_ARMOR_TOUGHNESS,
                new AttributeModifier(UUID.randomUUID(), "void_boots_toughness", 4,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.FEET));
        meta.addAttributeModifier(Attribute.GENERIC_MAX_HEALTH,
                new AttributeModifier(UUID.randomUUID(), "void_boots_health", 4,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.FEET));
        meta.addAttributeModifier(Attribute.GENERIC_MOVEMENT_SPEED,
                new AttributeModifier(UUID.randomUUID(), "void_boots_speed", 0.015,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.FEET));
        meta.addAttributeModifier(Attribute.GENERIC_KNOCKBACK_RESISTANCE,
                new AttributeModifier(UUID.randomUUID(), "void_boots_kb", 1.0,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.FEET));

        boots.setItemMeta(meta);
        return boots;
    }

    public static boolean isVoidBoots(ItemStack item, CoreProtectPlugin plugin) {
        if (item == null || item.getType() != Material.NETHERITE_BOOTS)
            return false;
        if (!item.hasItemMeta())
            return false;
        return item.getItemMeta().getPersistentDataContainer().has(getKey(plugin), PersistentDataType.BYTE);
    }
}
