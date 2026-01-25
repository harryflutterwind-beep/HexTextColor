package com.example.examplemod.client.fractured;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public class FracturedHudOverlay extends Gui {

    private static final String TAG_SHARDS = "HexFracShards";
    // Server uses SNAP ticks for the temporary buff window.
    private static final String TAG_SNAP_TICKS = "HexFracSnapTicks";
    private static final String TAG_SNAP_MAX   = "HexFracSnapMax";

    private static final int MAX_SHARDS = 5;
    private static final int DEFAULT_SNAP_MAX = 120; // 6s @ 20t/s (server default)

    private final RenderItem itemRenderer = new RenderItem();

    @SubscribeEvent
    public void onRender(RenderGameOverlayEvent.Post e) {
        if (e.type != RenderGameOverlayEvent.ElementType.EXPERIENCE) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.gameSettings.showDebugInfo) return;

        ItemStack s = FracturedUtil.findEquippedFractured(mc.thePlayer);
        if (s == null) return;

        ScaledResolution res = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int sw = res.getScaledWidth();
        int sh = res.getScaledHeight();

        // Position: above the XP level number (center-bottom)
        int baseX = (sw / 2) - 8;
        int baseY = sh - 56;

        float bob = (float) Math.sin((mc.thePlayer.ticksExisted + e.partialTicks) * 0.12f) * 2.0f;
        int iconY = baseY + (int) bob;

        // Force animated meta for display (even meta=flat, odd meta=anim)
        ItemStack display = s.copy();
        display.setItemDamage(display.getItemDamage() | 1);

        int __prevMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            RenderHelper.enableGUIStandardItemLighting();
            try {

                itemRenderer.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), display, baseX, iconY);
                itemRenderer.renderItemOverlayIntoGUI(mc.fontRenderer, mc.getTextureManager(), display, baseX, iconY);

            } finally {
                RenderHelper.disableStandardItemLighting();
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glDisable(GL12.GL_RESCALE_NORMAL);
                GL11.glColor4f(1f,1f,1f,1f);
            }
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        } finally {
            // reset common state we touch
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            GL11.glColor4f(1f, 1f, 1f, 1f);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            GL11.glMatrixMode(__prevMatrixMode);
        }

        int shards = 0;
        int snapTicks = 0;
        int snapMax = 0;

        if (s.hasTagCompound()) {
            if (s.getTagCompound().hasKey(TAG_SHARDS, 3)) shards = s.getTagCompound().getInteger(TAG_SHARDS);
            if (s.getTagCompound().hasKey(TAG_SNAP_TICKS, 3)) snapTicks = s.getTagCompound().getInteger(TAG_SNAP_TICKS);
            if (s.getTagCompound().hasKey(TAG_SNAP_MAX, 3)) snapMax = s.getTagCompound().getInteger(TAG_SNAP_MAX);
        }

        if (snapMax <= 0) snapMax = DEFAULT_SNAP_MAX;

        // Shard counter (right of icon)
        String shardText = shards + "/" + MAX_SHARDS;
        int textX = baseX + 18;
        int textY = iconY + 5;
        mc.fontRenderer.drawStringWithShadow(shardText, textX, textY, 0xE8D7FF);

        // SNAP duration bar (under icon)
        int barW = 34;
        int barH = 4;
        int barX = baseX - 9;
        int barY = iconY + 18;

        // Background
        drawRect(barX, barY, barX + barW, barY + barH, 0x90000000);

        if (snapMax > 0 && snapTicks > 0) {
            float pct = Math.min(1.0f, Math.max(0.0f, (float) snapTicks / (float) snapMax));
            int fill = (int) (barW * pct);
            drawRect(barX, barY, barX + fill, barY + barH, 0xB0D96CFF);
        }
    }
}
