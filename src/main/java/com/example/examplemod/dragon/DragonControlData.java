package com.example.examplemod.dragon;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldSavedData;

public class DragonControlData extends WorldSavedData {
    public static final String ID = "ExampleMod_DragonControl";

    // Effective HP model: we scale incoming damage by 200F / effectiveHp
    public float effectiveHp = 200F; // vanilla is ~200 (100 hearts)
    public float outgoingDamageMult = 1.0F;

    public DragonControlData() { super(ID); }
    public DragonControlData(String id) { super(id); }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        effectiveHp = nbt.getFloat("effectiveHp");
        if (effectiveHp <= 0) effectiveHp = 200F;
        outgoingDamageMult = nbt.getFloat("outgoingDamageMult");
        if (outgoingDamageMult <= 0) outgoingDamageMult = 1.0F;
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        nbt.setFloat("effectiveHp", effectiveHp);
        nbt.setFloat("outgoingDamageMult", outgoingDamageMult);
    }
}
