package com.moonlight.coreprotect.rpg.abilities;

import com.moonlight.coreprotect.rpg.RPGPlayer;
import com.moonlight.coreprotect.rpg.RPGSubclass;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public abstract class Ability {

    private final String id;
    private final String displayName;
    private final String description;
    private final Material icon;
    private final RPGSubclass subclass;
    private final double manaCost;
    private final long cooldownMs;
    private final int requiredLevel; // level needed to unlock
    private final int abilityCost;   // ability points to unlock
    private final ChatColor color;

    public Ability(String id, String displayName, String description, Material icon,
                   RPGSubclass subclass, double manaCost, long cooldownMs,
                   int requiredLevel, int abilityCost, ChatColor color) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.icon = icon;
        this.subclass = subclass;
        this.manaCost = manaCost;
        this.cooldownMs = cooldownMs;
        this.requiredLevel = requiredLevel;
        this.abilityCost = abilityCost;
        this.color = color;
    }

    public abstract void execute(Player player, RPGPlayer rpgPlayer);

    public boolean canUse(Player player, RPGPlayer rpgPlayer) {
        if (!rpgPlayer.hasAbility(id)) {
            player.sendMessage(ChatColor.RED + "✖ No tienes esta habilidad desbloqueada.");
            return false;
        }
        if (rpgPlayer.isOnCooldown(id)) {
            long remaining = rpgPlayer.getCooldownRemaining(id) / 1000;
            player.sendMessage(ChatColor.RED + "✖ Habilidad en cooldown: " + remaining + "s");
            return false;
        }
        if (rpgPlayer.getMana() < manaCost) {
            player.sendMessage(ChatColor.BLUE + "✖ Maná insuficiente (" + (int) rpgPlayer.getMana() + "/" + (int) manaCost + ")");
            return false;
        }
        return true;
    }

    public void use(Player player, RPGPlayer rpgPlayer) {
        if (!canUse(player, rpgPlayer)) return;
        rpgPlayer.useMana(manaCost);
        double cdReduction = rpgPlayer.getStats().getCooldownReduction();
        long finalCd = (long)(cooldownMs * (1.0 - cdReduction));
        rpgPlayer.setCooldown(id, finalCd);
        execute(player, rpgPlayer);
        player.sendMessage(color + "⚡ " + displayName + ChatColor.GRAY + " activada!");
    }

    // Getters
    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public Material getIcon() { return icon; }
    public RPGSubclass getSubclass() { return subclass; }
    public double getManaCost() { return manaCost; }
    public long getCooldownMs() { return cooldownMs; }
    public int getRequiredLevel() { return requiredLevel; }
    public int getAbilityCost() { return abilityCost; }
    public ChatColor getColor() { return color; }
}
