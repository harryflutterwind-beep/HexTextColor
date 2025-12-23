// src/main/java/com/example/examplemod/client/HexSlotOverlay.java
package com.example.examplemod.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.util.regex.Pattern;

import com.example.examplemod.ModConfig;

public class HexSlotOverlay {

    public static void register() { MinecraftForge.EVENT_BUS.register(new HexSlotOverlay()); }

    // reflect guiLeft/guiTop
    private static Field F_GUI_LEFT, F_GUI_TOP;

    // strict rarity detection — only draw when these tokens appear in LORE
    private enum RId { NONE, COMMON, UNCOMMON, RARE, EPIC, LEGEND, PEARL, SERAPH, ETECH, GLITCH, BLACK, EFF, EFF_PLUS }

    // word-boundary patterns (case-insensitive after we lowercase)
    private static final Pattern
            P_COMMON   = Pattern.compile("\\bcommon\\b"),
            P_UNCOMMON = Pattern.compile("\\buncommon\\b"),
            P_RARE     = Pattern.compile("\\brare\\b"),
            P_EPIC     = Pattern.compile("\\bepic\\b"),
            P_LEGEND   = Pattern.compile("\\blegend(ary)?\\b"),
            P_PEARL    = Pattern.compile("\\bpearlescent\\b"),
            P_SERAPH   = Pattern.compile("\\bseraph\\b"),
            P_ETECH    = Pattern.compile("\\b(e[- ]?tech|etech)\\b"),
            P_GLITCH   = Pattern.compile("\\bglitch\\b"),
            P_BLACK    = Pattern.compile("\\bblack\\b"),
    // effervescent (animated)
    P_EFF_PLUS = Pattern.compile("\\beffervescent_\\b"),
            P_EFF      = Pattern.compile("\\beffervescent\\b");

    // ── INVENTORY ──────────────────────────────────────────────────────────────
    @SubscribeEvent
    public void drawContainer(GuiScreenEvent.DrawScreenEvent.Post e) {
        if (!(e.gui instanceof GuiContainer)) return;
        if (!ModConfig.overlayEnableInventory) return;

        GuiContainer gui = (GuiContainer) e.gui;
        int guiLeft = getGuiLeft(gui);
        int guiTop  = getGuiTop(gui);

        Container c = gui.inventorySlots;
        if (c == null || c.inventorySlots == null) return;

        final long ms = Minecraft.getSystemTime() & 0xFFFFFFFFL;
        final float t = (ms % 4000L) / 4000f;

        for (Object o : c.inventorySlots) {
            if (!(o instanceof Slot)) continue;
            Slot s = (Slot) o;
            if (!s.getHasStack()) continue;

            ItemStack stack = s.getStack();
            int color = colorIfHasRarityLore(stack, t);  // STRICT lore-only
            if (color == 0) continue;

            int x = guiLeft + s.xDisplayPosition;
            int y = guiTop  + s.yDisplayPosition;

            int bA = ModConfig.overlayBorderAlpha & 0xFF;
            int fA = ModConfig.overlayFillAlpha   & 0xFF;

            boolean isBlack = (color == 0x000000);
            if (isBlack) { bA = 0xE0; fA = Math.max(fA, 0xA0); }

            drawBorder(x - 1, y - 1, 18, 18, color, bA);
            drawRect(x, y + 16 - ModConfig.overlayStripHeight, 16, ModConfig.overlayStripHeight, color, fA);
            if (isBlack) drawBorder(x, y, 16, 16, 0x666666, 0x80);
        }
    }

    // ── HOTBAR ────────────────────────────────────────────────────────────────
    @SubscribeEvent
    public void drawHotbar(RenderGameOverlayEvent.Post e) {
        if (e.type != RenderGameOverlayEvent.ElementType.HOTBAR) return;
        if (!ModConfig.overlayEnableHotbar) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null || mc.thePlayer == null) return;

        final long ms = Minecraft.getSystemTime() & 0xFFFFFFFFL;
        final float t  = (ms % 4000L) / 4000f;
        final float pulse = 0.5f + 0.5f * (float)Math.sin((ms % 1000L) / 1000f * 2f * (float)Math.PI);

        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int sw = sr.getScaledWidth();
        int sh = sr.getScaledHeight();

        final int left = sw / 2 - 90;
        final int top  = sh - 22;
        final int selected = mc.thePlayer.inventory.currentItem;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.mainInventory[i];
            if (stack == null) continue;

            int color = colorIfHasRarityLore(stack, t);  // STRICT lore-only
            if (color == 0) continue;

            boolean isSelected = (i == selected);
            boolean isBlack = (color == 0x000000);

            int x = left + i * 20 + 1; // slightly bigger frame than vanilla
            int y = top  + 1;

            int bA = Math.max(0, (ModConfig.overlayBorderAlpha - 0x10)) & 0xFF;
            int fA = ModConfig.overlayFillAlpha & 0xFF;
            if (isBlack) { bA = 0xE0; fA = Math.max(fA, 0xA0); }

            if (!isSelected) {
                drawBorder(x, y, 18, 18, color, bA);
                if (isBlack) drawBorder(x + 1, y + 1, 16, 16, 0x666666, 0x80);
                drawRect(x + 1, y + 17 - ModConfig.overlayStripHeight, 16, ModConfig.overlayStripHeight, color, fA);
            } else {
                int a = (int)(0x90 + 0x40 * pulse);
                drawRect(x + 1, y + 17, 16, 2, color, a);
                drawRect(x + 1, y + 17 - ModConfig.overlayStripHeight, 16, ModConfig.overlayStripHeight, color, fA);
            }
        }
    }

    // ── Lore-only color resolve ───────────────────────────────────────────────
    private int colorIfHasRarityLore(ItemStack stack, float t){
        if (stack == null || !stack.hasTagCompound()) return 0;
        NBTTagCompound tag = stack.getTagCompound();
        NBTTagCompound display = tag.getCompoundTag("display");
        if (display == null) return 0;
        NBTTagList lore = display.getTagList("Lore", 8); // string
        if (lore == null || lore.tagCount() == 0) return 0;

        RId rid = RId.NONE;

        for (int i = 0; i < lore.tagCount(); i++) {
            String line = stripTags(lore.getStringTagAt(i)).toLowerCase();

            if (P_EFF_PLUS.matcher(line).find()) { rid = RId.EFF_PLUS; break; }
            if (P_EFF.matcher(line).find())      { rid = RId.EFF;      break; }

            if (P_BLACK.matcher(line).find())    { rid = RId.BLACK;    break; }
            if (P_GLITCH.matcher(line).find())   { rid = RId.GLITCH;   break; }
            if (P_ETECH.matcher(line).find())    { rid = RId.ETECH;    break; }
            if (P_LEGEND.matcher(line).find())   { rid = RId.LEGEND;   break; }
            if (P_PEARL.matcher(line).find())    { rid = RId.PEARL;    break; }
            if (P_SERAPH.matcher(line).find())   { rid = RId.SERAPH;   break; }
            if (P_EPIC.matcher(line).find())     { rid = RId.EPIC;     break; }
            if (P_RARE.matcher(line).find())     { rid = RId.RARE;     break; }
            if (P_UNCOMMON.matcher(line).find()) { rid = RId.UNCOMMON; break; }
            if (P_COMMON.matcher(line).find())   { rid = RId.COMMON;   break; }
        }

        if (rid == RId.NONE) return 0; // nothing found → no overlay

        // animated ones
        if (rid == RId.EFF_PLUS) return effRainbow(t, 3.0f);
        if (rid == RId.EFF)      return effRainbow(t, 1.5f);

        // static: pull from config
        switch (rid){
            case BLACK:    return ModConfig.col24(ModConfig.overlay_black);
            case GLITCH:   return ModConfig.col24(ModConfig.overlay_glitch);
            case ETECH:    return ModConfig.col24(ModConfig.overlay_etech);
            case LEGEND:   return ModConfig.col24(ModConfig.overlay_legend);
            case PEARL:    return ModConfig.col24(ModConfig.overlay_pearl);
            case SERAPH:   return ModConfig.col24(ModConfig.overlay_seraph);
            case EPIC:     return ModConfig.col24(ModConfig.overlay_epic);
            case RARE:     return ModConfig.col24(ModConfig.overlay_rare);
            case UNCOMMON: return ModConfig.col24(ModConfig.overlay_uncommon);
            case COMMON:   return ModConfig.col24(ModConfig.overlay_common);
            default:       return 0;
        }
    }

    // ── utils ─────────────────────────────────────────────────────────────────
    private int effRainbow(float t, float speed){
        float hue = (t * speed) % 1.0f;
        float sat = 0.95f, val = 1.00f;
        return hsvToRgb(hue, sat, val);
    }
    private int hsvToRgb(float h, float s, float v){
        float r=0, g=0, b=0;
        int i = (int)Math.floor(h * 6.0f);
        float f = h * 6.0f - i;
        float p = v * (1.0f - s);
        float q = v * (1.0f - f * s);
        float t = v * (1.0f - (1.0f - f) * s);
        switch (i % 6){
            case 0: r=v; g=t; b=p; break;
            case 1: r=q; g=v; b=p; break;
            case 2: r=p; g=v; b=t; break;
            case 3: r=p; g=q; b=v; break;
            case 4: r=t; g=p; b=v; break;
            case 5: r=v; g=p; b=q; break;
        }
        int R = (int)(r * 255.0f) & 0xFF;
        int G = (int)(g * 255.0f) & 0xFF;
        int B = (int)(b * 255.0f) & 0xFF;
        return (R << 16) | (G << 8) | B;
    }

    private String stripTags(String s){
        if (s == null) return "";
        s = s.replaceAll("§[0-9A-FK-ORa-fk-or]", "");
        s = s.replaceAll("<[^>]+>", "");
        return s;
    }

    private void drawBorder(int x, int y, int w, int h, int rgb, int alpha){
        drawRect(x,     y,     w, 1, rgb, alpha);
        drawRect(x,     y+h-1, w, 1, rgb, alpha);
        drawRect(x,     y,     1, h, rgb, alpha);
        drawRect(x+w-1, y,     1, h, rgb, alpha);
    }
    private void drawRect(int x, int y, int w, int h, int rgb, int alpha){
        float af = (alpha & 0xFF) / 255f;
        float r  = ((rgb >> 16) & 0xFF) / 255f;
        float g  = ((rgb >> 8 ) & 0xFF) / 255f;
        float b  = ((rgb      ) & 0xFF) / 255f;

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);

        GL11.glColor4f(r,g,b,af);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3f(x,     y+h, 0);
        GL11.glVertex3f(x+w,   y+h, 0);
        GL11.glVertex3f(x+w,   y,   0);
        GL11.glVertex3f(x,     y,   0);
        GL11.glEnd();

        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1,1,1,1);
    }

    private static int getGuiLeft(GuiContainer g){
        try{
            if (F_GUI_LEFT == null){ F_GUI_LEFT = GuiContainer.class.getDeclaredField("guiLeft"); F_GUI_LEFT.setAccessible(true); }
            return F_GUI_LEFT.getInt(g);
        }catch(Throwable t){
            try{
                if (F_GUI_LEFT == null){ F_GUI_LEFT = GuiContainer.class.getDeclaredField("field_147003_i"); F_GUI_LEFT.setAccessible(true); }
                return F_GUI_LEFT.getInt(g);
            }catch(Throwable ignored){ return 0; }
        }
    }
    private static int getGuiTop(GuiContainer g){
        try{
            if (F_GUI_TOP == null){ F_GUI_TOP = GuiContainer.class.getDeclaredField("guiTop"); F_GUI_TOP.setAccessible(true); }
            return F_GUI_TOP.getInt(g);
        }catch(Throwable t){
            try{
                if (F_GUI_TOP == null){ F_GUI_TOP = GuiContainer.class.getDeclaredField("field_147009_r"); F_GUI_TOP.setAccessible(true); }
                return F_GUI_TOP.getInt(g);
            }catch(Throwable ignored){ return 0; }
        }
    }
}
