package com.electroshop.controller;

import com.electroshop.dto.ApiResponse;
import com.electroshop.dto.BusinessBannerDto;
import com.electroshop.dto.PredictiveSalesDto;
import com.electroshop.dto.ProfitBreakdownDto;
import com.electroshop.dto.ProfitPotentialDto;
import com.electroshop.dto.StockValueDto;
import com.electroshop.service.MetricRange;
import com.electroshop.service.MetricsService;
import com.electroshop.service.PredictiveSalesService;
import com.electroshop.service.ProfitAnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Business metrics: stock value, potential profit, the dashboard banner, the profit
 * breakdown and the sales forecast.
 *
 * <p>Tasks 9, 10, 11, 12 and the forecast half of task 2. Full paths are
 * {@code /api/metrics/…}.</p>
 *
 * <p><b>Every endpoint here requires {@code METRICS_VIEW}, not {@code DASHBOARD_VIEW}.</b>
 * The two are deliberately different decisions. Opening the dashboard is one thing;
 * reading what the company pays its suppliers, what margin each product carries and
 * which products are sold below cost is another. An Editor who may look at order counts
 * has no business with the purchase-price column, and before this permission existed
 * those were the same grant.</p>
 */
@RestController
@RequestMapping("/metrics")
public class MetricsController {

    private final MetricsService metricsService;
    private final ProfitAnalyticsService profitAnalyticsService;
    private final PredictiveSalesService predictiveSalesService;

    public MetricsController(MetricsService metricsService,
                             ProfitAnalyticsService profitAnalyticsService,
                             PredictiveSalesService predictiveSalesService) {
        this.metricsService = metricsService;
        this.profitAnalyticsService = profitAnalyticsService;
        this.predictiveSalesService = predictiveSalesService;
    }

    /**
     * TASK 10 — capital tied up in inventory, at cost.
     *
     * <p>{@code GET /api/metrics/stock-value}</p>
     */
    @GetMapping("/stock-value")
    @PreAuthorize("@permissionService.has('METRICS_VIEW')")
    public ResponseEntity<ApiResponse<StockValueDto>> stockValue() {
        return ResponseEntity.ok(ApiResponse.ok(metricsService.stockValue()));
    }

    /**
     * TASK 11 — profit the current inventory would yield at list price.
     *
     * <p>{@code GET /api/metrics/profit-potential}</p>
     */
    @GetMapping("/profit-potential")
    @PreAuthorize("@permissionService.has('METRICS_VIEW')")
    public ResponseEntity<ApiResponse<ProfitPotentialDto>> profitPotential() {
        return ResponseEntity.ok(ApiResponse.ok(metricsService.profitPotential()));
    }

    /**
     * TASK 9 — all four banner figures in one response.
     *
     * <p>{@code GET /api/metrics/banner}</p>
     *
     * <p>One call rather than four, because the banner renders as a unit. Four separate
     * requests would let the cards be computed against a catalogue that changed between
     * them, producing a stock value from one instant beside a margin from another — two
     * figures that cannot be reconciled and that an operator would reasonably assume
     * could be.</p>
     */
    @GetMapping("/banner")
    @PreAuthorize("@permissionService.has('METRICS_VIEW')")
    public ResponseEntity<ApiResponse<BusinessBannerDto>> banner() {
        return ResponseEntity.ok(ApiResponse.ok(metricsService.banner()));
    }

    /**
     * TASK 12 — where the profit comes from, by category, brand and product.
     *
     * <p>{@code GET /api/metrics/profit-breakdown?range=30d&category=…&brand=…}</p>
     *
     * <p>Both filters are optional and both are matched case-insensitively against the
     * values already in the catalogue. An unrecognised value produces an empty
     * breakdown rather than an error: the filter arrives in a query string, a stale
     * bookmark can carry a category that has since been renamed, and refusing to render
     * the card in that case helps nobody.</p>
     */
    @GetMapping("/profit-breakdown")
    @PreAuthorize("@permissionService.has('METRICS_VIEW')")
    public ResponseEntity<ApiResponse<ProfitBreakdownDto>> profitBreakdown(
            @RequestParam(name = "range", required = false) String range,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "brand", required = false) String brand) {
        MetricRange resolved = MetricRange.parse(range, MetricRange.D30);
        return ResponseEntity.ok(ApiResponse.ok(
                profitAnalyticsService.breakdown(resolved, category, brand)));
    }

    /**
     * TASK 2 — the sales forecast card.
     *
     * <p>{@code GET /api/metrics/predictive-sales?horizon=14}</p>
     *
     * <p>The horizon is clamped by the service rather than validated away here. A
     * forecast thirty days out from three weeks of history is arithmetic rather than
     * prediction, and the service is the place that knows how much history it has.</p>
     */
    @GetMapping("/predictive-sales")
    @PreAuthorize("@permissionService.has('METRICS_VIEW')")
    public ResponseEntity<ApiResponse<PredictiveSalesDto>> predictiveSales(
            @RequestParam(name = "horizon", defaultValue = "14") int horizon) {
        return ResponseEntity.ok(ApiResponse.ok(predictiveSalesService.forecast(horizon)));
    }
}
