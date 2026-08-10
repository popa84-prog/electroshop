package com.electroshop.service;

import com.electroshop.dto.PredictiveSalesDto;
import com.electroshop.dto.SeriesPointDto;
import com.electroshop.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A sales forecast fitted from the store's own order history.
 *
 * <p>Task 2, the "Predictive Sales" card.</p>
 *
 * <h2>What this is, stated plainly</h2>
 *
 * <p>A least-squares linear trend over daily revenue, multiplied by a day-of-week
 * seasonal index. Those are the two patterns daily retail revenue actually exhibits and
 * that can be fitted honestly from a few months of orders: a level that drifts, and a
 * week that repeats. It is not a neural network, it does not claim to be, and
 * {@code method} says so in the response so the card can present a projection as a
 * projection.</p>
 *
 * <p>The alternative — an ARIMA or a gradient-boosted model — would need far more
 * history than this catalogue has, would need holdout validation nobody would run, and
 * would produce a number no operator could sanity-check. A trend and a weekday index can
 * be argued with, which is the property that matters in a business panel.</p>
 *
 * <h2>Why every projected point carries an interval</h2>
 *
 * <p>A forecast without an interval is a guess wearing a suit. The band comes from the
 * residual standard deviation of the fit: how far the actual days scattered around the
 * line that was fitted to them. When that scatter is large the band is wide, and a band
 * wider than the value it surrounds is itself the message — there is not enough signal
 * to forecast, and {@code confidence} reads {@code LOW}.</p>
 *
 * <h2>Why there is a floor on history</h2>
 *
 * <p>Below {@link #MIN_HISTORY_DAYS} days of orders no forecast is produced at all.
 * {@code sufficient} is false, the series come back empty, and the card says what it is
 * waiting for. A line fitted to nine days of data is arithmetic, not prediction, and
 * dressing it up as a forecast is how a dashboard teaches people to distrust it.</p>
 */
@Service
public class PredictiveSalesService {

    /** Fewer days than this and no forecast is produced. Two full weeks plus a margin. */
    static final int MIN_HISTORY_DAYS = 21;

    /** How much history the fit uses at most. Older data describes a different business. */
    private static final int MAX_HISTORY_DAYS = 180;

    /** Longest horizon accepted, whatever the caller asks for. */
    private static final int MAX_HORIZON_DAYS = 60;

    /** Shortest horizon accepted. */
    private static final int MIN_HORIZON_DAYS = 1;

    /** Below this many days of history the forecast is labelled low confidence. */
    private static final int MEDIUM_CONFIDENCE_DAYS = 45;

    /** At or above this many days the forecast is labelled high confidence. */
    private static final int HIGH_CONFIDENCE_DAYS = 90;

    /**
     * Half-width of the interval, in residual standard deviations.
     *
     * <p>Roughly an 80% band under a normal assumption. A 95% band on retail data this
     * noisy is so wide that it stops informing anything; 80% keeps the band readable
     * while still being honest about the spread.</p>
     */
    private static final double INTERVAL_SIGMAS = 1.28;

    private static final String[] WEEKDAY_RO = {
            "Luni", "Marți", "Miercuri", "Joi", "Vineri", "Sâmbătă", "Duminică"
    };

    private static final String METHOD_DESCRIPTION =
            "Tendință liniară pe venitul zilnic, ajustată cu un indice sezonier pe zi a "
                    + "săptămânii. Intervalul provine din abaterea standard a reziduurilor "
                    + "ajustării — cu cât zilele reale s-au împrăștiat mai mult în jurul "
                    + "liniei, cu atât banda este mai lată.";

    private final OrderRepository orderRepository;

    public PredictiveSalesService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * Projects daily revenue {@code horizonDays} into the future.
     *
     * @param horizonDays how many days ahead to project; clamped to a sane range
     */
    public PredictiveSalesDto forecast(int horizonDays) {
        int horizon = Math.max(MIN_HORIZON_DAYS, Math.min(MAX_HORIZON_DAYS, horizonDays));

        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();

        LocalDateTime earliest = orderRepository.earliestOrder();
        if (earliest == null) {
            return insufficient(horizon, 0);
        }

        // The fit uses at most MAX_HISTORY_DAYS. Data older than that describes a
        // business with a different catalogue and different prices, and letting it pull
        // the trend line is how a forecast ends up anchored to last year's shop.
        LocalDate firstDay = earliest.toLocalDate();
        LocalDate windowStart = today.minusDays(MAX_HISTORY_DAYS - 1L);
        LocalDate start = firstDay.isAfter(windowStart) ? firstDay : windowStart;

        // Today is excluded from the fit. A partial day always reads as a collapse and
        // would drag the trend line down every single afternoon.
        LocalDate lastFullDay = today.minusDays(1);
        if (!start.isBefore(lastFullDay)) {
            return insufficient(horizon, 0);
        }

        int historyDays = (int) (lastFullDay.toEpochDay() - start.toEpochDay() + 1);
        if (historyDays < MIN_HISTORY_DAYS) {
            return insufficient(horizon, historyDays);
        }

        double[] daily = dailyRevenue(start, lastFullDay);

        // 1. Fit the level. Ordinary least squares on (day index, revenue).
        Fit fit = leastSquares(daily);

        // 2. Fit the week. Each weekday's average ratio to its own fitted trend value,
        //    which separates "Saturdays are strong" from "the whole business grew".
        double[] weekdayFactor = new double[7];
        long[] weekdaySamples = new long[7];
        double[] weekdaySum = new double[7];
        for (int i = 0; i < daily.length; i++) {
            double trend = fit.at(i);
            if (trend <= 0) {
                continue;
            }
            int wd = start.plusDays(i).getDayOfWeek().getValue() - 1;
            weekdaySum[wd] += daily[i] / trend;
            weekdaySamples[wd]++;
        }
        for (int wd = 0; wd < 7; wd++) {
            // A weekday never observed keeps a neutral factor. Guessing one from its
            // neighbours would invent a pattern the data has not shown.
            weekdayFactor[wd] = weekdaySamples[wd] == 0 ? 1.0 : weekdaySum[wd] / weekdaySamples[wd];
        }

        // 3. Measure the scatter around the fitted-and-seasonalised values. This is the
        //    residual the interval is built from, so the band reflects how well the
        //    model actually explained the past rather than how confident it sounds.
        double sumSq = 0;
        for (int i = 0; i < daily.length; i++) {
            int wd = start.plusDays(i).getDayOfWeek().getValue() - 1;
            double predicted = Math.max(0, fit.at(i) * weekdayFactor[wd]);
            double residual = daily[i] - predicted;
            sumSq += residual * residual;
        }
        double sigma = daily.length > 2 ? Math.sqrt(sumSq / (daily.length - 2)) : 0;

        // 4. Project.
        List<PredictiveSalesDto.ForecastPoint> points = new ArrayList<>(horizon);
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal lowerTotal = BigDecimal.ZERO;
        BigDecimal upperTotal = BigDecimal.ZERO;

        for (int h = 1; h <= horizon; h++) {
            LocalDate day = lastFullDay.plusDays(h);
            int wd = day.getDayOfWeek().getValue() - 1;
            double value = Math.max(0, fit.at(daily.length - 1 + h) * weekdayFactor[wd]);
            double halfWidth = INTERVAL_SIGMAS * sigma;

            BigDecimal v = money(value);
            // Revenue cannot be negative, so the lower bound is floored at zero rather
            // than being allowed to imply the shop will pay customers.
            BigDecimal lo = money(Math.max(0, value - halfWidth));
            BigDecimal hi = money(value + halfWidth);

            points.add(new PredictiveSalesDto.ForecastPoint(day.toString(), v, lo, hi));
            total = total.add(v);
            lowerTotal = lowerTotal.add(lo);
            upperTotal = upperTotal.add(hi);
        }

        // 5. Compare against the equivalent immediately past stretch, which is what
        //    turns a projected number into a statement about direction.
        BigDecimal comparison = BigDecimal.ZERO;
        for (int i = Math.max(0, daily.length - horizon); i < daily.length; i++) {
            comparison = comparison.add(money(daily[i]));
        }
        Double expectedChange = comparison.compareTo(BigDecimal.ZERO) == 0
                ? null
                : total.subtract(comparison)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(comparison, 1, RoundingMode.HALF_UP)
                        .doubleValue();

        List<SeriesPointDto> history = new ArrayList<>(daily.length);
        for (int i = 0; i < daily.length; i++) {
            history.add(SeriesPointDto.of(start.plusDays(i).toString(), money(daily[i])));
        }

        List<PredictiveSalesDto.WeekdayFactor> index = new ArrayList<>(7);
        for (int wd = 0; wd < 7; wd++) {
            index.add(new PredictiveSalesDto.WeekdayFactor(
                    WEEKDAY_RO[wd],
                    round(weekdayFactor[wd], 3),
                    weekdaySamples[wd]));
        }

        return new PredictiveSalesDto(
                history,
                points,
                total,
                lowerTotal,
                upperTotal,
                comparison,
                expectedChange,
                horizon,
                historyDays,
                MIN_HISTORY_DAYS,
                true,
                METHOD_DESCRIPTION,
                confidenceFor(historyDays, sigma, fit, daily.length),
                money(fit.slope()),
                index,
                MetricsService.CURRENCY
        );
    }

    /**
     * The answer when there is not enough history to fit anything.
     *
     * <p>Empty series and {@code sufficient = false} rather than a flat line at zero.
     * The two look identical on a chart and mean opposite things: one is a forecast of
     * no sales, the other is the absence of a forecast.</p>
     */
    private PredictiveSalesDto insufficient(int horizon, int historyDays) {
        return new PredictiveSalesDto(
                List.of(), List.of(),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null,
                horizon,
                historyDays,
                MIN_HISTORY_DAYS,
                false,
                METHOD_DESCRIPTION,
                "LOW",
                BigDecimal.ZERO,
                List.of(),
                MetricsService.CURRENCY
        );
    }

    /** Daily revenue from {@code start} to {@code end} inclusive, gaps filled with zero. */
    private double[] dailyRevenue(LocalDate start, LocalDate end) {
        List<Object[]> rows = orderRepository.revenueByDayBetween(
                start.atStartOfDay(), end.plusDays(1).atStartOfDay());

        Map<LocalDate, Double> byDay = new HashMap<>();
        for (Object[] row : rows) {
            LocalDate day = LocalDate.of(
                    ((Number) row[0]).intValue(),
                    ((Number) row[1]).intValue(),
                    ((Number) row[2]).intValue());
            byDay.put(day, ProfitAnalyticsService.dec(row[3]).doubleValue());
        }

        int n = (int) (end.toEpochDay() - start.toEpochDay() + 1);
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            // A day with no orders is a real zero, not a missing observation. Skipping
            // it would compress the timeline and make a quiet week look like normal
            // trading at a lower level.
            out[i] = byDay.getOrDefault(start.plusDays(i), 0.0);
        }
        return out;
    }

    /** Ordinary least squares over (index, value). */
    private static Fit leastSquares(double[] y) {
        int n = y.length;
        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += y[i];
            sumXY += (double) i * y[i];
            sumXX += (double) i * i;
        }
        double denom = n * sumXX - sumX * sumX;
        double slope = denom == 0 ? 0 : (n * sumXY - sumX * sumY) / denom;
        double intercept = (sumY - slope * sumX) / n;
        return new Fit(intercept, slope);
    }

    /**
     * How much to trust the projection.
     *
     * <p>Driven by two things: how much history was available, and how large the
     * residual scatter is relative to the level being forecast. A long history with wild
     * scatter is not a confident forecast, and neither is a tight fit over three
     * weeks.</p>
     */
    private static String confidenceFor(int historyDays, double sigma, Fit fit, int n) {
        double level = Math.max(1, fit.at(n / 2.0));
        double noiseRatio = sigma / level;

        if (historyDays >= HIGH_CONFIDENCE_DAYS && noiseRatio < 0.35) {
            return "HIGH";
        }
        if (historyDays >= MEDIUM_CONFIDENCE_DAYS && noiseRatio < 0.75) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private static BigDecimal money(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static Double round(double v, int decimals) {
        return BigDecimal.valueOf(v).setScale(decimals, RoundingMode.HALF_UP).doubleValue();
    }

    /** A fitted line: {@code value = intercept + slope × index}. */
    private record Fit(double intercept, double slope) {

        double at(double index) {
            return intercept + slope * index;
        }
    }
}
