package com.moonlight.coreprotect.commands;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.koth.KothManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Comando para guardar manualmente el mundo KOTH.
 * Uso: /savekoth [confirmar]
 */
public class SaveKothCommand implements CommandExecutor, TabCompleter {

    private final CoreProtectPlugin plugin;

    public SaveKothCommand(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Solo permitido para admins
        if (!sender.hasPermission("wardstone.admin")) {
            sender.sendMessage(SmallCaps.convert("§cNo tienes permiso para usar este comando."));
            return true;
        }

        World kothWorld = Bukkit.getWorld("koth");
        if (kothWorld == null) {
            sender.sendMessage(SmallCaps.convert("§cEl mundo KOTH no existe o no está cargado."));
            return true;
        }

        // Si no hay argumentos, mostrar advertencia
        if (args.length == 0) {
            sender.sendMessage("");
            sender.sendMessage(SmallCaps.convert("§6§l⚠ §e§lADVERTENCIA DE GUARDADO"));
            sender.sendMessage(SmallCaps.convert("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            sender.sendMessage(SmallCaps.convert("§fEstás a punto de guardar el mundo KOTH manualmente."));
            sender.sendMessage(SmallCaps.convert("§7Esto guardará:"));
            sender.sendMessage(SmallCaps.convert("§e• §fTodas las construcciones"));
            sender.sendMessage(SmallCaps.convert("§e• §fCambios en el terreno"));
            sender.sendMessage(SmallCaps.convert("§e• §fInventarios de entidades"));
            sender.sendMessage(SmallCaps.convert("§e• §fConfiguración del mundo"));
            sender.sendMessage("");
            sender.sendMessage(SmallCaps.convert("§7El mundo §cNO se restablecerá §7al reiniciar el servidor."));
            sender.sendMessage("");
            sender.sendMessage(SmallCaps.convert("§7Para confirmar el guardado, usa:"));
            sender.sendMessage(SmallCaps.convert("§e• §f/savekoth confirmar"));
            sender.sendMessage("");
            sender.sendMessage(SmallCaps.convert("§7Para cancelar, simplemente no hagas nada."));
            sender.sendMessage("");
            return true;
        }

        // Procesar argumento
        String arg = args[0].toLowerCase();
        if (!arg.equals("confirmar")) {
            sender.sendMessage(SmallCaps.convert("§cUso incorrecto. Usa /savekoth [confirmar]"));
            return true;
        }

        // Ejecutar guardado
        try {
            // Guardar el mundo
            kothWorld.save();
            
            // Forzar guardado de chunks
            org.bukkit.Chunk[] loadedChunks = kothWorld.getLoadedChunks();
            for (org.bukkit.Chunk chunk : loadedChunks) {
                chunk.unload(true);
            }
            
            // Volver a cargar chunks si es necesario
            if (sender instanceof Player) {
                Player player = (Player) sender;
                if (player.getWorld().equals(kothWorld)) {
                    kothWorld.loadChunk(kothWorld.getChunkAt(player.getLocation()));
                }
            }

            // Mensajes de confirmación
            sender.sendMessage("");
            sender.sendMessage(SmallCaps.convert("§6§l✅ §a§lMUNDO KOTH GUARDADO"));
            sender.sendMessage(SmallCaps.convert("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            sender.sendMessage(SmallCaps.convert("§fEl mundo KOTH ha sido guardado exitosamente."));
            sender.sendMessage(SmallCaps.convert("§7• §fConstrucciones guardadas"));
            sender.sendMessage(SmallCaps.convert("§7• §fCambios aplicados"));
            sender.sendMessage(SmallCaps.convert("§7• §fChunks sincronizados"));
            sender.sendMessage(SmallCaps.convert("§7• §fMundo protegido contra reset"));
            sender.sendMessage("");
            sender.sendMessage(SmallCaps.convert("§eEl mundo permanecerá intacto al reiniciar el servidor."));
            sender.sendMessage("");

            // Broadcast a otros admins
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("wardstone.admin") && !player.equals(sender)) {
                    player.sendMessage(SmallCaps.convert("§6[Admin] §e" + sender.getName() + " §fha guardado el mundo KOTH manualmente."));
                }
            }

            // Log del servidor
            plugin.getLogger().info("[KOTH] Mundo guardado manualmente por " + sender.getName());

        } catch (Exception e) {
            sender.sendMessage(SmallCaps.convert("§cError al guardar el mundo KOTH: " + e.getMessage()));
            plugin.getLogger().severe("[KOTH] Error al guardar mundo manualmente: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("wardstone.admin")) {
            return Arrays.asList();
        }

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return Arrays.asList("confirmar").stream()
                    .filter(option -> option.startsWith(input))
                    .collect(Collectors.toList());
        }

        return Arrays.asList();
    }
}
