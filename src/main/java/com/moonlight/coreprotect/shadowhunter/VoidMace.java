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
 * Item legendario "Martillo del Cero Absoluto" — recompensa de la Misión 8: El Heraldo del Hielo Eterno.
 * Habilidades:
 * - Congelación Absoluta (Click derecho): congela al rival durante 1.25s (inmóvil, partículas de hielo). CD: 12s
 * - Impacto Glacial (Shift + Click derecho): AOE 6 bloques, 10 daño + Slowness IV 3s + nieve en el suelo. CD: 25s
 * - Aliento Gélido (Pasiva): cada golpe aplica Slowness I 1.5s y partículas de escarcha
 * - Voluntad de Hielo (Pasiva): inmunidad al efecto de Freeze (polvo de nieve)
 */
public class VoidMace {

    public static final String VOID_MACE_KEY = "void_mace";

    public static NamespacedKey getKey(CoreProtectPlugin plugin) {
        return new NamespacedKey(plugin, VOID_MACE_KEY);
    }

    public static void giveVoidMace(Player player, CoreProtectPlugin plugin) {
        ItemStack mace = createVoidMace(plugin);

        World world = player.getWorld();
        Location loc = player.getLocation();

        player.sendTitle(
                SmallCaps.convert("§b§l❄ Martillo del Cero Absoluto ❄"),
                SmallCaps.convert("§3El frío eterno ahora te obedece..."),
                10, 100, 30);

        new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 100) {
                    cancel();

                    if (player.getInventory().firstEmpty() != -1) {
                        player.getInventory().addItem(mace);
                    } else {
                        player.getWorld().dropItemNaturally(player.getLocation(), mace);
                    }

                    // Efecto final: explosión de hielo
                    world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 2, 0), 150, 2, 2, 2, 0.8);
                    world.spawnParticle(Particle.SNOWFLAKE, loc.clone().add(0, 1, 0), 200, 3, 3, 3, 0.1);
                    world.spawnParticle(Particle.DUST, loc.clone().add(0, 1.5, 0), 100, 2, 2, 2, 0,
                            new Particle.DustOptions(Color.fromRGB(180, 220, 255), 3.0f));
                    world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 3, 0), 80, 2, 3, 2, 0.2);

                    world.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.7f);
                    world.playSound(loc, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f);
                    world.playSound(loc, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.6f, 1.5f);
                    world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.2f);

                    player.sendMessage("");
                    player.sendMessage(SmallCaps.convert("§b§l❄ §3§lHas obtenido: §b§lMartillo del Cero Absoluto §b§l❄"));
                    player.sendMessage(SmallCaps.convert("§7  §b▸ Congelación Absoluta §7(Click derecho)"));
                    player.sendMessage(SmallCaps.convert("§7    Congela al enemigo más cercano §b1.25s§7."));
                    player.sendMessage(SmallCaps.convert("§7  §3▸ Impacto Glacial §7(Shift + Click der.)"));
                    player.sendMessage(SmallCaps.convert("§7    AOE glacial: §c10 daño §7+ Slowness IV."));
                    player.sendMessage(SmallCaps.convert("§7  §f▸ Aliento Gélido §7(Pasiva)"));
                    player.sendMessage(SmallCaps.convert("§7    Cada golpe aplica §bSlowness I §71.5s."));
                    player.sendMessage(SmallCaps.convert("§7  §3▸ Voluntad de Hielo §7(Pasiva)"));
                    player.sendMessage(SmallCaps.convert("§7    §aInmune §7al efecto de congelación."));
                    player.sendMessage("");
                    player.sendMessage(SmallCaps.convert("§3§o\"El hielo no perdona. El hielo no olvida.\""));
                    player.sendMessage("");
                    return;
                }

                // Animación: tormenta de hielo ascendente
                double progress = ticks / 100.0;
                double angle1 = ticks * 0.25;
                double angle2 = angle1 + Math.PI;
                double angle3 = angle1 + Math.PI * 0.667;
                double radius = 3.5 - progress * 3.0;
                double y = ticks * 0.025;

                // Espiral de hielo azul
                world.spawnParticle(Particle.DUST,
                        loc.clone().add(Math.cos(angle1) * radius, y, Math.sin(angle1) * radius),
                        3, 0.05, 0.05, 0.05, 0,
                        new Particle.DustOptions(Color.fromRGB(100, 180, 255), 2.2f));

                // Espiral blanca (escarcha)
                world.spawnParticle(Particle.DUST,
                        loc.clone().add(Math.cos(angle2) * radius, y, Math.sin(angle2) * radius),
                        3, 0.05, 0.05, 0.05, 0,
                        new Particle.DustOptions(Color.fromRGB(220, 240, 255), 1.8f));

                // Espiral cian
                world.spawnParticle(Particle.DUST,
                        loc.clone().add(Math.cos(angle3) * radius * 0.7, y * 0.9, Math.sin(angle3) * radius * 0.7),
                        2, 0.05, 0.05, 0.05, 0,
                        new Particle.DustOptions(Color.fromRGB(50, 200, 230), 1.5f));

                // Copos de nieve
                if (ticks % 3 == 0) {
                    world.spawnParticle(Particle.SNOWFLAKE,
                            loc.clone().add((Math.random() - 0.5) * 4, y * 0.5 + 1, (Math.random() - 0.5) * 4),
                            2, 0.2, 0.5, 0.2, 0.02);
                }

                // Sonido
                if (ticks % 15 == 0) {
                    world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.5f + (float) progress * 0.5f);
                }
                if (ticks % 25 == 0) {
                    world.playSound(loc, Sound.BLOCK_POWDER_SNOW_STEP, 0.6f, 0.5f);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    public static ItemStack createVoidMace(CoreProtectPlugin plugin) {
        ItemStack mace = new ItemStack(Material.MACE);
        ItemMeta meta = mace.getItemMeta();

        meta.setDisplayName(SmallCaps.convert("§b§l§k||§r §b§lMartillo del Cero Absoluto §b§l§k||"));

        meta.setLore(Arrays.asList(
                "§8§m                              ",
                "§3\"Forjado en el corazón de una estrella muerta,",
                "§3 donde el frío es tan absoluto que el tiempo",
                "§3 deja de existir.\"",
                "§8§m                              ",
                "",
                "§b§l❄ Habilidades:",
                "",
                "§b▸ Congelación Absoluta §7(Click derecho)",
                "§7  Congela al enemigo más cercano",
                "§7  durante §b1.25s§7. No puede moverse,",
                "§7  atacar ni usar items.",
                "§7  Cooldown: §e12s",
                "",
                "§3▸ Impacto Glacial §7(Shift + Click der.)",
                "§7  Onda de frío en §b6 bloques§7.",
                "§7  §c10 daño §7+ §bSlowness IV §73s.",
                "§7  Congela el suelo con nieve.",
                "§7  Cooldown: §e25s",
                "",
                "§f▸ Aliento Gélido §7(Pasiva)",
                "§7  Cada golpe aplica §bSlowness I",
                "§7  §7durante §e1.5s§7 + partículas de hielo.",
                "",
                "§3▸ Voluntad de Hielo §7(Pasiva)",
                "§7  §aInmunidad total §7al efecto de",
                "§7  §bcongelación §7(polvo de nieve).",
                "",
                "§8§m                              ",
                "§b§lSet del Vacío §7(8/8)",
                "§8▸ §5Hoja del Vacío §7(Espada)",
                "§8▸ §4Devoradora de Almas §7(Hacha)",
                "§8▸ §0Fractura del Vacío §7(Pico)",
                "§8▸ §5Elegía del Errante §7(Peto)",
                "§8▸ §3Último Suspiro §7(Arco)",
                "§8▸ §bLágrimas de Lyra §7(Pantalones)",
                "§8▸ §4Pisada del Olvido §7(Botas)",
                "§8▸ §bMartillo del Cero Absoluto §7(Mazo) §a✔",
                "§8§m                              ",
                "§3§o\"El hielo no perdona. El hielo no olvida.\""));

        meta.addEnchant(Enchantment.DENSITY, 7, true);
        meta.addEnchant(Enchantment.UNBREAKING, 10, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        meta.getPersistentDataContainer().set(getKey(plugin), PersistentDataType.BYTE, (byte) 1);

        meta.addAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE,
                new AttributeModifier(UUID.randomUUID(), "void_mace_damage", 12,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND));
        meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED,
                new AttributeModifier(UUID.randomUUID(), "void_mace_speed", -2.8,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND));

        mace.setItemMeta(meta);
        return mace;
    }

    public static boolean isVoidMace(ItemStack item, CoreProtectPlugin plugin) {
        if (item == null || item.getType() != Material.MACE) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(getKey(plugin), PersistentDataType.BYTE);
    }
}
