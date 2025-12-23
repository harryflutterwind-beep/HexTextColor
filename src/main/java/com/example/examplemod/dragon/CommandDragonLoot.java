// src/main/java/com/example/examplemod/dragon/CommandDragonLoot.java
package com.example.examplemod.dragon;

import com.example.examplemod.api.DragonLootAPI;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;

public class CommandDragonLoot extends CommandBase {
    @Override public String getCommandName() { return "dragonloot"; }
    @Override public int getRequiredPermissionLevel() { return 2; } // OP only
    @Override public String getCommandUsage(ICommandSender s) {
        return "/dragonloot help\n"
                + "/dragonloot add <id> <chance> <min> <max>\n"
                + "/dragonloot adddim <id> <meta> <chance> <min> <max> <dim> <lootScale>\n"
                + "/dragonloot addnbt <id> <meta> <chance> <min> <max> <nbtJson>\n"
                + "/dragonloot addheld <chance> <min> <max> <dim|*> [lootScale]\n"
                + "/dragonloot addheldnbt <chance> <min> <max> <dim|*> <lootScale> <nbtJson>\n"
                + "/dragonloot clear";
    }

    @Override
    public void processCommand(ICommandSender s, String[] a) {
        if (a.length == 0 || "help".equalsIgnoreCase(a[0])) {
            say(s, getCommandUsage(s)); return;
        }

        try {
            if ("clear".equalsIgnoreCase(a[0])) {
                DragonLootAPI.clearAll();
                ok(s, "Cleared all custom dragon drop rules (vanilla unchanged).");
                return;
            }

            if ("add".equalsIgnoreCase(a[0]) && a.length >= 5) {
                boolean ok = DragonLootAPI.addDrop(a[1], d(a[2]), i(a[3]), i(a[4]));
                msg(s, ok, "Added " + a[1] + " any-dim", "Failed to resolve " + a[1]);
                return;
            }

            if ("adddim".equalsIgnoreCase(a[0]) && a.length >= 8) {
                boolean ok = DragonLootAPI.addDropForDim(
                        a[1], i(a[2]), d(a[3]), i(a[4]), i(a[5]),
                        null, i(a[6]), d(a[7]));
                msg(s, ok, "Added " + a[1] + " dim=" + a[6], "Failed to resolve " + a[1]);
                return;
            }

            if ("addnbt".equalsIgnoreCase(a[0]) && a.length >= 7) {
                String nbt = join(a, 6);
                boolean ok = DragonLootAPI.addDropWithNbt(a[1], i(a[2]), d(a[3]), i(a[4]), i(a[5]), nbt);
                msg(s, ok, "Added " + a[1] + " (NBT) any-dim", "Failed to resolve " + a[1]);
                return;
            }

            if ("addheld".equalsIgnoreCase(a[0]) && a.length >= 5) {
                if (!(s instanceof EntityPlayer)) { fail(s, "Must be a player to use addheld."); return; }
                EntityPlayer p = (EntityPlayer) s;
                ItemStack held = p.getHeldItem();
                if (held == null) { fail(s, "Hold the item first."); return; }

                String id = net.minecraft.item.Item.itemRegistry.getNameForObject(held.getItem());
                int meta  = held.getItemDamage();
                double chance = d(a[1]);
                int min = i(a[2]);
                int max = i(a[3]);
                String dimTok = a[4];
                double loot = (a.length >= 6) ? d(a[5]) : 0.0;

                boolean ok;
                if ("*".equals(dimTok)) {
                    ok = DragonLootAPI.addDropWithNbt(id, meta, chance, min, max, null);
                } else {
                    int dim = i(dimTok);
                    ok = DragonLootAPI.addDropForDim(id, meta, chance, min, max, null, dim, loot);
                }
                msg(s, ok, "Added held " + id + ":" + meta, "Failed to add held " + id);
                return;
            }

            if ("addheldnbt".equalsIgnoreCase(a[0]) && a.length >= 7) {
                if (!(s instanceof EntityPlayer)) { fail(s, "Must be a player to use addheldnbt."); return; }
                EntityPlayer p = (EntityPlayer) s;
                ItemStack held = p.getHeldItem();
                if (held == null) { fail(s, "Hold the item first."); return; }

                String id = net.minecraft.item.Item.itemRegistry.getNameForObject(held.getItem());
                int meta  = held.getItemDamage();
                double chance = d(a[1]);
                int min = i(a[2]);
                int max = i(a[3]);
                String dimTok = a[4];
                double loot = d(a[5]);
                String nbt = join(a, 6);

                boolean ok;
                if ("*".equals(dimTok)) {
                    ok = DragonLootAPI.addDropWithNbt(id, meta, chance, min, max, nbt);
                } else {
                    int dim = i(dimTok);
                    ok = DragonLootAPI.addDropForDim(id, meta, chance, min, max, nbt, dim, loot);
                }
                msg(s, ok, "Added held " + id + ":" + meta + " (NBT)", "Failed to add held " + id + " (NBT)");
                return;
            }

            fail(s, "Bad usage. Try /dragonloot help.");
        } catch (Exception ex) {
            fail(s, "Error: " + ex.getMessage());
        }
    }

    // helpers
    private static void say (ICommandSender s, String m){ s.addChatMessage(new ChatComponentText(m)); }
    private static void ok  (ICommandSender s, String m){ say(s, "§a[DragonLoot] §f" + m); }
    private static void fail(ICommandSender s, String m){ say(s, "§c[DragonLoot] §f" + m); }
    private static void msg (ICommandSender s, boolean ok, String good, String bad){ if(ok) ok(s, good); else fail(s, bad); }
    private static int    i(String s){ return Integer.parseInt(s); }
    private static double d(String s){ return Double.parseDouble(s); }
    private static String join(String[] arr, int from){
        StringBuilder b=new StringBuilder();
        for(int k=from;k<arr.length;k++){ if(k>from)b.append(' '); b.append(arr[k]); }
        return b.toString();
    }
}
