package com.example.examplemod.client.hud;

import com.example.examplemod.api.HexSocketAPI;
import com.example.examplemod.item.ItemGemIcons;
import com.example.examplemod.client.fractured.FracturedKeyHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.lang.reflect.Field;

/**
 * Void Orb HUD (client) — MC 1.7.10
 *
 * Behavior:
 *  - Show ONLY the rolled "type" label (styled if HexFont tags are supported)
 *  - When ready: just "Type"
 *  - When on cooldown: "Type  <sec>s" + cooldown bar
 *
 * Notes about the "box" in your screenshot:
 *  - The dark rectangle behind "Void Gem Orb <Enhanced>" is VANILLA's held-item name popup,
 *    not this HUD. This class can optionally suppress that popup while the Void HUD is active.
 */
@SideOnly(Side.CLIENT)
public class VoidOrbHudOverlay {

    // Client-only: Null Shell hold/charge (driven by FracturedKeyHandler)
    public static volatile boolean NS_HOLD_ACTIVE = false;
    public static volatile float   NS_HOLD_PCT = 0f; // 0..2 (2 = full overcharge)
    public static volatile boolean NS_HOLD_OVERCHARGE = false;

    public static void setNullShellHoldPct(float pct){
        NS_HOLD_ACTIVE = true;
        NS_HOLD_PCT = pct;
        NS_HOLD_OVERCHARGE = pct > 1f;
    }

    public static void clearNullShellHold(){
        NS_HOLD_ACTIVE = false;
        NS_HOLD_PCT = 0f;
        NS_HOLD_OVERCHARGE = false;
    }


    // 1.7.10-safe: use our own RenderItem instance.
    private static final RenderItem RI = new RenderItem();

    // Fallback icon texture (used when socketed gem stack isn't available client-side)
    private static final ResourceLocation VOID_ICON_RL =
            new ResourceLocation("hexcolorcodes", "textures/items/gems/orb_gem_violet_void_64.png");
    private static final ResourceLocation VOID_ICON_ANIM_RL =
            new ResourceLocation("hexcolorcodes", "textures/items/gems/orb_gem_violet_void_64_anim_8f.png");



    // HUD sizing
    private static final int BAR_W = 104;
    private static final int BAR_H = 3;

    // Spacing between the type line and the main cooldown bar
    private static final int MAIN_BAR_Y_DELTA = 14; // was 10
    // Tray spacing between title and first bar
    private static final int TRAY_TITLE_TO_BARS_Y = 18; // was 14
    private static final int TRAY_BLOCK_BASE_H = 26; // was 22

    // Global vertical shift for this HUD (positive = move up)
    private static final int HUD_Y_OFFSET = 12;


    // Null Shell charge sub-bar (thin)
    private static final int NS_BAR_H = 2;
    // Null Shell segmented bars (visual only)
    private static final int NS_SEGMENTS = 4;
    private static final int NS_SEG_GAP = 2;

    // If you want to hide vanilla's "selected item name" popup box while this HUD is active:
    private static final boolean SUPPRESS_VANILLA_HELD_NAME_POPUP = true;

    @SubscribeEvent
    public void onHud(RenderGameOverlayEvent.Post event) {
        if (event == null) return;

        // Experience stage is a nice point to render "above XP bar" without fighting other HUD.
        if (event.type != RenderGameOverlayEvent.ElementType.EXPERIENCE) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.theWorld == null) return;

        // If a GUI is open (Options, Inventory, etc.), don't render.
        if (mc.currentScreen != null) return;

        EntityPlayer p = mc.thePlayer;

        // Only show Null Shell (active) HUD when the Void family is selected.
        // Void passive bars (Abyss/Entropy/GW) may still display even when another family is selected.
        boolean voidSelected = false;
        try { voidSelected = FracturedKeyHandler.isHudKindSelected(p, FracturedKeyHandler.HUD_KIND_VOID); } catch (Throwable ignored) {}

        ItemStack host = findVoidHudHost(p);
        if (host == null) return;

        // If we're not currently on the Void family, only render if a passive Void timer bar is actually active.
        if (!voidSelected && !hasAnyVoidPassiveHudActive(mc, host)) return;

        NBTTagCompound tag = host.getTagCompound();
        if (tag == null) return;

        // Rolled Type
        String type = safeStr(tag.getString("HexVoidHudType"));
        if (type.length() == 0 && tag.hasKey("HexVoidType")) type = safeStr(tag.getString("HexVoidType"));
        if (type.length() == 0 && tag.hasKey("HexVoidOrbType")) type = safeStr(tag.getString("HexVoidOrbType"));
        if (type.length() == 0) type = "Void";

        // Cooldown (server writes these)
        long cdEnd = tag.getLong("HexVoidHudCDEnd");
        int cdMax  = tag.getInteger("HexVoidHudCDMax");

        long now = mc.theWorld.getTotalWorldTime();
        long rem = 0L;
        long localStamp = 0L;
        int localRem0 = 0;
        try { localStamp = tag.getLong("HexVoidHudLocalStamp"); } catch (Throwable ignored) {}
        try { localRem0 = tag.getInteger("HexVoidHudLocalRem"); } catch (Throwable ignored) {}
        if (localStamp > 0L && localRem0 > 0) {
            long elapsed = now - localStamp;
            if (elapsed < 0L) elapsed = 0L;
            rem = (long) localRem0 - elapsed;
        } else {
            rem = cdEnd - now;
        }
        if (rem < 0L) rem = 0L;



        // Null Shell: charge meter (server writes HexNullShellCharge / HexNullShellChargeMax)
        boolean isNullShell = isNullShellType(type);
        int nsCharge = 0;
        int nsMax = 1000;
        int nsPct = 0;
        if (isNullShell) {
            nsCharge = tag.hasKey("HexVoidCharge") ? tag.getInteger("HexVoidCharge") : tag.getInteger("HexNullShellCharge");
            nsMax = tag.hasKey("HexVoidChargeMax") ? tag.getInteger("HexVoidChargeMax") : tag.getInteger("HexNullShellChargeMax");
            if (nsMax <= 0) nsMax = 1000;
            if (nsCharge < 0) nsCharge = 0;
            if (nsCharge > nsMax) nsCharge = nsMax;
            nsPct = (int) Math.floor((nsCharge * 100.0) / (double) nsMax);
            if (nsPct < 0) nsPct = 0;
            if (nsPct > 100) nsPct = 100;
        }
        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int sw = sr.getScaledWidth();
        int sh = sr.getScaledHeight();

        // Passive proc tray: show Abyss/Entropy/GW bars on the right, stacked with other proc HUDs.
        int passiveCount = 0;
        if (getVoidEffectRem(mc, host, "abyss") > 0L) passiveCount++;
        if (getVoidEffectRem(mc, host, "entropy") > 0L) passiveCount++;
        if (getVoidEffectRem(mc, host, "gw") > 0L) passiveCount++;

        // Only show the CENTER Void HUD for the active (Null Shell) while Void family is selected.
        boolean showMain = voidSelected && isNullShell;

        // Centered above XP bar (actives)
        int baseX = (sw / 2) - (BAR_W / 2);
        int baseY = sh - 60 - HUD_Y_OFFSET;
        // Icon (always draw Void orb texture — never the host weapon/tool icon)
        int iconX = baseX - 18;
        int iconY = baseY - 12;

        // Main bar y (used for spacing even when only passive bars are shown)
        int barY = baseY + MAIN_BAR_Y_DELTA;

        int prevMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            // Baseline sane GL state (prevents opacity/texture glitches if another renderer leaked state)
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            try { GL11.glAlphaFunc(GL11.GL_GREATER, 0.01f); } catch (Throwable ignored) {}
            GL11.glColor4f(1f, 1f, 1f, 1f);

            if (showMain) {
                // Optional: suppress VANILLA selected-item-name popup ("Void Gem Orb <Enhanced>") while this HUD is active
                if (voidSelected && SUPPRESS_VANILLA_HELD_NAME_POPUP) {
                    suppressVanillaHeldItemName(mc, p.getCurrentEquippedItem());
                }

                // Render orb icon (flat vs animated based on socketed gem key / inventory meta)
                drawVoidOrbIcon(mc, p, host, iconX, iconY);


                if (voidSelected) {
                    // Build the type line (include family label; seconds are shown near the bar)
                    String typeStyled = styleType(mc, type);
                    String line = "§5Void Type§r: " + typeStyled;
                    if (isNullShell) {
                        line = line + " §7" + nsPct + "%";
                    }
                    // Draw text (no big background box — matches Light/Chaotic HUD style)
                    mc.fontRenderer.drawStringWithShadow(line, baseX, baseY - 10, 0xFFFFFF);
                    // Abyss Mark: show current stacks (server writes HexVoidHudMarks)
                    boolean isAbyssMark = isAbyssMarkType(type);

                    // Cooldown bar (under the type line)
                    if (cdMax > 0 && rem > 0) {
                        Gui.drawRect(baseX, barY, baseX + BAR_W, barY + BAR_H, 0xAA000000);

                        float pct = (float) rem / (float) cdMax;
                        if (pct < 0f) pct = 0f;
                        if (pct > 1f) pct = 1f;

                        int fill = (int) (BAR_W * pct);
                        Gui.drawRect(baseX, barY, baseX + fill, barY + BAR_H, barColorForType(type));
                        long sec = (rem + 19) / 20;
                        String s = sec + "s";
                        int tx = baseX + BAR_W - mc.fontRenderer.getStringWidth(s) - 1;
                        int ty = barY - 8;
                        mc.fontRenderer.drawStringWithShadow(s, tx, ty, 0xFFFFFFFF);
                    }


                }

                if (voidSelected) {
                    // Null Shell: thin charge bar (always shown while equipped)
                    // Place it UNDER the cooldown bar line (if any), with a small gap.
                    if (isNullShell) {
                        int extraY = barY + BAR_H + 6;
                        int nsBarY = extraY + 2;
                        // Null Shell: hold/charge bar for 100% ability (double press then hold)
                        if (NS_HOLD_ACTIVE) {
                            int holdY = nsBarY;

                            float hp = NS_HOLD_PCT;
                            if (hp < 0f) hp = 0f;
                            if (hp > 2f) hp = 2f;
                            boolean over = hp > 1f;
                            float phase = over ? (hp - 1f) : hp;
                            if (phase < 0f) phase = 0f;
                            if (phase > 1f) phase = 1f;

                            if (over) {
                                drawSegmentedBarOverchargeGlow(baseX, holdY, BAR_W, NS_BAR_H, phase, NS_SEGMENTS, NS_SEG_GAP, now);
                                mc.fontRenderer.drawStringWithShadow("OC", baseX + BAR_W + 4, holdY - 3, 0xFFFFFFFF);
                            } else {
                                drawSegmentedBar(baseX, holdY, BAR_W, NS_BAR_H, phase, NS_SEGMENTS, NS_SEG_GAP, barColorForType(type));
                            }

                            nsBarY = holdY + NS_BAR_H + 3;
                        }

                        drawSegmentedBar(baseX, nsBarY, BAR_W, NS_BAR_H,
                                (nsMax > 0) ? ((float) nsCharge / (float) nsMax) : 0f,
                                NS_SEGMENTS, NS_SEG_GAP, barColorForType(type));
                    }
                }
            }

            // Tray passives (Abyss/Entropy/GW) — draw even when Void family is NOT selected.
            if (passiveCount > 0) {
                int blockH = TRAY_BLOCK_BASE_H + (passiveCount * (BAR_H + 9));
                int topY = HexPassiveProcTray.allocTopY(mc, event, p, blockH);
                int trayX = HexPassiveProcTray.baseX(sr, BAR_W);

                // Icon + small header
                drawVoidOrbIcon(mc, p, host, trayX - 18, (topY + 10) - 12);
                mc.fontRenderer.drawStringWithShadow("§5Void Effects§r", trayX, topY, 0xFFFFFFFF);

                int y = topY + TRAY_TITLE_TO_BARS_Y;
                y = drawVoidEffectBar(mc, p, host, trayX, y, BAR_W, "abyss");
                y = drawVoidEffectBar(mc, p, host, trayX, y, BAR_W, "entropy");
                y = drawVoidEffectBar(mc, p, host, trayX, y, BAR_W, "gw");
            }
        } finally {
            GL11.glColor4f(1f, 1f, 1f, 1f);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            GL11.glMatrixMode(prevMatrixMode);
        }
    }




    private static long getVoidEffectRem(Minecraft mc, ItemStack host, String key){
        if (mc == null || mc.theWorld == null || host == null || key == null) return 0L;
        NBTTagCompound tag = host.getTagCompound();
        if (tag == null) return 0L;
        String suf = key.trim().toLowerCase();
        if (suf.length() == 0) return 0L;
        int rem0 = 0;
        long stamp = 0L;
        try { rem0 = tag.getInteger("HexVoidHudLocalRem_" + suf); } catch (Throwable ignored) {}
        try { stamp = tag.getLong("HexVoidHudLocalStamp_" + suf); } catch (Throwable ignored) {}
        if (rem0 <= 0 || stamp <= 0L) return 0L;
        long nowLocal = mc.theWorld.getTotalWorldTime();
        long elapsed = nowLocal - stamp;
        if (elapsed < 0L) elapsed = 0L;
        long rem = (long) rem0 - elapsed;
        return rem > 0L ? rem : 0L;
    }

    /**
     * Draw an additional passive-effect bar (Abyss Mark / Entropy / Gravity Well) stamped by server into the HUD host.
     *
     * Server writes (suffix = key):
     *  - HexVoidHudAbility_<key>
     *  - HexVoidHudCDMax_<key>
     *  - HexVoidHudLocalStamp_<key>   (dimension-local timebase)
     *  - HexVoidHudLocalRem_<key>     (remaining ticks at stamp time)
     *
     * We count down purely client-side using local time so dimension changes don't explode the timer.
     *
     * @return next y after drawing (or original y if nothing drawn).
     */
    private static int drawVoidEffectBar(Minecraft mc, EntityPlayer p, ItemStack host, int x, int y, int w, String key) {
        if (mc == null || mc.theWorld == null || mc.fontRenderer == null) return y;
        if (host == null || key == null) return y;

        NBTTagCompound tag = host.getTagCompound();
        if (tag == null) return y;

        String suf = key.trim().toLowerCase();
        if (suf.length() == 0) return y;

        int rem0 = 0;
        long stamp = 0L;
        int max = 0;
        String ability = "";
        try { rem0 = tag.getInteger("HexVoidHudLocalRem_" + suf); } catch (Throwable ignored) {}
        try { stamp = tag.getLong("HexVoidHudLocalStamp_" + suf); } catch (Throwable ignored) {}
        try { max = tag.getInteger("HexVoidHudCDMax_" + suf); } catch (Throwable ignored) {}
        try { ability = safeStr(tag.getString("HexVoidHudAbility_" + suf)); } catch (Throwable ignored) {}

        if (rem0 <= 0 || stamp <= 0L) return y;

        long nowLocal = mc.theWorld.getTotalWorldTime();
        long elapsed = nowLocal - stamp;
        if (elapsed < 0L) elapsed = 0L;

        long rem = (long) rem0 - elapsed;
        if (rem <= 0L) return y;

        if (max <= 0) max = rem0;
        if (max <= 0) max = 1;

        if (ability.length() == 0) {
            if ("abyss".equals(suf)) ability = "Abyss Mark";
            else if ("entropy".equals(suf)) ability = "Entropy";
            else if ("gw".equals(suf)) ability = "Gravity Well";
            else ability = "Void";
        }

        // Label
        long sec = (rem + 19L) / 20L;
        String label = styleType(mc, ability) + " §7" + sec + "s";
        mc.fontRenderer.drawStringWithShadow(label, x, y - 8, 0xFFFFFFFF);

        // Bar (shares the same width/height as the main bar; we keep it compact)
        Gui.drawRect(x, y, x + w, y + BAR_H, 0xAA000000);

        float pct = (float) rem / (float) max;
        if (pct < 0f) pct = 0f;
        if (pct > 1f) pct = 1f;
        int fill = (int) (w * pct);

        // Color keyed by the ability name
        int col = barColorForType(ability);
        if (fill > 0) Gui.drawRect(x, y, x + fill, y + BAR_H, col);

        return y + BAR_H + 9;
    }


    /**
     * When the Void family is NOT selected, we only want to render anything if at least one
     * passive Void proc timer (abyss/entropy/gw) is currently active on the HUD host.
     *
     * Uses the dimension-local stamp/rem keys so switching dimensions won't blow up timers.
     */
    private static boolean hasAnyVoidPassiveHudActive(Minecraft mc, ItemStack host) {
        if (mc == null || mc.theWorld == null || host == null) return false;
        NBTTagCompound tag = host.getTagCompound();
        if (tag == null) return false;

        long nowLocal = mc.theWorld.getTotalWorldTime();

        return isPassiveActive(tag, nowLocal, "abyss")
                || isPassiveActive(tag, nowLocal, "entropy")
                || isPassiveActive(tag, nowLocal, "gw");
    }

    private static boolean isPassiveActive(NBTTagCompound tag, long nowLocal, String suf) {
        if (tag == null || suf == null) return false;
        try {
            int rem0 = tag.getInteger("HexVoidHudLocalRem_" + suf);
            long stamp = tag.getLong("HexVoidHudLocalStamp_" + suf);
            if (rem0 <= 0 || stamp <= 0L) return false;
            long elapsed = nowLocal - stamp;
            if (elapsed < 0L) elapsed = 0L;
            return ((long) rem0 - elapsed) > 0L;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Draw a segmented bar (visual-only). pct is 0..1.
     * Uses Gui.drawRect (1.7.10-safe) and a dark background per segment.
     */
    private static void drawSegmentedBar(int x, int y, int w, int h, float pct, int segments, int gap, int fillColor) {
        if (w <= 0 || h <= 0) return;
        if (pct < 0f) pct = 0f;
        if (pct > 1f) pct = 1f;

        if (segments <= 1) {
            Gui.drawRect(x, y, x + w, y + h, 0xAA000000);
            int fill = (int) (w * pct);
            if (fill > 0) Gui.drawRect(x, y, x + fill, y + h, fillColor);
            return;
        }

        int totalGap = gap * (segments - 1);
        int segW = (w - totalGap) / segments;
        if (segW < 1) segW = 1;

        float segProg = pct * segments;

        for (int i = 0; i < segments; i++) {
            int sx = x + i * (segW + gap);
            int ex = sx + segW;

            Gui.drawRect(sx, y, ex, y + h, 0xAA000000);

            float frac = segProg - i;
            if (frac < 0f) frac = 0f;
            if (frac > 1f) frac = 1f;

            int fill = (int) (segW * frac);
            if (fill > 0) {
                Gui.drawRect(sx, y, sx + fill, y + h, fillColor);
            }
        }
    }

    /**
     * Overcharge bar: glow + animated purple->red gradient (visual-only).
     * pct is 0..1. Uses per-segment solid colors to approximate a moving gradient.
     */
    private static void drawSegmentedBarOverchargeGlow(int x, int y, int w, int h, float pct, int segments, int gap, long worldTime) {
        if (w <= 0 || h <= 0) return;
        if (pct < 0f) pct = 0f;
        if (pct > 1f) pct = 1f;

        // Purple <-> Red gradient endpoints
        final int PURPLE = 0x7B2CFF;
        final int RED    = 0xFF2B5C;

        // Gradient scroll speed (ticks). 40 ticks ~= 2s
        float shift = (worldTime % 40L) / 40.0f;

        int s = Math.max(1, segments);
        int totalGap = (s > 1) ? (gap * (s - 1)) : 0;
        int segW = (s > 1) ? ((w - totalGap) / s) : w;
        if (segW < 1) segW = 1;

        float segProg = (s > 1) ? (pct * s) : pct;

        for (int i = 0; i < s; i++) {
            int sx = x + i * (segW + gap);
            int ex = sx + segW;

            // background
            Gui.drawRect(sx, y, ex, y + h, 0xAA000000);

            // Compute an animated gradient color per segment
            float t = (s <= 1) ? shift : (((float) i / (float) (s - 1)) + shift);
            t = t - (float) Math.floor(t); // wrap 0..1
            int rgb = lerpRgb(PURPLE, RED, t);

            // soft glow behind the segment (bigger rect, low alpha)
            int glow = (0x35 << 24) | rgb;
            Gui.drawRect(sx - 1, y - 1, ex + 1, y + h + 1, glow);

            int fillColor = 0xFF000000 | rgb;

            float frac;
            if (s <= 1) {
                frac = pct;
            } else {
                frac = segProg - i;
                if (frac < 0f) frac = 0f;
                if (frac > 1f) frac = 1f;
            }

            int fill = (int) (segW * frac);
            if (fill > 0) {
                Gui.drawRect(sx, y, sx + fill, y + h, fillColor);
            }
        }
    }

    private static int lerpRgb(int a, int b, float t) {
        if (t < 0f) t = 0f;
        if (t > 1f) t = 1f;
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int rr = (int) (ar + (br - ar) * t);
        int rg = (int) (ag + (bg - ag) * t);
        int rb = (int) (ab + (bb - ab) * t);
        return (rr << 16) | (rg << 8) | rb;
    }


    /**
     * Find the stack to read HUD NBT from.
     * Equipped-only (hand + armor) so the HUD doesn't appear just because it's in a backpack.
     */
    private static ItemStack findVoidHudHost(EntityPlayer p) {
        if (p == null) return null;

        // Held item
        ItemStack held = p.getCurrentEquippedItem();
        if (isVoidHudStack(held)) return held;

        // Armor
        if (p.inventory != null && p.inventory.armorInventory != null) {
            for (int i = 0; i < p.inventory.armorInventory.length; i++) {
                ItemStack a = p.inventory.armorInventory[i];
                if (isVoidHudStack(a)) return a;
            }
        }

        return null;
    }

    private static boolean isVoidHudStack(ItemStack s) {
        if (s == null) return false;
        NBTTagCompound tag = s.getTagCompound();
        if (tag == null) return false;

        // Legacy / base keys (single-bar HUD)
        if (tag.hasKey("HexVoidHudType")
                || tag.hasKey("HexVoidHudCDEnd")
                || tag.hasKey("HexVoidHudCDMax")
                || tag.hasKey("HexVoidHudLocalRem")
                || tag.hasKey("HexVoidHudLocalStamp")
                || tag.hasKey("HexVoidType")
                || tag.hasKey("HexVoidOrbType")
                || tag.hasKey("HexVoidHudAbility")) { // legacy
            return true;
        }

        // New per-effect passive HUD keys (written by HexOrbEffectsController#stampVoidHudEffect)
        if (tag.hasKey("HexVoidHudLocalRem_abyss") || tag.hasKey("HexVoidHudLocalRem_entropy") || tag.hasKey("HexVoidHudLocalRem_gw")
                || tag.hasKey("HexVoidHudLocalStamp_abyss") || tag.hasKey("HexVoidHudLocalStamp_entropy") || tag.hasKey("HexVoidHudLocalStamp_gw")
                || tag.hasKey("HexVoidHudCDEnd_abyss") || tag.hasKey("HexVoidHudCDEnd_entropy") || tag.hasKey("HexVoidHudCDEnd_gw")
                || tag.hasKey("HexVoidHudCDMax_abyss") || tag.hasKey("HexVoidHudCDMax_entropy") || tag.hasKey("HexVoidHudCDMax_gw")) {
            return true;
        }

        return false;
    }


    /**
     * Prefer an actual orb stack (icon) matching the host's type if present anywhere on the player.
     * Falls back to the host stack.
     */
    private static final int META_VOID_FLAT  = 22;
    private static final int META_VOID_MULTI = 23;

    /** True only for the physical Void orb items (NOT the socket-host tool/weapon). */
    private static boolean isActualVoidOrbItem(ItemStack s){
        if (s == null || s.getItem() == null) return false;
        try{
            if (!(s.getItem() instanceof ItemGemIcons)) return false;
            int meta = s.getItemDamage();
            return meta == META_VOID_FLAT || meta == META_VOID_MULTI;
        }catch(Throwable ignored){
            return false;
        }
    }

    /**
     * We want the HUD icon to ALWAYS be the Void orb icon.
     * Never return the socket-host item (e.g., a sword) just because it carries HexVoidType tags.
     */
    private static ItemStack findVoidIconStack(EntityPlayer p, ItemStack host) {
        if (p == null) return null;

        // 1) If the player is directly holding a Void orb item, use it.
        try{
            ItemStack held = p.getCurrentEquippedItem();
            if (isActualVoidOrbItem(held)) return held;
        }catch(Throwable ignored){}

        // 2) Otherwise, look for any Void orb in inventory/hotbar (best visual match, uses real item icon).
        try{
            if (p.inventory != null){
                int sz = p.inventory.getSizeInventory();
                for (int i = 0; i < sz; i++){
                    ItemStack s = p.inventory.getStackInSlot(i);
                    if (isActualVoidOrbItem(s)) return s;
                }
            }
        }catch(Throwable ignored){}

        // 3) If GemStacks are available client-side, try the socketed gem stack.
        try{
            if (host != null && HexSocketAPI.hasSocketData(host)){
                int filled = HexSocketAPI.getSocketsFilled(host);
                for (int i = 0; i < filled; i++){
                    ItemStack g = HexSocketAPI.getGemAt(host, i);
                    if (isActualVoidOrbItem(g)) return g;
                }
            }
        }catch(Throwable ignored){}

        // Fall back to drawing VOID_ICON_RL texture.
        return null;
    }


    /**
     * When the Void orb is socketed, there is no physical orb stack in the inventory.
     * This pulls the embedded gem ItemStack from the socket NBT so the HUD icon always shows the orb texture
     * (never the held weapon/tool icon).
     */
    private static ItemStack findSocketedVoidGemStack(ItemStack host) {
        if (host == null) return null;
        try {
            int filled = HexSocketAPI.getSocketsFilled(host);
            if (filled <= 0) return null;
            for (int i = 0; i < filled; i++) {
                String k = HexSocketAPI.getGemKeyAt(host, i);
                if (k == null) continue;
                String lk = k.toLowerCase();
                if (!lk.contains("void")) continue;

                ItemStack gem = HexSocketAPI.getGemAt(host, i);
                if (gem != null) return gem;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * Standard 1.7.10 item GUI render path.
     */
    private static void drawItem(ItemStack stack, int x, int y) {
        if (stack == null) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return;

        int prevMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            RenderHelper.enableGUIStandardItemLighting();
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);

            GL11.glEnable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glColor4f(1f, 1f, 1f, 1f);

            RI.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, x, y);
            RI.renderItemOverlayIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, x, y);
        } finally {
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glColor4f(1f, 1f, 1f, 1f);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            GL11.glMatrixMode(prevMatrixMode);
        }
    }

    /**
     * Draw a 16x16 icon directly from our void gem texture.
     * Used as a hard fallback so socketed Void HUD never shows the held weapon/tool icon.
     */

    private static void drawVoidOrbIcon(Minecraft mc, EntityPlayer p, ItemStack host, int x, int y) {
        // Default: flat Void orb icon
        ResourceLocation rl = VOID_ICON_RL;

        // Prefer socketed gem KEY (works client-side even if GemStacks are not synced)
        try {
            if (host != null && HexSocketAPI.hasSocketData(host)) {
                int filled = HexSocketAPI.getSocketsFilled(host);
                for (int i = 0; i < filled; i++) {
                    String k = HexSocketAPI.getGemKeyAt(host, i);
                    if (k == null) continue;
                    String lk = k.toLowerCase();
                    if (lk.indexOf("orb_gem_violet_void") >= 0) {
                        if (lk.indexOf("anim") >= 0 || lk.indexOf("8f") >= 0) rl = VOID_ICON_ANIM_RL;
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {}

        // If we didn't detect from socket keys, fall back to any real orb item in the player inventory
        if (rl == VOID_ICON_RL) {
            try {
                ItemStack orb = findVoidIconStack(p, null); // do NOT pass host (safety: never return host item)
                if (orb != null && isActualVoidOrbItem(orb)) {
                    if (orb.getItemDamage() == 23) rl = VOID_ICON_ANIM_RL;
                }
            } catch (Throwable ignored) {}
        }

        drawVoidIconTexture(rl, x, y);
    }

    private static void drawVoidIconTexture(int x, int y) {
        drawVoidIconTexture(VOID_ICON_RL, x, y);
    }

    private static void drawVoidIconTexture(ResourceLocation rl, int x, int y) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.getTextureManager().bindTexture(rl);

// draw 16x16 icon
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        try { GL11.glAlphaFunc(GL11.GL_GREATER, 0.01f); } catch (Throwable ignored) {}
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1f, 1f, 1f, 1f);

        // Select UVs:
        // - flat icon: full texture (0..1)
        // - anim sheet (8 frames stacked vertically): draw only the current frame
        float u0 = 0f, u1 = 1f;
        float v0 = 0f, v1 = 1f;
        if (rl == VOID_ICON_ANIM_RL) {
            try {
                int frames = 8;
                long ms = Minecraft.getSystemTime();
                int frame = (int)((ms / 120L) % frames); // ~8.3 FPS
                v0 = frame / (float)frames;
                v1 = (frame + 1) / (float)frames;
            } catch (Throwable ignored) {}
        }

        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertexWithUV(x,      y + 16, 0, u0, v1);
        t.addVertexWithUV(x + 16, y + 16, 0, u1, v1);
        t.addVertexWithUV(x + 16, y,      0, u1, v0);
        t.addVertexWithUV(x,      y,      0, u0, v0);
        t.draw();

        GL11.glPopAttrib();
    }


    /**
     * Apply a lightweight "format style" to the type label:
     * - If your HexFont renderer is active, use <grad ...> tags
     * - Otherwise, fall back to vanilla § colors so users don't see raw tags
     */
    private static String styleType(Minecraft mc, String type) {
        if (type == null) return "";
        String t = type.trim();
        if (t.length() == 0) return "";

        // If already formatted, don't double-wrap.
        if (t.indexOf('§') >= 0 || t.indexOf('<') >= 0) return t;

        boolean useTags = false;
        if (mc != null && mc.fontRenderer != null) {
            String fr = mc.fontRenderer.getClass().getName();
            useTags = (fr != null && (fr.contains("Hex") || fr.contains("hex")));
        }

        if (useTags) {
            if ("Solar".equalsIgnoreCase(t)) {
                return "<grad #ffe66d #ffb300 scroll=0.30>" + t + "</grad>";
            }
            if ("Gravity Well".equalsIgnoreCase(t)) {
                return "<grad #b26bff #7a5cff scroll=0.26>" + t + "</grad>";
            }
            if ("Void".equalsIgnoreCase(t)) {
                return "<grad #ff4fd8 #7a5cff #00ffd5 #ffe66d scroll=0.38>" + t + "</grad>";
            }
            return "<grad #b26bff #7a5cff scroll=0.26>" + t + "</grad>";

        }

        // Vanilla fallback
        if ("Solar".equalsIgnoreCase(t)) return "§e" + t;
        if ("Gravity Well".equalsIgnoreCase(t)) return "§5" + t;
        return "§d" + t;
    }

    private static boolean isAbyssMarkType(String type) {
        String t = safeStr(type).toLowerCase();
        return (t.contains("abyss") && t.contains("mark")) || "am".equals(t) || "abyssmark".equals(t) || "voidabyssmark".equals(t);
    }


    private static boolean isNullShellType(String type) {
        String t = safeStr(type).toLowerCase();
        return (t.contains("null") && t.contains("shell")) || "ns".equals(t) || "nullshell".equals(t) || "voidnullshell".equals(t);
    }

    /**
     * Bar fill color that roughly matches the type theme.
     * (We keep this lightweight: a single ARGB int.)
     */
    private static int barColorForType(String type) {
        String t = safeStr(type);
        if ("Solar".equalsIgnoreCase(t)) return 0xAAFFD36B;
        if ("Gravity Well".equalsIgnoreCase(t)) return 0xAA7A5CFF;
        if (t.toLowerCase().contains("abyss") && t.toLowerCase().contains("mark")) return 0xAA5A2FD8;
        if ("Void".equalsIgnoreCase(t)) return 0xAA7A5CFF;
        return 0xAA7A5CFF;
    }

    private static String safeStr(String s) {
        return (s == null) ? "" : s.trim();
    }

    /**
     * The dark "box" behind the centered item name is vanilla.
     * This tries (safely) to clear the countdown so it never shows while the HUD is active.
     *
     * If reflection fails (obf names mismatch), it simply does nothing (no crash).
     */
    private static void suppressVanillaHeldItemName(Minecraft mc, ItemStack held) {
        if (mc == null || mc.ingameGUI == null || held == null) return;

        try {
            GuiIngame gui = mc.ingameGUI;

            // remainingHighlightTicks (dev) / field_92016_l (SRG/obf-ish)
            Field ticksF = ReflectionHelper.findField(GuiIngame.class, "remainingHighlightTicks", "field_92016_l");

            // highlightingItemStack (dev) / field_92017_k (SRG/obf-ish)
            Field stackF;
            try {
                stackF = ReflectionHelper.findField(GuiIngame.class, "highlightingItemStack", "field_92017_k");
            } catch (Throwable ignored) {
                // Dev fallback: find the first ItemStack field (there's typically only one)
                stackF = null;
                Field[] fs = GuiIngame.class.getDeclaredFields();
                for (int i = 0; i < fs.length; i++) {
                    if (fs[i].getType() == ItemStack.class) { stackF = fs[i]; break; }
                }
                if (stackF != null) stackF.setAccessible(true);
            }

            if (ticksF == null || stackF == null) return;
            ticksF.setAccessible(true);
            stackF.setAccessible(true);

            ItemStack hi = (ItemStack) stackF.get(gui);
            if (hi != null && ItemStack.areItemStacksEqual(hi, held)) {
                ticksF.setInt(gui, 0);
            }
        } catch (Throwable ignored) {
            // no-op: never crash the client just to hide a cosmetic box
        }
    }

    // Convenience if you want to self-register from anywhere:
    public static void register() {
        MinecraftForge.EVENT_BUS.register(new VoidOrbHudOverlay());
    }
}
