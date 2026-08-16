package com.likelion.hackathon.domain.notification.dto;

import com.likelion.hackathon.domain.notification.entity.Notification;
import com.likelion.hackathon.domain.notification.entity.enums.NotificationType;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID id, NotificationType type,
        UUID referenceId, String referenceType,
        boolean isRead, LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getType(),
                n.getReferenceId(), n.getReferenceType(),
                n.isRead(), n.getCreatedAt()
        );
    }
}
