package com.scmarket.data.mock;

import com.scmarket.data.ScannerStatus;
import com.scmarket.data.provider.ScannerStatusController;

public final class MockScannerStatusProvider implements ScannerStatusController {
    private boolean enabled = true;

    @Override
    public ScannerStatus getScannerStatus() {
        return new ScannerStatus(enabled);
    }

    @Override
    public void setScannerEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
