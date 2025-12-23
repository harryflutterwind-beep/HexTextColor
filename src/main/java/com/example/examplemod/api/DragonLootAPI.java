//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.example.examplemod.api;

import com.example.examplemod.dragon.DragonLootHandler;
import net.minecraft.item.Item;

public final class DragonLootAPI {
    private DragonLootAPI() {
    }

    public static boolean addDrop(String itemId, double chance, int min, int max) {
        return addDropFull(itemId, 0, chance, min, max, (String)null, (Integer)null, (double)0.0F);
    }

    public static boolean addDropWithNbt(String itemId, int meta, double chance, int min, int max, String nbtJson) {
        return addDropFull(itemId, meta, chance, min, max, nbtJson, (Integer)null, (double)0.0F);
    }

    public static boolean addDropForDim(String itemId, int meta, double chance, int min, int max, String nbtJson, int dim, double lootingScale) {
        return addDropFull(itemId, meta, chance, min, max, nbtJson, dim, lootingScale);
    }

    public static void clearAll() {
        DragonLootHandler.RULES.clear();
    }

    private static boolean addDropFull(String itemId, int meta, double chance, int min, int max, String nbtJson, Integer dimFilter, double lootingScale) {
        Item it = DragonLootHandler.resolveItem(itemId);
        if (it == null) {
            return false;
        } else {
            float p = (float)Math.max((double)0.0F, Math.min((double)1.0F, chance));
            DragonLootHandler.RULES.add(new DragonLootHandler.DropRule(it, meta, p, min, max, nbtJson != null && !nbtJson.trim().isEmpty() ? nbtJson.trim() : null, dimFilter, (float)Math.max((double)0.0F, lootingScale)));
            return true;
        }
    }
}
