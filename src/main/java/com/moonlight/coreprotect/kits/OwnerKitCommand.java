package com.moonlight.coreprotect.kits;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.List;

/**
 * /ownerkit — Kit exclusivo para OPs. Absurdamente roto.
 * Sin cooldown. Sin límites. Sin piedad.
 *
 * Habilidades pasivas (al llevar el set):
 * - Invulnerabilidad Divina: Inmunidad a TODO el daño (excepto /kill y void)
 * - Aura Aniquiladora: Mobs hostiles en 25 bloques mueren instantáneamente
 * - Poder Absoluto: Fuerza V + Velocidad III + Resistencia IV + Haste III + Night Vision permanentes
 * - Regeneración Infinita: Curación completa cada segundo
 * - Vuelo Perpetuo: Vuelo creativo permanente
 * - Juicio Final (Espada): Shift+Click Derecho = Tormenta de rayos AoE que aniquila todo
 */
public class OwnerKitCommand implements CommandExecutor {

    private final CoreProtectPlugin plugin;

    public static final String OWNER_KEY = "owner_kit";
    public static final String OWNER_KIT_NAME = "§6§k|||§r §e§l⭐ SET DIVINO ⭐ §6§k|||";

    public OwnerKitCommand(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSolo jugadores.");
            return true;
        }

        if (!player.isOp()) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo tienes permiso para usar este comando."));
            return true;
        }

        giveKit(player);
        return true;
    }

    // ==================== GIVE KIT ====================

    private void giveKit(Player player) {
        player.getInventory().clear();

        // === ARMADURA ===
        player.getInventory().setHelmet(createHelmet(player));
        player.getInventory().setChestplate(createChestplate(player));
        player.getInventory().setLeggings(createLeggings(player));
        player.getInventory().setBoots(createBoots(player));

        // === ARMAS Y HERRAMIENTAS ===
        player.getInventory().addItem(
                createSword(player),
                createPickaxe(player),
                createAxe(player),
                createShovel(player),
                createBow(player),
                createMace(player),
                createTrident(player)
        );

        // === EXTRAS ABSURDOS ===
        player.getInventory().addItem(
                new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 64),
                new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 64),
                new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 64),
                new ItemStack(Material.TOTEM_OF_UNDYING),
                new ItemStack(Material.TOTEM_OF_UNDYING),
                new ItemStack(Material.TOTEM_OF_UNDYING),
                new ItemStack(Material.TOTEM_OF_UNDYING),
                new ItemStack(Material.ELYTRA),
                new ItemStack(Material.FIREWORK_ROCKET, 64),
                new ItemStack(Material.FIREWORK_ROCKET, 64),
                new ItemStack(Material.FIREWORK_ROCKET, 64),
                new ItemStack(Material.ENDER_PEARL, 64),
                new ItemStack(Material.ENDER_PEARL, 64),
                new ItemStack(Material.NETHERITE_BLOCK, 64),
                new ItemStack(Material.NETHERITE_BLOCK, 64),
                new ItemStack(Material.EXPERIENCE_BOTTLE, 64),
                new ItemStack(Material.EXPERIENCE_BOTTLE, 64),
                new ItemStack(Material.EXPERIENCE_BOTTLE, 64),
                new ItemStack(Material.EXPERIENCE_BOTTLE, 64),
                new ItemStack(Material.DIAMOND_BLOCK, 64),
                new ItemStack(Material.EMERALD_BLOCK, 64),
                new ItemStack(Material.ARROW, 64)
        );

        // Offhand: Totem
        player.getInventory().setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));

        // Heal + feed
        player.setHealth(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
        player.setFoodLevel(20);
        player.setSaturation(20f);

        // Efectos épicos
        Location loc = player.getLocation();
        World world = loc.getWorld();

        // Explosión de partículas doradas
        world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 2, 0), 300, 3, 4, 3, 0.15);
        world.spawnParticle(Particle.DUST, loc.clone().add(0, 1, 0), 200, 2, 3, 2, 0,
                new Particle.DustOptions(Color.fromRGB(255, 215, 0), 3.0f));
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1, 0), 150, 2, 3, 2, 0.3);
        world.spawnParticle(Particle.FLASH, loc, 3, 0, 0, 0, 0);

        // Anillo de rayos
        for (int i = 0; i < 8; i++) {
            double angle = (Math.PI * 2.0 / 8) * i;
            Location strike = loc.clone().add(Math.cos(angle) * 4, 0, Math.sin(angle) * 4);
            world.strikeLightningEffect(strike);
        }

        // Sonidos
        world.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.5f);
        world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_DEATH, 0.5f, 1.5f);
        world.playSound(loc, Sound.ENTITY_WITHER_SPAWN, 0.3f, 2.0f);
        world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.5f);

        // Título
        player.sendTitle(
                SmallCaps.convert("§6§l⭐ MODO DIOS ⭐"),
                SmallCaps.convert("§e¡El poder absoluto es tuyo!"), 10, 80, 20);

        // Mensajes
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§6§l⭐§e§l════════════════════════════════§6§l⭐"));
        player.sendMessage(SmallCaps.convert("§6§l    ⭐ OWNER KIT — SET DIVINO ⭐"));
        player.sendMessage(SmallCaps.convert("§6§l⭐§e§l════════════════════════════════§6§l⭐"));
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§e  ▸ Invulnerabilidad Divina §8(1p)"));
        player.sendMessage(SmallCaps.convert("§7    Inmune a TODO tipo de daño."));
        player.sendMessage(SmallCaps.convert("§e  ▸ Aura Aniquiladora §8(2p)"));
        player.sendMessage(SmallCaps.convert("§7    Mobs hostiles en 25 bloques mueren al instante."));
        player.sendMessage(SmallCaps.convert("§e  ▸ Poder Absoluto §8(3p)"));
        player.sendMessage(SmallCaps.convert("§7    Fuerza V + Velocidad III + Resistencia IV permanentes."));
        player.sendMessage(SmallCaps.convert("§e  ▸ Regeneración Infinita §8(4p)"));
        player.sendMessage(SmallCaps.convert("§7    Curación completa cada segundo. Vuelo permanente."));
        player.sendMessage(SmallCaps.convert("§e  ▸ Juicio Final §8(Espada)"));
        player.sendMessage(SmallCaps.convert("§7    Shift + Click Derecho = Tormenta de rayos devastadora."));
        player.sendMessage(SmallCaps.convert("§e  ▸ Golpe Divino §8(Pasivo)"));
        player.sendMessage(SmallCaps.convert("§7    Cada golpe invoca un rayo sobre el enemigo."));
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§6§l⭐§e§l════════════════════════════════§6§l⭐"));
        player.sendMessage(SmallCaps.convert("§8  Solo para los dioses del servidor."));
        player.sendMessage("");
    }

    // ==================== ITEM CREATION ====================

    public static NamespacedKey getOwnerKey(CoreProtectPlugin plugin) {
        return new NamespacedKey(plugin, OWNER_KEY);
    }

    public static boolean isOwnerPiece(ItemStack item, CoreProtectPlugin plugin) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(getOwnerKey(plugin), PersistentDataType.BYTE);
    }

    public static int getOwnerPieceCount(Player player, CoreProtectPlugin plugin) {
        int count = 0;
        if (isOwnerPiece(player.getInventory().getHelmet(), plugin)) count++;
        if (isOwnerPiece(player.getInventory().getChestplate(), plugin)) count++;
        if (isOwnerPiece(player.getInventory().getLeggings(), plugin)) count++;
        if (isOwnerPiece(player.getInventory().getBoots(), plugin)) count++;
        return count;
    }

    private void markOwner(ItemMeta meta) {
        meta.getPersistentDataContainer().set(getOwnerKey(plugin), PersistentDataType.BYTE, (byte) 1);
    }

    private static List<String> getSetLore() {
        return Arrays.asList(
                "§6§m                                  ",
                "§6§l⭐ Set Divino del Creador ⭐",
                "§6§m                                  ",
                "",
                "§e▸ Invulnerabilidad Divina §8(1 pieza)",
                "§7  Inmune a todo tipo de daño.",
                "§7  El daño simplemente no existe para ti.",
                "",
                "§e▸ Aura Aniquiladora §8(2 piezas)",
                "§7  Los mobs hostiles en §c25 bloques",
                "§7  mueren instantáneamente.",
                "",
                "§e▸ Poder Absoluto §8(3 piezas)",
                "§7  §cFuerza V §7+ §bVelocidad III §7+ §9Resistencia IV",
                "§7  + §eHaste III §7+ §dVisión Nocturna §7permanentes.",
                "",
                "§e▸ Regeneración Infinita §8(4 piezas)",
                "§7  Curación completa cada segundo.",
                "§7  Vuelo creativo permanente.",
                "§7  §aSaturación infinita.",
                "",
                "§e▸ Juicio Final §8(Espada, Shift+Click Dcho)",
                "§7  Tormenta de rayos que aniquila",
                "§7  todo en §c30 bloques§7. CD: §e10s§7.",
                "",
                "§e▸ Golpe Divino §8(Pasivo al golpear)",
                "§7  Cada golpe invoca un rayo sobre el enemigo.",
                "",
                "§6§m                                  ",
                "§6§o\"Solo un dios puede portar el sol.\""
        );
    }

    private static List<String> getSwordLore() {
        List<String> lore = new java.util.ArrayList<>(getSetLore());
        lore.add("");
        lore.add("§6§l⭐ HABILIDAD ACTIVA:");
        lore.add("§e  Juicio Final §7(Shift + Click Derecho)");
        lore.add("§7  Tormenta de rayos AoE devastadora.");
        lore.add("§7  Mata todo en §c30 bloques§7. CD: §e10s§7.");
        return lore;
    }

    private void applyTrim(ItemStack item, TrimPattern pattern, TrimMaterial material) {
        if (item.getItemMeta() instanceof ArmorMeta armorMeta) {
            armorMeta.setTrim(new ArmorTrim(material, pattern));
            item.setItemMeta(armorMeta);
        }
    }

    // === HELMET ===
    private ItemStack createHelmet(Player player) {
        ItemStack item = new ItemStack(Material.NETHERITE_HELMET);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(SmallCaps.convert("§6§k|||§r §e§l⭐ Corona del Creador §6§k|||"));
        meta.setLore(getSetLore());
        meta.addEnchant(Enchantment.PROTECTION, 15, true);
        meta.addEnchant(Enchantment.UNBREAKING, 10, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.addEnchant(Enchantment.RESPIRATION, 10, true);
        meta.addEnchant(Enchantment.AQUA_AFFINITY, 1, true);
        meta.addEnchant(Enchantment.THORNS, 10, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        markOwner(meta);
        item.setItemMeta(meta);
        applyTrim(item, TrimPattern.SILENCE, TrimMaterial.GOLD);
        return item;
    }

    // === CHESTPLATE ===
    private ItemStack createChestplate(Player player) {
        ItemStack item = new ItemStack(Material.NETHERITE_CHESTPLATE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(SmallCaps.convert("§6§k|||§r §e§l⭐ Pectoral Divino §6§k|||"));
        meta.setLore(getSetLore());
        meta.addEnchant(Enchantment.PROTECTION, 15, true);
        meta.addEnchant(Enchantment.UNBREAKING, 10, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.addEnchant(Enchantment.THORNS, 10, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        markOwner(meta);
        item.setItemMeta(meta);
        applyTrim(item, TrimPattern.SILENCE, TrimMaterial.GOLD);
        return item;
    }

    // === LEGGINGS ===
    private ItemStack createLeggings(Player player) {
        ItemStack item = new ItemStack(Material.NETHERITE_LEGGINGS);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(SmallCaps.convert("§6§k|||§r §e§l⭐ Grebas Celestiales §6§k|||"));
        meta.setLore(getSetLore());
        meta.addEnchant(Enchantment.PROTECTION, 15, true);
        meta.addEnchant(Enchantment.UNBREAKING, 10, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.addEnchant(Enchantment.SWIFT_SNEAK, 5, true);
        meta.addEnchant(Enchantment.THORNS, 10, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        markOwner(meta);
        item.setItemMeta(meta);
        applyTrim(item, TrimPattern.SILENCE, TrimMaterial.GOLD);
        return item;
    }

    // === BOOTS ===
    private ItemStack createBoots(Player player) {
        ItemStack item = new ItemStack(Material.NETHERITE_BOOTS);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(SmallCaps.convert("§6§k|||§r §e§l⭐ Botas del Omnipotente §6§k|||"));
        meta.setLore(getSetLore());
        meta.addEnchant(Enchantment.PROTECTION, 15, true);
        meta.addEnchant(Enchantment.UNBREAKING, 10, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.addEnchant(Enchantment.FEATHER_FALLING, 15, true);
        meta.addEnchant(Enchantment.DEPTH_STRIDER, 5, true);
        meta.addEnchant(Enchantment.SOUL_SPEED, 5, true);
        meta.addEnchant(Enchantment.THORNS, 10, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        markOwner(meta);
        item.setItemMeta(meta);
        applyTrim(item, TrimPattern.SILENCE, TrimMaterial.GOLD);
        return item;
    }

    // === SWORD ===
    private ItemStack createSword(Player player) {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(SmallCaps.convert("§6§k|||§r §e§l⭐ Espada del Juicio Final §6§k|||"));
        meta.setLore(getSwordLore());
        meta.addEnchant(Enchantment.SHARPNESS, 15, true);
        meta.addEnchant(Enchantment.FIRE_ASPECT, 10, true);
        meta.addEnchant(Enchantment.LOOTING, 10, true);
        meta.addEnchant(Enchantment.SWEEPING_EDGE, 5, true);
        meta.addEnchant(Enchantment.KNOCKBACK, 3, true);
        meta.addEnchant(Enchantment.UNBREAKING, 10, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        markOwner(meta);
        item.setItemMeta(meta);
        return item;
    }

    // === PICKAXE ===
    private ItemStack createPickaxe(Player player) {
        ItemStack item = new ItemStack(Material.NETHERITE_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(SmallCaps.convert("§6§l⭐ Pico Omnisciente"));
        meta.addEnchant(Enchantment.EFFICIENCY, 15, true);
        meta.addEnchant(Enchantment.FORTUNE, 10, true);
        meta.addEnchant(Enchantment.UNBREAKING, 10, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        markOwner(meta);
        item.setItemMeta(meta);
        return item;
    }

    // === AXE ===
    private ItemStack createAxe(Player player) {
        ItemStack item = new ItemStack(Material.NETHERITE_AXE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(SmallCaps.convert("§6§l⭐ Hacha del Apocalipsis"));
        meta.addEnchant(Enchantment.EFFICIENCY, 15, true);
        meta.addEnchant(Enchantment.SHARPNESS, 15, true);
        meta.addEnchant(Enchantment.UNBREAKING, 10, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        markOwner(meta);
        item.setItemMeta(meta);
        return item;
    }

    // === SHOVEL ===
    private ItemStack createShovel(Player player) {
        ItemStack item = new ItemStack(Material.NETHERITE_SHOVEL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(SmallCaps.convert("§6§l⭐ Pala Terraformadora"));
        meta.addEnchant(Enchantment.EFFICIENCY, 15, true);
        meta.addEnchant(Enchantment.SILK_TOUCH, 1, true);
        meta.addEnchant(Enchantment.UNBREAKING, 10, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        markOwner(meta);
        item.setItemMeta(meta);
        return item;
    }

    // === BOW ===
    private ItemStack createBow(Player player) {
        ItemStack item = new ItemStack(Material.BOW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(SmallCaps.convert("§6§l⭐ Arco del Firmamento"));
        meta.addEnchant(Enchantment.POWER, 10, true);
        meta.addEnchant(Enchantment.FLAME, 1, true);
        meta.addEnchant(Enchantment.INFINITY, 1, true);
        meta.addEnchant(Enchantment.PUNCH, 5, true);
        meta.addEnchant(Enchantment.UNBREAKING, 10, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        markOwner(meta);
        item.setItemMeta(meta);
        return item;
    }

    // === MACE ===
    private ItemStack createMace(Player player) {
        ItemStack item = new ItemStack(Material.MACE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(SmallCaps.convert("§6§l⭐ Mazo del Cataclismo"));
        meta.addEnchant(Enchantment.DENSITY, 25, true);
        meta.addEnchant(Enchantment.WIND_BURST, 5, true);
        meta.addEnchant(Enchantment.FIRE_ASPECT, 10, true);
        meta.addEnchant(Enchantment.UNBREAKING, 10, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        markOwner(meta);
        item.setItemMeta(meta);
        return item;
    }

    // === TRIDENT ===
    private ItemStack createTrident(Player player) {
        ItemStack item = new ItemStack(Material.TRIDENT);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(SmallCaps.convert("§6§l⭐ Tridente de Poseidón"));
        meta.addEnchant(Enchantment.LOYALTY, 10, true);
        meta.addEnchant(Enchantment.CHANNELING, 1, true);
        meta.addEnchant(Enchantment.IMPALING, 10, true);
        meta.addEnchant(Enchantment.UNBREAKING, 10, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        markOwner(meta);
        item.setItemMeta(meta);
        return item;
    }
}
