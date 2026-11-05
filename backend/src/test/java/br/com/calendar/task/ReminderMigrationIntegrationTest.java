package br.com.calendar.task;

import java.sql.Connection;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@ActiveProfiles("test")
class ReminderMigrationIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void migrationsPreserveExistingConfigurationsAndTasks() throws Exception {
        String schema = "reminder_migration_"
                + UUID.randomUUID().toString().replace("-", "");

        try (Connection connection = dataSource.getConnection()) {
            String originalSchema = connection.getSchema();

            JdbcTemplate jdbc = new JdbcTemplate(
                    new SingleConnectionDataSource(connection, true)
            );

            try {
                jdbc.execute("CREATE SCHEMA " + schema);
                connection.setSchema(schema);

                migrateToVersion(jdbc.getDataSource(), schema, "13");

                jdbc.update("""
                        INSERT INTO users (id, name, email_confirmed)
                        VALUES ('migration-owner', 'Existing user', false)
                        """);

                jdbc.update("""
                        INSERT INTO configuration (user_id, language, theme)
                        VALUES ('migration-owner', 'en-US', 'dark')
                        """);

                jdbc.update("""
                        INSERT INTO task (id, user_id, title, starts_at)
                        VALUES (
                            'migration-task',
                            'migration-owner',
                            'Existing task',
                            TIMESTAMPTZ '2026-11-10T18:00:00Z'
                        )
                        """);

                migrateToVersion(jdbc.getDataSource(), schema, "15");

                Map<String, Object> configuration = jdbc.queryForMap("""
                        SELECT language, theme::text,
                               in_app_notifications_enabled,
                               email_notifications_enabled,
                               browser_push_notifications_enabled
                        FROM configuration
                        WHERE user_id = 'migration-owner'
                        """);

                assertEquals("en-US", configuration.get("language"));
                assertEquals("dark", configuration.get("theme"));
                assertEquals(
                        Boolean.TRUE,
                        configuration.get("in_app_notifications_enabled")
                );
                assertEquals(
                        Boolean.FALSE,
                        configuration.get("email_notifications_enabled")
                );
                assertEquals(
                        Boolean.FALSE,
                        configuration.get("browser_push_notifications_enabled")
                );

                Map<String, Object> task = jdbc.queryForMap("""
                        SELECT title, user_id, reminder_minutes_before
                        FROM task
                        WHERE id = 'migration-task'
                        """);

                assertEquals("Existing task", task.get("title"));
                assertEquals("migration-owner", task.get("user_id"));
                assertNull(task.get("reminder_minutes_before"));
            } finally {
                connection.setSchema(originalSchema);
                jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }

    private void migrateToVersion(
            DataSource migrationDataSource,
            String schema,
            String version) {
        Flyway.configure()
                .dataSource(migrationDataSource)
                .locations("classpath:db/migration")
                .schemas(schema)
                .defaultSchema(schema)
                .target(version)
                .load()
                .migrate();
    }
}