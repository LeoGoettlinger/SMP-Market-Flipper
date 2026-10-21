package com.scmarket.api.model;

import java.util.Objects;

public record ApiEnchantment(String id, int level) {
    public ApiEnchantment {
        Objects.requireNonNull(id, "id");
    }
}
