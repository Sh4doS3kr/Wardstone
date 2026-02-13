package com.moonlight.coreprotect.rpg;

import org.bukkit.ChatColor;
import org.bukkit.Material;

public enum RPGClass {
    WARRIOR("Guerrero", ChatColor.RED, Material.DIAMOND_SWORD,
            "Maestro del combate cuerpo a cuerpo",
            new String[]{"TANK", "DPS", "BERSERKER"}),
    MAGE("Mago", ChatColor.AQUA, Material.BLAZE_ROD,
            "Domina los elementos y la magia arcana",
            new String[]{"ELEMENTAL", "HEALER", "ARCANE"}),
    ARCHER("Arquero", ChatColor.GREEN, Material.BOW,
            "Experto en combate a distancia",
            new String[]{"RANGER", "HUNTER", "ASSASSIN"}),
    ROGUE("Pícaro", ChatColor.YELLOW, Material.IRON_SWORD,
            "Maestro del sigilo y el engaño",
            new String[]{"THIEF", "SABOTEUR", "POISON"}),
    PALADIN("Paladín", ChatColor.GOLD, Material.GOLDEN_SWORD,
            "Guerrero sagrado con poderes divinos",
            new String[]{"HOLY", "RETRIBUTION", "PROTECTION"});

    private final String displayName;
    private final ChatColor color;
    private final Material icon;
    private final String description;
    private final String[] subclassIds;

    RPGClass(String displayName, ChatColor color, Material icon, String description, String[] subclassIds) {
        this.displayName = displayName;
        this.color = color;
        this.icon = icon;
        this.description = description;
        this.subclassIds = subclassIds;
    }

    public String getDisplayName() { return displayName; }
    public ChatColor getColor() { return color; }
    public Material getIcon() { return icon; }
    public String getDescription() { return description; }
    public String[] getSubclassIds() { return subclassIds; }
    public String getColoredName() { return color + displayName; }
}
