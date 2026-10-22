package com.scmarket.automation;

import com.scmarket.automation.model.AutomationMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutomationModeTest {
    @Test
    void autoAndSmartAreAutomaticModesWhileSmartIsDistinct() {
        assertTrue(AutomationMode.AUTOMATIC_AUTHORIZED_SERVER.isAutomatic());
        assertFalse(AutomationMode.AUTOMATIC_AUTHORIZED_SERVER.isSmart());
        assertTrue(AutomationMode.SMART_AUTHORIZED_SERVER.isAutomatic());
        assertTrue(AutomationMode.SMART_AUTHORIZED_SERVER.isSmart());
        assertFalse(AutomationMode.DRY_RUN.isAutomatic());
        assertFalse(AutomationMode.CONFIRM_EACH.isAutomatic());
    }
}
