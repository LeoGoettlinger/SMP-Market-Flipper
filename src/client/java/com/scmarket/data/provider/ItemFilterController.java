package com.scmarket.data.provider;

import com.scmarket.data.ItemFilterSnapshot;
import com.scmarket.market.opportunity.ItemFilterMode;

import java.util.concurrent.CompletableFuture;

public interface ItemFilterController {
    ItemFilterSnapshot getItemFilters();

    CompletableFuture<Void> setMode(ItemFilterMode mode);

    CompletableFuture<Void> setWhitelisted(String itemId, boolean whitelisted);

    CompletableFuture<Void> setBlacklisted(String itemId, boolean blacklisted);
}
