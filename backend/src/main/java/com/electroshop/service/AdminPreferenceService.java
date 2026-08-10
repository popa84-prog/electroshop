package com.electroshop.service;

import com.electroshop.dto.DashboardLayoutDto;
import com.electroshop.dto.FavoritesDto;
import com.electroshop.model.AdminPreference;
import com.electroshop.repository.AdminPreferenceRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Per-administrator settings: dashboard layout, sidebar favourites, view density.
 *
 * <p>Tasks 3 and 4.</p>
 *
 * <h2>The server stores the layout; it does not interpret it</h2>
 *
 * <p>Which panels exist, what they are called and how wide they can be are decisions the
 * frontend owns. Duplicating that knowledge here would mean a backend change every time
 * a card is added, and two lists that must be kept identical always end up not being.</p>
 *
 * <p>What the backend does enforce is what only it can: the payload is bounded, the JSON
 * is well-formed, spans stay inside the grid, and a panel id it does not recognise is
 * dropped rather than rejected. That last point matters — a layout saved by a newer
 * frontend must not become unloadable after a rollback, which is exactly what rejecting
 * the whole payload for one unknown id would cause.</p>
 *
 * <h2>Favourites are validated on read as well as on write</h2>
 *
 * <p>A favourites list is user-controlled data that the interface turns into a link,
 * which makes it precisely the kind of value that must never be able to carry
 * {@code javascript:} or an off-site address. Checking on write is obvious; checking
 * again on read covers rows that predate the check or were inserted directly into the
 * database, which is the case a write-side-only guard silently misses.</p>
 */
@Service
public class AdminPreferenceService {

    /** How many favourites one administrator may pin. */
    static final int MAX_FAVORITES = 12;

    /** Every panel the dashboard knows about, in default display order. */
    private static final List<String> DEFAULT_PANEL_ORDER = List.of(
            "business-banner",
            "sales-chart",
            "profit-breakdown",
            "financial-overview",
            "inventory-health",
            "top-products",
            "product-performance",
            "order-efficiency",
            "customer-insights",
            "marketing-performance",
            "predictive-sales",
            "health-status",
            "ai-assistant",
            "activity",
            "operational-logs",
            "admin-tools",
            "system"
    );

    /** How wide each panel is by default, in twelve-column units. */
    private static final Map<String, Integer> DEFAULT_SPANS = Map.ofEntries(
            Map.entry("business-banner", 12),
            Map.entry("sales-chart", 8),
            Map.entry("predictive-sales", 4),
            Map.entry("profit-breakdown", 12),
            Map.entry("financial-overview", 12),
            Map.entry("inventory-health", 6),
            Map.entry("top-products", 6),
            Map.entry("product-performance", 6),
            Map.entry("order-efficiency", 6),
            Map.entry("customer-insights", 6),
            Map.entry("marketing-performance", 6),
            Map.entry("health-status", 4),
            Map.entry("ai-assistant", 8),
            Map.entry("activity", 6),
            Map.entry("operational-logs", 6),
            Map.entry("admin-tools", 6),
            Map.entry("system", 6)
    );

    /** The grid is twelve columns; a span outside this range is clamped. */
    private static final int MIN_SPAN = 3;
    private static final int MAX_SPAN = 12;

    /** Schema version of the stored layout payload. */
    private static final int LAYOUT_VERSION = 1;

    private final AdminPreferenceRepository repository;
    private final ObjectMapper objectMapper;

    public AdminPreferenceService(AdminPreferenceRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    // =====================================================================
    //  Dashboard layout
    // =====================================================================

    /**
     * The administrator's layout, or the default arrangement when they have none.
     *
     * <p>Never an empty response. An administrator who has customised nothing gets the
     * full default with {@code customised = false}, so the frontend renders one code
     * path either way and "Reset layout" is a delete followed by this same call.</p>
     */
    @Transactional(readOnly = true)
    public DashboardLayoutDto layout(Long adminId) {
        Optional<AdminPreference> stored =
                repository.findByAdminIdAndPrefKey(adminId, AdminPreference.KEY_DASHBOARD_LAYOUT);
        String density = readString(adminId, AdminPreference.KEY_DASHBOARD_DENSITY, "COMFORTABLE");

        if (stored.isEmpty() || stored.get().getValue() == null || stored.get().getValue().isBlank()) {
            return new DashboardLayoutDto(defaultPanels(), density, false, null, LAYOUT_VERSION);
        }

        try {
            List<DashboardLayoutDto.PanelState> panels = objectMapper.readValue(
                    stored.get().getValue(), new TypeReference<List<DashboardLayoutDto.PanelState>>() {});
            return new DashboardLayoutDto(
                    reconcile(panels), density, true, stored.get().getUpdatedAt(), LAYOUT_VERSION);
        } catch (Exception e) {
            // The stored value is not readable — hand-edited, or written by a version
            // whose shape has since changed. Falling back to the default is better than
            // failing the request: the operator loses an arrangement, not the dashboard.
            return new DashboardLayoutDto(defaultPanels(), density, false, null, LAYOUT_VERSION);
        }
    }

    /** Saves an arrangement, after clamping it into something the grid can render. */
    @Transactional
    public DashboardLayoutDto saveLayout(Long adminId, List<DashboardLayoutDto.PanelState> panels,
                                         String density) {
        List<DashboardLayoutDto.PanelState> clean = reconcile(panels);

        String json;
        try {
            json = objectMapper.writeValueAsString(clean);
        } catch (Exception e) {
            // Cannot happen for a list of records with primitive fields, but a silent
            // partial save would be far worse than a clear failure.
            throw new IllegalStateException("Layout-ul nu a putut fi serializat", e);
        }
        if (json.length() > AdminPreference.MAX_VALUE_LENGTH) {
            throw new IllegalArgumentException("Layout-ul depășește dimensiunea permisă");
        }

        upsert(adminId, AdminPreference.KEY_DASHBOARD_LAYOUT, json);
        if (density != null && !density.isBlank()) {
            upsert(adminId, AdminPreference.KEY_DASHBOARD_DENSITY, normaliseDensity(density));
        }

        return layout(adminId);
    }

    /** Removes the saved arrangement, which is what "Reset layout" does. */
    @Transactional
    public DashboardLayoutDto resetLayout(Long adminId) {
        repository.deleteByAdminIdAndPrefKey(adminId, AdminPreference.KEY_DASHBOARD_LAYOUT);
        repository.deleteByAdminIdAndPrefKey(adminId, AdminPreference.KEY_DASHBOARD_DENSITY);
        return layout(adminId);
    }

    /**
     * Merges a stored arrangement with the current panel registry.
     *
     * <p>Three things happen here, and each covers a real case. Unknown ids are dropped,
     * so a layout from a newer frontend still loads. Panels the stored layout has never
     * seen are appended, so a newly added card appears rather than being invisible until
     * the operator resets. And spans are clamped, so a hand-edited value cannot produce a
     * card wider than the grid.</p>
     */
    private static List<DashboardLayoutDto.PanelState> reconcile(
            List<DashboardLayoutDto.PanelState> stored) {

        List<DashboardLayoutDto.PanelState> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        if (stored != null) {
            for (DashboardLayoutDto.PanelState panel : stored) {
                if (panel == null || panel.id() == null) {
                    continue;
                }
                if (!DEFAULT_SPANS.containsKey(panel.id()) || !seen.add(panel.id())) {
                    continue;
                }
                out.add(new DashboardLayoutDto.PanelState(
                        panel.id(),
                        out.size(),
                        clampSpan(panel.span(), panel.id()),
                        panel.hidden()));
            }
        }

        for (String id : DEFAULT_PANEL_ORDER) {
            if (seen.add(id)) {
                out.add(new DashboardLayoutDto.PanelState(
                        id, out.size(), DEFAULT_SPANS.getOrDefault(id, 6), false));
            }
        }

        return out;
    }

    private static int clampSpan(int span, String panelId) {
        if (span < MIN_SPAN || span > MAX_SPAN) {
            return DEFAULT_SPANS.getOrDefault(panelId, 6);
        }
        return span;
    }

    private static List<DashboardLayoutDto.PanelState> defaultPanels() {
        return reconcile(List.of());
    }

    private static String normaliseDensity(String raw) {
        return "COMPACT".equalsIgnoreCase(raw.trim()) ? "COMPACT" : "COMFORTABLE";
    }

    // =====================================================================
    //  Sidebar favourites
    // =====================================================================

    /** The administrator's pinned routes, validated on the way out. */
    @Transactional(readOnly = true)
    public FavoritesDto favorites(Long adminId) {
        Optional<AdminPreference> stored =
                repository.findByAdminIdAndPrefKey(adminId, AdminPreference.KEY_SIDEBAR_FAVORITES);

        if (stored.isEmpty() || stored.get().getValue() == null || stored.get().getValue().isBlank()) {
            return new FavoritesDto(List.of(), MAX_FAVORITES);
        }

        try {
            List<FavoritesDto.Favorite> items = objectMapper.readValue(
                    stored.get().getValue(), new TypeReference<List<FavoritesDto.Favorite>>() {});
            return new FavoritesDto(sanitise(items), MAX_FAVORITES);
        } catch (Exception e) {
            return new FavoritesDto(List.of(), MAX_FAVORITES);
        }
    }

    /** Replaces the pinned routes. */
    @Transactional
    public FavoritesDto saveFavorites(Long adminId, List<FavoritesDto.Favorite> items) {
        List<FavoritesDto.Favorite> clean = sanitise(items);

        String json;
        try {
            json = objectMapper.writeValueAsString(clean);
        } catch (Exception e) {
            throw new IllegalStateException("Favoritele nu au putut fi serializate", e);
        }
        if (json.length() > AdminPreference.MAX_VALUE_LENGTH) {
            throw new IllegalArgumentException("Lista de favorite depășește dimensiunea permisă");
        }

        upsert(adminId, AdminPreference.KEY_SIDEBAR_FAVORITES, json);
        return favorites(adminId);
    }

    /**
     * Drops anything that is not a safe in-application route, and caps the list.
     *
     * <p>A route must begin with {@code /admin/} and contain no scheme, no protocol
     * separator and no backslash. The frontend renders these as links, so a value that
     * escaped this check would be a stored redirect with the operator's session attached
     * to it.</p>
     */
    private static List<FavoritesDto.Favorite> sanitise(List<FavoritesDto.Favorite> items) {
        if (items == null) {
            return List.of();
        }
        List<FavoritesDto.Favorite> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (FavoritesDto.Favorite item : items) {
            if (item == null || item.route() == null) {
                continue;
            }
            String route = item.route().trim();
            if (!isSafeAdminRoute(route) || !seen.add(route)) {
                continue;
            }
            out.add(new FavoritesDto.Favorite(
                    route,
                    truncate(item.label(), 60),
                    truncate(item.icon(), 40),
                    out.size()));
            if (out.size() >= MAX_FAVORITES) {
                break;
            }
        }
        return out;
    }

    static boolean isSafeAdminRoute(String route) {
        if (route == null || !route.startsWith("/admin/") || route.length() > 200) {
            return false;
        }
        // "//host" is a protocol-relative URL and leaves the site despite starting with
        // a slash, so the leading-slash check alone is not enough.
        return !route.startsWith("//")
                && !route.contains("://")
                && !route.contains("\\")
                && !route.contains("javascript:")
                && !route.contains("\n")
                && !route.contains("\r");
    }

    // =====================================================================
    //  Shared
    // =====================================================================

    /** Reads a plain string setting, or a default when it is absent. */
    @Transactional(readOnly = true)
    public String readString(Long adminId, String key, String fallback) {
        return repository.findByAdminIdAndPrefKey(adminId, key)
                .map(AdminPreference::getValue)
                .filter(v -> v != null && !v.isBlank())
                .orElse(fallback);
    }

    /** Writes a plain string setting. */
    @Transactional
    public void writeString(Long adminId, String key, String value) {
        upsert(adminId, key, value);
    }

    /**
     * Inserts or updates one setting.
     *
     * <p>Read-then-write rather than a database upsert, because Spring Data offers no
     * portable one and the unique constraint on {@code (adminId, prefKey)} makes a lost
     * race a constraint violation rather than a duplicate row — a loud failure instead
     * of a silent inconsistency.</p>
     */
    private void upsert(Long adminId, String key, String value) {
        AdminPreference preference = repository.findByAdminIdAndPrefKey(adminId, key)
                .orElseGet(() -> new AdminPreference(adminId, key, null));
        preference.setValue(value);
        repository.save(preference);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
