package com.moonlight.coreprotect.core;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.file.FileConfiguration;

public class CoreLevel {

    private final int level;
    private final Material material;
    private final String name;
    private final double price;
    private final int size;
    private final Particle particle;
    private final String vipRank; // null = no VIP required
    private final int prestigeRequired; // 0 = no prestige needed (core prestige)
    private final int globalPrestigeRequired; // 0 = no global prestige needed (/prestige)

    public CoreLevel(int level, Material material, String name, double price, int size, String vipRank, int prestigeRequired, int globalPrestigeRequired) {
        this.level = level;
        this.material = material;
        this.name = name;
        this.price = price;
        this.size = size;
        this.particle = getParticleForLevel(level);
        this.vipRank = vipRank;
        this.prestigeRequired = prestigeRequired;
        this.globalPrestigeRequired = globalPrestigeRequired;
    }

    public static CoreLevel fromConfig(FileConfiguration config, int level) {
        String path = "levels." + level + ".";
        if (!config.contains("levels." + level)) return null;
        Material material = Material.valueOf(config.getString(path + "material", "STONE"));
        String name = config.getString(path + "name", "Nucleo Nivel " + level);
        double price = config.getDouble(path + "price", 1000);
        int size = config.getInt(path + "size", 10);
        String vipRank = config.getString(path + "vip-rank", null);
        int prestigeRequired = config.getInt(path + "prestige-required", 0);
        int globalPrestigeRequired = config.getInt(path + "global-prestige-required", 0);
        return new CoreLevel(level, material, name, price, size, vipRank, prestigeRequired, globalPrestigeRequired);
    }

    private Particle getParticleForLevel(int level) {
        if (level <= 3)
            return Particle.CLOUD;
        if (level <= 6)
            return Particle.FLAME;
        if (level <= 9)
            return Particle.WITCH;
        if (level <= 12)
            return Particle.COMPOSTER;
        if (level <= 15)
            return Particle.END_ROD;
        if (level <= 18)
            return Particle.DRAGON_BREATH;
        if (level == 20)
            return Particle.SOUL_FIRE_FLAME;
        // VIP particles
        if (level == 21) return Particle.END_ROD;          // Luna - silver moonlight
        if (level == 22) return Particle.FIREWORK;         // Nova - golden star bursts
        if (level == 23) return Particle.DRAGON_BREATH;    // Eclipse - dark cosmic
        if (level == 24) return Particle.TOTEM_OF_UNDYING;  // MoonLord - divine
        // Prestige particles
        if (level == 25) return Particle.SOUL_FIRE_FLAME;   // Infernal
        if (level == 26) return Particle.SCULK_SOUL;        // Abismal
        if (level == 27) return Particle.FIREWORK;          // Celestial
        if (level == 28) return Particle.BUBBLE_POP;        // Atlantis
        if (level == 29) return Particle.DRAGON_BREATH;     // Dragon
        if (level == 30) return Particle.TOTEM_OF_UNDYING;  // Divino
        return Particle.SOUL_FIRE_FLAME;
    }

    public int getLevel() {
        return level;
    }

    public Material getMaterial() {
        return material;
    }

    public String getName() {
        return name;
    }

    public double getPrice() {
        return price;
    }

    public int getSize() {
        return size;
    }

    public Particle getParticle() {
        return particle;
    }

    public String getVipRank() {
        return vipRank;
    }

    public boolean isVip() {
        return vipRank != null;
    }

    public String getVipPermission() {
        return vipRank != null ? "wardstone.vip." + vipRank : null;
    }

    public int getPrestigeRequired() {
        return prestigeRequired;
    }

    public boolean requiresPrestige() {
        return prestigeRequired > 0;
    }

    public String getFormattedPrice() {
        if (price >= 1000000) {
            return String.format("%.1fM", price / 1000000);
        } else if (price >= 1000) {
            return String.format("%.1fK", price / 1000);
        }
        return String.valueOf((int) price);
    }

    public org.bukkit.inventory.ItemStack toItemStack() {
        org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(material);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(translateColors(name));

        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add("");
        lore.add(org.bukkit.ChatColor.GRAY + "Nivel: " + org.bukkit.ChatColor.WHITE + level);
        lore.add(org.bukkit.ChatColor.GRAY + "Precio: " + org.bukkit.ChatColor.GOLD + "$" + getFormattedPrice());
        lore.add(org.bukkit.ChatColor.GRAY + "Area: " + org.bukkit.ChatColor.WHITE + size + "x" + size);
        lore.add("");
        lore.add(org.bukkit.ChatColor.GRAY + "Proteccion desde Y minima");
        lore.add(org.bukkit.ChatColor.GRAY + "hasta Y maxima del mundo");
        if (isVip()) {
            lore.add("");
            lore.add(org.bukkit.ChatColor.GRAY + "Exclusivo rango " + translateColors(getVipGradientName()) );
            lore.add(org.bukkit.ChatColor.DARK_GRAY + "moonlightmc.tebex.io");
        }
        if (requiresPrestige()) {
            lore.add("");
            lore.add(org.bukkit.ChatColor.DARK_RED + "" + org.bukkit.ChatColor.BOLD + "★ " + org.bukkit.ChatColor.RED + "Requiere Prestigio Core " + getPrestigeRomanNumeral());
            lore.add(org.bukkit.ChatColor.DARK_GRAY + "Alcanza nivel 20 y prestigia " + prestigeRequired + "x");
        }
        if (requiresGlobalPrestige()) {
            lore.add("");
            lore.add(org.bukkit.ChatColor.LIGHT_PURPLE + "" + org.bukkit.ChatColor.BOLD + "✦ " + org.bukkit.ChatColor.LIGHT_PURPLE + "Requiere " + getGlobalPrestigeGlyph());
        }
        lore.add("");
        lore.add(org.bukkit.ChatColor.YELLOW + "Click para comprar");

        // PersistentData para identificar el nivel al colocar
        // Nota: ProtectionListener.onBlockPlace chequea esto
        meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey("coreprotect", "core_level"),
                org.bukkit.persistence.PersistentDataType.INTEGER,
                level);

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public String getVipGradientName() {
        if (vipRank == null) return "";
        switch (vipRank.toLowerCase()) {
            case "luna":
                return gradientText("Luna", new String[]{"&f", "&7", "&f", "&7"});
            case "nova":
                return gradientText("Nova", new String[]{"&e", "&6", "&e", "&6"});
            case "eclipse":
                return gradientText("Eclipse", new String[]{"&5", "&8", "&5", "&8", "&5", "&8", "&5"});
            case "moonlord":
                return gradientText("MoonLord", new String[]{"&d", "&5", "&d", "&5", "&d", "&5", "&d", "&5"});
            default:
                return vipRank;
        }
    }

    public String getPrestigeRomanNumeral() {
        switch (prestigeRequired) {
            case 1: return "I";
            case 2: return "II";
            case 3: return "III";
            default: return String.valueOf(prestigeRequired);
        }
    }

    /**
     * Returns the Nexo glyph tag for the required global prestige level.
     * Nexo replaces <glyph:rankN> in packets with the visual icon.
     */
    public String getGlobalPrestigeGlyph() {
        return "<glyph:rank" + globalPrestigeRequired + ">";
    }

    public boolean requiresGlobalPrestige() {
        return globalPrestigeRequired > 0;
    }

    public int getGlobalPrestigeRequired() {
        return globalPrestigeRequired;
    }

    private static String gradientText(String text, String[] colors) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            sb.append(colors[i % colors.length]).append(text.charAt(i));
        }
        return sb.toString();
    }

    private static String translateColors(String text) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', text);
    }
}
