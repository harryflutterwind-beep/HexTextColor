// src/main/java/com/example/examplemod/item/ItemTransformWeapon.java
package com.example.examplemod.item;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/**
 * Transformable weapon with simple NBT state + cooldown helpers.
 * Thermos/KCauldron-safe: no direct maxStackSize field access, no client imports.
 */
public class ItemTransformWeapon extends Item {

    // NBT keys
    private static final String KEY_ROOT         = "HexTransform";
    private static final String KEY_TRANSFORM    = "Transformed"; // boolean
    private static final String KEY_COOLDOWN     = "CD";          // int ticks
    private static final int    DEFAULT_CD_TICKS = 20 * 8;        // 8s default

    public ItemTransformWeapon() {
        super();
        // DO NOT touch maxStackSize directly – causes NoSuchFieldError in some envs
        // Just use getItemStackLimit overrides below.
    }

    // Hard limit to 1 per stack – safe across mappings
    @Override
    public int getItemStackLimit(ItemStack stack) {
        return 1;
    }

    // Some code paths call the no-arg version in 1.7.10; keep them in sync
    @Override
    public int getItemStackLimit() {
        return 1;
    }

    // ───────────────────────── static helpers used by TransformHandler ─────────────────────────

    /** Returns true if the stack is in transformed state. */
    public static boolean isTransformed(ItemStack stack) {
        if (stack == null) return false;
        NBTTagCompound t = ensure(stack);
        NBTTagCompound rt = getRoot(t);
        return rt.getBoolean(KEY_TRANSFORM);
    }

    /** Sets transformed state and (optionally) starts cooldown when turning off. */
    public static void setTransformed(ItemStack stack, boolean v) {
        if (stack == null) return;
        NBTTagCompound t = ensure(stack);
        NBTTagCompound rt = getRoot(t);
        rt.setBoolean(KEY_TRANSFORM, v);
        // If you want to start cooldown upon disabling transform, uncomment:
        // if (!v && rt.getInteger(KEY_COOLDOWN) <= 0) rt.setInteger(KEY_COOLDOWN, DEFAULT_CD_TICKS);
        stack.setTagCompound(t);
    }

    /** Decrements cooldown by 1 tick if above 0. Call once per tick. */
    public static void tickCooldown(ItemStack stack) {
        if (stack == null) return;
        NBTTagCompound t = ensure(stack);
        NBTTagCompound rt = getRoot(t);
        int cd = rt.getInteger(KEY_COOLDOWN);
        if (cd > 0) {
            rt.setInteger(KEY_COOLDOWN, cd - 1);
            stack.setTagCompound(t);
        }
    }

    /** Returns true if cooldown > 0. */
    public static boolean isOnCooldown(ItemStack stack) {
        if (stack == null) return false;
        NBTTagCompound t = stack.getTagCompound();
        if (t == null || !t.hasKey(KEY_ROOT, 10)) return false;
        return t.getCompoundTag(KEY_ROOT).getInteger(KEY_COOLDOWN) > 0;
    }

    /** Sets cooldown (ticks). */
    public static void setCooldown(ItemStack stack, int ticks) {
        if (stack == null) return;
        if (ticks < 0) ticks = 0;
        NBTTagCompound t = ensure(stack);
        NBTTagCompound rt = getRoot(t);
        rt.setInteger(KEY_COOLDOWN, ticks);
        stack.setTagCompound(t);
    }

    // ───────────────────────── optional: right-click to toggle ─────────────────────────

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) return stack; // client: do nothing

        // simple toggle with cooldown gate
        if (isOnCooldown(stack)) return stack;

        boolean cur = isTransformed(stack);
        setTransformed(stack, !cur);

        // start cooldown every toggle (tune this to your taste)
        setCooldown(stack, DEFAULT_CD_TICKS);

        // optional: feedback
        try {
            player.addChatMessage(new net.minecraft.util.ChatComponentText(
                    (cur ? "§7Transform: §cOFF" : "§7Transform: §aON") +
                            " §8( cooldown " + (DEFAULT_CD_TICKS / 20) + "s )"
            ));
        } catch (Throwable ignored) {}

        return stack;
    }

    // ───────────────────────── NBT utils ─────────────────────────

    private static NBTTagCompound ensure(ItemStack s) {
        NBTTagCompound t = s.getTagCompound();
        if (t == null) {
            t = new NBTTagCompound();
            s.setTagCompound(t);
        }
        if (!t.hasKey(KEY_ROOT, 10)) {
            t.setTag(KEY_ROOT, new NBTTagCompound());
        }
        return t;
    }

    private static NBTTagCompound getRoot(NBTTagCompound t) {
        return t.getCompoundTag(KEY_ROOT);
    }
}
