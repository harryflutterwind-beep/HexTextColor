package com.example.examplemod.client;

import cpw.mods.fml.client.registry.ClientRegistry;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

/**
 * Client-only keybinds for Lore Pages.
 *
 * Defaults:
 *  - Hold L    : enable Lore Pages mode
 *  - Page Up   : next page
 *  - Page Down : previous page
 *
 * All are rebindable in Controls.
 */
public final class LoreKeybinds {

    public static KeyBinding KB_LORE_VIEW;
    public static KeyBinding KB_LORE_NEXT;
    public static KeyBinding KB_LORE_PREV;

    private LoreKeybinds() {}

    /** Call from your ClientProxy init (FMLInitializationEvent). */
    public static void init() {
        KB_LORE_VIEW = new KeyBinding("loreview", Keyboard.KEY_L, "HexColorText");
        KB_LORE_NEXT = new KeyBinding("lorenext", Keyboard.KEY_PRIOR, "HexColorText"); // PgUp
        KB_LORE_PREV = new KeyBinding("loreprev", Keyboard.KEY_NEXT,  "HexColorText"); // PgDn

        ClientRegistry.registerKeyBinding(KB_LORE_VIEW);
        ClientRegistry.registerKeyBinding(KB_LORE_NEXT);
        ClientRegistry.registerKeyBinding(KB_LORE_PREV);
    }

    /**
     * Dynamic key display name that follows the Controls menu binding.
     * Example: "L", "RSHIFT", "MOUSE4", etc.
     */
    public static String getLoreViewKeyDisplay() {
        if (KB_LORE_VIEW == null) return "Unbound";
        int code = KB_LORE_VIEW.getKeyCode();
        if (code == 0) return "Unbound";
        try {
            return GameSettings.getKeyDisplayString(code);
        } catch (Throwable t) {
            return "Unbound";
        }
    }

    /**
     * True while the player is holding the Lore View key.
     * Uses LWJGL polling so it works reliably inside GUIs (inventory/chest tooltips).
     */
    public static boolean isLoreViewHeld() {
        if (KB_LORE_VIEW == null) return false;

        int code = KB_LORE_VIEW.getKeyCode();
        if (code == 0) return false; // unbound

        // Works even when a GUI is open.
        return Keyboard.isKeyDown(code);
    }

    /** Raw "is key down" for next/prev (works in GUIs). */
    public static boolean isNextDown() {
        if (KB_LORE_NEXT == null) return false;
        int code = KB_LORE_NEXT.getKeyCode();
        if (code == 0) return false;
        return Keyboard.isKeyDown(code);
    }

    /** Raw "is key down" for next/prev (works in GUIs). */
    public static boolean isPrevDown() {
        if (KB_LORE_PREV == null) return false;
        int code = KB_LORE_PREV.getKeyCode();
        if (code == 0) return false;
        return Keyboard.isKeyDown(code);
    }
}
