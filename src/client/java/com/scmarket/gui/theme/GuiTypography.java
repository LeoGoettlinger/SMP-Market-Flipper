package com.scmarket.gui.theme;

import com.scmarket.ModConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/** Applies the bundled Geist Sans face without replacing Minecraft's global font. */
public final class GuiTypography {
    private static final Style INTERFACE_STYLE = Style.EMPTY.withFont(
            new FontDescription.Resource(Identifier.fromNamespaceAndPath(ModConstants.MOD_ID, "interface"))
    );

    private GuiTypography() {
    }

    public static Component text(String value) {
        return Component.literal(value).setStyle(INTERFACE_STYLE);
    }

    public static int width(Font renderer, String value) {
        return renderer.width(text(value));
    }

    public static void draw(
            GuiGraphics context,
            Font renderer,
            String value,
            int x,
            int y,
            int color,
            boolean shadow
    ) {
        context.drawString(renderer, text(value), x, y, color, shadow);
    }

    public static void drawCentered(
            GuiGraphics context,
            Font renderer,
            String value,
            int centerX,
            int y,
            int color
    ) {
        Component styled = text(value);
        context.drawString(renderer, styled, centerX - renderer.width(styled) / 2,
                y, color, false);
    }

}
