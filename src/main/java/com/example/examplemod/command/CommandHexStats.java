package com.example.examplemod.command;

import com.example.examplemod.server.HexPlayerStats;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;

public class CommandHexStats extends CommandBase {

    @Override
    public String getCommandName() {
        return "hexstats";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/hexstats";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0; // testing convenience; change to 2 if you want ops only
    }
    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true; // allow everyone (even deopped)
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        EntityPlayer p = (sender instanceof EntityPlayer) ? (EntityPlayer) sender : null;
        if (p == null) {
            sender.addChatMessage(new ChatComponentText("§c[Hex] This command must be run by a player."));
            return;
        }

        HexPlayerStats.Snapshot s = HexPlayerStats.snapshot(p);

        sender.addChatMessage(new ChatComponentText("§d[HexStats]§7 source=§f" + s.source
                + "§7 rel=§f" + s.release + "§7 curMulti=§f" + round2(s.currentMulti)));

        sender.addChatMessage(new ChatComponentText("§7STR §f" + s.str + "§8 (eff " + (long) s.getStatEffective("str") + ")"
                + "§7  DEX §f" + s.dex + "§8 (eff " + (long) s.getStatEffective("dex") + ")"));

        sender.addChatMessage(new ChatComponentText("§7CON §f" + s.con + "§8 (eff " + (long) s.getStatEffective("con") + ")"
                + "§7  WIL §f" + s.wil + "§8 (eff " + (long) s.getStatEffective("wil") + ")"));

        sender.addChatMessage(new ChatComponentText("§7MND §f" + s.mnd + "§8 (eff " + (long) s.getStatEffective("mnd") + ")"
                + "§7  SPI §f" + s.spi + "§8 (eff " + (long) s.getStatEffective("spi") + ")"));
    }

    private static String round2(double d) {
        long v = Math.round(d * 100.0D);
        return String.valueOf(v / 100.0D);
    }
}
