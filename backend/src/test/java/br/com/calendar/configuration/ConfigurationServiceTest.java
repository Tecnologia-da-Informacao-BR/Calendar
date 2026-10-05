package br.com.calendar.configuration;

import br.com.calendar.configuration.dto.ConfigurationResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfigurationServiceTest {

    private static final String USER_ID = "usr_abc123";

    @Mock
    private ConfigurationRepository configurationRepository;

    @Mock
    private ConfigurationMapper configurationMapper;

    private ConfigurationService configurationService;

    @BeforeEach
    void setUp() {
        configurationService = new ConfigurationService(configurationRepository, configurationMapper);
    }

    @Test
    void returnsExistingConfigurationWhenItExists() {
        Configuration configuration = new Configuration();
        configuration.setId(USER_ID);
        configuration.setLanguage("en-US");
        configuration.setTheme(Theme.dark);

        ConfigurationResponseDTO expected = new ConfigurationResponseDTO(
                USER_ID,
                "en-US",
                Theme.dark,
                TimeFormat.h12,
                (short) 7,
                DefaultView.week,
                null,
                null);

        when(configurationRepository.findById(USER_ID)).thenReturn(Optional.of(configuration));
        when(configurationMapper.toResponse(configuration)).thenReturn(expected);

        ConfigurationResponseDTO response = configurationService.getDefaultConfiguration(USER_ID);

        assertEquals(expected, response);
        verify(configurationRepository).findById(USER_ID);
        verify(configurationMapper).toResponse(configuration);
        verify(configurationRepository, never()).save(any(Configuration.class));
    }

    @Test
    void createsDefaultConfigurationWhenNoneExists() {
        when(configurationRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(configurationRepository.save(any(Configuration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(configurationMapper.toResponse(any(Configuration.class)))
                .thenAnswer(invocation -> {
                    Configuration config = invocation.getArgument(0);
                    return new ConfigurationResponseDTO(
                            config.getId(),
                            config.getLanguage(),
                            config.getTheme(),
                            config.getTimeFormat(),
                            config.getWeekStartDay(),
                            config.getDefaultView(),
                            config.getCreatedAt(),
                            config.getUpdatedAt());
                });

        ConfigurationResponseDTO response = configurationService.getDefaultConfiguration(USER_ID);

        ArgumentCaptor<Configuration> savedConfig = ArgumentCaptor.forClass(Configuration.class);
        verify(configurationRepository).save(savedConfig.capture());
        Configuration capturedConfig = savedConfig.getValue();

        assertEquals(USER_ID, capturedConfig.getId());
        assertEquals("pt-BR", capturedConfig.getLanguage());
        assertEquals(Theme.light, capturedConfig.getTheme());
        assertEquals(TimeFormat.h24, capturedConfig.getTimeFormat());
        assertEquals((short) 1, capturedConfig.getWeekStartDay());
        assertEquals(DefaultView.month, capturedConfig.getDefaultView());

        assertEquals("pt-BR", response.language());
        assertEquals(Theme.light, response.theme());
        assertEquals(TimeFormat.h24, response.timeFormat());
        assertEquals((short) 1, response.weekStartDay());
        assertEquals(DefaultView.month, response.defaultView());
    }

    @Test
    void createDefaultConfigurationWithCorrectFields() {
        when(configurationRepository.save(any(Configuration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(configurationMapper.toResponse(any(Configuration.class)))
                .thenAnswer(invocation -> {
                    Configuration config = invocation.getArgument(0);
                    return new ConfigurationResponseDTO(
                            config.getId(),
                            config.getLanguage(),
                            config.getTheme(),
                            config.getTimeFormat(),
                            config.getWeekStartDay(),
                            config.getDefaultView(),
                            config.getCreatedAt(),
                            config.getUpdatedAt());
                });

        ConfigurationResponseDTO response = configurationService.createDefaultConfiguration(USER_ID);

        ArgumentCaptor<Configuration> savedConfig = ArgumentCaptor.forClass(Configuration.class);
        verify(configurationRepository).save(savedConfig.capture());
        Configuration capturedConfig = savedConfig.getValue();

        assertEquals(USER_ID, capturedConfig.getId());
        assertEquals("pt-BR", capturedConfig.getLanguage());
        assertEquals(Theme.light, capturedConfig.getTheme());
        assertEquals(TimeFormat.h24, capturedConfig.getTimeFormat());
        assertEquals((short) 1, capturedConfig.getWeekStartDay());
        assertEquals(DefaultView.month, capturedConfig.getDefaultView());

        assertEquals("pt-BR", response.language());
        assertEquals(Theme.light, response.theme());
        assertEquals(TimeFormat.h24, response.timeFormat());
        assertEquals((short) 1, response.weekStartDay());
        assertEquals(DefaultView.month, response.defaultView());
    }
}
