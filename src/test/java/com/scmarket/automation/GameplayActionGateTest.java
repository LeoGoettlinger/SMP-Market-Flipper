package com.scmarket.automation;

import com.scmarket.automation.service.GameplayActionGate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameplayActionGateTest {
    @Test
    void serializesModulesAndAllowsSafeReacquisitionAfterLeaseClose() {
        GameplayActionGate gate = new GameplayActionGate();
        GameplayActionGate.Lease fishing = gate.tryAcquire(GameplayActionGate.Owner.FISHING).orElseThrow();

        assertFalse(gate.tryAcquire(GameplayActionGate.Owner.AH_DEALS).isPresent());
        assertTrue(gate.availableTo(GameplayActionGate.Owner.FISHING));
        assertEquals(GameplayActionGate.Owner.FISHING, gate.currentOwner().orElseThrow());

        fishing.close();
        GameplayActionGate.Lease ah = gate.tryAcquire(GameplayActionGate.Owner.AH_DEALS).orElseThrow();
        ah.close();
        assertTrue(gate.currentOwner().isEmpty());
    }
}
