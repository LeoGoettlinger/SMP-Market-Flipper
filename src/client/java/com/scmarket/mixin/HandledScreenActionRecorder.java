package com.scmarket.mixin;

import com.scmarket.ScMarketClient;
import com.scmarket.diagnostics.LocalActionRecorder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Records only explicit, opt-in handled-screen clicks; chat and keyboard text are never captured. */
@Mixin(AbstractContainerScreen.class)
public abstract class HandledScreenActionRecorder {
    @Invoker("getHoveredSlot")
    protected abstract Slot scmarket$invokeGetSlotAt(double x, double y);

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void scmarket$recordMarketGuiClick(MouseButtonEvent click, boolean doubled,
                                               CallbackInfoReturnable<Boolean> cir) {
        LocalActionRecorder recorder = ScMarketClient.actionRecorder();
        if (recorder == null || !recorder.isRecording()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gui.screen() == null) {
            return;
        }
        Slot slot;
        try {
            slot = scmarket$invokeGetSlotAt(click.x(), click.y());
        } catch (RuntimeException unavailableSlot) {
            slot = null;
        }
        AbstractContainerScreen<?> handledScreen = (AbstractContainerScreen<?>)(Object)this;
        AbstractContainerMenu menu = handledScreen.getMenu();
        ItemStack stack = slot == null ? ItemStack.EMPTY : slot.getItem();
        String itemId = stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        String displayName = stack.isEmpty() ? "" : stack.getHoverName().getString();
        ItemLore itemLore = stack.isEmpty() ? null : stack.get(DataComponents.LORE);
        List<String> lore = itemLore == null ? List.of() : itemLore.lines().stream()
                .map(Component::getString).toList();
        Component title = client.gui.screen().getTitle();
        recorder.recordGuiClick(title == null ? "" : title.getString(),
                menu.containerId,
                slot == null ? -1 : menu.slots.indexOf(slot),
                itemId,
                stack.getCount(),
                displayName,
                lore,
                click.button(),
                (int) Math.round(click.x()),
                (int) Math.round(click.y()));
    }
}
