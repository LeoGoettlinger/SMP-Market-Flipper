package com.scmarket.mixin;

import com.scmarket.packet.AuctionHouseDataCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.world.item.ItemStack;

/**
 * Intercepts auction house screen packets from the server.
 * When the AH screen opens, captured slot data is pushed into {@link AuctionHouseDataCapture}.
 */
@Mixin(ClientPacketListener.class)
public abstract class AuctionHousePacketListener extends ClientCommonPacketListenerImpl {
    @Unique
    private static volatile AuctionHouseDataCapture scmarket$capture;

    protected AuctionHousePacketListener(Minecraft client, Connection connection, CommonListenerCookie connectionState) {
        super(client, connection, connectionState);
    }

    public static void setCapture(AuctionHouseDataCapture capture) {
        scmarket$capture = capture;
    }

    @Inject(method = "handleOpenScreen", at = @At("TAIL"))
    private void scmarket$onOpenScreen(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        AuctionHouseDataCapture capture = scmarket$capture;
        if (capture == null) {
            return;
        }
        String title = packet.getTitle().getString();
        int syncId = packet.getContainerId();
        capture.onScreenOpened(syncId, title);
    }

    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void scmarket$onInventory(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        AuctionHouseDataCapture capture = scmarket$capture;
        if (capture == null) {
            return;
        }
        int syncId = packet.containerId();
        List<ItemStack> contents = packet.items();
        capture.onInventorySynced(syncId, contents);
    }

    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
    private void scmarket$onSlotUpdate(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        AuctionHouseDataCapture capture = scmarket$capture;
        if (capture == null) {
            return;
        }
        int syncId = packet.getContainerId();
        int slot = packet.getSlot();
        ItemStack stack = packet.getItem();
        capture.onSlotUpdated(syncId, slot, stack);
    }

    @Inject(method = "handleContainerClose", at = @At("TAIL"))
    private void scmarket$onCloseScreen(ClientboundContainerClosePacket packet, CallbackInfo ci) {
        AuctionHouseDataCapture capture = scmarket$capture;
        if (capture == null) {
            return;
        }
        capture.onScreenClosed(packet.getContainerId());
    }
}
