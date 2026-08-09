package com.electroshop.controller;

import com.electroshop.dto.AdminNoteDto;
import com.electroshop.dto.AdminToolsDto;
import com.electroshop.dto.ApiResponse;
import com.electroshop.security.UserPrincipal;
import com.electroshop.service.AdminToolsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The administrator's personal workspace: notes, reminders, tasks and shortcuts.
 *
 * <p>Task 20. Full path is {@code /api/admin/tools}.</p>
 *
 * <p><b>The owner is always the authenticated caller.</b> No endpoint takes an
 * administrator id, so there is no request in which changing a number reaches somebody
 * else's notes. The service enforces the same rule at the query level, which is the
 * layer that still holds if a future endpoint forgets.</p>
 */
@RestController
@RequestMapping("/admin/tools")
public class AdminToolsController {

    private final AdminToolsService adminToolsService;

    public AdminToolsController(AdminToolsService adminToolsService) {
        this.adminToolsService = adminToolsService;
    }

    /**
     * Everything the panel shows.
     *
     * <p>{@code GET /api/admin/tools}</p>
     *
     * <p>One request for all four modules. They are small, always rendered together, and
     * four calls to fetch a handful of rows each would cost more in round trips than the
     * data weighs.</p>
     */
    @GetMapping
    @PreAuthorize("@permissionService.has('TOOLS_USE')")
    public ResponseEntity<ApiResponse<AdminToolsDto>> tools(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                adminToolsService.tools(principal.getId(), principal.getUsername())));
    }

    /**
     * Creates one item.
     *
     * <p>{@code POST /api/admin/tools}</p>
     */
    @PostMapping
    @PreAuthorize("@permissionService.has('TOOLS_USE')")
    public ResponseEntity<ApiResponse<AdminNoteDto>> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody AdminNoteDto request) {
        // The id is stripped rather than trusted: a create carrying an id would be an
        // update, and an update on this path would bypass the create-side limit check.
        AdminNoteDto sanitised = new AdminNoteDto(
                null, request.kind(), request.title(), request.content(),
                request.dueAt(), request.done(), request.priority(), request.linkTo(),
                false, null, null);
        return ResponseEntity.ok(ApiResponse.ok(
                "Element creat", adminToolsService.save(principal.getId(), sanitised)));
    }

    /**
     * Updates one item.
     *
     * <p>{@code PUT /api/admin/tools/{id}}</p>
     *
     * <p>The id comes from the path, not from the body. When the two disagree the path
     * wins, because that is the value the route, the logs and any future authorisation
     * rule all see.</p>
     */
    @PutMapping("/{id}")
    @PreAuthorize("@permissionService.has('TOOLS_USE')")
    public ResponseEntity<ApiResponse<AdminNoteDto>> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") Long id,
            @RequestBody AdminNoteDto request) {
        AdminNoteDto withId = new AdminNoteDto(
                id, request.kind(), request.title(), request.content(),
                request.dueAt(), request.done(), request.priority(), request.linkTo(),
                false, null, null);
        return ResponseEntity.ok(ApiResponse.ok(
                "Element actualizat", adminToolsService.save(principal.getId(), withId)));
    }

    /**
     * Ticks a task off, or puts it back.
     *
     * <p>{@code POST /api/admin/tools/{id}/toggle}</p>
     *
     * <p>A dedicated endpoint rather than a full update, because ticking a box should not
     * require the client to send back the whole record — and a client that sends back a
     * stale record silently reverts whatever changed in between.</p>
     */
    @PostMapping("/{id}/toggle")
    @PreAuthorize("@permissionService.has('TOOLS_USE')")
    public ResponseEntity<ApiResponse<AdminNoteDto>> toggle(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") Long id) {
        return ResponseEntity.ok(ApiResponse.ok(
                adminToolsService.toggleDone(principal.getId(), id)));
    }

    /**
     * Removes one item.
     *
     * <p>{@code DELETE /api/admin/tools/{id}}</p>
     *
     * <p>A note belongs to one person and has no accounting significance, so this is a
     * real delete rather than a soft one. That is deliberately unlike products and
     * orders, where deletion is a deactivation precisely because history depends on
     * them.</p>
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionService.has('TOOLS_USE')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") Long id) {
        adminToolsService.delete(principal.getId(), id);
        return ResponseEntity.ok(ApiResponse.ok("Element șters", null));
    }
}
