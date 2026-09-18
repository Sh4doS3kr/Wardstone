package com.moonlight.coreprotect.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Convierte texto normal a Small Caps Unicode.
 * Preserva § color codes, números y símbolos especiales.
 */
public final class SmallCaps {

    private static final String FROM = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String TO   = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";

    private SmallCaps() {}

    public static String convert(String input) {
        if (input == null || input.isEmpty()) return input;
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            // Preserve <glyph:...> and <shift:...> tags verbatim (Nexo MiniMessage tags)
            if (c == '<' && i + 1 < input.length()) {
                int close = input.indexOf('>', i);
                if (close > i) {
                    String tag = input.substring(i + 1, close);
                    if (tag.startsWith("glyph:") || tag.startsWith("shift:")) {
                        sb.append(input, i, close + 1);
                        i = close;
                        continue;
                    }
                }
            }
            if (c == '§' && i + 1 < input.length()) {
                sb.append(c).append(input.charAt(++i));
                continue;
            }
            int idx = FROM.indexOf(c);
            if (idx >= 0) {
                sb.append(TO.charAt(idx));
            } else {
                switch (c) {
                    case '\u00e1': case '\u00c1': sb.append('\u1d00'); break;
                    case '\u00e9': case '\u00c9': sb.append('\u1d07'); break;
                    case '\u00ed': case '\u00cd': sb.append('\u026a'); break;
                    case '\u00f3': case '\u00d3': sb.append('\u1d0f'); break;
                    case '\u00fa': case '\u00da': sb.append('\u1d1c'); break;
                    case '\u00f1': case '\u00d1': sb.append(c); break;
                    case '\u00fc': case '\u00dc': sb.append('\u1d1c'); break;
                    default: sb.append(c); break;
                }
            }
        }
        return sb.toString();
    }

    public static List<String> convertList(List<String> input) {
        if (input == null) return null;
        List<String> result = new ArrayList<>(input.size());
        for (String s : input) result.add(convert(s));
        return result;
    }
}
