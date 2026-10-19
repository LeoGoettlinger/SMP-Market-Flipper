package com.example.donutflipscanner.automation.service;

import com.example.donutflipscanner.automation.model.AuctionListingCandidate;
import com.example.donutflipscanner.automation.model.AuctionLocateResult;
import com.example.donutflipscanner.automation.model.AuctionVerificationResult;
import com.example.donutflipscanner.automation.model.InventoryVerificationResult;
import com.example.donutflipscanner.automation.model.ListingResult;
import com.example.donutflipscanner.automation.model.ListingVerificationResult;
import com.example.donutflipscanner.automation.model.PurchaseResult;
import com.example.donutflipscanner.automation.model.RelistPlan;
import com.example.donutflipscanner.automation.model.TradeExecutionRequest;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Applies an explicit, user-configured pause before each non-emergency interaction stage. */
public final class ActionPacingAuctionInteractionAdapter implements AuctionInteractionAdapter {
    public static final long MAXIMUM_DELAY_MILLIS = 10_000L;

    private final AuctionInteractionAdapter delegate;
    private final LongSupplier delayMillis;

    public ActionPacingAuctionInteractionAdapter(
            AuctionInteractionAdapter delegate,
            LongSupplier delayMillis
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.delayMillis = Objects.requireNonNull(delayMillis, "delayMillis");
    }

    @Override
    public CompletableFuture<AuctionLocateResult> locateListing(TradeExecutionRequest request) {
        return afterDelay(() -> delegate.locateListing(request));
    }

    @Override
    public CompletableFuture<AuctionVerificationResult> verifyListing(
            TradeExecutionRequest request, AuctionListingCandidate candidate
    ) {
        return afterDelay(() -> delegate.verifyListing(request, candidate));
    }

    @Override
    public CompletableFuture<PurchaseResult> purchase(
            TradeExecutionRequest request, AuctionListingCandidate candidate
    ) {
        return afterDelay(() -> delegate.purchase(request, candidate));
    }

    @Override
    public CompletableFuture<InventoryVerificationResult> verifyPurchase(TradeExecutionRequest request) {
        return afterDelay(() -> delegate.verifyPurchase(request));
    }

    @Override
    public CompletableFuture<ListingResult> listForSale(TradeExecutionRequest request, RelistPlan relistPlan) {
        return afterDelay(() -> delegate.listForSale(request, relistPlan));
    }

    @Override
    public CompletableFuture<ListingVerificationResult> verifyListingCreated(
            TradeExecutionRequest request, RelistPlan relistPlan
    ) {
        return afterDelay(() -> delegate.verifyListingCreated(request, relistPlan));
    }

    /** Safety cleanup must never wait behind a configured pacing delay. */
    @Override
    public CompletableFuture<Void> returnToSafeScreen() {
        return delegate.returnToSafeScreen();
    }

    private <T> CompletableFuture<T> afterDelay(Supplier<CompletableFuture<T>> action) {
        long delay = Math.max(0L, Math.min(MAXIMUM_DELAY_MILLIS, delayMillis.getAsLong()));
        if (delay == 0L) {
            return invoke(action);
        }
        return CompletableFuture.runAsync(
                        () -> { }, CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS))
                .thenCompose(ignored -> invoke(action));
    }

    private static <T> CompletableFuture<T> invoke(Supplier<CompletableFuture<T>> action) {
        try {
            return Objects.requireNonNull(action.get(), "interaction future");
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }
}
