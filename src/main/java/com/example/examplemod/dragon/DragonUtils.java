// src/main/java/com/example/examplemod/dragon/DragonUtils.java
package com.example.examplemod.dragon;

import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;

public final class DragonUtils {
    private DragonUtils() {}

    /** Storage key used in per-world MapStorage. */
    public static final String WSD_KEY = "HexDragonCfg";

    /** Persisted per-world dragon config. */
    public static class DragonData extends WorldSavedData {
        public float effectiveHp = 200f;      // “feel like this much HP”
        public float outgoingDamageMult = 1f; // scale the dragon’s damage

        public DragonData() { super(WSD_KEY); }
        public DragonData(String name) { super(name); }

        @Override
        public void readFromNBT(NBTTagCompound nbt) {
            effectiveHp = nbt.hasKey("effectiveHp") ? nbt.getFloat("effectiveHp") : 200f;
            outgoingDamageMult = nbt.hasKey("outgoingDamageMult") ? nbt.getFloat("outgoingDamageMult") : 1f;
            if (effectiveHp <= 0f) effectiveHp = 200f;
            if (outgoingDamageMult < 0f) outgoingDamageMult = 0f;
        }

        @Override
        public void writeToNBT(NBTTagCompound nbt) {
            nbt.setFloat("effectiveHp", effectiveHp);
            nbt.setFloat("outgoingDamageMult", outgoingDamageMult);
        }
    }

    /** Get or create the per-world DragonData. */
    public static DragonData dataFor(World w) {
        if (w == null) return new DragonData();
        MapStorage store = w.perWorldStorage;
        DragonData data = (DragonData) store.loadData(DragonData.class, WSD_KEY);
        if (data == null) {
            data = new DragonData();
            store.setData(WSD_KEY, data);
        }
        return data;
    }

    /** Spawn an Ender Dragon at x,y,z (server side). */
    public static void spawnDragon(World w, double x, double y, double z) {
        if (w == null || w.isRemote) return;
        EntityDragon d = new EntityDragon(w);
        d.setLocationAndAngles(x, y, z, w.rand.nextFloat() * 360f, 0f);
        w.spawnEntityInWorld(d);
    }
}
