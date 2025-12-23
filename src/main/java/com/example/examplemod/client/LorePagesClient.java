package com.example.examplemod.client;

import net.minecraft.client.settings.GameSettings;

/** Client-only helpers for Lore Pages. */
public final class LorePagesClient {

    private LorePagesClient() {}

    public static String getLoreViewKeyName() {
        if (LoreKeybinds.KB_LORE_VIEW == null) return "KEY";
        int code = LoreKeybinds.KB_LORE_VIEW.getKeyCode();
        try {
            return GameSettings.getKeyDisplayString(code);
        } catch (Throwable t) {
            return "KEY";
        }
    }
}
