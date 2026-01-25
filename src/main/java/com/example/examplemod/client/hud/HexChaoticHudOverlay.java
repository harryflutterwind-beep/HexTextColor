package com.example.examplemod.client.hud;

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
 * Shows the actual Chaotic orb item icon + a small bar indicating time until the next reroll.
 * Drawn above the hearts on the far left.
 *
 * NOTE:
 * - Displays ONLY when a Chaotic orb is "active" (held OR worn on armor).
 * - Reads the timers directly from the orb's NBT: HexChaosNextAt + HexChaosHold.
 */
public class HexChaoticHudOverlay {

    // 1.7.10 has no Minecraft#getRenderItem(); use our own RenderItem instance.
    private static final RenderItem ITEM_RENDER = new RenderItem();

    private static final String TAG_PROFILE       = "HexOrbProfile";
    private static final String TAG_CHAOS_NEXT_AT = "HexChaosNextAt"; // long world time
    private static final String TAG_CHAOS_HOLD    = "HexChaosHold";   // int ticks (duration of this cycle)

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event == null) return;
        // Render on HOTBAR so it also shows in creative (HEALTH can be skipped depending on HUD state).
        if (event.type != RenderGameOverlayEvent.ElementType.HOTBAR) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) return;
        if (mc.currentScreen != null) return; // avoid affecting GUI screens

        EntityPlayer p = mc.thePlayer;
        if (p.worldObj == null) return;

        ItemStack chaotic = findActiveChaotic(p);
        if (chaotic == null) return;

        NBTTagCompound tag = chaotic.getTagCompound();
        if (tag == null) return;

        long nextAt = tag.getLong(TAG_CHAOS_NEXT_AT);
        int hold = tag.getInteger(TAG_CHAOS_HOLD);
        if (hold <= 0) hold = 1;

        long now = p.worldObj.getTotalWorldTime();
        long remaining = nextAt - now;
        if (remaining < 0L) remaining = 0L;

        float frac = 1.0f - (remaining / (float) hold);
        if (frac < 0.0f) frac = 0.0f;
        if (frac > 1.0f) frac = 1.0f;

        ScaledResolution res = event.resolution;
        int sw = res.getScaledWidth();
        int sh = res.getScaledHeight();

        // Position: above hearts, far left
        int x = 2;
        int y = sh - 61; // hearts are ~sh-39; this sits above them

        // Slightly smaller overall (icon + bar)
        final float S = 0.85f;
        int sx = (int) (x / S);
        int sy = (int) (y / S);

        int __prevMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            GL11.glScalef(S, S, 1.0f);

            // Draw item icon (exact orb)
            RenderHelper.enableGUIStandardItemLighting();
            try {
                ITEM_RENDER.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), chaotic, sx, sy);
            } finally {
                RenderHelper.disableStandardItemLighting();
            }

            // Bar to the right of the icon
            int barX = sx + 16; // tighter spacing since we're scaling down
            int barY = sy + 5;
            int barW = 54;
            int barH = 6;

            int filled = (int) (barW * frac);

            // Background
            Gui.drawRect(barX, barY, barX + barW, barY + barH, 0xAA000000);
            // Fill (purple-ish)
            Gui.drawRect(barX, barY, barX + filled, barY + barH, 0xAA7A5CFF);

            // Optional: tiny tick/seconds text (kept minimal to avoid clutter)
            // int secs = (int) Math.ceil(remaining / 20.0);
            // mc.fontRenderer.drawStringWithShadow(secs + "s", barX + barW + 4, barY - 1, 0xE0E0E0);

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
        if (isChaotic(held)) return held;

        // Worn armor
        if (p.inventory != null && p.inventory.armorInventory != null) {
            for (int i = 0; i < p.inventory.armorInventory.length; i++) {
                ItemStack a = p.inventory.armorInventory[i];
                if (isChaotic(a)) return a;
            }
        }
        return null;
    }

    private boolean isChaotic(ItemStack stack) {
        if (stack == null) return false;
        if (!stack.hasTagCompound()) return false;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) return false;
        String prof = tag.getString(TAG_PROFILE);
        return prof != null && prof.startsWith("CHAOTIC_");
    }
}