package com.scmarket.automation.model;

public enum AutomationMode {
    DISABLED,
    DRY_RUN,
    CONFIRM_EACH,
    /** Automatic execution of every otherwise-eligible opportunity, subject to all safety gates. */
    AUTOMATIC_AUTHORIZED_SERVER,
    /** Automatic execution with an additional current whitelist/blacklist check at dispatch time. */
    SMART_AUTHORIZED_SERVER;

    public boolean isAutomatic() {
        return this == AUTOMATIC_AUTHORIZED_SERVER || this == SMART_AUTHORIZED_SERVER;
    }

    public boolean isSmart() {
        return this == SMART_AUTHORIZED_SERVER;
    }
}
