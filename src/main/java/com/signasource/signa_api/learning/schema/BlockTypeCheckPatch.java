package com.signasource.signa_api.learning.schema;

import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Drops the generated CHECK constraint on {@code lesson_blocks.type}.
 *
 * <p>Hibernate writes that constraint as a literal list of the enum values it knew when the table
 * was created. {@code ddl-auto=update} creates constraints but never widens them, so every new
 * block type turns into a production incident: the enum compiles, the import runs, and Postgres
 * rejects the insert. The entity now declares its own {@code columnDefinition} so the constraint is
 * not generated again; this runner removes the one already out there.
 *
 * <p>Nothing is lost by dropping it. The column is written only through a Java enum and the content
 * validator checks the type before anything reaches the database.
 *
 * <p>Runs before {@code ContentImportRunner}, which is the thing that would hit the constraint.
 */
@Component
@Order(1)
public class BlockTypeCheckPatch implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BlockTypeCheckPatch.class);

    private static final String CONSTRAINT = "lesson_blocks_type_check";

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public BlockTypeCheckPatch(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!isPostgres()) {
            return;
        }
        try {
            jdbc.execute("ALTER TABLE lesson_blocks DROP CONSTRAINT IF EXISTS " + CONSTRAINT);
        } catch (RuntimeException e) {
            // A database that never had the table, or a role without DDL rights. Neither is worth
            // refusing to start over: the import that follows reports the real problem.
            log.warn("Could not drop {}: {}", CONSTRAINT, e.getMessage());
        }
    }

    private boolean isPostgres() {
        try (var connection = dataSource.getConnection()) {
            return "PostgreSQL".equals(connection.getMetaData().getDatabaseProductName());
        } catch (Exception e) {
            log.warn("Could not read database metadata: {}", e.getMessage());
            return false;
        }
    }
}
