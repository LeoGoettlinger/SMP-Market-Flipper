package com.scmarket.data.mock;

import com.scmarket.data.ApiConnectionStatus;
import com.scmarket.data.provider.ApiConnectionStatusProvider;

public final class MockApiConnectionStatusProvider implements ApiConnectionStatusProvider {
    private static final ApiConnectionStatus STATUS =
            new ApiConnectionStatus("Mock Data", "2s ago", true);

    @Override
    public ApiConnectionStatus getApiConnectionStatus() {
        return STATUS;
    }
}

