package br.com.calendar.notification;

import br.com.calendar.common.exception.GlobalExceptionHandler;
import br.com.calendar.common.exception.ResourceNotFoundException;
import br.com.calendar.notification.dto.NotificationReadResponse;
import br.com.calendar.notification.dto.NotificationResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    private static final String USER_ID = "usr_abc123";
    private static final String NOTIFICATION_ID = "notification_123";

    @Mock
    private NotificationService notificationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new NotificationController(notificationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsAuthenticatedUserNotifications() throws Exception {
        when(notificationService.getNotifications(USER_ID)).thenReturn(List.of(
                new NotificationResponseDTO(
                        NOTIFICATION_ID,
                        "Task reminder",
                        Instant.parse("2026-10-01T20:00:00Z"),
                        NotificationType.notificacao,
                        false,
                        "task_123",
                        Instant.parse("2026-10-01T19:45:00Z"))));

        mockMvc.perform(get("/notifications")
                        .principal(new UsernamePasswordAuthenticationToken(USER_ID, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(NOTIFICATION_ID))
                .andExpect(jsonPath("$[0].read").value(false))
                .andExpect(jsonPath("$[0].taskId").value("task_123"));

        verify(notificationService).getNotifications(USER_ID);
    }

    @Test
    void returnsEmptyArrayWhenUserHasNoNotifications() throws Exception {
        when(notificationService.getNotifications(USER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/notifications")
                        .principal(new UsernamePasswordAuthenticationToken(USER_ID, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void returns401WhenGettingNotificationsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/notifications"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(notificationService);
    }

    @Test
    void marksOneNotificationAsRead() throws Exception {
        when(notificationService.markAsRead(NOTIFICATION_ID, USER_ID))
                .thenReturn(new NotificationReadResponse(1));

        mockMvc.perform(patch("/notifications/{id}", NOTIFICATION_ID)
                        .principal(new UsernamePasswordAuthenticationToken(USER_ID, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        verify(notificationService).markAsRead(NOTIFICATION_ID, USER_ID);
    }

    @Test
    void returns404WhenNotificationIsNotAccessible() throws Exception {
        when(notificationService.markAsRead(NOTIFICATION_ID, USER_ID))
                .thenThrow(new ResourceNotFoundException("Notification not found"));

        mockMvc.perform(patch("/notifications/{id}", NOTIFICATION_ID)
                        .principal(new UsernamePasswordAuthenticationToken(USER_ID, null)))
                .andExpect(status().isNotFound());
    }

    @Test
    void marksAllUserNotificationsAsRead() throws Exception {
        when(notificationService.markAllAsRead(USER_ID))
                .thenReturn(new NotificationReadResponse(3));

        mockMvc.perform(patch("/notifications/read-all")
                        .principal(new UsernamePasswordAuthenticationToken(USER_ID, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));

        verify(notificationService).markAllAsRead(USER_ID);
    }

    @Test
    void returns401WhenMarkingOneNotificationWithoutAuthentication() throws Exception {
        mockMvc.perform(patch("/notifications/{id}", NOTIFICATION_ID))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(notificationService);
    }

    @Test
    void returns401WhenMarkingAllNotificationsWithoutAuthentication() throws Exception {
        mockMvc.perform(patch("/notifications/read-all"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(notificationService);
    }
}
