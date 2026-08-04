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

        // Same grandfathering for the feature #6 security columns: MySQL's ADD COLUMN
        // on an existing non-empty table can leave these NULL for pre-existing rows even
        // though the Java field is a primitive with a default — best-effort, idempotent.
        safeExecute("UPDATE users SET failed_login_attempts = 0 WHERE failed_login_attempts IS NULL");
        safeExecute("UPDATE users SET token_version = 0 WHERE token_version IS NULL");
        safeExecute("UPDATE users SET two_factor_enabled = FALSE WHERE two_factor_enabled IS NULL");
        safeExecute("UPDATE login_events SET success = TRUE WHERE success IS NULL");

        // Force-delete-with-history-preservation feature: a permanently removed
        // product (ProductService#forceDeleteWithHistory) unlinks itself from every
        // order_items/purchase_items row that ever referenced it — product_id is set
        // to NULL on each — instead of the row being deleted, so accounting and
        // profit history survive intact. That requires product_id to actually accept
        // NULL at the database level; Hibernate's ddl-auto=update never relaxes an
        // existing NOT NULL constraint on its own, only ever adds new columns/tables,
        // so both foreign key columns are widened here explicitly. Re-applying the
        // same definition on every boot is a no-op once already nullable.
        safeExecute("ALTER TABLE order_items MODIFY COLUMN product_id BIGINT NULL");
        safeExecute("ALTER TABLE purchase_items MODIFY COLUMN product_id BIGINT NULL");
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
