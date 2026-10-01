package br.com.calendar.notification;

import br.com.calendar.common.exception.ResourceNotFoundException;
import br.com.calendar.notification.dto.NotificationReadResponse;
import br.com.calendar.notification.dto.NotificationResponseDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponseDTO> getNotifications(String userId) {
        return notificationRepository.findAllByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public NotificationReadResponse markAsRead(String notificationId, String userId) {
        int updatedCount = notificationRepository.markUnreadAsRead(notificationId, userId);
        if (updatedCount == 0 && !notificationRepository.existsByIdAndUser_Id(notificationId, userId)) {
            throw new ResourceNotFoundException("Notification not found");
        }

        return new NotificationReadResponse(updatedCount);
    }

    @Transactional
    public NotificationReadResponse markAllAsRead(String userId) {
        return new NotificationReadResponse(notificationRepository.markAllUnreadAsRead(userId));
    }

    private NotificationResponseDTO toResponse(Notification notification) {
        String taskId = notification.getTask() == null ? null : notification.getTask().getId();

        return new NotificationResponseDTO(
                notification.getId(),
                notification.getContent(),
                notification.getTimeBefore(),
                notification.getType(),
                notification.getRead(),
                taskId,
                notification.getCreatedAt());
    }
}
