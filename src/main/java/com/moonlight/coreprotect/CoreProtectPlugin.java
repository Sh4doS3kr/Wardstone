package com.moonlight.coreprotect;

import com.moonlight.coreprotect.commands.CoreCommand;
import com.moonlight.coreprotect.data.DataManager;
import com.moonlight.coreprotect.gui.GUIListener;
import com.moonlight.coreprotect.protection.CorePlaceListener;
import com.moonlight.coreprotect.protection.ProtectionListener;
import com.moonlight.coreprotect.protection.ProtectionManager;
import com.moonlight.coreprotect.achievements.AchievementListener;
import com.moonlight.coreprotect.achievements.AchievementManager;
import com.moonlight.coreprotect.evocore.EvoCore;
import com.moonlight.coreprotect.integrations.BlueMapIntegration;
import com.moonlight.coreprotect.utils.MessageManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class CoreProtectPlugin extends JavaPlugin {

    private static CoreProtectPlugin instance;
    private Economy economy;
    private ProtectionManager protectionManager;
    private DataManager dataManager;
    private MessageManager messageManager;
    private BlueMapIntegration blueMapIntegration;
    private AchievementManager achievementManager;
    private AchievementListener achievementListener;
    private EvoCore evoCore;

    @Override
    public void onEnable() {
        instance = this;

        // Guardar configuracion por defecto
        saveDefaultConfig();
        saveResource("messages.yml", false);

        // MIGRADOR: Si la carpeta de datos viene de CoreProtect (nombre anterior), copiar data.yml
        migrateDataFromOldFolder();

        // CONFIG MIGRATION: Fix Level 20 material if it's old
        if (getConfig().getString("levels.20.material").equals("BEACON") ||
                getConfig().getString("levels.20.material").equals("NETHER_STAR")) {
            getConfig().set("levels.20.material", "SEA_LANTERN");
            saveConfig();
            getLogger().info("MIGRADOR: Corregido material de nivel 20 a SEA_LANTERN en config.yml");
        }

        // Inicializar managers
        messageManager = new MessageManager(this);
        dataManager = new DataManager(this);
        protectionManager = new ProtectionManager(this);

        // Cargar datos guardados
        dataManager.loadData();

        // Configurar economia con Vault
        if (!setupEconomy()) {
            getLogger().severe("Vault no encontrado. El plugin requiere Vault para funcionar.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Inicializar achievements
        achievementManager = new AchievementManager(this);
        achievementManager.loadPlayerData();
        achievementListener = new AchievementListener(this);

        // Registrar eventos
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new CorePlaceListener(this), this);
        getServer().getPluginManager().registerEvents(achievementListener, this);

        // Registrar comandos
        // Registrar comandos
        getCommand("cores").setExecutor(new CoreCommand(this));
        getCommand("cores").setTabCompleter(new CoreCommand(this));
        getCommand("admincore").setExecutor(new com.moonlight.coreprotect.commands.AdminCommand(this));
        getCommand("admincore").setTabCompleter(new com.moonlight.coreprotect.commands.AdminCommand(this));

        // Auto-guardado
        int saveInterval = getConfig().getInt("settings.auto-save-interval", 5) * 60 * 20;
        getServer().getScheduler().runTaskTimer(this, () -> dataManager.saveData(), saveInterval, saveInterval);

        // Integración BlueMap (opcional)
        blueMapIntegration = new BlueMapIntegration(this);
        blueMapIntegration.init();

        // EvoCore - Sistema de equilibrio dinámico + web dashboard
        evoCore = new EvoCore(this);
        getServer().getPluginManager().registerEvents(evoCore.getDataCollector(), this);

        getLogger().info("CoreProtect habilitado correctamente");
    }

    @Override
    public void onDisable() {
        if (evoCore != null) {
            evoCore.shutdown();
        }
        if (dataManager != null) {
            dataManager.saveData();
        }
        getLogger().info("CoreProtect deshabilitado");
    }

    private void migrateDataFromOldFolder() {
        // Possible old folder names from previous plugin name
        String[] oldNames = {"CoreProtect", "coreprotect"};
        File currentFolder = getDataFolder();

        for (String oldName : oldNames) {
            File oldFolder = new File(currentFolder.getParentFile(), oldName);
            if (!oldFolder.exists() || oldFolder.equals(currentFolder)) continue;

            // Migrate data.yml
            File oldData = new File(oldFolder, "data.yml");
            File newData = new File(currentFolder, "data.yml");
            if (oldData.exists() && !newData.exists()) {
                try {
                    currentFolder.mkdirs();
                    Files.copy(oldData.toPath(), newData.toPath());
                    getLogger().info("MIGRADOR: Copiado data.yml desde " + oldFolder.getName() + " -> " + currentFolder.getName());
                } catch (IOException e) {
                    getLogger().severe("MIGRADOR: Error copiando data.yml: " + e.getMessage());
                }
            }

            // Migrate achievements.yml
            File oldAch = new File(oldFolder, "achievements.yml");
            File newAch = new File(currentFolder, "achievements.yml");
            if (oldAch.exists() && !newAch.exists()) {
                try {
                    currentFolder.mkdirs();
                    Files.copy(oldAch.toPath(), newAch.toPath());
                    getLogger().info("MIGRADOR: Copiado achievements.yml desde " + oldFolder.getName());
                } catch (IOException e) {
                    getLogger().severe("MIGRADOR: Error copiando achievements.yml: " + e.getMessage());
                }
            }
        }
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    public static CoreProtectPlugin getInstance() {
        return instance;
    }

    public Economy getEconomy() {
        return economy;
    }

    public ProtectionManager getProtectionManager() {
        return protectionManager;
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public BlueMapIntegration getBlueMapIntegration() {
        return blueMapIntegration;
    }

    public AchievementManager getAchievementManager() {
        return achievementManager;
    }

    public AchievementListener getAchievementListener() {
        return achievementListener;
    }

    
    public EvoCore getEvoCore() {
        return evoCore;
    }
}
