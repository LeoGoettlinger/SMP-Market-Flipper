package com.scmarket.data.mock;

import com.scmarket.data.NotificationSettings;
import com.scmarket.data.provider.NotificationSettingsController;

import java.util.concurrent.atomic.AtomicBoolean;

public final class MockNotificationSettingsController implements NotificationSettingsController {
    private final AtomicBoolean enabled = new AtomicBoolean(true);

    @Override
    public NotificationSettings getNotificationSettings() {
        return new NotificationSettings(enabled.get(), true);
    }

    @Override
    public void setNotificationsEnabled(boolean enabled) {
        this.enabled.set(enabled);
    }
}
