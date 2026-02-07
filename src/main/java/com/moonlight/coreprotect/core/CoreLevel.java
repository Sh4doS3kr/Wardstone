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

    public CoreLevel(int level, Material material, String name, double price, int size) {
        this.level = level;
        this.material = material;
        this.name = name;
        this.price = price;
        this.size = size;
        this.particle = getParticleForLevel(level);
    }

    public static CoreLevel fromConfig(FileConfiguration config, int level) {
        String path = "levels." + level + ".";
        Material material = Material.valueOf(config.getString(path + "material", "STONE"));
        String name = config.getString(path + "name", "Nucleo Nivel " + level);
        double price = config.getDouble(path + "price", 1000);
        int size = config.getInt(path + "size", 10);
        return new CoreLevel(level, material, name, price, size);
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

        meta.setDisplayName(org.bukkit.ChatColor.AQUA + name);

        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add("");
        lore.add(org.bukkit.ChatColor.GRAY + "Nivel: " + org.bukkit.ChatColor.WHITE + level);
        lore.add(org.bukkit.ChatColor.GRAY + "Precio: " + org.bukkit.ChatColor.GOLD + "$" + getFormattedPrice());
        lore.add(org.bukkit.ChatColor.GRAY + "Area: " + org.bukkit.ChatColor.WHITE + size + "x" + size);
        lore.add("");
        lore.add(org.bukkit.ChatColor.GRAY + "Proteccion desde Y minima");
        lore.add(org.bukkit.ChatColor.GRAY + "hasta Y maxima del mundo");
        lore.add("");
        lore.add(org.bukkit.ChatColor.YELLOW + "Click para comprar"); // Esto podria ser condicional pero esta bien por
                                                                      // ahora

        // PersistentData para identificar el nivel al colocar
        // Nota: ProtectionListener.onBlockPlace chequea esto
        meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(CoreLevel.class),
                        "core_level"),
                org.bukkit.persistence.PersistentDataType.INTEGER,
                level);

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
