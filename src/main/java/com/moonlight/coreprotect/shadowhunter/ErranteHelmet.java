package com.moonlight.coreprotect.shadowhunter;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

/**
 * Casco del Errante — recompensa de la Misión 10: El Fin.
 * El último fragmento del ser que caminó entre mundos.
 *
 * Habilidades:
 * - Escudo del Errante (Shift + Click Derecho): AoE Resistencia II + Regeneración II 8s (CD 20s)
 * - Paso entre Mundos (Shift + Click Izquierdo): Teleport corto en la dirección que miras (CD 10s)
 * - Aura del Vacío (Pasiva): Mobs hostiles cercanos reciben Lentitud y Debilidad
 */
public class ErranteHelmet {

    public static final String HELMET_KEY = "errante_helmet";

    public static NamespacedKey getKey(CoreProtectPlugin plugin) {
        return new NamespacedKey(plugin, HELMET_KEY);
    }

    public static boolean isErranteHelmet(ItemStack item, CoreProtectPlugin plugin) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(getKey(plugin), PersistentDataType.BYTE);
    }

    public static void giveErranteHelmet(Player player, CoreProtectPlugin plugin) {
        ItemStack helmet = create(plugin);

        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(helmet);
        } else {
            player.getWorld().dropItemNaturally(player.getLocation(), helmet);
        }
    }

    public static ItemStack create(CoreProtectPlugin plugin) {
        ItemStack helmet = new ItemStack(Material.NETHERITE_HELMET);
        ItemMeta meta = helmet.getItemMeta();
        meta.setDisplayName(SmallCaps.convert("§5§l✦ Casco del Errante §5§l✦"));
        meta.setLore(Arrays.asList(
                "",
                SmallCaps.convert("§7El último fragmento del alma del Errante."),
                SmallCaps.convert("§7Quien lo porta, lleva consigo la memoria"),
                SmallCaps.convert("§7de un ser que caminó entre mundos"),
                SmallCaps.convert("§7durante milenios... y eligió morir"),
                SmallCaps.convert("§7para que otros pudieran vivir."),
                "",
                SmallCaps.convert("§5§o\"No me olvides... por favor.\""),
                "",
                SmallCaps.convert("§8§m                              "),
                SmallCaps.convert("§5§l⚡ Habilidades Pasivas:"),
                "",
                SmallCaps.convert("§5▸ Memoria del Errante"),
                SmallCaps.convert("§7  Regeneración I permanente"),
                SmallCaps.convert("§7  mientras llevas el casco."),
                "",
                SmallCaps.convert("§5▸ Aura del Vacío"),
                SmallCaps.convert("§7  Mobs hostiles en §e5 bloques §7sufren"),
                SmallCaps.convert("§7  §cLentitud §7y §cDebilidad§7."),
                "",
                SmallCaps.convert("§5▸ Último Aliento"),
                SmallCaps.convert("§7  Al recibir daño letal, te salva"),
                SmallCaps.convert("§7  con §c4♥ §7+ invulnerabilidad §e3s§7."),
                SmallCaps.convert("§7  Cooldown: §e30 minutos"),
                "",
                SmallCaps.convert("§8§m                              "),
                SmallCaps.convert("§5✦ Protección Absoluta V"),
                SmallCaps.convert("§5✦ Espinas III"),
                SmallCaps.convert("§5✦ Respiración III"),
                SmallCaps.convert("§5✦ Afinidad Acuática I"),
                SmallCaps.convert("§5✦ Irrompible"),
                SmallCaps.convert("§8§m                              "),
                "",
                SmallCaps.convert("§8Misión 10: El Fin"),
                SmallCaps.convert("§8— El Errante —")
        ));
        meta.setUnbreakable(true);
        meta.addEnchant(Enchantment.PROTECTION, 5, true);
        meta.addEnchant(Enchantment.THORNS, 3, true);
        meta.addEnchant(Enchantment.RESPIRATION, 3, true);
        meta.addEnchant(Enchantment.AQUA_AFFINITY, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        meta.getPersistentDataContainer().set(getKey(plugin), PersistentDataType.BYTE, (byte) 1);

        helmet.setItemMeta(meta);
        return helmet;
    }
}
