package com.example.examplemod.transform;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.world.World;
import com.example.examplemod.item.ItemTransformWeapon;

public class TransformHandler {

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        EntityPlayer p = e.player;
        World w = p.worldObj;
        if (w.isRemote) return;

        boolean transformed = false;
        ItemStack held = p.getHeldItem();

        if (held != null && held.getItem() instanceof ItemTransformWeapon) {
            transformed = ItemTransformWeapon.isTransformed(held);
            ItemTransformWeapon.tickCooldown(held);
        } else {
            for (ItemStack s : p.inventory.mainInventory) {
                if (s != null && s.getItem() instanceof ItemTransformWeapon) {
                    ItemTransformWeapon.tickCooldown(s);
                    if (ItemTransformWeapon.isTransformed(s)) transformed = true;
                }
            }
        }

        if (transformed) {
            p.addPotionEffect(new PotionEffect(Potion.damageBoost.id, 40, 1, true));
            p.addPotionEffect(new PotionEffect(Potion.moveSpeed.id, 40, 0, true));
        }
    }
}
