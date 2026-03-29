// src/main/java/com/example/examplemod/client/HexSlotOverlay.java
package com.example.examplemod.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.examplemod.ModConfig;
import com.example.examplemod.beams.RarityDetect;

public class HexSlotOverlay {

    public static void register() { MinecraftForge.EVENT_BUS.register(new HexSlotOverlay()); }

    // reflect guiLeft/guiTop
    private static Field F_GUI_LEFT, F_GUI_TOP;

    // IMPORTANT:
    //  - We must NOT use 0 as "no overlay", because BLACK rarity is 0x000000 (== 0).
    //  - Use -1 sentinel for "no overlay".
    private static final int NO_OVERLAY = -1;

    // GLITCH gradient (pastel + lighter)
    private static final int GLITCH_TOP_RGB = 0xFF5FB8; // pastel neon magenta
    private static final int GLITCH_BOT_RGB = 0xFFD1E6; // soft pastel pink

    // Pseudo-black modifier accent (modifier ring inside the normal rarity frame)
    private static final int PSEUDO_BLACK_RING_RGB  = 0x050505;
    private static final int PSEUDO_BLACK_RING2_RGB = 0x1A1A1A;
    private static final int PSEUDO_BLACK_SHADE_RGB = 0x101010;

    // Evolved pop accents
    private static final int EVOLVED_WHITE_RGB = 0xFFF6FF;
    private static final int EVOLVED_T1_RGB    = 0xFFC8F6;
    private static final int EVOLVED_T2_RGB    = 0xFFE58A;
    private static final int EVOLVED_T3_RGB    = 0xFFF2B8;

    // word-boundary patterns (case-insensitive after we lowercase)
    private static final Pattern
            P_COMMON         = Pattern.compile("\\bcommon\\b"),
            P_UNCOMMON       = Pattern.compile("\\buncommon\\b"),
            P_RARE           = Pattern.compile("\\brare\\b"),
            P_EPIC           = Pattern.compile("\\bepic\\b"),
            P_LEGEND         = Pattern.compile("\\blegend(ary)?\\b"),
            P_PEARL          = Pattern.compile("\\bpearlescent\\b"),
            P_SERAPH         = Pattern.compile("\\bseraph\\b"),
            P_ETECH          = Pattern.compile("\\b(e[- ]?tech|etech)\\b"),
            P_GLITCH         = Pattern.compile("\\bglitch\\b"),
            P_BLACK          = Pattern.compile("\\bblack\\b"),
            P_EFF_PLUS       = Pattern.compile("effervescent(?:\\+|_)", Pattern.CASE_INSENSITIVE),
            P_EFF            = Pattern.compile("\\beffervescent\\b", Pattern.CASE_INSENSITIVE),
            P_EVOLVED_TIER   = Pattern.compile("evolved\\s*:\\s*tier\\s*(\\d+)", Pattern.CASE_INSENSITIVE),
            P_EVOLUTION_BOOST= Pattern.compile("evolution\\s+boost\\s*:\\s*\\+?(\\d+)\\s*%", Pattern.CASE_INSENSITIVE),
            P_EVOLVED_WORD   = Pattern.compile("\\bevolved\\b", Pattern.CASE_INSENSITIVE),
            P_EVOLVE_READY   = Pattern.compile("\\bevolve\\s*ready\\b", Pattern.CASE_INSENSITIVE);

    // ── INVENTORY ──────────────────────────────────────────────────────────────
    @SubscribeEvent
    public void drawContainer(GuiScreenEvent.DrawScreenEvent.Post e) {
        int __prevMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            // Safety: ensure HUD isn't affected by any prior 3D render state (F5 toggles can leave lighting on)
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            GL11.glColor4f(1f, 1f, 1f, 1f);

            if (!(e.gui instanceof GuiContainer)) return;
            if (!ModConfig.overlayEnableInventory) return;

            GuiContainer gui = (GuiContainer) e.gui;
            int guiLeft = getGuiLeft(gui);
            int guiTop  = getGuiTop(gui);

            Container c = gui.inventorySlots;
            if (c == null || c.inventorySlots == null) return;

            final long ms = Minecraft.getSystemTime() & 0xFFFFFFFFL;
            final float t = (ms % 4000L) / 4000f;

            for (Object o : c.inventorySlots) {
                if (!(o instanceof Slot)) continue;
                Slot s = (Slot) o;
                if (!s.getHasStack()) continue;

                ItemStack stack = s.getStack();
                String rarityKey = rarityKey(stack);
                int color = colorForRarityKey(rarityKey, t);
                if (color == NO_OVERLAY) continue;

                int x = guiLeft + s.xDisplayPosition;
                int y = guiTop  + s.yDisplayPosition;

                int bA = ModConfig.overlayBorderAlpha & 0xFF;
                int fA = ModConfig.overlayFillAlpha   & 0xFF;

                boolean isBlack  = "black".equals(rarityKey);
                boolean isGlitch = "glitch".equals(rarityKey) || hasTokenAnywhere(stack, P_GLITCH);
                boolean isPseudoBlack = hasPseudoBlackModifier(stack);

                if (isBlack) { bA = 0xE0; fA = Math.max(fA, 0xA0); }

                // ── ITEM GLOSS (on the icon itself) ──
                boolean isEtech  = "etech".equals(rarityKey) || hasTokenAnywhere(stack, P_ETECH);
                boolean isPearl  = "pearlescent".equals(rarityKey) || hasTokenAnywhere(stack, P_PEARL);
                boolean isLegend = "legendary".equals(rarityKey) || "legend".equals(rarityKey) || hasTokenAnywhere(stack, P_LEGEND);
                boolean isEff    = isEffKey(rarityKey) || hasTokenAnywhere(stack, P_EFF) || hasTokenAnywhere(stack, P_EFF_PLUS);
                int evolveTier   = getEvolveTier(stack);

                if (isGlitch || isEtech || isPearl || isLegend || isEff) {
                    float period = isEff ? 1200f : (isPearl ? 1600f : (isGlitch ? 2000f : 2400f));
                    float phase  = glossPhaseFor(stack, ms, period);
                    int glossA   = isEff ? 110 : (isGlitch ? 90 : 75);

                    // Neutral white gloss reads like "shine" best
                    drawItemGloss(x, y, 16, 16, phase, glossA, 0xFFFFFF);
                }
                if (isPseudoBlack) {
                    float phase = glossPhaseFor(stack, ms, 1850f);
                    drawItemShadeGloss(x, y, 16, 16, phase, 58, PSEUDO_BLACK_SHADE_RGB);
                }

                // ── SLOT BORDER/STRIP ──
                if (isGlitch) {
                    drawBorderVGradient(x - 1, y - 1, 18, 18, GLITCH_TOP_RGB, GLITCH_BOT_RGB, bA);
                    drawRectVGradient(x, y + 16 - ModConfig.overlayStripHeight, 16, ModConfig.overlayStripHeight,
                            GLITCH_TOP_RGB, GLITCH_BOT_RGB, fA);
                } else {
                    drawBorder(x - 1, y - 1, 18, 18, color, bA);
                    drawRect(x, y + 16 - ModConfig.overlayStripHeight, 16, ModConfig.overlayStripHeight, color, fA);
                }

                if (isBlack) drawBorder(x, y, 16, 16, 0x666666, 0x80);
                if (evolveTier > 0) drawEvolvedAccent(x - 1, y - 1, x, y, rarityKey, color, evolveTier, ms, false);
                if (isPseudoBlack) drawPseudoBlackInnerRing(x, y, ms, false);
            }
        } finally {
            // Hard reset: prevents "dark hotbar" / tinted UI after perspective changes
            GL11.glColor4f(1f, 1f, 1f, 1f);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            GL11.glMatrixMode(__prevMatrixMode);
        }
    }

    // ── HOTBAR ────────────────────────────────────────────────────────────────
    @SubscribeEvent
    public void drawHotbar(RenderGameOverlayEvent.Post e) {
        int __prevMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            // Safety: ensure HUD isn't affected by any prior 3D render state (F5 toggles can leave lighting on)
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            GL11.glColor4f(1f, 1f, 1f, 1f);

            if (e.type != RenderGameOverlayEvent.ElementType.HOTBAR) return;
            if (!ModConfig.overlayEnableHotbar) return;

            Minecraft mc = Minecraft.getMinecraft();
            if (mc.currentScreen != null || mc.thePlayer == null) return;

            final long ms = Minecraft.getSystemTime() & 0xFFFFFFFFL;
            final float t  = (ms % 4000L) / 4000f;
            final float pulse = 0.5f + 0.5f * (float)Math.sin((ms % 1000L) / 1000f * 2f * (float)Math.PI);

            ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
            int sw = sr.getScaledWidth();
            int sh = sr.getScaledHeight();

            final int left = sw / 2 - 90;
            final int top  = sh - 22;
            final int selected = mc.thePlayer.inventory.currentItem;

            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.thePlayer.inventory.mainInventory[i];
                if (stack == null) continue;

                String rarityKey = rarityKey(stack);
                int color = colorForRarityKey(rarityKey, t);
                if (color == NO_OVERLAY) continue;

                boolean isSelected = (i == selected);
                boolean isBlack    = "black".equals(rarityKey);
                boolean isGlitch   = "glitch".equals(rarityKey) || hasTokenAnywhere(stack, P_GLITCH);
                boolean isPseudoBlack = hasPseudoBlackModifier(stack);

                boolean isEtech  = "etech".equals(rarityKey) || hasTokenAnywhere(stack, P_ETECH);
                boolean isPearl  = "pearlescent".equals(rarityKey) || hasTokenAnywhere(stack, P_PEARL);
                boolean isLegend = "legendary".equals(rarityKey) || "legend".equals(rarityKey) || hasTokenAnywhere(stack, P_LEGEND);
                boolean isEff    = isEffKey(rarityKey) || hasTokenAnywhere(stack, P_EFF) || hasTokenAnywhere(stack, P_EFF_PLUS);
                int evolveTier   = getEvolveTier(stack);

                int x = left + i * 20 + 1; // frame
                int y = top  + 1;

                int bA = Math.max(0, (ModConfig.overlayBorderAlpha - 0x10)) & 0xFF;
                int fA = ModConfig.overlayFillAlpha & 0xFF;
                if (isBlack) { bA = 0xE0; fA = Math.max(fA, 0xA0); }

                // ── ITEM GLOSS (icon area inside the 18x18 frame) ──
                if (isGlitch || isEtech || isPearl || isLegend || isEff) {
                    float period = isEff ? 1200f : (isPearl ? 1600f : (isGlitch ? 2000f : 2400f));
                    float phase  = glossPhaseFor(stack, ms, period);
                    int glossA   = isEff ? 110 : (isGlitch ? 90 : 75);

                    drawItemGloss(x + 1, y + 1, 16, 16, phase, glossA, 0xFFFFFF);
                }
                if (isPseudoBlack) {
                    float phase = glossPhaseFor(stack, ms, 1850f);
                    drawItemShadeGloss(x + 1, y + 1, 16, 16, phase, 58, PSEUDO_BLACK_SHADE_RGB);
                }

                if (!isSelected) {
                    if (isGlitch) {
                        drawBorderVGradient(x, y, 18, 18, GLITCH_TOP_RGB, GLITCH_BOT_RGB, bA);
                        drawRectVGradient(x + 1, y + 17 - ModConfig.overlayStripHeight, 16, ModConfig.overlayStripHeight,
                                GLITCH_TOP_RGB, GLITCH_BOT_RGB, fA);
                    } else {
                        drawBorder(x, y, 18, 18, color, bA);
                        drawRect(x + 1, y + 17 - ModConfig.overlayStripHeight, 16, ModConfig.overlayStripHeight, color, fA);
                    }

                    if (isBlack) drawBorder(x + 1, y + 1, 16, 16, 0x666666, 0x80);
                    if (evolveTier > 0) drawEvolvedAccent(x, y, x + 1, y + 1, rarityKey, color, evolveTier, ms, false);
                    if (isPseudoBlack) drawPseudoBlackInnerRing(x + 1, y + 1, ms, false);

                } else {
                    int a = (int)(0x90 + 0x40 * pulse);

                    if (isGlitch) {
                        drawRectVGradient(x + 1, y + 17, 16, 2, GLITCH_TOP_RGB, GLITCH_BOT_RGB, a);
                        drawRectVGradient(x + 1, y + 17 - ModConfig.overlayStripHeight, 16, ModConfig.overlayStripHeight,
                                GLITCH_TOP_RGB, GLITCH_BOT_RGB, fA);
                    } else {
                        drawRect(x + 1, y + 17, 16, 2, color, a);
                        drawRect(x + 1, y + 17 - ModConfig.overlayStripHeight, 16, ModConfig.overlayStripHeight, color, fA);
                    }
                    if (evolveTier > 0) drawEvolvedAccent(x, y, x + 1, y + 1, rarityKey, color, evolveTier, ms, true);
                    if (isPseudoBlack) drawPseudoBlackInnerRing(x + 1, y + 1, ms, true);
                }
            }
        } finally {
            // Hard reset: prevents "dark hotbar" / tinted UI after perspective changes
            GL11.glColor4f(1f, 1f, 1f, 1f);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            GL11.glMatrixMode(__prevMatrixMode);
        }
    }

    // ── Robust rarity resolve (main lore, hidden tags, HexLorePages) ──────────
    private String rarityKey(ItemStack stack) {
        if (stack == null) return "";

        try {
            String key = RarityDetect.fromStack(stack);
            key = normalizeRarityKey(key);
            if (key.length() > 0) return key;
        } catch (Throwable ignored) {}

        List<String> lines = collectOverlayLines(stack);
        return detectRarityFromLines(lines);
    }

    private int colorForRarityKey(String key, float t) {
        key = normalizeRarityKey(key);
        if (key.length() == 0) return NO_OVERLAY;

        if ("effervescent_".equals(key) || "effervescent+".equals(key)) return effRainbow(t, 3.0f);
        if ("effervescent".equals(key)) return effRainbow(t, 1.5f);

        if ("black".equals(key))       return ModConfig.col24(ModConfig.overlay_black);
        if ("glitch".equals(key))      return ModConfig.col24(ModConfig.overlay_glitch);
        if ("etech".equals(key))       return ModConfig.col24(ModConfig.overlay_etech);
        if ("legendary".equals(key))   return ModConfig.col24(ModConfig.overlay_legend);
        if ("pearlescent".equals(key)) return ModConfig.col24(ModConfig.overlay_pearl);
        if ("seraph".equals(key))      return ModConfig.col24(ModConfig.overlay_seraph);
        if ("epic".equals(key))        return ModConfig.col24(ModConfig.overlay_epic);
        if ("rare".equals(key))        return ModConfig.col24(ModConfig.overlay_rare);
        if ("uncommon".equals(key))    return ModConfig.col24(ModConfig.overlay_uncommon);
        if ("common".equals(key))      return ModConfig.col24(ModConfig.overlay_common);

        return NO_OVERLAY;
    }

    private String normalizeRarityKey(String key) {
        if (key == null) return "";
        key = key.trim().toLowerCase();
        if ("legend".equals(key)) return "legendary";
        if ("e-tech".equals(key) || "e tech".equals(key)) return "etech";
        if ("effervescent+".equals(key)) return "effervescent_";
        return key;
    }

    private String detectRarityFromLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) return "";

        for (int i = 0; i < lines.size(); i++) {
            String line = stripTags(String.valueOf(lines.get(i))).toLowerCase();

            if (P_EFF_PLUS.matcher(line).find()) return "effervescent_";
            if (P_EFF.matcher(line).find())      return "effervescent";

            if (P_BLACK.matcher(line).find())    return "black";
            if (P_GLITCH.matcher(line).find())   return "glitch";
            if (P_ETECH.matcher(line).find())    return "etech";
            if (P_LEGEND.matcher(line).find())   return "legendary";
            if (P_PEARL.matcher(line).find())    return "pearlescent";
            if (P_SERAPH.matcher(line).find())   return "seraph";
            if (P_EPIC.matcher(line).find())     return "epic";
            if (P_RARE.matcher(line).find())     return "rare";
            if (P_UNCOMMON.matcher(line).find()) return "uncommon";
            if (P_COMMON.matcher(line).find())   return "common";
        }

        return "";
    }

    private boolean isEffKey(String rarityKey) {
        rarityKey = normalizeRarityKey(rarityKey);
        return "effervescent".equals(rarityKey) || "effervescent_".equals(rarityKey);
    }

    private List<String> collectOverlayLines(ItemStack stack) {
        List<String> out = new ArrayList<String>();
        if (stack == null || !stack.hasTagCompound()) return out;

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) return out;

        try {
            NBTTagCompound display = tag.getCompoundTag("display");
            NBTTagList lore = display.getTagList("Lore", 8);
            for (int i = 0; i < lore.tagCount(); i++) {
                out.add(String.valueOf(lore.getStringTagAt(i)));
            }
        } catch (Throwable ignored) {}

        return out;
    }

    // ── token check (host visible lore only; ignore HexLorePages/socket orb pages) ──
    private boolean hasTokenAnywhere(ItemStack stack, Pattern p){
        List<String> lines = collectOverlayLines(stack);
        for (int i = 0; i < lines.size(); i++) {
            String line = stripTags(String.valueOf(lines.get(i))).toLowerCase();
            if (p.matcher(line).find()) return true;
        }
        return false;
    }

    private boolean hasPseudoBlackModifier(ItemStack stack){
        try {
            return RarityDetect.hasPseudoBlackOverlay(stack);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private int getEvolveTier(ItemStack stack) {
        List<String> lines = collectOverlayLines(stack);
        if (lines.isEmpty()) return 0;

        int explicitTier = 0;
        int foundPct = 0;
        boolean foundEvolvedWord = false;

        for (int i = 0; i < lines.size(); i++) {
            String s = stripTags(String.valueOf(lines.get(i))).toLowerCase();
            if (s.length() == 0) continue;

            Matcher mt = P_EVOLVED_TIER.matcher(s);
            if (explicitTier <= 0 && mt.find()) {
                try { explicitTier = Integer.parseInt(mt.group(1)); } catch (Throwable ignored) {}
            }

            Matcher mb = P_EVOLUTION_BOOST.matcher(s);
            if (foundPct <= 0 && mb.find()) {
                try { foundPct = Integer.parseInt(mb.group(1)); } catch (Throwable ignored) {}
            }

            if (!P_EVOLVE_READY.matcher(s).find() && P_EVOLVED_WORD.matcher(s).find()) {
                foundEvolvedWord = true;
            }

            if (explicitTier > 0 && foundPct > 0 && foundEvolvedWord) break;
        }

        if (explicitTier > 3) explicitTier = 3;
        if (explicitTier > 0) return explicitTier;

        if (foundPct >= 87) return 3;
        if (foundPct >= 57) return 2;
        if (foundPct >= 40) return 1;

        return foundEvolvedWord ? 1 : 0;
    }

    private void drawEvolvedAccent(int frameX, int frameY, int iconX, int iconY, String rarityKey, int baseColor, int tier, long ms, boolean selected) {
        float evoPulse = 0.5f + 0.5f * (float)Math.sin((ms % 1200L) / 1200f * 2f * (float)Math.PI);
        int[] pal = evolvedPaletteForKey(rarityKey, ms);

        int evoTop   = boostEvolvedPaletteColor(pal[0], tier, baseColor);
        int evoBot   = boostEvolvedPaletteColor(pal[1], tier, baseColor);
        int evoShine = evolvedShineColorForTier(pal[2], tier, baseColor);

        int ringA   = selected ? (int)(140 + 65 * evoPulse) : (int)(112 + 52 * evoPulse);
        int ringA2  = selected ? (int)(98  + 48 * evoPulse) : (int)(82  + 40 * evoPulse);
        int glossA  = 86 + (tier * 12);
        int shineA  = Math.min(255, ringA + 34);
        int shineA2 = Math.min(255, ringA2 + 26);

        // rarity-aware evolved ring based on evolvedHeaderMap palettes
        drawBorderVGradient(frameX + 1, frameY + 1, 16, 16, evoTop, evoBot, ringA);

        // second inner pop for stronger tiers
        if (tier >= 2) {
            drawBorder(frameX + 2, frameY + 2, 14, 14, mixRgb(evoShine, EVOLVED_WHITE_RGB, 0.35f), ringA2);
        }

        // tiny top-edge shine bar + extra center sparkle on tier 3
        drawRect(iconX + 2, iconY, 12, 1, evoShine, shineA);
        if (tier >= 3) {
            drawRect(iconX + 4, iconY + 1, 8, 1, EVOLVED_WHITE_RGB, shineA2);
        }

        // stronger gloss sweep on the icon itself
        float evoPhase = glossPhaseForSeed(baseColor + tier * 131, ms, 1450f - (tier * 120f));
        drawItemGloss(iconX, iconY, 16, 16, evoPhase, glossA, mixRgb(evoShine, EVOLVED_WHITE_RGB, 0.45f));
    }

    private int boostEvolvedPaletteColor(int color, int tier, int baseColor) {
        if (tier >= 3) return mixRgb(color, EVOLVED_T3_RGB, 0.32f);
        if (tier == 2) return mixRgb(color, EVOLVED_T2_RGB, 0.24f);
        if (tier == 1) return mixRgb(color, mixRgb(baseColor, EVOLVED_T1_RGB, 0.45f), 0.18f);
        return color;
    }

    private int evolvedShineColorForTier(int color, int tier, int baseColor) {
        if (tier >= 3) return mixRgb(color, EVOLVED_WHITE_RGB, 0.58f);
        if (tier == 2) return mixRgb(color, EVOLVED_T2_RGB, 0.40f);
        if (tier == 1) return mixRgb(color, mixRgb(baseColor, EVOLVED_T1_RGB, 0.45f), 0.28f);
        return color;
    }

    private int[] evolvedPaletteForKey(String rarityKey, long ms) {
        rarityKey = normalizeRarityKey(rarityKey);
        float t = (ms % 4000L) / 4000f;

        if ("effervescent".equals(rarityKey) || "effervescent_".equals(rarityKey)) {
            return new int[] {
                    effRainbow((t * 2.0f) % 1.0f, 1.0f),
                    effRainbow(((t + 0.16f) * 2.0f) % 1.0f, 1.0f),
                    EVOLVED_WHITE_RGB
            };
        }
        if ("seraph".equals(rarityKey)) {
            return new int[] { 0xFF66C4, 0xFFB3E6, 0xFFD8F1 };
        }
        if ("glitch".equals(rarityKey)) {
            return new int[] { 0xFFC1E3, 0xFFE2F2, 0xFFF1F8 };
        }
        if ("etech".equals(rarityKey)) {
            return new int[] { 0xFF2BA6, 0xFF66CF, 0xFFB5E9 };
        }
        if ("epic".equals(rarityKey)) {
            return new int[] { 0x3A7BFF, 0x1ED1FF, 0xD8F4FF };
        }
        if ("legendary".equals(rarityKey) || "legend".equals(rarityKey)) {
            return new int[] { 0xFFB300, 0xFFF176, 0xFFF7C8 };
        }
        if ("pearlescent".equals(rarityKey)) {
            return new int[] { 0x00CED1, 0x48D1CC, 0xCFFDFC };
        }
        if ("rare".equals(rarityKey)) {
            return new int[] { 0xA64DFF, 0xD0A6FF, 0xF1E4FF };
        }
        if ("uncommon".equals(rarityKey)) {
            return new int[] { 0x00CC00, 0x33FF33, 0xDFFFE2 };
        }
        if ("common".equals(rarityKey)) {
            return new int[] { 0xFFFFFF, 0xDADADA, 0xFFFFFF };
        }
        if ("pseudo_black".equals(rarityKey)) {
            return new int[] { 0xFFFFFF, 0xD0D0D0, 0xFFFFFF };
        }
        if ("black".equals(rarityKey)) {
            return new int[] { 0x141414, 0x5A5A5A, 0xB8B8B8 };
        }

        return new int[] { baseFallbackColor(rarityKey), EVOLVED_T1_RGB, EVOLVED_WHITE_RGB };
    }

    private int baseFallbackColor(String rarityKey) {
        rarityKey = normalizeRarityKey(rarityKey);
        if ("black".equals(rarityKey)) return ModConfig.col24(ModConfig.overlay_black);
        if ("glitch".equals(rarityKey)) return ModConfig.col24(ModConfig.overlay_glitch);
        if ("etech".equals(rarityKey)) return ModConfig.col24(ModConfig.overlay_etech);
        if ("legendary".equals(rarityKey) || "legend".equals(rarityKey)) return ModConfig.col24(ModConfig.overlay_legend);
        if ("pearlescent".equals(rarityKey)) return ModConfig.col24(ModConfig.overlay_pearl);
        if ("seraph".equals(rarityKey)) return ModConfig.col24(ModConfig.overlay_seraph);
        if ("epic".equals(rarityKey)) return ModConfig.col24(ModConfig.overlay_epic);
        if ("rare".equals(rarityKey)) return ModConfig.col24(ModConfig.overlay_rare);
        if ("uncommon".equals(rarityKey)) return ModConfig.col24(ModConfig.overlay_uncommon);
        if ("common".equals(rarityKey)) return ModConfig.col24(ModConfig.overlay_common);
        if ("effervescent".equals(rarityKey) || "effervescent_".equals(rarityKey)) return 0xFFFFFF;
        return EVOLVED_T1_RGB;
    }

    private int mixRgb(int a, int b, float t) {
        if (t < 0f) t = 0f;
        if (t > 1f) t = 1f;

        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;

        int rr = (int)(ar + (br - ar) * t);
        int rg = (int)(ag + (bg - ag) * t);
        int rb = (int)(ab + (bb - ab) * t);

        return ((rr & 0xFF) << 16) | ((rg & 0xFF) << 8) | (rb & 0xFF);
    }

    private void drawPseudoBlackInnerRing(int x, int y, long ms, boolean selected){
        float pulse = 0.5f + 0.5f * (float)Math.sin((ms % 1200L) / 1200f * 2f * (float)Math.PI);
        int outerA = selected ? (int)(0xB8 + 0x20 * pulse) : (int)(0xA0 + 0x24 * pulse);
        int innerA = selected ? (int)(0x6C + 0x18 * pulse) : (int)(0x58 + 0x16 * pulse);
        drawBorder(x,     y,     16, 16, PSEUDO_BLACK_RING_RGB,  outerA);
        drawBorder(x + 1, y + 1, 14, 14, PSEUDO_BLACK_RING2_RGB, innerA);
    }

    // Dark moving band for pseudo-black overlays.
    // Normal alpha blending is used here because additive black would be invisible.
    private void drawItemShadeGloss(int x, int y, int w, int h, float phase, int alpha, int tintRgb) {
        float cx = phase * (w + 20f) - 10f;
        float bandW = 6.5f;
        float slope = 0.55f;

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);

        int slices = 6;
        for (int i = 0; i < slices; i++) {
            float fx0 = (i / (float)slices);
            float fx1 = ((i + 1) / (float)slices);

            float local0 = -1f + 2f * fx0;
            float local1 = -1f + 2f * fx1;

            float px0 = (x + cx + local0 * bandW);
            float px1 = (x + cx + local1 * bandW);

            int ix0 = (int)Math.floor(Math.max(px0, x));
            int ix1 = (int)Math.ceil (Math.min(px1, x + w));
            if (ix1 <= ix0) continue;

            float mid = (local0 + local1) * 0.5f;
            float tt = 1.0f - Math.min(1.0f, Math.abs(mid));
            int a = (int)(alpha * (tt * tt));
            if (a <= 0) continue;

            int dy0 = (int)((ix0 - x - w * 0.5f) * slope);
            int dy1 = (int)((ix1 - x - w * 0.5f) * slope);

            int y0 = y + Math.min(dy0, dy1);
            int y1 = y + h + Math.max(dy0, dy1);

            if (y0 < y) y0 = y;
            if (y1 > y + h) y1 = y + h;

            drawRect(ix0, y0, ix1 - ix0, y1 - y0, tintRgb, a);
        }

        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1,1,1,1);
    }

    // ── ITEM GLOSS (icon overlay pass) ────────────────────────────────────────
    private float glossPhaseFor(ItemStack stack, long ms, float basePeriodMs) {
        int seed = 0;
        try {
            seed = stack.getItem().hashCode() * 31 + stack.getItemDamage();
            if (stack.hasDisplayName()) seed = seed * 31 + stack.getDisplayName().hashCode();
            if (stack.hasTagCompound()) seed = seed * 31 + stack.getTagCompound().toString().hashCode();
        } catch (Throwable ignored) {}

        return glossPhaseForSeed(seed, ms, basePeriodMs);
    }

    private float glossPhaseForSeed(int seed, long ms, float basePeriodMs) {
        float off = ((seed & 0x7FFFFFFF) % 10000) / 10000f; // 0..1
        float phase = (ms / basePeriodMs + off) % 1.0f;
        if (phase < 0) phase += 1.0f;
        return phase;
    }

    // Draw a moving glossy band over the item icon area (x,y,w,h).
    // phase: 0..1 moves left->right
    // tintRgb: usually 0xFFFFFF for neutral gloss
    private void drawItemGloss(int x, int y, int w, int h, float phase, int alpha, int tintRgb) {
        float cx = phase * (w + 20f) - 10f; // enters/exits smoothly
        float bandW = 6.5f;                 // half-width of the glossy band
        float slope = 0.55f;                // diagonal tilt

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        // additive shine looks like "gloss"
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);

        int slices = 6; // cheap gradient
        for (int i = 0; i < slices; i++) {
            float fx0 = (i / (float)slices);
            float fx1 = ((i + 1) / (float)slices);

            float local0 = -1f + 2f * fx0;
            float local1 = -1f + 2f * fx1;

            float px0 = (x + cx + local0 * bandW);
            float px1 = (x + cx + local1 * bandW);

            int ix0 = (int)Math.floor(Math.max(px0, x));
            int ix1 = (int)Math.ceil (Math.min(px1, x + w));
            if (ix1 <= ix0) continue;

            float mid = (local0 + local1) * 0.5f;
            float gt = 1.0f - Math.min(1.0f, Math.abs(mid)); // 0..1
            int a = (int)(alpha * (gt * gt));
            if (a <= 0) continue;

            int dy0 = (int)((ix0 - x - w * 0.5f) * slope);
            int dy1 = (int)((ix1 - x - w * 0.5f) * slope);

            int y0 = y + Math.min(dy0, dy1);
            int y1 = y + h + Math.max(dy0, dy1);

            if (y0 < y) y0 = y;
            if (y1 > y + h) y1 = y + h;

            drawRect(ix0, y0, ix1 - ix0, y1 - y0, tintRgb, a);
        }

        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1,1,1,1);
    }

    // ── existing utils ─────────────────────────────────────────────────────────
    private int effRainbow(float t, float speed){
        float hue = (t * speed) % 1.0f;
        float sat = 0.95f, val = 1.00f;
        return hsvToRgb(hue, sat, val);
    }

    private int hsvToRgb(float h, float s, float v){
        float r=0, g=0, b=0;
        int i = (int)Math.floor(h * 6.0f);
        float f = h * 6.0f - i;
        float p = v * (1.0f - s);
        float q = v * (1.0f - f * s);
        float tt = v * (1.0f - (1.0f - f) * s);
        switch (i % 6){
            case 0: r=v; g=tt; b=p; break;
            case 1: r=q; g=v; b=p; break;
            case 2: r=p; g=v; b=tt; break;
            case 3: r=p; g=q; b=v; break;
            case 4: r=tt; g=p; b=v; break;
            case 5: r=v; g=p; b=q; break;
        }
        int R = (int)(r * 255.0f) & 0xFF;
        int G = (int)(g * 255.0f) & 0xFF;
        int B = (int)(b * 255.0f) & 0xFF;
        return (R << 16) | (G << 8) | B;
    }

    private String stripTags(String s){
        if (s == null) return "";
        s = s.replaceAll("§[0-9A-FK-ORa-fk-or]", "");
        s = s.replaceAll("<[^>]+>", "");
        return s;
    }

    private void drawBorder(int x, int y, int w, int h, int rgb, int alpha){
        drawRect(x,     y,     w, 1, rgb, alpha);
        drawRect(x,     y+h-1, w, 1, rgb, alpha);
        drawRect(x,     y,     1, h, rgb, alpha);
        drawRect(x+w-1, y,     1, h, rgb, alpha);
    }

    // Vertical gradient border (top color -> bottom color)
    private void drawBorderVGradient(int x, int y, int w, int h, int topRgb, int botRgb, int alpha){
        drawRect(x, y, w, 1, topRgb, alpha);
        drawRect(x, y + h - 1, w, 1, botRgb, alpha);
        drawRectVGradient(x,       y, 1, h, topRgb, botRgb, alpha);
        drawRectVGradient(x + w-1, y, 1, h, topRgb, botRgb, alpha);
    }

    private void drawRectVGradient(int x, int y, int w, int h, int topRgb, int botRgb, int alpha){
        float af = (alpha & 0xFF) / 255f;

        float tr = ((topRgb >> 16) & 0xFF) / 255f;
        float tg = ((topRgb >> 8 ) & 0xFF) / 255f;
        float tb = ((topRgb      ) & 0xFF) / 255f;

        float br = ((botRgb >> 16) & 0xFF) / 255f;
        float bg = ((botRgb >> 8 ) & 0xFF) / 255f;
        float bb = ((botRgb      ) & 0xFF) / 255f;

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);

        GL11.glBegin(GL11.GL_QUADS);

        // bottom
        GL11.glColor4f(br, bg, bb, af);
        GL11.glVertex3f(x,     y+h, 0);
        GL11.glVertex3f(x+w,   y+h, 0);

        // top
        GL11.glColor4f(tr, tg, tb, af);
        GL11.glVertex3f(x+w,   y,   0);
        GL11.glVertex3f(x,     y,   0);

        GL11.glEnd();

        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1,1,1,1);
    }

    private void drawRect(int x, int y, int w, int h, int rgb, int alpha){
        float af = (alpha & 0xFF) / 255f;
        float r  = ((rgb >> 16) & 0xFF) / 255f;
        float g  = ((rgb >> 8 ) & 0xFF) / 255f;
        float b  = ((rgb      ) & 0xFF) / 255f;

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);

        GL11.glColor4f(r,g,b,af);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3f(x,     y+h, 0);
        GL11.glVertex3f(x+w,   y+h, 0);
        GL11.glVertex3f(x+w,   y,   0);
        GL11.glVertex3f(x,     y,   0);
        GL11.glEnd();

        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1,1,1,1);
    }

    private static int getGuiLeft(GuiContainer g){
        try{
            if (F_GUI_LEFT == null){
                F_GUI_LEFT = GuiContainer.class.getDeclaredField("guiLeft");
                F_GUI_LEFT.setAccessible(true);
            }
            return F_GUI_LEFT.getInt(g);
        }catch(Throwable t){
            try{
                if (F_GUI_LEFT == null){
                    F_GUI_LEFT = GuiContainer.class.getDeclaredField("field_147003_i");
                    F_GUI_LEFT.setAccessible(true);
                }
                return F_GUI_LEFT.getInt(g);
            }catch(Throwable ignored){ return 0; }
        }
    }

    private static int getGuiTop(GuiContainer g){
        try{
            if (F_GUI_TOP == null){
                F_GUI_TOP = GuiContainer.class.getDeclaredField("guiTop");
                F_GUI_TOP.setAccessible(true);
            }
            return F_GUI_TOP.getInt(g);
        }catch(Throwable t){
            try{
                if (F_GUI_TOP == null){
                    F_GUI_TOP = GuiContainer.class.getDeclaredField("field_147009_r");
                    F_GUI_TOP.setAccessible(true);
                }
                return F_GUI_TOP.getInt(g);
            }catch(Throwable ignored){ return 0; }
        }
    }
}
