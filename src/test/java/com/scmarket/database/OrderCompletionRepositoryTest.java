package com.scmarket.database;

import com.scmarket.order.OrderCompletionObservation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderCompletionRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsOneIdempotentOrderObservationOutsideAhSales() {
        try (DatabaseManager database = new DatabaseManager(temporaryDirectory.resolve("orders.db"))) {
            OrderCompletionRepository repository = new OrderCompletionRepository(database);
            OrderCompletionObservation observation = new OrderCompletionObservation(
                    "order-123", "minecraft:diamond", 1_024, new BigDecimal("11"),
                    new BigDecimal("11264"), DatabaseTestFixtures.BASE_TIME);

            assertTrue(repository.record(observation).join());
            assertFalse(repository.record(observation).join());
            assertEquals(java.util.List.of(observation),
                    repository.recentForItem("minecraft:diamond", 20).join());
        }
    }
}
