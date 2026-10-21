package com.scmarket.data.provider;

import com.scmarket.data.FlipOpportunity;
import com.scmarket.data.OpportunityActionResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface OpportunityProvider {
    List<FlipOpportunity> getOpportunities();

    default CompletableFuture<OpportunityActionResult> reviewManually(String opportunityId) {
        return CompletableFuture.completedFuture(new OpportunityActionResult(
                false, "Live auction review is unavailable in the current data mode."
        ));
    }

    default CompletableFuture<Boolean> dismiss(String opportunityId) {
        return CompletableFuture.completedFuture(false);
    }

    default CompletableFuture<Boolean> markPurchasedManually(String opportunityId) {
        return CompletableFuture.completedFuture(false);
    }
}
