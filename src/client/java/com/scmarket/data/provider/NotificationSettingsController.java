package com.scmarket.data.provider;

import com.scmarket.data.NotificationSettings;

public interface NotificationSettingsController {
    NotificationSettings getNotificationSettings();

    void setNotificationsEnabled(boolean enabled);

    default void toggleNotifications() {
        setNotificationsEnabled(!getNotificationSettings().enabled());
    }
}
