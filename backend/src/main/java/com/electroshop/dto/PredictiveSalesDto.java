package com.electroshop.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * A sales forecast built from the store's own history.
 *
 * <p>Answers {@code GET /api/metrics/predictive-sales} and backs the dashboard's
 * "Predictive Sales" card.</p>
 *
 * <p><b>The method is stated because the number is only as good as the method.</b> The
 * forecast is a linear trend over daily revenue combined with a day-of-week seasonal
 * index — the two patterns that daily retail data actually exhibits and that can be
 * fitted honestly from a few months of orders. It is not a neural network and does not
 * claim to be one. {@code method} and {@code confidence} travel with the figures so the
 * card can present a projection as a projection.</p>
 *
 * <p><b>A forecast without an interval is a guess wearing a suit.</b> Every projected
 * point carries a lower and an upper bound derived from the residual spread of the fit,
 * and the card draws the band rather than a bare line. A band that is wider than the
 * value it surrounds is itself the message: there is not enough history to forecast, and
 * {@code confidence} will read {@code LOW}.</p>
 *
 * <p>Below {@code minHistoryDays} of order history no forecast is produced at all.
 * {@code sufficient} is false, the series are empty, and the card says what it is
 * waiting for. Fitting a trend to nine days of data produces a line that is arithmetic
 * rather than prediction.</p>
 *
 * @param history          actual daily revenue used to fit the model, oldest first
 * @param forecast         projected daily revenue, with bounds, oldest first
 * @param forecastTotal    summed projection across the horizon
 * @param forecastLower    summed lower bound
 * @param forecastUpper    summed upper bound
 * @param comparisonTotal  actual revenue over the equivalent immediately past period,
 *                         which is what makes the projection mean something
 * @param expectedChangePct projected total against that comparison
 * @param horizonDays      how many days ahead the projection runs
 * @param historyDays      how many days of history the fit used
 * @param minHistoryDays   the floor below which no forecast is produced
 * @param sufficient       whether there was enough history to forecast at all
 * @param method           plain-Romanian description of how the numbers were produced
 * @param confidence       {@code HIGH}, {@code MEDIUM} or {@code LOW}
 * @param trendPerDay      fitted slope: currency per day, signed
 * @param weekdayIndex     the seasonal factor per weekday, Monday first, where 1.0 is
 *                         an average day
 * @param currency         ISO code the amounts are expressed in
 */
public record PredictiveSalesDto(
        List<SeriesPointDto> history,
        List<ForecastPoint> forecast,
        BigDecimal forecastTotal,
        BigDecimal forecastLower,
        BigDecimal forecastUpper,
        BigDecimal comparisonTotal,
        Double expectedChangePct,
        int horizonDays,
        int historyDays,
        int minHistoryDays,
        boolean sufficient,
        String method,
        String confidence,
        BigDecimal trendPerDay,
        List<WeekdayFactor> weekdayIndex,
        String currency
) {

    /**
     * One projected day.
     *
     * @param label    {@code yyyy-MM-dd}
     * @param value    the central projection
     * @param lower    lower bound of the interval
     * @param upper    upper bound of the interval
     */
    public record ForecastPoint(String label, BigDecimal value, BigDecimal lower, BigDecimal upper) {}

    /**
     * How much a given weekday deviates from an average day.
     *
     * @param weekday Romanian day name
     * @param factor  multiplier where 1.0 is average; 1.30 means that weekday runs 30%
     *                above the trend line
     * @param samples how many observations produced the factor, so a weekday seen twice
     *                is not read with the same confidence as one seen forty times
     */
    public record WeekdayFactor(String weekday, Double factor, long samples) {}
}
