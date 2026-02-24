package com.example.examplemod.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/**
 * Server -> Client: spawn a one-shot Blackflame spray (custom particle using blackflame.png).
 *
 * This is purely visual. Damage / charge / effects are handled server-side.
 */
public class PacketBlackflameSprayFX implements IMessage {

    public float sx, sy, sz;
    public float ex, ey, ez;

    public PacketBlackflameSprayFX() {}

    public PacketBlackflameSprayFX(float sx, float sy, float sz, float ex, float ey, float ez) {
        this.sx = sx; this.sy = sy; this.sz = sz;
        this.ex = ex; this.ey = ey; this.ez = ez;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        sx = buf.readFloat();
        sy = buf.readFloat();
        sz = buf.readFloat();
        ex = buf.readFloat();
        ey = buf.readFloat();
        ez = buf.readFloat();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeFloat(sx);
        buf.writeFloat(sy);
        buf.writeFloat(sz);
        buf.writeFloat(ex);
        buf.writeFloat(ey);
        buf.writeFloat(ez);
    }

    public static class Handler implements IMessageHandler<PacketBlackflameSprayFX, IMessage> {
        @Override
        public IMessage onMessage(final PacketBlackflameSprayFX msg, final MessageContext ctx) {
            if (ctx.side != Side.CLIENT) return null;

            try {
                final net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
                Runnable r = new Runnable() {
                    @Override public void run() {
                        try {
                            com.example.examplemod.client.fx.BlackflameSprayClientFX.spawn(msg.sx, msg.sy, msg.sz, msg.ex, msg.ey, msg.ez);
                        } catch (Throwable ignored) {}
                    }
                };

                // 1.7.10 uses func_152344_a; newer builds have addScheduledTask
                try {
                    mc.getClass().getMethod("addScheduledTask", Runnable.class).invoke(mc, r);
                } catch (Throwable ignored) {
                    try {
                        mc.getClass().getMethod("func_152344_a", Runnable.class).invoke(mc, r);
                    } catch (Throwable ignored2) {
                        r.run();
                    }
                }
            } catch (Throwable ignored) {}

            return null;
        }
    }
}
