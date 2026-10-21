package com.scmarket.market.item.model;

import java.util.Objects;

public record ItemEnchantmentDescriptor(String id, int level) {
    public ItemEnchantmentDescriptor {
        Objects.requireNonNull(id, "id");
    }
}
