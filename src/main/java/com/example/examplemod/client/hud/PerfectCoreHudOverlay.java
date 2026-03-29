package com.example.examplemod.client.hud;

import com.example.examplemod.api.HexSocketAPI;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.awt.Color;

/**
 * Perfect Core passive HUD — MC 1.7.10
 *
 * Renders up to 2 rows:
 * - Prismatic Barrage (cooldown bar)
 * - Prismatic Drive (buff bar while active, cooldown bar afterward)
 */
@SideOnly(Side.CLIENT)
public class PerfectCoreHudOverlay {

    private static final RenderItem ITEM_RENDER = new RenderItem();

    private static final ResourceLocation PERFECT_CORE_ICON_ANIM =
            new ResourceLocation("hexcolorcodes", "textures/items/gems/evolved_rainbow_gem_64_anim_8f.png");

    private static final int BAR_W = 104;
    private static final int BAR_H = 5;
    private static final int ROW_H = 28;

    private static final String SUF_BURST = "burst";
    private static final String SUF_DRIVE = "drive";
    private static final String SUF_BARRIER = "barrier";

    @SubscribeEvent
    public void onHud(RenderGameOverlayEvent.Post event) {
        if (event == null) return;
        if (event.type != RenderGameOverlayEvent.ElementType.EXPERIENCE) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.theWorld == null) return;
        if (mc.currentScreen != null) return;

        EntityPlayer p = mc.thePlayer;
        ItemStack host = findHost(p);
        if (host == null) return;

        NBTTagCompound tag = host.getTagCompound();
        if (tag == null) return;

        long nowLocal = mc.theWorld.getTotalWorldTime();

        HudEntry burst = buildBurstEntry(tag, nowLocal);
        HudEntry drive = buildDriveEntry(tag, nowLocal);
        HudEntry barrier = buildBarrierEntry(tag, nowLocal);
        if (burst == null && drive == null && barrier == null) return;

        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int rows = 0;
        if (burst != null) rows++;
        if (drive != null) rows++;
        if (barrier != null) rows++;

        int topY = HexPassiveProcTray.allocTopY(mc, event, p, rows * ROW_H);
        int baseX = HexPassiveProcTray.baseX(sr, BAR_W);
        int y = topY + 10;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1f, 1f, 1f, 1f);

            if (burst != null) {
                renderEntry(mc, baseX, y, burst, nowLocal);
                y += ROW_H;
            }
            if (drive != null) {
                renderEntry(mc, baseX, y, drive, nowLocal);
                y += ROW_H;
            }
            if (barrier != null) {
                renderEntry(mc, baseX, y, barrier, nowLocal);
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            GL11.glColor4f(1f, 1f, 1f, 1f);
        }
    }

    private static void renderEntry(Minecraft mc, int baseX, int baseY, HudEntry e, long nowLocal) {
        int iconX = baseX - 18;
        int iconY = baseY - 12;

        drawPerfectCoreIcon(mc, findPerfectCoreIconStack(findHost(mc != null ? mc.thePlayer : null)), iconX, iconY);
        mc.fontRenderer.drawStringWithShadow(styleType(mc, e.title), baseX, baseY - 10, 0xFFFFFFFF);

        drawLabeledTime(mc, baseX, baseY + 1, BAR_W, styleLabel(mc, e.ability, e.buffActive), e.rem);
        Gui.drawRect(baseX, baseY + 11, baseX + BAR_W, baseY + 11 + BAR_H, 0xAA000000);

        float pct = (float) e.rem / (float) Math.max(1L, e.max);
        if (pct < 0f) pct = 0f;
        if (pct > 1f) pct = 1f;

        int fill = (int) (BAR_W * pct);
        if (fill > 0) {
            drawRainbowPulseBar(baseX, baseY + 11, fill, BAR_H, nowLocal, e.buffActive);
        }
    }

    private static HudEntry buildBurstEntry(NBTTagCompound tag, long nowLocal) {
        long rem = countdown(tag, "HexPerfectCoreHudLocalStamp_" + SUF_BURST, "HexPerfectCoreHudLocalRem_" + SUF_BURST, nowLocal);
        if (rem <= 0L) return null;

        HudEntry e = new HudEntry();
        e.title = defaultIfEmpty(tag.getString("HexPerfectCoreHudType_" + SUF_BURST), "Perfect Core");
        e.ability = defaultIfEmpty(tag.getString("HexPerfectCoreHudAbility_" + SUF_BURST), "Prismatic Barrage");
        e.rem = rem;
        e.max = Math.max(rem, safeInt(tag, "HexPerfectCoreHudCDMax_" + SUF_BURST));
        e.buffActive = false;
        return e;
    }

    private static HudEntry buildDriveEntry(NBTTagCompound tag, long nowLocal) {
        long buffRem = countdown(tag, "HexPerfectCoreHudBuffLocalStamp_" + SUF_DRIVE, "HexPerfectCoreHudBuffLocalRem_" + SUF_DRIVE, nowLocal);
        if (buffRem > 0L) {
            HudEntry e = new HudEntry();
            e.title = defaultIfEmpty(tag.getString("HexPerfectCoreHudType_" + SUF_DRIVE), "Perfect Core");
            e.ability = defaultIfEmpty(tag.getString("HexPerfectCoreHudAbility_" + SUF_DRIVE), "Prismatic Drive");
            e.rem = buffRem;
            e.max = Math.max(buffRem, safeInt(tag, "HexPerfectCoreHudBuffMax_" + SUF_DRIVE));
            e.buffActive = true;
            return e;
        }

        long cdRem = countdown(tag, "HexPerfectCoreHudCDLocalStamp_" + SUF_DRIVE, "HexPerfectCoreHudCDLocalRem_" + SUF_DRIVE, nowLocal);
        if (cdRem <= 0L) return null;

        HudEntry e = new HudEntry();
        e.title = defaultIfEmpty(tag.getString("HexPerfectCoreHudType_" + SUF_DRIVE), "Perfect Core");
        e.ability = "Cooldown";
        e.rem = cdRem;
        e.max = Math.max(cdRem, safeInt(tag, "HexPerfectCoreHudCDMax_" + SUF_DRIVE));
        e.buffActive = false;
        return e;
    }

    private static HudEntry buildBarrierEntry(NBTTagCompound tag, long nowLocal) {
        long buffRem = countdown(tag, "HexPerfectCoreHudBuffLocalStamp_" + SUF_BARRIER, "HexPerfectCoreHudBuffLocalRem_" + SUF_BARRIER, nowLocal);
        if (buffRem > 0L) {
            HudEntry e = new HudEntry();
            e.title = defaultIfEmpty(tag.getString("HexPerfectCoreHudType_" + SUF_BARRIER), "Perfect Core");
            e.ability = defaultIfEmpty(tag.getString("HexPerfectCoreHudAbility_" + SUF_BARRIER), "Perfect Barrier");
            e.rem = buffRem;
            e.max = Math.max(buffRem, safeInt(tag, "HexPerfectCoreHudBuffMax_" + SUF_BARRIER));
            e.buffActive = true;
            return e;
        }

        long cdRem = countdown(tag, "HexPerfectCoreHudCDLocalStamp_" + SUF_BARRIER, "HexPerfectCoreHudCDLocalRem_" + SUF_BARRIER, nowLocal);
        if (cdRem <= 0L) return null;

        HudEntry e = new HudEntry();
        e.title = defaultIfEmpty(tag.getString("HexPerfectCoreHudType_" + SUF_BARRIER), "Perfect Core");
        e.ability = "Cooldown";
        e.rem = cdRem;
        e.max = Math.max(cdRem, safeInt(tag, "HexPerfectCoreHudCDMax_" + SUF_BARRIER));
        e.buffActive = false;
        return e;
    }

    private static long countdown(NBTTagCompound tag, String stampKey, String remKey, long nowLocal) {
        if (tag == null) return 0L;
        int rem0 = safeInt(tag, remKey);
        long stamp = safeLong(tag, stampKey);
        if (rem0 <= 0 || stamp <= 0L) return 0L;

        long elapsed = nowLocal - stamp;
        if (elapsed < 0L) elapsed = 0L;
        long rem = (long) rem0 - elapsed;
        return (rem > 0L) ? rem : 0L;
    }

    private static ItemStack findHost(EntityPlayer p) {
        if (p == null) return null;

        ItemStack held = p.getCurrentEquippedItem();
        if (isHostStack(held)) return held;

        if (p.inventory != null && p.inventory.armorInventory != null) {
            for (int i = 0; i < p.inventory.armorInventory.length; i++) {
                ItemStack a = p.inventory.armorInventory[i];
                if (isHostStack(a)) return a;
            }
        }
        return null;
    }

    private static boolean isHostStack(ItemStack s) {
        if (s == null) return false;
        NBTTagCompound tag = s.getTagCompound();
        if (tag == null) return false;
        return tag.hasKey("HexPerfectCoreHudLocalRem_" + SUF_BURST)
                || tag.hasKey("HexPerfectCoreHudBuffLocalRem_" + SUF_DRIVE)
                || tag.hasKey("HexPerfectCoreHudCDLocalRem_" + SUF_DRIVE)
                || tag.hasKey("HexPerfectCoreHudBuffLocalRem_" + SUF_BARRIER)
                || tag.hasKey("HexPerfectCoreHudCDLocalRem_" + SUF_BARRIER);
    }

    private static String styleType(Minecraft mc, String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.length() == 0) return "";
        if (t.indexOf('§') >= 0 || t.indexOf('<') >= 0) return t;

        boolean useTags = false;
        if (mc != null && mc.fontRenderer != null) {
            String fr = mc.fontRenderer.getClass().getName();
            useTags = (fr != null && (fr.contains("Hex") || fr.contains("hex")));
        }

        if (useTags) {
            return "<shadow><pulse amp=0.30 speed=0.78><grad #FFF6A8 #7AD7FF #7A8DFF #FF7AD8 #FFF6A8 scroll=0.44 styles=l>" + t + "</grad></pulse></shadow>";
        }

        return "§b§l" + t + "§r";
    }

    private static String styleLabel(Minecraft mc, String s, boolean buff) {
        String t = (s == null) ? "" : s.trim();
        if (t.length() == 0) t = buff ? "Buff" : "Cooldown";
        if (t.indexOf('§') >= 0 || t.indexOf('<') >= 0) return t;

        boolean useTags = false;
        if (mc != null && mc.fontRenderer != null) {
            String fr = mc.fontRenderer.getClass().getName();
            useTags = (fr != null && (fr.contains("Hex") || fr.contains("hex")));
        }

        if (useTags) {
            if (buff) {
                return "<shadow><pulse amp=0.22 speed=0.86><grad #9CFFF2 #7AD7FF #7A8DFF #FF7AD8 scroll=0.34 styles=lo>" + t + "</grad></pulse></shadow>";
            }
            return "<shadow><pulse amp=0.16 speed=0.70><grad #EDEDED #BDBDBD #9A9A9A scroll=0.18 styles=o>" + t + "</grad></pulse></shadow>";
        }

        return buff ? ("§b" + t + " §aBuff§r") : ("§7" + t + "§r");
    }

    private static void drawLabeledTime(Minecraft mc, int x, int y, int w, String left, long ticks) {
        if (mc == null || mc.fontRenderer == null) return;
        String right = "§f" + formatSeconds(ticks) + "s";
        mc.fontRenderer.drawStringWithShadow(left, x, y, 0xFFFFFFFF);
        int rw = mc.fontRenderer.getStringWidth(right);
        mc.fontRenderer.drawStringWithShadow(right, x + w - rw, y, 0xFFFFFFFF);
    }

    private static ItemStack findPerfectCoreIconStack(ItemStack host) {
        if (host == null) return null;
        try {
            if (HexSocketAPI.hasSocketData(host)) {
                int filled = HexSocketAPI.getSocketsFilled(host);
                for (int i = 0; i < filled; i++) {
                    ItemStack gem = HexSocketAPI.getGemAt(host, i);
                    String key = HexSocketAPI.getGemKeyAt(host, i);
                    if (looksLikePerfectCore(gem, key)) {
                        ItemStack copy = gem.copy();
                        copy.stackSize = 1;
                        return copy;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static boolean looksLikePerfectCore(ItemStack gem, String key) {
        if (key != null) {
            String lk = key.toLowerCase();
            if (lk.indexOf("perfect_core") >= 0 || lk.indexOf("evolved_rainbow_gem_64_anim_8f") >= 0) return true;
        }
        if (gem != null) {
            try {
                String dn = gem.getDisplayName();
                if (dn != null) {
                    String ld = dn.toLowerCase();
                    if (ld.indexOf("perfect core") >= 0) return true;
                }
            } catch (Throwable ignored) {}
            try {
                NBTTagCompound tg = gem.getTagCompound();
                if (tg != null) {
                    String src = tg.getString("HexGemSource");
                    String fam = tg.getString("HexEvolvedFamily");
                    if ("evolved".equalsIgnoreCase(src) && "rainbow".equalsIgnoreCase(fam)) return true;
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }

    private static void drawPerfectCoreIcon(Minecraft mc, ItemStack icon, int x, int y) {
        if (mc == null) return;
        if (icon != null) {
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            try {
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glDisable(GL11.GL_FOG);
                GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glColor4f(1f, 1f, 1f, 1f);

                RenderHelper.enableGUIStandardItemLighting();
                try {
                    ITEM_RENDER.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), icon, x, y);
                    ITEM_RENDER.renderItemOverlayIntoGUI(mc.fontRenderer, mc.getTextureManager(), icon, x, y);
                } finally {
                    RenderHelper.disableStandardItemLighting();
                    GL11.glDisable(GL11.GL_LIGHTING);
                    GL11.glDisable(GL12.GL_RESCALE_NORMAL);
                    GL11.glColor4f(1f, 1f, 1f, 1f);
                }
            } finally {
                GL11.glPopAttrib();
            }
            return;
        }

        drawAnimatedVerticalIcon(PERFECT_CORE_ICON_ANIM, x, y, 8, 120L);
    }

    private static void drawAnimatedVerticalIcon(ResourceLocation rl, int x, int y, int frames, long frameMs) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || rl == null) return;

        mc.getTextureManager().bindTexture(rl);

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glColor4f(1f, 1f, 1f, 1f);

        float u0 = 0f;
        float u1 = 1f;
        float v0 = 0f;
        float v1 = 1f;

        if (frames > 1) {
            int frame = (int) ((Minecraft.getSystemTime() / Math.max(1L, frameMs)) % frames);
            v0 = frame / (float) frames;
            v1 = (frame + 1) / (float) frames;
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

    private static void drawRainbowPulseBar(int x, int y, int w, int h, long now, boolean buffActive) {
        if (w <= 0 || h <= 0) return;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        Tessellator t = Tessellator.instance;
        int step = 2;

        for (int sx = 0; sx < w; sx += step) {
            int ex = Math.min(w, sx + step);
            float pos = (sx / (float)Math.max(1, w));

            double wave = Math.sin((now * (buffActive ? 0.33D : 0.24D)) + (sx * 0.40D));
            float wf = (float)(0.5D + 0.5D * wave);

            float hue = (float)((now * 0.0065D) % 1.0D) + pos * 0.95f;
            hue = hue - (float)Math.floor(hue);

            float sat = buffActive ? 0.78f : 0.96f;
            float bri = buffActive ? (0.82f + 0.18f * wf) : (0.72f + 0.28f * wf);
            int rgb = Color.HSBtoRGB(hue, sat, bri) & 0x00FFFFFF;
            int alpha = buffActive ? (170 + (int)(50.0F * wf)) : (185 + (int)(55.0F * wf));

            t.startDrawingQuads();
            t.setColorRGBA_I(rgb, alpha);
            t.addVertex(x + sx, y + h, 0);
            t.addVertex(x + ex, y + h, 0);
            t.addVertex(x + ex, y, 0);
            t.addVertex(x + sx, y, 0);
            t.draw();

            if ((sx & 3) == 0) {
                int sparkleAlpha = Math.min(255, alpha + 22);
                t.startDrawingQuads();
                t.setColorRGBA_I(0xFFFFFF, sparkleAlpha);
                t.addVertex(x + sx, y + 1, 0);
                t.addVertex(x + sx + 1, y + 1, 0);
                t.addVertex(x + sx + 1, y, 0);
                t.addVertex(x + sx, y, 0);
                t.draw();
            }
        }

        GL11.glPopAttrib();
    }

    private static String formatSeconds(long ticks) {
        if (ticks <= 0L) return "0.0";
        float s = ticks / 20.0F;
        int t = Math.round(s * 10.0F);
        if (t < 0) t = 0;
        return (t / 10) + "." + (t % 10);
    }

    private static int safeInt(NBTTagCompound tag, String k) {
        try { return (tag != null && k != null) ? tag.getInteger(k) : 0; } catch (Throwable t) { return 0; }
    }

    private static long safeLong(NBTTagCompound tag, String k) {
        try { return (tag != null && k != null) ? tag.getLong(k) : 0L; } catch (Throwable t) { return 0L; }
    }

    private static String defaultIfEmpty(String s, String def) {
        return (s == null || s.length() == 0) ? def : s;
    }

    private static final class HudEntry {
        String title;
        String ability;
        long rem;
        long max;
        boolean buffActive;
    }
}
