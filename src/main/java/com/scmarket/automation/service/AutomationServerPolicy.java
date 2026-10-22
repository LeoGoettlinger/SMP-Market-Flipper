package com.scmarket.automation.service;

import java.util.Locale;
import java.util.Objects;

/**
 * Allows operation on explicitly configured servers without hard-coding a network brand.
 * The coordinator requires an exact server allowlist entry; allowlisting is the server-access gate.
 */
public final class AutomationServerPolicy {
    private AutomationServerPolicy() {
    }

    public static boolean permitsGameplayAutomation(String serverAddress) {
        String host = host(serverAddress);
        return !host.isEmpty() && host.length() <= 255
                && host.matches("[a-z0-9._:-]+")
                && !host.startsWith(".") && !host.endsWith(".");
    }

    private static String host(String serverAddress) {
        String value = Objects.requireNonNullElse(serverAddress, "").strip()
                .toLowerCase(Locale.ROOT);
        if (value.endsWith(".")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
