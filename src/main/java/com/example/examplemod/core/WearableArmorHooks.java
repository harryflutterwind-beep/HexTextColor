package com.example.examplemod.core;


import com.example.examplemod.util.WearableNameRules;
import net.minecraft.item.ItemStack;

public final class WearableArmorHooks {
    private WearableArmorHooks() {}

    /** armorType: 0=helmet,1=chest,2=legs,3=boots */
    public static boolean allowExtraArmorItems(ItemStack stack, int armorType) {
        // Only helmet slot gets the special rule
        return armorType == 0 && WearableNameRules.isHelmetWearableByName(stack);
    }
}
