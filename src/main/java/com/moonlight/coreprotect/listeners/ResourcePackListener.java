package com.moonlight.coreprotect.listeners;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Envía el resource pack del servidor al jugador al unirse.
 * Lee la URL y SHA1 desde plugins/Wardstone/resourcepack.yml
 * y vigila cambios en el archivo para aplicarlos al instante.
 */
public class ResourcePackListener implements Listener {

    private String packUrl = "";
    private String packSha1 = "";

    private static final String PROMPT_MESSAGE =
            "§6§l⚠ ¡RESOURCE PACK RECOMENDADO! ⚠\n\n" +
            "§fEste servidor usa §bmobs custom§f, §bmodelos 3D§f y §btexturas exclusivas§f.\n" +
            "§eSe recomienda §c§l§nENCARECIDAMENTE§r §eaceptar el resource pack\n" +
            "§epara disfrutar de la experiencia completa.\n\n" +
            "§7Tamaño: ~pequeño | Se descarga una sola vez.";

    private final CoreProtectPlugin plugin;
    private final File configFile;
    private long lastModified = 0;

    // ── Notification queue ──────────────────────────────────────────────────
    // Players whose resource pack status is already resolved (loaded/declined/failed)
    private static final Set<UUID> packResolved = new HashSet<>();
    // Notifications waiting for pack resolution, per player
    private static final Map<UUID, List<Runnable>> pendingNotifs = new HashMap<>();

    /**
     * Queue a notification for the player. If the resource pack is already
     * resolved for this player the notification fires immediately; otherwise
     * it waits until the pack loads or is declined.
     */
    public static void queueOrFire(CoreProtectPlugin plugin, Player player, Runnable notification) {
        UUID uuid = player.getUniqueId();
        if (packResolved.contains(uuid)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) notification.run();
            });
        } else {
            pendingNotifs.computeIfAbsent(uuid, k -> new ArrayList<>()).add(notification);
        }
    }

    /** Fire all queued notifications for a player sequentially (6s apart). */
    private void firePending(Player player) {
        UUID uuid = player.getUniqueId();
        packResolved.add(uuid);
        List<Runnable> pending = pendingNotifs.remove(uuid);
        if (pending == null || pending.isEmpty()) return;
        for (int i = 0; i < pending.size(); i++) {
            final Runnable r = pending.get(i);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) r.run();
            }, i * 120L); // 6s gap (title=5s + 1s buffer)
        }
    }
    // ────────────────────────────────────────────────────────────────────────

    public ResourcePackListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "resourcepack.yml");
        loadConfig();
        startFileWatcher();
    }

    /**
     * Carga (o crea) resourcepack.yml con la URL y SHA1 del resource pack.
     */
    private void loadConfig() {
        if (!configFile.exists()) {
            // Crear archivo por defecto
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("resource-pack-url", "https://download.mc-packs.net/pack/4b91a9d11bc335c99072249443b1b986851c4ec8.zip");
            yaml.set("resource-pack-sha1", "4b91a9d11bc335c99072249443b1b986851c4ec8");
            yaml.options().header(
                    "Configuración del Resource Pack del servidor.\n" +
                    "Cambia la URL y SHA1 aquí y se aplicará al instante sin reiniciar.\n" +
                    "Obtén tu pack en https://mc-packs.net/");
            try {
                plugin.getDataFolder().mkdirs();
                yaml.save(configFile);
            } catch (IOException e) {
                plugin.getLogger().warning("[ResourcePack] Error creando resourcepack.yml: " + e.getMessage());
            }
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
        packUrl = yaml.getString("resource-pack-url", "");
        packSha1 = yaml.getString("resource-pack-sha1", "");
        lastModified = configFile.lastModified();

        if (packUrl.isEmpty()) {
            plugin.getLogger().warning("[ResourcePack] URL vacía en resourcepack.yml — no se enviará pack.");
        } else {
            plugin.getLogger().info("[ResourcePack] Pack cargado: " + packUrl.substring(0, Math.min(60, packUrl.length())) + "...");
        }
    }

    /**
     * Vigila resourcepack.yml cada 5 segundos.
     * Si detecta un cambio, recarga automáticamente sin comandos.
     */
    private void startFileWatcher() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!configFile.exists()) return;
            long mod = configFile.lastModified();
            if (mod != lastModified) {
                lastModified = mod;
                // Recargar en el hilo principal
                Bukkit.getScheduler().runTask(plugin, () -> {
                    loadConfig();
                    plugin.getLogger().info("[ResourcePack] resourcepack.yml recargado automáticamente.");
                });
            }
        }, 100L, 100L); // cada 5 segundos
    }

    // UUID fijo del pack — permite reemplazarlo/remover por ID si cambia en el futuro
    private static final java.util.UUID PACK_UUID =
            java.util.UUID.fromString("8eafca1a-c887-726a-f387-4e3f2c893215");

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        packResolved.remove(uuid);

        if (packUrl.isEmpty()) {
            // No pack configured — resolve immediately so notifications fire
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) firePending(player);
            }, 60L);
            return;
        }

        // Delay 5s para asegurar que el cliente haya terminado de cargar
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                if (!player.isOnline()) return;
                sendPack(player);
            } catch (Throwable t) {
                plugin.getLogger().warning("[ResourcePack] Error enviando pack a " + player.getName() + ": " + t.getMessage());
            }
        }, 100L); // 5 segundos

        // Fallback: if pack status never fires within 20s, fire pending anyway
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !packResolved.contains(uuid)) {
                firePending(player);
            }
        }, 400L); // 20s
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        packResolved.remove(uuid);
        pendingNotifs.remove(uuid);
    }

    /**
     * Envía el resource pack. Todo envuelto en try-catch para que
     * BAJO NINGÚN CONCEPTO desconecte al jugador.
     */
    private void sendPack(Player player) {
        try {
            // API nueva (1.20.3+) — force=false para no expulsar jamás
            java.lang.reflect.Method m = player.getClass().getMethod("setResourcePack",
                    java.util.UUID.class, String.class, String.class, String.class, boolean.class);
            m.invoke(player, PACK_UUID, packUrl, packSha1, PROMPT_MESSAGE, false);
            return;
        } catch (NoSuchMethodException ignored) {
        } catch (Throwable t) {
            plugin.getLogger().warning("[ResourcePack] Error API nueva: " + t.getMessage());
        }

        try {
            byte[] hashBytes = hexStringToByteArray(packSha1);
            player.setResourcePack(packUrl, hashBytes, PROMPT_MESSAGE, false);
            return;
        } catch (Throwable ignored) {
        }

        try {
            byte[] hashBytes = hexStringToByteArray(packSha1);
            player.setResourcePack(packUrl, hashBytes);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Maneja la respuesta del resource pack.
     * NUNCA expulsa al jugador bajo ningún concepto.
     */
    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        try {
            Player player = event.getPlayer();
            if (player == null || !player.isOnline()) return;

            switch (event.getStatus()) {
                case ACCEPTED:
                    player.sendMessage("§a§l✔ §7Descargando resource pack...");
                    player.sendTitle("§a§lCargando...", "§7El pack de texturas se cargará pronto", 10, 70, 20);
                    break;
                case SUCCESSFULLY_LOADED:
                    player.sendMessage("§a§l✔ §a¡Resource pack cargado! §7Disfruta de los mobs custom.");
                    firePending(player);
                    break;
                case DECLINED:
                    player.sendMessage("§e§l⚠ §eResource pack rechazado. §7Activa §fResource Packs §7en opciones del servidor para ver mobs custom.");
                    firePending(player);
                    break;
                case FAILED_DOWNLOAD:
                    player.sendMessage("§c§l✖ §cError descargando el resource pack. No pasa nada, puedes seguir jugando.");
                    firePending(player);
                    break;
            }
        } catch (Throwable t) {
            // Silenciar absolutamente todo — jamás desconectar
            plugin.getLogger().warning("[ResourcePack] Error en status handler: " + t.getMessage());
        }
    }

    private static byte[] hexStringToByteArray(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
