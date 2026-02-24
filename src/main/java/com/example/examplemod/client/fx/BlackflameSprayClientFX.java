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
 * Client-only helper for Blackflame Burn spray particles.
 *
 * Uses ShadowFireStitch.BLACKFLAME icon (stitched into the blocks atlas).
 */
@SideOnly(Side.CLIENT)
public final class BlackflameSprayClientFX {

    private static final Random RAND = new Random();

    private BlackflameSprayClientFX() {}

    /** Spawns a one-shot line spray from (sx,sy,sz) to (ex,ey,ez). */
    public static void spawn(double sx, double sy, double sz, double ex, double ey, double ez) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return;

        World w = mc.theWorld;
        if (w == null) return;

        IIcon icon = ShadowFireStitch.BLACKFLAME;
        if (icon == null) icon = ShadowFireStitch.SHADOW_FIRE_0; // fallback (still looks "black flame-ish")

        // How many particles in the spray
        final int count = 42;

        double dx = ex - sx;
        double dy = ey - sy;
        double dz = ez - sz;

        // Normalize for forward velocity
        double len = Math.sqrt(dx*dx + dy*dy + dz*dz);
        if (len < 1e-6) len = 1.0;
        double nx = dx / len;
        double ny = dy / len;
        double nz = dz / len;

        for (int i = 0; i < count; i++) {
            double t = (count <= 1) ? 1.0 : ((double) i / (double) (count - 1));
            // bias towards the front (more particles near the impact)
            double tt = (t * t);

            double px = sx + dx * tt;
            double py = sy + dy * tt;
            double pz = sz + dz * tt;

            // small cone spread
            double spread = 0.10 + 0.18 * tt;
            px += (RAND.nextGaussian() * spread);
            py += (RAND.nextGaussian() * (spread * 0.75));
            pz += (RAND.nextGaussian() * spread);

            // forward + jitter velocity
            double v = 0.08 + RAND.nextDouble() * 0.05;
            double vx = nx * v + (RAND.nextGaussian() * 0.02);
            double vy = ny * v + (RAND.nextGaussian() * 0.02);
            double vz = nz * v + (RAND.nextGaussian() * 0.02);

            BlackflameParticleFX fx = new BlackflameParticleFX(w, px, py, pz, vx, vy, vz, icon);
            try {
                mc.effectRenderer.addEffect(fx);
            } catch (Throwable ignored) {}
        }

        // Small burst at impact
        for (int i = 0; i < 10; i++) {
            double px = ex + (RAND.nextGaussian() * 0.12);
            double py = ey + (RAND.nextGaussian() * 0.12);
            double pz = ez + (RAND.nextGaussian() * 0.12);

            double vx = (RAND.nextGaussian() * 0.03);
            double vy = (RAND.nextGaussian() * 0.03);
            double vz = (RAND.nextGaussian() * 0.03);

            BlackflameParticleFX fx = new BlackflameParticleFX(w, px, py, pz, vx, vy, vz, icon);
            fx.setMaxAge(12 + RAND.nextInt(10));
            fx.mulScale(1.15F);
            try { mc.effectRenderer.addEffect(fx); } catch (Throwable ignored) {}
        }
    }

    /** Single blackflame particle. */
    private static final class BlackflameParticleFX extends EntityFX {

        private final IIcon icon;
        private float startScale;

        public BlackflameParticleFX(World w, double x, double y, double z,
                                    double vx, double vy, double vz,
                                    IIcon icon) {
            super(w, x, y, z, vx, vy, vz);
            this.icon = icon;
            if (this.icon != null) {
                try { this.setParticleIcon(this.icon); } catch (Throwable ignored) {}
            }

            this.noClip = true;
            this.particleGravity = 0.0F;

            this.particleMaxAge = 10 + RAND.nextInt(8);
            this.particleScale = 0.85F + RAND.nextFloat() * 0.55F;
            this.startScale = this.particleScale;

            // Keep tint neutral (icon is already black)
            this.particleRed = 1.0F;
            this.particleGreen = 1.0F;
            this.particleBlue = 1.0F;

            // Slightly softer
            try { this.particleAlpha = 0.90F; } catch (Throwable ignored) {}
        }

        public void setMaxAge(int maxAge) {
            this.particleMaxAge = maxAge;
        }

        public void mulScale(float mul) {
            this.particleScale *= mul;
            this.startScale = this.particleScale;
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

            // drift + slow down
            this.moveEntity(this.motionX, this.motionY, this.motionZ);
            this.motionX *= 0.90D;
            this.motionY *= 0.90D;
            this.motionZ *= 0.90D;

            // gentle rise like flame
            this.motionY += 0.002D;

            // fade + shrink near end
            float life = (float) this.particleAge / (float) this.particleMaxAge;
            if (life > 0.60F) {
                float k = 1.0F - (life - 0.60F) / 0.40F;
                if (k < 0.0F) k = 0.0F;
                try { this.particleAlpha = 0.90F * k; } catch (Throwable ignored) {}
            }
            this.particleScale = this.startScale * (1.0F - 0.35F * life);
        }

        @Override
        public int getFXLayer() {
            // 1 = blocks atlas (IIcon UVs)
            return 1;
        }
    }
}
