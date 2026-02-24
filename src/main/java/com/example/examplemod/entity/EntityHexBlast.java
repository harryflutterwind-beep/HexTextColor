// src/main/java/com/example/examplemod/entity/EntityHexBlast.java
package com.example.examplemod.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import java.util.List;

/**
 * HexBlast supports:
 *  - Shape 0: expanding spherical blast (your original blast)
 *  - Shape 1: beam (kamehameha-style) with charge + grow
 *  - Shape 2: ki projectile (small moving orb)
 *
 * Colors support:
 *  - mode 0: solid c0
 *  - mode 1: gradient over time c0->c1->c2->c3
 *  - mode 2: rainbow slices (renderer picks palette per slice)
 *
 * Damage:
 *  - Will scaling (DBC aware if possible) with vanilla fallback
 */
public class EntityHexBlast extends Entity {

    // Shapes
    public static final byte SHAPE_SPHERE = 0;
    public static final byte SHAPE_BEAM   = 1;
    public static final byte SHAPE_PROJECTILE = 2;

    // Beam timing (fractions of life)
    public static final float BEAM_CHARGE_FRAC = 0.35f; // charge-only (core + flare)
    public static final float BEAM_GROW_FRAC   = 0.20f; // grow to full length after charge

    // lifetime + visuals (sphere)
    public int lifeTicks = 14;
    public float maxRadius = 4.5f;

    // palette
    public int color  = 0xAA66FF;   // c0
    public int color1 = 0x00D4FF;   // c1
    public int color2 = 0xFFFFFF;   // c2
    public int color3 = 0xFFB84D;   // c3
    public byte colorMode = 1;      // 0 solid, 1 gradient, 2 rainbow slices

    // owner + direction
    public int ownerId = -1;
    public float dirX = 0f, dirY = 0f, dirZ = 1f;

    // beam params
    public byte shape = SHAPE_SPHERE;
    public float beamRange = 24.0f;
    public float beamWidth = 0.9f;
    public int beamDamageInterval = 3; // ticks between beam damage pulses

    // projectile params (Shape 2: ki blast)
    public float projSpeed = 1.35f;    // blocks per tick
    public float projRadius = 0.45f;   // visual size (radius)
    private double projStartX, projStartY, projStartZ;
    private boolean projStarted = false;

    // damage controls
    public boolean doDamage = false;
    public boolean hitOnce = true;   // sphere: hit once; beam: false (ticks)
    public float maxDamage = 0f;     // optional cap/override (0 = use will scaling)
    public float willDamageMult = 8.0f;
    public float willDamageFlat = 200.0f;

    private int age = 0;
    private boolean damageApplied = false;

    public EntityHexBlast(World world) {
        super(world);
        this.noClip = true;
        this.ignoreFrustumCheck = true;
        this.setSize(0.1f, 0.1f);
    }

    public EntityHexBlast(World world, EntityPlayer owner, double x, double y, double z) {
        this(world);
        this.ownerId = owner != null ? owner.getEntityId() : -1;
        this.setPosition(x, y, z);

        // lock direction at spawn
        if (owner != null) {
            Vec3 look = owner.getLookVec();
            setDirection((float) look.xCoord, (float) look.yCoord, (float) look.zCoord);
        }
    }

    public void setDirection(float x, float y, float z) {
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        if (len < 0.0001f) {
            dirX = 0f; dirY = 0f; dirZ = 1f;
        } else {
            dirX = x / len;
            dirY = y / len;
            dirZ = z / len;
        }

        // also set rotation fields (helps some clients)
        float yaw = (float) (Math.atan2(dirX, dirZ) * 180.0 / Math.PI);
        float pitch = (float) (-Math.atan2(dirY, Math.sqrt(dirX * dirX + dirZ * dirZ)) * 180.0 / Math.PI);
        this.rotationYaw = yaw;
        this.rotationPitch = pitch;
    }

    public void setProjectileParams(float speed, float radius) {
        this.projSpeed = speed;
        this.projRadius = radius;
        this.setSize(0.1f, 0.1f);
    }


    @Override
    protected void entityInit() {
        // DataWatcher slots (must be <= 31 in 1.7.10):
        // 20 life, 21 maxRadius
        // 22 c0, 23 c1, 24 c2, 25 c3
        // 26 colorMode(byte), 27 shape(byte)
        // 28 beamRange, 29 beamWidth
        this.dataWatcher.addObject(19, Integer.valueOf(ownerId)); // owner id for client aiming
        this.dataWatcher.addObject(20, Integer.valueOf(lifeTicks));
        this.dataWatcher.addObject(21, Float.valueOf(maxRadius));
        this.dataWatcher.addObject(22, Integer.valueOf(color));
        this.dataWatcher.addObject(23, Integer.valueOf(color1));
        this.dataWatcher.addObject(24, Integer.valueOf(color2));
        this.dataWatcher.addObject(25, Integer.valueOf(color3));
        this.dataWatcher.addObject(26, Byte.valueOf(colorMode));
        this.dataWatcher.addObject(27, Byte.valueOf(shape));
        this.dataWatcher.addObject(28, Float.valueOf(beamRange));
        this.dataWatcher.addObject(29, Float.valueOf(beamWidth));
    }

    public void syncToClients() {
        this.dataWatcher.updateObject(19, Integer.valueOf(ownerId));
        this.dataWatcher.updateObject(20, Integer.valueOf(lifeTicks));
        this.dataWatcher.updateObject(21, Float.valueOf(maxRadius));
        this.dataWatcher.updateObject(22, Integer.valueOf(color));
        this.dataWatcher.updateObject(23, Integer.valueOf(color1));
        this.dataWatcher.updateObject(24, Integer.valueOf(color2));
        this.dataWatcher.updateObject(25, Integer.valueOf(color3));
        this.dataWatcher.updateObject(26, Byte.valueOf(colorMode));
        this.dataWatcher.updateObject(27, Byte.valueOf(shape));

        // IMPORTANT: We reuse watcher 28/29 for both BEAM + PROJECTILE to avoid watcher-id/type drift.
        //  - If shape == BEAM:    28 = beamRange,  29 = beamWidth
        //  - If shape == PROJECTILE: 28 = projSpeed, 29 = projRadius
        float p0 = (shape == SHAPE_PROJECTILE) ? projSpeed : beamRange;
        float p1 = (shape == SHAPE_PROJECTILE) ? projRadius : beamWidth;
        this.dataWatcher.updateObject(28, Float.valueOf(p0));
        this.dataWatcher.updateObject(29, Float.valueOf(p1));
    }

    public int getAge() { return age; }

    public int getLife() { return this.dataWatcher.getWatchableObjectInt(20); }
    public float getMaxRadius() { return this.dataWatcher.getWatchableObjectFloat(21); }

    public int getColor() { return this.dataWatcher.getWatchableObjectInt(22); }
    public int getColor1() { return this.dataWatcher.getWatchableObjectInt(23); }
    public int getColor2() { return this.dataWatcher.getWatchableObjectInt(24); }
    public int getColor3() { return this.dataWatcher.getWatchableObjectInt(25); }
    public byte getColorMode() { return (byte) (this.dataWatcher.getWatchableObjectByte(26) & 0xFF); }

    public byte getShape() { return (byte) (this.dataWatcher.getWatchableObjectByte(27) & 0xFF); }
    public boolean isBeam() { return getShape() == SHAPE_BEAM; }

    public float getBeamRange() {
        return isBeam() ? this.dataWatcher.getWatchableObjectFloat(28) : beamRange;
    }
    public float getBeamWidth() {
        return isBeam() ? this.dataWatcher.getWatchableObjectFloat(29) : beamWidth;
    }

    public float getProjectileSpeed() {
        return isProjectile() ? this.dataWatcher.getWatchableObjectFloat(28) : projSpeed;
    }
    public float getProjectileRadius() {
        return isProjectile() ? this.dataWatcher.getWatchableObjectFloat(29) : projRadius;
    }

    public boolean isProjectile() { return getShape() == SHAPE_PROJECTILE; }


    public float getDirX() { return dirX; }
    public float getDirY() { return dirY; }
    public float getDirZ() { return dirZ; }

    public int getOwnerId() {
        // Owner id must be synced to clients for beam aiming.
        if (this.worldObj != null && this.worldObj.isRemote) {
            try {
                return this.dataWatcher.getWatchableObjectInt(19);
            } catch (Throwable t) {
                return ownerId;
            }
        }
        return ownerId;
    }


    @Override
    public void onUpdate() {
        super.onUpdate();

        // server: movement / damage tick
        if (!worldObj.isRemote) {
            if (isProjectile()) {
                tickProjectile();
            } else if (doDamage) {
                if (!isBeam()) {
                    if (hitOnce) {
                        if (!damageApplied && age >= 2) {
                            applySphereDamage();
                            damageApplied = true;
                        }
                    } else {
                        if (age % 2 == 0) applySphereDamage();
                    }
                } else {
                    // beam: damage pulses (beam length is 0 during charge; applyBeamDamage() will early-out)
                    if (age % Math.max(1, beamDamageInterval) == 0) {
                        applyBeamDamage();
                    }
                }
            }
        }



        age++;
        // Client-side: keep direction coherent from rotation (we no longer sync dir via DataWatcher)
        if (this.worldObj != null && this.worldObj.isRemote) {
            // Vanilla look vector math (matches EntityLivingBase#getLook)
            float yaw = this.rotationYaw;
            float pitch = this.rotationPitch;
            float f = net.minecraft.util.MathHelper.cos(-yaw * 0.017453292F - (float)Math.PI);
            float f1 = net.minecraft.util.MathHelper.sin(-yaw * 0.017453292F - (float)Math.PI);
            float f2 = -net.minecraft.util.MathHelper.cos(-pitch * 0.017453292F);
            float f3 = net.minecraft.util.MathHelper.sin(-pitch * 0.017453292F);
            this.dirX = (f1 * f2);
            this.dirY = (f3);
            this.dirZ = (f * f2);
            float len = (float)Math.sqrt(this.dirX*this.dirX + this.dirY*this.dirY + this.dirZ*this.dirZ);
            if (len > 0.0001f) { this.dirX /= len; this.dirY /= len; this.dirZ /= len; }
        }

        if (age >= getLife()) setDead();
    }

    // Sphere visual radius easing
    public float currentRadius() {
        float t = (getLife() <= 0) ? 1f : (age / (float) getLife());
        float eased = 1f - (1f - t) * (1f - t);
        return getMaxRadius() * eased;
    }

    // Beam current length (server-side, no partial ticks)
    public float currentBeamLength() {
        float life = Math.max(1f, getLife());
        float t = age / life;

        float start = BEAM_CHARGE_FRAC;
        float grow  = BEAM_GROW_FRAC;

        if (t <= start) return 0f;
        float g = (t - start) / Math.max(0.0001f, grow);
        if (g < 0f) g = 0f;
        if (g > 1f) g = 1f;
        return getBeamRange() * g;
    }


    // -------------------------------------------------------------------------
    // Projectile tick (Shape 2: straight ki blast)
    // Moves forward along locked direction, raytraces blocks, checks entities
    // along the swept segment, and applies a small impact splash.
    // -------------------------------------------------------------------------
    private void tickProjectile() {
        // initialize starting point for range checks
        if (!projStarted) {
            projStarted = true;
            projStartX = posX;
            projStartY = posY;
            projStartZ = posZ;
        }

        final float speed = Math.max(0.05f, getProjectileSpeed());
        final float visR  = Math.max(0.05f, getProjectileRadius());

        // locked direction (set at spawn); normalize defensively
        float dx = dirX, dy = dirY, dz = dirZ;
        float dlen = (float)Math.sqrt(dx*dx + dy*dy + dz*dz);
        if (dlen < 0.0001f) {
            dx = 0f; dy = 0f; dz = 1f;
        } else {
            dx /= dlen; dy /= dlen; dz /= dlen;
        }

        final double x0 = posX, y0 = posY, z0 = posZ;
        final double x1 = x0 + dx * speed;
        final double y1 = y0 + dy * speed;
        final double z1 = z0 + dz * speed;

        // --- Block raytrace ---
        try {
            Vec3 start = Vec3.createVectorHelper(x0, y0, z0);
            Vec3 end   = Vec3.createVectorHelper(x1, y1, z1);
            MovingObjectPosition mop = worldObj.rayTraceBlocks(start, end);
            if (mop != null && mop.hitVec != null) {
                setPosition(mop.hitVec.xCoord, mop.hitVec.yCoord, mop.hitVec.zCoord);
                if (doDamage) applyProjectileImpactDamage(null, visR);
                setDead();
                return;
            }
        } catch (Throwable ignored) {}

        // --- Entity sweep check ---
        EntityLivingBase directHit = null;
        double bestT = Double.MAX_VALUE;

        try {
            AxisAlignedBB swept = this.boundingBox
                    .addCoord(dx * speed, dy * speed, dz * speed)
                    .expand(visR, visR, visR);

            @SuppressWarnings("unchecked")
            List<EntityLivingBase> list = worldObj.getEntitiesWithinAABB(EntityLivingBase.class, swept);

            for (int i = 0; i < list.size(); i++) {
                EntityLivingBase e = list.get(i);
                if (e == null || e.isDead) continue;
                if (ownerId != -1 && e.getEntityId() == ownerId) continue;

                // center point of entity (approx)
                double px = e.posX;
                double py = e.posY + e.getEyeHeight() * 0.5;
                double pz = e.posZ;

                // Compute closest approach along segment, and distance at that point
                SegmentHit h = closestPointOnSegment(px, py, pz, x0, y0, z0, x1, y1, z1);
                double hitRadius = visR + Math.max(0.25, e.width * 0.5);

                if (h != null && h.distSq <= hitRadius * hitRadius) {
                    // choose earliest along the segment (smallest t)
                    if (h.t < bestT) {
                        bestT = h.t;
                        directHit = e;
                    }
                }
            }
        } catch (Throwable ignored) {}

        if (directHit != null) {
            // Move to impact point roughly at the entity center for splash calculation
            setPosition(directHit.posX, directHit.posY + directHit.getEyeHeight() * 0.5, directHit.posZ);
            if (doDamage) applyProjectileImpactDamage(directHit, visR);
            setDead();
            return;
        }

        // advance
        setPosition(x1, y1, z1);

        // range cap uses beamRange as projectile range for simplicity
        float maxRange = getBeamRange();
        if (maxRange > 0.5f) {
            double rx = posX - projStartX;
            double ry = posY - projStartY;
            double rz = posZ - projStartZ;
            if ((rx*rx + ry*ry + rz*rz) >= (double)maxRange * (double)maxRange) {
                setDead();
            }
        }
    }

    private void applyProjectileImpactDamage(EntityLivingBase direct, float visR) {
        // Small splash radius; tuned to feel like a ki blast (mostly direct-hit)
        float splash = Math.max(0.75f, visR * 1.65f);
        float base = computeDamage();

        AxisAlignedBB bb = AxisAlignedBB.getBoundingBox(
                posX - splash, posY - splash, posZ - splash,
                posX + splash, posY + splash, posZ + splash
        );

        @SuppressWarnings("unchecked")
        List<EntityLivingBase> list = worldObj.getEntitiesWithinAABB(EntityLivingBase.class, bb);

        for (int i = 0; i < list.size(); i++) {
            EntityLivingBase e = list.get(i);
            if (e == null || e.isDead) continue;
            if (ownerId != -1 && e.getEntityId() == ownerId) continue;

            double dx = e.posX - posX;
            double dy = (e.posY + e.getEyeHeight() * 0.5) - posY;
            double dz = e.posZ - posZ;
            double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
            if (dist > splash || dist <= 0.0001) continue;

            float fall = 1.0f - (float)(dist / splash);
            float mult = (direct != null && e == direct) ? 1.0f : 0.55f;
            float dmg = base * mult * fall;

            applyDamageToEntity(e, dmg);
        }
    }

    // Helper struct for segment queries (avoids extra allocations)
    private static final class SegmentHit {
        final double t;      // 0..1 along segment
        final double distSq; // squared distance from point to segment
        SegmentHit(double t, double distSq) { this.t = t; this.distSq = distSq; }
    }

    private static SegmentHit closestPointOnSegment(
            double px, double py, double pz,
            double ax, double ay, double az,
            double bx, double by, double bz
    ) {
        double abx = bx - ax;
        double aby = by - ay;
        double abz = bz - az;

        double apx = px - ax;
        double apy = py - ay;
        double apz = pz - az;

        double abLenSq = abx*abx + aby*aby + abz*abz;
        if (abLenSq < 1.0e-9) {
            double dx = px - ax, dy = py - ay, dz = pz - az;
            return new SegmentHit(0.0, dx*dx + dy*dy + dz*dz);
        }

        double t = (apx*abx + apy*aby + apz*abz) / abLenSq;
        if (t < 0.0) t = 0.0;
        else if (t > 1.0) t = 1.0;

        double cx = ax + abx * t;
        double cy = ay + aby * t;
        double cz = az + abz * t;

        double dx = px - cx;
        double dy = py - cy;
        double dz = pz - cz;

        return new SegmentHit(t, dx*dx + dy*dy + dz*dz);
    }

    private void applySphereDamage() {
        float r = currentRadius();
        AxisAlignedBB bb = AxisAlignedBB.getBoundingBox(posX - r, posY - r, posZ - r, posX + r, posY + r, posZ + r);

        java.util.List list = worldObj.getEntitiesWithinAABB(EntityLivingBase.class, bb);
        for (int i = 0; i < list.size(); i++) {
            EntityLivingBase e = (EntityLivingBase) list.get(i);
            if (e == null || e.isDead) continue;
            if (ownerId != -1 && e.getEntityId() == ownerId) continue;

            double dx = e.posX - posX;
            double dy = (e.posY + e.getEyeHeight() * 0.5) - posY;
            double dz = e.posZ - posZ;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > r || dist <= 0.001) continue;

            float falloff = 1.0f - (float) (dist / r);
            float dmg = computeDamage() * falloff;

            applyDamageToEntity(e, dmg);
        }
    }

    private void applyBeamDamage() {
        float len = currentBeamLength();
        if (len < 0.5f) return; // still charging / too short

        float hw = Math.max(0.05f, getBeamWidth() * 0.5f);

        // Direction: prefer owner's current look vector (always matches crosshair) when available.
        float dx = getDirX();
        float dy = getDirY();
        float dz = getDirZ();
        try {
            Entity o = (ownerId != -1) ? worldObj.getEntityByID(ownerId) : null;
            if (o instanceof EntityLivingBase) {
                Vec3 look = ((EntityLivingBase) o).getLookVec();
                if (look != null) {
                    dx = (float) look.xCoord;
                    dy = (float) look.yCoord;
                    dz = (float) look.zCoord;
                    float dlen = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (dlen > 0.0001f) { dx /= dlen; dy /= dlen; dz /= dlen; }
                }
            }
        } catch (Throwable ignored) {}

        // bounding box around the segment (cheap filter)
        double x0 = posX, y0 = posY, z0 = posZ;
        double x1 = posX + dx * len;
        double y1 = posY + dy * len;
        double z1 = posZ + dz * len;

        double minX = Math.min(x0, x1) - hw;
        double minY = Math.min(y0, y1) - hw;
        double minZ = Math.min(z0, z1) - hw;
        double maxX = Math.max(x0, x1) + hw;
        double maxY = Math.max(y0, y1) + hw;
        double maxZ = Math.max(z0, z1) + hw;

        AxisAlignedBB bb = AxisAlignedBB.getBoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
        java.util.List list = worldObj.getEntitiesWithinAABB(EntityLivingBase.class, bb);

        float dmg = computeDamage();

        for (int i = 0; i < list.size(); i++) {
            EntityLivingBase e = (EntityLivingBase) list.get(i);
            if (e == null || e.isDead) continue;
            if (ownerId != -1 && e.getEntityId() == ownerId) continue;

            // distance from point to segment
            double ex = e.posX;
            double ey = e.posY + e.getEyeHeight() * 0.5;
            double ez = e.posZ;

            double vx = ex - x0;
            double vy = ey - y0;
            double vz = ez - z0;

            double proj = vx * dx + vy * dy + vz * dz; // dot(v, dir)
            if (proj < 0) proj = 0;
            if (proj > len) proj = len;

            double cx = x0 + dx * proj;
            double cy = y0 + dy * proj;
            double cz = z0 + dz * proj;

            double ddx = ex - cx;
            double ddy = ey - cy;
            double ddz = ez - cz;
            double dist = Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
            if (dist > hw) continue;

            applyDamageToEntity(e, dmg);
        }
    }

    private float computeDamage() {
        if (maxDamage > 0f) return maxDamage;

        EntityPlayer owner = (ownerId != -1) ? (EntityPlayer) worldObj.getEntityByID(ownerId) : null;
        float will = (owner != null) ? getPlayerWill(owner) : 0f;

        float d = will * willDamageMult + willDamageFlat;
        if (d < 0f) d = 0f;
        return d;
    }

    // --- Damage application: try DBC-aware path first, fallback to vanilla ---
    private void applyDamageToEntity(EntityLivingBase target, float dmg) {
        if (target == null || dmg <= 0f) return;

        Entity entOwner = (ownerId != -1) ? worldObj.getEntityByID(ownerId) : null;
        EntityPlayer owner = (entOwner instanceof EntityPlayer) ? (EntityPlayer) entOwner : null;

        // Try: com.example.examplemod.server.HexDBCProcDamageProvider.apply(owner, target, dmg)
        boolean dbcDone = false;
        try {
            Class c = Class.forName("com.example.examplemod.server.HexDBCProcDamageProvider");
            java.lang.reflect.Method m = c.getDeclaredMethod("apply", EntityPlayer.class, EntityLivingBase.class, float.class);
            m.setAccessible(true);
            Object res = m.invoke(null, owner, target, Float.valueOf(dmg));
            if (res instanceof Boolean) dbcDone = ((Boolean) res).booleanValue();
            else dbcDone = true;
        } catch (Throwable ignored) {}

        if (!dbcDone) {
            DamageSource src = (owner != null) ? DamageSource.causePlayerDamage(owner) : DamageSource.magic;
            target.attackEntityFrom(src, dmg);
        }
    }

    // --- Will stat access (tries your HexPlayerStats class; else 0) ---
    private float getPlayerWill(EntityPlayer p) {
        if (p == null) return 0f;

        // Try a few known class names
        String[] cls = new String[] {
                "com.example.examplemod.server.HexPlayerStats",
                "com.example.examplemod.HexPlayerStats",
                "com.example.examplemod.util.HexPlayerStats"
        };

        for (int i = 0; i < cls.length; i++) {
            try {
                Class c = Class.forName(cls[i]);
                // prefer getWill(EntityPlayer)
                try {
                    java.lang.reflect.Method m = c.getDeclaredMethod("getWill", EntityPlayer.class);
                    m.setAccessible(true);
                    Object o = m.invoke(null, p);
                    if (o instanceof Number) return ((Number) o).floatValue();
                } catch (Throwable ignored) {}

                // fallback: getPlayersDbcAttributes(player).wil
                try {
                    java.lang.reflect.Method m = c.getDeclaredMethod("getPlayersDbcAttributes", EntityPlayer.class);
                    m.setAccessible(true);
                    Object attrs = m.invoke(null, p);
                    if (attrs != null) {
                        try {
                            java.lang.reflect.Field f = attrs.getClass().getDeclaredField("wil");
                            f.setAccessible(true);
                            Object o = f.get(attrs);
                            if (o instanceof Number) return ((Number) o).floatValue();
                        } catch (Throwable ignored2) {}
                    }
                } catch (Throwable ignored) {}
            } catch (Throwable ignored) {}
        }
        return 0f;
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound nbt) {
        age = nbt.getInteger("Age");
        lifeTicks = nbt.getInteger("Life");
        maxRadius = nbt.getFloat("MaxR");
        color = nbt.getInteger("C0");
        color1 = nbt.getInteger("C1");
        color2 = nbt.getInteger("C2");
        color3 = nbt.getInteger("C3");
        colorMode = nbt.getByte("Mode");
        ownerId = nbt.getInteger("OwnerId");

        shape = nbt.getByte("Shape");
        beamRange = nbt.getFloat("BeamRange");
        beamWidth = nbt.getFloat("BeamWidth");
        projSpeed = nbt.hasKey("ProjSpeed") ? nbt.getFloat("ProjSpeed") : projSpeed;
        projRadius = nbt.hasKey("ProjRadius") ? nbt.getFloat("ProjRadius") : projRadius;
        beamDamageInterval = nbt.getInteger("BeamInt");

        dirX = nbt.getFloat("DirX");
        dirY = nbt.getFloat("DirY");
        dirZ = nbt.getFloat("DirZ");

        doDamage = nbt.getBoolean("DoDamage");
        hitOnce = nbt.getBoolean("HitOnce");
        maxDamage = nbt.getFloat("MaxDmg");
        willDamageMult = nbt.getFloat("WillMult");
        willDamageFlat = nbt.getFloat("WillFlat");
        damageApplied = nbt.getBoolean("DmgApplied");

        if (this.dataWatcher != null) syncToClients();
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound nbt) {
        nbt.setInteger("Age", age);
        nbt.setInteger("Life", lifeTicks);
        nbt.setFloat("MaxR", maxRadius);

        nbt.setInteger("C0", color);
        nbt.setInteger("C1", color1);
        nbt.setInteger("C2", color2);
        nbt.setInteger("C3", color3);
        nbt.setByte("Mode", colorMode);

        nbt.setInteger("OwnerId", ownerId);

        nbt.setByte("Shape", shape);
        nbt.setFloat("BeamRange", beamRange);
        nbt.setFloat("BeamWidth", beamWidth);
        nbt.setFloat("ProjSpeed", projSpeed);
        nbt.setFloat("ProjRadius", projRadius);
        nbt.setInteger("BeamInt", beamDamageInterval);

        nbt.setFloat("DirX", dirX);
        nbt.setFloat("DirY", dirY);
        nbt.setFloat("DirZ", dirZ);

        nbt.setBoolean("DoDamage", doDamage);
        nbt.setBoolean("HitOnce", hitOnce);
        nbt.setFloat("MaxDmg", maxDamage);
        nbt.setFloat("WillMult", willDamageMult);
        nbt.setFloat("WillFlat", willDamageFlat);
        nbt.setBoolean("DmgApplied", damageApplied);
    }
}