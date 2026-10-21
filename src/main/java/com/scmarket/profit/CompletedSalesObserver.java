package com.scmarket.profit;

import com.scmarket.database.entity.SaleEntity;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface CompletedSalesObserver {
    CompletableFuture<Integer> observe(List<SaleEntity> sales);

    static CompletedSalesObserver noOp() {
        return ignored -> CompletableFuture.completedFuture(0);
    }
}
