package com.example.examplemod.server;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Hex Orb Roller (MC 1.7.10 / Forge)
 *
 * What it does:
 *  - Rolls ONCE when a configured orb/pill enters a player's inventory
 *    (pickup OR creative grab) and injects lore with the rolled value.
 *  - Drops can still spawn "unrolled"; they roll when acquired.
 *
 * Notes:
 *  - Rolls are per ItemStack. If stackSize > 1, the whole stack shares one roll.
 *    If you want each orb unique, set maxStackSize=1 for these sub-items.
 *  - % values are stored as WHOLE percent units (12 == 12%).
 */
public class HexOrbRoller {

    // ---------------------------------------------------------------------
    // NBT keys
    // ---------------------------------------------------------------------
    private static final String TAG_ROLLED  = "HexOrbRolled";   // boolean
    private static final String TAG_PROFILE = "HexOrbProfile";  // string
    private static final String TAG_ROLLS   = "HexOrbRolls";    // compound of int per attr key

    private static final String TAG_LORE_DONE = "HexOrbLoreDone"; // boolean
    // Where your attribute system reads from (as in your screenshots)
    private static final String TAG_RPGCORE = "RPGCore";
    private static final String TAG_ATTRS   = "Attributes";

    // ---------------------------------------------------------------------
    // Lore styling (your renderer tags)
    // ---------------------------------------------------------------------
    private static final String G_FIERY_OPEN     = "<grad #ff7a18 #ffd36b #ff3b00 #ff00aa scroll=0.28>";
    private static final String G_ICY_OPEN       = "<grad #3aa7ff #6ad6ff #baf3ff #f4feff scroll=0.22>";
    private static final String G_GOLDEN_OPEN    = "<grad #fff3b0 #ffd36b #ffb300 #fff7d6 scroll=0.20>";
    private static final String G_NATURE_OPEN    = "<grad #19ff74 #6dffb4 #00d66b #d8ffe8 scroll=0.22>";
    private static final String G_AETHER_OPEN    = "<grad #00ffd5 #36d1ff #7a5cff #e9ffff scroll=0.24>";
    private static final String G_ENERGIZED_OPEN = "<grad #ff4fd8 #36d1ff #ffe66d #7cff6b #7a5cff scroll=0.34>";
    private static final String G_NEG_OPEN       = "<grad #7a00ff #ff4fd8 #120018 #7a5cff scroll=0.30>";
    private static final String G_CLOSE          = "</grad>";

    private static final String EFFECT_NA = "§7Effect: §8N/A";

    private static final String BONUS_NA  = "§7Bonus: §8N/A";
    // ---------------------------------------------------------------------
    // Drop behavior (optional)
    // ---------------------------------------------------------------------
    private static final boolean ENABLE_MOB_DROPS  = true;
    private static final boolean DROP_ONLY_HOSTILE = true;
    private static final float   DROP_CHANCE       = 0.038f; // 1.5%

    // ---------------------------------------------------------------------
    // Inventory scan (needed so creative-menu grabs roll on server)
    // ---------------------------------------------------------------------
    private static final int SCAN_EVERY_TICKS = 10;

    private final Item gemItem;

    // Your "energized" (swirly) metas: 18 = orb_gem_swirly_64, 19 = orb_gem_swirly_loop
    private final int META_SWIRLY_FLAT;
    private final int META_SWIRLY_ANIM;

    // Fire pills metas: 27 = pill_fire_textured_64 (flat), 26 = pill_fire_animated_64_anim (anim)
    private static final int META_PILL_FIRE_ANIM = 26;
    private static final int META_PILL_FIRE_FLAT = 27;

    // Negative orb metas: 12 = negative flat, 13 = negative animated (% variant)
    private static final int META_NEGATIVE_FLAT = 12;
    private static final int META_NEGATIVE_MULTI = 13;

    // Meta -> roll profile
    private final Map<Integer, RollProfile> metaProfiles = new HashMap<Integer, RollProfile>();

    // Throttle inventory scans
    private final Map<String, Integer> lastScanTick = new HashMap<String, Integer>();

    private final Random rng = new Random();

    // ---------------------------------------------------------------------
    // Constructors
    // ---------------------------------------------------------------------

    /**
     * Preferred constructor.
     * Uses ItemGemIcons metas: swirly flat=18, swirly anim=19.
     */
    public HexOrbRoller(Item gemItem) {
        this(gemItem, 18);
    }

    /**
     * Back-compat constructor if you want to override swirly meta.
     */
    public HexOrbRoller(Item gemItem, int metaSwirlyFlat) {
        this.gemItem = gemItem;
        this.META_SWIRLY_FLAT = metaSwirlyFlat;
        this.META_SWIRLY_ANIM = metaSwirlyFlat + 1;

        // Metas from ItemGemIcons.VARIANTS (as of your upload)
        //  0/1   = Frost
        //  6/7   = Solar
        //  8/9   = Nature
        // 12/13  = Negative
        // 14/15  = Inferno
        // 16/17  = Rainbow Energized
        // 18/19  = Swirly (your "Energized")
        // 20/21  = Aether
        // 26/27  = Fire pills

        // Stat-orbs: effect type is N/A for now (only show stats + roll)
        metaProfiles.put(14, RollProfiles.INFERNO_FLAT);
        metaProfiles.put(15, RollProfiles.INFERNO_MULTI);

        metaProfiles.put(0, RollProfiles.FROST_FLAT);
        metaProfiles.put(1, RollProfiles.FROST_MULTI);

        metaProfiles.put(6, RollProfiles.SOLAR_FLAT);
        metaProfiles.put(7, RollProfiles.SOLAR_MULTI);

        metaProfiles.put(8, RollProfiles.NATURE_FLAT);
        metaProfiles.put(9, RollProfiles.NATURE_MULTI);

        metaProfiles.put(META_NEGATIVE_FLAT, RollProfiles.NEGATIVE_FLAT);
        metaProfiles.put(META_NEGATIVE_MULTI, RollProfiles.NEGATIVE_MULTI);

        metaProfiles.put(20, RollProfiles.AETHER_FLAT);
        metaProfiles.put(21, RollProfiles.AETHER_MULTI);

        metaProfiles.put(16, RollProfiles.RAINBOW_ALL5_FLAT);
        metaProfiles.put(17, RollProfiles.RAINBOW_ALL5_MULTI);

        // Effect items (ONLY ones that have a real effect type right now)
        metaProfiles.put(META_SWIRLY_FLAT, RollProfiles.SWIRLY_EFFECT);
        metaProfiles.put(META_SWIRLY_ANIM, RollProfiles.SWIRLY_EFFECT);

        metaProfiles.put(META_PILL_FIRE_FLAT, RollProfiles.PILL_FIRE_EFFECT);
        metaProfiles.put(META_PILL_FIRE_ANIM, RollProfiles.PILL_FIRE_EFFECT);
    }

    // ---------------------------------------------------------------------
    // Events
    // ---------------------------------------------------------------------

    /**
     * Inject unrolled orb drops. They roll when acquired.
     */
    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        if (!ENABLE_MOB_DROPS) return;
        if (event.entityLiving == null) return;
        if (event.entityLiving.worldObj == null || event.entityLiving.worldObj.isRemote) return;

        if (event.entityLiving instanceof EntityPlayer) return;
        if (DROP_ONLY_HOSTILE && !(event.entityLiving instanceof IMob)) return;
        if (rng.nextFloat() > DROP_CHANCE) return;

        int meta = pickDropMeta();
        ItemStack stack = new ItemStack(gemItem, 1, meta);

        EntityItem ent = new EntityItem(event.entityLiving.worldObj,
                event.entityLiving.posX,
                event.entityLiving.posY + 0.4,
                event.entityLiving.posZ,
                stack);

        event.drops.add(ent);
    }

    /**
     * Rolls immediately on pickup (so it shows up in inventory right away).
     */
    @SubscribeEvent
    public void onItemPickup(EntityItemPickupEvent event) {
        if (event.entityPlayer == null) return;
        if (event.entityPlayer.worldObj == null || event.entityPlayer.worldObj.isRemote) return;
        if (event.item == null) return;

        ItemStack stack = event.item.getEntityItem();
        rollIfConfigured(stack);
    }

    /**
     * Rolls on creative-menu grabs (and any other server-side inventory insert)
     * by scanning the player's inventory occasionally.
     */
    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        if (event.entityLiving == null) return;
        if (!(event.entityLiving instanceof EntityPlayer)) return;

        EntityPlayer p = (EntityPlayer) event.entityLiving;
        if (p.worldObj == null || p.worldObj.isRemote) return;

        int now = p.ticksExisted;
        if (now <= 0) return;

        String key = p.getCommandSenderName();
        Integer last = lastScanTick.get(key);
        if (last != null && (now - last) < SCAN_EVERY_TICKS) return;
        lastScanTick.put(key, now);

        boolean changed = scanAndRollInventory(p);
        if (changed) {
            p.inventory.markDirty();
            if (p instanceof EntityPlayerMP) {
                ((EntityPlayerMP) p).inventoryContainer.detectAndSendChanges();
            }
        }
    }

    // ---------------------------------------------------------------------
    // Core logic
    // ---------------------------------------------------------------------

    private boolean scanAndRollInventory(EntityPlayer p) {
        boolean changed = false;

        for (int i = 0; i < p.inventory.mainInventory.length; i++) {
            if (rollIfConfigured(p.inventory.mainInventory[i])) changed = true;
        }
        for (int i = 0; i < p.inventory.armorInventory.length; i++) {
            if (rollIfConfigured(p.inventory.armorInventory[i])) changed = true;
        }
        if (rollIfConfigured(p.getCurrentEquippedItem())) changed = true;

        return changed;
    }

    /**
     * Rolls + writes NBT + injects lore.
     * Returns true if this call changed the stack.
     */
    private boolean rollIfConfigured(ItemStack stack) {
        if (stack == null) return false;
        if (stack.getItem() != gemItem) return false;

        int meta = stack.getItemDamage();
        RollProfile profile = metaProfiles.get(meta);

        NBTTagCompound tag = getOrCreateTag(stack);

        // Special case: Negative orb rolls 4 stats where 3 are negative and the 4th
        // is the absolute value of the sum of those 3 (balanced positive).
        if (meta == META_NEGATIVE_FLAT || meta == META_NEGATIVE_MULTI) {
            if (tag.getBoolean(TAG_ROLLED)) return false;
            RollProfile neg = (meta == META_NEGATIVE_MULTI) ? RollProfiles.NEGATIVE_MULTI : RollProfiles.NEGATIVE_FLAT;
            rollNegativeBalanced(stack, tag, neg);
            return true;
        }

        // If this meta isn't configured yet, still give it hover lore once (Effect/Bonus N/A).
        if (profile == null) {
            if (tag.getBoolean(TAG_LORE_DONE)) return false;
            applyNALore(stack);
            tag.setBoolean(TAG_LORE_DONE, true);
            stack.setTagCompound(tag);
            return true;
        }

        // Already rolled?
        if (tag.getBoolean(TAG_ROLLED)) return false;

        // roll values
        NBTTagCompound rolls = new NBTTagCompound();

        int shared = 0;
        if (profile.sharedRoll) {
            shared = rand(profile.sharedMin, profile.sharedMax);
        }

        for (RollEntry e : profile.entries) {
            int v = profile.sharedRoll ? shared : rand(e.min, e.max);
            rolls.setInteger(e.attrKey, v);
        }

        // persist
        tag.setBoolean(TAG_ROLLED, true);
        tag.setBoolean(TAG_LORE_DONE, true);
        tag.setString(TAG_PROFILE, profile.id);
        tag.setTag(TAG_ROLLS, rolls);

        // apply stats (only if profile has entries)
        applyRpgCoreAttributes(tag, profile, rolls);

        // lore
        applyLore(stack, profile, rolls);

        return true;
    }

    /**
     * Negative orb roll:
     * - Randomly selects 4 distinct stats.
     * - Rolls 3 negative values.
     * - The 4th value is the absolute sum of those 3 (balanced positive).
     */
    private void rollNegativeBalanced(ItemStack stack, NBTTagCompound tag, RollProfile profile) {
        final boolean pct = profile.pct;

        final String[] baseKeys = new String[] {
                "dbc.Strength",
                "dbc.Dexterity",
                "dbc.Constitution",
                "dbc.WillPower",
                "dbc.Spirit"
        };

        int[] idx = new int[] {0, 1, 2, 3, 4};
        // Fisher-Yates
        for (int i = idx.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = idx[i];
            idx[i] = idx[j];
            idx[j] = tmp;
        }

        int negMinAbs = pct ? 1 : 25;
        int negMaxAbs = pct ? 10 : 75;

        NBTTagCompound rolls = new NBTTagCompound();
        NBTTagList keyOrder = new NBTTagList();

        int sumNeg = 0;
        for (int k = 0; k < 3; k++) {
            int id = idx[k];
            String key = baseKeys[id] + (pct ? ".Multi" : "");
            int v = -rand(negMinAbs, negMaxAbs);
            sumNeg += v;
            rolls.setInteger(key, v);
            keyOrder.appendTag(new NBTTagString(key));
        }

        int id4 = idx[3];
        String key4 = baseKeys[id4] + (pct ? ".Multi" : "");
        int pos = Math.abs(sumNeg);
        rolls.setInteger(key4, pos);
        keyOrder.appendTag(new NBTTagString(key4));

        // persist
        tag.setBoolean(TAG_ROLLED, true);
        tag.setBoolean(TAG_LORE_DONE, true);
        tag.setString(TAG_PROFILE, profile.id);
        tag.setTag(TAG_ROLLS, rolls);
        tag.setTag("HexOrbKeys", keyOrder);

        // apply RPGCore attrs
        NBTTagCompound rpg = tag.getCompoundTag(TAG_RPGCORE);
        NBTTagCompound attrs = rpg.getCompoundTag(TAG_ATTRS);
        for (int i = 0; i < keyOrder.tagCount(); i++) {
            String k = keyOrder.getStringTagAt(i);
            int v = rolls.getInteger(k);
            attrs.setFloat(k, (float) v);
        }
        rpg.setTag(TAG_ATTRS, attrs);
        tag.setTag(TAG_RPGCORE, rpg);

        // lore
        applyNegativeLore(stack, profile, keyOrder, rolls);

        stack.setTagCompound(tag);
    }

    private void applyNegativeLore(ItemStack stack, RollProfile profile, NBTTagList keyOrder, NBTTagCompound rolls) {
        NBTTagCompound tag = getOrCreateTag(stack);
        NBTTagCompound display = tag.getCompoundTag("display");
        NBTTagList lore = display.getTagList("Lore", 8);

        List<String> newLines = new ArrayList<String>();
        newLines.add(profile.effectLine);
        newLines.add("\u00a77Bonus: <pulse amp=0.55 speed=0.85>" + G_NEG_OPEN + "3 Negatives + 1 Balanced Positive" + G_CLOSE + "</pulse>");

        for (int i = 0; i < keyOrder.tagCount(); i++) {
            String k = keyOrder.getStringTagAt(i);
            int v = rolls.getInteger(k);
            String name = displayNameForAttrKey(k);
            if (name == null) continue;

            boolean isPct = profile.pct;
            String suffix = isPct ? "%" : "";

            // Negative lines: purple/pink, Positive balance line: green pulse
            String open = (v >= 0)
                    ? ("<pulse amp=0.50 speed=0.80>" + G_NATURE_OPEN)
                    : G_NEG_OPEN;
            String close = (v >= 0) ? (G_CLOSE + "</pulse>") : G_CLOSE;

            newLines.add(open + name + " " + formatSigned(v) + suffix + close);
        }

        NBTTagList merged = new NBTTagList();
        for (String s : newLines) {
            merged.appendTag(new NBTTagString(s));
        }

        for (int i = 0; i < lore.tagCount(); i++) {
            String s = lore.getStringTagAt(i);
            if (s != null) {
                if (s.startsWith("\u00a77Effect:")) continue;
                if (s.startsWith("\u00a77Bonus:")) continue;
                // Remove any prior stat lines we injected (safe: only hits our renderer-tagged stat lines)
                if (s.contains("</grad>") && (s.contains("Strength") || s.contains("Dexterity") || s.contains("Constitution") || s.contains("WillPower") || s.contains("Spirit"))) {
                    continue;
                }
                if (containsExact(newLines, s)) continue;
                merged.appendTag(new NBTTagString(s));
            }
        }

        display.setTag("Lore", merged);
        tag.setTag("display", display);
        stack.setTagCompound(tag);
    }

    private static String displayNameForAttrKey(String attrKey) {
        if (attrKey == null) return null;
        if (attrKey.startsWith("dbc.Strength")) return "Strength";
        if (attrKey.startsWith("dbc.Dexterity")) return "Dexterity";
        if (attrKey.startsWith("dbc.Constitution")) return "Constitution";
        if (attrKey.startsWith("dbc.WillPower")) return "WillPower";
        if (attrKey.startsWith("dbc.Spirit")) return "Spirit";
        return null;
    }

    private static String formatSigned(int v) {
        return (v >= 0) ? ("+" + v) : String.valueOf(v);
    }

    private void applyRpgCoreAttributes(NBTTagCompound root, RollProfile profile, NBTTagCompound rolls) {
        if (profile.entries.isEmpty()) return;

        NBTTagCompound rpg = root.getCompoundTag(TAG_RPGCORE);
        NBTTagCompound attrs = rpg.getCompoundTag(TAG_ATTRS);

        for (RollEntry e : profile.entries) {
            int v = rolls.getInteger(e.attrKey);
            // Stored as WHOLE percent units for *.Multi keys.
            attrs.setFloat(e.attrKey, (float) v);
        }

        rpg.setTag(TAG_ATTRS, attrs);
        root.setTag(TAG_RPGCORE, rpg);
    }

    private void applyNALore(ItemStack stack) {
        NBTTagCompound tag = getOrCreateTag(stack);
        NBTTagCompound display = tag.getCompoundTag("display");
        NBTTagList lore = display.getTagList("Lore", 8); // 8 = string

        List<String> newLines = new ArrayList<String>();
        newLines.add(EFFECT_NA);
        newLines.add(BONUS_NA);

        NBTTagList merged = new NBTTagList();
        for (String s : newLines) {
            merged.appendTag(new NBTTagString(s));
        }

        for (int i = 0; i < lore.tagCount(); i++) {
            String s = lore.getStringTagAt(i);
            if (s != null) {
                if (s.startsWith("§7Effect:")) continue;
                if (s.startsWith("§7Bonus:")) continue;
            }
            if (!containsExact(newLines, s)) {
                merged.appendTag(new NBTTagString(s));
            }
        }

        display.setTag("Lore", merged);
        tag.setTag("display", display);
        stack.setTagCompound(tag);
    }

    private void applyLore(ItemStack stack, RollProfile profile, NBTTagCompound rolls) {
        NBTTagCompound tag = getOrCreateTag(stack);
        NBTTagCompound display = tag.getCompoundTag("display");
        NBTTagList lore = display.getTagList("Lore", 8); // 8 = string

        List<String> newLines = new ArrayList<String>();

        // effect line (N/A for stat-orbs; real text only for swirly + fire pills)
        if (profile.effectLine != null && profile.effectLine.length() > 0) {
            newLines.add(profile.effectLine);
        }

        // If this item currently has no stat rolls, still show a Bonus line so the tooltip isn't empty.
        if (profile.entries.isEmpty()) {
            newLines.add(BONUS_NA);
        }

        // roll line(s)
        if (profile.isAll5Shared && !profile.entries.isEmpty()) {
            int v = rolls.getInteger(profile.entries.get(0).attrKey);
            newLines.add(profile.gradientOpen + profile.displayName + " " + formatSigned(v) + (profile.pct ? "%" : "") + G_CLOSE);
        } else {
            for (RollEntry e : profile.entries) {
                int v = rolls.getInteger(e.attrKey);
                String name = (e.displayName != null && e.displayName.length() > 0) ? e.displayName : profile.displayName;
                newLines.add(profile.gradientOpen + name + " " + formatSigned(v) + (profile.pct ? "%" : "") + G_CLOSE);
            }
        }

        // swirly extra line (keeps your existing vibe)
        int meta = stack.getItemDamage();
        if (meta == META_SWIRLY_FLAT || meta == META_SWIRLY_ANIM) {
            newLines.add("§dUnlocks Unique attacks…");
        }

        // Merge: put our lines first, keep existing lore (avoid exact duplicates)
        NBTTagList merged = new NBTTagList();
        for (String s : newLines) {
            merged.appendTag(new NBTTagString(s));
        }
        for (int i = 0; i < lore.tagCount(); i++) {
            String s = lore.getStringTagAt(i);

            // Remove any previous Effect/Bonus lines so we don't stack duplicates,
            // especially when an item transitions from N/A -> real effect later.
            if (s != null) {
                if (s.startsWith("§7Effect:")) continue;
                if (s.startsWith("§7Bonus:")) continue;
                if ("§dUnlocks Unique attacks…".equals(s)) continue;
            }

            if (!containsExact(newLines, s)) {
                merged.appendTag(new NBTTagString(s));
            }
        }

        display.setTag("Lore", merged);
        tag.setTag("display", display);
        stack.setTagCompound(tag);
    }

    private static boolean containsExact(List<String> lines, String s) {
        for (String t : lines) {
            if (t.equals(s)) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------------
    // Drop pool
    // ---------------------------------------------------------------------

    private int pickDropMeta() {
        // Only drop configured metas.
        int[] pool = new int[]{
                0, 1,        // Frost
                6, 7,        // Solar
                8, 9,        // Nature
                14, 15,      // Inferno
                16, 17,      // Rainbow energized
                META_SWIRLY_FLAT, META_SWIRLY_ANIM, // Swirly (energized)
                20, 21,      // Aether
                META_PILL_FIRE_FLAT, META_PILL_FIRE_ANIM // Fire pills
        };
        return pool[rng.nextInt(pool.length)];
    }

    // ---------------------------------------------------------------------
    // Utilities
    // ---------------------------------------------------------------------

    private int rand(int min, int max) {
        if (max < min) { int t = min; min = max; max = t; }
        return min + rng.nextInt((max - min) + 1);
    }

    private static NBTTagCompound getOrCreateTag(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        return tag;
    }

    // ---------------------------------------------------------------------
    // Profiles
    // ---------------------------------------------------------------------

    private static final class RollEntry {
        final String attrKey;
        final String displayName;
        final int min;
        final int max;

        RollEntry(String attrKey, String displayName, int min, int max) {
            this.attrKey = attrKey;
            this.displayName = displayName;
            this.min = min;
            this.max = max;
        }
    }

    private static final class RollProfile {
        final String id;
        final String displayName;
        final String effectLine;
        final String gradientOpen;
        final boolean pct;

        final boolean isAll5Shared;
        final boolean sharedRoll;
        final int sharedMin;
        final int sharedMax;

        final List<RollEntry> entries = new ArrayList<RollEntry>();

        RollProfile(String id,
                    String displayName,
                    String effectLine,
                    String gradientOpen,
                    boolean pct,
                    boolean isAll5Shared,
                    boolean sharedRoll,
                    int sharedMin,
                    int sharedMax) {
            this.id = id;
            this.displayName = displayName;
            this.effectLine = effectLine;
            this.gradientOpen = gradientOpen;
            this.pct = pct;
            this.isAll5Shared = isAll5Shared;
            this.sharedRoll = sharedRoll;
            this.sharedMin = sharedMin;
            this.sharedMax = sharedMax;
        }

        RollProfile add(String attrKey, String displayName, int min, int max) {
            this.entries.add(new RollEntry(attrKey, displayName, min, max));
            return this;
        }
    }

    /**
     * Central place to tweak ranges.
     * Effect type: ONLY swirly + fire pill have real effects right now.
     * Everything else = N/A.
     */
    private static final class RollProfiles {

        // Inferno (Strength)
        static final RollProfile INFERNO_FLAT = new RollProfile(
                "INFERNO_FLAT",
                "Strength",
                EFFECT_NA,
                G_FIERY_OPEN,
                false,
                false,
                false,
                0,
                0
        ).add("dbc.Strength", "Strength", 25, 75);

        static final RollProfile INFERNO_MULTI = new RollProfile(
                "INFERNO_MULTI",
                "Strength",
                EFFECT_NA,
                G_FIERY_OPEN,
                true,
                false,
                false,
                0,
                0
        ).add("dbc.Strength.Multi", "Strength", 1, 10);

        // Frost (Dexterity)
        static final RollProfile FROST_FLAT = new RollProfile(
                "FROST_FLAT",
                "Dexterity",
                EFFECT_NA,
                G_ICY_OPEN,
                false,
                false,
                false,
                0,
                0
        ).add("dbc.Dexterity", "Dexterity", 25, 75);

        static final RollProfile FROST_MULTI = new RollProfile(
                "FROST_MULTI",
                "Dexterity",
                EFFECT_NA,
                G_ICY_OPEN,
                true,
                false,
                false,
                0,
                0
        ).add("dbc.Dexterity.Multi", "Dexterity", 1, 10);

        // Solar (Constitution)
        static final RollProfile SOLAR_FLAT = new RollProfile(
                "SOLAR_FLAT",
                "Constitution",
                EFFECT_NA,
                G_GOLDEN_OPEN,
                false,
                false,
                false,
                0,
                0
        ).add("dbc.Constitution", "Constitution", 25, 75);

        static final RollProfile SOLAR_MULTI = new RollProfile(
                "SOLAR_MULTI",
                "Constitution",
                EFFECT_NA,
                G_GOLDEN_OPEN,
                true,
                false,
                false,
                0,
                0
        ).add("dbc.Constitution.Multi", "Constitution", 1, 10);

        // Nature (WillPower)
        static final RollProfile NATURE_FLAT = new RollProfile(
                "NATURE_FLAT",
                "WillPower",
                EFFECT_NA,
                G_NATURE_OPEN,
                false,
                false,
                false,
                0,
                0
        ).add("dbc.WillPower", "WillPower", 25, 75);

        static final RollProfile NATURE_MULTI = new RollProfile(
                "NATURE_MULTI",
                "WillPower",
                EFFECT_NA,
                G_NATURE_OPEN,
                true,
                false,
                false,
                0,
                0
        ).add("dbc.WillPower.Multi", "WillPower", 1, 10);

        // Negative (special balanced roll handled in rollNegativeBalanced)
        static final RollProfile NEGATIVE_FLAT = new RollProfile(
                "NEGATIVE_FLAT",
                "Negative",
                EFFECT_NA,
                G_NEG_OPEN,
                false,
                false,
                false,
                0,
                0
        );

        static final RollProfile NEGATIVE_MULTI = new RollProfile(
                "NEGATIVE_MULTI",
                "Negative",
                EFFECT_NA,
                G_NEG_OPEN,
                true,
                false,
                false,
                0,
                0
        );

        // Aether (Spirit)
        static final RollProfile AETHER_FLAT = new RollProfile(
                "AETHER_FLAT",
                "Spirit",
                EFFECT_NA,
                G_AETHER_OPEN,
                false,
                false,
                false,
                0,
                0
        ).add("dbc.Spirit", "Spirit", 25, 75);

        static final RollProfile AETHER_MULTI = new RollProfile(
                "AETHER_MULTI",
                "Spirit",
                EFFECT_NA,
                G_AETHER_OPEN,
                true,
                false,
                false,
                0,
                0
        ).add("dbc.Spirit.Multi", "Spirit", 1, 10);

        // Rainbow Energized (ALL 5 stats; shared roll)
        static final RollProfile RAINBOW_ALL5_FLAT = new RollProfile(
                "RAINBOW_ALL5_FLAT",
                "All Attributes",
                EFFECT_NA,
                G_ENERGIZED_OPEN,
                false,
                true,
                true,
                250,
                750
        )
                .add("dbc.Strength",     "All Attributes", 0, 0)
                .add("dbc.Dexterity",    "All Attributes", 0, 0)
                .add("dbc.Constitution", "All Attributes", 0, 0)
                .add("dbc.WillPower",    "All Attributes", 0, 0)
                .add("dbc.Spirit",       "All Attributes", 0, 0);

        static final RollProfile RAINBOW_ALL5_MULTI = new RollProfile(
                "RAINBOW_ALL5_MULTI",
                "All Attributes",
                EFFECT_NA,
                G_ENERGIZED_OPEN,
                true,
                true,
                true,
                5,
                25
        )
                .add("dbc.Strength.Multi",     "All Attributes", 0, 0)
                .add("dbc.Dexterity.Multi",    "All Attributes", 0, 0)
                .add("dbc.Constitution.Multi", "All Attributes", 0, 0)
                .add("dbc.WillPower.Multi",    "All Attributes", 0, 0)
                .add("dbc.Spirit.Multi",       "All Attributes", 0, 0);

        // Swirly (your "Energized") - effect item (no stat roll here)
        static final RollProfile SWIRLY_EFFECT = new RollProfile(
                "SWIRLY_EFFECT",
                "Energized",
                "§7Effect: " + G_ENERGIZED_OPEN + "Unique Attacks" + G_CLOSE,
                G_ENERGIZED_OPEN,
                false,
                false,
                false,
                0,
                0
        );

        // Fire pill - effect item (no stat roll here yet)
        static final RollProfile PILL_FIRE_EFFECT = new RollProfile(
                "PILL_FIRE_EFFECT",
                "Fire Pill",
                "§7Effect: " + G_FIERY_OPEN + "Inferno Punch" + G_CLOSE + " §8(timed hits + finisher)",
                G_FIERY_OPEN,
                false,
                false,
                false,
                0,
                0
        );
    }
}
