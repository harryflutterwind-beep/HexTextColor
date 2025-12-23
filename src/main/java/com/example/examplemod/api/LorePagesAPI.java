package com.example.examplemod.api;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lore Pages API (safe to exist in api/):
 * - Script-friendly storage via Item NBT
 * - Optional Java providers (registry)
 * - Helper to auto-slice tooltip body into pages
 *
 * This class is SAFE (no net.minecraft.client.* imports).
 */
public final class LorePagesAPI {

    private LorePagesAPI() {}

    // NBT storage root
    public static final String NBT_ROOT  = "HexLorePages";
    // List of page compounds: each has "L": list of strings
    public static final String NBT_PAGES = "Pages";
    // Key inside each page compound containing line list
    public static final String NBT_LINES = "L";

    public interface Provider {
        /**
         * Return pages for this stack, or null if you don't handle it.
         * Each page is a list of tooltip lines (body-only; NOT including name line).
         */
        List<List<String>> getPages(ItemStack stack, EntityPlayer player);
    }

    private static final List<Provider> PROVIDERS = new ArrayList<Provider>();

    public static void registerProvider(Provider p) {
        if (p != null) PROVIDERS.add(p);
    }

    /**
     * Returns pages for this stack (body-only).
     * Priority:
     *  1) NBT scripted pages
     *  2) Providers
     *  3) empty list
     */
    public static List<List<String>> getPages(ItemStack stack, EntityPlayer player) {
        if (stack == null) return Collections.emptyList();

        List<List<String>> nbt = readPagesFromNBT(stack);
        if (!nbt.isEmpty()) return nbt;

        for (int i = 0; i < PROVIDERS.size(); i++) {
            try {
                List<List<String>> out = PROVIDERS.get(i).getPages(stack, player);
                if (out != null && !out.isEmpty()) return out;
            } catch (Throwable t) {
                // Never crash tooltips
            }
        }
        return Collections.emptyList();
    }

    // ─────────────────────────────────────────────
    // Convenience helpers (script-friendly)
    // ─────────────────────────────────────────────

    /** True if this stack currently has any stored pages in NBT. */
    public static boolean hasPages(ItemStack stack) {
        return stack != null && !readPagesFromNBT(stack).isEmpty();
    }

    /** Number of stored pages in NBT (0 if none). */
    public static int getPageCount(ItemStack stack) {
        return readPagesFromNBT(stack).size();
    }

    /**
     * Convenience: body lines -> autoPages -> writePagesToNBT.
     * If bodyLines is empty, clears pages.
     */
    public static void writeAutoPagesToNBT(ItemStack stack, List<String> bodyLines, int linesPerPage) {
        if (stack == null) return;
        if (bodyLines == null || bodyLines.isEmpty()) {
            clearPagesNBT(stack);
            return;
        }
        List<List<String>> pages = autoPages(bodyLines, linesPerPage);
        writePagesToNBT(stack, pages);
    }

    /**
     * Convenience alias for "pack into pages".
     * This does NOT modify visible lore; it only stores pages into NBT.
     * If you want custom slicing rules, implement them before calling this.
     */
    public static void packLoreIntoPages(ItemStack stack, List<String> loreLines, int linesPerPage) {
        writeAutoPagesToNBT(stack, loreLines, linesPerPage);
    }

    // ─────────────────────────────────────────────
    // Paging utilities
    // ─────────────────────────────────────────────

    /** Fixed-size fallback helper (body lines -> pages). */
    public static List<List<String>> autoPages(List<String> bodyLines, int linesPerPage) {
        if (bodyLines == null || bodyLines.isEmpty()) return Collections.emptyList();
        if (linesPerPage < 1) linesPerPage = 8;

        List<List<String>> pages = new ArrayList<List<String>>();
        int i = 0;
        while (i < bodyLines.size()) {
            int end = Math.min(i + linesPerPage, bodyLines.size());
            List<String> page = new ArrayList<String>();
            for (int j = i; j < end; j++) page.add(bodyLines.get(j));
            pages.add(page);
            i = end;
        }
        return pages;
    }

    /** Writes pages into NBT (each page is a list of lines). */
    public static void writePagesToNBT(ItemStack stack, List<List<String>> pages) {
        if (stack == null) return;

        if (pages == null || pages.isEmpty()) {
            clearPagesNBT(stack);
            return;
        }

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) tag = new NBTTagCompound();

        NBTTagCompound root = new NBTTagCompound();
        NBTTagList pagesList = new NBTTagList();

        for (int p = 0; p < pages.size(); p++) {
            List<String> lines = pages.get(p);
            if (lines == null) lines = Collections.emptyList();

            NBTTagList lineList = new NBTTagList();
            for (int i = 0; i < lines.size(); i++) {
                String s = lines.get(i);
                lineList.appendTag(new NBTTagString(s == null ? "" : s));
            }

            NBTTagCompound pageTag = new NBTTagCompound();
            pageTag.setTag(NBT_LINES, lineList);
            pagesList.appendTag(pageTag);
        }

        root.setTag(NBT_PAGES, pagesList);
        tag.setTag(NBT_ROOT, root);
        stack.setTagCompound(tag);
    }

    public static void clearPagesNBT(ItemStack stack) {
        if (stack == null) return;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) return;

        tag.removeTag(NBT_ROOT);
        if (tag.hasNoTags()) {
            stack.setTagCompound(null);
        }
    }

    /** Reads scripted pages from NBT. Returns empty list if none. */
    public static List<List<String>> readPagesFromNBT(ItemStack stack) {
        if (stack == null) return Collections.emptyList();

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) return Collections.emptyList();

        if (!tag.hasKey(NBT_ROOT, Constants.NBT.TAG_COMPOUND)) return Collections.emptyList();

        NBTTagCompound root = tag.getCompoundTag(NBT_ROOT);
        if (!root.hasKey(NBT_PAGES, Constants.NBT.TAG_LIST)) return Collections.emptyList();

        NBTTagList pagesList = root.getTagList(NBT_PAGES, Constants.NBT.TAG_COMPOUND);
        if (pagesList == null || pagesList.tagCount() <= 0) return Collections.emptyList();

        List<List<String>> out = new ArrayList<List<String>>();

        for (int p = 0; p < pagesList.tagCount(); p++) {
            NBTTagCompound pageTag = pagesList.getCompoundTagAt(p);
            NBTTagList lines = pageTag.getTagList(NBT_LINES, Constants.NBT.TAG_STRING);

            List<String> pageLines = new ArrayList<String>();
            for (int i = 0; i < lines.tagCount(); i++) {
                pageLines.add(lines.getStringTagAt(i));
            }

            out.add(pageLines);
        }

        return out;
    }
}
