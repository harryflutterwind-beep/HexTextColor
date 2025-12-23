// src/main/java/com/example/examplemod/server/ChatHexServer.java
package com.example.examplemod.server;

import com.example.examplemod.client.ChatSendHook;
import com.example.examplemod.client.HexChatExpand;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.event.ServerChatEvent;

public class ChatHexServer {

    @SubscribeEvent
    public void onChat(ServerChatEvent e) {
        try {
            String raw = e.message;
            if (raw == null) raw = "";

            // 1) Only process REAL player chat where the username is actually the player
            EntityPlayer player = e.player;
            if (player == null) {
                // NPC / console / fake sender → leave completely alone
                return;
            }
            if (!player.getCommandSenderName().equals(e.username)) {
                // Likely NPC / system text using a fake username (like "Baby Vegeta Final")
                // → do NOT touch it so CNPC's own formatting stays intact
                return;
            }

            // 2) Only process messages that actually use our syntax
            boolean hasOurSyntax =
                    ChatSendHook.containsOurSyntax(raw) ||
                            raw.matches("(?i).*[&][0-9A-FK-OR].*") ||
                            raw.matches("(?i).*&##[0-9A-F]{6}.*");

            if (!hasOurSyntax) {
                // Vanilla-style player chat → let Minecraft handle it
                return;
            }

            // 3) Expand our tags and &-codes in the message text
            String msg = raw;

            msg = HexChatExpand.expandGradients(msg);
            msg = HexChatExpand.expandRainbow(msg);
            msg = HexChatExpand.expandPulse(msg);

            // inline formatting (&c → §c, &##RRGGBB → §#RRGGBB)
            msg = msg.replaceAll("(?i)&([0-9A-FK-OR])", "§$1");
            msg = msg.replaceAll("&##([0-9A-Fa-f]{6})", "§#$1");

            ChatComponentText msgComp = new ChatComponentText(msg);

            // 4) For normal "<name> msg" chat, just replace the message arg
            if (e.component != null) {
                // In your mappings this is already ChatComponentTranslation,
                // so the cast is just to make 'tr' typed correctly.
                ChatComponentTranslation tr = (ChatComponentTranslation) e.component;

                if ("chat.type.text".equals(tr.getKey())) {
                    Object[] args = tr.getFormatArgs();
                    if (args != null && args.length >= 2) {
                        // args[0] = name / prefix, args[1] = message
                        args[1] = msgComp;
                    }
                }
            }

            // No need for any fallback assignments; NPC / system messages
            // are already skipped by the checks above.

        } catch (Throwable t) {
            System.out.println("[HexChatDbg][SERVER] ERROR: " + t);
        }
    }
}
