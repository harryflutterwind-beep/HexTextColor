package com.example.examplemod.client.fractured;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import java.util.Locale;

public final class FracturedUtil {

    private static final String TAG_PROFILE = "HexOrbProfile";

    private FracturedUtil() {}

    public static ItemStack findEquippedFractured(EntityPlayer p) {
        if (p == null) return null;

        ItemStack held = p.getCurrentEquippedItem();
        if (isFractured(held)) return held;

        if (p.inventory != null && p.inventory.armorInventory != null) {
            for (int i = 0; i < p.inventory.armorInventory.length; i++) {
                ItemStack s = p.inventory.armorInventory[i];
                if (isFractured(s)) return s;
            }
        }
        return null;
    }

    public static boolean isFractured(ItemStack s) {
        if (s == null) return false;
        if (!s.hasTagCompound()) return false;

        // Primary: profile tag (server writes e.g. "FRACTURED_FLAT" / "FRACTURED_MULTI")
        if (s.getTagCompound().hasKey(TAG_PROFILE, 8)) {
            String prof = s.getTagCompound().getString(TAG_PROFILE);
            if (prof != null) {
                String u = prof.toUpperCase(Locale.ROOT);
                if (u.startsWith("FRACTURED")) return true;
                if ("FRACTURED".equals(u)) return true;
                if ("FRACTURED_FLAT".equals(u)) return true;
                if ("FRACTURED_MULTI".equals(u)) return true;
                if ("FRACTURED_".equals(u)) return true;
                if ("FRACTURED".equalsIgnoreCase(prof)) return true;
                if ("fractured".equalsIgnoreCase(prof)) return true; // legacy
            }
        }

        // Fallback: if someone copied/renamed the stack and profile tag got lost,
        // still allow the HUD to show when it has Fractured shard/snap keys.
        return s.getTagCompound().hasKey("HexFracShards", 3)
                || s.getTagCompound().hasKey("HexFracSnapTicks", 3)
                || s.getTagCompound().hasKey("HexFracTier", 3)
                || s.getTagCompound().hasKey("HexFracType", 8);
    }
}
