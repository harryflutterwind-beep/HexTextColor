package com.example.examplemod.achievement;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import net.minecraft.entity.player.EntityPlayerMP;

public class AchievementLoginHandler {

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) event.player;
        // Uses vanilla achievement awarding path.
        // If /gamerule announceAchievements is true, Minecraft will broadcast it like vanilla.
        player.triggerAchievement(ModAchievements.HEX_START);
    }
}
