package com.scmarket.data.provider;

import com.scmarket.data.MarketChartPoint;
import com.scmarket.data.MarketChartSnapshot;
import com.scmarket.profit.PersonalProfitPoint;
import com.scmarket.profit.PersonalProfitSnapshot;
import com.scmarket.profit.PersonalProfitTracker;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Maps the immutable persistent ledger snapshot to the dashboard's cumulative-profit chart. */
public final class LivePersonalProfitChartProvider implements MarketChartProvider {
    private static final DateTimeFormatter LABEL_FORMAT =
            DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault());

    private final PersonalProfitTracker tracker;

    public LivePersonalProfitChartProvider(PersonalProfitTracker tracker) {
        this.tracker = Objects.requireNonNull(tracker, "tracker");
    }

    @Override
    public MarketChartSnapshot getMarketChart() {
        PersonalProfitSnapshot value = tracker.snapshot();
        List<MarketChartPoint> points = points(value.points());
        String range = value.realizedTrades() + (value.realizedTrades() == 1 ? " SALE" : " SALES")
                + " · " + value.openPositions() + " OPEN";
        return new MarketChartSnapshot(
                "TRACKED REALIZED PROFIT (GROSS)", range,
                ClientDataFormat.saturatedLong(value.realizedProfit()),
                value.returnPercent(), points
        );
    }

    private static List<MarketChartPoint> points(List<PersonalProfitPoint> source) {
        if (source.isEmpty()) {
            return List.of();
        }
        List<MarketChartPoint> mapped = new ArrayList<>(source.size() + 1);
        PersonalProfitPoint first = source.getFirst();
        mapped.add(new MarketChartPoint(LABEL_FORMAT.format(first.soldAt()), 0L));
        for (PersonalProfitPoint point : source) {
            mapped.add(new MarketChartPoint(
                    LABEL_FORMAT.format(point.soldAt()),
                    ClientDataFormat.saturatedLong(point.cumulativeProfit())
            ));
        }
        return List.copyOf(mapped);
    }
}
