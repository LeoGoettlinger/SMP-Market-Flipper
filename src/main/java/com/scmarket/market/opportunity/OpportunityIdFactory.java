package com.scmarket.market.opportunity;

import com.scmarket.util.HashingUtil;

import java.util.List;
import java.util.Objects;

public final class OpportunityIdFactory {
    public String create(String listingKey, String evaluationVersion, String itemFingerprint) {
        Objects.requireNonNull(listingKey, "listingKey");
        Objects.requireNonNull(evaluationVersion, "evaluationVersion");
        Objects.requireNonNull(itemFingerprint, "itemFingerprint");
        return "opportunity:" + HashingUtil.sha256Fields(List.of(
                evaluationVersion, listingKey, itemFingerprint
        ));
    }
}
