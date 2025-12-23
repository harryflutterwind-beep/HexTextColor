// src/main/java/com/example/examplemod/command/LorePagesCommand.java
package com.example.examplemod.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentText;

import java.util.ArrayList;
import java.util.List;

/**
 * /lorepages add <text...>
 * /lorepages insert <index> <text...>
 * /lorepages remove <index>
 * /lorepages clear
 * /lorepages list
 * /lorepages transfer <fromSlot> <toSlot> [move|copy]
 *
 * Stored in:
 *   stack.tag.HexLorePages.Pages (NBTTagList<String>)
 *
 * Notes:
 *  - Use \n inside text to create multi-line pages
 *  - Slot indices: 0-35 (0-8 hotbar, 9-35 inventory)
 */
public class LorePagesCommand extends CommandBase {

    private static final String NBT_ROOT = "HexLorePages";
    private static final String NBT_LIST = "Pages";

    @Override
    public String getCommandName() {
        return "lorepages";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/lorepages add <text...> | insert <index> <text...> | remove <index> | clear | list | transfer <fromSlot> <toSlot> [move|copy]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        // tweak as you want (2 = ops). If you want everyone to use it, return 0.
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        EntityPlayer p = (sender instanceof EntityPlayer) ? (EntityPlayer) sender : null;
        if (p == null) {
            sender.addChatMessage(new ChatComponentText("This command can only be used by a player."));
            return;
        }

        if (args == null || args.length < 1) throw new WrongUsageException(getCommandUsage(sender));

        String sub = args[0].toLowerCase();
        if ("add".equals(sub)) {
            requireArgs(args, 2, sender);
            ItemStack held = p.getHeldItem();
            if (held == null) {
                msg(p, "§cHold an item first.");
                return;
            }
            String text = joinFrom(args, 1);
            NBTTagList list = getOrCreatePagesList(held);
            list.appendTag(new net.minecraft.nbt.NBTTagString(text));
            savePagesList(held, list);
            msg(p, "§aAdded page §f#" + list.tagCount() + "§a.");
            return;
        }

        if ("insert".equals(sub)) {
            requireArgs(args, 3, sender);
            ItemStack held = p.getHeldItem();
            if (held == null) {
                msg(p, "§cHold an item first.");
                return;
            }
            int idx = parseIntBounded(p, args[1], 1, 9999) - 1; // user uses 1-based
            String text = joinFrom(args, 2);

            NBTTagList list = getOrCreatePagesList(held);
            List<String> pages = toStringList(list);

            if (idx < 0) idx = 0;
            if (idx > pages.size()) idx = pages.size();
            pages.add(idx, text);

            NBTTagList rebuilt = fromStringList(pages);
            savePagesList(held, rebuilt);

            msg(p, "§aInserted page at §f#" + (idx + 1) + "§a.");
            return;
        }

        if ("remove".equals(sub)) {
            requireArgs(args, 2, sender);
            ItemStack held = p.getHeldItem();
            if (held == null) {
                msg(p, "§cHold an item first.");
                return;
            }
            int idx = parseIntBounded(p, args[1], 1, 9999) - 1;

            NBTTagList list = getPagesList(held);
            if (list == null || list.tagCount() == 0) {
                msg(p, "§7No pages to remove.");
                return;
            }

            List<String> pages = toStringList(list);
            if (idx < 0 || idx >= pages.size()) {
                msg(p, "§cInvalid page index. Use /lorepages list");
                return;
            }

            pages.remove(idx);
            if (pages.isEmpty()) {
                clearPages(held);
                msg(p, "§aRemoved last page; pages cleared.");
            } else {
                savePagesList(held, fromStringList(pages));
                msg(p, "§aRemoved page §f#" + (idx + 1) + "§a.");
            }
            return;
        }

        if ("clear".equals(sub)) {
            ItemStack held = p.getHeldItem();
            if (held == null) {
                msg(p, "§cHold an item first.");
                return;
            }
            clearPages(held);
            msg(p, "§aCleared all pages.");
            return;
        }

        if ("list".equals(sub)) {
            ItemStack held = p.getHeldItem();
            if (held == null) {
                msg(p, "§cHold an item first.");
                return;
            }
            NBTTagList list = getPagesList(held);
            if (list == null || list.tagCount() == 0) {
                msg(p, "§7No pages stored on this item.");
                return;
            }
            msg(p, "§b§oPages: §f" + list.tagCount());
            for (int i = 0; i < list.tagCount(); i++) {
                String page = list.getStringTagAt(i);
                String preview = preview(page, 42);
                msg(p, "§7- §f#" + (i + 1) + "§7: §b§o" + preview);
            }
            return;
        }

        if ("transfer".equals(sub)) {
            requireArgs(args, 3, sender);

            int fromSlot = parseIntBounded(p, args[1], 0, 35);
            int toSlot   = parseIntBounded(p, args[2], 0, 35);

            String mode = (args.length >= 4) ? args[3].toLowerCase() : "copy";
            boolean move = "move".equals(mode);

            ItemStack from = p.inventory.getStackInSlot(fromSlot);
            ItemStack to   = p.inventory.getStackInSlot(toSlot);

            if (from == null) {
                msg(p, "§cNo item in fromSlot " + fromSlot + ".");
                return;
            }
            if (to == null) {
                msg(p, "§cNo item in toSlot " + toSlot + ".");
                return;
            }

            NBTTagList fromPages = getPagesList(from);
            if (fromPages == null || fromPages.tagCount() == 0) {
                msg(p, "§7No pages on fromSlot item.");
                return;
            }

            // Copy pages onto destination (overwrites destination pages)
            savePagesList(to, (NBTTagList) fromPages.copy());

            if (move) {
                clearPages(from);
                msg(p, "§aMoved pages from slot §f" + fromSlot + "§a to §f" + toSlot + "§a.");
            } else {
                msg(p, "§aCopied pages from slot §f" + fromSlot + "§a to §f" + toSlot + "§a.");
            }
            return;
        }

        throw new WrongUsageException(getCommandUsage(sender));
    }

    // ─────────────────────────────────────────────
    // NBT helpers
    // ─────────────────────────────────────────────

    private static NBTTagList getPagesList(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) return null;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(NBT_ROOT, 10)) return null;
        NBTTagCompound root = tag.getCompoundTag(NBT_ROOT);
        if (root == null || !root.hasKey(NBT_LIST, 9)) return null;
        return root.getTagList(NBT_LIST, 8);
    }

    private static NBTTagList getOrCreatePagesList(ItemStack stack) {
        if (stack.getTagCompound() == null) stack.setTagCompound(new NBTTagCompound());
        NBTTagCompound tag = stack.getTagCompound();

        if (!tag.hasKey(NBT_ROOT, 10)) tag.setTag(NBT_ROOT, new NBTTagCompound());
        NBTTagCompound root = tag.getCompoundTag(NBT_ROOT);

        if (!root.hasKey(NBT_LIST, 9)) root.setTag(NBT_LIST, new NBTTagList());
        return root.getTagList(NBT_LIST, 8);
    }

    private static void savePagesList(ItemStack stack, NBTTagList list) {
        if (stack.getTagCompound() == null) stack.setTagCompound(new NBTTagCompound());
        NBTTagCompound tag = stack.getTagCompound();

        if (!tag.hasKey(NBT_ROOT, 10)) tag.setTag(NBT_ROOT, new NBTTagCompound());
        NBTTagCompound root = tag.getCompoundTag(NBT_ROOT);

        root.setTag(NBT_LIST, list);

        // If empty, clean up to keep items tidy
        if (list == null || list.tagCount() == 0) {
            clearPages(stack);
        }
    }

    private static void clearPages(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) return;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(NBT_ROOT, 10)) return;

        NBTTagCompound root = tag.getCompoundTag(NBT_ROOT);
        if (root != null) root.removeTag(NBT_LIST);

        // If root is empty, remove root
        if (root == null || root.hasNoTags()) {
            tag.removeTag(NBT_ROOT);
        }

        // If tag is empty, remove compound
        if (tag.hasNoTags()) {
            stack.setTagCompound(null);
        }
    }

    // ─────────────────────────────────────────────
    // Utility helpers
    // ─────────────────────────────────────────────

    private static void msg(EntityPlayer p, String s) {
        p.addChatMessage(new ChatComponentText(s));
    }

    private static void requireArgs(String[] args, int min, ICommandSender sender) {
        if (args == null || args.length < min) throw new WrongUsageException(((CommandBase) (Object) new LorePagesCommand()).getCommandUsage(sender));
    }

    private static String joinFrom(String[] args, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (i > start) sb.append(" ");
            sb.append(args[i]);
        }
        return sb.toString();
    }

    private static int parseIntBounded(EntityPlayer p, String s, int min, int max) {
        try {
            int v = Integer.parseInt(s);
            if (v < min) v = min;
            if (v > max) v = max;
            return v;
        } catch (Throwable t) {
            msg(p, "§cInvalid number: §f" + s);
            return min;
        }
    }

    private static List<String> toStringList(NBTTagList list) {
        List<String> out = new ArrayList<String>();
        if (list == null) return out;
        for (int i = 0; i < list.tagCount(); i++) out.add(list.getStringTagAt(i));
        return out;
    }

    private static NBTTagList fromStringList(List<String> pages) {
        NBTTagList list = new NBTTagList();
        if (pages == null) return list;
        for (int i = 0; i < pages.size(); i++) {
            String s = pages.get(i);
            if (s == null) s = "";
            list.appendTag(new net.minecraft.nbt.NBTTagString(s));
        }
        return list;
    }

    private static String preview(String s, int max) {
        if (s == null) return "";
        String t = s.replace("\\n", " ").replace("\n", " ").replace("\r", " ");
        if (t.length() <= max) return t;
        return t.substring(0, Math.max(0, max - 3)) + "...";
    }
}
