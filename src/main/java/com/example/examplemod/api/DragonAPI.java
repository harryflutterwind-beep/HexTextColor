package com.example.examplemod.api;

import com.example.examplemod.dragon.DragonUtils;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.world.WorldServer;

public final class DragonAPI {
    private DragonAPI() {}

    public static int respawn(int dim, double x, double y, double z) {
        WorldServer ws = MinecraftServer.getServer().worldServerForDimension(dim);
        if (ws == null) return 0;

        DragonUtils.spawnDragon(ws, x, y, z);
        return 1;
    }

    public static int killAll(int dim) {
        WorldServer ws = MinecraftServer.getServer().worldServerForDimension(dim);
        if (ws == null) return 0;

        int killed = 0;

        for (Object o : ws.loadedEntityList) {
            if (o instanceof EntityDragon) {
                EntityDragon d = (EntityDragon) o;

                try { d.attackEntityFrom(DamageSource.outOfWorld, Float.MAX_VALUE); }
                catch (Throwable ignored) {}

                d.setDead();
                killed++;
            }
        }

        return killed;
    }

    public static float getEffectiveHp() {
        WorldServer ow = MinecraftServer.getServer().worldServerForDimension(0);
        return ow == null ? 200.0F : DragonUtils.dataFor(ow).effectiveHp;
    }

    public static void setEffectiveHp(float hp) {
        if (hp < 1.0F) hp = 1.0F;

        WorldServer ow = MinecraftServer.getServer().worldServerForDimension(0);
        if (ow != null) {
            DragonUtils.dataFor(ow).effectiveHp = hp;
            DragonUtils.dataFor(ow).markDirty();
        }
    }

    public static float getDamageMultiplier() {
        WorldServer ow = MinecraftServer.getServer().worldServerForDimension(0);
        return ow == null ? 1.0F : DragonUtils.dataFor(ow).outgoingDamageMult;
    }

    public static void setDamageMultiplier(float mult) {
        if (mult < 0.0F) mult = 0.0F;

        WorldServer ow = MinecraftServer.getServer().worldServerForDimension(0);
        if (ow != null) {
            DragonUtils.dataFor(ow).outgoingDamageMult = mult;
            DragonUtils.dataFor(ow).markDirty();
        }
    }

    // Dimension-specific wrappers (still 1-world for now)
    public static float getEffectiveHp(int dim) { return getEffectiveHp(); }
    public static void setEffectiveHp(int dim, float hp) { setEffectiveHp(hp); }
    public static float getDamageMultiplier(int dim) { return getDamageMultiplier(); }
    public static void setDamageMultiplier(int dim, float mult) { setDamageMultiplier(mult); }
}
