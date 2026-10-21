package com.scmarket.mixin;

import com.scmarket.ScMarketClient;
import com.scmarket.diagnostics.LocalActionRecorder;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Opt-in action metadata; target coordinates, names, chat, and key contents are intentionally omitted. */
@Mixin(MultiPlayerGameMode.class)
public abstract class ClientPlayerInteractionRecorder {
    @Inject(method = "useItem", at = @At("HEAD"))
    private void scmarket$recordUseItem(LocalPlayer player, InteractionHand hand,
                                        CallbackInfoReturnable<InteractionResult> cir) {
        scmarket$record("use_item", player.getItemInHand(hand));
    }

    @Inject(method = "startDestroyBlock", at = @At("HEAD"))
    private void scmarket$recordAttackBlock(BlockPos position, Direction direction,
                                            CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null) {
            scmarket$record("attack_block", player.getMainHandItem());
        }
    }

    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void scmarket$recordInteractBlock(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                                              CallbackInfoReturnable<InteractionResult> cir) {
        scmarket$record("interact_block", player.getItemInHand(hand));
    }

    @Inject(method = "attack", at = @At("HEAD"))
    private void scmarket$recordAttackEntity(Player player, Entity target, CallbackInfo ci) {
        scmarket$record("attack_entity", player.getMainHandItem());
    }

    @Inject(method = "interact", at = @At("HEAD"))
    private void scmarket$recordInteractEntity(Player player, Entity target, InteractionHand hand,
                                               CallbackInfoReturnable<InteractionResult> cir) {
        scmarket$record("interact_entity", player.getItemInHand(hand));
    }

    @Inject(method = "interactAt", at = @At("HEAD"))
    private void scmarket$recordInteractEntityAtLocation(
            Player player, Entity target, EntityHitResult hit, InteractionHand hand,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        scmarket$record("interact_entity", player.getItemInHand(hand));
    }

    private static void scmarket$record(String action, ItemStack stack) {
        LocalActionRecorder recorder = ScMarketClient.actionRecorder();
        if (recorder == null || !recorder.isRecording()) {
            return;
        }
        String itemId = stack == null || stack.isEmpty()
                ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        recorder.recordPlayerAction(action, itemId);
    }
}
