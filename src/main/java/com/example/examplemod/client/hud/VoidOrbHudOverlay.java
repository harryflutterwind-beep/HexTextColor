package com.example.examplemod.client.hud;

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

    // HUD sizing
    private static final int BAR_W = 104;
    private static final int BAR_H = 3;

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

        ItemStack host = findVoidHudHost(p);
        if (host == null) return;

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
        long rem = cdEnd - now;
        if (rem < 0) rem = 0;



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

        // Centered above XP bar
        int baseX = (sw / 2) - (BAR_W / 2);
        int baseY = sh - 60 - HUD_Y_OFFSET;
// Icon (prefer actual orb stack that matches the host's type)
        ItemStack iconStack = findVoidIconStack(p, host);
        int iconX = baseX - 18;
        int iconY = baseY - 12;

        // Optional: suppress VANILLA selected-item-name popup ("Void Gem Orb <Enhanced>") while this HUD is active
        if (SUPPRESS_VANILLA_HELD_NAME_POPUP) {
            suppressVanillaHeldItemName(mc, p.getCurrentEquippedItem());
        }

        // Render orb icon
        if (iconStack != null) {
            drawItem(iconStack, iconX, iconY);
        }

        // Build the type line: ONLY type; append seconds only when on cooldown
        String typeStyled = styleType(mc, type);
        String line = typeStyled;
        if (isNullShell) {
            line = line + " §7" + nsPct + "%";
        }
        // Draw text (no big background box — matches Light/Chaotic HUD style)
        mc.fontRenderer.drawStringWithShadow(line, baseX, baseY - 10, 0xFFFFFF);

        // Abyss Mark: show current stacks (server writes HexVoidHudMarks)
        boolean isAbyssMark = isAbyssMarkType(type);

        // Cooldown bar (under the type line)
        int barY = baseY + 10;
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

        // Null Shell: thin charge bar (always shown while equipped)
        if (isNullShell) {
            int nsBarY = barY + 6;
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

        return tag.hasKey("HexVoidHudType")
                || tag.hasKey("HexVoidHudCDEnd")
                || tag.hasKey("HexVoidHudCDMax")
                || tag.hasKey("HexVoidType")
                || tag.hasKey("HexVoidOrbType")
                || tag.hasKey("HexVoidHudAbility"); // legacy
    }

    /**
     * Prefer an actual orb stack (icon) matching the host's type if present anywhere on the player.
     * Falls back to the host stack.
     */
    private static ItemStack findVoidIconStack(EntityPlayer p, ItemStack host) {
        if (p == null) return host;

        String want = "";
        if (host != null && host.getTagCompound() != null) {
            NBTTagCompound ht = host.getTagCompound();
            want = safeStr(ht.getString("HexVoidHudType"));
            if (want.length() == 0 && ht.hasKey("HexVoidType")) want = safeStr(ht.getString("HexVoidType"));
            if (want.length() == 0 && ht.hasKey("HexVoidOrbType")) want = safeStr(ht.getString("HexVoidOrbType"));
        }

        // Held
        ItemStack held = p.getCurrentEquippedItem();
        if (isVoidOrbCandidate(held, want)) return held;

        // Armor
        if (p.inventory != null && p.inventory.armorInventory != null) {
            for (int i = 0; i < p.inventory.armorInventory.length; i++) {
                ItemStack a = p.inventory.armorInventory[i];
                if (isVoidOrbCandidate(a, want)) return a;
            }
        }

        // Main inventory
        if (p.inventory != null && p.inventory.mainInventory != null) {
            for (int i = 0; i < p.inventory.mainInventory.length; i++) {
                ItemStack s = p.inventory.mainInventory[i];
                if (isVoidOrbCandidate(s, want)) return s;
            }
        }

        return host;
    }

    private static boolean isVoidOrbCandidate(ItemStack s, String wantType) {
        if (s == null) return false;
        NBTTagCompound t = s.getTagCompound();
        if (t == null) return false;

        String type = safeStr(t.getString("HexVoidType"));
        if (type.length() == 0) type = safeStr(t.getString("HexVoidOrbType"));
        if (type.length() == 0) type = safeStr(t.getString("HexVoidHudType"));
        if (type.length() == 0) return false;

        return (wantType == null || wantType.length() == 0) || type.equalsIgnoreCase(wantType);
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