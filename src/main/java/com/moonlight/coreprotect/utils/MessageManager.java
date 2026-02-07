package com.moonlight.coreprotect.utils;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

public class MessageManager {

    private final CoreProtectPlugin plugin;
    private FileConfiguration messages;
    private String prefix;

    public MessageManager(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    private void loadMessages() {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);
        prefix = colorize(messages.getString("prefix", "&8[&bNucleos&8] "));
    }

    public void send(Player player, String path) {
        String message = getMessage(path);
        if (message != null && !message.isEmpty()) {
            player.sendMessage(prefix + message);
        }
    }

    public void send(Player player, String path, String... replacements) {
        String message = getMessage(path);
        if (message != null && !message.isEmpty()) {
            for (int i = 0; i < replacements.length; i += 2) {
                if (i + 1 < replacements.length) {
                    message = message.replace(replacements[i], replacements[i + 1]);
                }
            }
            player.sendMessage(prefix + message);
        }
    }

    public void sendRaw(Player player, String path) {
        String message = getMessage(path);
        if (message != null && !message.isEmpty()) {
            player.sendMessage(message);
        }
    }

    public void sendRaw(Player player, String path, String... replacements) {
        String message = getMessage(path);
        if (message != null && !message.isEmpty()) {
            for (int i = 0; i < replacements.length; i += 2) {
                if (i + 1 < replacements.length) {
                    message = message.replace(replacements[i], replacements[i + 1]);
                }
            }
            player.sendMessage(message);
        }
    }

    public void sendList(Player player, String path) {
        List<String> messageList = getMessageList(path);
        for (String message : messageList) {
            player.sendMessage(message);
        }
    }

    public void sendList(Player player, String path, String... replacements) {
        List<String> messageList = getMessageList(path);
        for (String message : messageList) {
            for (int i = 0; i < replacements.length; i += 2) {
                if (i + 1 < replacements.length) {
                    message = message.replace(replacements[i], replacements[i + 1]);
                }
            }
            player.sendMessage(message);
        }
    }

    public String getMessage(String path) {
        String message = messages.getString(path);
        return message != null ? colorize(message) : "";
    }

    public List<String> getMessageList(String path) {
        return messages.getStringList(path).stream()
                .map(this::colorize)
                .collect(Collectors.toList());
    }

    public String getPrefix() {
        return prefix;
    }

    private String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
