package com.electroshop.controller;

import com.electroshop.dto.ApiResponse;
import com.electroshop.dto.FinancialOverviewDto;
import com.electroshop.service.FinancialOverviewService;
import com.electroshop.service.MetricRange;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The financial overview panel.
 *
 * <p>Task 14. Full path is {@code /api/financial/overview}.</p>
 */
@RestController
@RequestMapping("/financial")
public class FinancialController {

    private final FinancialOverviewService financialOverviewService;

    public FinancialController(FinancialOverviewService financialOverviewService) {
        this.financialOverviewService = financialOverviewService;
    }

    /**
     * Revenue, profit and cost of goods sold over three, six or twelve months.
     *
     * <p>{@code GET /api/financial/overview?range=12m}</p>
     *
     * <p>Defaults to twelve months. A financial panel opened without a preference is
     * asking about the year, not about the last thirty days — and the shorter windows
     * remain one click away, whereas a year's context is not reconstructible from a
     * month's chart.</p>
     */
    @GetMapping("/overview")
    @PreAuthorize("@permissionService.has('METRICS_VIEW')")
    public ResponseEntity<ApiResponse<FinancialOverviewDto>> overview(
            @RequestParam(name = "range", required = false) String range) {
        MetricRange resolved = MetricRange.parse(range, MetricRange.M12);
        return ResponseEntity.ok(ApiResponse.ok(financialOverviewService.overview(resolved)));
    }
}
