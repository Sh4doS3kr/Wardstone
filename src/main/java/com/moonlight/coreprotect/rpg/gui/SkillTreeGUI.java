package com.moonlight.coreprotect.rpg.gui;

import com.moonlight.coreprotect.rpg.RPGPlayer;
import com.moonlight.coreprotect.rpg.RPGSubclass;
import com.moonlight.coreprotect.rpg.abilities.Ability;
import com.moonlight.coreprotect.rpg.abilities.AbilityRegistry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SkillTreeGUI {

    public static final String TITLE = ChatColor.LIGHT_PURPLE + "⚡ Árbol de Habilidades";

    public static void open(Player player, RPGPlayer rp, AbilityRegistry registry) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);

        ItemStack border = item(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, border);

        if (!rp.hasSubclass()) {
            inv.setItem(22, item(Material.BARRIER, ChatColor.RED + "No tienes subclase",
                    Arrays.asList("", ChatColor.GRAY + "Usa /class para elegir tu clase")));
            player.openInventory(inv);
            return;
        }

        RPGSubclass sub = rp.getSubclass();

        // Header
        inv.setItem(4, item(sub.getIcon(), sub.getColor() + "" + ChatColor.BOLD + sub.getDisplayName(), Arrays.asList(
                "", ChatColor.GRAY + sub.getDescription(),
                "", ChatColor.YELLOW + "Ability Points: " + ChatColor.AQUA + rp.getAbilityPoints(),
                ChatColor.GRAY + "Nivel: " + ChatColor.WHITE + rp.getLevel()
        )));

        // Show abilities from this subclass
        List<Ability> abilities = registry.getAbilitiesForSubclass(sub);
        int[] slots = {20, 22, 24}; // 3 abilities per subclass
        int[] arrowSlots = {29, 31, 33}; // bind buttons below

        for (int i = 0; i < abilities.size() && i < 3; i++) {
            Ability ability = abilities.get(i);
            boolean unlocked = rp.hasAbility(ability.getId());
            boolean canUnlock = !unlocked && rp.getAbilityPoints() >= ability.getAbilityCost()
                    && rp.getLevel() >= ability.getRequiredLevel();

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GRAY + ability.getDescription());
            lore.add("");
            lore.add(ChatColor.BLUE + "Maná: " + (int) ability.getManaCost());
            lore.add(ChatColor.YELLOW + "Cooldown: " + (ability.getCooldownMs() / 1000) + "s");
            lore.add(ChatColor.RED + "Nivel requerido: " + ability.getRequiredLevel());
            lore.add(ChatColor.AQUA + "Coste: " + ability.getAbilityCost() + " Ability Points");
            lore.add("");

            if (unlocked) {
                lore.add(ChatColor.GREEN + "✔ DESBLOQUEADA");
                lore.add(ChatColor.GRAY + "Usa /skill " + ability.getId() + " para activar");
                inv.setItem(slots[i], itemGlow(ability.getIcon(),
                        ability.getColor() + "" + ChatColor.BOLD + ability.getDisplayName(), lore));
                inv.setItem(arrowSlots[i], item(Material.LIME_STAINED_GLASS_PANE,
                        ChatColor.GREEN + "✔ Desbloqueada"));
            } else if (canUnlock) {
                lore.add(ChatColor.GREEN + "▶ Click para desbloquear");
                inv.setItem(slots[i], item(ability.getIcon(),
                        ChatColor.WHITE + "" + ChatColor.BOLD + ability.getDisplayName(), lore));
                inv.setItem(arrowSlots[i], item(Material.YELLOW_STAINED_GLASS_PANE,
                        ChatColor.YELLOW + "▶ Desbloquear (" + ability.getAbilityCost() + " AP)"));
            } else {
                lore.add(ChatColor.RED + "✖ Bloqueada");
                if (rp.getLevel() < ability.getRequiredLevel()) {
                    lore.add(ChatColor.GRAY + "Necesitas nivel " + ability.getRequiredLevel());
                }
                if (rp.getAbilityPoints() < ability.getAbilityCost()) {
                    lore.add(ChatColor.GRAY + "Necesitas " + ability.getAbilityCost() + " AP");
                }
                inv.setItem(slots[i], item(Material.GRAY_DYE,
                        ChatColor.DARK_GRAY + "" + ChatColor.BOLD + ability.getDisplayName(), lore));
                inv.setItem(arrowSlots[i], item(Material.RED_STAINED_GLASS_PANE,
                        ChatColor.RED + "✖ Bloqueada"));
            }
        }

        // Mastery info at bottom
        if (rp.hasClassMastery()) {
            inv.setItem(49, item(Material.NETHER_STAR, ChatColor.GOLD + "" + ChatColor.BOLD + "✦ CLASS MASTERY ✦",
                    Arrays.asList("", ChatColor.GRAY + "¡Has alcanzado la maestría de clase!",
                            ChatColor.GOLD + "Todas tus habilidades son más potentes")));
        } else {
            inv.setItem(49, item(Material.COAL, ChatColor.GRAY + "Maestría de Clase",
                    Arrays.asList("", ChatColor.GRAY + "Alcanza nivel 50 para desbloquear",
                            ChatColor.GRAY + "la maestría de " + sub.getDisplayName())));
        }

        player.openInventory(inv);
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

    private static ItemStack itemGlow(Material mat, String name, List<String> lore) {
        ItemStack is = item(mat, name, lore);
        ItemMeta meta = is.getItemMeta();
        meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        is.setItemMeta(meta);
        return is;
    }
}
