package com.example.examplemod.server;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

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
    private static Field   F_STR, F_DEX, F_SPI;
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
}
