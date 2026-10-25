package com.scmarket.order;

import java.util.Objects;

/** Fail-closed gate for automatic collection; delivered and currently claimable quantities stay distinct. */
public final class OrderClaimSafetyPolicy {
    public Assessment assessForAutomaticCollection(OrderClaimSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.deliveredQuantity() != snapshot.totalQuantity()) {
            return new Assessment(false, BlockReason.ORDER_NOT_FULLY_DELIVERED,
                    "The server has not confirmed delivery of the complete order.");
        }
        if (snapshot.claimableQuantity() != snapshot.totalQuantity()) {
            return new Assessment(false, BlockReason.CLAIMABLE_QUANTITY_MISMATCH,
                    "The currently claimable amount differs from the complete order; no automatic partial collection is allowed.");
        }
        if (snapshot.paidAmount().compareTo(snapshot.expectedPayout()) != 0) {
            return new Assessment(false, BlockReason.PAYOUT_MISMATCH,
                    "The server payout does not exactly match the expected order payout.");
        }
        return new Assessment(true, BlockReason.NONE,
                "The complete order, complete claimable amount, and payout all match.");
    }

    public enum BlockReason {
        NONE,
        ORDER_NOT_FULLY_DELIVERED,
        CLAIMABLE_QUANTITY_MISMATCH,
        PAYOUT_MISMATCH
    }

    public record Assessment(boolean eligible, BlockReason reason, String message) {
        public Assessment {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(message, "message");
            if (eligible != (reason == BlockReason.NONE)) {
                throw new IllegalArgumentException("Eligible assessments must have no block reason");
            }
        }
    }
}
