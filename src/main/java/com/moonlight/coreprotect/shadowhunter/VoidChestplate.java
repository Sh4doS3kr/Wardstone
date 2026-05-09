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
 * Item legendario "Elegía del Errante" — recompensa de la Misión 4.
 * Peto de Netherite con habilidades 100% PASIVAS:
 * - Manto de Sombras: 15% de esquivar ataques completamente
 * - Susurro del Errante: Regeneración lenta cuando estás por debajo del 50% HP
 * - Eco Protector: Resistencia I permanente mientras lo llevas puesto
 * - Voluntad Inquebrantable: Inmunidad a Wither y Weakness
 */
public class VoidChestplate {

    public static final String VOID_CHESTPLATE_KEY = "void_chestplate";

    public static NamespacedKey getKey(CoreProtectPlugin plugin) {
        return new NamespacedKey(plugin, VOID_CHESTPLATE_KEY);
    }

    public static void giveVoidChestplate(Player player, CoreProtectPlugin plugin) {
        ItemStack chestplate = createVoidChestplate(plugin);

        World world = player.getWorld();
        Location loc = player.getLocation();

        player.sendTitle( SmallCaps.convert("§5§l✦ Elegía del Errante ✦"), SmallCaps.convert("§7Su último regalo... su propia armadura..."), 10, 100, 30);

        new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 100) {
                    cancel();

                    if (player.getInventory().firstEmpty() != -1) {
                        player.getInventory().addItem(chestplate);
                    } else {
                        player.getWorld().dropItemNaturally(player.getLocation(), chestplate);
                    }

                    // Efecto final emotivo
                    world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 2, 0), 150, 2, 2, 2, 0.8);
                    world.spawnParticle(Particle.SOUL, loc.clone().add(0, 2, 0), 80, 2, 2, 2, 0.1);
                    world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 3, 0), 60, 1, 2, 1, 0.3);
                    world.spawnParticle(Particle.DUST, loc.clone().add(0, 2, 0), 100, 2, 2, 2, 0,
                            new Particle.DustOptions(Color.fromRGB(120, 50, 180), 3.0f));
                    world.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f);
                    world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.6f);
                    world.playSound(loc, Sound.ENTITY_WARDEN_DEATH, 0.3f, 1.5f);
                    world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 0.5f);

                    player.sendMessage("");
                    player.sendMessage(SmallCaps.convert("§5§l✦ §d§lHas obtenido: §5§lElegía del Errante §5§l✦"));
                    player.sendMessage(SmallCaps.convert("§7  §8▸ Manto de Sombras §7(Pasiva)"));
                    player.sendMessage(SmallCaps.convert("§7  Pasiva: §dSusurro del Errante §7(regeneración bajo 50% HP)"));
                    player.sendMessage(SmallCaps.convert("§7  Pasiva: §bEco Protector §7(Resistencia I permanente)"));
                    player.sendMessage(SmallCaps.convert("§7  Pasiva: §eVoluntad Inquebrantable §7(inmune a Wither/Weakness)"));
                    player.sendMessage("");
                    player.sendMessage(SmallCaps.convert("§7§o\"No me olvides...\""));
                    player.sendMessage("");
                    return;
                }

                // Animación: espiral ascendente triste, colores morado/azul que se desvanecen
                double progress = ticks / 100.0;
                double angle1 = ticks * 0.15;
                double angle2 = angle1 + Math.PI;
                double radius = 2.5 - progress * 2.0;
                double y = ticks * 0.035 + 0.5;

                // Espiral morada (el Errante)
                world.spawnParticle(Particle.DUST,
                        loc.clone().add(Math.cos(angle1) * radius, y, Math.sin(angle1) * radius),
                        3, 0.05, 0.05, 0.05, 0,
                        new Particle.DustOptions(Color.fromRGB(120, 50, 180), 2.0f));

                // Espiral azul (las lágrimas)
                world.spawnParticle(Particle.DUST,
                        loc.clone().add(Math.cos(angle2) * radius, y, Math.sin(angle2) * radius),
                        3, 0.05, 0.05, 0.05, 0,
                        new Particle.DustOptions(Color.fromRGB(50, 100, 200), 1.5f));

                // Almas ascendiendo (se va)
                if (ticks % 5 == 0) {
                    world.spawnParticle(Particle.SOUL,
                            loc.clone().add((Math.random() - 0.5) * 2, y, (Math.random() - 0.5) * 2),
                            1, 0, 0.2, 0, 0.02);
                }

                // Sonido melancólico creciente
                if (ticks % 12 == 0) {
                    world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.3f, 0.4f + (float) progress * 0.8f);
                }
                if (ticks % 25 == 0) {
                    world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.2f, 0.6f + (float) progress * 0.4f);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    public static ItemStack createVoidChestplate(CoreProtectPlugin plugin) {
        ItemStack chestplate = new ItemStack(Material.NETHERITE_CHESTPLATE);
        ItemMeta meta = chestplate.getItemMeta();

        meta.setDisplayName(SmallCaps.convert("§5§l§k||§r §d§lElegía del Errante §5§l§k||"));

        meta.setLore(Arrays.asList(
                "§8§m                              ",
                "§7\"Su última voluntad... su propia armadura.\"",
                "§7\"Lleva consigo el eco de quien fue.\"",
                "§8§m                              ",
                "",
                "§d§l✦ Habilidades Pasivas:",
                "",
                "§8▸ Manto de Sombras §7(Pasiva)",
                "§7  §a15% §7de probabilidad de §aesquivar",
                "§7  cualquier ataque completamente.",
                "",
                "§d▸ Susurro del Errante §7(Pasiva)",
                "§7  §aRegeneración I §7cuando tu vida",
                "§7  está por debajo del §c50%§7.",
                "",
                "§b▸ Eco Protector §7(Pasiva)",
                "§7  §aResistencia I §7permanente mientras",
                "§7  llevas el peto equipado.",
                "",
                "§e▸ Voluntad Inquebrantable §7(Pasiva)",
                "§7  Inmunidad total a los efectos",
                "§7  §cWither §7y §cWeakness§7.",
                "",
                "§8§m                              ",
                "§5§o\"No me olvides...\" — El Errante"));

        meta.addEnchant(Enchantment.PROTECTION, 5, true);
        meta.addEnchant(Enchantment.UNBREAKING, 10, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        meta.getPersistentDataContainer().set(getKey(plugin), PersistentDataType.BYTE, (byte) 1);

        // Armor attribute
        meta.addAttributeModifier(org.bukkit.attribute.Attribute.GENERIC_ARMOR,
                new AttributeModifier(UUID.randomUUID(), "void_chestplate_armor", 10,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.CHEST));
        meta.addAttributeModifier(org.bukkit.attribute.Attribute.GENERIC_ARMOR_TOUGHNESS,
                new AttributeModifier(UUID.randomUUID(), "void_chestplate_toughness", 4,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.CHEST));
        meta.addAttributeModifier(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH,
                new AttributeModifier(UUID.randomUUID(), "void_chestplate_health", 4,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.CHEST));

        chestplate.setItemMeta(meta);
        return chestplate;
    }

    public static boolean isVoidChestplate(ItemStack item, CoreProtectPlugin plugin) {
        if (item == null || item.getType() != Material.NETHERITE_CHESTPLATE)
            return false;
        if (!item.hasItemMeta())
            return false;
        return item.getItemMeta().getPersistentDataContainer().has(getKey(plugin), PersistentDataType.BYTE);
    }
}
