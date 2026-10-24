package br.com.calendar.configuration;

import br.com.calendar.auth.JwtUtil;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ConfigurationIntegrationTest {

    private static final String USER_ID = "configuration-owner";
    private static final String OTHER_USER_ID = "configuration-other";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private EntityManager entityManager;

    private String token;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jdbc.update("INSERT INTO users (id, name, email_confirmed) VALUES (?, ?, false), (?, ?, false)",
                USER_ID, "Configuration owner", OTHER_USER_ID, "Other user");
        insertConfiguration(USER_ID);
        insertConfiguration(OTHER_USER_ID);
        token = jwtUtil.generateToken(USER_ID);
    }

    @Test
    void patchPersistsPartialUpdateForAuthenticatedUserAndSubsequentGet() throws Exception {
        mockMvc.perform(patch("/configuration")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"theme": "dark", "defaultView": "week"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("pt-BR"))
                .andExpect(jsonPath("$.theme").value("dark"))
                .andExpect(jsonPath("$.timeFormat").value("h24"))
                .andExpect(jsonPath("$.weekStartDay").value(1))
                .andExpect(jsonPath("$.defaultView").value("week"));

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/configuration")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("pt-BR"))
                .andExpect(jsonPath("$.theme").value("dark"))
                .andExpect(jsonPath("$.timeFormat").value("h24"))
                .andExpect(jsonPath("$.weekStartDay").value(1))
                .andExpect(jsonPath("$.defaultView").value("week"));

        assertEquals("dark", jdbc.queryForObject(
                "SELECT theme::text FROM configuration WHERE user_id = ?", String.class, USER_ID));
        assertEquals("light", jdbc.queryForObject(
                "SELECT theme::text FROM configuration WHERE user_id = ?", String.class, OTHER_USER_ID));
    }

    private void insertConfiguration(String userId) {
        jdbc.update("""
                INSERT INTO configuration
                    (user_id, language, theme, time_format, week_start_day, default_view)
                VALUES (?, 'pt-BR', 'light', 'h24', 1, 'month')
                """, userId);
    }
}
