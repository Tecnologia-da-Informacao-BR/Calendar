package br.com.calendar.configuration;

import br.com.calendar.auth.JwtUtil;
import br.com.calendar.auth.TokenBlacklist;
import br.com.calendar.config.SecurityConfig;
import br.com.calendar.configuration.dto.ConfigurationResponseDTO;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
}
