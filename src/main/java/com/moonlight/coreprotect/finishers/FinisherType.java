package com.moonlight.coreprotect.finishers;

import org.bukkit.ChatColor;
import org.bukkit.Material;

public enum FinisherType {

    THUNDER_JUDGMENT("thunder",
            ChatColor.YELLOW + "" + ChatColor.BOLD + "⚡ El Juicio del Trueno",
            Material.LIGHTNING_ROD,
            new String[]{
                    ChatColor.GRAY + "Al eliminar a un jugador,",
                    ChatColor.GRAY + "caen 3 rayos seguidos sobre",
                    ChatColor.GRAY + "su cadáver con una explosión",
                    ChatColor.GRAY + "de chispas eléctricas.",
                    "",
                    ChatColor.DARK_PURPLE + "Estilo: " + ChatColor.YELLOW + "Zeus"
            },
            50000),

    VOID_INVOCATION("void",
            ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "🌀 Invocación del Vacío",
            Material.ENDER_EYE,
            new String[]{
                    ChatColor.GRAY + "Un agujero negro absorbe",
                    ChatColor.GRAY + "al jugador eliminado, que",
                    ChatColor.GRAY + "levita y explota en una",
                    ChatColor.GRAY + "nube de oscuridad.",
                    "",
                    ChatColor.DARK_PURPLE + "Estilo: " + ChatColor.LIGHT_PURPLE + "Void"
            },
            75000),

    BLOOD_ERUPTION("blood",
            ChatColor.DARK_RED + "" + ChatColor.BOLD + "🩸 Erupción de Sangre",
            Material.REDSTONE,
            new String[]{
                    ChatColor.GRAY + "Una fuente de sangre brota",
                    ChatColor.GRAY + "del suelo con gotas cayendo",
                    ChatColor.GRAY + "que simulan un baño carmesí",
                    ChatColor.GRAY + "sobre el campo de batalla.",
                    "",
                    ChatColor.DARK_PURPLE + "Estilo: " + ChatColor.RED + "Gore"
            },
            60000),

    SHATTERED_AMETHYST("amethyst",
            ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "💎 Shattered Amethyst",
            Material.AMETHYST_SHARD,
            new String[]{
                    ChatColor.GRAY + "El jugador explota en mil",
                    ChatColor.GRAY + "pedazos de cristal con",
                    ChatColor.GRAY + "sonidos de amatista",
                    ChatColor.GRAY + "rompiéndose en el aire.",
                    "",
                    ChatColor.DARK_PURPLE + "Estilo: " + ChatColor.LIGHT_PURPLE + "Crystal"
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
            80000);

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
