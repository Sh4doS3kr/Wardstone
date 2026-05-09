package com.moonlight.coreprotect.kits;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Kit secreto de YouTube — set diamante con habilidades únicas:
 *
 * Pasivas (al llevar piezas):
 *  - Eco (1p): El 3er golpe consecutivo al mismo objetivo explota con +1♥ extra y knockback.
 *  - Refracción (2p): Si devuelves un golpe en 0.6s tras recibirlo, ese hit hace +30% daño (parry).
 *  - Resonancia Astral (3p): Al bajar de 30% HP, pulso que ralentiza enemigos + Speed I para ti. CD 45s.
 *  - Constelación (4p): Al estar quieto 2s, ganas Resistencia I. Moverte la quita.
 *
 * Espada activa:
 *  - Corte Estelar (Shift+Click Derecho): Dispara un proyectil que explota al impactar (2.5♥ + KB). CD 12s.
 */
public class KitYTSecret implements CommandExecutor {

    private final CoreProtectPlugin plugin;
    private final File cooldownFile;
    private final File claimedFile;
    private static final long COOLDOWN_MS = 259200L * 1000L; // 72 horas

    public static NamespacedKey getYTKey(CoreProtectPlugin plugin) {
        return new NamespacedKey(plugin, "yt_secret_kit");
    }

    public static NamespacedKey getYTOwnerKey(CoreProtectPlugin plugin) {
        return new NamespacedKey(plugin, "yt_secret_owner");
    }

    public KitYTSecret(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        this.cooldownFile = new File(plugin.getDataFolder(), "kit_cooldowns.yml");
        this.claimedFile = new File(plugin.getDataFolder(), "yt_secret_claimed.yml");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cSolo jugadores.");
            return true;
        }
        giveKit((Player) sender);
        return true;
    }

    private void giveKit(Player player) {
        if (!player.hasPermission("wardstone.kit.ytsecret") && !player.isOp()) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo tienes acceso a este kit secreto."));
            return;
        }

        UUID uid = player.getUniqueId();
        if (!player.isOp() && hasClaimed(uid)) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cYa has reclamado este kit. Solo puedes obtenerlo una vez."));
            return;
        }

        if (!player.isOp()) {
            Long lastUse = getCooldown(uid);
            if (lastUse != null) {
                long remaining = COOLDOWN_MS - (System.currentTimeMillis() - lastUse);
                if (remaining > 0) {
                    long h = remaining / (1000 * 60 * 60);
                    long m = (remaining % (1000 * 60 * 60)) / (1000 * 60);
                    player.sendMessage(SmallCaps.convert("§c§l✖ §cDebes esperar §e" + h + "h " + m + "m §cpara usar este kit."));
                    return;
                }
            }
        }

        int empty = 0;
        for (ItemStack i : player.getInventory().getStorageContents())
            if (i == null || i.getType() == Material.AIR) empty++;
        if (empty < 14) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNecesitas al menos 14 slots libres."));
            return;
        }

        if (!player.isOp()) {
            setCooldown(uid, System.currentTimeMillis());
            markClaimed(uid);
        }

        // === CASCO ===
        ItemStack helmet = new ItemStack(Material.DIAMOND_HELMET);
        ItemMeta hm = helmet.getItemMeta();
        hm.setDisplayName(SmallCaps.convert("§b§l✧ §fCorona Estelar §b§l✧"));
        hm.setLore(getSetLore());
        hm.addEnchant(Enchantment.PROTECTION, 4, true);
        hm.addEnchant(Enchantment.UNBREAKING, 3, true);
        hm.addEnchant(Enchantment.MENDING, 1, true);
        hm.addEnchant(Enchantment.RESPIRATION, 3, true);
        hm.addEnchant(Enchantment.AQUA_AFFINITY, 1, true);
        hm.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        hm.getPersistentDataContainer().set(getYTKey(plugin), PersistentDataType.BYTE, (byte) 1);
        hm.getPersistentDataContainer().set(getYTOwnerKey(plugin), PersistentDataType.STRING, uid.toString());
        helmet.setItemMeta(hm);
        applyTrim(helmet, TrimPattern.TIDE, TrimMaterial.DIAMOND);

        // === PETO ===
        ItemStack chestplate = new ItemStack(Material.DIAMOND_CHESTPLATE);
        ItemMeta cm = chestplate.getItemMeta();
        cm.setDisplayName(SmallCaps.convert("§b§l✧ §fPeto del Horizonte §b§l✧"));
        cm.setLore(getSetLore());
        cm.addEnchant(Enchantment.PROTECTION, 4, true);
        cm.addEnchant(Enchantment.UNBREAKING, 3, true);
        cm.addEnchant(Enchantment.MENDING, 1, true);
        cm.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        cm.getPersistentDataContainer().set(getYTKey(plugin), PersistentDataType.BYTE, (byte) 1);
        cm.getPersistentDataContainer().set(getYTOwnerKey(plugin), PersistentDataType.STRING, uid.toString());
        chestplate.setItemMeta(cm);
        applyTrim(chestplate, TrimPattern.TIDE, TrimMaterial.DIAMOND);

        // === PANTALONES ===
        ItemStack leggings = new ItemStack(Material.DIAMOND_LEGGINGS);
        ItemMeta lm = leggings.getItemMeta();
        lm.setDisplayName(SmallCaps.convert("§b§l✧ §fGrebas Lunares §b§l✧"));
        lm.setLore(getSetLore());
        lm.addEnchant(Enchantment.PROTECTION, 4, true);
        lm.addEnchant(Enchantment.UNBREAKING, 3, true);
        lm.addEnchant(Enchantment.MENDING, 1, true);
        lm.addEnchant(Enchantment.SWIFT_SNEAK, 3, true);
        lm.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        lm.getPersistentDataContainer().set(getYTKey(plugin), PersistentDataType.BYTE, (byte) 1);
        lm.getPersistentDataContainer().set(getYTOwnerKey(plugin), PersistentDataType.STRING, uid.toString());
        leggings.setItemMeta(lm);
        applyTrim(leggings, TrimPattern.TIDE, TrimMaterial.DIAMOND);

        // === BOTAS ===
        ItemStack boots = new ItemStack(Material.DIAMOND_BOOTS);
        ItemMeta bm = boots.getItemMeta();
        bm.setDisplayName(SmallCaps.convert("§b§l✧ §fBotas del Eco §b§l✧"));
        bm.setLore(getSetLore());
        bm.addEnchant(Enchantment.PROTECTION, 4, true);
        bm.addEnchant(Enchantment.UNBREAKING, 3, true);
        bm.addEnchant(Enchantment.MENDING, 1, true);
        bm.addEnchant(Enchantment.FEATHER_FALLING, 4, true);
        bm.addEnchant(Enchantment.DEPTH_STRIDER, 3, true);
        bm.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        bm.getPersistentDataContainer().set(getYTKey(plugin), PersistentDataType.BYTE, (byte) 1);
        bm.getPersistentDataContainer().set(getYTOwnerKey(plugin), PersistentDataType.STRING, uid.toString());
        boots.setItemMeta(bm);
        applyTrim(boots, TrimPattern.TIDE, TrimMaterial.DIAMOND);

        // === ESPADA ===
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta sm = sword.getItemMeta();
        sm.setDisplayName(SmallCaps.convert("§b§l✧ §fHoja Estelar §b§l✧"));
        List<String> swordLore = new ArrayList<>(getSetLore());
        swordLore.add("");
        swordLore.add("§b▸ Corte Estelar §7(Shift + Click Derecho)");
        swordLore.add("§7  Dispara un proyectil que explota");
        swordLore.add("§7  al impactar (§c2.5♥ §7+ knockback). CD §e12s§7.");
        sm.setLore(swordLore);
        sm.addEnchant(Enchantment.SHARPNESS, 5, true);
        sm.addEnchant(Enchantment.UNBREAKING, 3, true);
        sm.addEnchant(Enchantment.MENDING, 1, true);
        sm.addEnchant(Enchantment.LOOTING, 3, true);
        sm.addEnchant(Enchantment.SWEEPING_EDGE, 3, true);
        sm.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        sm.getPersistentDataContainer().set(getYTKey(plugin), PersistentDataType.BYTE, (byte) 1);
        sm.getPersistentDataContainer().set(getYTOwnerKey(plugin), PersistentDataType.STRING, uid.toString());
        sword.setItemMeta(sm);

        // === HERRAMIENTAS ===
        ItemStack pickaxe = createTool(Material.DIAMOND_PICKAXE, "§b§lPico Estelar",
                new Object[]{Enchantment.EFFICIENCY, 5, Enchantment.FORTUNE, 3, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1}, player);
        ItemStack axe = createTool(Material.DIAMOND_AXE, "§b§lHacha Estelar",
                new Object[]{Enchantment.EFFICIENCY, 5, Enchantment.FORTUNE, 3, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1}, player);
        ItemStack shovel = createTool(Material.DIAMOND_SHOVEL, "§b§lPala Estelar",
                new Object[]{Enchantment.EFFICIENCY, 5, Enchantment.SILK_TOUCH, 1, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1}, player);

        // === EXTRAS ===
        player.getInventory().addItem(
                helmet, chestplate, leggings, boots,
                sword, pickaxe, axe, shovel,
                new ItemStack(Material.GOLDEN_APPLE, 64),
                new ItemStack(Material.GOLDEN_APPLE, 64),
                new ItemStack(Material.EXPERIENCE_BOTTLE, 64),
                new ItemStack(Material.EXPERIENCE_BOTTLE, 64),
                new ItemStack(Material.ENDER_PEARL, 16),
                new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 8)
        );

        player.sendTitle(SmallCaps.convert("§b§l✧ Kit Estelar ✧"), SmallCaps.convert("§f¡El cosmos te protege!"), 10, 60, 20);
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§b§l✧ §f§lKit Estelar §b§l✧ §7— Set recibido."));
        player.sendMessage(SmallCaps.convert("§b  ▸ Eco §8(1p)§7: 3er golpe seguido al mismo objetivo = +1♥ y knockback."));
        player.sendMessage(SmallCaps.convert("§b  ▸ Refracción §8(2p)§7: Devuelve un golpe en 0.6s = +30% daño (parry)."));
        player.sendMessage(SmallCaps.convert("§b  ▸ Resonancia §8(3p)§7: Al 30% HP: ralentiza enemigos + Speed I. CD 45s."));
        player.sendMessage(SmallCaps.convert("§b  ▸ Constelación §8(4p)§7: Quieto 2s = Resistencia I. Moverte la quita."));
        player.sendMessage(SmallCaps.convert("§b  ▸ Corte Estelar §8(Espada)§7: Shift+ClickDcho dispara proyectil. CD 12s."));
        player.sendMessage("");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        if (player.isOp()) player.sendMessage(SmallCaps.convert("§8[OP] Sin cooldown."));
    }

    private ItemStack createTool(Material mat, String name, Object[] enchants, Player player) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        for (int i = 0; i < enchants.length; i += 2)
            meta.addEnchant((Enchantment) enchants[i], (int) enchants[i + 1], true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(getYTKey(plugin), PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(getYTOwnerKey(plugin), PersistentDataType.STRING, player.getUniqueId().toString());
        item.setItemMeta(meta);
        return item;
    }

    private static List<String> getSetLore() {
        return Arrays.asList(
                "§8§m                              ",
                "§b§l✧ Set Estelar ✧",
                "§8§m                              ",
                "§b▸ Eco §8(1 pieza)",
                "§7  3er golpe seguido al mismo objetivo:",
                "§7  §c+1♥ §7extra + mini-knockback.",
                "",
                "§b▸ Refracción §8(2 piezas)",
                "§7  Devuelve un hit en §f0.6s §7tras recibir",
                "§7  uno: ese golpe hace §c+30% §7daño.",
                "",
                "§b▸ Resonancia Astral §8(3 piezas)",
                "§7  Al bajar de §c30% HP§7: pulso que aplica",
                "§7  §9Slowness I §7a enemigos + §bSpeed I §7a ti. CD §e45s§7.",
                "",
                "§b▸ Constelación §8(4 piezas)",
                "§7  Quieto §f2s§7: ganas §aResistencia I§7.",
                "§7  Al moverte se desactiva."
        );
    }

    private void applyTrim(ItemStack item, TrimPattern pattern, TrimMaterial material) {
        if (item.getItemMeta() instanceof ArmorMeta) {
            ArmorMeta meta = (ArmorMeta) item.getItemMeta();
            meta.setTrim(new ArmorTrim(material, pattern));
            meta.addItemFlags(ItemFlag.HIDE_ARMOR_TRIM);
            item.setItemMeta(meta);
        }
    }

    // === Cooldown persistence (shared file with KitDemon) ===
    // === One-time claim persistence ===

    private boolean hasClaimed(UUID uid) {
        if (!claimedFile.exists()) return false;
        org.bukkit.configuration.file.YamlConfiguration yaml =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(claimedFile);
        String path = "claimed." + uid.toString();
        return yaml.getBoolean(path, false);
    }

    private void markClaimed(UUID uid) {
        org.bukkit.configuration.file.YamlConfiguration yaml =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(claimedFile);
        yaml.set("claimed." + uid.toString(), true);
        try { yaml.save(claimedFile); } catch (IOException ignored) {}
    }

    private Long getCooldown(UUID uid) {
        if (!cooldownFile.exists()) return null;
        org.bukkit.configuration.file.YamlConfiguration yaml =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(cooldownFile);
        String path = "ytsecret." + uid.toString();
        return yaml.contains(path) ? yaml.getLong(path) : null;
    }

    private void setCooldown(UUID uid, long time) {
        org.bukkit.configuration.file.YamlConfiguration yaml =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(cooldownFile);
        yaml.set("ytsecret." + uid.toString(), time);
        try { yaml.save(cooldownFile); } catch (IOException ignored) {}
    }
}
