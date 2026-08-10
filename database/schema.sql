-- ============================================================
--  ElectroShop — Complete MySQL 8 schema (current)
--  Generated to match the JPA entities exactly. The backend also
--  auto-creates/updates this schema on boot (spring.jpa.hibernate.ddl-auto=update),
--  so loading this file is optional — it exists as a portable, standalone
--  backup and for environments where you want the schema created up-front.
--
--  Tables:
--    roles, users, user_roles (N:M),
--    products, product_images (1:N),
--    orders (1:N users), order_items (1:N orders, N:1 products),
--    suppliers, purchases (1:N suppliers), purchase_items (1:N purchases),
--    audit_logs, company_settings
-- ============================================================

CREATE DATABASE IF NOT EXISTS electroshop
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE electroshop;

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS purchase_items;
DROP TABLE IF EXISTS purchases;
DROP TABLE IF EXISTS suppliers;
DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS product_images;
DROP TABLE IF EXISTS user_roles;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS roles;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS audit_logs;
DROP TABLE IF EXISTS company_settings;

SET FOREIGN_KEY_CHECKS = 1;

-- ------------------------------------------------------------
-- roles
-- ------------------------------------------------------------
CREATE TABLE roles (
    id   BIGINT      NOT NULL AUTO_INCREMENT,
    name VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_name (name)
) ENGINE = InnoDB;

-- ------------------------------------------------------------
-- users
-- ------------------------------------------------------------
CREATE TABLE users (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    full_name  VARCHAR(100) NOT NULL,
    email      VARCHAR(150) NOT NULL,
    password   VARCHAR(255) NOT NULL,
    enabled    TINYINT(1)   NOT NULL DEFAULT 1,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_email (email)
) ENGINE = InnoDB;

-- ------------------------------------------------------------
-- user_roles (N:M join table)
-- ------------------------------------------------------------
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
) ENGINE = InnoDB;

-- ------------------------------------------------------------
-- products
--   name widened to 300 (long real-world product titles)
--   + subcategory, purchase_price (admin-only), sku
-- ------------------------------------------------------------
CREATE TABLE products (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    name           VARCHAR(300)  NOT NULL,
    description    TEXT          NULL,
    price          DECIMAL(12,2) NOT NULL,
    stock_quantity INT           NOT NULL DEFAULT 0,
    category       VARCHAR(80)   NULL,
    subcategory    VARCHAR(80)   NULL,
    brand          VARCHAR(80)   NULL,
    purchase_price DECIMAL(12,2) NULL,
    sku            VARCHAR(60)   NULL,
    image_url      VARCHAR(500)  NULL,
    created_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)   NULL,
    PRIMARY KEY (id),
    KEY idx_product_category (category),
    KEY idx_product_brand (brand)
) ENGINE = InnoDB;

-- ------------------------------------------------------------
-- product_images (1:N with products) — Cloudinary-hosted gallery
-- ------------------------------------------------------------
CREATE TABLE product_images (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    product_id BIGINT       NOT NULL,
    url        VARCHAR(500) NOT NULL,
    public_id  VARCHAR(200) NULL,
    is_primary TINYINT(1)   NOT NULL DEFAULT 0,
    position   INT          NOT NULL DEFAULT 0,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_prodimg_product (product_id),
    CONSTRAINT fk_prodimg_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
) ENGINE = InnoDB;

-- ------------------------------------------------------------
-- orders (1:N with users)
-- ------------------------------------------------------------
CREATE TABLE orders (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    user_id          BIGINT        NOT NULL,
    status           VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    total_amount     DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    shipping_address VARCHAR(300)  NULL,
    created_at       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)   NULL,
    PRIMARY KEY (id),
    KEY idx_order_user (user_id),
    KEY idx_order_status (status),
    CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;

-- ------------------------------------------------------------
-- order_items (1:N with orders, N:1 with products)
-- ------------------------------------------------------------
CREATE TABLE order_items (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    order_id   BIGINT        NOT NULL,
    product_id BIGINT        NOT NULL,
    quantity   INT           NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_orderitem_order (order_id),
    KEY idx_orderitem_product (product_id),
    CONSTRAINT fk_orderitem_order   FOREIGN KEY (order_id)   REFERENCES orders (id)   ON DELETE CASCADE,
    CONSTRAINT fk_orderitem_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE RESTRICT
) ENGINE = InnoDB;

-- ------------------------------------------------------------
-- suppliers (furnizori)
-- ------------------------------------------------------------
CREATE TABLE suppliers (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    name         VARCHAR(150) NOT NULL,
    contact_name VARCHAR(120) NULL,
    email        VARCHAR(150) NULL,
    phone        VARCHAR(40)  NULL,
    address      VARCHAR(300) NULL,
    tax_id       VARCHAR(40)  NULL,
    notes        TEXT         NULL,
    created_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id)
) ENGINE = InnoDB;

-- ------------------------------------------------------------
-- purchases (intrări marfă, 1:N cu suppliers)
-- ------------------------------------------------------------
CREATE TABLE purchases (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    supplier_id    BIGINT        NOT NULL,
    purchase_date  DATE          NOT NULL,
    invoice_number VARCHAR(60)   NULL,
    total_amount   DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    notes          TEXT          NULL,
    created_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_purchase_supplier (supplier_id),
    CONSTRAINT fk_purchase_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id) ON DELETE RESTRICT
) ENGINE = InnoDB;

-- ------------------------------------------------------------
-- purchase_items (1:N cu purchases, N:1 cu products)
-- ------------------------------------------------------------
CREATE TABLE purchase_items (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    purchase_id          BIGINT        NOT NULL,
    product_id           BIGINT        NOT NULL,
    quantity             INT           NOT NULL,
    unit_purchase_price  DECIMAL(12,2) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_purchaseitem_purchase (purchase_id),
    KEY idx_purchaseitem_product (product_id),
    CONSTRAINT fk_purchaseitem_purchase FOREIGN KEY (purchase_id) REFERENCES purchases (id) ON DELETE CASCADE,
    CONSTRAINT fk_purchaseitem_product  FOREIGN KEY (product_id)  REFERENCES products (id)  ON DELETE RESTRICT
) ENGINE = InnoDB;

-- ------------------------------------------------------------
-- audit_logs (jurnal de activitate)
-- ------------------------------------------------------------
CREATE TABLE audit_logs (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    actor       VARCHAR(120) NULL,
    action      VARCHAR(60)  NULL,
    entity_type VARCHAR(60)  NULL,
    entity_id   BIGINT       NULL,
    details     VARCHAR(500) NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id)
) ENGINE = InnoDB;

-- ------------------------------------------------------------
-- company_settings (date firmă / facturare — un singur rând)
-- ------------------------------------------------------------
CREATE TABLE company_settings (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    legal_name           VARCHAR(200) NULL,
    cui                  VARCHAR(40)  NULL,
    reg_com              VARCHAR(60)  NULL,
    address              VARCHAR(250) NULL,
    city                 VARCHAR(100) NULL,
    county               VARCHAR(100) NULL,
    country              VARCHAR(80)  NULL,
    postal_code          VARCHAR(20)  NULL,
    iban                 VARCHAR(40)  NULL,
    bank_name            VARCHAR(120) NULL,
    phone                VARCHAR(40)  NULL,
    email                VARCHAR(120) NULL,
    website              VARCHAR(120) NULL,
    vat_payer            TINYINT(1)   NOT NULL DEFAULT 1,
    vat_rate             DECIMAL(5,2) NULL,
    invoice_series       VARCHAR(12)  NULL,
    invoice_next_number  INT          NOT NULL DEFAULT 1,
    logo_url             VARCHAR(500) NULL,
    invoice_notes        TEXT         NULL,
    updated_at           DATETIME(6)  NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB;
