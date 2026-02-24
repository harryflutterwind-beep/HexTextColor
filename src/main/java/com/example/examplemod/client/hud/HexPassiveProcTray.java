package com.example.examplemod.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

/**
 * Simple shared "passive proc tray" layout helper.
 *
 * Goal: multiple independent HUD overlays can stack blocks vertically without overlapping.
 *
 * MC/Forge: 1.7.10
 */
public final class HexPassiveProcTray {

    private static long FRAME_KEY = Long.MIN_VALUE;
    private static int  CURSOR_Y = 0;
    private static int  START_Y = 0;

    // Right-side padding from the screen edge
    private static final int PAD_X = 8;
    // Gap between blocks
    private static final int GAP_Y = 6;

    private HexPassiveProcTray() {}

    /** Right-aligned X for a block of width w. */
    public static int baseX(ScaledResolution sr, int w) {
        if (sr == null) return 0;
        int sw = sr.getScaledWidth();
        return sw - PAD_X - w;
    }

    /**
     * Allocate the top Y for a HUD block of height blockH (in pixels).
     *
     * Blocks stack vertically (top-down) and reset every render frame.
     * We also try to start below the potion icons area.
     */
    public static int allocTopY(Minecraft mc, RenderGameOverlayEvent.Post event, EntityPlayer p, int blockH) {
        if (mc == null || mc.theWorld == null || event == null) return 0;

        long now = mc.theWorld.getTotalWorldTime();
        float pt = 0f;
        try { pt = event.partialTicks; } catch (Throwable ignored) {}

        long key = (now << 16) ^ (long) (pt * 1000f);
        if (key != FRAME_KEY) {
            FRAME_KEY = key;
            START_Y = computeStartY(p);
            CURSOR_Y = START_Y;
        }

        int y = CURSOR_Y;
        int h = blockH;
        if (h < 18) h = 18;
        CURSOR_Y += h + GAP_Y;

        return y;
    }

    private static int computeStartY(EntityPlayer p) {
        int start = 6;
        try {
            if (p != null && p.getActivePotionEffects() != null) {
                int count = p.getActivePotionEffects().size();
                // Vanilla HUD tends to use up to 5 rows before starting a new column.
                int rows = count;
                if (rows > 5) rows = 5;
                start += rows * 33;
            }
        } catch (Throwable ignored) {}
        return start;
    }
}
