package com.electroshop.controller;

import com.electroshop.dto.ApiResponse;
import com.electroshop.dto.CustomerInsightsDto;
import com.electroshop.service.CustomerInsightsService;
import com.electroshop.service.MetricRange;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer analysis.
 *
 * <p>Task 16. Full path is {@code /api/customers/insights}.</p>
 */
@RestController
@RequestMapping("/customers")
public class CustomerAnalyticsController {

    private final CustomerInsightsService customerInsightsService;

    public CustomerAnalyticsController(CustomerInsightsService customerInsightsService) {
        this.customerInsightsService = customerInsightsService;
    }

    /**
     * New against returning, purchase frequency, average basket and segments.
     *
     * <p>{@code GET /api/customers/insights?range=30d&type=RETURNING}</p>
     *
     * <p>{@code type} accepts {@code ALL}, {@code NEW}, {@code RETURNING}, or one of
     * the four segment keys. It narrows the population the figures are computed over,
     * and is applied after novelty has been decided — filtering to returning customers
     * and then asking how many are new would answer zero every time, which is a
     * plausible-looking way for a filter to break a panel.</p>
     *
     * <p>Reading this endpoint requires {@code METRICS_VIEW} because it exposes
     * per-customer spending, order history and contact addresses. That is a stricter
     * bar than viewing the dashboard, and deliberately so.</p>
     */
    @GetMapping("/insights")
    @PreAuthorize("@permissionService.has('METRICS_VIEW')")
    public ResponseEntity<ApiResponse<CustomerInsightsDto>> insights(
            @RequestParam(name = "range", required = false) String range,
            @RequestParam(name = "type", required = false) String type) {
        MetricRange resolved = MetricRange.parse(range, MetricRange.D30);
        return ResponseEntity.ok(ApiResponse.ok(customerInsightsService.insights(resolved, type)));
    }
}
