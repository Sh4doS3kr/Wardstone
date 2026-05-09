package com.moonlight.coreprotect.minigames;

public enum MiniGameType {
    TNT_RUN("§c§lTNT Run", "§7Corre sin parar. El bloque que pisas desaparece. ¡El último en pie gana!", "§c", 2, 24),
    SPLEEF("§f§lSpleef", "§7Rompe la nieve bajo tus rivales. ¡3 capas de pura destrucción!", "§f", 2, 24),
    TNT_TAG("§6§lTNT Tag", "§7¡Pilla-pilla explosivo! Pasa la bomba antes de que explote.", "§6", 3, 20),
    BLOCK_PARTY("§d§lBlock Party", "§7¡Pisa el color correcto en 3 segundos o caerás al vacío!", "§d", 2, 24),
    OITC("§b§lOITC", "§7Un arco, una flecha, una kill. ¡One in the Chamber!", "§b", 2, 16),
    SUMO("§e§lSumo", "§7Knockback FFA en una plataforma diminuta. ¡Tira a todos!", "§e", 2, 16),
    SNOWBALL_FIGHT("§b§lBolas de Nieve", "§7Pelea de bolas de nieve. ¡Cada golpe duele! Último en pie gana.", "§b", 2, 16),
    MUSICAL_CHAIRS("§d§lSillas Musicales", "§7¡La música para y debes sentarte! Siempre hay 1 silla menos.", "§d", 3, 24),
    PARKOUR_RACE("§a§lCarrera Parkour", "§7¡Recorre el parkour más rápido que todos! Checkpoints incluidos.", "§a", 2, 16),
    FLOOR_IS_LAVA("§6§lEl Suelo es Lava", "§7Las plataformas se destruyen. ¡No caigas a la lava!", "§6", 2, 24),
    YES_NO("§6§lSí o No", "§7¡Responde preguntas moviéndote! Verde=SÍ | Rojo=NO", "§6", 2, 16),
    BEAST_ESCAPE("§4§lEscape de la Bestia", "§7¡Escapa de la bestia o actívala para cazar a los supervivientes!", "§4", 4, 16),
    MURDER_MYSTERY("§8§lMisterio de Asesinato", "§7¡Encuentra al asesino antes de que mate a todos!", "§8", 5, 16),
    RED_LIGHT_GREEN_LIGHT("§c§lLuz Roja, Luz Verde", "§7¡Avanza cuando la luz esté verde! ¡Detente en rojo o morirás!", "§c", 2, 20),
    ANVIL_RAIN("§4§lLluvia de Yunques", "§7¡Esquiva los yunques que caen del cielo! Último en pie gana.", "§4", 2, 24),
    FARM_HUNT("§6§lCaza en la Granja", "§7¡Disfrazate de animal y escóndete! Los cazadores deben encontrar a los impostores.", "§6", 2, 24),
    BUILD_BATTLE("§a§lBuild Battle", "§7¡Replica la figura modelo! El peor constructor es eliminado cada ronda.", "§a", 2, 8),
    BUILD_BATTLE_CLASSIC("§b§lBuild Battle Clásico", "§7¡Construye sobre una temática y vota las creaciones de los demás!", "§b", 2, 12),
    PILLARS_OF_FORTUNE("§5§lPilares de la Fortuna", "§7¡Cada jugador en su pilar! Recibe items aleatorios y elimina a los demás. Último en pie gana.", "§5", 2, 15);

    private final String displayName;
    private final String description;
    private final String color;
    private final int minPlayers;
    private final int maxPlayers;

    MiniGameType(String displayName, String description, String color, int minPlayers, int maxPlayers) {
        this.displayName = displayName;
        this.description = description;
        this.color = color;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public String getColor() { return color; }
    public int getMinPlayers() { return minPlayers; }
    public int getMaxPlayers() { return maxPlayers; }

    public static MiniGameType fromString(String name) {
        for (MiniGameType type : values()) {
            if (type.name().equalsIgnoreCase(name) || 
                type.name().replace("_", "").equalsIgnoreCase(name.replace("_", "").replace(" ", ""))) {
                return type;
            }
        }
        return null;
    }
}
