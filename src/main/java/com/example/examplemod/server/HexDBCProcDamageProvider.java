package com.example.examplemod.server;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.nbt.NBTTagCompound;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Uses NPCDBC's DBCData (via reflection) to compute a reliable proc base-damage
 * even when Forge event damage is tiny (common with DBC/JRMCore).
 *
 * If NPCDBC isn't present, returns -1 so HexOrbEffectsController falls back to defaults.
 */
public final class HexDBCProcDamageProvider implements HexOrbEffectsController.ProcDamageProvider {

    // ── TUNING (adjust these to fit your server's stat scale) ────────────────
    public static volatile float BASE_ADD = 2500f;

    // "How much STR contributes" to proc base damage:
    // Example: STR=10,000 => +1500 base at 0.15
    public static volatile float STR_MULT = 0.15f;

    // Optional extra scaling from other stats (set to 0 if you don't want them)
    public static volatile float DEX_MULT = 0.00f;
    public static volatile float SPI_MULT = 0.00f;

    // Release factor (Release is usually 0..100)
    public static volatile float RELEASE_MIN_FACTOR = 0.30f; // at 0 release
    public static volatile float RELEASE_MAX_FACTOR = 1.50f; // at 100 release

    // Anim gem slight boost (optional)
    public static volatile float ANIM_GEM_BASE_MULT = 1.10f;

    // Clamp safety
    public static volatile float MIN_BASE = 2500f;
    public static volatile float MAX_BASE = 2500000f;

    // Uses stats.getCurrentMulti() if available (moderated via sqrt)
    public static volatile boolean USE_CURRENT_MULTI_SQRT = true;
    public static volatile float MAX_CURRENT_MULTI = 1000f;

    // Also consider eventDamage if it's already large/meaningful
    public static volatile boolean TAKE_MAX_WITH_EVENT_DAMAGE = true;

    // ── Reflection caches ───────────────────────────────────────────────────
    private static boolean LOOKED_UP = false;
    private static Method  M_GET_DBCDATA;     // DBCDataUniversal.get(player)
    private static Field   F_STR, F_DEX, F_SPI, F_WIL;
    private static Field   F_RELEASE;
    private static Field   F_STATS;           // dbcData.stats
    private static Method  M_GET_CURRENT_MULTI; // stats.getCurrentMulti()

    private static void ensureReflection() {
        if (LOOKED_UP) return;
        LOOKED_UP = true;

        try {
            Class<?> cUniversal = Class.forName("kamkeel.npcdbc.data.dbcdata.DBCDataUniversal");
            // Prefer get(EntityPlayer); fallback to getData(EntityPlayer) if needed
            try {
                M_GET_DBCDATA = cUniversal.getMethod("get", EntityPlayer.class);
            } catch (Throwable ignored) {
                M_GET_DBCDATA = cUniversal.getMethod("getData", EntityPlayer.class);
            }

            Class<?> cDBCData = Class.forName("kamkeel.npcdbc.data.dbcdata.DBCData");
            F_STR     = cDBCData.getField("STR");
            F_DEX     = cDBCData.getField("DEX");
            F_SPI     = cDBCData.getField("SPI");
            try { F_WIL = cDBCData.getField("WIL"); } catch (Throwable ignored) { F_WIL = null; }
            F_RELEASE = cDBCData.getField("Release");
            F_STATS   = cDBCData.getField("stats");

            Class<?> cStats = Class.forName("kamkeel.npcdbc.data.dbcdata.DBCDataStats");
            M_GET_CURRENT_MULTI = cStats.getMethod("getCurrentMulti");
        } catch (Throwable t) {
            // If any of this fails, we'll just return -1 at runtime (fallback to defaults).
            M_GET_DBCDATA = null;
        }
    }

    private static float clamp(float v, float lo, float hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    @Override
    public float getBaseDamage(EntityPlayer attacker, EntityLivingBase target,
                               HexOrbEffectsController.ProcStage stage,
                               float eventDamage, boolean isAnimGem) {

        if (attacker == null) return -1f;

        ensureReflection();
        if (M_GET_DBCDATA == null) return -1f;

        try {
            Object dbc = M_GET_DBCDATA.invoke(null, attacker);
            if (dbc == null) return -1f;

            int str = (F_STR != null) ? ((Number) F_STR.get(dbc)).intValue() : 0;
            int dex = (F_DEX != null) ? ((Number) F_DEX.get(dbc)).intValue() : 0;
            int spi = (F_SPI != null) ? ((Number) F_SPI.get(dbc)).intValue() : 0;

            int relInt = 0;
            if (F_RELEASE != null) {
                Object r = F_RELEASE.get(dbc); // byte
                if (r instanceof Number) relInt = ((Number) r).intValue() & 0xFF;
            }
            relInt = Math.max(0, Math.min(100, relInt));

            // Base from stats
            float base = BASE_ADD
                    + (str * STR_MULT)
                    + (dex * DEX_MULT)
                    + (spi * SPI_MULT);

            // Release factor (0..100 -> min..max)
            float rf = RELEASE_MIN_FACTOR + (relInt / 100f) * (RELEASE_MAX_FACTOR - RELEASE_MIN_FACTOR);
            base *= rf;

            // Optional: incorporate "current multi" (moderated)
            if (USE_CURRENT_MULTI_SQRT && F_STATS != null && M_GET_CURRENT_MULTI != null) {
                Object stats = F_STATS.get(dbc);
                if (stats != null) {
                    Object m = M_GET_CURRENT_MULTI.invoke(stats);
                    if (m instanceof Number) {
                        double cur = ((Number) m).doubleValue();
                        if (cur > 1.0) {
                            cur = Math.min(cur, (double) MAX_CURRENT_MULTI);
                            base *= (float) Math.sqrt(cur); // moderated so it won't explode
                        }
                    }
                }
            }

            if (isAnimGem) base *= ANIM_GEM_BASE_MULT;

            if (TAKE_MAX_WITH_EVENT_DAMAGE && eventDamage > 0f && !Float.isNaN(eventDamage) && !Float.isInfinite(eventDamage)) {
                base = Math.max(base, eventDamage);
            }

            if (Float.isNaN(base) || Float.isInfinite(base)) return -1f;

            base = clamp(base, MIN_BASE, MAX_BASE);
            return base;

        } catch (Throwable ignored) {
            return -1f;
        }
    }


    /**
     * Returns "effective" WillPower for scaling abilities:
     * - Prefers NPCDBC: WIL (base) * stats.getCurrentMulti() (form/release/buffs)
     * - Falls back to Forge attributes: "dbc.WillPower" and optional "dbc.WillPower.Multi"
     * - Last resort: a few common NBT keys if present
     *
     * This is designed to match the DBC sheet "Modified" value (including form multipliers),
     * without touching any GUI code.
     */
    public static double getWillPowerEffective(EntityPlayer player) {
        if (player == null) return 0D;

        // A) Attribute path (some packs expose modified will here already)
        double willAttr = getAttr(player, "dbc.WillPower");
        double willMultiAttr = getAttr(player, "dbc.WillPower.Multi");

        double willAttrEff = willAttr;
        // If it looks like the "original" value, apply the multiplier.
        if (willAttrEff > 0D && willMultiAttr > 1.05D) {
            if (willAttrEff < 50000D) willAttrEff = willAttrEff * willMultiAttr;
        }

        // B) NPCDBC path: WIL * currentMulti (form/release/other multipliers)
        double wilBase = 0D;
        double curMulti = 1D;

        ensureReflection();
        if (M_GET_DBCDATA != null) {
            try {
                Object dbc = M_GET_DBCDATA.invoke(null, player);
                if (dbc != null) {
                    // Base WIL
                    if (F_WIL != null) {
                        Object v = F_WIL.get(dbc);
                        if (v instanceof Number) wilBase = ((Number) v).doubleValue();
                    }

                    // Current multi
                    if (F_STATS != null && M_GET_CURRENT_MULTI != null) {
                        Object stats = F_STATS.get(dbc);
                        if (stats != null) {
                            Object m = M_GET_CURRENT_MULTI.invoke(stats);
                            if (m instanceof Number) {
                                double cm = ((Number) m).doubleValue();
                                if (!Double.isNaN(cm) && !Double.isInfinite(cm)) {
                                    if (cm < 0D) cm = 0D;
                                    if (cm > (double) MAX_CURRENT_MULTI) cm = (double) MAX_CURRENT_MULTI;
                                    curMulti = Math.max(1D, cm);
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        double wilNpcdbcEff = (wilBase > 0D) ? (wilBase * Math.max(1D, curMulti)) : 0D;

        // C) NBT fallback (only if present in your environment)
        double wilNbt = 0D;
        try {
            NBTTagCompound ed = player.getEntityData();
            if (ed != null) {
                if (ed.hasKey("jrmcWil")) wilNbt = (double) ed.getInteger("jrmcWil");
                else if (ed.hasKey("jrmcWIL")) wilNbt = (double) ed.getInteger("jrmcWIL");
                else if (ed.hasKey("jrmcWill")) wilNbt = (double) ed.getInteger("jrmcWill");
            }
        } catch (Throwable ignored) {}

        double best = 0D;
        if (willAttrEff > best) best = willAttrEff;
        if (wilNpcdbcEff > best) best = wilNpcdbcEff;
        if (wilNbt > best) best = wilNbt;

        if (Double.isNaN(best) || Double.isInfinite(best)) return 0D;
        return best;
    }


    /**
     * Returns "effective" Strength for scaling abilities:
     * - Prefers NPCDBC: STR (base) * stats.getCurrentMulti() (form/release/buffs)
     * - Falls back to Forge attributes: "dbc.Strength" and optional "dbc.Strength.Multi"
     * - Last resort: a few common NBT keys if present
     *
     * This aims to match the DBC sheet "Modified" value (including form multipliers).
     */
    public static double getStrengthEffective(EntityPlayer player) {
        if (player == null) return 0D;

        // A) Attribute path
        double strAttr = getAttr(player, "dbc.Strength");
        double strMultiAttr = getAttr(player, "dbc.Strength.Multi");

        double strAttrEff = strAttr;
        // If it looks like the "original" value, apply the multiplier.
        if (strAttrEff > 0D && strMultiAttr > 1.05D) {
            if (strAttrEff < 50000D) strAttrEff = strAttrEff * strMultiAttr;
        }

        // B) NPCDBC path: STR * currentMulti
        double strBase = 0D;
        double curMulti = 1D;

        ensureReflection();
        if (M_GET_DBCDATA != null) {
            try {
                Object dbc = M_GET_DBCDATA.invoke(null, player);
                if (dbc != null) {
                    if (F_STR != null) {
                        Object v = F_STR.get(dbc);
                        if (v instanceof Number) strBase = ((Number) v).doubleValue();
                    }

                    if (F_STATS != null && M_GET_CURRENT_MULTI != null) {
                        Object stats = F_STATS.get(dbc);
                        if (stats != null) {
                            Object m = M_GET_CURRENT_MULTI.invoke(stats);
                            if (m instanceof Number) {
                                double cm = ((Number) m).doubleValue();
                                if (!Double.isNaN(cm) && !Double.isInfinite(cm)) {
                                    if (cm < 0D) cm = 0D;
                                    if (cm > (double) MAX_CURRENT_MULTI) cm = (double) MAX_CURRENT_MULTI;
                                    curMulti = Math.max(1D, cm);
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        double strNpcdbcEff = (strBase > 0D) ? (strBase * Math.max(1D, curMulti)) : 0D;

        // C) NBT fallback
        double strNbt = 0D;
        try {
            NBTTagCompound ed = player.getEntityData();
            if (ed != null) {
                if (ed.hasKey("jrmcStr")) strNbt = (double) ed.getInteger("jrmcStr");
                else if (ed.hasKey("jrmcSTR")) strNbt = (double) ed.getInteger("jrmcSTR");
                else if (ed.hasKey("jrmcStrength")) strNbt = (double) ed.getInteger("jrmcStrength");
            }
        } catch (Throwable ignored) {}

        double best = 0D;
        if (strAttrEff > best) best = strAttrEff;
        if (strNpcdbcEff > best) best = strNpcdbcEff;
        if (strNbt > best) best = strNbt;

        if (Double.isNaN(best) || Double.isInfinite(best)) return 0D;
        return best;
    }
    private static double getAttr(EntityPlayer p, String name) {
        if (p == null || name == null) return 0D;
        try {
            IAttributeInstance inst = p.getAttributeMap().getAttributeInstanceByName(name);
            if (inst == null) return 0D;
            double v = inst.getAttributeValue();
            if (Double.isNaN(v) || Double.isInfinite(v)) return 0D;
            return v;
        } catch (Throwable ignored) {
            return 0D;
        }
    }

}
