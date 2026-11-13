package br.com.calendar.task;

import br.com.calendar.auth.JwtUtil;
import org.apache.tomcat.util.http.parser.Authorization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class TaskDailyIntegrationTest {

    private static final String USER_ID = "upcoming-owner";
    private static final String OTHER_USER_ID = "upcoming-other";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private TaskRepository repository;

    private String token;
    private Instant referenceTime;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jdbc.update("INSERT INTO users (id, name, email_confirmed) VALUES (?, ?, false), (?, ?, false)",
                USER_ID, "Daily owner", OTHER_USER_ID, "Other user");
        token = jwtUtil.generateToken(USER_ID);
    }

    @Test
    void returnsWeeklyOccurrenceOnRequestedDay() throws Exception {
        jdbc.update("""
                INSERT INTO task (id, user_id, title, starts_at, ends_at, repeat_interval, repeat)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                "weekly-meeting",
                USER_ID,
                "weekly meeting",
                OffsetDateTime.parse("2026-11-02T18:00:00Z"),
                OffsetDateTime.parse("2026-11-02T18:30:00Z"),
                "weekly",
                1);
        mockMvc.perform(get("/tasks")
                .param("date", "2026-11-09")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].startsAt").value("2026-11-09T18:00:00Z"))
                .andExpect(jsonPath("$[0].endsAt").value("2026-11-09T18:30:00Z"));

    }

}
