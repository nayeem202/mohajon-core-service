-- =====================================================================
-- Haalkhata service - initial schema (schema: hk)
-- Auth + all business tables migrated from Firebase/Firestore
-- =====================================================================

-- ---------------------------------------------------------------------
-- AUTH DOMAIN
-- ---------------------------------------------------------------------

CREATE TABLE users (
    id               VARCHAR(36) PRIMARY KEY,
    name             VARCHAR(150) NOT NULL,
    email            VARCHAR(255) UNIQUE,
    phone            VARCHAR(30),
    password_hash    VARCHAR(255),
    photo_url        VARCHAR(500),
    email_verified   BOOLEAN NOT NULL DEFAULT FALSE,
    is_new_user      BOOLEAN NOT NULL DEFAULT TRUE,
    provider         VARCHAR(30) NOT NULL DEFAULT 'local',
    shop_id          VARCHAR(60),
    role             VARCHAR(30) NOT NULL DEFAULT 'STAFF',
    created_at       TIMESTAMP NOT NULL,
    updated_at       TIMESTAMP NOT NULL
);
CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_phone ON users (phone);
CREATE INDEX idx_users_shop ON users (shop_id);

CREATE TABLE roles (
    id         VARCHAR(36) PRIMARY KEY,
    code       VARCHAR(50) NOT NULL UNIQUE,
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE permissions (
    id         VARCHAR(36) PRIMARY KEY,
    code       VARCHAR(100) NOT NULL UNIQUE,
    name       VARCHAR(150) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE user_roles (
    id      VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id VARCHAR(36) NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT uq_user_role UNIQUE (user_id, role_id)
);

CREATE TABLE role_permissions (
    id            VARCHAR(36) PRIMARY KEY,
    role_id       VARCHAR(36) NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission_id VARCHAR(36) NOT NULL REFERENCES permissions (id) ON DELETE CASCADE,
    CONSTRAINT uq_role_permission UNIQUE (role_id, permission_id)
);

CREATE TABLE refresh_tokens (
    id         VARCHAR(36) PRIMARY KEY,
    user_id    VARCHAR(36) NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token      VARCHAR(500) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens (token);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

CREATE TABLE login_sessions (
    id             VARCHAR(36) PRIMARY KEY,
    user_id        VARCHAR(36) NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    device_id      VARCHAR(150),
    device_name    VARCHAR(150),
    os_version     VARCHAR(50),
    manufacturer   VARCHAR(100),
    model          VARCHAR(100),
    location_label VARCHAR(200),
    last_login_at  TIMESTAMP NOT NULL,
    created_at     TIMESTAMP NOT NULL
);
CREATE INDEX idx_login_sessions_user ON login_sessions (user_id, last_login_at DESC);

CREATE TABLE email_verifications (
    id         VARCHAR(36) PRIMARY KEY,
    email      VARCHAR(255) NOT NULL,
    code       VARCHAR(10) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    verified   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_email_verifications_email ON email_verifications (email);

CREATE TABLE otp_codes (
    id         VARCHAR(36) PRIMARY KEY,
    phone      VARCHAR(30) NOT NULL,
    code       VARCHAR(10) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    verified   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_otp_codes_phone ON otp_codes (phone);

CREATE TABLE pending_registrations (
    id           VARCHAR(36) PRIMARY KEY,
    user_id      VARCHAR(36) NOT NULL,
    shop_name    VARCHAR(150),
    owner_name   VARCHAR(150),
    email        VARCHAR(255),
    phone_number VARCHAR(30),
    created_at   TIMESTAMP NOT NULL,
    updated_at   TIMESTAMP NOT NULL
);

CREATE TABLE registrations (
    id           VARCHAR(36) PRIMARY KEY,
    user_id      VARCHAR(36) NOT NULL,
    shop_id      VARCHAR(60) NOT NULL,
    email        VARCHAR(255),
    name         VARCHAR(150),
    phone_number VARCHAR(30),
    photo_url    VARCHAR(500),
    created_at   TIMESTAMP NOT NULL,
    updated_at   TIMESTAMP NOT NULL
);

-- ---------------------------------------------------------------------
-- SHOP DOMAIN
-- ---------------------------------------------------------------------

CREATE TABLE shops (
    shop_id          VARCHAR(60) PRIMARY KEY,
    owner_user_id    VARCHAR(36) NOT NULL,
    name             VARCHAR(150) NOT NULL,
    location         VARCHAR(255),
    phone_number     VARCHAR(30),
    owner_count      INT NOT NULL DEFAULT 1,
    total_investment DOUBLE PRECISION NOT NULL DEFAULT 0,
    created_at       TIMESTAMP NOT NULL,
    updated_at       TIMESTAMP NOT NULL
);
CREATE INDEX idx_shops_owner ON shops (owner_user_id);

CREATE TABLE shop_owners (
    id                 VARCHAR(36) PRIMARY KEY,
    shop_id            VARCHAR(60) NOT NULL REFERENCES shops (shop_id) ON DELETE CASCADE,
    owner_id           VARCHAR(36),
    name               VARCHAR(150),
    investment_amount  DOUBLE PRECISION NOT NULL DEFAULT 0
);
CREATE INDEX idx_shop_owners_shop ON shop_owners (shop_id);

-- ---------------------------------------------------------------------
-- PAYMENT METHODS (configurable at runtime)
-- ---------------------------------------------------------------------

CREATE TABLE payment_methods (
    code               VARCHAR(30) PRIMARY KEY,
    name               VARCHAR(100) NOT NULL,
    label              VARCHAR(100),
    enabled            BOOLEAN NOT NULL DEFAULT TRUE,
    requires_reference BOOLEAN NOT NULL DEFAULT FALSE,
    fee_percent        DOUBLE PRECISION NOT NULL DEFAULT 0,
    sort_order         INT NOT NULL DEFAULT 0,
    config             TEXT,
    created_at         TIMESTAMP NOT NULL,
    updated_at         TIMESTAMP NOT NULL
);

-- ---------------------------------------------------------------------
-- BUSINESS DOMAIN
-- ---------------------------------------------------------------------

CREATE TABLE products (
    product_id      VARCHAR(36) PRIMARY KEY,
    shop_id         VARCHAR(60) NOT NULL,
    name_en         VARCHAR(200) NOT NULL,
    name_bn         VARCHAR(200),
    barcode         VARCHAR(100),
    category        VARCHAR(100),
    unit            VARCHAR(30),
    image_path      VARCHAR(500),
    buying_price    DOUBLE PRECISION NOT NULL DEFAULT 0,
    purchase_price  DOUBLE PRECISION NOT NULL DEFAULT 0,
    selling_price   DOUBLE PRECISION NOT NULL DEFAULT 0,
    stock_qty       INT NOT NULL DEFAULT 0,
    low_stock_alert INT NOT NULL DEFAULT 0,
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at      TIMESTAMP NOT NULL
);
CREATE INDEX idx_products_shop ON products (shop_id, updated_at);
CREATE INDEX idx_products_shop_deleted ON products (shop_id, is_deleted);
CREATE INDEX idx_products_barcode ON products (barcode);

CREATE TABLE customers (
    customer_id VARCHAR(36) PRIMARY KEY,
    shop_id     VARCHAR(60) NOT NULL,
    name        VARCHAR(200) NOT NULL,
    phone       VARCHAR(30),
    email       VARCHAR(255),
    address     TEXT,
    notes       TEXT,
    image_path  VARCHAR(500),
    total_spent DOUBLE PRECISION NOT NULL DEFAULT 0,
    tag         VARCHAR(50),
    is_deleted  BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at  TIMESTAMP NOT NULL
);
CREATE INDEX idx_customers_shop ON customers (shop_id, updated_at);
CREATE INDEX idx_customers_shop_deleted ON customers (shop_id, is_deleted);

CREATE TABLE customer_ledger_entries (
    entry_id    VARCHAR(36) PRIMARY KEY,
    shop_id     VARCHAR(60) NOT NULL,
    customer_id VARCHAR(36) NOT NULL,
    type        VARCHAR(10) NOT NULL,
    amount      DOUBLE PRECISION NOT NULL,
    note        TEXT,
    created_at  TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP NOT NULL,
    is_deleted  BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_ledger_customer ON customer_ledger_entries (shop_id, customer_id, created_at);

CREATE TABLE sales (
    sale_id        VARCHAR(36) PRIMARY KEY,
    shop_id        VARCHAR(60) NOT NULL,
    customer_id    VARCHAR(36) NOT NULL,
    discount       DOUBLE PRECISION NOT NULL DEFAULT 0,
    vat_percent    DOUBLE PRECISION NOT NULL DEFAULT 0,
    charge         DOUBLE PRECISION NOT NULL DEFAULT 0,
    total_amount   DOUBLE PRECISION NOT NULL,
    paid_amount    DOUBLE PRECISION NOT NULL DEFAULT 0,
    due_amount     DOUBLE PRECISION NOT NULL DEFAULT 0,
    payment_status VARCHAR(10) NOT NULL DEFAULT 'DUE',
    created_at     TIMESTAMP NOT NULL,
    updated_at     TIMESTAMP NOT NULL,
    is_deleted     BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_sales_shop ON sales (shop_id, created_at);
CREATE INDEX idx_sales_shop_updated ON sales (shop_id, updated_at);
CREATE INDEX idx_sales_customer ON sales (customer_id);

CREATE TABLE sale_items (
    id             VARCHAR(36) PRIMARY KEY,
    sale_id        VARCHAR(36) NOT NULL REFERENCES sales (sale_id) ON DELETE CASCADE,
    product_id     VARCHAR(36),
    name           VARCHAR(200) NOT NULL,
    qty            DOUBLE PRECISION NOT NULL,
    unit_price     DOUBLE PRECISION NOT NULL,
    purchase_price DOUBLE PRECISION NOT NULL DEFAULT 0
);
CREATE INDEX idx_sale_items_sale ON sale_items (sale_id);

CREATE TABLE payments (
    payment_id     VARCHAR(36) PRIMARY KEY,
    shop_id        VARCHAR(60) NOT NULL,
    customer_id    VARCHAR(36) NOT NULL,
    sale_id        VARCHAR(36),
    amount         DOUBLE PRECISION NOT NULL,
    payment_method VARCHAR(30) NOT NULL,
    reference      VARCHAR(200),
    created_at     TIMESTAMP NOT NULL,
    updated_at     TIMESTAMP NOT NULL,
    is_deleted     BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_payments_shop_customer ON payments (shop_id, customer_id);
CREATE INDEX idx_payments_sale ON payments (sale_id);
CREATE INDEX idx_payments_shop_updated ON payments (shop_id, updated_at);

CREATE TABLE suppliers (
    supplier_id VARCHAR(36) PRIMARY KEY,
    shop_id     VARCHAR(60) NOT NULL,
    name        VARCHAR(200) NOT NULL,
    phone       VARCHAR(30),
    is_deleted  BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at  TIMESTAMP NOT NULL
);
CREATE INDEX idx_suppliers_shop ON suppliers (shop_id, updated_at);

CREATE TABLE purchases (
    purchase_id  VARCHAR(36) PRIMARY KEY,
    shop_id      VARCHAR(60) NOT NULL,
    supplier_id  VARCHAR(36) NOT NULL,
    total_amount DOUBLE PRECISION NOT NULL,
    paid_amount  DOUBLE PRECISION NOT NULL DEFAULT 0,
    created_at   TIMESTAMP NOT NULL,
    updated_at   TIMESTAMP NOT NULL,
    is_deleted   BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_purchases_shop ON purchases (shop_id, created_at);
CREATE INDEX idx_purchases_shop_updated ON purchases (shop_id, updated_at);
CREATE INDEX idx_purchases_supplier ON purchases (supplier_id);

CREATE TABLE purchase_items (
    id          VARCHAR(36) PRIMARY KEY,
    purchase_id VARCHAR(36) NOT NULL REFERENCES purchases (purchase_id) ON DELETE CASCADE,
    product_id  VARCHAR(36),
    name        VARCHAR(200) NOT NULL,
    qty         DOUBLE PRECISION NOT NULL,
    unit_cost   DOUBLE PRECISION NOT NULL
);
CREATE INDEX idx_purchase_items_purchase ON purchase_items (purchase_id);

CREATE TABLE expenses (
    expense_id     VARCHAR(36) PRIMARY KEY,
    shop_id        VARCHAR(60) NOT NULL,
    description    VARCHAR(300) NOT NULL,
    amount         DOUBLE PRECISION NOT NULL,
    category_id    VARCHAR(100),
    category_label VARCHAR(150),
    date           TIMESTAMP NOT NULL,
    notes          TEXT,
    is_deleted     BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at     TIMESTAMP NOT NULL
);
CREATE INDEX idx_expenses_shop ON expenses (shop_id, date);
CREATE INDEX idx_expenses_shop_updated ON expenses (shop_id, updated_at);

CREATE TABLE vendors (
    vendor_id          VARCHAR(36) PRIMARY KEY,
    shop_id            VARCHAR(60) NOT NULL,
    company_name       VARCHAR(200) NOT NULL,
    contact_person     VARCHAR(150),
    phone              VARCHAR(30),
    email              VARCHAR(255),
    category           VARCHAR(100),
    address            TEXT,
    products           TEXT,
    rating             DOUBLE PRECISION DEFAULT 0,
    total_orders       INT NOT NULL DEFAULT 0,
    total_spent        DOUBLE PRECISION NOT NULL DEFAULT 0,
    pending_amount     DOUBLE PRECISION NOT NULL DEFAULT 0,
    last_purchase_date TIMESTAMP,
    status             VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    is_deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMP NOT NULL,
    updated_at         TIMESTAMP NOT NULL
);
CREATE INDEX idx_vendors_shop ON vendors (shop_id, updated_at);

CREATE TABLE vendor_purchases (
    purchase_id    VARCHAR(36) PRIMARY KEY,
    shop_id        VARCHAR(60) NOT NULL,
    vendor_id      VARCHAR(36) NOT NULL,
    amount         DOUBLE PRECISION NOT NULL,
    date           TIMESTAMP NOT NULL,
    status         VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    notes          TEXT,
    items_supplied TEXT,
    is_deleted     BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at     TIMESTAMP NOT NULL
);
CREATE INDEX idx_vendor_purchases_shop ON vendor_purchases (shop_id, vendor_id, date);

CREATE TABLE vendor_payments (
    payment_id VARCHAR(36) PRIMARY KEY,
    shop_id    VARCHAR(60) NOT NULL,
    vendor_id  VARCHAR(36) NOT NULL,
    amount     DOUBLE PRECISION NOT NULL,
    date       TIMESTAMP NOT NULL,
    method     VARCHAR(30),
    reference  VARCHAR(200),
    notes      TEXT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_vendor_payments_shop ON vendor_payments (shop_id, vendor_id, date);

CREATE TABLE investors (
    investor_id       VARCHAR(36) PRIMARY KEY,
    shop_id           VARCHAR(60) NOT NULL,
    name              VARCHAR(200) NOT NULL,
    phone             VARCHAR(30),
    email             VARCHAR(255),
    address           TEXT,
    photo_url         VARCHAR(500),
    notes             TEXT,
    total_investment  DOUBLE PRECISION NOT NULL DEFAULT 0,
    total_profit      DOUBLE PRECISION NOT NULL DEFAULT 0,
    total_loss        DOUBLE PRECISION NOT NULL DEFAULT 0,
    outstanding       DOUBLE PRECISION NOT NULL DEFAULT 0,
    investment_count  INT NOT NULL DEFAULT 0,
    created_at        TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP NOT NULL
);
CREATE INDEX idx_investors_shop ON investors (shop_id, created_at);

CREATE TABLE investment_categories (
    id         VARCHAR(36) PRIMARY KEY,
    shop_id    VARCHAR(60) NOT NULL,
    name       VARCHAR(150) NOT NULL,
    icon       VARCHAR(50),
    color      VARCHAR(20),
    created_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_investment_categories_shop ON investment_categories (shop_id);

CREATE TABLE investments (
    investment_id      VARCHAR(36) PRIMARY KEY,
    shop_id            VARCHAR(60) NOT NULL,
    title              VARCHAR(200) NOT NULL,
    description        TEXT,
    category_id        VARCHAR(36),
    category_name      VARCHAR(150),
    total_investment   DOUBLE PRECISION NOT NULL DEFAULT 0,
    profit_share_method VARCHAR(20) NOT NULL DEFAULT 'proportional',
    notes              TEXT,
    created_by         VARCHAR(36),
    status             VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    roi                DOUBLE PRECISION NOT NULL DEFAULT 0,
    total_profit       DOUBLE PRECISION NOT NULL DEFAULT 0,
    total_paid         DOUBLE PRECISION NOT NULL DEFAULT 0,
    outstanding        DOUBLE PRECISION NOT NULL DEFAULT 0,
    is_deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMP NOT NULL,
    updated_at         TIMESTAMP NOT NULL
);
CREATE INDEX idx_investments_shop ON investments (shop_id, created_at);
CREATE INDEX idx_investments_shop_updated ON investments (shop_id, updated_at);

CREATE TABLE investment_members (
    id                VARCHAR(36) PRIMARY KEY,
    investment_id     VARCHAR(36) NOT NULL REFERENCES investments (investment_id) ON DELETE CASCADE,
    investor_id       VARCHAR(36) NOT NULL,
    shop_id           VARCHAR(60) NOT NULL,
    investment_amount DOUBLE PRECISION NOT NULL,
    share_percentage  DOUBLE PRECISION NOT NULL DEFAULT 0,
    profit            DOUBLE PRECISION NOT NULL DEFAULT 0,
    loss              DOUBLE PRECISION NOT NULL DEFAULT 0,
    paid_amount       DOUBLE PRECISION NOT NULL DEFAULT 0,
    remaining_amount  DOUBLE PRECISION NOT NULL DEFAULT 0,
    created_at        TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP NOT NULL
);
CREATE INDEX idx_members_investment ON investment_members (investment_id);
CREATE INDEX idx_members_investor ON investment_members (investor_id);

CREATE TABLE payouts (
    payout_id     VARCHAR(36) PRIMARY KEY,
    investment_id VARCHAR(36) NOT NULL,
    investor_id   VARCHAR(36) NOT NULL,
    shop_id       VARCHAR(60) NOT NULL,
    amount        DOUBLE PRECISION NOT NULL,
    note          TEXT,
    created_by    VARCHAR(36),
    created_at    TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP NOT NULL
);
CREATE INDEX idx_payouts_investment ON payouts (investment_id, created_at DESC);
CREATE INDEX idx_payouts_investor ON payouts (investor_id, created_at DESC);

-- ---------------------------------------------------------------------
-- SEED DATA
-- ---------------------------------------------------------------------

INSERT INTO roles (id, code, name, created_at) VALUES
    ('11111111-1111-1111-1111-111111111111', 'OWNER', 'Shop Owner', NOW()),
    ('22222222-2222-2222-2222-222222222222', 'STAFF', 'Staff Member', NOW());

INSERT INTO permissions (id, code, name, created_at) VALUES
    ('a0000000-0000-0000-0000-000000000001', 'product:read',    'View products',    NOW()),
    ('a0000000-0000-0000-0000-000000000002', 'product:write',   'Manage products',  NOW()),
    ('a0000000-0000-0000-0000-000000000003', 'sale:read',       'View sales',       NOW()),
    ('a0000000-0000-0000-0000-000000000004', 'sale:write',      'Record sales',     NOW()),
    ('a0000000-0000-0000-0000-000000000005', 'customer:read',   'View customers',   NOW()),
    ('a0000000-0000-0000-0000-000000000006', 'customer:write',  'Manage customers', NOW()),
    ('a0000000-0000-0000-0000-000000000007', 'payment:write',   'Record payments',  NOW()),
    ('a0000000-0000-0000-0000-000000000008', 'report:read',     'View reports',     NOW()),
    ('a0000000-0000-0000-0000-000000000009', 'staff:manage',    'Manage staff',     NOW()),
    ('a0000000-0000-0000-0000-00000000000a', 'settings:manage', 'Manage settings',  NOW());

INSERT INTO role_permissions (id, role_id, permission_id)
SELECT ('b0000000-0000-0000-0000-' || lpad((row_number() OVER ())::text, 12, '0')),
       '11111111-1111-1111-1111-111111111111', p.id
FROM permissions p;

INSERT INTO payment_methods (code, name, label, enabled, requires_reference, fee_percent, sort_order, created_at, updated_at) VALUES
    ('CASH',   'Cash',   'Cash',   TRUE, FALSE, 0, 1, NOW(), NOW()),
    ('BKASH',  'bKash',  'bKash',  TRUE, TRUE,  0, 2, NOW(), NOW()),
    ('NAGAD',  'Nagad',  'Nagad',  TRUE, TRUE,  0, 3, NOW(), NOW()),
    ('CREDIT', 'Credit', 'Credit', TRUE, FALSE, 0, 4, NOW(), NOW()),
    ('BANK',   'Bank',   'Bank',   TRUE, TRUE,  0, 5, NOW(), NOW()),
    ('OTHER',  'Other',  'Other',  TRUE, FALSE, 0, 6, NOW(), NOW());
