package br.com.calendar.configuration;

import br.com.calendar.configuration.dto.ConfigurationResponseDTO;
import org.springframework.stereotype.Component;

@Component
public class ConfigurationMapper {

    public ConfigurationResponseDTO toResponse(Configuration configuration) {
        return new ConfigurationResponseDTO(
                configuration.getId(),
                configuration.getLanguage(),
                configuration.getTheme(),
                configuration.getTimeFormat(),
                configuration.getWeekStartDay(),
                configuration.getDefaultView(),
                configuration.isInAppNotificationsEnabled(),
                configuration.isEmailNotificationsEnabled(),
                configuration.isBrowserPushNotificationsEnabled(),
                configuration.getCreatedAt(),
                configuration.getUpdatedAt()
        );
    }
}