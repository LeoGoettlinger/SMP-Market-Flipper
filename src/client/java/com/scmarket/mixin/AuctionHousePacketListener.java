package com.scmarket.mixin;

import com.scmarket.packet.AuctionHouseDataCapture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.client.network.ClientConnectionState;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.s2c.play.CloseScreenS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Intercepts auction house screen packets from the server.
 * When the AH screen opens, captured slot data is pushed into {@link AuctionHouseDataCapture}.
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class AuctionHousePacketListener extends ClientCommonNetworkHandler {
    @Unique
    private static volatile AuctionHouseDataCapture scmarket$capture;

    protected AuctionHousePacketListener(MinecraftClient client, ClientConnection connection, ClientConnectionState connectionState) {
        super(client, connection, connectionState);
    }

    public static void setCapture(AuctionHouseDataCapture capture) {
        scmarket$capture = capture;
    }

    @Inject(method = "onOpenScreen", at = @At("TAIL"))
    private void scmarket$onOpenScreen(OpenScreenS2CPacket packet, CallbackInfo ci) {
        AuctionHouseDataCapture capture = scmarket$capture;
        if (capture == null) {
            return;
        }
        String title = packet.getTitle().getString();
        int syncId = packet.getSyncId();
        capture.onScreenOpened(syncId, title);
    }

    @Inject(method = "onInventory", at = @At("TAIL"))
    private void scmarket$onInventory(InventoryS2CPacket packet, CallbackInfo ci) {
        AuctionHouseDataCapture capture = scmarket$capture;
        if (capture == null) {
            return;
        }
        int syncId = packet.getSyncId();
        List<ItemStack> contents = packet.getContents();
        capture.onInventorySynced(syncId, contents);
    }

    @Inject(method = "onScreenHandlerSlotUpdate", at = @At("TAIL"))
    private void scmarket$onSlotUpdate(ScreenHandlerSlotUpdateS2CPacket packet, CallbackInfo ci) {
        AuctionHouseDataCapture capture = scmarket$capture;
        if (capture == null) {
            return;
        }
        int syncId = packet.getSyncId();
        int slot = packet.getSlot();
        ItemStack stack = packet.getStack();
        capture.onSlotUpdated(syncId, slot, stack);
    }

    @Inject(method = "onCloseScreen", at = @At("TAIL"))
    private void scmarket$onCloseScreen(CloseScreenS2CPacket packet, CallbackInfo ci) {
        AuctionHouseDataCapture capture = scmarket$capture;
        if (capture == null) {
            return;
        }
        capture.onScreenClosed(packet.getSyncId());
    }
}
