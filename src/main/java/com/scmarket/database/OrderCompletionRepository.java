package com.scmarket.database;

import com.scmarket.order.OrderCompletionObservation;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Stores one completed-order observation per order ID, isolated from AH completed-sale history. */
public final class OrderCompletionRepository {
    private final DatabaseManager database;

    public OrderCompletionRepository(DatabaseManager database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    /** @return true only for the first persisted observation of this completed order. */
    public CompletableFuture<Boolean> record(OrderCompletionObservation observation) {
        Objects.requireNonNull(observation, "observation");
        return database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT OR IGNORE INTO order_completion_observations(
                        order_id, item_id, quantity, unit_price, total_value, completed_at
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setString(1, observation.orderId());
                statement.setString(2, observation.itemId());
                statement.setInt(3, observation.quantity());
                statement.setString(4, DatabaseValues.decimal(observation.unitPrice()));
                statement.setString(5, DatabaseValues.decimal(observation.totalValue()));
                statement.setLong(6, observation.completedAt().toEpochMilli());
                return statement.executeUpdate() == 1;
            }
        });
    }

    public CompletableFuture<List<OrderCompletionObservation>> recentForItem(String itemId, int limit) {
        Objects.requireNonNull(itemId, "itemId");
        int safeLimit = Math.max(1, Math.min(1_000, limit));
        return database.execute(connection -> {
            List<OrderCompletionObservation> results = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT order_id, item_id, quantity, unit_price, total_value, completed_at
                    FROM order_completion_observations
                    WHERE item_id = ?
                    ORDER BY completed_at DESC, order_id
                    LIMIT ?
                    """)) {
                statement.setString(1, itemId);
                statement.setInt(2, safeLimit);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        results.add(new OrderCompletionObservation(
                                result.getString("order_id"), result.getString("item_id"),
                                result.getInt("quantity"),
                                new BigDecimal(result.getString("unit_price")),
                                new BigDecimal(result.getString("total_value")),
                                Instant.ofEpochMilli(result.getLong("completed_at"))
                        ));
                    }
                }
            }
            return List.copyOf(results);
        });
    }
}
