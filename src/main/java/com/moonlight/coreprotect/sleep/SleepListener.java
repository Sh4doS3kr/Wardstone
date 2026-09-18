package com.moonlight.coreprotect.sleep;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Sistema de sueño: solo el 20% de los jugadores online necesitan dormir para que amanezca.
 * Mínimo 1 jugador. Muestra barra de progreso en el chat.
 */
public class SleepListener implements Listener {

    private final JavaPlugin plugin;
    private final Set<UUID> sleeping = new HashSet<>();

    public SleepListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBedEnter(PlayerBedEnterEvent event) {
        if (event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) return;

        Player player = event.getPlayer();
        World world = player.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) return;

        // Añadir al jugador al set de durmientes 1 tick después (Bukkit actualiza isSleeping con delay)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            sleeping.add(player.getUniqueId());
            checkSleep(world);
        }, 2L);
    }

    @EventHandler
    public void onBedLeave(PlayerBedLeaveEvent event) {
        sleeping.remove(event.getPlayer().getUniqueId());
    }

    private void checkSleep(World world) {
        // Contar jugadores online en el overworld (excluir vanish/spectator no cuenta si quieres, aquí contamos todos)
        long onlineInWorld = world.getPlayers().stream()
                .filter(p -> !p.isSleepingIgnored())
                .count();

        if (onlineInWorld == 0) return;

        // Jugadores durmiendo en este mundo
        long sleepingInWorld = world.getPlayers().stream()
                .filter(p -> sleeping.contains(p.getUniqueId()) && p.isSleeping())
                .count();

        // Mínimo necesario: 20% del total, redondeado arriba, mínimo 1
        long needed = Math.max(1, (long) Math.ceil(onlineInWorld * 0.20));

        // Barra de progreso visual
        broadcastSleepBar(world, sleepingInWorld, needed);

        if (sleepingInWorld >= needed) {
            // ¡Amanece!
            world.setTime(0);
            world.setStorm(false);
            world.setThundering(false);
            sleeping.clear();

            for (Player p : world.getPlayers()) {
                p.sendMessage(SmallCaps.convert("§e☀ §fHa amanecido gracias a los jugadores que durmieron."));
                p.playSound(p.getLocation(), org.bukkit.Sound.AMBIENT_CAVE, 0.3f, 2.0f);
            }
        }
    }

    private void broadcastSleepBar(World world, long current, long needed) {
        int barLength = 20;
        long filled = (needed > 0) ? Math.min(barLength, (long)(barLength * current / (double) needed)) : barLength;

        StringBuilder bar = new StringBuilder("§8[");
        for (int i = 0; i < barLength; i++) {
            bar.append(i < filled ? "§a█" : "§7░");
        }
        bar.append("§8] §f").append(current).append("§7/").append(needed);

        String msg = "§e☀ §fDurmiendo: " + bar + " §7(necesario 20%)";
        for (Player p : world.getPlayers()) {
            p.sendMessage(msg);
        }
    }
}
