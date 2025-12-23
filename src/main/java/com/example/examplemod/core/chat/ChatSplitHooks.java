package com.example.examplemod.core.chat;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.IChatComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * ChatSplitHooks – thin wrapper around FontRenderer.listFormattedStringToWidth.
 *
 * The idea:
 *   - Let HexFontRenderer handle ALL tag logic + width + carry-over
 *   - We only convert the resulting lines back into IChatComponent
 *
 * This mirrors vanilla behavior but uses your Hex pipeline when fr is
 * a HexFontRenderer.
 */
public class ChatSplitHooks {

    public static List<IChatComponent> splitChatHook(
            IChatComponent original,
            int width,
            FontRenderer fr,
            boolean keepNewlines,
            boolean wrapLongWords
    ) {
        List<IChatComponent> out = new ArrayList<IChatComponent>();
        if (original == null || fr == null) {
            return out;
        }

        // Full formatted text (includes § codes and ALL <tags>)
        String raw = original.getFormattedText();
        ChatStyle baseStyle = original.getChatStyle();

        // IMPORTANT:
        //  - If ChatHexHandler has installed HexFontRenderer into fr,
        //    this call is:
        //      HexFontRenderer.listFormattedStringToWidth(raw, width)
        //    which:
        //      * measures width ignoring tags
        //      * wraps into lines
        //      * calls HexChatWrapFix.carryAnimatedAcross(lines)
        //        so ALL styles carry like vanilla colors
        @SuppressWarnings("unchecked")
        List<String> lines = fr.listFormattedStringToWidth(raw, width);

        // Wrap each string line back into a chat component
        for (String line : lines) {
            ChatComponentText cc = new ChatComponentText(line);
            if (baseStyle != null) {
                cc.setChatStyle(baseStyle.createShallowCopy());
            }
            out.add(cc);
        }

        return out;
    }
}
