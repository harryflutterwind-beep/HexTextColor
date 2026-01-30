package com.example.examplemod.command;

import com.example.examplemod.server.HexPlayerStats;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;

public class CommandHexScale extends CommandBase {

    @Override
    public String getCommandName() {
        return "hexscale";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/hexscale <str|dex|con|wil|mnd|spi> <baseAdd> <statMult> [release] [sqrtMulti]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0; // testing convenience
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        EntityPlayer p = (sender instanceof EntityPlayer) ? (EntityPlayer) sender : null;
        if (p == null) {
            sender.addChatMessage(new ChatComponentText("§c[Hex] This command must be run by a player."));
            return;
        }

        if (args == null || args.length < 3) {
            sender.addChatMessage(new ChatComponentText("§eUsage: " + getCommandUsage(sender)));
            sender.addChatMessage(new ChatComponentText("§7Example: §f/hexscale dex 2500 0.15 true true"));
            return;
        }

        String stat = args[0];
        double baseAdd = parseD(sender, args[1], 2500D);
        double mult = parseD(sender, args[2], 0.15D);

        boolean applyRelease = (args.length >= 4) ? parseB(args[3], true) : true;
        boolean sqrtMulti    = (args.length >= 5) ? parseB(args[4], true) : true;

        HexPlayerStats.Snapshot snap = HexPlayerStats.snapshot(p);

        double val = HexPlayerStats.scaleFromStat(p, stat, baseAdd, mult, applyRelease, sqrtMulti);

        sender.addChatMessage(new ChatComponentText("§b[HexScale]§7 stat=§f" + stat
                + "§7 baseAdd=§f" + trim(baseAdd)
                + "§7 mult=§f" + trim(mult)
                + "§7 rel=§f" + (applyRelease ? snap.release : 0)
                + "§7 sqrtMulti=§f" + sqrtMulti));

        sender.addChatMessage(new ChatComponentText("§7raw=" + snap.getStat(stat)
                + "§7 eff=" + (long) snap.getStatEffective(stat)
                + "§7 curMulti=" + round2(snap.currentMulti)
                + "§a => scaled=" + (long) val));
    }

    private static double parseD(ICommandSender sender, String s, double def) {
        try { return Double.parseDouble(s); } catch (Throwable t) {
            sender.addChatMessage(new ChatComponentText("§c[HexScale] Bad number: " + s + " (using " + def + ")"));
            return def;
        }
    }

    private static boolean parseB(String s, boolean def) {
        if (s == null) return def;
        if ("true".equalsIgnoreCase(s) || "t".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s)) return true;
        if ("false".equalsIgnoreCase(s) || "f".equalsIgnoreCase(s) || "0".equals(s) || "no".equalsIgnoreCase(s)) return false;
        return def;
    }

    private static String round2(double d) {
        long v = Math.round(d * 100.0D);
        return String.valueOf(v / 100.0D);
    }

    private static String trim(double d) {
        if (Math.abs(d - Math.round(d)) < 1e-9) return String.valueOf((long) Math.round(d));
        return String.valueOf(d);
    }
}
