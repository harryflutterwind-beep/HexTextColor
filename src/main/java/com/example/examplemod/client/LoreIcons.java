package com.example.examplemod.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

public final class LoreIcons {

    private LoreIcons() {}

    // CHANGE if your modid differs
    public static final String MODID = "hexcolorcodes";

    // Path:
    // src/main/resources/assets/hexcolorcodes/textures/gems/pill_dark_fire_face_64.png
    public static final ResourceLocation TEX =
            new ResourceLocation(MODID, "textures/gems/pill_dark_fire_face_64.png");

    // atlas + cell sizing
    public static final int ATLAS_W = 128;
    public static final int ATLAS_H = 128;
    public static final int CELL_W  = 128;
    public static final int CELL_H  = 128;

    // id -> cell coords
    private static final Map<String, int[]> ICONS = new HashMap<String, int[]>();

    /** Register an icon at (col,row) in the atlas grid. */
    public static void regCell(String id, int col, int row) {
        if (id == null) return;
        ICONS.put(id.toLowerCase(), new int[]{col, row});
    }

    /** Draw icon by id at screen position. */
    public static boolean draw(String id, int x, int y, int w, int h) {
        if (id == null) return false;
        int[] cell = ICONS.get(id.toLowerCase());
        if (cell == null) return false;

        return drawCell(cell[0], cell[1], x, y, w, h);
    }

    /** Draw icon at atlas cell (col,row). */
    public static boolean drawCell(int col, int row, int x, int y, int w, int h) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.getTextureManager() == null) return false;

        mc.getTextureManager().bindTexture(TEX);

        float u0 = (col * CELL_W) / (float) ATLAS_W;
        float v0 = (row * CELL_H) / (float) ATLAS_H;
        float u1 = ((col * CELL_W) + CELL_W) / (float) ATLAS_W;
        float v1 = ((row * CELL_H) + CELL_H) / (float) ATLAS_H;

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glColor4f(1f, 1f, 1f, 1f);

        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertexWithUV(x,     y + h, 0, u0, v1);
        t.addVertexWithUV(x + w, y + h, 0, u1, v1);
        t.addVertexWithUV(x + w, y,     0, u1, v0);
        t.addVertexWithUV(x,     y,     0, u0, v0);
        t.draw();

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        return true;
    }

    // Register a test icon in the top-left cell
    static {
        regCell("test", 0, 0);
    }
}
