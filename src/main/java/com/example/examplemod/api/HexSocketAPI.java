package com.example.examplemod.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

/**
 * HexSocketAPI (1.7.10)
 *
 * Socket storage lives under:
 *   ItemStack.tag.HexGems
 *     - SocketsOpen (int)
 *     - SocketsMax  (int, -1 = unlimited)
 *     - Gems        (NBTTagList<String>)  // icon keys like "gems/orb_gem_orange_inferno_64" or with @frames
 *     - GemBonuses  (NBTTagList<Compound>) // OPTIONAL, aligned 1:1 with Gems
 *         - Attr (String)   e.g. "dbc.Strength"
 *         - Amt  (double)   e.g. 123
 *         - Name (String)   optional short display name e.g. "STR"
 *     - SocketsPageIndex (int)  // where the sockets-only lore page lives in HexLorePages/Pages
 *
 * Lore pages storage lives under:
 *   ItemStack.tag.HexLorePages
 *     - Pages (NBTTagList<String>) // each entry is a full page, lines separated by '\n'
 */
public final class HexSocketAPI {

    private HexSocketAPI() {}

    // ─────────────────────────────────────────────────────────────
    // NBT KEYS
    // ─────────────────────────────────────────────────────────────
    public static final String ROOT_HEX_GEMS = "HexGems";
    public static final String K_OPEN        = "SocketsOpen";
    public static final String K_MAX         = "SocketsMax";      // -1 = unlimited
    public static final String K_GEMS        = "Gems";            // list<string>
    public static final String K_BONUSES     = "GemBonuses";      // list<compound> aligned with Gems

    public static final String K_PAGE_INDEX  = "SocketsPageIndex";

    public static final String LORE_ROOT_KEY  = "HexLorePages";
    public static final String LORE_PAGES_KEY = "Pages";          // list<string>

    // per-bonus compound keys
    public static final String B_ATTR = "Attr";
    public static final String B_AMT  = "Amt";
    public static final String B_NAME = "Name";

    // ─────────────────────────────────────────────────────────────
    // RENDER CONSTANTS
    // ─────────────────────────────────────────────────────────────
    private static final String SOCKET_PREFIX = "Sockets:";
    private static final String ICON_EMPTY    = "<ico:gems/socket_empty_64>";

    // If there are NO bonuses present, we keep the compact icon layout
    private static final int ICONS_PER_LINE_COMPACT = 6;

    // If any bonus exists, we render 1 socket per line (icon + text)
    private static final int ICONS_PER_LINE_DETAILED = 1;

    // Fiery animated gradient (simple + safe: scroll only)
    // You can tweak colors/scroll later.
    private static final String FIRE_GRAD_OPEN  = "<grad #ff2a00 #ff7a00 #ffd200 scroll=0.22>";
    private static final String FIRE_GRAD_CLOSE = "</grad>";

    private static String fire(String s) {
        return FIRE_GRAD_OPEN + s + FIRE_GRAD_CLOSE;
    }


    private static String styleForGemKey(String gemKey, String s) {
        if (s == null) return "";
        // If the value already has formatting (MC § codes or our <grad>/<rbw> tags), don't double-wrap.
        if (s.indexOf('§') >= 0 || s.indexOf('<') >= 0) {
            return s;
        }

        String k = (gemKey == null ? "" : gemKey.toLowerCase());

        // Try to match your orb themes.
        if (k.contains("inferno") || k.contains("pill_fire")) {
            return "<grad #ff7a18 #ff3d00>" + s + "</grad>";
        }
        if (k.contains("frost")) {
            return "<grad #36d1ff #4facfe>" + s + "</grad>";
        }
        if (k.contains("solar") || k.contains("gold")) {
            return "<grad #ffe66d #ffb347>" + s + "</grad>";
        }
        if (k.contains("nature") || k.contains("green")) {
            return "<grad #7cff6b #23d5ab>" + s + "</grad>";
        }
        if (k.contains("aether") || k.contains("teal")) {
            return "<grad #00ffd5 #00b3ff>" + s + "</grad>";
        }
        if (k.contains("rainbow") || k.contains("energized") || k.contains("swirly") || k.contains("rbw")) {
            return "<grad #ff4fd8 #36d1ff #ffe66d #7cff6b scroll=0.20>" + s + "</grad>";
        }

        // Fallback: the original fire gradient.
        return FIRE_GRAD_OPEN + s + FIRE_GRAD_CLOSE;
    }

    // ─────────────────────────────────────────────────────────────
    // PUBLIC DATA CLASS
    // ─────────────────────────────────────────────────────────────
    public static final class GemBonus {
        public final String attrKey;
        public final double amount;
        public final String displayName;

        public GemBonus(String attrKey, double amount, String displayName) {
            this.attrKey = (attrKey == null) ? "" : attrKey;
            this.amount = amount;
            this.displayName = (displayName == null) ? "" : displayName;
        }
    }

    // ─────────────────────────────────────────────────────────────
    // SOCKET READ API
    // ─────────────────────────────────────────────────────────────
    public static boolean hasSocketData(ItemStack stack) {
        if (stack == null) return false;
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.hasKey(ROOT_HEX_GEMS, 10);
    }

    public static int getSocketsOpen(ItemStack stack) {
        ensureDefaults(stack);
        return getHexGems(stack).getInteger(K_OPEN);
    }

    public static int getSocketsMax(ItemStack stack) {
        ensureDefaults(stack);
        return getHexGems(stack).getInteger(K_MAX);
    }

    public static int getSocketsFilled(ItemStack stack) {
        ensureDefaults(stack);
        return getGemsList(stack).tagCount();
    }

    public static String getGemKeyAt(ItemStack stack, int idx) {
        ensureDefaults(stack);
        NBTTagList list = getGemsList(stack);
        if (idx < 0 || idx >= list.tagCount()) return "";
        return list.getStringTagAt(idx);
    }

    public static GemBonus getBonusAt(ItemStack stack, int idx) {
        ensureDefaults(stack);
        NBTTagList gems = getGemsList(stack);
        if (idx < 0 || idx >= gems.tagCount()) return null;

        NBTTagList bonuses = getBonusesList(stack, false);
        if (bonuses == null || idx >= bonuses.tagCount()) return null;

        try {
            NBTTagCompound b = bonuses.getCompoundTagAt(idx);
            if (b == null) return null;
            String attr = b.getString(B_ATTR);
            if (attr == null || attr.length() == 0) return null;
            double amt = b.hasKey(B_AMT) ? b.getDouble(B_AMT) : 0.0;
            String name = b.hasKey(B_NAME, 8) ? b.getString(B_NAME) : "";
            return new GemBonus(attr, amt, name);
        } catch (Throwable t) {
            return null;
        }
    }

    public static List<GemBonus> getAllBonuses(ItemStack stack) {
        ensureDefaults(stack);
        NBTTagList gems = getGemsList(stack);
        int n = gems.tagCount();
        if (n <= 0) return Collections.emptyList();

        List<GemBonus> out = new ArrayList<GemBonus>(n);
        for (int i = 0; i < n; i++) {
            GemBonus b = getBonusAt(stack, i);
            out.add(b);
        }
        return out;
    }

    // ─────────────────────────────────────────────────────────────
    // SOCKET WRITE API
    // (All write methods keep Gems/GemBonuses aligned and also keep the sockets-only lore page in sync.)
    // ─────────────────────────────────────────────────────────────

    /**
     * Add N more open sockets (min 1). If max is set and you'd exceed it, this API does NOT auto-set unlimited.
     * (Your command can still do that behavior if you want.)
     */
    public static void openSockets(ItemStack stack, int add) {
        if (stack == null) return;
        ensureDefaults(stack);
        if (add <= 0) add = 1;

        int before = getSocketsOpen(stack);
        int target = Math.max(0, before + add);

        setSocketsOpenInternal(stack, target);
        syncSocketsPageOnly(stack);
    }

    /** Set open sockets to an absolute value (clamps and trims Gems/Bonuses). */
    public static void setSocketsOpen(ItemStack stack, int open) {
        if (stack == null) return;
        ensureDefaults(stack);
        setSocketsOpenInternal(stack, open);
        syncSocketsPageOnly(stack);
    }

    /** Set max sockets (-1 unlimited). If open exceeds max, open is clamped. */
    public static void setSocketsMax(ItemStack stack, int max) {
        if (stack == null) return;
        ensureDefaults(stack);
        getHexGems(stack).setInteger(K_MAX, max);
        if (max >= 0 && getSocketsOpen(stack) > max) {
            setSocketsOpenInternal(stack, max);
        }
        syncSocketsPageOnly(stack);
    }

    /**
     * Socket a gem key (string) into the next available open socket.
     * Returns true if socketed.
     */
    public static boolean socketGem(ItemStack stack, String gemKey) {
        return socketGemWithBonus(stack, gemKey, null, 0.0, null);
    }

    /**
     * Socket a gem and also store an optional bonus to display next to it in the sockets lore page.
     * Returns true if socketed.
     */
    public static boolean socketGemWithBonus(ItemStack stack, String gemKey, String attrKey, double amount, String displayName) {
        if (stack == null) return false;
        ensureDefaults(stack);

        int open = getSocketsOpen(stack);
        int filled = getSocketsFilled(stack);
        if (open <= 0 || filled >= open) return false;

        String key = forceGemsFolder(stripPng(gemKey));
        if (key.length() == 0) return false;

        // append gem
        NBTTagCompound hex = getHexGems(stack);
        NBTTagList gems = getGemsList(stack);
        gems.appendTag(new NBTTagString(key));
        hex.setTag(K_GEMS, gems);

        // append bonus entry (optional)
        NBTTagList bonuses = getBonusesList(stack, true);
        if (bonuses == null) bonuses = new NBTTagList();

        NBTTagCompound b = new NBTTagCompound();
        if (attrKey != null && attrKey.trim().length() > 0) {
            b.setString(B_ATTR, attrKey.trim());
            b.setDouble(B_AMT, amount);
            if (displayName != null && displayName.trim().length() > 0) b.setString(B_NAME, displayName.trim());
        }
        bonuses.appendTag(b);
        hex.setTag(K_BONUSES, bonuses);

        // clamp (safety)
        clampListsToOpen(stack);

        syncSocketsPageOnly(stack);
        return true;
    }
    /** Convenience overload (int amount). Does not apply attributes; stores NBT + tooltip data only. */
    public static boolean socketGemWithBonus(ItemStack stack, String gemKey, String attrKey, int amount, String displayName) {
        return socketGemWithBonus(stack, gemKey, attrKey, (double) amount, displayName);
    }

    /** Convenience overload (int amount, no displayName). */
    public static boolean socketGemWithBonus(ItemStack stack, String gemKey, String attrKey, int amount) {
        return socketGemWithBonus(stack, gemKey, attrKey, (double) amount, null);
    }

    /** Convenience overload (double amount, no displayName). */
    public static boolean socketGemWithBonus(ItemStack stack, String gemKey, String attrKey, double amount) {
        return socketGemWithBonus(stack, gemKey, attrKey, amount, null);
    }


    /** Remove a socketed gem at filled-index idx (0-based). Returns true if removed. */
    public static boolean unsocketGem(ItemStack stack, int idx) {
        if (stack == null) return false;
        ensureDefaults(stack);

        int filled = getSocketsFilled(stack);
        if (idx < 0 || idx >= filled) return false;

        removeGemAndBonusAt(stack, idx);
        syncSocketsPageOnly(stack);
        return true;
    }

    /**
     * Close N sockets from the end (min 1).
     * If this trims filled gems, those gems/bonuses are removed from the end.
     */
    public static void closeSockets(ItemStack stack, int n) {
        if (stack == null) return;
        ensureDefaults(stack);
        if (n <= 0) n = 1;

        int before = getSocketsOpen(stack);
        int newOpen = Math.max(0, before - n);
        setSocketsOpenInternal(stack, newOpen);
        syncSocketsPageOnly(stack);
    }

    /**
     * Close a specific socket slot index within [0, open). This also reduces open by 1.
     * If the closed slot was filled, that gem/bonus is removed (and the rest shifts left).
     */
    public static boolean closeSlot(ItemStack stack, int slotIdx) {
        if (stack == null) return false;
        ensureDefaults(stack);

        int open = getSocketsOpen(stack);
        if (slotIdx < 0 || slotIdx >= open) return false;

        int filled = getSocketsFilled(stack);
        if (slotIdx < filled) {
            removeGemAndBonusAt(stack, slotIdx);
        }

        setSocketsOpenInternal(stack, open - 1);
        syncSocketsPageOnly(stack);
        return true;
    }

    /** Clear all sockets and gems/bonuses, and remove the sockets-only page. */
    public static void clearSockets(ItemStack stack) {
        if (stack == null) return;
        ensureDefaults(stack);

        setSocketsOpenInternal(stack, 0);
        clearGemsAndBonuses(stack);
        syncSocketsPageOnly(stack);
    }

    /** Upsert a bonus for an existing filled gem slot. */
    public static boolean setBonusAt(ItemStack stack, int idx, String attrKey, double amount, String displayName) {
        if (stack == null) return false;
        ensureDefaults(stack);

        int filled = getSocketsFilled(stack);
        if (idx < 0 || idx >= filled) return false;

        NBTTagCompound hex = getHexGems(stack);
        NBTTagList bonuses = getBonusesList(stack, true);
        if (bonuses == null) bonuses = new NBTTagList();

        // Ensure list length >= filled
        while (bonuses.tagCount() < filled) bonuses.appendTag(new NBTTagCompound());

        NBTTagList out = new NBTTagList();
        for (int i = 0; i < filled; i++) {
            NBTTagCompound b = (i < bonuses.tagCount()) ? bonuses.getCompoundTagAt(i) : new NBTTagCompound();
            if (b == null) b = new NBTTagCompound();

            if (i == idx) {
                NBTTagCompound nb = new NBTTagCompound();
                if (attrKey != null && attrKey.trim().length() > 0) {
                    nb.setString(B_ATTR, attrKey.trim());
                    nb.setDouble(B_AMT, amount);
                    if (displayName != null && displayName.trim().length() > 0) nb.setString(B_NAME, displayName.trim());
                }
                out.appendTag(nb);
            } else {
                out.appendTag(b.copy());
            }
        }

        hex.setTag(K_BONUSES, out);
        syncSocketsPageOnly(stack);
        return true;
    }
    /** Convenience overload (int amount). Does not apply attributes; stores NBT + tooltip data only. */
    public static boolean setBonusAt(ItemStack stack, int idx, String attrKey, int amount, String displayName) {
        return setBonusAt(stack, idx, attrKey, (double) amount, displayName);
    }

    /** Convenience overload (int amount, no displayName). */
    public static boolean setBonusAt(ItemStack stack, int idx, String attrKey, int amount) {
        return setBonusAt(stack, idx, attrKey, (double) amount, null);
    }

    /** Convenience overload (double amount, no displayName). */
    public static boolean setBonusAt(ItemStack stack, int idx, String attrKey, double amount) {
        return setBonusAt(stack, idx, attrKey, amount, null);
    }


    // ─────────────────────────────────────────────────────────────
    // Sockets-only Lore Page Sync
    // ─────────────────────────────────────────────────────────────

    /**
     * Keep the sockets-only lore page in sync with current open/filled sockets.
     * - Creates a new page the first time you open sockets on an item with lore pages.
     * - Updates the same page thereafter.
     * - Removes the page if open==0.
     */
    public static void syncSocketsPageOnly(ItemStack stack) {
        if (stack == null) return;
        ensureDefaults(stack);

        int open = getSocketsOpen(stack);
        if (open <= 0) {
            // remove page if present
            removeSocketsPageIfPresent(stack);
            return;
        }

        NBTTagCompound loreRoot = getOrCreateHexLorePages(stack);
        NBTTagList pages = getOrCreateStringList(loreRoot, LORE_PAGES_KEY);

        // decide which index to use
        int idx = getSocketsPageIndex(stack);
        if (idx < 0 || idx >= pages.tagCount()) {
            idx = findExistingSocketsPageIndex(pages);
        }

        // build page text
        List<String> lines = buildSocketsPageLines(stack);
        String pageText = joinLines(lines);

        if (idx < 0) {
            // append new page
            pages.appendTag(new NBTTagString(pageText));
            setSocketsPageIndex(stack, pages.tagCount() - 1);
        } else {
            pages = replacePageAt(pages, idx, pageText);
            loreRoot.setTag(LORE_PAGES_KEY, pages);
            setSocketsPageIndex(stack, idx);
        }

        // write back
        loreRoot.setTag(LORE_PAGES_KEY, pages);
        setTagCompound(stack, loreRoot);
    }

    // ─────────────────────────────────────────────────────────────
    // Page text building
    // ─────────────────────────────────────────────────────────────

    /** Builds the sockets-only page lines (NO other info). */
    public static List<String> buildSocketsPageLines(ItemStack stack) {
        if (stack == null) return Collections.emptyList();
        ensureDefaults(stack);

        int open = getSocketsOpen(stack);
        if (open <= 0) return Collections.emptyList();

        int filled = getSocketsFilled(stack);

        // If any bonus exists, we go "detailed" (1 socket per line: icon + bonus text)
        boolean anyBonus = false;
        for (int i = 0; i < filled; i++) {
            GemBonus b = getBonusAt(stack, i);
            if (b != null && b.attrKey != null && b.attrKey.length() > 0) { anyBonus = true; break; }
        }

        if (anyBonus) {
            return buildSocketLinesDetailed(stack, open, filled);
        }

        // Otherwise keep old compact icon grid
        return buildSocketLinesCompact(stack, open, filled);
    }

    private static List<String> buildSocketLinesCompact(ItemStack stack, int open, int filled) {
        List<String> lines = new ArrayList<String>();
        StringBuilder line = new StringBuilder();
        int inLine = 0;

        for (int i = 0; i < open; i++) {
            if (inLine == 0) {
                line.setLength(0);
                line.append("§7Sockets: ");
            } else {
                line.append(' ');
            }

            if (i < filled) {
                String key = forceGemsFolder(getGemKeyAt(stack, i));
                line.append("<ico:").append(key).append(">");
            } else {
                line.append(ICON_EMPTY);
            }

            inLine++;
            if (inLine >= ICONS_PER_LINE_COMPACT || i == open - 1) {
                lines.add(line.toString());
                inLine = 0;
            }
        }

        return lines;
    }

    private static List<String> buildSocketLinesDetailed(ItemStack stack, int open, int filled) {
        List<String> lines = new ArrayList<String>();

        for (int i = 0; i < open; i++) {
            StringBuilder line = new StringBuilder();
            line.append("§7Sockets: ");

            if (i < filled) {
                String key = forceGemsFolder(getGemKeyAt(stack, i));
                line.append("<ico:").append(key).append(">");

                GemBonus b = getBonusAt(stack, i);
                if (b != null && b.attrKey != null && b.attrKey.length() > 0) {
                    String name = (b.displayName != null && b.displayName.length() > 0)
                            ? b.displayName
                            : prettyAttrName(b.attrKey);

                    line.append(' ');
                    line.append(styleForGemKey(key, name));
                    line.append(' ');
                    line.append(styleForGemKey(key, formatAmountForAttr(b.attrKey, b.amount)));
                }
            } else {
                line.append(ICON_EMPTY);
            }

            lines.add(line.toString());

            // NOTE: ICONS_PER_LINE_DETAILED is effectively 1 (one socket per line)
            // Keeping the constant in case you want to experiment later.
            if (ICONS_PER_LINE_DETAILED != 1) {
                // no-op (intentionally)
            }
        }

        return lines;
    }

    private static String joinLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines.get(i));
        }
        return sb.toString();
    }



    private static String formatAmountForAttr(String attrKey, double amt) {
        if (attrKey != null && attrKey.endsWith(".Multi")) {
            // Display as percent. Stored values may be fractional (0.08) or whole (8).
            double pct = (Math.abs(amt) <= 1.000001d) ? (amt * 100.0d) : amt;

            double r = Math.rint(pct);
            if (Math.abs(pct - r) < 0.000001d) {
                int v = (int) r;
                return (v >= 0 ? "+" : "") + v + "%";
            }
            double v1 = Math.round(pct * 10.0d) / 10.0d;
            return (v1 >= 0 ? "+" : "") + v1 + "%";
        }
        return formatAmount(amt);
    }

    private static String formatAmount(double amt) {
        // For now: integer if close to int, otherwise 1 decimal.
        double r = Math.rint(amt);
        if (Math.abs(amt - r) < 0.000001) {
            int v = (int) r;
            return (v >= 0 ? "+" : "") + v;
        }
        double v1 = Math.round(amt * 10.0) / 10.0;
        return (v1 >= 0 ? "+" : "") + v1;
    }

    private static String prettyAttrName(String attrKey) {
        if (attrKey == null) return "";
        String k = attrKey.trim();
        if (k.equalsIgnoreCase("dbc.Strength")) return "STR";
        if (k.equalsIgnoreCase("dbc.Dexterity")) return "DEX";
        if (k.equalsIgnoreCase("dbc.Constitution")) return "CON";
        if (k.equalsIgnoreCase("dbc.Spirit")) return "SPI";
        if (k.equalsIgnoreCase("dbc.WillPower")) return "WIL";

        // fallback: last segment after '.'
        int dot = k.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < k.length()) return k.substring(dot + 1);
        return k;
    }

    // ─────────────────────────────────────────────────────────────
    // Internal: remove/replace sockets page
    // ─────────────────────────────────────────────────────────────

    private static void removeSocketsPageIfPresent(ItemStack stack) {
        int idx = getSocketsPageIndex(stack);
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(LORE_ROOT_KEY, 10)) {
            clearSocketsPageIndex(stack);
            return;
        }

        NBTTagCompound loreRoot = tag.getCompoundTag(LORE_ROOT_KEY);
        if (!loreRoot.hasKey(LORE_PAGES_KEY, 9)) {
            clearSocketsPageIndex(stack);
            return;
        }

        NBTTagList pages = loreRoot.getTagList(LORE_PAGES_KEY, 8);

        // If stored index is invalid, try to find it.
        if (idx < 0 || idx >= pages.tagCount()) {
            idx = findExistingSocketsPageIndex(pages);
        }

        if (idx < 0 || idx >= pages.tagCount()) {
            clearSocketsPageIndex(stack);
            return;
        }

        pages = removePageAt(pages, idx);
        loreRoot.setTag(LORE_PAGES_KEY, pages);
        setTagCompound(stack, loreRoot);

        clearSocketsPageIndex(stack);
    }

    private static int findExistingSocketsPageIndex(NBTTagList pages) {
        if (pages == null) return -1;
        for (int i = 0; i < pages.tagCount(); i++) {
            String page = pages.getStringTagAt(i);
            if (page == null) continue;
            String firstLine = firstNonEmptyLine(page);
            if (firstLine == null) continue;
            String norm = stripLeadingColorCodes(firstLine).trim();
            // sockets page always starts with "Sockets:" (color-codes ignored)
            if (norm.regionMatches(true, 0, SOCKET_PREFIX, 0, SOCKET_PREFIX.length())) return i;
        }
        return -1;
    }

    private static String firstNonEmptyLine(String page) {
        if (page == null) return null;
        String[] parts = page.split("\\n");
        for (int i = 0; i < parts.length; i++) {
            String s = parts[i];
            if (s == null) continue;
            if (s.trim().length() == 0) continue;
            return s;
        }
        return null;
    }

    private static NBTTagList replacePageAt(NBTTagList pages, int idx, String pageText) {
        NBTTagList out = new NBTTagList();
        for (int i = 0; i < pages.tagCount(); i++) {
            if (i == idx) out.appendTag(new NBTTagString(pageText));
            else out.appendTag(new NBTTagString(pages.getStringTagAt(i)));
        }
        return out;
    }

    private static NBTTagList removePageAt(NBTTagList pages, int idx) {
        NBTTagList out = new NBTTagList();
        for (int i = 0; i < pages.tagCount(); i++) {
            if (i == idx) continue;
            out.appendTag(new NBTTagString(pages.getStringTagAt(i)));
        }
        return out;
    }

    // ─────────────────────────────────────────────────────────────
    // NBT: HexLorePages helpers
    // ─────────────────────────────────────────────────────────────

    private static NBTTagCompound getOrCreateHexLorePages(ItemStack stack) {
        NBTTagCompound root = ensureRootTag(stack);
        if (!root.hasKey(LORE_ROOT_KEY, 10)) root.setTag(LORE_ROOT_KEY, new NBTTagCompound());
        return root.getCompoundTag(LORE_ROOT_KEY);
    }

    private static void setTagCompound(ItemStack stack, NBTTagCompound loreRoot) {
        // loreRoot is HexLorePages compound; ensure it is stored under the root
        NBTTagCompound root = ensureRootTag(stack);
        root.setTag(LORE_ROOT_KEY, loreRoot);
        stack.setTagCompound(root);
    }

    private static NBTTagList getOrCreateStringList(NBTTagCompound parent, String key) {
        if (!parent.hasKey(key, 9)) parent.setTag(key, new NBTTagList());
        return parent.getTagList(key, 8);
    }

    // ─────────────────────────────────────────────────────────────
    // NBT: HexGems helpers
    // ─────────────────────────────────────────────────────────────

    private static void ensureDefaults(ItemStack stack) {
        if (stack == null) return;
        NBTTagCompound hex = getHexGems(stack);
        if (!hex.hasKey(K_OPEN)) hex.setInteger(K_OPEN, 0);
        if (!hex.hasKey(K_MAX)) hex.setInteger(K_MAX, defaultMaxFor(stack));
        if (!hex.hasKey(K_GEMS, 9)) hex.setTag(K_GEMS, new NBTTagList());
        // bonuses are optional (created when needed)
    }

    private static int defaultMaxFor(ItemStack stack) {
        if (stack == null) return 1;

        // Artifacts / rings / amulets (your wearable-lore items) should default to 1 socket.
        if (hasAccessoryWearableLore(stack)) return 1;

        if (stack.getItem() instanceof ItemArmor) return 2;
        return 3; // weapons and most other gear
    }

    /**
     * Detects "artifact/ring/amulet" style items via their lore markers.
     * This is intentionally heuristic so it works across different item classes.
     */
    private static boolean hasAccessoryWearableLore(ItemStack stack) {
        try {
            if (stack == null || !stack.hasTagCompound()) return false;
            NBTTagCompound tag = stack.getTagCompound();
            if (tag == null || !tag.hasKey("display", 10)) return false;
            NBTTagCompound disp = tag.getCompoundTag("display");
            if (disp == null || !disp.hasKey("Lore", 9)) return false;
            NBTTagList lore = disp.getTagList("Lore", 8);
            if (lore == null) return false;

            for (int i = 0; i < lore.tagCount(); i++) {
                String line = lore.getStringTagAt(i);
                if (line == null) continue;
                String lower = line.toLowerCase();
                if (lower.contains("artifact") || lower.contains("amulet") || lower.contains("ring")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // Fall through
        }
        return false;
    }

    private static NBTTagCompound getHexGems(ItemStack stack) {
        NBTTagCompound root = ensureRootTag(stack);
        if (!root.hasKey(ROOT_HEX_GEMS, 10)) root.setTag(ROOT_HEX_GEMS, new NBTTagCompound());
        return root.getCompoundTag(ROOT_HEX_GEMS);
    }

    private static NBTTagList getGemsList(ItemStack stack) {
        NBTTagCompound hex = getHexGems(stack);
        if (!hex.hasKey(K_GEMS, 9)) hex.setTag(K_GEMS, new NBTTagList());
        return hex.getTagList(K_GEMS, 8);
    }

    private static NBTTagList getBonusesList(ItemStack stack, boolean padToGems) {
        NBTTagCompound hex = getHexGems(stack);
        if (!hex.hasKey(K_BONUSES, 9)) {
            if (!padToGems) return null;
            hex.setTag(K_BONUSES, new NBTTagList());
        }
        NBTTagList bonuses = hex.getTagList(K_BONUSES, 10);

        if (padToGems) {
            int gemsCount = getGemsList(stack).tagCount();
            // pad up
            while (bonuses.tagCount() < gemsCount) bonuses.appendTag(new NBTTagCompound());
            // trim down
            if (bonuses.tagCount() > gemsCount) {
                NBTTagList out = new NBTTagList();
                for (int i = 0; i < gemsCount; i++) out.appendTag(bonuses.getCompoundTagAt(i));
                bonuses = out;
                hex.setTag(K_BONUSES, bonuses);
            }
        }

        return bonuses;
    }

    private static void setSocketsOpenInternal(ItemStack stack, int open) {
        NBTTagCompound hex = getHexGems(stack);
        int v = Math.max(0, open);

        int max = hex.hasKey(K_MAX) ? hex.getInteger(K_MAX) : defaultMaxFor(stack);
        if (max >= 0 && v > max) v = max;

        hex.setInteger(K_OPEN, v);
        clampListsToOpen(stack);
    }

    private static void clampListsToOpen(ItemStack stack) {
        int open = getSocketsOpen(stack);
        NBTTagCompound hex = getHexGems(stack);

        if (open <= 0) {
            clearGemsAndBonuses(stack);
            return;
        }

        // clamp gems
        NBTTagList gems = getGemsList(stack);
        int filled = gems.tagCount();
        if (filled > open) {
            NBTTagList out = new NBTTagList();
            for (int i = 0; i < open; i++) out.appendTag(new NBTTagString(gems.getStringTagAt(i)));
            gems = out;
            hex.setTag(K_GEMS, gems);
            filled = open;
        }

        // clamp bonuses aligned to gems
        if (hex.hasKey(K_BONUSES, 9)) {
            NBTTagList bonuses = hex.getTagList(K_BONUSES, 10);

            // trim to filled
            if (bonuses.tagCount() > filled) {
                NBTTagList outB = new NBTTagList();
                for (int i = 0; i < filled; i++) outB.appendTag(bonuses.getCompoundTagAt(i));
                bonuses = outB;
                hex.setTag(K_BONUSES, bonuses);
            }
        }
    }

    private static void clearGemsAndBonuses(ItemStack stack) {
        NBTTagCompound hex = getHexGems(stack);
        hex.setTag(K_GEMS, new NBTTagList());
        if (hex.hasKey(K_BONUSES, 9)) hex.removeTag(K_BONUSES);
    }

    private static void removeGemAndBonusAt(ItemStack stack, int idx) {
        NBTTagCompound hex = getHexGems(stack);

        // remove gem string at idx
        NBTTagList gems = getGemsList(stack);
        NBTTagList outG = new NBTTagList();
        for (int i = 0; i < gems.tagCount(); i++) {
            if (i == idx) continue;
            outG.appendTag(new NBTTagString(gems.getStringTagAt(i)));
        }
        hex.setTag(K_GEMS, outG);

        // remove bonus at idx (if present)
        if (hex.hasKey(K_BONUSES, 9)) {
            NBTTagList bonuses = hex.getTagList(K_BONUSES, 10);
            NBTTagList outB = new NBTTagList();
            for (int i = 0; i < bonuses.tagCount(); i++) {
                if (i == idx) continue;
                outB.appendTag(bonuses.getCompoundTagAt(i));
            }
            // If now empty, remove tag completely.
            if (outB.tagCount() <= 0) hex.removeTag(K_BONUSES);
            else hex.setTag(K_BONUSES, outB);
        }

        clampListsToOpen(stack);
    }

    private static int getSocketsPageIndex(ItemStack stack) {
        NBTTagCompound hex = getHexGems(stack);
        return hex.hasKey(K_PAGE_INDEX) ? hex.getInteger(K_PAGE_INDEX) : -1;
    }

    private static void setSocketsPageIndex(ItemStack stack, int idx) {
        getHexGems(stack).setInteger(K_PAGE_INDEX, idx);
    }

    private static void clearSocketsPageIndex(ItemStack stack) {
        NBTTagCompound hex = getHexGems(stack);
        if (hex.hasKey(K_PAGE_INDEX)) hex.removeTag(K_PAGE_INDEX);
    }

    // ─────────────────────────────────────────────────────────────
    // Key normalization helpers (kept compatible with your command)
    // ─────────────────────────────────────────────────────────────

    /** Strip a trailing .png, if present. */
    private static String stripPng(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.length() == 0) return "";
        if (t.toLowerCase().endsWith(".png")) t = t.substring(0, t.length() - 4);
        // Allow paste: <ico:gems/...>
        if (t.startsWith("<ico:")) t = t.substring(5);
        if (t.endsWith(">")) t = t.substring(0, t.length() - 1);
        return t;
    }

    /** Forces icon keys into gems/... while preserving any "@12" anim suffix. */
    public static String forceGemsFolder(String key) {
        if (key == null) return "";
        String k = key.trim();
        if (k.length() == 0) return "";

        int at = k.indexOf('@');
        String base = (at >= 0) ? k.substring(0, at) : k;
        String suf  = (at >= 0) ? k.substring(at) : "";

        String low = base.toLowerCase();
        if (low.startsWith("gems/")) return base + suf;

        if (low.startsWith("items/")) base = "gems/" + base.substring("items/".length());
        else if (low.startsWith("item/")) base = "gems/" + base.substring("item/".length());
        else if (low.startsWith("textures/gems/")) base = "gems/" + base.substring("textures/gems/".length());
        else if (low.indexOf('/') < 0) base = "gems/" + base;
        else {
            // strip any other folder name
            int slash = base.indexOf('/');
            base = "gems/" + base.substring(slash + 1);
        }

        return base + suf;
    }

    // ─────────────────────────────────────────────────────────────
    // Root NBT helpers
    // ─────────────────────────────────────────────────────────────

    private static NBTTagCompound ensureRootTag(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        return tag;
    }

    // ─────────────────────────────────────────────────────────────
    // Color-stripping helper (used to find sockets page)
    // ─────────────────────────────────────────────────────────────

    private static String stripLeadingColorCodes(String s) {
        if (s == null) return "";
        int i = 0;
        while (i + 1 < s.length()) {
            char c = s.charAt(i);
            char n = s.charAt(i + 1);
            if ((c == '§' || c == '&') && isColorCodeChar(n)) { i += 2; continue; }
            if (c == ' ' || c == '\t') { i++; continue; }
            break;
        }
        return s.substring(i);
    }

    private static boolean isColorCodeChar(char c) {
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'f')
                || (c >= 'k' && c <= 'o')
                || (c == 'r')
                || (c >= 'A' && c <= 'F')
                || (c >= 'K' && c <= 'O')
                || (c == 'R');
    }
}
