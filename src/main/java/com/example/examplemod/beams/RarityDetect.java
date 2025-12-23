package com.example.examplemod.beams;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public final class RarityDetect {

    // Strip formatting & custom tags
    private static final Pattern LEGACY  = Pattern.compile("§[0-9A-FK-OR]", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEXTAGS = Pattern.compile("(?i)</?\\s*(grad|rainbow|#)[^>]*>|§#[0-9a-f]{6}|[«»]");
    private static final Pattern SYMBOLS = Pattern.compile("[^a-z0-9_]", Pattern.CASE_INSENSITIVE);

    /** Removes all formatting, gradient tags, and decorative symbols from text */
    private static String stripAll(String s){
        if (s == null) return "";
        String out = LEGACY.matcher(s).replaceAll("");
        out = HEXTAGS.matcher(out).replaceAll("");
        // Remove decorative stars or pulse markers
        out = out.replace("✦", "").replace("☆", "").replace("*", "");
        return out;
    }

    /** Normalizes for key matching */
    private static String norm(String s){
        if (s == null) return "";
        s = stripAll(s).toLowerCase();
        s = SYMBOLS.matcher(s).replaceAll(""); // keep only a-z0-9_
        return s;
    }

    /** Attempts to extract rarity key from stack lore or name */
    public static String fromStack(ItemStack st){
        if (st == null) return "";
        List<String> lore = getLore(st);

        // If no lore, try the display name
        if (lore.isEmpty()) {
            return keyFromText(norm(st.getDisplayName()));
        }

        // Fast markers (your tagged format)
        for (String line : lore) {
            String clean = norm(line);
            if (clean.contains("effervescent_")) return "effervescent_";
            if (clean.contains("effervescent"))  return "effervescent";
            if (clean.contains("pearlescent"))   return "pearlescent";
            if (clean.contains("seraph"))        return "seraph";
            if (clean.contains("black"))         return "black";
            // NEW:
            if (clean.contains("glitch"))        return "glitch";
            // "e-tech" in lore normalizes to "etech", so check that token:
            if (clean.contains("etech"))         return "etech";
        }

        // Check first line (most common)
        String k = keyFromText(norm(lore.get(0)));
        if (!k.isEmpty()) return k;

        // Fallback: join all lines
        StringBuilder sb = new StringBuilder();
        for (String line : lore) sb.append(line).append(' ');
        return keyFromText(norm(sb.toString()));
    }

    /** Converts cleaned text into a rarity key */
    private static String keyFromText(String s){
        if (s.contains("effervescent_")) return "effervescent_";
        if (s.contains("effervescent"))  return "effervescent";
        if (s.contains("pearlescent"))   return "pearlescent";
        if (s.contains("legendary"))     return "legendary";
        if (s.contains("epic"))          return "epic";
        if (s.contains("rare"))          return "rare";
        if (s.contains("uncommon"))      return "uncommon";
        if (s.contains("seraph"))        return "seraph";
        if (s.contains("black"))         return "black";
        if (s.contains("common"))        return "common";
        // NEW (order after the existing keys is fine—these strings are unique):
        if (s.contains("glitch"))        return "glitch";
        // supports either "e-tech" or "etech" because norm() removes '-'
        if (s.contains("etech"))         return "etech";
        return "";
    }

    /** Reads lore safely */
    private static List<String> getLore(ItemStack st){
        NBTTagCompound tag = st.getTagCompound();
        if (tag == null || !tag.hasKey("display", 10)) return Collections.emptyList();
        NBTTagCompound disp = tag.getCompoundTag("display");
        if (!disp.hasKey("Lore", 9)) return Collections.emptyList();

        NBTTagList list = disp.getTagList("Lore", 8);
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < list.tagCount(); i++) out.add(list.getStringTagAt(i));
        return out;
    }

    /** Returns beam height based on rarity */
    public static int beamHeight(String key){
        if (key == null) return 6;

        if ("common".equals(key))          return 6;
        else if ("uncommon".equals(key))   return 7;
        else if ("rare".equals(key))       return 9;
        else if ("epic".equals(key))       return 11;
        else if ("legendary".equals(key))  return 14;
        else if ("pearlescent".equals(key))return 16;
        else if ("seraph".equals(key))     return 17;
        else if ("effervescent".equals(key))   return 18;
        else if ("effervescent_".equals(key))  return 20;
        else if ("black".equals(key))      return 25;
            // NEW suggested heights (tweak to taste):
        else if ("etech".equals(key))      return 12; // between Epic and Legendary
        else if ("glitch".equals(key))     return 15; // between Legendary and Pearlescent
        else return 6;
    }
}
