package com.scmarket.gui;

public enum GuiTab {
    DASHBOARD("Dashboard", "Market and scanner overview"),
    FISHING("Fishing", "Fishing controls and safety status"),
    AH_DEALS("AH Deals", "Automatic auction-house deal workflow"),
    ORDER_TO_AH("Order→AH", "Order workflow status and safety checks"),
    OPPORTUNITIES("Market", "Review detected market opportunities"),
    ITEM_FILTERS("Filters", "Manage whitelist and blacklist"),
    HISTORY("History", "Reviewed and dismissed opportunities"),
    AUTOMATION("Automation", "Authorization-gated execution controls"),
    SETTINGS("Settings", "Configure scanner and interface");

    private final String displayName;
    private final String description;

    GuiTab(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }
}
