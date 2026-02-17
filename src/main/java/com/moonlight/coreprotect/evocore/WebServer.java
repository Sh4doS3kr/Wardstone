package com.moonlight.coreprotect.evocore;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.core.ProtectedRegion;
import com.moonlight.coreprotect.evocore.metrics.ItemMetrics;
import com.moonlight.coreprotect.evocore.metrics.MobMetrics;
import com.moonlight.coreprotect.evocore.metrics.ZoneMetrics;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class WebServer {

    private final CoreProtectPlugin plugin;
    private final EvoCore evoCore;
    private final int port;
    private HttpServer server;

    public WebServer(CoreProtectPlugin plugin, EvoCore evoCore, int port) {
        this.plugin = plugin;
        this.evoCore = evoCore;
        this.port = port;
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
            server.setExecutor(Executors.newFixedThreadPool(4));

            // API endpoints
            server.createContext("/api/zones", this::handleZones);
            server.createContext("/api/mobs", this::handleMobs);
            server.createContext("/api/items", this::handleItems);
            server.createContext("/api/economy", this::handleEconomy);
            server.createContext("/api/meta", this::handleMeta);
            server.createContext("/api/alerts", this::handleAlerts);
            server.createContext("/api/overview", this::handleOverview);

            // Dashboard
            server.createContext("/", this::handleDashboard);

            server.start();
            plugin.getLogger().info("[EvoCore] Web server iniciado en puerto " + port);
        } catch (IOException e) {
            plugin.getLogger().severe("[EvoCore] Error iniciando web server: " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            plugin.getLogger().info("[EvoCore] Web server detenido");
        }
    }

    // --- API Handlers ---

    private void handleOverview(HttpExchange exchange) throws IOException {
        if (!checkGet(exchange)) return;
        DataCollector dc = evoCore.getDataCollector();
        AnalyticsEngine ae = evoCore.getAnalyticsEngine();
        // AutoEventManager desactivado - sistema de oleadas eliminado
        // AutoEventManager aem = evoCore.getAutoEventManager();

        StringBuilder json = new StringBuilder("{");
        json.append("\"onlinePlayers\":").append(dc.getOnlinePlayers()).append(",");
        json.append("\"totalZones\":").append(plugin.getProtectionManager().getAllRegions().size()).append(",");
        json.append("\"totalMobKills\":").append(dc.getTotalMobKills()).append(",");
        json.append("\"totalBlocksBroken\":").append(dc.getTotalBlocksBroken()).append(",");
        json.append("\"totalBlocksPlaced\":").append(dc.getTotalBlocksPlaced()).append(",");
        json.append("\"activeInvasions\":0,"); // Siempre 0 - sistema desactivado
        json.append("\"activeTreasures\":0,"); // Siempre 0 - sistema desactivado
        json.append("\"alerts\":").append(listToJsonArray(ae.getAlerts()));
        json.append("}");
        sendJson(exchange, json.toString());
    }

    private void handleZones(HttpExchange exchange) throws IOException {
        if (!checkGet(exchange)) return;
        DataCollector dc = evoCore.getDataCollector();
        AnalyticsEngine ae = evoCore.getAnalyticsEngine();

        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (ProtectedRegion region : plugin.getProtectionManager().getAllRegions()) {
            ZoneMetrics zm = dc.getOrCreateZoneMetrics(region.getId());
            AnalyticsEngine.ZoneStatus status = ae.getZoneStatus(region.getId());
            String ownerName = Bukkit.getOfflinePlayer(region.getOwner()).getName();
            if (ownerName == null) ownerName = "Desconocido";

            if (!first) json.append(",");
            first = false;
            json.append("{");
            json.append("\"id\":\"").append(region.getId()).append("\",");
            json.append("\"owner\":\"").append(escapeJson(ownerName)).append("\",");
            json.append("\"level\":").append(region.getLevel()).append(",");
            json.append("\"size\":").append(region.getSize()).append(",");
            json.append("\"world\":\"").append(escapeJson(region.getWorldName())).append("\",");
            json.append("\"x\":").append(region.getCoreX()).append(",");
            json.append("\"z\":").append(region.getCoreZ()).append(",");
            json.append("\"status\":\"").append(status.name()).append("\",");
            json.append("\"killsHour\":").append(zm.getKillsLastHour()).append(",");
            json.append("\"killsDay\":").append(zm.getKillsLastDay()).append(",");
            json.append("\"players\":").append(zm.getPlayersInZone()).append(",");
            json.append("\"resources\":").append(zm.getResourcesCollected()).append(",");
            json.append("\"pressure\":").append(String.format("%.2f", zm.getPressureIndex())).append(",");
            json.append("\"spawnMod\":").append(String.format("%.2f", zm.getSpawnRateModifier())).append(",");
            json.append("\"lootMod\":").append(String.format("%.2f", zm.getLootMultiplier())).append(",");
            json.append("\"healthMod\":").append(String.format("%.2f", zm.getMobHealthModifier()));
            json.append("}");
        }
        json.append("]");
        sendJson(exchange, json.toString());
    }

    private void handleMobs(HttpExchange exchange) throws IOException {
        if (!checkGet(exchange)) return;
        DataCollector dc = evoCore.getDataCollector();
        AnalyticsEngine ae = evoCore.getAnalyticsEngine();
        Map<EntityType, String> statuses = ae.getMobStatuses();

        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (Map.Entry<EntityType, MobMetrics> entry : dc.getAllMobMetrics().entrySet()) {
            MobMetrics mm = entry.getValue();
            if (!first) json.append(",");
            first = false;
            json.append("{");
            json.append("\"type\":\"").append(entry.getKey().name()).append("\",");
            json.append("\"status\":\"").append(statuses.getOrDefault(entry.getKey(), "NORMAL")).append("\",");
            json.append("\"killsHour\":").append(mm.getKillsLastHour()).append(",");
            json.append("\"killsDay\":").append(mm.getKillsLastDay()).append(",");
            json.append("\"totalKills\":").append(mm.getTotalKills()).append(",");
            json.append("\"avgKillTime\":").append((int) mm.getAvgKillTimeMs()).append(",");
            json.append("\"avgPlayers\":").append(mm.getAvgPlayersInvolved()).append(",");
            json.append("\"spawnMod\":").append(String.format("%.2f", mm.getSpawnRateModifier())).append(",");
            json.append("\"healthMod\":").append(String.format("%.2f", mm.getHealthModifier())).append(",");
            json.append("\"damageMod\":").append(String.format("%.2f", mm.getDamageModifier())).append(",");
            json.append("\"lootMod\":").append(String.format("%.2f", mm.getLootQualityModifier()));
            json.append("}");
        }
        json.append("]");
        sendJson(exchange, json.toString());
    }

    private void handleItems(HttpExchange exchange) throws IOException {
        if (!checkGet(exchange)) return;
        DataCollector dc = evoCore.getDataCollector();

        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        // Top 50 most used items
        List<Map.Entry<Material, ItemMetrics>> sorted = dc.getAllItemMetrics().entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().getTimesEquipped(), a.getValue().getTimesEquipped()))
                .limit(50)
                .collect(Collectors.toList());

        for (Map.Entry<Material, ItemMetrics> entry : sorted) {
            ItemMetrics im = entry.getValue();
            if (!first) json.append(",");
            first = false;
            json.append("{");
            json.append("\"material\":\"").append(entry.getKey().name()).append("\",");
            json.append("\"equipped\":").append(im.getTimesEquipped()).append(",");
            json.append("\"used\":").append(im.getTimesUsed()).append(",");
            json.append("\"sold\":").append(im.getTimesSold()).append(",");
            json.append("\"bought\":").append(im.getTimesBought()).append(",");
            json.append("\"demand\":").append(String.format("%.2f", im.getDemandScore())).append(",");
            json.append("\"supply\":").append(String.format("%.2f", im.getSupplyScore()));
            json.append("}");
        }
        json.append("]");
        sendJson(exchange, json.toString());
    }

    private void handleEconomy(HttpExchange exchange) throws IOException {
        if (!checkGet(exchange)) return;
        DataCollector dc = evoCore.getDataCollector();

        // Top items by demand
        List<Map.Entry<Material, ItemMetrics>> byDemand = dc.getAllItemMetrics().entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue().getDemandScore(), a.getValue().getDemandScore()))
                .limit(20)
                .collect(Collectors.toList());

        StringBuilder json = new StringBuilder("{\"topDemand\":[");
        boolean first = true;
        for (Map.Entry<Material, ItemMetrics> entry : byDemand) {
            ItemMetrics im = entry.getValue();
            if (!first) json.append(",");
            first = false;
            json.append("{\"item\":\"").append(entry.getKey().name())
                    .append("\",\"demand\":").append(String.format("%.2f", im.getDemandScore()))
                    .append(",\"supply\":").append(String.format("%.2f", im.getSupplyScore()))
                    .append(",\"avgBuy\":").append(String.format("%.0f", im.getAvgBuyPrice()))
                    .append(",\"avgSell\":").append(String.format("%.0f", im.getAvgSellPrice()))
                    .append("}");
        }
        json.append("]}");
        sendJson(exchange, json.toString());
    }

    private void handleMeta(HttpExchange exchange) throws IOException {
        if (!checkGet(exchange)) return;
        DataCollector dc = evoCore.getDataCollector();

        // Top equipped weapons
        List<Map.Entry<Material, Integer>> topWeapons = dc.getEquippedWeapons().entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(20)
                .collect(Collectors.toList());

        StringBuilder json = new StringBuilder("{\"topWeapons\":[");
        boolean first = true;
        for (Map.Entry<Material, Integer> entry : topWeapons) {
            if (!first) json.append(",");
            first = false;
            json.append("{\"item\":\"").append(entry.getKey().name())
                    .append("\",\"count\":").append(entry.getValue()).append("}");
        }
        json.append("]}");
        sendJson(exchange, json.toString());
    }

    private void handleAlerts(HttpExchange exchange) throws IOException {
        if (!checkGet(exchange)) return;
        List<String> alerts = evoCore.getAnalyticsEngine().getAlerts();
        sendJson(exchange, listToJsonArray(alerts));
    }

    // --- Dashboard HTML ---

    private void handleDashboard(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/") || path.equals("/index.html")) {
            sendHtml(exchange, getDashboardHtml());
        } else {
            send404(exchange);
        }
    }

    private String getDashboardHtml() {
        String bluemapUrl = plugin.getConfig().getString("evocore.bluemap-url", "http://localhost:8100");
        return "<!DOCTYPE html>\n"
+ "<html lang=\"es\">\n"
+ "<head>\n"
+ "  <meta charset=\"UTF-8\">\n"
+ "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
+ "  <title>Wardstone - EvoCore Dashboard</title>\n"
+ "  <style>\n"
+ "    * { margin: 0; padding: 0; box-sizing: border-box; }\n"
+ "    :root {\n"
+ "      --bg: #0f0f1a; --surface: #1a1a2e; --surface2: #16213e;\n"
+ "      --accent: #e94560; --accent2: #0f3460; --text: #eee; --text2: #aaa;\n"
+ "      --green: #00d68f; --yellow: #ffaa00; --red: #e94560; --blue: #4fc3f7;\n"
+ "    }\n"
+ "    body { background: var(--bg); color: var(--text); font-family: 'Segoe UI', system-ui, sans-serif; }\n"
+ "    .header { background: linear-gradient(135deg, var(--surface2), var(--surface)); padding: 20px 30px;\n"
+ "      border-bottom: 2px solid var(--accent); display: flex; align-items: center; justify-content: space-between; }\n"
+ "    .header h1 { font-size: 1.8em; background: linear-gradient(90deg, var(--accent), var(--blue));\n"
+ "      -webkit-background-clip: text; -webkit-text-fill-color: transparent; }\n"
+ "    .header .status { display: flex; gap: 20px; align-items: center; }\n"
+ "    .header .dot { width: 10px; height: 10px; border-radius: 50%; background: var(--green);\n"
+ "      display: inline-block; animation: pulse 2s infinite; }\n"
+ "    @keyframes pulse { 0%,100% { opacity: 1; } 50% { opacity: 0.4; } }\n"
+ "    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));\n"
+ "      gap: 16px; padding: 20px; }\n"
+ "    .card { background: var(--surface); border-radius: 12px; padding: 20px;\n"
+ "      border: 1px solid rgba(255,255,255,0.05); transition: transform 0.2s; }\n"
+ "    .card:hover { transform: translateY(-2px); border-color: var(--accent); }\n"
+ "    .card h2 { font-size: 1.1em; color: var(--accent); margin-bottom: 12px;\n"
+ "      display: flex; align-items: center; gap: 8px; }\n"
+ "    .stat-row { display: flex; justify-content: space-between; padding: 6px 0;\n"
+ "      border-bottom: 1px solid rgba(255,255,255,0.05); }\n"
+ "    .stat-row:last-child { border: none; }\n"
+ "    .stat-label { color: var(--text2); font-size: 0.9em; }\n"
+ "    .stat-value { font-weight: bold; font-size: 0.95em; }\n"
+ "    .stat-value.green { color: var(--green); }\n"
+ "    .stat-value.yellow { color: var(--yellow); }\n"
+ "    .stat-value.red { color: var(--red); }\n"
+ "    .stat-value.blue { color: var(--blue); }\n"
+ "    .big-stat { text-align: center; padding: 10px; }\n"
+ "    .big-stat .number { font-size: 2.5em; font-weight: bold; }\n"
+ "    .big-stat .label { color: var(--text2); font-size: 0.85em; margin-top: 4px; }\n"
+ "    .overview-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(120px, 1fr)); gap: 12px; }\n"
+ "    .badge { display: inline-block; padding: 2px 8px; border-radius: 6px; font-size: 0.75em;\n"
+ "      font-weight: bold; text-transform: uppercase; }\n"
+ "    .badge.normal { background: rgba(0,214,143,0.15); color: var(--green); }\n"
+ "    .badge.overfarmed { background: rgba(233,69,96,0.15); color: var(--red); }\n"
+ "    .badge.underused { background: rgba(255,170,0,0.15); color: var(--yellow); }\n"
+ "    .badge.hotspot { background: rgba(79,195,247,0.15); color: var(--blue); }\n"
+ "    .table { width: 100%; border-collapse: collapse; font-size: 0.9em; }\n"
+ "    .table th { text-align: left; color: var(--text2); padding: 8px; font-size: 0.8em;\n"
+ "      text-transform: uppercase; border-bottom: 1px solid rgba(255,255,255,0.1); }\n"
+ "    .table td { padding: 8px; border-bottom: 1px solid rgba(255,255,255,0.03); }\n"
+ "    .bar-container { width: 100%; height: 8px; background: rgba(255,255,255,0.05);\n"
+ "      border-radius: 4px; overflow: hidden; }\n"
+ "    .bar-fill { height: 100%; border-radius: 4px; transition: width 1s ease; }\n"
+ "    .alerts-list { max-height: 200px; overflow-y: auto; }\n"
+ "    .alert-item { padding: 8px 12px; margin: 4px 0; background: rgba(233,69,96,0.1);\n"
+ "      border-left: 3px solid var(--red); border-radius: 4px; font-size: 0.85em; }\n"
+ "    .map-frame { width: 100%; height: 500px; border: none; border-radius: 12px;\n"
+ "      background: var(--surface); }\n"
+ "    .section { padding: 0 20px 20px; }\n"
+ "    .section h2 { font-size: 1.3em; margin-bottom: 16px; color: var(--text);\n"
+ "      display: flex; align-items: center; gap: 8px; }\n"
+ "    .tabs { display: flex; gap: 8px; padding: 0 20px; margin-bottom: -1px; }\n"
+ "    .tab { padding: 10px 20px; background: var(--surface); border: 1px solid rgba(255,255,255,0.05);\n"
+ "      border-bottom: none; border-radius: 8px 8px 0 0; cursor: pointer; color: var(--text2); }\n"
+ "    .tab.active { background: var(--surface2); color: var(--accent); border-color: var(--accent); }\n"
+ "    .tab-content { display: none; }\n"
+ "    .tab-content.active { display: block; }\n"
+ "    .refresh-btn { background: var(--accent); color: white; border: none; padding: 8px 16px;\n"
+ "      border-radius: 8px; cursor: pointer; font-size: 0.85em; }\n"
+ "    .refresh-btn:hover { opacity: 0.8; }\n"
+ "    .chart-bar { display: flex; align-items: center; gap: 10px; margin: 4px 0; }\n"
+ "    .chart-bar .name { width: 140px; font-size: 0.85em; color: var(--text2); text-align: right;\n"
+ "      overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }\n"
+ "    .chart-bar .bar-wrap { flex: 1; }\n"
+ "    .chart-bar .val { width: 50px; font-size: 0.85em; font-weight: bold; }\n"
+ "  </style>\n"
+ "</head>\n"
+ "<body>\n"
+ "  <div class=\"header\">\n"
+ "    <h1>⚔ Wardstone — EvoCore Dashboard</h1>\n"
+ "    <div class=\"status\">\n"
+ "      <span class=\"dot\"></span>\n"
+ "      <span id=\"playerCount\" style=\"color:var(--green)\">0 jugadores</span>\n"
+ "      <button class=\"refresh-btn\" onclick=\"loadAll()\">↻ Actualizar</button>\n"
+ "    </div>\n"
+ "  </div>\n"
+ "\n"
+ "  <!-- Overview Cards -->\n"
+ "  <div class=\"grid\" id=\"overviewGrid\"></div>\n"
+ "\n"
+ "  <!-- Tabs -->\n"
+ "  <div class=\"tabs\">\n"
+ "    <div class=\"tab active\" onclick=\"switchTab('zones')\">🗺 Zonas</div>\n"
+ "    <div class=\"tab\" onclick=\"switchTab('mobs')\">🧟 Mobs</div>\n"
+ "    <div class=\"tab\" onclick=\"switchTab('items')\">⚔ Meta</div>\n"
+ "    <div class=\"tab\" onclick=\"switchTab('economy')\">💰 Economía</div>\n"
+ "    <div class=\"tab\" onclick=\"switchTab('map')\">🗺 Mapa</div>\n"
+ "    <div class=\"tab\" onclick=\"switchTab('alerts')\">⚠ Alertas</div>\n"
+ "  </div>\n"
+ "\n"
+ "  <!-- Zones Tab -->\n"
+ "  <div class=\"section tab-content active\" id=\"tab-zones\">\n"
+ "    <table class=\"table\" id=\"zonesTable\">\n"
+ "      <thead><tr><th>Dueño</th><th>Nivel</th><th>Estado</th><th>Kills/h</th><th>Jugadores</th>\n"
+ "        <th>Presión</th><th>Spawn</th><th>Loot</th></tr></thead>\n"
+ "      <tbody></tbody>\n"
+ "    </table>\n"
+ "  </div>\n"
+ "\n"
+ "  <!-- Mobs Tab -->\n"
+ "  <div class=\"section tab-content\" id=\"tab-mobs\">\n"
+ "    <div id=\"mobsChart\"></div>\n"
+ "    <table class=\"table\" id=\"mobsTable\" style=\"margin-top:16px\">\n"
+ "      <thead><tr><th>Mob</th><th>Estado</th><th>Kills/h</th><th>Total</th><th>Avg Kill</th>\n"
+ "        <th>HP Mod</th><th>Spawn Mod</th></tr></thead>\n"
+ "      <tbody></tbody>\n"
+ "    </table>\n"
+ "  </div>\n"
+ "\n"
+ "  <!-- Items/Meta Tab -->\n"
+ "  <div class=\"section tab-content\" id=\"tab-items\">\n"
+ "    <h2>🏆 Items Más Equipados</h2>\n"
+ "    <div id=\"metaChart\"></div>\n"
+ "  </div>\n"
+ "\n"
+ "  <!-- Economy Tab -->\n"
+ "  <div class=\"section tab-content\" id=\"tab-economy\">\n"
+ "    <h2>💰 Top Demanda</h2>\n"
+ "    <div id=\"economyChart\"></div>\n"
+ "  </div>\n"
+ "\n"
+ "  <!-- Map Tab -->\n"
+ "  <div class=\"section tab-content\" id=\"tab-map\">\n"
+ "    <h2>🗺 Mapa del Servidor (BlueMap)</h2>\n"
+ "    <iframe class=\"map-frame\" src=\"" + bluemapUrl + "\" id=\"bluemapFrame\"></iframe>\n"
+ "  </div>\n"
+ "\n"
+ "  <!-- Alerts Tab -->\n"
+ "  <div class=\"section tab-content\" id=\"tab-alerts\">\n"
+ "    <h2>⚠ Alertas del Sistema</h2>\n"
+ "    <div class=\"alerts-list\" id=\"alertsList\"></div>\n"
+ "  </div>\n"
+ "\n"
+ "<script>\n"
+ "function switchTab(name) {\n"
+ "  document.querySelectorAll('.tab-content').forEach(t => t.classList.remove('active'));\n"
+ "  document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));\n"
+ "  document.getElementById('tab-' + name).classList.add('active');\n"
+ "  event.target.classList.add('active');\n"
+ "}\n"
+ "\n"
+ "function statusBadge(s) {\n"
+ "  const cls = s.toLowerCase();\n"
+ "  return '<span class=\"badge ' + cls + '\">' + s + '</span>';\n"
+ "}\n"
+ "\n"
+ "function modColor(v) {\n"
+ "  if (v > 1.1) return 'red';\n"
+ "  if (v < 0.9) return 'yellow';\n"
+ "  return 'green';\n"
+ "}\n"
+ "\n"
+ "function makeBar(value, max, color) {\n"
+ "  const pct = Math.min(100, (value / Math.max(1, max)) * 100);\n"
+ "  return '<div class=\"bar-container\"><div class=\"bar-fill\" style=\"width:' + pct + '%;background:var(--' + color + ')\"></div></div>';\n"
+ "}\n"
+ "\n"
+ "function chartBar(name, value, max, color) {\n"
+ "  return '<div class=\"chart-bar\"><div class=\"name\">' + name + '</div>'\n"
+ "    + '<div class=\"bar-wrap\">' + makeBar(value, max, color) + '</div>'\n"
+ "    + '<div class=\"val\" style=\"color:var(--' + color + ')\">' + value + '</div></div>';\n"
+ "}\n"
+ "\n"
+ "async function loadOverview() {\n"
+ "  const r = await fetch('/api/overview');\n"
+ "  const d = await r.json();\n"
+ "  document.getElementById('playerCount').textContent = d.onlinePlayers + ' jugadores';\n"
+ "  document.getElementById('overviewGrid').innerHTML = \n"
+ "    '<div class=\"card\"><div class=\"big-stat\"><div class=\"number\" style=\"color:var(--green)\">' + d.onlinePlayers + '</div><div class=\"label\">Jugadores Online</div></div></div>'\n"
+ "    + '<div class=\"card\"><div class=\"big-stat\"><div class=\"number\" style=\"color:var(--blue)\">' + d.totalZones + '</div><div class=\"label\">Zonas Protegidas</div></div></div>'\n"
+ "    + '<div class=\"card\"><div class=\"big-stat\"><div class=\"number\" style=\"color:var(--red)\">' + d.totalMobKills + '</div><div class=\"label\">Mobs Eliminados</div></div></div>'\n"
+ "    + '<div class=\"card\"><div class=\"big-stat\"><div class=\"number\" style=\"color:var(--yellow)\">' + d.activeInvasions + '</div><div class=\"label\">Invasiones Activas</div></div></div>';\n"
+ "}\n"
+ "\n"
+ "async function loadZones() {\n"
+ "  const r = await fetch('/api/zones');\n"
+ "  const zones = await r.json();\n"
+ "  const tbody = document.querySelector('#zonesTable tbody');\n"
+ "  tbody.innerHTML = zones.map(z =>\n"
+ "    '<tr><td>' + z.owner + '</td><td>' + z.level + '</td><td>' + statusBadge(z.status) + '</td>'\n"
+ "    + '<td>' + z.killsHour + '</td><td>' + z.players + '</td>'\n"
+ "    + '<td>' + z.pressure + '</td>'\n"
+ "    + '<td class=\"' + modColor(z.spawnMod) + '\">' + z.spawnMod + 'x</td>'\n"
+ "    + '<td class=\"' + modColor(z.lootMod) + '\">' + z.lootMod + 'x</td></tr>'\n"
+ "  ).join('');\n"
+ "}\n"
+ "\n"
+ "async function loadMobs() {\n"
+ "  const r = await fetch('/api/mobs');\n"
+ "  const mobs = await r.json();\n"
+ "  const maxKills = Math.max(1, ...mobs.map(m => m.killsHour));\n"
+ "  document.getElementById('mobsChart').innerHTML = mobs.slice(0, 10).map(m =>\n"
+ "    chartBar(m.type, m.killsHour, maxKills, m.status === 'OVERFARMED' ? 'red' : m.status === 'TOO_EASY' ? 'yellow' : 'green')\n"
+ "  ).join('');\n"
+ "  const tbody = document.querySelector('#mobsTable tbody');\n"
+ "  tbody.innerHTML = mobs.map(m =>\n"
+ "    '<tr><td>' + m.type + '</td><td>' + statusBadge(m.status) + '</td>'\n"
+ "    + '<td>' + m.killsHour + '</td><td>' + m.totalKills + '</td>'\n"
+ "    + '<td>' + (m.avgKillTime / 1000).toFixed(1) + 's</td>'\n"
+ "    + '<td class=\"' + modColor(m.healthMod) + '\">' + m.healthMod + 'x</td>'\n"
+ "    + '<td class=\"' + modColor(m.spawnMod) + '\">' + m.spawnMod + 'x</td></tr>'\n"
+ "  ).join('');\n"
+ "}\n"
+ "\n"
+ "async function loadMeta() {\n"
+ "  const r = await fetch('/api/meta');\n"
+ "  const d = await r.json();\n"
+ "  const maxCount = Math.max(1, ...d.topWeapons.map(w => w.count));\n"
+ "  document.getElementById('metaChart').innerHTML = d.topWeapons.map(w =>\n"
+ "    chartBar(w.item.replace(/_/g, ' '), w.count, maxCount, 'blue')\n"
+ "  ).join('');\n"
+ "}\n"
+ "\n"
+ "async function loadEconomy() {\n"
+ "  const r = await fetch('/api/economy');\n"
+ "  const d = await r.json();\n"
+ "  const maxDemand = Math.max(1, ...d.topDemand.map(i => i.demand));\n"
+ "  document.getElementById('economyChart').innerHTML = d.topDemand.map(i =>\n"
+ "    chartBar(i.item.replace(/_/g, ' '), i.demand.toFixed(1), maxDemand, 'yellow')\n"
+ "  ).join('');\n"
+ "}\n"
+ "\n"
+ "async function loadAlerts() {\n"
+ "  const r = await fetch('/api/alerts');\n"
+ "  const alerts = await r.json();\n"
+ "  document.getElementById('alertsList').innerHTML = alerts.length > 0\n"
+ "    ? alerts.map(a => '<div class=\"alert-item\">⚠ ' + a + '</div>').join('')\n"
+ "    : '<div style=\"color:var(--text2);padding:20px;text-align:center\">✅ Sin alertas — todo en equilibrio</div>';\n"
+ "}\n"
+ "\n"
+ "function loadAll() {\n"
+ "  loadOverview(); loadZones(); loadMobs(); loadMeta(); loadEconomy(); loadAlerts();\n"
+ "}\n"
+ "\n"
+ "loadAll();\n"
+ "setInterval(loadAll, 15000);\n"
+ "</script>\n"
+ "</body>\n"
+ "</html>";
    }

    // --- Utility methods ---

    private boolean checkGet(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return false;
        }
        return true;
    }

    private void sendJson(HttpExchange exchange, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendHtml(HttpExchange exchange, String html) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void send404(HttpExchange exchange) throws IOException {
        String msg = "Not Found";
        exchange.sendResponseHeaders(404, msg.length());
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(msg.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private String listToJsonArray(List<String> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escapeJson(list.get(i))).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }
}
