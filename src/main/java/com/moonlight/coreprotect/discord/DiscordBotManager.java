package com.moonlight.coreprotect.discord;

import com.moonlight.coreprotect.CoreProtectPlugin;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class DiscordBotManager extends ListenerAdapter {
    private final CoreProtectPlugin plugin;
    private JDA jda;
    private String verificationChannelId;
    
    // Almacena códigos de verificación: código -> PlayerData
    private final Map<String, PlayerVerification> pendingVerifications = new HashMap<>();
    
    // Almacena qué jugador tiene qué código activo
    private final Map<UUID, String> playerCodes = new HashMap<>();
    
    private static class PlayerVerification {
        String playerName;
        UUID playerUUID;
        String roleType; // "gaspi", "zatre", "artistdagu"
        long timestamp;
        
        PlayerVerification(String playerName, UUID playerUUID, String roleType) {
            this.playerName = playerName;
            this.playerUUID = playerUUID;
            this.roleType = roleType;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    public DiscordBotManager(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        this.verificationChannelId = plugin.getConfig().getString("discord.verification-channel", "1373603300791812147");
    }
    
    public void start(String token) {
        try {
            plugin.getLogger().info("[Discord] Iniciando bot de Discord...");
            
            jda = JDABuilder.createDefault(token)
                .enableIntents(
                    GatewayIntent.GUILD_MESSAGES,
                    GatewayIntent.MESSAGE_CONTENT,
                    GatewayIntent.GUILD_MEMBERS
                )
                .addEventListeners(this)
                .setActivity(Activity.playing("MoonLightMC"))
                .build();
            
            jda.awaitReady();
            
            // Registrar comandos slash
            jda.updateCommands().addCommands(
                Commands.slash("gaspi", "Verificar rol de Gaspi con código del servidor")
                    .addOption(OptionType.STRING, "codigo", "Código de verificación del servidor", true),
                Commands.slash("zatre", "Verificar rol de Zatre con código del servidor")
                    .addOption(OptionType.STRING, "codigo", "Código de verificación del servidor", true),
                Commands.slash("artistdagu", "Verificar rol de ArtistDagu con código del servidor")
                    .addOption(OptionType.STRING, "codigo", "Código de verificación del servidor", true)
            ).queue();
            
            plugin.getLogger().info("[Discord] Bot de Discord iniciado correctamente!");
            
            // Limpiar códigos expirados cada 5 minutos
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanExpiredCodes, 6000L, 6000L);
            
        } catch (Exception e) {
            plugin.getLogger().severe("[Discord] Error al iniciar bot: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void stop() {
        if (jda != null) {
            plugin.getLogger().info("[Discord] Deteniendo bot de Discord...");
            jda.shutdown();
            try {
                if (!jda.awaitShutdown(10, TimeUnit.SECONDS)) {
                    jda.shutdownNow();
                }
            } catch (InterruptedException e) {
                jda.shutdownNow();
            }
        }
    }
    
    /**
     * Genera un código de verificación para un jugador
     */
    public String generateVerificationCode(Player player, String roleType) {
        // Eliminar código anterior si existe
        String oldCode = playerCodes.get(player.getUniqueId());
        if (oldCode != null) {
            pendingVerifications.remove(oldCode);
        }
        
        // Generar nuevo código de 6 dígitos
        String code = String.format("%06d", new Random().nextInt(1000000));
        
        // Asegurar que el código sea único
        while (pendingVerifications.containsKey(code)) {
            code = String.format("%06d", new Random().nextInt(1000000));
        }
        
        // Guardar verificación
        PlayerVerification verification = new PlayerVerification(
            player.getName(),
            player.getUniqueId(),
            roleType
        );
        
        pendingVerifications.put(code, verification);
        playerCodes.put(player.getUniqueId(), code);
        
        plugin.getLogger().info("[Discord] Código generado para " + player.getName() + ": " + code + " (Rol: " + roleType + ")");
        
        return code;
    }
    
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String commandName = event.getName();
        
        // Ya no se usan comandos slash, solo mensajes de texto en #general
        event.reply("❌ Este comando ya no está disponible. Por favor, escribe tu código directamente en el canal #general.")
            .setEphemeral(true)
            .queue();
    }
    
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignorar mensajes del bot
        if (event.getAuthor().isBot()) return;
        
        // Solo en el canal de verificación (#general)
        if (!event.getChannel().getId().equals(verificationChannelId)) return;
        
        String content = event.getMessage().getContentRaw().trim();
        
        // Detectar si es un código (6 dígitos)
        if (content.matches("\\d{6}")) {
            String code = content;
            
            // Verificar el código
            PlayerVerification verification = pendingVerifications.get(code);
            
            if (verification == null) {
                event.getMessage().delete().queue();
                event.getChannel().sendMessage(
                    event.getAuthor().getAsMention() + " ❌ Código inválido o expirado. Usa `/discordcode` en Minecraft para obtener un nuevo código."
                ).queue(msg -> msg.delete().queueAfter(10, TimeUnit.SECONDS));
                return;
            }
            
            // Verificar expiración (10 minutos)
            long elapsed = System.currentTimeMillis() - verification.timestamp;
            if (elapsed > 600000) {
                pendingVerifications.remove(code);
                playerCodes.remove(verification.playerUUID);
                event.getMessage().delete().queue();
                event.getChannel().sendMessage(
                    event.getAuthor().getAsMention() + " ❌ Código expirado. Usa `/discordcode` en Minecraft para obtener un nuevo código."
                ).queue(msg -> msg.delete().queueAfter(10, TimeUnit.SECONDS));
                return;
            }
            
            // Código válido - dar items en Minecraft
            event.getMessage().delete().queue();
            
            // Eliminar código usado
            pendingVerifications.remove(code);
            playerCodes.remove(verification.playerUUID);
            
            // Ejecutar comando en Minecraft
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(verification.playerUUID);
                if (player != null && player.isOnline()) {
                    // Ejecutar el comando correspondiente
                    String command = verification.roleType;
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command + " " + player.getName());
                    
                    player.sendMessage("§a§l✔ §f¡Código de Discord verificado! Has recibido las recompensas de §e/" + command + "§f!");
                    plugin.getLogger().info("[Discord] " + player.getName() + " verificó código y recibió recompensas de /" + command);
                } else {
                    plugin.getLogger().warning("[Discord] Jugador " + verification.playerName + " no está online para recibir recompensas");
                }
            });
            
            // Mensaje de confirmación en Discord
            event.getChannel().sendMessage(
                event.getAuthor().getAsMention() + " ✅ ¡Código verificado! Las recompensas han sido enviadas a **" + verification.playerName + "** en el servidor de Minecraft."
            ).queue(msg -> msg.delete().queueAfter(15, TimeUnit.SECONDS));
            
            plugin.getLogger().info("[Discord] " + event.getAuthor().getName() + " verificó código para " + verification.playerName + " (Comando: /" + verification.roleType + ")");
        }
    }
    
    private void cleanExpiredCodes() {
        long now = System.currentTimeMillis();
        pendingVerifications.entrySet().removeIf(entry -> {
            long elapsed = now - entry.getValue().timestamp;
            if (elapsed > 600000) { // 10 minutos
                playerCodes.remove(entry.getValue().playerUUID);
                return true;
            }
            return false;
        });
    }
    
    private String getRoleId(String roleType) {
        // IDs de roles de Discord - CONFIGURAR ESTOS IDs
        switch (roleType.toLowerCase()) {
            case "gaspi":
                return "ROLE_ID_GASPI"; // Reemplazar con el ID real del rol
            case "zatre":
                return "ROLE_ID_ZATRE"; // Reemplazar con el ID real del rol
            case "artistdagu":
                return "ROLE_ID_ARTISTDAGU"; // Reemplazar con el ID real del rol
            default:
                return null;
        }
    }
    
    public boolean isConnected() {
        return jda != null && jda.getStatus() == JDA.Status.CONNECTED;
    }
}
