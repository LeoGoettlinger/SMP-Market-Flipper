package com.scmarket.mixin;

import com.scmarket.ScMarketClient;
import com.scmarket.diagnostics.LocalActionRecorder;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Opt-in action metadata; target coordinates, names, chat, and key contents are intentionally omitted. */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionRecorder {
    @Inject(method = "interactItem", at = @At("HEAD"))
    private void scmarket$recordUseItem(ClientPlayerEntity player, Hand hand,
                                        CallbackInfoReturnable<ActionResult> cir) {
        scmarket$record("use_item", player.getStackInHand(hand));
    }

    @Inject(method = "attackBlock", at = @At("HEAD"))
    private void scmarket$recordAttackBlock(BlockPos position, Direction direction,
                                            CallbackInfoReturnable<Boolean> cir) {
        ClientPlayerEntity player = net.minecraft.client.MinecraftClient.getInstance().player;
        if (player != null) {
            scmarket$record("attack_block", player.getMainHandStack());
        }
    }

    @Inject(method = "interactBlock", at = @At("HEAD"))
    private void scmarket$recordInteractBlock(ClientPlayerEntity player, Hand hand, BlockHitResult hit,
                                              CallbackInfoReturnable<ActionResult> cir) {
        scmarket$record("interact_block", player.getStackInHand(hand));
    }

    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void scmarket$recordAttackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
        scmarket$record("attack_entity", player.getMainHandStack());
    }

    @Inject(method = "interactEntity", at = @At("HEAD"))
    private void scmarket$recordInteractEntity(PlayerEntity player, Entity target, Hand hand,
                                               CallbackInfoReturnable<ActionResult> cir) {
        scmarket$record("interact_entity", player.getStackInHand(hand));
    }

    @Inject(method = "interactEntityAtLocation", at = @At("HEAD"))
    private void scmarket$recordInteractEntityAtLocation(
            PlayerEntity player, Entity target, EntityHitResult hit, Hand hand,
            CallbackInfoReturnable<ActionResult> cir
    ) {
        scmarket$record("interact_entity", player.getStackInHand(hand));
    }

    private static void scmarket$record(String action, ItemStack stack) {
        LocalActionRecorder recorder = ScMarketClient.actionRecorder();
        if (recorder == null || !recorder.isRecording()) {
            return;
        }
        String itemId = stack == null || stack.isEmpty()
                ? "" : Registries.ITEM.getId(stack.getItem()).toString();
        recorder.recordPlayerAction(action, itemId);
    }
}
