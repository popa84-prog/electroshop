package com.electroshop.controller;

import com.electroshop.dto.*;
import com.electroshop.service.AuditService;
import com.electroshop.service.CompanySettingsService;
import com.electroshop.service.DashboardService;
import com.electroshop.service.InvoiceService;
import com.electroshop.service.NotificationService;
import com.electroshop.service.OrderService;
import com.electroshop.service.ProductService;
import com.electroshop.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Admin management panel API. SecurityConfig lets any of the three admin-panel
 * roles (Admin/Manager/Editor) through the {@code /admin/**} door; each endpoint
 * below then enforces the exact granular permission it needs via
 * {@code @PreAuthorize("@permissionService.has('...')")} (feature #6). User
 * management and company settings stay Admin-only because {@code USERS_MANAGE}
 * and {@code SETTINGS_MANAGE} are only granted to {@code ROLE_ADMIN} — see
 * {@link com.electroshop.security.RolePermissions}.
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final UserService userService;
    private final OrderService orderService;
    private final DashboardService dashboardService;
    private final AuditService auditService;
    private final CompanySettingsService companySettingsService;
    private final InvoiceService invoiceService;
    private final ProductService productService;
    private final NotificationService notificationService;

    public AdminController(UserService userService, OrderService orderService,
                          DashboardService dashboardService, AuditService auditService,
                          CompanySettingsService companySettingsService,
                          InvoiceService invoiceService, ProductService productService,
                          NotificationService notificationService) {
        this.userService = userService;
        this.orderService = orderService;
        this.dashboardService = dashboardService;
        this.auditService = auditService;
        this.companySettingsService = companySettingsService;
        this.invoiceService = invoiceService;
        this.productService = productService;
        this.notificationService = notificationService;
    }

    // ---------- Admin product views (with purchase price + profit, feature #2) ----------

    @GetMapping("/products")
    @PreAuthorize("@permissionService.has('PRODUCTS_VIEW')")
    public ResponseEntity<ApiResponse<PageResponse<AdminProductDto>>> listProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "false") boolean inStock,
            // Quick-filter shortcut (feature #3): "low_stock" | "out_of_stock" | "no_image".
            @RequestParam(required = false) String quickFilter,
            // "Sortează" dropdown (feature: sortare catalog) — see resolveProductSort
            // for the whitelist that keeps these two params safe to pass straight
            // through from the client.
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        // The table lets the operator pick the page size, so the value is
        // clamped here: a hand-crafted request must not be able to pull the
        // whole catalogue into memory in one response.
        int safeSize = Math.max(1, Math.min(size, 200));
        int safePage = Math.max(0, page);
        Page<AdminProductDto> result = productService.adminList(search, category, inStock, quickFilter,
                PageRequest.of(safePage, safeSize, resolveProductSort(sortBy, direction)));
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(result)));
    }

    /**
     * Whitelists the "Sortează" dropdown's field/direction pair into a JPA
     * {@link Sort}. Only {@code name}, {@code price} and {@code stockQuantity}
     * are accepted as explicit choices — anything else (including a hand-crafted
     * request probing for other entity properties) silently falls back to
     * {@code createdAt}, so this endpoint can never throw on bad input or leak
     * schema details through an error message the way handing {@code sortBy}
     * straight to {@code Sort.by(...)} would.
     */
    private Sort resolveProductSort(String sortBy, String direction) {
        String field = switch (sortBy == null ? "" : sortBy) {
            case "name", "price", "stockQuantity" -> sortBy;
            default -> "createdAt";
        };
        return "asc".equalsIgnoreCase(direction) ? Sort.by(field).ascending() : Sort.by(field).descending();
    }

    @GetMapping("/products/{id}")
    @PreAuthorize("@permissionService.has('PRODUCTS_VIEW')")
    public ResponseEntity<ApiResponse<AdminProductDto>> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(productService.adminGet(id)));
    }

    // ---------- PDF invoice for an order (feature #9) ----------

    @GetMapping("/orders/{id}/invoice")
    @PreAuthorize("@permissionService.has('ORDERS_VIEW')")
    public ResponseEntity<byte[]> orderInvoice(@PathVariable Long id) {
        InvoiceService.InvoiceFile file = invoiceService.generateForOrder(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + file.filename())
                .contentType(MediaType.APPLICATION_PDF)
                .body(file.content());
    }

    // ---------- Company / billing settings (feature #9) ----------

    @GetMapping("/company-settings")
    @PreAuthorize("@permissionService.has('SETTINGS_MANAGE')")
    public ResponseEntity<ApiResponse<CompanySettingsDto>> getCompanySettings() {
        return ResponseEntity.ok(ApiResponse.ok(companySettingsService.get()));
    }

    @PutMapping("/company-settings")
    @PreAuthorize("@permissionService.has('SETTINGS_MANAGE')")
    public ResponseEntity<ApiResponse<CompanySettingsDto>> updateCompanySettings(
            @RequestBody CompanySettingsDto request) {
        return ResponseEntity.ok(
                ApiResponse.ok("Date firmă salvate", companySettingsService.update(request)));
    }

    // ---------- Dashboard ----------

    @GetMapping("/dashboard")
    @PreAuthorize("@permissionService.has('DASHBOARD_VIEW')")
    public ResponseEntity<ApiResponse<DashboardStatsDto>> dashboard() {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getStats()));
    }

    // ---------- Audit log ----------

    /**
     * Filtered activity feed. {@code entityType}/{@code entityId} let the
     * product preview popup pull just that product's price/stock/image history;
     * {@code action} backs the "filtrare după tip acțiune" dropdown on the
     * standalone Jurnal de activitate page.
     */
    @GetMapping("/audit-logs")
    @PreAuthorize("@permissionService.has('AUDIT_VIEW')")
    public ResponseEntity<ApiResponse<PageResponse<AuditLogDto>>> auditLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<AuditLogDto> result = auditService.search(action, entityType, entityId, PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(result)));
    }

    /** Exports the same filtered feed as .xlsx (default) or .csv. */
    @GetMapping("/audit-logs/export")
    @PreAuthorize("@permissionService.has('AUDIT_EXPORT')")
    public ResponseEntity<byte[]> exportAuditLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(defaultValue = "xlsx") String format) {

        byte[] body = auditService.export(action, entityType, entityId, format);
        boolean csv = "csv".equalsIgnoreCase(format);
        String filename = csv ? "jurnal-activitate.csv" : "jurnal-activitate.xlsx";
        MediaType type = csv
                ? MediaType.parseMediaType("text/csv; charset=UTF-8")
                : MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(type)
                .body(body);
    }

    // ---------- Users CRUD ----------

    @GetMapping("/users")
    @PreAuthorize("@permissionService.has('USERS_MANAGE')")
    public ResponseEntity<ApiResponse<PageResponse<UserDto>>> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<UserDto> result = userService.list(search,
                PageRequest.of(page, size, Sort.by("id").descending()));
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(result)));
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("@permissionService.has('USERS_MANAGE')")
    public ResponseEntity<ApiResponse<UserDto>> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getById(id)));
    }

    /** Accounts awaiting approval (self-registered, not yet approved). */
    @GetMapping("/users/pending")
    @PreAuthorize("@permissionService.has('USERS_MANAGE')")
    public ResponseEntity<ApiResponse<PageResponse<UserDto>>> pendingUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<UserDto> result = userService.listPending(
                PageRequest.of(page, size, Sort.by("id").descending()));
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(result)));
    }

    /** Approve a pending account so the user can log in. */
    @PostMapping("/users/{id}/approve")
    @PreAuthorize("@permissionService.has('USERS_MANAGE')")
    public ResponseEntity<ApiResponse<UserDto>> approveUser(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Cont aprobat", userService.approve(id)));
    }

    /** Manually unlocks an account that brute-force protection locked before its timer expires. */
    @PostMapping("/users/{id}/unlock")
    @PreAuthorize("@permissionService.has('USERS_MANAGE')")
    public ResponseEntity<ApiResponse<UserDto>> unlockUser(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Cont deblocat", userService.unlock(id)));
    }

    /** Turns off 2FA for a user who lost access to their authenticator device. */
    @PostMapping("/users/{id}/disable-2fa")
    @PreAuthorize("@permissionService.has('USERS_MANAGE')")
    public ResponseEntity<ApiResponse<UserDto>> disableUserTwoFactor(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("2FA dezactivată", userService.adminDisableTwoFactor(id)));
    }

    /** Login/connection history: who logged in, from which IP and location, when. */
    @GetMapping("/login-events")
    @PreAuthorize("@permissionService.has('USERS_MANAGE')")
    public ResponseEntity<ApiResponse<PageResponse<LoginEventDto>>> loginEvents(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        Page<LoginEventDto> result = userService.listLoginEvents(userId, PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(result)));
    }

    @PostMapping("/users")
    @PreAuthorize("@permissionService.has('USERS_MANAGE')")
    public ResponseEntity<ApiResponse<UserDto>> createUser(@Valid @RequestBody UserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("User created", userService.create(request)));
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("@permissionService.has('USERS_MANAGE')")
    public ResponseEntity<ApiResponse<UserDto>> updateUser(@PathVariable Long id,
                                                          @Valid @RequestBody UserRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("User updated", userService.update(id, request)));
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("@permissionService.has('USERS_MANAGE')")
    public ResponseEntity<ApiResponse<Object>> deleteUser(@PathVariable Long id) {
        userService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("User deleted", null));
    }

    // ---------- Orders management ----------

    @GetMapping("/orders")
    @PreAuthorize("@permissionService.has('ORDERS_VIEW')")
    public ResponseEntity<ApiResponse<PageResponse<OrderDto>>> listOrders(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<OrderDto> result = orderService.getAllOrders(status,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(result)));
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize("@permissionService.has('ORDERS_VIEW')")
    public ResponseEntity<ApiResponse<OrderDto>> getOrder(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getOrder(id)));
    }

    @PutMapping("/orders/{id}/status")
    @PreAuthorize("@permissionService.has('ORDERS_MANAGE')")
    public ResponseEntity<ApiResponse<OrderDto>> updateOrderStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok("Order status updated", orderService.updateStatus(id, request.status())));
    }

    @DeleteMapping("/orders/{id}")
    @PreAuthorize("@permissionService.has('ORDERS_MANAGE')")
    public ResponseEntity<ApiResponse<Object>> deleteOrder(@PathVariable Long id) {
        orderService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Order deleted", null));
    }

    /**
     * Export the product catalogue as a stock list: produs / achiziție /
     * preț vânzare / stoc. Respects the table's search box, so the operator can
     * export either everything or just the rows currently filtered.
     */
    @GetMapping("/products/export")
    @PreAuthorize("@permissionService.has('PRODUCTS_VIEW')")
    public ResponseEntity<byte[]> exportProducts(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "xlsx") String format) {

        byte[] body = productService.exportProducts(search, format);
        boolean csv = "csv".equalsIgnoreCase(format);
        String filename = csv ? "produse.csv" : "produse.xlsx";
        MediaType type = csv
                ? MediaType.parseMediaType("text/csv; charset=UTF-8")
                : MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(type)
                .body(body);
    }

    /** Export orders in a date range as .xlsx (default) or .csv for accounting. */
    @GetMapping("/orders/export")
    @PreAuthorize("@permissionService.has('ORDERS_VIEW')")
    public ResponseEntity<byte[]> exportOrders(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "xlsx") String format) {

        byte[] body = orderService.exportOrders(from, to, format);
        boolean csv = "csv".equalsIgnoreCase(format);
        String filename = csv ? "comenzi.csv" : "comenzi.xlsx";
        MediaType type = csv
                ? MediaType.parseMediaType("text/csv; charset=UTF-8")
                : MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(type)
                .body(body);
    }

    // ---------- Notifications (feature #8) ----------
    // Gated with DASHBOARD_VIEW rather than a dedicated permission: notifications are
    // informational/low-risk and DASHBOARD_VIEW is already granted to all three admin
    // roles (Admin/Manager/Editor), so every admin-panel user sees the same bell.

    @GetMapping("/notifications")
    @PreAuthorize("@permissionService.has('DASHBOARD_VIEW')")
    public ResponseEntity<ApiResponse<PageResponse<NotificationDto>>> listNotifications(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<NotificationDto> result = notificationService.search(type, unreadOnly, PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(result)));
    }

    @GetMapping("/notifications/unread-count")
    @PreAuthorize("@permissionService.has('DASHBOARD_VIEW')")
    public ResponseEntity<ApiResponse<Long>> unreadNotificationCount() {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.unreadCount()));
    }

    @PostMapping("/notifications/{id}/read")
    @PreAuthorize("@permissionService.has('DASHBOARD_VIEW')")
    public ResponseEntity<ApiResponse<NotificationDto>> markNotificationRead(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.markRead(id)));
    }

    @PostMapping("/notifications/read-all")
    @PreAuthorize("@permissionService.has('DASHBOARD_VIEW')")
    public ResponseEntity<ApiResponse<Object>> markAllNotificationsRead() {
        int count = notificationService.markAllRead();
        return ResponseEntity.ok(ApiResponse.ok(count + " notificări marcate ca citite", null));
    }
}
