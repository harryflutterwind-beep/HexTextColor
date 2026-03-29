package com.example.examplemod.beams;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RarityDetect {

    private static final Pattern LEGACY = Pattern.compile("§[0-9A-FK-OR]", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEXTAGS = Pattern.compile("(?i)</?\\s*(grad|rainbow|glow|pulse|wave|shake|zoom|scroll|jitter|wobble|outline|shadow|flicker|#)[^>]*>|§#[0-9a-f]{6}|[«»]");
    private static final Pattern SYMBOLS = Pattern.compile("[^a-z0-9_+]", Pattern.CASE_INSENSITIVE);
    private static final Pattern WS = Pattern.compile("\\s+");

    private static final Pattern PSEUDO_BLACK_BASE = Pattern.compile("\\[\\s*pseudoblackbase\\s*:\\s*([^\\]]+)\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern HIDDEN_RARITY = Pattern.compile("\\[\\s*(?:rarity\\s*:\\s*)?(common|uncommon|rare|e-?tech|epic|legendary|pearlescent|seraph|glitch|effervescent(?:\\+|_)?|pseudo(?:[_\\s-]?black)?|black)\\s*\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern EVOLVED_TIER = Pattern.compile("evolved\\s*:\\s*tier\\s*\\d+", Pattern.CASE_INSENSITIVE);
    private static final Pattern RARITY_TOKEN = Pattern.compile("\\b(common|uncommon|rare|e-?tech|epic|legendary|pearlescent|seraph|glitch|effervescent(?:\\+|_)?|pseudo(?:[_\\s-]?black)?|black)\\b", Pattern.CASE_INSENSITIVE);
    private RarityDetect() {}

    private static String stripAll(String s) {
        if (s == null) return "";
        String out = LEGACY.matcher(s).replaceAll("");
        out = HEXTAGS.matcher(out).replaceAll("");
        out = out.replace("✦", "").replace("★", "").replace("☆", "");
        return out;
    }

    private static String norm(String s) {
        if (s == null) return "";
        s = stripAll(s).toLowerCase(Locale.ROOT);
        s = s.replace('-', ' ');
        s = SYMBOLS.matcher(s).replaceAll("");
        s = WS.matcher(s).replaceAll(" ").trim();
        return s;
    }

    public static String fromStack(ItemStack st) {
        if (st == null) return "";

        String pseudoBase = pseudoBlackBaseKey(st);
        if (!pseudoBase.isEmpty()) return pseudoBase;

        List<String> lore = getLore(st);
        String visible = detectVisibleBaseRarity(lore);
        if (!visible.isEmpty()) return visible;

        String fromName = detectBaseFromRawText(st.getDisplayName(), stripAll(st.getDisplayName()));
        if (!fromName.isEmpty()) return fromName;

        NBTTagCompound tag = st.getTagCompound();
        if (tag != null && tag.hasKey(RarityTags.KEY)) {
            String direct = sanitizeKey(tag.getString(RarityTags.KEY));
            if (!direct.isEmpty()) {
                if ("pseudo_black".equals(direct)) {
                    String base = pseudoBlackBaseKey(st);
                    if (!base.isEmpty()) return base;
                    return "black";
                }
                return direct;
            }
        }

        if (tag != null) {
            String hidden = hiddenRarityFromText(tag.toString());
            if (!hidden.isEmpty()) {
                if ("pseudo_black".equals(hidden)) {
                    String base = pseudoBlackBaseKey(st);
                    if (!base.isEmpty()) return base;
                    return "black";
                }
                return hidden;
            }
        }

        return "";
    }

    public static boolean hasPseudoBlackOverlay(ItemStack st) {
        if (st == null) return false;
        NBTTagCompound tag = st.getTagCompound();
        if (tag != null) {
            if (tag.hasKey(RarityTags.PSEUDO_BLACK) && tag.getBoolean(RarityTags.PSEUDO_BLACK)) return true;
            String quick = tag.toString();
            if (quick != null) {
                String lower = quick.toLowerCase(Locale.ROOT);
                if (lower.contains("[pseudoblackbase:") || lower.contains("pseudo black overlay") || lower.contains("pseudo_black") || lower.contains("[pseudo_black]") || lower.contains("[pseudoblack]")) {
                    return true;
                }
            }
        }

        List<String> lore = getLore(st);
        for (int i = 0; i < lore.size(); i++) {
            String raw = lore.get(i);
            String clean = stripAll(raw).toLowerCase(Locale.ROOT);
            if (clean.contains("pseudo black overlay") || clean.contains("pseudo black") || clean.contains("[pseudoblackbase:")) {
                return true;
            }
        }
        return false;
    }

    public static String pseudoBlackBaseKey(ItemStack st) {
        if (st == null) return "";
        NBTTagCompound tag = st.getTagCompound();
        if (tag != null) {
            if (tag.hasKey(RarityTags.PSEUDO_BLACK_BASE)) {
                String direct = sanitizeKey(tag.getString(RarityTags.PSEUDO_BLACK_BASE));
                if (!direct.isEmpty() && !"pseudo_black".equals(direct) && !"black".equals(direct)) return direct;
            }

            String quick = tag.toString();
            if (quick != null && !quick.isEmpty()) {
                Matcher m = PSEUDO_BLACK_BASE.matcher(quick);
                if (m.find()) {
                    String parsed = sanitizeKey(m.group(1));
                    if (!parsed.isEmpty() && !"pseudo_black".equals(parsed) && !"black".equals(parsed)) return parsed;
                }
            }
        }

        List<String> lore = getLore(st);
        for (int i = 0; i < lore.size(); i++) {
            Matcher m = PSEUDO_BLACK_BASE.matcher(stripAll(lore.get(i)));
            if (m.find()) {
                String parsed = sanitizeKey(m.group(1));
                if (!parsed.isEmpty() && !"pseudo_black".equals(parsed) && !"black".equals(parsed)) return parsed;
            }
        }
        return "";
    }

    public static String baseRarityFromStack(ItemStack st) {
        return fromStack(st);
    }

    private static String detectVisibleBaseRarity(List<String> lore) {
        if (lore == null || lore.isEmpty()) return "";

        for (int i = 0; i < lore.size(); i++) {
            String raw = lore.get(i);
            String clean = stripAll(raw);
            String found = detectBaseFromRawText(raw, clean);
            if (!found.isEmpty()) return found;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lore.size(); i++) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(stripAll(lore.get(i)));
        }
        return hiddenRarityFromText(sb.toString());
    }

    private static String detectBaseFromRawText(String raw, String clean) {
        if (raw == null && clean == null) return "";
        String safeRaw = raw == null ? "" : raw;
        String safeClean = clean == null ? "" : clean;
        String lower = safeClean.toLowerCase(Locale.ROOT).trim();
        String compact = sanitizeKey(lower);

        lower = EVOLVED_TIER.matcher(lower).replaceAll("");

        if (lower.contains("pseudo black overlay") || lower.contains("pseudo black")) return "";
        if (safeRaw.indexOf('§') >= 0 && (safeRaw.indexOf('☆') >= 0 || safeRaw.indexOf('★') >= 0 || safeRaw.contains("§k"))) return "effervescent_";
        if (lower.indexOf("effervescent+") >= 0 || lower.indexOf("effervescent +") >= 0 || lower.indexOf("effervescentplus") >= 0 || lower.indexOf("effervescent_") >= 0) return "effervescent_";
        if (lower.indexOf("effervescent") >= 0) return "effervescent";
        if (lower.indexOf("e-tech") >= 0 || lower.indexOf("e tech") >= 0 || compact.indexOf("etech") >= 0) return "etech";
        if (lower.indexOf("pearlescent") >= 0) return "pearlescent";
        if (lower.indexOf("legendary") >= 0) return "legendary";
        if (lower.indexOf("uncommon") >= 0) return "uncommon";
        if (lower.indexOf("seraph") >= 0) return "seraph";
        if (lower.indexOf("glitch") >= 0) return "glitch";
        if (lower.indexOf("epic") >= 0) return "epic";
        if (lower.indexOf("rare") >= 0) return "rare";
        if (lower.indexOf("common") >= 0) return "common";
        if ("black".equals(lower) || lower.endsWith(" black") || lower.startsWith("black ") || lower.indexOf("[black]") >= 0) return "black";

        String hidden = hiddenRarityFromText(safeClean);
        if (!hidden.isEmpty() && !"pseudo_black".equals(hidden)) return hidden;

        Matcher m = RARITY_TOKEN.matcher(safeClean);
        if (m.find()) {
            String token = sanitizeKey(m.group(1));
            if (!"pseudo_black".equals(token)) return token;
        }
        return "";
    }

    private static String hiddenRarityFromText(String text) {
        if (text == null || text.isEmpty()) return "";
        Matcher m = HIDDEN_RARITY.matcher(stripAll(text));
        if (m.find()) return sanitizeKey(m.group(1));
        return "";
    }

    private static String sanitizeKey(String s) {
        String k = norm(s);
        if (k == null || k.isEmpty()) return "";
        if (k.indexOf("effervescent+") >= 0 || k.indexOf("effervescent_") >= 0 || k.indexOf("effervescentplus") >= 0) return "effervescent_";
        if (k.indexOf("effervescent") >= 0) return "effervescent";
        if (k.indexOf("pearlescent") >= 0) return "pearlescent";
        if (k.indexOf("legendary") >= 0) return "legendary";
        if (k.indexOf("epic") >= 0) return "epic";
        if (k.indexOf("rare") >= 0) return "rare";
        if (k.indexOf("uncommon") >= 0) return "uncommon";
        if (k.indexOf("seraph") >= 0) return "seraph";
        if (k.indexOf("glitch") >= 0) return "glitch";
        if (k.indexOf("etech") >= 0 || k.indexOf("e tech") >= 0) return "etech";
        if (k.indexOf("pseudo black") >= 0 || k.indexOf("pseudoblack") >= 0 || "pseudo".equals(k)) return "pseudo_black";
        if (k.indexOf("common") >= 0) return "common";
        if (k.indexOf("black") >= 0) return "black";
        return "";
    }

    private static List<String> getLore(ItemStack st) {
        NBTTagCompound tag = st.getTagCompound();
        if (tag == null || !tag.hasKey("display", 10)) return Collections.emptyList();
        NBTTagCompound disp = tag.getCompoundTag("display");
        if (!disp.hasKey("Lore", 9)) return Collections.emptyList();

        NBTTagList list = disp.getTagList("Lore", 8);
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < list.tagCount(); i++) out.add(list.getStringTagAt(i));
        return out;
    }

    public static int beamHeight(String key) {
        if (key == null) return 6;

        if ("common".equals(key)) return 6;
        else if ("uncommon".equals(key)) return 7;
        else if ("rare".equals(key)) return 9;
        else if ("epic".equals(key)) return 11;
        else if ("legendary".equals(key)) return 14;
        else if ("pearlescent".equals(key)) return 16;
        else if ("seraph".equals(key)) return 17;
        else if ("effervescent".equals(key)) return 18;
        else if ("effervescent_".equals(key)) return 20;
        else if ("black".equals(key)) return 25;
        else if ("etech".equals(key)) return 12;
        else if ("glitch".equals(key)) return 15;
        else return 6;
    }
}
