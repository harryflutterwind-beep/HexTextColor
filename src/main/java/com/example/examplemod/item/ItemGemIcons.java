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
    private static final int META_PILL_DARKFIRE_FLAT = 24;
    private static final int META_PILL_DARKFIRE_ANIM = 25;
    private static final int META_PILL_FIRE_ANIM = 26;
    private static final int META_PILL_FIRE_FLAT = 27;

    // Evolved animated gem metas
    private static final int META_EVOLVED_AETHER  = 28;
    private static final int META_EVOLVED_FROST   = 29;
    private static final int META_EVOLVED_NATURE  = 30;
    private static final int META_EVOLVED_RAINBOW = 31;
    private static final int META_EVOLVED_SOLAR   = 32;
    private static final int META_EVOLVED_INFERNO = 33;

    // NBT subtype tags (rolled by HexOrbRoller; used by HUDs and tooltips)
    private static final String TAG_VOID_TYPE     = "HexVoidType";
    private static final String TAG_DARKFIRE_TYPE = "HexDarkFireType";
    private static final String TAG_LIGHT_TYPE    = "HexLightType";

    // NBT subtype showcase lists (keep strings in sync with HexOrbRoller)
    private static final String[] VOID_TYPES = new String[] {
            "Null Shell",
            "Entropy",
            "Gravity Well",
            "Abyss Mark"
    };

    private static final String[] DARKFIRE_TYPES = new String[] {
            "Blackflame Burn",
            "Cinder Weakness",
            "Armor Scorch",
            "Ashen Lifesteal",
            "Ember Detonation",
            "Shadowflame Trail"
    };

    private static final String[] LIGHT_TYPES = new String[] {
            "Radiant",
            "Beacon",
            "Solar",
            "Halo",
            "Angelic"
    };



    // Creative menu only: set true if you ever want to expose per-type NBT variants for debugging.
    // Keep FALSE for normal play so the tab isn't flooded with duplicate Void/Light/Darkfire entries.
    private static final boolean SHOW_NBT_SUBTYPE_SHOWCASE = false;

    // Stat-orb metas (keep in sync with HexOrbRoller profiles)
    private static final int META_CHAOTIC_FLAT = 2;
    private static final int META_CHAOTIC_MULTI = 3;
    private static final int META_FRACTURED_FLAT = 4;
    private static final int META_FRACTURED_MULTI = 5;

    private static final int META_FROST_FLAT   = 0;
    private static final int META_FROST_MULTI  = 1;
    private static final int META_SOLAR_FLAT   = 6;
    private static final int META_SOLAR_MULTI  = 7;
    private static final int META_NATURE_FLAT  = 8;
    private static final int META_NATURE_MULTI = 9;

    // Light orb (textures exist at VARIANTS[10] and VARIANTS[11])
    private static final int META_LIGHT_FLAT   = 10;
    private static final int META_LIGHT_MULTI  = 11;

    private static final int META_INFERNO_FLAT = 14;
    private static final int META_INFERNO_MULTI= 15;
    private static final int META_RAINBOW_FLAT = 16;
    private static final int META_RAINBOW_MULTI= 17;
    private static final int META_AETHER_FLAT  = 20;
    private static final int META_AETHER_MULTI = 21;
    private static final int META_VOID_FLAT   = 22;
    private static final int META_VOID_MULTI  = 23;
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
    private static final String G_VOID_OPEN      = "<grad #b84dff #7a5cff #120018 #ff4fd8 scroll=0.30>";
    private static final String G_FRACTURE_OPEN  = "<grad #b84dff #7a5cff #00ffd5 #ff4fd8 scroll=0.30>";
    private static final String G_CHAOS_OPEN     = "<grad #ff4fd8 #7a5cff #00ffd5 #ffe66d scroll=0.38>";
    private static final String G_LIGHT_OPEN     = "<grad #fff7d6 #ffeaa8 #ffffff #ffd36b scroll=0.22>";
    private static final String G_DARKFIRE_OPEN  = "<grad #ff2b2b #ff3b00 #ff00aa #7a5cff #120018 scroll=0.32>";
    private static final String G_CLOSE          = "</grad>";

    private static final String EFFECT_NA = "\u00a77Effect: \u00a78N/A";
    private static final String BONUS_NA  = "\u00a77Bonus: \u00a78N/A";
    private static final String EFFECT_SWIRLY = "\u00a77Effect: \u00a7dUnique Attacks";
    private static final String EFFECT_OVERWRITE = "\u00a77Effect: " + G_ENERGIZED_OPEN + "Overwrite" + G_CLOSE;
    private static final String EFFECT_ALL_PASSIVES = "\u00a77Effect: <pulse amp=0.45 speed=0.92>" + G_ENERGIZED_OPEN + "All Passives" + G_CLOSE + "</pulse>";
    private static final String EFFECT_CHAOTIC = "\u00a77Effect: " + G_CHAOS_OPEN + "Chaotic Shifts" + G_CLOSE;
    private static final String EFFECT_FRACTURED= "\u00a77Effect: " + G_FRACTURE_OPEN + "Fractured" + G_CLOSE;
    private static final String EFFECT_VOID_RANDOM= "\u00a77Effect: <pulse amp=0.55 speed=0.85>" + G_VOID_OPEN + "Randomized Void Type" + G_CLOSE + "</pulse>";
    private static final String EFFECT_LIGHT = "\u00a77Effect: <pulse amp=0.35 speed=0.95>" + G_LIGHT_OPEN + "Light" + G_CLOSE + "</pulse>";
    private static final String EFFECT_DARKFIRE = "\u00a77Effect: <pulse amp=0.55 speed=0.85>" + G_DARKFIRE_OPEN + "Random Darkflame Type" + G_CLOSE + "</pulse>";
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
            "pill_fire_textured_64",
            "evolved_aether_gem_64_anim_8f",
            "evolved_frost_gem_64_anim_8f",
            "evolved_nature_gem_64_anim_8f",
            "evolved_rainbow_gem_64_anim_8f",
            "evolved_solar_gem_64_anim_8f",
            "evolved_inferno_gem_anim"
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
    public String getItemStackDisplayName(ItemStack stack) {
        return super.getItemStackDisplayName(stack);
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
        // NEI will sometimes call getSubItems with tab == null when building its item panel.
        // If we reject null, NEI falls back to only showing damage 0..15, hiding our higher metas (rainbow/swirly/aether/void/pills).
        if (tab != null && tab != CreativeTabs.tabAllSearch && tab != this.getCreativeTab()) return;
        for (int i = 0; i < VARIANTS.length; i++) {
            list.add(new ItemStack(item, 1, i));
        }
        if (SHOW_NBT_SUBTYPE_SHOWCASE) addNbtSubtypeShowcase(item, list);
    }


    /**
     * Creative/NEI showcase for NBT-based subtypes (Void/Light/Darkfire).
     * 1.7.10 creative tabs cannot "discover" NBT variants automatically, so we add pre-tagged stacks.
     * This only affects menus (creative/search/NEI) and does not change drops/rolling behavior.
     */
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addNbtSubtypeShowcase(Item item, List list) {
        if (item == null || list == null) return;

        // VOID: show each rolled type for both flat + enhanced metas
        for (int i = 0; i < VOID_TYPES.length; i++) {
            String t = VOID_TYPES[i];
            list.add(makeTypedStack(item, META_VOID_FLAT,   TAG_VOID_TYPE, t));
            list.add(makeTypedStack(item, META_VOID_MULTI,  TAG_VOID_TYPE, t));
        }

        // DARKFIRE pill: show each rolled effect type for both flat + anim metas
        for (int i = 0; i < DARKFIRE_TYPES.length; i++) {
            String t = DARKFIRE_TYPES[i];
            list.add(makeTypedStack(item, META_PILL_DARKFIRE_FLAT, TAG_DARKFIRE_TYPE, t));
            list.add(makeTypedStack(item, META_PILL_DARKFIRE_ANIM, TAG_DARKFIRE_TYPE, t));
        }

        // LIGHT: show each rolled Light type for both flat + enhanced metas
        for (int i = 0; i < LIGHT_TYPES.length; i++) {
            String t = LIGHT_TYPES[i];
            list.add(makeTypedStack(item, META_LIGHT_FLAT,  TAG_LIGHT_TYPE, t));
            list.add(makeTypedStack(item, META_LIGHT_MULTI, TAG_LIGHT_TYPE, t));
        }
    }

    /** Create a 1x stack with a single string tag, safe for creative listing. */
    private static ItemStack makeTypedStack(Item item, int meta, String tagKey, String value) {
        ItemStack s = new ItemStack(item, 1, meta);
        try {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString(tagKey, value);
            s.setTagCompound(tag);
        } catch (Throwable ignored) {}
        return s;
    }

    // Hover tooltip fallback:
    // - If the stack already has display lore (from HexOrbRoller rolling + writing lore), we do NOTHING.
    // - If it has no lore yet (e.g., still in the creative menu list), we show Effect/Bonus lines.
    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        if (stack == null || tooltip == null) return;

        // If real lore is already present, don't add extra lines (avoid duplicates).
        if (hasLoreLineStarting(stack, "\u00a77Effect:")
                || hasLoreLineStarting(stack, "\u00a77Bonus:")
                || hasLoreLineStarting(stack, "Sockets:")) {
            return;
        }

        int meta = stack.getItemDamage();

        // Effect line
        if (meta == META_SWIRLY_FLAT || meta == META_SWIRLY_ANIM) {
            tooltip.add(EFFECT_SWIRLY);
        } else if (meta == META_RAINBOW_FLAT || meta == META_RAINBOW_MULTI) {
            tooltip.add(EFFECT_OVERWRITE);
        } else if (meta == META_EVOLVED_RAINBOW) {
            tooltip.add(EFFECT_ALL_PASSIVES);
        } else if (meta == META_PILL_DARKFIRE_ANIM || meta == META_PILL_DARKFIRE_FLAT) {
            // Darkfire pill: show rolled type name only (no extra summary text).
            String dfType = null;
            try {
                NBTTagCompound t = (stack != null ? stack.getTagCompound() : null);
                if (t != null && t.hasKey(TAG_DARKFIRE_TYPE)) dfType = t.getString(TAG_DARKFIRE_TYPE);
            } catch (Throwable ignored) {}
            if (dfType == null || dfType.length() == 0) dfType = "Darkflame";
            tooltip.add("§7Effect: <pulse amp=0.55 speed=0.85>" + G_DARKFIRE_OPEN + dfType + G_CLOSE + "</pulse>");
        } else if (meta == META_PILL_FIRE_ANIM || meta == META_PILL_FIRE_FLAT) {
            tooltip.add(EFFECT_FIRE);
        } else if (meta == META_CHAOTIC_FLAT || meta == META_CHAOTIC_MULTI) {
            tooltip.add(EFFECT_CHAOTIC);
        } else if (meta == META_FRACTURED_FLAT || meta == META_FRACTURED_MULTI) {
            tooltip.add(EFFECT_FRACTURED);
        } else if (meta == META_VOID_FLAT || meta == META_VOID_MULTI) {
            // Preview-only Void tooltip for unrolled/creative stacks.
            // If the server already rolled a type but lore hasn't synced yet, show it here too.
            String vt = null;
            try {
                NBTTagCompound t = (stack != null ? stack.getTagCompound() : null);
                if (t != null && t.hasKey(TAG_VOID_TYPE)) vt = t.getString(TAG_VOID_TYPE);
            } catch (Throwable ignored) {}
            if (vt != null && vt.length() > 0) {
                tooltip.add("§7Effect: <pulse amp=0.55 speed=0.85>" + G_VOID_OPEN + vt + G_CLOSE + "</pulse>");
            } else {
                tooltip.add(EFFECT_VOID_RANDOM);
            }
        } else if (meta == META_LIGHT_FLAT || meta == META_LIGHT_MULTI) {
            // Preview-only Light tooltip for unrolled/creative stacks.
            // If the server already rolled a type but lore hasn't synced yet, show it here too.
            String lt = null;
            try {
                NBTTagCompound t = (stack != null ? stack.getTagCompound() : null);
                if (t != null && t.hasKey(TAG_LIGHT_TYPE)) lt = t.getString(TAG_LIGHT_TYPE);
            } catch (Throwable ignored) {}
            if (lt != null && lt.length() > 0) {
                tooltip.add("§7Effect: <pulse amp=0.35 speed=0.95>" + G_LIGHT_OPEN + "Light" + G_CLOSE + "</pulse> §7+ "
                        + "<pulse amp=0.35 speed=0.95>" + G_LIGHT_OPEN + lt + G_CLOSE + "</pulse>");
            } else {
                tooltip.add(EFFECT_LIGHT);
            }
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
                return "\u00a77Bonus: <pulse amp=0.55 speed=0.85>" + G_CHAOS_OPEN + "Shifts 1\u20134 stats (\u00b1)" + G_CLOSE + "</pulse>";

            case META_FRACTURED_FLAT:
            case META_FRACTURED_MULTI:
                return "\u00a77Bonus: <pulse amp=0.55 speed=0.85>" + G_FRACTURE_OPEN + "Balanced \u2022 Thresholds \u2022 Shards" + G_CLOSE + "</pulse>";

            case META_INFERNO_FLAT:
            case META_INFERNO_MULTI:
                return "\u00a77Bonus: " + G_FIERY_OPEN + "Strength Is a Randomized Value" + G_CLOSE;
            case META_EVOLVED_INFERNO:
                return "\u00a77Bonus: " + G_FIERY_OPEN + "Strength + 1 Random Stat (% Rolls)" + G_CLOSE;

            case META_FROST_FLAT:
            case META_FROST_MULTI:
                return "\u00a77Bonus: " + G_ICY_OPEN + "Dexterity Is a Randomized Value" + G_CLOSE;
            case META_EVOLVED_FROST:
                return "\u00a77Bonus: " + G_ICY_OPEN + "Dexterity + 1 Random Stat (% Rolls)" + G_CLOSE;

            case META_SOLAR_FLAT:
            case META_SOLAR_MULTI:
                return "\u00a77Bonus: " + G_GOLDEN_OPEN + "Constitution Is a Randomized Value" + G_CLOSE;
            case META_EVOLVED_SOLAR:
                return "\u00a77Bonus: " + G_GOLDEN_OPEN + "Constitution + 1 Random Stat (% Rolls)" + G_CLOSE;

            case META_NATURE_FLAT:
            case META_NATURE_MULTI:
                return "\u00a77Bonus: " + G_NATURE_OPEN + "WillPower Is a Randomized Value" + G_CLOSE;
            case META_EVOLVED_NATURE:
                return "\u00a77Bonus: " + G_NATURE_OPEN + "WillPower + 1 Random Stat (% Rolls)" + G_CLOSE;

            case META_AETHER_FLAT:
            case META_AETHER_MULTI:
                return "\u00a77Bonus: " + G_AETHER_OPEN + "Spirit Is a Randomized Value" + G_CLOSE;
            case META_EVOLVED_AETHER:
                return "\u00a77Bonus: " + G_AETHER_OPEN + "Spirit + 1 Random Stat (% Rolls)" + G_CLOSE;

            case META_SWIRLY_FLAT:
            case META_SWIRLY_ANIM:
            case META_RAINBOW_FLAT:
            case META_RAINBOW_MULTI:
            case META_EVOLVED_RAINBOW:
                return "\u00a77Bonus: " + G_ENERGIZED_OPEN + "All Attributes Are Randomized Values" + G_CLOSE;

            case META_VOID_FLAT:
            case META_VOID_MULTI:
                return "\u00a77Bonus: <pulse amp=0.55 speed=0.85>" + G_VOID_OPEN + "Random Attribute \u2022 Random Value" + G_CLOSE + "</pulse>";

            case META_LIGHT_FLAT:
            case META_LIGHT_MULTI:
                // Testing/preview line for the Light orb (unrolled stacks)
                return "\u00a77Bonus: <pulse amp=0.55 speed=0.85>" + G_LIGHT_OPEN + "Radiance \u2022 Light Abilities" + G_CLOSE + "</pulse>";

            case META_PILL_DARKFIRE_FLAT:
                return "\u00a77Bonus: <pulse amp=0.55 speed=0.85>" + G_DARKFIRE_OPEN + "2 Random Positive Stats (Flat)" + G_CLOSE + "</pulse>";

            case META_PILL_DARKFIRE_ANIM:
                return "\u00a77Bonus: <pulse amp=0.55 speed=0.85>" + G_DARKFIRE_OPEN + "2 Random Positive Stats (% Rolls)" + G_CLOSE + "</pulse>";

            case META_PILL_FIRE_FLAT:
                // Fire Pill now rolls 2 distinct positive stats (flat)
                return "\u00a77Bonus: <pulse amp=0.55 speed=0.85>" + G_FIERY_OPEN + "2 Random Positive Stats (Flat)" + G_CLOSE + "</pulse>";

            case META_PILL_FIRE_ANIM:
                // Enhanced/animated Fire Pill rolls 2 distinct positive stats (% rolls)
                return "\u00a77Bonus: <pulse amp=0.55 speed=0.85>" + G_FIERY_OPEN + "2 Random Positive Stats (% Rolls)" + G_CLOSE + "</pulse>";

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
