// src/main/java/com/example/examplemod/core/HexCoremod.java
package com.example.examplemod.core;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import java.util.Map;

@IFMLLoadingPlugin.Name("HexColorCodesCore")
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.SortingIndex(1001)
@IFMLLoadingPlugin.TransformerExclusions({
        "com.example.examplemod.core"
})
public class HexCoremod implements IFMLLoadingPlugin {

    @Override
    public String[] getASMTransformerClass() {
        return new String[]{
                "com.example.examplemod.core.ChatLengthTransformer",
                "com.example.examplemod.core.chat.ServerChatLimitTransformer",
                "com.example.examplemod.core.chat.GuiNewChatTransformer",
                "com.example.examplemod.core.gui.GuiModListSparkleTransformer",
                "com.example.examplemod.core.SlotArmorWearableTransformer",


        };
    }



    @Override
    public String getModContainerClass() { return null; }

    @Override
    public String getSetupClass() { return null; }

    @Override
    public void injectData(Map<String, Object> data) {}


    @Override
    public String getAccessTransformerClass() { return null; }
}
