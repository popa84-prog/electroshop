package com.electroshop.dto;

import com.electroshop.model.AuditLog;

import java.time.LocalDateTime;

public record AuditLogDto(
        Long id,
        String actor,
        String action,
        String entityType,
        Long entityId,
        String details,
        LocalDateTime createdAt
) {
    public static AuditLogDto from(AuditLog a) {
        return new AuditLogDto(
                a.getId(), a.getActor(), a.getAction(), a.getEntityType(),
                a.getEntityId(), a.getDetails(), a.getCreatedAt());
    }
}
