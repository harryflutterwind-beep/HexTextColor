package com.example.examplemod.server;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.nbt.NBTTagCompound;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Server-safe DBC stat reader with fallbacks.
 *
 * Goal:
 * - One place to read STR/DEX/CON/WIL/MND/SPI reliably on dedicated servers.
 * - Prefer NPCDBC DBCDataUniversal reflection when available.
 * - Fallback to Forge attribute-map names (dbc.*) when present.
 * - Last resort: NBT keys in player entity data (PlayerPersisted) if any.
 *
 * Note: this class does NOT apply damage. It's just stat reading + scaling helpers.
 */
public final class HexPlayerStats {

    private HexPlayerStats() {}

    // -------------------------------------------------------------------------
    // Snapshot
    // -------------------------------------------------------------------------

    public static final class Snapshot {
        public int str, dex, con, wil, mnd, spi;
        public int release;          // 0..100
        public double currentMulti;  // >= 1.0
        public String source;        // brief label (npcdbc/attr/nbt)

        public int getStat(String key) {
            if ("str".equalsIgnoreCase(key)) return str;
            if ("dex".equalsIgnoreCase(key)) return dex;
            if ("con".equalsIgnoreCase(key)) return con;
            if ("wil".equalsIgnoreCase(key)) return wil;
            if ("mnd".equalsIgnoreCase(key)) return mnd;
            if ("spi".equalsIgnoreCase(key)) return spi;
            return 0;
        }

        public double getStatEffective(String key) {
            return ((double) getStat(key)) * Math.max(1.0D, currentMulti);
        }
    }

    // -------------------------------------------------------------------------
    // NPCDBC reflection (preferred)
    // -------------------------------------------------------------------------

    private static boolean LOOKED_UP = false;
    private static Method  M_GET_DBCDATA;

    private static Field F_STR, F_DEX, F_CON, F_WIL, F_MND, F_SPI;
    private static Field F_RELEASE;
    private static Field F_STATS;
    private static Method M_GET_CURRENT_MULTI;

    private static void ensureReflection() {
        if (LOOKED_UP) return;
        LOOKED_UP = true;

        try {
            Class<?> cUniversal = Class.forName("kamkeel.npcdbc.data.dbcdata.DBCDataUniversal");
            try {
                M_GET_DBCDATA = cUniversal.getMethod("get", EntityPlayer.class);
            } catch (Throwable ignored) {
                M_GET_DBCDATA = cUniversal.getMethod("getData", EntityPlayer.class);
            }

            Class<?> cDBCData = Class.forName("kamkeel.npcdbc.data.dbcdata.DBCData");
            // These field names match what NPCDBC exposes in most builds; missing ones are optional.
            F_STR = safeGetField(cDBCData, "STR");
            F_DEX = safeGetField(cDBCData, "DEX");
            F_CON = safeGetField(cDBCData, "CON");
            F_WIL = safeGetField(cDBCData, "WIL");
            F_MND = safeGetField(cDBCData, "MND");
            F_SPI = safeGetField(cDBCData, "SPI");

            F_RELEASE = safeGetField(cDBCData, "Release");
            F_STATS   = safeGetField(cDBCData, "stats");

            // currentMulti is on DBCDataStats
            Class<?> cStats = Class.forName("kamkeel.npcdbc.data.dbcdata.DBCDataStats");
            M_GET_CURRENT_MULTI = cStats.getMethod("getCurrentMulti");

        } catch (Throwable t) {
            M_GET_DBCDATA = null; // NPCDBC not present or changed; we'll fall back.
        }
    }

    private static Field safeGetField(Class<?> c, String name) {
        try { return c.getField(name); } catch (Throwable ignored) { return null; }
    }

    private static int getIntField(Field f, Object obj) throws Exception {
        if (f == null || obj == null) return 0;
        Object v = f.get(obj);
        if (v instanceof Number) return ((Number) v).intValue();
        return 0;
    }

    private static int getRelease(Object dbc) throws Exception {
        if (F_RELEASE == null || dbc == null) return 0;
        Object r = F_RELEASE.get(dbc); // byte in many builds
        int rel = 0;
        if (r instanceof Number) rel = ((Number) r).intValue() & 0xFF;
        if (rel < 0) rel = 0;
        if (rel > 100) rel = 100;
        return rel;
    }

    private static double getCurrentMulti(Object dbc) throws Exception {
        if (F_STATS == null || M_GET_CURRENT_MULTI == null || dbc == null) return 1.0D;
        Object stats = F_STATS.get(dbc);
        if (stats == null) return 1.0D;
        Object m = M_GET_CURRENT_MULTI.invoke(stats);
        if (m instanceof Number) {
            double d = ((Number) m).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d) || d < 1.0D) return 1.0D;
            return d;
        }
        return 1.0D;
    }

    // -------------------------------------------------------------------------
    // Attribute / NBT fallbacks
    // -------------------------------------------------------------------------

    private static double getAttr(EntityPlayer p, String name) {
        try {
            if (p == null) return 0D;
            IAttributeInstance inst = p.getAttributeMap().getAttributeInstanceByName(name);
            if (inst == null) return 0D;
            return inst.getAttributeValue();
        } catch (Throwable ignored) {
            return 0D;
        }
    }

    private static int toInt(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) return 0;
        if (d < 0D) d = 0D;
        if (d > (double) Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) Math.round(d);
    }

    private static int readBestNumeric(NBTTagCompound tag, String[] keys) {
        if (tag == null || keys == null) return 0;
        for (int i = 0; i < keys.length; i++) {
            String k = keys[i];
            if (k == null) continue;
            try {
                if (tag.hasKey(k)) {
                    // NBT type varies per pack
                    return toInt(tag.getDouble(k));
                }
            } catch (Throwable ignored) {}
        }
        return 0;
    }

    private static final String TAG_PLAYER_PERSISTED = "PlayerPersisted";
    private static final String[] STR_KEYS = new String[]{ "jrmcStr", "jrmcSTR", "jrmcStrength" };
    private static final String[] DEX_KEYS = new String[]{ "jrmcDex", "jrmcDEX", "jrmcDexterity" };
    private static final String[] CON_KEYS = new String[]{ "jrmcCon", "jrmcCON", "jrmcConstitution" };
    private static final String[] WIL_KEYS = new String[]{ "jrmcWil", "jrmcWIL", "jrmcWill" };
    private static final String[] MND_KEYS = new String[]{ "jrmcMnd", "jrmcMND", "jrmcMind" };
    private static final String[] SPI_KEYS = new String[]{ "jrmcSpi", "jrmcSPI", "jrmcSpirit" };

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Get a server-safe snapshot. Uses NPCDBC reflection if available, else falls back to attributes/nbt.
     */
    public static Snapshot snapshot(EntityPlayer p) {
        Snapshot s = new Snapshot();
        if (p == null) { s.source = "none"; return s; }

        // 1) NPCDBC (best)
        ensureReflection();
        if (M_GET_DBCDATA != null) {
            try {
                Object dbc = M_GET_DBCDATA.invoke(null, p);
                if (dbc != null) {
                    s.str = getIntField(F_STR, dbc);
                    s.dex = getIntField(F_DEX, dbc);
                    s.con = getIntField(F_CON, dbc);
                    s.wil = getIntField(F_WIL, dbc);
                    s.mnd = getIntField(F_MND, dbc);
                    s.spi = getIntField(F_SPI, dbc);
                    s.release = getRelease(dbc);
                    s.currentMulti = getCurrentMulti(dbc);
                    s.source = "npcdbc";
                    return s;
                }
            } catch (Throwable ignored) {
                // fall through
            }
        }

        // 2) Forge attribute map (some packs expose these reliably)
        // We also read the optional ".Multi" attributes and apply when they look like base+multi split.
        int str = toInt(getAttr(p, "dbc.Strength"));
        int dex = toInt(getAttr(p, "dbc.Dexterity"));
        int con = toInt(getAttr(p, "dbc.Constitution"));
        int wil = toInt(getAttr(p, "dbc.WillPower"));
        int mnd = toInt(getAttr(p, "dbc.Mind"));
        int spi = toInt(getAttr(p, "dbc.Spirit"));

        double strM = getAttr(p, "dbc.Strength.Multi");
        double dexM = getAttr(p, "dbc.Dexterity.Multi");
        double conM = getAttr(p, "dbc.Constitution.Multi");
        double wilM = getAttr(p, "dbc.WillPower.Multi");
        double mndM = getAttr(p, "dbc.Mind.Multi");
        double spiM = getAttr(p, "dbc.Spirit.Multi");

        // If value looks like a base stat (not already multiplied), apply multi when meaningful
        if (str > 0 && str < 50000 && strM > 1.01D) str = toInt(str * strM);
        if (dex > 0 && dex < 50000 && dexM > 1.01D) dex = toInt(dex * dexM);
        if (con > 0 && con < 50000 && conM > 1.01D) con = toInt(con * conM);
        if (wil > 0 && wil < 50000 && wilM > 1.01D) wil = toInt(wil * wilM);
        if (mnd > 0 && mnd < 50000 && mndM > 1.01D) mnd = toInt(mnd * mndM);
        if (spi > 0 && spi < 50000 && spiM > 1.01D) spi = toInt(spi * spiM);

        if (str + dex + con + wil + mnd + spi > 0) {
            s.str = str; s.dex = dex; s.con = con; s.wil = wil; s.mnd = mnd; s.spi = spi;
            s.release = 0;
            s.currentMulti = 1.0D;
            s.source = "attr";
            return s;
        }

        // 3) NBT last resort
        try {
            NBTTagCompound ed = p.getEntityData();
            NBTTagCompound pp = null;
            if (ed != null && ed.hasKey(TAG_PLAYER_PERSISTED, 10)) {
                pp = ed.getCompoundTag(TAG_PLAYER_PERSISTED);
            }
            s.str = Math.max(readBestNumeric(ed, STR_KEYS), readBestNumeric(pp, STR_KEYS));
            s.dex = Math.max(readBestNumeric(ed, DEX_KEYS), readBestNumeric(pp, DEX_KEYS));
            s.con = Math.max(readBestNumeric(ed, CON_KEYS), readBestNumeric(pp, CON_KEYS));
            s.wil = Math.max(readBestNumeric(ed, WIL_KEYS), readBestNumeric(pp, WIL_KEYS));
            s.mnd = Math.max(readBestNumeric(ed, MND_KEYS), readBestNumeric(pp, MND_KEYS));
            s.spi = Math.max(readBestNumeric(ed, SPI_KEYS), readBestNumeric(pp, SPI_KEYS));
            s.release = 0;
            s.currentMulti = 1.0D;
            s.source = "nbt";
            return s;
        } catch (Throwable ignored) {}

        s.source = "none";
        return s;
    }

    // -------------------------------------------------------------------------
    // Scaling helpers (so orb controller can just call one line)
    // -------------------------------------------------------------------------

    /** Classic: (baseAdd + statEff * statMult) * releaseFactor * (sqrt(currentMulti) if enabled) */
    public static double scaleFromStat(EntityPlayer p, String statKey,
                                       double baseAdd, double statMult,
                                       boolean applyRelease, boolean sqrtCurrentMulti) {

        Snapshot s = snapshot(p);
        double statEff = s.getStatEffective(statKey);

        double out = baseAdd + (statEff * statMult);

        if (applyRelease) {
            // release 0..100 => 0.30..1.50 (match your proc provider defaults)
            double rel = Math.max(0D, Math.min(100D, (double) s.release));
            double rf = 0.30D + (rel / 100D) * (1.50D - 0.30D);
            out *= rf;
        }

        if (sqrtCurrentMulti) {
            double cm = Math.max(1.0D, s.currentMulti);
            out *= Math.sqrt(cm);
        }

        if (Double.isNaN(out) || Double.isInfinite(out)) return 0D;
        if (out < 0D) out = 0D;
        return out;
    }
}
