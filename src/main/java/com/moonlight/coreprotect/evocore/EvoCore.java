package com.moonlight.coreprotect.evocore;

import com.moonlight.coreprotect.CoreProtectPlugin;

public class EvoCore {

    private final CoreProtectPlugin plugin;
    private final DataCollector dataCollector;
    private final AnalyticsEngine analyticsEngine;
    private final BalanceEngine balanceEngine;
    // AutoEventManager desactivado - sistema de oleadas eliminado
    // private final AutoEventManager autoEventManager;
    private final WebServer webServer;

    public EvoCore(CoreProtectPlugin plugin) {
        this.plugin = plugin;

        // Initialize in order of dependency
        this.dataCollector = new DataCollector(plugin);
        this.analyticsEngine = new AnalyticsEngine(plugin, dataCollector);
        this.balanceEngine = new BalanceEngine(plugin, dataCollector, analyticsEngine);
        // this.autoEventManager = new AutoEventManager(plugin, dataCollector, analyticsEngine, balanceEngine); // Desactivado

        // Start web server
        int port = plugin.getConfig().getInt("evocore.web-port", 8095);
        this.webServer = new WebServer(plugin, this, port);
        this.webServer.start();

        plugin.getLogger().info("[EvoCore] Sistema de equilibrio dinámico activado");
        plugin.getLogger().info("[EvoCore] Dashboard web en http://localhost:" + port);
    }

    public void shutdown() {
        if (webServer != null) webServer.stop();
    }

    public DataCollector getDataCollector() { return dataCollector; }
    public AnalyticsEngine getAnalyticsEngine() { return analyticsEngine; }
    public BalanceEngine getBalanceEngine() { return balanceEngine; }
    // public AutoEventManager getAutoEventManager() { return autoEventManager; } // Desactivado
    public WebServer getWebServer() { return webServer; }
}
