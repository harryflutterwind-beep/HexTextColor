package com.example.examplemod.server;

import com.example.examplemod.api.HexSocketAPI;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.EntityDamageSourceIndirect;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import com.example.examplemod.server.HexDBCBridgeDamageApplier;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Server-side energized orb special effects (1.7.10).
 *
 * IMPORTANT: This class only does anything if it is REGISTERED on MinecraftForge.EVENT_BUS.
 * Example (init):
 *   MinecraftForge.EVENT_BUS.register(new HexOrbEffectsController());
 */
public final class HexOrbEffectsController {

    /** Optional hook so you can swap vanilla damage for DBC/body damage later. */
    public interface DamageApplier {
        /** Implementer should apply damage and handle any DBC syncing needed. */
        void deal(EntityPlayer attacker, EntityLivingBase target, float amount);
    }

    /** If set, ALL proc damage routes through this (after dev boost math, if enabled). */
    public static volatile DamageApplier DAMAGE_APPLIER = null;


    // -------------------------------------------------------------
    // FRACTURED: Flying blast (virtual projectile) keys (player entity NBT)
    // Written by PacketFracturedAction; advanced in onLivingUpdate (server side).
    // -------------------------------------------------------------
    private static final String FRB_KEY_ACTIVE = "HexFRB_Active";
    private static final String FRB_KEY_X      = "HexFRB_X";
    private static final String FRB_KEY_Y      = "HexFRB_Y";
    private static final String FRB_KEY_Z      = "HexFRB_Z";
    private static final String FRB_KEY_DX     = "HexFRB_DX";
    private static final String FRB_KEY_DY     = "HexFRB_DY";
    private static final String FRB_KEY_DZ     = "HexFRB_DZ";
    private static final String FRB_KEY_STEP   = "HexFRB_Step";
    private static final String FRB_KEY_TICKS  = "HexFRB_Ticks";

    // -------------------------------------------------------------
    // VOID: Gravity Well state (player entity NBT)
    // -------------------------------------------------------------
    private static final String VOID_GW_KEY_ACTIVE = "HexVoidGW_Active";
    private static final String VOID_GW_KEY_END    = "HexVoidGW_End";
    private static final String VOID_GW_KEY_X      = "HexVoidGW_X";
    private static final String VOID_GW_KEY_Y      = "HexVoidGW_Y";
    private static final String VOID_GW_KEY_Z      = "HexVoidGW_Z";
    private static final String VOID_GW_KEY_BASE   = "HexVoidGW_Base";
    private static final String VOID_GW_KEY_ANIM   = "HexVoidGW_Anim";
    private static final String VOID_GW_KEY_TICK   = "HexVoidGW_Tick";

    private static final String FRB_KEY_DMG    = "HexFRB_Dmg";
    private static final String FRB_KEY_KB     = "HexFRB_KB";
    private static final String FRB_KEY_RAD    = "HexFRB_Rad";

    private static final String FRB_KEY_AOE_RAD = "HexFRB_AoeRad";
    private static final String FRB_KEY_AOE_DMG = "HexFRB_AoeDmg";
    private static final String FRB_KEY_WIL_SNAP = "HexFRB_WilSnap";
    // -------------------------------------------------------------
    // PROC BASE DAMAGE SOURCE
    // Many mods (including DBC) may report tiny vanilla event damage
    // even when "real" damage is huge. Procs should not scale off that.
    // By default we use event damage only when it looks "realistic";
    // otherwise we fall back to fixed base values (configurable).
    //
    // You can override ALL of this at runtime via PROC_DAMAGE_PROVIDER
    // (great for CNPCs/Nashorn scripts or a dedicated DBC bridge).
    // -------------------------------------------------------------
    public enum ProcStage {
        FLAT_AOE,
        ANIM_PRIMARY,
        ANIM_CHAIN,
        SWIRL_DOT,
        SWIRL_EXPLOSION,
        PUSH_BLAST,
        RUSH_SHOT,
        RUSH_FINAL,
        RAINBOW_FIST,
        FIRE_PUNCH_HIT,
        FIRE_DOT_TICK,
        FIRE_DOT_EXPLOSION,
        FRACTURED_LOWHP
    }

    /** Return a "base damage" number that procs will scale from. */
    public interface ProcDamageProvider {
        /**
         * @param eventDamage  The raw Forge event damage (often unreliable for some mods).
         * @param isAnimGem    True when the energized ANIM gem is used, false for FLAT.
         * @return base damage to scale procs from. Return a negative value to use defaults.
         */
        float getBaseDamage(EntityPlayer attacker, EntityLivingBase target, ProcStage stage, float eventDamage, boolean isAnimGem);
    }

    /** Optional override for proc base-damage sourcing (scripts / other mods). */
    public static volatile ProcDamageProvider PROC_DAMAGE_PROVIDER = null;

    /** If true, we will use eventDamage only when it is >= PROC_EVENT_DAMAGE_MIN. */
    public static volatile boolean PROC_USE_EVENT_DAMAGE_WHEN_REASONABLE = true;

    /** Event damage below this is treated as "unreliable" (common with DBC). */
    public static volatile float PROC_EVENT_DAMAGE_MIN = 25.0f;

    // Fixed base damage fallbacks per stage (used when eventDamage is unreliable)
    public static volatile float PROC_BASE_FLAT_AOE        = 6.0f;
    public static volatile float PROC_BASE_ANIM_PRIMARY    = 9.0f;
    public static volatile float PROC_BASE_ANIM_CHAIN      = 7.0f;
    public static volatile float PROC_BASE_SWIRL_DOT       = 5.0f;
    public static volatile float PROC_BASE_SWIRL_EXPLOSION = 25.0f;
    public static volatile float PROC_BASE_PUSH_BLAST      = 5.0f;
    public static volatile float PROC_BASE_RUSH_SHOT      = 9.0f;
    public static volatile float PROC_BASE_RUSH_FINAL     = 32.0f;
    public static volatile float PROC_BASE_RAINBOW_FIST   = 650.0f;
    public static volatile float PROC_BASE_FIRE_PUNCH_HIT = 18.0f;
    public static volatile float PROC_BASE_FIRE_DOT_TICK  = 65.0f;
    public static volatile float PROC_BASE_FIRE_DOT_EXPLOSION = 320.0f;


    public static volatile float PROC_BASE_FRACTURED_LOWHP = 22.0f;
    private static float procBaseDamage(EntityPlayer attacker, EntityLivingBase target, ProcStage stage, float eventDamage, boolean isAnimGem){
        // 1) Provider hook (scripts / DBC bridge)
        ProcDamageProvider prov = PROC_DAMAGE_PROVIDER;
        if (prov != null){
            try {
                float v = prov.getBaseDamage(attacker, target, stage, eventDamage, isAnimGem);
                if (v >= 0f && !Float.isNaN(v) && !Float.isInfinite(v)) return v;
            } catch (Throwable ignored) {}
        }

        // 2) Use event damage only if it seems meaningful
        if (PROC_USE_EVENT_DAMAGE_WHEN_REASONABLE && eventDamage >= PROC_EVENT_DAMAGE_MIN) {
            return eventDamage;
        }

        // 3) Otherwise fallback to configured bases
        switch (stage){
            case FLAT_AOE:        return PROC_BASE_FLAT_AOE;
            case ANIM_PRIMARY:    return PROC_BASE_ANIM_PRIMARY;
            case ANIM_CHAIN:      return PROC_BASE_ANIM_CHAIN;
            case SWIRL_DOT:       return PROC_BASE_SWIRL_DOT;
            case SWIRL_EXPLOSION: return PROC_BASE_SWIRL_EXPLOSION;
            case PUSH_BLAST:      return PROC_BASE_PUSH_BLAST;
            case RUSH_SHOT:      return PROC_BASE_RUSH_SHOT;
            case RUSH_FINAL:     return PROC_BASE_RUSH_FINAL;
            case RAINBOW_FIST:   return PROC_BASE_RAINBOW_FIST;
            case FIRE_PUNCH_HIT: return PROC_BASE_FIRE_PUNCH_HIT;
            case FIRE_DOT_TICK:  return PROC_BASE_FIRE_DOT_TICK;
            case FIRE_DOT_EXPLOSION: return PROC_BASE_FIRE_DOT_EXPLOSION;
            case FRACTURED_LOWHP: return PROC_BASE_FRACTURED_LOWHP;
            default:              return PROC_BASE_FLAT_AOE;
        }
    }


    // -------------------------------------------------------------
    // DEV ENV DETECTION (1.7.10-safe)
    // -------------------------------------------------------------
    private static final boolean DEV_ENV = isDevEnvironment();

    /**
     * Dev-only proc damage boost (ONLY affects orb proc damage via dealProcDamage()).
     * This does NOT change the player's normal melee hit damage.
     */
    public static volatile boolean DEV_PROC_DAMAGE_BOOST = true; // only applied when DEV_ENV == true
    public static volatile float   DEV_PROC_DAMAGE_MULT  = 8.0f;
    public static volatile float   DEV_PROC_DAMAGE_ADD   = 0.0f;

    private static boolean isDevEnvironment() {
        try {
            Object v = Launch.blackboard.get("fml.deobfuscatedEnvironment");
            if (v instanceof Boolean) return ((Boolean) v).booleanValue();
            if (v != null) return Boolean.parseBoolean(String.valueOf(v));
        } catch (Throwable ignored) {}
        return false;
    }

    // -------------------------------------------------------------
    // GEM KEYS (must match the key stored by your socket system)
    // -------------------------------------------------------------
    public static volatile String GEM_ENERGIZED_FLAT = "gems/orb_gem_swirly_64";
    public static volatile String GEM_ENERGIZED_ANIM = "gems/orb_gem_swirly_loop";

    // Fire Pills
    public static volatile String PILL_FIRE_FLAT = "gems/pill_fire_textured_64";
    public static volatile String PILL_FIRE_ANIM = "gems/pill_fire_animated_64_anim";



    // Fractured Orbs
    public static volatile String GEM_FRACTURED_FLAT = "gems/orb_gem_fractured_64";
    public static volatile String GEM_FRACTURED_ANIM = "gems/orb_gem_fractured_anim_8f_64x516";

    // Void Orbs
    public static volatile String GEM_VOID_FLAT = "gems/orb_gem_violet_void_64";
    public static volatile String GEM_VOID_ANIM = "gems/orb_gem_violet_void_64_anim_8f";
    // -------------------------------------------------------------
    // DEBUG (chat-throttled)
    // -------------------------------------------------------------
    public static volatile boolean DEBUG_PROC = false;
    public static volatile boolean DEBUG_SOCKET_KEYS = false;


    // -------------------------------------------------------------
    // DAMAGE DEBUGGER (chat to attacker)
    // -------------------------------------------------------------
    public static volatile boolean DEBUG_DAMAGE = false;
    public static volatile boolean DEBUG_DAMAGE_VERBOSE = false;

    /** 0 = no throttle (can spam). 1 = max once per tick. 20 = once per second. */
    public static volatile int DEBUG_DAMAGE_THROTTLE_TICKS = 1;

    private static final String DBG_DMG_NEXT = "HexOrbDBG_DmgNext";

    private static void debugDamage(EntityPlayer p, String msg){
        if (!DEBUG_DAMAGE || p == null) return;

        int throttle = Math.max(0, DEBUG_DAMAGE_THROTTLE_TICKS);
        if (throttle > 0){
            NBTTagCompound d = p.getEntityData();
            long now = serverNow(p.worldObj);
            long next = d.getLong(DBG_DMG_NEXT);
            if (next != 0L && now < next) return;
            d.setLong(DBG_DMG_NEXT, now + throttle);
        }

        try {
            p.addChatMessage(new ChatComponentText(msg));
        } catch (Throwable ignored) {}
    }

    private static float safeHealth(EntityLivingBase e){
        try { return e.getHealth(); } catch (Throwable t){ return -1f; }
    }

    // -------------------------------------------------------------
    // DBC BODY% (health) helper
    // DBC stores BODY in player NBT (commonly jrmcBdy / jrmcBdyF).
    // We try a few keys and fall back to vanilla health%.
    // -------------------------------------------------------------
    private static final String[] DBC_BODY_CUR_KEYS = {"jrmcBdy", "jrmcBdyCur", "jrmcBdyc"};
    private static final String[] DBC_BODY_MAX_KEYS = {"jrmcBdyF", "jrmcBdyMax", "jrmcBdyM"};

    /** @return 0..1 (or -1 if unknown) */
    private static float getBodyPercent01(EntityPlayer p){
        if (p == null) return -1f;

        NBTTagCompound root = p.getEntityData();
        float cur = readFirstNumber(root, DBC_BODY_CUR_KEYS);
        float max = readFirstNumber(root, DBC_BODY_MAX_KEYS);

        // Some setups store these under PlayerPersisted
        if (!(max > 0f && cur >= 0f) && root != null && root.hasKey("PlayerPersisted", 10)){
            NBTTagCompound pp = root.getCompoundTag("PlayerPersisted");
            float cur2 = readFirstNumber(pp, DBC_BODY_CUR_KEYS);
            float max2 = readFirstNumber(pp, DBC_BODY_MAX_KEYS);
            if (max2 > 0f && cur2 >= 0f){
                cur = cur2;
                max = max2;
            }
        }

        if (max > 0f && cur >= 0f){
            float pct = cur / max;
            if (pct < 0f) pct = 0f;
            if (pct > 1f) pct = 1f;
            return pct;
        }

        // Fallback: vanilla health
        try {
            float mh = p.getMaxHealth();
            if (mh > 0f){
                float pct = p.getHealth() / mh;
                if (pct < 0f) pct = 0f;
                if (pct > 1f) pct = 1f;
                return pct;
            }
        } catch (Throwable ignored) {}

        return -1f;
    }

    private static float readFirstNumber(NBTTagCompound nbt, String[] keys){
        if (nbt == null || keys == null) return Float.NaN;
        for (String k : keys){
            float v = readNbtNumber(nbt, k);
            if (v == v) return v; // not NaN
        }
        return Float.NaN;
    }

    private static float readNbtNumber(NBTTagCompound nbt, String key){
        if (nbt == null || key == null) return Float.NaN;
        try {
            if (!nbt.hasKey(key)) return Float.NaN;
            NBTBase b = nbt.getTag(key);
            if (b == null) return Float.NaN;

            if (b instanceof NBTTagInt)    return ((NBTTagInt) b).func_150287_d();
            if (b instanceof NBTTagFloat)  return ((NBTTagFloat) b).func_150288_h();
            if (b instanceof NBTTagDouble) return (float) ((NBTTagDouble) b).func_150286_g();
            if (b instanceof NBTTagLong)   return (float) ((NBTTagLong) b).func_150291_c();
            if (b instanceof NBTTagShort)  return ((NBTTagShort) b).func_150289_e();
            if (b instanceof NBTTagByte)   return ((NBTTagByte) b).func_150290_f();
        } catch (Throwable ignored) {}
        return Float.NaN;
    }

    private static String entLabel(Entity e){
        if (e == null) return "null";
        String n;
        try { n = e.getCommandSenderName(); }
        catch (Throwable t){ n = e.getClass().getSimpleName(); }
        return n + "#" + e.getEntityId() + "/" + e.getClass().getSimpleName();
    }

    // -------------------------------------------------------------
    // PROC RELIABILITY FIXES
    // -------------------------------------------------------------

    /**
     * Main reason you only saw damage on CustomNPCs:
     * vanilla entities (and players/DBC) often have i-frames active when you try to apply extra damage
     * inside the same hit event. Resetting hurtResistantTime makes proc damage land.
     */
    public static volatile boolean PROC_RESET_IFRAMES_NONPLAYERS = true;
    public static volatile boolean PROC_RESET_IFRAMES_PLAYERS    = false;

    /**
     * Last resort: if attackEntityFrom() returns false (blocked) you can force health down
     * for NON-players only. Keep this false unless you really need it.
     */
    public static volatile boolean PROC_FORCE_SETHEALTH_FALLBACK_NONPLAYERS = false;

    // -------------------------------------------------------------
    // TWEAKABLE PROC SETTINGS
    // NOTE: Cooldown caps frequency no matter the chance.
    // For "proc every hit" testing set cooldowns to 0.
    // -------------------------------------------------------------

    // Flat gem: rainbow shockwave (AoE burst)
    public static volatile double ENERGIZED_FLAT_PROC_CHANCE = 0.17;
    public static volatile int    ENERGIZED_FLAT_COOLDOWN_TICKS = 60;
    public static volatile float  ENERGIZED_FLAT_RADIUS = 4.5f;
    public static volatile int    ENERGIZED_FLAT_MAX_HITS = 6;
    public static volatile float  ENERGIZED_FLAT_BONUS_DAMAGE = 10.0f;

    // Anim gem: chain arc (bounces)
    public static volatile double ENERGIZED_ANIM_PROC_CHANCE = 0.15;
    public static volatile int    ENERGIZED_ANIM_COOLDOWN_TICKS = 80;
    public static volatile float  ENERGIZED_ANIM_RADIUS = 6.0f;
    public static volatile int    ENERGIZED_ANIM_CHAIN_HITS = 4;
    public static volatile float  ENERGIZED_ANIM_CHAIN_DAMAGE_SCALE = 0.85f;

    public static volatile float  ENERGIZED_ANIM_PRIMARY_DAMAGE_SCALE = 1.20f;
    public static volatile float  ENERGIZED_ANIM_PRIMARY_BONUS_DAMAGE = 10.0f;

    // Global damage multiplier for both Energized procs
    public static volatile float  ENERGIZED_GLOBAL_DAMAGE_MULT = 1.35f;

    // -------------------------------------------------------------
    // Fire Pill: Fire Punch buff
    // - Proc starts a short window where your hands burn (VFX)
    // - Each melee hit during the window adds extra damage
    // - When the window ends, the last target you hit takes a short DoT, then a fire explosion
    // -------------------------------------------------------------
    public static volatile boolean FIRE_PUNCH_ENABLED = true;
    /** If false, fire effects will never apply to players (PvP safety). */
    public static volatile boolean FIRE_PUNCH_AFFECT_PLAYERS = true;

    public static volatile double  FIRE_PUNCH_CHANCE_FLAT = 0.16;
    public static volatile double  FIRE_PUNCH_CHANCE_ANIM = 0.20;

    public static volatile int     FIRE_PUNCH_COOLDOWN_TICKS = 180;      // 9s
    public static volatile int     FIRE_PUNCH_ANIM_COOLDOWN_TICKS = 160; // 8s

    /** How long the "fire hands" window lasts. */
    public static volatile int     FIRE_PUNCH_DURATION_TICKS = 90;       // 4.5s
    public static volatile int     FIRE_PUNCH_ANIM_DURATION_TICKS = 110; // 5.5s

    /** If you land a hit in the last N ticks, we start the DoT immediately; otherwise it starts when the timer ends. */
    public static volatile int     FIRE_PUNCH_FINISH_WINDOW_TICKS = 8;

    // VFX around hands while active
    public static volatile int     FIRE_PUNCH_PARTICLE_INTERVAL_TICKS = 1;
    public static volatile int     FIRE_PUNCH_PARTICLES_PER_TICK = 6;
    public static volatile int     FIRE_PUNCH_ANIM_PARTICLES_PER_TICK = 9;

    // Bonus damage per hit during the window
    public static volatile float   FIRE_PUNCH_HIT_DAMAGE_SCALE = 0.45f;
    public static volatile float   FIRE_PUNCH_ANIM_HIT_DAMAGE_SCALE = 0.55f;

    public static volatile float   FIRE_PUNCH_HIT_BONUS_DAMAGE = 8.0f;
    public static volatile float   FIRE_PUNCH_ANIM_HIT_BONUS_DAMAGE = 10.0f;

    public static volatile int     FIRE_PUNCH_SET_FIRE_SECONDS = 2;
    public static volatile int     FIRE_PUNCH_ANIM_SET_FIRE_SECONDS = 3;

    // Finisher DoT (applies to the last target you hit)
    public static volatile int     FIRE_DOT_DURATION_TICKS = 60;       // 3s
    public static volatile int     FIRE_DOT_ANIM_DURATION_TICKS = 80;  // 4s
    public static volatile int     FIRE_DOT_INTERVAL_TICKS = 10;       // 0.5s

    public static volatile float   FIRE_DOT_DAMAGE_SCALE = 0.20f;
    public static volatile float   FIRE_DOT_ANIM_DAMAGE_SCALE = 0.24f;

    public static volatile float   FIRE_DOT_BONUS_DAMAGE = 4.0f;
    public static volatile float   FIRE_DOT_ANIM_BONUS_DAMAGE = 5.0f;

    public static volatile int     FIRE_DOT_SET_FIRE_SECONDS = 1;
    public static volatile int     FIRE_DOT_ANIM_SET_FIRE_SECONDS = 2;

    // Explosion after DoT completes
    public static volatile float   FIRE_EXPLOSION_RADIUS = 4.5f;
    public static volatile float   FIRE_EXPLOSION_ANIM_RADIUS = 5.5f;

    public static volatile float   FIRE_EXPLOSION_DAMAGE_SCALE = 1.20f;
    public static volatile float   FIRE_EXPLOSION_ANIM_DAMAGE_SCALE = 1.45f;

    public static volatile float   FIRE_EXPLOSION_BONUS_DAMAGE = 18.0f;
    public static volatile float   FIRE_EXPLOSION_ANIM_BONUS_DAMAGE = 24.0f;


    // -------------------------------------------------------------
    // Fractured: Low-Body Chaos Surge (DBC-friendly)
    // Triggers when your BODY (DBC health) is below a threshold.
    // Chance + damage scale up as body gets lower.
    // Sometimes it backfires and hits YOU instead (true fractured chaos).
    // -------------------------------------------------------------
    public static volatile boolean FRACTURED_ENABLED = true;

    // -------------------------------------------------------------
    // VOID ORB: Gravity Well (defensive proc)  - ONLY used when the rolled void type is Gravity Well
    // -------------------------------------------------------------
    public static volatile boolean VOID_ENABLED = true;
    public static volatile float   VOID_GW_PROC_CHANCE          = 0.88f;
    public static volatile float   VOID_GW_PROC_CHANCE_ON_HIT  = 0.14f; // chance when YOU hit something
    public static volatile int     VOID_GW_COOLDOWN_TICKS       = 20 * 8;   // 8s
    public static volatile int     VOID_GW_DURATION_TICKS       = 20 * 2;   // 2s
    public static volatile float   VOID_GW_RADIUS              = 5.5f;
    public static volatile float   VOID_GW_RADIUS_ANIM         = 6.5f;
    public static volatile float   VOID_GW_PULL_STRENGTH       = 0.22f;     // horiz velocity per tick
    public static volatile float   VOID_GW_PULL_STRENGTH_ANIM  = 0.28f;
    public static volatile int     VOID_GW_DAMAGE_PERIOD_TICKS = 6;         // apply tick damage every N ticks
    public static volatile float   VOID_GW_TICK_DAMAGE_SCALE   = 0.20f;     // scales from incoming hit damage
    public static volatile float   VOID_GW_BURST_DAMAGE_SCALE  = 0.85f;


    // -------------------------------------------------------------
    // VOID ORB: Entropy (DoT applied by Gravity Well)
    // - Uses DBC Strength scaling when available (with vanilla fallback)
    // - Spawns the same Gravity Well particle style on the target while ticking
    // - On completion, returns a fraction of the DoT damage to the owner as healing
    // -------------------------------------------------------------
    public static volatile boolean VOID_ENTROPY_ENABLED = true;
    public static volatile boolean VOID_ENTROPY_AFFECT_PLAYERS = true;
    public static volatile int     VOID_ENTROPY_DURATION_TICKS = 20 * 3;     // 3s
    public static volatile int     VOID_ENTROPY_INTERVAL_TICKS = 10;         // 0.5s
    public static volatile float   VOID_ENTROPY_STRENGTH_SCALE = 6.0f;    // dmg per STR per tick
    public static volatile float   VOID_ENTROPY_BONUS_DAMAGE   = 8000.0f;       // flat per tick
    public static volatile float   VOID_ENTROPY_ANIM_MULT      = 1.18f;
    public static volatile float   VOID_ENTROPY_LIFESTEAL_FRACTION = 0.03f;  // 30% of DoT dealt returned at the end
    // Entropy proc tuning (for Entropy-type void gems)
    public static volatile float   VOID_ENTROPY_PROC_CHANCE         = 0.18f; // when YOU are damaged: chance to curse the attacker
    public static volatile float   VOID_ENTROPY_PROC_CHANCE_ON_HIT   = 0.30f; // when YOU hit something: chance to apply Entropy DoT
    public static volatile int     VOID_ENTROPY_COOLDOWN_TICKS       = 20 * 6; // shared cooldown for Entropy procs

    // -------------------------------------------------------------
    // VOID ORB: Abyss Mark (AoE mark -> delayed detonation)
    // - On proc, applies a Mark to ALL nearby entities in range (AoE "spread")
    // - Marked entities have visible void particles while marked
    // - When the timer ends, EACH marked entity detonates (single-target hit + burst VFX)
    // - HUD shows the detonation timer as a bar + seconds (reuses HexVoidHudCD fields)
    // -------------------------------------------------------------
    public static volatile boolean VOID_ABYSS_MARK_ENABLED = true;
    public static volatile boolean VOID_ABYSS_MARK_AFFECT_PLAYERS = true;

    /** Radius (blocks) used to APPLY the mark to nearby entities on proc. */
    public static volatile float   VOID_ABYSS_MARK_MARK_RADIUS      = 4.6f;
    public static volatile float   VOID_ABYSS_MARK_MARK_RADIUS_ANIM = 5.6f;

    /** Safety cap to avoid marking huge crowds at once. */
    public static volatile int     VOID_ABYSS_MARK_MAX_TARGETS = 12;

    /** How long the Mark lasts before detonation (this is the HUD timer). */
    public static volatile int     VOID_ABYSS_MARK_DURATION_TICKS = 20 * 6; // 6s

    /** Chance to apply when YOU are damaged (AoE centered on you). */
    public static volatile float   VOID_ABYSS_MARK_PROC_CHANCE = 0.20f;

    /** Chance to apply when YOU hit something (AoE centered on the target). */
    public static volatile float   VOID_ABYSS_MARK_PROC_CHANCE_ON_HIT = 0.35f;

    // Legacy (unused with the AoE/delay design, kept for back-compat configs):
    public static volatile int     VOID_ABYSS_MARK_MAX_STACKS = 3;
    public static volatile boolean VOID_ABYSS_MARK_DETONATE_ON_MAX = false;

    /** Detonation damage tuning (DBC WILL scaling with vanilla fallback). */
    public static volatile float   VOID_ABYSS_MARK_DETONATE_STR_SCALE = 8.0f;
    public static volatile float   VOID_ABYSS_MARK_DETONATE_BONUS_DMG  = 12000.0f;
    public static volatile float   VOID_ABYSS_MARK_ANIM_MULT          = 1.18f;

    /** Optional detonation knockback strength (0 disables). */
    public static volatile double  VOID_ABYSS_MARK_DETONATE_KB          = 0.20D;
    public static volatile double  VOID_ABYSS_MARK_DETONATE_KB_ANIM     = 0.28D;

    // Legacy (AoE detonation) - not used in the new design but kept for back-compat:
    public static volatile float   VOID_ABYSS_MARK_DETONATE_RADIUS      = 4.6f;
    public static volatile float   VOID_ABYSS_MARK_DETONATE_RADIUS_ANIM = 5.6f;
    // -------------------------------------------------------------
    // VOID ORB: Null Shell (charge meter only - dash/protection/push come later)
    // - Passively charges while you stand in DARKNESS
    // - Can still (rarely) charge in BRIGHT light
    // - (Rare) bonus charge when you HIT / are HIT, especially while in darkness
    //
    // Charge is stored on the PLAYER (entity NBT) and mirrored onto the active host stack NBT
    // so the client HUD can render it without a custom packet.
    // -------------------------------------------------------------
    public static volatile boolean VOID_NULL_SHELL_ENABLED = true;

    /** Charge is stored as 0..MAX (default 1000 = 100.0%). */
    public static volatile int     VOID_NULL_SHELL_CHARGE_MAX = 1000;


    /** How many full "bars" of charge can be stored (for multi-stage abilities). */
    public static volatile int     VOID_NULL_SHELL_CHARGE_STAGES = 3;

    /** Passive gain applied once per second while in darkness. */
    public static volatile int     VOID_NULL_SHELL_PASSIVE_DARK_GAIN_PER_SEC = 60;

    /** Passive gain in bright light: rare chance per second. */
    public static volatile float   VOID_NULL_SHELL_PASSIVE_LIGHT_CHANCE_PER_SEC = 0.08f;
    public static volatile int     VOID_NULL_SHELL_PASSIVE_LIGHT_GAIN = 2;

    /** Combat bonus while in darkness: rare chance per hit/hurt event. */
    public static volatile float   VOID_NULL_SHELL_COMBAT_BONUS_CHANCE_DARK = 0.16f;
    public static volatile int     VOID_NULL_SHELL_COMBAT_BONUS_GAIN_DARK = 10;

    /** Combat bonus while in bright light: even rarer. */
    public static volatile float   VOID_NULL_SHELL_COMBAT_BONUS_CHANCE_LIGHT = 0.06f;
    public static volatile int     VOID_NULL_SHELL_COMBAT_BONUS_GAIN_LIGHT = 6;

    /** Light thresholds (0..15). */
    public static volatile int     VOID_NULL_SHELL_DARK_LIGHT_MAX = 9;
    public static volatile int     VOID_NULL_SHELL_BRIGHT_LIGHT_MIN = 13;

    /** Don't spam NBT sync to the client more frequently than this. */
    public static volatile int     VOID_NULL_SHELL_STAMP_MIN_INTERVAL_TICKS = 20;



    /** Trigger threshold (0..1) of BODY%. Example: 0.45 = below 45% body. */
    public static volatile float  FRACTURED_TRIGGER_BODY_PCT = 0.45f;

    /** Base proc chance at the threshold. Extra chance ramps up as body gets lower. */
    public static volatile double FRACTURED_PROC_CHANCE_BASE = 0.10;
    public static volatile double FRACTURED_PROC_CHANCE_BONUS = 0.22;

    /** Anim gem is a little more "alive". */
    public static volatile double FRACTURED_PROC_CHANCE_BASE_ANIM = 0.14;
    public static volatile double FRACTURED_PROC_CHANCE_BONUS_ANIM = 0.28;

    public static volatile int    FRACTURED_COOLDOWN_TICKS = 30;

    /** Damage math: (base * scale + bonus) * (1 + missingPct * ramp) */
    public static volatile float  FRACTURED_DAMAGE_SCALE = 0.95f;
    public static volatile float  FRACTURED_DAMAGE_SCALE_ANIM = 1.10f;
    public static volatile float  FRACTURED_BONUS_DAMAGE = 10.0f;
    public static volatile float  FRACTURED_BONUS_DAMAGE_ANIM = 12.0f;
    public static volatile float  FRACTURED_MISSING_RAMP = 0.65f;

    /** True chaos: sometimes it backfires (self-hit). */
    public static volatile double FRACTURED_BACKLASH_CHANCE = 0.25;
    public static volatile float  FRACTURED_BACKLASH_SELF_SCALE = 0.35f;

    /** Rare extreme roll (bigger positive OR bigger backlash). */
    public static volatile double FRACTURED_EXTREME_CHANCE = 0.08;
    public static volatile float  FRACTURED_EXTREME_MULT_POS = 2.25f;
    public static volatile float  FRACTURED_EXTREME_MULT_NEG = 2.10f;

    // === Null Shell : Void Dash ===
    public static final float NS_PASSIVE_PROC_CHANCE = 0.02f; // 2% chance
    public static final float NS_PASSIVE_COST = 0.02f; // 2%
    public static final float NS_ACTIVE_COST  = (1.0f/3.0f); // 1st charge bar
    // 2 charge bars: Void Protection (defense buff)
    public static final float NS_DEFENSE_COST = (2.0f/3.0f);
    public static final int   NS_DEFENSE_DURATION_TICKS = 60;   // 3s
    public static final int   NS_DEFENSE_COOLDOWN_TICKS = 120;  // 6s (separate from dash)
    public static final float NS_DEFENSE_INCOMING_MULT = 0.65f; // 35% reduction

    // 100% charge: Void Push (AoE force burst)
    public static final float NS_PUSH_COST = 1.00f; // 3 charge bars (full)
    public static final int   NS_PUSH_COOLDOWN_TICKS = 200;        // 10s
    public static final int   NS_PUSH_CHARGE_TICKS = 100;          // 5s
    public static final int   NS_PUSH_OVERCHARGE_TICKS = 100;      // +5s
    public static final float NS_PUSH_OVERCHARGE_MULT = 1.87f;

    public static volatile float  NS_PUSH_BASE_DAMAGE = 8000f;      // added before stat scaling
    public static volatile double NS_PUSH_STAT_SCALE  = 1.60D;    // (DEX/CON weighted) * scale
    public static volatile float  NS_PUSH_DEX_WEIGHT  = 0.55f;
    public static volatile float  NS_PUSH_CON_WEIGHT  = 0.45f;
    public static volatile float  NS_PUSH_RADIUS      = 6.0f;

    public static volatile double NS_PUSH_KB_H = 1.25;
    public static volatile double NS_PUSH_KB_Y = 0.30;



    public static final int   NS_DASH_COOLDOWN_TICKS = 80; // 4s
    public static final double NS_PASSIVE_DISTANCE = 1.4;
    public static final double NS_ACTIVE_DISTANCE  = 3.6;

    public static final int NS_TRAIL_LIFE = 60;
    public static final double NS_TRAIL_RADIUS = 1.8;
    public static final float NS_TRAIL_BIG_MULT = 1.2f;
    public static final float NS_TRAIL_DOT_MULT = 0.25f;
    public static volatile int   NS_TRAIL_DOT_INTERVAL = 4;   // ticks between DoT pulses
    public static volatile float NS_TRAIL_BASE_DAMAGE = 2500f;   // added before stat scaling
    public static volatile double NS_TRAIL_STAT_SCALE = 0.60D; // (DEX/CON weighted) * scale
    public static volatile float NS_TRAIL_DEX_WEIGHT = 0.55f;
    public static volatile float NS_TRAIL_CON_WEIGHT = 0.45f;


    // -------------------------------------------------------------
    // Swirl + explode
    // -------------------------------------------------------------
    public static volatile boolean ENERGIZED_SWIRL_ENABLED = true;

    // Variant proc: Horizontal push-swirl + big rainbow blast (NO DoT)
    public static volatile boolean ENERGIZED_PUSH_BLAST_ENABLED = true;

    // Chance (checked when a swirl would start). If it triggers, we do PUSH_BLAST instead of normal swirl.
    public static volatile double ENERGIZED_PUSH_BLAST_CHANCE_FLAT = 0.60;
    public static volatile double ENERGIZED_PUSH_BLAST_CHANCE_ANIM = 0.85;

    // Movement (horizontal / pushing away from the attacker)
    public static volatile int   ENERGIZED_PUSH_SWIRL_TICKS = 18;
    public static volatile float ENERGIZED_PUSH_SWIRL_RADIUS = 0.90f;     // little orbit radius around the moving center
    public static volatile float ENERGIZED_PUSH_SWIRL_PUSH_DIST = 7.5f;   // how far the center drifts away from attacker
    public static volatile float ENERGIZED_PUSH_SWIRL_RAD_PER_TICK = 1.20f;
    public static volatile int   ENERGIZED_PUSH_SWIRL_TICK_PARTICLES = 12;

    // Big blast (uses explosion-style knockback + AoE)
    public static volatile float ENERGIZED_PUSH_BLAST_RADIUS = 7.5f;
    public static volatile float ENERGIZED_PUSH_BLAST_DAMAGE_SCALE = 1.70f;
    public static volatile float ENERGIZED_PUSH_BLAST_BONUS_DAMAGE = 28.0f;

    // Anim overrides (optional)
    public static volatile int   ENERGIZED_ANIM_PUSH_SWIRL_TICKS = 24;
    public static volatile float ENERGIZED_ANIM_PUSH_SWIRL_RADIUS = 1.05f;
    public static volatile float ENERGIZED_ANIM_PUSH_SWIRL_PUSH_DIST = 10.0f;
    public static volatile float ENERGIZED_ANIM_PUSH_SWIRL_RAD_PER_TICK = 1.28f;
    public static volatile int   ENERGIZED_ANIM_PUSH_SWIRL_TICK_PARTICLES = 16;

    public static volatile float ENERGIZED_ANIM_PUSH_BLAST_RADIUS = 9.0f;
    public static volatile float ENERGIZED_ANIM_PUSH_BLAST_DAMAGE_SCALE = 1.95f;
    public static volatile float ENERGIZED_ANIM_PUSH_BLAST_BONUS_DAMAGE = 36.0f;


    // Push-blast charge VFX (sphere grows around attacker while the push-swirl runs)
    public static volatile boolean ENERGIZED_PUSH_CHARGE_ENABLED = true;
    public static volatile float   ENERGIZED_PUSH_CHARGE_RADIUS_MIN = 0.65f;
    public static volatile float   ENERGIZED_PUSH_CHARGE_RADIUS_MAX = 3.25f;
    public static volatile int     ENERGIZED_PUSH_CHARGE_PARTICLES = 18;

    // Anim overrides (optional)
    public static volatile float   ENERGIZED_ANIM_PUSH_CHARGE_RADIUS_MAX = 3.75f;
    public static volatile int     ENERGIZED_ANIM_PUSH_CHARGE_PARTICLES = 24;

    // Variant proc: Rainbow Rush (pushback + rapid rainbow explosions + blink finisher)
    public static volatile boolean ENERGIZED_RUSH_ENABLED = true;
    /** If false, rush will never target players (useful for PvP safety). */
    public static volatile boolean ENERGIZED_RUSH_AFFECT_PLAYERS = true;

    public static volatile double  ENERGIZED_RUSH_CHANCE_FLAT = 0.15;
    public static volatile double  ENERGIZED_RUSH_CHANCE_ANIM = 0.14;
    public static volatile int     ENERGIZED_RUSH_COOLDOWN_TICKS = 140;       // 7s
    public static volatile int     ENERGIZED_ANIM_RUSH_COOLDOWN_TICKS = 120;  // 6s

    /** How many "micro explosions" before the finisher. */
    public static volatile int     ENERGIZED_RUSH_SHOTS = 6;
    public static volatile int     ENERGIZED_ANIM_RUSH_SHOTS = 8;

    /** Interval between micro explosions. */
    public static volatile int     ENERGIZED_RUSH_SHOT_INTERVAL_TICKS = 3;

    /** Delay after the last micro explosion before the finisher triggers (gives the dash a beat). */
    public static volatile int     ENERGIZED_RUSH_FINISH_DELAY_TICKS = 6;

    /** Initial shove when the rush starts. */
    public static volatile float   ENERGIZED_RUSH_INITIAL_KB_H = 0.95f;
    public static volatile float   ENERGIZED_RUSH_INITIAL_KB_Y = 0.08f;
    public static volatile float   ENERGIZED_ANIM_RUSH_INITIAL_KB_H = 1.10f;
    public static volatile float   ENERGIZED_ANIM_RUSH_INITIAL_KB_Y = 0.10f;

    /** Micro explosion AoE radius (0 = only primary target). */
    public static volatile float   ENERGIZED_RUSH_SHOT_RADIUS = 0.0f;
    public static volatile float   ENERGIZED_ANIM_RUSH_SHOT_RADIUS = 2.5f;

    /** Damage math for micro explosions. */
    public static volatile float   ENERGIZED_RUSH_SHOT_DAMAGE_SCALE = 0.40f;
    public static volatile float   ENERGIZED_RUSH_SHOT_BONUS_DAMAGE = 2.0f;
    public static volatile float   ENERGIZED_ANIM_RUSH_SHOT_DAMAGE_SCALE = 0.45f;
    public static volatile float   ENERGIZED_ANIM_RUSH_SHOT_BONUS_DAMAGE = 3.0f;

    /** Small shove per micro explosion (feels like repeated pops). */
    public static volatile float   ENERGIZED_RUSH_SHOT_KB_H = 0.14f;
    public static volatile float   ENERGIZED_RUSH_SHOT_KB_Y = 0.03f;
    public static volatile float   ENERGIZED_ANIM_RUSH_SHOT_KB_H = 0.18f;
    public static volatile float   ENERGIZED_ANIM_RUSH_SHOT_KB_Y = 0.04f;

    /** Finisher explosion AoE radius. */
    public static volatile float   ENERGIZED_RUSH_FINISH_RADIUS = 4.5f;
    public static volatile float   ENERGIZED_ANIM_RUSH_FINISH_RADIUS = 5.25f;

    /** Damage math for the finisher. */
    public static volatile float   ENERGIZED_RUSH_FINISH_DAMAGE_SCALE = 1.60f;
    public static volatile float   ENERGIZED_RUSH_FINISH_BONUS_DAMAGE = 24.0f;
    public static volatile float   ENERGIZED_ANIM_RUSH_FINISH_DAMAGE_SCALE = 1.85f;
    public static volatile float   ENERGIZED_ANIM_RUSH_FINISH_BONUS_DAMAGE = 32.0f;

    /** Blink attacker near the target before the finisher (server-side, collision-checked). */
    public static volatile boolean ENERGIZED_RUSH_BLINK_FINISH = true;
    public static volatile float   ENERGIZED_RUSH_BLINK_DISTANCE = 1.65f;
    public static volatile int     ENERGIZED_RUSH_BLINK_TRIES = 12;

    /** How hard the finisher launches the main target. */
    public static volatile float   ENERGIZED_RUSH_FINISH_KB_H_MULT = 2.35f;
    public static volatile float   ENERGIZED_RUSH_FINISH_KB_Y_MULT = 2.10f;
    public static volatile float   ENERGIZED_RUSH_FINISH_KB_Y_ADD  = 0.20f;

    /** If true, micro explosions can hit players reliably even when PROC_RESET_IFRAMES_PLAYERS is false. */
    public static volatile boolean ENERGIZED_RUSH_RESET_IFRAMES = true;

    /** Optional VFX: small charging rainbow sphere around attacker while the rush is active. */
    public static volatile boolean ENERGIZED_RUSH_CHARGE_VFX = true;
    public static volatile float   ENERGIZED_RUSH_CHARGE_RADIUS_MIN = 0.75f;
    public static volatile float   ENERGIZED_RUSH_CHARGE_RADIUS_MAX = 2.75f;
    public static volatile int     ENERGIZED_RUSH_CHARGE_PARTICLES = 14;



    // Variant proc: Rainbow Fist (mega hit + huge explosion + rainbow beams)
    public static volatile boolean ENERGIZED_FIST_ENABLED = true;
    /** If false, this variant will never target players (PvP safety). */
    public static volatile boolean ENERGIZED_FIST_AFFECT_PLAYERS = true;

    public static volatile double  ENERGIZED_FIST_CHANCE_FLAT = 0.10;
    public static volatile double  ENERGIZED_FIST_CHANCE_ANIM = 0.14;
    public static volatile int     ENERGIZED_FIST_COOLDOWN_TICKS = 220;      // 11s
    public static volatile int     ENERGIZED_ANIM_FIST_COOLDOWN_TICKS = 190; // 9.5s

    /** Damage = (procBase * scale + bonus) * globalMult */
    public static volatile float   ENERGIZED_FIST_DAMAGE_SCALE = 2.80f;
    public static volatile float   ENERGIZED_ANIM_FIST_DAMAGE_SCALE = 3.40f;
    public static volatile float   ENERGIZED_FIST_BONUS_DAMAGE = 180.0f;
    public static volatile float   ENERGIZED_ANIM_FIST_BONUS_DAMAGE = 240.0f;

    /** Splash radius around the punched target. Set <= 0 to only hit the main target. */
    public static volatile float   ENERGIZED_FIST_AOE_RADIUS = 3.75f;
    public static volatile float   ENERGIZED_ANIM_FIST_AOE_RADIUS = 4.50f;
    public static volatile int     ENERGIZED_FIST_MAX_HITS = 8;
    public static volatile int     ENERGIZED_ANIM_FIST_MAX_HITS = 10;

    /** Optional blink near the target right before the punch (collision-checked). */
    public static volatile boolean ENERGIZED_FIST_BLINK_TO_TARGET = true;
    public static volatile float   ENERGIZED_FIST_BLINK_DISTANCE = 1.45f;
    public static volatile int     ENERGIZED_FIST_BLINK_TRIES = 12;

    /** Extra launch on the punched target (and splash victims). */
    public static volatile float   ENERGIZED_FIST_KB_H_MULT = 3.10f;
    public static volatile float   ENERGIZED_FIST_KB_Y_MULT = 2.70f;
    public static volatile float   ENERGIZED_FIST_KB_Y_ADD  = 0.25f;

    // VFX knobs (beams + extra explosion density)
    public static volatile int     ENERGIZED_FIST_BEAM_COUNT = 10;
    public static volatile float   ENERGIZED_FIST_BEAM_LEN = 6.0f;
    public static volatile int     ENERGIZED_FIST_BEAM_STEPS = 18;
    public static volatile int     ENERGIZED_FIST_EXTRA_EXPLODES = 14;

    // Heal proc: short Body regen on attacker + bouncy particles (server-side)
    public static volatile boolean ENERGIZED_HEAL_ENABLED = true;
    public static volatile double  ENERGIZED_HEAL_PROC_CHANCE = 0.12;
    public static volatile double  ENERGIZED_ANIM_HEAL_PROC_CHANCE = 0.16;
    public static volatile int     ENERGIZED_HEAL_COOLDOWN_TICKS = 120;         // 6s
    public static volatile int     ENERGIZED_ANIM_HEAL_COOLDOWN_TICKS = 100;    // 5s

    /** Total heal duration in ticks (20 ticks = 1 second). */
    public static volatile int     ENERGIZED_HEAL_DURATION_TICKS = 60;          // 3s
    public static volatile int     ENERGIZED_ANIM_HEAL_DURATION_TICKS = 80;     // 4s

    /** Apply healing every N ticks while the buff is active. */
    public static volatile int     ENERGIZED_HEAL_INTERVAL_TICKS = 10;

    /** Heal nearby players too (radius in blocks). Set to 0 to disable AoE healing. */
    public static volatile float   ENERGIZED_HEAL_AOE_RADIUS = 6.0f;
    public static volatile float   ENERGIZED_ANIM_HEAL_AOE_RADIUS = 7.5f;

    /** Max nearby players to apply the heal buff to (0 = no cap). */
    public static volatile int     ENERGIZED_HEAL_AOE_MAX_TARGETS = 8;
    public static volatile int     ENERGIZED_ANIM_HEAL_AOE_MAX_TARGETS = 10;
// 0.5s

    /** Random percent of MAX Body restored per heal pulse (0.01 = 1%). */
    public static volatile float   ENERGIZED_HEAL_PERCENT_MIN = 0.30f;          // 30%
    public static volatile float   ENERGIZED_HEAL_PERCENT_MAX = 0.30f;          // 30%
    public static volatile float   ENERGIZED_ANIM_HEAL_PERCENT_MIN = 0.30f;     // 30%
    public static volatile float   ENERGIZED_ANIM_HEAL_PERCENT_MAX = 0.30f;     // 30%
    // VFX knobs
    public static volatile float   ENERGIZED_HEAL_PARTICLE_RADIUS = 1.15f;
    public static volatile float   ENERGIZED_ANIM_HEAL_PARTICLE_RADIUS = 1.45f;
    public static volatile int     ENERGIZED_HEAL_PARTICLES_PER_TICK = 10;
    public static volatile int     ENERGIZED_ANIM_HEAL_PARTICLES_PER_TICK = 14;
    public static volatile boolean ENERGIZED_HEAL_SHOW_RAINBOW_DUST = true;


    public static volatile int    ENERGIZED_SWIRL_TICKS = 16;
    public static volatile float  ENERGIZED_SWIRL_RADIUS = 0.85f;
    public static volatile float  ENERGIZED_SWIRL_LIFT_TOTAL = 2.6f;
    public static volatile float  ENERGIZED_SWIRL_RAD_PER_TICK = 0.95f;
    public static volatile int    ENERGIZED_SWIRL_TICK_PARTICLES = 8;

    public static volatile float  ENERGIZED_SWIRL_EXPLODE_RADIUS = 4.25f;
    public static volatile float  ENERGIZED_SWIRL_EXPLODE_DAMAGE_SCALE = 1.20f;
    public static volatile float  ENERGIZED_SWIRL_EXPLODE_BONUS_DAMAGE = 10.0f;

    /** If false, players only get VFX/SFX and damage (no forced swirl movement). */
    public static volatile boolean ENERGIZED_SWIRL_AFFECT_PLAYERS = true;

    // -------------------------------------------------------------
    // Swirl DoT
    // -------------------------------------------------------------
    public static volatile boolean ENERGIZED_SWIRL_DOT_ENABLED = true;
    public static volatile int     ENERGIZED_SWIRL_DOT_INTERVAL_TICKS = 4;
    public static volatile float   ENERGIZED_SWIRL_DOT_DAMAGE_SCALE = 0.12f;
    public static volatile float   ENERGIZED_SWIRL_DOT_BONUS_DAMAGE  = 1.2f;
    public static volatile boolean ENERGIZED_SWIRL_DOT_RESET_IFRAMES = true;
    public static volatile boolean ENERGIZED_SWIRL_DOT_AFFECT_PLAYERS = true;

    // -------------------------------------------------------------
    // ANIM overrides (stronger/longer)
    // -------------------------------------------------------------
    public static volatile int    ENERGIZED_ANIM_SWIRL_TICKS = 24;
    public static volatile float  ENERGIZED_ANIM_SWIRL_RADIUS = 1.05f;
    public static volatile float  ENERGIZED_ANIM_SWIRL_LIFT_TOTAL = 3.2f;
    public static volatile float  ENERGIZED_ANIM_SWIRL_RAD_PER_TICK = 1.10f;
    public static volatile int    ENERGIZED_ANIM_SWIRL_TICK_PARTICLES = 12;

    public static volatile float  ENERGIZED_ANIM_SWIRL_EXPLODE_RADIUS = 5.0f;
    public static volatile float  ENERGIZED_ANIM_SWIRL_EXPLODE_DAMAGE_SCALE = 1.45f;
    public static volatile float  ENERGIZED_ANIM_SWIRL_EXPLODE_BONUS_DAMAGE = 16.0f;

    public static volatile int    ENERGIZED_ANIM_SWIRL_DOT_INTERVAL_TICKS = 3;
    public static volatile float  ENERGIZED_ANIM_SWIRL_DOT_DAMAGE_SCALE = 0.16f;
    public static volatile float  ENERGIZED_ANIM_SWIRL_DOT_BONUS_DAMAGE  = 1.8f;

    // Rainbow particle tuning
    public static volatile int    RAINBOW_STEPS = 10;
    public static volatile int    RAINBOW_PARTICLES_BURST = 45;
    public static volatile int    RAINBOW_PARTICLES_SPARK = 14;

    // PVP scaling (optional safety)
    public static volatile float  DAMAGE_VS_PLAYERS_SCALE = 0.70f;

    // Knockback feel
    public static volatile float  KB_H = 0.55f;
    public static volatile float  KB_Y = 0.18f;

    // -------------------------------------------------------------
    // Internal NBT keys (stored on the TARGET while swirling)
    // -------------------------------------------------------------

    // -------------------------------------------------------------
    // Heal buff keys (stored on the PLAYER while healing)
    // -------------------------------------------------------------
    private static final String HEAL_KEY_END   = "HexHeal_End";
    private static final String HEAL_KEY_NEXT  = "HexHeal_Next";
    private static final String HEAL_KEY_PCT   = "HexHeal_Pct";
    private static final String HEAL_KEY_PCT_MIN = "HexHeal_PctMin";
    private static final String HEAL_KEY_PCT_MAX = "HexHeal_PctMax";
    private static final String HEAL_KEY_ANIM  = "HexHeal_Anim";

    private static final String SW_KEY_END    = "HexSwirl_End";
    private static final String SW_KEY_START  = "HexSwirl_Start";
    private static final String SW_KEY_CX     = "HexSwirl_CX";
    private static final String SW_KEY_CZ     = "HexSwirl_CZ";
    private static final String SW_KEY_BASEY  = "HexSwirl_BaseY";
    private static final String SW_KEY_OWNER  = "HexSwirl_OwnerId";
    private static final String SW_KEY_EXRAD  = "HexSwirl_ExRad";
    private static final String SW_KEY_EXDMG  = "HexSwirl_ExDmg";
    private static final String SW_KEY_DOT    = "HexSwirl_Dot";
    private static final String SW_KEY_DOTINT = "HexSwirl_DotInt";
    private static final String SW_KEY_RAD    = "HexSwirl_Rad";
    private static final String SW_KEY_LIFT   = "HexSwirl_Lift";
    private static final String SW_KEY_SPIN   = "HexSwirl_Spin";
    private static final String SW_KEY_TICKP  = "HexSwirl_TickP";
    private static final String SW_KEY_MODE  = "HexSwirl_Mode"; // 0=normal, 1=push-blast
    private static final String SW_KEY_DIRX  = "HexSwirl_DirX";
    private static final String SW_KEY_DIRZ  = "HexSwirl_DirZ";
    private static final String SW_KEY_PUSH  = "HexSwirl_Push";

    // -------------------------------------------------------------
    // Rainbow Rush keys (stored on the TARGET while the rush sequence runs)
    // -------------------------------------------------------------
    private static final String RUSH_KEY_END     = "HexRush_End";
    private static final String RUSH_KEY_NEXT    = "HexRush_Next";
    private static final String RUSH_KEY_OWNER   = "HexRush_OwnerId";
    private static final String RUSH_KEY_SHOTS   = "HexRush_Shots";
    private static final String RUSH_KEY_TOTAL   = "HexRush_Total";
    private static final String RUSH_KEY_INT     = "HexRush_Int";
    private static final String RUSH_KEY_FDELAY  = "HexRush_FDelay";
    private static final String RUSH_KEY_SHOTDMG = "HexRush_ShotDmg";
    private static final String RUSH_KEY_FINALDMG= "HexRush_FinalDmg";
    private static final String RUSH_KEY_SHOTRAD = "HexRush_ShotRad";
    private static final String RUSH_KEY_FINALRAD= "HexRush_FinalRad";
    private static final String RUSH_KEY_ANIM    = "HexRush_Anim";

    // -------------------------------------------------------------
    // Fire Punch keys (stored on the ATTACKER while the fire-hands window is active)
    // -------------------------------------------------------------
    private static final String FP_KEY_END     = "HexFirePunch_End";
    private static final String FP_KEY_NEXT    = "HexFirePunch_Next";
    private static final String FP_KEY_ANIM    = "HexFirePunch_Anim";
    private static final String FP_KEY_LASTTGT = "HexFirePunch_LastTgt";
    private static final String FP_KEY_FIN     = "HexFirePunch_Fin";

    // -------------------------------------------------------------
    // Fire DoT keys (stored on the TARGET for the finisher)
    // -------------------------------------------------------------
    private static final String FD_KEY_END     = "HexFireDot_End";
    private static final String FD_KEY_NEXT    = "HexFireDot_Next";
    private static final String FD_KEY_OWNER   = "HexFireDot_OwnerId";
    private static final String FD_KEY_DMG     = "HexFireDot_Dmg";
    private static final String FD_KEY_INT     = "HexFireDot_Int";
    private static final String FD_KEY_EXDMG   = "HexFireDot_ExDmg";
    private static final String FD_KEY_EXRAD   = "HexFireDot_ExRad";
    private static final String FD_KEY_ANIM    = "HexFireDot_Anim";


    // -------------------------------------------------------------
    // VOID Entropy keys (stored on the TARGET while entropy runs)
    // -------------------------------------------------------------
    private static final String VE_KEY_END     = "HexVoidEnt_End";
    private static final String VE_KEY_NEXT    = "HexVoidEnt_Next";
    private static final String VE_KEY_OWNER   = "HexVoidEnt_OwnerId";
    private static final String VE_KEY_DMG     = "HexVoidEnt_Dmg";
    private static final String VE_KEY_INT     = "HexVoidEnt_Int";
    private static final String VE_KEY_TOTAL   = "HexVoidEnt_Total";
    private static final String VE_KEY_ANIM    = "HexVoidEnt_Anim";


    // VOID Abyss Mark keys (stored on the TARGET while marked)
    private static final String VAM_KEY_OWNER  = "HexVoidAbyss_OwnerId";
    private static final String VAM_KEY_STACKS = "HexVoidAbyss_Stacks"; // legacy (old stacking design)
    private static final String VAM_KEY_EXPIRE = "HexVoidAbyss_Expire";
    private static final String VAM_KEY_ANIM   = "HexVoidAbyss_IsAnim";

    private static final String NS_KEY_CD_END = "HexVoidDashCDEnd";
    private static final String NS_KEY_CD_MAX = "HexVoidDashCDMax";





    // Null Shell: charge meter (stored on PLAYER entity NBT)
    private static final String VNS_KEY_CHARGE       = "HexVoidNS_Charge";      // int
    private static final String VNS_KEY_NEXT_PASSIVE = "HexVoidNS_NextPassive"; // long (server ticks)
    private static final String VNS_KEY_LAST_PCT     = "HexVoidNS_LastPct";     // int (0..100)
    private static final String VNS_KEY_LAST_STAMP   = "HexVoidNS_LastStamp";   // long (server ticks)

    // Null Shell: defense buff (Void Protection)
    private static final String VNS_KEY_DEF_END      = "HexVoidNS_DefEnd";      // long (server ticks)
    private static final String VNS_KEY_DEF_NEXT_FX  = "HexVoidNS_DefNextFX";  // long (server ticks)
    private static final String VNS_KEY_PUSH_START    = "HexVoidNS_PushStart";    // long (server ticks)
    private static final String VNS_KEY_PUSH_CHARGING = "HexVoidNS_PushCharging"; // byte (0/1)

    // Quick debug (server-side chat prints) - set false to silence
    public static volatile boolean VOID_NULL_SHELL_DEBUG = true;
    private static final String VNS_KEY_DBG_NEXT = "HexVoidNS_DbgNext"; // long (server ticks)
    private static void nsDbg(EntityPlayer p, String msg){
        if (!VOID_NULL_SHELL_DEBUG || p == null || p.worldObj == null || p.worldObj.isRemote) return;
        try{
            NBTTagCompound d = p.getEntityData();
            long now = serverNow(p.worldObj);
            long next = (d != null) ? d.getLong(VNS_KEY_DBG_NEXT) : 0L;
            if (next != 0L && now < next) return;
            if (d != null) d.setLong(VNS_KEY_DBG_NEXT, now + 10L); // ~0.5s throttle
            p.addChatMessage(new net.minecraft.util.ChatComponentText("§8[NSDBG] §7" + msg));
        } catch (Throwable ignored) {}
    }
    // Null Shell: local DBC damage applier (used only when global DAMAGE_APPLIER is not configured)
    private static final HexDBCBridgeDamageApplier NS_LOCAL_DBC_APPLIER = new HexDBCBridgeDamageApplier();





    // -------------------------------------------------------------
    // API (optional external tuning)
    // -------------------------------------------------------------
    public static final class API {
        private API(){}

        public static void setDamageApplier(DamageApplier applier){ DAMAGE_APPLIER = applier; }

        public static void setEnergizedFlatChance(double v){ ENERGIZED_FLAT_PROC_CHANCE = clamp01(v); }
        public static void setEnergizedAnimChance(double v){ ENERGIZED_ANIM_PROC_CHANCE = clamp01(v); }

        public static void setEnergizedFlatCooldownTicks(int t){ ENERGIZED_FLAT_COOLDOWN_TICKS = Math.max(0, t); }
        public static void setEnergizedAnimCooldownTicks(int t){ ENERGIZED_ANIM_COOLDOWN_TICKS = Math.max(0, t); }

        public static void setEnergizedFlatRadius(float r){ ENERGIZED_FLAT_RADIUS = Math.max(0f, r); }
        public static void setEnergizedAnimRadius(float r){ ENERGIZED_ANIM_RADIUS = Math.max(0f, r); }

        public static void setEnergizedSwirlEnabled(boolean v){ ENERGIZED_SWIRL_ENABLED = v; }
        public static void setEnergizedSwirlTicks(int t){ ENERGIZED_SWIRL_TICKS = Math.max(0, t); }
        public static void setEnergizedSwirlExplodeRadius(float r){ ENERGIZED_SWIRL_EXPLODE_RADIUS = Math.max(0f, r); }

        public static void setFirePunchEnabled(boolean v){ FIRE_PUNCH_ENABLED = v; }
        public static void setFirePunchAffectPlayers(boolean v){ FIRE_PUNCH_AFFECT_PLAYERS = v; }
        public static void setFirePunchFlatChance(double v){ FIRE_PUNCH_CHANCE_FLAT = clamp01(v); }
        public static void setFirePunchAnimChance(double v){ FIRE_PUNCH_CHANCE_ANIM = clamp01(v); }
        public static void setFirePunchCooldownTicks(int t){ FIRE_PUNCH_COOLDOWN_TICKS = Math.max(0, t); }
        public static void setFirePunchAnimCooldownTicks(int t){ FIRE_PUNCH_ANIM_COOLDOWN_TICKS = Math.max(0, t); }
        public static void setFirePunchDurationTicks(int t){ FIRE_PUNCH_DURATION_TICKS = Math.max(1, t); }
        public static void setFirePunchAnimDurationTicks(int t){ FIRE_PUNCH_ANIM_DURATION_TICKS = Math.max(1, t); }


        public static void setProcResetIFramesNonPlayers(boolean v){ PROC_RESET_IFRAMES_NONPLAYERS = v; }
        public static void setProcResetIFramesPlayers(boolean v){ PROC_RESET_IFRAMES_PLAYERS = v; }
        public static void setProcForceSetHealthFallbackNonPlayers(boolean v){ PROC_FORCE_SETHEALTH_FALLBACK_NONPLAYERS = v; }

        // Dev boost knobs (still only applies if DEV_ENV == true)
        public static void setDevProcDamageBoost(boolean v){ DEV_PROC_DAMAGE_BOOST = v; }
        public static void setDevProcDamageMult(float v){ DEV_PROC_DAMAGE_MULT = v; }
        public static void setDevProcDamageAdd(float v){ DEV_PROC_DAMAGE_ADD = v; }

        private static double clamp01(double v){ return v < 0 ? 0 : (v > 1 ? 1 : v); }
    }

    // -------------------------------------------------------------
    // EVENTS: some mods fire Attack but not Hurt (or vice-versa)
    // We listen to both and de-dupe per tick.
    // -------------------------------------------------------------

    @SubscribeEvent
    public void onLivingAttack(LivingAttackEvent e){
        if (e == null || e.entityLiving == null || e.source == null) return;

        // Null Shell: TRUE dodge happens as early as possible (Attack phase).
        // If it procs, we cancel this hit completely and perform the dash + trail.
        if (VOID_ENABLED && VOID_NULL_SHELL_ENABLED && e.entityLiving instanceof EntityPlayer){
            EntityPlayer victim = (EntityPlayer) e.entityLiving;
            long now = serverNow(victim.worldObj);
            if (tryVoidNullShellDodge(victim, e.source, now)){
                if (e.isCancelable()) e.setCanceled(true);
                return;
            }
        }

        handleHit(e.source, e.entityLiving, e.ammount, "Attack");
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent e){
        if (e == null || e.entityLiving == null || e.source == null) return;

        // Null Shell: TRUE dodge fallback (some damage sources skip Attack and go straight to Hurt).
        if (VOID_ENABLED && VOID_NULL_SHELL_ENABLED && e.entityLiving instanceof EntityPlayer){
            EntityPlayer victim = (EntityPlayer) e.entityLiving;
            long now = serverNow(victim.worldObj);
            if (tryVoidNullShellDodge(victim, e.source, now)){
                if (e.isCancelable()) e.setCanceled(true);
                return;
            }
        }

        // Null Shell: Void Protection reduces incoming damage while active.
        if (VOID_ENABLED && VOID_NULL_SHELL_ENABLED && e.entityLiving instanceof EntityPlayer){
            EntityPlayer victim = (EntityPlayer) e.entityLiving;
            long now = serverNow(victim.worldObj);
            if (isNullShellDefenseActive(victim, now)){
                // Scale the Forge event amount (affects vanilla + most modded hits)
                e.ammount = e.ammount * NS_DEFENSE_INCOMING_MULT;
                if (e.ammount < 0f) e.ammount = 0f;
            }
        }

        // VOID: defensive procs when the PLAYER is damaged (e.g. Gravity Well)
        if (VOID_ENABLED && e.entityLiving instanceof EntityPlayer){
            tryProcVoidOnDamaged((EntityPlayer) e.entityLiving, e.source, e.ammount);
        }

        handleHit(e.source, e.entityLiving, e.ammount, "Hurt");
    }

    private void handleHit(DamageSource src, EntityLivingBase target, float amount, String phase){
        Entity srcEnt = src.getEntity();
        Entity direct = src.getSourceOfDamage();

        EntityPlayer player = null;
        if (srcEnt instanceof EntityPlayer){
            player = (EntityPlayer) srcEnt;
        } else if (direct instanceof EntityPlayer){
            player = (EntityPlayer) direct;
        } else {
            return;
        }

        // de-dupe Attack+Hurt on same tick for same target
        if (!markOncePerTick(player, target)) return;

        // only do player melee-ish damage (filters out our own hexorb damage + projectiles)
        if (!isPlayerMeleeDamage(src, player)) {
            if (DEBUG_PROC) debugOncePerSecond(player,
                    "[HexOrb] blocked(" + phase + ") type=" + src.getDamageType() +
                            " proj=" + src.isProjectile() +
                            " srcEnt=" + safeEntName(src.getEntity()) +
                            " direct=" + safeEntName(src.getSourceOfDamage()));
            return;
        }

        EnergizedMatch match = findEnergizedMatch(player);
        FirePillMatch fireMatch = findFirePillMatch(player);
        FracturedMatch fracturedMatch = findFracturedMatch(player);

        // VOID: offensive procs when the PLAYER hits something (e.g. Gravity Well)
        if (VOID_ENABLED){
            tryProcVoidOnAttack(player, target, amount);
        }

        boolean hasEnergized = match.hasFlat || match.hasAnim;
        boolean hasFirePill  = fireMatch.hasFlat || fireMatch.hasAnim;
        boolean hasFractured = fracturedMatch.hasAnim || fracturedMatch.hasFlat;
        boolean fireActive   = isFirePunchActive(player);

        if (!hasEnergized && !hasFirePill && !fireActive && !hasFractured){
            if (DEBUG_PROC) debugOncePerSecond(player, "[HexOrb] no supported gem/pill on held/armor");
            return;
        }

        if (DEBUG_PROC && DEBUG_SOCKET_KEYS){
            if (hasEnergized && match.debugStack != null){
                debugOncePerSecond(player, "[HexOrb] hit(" + phase + ") energized " +
                        "tgt=" + safeEntName(target) + " " +
                        "dmg=" + amount + " hasFlat=" + match.hasFlat + " hasAnim=" + match.hasAnim);
                debugSocketKeysOncePerSecond(player, match.debugStack);
            }
            if (hasFirePill && fireMatch.debugStack != null){
                debugOncePerSecond(player, "[HexOrb] hit(" + phase + ") firepill " +
                        "tgt=" + safeEntName(target) + " " +
                        "dmg=" + amount + " hasFlat=" + fireMatch.hasFlat + " hasAnim=" + fireMatch.hasAnim);
                debugSocketKeysOncePerSecond(player, fireMatch.debugStack);
            }
        }

        // Fire Pill: Fire Punch buff (independent of energized procs)
        if (FIRE_PUNCH_ENABLED){
            if (!(target instanceof EntityPlayer) || FIRE_PUNCH_AFFECT_PLAYERS){
                if (hasFirePill){
                    // Prefer ANIM if present, else FLAT
                    boolean fireAnim = fireMatch.hasAnim;
                    tryStartFirePunch(player, fireAnim);
                }
                // If active, apply bonus hit damage + track finisher DoT/explosion
                applyFirePunchHit(player, target, amount);
            }
        }

        // Existing energized procs
        if (hasEnergized){
            if (match.hasAnim){
                tryProcEnergizedAnim(player, target, amount);
                tryProcEnergizedHeal(player, true);
            } else {
                tryProcEnergizedFlat(player, target, amount);
                tryProcEnergizedHeal(player, false);
            }
        }

        // Fractured: low-body chaos surge
        if (hasFractured && FRACTURED_ENABLED){
            boolean fracAnim = fracturedMatch.hasAnim;
            tryProcFracturedLowBody(player, target, amount, fracAnim);
        }

    }


    private static final class FracturedMatch {
        boolean hasFlat;
        boolean hasAnim;
        ItemStack debugStack;
    }

    /**
     * Checks fractured gems on held item first, then armor.
     * Priority: held anim > held flat > armor anim > armor flat.
     */
    private static FracturedMatch findFracturedMatch(EntityPlayer p){
        FracturedMatch m = new FracturedMatch();
        if (p == null) return m;

        ItemStack held = p.getHeldItem();
        if (held != null){
            if (hasGemSocketed(held, GEM_FRACTURED_ANIM)){
                m.hasAnim = true;
                m.debugStack = held;
                return m;
            }
            if (hasGemSocketed(held, GEM_FRACTURED_FLAT)){
                m.hasFlat = true;
                m.debugStack = held;
                return m;
            }
        }

        if (p.inventory != null && p.inventory.armorInventory != null){
            ItemStack[] armor = p.inventory.armorInventory;
            for (int i = 0; i < armor.length; i++){
                ItemStack a = armor[i];
                if (a == null) continue;
                if (hasGemSocketed(a, GEM_FRACTURED_ANIM)){
                    m.hasAnim = true;
                    m.debugStack = a;
                    return m;
                }
            }
            for (int i = 0; i < armor.length; i++){
                ItemStack a = armor[i];
                if (a == null) continue;
                if (hasGemSocketed(a, GEM_FRACTURED_FLAT)){
                    m.hasFlat = true;
                    m.debugStack = a;
                    return m;
                }
            }
        }

        return m;
    }


    // -------------------------------------------------------------
    // VOID: match + type discovery (Void gem socketed on held item or armor)
    // -------------------------------------------------------------

    // -------------------------------------------------------------
    // VOID: match + type discovery (Void gem socketed on held item or armor)
    // -------------------------------------------------------------
    private static final class VoidMatch {
        boolean hasFlat;
        boolean hasAnim;
        ItemStack debugStack; // host item that contains the socketed void gem OR the orb item itself (direct wear/hold)
        String voidType;      // rolled type (best effort)
        boolean found;        // true if we found a matching void orb/gem host
    }

    /** Some servers treat orbs as wearable/held items (not only socketed). Detect that too. */
    private static boolean isDirectVoidOrbItem(ItemStack s){
        if (s == null) return false;
        try {
            NBTTagCompound t = s.getTagCompound();
            if (t == null) return false;

            String k = null;
            if (t.hasKey("HexGemKey")) k = t.getString("HexGemKey");
            else if (t.hasKey("HexGemIcon")) k = t.getString("HexGemIcon");
            else if (t.hasKey("HexOrbIcon")) k = t.getString("HexOrbIcon");
            else if (t.hasKey("GemKey")) k = t.getString("GemKey");

            if (k != null && k.length() > 0){
                String nk = normalizeGemKey(k);
                return nk.contains("void");
            }
        } catch (Throwable ignored) {}

        // fallback: name contains "Void"
        try {
            String dn = s.getDisplayName();
            return dn != null && dn.toLowerCase().contains("void");
        } catch (Throwable ignored) {}
        return false;
    }

    private static String directVoidGemKey(ItemStack s){
        if (s == null) return "";
        try {
            NBTTagCompound t = s.getTagCompound();
            if (t == null) return "";
            String k = null;
            if (t.hasKey("HexGemKey")) k = t.getString("HexGemKey");
            else if (t.hasKey("HexGemIcon")) k = t.getString("HexGemIcon");
            else if (t.hasKey("HexOrbIcon")) k = t.getString("HexOrbIcon");
            else if (t.hasKey("GemKey")) k = t.getString("GemKey");
            return (k != null) ? k : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static void fillVoidMatchFromDirectItem(VoidMatch m, ItemStack s){
        if (m == null || s == null) return;

        String key = directVoidGemKey(s);
        String nk = normalizeGemKey(key);

        boolean anim = false;
        boolean flat = false;

        // Prefer exact compare against known keys, otherwise fall back to substring checks.
        if (keyMatchesWanted(key, GEM_VOID_ANIM)) anim = true;
        else if (keyMatchesWanted(key, GEM_VOID_FLAT)) flat = true;
        else {
            // tolerate other naming variants
            if (nk.contains("void") && nk.contains("anim")) anim = true;
            else if (nk.contains("void")) flat = true;
        }

        m.hasAnim = anim;
        m.hasFlat = (!anim) && flat; // if anim, treat as anim only
        if (!anim && !flat){
            // still treat it as flat if it is a direct void orb by name
            m.hasFlat = true;
        }

        m.debugStack = s;

        String vt = readVoidTypeFromHostItem(s);
        if (vt == null || vt.trim().isEmpty()){
            // If type was not stored (e.g. preview item), default to Gravity Well so the proc is still visible.
            vt = "Gravity Well";
        }
        m.voidType = vt;
        m.found = true;
    }

    private static boolean keyMatchesWanted(String raw, String wantedKey){
        if (raw == null || wantedKey == null) return false;
        String a = normalizeGemKey(raw);
        String b = normalizeGemKey(wantedKey);
        if (a.equals(b)) return true;
        if (a.startsWith("gems/") && a.substring(5).equals(b)) return true;
        if (b.startsWith("gems/") && b.substring(5).equals(a)) return true;
        return false;
    }

    private static VoidMatch findVoidMatch(EntityPlayer p){
        VoidMatch m = new VoidMatch();
        if (p == null) return m;

        ItemStack held = p.getHeldItem();

        // Direct-void (holding / wearing the orb item itself)
        if (isDirectVoidOrbItem(held)){
            fillVoidMatchFromDirectItem(m, held); // sets found
            return m;
        }

        // Socketed on held item
        if (held != null){
            if (hasGemSocketed(held, GEM_VOID_ANIM)){
                m.hasAnim = true;
                m.debugStack = held;
                m.voidType = readVoidTypeFromHostItem(held);
                if (m.voidType == null || m.voidType.trim().isEmpty()) m.voidType = "Gravity Well";
                m.found = true;
                return m;
            }
            if (hasGemSocketed(held, GEM_VOID_FLAT)){
                m.hasFlat = true;
                m.debugStack = held;
                m.voidType = readVoidTypeFromHostItem(held);
                if (m.voidType == null || m.voidType.trim().isEmpty()) m.voidType = "Gravity Well";
                m.found = true;
                return m;
            }
        }

        // Armor: direct orb items first (wearables)
        if (p.inventory != null && p.inventory.armorInventory != null){
            ItemStack[] armor = p.inventory.armorInventory;
            for (int i = 0; i < armor.length; i++){
                ItemStack a = armor[i];
                if (isDirectVoidOrbItem(a)){
                    fillVoidMatchFromDirectItem(m, a); // sets found
                    return m;
                }
            }

            // Armor: socketed
            for (int i = 0; i < armor.length; i++){
                ItemStack a = armor[i];
                if (a == null) continue;
                if (hasGemSocketed(a, GEM_VOID_ANIM)){
                    m.hasAnim = true;
                    m.debugStack = a;
                    m.voidType = readVoidTypeFromHostItem(a);
                    if (m.voidType == null || m.voidType.trim().isEmpty()) m.voidType = "Gravity Well";
                    m.found = true;
                    return m;
                }
            }
            for (int i = 0; i < armor.length; i++){
                ItemStack a = armor[i];
                if (a == null) continue;
                if (hasGemSocketed(a, GEM_VOID_FLAT)){
                    m.hasFlat = true;
                    m.debugStack = a;
                    m.voidType = readVoidTypeFromHostItem(a);
                    if (m.voidType == null || m.voidType.trim().isEmpty()) m.voidType = "Gravity Well";
                    m.found = true;
                    return m;
                }
            }
        }

        return m;
    }

    // -------------------------------------------------------------
    // Void: Null Shell - Dash/Trail (Step 1)
    // -------------------------------------------------------------

    /**
     * A short-lived damaging segment between two points.
     * Each entity takes a "big" hit the first time it touches the trail,
     * then periodic DoT pulses while it remains inside.
     */
    private static final class VoidTrail {
        final int dim;
        final int ownerId;
        final double x0, y0, z0;
        final double x1, y1, z1;

        int ticksLeft;
        long nextDotAt;

        final java.util.HashSet<Integer> hitOnce = new java.util.HashSet<Integer>();

        VoidTrail(EntityPlayer owner, double sx, double sy, double sz, double ex, double ey, double ez, long nowWorld){
            dim = owner.worldObj.provider.dimensionId;
            ownerId = owner.getEntityId();
            x0 = sx; y0 = sy; z0 = sz;
            x1 = ex; y1 = ey; z1 = ez;

            ticksLeft = NS_TRAIL_LIFE;
            nextDotAt = nowWorld + (long)Math.max(1, NS_TRAIL_DOT_INTERVAL);
        }
    }

    private static final java.util.ArrayList<VoidTrail> VOID_TRAILS = new java.util.ArrayList<VoidTrail>();
    private static final java.util.HashMap<Integer, Long> NS_TRAIL_LAST_TICK_BY_DIM = new java.util.HashMap<Integer, Long>();

    /** Ensure we tick trails once per world-tick (this class only listens on MinecraftForge.EVENT_BUS). */
    private static void maybeTickNullShellTrailsOncePerWorld(World w){
        if (w == null || w.isRemote) return;
        int dim = w.provider.dimensionId;
        long now = w.getTotalWorldTime();

        Integer k = Integer.valueOf(dim);
        Long last = NS_TRAIL_LAST_TICK_BY_DIM.get(k);
        if (last != null && last.longValue() == now) return;

        NS_TRAIL_LAST_TICK_BY_DIM.put(k, Long.valueOf(now));
        tickNullShellTrails(w, now);
    }

    private static void tickNullShellTrails(World w, long nowWorld){
        if (VOID_TRAILS.isEmpty()) return;

        java.util.Iterator<VoidTrail> it = VOID_TRAILS.iterator();
        while (it.hasNext()){
            VoidTrail t = it.next();
            if (t == null){
                it.remove();
                continue;
            }

            if (t.dim != w.provider.dimensionId) continue;

            t.ticksLeft--;
            if (t.ticksLeft <= 0){
                it.remove();
                continue;
            }

            // Particles so the trail is visible
            spawnNullShellTrailParticles(w, t, nowWorld);

            // Periodic DoT pulse
            if (nowWorld >= t.nextDotAt){
                applyNullShellTrailDamagePulse(w, t, false);
                t.nextDotAt = nowWorld + (long)Math.max(1, NS_TRAIL_DOT_INTERVAL);
            }
        }
    }

    private static void spawnNullShellTrail(EntityPlayer owner, double sx, double sy, double sz, double ex, double ey, double ez){
        if (owner == null) return;
        World w = owner.worldObj;
        if (w == null || w.isRemote) return;

        long nowWorld = w.getTotalWorldTime();
        VoidTrail t = new VoidTrail(owner, sx, sy, sz, ex, ey, ez, nowWorld);
        VOID_TRAILS.add(t);

        // Big first-contact hit on creation
        applyNullShellTrailDamagePulse(w, t, true);

        // Initial particles
        spawnNullShellTrailParticles(w, t, nowWorld);
    }

    private static void spawnNullShellTrailParticles(World w, VoidTrail t, long nowWorld){
        if (!(w instanceof WorldServer) || t == null) return;
        WorldServer ws = (WorldServer) w;

        double dx = t.x1 - t.x0;
        double dy = t.y1 - t.y0;
        double dz = t.z1 - t.z0;

        int steps = 6;
        for (int i = 0; i <= steps; i++){
            double a = (double)i / (double)steps;
            double px = t.x0 + dx * a;
            double py = t.y0 + dy * a;
            double pz = t.z0 + dz * a;

            ws.func_147487_a("portal", px, py, pz, 2, 0.12, 0.08, 0.12, 0.01);
            if (((nowWorld + (long)i) & 1L) == 0L){
                ws.func_147487_a("witchMagic", px, py, pz, 1, 0.10, 0.06, 0.10, 0.01);
            }
        }
    }

    /**
     * Apply either:
     *  - bigHit=true  => only BIG hits for entities touching the trail for the first time
     *  - bigHit=false => DoT pulse for entities already "primed" by a first contact
     *
     * (If an entity enters the trail later, it will still get its big first-hit, then DoT.)
     */
    private static void applyNullShellTrailDamagePulse(World w, VoidTrail t, boolean bigHit){
        if (w == null || t == null) return;

        net.minecraft.entity.Entity entOwner = w.getEntityByID(t.ownerId);
        if (!(entOwner instanceof EntityPlayer)) return;
        EntityPlayer owner = (EntityPlayer) entOwner;
        if (owner.isDead) return;

        double minX = Math.min(t.x0, t.x1) - NS_TRAIL_RADIUS;
        double minY = Math.min(t.y0, t.y1) - 1.0;
        double minZ = Math.min(t.z0, t.z1) - NS_TRAIL_RADIUS;
        double maxX = Math.max(t.x0, t.x1) + NS_TRAIL_RADIUS;
        double maxY = Math.max(t.y0, t.y1) + 2.0;
        double maxZ = Math.max(t.z0, t.z1) + NS_TRAIL_RADIUS;

        AxisAlignedBB bb = AxisAlignedBB.getBoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
        java.util.List list = w.getEntitiesWithinAABB(EntityLivingBase.class, bb);
        if (list == null || list.isEmpty()) return;

        for (int i = 0; i < list.size(); i++){
            Object o = list.get(i);
            if (!(o instanceof EntityLivingBase)) continue;
            EntityLivingBase e = (EntityLivingBase) o;
            if (e == null || e.isDead) continue;
            if (e == owner) continue;
            if (!isDamageable(e)) continue;

            if (!isPointNearSegment2D(e.posX, e.posZ, t.x0, t.z0, t.x1, t.z1, NS_TRAIL_RADIUS)) continue;

            Integer id = Integer.valueOf(e.getEntityId());
            boolean first = !t.hitOnce.contains(id);

            if (first){
                t.hitOnce.add(id);
                dealNullShellTrailDamage(owner, e, NS_TRAIL_BIG_MULT);
            } else if (!bigHit){
                dealNullShellTrailDamage(owner, e, NS_TRAIL_DOT_MULT);
            }
        }
    }

    /** 2D distance check (horizontal) between point and segment. */
    /** Basic sanity filter for proc/trail damage (avoid dead entities & creative players). */
    private static boolean isDamageable(EntityLivingBase e){
        if (e == null || e.isDead) return false;
        try { if (!e.isEntityAlive()) return false; } catch (Throwable ignored) {}
        try { if (e.getHealth() <= 0.0f) return false; } catch (Throwable ignored) {}
        if (e instanceof EntityPlayer){
            EntityPlayer ep = (EntityPlayer) e;
            try { if (ep.capabilities != null && ep.capabilities.isCreativeMode) return false; } catch (Throwable ignored) {}
        }
        return true;
    }

    private static boolean isPointNearSegment2D(double px, double pz, double ax, double az, double bx, double bz, double radius){
        double abx = bx - ax;
        double abz = bz - az;
        double apx = px - ax;
        double apz = pz - az;

        double abLenSq = abx*abx + abz*abz;
        double t = 0.0;

        if (abLenSq > 1.0e-6){
            t = (apx*abx + apz*abz) / abLenSq;
            if (t < 0.0) t = 0.0;
            else if (t > 1.0) t = 1.0;
        }

        double cx = ax + abx * t;
        double cz = az + abz * t;

        double dx = px - cx;
        double dz = pz - cz;
        return (dx*dx + dz*dz) <= (radius*radius);
    }


    private static void dealNullShellProcDamage(EntityPlayer attacker, EntityLivingBase target, float amount, String reason){
        if (attacker == null || target == null) return;
        if (amount <= 0f) return;

        float dmg = amount;

        // Dev-only proc boost (keeps behavior consistent with other procs)
        if (DEV_ENV && DEV_PROC_DAMAGE_BOOST){
            dmg = dmg * DEV_PROC_DAMAGE_MULT + DEV_PROC_DAMAGE_ADD;
        }

        if (Float.isNaN(dmg) || Float.isInfinite(dmg)) dmg = 1.0f;
        if (dmg <= 0f) return;

        // i-frame bypass follows the same knobs as the generic proc path
        boolean isPlayer = (target instanceof EntityPlayer);
        if ((isPlayer && PROC_RESET_IFRAMES_PLAYERS) || (!isPlayer && PROC_RESET_IFRAMES_NONPLAYERS)){
            tryResetIFrames(target);
        }

        // If the mod configured a global applier, use the normal route
        if (DAMAGE_APPLIER != null){
            dealProcDamage(attacker, target, dmg, reason);
            return;
        }

        // Otherwise: apply DBC Body damage directly (players + many DBC/NPCDBC mobs), vanilla fallback for others
        try {
            NS_LOCAL_DBC_APPLIER.deal(attacker, target, dmg);
        } catch (Throwable t){
            try {
                target.attackEntityFrom(hexOrbDamageSource(attacker), dmg);
            } catch (Throwable ignored) {}
        }
    }

    private static void dealNullShellTrailDamage(EntityPlayer owner, EntityLivingBase target, float mult){
        if (owner == null || target == null) return;

        HexPlayerStats.Snapshot s = HexPlayerStats.snapshot(owner);
        double strEff = (s != null) ? ((double)s.str * (double)s.currentMulti) : getStrengthEffective(owner);
        double stat = strEff;

        float dmg = (float)(stat * (double)NS_TRAIL_STAT_SCALE) + NS_TRAIL_BASE_DAMAGE;
        dmg = dmg * mult;

        if (dmg < 0f) dmg = 0f;
        dealNullShellProcDamage(owner, target, dmg, "NullShellTrail");
    }


    // -------------------------------------------------------------------------
    // Null Shell: DBC stat reflection (DEX/CON with form multipliers)
    // -------------------------------------------------------------------------
    private static boolean NS_STATS_LOOKED_UP = false;
    private static java.lang.reflect.Method NS_M_GET_DBCDATA = null;     // DBCDataUniversal.get(player) / getData(player)
    private static java.lang.reflect.Field  NS_F_DEX = null;
    private static java.lang.reflect.Field  NS_F_CON = null;
    private static java.lang.reflect.Field  NS_F_STATS = null;          // dbcData.stats
    private static java.lang.reflect.Method NS_M_GET_CURRENT_MULTI = null; // stats.getCurrentMulti()

    private static void ensureNullShellStatReflection(){
        if (NS_STATS_LOOKED_UP) return;
        NS_STATS_LOOKED_UP = true;

        try {
            Class<?> cUniversal = Class.forName("kamkeel.npcdbc.data.dbcdata.DBCDataUniversal");
            try {
                NS_M_GET_DBCDATA = cUniversal.getMethod("get", net.minecraft.entity.player.EntityPlayer.class);
            } catch (Throwable ignored) {
                NS_M_GET_DBCDATA = cUniversal.getMethod("getData", net.minecraft.entity.player.EntityPlayer.class);
            }

            Class<?> cDBCData = Class.forName("kamkeel.npcdbc.data.dbcdata.DBCData");
            NS_F_DEX   = cDBCData.getField("DEX");
            try { NS_F_CON = cDBCData.getField("CON"); } catch (Throwable ignored) { NS_F_CON = null; }
            NS_F_STATS = cDBCData.getField("stats");

            Class<?> cStats = Class.forName("kamkeel.npcdbc.data.dbcdata.DBCDataStats");
            NS_M_GET_CURRENT_MULTI = cStats.getMethod("getCurrentMulti");
        } catch (Throwable t){
            NS_M_GET_DBCDATA = null; // disables reflection path
        }
    }

    private static double getNullShellDbcStatEffective(EntityPlayer p, java.lang.reflect.Field f){
        if (p == null || f == null) return 0.0;
        ensureNullShellStatReflection();
        if (NS_M_GET_DBCDATA == null || NS_F_STATS == null || NS_M_GET_CURRENT_MULTI == null) return 0.0;

        try {
            Object dbc = NS_M_GET_DBCDATA.invoke(null, p);
            if (dbc == null) return 0.0;

            Object baseObj = f.get(dbc);
            if (!(baseObj instanceof Number)) return 0.0;
            double base = ((Number) baseObj).doubleValue();

            double multi = 1.0;
            try {
                Object stats = NS_F_STATS.get(dbc);
                if (stats != null){
                    Object m = NS_M_GET_CURRENT_MULTI.invoke(stats);
                    if (m instanceof Number){
                        multi = ((Number) m).doubleValue();
                    }
                }
            } catch (Throwable ignored) {}

            if (Double.isNaN(multi) || Double.isInfinite(multi) || multi <= 0.0) multi = 1.0;
            if (base < 0.0) base = 0.0;

            return base * multi;
        } catch (Throwable ignored) {
            return 0.0;
        }
    }

    private static double getDexterityEffective(EntityPlayer p){
        if (p == null) return 0.0;

        // 1) Try attribute-map path (works when DBC/NPCDBC registers attributes)
        double v = 0.0;
        try {
            net.minecraft.entity.ai.attributes.IAttributeInstance inst = p.getAttributeMap().getAttributeInstanceByName("dbc.Dexterity");
            if (inst != null) v = inst.getAttributeValue();
        } catch (Throwable ignored) {}

        if (v <= 0.0){
            try {
                net.minecraft.entity.ai.attributes.IAttributeInstance inst2 = p.getAttributeMap().getAttributeInstanceByName("Dexterity");
                if (inst2 != null) v = inst2.getAttributeValue();
            } catch (Throwable ignored) {}
        }

        // 2) Reflection fallback (NPCDBC / DBCDataUniversal) with form multipliers
        if (v <= 0.0){
            double r = getNullShellDbcStatEffective(p, NS_F_DEX);
            if (r > 0.0) v = r;
        }

        return v;
    }



    private static double getConstitutionEffective(EntityPlayer p){
        if (p == null) return 0.0;

        // 1) Try attribute-map path (works when DBC/NPCDBC registers attributes)
        double v = 0.0;
        try {
            net.minecraft.entity.ai.attributes.IAttributeInstance inst = p.getAttributeMap().getAttributeInstanceByName("dbc.Constitution");
            if (inst != null) v = inst.getAttributeValue();
        } catch (Throwable ignored) {}

        if (v <= 0.0){
            try {
                net.minecraft.entity.ai.attributes.IAttributeInstance inst2 = p.getAttributeMap().getAttributeInstanceByName("Constitution");
                if (inst2 != null) v = inst2.getAttributeValue();
            } catch (Throwable ignored) {}
        }

        // 2) Reflection fallback (NPCDBC / DBCDataUniversal) with form multipliers
        if (v <= 0.0){
            double r = getNullShellDbcStatEffective(p, NS_F_CON);
            if (r > 0.0) v = r;
        }

        return v;
    }




    private static int getNullShellMaxCharge(){
        int base = VOID_NULL_SHELL_CHARGE_MAX;
        if (base <= 0) base = 1000;
        int stages = VOID_NULL_SHELL_CHARGE_STAGES;
        if (stages <= 0) stages = 1;
        long v = (long)base * (long)stages;
        if (v > (long)Integer.MAX_VALUE) v = (long)Integer.MAX_VALUE;
        return (int) v;
    }

    private static float getNullShellChargeFrac(EntityPlayer p){
        if (p == null) return 0f;
        NBTTagCompound d = p.getEntityData();
        if (d == null) return 0f;

        int max = getNullShellMaxCharge();
        if (max <= 0) return 0f;

        int c = d.getInteger(VNS_KEY_CHARGE);
        if (c <= 0) return 0f;
        if (c >= max) return 1f;
        return (float)c / (float)max;
    }

    private static boolean isNullShellDefenseActive(EntityPlayer p, long now){
        if (p == null) return false;
        NBTTagCompound d = p.getEntityData();
        if (d == null) return false;
        long end = d.getLong(VNS_KEY_DEF_END);
        return end > now;
    }



    private static void consumeNullShellCharge(EntityPlayer p, float frac){
        if (p == null) return;
        NBTTagCompound d = p.getEntityData();
        if (d == null) return;

        int max = getNullShellMaxCharge();
        if (max <= 0) return;

        int cost = (int)Math.ceil((double)max * (double)frac);
        if (cost < 1) cost = 1;

        int c = d.getInteger(VNS_KEY_CHARGE);
        c -= cost;
        if (c < 0) c = 0;
        d.setInteger(VNS_KEY_CHARGE, c);

        // Mirror to host item for HUD if we can
        VoidMatch m = findVoidMatch(p);
        if (m != null && m.found && isVoidTypeNullShell(m.voidType)){
            stampNullShellChargeOntoHostStack(p, m.voidType, serverNow(p.worldObj));
        }
    }
    /** Alias for older call sites: mirror Null Shell charge onto the host stack for HUD. */
    private static void stampNullShellChargeOntoHostStack(EntityPlayer owner, String voidType, long now){
        if (owner == null) return;
        NBTTagCompound data = owner.getEntityData();
        if (data == null) return;
        int max = getNullShellMaxCharge();
        if (max <= 0) max = 1000;
        int charge = data.getInteger(VNS_KEY_CHARGE);
        if (charge < 0) charge = 0;
        if (charge > max) charge = max;
        VoidMatch m = findVoidMatch(owner);
        if (m == null || m.debugStack == null) return;
        stampNullShellChargeHud(owner, m.debugStack, voidType, charge, max, now, data);
    }


    private static boolean isTeleportSpotFreeGeneric(EntityPlayer p, World w, double x, double y, double z){
        if (p == null || w == null) return false;

        float hw = p.width * 0.5f;
        AxisAlignedBB bb = AxisAlignedBB.getBoundingBox(
                x - (double)hw, y, z - (double)hw,
                x + (double)hw, y + (double)p.height, z + (double)hw
        );

        try {
            java.util.List coll = w.getCollidingBoundingBoxes(p, bb);
            return (coll == null || coll.isEmpty());
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static boolean doNullShellDash(EntityPlayer p, double dirX, double dirZ, double dist){
        if (p == null || p.worldObj == null) return false;
        World w = p.worldObj;

        double len = Math.sqrt(dirX*dirX + dirZ*dirZ);
        if (len < 1.0e-6) return false;
        dirX /= len;
        dirZ /= len;

        double sx = p.posX;
        double sy = p.posY;
        double sz = p.posZ;

        double tx = sx + dirX * dist;
        double tz = sz + dirZ * dist;
        double ty = sy;

        double[] yTry = new double[]{ty, ty + 0.5, ty + 1.0, ty - 0.5, ty - 1.0};
        boolean placed = false;

        for (int i = 0; i < yTry.length; i++){
            double yy = yTry[i];
            if (isTeleportSpotFreeGeneric(p, w, tx, yy, tz)){
                if (p instanceof EntityPlayerMP){
                    ((EntityPlayerMP)p).setPositionAndUpdate(tx, yy, tz);
                } else {
                    p.setPosition(tx, yy, tz);
                }
                placed = true;
                break;
            }
        }

        if (!placed) return false;

        p.fallDistance = 0f;

        // Trail from start -> end (slightly above ground so it "reads" visually)
        spawnNullShellTrail(p, sx, sy + 0.15, sz, p.posX, p.posY + 0.15, p.posZ);
        return true;
    }

    /**
     * 3D dash in the direction the player is looking.
     * Vertical component is clamped to prevent extreme up/down teleports.
     */
    private static boolean doNullShellDash3D(EntityPlayer p, double dirX, double dirY, double dirZ, double dist){
        if (p == null || p.worldObj == null) return false;
        World w = p.worldObj;

        double len = Math.sqrt(dirX*dirX + dirY*dirY + dirZ*dirZ);
        if (len < 1.0e-6) return false;
        dirX /= len;
        dirY /= len;
        dirZ /= len;

        double sx = p.posX;
        double sy = p.posY;
        double sz = p.posZ;

        // Clamp vertical movement so "looking straight up/down" doesn't fling the player.
        double yDelta = dirY * dist;
        if (yDelta > 2.0) yDelta = 2.0;
        if (yDelta < -2.0) yDelta = -2.0;

        double tx = sx + dirX * dist;
        double tz = sz + dirZ * dist;
        double tyBase = sy + yDelta;

        // A few attempts around the intended Y.
        double[] yTry = new double[]{tyBase, tyBase + 0.5, tyBase + 1.0, tyBase - 0.5, tyBase - 1.0,
                sy, sy + 0.5, sy - 0.5};

        boolean placed = false;

        for (int i = 0; i < yTry.length; i++){
            double yy = yTry[i];
            if (yy < 1.0) yy = 1.0;
            if (yy > 255.0) yy = 255.0;

            if (isTeleportSpotFreeGeneric(p, w, tx, yy, tz)){
                if (p instanceof EntityPlayerMP){
                    ((EntityPlayerMP)p).setPositionAndUpdate(tx, yy, tz);
                } else {
                    p.setPosition(tx, yy, tz);
                }
                placed = true;
                break;
            }
        }

        if (!placed) return false;

        p.fallDistance = 0f;

        // Trail from start -> end (slightly above ground so it "reads" visually)
        spawnNullShellTrail(p, sx, sy + 0.15, sz, p.posX, p.posY + 0.15, p.posZ);
        return true;
    }


    /** Passive auto-dodge: returns true if we performed the dodge and the incoming hit should be negated. */
    private static boolean tryNullShellPassiveDash(EntityPlayer victim, DamageSource src, VoidMatch match, long now){
        if (victim == null || victim.worldObj == null || victim.worldObj.isRemote) return false;
        if (!VOID_ENABLED || !VOID_NULL_SHELL_ENABLED) return false;

        if (match == null || !match.found || !isVoidTypeNullShell(match.voidType)) return false;

        // De-dupe Attack+Hurt in the same server tick so we only roll once per hit.
        if (!markNullShellDodgeOncePerTick(victim, now)) return false;

        if (victim.worldObj.rand.nextFloat() > NS_PASSIVE_PROC_CHANCE) return false;

        if (!cooldownReady(victim, "HexOrbCD_VoidNullDash", NS_DASH_COOLDOWN_TICKS, now)) return false;
        if (getNullShellChargeFrac(victim) < NS_PASSIVE_COST) return false;

        Entity srcEnt = (src != null) ? src.getEntity() : null;

        double dirX, dirZ;
        if (srcEnt != null){
            // Blend "away" + perpendicular for a sidestep feel.
            double ax = victim.posX - srcEnt.posX;
            double az = victim.posZ - srcEnt.posZ;

            double alen = Math.sqrt(ax*ax + az*az);
            if (alen > 1.0e-6){
                ax /= alen;
                az /= alen;
            } else {
                ax = 1.0; az = 0.0;
            }

            double px = -az;
            double pz = ax;

            int side = (victim.worldObj.rand.nextBoolean() ? 1 : -1);
            dirX = ax * 0.85 + px * 0.35 * (double)side;
            dirZ = az * 0.85 + pz * 0.35 * (double)side;
        } else {
            double ang = victim.worldObj.rand.nextDouble() * Math.PI * 2.0;
            dirX = Math.cos(ang);
            dirZ = Math.sin(ang);
        }

        boolean dashed = doNullShellDash(victim, dirX, dirZ, NS_PASSIVE_DISTANCE);
        if (!dashed) return false;

        consumeNullShellCharge(victim, NS_PASSIVE_COST);

        if (match.debugStack != null){
            stampVoidHud(victim, match.debugStack, "Null Shell", "Void Dash",
                    now + (long)NS_DASH_COOLDOWN_TICKS,
                    0L,
                    NS_DASH_COOLDOWN_TICKS
            );
        }

        return true;
    }

    /** Active dash (Left CTRL) - Step 2 will wire packet -> this method. */
    public static boolean tryNullShellActiveDash(EntityPlayer p){
        if (p == null || p.worldObj == null || p.worldObj.isRemote) return false;
        if (!VOID_ENABLED || !VOID_NULL_SHELL_ENABLED) return false;

        VoidMatch match = findVoidMatch(p);
        if (match == null || !match.found){
            nsDbg(p, "Dash: no void orb host found (held/armor).");
            return false;
        }
        if (!isVoidTypeNullShell(match.voidType)){
            nsDbg(p, "Dash: void type is '" + String.valueOf(match.voidType) + "' (not Null Shell).");
            return false;
        }

        long now = serverNow(p.worldObj);
        NBTTagCompound d = p.getEntityData();

        // Cooldown check (NO stamp until we actually dash)
        long next = (d != null) ? d.getLong("HexOrbCD_VoidNullDash") : 0L;
        if (next != 0L && now < next){
            int secs = (int) Math.ceil((next - now) / 20.0);
            nsDbg(p, "Dash: on cooldown (" + secs + "s).");
            return false;
        }

        float frac = getNullShellChargeFrac(p);
        if (frac + 1.0e-4f < NS_ACTIVE_COST){
            int pct = (int) Math.floor(frac * 100.0f);
            nsDbg(p, "Dash: not enough charge (" + pct + "%, need " + (int)(NS_ACTIVE_COST * 100.0f) + "%).");
            return false;
        }

        // Dash in the direction you're LOOKING (uses look vector; vertical movement is clamped for safety)
        net.minecraft.util.Vec3 look = p.getLookVec();
        double lx = (look != null) ? look.xCoord : 0.0;
        double ly = (look != null) ? look.yCoord : 0.0;
        double lz = (look != null) ? look.zCoord : 0.0;

        boolean dashed = doNullShellDash3D(p, lx, ly, lz, NS_ACTIVE_DISTANCE);
        if (!dashed){
            nsDbg(p, "Dash: blocked (no safe spot).");
            return false;
        }

        consumeNullShellCharge(p, NS_ACTIVE_COST);
        cooldownStamp(d, "HexOrbCD_VoidNullDash", NS_DASH_COOLDOWN_TICKS, now);

        if (match.debugStack != null){
            stampVoidHud(p, match.debugStack, "Null Shell", "Void Dash",
                    now + (long)NS_DASH_COOLDOWN_TICKS,
                    0L,
                    NS_DASH_COOLDOWN_TICKS
            );
        }

        return true;
    }
    /** Defense buff (Void Protection) - triple CTRL (action 6). Consumes 50% charge and reduces incoming damage for a short duration. */
    public static boolean tryNullShellDefenseBuff(EntityPlayer p){
        if (p == null || p.worldObj == null || p.worldObj.isRemote) return false;
        if (!VOID_ENABLED || !VOID_NULL_SHELL_ENABLED) return false;

        VoidMatch match = findVoidMatch(p);
        if (match == null || !match.found){
            nsDbg(p, "Defense: no void orb host found (held/armor).");
            return false;
        }
        if (!isVoidTypeNullShell(match.voidType)){
            nsDbg(p, "Defense: void type is '" + String.valueOf(match.voidType) + "' (not Null Shell).");
            return false;
        }

        long now = serverNow(p.worldObj);
        NBTTagCompound data = p.getEntityData();
        if (data == null) return false;

        // Cooldown check (NO stamp until we actually activate)
        long next = data.getLong("HexOrbCD_VoidNullDef");
        if (next != 0L && now < next){
            int secs = (int) Math.ceil((next - now) / 20.0);
            nsDbg(p, "Defense: on cooldown (" + secs + "s).");
            return false;
        }

        float frac = getNullShellChargeFrac(p);
        if (frac + 1.0e-4f < NS_DEFENSE_COST){
            int pct = (int) Math.floor(frac * 100.0f);
            nsDbg(p, "Defense: not enough charge (" + pct + "%, need " + (int)(NS_DEFENSE_COST * 100.0f) + "%).");
            return false;
        }

        // Activate protection
        long end = now + (long)NS_DEFENSE_DURATION_TICKS;
        data.setLong(VNS_KEY_DEF_END, end);
        data.setLong(VNS_KEY_DEF_NEXT_FX, now); // start FX immediately

        consumeNullShellCharge(p, NS_DEFENSE_COST);
        cooldownStamp(data, "HexOrbCD_VoidNullDef", NS_DEFENSE_COOLDOWN_TICKS, now);

        if (match.debugStack != null){
            stampVoidHud(p, match.debugStack, "Null Shell", "Void Protection",
                    now + (long)NS_DEFENSE_COOLDOWN_TICKS,
                    end,
                    NS_DEFENSE_COOLDOWN_TICKS
            );
        }

        return true;
    }

// -------------------------------------------------------------------------
// Null Shell: 100% "Void Push" (double-press then hold, server-side charge)
// -------------------------------------------------------------------------

    /** Start charging Void Push (sent by client once the user is holding after a double-press). */
    public static boolean tryNullShellPushStart(EntityPlayer p){
        if (!VOID_ENABLED || !VOID_NULL_SHELL_ENABLED || p == null) return false;
        World w = p.worldObj;
        if (w == null || w.isRemote) return false;

        long now = serverNow(w);
        NBTTagCompound d = p.getEntityData();
        if (d == null) return false;

        // already charging?
        if (d.getByte(VNS_KEY_PUSH_CHARGING) != 0) { nsDbg(p, "Push: already charging."); return false; }

        // cooldown gate (but don't stamp until we actually fire)
        if (!cooldownReadyNoStamp(d, "HexOrbCD_VoidNullPush", now)) {
            long nx = d.getLong("HexOrbCD_VoidNullPush");
            int secs = (nx != 0L && now < nx) ? (int)Math.ceil((nx - now)/20.0) : 0;
            nsDbg(p, "Push: on cooldown (" + secs + "s)." );
            return false;
        }

        // must have full charge
        if (getNullShellChargeFrac(p) + 1.0e-4f < NS_PUSH_COST) {
            float f = getNullShellChargeFrac(p);
            nsDbg(p, "Push: need 100% charge (have " + (int)Math.floor(f*100.0f) + "%).");
            return false;
        }

        d.setLong(VNS_KEY_PUSH_START, now);
        d.setByte(VNS_KEY_PUSH_CHARGING, (byte) 1);

        // small tell + early particles so the user knows it's charging
        if (w instanceof WorldServer){
            WorldServer ws = (WorldServer) w;
            ws.func_147487_a("portal", p.posX, p.posY + 0.9, p.posZ, 12, 0.35, 0.55, 0.35, 0.01);
            ws.func_147487_a("mobSpell", p.posX, p.posY + 1.0, p.posZ, 10, 0.35, 0.55, 0.35, 0.01);
        }
        w.playSoundAtEntity(p, "random.fizz", 0.6f, 0.85f);

        return true;
    }

    /** Release Void Push (sent by client on key-up after charging). */
    public static boolean tryNullShellPushRelease(EntityPlayer p){
        if (!VOID_ENABLED || !VOID_NULL_SHELL_ENABLED || p == null) return false;
        World w = p.worldObj;
        if (w == null || w.isRemote) return false;

        long now = serverNow(w);
        NBTTagCompound d = p.getEntityData();
        if (d == null) return false;

        if (d.getByte(VNS_KEY_PUSH_CHARGING) == 0) { nsDbg(p, "Push: not charging (release ignored)."); return false; }

        long start = d.getLong(VNS_KEY_PUSH_START);
        d.removeTag(VNS_KEY_PUSH_CHARGING);
        d.removeTag(VNS_KEY_PUSH_START);

        if (start <= 0L) return false;

        long held = now - start;
        if (held < (long) NS_PUSH_CHARGE_TICKS){
            nsDbg(p, "Push: released too early (" + held + "t / " + NS_PUSH_CHARGE_TICKS + "t)." );
            // not charged long enough: no cost, no cooldown
            w.playSoundAtEntity(p, "random.click", 0.4f, 1.25f);
            return false;
        }

        // Double-check resources + cooldown right at fire time (prevents dupes/exploits).
        if (!cooldownReadyNoStamp(d, "HexOrbCD_VoidNullPush", now)) {
            long nx = d.getLong("HexOrbCD_VoidNullPush");
            int secs = (nx != 0L && now < nx) ? (int)Math.ceil((nx - now)/20.0) : 0;
            nsDbg(p, "Push: on cooldown (" + secs + "s).");
            return false;
        }
        if (getNullShellChargeFrac(p) + 1.0e-4f < NS_PUSH_COST) {
            float f = getNullShellChargeFrac(p);
            nsDbg(p, "Push: need 100% charge (have " + (int)Math.floor(f*100.0f) + "%).");
            return false;
        }

        boolean over = held >= (long) (NS_PUSH_CHARGE_TICKS + NS_PUSH_OVERCHARGE_TICKS);
        float mult = over ? NS_PUSH_OVERCHARGE_MULT : 1.0f;

        consumeNullShellCharge(p, NS_PUSH_COST);
        cooldownStamp(d, "HexOrbCD_VoidNullPush", NS_PUSH_COOLDOWN_TICKS, now);

        doNullShellVoidPush(p, mult);

        // HUD stamp for cooldown bar
        VoidMatch m = findVoidMatch(p);
        if (m != null && m.found && m.debugStack != null){
            stampVoidHud(p, m.debugStack, m.voidType, "Void Push", d.getLong("HexOrbCD_VoidNullPush"), 0L, NS_PUSH_COOLDOWN_TICKS);
        }

        return true;
    }

    private static boolean cooldownReadyNoStamp(NBTTagCompound d, String key, long now){
        if (d == null) return false;
        long next = d.getLong(key);
        return next == 0L || now >= next;
    }

    private static void cooldownStamp(NBTTagCompound d, String key, int cdTicks, long now){
        if (d == null) return;
        if (cdTicks <= 0) { d.removeTag(key); return; }
        d.setLong(key, now + (long) cdTicks);
    }




    private static final class EnergizedMatch {
        boolean hasFlat;
        boolean hasAnim;
        ItemStack debugStack;
    }

    /**
     * Checks energized gems on held item first, then armor.
     * Priority: held anim > held flat > armor anim > armor flat.
     */
    private static EnergizedMatch findEnergizedMatch(EntityPlayer p){
        EnergizedMatch m = new EnergizedMatch();
        if (p == null) return m;

        ItemStack held = p.getHeldItem();
        if (held != null){
            if (hasGemSocketed(held, GEM_ENERGIZED_ANIM)){
                m.hasAnim = true;
                m.debugStack = held;
                return m;
            }
            if (hasGemSocketed(held, GEM_ENERGIZED_FLAT)){
                m.hasFlat = true;
                m.debugStack = held;
                return m;
            }
        }

        if (p.inventory != null && p.inventory.armorInventory != null){
            ItemStack[] armor = p.inventory.armorInventory;
            for (int i = 0; i < armor.length; i++){
                ItemStack a = armor[i];
                if (a == null) continue;
                if (hasGemSocketed(a, GEM_ENERGIZED_ANIM)){
                    m.hasAnim = true;
                    m.debugStack = a;
                    return m;
                }
            }
            for (int i = 0; i < armor.length; i++){
                ItemStack a = armor[i];
                if (a == null) continue;
                if (hasGemSocketed(a, GEM_ENERGIZED_FLAT)){
                    m.hasFlat = true;
                    m.debugStack = a;
                    return m;
                }
            }
        }

        return m;
    }

    /**
     * The old strict "direct == player" check breaks for some mods.
     * This version is permissive but still blocks recursion/projectiles.
     */

    private static final class FirePillMatch {
        boolean hasFlat;
        boolean hasAnim;
        ItemStack debugStack;
    }

    private static FirePillMatch findFirePillMatch(EntityPlayer p){
        FirePillMatch match = new FirePillMatch();
        if (p == null) return match;

        // Prefer HELD item first, and prefer ANIM over FLAT (stronger).
        ItemStack held = p.getHeldItem();
        if (held != null){
            if (hasGemSocketed(held, PILL_FIRE_ANIM)){
                match.hasAnim = true;
                match.debugStack = held;
                return match;
            }
            if (hasGemSocketed(held, PILL_FIRE_FLAT)){
                match.hasFlat = true;
                match.debugStack = held;
                return match;
            }
        }

        // Then scan armor
        ItemStack[] armor = (p.inventory != null) ? p.inventory.armorInventory : null;
        if (armor != null){
            for (ItemStack s : armor){
                if (s == null) continue;
                if (hasGemSocketed(s, PILL_FIRE_ANIM)){
                    match.hasAnim = true;
                    match.debugStack = s;
                    return match;
                }
            }
            for (ItemStack s : armor){
                if (s == null) continue;
                if (hasGemSocketed(s, PILL_FIRE_FLAT)){
                    match.hasFlat = true;
                    match.debugStack = s;
                    return match;
                }
            }
        }

        return match;
    }

    private static boolean isPlayerMeleeDamage(DamageSource src, EntityPlayer p){
        if (src == null || p == null) return false;

        // must be attributed to the player (some mods set entity!=player but direct==player)
        if (src.getEntity() != p && src.getSourceOfDamage() != p) return false;

        String type = src.getDamageType();
        if ("hexorb".equals(type)) return false; // prevent recursion
        if ("thorns".equals(type)) return false;

        // block indirect/projectile sources
        if (src.isProjectile()) return false;
        if (src instanceof EntityDamageSourceIndirect && src.getSourceOfDamage() != p) return false;

        return true;
    }

    // -------------------------------------------------------------
    // FLAT PROC: Rainbow Shockwave (AoE)
    // -------------------------------------------------------------
    private static void tryProcEnergizedFlat(EntityPlayer p, EntityLivingBase primary, float baseDamage){
        if (p == null || primary == null) return;

        if (!roll(p, ENERGIZED_FLAT_PROC_CHANCE)) return;
        if (!cooldownReady(p, "HexOrbCD_EnergizedFlat", ENERGIZED_FLAT_COOLDOWN_TICKS)) return;

        World w = p.worldObj;

        float procBase = procBaseDamage(p, primary, ProcStage.FLAT_AOE, baseDamage, false);

        float dmg = Math.max(1.0f, (procBase + ENERGIZED_FLAT_BONUS_DAMAGE) * ENERGIZED_GLOBAL_DAMAGE_MULT);

        AxisAlignedBB box = primary.boundingBox.expand(ENERGIZED_FLAT_RADIUS, 1.5, ENERGIZED_FLAT_RADIUS);

        @SuppressWarnings("unchecked")
        List<EntityLivingBase> list = w.getEntitiesWithinAABB(EntityLivingBase.class, box);

        final double px = primary.posX, py = primary.posY, pz = primary.posZ;
        Collections.sort(list, new Comparator<EntityLivingBase>() {
            @Override public int compare(EntityLivingBase a, EntityLivingBase b){
                double da = a.getDistanceSq(px, py, pz);
                double db = b.getDistanceSq(px, py, pz);
                return da < db ? -1 : (da > db ? 1 : 0);
            }
        });

        int hit = 0;
        for (EntityLivingBase t : list){
            if (t == null) continue;
            if (t == p) continue;
            if (t.isDead) continue;

            float finalDmg = scaleForPvp(t, dmg);
            dealProcDamage(p, t, finalDmg, "flatAoE");
            applyKnockbackFrom(p, t);

            hit++;
            if (hit >= ENERGIZED_FLAT_MAX_HITS) break;
        }

        maybeStartSwirl(p, primary, baseDamage, false);

        playRainbowBurst(w, primary.posX, primary.posY + 1.0, primary.posZ);
        w.playSoundAtEntity(primary, "random.levelup", 0.8f, 1.35f);

        if (DEBUG_PROC) debugOncePerSecond(p, "[HexOrb] FLAT PROC!");
    }

    // -------------------------------------------------------------
    // ANIM PROC: Rainbow Chain Arc
    // -------------------------------------------------------------
    private static void tryProcEnergizedAnim(EntityPlayer p, EntityLivingBase primary, float baseDamage){
        if (p == null || primary == null) return;

        if (!roll(p, ENERGIZED_ANIM_PROC_CHANCE)) return;
        if (!cooldownReady(p, "HexOrbCD_EnergizedAnim", ENERGIZED_ANIM_COOLDOWN_TICKS)) return;

        World w = p.worldObj;

        float procBasePrimary = procBaseDamage(p, primary, ProcStage.ANIM_PRIMARY, baseDamage, true);

        float first = Math.max(1.0f, (procBasePrimary * ENERGIZED_ANIM_PRIMARY_DAMAGE_SCALE + ENERGIZED_ANIM_PRIMARY_BONUS_DAMAGE) * ENERGIZED_GLOBAL_DAMAGE_MULT);
        first = scaleForPvp(primary, first);
        dealProcDamage(p, primary, first, "animPrimary");
        applyKnockbackFrom(p, primary);

        AxisAlignedBB box = primary.boundingBox.expand(ENERGIZED_ANIM_RADIUS, 1.8, ENERGIZED_ANIM_RADIUS);

        @SuppressWarnings("unchecked")
        List<EntityLivingBase> list = w.getEntitiesWithinAABB(EntityLivingBase.class, box);

        final double px = primary.posX, py = primary.posY, pz = primary.posZ;
        List<EntityLivingBase> targets = new ArrayList<EntityLivingBase>();
        for (EntityLivingBase t : list){
            if (t == null) continue;
            if (t == p) continue;
            if (t == primary) continue;
            if (t.isDead) continue;
            targets.add(t);
        }

        Collections.sort(targets, new Comparator<EntityLivingBase>() {
            @Override public int compare(EntityLivingBase a, EntityLivingBase b){
                double da = a.getDistanceSq(px, py, pz);
                double db = b.getDistanceSq(px, py, pz);
                return da < db ? -1 : (da > db ? 1 : 0);
            }
        });

        int chained = 0;
        float procBaseChain = procBaseDamage(p, primary, ProcStage.ANIM_CHAIN, baseDamage, true);
        float chainDmg = Math.max(1.0f, (procBaseChain * ENERGIZED_ANIM_CHAIN_DAMAGE_SCALE) * ENERGIZED_GLOBAL_DAMAGE_MULT);

        for (EntityLivingBase t : targets){
            float finalDmg = scaleForPvp(t, chainDmg);
            dealProcDamage(p, t, finalDmg, "animChain");
            applyKnockbackFrom(p, t);

            playRainbowSpark(w, t.posX, t.posY + 1.0, t.posZ);

            chained++;
            if (chained >= ENERGIZED_ANIM_CHAIN_HITS) break;
        }

        maybeStartSwirl(p, primary, baseDamage, true);

        playRainbowBurst(w, primary.posX, primary.posY + 1.0, primary.posZ);
        w.playSoundAtEntity(primary, "fireworks.blast", 0.7f, 1.15f);

        if (DEBUG_PROC) debugOncePerSecond(p, "[HexOrb] ANIM PROC!");
    }

    // -------------------------------------------------------------
    // Heal proc: short regen on attacker (no script needed)
    // -------------------------------------------------------------
    private static void tryProcEnergizedHeal(EntityPlayer p, boolean isAnim){
        if (p == null) return;
        if (!ENERGIZED_HEAL_ENABLED) return;

        double chance = isAnim ? ENERGIZED_ANIM_HEAL_PROC_CHANCE : ENERGIZED_HEAL_PROC_CHANCE;
        if (chance <= 0.0) return;
        if (!rollChance(p, chance)) return;

        String cdKey = isAnim ? "HexOrbCD_HealAnim" : "HexOrbCD_HealFlat";
        int cdTicks = isAnim ? ENERGIZED_ANIM_HEAL_COOLDOWN_TICKS : ENERGIZED_HEAL_COOLDOWN_TICKS;
        if (!cooldownReady(p, cdKey, cdTicks)) return;

        startHealBuff(p, isAnim);
    }

    // -------------------------------------------------------------
    // Fractured: Low-Body Chaos Surge
    // -------------------------------------------------------------
    private static void tryProcFracturedLowBody(EntityPlayer p, EntityLivingBase primary, float baseDamage, boolean isAnim){
        if (p == null || primary == null) return;
        if (!FRACTURED_ENABLED) return;

        World w = p.worldObj;
        if (w == null || w.isRemote) return;

        float thresh = FRACTURED_TRIGGER_BODY_PCT;
        if (thresh < 0.05f) thresh = 0.05f;
        if (thresh > 0.99f) thresh = 0.99f;

        float bodyPct = getBodyPercent01(p);
        if (bodyPct < 0f) return;
        if (bodyPct > thresh) return;

        float missing = (thresh - bodyPct) / thresh; // 0..1
        if (missing < 0f) missing = 0f;
        if (missing > 1f) missing = 1f;

        double baseChance  = isAnim ? FRACTURED_PROC_CHANCE_BASE_ANIM  : FRACTURED_PROC_CHANCE_BASE;
        double bonusChance = isAnim ? FRACTURED_PROC_CHANCE_BONUS_ANIM : FRACTURED_PROC_CHANCE_BONUS;
        double chance = baseChance + bonusChance * missing;
        if (chance <= 0.0) return;
        if (!rollChance(p, chance)) return;

        String cdKey = isAnim ? "HexOrbCD_FracturedAnim" : "HexOrbCD_FracturedFlat";
        if (!cooldownReady(p, cdKey, FRACTURED_COOLDOWN_TICKS)) return;

        float procBase = procBaseDamage(p, primary, ProcStage.FRACTURED_LOWHP, baseDamage, isAnim);

        float scale = isAnim ? FRACTURED_DAMAGE_SCALE_ANIM : FRACTURED_DAMAGE_SCALE;
        float bonus = isAnim ? FRACTURED_BONUS_DAMAGE_ANIM : FRACTURED_BONUS_DAMAGE;

        float dmg = procBase * scale + bonus;
        if (dmg < 1.0f) dmg = 1.0f;
        dmg *= (1.0f + missing * FRACTURED_MISSING_RAMP);

        boolean backlash = rollChance(p, FRACTURED_BACKLASH_CHANCE);
        double extremeChance = FRACTURED_EXTREME_CHANCE * (0.60 + 0.70 * missing);
        boolean extreme = extremeChance > 0.0 && rollChance(p, extremeChance);

        if (!backlash){
            float out = dmg;
            if (extreme) out *= FRACTURED_EXTREME_MULT_POS;

            float finalDmg = scaleForPvp(primary, out);
            dealProcDamage(p, primary, finalDmg, extreme ? "fracturedExtreme" : "fractured");
            applyKnockbackFrom(p, primary);

            playRainbowBurst(w, primary.posX, primary.posY + primary.height * 0.55, primary.posZ);
            w.playSoundAtEntity(primary, "random.orb", 0.55f, extreme ? 1.65f : 1.25f);
        } else {
            float self = dmg * FRACTURED_BACKLASH_SELF_SCALE;
            if (self < 1.0f) self = 1.0f;
            if (extreme) self *= FRACTURED_EXTREME_MULT_NEG;

            dealProcDamage(p, p, self, extreme ? "fracturedBacklashExtreme" : "fracturedBacklash");
            playRainbowBurst(w, p.posX, p.posY + 1.0, p.posZ);
            w.playSoundAtEntity(p, "random.fizz", 0.55f, extreme ? 0.55f : 0.75f);
        }

        if (DEBUG_PROC){
            debugOncePerSecond(p, "§7[HexOrb] §dFractured§7 proc (body=" + (int)(bodyPct * 100f) + "%, " + (backlash ? "backlash" : "surge") + (extreme ? ", EXTREME" : "") + ")");
        }
    }


    private static void startHealBuff(EntityPlayer p, boolean isAnim){
        if (p == null) return;
        World w = p.worldObj;
        if (w == null || w.isRemote) return;

        long now = serverNow(w);

        int dur = isAnim ? ENERGIZED_ANIM_HEAL_DURATION_TICKS : ENERGIZED_HEAL_DURATION_TICKS;
        int interval = Math.max(1, ENERGIZED_HEAL_INTERVAL_TICKS);

        float minPct = isAnim ? ENERGIZED_ANIM_HEAL_PERCENT_MIN : ENERGIZED_HEAL_PERCENT_MIN;
        float maxPct = isAnim ? ENERGIZED_ANIM_HEAL_PERCENT_MAX : ENERGIZED_HEAL_PERCENT_MAX;

        if (minPct < 0f) minPct = 0f;
        if (maxPct < minPct) maxPct = minPct;

        long endTime = now + Math.max(1, dur);
        long nextTime = now; // heal immediately

        // Apply to self
        applyHealBuffToPlayer(p, endTime, nextTime, interval, minPct, maxPct, isAnim);

        // Optional AoE: apply same buff window to nearby players
        float radius = isAnim ? ENERGIZED_ANIM_HEAL_AOE_RADIUS : ENERGIZED_HEAL_AOE_RADIUS;
        int maxTargets = isAnim ? ENERGIZED_ANIM_HEAL_AOE_MAX_TARGETS : ENERGIZED_HEAL_AOE_MAX_TARGETS;

        if (radius > 0.0f){
            AxisAlignedBB box = p.boundingBox.expand(radius, 2.0, radius);
            @SuppressWarnings("unchecked")
            java.util.List<EntityPlayer> list = w.getEntitiesWithinAABB(EntityPlayer.class, box);

            int applied = 0;
            for (int i = 0; i < list.size(); i++){
                EntityPlayer other = list.get(i);
                if (other == null || other == p) continue;

                applyHealBuffToPlayer(other, endTime, nextTime, interval, minPct, maxPct, isAnim);

                applied++;
                if (maxTargets > 0 && applied >= maxTargets) break;
            }
        }

        // SFX + initial burst only on the caster (avoids AoE spam)
        w.playSoundAtEntity(p, "random.orb", 0.55f, isAnim ? 1.35f : 1.15f);
        playRainbowBurst(w, p.posX, p.posY + 1.0, p.posZ);
    }

    private static void applyHealBuffToPlayer(EntityPlayer t, long endTime, long nextTime, int intervalTicks,
                                              float minPct, float maxPct, boolean isAnim){
        if (t == null) return;
        World w = t.worldObj;
        if (w == null || w.isRemote) return;

        NBTTagCompound d = t.getEntityData();

        long prevEnd = d.getLong(HEAL_KEY_END);
        if (prevEnd <= 0L || endTime > prevEnd){
            d.setLong(HEAL_KEY_END, endTime);
        }

        long prevNext = d.getLong(HEAL_KEY_NEXT);
        if (prevNext <= 0L || nextTime < prevNext){
            d.setLong(HEAL_KEY_NEXT, nextTime);
        }

        // store interval for next scheduling (reuses global knob on tick, but we capture current too)
        // (we don't persist interval key; tick uses ENERGIZED_HEAL_INTERVAL_TICKS)

        d.setFloat(HEAL_KEY_PCT_MIN, minPct);
        d.setFloat(HEAL_KEY_PCT_MAX, maxPct);
        d.setBoolean(HEAL_KEY_ANIM, isAnim);

        // legacy fixed pct (cleanup if present)
        if (d.hasKey(HEAL_KEY_PCT)){
            d.removeTag(HEAL_KEY_PCT);
        }
    }

    private static void tickHealBuff(EntityPlayer p, NBTTagCompound d, World w, long now){
        if (p == null || d == null || w == null) return;

        long end = d.getLong(HEAL_KEY_END);
        if (end <= 0L) return;

        if (now >= end){
            // cleanup
            d.removeTag(HEAL_KEY_END);
            d.removeTag(HEAL_KEY_NEXT);
            d.removeTag(HEAL_KEY_PCT);
            d.removeTag(HEAL_KEY_PCT_MIN);
            d.removeTag(HEAL_KEY_PCT_MAX);
            d.removeTag(HEAL_KEY_ANIM);
            return;
        }

        boolean isAnim = d.getBoolean(HEAL_KEY_ANIM);

        // VFX: little bouncy particles around the player each tick
        playHealBouncyParticles(w, p, isAnim, now);

        long next = d.getLong(HEAL_KEY_NEXT);
        if (next == 0L) next = now;

        if (now >= next){
            float min = d.getFloat(HEAL_KEY_PCT_MIN);
            float max = d.getFloat(HEAL_KEY_PCT_MAX);

            float pct;
            if (max > 0f || min > 0f){
                if (min < 0f) min = 0f;
                if (max < min) max = min;
                pct = min + p.getRNG().nextFloat() * (max - min);
            } else {
                // backwards compat (older builds stored a fixed pct)
                pct = d.getFloat(HEAL_KEY_PCT);
            }

            if (pct > 0f){
                // Prefer DBC Body healing
                boolean ok = dbcRestoreHealthPercent(p, pct);
                if (!ok){
                    // vanilla fallback (small heart heal)
                    p.heal(Math.max(0.1f, pct * 20.0f));
                }
            }
            d.setLong(HEAL_KEY_NEXT, now + Math.max(1, ENERGIZED_HEAL_INTERVAL_TICKS));
        }
    }

    private static void playHealBouncyParticles(World w, EntityPlayer p, boolean isAnim, long now){
        if (!(w instanceof WorldServer)) return;
        WorldServer ws = (WorldServer) w;

        int count = isAnim ? ENERGIZED_ANIM_HEAL_PARTICLES_PER_TICK : ENERGIZED_HEAL_PARTICLES_PER_TICK;
        float radius = isAnim ? ENERGIZED_ANIM_HEAL_PARTICLE_RADIUS : ENERGIZED_HEAL_PARTICLE_RADIUS;

        // A little rainbow dust to sell the "energized" regen feel
        if (ENERGIZED_HEAL_SHOW_RAINBOW_DUST){
            int dust = Math.max(6, count / 2);
            spawnRainbowColors(ws, p.posX, p.posY + 1.0, p.posZ, dust, radius, 0.70, radius);
        }

        // Orbit + bounce: deterministic angle by time + per-index offset
        double t = (double) now * 0.35;
        for (int i = 0; i < count; i++){
            double ang = t + (6.283185307179586 * (double) i / (double) Math.max(1, count));
            double px = p.posX + Math.cos(ang) * radius;
            double pz = p.posZ + Math.sin(ang) * radius;

            // bounce on Y with a sine
            double py = p.posY + 0.35 + 0.75 + 0.35 * Math.sin(t * 0.85 + i);

            ws.func_147487_a("fireworksSpark", px, py, pz, 1, 0.02, 0.02, 0.02, 0.01);

            // occasional hearts
            if (ws.rand.nextInt(8) == 0){
                ws.func_147487_a("heart", px, py + 0.15, pz, 1, 0, 0, 0, 0);
            }
        }
    }

    // -------------------------------------------------------------
    // DBC heal bridge (reflection): DBCDataUniversal.get(player).stats.restoreHealthPercent(pct)
    // -------------------------------------------------------------
    private static boolean DBC_HEAL_LOOKED_UP = false;
    private static Method  DBC_GET_DATA = null;
    private static Field   DBC_STATS_FIELD = null;
    private static Method  DBC_RESTORE_HP_PCT = null;

    private static void ensureDbcHealReflection(){
        if (DBC_HEAL_LOOKED_UP) return;
        DBC_HEAL_LOOKED_UP = true;

        try {
            Class<?> cUniversal = Class.forName("kamkeel.npcdbc.data.dbcdata.DBCDataUniversal");
            try {
                DBC_GET_DATA = cUniversal.getMethod("get", EntityPlayer.class);
            } catch (Throwable ignored) {
                DBC_GET_DATA = cUniversal.getMethod("getData", EntityPlayer.class);
            }

            Class<?> cDBCData = Class.forName("kamkeel.npcdbc.data.dbcdata.DBCData");
            DBC_STATS_FIELD = cDBCData.getField("stats");

            Class<?> cStats = Class.forName("kamkeel.npcdbc.data.dbcdata.DBCDataStats");
            DBC_RESTORE_HP_PCT = cStats.getMethod("restoreHealthPercent", Float.TYPE);
        } catch (Throwable t){
            DBC_GET_DATA = null;
            DBC_STATS_FIELD = null;
            DBC_RESTORE_HP_PCT = null;
        }
    }

    private static boolean dbcRestoreHealthPercent(EntityPlayer p, float pct){
        if (p == null) return false;
        if (pct <= 0f) return true;

        ensureDbcHealReflection();
        if (DBC_GET_DATA == null || DBC_STATS_FIELD == null || DBC_RESTORE_HP_PCT == null) return false;

        try {
            Object dbc = DBC_GET_DATA.invoke(null, p);
            if (dbc == null) return false;

            Object stats = DBC_STATS_FIELD.get(dbc);
            if (stats == null) return false;

            DBC_RESTORE_HP_PCT.invoke(stats, pct);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }



    // -------------------------------------------------------------
    // Fire Pill: Fire Punch
    // -------------------------------------------------------------

    private static boolean isFirePunchActive(EntityPlayer p){
        if (p == null) return false;
        NBTTagCompound d = p.getEntityData();
        long end = d.getLong(FP_KEY_END);
        if (end <= 0L) return false;
        long now = serverNow(p.worldObj);
        return now < end;
    }

    private static void clearFirePunch(EntityPlayer p){
        if (p == null) return;
        NBTTagCompound d = p.getEntityData();
        d.removeTag(FP_KEY_END);
        d.removeTag(FP_KEY_NEXT);
        d.removeTag(FP_KEY_ANIM);
        d.removeTag(FP_KEY_LASTTGT);
        d.removeTag(FP_KEY_FIN);
    }

    private static void tryStartFirePunch(EntityPlayer p, boolean isAnim){
        if (!FIRE_PUNCH_ENABLED || p == null) return;

        World w = p.worldObj;
        long now = serverNow(w);
        NBTTagCompound d = p.getEntityData();

        long end = d.getLong(FP_KEY_END);
        if (end > 0L && now < end) return; // already active

        // cooldown gate
        String cdKey = isAnim ? "HexOrbCD_FirePunchAnim" : "HexOrbCD_FirePunchFlat";
        int cdTicks  = isAnim ? FIRE_PUNCH_ANIM_COOLDOWN_TICKS : FIRE_PUNCH_COOLDOWN_TICKS;
        if (!cooldownReady(p, cdKey, cdTicks)) return;

        // chance roll
        double chance = isAnim ? FIRE_PUNCH_CHANCE_ANIM : FIRE_PUNCH_CHANCE_FLAT;
        if (chance <= 0.0) return;
        if (!roll(p, chance)) return;

        int dur = isAnim ? FIRE_PUNCH_ANIM_DURATION_TICKS : FIRE_PUNCH_DURATION_TICKS;
        dur = Math.max(1, dur);

        d.setLong(FP_KEY_END, now + dur);
        d.setLong(FP_KEY_NEXT, now);
        d.setBoolean(FP_KEY_ANIM, isAnim);
        d.setInteger(FP_KEY_LASTTGT, 0);
        d.setBoolean(FP_KEY_FIN, false);

        // activation VFX
        if (w != null){
            w.playSoundAtEntity(p, "fire.ignite", 0.65f, isAnim ? 1.18f : 1.06f);
            spawnFireHands(w, p, isAnim, isAnim ? 18 : 12);
        }
    }

    /** Apply the per-hit bonus while Fire Punch is active, and manage the finisher. */
    private static void applyFirePunchHit(EntityPlayer attacker, EntityLivingBase target, float eventDamage){
        if (!FIRE_PUNCH_ENABLED || attacker == null || target == null) return;

        NBTTagCompound ad = attacker.getEntityData();
        long end = ad.getLong(FP_KEY_END);
        if (end <= 0L) return;

        World w = attacker.worldObj;
        long now = serverNow(w);
        if (now >= end) return;

        boolean isAnim = ad.getBoolean(FP_KEY_ANIM);

        // Track the last thing we hit (used for finisher if the timer expires before you hit again)
        ad.setInteger(FP_KEY_LASTTGT, target.getEntityId());

        // Bonus damage per hit
        float base = procBaseDamage(attacker, target, ProcStage.FIRE_PUNCH_HIT, eventDamage, isAnim);
        float scale = isAnim ? FIRE_PUNCH_ANIM_HIT_DAMAGE_SCALE : FIRE_PUNCH_HIT_DAMAGE_SCALE;
        float bonus = isAnim ? FIRE_PUNCH_ANIM_HIT_BONUS_DAMAGE : FIRE_PUNCH_HIT_BONUS_DAMAGE;

        float dmg = (base * scale) + bonus;
        dmg = scaleForPvp(target, dmg);

        if (dmg > 0f){
            dealProcDamage(attacker, target, dmg, "firePunch");
        }

        // Ignite
        int secs = isAnim ? FIRE_PUNCH_ANIM_SET_FIRE_SECONDS : FIRE_PUNCH_SET_FIRE_SECONDS;
        if (secs > 0){
            try { target.setFire(secs); } catch (Throwable ignored) {}
        }

        // Small hit VFX
        if (w != null){
            spawnFireImpact(w, target, isAnim);
        }

        // If we're in the final window, start the finisher immediately
        boolean fin = ad.getBoolean(FP_KEY_FIN);
        if (!fin){
            long remain = end - now;
            if (remain <= Math.max(0, FIRE_PUNCH_FINISH_WINDOW_TICKS)){
                ad.setBoolean(FP_KEY_FIN, true);
                startFireDot(attacker, target, eventDamage, isAnim);
                clearFirePunch(attacker);
            }
        }
    }

    /** Tick VFX and (when the timer ends) apply the finisher to the last target. */
    private static void tickFirePunchBuff(EntityPlayer p, NBTTagCompound data, World w, long now){
        if (p == null || data == null) return;

        long end = data.getLong(FP_KEY_END);
        if (end <= 0L) return;

        boolean isAnim = data.getBoolean(FP_KEY_ANIM);

        if (now >= end){
            boolean fin = data.getBoolean(FP_KEY_FIN);

            // If we never started the finisher yet, apply it to the last target now.
            if (!fin){
                int lastId = data.getInteger(FP_KEY_LASTTGT);
                if (lastId != 0){
                    Entity e = (w != null) ? w.getEntityByID(lastId) : null;
                    if (e instanceof EntityLivingBase){
                        EntityLivingBase tgt = (EntityLivingBase) e;
                        if (!tgt.isDead && (!(tgt instanceof EntityPlayer) || FIRE_PUNCH_AFFECT_PLAYERS)){
                            startFireDot(p, tgt, 0.0f, isAnim);
                        }
                    }
                }
            }

            // End VFX
            if (w != null){
                w.playSoundAtEntity(p, "random.fizz", 0.40f, isAnim ? 1.22f : 1.10f);
                spawnFireHands(w, p, isAnim, isAnim ? 10 : 6);
            }

            clearFirePunch(p);
            return;
        }

        // Hands VFX while active
        long next = data.getLong(FP_KEY_NEXT);
        int interval = Math.max(1, FIRE_PUNCH_PARTICLE_INTERVAL_TICKS);
        if (next <= 0L || now >= next){
            int count = isAnim ? FIRE_PUNCH_ANIM_PARTICLES_PER_TICK : FIRE_PUNCH_PARTICLES_PER_TICK;
            spawnFireHands(w, p, isAnim, Math.max(1, count));
            data.setLong(FP_KEY_NEXT, now + interval);
        }
    }

    private static void startFireDot(EntityPlayer owner, EntityLivingBase target, float eventDamage, boolean isAnim){
        if (target == null) return;
        if ((target instanceof EntityPlayer) && !FIRE_PUNCH_AFFECT_PLAYERS) return;

        World w = target.worldObj;
        long now = serverNow(w);

        NBTTagCompound td = target.getEntityData();
        int dur = isAnim ? FIRE_DOT_ANIM_DURATION_TICKS : FIRE_DOT_DURATION_TICKS;
        dur = Math.max(1, dur);

        td.setLong(FD_KEY_END, now + dur);
        td.setLong(FD_KEY_NEXT, now);
        td.setInteger(FD_KEY_OWNER, (owner != null) ? owner.getEntityId() : 0);
        td.setBoolean(FD_KEY_ANIM, isAnim);

        // cache tick damage
        float baseTick = (owner != null)
                ? procBaseDamage(owner, target, ProcStage.FIRE_DOT_TICK, eventDamage, isAnim)
                : (isAnim ? PROC_BASE_FIRE_DOT_TICK : PROC_BASE_FIRE_DOT_TICK);

        float tickScale = isAnim ? FIRE_DOT_ANIM_DAMAGE_SCALE : FIRE_DOT_DAMAGE_SCALE;
        float tickBonus = isAnim ? FIRE_DOT_ANIM_BONUS_DAMAGE : FIRE_DOT_BONUS_DAMAGE;
        float tickDmg = (baseTick * tickScale) + tickBonus;
        tickDmg = scaleForPvp(target, tickDmg);

        td.setFloat(FD_KEY_DMG, tickDmg);
        td.setInteger(FD_KEY_INT, Math.max(1, FIRE_DOT_INTERVAL_TICKS));

        // cache explosion damage + radius
        float baseEx = (owner != null)
                ? procBaseDamage(owner, target, ProcStage.FIRE_DOT_EXPLOSION, eventDamage, isAnim)
                : (isAnim ? PROC_BASE_FIRE_DOT_EXPLOSION : PROC_BASE_FIRE_DOT_EXPLOSION);

        float exScale = isAnim ? FIRE_EXPLOSION_ANIM_DAMAGE_SCALE : FIRE_EXPLOSION_DAMAGE_SCALE;
        float exBonus = isAnim ? FIRE_EXPLOSION_ANIM_BONUS_DAMAGE : FIRE_EXPLOSION_BONUS_DAMAGE;
        float exDmg = (baseEx * exScale) + exBonus;
        exDmg = scaleForPvp(target, exDmg);

        float rad = isAnim ? FIRE_EXPLOSION_ANIM_RADIUS : FIRE_EXPLOSION_RADIUS;

        td.setFloat(FD_KEY_EXDMG, exDmg);
        td.setFloat(FD_KEY_EXRAD, Math.max(0f, rad));

        if (w != null){
            w.playSoundAtEntity(target, "random.fizz", 0.45f, isAnim ? 1.35f : 1.20f);
            spawnFireImpact(w, target, isAnim);
        }
    }

    private static void clearFireDot(EntityLivingBase e){
        if (e == null) return;
        NBTTagCompound d = e.getEntityData();
        d.removeTag(FD_KEY_END);
        d.removeTag(FD_KEY_NEXT);
        d.removeTag(FD_KEY_OWNER);
        d.removeTag(FD_KEY_DMG);
        d.removeTag(FD_KEY_INT);
        d.removeTag(FD_KEY_EXDMG);
        d.removeTag(FD_KEY_EXRAD);
        d.removeTag(FD_KEY_ANIM);
    }

    private static void tickFireDot(EntityLivingBase ent, NBTTagCompound data, World w, long now){
        if (ent == null || data == null) return;

        long end = data.getLong(FD_KEY_END);
        if (end <= 0L) return;

        boolean isAnim = data.getBoolean(FD_KEY_ANIM);

        // finished -> explode
        if (now >= end){
            float exDmg = data.getFloat(FD_KEY_EXDMG);
            float rad = data.getFloat(FD_KEY_EXRAD);

            // VFX
            if (w != null){
                w.playSoundAtEntity(ent, "random.explode", 0.75f, isAnim ? 1.18f : 1.06f);
                spawnFireExplosion(w, ent.posX, ent.posY + ent.height * 0.5, ent.posZ, isAnim);
            }

            // Damage: AoE around the target
            EntityPlayer owner = null;
            int ownerId = data.getInteger(FD_KEY_OWNER);
            if (ownerId != 0 && w != null){
                Entity o = w.getEntityByID(ownerId);
                if (o instanceof EntityPlayer) owner = (EntityPlayer) o;
            }

            if (rad <= 0.05f){
                dealProcDamageNullable(owner, ent, exDmg);
            } else {
                AxisAlignedBB bb = AxisAlignedBB.getBoundingBox(
                        ent.posX - rad, ent.posY - rad, ent.posZ - rad,
                        ent.posX + rad, ent.posY + rad, ent.posZ + rad
                );
                @SuppressWarnings("unchecked")
                List<EntityLivingBase> list = w.getEntitiesWithinAABB(EntityLivingBase.class, bb);

                for (EntityLivingBase t : list){
                    if (t == null || t.isDead) continue;
                    if (owner != null && t == owner) continue;

                    double dx = t.posX - ent.posX;
                    double dz = t.posZ - ent.posZ;
                    double dist2 = (dx * dx) + (dz * dz);
                    if (dist2 > (double) (rad * rad)) continue;

                    dealProcDamageNullable(owner, t, exDmg);

                    // knock outward (small)
                    if (dx != 0 || dz != 0){
                        float kb = isAnim ? 0.55f : 0.40f;
                        try {
                            t.addVelocity(dx * 0.08 * kb, 0.08 * kb, dz * 0.08 * kb);
                            t.velocityChanged = true;
                        } catch (Throwable ignored) {}
                    }

                    // ignite nearby
                    int secs = isAnim ? FIRE_DOT_ANIM_SET_FIRE_SECONDS : FIRE_DOT_SET_FIRE_SECONDS;
                    if (secs > 0){
                        try { t.setFire(secs); } catch (Throwable ignored) {}
                    }
                }
            }

            clearFireDot(ent);
            return;
        }

        // ticking
        long next = data.getLong(FD_KEY_NEXT);
        int interval = Math.max(1, data.getInteger(FD_KEY_INT));
        if (next <= 0L || now >= next){
            float tickDmg = data.getFloat(FD_KEY_DMG);

            EntityPlayer owner = null;
            int ownerId = data.getInteger(FD_KEY_OWNER);
            if (ownerId != 0 && w != null){
                Entity o = w.getEntityByID(ownerId);
                if (o instanceof EntityPlayer) owner = (EntityPlayer) o;
            }

            dealProcDamageNullable(owner, ent, tickDmg);

            // keep them burning
            int secs = isAnim ? FIRE_DOT_ANIM_SET_FIRE_SECONDS : FIRE_DOT_SET_FIRE_SECONDS;
            if (secs > 0){
                try { ent.setFire(secs); } catch (Throwable ignored) {}
            }

            if (w != null){
                spawnFireDotTick(w, ent, isAnim);
            }

            data.setLong(FD_KEY_NEXT, now + interval);
        }
    }


    // -------------------------------------------------------------
    // VOID: Gravity Well (defensive proc) + tick
    // -------------------------------------------------------------

    private static void tryProcVoidOnAttack(EntityPlayer attacker, EntityLivingBase target, float dealtDamage){
        if (attacker == null || target == null) return;
        World w = attacker.worldObj;
        if (w == null || w.isRemote) return;

        // must have a void gem equipped (held item or armor)
        VoidMatch match = findVoidMatch(attacker);
        if (!match.hasFlat && !match.hasAnim) return;

        String type = match.voidType;

        // Gravity Well: offensive version centers on the hit target
        if (isVoidTypeGravityWell(type)){
            if (!roll(attacker, VOID_GW_PROC_CHANCE_ON_HIT)) return;
            if (!cooldownReady(attacker, "HexOrbCD_VoidGW", VOID_GW_COOLDOWN_TICKS)) return;

            long now = serverNow(w);
            stampVoidHud(attacker, match.debugStack, type, "Gravity Well",
                    now + (long) VOID_GW_COOLDOWN_TICKS,
                    now + (long) VOID_GW_DURATION_TICKS,
                    VOID_GW_COOLDOWN_TICKS);

            float base = dealtDamage;
            if (Float.isNaN(base) || Float.isInfinite(base)) base = 1.0f;
            if (base < 0.5f) base = 0.5f;

            // center on the target you hit (feels offensive)
            double cx = target.posX;
            double cy = target.posY + (target.height * 0.5);
            double cz = target.posZ;

            startVoidGravityWellAt(attacker, cx, cy, cz, base, match.hasAnim);
            return;
        }

        // Entropy: apply a DoT to the target (and heal you at the end)
        if (isVoidTypeEntropy(type)){
            if (!VOID_ENTROPY_ENABLED) return;
            if (!roll(attacker, VOID_ENTROPY_PROC_CHANCE_ON_HIT)) return;
            if (!cooldownReady(attacker, "HexOrbCD_VoidEntropy", VOID_ENTROPY_COOLDOWN_TICKS)) return;

            long now = serverNow(w);
            stampVoidHud(attacker, match.debugStack, type, "Entropy",
                    now + (long) VOID_ENTROPY_COOLDOWN_TICKS,
                    now + (long) VOID_ENTROPY_DURATION_TICKS,
                    VOID_ENTROPY_COOLDOWN_TICKS);

            applyVoidEntropy(attacker, target, match.hasAnim, now);
            return;
        }


        // Abyss Mark: AoE marks (detonate after timer)
        if (isVoidTypeAbyssMark(type)){
            if (!VOID_ABYSS_MARK_ENABLED) return;
            if (!roll(attacker, VOID_ABYSS_MARK_PROC_CHANCE_ON_HIT)) return;

            long now = serverNow(w);
            applyVoidAbyssMarkAoE(attacker, target, match.debugStack, type, match.hasAnim, now);
            return;
        }

        // Null Shell: charge bonus on HIT (rare)
        if (isVoidTypeNullShell(type)){
            long now = serverNow(w);
            tryBoostVoidNullShellCharge(attacker, match, now);
            return;
        }

    }

    private static void tryProcVoidOnDamaged(EntityPlayer victim, DamageSource src, float incomingDamage){
        if (victim == null || src == null) return;
        World w = victim.worldObj;
        if (w == null || w.isRemote) return;

        // prevent recursion / weird sources
        String dt = src.getDamageType();
        if ("hexorb".equals(dt)) return;
        if ("thorns".equals(dt)) return;

        // must have a void gem equipped (held item or armor)
        VoidMatch match = findVoidMatch(victim);
        if (!match.hasFlat && !match.hasAnim) return;

        String type = match.voidType;

        // Gravity Well: defensive proc centered on you
        if (isVoidTypeGravityWell(type)){
            if (!roll(victim, VOID_GW_PROC_CHANCE)) return;
            if (!cooldownReady(victim, "HexOrbCD_VoidGW", VOID_GW_COOLDOWN_TICKS)) return;

            long now = serverNow(w);
            stampVoidHud(victim, match.debugStack, type, "Gravity Well",
                    now + (long) VOID_GW_COOLDOWN_TICKS,
                    now + (long) VOID_GW_DURATION_TICKS,
                    VOID_GW_COOLDOWN_TICKS);

            startVoidGravityWell(victim, incomingDamage, match.hasAnim);
            return;
        }

        // Entropy: defensive version curses the attacker (if any)
        if (isVoidTypeEntropy(type)){
            if (!VOID_ENTROPY_ENABLED) return;

            Entity srcEnt = src.getEntity();
            EntityLivingBase attacker = (srcEnt instanceof EntityLivingBase) ? (EntityLivingBase) srcEnt : null;
            if (attacker == null || attacker == victim) return;

            if (!roll(victim, VOID_ENTROPY_PROC_CHANCE)) return;
            if (!cooldownReady(victim, "HexOrbCD_VoidEntropy", VOID_ENTROPY_COOLDOWN_TICKS)) return;

            long now = serverNow(w);
            stampVoidHud(victim, match.debugStack, type, "Entropy",
                    now + (long) VOID_ENTROPY_COOLDOWN_TICKS,
                    now + (long) VOID_ENTROPY_DURATION_TICKS,
                    VOID_ENTROPY_COOLDOWN_TICKS);

            applyVoidEntropy(victim, attacker, match.hasAnim, now);
            return;
        }


        // Abyss Mark: AoE marks (detonate after timer) - defensive proc is centered on YOU
        if (isVoidTypeAbyssMark(type)){
            if (!VOID_ABYSS_MARK_ENABLED) return;
            if (!roll(victim, VOID_ABYSS_MARK_PROC_CHANCE)) return;

            long now = serverNow(w);
            applyVoidAbyssMarkAoE(victim, victim, match.debugStack, type, match.hasAnim, now);
            return;
        }

        // Null Shell: (defensive) we do the TRUE dodge inside LivingAttack/LivingHurt so we can cancel damage.
// Here we only handle the optional "gain extra charge when hit" behavior.
        if (isVoidTypeNullShell(type)){
            long now = serverNow(w);
            tryBoostVoidNullShellCharge(victim, match, now);
            return;
        }

        if (DEBUG_PROC && type == null){
            debugOncePerSecond(victim, "[HexOrb][Void] type missing on host=" + itemLabel(match.debugStack));
        }
    }

    private static boolean isVoidTypeGravityWell(String t){
        if (t == null) return false;
        String s = t.trim().toLowerCase();
        // tolerate a few variants
        return (s.contains("gravity") && s.contains("well")) || "gw".equals(s) || "gravitywell".equals(s);
    }

    private static boolean isVoidTypeEntropy(String t){
        if (t == null) return false;
        String s = t.trim().toLowerCase();
        // tolerate common misspellings / shorthand
        return s.contains("entropy") || s.contains("entrophy") || "ent".equals(s) || "voidentropy".equals(s);
    }


    private static boolean isVoidTypeAbyssMark(String t){
        if (t == null) return false;
        String s = t.trim().toLowerCase();
        // tolerate variants: "Abyss Mark", "AbyssMark", "Void Abyss Mark", etc.
        return (s.contains("abyss") && s.contains("mark")) || "am".equals(s) || "abyssmark".equals(s) || "voidabyssmark".equals(s);
    }





    private static boolean isVoidTypeNullShell(String t){
        if (t == null) return false;
        String s = t.trim().toLowerCase();
        // tolerate variants: "Null Shell", "NullShell", "Void Null Shell", etc.
        return (s.contains("null") && s.contains("shell")) || "ns".equals(s) || "nullshell".equals(s) || "voidnullshell".equals(s);
    }

    /** Attempt a Null Shell passive dodge. Returns true if the incoming hit should be negated. */
    private static boolean tryVoidNullShellDodge(EntityPlayer victim, DamageSource src, long now){
        if (victim == null || victim.worldObj == null || victim.worldObj.isRemote) return false;
        if (!VOID_ENABLED || !VOID_NULL_SHELL_ENABLED) return false;

        // prevent recursion / weird sources
        if (src != null){
            String dt = src.getDamageType();
            if ("hexorb".equals(dt)) return false;
            if ("thorns".equals(dt)) return false;
        }

        // must have a void gem equipped (held item or armor)
        VoidMatch match = findVoidMatch(victim);
        if (match == null || (!match.hasFlat && !match.hasAnim)) return false;
        if (!match.found || !isVoidTypeNullShell(match.voidType)) return false;

        // If it procs, this is a TRUE dodge: caller should cancel/zero the hit.
        return tryNullShellPassiveDash(victim, src, match, now);
    }

    /** De-dupe Null Shell dodge roll across Attack+Hurt in the same tick. */
    private static boolean markNullShellDodgeOncePerTick(EntityPlayer p, long now){
        if (p == null) return true;
        NBTTagCompound d = p.getEntityData();
        long last = d.getLong("HexOrb_NSDodgeTick");
        if (last == now) return false;
        d.setLong("HexOrb_NSDodgeTick", now);
        return true;
    }




    private static int getLightLevelAt(World w, EntityPlayer p){
        if (w == null || p == null) return 15;
        int x = (int) Math.floor(p.posX);
        int y = (int) Math.floor(p.posY + 0.25);
        int z = (int) Math.floor(p.posZ);
        try {
            return w.getBlockLightValue(x, y, z);
        } catch (Throwable ignored) {
            return 15;
        }
    }

    /**
     * Passive Null Shell charging (runs ~1x per second).
     * Mirrors charge onto the active host item so the client HUD can draw it.
     */
    private static void tickVoidNullShellCharge(EntityPlayer p, World w, NBTTagCompound data, long now){
        if (!VOID_ENABLED || !VOID_NULL_SHELL_ENABLED) return;
        if (p == null || w == null || data == null || w.isRemote) return;

        long next = data.getLong(VNS_KEY_NEXT_PASSIVE);
        if (now < next) return;
        data.setLong(VNS_KEY_NEXT_PASSIVE, now + 20);

        VoidMatch match = findVoidMatch(p);
        if (match == null || match.debugStack == null) return;

        String type = match.voidType;
        if (!isVoidTypeNullShell(type)) return;

        int max = getNullShellMaxCharge();
        if (max <= 0) max = 1000;

        int charge = data.getInteger(VNS_KEY_CHARGE);
        if (charge < 0) charge = 0;
        if (charge > max) charge = max;

        int light = getLightLevelAt(w, p);
        boolean isDark = light <= VOID_NULL_SHELL_DARK_LIGHT_MAX;
        boolean isBright = light >= VOID_NULL_SHELL_BRIGHT_LIGHT_MIN;

        if (isDark){
            charge += VOID_NULL_SHELL_PASSIVE_DARK_GAIN_PER_SEC;
        } else if (isBright){
            if (roll(p, VOID_NULL_SHELL_PASSIVE_LIGHT_CHANCE_PER_SEC)){
                charge += VOID_NULL_SHELL_PASSIVE_LIGHT_GAIN;
            }
        } else {
            // mid light: half of bright chance/gain
            if (roll(p, VOID_NULL_SHELL_PASSIVE_LIGHT_CHANCE_PER_SEC * 0.5f)){
                charge += Math.max(1, VOID_NULL_SHELL_PASSIVE_LIGHT_GAIN / 2);
            }
        }

        if (charge < 0) charge = 0;
        if (charge > max) charge = max;

        data.setInteger(VNS_KEY_CHARGE, charge);
        stampNullShellChargeHud(p, match.debugStack, type, charge, max, now, data);
    }/** Spawns the Void Protection particles and clears the buff when it expires. */
    private static void tickVoidNullShellDefenseBuff(EntityPlayer p, World w, NBTTagCompound data, long now){
        if (!VOID_ENABLED || !VOID_NULL_SHELL_ENABLED) return;
        if (p == null || w == null || data == null || w.isRemote) return;

        long end = data.getLong(VNS_KEY_DEF_END);
        if (end <= 0L) return;

        // Expired?
        if (now >= end){
            data.removeTag(VNS_KEY_DEF_END);
            data.removeTag(VNS_KEY_DEF_NEXT_FX);
            return;
        }

        // Only keep the buff if the player still has Null Shell equipped
        VoidMatch match = findVoidMatch(p);
        if (match == null || !match.found || !isVoidTypeNullShell(match.voidType)){
            data.removeTag(VNS_KEY_DEF_END);
            data.removeTag(VNS_KEY_DEF_NEXT_FX);
            return;
        }

        long nextFx = data.getLong(VNS_KEY_DEF_NEXT_FX);
        if (now < nextFx) return;
        data.setLong(VNS_KEY_DEF_NEXT_FX, now + 2L); // every 2 ticks

        spawnNullShellProtectionParticles(w, p);
    }

    /** While charging Void Push, spawn a growing particle aura so everyone can see it. */
    private static void tickVoidNullShellPushCharge(World w, EntityPlayer p, NBTTagCompound data, long now){
        if (!VOID_ENABLED || !VOID_NULL_SHELL_ENABLED) return;
        if (w == null || p == null || data == null) return;
        if (w.isRemote) return;

        if (data.getByte(VNS_KEY_PUSH_CHARGING) == 0) return;

        long start = data.getLong(VNS_KEY_PUSH_START);
        if (start <= 0L){
            data.removeTag(VNS_KEY_PUSH_CHARGING);
            return;
        }

        long held = now - start;
        if (held < 0L) held = 0L;
        if (held > (long) (NS_PUSH_CHARGE_TICKS + NS_PUSH_OVERCHARGE_TICKS)) held = (long) (NS_PUSH_CHARGE_TICKS + NS_PUSH_OVERCHARGE_TICKS);

        float pct = held / (float) (NS_PUSH_CHARGE_TICKS + NS_PUSH_OVERCHARGE_TICKS);
        if (pct < 0f) pct = 0f;
        if (pct > 1f) pct = 1f;

        // particle density ramps up with charge
        if (w instanceof WorldServer){
            WorldServer ws = (WorldServer) w;
            int count = 6 + (int) (pct * 26f);

            // tighter in first-person would require a client particle;
            // this server aura is centered slightly above the camera to reduce blinding.
            ws.func_147487_a("mobSpell", p.posX, p.posY + 1.05, p.posZ, count, 0.45, 0.70, 0.45, 0.01);
            ws.func_147487_a("portal", p.posX, p.posY + 1.00, p.posZ, (int)(count * 0.6f), 0.40, 0.55, 0.40, 0.01);

            // overcharge: add a stronger "end" sparkle
            if (held >= (long) NS_PUSH_CHARGE_TICKS){
                ws.func_147487_a("witchMagic", p.posX, p.posY + 1.10, p.posZ, 4 + (int)(pct * 8f), 0.45, 0.65, 0.45, 0.01);
            }
        }
    }

    private static void doNullShellVoidPush(EntityPlayer p, float mult){
        if (p == null) return;
        World w = p.worldObj;
        if (w == null || w.isRemote) return;

        float radius = NS_PUSH_RADIUS;
        AxisAlignedBB bb = AxisAlignedBB.getBoundingBox(
                p.posX - radius, p.posY - 1.0, p.posZ - radius,
                p.posX + radius, p.posY + 3.0, p.posZ + radius
        );

        @SuppressWarnings("unchecked")
        List<EntityLivingBase> list = w.getEntitiesWithinAABB(EntityLivingBase.class, bb);
        if (list == null || list.isEmpty()) list = Collections.emptyList();

        // DEX scaling (Null Shell Push) - server-safe snapshot
        HexPlayerStats.Snapshot s = HexPlayerStats.snapshot(p);
        double dexEff = (s != null) ? ((double)s.dex * (double)s.currentMulti) : getDexterityEffective(p);
        float scaled = NS_PUSH_BASE_DAMAGE + (float)(dexEff * (double)NS_PUSH_STAT_SCALE);
        float dmg = scaled * mult;

        if (w instanceof WorldServer){
            WorldServer ws = (WorldServer) w;
            ws.func_147487_a("largeexplode", p.posX, p.posY + 0.2, p.posZ, 1, 0, 0, 0, 0);
            spawnVoidExplosionSphere(ws, p.posX, p.posY + 0.9, p.posZ, radius);
            ws.func_147487_a("portal", p.posX, p.posY + 0.9, p.posZ, 70, radius * 0.20, 0.65, radius * 0.20, 0.04);
            ws.func_147487_a("mobSpell", p.posX, p.posY + 1.0, p.posZ, 60, radius * 0.20, 0.65, radius * 0.20, 0.03);
            ws.func_147487_a("witchMagic", p.posX, p.posY + 1.0, p.posZ, 35, radius * 0.18, 0.60, radius * 0.18, 0.03);
        }
        w.playSoundAtEntity(p, "random.explode", 0.9f, 0.75f);

        for (EntityLivingBase t : list){
            if (t == null || t == p) continue;
            if (!isDamageable(t)) continue;

            double dist = p.getDistanceToEntity(t);
            if (dist > (double) radius + 0.25) continue;

            float falloff = 1.0f - (float) (dist / (double) radius);
            if (falloff < 0.10f) falloff = 0.10f;

            float finalDmg = scaleForPvp(t, dmg * falloff);
            dealNullShellProcDamage(p, t, finalDmg, "NullShellPush");

            // Strong radial knockback
            double kbH = NS_PUSH_KB_H * (double) falloff;
            double kbY = NS_PUSH_KB_Y * (double) falloff;
            applyKnockbackFrom(p, t, kbH, kbY);

            if (w instanceof WorldServer){
                WorldServer ws = (WorldServer) w;
                ws.func_147487_a("portal", t.posX, t.posY + 0.6, t.posZ, 10, 0.35, 0.45, 0.35, 0.01);
            }
        }
    }


    private static void spawnNullShellProtectionParticles(World w, EntityPlayer p){
        if (!(w instanceof WorldServer) || p == null) return;
        WorldServer ws = (WorldServer) w;

        // Spawn around torso to avoid blinding first-person too much
        double x = p.posX;
        double y = p.posY + 0.8;
        double z = p.posZ;

        double sx = 0.35;
        double sy = 0.55;
        double sz = 0.35;

        // "mobSpell" is the voidy/purple spell look; portal adds some depth
        ws.func_147487_a("mobSpell", x, y, z, 10, sx, sy, sz, 0.0);
        if ((ws.getTotalWorldTime() & 1L) == 0L){
            ws.func_147487_a("portal", x, y, z, 4, sx, sy, sz, 0.0);
        }
    }



    /**
     * Combat bonus charging (rare). Call on HIT and on HURT.
     */
    private static void tryBoostVoidNullShellCharge(EntityPlayer p, VoidMatch match, long now){
        if (!VOID_ENABLED || !VOID_NULL_SHELL_ENABLED) return;
        if (p == null || match == null || match.debugStack == null) return;

        World w = p.worldObj;
        if (w == null || w.isRemote) return;

        NBTTagCompound data = p.getEntityData();
        if (data == null) return;

        String type = match.voidType;
        if (!isVoidTypeNullShell(type)) return;

        int max = getNullShellMaxCharge();
        if (max <= 0) max = 1000;

        int charge = data.getInteger(VNS_KEY_CHARGE);
        if (charge < 0) charge = 0;
        if (charge >= max) return;

        int light = getLightLevelAt(w, p);
        boolean dark = light <= VOID_NULL_SHELL_DARK_LIGHT_MAX;

        float chance = dark ? VOID_NULL_SHELL_COMBAT_BONUS_CHANCE_DARK : VOID_NULL_SHELL_COMBAT_BONUS_CHANCE_LIGHT;
        int gain = dark ? VOID_NULL_SHELL_COMBAT_BONUS_GAIN_DARK : VOID_NULL_SHELL_COMBAT_BONUS_GAIN_LIGHT;

        if (!roll(p, chance)) return;

        charge += gain;
        if (charge > max) charge = max;

        data.setInteger(VNS_KEY_CHARGE, charge);
        stampNullShellChargeHud(p, match.debugStack, type, charge, max, now, data);
    }

    /**
     * Writes Null Shell charge fields onto the host stack so the client HUD can read them.
     * Throttled to avoid spamming inventory NBT sync.
     */
    private static void stampNullShellChargeHud(EntityPlayer owner, ItemStack host, String voidType, int charge, int max, long now, NBTTagCompound ownerData){
        if (host == null || owner == null) return;

        if (max <= 0) max = 1000;
        if (charge < 0) charge = 0;
        if (charge > max) charge = max;

        int pct = (int) Math.floor((charge * 100.0) / (double) max);
        if (pct < 0) pct = 0;
        if (pct > 100) pct = 100;

        if (ownerData != null){
            int lastPct = ownerData.getInteger(VNS_KEY_LAST_PCT);
            long lastStamp = ownerData.getLong(VNS_KEY_LAST_STAMP);
            if (pct == lastPct && (now - lastStamp) < (long) VOID_NULL_SHELL_STAMP_MIN_INTERVAL_TICKS){
                return;
            }
            ownerData.setInteger(VNS_KEY_LAST_PCT, pct);
            ownerData.setLong(VNS_KEY_LAST_STAMP, now);
        }

        NBTTagCompound tag = host.getTagCompound();
        if (tag == null) tag = new NBTTagCompound();

        String t = (voidType != null && voidType.length() > 0) ? voidType : "Null Shell";
        tag.setString("HexVoidHudType", t);
        tag.setInteger("HexVoidCharge", charge);
        tag.setInteger("HexVoidChargeMax", max);
        // Back-compat (older HUD builds)
        tag.setInteger("HexNullShellCharge", charge);
        tag.setInteger("HexNullShellChargeMax", max);
        host.setTagCompound(tag);

        if (owner instanceof EntityPlayerMP){
            try {
                ((EntityPlayerMP) owner).inventoryContainer.detectAndSendChanges();
            } catch (Throwable ignored) {}
        }
    }

    private static void startVoidGravityWell(EntityPlayer caster, float incomingDamage, boolean isAnim){
        if (caster == null) return;

        float base = incomingDamage;
        if (Float.isNaN(base) || Float.isInfinite(base)) base = 1.0f;
        if (base < 0.5f) base = 0.5f;

        startVoidGravityWellAt(caster, caster.posX, caster.posY + 0.85, caster.posZ, base, isAnim);
    }

    /**
     * Starts a Gravity Well centered at (cx,cy,cz). Used by both defensive (center on you) and offensive (center on hit target) procs.
     */
    private static void startVoidGravityWellAt(EntityPlayer caster, double cx, double cy, double cz, float baseDamage, boolean isAnim){
        if (caster == null) return;
        World w = caster.worldObj;
        if (w == null || w.isRemote) return;

        NBTTagCompound data = caster.getEntityData();
        if (data == null) return;

        long now = serverNow(w);

        float base = baseDamage;
        if (Float.isNaN(base) || Float.isInfinite(base)) base = 1.0f;
        if (base < 0.5f) base = 0.5f;

        data.setBoolean(VOID_GW_KEY_ACTIVE, true);
        data.setLong(VOID_GW_KEY_END, now + (long) VOID_GW_DURATION_TICKS);
        data.setDouble(VOID_GW_KEY_X, cx);
        data.setDouble(VOID_GW_KEY_Y, cy);
        data.setDouble(VOID_GW_KEY_Z, cz);
        data.setFloat(VOID_GW_KEY_BASE, base);
        data.setBoolean(VOID_GW_KEY_ANIM, isAnim);
        data.setInteger(VOID_GW_KEY_TICK, 0);

        // a small sound cue (server)
        try {
            w.playSoundEffect(cx, cy, cz, "portal.trigger", 0.75f, isAnim ? 0.70f : 0.85f);
        } catch (Throwable ignored) {}
    }


    private static void tickVoidGravityWell(EntityPlayer caster, NBTTagCompound data, World w, long now){
        if (caster == null || data == null || w == null || w.isRemote) return;
        if (!data.getBoolean(VOID_GW_KEY_ACTIVE)) return;

        long end = data.getLong(VOID_GW_KEY_END);
        if (end <= 0L){
            clearVoidGravityWell(data);
            return;
        }

        boolean isAnim = data.getBoolean(VOID_GW_KEY_ANIM);
        double cx = data.getDouble(VOID_GW_KEY_X);
        double cy = data.getDouble(VOID_GW_KEY_Y);
        double cz = data.getDouble(VOID_GW_KEY_Z);

        if (cx == 0 && cy == 0 && cz == 0){
            cx = caster.posX;
            cy = caster.posY + 0.85;
            cz = caster.posZ;
            data.setDouble(VOID_GW_KEY_X, cx);
            data.setDouble(VOID_GW_KEY_Y, cy);
            data.setDouble(VOID_GW_KEY_Z, cz);
        }

        float base = data.getFloat(VOID_GW_KEY_BASE);
        if (base <= 0f) base = 1.0f;

        float radius = isAnim ? VOID_GW_RADIUS_ANIM : VOID_GW_RADIUS;
        float pull   = isAnim ? VOID_GW_PULL_STRENGTH_ANIM : VOID_GW_PULL_STRENGTH;

        int tick = data.getInteger(VOID_GW_KEY_TICK) + 1;
        data.setInteger(VOID_GW_KEY_TICK, tick);

        // visuals (purple/black swirl)
        spawnVoidGravityParticles(w, cx, cy, cz, radius, tick, isAnim);

        // apply pull + periodic tick damage while active
        if (tick % 2 == 0){
            AxisAlignedBB bb = AxisAlignedBB.getBoundingBox(cx - radius, cy - radius, cz - radius, cx + radius, cy + radius, cz + radius);
            @SuppressWarnings("unchecked")
            List<EntityLivingBase> list = w.getEntitiesWithinAABB(EntityLivingBase.class, bb);

            if (list != null && !list.isEmpty()){
                for (int i = 0; i < list.size(); i++){
                    EntityLivingBase t = list.get(i);
                    if (t == null || t == caster) continue;
                    if (t.isDead) continue;

                    // pull toward center
                    pullTowardPoint(t, cx, cy, cz, pull, 0.02);
                    // Entropy DoT (Void): tag pulled targets so they take void damage over time
                    applyVoidEntropy(caster, t, isAnim, now);


                    // periodic damage
                    if (VOID_GW_DAMAGE_PERIOD_TICKS > 0 && (tick % VOID_GW_DAMAGE_PERIOD_TICKS) == 0){
                        float dmg = base * VOID_GW_TICK_DAMAGE_SCALE;
                        if (dmg > 0f){
                            dealProcDamage(caster, t, dmg, "VoidGW");
                        }
                    }
                }
            }
        }

        // end: burst damage + extra particles
        if (now >= end){
            AxisAlignedBB bb = AxisAlignedBB.getBoundingBox(cx - radius, cy - radius, cz - radius, cx + radius, cy + radius, cz + radius);
            @SuppressWarnings("unchecked")
            List<EntityLivingBase> list = w.getEntitiesWithinAABB(EntityLivingBase.class, bb);

            if (list != null && !list.isEmpty()){
                for (int i = 0; i < list.size(); i++){
                    EntityLivingBase t = list.get(i);
                    if (t == null || t == caster) continue;
                    if (t.isDead) continue;

                    // Refresh entropy on anything still inside when time resumes
                    applyVoidEntropy(caster, t, isAnim, now);

                    float dmg = base * VOID_GW_BURST_DAMAGE_SCALE;
                    if (dmg > 0f){
                        dealProcDamage(caster, t, dmg, "VoidGW_Burst");
                    }

                    // small vertical pop
                    try {
                        t.addVelocity(0.0, 0.12, 0.0);
                        t.velocityChanged = true;
                    } catch (Throwable ignored) {}
                }
            }

            // final swirl burst
            spawnVoidGravityBurstParticles(w, cx, cy, cz, radius, isAnim);
            clearVoidGravityWell(data);
        }
    }

    private static void clearVoidGravityWell(NBTTagCompound data){
        if (data == null) return;
        data.removeTag(VOID_GW_KEY_ACTIVE);
        data.removeTag(VOID_GW_KEY_END);
        data.removeTag(VOID_GW_KEY_X);
        data.removeTag(VOID_GW_KEY_Y);
        data.removeTag(VOID_GW_KEY_Z);
        data.removeTag(VOID_GW_KEY_BASE);
        data.removeTag(VOID_GW_KEY_ANIM);
        data.removeTag(VOID_GW_KEY_TICK);
    }

    /**
     * Null Shell: spawns a hollow "sphere shell" of void particles at the given radius.
     * Called on the 100% push explosion so players can see the true AoE size.
     */
    private static void spawnVoidExplosionSphere(WorldServer ws, double cx, double cy, double cz, float radius){
        if (ws == null) return;
        if (radius <= 0.25f) return;

        // Keep it lightweight: scale point density with radius, but clamp for performance.
        int rings = (int) Math.ceil(radius * 2.0f);
        if (rings < 6) rings = 6;
        if (rings > 18) rings = 18;

        int basePts = (int) Math.ceil(radius * 6.0f);
        if (basePts < 16) basePts = 16;
        if (basePts > 48) basePts = 48;

        // "Our" void palette (matches /voidsphere): portal + witch shimmer + deep purple mobSpell glow.
        final double GLOW_R = 0.55D;
        final double GLOW_G = 0.06D;
        final double GLOW_B = 0.78D;

        for (int i = 0; i <= rings; i++){
            double v = (double) i / (double) rings;          // 0..1
            double phi = Math.acos(1.0 - 2.0 * v);           // 0..pi (roughly uniform)
            double y = Math.cos(phi);
            double sinPhi = Math.sin(phi);

            int ringPts = (int) Math.round(basePts * sinPhi);
            if (ringPts < 8) ringPts = 8;

            for (int j = 0; j < ringPts; j++){
                double theta = (2.0 * Math.PI * (double) j) / (double) ringPts;
                double x = Math.cos(theta) * sinPhi;
                double z = Math.sin(theta) * sinPhi;

                // Slight thickness so the shell reads better at high speed
                double rr = (double) radius + ((ws.getTotalWorldTime() & 1L) == 0L ? 0.12D : -0.12D);

                double px = cx + (x * rr);
                double py = cy + (y * rr);
                double pz = cz + (z * rr);

                // Exact point particles (dx/dy/dz = 0). Speed is tiny so they appear "alive".
                ws.func_147487_a("portal", px, py, pz, 1, 0, 0, 0, 0.01);
                if ((j & 3) == 0){
                    ws.func_147487_a("witchMagic", px, py, pz, 1, 0, 0, 0, 0.01);
                }
                // mobSpell uses RGB when count=0 and dx/dy/dz are set to color components.
                if ((j & 1) == 0){
                    ws.func_147487_a("mobSpell", px, py, pz, 0, GLOW_R, GLOW_G, GLOW_B, 1.0D);
                }
            }
        }
    }

    private static void spawnVoidGravityParticles(World w, double cx, double cy, double cz, float radius, int tick, boolean isAnim){
        if (!(w instanceof WorldServer)) return;
        WorldServer ws = (WorldServer) w;

        // Guaranteed center swirl so you always see the "hole" even if ring particles are subtle
        try {
            ws.func_147487_a("portal", cx, cy, cz, isAnim ? 14 : 10, 0.28, 0.18, 0.28, 0.02);
            ws.func_147487_a("witchMagic", cx, cy, cz, isAnim ? 10 : 6, 0.22, 0.12, 0.22, 0.01);
        } catch (Throwable ignored) {}

        int rings = isAnim ? 10 : 7;
        double t = (double) tick * 0.35;
        for (int i = 0; i < rings; i++){
            double ang = t + (6.283185307179586 * (double) i / (double) rings);
            double r = radius * (0.35 + 0.25 * ws.rand.nextDouble());
            double px = cx + Math.cos(ang) * r;
            double pz = cz + Math.sin(ang) * r;
            double py = cy + (ws.rand.nextDouble() * 0.25 - 0.12);

            // deep purple glow (mobSpell uses RGB in dx/dy/dz)
            ws.func_147487_a("mobSpell", px, py, pz, 0, 0.45, 0.08, 0.62, 1.0);
            // dark core specks
            ws.func_147487_a("mobSpell", cx, cy, cz, 0, 0.02, 0.00, 0.03, 1.0);

            // subtle portal wisps
            if (ws.rand.nextInt(3) == 0){
                ws.func_147487_a("portal", px, py, pz, 2, 0.05, 0.05, 0.05, 0.01);
            }
        }
    }

    private static void spawnVoidGravityBurstParticles(World w, double cx, double cy, double cz, float radius, boolean isAnim){
        if (!(w instanceof WorldServer)) return;
        WorldServer ws = (WorldServer) w;

        // Burst flash at the center
        try {
            ws.func_147487_a("portal", cx, cy, cz, isAnim ? 28 : 20, radius * 0.35, radius * 0.18, radius * 0.35, 0.06);
            ws.func_147487_a("witchMagic", cx, cy, cz, isAnim ? 18 : 12, radius * 0.25, radius * 0.12, radius * 0.25, 0.02);
        } catch (Throwable ignored) {}

        int total = isAnim ? 80 : 55;
        for (int i = 0; i < total; i++){
            double px = cx + (ws.rand.nextDouble() * 2.0 - 1.0) * radius;
            double py = cy + (ws.rand.nextDouble() * 2.0 - 1.0) * (radius * 0.55);
            double pz = cz + (ws.rand.nextDouble() * 2.0 - 1.0) * radius;

            ws.func_147487_a("mobSpell", px, py, pz, 0, 0.55, 0.06, 0.78, 1.0);
            if (ws.rand.nextInt(2) == 0){
                ws.func_147487_a("portal", px, py, pz, 2, 0.08, 0.08, 0.08, 0.02);
            }
        }
    }

    // -------------------------------------------------------------
    // VOID ORB: Entropy (DoT)
    // -------------------------------------------------------------
    private static void applyVoidEntropy(EntityPlayer owner, EntityLivingBase target, boolean isAnim, long now){
        if (!VOID_ENABLED || !VOID_ENTROPY_ENABLED) return;
        if (owner == null || target == null) return;
        if (target == owner) return;
        if ((target instanceof EntityPlayer) && !VOID_ENTROPY_AFFECT_PLAYERS) return;

        World w = target.worldObj;
        if (w == null || w.isRemote) return;

        NBTTagCompound td = target.getEntityData();
        if (td == null) return;

        long end = td.getLong(VE_KEY_END);
        boolean active = (end > 0L) && (now < end);

        int ownerIdPrev = td.getInteger(VE_KEY_OWNER);
        boolean animPrev = td.getBoolean(VE_KEY_ANIM);

        int dur = Math.max(1, VOID_ENTROPY_DURATION_TICKS);
        long newEnd = now + (long) dur;

        // start / refresh
        if (!active){
            td.setFloat(VE_KEY_TOTAL, 0f);
            td.setLong(VE_KEY_NEXT, now);
        }
        if (newEnd > end){
            td.setLong(VE_KEY_END, newEnd);
        }

        td.setInteger(VE_KEY_OWNER, owner.getEntityId());
        td.setBoolean(VE_KEY_ANIM, isAnim);
        td.setInteger(VE_KEY_INT, Math.max(1, VOID_ENTROPY_INTERVAL_TICKS));

        // cache tick damage when starting or when the owner/anim state changes
        if (!active || ownerIdPrev != owner.getEntityId() || animPrev != isAnim){
            float tickDmg = computeVoidEntropyTickDamage(owner, isAnim);
            td.setFloat(VE_KEY_DMG, tickDmg);
        }
    }

    private static void tickVoidEntropy(EntityLivingBase ent, NBTTagCompound data, World w, long now){
        if (ent == null || data == null || w == null || w.isRemote) return;

        long end = data.getLong(VE_KEY_END);
        if (end <= 0L) return;

        boolean isAnim = data.getBoolean(VE_KEY_ANIM);

        // finished -> lifesteal payout + cleanup
        if (now >= end){
            float total = data.getFloat(VE_KEY_TOTAL);
            int ownerId = data.getInteger(VE_KEY_OWNER);
            Entity ownerEnt = (ownerId != 0) ? w.getEntityByID(ownerId) : null;
            EntityPlayer owner = (ownerEnt instanceof EntityPlayer) ? (EntityPlayer) ownerEnt : null;

            if (owner != null && total > 0f && VOID_ENTROPY_LIFESTEAL_FRACTION > 0f){
                float healAbs = total * VOID_ENTROPY_LIFESTEAL_FRACTION;
                if (healAbs > 0f){
                    // Prefer DBC Body healing via percent bridge; fallback to vanilla heal
                    if (!dbcAddBodyAbsViaNbt(owner, healAbs)){
                        try { owner.heal(healAbs); } catch (Throwable ignored) {}
                    }

                    // small owner VFX
                    try {
                        spawnVoidGravityParticles(w, owner.posX, owner.posY + owner.height * 0.55, owner.posZ, isAnim ? 1.10f : 0.90f, (int)(now & 0x7fffffff), isAnim);
                    } catch (Throwable ignored) {}
                }
            }

            // end burst on target
            try {
                spawnVoidGravityBurstParticles(w, ent.posX, ent.posY + ent.height * 0.55, ent.posZ, isAnim ? 1.15f : 0.95f, isAnim);
            } catch (Throwable ignored) {}

            clearVoidEntropy(ent);
            return;
        }

        // tick window
        long next = data.getLong(VE_KEY_NEXT);
        int interval = data.getInteger(VE_KEY_INT);
        if (interval <= 0) interval = Math.max(1, VOID_ENTROPY_INTERVAL_TICKS);

        // light particles even between damage ticks
        try {
            if ((now & 1L) == 0L){
                spawnVoidGravityParticles(w, ent.posX, ent.posY + ent.height * 0.55, ent.posZ, isAnim ? 0.95f : 0.75f, (int)(now & 0x7fffffff), isAnim);
            }
        } catch (Throwable ignored) {}

        if (now < next) return;

        float dmg = data.getFloat(VE_KEY_DMG);
        if (dmg <= 0f){
            int ownerId = data.getInteger(VE_KEY_OWNER);
            Entity ownerEnt = (ownerId != 0) ? w.getEntityByID(ownerId) : null;
            EntityPlayer owner = (ownerEnt instanceof EntityPlayer) ? (EntityPlayer) ownerEnt : null;
            dmg = computeVoidEntropyTickDamage(owner, isAnim);
            data.setFloat(VE_KEY_DMG, dmg);
        }

        if (dmg > 0f){
            int ownerId = data.getInteger(VE_KEY_OWNER);
            Entity ownerEnt = (ownerId != 0) ? w.getEntityByID(ownerId) : null;
            EntityPlayer owner = (ownerEnt instanceof EntityPlayer) ? (EntityPlayer) ownerEnt : null;

            // Only the FINAL tick should knock targets back.
            // The vanilla damage source (and some mod hooks) apply knockback on each hit,
            // so for intermediate ticks we restore motion after applying damage.
            // If the next scheduled tick would reach or pass the end time,
            // treat THIS tick as the final one.
            boolean finalTick = (now + (long) interval) >= end;

            float finalDmg = dmg;
            if (ent instanceof EntityPlayer){
                finalDmg = finalDmg * DAMAGE_VS_PLAYERS_SCALE;
            }

            if (owner != null){
                if (!finalTick) {
                    // suppress per-tick knockback; keep final tick knockback intact
                    double mx0 = ent.motionX;
                    double mz0 = ent.motionZ;
                    dealProcDamage(owner, ent, finalDmg, "VoidEntropy");
                    ent.motionX = mx0;
                    ent.motionZ = mz0;
                } else {
                    dealProcDamage(owner, ent, finalDmg, "VoidEntropy");
                }
            } else {
                try { ent.attackEntityFrom(DamageSource.magic, finalDmg); } catch (Throwable ignored) {}
            }

            float total = data.getFloat(VE_KEY_TOTAL);
            data.setFloat(VE_KEY_TOTAL, total + finalDmg);
        }

        data.setLong(VE_KEY_NEXT, now + (long)interval);
    }

    // -------------------------------------------------------------
    // VOID: Abyss Mark (AoE mark -> delayed detonation)
    // -------------------------------------------------------------

    /**
     * Applies Abyss Mark to nearby entities around {@code center}. Each marked entity detonates
     * after {@link #VOID_ABYSS_MARK_DURATION_TICKS}.
     *
     * HUD: we reuse HexVoidHudCDEnd/CDMax to show "detonation in Xs".
     */
    private static void applyVoidAbyssMarkAoE(EntityPlayer owner, EntityLivingBase center, ItemStack hudHost, String type, boolean isAnim, long now){
        if (owner == null || center == null) return;
        World w = owner.worldObj;
        if (w == null || w.isRemote) return;

        float radius = isAnim ? VOID_ABYSS_MARK_MARK_RADIUS_ANIM : VOID_ABYSS_MARK_MARK_RADIUS;
        if (radius < 0.25f) radius = 0.25f;
        if (radius > 24.0f) radius = 24.0f;

        long expireTick = now + (long) VOID_ABYSS_MARK_DURATION_TICKS;

        double r = (double) radius;
        double ox = center.posX;
        double oy = center.posY + (double)center.height * 0.5D;
        double oz = center.posZ;

        AxisAlignedBB bb = AxisAlignedBB.getBoundingBox(
                ox - r, oy - r, oz - r,
                ox + r, oy + r, oz + r
        );

        List list = null;
        try {
            list = w.getEntitiesWithinAABB(EntityLivingBase.class, bb);
        } catch (Throwable ignored) {}

        // If the scan fails, still try to mark the center.
        if (list == null || list.isEmpty()){
            boolean applied = applyVoidAbyssMarkSingle(owner, center, isAnim, now, expireTick);
            if (applied){
                stampVoidHud(owner, hudHost, type, "Abyss Mark", expireTick, expireTick, VOID_ABYSS_MARK_DURATION_TICKS);
            }
            return;
        }

        int appliedCount = 0;
        int cap = VOID_ABYSS_MARK_MAX_TARGETS;
        if (cap <= 0) cap = 12;
        if (cap > 64) cap = 64; // sanity

        double r2 = r * r;

        for (int i = 0; i < list.size(); i++){
            Object o = list.get(i);
            if (!(o instanceof EntityLivingBase)) continue;
            EntityLivingBase t = (EntityLivingBase) o;

            if (t == owner) continue;
            if (t.isDead) continue;
            if (!VOID_ABYSS_MARK_AFFECT_PLAYERS && (t instanceof EntityPlayer)) continue;

            double dx = (t.posX) - ox;
            double dy = (t.posY + (double)t.height * 0.5D) - oy;
            double dz = (t.posZ) - oz;
            if ((dx*dx + dy*dy + dz*dz) > r2) continue;

            if (applyVoidAbyssMarkSingle(owner, t, isAnim, now, expireTick)){
                // Immediate visible cue
                spawnVoidGravityParticles(w,
                        t.posX,
                        t.posY + t.height * 0.55,
                        t.posZ,
                        isAnim ? 1.35f : 1.10f,
                        (int) (now & 0x7FFFFFFF),
                        isAnim);

                appliedCount++;
                if (appliedCount >= cap) break;
            }
        }

        if (appliedCount > 0){
            stampVoidHud(owner, hudHost, type, "Abyss Mark", expireTick, expireTick, VOID_ABYSS_MARK_DURATION_TICKS);

            // "cast" burst at the center so it feels like an AoE application
            spawnVoidGravityBurstParticles(w, ox, oy, oz, isAnim ? 2.05f : 1.65f, isAnim);
            try {
                w.playSoundEffect(ox, oy, oz, "portal.travel", 0.55f, isAnim ? 0.70f : 0.85f);
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Marks a single entity. Returns true only if the mark was newly applied.
     *
     * We intentionally do NOT refresh the timer if this entity is already marked by the same owner,
     * so the detonation can't be "stalled" by repeated procs.
     */
    private static boolean applyVoidAbyssMarkSingle(EntityPlayer owner, EntityLivingBase target, boolean isAnim, long now, long expireTick){
        if (owner == null || target == null) return false;
        World w = owner.worldObj;
        if (w == null || w.isRemote) return false;

        if (!VOID_ABYSS_MARK_AFFECT_PLAYERS && (target instanceof EntityPlayer)) return false;

        NBTTagCompound data = target.getEntityData();
        if (data == null) return false;

        int ownerId = owner.getEntityId();

        int curOwner = data.getInteger(VAM_KEY_OWNER);
        long curExpire = data.getLong(VAM_KEY_EXPIRE);

        if (curOwner == ownerId && curExpire > 0L && now < curExpire){
            return false;
        }

        data.setInteger(VAM_KEY_OWNER, ownerId);
        data.setLong(VAM_KEY_EXPIRE, expireTick);
        data.setBoolean(VAM_KEY_ANIM, isAnim);

        // legacy (old stacking design)
        data.setInteger(VAM_KEY_STACKS, 1);

        return true;
    }

    private static float computeVoidAbyssDetonationDamage(EntityPlayer owner, boolean isAnim){
        // "Will explosion" style scaling (DBC -> vanilla fallback)
        float baseWill = getVoidAbyssEffectiveWill(owner);

        // uses the existing field name (historical) - we scale by WILL here
        float dmg = (baseWill * VOID_ABYSS_MARK_DETONATE_STR_SCALE) + VOID_ABYSS_MARK_DETONATE_BONUS_DMG;

        if (isAnim) dmg *= VOID_ABYSS_MARK_ANIM_MULT;
        if (dmg < 1f) dmg = 1f;
        return dmg;
    }

    private static float getVoidAbyssEffectiveWill(EntityPlayer owner){
        if (owner == null) return 0f;

        // DBC: include form multipliers etc if available
        try {
            if (owner instanceof EntityPlayerMP){
                return frbGetEffectiveWill((EntityPlayerMP) owner);
            }
        } catch (Throwable ignored) {}

        // Vanilla fallback: use base max HP as a rough "power" proxy
        float hp = 0f;
        try {
            hp = owner.getMaxHealth();
        } catch (Throwable ignored) {}

        // map 20hp -> 60 "will-ish" baseline, so low-power players don't get 0 damage
        if (hp < 1f) hp = 1f;
        float approx = 60f + (hp - 20f) * 2.0f;
        if (approx < 60f) approx = 60f;
        return approx;
    }

    // Legacy AoE detonation helper (not used by the AoE/delay design, but kept for back-compat).
    private static void voidAbyssDetonateAoE(EntityPlayer owner, EntityLivingBase center, boolean isAnim, float dmg){
        if (owner == null || center == null) return;
        World w = owner.worldObj;
        if (w == null || w.isRemote) return;

        float r = isAnim ? VOID_ABYSS_MARK_DETONATE_RADIUS_ANIM : VOID_ABYSS_MARK_DETONATE_RADIUS;
        if (r <= 0f) r = 0f;

        double kb = isAnim ? VOID_ABYSS_MARK_DETONATE_KB_ANIM : VOID_ABYSS_MARK_DETONATE_KB;

        // Burst visuals at the center
        spawnVoidGravityBurstParticles(w,
                center.posX,
                center.posY + center.height * 0.55,
                center.posZ,
                isAnim ? 2.25f : 1.75f,
                isAnim);

        if (r <= 0f){
            // single target fallback
            try { dealProcDamage(owner, center, dmg, "abyssMarkSingle"); } catch (Throwable ignored) {}
            return;
        }

        AxisAlignedBB bb = AxisAlignedBB.getBoundingBox(
                center.posX - r, center.posY - r, center.posZ - r,
                center.posX + r, center.posY + r, center.posZ + r
        );

        List list;
        try {
            list = w.getEntitiesWithinAABB(EntityLivingBase.class, bb);
        } catch (Throwable t){
            return;
        }

        if (list == null) return;

        double r2 = (double)r * (double)r;
        for (int i = 0; i < list.size(); i++){
            Object o = list.get(i);
            if (!(o instanceof EntityLivingBase)) continue;
            EntityLivingBase t = (EntityLivingBase) o;

            if (t == owner) continue;
            if (t.isDead) continue;
            if (!VOID_ABYSS_MARK_AFFECT_PLAYERS && (t instanceof EntityPlayer)) continue;

            double dx = t.posX - center.posX;
            double dy = (t.posY + t.height * 0.5D) - (center.posY + center.height * 0.5D);
            double dz = t.posZ - center.posZ;

            double d2 = dx*dx + dy*dy + dz*dz;
            if (d2 > r2) continue;

            double dist = Math.sqrt(d2);

            float falloff = (float)(1.0D - (dist / (double)r));
            if (falloff < 0.25f) falloff = 0.25f;
            if (falloff > 1.0f) falloff = 1.0f;

            float hit = dmg * falloff;

            try {
                dealProcDamage(owner, t, hit, "abyssMarkAoE");
            } catch (Throwable ignored) {}

            if (kb > 0D){
                try {
                    double inv = (dist > 0.0001D) ? (1.0D / dist) : 1.0D;
                    double nx = dx * inv;
                    double nz = dz * inv;

                    double k = kb * 0.85D * (double)falloff;
                    double vy = 0.10D + (kb * 0.05D) * (double)falloff;

                    t.addVelocity(nx * k, vy, nz * k);
                    t.velocityChanged = true;
                } catch (Throwable ignored) {}
            }
        }
    }

    private static void tickVoidAbyssMark(EntityLivingBase ent, NBTTagCompound data, World w, long now){
        if (ent == null || data == null || w == null || w.isRemote) return;

        long expire = data.getLong(VAM_KEY_EXPIRE);
        if (expire <= 0L) return;

        boolean isAnim = false;
        try { isAnim = data.getBoolean(VAM_KEY_ANIM); } catch (Throwable ignored) {}

        // While marked: visible particles
        if (now < expire){
            if ((now & 1L) == 0L){
                spawnVoidGravityParticles(w,
                        ent.posX,
                        ent.posY + ent.height * 0.55,
                        ent.posZ,
                        isAnim ? 1.35f : 1.10f,
                        (int) (now & 0x7FFFFFFF),
                        isAnim);
            }
            return;
        }

        // Detonation time
        int ownerId = data.getInteger(VAM_KEY_OWNER);
        EntityPlayer owner = null;
        if (ownerId != 0){
            Entity o = w.getEntityByID(ownerId);
            if (o instanceof EntityPlayer){
                owner = (EntityPlayer) o;
            }
        }

        // Always clear the mark (even if owner is offline)
        clearVoidAbyssMark(data);

        if (owner != null && !ent.isDead){
            float dmg = computeVoidAbyssDetonationDamage(owner, isAnim);
            dmg = scaleForPvp(ent, dmg);

            if (dmg > 0f){
                try {
                    dealProcDamage(owner, ent, dmg, "abyssMarkDet");
                } catch (Throwable ignored) {}
            }

            // light knockback (optional)
            double kb = isAnim ? VOID_ABYSS_MARK_DETONATE_KB_ANIM : VOID_ABYSS_MARK_DETONATE_KB;
            if (kb > 0D){
                try {
                    double dx = ent.posX - owner.posX;
                    double dz = ent.posZ - owner.posZ;
                    double dist = Math.sqrt(dx*dx + dz*dz);
                    if (dist < 0.001D) dist = 0.001D;
                    double nx = dx / dist;
                    double nz = dz / dist;
                    ent.addVelocity(nx * kb, 0.10D + kb * 0.08D, nz * kb);
                    ent.velocityChanged = true;
                } catch (Throwable ignored) {}
            }
        }

        // Burst particles + sound on detonation (visible regardless of owner presence)
        spawnVoidGravityBurstParticles(w,
                ent.posX,
                ent.posY + ent.height * 0.55,
                ent.posZ,
                isAnim ? 2.15f : 1.75f,
                isAnim);

        try {
            w.playSoundEffect(ent.posX, ent.posY, ent.posZ, "random.explode", 0.50f, isAnim ? 1.15f : 1.05f);
            w.playSoundEffect(ent.posX, ent.posY, ent.posZ, "portal.travel", 0.45f, isAnim ? 0.75f : 0.90f);
        } catch (Throwable ignored) {}
    }

    private static void clearVoidAbyssMark(NBTTagCompound data){
        if (data == null) return;
        data.removeTag(VAM_KEY_OWNER);
        data.removeTag(VAM_KEY_STACKS);
        data.removeTag(VAM_KEY_EXPIRE);
        data.removeTag(VAM_KEY_ANIM);
    }

    private static void stampVoidHudMarks(EntityPlayer owner, ItemStack host, int stacks, int maxStacks, int targetId, long expireTick){
        if (host == null) return;

        NBTTagCompound tag = host.getTagCompound();
        if (tag == null) tag = new NBTTagCompound();

        tag.setInteger("HexVoidHudMarks", Math.max(0, stacks));
        tag.setInteger("HexVoidHudMarkMax", Math.max(0, maxStacks));
        tag.setInteger("HexVoidHudMarkTarget", targetId);
        tag.setLong("HexVoidHudMarkExpire", expireTick);

        host.setTagCompound(tag);

        // push changes to client ASAP in MP
        if (owner instanceof EntityPlayerMP){
            try {
                ((EntityPlayerMP) owner).inventoryContainer.detectAndSendChanges();
            } catch (Throwable ignored) {}
        }
    }



    private static void clearVoidEntropy(EntityLivingBase ent){
        if (ent == null) return;
        NBTTagCompound d = ent.getEntityData();
        if (d == null) return;
        d.removeTag(VE_KEY_END);
        d.removeTag(VE_KEY_NEXT);
        d.removeTag(VE_KEY_OWNER);
        d.removeTag(VE_KEY_DMG);
        d.removeTag(VE_KEY_INT);
        d.removeTag(VE_KEY_TOTAL);
        d.removeTag(VE_KEY_ANIM);
    }

    private static float computeVoidEntropyTickDamage(EntityPlayer p, boolean isAnim){
        if (p == null) return VOID_ENTROPY_BONUS_DAMAGE;
        double str = getStrengthEffective(p);
        float dmg = (float)(str * (double)VOID_ENTROPY_STRENGTH_SCALE) + VOID_ENTROPY_BONUS_DAMAGE;
        if (isAnim) dmg *= VOID_ENTROPY_ANIM_MULT;
        if (dmg < 0f) dmg = 0f;
        return dmg;
    }

    /** DBC Strength-aware stat read; falls back to vanilla attack damage if DBC attrs aren't present. */
    private static double getStrengthEffective(EntityPlayer p){
        if (p == null) return 0.0;
        // Prefer our provider if it exists (keeps your multi/form logic centralized)
        try {
            Class<?> c = Class.forName("com.example.examplemod.server.HexDBCProcDamageProvider");
            try {
                Method m = c.getMethod("getStrengthEffective", EntityPlayer.class);
                Object r = m.invoke(null, p);
                if (r instanceof Number){
                    double v = ((Number) r).doubleValue();
                    if (v > 0.0) return v;
                }
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}

        double v = 0.0;
        try {
            net.minecraft.entity.ai.attributes.IAttributeInstance inst =
                    p.getAttributeMap().getAttributeInstanceByName("dbc.Strength");
            if (inst != null) v = inst.getAttributeValue();
        } catch (Throwable ignored) {}

        // Apply STR multi when it looks meaningful
        try {
            if (v > 0.0){
                net.minecraft.entity.ai.attributes.IAttributeInstance mi =
                        p.getAttributeMap().getAttributeInstanceByName("dbc.Strength.Multi");
                if (mi != null){
                    double mul = mi.getAttributeValue();
                    if (mul > 1.05D && v < 50000D) v = v * mul;
                }
            }
        } catch (Throwable ignored) {}

        if (v > 0.0) return v;

        // Vanilla fallback
        try {
            net.minecraft.entity.ai.attributes.IAttributeInstance ad = p.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.attackDamage);
            if (ad != null) return ad.getAttributeValue();
        } catch (Throwable ignored) {}
        return 0.0;
    }

    /**
     * Heal DBC "Body" by an absolute amount using the existing percent-bridge when possible.
     * Falls back to vanilla heal if DBC bridge isn't available.
     */
    private static boolean dbcRestoreHealthAbs(EntityPlayer p, float abs){
        if (p == null) return false;
        if (abs <= 0f) return true;

        // Compute % from max BODY if present
        float max = Float.NaN;
        try {
            NBTTagCompound root = p.getEntityData();
            max = readFirstNumber(root, DBC_BODY_MAX_KEYS);
            if (!(max > 0f) && root != null && root.hasKey("PlayerPersisted", 10)){
                NBTTagCompound pp = root.getCompoundTag("PlayerPersisted");
                float m2 = readFirstNumber(pp, DBC_BODY_MAX_KEYS);
                if (m2 > 0f) max = m2;
            }
        } catch (Throwable ignored) {}

        if (max > 0f){
            float pct = abs / max;
            if (pct < 0f) pct = 0f;
            if (pct > 1f) pct = 1f;
            return dbcRestoreHealthPercent(p, pct);
        }

        // If we can't determine DBC max, try a small percent guess (won't be perfect)
        return false;
    }


    /** Adds to DBC "Body" directly via NBT (mirrors the Fractured-style damage bridge, but healing). */
    private static boolean dbcAddBodyAbsViaNbt(EntityLivingBase ent, float abs){
        if (ent == null) return false;
        if (abs <= 0f) return true;

        try {
            NBTTagCompound ed = ent.getEntityData();
            if (ed == null) return false;

            // Match the damage bridge selection logic:
            // Prefer PlayerPersisted if it already stores Body, otherwise use root entity data.
            NBTTagCompound store = null;
            boolean storeIsPersisted = false;

            if (ed.hasKey("PlayerPersisted", 10)){
                NBTTagCompound pp = ed.getCompoundTag("PlayerPersisted");
                if (pp != null && pp.hasKey("jrmcBdy", 99)){
                    store = pp;
                    storeIsPersisted = true;
                }
            }
            if (store == null && ed.hasKey("jrmcBdy", 99)){
                store = ed;
            }
            if (store == null) return false;

            // Numeric-safe read (type 99 == any numeric)
            double body = store.getDouble("jrmcBdy");
            if (Double.isNaN(body) || Double.isInfinite(body)) return false;

            // Optional clamp to max if present
            double max = Double.NaN;
            if (store.hasKey("jrmcBdyF", 99)){
                max = store.getDouble("jrmcBdyF");
            } else if (store.hasKey("jrmcBdyMax", 99)){
                max = store.getDouble("jrmcBdyMax");
            } else if (store.hasKey("jrmcBdyM", 99)){
                max = store.getDouble("jrmcBdyM");
            }

            double nb = body + (double) abs;
            if (nb < 0D) nb = 0D;
            if (max > 0D && nb > max) nb = max;
            if (nb > 2.147e9D) nb = 2.147e9D; // keep in int-ish range, DBC uses ints in many builds

            // Write as float + mark changed (DBC uses this to sync).
            store.setFloat("jrmcBdy", (float) nb);
            store.setBoolean("jrmcDamaged", true);

            // If we modified PlayerPersisted, ensure it stays attached.
            if (storeIsPersisted){
                ed.setTag("PlayerPersisted", store);
            }

            return true;
        } catch (Throwable ignored){
            return false;
        }
    }


    /** Pull 'to' toward a fixed point. (Void GW uses this; other procs use knockback helpers.) */
    private static void pullTowardPoint(EntityLivingBase to, double cx, double cy, double cz, double horiz, double yBoost){
        if (to == null) return;

        double dx = cx - to.posX;
        double dz = cz - to.posZ;
        double d2 = dx * dx + dz * dz;
        if (d2 < 1.0e-6) return;

        double inv = 1.0 / Math.sqrt(d2);
        dx *= inv;
        dz *= inv;

        try {
            to.addVelocity(dx * horiz, yBoost, dz * horiz);
            to.velocityChanged = true;
        } catch (Throwable ignored) {}
    }

    /**
     * Best-effort void type discovery:
     * - First scans the host item's NBT recursively for known keys
     * - Then tries to inspect actual socketed gem ItemStacks via reflection (if available)
     */
    private static String readVoidTypeFromHostItem(ItemStack host){
        if (host == null) return null;

        // 1) Deep scan host NBT (fast + works if socket system stores gem NBT inside host)
        try {
            NBTTagCompound tag = host.getTagCompound();
            if (tag != null){
                String v =
                        findStringDeep(tag, "HexVoidType",
                                findStringDeep(tag, "HexVoidOrbType",
                                        findStringDeep(tag, "VoidType",
                                                findStringDeep(tag, "VoidOrbType", null))));
                if (v != null && v.trim().length() > 0) return v;
            }
        } catch (Throwable ignored) {}

        // 2) Try to inspect socketed gem ItemStacks
        try {
            int filled = getSocketsFilled(host);
            for (int i = 0; i < filled; i++){
                Object out = null;
                try { out = M_getGemAt.invoke(null, host, Integer.valueOf(i)); } catch (Throwable ignored) {}

                if (out instanceof ItemStack){
                    ItemStack gem = (ItemStack) out;
                    String key = normalizeGemKeyForCompare(gem);
                    if (GEM_VOID_FLAT.equals(key) || GEM_VOID_ANIM.equals(key)){
                        NBTTagCompound gt = gem.getTagCompound();
                        if (gt != null){
                            String v = gt.getString("HexVoidType");
                            if (v != null && v.trim().length() > 0) return v;
                            v = gt.getString("HexVoidOrbType");
                            if (v != null && v.trim().length() > 0) return v;
                            v = gt.getString("VoidType");
                            if (v != null && v.trim().length() > 0) return v;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private static String normalizeGemKeyForCompare(ItemStack gem){
        if (gem == null) return "";
        // In many builds, the gem key is stored in gem.getTagCompound().getString("HexGemKey").
        // If not present, this returns empty and we can't match; host deep-scan usually covers that.
        try {
            NBTTagCompound t = gem.getTagCompound();
            if (t != null){
                String k = t.getString("HexGemKey");
                if (k != null) return k.trim();
            }
        } catch (Throwable ignored) {}
        return "";
    }

    private static String findStringDeep(NBTTagCompound root, String key, String fallback){
        if (root == null || key == null) return fallback;

        try {
            if (root.hasKey(key)){
                String v = root.getString(key);
                if (v != null && v.trim().length() > 0) return v;
            }

            // recurse into compounds/lists
            @SuppressWarnings("unchecked")
            java.util.Set<String> keys = root.func_150296_c();
            for (String k : keys){
                NBTBase b = root.getTag(k);
                if (b == null) continue;

                byte id = b.getId();
                if (id == 10){ // compound
                    String v = findStringDeep((NBTTagCompound) b, key, null);
                    if (v != null) return v;
                } else if (id == 9){ // list
                    net.minecraft.nbt.NBTTagList list = (net.minecraft.nbt.NBTTagList) b;
                    int n = list.tagCount();
                    for (int i = 0; i < n; i++){
                        NBTBase li = list.getCompoundTagAt(i);
                        if (li instanceof NBTTagCompound){
                            String v = findStringDeep((NBTTagCompound) li, key, null);
                            if (v != null) return v;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        return fallback;
    }



    /**
     * Best-effort i-frame reset for cases where we don't have an attacker context
     * (e.g., DoT ticks using DamageSource.onFire).
     */
    private static void tryResetIFrames(EntityLivingBase target){
        if (target == null) return;

        // Deobf (dev) fields
        try { target.hurtResistantTime = 0; } catch (Throwable ignored) {}
        try { target.hurtTime = 0; } catch (Throwable ignored) {}

        // Obf/SRG reflection fallbacks (safe no-op if names differ)
        try {
            Field f = EntityLivingBase.class.getDeclaredField("field_70771_an"); // hurtResistantTime
            f.setAccessible(true);
            f.setInt(target, 0);
        } catch (Throwable ignored) {}

        try {
            Field f = EntityLivingBase.class.getDeclaredField("field_70737_aN"); // hurtTime
            f.setAccessible(true);
            f.setInt(target, 0);
        } catch (Throwable ignored) {}
    }

    // Back-compat: older drafts used this name.
    private static void tryResetFrames(EntityLivingBase target){
        tryResetIFrames(target);
    }

    private static void dealProcDamageNullable(EntityPlayer attacker, EntityLivingBase target, float amount){
        if (target == null || amount <= 0f) return;

        if (attacker != null){
            dealProcDamage(attacker, target, amount, "fireDot");
            return;
        }

        // Fallback (no attacker present)
        tryResetIFrames(target);
        try {
            target.attackEntityFrom(DamageSource.onFire, amount);
        } catch (Throwable ignored) {}
    }

    private static void spawnFireHands(World w, EntityPlayer p, boolean anim, int particles){
        if (!(w instanceof WorldServer) || p == null || particles <= 0) return;
        WorldServer ws = (WorldServer) w;

        double y = p.posY + p.height * 0.75;
        double yaw = Math.toRadians(p.rotationYaw);

        // Offsets to approximate left/right hands
        double side = 0.35;
        double fwd  = 0.25;

        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);

        double lx = p.posX + (-sin * fwd) + (cos * side);
        double lz = p.posZ + ( cos * fwd) + (sin * side);

        double rx = p.posX + (-sin * fwd) - (cos * side);
        double rz = p.posZ + ( cos * fwd) - (sin * side);

        // flame
        ws.func_147487_a("flame", lx, y, lz, particles, 0.05, 0.03, 0.05, 0.01);
        ws.func_147487_a("flame", rx, y, rz, particles, 0.05, 0.03, 0.05, 0.01);

        // small smoke (lighter)
        int smoke = Math.max(1, particles / 3);
        ws.func_147487_a("smoke", lx, y, lz, smoke, 0.05, 0.02, 0.05, 0.01);
        ws.func_147487_a("smoke", rx, y, rz, smoke, 0.05, 0.02, 0.05, 0.01);

        if (anim){
            // tiny extra sparks
            int lava = Math.max(1, particles / 4);
            ws.func_147487_a("lava", lx, y, lz, lava, 0.02, 0.02, 0.02, 0.01);
            ws.func_147487_a("lava", rx, y, rz, lava, 0.02, 0.02, 0.02, 0.01);
        }
    }

    private static void spawnFireImpact(World w, EntityLivingBase t, boolean anim){
        if (!(w instanceof WorldServer) || t == null) return;
        WorldServer ws = (WorldServer) w;

        double x = t.posX;
        double y = t.posY + (t.height * 0.55);
        double z = t.posZ;

        int flame = anim ? 18 : 12;
        ws.func_147487_a("flame", x, y, z, flame, 0.30, 0.22, 0.30, 0.01);

        int smoke = anim ? 10 : 6;
        ws.func_147487_a("smoke", x, y, z, smoke, 0.25, 0.18, 0.25, 0.01);
    }

    private static void spawnFireDotTick(World w, EntityLivingBase t, boolean anim){
        if (!(w instanceof WorldServer) || t == null) return;
        WorldServer ws = (WorldServer) w;

        double x = t.posX;
        double y = t.posY + (t.height * 0.55);
        double z = t.posZ;

        int flame = anim ? 8 : 6;
        ws.func_147487_a("flame", x, y, z, flame, 0.20, 0.18, 0.20, 0.01);

        int smoke = anim ? 5 : 3;
        ws.func_147487_a("smoke", x, y, z, smoke, 0.18, 0.16, 0.18, 0.01);
    }

    private static void spawnFireExplosion(World w, double x, double y, double z, boolean anim){
        if (!(w instanceof WorldServer)) return;
        WorldServer ws = (WorldServer) w;

        ws.func_147487_a("hugeexplosion", x, y, z, 1, 0.0, 0.0, 0.0, 0.0);

        int flame = anim ? 120 : 90;
        ws.func_147487_a("flame", x, y, z, flame, 0.85, 0.65, 0.85, 0.01);

        int smoke = anim ? 70 : 50;
        ws.func_147487_a("smoke", x, y, z, smoke, 0.80, 0.55, 0.80, 0.01);

        int lava = anim ? 30 : 20;
        ws.func_147487_a("lava", x, y, z, lava, 0.50, 0.35, 0.50, 0.01);
    }


    private static void maybeStartSwirl(EntityPlayer attacker, EntityLivingBase target, float baseDamage, boolean isAnim){
        if (!ENERGIZED_SWIRL_ENABLED) return;
        if (attacker == null || target == null) return;

        boolean canMove = true;
        if (target instanceof EntityPlayer && !ENERGIZED_SWIRL_AFFECT_PLAYERS) canMove = false;

        // Variant: Rainbow Fist (huge punch + massive rainbow blast + beams)
        if (ENERGIZED_FIST_ENABLED){
            if ((target instanceof EntityPlayer) && !ENERGIZED_FIST_AFFECT_PLAYERS){
                // skip targeting players
            } else {
                double chance = isAnim ? ENERGIZED_FIST_CHANCE_ANIM : ENERGIZED_FIST_CHANCE_FLAT;
                int cd = isAnim ? ENERGIZED_ANIM_FIST_COOLDOWN_TICKS : ENERGIZED_FIST_COOLDOWN_TICKS;

                if (chance > 0.0 && roll(attacker, chance)
                        && cooldownReady(attacker, isAnim ? "HexOrbCD_FistAnim" : "HexOrbCD_FistFlat", cd)){
                    doRainbowFist(attacker, target, baseDamage, isAnim);
                    return;
                }
            }
        }

// Variant: Rainbow Rush (pushback + rapid explosions + blink finisher)
        if (ENERGIZED_RUSH_ENABLED){
            if ((target instanceof EntityPlayer) && !ENERGIZED_RUSH_AFFECT_PLAYERS){
                // skip targeting players
            } else {
                double chance = isAnim ? ENERGIZED_RUSH_CHANCE_ANIM : ENERGIZED_RUSH_CHANCE_FLAT;
                int cd = isAnim ? ENERGIZED_ANIM_RUSH_COOLDOWN_TICKS : ENERGIZED_RUSH_COOLDOWN_TICKS;

                if (chance > 0.0 && rollChance(attacker, chance)
                        && cooldownReady(attacker, isAnim ? "HexOrbCD_RushAnim" : "HexOrbCD_RushFlat", cd)){
                    if (startRainbowRush(attacker, target, baseDamage, isAnim)){
                        return;
                    }
                }
            }
        }

        // Variant: horizontal push-swirl + big blast (NO DoT)
        if (ENERGIZED_PUSH_BLAST_ENABLED){
            double chance = isAnim ? ENERGIZED_PUSH_BLAST_CHANCE_ANIM : ENERGIZED_PUSH_BLAST_CHANCE_FLAT;
            if (chance > 0.0 && roll(attacker, chance)){
                int   ticks       = isAnim ? ENERGIZED_ANIM_PUSH_SWIRL_TICKS : ENERGIZED_PUSH_SWIRL_TICKS;
                float swirlRadius = isAnim ? ENERGIZED_ANIM_PUSH_SWIRL_RADIUS : ENERGIZED_PUSH_SWIRL_RADIUS;
                float radPerTick  = isAnim ? ENERGIZED_ANIM_PUSH_SWIRL_RAD_PER_TICK : ENERGIZED_PUSH_SWIRL_RAD_PER_TICK;
                int   tickParts   = isAnim ? ENERGIZED_ANIM_PUSH_SWIRL_TICK_PARTICLES : ENERGIZED_PUSH_SWIRL_TICK_PARTICLES;

                float exRadius    = isAnim ? ENERGIZED_ANIM_PUSH_BLAST_RADIUS : ENERGIZED_PUSH_BLAST_RADIUS;
                float exScale     = isAnim ? ENERGIZED_ANIM_PUSH_BLAST_DAMAGE_SCALE : ENERGIZED_PUSH_BLAST_DAMAGE_SCALE;
                float exBonus     = isAnim ? ENERGIZED_ANIM_PUSH_BLAST_BONUS_DAMAGE : ENERGIZED_PUSH_BLAST_BONUS_DAMAGE;

                float exBase = procBaseDamage(attacker, target, ProcStage.PUSH_BLAST, baseDamage, isAnim);
                float exDmg  = Math.max(1.0f, (exBase * exScale + exBonus) * ENERGIZED_GLOBAL_DAMAGE_MULT);

                // liftTotal = 0 for horizontal push swirl, dotPerApp = 0 (no DoT)
                startSwirl(attacker, target, ticks, canMove, exDmg, exRadius, 0.0f, 999999,
                        swirlRadius, 0.0f, radPerTick, tickParts);

                float pushDist = isAnim ? ENERGIZED_ANIM_PUSH_SWIRL_PUSH_DIST : ENERGIZED_PUSH_SWIRL_PUSH_DIST;
                markPushSwirl(attacker, target, pushDist);
                return;
            }
        }

        // Normal swirl + (optional) DoT + explode
        int   ticks       = isAnim ? ENERGIZED_ANIM_SWIRL_TICKS : ENERGIZED_SWIRL_TICKS;
        float swirlRadius = isAnim ? ENERGIZED_ANIM_SWIRL_RADIUS : ENERGIZED_SWIRL_RADIUS;
        float liftTotal   = isAnim ? ENERGIZED_ANIM_SWIRL_LIFT_TOTAL : ENERGIZED_SWIRL_LIFT_TOTAL;
        float radPerTick  = isAnim ? ENERGIZED_ANIM_SWIRL_RAD_PER_TICK : ENERGIZED_SWIRL_RAD_PER_TICK;
        int   tickParts   = isAnim ? ENERGIZED_ANIM_SWIRL_TICK_PARTICLES : ENERGIZED_SWIRL_TICK_PARTICLES;

        float exRadius    = isAnim ? ENERGIZED_ANIM_SWIRL_EXPLODE_RADIUS : ENERGIZED_SWIRL_EXPLODE_RADIUS;
        float exScale     = isAnim ? ENERGIZED_ANIM_SWIRL_EXPLODE_DAMAGE_SCALE : ENERGIZED_SWIRL_EXPLODE_DAMAGE_SCALE;
        float exBonus     = isAnim ? ENERGIZED_ANIM_SWIRL_EXPLODE_BONUS_DAMAGE : ENERGIZED_SWIRL_EXPLODE_BONUS_DAMAGE;

        int   dotInt      = isAnim ? ENERGIZED_ANIM_SWIRL_DOT_INTERVAL_TICKS : ENERGIZED_SWIRL_DOT_INTERVAL_TICKS;
        float dotScale    = isAnim ? ENERGIZED_ANIM_SWIRL_DOT_DAMAGE_SCALE : ENERGIZED_SWIRL_DOT_DAMAGE_SCALE;
        float dotBonus    = isAnim ? ENERGIZED_ANIM_SWIRL_DOT_BONUS_DAMAGE  : ENERGIZED_SWIRL_DOT_BONUS_DAMAGE;

        float exBase = procBaseDamage(attacker, target, ProcStage.SWIRL_EXPLOSION, baseDamage, isAnim);
        float dotBase = procBaseDamage(attacker, target, ProcStage.SWIRL_DOT, baseDamage, isAnim);

        float exDmg = Math.max(1.0f, (exBase * exScale + exBonus) * ENERGIZED_GLOBAL_DAMAGE_MULT);

        float dotPerApp = 0.0f;
        if (ENERGIZED_SWIRL_DOT_ENABLED){
            dotPerApp = Math.max(0.0f, (dotBase * dotScale + dotBonus) * ENERGIZED_GLOBAL_DAMAGE_MULT);
        }

        startSwirl(attacker, target, ticks, canMove, exDmg, exRadius, dotPerApp, Math.max(1, dotInt),
                swirlRadius, liftTotal, radPerTick, tickParts);
    }


    private static float scaleForPvp(EntityLivingBase t, float dmg){
        if (t instanceof EntityPlayer) return dmg * DAMAGE_VS_PLAYERS_SCALE;
        return dmg;
    }

    /**
     * THE spot that applies proc damage.
     * Fixes "only hits CNPCs" by handling i-frames.
     */
    private static void dealProcDamage(EntityPlayer attacker, EntityLivingBase target, float amount){
        dealProcDamage(attacker, target, amount, null);
    }

    private static void dealProcDamage(EntityPlayer attacker, EntityLivingBase target, float amount, String reason){
        if (attacker == null || target == null) return;

        float in = amount;
        float dmg = amount;

        int preHRT = target.hurtResistantTime;
        int preHT  = target.hurtTime;
        float hp0  = safeHealth(target);

        // Dev-only proc boost
        if (DEV_ENV && DEV_PROC_DAMAGE_BOOST){
            dmg = dmg * DEV_PROC_DAMAGE_MULT + DEV_PROC_DAMAGE_ADD;
        }

        // sanity clamp
        if (Float.isNaN(dmg) || Float.isInfinite(dmg)) dmg = 1.0f;
        if (dmg < 0f) dmg = 0f;

        // i-frames bypass (the big fix)
        if (target instanceof EntityPlayer) {
            if (PROC_RESET_IFRAMES_PLAYERS) {
                target.hurtResistantTime = 0;
                target.hurtTime = 0;
            }
        } else {
            if (PROC_RESET_IFRAMES_NONPLAYERS) {
                target.hurtResistantTime = 0;
                target.hurtTime = 0;
            }
        }

        int postResetHRT = target.hurtResistantTime;
        int postResetHT  = target.hurtTime;

        String r = (reason == null ? "proc" : reason);

        // DBC hook point
        DamageApplier applier = DAMAGE_APPLIER;
        if (applier != null){
            boolean ok = true;
            try {
                applier.deal(attacker, target, dmg);
            } catch (Throwable ignored) {
                ok = false;
            }

            float hp1 = safeHealth(target);

            if (DEBUG_DAMAGE){
                debugDamage(attacker,
                        "[HexOrb][DMG/APPLIER] " + r +
                                " tgt=" + entLabel(target) +
                                " in=" + in + " final=" + dmg +
                                " iFrames(" + preHRT + "," + preHT + ")->(" + postResetHRT + "," + postResetHT + ")" +
                                " hp(" + hp0 + "->" + hp1 + ")" +
                                " ok=" + ok
                );
            }

            if (ok) return;
            // fall back to vanilla if applier failed
        }

        boolean applied = false;
        try {
            applied = target.attackEntityFrom(hexOrbDamageSource(attacker), dmg);
        } catch (Throwable ignored) {}

        // Optional last-resort fallback (non-players only)
        if (!applied && PROC_FORCE_SETHEALTH_FALLBACK_NONPLAYERS && !(target instanceof EntityPlayer)) {
            try {
                float hp = target.getHealth();
                float nh = hp - dmg;
                if (nh < 0f) nh = 0f;
                target.setHealth(nh);
            } catch (Throwable ignored) {}
        }

        float hp1 = safeHealth(target);

        if (DEBUG_DAMAGE){
            debugDamage(attacker,
                    "[HexOrb][DMG] " + r +
                            " tgt=" + entLabel(target) +
                            " in=" + in + " final=" + dmg +
                            " iFrames(" + preHRT + "," + preHT + ")->(" + postResetHRT + "," + postResetHT + ")" +
                            " applied=" + applied +
                            " hp(" + hp0 + "->" + hp1 + ")"
            );

            if (DEBUG_DAMAGE_VERBOSE){
                debugDamage(attacker,
                        "  type=" + (target instanceof EntityPlayer ? "PLAYER" : "MOB") +
                                " src=hexorb"
                );
            }
        }
    }

    private static DamageSource hexOrbDamageSource(EntityPlayer attacker){
        // keep unique type to prevent recursion in isPlayerMeleeDamage()
        return new EntityDamageSource("hexorb", attacker);
    }

    // -------------------------------------------------------------
    // SWIRL UPDATE (server tick while a target is marked)
    // -------------------------------------------------------------
    @SubscribeEvent
    public void onLivingUpdate(LivingUpdateEvent e){
        if (e == null || e.entityLiving == null) return;

        EntityLivingBase ent = e.entityLiving;
        World w = ent.worldObj;
        if (w == null || w.isRemote) return;

        NBTTagCompound data = ent.getEntityData();

        // FRACTURED: advance flying blast (server-side)
        if (ent instanceof net.minecraft.entity.player.EntityPlayerMP) {
            tickFracturedFlyingBlast((net.minecraft.entity.player.EntityPlayerMP) ent);
        }
        if (data == null) return;

        long now = serverNow(w);

        // Heal buff update (independent of swirl; works in any dimension)
        if (ent instanceof EntityPlayer){
            tickHealBuff((EntityPlayer) ent, data, w, now);
            tickFirePunchBuff((EntityPlayer) ent, data, w, now);
            tickVoidGravityWell((EntityPlayer) ent, data, w, now);
        }

        // Rainbow Rush update (independent of swirl)
        tickRainbowRush(ent, data, w, now);
        tickFireDot(ent, data, w, now);
        tickVoidEntropy(ent, data, w, now);
        tickVoidAbyssMark(ent, data, w, now);


        if (ent instanceof EntityPlayer){
            EntityPlayer p = (EntityPlayer) ent;
            tickVoidNullShellCharge(p, w, data, now);
            tickVoidNullShellDefenseBuff(p, w, data, now);
            tickVoidNullShellPushCharge(w, p, data, now);
            // Step 1: Null Shell trail tick
            maybeTickNullShellTrailsOncePerWorld(w);
        }
        long end = data.getLong(SW_KEY_END);
        if (end <= 0L) return;
        if (now >= end){
            doSwirlExplosion(ent, true);
            clearSwirl(ent);
            return;
        }

        long start = data.getLong(SW_KEY_START);
        if (start <= 0L) start = now;

        long durL = end - start;
        if (durL <= 0L) durL = ENERGIZED_SWIRL_TICKS;
        if (durL > 1200L) durL = 1200L;
        int duration = (int) Math.max(1L, durL);

        float liftTotal = data.getFloat(SW_KEY_LIFT);
        if (liftTotal <= 0f) liftTotal = ENERGIZED_SWIRL_LIFT_TOTAL;

        float swirlRadius = data.getFloat(SW_KEY_RAD);
        if (swirlRadius <= 0f) swirlRadius = ENERGIZED_SWIRL_RADIUS;

        float radPerTick = data.getFloat(SW_KEY_SPIN);
        if (radPerTick == 0f) radPerTick = ENERGIZED_SWIRL_RAD_PER_TICK;

        int tickParticles = data.getInteger(SW_KEY_TICKP);
        if (tickParticles <= 0) tickParticles = ENERGIZED_SWIRL_TICK_PARTICLES;

        double t = (double) (now - start);
        double prog = Math.min(1.0, Math.max(0.0, t / (double) duration));

        double cx = data.getDouble(SW_KEY_CX);
        double cz = data.getDouble(SW_KEY_CZ);
        boolean doMove = !(Double.isNaN(cx) || Double.isNaN(cz));

        double baseY = data.getDouble(SW_KEY_BASEY);
        double y = baseY + prog * (double) liftTotal;

        if (doMove){
            double ang = t * (double) radPerTick;
            double r = (double) swirlRadius;

            int mode = data.getInteger(SW_KEY_MODE);
            double x;
            double z;
            if (mode == 1){
                double dirX = data.getDouble(SW_KEY_DIRX);
                double dirZ = data.getDouble(SW_KEY_DIRZ);
                float push = data.getFloat(SW_KEY_PUSH);
                double mcx = cx + dirX * prog * (double) push;
                double mcz = cz + dirZ * prog * (double) push;
                x = mcx + Math.cos(ang) * r;
                z = mcz + Math.sin(ang) * r;
            } else {
                x = cx + Math.cos(ang) * r;
                z = cz + Math.sin(ang) * r;
            }

            ent.fallDistance = 0.0f;
            ent.motionX = 0;
            ent.motionY = 0;
            ent.motionZ = 0;

            if (ent instanceof EntityPlayerMP){
                ((EntityPlayerMP) ent).setPositionAndUpdate(x, y, z);
            } else {
                ent.setPosition(x, y, z);
            }

            ent.velocityChanged = true;
        }

        // DoT
        if (ENERGIZED_SWIRL_DOT_ENABLED){
            if (!(ent instanceof EntityPlayer) || ENERGIZED_SWIRL_DOT_AFFECT_PLAYERS){
                int interval = data.getInteger(SW_KEY_DOTINT);
                if (interval <= 0) interval = Math.max(1, ENERGIZED_SWIRL_DOT_INTERVAL_TICKS);

                float dot = data.getFloat(SW_KEY_DOT);
                if (dot > 0.0f){
                    long elapsed = now - start;
                    if (elapsed % interval == 0){
                        float finalDmg = dot;
                        if (ent instanceof EntityPlayer) finalDmg *= DAMAGE_VS_PLAYERS_SCALE;

                        if (ENERGIZED_SWIRL_DOT_RESET_IFRAMES){
                            ent.hurtResistantTime = 0;
                            ent.hurtTime = 0;
                        }

                        int ownerId = data.getInteger(SW_KEY_OWNER);
                        Entity owner = w.getEntityByID(ownerId);
                        EntityPlayer attacker = (owner instanceof EntityPlayer) ? (EntityPlayer) owner : null;

                        if (attacker != null){
                            dealProcDamage(attacker, ent, finalDmg, "swirlDOT");
                        } else {
                            ent.attackEntityFrom(DamageSource.magic, finalDmg);
                        }
                    }
                }
            }
        }

        playRainbowSwirlTick(w, ent.posX, ent.posY + ent.height * 0.55, ent.posZ, tickParticles);

        // Push-blast windup: expanding rainbow sphere around attacker while the target is being shoved
        int vfxMode = data.getInteger(SW_KEY_MODE);
        if (vfxMode == 1 && ENERGIZED_PUSH_CHARGE_ENABLED){
            int ownerIdVfx = data.getInteger(SW_KEY_OWNER);
            Entity ownerEnt = w.getEntityByID(ownerIdVfx);

            if (ownerEnt != null){
                boolean animVfx = duration >= ENERGIZED_ANIM_PUSH_SWIRL_TICKS;

                int pCount = animVfx ? ENERGIZED_ANIM_PUSH_CHARGE_PARTICLES : ENERGIZED_PUSH_CHARGE_PARTICLES;
                float rMax = animVfx ? ENERGIZED_ANIM_PUSH_CHARGE_RADIUS_MAX : ENERGIZED_PUSH_CHARGE_RADIUS_MAX;

                float radius = ENERGIZED_PUSH_CHARGE_RADIUS_MIN + (rMax - ENERGIZED_PUSH_CHARGE_RADIUS_MIN) * (float) prog;
                playRainbowChargeSphereTick(w, ownerEnt.posX, ownerEnt.posY + ownerEnt.height * 0.60, ownerEnt.posZ, radius, pCount);
            }
        }

    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent e){
        if (e == null || e.entityLiving == null) return;

        EntityLivingBase ent = e.entityLiving;
        World w = ent.worldObj;
        if (w == null || w.isRemote) return;

        NBTTagCompound data = ent.getEntityData();

        // FRACTURED: advance flying blast (server-side)
        if (ent instanceof net.minecraft.entity.player.EntityPlayerMP) {
            tickFracturedFlyingBlast((net.minecraft.entity.player.EntityPlayerMP) ent);
        }
        if (data == null) return;

        // If a rush is active, just clear it (no extra explosion on death)
        if (data.getLong(RUSH_KEY_END) > 0L){
            clearRush(ent);
        }

        // If a Fire DoT is active, force its explosion now (so kills still pop)
        if (data.getLong(FD_KEY_END) > 0L){
            tickFireDot(ent, data, w, data.getLong(FD_KEY_END));
        }

        long end = data.getLong(SW_KEY_END);
        if (end <= 0L) return;

        doSwirlExplosion(ent, false);
        clearSwirl(ent);
    }

    private static void startSwirl(EntityPlayer attacker, EntityLivingBase target, int ticks, boolean allowMovement,
                                   float explodeDamage, float explodeRadius, float dotPerApp, int dotIntervalTicks,
                                   float swirlRadius, float liftTotal, float radPerTick, int tickParticles){
        if (attacker == null || target == null) return;
        World w = target.worldObj;
        if (w == null || w.isRemote) return;

        NBTTagCompound data = target.getEntityData();
        if (data == null) return;

        long now = serverNow(w);

        long existingEnd = data.getLong(SW_KEY_END);
        if (existingEnd > now) return;

        data.setLong(SW_KEY_START, now);
        data.setLong(SW_KEY_END, now + Math.max(1, ticks));

        if (allowMovement){
            data.setDouble(SW_KEY_CX, target.posX);
            data.setDouble(SW_KEY_CZ, target.posZ);
        } else {
            data.setDouble(SW_KEY_CX, Double.NaN);
            data.setDouble(SW_KEY_CZ, Double.NaN);
        }

        data.setDouble(SW_KEY_BASEY, target.posY);
        data.setInteger(SW_KEY_OWNER, attacker.getEntityId());
        data.setFloat(SW_KEY_EXDMG, explodeDamage);
        data.setFloat(SW_KEY_EXRAD, explodeRadius);
        data.setFloat(SW_KEY_DOT, dotPerApp);
        data.setInteger(SW_KEY_DOTINT, Math.max(1, dotIntervalTicks));
        data.setFloat(SW_KEY_RAD, swirlRadius);
        data.setFloat(SW_KEY_LIFT, liftTotal);
        data.setFloat(SW_KEY_SPIN, radPerTick);
        data.setInteger(SW_KEY_TICKP, Math.max(0, tickParticles));
        // default swirl behavior
        data.setInteger(SW_KEY_MODE, 0);
        data.removeTag(SW_KEY_DIRX);
        data.removeTag(SW_KEY_DIRZ);
        data.removeTag(SW_KEY_PUSH);

        playRainbowSpark(w, target.posX, target.posY + 1.0, target.posZ);
        w.playSoundAtEntity(target, "random.orb", 0.55f, 1.10f);
    }

    private static void markPushSwirl(EntityPlayer attacker, EntityLivingBase target, float pushDist){
        if (attacker == null || target == null) return;
        NBTTagCompound data = target.getEntityData();
        if (data == null) return;

        double dx = target.posX - attacker.posX;
        double dz = target.posZ - attacker.posZ;
        double d2 = dx * dx + dz * dz;
        if (d2 < 1.0e-6) {
            // fallback: push in the attacker's facing direction (roughly)
            dx = -Math.sin(Math.toRadians(attacker.rotationYaw));
            dz =  Math.cos(Math.toRadians(attacker.rotationYaw));
            d2 = dx * dx + dz * dz;
        }

        double inv = 1.0 / Math.sqrt(d2);
        dx *= inv;
        dz *= inv;

        data.setInteger(SW_KEY_MODE, 1);
        data.setDouble(SW_KEY_DIRX, dx);
        data.setDouble(SW_KEY_DIRZ, dz);
        data.setFloat(SW_KEY_PUSH, Math.max(0.0f, pushDist));
    }



    // -------------------------------------------------------------
    // RAINBOW RUSH (sequence proc)
    // -------------------------------------------------------------

    /** Returns true if the rush was started. */

    // -------------------------------------------------------------
    // Variant: Rainbow Fist (instant mega punch)
    // -------------------------------------------------------------
    private static void doRainbowFist(EntityPlayer attacker, EntityLivingBase primary, float baseDamage, boolean isAnim){
        if (attacker == null || primary == null) return;
        World w = primary.worldObj;
        if (w == null || w.isRemote) return;

        // Blink in for the punch (optional)
        if (ENERGIZED_FIST_BLINK_TO_TARGET && attacker instanceof EntityPlayerMP){
            tryBlinkNearTarget((EntityPlayerMP) attacker, primary, ENERGIZED_FIST_BLINK_DISTANCE, ENERGIZED_FIST_BLINK_TRIES);
        }

        // Swing for feedback
        try { attacker.swingItem(); } catch (Throwable ignored) {}

        double ix = primary.posX;
        double iy = primary.posY + primary.height * 0.50;
        double iz = primary.posZ;

        // VFX: huge blast + beams
        playRainbowFistMegaExplosion(w, attacker, ix, iy, iz);

        // Sound
        try { w.playSoundAtEntity(primary, "fireworks.largeBlast", 0.95f, isAnim ? 1.18f : 1.08f); } catch (Throwable ignored) {
            try { w.playSoundAtEntity(primary, "random.explode", 0.90f, isAnim ? 1.10f : 1.00f); } catch (Throwable ignored2) {}
        }

        float base = procBaseDamage(attacker, primary, ProcStage.RAINBOW_FIST, baseDamage, isAnim);

        float scale = isAnim ? ENERGIZED_ANIM_FIST_DAMAGE_SCALE : ENERGIZED_FIST_DAMAGE_SCALE;
        float bonus = isAnim ? ENERGIZED_ANIM_FIST_BONUS_DAMAGE : ENERGIZED_FIST_BONUS_DAMAGE;

        float dmg = Math.max(1.0f, (base * scale + bonus) * ENERGIZED_GLOBAL_DAMAGE_MULT);

        float rad = isAnim ? ENERGIZED_ANIM_FIST_AOE_RADIUS : ENERGIZED_FIST_AOE_RADIUS;
        int maxHits = isAnim ? ENERGIZED_ANIM_FIST_MAX_HITS : ENERGIZED_FIST_MAX_HITS;

        // Knockback scaling
        double kbH = KB_H * ENERGIZED_FIST_KB_H_MULT;
        double kbY = KB_Y * ENERGIZED_FIST_KB_Y_MULT + ENERGIZED_FIST_KB_Y_ADD;

        if (rad <= 0.05f){
            float finalDmg = scaleForPvp(primary, dmg);
            dealProcDamage(attacker, primary, finalDmg, "fist");
            applyKnockbackFrom(attacker, primary, kbH, kbY);
            return;
        }

        AxisAlignedBB box = primary.boundingBox.expand(rad, 1.8, rad);

        @SuppressWarnings("unchecked")
        List<EntityLivingBase> list = w.getEntitiesWithinAABB(EntityLivingBase.class, box);

        int hit = 0;
        for (EntityLivingBase t : list){
            if (t == null) continue;
            if (t == attacker) continue;
            if (t.isDead) continue;

            // Optional: avoid hitting players at all when disabled
            if (!ENERGIZED_FIST_AFFECT_PLAYERS && (t instanceof EntityPlayer)) continue;

            float finalDmg = scaleForPvp(t, dmg);
            dealProcDamage(attacker, t, finalDmg, "fistAoE");
            applyKnockbackFrom(attacker, t, kbH, kbY);

            hit++;
            if (hit >= maxHits) break;
        }
    }

    private static boolean startRainbowRush(EntityPlayer attacker, EntityLivingBase target, float baseDamage, boolean isAnim){
        if (attacker == null || target == null) return false;
        World w = target.worldObj;
        if (w == null || w.isRemote) return false;

        NBTTagCompound data = target.getEntityData();
        if (data == null) return false;

        long now = serverNow(w);

        // Don't overlap with an existing swirl or rush
        long swirlEnd = data.getLong(SW_KEY_END);
        if (swirlEnd > now) return false;

        long rushEnd = data.getLong(RUSH_KEY_END);
        if (rushEnd > now) return false;

        int shots = isAnim ? ENERGIZED_ANIM_RUSH_SHOTS : ENERGIZED_RUSH_SHOTS;
        shots = Math.max(1, shots);

        int interval = Math.max(1, ENERGIZED_RUSH_SHOT_INTERVAL_TICKS);
        int fdelay   = Math.max(0, ENERGIZED_RUSH_FINISH_DELAY_TICKS);

        float shotScale = isAnim ? ENERGIZED_ANIM_RUSH_SHOT_DAMAGE_SCALE : ENERGIZED_RUSH_SHOT_DAMAGE_SCALE;
        float shotBonus = isAnim ? ENERGIZED_ANIM_RUSH_SHOT_BONUS_DAMAGE : ENERGIZED_RUSH_SHOT_BONUS_DAMAGE;

        float finScale  = isAnim ? ENERGIZED_ANIM_RUSH_FINISH_DAMAGE_SCALE : ENERGIZED_RUSH_FINISH_DAMAGE_SCALE;
        float finBonus  = isAnim ? ENERGIZED_ANIM_RUSH_FINISH_BONUS_DAMAGE : ENERGIZED_RUSH_FINISH_BONUS_DAMAGE;

        float shotBase  = procBaseDamage(attacker, target, ProcStage.RUSH_SHOT, baseDamage, isAnim);
        float finalBase = procBaseDamage(attacker, target, ProcStage.RUSH_FINAL, baseDamage, isAnim);

        float shotDmg  = (shotBase  * shotScale + shotBonus) * ENERGIZED_GLOBAL_DAMAGE_MULT;
        float finalDmg = (finalBase * finScale  + finBonus)  * ENERGIZED_GLOBAL_DAMAGE_MULT;

        float shotRad  = isAnim ? ENERGIZED_ANIM_RUSH_SHOT_RADIUS : ENERGIZED_RUSH_SHOT_RADIUS;
        float finalRad = isAnim ? ENERGIZED_ANIM_RUSH_FINISH_RADIUS : ENERGIZED_RUSH_FINISH_RADIUS;

        // Store sequence data on TARGET
        data.setLong(RUSH_KEY_END, now + (long)shots * interval + fdelay + 60L); // safety end
        data.setLong(RUSH_KEY_NEXT, now); // first shot ASAP
        data.setInteger(RUSH_KEY_SHOTS, shots);
        data.setInteger(RUSH_KEY_TOTAL, shots);
        data.setInteger(RUSH_KEY_INT, interval);
        data.setInteger(RUSH_KEY_FDELAY, fdelay);
        data.setFloat(RUSH_KEY_SHOTDMG, Math.max(0f, shotDmg));
        data.setFloat(RUSH_KEY_FINALDMG, Math.max(0f, finalDmg));
        data.setFloat(RUSH_KEY_SHOTRAD, Math.max(0f, shotRad));
        data.setFloat(RUSH_KEY_FINALRAD, Math.max(0f, finalRad));
        data.setInteger(RUSH_KEY_OWNER, attacker.getEntityId());
        data.setBoolean(RUSH_KEY_ANIM, isAnim);

        // Initial shove (mostly horizontal)
        float kbH = isAnim ? ENERGIZED_ANIM_RUSH_INITIAL_KB_H : ENERGIZED_RUSH_INITIAL_KB_H;
        float kbY = isAnim ? ENERGIZED_ANIM_RUSH_INITIAL_KB_Y : ENERGIZED_RUSH_INITIAL_KB_Y;
        applyHorizontalKnock(target, attacker, kbH, kbY);

        // Start VFX
        playRainbowBurst(w, target.posX, target.posY + target.height * 0.55, target.posZ);
        w.playSoundAtEntity(target, "fireworks.launch", 0.55f, isAnim ? 1.15f : 1.05f);

        return true;
    }

    private static void tickRainbowRush(EntityLivingBase ent, NBTTagCompound data, World w, long now){
        if (ent == null || data == null || w == null) return;

        long end = data.getLong(RUSH_KEY_END);
        if (end <= 0L) return;

        if (now >= end){
            clearRush(ent);
            return;
        }

        int ownerId = data.getInteger(RUSH_KEY_OWNER);
        Entity ownerEnt = (ownerId != 0) ? w.getEntityByID(ownerId) : null;
        EntityPlayer attacker = (ownerEnt instanceof EntityPlayer) ? (EntityPlayer) ownerEnt : null;
        if (attacker == null || attacker.isDead){
            clearRush(ent);
            return;
        }

        // Optional charging sphere around attacker during the sequence
        if (ENERGIZED_RUSH_CHARGE_VFX){
            int total = Math.max(1, data.getInteger(RUSH_KEY_TOTAL));
            int left  = Math.max(0, data.getInteger(RUSH_KEY_SHOTS));
            float prog = 1.0f - (float)left / (float)total;

            float r = ENERGIZED_RUSH_CHARGE_RADIUS_MIN +
                    (ENERGIZED_RUSH_CHARGE_RADIUS_MAX - ENERGIZED_RUSH_CHARGE_RADIUS_MIN) * prog;

            playRainbowChargeSphereTick(w,
                    attacker.posX,
                    attacker.posY + attacker.height * 0.60,
                    attacker.posZ,
                    r,
                    Math.max(0, ENERGIZED_RUSH_CHARGE_PARTICLES));
        }

        long next = data.getLong(RUSH_KEY_NEXT);
        if (next <= 0L) next = now;
        if (now < next) return;

        int shots = data.getInteger(RUSH_KEY_SHOTS);
        int interval = Math.max(1, data.getInteger(RUSH_KEY_INT));
        int fdelay = Math.max(0, data.getInteger(RUSH_KEY_FDELAY));
        boolean isAnim = data.getBoolean(RUSH_KEY_ANIM);

        float shotDmg = data.getFloat(RUSH_KEY_SHOTDMG);
        float finalDmg = data.getFloat(RUSH_KEY_FINALDMG);
        float shotRad = data.getFloat(RUSH_KEY_SHOTRAD);
        float finalRad = data.getFloat(RUSH_KEY_FINALRAD);

        if (shots > 0){
            // Micro explosion
            playRainbowBurst(w, ent.posX, ent.posY + ent.height * 0.55, ent.posZ);
            w.playSoundAtEntity(ent, "random.fizz", 0.25f, isAnim ? 1.35f : 1.25f);

            // Ensure this can still land on players if desired
            if (ENERGIZED_RUSH_RESET_IFRAMES && (ent instanceof EntityPlayer)){
                try { ent.hurtResistantTime = 0; ent.hurtTime = 0; } catch (Throwable ignored) {}
            }

            if (shotRad <= 0.05f){
                dealProcDamage(attacker, ent, shotDmg, "rushShot");
            } else {
                dealRushDamageAoE(attacker, ent, w, shotRad, shotDmg, "rushShot");
            }

            // Small shove per shot (keeps "popping" backwards)
            float sh = isAnim ? ENERGIZED_ANIM_RUSH_SHOT_KB_H : ENERGIZED_RUSH_SHOT_KB_H;
            float sy = isAnim ? ENERGIZED_ANIM_RUSH_SHOT_KB_Y : ENERGIZED_RUSH_SHOT_KB_Y;
            applyHorizontalKnock(ent, attacker, sh, sy);

            shots--;
            data.setInteger(RUSH_KEY_SHOTS, shots);

            // Schedule next step
            if (shots <= 0){
                data.setLong(RUSH_KEY_NEXT, now + (long)fdelay);
            } else {
                data.setLong(RUSH_KEY_NEXT, now + (long)interval);
            }
            return;
        }

        // Finisher: blink near target, then big blast + launch
        if (ENERGIZED_RUSH_BLINK_FINISH && attacker instanceof EntityPlayerMP){
            tryBlinkNearTarget((EntityPlayerMP) attacker, ent, ENERGIZED_RUSH_BLINK_DISTANCE, ENERGIZED_RUSH_BLINK_TRIES);
        }

        // Visual: a quick "strike" swing right before the blast
        try { attacker.swingItem(); } catch (Throwable ignored) {}

        playRainbowBlastBig(w, ent.posX, ent.posY + ent.height * 0.50, ent.posZ);
        w.playSoundAtEntity(ent, "random.explode", 0.85f, isAnim ? 1.10f : 1.00f);

        if (ENERGIZED_RUSH_RESET_IFRAMES && (ent instanceof EntityPlayer)){
            try { ent.hurtResistantTime = 0; ent.hurtTime = 0; } catch (Throwable ignored) {}
        }

        if (finalRad <= 0.05f){
            dealProcDamage(attacker, ent, finalDmg, "rushFinal");
        } else {
            dealRushDamageAoE(attacker, ent, w, finalRad, finalDmg, "rushFinal");
        }

        // Launch the main target farther away from attacker
        applyKnockbackFrom(attacker, ent,
                KB_H * ENERGIZED_RUSH_FINISH_KB_H_MULT,
                KB_Y * ENERGIZED_RUSH_FINISH_KB_Y_MULT + ENERGIZED_RUSH_FINISH_KB_Y_ADD);

        clearRush(ent);
    }

    private static void clearRush(EntityLivingBase ent){
        if (ent == null) return;
        NBTTagCompound d = ent.getEntityData();
        if (d == null) return;
        d.removeTag(RUSH_KEY_END);
        d.removeTag(RUSH_KEY_NEXT);
        d.removeTag(RUSH_KEY_OWNER);
        d.removeTag(RUSH_KEY_SHOTS);
        d.removeTag(RUSH_KEY_TOTAL);
        d.removeTag(RUSH_KEY_INT);
        d.removeTag(RUSH_KEY_FDELAY);
        d.removeTag(RUSH_KEY_SHOTDMG);
        d.removeTag(RUSH_KEY_FINALDMG);
        d.removeTag(RUSH_KEY_SHOTRAD);
        d.removeTag(RUSH_KEY_FINALRAD);
        d.removeTag(RUSH_KEY_ANIM);
    }

    private static void dealRushDamageAoE(EntityPlayer attacker, EntityLivingBase primary, World w, float radius, float dmg, String tag){
        if (attacker == null || primary == null || w == null) return;
        if (radius <= 0.05f){
            dealProcDamage(attacker, primary, dmg, tag);
            return;
        }

        AxisAlignedBB bb = primary.boundingBox.expand(radius, radius, radius);
        @SuppressWarnings("unchecked")
        List<EntityLivingBase> hits = w.getEntitiesWithinAABB(EntityLivingBase.class, bb);

        if (hits == null || hits.isEmpty()) return;

        for (EntityLivingBase t : hits){
            if (t == null || t.isDead) continue;
            if (t == attacker) continue;

            if (ENERGIZED_RUSH_RESET_IFRAMES && (t instanceof EntityPlayer)){
                try { t.hurtResistantTime = 0; t.hurtTime = 0; } catch (Throwable ignored) {}
            }

            dealProcDamage(attacker, t, dmg, tag);
        }
    }

    /** Mostly-horizontal knock away from a source entity. */
    private static void applyHorizontalKnock(EntityLivingBase target, Entity src, float h, float y){
        if (target == null || src == null) return;

        double dx = target.posX - src.posX;
        double dz = target.posZ - src.posZ;
        double d2 = dx * dx + dz * dz;

        if (d2 < 1.0e-6){
            dx = -Math.sin(Math.toRadians(src.rotationYaw));
            dz =  Math.cos(Math.toRadians(src.rotationYaw));
            d2 = dx * dx + dz * dz;
        }

        double inv = 1.0 / Math.sqrt(d2);
        dx *= inv;
        dz *= inv;

        target.motionX += dx * h;
        target.motionZ += dz * h;
        target.motionY += y;

        try { target.velocityChanged = true; } catch (Throwable ignored) {}
    }

    /** Server-side blink near a target while avoiding clipping into blocks. */
    private static boolean tryBlinkNearTarget(EntityPlayerMP p, EntityLivingBase target, double dist, int tries){
        if (p == null || target == null) return false;
        World w = p.worldObj;
        if (w == null) return false;

        tries = Math.max(1, tries);

        double tx = target.posX;
        double tz = target.posZ;
        double ty = target.boundingBox != null ? target.boundingBox.minY : target.posY;

        // Prefer the side of the target facing the attacker
        double pdx = p.posX - tx;
        double pdz = p.posZ - tz;
        double pd2 = pdx * pdx + pdz * pdz;
        if (pd2 < 1.0e-4){
            pdx = -Math.sin(Math.toRadians(p.rotationYaw));
            pdz =  Math.cos(Math.toRadians(p.rotationYaw));
            pd2 = pdx * pdx + pdz * pdz;
        }

        double inv = 1.0 / Math.sqrt(pd2);
        pdx *= inv;
        pdz *= inv;

        // Try preferred direction first, then sweep around
        for (int i = 0; i < tries; i++){
            double ang = (i == 0) ? 0.0 : (2.0 * Math.PI) * (double)(i - 1) / (double)Math.max(1, tries - 1);
            double cos = Math.cos(ang);
            double sin = Math.sin(ang);

            double rx = pdx * cos - pdz * sin;
            double rz = pdx * sin + pdz * cos;

            double x = tx + rx * dist;
            double z = tz + rz * dist;

            // A few Y attempts around target feet
            double[] yoffs = new double[]{0.0, 0.5, 1.0, -0.5};
            for (double yo : yoffs){
                double y = ty + yo;
                if (y < 1.0) y = 1.0;
                if (y > 254.0) y = 254.0;

                if (isTeleportSpotFree(p, w, x, y, z)){
                    p.setPositionAndUpdate(x, y, z);
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean isTeleportSpotFree(EntityPlayerMP p, World w, double x, double y, double z){
        if (p == null || w == null) return false;

        float hw = p.width * 0.5f;
        AxisAlignedBB bb = AxisAlignedBB.getBoundingBox(
                x - hw, y, z - hw,
                x + hw, y + p.height, z + hw
        );

        @SuppressWarnings("unchecked")
        List<AxisAlignedBB> coll = w.getCollidingBoundingBoxes(p, bb);

        return coll == null || coll.isEmpty();
    }


    private static void clearSwirl(EntityLivingBase ent){
        if (ent == null) return;
        NBTTagCompound d = ent.getEntityData();
        if (d == null) return;
        d.removeTag(SW_KEY_END);
        d.removeTag(SW_KEY_START);
        d.removeTag(SW_KEY_CX);
        d.removeTag(SW_KEY_CZ);
        d.removeTag(SW_KEY_BASEY);
        d.removeTag(SW_KEY_OWNER);
        d.removeTag(SW_KEY_EXRAD);
        d.removeTag(SW_KEY_EXDMG);
        d.removeTag(SW_KEY_DOT);
        d.removeTag(SW_KEY_DOTINT);
        d.removeTag(SW_KEY_RAD);
        d.removeTag(SW_KEY_LIFT);
        d.removeTag(SW_KEY_SPIN);
        d.removeTag(SW_KEY_TICKP);
        d.removeTag(SW_KEY_MODE);
        d.removeTag(SW_KEY_DIRX);
        d.removeTag(SW_KEY_DIRZ);
        d.removeTag(SW_KEY_PUSH);
    }

    private static void doSwirlExplosion(EntityLivingBase centerEnt, boolean reachedEnd){
        if (centerEnt == null) return;

        World w = centerEnt.worldObj;
        if (w == null) return;

        NBTTagCompound data = centerEnt.getEntityData();
        if (data == null) return;

        int ownerId = data.getInteger(SW_KEY_OWNER);
        float exRad = data.getFloat(SW_KEY_EXRAD);
        float exDmg = data.getFloat(SW_KEY_EXDMG);

        if (exRad <= 0f) exRad = ENERGIZED_SWIRL_EXPLODE_RADIUS;
        if (exDmg <= 0f) exDmg = 1.0f;

        Entity owner = w.getEntityByID(ownerId);
        EntityPlayer attacker = (owner instanceof EntityPlayer) ? (EntityPlayer) owner : null;

        playRainbowBurst(w, centerEnt.posX, centerEnt.posY + 1.0, centerEnt.posZ);

        if (w instanceof WorldServer){
            WorldServer ws = (WorldServer) w;
            ws.func_147487_a("hugeexplosion", centerEnt.posX, centerEnt.posY + 0.6, centerEnt.posZ, 1, 0, 0, 0, 0);
            ws.func_147487_a("explode", centerEnt.posX, centerEnt.posY + 0.6, centerEnt.posZ, 12, 0.35, 0.25, 0.35, 0.08);
            ws.func_147487_a("fireworksSpark", centerEnt.posX, centerEnt.posY + 0.6, centerEnt.posZ, 24, 0.55, 0.35, 0.55, 0.14);
        }

        w.playSoundAtEntity(centerEnt, "random.explode", 0.7f, 1.05f);
        w.playSoundAtEntity(centerEnt, "fireworks.twinkle", 0.6f, 1.25f);

        AxisAlignedBB box = centerEnt.boundingBox.expand(exRad, 2.0, exRad);

        @SuppressWarnings("unchecked")
        List<EntityLivingBase> list = w.getEntitiesWithinAABB(EntityLivingBase.class, box);

        for (EntityLivingBase t : list){
            if (t == null) continue;
            if (t.isDead) continue;
            if (attacker != null && t == attacker) continue;

            float finalDmg = exDmg;
            if (t instanceof EntityPlayer) finalDmg *= DAMAGE_VS_PLAYERS_SCALE;

            if (attacker != null){
                dealProcDamage(attacker, t, finalDmg, "swirlEX");
            } else {
                t.attackEntityFrom(DamageSource.magic, finalDmg);
            }

            applyExplosionKnock(centerEnt, t);
        }

        if (reachedEnd){
            int mode = 0;
            try { mode = centerEnt.getEntityData().getInteger(SW_KEY_MODE); } catch (Throwable ignored) {}
            if (mode == 1){
                playRainbowBlastBig(w, centerEnt.posX, centerEnt.posY + 1.0, centerEnt.posZ);
                if (!w.isRemote){
                    w.playSoundEffect(centerEnt.posX, centerEnt.posY, centerEnt.posZ, "random.explode", 0.85f, 1.00f);
                }
            } else {
                playRainbowSpark(w, centerEnt.posX, centerEnt.posY + 1.0, centerEnt.posZ);
            }
        }
    }

    private static void applyExplosionKnock(EntityLivingBase from, EntityLivingBase to){
        if (from == null || to == null) return;

        double dx = to.posX - from.posX;
        double dz = to.posZ - from.posZ;
        double d2 = dx * dx + dz * dz;
        if (d2 < 1.0e-4) d2 = 1.0e-4;

        double inv = 1.0 / Math.sqrt(d2);
        dx *= inv;
        dz *= inv;

        to.addVelocity(dx * (KB_H * 1.10f), (KB_Y * 1.60f) + 0.18f, dz * (KB_H * 1.10f));
        to.velocityChanged = true;
        to.fallDistance = 0.0f;
    }

    private static void playRainbowSwirlTick(World w, double x, double y, double z, int tickParticles){
        if (!(w instanceof WorldServer)) return;
        WorldServer ws = (WorldServer) w;

        int steps = Math.max(4, RAINBOW_STEPS);
        float rad = 0.55f;
        long t = ws.getTotalWorldTime();

        for (int i = 0; i < steps; i++){
            float h = (float) i / (float) steps;
            float[] rgb = hsvToRgb(h, 1.0f, 1.0f);

            double ang = (t * 0.45) + (i * (Math.PI * 2.0 / steps));
            double px = x + Math.cos(ang) * rad;
            double pz = z + Math.sin(ang) * rad;
            double py = y + (ws.rand.nextDouble() * 0.25 - 0.10);

            ws.func_147487_a("reddust", px, py, pz, 0, rgb[0], rgb[1], rgb[2], 1.0);

            if (tickParticles > 0 && (i % 2 == 0)){
                ws.func_147487_a("fireworksSpark", px, py, pz, 1, 0.02, 0.02, 0.02, 0.01);
            }
        }
    }

    // -------------------------------------------------------------
    // COOLDOWN / RNG
    // -------------------------------------------------------------
    private static boolean roll(EntityPlayer p, double chance){
        if (chance <= 0) return false;
        if (chance >= 1) return true;
        return p.getRNG().nextDouble() < chance;
    }

    // Backwards-compat alias (some older snippets used rollChance)
    private static boolean rollChance(EntityPlayer p, double chance){
        return roll(p, chance);
    }


    /**
     * Global server tick time (dimension 0) for cooldown/logic.
     * Some mod dimensions keep their own time frozen/offset, which can break cooldowns.
     */
    private static long serverNow(World fallback){
        try {
            WorldServer ws0 = DimensionManager.getWorld(0);
            if (ws0 != null) return ws0.getTotalWorldTime();
        } catch (Throwable ignored) {}
        return (fallback != null) ? fallback.getTotalWorldTime() : 0L;
    }

    private static boolean cooldownReady(EntityPlayer p, String key, int cdTicks){
        if (cdTicks <= 0) return true;

        long now = serverNow(p.worldObj);
        NBTTagCompound data = p.getEntityData();
        long next = data.getLong(key);

        if (next != 0L && now < next) return false;

        data.setLong(key, now + cdTicks);
        return true;
    }
    /** Overload when the caller already computed serverNow(). */
    private static boolean cooldownReady(EntityPlayer p, String key, int cdTicks, long now){
        if (cdTicks <= 0) return true;
        if (p == null) return true;
        NBTTagCompound data = p.getEntityData();
        long next = data.getLong(key);
        if (next != 0L && now < next) return false;
        data.setLong(key, now + cdTicks);
        return true;
    }


    /** de-dupe Attack+Hurt in the same tick for the same target. */
    private static boolean markOncePerTick(EntityPlayer p, EntityLivingBase target){
        if (p == null || target == null) return true;
        NBTTagCompound d = p.getEntityData();
        long now = serverNow(p.worldObj);
        int dim = p.dimension;
        long lastTick = d.getLong("HexOrb_LastEvtTick");
        int lastTgt = d.getInteger("HexOrb_LastEvtTgt");
        int lastDim = d.getInteger("HexOrb_LastEvtDim");
        if (lastDim == dim && lastTick == now && lastTgt == target.getEntityId()) return false;
        d.setLong("HexOrb_LastEvtTick", now);
        d.setInteger("HexOrb_LastEvtTgt", target.getEntityId());
        d.setInteger("HexOrb_LastEvtDim", dim);
        return true;
    }

    // -------------------------------------------------------------
    // VFX helpers
    // -------------------------------------------------------------
    private static void playRainbowBurst(World w, double x, double y, double z){
        if (!(w instanceof WorldServer)) return;
        WorldServer ws = (WorldServer) w;

        spawnRainbowColors(ws, x, y, z, RAINBOW_PARTICLES_BURST, 0.70, 0.42, 0.70);

        ws.func_147487_a("fireworksSpark", x, y, z, 30, 0.50, 0.30, 0.50, 0.16);
        ws.func_147487_a("portal", x, y, z, 18, 0.45, 0.32, 0.45, 0.10);
        ws.func_147487_a("spell", x, y, z, 14, 0.35, 0.22, 0.35, 0.08);
    }

    /** Bigger version used by PUSH_BLAST end explosion. */
    private static void playRainbowBlastBig(World w, double x, double y, double z){
        if (!(w instanceof WorldServer)) return;
        WorldServer ws = (WorldServer) w;

        // extra dense rainbow + wider spread
        spawnRainbowColors(ws, x, y, z, Math.max(RAINBOW_PARTICLES_BURST * 2, 60), 1.25, 0.75, 1.25);

        ws.func_147487_a("fireworksSpark", x, y, z, 90, 1.10, 0.65, 1.10, 0.26);
        ws.func_147487_a("portal", x, y, z, 60, 1.00, 0.55, 1.00, 0.18);
        ws.func_147487_a("spell", x, y, z, 45, 0.85, 0.45, 0.85, 0.14);
        ws.func_147487_a("explode", x, y, z, 12, 0.60, 0.40, 0.60, 0.10);
    }


    // Mega explosion + rainbow beams (used by Rainbow Fist)
    private static void playRainbowFistMegaExplosion(World w, EntityPlayer attacker, double x, double y, double z){
        if (!(w instanceof WorldServer)) return;
        WorldServer ws = (WorldServer) w;

        // Base big blast
        playRainbowBlastBig(w, x, y, z);

        // Extra explosion density
        int extra = Math.max(0, ENERGIZED_FIST_EXTRA_EXPLODES);
        if (extra > 0){
            ws.func_147487_a("largeexplode", x, y, z, extra, 1.35, 0.90, 1.35, 0.18);
            ws.func_147487_a("explode", x, y, z, Math.max(6, extra / 2), 0.75, 0.55, 0.75, 0.12);
        }
        ws.func_147487_a("hugeexplosion", x, y, z, 1, 0.0, 0.0, 0.0, 0.0);

        // Beam from attacker -> impact (reads as "exploding fist")
        if (attacker != null){
            double ax = attacker.posX;
            double ay = attacker.posY + attacker.getEyeHeight() * 0.75;
            double az = attacker.posZ;
            spawnRainbowBeamLine(ws, ax, ay, az, x, y, z, ENERGIZED_FIST_BEAM_STEPS, 0.04);
        }

        // Radial beams out of the impact
        spawnRainbowRadialBeams(ws, x, y, z, ENERGIZED_FIST_BEAM_COUNT, ENERGIZED_FIST_BEAM_LEN, ENERGIZED_FIST_BEAM_STEPS);
    }

    private static void spawnRainbowRadialBeams(WorldServer ws, double x, double y, double z, int count, double len, int steps){
        if (ws == null) return;
        count = Math.max(0, count);
        if (count <= 0) return;

        len = Math.max(0.25, len);

        for (int i = 0; i < count; i++){
            // Random unit vector (spherical)
            double u = ws.rand.nextDouble() * 2.0 - 1.0; // -1..1
            double theta = ws.rand.nextDouble() * (Math.PI * 2.0);
            double s = Math.sqrt(Math.max(0.0, 1.0 - u * u));
            double dx = s * Math.cos(theta);
            double dz = s * Math.sin(theta);
            double dy = u * 0.65; // slightly flatter so beams read in front of player

            double x2 = x + dx * len;
            double y2 = y + dy * len;
            double z2 = z + dz * len;

            spawnRainbowBeamLine(ws, x, y, z, x2, y2, z2, steps, 0.03);
        }
    }

    private static void spawnRainbowBeamLine(WorldServer ws, double x1, double y1, double z1,
                                             double x2, double y2, double z2, int steps, double jitter){
        if (ws == null) return;

        steps = Math.max(4, steps);
        jitter = Math.max(0.0, jitter);

        double dx = (x2 - x1) / (double) steps;
        double dy = (y2 - y1) / (double) steps;
        double dz = (z2 - z1) / (double) steps;

        for (int i = 0; i <= steps; i++){
            double px = x1 + dx * i;
            double py = y1 + dy * i;
            double pz = z1 + dz * i;

            if (jitter > 0.0){
                px += (ws.rand.nextDouble() * 2.0 - 1.0) * jitter;
                py += (ws.rand.nextDouble() * 2.0 - 1.0) * jitter;
                pz += (ws.rand.nextDouble() * 2.0 - 1.0) * jitter;
            }

            float h = (float) i / (float) steps;
            float[] rgb = hsvToRgb(h, 1.0f, 1.0f);
            float r = rgb[0], g = rgb[1], b = rgb[2];

            // Colored beam core
            ws.func_147487_a("reddust", px, py, pz, 0, r, g, b, 1.0);
            ws.func_147487_a("mobSpell", px, py, pz, 0, r, g, b, 1.0);

            // Bright "spark" on top
            ws.func_147487_a("fireworksSpark", px, py, pz, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    // Expanding rainbow "charge" shell around a point (used for push-blast windup)
    private static void playRainbowChargeSphereTick(World w, double x, double y, double z, float radius, int total){
        if (!(w instanceof WorldServer)) return;
        WorldServer ws = (WorldServer) w;

        if (radius < 0.15f) radius = 0.15f;
        int n = Math.max(6, total);

        long t = ws.getTotalWorldTime();
        double timeShift = (double) t * 0.05;

        for (int i = 0; i < n; i++){
            // random-ish points on a sphere
            double u = (i + ws.rand.nextDouble()) / (double) n;
            double v = ws.rand.nextDouble();

            double theta = (Math.PI * 2.0 * u) + timeShift;
            double phi = Math.acos(2.0 * v - 1.0);

            double sx = Math.sin(phi) * Math.cos(theta);
            double sy = Math.cos(phi);
            double sz = Math.sin(phi) * Math.sin(theta);

            double px = x + sx * (double) radius;
            double py = y + sy * (double) radius;
            double pz = z + sz * (double) radius;

            float h = (float) (((double) t * 0.01 + u) % 1.0);
            float[] rgb = hsvToRgb(h, 1.0f, 1.0f);

            // "reddust": dx/dy/dz are RGB (0..1), speed acts as "size"
            ws.func_147487_a("reddust", px, py, pz, 0, rgb[0], rgb[1], rgb[2], 1.0);

            if ((i & 1) == 0){
                ws.func_147487_a("fireworksSpark", px, py, pz, 1, sx * 0.02, sy * 0.02, sz * 0.02, 0.01);
            }
        }

        // soft inner haze
        ws.func_147487_a("portal", x, y, z, Math.max(6, n / 4), radius * 0.12, radius * 0.08, radius * 0.12, 0.02);
        ws.func_147487_a("spell", x, y, z, Math.max(4, n / 6), radius * 0.10, radius * 0.06, radius * 0.10, 0.01);
    }



    private static void playRainbowSpark(World w, double x, double y, double z){
        if (!(w instanceof WorldServer)) return;
        WorldServer ws = (WorldServer) w;

        spawnRainbowColors(ws, x, y, z, RAINBOW_PARTICLES_SPARK, 0.22, 0.18, 0.22);
        ws.func_147487_a("fireworksSpark", x, y, z, 10, 0.22, 0.16, 0.22, 0.12);
    }

    private static void spawnRainbowColors(WorldServer ws, double x, double y, double z, int total, double spreadX, double spreadY, double spreadZ){
        if (ws == null) return;
        int steps = Math.max(3, RAINBOW_STEPS);
        int perStep = Math.max(1, total / steps);

        for (int i = 0; i < steps; i++){
            float h = (float) i / (float) steps;
            float[] rgb = hsvToRgb(h, 1.0f, 1.0f);
            float r = rgb[0], g = rgb[1], b = rgb[2];

            for (int j = 0; j < perStep; j++){
                double px = x + (ws.rand.nextDouble() * 2.0 - 1.0) * spreadX;
                double py = y + (ws.rand.nextDouble() * 2.0 - 1.0) * spreadY;
                double pz = z + (ws.rand.nextDouble() * 2.0 - 1.0) * spreadZ;

                ws.func_147487_a("reddust", px, py, pz, 0, r, g, b, 1.0);
                ws.func_147487_a("mobSpell", px, py, pz, 0, r, g, b, 1.0);
            }
        }
    }

    private static float[] hsvToRgb(float h, float s, float v){
        h = h - (float) Math.floor(h);
        s = clamp01f(s);
        v = clamp01f(v);

        float c = v * s;
        float hh = h * 6.0f;
        float x = c * (1.0f - Math.abs((hh % 2.0f) - 1.0f));

        float r1, g1, b1;
        if (hh < 1.0f){ r1 = c; g1 = x; b1 = 0; }
        else if (hh < 2.0f){ r1 = x; g1 = c; b1 = 0; }
        else if (hh < 3.0f){ r1 = 0; g1 = c; b1 = x; }
        else if (hh < 4.0f){ r1 = 0; g1 = x; b1 = c; }
        else if (hh < 5.0f){ r1 = x; g1 = 0; b1 = c; }
        else { r1 = c; g1 = 0; b1 = x; }

        float m = v - c;
        return new float[]{ r1 + m, g1 + m, b1 + m };
    }

    private static float clamp01f(float v){
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    // -------------------------------------------------------------
    // Knockback
    // -------------------------------------------------------------
    private static void applyKnockbackFrom(EntityPlayer from, EntityLivingBase to){
        applyKnockbackFrom(from, to, KB_H, KB_Y);
    }

    /** Knockback 'to' away from 'from' using explicit horiz strength + Y boost. */
    private static void applyKnockbackFrom(EntityPlayer from, EntityLivingBase to, double horiz, double yBoost){
        if (from == null || to == null) return;

        double dx = to.posX - from.posX;
        double dz = to.posZ - from.posZ;
        double d2 = dx * dx + dz * dz;
        if (d2 < 1.0e-4) return;

        double inv = 1.0 / Math.sqrt(d2);
        dx *= inv;
        dz *= inv;

        to.addVelocity(dx * horiz, yBoost, dz * horiz);
        to.velocityChanged = true;
    }


    // -------------------------------------------------------------
    // Socket detection (reflection, supports older/newer HexSocketAPI)
    // -------------------------------------------------------------
    private static Method M_getFilled;
    private static Method M_getSocketsFilled;
    private static Method M_getGemKeyAt;
    private static Method M_getGemAt;
    private static Method M_forceGemsFolder;

    static {
        M_getFilled        = findMethod("getFilled", ItemStack.class);
        M_getSocketsFilled = findMethod("getSocketsFilled", ItemStack.class);
        M_getGemKeyAt      = findMethod("getGemKeyAt", ItemStack.class, int.class);
        M_getGemAt         = findMethod("getGemAt", ItemStack.class, int.class);
        M_forceGemsFolder  = findMethod("forceGemsFolder", String.class);
    }

    private static Method findMethod(String name, Class<?>... params){
        try {
            Method m = HexSocketAPI.class.getMethod(name, params);
            m.setAccessible(true);
            return m;
        } catch (Throwable t){
            return null;
        }
    }

    private static boolean hasGemSocketed(ItemStack stack, String wantedKey){
        if (stack == null || wantedKey == null || wantedKey.isEmpty()) return false;

        String wanted = normalizeGemKey(wantedKey);

        int filled = getFilled(stack);
        for (int i = 0; i < filled; i++){
            String k = getGemKeyAt(stack, i);
            if (k == null || k.isEmpty()) continue;

            k = normalizeGemKey(k);

            if (k.equals(wanted)) return true;
            if (k.startsWith("gems/") && k.substring(5).equals(wanted)) return true;
            if (wanted.startsWith("gems/") && wanted.substring(5).equals(k)) return true;
        }
        return false;
    }

    private static int getFilled(ItemStack stack){
        try {
            if (M_getFilled != null) return (Integer) M_getFilled.invoke(null, stack);
            if (M_getSocketsFilled != null) return (Integer) M_getSocketsFilled.invoke(null, stack);
        } catch (Throwable ignored) {}
        return 0;
    }

    // Some code paths call this name; keep it as an alias of getFilled().
    private static int getSocketsFilled(ItemStack stack){
        return getFilled(stack);
    }

    private static String itemLabel(ItemStack s){
        if (s == null) return "null";
        try {
            Item it = s.getItem();
            String id = (it != null && Item.itemRegistry != null) ? String.valueOf(Item.itemRegistry.getNameForObject(it)) : "?";
            return s.getDisplayName() + " (" + id + ") x" + s.stackSize;
        } catch (Throwable ignored) {
            return String.valueOf(s);
        }
    }

    /**
     * Stores HUD-friendly fields on the host ItemStack so the CLIENT can render the type/ability + cooldown bar
     * without needing a custom S2C packet.
     *
     * NOTE: Inventory stack NBT is synced to the client when it changes, so we only write this on proc (not every tick).
     */
    private static void stampVoidHud(EntityPlayer owner, ItemStack host, String voidType, String abilityName, long cdEndTick, long activeEndTick, int cdMaxTicks){
        if (host == null) return;

        NBTTagCompound tag = host.getTagCompound();
        if (tag == null) tag = new NBTTagCompound();

        if (voidType != null && voidType.length() > 0){
            tag.setString("HexVoidHudType", voidType);
        }
        if (abilityName != null && abilityName.length() > 0){
            tag.setString("HexVoidHudAbility", abilityName);
        }

        tag.setLong("HexVoidHudCDEnd", cdEndTick);
        tag.setLong("HexVoidHudActiveEnd", activeEndTick);
        tag.setInteger("HexVoidHudCDMax", cdMaxTicks);

        host.setTagCompound(tag);

        // push changes to client ASAP in MP
        if (owner instanceof EntityPlayerMP){
            try {
                ((EntityPlayerMP) owner).inventoryContainer.detectAndSendChanges();
            } catch (Throwable ignored) {}
        }
    }



    private static String getGemKeyAt(ItemStack stack, int idx){
        try {
            Object out = null;
            if (M_getGemKeyAt != null) out = M_getGemKeyAt.invoke(null, stack, idx);
            else if (M_getGemAt != null) out = M_getGemAt.invoke(null, stack, idx);

            if (out == null) return null;

            String s = String.valueOf(out);

            if (M_forceGemsFolder != null){
                try {
                    Object forced = M_forceGemsFolder.invoke(null, s);
                    if (forced != null) s = String.valueOf(forced);
                } catch (Throwable ignored2){}
            }

            return s;
        } catch (Throwable ignored) {}
        return null;
    }

    private static String normalizeGemKey(String key){
        String k = key.trim();

        if (k.startsWith("<ico:") && k.endsWith(">")){
            k = k.substring(5, k.length() - 1);
        }

        while (k.startsWith("/")) k = k.substring(1);

        return k;
    }

    // -------------------------------------------------------------
    // Debug helpers
    // -------------------------------------------------------------
    private static void debugOncePerSecond(EntityPlayer p, String msg){
        if (!DEBUG_PROC || p == null) return;

        NBTTagCompound d = p.getEntityData();
        long now = serverNow(p.worldObj);
        long next = d.getLong("HexOrbDBG_Next");
        if (next != 0L && now < next) return;
        d.setLong("HexOrbDBG_Next", now + 20L);

        try {
            p.addChatMessage(new ChatComponentText(msg));
        } catch (Throwable ignored) {}
    }

    private static void debugSocketKeysOncePerSecond(EntityPlayer p, ItemStack held){
        if (!DEBUG_PROC || !DEBUG_SOCKET_KEYS || p == null || held == null) return;

        NBTTagCompound d = p.getEntityData();
        long now = serverNow(p.worldObj);
        long next = d.getLong("HexOrbDBG_KeysNext");
        if (next != 0L && now < next) return;
        d.setLong("HexOrbDBG_KeysNext", now + 20L);

        int filled = getFilled(held);
        StringBuilder sb = new StringBuilder();
        sb.append("[HexOrb] sockets filled=").append(filled).append(" keys=");
        for (int i = 0; i < filled && i < 6; i++){
            String k = getGemKeyAt(held, i);
            if (k == null) k = "null";
            sb.append(i == 0 ? "" : ", ").append(k);
        }
        if (filled > 6) sb.append(" ...");

        try {
            p.addChatMessage(new ChatComponentText(sb.toString()));
        } catch (Throwable ignored) {}
    }

    private static String safeEntName(Entity e){
        if (e == null) return "null";
        try { return e.getCommandSenderName(); } catch (Throwable t){ return e.getClass().getSimpleName(); }
    }


    // -------------------------------------------------------------

    // Returns MODIFIED WillPower (includes form multipliers) when DBC exposes it as an attribute.
    // Used only for FRACTURED flying-blast dynamic scaling.
    // Returns MODIFIED WillPower (includes form multipliers) when available.
    // Used only for FRACTURED flying-blast dynamic scaling.
    private static int frbGetEffectiveWill(net.minecraft.entity.player.EntityPlayerMP p){
        if (p == null) return 0;

        // Prefer our provider helper (handles Multi attribute + NPCDBC reflection + NBT fallback)
        try {
            double v = com.example.examplemod.server.HexDBCProcDamageProvider.getWillPowerEffective(p);
            if (v > 0D){
                if (v > 2000000000D) v = 2000000000D;
                return (int)Math.round(v);
            }
        } catch (Throwable ignored) {}

        // Fallback: attribute-only (may be base/original on some setups)
        try {
            net.minecraft.entity.ai.attributes.IAttributeInstance inst =
                    p.getAttributeMap().getAttributeInstanceByName("dbc.WillPower");
            if (inst != null){
                double v = inst.getAttributeValue();
                if (v > 0D){
                    // If Multi exists and looks meaningful, apply it when value seems "original".
                    try {
                        net.minecraft.entity.ai.attributes.IAttributeInstance mi =
                                p.getAttributeMap().getAttributeInstanceByName("dbc.WillPower.Multi");
                        if (mi != null){
                            double mul = mi.getAttributeValue();
                            if (mul > 1.05D && v < 50000D) v = v * mul;
                        }
                    } catch (Throwable ignored2) {}
                    if (v > 2000000000D) v = 2000000000D;
                    return (int)Math.round(v);
                }
            }
        } catch (Throwable ignored) {}
        return 0;
    }


    // FRACTURED: Flying blast tick (server-side)
    // -------------------------------------------------------------
    private static void tickFracturedFlyingBlast(net.minecraft.entity.player.EntityPlayerMP p){
        if (p == null || p.worldObj == null || p.worldObj.isRemote) return;

        NBTTagCompound data = p.getEntityData();
        if (data == null || !data.getBoolean(FRB_KEY_ACTIVE)) return;

        int ticksLeft = data.getInteger(FRB_KEY_TICKS);

        double x  = data.getDouble(FRB_KEY_X);
        double y  = data.getDouble(FRB_KEY_Y);
        double z  = data.getDouble(FRB_KEY_Z);

        double dx = data.getDouble(FRB_KEY_DX);
        double dy = data.getDouble(FRB_KEY_DY);
        double dz = data.getDouble(FRB_KEY_DZ);

        double step = data.getDouble(FRB_KEY_STEP);
        if (step <= 0D) step = 1.25D;

        double rad = data.getDouble(FRB_KEY_RAD);
        if (rad <= 0D) rad = 0.55D;

        float dmg = data.getFloat(FRB_KEY_DMG);
        if (Float.isNaN(dmg) || Float.isInfinite(dmg) || dmg <= 0f) dmg = 4.0f;

        double kb = data.getDouble(FRB_KEY_KB);
        if (kb < 0D) kb = 0D;

        double aoeRad = data.hasKey(FRB_KEY_AOE_RAD) ? data.getDouble(FRB_KEY_AOE_RAD) : 0D;
        float aoeDmg   = data.hasKey(FRB_KEY_AOE_DMG) ? data.getFloat(FRB_KEY_AOE_DMG) : (float)(dmg * 0.70f);

        // Dynamic scaling: if the player changes forms while the blast is flying,
        // scale damage by currentWill / snapshotWill (snapshot written by PacketFracturedAction).
        int wilSnap = data.hasKey(FRB_KEY_WIL_SNAP) ? data.getInteger(FRB_KEY_WIL_SNAP) : 0;
        int wilCur  = frbGetEffectiveWill(p);
        if (wilSnap > 0 && wilCur > 0){
            float wilMul = wilCur / (float) wilSnap;
            // Keep sane even if something returns weird numbers
            if (!Float.isNaN(wilMul) && !Float.isInfinite(wilMul)){
                if (wilMul < 0.10f) wilMul = 0.10f;
                if (wilMul > 40.0f) wilMul = 40.0f;
                dmg *= wilMul;
                aoeDmg *= wilMul;
            }
        }


        // Expired -> explode/dissipate at current position (and AoE if configured)
        if (ticksLeft <= 0){
            spawnFrbImpactFX(p, x, y, z);
            if (aoeRad > 0D && aoeDmg > 0f){
                frbExplode(p, x, y, z, aoeRad, aoeDmg, kb, null);
            }
            clearFrb(data);
            return;
        }

        net.minecraft.util.Vec3 start = net.minecraft.util.Vec3.createVectorHelper(x, y, z);
        net.minecraft.util.Vec3 end   = net.minecraft.util.Vec3.createVectorHelper(x + dx * step, y + dy * step, z + dz * step);

        // Bigger "orb" feel while flying
        spawnFrbTrailFXScaled(p, start.xCoord, start.yCoord, start.zCoord, rad);
        spawnFrbTrailFXScaled(p, end.xCoord, end.yCoord, end.zCoord, rad);

        // Block hit?
        net.minecraft.util.MovingObjectPosition mop = null;
        try {
            mop = p.worldObj.rayTraceBlocks(start, end);
        } catch (Throwable ignored) {}

        if (mop != null && mop.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK){
            // explode on contact
            double hx = mop.hitVec != null ? mop.hitVec.xCoord : end.xCoord;
            double hy = mop.hitVec != null ? mop.hitVec.yCoord : end.yCoord;
            double hz = mop.hitVec != null ? mop.hitVec.zCoord : end.zCoord;

            spawnFrbImpactFX(p, hx, hy, hz);
            if (aoeRad > 0D && aoeDmg > 0f){
                frbExplode(p, hx, hy, hz, aoeRad, aoeDmg, kb, null);
            }
            clearFrb(data);
            return;
        }

        // Entity hit scan along segment (expanded by rad)
        net.minecraft.entity.EntityLivingBase hit = null;
        double hitDistSq = Double.MAX_VALUE;

        try {
            net.minecraft.util.AxisAlignedBB bb = net.minecraft.util.AxisAlignedBB.getBoundingBox(
                    Math.min(start.xCoord, end.xCoord) - rad, Math.min(start.yCoord, end.yCoord) - rad, Math.min(start.zCoord, end.zCoord) - rad,
                    Math.max(start.xCoord, end.xCoord) + rad, Math.max(start.yCoord, end.yCoord) + rad, Math.max(start.zCoord, end.zCoord) + rad
            );

            @SuppressWarnings("unchecked")
            java.util.List<net.minecraft.entity.Entity> ents = p.worldObj.getEntitiesWithinAABBExcludingEntity(p, bb);

            if (ents != null){
                for (int i = 0; i < ents.size(); i++){
                    net.minecraft.entity.Entity e = ents.get(i);
                    if (!(e instanceof net.minecraft.entity.EntityLivingBase)) continue;
                    if (!e.canBeCollidedWith()) continue;

                    net.minecraft.entity.EntityLivingBase elb = (net.minecraft.entity.EntityLivingBase) e;
                    if (elb.isDead) continue;
                    if (elb == p) continue;

                    double dd = elb.getDistanceSq(start.xCoord, start.yCoord, start.zCoord);
                    if (dd < hitDistSq){
                        hitDistSq = dd;
                        hit = elb;
                    }
                }
            }
        } catch (Throwable ignored) {}

        if (hit != null){
            double hx = hit.posX;
            double hy = hit.posY + (double)hit.height * 0.5D;
            double hz = hit.posZ;

            // Direct hit damage
            try {
                dealProcDamage((net.minecraft.entity.player.EntityPlayer)p, hit, dmg, "fracturedBlast");
            } catch (Throwable ignored) {}

            // Knockback in travel direction
            if (kb > 0D){
                try {
                    hit.addVelocity(dx * kb, dy * (kb * 0.25D), dz * kb);
                    hit.velocityChanged = true;
                } catch (Throwable ignored) {}
            }

            // Explosion (visual always; AoE for big/triple)
            spawnFrbImpactFX(p, hx, hy, hz);
            if (aoeRad > 0D && aoeDmg > 0f){
                frbExplode(p, hx, hy, hz, aoeRad, aoeDmg, kb, hit);
            }

            clearFrb(data);
            return;
        }

        // No hit -> advance
        data.setDouble(FRB_KEY_X, end.xCoord);
        data.setDouble(FRB_KEY_Y, end.yCoord);
        data.setDouble(FRB_KEY_Z, end.zCoord);

        data.setInteger(FRB_KEY_TICKS, ticksLeft - 1);
    }

    private static void clearFrb(NBTTagCompound data){
        if (data == null) return;
        data.setBoolean(FRB_KEY_ACTIVE, false);
        data.removeTag(FRB_KEY_TICKS);
        data.removeTag(FRB_KEY_X);
        data.removeTag(FRB_KEY_Y);
        data.removeTag(FRB_KEY_Z);
        data.removeTag(FRB_KEY_DX);
        data.removeTag(FRB_KEY_DY);
        data.removeTag(FRB_KEY_DZ);
        data.removeTag(FRB_KEY_STEP);
        data.removeTag(FRB_KEY_DMG);
        data.removeTag(FRB_KEY_WIL_SNAP);
        data.removeTag(FRB_KEY_KB);
        data.removeTag(FRB_KEY_RAD);
        data.removeTag(FRB_KEY_AOE_RAD);
        data.removeTag(FRB_KEY_AOE_DMG);
    }

    private static void spawnFrbTrailFX(net.minecraft.entity.player.EntityPlayerMP p, double x, double y, double z){
        if (p == null || p.worldObj == null) return;
        if (p.worldObj instanceof net.minecraft.world.WorldServer){
            ((net.minecraft.world.WorldServer) p.worldObj).func_147487_a("spell", x, y, z, 3, 0.04D, 0.04D, 0.04D, 0.01D);
            ((net.minecraft.world.WorldServer) p.worldObj).func_147487_a("fireworksSpark", x, y, z, 1, 0.02D, 0.02D, 0.02D, 0.02D);
        }
    }



    /**
     * Scaled trail FX for the Fractured flying blast.
     * Server-safe: uses WorldServer.func_147487_a (particle packet) only.
     */
    private static void spawnFrbTrailFXScaled(net.minecraft.entity.player.EntityPlayerMP p, double x, double y, double z, double rad){
        if (p == null || p.worldObj == null) return;

        // Keep the tiny look identical to the base helper
        if (rad <= 0.60D){
            spawnFrbTrailFX(p, x, y, z);
            return;
        }

        if (p.worldObj instanceof net.minecraft.world.WorldServer){
            net.minecraft.world.WorldServer ws = (net.minecraft.world.WorldServer) p.worldObj;

            // Scale up count/spread gently so it doesn't become spammy
            double spread = rad * 0.65D;
            if (spread < 0.06D) spread = 0.06D;
            if (spread > 0.55D) spread = 0.55D;

            int spell = (int) Math.round(3.0D + rad * 18.0D);
            if (spell < 3) spell = 3;
            if (spell > 28) spell = 28;

            int sparks = (int) Math.round(1.0D + rad * 6.0D);
            if (sparks < 1) sparks = 1;
            if (sparks > 12) sparks = 12;

            double speed = 0.012D + rad * 0.018D;
            if (speed > 0.08D) speed = 0.08D;

            // Base magical trail
            ws.func_147487_a("spell", x, y, z, spell, spread, spread, spread, speed);
            ws.func_147487_a("fireworksSpark", x, y, z, sparks, spread * 0.55D, spread * 0.55D, spread * 0.55D, speed * 1.15D);

            // Light-blue tint (reddust uses offsets as RGB on the client)
            int blue = (int) Math.round(2.0D + rad * 10.0D);
            if (blue < 2) blue = 2;
            if (blue > 20) blue = 20;
            ws.func_147487_a("reddust", x, y, z, blue, 0.20D, 0.75D, 1.00D, 0.0D);

            if (rad >= 1.10D){
                int crit = (int) Math.round(rad * 4.0D);
                if (crit > 10) crit = 10;
                ws.func_147487_a("crit", x, y, z, crit, spread * 0.35D, spread * 0.35D, spread * 0.35D, 0.10D);
            }
        }
    }

    /**
     * AoE explosion damage + outward knockback for the Fractured flying blast.
     * @param directHit Optional entity already hit by the projectile (excluded from AoE to avoid double-dipping).
     */
    private static void frbExplode(net.minecraft.entity.player.EntityPlayerMP attacker,
                                   double x, double y, double z,
                                   double radius, float baseDamage,
                                   double kb, net.minecraft.entity.EntityLivingBase directHit){
        if (attacker == null || attacker.worldObj == null) return;
        if (radius <= 0.05D || baseDamage <= 0f) return;

        net.minecraft.world.World w = attacker.worldObj;

        double r = radius;
        if (r > 24D) r = 24D; // sanity cap
        double r2 = r * r;

        net.minecraft.util.AxisAlignedBB bb = net.minecraft.util.AxisAlignedBB.getBoundingBox(
                x - r, y - r, z - r,
                x + r, y + r, z + r
        );

        java.util.List list;
        try {
            list = w.getEntitiesWithinAABB(net.minecraft.entity.EntityLivingBase.class, bb);
        } catch (Throwable t){
            return;
        }
        if (list == null || list.isEmpty()) return;

        for (int i = 0; i < list.size(); i++){
            Object o = list.get(i);
            if (!(o instanceof net.minecraft.entity.EntityLivingBase)) continue;

            net.minecraft.entity.EntityLivingBase t = (net.minecraft.entity.EntityLivingBase) o;
            if (t == attacker) continue;
            if (t == directHit) continue;
            if (t.isDead) continue;

            // distance to entity center
            double cx = t.posX;
            double cy = t.posY + (double)t.height * 0.5D;
            double cz = t.posZ;

            double dx = cx - x;
            double dy = cy - y;
            double dz = cz - z;

            double d2 = dx*dx + dy*dy + dz*dz;
            if (d2 > r2) continue;

            double dist = Math.sqrt(d2);

            // Simple falloff, keep a minimum so the explosion still "feels" like an explosion
            float falloff = (float)(1.0D - (dist / r));
            if (falloff < 0.25f) falloff = 0.25f;
            if (falloff > 1.0f) falloff = 1.0f;

            float dmg = baseDamage * falloff;

            try {
                dealProcDamage((net.minecraft.entity.player.EntityPlayer) attacker, t, dmg, "fracturedBlastAoE");
            } catch (Throwable ignored) {}

            if (kb > 0D){
                try {
                    double inv = (dist > 0.0001D) ? (1.0D / dist) : 1.0D;
                    double nx = dx * inv;
                    double nz = dz * inv;

                    double k = kb * 0.85D * (double)falloff;
                    double vy = 0.12D + (kb * 0.05D) * (double)falloff;

                    t.addVelocity(nx * k, vy, nz * k);
                    t.velocityChanged = true;
                } catch (Throwable ignored) {}
            }
        }
    }

    private static void spawnFrbImpactFX(net.minecraft.entity.player.EntityPlayerMP p, double x, double y, double z){
        if (p == null || p.worldObj == null) return;
        p.worldObj.playSoundEffect(x, y, z, "random.explode", 0.55F, 1.25F);
        if (p.worldObj instanceof net.minecraft.world.WorldServer){
            ((net.minecraft.world.WorldServer) p.worldObj).func_147487_a("crit", x, y, z, 16, 0.25D, 0.25D, 0.25D, 0.12D);
            ((net.minecraft.world.WorldServer) p.worldObj).func_147487_a("spell", x, y, z, 24, 0.35D, 0.35D, 0.35D, 0.04D);
        }
    }

}
