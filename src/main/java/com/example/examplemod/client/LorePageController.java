package com.example.examplemod.client;

import net.minecraft.item.ItemStack;

/**
 * Tracks per-hovered-item tooltip page selection while the "view lore pages" key is held.
 *
 * This MUST use rebindable KeyBindings (LoreKeybinds), not hard-coded PgUp/PgDn.
 */
public class LorePageController {
    private static ItemStack lastStack = null;

    // 0-based index into VISIBLE pages (not necessarily raw stored pages).
    private static int viewPage = 0;

    private static boolean wasPrevDown = false;
    private static boolean wasNextDown = false;

    public static int getViewPage() {
        return viewPage;
    }

    public static void reset() {
        viewPage = 0;
        lastStack = null;
        wasPrevDown = false;
        wasNextDown = false;
    }

    public static void trackHovered(ItemStack stack) {
        if (stack == null) {
            reset();
            return;
        }

        // Reset page when hovering a different stack (item or NBT differs).
        if (lastStack == null || !ItemStack.areItemStacksEqual(stack, lastStack)) {
            viewPage = 0;
            lastStack = stack.copy();
            wasPrevDown = false;
            wasNextDown = false;
        }
    }

    /** Poll rebindable keys and update viewPage within [0, viewPages-1]. */
    public static void pollKeys(int viewPages) {
        if (viewPages <= 1) return;

        boolean prev = LoreKeybinds.isPrevDown();
        boolean next = LoreKeybinds.isNextDown();

        // Only advance once per key press (debounce).
        if (prev && !wasPrevDown) {
            viewPage--;
        }
        if (next && !wasNextDown) {
            viewPage++;
        }

        wasPrevDown = prev;
        wasNextDown = next;

        // Clamp.
        if (viewPage < 0) viewPage = 0;
        if (viewPage > viewPages - 1) viewPage = viewPages - 1;
    }
}
