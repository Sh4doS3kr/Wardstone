package com.moonlight.coreprotect.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

/**
 * GUI con información sobre Cubees (pets)
 */
public class PetsInfoGUI {

    public static final String INVENTORY_TITLE = "Información de Cubees";

    public static void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, INVENTORY_TITLE);

        // Header - Información general
        ItemStack header = createItem(Material.BOOK, "§b§lInformación de Cubees",
                Arrays.asList(
                        "§7Los Cubees son criaturas adorables",
                        "§7que puedes capturar y equipar como mascotas.",
                        "",
                        "§eComando: §f/pets equip <cubee>"
                ));
        inv.setItem(4, header);

        // Crafteo de Bocadillo Cubee
        ItemStack snack = createItem(Material.POTATO, "§b§lBocadillo Cubee",
                Arrays.asList(
                        "§7Usa para capturar Cubees Dizzy.",
                        "",
                        "§eReceta de crafteo:",
                        "§f  S  ",
                        "§f SPS ",
                        "§f  S  ",
                        "",
                        "§fS §7= Semillas de trigo (x4)",
                        "§fP §7= Papa (x1)",
                        "",
                        "§eProbabilidad de captura: §f5.25%"
                ));
        inv.setItem(11, snack);

        // Cómo capturar
        ItemStack capture = createItem(Material.LEAD, "§a§lCómo Capturar",
                Arrays.asList(
                        "§71. Encuentra un Cubee en el mundo",
                        "§72. Débitalo hasta estado §eDizzy §7(mareado)",
                        "§73. Craftea un §bBocadillo Cubee",
                        "§74. Click derecho en el Cubee Dizzy",
                        "§75. ¡5.25% de probabilidad de éxito!"
                ));
        inv.setItem(13, capture);

        // Cómo equipar
        ItemStack equip = createItem(Material.NAME_TAG, "§d§lCómo Equipar",
                Arrays.asList(
                        "§7Una vez capturado, usa:",
                        "",
                        "§e/pets list §7- Ver tus Cubees",
                        "§e/pets equip <nombre> §7- Equipar",
                        "§e/pets unequip §7- Desequipar",
                        "§e/pets info §7- Información del cubee",
                        "",
                        "§7El cubee te seguirá y te ayudará en combate."
                ));
        inv.setItem(15, equip);

        // Tipos de Cubees - Biomas
        ItemStack biomes = createItem(Material.OAK_LEAVES, "§6§lCubees de Bioma",
                Arrays.asList(
                        "§7Aparecen en biomas específicos:",
                        "",
                        "§f• Cubee de Badlands §7- Badlands",
                        "§f• Cubee de Playa §7- Playas",
                        "§f• Cubee de Abedul §7- Bosque de abedul",
                        "§f• Cubee de Cerezo §7- Cherry Grove",
                        "§f• Cubee de Roble Oscuro §7- Dark Forest",
                        "§f• Cubee de Taiga §7- Taiga",
                        "§f• Cubee del Profundo Oscuro §7- Deep Dark",
                        "§f• Cubee de Espeleotema §7- Dripstone Caves"
                ));
        inv.setItem(20, biomes);

        // Cubees Mágicos
        ItemStack magical = createItem(Material.ENCHANTED_BOOK, "§5§lCubees Mágicos",
                Arrays.asList(
                        "§7Cubees especiales con habilidades:",
                        "",
                        "§f• Bruja Ártica Cubee §7- Hielo y magia",
                        "§f• Guardián del Bosque Cubee §7- Curación",
                        "§f• Meowgician Cubee §7- Magia felina",
                        "§f• Oso de los Deseos Cubee §7- Deseos",
                        "",
                        "§e¡Más raros y poderosos!"
                ));
        inv.setItem(22, magical);

        // Cubees de Cueva
        ItemStack cave = createItem(Material.AMETHYST_CLUSTER, "§3§lCubees de Cueva",
                Arrays.asList(
                        "§7Solo aparecen en cuevas:",
                        "",
                        "§f• Cubee del Profundo Oscuro §7- Deep Dark",
                        "§f• Cubee de Espeleotema §7- Dripstone Caves",
                        "",
                        "§7Cuidado con el Warden..."
                ));
        inv.setItem(24, cave);

        // Información adicional
        ItemStack info = createItem(Material.PAPER, "§7§lInformación Adicional",
                Arrays.asList(
                        "§7Los Cubees tienen:",
                        "",
                        "§e• Niveles §7- Mejoran con experiencia",
                        "§e• Habilidades §7- Atacan y curan",
                        "§e• Cooldowns §7- Tiempo entre habilidades",
                        "",
                        "§7Alimenta a tu cubee con Bocadillo",
                        "§7para darle experiencia y subir nivel."
                ));
        inv.setItem(31, info);

        // Cerrar
        ItemStack close = createItem(Material.BARRIER, "§c§lCerrar",
                Arrays.asList("§7Click para cerrar"));
        inv.setItem(49, close);

        player.openInventory(inv);
    }

    private static ItemStack createItem(Material material, String name, java.util.List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
