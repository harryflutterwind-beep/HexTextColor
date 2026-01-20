package com.example.examplemod.server;

import com.example.examplemod.api.HexSocketAPI;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
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

    // ─────────────────────────────────────────────────────────────
    // PROC BASE DAMAGE SOURCE
    // Many mods (including DBC) may report tiny vanilla event damage
    // even when "real" damage is huge. Procs should not scale off that.
    // By default we use event damage only when it looks "realistic";
    // otherwise we fall back to fixed base values (configurable).
    //
    // You can override ALL of this at runtime via PROC_DAMAGE_PROVIDER
    // (great for CNPCs/Nashorn scripts or a dedicated DBC bridge).
    // ─────────────────────────────────────────────────────────────
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


    // ─────────────────────────────────────────────────────────────
    // DEV ENV DETECTION (1.7.10-safe)
    // ─────────────────────────────────────────────────────────────
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

    // ─────────────────────────────────────────────────────────────
    // GEM KEYS (must match the key stored by your socket system)
    // ─────────────────────────────────────────────────────────────
    public static volatile String GEM_ENERGIZED_FLAT = "gems/orb_gem_swirly_64";
    public static volatile String GEM_ENERGIZED_ANIM = "gems/orb_gem_swirly_loop";

    // Fire Pills
    public static volatile String PILL_FIRE_FLAT = "gems/pill_fire_textured_64";
    public static volatile String PILL_FIRE_ANIM = "gems/pill_fire_animated_64_anim";



    // Fractured Orbs
    public static volatile String GEM_FRACTURED_FLAT = "gems/orb_gem_fractured_64";
    public static volatile String GEM_FRACTURED_ANIM = "gems/orb_gem_fractured_anim_8f_64x516";
    // ─────────────────────────────────────────────────────────────
    // DEBUG (chat-throttled)
    // ─────────────────────────────────────────────────────────────
    public static volatile boolean DEBUG_PROC = false;
    public static volatile boolean DEBUG_SOCKET_KEYS = false;


    // ─────────────────────────────────────────────────────────────
    // DAMAGE DEBUGGER (chat to attacker)
    // ─────────────────────────────────────────────────────────────
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

    // ─────────────────────────────────────────────────────────────
    // DBC BODY% (health) helper
    // DBC stores BODY in player NBT (commonly jrmcBdy / jrmcBdyF).
    // We try a few keys and fall back to vanilla health%.
    // ─────────────────────────────────────────────────────────────
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

    // ─────────────────────────────────────────────────────────────
    // PROC RELIABILITY FIXES
    // ─────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────
    // TWEAKABLE PROC SETTINGS
    // NOTE: Cooldown caps frequency no matter the chance.
    // For "proc every hit" testing set cooldowns to 0.
    // ─────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────
    // Fire Pill: Fire Punch buff
    // - Proc starts a short window where your hands burn (VFX)
    // - Each melee hit during the window adds extra damage
    // - When the window ends, the last target you hit takes a short DoT, then a fire explosion
    // ─────────────────────────────────────────────────────────────
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


    // ─────────────────────────────────────────────────────────────
    // Fractured: Low-Body Chaos Surge (DBC-friendly)
    // Triggers when your BODY (DBC health) is below a threshold.
    // Chance + damage scale up as body gets lower.
    // Sometimes it backfires and hits YOU instead (true fractured chaos).
    // ─────────────────────────────────────────────────────────────
    public static volatile boolean FRACTURED_ENABLED = true;

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



    // ─────────────────────────────────────────────────────────────
    // Swirl + explode
    // ─────────────────────────────────────────────────────────────
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

    // ─────────────────────────────────────────────────────────────
    // Swirl DoT
    // ─────────────────────────────────────────────────────────────
    public static volatile boolean ENERGIZED_SWIRL_DOT_ENABLED = true;
    public static volatile int     ENERGIZED_SWIRL_DOT_INTERVAL_TICKS = 4;
    public static volatile float   ENERGIZED_SWIRL_DOT_DAMAGE_SCALE = 0.12f;
    public static volatile float   ENERGIZED_SWIRL_DOT_BONUS_DAMAGE  = 1.2f;
    public static volatile boolean ENERGIZED_SWIRL_DOT_RESET_IFRAMES = true;
    public static volatile boolean ENERGIZED_SWIRL_DOT_AFFECT_PLAYERS = true;

    // ─────────────────────────────────────────────────────────────
    // ANIM overrides (stronger/longer)
    // ─────────────────────────────────────────────────────────────
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

    // ─────────────────────────────────────────────────────────────
    // Internal NBT keys (stored on the TARGET while swirling)
    // ─────────────────────────────────────────────────────────────

    // ─────────────────────────────────────────────────────────────
    // Heal buff keys (stored on the PLAYER while healing)
    // ─────────────────────────────────────────────────────────────
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

    // ─────────────────────────────────────────────────────────────
    // Rainbow Rush keys (stored on the TARGET while the rush sequence runs)
    // ─────────────────────────────────────────────────────────────
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

    // ─────────────────────────────────────────────────────────────
    // Fire Punch keys (stored on the ATTACKER while the fire-hands window is active)
    // ─────────────────────────────────────────────────────────────
    private static final String FP_KEY_END     = "HexFirePunch_End";
    private static final String FP_KEY_NEXT    = "HexFirePunch_Next";
    private static final String FP_KEY_ANIM    = "HexFirePunch_Anim";
    private static final String FP_KEY_LASTTGT = "HexFirePunch_LastTgt";
    private static final String FP_KEY_FIN     = "HexFirePunch_Fin";

    // ─────────────────────────────────────────────────────────────
    // Fire DoT keys (stored on the TARGET for the finisher)
    // ─────────────────────────────────────────────────────────────
    private static final String FD_KEY_END     = "HexFireDot_End";
    private static final String FD_KEY_NEXT    = "HexFireDot_Next";
    private static final String FD_KEY_OWNER   = "HexFireDot_OwnerId";
    private static final String FD_KEY_DMG     = "HexFireDot_Dmg";
    private static final String FD_KEY_INT     = "HexFireDot_Int";
    private static final String FD_KEY_EXDMG   = "HexFireDot_ExDmg";
    private static final String FD_KEY_EXRAD   = "HexFireDot_ExRad";
    private static final String FD_KEY_ANIM    = "HexFireDot_Anim";


    // ─────────────────────────────────────────────────────────────
    // API (optional external tuning)
    // ─────────────────────────────────────────────────────────────
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

    // ─────────────────────────────────────────────────────────────
    // EVENTS: some mods fire Attack but not Hurt (or vice-versa)
    // We listen to both and de-dupe per tick.
    // ─────────────────────────────────────────────────────────────

    @SubscribeEvent
    public void onLivingAttack(LivingAttackEvent e){
        if (e == null || e.entityLiving == null || e.source == null) return;
        handleHit(e.source, e.entityLiving, e.ammount, "Attack");
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent e){
        if (e == null || e.entityLiving == null || e.source == null) return;
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

    // ─────────────────────────────────────────────────────────────
    // FLAT PROC: Rainbow Shockwave (AoE)
    // ─────────────────────────────────────────────────────────────
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

    // ─────────────────────────────────────────────────────────────
    // ANIM PROC: Rainbow Chain Arc
    // ─────────────────────────────────────────────────────────────
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

    // ─────────────────────────────────────────────────────────────
    // Heal proc: short regen on attacker (no script needed)
    // ─────────────────────────────────────────────────────────────
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

    // ─────────────────────────────────────────────────────────────
    // Fractured: Low-Body Chaos Surge
    // ─────────────────────────────────────────────────────────────
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

    // ─────────────────────────────────────────────────────────────
    // DBC heal bridge (reflection): DBCDataUniversal.get(player).stats.restoreHealthPercent(pct)
    // ─────────────────────────────────────────────────────────────
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



    // ─────────────────────────────────────────────────────────────
    // Fire Pill: Fire Punch
    // ─────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────
    // SWIRL UPDATE (server tick while a target is marked)
    // ─────────────────────────────────────────────────────────────
    @SubscribeEvent
    public void onLivingUpdate(LivingUpdateEvent e){
        if (e == null || e.entityLiving == null) return;

        EntityLivingBase ent = e.entityLiving;
        World w = ent.worldObj;
        if (w == null || w.isRemote) return;

        NBTTagCompound data = ent.getEntityData();
        if (data == null) return;

        long now = serverNow(w);

        // Heal buff update (independent of swirl; works in any dimension)
        if (ent instanceof EntityPlayer){
            tickHealBuff((EntityPlayer) ent, data, w, now);
            tickFirePunchBuff((EntityPlayer) ent, data, w, now);
        }

        // Rainbow Rush update (independent of swirl)
        tickRainbowRush(ent, data, w, now);
        tickFireDot(ent, data, w, now);

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



    // ─────────────────────────────────────────────────────────────
    // RAINBOW RUSH (sequence proc)
    // ─────────────────────────────────────────────────────────────

    /** Returns true if the rush was started. */

    // ─────────────────────────────────────────────────────────────
    // Variant: Rainbow Fist (instant mega punch)
    // ─────────────────────────────────────────────────────────────
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

    // ─────────────────────────────────────────────────────────────
    // COOLDOWN / RNG
    // ─────────────────────────────────────────────────────────────
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

    // ─────────────────────────────────────────────────────────────
    // VFX helpers
    // ─────────────────────────────────────────────────────────────
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

    // ─────────────────────────────────────────────────────────────
    // Knockback
    // ─────────────────────────────────────────────────────────────
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


    // ─────────────────────────────────────────────────────────────
    // Socket detection (reflection, supports older/newer HexSocketAPI)
    // ─────────────────────────────────────────────────────────────
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

    // ─────────────────────────────────────────────────────────────
    // Debug helpers
    // ─────────────────────────────────────────────────────────────
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
}
