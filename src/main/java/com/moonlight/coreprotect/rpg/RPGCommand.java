package com.moonlight.coreprotect.rpg;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.rpg.abilities.Ability;
import com.moonlight.coreprotect.rpg.gui.ClassSelectionGUI;
import com.moonlight.coreprotect.rpg.gui.SkillTreeGUI;
import com.moonlight.coreprotect.rpg.gui.StatsGUI;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RPGCommand implements CommandExecutor, TabCompleter {

    private final CoreProtectPlugin plugin;

    public RPGCommand(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Solo jugadores.");
            return true;
        }
        Player player = (Player) sender;
        String cmd = command.getName().toLowerCase();

        switch (cmd) {
            case "rpg":
                handleRpg(player, args);
                break;
            case "class":
                ClassSelectionGUI.openClassSelection(player);
                break;
            case "stats":
                handleStats(player);
                break;
            case "skills":
                handleSkills(player);
                break;
            case "skill":
                handleSkillUse(player, args);
                break;
            case "linkmc":
                handleLink(player);
                break;
        }
        return true;
    }

    private void handleRpg(Player player, String[] args) {
        RPGPlayer rp = plugin.getRPGManager().getPlayer(player.getUniqueId());
        String className = rp.hasClass() ? rp.getRpgClass().getColoredName() : ChatColor.GRAY + "Sin clase";
        String subName = rp.hasSubclass() ? rp.getSubclass().getColoredName() : ChatColor.GRAY + "Sin subclase";

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══════ " + ChatColor.WHITE + ChatColor.BOLD + "⚔ RPG PROFILE ⚔" + ChatColor.GOLD + " ═══════");
        player.sendMessage(ChatColor.GRAY + "  Clase: " + className);
        player.sendMessage(ChatColor.GRAY + "  Subclase: " + subName);
        player.sendMessage(ChatColor.GRAY + "  Nivel: " + ChatColor.YELLOW + rp.getLevel() +
                ChatColor.GRAY + " | XP: " + ChatColor.LIGHT_PURPLE + rp.getXp() + "/" + rp.getXpToNextLevel());
        player.sendMessage(ChatColor.GRAY + "  Maná: " + ChatColor.BLUE + (int) rp.getMana() + "/" + (int) rp.getStats().getMaxMana());
        player.sendMessage(ChatColor.GRAY + "  Skill Points: " + ChatColor.GREEN + rp.getSkillPoints() +
                ChatColor.GRAY + " | Ability Points: " + ChatColor.AQUA + rp.getAbilityPoints());
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "  STR: " + ChatColor.RED + rp.getStats().getStrength() +
                ChatColor.GRAY + " | DEX: " + ChatColor.GREEN + rp.getStats().getDexterity() +
                ChatColor.GRAY + " | INT: " + ChatColor.AQUA + rp.getStats().getIntelligence());
        player.sendMessage(ChatColor.GRAY + "  VIT: " + ChatColor.GOLD + rp.getStats().getVitality() +
                ChatColor.GRAY + " | WIS: " + ChatColor.BLUE + rp.getStats().getWisdom() +
                ChatColor.GRAY + " | LUK: " + ChatColor.GREEN + rp.getStats().getLuck());
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "  /class " + ChatColor.GRAY + "- Elegir clase");
        player.sendMessage(ChatColor.YELLOW + "  /stats " + ChatColor.GRAY + "- Asignar puntos");
        player.sendMessage(ChatColor.YELLOW + "  /skills " + ChatColor.GRAY + "- Árbol de habilidades");
        player.sendMessage(ChatColor.YELLOW + "  /skill <nombre> " + ChatColor.GRAY + "- Usar habilidad");
        player.sendMessage(ChatColor.YELLOW + "  /linkmc " + ChatColor.GRAY + "- Vincular cuenta web");
        player.sendMessage(ChatColor.GOLD + "══════════════════════════════");
    }

    private void handleStats(Player player) {
        RPGPlayer rp = plugin.getRPGManager().getPlayer(player.getUniqueId());
        StatsGUI.open(player, rp);
    }

    private void handleSkills(Player player) {
        RPGPlayer rp = plugin.getRPGManager().getPlayer(player.getUniqueId());
        if (!rp.hasSubclass()) {
            player.sendMessage(ChatColor.RED + "Primero debes elegir una clase con /class");
            return;
        }
        SkillTreeGUI.open(player, rp, plugin.getAbilityRegistry());
    }

    private void handleSkillUse(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Uso: /skill <nombre_habilidad>");
            return;
        }

        RPGPlayer rp = plugin.getRPGManager().getPlayer(player.getUniqueId());
        if (!rp.hasSubclass()) {
            player.sendMessage(ChatColor.RED + "Necesitas una clase primero. Usa /class");
            return;
        }

        String abilityId = args[0].toLowerCase();
        Ability ability = plugin.getAbilityRegistry().getAbility(abilityId);

        if (ability == null) {
            player.sendMessage(ChatColor.RED + "Habilidad no encontrada: " + abilityId);
            return;
        }

        ability.use(player, rp);
    }

    private void handleLink(Player player) {
        String code = plugin.getRPGManager().generateLinkCode(player.getUniqueId());
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══════ " + ChatColor.WHITE + ChatColor.BOLD + "🔗 VINCULAR CUENTA" + ChatColor.GOLD + " ═══════");
        player.sendMessage(ChatColor.GRAY + "  Tu código de vinculación:");
        player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "  " + code);
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "  Introduce este código en la web para");
        player.sendMessage(ChatColor.GRAY + "  vincular tu cuenta de Minecraft.");
        player.sendMessage(ChatColor.YELLOW + "  El código expira en 5 minutos.");
        player.sendMessage(ChatColor.GOLD + "══════════════════════════════");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return new ArrayList<>();
        String cmd = command.getName().toLowerCase();

        if (cmd.equals("skill") && args.length == 1) {
            Player player = (Player) sender;
            RPGPlayer rp = plugin.getRPGManager().getPlayer(player.getUniqueId());
            List<String> suggestions = new ArrayList<>();
            for (String ability : rp.getUnlockedAbilities()) {
                if (ability.startsWith(args[0].toLowerCase())) {
                    suggestions.add(ability);
                }
            }
            return suggestions;
        }

        return new ArrayList<>();
    }
}
