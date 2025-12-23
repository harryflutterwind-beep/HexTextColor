package com.example.examplemod.server;

import com.example.examplemod.core.WearableArmorHooks;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

public class WearableRightClickEquip {

    @SubscribeEvent
    public void onRightClick(PlayerInteractEvent e) {
        if (e == null || e.entityPlayer == null) return;

        // Server-side only
        if (e.entityPlayer.worldObj == null || e.entityPlayer.worldObj.isRemote) return;

        // Right click air or block
        if (e.action != PlayerInteractEvent.Action.RIGHT_CLICK_AIR
                && e.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;

        EntityPlayer p = e.entityPlayer;

        // Strongly recommended so you don’t break normal right-click behavior:
        // (remove this if you truly want "always right click equips")
        if (!p.isSneaking()) return;

        ItemStack held = p.getCurrentEquippedItem();
        if (held == null) return;

        // Reuse your existing "helmet wearable" logic:
        // WearableArmorHooks.allowExtraArmorItems(stack, armorType) where armorType 0 = helmet
        if (!WearableArmorHooks.allowExtraArmorItems(held, 0)) return;

        // Helmet slot in 1.7.10: armorInventory[3]
        if (p.inventory.armorInventory[3] != null) return; // only equip if empty (vanilla-like)

        // Equip ONE item
        ItemStack one = held.copy();
        one.stackSize = 1;
        p.inventory.armorInventory[3] = one;

        // Consume one from hand
        held.stackSize--;
        if (held.stackSize <= 0) {
            p.inventory.setInventorySlotContents(p.inventory.currentItem, null);
        }

        p.inventory.markDirty();
        p.inventoryContainer.detectAndSendChanges();

        // Prevent block placement / use from also firing
        e.setCanceled(true);
    }
}
