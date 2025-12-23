package com.example.examplemod.client;

import net.minecraft.item.ItemStack;

/**
 * Tracks the active lore page while the player is hovering an item tooltip.
 * Polls keys (works inside GUIs) and uses a debounce so holding PgUp/PgDn
 * doesn't blast through pages.
 *
 * Paging model:
 *  - Page 0 = the item's normal tooltip/lore (shown when NOT holding the view key)
 *  - Page 1 = intentionally blank placeholder (we'll populate later)
 *  - Page 2+ = extra pages from LorePagesAPI
 */
public final class LorePageController {

    private static int page = 1; // first "view" page is blank placeholder
    private static ItemStack lastStack = null;

    private static long nextAllowedSwitchMs = 0L;
    private static boolean lastNextDown = false;
    private static boolean lastPrevDown = false;

    private LorePageController() {}

    public static int getPage() {
        return page;
    }

    /** Reset paging when hovering a different item. */
    public static void trackHovered(ItemStack stack) {
        if (stack == null) return;

        if (lastStack == null || !ItemStack.areItemStacksEqual(stack, lastStack)) {
            lastStack = stack.copy();
            page = 1; // always start at placeholder page when you hold the key
            nextAllowedSwitchMs = 0L;
            lastNextDown = false;
            lastPrevDown = false;
        }
    }

    /** Poll PgUp/PgDn with debounce + edge detection. */
    public static void pollKeys(int maxPages) {
        if (maxPages <= 1) return;

        boolean nextDown = LoreKeybinds.isNextDown();
        boolean prevDown = LoreKeybinds.isPrevDown();

        long now = System.currentTimeMillis();

        // Rising-edge detection so you can tap to switch, plus debounce for hold.
        boolean nextPressed = nextDown && !lastNextDown;
        boolean prevPressed = prevDown && !lastPrevDown;

        lastNextDown = nextDown;
        lastPrevDown = prevDown;

        if (!nextPressed && !prevPressed) {
            // allow hold-scrolling after debounce window
            if (now >= nextAllowedSwitchMs) {
                // if holding, treat as pressed
                if (nextDown) nextPressed = true;
                if (prevDown) prevPressed = true;
            } else {
                return;
            }
        }

        if (now < nextAllowedSwitchMs) return;

        if (nextPressed) page++;
        if (prevPressed) page--;

        // clamp to "view pages" (1..maxPages-1). page 0 is never shown while held.
        if (page < 1) page = 1;
        if (page > maxPages - 1) page = maxPages - 1;

        nextAllowedSwitchMs = now + 120L;
    }
}
