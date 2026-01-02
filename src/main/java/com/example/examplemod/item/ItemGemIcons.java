// src/main/java/com/example/examplemod/item/ItemGemIcons.java
package com.example.examplemod.item;

import com.example.examplemod.ExampleMod;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import java.util.List;

/**
 * One item with many subtypes (metadata) that point at your textures in:
 *   assets/hexcolorcodes/textures/gems/*.png
 *
 * Texture names are registered as: "hexcolorcodes:gems/<file>".
 */
public class ItemGemIcons extends Item {

    /** Texture base names (no .png). Meta index == array index. */
    public static final String[] VARIANTS = new String[] {
            "orb_gem_blue_frost_64",
            "orb_gem_blue_frost_64_anim_8f",
            "orb_gem_chaoticSphere_64",
            "orb_gem_chaoticSphere_anim_8f_64x516",
            "orb_gem_fractured_64",
            "orb_gem_fractured_anim_8f_64x516",
            "orb_gem_gold_solar_64",
            "orb_gem_gold_solar_64_anim_8f",
            "orb_gem_green_nature_64",
            "orb_gem_green_nature_64_anim_8f",
            "orb_gem_light_64",
            "orb_gem_light_anim_8f_64x516",
            "orb_gem_negative_64",
            "orb_gem_negative_anim_8f_64x516",
            "orb_gem_orange_inferno_64",
            "orb_gem_orange_inferno_64_anim_8f",
            "orb_gem_rainbow_energized_64",
            "orb_gem_rainbow_energized_anim_8f_64x516",
            "orb_gem_swirly_64",
            "orb_gem_swirly_loop",
            "orb_gem_teal_aether_64",
            "orb_gem_teal_aether_64_anim_8f",
            "orb_gem_violet_void_64",
            "orb_gem_violet_void_64_anim_8f",
            "pill_dark_fire_face_64",
            "pill_dark_fire_face_64_anim",
            "pill_fire_animated_64_anim",
            "pill_fire_textured_64"
    };

    @SideOnly(Side.CLIENT)
    private IIcon[] icons;

    public ItemGemIcons() {
        super();
        setHasSubtypes(true);
        setMaxDamage(0);
        setMaxStackSize(64);
        setUnlocalizedName("hex_gem");
        setCreativeTab(ExampleMod.HEX_TAB);
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        int meta = (stack == null) ? 0 : stack.getItemDamage();
        if (meta < 0) meta = 0;
        if (meta >= VARIANTS.length) meta = VARIANTS.length - 1;
        return super.getUnlocalizedName() + "." + VARIANTS[meta];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister reg) {
        icons = new IIcon[VARIANTS.length];
        for (int i = 0; i < VARIANTS.length; i++) {
            // Textures live at: assets/hexcolorcodes/textures/gems/<name>.png
            icons[i] = reg.registerIcon("hexcolorcodes:gems/" + VARIANTS[i]);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int meta) {
        if (icons == null || icons.length == 0) return itemIcon;
        if (meta < 0) meta = 0;
        if (meta >= icons.length) meta = icons.length - 1;
        return icons[meta];
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        if (tab != this.getCreativeTab()) return;
        for (int i = 0; i < VARIANTS.length; i++) {
            list.add(new ItemStack(item, 1, i));
        }
    }
}
