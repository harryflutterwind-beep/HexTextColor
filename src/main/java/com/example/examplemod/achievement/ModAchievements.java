package com.example.examplemod.achievement;

import com.example.examplemod.ExampleMod;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.Achievement;
import net.minecraftforge.common.AchievementPage;

public class ModAchievements {

    public static Achievement HEX_START;
    public static Achievement FIRST_SOCKET;
    public static Achievement ORB_MASTER;

    public static AchievementPage HEX_PAGE;

    private static ItemStack getStarterIcon() {
        if (ExampleMod.TAB_ICON_ITEM != null) {
            return new ItemStack(ExampleMod.TAB_ICON_ITEM);
        }
        return new ItemStack(Blocks.enchanting_table);
    }

    public static void init() {
        // id, internalName, x, y, icon, parent
        //p_i45302_1_: = Achievement name
        //p_i45302_2_: = Achievement Description
        HEX_START = new Achievement(
                "achievement.hex_welcome",
                "hex_welcome",
                0, 0,
                getStarterIcon(),
                null
        ).registerStat();

        FIRST_SOCKET = new Achievement(
                "achievement.first_socket",
                "first_socket",
                2, 0,
                new ItemStack(Blocks.diamond_block),
                HEX_START
        ).registerStat();

        ORB_MASTER = new Achievement(
                "achievement.orb_master",
                "orb_master",
                4, 1,
                new ItemStack(Blocks.beacon),
                FIRST_SOCKET
        ).registerStat();

        HEX_PAGE = new AchievementPage("Hex Socket", HEX_START, FIRST_SOCKET, ORB_MASTER);
        AchievementPage.registerAchievementPage(HEX_PAGE);
    }
}
