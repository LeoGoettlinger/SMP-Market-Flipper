package com.scmarket.market.value;

import com.scmarket.market.item.model.NormalizedItem;

import java.util.concurrent.CompletableFuture;

public interface FairValueProvider {
    CompletableFuture<FairValueEstimate> estimateFor(NormalizedItem item);
}
