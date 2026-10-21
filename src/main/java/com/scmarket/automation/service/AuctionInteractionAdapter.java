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

public interface AuctionInteractionAdapter {
    CompletableFuture<AuctionLocateResult> locateListing(TradeExecutionRequest request);

    CompletableFuture<AuctionVerificationResult> verifyListing(
            TradeExecutionRequest request,
            AuctionListingCandidate candidate
    );

    CompletableFuture<PurchaseResult> purchase(
            TradeExecutionRequest request,
            AuctionListingCandidate candidate
    );

    CompletableFuture<InventoryVerificationResult> verifyPurchase(TradeExecutionRequest request);

    CompletableFuture<ListingResult> listForSale(TradeExecutionRequest request, RelistPlan relistPlan);

    CompletableFuture<ListingVerificationResult> verifyListingCreated(
            TradeExecutionRequest request,
            RelistPlan relistPlan
    );

    CompletableFuture<Void> returnToSafeScreen();
}
