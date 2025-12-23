package com.example.examplemod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.IChatComponent;

import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.GuiScreenEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.util.ChatComponentText;

import java.lang.reflect.Field;

/**
 * ChatHexHandler – GLOBAL SAFE VERSION
 *  - Installs HexFontRenderer as the global fontRendererObj
 *  - Hex is used for chat HUD, chat GUI, tooltips, inventory, etc.
 *  - Automatically falls back to vanilla for JourneyMap / modlist-like screens
 */
public class ChatHexHandler {

    // ──────────────────────────────────────────────────────────
    // Reflected vanilla fields
    // ──────────────────────────────────────────────────────────
    private static Field F_FONT;
    private static Field F_SGF;

    private static Field findFontField() {
        if (F_FONT != null) return F_FONT;
        for (String n : new String[]{"fontRendererObj", "fontRenderer", "field_71466_p"}) {
            try {
                Field f = Minecraft.class.getDeclaredField(n);
                f.setAccessible(true);
                F_FONT = f;
                System.out.println("[HexFont] Found font field: " + n);
                return f;
            } catch (Throwable ignored) {}
        }
        throw new RuntimeException("Could not locate Minecraft.fontRenderer");
    }

    private static Field findStdGalacticField() {
        if (F_SGF != null) return F_SGF;
        for (String n : new String[]{"standardGalacticFontRenderer", "field_71463_r"}) {
            try {
                Field f = Minecraft.class.getDeclaredField(n);
                f.setAccessible(true);
                F_SGF = f;
                System.out.println("[HexFont] Found standardGalacticFontRenderer: " + n);
                return f;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    // ──────────────────────────────────────────────────────────
    // Accessors
    // ──────────────────────────────────────────────────────────
    private static FontRenderer getMcFont() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            FontRenderer fr = (mc == null) ? null : (FontRenderer) findFontField().get(mc);
            if (fr != null) {
                // one noisy log at startup is fine
                // System.out.println("[HexFont] getMcFont = " + fr.getClass().getName());
            }
            return fr;
        } catch (Throwable t) {
            System.out.println("[HexFont] getMcFont ERROR " + t);
            return null;
        }
    }

    private static FontRenderer getMcStdGalactic() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            Field f = findStdGalacticField();
            return (mc == null || f == null) ? null : (FontRenderer) f.get(mc);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void setMcFonts(FontRenderer fr) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || fr == null) return;

            findFontField().set(mc, fr);
            Field sgf = findStdGalacticField();
            if (sgf != null) {
                sgf.set(mc, fr);
            }

            appliedFont = fr;
            System.out.println("[HexFont] setMcFonts -> " + fr.getClass().getName());

        } catch (Throwable t) {
            System.out.println("[HexFont] setMcFonts ERROR " + t);
        }
    }

    // ──────────────────────────────────────────────────────────
    // State
    // ──────────────────────────────────────────────────────────
    private static FontRenderer    vanillaFont;
    private static HexFontRenderer hexFont;
    private static FontRenderer    appliedFont;
    private static boolean         installed;

    public static String formatInline(String raw) {
        if (raw == null) return "";

        String out = raw;

        // convert &c, &6, &l, &o etc → §c, §6, §l, §o
        out = out.replaceAll("(?i)&([0-9A-FK-OR])", "§$1");

        // convert &##RRGGBB → §#RRGGBB
        out = out.replaceAll("&##([0-9A-Fa-f]{6})", "§#$1");

        return out;
    }

    /**
     * Ensure we have a HexFontRenderer and that it is installed
     * (unless we’re on a JourneyMap screen).
     */
    private static void ensureInstalled() {
        FontRenderer current = getMcFont();
        if (current == null) return;

        // Detect base font (vanilla)
        FontRenderer baseNow = (current instanceof HexFontRenderer)
                ? vanillaFont
                : current;

        if (!installed) {
            vanillaFont = (baseNow != null) ? baseNow : current;
            hexFont = new HexFontRenderer(vanillaFont);
            installed = true;

            System.out.println("[HexFont] Created HexFontRenderer wrapping "
                    + vanillaFont.getClass().getName());

            // FIRST INSTALL: immediately switch Minecraft to hex
            setMcFonts(hexFont);
            return;
        }

        // Resource pack or something changed the base font
        if (vanillaFont != baseNow && baseNow != null) {
            System.out.println("[HexFont] Base font changed (resource pack?), rebuilding HexFontRenderer");
            vanillaFont = baseNow;
            hexFont = new HexFontRenderer(vanillaFont);
            // Re-apply hex
            setMcFonts(hexFont);
        }
    }

    // ──────────────────────────────────────────────────────────
    // Screen type detection
    // ──────────────────────────────────────────────────────────
    private static boolean isJourneyMap(GuiScreen s) {
        if (s == null) return false;
        String cn = s.getClass().getName().toLowerCase();
        return cn.contains("journeymap") || cn.startsWith("jm.");
    }

    private static boolean isChatGui(GuiScreen s) {
        if (s == null) return false;
        String cn = s.getClass().getName();
        return cn.contains("GuiChat") || cn.contains("ChatScreen");
    }

    // ──────────────────────────────────────────────────────────
    // Events
    // ──────────────────────────────────────────────────────────
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;

        ensureInstalled();

        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen scr = (mc != null) ? mc.currentScreen : null;

        // 1) JourneyMap-style GUIs → vanilla font (avoid layout weirdness)
        if (isJourneyMap(scr)) {
            if (appliedFont != vanillaFont && vanillaFont != null) {
                System.out.println("[HexFont] Switching to vanilla font for JourneyMap GUI");
                setMcFonts(vanillaFont);
            }
            return;
        }

        // 2) EVERYTHING ELSE (HUD, chat HUD, chat GUI, inventory, creative, etc.) → hex
        if (hexFont != null && appliedFont != hexFont) {
            System.out.println("[HexFont] Ensuring HexFontRenderer is active (tick)");
            setMcFonts(hexFont);
        }
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent e) {
        ensureInstalled();

        // Match tick logic: JourneyMap → vanilla, everything else → hex
        if (isJourneyMap(e.gui)) {
            if (vanillaFont != null && appliedFont != vanillaFont) {
                System.out.println("[HexFont] onGuiOpen -> JourneyMap, reverting to vanilla");
                setMcFonts(vanillaFont);
            }
        } else if (hexFont != null && appliedFont != hexFont) {
            System.out.println("[HexFont] onGuiOpen -> non-JourneyMap, applying hex");
            setMcFonts(hexFont);
        }
    }

    @SubscribeEvent
    public void onClientChat(ClientChatReceivedEvent e) {
        // Do nothing.
        // Incoming messages (including GK debug) will be rendered directly
        // by HexFontRenderer, which already understands <grad>/<wave>/§#.
    }

    @SubscribeEvent
    public void onRenderChatInput(GuiScreenEvent.DrawScreenEvent.Post e) {
        if (!(e.gui instanceof GuiChat))
            return;

        Minecraft mc = Minecraft.getMinecraft();
        GuiChat chat = (GuiChat) e.gui;

        try {
            // Reflect input box
            Field f = GuiChat.class.getDeclaredField("inputField");
            f.setAccessible(true);
            GuiTextField input = (GuiTextField) f.get(chat);

            // What the player is currently typing
            String raw = input.getText();

            // Normalize inline legacy & codes and hex shortcuts
            String normalized = ChatHexHandler.formatInline(raw);
            // normalized = HexChatExpand.expandHex(normalized); // optional

            // Let the preview system handle tags + styling
            String preview = HexPreview.renderPreview(normalized);

            if (preview == null || preview.isEmpty())
                return;

            int x = input.xPosition + 4;
            int y = input.yPosition - 10;     // ABOVE input box
            int yInline = input.yPosition + 6; // INLINE overlay preview

            FontRenderer fr = ChatHexHandler.getActiveFont();

            fr.drawString(preview, x, y, 0xFFFFFF);
            fr.drawString(preview, x, yInline, 0xFFFFFF);

        } catch (Throwable ignored) {}
    }

    // ──────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────
    public static String render(String raw) {
        if (raw == null) return "";
        try {
            return ChatHexHandler.formatInline(raw);
        } catch (Throwable t) {
            return raw;
        }
    }

    public static FontRenderer getActiveFont() {
        // Whatever font is currently applied (hex or vanilla)
        return appliedFont != null ? appliedFont : getMcFont();
    }

    public static void ensureClientFonts() {
        try {
            ensureInstalled();
            if (hexFont != null) {
                System.out.println("[HexFont] ensureClientFonts() applying HexFontRenderer");
                setMcFonts(hexFont);
            }
        } catch (Throwable t) {
            System.out.println("[HexFont] ensureClientFonts ERROR " + t);
        }
    }
}
