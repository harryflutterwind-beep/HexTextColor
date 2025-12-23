/*  ItemBeamRenderer — Chaos FX pass 2
 *  - Softer global alphas
 *  - CHAOTIC: base glow + sparks
 *  - VOLATILE: noisy flicker + micro-sparks
 *  - PRIMORDIAL: orbiting rings w/ moving dots + inner spiral
 *  - ASCENDED: crown halo + double spiral + soft vertical pulses
 */
package com.example.examplemod.client;

import com.example.examplemod.ModConfig;
import com.example.examplemod.beams.RarityDetect;
import com.example.examplemod.beams.RarityTags;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.*;
import java.util.regex.Pattern;

@SideOnly(Side.CLIENT)
public final class ItemBeamRenderer {

    private static final ResourceLocation BEAM_TEX =
            new ResourceLocation("textures/entity/beacon_beam.png");

    // --- knobs you can tweak quickly ---
    private static final float ALPHA_DEFAULT   = 0.72f;
    private static final float ALPHA_LEGENDARY = 0.85f;
    private static final float ALPHA_BLACK     = 0.80f;
    private static final float ALPHA_ETECH     = 0.78f;
    private static final float ALPHA_GLITCH    = 0.80f;

    // Defaults (config overrides these live)
    private static final int   MAX_DRAW_DEFAULT    = 96;
    private static final int   RADIUS_SCAN_DEFAULT = 72;

    // Chaos intensities
    private static final float CHAOTIC_GLOW_SIZE   = 0.45f;
    private static final float CHAOTIC_GLOW_ALPHA  = 0.42f;
    private static final int   CHAOTIC_SPARKS      = 10;

    private static final float VOLATILE_ALPHA_JIT  = 0.22f;
    private static final float VOLATILE_RAD_JIT    = 0.07f;
    private static final int   VOLATILE_SPARKS     = 6;

    private static final int   PRIM_RING_COUNT     = 3;
    private static final int   PRIM_DOTS_PER_RING  = 18;

    private static final int   ASC_HALO_SIDES      = 36;
    private static final float ASC_HALO_ALPHA      = 0.85f;
    // Effervescent rainbow speeds (higher = faster)
    private static final float EFF_SPEED      = 0.030f; // was 0.010f
    private static final float EFF_SPEED_FAST = 0.050f; // was 0.020f (effervescent_)
    // --- base ring sizing/lift ---
    private static final double BASE_RING_LIFT          = 0.07; // y offset above ground haze
    private static final double PRIM_BASE_RING_SCALE    = 1.56; // was ~1.08
    private static final double ASC_BASE_RING_OUTER     = 1.52; // was ~1.15
    private static final double ASC_BASE_RING_INNER     = 1.26; // was ~0.98
    private static final float  BASE_RING_ALPHA_STRONG  = 0.82f;
    private static final float  BASE_RING_ALPHA_SOFT    = 0.70f;
    // --- base aura (near-ground) ---
    private static final double AURA_LIFT            = 0.02;  // sits just above ground
    private static final double AURA_RADIUS_SCALE    = 1.15;  // haze/pillars width vs shaft
    private static final double AURA_HEIGHT          = 0.90;  // pillar/arc height (~1 block)
    private static final int    AURA_PILLAR_COUNT    = 6;     // upright fog sheets
    private static final float  AURA_HAZE_ALPHA      = 0.45f; // soft disc
    private static final float  AURA_PILLAR_ALPHA    = 0.42f; // fog sheets
    private static final int    AURA_ARC_COUNT       = 7;     // mini lightning bolts
    private static final float  AURA_ARC_ALPHA       = 0.85f; // bright but small
// inside ItemBeamRenderer (class level)

    // MC color/style codes like §a, §l, etc.
    private static final java.util.regex.Pattern MC_CODES =
            java.util.regex.Pattern.compile("§[0-9A-FK-ORa-fk-or]");

    private static boolean hasEvolvedLore(net.minecraft.item.ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) return false;

        net.minecraft.nbt.NBTTagCompound tag = stack.getTagCompound();
        if (!tag.hasKey("display", 10)) return false;

        net.minecraft.nbt.NBTTagCompound display = tag.getCompoundTag("display");
        if (!display.hasKey("Lore", 9)) return false;

        net.minecraft.nbt.NBTTagList list = display.getTagList("Lore", 8); // 8 = string
        for (int i = 0; i < list.tagCount(); i++) {
            String raw = list.getStringTagAt(i);
            // strip § codes, then check for "evolved"
            String plain = MC_CODES.matcher(raw).replaceAll("").toLowerCase(java.util.Locale.ROOT);
            if (plain.contains("evolved")) {
                return true;
            }
        }
        return false;
    }

    // Bottom→Top gradients (r,g,b 0..255)
    private static final Map<String, int[][]> GRAD = new HashMap<String, int[][]>() {{
        put("common",        new int[][]{{255,255,255}, {218,218,218}});
        put("uncommon",      new int[][]{{  0,102,  0}, { 51,255, 51}});
        put("rare",          new int[][]{{122, 44,224}, {166, 77,255}});
        put("epic",          new int[][]{{ 14, 11,230}, { 58,123,255}});
        put("legendary",     new int[][]{{255,170,  0}, {255,224, 92}});
        put("pearlescent",   new int[][]{{ 48,200,190}, {120,255,220}});
        put("seraph",        new int[][]{{230, 58,158}, {255,102,196}});
        put("black",         new int[][]{{  0,  0,  0}, { 20, 20, 20}});
        put("effervescent",  new int[][]{{255,255,255}, {255,255,255}});
        put("effervescent_", new int[][]{{255,255,255}, {255,255,255}});
        put("etech",         new int[][]{{255,  43,166}, {255,  43,166}});
        put("glitch",        new int[][]{{255, 122,191}, {255, 161,211}});
    }};

    private static boolean useNormalAlpha(String key) {
        return "legendary".equals(key) || "black".equals(key);
    }
    private static float alphaFor(String key) {
        if ("legendary".equals(key)) return ALPHA_LEGENDARY;
        if ("black".equals(key))     return ALPHA_BLACK;
        if ("etech".equals(key))     return ALPHA_ETECH;
        if ("glitch".equals(key))    return ALPHA_GLITCH;
        return ALPHA_DEFAULT;
    }

    // ── chaos detection ────────────────────────────────────────
    private enum Chaos { NONE, CHAOTIC, VOLATILE, PRIMORDIAL, ASCENDED }
    private static final Set<String> CHAOS_KEYS = new HashSet<String>(Arrays.asList(
            "chaotic","volatile","primordial","ascended"));

    private static final Pattern SECTION_CODES = Pattern.compile("§[0-9A-FK-ORa-fk-or]");
    private static final Pattern ANGLE_TAGS    = Pattern.compile("<[^>]+>");

    private static String strip(String s){
        if (s == null) return "";
        String out = SECTION_CODES.matcher(s).replaceAll("");
        out = ANGLE_TAGS.matcher(out).replaceAll("");
        return out.trim();
    }
    private static Chaos chaosFromLore(ItemStack stack){
        try {
            NBTTagCompound tag = stack.getTagCompound();
            if (tag == null || !tag.hasKey("display", 10)) return Chaos.NONE;
            NBTTagCompound d = tag.getCompoundTag("display");
            if (!d.hasKey("Lore", 9)) return Chaos.NONE;
            NBTTagList lore = d.getTagList("Lore", 8);
            for (int i = 0; i < lore.tagCount(); i++) {
                String s = strip(lore.getStringTagAt(i)).toLowerCase(Locale.ROOT);
                if (s.startsWith("chaos:")) s = s.substring(6).trim();
                if (!CHAOS_KEYS.contains(s)) continue;
                if ("chaotic".equals(s))    return Chaos.CHAOTIC;
                if ("volatile".equals(s))   return Chaos.VOLATILE;
                if ("primordial".equals(s)) return Chaos.PRIMORDIAL;
                if ("ascended".equals(s))   return Chaos.ASCENDED;
            }
        } catch (Throwable ignored) {}
        return Chaos.NONE;
    }
    // Short, jaggy lightning arcs that rise ~1 block around the base.
// Uses PARTICLE_TEX; additive blend expected by caller.
    private void renderLightningArcs(Tessellator t, double cx, double y, double cz,
                                     double baseR, double height, int count,
                                     int rgbHex, float alpha, float timeSec) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(PARTICLE_TEX);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glDisable(GL11.GL_CULL_FACE);
        t.setBrightness(240);

        float cr=((rgbHex>>16)&255)/255f, cg=((rgbHex>>8)&255)/255f, cb=(rgbHex&255)/255f;

        for (int i=0; i<count; i++) {
            // base angle + mild jitter
            double a0 = (2*Math.PI*i)/count + timeSec*0.35 + i*0.13;
            double r  = baseR * (0.95 + 0.25*Math.sin(timeSec*0.9 + i));
            double x0 = cx + Math.cos(a0)*r;
            double z0 = cz + Math.sin(a0)*r;

            // 3 jaggy segments per arc
            double segH = height / 3.0;
            double px = x0, py = y + AURA_LIFT, pz = z0;
            for (int s=0; s<3; s++) {
                double wob = 0.12 + 0.05*Math.sin(timeSec*2.0 + i*0.8 + s);
                double dir = a0 + (s%2==0 ? 1 : -1) * (0.35 + 0.25*Math.sin(timeSec*1.3 + i));
                double nx  = px + Math.cos(dir)*wob;
                double nz  = pz + Math.sin(dir)*wob;
                double ny  = py + segH;

                double w = 0.03 + 0.02*Math.sin(timeSec*3.2 + i + s); // thin bolt strip

                t.startDrawingQuads();
                t.setColorRGBA_F(cr, cg, cb, alpha);
                t.addVertexWithUV(px - w, py, pz - w, 0, 1);
                t.addVertexWithUV(px + w, py, pz - w, 1, 1);
                t.addVertexWithUV(nx + w, ny, nz + w, 1, 0);
                t.addVertexWithUV(nx - w, ny, nz - w, 0, 0);
                t.draw();

                px = nx; py = ny; pz = nz;
            }
        }
    }

    public static void register() { MinecraftForge.EVENT_BUS.register(new ItemBeamRenderer()); }
    // Outer spiral rendered OUTSIDE the cone (thicker, always visible)
    private void renderOuterSpiral(Tessellator t, double cx, double y0, double cz,
                                   double r, int h, float timeSec,
                                   int strands, double turns, int rgbHex, float alpha, float widthScale) {
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        t.setBrightness(240);
        int segs = Math.max(18, h * 3);
        double omega = turns * 2.0 * Math.PI;
        float rotSpeed = 0.040f;
        float cr=((rgbHex>>16)&255)/255f, cg=((rgbHex>>8)&255)/255f, cb=(rgbHex&255)/255f;
        float width = (float)(r * widthScale);

        int S=Math.max(1,strands);
        for (int s=0;s<S;s++){
            double strandPhase = (2.0 * Math.PI * s) / S;
            for (int i=0;i<segs;i++){
                double f0=i/(double)segs, f1=(i+1)/(double)segs;
                double a0=f0*omega + strandPhase + timeSec*rotSpeed;
                double a1=f1*omega + strandPhase + timeSec*rotSpeed;
                double x0=cx+Math.cos(a0)*r, z0=cz+Math.sin(a0)*r;
                double x1=cx+Math.cos(a1)*r, z1=cz+Math.sin(a1)*r;
                double nx0=-Math.sin(a0), nz0=Math.cos(a0);
                double nx1=-Math.sin(a1), nz1=Math.cos(a1);
                double yy0=y0+f0*h, yy1=y0+f1*h;

                t.startDrawingQuads();
                t.setColorRGBA_F(cr, cg, cb, alpha);
                t.addVertexWithUV(x0 - nx0*width, yy0, z0 - nz0*width, 0, 1);
                t.addVertexWithUV(x0 + nx0*width, yy0, z0 + nz0*width, 1, 1);
                t.addVertexWithUV(x1 + nx1*width, yy1, z1 + nz1*width, 1, 0);
                t.addVertexWithUV(x1 - nx1*width, yy1, z1 - nz1*width, 0, 0);
                t.draw();
            }
        }
    }

    // Short, soft cylinder made from the beacon sheet (XZ ring of quads)
    private void renderBeaconAura(Tessellator t, double x, double y, double z,
                                  double r, double h, int sides,
                                  float[] rgb, float alpha,
                                  float vScroll, float rot)
    {
        Minecraft.getMinecraft().getTextureManager().bindTexture(BEAM_TEX);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);  // additive
        GL11.glDisable(GL11.GL_CULL_FACE);
        t.setBrightness(240);

        // Use a thin V slice so it looks like a foggy veil, not the whole sheet
        float v0 = (vScroll % 1.0f);
        float v1 = v0 + (float)(h * 0.15f);   // small slice of the texture
        float u0 = 0.00f, u1 = 1.00f;

        for (int i = 0; i < Math.max(8, sides); i++) {
            double a0 = rot + (2.0 * Math.PI * i) / sides;
            double a1 = rot + (2.0 * Math.PI * (i + 1)) / sides;

            double x0 = x + Math.cos(a0) * r, z0 = z + Math.sin(a0) * r;
            double x1 = x + Math.cos(a1) * r, z1 = z + Math.sin(a1) * r;

            t.startDrawingQuads();
            t.setColorRGBA_F(rgb[0], rgb[1], rgb[2], alpha);
            t.addVertexWithUV(x0, y,     z0, u0, v1);
            t.addVertexWithUV(x1, y,     z1, u1, v1);
            t.addVertexWithUV(x1, y + h, z1, u1, v0);
            t.addVertexWithUV(x0, y + h, z0, u0, v0);
            t.draw();
        }
    }

    private void renderBeaconDisc(Tessellator t, double x, double y, double z,
                                  double r, float[] rgb, float alpha, float vScroll)
    {
        Minecraft.getMinecraft().getTextureManager().bindTexture(BEAM_TEX);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glDisable(GL11.GL_CULL_FACE);
        t.setBrightness(240);

        float u0 = 0.0f, u1 = 1.0f;
        float v0 = (vScroll % 1.0f), v1 = v0 + 0.15f;

        t.startDrawingQuads();
        t.setColorRGBA_F(rgb[0], rgb[1], rgb[2], alpha);
        t.addVertexWithUV(x - r, y + 0.02, z - r, u0, v1);
        t.addVertexWithUV(x + r, y + 0.02, z - r, u1, v1);
        t.addVertexWithUV(x + r, y + 0.02, z + r, u1, v0);
        t.addVertexWithUV(x - r, y + 0.02, z + r, u0, v0);
        t.draw();
    }

    // Ascended: radial rays at the top
    private void renderSunburstTop(Tessellator t, double cx, double y, double cz,
                                   double r, int rays, int rgbHex, float alpha, float timeSec) {
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        t.setBrightness(240);
        float cr=((rgbHex>>16)&255)/255f, cg=((rgbHex>>8)&255)/255f, cb=(rgbHex&255)/255f;

        for (int i=0;i<rays;i++){
            double a = (2*Math.PI*i)/rays + timeSec*0.35;
            double len = r * (1.2 + 0.25*Math.sin(timeSec*1.1 + i));
            double x1 = cx + Math.cos(a)*len;
            double z1 = cz + Math.sin(a)*len;
            double w  = r*0.06;

            t.startDrawingQuads();
            t.setColorRGBA_F(cr, cg, cb, alpha);
            t.addVertexWithUV(cx - w, y, cz - w, 0, 1);
            t.addVertexWithUV(cx + w, y, cz - w, 1, 1);
            t.addVertexWithUV(x1 + w, y + 0.02, z1 + w, 1, 0);
            t.addVertexWithUV(x1 - w, y + 0.02, z1 - w, 0, 0);
            t.draw();
        }
    }

    // Ascended: slow shimmering shards drifting down inside/around beam
    private void renderFallingShards(Tessellator t, double cx, double y, double cz,
                                     double r, int h, float timeSec, int count, int rgbHex, float alpha) {
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        t.setBrightness(240);
        float cr=((rgbHex>>16)&255)/255f, cg=((rgbHex>>8)&255)/255f, cb=(rgbHex&255)/255f;

        for (int i=0;i<count;i++){
            double ang = (i*0.9) + timeSec*0.4;
            double rad = r * (0.4 + 0.8*((i%7)/7.0));
            double sx  = cx + Math.cos(ang)*rad;
            double sz  = cz + Math.sin(ang)*rad;
            double sy  = y + (h * (1.0 - ((timeSec*0.15 + i*0.07) % 1.0))); // falls down

            double s = 0.05 + 0.05*Math.abs(Math.sin(timeSec*2.0 + i));
            t.startDrawingQuads();
            t.setColorRGBA_F(cr, cg, cb, alpha);
            t.addVertexWithUV(sx - s, sy, sz - s, 0, 1);
            t.addVertexWithUV(sx + s, sy, sz - s, 1, 1);
            t.addVertexWithUV(sx + s, sy, sz + s, 1, 0);
            t.addVertexWithUV(sx - s, sy, sz + s, 0, 0);
            t.draw();
        }
    }
    // 0..255 rgb[] -> packed 0xRRGGBB
    private static int packRGB(int[] rgb){ return ((rgb[0]&255)<<16)|((rgb[1]&255)<<8)|(rgb[2]&255); }
    // 0..255 rgb[] mix
    private static int[] mixRGB(int[] a, int[] b, float t){
        float u = Math.max(0f, Math.min(1f, t));
        return new int[]{
                (int)(a[0] + (b[0]-a[0])*u),
                (int)(a[1] + (b[1]-a[1])*u),
                (int)(a[2] + (b[2]-a[2])*u)
        };
    }

    // Soft animated ring on the ground using the same texture family as your halos.
    private void renderGlyphAuraDisc(Tessellator t, double x, double y, double z,
                                     double r, float alpha, int rgbHex, float timeSec) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(GLYPH_RING_TEX);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glDisable(GL11.GL_CULL_FACE);
        t.setBrightness(240);

        float cr=((rgbHex>>16)&255)/255f, cg=((rgbHex>>8)&255)/255f, cb=(rgbHex&255)/255f;

        // gentle breathing + slow spin
        double rr = r * (1.00 + 0.04*Math.sin(timeSec*1.6));
        float uOff = (timeSec * 0.07f) % 1.0f;

        t.startDrawingQuads();
        t.setColorRGBA_F(cr, cg, cb, alpha);
        // UVs offset to create a subtle rotation shimmer
        t.addVertexWithUV(x - rr, y + 0.04, z - rr, 0+uOff, 1);
        t.addVertexWithUV(x + rr, y + 0.04, z - rr, 1+uOff, 1);
        t.addVertexWithUV(x + rr, y + 0.04, z + rr, 1+uOff, 0);
        t.addVertexWithUV(x - rr, y + 0.04, z + rr, 0+uOff, 0);
        t.draw();
    }

    // Vertical “veil” slices around the base, also from the glyph texture.
    private void renderGlyphVeils(Tessellator t, double x, double y, double z,
                                  double ringR, double height, int count,
                                  int rgbHex, float alpha, float timeSec) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(GLYPH_RING_TEX);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glDisable(GL11.GL_CULL_FACE);
        t.setBrightness(240);

        float cr=((rgbHex>>16)&255)/255f, cg=((rgbHex>>8)&255)/255f, cb=(rgbHex&255)/255f;

        for (int i=0;i<count;i++){
            double a   = (2*Math.PI*i)/count + timeSec*0.25;
            double rad = ringR * (0.92 + 0.10*Math.sin(timeSec*0.9 + i));
            double sx  = x + Math.cos(a)*rad;
            double sz  = z + Math.sin(a)*rad;

            // face toward center
            double face = a + Math.PI;
            double halfW = ringR * 0.12;
            double cosA  = Math.cos(face), sinA = Math.sin(face);

            double xL = sx - cosA*halfW, zL = sz - sinA*halfW;
            double xR = sx + cosA*halfW, zR = sz + sinA*halfW;

            double y0 = y + 0.02;
            double y1 = y0 + height * (0.70 + 0.12*Math.sin(timeSec*0.7 + i));

            float aF = alpha * (0.85f + 0.15f*(float)Math.sin(timeSec*2.0 + i));

            t.startDrawingQuads();
            t.setColorRGBA_F(cr, cg, cb, aF);
            t.addVertexWithUV(xL, y0, zL, 0, 1);
            t.addVertexWithUV(xR, y0, zR, 1, 1);
            t.addVertexWithUV(xR, y1, zR, 1, 0);
            t.addVertexWithUV(xL, y1, zL, 0, 0);
            t.draw();
        }
    }
    // Rotating/breathing base aura using auragi.png
    private void renderGiAura(Tessellator t, double x, double y, double z,
                              double radius, double height, int billboards,
                              int rgbHex, float alpha, float timeSec) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(AURA_GI_TEX);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);   // additive
        GL11.glDisable(GL11.GL_CULL_FACE);
        t.setBrightness(240);

        float cr=((rgbHex>>16)&255)/255f, cg=((rgbHex>>8)&255)/255f, cb=(rgbHex&255)/255f;

        // soft “breathing”
        double r  = radius * (1.00 + 0.05*Math.sin(timeSec*1.5));
        double y0 = y + 0.02;

        // 1) ground disc (flat, subtle spin via UV offset)
        float uOff = (timeSec * 0.08f) % 1.0f;
        t.startDrawingQuads();
        t.setColorRGBA_F(cr, cg, cb, alpha * 0.85f);
        t.addVertexWithUV(x - r, y0,     z - r, 0+uOff, 1);
        t.addVertexWithUV(x + r, y0,     z - r, 1+uOff, 1);
        t.addVertexWithUV(x + r, y0,     z + r, 1+uOff, 0);
        t.addVertexWithUV(x - r, y0,     z + r, 0+uOff, 0);
        t.draw();

        // 2) upright veils around the ring
        for (int i=0;i<billboards;i++){
            double a   = (2*Math.PI*i)/billboards + timeSec*0.30;
            double rad = r * (0.92 + 0.10*Math.sin(timeSec*0.9 + i));
            double sx  = x + Math.cos(a)*rad;
            double sz  = z + Math.sin(a)*rad;

            // face toward center
            double face = a + Math.PI;
            double halfW = r * 0.14;
            double cosA  = Math.cos(face), sinA = Math.sin(face);

            double xL = sx - cosA*halfW, zL = sz - sinA*halfW;
            double xR = sx + cosA*halfW, zR = sz + sinA*halfW;

            double y1 = y0 + height * (0.65 + 0.12*Math.sin(timeSec*0.7 + i));
            float  aF = alpha * (0.75f + 0.25f*(float)Math.sin(timeSec*2.0 + i));

            t.startDrawingQuads();
            t.setColorRGBA_F(cr, cg, cb, aF);
            t.addVertexWithUV(xL, y0, zL, 0, 1);
            t.addVertexWithUV(xR, y0, zR, 1, 1);
            t.addVertexWithUV(xR, y1, zR, 1, 0);
            t.addVertexWithUV(xL, y1, zL, 0, 0);
            t.draw();
        }
    }

    private static final ResourceLocation GLYPH_RING_TEX =
            new ResourceLocation("hexcolorcodes:textures/misc/glyph_ring.png");

    private static final ResourceLocation AURA_GI_TEX =
            new ResourceLocation("textures/entity/beacon_beam.png");

    // ── AURA PRIMITIVES ─────────────────────────────────────────────
    private static final ResourceLocation PARTICLE_TEX =
            new ResourceLocation("hexcolorcodes:textures/misc/auragi.png");
    // NEW: single-sprite evolved star texture
    private static final ResourceLocation EVOLVED_STAR_TEX =
            new ResourceLocation("hexcolorcodes:textures/misc/auragi.png");
    // Pink ascended flare (your new texture)
    private static final ResourceLocation ASCENDED_FLARE_TEX =
            new ResourceLocation("hexcolorcodes:textures/misc/particle2.png");
// ^ change path/filename if you use something else (e.g. particle2.png)

    // Flat ground haze (XZ plane), camera independent
    private void renderGroundHaze(Tessellator t, double x, double y, double z,
                                  double radius, float alpha, int rgbHex, float timeSec) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(PARTICLE_TEX);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);   // additive
        GL11.glDisable(GL11.GL_CULL_FACE);
        t.setBrightness(240);

        float cr=((rgbHex>>16)&255)/255f, cg=((rgbHex>>8)&255)/255f, cb=(rgbHex&255)/255f;
        double r  = radius * (1.00 + 0.06*Math.sin(timeSec*1.6));  // slow breathing
        double s0 = r, s1 = r;

        t.startDrawingQuads();
        t.setColorRGBA_F(cr, cg, cb, alpha);
        // use a square over the circle—cheap and looks like a soft disc with particle tex
        t.addVertexWithUV(x - s0, y + 0.02, z - s1, 0, 1);
        t.addVertexWithUV(x + s0, y + 0.02, z - s1, 1, 1);
        t.addVertexWithUV(x + s0, y + 0.02, z + s1, 1, 0);
        t.addVertexWithUV(x - s0, y + 0.02, z + s1, 0, 0);
        t.draw();
    }

    // Upright fog sheets arranged in a ring; each sheet gently rotates & lifts
    private void renderAuraPillars(Tessellator t, double x, double y, double z,
                                   double radius, double height, int count,
                                   int rgbHex, float alpha, float timeSec) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(PARTICLE_TEX);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);   // additive
        GL11.glDisable(GL11.GL_CULL_FACE);
        t.setBrightness(240);

        float cr=((rgbHex>>16)&255)/255f, cg=((rgbHex>>8)&255)/255f, cb=(rgbHex&255)/255f;

        for (int i = 0; i < count; i++) {
            double baseAng = (2*Math.PI*i)/count + timeSec*0.25;  // slow orbit
            double jitter  = 0.10*Math.sin(timeSec*1.3 + i*0.9);
            double rad     = radius * (0.90 + 0.15*Math.sin(timeSec*0.7 + i));
            double sx      = x + Math.cos(baseAng)*rad;
            double sz      = z + Math.sin(baseAng)*rad;
            double sy0     = y + 0.02 + 0.10*Math.sin(timeSec*1.5 + i); // slight bob
            double sy1     = sy0 + height * (0.55 + 0.15*Math.sin(timeSec*0.9 + i*0.7));

            // face roughly toward center by rotating quad around Y
            double faceAng = baseAng + Math.PI + jitter;
            double halfW   = radius * 0.14;   // sheet width
            double cosA    = Math.cos(faceAng), sinA = Math.sin(faceAng);

            // four corners for an upright quad
            double xL = sx - cosA*halfW, zL = sz - sinA*halfW;
            double xR = sx + cosA*halfW, zR = sz + sinA*halfW;

            float a = alpha * (0.80f + 0.20f*(float)Math.sin(timeSec*2.0 + i)); // little flicker

            t.startDrawingQuads();
            t.setColorRGBA_F(cr, cg, cb, a);
            t.addVertexWithUV(xL, sy0, zL, 0, 1);
            t.addVertexWithUV(xR, sy0, zR, 1, 1);
            t.addVertexWithUV(xR, sy1, zR, 1, 0);
            t.addVertexWithUV(xL, sy1, zL, 0, 0);
            t.draw();
        }
    }
    // Simple deterministic pseudo-random 0..1 based on an int index + salt
    private static double starNoise(int idx, int salt) {
        double x = idx * 12.9898 + salt * 78.233;
        double s = Math.sin(x) * 43758.5453;
        return s - Math.floor(s); // fract(s)
    }
    // Ascended-only: pink flares spiraling up the shaft
    private void renderAscendedFlareSpiral(Tessellator t,
                                           double cx, double y0, double cz,
                                           double r, int h, float timeSec,
                                           int count, int rgbHex, float baseAlpha) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(ASCENDED_FLARE_TEX);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glDisable(GL11.GL_CULL_FACE);
        t.setBrightness(240);

        float cr = ((rgbHex >> 16) & 255) / 255f;
        float cg = ((rgbHex >>  8) & 255) / 255f;
        float cb = ( rgbHex        & 255) / 255f;

        // how many full turns the spiral makes
        double turns = 2.5;
        double omega = turns * 2.0 * Math.PI;

        for (int i = 0; i < count; i++) {
            double f = i / (double)count;     // 0..1 along the spiral
            double ang = f * omega + timeSec * 0.7;   // rotate over time
            double yy  = y0 + h * f;                 // climb up the shaft

            double rad = r * (0.65 + 0.25 * Math.sin(timeSec * 0.8 + i * 0.7));
            double sx  = cx + Math.cos(ang) * rad;
            double sz  = cz + Math.sin(ang) * rad;

            // size + flicker
            double size = 0.22 + 0.10 * Math.sin(timeSec * 2.1 + i * 0.9);
            float a = baseAlpha * (0.65f + 0.35f * (float)Math.sin(timeSec * 3.0 + i * 0.5));

            t.startDrawingQuads();
            t.setColorRGBA_F(cr, cg, cb, a);
            t.addVertexWithUV(sx - size, yy,        sz - size, 0, 1);
            t.addVertexWithUV(sx + size, yy,        sz - size, 1, 1);
            t.addVertexWithUV(sx + size, yy,        sz + size, 1, 0);
            t.addVertexWithUV(sx - size, yy,        sz + size, 0, 0);
            t.draw();
        }
    }

    // Small, bright “stars” that orbit the shaft on a few rings.
    // Small, bright “stars” that float around the shaft in a loose cloud.
    private void renderEvolvedStars(Tessellator t, double cx, double y0, double cz,
                                    double ringR, int h, float timeSec,
                                    int ringCount, int starsPerRing,
                                    int rgbHex, float alpha) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(EVOLVED_STAR_TEX);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glDisable(GL11.GL_CULL_FACE);
        t.setBrightness(240);

        float cr = ((rgbHex >> 16) & 255) / 255f;
        float cg = ((rgbHex >>  8) & 255) / 255f;
        float cb = ( rgbHex        & 255) / 255f;

        // Total stars = “ringCount * starsPerRing”, but we ignore actual rings now
        int totalStars = Math.max(1, ringCount * starsPerRing);

        for (int i = 0; i < totalStars; i++) {
            // 0..1 randomish values for this star
            double r1 = starNoise(i, 11);
            double r2 = starNoise(i, 37);
            double r3 = starNoise(i, 73);

            // Base cylindrical distribution
            double baseRad    = ringR * (0.45 + 0.65 * r1);      // 0.45R .. 1.10R
            double baseAngle  = r2 * (Math.PI * 2.0);            // 0..2π
            double baseHeight = h * r3;                          // 0..h

            // Gentle drift over time so they’re not locked
            double ang        = baseAngle + timeSec * (0.25 + 0.25 * r1);
            double ry         = y0 + baseHeight
                    + 0.16 * Math.sin(timeSec * 0.9 + r2 * 10.0);

            double sx = cx + Math.cos(ang) * baseRad;
            double sz = cz + Math.sin(ang) * baseRad;

            // Size + flicker
            double size = 0.10 + 0.06 * Math.sin(timeSec * 2.4 + i);
            float  a    = alpha * (0.70f + 0.30f * (float)Math.sin(timeSec * 3.0 + i * 0.7));

            t.startDrawingQuads();
            t.setColorRGBA_F(cr, cg, cb, a);
            t.addVertexWithUV(sx - size, ry, sz - size, 0, 1);
            t.addVertexWithUV(sx + size, ry, sz - size, 1, 1);
            t.addVertexWithUV(sx + size, ry, sz + size, 1, 0);
            t.addVertexWithUV(sx - size, ry, sz + size, 0, 0);
            t.draw();
        }
    }

    // Adds pulsing rings up the shaft + orbiting stars around the beam.
    private void renderEvolvedOverlay(Tessellator t,
                                      double x, double y, double z,
                                      double radius, int h,
                                      float timeSec) {
        // Base sizes for the extra FX
        double outerR = radius * 1.75;
        double ringR  = radius * 1.50;

        // cyan / white accent for “evolved”
        int   evoOuter   = 0x9FFFFF;
        int   evoInner   = 0xFFFFFF;
        float ringAlpha  = 0.80f;
        float starAlpha  = 0.99f;

        beginOverlay();

        // ── 1) Thin outer spiral up the shaft (keeps that “upgraded” feel) ──
        renderOuterSpiral(
                t, x, y, z,
                outerR, h,
                timeSec * 0.7f,
                3,              // strands
                2.1,            // turns
                evoOuter,
                0.70f,          // alpha
                0.18f           // widthScale
        );

        // ── 2) Pulsing rings at several heights ──
        // place 3 rings spaced along the beam
        int ringCount = 3;
        for (int i = 0; i < ringCount; i++) {
            double frac   = (i + 1) / (double)(ringCount + 1);  // 0..1 along height
            double ry     = y + h * frac;
            double breathe = 1.0 + 0.06 * Math.sin(timeSec * 1.8 + i * 0.9);

            double rOuter = ringR * (1.18 + 0.12 * Math.sin(timeSec * 1.3 + i));
            double rInner = rOuter * 0.86;

            float aOuter = ringAlpha * (0.75f + 0.25f * (float)Math.sin(timeSec * 2.1 + i));
            float aInner = ringAlpha * 0.9f;

            // bright outer band
            renderTopHalo(
                    t, x, ry, z,
                    rOuter * breathe,
                    40,
                    evoOuter,
                    aOuter,
                    timeSec * 1.2f + i * 0.4f
            );
            // tighter inner band
            renderTopHalo(
                    t, x, ry, z,
                    rInner * breathe,
                    32,
                    evoInner,
                    aInner,
                    -timeSec * 1.4f - i * 0.3f
            );
        }

        // ── 3) Orbiting “stars” around the beam ──
        // a few rings of drifting points; small but bright
        int rings         = 3;
        int starsPerRing  = 10;   // total stars ≈ 30
        int starColor     = evoInner;
        renderEvolvedStars(t, x, y, z,
                radius * 1.45,
                h,
                timeSec,
                rings,
                starsPerRing,
                starColor,
                starAlpha
        );

        endOverlay();
    }


    @SubscribeEvent
    public void onRender(RenderWorldLastEvent evt) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.thePlayer;
        if (p == null || mc.theWorld == null) return;

        // Config gate
        if (!ModConfig.enableBeams) return;

        // Live knobs (guard against bad/zero values)
        final int radiusScan = (ModConfig.scanRadius > 0) ? ModConfig.scanRadius : RADIUS_SCAN_DEFAULT;
        final int maxDraw    = (ModConfig.maxDraw    > 0) ? ModConfig.maxDraw    : MAX_DRAW_DEFAULT;

        double camX = p.lastTickPosX + (p.posX - p.lastTickPosX) * evt.partialTicks;
        double camY = p.lastTickPosY + (p.posY - p.lastTickPosY) * evt.partialTicks;
        double camZ = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * evt.partialTicks;

        @SuppressWarnings("unchecked")
        List<EntityItem> items = mc.theWorld.getEntitiesWithinAABB(
                EntityItem.class,
                AxisAlignedBB.getBoundingBox(
                        p.posX - radiusScan, p.posY - radiusScan, p.posZ - radiusScan,
                        p.posX + radiusScan, p.posY + radiusScan, p.posZ + radiusScan
                )
        );
        if (items.isEmpty()) return;

        // global setup for the whole pass
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glDisable(GL11.GL_CULL_FACE);
        mc.getTextureManager().bindTexture(BEAM_TEX);

        Tessellator t = Tessellator.instance;

        final long  tMs        = Minecraft.getSystemTime();
        final float tSec       = (tMs % 200000L) / 1000f;
        final float tTicks     = (mc.theWorld.getTotalWorldTime() + evt.partialTicks);
        final float baseScroll = tTicks * 0.02f;
        final float phaseTime  = tSec;

        int drawn = 0;

        for (EntityItem ei : items) {
            if (drawn >= maxDraw) break;
            if (ei.isDead) continue;
            ItemStack st = ei.getEntityItem();
            if (st == null) continue;

            // ── isolate GL state per item ─────────────────────────────
            GL11.glPushMatrix();
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            try {
                NBTTagCompound tag = st.getTagCompound();

                String key = (tag != null && tag.hasKey(RarityTags.KEY)) ? tag.getString(RarityTags.KEY) : "";
                if (key == null || key.trim().isEmpty()) {
                    key = RarityDetect.fromStack(st);
                }
                key = (key == null) ? "" : key.trim().toLowerCase().replaceAll("[^a-z0-9_]", "");
                if (key.isEmpty()) {
                    continue; // nothing to render for this stack
                }

                Chaos chaos = chaosFromLore(st);

                int h = (tag != null && tag.hasKey(RarityTags.HKEY))
                        ? tag.getInteger(RarityTags.HKEY)
                        : RarityDetect.beamHeight(key);

                if (chaos == Chaos.ASCENDED)   h += 3;
                if (chaos == Chaos.PRIMORDIAL) h += 1;

                double baseR = 0.22 + (h >= 14 ? 0.06 : 0.0);

                int    sides   = 16;
                double taper   = 0.82;
                double twist   = 0.00;
                float  uvScale = 0.50f;

                double pulse = 0.0;
                if ("legendary".equals(key) || "epic".equals(key)) {
                    pulse = 0.10 * Math.sin(phaseTime * 0.20 * (float)(2*Math.PI));
                } else if ("effervescent".equals(key) || "effervescent_".equals(key)) {
                    pulse = 0.16 * Math.sin(phaseTime * 0.35 * (float)(2*Math.PI));
                } else if ("etech".equals(key)) {
                    pulse = 0.12 * Math.sin(phaseTime * 0.30 * (float)(2*Math.PI));
                } else if ("glitch".equals(key)) {
                    pulse = 0.10 * Math.sin(phaseTime * 0.25 * (float)(2*Math.PI));
                }

                // chaos traits
                float  chaosAlphaMod = 1.0f;
                double chaosRadJit   = 0.0;
                switch (chaos) {
                    case CHAOTIC:
                        pulse += 0.06 * Math.sin(phaseTime * 2.0 + ei.getEntityId() * 0.77);
                        twist += 0.10 * Math.sin(phaseTime * 1.2 + ei.getEntityId() * 0.11);
                        chaosAlphaMod = 0.95f;
                        break;
                    case VOLATILE:
                        chaosAlphaMod = 1.0f - (VOLATILE_ALPHA_JIT * 0.5f)
                                + VOLATILE_ALPHA_JIT * (float) (0.5 + 0.5 * Math.sin(phaseTime * 6.0 + ei.getEntityId() * 0.37));
                        chaosRadJit = VOLATILE_RAD_JIT * Math.sin(phaseTime * 5.2 + ei.getEntityId() * 0.21);
                        break;
                    case PRIMORDIAL:
                        taper = 0.78;
                        chaosAlphaMod = 0.92f;
                        break;
                    case ASCENDED:
                        baseR *= 0.95;
                        twist += 0.10;
                        chaosAlphaMod = 0.94f;
                        break;
                    default:
                        break;
                }

                double radius = baseR * (1.0 + pulse + chaosRadJit);

                // evolved flag from lore
                boolean evolved = hasEvolvedLore(st);
                if (evolved) {
                    radius *= 1.10; // slightly thicker shaft
                }

                if      ("legendary".equals(key))   twist += 0.10;
                else if ("seraph".equals(key))      twist += 0.08;
                else if ("pearlescent".equals(key)) twist += 0.08;
                else if ("epic".equals(key))        twist += 0.12;
                else if ("rare".equals(key))        twist += 0.08;
                else if ("uncommon".equals(key))    twist += 0.06;
                else if ("etech".equals(key))       twist += 0.12;
                else if ("glitch".equals(key))      twist += 0.10;

                // world-space position relative to camera
                double x = ei.lastTickPosX + (ei.posX - ei.lastTickPosX) * evt.partialTicks - camX;
                double y = ei.lastTickPosY + (ei.posY - ei.lastTickPosY) * evt.partialTicks - camY + 0.1;
                double z = ei.lastTickPosZ + (ei.posZ - ei.lastTickPosZ) * evt.partialTicks - camZ;

                int[]  bot255, top255;
                float[] botRGB, topRGB;

                if ("effervescent".equals(key) || "effervescent_".equals(key)) {
                    float speed = "effervescent_".equals(key) ? EFF_SPEED_FAST : EFF_SPEED;
                    float hueB  = (phaseTime * speed) % 1.0f;
                    float hueT  = (hueB + 0.08f) % 1.0f;
                    int   cB    = Color.HSBtoRGB(hueB, 1f, 1f);
                    int   cT    = Color.HSBtoRGB(hueT, 1f, 1f);
                    bot255 = new int[]{(cB >> 16) & 255, (cB >> 8) & 255, cB & 255};
                    top255 = new int[]{(cT >> 16) & 255, (cT >> 8) & 255, cT & 255};
                } else {
                    int[][] stops = GRAD.containsKey(key)
                            ? GRAD.get(key)
                            : new int[][]{{255, 255, 255}, {255, 255, 255}};
                    bot255 = stops[0];
                    top255 = stops[1];
                }

                botRGB = rgb(bot255[0], bot255[1], bot255[2]);
                topRGB = rgb(top255[0], top255[1], top255[2]);

                boolean normalAlpha = useNormalAlpha(key);
                if (normalAlpha) {
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                } else {
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                }

                float alpha = alphaFor(key) * chaosAlphaMod;
                if (evolved) {
                    alpha = Math.min(1.0f, alpha + 0.12f);
                }

                // main beam cone
                mc.getTextureManager().bindTexture(BEAM_TEX);
                renderConeGradient(t, x, y, z, radius, h, botRGB, topRGB, baseScroll,
                        sides, taper, twist, uvScale, alpha, tTicks);

                // ── extra chaos passes ─────────────────────────
                if (chaos == Chaos.CHAOTIC) {
                    renderBaseGlowBillboard(t, x, y, z, radius * CHAOTIC_GLOW_SIZE, CHAOTIC_GLOW_ALPHA, 0x9DF3FF);
                    renderSparkBurst(t, x, y, z, radius * 0.85, h, phaseTime, CHAOTIC_SPARKS, 0xC0F9FF, 1.4f);
                }
                if (chaos == Chaos.VOLATILE) {
                    renderSparkBurst(t, x, y, z, radius * 0.75, h, phaseTime, VOLATILE_SPARKS, 0xE8FFFF, 2.0f);
                }

                if (chaos == Chaos.PRIMORDIAL) {
                    final double outerR = radius * 1.30;

                    // 1) main cone again (keeps density)
                    mc.getTextureManager().bindTexture(BEAM_TEX);
                    renderConeGradient(t, x, y, z, radius, h, botRGB, topRGB, baseScroll,
                            sides, taper, twist, uvScale, alpha, tTicks);

                    // 2) overlays
                    beginOverlay();
                    mc.getTextureManager().bindTexture(BEAM_TEX);

                    final int primMainHex = packRGB(mixRGB(bot255, top255, 0.45f));
                    final int primAccent  = 0xF2FFFF;
                    renderOuterSpiral(t, x, y, z, outerR,        h, phaseTime,         3, 2.20, primMainHex, 0.96f, 0.22f);
                    renderOuterSpiral(t, x, y, z, outerR * 0.96, h, phaseTime + 0.55f, 2, 2.40, primAccent,  0.75f, 0.14f);

                    final int baseRingHex = packRGB(mixRGB(bot255, top255, 0.40f));
                    renderTopHalo(t, x, y + BASE_RING_LIFT, z,
                            radius * PRIM_BASE_RING_SCALE, 36, baseRingHex, BASE_RING_ALPHA_SOFT, phaseTime);

                    final int ringHex = packRGB(mixRGB(bot255, top255, 0.35f));
                    renderOrbitRingsWithDots(t, x, y, z, radius * 1.10, h, phaseTime,
                            PRIM_RING_COUNT + 1, PRIM_DOTS_PER_RING + 6, ringHex);

                    final double auraR   = radius * 1.12;
                    final double auraH   = 0.90;
                    final float  aRot    = (float) (phaseTime * 0.35);
                    final float  vOff    = baseScroll * 0.5f;
                    final float[] auraRGB = rgb(
                            (int) (bot255[0] * 0.85 + top255[0] * 0.15),
                            (int) (bot255[1] * 0.85 + top255[1] * 0.15),
                            (int) (bot255[2] * 0.85 + top255[2] * 0.15)
                    );

                    renderBeaconDisc (t, x, y,           z, auraR * 0.95, auraRGB, 0.55f, vOff);
                    renderBeaconAura (t, x, y + 0.02,    z, auraR, auraH, 12,      auraRGB, 0.46f, vOff, aRot);

                    endOverlay();

                    mc.getTextureManager().bindTexture(BEAM_TEX);
                    final int[] innerB = mixRGB(bot255, top255, 0.20f);
                    final int[] innerT = mixRGB(bot255, top255, 0.38f);
                    final float[] cB   = rgb(innerB[0], innerB[1], innerB[2]);
                    final float[] cT   = rgb(innerT[0], innerT[1], innerT[2]);
                    renderConeGradient(t, x, y, z, radius * 0.60, h, cB, cT, baseScroll,
                            sides, 0.70, twist * 0.5, uvScale * 0.9f,
                            Math.min(0.60f, alpha * 0.80f), tTicks);
                }

                if (chaos == Chaos.ASCENDED) {
                    double outerR1 = radius * 1.38, outerR2 = radius * 1.22;
                    int goldHex = 0xFFF5CC, whiteHex = 0xFFFFFF;
                    double breathe = 1.0 + 0.03 * Math.sin(phaseTime * 1.4);

                    mc.getTextureManager().bindTexture(BEAM_TEX);
                    renderConeGradient(t, x, y, z, radius, h, botRGB, topRGB, baseScroll,
                            sides, taper, twist, uvScale, alpha, tTicks);

                    beginOverlay();
                    mc.getTextureManager().bindTexture(BEAM_TEX);

                    renderTopHalo(t, x, y + BASE_RING_LIFT, z,
                            radius * ASC_BASE_RING_OUTER * breathe,
                            ASC_HALO_SIDES, goldHex,  BASE_RING_ALPHA_STRONG, phaseTime);
                    renderTopHalo(t, x, y + BASE_RING_LIFT, z,
                            radius * ASC_BASE_RING_INNER * breathe,
                            ASC_HALO_SIDES, whiteHex, BASE_RING_ALPHA_SOFT,   phaseTime);

                    renderTopHalo(t, x, y + BASE_RING_LIFT + 0.05, z,
                            radius * (ASC_BASE_RING_OUTER * 0.88) * breathe,
                            ASC_HALO_SIDES, goldHex, BASE_RING_ALPHA_SOFT, phaseTime + 0.4f);
                    renderTopHalo(t, x, y + BASE_RING_LIFT + 0.10, z,
                            radius * (ASC_BASE_RING_OUTER * 0.72) * breathe,
                            ASC_HALO_SIDES, whiteHex, BASE_RING_ALPHA_SOFT * 0.9f, phaseTime + 0.8f);

                    int softGold = 0xFFF5CC;
                    renderSoftSpiral(
                            t, x, y, z,
                            radius * 0.95,
                            h,
                            phaseTime,
                            2,
                            2.3,
                            softGold,
                            0.85f
                    );

                    renderVerticalPulse(t, x, y, z,
                            radius * 0.80,
                            h,
                            phaseTime,
                            whiteHex,
                            0.70f);

                    mc.getTextureManager().bindTexture(ASCENDED_FLARE_TEX);
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                    t.setBrightness(240);

                    int    flareCount = 48;
                    double turns      = 2.5;
                    double omega      = turns * 2.0 * Math.PI;

                    for (int i = 0; i < flareCount; i++) {
                        if (i % 3 != 0) continue;

                        double f   = i / (double) flareCount;
                        double ang = f * omega + phaseTime * 0.7;
                        double yy  = y + h * f;

                        double ringR = radius * 1.22
                                + radius * 0.10 * Math.sin(phaseTime * 0.9 + i);
                        double sx    = x + Math.cos(ang) * ringR;
                        double sz    = z + Math.sin(ang) * ringR;

                        double size = 0.18 + 0.06 * Math.sin(phaseTime * 2.1 + i);
                        float  a    = 0.35f + 0.45f * (float) Math.sin(phaseTime * 3.0 + i * 0.5);

                        t.startDrawingQuads();
                        t.setColorRGBA_F(1.0f, 1.0f, 1.0f, a);
                        t.addVertexWithUV(sx - size, yy,        sz - size, 0, 1);
                        t.addVertexWithUV(sx + size, yy,        sz - size, 1, 1);
                        t.addVertexWithUV(sx + size, yy,        sz + size, 1, 0);
                        t.addVertexWithUV(sx - size, yy,        sz + size, 0, 0);
                        t.draw();
                    }

                    endOverlay();
                }

                // evolved overlay on top of chaos FX
                if (evolved) {
                    renderEvolvedOverlay(t, x, y, z, radius, h, phaseTime);
                }

                if ("effervescent_".equals(key)) {
                    renderRainbowRings(t, x, y, z, radius * 1.06, h, phaseTime, 3);
                    renderRainbowSpiral(t, x, y, z, radius * 0.70, h, phaseTime, 2, 1.5);
                }

                if (normalAlpha) {
                    // restore to additive default for the rest of this item’s passes
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                }
            } finally {
                GL11.glPopAttrib();
                GL11.glPopMatrix();
            }

            if (++drawn >= maxDraw) break;
        }

        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glPopMatrix();
    }

    // Draw “on top” of the cone: additive, no depth write, no depth test (so it won’t hide inside)
// Call endOverlay() right after you finish those overlays.
    private void beginOverlay() {
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE); // additive
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
    }
    private void endOverlay() {
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE); // restore additive default
    }

    // ===== base cone =====
    private static float[] rgb(int r, int g, int b) { return new float[]{ r/255f, g/255f, b/255f }; }

    private void renderConeGradient(Tessellator t, double x, double y, double z,
                                    double baseR, double h, float[] botRGB, float[] topRGB,
                                    float vScroll, int sides, double taperTop, double twistTurns,
                                    float uvScale, float alpha, float tTicks) {
        if (sides < 3) sides = 3;
        t.setBrightness(240);
        double topR  = baseR * taperTop;
        double twist = twistTurns * 2.0 * Math.PI;
        float v1 = vScroll;
        float v2 = vScroll + (float)(h * uvScale);
        float phase = (tTicks + vScroll * 200.0f) * 0.005f;

        for (int i = 0; i < sides; i++) {
            double a0 = (2.0 * Math.PI * i) / sides;
            double a1 = (2.0 * Math.PI * (i + 1)) / sides;
            double a0Top = a0 + twist, a1Top = a1 + twist;

            double x0b = x + Math.cos(a0)    * baseR;
            double z0b = z + Math.sin(a0)    * baseR;
            double x1b = x + Math.cos(a1)    * baseR;
            double z1b = z + Math.sin(a1)    * baseR;

            double x0t = x + Math.cos(a0Top) * topR;
            double z0t = z + Math.sin(a0Top) * topR;
            double x1t = x + Math.cos(a1Top) * topR;
            double z1t = z + Math.sin(a1Top) * topR;

            float u0 = (float)i / (float)sides + phase;
            float u1 = (float)(i + 1) / (float)sides + phase;

            t.startDrawingQuads();
            t.setColorRGBA_F(botRGB[0], botRGB[1], botRGB[2], alpha);
            t.addVertexWithUV(x0b, y,     z0b, u0, v2);
            t.addVertexWithUV(x1b, y,     z1b, u1, v2);

            t.setColorRGBA_F(topRGB[0], topRGB[1], topRGB[2], alpha);
            t.addVertexWithUV(x1t, y + h, z1t, u1, v1);
            t.addVertexWithUV(x0t, y + h, z0t, u0, v1);
            t.draw();
        }
    }

    // ===== Chaos extras =====

    // Chaotic: billboard glow at base
    private void renderBaseGlowBillboard(Tessellator t, double x, double y, double z,
                                         double size, float alpha, int rgbHex) {
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        t.setBrightness(240);

        float cr = ((rgbHex>>16)&255)/255f, cg=((rgbHex>>8)&255)/255f, cb=(rgbHex&255)/255f;

        // camera-facing quad
        double s = size;
        t.startDrawingQuads();
        t.setColorRGBA_F(cr, cg, cb, alpha);
        t.addVertexWithUV(x - s, y+0.02, z - s, 0, 1);
        t.addVertexWithUV(x + s, y+0.02, z - s, 1, 1);
        t.addVertexWithUV(x + s, y+0.02, z + s, 1, 0);
        t.addVertexWithUV(x - s, y+0.02, z + s, 0, 0);
        t.draw();
    }

    // Chaotic / Volatile: upward sparks (cheap quads)
    private void renderSparkBurst(Tessellator t, double x, double y, double z, double r, int h,
                                  float timeSec, int count, int rgbHex, float speed) {
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        t.setBrightness(240);

        float cr = ((rgbHex>>16)&255)/255f, cg=((rgbHex>>8)&255)/255f, cb=(rgbHex&255)/255f;

        for (int i=0;i<count;i++){
            double ang = (i*2.399) + (timeSec*0.8) + (i*0.31);
            double rad = r * (0.6 + 0.4*Math.sin(timeSec*0.9 + i));
            double sx  = x + Math.cos(ang) * rad;
            double sz  = z + Math.sin(ang) * rad;
            double sy  = y + Math.abs(Math.sin(timeSec*speed + i))* (h*0.35);

            double s = 0.06 + 0.06*Math.abs(Math.sin(timeSec*1.8 + i));
            float a  = 0.85f;

            t.startDrawingQuads();
            t.setColorRGBA_F(cr, cg, cb, a);
            t.addVertexWithUV(sx - s, sy, sz - s, 0, 1);
            t.addVertexWithUV(sx + s, sy, sz - s, 1, 1);
            t.addVertexWithUV(sx + s, sy, sz + s, 1, 0);
            t.addVertexWithUV(sx - s, sy, sz + s, 0, 0);
            t.draw();
        }
    }

    // Primordial: orbiting rings with moving dots
    private void renderOrbitRingsWithDots(Tessellator t, double x, double y, double z,
                                          double baseR, int h, float timeSec,
                                          int ringCount, int dotsPerRing, int rgbHex) {
        t.setBrightness(240);
        int sides = 24;

        float cr = ((rgbHex>>16)&255)/255f, cg=((rgbHex>>8)&255)/255f, cb=(rgbHex&255)/255f;
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        for (int rIdx=0; rIdx<ringCount; rIdx++){
            double frac = (rIdx + 1) / (double)(ringCount + 1);
            double ry   = y + h * frac;
            double rad  = baseR * (1.05 + 0.25 * Math.sin(timeSec*0.5 + rIdx));

            // ring band
            for (int i = 0; i < sides; i++) {
                double a0 = (2.0*Math.PI*i)/sides, a1 = (2.0*Math.PI*(i+1))/sides;
                double x0 = x + Math.cos(a0) * rad, z0 = z + Math.sin(a0) * rad;
                double x1 = x + Math.cos(a1) * rad, z1 = z + Math.sin(a1) * rad;
                t.startDrawingQuads();
                t.setColorRGBA_F(cr, cg, cb, 0.55f);
                t.addVertexWithUV(x0, ry - 0.015, z0, 0, 1);
                t.addVertexWithUV(x1, ry - 0.015, z1, 1, 1);
                t.addVertexWithUV(x1, ry + 0.015, z1, 1, 0);
                t.addVertexWithUV(x0, ry + 0.015, z0, 0, 0);
                t.draw();
            }
            // moving dots
            for (int d=0; d<dotsPerRing; d++){
                double a = (2.0*Math.PI)*(d/(double)dotsPerRing) + timeSec*0.9 + rIdx*0.6;
                double dx = x + Math.cos(a) * rad;
                double dz = z + Math.sin(a) * rad;
                double s  = 0.05 + 0.03*Math.sin(timeSec*2.2 + d);
                t.startDrawingQuads();
                t.setColorRGBA_F(cr, cg, cb, 0.95f);
                t.addVertexWithUV(dx - s, ry, dz - s, 0, 1);
                t.addVertexWithUV(dx + s, ry, dz - s, 1, 1);
                t.addVertexWithUV(dx + s, ry, dz + s, 1, 0);
                t.addVertexWithUV(dx - s, ry, dz + s, 0, 0);
                t.draw();
            }
        }
    }

    // Ascended: crown halo at top
    private void renderTopHalo(Tessellator t, double cx, double y, double cz,
                               double r, int sides, int rgbHex, float alpha, float timeSec) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(BEAM_TEX);
        t.setBrightness(240);
        float cr = ((rgbHex>>16)&255)/255f, cg=((rgbHex>>8)&255)/255f, cb=(rgbHex&255)/255f;
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        double rad = r * (1.0 + 0.08*Math.sin(timeSec*1.4));
        for (int i=0;i<sides;i++){
            double a0=(2*Math.PI*i)/sides, a1=(2*Math.PI*(i+1))/sides;
            double x0=cx+Math.cos(a0)*rad, z0=cz+Math.sin(a0)*rad;
            double x1=cx+Math.cos(a1)*rad, z1=cz+Math.sin(a1)*rad;

            t.startDrawingQuads();
            t.setColorRGBA_F(cr, cg, cb, alpha);
            t.addVertexWithUV(x0, y - 0.02, z0, 0, 1);
            t.addVertexWithUV(x1, y - 0.02, z1, 1, 1);
            t.addVertexWithUV(x1, y + 0.02, z1, 1, 0);
            t.addVertexWithUV(x0, y + 0.02, z0, 0, 0);
            t.draw();
        }
    }

    // Ascended: soft vertical pulse inside shaft
    private void renderVerticalPulse(Tessellator t, double x, double y, double z,
                                     double r, int h, float timeSec, int rgbHex, float alpha) {
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        t.setBrightness(240);
        float cr = ((rgbHex>>16)&255)/255f, cg=((rgbHex>>8)&255)/255f, cb=(rgbHex&255)/255f;

        // move 2 pulses up the shaft
        for (int k=0;k<2;k++){
            double p = (timeSec*0.4 + k*0.5) % 1.0;
            double y0 = y + p*h - 0.6;
            double y1 = y0 + 1.2;
            double w  = r*0.75;

            t.startDrawingQuads();
            t.setColorRGBA_F(cr, cg, cb, alpha);
            t.addVertexWithUV(x - w, y0, z - w, 0, 1);
            t.addVertexWithUV(x + w, y0, z - w, 1, 1);
            t.addVertexWithUV(x + w, y1, z + w, 1, 0);
            t.addVertexWithUV(x - w, y1, z + w, 0, 0);
            t.draw();
        }
    }

    // existing effervescent helpers (unchanged)
    private void renderRainbowRings(Tessellator t, double x, double y, double z,
                                    double baseR, int h, float timeSec, int ringCount) { /* unchanged */ }
    private void renderRainbowSpiral(Tessellator t, double cx, double y0, double cz,
                                     double r, int h, float timeSec,
                                     int strands, double turns) { /* unchanged */ }

    // gold soft spiral for Ascended
    private void renderSoftSpiral(Tessellator t, double cx, double y0, double cz,
                                  double r, int h, float timeSec,
                                  int strands, double turns, int rgbHex, float alpha) {
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        t.setBrightness(240);
        int segs = Math.max(16, h * 3);
        float width = (float)(r * 0.16);
        double omega = turns * 2.0 * Math.PI;
        float rotSpeed = 0.030f;
        float cr=((rgbHex>>16)&255)/255f, cg=((rgbHex>>8)&255)/255f, cb=(rgbHex&255)/255f;

        int S=Math.max(1,strands);
        for (int s=0;s<S;s++){
            double strandPhase = (2.0 * Math.PI * s) / S;
            for (int i=0;i<segs;i++){
                double f0=i/(double)segs, f1=(i+1)/(double)segs;
                double a0=f0*omega + strandPhase + timeSec*rotSpeed;
                double a1=f1*omega + strandPhase + timeSec*rotSpeed;
                double x0=cx+Math.cos(a0)*r, z0=cz+Math.sin(a0)*r;
                double x1=cx+Math.cos(a1)*r, z1=cz+Math.sin(a1)*r;
                double nx0=-Math.sin(a0), nz0=Math.cos(a0);
                double nx1=-Math.sin(a1), nz1=Math.cos(a1);
                double yy0=y0+f0*h, yy1=y0+f1*h;

                t.startDrawingQuads();
                t.setColorRGBA_F(cr, cg, cb, alpha);
                t.addVertexWithUV(x0 - nx0*width, yy0, z0 - nz0*width, 0, 1);
                t.addVertexWithUV(x0 + nx0*width, yy0, z0 + nz0*width, 1, 1);
                t.addVertexWithUV(x1 + nx1*width, yy1, z1 + nz1*width, 1, 0);
                t.addVertexWithUV(x1 - nx1*width, yy1, z1 - nz1*width, 0, 0);
                t.draw();
            }
        }
    }
}
