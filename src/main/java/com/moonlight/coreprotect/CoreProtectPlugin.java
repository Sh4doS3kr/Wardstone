package com.moonlight.coreprotect;

import com.moonlight.coreprotect.commands.CoreCommand;
import com.moonlight.coreprotect.commands.GaspiCommand;
import com.moonlight.coreprotect.commands.PetsInfoCommand;
import com.moonlight.coreprotect.commands.SaveKothCommand;
import com.moonlight.coreprotect.commands.ZatreCommand;
import com.moonlight.coreprotect.crafting.CubeeSnackRecipe;
import com.moonlight.coreprotect.finishers.FinisherCommand;
import com.moonlight.coreprotect.finishers.FinisherListener;
import com.moonlight.coreprotect.finishers.FinisherManager;
import com.moonlight.coreprotect.koth.KothAbilityBlocker;
import com.moonlight.coreprotect.koth.KothCommand;
import com.moonlight.coreprotect.koth.KothListener;
import com.moonlight.coreprotect.koth.KothManager;
import com.moonlight.coreprotect.koth.KothModeListener;
import com.moonlight.coreprotect.rewards.PlayTimeRewardsManager;
import com.moonlight.coreprotect.rewards.PlayTimeRewardsListener;
import com.moonlight.coreprotect.protection.CorePlaceListener;
import com.moonlight.coreprotect.protection.ProtectionListener;
import com.moonlight.coreprotect.protection.ProtectionManager;
import com.moonlight.coreprotect.protection.SpawnCollisionManager;
import com.moonlight.coreprotect.protection.WindChargeListener;
import com.moonlight.coreprotect.achievements.AchievementListener;
import com.moonlight.coreprotect.achievements.AchievementManager;
import com.moonlight.coreprotect.data.DataManager;
import com.moonlight.coreprotect.gui.GUIListener;
import com.moonlight.coreprotect.combat.CombatTagManager;
import com.moonlight.coreprotect.combat.CombatTagListener;
import com.moonlight.coreprotect.auction.AuctionManager;
import com.moonlight.coreprotect.auction.AuctionCommand;
import com.moonlight.coreprotect.auction.AuctionListener;
import com.moonlight.coreprotect.bounty.BountyManager;
import com.moonlight.coreprotect.bounty.BountyCommand;
import com.moonlight.coreprotect.bounty.BountyListener;
import com.moonlight.coreprotect.shadowhunter.ShadowHunterManager;
import com.moonlight.coreprotect.shadowhunter.ShadowHunterListener;
import com.moonlight.coreprotect.shadowhunter.SkyParkourListener;
import com.moonlight.coreprotect.shadowhunter.VoidBladeListener;
import com.moonlight.coreprotect.shadowhunter.VoidPickaxeListener;
import com.moonlight.coreprotect.shadowhunter.VoidChestplateListener;
import com.moonlight.coreprotect.shadowhunter.VoidBowListener;
import com.moonlight.coreprotect.shadowhunter.VoidLeggingsListener;
import com.moonlight.coreprotect.shadowhunter.VoidBootsListener;
import com.moonlight.coreprotect.shadowhunter.LabyrinthProtectionListener;
import com.moonlight.coreprotect.shadowhunter.MissionArmorRestrictionListener;
import com.moonlight.coreprotect.shadowhunter.AbyssBladeListener;
import com.moonlight.coreprotect.shadowhunter.WhiteHoleBladeListener;
import com.moonlight.coreprotect.shadowhunter.ErranteCommand;
import com.moonlight.coreprotect.shadowhunter.ErranteGUIListener;
import com.moonlight.coreprotect.sleep.SleepListener;
import com.moonlight.coreprotect.vault.PrivateVaultManager;
import com.moonlight.coreprotect.vault.PrivateVaultCommand;
import com.moonlight.coreprotect.vault.PrivateVaultListener;
import com.moonlight.coreprotect.listeners.EnchantedGoldenAppleCooldown;
import com.moonlight.coreprotect.afkzone.AfkZoneManager;
import com.moonlight.coreprotect.afkzone.AfkZoneListener;
import com.moonlight.coreprotect.evocore.EvoCore;
import com.moonlight.coreprotect.integrations.BlueMapIntegration;
import com.moonlight.coreprotect.utils.MessageManager;
import com.moonlight.coreprotect.pvp.PvBarrierListener;
import com.moonlight.coreprotect.essence.EssenceManager;
import com.moonlight.coreprotect.essence.EssenceCommand;
import com.moonlight.coreprotect.essence.EssenceShopGUI;
import com.moonlight.coreprotect.essence.EssenceListener;
import com.moonlight.coreprotect.kits.KitsCommand;
import com.moonlight.coreprotect.commands.ArtistDaguCommand;
import com.moonlight.coreprotect.performance.TPSMonitor;
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
    private FinisherManager finisherManager;
    private FinisherListener finisherListener;
    private KothManager kothManager;
    private PlayTimeRewardsManager playTimeRewardsManager;
    private CombatTagManager combatTagManager;
    private AuctionManager auctionManager;
    private BountyManager bountyManager;
    private ShadowHunterManager shadowHunterManager;
    private VoidBladeListener voidBladeListener;
    private SkyParkourListener skyParkourListener;
    private VoidLeggingsListener voidLeggingsListener;
    private AfkZoneManager afkZoneManager;
    private TPSMonitor tpsMonitor;
    private PrivateVaultManager privateVaultManager;
    private com.moonlight.coreprotect.bossarena.BossArenaManager bossArenaManager;
    private EssenceManager essenceManager;
    private EssenceShopGUI essenceShopGUI;
    private ProtectionListener protectionListener;
    private SpawnCollisionManager spawnCollisionManager;
    private com.moonlight.coreprotect.structures.StructureManager structureManager;
    private com.moonlight.coreprotect.structures.FishingManager fishingManager;
    private com.moonlight.coreprotect.spawners.SpawnerManager spawnerManager;
    private com.moonlight.coreprotect.discord.DiscordBotManager discordBotManager;
    private com.moonlight.coreprotect.minigames.MiniGameManager miniGameManager;
    private com.moonlight.coreprotect.core.CorePrestigeManager corePrestigeManager;
    private com.moonlight.coreprotect.streak.DailyStreakManager dailyStreakManager;
    private com.moonlight.coreprotect.prestige.PrestigeManager prestigeManager;
    private com.moonlight.coreprotect.prestige.PrestigeRouletteManager prestigeRouletteManager;
    private com.moonlight.coreprotect.prestige.PrestigeWeeklyManager prestigeWeeklyManager;
    private com.moonlight.coreprotect.tips.TipsManager tipsManager;
    private com.moonlight.coreprotect.tips.BossBarAnnouncer bossBarAnnouncer;
    private com.moonlight.coreprotect.warps.PlayerWarpManager playerWarpManager;
    private com.moonlight.coreprotect.warps.PlayerWarpGUI playerWarpGUI;
    private com.moonlight.coreprotect.rewards.DailyRouletteManager dailyRouletteManager;
    private com.moonlight.coreprotect.events.GlobalEventManager globalEventManager;
    private com.moonlight.coreprotect.events.ScratchCardManager scratchCardManager;
    private com.moonlight.coreprotect.koth.KothCommand kothCommand;
    private com.moonlight.coreprotect.voicechat.ProxiChatManager proxiChatManager;
    private com.moonlight.coreprotect.rewards.PendingRewardsManager pendingRewardsManager;
    private com.moonlight.coreprotect.rewards.PlaytimeRouletteManager playtimeRouletteManager;

    @Override
    public void onEnable() {
        instance = this;

        // Guardar configuracion por defecto
        saveDefaultConfig();
        saveResource("messages.yml", false);

        // Asegurar que la sección de Discord exista en el config
        if (!getConfig().contains("discord.bot-token")) {
            getConfig().set("discord.bot-token", "TU_TOKEN_AQUI");
            getConfig().set("discord.verification-channel", "1373603300791812147");
            saveConfig();
            getLogger().info("[Discord] Sección de Discord agregada al config.yml");
        }

        // MIGRADOR: Si la carpeta de datos viene de CoreProtect (nombre anterior),
        // copiar data.yml
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
        spawnCollisionManager = new SpawnCollisionManager(this);

        // Cargar datos guardados
        dataManager.loadData();

        // Inicializar Core Prestige system
        corePrestigeManager = new com.moonlight.coreprotect.core.CorePrestigeManager(this);

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

        // Inicializar finishers
        finisherManager = new FinisherManager(this);
        finisherListener = new FinisherListener(this);

        // Inicializar sistema de recompensas por tiempo
        playTimeRewardsManager = new PlayTimeRewardsManager(this);
        getServer().getPluginManager().registerEvents(new PlayTimeRewardsListener(playTimeRewardsManager), this);

        // Registrar eventos
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        this.protectionListener = new ProtectionListener(this);
        getServer().getPluginManager().registerEvents(this.protectionListener, this);
        getServer().getPluginManager().registerEvents(new CorePlaceListener(this), this);
        getServer().getPluginManager().registerEvents(new WindChargeListener(this), this);
        getServer().getPluginManager().registerEvents(spawnCollisionManager, this);
        getServer().getPluginManager().registerEvents(achievementListener, this);
        getServer().getPluginManager().registerEvents(finisherListener, this);

        // Registrar comandos
        getCommand("cores").setExecutor(new CoreCommand(this));
        getCommand("cores").setTabCompleter(new CoreCommand(this));
        getCommand("admincore").setExecutor(new com.moonlight.coreprotect.commands.AdminCommand(this));
        getCommand("admincore").setTabCompleter(new com.moonlight.coreprotect.commands.AdminCommand(this));
        getCommand("petsinfo").setExecutor(new PetsInfoCommand());
        getCommand("mobdiag").setExecutor(new com.moonlight.coreprotect.commands.MobDiagCommand(this));
        getCommand("gaspi").setExecutor(new GaspiCommand(this));
        getCommand("zatre").setExecutor(new ZatreCommand(this));
        getCommand("claimcore").setExecutor(new com.moonlight.coreprotect.commands.ClaimCoreCommand(this));
        getCommand("finishers").setExecutor(new FinisherCommand(this));
        getCommand("recompensas").setExecutor(playTimeRewardsManager);
        getCommand("recompensas").setTabCompleter(playTimeRewardsManager);
        getCommand("tiempojugado").setExecutor(playTimeRewardsManager);
        getCommand("tiempojugado").setTabCompleter(playTimeRewardsManager);
        getCommand("playtime").setExecutor(playTimeRewardsManager);
        getCommand("playtime").setTabCompleter(playTimeRewardsManager);

        // Registrar KOTH
        kothManager = new KothManager(this);
        kothCommand = new KothCommand(this);
        getCommand("wardstone").setExecutor(kothCommand);
        getCommand("wardstone").setTabCompleter(kothCommand);
        getCommand("koth").setExecutor(kothCommand);
        getCommand("koth").setTabCompleter(kothCommand);
        getCommand("voicechat").setExecutor(kothCommand);
        getServer().getPluginManager().registerEvents(new KothListener(this), this);
        getServer().getPluginManager().registerEvents(new KothModeListener(this, kothManager), this);
        getServer().getPluginManager().registerEvents(new KothAbilityBlocker(this, kothManager), this);

        // Registrar comando savekoth
        getCommand("savekoth").setExecutor(new SaveKothCommand(this));
        getCommand("savekoth").setTabCompleter(new SaveKothCommand(this));

        // Auto-guardado
        int saveInterval = getConfig().getInt("settings.auto-save-interval", 5) * 60 * 20;
        getServer().getScheduler().runTaskTimer(this, () -> dataManager.saveData(), saveInterval, saveInterval);

        // Integración BlueMap (opcional)
        blueMapIntegration = new BlueMapIntegration(this);
        blueMapIntegration.init();

        // CRITICAL: Force maxEntityCramming to 24 on all worlds (override other plugins)
        forceMaxEntityCramming();

        // Command Blocker - ocultar plugins y filtrar tab-completion
        getServer().getPluginManager()
                .registerEvents(new com.moonlight.coreprotect.protection.CommandBlockerListener(this), this);

        // Kit Demon - interceptar /kit demon de Essentials
        getServer().getPluginManager()
                .registerEvents(new com.moonlight.coreprotect.kits.KitDemon(this), this);
        // Kit Demon - habilidades pasivas del set
        com.moonlight.coreprotect.kits.KitDemonListener kitDemonListener = new com.moonlight.coreprotect.kits.KitDemonListener(this);
        getServer().getPluginManager().registerEvents(kitDemonListener, this);
        getServer().getScheduler().runTaskTimer(this, () -> kitDemonListener.tickAmbient(), 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, () -> kitDemonListener.tickAuraInfernal(), 40L, 40L);
        
        // Kit Demon - protección de items
        getServer().getPluginManager().registerEvents(new com.moonlight.coreprotect.kits.KitDemonProtectionListener(this), this);
        // Kit Demon - penalización de durabilidad para no-dueños
        getServer().getPluginManager().registerEvents(new com.moonlight.coreprotect.kits.DemonDurabilityPenaltyListener(this), this);

        // Kit YT Secret - /ytsecretkit
        getCommand("ytsecretkit").setExecutor(new com.moonlight.coreprotect.kits.KitYTSecret(this));
        com.moonlight.coreprotect.kits.KitYTSecretListener ytKitListener = new com.moonlight.coreprotect.kits.KitYTSecretListener(this);
        getServer().getPluginManager().registerEvents(ytKitListener, this);

        // Vela Combos - Espada de combos secuenciales
        getServer().getPluginManager().registerEvents(new com.moonlight.coreprotect.combos.CandleComboListener(this), this);
        getCommand("giveswordcandle").setExecutor(new com.moonlight.coreprotect.combos.CandleSwordCommand(this));

        // Boss Arena System
        bossArenaManager = new com.moonlight.coreprotect.bossarena.BossArenaManager(this);
        com.moonlight.coreprotect.bossarena.BossArenaCommand bossArenaCmd = new com.moonlight.coreprotect.bossarena.BossArenaCommand(this, bossArenaManager);
        getCommand("wardboss").setExecutor(bossArenaCmd);
        getCommand("wardboss").setTabCompleter(bossArenaCmd);
        getCommand("boss").setExecutor(bossArenaCmd);
        getCommand("bossarena").setExecutor(bossArenaCmd);
        com.moonlight.coreprotect.bossarena.BossArenaListener bossArenaListener = new com.moonlight.coreprotect.bossarena.BossArenaListener(this, bossArenaManager);
        getServer().getPluginManager().registerEvents(bossArenaListener, this);
        getServer().getScheduler().runTaskTimer(this, () -> bossArenaListener.tickAntifly(), 20L, 20L);

        // Combat Tag System
        combatTagManager = new CombatTagManager(this);
        getServer().getPluginManager().registerEvents(new CombatTagListener(this, combatTagManager), this);

        // PvP Barrier System - Barrera de cristal rojo para área restringida
        getServer().getPluginManager().registerEvents(new PvBarrierListener(this), this);
        
        // Anti-Zombie in Spawn Core - Check every 2 seconds (40 ticks)
        getServer().getScheduler().runTaskTimer(this, () -> protectionListener.checkZombiesInSpawnCore(), 40L, 40L);

        // Sleep system - 20% de jugadores para amanecer
        getServer().getPluginManager().registerEvents(new SleepListener(this), this);

        // Enchanted Golden Apple Cooldown - 310 segundos (5m 10s)
        getServer().getPluginManager().registerEvents(new EnchantedGoldenAppleCooldown(this), this);

        // Custom death messages
        getServer().getPluginManager().registerEvents(new CustomDeathListener(), this);

        // Custom join/quit messages: [+] / [-] with prestige tag
        getServer().getPluginManager().registerEvents(new com.moonlight.coreprotect.listeners.JoinQuitMessageListener(this), this);

        // Command filter: hide commands without permission, block /plugins etc.
        getServer().getPluginManager().registerEvents(new com.moonlight.coreprotect.listeners.CommandFilterListener(), this);

        // AFK Zone - zona de recompensas
        afkZoneManager = new AfkZoneManager(this);
        getServer().getPluginManager().registerEvents(new AfkZoneListener(this, afkZoneManager), this);

        // Auction House
        auctionManager = new AuctionManager(this);
        AuctionListener auctionListener = new AuctionListener(this, auctionManager);
        getServer().getPluginManager().registerEvents(auctionListener, this);
        getCommand("ah").setExecutor(new AuctionCommand(this, auctionManager, auctionListener));
        getCommand("ah").setTabCompleter(new AuctionCommand(this, auctionManager, auctionListener));

        // Bounty System
        bountyManager = new BountyManager(this);
        BountyListener bountyListener = new BountyListener(this, bountyManager);
        getServer().getPluginManager().registerEvents(bountyListener, this);
        getCommand("bounty").setExecutor(new BountyCommand(this, bountyManager, bountyListener));
        getCommand("bounty").setTabCompleter(new BountyCommand(this, bountyManager, bountyListener));

        // Shadow Hunter Quest System
        shadowHunterManager = new ShadowHunterManager(this);
        getServer().getPluginManager().registerEvents(new ShadowHunterListener(this, shadowHunterManager), this);
        voidBladeListener = new VoidBladeListener(this);
        getServer().getPluginManager().registerEvents(voidBladeListener, this);
        skyParkourListener = new SkyParkourListener(this);
        getServer().getPluginManager().registerEvents(skyParkourListener, this);
        // Partículas ambientales de la Hoja del Vacío
        getServer().getScheduler().runTaskTimer(this, () -> voidBladeListener.tickAmbientParticles(), 20L, 10L);
        // Misión 2: Devoradora de Almas
        AbyssBladeListener abyssBladeListener = new AbyssBladeListener(this);
        getServer().getPluginManager().registerEvents(abyssBladeListener, this);
        getServer().getScheduler().runTaskTimer(this, () -> abyssBladeListener.tickAmbientParticles(), 20L, 10L);
        // Misión 9: Estrella del Origen
        WhiteHoleBladeListener whiteHoleBladeListener = new WhiteHoleBladeListener(this);
        getServer().getPluginManager().registerEvents(whiteHoleBladeListener, this);
        getServer().getScheduler().runTaskTimer(this, () -> whiteHoleBladeListener.tickAmbientParticles(), 20L, 10L);

        // Comando /errante GUI
        getCommand("errante").setExecutor(new ErranteCommand(this));
        getServer().getPluginManager().registerEvents(new ErranteGUIListener(this), this);

        // Misión 3: Fractura del Vacío (pico)
        VoidPickaxeListener voidPickaxeListener = new VoidPickaxeListener(this);
        getServer().getPluginManager().registerEvents(voidPickaxeListener, this);
        getServer().getScheduler().runTaskTimer(this, () -> voidPickaxeListener.tickAmbientParticles(), 20L, 10L);

        // Misión 4: Elegía del Errante (peto)
        VoidChestplateListener voidChestplateListener = new VoidChestplateListener(this);
        getServer().getPluginManager().registerEvents(voidChestplateListener, this);
        getServer().getScheduler().runTaskTimer(this, () -> voidChestplateListener.tickPassives(), 20L, 20L);

        // Misión 5: Último Suspiro
        VoidBowListener voidBowListener = new VoidBowListener(this);
        getServer().getPluginManager().registerEvents(voidBowListener, this);
        getServer().getScheduler().runTaskTimer(this, () -> voidBowListener.tickPassive(), 20L, 20L);

        // Misión 6: Lágrimas de Lyra (leggings)
        voidLeggingsListener = new VoidLeggingsListener(this);
        getServer().getPluginManager().registerEvents(voidLeggingsListener, this);

        // Misión 7: Pisada del Olvido (botas)
        getServer().getPluginManager().registerEvents(new VoidBootsListener(this), this);
        getServer().getPluginManager().registerEvents(new LabyrinthProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new MissionArmorRestrictionListener(this), this);

        // Soul Speed exploit fix
        com.moonlight.coreprotect.listeners.SoulSpeedExploitFixListener soulSpeedFix = new com.moonlight.coreprotect.listeners.SoulSpeedExploitFixListener(this);
        getServer().getPluginManager().registerEvents(soulSpeedFix, this);
        getServer().getScheduler().runTaskTimer(this, () -> soulSpeedFix.tick(), 40L, 40L);

        // Misión 8: Martillo del Cero Absoluto (mazo)
        getServer().getPluginManager().registerEvents(new com.moonlight.coreprotect.shadowhunter.VoidMaceListener(this), this);

        // Private Vault System
        privateVaultManager = new PrivateVaultManager(this);
        PrivateVaultCommand pvCommand = new PrivateVaultCommand(this);
        getCommand("pv").setExecutor(pvCommand);
        getCommand("pv").setTabCompleter(pvCommand);
        getServer().getPluginManager().registerEvents(new PrivateVaultListener(this), this);

        // Essence System
        essenceManager = new EssenceManager(this);
        essenceShopGUI = new EssenceShopGUI(this);
        EssenceCommand essenceCmd = new EssenceCommand(this);
        getCommand("esencias").setExecutor(essenceCmd);
        getCommand("esencias").setTabCompleter(essenceCmd);
        getServer().getPluginManager().registerEvents(new EssenceListener(this, essenceShopGUI), this);
        getServer().getPluginManager().registerEvents(new com.moonlight.coreprotect.essence.SingleUseCommandListener(this), this);
        getServer().getPluginManager().registerEvents(new com.moonlight.coreprotect.listeners.BossEssenceListener(this), this);
        getServer().getPluginManager().registerEvents(new com.moonlight.coreprotect.essence.DashSwordListener(this), this);
        getServer().getPluginManager().registerEvents(new com.moonlight.coreprotect.essence.EarthquakeSwordListener(this), this);
        getServer().getPluginManager().registerEvents(new com.moonlight.coreprotect.shadowhunter.ErranteHelmetListener(this), this);

        // Player Warps System
        playerWarpManager = new com.moonlight.coreprotect.warps.PlayerWarpManager(this);
        playerWarpGUI = new com.moonlight.coreprotect.warps.PlayerWarpGUI(this);
        com.moonlight.coreprotect.warps.PlayerWarpCommand pwarpCmd = new com.moonlight.coreprotect.warps.PlayerWarpCommand(this);
        getCommand("pwarp").setExecutor(pwarpCmd);
        getCommand("pwarp").setTabCompleter(pwarpCmd);
        getCommand("pwarps").setExecutor(pwarpCmd);
        getServer().getPluginManager().registerEvents(playerWarpGUI, this);

        // Kits GUI
        KitsCommand kitsCmd = new KitsCommand(this);
        getCommand("kits").setExecutor(kitsCmd);
        getServer().getPluginManager().registerEvents(kitsCmd, this);
        getServer().getPluginManager().registerEvents(new com.moonlight.coreprotect.kits.KitAbilitiesListener(this), this);

        // /artistdagu command
        getCommand("artistdagu").setExecutor(new ArtistDaguCommand(this));

        // EvoCore - Sistema de equilibrio dinámico + web dashboard
        evoCore = new EvoCore(this);
        getServer().getPluginManager().registerEvents(evoCore.getDataCollector(), this);

        // TPS Monitor - Sistema de monitoreo de rendimiento
        tpsMonitor = new TPSMonitor(this);
        getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
                tpsMonitor.onPlayerJoin(event.getPlayer());
            }

            @org.bukkit.event.EventHandler
            public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
                tpsMonitor.onPlayerQuit(event.getPlayer());
            }
        }, this);

        // PlaceholderAPI integration (optional)
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new WardstoneExpansion(this).register();
            new com.moonlight.coreprotect.bossarena.BossArenaPlaceholder(this).register();
            getLogger().info("[Wardstone] PlaceholderAPI detectado. Placeholders registrados.");
        }

        // Structure Generation System
        structureManager = new com.moonlight.coreprotect.structures.StructureManager(this);
        getServer().getPluginManager().registerEvents(structureManager, this);
        getServer().getPluginManager().registerEvents(new com.moonlight.coreprotect.structures.StructureListener(this), this);

        // Fishing System
        fishingManager = new com.moonlight.coreprotect.structures.FishingManager(this);
        getServer().getPluginManager().registerEvents(fishingManager, this);

        // Spawner Manager — custom 15-45s spawner timer with hologram progress bars
        spawnerManager = new com.moonlight.coreprotect.spawners.SpawnerManager(this);
        getServer().getPluginManager().registerEvents(spawnerManager, this);

        // Discord Bot Integration (desactivado)
        // discordcode command removed

        // MiniGames System
        miniGameManager = new com.moonlight.coreprotect.minigames.MiniGameManager(this);
        com.moonlight.coreprotect.minigames.MiniGameCommand miniGameCmd = new com.moonlight.coreprotect.minigames.MiniGameCommand(this);
        getCommand("wardgames").setExecutor(miniGameCmd);
        getCommand("wardgames").setTabCompleter(miniGameCmd);
        com.moonlight.coreprotect.minigames.JoinCommand joinCmd = new com.moonlight.coreprotect.minigames.JoinCommand(this);
        getCommand("join").setExecutor(joinCmd);
        getCommand("join").setTabCompleter(joinCmd);
        getCommand("minijuegos").setExecutor(joinCmd);
        getCommand("minijuegos").setTabCompleter(joinCmd);
        getCommand("mg").setExecutor(joinCmd);
        getCommand("mg").setTabCompleter(joinCmd);
        // /leave y /salir = salir directamente del minijuego
        getCommand("leave").setExecutor((sender, cmd, label, args) -> {
            if (sender instanceof org.bukkit.entity.Player) {
                org.bukkit.entity.Player p = (org.bukkit.entity.Player) sender;
                if (miniGameManager != null) {
                    miniGameManager.leavePlayer(p);
                } else {
                    p.sendMessage("§cSistema de minijuegos no disponible.");
                }
            }
            return true;
        });
        getServer().getPluginManager().registerEvents(new com.moonlight.coreprotect.minigames.MiniGameListener(this), this);

        // Resource Pack — enviar al unirse con recomendación
        getServer().getPluginManager().registerEvents(
                new com.moonlight.coreprotect.listeners.ResourcePackListener(this), this);

        // Cubee Tutorial — aviso de receta cerca de Cubees Dizzy
        getServer().getPluginManager().registerEvents(
                new com.moonlight.coreprotect.listeners.CubeeTutorialListener(this), this);

        // Survival Update System — lobby, RTP portals, dungeons
        com.moonlight.coreprotect.survivalupdate.SurvivalUpdateManager survivalUpdateManager =
                new com.moonlight.coreprotect.survivalupdate.SurvivalUpdateManager(this);
        survivalUpdateManager.ensureLobbyLoaded();
        com.moonlight.coreprotect.survivalupdate.SurvivalUpdateCommand survivalUpdateCmd =
                new com.moonlight.coreprotect.survivalupdate.SurvivalUpdateCommand(this, survivalUpdateManager);
        getCommand("survivalupdate").setExecutor(survivalUpdateCmd);
        getCommand("survivalupdate").setTabCompleter(survivalUpdateCmd);
        getServer().getPluginManager().registerEvents(
                new com.moonlight.coreprotect.survivalupdate.SurvivalUpdateListener(this, survivalUpdateManager), this);

        // Crafting Recipes — Bocadillo Cubee
        CubeeSnackRecipe.register();

        // MythicMobs Natural Spawning — gestionado nativamente por MythicMobs
        // vía MythicMobs/randomspawns/cubees_spawns.yml (sin Java adicional).

        // Daily Streak System
        dailyStreakManager = new com.moonlight.coreprotect.streak.DailyStreakManager(this);
        com.moonlight.coreprotect.streak.DailyStreakCommand streakCmd = new com.moonlight.coreprotect.streak.DailyStreakCommand(this);
        getCommand("racha").setExecutor(streakCmd);
        getCommand("streak").setExecutor(streakCmd);
        getCommand("daily").setExecutor(streakCmd);
        getServer().getPluginManager().registerEvents(new com.moonlight.coreprotect.streak.DailyStreakListener(this), this);

        // PlayTime GUI (/tiempojugado)
        com.moonlight.coreprotect.rewards.PlayTimeCommand playTimeCmd = new com.moonlight.coreprotect.rewards.PlayTimeCommand(this);
        getCommand("tiempojugado").setExecutor(playTimeCmd);
        getServer().getPluginManager().registerEvents(new com.moonlight.coreprotect.rewards.PlayTimeGUIListener(this), this);

        // Prestige System
        prestigeManager = new com.moonlight.coreprotect.prestige.PrestigeManager(this);
        com.moonlight.coreprotect.prestige.PrestigeCommand prestigeCmd = new com.moonlight.coreprotect.prestige.PrestigeCommand(this);
        getCommand("prestigio").setExecutor(prestigeCmd);
        getCommand("prestige").setExecutor(prestigeCmd);
        getServer().getPluginManager().registerEvents(new com.moonlight.coreprotect.prestige.PrestigeListener(this), this);

        // Prestige Particles — walking effects per tier
        new com.moonlight.coreprotect.prestige.PrestigeParticleTask(this);

        // Prestige Passive Abilities — haste, crop doubles, auto-smelt, regen, fall resist, double XP
        getServer().getPluginManager().registerEvents(new com.moonlight.coreprotect.prestige.PrestigePassiveListener(this), this);

        // Prestige Roulette — animated prize roulette on prestige up
        prestigeRouletteManager = new com.moonlight.coreprotect.prestige.PrestigeRouletteManager(this);
        getServer().getPluginManager().registerEvents(prestigeRouletteManager, this);

        // Prestige Custom Tracker — killstreak, prestige kills, biome discovery
        getServer().getPluginManager().registerEvents(new com.moonlight.coreprotect.prestige.PrestigeCustomTracker(this), this);

        // Prestige Co-op Tracker — collaborative mission tracking
        getServer().getPluginManager().registerEvents(new com.moonlight.coreprotect.prestige.PrestigeCoopTracker(this), this);

        // Prestige Weekly Missions — rotating missions for P30+
        prestigeWeeklyManager = new com.moonlight.coreprotect.prestige.PrestigeWeeklyManager(this);
        getServer().getPluginManager().registerEvents(prestigeWeeklyManager, this);

        // Pending Rewards Manager — /ruleta hub (must be before roulette managers)
        pendingRewardsManager = new com.moonlight.coreprotect.rewards.PendingRewardsManager(this);
        getServer().getPluginManager().registerEvents(pendingRewardsManager, this);
        getCommand("ruleta").setExecutor(pendingRewardsManager);

        // Daily Roulette — CS:GO-style dopamine roulette on login
        dailyRouletteManager = new com.moonlight.coreprotect.rewards.DailyRouletteManager(this);
        getServer().getPluginManager().registerEvents(dailyRouletteManager, this);

        // Global Events — random server-wide events every 30min with boss bar
        globalEventManager = new com.moonlight.coreprotect.events.GlobalEventManager(this);
        getServer().getPluginManager().registerEvents(globalEventManager, this);

        // Scratch Cards — rasca y gana from mob kills & mining
        scratchCardManager = new com.moonlight.coreprotect.events.ScratchCardManager(this);
        getServer().getPluginManager().registerEvents(scratchCardManager, this);

        // Playtime Roulette — auto-roulette every 30min online
        playtimeRouletteManager = new com.moonlight.coreprotect.rewards.PlaytimeRouletteManager(this);
        getServer().getPluginManager().registerEvents(playtimeRouletteManager, this);

        // Trade system
        com.moonlight.coreprotect.trading.TradeCommand tradeCmd = new com.moonlight.coreprotect.trading.TradeCommand(this);
        getCommand("trade").setExecutor(tradeCmd);
        getCommand("trade").setTabCompleter(tradeCmd);
        getServer().getPluginManager().registerEvents(tradeCmd, this);

        // Banhammer & PetarPC listener
        com.moonlight.coreprotect.koth.BanhammerListener banhammerListener = new com.moonlight.coreprotect.koth.BanhammerListener(this);
        getServer().getPluginManager().registerEvents(banhammerListener, this);

        // BlockEnd listener
        com.moonlight.coreprotect.koth.BlockEndListener blockEndListener = new com.moonlight.coreprotect.koth.BlockEndListener(this);
        getServer().getPluginManager().registerEvents(blockEndListener, this);

        // Tips System — broadcasts tips every 4 minutes
        tipsManager = new com.moonlight.coreprotect.tips.TipsManager(this);

        // BossBar Announcer — mensajes rotativos en la bossbar
        bossBarAnnouncer = new com.moonlight.coreprotect.tips.BossBarAnnouncer(this);
        getServer().getPluginManager().registerEvents(bossBarAnnouncer, this);

        // Nether Spawn Fixer — fuerza spawn de mobs del Nether
        com.moonlight.coreprotect.spawn.NetherSpawnFixer netherFixer = new com.moonlight.coreprotect.spawn.NetherSpawnFixer(this);
        getServer().getPluginManager().registerEvents(netherFixer, this);
        netherFixer.ensureNetherSettings();

        // Staff Inspect — /staffinspect [jugador]
        com.moonlight.coreprotect.commands.StaffInspectCommand staffInspectCmd =
                new com.moonlight.coreprotect.commands.StaffInspectCommand(this);
        getCommand("staffinspect").setExecutor(staffInspectCmd);
        getCommand("staffinspect").setTabCompleter(staffInspectCmd);
        getServer().getPluginManager().registerEvents(staffInspectCmd, this);

        // Actualizar distancia de visualización inmediatamente al iniciar
        if (protectionListener != null) {
            protectionListener.updateAllWorldsAndPlayersViewDistance();
        }

        // ProxiChat — iniciar servidor web de voz
        proxiChatManager = new com.moonlight.coreprotect.voicechat.ProxiChatManager(this);
        proxiChatManager.start();

        // Mice — /ratones crafteo de queso Swiss
        com.moonlight.coreprotect.mice.MiceCommand miceCmd = new com.moonlight.coreprotect.mice.MiceCommand(this);
        getCommand("ratones").setExecutor(miceCmd);
        getServer().getPluginManager().registerEvents(miceCmd, this);

        // Owner Kit — kit OP exclusivo para OPs
        getCommand("ownerkit").setExecutor(new com.moonlight.coreprotect.kits.OwnerKitCommand(this));
        com.moonlight.coreprotect.kits.OwnerKitListener ownerKitListener = new com.moonlight.coreprotect.kits.OwnerKitListener(this);
        getServer().getPluginManager().registerEvents(ownerKitListener, this);
        getServer().getScheduler().runTaskTimer(this, () -> ownerKitListener.tickPassives(), 20L, 20L);

        getLogger().info("CoreProtect habilitado correctamente");
    }

    @Override
    public void onDisable() {
        // Guardar mundo KOTH antes de apagar
        if (kothManager != null) {
            kothManager.shutdown();
            // Guardado forzado del mundo KOTH
            org.bukkit.World kothWorld = org.bukkit.Bukkit.getWorld("koth");
            if (kothWorld != null) {
                kothWorld.save();
                getLogger().info("[KOTH] Mundo KOTH guardado forzadamente antes de apagar el servidor.");
            }
        }
        if (combatTagManager != null) {
            combatTagManager.cleanup();
        }
        if (auctionManager != null) {
            auctionManager.shutdown();
        }
        if (bountyManager != null) {
            bountyManager.shutdown();
        }
        if (essenceManager != null) {
            essenceManager.shutdown();
        }
        if (spawnerManager != null) {
            spawnerManager.shutdown();
        }
        if (shadowHunterManager != null) {
            shadowHunterManager.shutdown();
        }
        if (voidLeggingsListener != null) {
            voidLeggingsListener.cleanup();
        }
        if (structureManager != null) {
            structureManager.shutdown();
        }
        if (fishingManager != null) {
            fishingManager.shutdown();
        }
        if (evoCore != null) {
            evoCore.shutdown();
        }
        if (tpsMonitor != null) {
            tpsMonitor.shutdown();
        }
        if (discordBotManager != null) {
            discordBotManager.stop();
        }
        if (spawnCollisionManager != null) {
            spawnCollisionManager.cleanup();
        }
        if (miniGameManager != null) {
            miniGameManager.shutdown();
        }
        if (dailyStreakManager != null) {
            dailyStreakManager.shutdown();
        }
        if (prestigeManager != null) {
            prestigeManager.shutdown();
        }
        if (prestigeWeeklyManager != null) {
            prestigeWeeklyManager.saveData();
        }
        if (tipsManager != null) {
            tipsManager.shutdown();
        }
        if (bossBarAnnouncer != null) {
            bossBarAnnouncer.shutdown();
        }
        if (dailyRouletteManager != null) {
            dailyRouletteManager.saveData();
        }
        if (pendingRewardsManager != null) {
            pendingRewardsManager.saveData();
        }
        if (globalEventManager != null) {
            globalEventManager.shutdown();
        }
        if (playerWarpManager != null) {
            playerWarpManager.shutdown();
        }
        if (proxiChatManager != null) {
            proxiChatManager.stop();
        }
        if (dataManager != null) {
            dataManager.saveData();
        }
        getLogger().info("CoreProtect deshabilitado");
    }

    private void migrateDataFromOldFolder() {
        // Possible old folder names from previous plugin name
        String[] oldNames = { "CoreProtect", "coreprotect" };
        File currentFolder = getDataFolder();

        for (String oldName : oldNames) {
            File oldFolder = new File(currentFolder.getParentFile(), oldName);
            if (!oldFolder.exists() || oldFolder.equals(currentFolder))
                continue;

            // Migrate data.yml
            File oldData = new File(oldFolder, "data.yml");
            File newData = new File(currentFolder, "data.yml");
            if (oldData.exists() && !newData.exists()) {
                try {
                    currentFolder.mkdirs();
                    Files.copy(oldData.toPath(), newData.toPath());
                    getLogger().info("MIGRADOR: Copiado data.yml desde " + oldFolder.getName() + " -> "
                            + currentFolder.getName());
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

    public CombatTagManager getCombatTagManager() {
        return combatTagManager;
    }

    public BountyManager getBountyManager() {
        return bountyManager;
    }

    public AfkZoneManager getAfkZoneManager() {
        return afkZoneManager;
    }

    public EvoCore getEvoCore() {
        return evoCore;
    }

    public KothManager getKothManager() {
        return kothManager;
    }

    public com.moonlight.coreprotect.koth.KothCommand getKothCommand() {
        return kothCommand;
    }

    public FinisherManager getFinisherManager() {
        return finisherManager;
    }

    public FinisherListener getFinisherListener() {
        return finisherListener;
    }

    public TPSMonitor getTpsMonitor() {
        return tpsMonitor;
    }

    public ShadowHunterManager getShadowHunterManager() {
        return shadowHunterManager;
    }

    public SkyParkourListener getSkyParkourListener() {
        return skyParkourListener;
    }

    public PrivateVaultManager getPrivateVaultManager() {
        return privateVaultManager;
    }

    public com.moonlight.coreprotect.bossarena.BossArenaManager getBossArenaManager() {
        return bossArenaManager;
    }

    public PlayTimeRewardsManager getPlayTimeRewardsManager() {
        return playTimeRewardsManager;
    }

    public EssenceManager getEssenceManager() {
        return essenceManager;
    }

    public com.moonlight.coreprotect.structures.StructureManager getStructureManager() {
        return structureManager;
    }

    public com.moonlight.coreprotect.structures.FishingManager getFishingManager() {
        return fishingManager;
    }

    public ProtectionListener getProtectionListener() {
        return protectionListener;
    }

    public com.moonlight.coreprotect.discord.DiscordBotManager getDiscordBotManager() {
        return discordBotManager;
    }

    public void setDiscordBotManager(com.moonlight.coreprotect.discord.DiscordBotManager manager) {
        this.discordBotManager = manager;
    }

    public com.moonlight.coreprotect.minigames.MiniGameManager getMiniGameManager() {
        return miniGameManager;
    }

    public com.moonlight.coreprotect.core.CorePrestigeManager getCorePrestigeManager() {
        return corePrestigeManager;
    }

    public com.moonlight.coreprotect.streak.DailyStreakManager getDailyStreakManager() {
        return dailyStreakManager;
    }

    public com.moonlight.coreprotect.prestige.PrestigeManager getPrestigeManager() {
        return prestigeManager;
    }

    public com.moonlight.coreprotect.prestige.PrestigeRouletteManager getPrestigeRouletteManager() {
        return prestigeRouletteManager;
    }

    public com.moonlight.coreprotect.prestige.PrestigeWeeklyManager getPrestigeWeeklyManager() {
        return prestigeWeeklyManager;
    }

    public com.moonlight.coreprotect.tips.TipsManager getTipsManager() {
        return tipsManager;
    }

    public com.moonlight.coreprotect.warps.PlayerWarpManager getPlayerWarpManager() {
        return playerWarpManager;
    }

    public com.moonlight.coreprotect.warps.PlayerWarpGUI getPlayerWarpGUI() {
        return playerWarpGUI;
    }

    public com.moonlight.coreprotect.rewards.DailyRouletteManager getDailyRouletteManager() {
        return dailyRouletteManager;
    }

    public com.moonlight.coreprotect.events.GlobalEventManager getGlobalEventManager() {
        return globalEventManager;
    }

    public com.moonlight.coreprotect.events.ScratchCardManager getScratchCardManager() {
        return scratchCardManager;
    }

    public com.moonlight.coreprotect.rewards.PendingRewardsManager getPendingRewardsManager() {
        return pendingRewardsManager;
    }

    public com.moonlight.coreprotect.rewards.PlaytimeRouletteManager getPlaytimeRouletteManager() {
        return playtimeRouletteManager;
    }


    // ==================== PRESTIGE MONEY MULTIPLIER ====================

    /**
     * Deposits money to a player with prestige multiplier applied.
     * Use this instead of economy.depositPlayer() for earnings (not refunds/trades).
     */
    public void depositWithMultiplier(org.bukkit.entity.Player player, double amount) {
        if (economy == null) return;
        economy.depositPlayer(player, amount);
    }

    public void depositWithMultiplier(org.bukkit.OfflinePlayer player, double amount) {
        if (economy == null) return;
        economy.depositPlayer(player, amount);
    }

    // ==================== FORCE MAX ENTITY CRAMMING ====================

    private void forceMaxEntityCramming() {
        // Set maxEntityCramming to 24 on all worlds immediately
        for (org.bukkit.World world : getServer().getWorlds()) {
            world.setGameRule(org.bukkit.GameRule.MAX_ENTITY_CRAMMING, 24);
            getLogger().info("[CRAMMING] Set maxEntityCramming=24 on world: " + world.getName());
        }

        // Register listener to force it on world load (override other plugins)
        getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
            public void onWorldLoad(org.bukkit.event.world.WorldLoadEvent event) {
                event.getWorld().setGameRule(org.bukkit.GameRule.MAX_ENTITY_CRAMMING, 24);
                getLogger().info("[CRAMMING] Forced maxEntityCramming=24 on world load: " + event.getWorld().getName());
            }

            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
            public void onWorldInit(org.bukkit.event.world.WorldInitEvent event) {
                event.getWorld().setGameRule(org.bukkit.GameRule.MAX_ENTITY_CRAMMING, 24);
                getLogger().info("[CRAMMING] Forced maxEntityCramming=24 on world init: " + event.getWorld().getName());
            }
        }, this);

        // Periodic enforcement every 5 minutes (override any changes)
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (org.bukkit.World world : getServer().getWorlds()) {
                Integer current = world.getGameRuleValue(org.bukkit.GameRule.MAX_ENTITY_CRAMMING);
                if (current == null || current != 24) {
                    world.setGameRule(org.bukkit.GameRule.MAX_ENTITY_CRAMMING, 24);
                    getLogger().warning("[CRAMMING] Restored maxEntityCramming=24 on world: " + world.getName() + " (was " + current + ")");
                }
            }
        }, 6000L, 6000L); // Every 5 minutes
    }
}
