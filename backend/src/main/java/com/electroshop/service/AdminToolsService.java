package com.electroshop.service;

import com.electroshop.dto.AdminNoteDto;
import com.electroshop.dto.AdminToolsDto;
import com.electroshop.model.AdminNote;
import com.electroshop.model.AdminNoteKind;
import com.electroshop.repository.AdminNoteRepository;
import com.electroshop.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The administrator's personal workspace: notes, reminders, tasks and shortcuts.
 *
 * <p>Task 20.</p>
 *
 * <h2>Ownership is enforced in the query, not in the interface</h2>
 *
 * <p>Every read and every write is scoped by {@code adminId}, and the repository offers
 * no method that can reach a row without it. Two administrators sharing a panel must not
 * see each other's notes, and an interface-level check is one refactor away from being
 * bypassed. Update and delete load through {@code findByIdAndAdminId} so guessing a
 * numeric id gets an empty result rather than somebody else's reminder.</p>
 *
 * <h2>Shortcuts are derived from what this person actually does</h2>
 *
 * <p>The requirement asks for shortcuts to frequent actions, and "frequent" is already
 * recorded: the audit log knows what this administrator has been doing. A fixed menu
 * would be the same for everyone and right for nobody — the person who spends their day
 * in orders and the person who spends it in the catalogue want different buttons. The
 * list is built from their own recent actions and filtered to routes they can reach, with
 * a sensible default set for an account with no history yet.</p>
 */
@Service
public class AdminToolsService {

    /** How many items of each kind one administrator may keep. */
    static final int MAX_NOTES = 100;
    static final int MAX_REMINDERS = 100;
    static final int MAX_TASKS = 200;

    /** How many shortcuts the panel shows. */
    private static final int SHORTCUT_LIMIT = 6;

    /** How far back the shortcut derivation looks. */
    private static final int SHORTCUT_WINDOW_DAYS = 30;

    /** Actions mapped to the route and label a shortcut would carry. */
    private static final Map<String, String[]> ACTION_SHORTCUTS = new LinkedHashMap<>();

    static {
        // key = uppercase fragment of the action code
        // value = { label, icon, route }
        ACTION_SHORTCUTS.put("PRODUCT", new String[]{"Produse", "box", "/admin/products"});
        ACTION_SHORTCUTS.put("STOCK", new String[]{"Stoc", "box", "/admin/products"});
        ACTION_SHORTCUTS.put("ORDER", new String[]{"Comenzi", "cart", "/admin/orders"});
        ACTION_SHORTCUTS.put("USER", new String[]{"Utilizatori", "users", "/admin/users"});
        ACTION_SHORTCUTS.put("OFFER", new String[]{"Promoții", "tag", "/admin/offers"});
        ACTION_SHORTCUTS.put("PURCHASE", new String[]{"Recepții", "truck", "/admin/purchases"});
        ACTION_SHORTCUTS.put("SUPPLIER", new String[]{"Furnizori", "truck", "/admin/suppliers"});
        ACTION_SHORTCUTS.put("SETTING", new String[]{"Setări", "cog", "/admin/settings"});
        ACTION_SHORTCUTS.put("IMPORT", new String[]{"Import produse", "upload", "/admin/products"});
    }

    /** What a brand-new administrator sees before they have any history. */
    private static final List<AdminToolsDto.Shortcut> DEFAULT_SHORTCUTS = List.of(
            new AdminToolsDto.Shortcut("products", "Produse", "box", "/admin/products", 0, false),
            new AdminToolsDto.Shortcut("orders", "Comenzi", "cart", "/admin/orders", 0, false),
            new AdminToolsDto.Shortcut("offers", "Promoții", "tag", "/admin/offers", 0, false),
            new AdminToolsDto.Shortcut("purchases", "Recepții", "truck", "/admin/purchases", 0, false)
    );

    private final AdminNoteRepository noteRepository;
    private final AuditLogRepository auditLogRepository;

    public AdminToolsService(AdminNoteRepository noteRepository,
                             AuditLogRepository auditLogRepository) {
        this.noteRepository = noteRepository;
        this.auditLogRepository = auditLogRepository;
    }

    /** Everything the panel shows, in one response. */
    @Transactional(readOnly = true)
    public AdminToolsDto tools(Long adminId, String actorEmail) {
        LocalDateTime now = LocalDateTime.now();

        List<AdminNoteDto> notes = toDtos(
                noteRepository.findByAdminIdAndKindOrderByCreatedAtDesc(adminId, AdminNoteKind.NOTE), now);
        List<AdminNoteDto> reminders = toDtos(
                noteRepository.findByAdminIdAndKindOrderByCreatedAtDesc(adminId, AdminNoteKind.REMINDER), now);
        List<AdminNoteDto> tasks = toDtos(noteRepository.openTasks(adminId), now);

        // Completed tasks are appended after the open ones rather than hidden. A task
        // manager that erases finished work gives no sense of progress, and an operator
        // ticking things off wants to see the list they have cleared.
        List<AdminNote> allTasks =
                noteRepository.findByAdminIdAndKindOrderByCreatedAtDesc(adminId, AdminNoteKind.TASK);
        for (AdminNote task : allTasks) {
            if (task.isDone()) {
                tasks.add(toDto(task, now));
            }
        }

        return new AdminToolsDto(
                notes,
                reminders,
                tasks,
                shortcuts(actorEmail, now),
                noteRepository.countByAdminIdAndKindAndDoneFalse(adminId, AdminNoteKind.TASK),
                noteRepository.dueReminders(adminId, now).size(),
                new AdminToolsDto.Limits(
                        MAX_NOTES, MAX_REMINDERS, MAX_TASKS, AdminNote.MAX_CONTENT_LENGTH)
        );
    }

    /**
     * Creates or updates one item.
     *
     * <p>{@code id} present means update, absent means create — the only signal the
     * endpoint needs to tell the two apart, and one the client cannot get wrong by
     * omitting a flag.</p>
     */
    @Transactional
    public AdminNoteDto save(Long adminId, AdminNoteDto request) {
        AdminNoteKind kind = parseKind(request.kind());
        String content = request.content() == null ? "" : request.content().trim();

        if (content.isEmpty()) {
            throw new IllegalArgumentException("Conținutul nu poate fi gol");
        }
        if (content.length() > AdminNote.MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException(
                    "Conținutul depășește " + AdminNote.MAX_CONTENT_LENGTH + " de caractere");
        }

        AdminNote note;
        if (request.id() != null) {
            note = noteRepository.findByIdAndAdminId(request.id(), adminId)
                    .orElseThrow(() -> new IllegalArgumentException("Elementul nu există"));
        } else {
            enforceLimit(adminId, kind);
            note = new AdminNote(adminId, kind, null, content);
        }

        note.setKind(kind);
        note.setTitle(truncate(request.title(), 200));
        note.setContent(content);
        note.setDueAt(request.dueAt());
        note.setDone(request.done());
        note.setPriority(clampPriority(request.priority()));
        note.setLinkTo(safeRoute(request.linkTo()));

        return toDto(noteRepository.save(note), LocalDateTime.now());
    }

    /** Removes one item, scoped to its owner. */
    @Transactional
    public void delete(Long adminId, Long id) {
        noteRepository.findByIdAndAdminId(id, adminId).ifPresent(noteRepository::delete);
    }

    /** Toggles a task's completion. */
    @Transactional
    public AdminNoteDto toggleDone(Long adminId, Long id) {
        AdminNote note = noteRepository.findByIdAndAdminId(id, adminId)
                .orElseThrow(() -> new IllegalArgumentException("Elementul nu există"));
        note.setDone(!note.isDone());
        return toDto(noteRepository.save(note), LocalDateTime.now());
    }

    /**
     * Refuses a create that would exceed the per-kind cap.
     *
     * <p>The cap exists because this table has no other bound: a script hitting the
     * endpoint in a loop would otherwise fill the database through a feature meant for a
     * handful of reminders.</p>
     */
    private void enforceLimit(Long adminId, AdminNoteKind kind) {
        long existing = noteRepository
                .findByAdminIdAndKindOrderByCreatedAtDesc(adminId, kind).size();
        int max = switch (kind) {
            case NOTE -> MAX_NOTES;
            case REMINDER -> MAX_REMINDERS;
            case TASK -> MAX_TASKS;
        };
        if (existing >= max) {
            throw new IllegalArgumentException(
                    "Ai atins limita de " + max + " elemente de acest tip");
        }
    }

    /**
     * Quick actions, ranked by how often this person performs the underlying action.
     *
     * <p>Falls back to a default set when the account has no recent history, so a new
     * administrator gets a usable panel rather than an empty box that only fills after
     * they have already found the pages by hand.</p>
     */
    private List<AdminToolsDto.Shortcut> shortcuts(String actorEmail, LocalDateTime now) {
        if (actorEmail == null || actorEmail.isBlank()) {
            return DEFAULT_SHORTCUTS;
        }

        Map<String, long[]> tallies = new LinkedHashMap<>();
        for (Object[] row : auditLogRepository.actionCountsForActor(
                actorEmail, now.minusDays(SHORTCUT_WINDOW_DAYS))) {
            String action = row[0] == null ? "" : String.valueOf(row[0]).toUpperCase(Locale.ROOT);
            long count = ((Number) row[1]).longValue();

            for (Map.Entry<String, String[]> entry : ACTION_SHORTCUTS.entrySet()) {
                if (action.contains(entry.getKey())) {
                    tallies.computeIfAbsent(entry.getKey(), k -> new long[1])[0] += count;
                    break;
                }
            }
        }

        if (tallies.isEmpty()) {
            return DEFAULT_SHORTCUTS;
        }

        List<Map.Entry<String, long[]>> ranked = new ArrayList<>(tallies.entrySet());
        ranked.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));

        List<AdminToolsDto.Shortcut> out = new ArrayList<>(SHORTCUT_LIMIT);
        for (Map.Entry<String, long[]> entry : ranked) {
            if (out.size() >= SHORTCUT_LIMIT) {
                break;
            }
            String[] spec = ACTION_SHORTCUTS.get(entry.getKey());
            out.add(new AdminToolsDto.Shortcut(
                    entry.getKey().toLowerCase(Locale.ROOT),
                    spec[0], spec[1], spec[2],
                    entry.getValue()[0],
                    true));
        }
        return out;
    }

    private List<AdminNoteDto> toDtos(List<AdminNote> notes, LocalDateTime now) {
        List<AdminNoteDto> out = new ArrayList<>(notes.size());
        for (AdminNote note : notes) {
            out.add(toDto(note, now));
        }
        return out;
    }

    /**
     * Converts one row, computing {@code overdue} on the server.
     *
     * <p>Derived here rather than in the browser because a client clock that is wrong —
     * and they frequently are — would mark items overdue that are not, or hide ones that
     * are.</p>
     */
    private static AdminNoteDto toDto(AdminNote note, LocalDateTime now) {
        boolean overdue = !note.isDone()
                && note.getDueAt() != null
                && note.getDueAt().isBefore(now);

        return new AdminNoteDto(
                note.getId(),
                note.getKind() == null ? AdminNoteKind.NOTE.name() : note.getKind().name(),
                note.getTitle(),
                note.getContent(),
                note.getDueAt(),
                note.isDone(),
                note.getPriority(),
                note.getLinkTo(),
                overdue,
                note.getCreatedAt(),
                note.getUpdatedAt()
        );
    }

    private static AdminNoteKind parseKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return AdminNoteKind.NOTE;
        }
        try {
            return AdminNoteKind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return AdminNoteKind.NOTE;
        }
    }

    private static int clampPriority(int priority) {
        if (priority < AdminNote.MIN_PRIORITY) {
            return AdminNote.MIN_PRIORITY;
        }
        return Math.min(priority, AdminNote.MAX_PRIORITY);
    }

    /**
     * Keeps a stored link inside the application.
     *
     * <p>The interface renders this as an anchor, so a value that escaped this check
     * would be a stored redirect carrying the operator's session. Anything that is not a
     * plain {@code /admin/…} path is discarded rather than corrected — a half-repaired
     * URL is a URL somebody has to reason about later.</p>
     */
    private static String safeRoute(String route) {
        return AdminPreferenceService.isSafeAdminRoute(route == null ? null : route.trim())
                ? route.trim()
                : null;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
