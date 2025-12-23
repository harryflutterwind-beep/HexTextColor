// src/main/java/com/example/examplemod/ModConfig.java
package com.example.examplemod;

import net.minecraftforge.common.config.Configuration;
import java.io.File;

public final class ModConfig {

    // Exposed so GuiConfig can read categories
    public static Configuration cfg;

    // ---------- GENERAL ----------
    public static boolean enableBeams = true;
    public static int     scanRadius  = 72;
    public static int     maxDraw     = 96;

    // ---------- VISUAL ----------
    public static double opacity        = 0.70; // 0..1
    public static double baseRadius     = 0.22;
    public static double thickTierBoost = 0.06;
    public static double coneTaper      = 0.82;
    public static int    coneSides      = 16;
    public static double brightness     = 1.10;

    public static double twistLegendary = 0.10;
    public static double twistEpic      = 0.12;
    public static double twistRare      = 0.08;
    public static double twistUncommon  = 0.06;
    public static double twistPearl     = 0.08;
    public static double twistSeraph    = 0.06;
    public static double twistEtech     = 0.12;
    public static double twistGlitch    = 0.10;

    // ---------- RAINBOW ----------
    public static double rbwSpeed     = 0.010; // effervescent
    public static double rbwSpeedPlus = 0.020; // effervescent_
    public static int    ringCount    = 3;

    // ---------- COLOR (beams; hex strings bottom→top) ----------
    public static String grad_common_bot    = "#FFFFFF", grad_common_top    = "#DADADA";
    public static String grad_uncommon_bot  = "#006600", grad_uncommon_top  = "#33FF33";
    public static String grad_rare_bot      = "#7A2CE0", grad_rare_top      = "#A64DFF";
    public static String grad_epic_bot      = "#3A7BFF", grad_epic_top      = "#1ED1FF";
    public static String grad_legend_bot    = "#FFC52A", grad_legend_top    = "#FFF6A0";
    public static String grad_pearl_bot     = "#40E0D0", grad_pearl_top     = "#7FFFD4";
    public static String grad_seraph_bot    = "#FF66C4", grad_seraph_top    = "#FFB3E6";
    public static String grad_black_bot     = "#000000", grad_black_top     = "#141414";
    public static String grad_etech_bot     = "#FF2BA6", grad_etech_top     = "#FF2BA6";
    public static String grad_glitch_bot    = "#FFB6DF", grad_glitch_top    = "#FFD6ED";

    // ---------- OVERLAY (inventory/hotbar frames) ----------
    public static boolean overlayEnableInventory = true;
    public static boolean overlayEnableHotbar    = true;
    public static int     overlayBorderAlpha     = 0x90; // 0..255
    public static int     overlayFillAlpha       = 0x50; // 0..255
    public static int     overlayStripHeight     = 2;    // pixels

    // Per-rarity overlay colors (hex). Used for the slot border/strip.
    public static String overlay_common   = "#FFFFFF";
    public static String overlay_uncommon = "#33FF33";
    public static String overlay_rare     = "#A64DFF";
    public static String overlay_epic     = "#3A7BFF";
    public static String overlay_legend   = "#FFC845";
    public static String overlay_pearl    = "#00F0E0";
    public static String overlay_seraph   = "#FF66CC";
    public static String overlay_etech    = "#FF2BA6";
    public static String overlay_glitch   = "#FFC6E6";
    public static String overlay_black    = "#000000";

    // ---------- API ----------
    public static void load(File file) {
        cfg = new Configuration(file);
        cfg.load();
        syncFromConfig();
        if (cfg.hasChanged()) cfg.save();
    }

    /** Read all values from cfg into fields (called on load & on GUI “Done”). */
    public static void syncFromConfig() {
        // general
        enableBeams = cfg.getBoolean("enableBeams", "general", enableBeams, "Turn item beams on/off");
        scanRadius  = cfg.getInt("scanRadius", "general", scanRadius, 8, 256, "Radius to scan for dropped items");
        maxDraw     = cfg.getInt("maxDraw", "general", maxDraw, 8, 512, "Max beams to draw per frame");

        // visual
        opacity        = cfg.getFloat("opacity", "visual", (float)opacity, 0.05f, 1.0f, "Beam opacity (alpha 0..1)");
        baseRadius     = cfg.getFloat("baseRadius", "visual", (float)baseRadius, 0.05f, 1.0f, "Base beam radius");
        thickTierBoost = cfg.getFloat("thickTierBoost", "visual", (float)thickTierBoost, 0f, 1.0f, "Extra radius for tall beams");
        coneTaper      = cfg.getFloat("coneTaper", "visual", (float)coneTaper, 0.50f, 1.0f, "Cone taper (<1 = cone)");
        coneSides      = cfg.getInt("coneSides", "visual", coneSides, 3, 48, "Cone sides (roundness)");
        brightness     = cfg.getFloat("brightness", "visual", (float)brightness, 0.25f, 2.0f, "Global brightness multiplier");

        twistLegendary = cfg.getFloat("twistLegendary", "visual", (float)twistLegendary, 0f, 1f, "Legendary twist turns");
        twistEpic      = cfg.getFloat("twistEpic", "visual", (float)twistEpic, 0f, 1f, "Epic twist turns");
        twistRare      = cfg.getFloat("twistRare", "visual", (float)twistRare, 0f, 1f, "Rare twist turns");
        twistUncommon  = cfg.getFloat("twistUncommon", "visual", (float)twistUncommon, 0f, 1f, "Uncommon twist turns");
        twistPearl     = cfg.getFloat("twistPearl", "visual", (float)twistPearl, 0f, 1f, "Pearlescent twist turns");
        twistSeraph    = cfg.getFloat("twistSeraph", "visual", (float)twistSeraph, 0f, 1f, "Seraph twist turns");
        twistEtech     = cfg.getFloat("twistEtech", "visual", (float)twistEtech, 0f, 1f, "E-Tech twist turns");
        twistGlitch    = cfg.getFloat("twistGlitch", "visual", (float)twistGlitch, 0f, 1f, "Glitch twist turns");

        // rainbow
        rbwSpeed     = cfg.getFloat("rainbowSpeed",     "rainbow", (float)rbwSpeed,     0.001f, 0.100f, "Effervescent rainbow speed");
        rbwSpeedPlus = cfg.getFloat("rainbowSpeedPlus", "rainbow", (float)rbwSpeedPlus, 0.001f, 0.100f, "Effervescent_ rainbow speed");
        ringCount    = cfg.getInt("ringCount", "rainbow", ringCount, 0, 8, "Orbiting rings for effervescent_");

        // color (beams)
        grad_common_bot    = cfg.getString("common_bottom",      "color", grad_common_bot,    "Hex #RRGGBB");
        grad_common_top    = cfg.getString("common_top",         "color", grad_common_top,    "Hex #RRGGBB");
        grad_uncommon_bot  = cfg.getString("uncommon_bottom",    "color", grad_uncommon_bot,  "Hex #RRGGBB");
        grad_uncommon_top  = cfg.getString("uncommon_top",       "color", grad_uncommon_top,  "Hex #RRGGBB");
        grad_rare_bot      = cfg.getString("rare_bottom",        "color", grad_rare_bot,      "Hex #RRGGBB");
        grad_rare_top      = cfg.getString("rare_top",           "color", grad_rare_top,      "Hex #RRGGBB");
        grad_epic_bot      = cfg.getString("epic_bottom",        "color", grad_epic_bot,      "Hex #RRGGBB");
        grad_epic_top      = cfg.getString("epic_top",           "color", grad_epic_top,      "Hex #RRGGBB");
        grad_legend_bot    = cfg.getString("legendary_bottom",   "color", grad_legend_bot,    "Hex #RRGGBB");
        grad_legend_top    = cfg.getString("legendary_top",      "color", grad_legend_top,    "Hex #RRGGBB");
        grad_pearl_bot     = cfg.getString("pearlescent_bottom", "color", grad_pearl_bot,     "Hex #RRGGBB");
        grad_pearl_top     = cfg.getString("pearlescent_top",    "color", grad_pearl_top,     "Hex #RRGGBB");
        grad_seraph_bot    = cfg.getString("seraph_bottom",      "color", grad_seraph_bot,    "Hex #RRGGBB");
        grad_seraph_top    = cfg.getString("seraph_top",         "color", grad_seraph_top,    "Hex #RRGGBB");
        grad_black_bot     = cfg.getString("black_bottom",       "color", grad_black_bot,     "Hex #RRGGBB");
        grad_black_top     = cfg.getString("black_top",          "color", grad_black_top,     "Hex #RRGGBB");
        grad_etech_bot     = cfg.getString("etech_bottom",       "color", grad_etech_bot,     "Hex #RRGGBB (E-Tech)");
        grad_etech_top     = cfg.getString("etech_top",          "color", grad_etech_top,     "Hex #RRGGBB (E-Tech)");
        grad_glitch_bot    = cfg.getString("glitch_bottom",      "color", grad_glitch_bot,    "Hex #RRGGBB (Glitch)");
        grad_glitch_top    = cfg.getString("glitch_top",         "color", grad_glitch_top,    "Hex #RRGGBB (Glitch)");

        // overlays
        overlayEnableInventory = cfg.getBoolean("enableInventoryOverlays", "overlay", overlayEnableInventory, "Draw colored frames in inventory");
        overlayEnableHotbar    = cfg.getBoolean("enableHotbarOverlays",    "overlay", overlayEnableHotbar,    "Draw colored frames on hotbar");
        overlayBorderAlpha     = cfg.getInt("borderAlpha", "overlay", overlayBorderAlpha, 0, 255, "Border alpha 0..255");
        overlayFillAlpha       = cfg.getInt("fillAlpha",   "overlay", overlayFillAlpha,   0, 255, "Bottom strip alpha 0..255");
        overlayStripHeight     = cfg.getInt("stripHeight", "overlay", overlayStripHeight, 1, 4,   "Bottom strip height (px)");

        overlay_common   = cfg.getString("color_common",   "overlay", overlay_common,   "Hex #RRGGBB");
        overlay_uncommon = cfg.getString("color_uncommon", "overlay", overlay_uncommon, "Hex #RRGGBB");
        overlay_rare     = cfg.getString("color_rare",     "overlay", overlay_rare,     "Hex #RRGGBB");
        overlay_epic     = cfg.getString("color_epic",     "overlay", overlay_epic,     "Hex #RRGGBB");
        overlay_legend   = cfg.getString("color_legend",   "overlay", overlay_legend,   "Hex #RRGGBB");
        overlay_pearl    = cfg.getString("color_pearl",    "overlay", overlay_pearl,    "Hex #RRGGBB");
        overlay_seraph   = cfg.getString("color_seraph",   "overlay", overlay_seraph,   "Hex #RRGGBB");
        overlay_etech    = cfg.getString("color_etech",    "overlay", overlay_etech,    "Hex #RRGGBB");
        overlay_glitch   = cfg.getString("color_glitch",   "overlay", overlay_glitch,   "Hex #RRGGBB");
        overlay_black    = cfg.getString("color_black",    "overlay", overlay_black,    "Hex #RRGGBB");
    }

    public static void saveIfNeeded() {
        if (cfg != null && cfg.hasChanged()) cfg.save();
    }

    // Utility for renderer to parse hex + apply brightness
    public static int[] col(String hex, double mul) {
        if (hex == null) hex = "#FFFFFF";
        hex = hex.trim();
        if (hex.startsWith("#")) hex = hex.substring(1);
        int v;
        try { v = (int)Long.parseLong(hex, 16); }
        catch (Exception e) { v = 0xFFFFFF; }
        int r = (v >> 16) & 255, g = (v >> 8) & 255, b = v & 255;
        r = (int)Math.min(255, Math.round(r * mul));
        g = (int)Math.min(255, Math.round(g * mul));
        b = (int)Math.min(255, Math.round(b * mul));
        return new int[]{ r, g, b };
    }

    /** simple parse without brightness */
    public static int col24(String hex) {
        if (hex == null) return 0xFFFFFF;
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        try { return (int)Long.parseLong(h, 16) & 0xFFFFFF; } catch (Throwable t) { return 0xFFFFFF; }
    }

    private ModConfig() {}
}
