package com.example.examplemod.dragon;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingDropsEvent;

public final class DragonLootHandler {

    public static final List<DropRule> RULES = new CopyOnWriteArrayList<DropRule>();

    public DragonLootHandler() {}

    @SubscribeEvent
    public void onDragonDrops(LivingDropsEvent e) {
        if (!(e.entityLiving instanceof EntityDragon)) {
            return;
        }
        if (RULES.isEmpty()) {
            return;
        }

        // MCP mappings instead of field_70170_p / field_73011_w / field_76574_g
        WorldServer ws = (WorldServer) e.entityLiving.worldObj;
        int dim = ws.provider.dimensionId;

        Random rng = e.entityLiving.getRNG();

        int looting = 0;
        Entity src = (e.source != null) ? e.source.getEntity() : null;
        if (src instanceof EntityLivingBase) {
            // MCP name instead of func_77519_f
            looting = EnchantmentHelper.getLootingModifier((EntityLivingBase) src);
        }

        for (DropRule r : RULES) {
            if (r.item == null) continue;
            if (r.dimFilter != null && r.dimFilter.intValue() != dim) continue;

            float bonus = r.chance * r.lootingScale * (float) Math.max(0, looting);
            float p = Math.max(0.0F, Math.min(1.0F, r.chance + bonus));
            if (rng.nextFloat() > p) continue;

            int count = r.min + rng.nextInt(r.max - r.min + 1);
            ItemStack stack = new ItemStack(r.item, count, r.meta);

            if (r.nbtJson != null && !r.nbtJson.trim().isEmpty()) {
                try {
                    // In 1.7.10 this name is usually still func_150315_a;
                    // if it errors, change to JsonToNBT.getTagFromJson(r.nbtJson);
                    NBTBase parsed = JsonToNBT.func_150315_a(r.nbtJson);
                    if (parsed instanceof NBTTagCompound) {
                        // MCP name instead of func_77982_d
                        stack.setTagCompound((NBTTagCompound) parsed);
                    }
                } catch (NBTException ignored) {
                }
            }

            // MCP names instead of field_70165_t / field_70163_u / field_70161_v / field_145804_b
            EntityItem drop = new EntityItem(
                    ws,
                    e.entityLiving.posX,
                    e.entityLiving.posY,
                    e.entityLiving.posZ,
                    stack
            );
            drop.delayBeforeCanPickup = 10;

            e.drops.add(drop);
        }
    }

    public static Item resolveItem(String idOrDomainColonPath) {
        try {
            // MCP: Item.itemRegistry instead of field_150901_e.func_82594_a
            Object o = Item.itemRegistry.getObject(idOrDomainColonPath);
            return (o instanceof Item) ? (Item) o : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public static final class DropRule {
        public final Item item;
        public final int meta;
        public final float chance;
        public final int min;
        public final int max;
        public final String nbtJson;
        public final Integer dimFilter;
        public final float lootingScale;

        public DropRule(Item item, int meta, float chance, int min, int max,
                        String nbtJson, Integer dimFilter, float lootingScale) {

            this.item = item;
            this.meta = meta;
            this.chance = chance;
            this.min = Math.max(1, min);
            this.max = Math.max(this.min, max);
            this.nbtJson = nbtJson;
            this.dimFilter = dimFilter;
            this.lootingScale = Math.max(0.0F, lootingScale);
        }
    }
}
