// src/main/java/com/example/examplemod/dragon/DragonCommand.java
package com.example.examplemod.dragon;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DragonCommand extends CommandBase {

    @Override public String getCommandName() { return "dragon"; }
    @Override public int getRequiredPermissionLevel() { return 2; } // OP only

    @Override
    public String getCommandUsage(ICommandSender s) {
        return "/dragon respawn [x y z] [dim] | /dragon stats [hp <amt> | dmgmult <x>] [dim] | /dragon killall [dim]";
    }

    @Override
    public void processCommand(ICommandSender s, String[] a) {
        if (a.length < 1) { sendHelp(s); return; }
        String sub = a[0].toLowerCase();

        if ("respawn".equals(sub)) { handleRespawn(s, a); return; }
        if ("stats".equals(sub))   { handleStats(s, a);   return; }

        if ("killall".equals(sub)) {
            int dim;
            if (a.length >= 2) dim = parseInt(s, a[1]);
            else if (s instanceof EntityPlayerMP) dim = ((EntityPlayerMP)s).worldObj.provider.dimensionId;
            else dim = 1;

            World w = getWorldForDim(dim);
            if (w == null) { say(s, "§cInvalid dim: " + dim); return; }

            int killed = 0;
            for (Object o : (List) w.loadedEntityList) {
                if (o instanceof net.minecraft.entity.boss.EntityDragon) {
                    net.minecraft.entity.boss.EntityDragon d = (net.minecraft.entity.boss.EntityDragon) o;
                    try { d.attackEntityFrom(net.minecraft.util.DamageSource.outOfWorld, Float.MAX_VALUE); } catch (Throwable ignore) {}
                    d.setDead();
                    killed++;
                }
            }
            say(s, "§aKilled §e" + killed + "§a Ender Dragon(s) in dim §b" + dim + "§a.");
            return;
        }

        say(s, "§cUnknown subcommand. Usage:§r " + getCommandUsage(s));
    }

    // ---------- colorful help (uses only § codes + a <grad> title) ----------
    private static void sendHelp(ICommandSender s){
        // Title stays gradient (safe with our formatter)
        say(s, "<grad #22D3EE #06B6D4 #8B5CF6>◆ Dragon Command</grad>  §7(builder flow)");

        // Lines use vanilla § colors (no <#...> so no stray </#>)
        // Bullets / icons
        final String bullet = "§b»§r ";

        // /dragon stats (bright blue as requested)
        say(s, bullet + "§b/dragon stats§r  §3hp§r §a(<amt>)§r  §7|§r  §3dmgmult§r §e(×)§r  §7→ stage values for current dimension");

        // /dragon show / /dragon clear
        say(s, bullet + "§b/dragon show§r  §7→ view staged  §b•§r  §b/dragon clear§r  §7→ clear staged");

        // /dragon respawn
        say(s, bullet + "§b/dragon respawn§r §7[§3custom§7|§3above§7] [x y z] [§3hp§r §a(<amt>)§7] [§3dmgmult§r §e(×)§7]");
        say(s, "   §b•§r §fcustom/above§r §7(no coords) → spawns §f+50y§7 above you");
        say(s, "   §b•§r §7inline §fhp/dmg§7 override staged values");

        // /dragon killall
        say(s, bullet + "§b/dragon killall§r §7→ remove all Ender Dragons in your current dimension");
    }

    // ---------- handlers ----------
    private void handleRespawn(ICommandSender s, String[] a) {
        double x = 0.5, y = 80.0, z = 0.5;
        int dim = 1;
        boolean useAbove = false;
        int arg = 1;

        if (a.length >= 2 && "above".equalsIgnoreCase(a[1])) { useAbove = true; arg = 2; }

        if (a.length >= arg + 3) {
            x = parseDouble(s, a[arg]);
            y = parseDouble(s, a[arg + 1]);
            z = parseDouble(s, a[arg + 2]);
            if (a.length >= arg + 4) dim = parseInt(s, a[arg + 3]);
        } else if (s instanceof EntityPlayerMP) {
            EntityPlayerMP p = (EntityPlayerMP) s;
            dim = p.worldObj.provider.dimensionId;
            if (useAbove) { x = p.posX; y = p.posY + 50.0; z = p.posZ; }
        }

        World w = getWorldForDim(dim);
        if (w == null) { say(s, "§cInvalid dim: " + dim); return; }

        DragonUtils.spawnDragon(w, x, y, z);
        say(s, String.format("§aSpawned Ender Dragon at §f(%.1f, %.1f, %.1f) §7in dim §e%d§7.", x, y, z, dim));
    }

    private void handleStats(ICommandSender s, String[] a) {
        int dim;
        if (a.length >= 4) dim = parseInt(s, a[a.length - 1]);
        else if (a.length == 2) {
            try { dim = parseInt(s, a[1]); }
            catch (Exception ex) { dim = (s instanceof EntityPlayerMP) ? ((EntityPlayerMP)s).dimension : 1; }
        } else dim = (s instanceof EntityPlayerMP) ? ((EntityPlayerMP)s).dimension : 1;

        World w = getWorldForDim(dim);
        if (w == null) { say(s, "§cInvalid dim: " + dim); return; }

        if (a.length == 1 || (a.length == 2 && isInteger(a[1]))) {
            float hp = DragonUtils.dataFor(w).effectiveHp;
            float dm = DragonUtils.dataFor(w).outgoingDamageMult;
            say(s, String.format("§9[Dragon §7dim %d§9] §f effectiveHp=§e%.2f§f, dmgMult=§e%.3f", dim, hp, dm));
            return;
        }

        if (a.length >= 3) {
            String stat = a[1].toLowerCase();
            if ("hp".equals(stat)) {
                float hp = (float) parseDouble(s, a[2]); if (hp < 1f) hp = 1f;
                DragonUtils.dataFor(w).effectiveHp = hp; DragonUtils.dataFor(w).markDirty();
                say(s, "§aDragon effective HP set to §e" + hp + " §7(dim " + dim + ")");
                return;
            }
            if ("dmgmult".equals(stat)) {
                float m = (float) parseDouble(s, a[2]); if (m < 0f) m = 0f;
                DragonUtils.dataFor(w).outgoingDamageMult = m; DragonUtils.dataFor(w).markDirty();
                say(s, "§aDragon outgoing damage multiplier set to §e" + m + " §7(dim " + dim + ")");
                return;
            }
        }
        sendHelp(s);
    }

    // ---------- tab completion ----------
    @Override @SuppressWarnings("rawtypes")
    public List addTabCompletionOptions(ICommandSender s, String[] a) {
        if (a.length == 1) return getListOfStringsMatchingLastWord(a, "respawn", "stats", "killall");
        if (a.length == 2 && "stats".equalsIgnoreCase(a[0]))
            return getListOfStringsMatchingLastWord(a, "hp", "dmgmult", "0", "1", "-1");
        if (a.length == 2 && "respawn".equalsIgnoreCase(a[0]))
            return getListOfStringsMatchingLastWord(a, "above");
        if (a.length == 3 && "stats".equalsIgnoreCase(a[0]) && !"hp".equalsIgnoreCase(a[1]) && !"dmgmult".equalsIgnoreCase(a[1]))
            return getListOfStringsMatchingLastWord(a, "0", "1", "-1");
        if ((a.length == 4 && "stats".equalsIgnoreCase(a[0]))
                || (a.length == 5 && "respawn".equalsIgnoreCase(a[0]))
                || (a.length == 2 && "killall".equalsIgnoreCase(a[0])))
            return getListOfStringsMatchingLastWord(a, "0", "1", "-1");
        return null;
    }

    // ---------- helpers ----------
    private static boolean isInteger(String s){ try { Integer.parseInt(s); return true; } catch (Exception e) { return false; } }

    private static World getWorldForDim(int dim) {
        for (World w : MinecraftServer.getServer().worldServers)
            if (w != null && w.provider.dimensionId == dim) return w;
        return null;
    }

    /** Server-safe pretty print. We keep the formatter for other uses, but help lines use only § codes. */
    private static void say(ICommandSender s, String raw){
        s.addChatMessage(new ChatComponentText(formatInlineServer(raw)));
    }

    // ───────── minimal inline formatter (safe for <grad>, ignores everything else) ─────────
    // We *only* touch §#RGB / §#RRGGBB when followed by legacy style flags, to support other messages if you use them.
    private static final Pattern P_SEC6 = Pattern.compile("(?i)§#([0-9a-f]{6})([lmonkr]+)");
    private static final Pattern P_SEC3 = Pattern.compile("(?i)§#([0-9a-f]{3})([lmonkr]+)");

    private static String stylesToLegacy(String flags){
        StringBuilder sb = new StringBuilder(flags.length() * 2);
        for (int i = 0; i < flags.length(); i++){
            char c = Character.toLowerCase(flags.charAt(i));
            if ("lmonkr".indexOf(c) >= 0) sb.append('§').append(c);
        }
        return sb.toString();
    }
    private static String expandRGB3(String h){ // ABC -> AABBCC
        h = h.toUpperCase();
        return ""+h.charAt(0)+h.charAt(0)+h.charAt(1)+h.charAt(1)+h.charAt(2)+h.charAt(2);
    }

    private static String formatInlineServer(String raw){
        if (raw == null || raw.isEmpty()) return "";
        String s = raw.replace('&', '§');

        // §#RGB → <#RRGGBB>…</#>
        Matcher m3 = P_SEC3.matcher(s);
        StringBuffer b3 = new StringBuffer();
        while (m3.find()){
            String hex = expandRGB3(m3.group(1));
            String legacy = stylesToLegacy(m3.group(2));
            m3.appendReplacement(b3, "<#" + hex + ">" + Matcher.quoteReplacement(legacy) + "</#>");
        }
        m3.appendTail(b3);
        s = b3.toString();

        // §#RRGGBB → <#RRGGBB>…</#>
        Matcher m6 = P_SEC6.matcher(s);
        StringBuffer b6 = new StringBuffer();
        while (m6.find()){
            String hex = m6.group(1).toUpperCase();
            String legacy = stylesToLegacy(m6.group(2));
            m6.appendReplacement(b6, "<#" + hex + ">" + Matcher.quoteReplacement(legacy) + "</#>");
        }
        m6.appendTail(b6);
        return s;
    }
}
