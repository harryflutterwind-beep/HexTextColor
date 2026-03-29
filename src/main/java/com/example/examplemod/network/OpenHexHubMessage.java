package com.example.examplemod.network;

import com.example.examplemod.client.HexHubClientHooks;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class OpenHexHubMessage implements IMessage {

    @Override
    public void toBytes(ByteBuf buf) {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
    }

    public static class Handler implements IMessageHandler<OpenHexHubMessage, IMessage> {
        @Override
        public IMessage onMessage(OpenHexHubMessage message, MessageContext ctx) {
            HexHubClientHooks.OPEN_HUB_NEXT_TICK = true;
            return null;
        }
    }
}