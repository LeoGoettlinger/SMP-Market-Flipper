package com.scmarket.mixin;

import com.scmarket.ScMarketClient;
import com.scmarket.diagnostics.LocalActionRecorder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Records only explicit, opt-in handled-screen clicks; chat and keyboard text are never captured. */
@Mixin(HandledScreen.class)
public abstract class HandledScreenActionRecorder {
    @Invoker("getSlotAt")
    protected abstract Slot scmarket$invokeGetSlotAt(double x, double y);

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void scmarket$recordMarketGuiClick(Click click, boolean doubled,
                                               CallbackInfoReturnable<Boolean> cir) {
        LocalActionRecorder recorder = ScMarketClient.actionRecorder();
        if (recorder == null || !recorder.isRecording()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.currentScreen == null) {
            return;
        }
        Slot slot;
        try {
            slot = scmarket$invokeGetSlotAt(click.x(), click.y());
        } catch (RuntimeException unavailableSlot) {
            slot = null;
        }
        ItemStack stack = slot == null ? ItemStack.EMPTY : slot.getStack();
        String itemId = stack.isEmpty() ? "" : Registries.ITEM.getId(stack.getItem()).toString();
        Text title = client.currentScreen.getTitle();
        recorder.recordGuiClick(title == null ? "" : title.getString(),
                client.player.currentScreenHandler.syncId,
                slot == null ? -1 : slot.id,
                itemId,
                stack.getCount(),
                click.button(),
                (int) Math.round(click.x()),
                (int) Math.round(click.y()));
    }
}
