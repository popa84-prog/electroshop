package com.electroshop.controller;

import com.electroshop.dto.ApiResponse;
import com.electroshop.dto.ProductPerformanceDto;
import com.electroshop.dto.TopProductsInsightDto;
import com.electroshop.service.MetricRange;
import com.electroshop.service.ProductPerformanceService;
import com.electroshop.service.TopProductsInsightService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Product performance and the top-products card.
 *
 * <p>Tasks 18 and 6. Full paths are {@code /api/products/performance} and
 * {@code /api/products/top-insights}.</p>
 *
 * <p>Both share the {@code /products} prefix with {@link ProductController}, which
 * declares {@code @GetMapping("/{id}")}. Spring picks the most specific match across
 * every controller and a literal segment beats a template, so neither path is ever
 * parsed as a product id.</p>
 */
@RestController
@RequestMapping("/products")
public class ProductAnalyticsController {

    private final ProductPerformanceService productPerformanceService;
    private final TopProductsInsightService topProductsInsightService;

    public ProductAnalyticsController(ProductPerformanceService productPerformanceService,
                                      TopProductsInsightService topProductsInsightService) {
        this.productPerformanceService = productPerformanceService;
        this.topProductsInsightService = topProductsInsightService;
    }

    /**
     * TASK 18 — rising, declining and stagnant products, with recommendations.
     *
     * <p>{@code GET /api/products/performance?range=30d}</p>
     */
    @GetMapping("/performance")
    @PreAuthorize("@permissionService.has('METRICS_VIEW')")
    public ResponseEntity<ApiResponse<ProductPerformanceDto>> performance(
            @RequestParam(name = "range", required = false) String range) {
        MetricRange resolved = MetricRange.parse(range, MetricRange.D30);
        return ResponseEntity.ok(ApiResponse.ok(productPerformanceService.performance(resolved)));
    }

    /**
     * TASK 6 — the three top-product rankings and the promotion candidates.
     *
     * <p>{@code GET /api/products/top-insights?range=30d&category=…&brand=…}</p>
     *
     * <p>Three rankings come back in one response — by revenue, by units and by profit —
     * because they are genuinely different lists and the card switches between them
     * locally. Fetching each on demand would make switching a network round trip and,
     * worse, would let the three be computed against different moments.</p>
     */
    @GetMapping("/top-insights")
    @PreAuthorize("@permissionService.has('METRICS_VIEW')")
    public ResponseEntity<ApiResponse<TopProductsInsightDto>> topInsights(
            @RequestParam(name = "range", required = false) String range,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "brand", required = false) String brand) {
        MetricRange resolved = MetricRange.parse(range, MetricRange.D30);
        return ResponseEntity.ok(ApiResponse.ok(
                topProductsInsightService.insights(resolved, category, brand)));
    }
}
