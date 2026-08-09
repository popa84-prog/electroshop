package com.electroshop.controller;

import com.electroshop.dto.ActivityFeedDto;
import com.electroshop.dto.ApiResponse;
import com.electroshop.dto.DashboardLayoutDto;
import com.electroshop.dto.FavoritesDto;
import com.electroshop.dto.GlobalSearchDto;
import com.electroshop.security.PermissionService;
import com.electroshop.security.UserPrincipal;
import com.electroshop.service.ActivityFeedService;
import com.electroshop.service.AdminPreferenceService;
import com.electroshop.service.GlobalSearchService;
import com.electroshop.service.MetricRange;
import com.electroshop.service.SystemLogExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Dashboard layout, sidebar favourites, global search and the activity feed.
 *
 * <p>Tasks 3, 4 and 5. Full paths are {@code /api/admin/…}.</p>
 *
 * <p><b>Every endpoint here is scoped to the authenticated caller.</b> The administrator
 * id comes from the security context, never from a path or a body parameter. An endpoint
 * that accepted an id would be one where changing a number in a request edits somebody
 * else's dashboard, and no amount of interface discipline prevents that.</p>
 */
@RestController
@RequestMapping("/admin")
public class DashboardConfigController {

    private final AdminPreferenceService preferenceService;
    private final GlobalSearchService globalSearchService;
    private final ActivityFeedService activityFeedService;
    private final SystemLogExportService exportService;
    private final PermissionService permissionService;

    public DashboardConfigController(AdminPreferenceService preferenceService,
                                     GlobalSearchService globalSearchService,
                                     ActivityFeedService activityFeedService,
                                     SystemLogExportService exportService,
                                     PermissionService permissionService) {
        this.preferenceService = preferenceService;
        this.globalSearchService = globalSearchService;
        this.activityFeedService = activityFeedService;
        this.exportService = exportService;
        this.permissionService = permissionService;
    }

    // =====================================================================
    //  TASK 4 — dashboard layout
    // =====================================================================

    /** {@code GET /api/admin/dashboard/layout} */
    @GetMapping("/dashboard/layout")
    @PreAuthorize("@permissionService.has('DASHBOARD_VIEW')")
    public ResponseEntity<ApiResponse<DashboardLayoutDto>> layout(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(preferenceService.layout(principal.getId())));
    }

    /**
     * {@code PUT /api/admin/dashboard/layout}
     *
     * <p>Replaces the whole arrangement rather than patching it. The layout is a single
     * ordered list, and a partial update would need the client to describe a reordering
     * as a sequence of moves — more protocol to get wrong, for a payload measured in
     * hundreds of bytes.</p>
     */
    @PutMapping("/dashboard/layout")
    @PreAuthorize("@permissionService.has('DASHBOARD_VIEW')")
    public ResponseEntity<ApiResponse<DashboardLayoutDto>> saveLayout(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody LayoutRequest request) {
        DashboardLayoutDto saved = preferenceService.saveLayout(
                principal.getId(),
                request == null ? List.of() : request.panels(),
                request == null ? null : request.density());
        return ResponseEntity.ok(ApiResponse.ok("Layout salvat", saved));
    }

    /**
     * {@code DELETE /api/admin/dashboard/layout} — the "Reset layout" button.
     *
     * <p>Returns the default arrangement rather than an empty body, so the interface
     * re-renders from the server's answer instead of reconstructing the default from a
     * copy of the panel registry that would have to be kept in step.</p>
     */
    @DeleteMapping("/dashboard/layout")
    @PreAuthorize("@permissionService.has('DASHBOARD_VIEW')")
    public ResponseEntity<ApiResponse<DashboardLayoutDto>> resetLayout(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Layout resetat", preferenceService.resetLayout(principal.getId())));
    }

    // =====================================================================
    //  TASK 3 — favourites and global search
    // =====================================================================

    /** {@code GET /api/admin/favorites} */
    @GetMapping("/favorites")
    @PreAuthorize("@permissionService.has('DASHBOARD_VIEW')")
    public ResponseEntity<ApiResponse<FavoritesDto>> favorites(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(preferenceService.favorites(principal.getId())));
    }

    /** {@code PUT /api/admin/favorites} */
    @PutMapping("/favorites")
    @PreAuthorize("@permissionService.has('DASHBOARD_VIEW')")
    public ResponseEntity<ApiResponse<FavoritesDto>> saveFavorites(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody FavoritesRequest request) {
        FavoritesDto saved = preferenceService.saveFavorites(
                principal.getId(), request == null ? List.of() : request.items());
        return ResponseEntity.ok(ApiResponse.ok("Favorite salvate", saved));
    }

    /**
     * {@code GET /api/admin/search?q=…}
     *
     * <p>Each group is filled only if the caller holds the matching permission, and the
     * check happens here rather than inside the service so the service stays a plain
     * search with no opinion about security. A group the caller cannot view is absent
     * from the response rather than empty: the two are distinguishable, and the
     * difference would leak the existence of records the permission withholds.</p>
     */
    @GetMapping("/search")
    @PreAuthorize("@permissionService.has('DASHBOARD_VIEW')")
    public ResponseEntity<ApiResponse<GlobalSearchDto>> search(
            @RequestParam(name = "q", required = false) String query) {
        GlobalSearchDto results = globalSearchService.search(
                query,
                permissionService.has("PRODUCTS_VIEW"),
                permissionService.has("ORDERS_VIEW"),
                permissionService.has("USERS_MANAGE"));
        return ResponseEntity.ok(ApiResponse.ok(results));
    }

    // =====================================================================
    //  TASK 5 — activity feed
    // =====================================================================

    /** {@code GET /api/admin/activity?range=7d&category=PRODUCTS&actor=…&q=…} */
    @GetMapping("/activity")
    @PreAuthorize("@permissionService.has('AUDIT_VIEW')")
    public ResponseEntity<ApiResponse<ActivityFeedDto>> activity(
            @RequestParam(name = "range", required = false) String range,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "actor", required = false) String actor,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "25") int size) {
        MetricRange resolved = MetricRange.parse(range, MetricRange.D7);
        return ResponseEntity.ok(ApiResponse.ok(
                activityFeedService.feed(resolved, category, actor, query, page, size)));
    }

    /**
     * {@code GET /api/admin/activity/export} — the same rows as a CSV file.
     *
     * <p>Requires {@code AUDIT_EXPORT} rather than {@code AUDIT_VIEW}. Reading the log on
     * screen and carrying it out of the building as a file are different acts, and the
     * permission model already made that distinction before this endpoint existed.</p>
     */
    @GetMapping("/activity/export")
    @PreAuthorize("@permissionService.has('AUDIT_EXPORT')")
    public ResponseEntity<byte[]> exportActivity(
            @RequestParam(name = "range", required = false) String range,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "actor", required = false) String actor,
            @RequestParam(name = "q", required = false) String query) {

        MetricRange resolved = MetricRange.parse(range, MetricRange.D7);
        byte[] csv = exportService.exportActivity(
                activityFeedService.forExport(resolved, category, actor, query));

        String filename = "activitate-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
    }

    /** The layout payload. */
    public record LayoutRequest(List<DashboardLayoutDto.PanelState> panels, String density) {}

    /** The favourites payload. */
    public record FavoritesRequest(List<FavoritesDto.Favorite> items) {}
}
