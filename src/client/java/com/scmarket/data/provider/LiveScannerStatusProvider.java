package com.scmarket.data.provider;

import com.scmarket.data.ScannerStatus;
import com.scmarket.market.scanner.MarketScanner;
import com.scmarket.market.scanner.MarketScannerConfig;
import com.scmarket.market.scanner.MarketScannerSnapshot;

import java.util.Objects;
import java.util.function.Consumer;

public final class LiveScannerStatusProvider implements ScannerStatusController {
    private final MarketScanner scanner;
    private final Consumer<Boolean> enabledChangeListener;

    public LiveScannerStatusProvider(MarketScanner scanner) {
        this(scanner, ignored -> { });
    }

    public LiveScannerStatusProvider(MarketScanner scanner, Consumer<Boolean> enabledChangeListener) {
        this.scanner = Objects.requireNonNull(scanner, "scanner");
        this.enabledChangeListener = Objects.requireNonNull(enabledChangeListener, "enabledChangeListener");
    }

    @Override
    public ScannerStatus getScannerStatus() {
        MarketScannerSnapshot snapshot = scanner.snapshot();
        return new ScannerStatus(
                scanner.configuration().scannerEnabled(), snapshot.state().name(),
                snapshot.pauseReason().name(), snapshot.lastSanitizedError()
        );
    }

    @Override
    public void setScannerEnabled(boolean enabled) {
        MarketScannerConfig updated = scanner.configuration().withScannerEnabled(enabled);
        scanner.updateConfiguration(updated);
        enabledChangeListener.accept(enabled);
    }
}
