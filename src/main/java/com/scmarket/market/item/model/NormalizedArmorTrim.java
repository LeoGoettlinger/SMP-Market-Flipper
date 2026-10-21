package com.scmarket.market.item.model;

import java.util.Objects;

public record NormalizedArmorTrim(String material, String pattern) {
    public NormalizedArmorTrim {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(pattern, "pattern");
    }
}
