package com.scmarket.market.item.model;

import java.util.Objects;
import java.util.OptionalInt;

public record NormalizedContainedItem(OptionalInt count, NormalizedItem item) {
    public NormalizedContainedItem {
        count = count == null ? OptionalInt.empty() : count;
        Objects.requireNonNull(item, "item");
    }
}
