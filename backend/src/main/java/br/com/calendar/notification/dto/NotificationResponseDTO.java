package br.com.calendar.notification.dto;

import br.com.calendar.notification.NotificationType;

import java.time.Instant;

public record NotificationResponseDTO(
        String id,
        String content,
        Instant timeBefore,
        NotificationType type,
        Boolean read,
        String taskId,
        Instant createdAt) {
}
