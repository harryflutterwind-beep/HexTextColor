package com.example.examplemod.client;

import cpw.mods.fml.client.IModGuiFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import java.util.Set;

public class ModGuiFactory implements IModGuiFactory {

    @Override
    public void initialize(Minecraft minecraftInstance) {
        // nothing
    }

    @Override
    public Class<? extends GuiScreen> mainConfigGuiClass() {
        return ModConfigGui.class;
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return null; // no in-game (pause menu) runtime options
    }

    @Override
    public RuntimeOptionGuiHandler getHandlerFor(RuntimeOptionCategoryElement element) {
        return null; // no runtime handlers
    }
}
