package com.electroshop.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MetricRange} decides what every analytics panel means by a time window,
 * so a mistake here is a mistake in eight reports at once.
 */
class MetricRangeTest {

    /** A fixed moment, so nothing in these tests depends on when they run. */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 9, 16, 30);

    @Test
    void anUnknownCodeFallsBackInsteadOfFailing() {
        // The range arrives in a query string, so a stale bookmark or a typed URL
        // can carry anything. Refusing to render a dashboard because a parameter
        // is misspelled serves nobody, and the resolved code is echoed in the
        // response so the interface can show which window it actually got.
        assertEquals(MetricRange.D30, MetricRange.parse("nonsense"));
        assertEquals(MetricRange.D30, MetricRange.parse(null));
        assertEquals(MetricRange.D30, MetricRange.parse("   "));
    }

    @Test
    void eachPanelChoosesItsOwnDefault() {
        // The financial panel opens on twelve months; the sales chart on thirty
        // days. One global default would be wrong for one of them.
        assertEquals(MetricRange.M12, MetricRange.parse(null, MetricRange.M12));
        assertEquals(MetricRange.D7, MetricRange.parse("", MetricRange.D7));
        // An explicit value always wins over the fallback.
        assertEquals(MetricRange.H24, MetricRange.parse("24h", MetricRange.M12));
    }

    @Test
    void theWindowEndsNowRatherThanAtMidnight() {
        // A "last 7 days" figure that stops at midnight silently discards
        // everything sold today — which is the number an operator opening the
        // dashboard in the afternoon is most interested in.
        assertEquals(NOW, MetricRange.D7.to(NOW));
        assertEquals(NOW.minusDays(7), MetricRange.D7.from(NOW));
    }

    @Test
    void theComparisonWindowIsExactlyOneWindowEarlier() {
        // Comparing a part-day against a whole day is what makes a dashboard
        // report a collapse every morning. The previous window must be the same
        // length and must end where the current one begins.
        LocalDateTime from = MetricRange.D30.from(NOW);
        LocalDateTime previousFrom = MetricRange.D30.previousFrom(NOW);
        LocalDateTime previousTo = MetricRange.D30.previousTo(NOW);

        assertEquals(from, previousTo);
        assertEquals(NOW.minusDays(60), previousFrom);
        assertEquals(
                java.time.Duration.between(from, NOW),
                java.time.Duration.between(previousFrom, previousTo));
    }

    @Test
    void bucketLabelsCoverTheWholeWindowWithNoGaps() {
        List<String> days = MetricRange.D7.bucketLabels(NOW);

        // Eight labels for seven days: the window opens part-way through a day
        // and closes part-way through another, so both partial days are present.
        // A chart that omitted either would lose real sales at its edges.
        assertEquals(8, days.size());
        assertEquals("2026-08-02", days.get(0));
        assertEquals("2026-08-09", days.get(days.size() - 1));

        for (int i = 1; i < days.size(); i++) {
            assertTrue(days.get(i).compareTo(days.get(i - 1)) > 0,
                    "etichetele trebuie să fie strict crescătoare");
        }
    }

    @Test
    void monthlyRangesLabelByMonthAndDailyRangesByDay() {
        assertEquals(MetricRange.Bucket.MONTH, MetricRange.M12.bucket());
        assertEquals(MetricRange.Bucket.DAY, MetricRange.D30.bucket());
        assertEquals(MetricRange.Bucket.HOUR, MetricRange.H24.bucket());

        assertEquals("2026-08", MetricRange.M12.labelFor(NOW));
        assertEquals("2026-08-09", MetricRange.D30.labelFor(NOW));
        assertEquals("2026-08-09 16:00", MetricRange.H24.labelFor(NOW));
    }

    @Test
    void aTimestampAlwaysLandsInABucketTheAxisContains() {
        // This is the property that matters: if a row could be bucketed into a
        // label the axis does not have, its value silently disappears from the
        // chart. The label producer and the axis producer must be the same code.
        for (MetricRange range : MetricRange.values()) {
            List<String> labels = range.bucketLabels(NOW);
            LocalDateTime inside = range.from(NOW).plusMinutes(1);

            assertTrue(labels.contains(range.labelFor(inside)),
                    range.code() + ": eticheta unui moment din fereastră trebuie să existe pe axă");
            assertTrue(labels.contains(range.labelFor(NOW)),
                    range.code() + ": eticheta momentului curent trebuie să existe pe axă");
        }
    }

    @Test
    void theResolvedWindowTravelsWithEveryResponse() {
        var info = MetricRange.M3.info(NOW);

        assertEquals("3m", info.code());
        assertEquals(NOW, info.to());
        assertEquals(NOW.minusMonths(3), info.from());
        assertEquals("MONTH", info.bucket());
        // Null when the underlying data has always existed; the panels built on
        // newly collected data pass a real date instead.
        assertEquals(null, info.dataAvailableFrom());

        var collecting = MetricRange.M3.info(NOW, NOW.minusDays(21));
        assertEquals(NOW.minusDays(21), collecting.dataAvailableFrom());
    }

    @Test
    void windowLengthIsReportedInDaysForVelocityCalculations() {
        // Long literals: the method returns a long, and an int literal would
        // box to Integer and never equal a Long carrying the same number.
        assertEquals(7L, MetricRange.D7.lengthInDays(NOW));
        assertEquals(30L, MetricRange.D30.lengthInDays(NOW));
        assertEquals(1L, MetricRange.H24.lengthInDays(NOW));
        // Months vary in length, so this is a real elapsed count rather than 90.
        assertFalse(MetricRange.M3.lengthInDays(NOW) == 0);
    }
}
