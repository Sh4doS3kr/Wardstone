package com.moonlight.coreprotect.shadowhunter;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Item legendario "Último Suspiro" — recompensa de la Misión 5.
 * - Lluvia Celestial: pasiva al disparar → 5 flechas caen del cielo. CD 12s
 * - Paso del Viento: Shift + Disparar → dash 10 bloques + invulnerabilidad breve. CD 18s
 * - Raíces del Cielo: pasiva → Regen I + Velocidad I + pétalos mientras sostienes
 */
public class VoidBow {

    public static final String VOID_BOW_KEY = "void_bow";

    public static NamespacedKey getKey(CoreProtectPlugin plugin) {
        return new NamespacedKey(plugin, VOID_BOW_KEY);
    }

    public static void giveVoidBow(Player player, CoreProtectPlugin plugin) {
        ItemStack bow = createVoidBow(plugin);
        World world = player.getWorld();
        Location loc = player.getLocation();

        player.sendTitle( SmallCaps.convert("§d§l✦ Último Suspiro ✦"), SmallCaps.convert("§7Lo que el Errante nunca pudo decir..."), 10, 80, 20);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 80) {
                    cancel();
                    if (player.getInventory().firstEmpty() != -1) {
                        player.getInventory().addItem(bow);
                    } else {
                        player.getWorld().dropItemNaturally(player.getLocation(), bow);
                    }
                    world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 2, 0), 80, 1.5, 1.5, 1.5, 0.5);
                    world.spawnParticle(Particle.DUST, loc.clone().add(0, 1.5, 0), 60, 1, 1, 1, 0,
                            new Particle.DustOptions(Color.fromRGB(255, 180, 220), 2.5f));
                    world.spawnParticle(Particle.CHERRY_LEAVES, loc.clone().add(0, 2, 0), 40, 1.5, 1.5, 1.5, 0.1);
                    world.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                    world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.9f);
                    world.playSound(loc, Sound.ENTITY_ALLAY_DEATH, 0.8f, 0.6f);

                    player.sendMessage("");
                    player.sendMessage(SmallCaps.convert("§d§l✦ §b§lHas obtenido: §d§lÚltimo Suspiro §d§l✦"));
                    player.sendMessage(SmallCaps.convert("§7  Pasiva al disparar: §dLluvia Celestial §8(CD 12s)"));
                    player.sendMessage(SmallCaps.convert("§7  Shift + Disparar: §bPaso del Viento §8(CD 18s)"));
                    player.sendMessage(SmallCaps.convert("§7  Pasiva al sostener: §aRegeneración I §7+ §bVelocidad I §7+ pétalos"));
                    player.sendMessage("");
                    return;
                }

                double a1 = ticks * 0.22;
                double a2 = a1 + Math.PI;
                double radius = 2.0 - (ticks / 80.0) * 1.5;
                double y = ticks * 0.04 + 1;

                world.spawnParticle(Particle.DUST,
                        loc.clone().add(Math.cos(a1) * radius, y, Math.sin(a1) * radius),
                        2, 0.05, 0.05, 0.05, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 150, 200), 1.8f));
                world.spawnParticle(Particle.DUST,
                        loc.clone().add(Math.cos(a2) * radius, y, Math.sin(a2) * radius),
                        2, 0.05, 0.05, 0.05, 0,
                        new Particle.DustOptions(Color.fromRGB(200, 255, 220), 1.5f));
                world.spawnParticle(Particle.CHERRY_LEAVES,
                        loc.clone().add(Math.cos(a1 + 1) * radius, y, Math.sin(a1 + 1) * radius),
                        2, 0.1, 0.1, 0.1, 0.02);

                if (ticks % 8 == 0) {
                    world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.3f, 1.5f + ticks * 0.015f);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    public static ItemStack createVoidBow(CoreProtectPlugin plugin) {
        ItemStack bow = new ItemStack(Material.BOW);
        ItemMeta meta = bow.getItemMeta();

        meta.setDisplayName(SmallCaps.convert("§d§l§k|§r §d§lÚltimo Suspiro §d§l§k|"));
        meta.setLore(Arrays.asList(
                "§8§m                              ",
                "§7\"Construí un jardín en el cielo porque\"",
                "§7\"ya no me quedaba sitio en la tierra.\"",
                "§7§o— Carta del Errante",
                "§8§m                              ",
                "",
                "§d§l❀ Habilidades:",
                "",
                "§d▸ Lluvia Celestial §7(Pasiva al disparar)",
                "§7  Cada disparo invoca §b5 flechas §7del cielo",
                "§7  que caen como lluvia sobre el objetivo. CD: §e12s",
                "",
                "§b▸ Paso del Viento §7(Shift + Disparar)",
                "§7  Dash de §b10 bloques §7dejando un rastro de",
                "§7  pétalos. §eInvulnerable §7durante el dash. CD: §e18s",
                "",
                "§a▸ Raíces del Cielo §7(Pasiva al sostener)",
                "§7  §aRegeneración I §7+ §bVelocidad I §7mientras",
                "§7  sostienes el arco. Pétalos de cerezo brotan.",
                "",
                "§8§m                              ",
                "§d§oLo que florece en el cielo no se marchita."));

        meta.addEnchant(Enchantment.POWER, 8, true);
        meta.addEnchant(Enchantment.INFINITY, 1, true);
        meta.addEnchant(Enchantment.PUNCH, 3, true);
        meta.addEnchant(Enchantment.FLAME, 1, true);
        meta.addEnchant(Enchantment.UNBREAKING, 10, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        meta.getPersistentDataContainer().set(getKey(plugin), PersistentDataType.BYTE, (byte) 1);
        bow.setItemMeta(meta);
        return bow;
    }

    public static boolean isVoidBow(ItemStack item, CoreProtectPlugin plugin) {
        if (item == null || item.getType() != Material.BOW) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(getKey(plugin), PersistentDataType.BYTE);
    }
}
