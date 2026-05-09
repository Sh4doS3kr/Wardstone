package com.moonlight.coreprotect.afkzone;

public enum AfkRewardRarity {
    COMUN("§f§lCOMÚN",        "§f", "✦", 55),
    POCO_COMUN("§a§lPOCO COMÚN", "§a", "✦✦", 25),
    RARO("§b§lRARO",           "§b", "✦✦✦", 13),
    EPICO("§d§lÉPICO",         "§d", "✦✦✦✦", 5),
    LEGENDARIO("§6§lLEGENDARIO", "§6", "✦✦✦✦✦", 2);

    private final String display;
    private final String color;
    private final String stars;
    private final int chance;

    AfkRewardRarity(String display, String color, String stars, int chance) {
        this.display = display;
        this.color = color;
        this.stars = stars;
        this.chance = chance;
    }

    public String getDisplay() { return display; }
    public String getColor()   { return color; }
    public String getStars()   { return stars; }
    public int getChance()     { return chance; }
}
