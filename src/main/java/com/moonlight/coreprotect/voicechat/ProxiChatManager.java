package com.moonlight.coreprotect.voicechat;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Manages the ProxiChat Node.js server process and sends player positions.
 */
public class ProxiChatManager {

    private final CoreProtectPlugin plugin;
    private Process nodeProcess;
    private Thread outputThread;
    private Thread errorThread;
    private BukkitTask positionTask;

    private static final String POSITIONS_URL = "http://172.17.0.1:39147/api/positions";
    private static final String API_SECRET = "fabc8f74d4db03a3ceba964f79b1c3a0ee14a11004191ed69c87be552e23382920c3fbf56a1458bcf750134b7464f2ba";

    public ProxiChatManager(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        // Check if ProxiChat server is already running externally (systemd service)
        try {
            java.net.URL url = new java.net.URL("http://172.17.0.1:39147/");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.getResponseCode();
            conn.disconnect();
            plugin.getLogger().info("[ProxiChat] Servidor web detectado en puerto 39147 ✔");
        } catch (Exception ignored) {
            plugin.getLogger().info("[ProxiChat] Servidor web no detectado. Intentando iniciar Node.js...");
            startNodeProcess();
        }

        // Start sending player positions every 20 ticks (1 second)
        startPositionBroadcast();
    }

    private void startNodeProcess() {
        File proxiDir = new File(plugin.getDataFolder().getParentFile(), "ProximityVoiceChat");

        if (!proxiDir.exists() || !new File(proxiDir, "server.js").exists()) {
            plugin.getLogger().warning("[ProxiChat] No se encontró ProximityVoiceChat/server.js — Ejecuta el servidor Node.js manualmente.");
            return;
        }

        if (!new File(proxiDir, "node_modules").exists()) {
            plugin.getLogger().warning("[ProxiChat] Falta node_modules. Ejecuta 'npm install' en " + proxiDir.getAbsolutePath());
            return;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("node", "server.js");
            pb.directory(proxiDir);
            pb.redirectErrorStream(false);

            nodeProcess = pb.start();

            final Process proc = nodeProcess;
            outputThread = new Thread(() -> {
                try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        plugin.getLogger().info("[ProxiChat] " + line);
                    }
                } catch (IOException ignored) {}
            }, "ProxiChat-stdout");
            outputThread.setDaemon(true);
            outputThread.start();

            errorThread = new Thread(() -> {
                try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(proc.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("DEP0060")) continue;
                        plugin.getLogger().warning("[ProxiChat] " + line);
                    }
                } catch (IOException ignored) {}
            }, "ProxiChat-stderr");
            errorThread.setDaemon(true);
            errorThread.start();

            plugin.getLogger().info("[ProxiChat] Servidor web iniciado en puerto 39147");
        } catch (IOException e) {
            plugin.getLogger().warning("[ProxiChat] No se pudo iniciar Node.js. Usa el servicio systemd externo.");
        }
    }

    private void startPositionBroadcast() {
        positionTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                StringBuilder sb = new StringBuilder();
                sb.append("{\"secret\":\"").append(API_SECRET).append("\",\"players\":[");

                boolean first = true;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    Location loc = player.getLocation();
                    if (!first) sb.append(",");
                    first = false;
                    sb.append("{\"name\":\"").append(player.getName()).append("\"")
                      .append(",\"x\":").append(Math.round(loc.getX()))
                      .append(",\"y\":").append(Math.round(loc.getY()))
                      .append(",\"z\":").append(Math.round(loc.getZ()))
                      .append(",\"world\":\"").append(loc.getWorld().getName()).append("\"}");
                }
                sb.append("]}");

                java.net.URL url = new java.net.URL(POSITIONS_URL);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(2000);

                try (java.io.OutputStream os = conn.getOutputStream()) {
                    os.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                }
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception ignored) {
                // Server might not be running yet, silently ignore
            }
        }, 20L, 20L); // Every 1 second
    }

    public void stop() {
        if (positionTask != null) {
            positionTask.cancel();
            positionTask = null;
        }

        if (nodeProcess != null && nodeProcess.isAlive()) {
            nodeProcess.destroy();
            try {
                if (!nodeProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    nodeProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                nodeProcess.destroyForcibly();
            }
            plugin.getLogger().info("[ProxiChat] Servidor web detenido.");
        }
        nodeProcess = null;
    }

    public boolean isRunning() {
        return nodeProcess != null && nodeProcess.isAlive();
    }
}
