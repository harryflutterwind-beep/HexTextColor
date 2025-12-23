//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.example.examplemod.client;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class HexFontRenderer extends FontRenderer {
    private FontRenderer base;
    private static final int[] VANILLA_RGB = new int[]{
            0, 170, 43520, 43690, 11141120, 11141290,
            16755200, 11184810, 5592405, 5592575, 5635925,
            5636095, 16733525, 16733695, 16777045, 16777215
    };

// ─────────────────────────────────────────────
// CORE TAG PATTERNS
// ─────────────────────────────────────────────
// At top of HexFontRenderer (with your other patterns)

    // NEW: detect the *first* global-style open tag in a line
    private static final Pattern GLOBAL_HEADER_TAG =
            Pattern.compile(
                    "(?i)(<\\s*(grad|pulse|wave|zoom|shake|scroll|jitter|wobble|shootingstar|loop|sparkle|flicker|glitch|outline|shadow|glow|rain|rainbow|rb|rbw|snow)[^>]*>)"
            );

    private static final Pattern TAG_HEX_ANY =
            Pattern.compile("<\\s*#?([0-9a-fA-F]{3}|[0-9a-fA-F]{6})(?i:([lmonkr]*))\\s*>");
    private static final Pattern TAG_HEX_ANY_CHEV =
            Pattern.compile("[«]\\s*#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})(?i:([lmonkr]*))\\s*[»]");

    // long-form opens
    private static final Pattern TAG_GRAD_OPEN =
            Pattern.compile("(?i)<\\s*grad\\b([^>]*)>");

    // accept <rainbow>, <rb>, <rbw>
    private static final Pattern TAG_RBW_OPEN =
            Pattern.compile("(?i)<\\s*(?:rainbow|rb|rbw)(?:\\s+([^>]*))?\\s*>");

    private static final Pattern TAG_PULSE_OPEN =
            Pattern.compile("(?i)<\\s*pulse\\b([^>]*)>");

    private static final String LEG = "(?i)(?:§[0-9A-FK-OR])*";

    // closes
    private static final Pattern TAG_HEX_CLOSE =
            Pattern.compile(LEG + "</\\s*#\\s*>" + LEG);
    private static final Pattern TAG_HEX_CLOSE_CHEV =
            Pattern.compile(LEG + "[«]" + LEG + "/\\s*#" + LEG + "[»]" + LEG);

    private static final Pattern TAG_GRAD_CLOSE =
            Pattern.compile(LEG + "</\\s*grad\\s*>" + LEG);

    // accept </rainbow>, </rb>, </rbw>
    private static final Pattern TAG_RBW_CLOSE =
            Pattern.compile(LEG + "</\\s*(?:rainbow|rb|rbw)\\s*>" + LEG);

    private static final Pattern TAG_PULSE_CLOSE =
            Pattern.compile(LEG + "</\\s*pulse\\s*>" + LEG);

    private static final Pattern TAG_GRAD_CLOSE_CHEV =
            Pattern.compile(LEG + "[«]" + LEG + "/\\s*(?i:grad)" + LEG + "[»]" + LEG);

    private static final Pattern TAG_RBW_CLOSE_CHEV =
            Pattern.compile(LEG + "[«]" + LEG + "/\\s*(?i:(?:rainbow|rb|rbw))" + LEG + "[»]" + LEG);

    private static final Pattern TAG_PULSE_CLOSE_CHEV =
            Pattern.compile(LEG + "[«]" + LEG + "/\\s*(?i:pulse)" + LEG + "[»]" + LEG);

    // “any close” helpers
    private static final Pattern GRAD_CLOSE_ANY =
            Pattern.compile(LEG + "(?:</\\s*grad\\s*>|[«]" + LEG + "/\\s*(?:grad)" + LEG + "[»])" + LEG);

    private static final Pattern RBW_CLOSE_ANY =
            Pattern.compile(LEG + "(?:</\\s*(?:rainbow|rb|rbw)\\s*>"
                    + "|[«]" + LEG + "/\\s*(?:rainbow|rb|rbw)" + LEG + "[»])" + LEG);

    private static final Pattern PULSE_CLOSE_ANY =
            Pattern.compile(LEG + "(?:</\\s*pulse\\s*>|[«]" + LEG + "/\\s*(?:pulse)" + LEG + "[»])" + LEG);

    // chevron opens
    private static final Pattern TAG_GRAD_OPEN_CHEV =
            Pattern.compile("(?i)[«]\\s*grad\\b([^»]*)[»]");
    private static final Pattern TAG_RBW_OPEN_CHEV =
            Pattern.compile("(?i)[«]\\s*(?:rainbow|rb|rbw)(?:\\s+([^»]*))?\\s*[»]");
    private static final Pattern TAG_PULSE_OPEN_CHEV =
            Pattern.compile("(?i)[«]\\s*pulse\\b([^»]*)[»]");

// ─────────────────────────────────────────────
// SHORT-HAND OPEN TAGS (new)
// ─────────────────────────────────────────────

    // <g:#F00:#0F0>    → gradient
    private static final Pattern TAG_GRAD_SHORT =
            Pattern.compile("(?i)<\\s*g:([^>]*)>");

    // <pl:#FF00FF>     → pulse
    private static final Pattern TAG_PULSE_SHORT =
            Pattern.compile("(?i)<\\s*pl:([^>]*)>");

    // <wave:a:5:2>     → wave (type, speed, amp)
    private static final Pattern TAG_WAVE_SHORT =
            Pattern.compile("(?i)<\\s*wave:([a-z]):([0-9.]+):([0-9.]+)>");

    // <zoom:a:1.5:20>  → zoom (type, scale, cycle)
    private static final Pattern TAG_ZOOM_SHORT =
            Pattern.compile("(?i)<\\s*zoom:([a-z]):([0-9.]+):([0-9.]+)>");

    private final Random obfRng = new Random();
    private final Map<Integer, char[]> obfByWidth = new HashMap();


    private static final Pattern HEX_WITH_STYLES =
            Pattern.compile("(?i)§#([0-9a-f]{6})([lmonkr]*)");


    // strip patterns – only rainbow bits changed to (?:rainbow|rb|rbw)
// strip patterns – now includes all style tags
    private static final Pattern TAG_STRIP = Pattern.compile(
            "(</\\s*#\\s*>)"
                    + "|(<\\s*#?([0-9a-fA-F]{3}|[0-9a-fA-F]{6})(?i:[lmonkr]*)\\s*>)"
                    + "|([«]\\s*#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})(?i:[lmonkr]*)\\s*[»])"
                    + "|([«]\\s*/\\s*#\\s*[»])"

                    // <grad> and friends
                    + "|((?i)<\\s*grad\\b[^>]*>)"
                    + "|((?i)</\\s*grad\\s*>)"
                    + "|((?i)<\\s*(?:rain|rainbow|rb|rbw)\\b[^>]*>)"
                    + "|((?i)</\\s*(?:rain|rainbow|rb|rbw)\\s*>)"
                    + "|((?i)<\\s*pulse\\b[^>]*>)"
                    + "|((?i)</\\s*pulse\\s*>)"

                    // NEW: motion / FX tags
                    + "|((?i)<\\s*(?:wave|zoom|scroll|shake|jitter|wobble|loop|loop"
                    +              "|shootingstar|shootingstar|shootingstar|sparkle|flicker|glitch|outline|shadow|glow|snow)\\b[^>]*>)"
                    + "|((?i)</\\s*(?:wave|zoom|scroll|shake|jitter|wobble|loop|loop"
                    +               "|shootingstar|shootingstar|shootingstar|sparkle|flicker|glitch|outline|shadow|glow|snow)\\s*>)"

                    // «grad» etc.
                    + "|((?i)[«]\\s*grad\\b[^»]*[»])"
                    + "|((?i)[«]\\s*/\\s*grad\\s*[»])"
                    + "|((?i)[«]\\s*(?:rain|rainbow|rb|rbw)\\b[^»]*[»])"
                    + "|((?i)[«]\\s*/\\s*(?:rain|rainbow|rb|rbw)\\s*[»])"
                    + "|((?i)[«]\\s*pulse\\b[^»]*[»])"
                    + "|((?i)[«]\\s*/\\s*pulse\\s*[»])"

                    // «wave», «zoom», etc.
                    + "|((?i)[«]\\s*(?:wave|zoom|scroll|shake|jitter|wobble|loop|loop"
                    +              "|shootingstar|shootingstar|shootingstar|sparkle|flicker|glitch|outline|shadow|glow|snow)\\b[^»]*[»])"
                    + "|((?i)[«]\\s*/\\s*(?:wave|zoom|scroll|shake|jitter|wobble|loop|loop"
                    +               "|shootingstar|shootingstar|shootingstar|sparkle|flicker|glitch|outline|shadow|glow|snow)\\s*[»])"

                    // inline hex §#RRGGBB
                    + "|(?i)(§#[0-9a-fA-F]{6}[lmonkr]*)"
                    + "|(?i)(§#[0-9a-fA-F]{3}[lmonkr]*)"
    );

    // Fallback quick detect: ensures wrapping uses Hex logic even if TAG_STRIP drifts in a merge.
    private static final Pattern LOOKS_LIKE_OUR_TAG = Pattern.compile(
            "(?i)(<\\s*/?\\s*(?:grad|rain|rainbow|rb|rbw|pulse|wave|zoom|scroll|shake|jitter|wobble|loop|shootingstar|sparkle|flicker|glitch|outline|shadow|glow|snow)\\b"
                    + "|[«]\\s*/?\\s*(?:grad|rain|rainbow|rb|rbw|pulse|wave|zoom|scroll|shake|jitter|wobble|loop|shootingstar|sparkle|flicker|glitch|outline|shadow|glow|snow)\\b)"
    );

    // Match *any* vanilla style code: '§' plus the next char.
// Using '.' makes sure we never strip just '§' and leave the code char behind.
    private static final Pattern LEGACY_CTRL_ANY =
            Pattern.compile("§.", Pattern.DOTALL);

    private static final Pattern CTRL_STRIP =
            Pattern.compile("[\\p{Cntrl}&&[^\\n\\t]]");
    private static final Pattern ZW_STRIP =
            Pattern.compile("[\\u200B\\u200C\\u200D\\u2060]");

    // include rb / rbw here too
// Any number of our angle-bracket / chevron tags at the front of the line,
// followed by at least one space. Used to re-inject tags on wrapped lines.
    private static final Pattern LEADING_TAGS_THEN_SPACE = Pattern.compile(
            "^(" +
                    // <#FFFFFF>, <#FFF>, etc. (+ optional legacy styles)
                    "(?:<\\s*#?[0-9a-fA-F]{3,6}(?i:[lmonkr]*)\\s*>)|" +
                    // «#FFFFFF», «#FFF»
                    "(?:[«]\\s*#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})(?i:[lmonkr]*)\\s*[»])|" +

                    // <grad ...> / <g:...> and chevrons
                    "(?:(?i)<\\s*(?:grad\\b|g:)[^>]*>)|" +
                    "(?:(?i)[«]\\s*(?:grad\\b|g:)[^»]*[»])|" +

                    // <rainbow ...>, <rb ...>, <rbw ...> and chevrons
                    "(?:(?i)<\\s*(?:rainbow|rb|rbw)\\b[^>]*>)|" +
                    "(?:(?i)[«]\\s*(?:rainbow|rb|rbw)\\b[^»]*[»])|" +

                    // <pulse ...>, <pl:...> and chevrons
                    "(?:(?i)<\\s*(?:pulse\\b|pl:)[^>]*>)|" +
                    "(?:(?i)[«]\\s*(?:pulse\\b|pl:)[^»]*[»])|" +

                    // Motion / FX tags: wave, zoom, shake, scroll, jitter, wobble,
                    // outline, shadow, sparkle, flicker, glitch (long forms)
                    "(?:(?i)<\\s*(?:wave|zoom|shake|scroll|jitter|wobble|shootingstar|loop|outline|shadow|sparkle|flicker|glitch|snow)\\b[^>]*>)|" +
                    "(?:(?i)[«]\\s*(?:wave|zoom|shake|scroll|jitter|wobble|shootingstar|loop|outline|shadow|sparkle|flicker|glitch|snow)\\b[^»]*[»])" +
                    ")+\\s+"
    );

    // Add near the other patterns at the top if you want:
    private static final String STYLE_TAG_NAMES =
            "(?:grad|rain|rainbow|rb|rbw|pulse"
                    + "|wave|zoom|scroll|shake|jitter|wobble"
                    + "|shootingstar|shootingstar|sparkle|flicker|glitch|outline|shadow|glow|snow|#)";


    private static int vanillaColorFor(char k) {
        int idx = -1;
        if (k >= '0' && k <= '9') {
            idx = k - 48;
        } else if (k >= 'a' && k <= 'f') {
            idx = 10 + (k - 97);
        } else if (k >= 'A' && k <= 'F') {
            idx = 10 + (k - 65);
        }

        return idx >= 0 && idx < 16 ? VANILLA_RGB[idx] : -1;
    }

    // At top of HexFontRenderer (with your other patterns)
    // Protect full Hex tags so wrapping NEVER splits inside "<wave #ff007e>" / "<loop #00ffff scroll=0.28>".
// Also supports the « » alias form.
    private static final Pattern PROTECT_TAG = Pattern.compile(
            "(?i)("
                    + "<\\s*/?\\s*(?:grad|g|rbw|rainbow|wave|loop|pulse|pl|shake|zoom|"
                    + "rain|scroll|jitter|wobble|shootingstar|gstar|sparkle|flicker|glitch|"
                    + "outline|shadow|glow|snow)\\b[^>]*>"
                    + "|"
                    + "«\\s*/?\\s*(?:grad|g|rbw|rainbow|wave|loop|pulse|pl|shake|zoom|"
                    + "rain|scroll|jitter|wobble|shootingstar|gstar|sparkle|flicker|glitch|"
                    + "outline|shadow|glow|snow)\\b[^»]*»"
                    + ")"
    );

    // ----------------------------------------------------------
//  Wrap helpers used by listFormattedStringToWidth
// ----------------------------------------------------------
    private static String preprocessForWrap(String s) {
        if (s == null || s.isEmpty()) return "";

        Matcher m = PROTECT_TAG.matcher(s);
        StringBuffer out = new StringBuffer();

        while (m.find()) {
            String tag = m.group(1); // the full <wave ...> or </wave>
            // Wrap just the TAG in a protected range:
            String wrapped = "\ue000" + tag + "\ue002";
            m.appendReplacement(out, Matcher.quoteReplacement(wrapped));
        }
        m.appendTail(out);

        return out.toString();
    }

    // ----------------------------------------------------------
    // Local carryover (keeps nesting correct across wrapped lines)
    // ----------------------------------------------------------
    private static List<String> carryAnimatedAcrossLocal(List<String> lines) {
        if (lines == null || lines.isEmpty()) return lines;

        List<String> out = new ArrayList<String>(lines.size());
        String carryLegacy = "";
        ArrayDeque<String> carryTags = new ArrayDeque<String>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null) line = "";

            if (i > 0) {
                // Preserve "<Name> " prefix (chat rendering)
                String prefix = "";
                String rest = line;

                int gt = rest.indexOf("> ");
                if (rest.startsWith("<") && gt > 0 && gt < 64) {
                    prefix = rest.substring(0, gt + 2);
                    rest = rest.substring(gt + 2);
                }

                // Skip injection if the line begins with a closing tag
                String restTrim = rest;
                while (restTrim.startsWith("§") && restTrim.length() >= 2) {
                    restTrim = restTrim.substring(2);
                }

                if (!(restTrim.startsWith("</") || restTrim.startsWith("«/"))) {
                    StringBuilder sb = new StringBuilder(prefix);

                    if (carryLegacy != null && !carryLegacy.isEmpty()) sb.append(carryLegacy);

                    if (carryTags != null && !carryTags.isEmpty()) {
                        String chain = buildTagChain(carryTags);
                        if (!rest.startsWith(chain)) sb.append(chain);
                    }

                    sb.append(rest);
                    line = sb.toString();
                } else if (!prefix.isEmpty()) {
                    line = prefix + rest;
                }
            }

            out.add(line);

            // update carry for next line
            carryLegacy = computeLegacyCarry(line);
            carryTags = computeOpenTagStackAtEnd(line);
        }

        return out;
    }

    private static String buildTagChain(Deque<String> stack) {
        if (stack == null || stack.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String t : stack) sb.append(t);
        return sb.toString();
    }

    private static String computeLegacyCarry(String s) {
        if (s == null || s.isEmpty()) return "";
        char lastColor = 0;
        boolean bold=false, italic=false, under=false, strike=false, obf=false;

        for (int i=0;i<s.length()-1;i++) {
            if (s.charAt(i) == '§') {
                char c = Character.toLowerCase(s.charAt(i+1));
                if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')) {
                    lastColor = c;
                    bold=italic=under=strike=obf=false;
                } else if (c == 'r') {
                    lastColor = 0;
                    bold=italic=under=strike=obf=false;
                } else if (c == 'l') bold=true;
                else if (c == 'o') italic=true;
                else if (c == 'n') under=true;
                else if (c == 'm') strike=true;
                else if (c == 'k') obf=true;
            }
        }

        StringBuilder active = new StringBuilder();
        if (lastColor != 0) active.append('§').append(lastColor);
        if (obf)    active.append("§k");
        if (bold)   active.append("§l");
        if (strike) active.append("§m");
        if (under)  active.append("§n");
        if (italic) active.append("§o");
        return active.toString();
    }

    private static ArrayDeque<String> computeOpenTagStackAtEnd(String s) {
        ArrayDeque<String> stack = new ArrayDeque<String>();
        if (s == null || s.isEmpty()) return stack;

        Matcher m = Pattern.compile("(?i)(<\\s*/?\\s*([a-z]+)\\b[^>]*>|«\\s*/?\\s*([a-z]+)\\b[^»]*»)").matcher(s);
        while (m.find()) {
            String full = m.group(1);
            String name = (m.group(2) != null) ? m.group(2) : m.group(3);
            if (name == null) continue;
            name = name.toLowerCase();

            if (!isHexTagName(name)) continue;

            boolean closing = full.startsWith("</") || full.startsWith("«/");
            if (!closing) {
                stack.addLast(full);
            } else {
                for (java.util.Iterator<String> it = stack.descendingIterator(); it.hasNext(); ) {
                    String open = it.next();
                    String openName = extractHexTagName(open);
                    if (name.equals(openName)) {
                        it.remove();
                        break;
                    }
                }
            }
        }
        return stack;
    }

    private static boolean isHexTagName(String name) {
        if (name == null) return false;
        return name.equals("grad") || name.equals("g")
                || name.equals("rbw") || name.equals("rainbow")
                || name.equals("wave") || name.equals("loop")
                || name.equals("pulse") || name.equals("pl")
                || name.equals("shake") || name.equals("zoom")
                || name.equals("rain") || name.equals("scroll")
                || name.equals("jitter") || name.equals("wobble")
                || name.equals("shootingstar") || name.equals("gstar")
                || name.equals("sparkle") || name.equals("flicker")
                || name.equals("glitch") || name.equals("outline")
                || name.equals("shadow") || name.equals("glow")
                || name.equals("snow");
    }

    private static String extractHexTagName(String openTag) {
        if (openTag == null) return null;
        Matcher m = Pattern.compile("(?i)(?:<\\s*/?\\s*([a-z]+)\\b|«\\s*/?\\s*([a-z]+)\\b)").matcher(openTag);
        if (m.find()) {
            String n = (m.group(1) != null) ? m.group(1) : m.group(2);
            return (n != null) ? n.toLowerCase() : null;
        }
        return null;
    }


    private static String postprocessAfterWrap(String s) {
        if (s == null || s.isEmpty()) return "";

        StringBuilder out = new StringBuilder();
        int len = s.length();
        int i = 0;

        while (i < len) {
            char ch = s.charAt(i);

            if (ch == '\ue000' || ch == '\ue001') {
                // restore everything up to the terminator
                int j = i + 1;
                while (j < len && s.charAt(j) != '\ue002') j++;
                if (j < len) j++; // include terminator

                // strip the sentinels themselves
                String inner = s.substring(i + 1, j - 1);
                out.append(inner);

                i = j;
                continue;
            }

            // normal char
            out.append(ch);
            i++;
        }

        return out.toString();
    }


    private static boolean isForgeModListLike() {
        GuiScreen s = Minecraft.getMinecraft().currentScreen;
        if (s == null) {
            return false;
        } else {
            String cn = s.getClass().getName();
            return cn.startsWith("cpw.mods.fml.client.GuiModList") || cn.startsWith("cpw.mods.fml.client.GuiScrollingList") || cn.startsWith("cpw.mods.fml.client.GuiSlotModList") || cn.startsWith("cpw.mods.fml.client.config.GuiConfig") || cn.contains("GuiModList") || cn.contains("GuiConfig");
        }
    }

    // ----------------------------------------------------------
    // Fix CP1252/UTF-8 section-sign junk seen in Forge Mod List
    // (shows as Â or 'A' before § on some systems)
    // ----------------------------------------------------------
    private static String fixSectionJunk(String s) {
        if (s == null || s.isEmpty()) return s;
        // Most common: "Â§" => "§"
        s = s.replace("\u00C2\u00A7", "\u00A7");
        // Occasionally a lone Â shows up
        s = s.replace("\u00C2", "");
        return s;
    }


    private static String applyHexShortcuts(String s) {
        if (s != null && s.indexOf(60) >= 0) {
            s = s.replace("<g_fire>", "<grad #FF2000 #FF5A00 #FFCC00 #FF5A00 #FF2000 scroll=0.20>");
            return s;
        } else {
            return s;
        }
    }

    private static String sanitize(String s) {
        if (s == null) {
            return null;
        } else {
            s = applyHexShortcuts(s);
            s = CTRL_STRIP.matcher(s).replaceAll("");
            s = ZW_STRIP.matcher(s).replaceAll("");
            s = s.replace(' ', ' ').replace(' ', ' ').replace(' ', ' ');
            return s;
        }
    }

    private void buildObfBuckets() {
        Map<Integer, ArrayList<Character>> tmp = new HashMap();

        for(int i = 0; i < "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()[]{}<>?/\\|+-_=;:,.'\\\"~".length(); ++i) {
            char ch = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()[]{}<>?/\\|+-_=;:,.'\\\"~".charAt(i);
            int w = this.base.getCharWidth(ch);
            if (w > 0) {
                ArrayList<Character> list = (ArrayList)tmp.get(w);
                if (list == null) {
                    list = new ArrayList();
                    tmp.put(w, list);
                }

                list.add(ch);
            }
        }

        for(Map.Entry<Integer, ArrayList<Character>> e : tmp.entrySet()) {
            ArrayList<Character> list = (ArrayList)e.getValue();
            char[] arr = new char[list.size()];

            for(int i = 0; i < arr.length; ++i) {
                arr[i] = (Character)list.get(i);
            }

            this.obfByWidth.put(e.getKey(), arr);
        }

    }

    // Static overload for static parsers (snow args, etc.)
    private static int parseHexColor(String s, int fallback) {
        if (s == null) return fallback;
        s = s.trim();
        if (s.isEmpty()) return fallback;

        if (s.startsWith("#")) s = s.substring(1);

        if (s.length() == 3) {
            char r = s.charAt(0), g = s.charAt(1), b = s.charAt(2);
            s = "" + r + r + g + g + b + b;
        }

        if (s.length() != 6) return fallback;

        try { return Integer.parseInt(s, 16) & 0xFFFFFF; }
        catch (Throwable t) { return fallback; }
    }


    private static int sampleGrad(int[] grad, float t) {
        if (grad == null || grad.length == 0) return 0xFFFFFF;
        if (grad.length == 1) return grad[0] & 0xFFFFFF;

        t = t - (float)Math.floor(t); // wrap 0..1
        float pos = t * (grad.length - 1);
        int i = (int)pos;
        float f = pos - i;

        int c0 = grad[i] & 0xFFFFFF;
        int c1 = grad[Math.min(i + 1, grad.length - 1)] & 0xFFFFFF;

        // IMPORTANT: call your existing lerpRGB
        return lerpRGB(c0, c1, f) & 0xFFFFFF;
    }

    private static String findAttrValueStatic(String attrs, String key) {
        if (attrs == null || attrs.isEmpty() || key == null || key.isEmpty()) return null;

        int idx = 0;
        while (idx >= 0) {
            idx = attrs.indexOf(key, idx);
            if (idx < 0) break;

            // left boundary
            if (idx > 0) {
                char prev = attrs.charAt(idx - 1);
                if (Character.isLetterOrDigit(prev) || prev == '_' || prev == '-') {
                    idx += key.length();
                    continue;
                }
            }

            int j = idx + key.length();
            while (j < attrs.length() && Character.isWhitespace(attrs.charAt(j))) j++;
            if (j >= attrs.length() || attrs.charAt(j) != '=') { idx = j; continue; }

            j++; // '='
            while (j < attrs.length() && Character.isWhitespace(attrs.charAt(j))) j++;
            if (j >= attrs.length()) return "";

            char q = attrs.charAt(j);
            if (q == '"' || q == '\'') {
                int start = j + 1;
                int end = attrs.indexOf(q, start);
                if (end < 0) end = attrs.length();
                return attrs.substring(start, end).trim();
            }

            int start = j, end = j;
            while (end < attrs.length()) {
                char ch = attrs.charAt(end);
                if (Character.isWhitespace(ch) || ch == '>') break;

                end++;
            }
            return attrs.substring(start, end).trim();
        }
        return null;
    }
    private static boolean hasBareKey(String attrs, String key) {
        if (attrs == null || attrs.isEmpty() || key == null || key.isEmpty()) return false;

        int idx = 0;
        while (true) {
            idx = attrs.indexOf(key, idx);
            if (idx < 0) return false;

            // left boundary
            if (idx > 0) {
                char p = attrs.charAt(idx - 1);
                if (Character.isLetterOrDigit(p) || p == '_' || p == '-') { idx += key.length(); continue; }
            }

            int j = idx + key.length();

            // right boundary: allow whitespace/end, NOT letter/digit/_/-
            if (j < attrs.length()) {
                char n = attrs.charAt(j);
                if (Character.isLetterOrDigit(n) || n == '_' || n == '-') { idx += key.length(); continue; }
            }

            // make sure it's NOT key=...
            int k = j;
            while (k < attrs.length() && Character.isWhitespace(attrs.charAt(k))) k++;
            if (k < attrs.length() && attrs.charAt(k) == '=') { idx = k + 1; continue; }

            return true;
        }
    }


    private static String getStr(String attrs, String key, String def) {
        String v = findAttrValueStatic(attrs, key);
        return (v == null || v.isEmpty()) ? def : v;
    }

    private static float getFloat(String attrs, String key, float def) {
        String v = findAttrValueStatic(attrs, key);
        if (v == null || v.isEmpty()) return def;
        try { return Float.parseFloat(v); } catch (Throwable t) { return def; }
    }

    private static boolean getBool(String attrs, String key, boolean def) {
        String v = findAttrValueStatic(attrs, key);
        if (v == null) return def;
        v = v.trim().toLowerCase();
        if (v.isEmpty()) return true;
        if ("1".equals(v) || "true".equals(v) || "yes".equals(v) || "on".equals(v)) return true;
        if ("0".equals(v) || "false".equals(v) || "no".equals(v) || "off".equals(v)) return false;
        return def;
    }

    private static void parseSnowArgs(String attrs, Op op) {
        if (op == null) return;

        // Ensure snow is enabled for this op (fixes "snow broke again")
        op.snowOn = true;

        SnowCfg c = (op.snow != null ? op.snow : new SnowCfg());

        // attrs can be null; make safe for getFloat/getStr helpers
        if (attrs == null) attrs = "";

        // read floats
        c.dens   = getFloat(attrs, "dens", c.dens);
        c.spread = getFloat(attrs, "spread", c.spread);
        c.speed  = getFloat(attrs, "speed", c.speed);
        c.drift  = getFloat(attrs, "drift", c.drift);
        c.fall   = getFloat(attrs, "fall", c.fall);
        c.start  = getFloat(attrs, "start", c.start);
        c.size   = getFloat(attrs, "size", c.size);
        c.alpha  = clamp01(getFloat(attrs, "alpha", c.alpha));

        // NEW: flakeMix/mix (0..1) to keep colored flakes looking 'snowy'
        op.snowMix = clamp01(getFloat(attrs, "flakeMix", getFloat(attrs, "mix", op.snowMix)));

        // glyphs
        String g = getStr(attrs, "glyphs", null);
        if (g != null && g.length() > 0) c.glyphs = g;

        // ── flake color overrides ─────────────────────────────
        // Supported:
        //   color= / snowcolor=   (legacy)
        //   snowgrad= / grad=     (legacy)
        //   snowrainbow / snowrainbowspeed (legacy)
        //   flake=rbw | flake=#RRGGBB | flake=#A,#B,#C (NEW alias)
        //   flakeMix=0..1 (NEW) mix flakes toward white
        String col = getStr(attrs, "color", null);
        if (col == null) col = getStr(attrs, "snowcolor", null);

        // NEW alias: flake=
        String flake = getStr(attrs, "flake", null);
        if ((col == null || col.length() == 0) && flake != null && flake.length() > 0) {
            String f0 = flake.trim();
            if ("rbw".equalsIgnoreCase(f0) || "rainbow".equalsIgnoreCase(f0)) {
                c.useRainbow = true;
            } else if (f0.indexOf(',') >= 0 || f0.indexOf(' ') >= 0) {
                // treat as gradient list
                c.grad = parseSnowGradValue(f0);
                if (c.grad != null && c.grad.length >= 2) c.useGrad = true;
            } else {
                col = f0; // treat as solid hex, parsed below
            }
        }
        if (col != null && col.length() > 0) c.color = parseHexColor(col, c.color);

        // gradient for flakes: snowgrad=#AEEBFF,#FFFFFF,#AEEBFF
        // also accept grad= as alias
        String sg = getStr(attrs, "snowgrad", null);
        if (sg == null) sg = getStr(attrs, "grad", null);
        if (sg != null) {
            c.grad = parseSnowGradValue(sg);
            if (c.grad != null && c.grad.length >= 2) c.useGrad = true;
        }

        // optional gradient scroll controls
        c.gradScroll = getBool(attrs, "snowgradscroll", c.gradScroll) || hasBareKey(attrs, "snowgradscroll");
        c.gradScrollSpeed = getFloat(attrs, "snowgradscrollspeed", c.gradScrollSpeed);

        c.useRainbow = getBool(attrs, "snowrainbow", c.useRainbow) || hasBareKey(attrs, "snowrainbow");
        c.rbSpeed = getFloat(attrs, "snowrainbowspeed", c.rbSpeed);


        // ── NEW: flake-only style toggles ─────────────────────
        // (these should be fields on Op: snowInherit, snowWave, snowPulse, snowFlicker)
        op.snowWave    = getBool(attrs, "wave", false)    || hasBareKey(attrs, "wave");
        op.snowPulse   = getBool(attrs, "pulse", false)   || hasBareKey(attrs, "pulse");
        op.snowFlicker = getBool(attrs, "flicker", false) || hasBareKey(attrs, "flicker");


        // Push config into op (what drawSnowAura reads)
// Push config into op (IMPORTANT: do NOT force defaults into op)
        op.snowColor = -1;      // means "inherit"
        op.snowGrad  = null;


        // If flake= provided a gradient/rainbow, c.useGrad/c.grad/c.useRainbow may already be set.
        // Mirror that onto op.snowGrad so drawSnowAura can use it without needing snowgrad= specifically.
        if (c.useGrad && c.grad != null && c.grad.length >= 2) {
            op.snowGrad = c.grad;
        }
        if (col != null && col.length() > 0) {
            // explicit solid flake color
            op.snowColor = parseHexColor(col, -1);
        }

        if (sg != null && sg.length() > 0) {
            // explicit gradient flake color
            int[] gtmp = parseSnowGradValue(sg);
            if (gtmp != null && gtmp.length >= 2) op.snowGrad = gtmp;

        }

        op.snow = c;


        // Copy the core numeric params you already use
        op.snowSpeed   = c.speed;
        op.snowFall    = c.fall;
        op.snowStart   = c.start;
        op.snowSpread  = c.spread;
        op.snowDrift   = c.drift;
        op.snowDensity = c.dens;
    }

    // --- helpers used above ---
    private String findAttrValue(String attrs, String key) {
        if (attrs == null) return null;
        // matches: key=123 or key="123"
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\b" + java.util.regex.Pattern.quote(key) + "\\s*=\\s*(\"([^\"]*)\"|([^\\s>]+))")
                .matcher(attrs);
        if (!m.find()) return null;
        return m.group(2) != null ? m.group(2) : m.group(3);
    }

    private Integer findFirstHexColor(String attrs) {
        if (attrs == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("#([0-9a-fA-F]{6}|[0-9a-fA-F]{3})")
                .matcher(attrs);
        if (!m.find()) return null;
        return Integer.valueOf(parseHexColor("#" + m.group(1), 0xFFFFFF));

    }
    // Parses a CSV list VALUE like "#AEEBFF,#FFFFFF,#AEEBFF"
    private static int[] parseColorListValue(String v) {
        if (v == null) return null;
        v = v.trim();
        if (v.isEmpty()) return null;

        String[] parts = v.split("\\s*,\\s*");
        java.util.ArrayList<Integer> out = new java.util.ArrayList<Integer>();

        for (int i = 0; i < parts.length; i++) {
            String p = parts[i].trim();
            if (p.isEmpty()) continue;

            // use your static overload you added earlier:
            // parseHexColor(String, int fallback)
            int c = parseHexColor(p, -1);
            if (c != -1) out.add(Integer.valueOf(c));
        }

        if (out.size() < 2) return null; // gradients need 2+ stops
        int[] arr = new int[out.size()];
        for (int i = 0; i < out.size(); i++) arr[i] = out.get(i).intValue();
        return arr;
    }
    private static int[] parseSnowGradValue(String v) {
        if (v == null) return null;
        v = v.trim();
        if (v.isEmpty()) return null;

        String[] parts = v.split("[,\\s]+");
        java.util.ArrayList<Integer> out = new java.util.ArrayList<Integer>();

        for (int i = 0; i < parts.length; i++) {
            String p = parts[i].trim();
            if (p.isEmpty()) continue;
            int c = parseHexColor(p, -1);
            if (c != -1) out.add(c & 0xFFFFFF);
        }

        if (out.size() < 2) return null;

        int[] arr = new int[out.size()];
        for (int i = 0; i < out.size(); i++) arr[i] = out.get(i);
        return arr;
    }


    private Float parseFloatAttr(String attrs, String key) {
        // expects key=value (value float)
        // reuse your existing attr parser if you already have one
        try {
            String v = findAttrValue(attrs, key);
            if (v == null) return null;
            return Float.valueOf(Float.parseFloat(v));
        } catch (Throwable t) { return null; }
    }

    // Move the first global style tag (<grad>, <wave>, etc.) to the *front*
// of the string, so it wraps the entire message (including "<Player>").
    private static String hoistGlobalHeader(String s) {
        if (s == null || s.isEmpty()) return s;

        Matcher m = GLOBAL_HEADER_TAG.matcher(s);
        if (!m.find()) {
            return s; // no global tag → nothing to do
        }

        String header = m.group(1);       // the full "<wave ...>" or "<grad ...>"
        int start = m.start(1);

        // If it's already at the very front, we don't change anything.
        if (start == 0) {
            return s;
        }

        // Move the header to the front and remove it from its original spot
        String before = s.substring(0, start);
        String after  = s.substring(start + header.length());

        return header + before + after;
    }

    private char obfuscateCharSameWidth(char original) {
        // get the width of the original character
        int w = this.base.getCharWidth(original);

        if (w <= 0) {
            return original;
        }

        // lookup bucket for this width
        char[] bucket = (char[]) this.obfByWidth.get(w);

        if (bucket != null && bucket.length > 0) {
            return bucket[this.obfRng.nextInt(bucket.length)];
        }

        // fallback character set
        final String fallback =
                "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789" +
                        "!@#$%^&*()[]{}<>?/\\|+-_=;:,.'\\\"~";

        return fallback.charAt(this.obfRng.nextInt(fallback.length()));
    }

    private static String stylesToLegacy(String styles) {
        if (styles != null && !styles.isEmpty()) {
            StringBuilder sb = new StringBuilder(styles.length() * 2);

            for(char ch : styles.toLowerCase().toCharArray()) {
                switch (ch) {
                    case 'k':
                        sb.append("§k");
                        break;
                    case 'l':
                        sb.append("§l");
                        break;
                    case 'm':
                        sb.append("§m");
                        break;
                    case 'n':
                        sb.append("§n");
                        break;
                    case 'o':
                        sb.append("§o");
                    case 'p':
                    case 'q':
                    default:
                        break;
                    case 'r':
                        sb.append("§r");
                }
            }

            return sb.toString();
        } else {
            return "";
        }
    }

    private static String stripLeadingTagSpace(String s) {
        return s != null && !s.isEmpty() ? LEADING_TAGS_THEN_SPACE.matcher(s).replaceFirst("$1") : s;
    }

    private static String cleanPayload(String s) {
        if (s == null) {
            return "";
        } else {
            s = sanitize(s);
            s = stripLeadingTagSpace(s);
            s = TAG_STRIP.matcher(s).replaceAll("");
            return s;
        }
    }

    public HexFontRenderer(FontRenderer base) {
        super(
                Minecraft.getMinecraft().gameSettings,
                // Keep using whatever texture the original font was using
                resolveFontTexture(base),
                Minecraft.getMinecraft().getTextureManager(),
                base.getUnicodeFlag()
        );

        // Unwrap if someone passes us another HexFontRenderer
        FontRenderer effectiveBase = base;
        if (base instanceof HexFontRenderer) {
            try {
                Field f = HexFontRenderer.class.getDeclaredField("base");
                f.setAccessible(true);
                FontRenderer inner = (FontRenderer) f.get(base);
                if (inner != null) {
                    effectiveBase = inner;
                }
            } catch (Throwable ignored) {}
        }

        this.base = effectiveBase;

        // Copy over state from the vanilla renderer so our parent behaves identically
        this.fontRandom = this.base.fontRandom;
        this.setUnicodeFlag(this.base.getUnicodeFlag());
        this.setBidiFlag(this.base.getBidiFlag());

        // Optional but handy: one-time debug so we know what we're wrapping
        try {
            System.out.println("[HexFont] HexFontRenderer created, wrapping " + this.base.getClass().getName());
        } catch (Throwable ignored) {}

        // If you have any extra setup (bucket maps, regex compilation, etc.), keep it here:
        this.buildObfBuckets();
    }

    private static ResourceLocation resolveFontTexture(FontRenderer base) {
        try {
            Field f;
            try {
                f = FontRenderer.class.getDeclaredField("locationFontTexture");
            } catch (NoSuchFieldException var3) {
                f = FontRenderer.class.getDeclaredField("field_111273_g");
            }

            f.setAccessible(true);
            return (ResourceLocation)f.get(base);
        } catch (Throwable var4) {
            return new ResourceLocation("textures/font/ascii.png");
        }
    }

    private void syncBaseStateFromThis() {
        try {
            // sync unicode flag
            this.base.setUnicodeFlag(this.getUnicodeFlag());

            // sync bidi flag (right-to-left languages)
            this.base.setBidiFlag(this.getBidiFlag());
        } catch (Throwable ignored) {}
    }


    private static boolean isFromClassPrefix(String fqcnPrefix) {
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        int n = Math.min(st.length, 48);

        for(int i = 2; i < n; ++i) {
            String cn = st[i].getClassName();
            if (cn != null && cn.startsWith(fqcnPrefix)) {
                return true;
            }
        }

        return false;
    }


    private static boolean isJourneyMapManageWaypointsScreen() {
        GuiScreen s = Minecraft.getMinecraft().currentScreen;
        if (s == null) {
            return false;
        } else {
            String cn = s.getClass().getName().toLowerCase();
            return cn.contains("journeymap") && cn.contains("waypoint");
        }
    }

    private static boolean isForgeModListScreen() {
        GuiScreen s = Minecraft.getMinecraft().currentScreen;
        if (s == null) {
            return false;
        } else {
            String cn = s.getClass().getName();
            return cn.startsWith("cpw.mods.fml.client.GuiModList") || cn.contains("GuiModList") || cn.startsWith("cpw.mods.fml.client.GuiScrollingList") || cn.startsWith("cpw.mods.fml.client.GuiSlotModList");
        }
    }

    private static boolean isForgeConfigGui() {
        GuiScreen s = Minecraft.getMinecraft().currentScreen;
        if (s == null) {
            return false;
        } else {
            String cn = s.getClass().getName();
            return cn.startsWith("cpw.mods.fml.client.config.GuiConfig") || cn.contains("GuiConfig");
        }
    }


    private static boolean hasHexControls(String s) {
        return s != null && s.indexOf(167) >= 0 && s.contains("§#");
    }

    private static boolean hasOurTags(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }

        // Any hit in TAG_STRIP means we found one of "our" constructs:
        //  - <grad>, <rain>, <rainbow>, <pulse>
        //  - <wave>, <zoom>, <scroll>, <shake>, <jitter>, <wobble>
        //  - <sparkle>, <flicker>, <glitch>, <outline>, <shadow>, <glow>
        //  - chevron forms «...»
        //  - inline §#RRGGBB / §#RGB, etc.
        return TAG_STRIP.matcher(s).find();
    }


    private static boolean looksLikeOurSyntax(String s) {
        if (s == null || s.isEmpty()) return false;

        // Fast path: any known tag via TAG_STRIP (when it matches correctly)
        try {
            if (TAG_STRIP.matcher(s).find()) return true;
        } catch (Throwable t) {
            // ignore – fall back to LOOKS_LIKE_OUR_TAG below
        }

        // Inline §#RRGGBB without tags
        if (hasHexControls(s)) return true;

        // Robust fallback: catches <wave>, <grad>, «rbw», etc even if TAG_STRIP is out of sync.
        return LOOKS_LIKE_OUR_TAG.matcher(s).find();
    }



    @Override
    public int getStringWidth(String text) {
        // CNPC GUIs must use vanilla metrics
        if (isCustomNpcScreen()) {
            return this.base.getStringWidth(text == null ? "" : text);
        }

        if (text == null) {
            return 0;
        }

        // Keep whatever sanitize() you already use for debug / control chars
        String s = sanitize(text);
        if (s == null || s.isEmpty()) {
            return 0;
        }

        // Let vanilla handle mod-list / JM special screens if you want:
        if (isForgeModListLike() || isJourneyMapManageWaypointsScreen()) {
            return this.base.getStringWidth(isForgeModListLike() ? fixSectionJunk(s) : s);
        }

        // If the string has none of our tags or hex controls, just delegate
        if (!hasOurTags(s) && !hasHexControls(s)) {
            return this.base.getStringWidth(s);
        }

        // Strip our <grad>/<pulse>/<wave>/… tags and § codes → visible text
        String plain = TAG_STRIP.matcher(s).replaceAll("");
        plain = LEGACY_CTRL_ANY.matcher(plain).replaceAll("");

        return this.base.getStringWidth(plain);
    }


    // ------------------------------------------------------------------
// Wrap safety: prevent chat wrapping from cutting inside <...> tags.
// This avoids cases where no-space messages split a tag header across lines
// (e.g. "<grad ... scroll=0.28>" becoming "<grad ... " + "scroll=0.28>").
// ------------------------------------------------------------------
    private static int snapCutOutsideTags(String full, int cut) {
        if (full == null || cut <= 0 || cut >= full.length()) return cut;

        // <...> tags
        int lastOpen = full.lastIndexOf('<', cut - 1);
        int lastClose = full.lastIndexOf('>', cut - 1);
        if (lastOpen > lastClose) {
            return lastOpen; // cut before the tag starts
        }

        // «... » tags (your alternate tag delimiters)
        int lastLA = full.lastIndexOf('«', cut - 1);
        int lastRA = full.lastIndexOf('»', cut - 1);
        if (lastLA > lastRA) {
            return lastLA;
        }

        return cut;
    }

    @Override
    public String trimStringToWidth(String text, int width, boolean reverse) {
        // CNPC GUIs must use vanilla trimming
        if (isCustomNpcScreen()) {
            return this.base.trimStringToWidth(text == null ? "" : text, width, reverse);
        }

        // Leave special GUIs alone
        if (isForgeModListLike() || isJourneyMapManageWaypointsScreen()) {
            return this.base.trimStringToWidth(text, width, reverse);
        }

        String s = sanitize(text);
        if (s == null || s.isEmpty()) {
            // protect tag-split on wrap (no-space messages)
            if (!reverse && s != null) {
                int cutLen = s.length();
                int safe = snapCutOutsideTags(s, cutLen);
                if (safe != cutLen && safe > 0) {
                    return s.substring(0, safe);
                }
            }

            return s;
        }

        // Only do tag-aware logic when needed
        if (!hasOurTags(s) && !hasHexControls(s)) {
            return this.base.trimStringToWidth(s, width, reverse);
        }

        s = stripLeadingTagSpace(s);

        // 1) "visible only" string for measuring
        String plain = TAG_STRIP.matcher(s).replaceAll("");
        plain = LEGACY_CTRL_ANY.matcher(plain).replaceAll("");

        // 2) Ask vanilla how many visible chars fit
        String cutPlain = this.base.trimStringToWidth(plain, width, reverse);
        int need = cutPlain.length();

        if (!reverse) {
            int i = 0;
            int produced = 0;
            int n = s.length();

            while (i < n && produced < need) {
                int next = skipAnyTagForward(s, i);
                if (next != i) {
                    // skipped a tag / § code (zero-width)
                    i = next;
                } else {
                    i++;
                    produced++;
                }
            }

            return s.substring(0, i);
        } else {
            int i = s.length();
            int produced = 0;

            while (i > 0 && produced < need) {
                int prev = skipAnyTagBackward(s, i);
                if (prev != i) {
                    i = prev;
                } else {
                    i--;
                    produced++;
                }
            }

            return s.substring(i);
        }
    }

    // Canonical style tag names we treat as "animated blocks"
    private static final String[] ANIMATED_TAG_NAMES = new String[] {
            "grad",
            "rainbow",
            "pulse",
            "wave",
            "zoom",
            "scroll",
            "shake",
            "wobble",
            "jitter",
            "outline",
            "shadow",
            "glow",
            "sparkle",
            "flicker",
            "shootingstar",
            "glitch",
            "snow"
    };

    /**
     * Parse a tag like:
     *   "<grad #F00 #0F0>"
     *   "«grad #F00 #0F0»"
     *   "</wave>"
     *   "«/wave»"
     *
     * and return the canonical style name ("grad", "rainbow", "wave", etc.)
     * or null if it's not one of our animated/FX tags.
     */
    private static String getAnimatedTagName(String raw) {
        if (raw == null) return null;

        String t = raw.trim().toLowerCase();

        // strip leading '<' / '«'
        int idx = 0;
        if (idx < t.length() && (t.charAt(idx) == '<' || t.charAt(idx) == '«')) {
            idx++;
        }

        // optional '/'
        if (idx < t.length() && t.charAt(idx) == '/') {
            idx++;
        }

        // skip whitespace
        while (idx < t.length() && Character.isWhitespace(t.charAt(idx))) {
            idx++;
        }

        // read the tag name [a-z]+
        int start = idx;
        while (idx < t.length()) {
            char c = t.charAt(idx);
            if (!Character.isLetter(c)) break;
            idx++;
        }
        if (idx <= start) {
            return null;
        }

        String name = t.substring(start, idx);

        // normalize rainbow aliases if you ever see them here
        if ("rain".equals(name) || "rb".equals(name) || "rbw".equals(name)) {
            name = "rainbow";
        }

        // check against our canonical list
        for (String allowed : ANIMATED_TAG_NAMES) {
            if (allowed.equals(name)) {
                return name;
            }
        }

        return null;
    }

    private static boolean isAnimatedOpenTag(String tag) {
        if (tag == null) return false;
        String t = tag.trim().toLowerCase();

        // opening tags start with "<name" or "«name" (no slash right after)
        if (!(t.startsWith("<") || t.startsWith("«"))) {
            return false;
        }
        // "< /" or "« /" → not an opener
        int idx = 1;
        if (idx < t.length() && t.charAt(idx) == '/') {
            return false;
        }

        return getAnimatedTagName(tag) != null;
    }
    private static boolean isCustomNpcScreen() {
        GuiScreen s = Minecraft.getMinecraft().currentScreen;
        if (s == null) return false;

        String cn = s.getClass().getName();
        String lc = cn.toLowerCase();

        // --------------------------------------------------------
        // 1) EXPLICITLY SKIP SCRIPT / SCRIPTER GUIs
        //    → we *do not* want to disable HexFont globally
        //      just because the Scripter tool is open.
        // --------------------------------------------------------
        if (cn.startsWith("noppes.npcs.scripted.gui")
                || lc.contains("scriptgui")
                || lc.contains("guiscript")
                || lc.contains("scriptconsole")
                || lc.contains("scriptlanguages")
                || lc.contains("scriptmenu")) {
            return false;
        }

        // --------------------------------------------------------
        // 2) Normal CNPC client GUIs (dialogs, editors, etc.)
        //    These should still bypass HexFont like before.
        // --------------------------------------------------------
        // if (cn.startsWith("noppes.npcs.client.gui")) {
        //   return true;
        // }

        // --------------------------------------------------------
        // 3) Fallback for any other CNPC GUIs that aren’t in the
        //    usual package, but are *not* script-related.
        // --------------------------------------------------------
        // if ((lc.contains("noppes.npcs") || lc.contains("customnpc"))
        //        && !lc.contains("script")) {
        //    return true;
        //  }

        return false;
    }


    private static boolean shouldBypassCustomRendering() {
        // Forge mod list / config → always bypass
        if (isForgeModListScreen() || isForgeConfigGui()) {
            return true;
        }

        // Our “special” renderers that SHOULD use Hex effects:
        if (isFromClassPrefix("net.minecraft.client.gui.GuiNewChat")) return false;
        if (isFromClassPrefix("net.minecraft.client.gui.GuiIngame")) return false;

        GuiScreen s = Minecraft.getMinecraft().currentScreen;
        if (s == null) return false;

        if (s instanceof GuiChat)      return false;
        if (s instanceof GuiInventory) return false;
        if (s instanceof GuiContainer) return false;

        // NEW: CustomNPCs → **always bypass**
        if (isCustomNpcScreen()) return true;

        // Journeymap waypoints etc → bypass
        if (isJourneyMapManageWaypointsScreen()) return true;

        return false;
    }


    private static boolean isAnimatedCloseTag(String tag) {
        if (tag == null) return false;
        String t = tag.trim().toLowerCase();

        // closing tags: "</name>" or "«/name»"
        if (!(t.startsWith("</") || t.startsWith("«/"))) {
            return false;
        }

        return getAnimatedTagName(tag) != null;
    }

    /**
     * Scan a wrapped line and figure out which animated blocks
     * (grad/rainbow/pulse/wave/zoom/etc.) are still open at the end.
     *
     * Returns a prefix like:
     *   "<grad><wave>"
     *
     * that you can prepend to the *next* line so the effects continue.
     */
// Carries active style tags + legacy § formatting from one wrapped line to the next.
// NOTE: this is ONLY for wrap injection; rendering is unchanged.
    private static String carryStylesAcrossLines(String prevLine, String nextLine) {
        if (nextLine == null) return null;
        if (prevLine == null) return nextLine;

        // Preserve vanilla chat prefix: "<Name> " (do not treat it as a tag)
        String prefix = "";
        int gt = nextLine.indexOf("> ");
        if (nextLine.startsWith("<") && gt > 0 && gt < 48) { // small guard
            prefix = nextLine.substring(0, gt + 2);
            nextLine = nextLine.substring(gt + 2);
        }

        // Collect legacy § formatting active at end of prev line
        String legacy = getActiveLegacyPrefix(prevLine);

        // Collect active nested tags (our tags only) + inline hex pushes
        String openTags = getActiveOpenTagPrefix(prevLine);

        // Don’t double-inject if nextLine already starts with a tag/code
        String trimmed = nextLine;
        while (trimmed.startsWith("§") && trimmed.length() >= 2) trimmed = trimmed.substring(2);

        if (trimmed.startsWith("<") || trimmed.startsWith("«") || trimmed.startsWith("§")) {
            return prefix + legacy + nextLine;
        }

        return prefix + legacy + openTags + nextLine;
    }

    /**
     * Carry formatting (vanilla § codes + our nested tags) across a wrapped line list.
     * This mirrors vanilla's behavior but understands our <tag> / «tag» system.
     */
    private static java.util.List<String> carryStylesAcrossLines(java.util.List<String> lines) {
        if (lines == null) return null;
        if (lines.isEmpty()) return lines;

        java.util.List<String> out = new java.util.ArrayList<String>(lines.size());
        out.add(lines.get(0));

        for (int i = 1; i < lines.size(); i++) {
            String prev = out.get(i - 1);
            String next = lines.get(i);
            out.add(carryStylesAcrossLines(prev, next));
        }
        return out;
    }



    // Builds § prefix for color/styles active at end of s
    private static String getActiveLegacyPrefix(String s) {
        if (s == null || s.isEmpty()) return "";
        char color = 0;
        boolean bold=false, italic=false, under=false, strike=false, obf=false;

        for (int i = 0; i < s.length() - 1; i++) {
            if (s.charAt(i) == '§') {
                char c = Character.toLowerCase(s.charAt(i + 1));
                if (c == 'r') { color=0; bold=italic=under=strike=obf=false; }
                else if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')) { color=c; bold=italic=under=strike=obf=false; }
                else if (c == 'l') bold=true;
                else if (c == 'o') italic=true;
                else if (c == 'n') under=true;
                else if (c == 'm') strike=true;
                else if (c == 'k') obf=true;
            }
        }

        StringBuilder out = new StringBuilder();
        if (color != 0) out.append('§').append(color);
        if (obf)   out.append("§k");
        if (bold)  out.append("§l");
        if (strike)out.append("§m");
        if (under) out.append("§n");
        if (italic)out.append("§o");
        return out.toString();
    }

    // Carries open style tags across wrap. Only considers your known STYLE_TAG_NAMES and inline hex pushes.
    private static String getActiveOpenTagPrefix(String s) {
        if (s == null || s.isEmpty()) return "";

        java.util.ArrayDeque<String> stack = new java.util.ArrayDeque<String>();
        StringBuilder openPrefix = new StringBuilder();

        // Scan for <tag ...> </tag> and <#RRGGBB> </#>
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            boolean chev = (ch == '«');
            if (ch != '<' && !chev) continue;

            int end = s.indexOf(chev ? '»' : '>', i + 1);
            if (end < 0) continue;

            String raw = s.substring(i + 1, end).trim(); // e.g. "grad #a #b scroll=0.2" or "/grad"
            if (raw.isEmpty()) { i = end; continue; }

            boolean closing = raw.startsWith("/") || raw.startsWith("\\");
            String body = closing ? raw.substring(1).trim() : raw;
            String name = firstTokenLower(body); // tag name

            // Inline hex push/pop
            if (!closing && name.startsWith("#") && name.length() == 7) {
                // treat <#RRGGBB> as push that must be re-opened
                stack.addLast(name); // store "#RRGGBB"
                i = end;
                continue;
            }
            if (closing && name.equals("#")) {
                // pop last hex push if present
                // find last "#RRGGBB" entry
                java.util.Iterator<String> it = stack.descendingIterator();
                while (it.hasNext()) {
                    String v = it.next();
                    if (v != null && v.length() == 7 && v.charAt(0) == '#') { it.remove(); break; }
                }
                i = end;
                continue;
            }

            // Only carry our known style tags
            if (STYLE_TAG_NAMES != null && STYLE_TAG_NAMES.contains(name)) {
                if (!closing) {
                    // push exact open token text (preserve attrs)
                    stack.addLast((chev ? "«" : "<") + raw + (chev ? "»" : ">"));
                } else {
                    // pop matching style
                    java.util.Iterator<String> it2 = stack.descendingIterator();
                    while (it2.hasNext()) {
                        String tok = it2.next();
                        if (tok == null) continue;
                        // tok is like "<grad ...>" — match by first token
                        String tn = firstTokenLower(tok.substring(1, tok.length() - 1).trim());
                        if (tn.equals(name)) { it2.remove(); break; }
                    }
                }
            }

            i = end;
        }

        // Rebuild opens in order
        for (String tok : stack) {
            if (tok == null) continue;

            // If it is "#RRGGBB", rebuild as <#RRGGBB>
            if (tok.length() == 7 && tok.charAt(0) == '#') {
                openPrefix.append("<").append(tok).append(">");
            } else {
                openPrefix.append(tok);
            }
        }

        return openPrefix.toString();
    }

    private static String firstTokenLower(String s) {
        if (s == null) return "";
        int sp = s.indexOf(' ');
        String t = (sp < 0 ? s : s.substring(0, sp)).trim();
        return t.toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public List<String> listFormattedStringToWidth(String text, int width) {
        // CNPC GUIs must use vanilla wrapping completely
        if (isCustomNpcScreen()) {
            // mimic vanilla null behaviour
            if (text == null) {
                List<String> out = new ArrayList<String>();
                out.add("");
                return out;
            }
            return this.base.listFormattedStringToWidth(text, width);
        }

        // Safety / null behaviour identical to vanilla
        if (text == null) {
            List<String> out = new ArrayList<String>();
            out.add("");
            return out;
        }

        // Keep all the “special screens” bypasses you already had
        if (isForgeModListLike()) {
            return this.base.listFormattedStringToWidth(fixSectionJunk(text), width);
        }
        if (isJourneyMapManageWaypointsScreen()) {
            return this.base.listFormattedStringToWidth(text, width);
        }

        // If it DOESN'T look like our <grad>/<wave>/etc syntax,
        // don't touch it – let vanilla handle colors & wrapping.
        if (!looksLikeOurSyntax(text)) {
            return this.base.listFormattedStringToWidth(text, width);
        }

        // From here on: text *does* use our syntax, so we run the
        // custom wrapping logic.

        this.syncBaseStateFromThis();
        // 1) sanitize + hoist our global header tags
        String s = sanitize(text);
        s = hoistGlobalHeader(s);
        if (s.isEmpty()) {
            List<String> out = new ArrayList<String>();
            out.add("");
            return out;
        }

        // 2) protect tags so width calc only sees visible glyphs
        String protectedS = preprocessForWrap(s);
        List<String> lines = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        int visibleWidth = 0;

        int len = protectedS.length();
        int i = 0;

        while (i < len) {
            char ch = protectedS.charAt(i);

            // protected ranges (our fake runes)
            if (ch == '\ue000' || ch == '\ue001') {
                int j = i + 1;
                while (j < len && protectedS.charAt(j) != '\ue002') {
                    j++;
                }
                if (j < len) j++; // skip terminator
                current.append(protectedS, i, j);
                i = j;
                continue;
            }

            // vanilla § formatting – copy as-is, zero width
            if (ch == '§' && i + 1 < len) {
                current.append(protectedS, i, i + 2);
                i += 2;
                continue;
            }

            int w = this.base.getCharWidth(ch);

            if (visibleWidth + w > width && visibleWidth > 0) {
                String line = stripLeadingTagSpace(current.toString());
                if (!line.isEmpty()) {
                    lines.add(line);
                }
                current.setLength(0);
                visibleWidth = 0;
                // re-handle current ch as first char of next line
            } else {
                current.append(ch);
                visibleWidth += w;
                i++;
            }
        }

        if (current.length() > 0) {
            String line = stripLeadingTagSpace(current.toString());
            lines.add(line);
        }

        if (lines.isEmpty()) {
            lines.add("");
        }

        // 3) restore real tags per line
        List<String> restored = new ArrayList<String>(lines.size());
        for (int li = 0; li < lines.size(); li++) {
            String before = lines.get(li);
            String after = postprocessAfterWrap(before);
            restored.add(after);
        }

        // 4) carry vanilla § formats + our animated tags across wrapped lines
        // Use the newer local carryover logic that preserves nested tag stacks
        // even when a wrapped segment contains only symbols (no "text" glyphs).
        return carryAnimatedAcrossLocal(restored);
    }


    public String safeTrimStringToWidth(String text, int width) {
        if (text == null || text.isEmpty()) return "";

        int currentWidth = 0;
        int i = 0;
        int len = text.length();

        while (i < len) {
            char c = text.charAt(i);

            // Skip <tags ...>
            if (c == '<') {
                int end = text.indexOf('>', i);
                if (end != -1) {
                    i = end + 1;
                    continue;
                }
            }

            // Skip § codes
            if (c == '§' && i + 1 < len) {
                i += 2;
                continue;
            }

            // Visible char width using THIS renderer instance
            currentWidth += this.getCharWidth(c);

            if (currentWidth > width)
                break;

            i++;
        }

        return text.substring(0, i);
    }

    private static int scaleRGB(int rgb, float m) {
        rgb &= 0xFFFFFF;
        int r = (rgb >> 16) & 255;
        int g = (rgb >>  8) & 255;
        int b = (rgb      ) & 255;
        r = (int)(r * m); if (r < 0) r = 0; if (r > 255) r = 255;
        g = (int)(g * m); if (g < 0) g = 0; if (g > 255) g = 255;
        b = (int)(b * m); if (b < 0) b = 0; if (b > 255) b = 255;
        return (r << 16) | (g << 8) | b;
    }

    private static int skipAnyTagForward(String s, int from) {
        Matcher m = TAG_STRIP.matcher(s);
        if (m.region(from, s.length()).lookingAt()) {
            return m.end();
        } else {
            Matcher l = LEGACY_CTRL_ANY.matcher(s);
            return l.region(from, s.length()).lookingAt() ? l.end() : from;
        }
    }

    private static int skipAnyTagBackward(String s, int endExclusive) {
        Matcher m = TAG_STRIP.matcher(s);

        while(m.find()) {
            if (m.end() == endExclusive) {
                return m.start();
            }

            if (m.end() > endExclusive) {
                break;
            }
        }

        Matcher l = LEGACY_CTRL_ANY.matcher(s);

        while(l.find()) {
            if (l.end() == endExclusive) {
                return l.start();
            }

            if (l.end() > endExclusive) {
                break;
            }
        }

        return endExclusive;
    }

    private static String applyAndStripLegacyCodes(String s, LegacyState st) {
        if (s != null && !s.isEmpty()) {
            StringBuilder vis = new StringBuilder(s.length());
            int i = 0;
            int n = s.length();

            while(i < n) {
                char c = s.charAt(i);
                Matcher m = HEX_WITH_STYLES.matcher(s.substring(i));
                if (m.lookingAt()) {
                    String hex = m.group(1);
                    String styles = m.group(2);
                    if (styles != null && !styles.isEmpty()) {
                        for(char flag : styles.toLowerCase().toCharArray()) {
                            switch (flag) {
                                case 'k':
                                    st.obfuscated = true;
                                    st.obfOnce = true;
                                    break;
                                case 'l':
                                    st.bold = true;
                                    break;
                                case 'm':
                                    st.strikethrough = true;
                                    break;
                                case 'n':
                                    st.underline = true;
                                    break;
                                case 'o':
                                    st.italic = true;
                                case 'p':
                                case 'q':
                                default:
                                    break;
                                case 'r':
                                    st.reset();
                            }
                        }
                    }

                    i += m.end();
                } else if (c == 167 && i + 1 < n) {
                    char k = Character.toLowerCase(s.charAt(i + 1));
                    switch (k) {
                        case '0':
                        case '1':
                        case '2':
                        case '3':
                        case '4':
                        case '5':
                        case '6':
                        case '7':
                        case '8':
                        case '9':
                        case 'a':
                        case 'b':
                        case 'c':
                        case 'd':
                        case 'e':
                        case 'f':
                        case 'r':
                            st.reset();
                        case ':':
                        case ';':
                        case '<':
                        case '=':
                        case '>':
                        case '?':
                        case '@':
                        case 'A':
                        case 'B':
                        case 'C':
                        case 'D':
                        case 'E':
                        case 'F':
                        case 'G':
                        case 'H':
                        case 'I':
                        case 'J':
                        case 'K':
                        case 'L':
                        case 'M':
                        case 'N':
                        case 'O':
                        case 'P':
                        case 'Q':
                        case 'R':
                        case 'S':
                        case 'T':
                        case 'U':
                        case 'V':
                        case 'W':
                        case 'X':
                        case 'Y':
                        case 'Z':
                        case '[':
                        case '\\':
                        case ']':
                        case '^':
                        case '_':
                        case '`':
                        case 'g':
                        case 'h':
                        case 'i':
                        case 'j':
                        case 'p':
                        case 'q':
                        default:
                            break;
                        case 'k':
                            st.obfuscated = true;
                            st.obfOnce = true;
                            break;
                        case 'l':
                            st.bold = true;
                            break;
                        case 'm':
                            st.strikethrough = true;
                            break;
                        case 'n':
                            st.underline = true;
                            break;
                        case 'o':
                            st.italic = true;
                    }

                    i += 2;
                } else {
                    vis.append(c);
                    ++i;
                }
            }

            return vis.toString();
        } else {
            return s;
        }
    }

    private static String legacyPrefix(LegacyState st) {
        StringBuilder sb = new StringBuilder(10);
        if (st.bold) {
            sb.append("§l");
        }

        if (st.italic) {
            sb.append("§o");
        }

        if (st.underline) {
            sb.append("§n");
        }

        if (st.strikethrough) {
            sb.append("§m");
        }

        if (st.obfuscated) {
            sb.append("§k");
        }

        return sb.toString();
    }

    private static String legacyPrefixNoObf(LegacyState st) {
        StringBuilder sb = new StringBuilder(10);
        if (st.bold) {
            sb.append("§l");
        }

        if (st.italic) {
            sb.append("§o");
        }

        if (st.underline) {
            sb.append("§n");
        }

        if (st.strikethrough) {
            sb.append("§m");
        }

        return sb.toString();
    }

    @Override
    public int drawString(String text, int x, int y, int color, boolean shadow) {
        // ⬅ NEW: never use HexFontRenderer inside CNPC GUIs
        if (isCustomNpcScreen()) {
            return this.base.drawString(text, x, y, applyForcedMul(color), shadow);
        }

        if (isForgeModListLike()) {
            return this.base.drawString(fixSectionJunk(text), x, y, applyForcedMul(color), shadow);
        }

        this.syncBaseStateFromThis();

        if (isJourneyMapManageWaypointsScreen()) {
            return this.base.drawString(text, x, y, applyForcedMul(color), shadow);

        }

        // If it does NOT look like our <grad>/<wave>/etc syntax, bail out
        if (!looksLikeOurSyntax(text)) {
            return this.base.drawString(text, x, y, applyForcedMul(color), shadow);
        }

        // Only our syntax reaches here
        return this.drawHexAware(text, x, y, color, shadow);
    }

    @Override
    public int drawStringWithShadow(String text, int x, int y, int color) {
        // Never use HexFontRenderer inside CNPC GUIs
        if (isCustomNpcScreen()) {
            return this.base.drawStringWithShadow(text, x, y, color);
        }

        if (isForgeModListLike()) {
            return this.base.drawStringWithShadow(fixSectionJunk(text), x, y, color);
        }

        this.syncBaseStateFromThis();

        if (isJourneyMapManageWaypointsScreen()) {
            return this.base.drawStringWithShadow(text, x, y, color);
        }

        // If it does NOT look like our <grad>/<wave>/etc syntax, bail out
        if (!looksLikeOurSyntax(text)) {
            return this.base.drawStringWithShadow(text, x, y, color);
        }

        // Only our tagged messages reach here
        return this.drawHexAware(text, x, y, color, true);
    }

    private static String dropOurTagsOnlyPrefix(String s) {
        if (s != null && !s.isEmpty()) {
            int i = 0;

            int n;
            Matcher m;
            for(n = s.length(); i < n; i = m.end()) {
                m = TAG_STRIP.matcher(s);
                if (!m.region(i, n).lookingAt()) {
                    break;
                }
            }

            return i > 0 && i <= n ? s.substring(i) : s;
        } else {
            return s;
        }
    }


    private static int skipLegacyCodes(String s, int i) {
        for(int n = s.length(); i + 1 < n && s.charAt(i) == 167; i += 2) {
            char k = Character.toLowerCase(s.charAt(i + 1));
            if ("0123456789abcdefklmnor".indexOf(k) < 0) {
                break;
            }
        }

        return i;
    }

    private int drawHexAware(String text, int x, int y, int fallbackColor, boolean shadow) {
        // 1) Always sanitize first
        text = sanitize(text);
        if (text == null || text.length() == 0) {
            return 0;
        }

        // 2) Normalize §# → <#...> tags, then strip inline hex so our tag parser owns them
        if (text.indexOf('§') >= 0) {
            text = normalizeControlsToTags(text);
            text = postNormalizeCleanup(text);
            text = stripHexAndReset(text);
        } else {
            text = stripHexAndReset(text);
        }

        // 3) Trim junk spaces after leading tags
        text = stripLeadingTagSpace(text);

        // 4) If there are no tags at all, just use vanilla
        if (text.indexOf('<') < 0 && text.indexOf('«') < 0) {
            return this.base.drawString(text, x, y, applyForcedMul(fallbackColor), shadow);
        }

        List<Op> ops = this.parseToOps(text);
        return drawOps(ops, x, y, fallbackColor, shadow);

    }

    private static float timeSeconds() {
        return Minecraft.getSystemTime() / 1000.0F;
    }

    private static int hsvToRgb(float h, float s, float v) {
        // Wrap hue to 0..360
        if (Float.isNaN(h)) h = 0f;
        h = h % 360.0f;
        if (h < 0.0f) h += 360.0f;

        // Clamp s/v
        if (s < 0.0f) s = 0.0f; else if (s > 1.0f) s = 1.0f;
        if (v < 0.0f) v = 0.0f; else if (v > 1.0f) v = 1.0f;

        // Grayscale (no saturation)
        if (s <= 0.00001f) {
            int g = Math.round(v * 255.0f);
            if (g < 0) g = 0; else if (g > 255) g = 255;
            return (g << 16) | (g << 8) | g;
        }

        float c = v * s;
        float hPrime = h / 60.0f; // 0..6
        float x = c * (1.0f - Math.abs((hPrime % 2.0f) - 1.0f));
        float m = v - c;

        float r1, g1, b1;
        if (hPrime < 1.0f) { r1 = c; g1 = x; b1 = 0.0f; }
        else if (hPrime < 2.0f) { r1 = x; g1 = c; b1 = 0.0f; }
        else if (hPrime < 3.0f) { r1 = 0.0f; g1 = c; b1 = x; }
        else if (hPrime < 4.0f) { r1 = 0.0f; g1 = x; b1 = c; }
        else if (hPrime < 5.0f) { r1 = x; g1 = 0.0f; b1 = c; }
        else { r1 = c; g1 = 0.0f; b1 = x; }

        int R = Math.round((r1 + m) * 255.0f);
        int G = Math.round((g1 + m) * 255.0f);
        int B = Math.round((b1 + m) * 255.0f);

        // Clamp RGB
        if (R < 0) R = 0; else if (R > 255) R = 255;
        if (G < 0) G = 0; else if (G > 255) G = 255;
        if (B < 0) B = 0; else if (B > 255) B = 255;

        return (R << 16) | (G << 8) | B;
    }

    private int pulseColorRGB(int rgb, float speed, float amp) {
        float sp  = (speed <= 0.0F ? 1.0F : speed);
        float a   = (amp   <= 0.0F ? 0.25F : amp);

        float t   = timeSeconds() * sp;
        float mul = 1.0F + a * (float)Math.sin(t);

        int r = (int)(((rgb >> 16) & 255) * mul);
        int g = (int)(((rgb >>  8) & 255) * mul);
        int b = (int)(( rgb        & 255) * mul);

        if (r < 0) r = 0; else if (r > 255) r = 255;
        if (g < 0) g = 0; else if (g > 255) g = 255;
        if (b < 0) b = 0; else if (b > 255) b = 255;

        return (r << 16) | (g << 8) | b;
    }

    private String plainTextFromOps(List<Op> ops) {
        StringBuilder sb = new StringBuilder();
        collectPlainText(ops, sb);
        return sb.toString();
    }

    private void collectPlainText(List<Op> ops, StringBuilder out) {
        if (ops == null) return;
        for (Op o : ops) {
            if (o == null) continue;

            if (o.kind == Kind.TEXT) {
                if (o.payload != null && o.payload.length() > 0) {
                    // drop our tags if they leaked, then strip § formatting
                    String seg = dropOurTagsOnlyPrefix(o.payload);
                    seg = stripVanillaFormatting(seg);
                    out.append(seg);
                }
            }

            if (o.children != null && !o.children.isEmpty()) {
                collectPlainText(o.children, out);
            }
        }
    }

    private String stripVanillaFormatting(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00A7' && i + 1 < s.length()) { i++; continue; }
            b.append(c);
        }
        return b.toString();
    }

    // if you already have a "strip tags" helper, use that instead
    private String stripAllTagsToPlainText(String s) {
        if (s == null || s.isEmpty()) return "";
        // quick-and-dirty remove <...> tags; ok for overlay input
        s = s.replaceAll("(?s)<[^>]+>", "");
        s = s.replaceAll("(?s)«[^»]+»", "");
        return stripVanillaFormatting(s);
    }

    private int drawRainbowAndReturnAdvanceCustom(String s, int x, int y,
                                                  boolean shadow,
                                                  int cycles, float sat, float val,
                                                  float phase, float speed,
                                                  boolean pulseOn, float pulseAmp, float pulseSpeed,
                                                  boolean gradWave, float gradWaveAmp, float gradWaveSpeed,
                                                  boolean gradRain, float gradRainAmp, float gradRainSpeed,
                                                  String legacyFmt) {
        s = cleanPayload(s);
        LegacyState local = new LegacyState();
        applyAndStripLegacyCodes(legacyFmt == null ? "" : legacyFmt, local);

        // If legacy fmt included §k we keep obfuscation on
        if (legacyFmt != null && legacyFmt.contains("§k")) {
            local.obfOnce = false;
            local.obfuscated = true;
        }

        s = applyAndStripLegacyCodes(s, local);
        if (s.isEmpty()) {
            return 0;
        }

        float fx = (float) x;
        int len = s.length();
        int cy = Math.max(1, cycles);

        // rainbow scroll phase (speed / scroll)
        float tphase = 0.0F;
        if (speed > 0.0F) {
            tphase = (timeSeconds() * speed) % 1.0F;
        }

        // brightness pulse
        float vNow = val;
        if (pulseOn) {
            float omega = Math.max(0.01F, pulseSpeed) * ((float) Math.PI * 2F);
            float p = 0.5F + 0.5F * (float) Math.sin(timeSeconds() * omega);
            vNow = clamp01((1.0F - pulseAmp) * val + pulseAmp * val * p);
        }

        boolean useWave = gradWave && !gradRain;
        boolean useRain = gradRain;

        for (int i = 0; i < len; ++i) {
            float t = (len <= 1) ? 0.0F : (float) i / (float) (len - 1);
            float h01 = (phase + tphase + t * (float) cy) % 1.0F;
            int rgb = hsvToRgb(h01 * 360.0F, sat, vNow);

            char ch = s.charAt(i);
            if (local.obfuscated) {
                ch = this.obfuscateCharSameWidth(ch);
            }

            String prefix = legacyPrefix(local);

            // wave / rain motion applied to rainbow text
            int yOffset = y;
            if (useWave) {
                float tWave = timeSeconds();
                float dy = (float) Math.sin((tWave * gradWaveSpeed) + (i * 0.4F)) * gradWaveAmp;
                yOffset = (int) (y + dy);
            } else if (useRain) {
                float tRain = timeSeconds();

                // Use gradRainAmp as fall distance, gradRainSpeed as falls/sec
                float fallDist = Math.max(0.5F, gradRainAmp);
                float fallSpeed = Math.max(0.05F, gradRainSpeed) * 0.15F; // scale down; your old defaults were "8"

                float rainphase = i * 0.17F;
                float u = (tRain * fallSpeed + phase) % 1.0F;

                float dy = (-fallDist) + (u * (fallDist * 2.0F));
                yOffset = (int) (y + dy);
            }

            int adv = this.drawTextWithLegacyInline(prefix + ch, (int) fx, yOffset, rgb, shadow, local);
            fx += adv;


            if (local.obfOnce) {
                local.obfuscated = false;
                local.obfOnce = false;
            }
        }

        return Math.round(fx) - x;
    }

    private int drawGradientMulti(String s, int x, int y, int[] stops, boolean shadow,
                                  boolean scroll, float speed, boolean pulseOn,
                                  float pulseAmp, float pulseSpeed,
                                  int[] scrollStops, int scrollRgb, float scrollMix,
                                  boolean gradWave, float gradWaveAmp, float gradWaveSpeed,
                                  boolean gradRain, float gradRainAmp, float gradRainSpeed,
                                  String legacyFmt) {


        s = cleanPayload(s);
        LegacyState local = new LegacyState();
        applyAndStripLegacyCodes(legacyFmt == null ? "" : legacyFmt, local);

        if (legacyFmt != null && legacyFmt.contains("§k")) {
            local.obfOnce = false;
        }

        s = applyAndStripLegacyCodes(s, local);

        if (s.isEmpty() || stops == null || stops.length < 2) {
            return 0;
        }

        float fx = (float) x;
        int len = s.length();
        int segments = stops.length - 1;

        boolean hasOverlayStops = (scrollStops != null && scrollStops.length >= 2);
        boolean hasOverlaySolid = (scrollRgb >= 0);
        boolean useOverlay = scroll && speed > 0.0F && (hasOverlayStops || hasOverlaySolid);

        float phase = (scroll && speed > 0.0F)
                ? (timeSeconds() * speed) % 1.0F
                : 0.0F;

        float vMul = 1.0F;
        if (pulseOn) {
            float omega = Math.max(0.01F, pulseSpeed) * (float) (Math.PI * 2F);
            float p = 0.5F + 0.5F * (float) Math.sin(timeSeconds() * omega);
            vMul = clamp01(1.0F - pulseAmp + pulseAmp * p);
        }

        scrollMix = clamp01(scrollMix);

        for (int i = 0; i < len; i++) {
            float tBase = (len <= 1) ? 0.0F : (float) i / (float) (len - 1);

            // Base gradient position:
            // - If we *don’t* have an overlay, keep old behavior (base scrolls).
            // - If we *do* have an overlay, base stays static and only overlay scrolls.
            float tGrad = tBase;
            if (!useOverlay && scroll && speed > 0.0F) {
                tGrad = (tBase + phase) % 1.0F;
            }

            float segPos = tGrad * (float) segments;
            int si = Math.min((int) Math.floor(segPos), segments - 1);
            float lt = segPos - si;

            int baseRgb = lerpRGB(stops[si], stops[si + 1], lt);
            int rgb = baseRgb;

            // ---- Overlay scroll (optional) ----
            if (useOverlay) {
                int overlayRgb = baseRgb;

                if (hasOverlayStops) {
                    int oSegs = scrollStops.length - 1;
                    float tOver = (tBase + phase) % 1.0F;
                    float segPosO = tOver * (float) oSegs;
                    int oi = Math.min((int) Math.floor(segPosO), oSegs - 1);
                    float lot = segPosO - oi;
                    overlayRgb = lerpRGB(scrollStops[oi], scrollStops[oi + 1], lot);
                } else if (hasOverlaySolid) {
                    overlayRgb = scrollRgb;
                }

                // Blend overlay into base
                rgb = lerpRGB(baseRgb, overlayRgb, scrollMix);
            }

            int rI = (rgb >> 16) & 255;
            int gI = (rgb >> 8) & 255;
            int bI = rgb & 255;

            if (pulseOn) {
                float mul = clamp01(vMul);
                rI = Math.round(rI * mul);
                gI = Math.round(gI * mul);
                bI = Math.round(bI * mul);
                rI = Math.min(255, Math.max(0, rI));
                gI = Math.min(255, Math.max(0, gI));
                bI = Math.min(255, Math.max(0, bI));
            }

            rgb = (rI << 16) | (gI << 8) | bI;
            rgb = applyForcedColor(rgb);

            char ch = s.charAt(i);
            if (local.obfuscated) {
                ch = this.obfuscateCharSameWidth(ch);
            }

            String prefix = legacyPrefix(local);

            // ---- gradient motion (wave / rain) ----
            int yOffset = y;
            if (gradWave) {
                // same feel as drawWave(...)
                float t = timeSeconds();
                float dy = (float) Math.sin((t * gradWaveSpeed) + (i * 0.4F)) * gradWaveAmp;
                yOffset = (int) (y + dy);
            } else if (gradRain) {
                // same feel as drawRain(...)
                float t = timeSeconds();
                float dy = (float) (Math.sin((t * gradRainSpeed) + i) * gradRainAmp);
                yOffset = (int) (y + dy);
            }

            int adv = this.drawTextWithLegacyInline(prefix + ch, (int) fx, yOffset, rgb, shadow, local);
            fx += adv;


            if (local.obfOnce) {
                local.obfuscated = false;
                local.obfOnce = false;
            }
        }

        return Math.round(fx) - x;
    }

    private int drawPulse(String s, int x, int y, boolean shadow, int baseRgb, float speed, float amp, String legacyFmt) {
        s = cleanPayload(s);
        LegacyState local = new LegacyState();
        applyAndStripLegacyCodes(legacyFmt == null ? "" : legacyFmt, local);
        if (legacyFmt != null && legacyFmt.contains("§k")) {
            local.obfOnce = false;
            local.obfuscated = true;
        }

        s = applyAndStripLegacyCodes(s, local);
        if (s.isEmpty()) {
            return 0;
        } else {
            float fx = (float)x;
            float r = (float)(baseRgb >> 16 & 255) / 255.0F;
            float g = (float)(baseRgb >> 8 & 255) / 255.0F;
            float b = (float)(baseRgb & 255) / 255.0F;
            float max = Math.max(r, Math.max(g, b));
            float min = Math.min(r, Math.min(g, b));
            float d = max - min;
            float sSat = max == 0.0F ? 0.0F : d / max;
            float h;
            if (d == 0.0F) {
                h = 0.0F;
            } else if (max == r) {
                h = 60.0F * ((g - b) / d % 6.0F);
            } else if (max == g) {
                h = 60.0F * ((b - r) / d + 2.0F);
            } else {
                h = 60.0F * ((r - g) / d + 4.0F);
            }

            if (h < 0.0F) {
                h += 360.0F;
            }

            float t = timeSeconds();
            float omega = Math.max(0.01F, speed) * ((float)Math.PI * 2F);
            float pulse = (float)((double)0.5F + (double)0.5F * Math.sin((double)(t * omega)));
            float v = clamp01(max * (1.0F - amp) + max * amp * pulse);
            int rgb = hsvToRgb(h, sSat, v);

            for(int i = 0; i < s.length(); ++i) {
                char ch = s.charAt(i);
                if (local.obfuscated) {
                    ch = this.obfuscateCharSameWidth(ch);
                }

                String prefix = legacyPrefix(local);
                int adv = this.drawTextWithLegacyInline(prefix + ch, (int)fx, y, rgb, shadow, local);
                fx += adv;

                if (local.obfOnce) {
                    local.obfuscated = false;
                    local.obfOnce = false;
                }
            }

            return Math.round(fx) - x;
        }
    }

    private int drawWavePlain(String s, int x, int y, int color, boolean shadow,
                              float amp, float speed, java.util.List<Op> kids) {

        if (s == null || s.length() == 0) return 0;

        float a = (amp <= 0.0F ? 2.0F : amp);
        float sp = (speed <= 0.0F ? 6.0F : speed);

        int[] colors = null;
        if (kids != null && kids.size() > 0) {
            colors = new int[s.length()];
            for (int i = 0; i < s.length(); i++) {
                colors[i] = colorAtIndexFromOps(kids, i, color);
            }
        }

        float t = timeSeconds();
        float fx = x;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            float dy = (float)Math.sin((t * sp) + (i * 0.55F)) * a;
            int col = (colors != null ? colors[i] : color);

            this.base.drawString(String.valueOf(c), (int)fx, (int)(y + dy), applyForcedMul(col), shadow);
            fx += this.base.getCharWidth(c);
        }

        return (int)(fx - x);
    }

    private int drawWave(String s, int x, int y, int color, boolean shadow) {
        float t = timeSeconds();
        float amp = 2.0f;      // amplitude
        float speed = 6.0F;    // wavespeed

        float fx = x;
        for (int i = 0; i < s.length(); i++) {
            float dy = (float) Math.sin((t * speed) + (i * 0.4F)) * amp;
            char c = s.charAt(i);
            this.base.drawString("" + c, (int) fx, (int) (y + dy), applyForcedMul(color), shadow);
            fx += this.base.getCharWidth(c);
        }
        return (int) (fx - x);
    }

    private int drawShake(String s, int x, int y, int color, boolean shadow) {
        float fx = x;
        Random r = this.obfRng;

        for (int i = 0; i < s.length(); i++) {
            float dx = (r.nextFloat() - 0.5F) * 2F;
            float dy = (r.nextFloat() - 0.5F) * 2F;
            char c = s.charAt(i);
            this.base.drawString("" + c, (int) (fx + dx), (int) (y + dy), applyForcedMul(color), shadow);
            fx += this.base.getCharWidth(c);
        }
        return (int) (fx - x);
    }


    // Same as drawShake, but can inherit per-character colors from nested ops (grad/rbw/etc).
    private int drawShakeColored(String s, int x, int y, int color, boolean shadow, java.util.List<Op> kids) {
        if (s == null || s.isEmpty()) return 0;

        float fx = x;
        Random r = this.obfRng;

        for (int i = 0; i < s.length(); i++) {
            float dx = (r.nextFloat() - 0.5F) * 2F;
            float dy = (r.nextFloat() - 0.5F) * 2F;
            char c = s.charAt(i);

            int cc = (kids != null && !kids.isEmpty()) ? colorAtIndexFromOps(kids, i, color) : color;
            this.base.drawString(String.valueOf(c), (int) (fx + dx), (int) (y + dy), applyForcedMul(cc), shadow);
            fx += this.base.getCharWidth(c);
        }
        return (int) (fx - x);
    }

    private int drawZoomColored(String s, int x, int y, int color, boolean shadow, java.util.List<Op> kids) {
        if (s == null || s.isEmpty()) return 0;

// Same "zoom pulse" cadence, but add a pop/explode window near the peak.
// Slower rebuild: damp the time input so the full cycle takes longer.
        float t = timeSeconds() * 0.55F;
        float w = (float) Math.sin(t * 5.0F); // -1..1
        float z = 0.5F * (w + 1.0F);          //  0..1

        float scale = 1.0F + 0.2F * w;        // original feel

        float popP = 0.0F;
        float fade = 1.0F;
        float explodePx = 22.0F;

        float p4 = 0.0F;

        if (z > 0.85F) {
            popP = (z - 0.85F) / 0.15F;
            if (popP < 0.0F) popP = 0.0F;
            if (popP > 1.0F) popP = 1.0F;

            float p2 = popP * popP;
            p4 = p2 * p2;

            // balloon pop: overshoot right at the end, then everything scatters + fades
            float overshoot = 1.0F + 0.25F * (float) Math.sin(popP * (float) Math.PI);
            scale *= overshoot;

            // Fade faster during pop (looks more like an actual burst)
            fade = 1.0F - p2;

            // More explody: larger radius + most travel near the end
            explodePx = 22.0F + 26.0F * p2;
        }

        float fx = x;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            int cc = (kids != null && !kids.isEmpty()) ? colorAtIndexFromOps(kids, i, color) : color;

            // fade during pop by darkening the RGB (keeps it compatible with no-alpha draw)
            if (fade < 1.0F) {
                int rr = (int) (((cc >> 16) & 255) * fade);
                int gg = (int) (((cc >> 8) & 255) * fade);
                int bb = (int) ((cc & 255) * fade);
                cc = (rr << 16) | (gg << 8) | bb;
            }

            int dx = 0, dy = 0;
            if (popP > 0.0F) {
                float a = zoomAngle(i, c);
                float off = (explodePx * 2.2F) * p4;
                dx = (int) (Math.cos(a) * off);
                dy = (int) (Math.sin(a) * off);
            }

            GL11.glPushMatrix();
            GL11.glTranslatef(fx + dx, y + dy, 0);
            GL11.glScalef(scale, scale, 1.0F);
            this.base.drawString(String.valueOf(c), 0, 0, applyForcedMul(cc), shadow);
            GL11.glPopMatrix();

            fx += this.base.getCharWidth(c) * scale;
        }
        return (int) (fx - x);
    }

    // ZOOM ONLY: stable per-letter direction so the "pop" scatters letters consistently.
    private static float zoomAngle(int i, char c) {
        int h = i * 374761393;
        h ^= (c * 668265263);
        h ^= 0x9E3779B9;
        h ^= (h >>> 13);
        h *= 1103515245;
        h ^= (h >>> 16);

        // 0..2pi using low bits
        return ((h & 1023) / 1023.0F) * 6.2831853F;
    }



    private int drawScrollColored(String s, int x, int y, int color, boolean shadow, java.util.List<Op> kids) {
        if (s == null || s.isEmpty()) return 0;

        float t = timeSeconds();
        float fx = x;

        for (int i = 0; i < s.length(); i++) {
            float dx = (float) Math.sin(t * 5.0F + i * 0.4F) * 2.0F;
            char c = s.charAt(i);

            int cc = (kids != null && !kids.isEmpty()) ? colorAtIndexFromOps(kids, i, color) : color;
            this.base.drawString(String.valueOf(c), (int) (fx + dx), y, applyForcedMul(cc), shadow);
            fx += this.base.getCharWidth(c);
        }
        return (int) (fx - x);
    }

    private int drawJitterColored(String s, int x, int y, int color, boolean shadow, float amp, float speed, java.util.List<Op> kids) {
        s = cleanPayload(s);
        if (s == null || s.isEmpty()) return 0;

        float sp = speed <= 0.0F ? 12.0F : speed;
        float a  = amp   <= 0.0F ? 1.25F : amp;

        // Precompute per-char colors once if nested ops exist
        int[] cols = null;
        if (kids != null && !kids.isEmpty()) {
            cols = new int[s.length()];
            for (int i = 0; i < s.length(); i++) {
                cols[i] = colorAtIndexFromOps(kids, i, color);
            }
        }

        // Jitter is "staccato" — it hops between positions, but we ease between hops so it doesn't look identical to shake.
        float tt = timeSeconds() * sp;
        int step = (int)Math.floor(tt);
        float f  = tt - step;

        // easing between steps
        float ease = f * f * (3.0F - 2.0F * f);

        float fx = x;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u0000') continue;

            // hash for previous and next step (deterministic)
            int h0 = i * 374761393 + step * 668265263;
            h0 ^= (h0 >>> 13);
            h0 *= 1274126177;
            h0 ^= (h0 >>> 16);

            int h1 = i * 374761393 + (step + 1) * 668265263;
            h1 ^= (h1 >>> 13);
            h1 *= 1274126177;
            h1 ^= (h1 >>> 16);

            // map to [-1, 1]
            float x0 = (((h0       ) & 1023) / 511.5F) - 1.0F;
            float y0 = (((h0 >> 10) & 1023) / 511.5F) - 1.0F;
            float x1 = (((h1       ) & 1023) / 511.5F) - 1.0F;
            float y1 = (((h1 >> 10) & 1023) / 511.5F) - 1.0F;

            // Occasionally do a bigger "pop" (keeps it distinct from shake)
            float pop0 = (((h0 >> 20) & 255) / 255.0F);
            float pop1 = (((h1 >> 20) & 255) / 255.0F);
            float p0 = pop0 > 0.86F ? 1.85F : 1.0F;
            float p1 = pop1 > 0.86F ? 1.85F : 1.0F;

            float dx = (x0 * p0) * (1.0F - ease) + (x1 * p1) * ease;
            float dy = (y0 * p0) * (1.0F - ease) + (y1 * p1) * ease;

            int col = (cols != null && i < cols.length) ? cols[i] : color;

            drawCharWithColor(c, (int)(fx + dx * a), (int)(y + dy * a), col, shadow);
            fx += this.base.getCharWidth(c);
        }

        return (int)(fx - x);
    }



    private int drawWobbleColored(String s, int x, int y, int color, boolean shadow, float amp, float speed, java.util.List<Op> kids) {
        s = cleanPayload(s);
        if (s == null || s.isEmpty()) return 0;

        float fx = x;
        float a  = amp   > 0.0F ? amp   : 3.0F;
        float sp = speed > 0.0F ? speed : 2.0F;
        float t  = timeSeconds();

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            // Per-letter phase offset / slight amp variance (stable, not jitter)
            int h = i * 374761393;
            h ^= (c * 668265263);
            h = (h ^ (h >>> 13)) * 1274126177;
            float r01 = (h & 1023) / 1023.0F;           // 0..1
            float off = (r01 - 0.5F) * 1.4F;            // -0.7..+0.7
            float ai  = a * (0.90F + 0.25F * r01);      // ~0.90a..1.15a

            float phase = (t * sp) + (i * 0.55F) + off;

// U-path wobble: letters trace a "half-pipe" (ends high, center low) with smooth left↔right sway.
// Screen-space note: +Y is down, so bigger dy means the letter sits lower.
            float ampY = ai * 1.15F;   // vertical depth of the U
            float ampX = ai * 0.95F;   // horizontal travel across the U

            float u = (float)Math.sin(phase);      // -1..+1 (left..right)
            float dx = ampX * u;                   // left/right
            float dy = ampY * (1.0F - (u * u));    // 0 at edges, max at center (U shape)
            int cc = (kids != null && !kids.isEmpty()) ? colorAtIndexFromOps(kids, i, color) : color;
            this.base.drawString(String.valueOf(c), (int) (fx + dx), (int) (y + dy), applyForcedMul(cc), shadow);

            fx += this.base.getCharWidth(c);
        }
        return (int) (fx - x);
    }
    private int drawLoopColored(String s, int x, int y, int color, boolean shadow, float amp, float speed, java.util.List<Op> kids) {
        s = cleanPayload(s);
        if (s == null || s.isEmpty()) return 0;

        // Keep the feel of the "standard wave", but evolve the PATH:
        //   wave segment -> single loop -> double loop (figure-8-ish),
        // and do it PER-CHAR (like cars on a roller coaster), not as a whole-word mode toggle.
        float sp = speed <= 0.0F ? 6.0F : speed;
        float a  = amp   <= 0.0F ? 2.2F : amp;


        // Make loop segments larger than the base wave (roller-coaster feel)
        final float LOOP_SCALE = 1.6F;
// Kids may include nested color pushes; precompute per-char colors once.
        int[] cols = null;
        if (kids != null && !kids.isEmpty()) {
            cols = new int[s.length()];
            for (int i = 0; i < s.length(); i++) {
                cols[i] = colorAtIndexFromOps(kids, i, color);
            }
        }

        // Drive time like the standard wave does.
        float t = timeSeconds() * sp;
        float fx = x;

        // Segment boundaries on the "track" parameter u in [0,1)
        final float W_END = 0.42F;  // wave portion
        final float L_END = 0.72F;  // single loop portion
        final float BLEND = 0.06F;  // smooth transition width

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u0000') continue;

            // Phase offset so motion travels across the word (wave-like).
            float p = t + i * 0.18F;

            // Track parameter u: "train cars" are spaced by i, and the whole train moves with time.
            // Using a slow multiplier keeps the coaster readable (avoids twitch/jitter).
            float u = (p * 0.085F);
            u = u - (float)Math.floor(u); // fract(u)

            // Compute offsets for each segment.
            float waveY = (float)Math.sin(p) * a;
            float waveX = 0.0F;

            // Single loop: one full circle per segment
            float vL = (u - W_END) / (L_END - W_END);
            if (vL < 0.0F) vL = 0.0F;
            if (vL > 1.0F) vL = 1.0F;
            float angL = vL * (float)(Math.PI * 2.0);
            float loopX = (float)Math.cos(angL) * (a * LOOP_SCALE);
            float loopY = -(float)Math.sin(angL) * (a * LOOP_SCALE);

            // Double loop: two circles / figure-8-ish feel
            float vD = (u - L_END) / (1.0F - L_END);
            if (vD < 0.0F) vD = 0.0F;
            if (vD > 1.0F) vD = 1.0F;
            float angD = vD * (float)(Math.PI * 4.0); // 0..4π
            float dX = (float)Math.cos(angD) * (a * 0.95F * LOOP_SCALE);
            float dY = -(float)Math.sin(angD) * (a * 0.95F * LOOP_SCALE);
            // Add a gentle "roller coaster" bank so it reads like a double-loop track
            dX *= (0.70F + 0.30F * (float)Math.sin(angD * 0.5F));

            // Blend between segments so the track is continuous.
            // Weights are per-char based on u, so you can SEE wave->loop->double-loop across the text.
            float wWave = 1.0F;
            float wLoop = 0.0F;
            float wDbl  = 0.0F;

            // wave -> loop blend around W_END
            if (u > W_END - BLEND) {
                float tt = smoothstep(W_END - BLEND, W_END + BLEND, u);
                wWave = 1.0F - tt;
                wLoop = tt;
            }

            // loop -> double loop blend around L_END
            if (u > L_END - BLEND) {
                float tt = smoothstep(L_END - BLEND, L_END + BLEND, u);
                // fade loop into double loop
                wDbl = tt;
                wLoop = (1.0F - tt) * (u >= W_END ? 1.0F : wLoop);
                wWave = 0.0F;
            }

            // Normalize just in case overlaps occurred
            float sum = wWave + wLoop + wDbl;
            if (sum <= 0.0001F) {
                wWave = 1.0F; wLoop = 0.0F; wDbl = 0.0F; sum = 1.0F;
            }
            wWave /= sum; wLoop /= sum; wDbl /= sum;

            float dx = waveX * wWave + loopX * wLoop + dX * wDbl;
            float dy = waveY * wWave + loopY * wLoop + dY * wDbl;

            int col = (cols != null && i < cols.length) ? cols[i] : color;

            drawCharWithColor(c, (int)(fx + dx), (int)(y + dy), col, shadow);
            fx += this.base.getCharWidth(c);
        }

        return (int)(fx - x);
    }


    /**
     * Draw a single character using the wrapped vanilla FontRenderer.
     * We intentionally draw via drawString(String) to avoid relying on protected internals.
     */
    private void drawCharWithColor(char c, int x, int y, int color, boolean shadow) {
        int col = applyForcedColor(color);
        this.base.drawString(String.valueOf(c), x, y, col, shadow);
    }


    private static float smoothstep(float e0, float e1, float x) {
        float t = (x - e0) / (e1 - e0);
        if (t < 0.0F) t = 0.0F;
        if (t > 1.0F) t = 1.0F;
        return t * t * (3.0F - 2.0F * t);
    }


    private int drawZoom(String s, int x, int y, int color, boolean shadow) {
        if (s == null || s.isEmpty()) return 0;

// Old zoom was basically a faster pulse. Keep the same cadence, but add a
// "pop/explode" window near the peak where letters scatter + fade, then repeat.
// Slower rebuild: damp the time input so the full cycle takes longer.
        float t = timeSeconds() * 0.55F;
        float w = (float) Math.sin(t * 5.0F); // -1..1
        float z = 0.5F * (w + 1.0F);          //  0..1

        float scale = 1.0F + 0.2F * w;        // ~0.8..1.2 (original feel)

// Explode near the max zoom
        float popP = 0.0F;   // 0..1 in pop window
        float fade = 1.0F;   // 1..0
        float explodePx = 22.0F;

        float p4 = 0.0F;

        if (z > 0.85F) {
            popP = (z - 0.85F) / 0.15F;
            if (popP < 0.0F) popP = 0.0F;
            if (popP > 1.0F) popP = 1.0F;

            // push slightly harder at the end
            float p2 = popP * popP;
            p4 = p2 * p2;
            float overshoot = 1.0F + 0.25F * (float) Math.sin(popP * (float) Math.PI);
            scale *= overshoot;

            // Fade faster during pop (looks more like an actual burst)
            fade = 1.0F - p2;

            // More explody: larger radius + most travel near the end
            explodePx = 22.0F + 26.0F * p2;
        }

        float fx = x;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            int col = color;
            if (fade < 1.0F) {
                int rr = (int) (((col >> 16) & 255) * fade);
                int gg = (int) (((col >> 8) & 255) * fade);
                int bb = (int) ((col & 255) * fade);
                col = (rr << 16) | (gg << 8) | bb;
            }

            int dx = 0, dy = 0;
            if (popP > 0.0F) {
                float a = zoomAngle(i, c);
                float off = (explodePx * 2.2F) * p4;
                dx = (int) (Math.cos(a) * off);
                dy = (int) (Math.sin(a) * off);
            }

            GL11.glPushMatrix();
            GL11.glTranslatef(fx + dx, y + dy, 0);
            GL11.glScalef(scale, scale, 1.0F);
            this.base.drawString(String.valueOf(c), 0, 0, applyForcedMul(col), shadow);
            GL11.glPopMatrix();

            fx += this.base.getCharWidth(c) * scale;
        }
        return (int) (fx - x);
    }
    private int colorAtIndexFromOps(java.util.List<Op> kids, int idx, int fallback) {
        // Walk the ops in visible text order and find the style that applies to char idx
        int cursor = 0;
        int cur = fallback;
        java.util.ArrayDeque<Integer> stack = new java.util.ArrayDeque<Integer>();

        for (Op c : kids) {
            if (c == null) continue;

            // If this op contains nested text, use its visible length to locate idx
            if (c.children != null && !c.children.isEmpty()) {
                String subPlain = plainTextFromOps(c.children);
                int len = (subPlain == null ? 0 : subPlain.length());

                if (idx < cursor + len) {
                    int rel = idx - cursor;

                    // ✅ Special-case wrappers that compute per-char colors themselves.
                    // If we just descend, we'd lose gradient/rainbow info because the inner TEXT ops
                    // don't carry per-char colors.
                    if (c.kind == Kind.GRADIENT_MULTI) {
                        return sampleGradientMultiColorAt(c, rel, len, cur);
                    }
                    if (c.kind == Kind.RAINBOW_TEXT) {
                        return sampleRainbowColorAt(c, rel, len, cur);
                    }

                    // Default: descend normally
                    return colorAtIndexFromOps(c.children, rel, cur);
                }

                cursor += len;
                continue;
            }

            // Plain text consumes characters
            if (c.kind == Kind.TEXT) {
                String t = (c.payload == null ? "" : c.payload);
                int len = t.length();
                if (idx < cursor + len) return cur;
                cursor += len;
                continue;
            }

            // Hex push/pop in nested mode
            if (c.kind == Kind.PUSH_HEX) {
                stack.push(cur);
                if (c.rgb >= 0) cur = (c.rgb & 0xFFFFFF);
                continue;
            }
            if (c.kind == Kind.POP_HEX) {
                if (!stack.isEmpty()) cur = stack.pop();
                continue;
            }

            // Wrapper op directly provides a solid color
            if (c.rgb >= 0) cur = (c.rgb & 0xFFFFFF);
        }

        return fallback;
    }

    /**
     * Compute the GRADIENT_MULTI color at a specific character index.
     * Mirrors drawGradientMulti's per-char math, but without rendering.
     */
    private int sampleGradientMultiColorAt(Op op, int i, int len, int fallback) {
        if (op == null || op.stops == null || op.stops.length < 2) return fallback;

        int[] stops = op.stops;
        int segments = stops.length - 1;

        boolean scroll = op.scroll;
        float speed = op.speed;

        int[] scrollStops = op.scrollStops;
        int scrollRgb = op.scrollRgb;
        float scrollMix = clamp01(op.scrollMix);

        boolean hasOverlayStops = (scrollStops != null && scrollStops.length >= 2);
        boolean hasOverlaySolid = (scrollRgb >= 0);
        boolean useOverlay = scroll && speed > 0.0F && (hasOverlayStops || hasOverlaySolid);

        float phase = (scroll && speed > 0.0F)
                ? (timeSeconds() * speed) % 1.0F
                : 0.0F;

        // brightness pulse (matches drawGradientMulti)
        float vMul = 1.0F;
        if (op.pulseOn) {
            float omega = Math.max(0.01F, op.pulseSpeed) * (float)(Math.PI * 2F);
            float p = 0.5F + 0.5F * (float)Math.sin(timeSeconds() * omega);
            vMul = clamp01(1.0F - op.pulseAmp + op.pulseAmp * p);
        }

        float tBase = (len <= 1) ? 0.0F : (float)i / (float)(len - 1);

        // Base gradient position:
        // - If we don’t have an overlay, base can scroll (old behavior).
        // - If we do have an overlay, base stays static and only overlay scrolls.
        float tGrad = tBase;
        if (!useOverlay && scroll && speed > 0.0F) {
            tGrad = (tBase + phase) % 1.0F;
        }

        float segPos = tGrad * (float)segments;
        int si = Math.min((int)Math.floor(segPos), segments - 1);
        float lt = segPos - si;

        int baseRgb = lerpRGB(stops[si], stops[si + 1], lt);
        int rgb = baseRgb;

        // Overlay scroll (optional)
        if (useOverlay) {
            int overlayRgb = baseRgb;

            if (hasOverlayStops) {
                int oSegs = scrollStops.length - 1;
                float tOver = (tBase + phase) % 1.0F;
                float segPosO = tOver * (float)oSegs;
                int oi = Math.min((int)Math.floor(segPosO), oSegs - 1);
                float lot = segPosO - oi;
                overlayRgb = lerpRGB(scrollStops[oi], scrollStops[oi + 1], lot);
            } else if (hasOverlaySolid) {
                overlayRgb = scrollRgb;
            }

            rgb = lerpRGB(baseRgb, overlayRgb, scrollMix);
        }

        // Apply pulse brightness
        if (op.pulseOn) {
            int r = (rgb >> 16) & 255;
            int g = (rgb >> 8) & 255;
            int b = rgb & 255;
            r = Math.round(r * vMul);
            g = Math.round(g * vMul);
            b = Math.round(b * vMul);
            r = Math.min(255, Math.max(0, r));
            g = Math.min(255, Math.max(0, g));
            b = Math.min(255, Math.max(0, b));
            rgb = (r << 16) | (g << 8) | b;
        }

        return rgb & 0xFFFFFF;
    }

    /**
     * Compute the RAINBOW_TEXT color at a specific character index.
     * Mirrors drawRainbowAndReturnAdvanceCustom's per-char math, but without rendering.
     */
    private int sampleRainbowColorAt(Op op, int i, int len, int fallback) {
        if (op == null) return fallback;

        int cy = Math.max(1, op.cycles);

        float tphase = 0.0F;
        if (op.speed > 0.0F) {
            tphase = (timeSeconds() * op.speed) % 1.0F;
        }

        // brightness pulse (matches drawRainbowAndReturnAdvanceCustom)
        float vNow = op.val;
        if (op.pulseOn) {
            float omega = Math.max(0.01F, op.pulseSpeed) * (float)(Math.PI * 2F);
            float p = 0.5F + 0.5F * (float)Math.sin(timeSeconds() * omega);
            vNow = clamp01((1.0F - op.pulseAmp) * op.val + op.pulseAmp * op.val * p);
        }

        float t = (len <= 1) ? 0.0F : (float)i / (float)(len - 1);
        float h01 = (op.phase + tphase + t * (float)cy) % 1.0F;

        int rgb = hsvToRgb(h01 * 360.0F, op.sat, vNow);
        return rgb & 0xFFFFFF;
    }

    private static void parseRainDropColorArgs(String attrs, Op out) {
        if (attrs == null || attrs.isEmpty() || out == null) return;

        // drop=inherit | drop=#RRGGBB | drop=#A,#B,#C | drop=rbw
        Matcher m = Pattern.compile("(?i)\\b(drop|dropcol|dropcolor|raincol|raincolor)\\s*=\\s*([^\\s>]+)")
                .matcher(attrs);

        if (m.find()) {
            String v = m.group(2).trim();
            v = v.replace("\"", "").replace("'", "");

            if (v.equalsIgnoreCase("inherit")) {
                // default behavior: use nested kids’ per-char colors
                return;
            }

            if (v.equalsIgnoreCase("rbw") || v.equalsIgnoreCase("rainbow")) {
                out.rainDropRainbow = true;
                return;
            }

            // gradient form: #A,#B,#C
            if (v.indexOf(',') >= 0) {
                String[] parts = v.split(",");
                java.util.ArrayList cols = new java.util.ArrayList(); // Java 6 (raw type)
                for (int i = 0; i < parts.length; i++) {
                    String p = parts[i];
                    if (p == null) continue;
                    p = p.trim();
                    if (p.length() == 0) continue;
                    try {
                        String pp = p;
                        if (pp.startsWith("#")) pp = pp.substring(1);
                        cols.add(Integer.valueOf(parseHexRGB(pp)));

                    } catch (Exception ignored) {}
                }
                if (!cols.isEmpty()) {
                    out.rainDropGrad = new int[cols.size()];
                    for (int i = 0; i < cols.size(); i++) {
                        out.rainDropGrad[i] = ((Integer) cols.get(i)).intValue();
                    }
                }
                return;
            }

            // solid form: #RRGGBB
            try {
                String vv = v;
                if (vv.startsWith("#")) vv = vv.substring(1);
                out.rainDropColor = parseHexRGB(vv);

            } catch (Exception ignored) {}
        }

        // (optional) allow rainbow tuning if you want:
        Matcher kv = Pattern.compile("(?i)\\b(dropcycles|dropsat|dropval|dropspeed|dropphase)\\s*=\\s*([\\d.]+)")
                .matcher(attrs);
        while (kv.find()) {
            String k = kv.group(1).toLowerCase();
            float f = parseFloatSafe(kv.group(2), 0.0F);
            if ("dropcycles".equals(k)) out.rainDropCycles = Math.max(1, (int) f);
            else if ("dropsat".equals(k)) out.rainDropSat = f;
            else if ("dropval".equals(k)) out.rainDropVal = f;
            else if ("dropspeed".equals(k)) out.rainDropSpeed = f;
            else if ("dropphase".equals(k)) out.rainDropPhase = f;
        }
    }

    private int drawRain(String s, int x, int y, int color, boolean shadow) {
        // draw normal text first (so it can contain legacy formatting)
        int adv = this.drawString(s, x, y, color, shadow);

        // overlay uses plain string (no tags) - this is for the non-nested path
        String plain = stripAllTagsToPlainText(s);

        // ✅ use x/color, and NO children here
        drawRainOverlayPlain(plain, x, y, color, shadow,
                0.45F, 18.0F, 14.0F, 0.45F);

        return adv;
    }

    // Legacy non-nested path (called by drawRain)
    private void drawRainOverlayPlain(String s, int x, int y, int color, boolean shadow,
                                      float baseSpeed, float fallDist, float startAbove, float visibleFrac) {
        drawRainOverlayPlain(s, x, y, color, shadow,
                baseSpeed, fallDist, startAbove, visibleFrac,
                (java.util.List<Op>) null, (Op) null);
    }



    private void drawRainOverlayPlain(String s, int x, int y, int color, boolean shadow,
                                      float baseSpeed, float fallDist, float startAbove, float visibleFrac,
                                      java.util.List<Op> kids, Op rainOp) {

        if (s == null || s.isEmpty()) return;

        // existing per-char colors from kids (inherit path)
        int[] colors = null;
        if (kids != null && !kids.isEmpty()) {
            colors = new int[s.length()];
            for (int i = 0; i < s.length(); i++) {
                colors[i] = colorAtIndexFromOps(kids, i, color);
            }
        }

        final int len = s.length();
        float t = timeSeconds();
        float fx = x;

        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);

            int h = i * 374761393;
            h ^= (c * 668265263);
            h = (h ^ (h >>> 13)) * 1274126177;
            h ^= (h >>> 16);

            float r = (h & 0x7fffffff) / 2147483647.0f;

            float speedMul = 0.65F + (r * 0.90F);
            float phase = (r * 0.97F) + (i * 0.21F);

            float u = (t * baseSpeed * speedMul + phase) % 1.0F;

            if (u < visibleFrac) {
                float v = u / visibleFrac;
                float dy = (-startAbove) + (v * fallDist);
                float drift = (float)Math.sin((t * (0.6F + r)) + i * 1.7F) * 0.18F;

                // ✅ choose rain drop color source
                int dropCol;

                if (rainOp != null && rainOp.rainDropRainbow) {
                    float tphase = (t * rainOp.rainDropSpeed) + rainOp.rainDropPhase;
                    float tt = (len <= 1) ? 0.0F : (float)i / (float)(len - 1);
                    float h01 = (tphase + tt * (float)rainOp.rainDropCycles) % 1.0F;
                    dropCol = hsvToRgb(h01 * 360.0F, rainOp.rainDropSat, rainOp.rainDropVal);

                } else if (rainOp != null && rainOp.rainDropGrad != null && rainOp.rainDropGrad.length > 0) {
                    float tt = (len <= 1) ? 0.0F : (float)i / (float)(len - 1);
                    float g = (tt + (t * 0.15F)) % 1.0F; // subtle drift; tweak if desired
                    dropCol = sampleGrad(rainOp.rainDropGrad, g);

                } else if (rainOp != null && rainOp.rainDropColor >= 0) {
                    dropCol = rainOp.rainDropColor;

                } else {
                    // default = inherit from nested kids if present, else base color
                    dropCol = (colors != null ? colors[i] : color);
                }

                this.base.drawString("" + c, (int)(fx + drift), (int)(y + dy), applyForcedMul(dropCol), shadow);
            }

            fx += this.base.getCharWidth(c);
        }
    }



    private int drawScroll(String s, int x, int y, int color, boolean shadow) {
        float t = timeSeconds();
        float fx = x;

        for (int i = 0; i < s.length(); i++) {
            float dx = (float) Math.sin(t * 5.0F + i * 0.4F) * 2.0F;
            char c = s.charAt(i);
            this.base.drawString("" + c, (int) (fx + dx), y, applyForcedMul(color), shadow);
            fx += this.base.getCharWidth(c);
        }
        return (int) (fx - x);
    }

    private int drawJitter(String s, int x, int y, int color, boolean shadow, float amp, float speed) {
        s = cleanPayload(s);
        if (s == null || s.isEmpty()) return 0;

        float fx = x;
        float jitterAmp = amp > 0.0F ? amp : 1.0F;
        Random r = this.obfRng;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            float dx = (r.nextFloat() - 0.5F) * 2.0F * jitterAmp;
            float dy = (r.nextFloat() - 0.5F) * 2.0F * jitterAmp;
            this.base.drawString(String.valueOf(c), (int) (fx + dx), (int) (y + dy), applyForcedMul(color), shadow);
            fx += this.base.getCharWidth(c);
        }
        return Math.round(fx) - x;
    }

    private int drawWobble(String s, int x, int y, int color, boolean shadow, float amp, float speed) {
        s = cleanPayload(s);
        if (s == null || s.isEmpty()) return 0;

        float fx = x;
        float a  = amp   > 0.0F ? amp   : 3.0F;
        float sp = speed > 0.0F ? speed : 2.0F;
        float t  = timeSeconds();

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            // Per-letter phase offset / slight amp variance (stable, not jitter)
            int h = i * 374761393;
            h ^= (c * 668265263);
            h = (h ^ (h >>> 13)) * 1274126177;
            float r01 = (h & 1023) / 1023.0F;           // 0..1
            float off = (r01 - 0.5F) * 1.4F;            // -0.7..+0.7
            float ai  = a * (0.90F + 0.25F * r01);      // ~0.90a..1.15a

            float phase = (t * sp) + (i * 0.55F) + off;

// U-path wobble: letters trace a "half-pipe" (ends high, center low) with smooth left↔right sway.
// Screen-space note: +Y is down, so bigger dy means the letter sits lower.
            float ampY = ai * 1.15F;   // vertical depth of the U
            float ampX = ai * 0.95F;   // horizontal travel across the U

            float u = (float)Math.sin(phase);      // -1..+1 (left..right)
            float dx = ampX * u;                   // left/right
            float dy = ampY * (1.0F - (u * u));    // 0 at edges, max at center (U shape)
            this.base.drawString(String.valueOf(c), (int) (fx + dx), (int) (y + dy), applyForcedMul(color), shadow);
            fx += this.base.getCharWidth(c);
        }
        return Math.round(fx) - x;
    }


    private int drawShootingStarColored(String s, int x, int y, int color, boolean shadow,
                                        java.util.List<Op> kids, Op cfg) {
        s = cleanPayload(s);
        if (s == null || s.isEmpty()) return 0;

        // Precompute per-char colors once (supports grad/rbw/solid nested)
        int[] cols = null;
        if (kids != null && !kids.isEmpty()) {
            cols = new int[s.length()];
            for (int i = 0; i < s.length(); i++) {
                cols[i] = colorAtIndexFromOps(kids, i, color);
            }
        }

        float now = timeSeconds();

        float cycle = (cfg != null && cfg.ssCycleSec > 0.0F) ? cfg.ssCycleSec : 5.8F;
        cycle = Math.max(0.6F, cycle);

        float glowF = (cfg != null) ? clamp01(cfg.ssGlowFrac) : 0.19F;
        float flyF  = (cfg != null) ? clamp01(cfg.ssFlightFrac) : 0.45F;


        float arc  = (cfg != null) ? cfg.ssArc : 15.0F;
        float dist = (cfg != null) ? cfg.ssDist : 45.0F;
        float chance = 1.0F; // force every letter to participate (random timing is per-letter via phase offset)

        float explodeF = (cfg != null) ? clamp01(cfg.ssExplodeFrac) : 0.12F;
        float boomDist = (cfg != null) ? cfg.ssExplodeDist : 10.0F;
        float boomGlow = (cfg != null) ? clamp01(cfg.ssExplodeGlow) : 0.65F;

        // Keep timing sane: glow + flight + explode <= 0.98
        float sum = glowF + flyF + explodeF;
        if (sum > 0.98F) {
            float k = 0.98F / Math.max(0.0001F, sum);
            glowF *= k;
            flyF  *= k;
            explodeF *= k;
        }

        float fx = x;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            int w = this.base.getCharWidth(c);
            if (w <= 0) {
                fx += w;
                continue;
            }

            int baseCol = (cols != null && i < cols.length) ? cols[i] : color;

            // stable per-char seed
            int h = i * 374761393;
            h ^= (c * 668265263);
            h = (h ^ (h >>> 13)) * 1274126177;
            h ^= (h >>> 16);

            float r01 = (h & 1023) / 1023.0F;
            boolean active = true; // always active; per-letter randomness comes from off + stable seed

            // phase offset per char (keeps it non-synced)
            float off = (((h >>> 10) & 1023) / 1023.0F) * cycle;
            float u = (now + off) / cycle;
            u = u - (float)Math.floor(u); // fract 0..1

            if (!active) {
                // normal draw
                this.base.drawString(String.valueOf(c), (int)fx, y, applyForcedMul(baseCol), shadow);
                fx += w;
                continue;
            }

            if (u < glowF) {
                float g = (glowF <= 0.0001F) ? 1.0F : clamp01(u / glowF);
                // ease-in
                g = g * g;

                int glowCol = mixToWhite(baseCol, 0.65F * g);
                float alpha = 0.80F + 0.20F * g;

                drawGlowGlyph(c, (int)fx, y, glowCol, 0.55F * alpha, shadow);
                this.base.drawString(String.valueOf(c), (int)fx, y, argb(alpha, glowCol), shadow);

                fx += w;
                continue;
            }

            if (u < glowF + flyF) {
                float p = (flyF <= 0.0001F) ? 1.0F : clamp01((u - glowF) / flyF);

                // arc (UP): y is screen-down, so negative dy goes up
                float dy = -arc * (4.0F * p * (1.0F - p));
                float dx = dist * p;

                // fade late in flight
                float fade = (p < 0.70F) ? 1.0F : clamp01(1.0F - ((p - 0.70F) / 0.30F));
                float alpha = fade;

                int flyCol = mixToWhite(baseCol, 0.85F);
                drawGlowGlyph(c, (int)(fx + dx), (int)(y + dy), flyCol, 0.60F * alpha, shadow);
                this.base.drawString(String.valueOf(c), (int)(fx + dx), (int)(y + dy), argb(alpha, flyCol), shadow);

                // do NOT draw at the base position during flight
                fx += w;
                continue;
            }


            if (u < glowF + flyF + explodeF) {
                float e = (explodeF <= 0.0001F) ? 1.0F : clamp01((u - (glowF + flyF)) / explodeF);

                // deterministic explosion “shards” (multi-copy burst)
                int shards = 7; // more shards = bigger “pop”
                float baseAng = (((h >>> 20) & 1023) / 1023.0F) * 6.2831855F;

                // ease-out expansion
                float rr = boomDist * (1.0F - (1.0F - e) * (1.0F - e));

                // fade out quickly
                float alpha = clamp01(1.0F - e);

                int boomCol = mixToWhite(baseCol, 0.90F);

                // main burst: multiple ghost copies flying outward
                for (int sh = 0; sh < shards; sh++) {
                    int hh = h ^ (sh * 0x9E3779B9);
                    hh = (hh ^ (hh >>> 16)) * 0x85EBCA6B;
                    hh = (hh ^ (hh >>> 13)) * 0xC2B2AE35;


                    float ang = baseAng + (6.2831855F * sh / (float)shards) + (r01 - 0.5F) * 0.55F;
                    float rad = rr * (0.45F + 0.70F * r01);
                    float ox = (float)Math.cos(ang) * rad;
                    float oy = (float)Math.sin(ang) * rad;

                    // bias explosion upward (screen-down => negative)
                    oy -= rad * 0.45F;

                    float a2 = alpha * (0.55F + 0.45F * (1.0F - r01));
                    drawGlowGlyph(c, (int)(fx + ox), (int)(y + oy), boomCol, boomGlow * a2, shadow);
                    this.base.drawString(String.valueOf(c), (int)(fx + ox), (int)(y + oy), argb(a2, boomCol), shadow);
                }

                // tiny “core flash” at origin for impact
                drawGlowGlyph(c, (int)fx, y, boomCol, boomGlow * alpha * 0.75F, shadow);
                fx += w;
                continue;
            }

            // rest: fade + slide back to the base position (prevents a snap-back) and repeat
            float restStart = glowF + flyF + explodeF;
            float restLen = 1.0F - restStart;

            if (restLen > 0.0001F) {
                float p = clamp01((u - restStart) / restLen); // 0..1
                // start from the flight end (dx=dist) and come back to origin
                float dx = dist * (1.0F - p);
                float dy = 0.0F;

                // ease-in so it "reappears" smoothly
                float a = p * p;

                // subtle trailing glow while returning
                int retCol = mixToWhite(baseCol, 0.35F * a);
                drawGlowGlyph(c, (int)(fx + dx), (int)(y + dy), retCol, 0.35F * a, shadow);
                this.base.drawString(String.valueOf(c), (int)(fx + dx), (int)(y + dy), argb(a, baseCol), shadow);
            } else {
                // extremely short/no rest window — just draw normally
                this.base.drawString(String.valueOf(c), (int)fx, y, applyForcedMul(baseCol), shadow);
            }

            fx += w;
        }

        return (int)(fx - x);
    }

    private void drawGlowGlyph(char c, int x, int y, int rgb, float alpha, boolean shadow) {
        int col = argb(alpha, rgb);
        // cheap glow: 4-neighbor blur
        this.base.drawString(String.valueOf(c), x + 1, y, col, shadow);
        this.base.drawString(String.valueOf(c), x - 1, y, col, shadow);
        this.base.drawString(String.valueOf(c), x, y + 1, col, shadow);
        this.base.drawString(String.valueOf(c), x, y - 1, col, shadow);
    }

    private static int argb(float a, int rgb) {
        int A = Math.min(255, Math.max(0, (int)(a * 255.0F)));
        return (A << 24) | (rgb & 0xFFFFFF);
    }

    private static int mixToWhite(int rgb, float t) {
        t = clamp01(t);
        int r = (rgb >> 16) & 255;
        int g = (rgb >> 8) & 255;
        int b = rgb & 255;

        r = (int)(r + (255 - r) * t);
        g = (int)(g + (255 - g) * t);
        b = (int)(b + (255 - b) * t);

        return (r << 16) | (g << 8) | b;
    }


    private int drawOutlineText(String s, int x, int y, int textColor, int outlineColor, boolean shadow) {
        if (s == null || s.isEmpty()) return 0;

        // ✅ Use full tag renderer so <rbw>/<rainbow>/<grad>/<pulse> etc work inside outline.
        // This may render nested tags multiple times (once per outline pass) which is expected for an outline effect.

        int adv = this.drawString(s, x + 1, y, outlineColor, shadow);
        this.drawString(s, x - 1, y, outlineColor, shadow);
        this.drawString(s, x, y + 1, outlineColor, shadow);
        this.drawString(s, x, y - 1, outlineColor, shadow);

        // Center text
        this.drawString(s, x, y, textColor, shadow);

        return adv;
    }

    private int drawShadowText(String s, int x, int y, int textColor, int shadowColor, boolean shadow) {
        if (s == null || s.isEmpty()) return 0;

        // How "thick" the depth looks (1..3 is plenty)
        final int depth = 2;

        // How dark the shadow is vs the chosen shadowColor (0..1)
        // 1.0 = pure shadowColor, lower = lighter
        final float strength = 0.85f;

        // OPTIONAL: slightly darken the main text to sell depth (0..1). 1.0 = unchanged
        final float mainMul = 0.97f;

        boolean prevForce = FORCE_FLAT_COLOR;
        int prevColor = FORCED_FLAT_COLOR;

        FORCE_FLAT_COLOR = true;

        try {
            // Build a "shape" shadow: a small wedge / blob behind the text
            // Draw farthest first, closest last.
            for (int d = depth; d >= 1; d--) {
                float t = (depth == 0) ? 1.0f : (d / (float)depth); // 0..1
                // Slight fade as it gets closer to the text
                float mul = strength * (0.55f + 0.45f * t);

                int c = mulRGB(shadowColor, mul);

                FORCED_FLAT_COLOR = c;

                // main diagonal drop
                this.drawString(s, x + d, y + d, c, false);

                // widen it a bit to make a "shape" (depth)
                if (d >= 2) {
                    this.drawString(s, x + d,     y + (d - 1), c, false);
                    this.drawString(s, x + (d - 1), y + d,     c, false);
                }
            }
        } finally {
            FORCE_FLAT_COLOR = prevForce;
            FORCED_FLAT_COLOR = prevColor;
        }

        // Main text (optionally darkened a hair for depth)
        int main = mulRGB(textColor, mainMul);
        return this.drawString(s, x, y, main, shadow);
    }

    private void drawSparkleOverlayPlain(String plain, int x, int y, int color, boolean shadow,
                                         float intensity, float speed,
                                         java.util.List<Op> kids, Op op) {
        plain = cleanPayload(plain);
        if (plain == null || plain.isEmpty()) return;

        // Precompute per-char colors when nested styles exist (grad/rbw/etc)
        int[] colors = null;
        if (kids != null && !kids.isEmpty()) {
            colors = new int[plain.length()];
            for (int i = 0; i < plain.length(); i++) {
                colors[i] = colorAtIndexFromOps(kids, i, color);
            }
        }

        // Star color settings (optional)
        final int starSolid = (op != null ? op.sparkleStarColor : -1);
        final int[] starGrad = (op != null ? op.sparkleStarGrad : null);
        final boolean starRbw = (op != null && op.sparkleStarRbw);
        final float starSpeed = (op != null ? op.sparkleStarSpeed : 1.0F);
        final float starCycles = (op != null ? op.sparkleStarCycles : 1.0F);
        final float starSat = (op != null ? op.sparkleStarSat : 1.0F);
        final float starVal = (op != null ? op.sparkleStarVal : 1.0F);


        float inten = intensity <= 0.0F ? 1.0F : intensity;
        float dens  = clamp01(inten);
        float sp    = (speed <= 0.01F ? 4.0F : speed);

        final char[] STARS = new char[]{'✦','✧','✶','✷','⋆','*','·'};

        float t = timeSeconds() * sp;

        int fx = x;

        for (int i = 0; i < plain.length(); i++) {
            char c = plain.charAt(i);

            int cw = this.base.getCharWidth(c);
            if (cw < 0) cw = 0;

            int h = i * 374761393;
            h ^= (c * 668265263);
            h = (h ^ (h >>> 13)) * 1274126177;
            h ^= (h >>> 16);

            float r01 = (h & 0x7fffffff) / 2147483647.0f;

            float appear = clamp01(0.10f + 0.45f * dens);

            float phase = r01 * 6.2831853f;
            float tw = 0.5f + 0.5f * (float)Math.sin(t + phase);
            boolean on = (r01 < appear) && (tw > 0.55f);

            if (on && cw > 0) {
                int idx = (int)((r01 * 997.0f) + (t * 1.4f)) % STARS.length;
                if (idx < 0) idx += STARS.length;
                char star = STARS[idx];

                int sx = fx + (int)(cw * (0.15f + 0.70f * r01));
                int sy = y - 1 - (int)(2.0f * (0.5f + 0.5f * (float)Math.sin(t * 0.8f + r01 * 9.0f)));

                float mix = clamp01(0.35f + 0.65f * tw);

                int baseTextCol = (colors != null ? colors[i] : color) & 0xFFFFFF;

                int baseStarCol;
                if (starRbw) {
                    // Rainbow stars (independent of text)
                    float hue = (timeSeconds() * (40.0f * starSpeed)) + (r01 * 360.0f)
                            + ((plain.length() <= 1) ? 0.0f : (i / (float)(plain.length() - 1)) * (360.0f * starCycles));
                    baseStarCol = hsvToRgb(hue, starSat, starVal) & 0xFFFFFF;
                } else if (starGrad != null && starGrad.length >= 2) {
                    float tt = (timeSeconds() * (0.25f * starSpeed)) + r01
                            + ((plain.length() <= 1) ? 0.0f : (i / (float)(plain.length() - 1)) * starCycles);
                    baseStarCol = sampleGrad(starGrad, tt) & 0xFFFFFF;
                } else if (starSolid >= 0) {
                    baseStarCol = starSolid & 0xFFFFFF;
                } else {
                    // Default: stars inherit the per-char text color
                    baseStarCol = baseTextCol;
                }

                int starColor = scaleRGB(baseStarCol, 0.60f + 0.80f * mix);

                this.base.drawString(String.valueOf(star), sx, sy, applyForcedMul(starColor), shadow);
            }

            fx += cw;
        }
    }


    private int drawSparkleText(String s, int x, int y, int color, boolean shadow, float intensity, float speed) {
        s = cleanPayload(s);
        if (s == null || s.isEmpty()) return 0;

        LegacyState st = new LegacyState();
        int fx = x;

        // Pass 1: draw the base text normally (keeps legacy formatting working)
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (c == '§' && i + 1 < s.length()) {
                String fmt = s.substring(i, i + 2);
                this.drawTextWithLegacyInline(fmt, 0, 0, color, false, st); // state only
                i++;
                continue;
            }

            int adv = this.drawTextWithLegacyInline(String.valueOf(c), fx, y, color, shadow, st);
            fx += adv;
        }

        int totalAdv = fx - x;

        // Pass 2: overlay twinkling stars (does NOT affect width)
        // IMPORTANT: pass plain text (no tags, no § codes)
        String plain = stripAllTagsToPlainText(s); // use your helper
        drawSparkleOverlayPlain(plain, x, y, color, shadow, intensity, speed, null, null);

        return totalAdv;
    }

    private int drawFlickerText(String s, int x, int y, int color, boolean shadow, float speed) {
        if (s == null || s.isEmpty()) return 0;

        float sp = speed <= 0.0F ? 6.0F : speed;

        // If we have nested color pushes (grad/rbw/solid), do a per-char pass so flicker is per letter.
        // If "real nested" styles exist inside, fall back to the original global-mul path to avoid
        // trying to animate ops-on-ops (keeps behavior consistent & stable).
        if (s.indexOf('<') >= 0 || s.indexOf('«') >= 0) {
            java.util.List<Op> kids = parseToOps(s);
            if (kids != null && !kids.isEmpty()) {
                if (!hasOnlyColorOps(kids)) {
                    // global mul fallback (preserves nested effects)
                    float t = timeSeconds() * sp;
                    float mul = 0.4F + 0.6F * Math.abs((float)Math.sin(t));
                    boolean prev = FORCE_COLOR_MUL;
                    float prevMul = FORCED_COLOR_MUL;
                    FORCE_COLOR_MUL = true;
                    FORCED_COLOR_MUL = mul;
                    try {
                        return drawOps(kids, x, y, color, shadow);
                    } finally {
                        FORCE_COLOR_MUL = prev;
                        FORCED_COLOR_MUL = prevMul;
                    }
                }

                String plain = plainTextFromOps(kids);
                return drawFlickerColored(plain, x, y, color, shadow, sp, kids);
            }
        }

        // Plain (no nested tags): per-letter random flicker
        return drawFlickerColored(s, x, y, color, shadow, sp, null);
    }

    private int drawFlickerColored(String s, int x, int y, int color, boolean shadow, float sp, java.util.List<Op> kids) {
        s = cleanPayload(s);
        if (s == null || s.isEmpty()) return 0;

        // Precompute per-char colors when nested pushes exist
        int[] cols = null;
        if (kids != null && !kids.isEmpty()) {
            cols = new int[s.length()];
            for (int i = 0; i < s.length(); i++) {
                cols[i] = colorAtIndexFromOps(kids, i, color);
            }
        }

        float t = timeSeconds();
        int bucket = (int)Math.floor(t * (sp * 0.75F)); // changes a few times per second

        float fx = x;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            int baseCol = (cols != null && i < cols.length) ? cols[i] : color;

            // stable seed per letter
            int h = i * 374761393;
            h ^= (c * 668265263);
            h ^= (bucket * 1274126177);
            h = (h ^ (h >>> 13)) * 1274126177;
            h ^= (h >>> 16);

            // 0..1 random each bucket
            float r01 = ((h >>> 1) & 0x7FFFFFFF) / 2147483647.0F;

            // smooth-ish flicker (independent phase per letter)
            float phase = (i * 0.85F) + (((h >>> 20) & 1023) / 1023.0F) * 6.28318F;
            float wob = 0.35F + 0.65F * Math.abs((float)Math.sin((t * sp) + phase));

            // final brightness multiplier: random * smooth (keeps it from being pure strobe)
            float mul = 0.20F + 0.80F * (r01 * wob);

            // occasional brighter pop
            if (r01 > 0.93F) {
                mul = 0.95F + 0.05F * wob;
            }

            int rr = (int)(((baseCol >> 16) & 255) * mul);
            int gg = (int)(((baseCol >> 8) & 255) * mul);
            int bb = (int)((baseCol & 255) * mul);

            int flickerColor = (rr << 16) | (gg << 8) | bb;

            drawCharWithColor(c, (int)fx, y, flickerColor, shadow);

            fx += this.base.getCharWidth(c);
        }

        return (int)(fx - x);
    }


    private int drawGlitchText(String s, int x, int y, int color, boolean shadow,
                               float amount, float pulseSpeed, float pulseAmp,
                               boolean overlayOnly) {

        s = cleanPayload(s);
        if (s == null || s.isEmpty()) return 0;

        float amt = amount <= 0.0F ? 1.0F : amount;
        Random r = this.obfRng;
        float prob = clamp01(amt * 0.35F);

        float tFx = x;

        if (!overlayOnly) {
            // original: supports legacy § formatting
            LegacyState st = new LegacyState();

            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);

                if (c == '§' && i + 1 < s.length()) {
                    String fmt = s.substring(i, i + 2);
                    this.drawTextWithLegacyInline(fmt, 0, 0, color, false, st);
                    i++;
                    continue;
                }

                char drawChar = c;
                if (r.nextFloat() < prob) drawChar = this.obfuscateCharSameWidth(drawChar);

                float dx = (r.nextFloat() - 0.5F) * 2.0F * amt;
                float dy = (r.nextFloat() - 0.5F) * 2.0F * amt;

                int pulsed = pulseColorRGB(color, pulseSpeed, pulseAmp);

                int adv = this.drawTextWithLegacyInline(String.valueOf(drawChar),
                        (int)(tFx + dx), (int)(y + dy), pulsed, shadow, st);

                tFx += adv;
            }

            return (int)(tFx - x);
        }

        // overlayOnly: assumes s is already plain (no §), no width advance returned
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            char drawChar = c;
            if (r.nextFloat() < prob) drawChar = this.obfuscateCharSameWidth(drawChar);

            float dx = (r.nextFloat() - 0.5F) * 2.0F * amt;
            float dy = (r.nextFloat() - 0.5F) * 2.0F * amt;

            int pulsed = pulseColorRGB(color, pulseSpeed, pulseAmp);

            this.base.drawString(String.valueOf(drawChar),
                    (int)(tFx + dx), (int)(y + dy), applyForcedMul(pulsed), shadow);

            tFx += this.base.getCharWidth(c);
        }

        return 0; // overlay should not claim any width
    }


    private void drawSnowAura(Op op, String s, int x, int y, int baseColor, boolean shadow) {
        if (op == null || !op.snowOn || s == null) return;


// debug marker: if this shows, drawSnowAura is being called
        // this.base.drawString("✶", x - 6, y, 0x00FFFF, shadow);

// don’t gate snow while debugging
// if (!op.snowOn) return;


        float t = timeSeconds();

        int w = this.getStringWidth(s);
        int h = this.base.FONT_HEIGHT;

        float speed  = op.snowSpeed;
        float fall   = op.snowFall;
        float start  = op.snowStart;
        float spread = op.snowSpread;
        float driftA = op.snowDrift;
        float dens   = op.snowDensity;

        // Fewer flakes when density is low (more spaced out)
        // dens ~0.10 very sparse, dens ~1.0 heavy
        int flakes = Math.max(4, (int)(w * (0.12f + 0.55f * dens)));
        flakes = Math.min(flakes, 64);

        // Default glyphs (safe in most fonts)
        char[] glyphs = new char[]{'·','*','°','✶'};

        // Optional: honor glyphs= from SnowCfg if you kept it in op.snow
        if (op.snow != null && op.snow.glyphs != null && op.snow.glyphs.length() > 0) {
            glyphs = op.snow.glyphs.toCharArray();
        }

        for (int j = 0; j < flakes; j++) {
            // stable per-flake hash
            int hsh = (x * 31 + y * 17 + j * 101) ^ (s.length() * 131);
            hsh = (hsh ^ (hsh >>> 13)) * 1274126177;
            hsh ^= (hsh >>> 16);

            float r1 = (hsh & 0x7fffffff) / 2147483647.0f;

            int h2 = hsh * 374761393 + 668265263;
            h2 = (h2 ^ (h2 >>> 13)) * 1274126177;
            h2 ^= (h2 >>> 16);
            float r2 = (h2 & 0x7fffffff) / 2147483647.0f;

            float speedMul = 0.65F + r2 * 0.85F;

            // x around the text with padding
            float fx = (x - spread) + r1 * (w + spread * 2.0F);

            // fall from above, wrap through band around text
            float band = (start + fall + h + 8.0F);
            float u = (t * speed * speedMul + j * 0.19F) % 1.0F;
            float fy = (y - start) + (u * band);

            float drift = (float)Math.sin(t * (0.6F + r1) + j * 1.7F) * driftA;

            // choose base flake color
            int flakeColor = 0xFFFFFF;

            // normalized horizontal position across the snow band (0..1)
            float tx = (w <= 1 ? 0f : (fx - (x - spread)) / (w + spread * 2.0f));
            tx = Math.max(0f, Math.min(1f, tx));

            // Inherit can be per-char if we have nested ops
            if (op.snowInherit) {
                int inherit = (baseColor & 0xFFFFFF);

                // If we have children ops, sample the color at a char index derived from tx.
                // This lets <snow> follow <grad>/<rbw> underneath, without requiring snowgrad=.
                if (op.children != null && !op.children.isEmpty()) {
                    // Use op.plain if available (already stripped), else fallback to cleaned payload
                    String plain = (op.plain != null ? op.plain : cleanPayload(s));
                    int nChars = (plain != null ? plain.length() : s.length());
                    if (nChars > 0) {
                        int idx = (int)Math.floor(tx * nChars);
                        if (idx < 0) idx = 0;
                        if (idx >= nChars) idx = nChars - 1;
                        inherit = colorAtIndexFromOps(op.children, idx, inherit) & 0xFFFFFF;
                    }
                }

                flakeColor = inherit;
            }

// solid override wins (if provided)
            if (op.snowColor != -1) flakeColor = (op.snowColor & 0xFFFFFF);

// gradient override wins over solid (if present)

            if (op.snowGrad != null && op.snowGrad.length > 1) {
                float scroll = 0f;
                if (op.snow != null && op.snow.gradScroll) {
                    float sp = (op.snow.gradScrollSpeed > 0 ? op.snow.gradScrollSpeed : 0.25f);
                    scroll = t * sp;
                } else {
                    scroll = t * 0.25f * Math.max(0.1f, speed);
                }
                flakeColor = sampleGrad(op.snowGrad, tx + scroll);
            }
            // rainbow flakes (only when no explicit snowGrad is present)
            if ((op.snowGrad == null || op.snowGrad.length < 2) && op.snow != null && op.snow.useRainbow) {
                float sp = (op.snow.rbSpeed > 0f ? op.snow.rbSpeed : 1.2f);

                // hue in DEGREES (0..360) because your hsvToRgb expects degrees
                // tx spreads rainbow across the message; time animates it
                float hue = (tx * 360.0f + t * 90.0f * sp) % 360.0f;

                flakeColor = hsvToRgb(hue, 1.0f, 1.0f) & 0xFFFFFF;
            }


            // NEW: optional mix toward white so colored flakes still read as 'snow'
            if (op.snowMix > 0.0F) {
                flakeColor = lerpRGB(flakeColor, 0xFFFFFF, clamp01(op.snowMix));
            }

            // ── NEW: flake-only animation styles ─────────────────────

            // flicker: skip some frames for that "sparkly" snow
            if (op.snowFlicker) {
                float ft = t + j * 0.19f + r1 * 0.37f;
                if (((int)(ft * 10f) & 1) == 0) continue;
            }

// wave: extra drift wobble (does NOT affect text)
            if (op.snowWave) {
                drift += (float)Math.sin(t * (1.8f + r2) + j * 0.9f) * 1.4f;
            }

// pulse: brighten/dim flakes
            if (op.snowPulse) {
                float ft = t + j * 0.11f + r2 * 0.73f;
                float p = 0.70f + 0.30f * (float)Math.sin(ft * 3.2f);
                flakeColor = mulRGB(flakeColor, p);
            }


            // draw only near the text region
            if (fy >= (y - start - 4) && fy <= (y + h + 10)) {
                int gi = (hsh & 0x7fffffff) % glyphs.length;
                char g = glyphs[gi];
                this.base.drawString(String.valueOf(g), (int)(fx + drift), (int)fy, applyForcedMul(flakeColor), shadow);
            }
        }
    }

    private int drawTextWithLegacyInline(String s, int x, int y, int color, boolean shadow, LegacyState legacy) {
        int startX = x;
        if (s != null && !s.isEmpty()) {
            int i = 0;
            int n = s.length();

            while (i < n) {
                char c = s.charAt(i);

                // Handle § formatting
                if (c == '§' && i + 1 < n) {
                    char k = s.charAt(i + 1);
                    char kl = Character.toLowerCase(k);

                    int col = vanillaColorFor(k);
                    if (col >= 0) {
                        legacy.reset();
                        legacy.colorRgb = col;
                        i += 2;
                        continue;
                    }

                    switch (kl) {
                        case 'k':
                            legacy.obfuscated = true;
                            legacy.obfOnce = true;
                            i += 2;
                            i = skipLegacyCodes(s, i);
                            continue;

                        case 'l':
                            legacy.bold = true;
                            i += 2;
                            continue;

                        case 'm':
                            legacy.strikethrough = true;
                            i += 2;
                            continue;

                        case 'n':
                            legacy.underline = true;
                            i += 2;
                            continue;

                        case 'o':
                            legacy.italic = true;
                            i += 2;
                            continue;

                        case 'r':
                            legacy.reset();
                            i += 2;
                            continue;

                        default:
                            break;
                    }
                }

                String prefix = legacyPrefix(legacy);
                int drawRgb = (legacy.colorRgb != null ? legacy.colorRgb : color);

                char out = legacy.obfuscated
                        ? this.obfuscateCharSameWidth(c)
                        : c;

                // DRAW GLYPH (MCP)
                this.base.drawString(prefix + out, x, y, applyForcedMul(drawRgb), shadow);

                // ADVANCE WIDTH (MCP)
                x += this.base.getCharWidth(out);

                if (legacy.obfOnce) {
                    legacy.obfuscated = false;
                    legacy.obfOnce = false;
                }

                i++;
            }

            return x - startX;
        }

        return 0;
    }

    private static int mulRGB(int rgb, float mul) {
        if (mul <= 0f) return 0x000000;
        if (mul >= 1f) return (rgb & 0xFFFFFF);

        int r = (rgb >> 16) & 255;
        int g = (rgb >> 8) & 255;
        int b = (rgb) & 255;

        r = (int)(r * mul);
        g = (int)(g * mul);
        b = (int)(b * mul);

        if (r < 0) r = 0; else if (r > 255) r = 255;
        if (g < 0) g = 0; else if (g > 255) g = 255;
        if (b < 0) b = 0; else if (b > 255) b = 255;

        return (r << 16) | (g << 8) | b;
    }

    private static String stripHexAndReset(String s) {
        if (s != null && !s.isEmpty()) {
            s = s.replaceAll("(?i)§#[0-9a-f]{6}[lmonkr]*", "");
            s = s.replaceAll("(?i)§#[0-9a-f]{3}[lmonkr]*", "");
            return s;
        } else {
            return s;
        }
    }

    private static String postNormalizeCleanup(String s) {
        if (s != null && !s.isEmpty()) {
            s = s.replaceAll("(?is)<\\s*#?[0-9a-f]{3,6}\\s*>\\s*</\\s*#\\s*>", "");
            s = s.replaceAll("(?is)<\\s*(grad|rainbow|pulse)\\b[^>]*>\\s*</\\s*\\1\\s*>", "");
            return s;
        } else {
            return s;
        }
    }

    private static int indexOfIgnoreSpace(String s, int from, String needle) {
        String collapsed = s.substring(from).replaceAll("\\s+", " ");
        String n = needle.replaceAll("\\s+", " ");
        int pos = collapsed.toLowerCase().indexOf(n.toLowerCase());
        if (pos < 0) {
            return -1;
        } else {
            int i = from;

            for(int seen = 0; i < s.length() && seen < pos; ++i) {
                char ch = s.charAt(i);
                if (!Character.isWhitespace(ch)) {
                    ++seen;
                } else {
                    while(i < s.length() && Character.isWhitespace(s.charAt(i))) {
                        ++i;
                    }

                    ++seen;
                    --i;
                }
            }

            return i;
        }
    }

    private static int skipPastCloser(String s, int start) {
        int i;
        for(i = start; i < s.length() && s.charAt(i) != '>'; ++i) {
        }

        if (i < s.length()) {
            ++i;
        }

        return i;
    }

    private static String normalizeControlsToTags(String text) {
        if (text != null && !text.isEmpty()) {
            StringBuilder out = new StringBuilder(text.length() + 16);
            boolean open = false;
            int i = 0;
            int n = text.length();

            while(i < n) {
                char c = text.charAt(i);
                if (c == 167 && i + 1 < n) {
                    char n1 = text.charAt(i + 1);
                    if (n1 == '#' && i + 3 < n) {
                        if (i + 7 < n) {
                            String hex6 = text.substring(i + 2, i + 8);
                            if (hex6.matches("(?i)[0-9a-f]{6}")) {
                                if (open) {
                                    out.append("</#>");
                                }

                                out.append("<#").append(hex6).append(">");
                                open = true;
                                i += 8;
                                continue;
                            }
                        }

                        String hex3 = text.substring(i + 2, Math.min(i + 5, n));
                        if (hex3.matches("(?i)[0-9a-f]{3}")) {
                            String h = hex3.toUpperCase();
                            String expanded = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
                            if (open) {
                                out.append("</#>");
                            }

                            out.append("<#").append(expanded).append(">");
                            open = true;
                            i += 5;
                            continue;
                        }
                    }

                    if (n1 == 'r') {
                        if (open) {
                            out.append("</#>");
                            open = false;
                        }

                        out.append("§r");
                        i += 2;
                        continue;
                    }

                    if ("0123456789abcdeflmonkr".indexOf(Character.toLowerCase(n1)) >= 0) {
                        out.append('§').append(n1);
                        i += 2;
                        continue;
                    }
                }

                out.append(c);
                ++i;
            }

            if (open) {
                out.append("</#>");
            }

            return out.toString();
        } else {
            return text;
        }
    }

    public static String formatInline(String s) {
        if (s == null) {
            return "";
        } else {
            s = s.replace('&', '§');
            String out = s.replaceAll("(?i)§#([0-9a-f]{6})([lmonkr]*)", "<#$1>$2</#>");
            out = out.replaceAll("(?i)§#([0-9a-f]{3})([lmonkr]*)", "<#$1>$2</#>");
            return out;
        }
    }

    private static int lerpRGB(int a, int b, float t) {
        int ar = a >> 16 & 255;
        int ag = a >> 8 & 255;
        int ab = a & 255;
        int br = b >> 16 & 255;
        int bg = b >> 8 & 255;
        int bb = b & 255;
        int r = (int)((float)ar + (float)(br - ar) * t);
        int g = (int)((float)ag + (float)(bg - ag) * t);
        int bl = (int)((float)ab + (float)(bb - ab) * t);
        return r << 16 | g << 8 | bl;
    }

    // pulls first #RRGGBB / #RGB from tag attrs into op.rgb
    private static void parseMotionColor(String attrs, Op out) {
        if (attrs == null) return;
        List<String> hexes = parseHexTokens(attrs);
        if (!hexes.isEmpty()) {
            out.rgb = parseHexRGB(hexes.get(0));
        }
    }

    private static int parseHexRGB(String hex) {
        hex = hex.trim();
        if (hex.length() == 3) {
            char r = hex.charAt(0);
            char g = hex.charAt(1);
            char b = hex.charAt(2);
            hex = "" + r + r + g + g + b + b;
        }

        return Integer.parseInt(hex, 16) & 16777215;
    }

    private static List<String> parseHexTokens(String attrs) {
        List<String> list = new ArrayList();
        if (attrs == null) {
            return list;
        } else {
            Matcher m = Pattern.compile("(?i)(?:^|\\s)#([0-9a-f]{3}|[0-9a-f]{6})(?=$|\\s|[,;])").matcher(attrs);

            while(m.find()) {
                list.add(m.group(1));
            }

            return list;
        }
    }

    private static float parseFloatSafe(String s, float d) {
        try {
            return Float.parseFloat(s);
        } catch (Throwable var3) {
            return d;
        }
    }

    private static void parsePulseArgs(String attrs, Op out) {
        List<String> hexes = parseHexTokens(attrs);
        if (!hexes.isEmpty()) {
            out.solid = parseHexRGB((String)hexes.get(0));
        }

        Matcher kv = Pattern.compile("(?i)\\b(speed|amp)\\s*=\\s*([\\d.]+)").matcher(attrs == null ? "" : attrs);

        while(kv.find()) {
            String k = kv.group(1).toLowerCase();
            String v = kv.group(2);
            if ("speed".equals(k)) {
                out.speed = Math.max(0.0F, parseFloatSafe(v, 0.0F));
            } else if ("amp".equals(k)) {
                out.amp = clamp01(parseFloatSafe(v, 0.25F));
            }
        }

    }
    // Generic motion parser for tags like <wave>, <shake>, <jitter>, <wobble>
    // understands: amp=, amplitude=, intensity=, speed=
// Generic motion parser for tags like <wave>, <shake>, <jitter>, <wobble>, <zoom>, <rain>, <scroll>
// understands: #hex, amp= / amplitude= / intensity=, speed=
    private static void parseMotionArgs(String attrs, Op out, float defaultAmp, float defaultSpeed) {
        out.amp = defaultAmp;
        out.speed = defaultSpeed;
        out.rgb = -1; // no explicit color by default

        if (attrs == null || attrs.trim().isEmpty()) {
            return;
        }

        // NEW: first hex in the args becomes the text color for this motion op
        parseMotionColor(attrs, out);

        Matcher kv = Pattern.compile("(?i)\\b(amp|amplitude|intensity|speed)\\s*=\\s*([\\d.]+)").matcher(attrs);
        while (kv.find()) {
            String k = kv.group(1).toLowerCase();
            String v = kv.group(2);
            float f = parseFloatSafe(v, 0.0F);

            if ("speed".equals(k)) {
                out.speed = f;
            } else {
                // amp, amplitude, intensity → amplitude
                out.amp = f;
            }
        }
    }

    private static void parseOutlineArgs(String attrs, Op out) {
        if (attrs == null) return;
        // first hex becomes outline color
        List<String> hexes = parseHexTokens(attrs);
        if (!hexes.isEmpty()) {
            out.outlineColor = parseHexRGB(hexes.get(0));
        }
    }

    private static void parseShadowArgs(String attrs, Op out) {
        // default
        out.shadowColor = 0x3F3F3F;

        if (attrs == null) return;

        List<String> hexes = parseHexTokens(attrs);
        if (!hexes.isEmpty()) {
            out.shadowColor = parseHexRGB(hexes.get(0));
        }
    }

    private static void parseSparkleArgs(String attrs, Op out) {
        // default sparkle
        out.sparkleIntensity = 1.0F;
        out.speed = 4.0F;
        out.rgb = -1;

        if (attrs == null || attrs.trim().isEmpty()) {
            return;
        }

        // NEW: allow #hex on <sparkle>
        parseMotionColor(attrs, out);
        // Star color override: star=rbw | star=#RRGGBB | star=#A,#B,#C
        // Aliases: stars=, spark=, glyph= (legacy)
        String starSpec = getStr(attrs, "star", null);
        if (starSpec == null) starSpec = getStr(attrs, "stars", null);
        if (starSpec == null) starSpec = getStr(attrs, "spark", null);
        if (starSpec == null) starSpec = getStr(attrs, "glyph", null);

        if (starSpec != null && !starSpec.trim().isEmpty()) {
            String s0 = starSpec.trim();
            if ("rbw".equalsIgnoreCase(s0) || "rainbow".equalsIgnoreCase(s0)) {
                out.sparkleStarRbw = true;
                out.sparkleStarGrad = null;
                out.sparkleStarColor = -1;
            } else if (s0.indexOf(',') >= 0) {
                int[] g = parseColorListValue(s0);
                if (g == null) g = parseSnowGradValue(s0); // allow space-delimited too
                if (g != null && g.length >= 2) {
                    out.sparkleStarGrad = g;
                    out.sparkleStarRbw = false;
                    out.sparkleStarColor = -1;
                }
            } else {
                int c = parseHexColor(s0, -1);
                if (c != -1) {
                    out.sparkleStarColor = c & 0xFFFFFF;
                    out.sparkleStarGrad = null;
                    out.sparkleStarRbw = false;
                }
            }
        }

        // Optional tuning
        out.sparkleStarSpeed  = getFloat(attrs, "starspeed", out.sparkleStarSpeed);
        out.sparkleStarCycles = getFloat(attrs, "starcycles", out.sparkleStarCycles);
        out.sparkleStarSat    = clamp01(getFloat(attrs, "starsat", out.sparkleStarSat));
        out.sparkleStarVal    = clamp01(getFloat(attrs, "starval", out.sparkleStarVal));


        Matcher kv = Pattern.compile("(?i)\\b(density|intensity|speed)\\s*=\\s*([\\d.]+)").matcher(attrs);
        while (kv.find()) {
            String k = kv.group(1).toLowerCase();
            String v = kv.group(2);
            float f = parseFloatSafe(v, 0.0F);

            if ("speed".equals(k)) {
                out.speed = f;
            } else {
                // density/intensity → intensity
                out.sparkleIntensity = f;
            }
        }
    }

    private static void parseFlickerArgs(String attrs, Op out) {
        // default flicker
        out.flickerSpeed = 6.0F;
        out.rgb = -1;

        if (attrs == null || attrs.trim().isEmpty()) {
            return;
        }

        // NEW: allow #hex on <flicker>
        parseMotionColor(attrs, out);

        Matcher kv = Pattern.compile("(?i)\\b(speed|rate)\\s*=\\s*([\\d.]+)").matcher(attrs);
        while (kv.find()) {
            String v = kv.group(2);
            out.flickerSpeed = Math.max(0.01F, parseFloatSafe(v, 6.0F));
        }
    }

    private static void parseGlitchArgs(String attrs, Op out) {
        // default glitch strength
        out.glitchAmount = 1.0F;
        out.rgb = -1;

        if (attrs == null || attrs.trim().isEmpty()) {
            return;
        }

        // NEW: allow #hex on <glitch>
        parseMotionColor(attrs, out);

        Matcher kv = Pattern.compile("(?i)\\b(intensity|amount|amp)\\s*=\\s*([\\d.]+)").matcher(attrs);
        while (kv.find()) {
            String v = kv.group(2);
            out.glitchAmount = Math.max(0.0F, parseFloatSafe(v, 1.0F));
        }
    }


    private static void parseShootingStarArgs(String attrs, Op out) {
        // defaults already in Op
        out.rgb = -1;

        if (attrs == null || attrs.trim().isEmpty()) {
            return;
        }

        // allow #hex on <shootingstar>
        parseMotionColor(attrs, out);

        out.ssCycleSec   = Math.max(0.6F, getFloat(attrs, "cycle", out.ssCycleSec));
        out.ssGlowFrac   = clamp01(getFloat(attrs, "glow", out.ssGlowFrac));
        out.ssFlightFrac = clamp01(getFloat(attrs, "flight", out.ssFlightFrac));
        out.ssArc        = getFloat(attrs, "arc", out.ssArc);
        out.ssDist       = getFloat(attrs, "dist", out.ssDist);
        out.ssChance     = clamp01(getFloat(attrs, "chance", out.ssChance));

        out.ssExplodeFrac = clamp01(getFloat(attrs, "explode", out.ssExplodeFrac));
        out.ssExplodeDist = getFloat(attrs, "boom", out.ssExplodeDist);
        out.ssExplodeGlow = clamp01(getFloat(attrs, "boomglow", out.ssExplodeGlow));

        // aliases
        out.ssExplodeFrac = clamp01(getFloat(attrs, "blast", out.ssExplodeFrac));
        out.ssExplodeDist = getFloat(attrs, "blastdist", out.ssExplodeDist);

        // convenience aliases
        out.ssCycleSec   = Math.max(0.6F, getFloat(attrs, "period", out.ssCycleSec));
        out.ssArc        = getFloat(attrs, "height", out.ssArc);
        out.ssDist       = getFloat(attrs, "len", out.ssDist);
    }

    private static String stylesFromAttrs(String attrs) {
        if (attrs == null) {
            return "";
        } else {
            Matcher m = Pattern.compile("(?i)\\bstyles\\s*=\\s*([lmonkr]+)\\b").matcher(attrs);
            if (m.find()) {
                return stylesToLegacy(m.group(1));
            } else {
                m = Pattern.compile("(?i)\\b([lmonkr]+)\\b").matcher(attrs);

                while(m.find()) {
                    String token = m.group(1);
                    if (token != null && !token.isEmpty()) {
                        return stylesToLegacy(token);
                    }
                }

                return "";
            }
        }
    }

    private static void parseGradAnimArgs(String attrs, Op out) {
        if (attrs != null) {
            // main scroll speed (same as before)
            Matcher kv = Pattern.compile("(?i)\\b(scroll)\\s*=\\s*([\\d.]+)").matcher(attrs);
            if (kv.find()) {
                out.scroll = true;
                out.speed = Math.max(0.0F, parseFloatSafe(kv.group(2), 0.0F));
            }

            // optional: solid color used only for the scrolling overlay
            // e.g. scrollrgb=#FF2BA6
            Matcher cSolid = Pattern.compile("(?i)\\bscrollrgb\\s*=\\s*#([0-9a-f]{3}|[0-9a-f]{6})").matcher(attrs);
            if (cSolid.find()) {
                out.scrollRgb = parseHexRGB(cSolid.group(1));
            }

            // optional: second gradient just for the scroll overlay
            // e.g. scrollgrad=#FF2BA6,#FF66CF,#FF2BA6
            Matcher cGrad = Pattern.compile("(?i)\\bscrollgrad\\s*=\\s*([^\\s>]+)").matcher(attrs);
            if (cGrad.find()) {
                String list = cGrad.group(1);
                String[] parts = list.split("[,;]+");
                java.util.ArrayList<Integer> cols = new java.util.ArrayList<Integer>();
                for (String p : parts) {
                    if (p == null) continue;
                    p = p.trim();
                    if (p.isEmpty()) continue;
                    if (p.charAt(0) == '#') p = p.substring(1);
                    if (p.matches("(?i)[0-9a-f]{3}|[0-9a-f]{6}")) {
                        cols.add(parseHexRGB(p));
                    }
                }
                if (cols.size() >= 2) {
                    out.scrollStops = new int[cols.size()];
                    for (int i = 0; i < cols.size(); i++) {
                        out.scrollStops[i] = cols.get(i);
                    }
                } else if (cols.size() == 1) {
                    // degrade to solid
                    out.scrollRgb = cols.get(0);
                }
            }
            // optional: wave motion for gradient text
            //   waveamp=2.0   wavespeed=6.0
            Matcher wAmp = Pattern.compile("(?i)\\bwaveamp\\s*=\\s*([\\d.]+)").matcher(attrs);
            if (wAmp.find()) {
                out.gradWave = true;
                out.gradWaveAmp = parseFloatSafe(wAmp.group(1), out.gradWaveAmp);
            }

            Matcher wSpeed = Pattern.compile("(?i)\\bwavespeed\\s*=\\s*([\\d.]+)").matcher(attrs);
            if (wSpeed.find()) {
                out.gradWave = true;
                out.gradWaveSpeed = parseFloatSafe(wSpeed.group(1), out.gradWaveSpeed);
            }

            // optional: rain motion for gradient text
            //   rainamp=4.0   rainspeed=8.0
            Matcher rAmp = Pattern.compile("(?i)\\brainamp\\s*=\\s*([\\d.]+)").matcher(attrs);
            if (rAmp.find()) {
                out.gradRain = true;
                out.gradRainAmp = parseFloatSafe(rAmp.group(1), out.gradRainAmp);
            }

            Matcher rSpeed = Pattern.compile("(?i)\\brainspeed\\s*=\\s*([\\d.]+)").matcher(attrs);
            if (rSpeed.find()) {
                out.gradRain = true;
                out.gradRainSpeed = parseFloatSafe(rSpeed.group(1), out.gradRainSpeed);
            }

            // if rain is specified, let it win over wave
            if (out.gradRain) {
                out.gradWave = false;
            }

            // optional: how strongly the overlay is blended (0–1)
            // e.g. scrollmix=0.6
            Matcher cMix = Pattern.compile("(?i)\\bscrollmix\\s*=\\s*([\\d.]+)").matcher(attrs);
            if (cMix.find()) {
                out.scrollMix = clamp01(parseFloatSafe(cMix.group(1), out.scrollMix));
            }

            // existing pulse inline support
            parsePulseParamsInline(attrs, out);
        }
    }
    private static boolean hasRealNestedStyles(List<Op> kids) {
        if (kids == null || kids.isEmpty()) return false;

        for (Op c : kids) {
            if (c == null) continue;

            // Allow plain text + simple hex pushes without forcing nested rendering
            if (c.kind == Kind.TEXT || c.kind == Kind.PUSH_HEX || c.kind == Kind.POP_HEX) {
                continue;
            }
            return true; // any other op kind means “real nested”
        }
        return false;
    }

    // True when the ops contain ONLY text + color sources (so we can safely do per-letter effects
    // without “animating nested animations”).
    private static boolean hasOnlyColorOps(List<Op> kids) {
        if (kids == null || kids.isEmpty()) return true;

        for (Op c : kids) {
            if (c == null) continue;

            if (c.kind == Kind.TEXT
                    || c.kind == Kind.PUSH_HEX
                    || c.kind == Kind.POP_HEX
                    || c.kind == Kind.GRADIENT_MULTI
                    || c.kind == Kind.RAINBOW_TEXT) {
                continue;
            }
            return false;
        }
        return true;
    }


    private static void parseRainbowSpeed(String attrs, Op out) {
        if (attrs != null) {
            // allow both speed= and scroll=
            Matcher kv = Pattern.compile("(?i)\\b(speed|scroll)\\s*=\\s*([\\d.]+)").matcher(attrs);
            while (kv.find()) {
                String v = kv.group(2);
                out.speed = Math.max(0.0F, parseFloatSafe(v, 0.0F));
            }
        }
    }

    private static void parseRainbowArgs(String attrs, Op out) {
        if (attrs != null && !attrs.trim().isEmpty()) {
            Matcher lone = Pattern.compile("\\b(\\d+)\\b").matcher(attrs);
            if (lone.find()) {
                try {
                    out.cycles = Math.max(1, Integer.parseInt(lone.group(1)));
                } catch (Throwable var8) {
                }
            }

            Matcher kv = Pattern.compile("(?i)\\b(cycles|sat|val|phase)\\s*=\\s*([\\d.]+)").matcher(attrs);

            while(kv.find()) {
                String k = kv.group(1).toLowerCase();
                String v = kv.group(2);

                try {
                    if ("cycles".equals(k)) {
                        out.cycles = Math.max(1, (int)Float.parseFloat(v));
                    } else if ("sat".equals(k)) {
                        out.sat = clamp01(Float.parseFloat(v));
                    } else if ("val".equals(k)) {
                        out.val = clamp01(Float.parseFloat(v));
                    } else if ("phase".equals(k)) {
                        float p = Float.parseFloat(v);
                        out.phase = p - (float)Math.floor((double)p);
                    }
                } catch (Throwable var7) {
                }
            }
            // Wave / rain motion support for rainbow (shared with gradients)
            Matcher wAmp = Pattern.compile("(?i)\\bwaveamp\\s*=\\s*([\\d.]+)").matcher(attrs);
            if (wAmp.find()) {
                out.gradWave = true;
                out.gradWaveAmp = parseFloatSafe(wAmp.group(1), out.gradWaveAmp);
            }

            Matcher wSpeed = Pattern.compile("(?i)\\bwavespeed\\s*=\\s*([\\d.]+)").matcher(attrs);
            if (wSpeed.find()) {
                out.gradWave = true;
                out.gradWaveSpeed = parseFloatSafe(wSpeed.group(1), out.gradWaveSpeed);
            }

            Matcher rAmp = Pattern.compile("(?i)\\brainamp\\s*=\\s*([\\d.]+)").matcher(attrs);
            if (rAmp.find()) {
                out.gradRain = true;
                out.gradRainAmp = parseFloatSafe(rAmp.group(1), out.gradRainAmp);
            }

            Matcher rSpeed = Pattern.compile("(?i)\\brainspeed\\s*=\\s*([\\d.]+)").matcher(attrs);
            if (rSpeed.find()) {
                out.gradRain = true;
                out.gradRainSpeed = parseFloatSafe(rSpeed.group(1), out.gradRainSpeed);
            }

// if rain is specified, let it win over wave
            if (out.gradRain) {
                out.gradWave = false;
            }


            parsePulseParamsInline(attrs, out);
        } else {
            parsePulseParamsInline(attrs, out);
        }
    }

    private static float clamp01(float f) {
        return f < 0.0F ? 0.0F : (f > 1.0F ? 1.0F : f);
    }

    private static void parsePulseParamsInline(String attrs, Op out) {
        if (attrs != null) {
            Matcher kv = Pattern.compile("(?i)\\b(pulse|pulsespeed|amp)\\s*=\\s*([\\w.]+)").matcher(attrs);

            while(kv.find()) {
                String k = kv.group(1).toLowerCase();
                String v = kv.group(2);
                if ("pulse".equals(k)) {
                    out.pulseOn = !"0".equals(v) && !"false".equalsIgnoreCase(v);
                } else if ("pulsespeed".equals(k)) {
                    out.pulseSpeed = Math.max(0.01F, parseFloatSafe(v, 1.0F));
                } else if ("amp".equals(k)) {
                    out.pulseAmp = clamp01(parseFloatSafe(v, 0.25F));
                }
            }

        }
    }
// ─────────────────────────────────────────────
// Shorthand tag expander
// ─────────────────────────────────────────────

    private static String expandShortTags(String input) {
        if (input == null) return null;
        // Quick check – avoid work if no tags at all
        if (input.indexOf('<') < 0 && input.indexOf('«') < 0) return input;

        String s = input;
        StringBuffer sb;
        Matcher m;

        // <g:#FF0000:#00FF00[:extra]>
        m = TAG_GRAD_SHORT.matcher(s);
        sb = new StringBuffer();
        while (m.find()) {
            String body = m.group(1); // everything after "g:"
            String rep  = buildGradFromShort(body);
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        s = sb.toString();

        // <pl:#FF00FF[:extra]>
        m = TAG_PULSE_SHORT.matcher(s);
        sb = new StringBuffer();
        while (m.find()) {
            String body = m.group(1);
            String rep  = buildPulseFromShort(body);
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        s = sb.toString();

        // <wave:a:5:2>
        m = TAG_WAVE_SHORT.matcher(s);
        sb = new StringBuffer();
        while (m.find()) {
            String type  = m.group(1);
            String speed = m.group(2);
            String amp   = m.group(3);
            String rep   = buildWaveFromShort(type, speed, amp);
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        s = sb.toString();

        // <zoom:a:1.5:20>
        m = TAG_ZOOM_SHORT.matcher(s);
        sb = new StringBuffer();
        while (m.find()) {
            String type  = m.group(1);
            String scale = m.group(2);
            String cycle = m.group(3);
            String rep   = buildZoomFromShort(type, scale, cycle);
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        s = sb.toString();

        return s;
    }

    // <g:#FF0000:#00FF00:...>  →  <grad #FF0000 #00FF00 ...>
    private static String buildGradFromShort(String body) {
        if (body == null) body = "";
        body = body.trim();
        if (body.isEmpty()) return "<grad>";

        String[] parts = body.split(":");
        StringBuilder colors = new StringBuilder();
        StringBuilder extras = new StringBuilder();

        for (String rawPart : parts) {
            if (rawPart == null) continue;
            String p = rawPart.trim();
            if (p.isEmpty()) continue;

            if (p.matches("(?i)#?[0-9a-f]{3,6}")) {
                if (p.charAt(0) != '#') p = "#" + p;
                colors.append(' ').append(p);
            } else {
                if (extras.length() == 0) extras.append(' ');
                else extras.append(' ');
                extras.append(p);
            }
        }

        if (colors.length() == 0) {
            // No valid color tokens, keep whatever extra attrs we had
            return "<grad" + extras.toString() + ">";
        }
        return "<grad" + colors.toString() + extras.toString() + ">";
    }

    // <pl:#FF00FF:...>  →  <pulse #FF00FF ...>
    private static String buildPulseFromShort(String body) {
        if (body == null) body = "";
        body = body.trim();
        if (body.isEmpty()) return "<pulse>";

        String[] parts = body.split(":");
        String color = null;
        StringBuilder extras = new StringBuilder();

        for (String rawPart : parts) {
            if (rawPart == null) continue;
            String p = rawPart.trim();
            if (p.isEmpty()) continue;

            if (color == null && p.matches("(?i)#?[0-9a-f]{3,6}")) {
                if (p.charAt(0) != '#') p = "#" + p;
                color = p;
            } else {
                if (extras.length() == 0) extras.append(' ');
                else extras.append(' ');
                extras.append(p);
            }
        }

        if (color == null) {
            return "<pulse" + extras.toString() + ">";
        }
        return "<pulse " + color + extras.toString() + ">";
    }

    // <wave:a:5:2>  →  <wave type=a amp=2 speed=5>
    private static String buildWaveFromShort(String type, String speed, String amp) {
        String t = (type  == null || type.isEmpty())  ? "a"   : type;
        String s = (speed == null || speed.isEmpty()) ? "5"   : speed;
        String a = (amp   == null || amp.isEmpty())   ? "2"   : amp;
        return "<wave type=" + t + " amp=" + a + " speed=" + s + ">";
    }

    private int applyForcedMul(int rgb) {
        rgb &= 0xFFFFFF;
        if (!FORCE_COLOR_MUL) return rgb;

        float mul = FORCED_COLOR_MUL;
        if (mul <= 0.0f) return rgb;

        int r = (rgb >> 16) & 255;
        int g = (rgb >> 8) & 255;
        int b = rgb & 255;

        r = (int)(r * mul); if (r > 255) r = 255; else if (r < 0) r = 0;
        g = (int)(g * mul); if (g > 255) g = 255; else if (g < 0) g = 0;
        b = (int)(b * mul); if (b > 255) b = 255; else if (b < 0) b = 0;

        return (r << 16) | (g << 8) | b;
    }

    // <zoom:a:1.5:20>  →  <zoom type=a amp=1.5 speed=20 scale=1.5 cycle=20>
    private static String buildZoomFromShort(String type, String scale, String cycle) {
        String t  = (type  == null || type.isEmpty())  ? "a"   : type;
        String sc = (scale == null || scale.isEmpty()) ? "1.0" : scale;
        String cy = (cycle == null || cycle.isEmpty()) ? "20"  : cycle;

        // We set both amp/speed *and* scale/cycle so whatever parseMotionArgs looks for will be satisfied.
        return "<zoom type=" + t
                + " amp=" + sc
                + " speed=" + cy
                + " scale=" + sc
                + " cycle=" + cy
                + ">";
    }

    private ArrayList<Op> parseChildren(String inner) {
        if (inner == null || inner.isEmpty()) return new ArrayList<Op>();
        return new ArrayList<Op>(parseToOps(inner));
    }

    private int drawOps(List<Op> ops, int x, int y, int baseColor, boolean shadow) {
        if (ops == null || ops.isEmpty()) return 0;

        int cursorX = x;
        int maxRight = x;

        boolean hexActive = false;
        int activeHexRGB = baseColor;

        Deque<Integer> colorStack = new ArrayDeque<Integer>();
        colorStack.push(baseColor);

        LegacyState legacy = new LegacyState();

        for (Op op : ops) {
            if (op == null) continue;
            switch (op.kind) {

                case TEXT:
                    if (op.payload != null && op.payload.length() > 0) {
                        String seg = dropOurTagsOnlyPrefix(op.payload);
                        int adv = this.drawTextWithLegacyInline(
                                seg,
                                cursorX,
                                y,
                                hexActive ? activeHexRGB : baseColor,
                                shadow,
                                legacy
                        );
                        cursorX += adv;
                        maxRight = Math.max(maxRight, cursorX);
                    }
                    break;

                case PUSH_HEX:
                    colorStack.push(activeHexRGB);
                    activeHexRGB = op.rgb;
                    hexActive = true;
                    break;

                case POP_HEX:
                    activeHexRGB = colorStack.isEmpty() ? baseColor : colorStack.pop();
                    hexActive = (activeHexRGB != baseColor);
                    break;

                case GRADIENT_MULTI: {
                    Integer saved = legacy.colorRgb;
                    String fmt = legacyPrefixNoObf(legacy)
                            + (op.legacyFromTag == null ? "" : op.legacyFromTag);

                    int col = (op.rgb >= 0 ? op.rgb : (hexActive ? activeHexRGB : baseColor));

                    // ✅ Only take nested path if there are real nested styles inside
                    if (hasRealNestedStyles(op.children)) {
                        int adv = drawOps(op.children, cursorX, y, col, shadow);
                        cursorX += adv;
                        maxRight = Math.max(maxRight, cursorX);
                        legacy.colorRgb = saved;
                        break;
                    }

                    String payload = (op.payload == null ? "" : op.payload);
                    int adv = this.drawGradientMulti(
                            payload,
                            cursorX,
                            y,
                            op.stops,
                            shadow,
                            op.scroll,
                            op.speed,
                            op.pulseOn,
                            op.pulseAmp,
                            op.pulseSpeed,
                            op.scrollStops,
                            op.scrollRgb,
                            op.scrollMix,
                            op.gradWave,
                            op.gradWaveAmp,
                            op.gradWaveSpeed,
                            op.gradRain,
                            op.gradRainAmp,
                            op.gradRainSpeed,
                            fmt
                    );

                    legacy.colorRgb = saved;
                    cursorX += adv;
                    maxRight = Math.max(maxRight, cursorX);
                    break;
                }

                case RAINBOW_TEXT: {
                    Integer saved = legacy.colorRgb;
                    String fmt = legacyPrefixNoObf(legacy)
                            + (op.legacyFromTag == null ? "" : op.legacyFromTag);

                    int col = (op.rgb >= 0 ? op.rgb : (hexActive ? activeHexRGB : baseColor));

                    // ✅ Only take nested path if there are real nested styles inside
                    if (hasRealNestedStyles(op.children)) {
                        int adv = drawOps(op.children, cursorX, y, col, shadow);
                        cursorX += adv;
                        maxRight = Math.max(maxRight, cursorX);
                        legacy.colorRgb = saved;
                        break;
                    }

                    String payload = (op.payload == null ? "" : op.payload);
                    int adv = this.drawRainbowAndReturnAdvanceCustom(
                            payload,
                            cursorX,
                            y,
                            shadow,
                            op.cycles,
                            op.sat,
                            op.val,
                            op.phase,
                            op.speed,
                            op.pulseOn,
                            op.pulseAmp,
                            op.pulseSpeed,
                            op.gradWave,
                            op.gradWaveAmp,
                            op.gradWaveSpeed,
                            op.gradRain,
                            op.gradRainAmp,
                            op.gradRainSpeed,
                            fmt
                    );

                    legacy.colorRgb = saved;
                    cursorX += adv;
                    maxRight = Math.max(maxRight, cursorX);
                    break;
                }

                case PULSE_HEX: {
                    Integer saved = legacy.colorRgb;

                    String fmt = legacyPrefixNoObf(legacy)
                            + (op.legacyFromTag == null ? "" : op.legacyFromTag);

                    // ✅ pulse color comes from solid
                    int col = (op.solid >= 0 ? op.solid : (hexActive ? activeHexRGB : baseColor));

                    float sp  = (op.speed > 0.0F ? op.speed : 1.0F);
                    float amp = (op.amp   > 0.0F ? op.amp   : 0.25F);

                    String payload = (op.payload == null ? "" : op.payload);
                    boolean hasNestedTags = (payload.indexOf('<') >= 0 || payload.indexOf('«') >= 0);

                    // ✅ Only do nested render if there are actually nested tags
                    if (hasNestedTags && op.children != null && !op.children.isEmpty()) {

                        float t = timeSeconds();
                        float mul = 1.0F + (amp * (float)Math.sin(t * sp * ((float)Math.PI * 2F)));

                        if (mul < 0.15F) mul = 0.15F;
                        if (mul > 2.50F) mul = 2.50F;

                        boolean prevMul = FORCE_COLOR_MUL;
                        float   prevMulV = FORCED_COLOR_MUL;

                        FORCE_COLOR_MUL = true;
                        FORCED_COLOR_MUL = (prevMul ? (prevMulV * mul) : mul);

                        try {
                            int adv = drawOps(op.children, cursorX, y, col, shadow);
                            cursorX += adv;
                            maxRight = Math.max(maxRight, cursorX);
                        } finally {
                            FORCE_COLOR_MUL = prevMul;
                            FORCED_COLOR_MUL = prevMulV;
                        }

                    } else {
                        // ✅ plain pulse stays plain pulse
                        int adv = this.drawPulse(payload, cursorX, y, shadow, col, sp, amp, fmt);
                        cursorX += adv;
                        maxRight = Math.max(maxRight, cursorX);
                    }

                    legacy.colorRgb = saved;
                    break;
                }

                case WAVE_TEXT: {
                    int col = (op.rgb >= 0 ? op.rgb : (hexActive ? activeHexRGB : baseColor));

                    float amp = (op.amp > 0.0F ? op.amp : 2.0F);
                    float sp  = (op.speed > 0.0F ? op.speed : 6.0F);

                    // ✅ If we have kids, inherit colors from them
                    if (op.children != null && op.children.size() > 0) {
                        int adv = drawWavePlain(op.payload, cursorX, y, col, shadow, amp, sp, op.children);
                        cursorX += adv;
                        maxRight = Math.max(maxRight, cursorX);
                        break;
                    }

                    // ✅ If kids are missing, NEVER render raw tags — wave the plain payload only
                    // (payload should already be plain from the parser; this is just extra safety)
                    String plain = stripAllTagsToPlainText(op.payload);
                    int adv = drawWavePlain(plain, cursorX, y, col, shadow, amp, sp, null);
                    cursorX += adv;
                    maxRight = Math.max(maxRight, cursorX);
                    break;
                }



                case SHAKE_TEXT: {
                    int col = (op.rgb >= 0 ? op.rgb : (hexActive ? activeHexRGB : baseColor));

                    java.util.List<Op> kids = (op.children != null && !op.children.isEmpty()) ? op.children : null;
                    String plain = op.payload;

                    if (kids == null && plain != null && (plain.indexOf('<') >= 0 || plain.indexOf('«') >= 0)) {
                        kids = parseToOps(plain);
                        plain = plainTextFromOps(kids);
                    } else if (kids != null) {
                        plain = plainTextFromOps(kids);
                    }

                    cursorX += drawShakeColored(plain, cursorX, y, col, shadow, kids);
                    maxRight = Math.max(maxRight, cursorX);
                    break;
                }

                case ZOOM_TEXT: {
                    int col = (op.rgb >= 0 ? op.rgb : (hexActive ? activeHexRGB : baseColor));

                    java.util.List<Op> kids = (op.children != null && !op.children.isEmpty()) ? op.children : null;
                    String plain = op.payload;

                    if (kids == null && plain != null && (plain.indexOf('<') >= 0 || plain.indexOf('«') >= 0)) {
                        kids = parseToOps(plain);
                        plain = plainTextFromOps(kids);
                    } else if (kids != null) {
                        plain = plainTextFromOps(kids);
                    }

                    cursorX += drawZoomColored(plain, cursorX, y, col, shadow, kids);
                    maxRight = Math.max(maxRight, cursorX);
                    break;
                }

                case RAIN_TEXT: {
                    int col = (op.rgb >= 0 ? op.rgb : (hexActive ? activeHexRGB : baseColor));

                    if (op.children != null && !op.children.isEmpty()) {

                        int adv = drawOps(op.children, cursorX, y, col, shadow);

                        String plain = plainTextFromOps(op.children);

                        // ✅ ONLY valid here (op/cursorX/col exist)

                        drawRainOverlayPlain(plain, cursorX, y, col, shadow,
                                0.45F, 18.0F, 14.0F, 0.45F,
                                op.children, op);



                        cursorX += adv;
                        maxRight = Math.max(maxRight, cursorX);

                    } else {
                        cursorX += drawRain(op.payload, cursorX, y, col, shadow);
                        maxRight = Math.max(maxRight, cursorX);
                    }
                    break;
                }

                case SCROLL_TEXT: {
                    int col = (op.rgb >= 0 ? op.rgb : (hexActive ? activeHexRGB : baseColor));

                    java.util.List<Op> kids = (op.children != null && !op.children.isEmpty()) ? op.children : null;
                    String plain = op.payload;

                    if (kids == null && plain != null && (plain.indexOf('<') >= 0 || plain.indexOf('«') >= 0)) {
                        kids = parseToOps(plain);
                        plain = plainTextFromOps(kids);
                    } else if (kids != null) {
                        plain = plainTextFromOps(kids);
                    }

                    cursorX += drawScrollColored(plain, cursorX, y, col, shadow, kids);
                    maxRight = Math.max(maxRight, cursorX);
                    break;
                }

                case JITTER_TEXT: {
                    int col = (op.rgb >= 0 ? op.rgb : (hexActive ? activeHexRGB : baseColor));

                    java.util.List<Op> kids = (op.children != null && !op.children.isEmpty()) ? op.children : null;
                    String plain = op.payload;

                    if (kids == null && plain != null && (plain.indexOf('<') >= 0 || plain.indexOf('«') >= 0)) {
                        kids = parseToOps(plain);
                        plain = plainTextFromOps(kids);
                    } else if (kids != null) {
                        plain = plainTextFromOps(kids);
                    }

                    cursorX += drawJitterColored(plain, cursorX, y, col, shadow, op.amp, op.speed, kids);
                    maxRight = Math.max(maxRight, cursorX);
                    break;
                }


                case WOBBLE_TEXT: {
                    int col = (op.rgb >= 0 ? op.rgb : (hexActive ? activeHexRGB : baseColor));

                    java.util.List<Op> kids = (op.children != null && !op.children.isEmpty()) ? op.children : null;
                    String plain = op.payload;

                    if (kids == null && plain != null && (plain.indexOf('<') >= 0 || plain.indexOf('«') >= 0)) {
                        kids = parseToOps(plain);
                        plain = plainTextFromOps(kids);
                    } else if (kids != null) {
                        plain = plainTextFromOps(kids);
                    }

                    cursorX += drawWobbleColored(plain, cursorX, y, col, shadow, op.amp, op.speed, kids);
                    maxRight = Math.max(maxRight, cursorX);
                    break;
                }



                case LOOP_TEXT: {
                    int col = (op.rgb >= 0 ? op.rgb : (hexActive ? activeHexRGB : baseColor));

                    java.util.List<Op> kids = (op.children != null && !op.children.isEmpty()) ? op.children : null;
                    String plain = op.payload;

                    if (kids == null && plain != null && (plain.indexOf('<') >= 0 || plain.indexOf('«') >= 0)) {
                        kids = parseToOps(plain);
                        plain = plainTextFromOps(kids);
                    } else if (kids != null) {
                        plain = plainTextFromOps(kids);
                    }

                    cursorX += drawLoopColored(plain, cursorX, y, col, shadow, op.amp, op.speed, kids);
                    maxRight = Math.max(maxRight, cursorX);
                    break;
                }




                case SHOOTING_STAR_TEXT: {
                    int col = (op.rgb >= 0 ? op.rgb : (hexActive ? activeHexRGB : baseColor));

                    java.util.List<Op> kids = (op.children != null && !op.children.isEmpty()) ? op.children : null;
                    String plain = op.payload;

                    if (kids == null && plain != null && (plain.indexOf('<') >= 0 || plain.indexOf('«') >= 0)) {
                        kids = parseToOps(plain);
                        plain = plainTextFromOps(kids);
                    } else if (kids != null) {
                        plain = plainTextFromOps(kids);
                    }

                    cursorX += drawShootingStarColored(plain, cursorX, y, col, shadow, kids, op);
                    maxRight = Math.max(maxRight, cursorX);
                    break;
                }


                case OUTLINE_TEXT: {
                    int col = (op.rgb >= 0 ? op.rgb : (hexActive ? activeHexRGB : baseColor));

                    if (op.children != null && !op.children.isEmpty()) {

                        boolean prevSolid = FORCE_COLOR_SOLID;
                        int prevSolidCol = FORCED_COLOR_SOLID;

                        // 1) Outline pass: force solid outline color
                        FORCE_COLOR_SOLID = true;
                        FORCED_COLOR_SOLID = (op.outlineColor != 0 ? op.outlineColor : 0x000000);

                        try {
                            // draw 4-direction outline (or 8 if you want thicker)
                            drawOps(op.children, cursorX + 1, y, col, shadow);
                            drawOps(op.children, cursorX - 1, y, col, shadow);
                            drawOps(op.children, cursorX, y + 1, col, shadow);
                            drawOps(op.children, cursorX, y - 1, col, shadow);
                        } finally {
                            FORCE_COLOR_SOLID = prevSolid;
                            FORCED_COLOR_SOLID = prevSolidCol;
                        }

                        // 2) Main pass (normal colors/grad/rbw intact)
                        int adv = drawOps(op.children, cursorX, y, col, shadow);
                        cursorX += adv;
                        maxRight = Math.max(maxRight, cursorX);

                    } else {
                        cursorX += drawOutlineText(op.payload, cursorX, y, col, op.outlineColor, shadow);
                        maxRight = Math.max(maxRight, cursorX);
                    }
                    break;
                }


                case SHADOW_TEXT: {
                    int mainCol = (op.rgb >= 0 ? op.rgb : (hexActive ? activeHexRGB : baseColor));
                    int shCol   = (op.shadowColor != 0 ? op.shadowColor : 0x3F3F3F);

                    if (op.children != null && !op.children.isEmpty()) {

                        boolean prevSolid = FORCE_COLOR_SOLID;
                        int prevSolidCol  = FORCED_COLOR_SOLID;

                        // shadow pass: solid shadow color
                        FORCE_COLOR_SOLID  = true;
                        FORCED_COLOR_SOLID = shCol;

                        try {
                            drawOps(op.children, cursorX + 1, y + 1, mainCol, shadow);
                        } finally {
                            FORCE_COLOR_SOLID  = prevSolid;
                            FORCED_COLOR_SOLID = prevSolidCol;
                        }

                        // main pass: normal nested render (grad/rbw/etc preserved)
                        int adv = drawOps(op.children, cursorX, y, mainCol, shadow);
                        cursorX += adv;
                        maxRight = Math.max(maxRight, cursorX);

                    } else {
                        int adv = drawShadowText(op.payload, cursorX, y, mainCol, shCol, shadow);
                        cursorX += adv;
                        maxRight = Math.max(maxRight, cursorX);
                    }
                    break;
                }


                case SPARKLE_TEXT: {
                    int col = (op.rgb >= 0 ? op.rgb : (hexActive ? activeHexRGB : baseColor));

                    if (op.children != null && !op.children.isEmpty()) {

                        int adv = drawOps(op.children, cursorX, y, col, shadow);

                        String plain = plainTextFromOps(op.children); // the helper we made for rain
                        drawSparkleOverlayPlain(plain, cursorX, y, col, shadow,
                                op.sparkleIntensity, op.speed, op.children, op);

                        cursorX += adv;
                        maxRight = Math.max(maxRight, cursorX);

                    } else {
                        int adv = this.drawString(op.payload, cursorX, y, col, shadow);
                        String plain = stripAllTagsToPlainText(op.payload);
                        drawSparkleOverlayPlain(plain, cursorX, y, col, shadow,
                                op.sparkleIntensity, op.speed, null, op);
                        cursorX += adv;
                        maxRight = Math.max(maxRight, cursorX);
                    }
                    break;
                }


                case FLICKER_TEXT: {
                    int baseCol = (op.rgb >= 0 ? op.rgb : (hexActive ? activeHexRGB : baseColor));
                    cursorX += drawFlickerText(op.payload, cursorX, y, baseCol, shadow, op.flickerSpeed);
                    maxRight = Math.max(maxRight, cursorX);
                    break;
                }

                case GLITCH_TEXT: {
                    int col = (op.rgb >= 0 ? op.rgb : (hexActive ? activeHexRGB : baseColor));

                    if (op.children != null && !op.children.isEmpty()) {
                        int adv = drawOps(op.children, cursorX, y, col, shadow);

                        String plain = plainTextFromOps(op.children);
                        drawGlitchText(plain, cursorX, y, col, shadow,
                                op.glitchAmount, 1.0F, 0.0F,
                                true); // overlayOnly

                        cursorX += adv;
                        maxRight = Math.max(maxRight, cursorX);
                    } else {
                        cursorX += drawGlitchText(op.payload, cursorX, y, col, shadow,
                                op.glitchAmount, 1.0F, 0.0F,
                                false); // normal
                        maxRight = Math.max(maxRight, cursorX);
                    }
                    break;
                }

                case SNOW_AURA: {
                    int col = (op.rgb >= 0 ? op.rgb : (hexActive ? activeHexRGB : baseColor));

                    // Nested path
                    if (op.children != null && !op.children.isEmpty()) {

                        // 1) draw the inner styled content
                        int adv = drawOps(op.children, cursorX, y, col, shadow);

                        // 2) snow overlay uses plain text for width/flake placement
                        String plain = plainTextFromOps(op.children);
                        drawSnowAura(op, plain, cursorX, y, col, shadow);

                        cursorX += adv;
                        maxRight = Math.max(maxRight, cursorX);
                        break;
                    }

                    // Legacy/plain path
                    int adv = this.drawString(op.payload, cursorX, y, col, shadow);

                    // overlay snow around same payload
                    drawSnowAura(op, op.payload, cursorX, y, col, shadow);

                    cursorX += adv;
                    maxRight = Math.max(maxRight, cursorX);
                    break;
                }


            }


            maxRight = Math.max(maxRight, cursorX);



            maxRight = Math.max(maxRight, cursorX);



            maxRight = Math.max(maxRight, cursorX);
        }

        return maxRight - x;
    }


    private List<Op> parseToOps(String raw) {
        List<Op> ops = new ArrayList();
        if (raw == null || raw.isEmpty()) return ops;

        // Expand shorthand tags (<g:...>, <pl:...>, <wave:a:...>, <zoom:a:...>)
        String s = expandShortTags(raw);
        while (true) {
            // Core tags
            Matcher mgA = TAG_GRAD_OPEN.matcher(s);
            Matcher mgC = TAG_GRAD_OPEN_CHEV.matcher(s);
            Matcher mrA = TAG_RBW_OPEN.matcher(s);
            Matcher mrC = TAG_RBW_OPEN_CHEV.matcher(s);
            Matcher mpA = TAG_PULSE_OPEN.matcher(s);
            Matcher mpC = TAG_PULSE_OPEN_CHEV.matcher(s);

            // WAVE
            Matcher mwA   = Pattern.compile("(?i)<\\s*wave([^>]*)>").matcher(s);
            Matcher mwC   = Pattern.compile("(?i)[«]\\s*wave([^»]*)[»]").matcher(s);

            // SHAKE
            Matcher mshA  = Pattern.compile("(?i)<\\s*shake([^>]*)>").matcher(s);
            Matcher mshC  = Pattern.compile("(?i)[«]\\s*shake([^»]*)[»]").matcher(s);

            // ZOOM
            Matcher mzA   = Pattern.compile("(?i)<\\s*zoom([^>]*)>").matcher(s);
            Matcher mzC   = Pattern.compile("(?i)[«]\\s*zoom([^»]*)[»]").matcher(s);

            // RAIN
            Matcher mrnA  = Pattern.compile("(?i)<\\s*rain([^>]*)>").matcher(s);
            Matcher mrnC  = Pattern.compile("(?i)[«]\\s*rain([^»]*)[»]").matcher(s);

            // SCROLL
            Matcher mscA  = Pattern.compile("(?i)<\\s*scroll([^>]*)>").matcher(s);
            Matcher mscC  = Pattern.compile("(?i)[«]\\s*scroll([^»]*)[»]").matcher(s);

            // SNOW
            Matcher msnA  = Pattern.compile("(?i)<\\s*snow([^>]*)>").matcher(s);
            Matcher msnC  = Pattern.compile("(?i)[«]\\s*snow([^»]*)[»]").matcher(s);

            // JITTER
            Matcher mjitA = Pattern.compile("(?i)<\\s*jitter([^>]*)>").matcher(s);
            Matcher mjitC = Pattern.compile("(?i)[«]\\s*jitter([^»]*)[»]").matcher(s);

            // WOBBLE
            Matcher mwbA  = Pattern.compile("(?i)<\\s*wobble([^>]*)>").matcher(s);
            Matcher mwbC  = Pattern.compile("(?i)[«]\\s*wobble([^»]*)[»]").matcher(s);

            // LOOP
            Matcher mlpA  = Pattern.compile("(?i)<\\s*loop([^>]*)>").matcher(s);
            Matcher mlpC  = Pattern.compile("(?i)[«]\\s*loop([^»]*)[»]").matcher(s);


            // SHOOTING STAR
            Matcher mssA  = Pattern.compile("(?i)<\\s*shootingstar([^>]*)>").matcher(s);
            Matcher mssC  = Pattern.compile("(?i)[«]\\s*shootingstar([^»]*)[»]").matcher(s);

            // OUTLINE
            Matcher moutA = Pattern.compile("(?i)<\\s*outline([^>]*)>").matcher(s);
            Matcher moutC = Pattern.compile("(?i)[«]\\s*outline([^»]*)[»]").matcher(s);

            // SHADOW
            Matcher mshdA = Pattern.compile("(?i)<\\s*shadow([^>]*)>").matcher(s);
            Matcher mshdC = Pattern.compile("(?i)[«]\\s*shadow([^»]*)[»]").matcher(s);

            // SPARKLE
            Matcher mspA  = Pattern.compile("(?i)<\\s*sparkle([^>]*)>").matcher(s);
            Matcher mspC  = Pattern.compile("(?i)[«]\\s*sparkle([^»]*)[»]").matcher(s);

            // FLICKER
            Matcher mfA   = Pattern.compile("(?i)<\\s*flicker([^>]*)>").matcher(s);
            Matcher mfC   = Pattern.compile("(?i)[«]\\s*flicker([^»]*)[»]").matcher(s);

            // GLITCH
            Matcher mglA  = Pattern.compile("(?i)<\\s*glitch([^>]*)>").matcher(s);
            Matcher mglC  = Pattern.compile("(?i)[«]\\s*glitch([^»]*)[»]").matcher(s);

            boolean gA  = mgA.find();
            boolean gC  = mgC.find();
            boolean rA  = mrA.find();
            boolean rC  = mrC.find();
            boolean pA  = mpA.find();
            boolean pC  = mpC.find();

            boolean wA  = mwA.find();
            boolean wC  = mwC.find();
            boolean shA = mshA.find();
            boolean shC = mshC.find();
            boolean zA  = mzA.find();
            boolean zC  = mzC.find();
            boolean rnA = mrnA.find();
            boolean rnC = mrnC.find();
            boolean scA = mscA.find();
            boolean scC = mscC.find();
            boolean snA = msnA.find();
            boolean snC = msnC.find();

            boolean jitA = mjitA.find();
            boolean jitC = mjitC.find();
            boolean wbA  = mwbA.find();
            boolean wbC  = mwbC.find();
            boolean lpA  = mlpA.find();
            boolean lpC  = mlpC.find();
            boolean ssA  = mssA.find();
            boolean ssC  = mssC.find();
            boolean outA = moutA.find();
            boolean outC = moutC.find();
            boolean shdA = mshdA.find();
            boolean shdC = mshdC.find();
            boolean spA  = mspA.find();
            boolean spC  = mspC.find();
            boolean flA  = mfA.find();
            boolean flC  = mfC.find();
            boolean glA  = mglA.find();
            boolean glC  = mglC.find();

            // If there are no more known tags, flush tail + stop
            if (!gA && !gC && !rA && !rC && !pA && !pC &&
                    !wA && !wC && !shA && !shC && !zA && !zC &&
                    !rnA && !rnC && !scA && !scC &&
                    !snA && !snC &&
                    !jitA && !jitC && !wbA && !wbC &&
                    !lpA && !lpC && !ssA && !ssC && !outA && !outC && !shdA && !shdC &&
                    !spA && !spC && !flA && !flC &&
                    !glA && !glC) {
                break;
            }


            // Earliest tag location of ANY type
            int idxGA   = gA   ? mgA.start()   : Integer.MAX_VALUE;
            int idxGC   = gC   ? mgC.start()   : Integer.MAX_VALUE;
            int idxRA   = rA   ? mrA.start()   : Integer.MAX_VALUE;
            int idxRC   = rC   ? mrC.start()   : Integer.MAX_VALUE;
            int idxPA   = pA   ? mpA.start()   : Integer.MAX_VALUE;
            int idxPC   = pC   ? mpC.start()   : Integer.MAX_VALUE;
            int idxWA   = wA   ? mwA.start()   : Integer.MAX_VALUE;
            int idxWC   = wC   ? mwC.start()   : Integer.MAX_VALUE;
            int idxSHA  = shA  ? mshA.start()  : Integer.MAX_VALUE;
            int idxSHC  = shC  ? mshC.start()  : Integer.MAX_VALUE;
            int idxZA   = zA   ? mzA.start()   : Integer.MAX_VALUE;
            int idxZC   = zC   ? mzC.start()   : Integer.MAX_VALUE;
            int idxRNA  = rnA  ? mrnA.start()  : Integer.MAX_VALUE;
            int idxRNC  = rnC  ? mrnC.start()  : Integer.MAX_VALUE;
            int idxSCA  = scA  ? mscA.start()  : Integer.MAX_VALUE;
            int idxSCC  = scC  ? mscC.start()  : Integer.MAX_VALUE;
            int idxSNA  = snA  ? msnA.start()  : Integer.MAX_VALUE;
            int idxSNC  = snC  ? msnC.start()  : Integer.MAX_VALUE;

            int idxJITA = jitA ? mjitA.start() : Integer.MAX_VALUE;
            int idxJITC = jitC ? mjitC.start() : Integer.MAX_VALUE;
            int idxWBA  = wbA  ? mwbA.start()  : Integer.MAX_VALUE;
            int idxWBC  = wbC  ? mwbC.start()  : Integer.MAX_VALUE;

            int idxLPA  = lpA  ? mlpA.start()  : Integer.MAX_VALUE;
            int idxLPC  = lpC  ? mlpC.start()  : Integer.MAX_VALUE;
            int idxSSA  = ssA  ? mssA.start()  : Integer.MAX_VALUE;
            int idxSSC  = ssC  ? mssC.start()  : Integer.MAX_VALUE;
            int idxOUTA = outA ? moutA.start() : Integer.MAX_VALUE;
            int idxOUTC = outC ? moutC.start() : Integer.MAX_VALUE;
            int idxSHDA = shdA ? mshdA.start() : Integer.MAX_VALUE;
            int idxSHDC = shdC ? mshdC.start() : Integer.MAX_VALUE;
            int idxSPA  = spA  ? mspA.start()  : Integer.MAX_VALUE;
            int idxSPC  = spC  ? mspC.start()  : Integer.MAX_VALUE;
            int idxFLA  = flA  ? mfA.start()   : Integer.MAX_VALUE;
            int idxFLC  = flC  ? mfC.start()   : Integer.MAX_VALUE;
            int idxGLA  = glA  ? mglA.start()  : Integer.MAX_VALUE;
            int idxGLC  = glC  ? mglC.start()  : Integer.MAX_VALUE;

            int pick = idxGA;
            pick = Math.min(pick, idxGC);
            pick = Math.min(pick, idxRA);
            pick = Math.min(pick, idxRC);
            pick = Math.min(pick, idxPA);
            pick = Math.min(pick, idxPC);
            pick = Math.min(pick, idxWA);
            pick = Math.min(pick, idxWC);
            pick = Math.min(pick, idxSHA);
            pick = Math.min(pick, idxSHC);
            pick = Math.min(pick, idxZA);
            pick = Math.min(pick, idxZC);
            pick = Math.min(pick, idxRNA);
            pick = Math.min(pick, idxRNC);
            pick = Math.min(pick, idxSCA);
            pick = Math.min(pick, idxSCC);
            pick = Math.min(pick, idxSNA);
            pick = Math.min(pick, idxSNC);

            pick = Math.min(pick, idxJITA);
            pick = Math.min(pick, idxJITC);
            pick = Math.min(pick, idxWBA);
            pick = Math.min(pick, idxWBC);
            pick = Math.min(pick, idxLPA);
            pick = Math.min(pick, idxLPC);
            pick = Math.min(pick, idxSSA);
            pick = Math.min(pick, idxSSC);
            pick = Math.min(pick, idxOUTA);
            pick = Math.min(pick, idxOUTC);
            pick = Math.min(pick, idxSHDA);
            pick = Math.min(pick, idxSHDC);
            pick = Math.min(pick, idxSPA);
            pick = Math.min(pick, idxSPC);
            pick = Math.min(pick, idxFLA);
            pick = Math.min(pick, idxFLC);
            pick = Math.min(pick, idxGLA);
            pick = Math.min(pick, idxGLC);

            if (pick > 0 && pick < Integer.MAX_VALUE) {
                // Emit any plain text before the first tag
                emitWithSimpleHex(s.substring(0, pick), ops);
                s = s.substring(pick);
            } else {
                // Re-scan on the sliced string; now we only care about tags at the FRONT
                mgA  = TAG_GRAD_OPEN.matcher(s);
                mgC  = TAG_GRAD_OPEN_CHEV.matcher(s);
                mrA  = TAG_RBW_OPEN.matcher(s);
                mrC  = TAG_RBW_OPEN_CHEV.matcher(s);
                mpA  = TAG_PULSE_OPEN.matcher(s);
                mpC  = TAG_PULSE_OPEN_CHEV.matcher(s);

                mwA   = Pattern.compile("(?i)<\\s*wave([^>]*)>").matcher(s);
                mwC   = Pattern.compile("(?i)[«]\\s*wave([^»]*)[»]").matcher(s);
                mshA  = Pattern.compile("(?i)<\\s*shake([^>]*)>").matcher(s);
                mshC  = Pattern.compile("(?i)[«]\\s*shake([^»]*)[»]").matcher(s);
                mzA   = Pattern.compile("(?i)<\\s*zoom([^>]*)>").matcher(s);
                mzC   = Pattern.compile("(?i)[«]\\s*zoom([^»]*)[»]").matcher(s);
                mrnA  = Pattern.compile("(?i)<\\s*rain([^>]*)>").matcher(s);
                mrnC  = Pattern.compile("(?i)[«]\\s*rain([^»]*)[»]").matcher(s);
                mscA  = Pattern.compile("(?i)<\\s*scroll([^>]*)>").matcher(s);
                mscC  = Pattern.compile("(?i)[«]\\s*scroll([^»]*)[»]").matcher(s);
                msnA  = Pattern.compile("(?i)<\\s*snow([^>]*)>").matcher(s);
                msnC  = Pattern.compile("(?i)[«]\\s*snow([^»]*)[»]").matcher(s);

                mjitA = Pattern.compile("(?i)<\\s*jitter([^>]*)>").matcher(s);
                mjitC = Pattern.compile("(?i)[«]\\s*jitter([^»]*)[»]").matcher(s);
                mwbA  = Pattern.compile("(?i)<\\s*wobble([^>]*)>").matcher(s);
                mwbC  = Pattern.compile("(?i)[«]\\s*wobble([^»]*)[»]").matcher(s);
                mlpA  = Pattern.compile("(?i)<\\s*loop([^>]*)>").matcher(s);
                mlpC  = Pattern.compile("(?i)[«]\\s*loop([^»]*)[»]").matcher(s);
                mssA  = Pattern.compile("(?i)<\\s*shootingstar([^>]*)>").matcher(s);
                mssC  = Pattern.compile("(?i)[«]\\s*shootingstar([^»]*)[»]").matcher(s);
                moutA = Pattern.compile("(?i)<\\s*outline([^>]*)>").matcher(s);
                moutC = Pattern.compile("(?i)[«]\\s*outline([^»]*)[»]").matcher(s);
                mshdA = Pattern.compile("(?i)<\\s*shadow([^>]*)>").matcher(s);
                mshdC = Pattern.compile("(?i)[«]\\s*shadow([^»]*)[»]").matcher(s);
                mspA  = Pattern.compile("(?i)<\\s*sparkle([^>]*)>").matcher(s);
                mspC  = Pattern.compile("(?i)[«]\\s*sparkle([^»]*)[»]").matcher(s);
                mfA   = Pattern.compile("(?i)<\\s*flicker([^>]*)>").matcher(s);
                mfC   = Pattern.compile("(?i)[«]\\s*flicker([^»]*)[»]").matcher(s);
                mglA  = Pattern.compile("(?i)<\\s*glitch([^>]*)>").matcher(s);
                mglC  = Pattern.compile("(?i)[«]\\s*glitch([^»]*)[»]").matcher(s);

                boolean rbwAt     = mrA.lookingAt()   || mrC.lookingAt();
                boolean gradAt    = mgA.lookingAt()   || mgC.lookingAt();
                boolean pulseAt   = mpA.lookingAt()   || mpC.lookingAt();
                boolean waveAt    = mwA.lookingAt()   || mwC.lookingAt();
                boolean shakeAt   = mshA.lookingAt()  || mshC.lookingAt();
                boolean zoomAt    = mzA.lookingAt()   || mzC.lookingAt();
                boolean rainAt    = mrnA.lookingAt()  || mrnC.lookingAt();
                boolean scrollAt  = mscA.lookingAt()  || mscC.lookingAt();
                boolean jitterAt  = mjitA.lookingAt() || mjitC.lookingAt();
                boolean wobbleAt  = mwbA.lookingAt()  || mwbC.lookingAt();
                boolean loopAt    = mlpA.lookingAt()  || mlpC.lookingAt();
                boolean shootingStarAt = mssA.lookingAt() || mssC.lookingAt();
                boolean outlineAt = moutA.lookingAt() || moutC.lookingAt();
                boolean shadowAt  = mshdA.lookingAt() || mshdC.lookingAt();
                boolean sparkleAt = mspA.lookingAt()  || mspC.lookingAt();
                boolean flickerAt = mfA.lookingAt()   || mfC.lookingAt();
                boolean glitchAt  = mglA.lookingAt()  || mglC.lookingAt();
                boolean snowAt    = msnA.lookingAt()  || msnC.lookingAt();


                // ─────────────────────────────────────
                // RAINBOW
                // ─────────────────────────────────────
                if (rbwAt) {
                    boolean chevron = mrC.lookingAt();
                    Matcher mOpen = chevron ? mrC : mrA;
                    int openEnd = mOpen.end();
                    String attrs = mOpen.group(1);
                    Matcher closePref = (chevron ? TAG_RBW_CLOSE_CHEV : TAG_RBW_CLOSE)
                            .matcher(s).region(openEnd, s.length());
                    Matcher closeOther = (chevron ? TAG_RBW_CLOSE : TAG_RBW_CLOSE_CHEV)
                            .matcher(s).region(openEnd, s.length());
                    Op r = new Op();
                    r.kind = HexFontRenderer.Kind.RAINBOW_TEXT;
                    parseRainbowArgs(attrs, r);
                    parseRainbowSpeed(attrs, r);
                    r.legacyFromTag = stylesFromAttrs(attrs);
                    if (closePref.find()) {
                        String inner = s.substring(openEnd, closePref.start());
                        r.payload = inner;
                        r.children = parseChildren(inner);
                        ops.add(r);
                        s = s.substring(closePref.end());
                    } else if (closeOther.find()) {
                        String inner = s.substring(openEnd, closeOther.start());
                        r.payload = inner;
                        r.children = parseChildren(inner);
                        ops.add(r);
                        s = s.substring(closeOther.end());
                    } else {
                        String inner = s.substring(openEnd);
                        r.payload = inner;
                        r.children = parseChildren(inner);
                        ops.add(r);
                        s = "";
                    }

                    continue;
                }

                // ─────────────────────────────────────
                // GRADIENT
                // ─────────────────────────────────────
                if (gradAt) {
                    boolean chevron = mgC.lookingAt();
                    Matcher mOpen = chevron ? mgC : mgA;
                    int openEnd = mOpen.end();
                    String attrs = mOpen.group(1);
                    List<String> hexes = parseHexTokens(attrs);
                    if (hexes.size() < 2) {
                        emitWithSimpleHex(s.substring(0, openEnd), ops);
                        s = s.substring(openEnd);
                    } else {
                        int[] stops = new int[hexes.size()];
                        for (int i = 0; i < hexes.size(); ++i) {
                            stops[i] = parseHexRGB((String) hexes.get(i));
                        }

                        Matcher closePref = (chevron ? TAG_GRAD_CLOSE_CHEV : TAG_GRAD_CLOSE)
                                .matcher(s).region(openEnd, s.length());
                        Matcher closeOther = (chevron ? TAG_GRAD_CLOSE : TAG_GRAD_CLOSE_CHEV)
                                .matcher(s).region(openEnd, s.length());

                        Op g = new Op();
                        g.kind = HexFontRenderer.Kind.GRADIENT_MULTI;
                        g.stops = stops;
                        parseGradAnimArgs(attrs, g);
                        g.legacyFromTag = stylesFromAttrs(attrs);
                        if (closePref.find()) {
                            String inner = s.substring(openEnd, closePref.start());
                            g.payload = inner;
                            g.children = parseChildren(inner);
                            ops.add(g);
                            s = s.substring(closePref.end());
                        } else if (closeOther.find()) {
                            String inner = s.substring(openEnd, closeOther.start());
                            g.payload = inner;
                            g.children = parseChildren(inner);
                            ops.add(g);
                            s = s.substring(closeOther.end());
                        } else {
                            String inner = s.substring(openEnd);
                            g.payload = inner;
                            g.children = parseChildren(inner);
                            ops.add(g);
                            s = "";
                        }

                    }
                    continue;
                }

                // ─────────────────────────────────────
                // BLOCK EFFECT TAGS (wave / shake / zoom / rain / scroll / jitter / wobble / outline / shadow / sparkle / flicker / glitch)
                // ─────────────────────────────────────

                if (waveAt) {
                    boolean chev = mwC.lookingAt();
                    Matcher mOpen = chev ? mwC : mwA;
                    int openEnd = mOpen.end();
                    String attrs = mOpen.group(1);

                    Matcher close = Pattern.compile("(?i)</\\s*wave\\s*>")
                            .matcher(s).region(openEnd, s.length());

                    boolean found = close.find();

                    Op w = new Op();
                    w.kind = Kind.WAVE_TEXT;
                    parseMotionArgs(attrs, w, 2.0F, 6.0F);

                    String inner = found ? s.substring(openEnd, close.start()) : s.substring(openEnd);

// nested parse
                    java.util.List<Op> kids = parseToOps(inner);
                    w.children = kids;

// payload is what we animate (plain glyph stream)
                    w.payload = plainTextFromOps(kids);

// keep the original too (optional but helps sanity checks)

                    ops.add(w);

                    s = found ? s.substring(close.end()) : "";
                    continue;

                }

                if (shakeAt) {
                    boolean chev = mshC.lookingAt();
                    Matcher mOpen = chev ? mshC : mshA;
                    int openEnd = mOpen.end();
                    String attrs = mOpen.group(1);

                    Matcher close = Pattern.compile("(?i)</\\s*shake\\s*>")
                            .matcher(s).region(openEnd, s.length());

                    boolean found = close.find();

                    Op sh = new Op();
                    sh.kind = Kind.SHAKE_TEXT;
                    parseMotionArgs(attrs, sh, 2.0F, 0.0F);

                    sh.payload = found
                            ? s.substring(openEnd, close.start())
                            : s.substring(openEnd);

                    ops.add(sh);
                    s = found ? s.substring(close.end()) : "";
                    continue;
                }

                if (zoomAt) {
                    boolean chev = mzC.lookingAt();
                    Matcher mOpen = chev ? mzC : mzA;
                    int openEnd = mOpen.end();
                    String attrs = mOpen.group(1);

                    Matcher close = Pattern.compile("(?i)</\\s*zoom\\s*>")
                            .matcher(s).region(openEnd, s.length());

                    boolean found = close.find();

                    Op z = new Op();
                    z.kind = Kind.ZOOM_TEXT;
                    parseMotionArgs(attrs, z, 1.0F, 0.0F);

                    z.payload = found
                            ? s.substring(openEnd, close.start())
                            : s.substring(openEnd);

                    ops.add(z);
                    s = found ? s.substring(close.end()) : "";
                    continue;
                }

                if (rainAt) {
                    boolean chev = mrnC.lookingAt();
                    Matcher mOpen = chev ? mrnC : mrnA;
                    int openEnd = mOpen.end();
                    String attrs = mOpen.group(1);

                    Matcher close = Pattern.compile("(?i)</\\s*rain\\s*>")
                            .matcher(s).region(openEnd, s.length());

                    boolean found = close.find();

                    Op rn = new Op();
                    rn.kind = Kind.RAIN_TEXT;
                    parseMotionArgs(attrs, rn, 4.0F, 8.0F);
                    parseRainDropColorArgs(attrs, rn);

                    // inner markup between <rain ...> and </rain>
                    String inner = found
                            ? s.substring(openEnd, close.start())
                            : s.substring(openEnd);

                    // ✅ NEW: nested parse so <grad>, <rbw>, etc. work inside rain
                    List<Op> kids = parseToOps(inner);     // <-- use your actual nested parser name
                    rn.children = kids;

                    // ✅ payload becomes plain glyph stream (for overlay + widths)
                    rn.payload = plainTextFromOps(kids);

                    ops.add(rn);

                    // consume the tag
                    s = found ? s.substring(close.end()) : "";
                    continue;
                }


                if (snowAt) {
                    boolean chev = msnC.lookingAt();
                    Matcher mOpen = chev ? msnC : msnA;
                    int openEnd = mOpen.end();
                    String attrs = mOpen.group(1);

                    Matcher close = Pattern.compile("(?i)</\\s*snow\\s*>")
                            .matcher(s).region(openEnd, s.length());

                    boolean found = close.find();

                    Op sn = new Op();
                    sn.kind = Kind.SNOW_AURA;

                    // <-- your snow config parser (this is where color/gradient/etc live)
                    parseSnowArgs(attrs, sn);

                    sn.payload = found
                            ? s.substring(openEnd, close.start())
                            : s.substring(openEnd);

                    ops.add(sn);
                    s = found ? s.substring(close.end()) : "";
                    continue;
                }

                if (scrollAt) {
                    boolean chev = mscC.lookingAt();
                    Matcher mOpen = chev ? mscC : mscA;
                    int openEnd = mOpen.end();
                    String attrs = mOpen.group(1);

                    Matcher close = Pattern.compile("(?i)</\\s*scroll\\s*>")
                            .matcher(s).region(openEnd, s.length());

                    boolean found = close.find();

                    Op sc = new Op();
                    sc.kind = Kind.SCROLL_TEXT;
                    parseMotionArgs(attrs, sc, 2.0F, 5.0F);

                    sc.payload = found
                            ? s.substring(openEnd, close.start())
                            : s.substring(openEnd);

                    ops.add(sc);
                    s = found ? s.substring(close.end()) : "";
                    continue;
                }

                if (jitterAt) {
                    boolean chev = mjitC.lookingAt();
                    Matcher mOpen = chev ? mjitC : mjitA;
                    int openEnd = mOpen.end();
                    String attrs = mOpen.group(1);

                    Matcher close = Pattern.compile("(?i)</\\s*jitter\\s*>")
                            .matcher(s).region(openEnd, s.length());

                    boolean found = close.find();

                    Op jt = new Op();
                    jt.kind = Kind.JITTER_TEXT;
                    parseMotionArgs(attrs, jt, 1.0F, 12.0F);

                    jt.payload = found
                            ? s.substring(openEnd, close.start())
                            : s.substring(openEnd);

                    ops.add(jt);
                    s = found ? s.substring(close.end()) : "";
                    continue;
                }

                if (wobbleAt) {
                    boolean chev = mwbC.lookingAt();
                    Matcher mOpen = chev ? mwbC : mwbA;
                    int openEnd = mOpen.end();
                    String attrs = mOpen.group(1);

                    Matcher close = Pattern.compile("(?i)</\\s*wobble\\s*>")
                            .matcher(s).region(openEnd, s.length());

                    boolean found = close.find();

                    Op wb = new Op();
                    wb.kind = Kind.WOBBLE_TEXT;
                    parseMotionArgs(attrs, wb, 4.0F, 2.0F);

                    wb.payload = found
                            ? s.substring(openEnd, close.start())
                            : s.substring(openEnd);

                    ops.add(wb);
                    s = found ? s.substring(close.end()) : "";
                    continue;
                }

                if (loopAt) {
                    boolean chev = mlpC.lookingAt();
                    Matcher mOpen = chev ? mlpC : mlpA;
                    int openEnd = mOpen.end();
                    String attrs = mOpen.group(1);

                    Matcher close = Pattern.compile("(?i)</\\s*loop\\s*>")
                            .matcher(s).region(openEnd, s.length());

                    boolean found = close.find();

                    Op lp = new Op();
                    lp.kind = Kind.LOOP_TEXT;
                    parseMotionArgs(attrs, lp, 4.0F, 2.0F);

                    lp.payload = found
                            ? s.substring(openEnd, close.start())
                            : s.substring(openEnd);

                    ops.add(lp);
                    s = found ? s.substring(close.end()) : "";
                    continue;
                }


                if (shootingStarAt) {
                    boolean chev = mssC.lookingAt();
                    Matcher mOpen = chev ? mssC : mssA;
                    int openEnd = mOpen.end();
                    String attrs = mOpen.group(1);

                    Matcher close = Pattern.compile("(?i)</\\s*shootingstar\\s*>")
                            .matcher(s).region(openEnd, s.length());

                    boolean found = close.find();

                    String inner = found
                            ? s.substring(openEnd, close.start())
                            : s.substring(openEnd);

                    Op ss = new Op();
                    ss.kind = Kind.SHOOTING_STAR_TEXT;
                    parseShootingStarArgs(attrs, ss);

                    ss.payload = inner;
                    ss.children = parseChildren(inner);

                    ops.add(ss);
                    s = found ? s.substring(close.end()) : "";
                    continue;
                }

                if (outlineAt) {
                    boolean chev = moutC.lookingAt();
                    Matcher mOpen = chev ? moutC : moutA;
                    int openEnd = mOpen.end();
                    String attrs = mOpen.group(1);

                    Matcher close = Pattern.compile("(?i)</\\s*outline\\s*>")
                            .matcher(s).region(openEnd, s.length());

                    boolean found = close.find();

                    Op o = new Op();
                    o.kind = Kind.OUTLINE_TEXT;
                    parseOutlineArgs(attrs, o);

                    o.payload = found
                            ? s.substring(openEnd, close.start())
                            : s.substring(openEnd);

                    ops.add(o);
                    s = found ? s.substring(close.end()) : "";
                    continue;
                }

                if (shadowAt) {
                    boolean chev = mshdC.lookingAt();
                    Matcher mOpen = chev ? mshdC : mshdA;
                    int openEnd = mOpen.end();
                    String attrs = mOpen.group(1);

                    Matcher close = Pattern.compile("(?i)</\\s*shadow\\s*>")
                            .matcher(s).region(openEnd, s.length());

                    boolean found = close.find();

                    String inner = found
                            ? s.substring(openEnd, close.start())
                            : s.substring(openEnd);

                    Op o = new Op();
                    o.kind = Kind.SHADOW_TEXT;

                    // default + parse args
                    o.shadowColor = 0x3F3F3F;     // vanilla-ish shadow tone
                    parseShadowArgs(attrs, o);

                    o.payload = inner;

                    // IMPORTANT: nested tags must be parsed into children so draw side can render them twice
                    o.children = parseChildren(inner);

                    ops.add(o);
                    s = found ? s.substring(close.end()) : "";
                    continue;
                }

                if (sparkleAt) {
                    boolean chev = mspC.lookingAt();
                    Matcher mOpen = chev ? mspC : mspA;
                    int openEnd = mOpen.end();
                    String attrs = mOpen.group(1);

                    Matcher close = Pattern.compile("(?i)</\\s*sparkle\\s*>")
                            .matcher(s).region(openEnd, s.length());

                    boolean found = close.find();

                    Op sp = new Op();
                    sp.kind = Kind.SPARKLE_TEXT;
                    parseSparkleArgs(attrs, sp);

                    sp.payload = found
                            ? s.substring(openEnd, close.start())
                            : s.substring(openEnd);

                    ops.add(sp);
                    s = found ? s.substring(close.end()) : "";
                    continue;
                }

                if (flickerAt) {
                    boolean chev = mfC.lookingAt();
                    Matcher mOpen = chev ? mfC : mfA;
                    int openEnd = mOpen.end();
                    String attrs = mOpen.group(1);

                    Matcher close = Pattern.compile("(?i)</\\s*flicker\\s*>")
                            .matcher(s).region(openEnd, s.length());

                    boolean found = close.find();

                    String inner = found
                            ? s.substring(openEnd, close.start())
                            : s.substring(openEnd);

                    Op fk = new Op();
                    fk.kind = Kind.FLICKER_TEXT;

                    // default + parse args
                    fk.flickerSpeed = 6.0F;
                    parseFlickerArgs(attrs, fk);

                    fk.payload  = inner;
                    fk.children = parseChildren(inner); // ✅ add this

                    ops.add(fk);
                    s = found ? s.substring(close.end()) : "";
                    continue;
                }


                if (glitchAt) {
                    boolean chev = mglC.lookingAt();
                    Matcher mOpen = chev ? mglC : mglA;
                    int openEnd = mOpen.end();
                    String attrs = mOpen.group(1);

                    Matcher close = Pattern.compile("(?i)</\\s*glitch\\s*>")
                            .matcher(s).region(openEnd, s.length());

                    boolean found = close.find();

                    Op gl = new Op();
                    gl.kind = Kind.GLITCH_TEXT;
                    parseGlitchArgs(attrs, gl);

                    gl.payload = found
                            ? s.substring(openEnd, close.start())
                            : s.substring(openEnd);

                    ops.add(gl);
                    s = found ? s.substring(close.end()) : "";
                    continue;
                }

                // ─────────────────────────────────────
                // PULSE (unchanged) — only if no other block tags matched at front
                // ─────────────────────────────────────
                if (!pulseAt) {
                    int end = s.indexOf('>');
                    if (end < 0) {
                        emitWithSimpleHex(s, ops);
                        s = "";
                        break;
                    }

                    // Emit the whole unknown/other tag chunk as literal text
                    emitWithSimpleHex(s.substring(0, end + 1), ops);
                    s = s.substring(end + 1);
                    continue;
                }


                boolean chevron = mpC.lookingAt();
                Matcher mOpen = chevron ? mpC : mpA;
                int openEnd = mOpen.end();
                String attrs = mOpen.group(1);
                Matcher closePref = (chevron ? TAG_PULSE_CLOSE_CHEV : TAG_PULSE_CLOSE)
                        .matcher(s).region(openEnd, s.length());
                Matcher closeOther = (chevron ? TAG_PULSE_CLOSE : TAG_PULSE_CLOSE_CHEV)
                        .matcher(s).region(openEnd, s.length());
                Op p = new Op();
                p.kind = HexFontRenderer.Kind.PULSE_HEX;
                parsePulseArgs(attrs, p);
                p.legacyFromTag = stylesFromAttrs(attrs);
                if (closePref.find()) {
                    String inner = s.substring(openEnd, closePref.start());
                    p.payload = inner;
                    p.children = parseChildren(inner);

                    ops.add(p);
                    s = s.substring(closePref.end());
                } else if (closeOther.find()) {
                    String inner = s.substring(openEnd);
                    p.payload = inner;
                    p.children = parseChildren(inner);

                    ops.add(p);
                    s = s.substring(closeOther.end());
                } else {
                    // No closing </pulse> yet (e.g., user is still typing in chat/preview).
                    // Treat the remainder as literal text so we don't crash on closeOther.start().
                    emitWithSimpleHex(s, ops);
                    s = "";
                }
            }
        }

        if (!s.isEmpty()) {
            emitWithSimpleHex(s, ops);
        }

        return ops;
    }

    private static String stripStrayAnimatedClosers(String s) {
        if (s != null && !s.isEmpty()) {
            // existing: strip grad / rainbow / pulse closers (angle + chevron)
            s = GRAD_CLOSE_ANY.matcher(s).replaceAll("");
            s = RBW_CLOSE_ANY.matcher(s).replaceAll("");
            s = PULSE_CLOSE_ANY.matcher(s).replaceAll("");

            // NEW: strip closers for our extra block tags
            s = s.replaceAll(
                    "(?i)</\\s*(wave|shake|zoom|rain|scroll|outline|shadow|sparkle|flicker|glitch|jitter|wobble)\\s*>",
                    ""
            );

            // NEW: strip stray hex close tags too
            s = s.replaceAll("(?i)</\\s*#\\s*>", "");

            // existing generic “dangling close tag at end of line” cleanup
            s = s.replaceAll("(?i)</[a-z]*\\s*$", "");
            return s;
        }
        return s;
    }

    private static void emitWithSimpleHex(String s, List<Op> out) {
        int i = 0;
        int n = s.length();

        while (i < n) {

            // 1) Skip gradient / rainbow / pulse closers silently
            Matcher m = GRAD_CLOSE_ANY.matcher(s).region(i, n);
            if (m.lookingAt()) {
                i = m.end();
                continue;
            }
            m = RBW_CLOSE_ANY.matcher(s).region(i, n);
            if (m.lookingAt()) {
                i = m.end();
                continue;
            }
            m = PULSE_CLOSE_ANY.matcher(s).region(i, n);
            if (m.lookingAt()) {
                i = m.end();
                continue;
            }

            // 2) NEW: standalone hex close tags → POP_HEX, no visible text
            Matcher mCloseA = TAG_HEX_CLOSE.matcher(s).region(i, n);
            Matcher mCloseC = TAG_HEX_CLOSE_CHEV.matcher(s).region(i, n);
            if (mCloseA.lookingAt() || mCloseC.lookingAt()) {
                Op pop = new Op();
                pop.kind = Kind.POP_HEX;
                out.add(pop);

                i = mCloseA.lookingAt() ? mCloseA.end() : mCloseC.end();
                continue;
            }

            // 3) Look for hex OPEN tags in the remaining text
            Matcher openA = TAG_HEX_ANY.matcher(s).region(i, n);
            Matcher openC = TAG_HEX_ANY_CHEV.matcher(s).region(i, n);
            boolean hasA = openA.find();
            boolean hasC = openC.find();

            if (!hasA && !hasC) {
                // No more hex opens: flush the rest as plain text (minus stray animated closers)
                String tail = stripStrayAnimatedClosers(s.substring(i));
                if (!tail.isEmpty()) {
                    Op t = new Op();
                    t.kind = Kind.TEXT;
                    t.payload = tail;
                    out.add(t);
                }
                break;
            }

            int aStart = hasA ? openA.start() : Integer.MAX_VALUE;
            int cStart = hasC ? openC.start() : Integer.MAX_VALUE;
            boolean pickChevron = cStart < aStart;
            int segStart = pickChevron ? cStart : aStart;

            // Emit any plain text BEFORE this hex tag
            if (segStart > i) {
                String chunk = stripStrayAnimatedClosers(s.substring(i, segStart));
                if (!chunk.isEmpty()) {
                    Op t = new Op();
                    t.kind = Kind.TEXT;
                    t.payload = chunk;
                    out.add(t);
                }
            }

            if (pickChevron) {
                // «#RRGGBBstyles»
                Matcher mOpen = openC;
                int afterOpen = mOpen.end();
                int rgb = parseHexRGB(mOpen.group(1));

                Op push = new Op();
                push.kind = Kind.PUSH_HEX;
                push.rgb = rgb;
                out.add(push);

                String legacy = stylesToLegacy(mOpen.group(2));
                if (!legacy.isEmpty()) {
                    Op tStyle = new Op();
                    tStyle.kind = Kind.TEXT;
                    tStyle.payload = legacy;
                    out.add(tStyle);
                }

                // If we find a matching chevron closer in THIS substring, treat body as plain text
                Matcher close = TAG_HEX_CLOSE_CHEV.matcher(s).region(afterOpen, n);
                if (close.find()) {
                    if (close.start() > afterOpen) {
                        Op t = new Op();
                        t.kind = Kind.TEXT;
                        t.payload = s.substring(afterOpen, close.start());
                        out.add(t);
                    }

                    Op pop = new Op();
                    pop.kind = Kind.POP_HEX;
                    out.add(pop);

                    i = close.end();
                } else {
                    // No closer here → keep hex active; remainder becomes TEXT, POP handled later
                    if (afterOpen < n) {
                        String chunk = s.substring(afterOpen);
                        if (!chunk.isEmpty()) {
                            Op t = new Op();
                            t.kind = Kind.TEXT;
                            t.payload = chunk;
                            out.add(t);
                        }
                    }
                    i = n;
                }

            } else {
                // <#RRGGBBstyles>
                Matcher mOpen = openA;
                int afterOpen = mOpen.end();
                int rgb = parseHexRGB(mOpen.group(1));

                Op push = new Op();
                push.kind = Kind.PUSH_HEX;
                push.rgb = rgb;
                out.add(push);

                String legacy = stylesToLegacy(mOpen.group(2));
                if (!legacy.isEmpty()) {
                    Op tStyle = new Op();
                    tStyle.kind = Kind.TEXT;
                    tStyle.payload = legacy;
                    out.add(tStyle);
                }

                Matcher close = TAG_HEX_CLOSE.matcher(s).region(afterOpen, n);
                if (close.find()) {
                    if (close.start() > afterOpen) {
                        Op t = new Op();
                        t.kind = Kind.TEXT;
                        t.payload = s.substring(afterOpen, close.start());
                        out.add(t);
                    }

                    Op pop = new Op();
                    pop.kind = Kind.POP_HEX;
                    out.add(pop);

                    i = close.end();
                } else {
                    // No closer here → keep hex active until a later </#> or end-of-line
                    if (afterOpen < n) {
                        Op t = new Op();
                        t.kind = Kind.TEXT;
                        t.payload = s.substring(afterOpen);
                        out.add(t);
                    }
                    i = n;
                }
            }
        }
    }

    static final class SnowCfg {
        float dens   = 0.35f;   // flakes per pixel-ish
        float spread = 2.2f;    // radius in "font heights"
        float speed  = 0.85f;   // fall speed
        float drift  = 1.1f;    // horizontal drift amplitude
        float fall   = 14f;     // vertical travel in px-ish
        float start  = 2f;      // start height above text
        float size   = 1.0f;    // flake scale
        float alpha  = 0.85f;   // flake alpha (0..1)

        String glyphs = "·*°★✧✫☪";  // choose chars
        int color = -1;          // -1 = inherit baseColor
        boolean useGrad = false; // don't auto-enable
        int[] grad = null;
        // parsed colors
        boolean gradScroll = false;
        float gradScrollSpeed = 0.25f;

        boolean useRainbow = false;
        float rbSpeed = 1.2f;   // hue speed
    }

    private static final class LegacyState {
        boolean bold;
        boolean italic;
        boolean underline;
        boolean strikethrough;
        boolean obfuscated;
        boolean obfOnce;
        Integer colorRgb;

        private LegacyState() {
        }

        void reset() {
            this.bold = this.italic = this.underline = this.strikethrough = this.obfuscated = this.obfOnce = false;
            this.colorRgb = null;
        }
    }

    private static enum Kind {
        TEXT,
        PUSH_HEX,
        POP_HEX,
        GRADIENT_MULTI,
        RAINBOW_TEXT,
        PULSE_HEX,
        WAVE_TEXT,
        SHAKE_TEXT,
        ZOOM_TEXT,
        RAIN_TEXT,
        SCROLL_TEXT,
        OUTLINE_TEXT,
        SHADOW_TEXT,
        SPARKLE_TEXT,
        FLICKER_TEXT,
        GLITCH_TEXT,
        JITTER_TEXT,
        WOBBLE_TEXT,
        LOOP_TEXT,
        SHOOTING_STAR_TEXT,
        SNOW_AURA;

        private Kind() {
        }
    }
    // Shadow/outline passes sometimes need to override per-glyph colors (grad/rbw)
    private boolean FORCE_FLAT_COLOR = false;
    private int FORCED_FLAT_COLOR = 0x000000;
    private boolean FORCE_COLOR_MUL = false;
    private float FORCED_COLOR_MUL = 1.0F;

    private static boolean FORCE_COLOR_SOLID = false;
    private static int FORCED_COLOR_SOLID = 0xFFFFFF;

    private int applyForcedColor(int rgb) {
        rgb &= 0xFFFFFF;

        // Solid override (outline/shadow) takes precedence
        if (FORCE_COLOR_SOLID) {
            rgb = (FORCED_COLOR_SOLID & 0xFFFFFF);
        }

        // Multiplier (pulse/flicker)
        if (FORCE_COLOR_MUL) {
            float m = FORCED_COLOR_MUL;

            int r = (rgb >> 16) & 255;
            int g = (rgb >> 8) & 255;
            int b = rgb & 255;

            r = Math.min(255, Math.max(0, Math.round(r * m)));
            g = Math.min(255, Math.max(0, Math.round(g * m)));
            b = Math.min(255, Math.max(0, Math.round(b * m)));

            rgb = (r << 16) | (g << 8) | b;
        }

        return rgb;
    }




    private static class Op {
        Kind kind;

        // existing fields
        String payload;
        int rgb;
        int[] stops;
        int cycles;
        float sat;
        float val;
        float phase;
        float speed;
        boolean scroll;
        int solid;
        float amp;
        boolean pulseOn;
        float pulseAmp;
        float pulseSpeed;
        String legacyFromTag;

        int[] scrollStops = null;
        int   scrollRgb   = -1;
        float scrollMix   = 0.5F;
        // motion for gradient text (wave / rain)
        boolean gradWave  = false;
        boolean gradRain  = false;
        float   gradWaveAmp   = 2.0F;
        float   gradWaveSpeed = 6.0F;
        float   gradRainAmp   = 4.0F;
        float   gradRainSpeed = 8.0F;
        // --- Snow aura config (for <snow ...>) ---
        public boolean snowOn = false;
        public float snowSpeed = 0.45F;     // falls/sec-ish
        public float snowFall  = 18.0F;     // how far it falls (px)
        public float snowStart = 14.0F;     // starts above text (px)
        public float snowSpread = 6.0F;     // horizontal padding around text (px)
        public float snowDrift = 0.35F;     // sideways drift amplitude
        public float snowDensity = 0.35F;   // 0..1 (lower = more spaced out)
        public int snowColor = -1;          // -1 = default (use white)
        public int[] snowGrad = null;       // optional gradient colors
        public float snowMix = 0.0F;      // 0..1 mix flakeColor toward white (optional)
        public SnowCfg snow;

        boolean snowInherit = true;
        boolean snowWave = false;
        boolean snowPulse = false;
        boolean snowFlicker = false;

        // inside Op

        public List<Op> children;
        // parsed ops for inner text
        String plain;                    // inner text stripped for width/flake band


        // ──────────────────────────────
        // NEW minimal fields so new Kinds don't break
        // ──────────────────────────────

        // outline color (optional)
        int outlineColor = 0x000000;     // default black

        // shadow color
        int shadowColor = 0x000000;      // default black

        // sparkle intensity (0–1)
        float sparkleIntensity = 1.0F;

        // sparkle star color (optional). If unset, stars inherit the per-char text color.
        int sparkleStarColor = -1;     // solid #RRGGBB
        int[] sparkleStarGrad = null;  // gradient stops for stars
        boolean sparkleStarRbw = false;// rainbow stars
        float sparkleStarSpeed = 1.0F; // scroll speed for star grad/rbw
        float sparkleStarSat   = 1.0F; // rainbow saturation
        float sparkleStarVal   = 1.0F; // rainbow value/brightness
        float sparkleStarCycles = 1.0F;// how many hue/grad cycles across the line


        // flicker speed (0–?)
        float flickerSpeed = 1.0F;

        // shooting star tuning
        float ssCycleSec = 3.0F;    // total cycle time (seconds)
        float ssGlowFrac = 0.22F;   // portion of cycle spent charging glow
        float ssFlightFrac = 0.28F; // portion spent flying
        float ssArc = 18.0F;        // arc height in pixels (upwards)
        float ssDist = 14.0F;       // horizontal travel in pixels
        float ssChance = 0.60F;     // chance per letter per cycle

        // explosion tuning (after flight)
        float ssExplodeFrac = 0.14F; // portion of cycle spent exploding
        float ssExplodeDist = 10.0F; // explosion scatter radius in pixels
        float ssExplodeGlow = 0.65F; // explosion glow strength (0..1)

        // glitch amount (0–5)
        float glitchAmount = 1.0F;

        // --- Rain overlay color source (for <rain ...>) ---
        public int   rainDropColor = -1;     // solid override, -1 = none
        public int[] rainDropGrad  = null;   // gradient override, null = none
        public boolean rainDropRainbow = false; // rainbow override

        // optional tuning (defaults are fine)
        public int   rainDropCycles = 3;
        public float rainDropSat    = 0.90F;
        public float rainDropVal    = 1.00F;
        public float rainDropSpeed  = 1.00F;
        public float rainDropPhase  = 0.00F;

        // ──────────────────────────────

        private Op() {
            // original defaults
            this.cycles     = 1;
            this.sat        = 1.0F;
            this.val        = 1.0F;
            this.phase      = 0.0F;
            this.speed      = 0.0F;
            this.scroll     = false;
            this.solid      = -1;
            this.amp        = 0.0F;
            this.pulseOn    = false;
            this.pulseAmp   = 0.0F;
            this.pulseSpeed = 1.0F;
            this.scrollStops = null;
            this.scrollRgb   = -1;
            this.scrollMix   = 0.5F;
            this.gradWave      = false;
            this.gradRain      = false;
            this.gradWaveAmp   = 2.0F;
            this.gradWaveSpeed = 6.0F;
            this.gradRainAmp   = 4.0F;
            this.gradRainSpeed = 8.0F;
            this.rgb        = -1;
        }
    }



    // ─────────────────────────────────────────────────────────────
    // Forge Mod List description fix (dev/runtime): delegate ONLY the
    // split-string path to vanilla so the description renders/wraps.
    // Does NOT change any Hex styles (chat/tooltips/etc remain custom).
    // ─────────────────────────────────────────────────────────────
    @Override
    public void drawSplitString(String str, int x, int y, int wrapWidth, int textColor) {
        if (isForgeModListLike() && this.base != null) {
            this.base.drawSplitString(fixSectionJunk(str), x, y, wrapWidth, textColor);
            return;
        }
        super.drawSplitString(str, x, y, wrapWidth, textColor);
    }

    @Override
    public int splitStringWidth(String str, int wrapWidth) {
        if (isForgeModListLike() && this.base != null) {
            return this.base.splitStringWidth(fixSectionJunk(str), wrapWidth);
        }
        return super.splitStringWidth(str, wrapWidth);
    }

}