// src/main/java/com/example/examplemod/client/HexChatWrapFix.java
package com.example.examplemod.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.IChatComponent;

public class HexChatWrapFix {

    // Enable via JVM args (NOT Java code):
    //   -Dhexchat.debugWrap=true
    //   -Dhexchat.debugCarry=true
    private static final boolean DEBUG_WRAP  = Boolean.getBoolean("hexchat.debugWrap");
    private static final boolean DEBUG_CARRY = Boolean.getBoolean("hexchat.debugCarry");

    private static void dbgWrap(String s)  { if (DEBUG_WRAP)  System.out.println(s); }
    private static void dbgCarry(String s) { if (DEBUG_CARRY) System.out.println(s); }

    // ============================================================
    // 0) VANILLA FORMAT CARRY (public clone of getFormatFromString)
    // ============================================================

    private static String getActiveVanillaFormats(String s) {
        if (s == null || s.isEmpty()) return "";

        String result = "";
        int len = s.length();

        for (int i = 0; i < len - 1; ++i) {
            char c = s.charAt(i);
            if (c != '§') continue;

            char code = Character.toLowerCase(s.charAt(i + 1));
            i++;

            // color (0–9, a–f): replaces previous color + styles
            if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                result = "§" + code;
                continue;
            }

            // reset
            if (code == 'r') {
                result = "";
                continue;
            }

            // formats k–o
            if ("klmno".indexOf(code) >= 0) {
                result = result + "§" + code;
            }
        }

        return result;
    }

    public static String stripLeadingResets(String s) {
        if (s == null || s.isEmpty()) return "";
        int i = 0;
        int n = s.length();

        while (i + 1 < n && s.charAt(i) == '§'
                && Character.toLowerCase(s.charAt(i + 1)) == 'r') {
            i += 2;
        }
        return s.substring(i);
    }

    // ============================================================
    // 1) CUSTOM TAG CARRY (stack-based, supports nesting)
    // ============================================================

    private static final Set SUPPORTED = new HashSet();
    static {
        // effect tags
        SUPPORTED.add("grad");
        SUPPORTED.add("pulse");
        SUPPORTED.add("wave");
        SUPPORTED.add("zoom");
        SUPPORTED.add("shake");
        SUPPORTED.add("wobble");
        SUPPORTED.add("jitter");
        SUPPORTED.add("outline");
        SUPPORTED.add("shadow");
        SUPPORTED.add("glow");
        SUPPORTED.add("sparkle");
        SUPPORTED.add("flicker");
        SUPPORTED.add("glitch");
        SUPPORTED.add("scroll");

        // NEW styles you added in HexFontRenderer:
        SUPPORTED.add("loop");
        SUPPORTED.add("shootingstar");
        SUPPORTED.add("snow");

        // rain + rainbow family (we canonicalize rainbow/rb/rbw -> "rbw")
        SUPPORTED.add("rain");
        SUPPORTED.add("rbw");

        // hex span canonical close </#>
        SUPPORTED.add("#");
    }

    private static class Tag {
        String key;     // canonical key ("wave", "grad", "rbw", "#", etc.)
        String header;  // exact opening header to re-inject ("<wave ...>", "<g:...>", "«wave ...»")
        Tag(String k, String h) { key = k; header = h; }
    }

    /**
     * Returns ONLY your custom tag carry (no § codes).
     * Keeps proper nesting order by using a stack.
     */
    public static String computeCarryTagsOnly(String line) {
        if (line == null || line.isEmpty()) return "";

        ArrayList stack = new ArrayList(); // Tag[]
        int n = line.length();

        for (int i = 0; i < n; i++) {
            char ch = line.charAt(i);
            if (ch != '<' && ch != '«') continue;

            char closeCh = (ch == '«') ? '»' : '>';
            int end = line.indexOf(closeCh, i + 1);
            if (end < 0) continue;

            String inside = line.substring(i + 1, end).trim(); // like: "wave #ff0" or "/wave"
            if (inside.length() == 0) { i = end; continue; }

            boolean closing = inside.charAt(0) == '/';
            String body = closing ? inside.substring(1).trim() : inside;

            // tagName is first token (up to first whitespace)
            int sp = body.indexOf(' ');
            String tagName = (sp >= 0) ? body.substring(0, sp) : body;
            if (tagName.length() == 0) { i = end; continue; }

            String canon = canonicalTag(tagName);

            if (!isSupported(canon)) {
                i = end;
                continue;
            }

            if (closing) {
                popTag(stack, canon);
            } else {
                String header = line.substring(i, end + 1);
                stack.add(new Tag(canon, header));
            }

            i = end;
        }

        if (stack.isEmpty()) return "";

        // emit in stack order (outer -> inner)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stack.size(); i++) {
            Tag t = (Tag) stack.get(i);
            sb.append(t.header);
        }
        return sb.toString();
    }

    private static boolean isSupported(String canon) {
        return SUPPORTED.contains(canon);
    }

    private static String canonicalTag(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toLowerCase();
        if (s.isEmpty()) return "";

        // rainbow family aliases
        if ("rainbow".equals(s) || "rb".equals(s) || "rbw".equals(s)) return "rbw";

        // shorthand opens
        if (s.startsWith("g:")) return "grad";
        if (s.startsWith("pl:")) return "pulse";
        if (s.startsWith("wave:")) return "wave";
        if (s.startsWith("zoom:")) return "zoom";

        // hex span opens: <FFF>, <#FF00FF>, <#FF00FFl>, etc.
        if (looksLikeHexSpan(s)) return "#";
        if ("#".equals(s)) return "#";

        return s;
    }

    private static boolean looksLikeHexSpan(String s) {
        if (s == null || s.isEmpty()) return false;

        int p = 0;
        if (s.charAt(0) == '#') p = 1;

        // read 3 or 6 hex digits
        int hex = 0;
        while (p + hex < s.length() && hex < 6 && isHex(s.charAt(p + hex))) {
            hex++;
        }
        if (!(hex == 3 || hex == 6)) return false;

        // remaining chars must be legacy style letters (lmonkr)
        for (int i = p + hex; i < s.length(); i++) {
            if ("lmonkr".indexOf(s.charAt(i)) < 0) return false;
        }
        return true;
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'f')
                || (c >= 'A' && c <= 'F');
    }

    private static void popTag(ArrayList stack, String canon) {
        for (int i = stack.size() - 1; i >= 0; i--) {
            Tag t = (Tag) stack.get(i);
            if (canon.equals(t.key)) {
                stack.remove(i);
                return;
            }
        }
    }

    // ============================================================
    // 2) MAIN HELPER THE TRANSFORMER WILL CALL
    // ============================================================

    /**
     * Used inside GuiNewChat wrapping:
     * - prevLine = the trimmed visible line
     * - remainder = the leftover string
     *
     * Returns: carryTags(prevLine) + remainder
     */
    public static String prependCustomCarry(String prevLine, String remainder) {
        if (remainder == null || remainder.length() == 0) return remainder;

        String carry = computeCarryTagsOnly(prevLine);
        if (carry == null || carry.length() == 0) return remainder;

        return carry + remainder;
    }

    // ============================================================
    // 3) (Optional) list-based carry for any List<String> wrappers
    // ============================================================

    @SuppressWarnings({ "rawtypes" })
    public static List<String> carryAnimatedAcross(List lines) {
        List<String> out = new ArrayList<String>();
        if (lines == null || lines.isEmpty()) return out;

        String vanillaCarry = "";
        String tagCarry = "";

        for (int idx = 0; idx < lines.size(); idx++) {
            Object o = lines.get(idx);
            String raw = (o == null) ? "" : o.toString();
            String stripped = stripLeadingResets(raw);

            String built;
            if (idx == 0) {
                built = stripped;
            } else {
                built = vanillaCarry + tagCarry + stripped;
            }

            dbgCarry("[HexCarry] line[" + idx + "] raw='" + raw + "'");
            dbgCarry("[HexCarry]   prefix='" + (vanillaCarry + tagCarry) + "'");
            dbgCarry("[HexCarry]   built ='" + built + "'");

            out.add(built);

            vanillaCarry = getActiveVanillaFormats(built);
            tagCarry = computeCarryTagsOnly(built);
        }

        return out;
    }

    // ============================================================
    // 4) Component-level helper (if you ever hook List<IChatComponent>)
    // ============================================================

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static List carryComponents(List components) {
        if (components == null || components.isEmpty()) return components;

        List texts  = new ArrayList(components.size());
        List styles = new ArrayList(components.size());

        for (int i = 0; i < components.size(); i++) {
            Object o = components.get(i);

            if (o instanceof IChatComponent) {
                IChatComponent ic = (IChatComponent) o;
                texts.add(ic.getFormattedText());

                ChatStyle st = ic.getChatStyle();
                styles.add(st != null ? st.createShallowCopy() : new ChatStyle());
            } else {
                texts.add(o != null ? o.toString() : "");
                styles.add(new ChatStyle());
            }
        }

        List carried = carryAnimatedAcross(texts);

        List out = new ArrayList(carried.size());
        for (int i = 0; i < carried.size(); i++) {
            String s = (String) carried.get(i);
            ChatComponentText c = new ChatComponentText(s);

            if (i < styles.size() && styles.get(i) != null) {
                c.setChatStyle((ChatStyle) styles.get(i));
            }
            out.add(c);
        }

        dbgWrap("[HexCarry] carryComponents in=" + components.size() + " out=" + out.size());
        return out;
    }
}
