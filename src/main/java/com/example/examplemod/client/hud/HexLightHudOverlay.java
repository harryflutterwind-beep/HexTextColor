package com.example.examplemod.client.hud;


import com.example.examplemod.client.fractured.FracturedKeyHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import com.example.examplemod.api.HexSocketAPI;
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
import org.lwjgl.opengl.GL12;

import java.util.Random;

/**
 * Light Orb HUD (MC 1.7.10)
 *
 * Shows the actual Light orb item icon + a small Radiance bar (0..100)
 * and the current Light type (Radiant/Beacon/Solar/Halo/Angelic).
 *
 * IMPORTANT:
 * - This overlay must NEVER leak GL state into other HUD elements.
 *   We wrap rendering in PushAttrib/PopAttrib and always restore matrix mode.
 */
public class HexLightHudOverlay extends Gui {

    // 1.7.10 has no Minecraft#getRenderItem(); use our own RenderItem instance.
    private static final RenderItem ITEM_RENDER = new RenderItem();

    private static final String TAG_PROFILE     = "HexOrbProfile";
    private static final String TAG_LIGHT_TYPE  = "HexLightType";
    private static final String TAG_LIGHT_RAD   = "HexLightRadiance";
    private static final String TAG_L_WARD_END  = "HexLightWardEnd";

    // Solar Beam cooldown tags (server writes, HUD reads)
    private static final String TAG_L_BEAM_CD     = "HexLightBeamCd";
    private static final String TAG_L_BEAM_CD_MAX = "HexLightBeamCdMax";

    // Track last seen Ward end to detect activation bursts
    private static long LAST_WARD_END_SEEN = 0L;

    // Throttle particles so we don't spam every single frame
    private static long LAST_WARD_GLOW_TICK = -9999L;

    private static final Random RNG = new Random();

    // Warm white / yellow-ish glow. Tweak B:
    // - more yellow: lower B (0.60-0.70)
    // - more white: higher B (0.85-0.95)
    private static final double WARD_R = 1.00D;
    private static final double WARD_G = 1.00D;
    private static final double WARD_B = 0.78D;

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event == null) return;
        if (event.type != RenderGameOverlayEvent.ElementType.HOTBAR) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) return;

        // If a GUI is open (Controls, Options, etc.), DO NOT render overlay.
        if (mc.currentScreen != null) return;

        EntityPlayer p = mc.thePlayer;
        if (p.worldObj == null) return;

        ItemStack light = FracturedKeyHandler.getSelectedHudStackOfKind(p, FracturedKeyHandler.HUD_KIND_LIGHT);
        if (light == null) return;

        NBTTagCompound tag = light.getTagCompound();
        if (tag == null) return;

        String type = tag.getString(TAG_LIGHT_TYPE);
        int rad = tag.getInteger(TAG_LIGHT_RAD);
        if (rad < 0) rad = 0;
        if (rad > 100) rad = 100;

        // ─────────────────────────────────────────────────────────────
        // Radiant Ward glow (CLIENT-SIDE particles; warm white/yellow)
        // This is safe and avoids server particle packet edge cases.
        // ─────────────────────────────────────────────────────────────
        long now = p.worldObj.getTotalWorldTime();
        long wardEnd = tag.getLong(TAG_L_WARD_END);
        if (wardEnd > now) {
            // If Ward just (re)triggered, emit a short burst once
            if (wardEnd > LAST_WARD_END_SEEN) {
                spawnRadiantWardBurst(mc, p);
                LAST_WARD_END_SEEN = wardEnd;
            }
            spawnRadiantWardGlow(mc, p, now);
        }

        // Some environments can give null resolution in edge cases; be defensive.
        ScaledResolution res = event.resolution;
        if (res == null) return;

        int sh = res.getScaledHeight();

        // Position: above hearts, far left
        int x = 2;
        int y = sh - 61;

        // Slightly smaller overall (icon + bar)
        final float S = 0.85f;
        int sx = (int) (x / S);
        int sy = (int) (y / S);

        int prevMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);

        // Hard guard against GL state leaks (hotbar darkening / tinting).
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            GL11.glColor4f(1f, 1f, 1f, 1f);
            GL11.glScalef(S, S, 1.0f);

            // Draw the item icon
            RenderHelper.enableGUIStandardItemLighting();
            try {
                ITEM_RENDER.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), light, sx, sy);
            } finally {
                RenderHelper.disableStandardItemLighting();
                // Extra safety: vanilla expects lighting off for HUD quads.
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glDisable(GL12.GL_RESCALE_NORMAL);
                GL11.glColor4f(1f, 1f, 1f, 1f);
            }

            // Radiance bar next to icon
            int barX = sx + 18;
            int barY = sy + 6;
            int barW = 46;
            int barH = 6;

            // Backdrop
            drawRect(barX, barY, barX + barW, barY + barH, 0xAA000000);

            int fill = (int) (barW * (rad / 100.0f));
            if (fill < 0) fill = 0;
            if (fill > barW) fill = barW;

            // Fill (gold-ish)
            if (fill > 2) {
                drawRect(barX + 1, barY + 1, barX + 1 + fill - 2, barY + barH - 1, 0xAAFFD36B);
            }

// Solar Beam cooldown bar (fills up as the cooldown recovers)
            if (type != null && "Solar".equalsIgnoreCase(type)) {
                int cd = tag.getInteger(TAG_L_BEAM_CD);
                int cdMax = tag.getInteger(TAG_L_BEAM_CD_MAX);
                if (cdMax <= 0) cdMax = cd;
                if (cd > 0 && cdMax > 0) {
                    // A thinner bar just under the radiance bar
                    int cdBarY = barY + barH + 2;
                    int cdBarH = 3;

                    drawRect(barX, cdBarY, barX + barW, cdBarY + cdBarH, 0xAA000000);

                    float fillF = 1.0f - (cd / (float) cdMax);
                    if (fillF < 0f) fillF = 0f;
                    if (fillF > 1f) fillF = 1f;

                    int cdFill = (int) (barW * fillF);
                    if (cdFill > 2) {
                        drawRect(barX + 1, cdBarY + 1, barX + 1 + cdFill - 2, cdBarY + cdBarH - 1, 0xAAFFF0A3);
                    }

                    int secs = (int) Math.ceil(cd / 20.0);
                    String cdTxt = "§eBeam§7: §f" + secs + "s";
                    mc.fontRenderer.drawStringWithShadow(cdTxt, barX, cdBarY + 4, 0xFFFFFF);
                }
            }

            // Text
            String label = (type == null || type.length() == 0) ? "Light" : type;
            String txt = "\u00A7f" + label + "\u00A77  " + "\u00A7e" + rad + "\u00A77%";
            mc.fontRenderer.drawStringWithShadow(txt, barX, barY - 10, 0xFFFFFF);

        } finally {
            // Always restore state (prevents "dark mode" hotbar after F5, etc.)
            GL11.glColor4f(1f, 1f, 1f, 1f);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            GL11.glMatrixMode(prevMatrixMode);
        }
    }

    /**
     * Soft glow while Ward is active.
     * Throttled to once per 2 world ticks (not per-frame).
     */
    private void spawnRadiantWardGlow(Minecraft mc, EntityPlayer p, long now) {
        if (mc == null || mc.theWorld == null || p == null) return;

        // throttle: only once every 2 ticks
        if (now <= LAST_WARD_GLOW_TICK) return;
        if ((now & 1L) != 0L) return;
        LAST_WARD_GLOW_TICK = now;

        // Subtle aura around the body
        final int particles = 6; // tweak intensity
        for (int i = 0; i < particles; i++) {
            double ox = (RNG.nextDouble() - 0.5D) * 1.1D;
            double oz = (RNG.nextDouble() - 0.5D) * 1.1D;
            double oy = 0.15D + (RNG.nextDouble() * 1.55D);

            mc.theWorld.spawnParticle(
                    "reddust",
                    p.posX + ox,
                    p.posY + oy,
                    p.posZ + oz,
                    WARD_R, WARD_G, WARD_B
            );
        }
    }

    /**
     * One-time burst when Ward activates.
     */
    private void spawnRadiantWardBurst(Minecraft mc, EntityPlayer p) {
        if (mc == null || mc.theWorld == null || p == null) return;

        final int bursts = 26; // tweak intensity
        for (int i = 0; i < bursts; i++) {
            double ox = (RNG.nextDouble() - 0.5D) * 1.8D;
            double oz = (RNG.nextDouble() - 0.5D) * 1.8D;
            double oy = 0.10D + (RNG.nextDouble() * 1.9D);

            mc.theWorld.spawnParticle(
                    "reddust",
                    p.posX + ox,
                    p.posY + oy,
                    p.posZ + oz,
                    WARD_R, WARD_G, WARD_B
            );
        }
    }

    private ItemStack findLightInSockets(ItemStack host) {
        if (host == null) return null;
        try {
            int filled = HexSocketAPI.getSocketsFilled(host);
            for (int i = 0; i < filled; i++) {
                ItemStack g = HexSocketAPI.getGemAt(host, i);
                if (isLight(g)) return g;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private ItemStack findActiveLight(EntityPlayer p) {
        if (p == null || p.inventory == null) return null;

        // Held: direct or socketed
        ItemStack held = p.getCurrentEquippedItem();
        if (isLight(held)) return held;

        ItemStack g = findLightInSockets(held);
        if (g != null) return g;

        // Armor: direct or socketed
        for (int i = 0; i < p.inventory.armorInventory.length; i++) {
            ItemStack a = p.inventory.armorInventory[i];
            if (isLight(a)) return a;

            g = findLightInSockets(a);
            if (g != null) return g;
        }

        return null;
    }


    private boolean isLight(ItemStack stack) {
        if (stack == null) return false;
        if (!stack.hasTagCompound()) return false;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) return false;

        String prof = tag.getString(TAG_PROFILE);
        if (prof != null && prof.startsWith("LIGHT_")) return true;

        // Socketed/stripped fallback: keep showing HUD if Light keys are present.
        return tag.hasKey(TAG_LIGHT_TYPE) || tag.hasKey(TAG_LIGHT_RAD);
    }
}
