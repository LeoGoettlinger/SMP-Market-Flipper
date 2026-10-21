package com.scmarket.market.item;

import com.scmarket.database.DatabaseManager;
import com.scmarket.database.FingerprintRepository;
import com.scmarket.database.entity.ItemFingerprintEntity;
import com.scmarket.market.item.model.ItemDescriptor;
import com.scmarket.market.item.model.NormalizedItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemFingerprintPersistenceMapperTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void normalizedFingerprintRoundTripsThroughChunkTwoRepository() {
        NormalizedItem normalized = new ItemNormalizer().normalize(
                ItemDescriptor.simple("minecraft:netherite_ingot", 4)
        );
        ItemFingerprintEntity entity = new ItemFingerprintPersistenceMapper().toEntity(
                normalized,
                Instant.parse("2026-08-03T12:00:00Z")
        );

        try (DatabaseManager database = new DatabaseManager(temporaryDirectory.resolve("market-data.db"))) {
            database.ready().join();
            FingerprintRepository repository = new FingerprintRepository(database);
            assertTrue(repository.insertIfAbsent(entity).join());
            assertEquals(entity, repository.find(entity.fingerprint()).join().orElseThrow());
        }
    }
}
