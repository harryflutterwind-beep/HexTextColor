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

    // IMPORTANT:
    //  - We must NOT use 0 as "no overlay", because BLACK rarity is 0x000000 (== 0).
    //  - Use -1 sentinel for "no overlay".
    private static final int NO_OVERLAY = -1;

    // GLITCH gradient (pastel + lighter)
    private static final int GLITCH_TOP_RGB = 0xFF5FB8; // pastel neon magenta
    private static final int GLITCH_BOT_RGB = 0xFFD1E6; // soft pastel pink

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
            int color = colorIfHasRarityLore(stack, t); // STRICT lore-only
            if (color == NO_OVERLAY) continue;

            int x = guiLeft + s.xDisplayPosition;
            int y = guiTop  + s.yDisplayPosition;

            int bA = ModConfig.overlayBorderAlpha & 0xFF;
            int fA = ModConfig.overlayFillAlpha   & 0xFF;

            boolean isBlack  = (color == 0x000000);
            boolean isGlitch = hasTokenInLore(stack, P_GLITCH);

            if (isBlack) { bA = 0xE0; fA = Math.max(fA, 0xA0); }

            // ── ITEM GLOSS (on the icon itself) ──
            // Gloss is only for higher tiers (you can widen this list later)
            boolean isEtech  = hasTokenInLore(stack, P_ETECH);
            boolean isPearl  = hasTokenInLore(stack, P_PEARL);
            boolean isLegend = hasTokenInLore(stack, P_LEGEND);
            boolean isEff    = hasTokenInLore(stack, P_EFF) || hasTokenInLore(stack, P_EFF_PLUS);

            if (isGlitch || isEtech || isPearl || isLegend || isEff) {
                float period = isEff ? 1200f : (isPearl ? 1600f : (isGlitch ? 2000f : 2400f));
                float phase  = glossPhaseFor(stack, ms, period);
                int glossA   = isEff ? 110 : (isGlitch ? 90 : 75);

                // Neutral white gloss reads like "shine" best
                drawItemGloss(x, y, 16, 16, phase, glossA, 0xFFFFFF);
            }

            // ── SLOT BORDER/STRIP ──
            if (isGlitch) {
                drawBorderVGradient(x - 1, y - 1, 18, 18, GLITCH_TOP_RGB, GLITCH_BOT_RGB, bA);
                drawRectVGradient(x, y + 16 - ModConfig.overlayStripHeight, 16, ModConfig.overlayStripHeight,
                        GLITCH_TOP_RGB, GLITCH_BOT_RGB, fA);
            } else {
                drawBorder(x - 1, y - 1, 18, 18, color, bA);
                drawRect(x, y + 16 - ModConfig.overlayStripHeight, 16, ModConfig.overlayStripHeight, color, fA);
            }

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

            int color = colorIfHasRarityLore(stack, t); // STRICT lore-only
            if (color == NO_OVERLAY) continue;

            boolean isSelected = (i == selected);
            boolean isBlack    = (color == 0x000000);
            boolean isGlitch   = hasTokenInLore(stack, P_GLITCH);

            boolean isEtech  = hasTokenInLore(stack, P_ETECH);
            boolean isPearl  = hasTokenInLore(stack, P_PEARL);
            boolean isLegend = hasTokenInLore(stack, P_LEGEND);
            boolean isEff    = hasTokenInLore(stack, P_EFF) || hasTokenInLore(stack, P_EFF_PLUS);

            int x = left + i * 20 + 1; // frame
            int y = top  + 1;

            int bA = Math.max(0, (ModConfig.overlayBorderAlpha - 0x10)) & 0xFF;
            int fA = ModConfig.overlayFillAlpha & 0xFF;
            if (isBlack) { bA = 0xE0; fA = Math.max(fA, 0xA0); }

            // ── ITEM GLOSS (icon area inside the 18x18 frame) ──
            if (isGlitch || isEtech || isPearl || isLegend || isEff) {
                float period = isEff ? 1200f : (isPearl ? 1600f : (isGlitch ? 2000f : 2400f));
                float phase  = glossPhaseFor(stack, ms, period);
                int glossA   = isEff ? 110 : (isGlitch ? 90 : 75);

                drawItemGloss(x + 1, y + 1, 16, 16, phase, glossA, 0xFFFFFF);
            }

            if (!isSelected) {
                if (isGlitch) {
                    drawBorderVGradient(x, y, 18, 18, GLITCH_TOP_RGB, GLITCH_BOT_RGB, bA);
                    drawRectVGradient(x + 1, y + 17 - ModConfig.overlayStripHeight, 16, ModConfig.overlayStripHeight,
                            GLITCH_TOP_RGB, GLITCH_BOT_RGB, fA);
                } else {
                    drawBorder(x, y, 18, 18, color, bA);
                    drawRect(x + 1, y + 17 - ModConfig.overlayStripHeight, 16, ModConfig.overlayStripHeight, color, fA);
                }

                if (isBlack) drawBorder(x + 1, y + 1, 16, 16, 0x666666, 0x80);

            } else {
                int a = (int)(0x90 + 0x40 * pulse);

                if (isGlitch) {
                    drawRectVGradient(x + 1, y + 17, 16, 2, GLITCH_TOP_RGB, GLITCH_BOT_RGB, a);
                    drawRectVGradient(x + 1, y + 17 - ModConfig.overlayStripHeight, 16, ModConfig.overlayStripHeight,
                            GLITCH_TOP_RGB, GLITCH_BOT_RGB, fA);
                } else {
                    drawRect(x + 1, y + 17, 16, 2, color, a);
                    drawRect(x + 1, y + 17 - ModConfig.overlayStripHeight, 16, ModConfig.overlayStripHeight, color, fA);
                }
            }
        }
    }

    // ── Lore-only color resolve ───────────────────────────────────────────────
    private int colorIfHasRarityLore(ItemStack stack, float t){
        if (stack == null || !stack.hasTagCompound()) return NO_OVERLAY;
        NBTTagCompound tag = stack.getTagCompound();

        // getCompoundTag never returns null in 1.7.10; it returns empty compound if missing.
        NBTTagCompound display = tag.getCompoundTag("display");
        if (display == null) return NO_OVERLAY;

        NBTTagList lore = display.getTagList("Lore", 8); // string
        if (lore == null || lore.tagCount() == 0) return NO_OVERLAY;

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

        if (rid == RId.NONE) return NO_OVERLAY; // nothing found → no overlay

        // animated ones
        if (rid == RId.EFF_PLUS) return effRainbow(t, 3.0f);
        if (rid == RId.EFF)      return effRainbow(t, 1.5f);

        // static: pull from config
        switch (rid){
            case BLACK:    return ModConfig.col24(ModConfig.overlay_black); // can be 0x000000 safely now
            case GLITCH:   return ModConfig.col24(ModConfig.overlay_glitch); // actual drawing gradient is handled in render loops
            case ETECH:    return ModConfig.col24(ModConfig.overlay_etech);
            case LEGEND:   return ModConfig.col24(ModConfig.overlay_legend);
            case PEARL:    return ModConfig.col24(ModConfig.overlay_pearl);
            case SERAPH:   return ModConfig.col24(ModConfig.overlay_seraph);
            case EPIC:     return ModConfig.col24(ModConfig.overlay_epic);
            case RARE:     return ModConfig.col24(ModConfig.overlay_rare);
            case UNCOMMON: return ModConfig.col24(ModConfig.overlay_uncommon);
            case COMMON:   return ModConfig.col24(ModConfig.overlay_common);
            default:       return NO_OVERLAY;
        }
    }

    // ── token check (reuse your lore + stripTags rules) ───────────────────────
    private boolean hasTokenInLore(ItemStack stack, Pattern p){
        if (stack == null || !stack.hasTagCompound()) return false;
        NBTTagCompound tag = stack.getTagCompound();
        NBTTagCompound display = tag.getCompoundTag("display");
        if (display == null) return false;
        NBTTagList lore = display.getTagList("Lore", 8);
        if (lore == null || lore.tagCount() == 0) return false;

        for (int i = 0; i < lore.tagCount(); i++){
            String line = stripTags(lore.getStringTagAt(i)).toLowerCase();
            if (p.matcher(line).find()) return true;
        }
        return false;
    }

    // ── ITEM GLOSS (icon overlay pass) ────────────────────────────────────────
    private float glossPhaseFor(ItemStack stack, long ms, float basePeriodMs) {
        int seed = 0;
        try {
            seed = stack.getItem().hashCode() * 31 + stack.getItemDamage();
            if (stack.hasDisplayName()) seed = seed * 31 + stack.getDisplayName().hashCode();
            if (stack.hasTagCompound()) seed = seed * 31 + stack.getTagCompound().toString().hashCode();
        } catch (Throwable ignored) {}

        float off = ((seed & 0x7FFFFFFF) % 10000) / 10000f; // 0..1
        float phase = (ms / basePeriodMs + off) % 1.0f;
        if (phase < 0) phase += 1.0f;
        return phase;
    }

    // Draw a moving glossy band over the item icon area (x,y,w,h).
    // phase: 0..1 moves left->right
    // tintRgb: usually 0xFFFFFF for neutral gloss
    private void drawItemGloss(int x, int y, int w, int h, float phase, int alpha, int tintRgb) {
        float cx = phase * (w + 20f) - 10f; // enters/exits smoothly
        float bandW = 6.5f;                 // half-width of the glossy band
        float slope = 0.55f;                // diagonal tilt

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        // additive shine looks like "gloss"
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);

        int slices = 6; // cheap gradient
        for (int i = 0; i < slices; i++) {
            float fx0 = (i / (float)slices);
            float fx1 = ((i + 1) / (float)slices);

            float local0 = -1f + 2f * fx0;
            float local1 = -1f + 2f * fx1;

            float px0 = (x + cx + local0 * bandW);
            float px1 = (x + cx + local1 * bandW);

            int ix0 = (int)Math.floor(Math.max(px0, x));
            int ix1 = (int)Math.ceil (Math.min(px1, x + w));
            if (ix1 <= ix0) continue;

            float mid = (local0 + local1) * 0.5f;
            float t = 1.0f - Math.min(1.0f, Math.abs(mid)); // 0..1
            int a = (int)(alpha * (t * t));
            if (a <= 0) continue;

            int dy0 = (int)((ix0 - x - w * 0.5f) * slope);
            int dy1 = (int)((ix1 - x - w * 0.5f) * slope);

            int y0 = y + Math.min(dy0, dy1);
            int y1 = y + h + Math.max(dy0, dy1);

            if (y0 < y) y0 = y;
            if (y1 > y + h) y1 = y + h;

            drawRect(ix0, y0, ix1 - ix0, y1 - y0, tintRgb, a);
        }

        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1,1,1,1);
    }

    // ── existing utils ─────────────────────────────────────────────────────────
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

    // Vertical gradient border (top color -> bottom color)
    private void drawBorderVGradient(int x, int y, int w, int h, int topRgb, int botRgb, int alpha){
        drawRect(x, y, w, 1, topRgb, alpha);
        drawRect(x, y + h - 1, w, 1, botRgb, alpha);
        drawRectVGradient(x,       y, 1, h, topRgb, botRgb, alpha);
        drawRectVGradient(x + w-1, y, 1, h, topRgb, botRgb, alpha);
    }

    private void drawRectVGradient(int x, int y, int w, int h, int topRgb, int botRgb, int alpha){
        float af = (alpha & 0xFF) / 255f;

        float tr = ((topRgb >> 16) & 0xFF) / 255f;
        float tg = ((topRgb >> 8 ) & 0xFF) / 255f;
        float tb = ((topRgb      ) & 0xFF) / 255f;

        float br = ((botRgb >> 16) & 0xFF) / 255f;
        float bg = ((botRgb >> 8 ) & 0xFF) / 255f;
        float bb = ((botRgb      ) & 0xFF) / 255f;

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);

        GL11.glBegin(GL11.GL_QUADS);

        // bottom
        GL11.glColor4f(br, bg, bb, af);
        GL11.glVertex3f(x,     y+h, 0);
        GL11.glVertex3f(x+w,   y+h, 0);

        // top
        GL11.glColor4f(tr, tg, tb, af);
        GL11.glVertex3f(x+w,   y,   0);
        GL11.glVertex3f(x,     y,   0);

        GL11.glEnd();

        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1,1,1,1);
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
            if (F_GUI_LEFT == null){
                F_GUI_LEFT = GuiContainer.class.getDeclaredField("guiLeft");
                F_GUI_LEFT.setAccessible(true);
            }
            return F_GUI_LEFT.getInt(g);
        }catch(Throwable t){
            try{
                if (F_GUI_LEFT == null){
                    F_GUI_LEFT = GuiContainer.class.getDeclaredField("field_147003_i");
                    F_GUI_LEFT.setAccessible(true);
                }
                return F_GUI_LEFT.getInt(g);
            }catch(Throwable ignored){ return 0; }
        }
    }

    private static int getGuiTop(GuiContainer g){
        try{
            if (F_GUI_TOP == null){
                F_GUI_TOP = GuiContainer.class.getDeclaredField("guiTop");
                F_GUI_TOP.setAccessible(true);
            }
            return F_GUI_TOP.getInt(g);
        }catch(Throwable t){
            try{
                if (F_GUI_TOP == null){
                    F_GUI_TOP = GuiContainer.class.getDeclaredField("field_147009_r");
                    F_GUI_TOP.setAccessible(true);
                }
                return F_GUI_TOP.getInt(g);
            }catch(Throwable ignored){ return 0; }
        }
    }
}
