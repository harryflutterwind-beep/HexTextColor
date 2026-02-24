// src/main/java/com/example/examplemod/client/render/RenderHexBlast.java
package com.example.examplemod.client.render;

import com.example.examplemod.entity.EntityHexBlast;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.Vec3;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public class RenderHexBlast extends Render {

    // Soft circular sprite (works for both sphere + beam glow)
    private static final ResourceLocation TEX =
            new ResourceLocation("customnpcs", "textures/items/npcHolySpell.png");

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return TEX;
    }

    @Override
    public void doRender(Entity entity, double x, double y, double z, float yaw, float partialTicks) {
        if (!(entity instanceof EntityHexBlast)) return;
        EntityHexBlast b = (EntityHexBlast) entity;

        float age = b.getAge() + partialTicks;
        float life = Math.max(1f, b.getLife());
        float time = age / life;

        float alpha = 1.0f - time;
        alpha = alpha * alpha;

        // Multi-color palette
        int c0 = b.getColor();
        int c1 = b.getColor1();
        int c2 = b.getColor2();
        int c3 = b.getColor3();
        byte mode = b.getColorMode();

        bindEntityTexture(b);

        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDepthMask(false);

        // Beam path (kame core + grow)
        if (b.isBeam()) {
            renderBeam(b, partialTicks, age, time, alpha, c0, c1, c2, c3, mode);
            // restore
            GL11.glDepthMask(true);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_LIGHTING);
            GL11.glPopMatrix();
            return;
        }


        // Projectile path (small moving orb / ki blast)
        if (b.isProjectile()) {
            renderProjectile(b, partialTicks, age, time, alpha, c0, c1, c2, c3, mode);
            // restore
            GL11.glDepthMask(true);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_LIGHTING);
            GL11.glPopMatrix();
            return;
        }

// Sphere path (your existing spherical blast)
        float eased = 1f - (1f - time) * (1f - time);
        float radius = b.getMaxRadius() * eased;

        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        // slight spin
        GL11.glRotatef(age * 10f, 0f, 1f, 0f);

        // Pass A: color-preserving shell
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        drawSphericalShell(radius, mode, time, c0, c1, c2, c3, alpha, age);

        // Pass B: additive glow overlay
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        int glowCol = (mode == 0) ? c0 : gradient4(c0, c1, c2, c3, time);
        drawGlow(radius * 0.85f, glowCol, alpha * 0.35f);

        // restore
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glPopMatrix();
    }

    // =============================================================================
    // BEAM RENDER
    // =============================================================================

    private void renderBeam(EntityHexBlast b, float partialTicks, float age, float time, float alpha,
                            int c0, int c1, int c2, int c3, byte mode) {

        // Beam growth timing matches server logic
        float t = (b.getAge() + partialTicks) / Math.max(1f, b.getLife());

        float charge = EntityHexBlast.BEAM_CHARGE_FRAC;
        float grow   = EntityHexBlast.BEAM_GROW_FRAC;

        float growT = (t - charge) / Math.max(0.0001f, grow);
        if (growT < 0f) growT = 0f;
        if (growT > 1f) growT = 1f;

        float lenFull = b.getBeamRange();
        float len = lenFull * growT;

        float hw = Math.max(0.05f, b.getBeamWidth() * 0.5f);

        // Direction basis (prefer owner's look for perfect crosshair alignment)
        float dx = b.getDirX();
        float dy = b.getDirY();
        float dz = b.getDirZ();

        try {
            int oid = b.getOwnerId();
            if (oid != -1) {
                Entity o = b.worldObj.getEntityByID(oid);
                if (o instanceof EntityLivingBase) {
                    Vec3 look = ((EntityLivingBase) o).getLook(partialTicks);
                    if (look != null) {
                        dx = (float) look.xCoord;
                        dy = (float) look.yCoord;
                        dz = (float) look.zCoord;
                        float dlen = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (dlen > 0.0001f) { dx /= dlen; dy /= dlen; dz /= dlen; }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // Build orthonormal basis: right/up around dir
        float upx = 0f, upy = 1f, upz = 0f;
        // right = dir x upRef
        float rx = dy * upz - dz * upy;
        float ry = dz * upx - dx * upz;
        float rz = dx * upy - dy * upx;
        float rlen = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (rlen < 0.0001f) {
            // dir near vertical, use different up
            upx = 1f; upy = 0f; upz = 0f;
            rx = dy * upz - dz * upy;
            ry = dz * upx - dx * upz;
            rz = dx * upy - dy * upx;
            rlen = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        }
        rx /= rlen; ry /= rlen; rz /= rlen;

        // up = right x dir
        float ux = ry * dz - rz * dy;
        float uy = rz * dx - rx * dz;
        float uz = rx * dy - ry * dx;

        // pulse
        float pulse = 0.92f + 0.08f * (float) Math.sin(age * 0.35f);

        // --- Core ball (always visible; bigger during charge) ---
        float coreBoost = (growT <= 0.001f) ? 1.25f : 1.0f;
        float coreR = Math.max(hw * 2.35f, 0.22f) * pulse * coreBoost;

        // slight spin
        GL11.glPushMatrix();
        GL11.glRotatef(age * 10f, 0f, 1f, 0f);

        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        drawSphericalShell(coreR, mode, time, c0, c1, c2, c3, alpha, age);

        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        int glowCol = (mode == 0) ? c0 : gradient4(c0, c1, c2, c3, time);
        drawGlow(coreR * 0.88f, glowCol, alpha * 0.28f);

        GL11.glPopMatrix();

        // --- Throat / flare ---
        // During charge: small flare; during grow/fire: lengthen a bit
        float throatLen = (1.35f + 2.25f * growT) * pulse;
        float throatW   = hw * (1.85f + 0.55f * (1f - growT));

        // Pass A (color)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        drawBeamPlanes(dx, dy, dz, rx, ry, rz, ux, uy, uz, throatLen, throatW, mode, time, c0, c1, c2, c3, alpha * 0.85f);

        // Pass B (glow)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        drawBeamPlanes(dx, dy, dz, rx, ry, rz, ux, uy, uz, throatLen, throatW * 1.18f, mode, time, c0, c1, c2, c3, alpha * 0.18f);

        // --- Main beam (only after charge) ---
        if (len > 0.01f) {
            // Pass A
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            drawBeamPlanes(dx, dy, dz, rx, ry, rz, ux, uy, uz, len, hw, mode, time, c0, c1, c2, c3, alpha);

            // Pass B glow (slightly wider, dimmer)
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            drawBeamPlanes(dx, dy, dz, rx, ry, rz, ux, uy, uz, len, hw * 1.22f, mode, time, c0, c1, c2, c3, alpha * 0.22f);
        }
    }

    private void drawBeamPlanes(float dx, float dy, float dz,
                                float rx, float ry, float rz,
                                float ux, float uy, float uz,
                                float len, float halfWidth,
                                byte mode, float time,
                                int c0, int c1, int c2, int c3,
                                float alpha) {

        int planes = 6; // more = rounder tube
        if (planes < 2) planes = 2;

        Tessellator t = Tessellator.instance;

        for (int i = 0; i < planes; i++) {
            float ang = (float) (Math.PI * i / planes);
            float ca = (float) Math.cos(ang);
            float sa = (float) Math.sin(ang);

            float ox = (rx * ca + ux * sa) * halfWidth;
            float oy = (ry * ca + uy * sa) * halfWidth;
            float oz = (rz * ca + uz * sa) * halfWidth;

            // pick slice color
            int col;
            if (mode == 2) {
                int pick = i & 3;
                col = (pick == 0) ? c0 : (pick == 1) ? c1 : (pick == 2) ? c2 : c3;
            } else if (mode == 1) {
                col = gradient4(c0, c1, c2, c3, time);
            } else {
                col = c0;
            }

            float r = ((col >> 16) & 255) / 255f;
            float g = ((col >> 8) & 255) / 255f;
            float b = (col & 255) / 255f;

            // end point
            float ex = dx * len;
            float ey = dy * len;
            float ez = dz * len;

            // alpha falls slightly along length for nicer taper
            float a0 = alpha;
            float a1 = alpha * 0.82f;

            t.startDrawingQuads();
            t.setColorRGBA_F(r, g, b, a0);
            t.addVertexWithUV( ox,  oy,  oz, 0, 1);
            t.addVertexWithUV(-ox, -oy, -oz, 1, 1);

            t.setColorRGBA_F(r, g, b, a1);
            t.addVertexWithUV(-ox + ex, -oy + ey, -oz + ez, 1, 0);
            t.addVertexWithUV( ox + ex,  oy + ey,  oz + ez, 0, 0);
            t.draw();
        }
    }

    // =============================================================================

    // =============================================================================
    // PROJECTILE RENDER (small orb / ki blast)
    // =============================================================================

    private void renderProjectile(EntityHexBlast b, float partialTicks, float age, float time, float alpha,
                                  int c0, int c1, int c2, int c3, byte mode) {

        float r = Math.max(0.08f, b.getProjectileRadius());

        // Looping phase so the projectile "shimmers" even for long lifetimes
        float phase = (age * 0.07f) % 1.0f;

        int shellCol = (mode == 0) ? c0 : gradient4(c0, c1, c2, c3, phase);

        // color-preserving shell
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        drawSphericalShell(r, mode, phase, c0, c1, c2, c3, Math.min(1.0f, alpha * 0.90f), age);

        // additive glow core
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        drawGlow(r * 1.55f, shellCol, alpha * 0.60f);

        // small tail glows along -dir so it reads as a moving ki blast
        float dx = b.getDirX(), dy = b.getDirY(), dz = b.getDirZ();
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 0.0001f) { dx /= len; dy /= len; dz /= len; } else { dx = 0f; dy = 0f; dz = 1f; }

        for (int i = 1; i <= 3; i++) {
            float dist = r * (1.4f + i * 1.2f);
            GL11.glPushMatrix();
            GL11.glTranslatef(-dx * dist, -dy * dist, -dz * dist);
            drawGlow(r * (1.05f - 0.20f * i), shellCol, alpha * (0.20f * (1.0f - 0.18f * i)));
            GL11.glPopMatrix();
        }
    }

// SPHERE RENDER HELPERS (existing)
    // =============================================================================

    private void drawSphericalShell(float radius, byte mode, float time, int c0, int c1, int c2, int c3, float alpha, float age) {
        drawRingShell(radius, 12, mode, time, c0, c1, c2, c3, alpha);

        GL11.glPushMatrix();
        GL11.glRotatef(90f, 1f, 0f, 0f);
        drawRingShell(radius, 12, mode, time, c0, c1, c2, c3, alpha);
        GL11.glPopMatrix();

        GL11.glPushMatrix();
        GL11.glRotatef(90f, 0f, 0f, 1f);
        drawRingShell(radius, 12, mode, time, c0, c1, c2, c3, alpha);
        GL11.glPopMatrix();

        float tilt = 35f;
        float spin = age * 6f;

        GL11.glPushMatrix();
        GL11.glRotatef(spin, 0f, 1f, 0f);
        GL11.glRotatef(tilt, 1f, 0f, 0f);
        drawRingShell(radius * 0.92f, 10, mode, time, c0, c1, c2, c3, alpha);
        GL11.glPopMatrix();

        GL11.glPushMatrix();
        GL11.glRotatef(spin + 90f, 0f, 1f, 0f);
        GL11.glRotatef(-tilt, 0f, 0f, 1f);
        drawRingShell(radius * 0.92f, 10, mode, time, c0, c1, c2, c3, alpha);
        GL11.glPopMatrix();
    }

    private void drawRingShell(float radius, int slices, byte mode, float time, int c0, int c1, int c2, int c3, float alpha) {
        if (slices < 3) slices = 3;
        float step = 360f / slices;

        int gradCol = (mode == 1) ? gradient4(c0, c1, c2, c3, time) : c0;

        for (int i = 0; i < slices; i++) {
            GL11.glPushMatrix();
            GL11.glRotatef(i * step, 0f, 1f, 0f);

            int col;
            if (mode == 2) {
                int pick = i & 3;
                col = (pick == 0) ? c0 : (pick == 1) ? c1 : (pick == 2) ? c2 : c3;
            } else {
                col = gradCol;
            }

            applyGLColor(col, alpha);
            drawSingleQuad(radius);
            GL11.glPopMatrix();
        }
    }

    private void drawGlow(float radius, int col, float alpha) {
        applyGLColor(col, alpha);

        drawRingShell(radius, 10, (byte) 0, 0f, col, col, col, col, alpha);

        GL11.glPushMatrix();
        GL11.glRotatef(90f, 1f, 0f, 0f);
        drawSingleQuad(radius);
        GL11.glPopMatrix();

        GL11.glPushMatrix();
        GL11.glRotatef(90f, 0f, 0f, 1f);
        drawSingleQuad(radius);
        GL11.glPopMatrix();
    }

    private void drawSingleQuad(float radius) {
        float s = radius;
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertexWithUV(-s, -s, 0, 0, 1);
        t.addVertexWithUV( s, -s, 0, 1, 1);
        t.addVertexWithUV( s,  s, 0, 1, 0);
        t.addVertexWithUV(-s,  s, 0, 0, 0);
        t.draw();
    }

    private void applyGLColor(int col, float alpha) {
        float r = ((col >> 16) & 255) / 255f;
        float g = ((col >> 8) & 255) / 255f;
        float b = (col & 255) / 255f;
        GL11.glColor4f(r, g, b, alpha);
    }

    private int lerpColor(int a, int b, float t) {
        t = clamp01(t);
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;

        int rr = ar + (int) ((br - ar) * t);
        int rg = ag + (int) ((bg - ag) * t);
        int rb = ab + (int) ((bb - ab) * t);

        return (rr << 16) | (rg << 8) | rb;
    }

    private int gradient4(int c0, int c1, int c2, int c3, float t) {
        t = clamp01(t);
        if (t <= 0.33333334f) return lerpColor(c0, c1, t / 0.33333334f);
        if (t <= 0.6666667f) return lerpColor(c1, c2, (t - 0.33333334f) / 0.33333334f);
        return lerpColor(c2, c3, (t - 0.6666667f) / 0.33333334f);
    }

    private float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }
}
