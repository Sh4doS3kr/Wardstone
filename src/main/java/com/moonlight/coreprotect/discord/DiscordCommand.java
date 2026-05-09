package com.moonlight.coreprotect.discord;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class DiscordCommand implements CommandExecutor, TabCompleter {
    private final CoreProtectPlugin plugin;
    
    public DiscordCommand(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cEste comando solo puede ser usado por jugadores.");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "gaspi":
                generateCode(player, "gaspi");
                break;
            case "zatre":
                generateCode(player, "zatre");
                break;
            case "artistdagu":
                generateCode(player, "artistdagu");
                break;
            default:
                sendHelp(player);
                break;
        }
        
        return true;
    }
    
    private void generateCode(Player player, String roleType) {
        DiscordBotManager botManager = plugin.getDiscordBotManager();
        
        if (botManager == null || !botManager.isConnected()) {
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("§c§l✖ §cBot de Discord no disponible"));
            player.sendMessage(SmallCaps.convert("§7El bot de Discord no está conectado actualmente."));
            player.sendMessage(SmallCaps.convert("§7Contacta con un administrador."));
            player.sendMessage("");
            return;
        }
        
        String code = botManager.generateVerificationCode(player, roleType);
        
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§d§l⚡ §fCódigo de Verificación de Discord"));
        player.sendMessage(SmallCaps.convert("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§7Tu código de verificación para §e/" + roleType + "§7:"));
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("  §6§l" + code));
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§e§l1. §7Únete al Discord: §bdiscord.mlmc.lat"));
        player.sendMessage(SmallCaps.convert("§e§l2. §7Verifica tu cuenta en Discord"));
        player.sendMessage(SmallCaps.convert("§e§l3. §7Escribe el código en §e#general"));
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§7⏱ El código expira en §c10 minutos"));
        player.sendMessage(SmallCaps.convert("§7§oAl verificar recibirás las recompensas de §e/" + roleType));
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§c⚠ §7No compartas este código con nadie"));
        player.sendMessage(SmallCaps.convert("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        player.sendMessage("");
    }
    
    private void sendHelp(Player player) {
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§d§l⚡ §fVerificación de Discord"));
        player.sendMessage(SmallCaps.convert("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§7Genera un código para verificar tu cuenta de Discord:"));
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("  §e/discordcode gaspi §7- §fRol de Gaspi"));
        player.sendMessage(SmallCaps.convert("  §e/discordcode zatre §7- §fRol de Zatre"));
        player.sendMessage(SmallCaps.convert("  §e/discordcode artistdagu §7- §fRol de ArtistDagu"));
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§7Luego usa el código en Discord en §e#verificación"));
        player.sendMessage(SmallCaps.convert("§7con el comando correspondiente."));
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        player.sendMessage("");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("gaspi", "zatre", "artistdagu").stream()
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        return null;
    }
}
