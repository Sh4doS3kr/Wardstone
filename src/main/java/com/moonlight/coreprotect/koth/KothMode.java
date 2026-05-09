package com.moonlight.coreprotect.koth;

/**
 * Tipos de modos de KOTH disponibles.
 */
public enum KothMode {
    CLASICO("Clásico", "§6§lKOTH CLÁSICO", "Captura la zona contra otros jugadores"),
    COOPERATIVO("Cooperativo", "§a§lKOTH COOPERATIVO", "Trabaja juntos para derrotar a los NPCs"),
    NOCTURNO("Nocturno", "§8§lKOTH NOCTURNO", "Combate con visión limitada"),
    VALE_TODO("Vale Todo", "§c§lKOTH VALE TODO", "¡Todo vale! Elytras, mazo y más");
    
    private final String displayName;
    private final String title;
    private final String description;
    
    KothMode(String displayName, String title, String description) {
        this.displayName = displayName;
        this.title = title;
        this.description = description;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getTitle() {
        return title;
    }
    
    public String getDescription() {
        return description;
    }
}
