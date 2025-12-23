package com.example.examplemod.util;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.util.Constants;

import java.util.Locale;

public final class WearableNameRules {
    private WearableNameRules() {}

    public static boolean isHelmetWearableByName(ItemStack stack) {
        if (stack == null) return false;

        // 1) Check display name (keeps compatibility with old items)
        String rawName = stack.getDisplayName();
        if (containsWearableKeyword(rawName)) return true;

        // 2) Check lore (new system)
        return loreContainsWearableKeyword(stack);
    }

    private static boolean containsWearableKeyword(String raw) {
        if (raw == null) return false;

        String clean = EnumChatFormatting.getTextWithoutFormattingCodes(raw);
        if (clean == null) clean = raw;

        clean = clean.toLowerCase(Locale.ROOT);
        return clean.contains("ring") || clean.contains("amulet") || clean.contains("artifact");
    }

    private static boolean loreContainsWearableKeyword(ItemStack stack) {
        if (stack == null) return false;
        if (!stack.hasTagCompound()) return false;

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) return false;

        if (!tag.hasKey("display", Constants.NBT.TAG_COMPOUND)) return false;
        NBTTagCompound display = tag.getCompoundTag("display");
        if (display == null) return false;

        if (!display.hasKey("Lore", Constants.NBT.TAG_LIST)) return false;

        // Lore list of strings
        NBTTagList lore = display.getTagList("Lore", Constants.NBT.TAG_STRING);
        if (lore == null || lore.tagCount() <= 0) return false;

        for (int i = 0; i < lore.tagCount(); i++) {
            String line = lore.getStringTagAt(i);
            if (containsWearableKeyword(line)) return true;
        }
        return false;
    }
}
