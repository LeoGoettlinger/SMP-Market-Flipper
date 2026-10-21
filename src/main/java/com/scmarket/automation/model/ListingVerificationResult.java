package com.scmarket.automation.model;

import java.util.Objects;

public record ListingVerificationResult(boolean verified, String message) {
    public ListingVerificationResult {
        message = Objects.requireNonNullElse(message, "");
    }
}
