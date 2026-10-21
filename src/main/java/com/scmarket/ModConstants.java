package com.scmarket;

/** Central rename points for the mod identity and user-facing defaults. */
public final class ModConstants {
    public static final String MOD_NAME = "SC-Market";
    public static final String MOD_ID = "sc_market";
    public static final String MOD_VERSION = "0.1.0";
    public static final String PACKAGE_NAME = "com.scmarket";
    public static final String CONFIG_DIRECTORY_NAME = "sc-market";
    /** Historical path retained solely for one-way, non-destructive user-data migration. */
    public static final String LEGACY_CONFIG_DIRECTORY_NAME = "donut-flip-scanner";

    public static final String OPEN_GUI_KEY = "key." + MOD_ID + ".open_gui";
    public static final String KEYBIND_CATEGORY_PATH = "main";

    private ModConstants() {
    }
}
