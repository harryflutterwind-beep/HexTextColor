package com.example.examplemod.server;

import com.example.examplemod.beams.RarityDetect;
import com.example.examplemod.beams.RarityTags;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;

public final class DropTagHandler {

    @SubscribeEvent
    public void onItemToss(ItemTossEvent e) {
        tagEntityItem(e.entityItem);
    }

    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent e) {
        if (e.world.isRemote) return;
        if (e.entity instanceof EntityItem) tagEntityItem((EntityItem) e.entity);
    }

    private void tagEntityItem(EntityItem ei) {
        if (ei == null) return;
        ItemStack stack = ei.getEntityItem();
        if (stack == null) return;

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) tag = new NBTTagCompound();

        String detected = RarityDetect.fromStack(stack);
        if (detected == null) detected = "";

        if (detected.isEmpty()) {
            if (tag.hasKey(RarityTags.KEY)) {
                tag.removeTag(RarityTags.KEY);
                tag.removeTag(RarityTags.HKEY);
                stack.setTagCompound(tag);
            }
            return;
        }

        String stamped = tag.hasKey(RarityTags.KEY) ? tag.getString(RarityTags.KEY) : "";
        if (!detected.equalsIgnoreCase(stamped)) {
            tag.setString(RarityTags.KEY, detected);
            tag.setInteger(RarityTags.HKEY, RarityDetect.beamHeight(detected));
            stack.setTagCompound(tag);
        }
    }
}
