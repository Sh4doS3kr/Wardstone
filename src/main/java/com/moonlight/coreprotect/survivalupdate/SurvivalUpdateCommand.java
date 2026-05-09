package com.moonlight.coreprotect.survivalupdate;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Comando /survivalupdate — Setup de la gran actualización del survival.
 *
 * Flujo:
 *  /survivalupdate setup  → Inicia el proceso, muestra instrucciones
 *  /survivalupdate 1      → Crea mundo void del lobby
 *  /survivalupdate 2      → Guarda posición actual como spawn
 *  /survivalupdate 3      → Detecta End Portal Frames → portales RTP
 */
public class SurvivalUpdateCommand implements CommandExecutor, TabCompleter {

    private final CoreProtectPlugin plugin;
    private final SurvivalUpdateManager manager;

    private static final String LOBBY_WORLD_NAME = "lobby";

    public SurvivalUpdateCommand(CoreProtectPlugin plugin, SurvivalUpdateManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cNo tienes permiso para usar este comando."));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "setup":
                handleSetup(sender);
                break;
            case "1":
                handleStep1(sender);
                break;
            case "2":
                handleStep2(sender);
                break;
            case "3":
                handleStep3(sender);
                break;
            case "4":
            case "5":
                handleStep5(sender);
                break;
            case "info":
                handleInfo(sender);
                break;
            case "reset":
                handleReset(sender);
                break;
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    // ==================== SETUP ====================

    private void handleSetup(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage("§6§l═══════════════════════════════════════");
        sender.sendMessage("§6§l   ⚙ SURVIVAL UPDATE — SETUP");
        sender.sendMessage("§6§l═══════════════════════════════════════");
        sender.sendMessage("");
        sender.sendMessage("§eSigue estos pasos en orden:");
        sender.sendMessage("");
        sender.sendMessage("§e§lPaso 1: §f/survivalupdate 1");
        sender.sendMessage("§7  Crea un mundo vacío para el lobby.");
        sender.sendMessage("§7  Luego copia tu mapa del lobby con WorldEdit.");
        sender.sendMessage("");
        sender.sendMessage("§e§lPaso 2: §f/survivalupdate 2");
        sender.sendMessage("§7  Colócate donde quieras el spawn del lobby");
        sender.sendMessage("§7  y ejecuta el comando para guardar la posición.");
        sender.sendMessage("");
        sender.sendMessage("§e§lPaso 3: §f/survivalupdate 3");
        sender.sendMessage("§7  Colócate cerca de los §bEnd Portals §7(10 bloques).");
        sender.sendMessage("§7  Se registrarán como portales de §aRTP§7.");
        sender.sendMessage("§7  Al entrar, el jugador irá a coordenadas aleatorias en §eworld§7.");
        sender.sendMessage("");
        sender.sendMessage("§e§lPaso 5: §f/survivalupdate 5");
        sender.sendMessage("§7  Colócate cerca de los §aSlime Blocks §7(5 bloques).");
        sender.sendMessage("§7  Se registrarán como §alauncher pads§7.");
        sender.sendMessage("§7  Los jugadores saldrán disparados al pasar por encima.");
        sender.sendMessage("");
        sender.sendMessage("§6§l═══════════════════════════════════════");
        sender.sendMessage("");
    }

    // ==================== PASO 1: CREAR MUNDO LOBBY ====================

    private void handleStep1(CommandSender sender) {
        sender.sendMessage(SmallCaps.convert("§e§l⚙ §ePaso 1: Creando mundo lobby..."));

        World world = manager.createLobbyWorld(LOBBY_WORLD_NAME);
        if (world == null) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cError al crear el mundo lobby."));
            return;
        }

        sender.sendMessage(SmallCaps.convert("§a§l✔ §aMundo '§e" + LOBBY_WORLD_NAME + "§a' creado correctamente."));
        sender.sendMessage(SmallCaps.convert("§7  Ahora copia tu mapa del lobby aquí con WorldEdit."));
        sender.sendMessage(SmallCaps.convert("§7  Cuando termines, ejecuta §e/survivalupdate 2§7."));

        // Teleportar al admin al mundo lobby
        if (sender instanceof Player) {
            Player player = (Player) sender;
            Location tp = new Location(world, 0.5, 100, 0.5);
            player.teleport(tp);
            sender.sendMessage(SmallCaps.convert("§7  Teletransportado al mundo lobby."));
        }
    }

    // ==================== PASO 2: GUARDAR SPAWN ====================

    private void handleStep2(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cEste comando solo funciona en el juego."));
            return;
        }

        Player player = (Player) sender;

        // Verificar que el mundo lobby existe
        if (manager.getLobbyWorldName() == null) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cPrimero ejecuta §e/survivalupdate 1 §cpara crear el mundo lobby."));
            return;
        }

        Location loc = player.getLocation();
        manager.setLobbySpawn(loc);

        sender.sendMessage(SmallCaps.convert("§a§l✔ §aSpawn del lobby guardado:"));
        sender.sendMessage(SmallCaps.convert("§7  Mundo: §e" + loc.getWorld().getName()));
        sender.sendMessage(String.format("§7  Posición: §e%.1f, %.1f, %.1f", loc.getX(), loc.getY(), loc.getZ()));
        sender.sendMessage(String.format("§7  Rotación: §e%.1f, %.1f", loc.getYaw(), loc.getPitch()));
        sender.sendMessage(SmallCaps.convert("§7  Ahora ejecuta §e/survivalupdate 3 §7cerca de los End Portal Frames."));
    }

    // ==================== PASO 3: DETECTAR PORTALES RTP ====================

    private void handleStep3(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cEste comando solo funciona en el juego."));
            return;
        }

        Player player = (Player) sender;

        sender.sendMessage(SmallCaps.convert("§e§l⚙ §ePaso 3: Buscando End Portals en 10 bloques..."));

        int found = manager.detectRtpPortals(player);

        if (found == 0) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cNo se encontraron End Portals nuevos en 10 bloques."));
            sender.sendMessage(SmallCaps.convert("§7  Colócate cerca de los portales del End activos y vuelve a intentar."));
            int total = manager.getRtpPortals().size();
            if (total > 0) {
                sender.sendMessage(SmallCaps.convert("§7  Portales RTP registrados totales: §e" + total));
            }
        } else {
            sender.sendMessage(SmallCaps.convert("§a§l✔ §aSe encontraron §e" + found + " §abloques de End Portal."));
            sender.sendMessage(SmallCaps.convert("§7  Total portales RTP registrados: §e" + manager.getRtpPortals().size()));
            sender.sendMessage(SmallCaps.convert("§a  Cuando un jugador entre en estos portales, será teletransportado"));
            sender.sendMessage(SmallCaps.convert("§a  a coordenadas aleatorias en el mundo §eworld§a."));
            sender.sendMessage(SmallCaps.convert("§7  Ahora ejecuta §e/survivalupdate 5 §7cerca de los Slime Blocks."));
        }
    }

    // ==================== PASO 5: DETECTAR LAUNCHER PADS ====================

    private void handleStep5(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(SmallCaps.convert("\u00a7c\u00a7l\u2716 \u00a7cEste comando solo funciona en el juego."));
            return;
        }

        Player player = (Player) sender;

        sender.sendMessage(SmallCaps.convert("\u00a7e\u00a7l\u2699 \u00a7ePaso 5: Buscando Slime Blocks en 5 bloques..."));

        int found = manager.detectLauncherPads(player);

        if (found == 0) {
            sender.sendMessage(SmallCaps.convert("\u00a7c\u00a7l\u2716 \u00a7cNo se encontraron Slime Blocks nuevos en 5 bloques."));
            int total = manager.getLauncherPads().size();
            if (total > 0) {
                sender.sendMessage(SmallCaps.convert("\u00a77  Launcher pads registrados totales: \u00a7e" + total));
            }
        } else {
            sender.sendMessage(SmallCaps.convert("\u00a7a\u00a7l\u2714 \u00a7aSe encontraron \u00a7e" + found + " \u00a7aSlime Blocks."));
            sender.sendMessage(SmallCaps.convert("\u00a77  Total launcher pads registrados: \u00a7e" + manager.getLauncherPads().size()));
        }

        // Guardar destino fijo: 200, 171, 237 en el mundo del lobby
        World lobbyWorld = player.getWorld();
        Location target = new Location(lobbyWorld, 200, 171, 237);
        manager.setLauncherTarget(target);
        sender.sendMessage(SmallCaps.convert("\u00a7a  Destino del lanzamiento: \u00a7e200, 171, 237"));

        // Marcar setup como completo
        manager.setSetupComplete(true);
        sender.sendMessage("");
        sender.sendMessage("§a§l═══════════════════════════════════════");
        sender.sendMessage("§a§l   ✔ SETUP COMPLETADO");
        sender.sendMessage("§a§l═══════════════════════════════════════");
        sender.sendMessage("");
        sender.sendMessage(SmallCaps.convert("§7Resumen:"));
        sender.sendMessage(SmallCaps.convert("§7  Mundo lobby: §e" + manager.getLobbyWorldName()));
        sender.sendMessage(SmallCaps.convert("§7  Spawn: §e" + (manager.getLobbySpawn() != null ? "Configurado" : "§cNo configurado")));
        sender.sendMessage(SmallCaps.convert("§7  Portales RTP: §e" + manager.getRtpPortals().size()));
        sender.sendMessage(SmallCaps.convert("§7  Launcher Pads: §e" + manager.getLauncherPads().size()));
        sender.sendMessage("");
    }

    // ==================== INFO ====================

    private void handleInfo(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage("§6§l⚙ Survival Update — Estado");
        sender.sendMessage("§7  Setup completo: " + (manager.isSetupComplete() ? "§a✔ Sí" : "§c✖ No"));
        sender.sendMessage("§7  Mundo lobby: §e" + (manager.getLobbyWorldName() != null ? manager.getLobbyWorldName() : "§cNo creado"));
        sender.sendMessage("§7  Spawn: " + (manager.getLobbySpawn() != null ? "§a✔ Configurado" : "§c✖ No configurado"));
        sender.sendMessage("§7  Portales RTP: §e" + manager.getRtpPortals().size());
        sender.sendMessage("§7  Launcher Pads: §e" + manager.getLauncherPads().size());
        sender.sendMessage("");
    }

    // ==================== RESET ====================

    private void handleReset(CommandSender sender) {
        manager.setSetupComplete(false);
        sender.sendMessage(SmallCaps.convert("§e§l⚠ §eSetup marcado como incompleto. Puedes volver a ejecutar los pasos."));
    }

    // ==================== HELP ====================

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage("§6§l⚙ Survival Update — Comandos");
        sender.sendMessage("§e  /survivalupdate setup §7— Iniciar setup (ver instrucciones)");
        sender.sendMessage("§e  /survivalupdate 1 §7— Crear mundo lobby vacío");
        sender.sendMessage("§e  /survivalupdate 2 §7— Guardar posición como spawn");
        sender.sendMessage("§e  /survivalupdate 3 §7— Detectar End Portal Frames → RTP");
        sender.sendMessage("§e  /survivalupdate 5 §7— Detectar Slime Blocks → Launcher Pads");
        sender.sendMessage("§e  /survivalupdate info §7— Ver estado actual");
        sender.sendMessage("§e  /survivalupdate reset §7— Resetear estado de setup");
        sender.sendMessage("");
    }

    // ==================== TAB COMPLETER ====================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.isOp()) return new ArrayList<>();

        if (args.length == 1) {
            List<String> completions = Arrays.asList("setup", "1", "2", "3", "4", "5", "info", "reset");
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
