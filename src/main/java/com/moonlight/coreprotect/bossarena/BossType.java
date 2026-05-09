package com.moonlight.coreprotect.bossarena;

/**
 * Tipos de boss disponibles en el sistema Boss Arena.
 */
public enum BossType {
    VORGATH("Vorgath", "§4§lVorgath", "Señor del Bosque Carmesí", "§4"),
    GLACIUS("Glacius", "§b§lGlacius", "Emperador del Invierno Eterno", "§b"),
    ABYSSAL_WARDEN("Bloodfury", "§4§lWarlord Kragan", "Señor de la Furia Sangrienta", "§4"),
    CUBOSS_SLIME("Cuboss", "§a§lCuboss-Slime", "Rey de los Slimes", "§a"),
    APOCALYPSE_TYPE_A("Apocalypse", "§5§lApocalypse", "Heraldo del Vacío", "§5");

    private final String id;
    private final String displayName;
    private final String subtitle;
    private final String color;

    BossType(String id, String displayName, String subtitle, String color) {
        this.id = id;
        this.displayName = displayName;
        this.subtitle = subtitle;
        this.color = color;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getSubtitle() { return subtitle; }
    public String getColor() { return color; }

    public static BossType fromString(String name) {
        for (BossType type : values()) {
            if (type.id.equalsIgnoreCase(name) || type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        return CUBOSS_SLIME; // Default
    }
}
