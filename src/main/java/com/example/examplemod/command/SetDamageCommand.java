// src/main/java/com/example/examplemod/command/SetDamageCommand.java
package com.example.examplemod.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.ChatComponentText;

import java.util.*;

public class SetDamageCommand extends CommandBase {

    // We only touch our own modifier, identified by UUID + Name.
    private static final UUID   HEX_UUID   = UUID.fromString("b6a4d3f2-7b4a-4f1f-9f5a-2c1b2f0a1a01");
    private static final String ATTR_NAME  = "generic.attackDamage";
    private static final String MOD_NAME   = "HexAttack";

    @Override public String getCommandName() { return "setdamage"; } // keep old name
    @Override public String getCommandUsage(ICommandSender s) { return "/setdamage <value|+N|-N|clear> [op=0|1|2]"; }
    @Override public int getRequiredPermissionLevel() { return 2; } // ops only

    // Optional aliases so /damage also works
    @Override @SuppressWarnings("rawtypes")
    public List getCommandAliases() { return Arrays.asList("damage", "hexdmg", "hexdamage"); }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP)) { sender.addChatMessage(txt("Player only.")); return; }
        EntityPlayerMP p = (EntityPlayerMP) sender;

        if (args.length < 1) { p.addChatMessage(txt(getCommandUsage(sender))); return; }

        ItemStack held = p.getHeldItem();
        if (held == null) { p.addChatMessage(txt("§cHold an item.")); return; }

        String tok = args[0].trim().toLowerCase(Locale.ROOT);

        // clear our modifier
        if ("clear".equals(tok)) {
            int removed = removeHexAttack(held);
            p.inventoryContainer.detectAndSendChanges();
            p.addChatMessage(txt(removed > 0 ? "§aRemoved attack modifier." : "§7No HexAttack modifier found."));
            return;
        }

        // optional op= (0 add, 1 add% base, 2 multiply total)
        int op = 0;
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if (a != null && a.startsWith("op=")) {
                try { op = Math.max(0, Math.min(2, Integer.parseInt(a.substring(3)))); }
                catch (NumberFormatException ignore) {}
            }
        }

        boolean relative = tok.startsWith("+") || tok.startsWith("-");
        double val;
        try { val = Double.parseDouble(tok); }
        catch (NumberFormatException nfe) { p.addChatMessage(txt("§cBad number: §f" + args[0])); return; }

        double cur = readHexAttackAmount(held);
        double target = relative ? (cur + val) : val;

        writeHexAttack(held, target, op);
        p.inventoryContainer.detectAndSendChanges();
        p.addChatMessage(txt("§aAttack Damage set to §f" + trim(target) + " §7(op=" + op + ")."));
    }

    // ── NBT helpers ────────────────────────────────────────────────────────────
    private int removeHexAttack(ItemStack stack){
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

    private double readHexAttackAmount(ItemStack stack){
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey("AttributeModifiers", 9)) return 0.0;
        NBTTagList mods = tag.getTagList("AttributeModifiers", 10);
        for (int i=0;i<mods.tagCount();i++){
            NBTTagCompound m = mods.getCompoundTagAt(i);
            if (isOurs(m)) return m.getDouble("Amount");
        }
        return 0.0;
    }

    private void writeHexAttack(ItemStack stack, double amount, int op){
        if (op < 0 || op > 2) op = 0;

        NBTTagCompound tag = ensureTag(stack);
        NBTTagList mods = ensureAttrList(tag);

        // remove our old one
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

    private boolean isOurs(NBTTagCompound m){
        if (m == null) return false;
        if (!ATTR_NAME.equals(m.getString("AttributeName"))) return false;
        if (!MOD_NAME.equals(m.getString("Name"))) return false;
        return m.getLong("UUIDMost")  == HEX_UUID.getMostSignificantBits()
                && m.getLong("UUIDLeast") == HEX_UUID.getLeastSignificantBits();
    }

    private NBTTagCompound ensureTag(ItemStack s){
        NBTTagCompound t = s.getTagCompound();
        if (t == null) { t = new NBTTagCompound(); s.setTagCompound(t); }
        return t;
    }
    private NBTTagList ensureAttrList(NBTTagCompound tag){
        if (!tag.hasKey("AttributeModifiers", 9)) tag.setTag("AttributeModifiers", new NBTTagList());
        return tag.getTagList("AttributeModifiers", 10);
    }

    private ChatComponentText txt(String s){ return new ChatComponentText(s); }
    private String trim(double d){ String s = String.valueOf(d); return s.endsWith(".0") ? s.substring(0, s.length()-2) : s; }
}
