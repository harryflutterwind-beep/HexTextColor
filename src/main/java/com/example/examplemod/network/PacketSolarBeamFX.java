package com.example.examplemod.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Client FX packet: renders a short-lived "rarity-style" beam between two points.
 *
 * IMPORTANT (1.7.10):
 * - This class must be safe to load on a dedicated server.
 * - Therefore, the handler uses reflection (no net.minecraft.client.* imports).
 */
public class PacketSolarBeamFX implements IMessage {

    public double sx, sy, sz;
    public double ex, ey, ez;
    public int botRGB;
    public int topRGB;
    public byte chaosOrdinal;
    public boolean evolved;
    public float radiusScale;
    public int lifeTicks;

    public PacketSolarBeamFX() {}

    public PacketSolarBeamFX(double sx, double sy, double sz,
                             double ex, double ey, double ez,
                             int botRGB, int topRGB,
                             byte chaosOrdinal, boolean evolved,
                             float radiusScale, int lifeTicks) {
        this.sx = sx; this.sy = sy; this.sz = sz;
        this.ex = ex; this.ey = ey; this.ez = ez;
        this.botRGB = botRGB;
        this.topRGB = topRGB;
        this.chaosOrdinal = chaosOrdinal;
        this.evolved = evolved;
        this.radiusScale = radiusScale;
        this.lifeTicks = lifeTicks;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeDouble(sx);
        buf.writeDouble(sy);
        buf.writeDouble(sz);
        buf.writeDouble(ex);
        buf.writeDouble(ey);
        buf.writeDouble(ez);
        buf.writeInt(botRGB);
        buf.writeInt(topRGB);
        buf.writeByte(chaosOrdinal);
        buf.writeBoolean(evolved);
        buf.writeFloat(radiusScale);
        buf.writeInt(lifeTicks);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        sx = buf.readDouble();
        sy = buf.readDouble();
        sz = buf.readDouble();
        ex = buf.readDouble();
        ey = buf.readDouble();
        ez = buf.readDouble();
        botRGB = buf.readInt();
        topRGB = buf.readInt();
        chaosOrdinal = buf.readByte();
        evolved = buf.readBoolean();
        radiusScale = buf.readFloat();
        lifeTicks = buf.readInt();
    }

    public static class Handler implements IMessageHandler<PacketSolarBeamFX, IMessage> {

        @Override
        public IMessage onMessage(final PacketSolarBeamFX msg, final MessageContext ctx) {

            // No-op if this ever hits the server by mistake.
            try {
                if (ctx == null || ctx.side == null || !ctx.side.isClient()) return null;
            } catch (Throwable ignored) {}

            try {
                // Minecraft mc = Minecraft.getMinecraft();
                Class<?> mcCls = Class.forName("net.minecraft.client.Minecraft");
                final Object mc = mcCls.getMethod("getMinecraft").invoke(null);
                if (mc == null) return null;

                final Runnable r = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            // ItemBeamRenderer.addTransientBeam(...)
                            Class<?> beamCls = Class.forName("com.example.examplemod.client.ItemBeamRenderer");
                            beamCls.getMethod("addTransientBeam",
                                    double.class, double.class, double.class,
                                    double.class, double.class, double.class,
                                    int.class, int.class,
                                    byte.class, boolean.class,
                                    float.class, int.class
                            ).invoke(null,
                                    msg.sx, msg.sy, msg.sz,
                                    msg.ex, msg.ey, msg.ez,
                                    msg.botRGB, msg.topRGB,
                                    msg.chaosOrdinal, msg.evolved,
                                    msg.radiusScale, msg.lifeTicks
                            );
                        } catch (Throwable ignored) {}
                    }
                };

                // 1.7.10 uses func_152344_a; newer builds have addScheduledTask
                try {
                    mcCls.getMethod("addScheduledTask", Runnable.class).invoke(mc, r);
                } catch (Throwable ignored) {
                    try {
                        mcCls.getMethod("func_152344_a", Runnable.class).invoke(mc, r);
                    } catch (Throwable ignored2) {
                        r.run();
                    }
                }

            } catch (Throwable ignored) {}

            return null;
        }
    }
}
