package com.electroshop.dto;

import com.electroshop.model.Notification;

import java.time.LocalDateTime;

public record NotificationDto(
        Long id,
        String type,
        String title,
        String message,
        String entityType,
        Long entityId,
        boolean read,
        LocalDateTime createdAt
) {
    public static NotificationDto from(Notification n) {
        return new NotificationDto(
                n.getId(), n.getType(), n.getTitle(), n.getMessage(),
                n.getEntityType(), n.getEntityId(), n.isRead(), n.getCreatedAt());
    }
}
