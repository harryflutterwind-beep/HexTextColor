package com.example.examplemod.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.WorldSettings;

import java.util.List;
import java.util.Random;

/**
 * Forge 1.7.10 quick game mode command.
 * Register two instances:
 * - new CommandQuickGamemode("c", WorldSettings.GameType.CREATIVE, "§bCreative")
 * - new CommandQuickGamemode("s", WorldSettings.GameType.SURVIVAL, "§eSurvival")
 */
public class CommandQuickGamemode extends CommandBase {

    private static final Random RAND = new Random();

    // 10 animated gradient presets (different colors + scroll speeds)
    private static final String[] RAND_GRAD_OPEN = new String[] {
            "<grad #00FF7F #00C8FF scroll=0.35>",
            "<grad #00C8FF #B100FF scroll=0.35>",
            "<grad #FFD54F #FF6D00 scroll=0.30>",
            "<grad #FF5252 #FF00C8 scroll=0.40>",
            "<grad #7C4DFF #18FFFF scroll=0.25>",
            "<grad #76FF03 #FFEA00 scroll=0.50>",
            "<grad #40C4FF #69F0AE scroll=0.20>",
            "<grad #FF4081 #7C4DFF scroll=0.45>",
            "<grad #B2FF59 #40C4FF scroll=0.33>",
            "<grad #FF9100 #00E5FF scroll=0.38>"
    };

    private static String pickRandGradOpen() {
        return RAND_GRAD_OPEN[RAND.nextInt(RAND_GRAD_OPEN.length)];
    }

    private final String cmdName;
    private final WorldSettings.GameType mode;
    private final String coloredModeName;

    public CommandQuickGamemode(String cmdName, WorldSettings.GameType mode, String coloredModeName) {
        this.cmdName = cmdName;
        this.mode = mode;
        this.coloredModeName = coloredModeName;
    }

    @Override
    public String getCommandName() {
        return cmdName;
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/" + cmdName + " [player]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2; // same as vanilla /gamemode
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        EntityPlayerMP target;

        if (args.length >= 1) {
            target = getPlayer(sender, args[0]);
        } else {
            // Console has no "self"
            if (sender instanceof EntityPlayerMP) {
                target = (EntityPlayerMP) sender;
            } else {
                throw new WrongUsageException(getCommandUsage(sender));
            }
        }

        target.setGameType(mode);

        // Random gradient picked once per command call
        final String g = pickRandGradOpen();
        final String gc = "</grad>";

        // Feedback
        if (sender != target) {
            sender.addChatMessage(new ChatComponentText(
                    g + "Set " + target.getCommandSenderName() + " to " + coloredModeName + gc
            ));
        }

        target.addChatMessage(new ChatComponentText(
                g + "Game mode set to " + coloredModeName + gc
        ));
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, MinecraftServer.getServer().getAllUsernames());
        }
        return null;
    }
}
