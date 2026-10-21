package com.scmarket.gui.panel;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

public interface TabContentPanel {
    void render(GuiGraphicsExtractor context, Font textRenderer, int mouseX, int mouseY);

    default boolean mouseClicked(int mouseX, int mouseY, int button) {
        return false;
    }

    default boolean mouseScrolled(int mouseX, int mouseY, double verticalAmount) {
        return false;
    }

    default boolean keyPressed(KeyEvent input) {
        return false;
    }

    default boolean charTyped(CharacterEvent input) {
        return false;
    }
}
