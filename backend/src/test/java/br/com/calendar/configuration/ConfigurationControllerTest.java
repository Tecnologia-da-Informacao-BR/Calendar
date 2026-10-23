package br.com.calendar.configuration;

import br.com.calendar.auth.JwtUtil;
import br.com.calendar.auth.TokenBlacklist;
import br.com.calendar.config.SecurityConfig;
import br.com.calendar.configuration.dto.ConfigurationResponseDTO;
import br.com.calendar.configuration.dto.UpdateConfigurationDTO;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@WebMvcTest(ConfigurationController.class)
@Import(SecurityConfig.class)
class ConfigurationControllerTest {

    private static final String USER_ID = "usr_abc123";
    private static final String TOKEN = "valid-token";

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private ConfigurationService configurationService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private TokenBlacklist tokenBlacklist;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters((Filter) context.getBean("springSecurityFilterChain"))
                .build();
    }

    @Test
    void returnsTheAuthenticatedUsersConfiguration() throws Exception {
        when(jwtUtil.extractUserId(TOKEN)).thenReturn(USER_ID);
        when(jwtUtil.isExpired(TOKEN)).thenReturn(false);
        when(tokenBlacklist.isRevoked(TOKEN)).thenReturn(false);

        ConfigurationResponseDTO expected = new ConfigurationResponseDTO(
                USER_ID,
                "pt-BR",
                Theme.light,
                TimeFormat.h24,
                (short) 1,
                DefaultView.month,
                null,
                null);

        when(configurationService.getDefaultConfiguration(USER_ID)).thenReturn(expected);

        mockMvc.perform(get("/configuration")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_ID))
                .andExpect(jsonPath("$.language").value("pt-BR"))
                .andExpect(jsonPath("$.theme").value("light"))
                .andExpect(jsonPath("$.timeFormat").value("h24"))
                .andExpect(jsonPath("$.weekStartDay").value(1));

        verify(configurationService).getDefaultConfiguration(USER_ID);
    }

    @Test
    void returns401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/configuration"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(configurationService);
    }

    @Test
    void updatesTheAuthenticatedUsersConfiguration() throws Exception {
        authenticate();
        ConfigurationResponseDTO updated = new ConfigurationResponseDTO(
                USER_ID,
                "en-US",
                Theme.dark,
                TimeFormat.h12,
                (short) 7,
                DefaultView.week,
                null,
                null);
        when(configurationService.updateConfiguration(eq(USER_ID), any(UpdateConfigurationDTO.class)))
                .thenReturn(updated);

        mockMvc.perform(patch("/configuration")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "language": "en-US",
                                  "theme": "dark",
                                  "timeFormat": "h12",
                                  "weekStartDay": 7,
                                  "defaultView": "week"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_ID))
                .andExpect(jsonPath("$.language").value("en-US"))
                .andExpect(jsonPath("$.theme").value("dark"))
                .andExpect(jsonPath("$.timeFormat").value("h12"))
                .andExpect(jsonPath("$.weekStartDay").value(7))
                .andExpect(jsonPath("$.defaultView").value("week"));

        verify(configurationService).updateConfiguration(eq(USER_ID), any(UpdateConfigurationDTO.class));
    }

    @Test
    void returns400ForInvalidConfigurationValues() throws Exception {
        authenticate();

        mockMvc.perform(patch("/configuration")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"theme": "blue"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(configurationService);
    }

    @Test
    void returns400ForOutOfRangeWeekStartDay() throws Exception {
        authenticate();

        mockMvc.perform(patch("/configuration")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"weekStartDay": 8}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid fields"))
                .andExpect(jsonPath("$.fields.weekStartDay").exists());

        verifyNoInteractions(configurationService);
    }

    @Test
    void returns401WhenPatchIsNotAuthenticated() throws Exception {
        mockMvc.perform(patch("/configuration")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"theme": "dark"}
                                """))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(configurationService);
    }

    private void authenticate() {
        when(jwtUtil.extractUserId(TOKEN)).thenReturn(USER_ID);
        when(jwtUtil.isExpired(TOKEN)).thenReturn(false);
        when(tokenBlacklist.isRevoked(TOKEN)).thenReturn(false);
    }
}
