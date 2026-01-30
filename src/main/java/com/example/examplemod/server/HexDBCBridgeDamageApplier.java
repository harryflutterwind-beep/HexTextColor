package com.example.examplemod.server;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
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

    private static final String TAG_PLAYER_PERSISTED = "PlayerPersisted";
    private static final String TAG_JRMC_BDY = "jrmcBdy";
    private static final String TAG_JRMC_DAMAGED = "jrmcDamaged";

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

    /** True if the entity appears to be a DBC/JRMCore "Body"-health entity (players + some DBC/NPCDBC mobs). */
    private static boolean hasDbcBody(EntityLivingBase target) {
        if (target == null) return false;
        try {
            NBTTagCompound ed = target.getEntityData();
            if (ed == null) return false;

            if (ed.hasKey(TAG_PLAYER_PERSISTED, 10)) {
                NBTTagCompound persisted = ed.getCompoundTag(TAG_PLAYER_PERSISTED);
                if (persisted != null && persisted.hasKey(TAG_JRMC_BDY, 99)) return true;
            }

            return ed.hasKey(TAG_JRMC_BDY, 99);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Fractured-style Body subtract fallback.
     * Works for many NPCDBC/DBC entities that expose jrmcBdy on the entity NBT.
     */
    private static boolean subtractDbcBody(EntityLivingBase target, float amount) {
        if (target == null || amount <= 0f) return false;
        try {
            NBTTagCompound ed = target.getEntityData();
            if (ed == null) return false;

            boolean hasPersisted = ed.hasKey(TAG_PLAYER_PERSISTED, 10);
            NBTTagCompound persisted = hasPersisted ? ed.getCompoundTag(TAG_PLAYER_PERSISTED) : null;

            NBTTagCompound store = null;
            boolean storeIsPersisted = false;

            if (persisted != null && persisted.hasKey(TAG_JRMC_BDY, 99)) {
                store = persisted;
                storeIsPersisted = true;
            } else if (ed.hasKey(TAG_JRMC_BDY, 99)) {
                store = ed;
            }

            if (store == null) return false;

            // NBT numeric-safe read (type 99 == any numeric)
            double body = store.getDouble(TAG_JRMC_BDY);
            if (Double.isNaN(body) || Double.isInfinite(body)) return false;

            double nb = body - (double) amount;
            if (nb < 0D) nb = 0D;
            if (nb > 2.147e9D) nb = 2.147e9D; // clamp to int-ish range, DBC uses ints in many builds

            // Write as float (matches the Fractured helper behavior)
            store.setFloat(TAG_JRMC_BDY, (float) nb);
            store.setBoolean(TAG_JRMC_DAMAGED, true);

            // If we modified PlayerPersisted, ensure it stays attached
            if (storeIsPersisted) {
                ed.setTag(TAG_PLAYER_PERSISTED, store);
            }

            return true;
        } catch (Throwable ignored) {
            return false;
        }
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

        // Fractured parity:
        // 1) If the target looks like a DBC "Body" entity, try JRMCore damage (works for players and many DBC/NPCDBC mobs)
        // 2) If that fails, do Fractured-style NBT body subtract
        // 3) Otherwise vanilla fallback

        if (hasDbcBody(target) || (target instanceof EntityPlayer)) {
            Method m = getJrmcDam();
            if (m != null) {
                try {
                    m.invoke(null, (Entity) target, dmgInt, src);
                    return;
                } catch (Throwable ignored) {
                    // fall through
                }
            }

            if (subtractDbcBody(target, amount)) {
                return;
            }
        }

        // Vanilla fallback (non-DBC targets)
        try {
            target.attackEntityFrom(src, amount);
        } catch (Throwable ignored) {}
    }
}
