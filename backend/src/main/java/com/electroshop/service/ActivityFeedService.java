package com.electroshop.service;

import com.electroshop.dto.ActivityFeedDto;
import com.electroshop.model.AuditLog;
import com.electroshop.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The recent-activity panel: filtering, search, expandable detail and CSV export.
 *
 * <p>Task 5.</p>
 *
 * <h2>Categories are derived on the server</h2>
 *
 * <p>{@code AuditLog} records an action code and an entity type. It does not record
 * which of the operator's mental categories — products, orders, users, system — the
 * action belongs to, and the filter buttons need exactly that. Deriving it in the
 * browser would put the classification in one place and the filtering in another, and
 * the two would drift apart the first time an action code is added. Here the same
 * function produces the row's category and the filter's counts, so they cannot
 * disagree.</p>
 *
 * <h2>Field-level changes are parsed where they exist and left empty where they do not</h2>
 *
 * <p>The audit detail column holds free text written by whoever added each action. Some
 * of it follows a recognisable {@code field: old -> new} shape and can be parsed into
 * structured changes; much of it is a sentence. The parser handles the first case and
 * returns nothing for the second.</p>
 *
 * <p>Returning nothing is the point. Inferring a diff from an unstructured sentence
 * would produce confident, specific, invented claims about what somebody changed — and
 * an audit trail exists precisely so that such claims can be checked rather than
 * guessed. An empty {@code changes} list with the raw text beside it is honest; a
 * fabricated one is worse than no feature at all.</p>
 */
@Service
public class ActivityFeedService {

    /** Largest export, so a CSV request cannot stream the whole table into memory. */
    private static final int EXPORT_LIMIT = 10_000;

    /** Largest page the endpoint will serve. */
    private static final int MAX_PAGE_SIZE = 200;

    /**
     * Matches {@code field: old -> new} and its common variations.
     *
     * <p>Deliberately strict. A loose pattern would match ordinary prose containing an
     * arrow or a colon and turn a sentence into a fake field change, which is the exact
     * failure this method exists to avoid.</p>
     */
    private static final Pattern CHANGE = Pattern.compile(
            "([\\p{L}0-9 _]{2,40})\\s*:\\s*([^\\->;]{0,120}?)\\s*(?:->|→|=>)\\s*([^;]{0,120})");

    /** Action-code fragments that place a row in a category. */
    private static final Map<String, String[]> CATEGORY_KEYWORDS = Map.of(
            "PRODUCTS", new String[]{"PRODUCT", "STOCK", "IMAGE", "PRICE", "BRAND", "CATEGOR", "IMPORT"},
            "ORDERS", new String[]{"ORDER", "INVOICE", "SHIP", "DELIVER", "RETURN", "CANCEL"},
            "USERS", new String[]{"USER", "ROLE", "LOGIN", "PASSWORD", "2FA", "PERMISSION"},
            "SYSTEM", new String[]{"SETTING", "COMPANY", "BACKUP", "OFFER", "SUPPLIER", "PURCHASE", "NOTIFICATION"}
    );

    private static final Map<String, String> CATEGORY_LABELS = new LinkedHashMap<>();

    static {
        CATEGORY_LABELS.put("PRODUCTS", "Produse");
        CATEGORY_LABELS.put("ORDERS", "Comenzi");
        CATEGORY_LABELS.put("USERS", "Utilizatori");
        CATEGORY_LABELS.put("SYSTEM", "Sistem");
        CATEGORY_LABELS.put("OTHER", "Altele");
    }

    /** Admin routes an entity type opens, so a row becomes one click. */
    private static final Map<String, String> ENTITY_ROUTES = Map.of(
            "Product", "/admin/products",
            "Order", "/admin/orders",
            "User", "/admin/users",
            "Offer", "/admin/offers",
            "Supplier", "/admin/suppliers",
            "Purchase", "/admin/purchases",
            "CompanySettings", "/admin/settings"
    );

    private final AuditLogRepository auditLogRepository;

    public ActivityFeedService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * A filtered, searched page of activity.
     *
     * @param range    the window
     * @param category one of the category keys, or {@code null} for all
     * @param actor    restrict to one person, or {@code null} for all
     * @param query    free-text search across action, entity and detail
     * @param page     zero-based page index
     * @param size     page size
     */
    @Transactional(readOnly = true)
    public ActivityFeedDto feed(MetricRange range,
                                String category,
                                String actor,
                                String query,
                                int page,
                                int size) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = range.from(now);
        LocalDateTime to = range.to(now);

        String q = query == null || query.isBlank() ? null : query.trim();
        String actorFilter = actor == null || actor.isBlank() ? null : actor.trim();

        Page<AuditLog> found = auditLogRepository.searchFeed(
                from, to, actorFilter, q,
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(MAX_PAGE_SIZE, size))));

        // The category filter is applied after the page is read, because the category
        // is derived rather than stored. Pushing it into the query would mean encoding
        // the keyword lists as SQL LIKE clauses, which would put the classification in
        // two places written in two languages.
        List<ActivityFeedDto.Entry> entries = new ArrayList<>();
        for (AuditLog log : found.getContent()) {
            ActivityFeedDto.Entry entry = toEntry(log);
            if (category == null || category.isBlank() || "ALL".equalsIgnoreCase(category)
                    || entry.category().equalsIgnoreCase(category)) {
                entries.add(entry);
            }
        }

        return new ActivityFeedDto(
                entries,
                found.getNumber(),
                found.getSize(),
                found.getTotalElements(),
                found.getTotalPages(),
                categoryCounts(from, to),
                auditLogRepository.distinctActorsBetween(from, to),
                range.info(now)
        );
    }

    /** Rows for the CSV export, capped. */
    @Transactional(readOnly = true)
    public List<ActivityFeedDto.Entry> forExport(MetricRange range,
                                                 String category,
                                                 String actor,
                                                 String query) {
        LocalDateTime now = LocalDateTime.now();
        String q = query == null || query.isBlank() ? null : query.trim();
        String actorFilter = actor == null || actor.isBlank() ? null : actor.trim();

        Page<AuditLog> found = auditLogRepository.searchFeed(
                range.from(now), range.to(now), actorFilter, q,
                PageRequest.of(0, EXPORT_LIMIT));

        List<ActivityFeedDto.Entry> out = new ArrayList<>();
        for (AuditLog log : found.getContent()) {
            ActivityFeedDto.Entry entry = toEntry(log);
            if (category == null || category.isBlank() || "ALL".equalsIgnoreCase(category)
                    || entry.category().equalsIgnoreCase(category)) {
                out.add(entry);
            }
        }
        return out;
    }

    private ActivityFeedDto.Entry toEntry(AuditLog log) {
        String category = categoryOf(log.getAction(), log.getEntityType());
        return new ActivityFeedDto.Entry(
                log.getId(),
                log.getActor(),
                log.getAction(),
                humanise(log.getAction()),
                category,
                log.getEntityType(),
                log.getEntityId(),
                entityNameFrom(log.getDetails()),
                linkFor(log.getEntityType(), log.getEntityId()),
                log.getDetails(),
                parseChanges(log.getDetails()),
                log.getCreatedAt()
        );
    }

    /**
     * Which category an action belongs to.
     *
     * <p>The entity type is consulted first because it is the more reliable signal: an
     * action code is a free string somebody chose, while the entity type names an actual
     * class. Keywords are the fallback for actions that touch no entity.</p>
     */
    static String categoryOf(String action, String entityType) {
        if (entityType != null) {
            String type = entityType.toUpperCase(Locale.ROOT);
            if (type.contains("PRODUCT")) {
                return "PRODUCTS";
            }
            if (type.contains("ORDER")) {
                return "ORDERS";
            }
            if (type.contains("USER") || type.contains("ROLE")) {
                return "USERS";
            }
        }

        String code = action == null ? "" : action.toUpperCase(Locale.ROOT);
        for (Map.Entry<String, String[]> entry : CATEGORY_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (code.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }
        return "OTHER";
    }

    /**
     * Turns {@code PRODUCT_STOCK_CHANGED} into {@code Produs stoc modificat}.
     *
     * <p>A best-effort readable label. The raw code travels beside it in the response,
     * so a support conversation can always refer to the exact stored value rather than
     * to this rendering.</p>
     */
    static String humanise(String action) {
        if (action == null || action.isBlank()) {
            return "—";
        }
        Map<String, String> words = Map.ofEntries(
                Map.entry("PRODUCT", "Produs"),
                Map.entry("ORDER", "Comandă"),
                Map.entry("USER", "Utilizator"),
                Map.entry("STOCK", "stoc"),
                Map.entry("PRICE", "preț"),
                Map.entry("IMAGE", "imagine"),
                Map.entry("BRAND", "marcă"),
                Map.entry("CREATED", "creat"),
                Map.entry("UPDATED", "actualizat"),
                Map.entry("DELETED", "șters"),
                Map.entry("CHANGED", "modificat"),
                Map.entry("APPROVED", "aprobat"),
                Map.entry("CANCELLED", "anulat"),
                Map.entry("SHIPPED", "expediat"),
                Map.entry("DELIVERED", "livrat"),
                Map.entry("RETURNED", "returnat"),
                Map.entry("IMPORT", "import"),
                Map.entry("EXPORT", "export"),
                Map.entry("LOGIN", "autentificare"),
                Map.entry("SETTINGS", "setări"),
                Map.entry("OFFER", "ofertă")
        );

        StringBuilder sb = new StringBuilder();
        for (String part : action.split("_")) {
            String translated = words.get(part.toUpperCase(Locale.ROOT));
            sb.append(sb.length() == 0 ? "" : " ");
            sb.append(translated != null ? translated : part.toLowerCase(Locale.ROOT));
        }
        String result = sb.toString();
        return result.isEmpty() ? action : Character.toUpperCase(result.charAt(0)) + result.substring(1);
    }

    /**
     * Field-level changes, where the detail text carries them in a recognisable shape.
     *
     * <p>Returns an empty list rather than guessing. See the class comment.</p>
     */
    static List<ActivityFeedDto.FieldChange> parseChanges(String details) {
        if (details == null || details.isBlank()) {
            return List.of();
        }
        List<ActivityFeedDto.FieldChange> out = new ArrayList<>();
        Matcher matcher = CHANGE.matcher(details);
        while (matcher.find() && out.size() < 20) {
            String field = matcher.group(1).trim();
            String oldValue = blankToNull(matcher.group(2));
            String newValue = blankToNull(matcher.group(3));
            if (!field.isEmpty() && (oldValue != null || newValue != null)) {
                out.add(new ActivityFeedDto.FieldChange(field, oldValue, newValue));
            }
        }
        return out;
    }

    /**
     * A readable entity name pulled from the detail text, when one is quoted.
     *
     * <p>Only a quoted string counts. Taking the first few words instead would produce a
     * plausible-looking name for rows that never had one.</p>
     */
    static String entityNameFrom(String details) {
        if (details == null) {
            return null;
        }
        Matcher quoted = Pattern.compile("[\"„']([^\"”']{2,120})[\"”']").matcher(details);
        return quoted.find() ? quoted.group(1) : null;
    }

    private static String linkFor(String entityType, Long entityId) {
        if (entityType == null) {
            return null;
        }
        String base = ENTITY_ROUTES.get(entityType);
        if (base == null) {
            return null;
        }
        return entityId == null ? base : base + "?id=" + entityId;
    }

    /**
     * How many rows each category holds in the window.
     *
     * <p>Counted over the whole window rather than over the current page, because the
     * filter buttons describe what filtering would find, not what is currently on
     * screen. The rows are read in one pass and classified in memory for the same
     * reason the filter is: the classification lives in one function.</p>
     */
    private List<ActivityFeedDto.CategoryCount> categoryCounts(LocalDateTime from,
                                                               LocalDateTime to) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String key : CATEGORY_LABELS.keySet()) {
            counts.put(key, 0L);
        }

        for (Object[] row : auditLogRepository.actionEntityCounts(from, to)) {
            String category = categoryOf((String) row[0], (String) row[1]);
            counts.merge(category, ((Number) row[2]).longValue(), Long::sum);
        }

        List<ActivityFeedDto.CategoryCount> out = new ArrayList<>(counts.size());
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            out.add(new ActivityFeedDto.CategoryCount(
                    entry.getKey(), CATEGORY_LABELS.get(entry.getKey()), entry.getValue()));
        }
        return out;
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String trimmed = s.trim();
        return trimmed.isEmpty() || "—".equals(trimmed) || "null".equalsIgnoreCase(trimmed)
                ? null
                : trimmed;
    }
}
