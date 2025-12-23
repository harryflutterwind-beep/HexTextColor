// src/main/java/com/example/examplemod/client/ChatSendHook.java
package com.example.examplemod.client;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatSendHook {

    private ChatSendHook() {}

    // &wave, &pulse, &zoom, &shake, etc at the BEGINNING of the line = full message style
    private static final Pattern LEADING_STYLE =
            Pattern.compile(
                    "^\\s*&(?:(wave" +
                            "|pulse" +
                            "|zoom" +
                            "|shake" +
                            "|wobble" +
                            "|jitter" +
                            "|rain" +      // alias → rainbow
                            "|rainbow" +
                            "|scroll" +
                            "|outline" +
                            "|shadow" +
                            "|sparkle" +
                            "|flicker" +
                            "|glitch" +
                            ")\\b)(.*)$",
                    Pattern.CASE_INSENSITIVE
            );

    /**
     * Turns things like:
     *   &wave testing
     *   &rain big rainbow text
     *
     * into:
     *   <wave>testing</wave>
     *   <rainbow>big rainbow text</rainbow>
     */
    private static String expandLeadingStyleAmp(String msg) {
        if (msg == null || msg.isEmpty()) return msg;

        Matcher m = LEADING_STYLE.matcher(msg);
        if (!m.find()) return msg;

        String style = m.group(1).toLowerCase(Locale.ROOT);
        String rest  = m.group(2);

        // trim a single space after the command
        if (rest.startsWith(" ")) rest = rest.substring(1);

        // &rain should map to <rainbow>…</rainbow>
        if ("rain".equals(style)) {
            style = "rainbow";
        }

        String open  = "<" + style + ">";
        String close = "</" + style + ">";

        // if the user already closes it manually somewhere, don’t double-close
        String restLower = rest.toLowerCase(Locale.ROOT);
        if (restLower.contains("</" + style + ">")) {
            // They’re handling the closing tag themselves
            return open + rest;
        }

        // Default: wrap the whole rest of the message
        return open + rest + close;
    }

    /**
     * Main normalization entry point for outgoing chat.
     *
     * Call this from your send hook (GuiChat / ClientChatSentEvent) before
     * the message gets sent to the server.
     */
    public static String normalizeInline(String msg) {
        if (msg == null || msg.isEmpty()) return msg;

        // 1) Convert leading &style into full-line <style>...</style>
        msg = expandLeadingStyleAmp(msg);

        // 2) If the line starts with <grad>/<pulse>/<wave>/... and isn't closed,
        //    auto-close them so they cover the whole visible message.
        msg = HexChatExpand.autoCloseLeadingTags(msg);

        // 3) Expand short tags + inline #RRGGBB/#RGB outside of <tags>
        //    (keeps your tag structure intact for the renderer)
        msg = HexChatExpand.preprocessOutgoing(msg);

        // 4) Convert &c → §c and wrap inline hex into §#RRGGBB, etc.
        //    This uses your HexFontRenderer’s static helper.
        msg = HexFontRenderer.formatInline(msg);

        return msg;
    }
    public static boolean containsOurSyntax(String s) {
        if (s == null || s.isEmpty()) return false;
        String lower = s.toLowerCase();

        // our XML-style tags
        if (lower.contains("<grad")   || lower.contains("</grad")   ||
                lower.contains("<rain")   || lower.contains("</rain")   ||
                lower.contains("<rainbow")|| lower.contains("</rainbow")||
                lower.contains("<pulse")  || lower.contains("</pulse")  ||
                lower.contains("<wave")   || lower.contains("</wave")   ||
                lower.contains("<zoom")   || lower.contains("</zoom")   ||
                lower.contains("<scroll") || lower.contains("</scroll") ||
                lower.contains("<shake")  || lower.contains("</shake")  ||
                lower.contains("<jitter") || lower.contains("</jitter") ||
                lower.contains("<wobble") || lower.contains("</wobble") ||
                lower.contains("<sparkle")|| lower.contains("</sparkle")||
                lower.contains("<flicker")|| lower.contains("</flicker")||
                lower.contains("<glow")   || lower.contains("</glow")   ||
                lower.contains("<outline")|| lower.contains("</outline")||
                lower.contains("<shadow") || lower.contains("</shadow") ||
                lower.contains("<glitch") || lower.contains("</glitch")) {
            return true;
        }

        // inline §#RRGGBB codes
        return s.contains("§#");
    }

}
