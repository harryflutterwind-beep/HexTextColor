package com.example.examplemod.client.fx;

import com.example.examplemod.client.ShadowFireStitch;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import java.util.Random;

/**
 * Simple custom particle that uses the stitched BLACKFLAME icon (blocks atlas).
 *
 * Texture: assets/hexcolorcodes/textures/blocks/blackflame.png (+ optional .mcmeta)
 */
@SideOnly(Side.CLIENT)
public class BlackflameSprayFX extends EntityFX {

    private static final Random R = new Random();

    public BlackflameSprayFX(World w, double x, double y, double z, double vx, double vy, double vz) {
        super(w, x, y, z, vx, vy, vz);

        // Use our icon if available
        IIcon icon = ShadowFireStitch.BLACKFLAME;
        if (icon != null) {
            this.setParticleIcon(icon);
        }

        // Tuning (small fast puffs)
        this.particleMaxAge = 10 + R.nextInt(8);
        this.particleScale = 0.55F + (R.nextFloat() * 0.45F);

        // Dark flames should feel "smoky" and slightly floaty
        this.particleGravity = 0.0F;

        // Keep full color (texture is already dark). You can tint slightly purple if you want.
        this.particleRed = 1.0F;
        this.particleGreen = 1.0F;
        this.particleBlue = 1.0F;
        this.particleAlpha = 0.92F;

        // No collision so it doesn't get stuck on blocks mid-spray
        this.noClip = true;
    }

    @Override
    public int getFXLayer() {
        // 1 = blocks/terrain atlas (where our stitched icon lives)
        return 1;
    }

    @Override
    public void onUpdate() {
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;

        if (this.particleAge++ >= this.particleMaxAge) {
            this.setDead();
            return;
        }

        // Fade out
        this.particleAlpha *= 0.92F;

        // Mild slowdown (smoky)
        this.motionX *= 0.88D;
        this.motionY *= 0.88D;
        this.motionZ *= 0.88D;

        // Slight upward drift
        this.motionY += 0.0025D;

        this.moveEntity(this.motionX, this.motionY, this.motionZ);
    }

    // --------------------------------------------------------------------
    // Spawners
    // --------------------------------------------------------------------

    /** Spawns a blackflame "spray" from start to end, plus an impact burst at the end. */
    public static void spawnLine(double sx, double sy, double sz, double ex, double ey, double ez) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null) return;

            World w = mc.theWorld;
            if (w == null) return;

            double dx = ex - sx;
            double dy = ey - sy;
            double dz = ez - sz;

            double len = Math.sqrt(dx*dx + dy*dy + dz*dz);
            if (len < 0.001D) return;

            double nx = dx / len;
            double ny = dy / len;
            double nz = dz / len;

            // Spray density scales with distance
            int steps = (int) Math.max(10, Math.min(42, len * 2.2D));

            for (int i = 0; i <= steps; i++) {
                double t = (double) i / (double) steps;

                // Position along ray with tiny jitter
                double px = sx + dx * t + (R.nextDouble() - 0.5D) * 0.16D;
                double py = sy + dy * t + (R.nextDouble() - 0.5D) * 0.12D;
                double pz = sz + dz * t + (R.nextDouble() - 0.5D) * 0.16D;

                // Forward velocity with some sideways spread
                double spread = 0.035D + (R.nextDouble() * 0.03D);
                double vx = nx * (0.10D + R.nextDouble() * 0.05D) + (R.nextDouble() - 0.5D) * spread;
                double vy = ny * (0.08D + R.nextDouble() * 0.04D) + (R.nextDouble() - 0.5D) * (spread * 0.65D);
                double vz = nz * (0.10D + R.nextDouble() * 0.05D) + (R.nextDouble() - 0.5D) * spread;

                mc.effectRenderer.addEffect(new BlackflameSprayFX(w, px, py, pz, vx, vy, vz));
            }

            // Impact burst at end
            int burst = 14 + R.nextInt(10);
            for (int i = 0; i < burst; i++) {
                double px = ex + (R.nextDouble() - 0.5D) * 0.25D;
                double py = ey + (R.nextDouble() - 0.5D) * 0.20D;
                double pz = ez + (R.nextDouble() - 0.5D) * 0.25D;

                double vx = (R.nextDouble() - 0.5D) * 0.18D;
                double vy = (R.nextDouble() - 0.5D) * 0.12D + 0.02D;
                double vz = (R.nextDouble() - 0.5D) * 0.18D;

                BlackflameSprayFX fx = new BlackflameSprayFX(w, px, py, pz, vx, vy, vz);
                fx.particleScale *= 1.15F;
                mc.effectRenderer.addEffect(fx);
            }
        } catch (Throwable ignored) {}
    }
}
