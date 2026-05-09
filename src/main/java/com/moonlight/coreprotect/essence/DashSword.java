package com.moonlight.coreprotect.essence;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

/**
 * "Filo Fantasma" — Espada de Netherite con habilidad de Dash.
 * Click derecho: Dash rápido con partículas y animación.
 * Si impacta a un jugador/entidad durante el dash, hace daño bonus.
 * Cooldown: 1.75 segundos.
 * Coste: 75 Esencias.
 */
public class DashSword {

    public static final String DASH_SWORD_KEY = "dash_sword";

    public static NamespacedKey getKey(CoreProtectPlugin plugin) {
        return new NamespacedKey(plugin, DASH_SWORD_KEY);
    }

    public static boolean isDashSword(ItemStack item, CoreProtectPlugin plugin) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(getKey(plugin), PersistentDataType.BYTE);
    }

    public static ItemStack create(CoreProtectPlugin plugin) {
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = sword.getItemMeta();

        meta.setDisplayName(SmallCaps.convert("§b§l§k||§r §b§lFilo Fantasma §b§l§k||"));

        meta.setLore(Arrays.asList(
                SmallCaps.convert("§8§m                              "),
                SmallCaps.convert("§b\"Una estela de sombras... y luego,"),
                SmallCaps.convert("§b el silencio del acero.\""),
                SmallCaps.convert("§8§m                              "),
                "",
                SmallCaps.convert("§b§l⚡ Habilidad Activa:"),
                "",
                SmallCaps.convert("§3▸ Embestida Fantasma §7(Click Derecho)"),
                SmallCaps.convert("§7  Te lanzas hacia donde miras"),
                SmallCaps.convert("§7  dejando una §bestela de partículas§7."),
                SmallCaps.convert("§7  Si §aimpactas §7a un enemigo durante"),
                SmallCaps.convert("§7  el dash, le infliges §c§ldaño bonus§7."),
                "",
                SmallCaps.convert("§7  Cooldown: §e1.75s"),
                "",
                SmallCaps.convert("§8§m                              "),
                SmallCaps.convert("§b§o\"Lo que no ves... ya te ha cortado.\"")
        ));

        meta.addEnchant(Enchantment.SHARPNESS, 5, true);
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.addEnchant(Enchantment.SWEEPING_EDGE, 3, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        meta.getPersistentDataContainer().set(getKey(plugin), PersistentDataType.BYTE, (byte) 1);

        sword.setItemMeta(meta);
        return sword;
    }
}
