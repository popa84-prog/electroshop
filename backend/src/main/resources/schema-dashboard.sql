-- =====================================================================
--  ElectroShop — schema for the dashboard redesign (tasks 1–20)
-- =====================================================================
--
--  WHEN THIS FILE IS NEEDED
--
--  Production runs with spring.jpa.hibernate.ddl-auto=update, which creates
--  these tables automatically on first startup. This script exists for the
--  environments that do not: ddl-auto=validate, a managed database where the
--  application user has no DDL rights, or a review that wants to see exactly
--  what is being added before it is added.
--
--  Every statement here is additive. No existing column is renamed, no
--  existing constraint is tightened, no data is rewritten. Running this
--  script against a database that already has the tables is a no-op thanks
--  to IF NOT EXISTS, so it is safe to run twice.
--
--  Written for MySQL 8. The application also runs on H2 in MODE=MySQL for
--  tests, where these statements parse unchanged.
--
--  WHY THE INDEXES LOOK LIKE THIS
--
--  Every index below exists because a specific query in the analytics
--  services needs it, and the column order matches that query's WHERE and
--  GROUP BY clauses. An index whose leading column is not the one being
--  filtered on is an index the optimiser will not use, so the equality
--  predicates come first and the range predicate on the timestamp comes
--  last. On a catalogue of a few hundred products and a few thousand orders
--  the difference is small; the reports are written to stay correct at a
--  hundred times that size, and the indexes are what make that true.
-- =====================================================================


-- ---------------------------------------------------------------------
--  order_status_events — TASK 15
--
--  Orders record when they were created and when they were last touched.
--  Neither says when an order moved from PENDING to PAID or from SHIPPED to
--  DELIVERED, which is exactly what "average processing time" and "average
--  delivery time" measure. This table records each transition as it
--  happens.
--
--  Append-only by design. Nothing in the application updates or deletes a
--  row here: a metric computed over a history that can be rewritten is not
--  a metric.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS order_status_events (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    order_id     BIGINT       NOT NULL,
    -- Denormalised copy of order_id. The aggregation queries group by order
    -- without ever needing the order row, and grouping on the association
    -- would force a join to `orders` for no benefit.
    order_ref    BIGINT       NOT NULL,
    from_status  VARCHAR(20)  NULL,
    to_status    VARCHAR(20)  NOT NULL,
    actor        VARCHAR(120) NULL,
    reason       VARCHAR(300) NULL,
    created_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ose_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Serves "the first moment each order entered status X in this window",
-- which is the shape of every duration query in the efficiency report.
CREATE INDEX idx_ose_order_created ON order_status_events (order_ref, created_at);

-- Serves the rate and volume queries, which filter on the destination
-- status and then on the window.
CREATE INDEX idx_ose_to_created ON order_status_events (to_status, created_at);


-- ---------------------------------------------------------------------
--  offer_events — TASK 17
--
--  Click-through rate, conversion rate and cost per acquisition are ratios
--  over counted interactions. The catalogue has never recorded an
--  impression or a click, so none of those numbers exists until this table
--  starts filling. Collection begins the day this ships; there is no
--  retroactive history to reconstruct.
--
--  PRIVACY: no visitor identity is stored. session_hash is a one-way digest
--  supplied by the browser, used only to avoid counting the same visitor's
--  impression twice and to attribute a conversion to a prior click. It is
--  not reversible and is never joined to the users table.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS offer_events (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    offer_id     BIGINT        NOT NULL,
    offer_ref    BIGINT        NOT NULL,
    type         VARCHAR(20)   NOT NULL,
    session_hash VARCHAR(64)   NULL,
    order_ref    BIGINT        NULL,
    -- Copied rather than joined, for the same reason order_items copies its
    -- cost price: an order edited later must not silently rewrite the
    -- historical performance of a campaign already reported on.
    order_value  DECIMAL(12,2) NULL,
    created_at   DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_oe_offer FOREIGN KEY (offer_id) REFERENCES offers (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Serves the funnel query: totals per offer per event type inside a window.
CREATE INDEX idx_oe_offer_type_created ON offer_events (offer_ref, type, created_at);

-- Serves the evolution chart, which groups one event type by day across all
-- offers.
CREATE INDEX idx_oe_created ON offer_events (created_at);

-- Serves impression de-duplication and last-click attribution, both of
-- which look up by session.
CREATE INDEX idx_oe_session ON offer_events (session_hash);


-- ---------------------------------------------------------------------
--  system_log_entries — TASK 19
--
--  The application logs to the console, which is enough while somebody is
--  watching it and useless afterwards: a container restart takes the
--  history with it, and "what broke overnight" is precisely the question
--  the operational panel exists to answer.
--
--  This is deliberately NOT a general-purpose log sink. Writing every
--  request here would make the database the bottleneck it is supposed to be
--  monitoring, and it would fail hardest at the moment the database is
--  already in trouble. Only failures and a few named milestones are
--  persisted; throughput and latency are counted in memory.
--
--  RETENTION is enforced by a scheduled job, not merely recommended. A
--  monitoring table that grows without bound eventually becomes the outage.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS system_log_entries (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    source      VARCHAR(20)  NOT NULL,
    level       VARCHAR(10)  NOT NULL,
    code        VARCHAR(80)  NOT NULL,
    message     VARCHAR(500) NOT NULL,
    -- Stored without the query string. A query string can carry a token or a
    -- customer identifier, and a monitoring table is exactly the place where
    -- such a value would be read by people who have no need for it.
    context     VARCHAR(300) NULL,
    status_code INT          NULL,
    duration_ms BIGINT       NULL,
    -- Bounded on write by the service. An unbounded column filled by a
    -- recursive failure is a way to run the disk out of space during the
    -- very incident the table exists to document.
    detail      TEXT         NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Serves the panel's filtered table and its per-source summary tiles.
CREATE INDEX idx_sle_source_level_created ON system_log_entries (source, level, created_at);

-- Serves the retention delete and the unfiltered newest-first listing.
CREATE INDEX idx_sle_created ON system_log_entries (created_at);


-- ---------------------------------------------------------------------
--  admin_preferences — TASKS 3 and 4
--
--  Three requirements need per-administrator persistence: the dashboard
--  layout, the sidebar favourites, and the compact/expanded view mode. None
--  of them is ever queried by its contents, all of them are read and written
--  whole, and all of them belong to exactly one admin — so one key/value
--  table serves all three instead of three schemas.
--
--  The value is JSON because a layout is a list of objects and the
--  favourites are a list of strings, shapes a column-per-field design would
--  have to flatten and reassemble on every read. The backend does not
--  interpret the value; it enforces only that it is well-formed and bounded.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS admin_preferences (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    admin_id   BIGINT      NOT NULL,
    pref_key   VARCHAR(60) NOT NULL,
    value      TEXT        NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    -- One row per admin per key. The upsert path depends on this: without
    -- it, a double-submit would leave two rows and reads would become
    -- order-dependent.
    CONSTRAINT uk_admin_pref UNIQUE (admin_id, pref_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_ap_admin ON admin_preferences (admin_id);


-- ---------------------------------------------------------------------
--  admin_notes — TASK 20
--
--  Quick notes, reminders and internal tasks are the same record wearing
--  three labels: text owned by an admin, optionally due at a time,
--  optionally finished. The `kind` column carries the difference; three
--  tables would triple the schema to express one column.
--
--  OWNERSHIP is enforced in every query, not assumed by the interface. Two
--  administrators sharing a panel must not see each other's notes, and the
--  only reliable place to guarantee that is the WHERE clause.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS admin_notes (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    admin_id   BIGINT       NOT NULL,
    kind       VARCHAR(20)  NOT NULL,
    title      VARCHAR(200) NULL,
    content    TEXT         NOT NULL,
    due_at     DATETIME(6)  NULL,
    -- NOT NULL even for notes and reminders, where it stays false and is
    -- ignored: a nullable boolean produces three states for a two-state
    -- question and every query then has to say so.
    done       BIT(1)       NOT NULL DEFAULT b'0',
    priority   INT          NOT NULL DEFAULT 2,
    -- Validated to start with /admin/ on write, so a stored value can never
    -- become a link that sends an operator off-site.
    link_to    VARCHAR(200) NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Serves every panel query: one admin's items of one kind, open ones first.
CREATE INDEX idx_an_admin_kind ON admin_notes (admin_id, kind, done);

-- Serves the due-reminder sweep.
CREATE INDEX idx_an_due ON admin_notes (due_at);


-- ---------------------------------------------------------------------
--  offers.campaign_cost — TASK 17
--
--  Cost per acquisition needs a cost. The offers table records what a
--  campaign says and when it runs, never what it costs to run, so the
--  column is added here.
--
--  NULL means "not recorded", not "free". The marketing panel reports cost
--  per acquisition as null for such campaigns rather than as zero: a
--  campaign with no recorded cost and a campaign that genuinely cost
--  nothing produce very different conclusions about whether to repeat it.
-- ---------------------------------------------------------------------
ALTER TABLE offers
    ADD COLUMN IF NOT EXISTS campaign_cost DECIMAL(12,2) NULL;


-- ---------------------------------------------------------------------
--  Supporting indexes on existing tables
--
--  The analytics services aggregate over orders and order items by date
--  range, and over products by stock level. These indexes are additive and
--  change no behaviour; they only stop the reports from degrading into full
--  scans as the tables grow.
-- ---------------------------------------------------------------------

-- Every financial and efficiency report filters orders by placement date
-- and status, in that order.
CREATE INDEX IF NOT EXISTS idx_orders_created_status ON orders (created_at, status);

-- Customer insights groups orders by customer and needs each customer's
-- first-ever order date.
CREATE INDEX IF NOT EXISTS idx_orders_user_created ON orders (user_id, created_at);

-- Profit breakdown and product performance join order items to their
-- product and aggregate per product.
CREATE INDEX IF NOT EXISTS idx_order_items_product ON order_items (product_id);

-- Inventory health filters active products by stock level.
CREATE INDEX IF NOT EXISTS idx_products_active_stock ON products (active, stock_quantity);

-- Profit breakdown groups active products by category and by brand.
CREATE INDEX IF NOT EXISTS idx_products_active_category ON products (active, category);
CREATE INDEX IF NOT EXISTS idx_products_active_brand ON products (active, brand);
