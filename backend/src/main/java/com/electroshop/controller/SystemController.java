package com.electroshop.controller;

import com.electroshop.dto.ApiResponse;
import com.electroshop.dto.HealthStatusDto;
import com.electroshop.dto.SystemLogsDto;
import com.electroshop.service.HealthMetricsService;
import com.electroshop.service.MetricRange;
import com.electroshop.service.SystemLogExportService;
import com.electroshop.service.SystemLogService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Infrastructure monitoring: health, operational logs and their export.
 *
 * <p>Tasks 2, 8 and 19. Full paths are {@code /api/system/…}.</p>
 *
 * <p><b>Everything here requires {@code SYSTEM_MONITOR}, which only Admin holds.</b>
 * Operational logs carry endpoint paths, stack traces and driver messages — the exact
 * material an attacker uses to map a system — so the audience is the smallest one that
 * can still fix an outage. The health card is under the same permission for a simpler
 * reason: it lists the slowest endpoints, which is a map of where to apply load.</p>
 */
@RestController
@RequestMapping("/system")
public class SystemController {

    private final SystemLogService systemLogService;
    private final HealthMetricsService healthMetricsService;
    private final SystemLogExportService exportService;

    public SystemController(SystemLogService systemLogService,
                            HealthMetricsService healthMetricsService,
                            SystemLogExportService exportService) {
        this.systemLogService = systemLogService;
        this.healthMetricsService = healthMetricsService;
        this.exportService = exportService;
    }

    /**
     * TASK 2 — live performance of the running instance.
     *
     * <p>{@code GET /api/system/health-status}</p>
     */
    @GetMapping("/health-status")
    @PreAuthorize("@permissionService.has('SYSTEM_MONITOR')")
    public ResponseEntity<ApiResponse<HealthStatusDto>> healthStatus() {
        return ResponseEntity.ok(ApiResponse.ok(healthMetricsService.health()));
    }

    /**
     * TASK 19 — the operational log, filtered, searched and paged.
     *
     * <p>{@code GET /api/system/logs?range=7d&source=API&level=ERROR&q=timeout&page=0&size=50}</p>
     *
     * <p>Every filter is optional and an unrecognised value is treated as "no filter"
     * rather than as an error. This panel is opened because something is already wrong,
     * and refusing to render it over a misspelled query parameter helps nobody.</p>
     */
    @GetMapping("/logs")
    @PreAuthorize("@permissionService.has('SYSTEM_MONITOR')")
    public ResponseEntity<ApiResponse<SystemLogsDto>> logs(
            @RequestParam(name = "range", required = false) String range,
            @RequestParam(name = "source", required = false) String source,
            @RequestParam(name = "level", required = false) String level,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {

        MetricRange resolved = MetricRange.parse(range, MetricRange.D7);
        SystemLogsDto logs = systemLogService.logs(
                resolved, source, level, query, page, size, healthMetricsService.uptime());
        return ResponseEntity.ok(ApiResponse.ok(logs));
    }

    /**
     * TASK 19 — the same filtered rows as a CSV file.
     *
     * <p>{@code GET /api/system/logs/export?range=7d&source=API}</p>
     *
     * <p>Returns bytes rather than a streamed response because the export is capped at a
     * bounded number of rows, and a bounded body is simpler to get right than a stream
     * whose failure mode is a half-written file the browser saves anyway.</p>
     */
    @GetMapping("/logs/export")
    @PreAuthorize("@permissionService.has('SYSTEM_MONITOR')")
    public ResponseEntity<byte[]> exportLogs(
            @RequestParam(name = "range", required = false) String range,
            @RequestParam(name = "source", required = false) String source,
            @RequestParam(name = "level", required = false) String level,
            @RequestParam(name = "q", required = false) String query) {

        MetricRange resolved = MetricRange.parse(range, MetricRange.D7);
        byte[] csv = exportService.exportLogs(
                systemLogService.forExport(resolved, source, level, query));

        String filename = "jurnal-sistem-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
    }
}
