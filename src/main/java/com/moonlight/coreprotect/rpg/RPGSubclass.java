package com.moonlight.coreprotect.rpg;

import org.bukkit.ChatColor;
import org.bukkit.Material;

public enum RPGSubclass {
    // Warrior subclasses
    TANK(RPGClass.WARRIOR, "Tank", ChatColor.DARK_RED, Material.SHIELD,
            "Defensor imparable que protege a sus aliados",
            new String[]{"taunt", "shield_wall", "last_stand"}),
    DPS(RPGClass.WARRIOR, "DPS", ChatColor.RED, Material.NETHERITE_SWORD,
            "Guerrero ofensivo de daño devastador",
            new String[]{"whirlwind", "execute", "battle_cry"}),
    BERSERKER(RPGClass.WARRIOR, "Berserker", ChatColor.DARK_RED, Material.NETHERITE_AXE,
            "Furia descontrolada, cuanto menos vida más daño",
            new String[]{"rage_mode", "bloodthirst", "unstoppable"}),

    // Mage subclasses
    ELEMENTAL(RPGClass.MAGE, "Elemental", ChatColor.RED, Material.FIRE_CHARGE,
            "Domina fuego, rayo y hielo para destruir",
            new String[]{"fireball", "lightning_chain", "ice_prison"}),
    HEALER(RPGClass.MAGE, "Healer", ChatColor.WHITE, Material.GOLDEN_APPLE,
            "Sana aliados y protege con magia sagrada",
            new String[]{"holy_light", "group_heal", "divine_shield"}),
    ARCANE(RPGClass.MAGE, "Arcane", ChatColor.DARK_PURPLE, Material.ENDER_EYE,
            "Manipula el espacio-tiempo y la energía pura",
            new String[]{"teleport", "mana_shield", "time_warp"}),

    // Archer subclasses
    RANGER(RPGClass.ARCHER, "Ranger", ChatColor.GREEN, Material.SPECTRAL_ARROW,
            "Maestro del arco con flechas especiales",
            new String[]{"multishot", "volley", "explosive_arrow"}),
    HUNTER(RPGClass.ARCHER, "Hunter", ChatColor.DARK_GREEN, Material.WOLF_SPAWN_EGG,
            "Cazador con bestias compañeras",
            new String[]{"pet_wolf", "track", "beast_master"}),
    ASSASSIN(RPGClass.ARCHER, "Assassin", ChatColor.GRAY, Material.IRON_SWORD,
            "Ataca desde las sombras con daño letal",
            new String[]{"stealth", "backstab", "smoke_bomb"}),

    // Rogue subclasses
    THIEF(RPGClass.ROGUE, "Thief", ChatColor.YELLOW, Material.GOLD_NUGGET,
            "Roba items y se escabulle como nadie",
            new String[]{"pickpocket", "lockpick", "disguise"}),
    SABOTEUR(RPGClass.ROGUE, "Saboteur", ChatColor.DARK_GRAY, Material.TNT,
            "Experto en trampas y demolición",
            new String[]{"trap_mastery", "demolition", "sabotage"}),
    POISON(RPGClass.ROGUE, "Envenenador", ChatColor.DARK_GREEN, Material.SPIDER_EYE,
            "Maestro de los venenos y toxinas letales",
            new String[]{"venom_strike", "poison_cloud", "antidote"}),

    // Paladin subclasses
    HOLY(RPGClass.PALADIN, "Holy", ChatColor.GOLD, Material.SUNFLOWER,
            "Guerrero sagrado con poderes curativos",
            new String[]{"divine_strike", "blessing", "resurrection"}),
    RETRIBUTION(RPGClass.PALADIN, "Retribution", ChatColor.GOLD, Material.GOLDEN_AXE,
            "Castiga a los enemigos con furia divina",
            new String[]{"hammer_of_justice", "crusader_strike", "holy_wrath"}),
    PROTECTION(RPGClass.PALADIN, "Protection", ChatColor.WHITE, Material.IRON_CHESTPLATE,
            "Protector divino, escudo de los inocentes",
            new String[]{"divine_protection", "guardian_angel", "holy_barrier"});

    private final RPGClass parentClass;
    private final String displayName;
    private final ChatColor color;
    private final Material icon;
    private final String description;
    private final String[] abilityIds;

    RPGSubclass(RPGClass parentClass, String displayName, ChatColor color, Material icon,
                String description, String[] abilityIds) {
        this.parentClass = parentClass;
        this.displayName = displayName;
        this.color = color;
        this.icon = icon;
        this.description = description;
        this.abilityIds = abilityIds;
    }

    public RPGClass getParentClass() { return parentClass; }
    public String getDisplayName() { return displayName; }
    public ChatColor getColor() { return color; }
    public Material getIcon() { return icon; }
    public String getDescription() { return description; }
    public String[] getAbilityIds() { return abilityIds; }
    public String getColoredName() { return color + displayName; }

    public static RPGSubclass fromId(String id) {
        try { return valueOf(id.toUpperCase()); }
        catch (Exception e) { return null; }
    }

    public static RPGSubclass[] getSubclassesFor(RPGClass rpgClass) {
        return java.util.Arrays.stream(values())
                .filter(s -> s.parentClass == rpgClass)
                .toArray(RPGSubclass[]::new);
    }
}
