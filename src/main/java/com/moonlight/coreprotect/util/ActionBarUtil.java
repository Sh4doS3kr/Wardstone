package com.moonlight.coreprotect.util;

import org.bukkit.entity.Player;

/**
 * Utilidad para enviar mensajes de Action Bar compatible con Spigot y Paper.
 * Usa reflexión para soportar múltiples versiones de API.
 */
public class ActionBarUtil {

    private ActionBarUtil() {}

    public static void send(Player player, String message) {
        // Método 1: Paper Adventure API
        try {
            Class<?> componentClass = Class.forName("net.kyori.adventure.text.Component");
            java.lang.reflect.Method textMethod = componentClass.getMethod("text", String.class);
            Object component = textMethod.invoke(null, message);
            java.lang.reflect.Method sendMethod = player.getClass().getMethod("sendActionBar", componentClass);
            sendMethod.invoke(player, component);
            return;
        } catch (Exception ignored) {}

        // Método 2: Spigot BungeeChat API
        try {
            Class<?> chatType = Class.forName("net.md_5.bungee.api.ChatMessageType");
            Object actionBar = Enum.valueOf((Class<Enum>) chatType, "ACTION_BAR");
            Class<?> textCompClass = Class.forName("net.md_5.bungee.api.chat.TextComponent");
            Object textComp = textCompClass.getConstructor(String.class).newInstance(message);
            Class<?> baseCompClass = Class.forName("net.md_5.bungee.api.chat.BaseComponent");
            Object spigot = player.getClass().getMethod("spigot").invoke(player);
            for (java.lang.reflect.Method m : spigot.getClass().getMethods()) {
                if (m.getName().equals("sendMessage")) {
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length == 2 && params[0].equals(chatType)) {
                        Object arr = java.lang.reflect.Array.newInstance(baseCompClass, 1);
                        java.lang.reflect.Array.set(arr, 0, textComp);
                        m.invoke(spigot, actionBar, arr);
                        return;
                    }
                }
            }
        } catch (Exception ignored) {}

        // Método 3: Fallback a subtitle
        player.sendTitle("", message, 0, 30, 5);
    }
}
