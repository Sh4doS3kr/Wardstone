package com.moonlight.coreprotect.rpg.gui;

import com.moonlight.coreprotect.rpg.RPGPlayer;
import com.moonlight.coreprotect.rpg.RPGStats;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

public class StatsGUI {

    public static final String TITLE = ChatColor.DARK_GREEN + "📊 Estadísticas RPG";

    public static void open(Player player, RPGPlayer rp) {
        Inventory inv = Bukkit.createInventory(null, 45, TITLE);
        RPGStats stats = rp.getStats();

        ItemStack border = item(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 45; i++) inv.setItem(i, border);

        // Player info - slot 4
        String className = rp.hasClass() ? rp.getRpgClass().getColoredName() : ChatColor.GRAY + "Sin clase";
        String subName = rp.hasSubclass() ? rp.getSubclass().getColoredName() : ChatColor.GRAY + "Sin subclase";
        inv.setItem(4, item(Material.PLAYER_HEAD, ChatColor.GOLD + "" + ChatColor.BOLD + player.getName(), Arrays.asList(
                "", ChatColor.GRAY + "Clase: " + className,
                ChatColor.GRAY + "Subclase: " + subName,
                ChatColor.GRAY + "Nivel: " + ChatColor.YELLOW + rp.getLevel(),
                ChatColor.GRAY + "XP: " + ChatColor.LIGHT_PURPLE + rp.getXp() + "/" + rp.getXpToNextLevel(),
                xpBar(rp.getXpProgress()),
                "", ChatColor.GRAY + "Maná: " + ChatColor.BLUE + (int) rp.getMana() + "/" + (int) stats.getMaxMana(),
                ChatColor.GRAY + "Skill Points: " + ChatColor.GREEN + rp.getSkillPoints(),
                ChatColor.GRAY + "Ability Points: " + ChatColor.AQUA + rp.getAbilityPoints()
        )));

        // Stats with + buttons
        // STR - slot 19
        inv.setItem(19, statItem(Material.DIAMOND_SWORD, ChatColor.RED, "STR", "Fuerza",
                stats.getStrength(), "+" + String.format("%.0f%%", (stats.getMeleeDamageMultiplier() - 1) * 100) + " daño melee"));
        inv.setItem(28, plusButton("STR", rp.getSkillPoints()));

        // DEX - slot 20
        inv.setItem(20, statItem(Material.BOW, ChatColor.GREEN, "DEX", "Destreza",
                stats.getDexterity(), "+" + String.format("%.0f%%", (stats.getRangedDamageMultiplier() - 1) * 100) + " daño ranged"));
        inv.setItem(29, plusButton("DEX", rp.getSkillPoints()));

        // INT - slot 21
        inv.setItem(21, statItem(Material.ENCHANTED_BOOK, ChatColor.AQUA, "INT", "Inteligencia",
                stats.getIntelligence(), "+" + String.format("%.0f%%", (stats.getMagicDamageMultiplier() - 1) * 100) + " daño mágico"));
        inv.setItem(30, plusButton("INT", rp.getSkillPoints()));

        // VIT - slot 23
        inv.setItem(23, statItem(Material.GOLDEN_APPLE, ChatColor.GOLD, "VIT", "Vitalidad",
                stats.getVitality(), "+" + String.format("%.0f", stats.getMaxHealthBonus()) + " HP máx"));
        inv.setItem(32, plusButton("VIT", rp.getSkillPoints()));

        // WIS - slot 24
        inv.setItem(24, statItem(Material.LAPIS_LAZULI, ChatColor.BLUE, "WIS", "Sabiduría",
                stats.getWisdom(), "+" + String.format("%.0f", stats.getMaxMana() - 100) + " maná, -" +
                        String.format("%.0f%%", stats.getCooldownReduction() * 100) + " CD"));
        inv.setItem(33, plusButton("WIS", rp.getSkillPoints()));

        // LUK - slot 25
        inv.setItem(25, statItem(Material.EMERALD, ChatColor.GREEN, "LUK", "Suerte",
                stats.getLuck(), String.format("%.0f%%", stats.getCritChance() * 100) + " crit, +" +
                        String.format("%.0f%%", (stats.getLootBonus() - 1) * 100) + " loot"));
        inv.setItem(34, plusButton("LUK", rp.getSkillPoints()));

        // Combat stats summary - slot 40
        inv.setItem(40, item(Material.BOOK, ChatColor.YELLOW + "Estadísticas de Combate", Arrays.asList(
                "", ChatColor.GRAY + "Mobs eliminados: " + ChatColor.WHITE + rp.getTotalMobKills(),
                ChatColor.GRAY + "Bosses eliminados: " + ChatColor.WHITE + rp.getTotalBossKills(),
                ChatColor.GRAY + "Muertes: " + ChatColor.WHITE + rp.getTotalDeaths(),
                ChatColor.GRAY + "Dungeon max: " + ChatColor.WHITE + rp.getDungeonLevel()
        )));

        player.openInventory(inv);
    }

    private static ItemStack statItem(Material mat, ChatColor color, String id, String name, int value, String bonus) {
        return item(mat, color + "" + ChatColor.BOLD + name + " (" + id + "): " + ChatColor.WHITE + value, Arrays.asList(
                "", ChatColor.GRAY + bonus, "", ChatColor.YELLOW + "Valor actual: " + value));
    }

    private static ItemStack plusButton(String statId, int skillPoints) {
        if (skillPoints > 0) {
            return item(Material.LIME_DYE, ChatColor.GREEN + "▲ Subir " + statId + " (+1)", Arrays.asList(
                    "", ChatColor.GRAY + "Gasta 1 Skill Point", ChatColor.GREEN + "Click para subir"));
        } else {
            return item(Material.GRAY_DYE, ChatColor.RED + "✖ Sin Skill Points", Arrays.asList(
                    "", ChatColor.GRAY + "Sube de nivel para obtener más"));
        }
    }

    private static String xpBar(double progress) {
        int filled = (int)(progress * 20);
        StringBuilder bar = new StringBuilder(ChatColor.GRAY + "[");
        for (int i = 0; i < 20; i++) {
            bar.append(i < filled ? ChatColor.GREEN + "█" : ChatColor.DARK_GRAY + "░");
        }
        bar.append(ChatColor.GRAY + "] " + ChatColor.YELLOW + String.format("%.1f%%", progress * 100));
        return bar.toString();
    }

    private static ItemStack item(Material mat, String name) {
        return item(mat, name, null);
    }

    private static ItemStack item(Material mat, String name, List<String> lore) {
        ItemStack is = new ItemStack(mat);
        ItemMeta meta = is.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null) meta.setLore(lore);
        is.setItemMeta(meta);
        return is;
    }
}
