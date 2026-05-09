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
 * Item legendario "Lágrimas de Lyra" — recompensa de la Misión 6.
 * Pantalones de Netherite con habilidades pasivas:
 * - Reflejo Etéreo: 15% de probabilidad de reducir daño a la mitad
 * - Lágrimas Sanadoras: Regeneración lenta bajo la lluvia o en agua
 * - Velo del Vacío: Speed I permanente mientras estás agachado
 * - Consuelo Eterno: Inmunidad a Slowness y Mining Fatigue
 */
public class VoidLeggings {

    public static final String VOID_LEGGINGS_KEY = "void_leggings";

    public static NamespacedKey getKey(CoreProtectPlugin plugin) {
        return new NamespacedKey(plugin, VOID_LEGGINGS_KEY);
    }

    public static void giveVoidLeggings(Player player, CoreProtectPlugin plugin) {
        ItemStack leggings = createVoidLeggings(plugin);

        World world = player.getWorld();
        Location loc = player.getLocation();

        player.sendTitle(SmallCaps.convert("§b§l✦ Lágrimas de Lyra ✦"), SmallCaps.convert("§7Sus lágrimas se convierten en tu armadura..."), 10, 100, 30);

        new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 100) {
                    cancel();

                    if (player.getInventory().firstEmpty() != -1) {
                        player.getInventory().addItem(leggings);
                    } else {
                        player.getWorld().dropItemNaturally(player.getLocation(), leggings);
                    }

                    // Efecto final: lluvia de lágrimas de alma
                    world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 2, 0), 150, 2, 2, 2, 0.8);
                    world.spawnParticle(Particle.SOUL, loc.clone().add(0, 3, 0), 100, 2, 3, 2, 0.1);
                    world.spawnParticle(Particle.DRIPPING_WATER, loc.clone().add(0, 4, 0), 80, 3, 1, 3, 0);
                    world.spawnParticle(Particle.DUST, loc.clone().add(0, 2, 0), 100, 2, 2, 2, 0,
                            new Particle.DustOptions(Color.fromRGB(100, 180, 220), 3.0f));
                    world.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f);
                    world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.6f);
                    world.playSound(loc, Sound.ENTITY_ALLAY_DEATH, 0.8f, 0.5f);
                    world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 0.5f);

                    player.sendMessage("");
                    player.sendMessage(SmallCaps.convert("§b§l✦ §d§lHas obtenido: §b§lLágrimas de Lyra §b§l✦"));
                    player.sendMessage(SmallCaps.convert("§7  §8▸ Reflejo Etéreo §7(15% reducir daño)"));
                    player.sendMessage(SmallCaps.convert("§7  §b▸ Lágrimas Sanadoras §7(regen bajo lluvia/agua)"));
                    player.sendMessage(SmallCaps.convert("§7  §9▸ Velo del Vacío §7(Speed I agachado)"));
                    player.sendMessage(SmallCaps.convert("§7  §e▸ Consuelo Eterno §7(inmune Slowness/Mining Fatigue)"));
                    player.sendMessage("");
                    player.sendMessage(SmallCaps.convert("§7§o\"Ya no lloro, papá... ahora descanso.\""));
                    player.sendMessage("");
                    return;
                }

                // Animación: lágrimas cayendo en espiral, azul/cian
                double progress = ticks / 100.0;
                double angle1 = ticks * 0.15;
                double angle2 = angle1 + Math.PI;
                double radius = 2.5 - progress * 2.0;
                double y = ticks * 0.035 + 0.5;

                // Espiral azul (lágrimas)
                world.spawnParticle(Particle.DUST,
                        loc.clone().add(Math.cos(angle1) * radius, y, Math.sin(angle1) * radius),
                        3, 0.05, 0.05, 0.05, 0,
                        new Particle.DustOptions(Color.fromRGB(100, 180, 220), 2.0f));

                // Espiral blanca (paz)
                world.spawnParticle(Particle.DUST,
                        loc.clone().add(Math.cos(angle2) * radius, y, Math.sin(angle2) * radius),
                        3, 0.05, 0.05, 0.05, 0,
                        new Particle.DustOptions(Color.fromRGB(220, 240, 255), 1.5f));

                // Gotas de agua cayendo (lágrimas)
                if (ticks % 3 == 0) {
                    world.spawnParticle(Particle.DRIPPING_WATER,
                            loc.clone().add((Math.random() - 0.5) * 3, 3 + Math.random(), (Math.random() - 0.5) * 3),
                            1, 0, 0, 0, 0);
                }

                // Almas ascendiendo
                if (ticks % 5 == 0) {
                    world.spawnParticle(Particle.SOUL,
                            loc.clone().add((Math.random() - 0.5) * 2, y, (Math.random() - 0.5) * 2),
                            1, 0, 0.2, 0, 0.02);
                }

                // Sonido melancólico
                if (ticks % 15 == 0) {
                    world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.3f, 0.6f + (float) progress * 0.6f);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    public static ItemStack createVoidLeggings(CoreProtectPlugin plugin) {
        ItemStack leggings = new ItemStack(Material.NETHERITE_LEGGINGS);
        ItemMeta meta = leggings.getItemMeta();

        meta.setDisplayName(SmallCaps.convert("§b§l§k||§r §b§lLágrimas de Lyra §b§l§k||"));

        meta.setLore(Arrays.asList(
                "§8§m                              ",
                "§7\"Cada lágrima que derramó se convirtió",
                "§7 en armadura... para quien la recordara.\"",
                "§8§m                              ",
                "",
                "§b§l✦ Habilidades Pasivas:",
                "",
                "§8▸ Reflejo Etéreo §7(Pasiva)",
                "§7  §a15% §7de probabilidad de §areducir",
                "§7  cualquier daño a la §amitad§7.",
                "",
                "§b▸ Lágrimas Sanadoras §7(Pasiva)",
                "§7  §aRegeneración I §7cuando estás bajo",
                "§7  la §blluvia §7o dentro del §bagua§7.",
                "",
                "§9▸ Velo del Vacío §7(Pasiva)",
                "§7  §aSpeed I §7permanente mientras",
                "§7  estás §eagachado§7.",
                "",
                "§e▸ Consuelo Eterno §7(Pasiva)",
                "§7  Inmunidad total a los efectos",
                "§7  §cSlowness §7y §cMining Fatigue§7.",
                "",
                "§8§m                              ",
                "§b§o\"Ya no lloro, papá...\" — Lyra"));

        meta.addEnchant(Enchantment.PROTECTION, 5, true);
        meta.addEnchant(Enchantment.UNBREAKING, 10, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        meta.getPersistentDataContainer().set(getKey(plugin), PersistentDataType.BYTE, (byte) 1);

        // Armor attributes
        meta.addAttributeModifier(Attribute.ARMOR,
                new AttributeModifier(UUID.randomUUID(), "void_leggings_armor", 8,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.LEGS));
        meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS,
                new AttributeModifier(UUID.randomUUID(), "void_leggings_toughness", 4,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.LEGS));
        meta.addAttributeModifier(Attribute.MAX_HEALTH,
                new AttributeModifier(UUID.randomUUID(), "void_leggings_health", 4,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.LEGS));

        leggings.setItemMeta(meta);
        return leggings;
    }

    public static boolean isVoidLeggings(ItemStack item, CoreProtectPlugin plugin) {
        if (item == null || item.getType() != Material.NETHERITE_LEGGINGS)
            return false;
        if (!item.hasItemMeta())
            return false;
        return item.getItemMeta().getPersistentDataContainer().has(getKey(plugin), PersistentDataType.BYTE);
    }
}
