package com.moonlight.coreprotect.kits;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.inventory.ItemFlag;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Kit Demon personalizado que intercepta /kit demon de Essentials.
 * Permiso requerido: essentials.kits.demon
 * Cooldown: 360000 segundos (100 horas)
 *
 * Habilidades pasivas (al llevar el set):
 * - Alma Ardiente: Inmunidad a fuego/lava
 * - Sed de Sangre: Lifesteal 15% al golpear
 * - Aura Infernal: Los enemigos cercanos reciben Slowness + daño periódico
 * - Pacto Demoníaco: Al bajar de 20% HP → ráfaga de fuerza y regeneración
 */
public class KitDemon implements Listener {

    private final CoreProtectPlugin plugin;
    private final File cooldownFile;
    private static final long COOLDOWN_MS = 360000L * 1000L; // 360000 segundos en milisegundos

    public KitDemon(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        // Use shared cooldown file with KitsCommand
        this.cooldownFile = new File(plugin.getDataFolder(), "kit_cooldowns.yml");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage().substring(1).trim();
        String[] parts = message.split("\\s+");
        String baseCommand = parts[0].toLowerCase();

        // Interceptar /kit demon, /essentials:kit demon, /ekit demon
        if ((baseCommand.equals("kit") || baseCommand.equals("essentials:kit") || baseCommand.equals("ekit")) 
                && parts.length >= 2 && parts[1].equalsIgnoreCase("demon")) {
            event.setCancelled(true);
            giveKit(event.getPlayer());
        }
    }

    private void giveKit(Player player) {
        // Verificar permiso
        if (!player.hasPermission("essentials.kits.demon")) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo tienes permiso para usar este kit."));
            return;
        }

        // Verificar cooldown (OPs sin cooldown) - use shared cooldown system
        UUID uid = player.getUniqueId();
        if (!player.isOp()) {
            Long lastUse = getSharedCooldown(uid);
            if (lastUse != null) {
                long remaining = COOLDOWN_MS - (System.currentTimeMillis() - lastUse);
                if (remaining > 0) {
                    long hours = remaining / (1000 * 60 * 60);
                    long minutes = (remaining % (1000 * 60 * 60)) / (1000 * 60);
                    long seconds = (remaining % (1000 * 60)) / 1000;
                    player.sendMessage(SmallCaps.convert("§c§l✖ §cDebes esperar §e" + hours + "h " + minutes + "m " + seconds + "s §cpara usar este kit de nuevo."));
                    return;
                }
            }
        }

        // Check if inventory has space (Kit Demon requires ~18 slots)
        int emptySlots = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) {
                emptySlots++;
            }
        }
        if (emptySlots < 18) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cTu inventario está lleno. Libera espacio para recibir el kit."));
            return;
        }

        // Registrar cooldown (solo si no es OP) - use shared cooldown system
        if (!player.isOp()) {
            setSharedCooldown(uid, System.currentTimeMillis());
        }

        // === CASCO ===
        ItemStack helmet = new ItemStack(Material.NETHERITE_HELMET);
        ItemMeta hMeta = helmet.getItemMeta();
        hMeta.setDisplayName(SmallCaps.convert("§4§k|§r §c§lCorona del Abismo §4§k|"));
        hMeta.setLore(getSetLore());
        hMeta.addEnchant(Enchantment.PROTECTION, 7, true);
        hMeta.addEnchant(Enchantment.UNBREAKING, 4, true);
        hMeta.addEnchant(Enchantment.MENDING, 1, true);
        hMeta.addEnchant(Enchantment.RESPIRATION, 4, true);
        hMeta.addEnchant(Enchantment.AQUA_AFFINITY, 4, true);
        hMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        hMeta.getPersistentDataContainer().set(getDemonKey(plugin), PersistentDataType.BYTE, (byte) 1);
        hMeta.getPersistentDataContainer().set(getDemonOwnerKey(plugin), PersistentDataType.STRING, player.getUniqueId().toString());
        helmet.setItemMeta(hMeta);
        applyTrim(helmet, TrimPattern.HOST, TrimMaterial.REDSTONE);

        // === PETO ===
        ItemStack chestplate = new ItemStack(Material.NETHERITE_CHESTPLATE);
        ItemMeta cMeta = chestplate.getItemMeta();
        cMeta.setDisplayName(SmallCaps.convert("§4§k|§r §c§lCoraza del Diablo §4§k|"));
        cMeta.setLore(getSetLore());
        cMeta.addEnchant(Enchantment.PROTECTION, 7, true);
        cMeta.addEnchant(Enchantment.UNBREAKING, 4, true);
        cMeta.addEnchant(Enchantment.MENDING, 1, true);
        cMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        cMeta.getPersistentDataContainer().set(getDemonKey(plugin), PersistentDataType.BYTE, (byte) 1);
        cMeta.getPersistentDataContainer().set(getDemonOwnerKey(plugin), PersistentDataType.STRING, player.getUniqueId().toString());
        chestplate.setItemMeta(cMeta);
        applyTrim(chestplate, TrimPattern.HOST, TrimMaterial.REDSTONE);

        // === PANTALÓN ===
        ItemStack leggings = new ItemStack(Material.NETHERITE_LEGGINGS);
        ItemMeta lMeta = leggings.getItemMeta();
        lMeta.setDisplayName(SmallCaps.convert("§4§k|§r §c§lGrebas Infernales §4§k|"));
        lMeta.setLore(getSetLore());
        lMeta.addEnchant(Enchantment.PROTECTION, 7, true);
        lMeta.addEnchant(Enchantment.UNBREAKING, 4, true);
        lMeta.addEnchant(Enchantment.MENDING, 1, true);
        lMeta.addEnchant(Enchantment.SWIFT_SNEAK, 4, true);
        lMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        lMeta.getPersistentDataContainer().set(getDemonKey(plugin), PersistentDataType.BYTE, (byte) 1);
        lMeta.getPersistentDataContainer().set(getDemonOwnerKey(plugin), PersistentDataType.STRING, player.getUniqueId().toString());
        leggings.setItemMeta(lMeta);
        applyTrim(leggings, TrimPattern.HOST, TrimMaterial.REDSTONE);

        // === BOTAS ===
        ItemStack boots = new ItemStack(Material.NETHERITE_BOOTS);
        ItemMeta bMeta = boots.getItemMeta();
        bMeta.setDisplayName(SmallCaps.convert("§4§k|§r §c§lBotas de la Condena §4§k|"));
        bMeta.setLore(getSetLore());
        bMeta.addEnchant(Enchantment.PROTECTION, 7, true);
        bMeta.addEnchant(Enchantment.UNBREAKING, 4, true);
        bMeta.addEnchant(Enchantment.MENDING, 1, true);
        bMeta.addEnchant(Enchantment.FEATHER_FALLING, 5, true);
        bMeta.addEnchant(Enchantment.DEPTH_STRIDER, 4, true);
        bMeta.addEnchant(Enchantment.SOUL_SPEED, 4, true);
        bMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        bMeta.getPersistentDataContainer().set(getDemonKey(plugin), PersistentDataType.BYTE, (byte) 1);
        bMeta.getPersistentDataContainer().set(getDemonOwnerKey(plugin), PersistentDataType.STRING, player.getUniqueId().toString());
        boots.setItemMeta(bMeta);
        applyTrim(boots, TrimPattern.HOST, TrimMaterial.REDSTONE);

        // === ARMAS Y HERRAMIENTAS ===
        ItemStack sword = createTool(Material.NETHERITE_SWORD,
                "§4§k|§r §c§lColmillo Demoníaco §4§k|",
                new Object[]{Enchantment.SHARPNESS, 7, Enchantment.FIRE_ASPECT, 4,
                        Enchantment.LOOTING, 4,
                        Enchantment.UNBREAKING, 4, Enchantment.MENDING, 1}, player);

        ItemMeta swordMeta = sword.getItemMeta();
        List<String> swordLore = swordMeta.getLore();
        if (swordLore == null) swordLore = new java.util.ArrayList<>();
        swordLore.add("");
        swordLore.add("§c▸ Furia Demoníaca §7(Shift + Click Derecho)");
        swordLore.add("§7  Dash igneo frontal que quema a los enemigos.");
        swordLore.add("§7  CD: §e15s§7.");
        swordMeta.setLore(swordLore);
        sword.setItemMeta(swordMeta);

        ItemStack pickaxe = createTool(Material.NETHERITE_PICKAXE, "§4§lPico Demoníaco",
                new Object[]{Enchantment.EFFICIENCY, 7, Enchantment.FORTUNE, 4,
                        Enchantment.UNBREAKING, 4, Enchantment.MENDING, 1}, player);

        ItemStack axe = createTool(Material.NETHERITE_AXE, "§4§lHacha Demoníaca",
                new Object[]{Enchantment.EFFICIENCY, 7, Enchantment.FORTUNE, 4,
                        Enchantment.UNBREAKING, 4, Enchantment.MENDING, 1}, player);

        ItemStack shovel = createTool(Material.NETHERITE_SHOVEL, "§4§lPala Demoníaca",
                new Object[]{Enchantment.EFFICIENCY, 7, Enchantment.SILK_TOUCH, 1,
                        Enchantment.UNBREAKING, 4, Enchantment.MENDING, 1}, player);

        ItemStack hoe = createTool(Material.NETHERITE_HOE, "§4§lAzada Demoníaca",
                new Object[]{Enchantment.EFFICIENCY, 7, Enchantment.FORTUNE, 4,
                        Enchantment.UNBREAKING, 4, Enchantment.MENDING, 1}, player);

        // === ITEMS EXTRA ===
        // Dar todos los items
        player.getInventory().addItem(
                helmet, chestplate, leggings, boots,
                sword, pickaxe, axe, shovel, hoe,
                new ItemStack(Material.EXPERIENCE_BOTTLE, 64),
                new ItemStack(Material.EXPERIENCE_BOTTLE, 64),
                new ItemStack(Material.EXPERIENCE_BOTTLE, 64),
                new ItemStack(Material.EXPERIENCE_BOTTLE, 64),
                new ItemStack(Material.ENDER_PEARL, 16),
                new ItemStack(Material.ENDER_PEARL, 16),
                new ItemStack(Material.GOLDEN_APPLE, 64),
                new ItemStack(Material.GOLDEN_APPLE, 64),
                new ItemStack(Material.AMETHYST_BLOCK, 32),
                new ItemStack(Material.DIAMOND_BLOCK, 32),
                new ItemStack(Material.EMERALD_BLOCK, 42),
                new ItemStack(Material.LAPIS_BLOCK, 32),
                new ItemStack(Material.GOLD_BLOCK, 32),
                new ItemStack(Material.ENDER_PEARL, 16),
                new ItemStack(Material.EXPERIENCE_BOTTLE, 64),
                new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 32)
        );

        player.sendTitle( SmallCaps.convert("§4§l☠ Kit Demon ☠"), SmallCaps.convert("§c¡El poder del abismo es tuyo!"), 10, 60, 20);
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§4§l☠ §c§lKit Demon §4§l☠ §7— Set recibido."));
        player.sendMessage(SmallCaps.convert("§c  ▸ Furia Demoníaca §8(Espada)§7: Dash igneo (Shift+ClickDcho)."));
        player.sendMessage(SmallCaps.convert("§c  ▸ Alma Ardiente §8(1p)§7: Inmunidad al fuego, absorber llamas cura."));
        player.sendMessage(SmallCaps.convert("§c  ▸ Sed de Sangre §8(2p)§7: Robavida 20% + Celeridad al matar."));
        player.sendMessage(SmallCaps.convert("§c  ▸ Marca Demoniaca §8(2p)§7: Marca objetivos. +35% daño."));
        player.sendMessage(SmallCaps.convert("§c  ▸ Aura Infernal §8(3p)§7: Marchit. II + daño a cercanos."));
        player.sendMessage(SmallCaps.convert("§c  ▸ Pacto Demoníaco §8(4p)§7: Al 20% HP: Fuerza III + Regen III."));
        player.sendMessage(SmallCaps.convert("§c  ▸ Poder del Abismo: §7Fuerza II (Peto) y Velocidad II (Botas)."));
        player.sendMessage("");
        if (player.isOp()) player.sendMessage(SmallCaps.convert("§8[OP] Sin cooldown."));
    }

    private ItemStack createTool(Material material, String displayName, Object[] enchants, Player player) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(displayName);
        for (int i = 0; i < enchants.length; i += 2) {
            meta.addEnchant((Enchantment) enchants[i], (int) enchants[i + 1], true);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(getDemonKey(plugin), PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(getDemonOwnerKey(plugin), PersistentDataType.STRING, player.getUniqueId().toString());
        item.setItemMeta(meta);
        return item;
    }

    private static List<String> getSetLore() {
        return Arrays.asList(
                "§8§m                              ",
                "§4§l✦ Set Demoniaco §4§l✦",
                "§8§m                              ",
                "§c▸ Poder del Abismo",
                "§7  Espada: Furia Demoníaca (Dash Igneo)",
                "§7  Peto: Otorga §cFuerza II§7 permanentemente.",
                "§7  Botas: Otorga §bVelocidad II§7 permanentemente.",
                "",
                "§c▸ Alma Ardiente §8(1 pieza)",
                "§7  Inmunidad al fuego. Absorber",
                "§7  llamas restaura vida.",
                "",
                "§c▸ Sed de Sangre §8(2 piezas)",
                "§7  Robavida §c20%§7. §eCeleridad I §7al matar.",
                "",
                "§c▸ Marca Demoniaca §8(2 piezas)",
                "§7  Golpear marca al objetivo §c8s§7.",
                "§7  §c+35% §7daño contra marcados. CD §e15s§7.",
                "",
                "§c▸ Aura Infernal §8(3 piezas)",
                "§7  Anillo de fuego: §8Marchit. II §7+",
                "§7  daño a enemigos en §c4 bloques§7.",
                "",
                "§c▸ Pacto Demoníaco §8(4 piezas)",
                "§7  Al §c20% HP§7: §cFuerza III §7+ §aRegen III",
                "§7  + empuje demoníaco. CD §e60s§7.",
                "",
                "§8§m                              ",
                "§4§o\"El demonio que llevas dentro despierta.\""
        );
    }

    private void applyTrim(ItemStack item, TrimPattern pattern, TrimMaterial material) {
        if (item.getItemMeta() instanceof ArmorMeta) {
            ArmorMeta meta = (ArmorMeta) item.getItemMeta();
            meta.setTrim(new ArmorTrim(material, pattern));
            item.setItemMeta(meta);
        }
    }

    public static final String DEMON_KEY = "demon_kit";
    public static final String DEMON_OWNER_KEY = "demon_owner";

    public static NamespacedKey getDemonKey(CoreProtectPlugin plugin) {
        return new NamespacedKey(plugin, DEMON_KEY);
    }

    public static NamespacedKey getDemonOwnerKey(CoreProtectPlugin plugin) {
        return new NamespacedKey(plugin, DEMON_OWNER_KEY);
    }

    public static boolean isDemonPiece(ItemStack item, CoreProtectPlugin plugin) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(getDemonKey(plugin), PersistentDataType.BYTE);
    }

    /**
     * Cuenta cuántas piezas del set demon lleva el jugador.
     */
    public static int getDemonPieceCount(org.bukkit.entity.Player player, CoreProtectPlugin plugin) {
        int count = 0;
        if (isDemonPiece(player.getInventory().getHelmet(), plugin)) count++;
        if (isDemonPiece(player.getInventory().getChestplate(), plugin)) count++;
        if (isDemonPiece(player.getInventory().getLeggings(), plugin)) count++;
        if (isDemonPiece(player.getInventory().getBoots(), plugin)) count++;
        return count;
    }

    // ==================== SHARED COOLDOWN SYSTEM ====================
    // Use the same cooldown file as KitsCommand to prevent bypass

    private Long getSharedCooldown(UUID uid) {
        if (!cooldownFile.exists()) return null;
        try {
            org.bukkit.configuration.file.YamlConfiguration yaml =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(cooldownFile);
            if (yaml.isSet("demon." + uid.toString())) {
                return yaml.getLong("demon." + uid.toString());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[KitDemon] Error leyendo cooldown: " + e.getMessage());
        }
        return null;
    }

    private void setSharedCooldown(UUID uid, long timestamp) {
        try {
            org.bukkit.configuration.file.YamlConfiguration yaml;
            if (cooldownFile.exists()) {
                yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(cooldownFile);
            } else {
                yaml = new org.bukkit.configuration.file.YamlConfiguration();
            }
            yaml.set("demon." + uid.toString(), timestamp);
            yaml.save(cooldownFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[KitDemon] Error guardando cooldown: " + e.getMessage());
        }
    }
}
