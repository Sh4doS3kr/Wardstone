package com.moonlight.coreprotect.shadowhunter;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.UUID;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Item legendario "Fractura del Vacío" — recompensa de la Misión 3.
 * Pico de Netherite con habilidades:
 * - Veta Sombría (Click derecho): mina 3x3. CD: 15s
 * - Ojo del Vacío (Shift+Click): destaca minerales 10 bloques. CD: 30s
 * - Resonancia (Pasiva): 5% doble drops al minar.
 */
public class VoidPickaxe {

    public static final String VOID_PICKAXE_KEY = "void_pickaxe";

    public static NamespacedKey getKey(CoreProtectPlugin plugin) {
        return new NamespacedKey(plugin, VOID_PICKAXE_KEY);
    }

    public static void giveVoidPickaxe(Player player, CoreProtectPlugin plugin) {
        ItemStack pick = createVoidPickaxe(plugin);

        World world = player.getWorld();
        Location loc = player.getLocation();

        player.sendTitle( SmallCaps.convert("§0§l✦ Fractura del Vacío ✦"), SmallCaps.convert("§7El vacío se moldea a tu voluntad..."), 10, 80, 20);

        new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 80) {
                    cancel();
                    if (player.getInventory().firstEmpty() != -1) {
                        player.getInventory().addItem(pick);
                    } else {
                        player.getWorld().dropItemNaturally(player.getLocation(), pick);
                    }

                    world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 2, 0), 120, 1.5, 1.5, 1.5, 0.8);
                    world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 2, 0), 80, 1, 1, 1, 0.5);
                    world.spawnParticle(Particle.DUST, loc.clone().add(0, 1.5, 0), 60, 1, 1, 1, 0,
                            new Particle.DustOptions(Color.fromRGB(0, 0, 0), 3.0f));
                    world.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                    world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.8f);
                    world.playSound(loc, Sound.ENTITY_WARDEN_EMERGE, 0.5f, 1.5f);

                    player.sendMessage("");
                    player.sendMessage(SmallCaps.convert("§0§l✦ §8§lHas obtenido: §0§lFractura del Vacío §0§l✦"));
                    player.sendMessage(SmallCaps.convert("§7  Click derecho: §8Veta Sombría §7(mina 3x3)"));
                    player.sendMessage(SmallCaps.convert("§7  Shift + Click: §bOjo del Vacío §7(detecta minerales)"));
                    player.sendMessage(SmallCaps.convert("§7  Pasiva: §5Resonancia §7(5% doble drops)"));
                    player.sendMessage("");
                    return;
                }

                // Triple espiral ascendente negra/gris/morada
                double angle1 = ticks * 0.2;
                double angle2 = angle1 + Math.PI * 2.0 / 3.0;
                double angle3 = angle1 + Math.PI * 4.0 / 3.0;
                double radius = 2.0 - (ticks / 80.0) * 1.5;
                double y = ticks * 0.04 + 1;

                world.spawnParticle(Particle.DUST,
                        loc.clone().add(Math.cos(angle1) * radius, y, Math.sin(angle1) * radius),
                        3, 0.05, 0.05, 0.05, 0,
                        new Particle.DustOptions(Color.fromRGB(10, 10, 10), 2.0f));
                world.spawnParticle(Particle.DUST,
                        loc.clone().add(Math.cos(angle2) * radius, y, Math.sin(angle2) * radius),
                        3, 0.05, 0.05, 0.05, 0,
                        new Particle.DustOptions(Color.fromRGB(80, 80, 100), 1.5f));
                world.spawnParticle(Particle.DUST,
                        loc.clone().add(Math.cos(angle3) * radius, y, Math.sin(angle3) * radius),
                        3, 0.05, 0.05, 0.05, 0,
                        new Particle.DustOptions(Color.fromRGB(60, 0, 90), 1.5f));
                world.spawnParticle(Particle.ASH,
                        loc.clone().add(Math.cos(angle1) * radius, y, Math.sin(angle1) * radius),
                        2, 0, 0, 0, 0.01);

                if (ticks % 6 == 0) {
                    world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 0.3f + ticks * 0.02f);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    public static ItemStack createVoidPickaxe(CoreProtectPlugin plugin) {
        ItemStack pick = new ItemStack(Material.NETHERITE_PICKAXE);
        ItemMeta meta = pick.getItemMeta();

        meta.setDisplayName(SmallCaps.convert("§0§l§k||§r §8§lFractura del Vacío §0§l§k||"));

        meta.setLore(Arrays.asList(
                "§8§m                              ",
                "§7\"Moldea la realidad, fractura la piedra...\"",
                "§8§m                              ",
                "",
                "§8§l⚡ Habilidades:",
                "",
                "§8▸ Veta Sombría §7(Click derecho)",
                "§7  Mina instantáneamente un área",
                "§7  de §c5x5 §7bloques al frente.",
                "§7  Cooldown: §e8s",
                "",
                "§b▸ Ojo del Vacío §7(Shift + Click der.)",
                "§7  Destaca todos los minerales valiosos",
                "§7  en un radio de §c15 bloques §7durante 10s.",
                "§7  Cooldown: §e25s",
                "",
                "§5▸ Resonancia §7(Pasiva)",
                "§7  §a10% §7de chance de obtener el",
                "§7  §adoble de drops §7al minar.",
                "",
                "§f▸ Prisa del Vacío §7(Pasiva)",
                "§7  Otorga §ePrisa Minera II §7y §bVelocidad I",
                "§7  mientras lo sostienes en la mano.",
                "",
                "§8§m                              ",
                "§0§oEl vacío moldea todo..."));

        meta.addEnchant(Enchantment.EFFICIENCY, 6, true);
        meta.addEnchant(Enchantment.FORTUNE, 4, true);
        meta.addEnchant(Enchantment.UNBREAKING, 10, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        meta.getPersistentDataContainer().set(getKey(plugin), PersistentDataType.BYTE, (byte) 1);

        pick.setItemMeta(meta);
        return pick;
    }

    public static boolean isVoidPickaxe(ItemStack item, CoreProtectPlugin plugin) {
        if (item == null || item.getType() != Material.NETHERITE_PICKAXE)
            return false;
        if (!item.hasItemMeta())
            return false;
        return item.getItemMeta().getPersistentDataContainer().has(getKey(plugin), PersistentDataType.BYTE);
    }
}
