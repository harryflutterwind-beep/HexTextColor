// src/main/java/com/example/examplemod/item/ItemGemIcons.java
package com.example.examplemod.item;

import com.example.examplemod.ExampleMod;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.IIcon;

import java.util.List;

/**
 * One item with many subtypes (metadata) that point at your textures in:
 *   assets/hexcolorcodes/textures/gems/*.png
 *
 * Texture names are registered as: "hexcolorcodes:gems/<file>".
 */
public class ItemGemIcons extends Item {

    // These metas are the ONLY ones that should show a real Effect line for now.
    // Keep in sync with HexOrbRoller.
    private static final int META_SWIRLY_FLAT = 18;
    private static final int META_SWIRLY_ANIM = 19;
    private static final int META_PILL_FIRE_ANIM = 26;
    private static final int META_PILL_FIRE_FLAT = 27;

    // Stat-orb metas (keep in sync with HexOrbRoller profiles)
    private static final int META_CHAOTIC_FLAT = 2;
    private static final int META_CHAOTIC_MULTI = 3;
    private static final int META_FRACTURED_FLAT = 4;
    private static final int META_FRACTURED_MULTI = 5;

    private static final int META_FROST_FLAT   = 0;
    private static final int META_FROST_MULTI  = 1;
    private static final int META_SOLAR_FLAT   = 6;
    private static final int META_SOLAR_MULTI  = 7;
    private static final int META_LIGHT_FLAT   = 10;
    private static final int META_LIGHT_MULTI  = 11;
    private static final int META_NATURE_FLAT  = 8;
    private static final int META_NATURE_MULTI = 9;
    private static final int META_INFERNO_FLAT = 14;
    private static final int META_INFERNO_MULTI= 15;
    private static final int META_RAINBOW_FLAT = 16;
    private static final int META_RAINBOW_MULTI= 17;
    private static final int META_AETHER_FLAT  = 20;
    private static final int META_AETHER_MULTI = 21;
    private static final int META_NEGATIVE_FLAT = 12;
    private static final int META_NEGATIVE_MULTI= 13;

    // Preview ranges (real rolls are done by HexOrbRoller when acquired)
    private static final int STAT_FLAT_MIN = 25;
    private static final int STAT_FLAT_MAX = 75;
    private static final int STAT_PCT_MIN  = 1;
    private static final int STAT_PCT_MAX  = 10;
    private static final int ALL5_FLAT_MIN = 250;
    private static final int ALL5_FLAT_MAX = 750;
    private static final int ALL5_PCT_MIN  = 5;
    private static final int ALL5_PCT_MAX  = 25;

    // Gradient tags (HexFontRenderer parses these in tooltips)
    private static final String G_FIERY_OPEN     = "<grad #ff7a18 #ffd36b #ff3b00 #ff00aa scroll=0.28>";
    private static final String G_ICY_OPEN       = "<grad #3aa7ff #6ad6ff #baf3ff #f4feff scroll=0.22>";
    private static final String G_GOLDEN_OPEN    = "<grad #fff3b0 #ffd36b #ffb300 #fff7d6 scroll=0.20>";
    private static final String G_NATURE_OPEN    = "<grad #19ff74 #6dffb4 #00d66b #d8ffe8 scroll=0.22>";
    private static final String G_AETHER_OPEN    = "<grad #00ffd5 #36d1ff #7a5cff #e9ffff scroll=0.24>";
    private static final String G_ENERGIZED_OPEN = "<grad #ff4fd8 #36d1ff #ffe66d #7cff6b #7a5cff scroll=0.34>";
    private static final String G_NEG_OPEN       = "<grad #7a00ff #ff4fd8 #120018 #7a5cff scroll=0.30>";
    private static final String G_FRACTURE_OPEN  = "<grad #b84dff #7a5cff #00ffd5 #ff4fd8 scroll=0.30>";
    private static final String G_CHAOS_OPEN     = "<grad #ff4fd8 #7a5cff #00ffd5 #ffe66d scroll=0.38>";
    private static final String G_CLOSE          = "</grad>";

    private static final String EFFECT_NA = "\u00a77Effect: \u00a78N/A";
    private static final String BONUS_NA  = "\u00a77Bonus: \u00a78N/A";
    private static final String EFFECT_SWIRLY = "\u00a77Effect: \u00a7dUnique Attacks";
    private static final String EFFECT_CHAOTIC = "\u00a77Effect: " + G_CHAOS_OPEN + "Chaotic Shifts" + G_CLOSE;
    private static final String EFFECT_FRACTURED= "\u00a77Effect: " + G_FRACTURE_OPEN + "Fractured" + G_CLOSE;
    private static final String EFFECT_SOLAR_RANDOM = "\u00a77Effect: <pulse amp=0.55 speed=0.85>" + G_GOLDEN_OPEN + "Randomized Type" + G_CLOSE + "</pulse>";
    private static final String EFFECT_FIRE   = "\u00a77Effect: \u00a76Inferno Punch \u00a78(timed hits + finisher)";

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
        // IMPORTANT (1.7.10):
        // NEI / Creative "Search" tab (tabAllSearch) calls getSubItems with the Search tab.
        // If we only accept our exact creative tab, some variants won't appear in NEI/search.
        // 1.7.10 does NOT have Item#isInCreativeTab, so we explicitly allow Search + our tab.
        if (tab != CreativeTabs.tabAllSearch && tab != this.getCreativeTab()) return;
        for (int i = 0; i < VARIANTS.length; i++) {
            list.add(new ItemStack(item, 1, i));
        }
    }

    // Hover tooltip fallback:
    // - If the stack already has display lore (from HexOrbRoller rolling + writing lore), we do NOTHING.
    // - If it has no lore yet (e.g., still in the creative menu list), we show Effect/Bonus lines.
    // - Only the Swirly + Fire pills get real Effect text right now; everything else is Effect: N/A.
    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        if (stack == null || tooltip == null) return;

        // If real lore is already present, don't add extra lines (avoid duplicates).
        if (hasLoreLineStarting(stack, "\u00a77Effect:") || hasLoreLineStarting(stack, "\u00a77Bonus:") || hasLoreLineStarting(stack, "Sockets:")) {
            return;
        }

        int meta = stack.getItemDamage();

        // Effect line
        if (meta == META_SWIRLY_FLAT || meta == META_SWIRLY_ANIM) {
            tooltip.add(EFFECT_SWIRLY);
        } else if (meta == META_PILL_FIRE_ANIM || meta == META_PILL_FIRE_FLAT) {
            tooltip.add(EFFECT_FIRE);
        } else if (meta == META_CHAOTIC_FLAT || meta == META_CHAOTIC_MULTI) {
            tooltip.add(EFFECT_CHAOTIC);
        } else if (meta == META_FRACTURED_FLAT || meta == META_FRACTURED_MULTI) {
            tooltip.add(EFFECT_FRACTURED);
        } else if (meta == META_SOLAR_FLAT || meta == META_SOLAR_MULTI || meta == META_LIGHT_FLAT || meta == META_LIGHT_MULTI) {
            tooltip.add(EFFECT_SOLAR_RANDOM);
        } else {
            tooltip.add(EFFECT_NA);
        }

        // Bonus line (simple label; actual roll value is hidden but stored by HexOrbRoller)
        String bonus = getBonusPreview(meta);
        tooltip.add(bonus != null ? bonus : BONUS_NA);
    }

    /**
     * Preview-only: show the STAT NAME in the orb's color and that it's randomized.
     * (We intentionally do NOT show the numeric range/value here.)
     */
    private static String getBonusPreview(int meta) {
        switch (meta) {
            case META_NEGATIVE_FLAT:
            case META_NEGATIVE_MULTI:
                // Negative orb: 3 random negative stats, and 1 positive stat that balances the sum.
                return "\u00a77Bonus: <pulse amp=0.55 speed=0.85>" + G_NEG_OPEN + "3 Negatives + 1 Balanced Positive" + G_CLOSE + "</pulse>";

            case META_CHAOTIC_FLAT:
            case META_CHAOTIC_MULTI:
                return "\u00a77Bonus: <pulse amp=0.55 speed=0.85>" + G_CHAOS_OPEN + "Shifts 1–4 stats (±)" + G_CLOSE + "</pulse>";

            case META_FRACTURED_FLAT:
            case META_FRACTURED_MULTI:
                return "\u00a77Bonus: <pulse amp=0.55 speed=0.85>" + G_FRACTURE_OPEN + "Balanced • Thresholds • Shards" + G_CLOSE + "</pulse>";

            case META_INFERNO_FLAT:
            case META_INFERNO_MULTI:
                return "\u00a77Bonus: " + G_FIERY_OPEN + "Strength Is a Randomized Value" + G_CLOSE;

            case META_FROST_FLAT:
            case META_FROST_MULTI:
                return "\u00a77Bonus: " + G_ICY_OPEN + "Dexterity Is a Randomized Value" + G_CLOSE;

            case META_SOLAR_FLAT:
            case META_SOLAR_MULTI:
            case META_LIGHT_FLAT:
            case META_LIGHT_MULTI:
                return "\u00a77Bonus: <pulse amp=0.55 speed=0.85>" + G_GOLDEN_OPEN + "Random Attribute \u2022 Random Value" + G_CLOSE + "</pulse>";

            case META_NATURE_FLAT:
            case META_NATURE_MULTI:
                return "\u00a77Bonus: " + G_NATURE_OPEN + "WillPower Is a Randomized Value" + G_CLOSE;

            case META_AETHER_FLAT:
            case META_AETHER_MULTI:
                return "\u00a77Bonus: " + G_AETHER_OPEN + "Spirit Is a Randomized Value" + G_CLOSE;

            case META_RAINBOW_FLAT:
            case META_RAINBOW_MULTI:
                return "\u00a77Bonus: " + G_ENERGIZED_OPEN + "All Attributes Are Randomized Values" + G_CLOSE;
        }
        return null;
    }

    @SideOnly(Side.CLIENT)
    private static boolean hasLoreLineStarting(ItemStack stack, String startsWith) {
        if (stack == null || startsWith == null) return false;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) return false;
        if (!tag.hasKey("display", 10)) return false;
        NBTTagCompound disp = tag.getCompoundTag("display");
        if (disp == null) return false;
        if (!disp.hasKey("Lore", 9)) return false;

        NBTTagList lore = disp.getTagList("Lore", 8);
        for (int i = 0; i < lore.tagCount(); i++) {
            String s = lore.getStringTagAt(i);
            if (s != null && s.startsWith(startsWith)) return true;
        }
        return false;
    }
}
