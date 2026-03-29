package com.example.examplemod.server;

import com.example.examplemod.beams.RarityDetect;
import com.example.examplemod.beams.RarityTags;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;

public final class DropTagHandler {

    /**
     * Flip this on while testing. Blank filter = log all items.
     * Example filter: "minecraft:leather" or "minecraft:rotten_flesh"
     */
    public static boolean DEBUG = false;
    public static boolean DEBUG_TO_CHAT = true;
    public static String DEBUG_ITEM_FILTER = "";

    public static void setDebug(boolean enabled) {
        DEBUG = enabled;
    }

    public static boolean toggleDebug() {
        DEBUG = !DEBUG;
        return DEBUG;
    }

    @SubscribeEvent
    public void onItemToss(ItemTossEvent e) {
        tagEntityItem(e.entityItem);
    }

    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent e) {
        if (e.world.isRemote) return;

        if (e.entity instanceof EntityItem) {
            tagEntityItem((EntityItem) e.entity);
            return;
        }

        if (e.entity instanceof EntityPlayer) {
            sanitizePlayerInventory((EntityPlayer) e.entity);
        }
    }

    @SubscribeEvent
    public void onItemPickup(EntityItemPickupEvent e) {
        if (e == null || e.item == null) return;

        EntityPlayer player = e.entityPlayer;
        ItemStack ground = e.item.getEntityItem();

        if (DEBUG && shouldDebug(ground)) {
            debugPickup(player, "before", ground);
        }

        tagEntityItem(e.item);
        if (player != null) sanitizePlayerInventory(player);

        if (DEBUG && shouldDebug(ground)) {
            debugPickup(player, "after", e.item.getEntityItem());
        }
    }

    private void tagEntityItem(EntityItem ei) {
        if (ei == null) return;
        ItemStack stack = ei.getEntityItem();
        normalizeStackTag(stack);
    }

    private void sanitizePlayerInventory(EntityPlayer player) {
        if (player == null || player.inventory == null || player.inventory.mainInventory == null) return;

        ItemStack[] inv = player.inventory.mainInventory;
        for (int i = 0; i < inv.length; i++) {
            normalizeStackTag(inv[i]);
        }
        for (int i = 0; i < player.inventory.armorInventory.length; i++) {
            normalizeStackTag(player.inventory.armorInventory[i]);
        }
    }

    private void normalizeStackTag(ItemStack stack) {
        if (stack == null) return;

        NBTTagCompound tag = stack.getTagCompound();
        String detected = RarityDetect.fromStack(stack);
        if (detected == null) detected = "";

        if (detected.isEmpty()) {
            if (tag == null) return;

            boolean removed = false;
            if (tag.hasKey(RarityTags.KEY)) {
                tag.removeTag(RarityTags.KEY);
                removed = true;
            }
            if (tag.hasKey(RarityTags.HKEY)) {
                tag.removeTag(RarityTags.HKEY);
                removed = true;
            }

            if (tag.hasNoTags()) {
                stack.setTagCompound(null);
                if (DEBUG && shouldDebug(stack)) {
                    debug("cleared empty tag from " + describe(stack));
                }
            } else {
                stack.setTagCompound(tag);
                if (DEBUG && removed && shouldDebug(stack)) {
                    debug("removed rarity keys but kept other tag data on " + describe(stack));
                }
            }
            return;
        }

        if (tag == null) tag = new NBTTagCompound();

        String stamped = tag.hasKey(RarityTags.KEY) ? tag.getString(RarityTags.KEY) : "";
        int expectedHeight = RarityDetect.beamHeight(detected);
        int stampedHeight = tag.hasKey(RarityTags.HKEY) ? tag.getInteger(RarityTags.HKEY) : Integer.MIN_VALUE;

        if (!detected.equalsIgnoreCase(stamped) || stampedHeight != expectedHeight) {
            tag.setString(RarityTags.KEY, detected);
            tag.setInteger(RarityTags.HKEY, expectedHeight);
            stack.setTagCompound(tag);
            if (DEBUG && shouldDebug(stack)) {
                debug("stamped rarity tags on " + describe(stack) + " detected=" + detected + " height=" + expectedHeight);
            }
        }
    }

    private void debugPickup(EntityPlayer player, String phase, ItemStack ground) {
        sendDebug(player, "§7[DropDebug] §f" + phase + " ground: " + describe(ground));
        if (player == null || player.inventory == null || player.inventory.mainInventory == null || ground == null) return;

        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            ItemStack in = player.inventory.mainInventory[i];
            if (in == null) continue;
            if (!sameBaseItem(in, ground)) continue;
            sendDebug(player, "§7[DropDebug] §fslot " + i + ": " + describe(in));
        }
    }

    private boolean sameBaseItem(ItemStack a, ItemStack b) {
        if (a == null || b == null) return false;
        if (a.getItem() != b.getItem()) return false;
        return a.getItemDamage() == b.getItemDamage();
    }

    private boolean shouldDebug(ItemStack stack) {
        if (!DEBUG || stack == null) return false;
        String filter = DEBUG_ITEM_FILTER == null ? "" : DEBUG_ITEM_FILTER.trim();
        if (filter.isEmpty()) return true;
        return filter.equalsIgnoreCase(getRegistryName(stack));
    }

    private String describe(ItemStack stack) {
        if (stack == null) return "<null>";
        String id = getRegistryName(stack);
        String tag = stack.hasTagCompound() ? stack.getTagCompound().toString() : "null";
        return id + " x" + stack.stackSize + " dmg=" + stack.getItemDamage() + " detected=" + safe(RarityDetect.fromStack(stack)) + " tag=" + tag;
    }

    private String getRegistryName(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return "<null>";
        Object o = Item.itemRegistry.getNameForObject(stack.getItem());
        return o == null ? String.valueOf(stack.getItem()) : String.valueOf(o);
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private void sendDebug(EntityPlayer player, String msg) {
        debug(stripColors(msg));
        if (DEBUG_TO_CHAT && player != null) {
            player.addChatMessage(new net.minecraft.util.ChatComponentText(msg));
        }
    }

    private void debug(String msg) {
        System.out.println("[DropTagHandler] " + msg);
    }

    private String stripColors(String s) {
        if (s == null) return "";
        return s.replaceAll("§.", "");
    }
}
