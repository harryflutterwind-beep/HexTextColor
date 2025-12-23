// src/main/java/com/example/examplemod/client/ModListSparkles.java
package com.example.examplemod.client;

import cpw.mods.fml.common.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.util.Random;

public class ModListSparkles {

    // Your modid (from the screenshot)
    private static final String OUR_MODID = "hexcolorcodes";

    private static Field F_SELECTED_MOD;
    private static Field F_SCROLL_LIST; // GuiScrollingList field on GuiModList

    // Spark pool
    private static final int MAX = 72;
    private static final Spark[] P = new Spark[MAX];
    private static boolean init;
    private static final Random R = new Random();

    private static long lastMs;
    private static int spawnAccumMs;

    private static class Spark {
        float x, y, vx, vy;
        int lifeMs, ageMs;
        float size;
        int alphaMax;

        void reset(float sx, float sy) {
            x = sx;
            y = sy;

            // Gentle float + drift
            vx = (R.nextFloat() - 0.5f) * 0.20f;
            vy = -0.05f - R.nextFloat() * 0.20f;

            lifeMs = 520 + R.nextInt(700);
            ageMs = 0;

            size = 1.6f + R.nextFloat() * 2.8f;
            alphaMax = 150 + R.nextInt(105);
        }
    }

    private static void ensureInit() {
        if (init) return;
        for (int i = 0; i < MAX; i++) P[i] = new Spark();
        init = true;
    }

    public static void renderIfSelected(Object guiModList, int mouseX, int mouseY, float partialTicks) {
        if (guiModList == null) return;

        ModContainer sel = getSelectedMod(guiModList);
        if (sel == null) return;

        String id;
        try { id = sel.getModId(); }
        catch (Throwable t) { return; }

        if (id == null || !OUR_MODID.equalsIgnoreCase(id)) return;

        ensureInit();

        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int w = sr.getScaledWidth();
        int h = sr.getScaledHeight();

        // Determine where the right-side info panel begins using the scrolling list's right bound.
        int panelLeft = getInfoPanelLeft(guiModList, w);
        int panelRight = w - 12;

        if (panelRight - panelLeft < 60) return;

        // Two zones:
        // 1) Icon/logo zone (top area)
        int iconL = panelLeft + 6;
        int iconR = Math.min(panelLeft + 130, panelRight);
        int iconT = 22;
        int iconB = 92;

        // 2) Text/info zone (below icon, large area)
        int textL = panelLeft + 6;
        int textR = panelRight;
        int textT = 80;
        int textB = h - 44;

        // Spawn + update:
        // Slightly more spawn when selected so it feels alive
        updatePool(iconL, iconT, iconR, iconB, textL, textT, textR, textB);

        // Draw overlay sparkles
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        drawSparkles();

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glPopMatrix();
    }

    /** Right panel begins right after the scrolling list. */
    private static int getInfoPanelLeft(Object guiModList, int w) {
        // Fallback if reflection fails
        int fallback = (int)(w * 0.34f) + 8;

        Object list = getScrollingList(guiModList);
        if (list == null) return fallback;

        Integer right = readIntField(list, "right", "field_148_???", "listRight");
        if (right == null) {
            // Some GuiScrollingList variants store left+width; try those
            Integer left = readIntField(list, "left", "listLeft");
            Integer width = readIntField(list, "listWidth", "width");
            if (left != null && width != null && width > 0) right = left + width;
        }

        if (right == null) return fallback;

        int panelLeft = right + 10;
        if (panelLeft < 0 || panelLeft > w - 40) return fallback;
        return panelLeft;
    }

    private static Object getScrollingList(Object guiModList) {
        try {
            if (F_SCROLL_LIST == null) {
                // find first field assignable to cpw.mods.fml.client.GuiScrollingList
                Class<?> cls = guiModList.getClass();
                Field found = null;
                for (Field f : cls.getDeclaredFields()) {
                    Class<?> t = f.getType();
                    if (t != null && "cpw.mods.fml.client.GuiScrollingList".equals(t.getName())) {
                        found = f;
                        break;
                    }
                }
                if (found != null) {
                    found.setAccessible(true);
                    F_SCROLL_LIST = found;
                }
            }
            if (F_SCROLL_LIST == null) return null;
            return F_SCROLL_LIST.get(guiModList);
        } catch (Throwable ignored) {}
        return null;
    }

    private static Integer readIntField(Object obj, String... names) {
        if (obj == null) return null;
        Class<?> c = obj.getClass();
        for (String n : names) {
            try {
                Field f = c.getDeclaredField(n);
                f.setAccessible(true);
                Object v = f.get(obj);
                if (v instanceof Integer) return (Integer) v;
            } catch (Throwable ignored) {}
        }
        // If named lookup fails, scan for a plausible "right" int by heuristic is risky—skip.
        return null;
    }

    private static ModContainer getSelectedMod(Object guiModList) {
        try {
            if (F_SELECTED_MOD == null) {
                // Common name in FML 1.7.10
                F_SELECTED_MOD = guiModList.getClass().getDeclaredField("selectedMod");
                F_SELECTED_MOD.setAccessible(true);
            }
            Object o = F_SELECTED_MOD.get(guiModList);
            if (o instanceof ModContainer) return (ModContainer) o;
        } catch (Throwable ignored) {}
        return null;
    }

    private static void updatePool(
            int iconL, int iconT, int iconR, int iconB,
            int textL, int textT, int textR, int textB
    ) {
        long now = System.currentTimeMillis();
        if (lastMs == 0) lastMs = now;

        int dt = (int)Math.min(50L, Math.max(0L, now - lastMs));
        lastMs = now;

        // ~16-20 spawns/sec while selected (half in icon area, half in text area)
        spawnAccumMs += dt;
        while (spawnAccumMs >= 55) {
            spawnAccumMs -= 55;

            // Alternate zones (random)
            if (R.nextBoolean()) spawnOne(iconL, iconT, iconR, iconB);
            else spawnOne(textL, textT, textR, textB);
        }

        for (int i = 0; i < MAX; i++) {
            Spark s = P[i];
            if (s.lifeMs <= 0) continue;

            s.ageMs += dt;
            if (s.ageMs >= s.lifeMs) {
                s.lifeMs = 0;
                continue;
            }

            s.x += s.vx * dt;
            s.y += s.vy * dt;

            // gentle damping
            s.vx *= 0.995f;
            s.vy *= 0.998f;
        }
    }

    private static void spawnOne(int left, int top, int right, int bottom) {
        if (right - left < 10 || bottom - top < 10) return;

        for (int i = 0; i < MAX; i++) {
            Spark s = P[i];
            if (s.lifeMs > 0) continue;

            float sx = left + 6 + R.nextFloat() * (right - left - 12);
            float sy = top + 6 + R.nextFloat() * (bottom - top - 12);
            s.reset(sx, sy);
            return;
        }
    }

    private static void drawSparkles() {
        Tessellator t = Tessellator.instance;

        for (int i = 0; i < MAX; i++) {
            Spark s = P[i];
            if (s.lifeMs <= 0) continue;

            float a = 1.0f - (s.ageMs / (float)s.lifeMs);
            // ease (stronger at start)
            a = a * a;

            int alpha = (int)(s.alphaMax * a);
            if (alpha <= 2) continue;

            float sz = s.size * (0.70f + 0.70f * (1.0f - a)); // tiny pulse

            int col = (alpha << 24) | 0xFFFFFF;

            GL11.glColor4ub((byte)255, (byte)255, (byte)255, (byte)alpha);

            float x1 = s.x - sz, y1 = s.y - sz;
            float x2 = s.x + sz, y2 = s.y + sz;

            // Soft quad
            t.startDrawingQuads();
            t.addVertex(x1, y2, 0);
            t.addVertex(x2, y2, 0);
            t.addVertex(x2, y1, 0);
            t.addVertex(x1, y1, 0);
            t.draw();

            // Cross sparkle
            Gui.drawRect((int)(s.x - 1), (int)(s.y - (sz + 2)), (int)(s.x + 1), (int)(s.y + (sz + 2)), col);
            Gui.drawRect((int)(s.x - (sz + 2)), (int)(s.y - 1), (int)(s.x + (sz + 2)), (int)(s.y + 1), col);
        }

        GL11.glColor4f(1, 1, 1, 1);
    }
}
