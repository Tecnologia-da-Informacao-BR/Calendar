package br.com.calendar.configuration.dto;

import br.com.calendar.configuration.DefaultView;
import br.com.calendar.configuration.Theme;
import br.com.calendar.configuration.TimeFormat;

import java.time.Instant;

public record ConfigurationResponseDTO(
        String userId,
        String language,
        Theme theme,
        TimeFormat timeFormat,
        Short weekStartDay,
        DefaultView defaultView,
        boolean inAppNotificationsEnabled,
        boolean emailNotificationsEnabled,
        boolean browserPushNotificationsEnabled,
        Instant createdAt,
        Instant updatedAt) {
}