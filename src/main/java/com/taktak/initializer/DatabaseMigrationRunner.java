package com.taktak.initializer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.sql.Statement;

@Component
@ConditionalOnProperty(
        name = "taktak.migrations.run-on-startup",
        havingValue = "true",
        matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
@Order(20)
public class DatabaseMigrationRunner implements ApplicationRunner {

    private static final String MIGRATION_ID = "20260823_monastir_full_catalog";
    private static final String MIGRATION_RESOURCE =
            "db/migration/20260823_monastir_full_catalog.sql";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS taktak_schema_migrations (
                    migration_id VARCHAR(100) PRIMARY KEY,
                    applied_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);

        Integer alreadyApplied = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM taktak_schema_migrations WHERE migration_id = ?",
                Integer.class,
                MIGRATION_ID
        );
        if (alreadyApplied != null && alreadyApplied > 0) {
            return;
        }

        ClassPathResource resource = new ClassPathResource(MIGRATION_RESOURCE);
        String sql = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
            return null;
        });

        jdbcTemplate.update(
                "INSERT INTO taktak_schema_migrations (migration_id) VALUES (?) ON CONFLICT DO NOTHING",
                MIGRATION_ID
        );
        log.info("Migration {} appliquée", MIGRATION_ID);
    }
}
