package com.example.examplemod.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HexChatExpand — tag / shortcut preprocessor for outgoing chat.
 *
 * Responsibilities:
 *   - Expand inline hex:  #RRGGBB / #RGB  → §#RRGGBB / §#RRGGBB
 *       (but ONLY outside <tags> / «tags» so <grad #...> is safe)
 *   - Expand "short" aliases:
 *       <g:#F00:#0F0>    → <grad #F00 #0F0>
 *       </g>             → </grad>
 *       <rb>             → <rainbow>
 *       </rb>            → </rainbow>
 *       <pl:#F0F>        → <pulse #F0F>
 *       </pl>            → </pulse>
 *       <wave:a:5:2>     → <wave type=a speed=5 amp=2>
 *       <zoom:a:1.5:20>  → <zoom type=a scale=1.5 cycle=20>
 *
 * Live-preview helpers (_LIVE) are left as-is.
 */
public final class HexChatExpand {

    private HexChatExpand() {}

    // ============================================================
    // 0. NEW: AUTO-CLOSE LEADING TAGS (wrap whole message)
    // ============================================================

    // Matches:  ^[spaces][TAGBLOCK]rest
    // TAGBLOCK is a sequence of style tags at the start:
    //   <grad ...><pulse ...><wave ...>Message...
    private static final Pattern LEADING_TAG_BLOCK = Pattern.compile(
            "^(\\s*)" + // 1 = leading whitespace
                    "((?:[<«]\\s*(grad|rbw|rainbow|pulse|wave|zoom|shake|wobble|jitter|outline|shadow|glow|sparkle|flicker|glitch|scroll)[^>»]*[>»])+)"+ // 2 = tag block
                    "(.*)$",    // 3 = rest of line
            Pattern.CASE_INSENSITIVE
    );

    // Single opening style tag inside that block
    private static final Pattern SINGLE_OPEN_TAG = Pattern.compile(
            "[<«]\\s*(grad|rbw|rainbow|pulse|wave|zoom|shake|wobble|jitter|outline|shadow|glow|sparkle|flicker|glitch|scroll)\\b[^>»]*[>»]",
            Pattern.CASE_INSENSITIVE
    );

    // Closing tag anywhere in the rest of the line
    private static final Pattern CLOSE_TAG = Pattern.compile(
            "</\\s*(grad|rainbow|pulse|wave|zoom|shake|wobble|jitter|outline|shadow|glow|sparkle|flicker|glitch|scroll)\\s*>",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * If the message starts with style tags (grad/pulse/wave/etc) and you don't
     * close them anywhere, auto-close them at the end so they affect the full line.
     *
     * Examples:
     *   "<grad ...>Hello"
     *       → "<grad ...>Hello</grad>"
     *
     *   "<grad ...><pulse ...>Hello"
     *       → "<grad ...><pulse ...>Hello</pulse></grad>"
     *
     *   "<pulse ...>Warning</pulse> normal"
     *       → unchanged (already has </pulse> in the line)
     */
    public static String autoCloseLeadingTags(String msg) {
        if (msg == null || msg.isEmpty()) return msg;

        Matcher m = LEADING_TAG_BLOCK.matcher(msg);
        if (!m.find()) {
            // no leading tag combo at the front of the line
            return msg;
        }

        String leadingWs = m.group(1);
        String tagsBlock = m.group(2);
        String rest      = m.group(3);

        // Collect opening tags in the order they appear
        Deque<String> stack = new ArrayDeque<String>();
        Matcher tagMatcher = SINGLE_OPEN_TAG.matcher(tagsBlock);
        while (tagMatcher.find()) {
            String name = tagMatcher.group(1);
            if (name == null) continue;
            name = name.toLowerCase(Locale.ROOT);

            // normalize "rbw" to "rainbow"
            if ("rbw".equals(name)) {
                name = "rainbow";
            }

            // push so we can close in reverse (last opened, first closed)
            stack.push(name);
        }

        if (stack.isEmpty()) {
            return msg;
        }

        // Scan the rest of the line for explicit closing tags, pop them off
        Matcher closeMatcher = CLOSE_TAG.matcher(rest);
        while (closeMatcher.find() && !stack.isEmpty()) {
            String closeName = closeMatcher.group(1);
            if (closeName == null) continue;
            closeName = closeName.toLowerCase(Locale.ROOT);

            // remove the first matching entry in the stack (top-first)
            Iterator<String> it = stack.iterator();
            while (it.hasNext()) {
                if (it.next().equals(closeName)) {
                    it.remove();
                    break;
                }
            }
        }

        // Rebuild line + append any missing closing tags
        StringBuilder out = new StringBuilder();
        out.append(leadingWs).append(tagsBlock).append(rest);

        while (!stack.isEmpty()) {
            String name = stack.pop();
            out.append("</").append(name).append(">");
        }

        return out.toString();
    }

    // ============================================================
    // 1. PUBLIC PIPELINE HELPERS
    // ============================================================

    /**
     * Full preprocessing pass for outgoing chat:
     *   1) expand short tags
     *   2) expand #RRGGBB / #RGB outside of <...> into §#RRGGBB
     *
     * Call this from your ClientChat send hook *before*
     * HexFontRenderer.formatInline(msg).
     *
     * NOTE: if you want "wrap whole message" behavior, call
     * HexChatExpand.autoCloseLeadingTags(...) *before* this.
     */
    public static String preprocessOutgoing(String s) {
        if (s == null || s.isEmpty()) return s;
        s = expandGradients(s);
        s = expandRainbow(s);
        s = expandPulse(s);
        s = expandWaveShort(s);
        s = expandZoomShort(s);
        s = expandHexOutsideTags(s);
        return s;
    }

    // Old API names used by existing hooks — now do useful work.
    public static String expandGradients(String text) {
        if (text == null || text.isEmpty()) return text;
        return expandGradientShort(text);
    }

    public static String expandRainbow(String text) {
        if (text == null || text.isEmpty()) return text;
        return expandRainbowShort(text);
    }

    public static String expandPulse(String text) {
        if (text == null || text.isEmpty()) return text;
        return expandPulseShort(text);
    }

    // ============================================================
    // 2. INLINE HEX (#RRGGBB / #RGB) — OUTSIDE TAGS ONLY
    // ============================================================

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'f')
                || (c >= 'A' && c <= 'F');
    }

    /**
     * Converts bare #RRGGBB / #RGB outside of <...> / «...» into §#RRGGBB.
     *   "#FF00FFText" → "§#FF00FFText"
     *
     * Inside tags like <grad #F00 #0F0> the # is left alone so your
     * HexFontRenderer gradient parser still sees it.
     */
    public static String expandHexOutsideTags(String s) {
        if (s == null || s.isEmpty()) return s;

        StringBuilder out = new StringBuilder(s.length() + 16);
        boolean inAngle = false;
        boolean inChevron = false;

        int n = s.length();
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);

            // Track whether we're inside <...> or «...»
            if (c == '<') {
                inAngle = true;
                out.append(c);
                continue;
            }
            if (c == '>') {
                inAngle = false;
                out.append(c);
                continue;
            }
            if (c == '«') {
                inChevron = true;
                out.append(c);
                continue;
            }
            if (c == '»') {
                inChevron = false;
                out.append(c);
                continue;
            }

            // Only treat # as inline color when NOT inside a tag
            if (!inAngle && !inChevron && c == '#') {
                int j = i + 1;
                // collect up to 6 hex digits
                while (j < n && isHexDigit(s.charAt(j)) && (j - (i + 1)) < 6) {
                    j++;
                }
                int len = j - (i + 1);
                if (len == 3 || len == 6) {
                    // This is a #RGB or #RRGGBB token → §#HEX
                    out.append('§').append('#');
                    out.append(s.substring(i + 1, j));
                    i = j - 1; // -1 because loop will i++
                    continue;
                }
            }

            out.append(c);
        }

        return out.toString();
    }

    // ============================================================
    // 3. SHORT GRADIENT TAGS
    //    <g:#F00:#0F0> → <grad #F00 #0F0>
    // ============================================================

    private static final Pattern SHORT_GRAD_OPEN =
            Pattern.compile("(?i)<g:([^>]+)>");

    private static String expandGradientShort(String s) {
        if (s == null || s.isEmpty()) return s;
        if (s.indexOf("<g:") < 0 && s.indexOf("<G:") < 0 &&
                s.indexOf("</g>") < 0 && s.indexOf("</G>") < 0) {
            return s;
        }

        StringBuffer buf = new StringBuffer();
        Matcher m = SHORT_GRAD_OPEN.matcher(s);
        while (m.find()) {
            String attrs = m.group(1);        // "#F00:#0F0:#00F"
            String spaced = attrs.replace(':', ' ').trim(); // "#F00 #0F0 #00F"
            String replacement = "<grad " + spaced + ">";
            m.appendReplacement(buf, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(buf);
        s = buf.toString();

        // closing alias
        s = s.replaceAll("(?i)</g>", "</grad>");
        return s;
    }

    // ============================================================
    // 4. SHORT RAINBOW TAGS
    //    <rb> → <rainbow>
    //    <rb:stuff> → <rainbow stuff>
    // ============================================================

    private static final Pattern SHORT_RB_ATTR =
            Pattern.compile("(?i)<rb:([^>]+)>");

    private static String expandRainbowShort(String s) {
        if (s == null || s.isEmpty()) return s;
        if (s.toLowerCase().indexOf("<rb") < 0) return s;

        StringBuffer buf = new StringBuffer();
        Matcher m = SHORT_RB_ATTR.matcher(s);
        while (m.find()) {
            String attrs = m.group(1);                // e.g. "cycles=2:speed=4"
            String spaced = attrs.replace(':', ' ').trim();
            String repl = "<rainbow " + spaced + ">";
            m.appendReplacement(buf, Matcher.quoteReplacement(repl));
        }
        m.appendTail(buf);
        s = buf.toString();

        s = s.replaceAll("(?i)<rb>", "<rainbow>");
        s = s.replaceAll("(?i)</rb>", "</rainbow>");
        return s;
    }

    // ============================================================
    // 5. SHORT PULSE TAGS
    //    <pl:#F0F>Text</pl> → <pulse #F0F>Text</pulse>
    // ============================================================

    private static final Pattern SHORT_PL_OPEN =
            Pattern.compile("(?i)<pl:([^>]+)>");

    private static String expandPulseShort(String s) {
        if (s == null || s.isEmpty()) return s;
        if (s.toLowerCase().indexOf("<pl") < 0) return s;

        StringBuffer buf = new StringBuffer();
        Matcher m = SHORT_PL_OPEN.matcher(s);
        while (m.find()) {
            String attrs = m.group(1);              // e.g. "#FF00FF", "#0FF:speed=6"
            String spaced = attrs.replace(':', ' ').trim();
            String repl = "<pulse " + spaced + ">";
            m.appendReplacement(buf, Matcher.quoteReplacement(repl));
        }
        m.appendTail(buf);
        s = buf.toString();

        s = s.replaceAll("(?i)</pl>", "</pulse>");
        return s;
    }

    // ============================================================
    // 6. SHORT WAVE / ZOOM TAGS
    //    <wave:a:5:2> → <wave type=a speed=5 amp=2>
    //    <zoom:a:1.5:20> → <zoom type=a scale=1.5 cycle=20>
    // ============================================================

    private static final Pattern SHORT_WAVE_OPEN =
            Pattern.compile("(?i)<wave:([^>]+)>");
    private static final Pattern SHORT_ZOOM_OPEN =
            Pattern.compile("(?i)<zoom:([^>]+)>");

    private static String expandWaveShort(String s) {
        if (s == null || s.isEmpty()) return s;
        if (s.toLowerCase().indexOf("<wave:") < 0) return s;

        StringBuffer buf = new StringBuffer();
        Matcher m = SHORT_WAVE_OPEN.matcher(s);
        while (m.find()) {
            String attrs = m.group(1);   // e.g. "a:5:2"
            String[] parts = attrs.split(":");
            StringBuilder ab = new StringBuilder();

            if (parts.length > 0 && parts[0].length() > 0) {
                ab.append("type=").append(parts[0]);
            }
            if (parts.length > 1 && parts[1].length() > 0) {
                if (ab.length() > 0) ab.append(' ');
                ab.append("speed=").append(parts[1]);
            }
            if (parts.length > 2 && parts[2].length() > 0) {
                if (ab.length() > 0) ab.append(' ');
                ab.append("amp=").append(parts[2]);
            }

            String repl = "<wave " + ab.toString() + ">";
            m.appendReplacement(buf, Matcher.quoteReplacement(repl));
        }
        m.appendTail(buf);
        return buf.toString();
    }

    private static String expandZoomShort(String s) {
        if (s == null || s.isEmpty()) return s;
        if (s.toLowerCase().indexOf("<zoom:") < 0) return s;

        StringBuffer buf = new StringBuffer();
        Matcher m = SHORT_ZOOM_OPEN.matcher(s);
        while (m.find()) {
            String attrs = m.group(1);   // e.g. "a:1.5:20"
            String[] parts = attrs.split(":");
            StringBuilder ab = new StringBuilder();

            if (parts.length > 0 && parts[0].length() > 0) {
                ab.append("type=").append(parts[0]);
            }
            if (parts.length > 1 && parts[1].length() > 0) {
                if (ab.length() > 0) ab.append(' ');
                ab.append("scale=").append(parts[1]);
            }
            if (parts.length > 2 && parts[2].length() > 0) {
                if (ab.length() > 0) ab.append(' ');
                ab.append("cycle=").append(parts[2]);
            }

            String repl = "<zoom " + ab.toString() + ">";
            m.appendReplacement(buf, Matcher.quoteReplacement(repl));
        }
        m.appendTail(buf);
        return buf.toString();
    }

    // ============================================================
    // 7. LIVE PREVIEW HELPERS (unchanged, optional)
    // ============================================================

    public static String expandGradients_LIVE(String s) {
        if (s == null || s.isEmpty()) return s;

        if (s.contains("<grad")) {
            int time = (int)(System.currentTimeMillis() % 750);
            int r = (time * 255) / 750;
            int g = 128;
            int b = 255 - r;

            String hex = String.format("§#%02X%02X%02X", r, g, b);
            return s.replaceAll("(?i)<grad([^>]*)?", hex);
        }
        return s;
    }

    public static String expandRainbow_LIVE(String s) {
        if (s == null || s.isEmpty()) return s;

        if (s.toLowerCase().contains("<rainbow")) {
            long t = System.currentTimeMillis() / 35;
            String hex = rainbowColor(t);
            return s.replaceAll("(?i)<rainbow([^>]*)?", hex);
        }
        return s;
    }

    public static String expandPulse_LIVE(String s) {
        if (s == null || s.isEmpty()) return s;

        if (s.toLowerCase().contains("<pulse")) {
            long t = System.currentTimeMillis() / 40;
            int v = (int)(Math.sin(t * 0.35) * 80 + 175);
            String hex = String.format("§#%02X%02X%02X", v, v, 255);
            return s.replaceAll("(?i)<pulse([^>]*)?", hex);
        }
        return s;
    }

    private static String rainbowColor(long t) {
        int r = (int)(Math.sin(t * 0.12) * 127 + 128);
        int g = (int)(Math.sin(t * 0.12 + 2) * 127 + 128);
        int b = (int)(Math.sin(t * 0.12 + 4) * 127 + 128);
        return String.format("§#%02X%02X%02X", r, g, b);
    }
}
