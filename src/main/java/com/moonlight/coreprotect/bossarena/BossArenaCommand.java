package com.moonlight.coreprotect.bossarena;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Comandos del sistema Boss Arena:
 *
 * /boss, /bossarena, /warp boss → Teletransportar a la arena (todos)
 * /wardboss setup              → Crear nueva arena (ops)
 * /wardboss setboss            → Marcar posición del boss (ops, en setup)
 * /wardboss setspawn           → Marcar spawn de jugadores (ops, en setup)
 * /wardboss setmin             → Marcar esquina mínima del límite (ops, en setup)
 * /wardboss setmax             → Marcar esquina máxima del límite (ops, en setup)
 * /wardboss forcespawn         → Forzar spawn del boss (ops)
 * /wardboss forcestop          → Forzar parar boss (ops)
 * /wardboss list               → Listar arenas (ops)
 * /wardboss info               → Info del estado actual (ops)
 */
public class BossArenaCommand implements CommandExecutor, TabCompleter {

    private final CoreProtectPlugin plugin;
    private final BossArenaManager manager;

    public BossArenaCommand(CoreProtectPlugin plugin, BossArenaManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmdName = command.getName().toLowerCase();

        // /boss, /bossarena → Teletransportar
        if (cmdName.equals("boss") || cmdName.equals("bossarena")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(SmallCaps.convert("§cSolo jugadores."));
                return true;
            }
            manager.teleportToArena((Player) sender);
            return true;
        }

        // /wardboss → Admin commands
        if (cmdName.equals("wardboss")) {
            if (!sender.isOp()) {
                sender.sendMessage(SmallCaps.convert("§c§l✖ §cNo tienes permiso."));
                return true;
            }
            if (args.length == 0) {
                sendHelp(sender);
                return true;
            }

            String sub = args[0].toLowerCase();

            switch (sub) {
                case "setup":
                    return handleSetup(sender);
                case "setboss":
                    return handleSetBossType(sender, args);
                case "setbosspos":
                    return handleSetPoint(sender, BossArenaManager.SetupSession.Step.BOSS_SPAWN);
                case "setspawn":
                    return handleSetPoint(sender, BossArenaManager.SetupSession.Step.PLAYER_SPAWN);
                case "setmin":
                    return handleSetPoint(sender, BossArenaManager.SetupSession.Step.BOUND_MIN);
                case "setmax":
                    return handleSetPoint(sender, BossArenaManager.SetupSession.Step.BOUND_MAX);
                case "done":
                    return handleDone(sender);
                case "forcespawn":
                    return handleForceSpawn(sender, args);
                case "forcestop":
                    return handleForceStop(sender);
                case "list":
                    return handleList(sender);
                case "info":
                    return handleInfo(sender);
                case "delete":
                    return handleDelete(sender, args);
                default:
                    sendHelp(sender);
                    return true;
            }
        }

        return false;
    }

    private boolean handleSetup(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(SmallCaps.convert("§cSolo jugadores."));
            return true;
        }
        Player player = (Player) sender;

        // Crear nueva arena
        ArenaData arena = manager.createArena();
        if (arena == null) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cError creando la arena."));
            return true;
        }

        // Teletransportar al jugador al nuevo mundo
        org.bukkit.World world = arena.getWorld();
        if (world != null) {
            player.teleport(new org.bukkit.Location(world, 0, 65, 0));
        }

        // Iniciar sesión de setup
        BossArenaManager.SetupSession session = new BossArenaManager.SetupSession(arena);
        manager.getSetupSessions().put(player.getUniqueId(), session);

        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§4§l⚔ §c§lBOSS ARENA - SETUP §4§l⚔"));
        player.sendMessage(SmallCaps.convert("§7Arena creada: §e" + arena.getWorldName()));
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§7Construye tu arena y luego configúrala:"));
        player.sendMessage(SmallCaps.convert("§e/wardboss setboss <vorgath|glacius> §7→ Elegir tipo de boss"));
        player.sendMessage(SmallCaps.convert("§e/wardboss setbosspos §7→ Posición del boss"));
        player.sendMessage(SmallCaps.convert("§e/wardboss setspawn §7→ Spawn de jugadores"));
        player.sendMessage(SmallCaps.convert("§e/wardboss setmin §7→ Esquina mínima del límite"));
        player.sendMessage(SmallCaps.convert("§e/wardboss setmax §7→ Esquina máxima del límite"));
        player.sendMessage(SmallCaps.convert("§e/wardboss done §7→ Finalizar setup"));
        player.sendMessage("");

        return true;
    }

    private boolean handleSetPoint(CommandSender sender, BossArenaManager.SetupSession.Step step) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(SmallCaps.convert("§cSolo jugadores."));
            return true;
        }
        Player player = (Player) sender;

        // Detectar automáticamente el mundo donde está el jugador
        ArenaData arena = manager.getArenaByWorld(player.getWorld().getName());
        if (arena == null) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo estás en un mundo de boss arena."));
            player.sendMessage(SmallCaps.convert("§7Usa §e/wardboss setup §7para crear una nueva arena."));
            return true;
        }

        // No requiere sesión de setup, trabaja directamente con la arena
        BossArenaManager.SetupSession session = manager.getSetupSessions().get(player.getUniqueId());
        if (session == null) {
            session = new BossArenaManager.SetupSession(arena);
            manager.getSetupSessions().put(player.getUniqueId(), session);
        }

        org.bukkit.Location loc = player.getLocation();

        switch (step) {
            case BOSS_SPAWN:
                arena.setBossSpawn(loc);
                player.sendMessage(SmallCaps.convert("§a§l✔ §aPosición del boss establecida: §e" + formatLoc(loc)));
                break;
            case PLAYER_SPAWN:
                arena.setPlayerSpawn(loc);
                player.sendMessage(SmallCaps.convert("§a§l✔ §aSpawn de jugadores establecido: §e" + formatLoc(loc)));
                break;
            case BOUND_MIN:
                arena.setBoundMin(loc);
                player.sendMessage(SmallCaps.convert("§a§l✔ §aLímite mínimo establecido: §e" + formatLoc(loc)));
                break;
            case BOUND_MAX:
                arena.setBoundMax(loc);
                player.sendMessage(SmallCaps.convert("§a§l✔ §aLímite máximo establecido: §e" + formatLoc(loc)));
                break;
        }

        manager.saveConfig();

        // Mostrar progreso
        player.sendMessage("§7Progreso: " +
                (arena.getBossSpawn() != null ? "§a✔" : "§c✖") + " §7Boss  " +
                (arena.getPlayerSpawn() != null ? "§a✔" : "§c✖") + " §7Spawn  " +
                (arena.getBoundMin() != null ? "§a✔" : "§c✖") + " §7Min  " +
                (arena.getBoundMax() != null ? "§a✔" : "§c✖") + " §7Max");

        if (arena.isConfigured()) {
            player.sendMessage(SmallCaps.convert("§a¡Arena completamente configurada! Usa §e/wardboss done §apara finalizar."));
        }

        return true;
    }

    private boolean handleDone(CommandSender sender) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;

        // Detectar arena del mundo actual
        ArenaData arena = manager.getArenaByWorld(player.getWorld().getName());
        if (arena == null) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo estás en un mundo de boss arena."));
            return true;
        }

        // Limpiar sesión si existe
        manager.getSetupSessions().remove(player.getUniqueId());
        if (!arena.isConfigured()) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cLa arena no está completamente configurada."));
            player.sendMessage("§7Faltan: " +
                    (arena.getBossSpawn() == null ? "§cBoss " : "") +
                    (arena.getPlayerSpawn() == null ? "§cSpawn " : "") +
                    (arena.getBoundMin() == null ? "§cMin " : "") +
                    (arena.getBoundMax() == null ? "§cMax " : ""));
            return true;
        }

        manager.saveConfig();
        player.sendMessage(SmallCaps.convert("§a§l✔ §a¡Arena §e" + arena.getWorldName() + " §aconfigurada correctamente!"));
        player.sendMessage(SmallCaps.convert("§7El boss puede spawnear aleatoriamente en esta arena."));
        return true;
    }

    private boolean handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cUso: §e/wardboss delete <nombre_mundo>"));
            sender.sendMessage(SmallCaps.convert("§7Ejemplo: §e/wardboss delete bossarena_1"));
            return true;
        }

        String worldName = args[1];
        ArenaData arena = manager.getArenaByWorld(worldName);
        
        if (arena == null) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cNo existe una arena con ese nombre."));
            sender.sendMessage(SmallCaps.convert("§7Usa §e/wardboss list §7para ver las arenas disponibles."));
            return true;
        }

        // Verificar que no sea la arena activa
        if (manager.isBossActive() && manager.getCurrentArena() != null && 
            manager.getCurrentArena().getWorldName().equals(worldName)) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes eliminar la arena activa. Usa §e/wardboss forcestop §cprimero."));
            return true;
        }

        // Eliminar arena
        org.bukkit.World world = arena.getWorld();
        if (world != null) {
            // Teletransportar jugadores fuera
            for (org.bukkit.entity.Player p : world.getPlayers()) {
                p.teleport(org.bukkit.Bukkit.getWorlds().get(0).getSpawnLocation());
                p.sendMessage(SmallCaps.convert("§7Has sido teletransportado porque la arena fue eliminada."));
            }
            
            // Descargar mundo
            org.bukkit.Bukkit.unloadWorld(world, false);
        }

        // Eliminar del manager
        manager.getArenas().remove(worldName);
        manager.saveConfig();

        // Eliminar carpeta del mundo (opcional, comentado por seguridad)
        // File worldFolder = new File(org.bukkit.Bukkit.getWorldContainer(), worldName);
        // deleteDirectory(worldFolder);

        sender.sendMessage(SmallCaps.convert("§a§l✔ §aArena §e" + worldName + " §aeliminada correctamente."));
        sender.sendMessage(SmallCaps.convert("§7El mundo ha sido descargado. Para eliminarlo completamente del disco,"));
        sender.sendMessage(SmallCaps.convert("§7borra manualmente la carpeta §e" + worldName + " §7del servidor."));
        return true;
    }

    private boolean handleForceSpawn(CommandSender sender, String[] args) {
        if (manager.isBossActive()) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cYa hay un boss activo."));
            return true;
        }

        if (args.length < 2) {
            // Sin argumento: mostrar menú de selección
            sender.sendMessage("");
            sender.sendMessage(SmallCaps.convert("§4§l⚔ §c§lFORCE SPAWN §4§l⚔"));
            sender.sendMessage(SmallCaps.convert("§7Elige qué boss spawnear:"));
            for (BossType bt : BossType.values()) {
                sender.sendMessage(SmallCaps.convert("  " + bt.getColor() + "§l" + bt.getId() + " §8- §7" + bt.getSubtitle()));
            }
            sender.sendMessage("");
            sender.sendMessage(SmallCaps.convert("§7Uso: §e/wardboss forcespawn <tipo>"));
            sender.sendMessage(SmallCaps.convert("§7Ejemplo: §e/wardboss forcespawn glacius"));
            return true;
        }

        String typeName = args[1];
        BossType type = null;
        for (BossType bt : BossType.values()) {
            if (bt.getId().equalsIgnoreCase(typeName) || bt.name().equalsIgnoreCase(typeName)) {
                type = bt;
                break;
            }
        }

        if (type == null) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cTipo de boss no reconocido: §e" + typeName));
            sender.sendMessage(SmallCaps.convert("§7Tipos disponibles:"));
            for (BossType bt : BossType.values()) {
                sender.sendMessage(SmallCaps.convert("  " + bt.getColor() + "§l" + bt.getId() + " §8- §7" + bt.getSubtitle()));
            }
            return true;
        }

        manager.forceSpawn(type);
        sender.sendMessage(SmallCaps.convert("§a§l✔ §aSpawn forzado de " + type.getColor() + "§l" + type.getDisplayName() + "§a."));
        return true;
    }

    private boolean handleForceStop(CommandSender sender) {
        if (!manager.isBossActive()) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cNo hay ningún boss activo."));
            return true;
        }

        manager.forceStop();
        sender.sendMessage(SmallCaps.convert("§a§l✔ §aBoss eliminado forzosamente."));
        return true;
    }

    private boolean handleList(CommandSender sender) {
        Map<String, ArenaData> arenas = manager.getArenas();
        if (arenas.isEmpty()) {
            sender.sendMessage(SmallCaps.convert("§c§l✖ §cNo hay arenas creadas."));
            return true;
        }

        sender.sendMessage(SmallCaps.convert("§4§l⚔ §cArenas de Boss §4§l⚔"));
        for (Map.Entry<String, ArenaData> entry : arenas.entrySet()) {
            ArenaData a = entry.getValue();
            String status = a.isConfigured() ? "§a✔ Configurada" : "§c✖ Incompleta";
            BossType bt = a.getBossType();
            sender.sendMessage(SmallCaps.convert("§7- §e" + entry.getKey() + " §7[" + status + "§7] §8| " + bt.getColor() + "§l" + bt.getId()));
        }
        return true;
    }

    private boolean handleInfo(CommandSender sender) {
        sender.sendMessage(SmallCaps.convert("§4§l⚔ §cBoss Arena Info §4§l⚔"));
        sender.sendMessage(SmallCaps.convert("§7Boss activo: " + (manager.isBossActive() ? "§a§lSÍ" : "§c§lNO")));
        sender.sendMessage(SmallCaps.convert("§7Arenas: §e" + manager.getArenas().size()));
        sender.sendMessage(SmallCaps.convert("§7Próximo spawn: §e" + manager.getTimeUntilSpawn()));

        if (manager.isBossActive() && manager.getCurrentArena() != null) {
            sender.sendMessage(SmallCaps.convert("§7Arena actual: §e" + manager.getCurrentArena().getWorldName()));
        }
        return true;
    }

    private boolean handleSetBossType(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(SmallCaps.convert("§cSolo jugadores."));
            return true;
        }
        Player player = (Player) sender;

        ArenaData arena = manager.getArenaByWorld(player.getWorld().getName());
        if (arena == null) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo estás en un mundo de boss arena."));
            player.sendMessage(SmallCaps.convert("§7Usa §e/wardboss setup §7para crear una nueva arena."));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cUso: §e/wardboss setboss <tipo>"));
            player.sendMessage(SmallCaps.convert("§7Tipos disponibles:"));
            for (BossType type : BossType.values()) {
                String selected = arena.getBossType() == type ? " §a§l← ACTUAL" : "";
                player.sendMessage(SmallCaps.convert("  " + type.getColor() + "§l" + type.getId() + " §8- §7" + type.getSubtitle() + selected));
            }
            return true;
        }

        String typeName = args[1];
        BossType type = null;
        for (BossType bt : BossType.values()) {
            if (bt.getId().equalsIgnoreCase(typeName) || bt.name().equalsIgnoreCase(typeName)) {
                type = bt;
                break;
            }
        }

        if (type == null) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cTipo de boss no reconocido: §e" + typeName));
            player.sendMessage(SmallCaps.convert("§7Tipos disponibles:"));
            for (BossType bt : BossType.values()) {
                player.sendMessage(SmallCaps.convert("  " + bt.getColor() + "§l" + bt.getId() + " §8- §7" + bt.getSubtitle()));
            }
            return true;
        }

        arena.setBossType(type);
        manager.saveConfig();

        player.sendMessage(SmallCaps.convert("§a§l✔ §aBoss de la arena configurado: " + type.getColor() + "§l" + type.getId()));
        player.sendMessage(SmallCaps.convert("§7" + type.getSubtitle()));
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(SmallCaps.convert("§4§l⚔ §c§lBOSS ARENA §4§l⚔"));
        sender.sendMessage(SmallCaps.convert("§e/wardboss setup §7→ Crear nueva arena"));
        sender.sendMessage(SmallCaps.convert("§e/wardboss setboss <tipo> §7→ Elegir boss (vorgath, glacius)"));
        sender.sendMessage(SmallCaps.convert("§e/wardboss setbosspos §7→ Posición del boss"));
        sender.sendMessage(SmallCaps.convert("§e/wardboss setspawn §7→ Spawn de jugadores"));
        sender.sendMessage(SmallCaps.convert("§e/wardboss setmin §7→ Límite mínimo"));
        sender.sendMessage(SmallCaps.convert("§e/wardboss setmax §7→ Límite máximo"));
        sender.sendMessage(SmallCaps.convert("§e/wardboss done §7→ Finalizar setup"));
        sender.sendMessage(SmallCaps.convert("§e/wardboss delete <mundo> §7→ Eliminar arena"));
        sender.sendMessage(SmallCaps.convert("§e/wardboss forcespawn <tipo> §7→ Forzar spawn de un boss"));
        sender.sendMessage(SmallCaps.convert("§e/wardboss forcestop §7→ Forzar parar"));
        sender.sendMessage(SmallCaps.convert("§e/wardboss list §7→ Listar arenas"));
        sender.sendMessage(SmallCaps.convert("§e/wardboss info §7→ Info del estado"));
        sender.sendMessage("");
        sender.sendMessage(SmallCaps.convert("§e/boss §7→ Ir a la arena (todos)"));
    }

    private String formatLoc(org.bukkit.Location loc) {
        return String.format("%.1f, %.1f, %.1f", loc.getX(), loc.getY(), loc.getZ());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmdName = command.getName().toLowerCase();

        if (cmdName.equals("wardboss") && sender.isOp()) {
            if (args.length == 1) {
                List<String> subs = Arrays.asList("setup", "setboss", "setbosspos", "setspawn", "setmin", "setmax",
                        "done", "delete", "forcespawn", "forcestop", "list", "info");
                List<String> result = new ArrayList<>();
                for (String s : subs) {
                    if (s.startsWith(args[0].toLowerCase())) result.add(s);
                }
                return result;
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("delete")) {
                List<String> arenas = new ArrayList<>();
                for (String worldName : manager.getArenas().keySet()) {
                    if (worldName.toLowerCase().startsWith(args[1].toLowerCase())) {
                        arenas.add(worldName);
                    }
                }
                return arenas;
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("forcespawn")) {
                List<String> types = new ArrayList<>();
                for (BossType bt : BossType.values()) {
                    if (bt.getId().toLowerCase().startsWith(args[1].toLowerCase())) {
                        types.add(bt.getId().toLowerCase());
                    }
                }
                return types;
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("setboss")) {
                List<String> types = new ArrayList<>();
                for (BossType bt : BossType.values()) {
                    if (bt.getId().toLowerCase().startsWith(args[1].toLowerCase())) {
                        types.add(bt.getId().toLowerCase());
                    }
                }
                return types;
            }
        }

        return Collections.emptyList();
    }
}
