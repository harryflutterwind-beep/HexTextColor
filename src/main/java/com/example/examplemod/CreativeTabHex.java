package com.example.examplemod;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.init.Items;

public class CreativeTabHex extends CreativeTabs {
    private Item icon;  // set after items are registered

    public CreativeTabHex() { super("hexmodtab"); }

    /** Call this from your @Mod preInit after you create/register the item. */
    public void setIcon(Item item) { this.icon = item; }

    @Override
    public Item getTabIconItem() {
        // Safe fallback so we never crash during early init
        return (icon != null) ? icon : Items.diamond;
    }

    @Override
    public String getTranslatedTabLabel() { return "<shadow><flicker amp=0.7 speed=1.4><wave amp=2.2 speed=6><grad #ff4fd8 #36d1ff #ffe66d #7cff6b scroll=0.22>Hex Items</grad></wave></flicker></shadow>"; }
}
