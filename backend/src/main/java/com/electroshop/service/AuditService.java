package com.electroshop.service;

import com.electroshop.dto.AuditLogDto;
import com.electroshop.model.AuditLog;
import com.electroshop.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Records who did what across the admin surface. Logging must never break the
 * main operation, so every write is best-effort and swallows its own errors.
 */
@Service
public class AuditService {

    /** Upper bound on how many rows a single export may contain — a report, not a dump. */
    private static final int MAX_EXPORT_ROWS = 20_000;

    private final AuditLogRepository repository;
    private final AuditLogExportService exportService;

    public AuditService(AuditLogRepository repository, AuditLogExportService exportService) {
        this.repository = repository;
        this.exportService = exportService;
    }

    public void log(String action, String entityType, Long entityId, String details) {
        try {
            AuditLog entry = new AuditLog();
            entry.setActor(currentActor());
            entry.setAction(action);
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            entry.setDetails(details != null && details.length() > 500 ? details.substring(0, 500) : details);
            repository.save(entry);
        } catch (Exception ignored) {
            // auditing must not interfere with the primary flow
        }
    }

    /**
     * Cine execută acțiunea curentă, ca text.
     *
     * <p>Expus public pentru documentele fiscale, care trebuie să rețină în
     * propriile coloane cine a cerut o stornare — jurnalul de audit se poate
     * arhiva sau curăța, iar documentul trebuie să rămână lizibil singur.
     * Alternativa ar fi fost ca fiecare serviciu să citească din nou contextul
     * de securitate, adică mai multe locuri în care numele actorului se poate
     * determina altfel decât în jurnal.</p>
     */
    public String currentActorName() {
        return currentActor();
    }

    private String currentActor() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null) {
                return auth.getName();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "system";
    }

    /**
     * Filtered activity feed — pass all-null filters for the unfiltered case
     * (feature #5: filtrare după tip acțiune, and the per-product history
     * popup, which passes entityType="Product" + entityId).
     */
    @Transactional(readOnly = true)
    public Page<AuditLogDto> search(String action, String entityType, Long entityId, Pageable pageable) {
        return repository.search(blankToNull(action), blankToNull(entityType), entityId, pageable)
                .map(AuditLogDto::from);
    }

    @Transactional(readOnly = true)
    public byte[] export(String action, String entityType, Long entityId, String format) {
        List<AuditLogDto> rows = repository.search(
                blankToNull(action), blankToNull(entityType), entityId,
                PageRequest.of(0, MAX_EXPORT_ROWS, Sort.by("createdAt").descending())
        ).map(AuditLogDto::from).getContent();

        return "csv".equalsIgnoreCase(format) ? exportService.toCsv(rows) : exportService.toExcel(rows);
    }

    private String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v;
    }
}
