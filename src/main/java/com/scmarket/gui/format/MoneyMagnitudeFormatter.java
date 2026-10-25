package com.scmarket.gui.format;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Locale-independent compact money display that rounds magnitudes upward instead of understating value. */
public final class MoneyMagnitudeFormatter {
    private static final String[] SUFFIXES = {"", "K", "M", "B", "T", "Q"};

    private MoneyMagnitudeFormatter() {
    }

    public static String format(long amount) {
        return format(BigDecimal.valueOf(amount));
    }

    public static String format(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() == 0) {
            return "0";
        }
        BigDecimal absolute = amount.abs();
        if (absolute.compareTo(BigDecimal.valueOf(1_000L)) < 0) {
            return amount.stripTrailingZeros().toPlainString();
        }
        int magnitude = 0;
        BigDecimal divisor = BigDecimal.ONE;
        while (magnitude < SUFFIXES.length - 1
                && absolute.compareTo(divisor.multiply(BigDecimal.valueOf(1_000L))) >= 0) {
            divisor = divisor.multiply(BigDecimal.valueOf(1_000L));
            magnitude++;
        }
        BigDecimal scaled = absolute.divide(divisor, 1, RoundingMode.CEILING);
        if (scaled.compareTo(BigDecimal.valueOf(1_000L)) >= 0 && magnitude < SUFFIXES.length - 1) {
            divisor = divisor.multiply(BigDecimal.valueOf(1_000L));
            magnitude++;
            scaled = absolute.divide(divisor, 1, RoundingMode.CEILING);
        }
        String number = scaled.stripTrailingZeros().toPlainString();
        return (amount.signum() < 0 ? "-" : "") + number + SUFFIXES[magnitude];
    }
}
