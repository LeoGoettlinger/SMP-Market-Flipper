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

/** Deterministic simulation. This adapter never accesses Minecraft, networking, or the filesystem. */
public final class DryRunAuctionInteractionAdapter implements AuctionInteractionAdapter {
    @Override
    public CompletableFuture<AuctionLocateResult> locateListing(TradeExecutionRequest request) {
        return CompletableFuture.completedFuture(AuctionLocateResult.found(request.expectedCandidate()));
    }

    @Override
    public CompletableFuture<AuctionVerificationResult> verifyListing(
            TradeExecutionRequest request,
            AuctionListingCandidate candidate
    ) {
        return CompletableFuture.completedFuture(AuctionVerificationResult.accepted());
    }

    @Override
    public CompletableFuture<PurchaseResult> purchase(
            TradeExecutionRequest request,
            AuctionListingCandidate candidate
    ) {
        return CompletableFuture.completedFuture(new PurchaseResult(
                true, true, "Dry run simulated a purchase; no Minecraft input occurred."
        ));
    }

    @Override
    public CompletableFuture<InventoryVerificationResult> verifyPurchase(TradeExecutionRequest request) {
        return CompletableFuture.completedFuture(new InventoryVerificationResult(
                true, false, "Dry run simulated an exact inventory delta."
        ));
    }

    @Override
    public CompletableFuture<ListingResult> listForSale(
            TradeExecutionRequest request,
            RelistPlan relistPlan
    ) {
        return CompletableFuture.completedFuture(new ListingResult(
                true, "Dry run simulated creating the resale listing."
        ));
    }

    @Override
    public CompletableFuture<ListingVerificationResult> verifyListingCreated(
            TradeExecutionRequest request,
            RelistPlan relistPlan
    ) {
        return CompletableFuture.completedFuture(new ListingVerificationResult(
                true, "Dry run simulated listing verification."
        ));
    }

    @Override
    public CompletableFuture<Void> returnToSafeScreen() {
        return CompletableFuture.completedFuture(null);
    }
}
