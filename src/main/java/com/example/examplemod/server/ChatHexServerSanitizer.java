// src/main/java/com/example/examplemod/server/ChatHexServerSanitizer.java
package com.example.examplemod.server;

import java.util.regex.Pattern;

public final class ChatHexServerSanitizer {

    // Matches '#RRGGBB' that isn't part of a word
    private static final Pattern HEX = Pattern.compile("(?i)(?<!\\w)#([0-9a-f]{6})");

    private ChatHexServerSanitizer() {}

    /**
     * Called from NetHandlerPlayServer before vanilla logic.
     * We ONLY:
     *  - convert "#RRGGBB" → "§#RRGGBB" (for hex color support)
     *
     * We do NOT touch length or strip tags here anymore.
     */
    public static String sanitizeIncoming(String raw) {
        if (raw == null) return null;
        String s = raw;

        // Turn #RRGGBB into §#RRGGBB for clients that use §#hex
        s = HEX.matcher(s).replaceAll("§#$1");

        return s;
    }

    /**
     * Replacement for ChatAllowedCharacters.isAllowedCharacter.
     * Loosens the rules so our custom tags don't disconnect the player.
     */
    public static boolean isAllowedChatChar(char c) {
        // Allow standard printable chars (space and above) except hard control DEL
        if (c >= 32 && c != 127) return true;

        // Allow Minecraft formatting char
        if (c == '\u00A7') return true;

        // Optional: allow tab
        if (c == '\t') return true;

        // Everything else is treated as illegal
        return false;
    }
}
