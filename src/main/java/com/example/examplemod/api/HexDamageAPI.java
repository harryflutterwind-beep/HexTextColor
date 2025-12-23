// src/main/java/com/example/examplemod/api/HexDamageAPI.java
package com.example.examplemod.api;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.UUID;

public final class HexDamageAPI {

    // Same identifiers as SetDamageCommand
    private static final UUID   HEX_UUID   = UUID.fromString("b6a4d3f2-7b4a-4f1f-9f5a-2c1b2f0a1a01");
    private static final String ATTR_NAME  = "generic.attackDamage";
    private static final String MOD_NAME   = "HexAttack";

    private HexDamageAPI() {}

    // ─────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────

    /** Set attack damage on the held item. op = 0(add),1(add% base),2(mult total). */
    public static void setHeldAttackDamage(EntityPlayer player, double amount, int op) {
        if (player == null) return;
        ItemStack held = player.getHeldItem();
        if (held == null) return;

        writeHexAttack(held, amount, op);
        syncInventory(player);
    }

    /** Clear our HexAttack modifier from the held item. */
    public static void clearHeldAttackDamage(EntityPlayer player) {
        if (player == null) return;
        ItemStack held = player.getHeldItem();
        if (held == null) return;

        removeHexAttack(held);
        syncInventory(player);
    }

    /** Read our HexAttack amount from the held item (0.0 if none). */
    public static double getHeldAttackDamage(EntityPlayer player) {
        if (player == null) return 0.0;
        ItemStack held = player.getHeldItem();
        if (held == null) return 0.0;
        return readHexAttackAmount(held);
    }

    // ─────────────────────────────────
    // INTERNAL HELPERS (copied from SetDamageCommand)
    // ─────────────────────────────────

    private static void syncInventory(EntityPlayer player) {
        if (player instanceof EntityPlayerMP) {
            ((EntityPlayerMP) player).inventoryContainer.detectAndSendChanges();
        }
    }

    private static int removeHexAttack(ItemStack stack){
        NBTTagCompound tag = ensureTag(stack);
        NBTTagList mods = ensureAttrList(tag);
        NBTTagList out = new NBTTagList();
        int removed = 0;
        for (int i=0;i<mods.tagCount();i++){
            NBTTagCompound m = mods.getCompoundTagAt(i);
            if (isOurs(m)) { removed++; continue; }
            out.appendTag(m);
        }
        tag.setTag("AttributeModifiers", out);
        stack.setTagCompound(tag);
        return removed;
    }

    private static double readHexAttackAmount(ItemStack stack){
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey("AttributeModifiers", 9)) return 0.0;
        NBTTagList mods = tag.getTagList("AttributeModifiers", 10);
        for (int i=0;i<mods.tagCount();i++){
            NBTTagCompound m = mods.getCompoundTagAt(i);
            if (isOurs(m)) return m.getDouble("Amount");
        }
        return 0.0;
    }

    private static void writeHexAttack(ItemStack stack, double amount, int op){
        if (op < 0 || op > 2) op = 0;

        NBTTagCompound tag = ensureTag(stack);
        NBTTagList mods = ensureAttrList(tag);

        // remove our old modifier
        NBTTagList out = new NBTTagList();
        for (int i=0;i<mods.tagCount();i++){
            NBTTagCompound m = mods.getCompoundTagAt(i);
            if (!isOurs(m)) out.appendTag(m);
        }

        // add new one
        NBTTagCompound m = new NBTTagCompound();
        m.setString("AttributeName", ATTR_NAME);
        m.setString("Name", MOD_NAME);
        m.setDouble("Amount", amount);
        m.setInteger("Operation", op);
        m.setLong("UUIDMost",  HEX_UUID.getMostSignificantBits());
        m.setLong("UUIDLeast", HEX_UUID.getLeastSignificantBits());
        out.appendTag(m);

        tag.setTag("AttributeModifiers", out);
        stack.setTagCompound(tag);
    }

    private static boolean isOurs(NBTTagCompound m){
        if (m == null) return false;
        if (!ATTR_NAME.equals(m.getString("AttributeName"))) return false;
        if (!MOD_NAME.equals(m.getString("Name"))) return false;
        return m.getLong("UUIDMost")  == HEX_UUID.getMostSignificantBits()
                && m.getLong("UUIDLeast") == HEX_UUID.getLeastSignificantBits();
    }

    private static NBTTagCompound ensureTag(ItemStack s){
        NBTTagCompound t = s.getTagCompound();
        if (t == null) { t = new NBTTagCompound(); s.setTagCompound(t); }
        return t;
    }

    private static NBTTagList ensureAttrList(NBTTagCompound tag){
        if (!tag.hasKey("AttributeModifiers", 9)) {
            tag.setTag("AttributeModifiers", new NBTTagList());
        }
        return tag.getTagList("AttributeModifiers", 10);
    }
}
