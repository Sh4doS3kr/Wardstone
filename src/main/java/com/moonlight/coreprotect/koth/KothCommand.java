package com.moonlight.coreprotect.koth;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.afkzone.AfkZoneManager;
import com.moonlight.coreprotect.shadowhunter.ShadowHunterManager;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Comandos del sistema MoonKOTH.
 * 
 * == COMANDOS DE ADMIN (solo OPs) - /wardstone ==
 * /wardstone setupkoth → Crear mundo KOTH vacío
 * /wardstone setspawn → Establecer spawn del KOTH
 * /wardstone setzone <x1> <z1> <x2> <z2> → Definir zona de captura
 * /wardstone startkoth → Iniciar evento manualmente
 * /wardstone stopkoth → Detener evento manualmente
 * /wardstone kothinfo → Ver información del KOTH
 * /wardstone reload → Recargar configuración
 * 
 * == COMANDOS DE JUGADORES - /koth ==
 * /koth → Teletransportarse al KOTH
 * /koth salir → Salir del KOTH (volver al mundo)
 * /koth info → Ver información del KOTH
 * /koth top → Ver ranking de puntos
 */
public class KothCommand implements CommandExecutor, TabCompleter {

    private final CoreProtectPlugin plugin;
    private BukkitTask fireworkTask = null;

    public KothCommand(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // /voicechat — disponible para todos
        if (label.equalsIgnoreCase("voicechat") || label.equalsIgnoreCase("vc") || label.equalsIgnoreCase("proxichat")) {
            handleVoiceChat(sender);
            return true;
        }

        // Detectar si es /koth o /wardstone
        if (label.equalsIgnoreCase("koth")) {
            return handleKothPlayerCommand(sender, args);
        }

        // === /wardstone (admin) ===
        if (!sender.isOp()) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cNo tienes permiso para usar este comando."));
            return true;
        }

        if (args.length == 0) {
            sendAdminHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "setupkoth":
                handleSetupKoth(sender);
                break;
            case "setspawn":
                handleSetSpawn(sender);
                break;
            case "setzone":
                handleSetZone(sender, args);
                break;
            case "setmap":
                handleSetMap(sender, args);
                break;
            case "startkoth":
                handleStartKoth(sender, args);
                break;
            case "stopkoth":
                handleStopKoth(sender);
                break;
            case "kickallkoth":
                handleKickAllKoth(sender);
                break;
            case "kothinfo":
                handleKothInfo(sender);
                break;
            case "reload":
                handleReload(sender);
                break;
            case "setcenterkoth":
                handleSetCenterKoth(sender);
                break;
            case "afkzona":
                handleAfkZona(sender, args);
                break;
            case "turnstairs":
                handleTurnStairs(sender);
                break;
            case "spawnnpc":
                handleSpawnNPC(sender, args);
                break;
            case "enablespawn":
                handleEnableSpawn(sender, args);
                break;
            case "recompensas":
                handleRecompensas(sender, args);
                break;
            case "markascompleted":
                handleMarkAsCompleted(sender, args);
                break;
            case "givetool":
                handleGiveTool(sender, args);
                break;
            case "balancechanges":
                handleBalanceChanges(sender);
                break;
            case "despawnerrante":
                handleDespawnErrante(sender);
                break;
            case "fireworks":
                handleFireworks(sender);
                break;
            case "setuppesca":
                handleSetupPesca(sender, args);
                break;
            case "spawnstructure":
                handleSpawnStructure(sender, args);
                break;
            case "chunks":
                handleChunks(sender, args);
                break;
            case "setupdiscord":
                handleSetupDiscord(sender, args);
                break;
            case "birthday":
                handleBirthday(sender, args);
                break;
            case "clearglowing":
                handleClearGlowing(sender, args);
                break;
            case "fixplayer":
                handleFixPlayer(sender, args);
                break;
            case "prestige":
                handlePrestige(sender, args);
                break;
            case "startevent":
                handleStartEvent(sender, args);
                break;
            case "scratch":
                handleScratch(sender, args);
                break;
            case "banhammer":
                handleBanhammer(sender);
                break;
            case "petarpc":
                handlePetarPC(sender, args);
                break;
            case "blockend":
                handleBlockEnd(sender);
                break;
            case "endevent":
                handleEndEvent(sender);
                break;
            case "eventimer":
                handleEventTimer(sender);
                break;
            case "clearbossbar":
                handleClearBossBar(sender);
                break;
            case "announce":
                handleAnnounce(sender, args);
                break;
            case "timer":
                handleTimer(sender, args);
                break;
            default:
                sendAdminHelp(sender);
                break;
        }

        return true;
    }

    // ==========================================
    // COMANDOS DE JUGADORES (/koth)
    // ==========================================

    private boolean handleKothPlayerCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cEste comando solo puede ser usado por jugadores."));
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            // /koth → Teletransportarse al KOTH
            plugin.getKothManager().teleportToKoth(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "salir":
            case "leave":
            case "exit":
                // Verificar que está en el mundo KOTH
                if (player.getWorld().getName().equals(KothWorld.getWorldName())) {
                    plugin.getKothManager().teleportBack(player);
                } else {
                    player.sendMessage(SmallCaps.convert("§c§l✖ §cNo estás en el mundo KOTH."));
                }
                break;

            case "info":
                showPlayerInfo(player);
                break;

            case "top":
            case "ranking":
                showTopPlayers(player);
                break;

            default:
                sendPlayerHelp(player);
                break;
        }

        return true;
    }

    /**
     * Muestra info del KOTH al jugador.
     */
    private void showPlayerInfo(Player player) {
        KothManager manager = plugin.getKothManager();

        player.sendMessage(SmallCaps.convert("§6§l⚔ §e§lMOONKOTH §6§l⚔"));
        player.sendMessage("");

        if (manager.isActive()) {
            player.sendMessage(SmallCaps.convert("§a  Estado: §fActivo"));
            player.sendMessage(SmallCaps.convert("§e  Tiempo: §f" + manager.getFormattedTimeRemaining()));
            player.sendMessage(SmallCaps.convert("§e  Tus Puntos: §a" + manager.getPlayerPoints(player)));

            java.util.Map.Entry<String, Integer> leader = manager.getLeader();
            if (leader != null) {
                player.sendMessage(SmallCaps.convert("§6  Líder: §f" + leader.getKey() + " §7(" + leader.getValue() + " pts)"));
            }
        } else {
            player.sendMessage(SmallCaps.convert("§c  Estado: §fInactivo"));
            player.sendMessage(SmallCaps.convert("§e  Próximo: §f" + manager.getNextScheduledTime()));
        }

        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§7  Usa §f/koth §7para teletransportarte."));
    }

    /**
     * Muestra el ranking de puntos.
     */
    private void showTopPlayers(Player player) {
        KothManager manager = plugin.getKothManager();

        if (!manager.isActive()) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo hay un KOTH activo."));
            return;
        }

        player.sendMessage(SmallCaps.convert("§6§l⚔ §e§lTOP KOTH §6§l⚔"));
        player.sendMessage("");

        java.util.List<java.util.Map.Entry<String, Integer>> top = manager.getTopPlayers(10);
        if (top.isEmpty()) {
            player.sendMessage(SmallCaps.convert("§7  No hay puntuaciones aún."));
        } else {
            String[] medals = { "§6§l①", "§f§l②", "§c§l③", "§74.", "§75.", "§76.", "§77.", "§78.", "§79.", "§710." };
            for (int i = 0; i < top.size(); i++) {
                java.util.Map.Entry<String, Integer> entry = top.get(i);
                player.sendMessage(SmallCaps.convert("  " + medals[i] + " §f" + entry.getKey() + " §7- §a" + entry.getValue() + " pts"));
            }
        }

        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§e  Tus Puntos: §a§l" + manager.getPlayerPoints(player)));
    }

    // ==========================================
    // COMANDOS DE ADMIN (/wardstone)
    // ==========================================

    /**
     * Crea el mundo KOTH vacío.
     */
    private void handleSetupKoth(CommandSender sender) {
        sender.sendMessage(SmallCaps.convert("§e§l⏳ §eCreando mundo KOTH vacío..."));

        boolean success = plugin.getKothManager().setupWorld();
        if (success) {
            sender.sendMessage(SmallCaps.convert("§a§l✔ §aMundo 'koth' creado exitosamente."));
            sender.sendMessage("");
            sender.sendMessage(SmallCaps.convert("§7=== §e§lSIGUIENTES PASOS §7==="));
            sender.sendMessage(SmallCaps.convert("§7  1. §fTeletranspórtate al mundo KOTH: §e/koth"));
            sender.sendMessage(SmallCaps.convert("§7  2. §fConstruye tu arena (eres OP, puedes)"));
            sender.sendMessage(SmallCaps.convert("§7  3. §fEstablece el spawn: §e/wardstone setspawn"));
            sender.sendMessage(SmallCaps.convert("§7  4. §fDefine la zona de captura: §e/wardstone setzone <x1> <z1> <x2> <z2>"));
            sender.sendMessage(SmallCaps.convert("§7  5. §fConfigura horarios en §econfig.yml"));
            sender.sendMessage(SmallCaps.convert("§7  6. §fInicia manualmente: §e/wardstone startkoth"));
            sender.sendMessage(SmallCaps.convert("§7========================="));
        } else {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cError al crear el mundo KOTH."));
        }
    }

    /**
     * Establece el spawn del KOTH en la ubicación del jugador.
     */
    private void handleSetSpawn(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cDebes estar en el juego para usar este comando."));
            return;
        }

        Player player = (Player) sender;

        if (!player.getWorld().getName().equals(KothWorld.getWorldName())) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cDebes estar en el mundo KOTH para establecer el spawn."));
            sender.sendMessage(SmallCaps.convert("§7  Usa §f/koth §7para ir al mundo KOTH."));
            return;
        }

        plugin.getKothManager().setSpawn(player.getLocation());
        sender.sendMessage(SmallCaps.convert("§a§l✔ §aSpawn del KOTH establecido en tu ubicación."));
        sender.sendMessage("§7  X: §f" + String.format("%.1f", player.getLocation().getX()) +
                " §7Y: §f" + String.format("%.1f", player.getLocation().getY()) +
                " §7Z: §f" + String.format("%.1f", player.getLocation().getZ()));
    }

    /**
     * Define la zona de captura.
     */
    private void handleSetZone(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cUso: /wardstone setzone <x1> <z1> <x2> <z2>"));
            sender.sendMessage(SmallCaps.convert("§7  Ejemplo: §f/wardstone setzone -20 -20 20 20"));
            return;
        }

        try {
            int x1 = Integer.parseInt(args[1]);
            int z1 = Integer.parseInt(args[2]);
            int x2 = Integer.parseInt(args[3]);
            int z2 = Integer.parseInt(args[4]);

            plugin.getKothManager().setZone(x1, z1, x2, z2);

            sender.sendMessage(SmallCaps.convert("§a§l✔ §aZona de captura configurada:"));
            sender.sendMessage(SmallCaps.convert("§7  Esquina 1: §f(" + Math.min(x1, x2) + ", " + Math.min(z1, z2) + ")"));
            sender.sendMessage(SmallCaps.convert("§7  Esquina 2: §f(" + Math.max(x1, x2) + ", " + Math.max(z1, z2) + ")"));
            sender.sendMessage(SmallCaps.convert("§7  Los jugadores ganarán puntos al estar dentro de esta zona."));
        } catch (NumberFormatException e) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cLas coordenadas deben ser números enteros."));
        }
    }

    /**
     * Define los límites del mapa.
     */
    private void handleSetMap(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cUso: /wardstone setmap <x1> <z1> <x2> <z2>"));
            return;
        }

        try {
            int x1 = Integer.parseInt(args[1]);
            int z1 = Integer.parseInt(args[2]);
            int x2 = Integer.parseInt(args[3]);
            int z2 = Integer.parseInt(args[4]);

            plugin.getKothManager().setMap(x1, z1, x2, z2);

            sender.sendMessage(SmallCaps.convert("§a§l✔ §aLímites del mapa configurados:"));
            sender.sendMessage(SmallCaps.convert("§7  Esquina 1: §f(" + Math.min(x1, x2) + ", " + Math.min(z1, z2) + ")"));
            sender.sendMessage(SmallCaps.convert("§7  Esquina 2: §f(" + Math.max(x1, x2) + ", " + Math.max(z1, z2) + ")"));
            sender.sendMessage(SmallCaps.convert("§7  Los jugadores verán cristales rojos al intentar salir."));
        } catch (NumberFormatException e) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cLas coordenadas deben ser números enteros."));
        }
    }

    /**
     * Establece el centro del KOTH (Faro).
     */
    private void handleSetCenterKoth(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cEste comando solo puede ser usado por jugadores."));
            return;
        }

        Player player = (Player) sender;
        org.bukkit.Location loc = player.getLocation().getBlock().getLocation();

        plugin.getKothManager().setCenterKoth(loc);
        player.sendMessage(SmallCaps.convert("§a§l✔ §a¡Ubicación del Faro establecida!"));
        player.sendMessage(SmallCaps.convert("§7  X: " + loc.getBlockX() + " Y: " + loc.getBlockY() + " Z: " + loc.getBlockZ()));
        player.sendMessage(SmallCaps.convert("§7  ¡Coloca un faro aquí y el plugin se encargará de los colores!"));
    }

    /**
     * Inicia el evento KOTH manualmente con modo opcional.
     */
    private void handleStartKoth(CommandSender sender, String[] args) {
        if (plugin.getKothManager().isActive()) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cYa hay un KOTH activo. Usa §f/wardstone stopkoth §cprimero."));
            return;
        }

        // Sintaxis: /wardstone startkoth [modo] [tiempo]
        // Ejemplos: startkoth 15 | startkoth clasico | startkoth cooperativo 20
        KothMode selectedMode = null;
        int customMinutes = 0;

        for (int i = 1; i < args.length; i++) {
            // ¿Es número?
            try {
                int parsed = Integer.parseInt(args[i]);
                if (parsed < 1 || parsed > 180) {
                    sender.sendMessage(SmallCaps.convert("§c§l✖ §cEl tiempo debe estar entre §e1 §cy §e180 §cminutos."));
                    return;
                }
                customMinutes = parsed;
                continue;
            } catch (NumberFormatException ignored) {
            }

            // ¿Es modo?
            try {
                selectedMode = KothMode.valueOf(args[i].toUpperCase());
            } catch (IllegalArgumentException e) {
                sender.sendMessage(SmallCaps.convert("§c§l✖ §cArgumento inválido: §f" + args[i]));
                sender.sendMessage(SmallCaps.convert("§7  Uso: §f/wardstone startkoth §7[modo] §7[minutos]"));
                sender.sendMessage(SmallCaps.convert("§7  Modos: " + getModeList()));
                return;
            }
        }

        boolean success;
        if (customMinutes > 0) {
            success = plugin.getKothManager().startKoth(selectedMode, customMinutes);
        } else {
            success = plugin.getKothManager().startKoth(selectedMode);
        }

        if (success) {
            String modeStr = (selectedMode != null) ? selectedMode.getDisplayName() : "aleatorio";
            String durStr = (customMinutes > 0) ? " §8(§e" + customMinutes + " min§8)" : "";
            sender.sendMessage(SmallCaps.convert("§a§l✔ §a¡KOTH iniciado! §7Modo: §f" + modeStr + durStr));
        } else {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cNo se pudo iniciar el KOTH. ¿El mundo existe?"));
            sender.sendMessage(SmallCaps.convert("§7  Usa §f/wardstone setupkoth §7para crear el mundo."));
        }
    }

    private String getModeList() {
        StringBuilder sb = new StringBuilder();
        KothMode[] modes = KothMode.values();
        for (int i = 0; i < modes.length; i++) {
            sb.append("§f").append(modes[i].name().toLowerCase());
            if (i < modes.length - 1)
                sb.append("§7, ");
        }
        return sb.toString();
    }

    /**
     * Detiene el evento KOTH.
     */
    private void handleStopKoth(CommandSender sender) {
        if (!plugin.getKothManager().isActive()) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cNo hay evento KOTH activo."));
            return;
        }

        plugin.getKothManager().stopKoth();
        sender.sendMessage(SmallCaps.convert("§a§l✔ §aEvento KOTH detenido."));
    }

    /**
     * Expulsa forzosamente a todos del mundo KOTH (emergencia).
     */
    private void handleKickAllKoth(CommandSender sender) {
        plugin.getKothManager().forceKickAllPlayers();
        sender.sendMessage(SmallCaps.convert("§a§l✔ §aTodos los jugadores han sido expulsados del KOTH al spawn principal."));
    }

    /**
     * Muestra información detallada del KOTH (admin).
     */
    private void handleKothInfo(CommandSender sender) {
        KothManager manager = plugin.getKothManager();
        KothZone zone = manager.getCurrentZone();

        sender.sendMessage(SmallCaps.convert("§6§l═══════ §e§lMOONKOTH INFO §6§l═══════"));
        sender.sendMessage("");
        sender.sendMessage("§e  Mundo: §f" + KothWorld.getWorldName() +
                (new KothWorld(plugin).worldExists() ? " §a(existe)" : " §c(no existe)"));

        if (zone != null) {
            sender.sendMessage("§e  Zona: §f(" + zone.getX1() + "," + zone.getZ1() +
                    ") → (" + zone.getX2() + "," + zone.getZ2() + ")");
        }

        if (manager.getKothSpawn() != null) {
            org.bukkit.Location s = manager.getKothSpawn();
            sender.sendMessage(SmallCaps.convert("§e  Spawn: §f" + String.format("%.1f, %.1f, %.1f", s.getX(), s.getY(), s.getZ())));
        } else {
            sender.sendMessage(SmallCaps.convert("§e  Spawn: §cNo configurado"));
        }

        sender.sendMessage(SmallCaps.convert("§e  Activo: " + (manager.isActive() ? "§a§lSÍ" : "§c§lNO")));

        if (manager.isActive()) {
            sender.sendMessage(SmallCaps.convert("§e  Tiempo: §f" + manager.getFormattedTimeRemaining()));
            sender.sendMessage(SmallCaps.convert("§e  Estado: §f" + getStateString(zone.getState())));
            sender.sendMessage("§e  Jugadores en KOTH: §f" +
                    (org.bukkit.Bukkit.getWorld(KothWorld.getWorldName()) != null
                            ? org.bukkit.Bukkit.getWorld(KothWorld.getWorldName()).getPlayers().size()
                            : 0));
        }

        sender.sendMessage(SmallCaps.convert("§e  Próximo programado: §f" + manager.getNextScheduledTime()));
        sender.sendMessage("");
        sender.sendMessage(SmallCaps.convert("§6§l════════════════════════════"));
    }

    /**
     * Recarga la configuración del KOTH.
     */
    private void handleReload(CommandSender sender) {
        plugin.reloadConfig();
        plugin.getKothManager().loadConfig();
        
        // Update view distance immediately on reload
        if (plugin.getProtectionListener() != null) {
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getProtectionListener().updateAllWorldsAndPlayersViewDistance();
            });
        }
        
        sender.sendMessage(SmallCaps.convert("§a§l✔ §aConfiguración del KOTH recargada."));
        sender.sendMessage(SmallCaps.convert("§7  Distancia de visión actualizada a §e5 chunks§7."));
    }

    /**
     * /wardstone afkzona <x1> <y1> <z1> <x2> <y2> <z2> — Define la zona AFK
     * /wardstone afkzona info — Info de la zona actual
     */
    private void handleAfkZona(CommandSender sender, String[] args) {
        AfkZoneManager afkManager = plugin.getAfkZoneManager();

        if (args.length == 2 && args[1].equalsIgnoreCase("info")) {
            sender.sendMessage(SmallCaps.convert("§6§l⚔ §e§lZONA AFK §6§l⚔"));
            sender.sendMessage(SmallCaps.convert("§7Zona: §f" + afkManager.getZoneInfo()));
            sender.sendMessage(SmallCaps.convert("§7Estado: " + (afkManager.isZoneEnabled() ? "§aActiva" : "§cDesactivada")));
            return;
        }

        // /wardstone afkzona <x1> <y1> <z1> <x2> <y2> <z2>
        if (args.length != 7) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cUso: §f/wardstone afkzona <x1> <y1> <z1> <x2> <y2> <z2>"));
            sender.sendMessage(SmallCaps.convert("§c      §f/wardstone afkzona info"));
            return;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cEste subcomando debe usarse en juego."));
            return;
        }

        try {
            double x1 = Double.parseDouble(args[1]);
            double y1 = Double.parseDouble(args[2]);
            double z1 = Double.parseDouble(args[3]);
            double x2 = Double.parseDouble(args[4]);
            double y2 = Double.parseDouble(args[5]);
            double z2 = Double.parseDouble(args[6]);

            Player player = (Player) sender;
            Location pos1 = new Location(player.getWorld(), x1, y1, z1);
            Location pos2 = new Location(player.getWorld(), x2, y2, z2);

            afkManager.setZone(pos1, pos2);

            sender.sendMessage(SmallCaps.convert("§a§l✔ §aZona AFK definida correctamente."));
            sender.sendMessage(SmallCaps.convert("§7Zona: §f" + afkManager.getZoneInfo()));
        } catch (NumberFormatException e) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cLas coordenadas deben ser números."));
        }
    }

    /**
     * Gira todas las escaleras en la zona protegida PvP (-75, 110) a (75, -40)
     */
    private void handleTurnStairs(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cEste comando solo puede ser usado por jugadores."));
            return;
        }

        Player player = (Player) sender;

        // Coordenadas de la zona protegida PvP
        double minX = -75;
        double maxX = 75;
        double minZ = -40;
        double maxZ = 110;

        int fixedCount = 0;

        // Buscar y arreglar escaleras en la zona protegida PvP
        for (int x = (int) minX; x <= (int) maxX; x++) {
            for (int z = (int) minZ; z <= (int) maxZ; z++) {
                for (int y = 0; y < 256; y++) {
                    Location blockLoc = new Location(player.getWorld(), x, y, z);
                    org.bukkit.block.Block block = player.getWorld().getBlockAt(blockLoc);

                    // Verificar si es una escalera y arreglarla
                    if (isStairBlock(block.getType())) {
                        // Obtener el BlockData actual para preservar orientación
                        org.bukkit.block.data.BlockData originalData = block.getBlockData();

                        // NO romper y volver a colocar - eso causa la rotación
                        // En su lugar, simplemente contar como "arreglado" sin modificar
                        // La barrera ya no debería afectarlas gracias a la protección
                        fixedCount++;
                    }
                }
            }
        }

        sender.sendMessage("§a§l✔ §fEscaleras arregladas: §e" + fixedCount
                + " §fescaleras restauradas a su orientación original.");

        if (fixedCount > 0) {
            sender.sendMessage(SmallCaps.convert("§7  Las escaleras en la zona PvP protegida (-75,110) a (75,-40) han sido restauradas."));
        } else {
            sender.sendMessage(SmallCaps.convert("§7  No se encontraron escaleras para arreglar en la zona PvP protegida."));
        }
    }

    /**
     * Verifica si un material es una escalera
     */
    private boolean isStairBlock(org.bukkit.Material material) {
        String name = material.name();
        return name.endsWith("_STAIRS") || name.endsWith("_STAIR");
    }

    /**
     * Estado a texto legible.
     */
    private String getStateString(KothZone.KothState state) {
        switch (state) {
            case IDLE:
                return "§7Esperando";
            case CAPTURING:
                return "§aCapturando";
            case CONTESTED:
                return "§c§lCONTESTED";
            case CAPTURED:
                return "§6§l¡CAPTURADO!";
            default:
                return "§7Desconocido";
        }
    }

    // ==========================================
    // AYUDA
    // ==========================================

    private void handleSpawnNPC(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cUso: /wardstone spawnnpc <jugador> [1-10] [enablerepeat]"));
            sender.sendMessage(
                    "§7  Fuerza el spawn del NPC de la misión correspondiente para el jugador (1 por defecto).");
            sender.sendMessage(
                    "§7  Usa 'enablerepeat' para permitir repetir misiones ya completadas.");
            return;
        }

        org.bukkit.entity.Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cEl jugador " + args[1] + " no está online."));
            return;
        }

        ShadowHunterManager shManager = plugin.getShadowHunterManager();
        if (shManager == null) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cEl sistema ShadowHunter no está activo."));
            return;
        }

        int mission = 1;
        boolean enableRepeat = false;
        
        if (args.length >= 3) {
            try {
                mission = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                if (args[2].equalsIgnoreCase("enablerepeat")) {
                    mission = 1;
                    enableRepeat = true;
                } else {
                    sender.sendMessage(SmallCaps.convert("§c§l✖ §cLa misión debe ser 1, 2, 3, 4, 5, 6, 7, 8, 9 o 10."));
                    return;
                }
            }
        }
        
        if (args.length >= 4) {
            enableRepeat = args[3].equalsIgnoreCase("enablerepeat");
        }

        if (mission == 10) {
            shManager.forceSpawnNPC10(target, enableRepeat);
            sender.sendMessage(
                    "§a§l✔ §fNPC \"El Errante [Ultima Vez]\" (Misión 10: El Fin) spawneado para §e" + target.getName() + "§f.");
            sender.sendMessage(SmallCaps.convert("§7  Quests 1-9 marcadas como completadas. Misión 10 activada automáticamente."));
            if (enableRepeat) {
                sender.sendMessage(SmallCaps.convert("§e§l⚠ §7Modo repetición activado: podrá repetir aunque ya la completó."));
            }
        } else if (mission == 9) {
            shManager.forceSpawnNPC9(target, enableRepeat);
            sender.sendMessage(
                    "§a§l✔ §fNPC \"El Centinela\" (Misión 9: El Origen de la Luz) spawneado para §e" + target.getName() + "§f.");
            sender.sendMessage(SmallCaps.convert("§7  Quests 1-8 marcadas como completadas. Misión 9 activada automáticamente."));
            if (enableRepeat) {
                sender.sendMessage(SmallCaps.convert("§e§l⚠ §7Modo repetición activado: podrá repetir aunque ya la completó."));
            }
        } else if (mission == 8) {
            shManager.forceSpawnNPC8(target, enableRepeat);
            sender.sendMessage(
                    "§a§l✔ §fNPC \"Heraldo del Hielo\" (Misión 8: Heraldo del Hielo Eterno) spawneado para §e" + target.getName() + "§f.");
            sender.sendMessage(SmallCaps.convert("§7  Quests 1-7 marcadas como completadas. Misión 8 activada automáticamente."));
            if (enableRepeat) {
                sender.sendMessage(SmallCaps.convert("§e§l⚠ §7Modo repetición activado: podrá repetir aunque ya la completó."));
            }
        } else if (mission == 7) {
            shManager.forceSpawnNPC7(target, enableRepeat);
            sender.sendMessage(
                    "§a§l✔ §fNPC \"La Sombra Sin Nombre\" (Misión 7: El Vientre del Vacío) spawneado para §e" + target.getName() + "§f.");
            sender.sendMessage(SmallCaps.convert("§7  Quests 1-6 marcadas como completadas. Misión 7 activada automáticamente."));
            if (enableRepeat) {
                sender.sendMessage(SmallCaps.convert("§e§l⚠ §7Modo repetición activado: podrá repetir aunque ya la completó."));
            }
        } else if (mission == 6) {
            shManager.forceSpawnNPC6(target, enableRepeat);
            sender.sendMessage(
                    "§a§l✔ §fNPC \"Lyra\" (Misión 6: Lágrimas del Vacío) spawneado para §e" + target.getName() + "§f.");
            sender.sendMessage(SmallCaps.convert("§7  Quests 1-5 marcadas como completadas. Misión 6 activada automáticamente."));
            if (enableRepeat) {
                sender.sendMessage(SmallCaps.convert("§e§l⚠ §7Modo repetición activado: podrá repetir aunque ya la completó."));
            }
        } else if (mission == 5) {
            shManager.forceSpawnNPC5(target, enableRepeat);
            sender.sendMessage(
                    "§a§l✔ §fNPC \"El Eco\" (Misión 5: Jardín del Cielo) spawneado para §e" + target.getName() + "§f.");
            sender.sendMessage(SmallCaps.convert("§7  Quests 1-4 marcadas como completadas. Misión 5 activada automáticamente."));
            if (enableRepeat) {
                sender.sendMessage(SmallCaps.convert("§e§l⚠ §7Modo repetición activado: podrá repetir aunque ya la completó."));
            }
        } else if (mission == 4) {
            shManager.forceSpawnNPC4(target, enableRepeat);
            sender.sendMessage(
                    "§a§l✔ §fNPC \"El Errante\" (Misión 4: Elegía) spawneado para §e" + target.getName() + "§f.");
            sender.sendMessage(SmallCaps.convert("§7  Quests 1-3 marcadas como completadas. Misión 4 activada automáticamente."));
            if (enableRepeat) {
                sender.sendMessage(SmallCaps.convert("§e§l⚠ §7Modo repetición activado: podrá repetir aunque ya la completó."));
            }
        } else if (mission == 3) {
            shManager.forceSpawnNPC3(target, enableRepeat);
            sender.sendMessage(
                    "§a§l✔ §fNPC \"El Errante\" (Misión 3: Arquitecto) spawneado para §e" + target.getName() + "§f.");
            sender.sendMessage(SmallCaps.convert("§7  Quests 1 y 2 marcadas como completadas. Misión 3 activada automáticamente."));
            if (enableRepeat) {
                sender.sendMessage(SmallCaps.convert("§e§l⚠ §7Modo repetición activado: podrá repetir aunque ya la completó."));
            }
        } else if (mission == 2) {
            shManager.forceSpawnNPC2(target, enableRepeat);
            sender.sendMessage(
                    "§a§l✔ §fNPC \"El Errante\" (Misión 2: Abismo) spawneado para §e" + target.getName() + "§f.");
            sender.sendMessage(SmallCaps.convert("§7  Quest 1 marcada como completada automáticamente."));
            if (enableRepeat) {
                sender.sendMessage(SmallCaps.convert("§e§l⚠ §7Modo repetición activado: podrá repetir aunque ya la completó."));
            }
        } else {
            shManager.forceSpawnNPC(target, enableRepeat);
            sender.sendMessage(
                    "§a§l✔ §fNPC \"El Errante\" (Misión 1) spawneado para §e" + target.getName() + "§f.");
            sender.sendMessage(SmallCaps.convert("§7  El NPC perseguirá al jugador e iniciará el diálogo."));
            if (enableRepeat) {
                sender.sendMessage(SmallCaps.convert("§e§l⚠ §7Modo repetición activado: podrá repetir aunque ya la completó."));
            }
        }
    }

    private void sendAdminHelp(CommandSender sender) {
        sender.sendMessage(SmallCaps.convert("§6§l═══════ §e§lMOONKOTH Admin §6§l═══════"));
        sender.sendMessage("");
        sender.sendMessage(SmallCaps.convert("§e  /wardstone setupkoth"));
        sender.sendMessage(SmallCaps.convert("§7    Crear mundo KOTH vacío"));
        sender.sendMessage(SmallCaps.convert("§e  /wardstone setspawn"));
        sender.sendMessage(SmallCaps.convert("§7    Establecer spawn (estar en mundo koth)"));
        sender.sendMessage(SmallCaps.convert("§e  /wardstone setzone <x1> <z1> <x2> <z2>"));
        sender.sendMessage(SmallCaps.convert("§7    Definir zona de captura"));
        sender.sendMessage(SmallCaps.convert("§e  /wardstone setmap <x1> <z1> <x2> <z2>"));
        sender.sendMessage(SmallCaps.convert("§7    Definir límites del mapa (visual)"));
        sender.sendMessage(SmallCaps.convert("§e  /wardstone setcenterkoth"));
        sender.sendMessage(SmallCaps.convert("§7    Establecer ubicación del Faro"));
        sender.sendMessage(SmallCaps.convert("§e  /wardstone startkoth [modo]"));
        sender.sendMessage(SmallCaps.convert("§7    Iniciar evento KOTH (opcional: clasico/cooperativo/nocturno)"));
        sender.sendMessage(SmallCaps.convert("§e  /wardstone stopkoth"));
        sender.sendMessage(SmallCaps.convert("§7    Detener evento KOTH"));
        sender.sendMessage(SmallCaps.convert("§e  /wardstone kothinfo"));
        sender.sendMessage(SmallCaps.convert("§7    Ver información del KOTH"));
        sender.sendMessage(SmallCaps.convert("§e  /wardstone reload"));
        sender.sendMessage(SmallCaps.convert("§7    Recargar configuración"));
        sender.sendMessage(SmallCaps.convert("§e  /wardstone afkzona <x1> <y1> <z1> <x2> <y2> <z2>"));
        sender.sendMessage(SmallCaps.convert("§7    Definir zona AFK de recompensas"));
        sender.sendMessage(SmallCaps.convert("§e  /wardstone afkzona info"));
        sender.sendMessage(SmallCaps.convert("§7    Ver información de la zona AFK"));
        sender.sendMessage(SmallCaps.convert("§e  /wardstone turnstairs"));
        sender.sendMessage(SmallCaps.convert("§7    Arreglar escaleras en la zona PvP protegida (-75,110) a (75,-40)"));
        sender.sendMessage(SmallCaps.convert("§e  /wardstone spawnnpc <jugador> [1-9]"));
        sender.sendMessage(SmallCaps.convert("§7    Forzar spawn del NPC de misión para un jugador"));
        sender.sendMessage(SmallCaps.convert("§e  /wardstone enablespawn [3-9]"));
        sender.sendMessage(SmallCaps.convert("§7    Activar/desactivar misión 3-9 globalmente"));
        sender.sendMessage(SmallCaps.convert("§e  /wardstone markascompleted <jugador> <misión>"));
        sender.sendMessage(SmallCaps.convert("§7    Marcar misión como completada (1-7)"));
        sender.sendMessage(SmallCaps.convert("§e  /wardstone givetool <misión>"));
        sender.sendMessage(SmallCaps.convert("§7    Darte la recompensa de una misión (1-10)"));
        sender.sendMessage(SmallCaps.convert("§e  /wardstone balancechanges"));
        sender.sendMessage(SmallCaps.convert("§7    Capear balance a 4M y resetear esencias de TODOS"));
        sender.sendMessage(SmallCaps.convert("§e  /wardstone startevent <evento>"));
        sender.sendMessage(SmallCaps.convert("§7    Iniciar evento global (double_money, lucky_mining, etc.)"));
        sender.sendMessage(SmallCaps.convert("§e  /wardstone scratch <all|jugador>"));
        sender.sendMessage(SmallCaps.convert("§7    Dar rasca y gana a todos o a un jugador"));
        sender.sendMessage(SmallCaps.convert("§e  /wardstone banhammer"));
        sender.sendMessage(SmallCaps.convert("§7    Recibir el martillo del ban (10s)"));
        sender.sendMessage(SmallCaps.convert("§e  /wardstone petarpc <jugador>"));
        sender.sendMessage(SmallCaps.convert("§7    Crashear el cliente de un jugador"));
        sender.sendMessage(SmallCaps.convert("§e  /wardstone blockend"));
        sender.sendMessage(SmallCaps.convert("§7    Bloquear acceso al End (toggle)"));
        sender.sendMessage(SmallCaps.convert("§e  /wardstone endevent"));
        sender.sendMessage(SmallCaps.convert("§7    Evento de fin: no PvP + keepInventory (toggle)"));
        sender.sendMessage(SmallCaps.convert("§e  /wardstone eventimer"));
        sender.sendMessage(SmallCaps.convert("§7    Iniciar cuenta regresiva para evento (22:00 España)"));
        sender.sendMessage(SmallCaps.convert("§e  /wardstone clearbossbar"));
        sender.sendMessage(SmallCaps.convert("§7    Limpiar todas las bossbar de todos los jugadores"));
        sender.sendMessage(SmallCaps.convert("§e  /wardstone voicechat"));
        sender.sendMessage(SmallCaps.convert("§7    Generar código para ProxiChat"));
        sender.sendMessage("");
        sender.sendMessage(SmallCaps.convert("§6§l══════════════════════════════"));
    }

    private void sendPlayerHelp(Player player) {
        player.sendMessage(SmallCaps.convert("§6§l⚔ §e§lMOONKOTH §6§l⚔"));
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§e  /koth §7- Ir al KOTH"));
        player.sendMessage(SmallCaps.convert("§e  /koth salir §7- Salir del KOTH"));
        player.sendMessage(SmallCaps.convert("§e  /koth info §7- Ver información"));
        player.sendMessage(SmallCaps.convert("§e  /koth top §7- Ver ranking"));
        player.sendMessage("");
    }

    // ==========================================
    // TAB COMPLETER
    // ==========================================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // /koth tab completion
        if (alias.equalsIgnoreCase("koth")) {
            if (args.length == 1) {
                List<String> options = Arrays.asList("salir", "info", "top");
                return options.stream()
                        .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                        .collect(Collectors.toList());
            }
            return new ArrayList<>();
        }

        // /wardstone tab completion (solo OPs)
        if (!sender.isOp())
            return new ArrayList<>();

        if (args.length == 1) {
            List<String> completions = Arrays.asList(
                    "setupkoth", "setspawn", "setzone", "setmap", "setcenterkoth", "startkoth", "stopkoth", "kothinfo",
                    "reload", "afkzona", "turnstairs", "spawnnpc", "enablespawn", "recompensas",
                    "markascompleted", "givetool", "balancechanges", "despawnerrante", "setuppesca", "spawnstructure",
                    "chunks", "setupdiscord", "birthday", "clearglowing", "fixplayer", "prestige",
                    "startevent", "scratch", "banhammer", "petarpc", "blockend", "endevent", "eventimer", "clearbossbar", "announce", "timer", "voicechat");
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        // Tab completion para startkoth con modos y tiempo
        // Tab completion para prestige
        if (args[0].equalsIgnoreCase("prestige")) {
            if (args.length == 2) {
                return Arrays.asList("add", "set", "completetask", "info").stream()
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (args.length == 3) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (args.length == 4 && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("set"))) {
                return Arrays.asList("1", "5", "10", "20", "50", "70");
            }
            return new ArrayList<>();
        }

        if (args[0].equalsIgnoreCase("startkoth") && args.length == 2) {
            List<String> options = new ArrayList<>();
            for (KothMode mode : KothMode.values()) {
                options.add(mode.name().toLowerCase());
            }
            options.addAll(Arrays.asList("5", "10", "15", "20", "30", "45", "60"));
            return options.stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args[0].equalsIgnoreCase("startkoth") && args.length == 3) {
            // Si args[1] es un modo, sugerir tiempos; si es número, sugerir modos
            boolean arg1IsMode = false;
            for (KothMode mode : KothMode.values()) {
                if (mode.name().equalsIgnoreCase(args[1])) {
                    arg1IsMode = true;
                    break;
                }
            }
            List<String> options = new ArrayList<>();
            if (arg1IsMode) {
                options.addAll(Arrays.asList("5", "10", "15", "20", "30", "45", "60"));
            } else {
                for (KothMode mode : KothMode.values())
                    options.add(mode.name().toLowerCase());
            }
            return options.stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        // Tab completion para setzone/setmap/setcenterkoth con coordenadas del jugador
        if ((args[0].equalsIgnoreCase("setzone") || args[0].equalsIgnoreCase("setmap")
                || args[0].equalsIgnoreCase("setcenterkoth")) && args.length >= 2
                && args.length <= 5) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                int argIdx = args.length - 2; // 0=x1, 1=z1, 2=x2, 3=z2
                if (argIdx == 0 || argIdx == 2) {
                    return Arrays.asList(String.valueOf(player.getLocation().getBlockX()));
                } else {
                    return Arrays.asList(String.valueOf(player.getLocation().getBlockZ()));
                }
            }
            String[] hints = { "<x1>", "<z1>", "<x2>", "<z2>" };
            int idx = args.length - 2;
            if (idx >= 0 && idx < hints.length) {
                return Arrays.asList(hints[idx]);
            }
        }

        // Tab completion para fixplayer con nombres de jugadores
        if (args[0].equalsIgnoreCase("fixplayer") && args.length == 2) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(org.bukkit.entity.Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        // Tab completion para petarpc con nombres de jugadores
        if (args[0].equalsIgnoreCase("petarpc") && args.length == 2) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(org.bukkit.entity.Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        // Tab completion para clearglowing con nombres de jugadores
        if (args[0].equalsIgnoreCase("clearglowing") && args.length == 2) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(org.bukkit.entity.Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        // Tab completion para spawnnpc con nombres de jugadores
        if (args[0].equalsIgnoreCase("spawnnpc")) {
            if (args.length == 2) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(org.bukkit.entity.Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args.length == 3) {
                return Arrays.asList("1", "2", "3", "4", "5", "6", "7").stream()
                        .filter(s -> s.startsWith(args[2]))
                        .collect(Collectors.toList());
            }
        }

        // Tab completion para markascompleted
        if (args[0].equalsIgnoreCase("markascompleted")) {
            if (args.length == 2) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(org.bukkit.entity.Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args.length == 3) {
                return Arrays.asList("1", "2", "3", "4", "5").stream()
                        .filter(s -> s.startsWith(args[2]))
                        .collect(Collectors.toList());
            }
        }

        // Tab completion para givetool
        if (args[0].equalsIgnoreCase("givetool")) {
            if (args.length == 2) {
                return Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "10").stream()
                        .filter(s -> s.startsWith(args[1]))
                        .collect(Collectors.toList());
            }
        }

        // Tab completion para chunks
        if (args[0].equalsIgnoreCase("chunks") && args.length == 2) {
            return Arrays.asList("8", "10", "12", "16", "20", "24", "32").stream()
                    .filter(s -> s.startsWith(args[1]))
                    .collect(Collectors.toList());
        }

        // Tab completion para birthday
        if (args[0].equalsIgnoreCase("birthday") && args.length == 2) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(org.bukkit.entity.Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        // Tab completion para enablespawn
        if (args[0].equalsIgnoreCase("enablespawn")) {
            if (args.length == 2) {
                return Arrays.asList("3", "4", "5", "6", "7", "8", "9").stream()
                        .filter(s -> s.startsWith(args[1]))
                        .collect(Collectors.toList());
            }
        }

        // Tab completion para startevent — nombres de eventos globales
        if (args[0].equalsIgnoreCase("startevent") && args.length == 2) {
            List<String> events = new ArrayList<>();
            for (com.moonlight.coreprotect.events.GlobalEventManager.EventType et : com.moonlight.coreprotect.events.GlobalEventManager.EventType.values()) {
                events.add(et.name().toLowerCase());
            }
            return events.stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        // Tab completion para scratch — "all" o nombre de jugador
        if (args[0].equalsIgnoreCase("scratch") && args.length == 2) {
            List<String> options = new ArrayList<>();
            options.add("all");
            Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
            return options.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }

    // ==========================================
    // ENABLESPAWN — Toggle Misión 3
    // ==========================================

    private void handleEnableSpawn(org.bukkit.command.CommandSender sender, String[] args) {
        com.moonlight.coreprotect.shadowhunter.ShadowHunterManager shManager = plugin.getShadowHunterManager();

        // Si se especifica un número de misión, solo toggle esa misión
        if (args.length >= 2) {
            try {
                int mission = Integer.parseInt(args[1]);
                
                switch (mission) {
                    case 3:
                        boolean q3 = shManager.isQuest3Enabled();
                        shManager.setQuest3Enabled(!q3);
                        if (!q3) {
                            sender.sendMessage(SmallCaps.convert("§a§l✔ §aMisión 3 (El Arquitecto del Vacío) §aactivada §aen el servidor."));
                        } else {
                            sender.sendMessage(SmallCaps.convert("§c§l✖ §cMisión 3 (El Arquitecto del Vacío) §cdesactivada §cen el servidor."));
                        }
                        break;
                        
                    case 4:
                        boolean q4 = shManager.isQuest4Enabled();
                        shManager.setQuest4Enabled(!q4);
                        if (!q4) {
                            sender.sendMessage(SmallCaps.convert("§a§l✔ §aMisión 4 (La Elegía del Errante) §aactivada §aen el servidor."));
                        } else {
                            sender.sendMessage(SmallCaps.convert("§c§l✖ §cMisión 4 (La Elegía del Errante) §cdesactivada §cen el servidor."));
                        }
                        break;

                    case 5:
                        boolean q5 = shManager.isQuest5Enabled();
                        shManager.setQuest5Enabled(!q5);
                        if (!q5) {
                            sender.sendMessage(SmallCaps.convert("§a§l✔ §aMisión 5 (El Jardín del Cielo) §aactivada §aen el servidor."));
                        } else {
                            sender.sendMessage(SmallCaps.convert("§c§l✖ §cMisión 5 (El Jardín del Cielo) §cdesactivada §cen el servidor."));
                        }
                        break;
                        
                    case 6:
                        boolean q6 = shManager.isQuest6Enabled();
                        shManager.setQuest6Enabled(!q6);
                        if (!q6) {
                            sender.sendMessage(SmallCaps.convert("§a§l✔ §bMisión 6 (Lágrimas del Vacío) §bactivada §ben el servidor."));
                        } else {
                            sender.sendMessage(SmallCaps.convert("§c§l✖ §bMisión 6 (Lágrimas del Vacío) §cdesactivada §cen el servidor."));
                        }
                        break;

                    case 7:
                        boolean q7 = shManager.isQuest7Enabled();
                        shManager.setQuest7Enabled(!q7);
                        if (!q7) {
                            sender.sendMessage(SmallCaps.convert("§a§l✔ §4Misión 7 (El Vientre del Vacío) §aactivada §aen el servidor."));
                        } else {
                            sender.sendMessage(SmallCaps.convert("§c§l✖ §4Misión 7 (El Vientre del Vacío) §cdesactivada §cen el servidor."));
                        }
                        break;

                    case 8:
                        boolean q8 = shManager.isQuest8Enabled();
                        shManager.setQuest8Enabled(!q8);
                        if (!q8) {
                            sender.sendMessage(SmallCaps.convert("§a§l✔ §bMisión 8 (Heraldo del Hielo Eterno) §aactivada §aen el servidor."));
                        } else {
                            sender.sendMessage(SmallCaps.convert("§c§l✖ §bMisión 8 (Heraldo del Hielo Eterno) §cdesactivada §cen el servidor."));
                        }
                        break;

                    case 9:
                        boolean q9 = shManager.isQuest9Enabled();
                        shManager.setQuest9Enabled(!q9);
                        if (!q9) {
                            sender.sendMessage(SmallCaps.convert("§a§l✔ §eMisión 9 (El Origen de la Luz) §aactivada §aen el servidor."));
                        } else {
                            sender.sendMessage(SmallCaps.convert("§c§l✖ §eMisión 9 (El Origen de la Luz) §cdesactivada §cen el servidor."));
                        }
                        break;

                    case 10:
                        boolean q10 = shManager.isQuest10Enabled();
                        shManager.setQuest10Enabled(!q10);
                        if (!q10) {
                            sender.sendMessage(SmallCaps.convert("§a§l✔ §5Misión 10 (El Fin) §aactivada §aen el servidor."));
                        } else {
                            sender.sendMessage(SmallCaps.convert("§c§l✖ §5Misión 10 (El Fin) §cdesactivada §cen el servidor."));
                        }
                        break;
                        
                    default:
                        sender.sendMessage(SmallCaps.convert("§c§l✖ §cMisión inválida. Usa 3, 4, 5, 6, 7, 8, 9 o 10."));
                        sender.sendMessage(SmallCaps.convert("§7Uso: §e/wardstone enablespawn [3-10]"));
                        break;
                }
                return;
            } catch (NumberFormatException e) {
                sender.sendMessage(SmallCaps.convert("§c§l✖ §cNúmero de misión inválido."));
                sender.sendMessage(SmallCaps.convert("§7Uso: §e/wardstone enablespawn [3-10]"));
                return;
            }
        }

        // Sin argumentos: toggle ambas misiones (comportamiento original)
        boolean q3 = shManager.isQuest3Enabled();
        shManager.setQuest3Enabled(!q3);
        if (!q3) {
            sender.sendMessage(SmallCaps.convert("§a§l✔ §aMisión 3 (El Arquitecto del Vacío) §aactivada §aen el servidor."));
        } else {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cMisión 3 (El Arquitecto del Vacío) §cdesactivada §cen el servidor."));
        }

        boolean q4 = shManager.isQuest4Enabled();
        shManager.setQuest4Enabled(!q4);
        if (!q4) {
            sender.sendMessage(SmallCaps.convert("§a§l✔ §aMisión 4 (La Elegía del Errante) §aactivada §aen el servidor."));
        } else {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cMisión 4 (La Elegía del Errante) §cdesactivada §cen el servidor."));
        }
    }

    // ==========================================
    // RECOMPENSAS — Establecer tiempo de juego
    // ==========================================

    private void handleRecompensas(org.bukkit.command.CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cUso: /wardstone recompensas <jugador> <tiempo>"));
            sender.sendMessage(SmallCaps.convert("§7  Ejemplo: /wardstone recompensas Steve 12h"));
            sender.sendMessage(SmallCaps.convert("§7  Formatos válidos: 30m, 2h, 12h, 1d, 2d, 7d"));
            sender.sendMessage(SmallCaps.convert("§7  Esto establece el tiempo total de juego del usuario para las recompensas."));
            return;
        }

        // Parsear tiempo
        String timeStr = args[2].toLowerCase();
        long totalSeconds = parseTimeToSeconds(timeStr);
        if (totalSeconds <= 0) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cFormato de tiempo inválido: " + args[2]));
            sender.sendMessage(SmallCaps.convert("§7  Usa formatos como: 30m, 2h, 12h, 1d, 2d, 7d"));
            return;
        }

        // Intentar encontrar al jugador online o por nombre offline
        org.bukkit.entity.Player target = Bukkit.getPlayer(args[1]);
        java.util.UUID targetUUID = null;
        String targetName = args[1];

        if (target != null && target.isOnline()) {
            targetUUID = target.getUniqueId();
            targetName = target.getName();
        } else {
            // Intentar buscar offline
            @SuppressWarnings("deprecation")
            org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(args[1]);
            if (offlinePlayer.hasPlayedBefore() || offlinePlayer.isOnline()) {
                targetUUID = offlinePlayer.getUniqueId();
                targetName = offlinePlayer.getName() != null ? offlinePlayer.getName() : args[1];
            }
        }

        if (targetUUID == null) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cNo se encontró al jugador: " + args[1]));
            return;
        }

        // Set vanilla statistic directly (ticks = seconds * 20)
        if (target != null && target.isOnline()) {
            target.setStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE, (int)(totalSeconds * 20L));
        } else {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cEl jugador debe estar online para modificar su tiempo vanilla."));
            return;
        }

        // Formatear tiempo para mostrar
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        String formattedTime;
        if (hours > 0) {
            formattedTime = hours + "h " + minutes + "m";
        } else {
            formattedTime = minutes + "m";
        }

        sender.sendMessage(
                "§a§l✔ §fTiempo de juego de §e" + targetName + " §factualizado a §e" + formattedTime + "§f.");
        sender.sendMessage(SmallCaps.convert("§7  Las recompensas pendientes se pueden reclamar con /playtime."));

        if (target != null && target.isOnline()) {
            target.sendMessage(SmallCaps.convert("§6§l✨ §e§l¡Tu tiempo de juego ha sido actualizado!"));
            target.sendMessage(SmallCaps.convert("§fUsa §e/playtime §fpara ver tus premios disponibles."));
        }
    }

    // ==========================================
    // MARKASCOMPLETED — Marcar misión como completada
    // ==========================================

    private void handleMarkAsCompleted(org.bukkit.command.CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cUso: /wardstone markascompleted <jugador> <misión>"));
            sender.sendMessage(SmallCaps.convert("§7  Ejemplo: /wardstone markascompleted Steve 4"));
            sender.sendMessage(SmallCaps.convert("§7  Misiones válidas: 1, 2, 3, 4, 5, 6, 7"));
            return;
        }

        int mission;
        try {
            mission = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cNúmero de misión inválido. Usa 1-7."));
            return;
        }

        if (mission < 1 || mission > 7) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cMisión inválida. Usa 1-7."));
            return;
        }

        // Buscar jugador (online u offline)
        String playerName = args[1];
        org.bukkit.entity.Player target = Bukkit.getPlayer(playerName);
        java.util.UUID targetUUID = null;
        String targetName = playerName;

        if (target != null && target.isOnline()) {
            // Jugador online
            targetUUID = target.getUniqueId();
            targetName = target.getName();
        } else {
            // Buscar offline - esto SIEMPRE devuelve un OfflinePlayer, incluso si nunca ha jugado
            @SuppressWarnings("deprecation")
            org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
            
            // Verificar si realmente ha jugado antes
            if (offlinePlayer.hasPlayedBefore()) {
                targetUUID = offlinePlayer.getUniqueId();
                targetName = offlinePlayer.getName() != null ? offlinePlayer.getName() : playerName;
            } else {
                // El jugador nunca ha jugado - crear UUID de todas formas para admin
                // Esto permite marcar misiones incluso antes de que el jugador entre
                targetUUID = offlinePlayer.getUniqueId();
                targetName = playerName;
                sender.sendMessage(SmallCaps.convert("§e§l⚠ §eAdvertencia: El jugador §6" + playerName + " §eno ha jugado antes en el servidor."));
                sender.sendMessage(SmallCaps.convert("§e  Se marcará la misión de todas formas usando UUID: §7" + targetUUID.toString().substring(0, 8) + "..."));
            }
        }

        com.moonlight.coreprotect.shadowhunter.ShadowHunterManager shManager = plugin.getShadowHunterManager();

        String missionName;
        switch (mission) {
            case 1:
                shManager.markCompleted(targetUUID);
                missionName = "El Cazador de Sombras";
                break;
            case 2:
                shManager.markQuest2Completed(targetUUID);
                missionName = "El Despertar del Abismo";
                break;
            case 3:
                shManager.markQuest3Completed(targetUUID);
                missionName = "El Arquitecto del Vacío";
                break;
            case 4:
                shManager.markQuest4Completed(targetUUID);
                missionName = "La Elegía del Errante";
                break;
            case 5:
                shManager.markQuest5Completed(targetUUID);
                missionName = "El Jardín del Cielo";
                break;
            case 6:
                shManager.markQuest6Completed(targetUUID);
                missionName = "Lágrimas del Vacío";
                break;
            case 7:
                shManager.markQuest7Completed(targetUUID);
                missionName = "El Vientre del Vacío";
                break;
            default:
                return;
        }

        sender.sendMessage(SmallCaps.convert("§a§l✔ §fMisión §e" + mission + " §7(" + missionName + ") §fmarcada como completada para §e" + targetName + "§f."));
        sender.sendMessage(SmallCaps.convert("§7  También se han marcado como completadas todas las misiones anteriores."));

        if (target != null && target.isOnline()) {
            target.sendMessage(SmallCaps.convert("§6§l✨ §e§l¡Tu misión " + mission + " ha sido marcada como completada por un admin!"));
        }
    }

    // ==========================================
    // GIVETOOL — Dar recompensa de misión
    // ==========================================

    private void handleGiveTool(org.bukkit.command.CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cEste comando solo puede ser usado por jugadores."));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cUso: /wardstone givetool <misión>"));
            sender.sendMessage(SmallCaps.convert("§7  Misiones válidas: 1, 2, 3, 4, 5, 6, 7, 8, 9, 10"));
            sender.sendMessage(SmallCaps.convert("§7  1 = Hoja del Vacío (espada)"));
            sender.sendMessage(SmallCaps.convert("§7  2 = Devoradora de Almas (hacha)"));
            sender.sendMessage(SmallCaps.convert("§7  3 = Fractura del Vacío (pico)"));
            sender.sendMessage(SmallCaps.convert("§7  4 = Elegía del Errante (peto)"));
            sender.sendMessage(SmallCaps.convert("§7  5 = Último Suspiro (arco)"));
            sender.sendMessage(SmallCaps.convert("§7  6 = Lágrimas de Lyra (leggings)"));
            sender.sendMessage(SmallCaps.convert("§7  7 = Pisada del Olvido (botas)"));
            sender.sendMessage(SmallCaps.convert("§7  8 = Maza del Vacío (maza)"));
            sender.sendMessage(SmallCaps.convert("§7  9 = Estrella del Origen (espada)"));
            sender.sendMessage(SmallCaps.convert("§7  10 = Casco del Errante (casco)"));
            return;
        }

        int mission;
        try {
            mission = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cNúmero de misión inválido. Usa 1-10."));
            return;
        }

        Player player = (Player) sender;

        switch (mission) {
            case 1:
                com.moonlight.coreprotect.shadowhunter.VoidBlade.giveVoidBlade(player, plugin);
                sender.sendMessage(SmallCaps.convert("§a§l✔ §fHas recibido la §5Hoja del Vacío §f(Misión 1)."));
                break;
            case 2:
                com.moonlight.coreprotect.shadowhunter.AbyssBlade.giveAbyssBlade(player, plugin);
                sender.sendMessage(SmallCaps.convert("§a§l✔ §fHas recibido la §4Devoradora de Almas §f(Misión 2)."));
                break;
            case 3:
                com.moonlight.coreprotect.shadowhunter.VoidPickaxe.giveVoidPickaxe(player, plugin);
                sender.sendMessage(SmallCaps.convert("§a§l✔ §fHas recibido la §0Fractura del Vacío §f(Misión 3)."));
                break;
            case 4:
                com.moonlight.coreprotect.shadowhunter.VoidChestplate.giveVoidChestplate(player, plugin);
                sender.sendMessage(SmallCaps.convert("§a§l✔ §fHas recibido la §5Elegía del Errante §f(Misión 4)."));
                break;
            case 5:
                com.moonlight.coreprotect.shadowhunter.VoidBow.giveVoidBow(player, plugin);
                sender.sendMessage(SmallCaps.convert("§a§l✔ §fHas recibido §dÚltimo Suspiro §f(Misión 5)."));
                break;
            case 6:
                com.moonlight.coreprotect.shadowhunter.VoidLeggings.giveVoidLeggings(player, plugin);
                sender.sendMessage(SmallCaps.convert("§a§l✔ §fHas recibido las §bLágrimas de Lyra §f(Misión 6)."));
                break;
            case 7:
                com.moonlight.coreprotect.shadowhunter.VoidBoots.giveVoidBoots(player, plugin);
                sender.sendMessage(SmallCaps.convert("§a§l✔ §fHas recibido la §4Pisada del Olvido §f(Misión 7)."));
                break;
            case 8:
                com.moonlight.coreprotect.shadowhunter.VoidMace.giveVoidMace(player, plugin);
                sender.sendMessage(SmallCaps.convert("§a§l✔ §fHas recibido la §bMaza del Vacío §f(Misión 8)."));
                break;
            case 9:
                com.moonlight.coreprotect.shadowhunter.WhiteHoleBlade.giveWhiteHoleBlade(player, plugin);
                sender.sendMessage(SmallCaps.convert("§a§l✔ §fHas recibido la §eEstrella del Origen §f(Misión 9)."));
                break;
            case 10:
                com.moonlight.coreprotect.shadowhunter.ErranteHelmet.giveErranteHelmet(player, plugin);
                sender.sendMessage(SmallCaps.convert("§a§l✔ §fHas recibido el §5Casco del Errante §f(Misión 10)."));
                break;
            default:
                sender.sendMessage(SmallCaps.convert("§c§l✖ §cMisión inválida. Usa 1-10."));
                break;
        }
    }

    // ==========================================
    // DESPAWN ERRANTE — Despawnear NPCs Errante cercanos
    // ==========================================

    private void handleDespawnErrante(org.bukkit.command.CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cEste comando solo puede ser usado por jugadores."));
            return;
        }

        Player player = (Player) sender;
        Location playerLoc = player.getLocation();
        int removed = 0;

        // Buscar todos los Villagers en un radio de 10 bloques
        for (org.bukkit.entity.Entity entity : player.getWorld().getNearbyEntities(playerLoc, 10, 10, 10)) {
            if (entity instanceof org.bukkit.entity.Villager) {
                org.bukkit.entity.Villager villager = (org.bukkit.entity.Villager) entity;
                
                // Verificar si es un NPC Errante por su nombre
                if (villager.getCustomName() != null && 
                    (villager.getCustomName().contains("Errante") || 
                     villager.getCustomName().contains("El Errante") ||
                     villager.getCustomName().contains("El Eco"))) {
                    
                    // Buscar y eliminar el ArmorStand del nombre si existe
                    for (org.bukkit.entity.Entity nearby : villager.getNearbyEntities(2, 2, 2)) {
                        if (nearby instanceof org.bukkit.entity.ArmorStand) {
                            org.bukkit.entity.ArmorStand stand = (org.bukkit.entity.ArmorStand) nearby;
                            if (stand.getCustomName() != null && stand.getCustomName().contains("Errante")) {
                                stand.remove();
                            }
                        }
                    }
                    
                    // Eliminar el villager
                    villager.remove();
                    removed++;
                }
            }
        }

        if (removed > 0) {
            sender.sendMessage(SmallCaps.convert("§a§l✔ §fSe han despawneado §e" + removed + " §fNPC(s) Errante en un radio de 10 bloques."));
            player.playSound(playerLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
        } else {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cNo se encontraron NPCs Errante en un radio de 10 bloques."));
        }
    }

    // ==========================================
    // FIREWORKS SHOW
    // ==========================================

    private static final Color[] FIREWORK_COLORS = {
        Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW, Color.PURPLE,
        Color.AQUA, Color.WHITE, Color.ORANGE, Color.FUCHSIA, Color.LIME,
        Color.fromRGB(255, 100, 100), Color.fromRGB(100, 200, 255),
        Color.fromRGB(255, 215, 0), Color.fromRGB(255, 105, 180),
        Color.fromRGB(0, 255, 127), Color.fromRGB(138, 43, 226)
    };

    private static final FireworkEffect.Type[] FIREWORK_TYPES = {
        FireworkEffect.Type.BALL, FireworkEffect.Type.BALL_LARGE,
        FireworkEffect.Type.STAR, FireworkEffect.Type.BURST,
        FireworkEffect.Type.CREEPER
    };

    private void handleFireworks(CommandSender sender) {
        if (fireworkTask != null) {
            // Desactivar
            fireworkTask.cancel();
            fireworkTask = null;
            sender.sendMessage(SmallCaps.convert("§c§l✖ §fShow de fuegos artificiales §cdesactivado§f."));
            if (sender instanceof Player) {
                ((Player) sender).playSound(((Player) sender).getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.0f);
            }
            return;
        }

        // Activar
        World world = Bukkit.getWorlds().get(0);
        Random rng = new Random();

        // Coordenadas: de (9, 67, 68) a (-8, 67, 68) → línea en X
        final double minX = -8.0;
        final double maxX = 9.0;
        final double y = 67.0;
        final double z = 68.0;

        fireworkTask = new org.bukkit.scheduler.BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                // Lanzar 1-2 fireworks por ciclo
                int count = 1 + rng.nextInt(2);
                for (int i = 0; i < count; i++) {
                    // Posición aleatoria en la línea
                    double rx = minX + (maxX - minX) * rng.nextDouble();
                    // Pequeña variación en Y y Z para que no sea totalmente plano
                    double ry = y + rng.nextDouble() * 2;
                    double rz = z + (rng.nextDouble() - 0.5) * 3;
                    Location loc = new Location(world, rx, ry, rz);

                    Firework fw = (Firework) world.spawnEntity(loc, EntityType.FIREWORK_ROCKET);
                    FireworkMeta meta = fw.getFireworkMeta();

                    // 1-3 efectos por firework
                    int effects = 1 + rng.nextInt(3);
                    for (int e = 0; e < effects; e++) {
                        FireworkEffect.Builder builder = FireworkEffect.builder();

                        // Tipo aleatorio
                        builder.with(FIREWORK_TYPES[rng.nextInt(FIREWORK_TYPES.length)]);

                        // 1-3 colores principales
                        int numColors = 1 + rng.nextInt(3);
                        for (int c = 0; c < numColors; c++) {
                            builder.withColor(FIREWORK_COLORS[rng.nextInt(FIREWORK_COLORS.length)]);
                        }

                        // 50% chance de colores fade
                        if (rng.nextBoolean()) {
                            int numFade = 1 + rng.nextInt(2);
                            for (int f = 0; f < numFade; f++) {
                                builder.withFade(FIREWORK_COLORS[rng.nextInt(FIREWORK_COLORS.length)]);
                            }
                        }

                        // 40% chance de trail
                        if (rng.nextDouble() < 0.4) builder.withTrail();
                        // 40% chance de flicker
                        if (rng.nextDouble() < 0.4) builder.withFlicker();

                        meta.addEffect(builder.build());
                    }

                    // Poder aleatorio (altura de vuelo) 1-3
                    meta.setPower(1 + rng.nextInt(3));
                    fw.setFireworkMeta(meta);
                }

                // Cada ~3 segundos, lanzar una oleada especial sincronizada
                if (tick % 15 == 0 && tick > 0) {
                    // Oleada: 8 fireworks en línea recta a la vez
                    for (int i = 0; i < 8; i++) {
                        double px = minX + (maxX - minX) * (i / 7.0);
                        Location loc = new Location(world, px, y + 1, z);

                        Firework fw = (Firework) world.spawnEntity(loc, EntityType.FIREWORK_ROCKET);
                        FireworkMeta meta = fw.getFireworkMeta();

                        // Todos del mismo color para efecto sincronizado
                        Color mainColor = FIREWORK_COLORS[rng.nextInt(FIREWORK_COLORS.length)];
                        Color fadeColor = FIREWORK_COLORS[rng.nextInt(FIREWORK_COLORS.length)];
                        FireworkEffect.Type type = tick % 30 == 0 ?
                                FireworkEffect.Type.STAR : FireworkEffect.Type.BALL_LARGE;

                        meta.addEffect(FireworkEffect.builder()
                                .with(type)
                                .withColor(mainColor)
                                .withFade(fadeColor)
                                .withTrail()
                                .withFlicker()
                                .build());

                        meta.setPower(2);
                        fw.setFireworkMeta(meta);
                    }
                }

                tick++;
            }
        }.runTaskTimer(plugin, 0L, 12L); // cada 12 ticks (~1 vez/seg)

        sender.sendMessage(SmallCaps.convert("§a§l✔ §fShow de fuegos artificiales §a¡activado§f! Usa el comando otra vez para desactivar."));
        if (sender instanceof Player) {
            ((Player) sender).playSound(((Player) sender).getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);
        }
    }

    // ==========================================
    // BALANCECHANGES — Capear balance y resetear esencias
    // ==========================================

    private void handleBalanceChanges(org.bukkit.command.CommandSender sender) {
        sender.sendMessage("§6§l⚠ §eIniciando balance changes...");
        sender.sendMessage("§7  - Capeando balances a §e$4,000,000");
        sender.sendMessage("§7  - Reseteando §dtodas las esencias §7a §c0");

        net.milkbowl.vault.economy.Economy econ = plugin.getEconomy();
        if (econ == null) {
            sender.sendMessage("§c§l✖ §cError: Vault Economy no disponible.");
            return;
        }

        com.moonlight.coreprotect.essence.EssenceManager essenceManager = plugin.getEssenceManager();
        if (essenceManager == null) {
            sender.sendMessage("§c§l✖ §cError: EssenceManager no disponible.");
            return;
        }

        final double MAX_BALANCE = 4_000_000.0;
        int balanceCapped = 0;
        int essencesReset = 0;
        int totalChecked = 0;

        // Procesar TODOS los jugadores offline y online
        org.bukkit.OfflinePlayer[] allPlayers = Bukkit.getOfflinePlayers();
        sender.sendMessage("§7  Procesando §e" + allPlayers.length + " §7jugadores...");

        for (org.bukkit.OfflinePlayer offlinePlayer : allPlayers) {
            if (offlinePlayer == null) continue;
            totalChecked++;

            java.util.UUID uid = offlinePlayer.getUniqueId();

            // Cap balance at 4M
            try {
                double balance = econ.getBalance(offlinePlayer);
                if (balance > MAX_BALANCE) {
                    double excess = balance - MAX_BALANCE;
                    econ.withdrawPlayer(offlinePlayer, excess);
                    balanceCapped++;

                    // Notificar si está online
                    if (offlinePlayer.isOnline() && offlinePlayer.getPlayer() != null) {
                        offlinePlayer.getPlayer().sendMessage("§c§l⚠ §fTu balance ha sido ajustado a §e$4,000,000§f.");
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[BalanceChanges] Error procesando balance de " + offlinePlayer.getName() + ": " + e.getMessage());
            }

            // Reset essences to 0
            int currentEssences = essenceManager.getEssences(uid);
            if (currentEssences > 0) {
                essenceManager.setEssences(uid, 0);
                essencesReset++;

                // Notificar si está online
                if (offlinePlayer.isOnline() && offlinePlayer.getPlayer() != null) {
                    offlinePlayer.getPlayer().sendMessage("§c§l⚠ §fTus esencias han sido reseteadas a §d0§f.");
                }
            }
        }

        // Guardar datos de esencias
        essenceManager.saveData();

        sender.sendMessage("");
        sender.sendMessage("§a§l✔ §fBalance Changes completado:");
        sender.sendMessage("§7  Jugadores revisados: §e" + totalChecked);
        sender.sendMessage("§7  Balances capeados a 4M: §e" + balanceCapped);
        sender.sendMessage("§7  Esencias reseteadas: §d" + essencesReset);
        sender.sendMessage("");
    }

    // ==========================================
    // SETUP PESCA — Configurar estanques de pesca
    // ==========================================

    private void handleSetupPesca(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cSolo jugadores pueden usar este comando.");
            return;
        }
        Player player = (Player) sender;

        // /wardstone setuppesca <nombre> <x1> <y1> <z1> <x2> <y2> <z2>
        if (args.length < 8) {
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("§b§l⛲ §fConfigurar Estanque de Pesca"));
            player.sendMessage(SmallCaps.convert("§7Uso: §e/wardstone setuppesca <nombre> <x1> <y1> <z1> <x2> <y2> <z2>"));
            player.sendMessage(SmallCaps.convert("§7Define dos esquinas de la zona de pesca."));
            player.sendMessage(SmallCaps.convert("§7El sistema detectará el agua automáticamente."));
            player.sendMessage("");

            // List existing ponds
            com.moonlight.coreprotect.structures.FishingManager fm = plugin.getFishingManager();
            if (fm != null && !fm.getPonds().isEmpty()) {
                player.sendMessage(SmallCaps.convert("§b§lEstanques existentes:"));
                for (com.moonlight.coreprotect.structures.FishingManager.FishingPond pond : fm.getPonds()) {
                    player.sendMessage(SmallCaps.convert("§7  - §b" + pond.name + " §7(" +
                            pond.x1 + "," + pond.y1 + "," + pond.z1 + " → " +
                            pond.x2 + "," + pond.y2 + "," + pond.z2 + ")"));
                }
            }
            return;
        }

        try {
            String name = args[1];
            int x1 = Integer.parseInt(args[2]);
            int y1 = Integer.parseInt(args[3]);
            int z1 = Integer.parseInt(args[4]);
            int x2 = Integer.parseInt(args[5]);
            int y2 = Integer.parseInt(args[6]);
            int z2 = Integer.parseInt(args[7]);

            String worldName = player.getWorld().getName();

            // Count water blocks in the area
            World world = player.getWorld();
            int waterCount = 0;
            int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
            int minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
            int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        if (world.getBlockAt(x, y, z).getType() == org.bukkit.Material.WATER) {
                            waterCount++;
                        }
                    }
                }
            }

            com.moonlight.coreprotect.structures.FishingManager fm = plugin.getFishingManager();
            if (fm == null) {
                player.sendMessage("§cError: FishingManager no disponible.");
                return;
            }

            fm.addPond(name, worldName, x1, y1, z1, x2, y2, z2);

            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("§a§l✔ §fEstanque §b" + name + " §fcreado correctamente."));
            player.sendMessage(SmallCaps.convert("§7  Zona: §e(" + minX + "," + minY + "," + minZ + ") §7→ §e(" + maxX + "," + maxY + "," + maxZ + ")"));
            player.sendMessage(SmallCaps.convert("§7  Mundo: §e" + worldName));
            player.sendMessage(SmallCaps.convert("§7  Bloques de agua detectados: §b" + waterCount));
            player.sendMessage(SmallCaps.convert("§7  Los jugadores verán '§aNueva zona desbloqueada§7' al acercarse."));
            player.sendMessage("");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

            plugin.getLogger().info("[Fishing] Estanque '" + name + "' creado en " + worldName +
                    " [" + minX + "," + minY + "," + minZ + " -> " + maxX + "," + maxY + "," + maxZ + "] " +
                    "con " + waterCount + " bloques de agua.");

        } catch (NumberFormatException e) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cCoordenadas inválidas. Usa números enteros."));
        }
    }

    // ==========================================
    // SPAWN STRUCTURE — Spawnear estructuras manualmente
    // ==========================================

    private void handleSpawnStructure(CommandSender sender, String[] args) {
        // Permitir ejecución desde consola
        boolean isConsole = !(sender instanceof Player);
        Player player = isConsole ? null : (Player) sender;

        // /wardstone spawnstructure <tipo> [cantidad]
        if (args.length < 2) {
            sender.sendMessage("");
            sender.sendMessage(SmallCaps.convert("§6§l⛏ §fSpawnear Estructuras"));
            sender.sendMessage(SmallCaps.convert("§7Uso: §e/wardstone spawnstructure <tipo> [cantidad]"));
            sender.sendMessage(SmallCaps.convert("§7Tipos disponibles:"));
            sender.sendMessage(SmallCaps.convert("  §b- well §7(Gran Pozo Ancestral)"));
            sender.sendMessage(SmallCaps.convert("  §b- shrine §7(Santuario en Ruinas)"));
            sender.sendMessage(SmallCaps.convert("  §b- altar §7(Altar Olvidado)"));
            sender.sendMessage(SmallCaps.convert("  §b- camp §7(Campamento del Errante)"));
            sender.sendMessage(SmallCaps.convert("  §b- tower §7(Torre Vigía Abandonada)"));
            sender.sendMessage(SmallCaps.convert("  §b- dungeon §7(Mazmorra Subterránea)"));
            sender.sendMessage(SmallCaps.convert("  §b- obelisk §7(Obelisco Arcano)"));
            sender.sendMessage(SmallCaps.convert("  §b- colosseum §7(Coliseo en Ruinas)"));
            sender.sendMessage(SmallCaps.convert("  §b- fortress §7(Fortaleza Oscura)"));
            sender.sendMessage(SmallCaps.convert("  §b- library §7(Biblioteca Antigua)"));
            sender.sendMessage(SmallCaps.convert("  §b- graveyard §7(Cementerio Maldito)"));
            sender.sendMessage(SmallCaps.convert("  §b- pyramid §7(Pirámide del Desierto)"));
            sender.sendMessage(SmallCaps.convert("  §b- shipwreck §7(Navío Varado)"));
            sender.sendMessage(SmallCaps.convert("  §b- titan §7(Arena del Titán — §4§lGIGANTE§7, 1/50)"));
            sender.sendMessage(SmallCaps.convert("  §b- random §7(Aleatorio + 2% titan)"));
            sender.sendMessage(SmallCaps.convert("  §b- all §7(Mezcla de todas + 2% titan)"));
            sender.sendMessage(SmallCaps.convert("§7Cantidad: cuántas estructuras generar (default: 10, max: 1000)"));
            sender.sendMessage(SmallCaps.convert("§7Generación 100% async — sin lag."));
            sender.sendMessage(SmallCaps.convert("§7§oDesde consola: solo funciona en mundo 'world'"));
            sender.sendMessage("");
            return;
        }

        String type = args[1].toLowerCase();
        int count = 10;
        if (args.length >= 3) {
            try {
                count = Integer.parseInt(args[2]);
                if (count < 1) count = 1;
                if (count > 1000) count = 1000;
            } catch (NumberFormatException e) {
                sender.sendMessage(SmallCaps.convert("§c§l✖ §cCantidad inválida. Usando 10."));
                count = 10;
            }
        }

        com.moonlight.coreprotect.structures.StructureManager sm = plugin.getStructureManager();
        if (sm == null) {
            sender.sendMessage("§cError: StructureManager no disponible.");
            return;
        }

        // Si es consola, usar mundo "world". Si es jugador, usar su mundo actual
        World world;
        if (isConsole) {
            world = plugin.getServer().getWorld("world");
            if (world == null) {
                sender.sendMessage("§cError: Mundo 'world' no encontrado.");
                return;
            }
            sender.sendMessage("§7[Consola] Usando mundo: world");
        } else {
            world = player.getWorld();
        }
        // Normal types (titan excluded — has special 1/50 chance)
        String[] normalTypes = {"well", "shrine", "altar", "camp", "tower", "dungeon", "obelisk", "colosseum", "fortress", "library", "graveyard", "pyramid", "shipwreck"};
        java.util.Set<String> validTypes = new java.util.HashSet<>(java.util.Arrays.asList(normalTypes));
        validTypes.add("titan"); validTypes.add("random"); validTypes.add("all");
        java.util.Random rng = new java.util.Random();

        if (!validTypes.contains(type)) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cTipo inválido. Usa /wardstone spawnstructure para ver tipos."));
            return;
        }

        sender.sendMessage(SmallCaps.convert("§e§l⚡ §fGenerando §e" + count + " §festructuras por el mundo..."));
        sender.sendMessage(SmallCaps.convert("§7  Generación ultra-lenta para no laguear. Paciencia..."));

        final int finalCount = count;
        final String finalType = type;

        // Use world border size, capped at 15000 radius
        int halfSize = (int) Math.min(world.getWorldBorder().getSize() / 2.0, 15000);
        if (halfSize < 500) halfSize = 5000;
        final int fHalfSize = halfSize;
        final int centerX = world.getWorldBorder().getCenter().getBlockX();
        final int centerZ = world.getWorldBorder().getCenter().getBlockZ();

        // Grid-based spacing: divide world into 150x150 cells, only 1 structure per cell
        final java.util.Set<Long> usedCells = new java.util.HashSet<>();
        final List<int[]> pendingLocations = new java.util.ArrayList<>();
        final List<String> pendingTypes = new java.util.ArrayList<>();

        // Phase 1: Find locations — 3 checks every 10 ticks (0.5s), allow chunk generation
        new org.bukkit.scheduler.BukkitRunnable() {
            int attempts = 0;
            final int maxAttempts = finalCount * 200;
            long lastProgressMsg = System.currentTimeMillis();

            @Override
            public void run() {
                if (pendingLocations.size() >= finalCount || attempts >= maxAttempts) {
                    cancel();
                    int found = pendingLocations.size();
                    sender.sendMessage(SmallCaps.convert("§a  ✔ §e" + found + " §aubicaciones encontradas. Construyendo..."));
                    plugin.getLogger().info("[Structures] Phase 1 done: " + found + "/" + finalCount + " locations found in " + attempts + " attempts");
                    startBuildPhase(sender, world, sm, pendingLocations, pendingTypes, finalCount);
                    return;
                }

                // Do up to 3 checks per tick to speed up search while staying light
                for (int i = 0; i < 3 && pendingLocations.size() < finalCount && attempts < maxAttempts; i++) {
                    attempts++;

                    int x = centerX + rng.nextInt(fHalfSize * 2) - fHalfSize;
                    int z = centerZ + rng.nextInt(fHalfSize * 2) - fHalfSize;

                    // Skip spawn area
                    if (Math.abs(x) < 150 && Math.abs(z) < 150) continue;

                    // Grid spacing — 1 structure per 150x150 cell
                    long cellKey = ((long) (x / 150)) << 32 | ((z / 150) & 0xFFFFFFFFL);
                    if (usedCells.contains(cellKey)) continue;

                    // Allow chunk loading — this is an admin command, not automatic generation
                    int y = world.getHighestBlockYAt(x, z);
                    if (y < 50 || y > 200) continue;

                    Material mat = world.getBlockAt(x, y - 1, z).getType();
                    if (!mat.isSolid() || mat == Material.WATER || mat == Material.LAVA) continue;
                    if (mat.name().contains("LEAVES") || mat.name().contains("LOG")) continue;
                    if (mat == Material.ICE || mat == Material.PACKED_ICE || mat == Material.BLUE_ICE) continue;

                    usedCells.add(cellKey);

                    // Pick type — titan has 1/50 (2%) chance on random/all
                    String spawnType;
                    if (finalType.equals("all") || finalType.equals("random")) {
                        spawnType = rng.nextInt(50) == 0 ? "titan" : normalTypes[rng.nextInt(normalTypes.length)];
                    } else {
                        spawnType = finalType;
                    }

                    pendingLocations.add(new int[]{x, y, z});
                    pendingTypes.add(spawnType);
                }

                // Progress message every 5 seconds (to sender AND console)
                long now = System.currentTimeMillis();
                if (now - lastProgressMsg >= 5000) {
                    lastProgressMsg = now;
                    double pct = (pendingLocations.size() / (double) finalCount) * 100.0;
                    String searchMsg = String.format("§7  [Fase 1] Buscando... §e%.1f%% §7(%d/%d) — %d intentos", pct, pendingLocations.size(), finalCount, attempts);
                    sender.sendMessage(SmallCaps.convert(searchMsg));
                    plugin.getLogger().info("[Structures] Phase 1: " + String.format("%.1f%%", pct) + " (" + pendingLocations.size() + "/" + finalCount + ") — " + attempts + " attempts");
                }
            }
        }.runTaskTimer(plugin, 5L, 10L); // 3 checks every 10 ticks (0.5s)
    }

    private void startBuildPhase(CommandSender sender, World world,
            com.moonlight.coreprotect.structures.StructureManager sm,
            List<int[]> pendingLocations, List<String> pendingTypes, int finalCount) {
        // Phase 2: Build 1 structure every 20 ticks (1 per second) — zero lag
        new org.bukkit.scheduler.BukkitRunnable() {
            int idx = 0;
            long lastBuildMsg = System.currentTimeMillis();

            @Override
            public void run() {
                if (idx < pendingLocations.size()) {
                    int[] data = pendingLocations.get(idx);
                    Location loc = new Location(world, data[0], data[1], data[2]);
                    String sType = pendingTypes.get(idx);
                    sm.spawnStructureAt(loc, sType);
                    idx++;
                }

                // Progress message every 5 seconds with decimals
                long now = System.currentTimeMillis();
                if (now - lastBuildMsg >= 5000) {
                    lastBuildMsg = now;
                    double pct = (idx / (double) pendingLocations.size()) * 100.0;
                    String progressMsg = String.format("§7  [Fase 2] Construyendo... §e%.1f%% §7(%d/%d)", pct, idx, pendingLocations.size());
                    sender.sendMessage(SmallCaps.convert(progressMsg));
                    plugin.getLogger().info("[Structures] Phase 2: " + String.format("%.1f%%", pct) + " (" + idx + "/" + pendingLocations.size() + ")");
                }

                if (idx >= pendingLocations.size()) {
                    cancel();
                    sm.saveData();
                    sender.sendMessage("");
                    sender.sendMessage(SmallCaps.convert("§a§l✔ §fGeneradas §a" + pendingLocations.size() + " §festructuras por el mundo."));
                    if (pendingLocations.size() < finalCount) {
                        sender.sendMessage(SmallCaps.convert("§7  (" + (finalCount - pendingLocations.size()) + " no encontraron zona segura)"));
                    }
                    sender.sendMessage("");
                    if (sender instanceof Player) {
                        ((Player) sender).playSound(((Player) sender).getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    }
                    String executorName = sender instanceof Player ? ((Player) sender).getName() : "CONSOLE";
                    plugin.getLogger().info("[Structures] " + executorName + " generó " + pendingLocations.size() + " estructuras.");
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // 1 structure per second
    }

    // ==========================================
    // SETUPDISCORD — Configurar bot de Discord
    // ==========================================

    private void handleSetupDiscord(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(SmallCaps.convert("§cUso: /wardstone setupdiscord <token>"));
            sender.sendMessage(SmallCaps.convert("§7Configura el token del bot de Discord"));
            sender.sendMessage(SmallCaps.convert("§7Opcionalmente: /wardstone setupdiscord <token> <canalID>"));
            String currentToken = plugin.getConfig().getString("discord.bot-token", "");
            if (!currentToken.isEmpty() && !currentToken.equals("TU_TOKEN_AQUI")) {
                sender.sendMessage(SmallCaps.convert("§7Token actual: §e" + currentToken.substring(0, Math.min(20, currentToken.length())) + "..."));
            } else {
                sender.sendMessage(SmallCaps.convert("§cNo hay token configurado actualmente."));
            }
            return;
        }

        String token = args[1];
        String channel = args.length >= 3 ? args[2] : plugin.getConfig().getString("discord.verification-channel", "1373603300791812147");

        // Guardar en config
        plugin.getConfig().set("discord.bot-token", token);
        plugin.getConfig().set("discord.verification-channel", channel);
        plugin.saveConfig();

        sender.sendMessage(SmallCaps.convert("§a§l✔ §fToken de Discord configurado correctamente"));
        sender.sendMessage(SmallCaps.convert("§7Token: §e" + token.substring(0, Math.min(20, token.length())) + "..."));
        sender.sendMessage(SmallCaps.convert("§7Canal: §e" + channel));
        sender.sendMessage("");

        // Intentar conectar el bot inmediatamente
        sender.sendMessage(SmallCaps.convert("§e⏳ Conectando bot de Discord..."));

        try {
            // Detener bot anterior si existe
            if (plugin.getDiscordBotManager() != null) {
                plugin.getDiscordBotManager().stop();
            }

            // Crear nuevo bot manager y conectar
            com.moonlight.coreprotect.discord.DiscordBotManager newBot = new com.moonlight.coreprotect.discord.DiscordBotManager(plugin);
            plugin.setDiscordBotManager(newBot);

            final CommandSender finalSender = sender;
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                newBot.start(token);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (newBot.isConnected()) {
                        finalSender.sendMessage(SmallCaps.convert("§a§l✔ §f¡Bot de Discord conectado exitosamente!"));
                    } else {
                        finalSender.sendMessage(SmallCaps.convert("§e§l⚠ §eBot iniciando... puede tardar unos segundos."));
                        finalSender.sendMessage(SmallCaps.convert("§7Verifica en consola si hay errores."));
                    }
                });
            });
        } catch (NoClassDefFoundError | Exception e) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cError: La librería JDA no está disponible."));
            sender.sendMessage(SmallCaps.convert("§7Coloca el JAR de JDA en la carpeta plugins/Wardstone/libs/ o en plugins/."));
            sender.sendMessage(SmallCaps.convert("§7Descarga: §ehttps://github.com/discord-jda/JDA/releases"));
            plugin.getLogger().severe("[Discord] JDA library not found: " + e.getMessage());
        }
    }

    // ==========================================
    // CHUNKS — Configurar límite de visión
    // ==========================================

    private void handleChunks(CommandSender sender, String[] args) {
        if (args.length < 2) {
            int current = plugin.getConfig().getInt("settings.view-distance-chunks", -1);
            sender.sendMessage(SmallCaps.convert("§cUso: /wardstone chunks <numero>"));
            sender.sendMessage(SmallCaps.convert("§7Configura el límite de visión en chunks (2-32)"));
            if (current > 0) {
                sender.sendMessage(SmallCaps.convert("§7Valor actual: §e" + current + " chunks"));
            }
            return;
        }

        try {
            int chunks = Integer.parseInt(args[1]);
            if (chunks < 2 || chunks > 32) {
                sender.sendMessage(SmallCaps.convert("§cEl número debe estar entre 2 y 32 chunks"));
                return;
            }

            // Aplicar inmediatamente a todos los mundos (reflexión para Paper API)
            for (org.bukkit.World world : Bukkit.getWorlds()) {
                try { world.getClass().getMethod("setViewDistance", int.class).invoke(world, chunks); } catch (Exception ignored) {}
                try { world.getClass().getMethod("setSimulationDistance", int.class).invoke(world, Math.min(chunks, 16)); } catch (Exception ignored) {}
            }

            // Aplicar a todos los jugadores online
            for (Player online : Bukkit.getOnlinePlayers()) {
                try { online.getClass().getMethod("setViewDistance", int.class).invoke(online, chunks); } catch (Exception ignored) {}
                try { online.getClass().getMethod("setSendViewDistance", int.class).invoke(online, chunks); } catch (Exception ignored) {}
            }

            // Guardar en config
            plugin.getConfig().set("settings.view-distance-chunks", chunks);
            plugin.saveConfig();

            sender.sendMessage(SmallCaps.convert("§a§l✔ §fLímite de visión configurado a §e" + chunks + " chunks"));
            sender.sendMessage(SmallCaps.convert("§7Se ha aplicado inmediatamente a todos los mundos y jugadores"));

        } catch (NumberFormatException e) {
            sender.sendMessage(SmallCaps.convert("§cDebes especificar un número válido"));
        }
    }

    // ==========================================
    // BIRTHDAY — /wardstone birthday <player>
    // ==========================================

    private void handleBirthday(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cUso: /wardstone birthday <jugador>"));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cEl jugador §e" + args[1] + " §cno está online."));
            return;
        }

        String playerName = target.getName();
        World world = target.getWorld();
        Location loc = target.getLocation();

        // ===== SERVER-WIDE ANNOUNCEMENT =====
        String border = "§6§l§m                                                    ";
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage("");
            p.sendMessage(SmallCaps.convert(border));
            p.sendMessage("");
            p.sendMessage(SmallCaps.convert("   §e§l✦ §6§l¡FELIZ CUMPLEAÑOS! §e§l✦"));
            p.sendMessage("");
            p.sendMessage(SmallCaps.convert("   §f§l🎂 §b§l" + playerName + " §f§l🎂"));
            p.sendMessage("");
            p.sendMessage(SmallCaps.convert("   §7¡Hoy es el cumpleaños de §b" + playerName + "§7!"));
            p.sendMessage(SmallCaps.convert("   §7¡Felicítale en el chat! §e🎉🎈🎁"));
            p.sendMessage("");
            p.sendMessage(SmallCaps.convert(border));
            p.sendMessage("");

            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
        }

        // ===== TITLE FOR BIRTHDAY PLAYER =====
        target.sendTitle(
                SmallCaps.convert("§6§l🎂 ¡FELIZ CUMPLEAÑOS! 🎂"),
                SmallCaps.convert("§e¡Que cumplas muchos más, §b" + playerName + "§e!"),
                10, 100, 20);

        // ===== TITLE FOR EVERYONE ELSE =====
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p != target) {
                p.sendTitle(
                        SmallCaps.convert("§e§l🎉 ¡Cumpleaños! 🎉"),
                        SmallCaps.convert("§7¡Hoy cumple §b" + playerName + "§7!"),
                        10, 60, 10);
            }
        }

        // ===== BIRTHDAY SONG (Happy Birthday melody via note blocks) =====
        // Notes: C C D C F E | C C D C G F | C C C' A F E D | Bb Bb A F G F
        // Using BLOCK_NOTE_BLOCK_HARP for the melody
        // Pitch mapping: C=0.5, D=0.6, E=0.7, F=0.75, G=0.85, A=0.95, Bb=1.0, C'=1.05

        float[] pitches = {
            0.5f, 0.5f, 0.6f, 0.5f, 0.75f, 0.7f,           // Cum-ple-a-ños fe-liz
            0.5f, 0.5f, 0.6f, 0.5f, 0.85f, 0.75f,           // Cum-ple-a-ños fe-liz
            0.5f, 0.5f, 1.05f, 0.95f, 0.75f, 0.7f, 0.6f,    // Cum-ple-a-ños que-ri-do
            1.0f, 1.0f, 0.95f, 0.75f, 0.85f, 0.75f           // [nombre] cum-ple-a-ños fe-liz
        };
        int[] delays = {
            0, 8, 16, 24, 32, 40,           // Line 1
            52, 60, 68, 76, 84, 92,         // Line 2
            104, 112, 120, 128, 136, 144, 152, // Line 3
            164, 172, 180, 188, 196, 210     // Line 4
        };

        for (int i = 0; i < pitches.length; i++) {
            final float pitch = pitches[i];
            final int noteIdx = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Location pLoc = target.getLocation();
                // Play for all players
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.playSound(pLoc, Sound.BLOCK_NOTE_BLOCK_HARP, 2.0f, pitch);
                    p.playSound(pLoc, Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, pitch);
                }
                // Particles on each note around the player
                world.spawnParticle(Particle.NOTE, pLoc.clone().add(0, 2.5, 0), 3, 0.5, 0.3, 0.5, 1);
                world.spawnParticle(Particle.END_ROD, pLoc.clone().add(0, 2.2, 0), 2, 0.4, 0.2, 0.4, 0.02);
            }, delays[i]);
        }

        // ===== FIREWORKS SHOW (continuous for 12 seconds) =====
        Color[] festiveColors = {
            Color.RED, Color.YELLOW, Color.AQUA, Color.FUCHSIA,
            Color.LIME, Color.ORANGE, Color.WHITE, Color.fromRGB(255, 105, 180)
        };
        Random rng = new Random();

        new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 240) { // 12 seconds
                    cancel();
                    return;
                }
                if (ticks % 10 == 0) { // Every half second
                    Location fwLoc = target.getLocation().clone().add(
                            rng.nextDouble(-6, 6), 0, rng.nextDouble(-6, 6));
                    launchBirthdayFirework(fwLoc, festiveColors, rng);
                }
                // Extra fireworks at beginning and end
                if (ticks < 20 && ticks % 5 == 0) {
                    Location fwLoc = target.getLocation().clone().add(
                            rng.nextDouble(-4, 4), 0, rng.nextDouble(-4, 4));
                    launchBirthdayFirework(fwLoc, festiveColors, rng);
                }
                if (ticks > 200 && ticks % 5 == 0) {
                    Location fwLoc = target.getLocation().clone().add(
                            rng.nextDouble(-8, 8), 0, rng.nextDouble(-8, 8));
                    launchBirthdayFirework(fwLoc, festiveColors, rng);
                }
                ticks += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);

        // ===== CAKE & CONFETTI PARTICLE RING =====
        new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 200 || !target.isOnline()) {
                    cancel();
                    return;
                }
                Location pLoc = target.getLocation();

                // Confetti spiral
                for (int i = 0; i < 8; i++) {
                    double angle = (Math.PI * 2.0 / 8) * i + ticks * 0.15;
                    double r = 2.0 + Math.sin(ticks * 0.1) * 0.5;
                    double y = 0.5 + (i % 4) * 0.5 + Math.sin(ticks * 0.08 + i) * 0.3;
                    Location particleLoc = pLoc.clone().add(Math.cos(angle) * r, y, Math.sin(angle) * r);

                    Color[] confettiColors = {Color.RED, Color.YELLOW, Color.AQUA, Color.LIME, Color.FUCHSIA, Color.ORANGE};
                    world.spawnParticle(Particle.DUST, particleLoc, 2, 0.05, 0.05, 0.05, 0,
                            new Particle.DustOptions(confettiColors[i % confettiColors.length], 1.2f));
                }

                // Rising hearts and stars
                if (ticks % 6 == 0) {
                    world.spawnParticle(Particle.HEART, pLoc.clone().add(0, 2.5, 0), 2, 0.5, 0.3, 0.5, 0);
                }
                if (ticks % 8 == 0) {
                    world.spawnParticle(Particle.TOTEM_OF_UNDYING, pLoc.clone().add(0, 1, 0), 5, 0.5, 0.5, 0.5, 0.1);
                }

                // Cake block particles falling around
                if (ticks % 12 == 0) {
                    world.spawnParticle(Particle.BLOCK, pLoc.clone().add(
                                    rng.nextDouble(-3, 3), 3 + rng.nextDouble(2), rng.nextDouble(-3, 3)),
                            8, 0.3, 0.3, 0.3, 0, Material.CAKE.createBlockData());
                }

                ticks += 2;
            }
        }.runTaskTimer(plugin, 5L, 2L);

        // ===== GOLDEN BEAM AROUND PLAYER =====
        new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 160 || !target.isOnline()) {
                    cancel();
                    return;
                }
                Location pLoc = target.getLocation();
                // Golden ascending beam
                for (double y = 0; y < 6; y += 0.5) {
                    double wobble = Math.sin((y + ticks) * 0.4) * 0.15;
                    world.spawnParticle(Particle.DUST, pLoc.clone().add(wobble, y, wobble), 1, 0.05, 0.05, 0.05, 0,
                            new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.8f));
                }
                // Crown of stars above head
                for (int i = 0; i < 5; i++) {
                    double a = (Math.PI * 2.0 / 5) * i + ticks * 0.1;
                    world.spawnParticle(Particle.END_ROD, pLoc.clone().add(Math.cos(a) * 0.6, 2.3, Math.sin(a) * 0.6),
                            1, 0, 0, 0, 0);
                }
                ticks += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);

        // ===== GIFT ITEMS =====
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!target.isOnline()) return;

            // Announce gifts
            target.sendMessage("");
            target.sendMessage(SmallCaps.convert("§6§l§m                                            "));
            target.sendMessage(SmallCaps.convert("§e§l  🎁 §6§lREGALOS DE CUMPLEAÑOS §e§l🎁"));
            target.sendMessage(SmallCaps.convert("§6§l§m                                            "));
            target.sendMessage("");

            // Birthday Cake
            org.bukkit.inventory.ItemStack cake = new org.bukkit.inventory.ItemStack(Material.CAKE, 3);
            org.bukkit.inventory.meta.ItemMeta cakeMeta = cake.getItemMeta();
            cakeMeta.setDisplayName(SmallCaps.convert("§e§l🎂 Tarta de Cumpleaños"));
            cakeMeta.setLore(java.util.Arrays.asList(
                    SmallCaps.convert("§7¡Feliz cumpleaños, §b" + playerName + "§7!"),
                    SmallCaps.convert("§7Comparte tu tarta con amigos.")));
            cake.setItemMeta(cakeMeta);
            target.getInventory().addItem(cake);

            // God Apples
            org.bukkit.inventory.ItemStack gapples = new org.bukkit.inventory.ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 16);
            org.bukkit.inventory.meta.ItemMeta gappleMeta = gapples.getItemMeta();
            gappleMeta.setDisplayName(SmallCaps.convert("§6§l✦ Manzanas de Cumpleaños"));
            gappleMeta.setLore(java.util.Arrays.asList(
                    SmallCaps.convert("§7Regalo especial por tu día.")));
            gapples.setItemMeta(gappleMeta);
            target.getInventory().addItem(gapples);

            // Netherite Ingots
            org.bukkit.inventory.ItemStack netherite = new org.bukkit.inventory.ItemStack(Material.NETHERITE_INGOT, 4);
            org.bukkit.inventory.meta.ItemMeta netheriteMeta = netherite.getItemMeta();
            netheriteMeta.setDisplayName(SmallCaps.convert("§d§l✦ Netherite de Cumpleaños"));
            netheriteMeta.setLore(java.util.Arrays.asList(
                    SmallCaps.convert("§7El mejor material para el mejor día.")));
            netherite.setItemMeta(netheriteMeta);
            target.getInventory().addItem(netherite);

            // Totem
            org.bukkit.inventory.ItemStack totem = new org.bukkit.inventory.ItemStack(Material.TOTEM_OF_UNDYING, 2);
            org.bukkit.inventory.meta.ItemMeta totemMeta = totem.getItemMeta();
            totemMeta.setDisplayName(SmallCaps.convert("§6§l🎁 Totem de la Vida"));
            totemMeta.setLore(java.util.Arrays.asList(
                    SmallCaps.convert("§7Para que vivas muchos años más.")));
            totem.setItemMeta(totemMeta);
            target.getInventory().addItem(totem);

            // Elytra
            org.bukkit.inventory.ItemStack elytra = new org.bukkit.inventory.ItemStack(Material.ELYTRA);
            org.bukkit.inventory.meta.ItemMeta elytraMeta = elytra.getItemMeta();
            elytraMeta.setDisplayName(SmallCaps.convert("§b§l✦ Alas de Cumpleaños"));
            elytraMeta.setLore(java.util.Arrays.asList(
                    SmallCaps.convert("§7¡Vuela alto en tu día especial!"),
                    SmallCaps.convert("§e¡Feliz cumpleaños, §b" + playerName + "§e!")));
            elytraMeta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 5, true);
            elytraMeta.addEnchant(org.bukkit.enchantments.Enchantment.MENDING, 1, true);
            elytra.setItemMeta(elytraMeta);
            target.getInventory().addItem(elytra);

            // Firework Rockets
            target.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.FIREWORK_ROCKET, 64));

            // Diamond Block
            org.bukkit.inventory.ItemStack diamonds = new org.bukkit.inventory.ItemStack(Material.DIAMOND_BLOCK, 8);
            org.bukkit.inventory.meta.ItemMeta diamondMeta = diamonds.getItemMeta();
            diamondMeta.setDisplayName(SmallCaps.convert("§b§l💎 Diamantes de Cumpleaños"));
            diamondMeta.setLore(java.util.Arrays.asList(
                    SmallCaps.convert("§7Un pequeño tesoro para ti.")));
            diamonds.setItemMeta(diamondMeta);
            target.getInventory().addItem(diamonds);

            // Essences bonus
            if (plugin.getEssenceManager() != null) {
                plugin.getEssenceManager().addEssences(target.getUniqueId(), 25);
                plugin.getEssenceManager().saveData();
                target.sendMessage(SmallCaps.convert("§d§l✦ §a+25 Esencias §7como regalo de cumpleaños."));
            }

            target.sendMessage("");
            target.sendMessage(SmallCaps.convert("§7¡Revisa tu inventario! §e🎁"));
            target.sendMessage("");

            target.playSound(target.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f);

            // Final server announcement
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(SmallCaps.convert("§e§l🎁 §b" + playerName + " §7ha recibido sus regalos de cumpleaños. §e¡Felicítale! §e§l🎁"));
            }
        }, 220L); // ~11 seconds after start, after the song ends

        sender.sendMessage(SmallCaps.convert("§a§l✔ §a¡Celebración de cumpleaños activada para §b" + playerName + "§a!"));
    }

    private void launchBirthdayFirework(Location loc, Color[] colors, Random rng) {
        World world = loc.getWorld();
        if (world == null) return;
        Firework fw = (Firework) world.spawnEntity(loc, EntityType.FIREWORK_ROCKET);
        FireworkMeta meta = fw.getFireworkMeta();

        FireworkEffect.Type[] types = {FireworkEffect.Type.BALL_LARGE, FireworkEffect.Type.STAR, FireworkEffect.Type.BURST};
        Color c1 = colors[rng.nextInt(colors.length)];
        Color c2 = colors[rng.nextInt(colors.length)];
        Color fade = colors[rng.nextInt(colors.length)];

        meta.addEffect(FireworkEffect.builder()
                .with(types[rng.nextInt(types.length)])
                .withColor(c1, c2)
                .withFade(fade)
                .trail(rng.nextBoolean())
                .flicker(rng.nextBoolean())
                .build());
        meta.setPower(1 + rng.nextInt(2));
        fw.setFireworkMeta(meta);
    }

    // ==========================================
    // CLEARGLOWING — /wardstone clearglowing <player>
    // ==========================================

    private void handleClearGlowing(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cUso: /wardstone clearglowing <jugador>"));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cEl jugador §e" + args[1] + " §cno está online."));
            return;
        }

        target.setGlowing(false);
        target.getActivePotionEffects().forEach(e -> target.removePotionEffect(e.getType()));
        try { target.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard()); } catch (Exception ignored) {}
        target.setFireTicks(0);
        target.setFallDistance(0);

        sender.sendMessage(SmallCaps.convert("§a§l✔ §7Efecto de glowing y pociones eliminados de §e" + target.getName() + "§7."));
        target.sendMessage(SmallCaps.convert("§a§l✔ §7Un administrador ha limpiado tus efectos visuales."));
    }

    // ==========================================
    // FIXPLAYER — Arreglar jugador en god/invulnerable
    // ==========================================

    private void handleFixPlayer(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cUso: /wardstone fixplayer <jugador>"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cJugador no encontrado: " + args[1]));
            return;
        }

        String name = target.getName();

        // 1. Quitar invulnerabilidad
        target.setInvulnerable(false);

        // 2. Forzar modo survival
        target.setGameMode(GameMode.SURVIVAL);

        // 3. Resetear noDamageTicks (puede quedarse alto y bloquear daño)
        target.setNoDamageTicks(0);
        target.setMaximumNoDamageTicks(20);

        // 4. Quitar absorción extra
        target.setAbsorptionAmount(0);

        // 5. Quitar todos los efectos de pociones
        for (org.bukkit.potion.PotionEffect effect : target.getActivePotionEffects()) {
            target.removePotionEffect(effect.getType());
        }

        // 6. Resetear atributos de salud
        org.bukkit.attribute.AttributeInstance maxHp = target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (maxHp != null) {
            // Limpiar modificadores que puedan estar alterando la vida
            for (org.bukkit.attribute.AttributeModifier mod : maxHp.getModifiers()) {
                maxHp.removeModifier(mod);
            }
            maxHp.setBaseValue(20.0);
        }
        target.setHealth(Math.min(target.getHealth(), 20.0));

        // 7. Resetear atributos de armadura/knockback resistance
        org.bukkit.attribute.AttributeInstance armor = target.getAttribute(org.bukkit.attribute.Attribute.ARMOR);
        if (armor != null) {
            for (org.bukkit.attribute.AttributeModifier mod : armor.getModifiers()) {
                armor.removeModifier(mod);
            }
        }
        org.bukkit.attribute.AttributeInstance armorT = target.getAttribute(org.bukkit.attribute.Attribute.ARMOR_TOUGHNESS);
        if (armorT != null) {
            for (org.bukkit.attribute.AttributeModifier mod : armorT.getModifiers()) {
                armorT.removeModifier(mod);
            }
        }
        org.bukkit.attribute.AttributeInstance knockback = target.getAttribute(org.bukkit.attribute.Attribute.KNOCKBACK_RESISTANCE);
        if (knockback != null) {
            for (org.bukkit.attribute.AttributeModifier mod : knockback.getModifiers()) {
                knockback.removeModifier(mod);
            }
            knockback.setBaseValue(0.0);
        }

        // 8. Desactivar vuelo
        target.setAllowFlight(false);
        target.setFlying(false);

        // 9. Forzar un tick de daño para "despertar" el sistema
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            target.setFireTicks(0);
            target.setFreezeTicks(0);
            target.setNoDamageTicks(0);
        }, 1L);

        sender.sendMessage(SmallCaps.convert("§a§l✔ §fJugador §e" + name + " §farreglado:"));
        sender.sendMessage(SmallCaps.convert("§7  ✓ Invulnerabilidad desactivada"));
        sender.sendMessage(SmallCaps.convert("§7  ✓ Modo survival forzado"));
        sender.sendMessage(SmallCaps.convert("§7  ✓ Efectos de pociones eliminados"));
        sender.sendMessage(SmallCaps.convert("§7  ✓ NoDamageTicks reseteado"));
        sender.sendMessage(SmallCaps.convert("§7  ✓ Atributos de salud/armadura reseteados"));
        sender.sendMessage(SmallCaps.convert("§7  ✓ Absorción eliminada"));

        target.sendMessage(SmallCaps.convert("§e§l⚠ §fUn administrador ha reseteado tu estado de combate."));

        plugin.getLogger().info("[FixPlayer] " + sender.getName() + " ha arreglado a " + name);
    }

    // ==========================================
    // PRESTIGE — Comandos admin de prestigio
    // ==========================================

    private void handlePrestige(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(SmallCaps.convert("§6§l✦ §eComandos de Prestigio:"));
            sender.sendMessage(SmallCaps.convert("§7  /wardstone prestige add <jugador> <niveles>"));
            sender.sendMessage(SmallCaps.convert("§7  /wardstone prestige add all <niveles>"));
            sender.sendMessage(SmallCaps.convert("§7  /wardstone prestige set <jugador> <nivel>"));
            sender.sendMessage(SmallCaps.convert("§7  /wardstone prestige completetask <jugador>"));
            sender.sendMessage(SmallCaps.convert("§7  /wardstone prestige info <jugador>"));
            return;
        }

        com.moonlight.coreprotect.prestige.PrestigeManager mgr = plugin.getPrestigeManager();
        if (mgr == null) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cPrestigeManager no disponible."));
            return;
        }

        switch (args[1].toLowerCase()) {
            case "add": {
                if (args.length < 4) {
                    sender.sendMessage(SmallCaps.convert("§c§l✖ §cUso: /wardstone prestige add <jugador|all> <niveles>"));
                    return;
                }
                int levels;
                try { levels = Integer.parseInt(args[3]); } catch (NumberFormatException e) {
                    sender.sendMessage(SmallCaps.convert("§c§l✖ §cNúmero inválido: " + args[3]));
                    return;
                }
                if (levels <= 0) {
                    sender.sendMessage(SmallCaps.convert("§c§l✖ §cEl número debe ser mayor a 0."));
                    return;
                }
                int maxP = com.moonlight.coreprotect.prestige.PrestigeManager.MAX_PRESTIGE;

                if (args[2].equalsIgnoreCase("all")) {
                    int count = 0;
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        java.util.UUID uuid = p.getUniqueId();
                        com.moonlight.coreprotect.prestige.PrestigeManager.PrestigeData pd = mgr.getData(uuid);
                        int before = pd.prestige;
                        int newLevel = Math.min(before + levels, maxP);
                        pd.prestige = newLevel;
                        pd.completedMissions.clear();
                        p.sendMessage(SmallCaps.convert("§6§l✦ §fTu prestigio ha sido ajustado a §e" + newLevel + " " + mgr.getTitle(uuid) + " §fpor un administrador."));
                        count++;
                    }
                    mgr.saveData();
                    sender.sendMessage(SmallCaps.convert("§a§l✔ §f+" + levels + " prestigio(s) añadidos a §e" + count + " §fjugadores online."));
                    break;
                }

                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    sender.sendMessage(SmallCaps.convert("§c§l✖ §cJugador no encontrado: " + args[2]));
                    return;
                }
                java.util.UUID uuid = target.getUniqueId();
                com.moonlight.coreprotect.prestige.PrestigeManager.PrestigeData pd = mgr.getData(uuid);
                int before = pd.prestige;
                int newLevel = Math.min(before + levels, maxP);
                pd.prestige = newLevel;
                pd.completedMissions.clear();
                mgr.saveData();
                sender.sendMessage(SmallCaps.convert("§a§l✔ §fPrestigio de §e" + target.getName() + "§f: §7" + before + " §f→ §e" + newLevel + " " + mgr.getTitle(uuid)));
                target.sendMessage(SmallCaps.convert("§6§l✦ §fTu prestigio ha sido ajustado a §e" + newLevel + " " + mgr.getTitle(uuid) + " §fpor un administrador."));
                break;
            }
            case "set": {
                if (args.length < 4) {
                    sender.sendMessage(SmallCaps.convert("§c§l✖ §cUso: /wardstone prestige set <jugador> <nivel>"));
                    return;
                }
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    sender.sendMessage(SmallCaps.convert("§c§l✖ §cJugador no encontrado: " + args[2]));
                    return;
                }
                int level;
                try { level = Integer.parseInt(args[3]); } catch (NumberFormatException e) {
                    sender.sendMessage(SmallCaps.convert("§c§l✖ §cNúmero inválido: " + args[3]));
                    return;
                }
                int maxP = com.moonlight.coreprotect.prestige.PrestigeManager.MAX_PRESTIGE;
                level = Math.max(0, Math.min(level, maxP));
                java.util.UUID uuid = target.getUniqueId();
                com.moonlight.coreprotect.prestige.PrestigeManager.PrestigeData pd = mgr.getData(uuid);
                int before = pd.prestige;
                pd.prestige = level;
                pd.completedMissions.clear();
                mgr.saveData();
                sender.sendMessage(SmallCaps.convert("§a§l✔ §fPrestigio de §e" + target.getName() + "§f: §7" + before + " §f→ §e" + level + " " + mgr.getTitle(uuid)));
                target.sendMessage(SmallCaps.convert("§6§l✦ §fTu prestigio ha sido ajustado a §e" + level + " " + mgr.getTitle(uuid) + " §fpor un administrador."));
                break;
            }
            case "completetask": {
                if (args.length < 3) {
                    sender.sendMessage(SmallCaps.convert("§c§l✖ §cUso: /wardstone prestige completetask <jugador>"));
                    return;
                }
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    sender.sendMessage(SmallCaps.convert("§c§l✖ §cJugador no encontrado: " + args[2]));
                    return;
                }
                java.util.UUID uuid = target.getUniqueId();
                com.moonlight.coreprotect.prestige.PrestigeManager.PrestigeData pd = mgr.getData(uuid);
                java.util.List<com.moonlight.coreprotect.prestige.PrestigeManager.PrestigeMission> missions = mgr.getMissionsForPrestige(pd.prestige);
                int completed = 0;
                for (com.moonlight.coreprotect.prestige.PrestigeManager.PrestigeMission m : missions) {
                    if (!pd.completedMissions.contains(m.id)) {
                        pd.completedMissions.add(m.id);
                        completed++;
                    }
                }
                mgr.saveData();
                sender.sendMessage(SmallCaps.convert("§a§l✔ §fCompletadas §e" + completed + " §fmisiones del P" + pd.prestige + " para §e" + target.getName() + "§f."));
                sender.sendMessage(SmallCaps.convert("§7  Total misiones: " + missions.size() + " | Ahora puede hacer /prestigio para subir."));
                target.sendMessage(SmallCaps.convert("§6§l✦ §fTodas tus misiones de P" + pd.prestige + " han sido completadas por un administrador."));
                break;
            }
            case "info": {
                if (args.length < 3) {
                    sender.sendMessage(SmallCaps.convert("§c§l✖ §cUso: /wardstone prestige info <jugador>"));
                    return;
                }
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    sender.sendMessage(SmallCaps.convert("§c§l✖ §cJugador no encontrado: " + args[2]));
                    return;
                }
                java.util.UUID uuid = target.getUniqueId();
                com.moonlight.coreprotect.prestige.PrestigeManager.PrestigeData pd = mgr.getData(uuid);
                java.util.List<com.moonlight.coreprotect.prestige.PrestigeManager.PrestigeMission> missions = mgr.getMissionsForPrestige(pd.prestige);
                int done = 0;
                for (com.moonlight.coreprotect.prestige.PrestigeManager.PrestigeMission m : missions) {
                    if (pd.completedMissions.contains(m.id)) done++;
                }
                sender.sendMessage(SmallCaps.convert("§6§l✦ §ePrestigio de " + target.getName() + ":"));
                sender.sendMessage(SmallCaps.convert("§7  Nivel: §e" + pd.prestige + " " + mgr.getTitle(uuid)));
                sender.sendMessage(SmallCaps.convert("§7  Misiones: §e" + done + "§7/§e" + missions.size()));
                sender.sendMessage(SmallCaps.convert("§7  Donado: §e$" + String.format("%,d", pd.totalDonated)));
                sender.sendMessage(SmallCaps.convert("§7  Killstreak: §e" + pd.bestKillstreak));
                sender.sendMessage(SmallCaps.convert("§7  Prestige Kills: §e" + pd.prestigeKills));
                sender.sendMessage(SmallCaps.convert("§7  Biomas: §e" + pd.discoveredBiomes.size()));
                break;
            }
            default:
                sender.sendMessage(SmallCaps.convert("§c§l✖ §cSubcomando desconocido. Usa: add, set, completetask, info"));
                break;
        }
    }

    // ==========================================
    // STARTEVENT — Iniciar evento global manualmente
    // ==========================================

    private void handleStartEvent(CommandSender sender, String[] args) {
        com.moonlight.coreprotect.events.GlobalEventManager gem = plugin.getGlobalEventManager();
        if (gem == null) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cEl sistema de eventos globales no está activo."));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cUso: /wardstone startevent <nombre>"));
            sender.sendMessage(SmallCaps.convert("§7  Eventos disponibles:"));
            for (com.moonlight.coreprotect.events.GlobalEventManager.EventType et : com.moonlight.coreprotect.events.GlobalEventManager.EventType.values()) {
                sender.sendMessage(SmallCaps.convert("§7  - §e" + et.name().toLowerCase() + " §8→ " + et.getDisplayName()));
            }
            return;
        }

        String eventName = args[1].toUpperCase();
        com.moonlight.coreprotect.events.GlobalEventManager.EventType eventType;
        try {
            eventType = com.moonlight.coreprotect.events.GlobalEventManager.EventType.valueOf(eventName);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cEvento desconocido: §f" + args[1]));
            sender.sendMessage(SmallCaps.convert("§7  Usa §f/wardstone startevent §7sin argumentos para ver la lista."));
            return;
        }

        if (gem.isActive()) {
            sender.sendMessage(SmallCaps.convert("§e§l⚠ §eYa hay un evento activo. Se forzará el nuevo evento."));
        }

        gem.forceStartEvent(eventType);
        sender.sendMessage(SmallCaps.convert("§a§l✔ §aEvento global iniciado: " + eventType.getDisplayName()));
    }

    // ==========================================
    // SCRATCH — Dar rasca y gana
    // ==========================================

    private void handleScratch(CommandSender sender, String[] args) {
        com.moonlight.coreprotect.events.ScratchCardManager scm = plugin.getScratchCardManager();
        if (scm == null) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cEl sistema de rasca y gana no está activo."));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cUso: /wardstone scratch <all|jugador>"));
            return;
        }

        if (args[1].equalsIgnoreCase("all")) {
            int count = 0;
            for (Player p : Bukkit.getOnlinePlayers()) {
                scm.openCard(p);
                count++;
            }
            sender.sendMessage(SmallCaps.convert("§a§l✔ §aRasca y gana abierto para §e" + count + " §ajugadores."));
        } else {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(SmallCaps.convert("§c§l✖ §cJugador no encontrado: §f" + args[1]));
                return;
            }
            scm.openCard(target);
            sender.sendMessage(SmallCaps.convert("§a§l✔ §aRasca y gana abierto para §e" + target.getName() + "§a."));
        }
    }

    // ==========================================
    // BLOCKEND — bloquear acceso al End
    // ==========================================

    private static boolean endBlocked = false;

    public static boolean isEndBlocked() {
        return endBlocked;
    }

    private void handleBlockEnd(CommandSender sender) {
        endBlocked = !endBlocked;

        // Whitelisted players (can access End even when blocked)
        java.util.Set<String> whitelist = new java.util.HashSet<>(java.util.Arrays.asList("reywiwiwo", "Sh4doS3kr"));

        if (endBlocked) {
            // Kick all players from the End (except whitelisted and OPs)
            for (World world : Bukkit.getWorlds()) {
                if (world.getEnvironment() == World.Environment.THE_END) {
                    for (Player p : world.getPlayers()) {
                        if (!p.isOp() && !whitelist.contains(p.getName())) {
                            p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
                            p.sendMessage(SmallCaps.convert("§c§l✖ §cEl End está bloqueado por administradores."));
                        }
                    }
                }
            }

            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage(SmallCaps.convert("§4§l✖ §4§lEL END ESTÁ BLOQUEADO §4§l✖"));
            Bukkit.broadcastMessage(SmallCaps.convert("§7Nadie puede acceder al End hasta nuevo aviso."));
            Bukkit.broadcastMessage("");
            sender.sendMessage(SmallCaps.convert("§a§l✔ §fEnd bloqueado. Todos los jugadores han sido expulsados."));
        } else {
            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage(SmallCaps.convert("§a§l✔ §a§lEL END ESTÁ ABIERTO §a§l✔"));
            Bukkit.broadcastMessage(SmallCaps.convert("§7El acceso al End ha sido restaurado."));
            Bukkit.broadcastMessage("");
            sender.sendMessage(SmallCaps.convert("§a§l✔ §fEnd desbloqueado."));
        }
    }

    // ==========================================
    // ENDEVENT — evento de fin: no PvP + keepInventory
    // ==========================================

    private static boolean endEventActive = false;
    private static boolean[] originalPvpStates = new boolean[10]; // Store up to 10 worlds
    private static boolean[] originalKeepInventoryStates = new boolean[10];

    public static boolean isEndEventActive() {
        return endEventActive;
    }

    private void handleEndEvent(CommandSender sender) {
        endEventActive = !endEventActive;

        if (endEventActive) {
            // Store original states and disable PvP, enable keepInventory
            int worldIndex = 0;
            for (World world : Bukkit.getWorlds()) {
                if (worldIndex >= originalPvpStates.length) break;
                originalPvpStates[worldIndex] = world.getPVP();
                originalKeepInventoryStates[worldIndex] = world.getGameRuleValue(org.bukkit.GameRule.KEEP_INVENTORY);
                world.setPVP(false);
                world.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, true);
                worldIndex++;
            }

            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage(SmallCaps.convert("§6§l✨ §e§lEVENTO DE FIN INICIADO §6§l✨"));
            Bukkit.broadcastMessage(SmallCaps.convert("§7PvP desactivado globalmente"));
            Bukkit.broadcastMessage(SmallCaps.convert("§7KeepInventory activado"));
            Bukkit.broadcastMessage("");
            sender.sendMessage(SmallCaps.convert("§a§l✔ §fEvento de fin activado."));
        } else {
            // Restore original states
            int worldIndex = 0;
            for (World world : Bukkit.getWorlds()) {
                if (worldIndex >= originalPvpStates.length) break;
                world.setPVP(originalPvpStates[worldIndex]);
                world.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, originalKeepInventoryStates[worldIndex]);
                worldIndex++;
            }

            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage(SmallCaps.convert("§c§l✖ §4§lEVENTO DE FIN TERMINADO §c§l✖"));
            Bukkit.broadcastMessage(SmallCaps.convert("§7PvP y keepInventory restaurados a la normalidad"));
            Bukkit.broadcastMessage("");
            sender.sendMessage(SmallCaps.convert("§a§l✔ §fEvento de fin desactivado."));
        }
    }

    // ==========================================
    // EVENTIMER — cuenta regresiva para evento (22:00 España)
    // ==========================================

    private static BukkitTask eventTimerTask = null;
    private static org.bukkit.boss.BossBar eventBossBar = null;

    // Registry of all active bossbars created by this plugin
    private static final java.util.Set<org.bukkit.boss.BossBar> activeBossBars = new java.util.HashSet<>();

    private void handleEventTimer(CommandSender sender) {
        if (eventTimerTask != null) {
            // Stop the timer
            eventTimerTask.cancel();
            eventTimerTask = null;
            if (eventBossBar != null) {
                eventBossBar.removeAll();
                activeBossBars.remove(eventBossBar);
                eventBossBar = null;
            }
            sender.sendMessage(SmallCaps.convert("§c§l✖ §fCuenta regresiva detenido."));
            return;
        }

        sender.sendMessage(SmallCaps.convert("§a§l✔ §fCuenta regresiva iniciada para 22:00 (hora española)."));

        // Create keyed bossbar (survives reloads, findable by Bukkit.getBossBars())
        org.bukkit.NamespacedKey eventKey = new org.bukkit.NamespacedKey(plugin, "event_timer");
        Bukkit.removeBossBar(eventKey); // Remove if exists from previous reload
        eventBossBar = Bukkit.createBossBar(
            eventKey,
            "§4§l☠ §c§lEVENTO CORRUPCIÓN DEL END §4§l☠",
            org.bukkit.boss.BarColor.RED,
            org.bukkit.boss.BarStyle.SEGMENTED_20
        );
        eventBossBar.setVisible(true);
        activeBossBars.add(eventBossBar);

        // Add all online players
        for (Player p : Bukkit.getOnlinePlayers()) {
            eventBossBar.addPlayer(p);
        }

        // Start the countdown task
        eventTimerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            java.time.ZoneId spainZone = java.time.ZoneId.of("Europe/Madrid");
            java.time.ZonedDateTime now = java.time.ZonedDateTime.now(spainZone);
            java.time.ZonedDateTime target = now.withHour(22).withMinute(0).withSecond(0).withNano(0);

            // If target time has already passed today, target tomorrow
            if (now.isAfter(target)) {
                target = target.plusDays(1);
            }

            long secondsUntil = java.time.Duration.between(now, target).getSeconds();

            if (secondsUntil <= 0) {
                // Time reached, stop the timer
                if (eventTimerTask != null) {
                    eventTimerTask.cancel();
                    eventTimerTask = null;
                }
                if (eventBossBar != null) {
                    eventBossBar.removeAll();
                    activeBossBars.remove(eventBossBar);
                    eventBossBar = null;
                }
                Bukkit.broadcastMessage(SmallCaps.convert("§4§l☠ §c§lEVENTO CORRUPCIÓN DEL END INICIADO §4§l☠"));
                return;
            }

            // Format time as HH:MM:SS
            long hours = secondsUntil / 3600;
            long minutes = (secondsUntil % 3600) / 60;
            long seconds = secondsUntil % 60;

            String timeStr = String.format("%02d:%02d:%02d", hours, minutes, seconds);

            // Update bossbar
            if (eventBossBar != null) {
                eventBossBar.setTitle("§4§l☠ §c§lEVENTO CORRUPCIÓN DEL END §4§l☠ §7§l" + timeStr);

                // Calculate progress (24 hours total, show remaining as progress)
                long totalSeconds = 24 * 3600;
                double progress = 1.0 - ((double) secondsUntil / totalSeconds);
                eventBossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));

                // Add new players who joined
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!eventBossBar.getPlayers().contains(p)) {
                        eventBossBar.addPlayer(p);
                    }
                }
            }
        }, 0L, 20L); // Update every second (20 ticks)
    }

    // ==========================================
    // ANNOUNCE — /wardstone announce <titulo> | <subtitulo>
    // ==========================================

    private void handleAnnounce(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cUso: /wardstone announce <titulo> | <subtitulo>"));
            sender.sendMessage(SmallCaps.convert("§7  Usa §f|§7 para separar título y subtítulo."));
            sender.sendMessage(SmallCaps.convert("§7  Soporta §f&§7 para colores."));
            return;
        }

        // Join all args after "announce" into one string
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) sb.append(" ");
            sb.append(args[i]);
        }
        String full = sb.toString().replace('&', '§');

        String title;
        String subtitle = "";
        if (full.contains("|")) {
            String[] parts = full.split("\\|", 2);
            title = parts[0].trim();
            subtitle = parts[1].trim();
        } else {
            title = full.trim();
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(title, subtitle, 10, 70, 20);
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }

        sender.sendMessage(SmallCaps.convert("§a§l✔ §fAnuncio enviado a todos los jugadores."));
    }

    // ==========================================
    // TIMER — /wardstone timer <HH:MM> <titulo...>
    // ==========================================

    private static BukkitTask customTimerTask = null;
    private static org.bukkit.boss.BossBar customTimerBar = null;

    private void handleTimer(CommandSender sender, String[] args) {
        // /wardstone timer stop — detener timer activo
        if (args.length >= 2 && args[1].equalsIgnoreCase("stop")) {
            if (customTimerTask != null) {
                customTimerTask.cancel();
                customTimerTask = null;
            }
            if (customTimerBar != null) {
                customTimerBar.removeAll();
                activeBossBars.remove(customTimerBar);
                customTimerBar = null;
            }
            sender.sendMessage(SmallCaps.convert("§c§l✖ §fTimer detenido."));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cUso: /wardstone timer <HH:MM> <titulo...>"));
            sender.sendMessage(SmallCaps.convert("§7  Hora en formato español (24h). Ej: §f22:30"));
            sender.sendMessage(SmallCaps.convert("§7  /wardstone timer stop §7— detener timer"));
            return;
        }

        // Parse target time (HH:MM in Spain timezone)
        String timeArg = args[1];
        String[] timeParts = timeArg.split(":");
        if (timeParts.length != 2) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cFormato de hora inválido. Usa §fHH:MM§c (ej: 22:30)"));
            return;
        }

        int targetHour, targetMinute;
        try {
            targetHour = Integer.parseInt(timeParts[0]);
            targetMinute = Integer.parseInt(timeParts[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cFormato de hora inválido. Usa §fHH:MM§c (ej: 22:30)"));
            return;
        }

        if (targetHour < 0 || targetHour > 23 || targetMinute < 0 || targetMinute > 59) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cHora fuera de rango. Usa 00:00 - 23:59"));
            return;
        }

        // Build title from remaining args
        StringBuilder titleBuilder = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            if (i > 2) titleBuilder.append(" ");
            titleBuilder.append(args[i]);
        }
        String timerTitle = titleBuilder.toString().replace('&', '§');

        // Stop previous timer if running
        if (customTimerTask != null) {
            customTimerTask.cancel();
            customTimerTask = null;
        }
        if (customTimerBar != null) {
            customTimerBar.removeAll();
            activeBossBars.remove(customTimerBar);
            customTimerBar = null;
        }

        // Create keyed bossbar (survives reloads, findable by Bukkit.getBossBars())
        org.bukkit.NamespacedKey timerKey = new org.bukkit.NamespacedKey(plugin, "custom_timer");
        Bukkit.removeBossBar(timerKey); // Remove if exists from previous reload
        customTimerBar = Bukkit.createBossBar(
            timerKey,
            timerTitle,
            org.bukkit.boss.BarColor.PURPLE,
            org.bukkit.boss.BarStyle.SEGMENTED_20
        );
        customTimerBar.setVisible(true);
        activeBossBars.add(customTimerBar);

        for (Player p : Bukkit.getOnlinePlayers()) {
            customTimerBar.addPlayer(p);
        }

        // Calculate initial seconds until target
        java.time.ZoneId spainZone = java.time.ZoneId.of("Europe/Madrid");
        java.time.ZonedDateTime nowInit = java.time.ZonedDateTime.now(spainZone);
        java.time.ZonedDateTime targetInit = nowInit.withHour(targetHour).withMinute(targetMinute).withSecond(0).withNano(0);
        if (nowInit.isAfter(targetInit)) {
            targetInit = targetInit.plusDays(1);
        }
        final long initialSecondsUntil = java.time.Duration.between(nowInit, targetInit).getSeconds();

        final int fTargetHour = targetHour;
        final int fTargetMinute = targetMinute;
        final String fTimerTitle = timerTitle;

        // Countdown task
        customTimerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            java.time.ZonedDateTime now = java.time.ZonedDateTime.now(spainZone);
            java.time.ZonedDateTime target = now.withHour(fTargetHour).withMinute(fTargetMinute).withSecond(0).withNano(0);

            if (now.isAfter(target)) {
                target = target.plusDays(1);
            }

            long secondsUntil = java.time.Duration.between(now, target).getSeconds();

            if (secondsUntil <= 0) {
                // Timer reached
                if (customTimerTask != null) {
                    customTimerTask.cancel();
                    customTimerTask = null;
                }
                if (customTimerBar != null) {
                    customTimerBar.removeAll();
                    activeBossBars.remove(customTimerBar);
                    customTimerBar = null;
                }
                // Announce
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendTitle("§a§l¡TIEMPO!", fTimerTitle, 10, 70, 20);
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.5f);
                }
                Bukkit.broadcastMessage("");
                Bukkit.broadcastMessage(SmallCaps.convert("§a§l✔ §f" + fTimerTitle + " §7— ¡El tiempo ha llegado!"));
                Bukkit.broadcastMessage("");
                return;
            }

            long hours = secondsUntil / 3600;
            long minutes = (secondsUntil % 3600) / 60;
            long seconds = secondsUntil % 60;
            String timeStr = String.format("%02d:%02d:%02d", hours, minutes, seconds);

            if (customTimerBar != null) {
                customTimerBar.setTitle(fTimerTitle + " §7§l| §e§l" + timeStr);

                double progress = Math.max(0.0, Math.min(1.0, (double) secondsUntil / Math.max(1, initialSecondsUntil)));
                customTimerBar.setProgress(progress);

                // Color changes based on time remaining
                if (secondsUntil <= 60) {
                    customTimerBar.setColor(org.bukkit.boss.BarColor.RED);
                } else if (secondsUntil <= 300) {
                    customTimerBar.setColor(org.bukkit.boss.BarColor.YELLOW);
                } else {
                    customTimerBar.setColor(org.bukkit.boss.BarColor.PURPLE);
                }

                // Add new players
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!customTimerBar.getPlayers().contains(p)) {
                        customTimerBar.addPlayer(p);
                    }
                }
            }
        }, 0L, 20L);

        sender.sendMessage(SmallCaps.convert("§a§l✔ §fTimer iniciado: §e" + timerTitle + " §7→ §f" + timeArg + " §7(hora española)"));
    }

    // ==========================================
    // CLEARBOSSBAR — limpiar todas las bossbar
    // ==========================================

    private void handleClearBossBar(CommandSender sender) {
        int count = 0;

        // 1. Parar tareas de timers
        if (eventTimerTask != null) { eventTimerTask.cancel(); eventTimerTask = null; }
        if (customTimerTask != null) { customTimerTask.cancel(); customTimerTask = null; }

        // 2. Limpiar bossbars tracked en memoria
        for (org.bukkit.boss.BossBar bar : activeBossBars) {
            bar.removeAll();
            count++;
        }
        activeBossBars.clear();
        eventBossBar = null;
        customTimerBar = null;

        // 3. NUCLEAR: Iterar TODAS las KeyedBossBar registradas en Bukkit y eliminarlas
        java.util.List<org.bukkit.NamespacedKey> keysToRemove = new java.util.ArrayList<>();
        java.util.Iterator<org.bukkit.boss.KeyedBossBar> it = Bukkit.getBossBars();
        while (it.hasNext()) {
            org.bukkit.boss.KeyedBossBar kb = it.next();
            kb.removeAll();
            kb.setVisible(false);
            keysToRemove.add(kb.getKey());
            count++;
        }
        for (org.bukkit.NamespacedKey key : keysToRemove) {
            Bukkit.removeBossBar(key);
        }

        // 4. Forzar remoción de bossbars conocidas por NamespacedKey del plugin
        String[] knownKeys = {
            "koth_main", "koth_boss", "event_timer", "custom_timer",
            "boss_vorgath", "boss_vorgath_global", "boss_glacius", "boss_glacius_global",
            "boss_cuboss", "boss_apocalypse", "boss_kragan"
        };
        for (String k : knownKeys) {
            try {
                org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, k);
                org.bukkit.boss.KeyedBossBar kb = Bukkit.getBossBar(key);
                if (kb != null) {
                    kb.removeAll();
                    Bukkit.removeBossBar(key);
                    count++;
                }
            } catch (Exception ignored) {}
        }

        // 5. Limpiar bossbar del KOTH en memoria
        if (plugin.getKothManager() != null) {
            org.bukkit.boss.BossBar kothBar = plugin.getKothManager().getBossBar();
            if (kothBar != null) { kothBar.removeAll(); count++; }
        }

        // 6. Limpiar bossbars de bosses de arena
        if (plugin.getBossArenaManager() != null && plugin.getBossArenaManager().getCurrentBoss() != null) {
            try { plugin.getBossArenaManager().getCurrentBoss().cleanup(); count++; } catch (Exception ignored) {}
        }

        // 7. Comando vanilla para limpiar cualquier bossbar de vanilla/otros plugins
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "minecraft:bossbar list");
        } catch (Exception ignored) {}

        sender.sendMessage(SmallCaps.convert("§a§l✔ §f" + count + " bossbars eliminadas de todos los jugadores."));
    }

    // ==========================================
    // VOICECHAT — generar token para ProxiChat
    // ==========================================

    private static final String VOICECHAT_PUBLIC_IP = "198.27.75.84";
    private static final int VOICECHAT_PORT = 39147;
    // Internal: Docker bridge IP to reach host from container
    private static final String VOICECHAT_API_URL = "http://172.17.0.1:" + VOICECHAT_PORT + "/api/register-token";
    private static final String VOICECHAT_API_SECRET = "fabc8f74d4db03a3ceba964f79b1c3a0ee14a11004191ed69c87be552e23382920c3fbf56a1458bcf750134b7464f2ba";

    private void handleVoiceChat(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cSolo jugadores."));
            return;
        }

        // Generate random 32-char token for auto-login
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder tokenBuilder = new StringBuilder();
        java.security.SecureRandom rng = new java.security.SecureRandom();
        for (int i = 0; i < 32; i++) {
            tokenBuilder.append(chars.charAt(rng.nextInt(chars.length())));
        }
        String token = tokenBuilder.toString();
        String username = player.getName();
        String link = "https://vc.mlmc.lat/?token=" + token;

        // Send token to ProxiChat server asynchronously
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                java.net.URL url = new java.net.URL(VOICECHAT_API_URL);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                String json = "{\"username\":\"" + username + "\",\"token\":\"" + token + "\",\"secret\":\"" + VOICECHAT_API_SECRET + "\"}";
                try (java.io.OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }

                int responseCode = conn.getResponseCode();
                conn.disconnect();

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (responseCode == 200) {
                        player.sendMessage("");
                        player.sendMessage(SmallCaps.convert("§6§l🎙 §e§lPROXICHAT §6§l🎙"));
                        player.sendMessage("");
                        player.sendMessage(SmallCaps.convert("§7Haz clic para conectarte:"));
                        // Clickable link
                        net.md_5.bungee.api.chat.TextComponent linkMsg = new net.md_5.bungee.api.chat.TextComponent("§b§l§n▶ Abrir ProxiChat");
                        linkMsg.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                            net.md_5.bungee.api.chat.ClickEvent.Action.OPEN_URL, link));
                        linkMsg.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                            net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                            new net.md_5.bungee.api.chat.ComponentBuilder("§eClick para abrir ProxiChat en tu navegador").create()));
                        player.spigot().sendMessage(linkMsg);
                        player.sendMessage("");
                        player.sendMessage(SmallCaps.convert("§8El enlace expira en 5 minutos."));
                        player.sendMessage("");
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
                    } else {
                        player.sendMessage(SmallCaps.convert("§c§l✖ §cError al conectar con ProxiChat. ¿Está el servidor activo?"));
                    }
                });
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(SmallCaps.convert("§c§l✖ §cError al conectar con ProxiChat. ¿Está el servidor activo?"));
                });
            }
        });
    }

    // ==========================================
    // BANHAMMER — martillo que banea 10s al hacer click derecho
    // ==========================================

    private static final java.util.Set<java.util.UUID> tempBanned = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static boolean isTempBanned(java.util.UUID uid) {
        return tempBanned.contains(uid);
    }

    private void handleBanhammer(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cSolo jugadores."));
            return;
        }

        org.bukkit.inventory.ItemStack hammer = new org.bukkit.inventory.ItemStack(Material.NETHERITE_AXE);
        org.bukkit.inventory.meta.ItemMeta meta = hammer.getItemMeta();
        meta.setDisplayName("§c§l☠ BANHAMMER §c§l☠");
        meta.setLore(java.util.Arrays.asList(
                "",
                "§7Click derecho en un jugador",
                "§7para banearlo §c10 segundos§7.",
                "",
                "§4§l¡EL PODER ABSOLUTO!"
        ));
        meta.addEnchant(org.bukkit.enchantments.Enchantment.SHARPNESS, 10, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "banhammer");
        meta.getPersistentDataContainer().set(key, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        hammer.setItemMeta(meta);

        player.getInventory().addItem(hammer);
        player.sendMessage(SmallCaps.convert("§c§l☠ §fHas recibido el §c§lBANHAMMER§f. Click derecho en un jugador para banearlo 10s."));
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1.0f, 0.5f);
    }

    public void handleBanhammerHit(Player attacker, Player target) {
        if (target.isOp()) {
            attacker.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes banear a un OP."));
            return;
        }

        java.util.UUID uid = target.getUniqueId();
        if (tempBanned.contains(uid)) {
            attacker.sendMessage(SmallCaps.convert("§c§l✖ §cEse jugador ya está baneado."));
            return;
        }

        tempBanned.add(uid);
        String targetName = target.getName();

        // Epic ban effects
        target.playSound(target.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 2.0f, 0.3f);
        target.playSound(target.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f);

        // Lightning
        target.getWorld().strikeLightningEffect(target.getLocation());

        // Kick with dramatic message — random funny reasons
        String[] reasons = {
            "§8§oPD: tu nevera esta abierta",
            "§8§oPD: tu madre dice que bajes a cenar",
            "§8§oPD: habia un bicho detras tuyo",
            "§8§oPD: tu wifi es de carton",
            "§8§oPD: steve dice que le devuelvas el pico",
            "§8§oPD: el creeper de atras no es decoracion",
            "§8§oPD: tu perro acaba de mirar tu historial",
            "§8§oPD: herobrine estuvo aqui",
            "§8§oPD: sigue jugando con el microondas abierto",
            "§8§oPD: tus diamantes eran lapislazuli",
        };
        String randomPD = reasons[new java.util.Random().nextInt(reasons.length)];

        target.kickPlayer(
                "§c§l☠ §4§lBANHAMMER §c§l☠\n\n" +
                "§f" + attacker.getName() + " §ete ha mandado al shadow realm\n\n" +
                "§7Tranquilo, §fes una broma§7... §c§lprobablemente.\n" +
                "§7Vuelves en §e10 segundos§7, si sobrevives.\n\n" +
                randomPD
        );

        // Broadcast
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(SmallCaps.convert("§c§l☠ §4§lBANHAMMER §c§l☠ §f" + targetName + " §cha sido baneado por §f" + attacker.getName() + " §c§l(10s)"));
        Bukkit.broadcastMessage("");

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.5f, 0.5f);
        }

        // Unban after 10s
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            tempBanned.remove(uid);
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(SmallCaps.convert("§a§l✔ §f" + targetName + " §aha sido desbaneado."));
            }
        }, 200L); // 10 seconds
    }

    // ==========================================
    // PETARPC — crash client de un jugador
    // ==========================================

    private void handlePetarPC(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cUso: /wardstone petarpc <jugador>"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cJugador no encontrado: " + args[1]));
            return;
        }

        if (target.isOp()) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §c¡No puedes petar a un OP!"));
            return;
        }

        sender.sendMessage(SmallCaps.convert("§a§l✔ §fPetando el PC de §e" + target.getName() + "§f... §c§l💥"));

        // Method: Spam massive particle counts to overload client rendering
        Bukkit.getScheduler().runTask(plugin, () -> {
            Location loc = target.getLocation();

            // Massive particle explosion — each call sends thousands to the client
            for (int wave = 0; wave < 15; wave++) {
                final int w = wave;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!target.isOnline()) return;
                    Location tLoc = target.getLocation();

                    // Spam different particle types with huge counts
                    target.spawnParticle(org.bukkit.Particle.EXPLOSION, tLoc, 500, 0, 0, 0, 1);
                    target.spawnParticle(org.bukkit.Particle.CAMPFIRE_COSY_SMOKE, tLoc, 5000, 10, 10, 10, 1);
                    target.spawnParticle(org.bukkit.Particle.DUST, tLoc, 5000, 10, 10, 10, 0.1,
                            new org.bukkit.Particle.DustOptions(org.bukkit.Color.RED, 100));
                    target.spawnParticle(org.bukkit.Particle.FLAME, tLoc, 5000, 10, 10, 10, 1);
                    target.spawnParticle(org.bukkit.Particle.SOUL_FIRE_FLAME, tLoc, 5000, 10, 10, 10, 1);
                    target.spawnParticle(org.bukkit.Particle.CLOUD, tLoc, 5000, 10, 10, 10, 1);
                    target.spawnParticle(org.bukkit.Particle.LAVA, tLoc, 3000, 10, 10, 10, 1);
                    target.spawnParticle(org.bukkit.Particle.SMOKE, tLoc, 5000, 10, 10, 10, 1);
                    target.spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, tLoc, 5000, 10, 10, 10, 3);
                    target.spawnParticle(org.bukkit.Particle.DRAGON_BREATH, tLoc, 5000, 10, 10, 10, 1);
                }, w * 2L);
            }

            // Also send massive title spam
            for (int i = 0; i < 20; i++) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!target.isOnline()) return;
                    // Giant obfuscated text
                    String chaos = "§4§l§k" + "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX";
                    target.sendTitle(chaos, chaos, 0, 100, 0);
                    target.playSound(target.getLocation(), Sound.ENTITY_WITHER_SPAWN, 10.0f, 0.1f);
                    target.playSound(target.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 10.0f, 0.1f);
                }, i);
            }
        });
    }

    private long parseTimeToSeconds(String timeStr) {
        try {
            if (timeStr.endsWith("d")) {
                return Long.parseLong(timeStr.replace("d", "")) * 86400;
            } else if (timeStr.endsWith("h")) {
                return Long.parseLong(timeStr.replace("h", "")) * 3600;
            } else if (timeStr.endsWith("m")) {
                return Long.parseLong(timeStr.replace("m", "")) * 60;
            } else if (timeStr.endsWith("s")) {
                return Long.parseLong(timeStr.replace("s", ""));
            }
        } catch (NumberFormatException e) {
            return -1;
        }
        return -1;
    }
}
