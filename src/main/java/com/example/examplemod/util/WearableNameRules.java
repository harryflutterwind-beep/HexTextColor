package com.example.examplemod.util;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import java.util.Locale;
import java.util.regex.Pattern;

public final class WearableNameRules {
    private WearableNameRules() {}

    // Strips vanilla formatting codes (works on all servers, no EnumChatFormatting call)
    private static final Pattern STRIP_MC = Pattern.compile("(?i)§[0-9A-FK-OR]");

    // Optional: strips your <grad ...> / <rbw ...> tags if they exist in lore/name
    private static final Pattern STRIP_TAGS = Pattern.compile("(?is)</?\\s*\\w+[^>]*>");

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

        String clean = STRIP_MC.matcher(raw).replaceAll("");
        clean = STRIP_TAGS.matcher(clean).replaceAll("");
        clean = clean.toLowerCase(Locale.ROOT);

        return clean.contains("ring") || clean.contains("amulet") || clean.contains("artifact");
    }

    private static boolean loreContainsWearableKeyword(ItemStack stack) {
        if (!stack.hasTagCompound()) return false;

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) return false;

        if (!tag.hasKey("display", Constants.NBT.TAG_COMPOUND)) return false;
        NBTTagCompound display = tag.getCompoundTag("display");
        if (display == null) return false;

        if (!display.hasKey("Lore", Constants.NBT.TAG_LIST)) return false;

        NBTTagList lore = display.getTagList("Lore", Constants.NBT.TAG_STRING);
        if (lore == null || lore.tagCount() <= 0) return false;

        for (int i = 0; i < lore.tagCount(); i++) {
            String line = lore.getStringTagAt(i);
            if (containsWearableKeyword(line)) return true;
        }
        return false;
    }
}
