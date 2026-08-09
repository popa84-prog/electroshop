package com.electroshop.controller;

import com.electroshop.dto.ApiResponse;
import com.electroshop.dto.OrderEfficiencyDto;
import com.electroshop.service.MetricRange;
import com.electroshop.service.OrderEfficiencyService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Order processing efficiency.
 *
 * <p>Task 15. Full path is {@code /api/orders/efficiency}.</p>
 *
 * <p><b>On sharing the {@code /orders} prefix with {@link OrderController}.</b> That
 * controller already maps {@code /orders} and declares {@code @GetMapping("/{id}")}.
 * Spring resolves a request against every handler in the application and picks the most
 * specific match, and a literal path segment always beats a template one, so
 * {@code /api/orders/efficiency} reaches this method rather than being parsed as an
 * order with the id "efficiency". Declaring it here instead of inside
 * {@code OrderController} keeps the analytics code out of a controller that is already
 * long, at no cost to routing.</p>
 */
@RestController
@RequestMapping("/orders")
public class OrderAnalyticsController {

    private final OrderEfficiencyService orderEfficiencyService;

    public OrderAnalyticsController(OrderEfficiencyService orderEfficiencyService) {
        this.orderEfficiencyService = orderEfficiencyService;
    }

    /**
     * Processing time, delivery time, return rate and cancellation rate.
     *
     * <p>{@code GET /api/orders/efficiency?range=30d}</p>
     */
    @GetMapping("/efficiency")
    @PreAuthorize("@permissionService.has('METRICS_VIEW')")
    public ResponseEntity<ApiResponse<OrderEfficiencyDto>> efficiency(
            @RequestParam(name = "range", required = false) String range) {
        MetricRange resolved = MetricRange.parse(range, MetricRange.D30);
        return ResponseEntity.ok(ApiResponse.ok(orderEfficiencyService.efficiency(resolved)));
    }
}
