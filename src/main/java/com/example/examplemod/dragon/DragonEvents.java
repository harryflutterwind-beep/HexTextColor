// src/main/java/com/example/examplemod/dragon/DragonEvents.java
package com.example.examplemod.dragon;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityDragonPart;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

public final class DragonEvents {

    /** Apply configured HP as soon as a dragon spawns/joins the world. */
    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent e) {
        if (e.world.isRemote) return;
        if (!(e.entity instanceof EntityDragon)) return;

        EntityDragon d = (EntityDragon) e.entity;
        DragonUtils.DragonData data = DragonUtils.dataFor(e.world);
        float targetHp = Math.max(1f, data.effectiveHp);

        d.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(targetHp);
        d.setHealth(targetHp);
    }

    /** Scale dragon outgoing and incoming damage. */
    @SubscribeEvent
    public void onHurt(LivingHurtEvent e) {
        if (e.entity.worldObj.isRemote) return;

        DragonUtils.DragonData data = DragonUtils.dataFor(e.entity.worldObj);
        float dmgMultOut  = Math.max(0f, data.outgoingDamageMult);
        float effectiveHp = Math.max(1f, data.effectiveHp);

        // OUTGOING: if the source is the dragon or one of its parts, scale up/down.
        Entity src = e.source.getEntity();
        if (src instanceof EntityDragon || src instanceof EntityDragonPart) {
            if (dmgMultOut != 1f) e.ammount *= dmgMultOut;
        }

        // INCOMING: if the victim is the dragon, scale the damage to simulate effective HP.
        if (e.entity instanceof EntityDragon) {
            final float vanillaHp = 200f;
            float scale = vanillaHp / effectiveHp; // >1 → weaker, <1 → tougher
            if (scale != 1f) e.ammount *= scale;
        }
    }
}
//update to run on server
