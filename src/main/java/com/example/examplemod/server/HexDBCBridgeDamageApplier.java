package com.example.examplemod.server;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;

import java.lang.reflect.Method;

/**
 * Routes proc damage to DBC/JRMCore for players (Body damage),
 * and falls back to vanilla attackEntityFrom for non-player entities.
 */
public final class HexDBCBridgeDamageApplier implements HexOrbEffectsController.DamageApplier {

    private static Method JRMC_DAM;
    private static boolean LOOKED_UP;

    private static Method getJrmcDam() {
        if (LOOKED_UP) return JRMC_DAM;
        LOOKED_UP = true;

        try {
            Class<?> cls = Class.forName("JinRyuu.JRMCore.JRMCoreH");
            // public static void jrmcDam(Entity target, int dmg, DamageSource src)
            JRMC_DAM = cls.getMethod("jrmcDam", Entity.class, int.class, DamageSource.class);
            JRMC_DAM.setAccessible(true);
        } catch (Throwable t) {
            JRMC_DAM = null;
        }
        return JRMC_DAM;
    }

    @Override
    public void deal(EntityPlayer attacker, EntityLivingBase target, float amount) {
        if (attacker == null || target == null) return;

        // Server-side only
        if (attacker.worldObj == null || attacker.worldObj.isRemote) return;
        if (target.worldObj == null || target.worldObj.isRemote) return;

        if (Float.isNaN(amount) || Float.isInfinite(amount)) amount = 1f;
        if (amount <= 0f) return;

        int dmgInt = (int) Math.ceil(amount);
        if (dmgInt < 1) dmgInt = 1;

        // Keep your same damage type so your controller can ignore recursion properly
        DamageSource src = new EntityDamageSource("hexorb", attacker);

        // If target is a player (DBC uses Body), try JRMCore damage first
        if (target instanceof EntityPlayer) {
            Method m = getJrmcDam();
            if (m != null) {
                try {
                    m.invoke(null, (Entity) target, dmgInt, src);
                    return;
                } catch (Throwable ignored) {
                    // fall back
                }
            }
        }

        // Non-players (and fallback): vanilla
        try {
            target.attackEntityFrom(src, amount);
        } catch (Throwable ignored) {}
    }
}
