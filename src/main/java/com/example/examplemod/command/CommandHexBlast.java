// src/main/java/com/example/examplemod/command/CommandHexBlast.java
package com.example.examplemod.command;

import com.example.examplemod.entity.EntityHexBlast;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/**
 * /hexblast
 *
 * Sphere (default):
 *   /hexblast [radius] [life] [palette...] [damage]
 *
 * Beam:
 *   /hexblast beam [range] [life] [width] [palette...] [damage] size=1.0 interval=3
 *
 * Ki projectile:
 *   /hexblast ki [range] [life?] [speed] [radius] [palette...] [damage] size=1.0
 *
 * Palette forms:
 *   <hex>                                 (solid color)
 *   <mode 0..2>                           (uses current colors)
 *   <mode> <c0> <c1> <c2> <c3>            (gradient/rainbow using 4 colors)
 *
 * Extra flags:
 *   size=<mult>     (scales radius/width)
 *   interval=<n>    (beam only: damage tick interval)
 *   willmult=<f>    (damage scaling if you use will-based damage)
 *   willflat=<f>
 *   nodmg           (visual only)
 */
public class CommandHexBlast extends CommandBase {

    @Override
    public String getCommandName() {
        return "hexblast";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/hexblast [radius] [life] [hex|mode colors...] [damage]  |  /hexblast beam ...  |  /hexblast ki ...";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2; // OP
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayer)) {
            sender.addChatMessage(new ChatComponentText("Player only."));
            return;
        }

        EntityPlayer p = (EntityPlayer) sender;
        World w = p.worldObj;

        // -------- defaults --------
        boolean beamMode = false;
        boolean kiMode = false;

        float sphereRadius = 6.0f;
        float beamRange = 28.0f;
        float beamWidth = 1.0f;
        int life = 18;

        // ki projectile defaults
        float kiSpeed = 1.35f;
        float kiRadius = 0.45f;

        // colors
        byte mode = 1; // 0 solid, 1 gradient, 2 rainbow slices
        int c0 = 0x8B3DFF;
        int c1 = 0x00D4FF;
        int c2 = 0xFFFFFF;
        int c3 = 0xFFB84D;

        // damage controls
        boolean noDmg = false;
        float damageOverride = 0f; // 0 = use will scaling (if your entity supports it)
        float willMult = 12.0f;
        float willFlat = 500.0f;
        int interval = 3; // beam damage pulses

        float sizeMult = 1.0f;

        try {
            // -------- mode token (beam/ki) --------
            int i = 0;
            if (args.length > 0) {
                String sub = safeLower(args[0]);
                if ("beam".equals(sub) || "kame".equals(sub) || "kamehameha".equals(sub)) {
                    beamMode = true;
                    i = 1;
                } else if ("ki".equals(sub) || "proj".equals(sub) || "projectile".equals(sub)) {
                    kiMode = true;
                    i = 1;
                }
            }

            // -------- scan key=values + flags first (order independent) --------
            for (int k = 0; k < args.length; k++) {
                String a = args[k];
                if (a == null) continue;
                String al = safeLower(a);

                if ("nodmg".equals(al) || "noDmg".equals(a)) noDmg = true;

                int eq = a.indexOf('=');
                if (eq > 0) {
                    String key = safeLower(a.substring(0, eq).trim());
                    String val = a.substring(eq + 1).trim();

                    if ("size".equals(key)) sizeMult = parseFloatSafe(val, sizeMult);
                    else if ("interval".equals(key)) interval = parseIntSafe(val, interval);
                    else if ("willmult".equals(key) || "willx".equals(key)) willMult = parseFloatSafe(val, willMult);
                    else if ("willflat".equals(key) || "willadd".equals(key)) willFlat = parseFloatSafe(val, willFlat);
                }
            }

            // -------- positional parsing (starts at i) --------
            // We only consume tokens that are not key= and not flags.
            // This keeps it flexible.
            // Build a compact list of positional tokens:
            java.util.ArrayList pos = new java.util.ArrayList();
            for (int k = i; k < args.length; k++) {
                String a = args[k];
                if (a == null) continue;
                if (a.indexOf('=') >= 0) continue;
                if ("nodmg".equalsIgnoreCase(a)) continue;
                pos.add(a);
            }

            // Helper to get token at index
            java.util.List plist = pos;

            // -------- parse core numbers by mode --------
            int cursor = 0;
            if (beamMode) {
                if (plist.size() > cursor && looksLikeNumber((String) plist.get(cursor))) {
                    beamRange = parseFloatSafe((String) plist.get(cursor), beamRange);
                    cursor++;
                }
                if (plist.size() > cursor && looksLikeNumber((String) plist.get(cursor))) {
                    life = parseIntSafe((String) plist.get(cursor), life);
                    cursor++;
                }
                if (plist.size() > cursor && looksLikeNumber((String) plist.get(cursor))) {
                    beamWidth = parseFloatSafe((String) plist.get(cursor), beamWidth);
                    cursor++;
                }
            } else if (kiMode) {
                if (plist.size() > cursor && looksLikeNumber((String) plist.get(cursor))) {
                    beamRange = parseFloatSafe((String) plist.get(cursor), beamRange);
                    cursor++;
                }

                // life is optional for ki. If not provided, derive from range/speed later.
                boolean lifeProvided = false;
                if (plist.size() > cursor && looksLikeNumber((String) plist.get(cursor))) {
                    life = parseIntSafe((String) plist.get(cursor), life);
                    cursor++;
                    lifeProvided = true;
                }

                if (plist.size() > cursor && looksLikeNumber((String) plist.get(cursor))) {
                    kiSpeed = parseFloatSafe((String) plist.get(cursor), kiSpeed);
                    cursor++;
                }
                if (plist.size() > cursor && looksLikeNumber((String) plist.get(cursor))) {
                    kiRadius = parseFloatSafe((String) plist.get(cursor), kiRadius);
                    cursor++;
                }

                if (!lifeProvided) {
                    float sp = Math.max(0.10f, kiSpeed);
                    life = Math.max(10, (int) Math.ceil(beamRange / sp) + 6);
                }
            } else {
                // sphere
                if (plist.size() > cursor && looksLikeNumber((String) plist.get(cursor))) {
                    sphereRadius = parseFloatSafe((String) plist.get(cursor), sphereRadius);
                    cursor++;
                }
                if (plist.size() > cursor && looksLikeNumber((String) plist.get(cursor))) {
                    life = parseIntSafe((String) plist.get(cursor), life);
                    cursor++;
                }
            }

            // -------- parse palette (mode + 4 colors OR single color OR mode only) --------
            if (plist.size() > cursor) {
                String tok = (String) plist.get(cursor);

                if (looksLikeHexColor(tok)) {
                    // solid color
                    mode = 0;
                    c0 = parseHexColor(tok, c0);
                    cursor++;
                } else if (looksLikeNumber(tok) && (cursor + 4) < plist.size()
                        && looksLikeHexColor((String) plist.get(cursor + 1))
                        && looksLikeHexColor((String) plist.get(cursor + 2))
                        && looksLikeHexColor((String) plist.get(cursor + 3))
                        && looksLikeHexColor((String) plist.get(cursor + 4))) {

                    mode = clampMode(parseIntSafe(tok, mode));
                    c0 = parseHexColor((String) plist.get(cursor + 1), c0);
                    c1 = parseHexColor((String) plist.get(cursor + 2), c1);
                    c2 = parseHexColor((String) plist.get(cursor + 3), c2);
                    c3 = parseHexColor((String) plist.get(cursor + 4), c3);
                    cursor += 5;

                } else if (looksLikeNumber(tok)) {
                    mode = clampMode(parseIntSafe(tok, mode));
                    cursor++;
                }
            }

            // -------- optional damage override at the end --------
            if (plist.size() > cursor && looksLikeNumber((String) plist.get(cursor))) {
                damageOverride = parseFloatSafe((String) plist.get(cursor), damageOverride);
                cursor++;
            }

            // -------- spawn --------
            Vec3 look = p.getLookVec();
            double sx = p.posX + look.xCoord * 1.4;
            double sy = p.posY + p.getEyeHeight() - 0.1 + look.yCoord * 1.4;
            double sz = p.posZ + look.zCoord * 1.4;

            EntityHexBlast b = new EntityHexBlast(w);
            b.ownerId = p.getEntityId();

            // common
            b.lifeTicks = life;
            b.colorMode = mode;
            b.color = c0;
            b.color1 = c1;
            b.color2 = c2;
            b.color3 = c3;

            b.doDamage = !noDmg;
            b.maxDamage = damageOverride;
            b.willDamageMult = willMult;
            b.willDamageFlat = willFlat;

            if (beamMode) {
                b.shape = EntityHexBlast.SHAPE_BEAM;
                b.beamRange = beamRange;
                b.beamWidth = beamWidth * sizeMult;
                b.beamDamageInterval = Math.max(1, interval);
                b.hitOnce = false;

                // Keep the origin close to the player
                b.setPosition(sx, sy, sz);
                // Store initial direction; renderer/server can also pull owner look if implemented.
                b.setDirection((float) look.xCoord, (float) look.yCoord, (float) look.zCoord);

                b.syncToClients();
                w.spawnEntityInWorld(b);

                sender.addChatMessage(new ChatComponentText(
                        "§a[HexBlast] Beam §7range=" + beamRange +
                                " width=" + beamWidth +
                                " size=" + sizeMult +
                                " life=" + life +
                                " interval=" + interval +
                                " mode=" + mode +
                                (noDmg ? " §8(nodmg)" : " §7will=(" + willMult + "x+" + willFlat + ")") +
                                (damageOverride > 0f ? (" §7dmg=" + damageOverride) : "")
                ));
            } else if (kiMode) {
                b.shape = EntityHexBlast.SHAPE_PROJECTILE;
                b.beamRange = beamRange; // reuse as projectile range cap
                b.setProjectileParams(kiSpeed, kiRadius * sizeMult);
                b.hitOnce = true;

                b.setPosition(sx, sy, sz);
                b.setDirection((float) look.xCoord, (float) look.yCoord, (float) look.zCoord);

                b.syncToClients();
                w.spawnEntityInWorld(b);

                sender.addChatMessage(new ChatComponentText(
                        "§a[HexBlast] Ki §7range=" + beamRange +
                                " speed=" + kiSpeed +
                                " radius=" + (kiRadius * sizeMult) +
                                " life=" + life +
                                " mode=" + mode +
                                (noDmg ? " §8(nodmg)" : " §7will=(" + willMult + "x+" + willFlat + ")") +
                                (damageOverride > 0f ? (" §7dmg=" + damageOverride) : "")
                ));
            } else {
                b.shape = EntityHexBlast.SHAPE_SPHERE;
                b.maxRadius = sphereRadius * sizeMult;
                b.hitOnce = true;

                b.setPosition(sx, sy, sz);
                b.setDirection((float) look.xCoord, (float) look.yCoord, (float) look.zCoord);

                b.syncToClients();
                w.spawnEntityInWorld(b);

                sender.addChatMessage(new ChatComponentText(
                        "§a[HexBlast] Sphere §7r=" + sphereRadius +
                                " size=" + sizeMult +
                                " maxR=" + (sphereRadius * sizeMult) +
                                " life=" + life +
                                " mode=" + mode +
                                (noDmg ? " §8(nodmg)" : " §7will=(" + willMult + "x+" + willFlat + ")") +
                                (damageOverride > 0f ? (" §7dmg=" + damageOverride) : "")
                ));
            }

        } catch (Throwable t) {
            sender.addChatMessage(new ChatComponentText("§c[HexBlast] Error: " + t));
            sender.addChatMessage(new ChatComponentText("§7(If you're on a dedicated server, make sure the server jar is updated too.)"));
        }
    }

    // ---------------- helpers ----------------

    private static String safeLower(String s) {
        return s == null ? "" : s.toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean looksLikeNumber(String s) {
        if (s == null) return false;
        s = s.trim();
        if (s.length() == 0) return false;
        // allow leading +/-
        int i = 0;
        char c0 = s.charAt(0);
        if (c0 == '+' || c0 == '-') i = 1;
        boolean seenDigit = false;
        boolean seenDot = false;
        for (; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') { seenDigit = true; continue; }
            if (c == '.' && !seenDot) { seenDot = true; continue; }
            return false;
        }
        return seenDigit;
    }

    private static float parseFloatSafe(String s, float def) {
        try { return Float.parseFloat(s.trim()); } catch (Throwable t) { return def; }
    }

    private static int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Throwable t) { return def; }
    }

    private static byte clampMode(int v) {
        if (v < 0) v = 0;
        if (v > 2) v = 2;
        return (byte) v;
    }

    private static boolean looksLikeHexColor(String s) {
        if (s == null) return false;
        s = s.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
        if (!(s.length() == 3 || s.length() == 6)) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= '0' && c <= '9') ||
                    (c >= 'a' && c <= 'f') ||
                    (c >= 'A' && c <= 'F');
            if (!ok) return false;
        }
        return true;
    }

    private static int parseHexColor(String raw, int def) {
        try {
            String s = raw.trim();
            if (s.startsWith("#")) s = s.substring(1);
            if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);

            if (s.length() == 3) {
                char r = s.charAt(0), g = s.charAt(1), b = s.charAt(2);
                s = "" + r + r + g + g + b + b;
            }
            if (s.length() != 6) return def;
            return Integer.parseInt(s, 16) & 0xFFFFFF;
        } catch (Throwable t) {
            return def;
        }
    }
}
