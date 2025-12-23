package com.example.examplemod.client;

import com.example.examplemod.ModConfig;
import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.IConfigElement;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;

import java.util.ArrayList;
import java.util.List;

public class ModConfigGui extends GuiConfig {

    public ModConfigGui(GuiScreen parent) {
        super(parent, buildElements(), "hexcolorcodes", false, false, "HexColorCodes Config");
    }

    private static List<IConfigElement> buildElements() {
        // Ensure ModConfig.load(...) ran in preInit so ModConfig.cfg is non-null
        List<IConfigElement> list = new ArrayList<IConfigElement>();
        if (ModConfig.cfg != null) {
            list.add(new ConfigElement(ModConfig.cfg.getCategory("general")));
            list.add(new ConfigElement(ModConfig.cfg.getCategory("visual")));
            list.add(new ConfigElement(ModConfig.cfg.getCategory("rainbow")));
            list.add(new ConfigElement(ModConfig.cfg.getCategory("color")));
        }
        return list;
    }
}
