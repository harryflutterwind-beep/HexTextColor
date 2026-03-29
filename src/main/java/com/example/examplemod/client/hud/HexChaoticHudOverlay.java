package com.example.examplemod.client.hud;

import com.example.examplemod.api.HexSocketAPI;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.lwjgl.opengl.GL11;

/**
 * Chaotic Orb HUD (MC 1.7.10)
 *
 * Shows the Chaotic orb icon + an animated gradient charge bar.
 * Drawn above the hearts on the far left.
 *
 * Notes:
 * - Uses Darkfire-style GL state isolation for the item render so the orb never looks "faded"
 * - Bar is a scrolling 3-stop gradient (magenta -> violet -> cyan) that matches tooltip vibes
 */
public class HexChaoticHudOverlay {

    // 1.7.10 has no Minecraft#getRenderItem(); use our own RenderItem instance.
    private static final RenderItem ITEM_RENDER = new RenderItem();

    private static final String TAG_PROFILE       = "HexOrbProfile";
    private static final String TAG_CHAOS_NEXT_AT = "HexChaosNextAt"; // long epoch ms
    private static final String TAG_CHAOS_HOLD    = "HexChaosHold";   // int ticks (duration of this cycle)

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event == null) return;
        // Render on HOTBAR so it also shows in creative (HEALTH can be skipped depending on HUD state).
        if (event.type != RenderGameOverlayEvent.ElementType.HOTBAR) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) return;
        EntityPlayer p = mc.thePlayer;
        if (p.worldObj == null) return;

        ItemStack chaotic = findActiveChaotic(p);
        if (chaotic == null) return;

        NBTTagCompound tag = chaotic.getTagCompound();
        if (tag == null) return;

        long nextAt = tag.getLong(TAG_CHAOS_NEXT_AT);
        int hold = tag.getInteger(TAG_CHAOS_HOLD);
        if (hold <= 0) hold = 1;

        long now = System.currentTimeMillis();
        // Server stores NEXT_AT as epoch milliseconds; HOLD is stored as ticks (20 tps).
        long remainingMs = nextAt - now;

        long totalMs = (long) hold * 50L;
        if (totalMs <= 0L) totalMs = 1L;

        // Clamp so bar doesn't go weird if old values are present.
        if (remainingMs > totalMs) remainingMs = totalMs;
        if (remainingMs < 0L) remainingMs = 0L;

        float frac = 1.0f - (remainingMs / (float) totalMs);
        if (frac < 0.0f) frac = 0.0f;
        if (frac > 1.0f) frac = 1.0f;

        ScaledResolution res = event.resolution;
        int sh = res.getScaledHeight();

        // Position: above hearts, far left
        int x = 2;
        int y = sh - 81; // hearts are ~sh-39; this sits above them

        // Slightly smaller overall (icon + bar)
        final float S = 0.85f;
        int sx = (int) (x / S);
        int sy = (int) (y / S);

        int __prevMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            GL11.glScalef(S, S, 1.0f);

            // ---- Orb icon (state isolated so it never inherits alpha/tint) ----
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            try {
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glDisable(GL11.GL_FOG);
                GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glColor4f(1f, 1f, 1f, 1f);

                RenderHelper.enableGUIStandardItemLighting();
                try {
                    ITEM_RENDER.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), chaotic, sx, sy);
                } finally {
                    RenderHelper.disableStandardItemLighting();
                }

                GL11.glColor4f(1f, 1f, 1f, 1f);
            } finally {
                GL11.glPopAttrib();
            }

            // ---- Animated gradient bar ----
            // Bar to the right of the icon
            int barX = sx + 16;
            int barY = sy + 5;
            int barW = 54;
            int barH = 6;

            int filled = (int) (barW * frac);
            if (filled < 0) filled = 0;
            if (filled > barW) filled = barW;

            // Background: very subtle (not a "black box")
            int bg = 0x18000000;
            Gui.drawRect(barX, barY, barX + barW, barY + barH, bg);

            // Outline (thin)
            int outline = 0xA05B2EFF; // purple outline
            drawOutline(barX, barY, barW, barH, outline);

            // Fill: scrolling gradient
            long tMs = now;
            float phase = (tMs % 1200L) / 1200.0f; // 0..1

            // gentle pulse for alpha
            float pulse = 0.70f + 0.30f * (float) Math.sin((tMs % 2000L) / 2000.0f * (Math.PI * 2.0));

            // draw 1px vertical strips across filled portion
            for (int i = 0; i < filled; i++) {
                float u = (filled <= 1) ? 0f : (i / (float) (filled - 1));
                float g = fract(u + phase); // scrolling
                int rgb = chaoticGradientRGB(g);
                int col = withAlpha(rgb, (int) (170 * pulse)); // 0..255
                Gui.drawRect(barX + i, barY, barX + i + 1, barY + barH, col);
            }

            // Shimmer highlight line (subtle, rides inside filled area)
            if (filled > 4) {
                float shPhase = fract(phase * 1.7f);
                int lx = barX + (int) (shPhase * (filled - 1));
                int glow = withAlpha(0xD6F8FF, 140); // pale cyan
                Gui.drawRect(lx, barY, lx + 1, barY + barH, glow);
                // slight 2px shoulder
                int glow2 = withAlpha(0xB9E7FF, 85);
                if (lx - 1 >= barX) Gui.drawRect(lx - 1, barY, lx, barY + barH, glow2);
                if (lx + 1 <= barX + filled) Gui.drawRect(lx + 1, barY, lx + 2, barY + barH, glow2);
            }

        } finally {
            // reset common state we touch
            GL11.glColor4f(1f, 1f, 1f, 1f);
            GL11.glPopMatrix();
            GL11.glMatrixMode(__prevMatrixMode);
        }
    }

    private ItemStack findActiveChaotic(EntityPlayer p) {
        if (p == null) return null;

        // Held (mainhand / selected hotbar)
        ItemStack held = p.getCurrentEquippedItem();
        ItemStack found = findChaoticOnStackOrSockets(held);
        if (found != null) return found;

        // Worn armor
        if (p.inventory != null && p.inventory.armorInventory != null) {
            for (int i = 0; i < p.inventory.armorInventory.length; i++) {
                ItemStack a = p.inventory.armorInventory[i];
                found = findChaoticOnStackOrSockets(a);
                if (found != null) return found;
            }
        }
        return null;
    }

    private ItemStack findChaoticOnStackOrSockets(ItemStack host) {
        if (isChaotic(host)) return host;
        if (host == null) return null;

        try {
            int filled = HexSocketAPI.getSocketsFilled(host);
            for (int i = 0; i < filled; i++) {
                ItemStack gem = HexSocketAPI.getGemAt(host, i);
                if (isChaotic(gem)) return gem;
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private boolean isChaotic(ItemStack stack) {
        if (stack == null) return false;
        if (!stack.hasTagCompound()) return false;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) return false;
        String prof = tag.getString(TAG_PROFILE);
        if (prof != null && prof.startsWith("CHAOTIC_")) return true;

        // Fallback: chaotic stacks always carry these tags.
        return tag.hasKey(TAG_CHAOS_NEXT_AT) || tag.hasKey(TAG_CHAOS_HOLD);
    }

    // ----- tiny helpers (self-contained, no external deps) -----

    private static void drawOutline(int x, int y, int w, int h, int col) {
        // top
        Gui.drawRect(x, y, x + w, y + 1, col);
        // bottom
        Gui.drawRect(x, y + h - 1, x + w, y + h, col);
        // left
        Gui.drawRect(x, y, x + 1, y + h, col);
        // right
        Gui.drawRect(x + w - 1, y, x + w, y + h, col);
    }

    private static int withAlpha(int rgb, int a) {
        if (a < 0) a = 0;
        if (a > 255) a = 255;
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    private static float fract(float v) {
        return v - (float) Math.floor(v);
    }

    /**
     * 3-stop looped gradient:
     *  0.00 -> 0.50 : magenta -> violet
     *  0.50 -> 1.00 : violet  -> cyan
     * and wraps naturally due to fract()
     */
    private static int chaoticGradientRGB(float t) {
        // Tooltip-ish palette:
        // magenta/pink, violet, cyan
        final int MAG = 0xFF3FD6;
        final int VIO = 0x8B4CFF;
        final int CYA = 0x32E9FF;

        if (t < 0.5f) {
            float u = t / 0.5f;
            return lerpRGB(MAG, VIO, u);
        } else {
            float u = (t - 0.5f) / 0.5f;
            return lerpRGB(VIO, CYA, u);
        }
    }

    private static int lerpRGB(int a, int b, float t) {
        if (t < 0f) t = 0f;
        if (t > 1f) t = 1f;
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        int rr = (int) (ar + (br - ar) * t);
        int rg = (int) (ag + (bg - ag) * t);
        int rb = (int) (ab + (bb - ab) * t);
        return (rr << 16) | (rg << 8) | rb;
    }
}
