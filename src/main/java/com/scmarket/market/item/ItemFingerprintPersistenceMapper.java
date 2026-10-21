package com.scmarket.market.item;

import com.scmarket.database.entity.ItemFingerprintEntity;
import com.scmarket.market.item.model.NormalizedItem;

import java.time.Instant;
import java.util.Objects;

public final class ItemFingerprintPersistenceMapper {
    public ItemFingerprintEntity toEntity(NormalizedItem item, Instant createdAt) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(createdAt, "createdAt");
        return new ItemFingerprintEntity(
                item.fingerprint().sha256(),
                item.itemId(),
                item.matchQuality().matchType().name(),
                item.fingerprint().canonicalMetadata(),
                createdAt
        );
    }
}
