package com.scmarket.automation.service;

import com.scmarket.automation.model.AuctionListingCandidate;
import com.scmarket.automation.model.AuctionLocateResult;
import com.scmarket.automation.model.AuctionVerificationResult;
import com.scmarket.automation.model.InventoryVerificationResult;
import com.scmarket.automation.model.ListingResult;
import com.scmarket.automation.model.ListingVerificationResult;
import com.scmarket.automation.model.PurchaseResult;
import com.scmarket.automation.model.RelistPlan;
import com.scmarket.automation.model.TradeExecutionRequest;

import java.util.concurrent.CompletableFuture;

/** Fail-closed placeholder used until a version-verified authorized adapter is attached. */
public final class RejectingAuctionInteractionAdapter implements AuctionInteractionAdapter {
    private static final String MESSAGE = "No authorized Minecraft auction adapter is attached.";

    @Override
    public CompletableFuture<AuctionLocateResult> locateListing(TradeExecutionRequest request) {
        return CompletableFuture.completedFuture(AuctionLocateResult.missing(MESSAGE));
    }

    @Override
    public CompletableFuture<AuctionVerificationResult> verifyListing(
            TradeExecutionRequest request,
            AuctionListingCandidate candidate
    ) {
        return CompletableFuture.completedFuture(AuctionVerificationResult.rejected(MESSAGE));
    }

    @Override
    public CompletableFuture<PurchaseResult> purchase(
            TradeExecutionRequest request,
            AuctionListingCandidate candidate
    ) {
        return CompletableFuture.completedFuture(new PurchaseResult(false, false, MESSAGE));
    }

    @Override
    public CompletableFuture<InventoryVerificationResult> verifyPurchase(TradeExecutionRequest request) {
        return CompletableFuture.completedFuture(new InventoryVerificationResult(false, true, MESSAGE));
    }

    @Override
    public CompletableFuture<ListingResult> listForSale(TradeExecutionRequest request, RelistPlan relistPlan) {
        return CompletableFuture.completedFuture(new ListingResult(false, MESSAGE));
    }

    @Override
    public CompletableFuture<ListingVerificationResult> verifyListingCreated(
            TradeExecutionRequest request,
            RelistPlan relistPlan
    ) {
        return CompletableFuture.completedFuture(new ListingVerificationResult(false, MESSAGE));
    }

    @Override
    public CompletableFuture<Void> returnToSafeScreen() {
        return CompletableFuture.completedFuture(null);
    }
}
