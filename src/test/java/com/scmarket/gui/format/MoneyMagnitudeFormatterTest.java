package com.scmarket.gui.format;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoneyMagnitudeFormatterTest {
    @Test
    void formatsTheFwmMagnitudeExamples() {
        assertEquals("1.4K", MoneyMagnitudeFormatter.format(new BigDecimal("1300")));
        assertEquals("12M", MoneyMagnitudeFormatter.format(new BigDecimal("12000000")));
        assertEquals("3.1B", MoneyMagnitudeFormatter.format(new BigDecimal("3100000000")));
    }

    @Test
    void roundsMagnitudesUpAndCarriesIntoTheNextSuffix() {
        assertEquals("1.1K", MoneyMagnitudeFormatter.format(new BigDecimal("1001")));
        assertEquals("1M", MoneyMagnitudeFormatter.format(new BigDecimal("999999")));
        assertEquals("1B", MoneyMagnitudeFormatter.format(new BigDecimal("999999999")));
    }

    @Test
    void preservesSmallAndNegativeAmounts() {
        assertEquals("999.99", MoneyMagnitudeFormatter.format(new BigDecimal("999.99")));
        assertEquals("-1.4K", MoneyMagnitudeFormatter.format(new BigDecimal("-1300")));
        assertEquals("0", MoneyMagnitudeFormatter.format(BigDecimal.ZERO));
    }

    @Test
    void isSafeForLongExtremes() {
        assertEquals("9223.4Q", MoneyMagnitudeFormatter.format(Long.MAX_VALUE));
        assertEquals("-9223.4Q", MoneyMagnitudeFormatter.format(Long.MIN_VALUE));
    }
}
