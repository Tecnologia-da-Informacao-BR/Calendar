package br.com.calendar.notification;

import br.com.calendar.common.exception.ResourceNotFoundException;
import br.com.calendar.notification.dto.NotificationReadResponse;
import br.com.calendar.notification.dto.NotificationResponseDTO;
import br.com.calendar.task.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final String USER_ID = "usr_abc123";
    private static final String NOTIFICATION_ID = "notification_123";

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository);
    }

    @Test
    void returnsUserNotificationsMappedInRepositoryOrder() {
        Notification newest = notification("notification_new", Instant.parse("2026-10-01T19:45:00Z"), false, "task_new");
        Notification oldest = notification("notification_old", Instant.parse("2026-10-01T18:45:00Z"), true, null);
        when(notificationRepository.findAllByUser_IdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of(newest, oldest));

        List<NotificationResponseDTO> response = notificationService.getNotifications(USER_ID);

        assertEquals(2, response.size());
        assertEquals("notification_new", response.get(0).id());
        assertEquals("task_new", response.get(0).taskId());
        assertEquals(false, response.get(0).read());
        assertEquals("notification_old", response.get(1).id());
        assertEquals(true, response.get(1).read());
        assertNull(response.get(1).taskId());
        verify(notificationRepository).findAllByUser_IdOrderByCreatedAtDesc(USER_ID);
    }

    @Test
    void marksOneUnreadNotificationAsReadAndReturnsUpdatedCount() {
        when(notificationRepository.markUnreadAsRead(NOTIFICATION_ID, USER_ID)).thenReturn(1);

        NotificationReadResponse response = notificationService.markAsRead(NOTIFICATION_ID, USER_ID);

        assertEquals(1, response.count());
        verify(notificationRepository).markUnreadAsRead(NOTIFICATION_ID, USER_ID);
    }

    @Test
    void returnsZeroWhenNotificationIsAlreadyRead() {
        when(notificationRepository.markUnreadAsRead(NOTIFICATION_ID, USER_ID)).thenReturn(0);
        when(notificationRepository.existsByIdAndUser_Id(NOTIFICATION_ID, USER_ID)).thenReturn(true);

        NotificationReadResponse response = notificationService.markAsRead(NOTIFICATION_ID, USER_ID);

        assertEquals(0, response.count());
    }

    @Test
    void throwsNotFoundWhenNotificationDoesNotBelongToUser() {
        when(notificationRepository.markUnreadAsRead(NOTIFICATION_ID, USER_ID)).thenReturn(0);
        when(notificationRepository.existsByIdAndUser_Id(NOTIFICATION_ID, USER_ID)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> notificationService.markAsRead(NOTIFICATION_ID, USER_ID));
    }

    @Test
    void marksAllUnreadNotificationsAsReadAndReturnsUpdatedCount() {
        when(notificationRepository.markAllUnreadAsRead(USER_ID)).thenReturn(3);

        NotificationReadResponse response = notificationService.markAllAsRead(USER_ID);

        assertEquals(3, response.count());
        verify(notificationRepository).markAllUnreadAsRead(USER_ID);
    }

    private Notification notification(String id, Instant createdAt, boolean read, String taskId) {
        Notification notification = new Notification();
        notification.setId(id);
        notification.setCreatedAt(createdAt);
        notification.setRead(read);

        if (taskId != null) {
            Task task = new Task();
            task.setId(taskId);
            notification.setTask(task);
        }

        return notification;
    }
}
