package com.example.examplemod.gui;

import com.example.examplemod.api.HexSocketAPI;
import com.example.examplemod.item.ItemGemIcons;

import cpw.mods.fml.common.registry.GameData;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.InventoryCraftResult;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ChatComponentText;

import java.lang.reflect.Method;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Option A:
 * - Left slot: target item (stackSize == 1)
 * - Right slot: gem/pill (ItemGemIcons)
 * - Output: preview; taking output consumes inputs and gives result
 * - NO stat rolling yet (we only socket the gem key via HexSocketAPI.socketGem)
 */
public class ContainerHexSocketStation extends Container {

    private final EntityPlayer player;

    // Client-synced: whether this player is allowed to view the debug overlay (OP/admin).
    private static final int PB_DEBUG_ALLOWED = 203;
    private boolean clientDebugAllowed = false;
    private int lastSentDebugAllowed = -1;

    public boolean isDebugAllowed() {
        return clientDebugAllowed;
    }


    private final InventoryBasic input = new InventoryBasic("HexSocketStation", false, 2) {
        @Override
        public void markDirty() {
            super.markDirty();
            // IMPORTANT: InventoryBasic does NOT notify the container by default.
            // Mirror vanilla anvil behavior so the preview/output updates immediately.
            ContainerHexSocketStation.this.onCraftMatrixChanged(this);
        }
    };

    private final InventoryCraftResult output = new InventoryCraftResult();

    // The gem key that produced the current preview (server-side).
    // Used so we don't accidentally apply a different key or double-socket.
    private String previewGemKey = "";

    // Keys used by your HexGems NBT root (matches HexSocketCommand + HexSocketAPI).
    private static final String NBT_ROOT = "HexGems";
    private static final String NBT_OPEN = "SocketsOpen";
    private static final String NBT_MAX  = "SocketsMax";
    private static final String NBT_GEMS = "Gems";

    // Slot indices inside this container:
    // 0 = target, 1 = gem, 2 = output
    private static final int SLOT_TARGET = 0;
    private static final int SLOT_GEM    = 1;
    private static final int SLOT_OUTPUT = 2;

    public ContainerHexSocketStation(EntityPlayer player) {
        this.player = player;

        // Anvil-like positions
        this.addSlotToContainer(new Slot(input, SLOT_TARGET, 27, 47) {
            @Override
            public boolean isItemValid(ItemStack stack) {
                return stack != null && stack.stackSize == 1;
            }
        });

        this.addSlotToContainer(new Slot(input, SLOT_GEM, 76, 47) {
            @Override
            public boolean isItemValid(ItemStack stack) {
                return stack != null && stack.getItem() instanceof ItemGemIcons;
            }
        });

        this.addSlotToContainer(new SlotOutput(player, output, 0, 134, 47));

        // Player inventory
        bindPlayerInventory(player.inventory);
        updateOutput();
    }

    private void bindPlayerInventory(InventoryPlayer inv) {
        // Main inventory 3 rows
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlotToContainer(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        // Hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlotToContainer(new Slot(inv, col, 8 + col * 18, 142));
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return true; // no block/tile, command-opened GUI
    }

    @Override
    public void addCraftingToCrafters(ICrafting ic) {
        super.addCraftingToCrafters(ic);
        if (player != null && player.worldObj != null && !player.worldObj.isRemote) {
            int allowed = computeDebugAllowedServer() ? 1 : 0;
            try {
                ic.sendProgressBarUpdate(this, PB_DEBUG_ALLOWED, allowed);
            } catch (Throwable ignored) {
            }
            lastSentDebugAllowed = allowed;
        }
    }

    @Override
    public void onCraftMatrixChanged(IInventory inv) {
        super.onCraftMatrixChanged(inv);
        if (inv == input) updateOutput();
    }

    private boolean computeDebugAllowedServer() {
        try {
            return player != null && player.canCommandSenderUseCommand(2, "hexsocket");
        } catch (Throwable t) {
            return false;
        }
    }


    @Override
    public void updateProgressBar(int id, int value) {
        super.updateProgressBar(id, value);
        if (id == PB_DEBUG_ALLOWED) {
            this.clientDebugAllowed = (value != 0);
        }
    }

    @Override
    public void detectAndSendChanges() {
        // Keep output preview always correct (InventoryBasic won't always trigger onCraftMatrixChanged)
        updateOutput();

        // Sync debug permission (server -> client)
        if (player != null && player.worldObj != null && !player.worldObj.isRemote) {
            int allowed = computeDebugAllowedServer() ? 1 : 0;
            if (allowed != lastSentDebugAllowed) {
                lastSentDebugAllowed = allowed;
                for (Object o : this.crafters) {
                    try {
                        ((ICrafting) o).sendProgressBarUpdate(this, PB_DEBUG_ALLOWED, allowed);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }

        super.detectAndSendChanges();
    }


    private void updateOutput() {
        // Server owns the preview. Client receives slot updates via packets.
        if (player != null && player.worldObj != null && player.worldObj.isRemote) {
            return;
        }

        ItemStack target = input.getStackInSlot(SLOT_TARGET);
        ItemStack gem    = input.getStackInSlot(SLOT_GEM);

        // Always clear the preview first (prevents stale client output).
        // NOTE: output (InventoryCraftResult) is size 1 and uses index 0.
        // SLOT_OUTPUT is the *container* slot index (2), not the output inventory index.
        output.setInventorySlotContents(0, null);
        previewGemKey = null;

        if (target == null || gem == null) {
            return;
        }
        if (target.stackSize != 1 || gem.stackSize < 1) {
            return;
        }

        // NOTE: "open" is how many sockets are unlocked, NOT the max. You can only socket when open > filled.
        int open   = safeGetOpen(target);
        int filled = safeGetFilled(target);

        if (open <= 0 || open <= filled) {
            return;
        }

        // Derive the correct variant token from the gem stack (server-safe).
        String token = deriveGemTokenPreferVariant(gem);
        String kCanon = normalizeGemKey(token); // always "gems/<name>" (no .png)

        if (kCanon == null || kCanon.trim().isEmpty()) {
            return;
        }

        ItemStack preview = target.copy();
        preview.stackSize = 1;

        // Ensure preview has the same socket metadata shape as the target.
        ensureSocketDefaults(preview);

        // Important: guarantee the preview copy has the same open socket count as the real target,
        // even if the preview copy's NBT is missing/partial for any reason.
        try {
            HexSocketAPI.setSocketsOpen(preview, open);
        } catch (Throwable ignored) {
            NBTTagCompound tag = preview.getTagCompound();
            if (tag != null) {
                NBTTagCompound root = tag.getCompoundTag(NBT_ROOT);
                root.setInteger(NBT_OPEN, open);
                tag.setTag(NBT_ROOT, root);
                preview.setTagCompound(tag);
            }
        }

        List<SocketBonus> bonuses = extractSocketBonusesFromGem(gem, kCanon);
        SocketBonus firstBonus = (bonuses == null || bonuses.isEmpty()) ? null : bonuses.get(0);

        // Always socket the gem first (this also appends an empty bonus compound so indices stay aligned).
        boolean ok = HexSocketAPI.socketGem(preview, kCanon);

        // For display: force icon key to be base folder. (prevents "gems/" mismatches in <ico:...>)
        sanitizeGemKeysInPlace(preview);

        // Store the full gem/orb ItemStack into the parallel GemStacks list so effects/NBT carry over
        // when socketed (e.g., Fractured shards/cooldowns, action levels, etc.).
        if (ok) {
            try {
                int filledNow = HexSocketAPI.getSocketsFilled(preview);
                int socketIdx = Math.max(0, filledNow - 1);

                ItemStack gemCopy = gem.copy();
                gemCopy.stackSize = 1;

                // Keep a canonical key hint for downstream systems (optional but helpful).
                NBTTagCompound gtag = gemCopy.getTagCompound();
                if (gtag == null) gtag = new NBTTagCompound();
                gtag.setString("HexGemKey", kCanon);
                gemCopy.setTagCompound(gtag);

                HexSocketAPI.setGemAt(preview, socketIdx, gemCopy);
            } catch (Throwable t) {
                // ignore - socketing will still work via gem keys
            }
        }

        boolean storedInApi = false;
        if (ok && bonuses != null && !bonuses.isEmpty()) {
            int filledNow = HexSocketAPI.getSocketsFilled(preview);
            int socketIdx = Math.max(0, filledNow - 1);

            // Write ALL stats for this gem into the socket-bonus list (multi-roll support).
            try {
                String[] keys = new String[bonuses.size()];
                double[] amts = new double[bonuses.size()];
                String[] names = new String[bonuses.size()];
                for (int bi = 0; bi < bonuses.size(); bi++) {
                    SocketBonus b = bonuses.get(bi);
                    keys[bi] = (b == null || b.attrKey == null) ? "" : b.attrKey;
                    amts[bi] = (b == null) ? 0.0 : b.amount;
                    names[bi] = (b == null || b.displayName == null) ? "" : b.displayName;
                }
                storedInApi = HexSocketAPI.setBonusesAt(preview, socketIdx, keys, amts, names);
            } catch (Throwable t) {
                storedInApi = false;
            }

            // Legacy fallback: store only the first stat if setBonusesAt is unavailable.
            if (!storedInApi && firstBonus != null && firstBonus.attrKey != null && firstBonus.attrKey.length() > 0) {
                try {
                    storedInApi = HexSocketAPI.setBonusAt(preview, socketIdx, firstBonus.attrKey, firstBonus.amount, firstBonus.displayName);
                } catch (Throwable t) {
                    storedInApi = false;
                }
            }
        }

        // Some gems may not store rolls in NBT; patch RPGCore attributes as a fallback (uses the list).
        applySocketBonusesToRpgCoreIfNeeded(preview, gem, bonuses);

        // If API storage failed (old runtime), store minimal fallback data for hex lore patching.
        if (!storedInApi && firstBonus != null && firstBonus.attrKey != null && firstBonus.attrKey.length() > 0) {
            // Don't rely on API "filled" in case it de-dups; use the raw NBT list length.
            tryStoreSocketBonusFallback(preview, Math.max(0, nbtGetSocketsFilled(preview) - 1), firstBonus);
        }

// Refresh the sockets lore pages so PREVIEW + FINAL item show the stat line immediately.
        refreshSocketLorePagesForStation(preview);

        previewGemKey = kCanon;
        // Output inventory index is always 0 (see note above).
        output.setInventorySlotContents(0, preview);
    }

    /**
     * Normalize a gem/pill key into the canonical format used by the socket system:
     *   "gems/<name>" (no .png)
     *
     * This mirrors the intent of HexSocketCommand.normalizeGemKey(...) without alias mapping.
     */
    private static String normalizeGemKey(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.isEmpty()) return "";

        // Allow passing a raw icon token.
        if (s.startsWith("<ico:") && s.endsWith(">")) {
            s = s.substring(5, s.length() - 1).trim();
        }

        // Some call sites may hand us a registry name like "modid:path"
        // or an unlocalized name like "item.something".
        int colon = s.indexOf(':');
        if (colon >= 0 && colon < s.length() - 1) {
            s = s.substring(colon + 1);
        }
        if (s.startsWith("item.")) s = s.substring(5);
        if (s.startsWith("tile.")) s = s.substring(5);


        // Item unlocalized ids can look like "hex_gem.orb_gem_violet_void_64" after stripping.
        // Convert dotted ids to just the last segment ("orb_gem_violet_void_64") so we match your texture keys.
        if (s.indexOf('/') < 0) {
            int dot = s.lastIndexOf('.');
            if (dot >= 0 && dot + 1 < s.length()) {
                s = s.substring(dot + 1);
            }
        }

        // Strip a leading "textures/" if someone passed a full path.
        if (s.startsWith("textures/")) {
            s = s.substring("textures/".length());
        }

        // Strip extension
        if (s.endsWith(".png")) {
            s = s.substring(0, s.length() - 4);
        }

        // Already canonical
        if (s.startsWith("gems/")) return s;

        // ------------------------------------------------------------------
        // IMPORTANT: Server-safe normalization for your gem/pill items.
        // Many mod items expose a server-side unlocalized/registry name like:
        //   "hex_gemorb_gem_violet_void_64"  -> texture key: "orb_gem_violet_void_64"
        //   "hex_gempill_pill_dark_fire_face_64" -> texture key: "pill_dark_fire_face_64"
        // (The client may have a VARIANTS[] array, but it's often @SideOnly(CLIENT).)
        // ------------------------------------------------------------------

        // Strip any folder and keep only the filename-like token.
        int slash = s.lastIndexOf('/');
        if (slash >= 0 && slash < s.length() - 1) {
            s = s.substring(slash + 1);
        }

        // Map common item-name patterns -> texture key patterns
        if (s.startsWith("hex_gemorb_gem_")) {
            s = "orb_gem_" + s.substring("hex_gemorb_gem_".length());
        } else if (s.startsWith("gemorb_gem_")) {
            s = "orb_gem_" + s.substring("gemorb_gem_".length());
        } else if (s.startsWith("hex_gempill_pill_")) {
            s = "pill_" + s.substring("hex_gempill_pill_".length());
        } else if (s.startsWith("gempill_pill_")) {
            s = "pill_" + s.substring("gempill_pill_".length());
        } else if (s.startsWith("hex_gemorb_")) {
            // Occasionally without the extra "gem_" token:
            // hex_gemorb_violet_void_64 -> orb_gem_violet_void_64
            s = "orb_gem_" + s.substring("hex_gemorb_".length());
        } else if (s.startsWith("hex_gempill_")) {
            // hex_gempill_dark_fire_face_64 -> pill_dark_fire_face_64
            s = "pill_" + s.substring("hex_gempill_".length());
        }

        // If the caller already provided "orb_gem_*" or "pill_*", accept it.
        return "gems/" + s;
    }

    /**
     * Server-safe helper: derive the canonical gem texture key from an
     * unlocalized/registry-like token.
     *
     * This project has used a few names for this helper across iterations
     * ("deriveKeyFromUnlocId" / "deriveKeyFromUnlockId"). We keep both to
     * avoid IDE/merge churn.
     */
    private static String deriveKeyFromUnlocId(String token) {
        return normalizeGemKey(token);
    }

    // Back-compat alias (some IDEs/autocomplete ended up with this name).
    private static String deriveKeyFromUnlockId(String token) {
        return deriveKeyFromUnlocId(token);
    }

    /**
     * Prefer deriving the gem token from ItemGemIcons.VARIANTS (server-safe),
     * falling back to registry/unlocalized strings. Returned value is a *token*
     * like "orb_gem_teal_aether_64" (not prefixed). Call normalizeGemKey(...)
     * to get the canonical stored key ("gems/<token>").
     */
    private static String deriveGemTokenPreferVariant(ItemStack gem) {
        if (gem == null) return null;

        // Best case: our gem/pill item class with a stable VARIANTS table.
        try {
            if (gem.getItem() instanceof ItemGemIcons) {
                int m = gem.getItemDamage();
                if (m < 0) m = 0;
                if (m >= ItemGemIcons.VARIANTS.length) m = ItemGemIcons.VARIANTS.length - 1;
                String v = ItemGemIcons.VARIANTS[m];
                if (v != null && !v.trim().isEmpty()) return v.trim();
            }
        } catch (Throwable ignored) {}

        // Fallback: try registry name (modid:itemname) then unlocalized name.
        try {
            Object reg = Item.itemRegistry.getNameForObject(gem.getItem());
            if (reg != null) {
                String s = reg.toString();
                if (s.contains(":")) s = s.substring(s.indexOf(':') + 1);
                if (s != null && !s.trim().isEmpty()) return s.trim();
            }
        } catch (Throwable ignored) {}

        try {
            String u = gem.getUnlocalizedName();
            if (u != null && !u.trim().isEmpty()) return u.trim();
        } catch (Throwable ignored) {}

        return null;
    }

    /**
     * Normalize any already-stored gem keys in-place so the renderer always
     * receives canonical "gems/<token>" keys (no png, no items/, etc.).
     *
     * This is important because older builds (or other entry points) may have
     * written "pills/..." or raw tokens into the list.
     */
    private static void sanitizeGemKeysInPlace(ItemStack stack) {
        if (stack == null) return;

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(NBT_ROOT, 10)) return;

        NBTTagCompound root = tag.getCompoundTag(NBT_ROOT);
        if (root == null || !root.hasKey(NBT_GEMS, 9)) return;

        NBTTagList gems = root.getTagList(NBT_GEMS, 8);
        if (gems == null) return;

        NBTTagList rebuilt = new NBTTagList();

        for (int i = 0; i < gems.tagCount(); i++) {
            String k = gems.getStringTagAt(i);
            if (k == null) continue;
            k = k.trim();
            if (k.isEmpty()) continue;

            String canon = normalizeGemKey(k);
            if (canon == null) continue;
            canon = canon.trim();
            if (canon.isEmpty()) continue;

            // NOTE: duplicates are allowed (e.g., two flat gems, two % gems).
            // We only normalize the stored key so rendering/lore stays consistent.
            rebuilt.appendTag(new NBTTagString(canon));
        }

        root.setTag(NBT_GEMS, rebuilt);
    }


    /** Ensure the canonical HexGems structure exists so socketing doesn't fail on missing tags. */
    private static void ensureSocketDefaults(ItemStack stack) {
        if (stack == null) return;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }

        NBTTagCompound root;
        if (tag.hasKey("HexGems", 10)) {
            root = tag.getCompoundTag("HexGems");
        } else {
            root = new NBTTagCompound();
            tag.setTag("HexGems", root);
        }

        if (!root.hasKey("Gems", 9)) {
            root.setTag("Gems", new NBTTagList());
        }

        // If the open count is missing, leave it alone; we don't want to auto-open sockets.
        // (The open/filled pre-check in updateOutput determines readiness.)
    }

    /**
     * Fallback socketing path that appends to the canonical NBT list when API refuses.
     * Only used for preview/result generation; it does NOT consume inputs.
     */


    /**
     * Preview-safe fallback that does NOT ask HexSocketAPI for open sockets.
     * We rely on the open value already computed for the real target stack in updateOutput().
     *
     * This fixes the common case where previews would be null because older builds
     * (or copied stacks) report open=0 via HexSocketAPI.getSocketsOpen(...) even though
     * the real item has open slots.
     */
    private static boolean fallbackSocketGemKnown(ItemStack stack, String normKey, int openKnown) {
        if (stack == null) return false;
        if (normKey == null) return false;

        normKey = normalizeGemKey(normKey);
        if (normKey == null) return false;
        normKey = normKey.trim();
        if (normKey.isEmpty()) return false;

        // Ensure HexGems root exists
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }

        NBTTagCompound root;
        if (tag.hasKey(NBT_ROOT, 10)) {
            root = tag.getCompoundTag(NBT_ROOT);
        } else {
            root = new NBTTagCompound();
            tag.setTag(NBT_ROOT, root);
        }

        // Ensure Gems list (STRING list)
        NBTTagList gems = root.hasKey(NBT_GEMS, 9) ? root.getTagList(NBT_GEMS, 8) : new NBTTagList();
        if (!root.hasKey(NBT_GEMS, 9)) root.setTag(NBT_GEMS, gems);

        // NOTE: duplicates are allowed (players can socket multiple of the same gem type).

        int filled = gems.tagCount();
        if (openKnown > 0 && openKnown <= filled) return false;

        gems.appendTag(new NBTTagString(normKey));

        // Make sure SocketsOpen exists (some render paths expect a concrete int tag).
        if (openKnown > 0) {
            int curOpen = nbtReadAnyInt(root, NBT_OPEN, 0);
            if (curOpen < openKnown) {
                try { root.setInteger(NBT_OPEN, openKnown); } catch (Throwable ignored) {}
            }
        }

        return true;
    }

    private static boolean fallbackSocketGem(ItemStack stack, String normKey) {
        if (stack == null) return false;
        if (normKey == null) return false;
        normKey = normKey.trim();
        if (normKey.isEmpty()) return false;

        // Make sure the HexGems root exists (preview copies may be missing it).
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }

        NBTTagCompound root;
        if (tag.hasKey(NBT_ROOT, 10)) {
            root = tag.getCompoundTag(NBT_ROOT);
        } else {
            root = new NBTTagCompound();
            tag.setTag(NBT_ROOT, root);
        }

        // Ensure gems list
        NBTTagList gems = root.hasKey(NBT_GEMS, 9) ? root.getTagList(NBT_GEMS, 8) : new NBTTagList();
        if (!root.hasKey(NBT_GEMS, 9)) root.setTag(NBT_GEMS, gems);

        // IMPORTANT:
        // Some builds derive "open sockets" via API logic (or other sources) and may not
        // physically store SocketsOpen in NBT yet. So we MUST NOT rely only on the NBT int.
        int open = 0;
        try {
            open = HexSocketAPI.getSocketsOpen(stack);
        } catch (Throwable ignored) {
            open = nbtGetSocketsOpen(stack);
        }
        if (open <= 0) {
            // last-ditch read (won't hurt if absent)
            open = nbtReadAnyInt(root, NBT_OPEN, 0);
        }

        int filled = gems.tagCount();
        if (open <= filled) return false;

        gems.appendTag(new NBTTagString(normKey));
        return true;
    }


    // Reads an integer-like value regardless of underlying numeric tag type (byte/short/int/long/float/double).
    private static int nbtReadAnyInt(NBTTagCompound c, String key, int def) {
        if (c == null || key == null || key.isEmpty() || !c.hasKey(key)) return def;
        try {
            NBTBase base = c.getTag(key);
            if (base instanceof NBTTagByte)   return ((NBTTagByte) base).func_150290_f();
            if (base instanceof NBTTagShort)  return ((NBTTagShort) base).func_150289_e();
            if (base instanceof NBTTagInt)    return ((NBTTagInt) base).func_150287_d();
            if (base instanceof NBTTagLong)   return (int) ((NBTTagLong) base).func_150291_c();
            if (base instanceof NBTTagFloat)  return (int) ((NBTTagFloat) base).func_150288_h();
            if (base instanceof NBTTagDouble) return (int) ((NBTTagDouble) base).func_150286_g();
        } catch (Throwable ignored) {
        }
        return def;
    }

    private static int nbtGetSocketsOpen(ItemStack stack) {
        try {
            if (stack == null) return 0;
            NBTTagCompound tag = stack.getTagCompound();
            if (tag == null || !tag.hasKey("HexGems", 10)) return 0;
            NBTTagCompound root = tag.getCompoundTag("HexGems");
            return nbtReadAnyInt(root, "SocketsOpen", 0);
        } catch (Throwable err) {
            return 0;
        }
    }

    private static int nbtGetSocketsFilled(ItemStack stack) {
        try {
            if (stack == null) return 0;
            NBTTagCompound tag = stack.getTagCompound();
            if (tag == null || !tag.hasKey("HexGems", 10)) return 0;
            NBTTagCompound root = tag.getCompoundTag("HexGems");
            if (!root.hasKey("Gems", 9)) return 0;
            return root.getTagList("Gems", 8).tagCount();
        } catch (Throwable err) {
            return 0;
        }
    }

    /**
     * Returns gem key candidates for HexSocketAPI.socketGem(...).
     *
     * We dedupe while preserving order.
     *
     * Typical expected format: "gems/<name>" (no .png)
     * But we also try:
     *  - raw variant (if it already contains a folder)
     *  - "pills/<name>" for pill_* variants
     */
    private static List<String> gemKeyCandidatesFromStack(ItemStack gem) {
        ArrayList<String> out = new ArrayList<String>();
        if (gem == null) return out;

        Item item = gem.getItem();
        if (item == null) return out;

        LinkedHashSet<String> set = new LinkedHashSet<String>();

        // 1) If the gem item already carries an explicit key, use it.
        try {
            if (gem.hasTagCompound()) {
                NBTTagCompound tag = gem.getTagCompound();
                if (tag != null && tag.hasKey(NBT_ROOT, 10)) {
                    NBTTagCompound root = tag.getCompoundTag(NBT_ROOT);
                    if (root != null && root.hasKey("GemKey", 8)) {
                        String k = root.getString("GemKey");
                        if (k != null && !k.trim().isEmpty()) set.add(k.trim());
                    }
                }
                if (tag != null && tag.hasKey("GemKey", 8)) {
                    String k2 = tag.getString("GemKey");
                    if (k2 != null && !k2.trim().isEmpty()) set.add(k2.trim());
                }
            }
        } catch (Throwable ignored) {}

        // 2) Registry name (server-safe) like "hexcolorcodes:hex_gemorb_gem_violet_void_64"
        try {
            Object rl = GameData.getItemRegistry().getNameForObject(item);
            if (rl != null) {
                String s = String.valueOf(rl);
                if (!s.trim().isEmpty()) {
                    set.add(s.trim());
                    int c = s.indexOf(':');
                    if (c >= 0 && c < s.length() - 1) set.add(s.substring(c + 1));
                }
            }
        } catch (Throwable ignored) {}

        // 3) Unlocalized names (often variant-specific on server)
        try {
            String u = item.getUnlocalizedName(gem);
            if (u != null && !u.trim().isEmpty()) set.add(u.trim());
        } catch (Throwable ignored) {}
        try {
            String u0 = item.getUnlocalizedName();
            if (u0 != null && !u0.trim().isEmpty()) set.add(u0.trim());
        } catch (Throwable ignored) {}

        // 4) If this is the icon/meta item, try VARIANTS (client may have it; server might not)
        if (item instanceof ItemGemIcons) {
            try {
                int meta = gem.getItemDamage();
                if (meta < 0) meta = 0;

                String[] variants = ItemGemIcons.VARIANTS; // may throw on server if @SideOnly
                if (variants != null && variants.length > 0) {
                    if (meta >= variants.length) meta = variants.length - 1;
                    String raw = variants[meta];
                    if (raw != null) {
                        raw = raw.trim();
                        if (!raw.isEmpty()) {
                            set.add(raw);
                            if (raw.endsWith(".png")) set.add(raw.substring(0, raw.length() - 4));
                            if (raw.startsWith("gems/")) set.add(raw.substring("gems/".length()));
                            if (raw.startsWith("pills/")) set.add(raw.substring("pills/".length()));
                        }
                    }
                }
            } catch (Throwable ignored) {
                // ignore (server often can't see VARIANTS)
            }
        }


// Server-safe fallbacks: on a dedicated server the reflective VARIANTS path may be unavailable.
// Try (in order): explicit NBT key, meta-based unlocalized name, then substring heuristics.
        if (set.isEmpty()) {
            try {
                // 1) NBT explicit (if we ever decide to stamp it onto the item)
                if (gem.hasTagCompound()) {
                    NBTTagCompound t = gem.getTagCompound();
                    if (t != null && t.hasKey("HexGemKey", 8)) {
                        String k = t.getString("HexGemKey");
                        if (k != null && !k.trim().isEmpty()) set.add(k.trim());
                    }
                }
            } catch (Throwable ignored) {}

            try {
                // 2) Unlocalized name often includes the variant (works server-side)
                String unloc = null;
                try { unloc = gem.getItem().getUnlocalizedName(gem); } catch (Throwable ignored) {}
                if (unloc == null) {
                    try { unloc = gem.getUnlocalizedName(); } catch (Throwable ignored) {}
                }
                if (unloc != null) {
                    if (unloc.startsWith("item.")) unloc = unloc.substring(5);
                    else if (unloc.startsWith("tile.")) unloc = unloc.substring(5);

                    // If it still contains dots, keep only the last segment
                    int d = unloc.lastIndexOf('.');
                    if (d >= 0) unloc = unloc.substring(d + 1);

                    String derived = deriveKeyFromUnlocId(unloc);
                    if (derived != null && !derived.trim().isEmpty()) {
                        set.add(derived.trim());
                    }
                }
            } catch (Throwable ignored) {}
        }

// Expand/normalize fallbacks too (strip .png, add/remove folder prefix).
        if (!set.isEmpty()) {
            ArrayList<String> extra = new ArrayList<String>();
            for (String raw : set) {
                if (raw == null) continue;
                raw = raw.trim();
                if (raw.isEmpty()) continue;

                if (raw.endsWith(".png")) extra.add(raw.substring(0, raw.length() - 4));
                if (raw.startsWith("gems/")) extra.add(raw.substring("gems/".length()));
                if (raw.startsWith("pills/")) extra.add(raw.substring("pills/".length()));
            }
            set.addAll(extra);
        }
        out.addAll(set);

        // Remove empty
        for (int i = out.size() - 1; i >= 0; i--) {
            if (out.get(i) == null || out.get(i).trim().isEmpty()) out.remove(i);
        }
        return out;
    }

    private static int safeGetOpen(ItemStack s) {
        try {
            return HexSocketAPI.getSocketsOpen(s);
        } catch (Throwable err) {
            return nbtGetSocketsOpen(s);
        }
    }
    private static int safeGetFilled(ItemStack s) {
        try {
            return HexSocketAPI.getSocketsFilled(s);
        } catch (Throwable err) {
            return nbtGetSocketsFilled(s);
        }
    }

    /**
     * Output slot was taken -> consume the inputs.
     * The taken stack is already the preview stack (socketed) produced in updateOutput().
     */
    private void consumeInputsAfterTake() {
        ItemStack target = input.getStackInSlot(SLOT_TARGET);
        ItemStack gem    = input.getStackInSlot(SLOT_GEM);

        if (target == null || gem == null) return;
        if (!(gem.getItem() instanceof ItemGemIcons)) return;

        // Consume inputs
        input.setInventorySlotContents(SLOT_TARGET, null);

        gem.stackSize -= 1;
        if (gem.stackSize <= 0) gem = null;
        input.setInventorySlotContents(SLOT_GEM, gem);

        // Clear preview key + output slot, then recompute preview.
        previewGemKey = "";
        output.setInventorySlotContents(0, null);

        // Put nothing in output directly; the player already took it.
        // Just recompute preview.
        updateOutput();
        detectAndSendChanges();
    }

    @Override
    public void onContainerClosed(EntityPlayer player) {
        super.onContainerClosed(player);

        // Return any items left in input to player (server-side)
        if (!player.worldObj.isRemote) {
            for (int i = 0; i < 2; i++) {
                ItemStack s = input.getStackInSlotOnClosing(i);
                if (s != null) {
                    if (!player.inventory.addItemStackToInventory(s)) {
                        player.dropPlayerItemWithRandomChoice(s, false);
                    }
                }
            }
        }
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        ItemStack copied = null;
        Slot slot = (Slot) this.inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) return null;

        ItemStack stack = slot.getStack();
        copied = stack.copy();

        // container slots: 0..2, player inventory starts at 3
        if (index == SLOT_OUTPUT) {
            // Shift-click output -> move to player inventory
            // (SlotOutput will consume inputs via onPickupFromSlot)
            if (!this.mergeItemStack(stack, 3, this.inventorySlots.size(), true)) return null;
            slot.onSlotChange(stack, copied);
        } else if (index >= 3) {
            // From player inventory -> try put into appropriate input slot
            if (stack.getItem() instanceof ItemGemIcons) {
                if (!this.mergeItemStack(stack, SLOT_GEM, SLOT_GEM + 1, false)) return null;
            } else {
                if (!this.mergeItemStack(stack, SLOT_TARGET, SLOT_TARGET + 1, false)) return null;
            }
        } else {
            // From input slots -> move to player inventory
            if (!this.mergeItemStack(stack, 3, this.inventorySlots.size(), true)) return null;
        }

        if (stack.stackSize == 0) slot.putStack(null);
        else slot.onSlotChanged();

        if (stack.stackSize == copied.stackSize) return null;

        slot.onPickupFromSlot(player, stack);
        return copied;
    }

    private class SlotOutput extends Slot {
        public SlotOutput(EntityPlayer player, IInventory inv, int idx, int x, int y) {
            super(inv, idx, x, y);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return false;
        }

        @Override
        public boolean canTakeStack(EntityPlayer player) {
            // Basic gate: only allow taking if inputs exist and the output preview is present
            return this.getHasStack()
                    && input.getStackInSlot(SLOT_TARGET) != null
                    && input.getStackInSlot(SLOT_GEM) != null
                    && super.canTakeStack(player);
        }

        @Override
        public void onPickupFromSlot(EntityPlayer player, ItemStack stack) {
            // Server side only
            if (!player.worldObj.isRemote) {
                // Consume inputs when output is taken
                consumeInputsAfterTake();
            }

            super.onPickupFromSlot(player, stack);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Gem bonus + attribute carry-over helpers (Socket Station)
    // ─────────────────────────────────────────────────────────────

    private static class SocketBonus {
        public final String attrKey;
        public final String displayName;
        public final double amount;

        private SocketBonus(String attrKey, String displayName, double amount) {
            this.attrKey = attrKey == null ? "" : attrKey;
            this.displayName = displayName == null ? "" : displayName;
            this.amount = amount;
        }
    }

    /**
     * Pull the rolled bonus out of the gem stack. Supports the NBT written by HexOrbRoller:
     *  - HexOrbRolls (compound of attrKey -> int roll)
     *  - RPGCore.Attributes (compound of attrKey -> float roll)
     */
    private static SocketBonus extractSocketBonusFromGem(ItemStack gem, String canonicalGemKey) {
        List<SocketBonus> list = extractSocketBonusesFromGem(gem, canonicalGemKey);
        if (list == null || list.isEmpty()) return null;
        return list.get(0);
    }

    private static List<SocketBonus> extractSocketBonusesFromGem(ItemStack gem, String canonicalGemKey) {
        List<SocketBonus> out = new ArrayList<SocketBonus>();
        if (gem == null) return out;

        // Prefer rolled NBT first (HexOrbRolls / RPGCore.Attributes), then fall back to lore parsing.
        try {
            NBTTagCompound tag = gem.getTagCompound();
            if (tag != null) {
                // 1) Preferred: your roller NBT (HexOrbRolls)
                if (tag.hasKey("HexOrbRolls", 10)) {
                    NBTTagCompound rolls = tag.getCompoundTag("HexOrbRolls");
                    Object[] keyObjs = rolls.func_150296_c().toArray();
                    String[] keys = new String[keyObjs.length];
                    for (int i = 0; i < keyObjs.length; i++) keys[i] = String.valueOf(keyObjs[i]);
                    if (keys.length > 0) {
                        java.util.Arrays.sort(keys);
                        for (int i = 0; i < keys.length; i++) {
                            String k = keys[i];
                            double raw;
                            try { raw = rolls.getDouble(k); } catch (Throwable t) { raw = 0d; }
                            if (raw == 0d) continue;
                            float amt = (float) normalizeBonusAmount(k, raw);
                            out.add(new SocketBonus(k, prettyAttrNameFromKey(k), amt));
                        }
                    }
                    if (!out.isEmpty()) return out;
                }

                // 2) Alternate: RPGCore attributes on the gem (older / script-generated orbs)
                if (tag.hasKey("RPGCore", 10)) {
                    NBTTagCompound rpg = tag.getCompoundTag("RPGCore");
                    if (rpg.hasKey("Attributes", 10)) {
                        NBTTagCompound attrs = rpg.getCompoundTag("Attributes");
                        Object[] keyObjs = attrs.func_150296_c().toArray();
                        String[] keys = new String[keyObjs.length];
                        for (int i = 0; i < keyObjs.length; i++) keys[i] = String.valueOf(keyObjs[i]);
                        if (keys.length > 0) {
                            java.util.Arrays.sort(keys);
                            for (int i = 0; i < keys.length; i++) {
                                String k = keys[i];
                                double raw;
                                try { raw = attrs.getDouble(k); } catch (Throwable t) { raw = 0d; }
                                if (raw == 0d) continue;
                                float amt = (float) normalizeBonusAmount(k, raw);
                                out.add(new SocketBonus(k, prettyAttrNameFromKey(k), amt));
                            }
                        }
                    }
                    if (!out.isEmpty()) return out;
                }
            }
        } catch (Throwable t) {
            // ignore and fall back to lore parsing
        }

        // 3) Fallback: parse HexLorePages (tooltip pager storage) for rolled stat lines.
        out.addAll(parseBonusesFromHexLorePages(gem));
        if (!out.isEmpty()) return out;

        // 4) Fallback: parse display lore lines.
        out.addAll(parseBonusesFromDisplayLore(gem));
        return out;
    }


    private static double normalizeBonusAmount(String attrKey, double raw) {
        if (attrKey == null) return raw;
        if (attrKey.endsWith(".Multi")) {
            // If stored as whole percent (5), convert to fractional (0.05) for socket-page formatting.
            if (Math.abs(raw) > 1.000001d) return raw / 100.0d;
        }
        return raw;
    }



    /**
     * Many of your gem orbs store their rolled stat line on a LorePages page (HexLorePages.Pages)
     * instead of display.Lore. This extracts the first stat line that looks like:
     *   Strength +25
     *   Spirit +8%
     */
    private static SocketBonus parseBonusFromHexLorePages(ItemStack gem) {
        List<SocketBonus> list = parseBonusesFromHexLorePages(gem);
        if (list == null || list.isEmpty()) return null;
        return list.get(0);
    }

    private static List<SocketBonus> parseBonusesFromHexLorePages(ItemStack gem) {
        List<SocketBonus> out = new ArrayList<SocketBonus>();
        if (gem == null) return out;
        try {
            NBTTagCompound tag = gem.getTagCompound();
            if (tag == null || !tag.hasKey("HexLorePages", 9)) return out;
            NBTTagList pages = tag.getTagList("HexLorePages", 10);
            for (int pi = 0; pi < pages.tagCount(); pi++) {
                NBTTagCompound page = pages.getCompoundTagAt(pi);
                if (page == null) continue;
                if (!page.hasKey("Lines", 9)) continue;
                NBTTagList lines = page.getTagList("Lines", 8);
                for (int li = 0; li < lines.tagCount(); li++) {
                    String line = lines.getStringTagAt(li);
                    SocketBonus b = parseBonusLine(line);
                    if (b == null) continue;
                    boolean seen = false;
                    for (int x = 0; x < out.size(); x++) {
                        if (out.get(x).attrKey.equals(b.attrKey)) { seen = true; break; }
                    }
                    if (!seen) out.add(b);
                }
            }
        } catch (Throwable t) {
            // ignore
        }
        return out;
    }


    /** Parse a single plain or formatted tooltip line into a SocketBonus. */
    private static SocketBonus parseBonusLine(String line) {
        if (line == null || line.isEmpty()) return null;
        String plain = stripStyleMarkup(stripMcColors(line)).trim();
        if (plain.isEmpty()) return null;

        // Match e.g. "Spirit +5%" or "Constitution -9".
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^([A-Za-z ]+?)\\s*([+-])\\s*(\\d+(?:\\.\\d+)?)\\s*(%)?$")
                .matcher(plain);
        if (!m.find()) return null;

        String statName = m.group(1).trim();
        String sign = m.group(2);
        double val = Double.parseDouble(m.group(3));
        if ("-".equals(sign)) val = -val;
        boolean isPct = (m.group(4) != null && !m.group(4).isEmpty());

        String attrKey = mapStatNameToAttrKey(statName, isPct);
        if (attrKey == null) return null;

        // Store whole percent for % lines (7% => 7). If it was already fractional (0.07), convert to 7.
        double amt = isPct ? (Math.abs(val) <= 1.0 ? (val * 100.0) : val) : val;
        return new SocketBonus(attrKey, statName, amt);
    }

    private static SocketBonus parseBonusFromDisplayLore(ItemStack gem) {
        List<SocketBonus> list = parseBonusesFromDisplayLore(gem);
        if (list == null || list.isEmpty()) return null;
        return list.get(0);
    }

    private static List<SocketBonus> parseBonusesFromDisplayLore(ItemStack gem) {
        List<SocketBonus> out = new ArrayList<SocketBonus>();
        if (gem == null) return out;
        try {
            NBTTagCompound tag = gem.getTagCompound();
            if (tag == null) return out;
            if (!tag.hasKey("display", 10)) return out;
            NBTTagCompound disp = tag.getCompoundTag("display");
            if (!disp.hasKey("Lore", 9)) return out;
            NBTTagList lore = disp.getTagList("Lore", 8);
            for (int i = 0; i < lore.tagCount(); i++) {
                String line = lore.getStringTagAt(i);
                SocketBonus b = parseBonusLine(line);
                if (b == null) continue;
                boolean seen = false;
                for (int x = 0; x < out.size(); x++) {
                    if (out.get(x).attrKey.equals(b.attrKey)) { seen = true; break; }
                }
                if (!seen) out.add(b);
            }
        } catch (Throwable t) {
            // ignore
        }
        return out;
    }


    private static String mapStatNameToAttrKey(String statName, boolean isPct) {
        if (statName == null) return null;
        String s = statName.trim().toLowerCase(java.util.Locale.ROOT);
        if (s.equals("strength")) return isPct ? "dbc.Strength.Multi" : "dbc.Strength";
        if (s.equals("dexterity")) return isPct ? "dbc.Dexterity.Multi" : "dbc.Dexterity";
        if (s.equals("constitution")) return isPct ? "dbc.Constitution.Multi" : "dbc.Constitution";
        if (s.equals("spirit")) return isPct ? "dbc.Spirit.Multi" : "dbc.Spirit";
        if (s.equals("willpower") || s.equals("will power")) return isPct ? "dbc.WillPower.Multi" : "dbc.WillPower";
        // Common alternate labels
        if (s.equals("will power")) return isPct ? "dbc.WillPower.Multi" : "dbc.WillPower";
        return null;
    }

    private static String stripMcColors(String s) {
        if (s == null) return "";
        // Remove vanilla '§' codes
        return s.replaceAll("\u00A7.", "");
    }

    private static String stripStyleMarkup(String s) {
        if (s == null) return "";
        // Remove <...> tags used by your HexFontRenderer style system
        return s.replaceAll("<[^>]+>", "");
    }



    private static String prettyAttrNameFromKey(String attrKey) {
        if (attrKey == null) return "";
        String k = attrKey.trim();
        if (k.endsWith(".Multi")) {
            k = k.substring(0, k.length() - ".Multi".length());
        }
        if (k.equalsIgnoreCase("dbc.Strength")) return "Strength";
        if (k.equalsIgnoreCase("dbc.Dexterity")) return "Dexterity";
        if (k.equalsIgnoreCase("dbc.Constitution")) return "Constitution";
        if (k.equalsIgnoreCase("dbc.Spirit")) return "Spirit";
        if (k.equalsIgnoreCase("dbc.WillPower")) return "Will Power";

        int dot = k.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < k.length()) {
            return k.substring(dot + 1);
        }
        return k;
    }

    /**
     * Merge RPGCore.Attributes from the gem into the target (additive).
     * If the gem doesn't have RPGCore but does have HexOrbRolls, those rolls are merged instead.
     */
    private static void mergeRpgCoreAttributesAdd(ItemStack target, ItemStack gem) {
        if (target == null || gem == null) return;

        NBTTagCompound gTag = gem.getTagCompound();
        if (gTag == null) return;

        // Source attributes
        NBTTagCompound src = null;
        if (gTag.hasKey("RPGCore", 10)) {
            NBTTagCompound rpg = gTag.getCompoundTag("RPGCore");
            if (rpg.hasKey("Attributes", 10)) {
                src = rpg.getCompoundTag("Attributes");
            }
        }
        if (src == null && gTag.hasKey("HexOrbRolls", 10)) {
            src = gTag.getCompoundTag("HexOrbRolls");
        }
        if (src == null) return;

        Set<String> keys = src.func_150296_c();
        if (keys == null || keys.isEmpty()) return;

        NBTTagCompound tTag = target.getTagCompound();
        if (tTag == null) tTag = new NBTTagCompound();

        NBTTagCompound tRpg;
        if (tTag.hasKey("RPGCore", 10)) {
            tRpg = tTag.getCompoundTag("RPGCore");
        } else {
            tRpg = new NBTTagCompound();
        }

        NBTTagCompound tAttrs;
        if (tRpg.hasKey("Attributes", 10)) {
            tAttrs = tRpg.getCompoundTag("Attributes");
        } else {
            tAttrs = new NBTTagCompound();
        }

        for (String k : keys) {
            if (k == null || k.length() == 0) continue;

            float add = 0f;
            try {
                // Handles float or int tags (int will coerce through getFloat in many mappings).
                add = src.getFloat(k);
                if (add == 0f) {
                    add = (float) src.getInteger(k);
                }
            } catch (Throwable ignored) {
                // ignore
            }

            if (add == 0f) continue;

            float cur = 0f;
            try {
                if (tAttrs.hasKey(k, 99)) {
                    cur = tAttrs.getFloat(k);
                }
            } catch (Throwable ignored) {
                // ignore
            }

            tAttrs.setFloat(k, cur + add);
        }

        tRpg.setTag("Attributes", tAttrs);
        tTag.setTag("RPGCore", tRpg);
        target.setTagCompound(tTag);
    }
    /**
     * Additively merge a single attribute into an RPGCore.Attributes compound.
     *
     * We store as float for consistency with the rest of this container and with typical RPGCore usage.
     */
    private static void mergeAttributeAdd(NBTTagCompound attrs, String key, double add) {
        if (attrs == null) return;
        if (key == null || key.length() == 0) return;

        float cur = 0f;
        try {
            if (attrs.hasKey(key, 99)) {
                // Handles float/double/int tags.
                cur = attrs.getFloat(key);
                if (cur == 0f) {
                    cur = (float) attrs.getDouble(key);
                }
            }
        } catch (Throwable ignored) {
            // ignore
        }

        attrs.setFloat(key, cur + (float) add);
    }

    /**
     * Some gems/orbs are authored only via tooltip/lore lines (no RPGCore or HexOrbRolls tag).
     * If we successfully parsed at least one bonus line for display, also write it into the
     * target's RPGCore.Attributes so the bonus exists on the resulting item.
     */
    private static void applySocketBonusToRpgCoreIfNeeded(ItemStack target, ItemStack gem, SocketBonus bonus) {
        if (bonus == null) return;
        List<SocketBonus> list = new ArrayList<SocketBonus>();
        list.add(bonus);
        applySocketBonusesToRpgCoreIfNeeded(target, gem, list);
    }

    private static void applySocketBonusesToRpgCoreIfNeeded(ItemStack target, ItemStack gem, List<SocketBonus> bonuses) {
        if (target == null || gem == null || bonuses == null || bonuses.isEmpty()) return;
        try {
            NBTTagCompound gt = gem.getTagCompound();
            if (gt == null) return;

            NBTTagCompound it = target.getTagCompound();
            if (it == null) { it = new NBTTagCompound(); target.setTagCompound(it); }

            NBTTagCompound rpg = it.hasKey("RPGCore", 10) ? it.getCompoundTag("RPGCore") : new NBTTagCompound();
            NBTTagCompound attrs = rpg.hasKey("Attributes", 10) ? rpg.getCompoundTag("Attributes") : new NBTTagCompound();

            for (int bi = 0; bi < bonuses.size(); bi++) {
                SocketBonus b = bonuses.get(bi);
                if (b == null || b.attrKey == null || b.attrKey.length() == 0) continue;

                boolean gemHas = false;
                try {
                    if (gt.hasKey("HexOrbRolls", 10)) {
                        NBTTagCompound rolls = gt.getCompoundTag("HexOrbRolls");
                        gemHas = rolls != null && rolls.hasKey(b.attrKey);
                    }
                    if (!gemHas && gt.hasKey("RPGCore", 10)) {
                        NBTTagCompound gr = gt.getCompoundTag("RPGCore");
                        if (gr != null && gr.hasKey("Attributes", 10)) {
                            NBTTagCompound ga = gr.getCompoundTag("Attributes");
                            gemHas = ga != null && ga.hasKey(b.attrKey);
                        }
                    }
                } catch (Throwable t) {
                    gemHas = false;
                }
                if (gemHas) continue;

                mergeAttributeAdd(attrs, b.attrKey, b.amount);
            }

            rpg.setTag("Attributes", attrs);
            it.setTag("RPGCore", rpg);
        } catch (Throwable t) {
            // ignore
        }
    }




    // ─────────────────────────────────────────────────────────────
    // Lore/page refresh helpers (Socket Station)
    // ─────────────────────────────────────────────────────────────

    /**
     * If the runtime server jar is missing HexSocketAPI.setBonusAt(...) / socketGemWithBonus(...),
     * the resulting item will still have the RPGCore attribute applied but the Sockets page has
     * nothing to print. This fallback stores the bonus under HexGems.Bonuses so we can build a
     * visible confirmation line in the tooltip.
     */
    private static void tryStoreSocketBonusFallback(ItemStack stack, int slotIdx, SocketBonus bonus) {
        if (stack == null || bonus == null) return;
        if (slotIdx < 0) slotIdx = 0;

        // If the API already stored bonus data, don't overwrite it.
        try {
            NBTTagCompound tag = stack.getTagCompound();
            if (tag != null && tag.hasKey(NBT_ROOT, 10)) {
                NBTTagCompound root = tag.getCompoundTag(NBT_ROOT);
                if (root != null && root.hasKey("Bonuses", 9)) {
                    NBTTagList existing = root.getTagList("Bonuses", 10);
                    if (existing != null && existing.tagCount() > slotIdx) {
                        NBTTagCompound ex = existing.getCompoundTagAt(slotIdx);
                        if (ex != null && (ex.hasKey("K", 8) || ex.hasKey("Key", 8) || ex.hasKey("Attr", 8))) {
                            return;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // fall through
        }

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }

        NBTTagCompound root = tag.hasKey(NBT_ROOT, 10) ? tag.getCompoundTag(NBT_ROOT) : new NBTTagCompound();
        if (!tag.hasKey(NBT_ROOT, 10)) tag.setTag(NBT_ROOT, root);

        // Build/resize Bonuses as a TAG_List of TAG_Compound.
        NBTTagList bonuses;
        try {
            bonuses = root.hasKey("Bonuses", 9) ? root.getTagList("Bonuses", 10) : new NBTTagList();
        } catch (Throwable t) {
            bonuses = new NBTTagList();
        }

        int need = slotIdx + 1;
        if (bonuses.tagCount() < need) {
            for (int i = bonuses.tagCount(); i < need; i++) {
                bonuses.appendTag(new NBTTagCompound());
            }
        }

        // Replace by rebuilding (NBTTagList has awkward setters in 1.7.10 mappings).
        NBTTagList rebuilt = new NBTTagList();
        for (int i = 0; i < bonuses.tagCount(); i++) {
            if (i == slotIdx) {
                NBTTagCompound b = new NBTTagCompound();
                b.setString("K", bonus.attrKey);
                b.setString("N", bonus.displayName);
                b.setDouble("V", bonus.amount);
                rebuilt.appendTag(b);
            } else {
                rebuilt.appendTag(bonuses.getCompoundTagAt(i));
            }
        }

        root.setTag("Bonuses", rebuilt);
        tag.setTag(NBT_ROOT, root);
        stack.setTagCompound(tag);
    }

    /**
     * Rebuild/patch the sockets lore pages so players can SEE the rolled value
     * in the output preview and on the final item.
     */
    private static void refreshSocketLorePagesForStation(ItemStack stack) {
        if (stack == null) return;

        // Prefer the API if the method exists in this runtime jar.
        if (invokeHexSocketAPINoReturn("rebuildSocketsPage", stack)) return;
        if (invokeHexSocketAPINoReturn("rebuildSocketPage", stack)) return;
        if (invokeHexSocketAPINoReturn("rebuildSocketLorePages", stack)) return;
        if (invokeHexSocketAPINoReturn("rebuildSocketsLorePages", stack)) return;
        if (invokeHexSocketAPINoReturn("rebuildLorePages", stack)) return;
        if (invokeHexSocketAPINoReturn("syncLorePages", stack)) return;
        if (invokeHexSocketAPINoReturn("refreshLorePages", stack)) return;

        // Fallback: patch the existing Sockets page in HexLorePages.
        patchSocketBonusesIntoHexLorePages(stack);
    }

    private static boolean invokeHexSocketAPINoReturn(String methodName, ItemStack stack) {
        try {
            Method m = HexSocketAPI.class.getMethod(methodName, ItemStack.class);
            m.invoke(null, stack);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Minimal, safe patch: keep the existing sockets art/layout, just append a Bonus section.
     * Supports both new (Pages: List<Compound{L:List<String>}>) and legacy (Pages: List<String>) formats.
     */
    /**
     * Minimal, safe patch: keep the existing sockets art/layout, but inject each socket's rolled
     * stat/value onto the existing "Sockets:" lines (so it shows like your inline example), and
     * remove any previous "Bonuses:" block that may have been injected by older station builds.
     *
     * Supports both new (Pages: List<Compound{L:List<String>}>) and legacy (Pages: List<String>) formats.
     */
    private static void patchSocketBonusesIntoHexLorePages(ItemStack stack) {
        try {
            List<String> slotBonus = buildSocketBonusLines(stack); // 1 line per filled socket, already styled
            if (slotBonus == null || slotBonus.isEmpty()) return;

            NBTTagCompound tag = stack.getTagCompound();
            if (tag == null) {
                tag = new NBTTagCompound();
                stack.setTagCompound(tag);
            }

            NBTTagCompound lp = tag.hasKey("HexLorePages", 10) ? tag.getCompoundTag("HexLorePages") : new NBTTagCompound();
            if (!tag.hasKey("HexLorePages", 10)) tag.setTag("HexLorePages", lp);

            if (lp.hasKey("Pages", 9)) {
                // ── New format: Pages is a TAG_List of TAG_Compound, each containing L: TAG_List of TAG_String.
                try {
                    NBTTagList pagesNew = lp.getTagList("Pages", 10);
                    if (pagesNew != null && pagesNew.tagCount() > 0) {
                        int pageIdx = findSocketsPageIndexNew(pagesNew);
                        if (pageIdx < 0) pageIdx = 0;

                        NBTTagCompound page = pagesNew.getCompoundTagAt(pageIdx);
                        if (page == null) page = new NBTTagCompound();

                        NBTTagList lines = page.getTagList("L", 8);
                        ArrayList<String> rebuilt = new ArrayList<String>();

                        int socketLine = 0;
                        boolean insertedSocketsBlock = false;
                        int skipAfterBonusHeader = 0;
                        for (int i = 0; i < lines.tagCount(); i++) {
                            String s = lines.getStringTagAt(i);
                            if (s == null) s = "";

                            if (skipAfterBonusHeader > 0) {
                                skipAfterBonusHeader--;
                                continue;
                            }

                            if (isBonusHeaderLine(s)) {
                                // remove the blank spacer we inserted before the old block
                                if (!rebuilt.isEmpty()) {
                                    String last = rebuilt.get(rebuilt.size() - 1);
                                    if (last != null && stripStyleMarkup(stripMcColors(last)).trim().isEmpty()) {
                                        rebuilt.remove(rebuilt.size() - 1);
                                    }
                                }
                                // skip old injected lines (best-effort: one line per filled socket)
                                skipAfterBonusHeader = slotBonus.size();
                                continue;
                            }

                            if (looksLikeSocketsHeader(s)) {
                                // Some socket pages only have ONE "Sockets:" header line.
                                // We want ONE line per filled socket, so expand the block here.
                                if (!insertedSocketsBlock) {
                                    for (int si = 0; si < slotBonus.size(); si++) {
                                        String b = slotBonus.get(si);
                                        String line = "Sockets:";
                                        if (b != null && !b.trim().isEmpty()) line += " " + b;
                                        rebuilt.add(line);
                                    }
                                    insertedSocketsBlock = true;
                                    socketLine = slotBonus.size();
                                }
                                // Skip any additional/duplicate "Sockets:" header lines already in the page.
                                continue;
                            }

                            rebuilt.add(s);
                        }

                        // If we didn't find any socket headers but we have bonuses, prepend a minimal sockets block.
                        if (socketLine == 0) {
                            ArrayList<String> withSockets = new ArrayList<String>();
                            for (int i = 0; i < slotBonus.size(); i++) {
                                String b = slotBonus.get(i);
                                String line = "Sockets:";
                                if (b != null && !b.trim().isEmpty()) line += " " + b;
                                withSockets.add(line);
                            }
                            withSockets.addAll(rebuilt);
                            rebuilt = withSockets;
                        }

                        NBTTagList rebuiltLines = new NBTTagList();
                        for (String s : rebuilt) rebuiltLines.appendTag(new NBTTagString(s));
                        page.setTag("L", rebuiltLines);

                        // rebuild pages list with the updated page
                        NBTTagList rebuiltPages = new NBTTagList();
                        for (int i = 0; i < pagesNew.tagCount(); i++) {
                            if (i == pageIdx) rebuiltPages.appendTag(page);
                            else rebuiltPages.appendTag(pagesNew.getCompoundTagAt(i));
                        }

                        lp.setTag("Pages", rebuiltPages);
                        tag.setTag("HexLorePages", lp);
                        stack.setTagCompound(tag);
                        return;
                    }
                } catch (Throwable ignored) {
                    // fall through
                }

                // ── Legacy format: Pages is a TAG_List of TAG_String with newlines.
                try {
                    NBTTagList pagesLegacy = lp.getTagList("Pages", 8);
                    if (pagesLegacy != null && pagesLegacy.tagCount() > 0) {
                        int pageIdx = findSocketsPageIndexLegacy(pagesLegacy);
                        if (pageIdx < 0) pageIdx = 0;

                        String pageText = pagesLegacy.getStringTagAt(pageIdx);
                        if (pageText == null) pageText = "";

                        String[] arr = pageText.split("\\r?\\n");
                        ArrayList<String> rebuilt = new ArrayList<String>();

                        int socketLine = 0;
                        boolean insertedSocketsBlock = false;
                        int skipAfterBonusHeader = 0;
                        for (int i = 0; i < arr.length; i++) {
                            String s = arr[i] == null ? "" : arr[i];

                            if (skipAfterBonusHeader > 0) {
                                skipAfterBonusHeader--;
                                continue;
                            }

                            if (isBonusHeaderLine(s)) {
                                if (!rebuilt.isEmpty()) {
                                    String last = rebuilt.get(rebuilt.size() - 1);
                                    if (last != null && stripStyleMarkup(stripMcColors(last)).trim().isEmpty()) {
                                        rebuilt.remove(rebuilt.size() - 1);
                                    }
                                }
                                skipAfterBonusHeader = slotBonus.size();
                                continue;
                            }

                            if (looksLikeSocketsHeader(s)) {
                                // Some socket pages only have ONE "Sockets:" header line.
                                // We want ONE line per filled socket, so expand the block here.
                                if (!insertedSocketsBlock) {
                                    for (int si = 0; si < slotBonus.size(); si++) {
                                        String b = slotBonus.get(si);
                                        String line = "Sockets:";
                                        if (b != null && !b.trim().isEmpty()) line += " " + b;
                                        rebuilt.add(line);
                                    }
                                    insertedSocketsBlock = true;
                                    socketLine = slotBonus.size();
                                }
                                // Skip any additional/duplicate "Sockets:" header lines already in the page.
                                continue;
                            }

                            rebuilt.add(s);
                        }

                        if (socketLine == 0) {
                            ArrayList<String> withSockets = new ArrayList<String>();
                            for (int i = 0; i < slotBonus.size(); i++) {
                                String b = slotBonus.get(i);
                                String line = "Sockets:";
                                if (b != null && !b.trim().isEmpty()) line += " " + b;
                                withSockets.add(line);
                            }
                            withSockets.addAll(rebuilt);
                            rebuilt = withSockets;
                        }

                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < rebuilt.size(); i++) {
                            if (i > 0) sb.append("\\n");
                            sb.append(rebuilt.get(i));
                        }

                        NBTTagList rebuiltPages = new NBTTagList();
                        for (int i = 0; i < pagesLegacy.tagCount(); i++) {
                            if (i == pageIdx) rebuiltPages.appendTag(new NBTTagString(sb.toString()));
                            else rebuiltPages.appendTag(new NBTTagString(pagesLegacy.getStringTagAt(i)));
                        }

                        lp.setTag("Pages", rebuiltPages);
                        tag.setTag("HexLorePages", lp);
                        stack.setTagCompound(tag);
                        return;
                    }
                } catch (Throwable ignored) {
                    // fall through
                }
            }

            // No pages: create a small sockets block (inline style).
            NBTTagList newPages = new NBTTagList();
            NBTTagCompound page0 = new NBTTagCompound();
            NBTTagList l0 = new NBTTagList();
            for (int i = 0; i < slotBonus.size(); i++) {
                String b = slotBonus.get(i);
                String line = "Sockets:";
                if (b != null && !b.trim().isEmpty()) line += " " + b;
                l0.appendTag(new NBTTagString(line));
            }
            page0.setTag("L", l0);
            newPages.appendTag(page0);
            lp.setTag("Pages", newPages);
            tag.setTag("HexLorePages", lp);
            stack.setTagCompound(tag);

        } catch (Throwable ignored) {
            // ignore
        }
    }


    private static int findSocketsPageIndexNew(NBTTagList pagesNew) {
        if (pagesNew == null) return -1;
        for (int i = 0; i < pagesNew.tagCount(); i++) {
            NBTTagCompound page = pagesNew.getCompoundTagAt(i);
            if (page == null) continue;
            NBTTagList lines = page.getTagList("L", 8);
            if (lines == null) continue;
            for (int j = 0; j < lines.tagCount(); j++) {
                String s = lines.getStringTagAt(j);
                if (looksLikeSocketsHeader(s)) return i;
            }
        }
        return -1;
    }

    private static int findSocketsPageIndexLegacy(NBTTagList pagesLegacy) {
        if (pagesLegacy == null) return -1;
        for (int i = 0; i < pagesLegacy.tagCount(); i++) {
            String txt = pagesLegacy.getStringTagAt(i);
            if (txt == null) continue;
            String[] lines = txt.split("\\n|\\r?\\n");
            for (int j = 0; j < lines.length; j++) {
                if (looksLikeSocketsHeader(lines[j])) return i;
            }
        }
        return -1;
    }

    private static boolean looksLikeSocketsHeaderLine(String s) {
        return looksLikeSocketsHeader(s);
    }

    private static boolean looksLikeSocketsHeader(String s) {
        if (s == null) return false;

        // Safety: Only treat lines that already contain socket icon tokens as the generated sockets page.
        // This prevents accidental patching of help/guide pages that mention "Sockets:" in plain text.
        if (!(s.contains("<ico:") || s.toLowerCase(java.util.Locale.ROOT).contains("socket_empty"))) return false;

        String p = stripStyleMarkup(stripMcColors(s)).trim().toLowerCase(java.util.Locale.ROOT);
        return p.equals("sockets:") || p.startsWith("sockets:");
    }

    private static boolean isBonusHeaderLine(String s) {
        if (s == null) return false;
        String p = stripStyleMarkup(stripMcColors(s)).trim().toLowerCase(java.util.Locale.ROOT);
        return p.equals("bonuses:") || p.equals("bonus:") || p.startsWith("bonuses:") || p.startsWith("bonus:");
    }

    // ---------------------------------------------------------------------
    // Lore styling (match HexOrbRoller.java so your renderer shows the same gradients)
    // ---------------------------------------------------------------------
    private static final String G_FIERY_OPEN     = "<grad #ff7a18 #ffd36b #ff3b00 #ff00aa scroll=0.28>";
    private static final String G_ICY_OPEN       = "<grad #3aa7ff #6ad6ff #baf3ff #f4feff scroll=0.22>";
    private static final String G_GOLDEN_OPEN    = "<grad #fff3b0 #ffd36b #ffb300 #fff7d6 scroll=0.20>";
    private static final String G_NATURE_OPEN    = "<grad #19ff74 #6dffb4 #00d66b #d8ffe8 scroll=0.22>";
    private static final String G_AETHER_OPEN    = "<grad #00ffd5 #36d1ff #7a5cff #e9ffff scroll=0.24>";
    private static final String G_VOID_OPEN      = "<grad #b36bff #7b2cff #36d1ff #ff4fd8 scroll=0.24>";
    private static final String G_ENERGIZED_OPEN = "<grad #ff4fd8 #36d1ff #ffe66d #7cff6b #7a5cff scroll=0.34>";
    // Dark Fire Pill theme (purple → magenta → ember)
    private static final String G_DARKFIRE_OPEN = "<grad #120015 #7b2cff #ff007e #ff3b00 scroll=0.26>";
    // Fractured theme (ice-white → bright blue)
    private static final String G_FRACTURED_OPEN = "<grad #f4feff #baf3ff #6ad6ff #3aa7ff scroll=0.22>";

    private static final String G_CLOSE          = "</grad>";

    /** Build per-socket text like: "<ico:gems/orb_gem_teal_aether_64> <grad ...>Spirit +1%</grad>" */
    private static String applyGemStyle(String canonicalGemKey, String text) {
        if (text == null) text = "";
        // If text already contains MC formatting or Hex tags, don't double-wrap.
        if (text.indexOf('§') >= 0 || text.indexOf('<') >= 0) return text;
        String open = gemGradientOpen(canonicalGemKey);
        if (open.length() == 0) return text;
        return open + text + G_CLOSE;
    }

    private static String gemGradientOpen(String canonicalGemKey) {
        if (canonicalGemKey == null) return "";
        String k = canonicalGemKey.toLowerCase();

        // IMPORTANT: check specific keys BEFORE broad matches like "fire" / "blue".
        if (k.contains("darkfire") || k.contains("pill_darkfire") || k.contains("dark_fire") || k.contains("dark fire")) return G_DARKFIRE_OPEN;
        if (k.contains("fractured") || k.contains("frac")) return G_FRACTURED_OPEN;

        if (k.contains("void")) return G_VOID_OPEN;

        if (k.contains("inferno") || k.contains("fire") || k.contains("orange")) return G_FIERY_OPEN;
        if (k.contains("frost") || k.contains("ice") || k.contains("blue")) return G_ICY_OPEN;
        if (k.contains("solar") || k.contains("gold") || k.contains("yellow")) return G_GOLDEN_OPEN;
        if (k.contains("nature") || k.contains("green")) return G_NATURE_OPEN;
        if (k.contains("aether") || k.contains("teal") || k.contains("cyan")) return G_AETHER_OPEN;
        if (k.contains("rainbow") || k.contains("swirly") || k.contains("energized")) return G_ENERGIZED_OPEN;
        return "";
    }

    private static List<String> buildSocketBonusLines(ItemStack stack) {
        ArrayList<String> out = new ArrayList<String>();
        if (stack == null) return out;

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(NBT_ROOT, 10)) return out;
        NBTTagCompound root = tag.getCompoundTag(NBT_ROOT);
        if (root == null) return out;

        if (!root.hasKey(NBT_GEMS, 9)) return out;
        NBTTagList gems = root.getTagList(NBT_GEMS, 8);
        if (gems == null || gems.tagCount() == 0) return out;

        NBTTagList bonuses = null;
        try {
            if (root.hasKey("Bonuses", 9)) bonuses = root.getTagList("Bonuses", 10);
        } catch (Throwable ignored) {
            bonuses = null;
        }

        for (int i = 0; i < gems.tagCount(); i++) {
            String gemKey = gems.getStringTagAt(i);
            if (gemKey == null || gemKey.trim().isEmpty()) continue;
            String canonGem = normalizeGemKey(gemKey);

            String attrKey = "";
            String name = "";
            double val = 0d;

            if (bonuses != null && bonuses.tagCount() > i) {
                NBTTagCompound b = bonuses.getCompoundTagAt(i);
                if (b != null) {
                    if (b.hasKey("K", 8)) attrKey = b.getString("K");
                    else if (b.hasKey("Key", 8)) attrKey = b.getString("Key");
                    else if (b.hasKey("Attr", 8)) attrKey = b.getString("Attr");

                    if (b.hasKey("N", 8)) name = b.getString("N");
                    else if (b.hasKey("Name", 8)) name = b.getString("Name");

                    try {
                        if (b.hasKey("V")) val = b.getDouble("V");
                        else if (b.hasKey("Val")) val = b.getDouble("Val");
                        else if (b.hasKey("Amount")) val = b.getDouble("Amount");
                    } catch (Throwable ignored) {
                        val = 0d;
                    }
                }
            }

            if (attrKey == null || attrKey.trim().isEmpty()) continue;
            if (name == null || name.trim().isEmpty()) name = prettyAttrNameFromKey(attrKey);

            // Special-case: Rainbow Energized orbs roll ALL 5 stats with a shared value; show it as "All Attributes"
            String _lcg = canonGem.toLowerCase(java.util.Locale.ROOT);
            if (_lcg.contains("rainbow_energized")) {
                name = "All Attributes";
            }

            String formatted = formatBonus(attrKey, val, name);
            if (formatted == null || formatted.trim().isEmpty()) continue;

            // IMPORTANT: include the socketed orb icon + styled stat/value (matches your tooltip examples).
            out.add("<ico:" + canonGem + "> " + applyGemStyle(canonGem, formatted));
        }

        return out;
    }

    private static String formatBonus(String attrKey, double rawVal, String name) {
        if (attrKey == null) return "";
        if (name == null) name = "";

        boolean isMulti = attrKey.endsWith(".Multi");

        // Normalize percent display.
        if (isMulti) {
            double pct = rawVal;
            if (Math.abs(pct) < 1.0d) pct = pct * 100.0d;
            String n = formatNumberClean(pct, 2);
            if (n == null || n.isEmpty()) return "";
            return name + " " + (pct >= 0 ? "+" : "") + n + "%";
        }

        String n = formatNumberClean(rawVal, 2);
        if (n == null || n.isEmpty()) return "";
        return name + " " + (rawVal >= 0 ? "+" : "") + n;
    }

    private static String formatNumberClean(double v, int maxDecimals) {
        // Prefer integer-looking output.
        double abs = Math.abs(v);
        if (Math.abs(v - Math.rint(v)) < 0.000001d) {
            return String.valueOf((long) Math.rint(v));
        }

        // Otherwise show up to maxDecimals, trim trailing zeros.
        java.math.BigDecimal bd = new java.math.BigDecimal(v);
        bd = bd.setScale(maxDecimals, java.math.RoundingMode.HALF_UP).stripTrailingZeros();
        String s = bd.toPlainString();
        // Guard against "-0"
        if (s.equals("-0")) s = "0";
        return s;
    }

}
