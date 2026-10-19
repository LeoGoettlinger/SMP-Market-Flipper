package com.example.donutflipscanner.automation;

import com.example.donutflipscanner.automation.model.AuctionLocateResult;
import com.example.donutflipscanner.automation.model.AuctionVerificationResult;
import com.example.donutflipscanner.automation.model.InventoryVerificationResult;
import com.example.donutflipscanner.automation.model.ListingResult;
import com.example.donutflipscanner.automation.model.ListingVerificationResult;
import com.example.donutflipscanner.automation.model.PurchaseResult;
import com.example.donutflipscanner.automation.service.ActionPacingAuctionInteractionAdapter;
import com.example.donutflipscanner.automation.service.AuctionInteractionAdapter;
import com.example.donutflipscanner.automation.model.TradeExecutionRequest;
import com.example.donutflipscanner.automation.model.AuctionListingCandidate;
import com.example.donutflipscanner.automation.model.RelistPlan;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionPacingAuctionInteractionAdapterTest {
    @Test
    void appliesConfiguredPauseBeforeStagesButNeverDelaysEmergencyCleanup() throws Exception {
        AtomicBoolean located = new AtomicBoolean();
        AtomicBoolean cleanedUp = new AtomicBoolean();
        AuctionInteractionAdapter delegate = new AuctionInteractionAdapter() {
            @Override
            public CompletableFuture<AuctionLocateResult> locateListing(TradeExecutionRequest request) {
                located.set(true);
                return CompletableFuture.completedFuture(AuctionLocateResult.missing("test"));
            }

            @Override
            public CompletableFuture<AuctionVerificationResult> verifyListing(
                    TradeExecutionRequest request, AuctionListingCandidate candidate) {
                return CompletableFuture.completedFuture(AuctionVerificationResult.accepted());
            }

            @Override
            public CompletableFuture<PurchaseResult> purchase(
                    TradeExecutionRequest request, AuctionListingCandidate candidate) {
                return CompletableFuture.completedFuture(new PurchaseResult(false, false, "test"));
            }

            @Override
            public CompletableFuture<InventoryVerificationResult> verifyPurchase(TradeExecutionRequest request) {
                return CompletableFuture.completedFuture(new InventoryVerificationResult(false, false, "test"));
            }

            @Override
            public CompletableFuture<ListingResult> listForSale(
                    TradeExecutionRequest request, RelistPlan relistPlan) {
                return CompletableFuture.completedFuture(new ListingResult(false, "test"));
            }

            @Override
            public CompletableFuture<ListingVerificationResult> verifyListingCreated(
                    TradeExecutionRequest request, RelistPlan relistPlan) {
                return CompletableFuture.completedFuture(new ListingVerificationResult(false, "test"));
            }

            @Override
            public CompletableFuture<Void> returnToSafeScreen() {
                cleanedUp.set(true);
                return CompletableFuture.completedFuture(null);
            }
        };
        ActionPacingAuctionInteractionAdapter paced =
                new ActionPacingAuctionInteractionAdapter(delegate, () -> 150L);

        CompletableFuture<AuctionLocateResult> pending = paced.locateListing(null);
        assertFalse(located.get());
        assertTrue(paced.returnToSafeScreen().get(100, TimeUnit.MILLISECONDS) == null);
        assertTrue(cleanedUp.get());
        pending.get(2, TimeUnit.SECONDS);
        assertTrue(located.get());
    }
}
