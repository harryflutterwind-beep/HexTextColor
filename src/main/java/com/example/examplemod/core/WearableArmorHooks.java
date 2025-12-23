package com.example.examplemod.core;

import com.example.examplemod.util.WearableNameRules;
import net.minecraft.item.ItemStack;

public final class WearableArmorHooks {
    private WearableArmorHooks() {}

    /**
     * Some call sites use: 0=helmet,1=chest,2=legs,3=boots
     * Others (common in 1.7.10) use armorInventory index: 3=helmet,2=chest,1=legs,0=boots
     */
    public static boolean allowExtraArmorItems(ItemStack stack, int armorType) {
        if (stack == null) return false;

        // Accept helmet under BOTH conventions
        boolean isHelmet = (armorType == 0 || armorType == 3);

        return isHelmet && WearableNameRules.isHelmetWearableByName(stack);
    }
}
