package br.com.calendar.configuration;

import br.com.calendar.configuration.dto.ConfigurationResponseDTO;
import br.com.calendar.configuration.dto.UpdateConfigurationDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Service
public class ConfigurationService {

    private static final String DEFAULT_LANGUAGE = "pt-BR";
    private static final Theme DEFAULT_THEME = Theme.light;
    private static final TimeFormat DEFAULT_TIME_FORMAT = TimeFormat.h24;
    private static final Short DEFAULT_WEEK_START_DAY = 1; // 1 = Monday (ISO 8601)
    private static final DefaultView DEFAULT_VIEW = DefaultView.month;

    private final ConfigurationRepository configurationRepository;
    private final ConfigurationMapper configurationMapper;

    public ConfigurationService(ConfigurationRepository configurationRepository,
                                 ConfigurationMapper configurationMapper) {
        this.configurationRepository = configurationRepository;
        this.configurationMapper = configurationMapper;
    }

    public ConfigurationResponseDTO createDefaultConfiguration(String userId) {
        Configuration saved = configurationRepository.save(defaultConfiguration(userId));
        return configurationMapper.toResponse(saved);
    }

    private Configuration defaultConfiguration(String userId) {
        Configuration configuration = new Configuration();
        configuration.setId(userId);
        configuration.setLanguage(DEFAULT_LANGUAGE);
        configuration.setTheme(DEFAULT_THEME);
        configuration.setTimeFormat(DEFAULT_TIME_FORMAT);
        configuration.setWeekStartDay(DEFAULT_WEEK_START_DAY);
        configuration.setDefaultView(DEFAULT_VIEW);
        return configuration;
    }

    public ConfigurationResponseDTO getDefaultConfiguration(String userId) {
        Optional<Configuration> configuration = configurationRepository.findById(userId);

        return configuration.map(configurationMapper::toResponse)
                .orElseGet(() -> createDefaultConfiguration(userId));
    }

    @Transactional
    public ConfigurationResponseDTO updateConfiguration(String userId, UpdateConfigurationDTO request) {
        Configuration configuration = configurationRepository.findById(userId)
                .orElseGet(() -> defaultConfiguration(userId));

        if (request.language() != null) {
            configuration.setLanguage(request.language());
        }
        if (request.theme() != null) {
            configuration.setTheme(request.theme());
        }
        if (request.timeFormat() != null) {
            configuration.setTimeFormat(request.timeFormat());
        }
        if (request.weekStartDay() != null) {
            configuration.setWeekStartDay(request.weekStartDay());
        }
        if (request.defaultView() != null) {
            configuration.setDefaultView(request.defaultView());
        }

        return configurationMapper.toResponse(configurationRepository.save(configuration));
    }
}
