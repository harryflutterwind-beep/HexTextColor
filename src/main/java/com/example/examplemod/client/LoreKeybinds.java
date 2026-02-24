package com.example.examplemod.client;

import cpw.mods.fml.client.registry.ClientRegistry;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

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


    /** True if a KeyBinding is currently held down. Supports keyboard and mouse bindings. */
    private static boolean isBindingDown(KeyBinding kb) {
        if (kb == null) return false;
        int code = kb.getKeyCode();
        if (code == 0) return false; // unbound
        try {
            if (code < 0) {
                // Mouse buttons use negative codes: -100 = MOUSE0, -99 = MOUSE1, ...
                return Mouse.isButtonDown(code + 100);
            }
            return Keyboard.isKeyDown(code);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * True while the player is holding the Lore View key.
     * Uses LWJGL polling so it works reliably inside GUIs (inventory/chest tooltips).
     */
    public static boolean isLoreViewHeld() {
        return isBindingDown(KB_LORE_VIEW);
    }

    /** Raw "is key down" for next/prev (works in GUIs). */
    public static boolean isNextDown() {
        return isBindingDown(KB_LORE_NEXT);
    }

    /** Raw "is key down" for next/prev (works in GUIs). */
    public static boolean isPrevDown() {
        return isBindingDown(KB_LORE_PREV);
    }
}
