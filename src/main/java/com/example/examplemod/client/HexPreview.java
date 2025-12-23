package com.example.examplemod.client;

import net.minecraft.client.gui.FontRenderer;

/**
 * HexPreview — CLOSED-TAG LIVE PREVIEW
 * - Shows formats only when their tags are balanced (open + close present)
 * - Supports & / § legacy codes in the input box
 * - Uses *_LIVE helpers so you see a color instead of literal tags
 */
public final class HexPreview {

    private HexPreview() {}

    /** Returns formatted preview for the chat input line. */
    public static String renderPreview(String raw) {
        if (raw == null || raw.isEmpty())
            return "";

        String s = raw;

        try {
            // 1) Allow legacy & formatting in the input (&c, &l, &##RRGGBB → § codes)
            s = ChatHexHandler.formatInline(s);

            // 2) Only animate when tags look "complete" (have both open + close)
            boolean completeGrad   = s.matches("(?i).*<grad[^>]*>.*</grad>.*");
            boolean completeRain   = s.matches("(?i).*<rainbow[^>]*>.*</rainbow>.*");
            boolean completePulse  = s.matches("(?i).*<pulse[^>]*>.*</pulse>.*");

            // Use LIVE helpers, but only if tag pair is present
            if (completeGrad) {
                s = HexChatExpand.expandGradients_LIVE(s);
            }
            if (completeRain) {
                s = HexChatExpand.expandRainbow_LIVE(s);
            }
            if (completePulse) {
                s = HexChatExpand.expandPulse_LIVE(s);
            }

            // NOTE: we deliberately do NOT call expandHex() here,
            // so we don't break <grad #RRGGBB ...> attributes.
            return s;

        } catch (Throwable t) {
            // if anything explodes, just show the raw text
            return raw;
        }
    }

    /** Safe width calc for layout helpers (if you ever need it) */
    public static int getHexWidth(String raw) {
        try {
            FontRenderer fr = ChatHexHandler.getActiveFont();
            return fr != null ? fr.getStringWidth(renderPreview(raw)) : raw.length() * 6;
        } catch (Throwable ignored) {
            return raw.length() * 6;
        }
    }
}
