// src/main/java/com/example/examplemod/command/LorePagesCommand.java
package com.example.examplemod.command;

import com.example.examplemod.api.LorePagesAPI;
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
 * /lorepages testicon
 *
 * Stored in:
 *   stack.tag.HexLorePages.Pages (NBTTagList<Compound>)
 *   each page compound stores its lines in key "L"
 *
 * Notes:
 *  - Use \n inside text to create multi-line pages
 *  - Legacy string-page items are read and migrated on write
 *  - Slot indices: 0-35 (0-8 hotbar, 9-35 inventory)
 */
public class LorePagesCommand extends CommandBase {

    private static final String NBT_ROOT = LorePagesAPI.NBT_ROOT;
    private static final String NBT_LIST = LorePagesAPI.NBT_PAGES;

    @Override
    public String getCommandName() {
        return "lorepages";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/lorepages add <text...> | insert <index> <text...> | remove <index> | clear | list | transfer <fromSlot> <toSlot> [move|copy] | testicon";
    }

    @Override
    public int getRequiredPermissionLevel() {
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

        if ("testicon".equals(sub)) {
            ItemStack held = p.getHeldItem();
            if (held == null) {
                msg(p, "§cHold an item first.");
                return;
            }

            String text =
                    "&7Icon test: <ico:gems/pill_dark_fire_face_64_anim> &fPurple\\n" +
                            "&7Bigger: <ico:gems/pill_dark_fire_face_64_anim> &fBigger one\\n" +
                            "&7Inline: &aAAA <ico:gems/pill_dark_fire_face_64_anim> &bBBB";

            List<List<String>> pages = readPages(held);
            pages.add(pageTextToLines(text));
            writePages(held, pages);

            msg(p, "§aAdded icon test page §f#" + pages.size() + "§a. Open lore pages and hover.");
            return;
        }

        if ("add".equals(sub)) {
            requireArgs(args, 2, sender);
            ItemStack held = p.getHeldItem();
            if (held == null) {
                msg(p, "§cHold an item first.");
                return;
            }
            String text = joinFrom(args, 1);
            List<List<String>> pages = readPages(held);
            pages.add(pageTextToLines(text));
            writePages(held, pages);
            msg(p, "§aAdded page §f#" + pages.size() + "§a.");
            return;
        }

        if ("insert".equals(sub)) {
            requireArgs(args, 3, sender);
            ItemStack held = p.getHeldItem();
            if (held == null) {
                msg(p, "§cHold an item first.");
                return;
            }
            int idx = parseIntBounded(p, args[1], 1, 9999) - 1;
            String text = joinFrom(args, 2);

            List<List<String>> pages = readPages(held);
            if (idx < 0) idx = 0;
            if (idx > pages.size()) idx = pages.size();
            pages.add(idx, pageTextToLines(text));

            writePages(held, pages);
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

            List<List<String>> pages = readPages(held);
            if (pages.isEmpty()) {
                msg(p, "§7No pages to remove.");
                return;
            }

            if (idx < 0 || idx >= pages.size()) {
                msg(p, "§cInvalid page index. Use /lorepages list");
                return;
            }

            pages.remove(idx);
            if (pages.isEmpty()) {
                clearPages(held);
                msg(p, "§aRemoved last page; pages cleared.");
            } else {
                writePages(held, pages);
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
            List<List<String>> pages = readPages(held);
            if (pages.isEmpty()) {
                msg(p, "§7No pages stored on this item.");
                return;
            }
            msg(p, "§b§oPages: §f" + pages.size());
            for (int i = 0; i < pages.size(); i++) {
                String preview = preview(joinPageLines(pages.get(i)), 42);
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

            List<List<String>> fromPages = readPages(from);
            if (fromPages.isEmpty()) {
                msg(p, "§7No pages on fromSlot item.");
                return;
            }

            writePages(to, deepCopyPages(fromPages));

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

    private static List<List<String>> readPages(ItemStack stack) {
        List<List<String>> pages = LorePagesAPI.readPagesFromNBT(stack);
        if (pages != null && !pages.isEmpty()) {
            return deepCopyPages(pages);
        }
        return readLegacyStringPages(stack);
    }

    private static void writePages(ItemStack stack, List<List<String>> pages) {
        if (pages == null || pages.isEmpty()) {
            LorePagesAPI.clearPagesNBT(stack);
            return;
        }
        LorePagesAPI.writePagesToNBT(stack, pages);
    }

    private static void clearPages(ItemStack stack) {
        LorePagesAPI.clearPagesNBT(stack);
    }

    private static List<List<String>> readLegacyStringPages(ItemStack stack) {
        List<List<String>> out = new ArrayList<List<String>>();
        if (stack == null || !stack.hasTagCompound()) return out;

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(NBT_ROOT, 10)) return out;

        NBTTagCompound root = tag.getCompoundTag(NBT_ROOT);
        if (root == null || !root.hasKey(NBT_LIST, 9)) return out;

        NBTTagList list = root.getTagList(NBT_LIST, 8);
        if (list == null || list.tagCount() <= 0) return out;

        for (int i = 0; i < list.tagCount(); i++) {
            out.add(pageTextToLines(list.getStringTagAt(i)));
        }
        return out;
    }

    private static List<String> pageTextToLines(String text) {
        List<String> lines = new ArrayList<String>();
        if (text == null) {
            lines.add("");
            return lines;
        }

        String normalized = text.replace("\r", "").replace("\\n", "\n");
        String[] split = normalized.split("\n", -1);
        if (split.length == 0) {
            lines.add("");
            return lines;
        }

        for (int i = 0; i < split.length; i++) {
            lines.add(split[i] == null ? "" : split[i]);
        }
        return lines;
    }

    private static String joinPageLines(List<String> page) {
        if (page == null || page.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < page.size(); i++) {
            if (i > 0) sb.append(" ");
            sb.append(page.get(i) == null ? "" : page.get(i));
        }
        return sb.toString();
    }

    private static List<List<String>> deepCopyPages(List<List<String>> pages) {
        List<List<String>> out = new ArrayList<List<String>>();
        if (pages == null) return out;
        for (int i = 0; i < pages.size(); i++) {
            List<String> src = pages.get(i);
            List<String> dst = new ArrayList<String>();
            if (src != null) {
                for (int j = 0; j < src.size(); j++) {
                    dst.add(src.get(j));
                }
            }
            out.add(dst);
        }
        return out;
    }

    private static void msg(EntityPlayer p, String s) {
        p.addChatMessage(new ChatComponentText(s));
    }

    private static void requireArgs(String[] args, int min, ICommandSender sender) {
        if (args == null || args.length < min)
            throw new WrongUsageException(((CommandBase) (Object) new LorePagesCommand()).getCommandUsage(sender));
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

    private static String preview(String s, int max) {
        if (s == null) return "";
        String t = s.replace("\\n", " ").replace("\n", " ").replace("\r", " ");
        if (t.length() <= max) return t;
        return t.substring(0, Math.max(0, max - 3)) + "...";
    }
}
