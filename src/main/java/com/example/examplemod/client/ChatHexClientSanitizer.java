// src/main/java/com/example/examplemod/client/ChatHexClientSanitizer.java
package com.example.examplemod.client;

public final class ChatHexClientSanitizer {

    private ChatHexClientSanitizer() {}

    /**
     * Pre-process outgoing chat, similar to HexText's MixinGuiChat:
     * - convert &x codes to §x
     * - normalize spacing
     * - you can add more rules later if wanted
     */
    public static String preSendChat(String original) {
        if (original == null) return null;

        String s = original;

        // convert & to § for vanilla-style color codes
        // (if you don't want this, you can comment it out)
        s = s.replace('&', '§');

        // simple normalize space (vanilla does something similar)
        s = s.trim().replaceAll("\\s+", " ");

        return s;
    }
}
