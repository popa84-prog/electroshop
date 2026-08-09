package com.electroshop.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The financial picture over three, six or twelve months.
 *
 * <p>Answers {@code GET /api/financial/overview}. Four series drive four charts:
 * monthly revenue as a line, monthly profit as an area, monthly cost of goods sold as
 * bars, and the twelve-month profit trajectory as a line.</p>
 *
 * <p><b>Cost of goods sold, not stock value.</b> The requirement names a "total stock
 * cost" chart. Plotting the current inventory value month by month would draw the same
 * number repeated, because stock value is a snapshot of today and has no history — the
 * database records what stock is, not what it was. What does have a month-by-month
 * history, and what a financial panel is actually asking for, is the cost of the goods
 * that were sold in each month. That is summed from {@code OrderItem.costPrice} and it
 * is the figure that pairs with revenue to give profit.</p>
 *
 * <p>Every series covers exactly the same months in the same order, including months
 * with no activity, which are present with zeroes. A chart that omits empty months
 * compresses a quiet summer into a short gap and makes a decline look like a plateau.</p>
 *
 * @param revenue        monthly revenue, oldest first
 * @param profit         monthly profit, oldest first
 * @param cogs           monthly cost of goods sold, oldest first
 * @param profitTrend    the twelve-month profit line, independent of the selected
 *                       range so the long view is always available
 * @param totalRevenue   revenue across the whole selected window
 * @param totalProfit    profit across the whole selected window
 * @param totalCogs      cost of goods sold across the whole selected window
 * @param marginPct      window margin as a percentage of revenue
 * @param revenueDelta   window revenue against the preceding window of equal length
 * @param profitDelta    window profit against the preceding window
 * @param bestMonth      the strongest month by profit inside the window
 * @param worstMonth     the weakest month by profit inside the window
 * @param currency       ISO code the amounts are expressed in
 * @param range          the resolved window
 * @param ordersCounted  how many orders the figures are built from
 * @param itemsWithoutCost order lines with no recorded cost price, excluded from profit
 *                       and disclosed so the margin can be read with the right caution
 */
public record FinancialOverviewDto(
        List<SeriesPointDto> revenue,
        List<SeriesPointDto> profit,
        List<SeriesPointDto> cogs,
        List<SeriesPointDto> profitTrend,
        BigDecimal totalRevenue,
        BigDecimal totalProfit,
        BigDecimal totalCogs,
        Double marginPct,
        DeltaDto revenueDelta,
        DeltaDto profitDelta,
        MonthSummary bestMonth,
        MonthSummary worstMonth,
        String currency,
        RangeInfoDto range,
        long ordersCounted,
        long itemsWithoutCost
) {

    /**
     * A single month singled out for being the best or the worst.
     *
     * @param label     {@code yyyy-MM}
     * @param revenue   revenue that month
     * @param profit    profit that month
     * @param marginPct margin that month
     * @param orders    orders that month
     */
    public record MonthSummary(
            String label,
            BigDecimal revenue,
            BigDecimal profit,
            Double marginPct,
            long orders
    ) {}
}
