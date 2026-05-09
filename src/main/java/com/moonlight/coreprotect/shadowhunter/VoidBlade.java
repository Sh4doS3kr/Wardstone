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
 * Item legendario "Hoja del Vacío" - recompensa de la misión El Cazador de Sombras.
 * Habilidades:
 * - Golpe Sombrío (Click derecho): proyectil de sombra, 15 daño + ceguera 3s. CD: 8s
 * - Furia del Vacío (Shift + Click derecho): AOE 5 bloques, 8 daño + Slowness III. CD: 20s
 * - Sed de Sombras (Pasiva): cada kill cura 2❤ + Speed I 3s
 */
public class VoidBlade {

    public static final String VOID_BLADE_KEY = "void_blade";

    public static NamespacedKey getKey(CoreProtectPlugin plugin) {
        return new NamespacedKey(plugin, VOID_BLADE_KEY);
    }

    /**
     * Crea y da la Hoja del Vacío al jugador con animación.
     */
    public static void giveVoidBlade(Player player, CoreProtectPlugin plugin) {
        ItemStack blade = createVoidBlade(plugin);

        // Animación de entrega
        World world = player.getWorld();
        Location loc = player.getLocation();

        // Item flotante antes de darlo
        player.sendTitle( SmallCaps.convert("§5§l✦ Hoja del Vacío ✦"), SmallCaps.convert("§7Un arma forjada en las sombras..."), 10, 80, 20);

        // Partículas de entrega
        new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 60) {
                    cancel();

                    // Dar el item
                    if (player.getInventory().firstEmpty() != -1) {
                        player.getInventory().addItem(blade);
                    } else {
                        player.getWorld().dropItemNaturally(player.getLocation(), blade);
                    }

                    // Efecto final
                    world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 2, 0), 80, 1, 1, 1, 0.5);
                    world.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                    world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);

                    player.sendMessage("");
                    player.sendMessage(SmallCaps.convert("§5§l✦ §d§lHas obtenido: §5§lHoja del Vacío §5§l✦"));
                    player.sendMessage(SmallCaps.convert("§7  Click derecho: §bGolpe Sombrío §7(proyectil sombra)"));
                    player.sendMessage(SmallCaps.convert("§7  Shift + Click: §cFuria del Vacío §7(AOE explosivo)"));
                    player.sendMessage(SmallCaps.convert("§7  Pasiva: §5Sed de Sombras §7(cura al matar)"));
                    player.sendMessage("");
                    return;
                }

                // Espiral ascendente morada
                double angle = ticks * 0.3;
                double radius = 1.5 - (ticks / 60.0) * 1.0;
                double x = Math.cos(angle) * radius;
                double z = Math.sin(angle) * radius;
                double y = ticks * 0.05 + 1;

                world.spawnParticle(Particle.DUST,
                    loc.clone().add(x, y, z), 3, 0.05, 0.05, 0.05, 0,
                    new Particle.DustOptions(Color.fromRGB(150, 0, 255), 1.5f));
                world.spawnParticle(Particle.END_ROD,
                    loc.clone().add(x, y, z), 1, 0, 0, 0, 0.01);

                if (ticks % 10 == 0) {
                    world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 0.8f + ticks * 0.02f);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    /**
     * Crea el ItemStack de la Hoja del Vacío.
     */
    public static ItemStack createVoidBlade(CoreProtectPlugin plugin) {
        ItemStack blade = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = blade.getItemMeta();

        meta.setDisplayName(SmallCaps.convert("§5§l§k||§r §5§lHoja del Vacío §5§l§k||"));

        meta.setLore(Arrays.asList(
            "§8§m                              ",
            "§7\"Forjada en la oscuridad entre mundos\"",
            "§8§m                              ",
            "",
            "§d§l⚡ Habilidades:",
            "",
            "§b▸ Golpe Sombrío §7(Click derecho)",
            "§7  Lanza un proyectil de sombra que",
            "§7  hace §c15 daño §7y ciega 3s.",
            "§7  Cooldown: §e8s",
            "",
            "§c▸ Furia del Vacío §7(Shift + Click der.)",
            "§7  Dañas a todos en 5 bloques",
            "§7  por §c8 daño §7+ Slowness III.",
            "§7  Cooldown: §e20s",
            "",
            "§5▸ Sed de Sombras §7(Pasiva)",
            "§7  Cada kill te cura §a2❤ §7y te da",
            "§7  Speed I por 3s.",
            "",
            "§8§m                              ",
            "§7§oEl vacío te observa..."
        ));

        // Encantamientos
        meta.addEnchant(Enchantment.SHARPNESS, 5, true);
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        // PersistentDataContainer para identificar el item
        meta.getPersistentDataContainer().set(getKey(plugin), PersistentDataType.BYTE, (byte) 1);

        // Atributos custom
        meta.addAttributeModifier(org.bukkit.attribute.Attribute.ATTACK_DAMAGE,
            new AttributeModifier(UUID.randomUUID(), "void_blade_damage", 8, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND));
        meta.addAttributeModifier(org.bukkit.attribute.Attribute.ATTACK_SPEED,
            new AttributeModifier(UUID.randomUUID(), "void_blade_speed", -2.4, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND));

        blade.setItemMeta(meta);
        return blade;
    }

    /**
     * Verifica si un ItemStack es la Hoja del Vacío.
     */
    public static boolean isVoidBlade(ItemStack item, CoreProtectPlugin plugin) {
        if (item == null || item.getType() != Material.NETHERITE_SWORD) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(getKey(plugin), PersistentDataType.BYTE);
    }
}
