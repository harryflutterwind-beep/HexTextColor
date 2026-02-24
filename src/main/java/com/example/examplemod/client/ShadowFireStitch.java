package com.example.examplemod.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.util.IIcon;
import net.minecraftforge.client.event.TextureStitchEvent;

/**
 * Captures the Shadow Fire IIcons from the blocks texture atlas so we can use them
 * for custom entity overlays / particles without needing a block instance reference.
 */
@SideOnly(Side.CLIENT)
public final class ShadowFireStitch {

    public static IIcon SHADOW_FIRE_0;
    public static IIcon SHADOW_FIRE_1;
    public static IIcon BLACKFLAME;

    @SubscribeEvent
    public void onTextureStitch(TextureStitchEvent.Pre e) {
        if (e.map == null || e.map.getTextureType() != 0) return; // 0 = blocks atlas
        SHADOW_FIRE_0 = e.map.registerIcon("hexcolorcodes:shadow_fire_layer_0");
        SHADOW_FIRE_1 = e.map.registerIcon("hexcolorcodes:shadow_fire_layer_1");
        BLACKFLAME = e.map.registerIcon("hexcolorcodes:blackflame_16x512");
    }
}
