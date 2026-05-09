package com.moonlight.coreprotect.finishers;

import org.bukkit.ChatColor;
import org.bukkit.Material;

public enum FinisherType {

    THUNDER_JUDGMENT("thunder",
            ChatColor.YELLOW + "" + ChatColor.BOLD + "⚡ El Juicio del Trueno",
            Material.LIGHTNING_ROD,
            new String[]{
                    ChatColor.GRAY + "Una jaula eléctrica atrapa",
                    ChatColor.GRAY + "a la víctima en el suelo.",
                    ChatColor.GRAY + "Rayos caen en círculo hasta",
                    ChatColor.GRAY + "una explosión devastadora.",
                    "",
                    ChatColor.DARK_PURPLE + "Tipo: " + ChatColor.YELLOW + "Suelo"
            },
            50000),

    VOID_INVOCATION("void",
            ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "🌀 Invocación del Vacío",
            Material.ENDER_EYE,
            new String[]{
                    ChatColor.GRAY + "Un vórtice oscuro atrapa a",
                    ChatColor.GRAY + "la víctima haciéndola girar",
                    ChatColor.GRAY + "sin control mientras bloques",
                    ChatColor.GRAY + "son absorbidos al centro.",
                    "",
                    ChatColor.DARK_PURPLE + "Tipo: " + ChatColor.LIGHT_PURPLE + "Giro"
            },
            75000),

    BLOOD_ERUPTION("blood",
            ChatColor.DARK_RED + "" + ChatColor.BOLD + "🩸 Erupción de Sangre",
            Material.REDSTONE,
            new String[]{
                    ChatColor.GRAY + "Géiseres de sangre brotan",
                    ChatColor.GRAY + "del suelo en un anillo",
                    ChatColor.GRAY + "alrededor de la víctima",
                    ChatColor.GRAY + "con oleadas carmesí.",
                    "",
                    ChatColor.DARK_PURPLE + "Tipo: " + ChatColor.RED + "Suelo"
            },
            60000),

    SHATTERED_AMETHYST("amethyst",
            ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "💎 Shattered Amethyst",
            Material.AMETHYST_SHARD,
            new String[]{
                    ChatColor.GRAY + "Cristales crecen formando",
                    ChatColor.GRAY + "una prisión hexagonal que",
                    ChatColor.GRAY + "atrapa y luego estalla en",
                    ChatColor.GRAY + "miles de fragmentos.",
                    "",
                    ChatColor.DARK_PURPLE + "Tipo: " + ChatColor.LIGHT_PURPLE + "Suelo"
            },
            65000),

    ORBITAL_STRIKE("orbital",
            ChatColor.WHITE + "" + ChatColor.BOLD + "☄ Ataque Orbital",
            Material.END_ROD,
            new String[]{
                    ChatColor.GRAY + "Un haz de luz blanca cae",
                    ChatColor.GRAY + "del cielo sobre la víctima.",
                    ChatColor.GRAY + "Al impactar, fuegos artificiales",
                    ChatColor.GRAY + "y una explosión de luz.",
                    "",
                    ChatColor.DARK_PURPLE + "Estilo: " + ChatColor.WHITE + "Celestial"
            },
            80000),

    HELLFIRE("hellfire",
            ChatColor.RED + "" + ChatColor.BOLD + "🔥 Infierno Demoníaco",
            Material.MAGMA_BLOCK,
            new String[]{
                    ChatColor.GRAY + "Un lago de lava se expande",
                    ChatColor.GRAY + "y 8 pilares de fuego erucionan",
                    ChatColor.GRAY + "alrededor de la víctima con",
                    ChatColor.GRAY + "una erupción volcánica final.",
                    "",
                    ChatColor.DARK_PURPLE + "Tipo: " + ChatColor.RED + "Suelo"
            },
            70000),

    ICE_STORM("ice",
            ChatColor.AQUA + "" + ChatColor.BOLD + "❄ Tormenta de Hielo",
            Material.BLUE_ICE,
            new String[]{
                    ChatColor.GRAY + "Spikes de hielo crecen desde",
                    ChatColor.GRAY + "la víctima en 12 direcciones",
                    ChatColor.GRAY + "como una explosión glacial",
                    ChatColor.GRAY + "que congela todo a su paso.",
                    "",
                    ChatColor.DARK_PURPLE + "Tipo: " + ChatColor.AQUA + "Suelo"
            },
            72000),

    DRAGON_WRATH("dragon",
            ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "🐉 Ira del Dragón",
            Material.DRAGON_HEAD,
            new String[]{
                    ChatColor.GRAY + "La víctima asciende envuelta",
                    ChatColor.GRAY + "en espirales de aliento de",
                    ChatColor.GRAY + "dragón púrpura hasta una",
                    ChatColor.GRAY + "explosión celestial.",
                    "",
                    ChatColor.DARK_PURPLE + "Tipo: " + ChatColor.LIGHT_PURPLE + "Cielo"
            },
            90000),

    SOUL_VORTEX("soul",
            ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "👻 Vórtice de Almas",
            Material.SOUL_LANTERN,
            new String[]{
                    ChatColor.GRAY + "La víctima gira sin control",
                    ChatColor.GRAY + "a una velocidad demencial",
                    ChatColor.GRAY + "mientras las almas drenan",
                    ChatColor.GRAY + "su esencia vital.",
                    "",
                    ChatColor.DARK_PURPLE + "Tipo: " + ChatColor.DARK_AQUA + "Giro"
            },
            85000),

    WITHER_STORM("wither",
            ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "💀 Tormenta Wither",
            Material.WITHER_SKELETON_SKULL,
            new String[]{
                    ChatColor.GRAY + "La oscuridad consume todo.",
                    ChatColor.GRAY + "Cráneos wither orbitan la",
                    ChatColor.GRAY + "víctima en el suelo hasta",
                    ChatColor.GRAY + "una explosión de tinieblas.",
                    "",
                    ChatColor.DARK_PURPLE + "Tipo: " + ChatColor.DARK_GRAY + "Suelo"
            },
            95000),

    SCULK_RESONANCE("sculk",
            ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "🔊 Resonancia Sculk",
            Material.SCULK_CATALYST,
            new String[]{
                    ChatColor.GRAY + "Zarcillos de sculk se extienden",
                    ChatColor.GRAY + "por el suelo. Ondas sónicas",
                    ChatColor.GRAY + "pulsan hacia afuera hasta un",
                    ChatColor.GRAY + "boom sónico devastador.",
                    "",
                    ChatColor.DARK_PURPLE + "Tipo: " + ChatColor.DARK_AQUA + "Suelo"
            },
            100000),

    APOCALYPSE("apocalypse",
            ChatColor.GOLD + "" + ChatColor.BOLD + "💥 Apocalipsis Divino",
            Material.NETHER_STAR,
            new String[]{
                    ChatColor.GRAY + "La realidad colapsa. Pilares",
                    ChatColor.GRAY + "elementales, un tornado de",
                    ChatColor.GRAY + "bloques premium y una supernova",
                    ChatColor.GRAY + "que destruye todo a su paso.",
                    "",
                    ChatColor.GOLD + "★ " + ChatColor.RED + "EL FINISHER DEFINITIVO",
                    ChatColor.DARK_PURPLE + "Tipo: " + ChatColor.GOLD + "Celestial + Suelo"
            },
            250000);

    private final String id;
    private final String displayName;
    private final Material icon;
    private final String[] description;
    private final double defaultPrice;

    FinisherType(String id, String displayName, Material icon, String[] description, double defaultPrice) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.description = description;
        this.defaultPrice = defaultPrice;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public Material getIcon() { return icon; }
    public String[] getDescription() { return description; }
    public double getDefaultPrice() { return defaultPrice; }

    public static FinisherType fromId(String id) {
        for (FinisherType type : values()) {
            if (type.id.equalsIgnoreCase(id)) return type;
        }
        return null;
    }
}
