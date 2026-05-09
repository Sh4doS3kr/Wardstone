package com.moonlight.coreprotect.essence;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

/**
 * "Colmillo Sísmico" — Espada Netherite con habilidad de Terremoto.
 * Mantener Click Derecho: la espada vibra y brilla (animación de carga).
 * Al caer desde una altura, genera un terremoto proporcional a la altura.
 * Cooldown: 18 segundos.
 * Coste: 85 Esencias.
 */
public class EarthquakeSword {

    public static final String EARTHQUAKE_SWORD_KEY = "earthquake_sword";

    public static NamespacedKey getKey(CoreProtectPlugin plugin) {
        return new NamespacedKey(plugin, EARTHQUAKE_SWORD_KEY);
    }

    public static boolean isEarthquakeSword(ItemStack item, CoreProtectPlugin plugin) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(getKey(plugin), PersistentDataType.BYTE);
    }

    public static ItemStack create(CoreProtectPlugin plugin) {
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = sword.getItemMeta();

        meta.setDisplayName(SmallCaps.convert("§6§l§k||§r §6§lColmillo Sísmico §6§l§k||"));

        meta.setLore(Arrays.asList(
                SmallCaps.convert("§8§m                              "),
                SmallCaps.convert("§6\"La tierra tiembla donde caigo..."),
                SmallCaps.convert("§6 y el suelo recuerda mi peso.\""),
                SmallCaps.convert("§8§m                              "),
                "",
                SmallCaps.convert("§6§l⚡ Habilidad Activa:"),
                "",
                SmallCaps.convert("§e▸ Impacto Sísmico §7(Click Derecho + Caer)"),
                SmallCaps.convert("§7  §eMantén Click Derecho §7para §6cargar"),
                SmallCaps.convert("§7  la espada. La hoja §evibrará§7."),
                SmallCaps.convert("§7  Al §ccaer§7, genera un §6§lterremoto"),
                SmallCaps.convert("§7  proporcional a la §caltura de caída§7."),
                "",
                SmallCaps.convert("§7  §c• 3-5 bloques§7: Onda pequeña (r=4)"),
                SmallCaps.convert("§7  §c• 5-10 bloques§7: Terremoto medio (r=6)"),
                SmallCaps.convert("§7  §c• 10+ bloques§7: §4§lMEGATERREMOTO§7 (r=9)"),
                "",
                SmallCaps.convert("§7  Cooldown: §e18s"),
                SmallCaps.convert("§7  §8(Más altura = más daño y radio)"),
                "",
                SmallCaps.convert("§8§m                              "),
                SmallCaps.convert("§6§o\"La furia de la tierra, en tu hoja.\"")
        ));

        meta.addEnchant(Enchantment.SHARPNESS, 5, true);
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.addEnchant(Enchantment.KNOCKBACK, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        meta.getPersistentDataContainer().set(getKey(plugin), PersistentDataType.BYTE, (byte) 1);

        sword.setItemMeta(meta);
        return sword;
    }
}
