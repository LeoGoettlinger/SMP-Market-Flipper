package com.example.donutflipscanner.automation;

import com.example.donutflipscanner.automation.service.AutomationServerPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutomationServerPolicyTest {
    @Test
    void acceptsDonutAndOtherExplicitlyConfiguredHostsWithoutBrandSpecificRules() {
        assertTrue(AutomationServerPolicy.permitsGameplayAutomation("donutsmp.net"));
        assertTrue(AutomationServerPolicy.permitsGameplayAutomation("play.donutsmp.net:25565"));
        assertTrue(AutomationServerPolicy.permitsGameplayAutomation("private.example:25565"));
        assertTrue(AutomationServerPolicy.permitsGameplayAutomation("localhost"));
    }

    @Test
    void rejectsBlankAndMalformedServerAddresses() {
        assertFalse(AutomationServerPolicy.permitsGameplayAutomation(""));
        assertFalse(AutomationServerPolicy.permitsGameplayAutomation("bad host"));
        assertFalse(AutomationServerPolicy.permitsGameplayAutomation("host/path"));
    }
}
