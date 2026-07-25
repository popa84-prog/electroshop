package com.electroshop.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies small, idempotent schema tweaks that Hibernate's {@code ddl-auto=update}
 * does NOT perform on an already-existing table (notably: widening an existing
 * column). Runs before {@link DataInitializer} so the schema is correct before any
 * seeding or import happens.
 *
 * <p>Real product names can be long (observed up to ~240 characters), while the
 * original mapping was {@code VARCHAR(150)}, which caused "Data too long for column
 * 'name'" failures during the Excel import. This widens it to 300. Each statement is
 * best-effort and wrapped so a failure never blocks application startup.</p>
 */
@Component
@Order(0)
public class SchemaFixer implements CommandLineRunner {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void run(String... args) {
        // Widen products.name so long real-world product titles import cleanly.
        // Re-applying the same definition is a cheap no-op on subsequent boots.
        safeExecute("ALTER TABLE products MODIFY COLUMN name VARCHAR(300) NOT NULL");

        // Grandfather existing accounts when the new 'approved' column is first added:
        // Hibernate adds it as NULL for pre-existing rows, so mark those as approved
        // to avoid locking out anyone who registered before the approval feature.
        // Idempotent: new pending accounts store approved = 0 (not NULL) and are untouched.
        safeExecute("UPDATE users SET approved = TRUE WHERE approved IS NULL");
    }

    private void safeExecute(String sql) {
        try {
            entityManager.createNativeQuery(sql).executeUpdate();
        } catch (Exception ignored) {
            // Table may not exist yet on a brand-new database, or the underlying
            // engine may not support this exact DDL syntax — either way, skip it.
        }
    }
}
