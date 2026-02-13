package com.moonlight.coreprotect.rpg.gui;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.rpg.*;
import com.moonlight.coreprotect.rpg.abilities.Ability;
import com.moonlight.coreprotect.rpg.abilities.AbilityRegistry;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;

public class RPGGUIListener implements Listener {

    private final CoreProtectPlugin plugin;

    public RPGGUIListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();

        if (title.equals(ClassSelectionGUI.CLASS_TITLE)) {
            handleClassSelection(event, player);
        } else if (title.startsWith(ClassSelectionGUI.SUBCLASS_TITLE_PREFIX)) {
            handleSubclassSelection(event, player, title);
        } else if (title.equals(StatsGUI.TITLE)) {
            handleStats(event, player);
        } else if (title.equals(SkillTreeGUI.TITLE)) {
            handleSkillTree(event, player);
        }
    }

    private void handleClassSelection(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 11 || slot > 15) return;

        RPGClass[] classes = RPGClass.values();
        int index = slot - 11;
        if (index >= classes.length) return;

        RPGClass selected = classes[index];
        RPGPlayer rp = plugin.getRPGManager().getPlayer(player.getUniqueId());

        // If already has a class, confirm change
        if (rp.hasClass() && rp.getRpgClass() != selected) {
            player.sendMessage(ChatColor.YELLOW + "⚠ Cambiar de clase reiniciará tu subclase.");
        }

        rp.setRpgClass(selected);
        rp.setSubclass(null);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        player.sendMessage(ChatColor.GREEN + "⚔ ¡Has elegido la clase " + selected.getColoredName() + ChatColor.GREEN + "!");

        // Open subclass selection
        ClassSelectionGUI.openSubclassSelection(player, selected);
    }

    private void handleSubclassSelection(InventoryClickEvent event, Player player, String title) {
        event.setCancelled(true);
        int slot = event.getRawSlot();

        // Back button
        if (slot == 22) {
            ClassSelectionGUI.openClassSelection(player);
            return;
        }

        RPGPlayer rp = plugin.getRPGManager().getPlayer(player.getUniqueId());
        if (!rp.hasClass()) return;

        RPGSubclass[] subs = RPGSubclass.getSubclassesFor(rp.getRpgClass());
        int[] slots = {11, 13, 15};

        for (int i = 0; i < subs.length && i < 3; i++) {
            if (slot == slots[i]) {
                rp.setSubclass(subs[i]);
                player.closeInventory();
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
                player.sendMessage(ChatColor.GREEN + "⚡ ¡Has elegido la subclase " +
                        subs[i].getColoredName() + ChatColor.GREEN + "!");
                player.sendMessage(ChatColor.GRAY + "Usa /skills para ver tu árbol de habilidades.");
                player.sendMessage(ChatColor.GRAY + "Usa /stats para asignar tus puntos de estadística.");
                plugin.getRPGManager().saveAll();
                return;
            }
        }
    }

    private void handleStats(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        RPGPlayer rp = plugin.getRPGManager().getPlayer(player.getUniqueId());

        // Plus buttons at slots 28-34 (skipping 31)
        String statId = null;
        switch (slot) {
            case 28: statId = "STR"; break;
            case 29: statId = "DEX"; break;
            case 30: statId = "INT"; break;
            case 32: statId = "VIT"; break;
            case 33: statId = "WIS"; break;
            case 34: statId = "LUK"; break;
        }

        if (statId != null && rp.getSkillPoints() > 0) {
            rp.getStats().addStat(statId, 1);
            rp.setSkillPoints(rp.getSkillPoints() - 1);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
            plugin.getRPGManager().saveAll();

            // Apply stat changes immediately
            plugin.getRPGCombatListener().applyStatBonuses(player);

            // Refresh GUI
            StatsGUI.open(player, rp);
        } else if (statId != null) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        }
    }

    private void handleSkillTree(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        RPGPlayer rp = plugin.getRPGManager().getPlayer(player.getUniqueId());

        if (!rp.hasSubclass()) return;

        AbilityRegistry registry = plugin.getAbilityRegistry();
        List<Ability> abilities = registry.getAbilitiesForSubclass(rp.getSubclass());
        int[] abilitySlots = {20, 22, 24};

        for (int i = 0; i < abilities.size() && i < 3; i++) {
            if (slot == abilitySlots[i]) {
                Ability ability = abilities.get(i);

                if (rp.hasAbility(ability.getId())) {
                    player.sendMessage(ChatColor.YELLOW + "Ya tienes esta habilidad desbloqueada.");
                    player.sendMessage(ChatColor.GRAY + "Usa /skill " + ability.getId() + " para activarla.");
                    return;
                }

                if (rp.getLevel() < ability.getRequiredLevel()) {
                    player.sendMessage(ChatColor.RED + "Necesitas nivel " + ability.getRequiredLevel() + " para desbloquear esta habilidad.");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }

                if (rp.getAbilityPoints() < ability.getAbilityCost()) {
                    player.sendMessage(ChatColor.RED + "Necesitas " + ability.getAbilityCost() + " Ability Points.");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }

                // Unlock!
                rp.setAbilityPoints(rp.getAbilityPoints() - ability.getAbilityCost());
                rp.unlockAbility(ability.getId());
                plugin.getRPGManager().saveAll();

                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.5f);
                player.sendMessage(ability.getColor() + "⚡ ¡Habilidad " + ability.getDisplayName() + " desbloqueada!");
                player.sendMessage(ChatColor.GRAY + "Usa /skill " + ability.getId() + " para activarla.");

                // Refresh
                SkillTreeGUI.open(player, rp, registry);
                return;
            }
        }
    }
}
