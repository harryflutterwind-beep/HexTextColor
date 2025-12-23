// src/main/java/com/example/examplemod/item/ItemTabIcon.java
package com.example.examplemod.item;

import net.minecraft.item.Item;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ItemTabIcon extends Item {
    public ItemTabIcon() {
        setMaxStackSize(1);
        setUnlocalizedName("hex_tab_icon"); // <= lowercase, no spaces
        setCreativeTab(null);               // hidden from tabs
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerIcons(net.minecraft.client.renderer.texture.IIconRegister reg) {
        this.itemIcon = reg.registerIcon("hexcolorcodes:hex_tab_icon"); // MODID:texture
        System.out.println("[HexTab] registered icon hexcolorcodes:hex_tab_icon");
    }
}
