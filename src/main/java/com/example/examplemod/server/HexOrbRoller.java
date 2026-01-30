package com.example.examplemod.server;


import com.example.examplemod.api.LorePagesAPI;
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
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.DamageSource;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.Entity;

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
    private static final String G_VOID_OPEN      = "<grad #b84dff #7a5cff #120018 #ff4fd8 scroll=0.30>";
    // Shorter Void gradient for lore pages (helps tooltip width)
    private static final String G_VOID_PAGE_OPEN = "<grad #b84dff #7a5cff scroll=0.22>";
    private static final String G_FRACTURE_OPEN  = "<grad #b84dff #7a5cff #00ffd5 #ff4fd8 scroll=0.30>";
    // Darker purple chaos palette (matches your desired Chaotic style)
    private static final String G_CHAOS_OPEN     = "<grad #ff4fd8 #7a5cff #00ffd5 #ffe66d scroll=0.38>";
    private static final String G_LIGHT_OPEN     = "<grad #fff7d6 #ffeaa8 #ffffff #ffd36b scroll=0.22>";
    // Shorter Light gradient for lore pages (helps tooltip width)
    private static final String G_LIGHT_PAGE_OPEN = "<grad #fff7d6 #ffd36b scroll=0.22>";
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

    // Chaotic sphere metas: 2 = flat, 3 = animated (% variant)
    private static final int META_CHAOTIC_FLAT = 2;
    private static final int META_CHAOTIC_MULTI = 3;

    // Fractured metas: 4 = flat, 5 = animated (% variant)
    private static final int META_FRACTURED_FLAT = 4;
    private static final int META_FRACTURED_MULTI = 5;

    // Light metas: 10 = flat, 11 = animated (% variant)
    private static final int META_LIGHT_FLAT = 10;
    private static final int META_LIGHT_MULTI = 11;

    // Negative orb metas: 12 = negative flat, 13 = negative animated (% variant)
    private static final int META_NEGATIVE_FLAT = 12;
    private static final int META_NEGATIVE_MULTI = 13;


    // Void metas: 22 = flat, 23 = animated (% variant)
    private static final int META_VOID_FLAT = 22;
    private static final int META_VOID_MULTI = 23;
    // Meta -> roll profile
    private final Map<Integer, RollProfile> metaProfiles = new HashMap<Integer, RollProfile>();

    // Throttle inventory scans
    private final Map<String, Integer> lastScanTick = new HashMap<String, Integer>();

    // Throttle fractured shard gains (avoid multi-hit spam)
    private final Map<String, Integer> lastShardGainTick = new HashMap<String, Integer>();

    // Fractured shard gain chances (Fractured-only)
    private static final float FR_SHARD_CHANCE_TAKE_DAMAGE = 0.12f;
    private static final float FR_SHARD_CHANCE_DEAL_DAMAGE = 0.08f;
    private static final float FR_SHARD_CHANCE_KILL_ENTITY = 0.18f;

    // ---------------------------------------------------------------------
    // Fractured debug
    // ---------------------------------------------------------------------
    // Toggle this in-code (no command): when true, Fractured will send the
    // player a chat message on tier changes and SNAP start/end.
    private static final boolean DEBUG_FRACTURED = true;


    // ---------------------------------------------------------------------
    // Light (Radiant / Beacon / Solar / Halo / Angelic)
    // ---------------------------------------------------------------------
    private static final String TAG_LIGHT_TYPE     = "HexLightType";     // string
    private static final String TAG_LIGHT_RAD      = "HexLightRadiance"; // int 0..100
    private static final String TAG_VOID_TYPE     = "HexVoidType";      // string


    // Per-move cooldown/flags stored on the orb (world ticks)
    private static final String TAG_L_CD_WARD      = "HexLightCdWard";
    private static final String TAG_L_CD_AEGIS     = "HexLightCdAegis";
    private static final String TAG_L_CD_BEACON    = "HexLightCdBeacon";
    private static final String TAG_L_CD_IMMUNE    = "HexLightCdImmune";
    // Beacon DBC-regen schedule (world ticks)
    private static final String TAG_L_BEACON_DBC_REGEN_END  = "HexLightBeaconRegenEnd";
    private static final String TAG_L_BEACON_DBC_REGEN_NEXT = "HexLightBeaconRegenNext";
    private static final String TAG_L_CD_SOLAR     = "HexLightCdSolar";
    private static final String TAG_L_CD_LUMEN     = "HexLightCdLumen";
    private static final String TAG_L_CD_HALO      = "HexLightCdHalo";
    private static final String TAG_L_CD_PURIFY    = "HexLightCdPurify";

    // Solar Beam (double-tap LCTRL) cooldown (ticks remaining; decremented in updateLightDynamics)
    private static final String TAG_L_BEAM_CD     = "HexLightBeamCd";
    private static final String TAG_L_BEAM_CD_MAX = "HexLightBeamCdMax";
    private static final String TAG_L_WARD_END     = "HexLightWardEnd";
    private static final String TAG_L_IMMUNE_END   = "HexLightImmuneEnd";
    private static final String TAG_L_IMMUNE_HITS  = "HexLightImmuneHits";
    private static final String TAG_L_SMITE_READY  = "HexLightSmiteReady";
    private static final String TAG_L_HITCOUNT     = "HexLightHitCount";

    private static final String TAG_L_HIT_COUNT    = "HexLightHitCount"; // alias for older refs
    private static final String TAG_L_HITGOAL      = "HexLightHitGoal";

    private static final int LIGHT_RAD_MAX = 100;


    // Solar Beam costs (radiance percent)
    private static final int L_COST_SOLAR_BEAM       = 10;
    private static final int L_COST_SUPER_SOLAR_BEAM = 80;
    // Beam cooldown (ticks)
    private static final int L_BEAM_CD_TICKS       = 70;   // 3.5s
    private static final int L_SUPER_BEAM_CD_TICKS = 240;  // 12s
    // Type weights: Angelic is rare
    private static final int LIGHT_W_RADIANT = 34;
    private static final int LIGHT_W_BEACON  = 28;
    private static final int LIGHT_W_SOLAR   = 24;
    private static final int LIGHT_W_HALO    = 20;
    private static final int LIGHT_W_ANGELIC = 4;

    // Radiance gains (throttled internally)
    private static final int LIGHT_GAIN_SUN   = 1;
    private static final int LIGHT_GAIN_DEAL  = 2;
    private static final int LIGHT_GAIN_TAKE  = 3;
    private static final int LIGHT_GAIN_KILL  = 10;

    // Costs
    private static final int COST_WARD   = 10;
    private static final int COST_AEGIS  = 30;
    private static final int COST_BEACON = 25;
    private static final int COST_IMMUNE = 80;
    private static final int COST_LUMEN  = 15;
    private static final int COST_PURIFY = 12;
    private static final int COST_HALO   = 20;

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

        // Light (Radiant/Beacon/Solar/Halo/Angelic)
        metaProfiles.put(META_LIGHT_FLAT, RollProfiles.LIGHT_FLAT);
        metaProfiles.put(META_LIGHT_MULTI, RollProfiles.LIGHT_MULTI);

        // Chaotic sphere (dynamic: shifts 1-4 stats or ALL, +/- values, rerolls constantly)
        metaProfiles.put(META_CHAOTIC_FLAT, RollProfiles.CHAOTIC_FLAT);
        metaProfiles.put(META_CHAOTIC_MULTI, RollProfiles.CHAOTIC_MULTI);

        // Fractured (dynamic: balanced + thresholds + shards)
        metaProfiles.put(META_FRACTURED_FLAT, RollProfiles.FRACTURED_FLAT);
        metaProfiles.put(META_FRACTURED_MULTI, RollProfiles.FRACTURED_MULTI);

        metaProfiles.put(META_NEGATIVE_FLAT, RollProfiles.NEGATIVE_FLAT);
        metaProfiles.put(META_NEGATIVE_MULTI, RollProfiles.NEGATIVE_MULTI);

        metaProfiles.put(20, RollProfiles.AETHER_FLAT);
        metaProfiles.put(21, RollProfiles.AETHER_MULTI);

        // Void (type-driven; bonus roll is assigned at roll time)
        metaProfiles.put(META_VOID_FLAT, RollProfiles.VOID_FLAT);
        metaProfiles.put(META_VOID_MULTI, RollProfiles.VOID_MULTI);

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
        long nowWorld = event.entityPlayer.worldObj.getTotalWorldTime();
        rollIfConfigured(stack, nowWorld);
    }
    /**
     * Fractured shard gain:
     * - Gain shards when you deal damage OR take damage.
     * - Chance-based (to avoid instant shard spam).
     */
    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (event == null) return;
        if (event.entityLiving == null) return;
        if (event.entityLiving.worldObj == null || event.entityLiving.worldObj.isRemote) return;

        // Taking damage
        if (event.entityLiving instanceof EntityPlayer) {
            EntityPlayer p = (EntityPlayer) event.entityLiving;
            tryAddFractureShard(p, FR_SHARD_CHANCE_TAKE_DAMAGE);
            try {
                tryLightOnTakeDamage(p, event);
            } catch (Throwable t) {
                // keep server stable
            }
        }

        // Dealing damage
        if (event.source != null && event.source.getEntity() instanceof EntityPlayer) {
            EntityPlayer p = (EntityPlayer) event.source.getEntity();
            tryAddFractureShard(p, FR_SHARD_CHANCE_DEAL_DAMAGE);
            try {
                tryLightOnDealDamage(p, event);
            } catch (Throwable t) {
                // keep server stable
            }
        }
    }

    /**
     * Fractured shard gain on kills (chance-based).
     */
    @SubscribeEvent
    public void onLivingDeathShard(LivingDeathEvent event) {
        if (event == null) return;
        if (event.entityLiving == null) return;
        if (event.entityLiving.worldObj == null || event.entityLiving.worldObj.isRemote) return;

        // Exclude player deaths
        if (event.entityLiving instanceof EntityPlayer) return;

        if (event.source != null && event.source.getEntity() instanceof EntityPlayer) {
            EntityPlayer p = (EntityPlayer) event.source.getEntity();
            tryAddFractureShard(p, FR_SHARD_CHANCE_KILL_ENTITY);
            tryLightOnKill(p, event.entityLiving);
        }
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
        // Dynamic chaotic behavior (rerolls constantly)
        if (updateChaoticDynamics(p)) changed = true;
        // Light behavior (radiance + procs)
        try {
            if (updateLightDynamics(p)) changed = true;

            // Force-refresh Light tooltip on legacy stacks (fix N/A)
            for (int i = 0; i < p.inventory.mainInventory.length; i++) ensureLightLore(p.inventory.mainInventory[i]);
            for (int i = 0; i < p.inventory.armorInventory.length; i++) ensureLightLore(p.inventory.armorInventory[i]);
            ensureLightLore(p.getCurrentEquippedItem());
        } catch (Throwable t) {
            // Never let a malformed Light orb NBT crash the whole server tick.
        }
        // Dynamic fractured behavior (thresholds + shards)
        if (updateFracturedDynamics(p)) changed = true;
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

        long nowWorld = (p != null && p.worldObj != null) ? p.worldObj.getTotalWorldTime() : 0L;

        for (int i = 0; i < p.inventory.mainInventory.length; i++) {
            if (rollIfConfigured(p.inventory.mainInventory[i], nowWorld)) changed = true;
        }
        for (int i = 0; i < p.inventory.armorInventory.length; i++) {
            if (rollIfConfigured(p.inventory.armorInventory[i], nowWorld)) changed = true;
        }
        if (rollIfConfigured(p.getCurrentEquippedItem(), nowWorld)) changed = true;

        return changed;
    }

    /**
     * Rolls + writes NBT + injects lore.
     * Returns true if this call changed the stack.
     */
    private boolean rollIfConfigured(ItemStack stack) {
        return rollIfConfigured(stack, -1L);
    }

    /**
     * Same as rollIfConfigured(stack) but lets callers provide world time for
     * dynamic profiles (Chaotic uses this to schedule its next reroll).
     */
    private boolean rollIfConfigured(ItemStack stack, long nowWorld) {
        if (stack == null) return false;
        if (stack.getItem() != gemItem) return false;

        if (nowWorld < 0L) nowWorld = 0L;

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

        // Special case: Fractured orb is dynamic.
        // It rolls a balanced set (3 negatives + 1 equal positive), then continuously shifts
        // via HP thresholds and shard SNAP windows.
        if (meta == META_FRACTURED_FLAT || meta == META_FRACTURED_MULTI) {
            if (tag.getBoolean(TAG_ROLLED)) return false;
            RollProfile fr = (meta == META_FRACTURED_MULTI) ? RollProfiles.FRACTURED_MULTI : RollProfiles.FRACTURED_FLAT;
            rollFracturedDynamicBase(stack, tag, fr);
            return true;
        }


        // Special case: Light orb (types + radiance-driven effects).
        // Rolls ONCE: picks a Light type and a bonus stat line.
        if (meta == META_LIGHT_FLAT || meta == META_LIGHT_MULTI) {
            if (tag.getBoolean(TAG_ROLLED)) return false;
            RollProfile li = (meta == META_LIGHT_MULTI) ? RollProfiles.LIGHT_MULTI : RollProfiles.LIGHT_FLAT;
            try {
                rollLightOrbBase(stack, tag, li);
                return true;
            } catch (Throwable t) {
                return false;
            }
        }



// Special case: Void orb (types; effects handled elsewhere).
// Rolls ONCE: picks a Void type and a bonus stat line.
        if (meta == META_VOID_FLAT || meta == META_VOID_MULTI) {
            if (tag.getBoolean(TAG_ROLLED)) return false;
            RollProfile vo = (meta == META_VOID_MULTI) ? RollProfiles.VOID_MULTI : RollProfiles.VOID_FLAT;
            try {
                rollVoidOrbBase(stack, tag, vo);
                return true;
            } catch (Throwable t) {
                return false;
            }
        }

// Special case: Chaotic sphere is dynamic.
        // Rolls 1-4 random stats (or ALL rarely), values can be +/- and rerolls forever.
        if (meta == META_CHAOTIC_FLAT || meta == META_CHAOTIC_MULTI) {
            if (tag.getBoolean(TAG_ROLLED)) return false;
            RollProfile ch = (meta == META_CHAOTIC_MULTI) ? RollProfiles.CHAOTIC_MULTI : RollProfiles.CHAOTIC_FLAT;
            rollChaoticDynamicBase(stack, tag, ch, nowWorld);
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

    private static String format2(float v) {
        try {
            return String.format(java.util.Locale.US, "%.2f", v);
        } catch (Throwable t) {
            return String.valueOf(v);
        }
    }

    // ---------------------------------------------------------------------
    // DBC Body (jrmcBdy) helpers
    // ---------------------------------------------------------------------
    // DBC stores Body as NBT numbers. We read them directly so Fractured gates
    // react to DBC Body instead of vanilla hearts.
    private static final String TAG_PLAYER_PERSISTED = "PlayerPersisted";
    private static final String TAG_DBC_BODY         = "jrmcBdy";
    // Some installs expose a max key; many do not. When absent, we learn max from observed Body.
    private static final String TAG_DBC_BODY_MAX     = "jrmcBdyMax";
    private static final String TAG_FR_BODY_MAX_LEARNED = "HexFracBodyMax";
    /**
     * @return float[]{body, bodyMax} if present, otherwise null.
     */
    private static float[] getDbcBodyPair(EntityPlayer p) {
        if (p == null) return null;
        NBTTagCompound ed = p.getEntityData();
        if (ed == null) return null;

        // Prefer PlayerPersisted, but fall back to root if needed.
        boolean hasPersisted = ed.hasKey(TAG_PLAYER_PERSISTED, 10);
        NBTTagCompound persisted = hasPersisted ? ed.getCompoundTag(TAG_PLAYER_PERSISTED) : null;

        NBTTagCompound src = null;
        boolean srcIsPersisted = false;

        if (persisted != null && persisted.hasKey(TAG_DBC_BODY)) {
            src = persisted;
            srcIsPersisted = true;
        } else if (ed.hasKey(TAG_DBC_BODY)) {
            src = ed;
        } else {
            return null;
        }

        float body = (float) src.getInteger(TAG_DBC_BODY);
        if (body < 0.0f) body = 0.0f;

        float max = 0.0f;
        if (src.hasKey(TAG_DBC_BODY_MAX)) {
            max = (float) src.getInteger(TAG_DBC_BODY_MAX);
        } else if (src.hasKey(TAG_FR_BODY_MAX_LEARNED)) {
            max = src.getFloat(TAG_FR_BODY_MAX_LEARNED);
        }

        // Learn max if missing or too small. This makes Fractured gates work even
        // when DBC doesn't expose a "max body" key.
        if (max <= 0.0f || body > max) {
            max = body;
            src.setFloat(TAG_FR_BODY_MAX_LEARNED, max);

            // If we modified PlayerPersisted, write it back (safe even if it already existed).
            if (srcIsPersisted && hasPersisted) {
                ed.setTag(TAG_PLAYER_PERSISTED, src);
            }
        }

        if (max <= 0.0f) return null;
        if (body > max) body = max;

        return new float[]{body, max};
    }

    private static float getEffectiveBody(EntityPlayer p) {
        float[] pair = getDbcBodyPair(p);
        return (pair != null) ? pair[0] : (p != null ? p.getHealth() : 0.0f);
    }

    private static float getEffectiveBodyMax(EntityPlayer p) {
        float[] pair = getDbcBodyPair(p);
        return (pair != null) ? pair[1] : (p != null ? p.getMaxHealth() : 0.0f);
    }

    /**
     * Best-effort DBC body heal. Writes to jrmcBdy (and clamps to max if known).
     * Returns true if we successfully touched a DBC body key, false if player isn't using DBC body.
     */
    private static boolean addDbcBody(EntityPlayer p, float add) {
        if (p == null || add <= 0f) return false;
        try {
            NBTTagCompound ed = p.getEntityData();
            if (ed == null) return false;

            boolean hasPersisted = ed.hasKey(TAG_PLAYER_PERSISTED, 10);
            NBTTagCompound persisted = hasPersisted ? ed.getCompoundTag(TAG_PLAYER_PERSISTED) : null;

            NBTTagCompound src = null;
            boolean srcIsPersisted = false;

            if (persisted != null && persisted.hasKey(TAG_DBC_BODY)) {
                src = persisted;
                srcIsPersisted = true;
            } else if (ed.hasKey(TAG_DBC_BODY)) {
                src = ed;
            } else {
                return false;
            }

            int cur = src.getInteger(TAG_DBC_BODY);
            if (cur < 0) cur = 0;

            int max = 0;
            if (src.hasKey(TAG_DBC_BODY_MAX)) {
                max = src.getInteger(TAG_DBC_BODY_MAX);
            } else if (src.hasKey(TAG_FR_BODY_MAX_LEARNED)) {
                max = (int) src.getFloat(TAG_FR_BODY_MAX_LEARNED);
            }
            if (max <= 0) max = Math.max(cur, 1);

            int inc = (int) Math.floor(add);
            if (inc <= 0) inc = 1;

            long nb = (long) cur + (long) inc;
            if (nb > (long) max) nb = (long) max;
            if (nb < 0L) nb = 0L;

            src.setInteger(TAG_DBC_BODY, (int) nb);

            if (srcIsPersisted && hasPersisted) {
                ed.setTag(TAG_PLAYER_PERSISTED, src);
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isFracturedDebugEnabled(EntityPlayer p) {
        return DEBUG_FRACTURED;
    }


    // ---------------------------------------------------------------------
    // Fractured (dynamic) behavior
    // ---------------------------------------------------------------------

    // ---------------------------------------------------------------------
    // Chaotic (dynamic) behavior
    // ---------------------------------------------------------------------
    private static final String TAG_CHAOS_NEXT_AT = "HexChaosNextAt"; // long world time when next reroll is allowed

    /**
     * Scans the player's inventory and updates any CHAOTIC profile stacks.
     * Returns true if anything changed.
     */
    private boolean updateChaoticDynamics(EntityPlayer p) {
        boolean changed = false;
        if (p == null) return false;

        for (int i = 0; i < p.inventory.mainInventory.length; i++) {
            if (updateChaoticStack(p.inventory.mainInventory[i], p)) changed = true;
        }
        for (int i = 0; i < p.inventory.armorInventory.length; i++) {
            if (updateChaoticStack(p.inventory.armorInventory[i], p)) changed = true;
        }
        if (updateChaoticStack(p.getCurrentEquippedItem(), p)) changed = true;
        return changed;
    }

    private boolean updateChaoticStack(ItemStack stack, EntityPlayer p) {
        if (stack == null) return false;
        if (stack.getItem() != gemItem) return false;
        if (p == null || p.worldObj == null) return false;

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) return false;
        if (!tag.getBoolean(TAG_ROLLED)) return false;

        String prof = tag.getString(TAG_PROFILE);
        if (prof == null || !prof.startsWith("CHAOTIC_")) return false;

        long now = p.worldObj.getTotalWorldTime();
        long nextAt = tag.getLong(TAG_CHAOS_NEXT_AT);

        // If missing scheduling info (older stacks), reroll once immediately and schedule.
        if (nextAt <= 0L || now >= nextAt) {
            RollProfile profile = "CHAOTIC_MULTI".equals(prof) ? RollProfiles.CHAOTIC_MULTI : RollProfiles.CHAOTIC_FLAT;
            rerollChaoticNow(stack, tag, profile, now);
            return true;
        }

        return false;
    }

    private void rollChaoticDynamicBase(ItemStack stack, NBTTagCompound tag, RollProfile profile, long nowWorld) {
        // Initialize as rolled + dynamic.
        tag.setBoolean(TAG_ROLLED, true);
        tag.setBoolean(TAG_LORE_DONE, true);
        tag.setString(TAG_PROFILE, profile.id);

        rerollChaoticNow(stack, tag, profile, nowWorld);
        stack.setTagCompound(tag);
    }

    private void rerollChaoticNow(ItemStack stack, NBTTagCompound tag, RollProfile profile, long nowWorld) {
        boolean pct = profile.pct;

        // Remove old chaotic keys from RPGCore so we don't leave stale bonuses behind.
        NBTTagCompound rpg = tag.getCompoundTag(TAG_RPGCORE);
        NBTTagCompound attrs = rpg.getCompoundTag(TAG_ATTRS);
        if (tag.hasKey(TAG_KEYS, 9)) {
            NBTTagList oldKeys = tag.getTagList(TAG_KEYS, 8);
            for (int i = 0; i < oldKeys.tagCount(); i++) {
                String k = oldKeys.getStringTagAt(i);
                if (k != null && k.length() > 0) {
                    attrs.removeTag(k);
                }
            }
        }

        // Pick how many stats this cycle (1-4, or ALL rarely)
        boolean all = rng.nextInt(100) < 7; // ~7% chance to roll ALL 5
        int count = all ? 5 : (1 + rng.nextInt(4));

        // Wide, variable threshold per cycle ("true chaos")
        int cap = pct ? rand(4, 35) : rand(40, 260);

        int[] idx = new int[]{0, 1, 2, 3, 4};
        for (int i = idx.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int t = idx[i];
            idx[i] = idx[j];
            idx[j] = t;
        }

        String[] base = new String[]{
                "dbc.Strength",
                "dbc.Dexterity",
                "dbc.Constitution",
                "dbc.WillPower",
                "dbc.Spirit"
        };

        NBTTagCompound rolls = new NBTTagCompound();
        NBTTagList keyOrder = new NBTTagList();

        for (int i = 0; i < count; i++) {
            String k = base[idx[i]] + (pct ? ".Multi" : "");
            int v = rand(1, cap);
            if (rng.nextBoolean()) v = -v;
            rolls.setInteger(k, v);
            keyOrder.appendTag(new NBTTagString(k));
            attrs.setFloat(k, (float) v);
        }

        // Persist + schedule next reroll
        tag.setTag(TAG_ROLLS, rolls);
        tag.setTag(TAG_KEYS, keyOrder);

        rpg.setTag(TAG_ATTRS, attrs);
        tag.setTag(TAG_RPGCORE, rpg);

        int hold = rand(100, 200); // ~5-10s hold (slower chaos)
        tag.setInteger("HexChaosHold", hold);
        tag.setLong(TAG_CHAOS_NEXT_AT, nowWorld + (long) hold);
        applyChaoticLore(stack, profile, keyOrder, rolls);
        stack.setTagCompound(tag);
    }

    private void applyChaoticLore(ItemStack stack, RollProfile profile, NBTTagList keyOrder, NBTTagCompound rolls) {
        NBTTagCompound tag = getOrCreateTag(stack);
        NBTTagCompound display = tag.getCompoundTag("display");
        NBTTagList lore = display.getTagList("Lore", 8);

        List<String> newLines = new ArrayList<String>();

        // Short, matching style (same vibe as the name)
        newLines.add("§7Effect: " + "<pulse amp=0.55 speed=0.85>" + G_CHAOS_OPEN + "Chaotic Shifts" + G_CLOSE + "</pulse>");
        String cnt = (keyOrder != null && keyOrder.tagCount() == 5) ? "ALL" : String.valueOf(keyOrder.tagCount());
        newLines.add("§7Bonus: " + "<pulse amp=0.55 speed=0.85>" + G_CHAOS_OPEN + "Shifts " + cnt + " (±)" + G_CLOSE + "</pulse>");

        for (int i = 0; i < keyOrder.tagCount(); i++) {
            String k = keyOrder.getStringTagAt(i);
            int v = rolls.getInteger(k);
            String name = displayNameForAttrKey(k);
            if (name == null) continue;
            String suffix = profile.pct ? "%" : "";
            newLines.add(G_CHAOS_OPEN + name + " " + formatSigned(v) + suffix + G_CLOSE);
        }

        NBTTagList merged = new NBTTagList();
        for (String s : newLines) {
            merged.appendTag(new NBTTagString(s));
        }

        for (int i = 0; i < lore.tagCount(); i++) {
            String s = lore.getStringTagAt(i);
            if (s != null) {
                if (s.startsWith("§7Effect:")) continue;
                if (s.startsWith("§7Bonus:")) continue;
                // Remove old injected stat lines (we re-add fresh each reroll)
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

// ---------------------------------------------------------------------
// Light orb (types + radiance-driven procs)
// ---------------------------------------------------------------------

    private static final String LIGHT_TYPE_RADIANT = "Radiant";
    private static final String LIGHT_TYPE_BEACON  = "Beacon";
    private static final String LIGHT_TYPE_SOLAR   = "Solar";
    private static final String LIGHT_TYPE_HALO    = "Halo";
    private static final String LIGHT_TYPE_ANGELIC = "Angelic";


    // ---------------------------------------------------------------------
// Void orb (rolled type placeholder; effects implemented in HexOrbEffectsController later)
// ---------------------------------------------------------------------
    private static final String VOID_TYPE_ENTROPY     = "Entropy";
    private static final String VOID_TYPE_GRAVITY_WELL= "Gravity Well";
    private static final String VOID_TYPE_ABYSS_MARK  = "Abyss Mark";

    private static final String VOID_TYPE_NULL_SHELL  = "Null Shell";
    // Small internal throttles to avoid spam + over-gain
    private static final String TAG_L_LAST_SUN_GAIN  = "HexLightLastSunGain";   // long
    private static final String TAG_L_LAST_DEAL_GAIN = "HexLightLastDealGain";  // long
    private static final String TAG_L_LAST_TAKE_GAIN = "HexLightLastTakeGain";  // long
    private static final String TAG_L_LAST_KILL_GAIN = "HexLightLastKillGain";  // long
    private static final String TAG_L_HIT_GOAL       = "HexLightHitGoal";       // int (6..10)
    private static final String TAG_L_MSG_AT         = "HexLightMsgAt";         // long

    // Costs (Radiance)
    private static final int L_COST_WARD   = 10;
    private static final int L_COST_LUMEN  = 15;
    private static final int L_COST_PURIFY = 12;
    private static final int L_COST_AEGIS  = 30;
    private static final int L_COST_SOLAR  = 0;   // Solar charges passively to 100
    private static final int L_COST_BEACON = 25;
    private static final int L_COST_HALO   = 20;
    private static final int L_COST_IMMUNE = 80;

    // Cooldowns (ticks)
    private static final int L_CD_WARD_TICKS   = 80;    // 4s
    private static final int L_CD_LUMEN_TICKS  = 100;   // 5s
    private static final int L_CD_PURIFY_TICKS = 40;    // 2s
    private static final int L_CD_AEGIS_TICKS  = 400;   // 20s
    private static final int L_CD_SOLAR_TICKS  = 120;   // 6s between smites if somehow recharged quickly
    private static final int L_CD_BEACON_TICKS = 600;   // 30s
    private static final int L_CD_HALO_TICKS   = 60;    // 3s
    private static final int L_CD_IMMUNE_TICKS = 1200;  // 60s

    // Durations (ticks)
    private static final int L_WARD_DURATION   = 60;  // 3s
    private static final int L_IMMUNE_DURATION = 60;  // 3s (also limited by hits)

    // Beacon of Resolve: DBC body regen ticks (in addition to vanilla Regen potion)
    private static final int   L_BEACON_DBC_REGEN_DURATION = 80;   // 4s
    private static final int   L_BEACON_DBC_REGEN_INTERVAL = 20;   // 1s pulses
    private static final float L_BEACON_DBC_REGEN_PCT_PER_PULSE = 0.02f; // 2% max body per pulse (~8% total)
    private static final int L_MARK_DURATION   = 100; // 5s

    // Mark key prefix stored on targets
    private static final String TAG_L_MARK_PREFIX = "HexLightIllumEnd_"; // +playerName => long end tick

    private ItemStack getActiveLightOrb(EntityPlayer p) {
        if (p == null || p.inventory == null) return null;
        ItemStack held = p.getCurrentEquippedItem();
        if (isLightOrbStack(held)) return held;
        for (int i = 0; i < p.inventory.armorInventory.length; i++) {
            ItemStack a = p.inventory.armorInventory[i];
            if (isLightOrbStack(a)) return a;
        }
        return null;
    }

    private boolean isLightOrbStack(ItemStack stack) {
        if (stack == null) return false;
        if (gemItem == null) return false;
        if (stack.getItem() != gemItem) return false;
        int meta = stack.getItemDamage();
        if (meta != META_LIGHT_FLAT && meta != META_LIGHT_MULTI) return false;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) return false;
        if (!tag.getBoolean(TAG_ROLLED)) return false;
        String prof = tag.getString(TAG_PROFILE);
        return prof != null && prof.startsWith("LIGHT_");
    }

    private static int clampInt(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    private static int getLightRadiance(NBTTagCompound tag) {
        if (tag == null) return 0;
        return clampInt(tag.getInteger(TAG_LIGHT_RAD), 0, 100);
    }

    private static void setLightRadiance(NBTTagCompound tag, int v) {
        if (tag == null) return;
        tag.setInteger(TAG_LIGHT_RAD, clampInt(v, 0, 100));
    }

    private static String getLightType(NBTTagCompound tag) {
        if (tag == null) return LIGHT_TYPE_RADIANT;
        String t = tag.getString(TAG_LIGHT_TYPE);
        if (t == null || t.length() == 0) return LIGHT_TYPE_RADIANT;
        return t;
    }

    private static boolean isAngelic(String t) {
        return LIGHT_TYPE_ANGELIC.equalsIgnoreCase(t);
    }

    private boolean typeHasRadiantMoves(String t) {
        return isAngelic(t) || LIGHT_TYPE_RADIANT.equalsIgnoreCase(t);
    }
    private boolean typeHasBeaconMoves(String t) {
        return isAngelic(t) || LIGHT_TYPE_BEACON.equalsIgnoreCase(t);
    }
    private boolean typeHasSolarMoves(String t) {
        return isAngelic(t) || LIGHT_TYPE_SOLAR.equalsIgnoreCase(t);
    }
    private boolean typeHasHaloMoves(String t) {
        return isAngelic(t) || LIGHT_TYPE_HALO.equalsIgnoreCase(t);
    }

    // ─────────────────────────────────────────────────────────────
    // Light (Radiant) — Ward glow FX (server-spawned particles)
    // ─────────────────────────────────────────────────────────────

    // Disable server-side particle broadcasts by default (prevents rare client FX crashes).
    // Glow is handled client-side in HexLightHudOverlay instead.
    private static final boolean ENABLE_SERVER_RADIANT_WARD_FX = false;

    /**
     * Subtle warm-white aura while Radiant Ward is active.
     * Uses WorldServer particle broadcast so all nearby clients see it.
     */
    private void emitRadiantWardGlow(EntityPlayer p, long now) {
        if (p == null || p.worldObj == null) return;
        if (!(p.worldObj instanceof WorldServer)) return;

        // throttle to reduce spam: every 2 ticks
        if ((now & 1L) != 0L) return;

        WorldServer ws = (WorldServer) p.worldObj;

        // Yellowish / white glow (tweak these 3)
        final double r = 1.00D;
        final double g = 1.00D;
        final double b = 0.78D;

        final int particles = 6;
        for (int i = 0; i < particles; i++) {
            double ox = (rng.nextDouble() - 0.5D) * 0.90D;
            double oz = (rng.nextDouble() - 0.5D) * 0.90D;
            double oy = 0.10D + rng.nextDouble() * 1.70D;

            ws.func_147487_a("reddust",
                    p.posX + ox,
                    p.posY + oy,
                    p.posZ + oz,
                    0,
                    r, g, b,
                    1.0D
            );
        }

        // tiny sparkle every ~4 ticks for "shine"
        if ((now & 3L) == 0L) {
            ws.func_147487_a("fireworksSpark",
                    p.posX,
                    p.posY + 1.0D,
                    p.posZ,
                    1,
                    0.20D, 0.30D, 0.20D,
                    0.01D
            );
        }
    }

    /**
     * Brighter burst right when Radiant Ward triggers.
     */
    private void emitRadiantWardBurst(EntityPlayer p) {
        if (p == null || p.worldObj == null) return;
        if (!(p.worldObj instanceof WorldServer)) return;

        WorldServer ws = (WorldServer) p.worldObj;

        final double r = 1.00D;
        final double g = 1.00D;
        final double b = 0.78D;

        // Ring-ish burst around the player
        final int bursts = 26;
        for (int i = 0; i < bursts; i++) {
            double ang = (Math.PI * 2.0D) * (i / (double) bursts);
            double rad = 0.35D + rng.nextDouble() * 0.75D;

            double px = p.posX + Math.cos(ang) * rad;
            double pz = p.posZ + Math.sin(ang) * rad;
            double py = p.posY + 0.20D + rng.nextDouble() * 1.60D;

            ws.func_147487_a("reddust", px, py, pz, 0, r, g, b, 1.0D);
        }

        // quick sparkle pop
        ws.func_147487_a("fireworksSpark",
                p.posX,
                p.posY + 1.0D,
                p.posZ,
                8,
                0.30D, 0.20D, 0.30D,
                0.05D
        );
    }



    private String pickRandomVoidType() {
        // Even weights for now — tweak anytime.
        int r = rng.nextInt(4);
        if (r == 0) return VOID_TYPE_ENTROPY;
        if (r == 1) return VOID_TYPE_GRAVITY_WELL;
        if (r == 2) return VOID_TYPE_ABYSS_MARK;
        return VOID_TYPE_NULL_SHELL;
    }

    private String pickRandomLightType() {
        // Weighted so Angelic is rare.
        int r = rng.nextInt(100);
        if (r < 28) return LIGHT_TYPE_RADIANT;   // 28%
        if (r < 54) return LIGHT_TYPE_BEACON;    // 26%
        if (r < 78) return LIGHT_TYPE_SOLAR;     // 24%
        if (r < 98) return LIGHT_TYPE_HALO;      // 20%
        return LIGHT_TYPE_ANGELIC;               // 2%
    }

    private void addRadiance(NBTTagCompound tag, long now, String throttleKey, int amount, int minIntervalTicks) {
        if (tag == null) return;
        long last = tag.getLong(throttleKey);
        if (now - last < (long) minIntervalTicks) return;
        tag.setLong(throttleKey, now);
        setLightRadiance(tag, getLightRadiance(tag) + amount);
    }

    /**
     * Light orb roll (once):
     * - Pick Light type (Radiant / Beacon / Solar / Halo / Angelic)
     * - Pick a bonus stat line based on type + flat/% variant
     * - Initialize radiance + counters/cooldowns
     */
    private void rollLightOrbBase(ItemStack stack, NBTTagCompound tag, RollProfile base) {
        if (stack == null || tag == null || base == null) return;

        final boolean pct = base.pct;
        final String type = pickRandomLightType();
        tag.setString(TAG_LIGHT_TYPE, type);
        setLightRadiance(tag, 0);

        // init counters / flags
        tag.setInteger(TAG_L_HIT_COUNT, 0);
        tag.setInteger(TAG_L_HIT_GOAL, 6 + rng.nextInt(5)); // 6..10
        tag.setBoolean(TAG_L_SMITE_READY, false);
        tag.setInteger(TAG_L_IMMUNE_HITS, 0);
        tag.removeTag(TAG_L_IMMUNE_END);
        tag.removeTag(TAG_L_WARD_END);

        // choose bonus stat (Light: only core 5 stats + their % multis)
        String attrKey;
        String display;
        int min;
        int max;

        // Core 5 stat pools
        final String[] CORE_FLAT_KEYS = new String[] {
                "dbc.Strength",
                "dbc.Dexterity",
                "dbc.Constitution",
                "dbc.WillPower",
                "dbc.Spirit"
        };
        final String[] CORE_NAMES = new String[] {
                "Strength",
                "Dexterity",
                "Constitution",
                "WillPower",
                "Spirit"
        };

        int idx; // index into CORE_* arrays

        if (pct) {
            min = 3; max = 10; // default % range (whole percent units)

            if (LIGHT_TYPE_RADIANT.equalsIgnoreCase(type)) {
                idx = 2; // Constitution
            } else if (LIGHT_TYPE_BEACON.equalsIgnoreCase(type)) {
                idx = rng.nextBoolean() ? 3 : 4; // WillPower / Spirit
            } else if (LIGHT_TYPE_SOLAR.equalsIgnoreCase(type)) {
                idx = rng.nextBoolean() ? 0 : 4; // Strength / Spirit
            } else if (LIGHT_TYPE_HALO.equalsIgnoreCase(type)) {
                idx = 1; // Dexterity
            } else { // Angelic: any core stat
                idx = rng.nextInt(CORE_FLAT_KEYS.length);
            }

            attrKey = CORE_FLAT_KEYS[idx] + ".Multi";
            display = CORE_NAMES[idx];

        } else {
            min = 20; max = 65; // default flat range

            if (LIGHT_TYPE_RADIANT.equalsIgnoreCase(type)) {
                idx = 2; // Constitution
            } else if (LIGHT_TYPE_BEACON.equalsIgnoreCase(type)) {
                idx = rng.nextBoolean() ? 3 : 4; // WillPower / Spirit
            } else if (LIGHT_TYPE_SOLAR.equalsIgnoreCase(type)) {
                idx = rng.nextBoolean() ? 0 : 4; // Strength / Spirit
            } else if (LIGHT_TYPE_HALO.equalsIgnoreCase(type)) {
                idx = 1; // Dexterity
            } else { // Angelic: any core stat
                idx = rng.nextInt(CORE_FLAT_KEYS.length);
            }

            attrKey = CORE_FLAT_KEYS[idx];
            display = CORE_NAMES[idx];
        }

        int v = rand(min, max);

        RollProfile dyn = new RollProfile(
                base.id,
                base.displayName,
                // Inject type into the effect line without touching other systems.
                "§7Effect: " + G_LIGHT_OPEN + type + G_CLOSE,
                base.gradientOpen,
                base.pct,
                false,
                false,
                0,
                0
        );
        dyn.entries.add(new RollEntry(attrKey, display, min, max));

        NBTTagCompound rolls = new NBTTagCompound();
        rolls.setInteger(attrKey, v);

        tag.setBoolean(TAG_ROLLED, true);
        tag.setBoolean(TAG_LORE_DONE, true);
        tag.setString(TAG_PROFILE, dyn.id);
        tag.setTag(TAG_ROLLS, rolls);

        applyRpgCoreAttributes(tag, dyn, rolls);
        applyLore(stack, dyn, rolls);
        ensureLightLorePages(stack);



        stack.setTagCompound(tag);
    }

    void rollVoidOrbBase(ItemStack stack, NBTTagCompound tag, RollProfile base) {
        if (stack == null || tag == null || base == null) return;

        final boolean pct = base.pct;

        // Pick and store a Void type (effects implemented later in HexOrbEffectsController).
        final String type = pickRandomVoidType();
        tag.setString(TAG_VOID_TYPE, type);

        // Choose one random bonus stat (core 5 only, flat or % based on meta).
        final String[] CORE_FLAT_KEYS = new String[] {
                "dbc.Strength",
                "dbc.Dexterity",
                "dbc.Constitution",
                "dbc.WillPower",
                "dbc.Spirit"
        };
        final String[] CORE_NAMES = new String[] {
                "Strength",
                "Dexterity",
                "Constitution",
                "WillPower",
                "Spirit"
        };

        int idx = rng.nextInt(CORE_FLAT_KEYS.length);

        String attrKey = pct ? (CORE_FLAT_KEYS[idx] + ".Multi") : CORE_FLAT_KEYS[idx];
        String display = CORE_NAMES[idx];

        // Baseline ranges (tweak later)
        int min = pct ? 1 : 25;
        int max = pct ? 10 : 75;

        int v = rand(min, max);

        // Dynamic profile: inject rolled type into effect line.
        RollProfile dyn = new RollProfile(
                base.id,
                base.displayName,
                "§7Effect: <pulse amp=0.55 speed=0.85>" + G_VOID_OPEN + type + G_CLOSE + "</pulse>",
                base.gradientOpen,
                base.pct,
                false,
                false,
                0,
                0
        );
        dyn.entries.add(new RollEntry(attrKey, display, min, max));

        NBTTagCompound rolls = new NBTTagCompound();
        rolls.setInteger(attrKey, v);

        tag.setBoolean(TAG_ROLLED, true);
        tag.setBoolean(TAG_LORE_DONE, true);
        tag.setString(TAG_PROFILE, dyn.id);
        tag.setTag(TAG_ROLLS, rolls);

        applyRpgCoreAttributes(tag, dyn, rolls);
        applyLore(stack, dyn, rolls);
        ensureVoidLorePages(stack);

        stack.setTagCompound(tag);
    }



    /**
     * Called when the player (with a light orb active) takes damage.
     */
    private void tryLightOnTakeDamage(EntityPlayer p, LivingHurtEvent event) {
        if (p == null || event == null) return;
        if (p.worldObj == null || p.worldObj.isRemote) return;

        ItemStack orb = getActiveLightOrb(p);
        if (orb == null) return;
        NBTTagCompound tag = orb.getTagCompound();
        if (tag == null) return;

        long now = p.worldObj.getTotalWorldTime();
        String type = getLightType(tag);
        int rad = getLightRadiance(tag);

        // Immunity window (Beacon / Angelic)
        int immHits = tag.getInteger(TAG_L_IMMUNE_HITS);
        long immEnd = tag.getLong(TAG_L_IMMUNE_END);
        if (immHits > 0 && immEnd > now && typeHasBeaconMoves(type)) {
            tag.setInteger(TAG_L_IMMUNE_HITS, immHits - 1);
            event.ammount = 0.0F;
            event.setCanceled(true);
            if (immHits - 1 <= 0) {
                tag.setInteger(TAG_L_IMMUNE_HITS, 0);
                tag.removeTag(TAG_L_IMMUNE_END);
            }
            orb.setTagCompound(tag);
            return;
        }

        // Radiance gain (taking damage)
        addRadiance(tag, now, TAG_L_LAST_TAKE_GAIN, 3, 10);
        rad = getLightRadiance(tag);

        // Perfect Radiance (panic immunity)
        if (typeHasBeaconMoves(type) && rad >= L_COST_IMMUNE && now >= tag.getLong(TAG_L_CD_IMMUNE)) {
            float curPct = getEffectiveBodyMax(p) > 0.0f ? (getEffectiveBody(p) / getEffectiveBodyMax(p)) : 1.0f;
            // If already low OR this hit is big, treat as panic proc.
            if (curPct <= 0.18f || (event.ammount >= 8.0f && curPct <= 0.30f)) {
                tag.setLong(TAG_L_CD_IMMUNE, now + L_CD_IMMUNE_TICKS);
                tag.setLong(TAG_L_IMMUNE_END, now + L_IMMUNE_DURATION);
                tag.setInteger(TAG_L_IMMUNE_HITS, 2);
                setLightRadiance(tag, 0);
                event.ammount = 0.0F;
                event.setCanceled(true);
                orb.setTagCompound(tag);
                return;
            }
        }

        // Radiant Ward (on hit)
        if (typeHasRadiantMoves(type)) {
            // Apply DR if ward active
            long wardEnd = tag.getLong(TAG_L_WARD_END);
            if (wardEnd > now) {
                event.ammount = event.ammount * 0.70F; // 30% DR
            }

            // Trigger ward on hit (if enough radiance and off CD)
            if (rad >= L_COST_WARD && now >= tag.getLong(TAG_L_CD_WARD)) {
                tag.setLong(TAG_L_CD_WARD, now + L_CD_WARD_TICKS);
                tag.setLong(TAG_L_WARD_END, now + L_WARD_DURATION);
                setLightRadiance(tag, rad - L_COST_WARD);

                // Visual burst when Ward activates
                if (ENABLE_SERVER_RADIANT_WARD_FX) emitRadiantWardBurst(p);

                // Flash knockback (tiny AoE)
                AxisAlignedBB box = AxisAlignedBB.getBoundingBox(p.posX - 3, p.posY - 1, p.posZ - 3, p.posX + 3, p.posY + 2, p.posZ + 3);
                @SuppressWarnings("unchecked")
                List<EntityLivingBase> list = p.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, box);
                for (EntityLivingBase e : list) {
                    if (e == null || e == p) continue;
                    // push away
                    double dx = e.posX - p.posX;
                    double dz = e.posZ - p.posZ;
                    double d = Math.sqrt(dx*dx + dz*dz);
                    if (d < 0.001) d = 0.001;
                    e.motionX += (dx / d) * 0.35;
                    e.motionZ += (dz / d) * 0.35;
                    e.addPotionEffect(new PotionEffect(Potion.blindness.id, 20, 0));
                }
            }
        }

        // Beacon of Resolve (low HP clutch)
        if (typeHasBeaconMoves(type) && rad >= L_COST_BEACON && now >= tag.getLong(TAG_L_CD_BEACON)) {
            float pctHP = getEffectiveBodyMax(p) > 0.0f ? (getEffectiveBody(p) / getEffectiveBodyMax(p)) : 1.0f;
            if (pctHP <= 0.35f) {
                tag.setLong(TAG_L_CD_BEACON, now + L_CD_BEACON_TICKS);
                setLightRadiance(tag, rad - L_COST_BEACON);

                // "Cleanse" common debuffs + give burst recovery
                p.removePotionEffect(Potion.poison.id);
                p.removePotionEffect(Potion.wither.id);
                p.removePotionEffect(Potion.moveSlowdown.id);
                p.removePotionEffect(Potion.weakness.id);
                p.addPotionEffect(new PotionEffect(Potion.moveSpeed.id, 120, 1));     // 6s speed II
                p.addPotionEffect(new PotionEffect(Potion.regeneration.id, 80, 0)); // 4s regen I
                p.addPotionEffect(new PotionEffect(Potion.resistance.id, 60, 0));   // 3s resist I
                // Also regen DBC Body (health) while Beacon regen is active
                tag.setLong(TAG_L_BEACON_DBC_REGEN_END, now + L_BEACON_DBC_REGEN_DURATION);
                tag.setLong(TAG_L_BEACON_DBC_REGEN_NEXT, now);
            }
        }

        orb.setTagCompound(tag);
    }


// ---------------------------------------------------------------------
// Light damage helpers (DBC-first; vanilla fallback)
// ---------------------------------------------------------------------

    private static double getStrengthEffective(EntityPlayer p){
        double v = 0D;
        try { v = HexDBCProcDamageProvider.getStrengthEffective(p); } catch (Throwable ignored) {}
        if (v < 0D) v = 0D;
        return v;
    }

    private static double getWillEffective(EntityPlayer p){
        double v = 0D;
        try { v = HexDBCProcDamageProvider.getWillPowerEffective(p); } catch (Throwable ignored) {}
        if (v < 0D) v = 0D;
        return v;
    }

    // Smite: big single-target hit (STR-scaled).
    private static float computeSolarSmiteDamage(EntityPlayer p){
        double str = getStrengthEffective(p);
        double base = 35000D + (str * 26.0D);
        base *= 2.10D; // Smite is a finisher (full radiance)
        if (base < 25000D) base = 25000D;
        if (base > 25000000D) base = 25000000D;
        return (float) base;
    }

    // Lumen Burst: AoE pulse + mark (WIL-scaled).
    private static float computeLumenBurstDamage(EntityPlayer p){
        double wil = getWillEffective(p);
        double base = 18000D + (wil * 18.0D);
        if (base < 12000D) base = 12000D;
        if (base > 18000000D) base = 18000000D;
        return (float) base;
    }

    /**
     * Applies DBC Body damage if the target exposes jrmcBdy. Otherwise applies a small vanilla fallback.
     * Returns true if DBC Body was touched.
     */
    private static boolean applyDbcBodyDamageOrFallback(EntityPlayer src, EntityLivingBase target, float dbcDamage) {
        if (src == null || target == null || dbcDamage <= 0f) return false;

        try {
            NBTTagCompound ed = target.getEntityData();
            if (ed == null) return false;

            boolean hasPersisted = ed.hasKey(TAG_PLAYER_PERSISTED, 10);
            NBTTagCompound persisted = hasPersisted ? ed.getCompoundTag(TAG_PLAYER_PERSISTED) : null;

            NBTTagCompound store = null;
            boolean storeIsPersisted = false;

            if (persisted != null && persisted.hasKey(TAG_DBC_BODY)) {
                store = persisted;
                storeIsPersisted = true;
            } else if (ed.hasKey(TAG_DBC_BODY)) {
                store = ed;
            }

            if (store != null) {
                int cur = store.getInteger(TAG_DBC_BODY);
                int next = (int) Math.max(0, (double)cur - (double)dbcDamage);
                store.setInteger(TAG_DBC_BODY, next);

                if (storeIsPersisted && hasPersisted) {
                    ed.setTag(TAG_PLAYER_PERSISTED, store);
                }

                // If we dropped to 0, try to ensure the entity actually dies (some non-DBC mobs ignore DBC body).
                if (next <= 0) {
                    try { target.setHealth(0.0F); } catch (Throwable ignored) {}
                    try { target.attackEntityFrom(DamageSource.causePlayerDamage(src), 1000000.0F); } catch (Throwable ignored) {}
                }
                return true;
            }
        } catch (Throwable ignored) {
        }

        // Vanilla fallback: convert large DBC numbers into a sane hearts amount.
        float vanilla = dbcDamage / 6500.0F;
        if (vanilla < 2.0F) vanilla = 2.0F;
        if (vanilla > 18.0F) vanilla = 18.0F;
        try { target.attackEntityFrom(DamageSource.causePlayerDamage(src), vanilla); } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Called when the player (with a light orb active) deals damage to a living target.
     */
    private void tryLightOnDealDamage(EntityPlayer p, LivingHurtEvent event) {
        if (p == null || event == null) return;
        if (p.worldObj == null || p.worldObj.isRemote) return;

        ItemStack orb = getActiveLightOrb(p);
        if (orb == null) return;
        NBTTagCompound tag = orb.getTagCompound();
        if (tag == null) return;

        long now = p.worldObj.getTotalWorldTime();
        String type = getLightType(tag);

        // Radiance gain (dealing damage)
        addRadiance(tag, now, TAG_L_LAST_DEAL_GAIN, 2, 10);
        int rad = getLightRadiance(tag);

        // Marked target takes extra damage from this player (Solar / Angelic)
        if (event.entityLiving != null && (typeHasSolarMoves(type))) {
            NBTTagCompound ed = event.entityLiving.getEntityData();
            if (ed != null) {
                String k = TAG_L_MARK_PREFIX + p.getCommandSenderName();
                long end = ed.getLong(k);
                if (end > now) {
                    event.ammount = event.ammount * 1.20F; // +20%
                }
            }
        }

        // Solar Smite (consumes full charge)
        if (typeHasSolarMoves(type) && tag.getBoolean(TAG_L_SMITE_READY) && now >= tag.getLong(TAG_L_CD_SOLAR)) {
            tag.setLong(TAG_L_CD_SOLAR, now + L_CD_SOLAR_TICKS);
            tag.setBoolean(TAG_L_SMITE_READY, false);
            setLightRadiance(tag, 0);

            // Smite: DBC damage scaled from STR (with vanilla fallback)
            if (event.entityLiving != null) {
                float smiteDmg = computeSolarSmiteDamage(p);
                applyDbcBodyDamageOrFallback(p, event.entityLiving, smiteDmg);
            }

            // tiny visual via short fire (optional)
            if (event.entityLiving != null) {
                event.entityLiving.setFire(1);
            }
        }

        // Purifying Strikes (every N hits)
        if (typeHasHaloMoves(type) && rad >= L_COST_PURIFY && now >= tag.getLong(TAG_L_CD_PURIFY)) {
            int goal = tag.getInteger(TAG_L_HIT_GOAL);
            if (goal < 3) goal = 6 + rng.nextInt(5);
            int count = tag.getInteger(TAG_L_HIT_COUNT) + 1;
            tag.setInteger(TAG_L_HIT_COUNT, count);

            if (count >= goal) {
                tag.setLong(TAG_L_CD_PURIFY, now + L_CD_PURIFY_TICKS);
                tag.setInteger(TAG_L_HIT_COUNT, 0);
                tag.setInteger(TAG_L_HIT_GOAL, 6 + rng.nextInt(5));
                setLightRadiance(tag, rad - L_COST_PURIFY);

                // heal + extra spark damage
                p.heal(1.0F);
                event.ammount += 2.0F;
            }
        }

        orb.setTagCompound(tag);
    }

    /**
     * Called when the player (with a light orb active) kills an entity.
     */
    private void tryLightOnKill(EntityPlayer p, EntityLivingBase dead) {
        if (p == null || dead == null) return;
        if (p.worldObj == null || p.worldObj.isRemote) return;

        ItemStack orb = getActiveLightOrb(p);
        if (orb == null) return;
        NBTTagCompound tag = orb.getTagCompound();
        if (tag == null) return;

        long now = p.worldObj.getTotalWorldTime();
        String type = getLightType(tag);

        // Radiance gain (kill)
        addRadiance(tag, now, TAG_L_LAST_KILL_GAIN, 10, 40);
        int rad = getLightRadiance(tag);

        // Lumen Burst (mark nearby enemies)
        if (typeHasSolarMoves(type) && rad >= L_COST_LUMEN && now >= tag.getLong(TAG_L_CD_LUMEN)) {
            tag.setLong(TAG_L_CD_LUMEN, now + L_CD_LUMEN_TICKS);
            setLightRadiance(tag, rad - L_COST_LUMEN);

            AxisAlignedBB box = AxisAlignedBB.getBoundingBox(p.posX - 6, p.posY - 2, p.posZ - 6, p.posX + 6, p.posY + 3, p.posZ + 6);
            @SuppressWarnings("unchecked")
            List<EntityLivingBase> list = p.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, box);
            String key = TAG_L_MARK_PREFIX + p.getCommandSenderName();
            for (EntityLivingBase e : list) {
                if (e == null || e == p) continue;
                if (e.getHealth() <= 0.0f) continue;
                e.getEntityData().setLong(key, now + L_MARK_DURATION);
                e.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 40, 0)); // tiny stagger
                // Damage pulse scaled from WillPower (DBC-first)
                float lumenDmg = computeLumenBurstDamage(p);
                applyDbcBodyDamageOrFallback(p, e, lumenDmg);
            }
        }

        orb.setTagCompound(tag);
    }

    /**
     * Per-tick light dynamics: sunlight charge, aegis pulse, halo sprint buff.
     * Returns true if the active orb changed.
     */
    private boolean updateLightDynamics(EntityPlayer p) {
        if (p == null || p.worldObj == null || p.worldObj.isRemote) return false;

        ItemStack orb = getActiveLightOrb(p);
        if (orb == null) return false;
        NBTTagCompound tag = orb.getTagCompound();
        if (tag == null) return false;

        long now = p.worldObj.getTotalWorldTime();
        String type = getLightType(tag);

        int beforeRad = getLightRadiance(tag);
        boolean beforeSmite = tag.getBoolean(TAG_L_SMITE_READY);

// Solar Beam cooldown tick-down (server authoritative; HUD reads remaining ticks)
// NOTE: Light dynamics may be throttled (inventory scan cadence), so we tick down by elapsed world-ticks.
        final String TAG_L_LAST_TICK = "HexLightLastDynTick";
        long lastDyn = tag.getLong(TAG_L_LAST_TICK);
        if (lastDyn <= 0L || lastDyn > now) lastDyn = now;
        int dt = (int) (now - lastDyn);
        if (dt < 1) dt = 1;
// Cap dt so a long pause (logout/lag) doesn't instantly finish cooldowns in one update.
        if (dt > 40) dt = 40;
        tag.setLong(TAG_L_LAST_TICK, now);

        int beamCd = tag.getInteger(TAG_L_BEAM_CD);
        if (beamCd > 0) {
            beamCd -= dt;
            if (beamCd < 0) beamCd = 0;
            tag.setInteger(TAG_L_BEAM_CD, beamCd);

            // Preserve max for HUD; should be set when the beam is fired.
            int beamMax = tag.getInteger(TAG_L_BEAM_CD_MAX);
            if (beamMax <= 0 && beamCd > 0) {
                tag.setInteger(TAG_L_BEAM_CD_MAX, beamCd);
            }
        } else if (beamCd < 0) {
            tag.setInteger(TAG_L_BEAM_CD, 0);
        }

        // Sunlight charge (Solar / Angelic)
        if (typeHasSolarMoves(type)) {
            int x = MathHelper.floor_double(p.posX);
            int y = MathHelper.floor_double(p.posY + 1.0);
            int z = MathHelper.floor_double(p.posZ);
            boolean sky = p.worldObj.canBlockSeeTheSky(x, y, z);
            boolean day = p.worldObj.isDaytime();
            if (sky && day) {
                addRadiance(tag, now, TAG_L_LAST_SUN_GAIN, 1, 20);
            }

            int rad = getLightRadiance(tag);
            if (rad >= 100 && !tag.getBoolean(TAG_L_SMITE_READY) && now >= tag.getLong(TAG_L_CD_SOLAR)) {
                tag.setBoolean(TAG_L_SMITE_READY, true);
                // one-time notification (throttled)
                long lastMsg = tag.getLong(TAG_L_MSG_AT);
                if (now - lastMsg > 200) {
                    tag.setLong(TAG_L_MSG_AT, now);
                    p.addChatMessage(new ChatComponentText("§e[Light] §fSmite is ready."));
                }
            }
        }

        // Aegis pulse (Radiant / Angelic)
        if (typeHasRadiantMoves(type)) {
            int rad = getLightRadiance(tag);
            if (rad >= L_COST_AEGIS && now >= tag.getLong(TAG_L_CD_AEGIS)) {
                tag.setLong(TAG_L_CD_AEGIS, now + L_CD_AEGIS_TICKS);
                setLightRadiance(tag, rad - L_COST_AEGIS);
                // Absorption + tiny regen
                p.addPotionEffect(new PotionEffect(22, 160, 0));   // 8s
                p.addPotionEffect(new PotionEffect(Potion.regeneration.id, 60, 0)); // 3s
            }
        }

        // Halo step proxy (Halo / Angelic): short sprint burst
        if (typeHasHaloMoves(type)) {
            int rad = getLightRadiance(tag);
            if (p.isSprinting() && rad >= L_COST_HALO && now >= tag.getLong(TAG_L_CD_HALO)) {
                tag.setLong(TAG_L_CD_HALO, now + L_CD_HALO_TICKS);
                setLightRadiance(tag, rad - L_COST_HALO);
                p.addPotionEffect(new PotionEffect(Potion.moveSpeed.id, 40, 1));   // 2s speed II
                p.addPotionEffect(new PotionEffect(Potion.resistance.id, 20, 0));  // 1s resist I
            }
        }

        // Radiant Ward glow (visual only)
        if (typeHasRadiantMoves(type)) {
            long wardEnd = tag.getLong(TAG_L_WARD_END);
            if (wardEnd > now) {
                if (ENABLE_SERVER_RADIANT_WARD_FX) emitRadiantWardGlow(p, now);
            }
        }


// Beacon of Resolve: DBC body regen tick (in addition to vanilla Regen potion)
        if (typeHasBeaconMoves(type)) {
            long end = tag.getLong(TAG_L_BEACON_DBC_REGEN_END);
            if (end > now) {
                long next = tag.getLong(TAG_L_BEACON_DBC_REGEN_NEXT);
                if (next <= 0L) next = now;
                if (now >= next) {
                    float[] pair = getDbcBodyPair(p);
                    if (pair != null && pair[1] > 0.0f) {
                        float amt = pair[1] * L_BEACON_DBC_REGEN_PCT_PER_PULSE;
                        if (amt < 1.0f) amt = 1.0f;
                        addDbcBody(p, amt);
                    }
                    tag.setLong(TAG_L_BEACON_DBC_REGEN_NEXT, now + L_BEACON_DBC_REGEN_INTERVAL);
                }
            } else if (end != 0L) {
                // cleanup
                tag.removeTag(TAG_L_BEACON_DBC_REGEN_END);
                tag.removeTag(TAG_L_BEACON_DBC_REGEN_NEXT);
            }
        }


        int afterRad = getLightRadiance(tag);
        boolean afterSmite = tag.getBoolean(TAG_L_SMITE_READY);

        boolean changed = (beforeRad != afterRad) || (beforeSmite != afterSmite);
        if (changed) {
            orb.setTagCompound(tag);
        }
        return changed;
    }

    // NBT keys for fractured dynamics
    private static final String TAG_BASE_ROLLS = "HexOrbBaseRolls";      // compound (ints)
    private static final String TAG_KEYS      = "HexOrbKeys";           // list (string)
    private static final String TAG_FR_SHARDS = "HexFracShards";        // int 0..5
    private static final String TAG_FR_SNAP   = "HexFracSnapTicks";     // int remaining
    private static final String TAG_FR_TIER   = "HexFracTier";          // int (100,80,60,30,10,5,0)
    private static final String TAG_FR_TYPE   = "HexFracType";          // string (Fractured Type)


    // ------------------------------------------------------------
    // Fractured "Type" presets
    // Only affects the FRACTURED orb (base roll spread + HP curve + SNAP).
    // ------------------------------------------------------------
    private enum FracturedType {
        BALANCED ("Balanced", 1.00f, 1.00f, 1.00f, 1.00f),
        STEADY   ("Steady",   1.00f, 0.85f, 0.95f, 0.85f),
        AGGRESSIVE("Aggressive", 1.05f, 1.15f, 1.05f, 1.15f),
        BULWARK  ("Bulwark",  0.95f, 0.75f, 1.00f, 0.90f),
        VOLATILE ("Volatile", 0.90f, 1.35f, 1.10f, 1.45f),
        MERCY    ("Mercy",    1.00f, 0.95f, 0.90f, 1.30f),
        RAZOR    ("Razor",    0.85f, 1.20f, 1.15f, 1.25f),
        TITAN    ("Titan",    1.10f, 1.05f, 1.05f, 0.90f),
        PRISM    ("Prism",    0.95f, 1.10f, 1.00f, 1.20f),
        SHATTER  ("Shatter",  0.80f, 1.55f, 1.20f, 1.60f);

        final String label;
        // Multiplies the absolute negative roll min/max in rollFracturedDynamicBase
        final float negMinMul;
        final float negMaxMul;
        // Multiplies the balancing positive roll (abs(sumNeg) * posScale)
        final float posScale;
        // Scales how extreme HP tiers feel away from 1.00 (baseThr = 1 + (baseThr-1)*curveScale)
        final float curveScale;

        FracturedType(String label, float negMinMul, float negMaxMul, float posScale, float curveScale) {
            this.label = label;
            this.negMinMul = negMinMul;
            this.negMaxMul = negMaxMul;
            this.posScale = posScale;
            this.curveScale = curveScale;
        }

        float applyCurve(int tier, float baseThr) {
            float out = 1.0f + ((baseThr - 1.0f) * curveScale);

            // A little extra drama at the emergency tiers for some types (still Fractured-only).
            if (tier <= 10) {
                if (this == MERCY)    out *= 1.18f;
                if (this == SHATTER)  out *= 1.22f;
                if (this == VOLATILE) out *= 1.15f;
                if (this == BULWARK)  out *= 0.92f;
                if (this == STEADY)   out *= 0.95f;
            }
            return out;
        }

        float snapMult() {
            // SNAP is already powerful; types nudge it rather than redefine it.
            if (this == SHATTER)  return 1.35f;
            if (this == VOLATILE) return 1.25f;
            if (this == RAZOR)    return 1.18f;
            if (this == STEADY)   return 0.88f;
            if (this == BULWARK)  return 0.92f;
            return 1.00f;
        }
    }

    private static FracturedType resolveFracturedType(String s) {
        if (s == null || s.length() == 0) return null;
        for (FracturedType t : FracturedType.values()) {
            if (t.label.equalsIgnoreCase(s)) return t;
        }
        return null;
    }

    private FracturedType pickRandomFracturedType() {
        // Equal-weight by default (simple + fun).
        FracturedType[] all = FracturedType.values();
        return all[rng.nextInt(all.length)];
    }

    /**
     * Scans the player's inventory and updates any FRACTURED profile stacks.
     * Returns true if anything changed.
     */
    private boolean updateFracturedDynamics(EntityPlayer p) {
        boolean changed = false;
        if (p == null) return false;

        for (int i = 0; i < p.inventory.mainInventory.length; i++) {
            if (updateFracturedStack(p.inventory.mainInventory[i], p)) changed = true;
        }
        for (int i = 0; i < p.inventory.armorInventory.length; i++) {
            if (updateFracturedStack(p.inventory.armorInventory[i], p)) changed = true;
        }
        if (updateFracturedStack(p.getCurrentEquippedItem(), p)) changed = true;
        return changed;
    }

    /**
     * Adds one shard to the first fractured stack we can find on this player.
     * At 5 shards, triggers SNAP for a short duration and resets shards.
     */
    private void tryAddFractureShard(EntityPlayer p, float chance) {
        if (p == null || p.worldObj == null || p.worldObj.isRemote) return;

        // Chance-based shard gain
        if (chance < 1.0f) {
            if (chance <= 0.0f) return;
            if (rng.nextFloat() > chance) return;
        }

        String key = p.getCommandSenderName();
        int now = p.ticksExisted;
        Integer last = lastShardGainTick.get(key);
        if (last != null && (now - last) < 8) return; // throttle
        lastShardGainTick.put(key, now);

        ItemStack stack = findFirstFracturedStack(p);
        if (stack == null) return;

        NBTTagCompound tag = getOrCreateTag(stack);
        if (!tag.getBoolean(TAG_ROLLED)) return;
        String prof = tag.getString(TAG_PROFILE);
        if (prof == null || (!prof.startsWith("FRACTURED_"))) return;

        int shards = tag.getInteger(TAG_FR_SHARDS);
        int snap = tag.getInteger(TAG_FR_SNAP);

        // If snap is active, don't build shards (keeps it readable)
        if (snap > 0) return;

        if (shards < 5) {
            shards++;
        } else {
            shards = 5; // clamp at max; do NOT auto-reset
        }
        // SNAP is started when a shard is SPENT (blast), not when shards reach max.

        tag.setInteger(TAG_FR_SHARDS, shards);
        stack.setTagCompound(tag);

        // Force an immediate update tick so the effect is felt quickly.
        updateFracturedStack(stack, p);

        p.inventory.markDirty();
        if (p instanceof EntityPlayerMP) {
            ((EntityPlayerMP) p).inventoryContainer.detectAndSendChanges();
        }
    }

    private ItemStack findFirstFracturedStack(EntityPlayer p) {
        if (p == null) return null;

        // Prefer held + armor first
        ItemStack cur = p.getCurrentEquippedItem();
        if (isFracturedProfile(cur)) return cur;

        for (int i = 0; i < p.inventory.armorInventory.length; i++) {
            ItemStack s = p.inventory.armorInventory[i];
            if (isFracturedProfile(s)) return s;
        }
        for (int i = 0; i < p.inventory.mainInventory.length; i++) {
            ItemStack s = p.inventory.mainInventory[i];
            if (isFracturedProfile(s)) return s;
        }
        return null;
    }

    private boolean isFracturedProfile(ItemStack stack) {
        if (stack == null) return false;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) return false;
        if (!tag.hasKey(TAG_PROFILE, 8)) return false;
        String prof = tag.getString(TAG_PROFILE);
        return prof != null && prof.startsWith("FRACTURED_");
    }

    /**
     * True only when this fractured stack is currently "active" on the player:
     * - held in hand (selected hotbar)
     * - OR worn in an armor slot
     * If the orb is only sitting in inventory, this returns false.
     */
    private boolean isFracturedActiveStack(EntityPlayer p, ItemStack stack) {
        if (p == null || stack == null) return false;
        if (p.inventory == null) return false;

        // Held / selected hotbar
        ItemStack held = p.getCurrentEquippedItem();
        if (held == stack) return true;

        // Armor slots
        if (p.inventory.armorInventory != null) {
            for (int i = 0; i < p.inventory.armorInventory.length; i++) {
                if (p.inventory.armorInventory[i] == stack) return true;
            }
        }

        // Selected slot in main inventory
        if (p.inventory.mainInventory != null) {
            int ci = p.inventory.currentItem;
            if (ci >= 0 && ci < p.inventory.mainInventory.length && p.inventory.mainInventory[ci] == stack) return true;
        }

        return false;
    }


    /**
     * Applies HP-threshold multiplier + shard SNAP multiplier to the BASE rolls,
     * then writes the CURRENT values back into RPGCore.Attributes + tooltip stat lines.
     */
    private boolean updateFracturedStack(ItemStack stack, EntityPlayer p) {
        if (stack == null || p == null) return false;

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) return false;
        if (!tag.getBoolean(TAG_ROLLED)) return false;

        String prof = tag.getString(TAG_PROFILE);
        if (prof == null || !prof.startsWith("FRACTURED_")) return false;

        // Need base rolls + key order
        if (!tag.hasKey(TAG_BASE_ROLLS, 10) || !tag.hasKey(TAG_KEYS, 9)) return false;

        NBTTagCompound base = tag.getCompoundTag(TAG_BASE_ROLLS);
        NBTTagList keys = tag.getTagList(TAG_KEYS, 8);
        if (keys == null || keys.tagCount() == 0) return false;

        boolean pct = prof.endsWith("MULTI");

        // Fractured Type (only affects this orb)
        FracturedType fType = resolveFracturedType(tag.hasKey(TAG_FR_TYPE, 8) ? tag.getString(TAG_FR_TYPE) : null);
        if (fType == null) fType = FracturedType.BALANCED;


        // Threshold multiplier (Fractured only)
        // Requirements:
        //  - Base (default rolled) ONLY at full HP
        //  - Otherwise, always apply a tier mod based on current HP%
        //  - If you heal upward across gates (e.g., 60% -> 80%), it swaps to that higher tier
        boolean active = isFracturedActiveStack(p, stack);

        // Threshold multiplier (Fractured-only)
        // Only track HP thresholds when the orb is ACTIVE (held or worn).
        // If not active, behave like "full HP" (base rolls) and avoid body reads.
        float frac = 1.0f;
        boolean isFull = true;
        float hp = 0.0f;
        float max = 0.0f;
        if (active) {
            hp = getEffectiveBody(p);
            max = getEffectiveBodyMax(p);
            frac = (max > 0.0f) ? (hp / max) : 1.0f;
            isFull = (max > 0.0f) && (hp >= (max - 0.01f));
        }


        int lastTier = tag.hasKey(TAG_FR_TIER, 3) ? tag.getInteger(TAG_FR_TIER) : -999;
        int tier;
        float thrMult;
        boolean forcePositive = false; // at 10%/5% gates, negatives become positives

        if (isFull) {
            tier = 100;
            thrMult = 1.00f;
        } else if (frac >= 0.80f) {
            tier = 80;
            thrMult = 0.90f;
        } else if (frac >= 0.60f) {
            tier = 60;
            thrMult = 1.05f;
        } else if (frac >= 0.30f) {
            tier = 30;
            thrMult = 1.25f;
        } else {
            // Below 30%: ramp up, with last 10% / last 5% emergency surge
            if (frac <= 0.05f) {
                tier = 5;
                thrMult = 2.05f;
                forcePositive = true;
            } else if (frac <= 0.10f) {
                tier = 10;
                thrMult = 1.75f;
                forcePositive = true;
            } else {
                tier = 25;
                thrMult = 1.40f;
            }
        }

        // Apply Type curve (Fractured-only)
        thrMult = fType.applyCurve(tier, thrMult);

        // Shard SNAP window
        int snapBefore = tag.getInteger(TAG_FR_SNAP);
        int snap = snapBefore;
        if (snap > 0) {
            snap = Math.max(0, snap - SCAN_EVERY_TICKS);
            tag.setInteger(TAG_FR_SNAP, snap);
        }
        boolean snapActiveBefore = (snapBefore > 0);
        boolean snapActiveAfter  = (snap > 0);

        float shardMult = snapActiveAfter ? (1.60f * fType.snapMult()) : 1.00f;
        float mult = thrMult * shardMult;

        // Persist + optional debug on tier changes
        if (tier != lastTier) {
            tag.setInteger(TAG_FR_TIER, tier);
            if (isFracturedDebugEnabled(p)) {
                boolean dbcBody = (getDbcBodyPair(p) != null);
                int pctInt = (int) Math.floor(frac * 100.0f + 0.5f);
                String tierLabel = (tier == 25) ? "<30" : String.valueOf(tier);
                p.addChatMessage(new ChatComponentText(
                        "§7[Fractured] §fHP " + pctInt + "% §7-> §bTier " + tierLabel + "% §7(x" + format2(mult) + ")"
                                + (forcePositive ? " §d(neg->pos)" : "")
                                + (snapActiveAfter ? " §bSNAP" : "")
                                + (dbcBody ? " §8(DBC Body)" : " §8(Vanilla HP)")
                ));
            }
        }

        if (!snapActiveBefore && snapActiveAfter && isFracturedDebugEnabled(p)) {
            p.addChatMessage(new ChatComponentText("§7[Fractured] §bSNAP §7active (x" + format2(mult) + ")"));
        }
        if (snapActiveBefore && !snapActiveAfter && isFracturedDebugEnabled(p)) {
            p.addChatMessage(new ChatComponentText("§7[Fractured] §bSNAP §7ended"));
        }

        // Current rolls compound
        NBTTagCompound cur = tag.hasKey(TAG_ROLLS, 10) ? tag.getCompoundTag(TAG_ROLLS) : new NBTTagCompound();

        // IMPORTANT: other systems may overwrite RPGCore attrs/rolls.
        // We must continuously ENFORCE the expected tiered values so the buff "sticks".
        NBTTagCompound rpg = tag.getCompoundTag(TAG_RPGCORE);
        NBTTagCompound attrs = rpg.getCompoundTag(TAG_ATTRS);

        boolean curChanged = false;
        boolean attrsMismatch = false;

        for (int i = 0; i < keys.tagCount(); i++) {
            String k = keys.getStringTagAt(i);
            int b = base.getInteger(k);
            int bForCalc = (forcePositive && b < 0) ? Math.abs(b) : b;

            int v = Math.round(bForCalc * mult);

            // Prevent a "0%" feel on small pct rolls
            if (pct && v == 0 && bForCalc != 0) v = (bForCalc > 0) ? 1 : -1;

            if (!cur.hasKey(k) || cur.getInteger(k) != v) {
                cur.setInteger(k, v);
                curChanged = true;
            }

            // Detect if attrs were overwritten externally
            float curAttr = attrs.hasKey(k) ? attrs.getFloat(k) : Float.NaN;
            if (!(curAttr == (float) v)) {
                attrsMismatch = true;
            }
        }

        boolean tierChanged = (tier != lastTier);
        boolean snapEdge = (snapActiveBefore != snapActiveAfter);
        boolean needsWrite = curChanged || attrsMismatch || tierChanged || snapEdge;

        if (!needsWrite) {
            // Still ensure the tag changes (tier/snap decrement) are kept on the stack
            stack.setTagCompound(tag);
            return false;
        }

        // Persist current rolls
        tag.setTag(TAG_ROLLS, cur);

        // Write RPGCore attrs (enforced)
        for (int i = 0; i < keys.tagCount(); i++) {
            String k = keys.getStringTagAt(i);
            attrs.setFloat(k, (float) cur.getInteger(k));
        }
        rpg.setTag(TAG_ATTRS, attrs);
        tag.setTag(TAG_RPGCORE, rpg);

        stack.setTagCompound(tag);

        // Sync tooltip stat lines to current values
        applyFracturedLore(stack, (prof.endsWith("MULTI") ? RollProfiles.FRACTURED_MULTI : RollProfiles.FRACTURED_FLAT), keys, cur);



        // Fractured-only: add lore pages once
        ensureFracturedLorePages(stack);
        return true;
    }

    /**
     * Initial fractured roll:
     * - Select 4 distinct stats.
     * - Roll 3 negatives.
     * - 4th = abs(sum of negatives) as the boon.
     * BASE is stored and then the live CURRENT values shift over time.
     */
    private void rollFracturedDynamicBase(ItemStack stack, NBTTagCompound tag, RollProfile profile) {
        final boolean pct = profile.pct;

        final String[] baseKeys = new String[] {
                "dbc.Strength",
                "dbc.Dexterity",
                "dbc.Constitution",
                "dbc.WillPower",
                "dbc.Spirit"
        };

        int[] idx = new int[] {0, 1, 2, 3, 4};
        for (int i = idx.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = idx[i];
            idx[i] = idx[j];
            idx[j] = tmp;
        }

        // Fractured Type (rolled once per orb, stored in NBT)
        FracturedType fType = resolveFracturedType(tag.hasKey(TAG_FR_TYPE, 8) ? tag.getString(TAG_FR_TYPE) : null);
        if (fType == null) {
            fType = pickRandomFracturedType();
            tag.setString(TAG_FR_TYPE, fType.label);
        }

        int baseNegMinAbs = pct ? 2 : 25;
        int baseNegMaxAbs = pct ? 12 : 90;
        int negMinAbs = Math.max(1, Math.round(baseNegMinAbs * fType.negMinMul));
        int negMaxAbs = Math.max(negMinAbs, Math.round(baseNegMaxAbs * fType.negMaxMul));

        NBTTagCompound base = new NBTTagCompound();
        NBTTagCompound cur = new NBTTagCompound();
        NBTTagList keyOrder = new NBTTagList();

        int sumNeg = 0;
        for (int k = 0; k < 3; k++) {
            int id = idx[k];
            String key = baseKeys[id] + (pct ? ".Multi" : "");
            int v = -rand(negMinAbs, negMaxAbs);
            sumNeg += v;
            base.setInteger(key, v);
            cur.setInteger(key, v);
            keyOrder.appendTag(new NBTTagString(key));
        }

        int id4 = idx[3];
        String key4 = baseKeys[id4] + (pct ? ".Multi" : "");
        int pos = Math.round(Math.abs(sumNeg) * fType.posScale);
        if (pos < 1) pos = 1;
        base.setInteger(key4, pos);
        cur.setInteger(key4, pos);
        keyOrder.appendTag(new NBTTagString(key4));

        // persist
        tag.setBoolean(TAG_ROLLED, true);
        tag.setBoolean(TAG_LORE_DONE, true);
        tag.setString(TAG_PROFILE, profile.id);
        tag.setTag(TAG_BASE_ROLLS, base);
        tag.setTag(TAG_ROLLS, cur);
        tag.setTag(TAG_KEYS, keyOrder);

        // init dynamics
        tag.setInteger(TAG_FR_SHARDS, 0);
        tag.setInteger(TAG_FR_SNAP, 0);

        // apply RPGCore attrs (current)
        NBTTagCompound rpg = tag.getCompoundTag(TAG_RPGCORE);
        NBTTagCompound attrs = rpg.getCompoundTag(TAG_ATTRS);
        for (int i = 0; i < keyOrder.tagCount(); i++) {
            String k = keyOrder.getStringTagAt(i);
            attrs.setFloat(k, (float) cur.getInteger(k));
        }
        rpg.setTag(TAG_ATTRS, attrs);
        tag.setTag(TAG_RPGCORE, rpg);

        // lore
        applyFracturedLore(stack, profile, keyOrder, cur);



        // Fractured-only: add lore pages once
        ensureFracturedLorePages(stack);
        stack.setTagCompound(tag);
    }

    private void applyFracturedLore(ItemStack stack, RollProfile profile, NBTTagList keyOrder, NBTTagCompound rolls) {
        NBTTagCompound tag = getOrCreateTag(stack);
        NBTTagCompound display = tag.getCompoundTag("display");
        NBTTagList lore = display.getTagList("Lore", 8);

        List<String> newLines = new ArrayList<String>();

        // Effect + Bonus (short)
        newLines.add("§7Effect: " + "<pulse amp=0.55 speed=0.85>" + G_FRACTURE_OPEN + "Fracture" + G_CLOSE + "</pulse>");
        String typeLabel = FracturedType.BALANCED.label;
        if (tag.hasKey(TAG_FR_TYPE, 8)) {
            FracturedType ft = resolveFracturedType(tag.getString(TAG_FR_TYPE));
            if (ft != null) typeLabel = ft.label;
        }
        newLines.add("§7Bonus: " + G_FRACTURE_OPEN + typeLabel + " • Thresholds • Shards" + G_CLOSE);

        for (int i = 0; i < keyOrder.tagCount(); i++) {
            String k = keyOrder.getStringTagAt(i);
            int v = rolls.getInteger(k);
            String name = displayNameForAttrKey(k);
            if (name == null) continue;

            String suffix = profile.pct ? "%" : "";            // Keep stat + value in the same style (matches the name).
            newLines.add(profile.gradientOpen + name + " " + formatSigned(v) + suffix + G_CLOSE);
        }

        NBTTagList merged = new NBTTagList();
        for (String s : newLines) {
            merged.appendTag(new NBTTagString(s));
        }

        for (int i = 0; i < lore.tagCount(); i++) {
            String s = lore.getStringTagAt(i);
            if (s != null) {
                if (s.startsWith("§7Effect:")) continue;
                if (s.startsWith("§7Bonus:")) continue;
                // Remove prior stat lines we injected
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


    // ─────────────────────────────────────────────
    // Fractured Lore Pages (two-page tooltip help)
    // Stored in NBT via LorePagesAPI so the pager can show it.
    // ─────────────────────────────────────────────
    private void ensureFracturedLorePages(ItemStack stack) {
        if (stack == null) return;

        try {
            // Copy existing NBT pages (if any) so we don't overwrite other systems.
            List<List<String>> pages = new ArrayList<List<String>>();

            if (LorePagesAPI.hasPages(stack)) {
                List<List<String>> existing = LorePagesAPI.getPages(stack, null);
                if (existing != null) {
                    for (int p = 0; p < existing.size(); p++) {
                        List<String> pg = existing.get(p);
                        List<String> copy = new ArrayList<String>();
                        if (pg != null) copy.addAll(pg);
                        pages.add(copy);
                    }
                }
            }

            // Marker check (avoid duplicates)
            for (int p = 0; p < pages.size(); p++) {
                List<String> pg = pages.get(p);
                if (pg == null) continue;
                for (int i = 0; i < pg.size(); i++) {
                    String ln = pg.get(i);
                    if (ln != null && ln.toUpperCase().contains("FRACTURED ORB")) {
                        return;
                    }
                }
            }

            // Pager starts at page 1, so we keep index 0 as an invisible placeholder.
            if (pages.isEmpty()) {
                pages.add(new ArrayList<String>());
            }

            NBTTagCompound tag = getOrCreateTag(stack);
            String type = tag.hasKey(TAG_FR_TYPE, 8) ? tag.getString(TAG_FR_TYPE) : FracturedType.BALANCED.label;

            // Page 1: what Fractured is + Type + health tiers
            List<String> p1 = new ArrayList<String>();
            p1.add("§b<grad #7a5cff #36d1ff #ff4fd8 #ffe66d scroll=0.30>FRACTURED ORB</grad>§r");
            p1.add("§7Rolled stats shift with your §cHealth§7.");
            p1.add("§7Tiers: §f100%§7, §f60%§7, §f30%§7, §f<30%§7,");
            p1.add("§7then §f10%§7 and §f5%§7 emergency surges.");
            p1.add("§7At 10%/5%: §bnegatives become positives§7.");
            p1.add("§7Type: §b" + type + "§7 (one per orb).");
            p1.add("§7Type tweaks: negatives, surge curve, SNAP.");
            p1.add("§8Hold §fLore key§8 (default §fL§8). PgUp/PgDn.");
            pages.add(p1);

            // Page 2: shards + controls
            List<String> p2 = new ArrayList<String>();
            p2.add("§b<grad #36d1ff #7cff6b #ffe66d #ff4fd8 scroll=0.28>SHARDS & SNAP</grad>§r");
            p2.add("§7HUD appears when equipped (hand or armor).");
            p2.add("§7Gain shards (chance): deal dmg, take dmg,");
            p2.add("§7or kill an enemy (DBC + CustomNPCs too).");
            p2.add("§7Double-tap §fLCTRL§7: spend §b1 shard§7 to");
            p2.add("§7trigger §bSNAP§7 + fire a §dFracture Blast§7.");
            p2.add("§7At §bmax shards§7: triple-tap §fLCTRL§7 to");
            p2.add("§7consume all shards for a §cShatter Blast§7.");
            pages.add(p2);

            LorePagesAPI.writePagesToNBT(stack, pages);
        } catch (Throwable t) {
            // Never crash tooltips or server ticks because of lore pages.
        }
    }

    // ─────────────────────────────────────────────
    // Light (lore pages)
    // ─────────────────────────────────────────────
    // ─────────────────────────────────────────────
// Light lore pages (single page: rolled type only)
// ─────────────────────────────────────────────
    private void ensureLightLorePages(ItemStack stack) {
        if (stack == null) return;

        try {
            // Preserve any existing pages so we don't overwrite other systems.
            List<List<String>> pages = new ArrayList<List<String>>();

            if (LorePagesAPI.hasPages(stack)) {
                List<List<String>> existing = LorePagesAPI.getPages(stack, null);
                if (existing != null) {
                    for (int p = 0; p < existing.size(); p++) {
                        List<String> pg = existing.get(p);
                        List<String> copy = new ArrayList<String>();
                        if (pg != null) copy.addAll(pg);
                        pages.add(copy);
                    }
                }
            }

            // Marker check (avoid duplicates)
            for (int p = 0; p < pages.size(); p++) {
                List<String> pg = pages.get(p);
                if (pg == null) continue;
                for (int i = 0; i < pg.size(); i++) {
                    String ln = pg.get(i);
                    if (ln != null && ln.toUpperCase().contains("LIGHT ORB")) {
                        return;
                    }
                }
            }

            // Pager starts at page 1, keep index 0 as placeholder if empty.
            if (pages.isEmpty()) pages.add(new ArrayList<String>());

            NBTTagCompound tag = getOrCreateTag(stack);
            String type = tag.hasKey(TAG_LIGHT_TYPE, 8) ? tag.getString(TAG_LIGHT_TYPE) : LIGHT_TYPE_RADIANT;

            // Two-passive summary per type (keep text short so tooltips stay left)
            String aName, aDesc, bName, bDesc;

            if (LIGHT_TYPE_RADIANT.equalsIgnoreCase(type)) {
                aName = "Ward";  aDesc = "On hit: brief DR + flash.";
                bName = "Aegis"; bDesc = "Shield pulse / absorb.";
            } else if (LIGHT_TYPE_BEACON.equalsIgnoreCase(type)) {
                aName = "Resolve"; aDesc = "Low HP: cleanse + regen.";
                bName = "Perfect"; bDesc = "Panic: immune briefly (rare).";
            } else if (LIGHT_TYPE_SOLAR.equalsIgnoreCase(type)) {
                aName = "Smite"; aDesc = "Sun charge: next hit beams.";
                bName = "Lumen"; bDesc = "Kill/crit: mark (more dmg).";
            } else if (LIGHT_TYPE_HALO.equalsIgnoreCase(type)) {
                aName = "Step";   aDesc = "Sprint: dash charge + DR.";
                bName = "Purify"; bDesc = "N hits: spark + small heal.";
            } else { // Angelic
                aName = "All"; aDesc = "Has every Light passive.";
                bName = "—";  bDesc = "Costs more Radiance to chain.";
            }

            List<String> p1 = new ArrayList<String>();
            p1.add("§f" + G_LIGHT_PAGE_OPEN + "LIGHT ORB" + G_CLOSE + " §7(" + "§f" + G_LIGHT_PAGE_OPEN + type + G_CLOSE + "§7)");
            p1.add("§7Build §eRadiance§7 (0-100). Spend it to");
            p1.add("§7trigger two passives for this type.");
            p1.add(" ");
            p1.add("§f" + G_LIGHT_PAGE_OPEN + aName + G_CLOSE + "§7: " + aDesc);
            p1.add("§f" + G_LIGHT_PAGE_OPEN + bName + G_CLOSE + "§7: " + bDesc);
            p1.add(" ");
            p1.add("§8HUD shows when held/worn.");

            pages.add(p1);
            LorePagesAPI.writePagesToNBT(stack, pages);
        } catch (Throwable t) {
            // Never crash tooltips or server ticks because of lore pages.
        }
    }

    void ensureVoidLorePages(ItemStack stack) {
        if (stack == null) return;

        try {
            // Preserve any existing pages so we don't overwrite other systems.
            List<List<String>> pages = new ArrayList<List<String>>();

            if (LorePagesAPI.hasPages(stack)) {
                List<List<String>> existing = LorePagesAPI.getPages(stack, null);
                if (existing != null) {
                    for (int p = 0; p < existing.size(); p++) {
                        List<String> pg = existing.get(p);
                        List<String> copy = new ArrayList<String>();
                        if (pg != null) copy.addAll(pg);
                        pages.add(copy);
                    }
                }
            }

            // Marker check (avoid duplicates)
            for (int p = 0; p < pages.size(); p++) {
                List<String> pg = pages.get(p);
                if (pg == null) continue;
                for (int i = 0; i < pg.size(); i++) {
                    String ln = pg.get(i);
                    if (ln != null && ln.toUpperCase().contains("VOID ORB")) {
                        return;
                    }
                }
            }

            // Pager starts at page 1, keep index 0 as placeholder if empty.
            if (pages.isEmpty()) pages.add(new ArrayList<String>());

            NBTTagCompound tag = getOrCreateTag(stack);
            String type = tag.hasKey(TAG_VOID_TYPE, 8) ? tag.getString(TAG_VOID_TYPE) : VOID_TYPE_ENTROPY;

            // Page 1 — overview + quick type list
            List<String> p1 = new ArrayList<String>();
            p1.add("§f" + G_VOID_PAGE_OPEN + "VOID ORB" + G_CLOSE + " §7(§f" + G_VOID_PAGE_OPEN + type + G_CLOSE + "§7)");
            p1.add("§7Rolls a §fVoid Type§7 + a stat bonus.");
            p1.add("§7Type decides what you inflict on hit.");
            p1.add(" ");
            p1.add("§7This orb rolled: §f" + G_VOID_PAGE_OPEN + type + G_CLOSE + "§7.");
            p1.add("§7See the next page for details.");
            p1.add(" ");
            p1.add("§8HUD shows when held/worn.");

            // Page 2 — current type details (kept short)
            List<String> p2 = new ArrayList<String>();
            p2.add("§f" + G_VOID_PAGE_OPEN + "VOID TYPE" + G_CLOSE + " §7(§f" + G_VOID_PAGE_OPEN + type + G_CLOSE + "§7)");
            p2.add(" ");

            if (VOID_TYPE_ENTROPY.equalsIgnoreCase(type)) {
                p2.add("§7On hit: apply §fCorruption§7 to target.");
                p2.add("§7Deals damage over time; reapply adds");
                p2.add("§7stacks (caps) and refreshes duration.");
                p2.add("§7More stacks = stronger ticks.");
            } else if (VOID_TYPE_GRAVITY_WELL.equalsIgnoreCase(type)) {
                p2.add("§7Chance on hit: spawn a §fWell§7.");
                p2.add("§7Briefly pulls nearby foes inward,");
                p2.add("§7then ends in a small burst.");
                p2.add("§7Great for clumping into AoE.");
            } else if (VOID_TYPE_NULL_SHELL.equalsIgnoreCase(type)) {
                p2.add("§7On hit: apply §fNull Shell§7 briefly.");
                p2.add("§7Weakens the target (dmg down / shred).");
                p2.add("§7Also grants you §fVoid Protection§7.");
                p2.add("§7Excellent vs tanky foes.");
            } else { // Abyss Mark
                p2.add("§7On hit: apply a §fMark§7 stack.");
                p2.add("§7At 3 stacks: detonate for bonus dmg");
                p2.add("§7and clear stacks. Reapply refreshes.");
                p2.add("§7Best for sustained combos.");
            }

            pages.add(p1);
            pages.add(p2);

            LorePagesAPI.writePagesToNBT(stack, pages);
        } catch (Throwable t) {
            // Never crash tooltips or server ticks because of lore pages.
        }
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


    // ---------------------------------------------------------------------
    // Light tooltip refresh (force fix for legacy N/A tooltips)
    // ---------------------------------------------------------------------
    private void ensureLightLore(ItemStack stack) {
        if (stack == null || stack.getItem() != gemItem) return;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) return;
        if (!tag.hasKey(TAG_PROFILE, 8)) return;
        String prof = tag.getString(TAG_PROFILE);
        if (prof == null || !prof.startsWith("LIGHT_")) return;

        NBTTagCompound display = tag.getCompoundTag("display");
        NBTTagList lore = display.getTagList("Lore", 8);

        List<String> newLines = new ArrayList<String>();
        newLines.add("§7Effect: " + "<pulse amp=0.35 speed=0.95>" + G_LIGHT_OPEN + "Light" + G_CLOSE + "</pulse>");
        newLines.add("§7Bonus: " + "<pulse amp=0.40 speed=0.90>" + G_LIGHT_OPEN + "Radiance • Light Abilities" + G_CLOSE + "</pulse>");

        NBTTagList merged = new NBTTagList();
        for (String s : newLines) merged.appendTag(new NBTTagString(s));

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


        // Chaotic sphere (special: shifting +/- stats, handled in rollChaoticDynamicBase/updateChaoticDynamics)
        static final RollProfile CHAOTIC_FLAT = new RollProfile(
                "CHAOTIC_FLAT",
                "Chaotic",
                "",
                G_CHAOS_OPEN,
                false,
                false,
                false,
                0,
                0
        );

        static final RollProfile CHAOTIC_MULTI = new RollProfile(
                "CHAOTIC_MULTI",
                "Chaotic",
                "",
                G_CHAOS_OPEN,
                true,
                false,
                false,
                0,
                0
        );



        // Fractured (special: balanced roll + thresholds + shards, handled in rollFractured)
        static final RollProfile FRACTURED_FLAT = new RollProfile(
                "FRACTURED_FLAT",
                "Fractured",
                "§7Effect: " + G_FRACTURE_OPEN + "Fractured" + G_CLOSE,
                G_FRACTURE_OPEN,
                false,
                false,
                false,
                0,
                0
        );

        static final RollProfile FRACTURED_MULTI = new RollProfile(
                "FRACTURED_MULTI",
                "Fractured",
                "§7Effect: " + G_FRACTURE_OPEN + "Fractured" + G_CLOSE,
                G_FRACTURE_OPEN,
                true,
                false,
                false,
                0,
                0
        );
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

        // Void (type-driven; bonus roll is assigned at roll time)
        static final RollProfile VOID_FLAT = new RollProfile(
                "VOID_FLAT",
                "Void",
                "§7Effect: <pulse amp=0.55 speed=0.85>" + G_VOID_OPEN + "Randomized Void Type" + G_CLOSE + "</pulse>",
                G_VOID_OPEN,
                false,
                false,
                false,
                0,
                0
        );

        static final RollProfile VOID_MULTI = new RollProfile(
                "VOID_MULTI",
                "Void",
                "§7Effect: <pulse amp=0.55 speed=0.85>" + G_VOID_OPEN + "Randomized Void Type" + G_CLOSE + "</pulse>",
                G_VOID_OPEN,
                true,
                false,
                false,
                0,
                0
        );



        // Light (type-driven effects; bonus roll is assigned at roll time)
        static final RollProfile LIGHT_FLAT = new RollProfile(
                "LIGHT_FLAT",
                "Light",
                "§7Effect: " + "<pulse amp=0.35 speed=0.95>" + G_LIGHT_OPEN + "Light" + G_CLOSE + "</pulse>",
                G_LIGHT_OPEN,
                false,
                false,
                false,
                0,
                0
        );

        static final RollProfile LIGHT_MULTI = new RollProfile(
                "LIGHT_MULTI",
                "Light",
                "§7Effect: " + "<pulse amp=0.35 speed=0.95>" + G_LIGHT_OPEN + "Light" + G_CLOSE + "</pulse>",
                G_LIGHT_OPEN,
                true,
                false,
                false,
                0,
                0
        );


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