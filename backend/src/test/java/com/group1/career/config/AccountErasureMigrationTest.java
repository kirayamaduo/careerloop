package com.group1.career.config;

import org.h2.tools.RunScript;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AccountErasureMigrationTest {

    @Test
    void v22RunsInMysqlCompatibilityModeAndMakesAuditIdentitiesNullable() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:v22_migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE", "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE account_deletion_log (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      user_id BIGINT NOT NULL,
                      ip_hash VARCHAR(64),
                      created_at DATETIME NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE admin_audit_log (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      admin_id BIGINT NOT NULL,
                      created_at DATETIME NOT NULL
                    )
                    """);

            var resource = AccountErasureMigrationTest.class.getResourceAsStream(
                    "/db/migration/V22__account_erasure_anonymous_audit.sql");
            assertNotNull(resource);
            RunScript.execute(
                    connection,
                    new InputStreamReader(resource, StandardCharsets.UTF_8));

            assertEquals("YES", nullable(statement, "account_deletion_log", "user_id"));
            assertEquals("YES", nullable(statement, "admin_audit_log", "admin_id"));
            assertEquals("NO", nullable(statement, "account_deletion_log", "status"));
            assertEquals("'PENDING'", defaultValue(
                    statement, "account_deletion_log", "status"));
        }
    }

    private String nullable(Statement statement, String table, String column) throws Exception {
        try (ResultSet rows = statement.executeQuery("""
                SELECT is_nullable
                FROM information_schema.columns
                WHERE table_name = '%s' AND column_name = '%s'
                """.formatted(table, column))) {
            rows.next();
            return rows.getString(1);
        }
    }

    private String defaultValue(Statement statement, String table, String column) throws Exception {
        try (ResultSet rows = statement.executeQuery("""
                SELECT column_default
                FROM information_schema.columns
                WHERE table_name = '%s' AND column_name = '%s'
                """.formatted(table, column))) {
            rows.next();
            return rows.getString(1);
        }
    }
}
