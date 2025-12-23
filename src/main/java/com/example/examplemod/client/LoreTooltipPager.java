// src/main/java/com/example/examplemod/client/LoreTooltipPager.java
package com.example.examplemod.client;

import com.example.examplemod.api.LorePagesAPI;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Lore tooltip pager (client-side).
 *
 * Default behavior:
 *  - If the item has NO stored pages, do nothing (vanilla tooltip only).
 *  - If the item HAS stored pages:
 *      - Not holding view key: add a "Hold <key>..." hint line (aqua + italic)
 *      - Holding view key: show paged tooltip with your HR bars + view footer
 *
 * Supports BOTH formats:
 *
 * NEW (API / Compound pages):
 *  stack.tag.HexLorePages.Pages = NBTTagList<Compound>
 *  each compound has "L" = NBTTagList<String> (lines)
 *
 * OLD (Legacy / String pages):
 *  stack.tag.HexLorePages.Pages = NBTTagList<String>
 *  each page is one big string split on \n or "\\n"
 */
public class LoreTooltipPager {

    // NBT location for stored pages
    private static final String NBT_ROOT = "HexLorePages";
    private static final String NBT_LIST = "Pages"; // legacy: NBTTagList of NBTTagString

    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent e) {
        if (e == null) return;

        ItemStack stack = e.itemStack;
        List<String> tip = e.toolTip;
        if (stack == null || tip == null || tip.isEmpty()) return;

        EntityPlayer player = e.entityPlayer;

        // Enable paging UI if item has API pages OR legacy pages
        boolean hasStored = hasStoredPages(stack);
        if (!hasStored) return;

        // Not holding key: keep normal tooltip, add hint line (italic + aqua)
        if (!LoreKeybinds.isLoreViewHeld()) {
            final String A  = "§b";
            final String IT = "§o";
            tip.add(A + IT + "Hold §f" + IT + safeLoreViewKeyName() + A + IT + " to view pages §7" + IT);
            return;
        }

        // Holding key: override tooltip with paged view
        LorePageController.trackHovered(stack);

        // Build pages (API-first; legacy fallback)
        List<List<String>> pages = getAllPages(stack, player);
        int maxPages = (pages == null) ? 0 : pages.size();
        if (maxPages <= 0) return;

        // Poll PgUp/PgDn to adjust the active page
        LorePageController.pollKeys(maxPages);

        int page = LorePageController.getPage();
        if (page < 0) page = 0;
        if (page > maxPages - 1) page = maxPages - 1;

        // Preserve the item header (name + optional 2nd line like an ID)
        List<String> header = new ArrayList<String>();
        if (!tip.isEmpty()) {
            header.add(tip.get(0));
            if (tip.size() > 1) {
                // Many mods add an ID/extra line directly under the name; preserve it too.
                header.add(tip.get(1));
            }
        }

        applyPageToTooltip(tip, header, pages.get(page), page, maxPages);
    }

    // ─────────────────────────────────────────────
    // Stored pages detection
    // ─────────────────────────────────────────────

    private static boolean hasStoredPages(ItemStack stack) {
        if (stack == null) return false;

        // NEW: API format pages
        try {
            if (LorePagesAPI.hasPages(stack)) return true;
        } catch (Throwable ignored) {}

        // OLD: legacy string-list pages
        return hasLegacyPages(stack);
    }

    private static boolean hasLegacyPages(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) return false;
        net.minecraft.nbt.NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(NBT_ROOT, 10)) return false; // 10 = compound
        net.minecraft.nbt.NBTTagCompound root = tag.getCompoundTag(NBT_ROOT);
        if (root == null || !root.hasKey(NBT_LIST, 9)) return false; // 9 = list
        net.minecraft.nbt.NBTTagList list = root.getTagList(NBT_LIST, 8); // 8 = string
        return list != null && list.tagCount() > 0;
    }

    // ─────────────────────────────────────────────
    // Page reading (API-first, legacy fallback)
    // ─────────────────────────────────────────────

    private static List<List<String>> getAllPages(ItemStack stack, EntityPlayer player) {
        // 1) API NBT pages first (matches your script)
        try {
            List<List<String>> api = LorePagesAPI.readPagesFromNBT(stack);
            if (api != null && !api.isEmpty()) return api;
        } catch (Throwable ignored) {}

        // 2) Legacy string-list pages
        List<List<String>> legacy = getLegacyStoredPages(stack);
        if (legacy != null && !legacy.isEmpty()) return legacy;

        // 3) Optional providers (only if you ever register providers)
        // LorePagesAPI.getPages() returns NBT pages if present, otherwise providers.
        // Since we already tried NBT, this is effectively provider-only here.
        try {
            List<List<String>> prov = LorePagesAPI.getPages(stack, player);
            if (prov != null && !prov.isEmpty()) return prov;
        } catch (Throwable ignored) {}

        return new ArrayList<List<String>>();
    }

    private static List<List<String>> getLegacyStoredPages(ItemStack stack) {
        List<List<String>> out = new ArrayList<List<String>>();
        if (stack == null || !stack.hasTagCompound()) return out;

        net.minecraft.nbt.NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(NBT_ROOT, 10)) return out;

        net.minecraft.nbt.NBTTagCompound root = tag.getCompoundTag(NBT_ROOT);
        if (root == null || !root.hasKey(NBT_LIST, 9)) return out;

        net.minecraft.nbt.NBTTagList list = root.getTagList(NBT_LIST, 8);
        if (list == null) return out;

        for (int i = 0; i < list.tagCount(); i++) {
            String pageText = list.getStringTagAt(i);
            out.add(splitLegacyPageToLines(pageText));
        }
        return out;
    }

    private static List<String> splitLegacyPageToLines(String pageText) {
        List<String> lines = new ArrayList<String>();
        if (pageText == null) return lines;

        // Supports either real newlines or literal "\n"
        String[] split = pageText.split("\\\\n|\\r?\\n");
        for (int j = 0; j < split.length; j++) {
            String s = split[j];
            if (s == null) s = "";
            lines.add(s);
        }
        return lines;
    }

    // ─────────────────────────────────────────────
    // Key name helper (safe reflection)
    // ─────────────────────────────────────────────

    private static String safeLoreViewKeyName() {
        try {
            String[] methods = new String[] {
                    "getLoreViewKeyName",
                    "getLoreViewKeyDisplay",
                    "getLoreViewKeyString",
                    "getViewKeyName",
                    "getKeyName"
            };
            for (int i = 0; i < methods.length; i++) {
                try {
                    java.lang.reflect.Method m = LoreKeybinds.class.getMethod(methods[i]);
                    Object out = m.invoke(null);
                    if (out != null) {
                        String s = String.valueOf(out).trim();
                        if (!s.isEmpty()) return s;
                    }
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (Throwable ignored) {}
        return "Lore View";
    }

    // ─────────────────────────────────────────────
    // Tooltip renderer (keeps your HR format exactly)
    // ─────────────────────────────────────────────

    private static void applyPageToTooltip(List<String> tip, List<String> header, List<String> body, int page, int maxPages) {
        final String IT = "§o";
        final String A  = "§b";

        // Keep your format exactly the same
        final String HR = "<pulse amp=0.36 speed=0.50><loop #a400ff><grad #0a0012 #3a0066 #a400ff #ffffff #00ffff #ff2bd6 #150024 scroll=0.31>======================</grad></loop></pulse>";

        List<String> out = new ArrayList<String>();

        // Header (name + optional id line), preserved exactly
        if (header != null && !header.isEmpty()) {
            for (int i = 0; i < header.size(); i++) {
                String h = header.get(i);
                if (h == null) h = "";
                if (!h.isEmpty()) out.add(h);
            }
        }

        // Space under the name/id (prevents "bunched" look)
        out.add("");

        // Page content
        out.add(HR);

        if (body != null && !body.isEmpty()) {
            for (int i = 0; i < body.size(); i++) {
                String line = body.get(i);
                if (line == null) line = "";
                // Preserve formatting; optional italic for the page view
                out.add(IT + line);
            }
        }

        out.add(HR);

        int viewTotal = Math.max(1, maxPages);
        int viewIndex = Math.max(1, page + 1);
        out.add(A + IT + "View " + viewIndex + "/" + viewTotal + "  (PgUp/PgDn)");

        // Bottom spacer so it doesn't touch the border
        out.add("");

        tip.clear();
        tip.addAll(out);
    }

    // (Kept in case you want it later; currently unused)
    @SuppressWarnings("unused")
    private static String stripColor(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.replaceAll("(?i)§[0-9A-FK-OR]", "");
    }
}
