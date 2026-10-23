package br.com.calendar.config;

import br.com.calendar.auth.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "management.endpoints.web.exposure.include=health,info,metrics",
        "management.endpoint.health.probes.enabled=true",
        // The test profile deliberately has no SMTP server.
        "management.health.mail.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ActuatorSecurityIntegrationTest {

    private static final String USER_ID = "actuator-user";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtUtil jwtUtil;

    private String token;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO users (id, name, email_confirmed) VALUES (?, ?, false)",
                USER_ID, "Actuator user");
        token = jwtUtil.generateToken(USER_ID);
    }

    @Test
    void healthIsPublicAndDoesNotExposeDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @ParameterizedTest
    @CsvSource({
            "/actuator, $._links.metrics.href",
            "/actuator/info, $.application",
            "/actuator/metrics, $.names",
            "/actuator/metrics/jvm.memory.used, $.measurements",
            "/actuator/health/liveness, $.status"
    })
    void exposedActuatorPathsRequireAndAcceptValidAccessToken(String path, String responseField) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath(responseField).exists());
    }

    @Test
    void invalidTokenDoesNotGrantActuatorAccess() throws Exception {
        mockMvc.perform(get("/actuator/metrics").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void scopedTokensDoNotGrantActuatorAccess() throws Exception {
        for (String scopedToken : new String[]{jwtUtil.generatePasswordResetToken(USER_ID),
                jwtUtil.generateEmailConfirmationToken(USER_ID)}) {
            mockMvc.perform(get("/actuator/metrics").header("Authorization", "Bearer " + scopedToken))
                    .andExpect(status().isUnauthorized());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/v3/api-docs", "/swagger-ui/index.html"})
    void apiDocumentationRemainsPublic(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk());
    }

    @Test
    void userRouteStillRequiresAndAcceptsAccessToken() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID));
    }

    @Test
    void logoutRemainsProtected() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isUnauthorized());
    }
}
