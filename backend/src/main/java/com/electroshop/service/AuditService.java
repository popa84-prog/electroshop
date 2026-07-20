package com.electroshop.service;

import com.electroshop.dto.AuditLogDto;
import com.electroshop.model.AuditLog;
import com.electroshop.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records who did what across the admin surface. Logging must never break the
 * main operation, so every write is best-effort and swallows its own errors.
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
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

    @Transactional(readOnly = true)
    public Page<AuditLogDto> list(Pageable pageable) {
        return repository.findAllByOrderByCreatedAtDesc(pageable).map(AuditLogDto::from);
    }
}
