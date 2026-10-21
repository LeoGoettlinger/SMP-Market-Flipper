package com.scmarket.mixin;

import com.scmarket.ScMarketClient;
import com.scmarket.diagnostics.LocalActionRecorder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Captures screen-relative clicks in non-container dialogs (including sign-entry screens), never text. */
@Mixin(Screen.class)
public abstract class ScreenActionRecorder {
    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void scmarket$recordDialogClick(MouseButtonEvent click, boolean doubled,
                                            CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof AbstractContainerScreen<?>) {
            return; // The more detailed slot-aware hook records these once.
        }
        LocalActionRecorder recorder = ScMarketClient.actionRecorder();
        if (recorder == null || !recorder.isRecording()) {
            return;
        }
        Screen screen = (Screen) (Object) this;
        Minecraft client = Minecraft.getInstance();
        int syncId = client.player == null ? -1 : client.player.containerMenu.containerId;
        recorder.recordGuiClick(screen.getTitle().getString(), syncId, -1, "", 0,
                click.button(), (int) Math.round(click.x()), (int) Math.round(click.y()));
    }
}
