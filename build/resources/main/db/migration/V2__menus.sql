-- =====================================================================
-- Haalkhata service - configurable menus + per-staff menu grants
-- =====================================================================

CREATE TABLE menus (
    id         VARCHAR(36) PRIMARY KEY,
    code       VARCHAR(50) NOT NULL UNIQUE,
    name       VARCHAR(100) NOT NULL,
    section    VARCHAR(30) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE user_menus (
    id      VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    menu_id VARCHAR(36) NOT NULL REFERENCES menus (id) ON DELETE CASCADE,
    CONSTRAINT uq_user_menu UNIQUE (user_id, menu_id)
);
CREATE INDEX idx_user_menus_user ON user_menus (user_id);

INSERT INTO menus (id, code, name, section, sort_order, created_at) VALUES
    ('c1000000-0000-0000-0000-000000000001', 'HOME',            'Home',            'MAIN',        1,  NOW()),
    ('c1000000-0000-0000-0000-000000000002', 'SALES',           'Sales (POS)',     'MAIN',        2,  NOW()),
    ('c1000000-0000-0000-0000-000000000003', 'STOCK',           'Stock',           'MAIN',        3,  NOW()),
    ('c1000000-0000-0000-0000-000000000004', 'CLIENTS',         'Clients',         'MAIN',        4,  NOW()),
    ('c1000000-0000-0000-0000-000000000005', 'PRODUCTS',        'Products',        'OPERATIONS',  1,  NOW()),
    ('c1000000-0000-0000-0000-000000000006', 'ADD_PRODUCT',     'Add Product',     'OPERATIONS',  2,  NOW()),
    ('c1000000-0000-0000-0000-000000000007', 'ADD_CUSTOMER',    'Add Customer',    'OPERATIONS',  3,  NOW()),
    ('c1000000-0000-0000-0000-000000000008', 'VENDORS',         'Vendors',         'OPERATIONS',  4,  NOW()),
    ('c1000000-0000-0000-0000-000000000009', 'EXPENSES',        'Expenses',        'OPERATIONS',  5,  NOW()),
    ('c1000000-0000-0000-0000-00000000000a', 'INVESTORS',       'Investors',       'OPERATIONS',  6,  NOW()),
    ('c1000000-0000-0000-0000-00000000000b', 'BARCODE',         'Barcode Scan',    'OPERATIONS',  7,  NOW()),
    ('c1000000-0000-0000-0000-00000000000c', 'PURCHASE',        'Purchase',        'OPERATIONS',  8,  NOW()),
    ('c1000000-0000-0000-0000-00000000000d', 'DUE',             'Due',             'FINANCE',     1,  NOW()),
    ('c1000000-0000-0000-0000-00000000000e', 'INVESTMENTS',     'Investments',     'FINANCE',     2,  NOW()),
    ('c1000000-0000-0000-0000-00000000000f', 'REPORTS',         'Reports',         'FINANCE',     3,  NOW()),
    ('c1000000-0000-0000-0000-000000000010', 'SALES_HISTORY',   'Sales History',   'FINANCE',     4,  NOW()),
    ('c1000000-0000-0000-0000-000000000011', 'PROFILE',         'My Profile',      'APP',         1,  NOW()),
    ('c1000000-0000-0000-0000-000000000012', 'STAFF',           'Staff',           'APP',         2,  NOW()),
    ('c1000000-0000-0000-0000-000000000013', 'SETTINGS',        'Settings',        'APP',         3,  NOW()),
    ('c1000000-0000-0000-0000-000000000014', 'SECURITY',        'Account Security','APP',         4,  NOW()),
    ('c1000000-0000-0000-0000-000000000015', 'PRIVACY',         'Privacy',         'APP',         5,  NOW());
