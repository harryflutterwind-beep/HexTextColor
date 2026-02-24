package com.example.examplemod.client.fractured;

import com.example.examplemod.api.HexSocketAPI;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Locale;

public final class FracturedUtil {

    private static final String TAG_PROFILE = "HexOrbProfile";
    private static final String TAG_ROLLED  = "HexOrbRolled";

    // Fractured identifiers
    private static final String FRACTURED_PREFIX = "FRACTURED_";
    private static final String TAG_SHARDS       = "HexFracShards";
    private static final String TAG_SNAP_TICKS   = "HexFracSnapTicks";
    private static final String TAG_SNAP_MAX     = "HexFracSnapMax";
    private static final String TAG_TIER         = "HexFracTier";
    private static final String TAG_TYPE         = "HexFracType";

    // Common “what gem is this” keys used across payloads
    private static final String[] GEM_KEY_TAGS = new String[] {
            "HexGemKey",
            "HexGemIcon",
            "HexOrbIcon",
            "GemKey"
    };

    private FracturedUtil() {}

    public static ItemStack findEquippedFractured(EntityPlayer p) {
        if (p == null) return null;

        // Main hand
        ItemStack held = null;
        try { held = p.getCurrentEquippedItem(); } catch (Throwable ignored) {}
        if (isFractured(held)) return held;

        // Socketed gems on held host
        ItemStack sockHeld = findSocketedFractured(held);
        if (sockHeld != null) return sockHeld;

        // Armor slots
        try {
            ItemStack[] armor = (p.inventory != null) ? p.inventory.armorInventory : null;
            if (armor != null) {
                for (int i = 0; i < armor.length; i++) {
                    ItemStack a = armor[i];
                    if (isFractured(a)) return a;

                    ItemStack sock = findSocketedFractured(a);
                    if (sock != null) return sock;
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private static ItemStack findSocketedFractured(ItemStack host) {
        if (host == null) return null;
        try {
            int filled = HexSocketAPI.getSocketsFilled(host);
            for (int i = 0; i < filled; i++) {
                ItemStack gem = HexSocketAPI.getGemAt(host, i);
                if (isFractured(gem)) return gem;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static boolean isFractured(ItemStack s) {
        if (s == null) return false;

        NBTTagCompound tag = s.getTagCompound();
        if (tag == null) return false;

        // Normal orb markers path
        if (tag.getBoolean(TAG_ROLLED) && tag.hasKey(TAG_PROFILE)) {
            String prof = tag.getString(TAG_PROFILE);
            if (prof != null && prof.startsWith(FRACTURED_PREFIX)) return true;
        }

        // If fractured-specific keys exist, treat as Fractured even if profile was stripped.
        if (tag.hasKey(TAG_SHARDS) || tag.hasKey(TAG_SNAP_TICKS) || tag.hasKey(TAG_SNAP_MAX)
                || tag.hasKey(TAG_TIER) || tag.hasKey(TAG_TYPE)) {
            return true;
        }

        // Fallback: detect by gem key/icon string
        String key = readGemKey(tag);
        if (key != null) {
            String n = normalizeGemKey(key);
            if (n.indexOf("fractured") >= 0) return true;
            if (n.indexOf("orb_gem_fractured") >= 0) return true;
        }

        return false;
    }

    private static String readGemKey(NBTTagCompound tag) {
        if (tag == null) return null;
        for (int i = 0; i < GEM_KEY_TAGS.length; i++) {
            String k = GEM_KEY_TAGS[i];
            if (tag.hasKey(k)) {
                String v = tag.getString(k);
                if (v != null && v.length() > 0) return v;
            }
        }
        return null;
    }

    private static String normalizeGemKey(String raw) {
        if (raw == null) return "";
        raw = raw.trim();

        // unwrap <ico:...> if present
        if (raw.startsWith("<ico:")) {
            int end = raw.indexOf('>');
            if (end > 5) raw = raw.substring(5, end);
        }

        raw = raw.replace('\\', '/');
        while (raw.startsWith("/")) raw = raw.substring(1);

        raw = raw.toLowerCase(Locale.ENGLISH);

        if (raw.endsWith(".png")) raw = raw.substring(0, raw.length() - 4);
        return raw;
    }
}
