package br.com.calendar.notification;

import br.com.calendar.notification.dto.NotificationReadResponse;
import br.com.calendar.notification.dto.NotificationResponseDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<List<NotificationResponseDTO>> getNotifications(Authentication authentication) {
        return ResponseEntity.ok(notificationService.getNotifications(authenticatedUserId(authentication)));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<NotificationReadResponse> markAllAsRead(Authentication authentication) {
        return ResponseEntity.ok(notificationService.markAllAsRead(authenticatedUserId(authentication)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<NotificationReadResponse> markAsRead(
            @PathVariable String id, Authentication authentication) {
        return ResponseEntity.ok(notificationService.markAsRead(id, authenticatedUserId(authentication)));
    }

    private String authenticatedUserId(Authentication authentication) {
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }

        return authentication.getName();
    }
}
