CREATE TABLE purchase_order (
    order_id      VARCHAR(36) PRIMARY KEY,
    shop_id       VARCHAR(60) NOT NULL,
    supplier_id   VARCHAR(36),
    status        VARCHAR(20) NOT NULL,
    total_amount  NUMERIC(14, 2) NOT NULL,
    notes         VARCHAR(500),
    created_at    TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP NOT NULL
);

CREATE INDEX idx_purchase_order_shop_date
    ON purchase_order (shop_id, created_at);

CREATE TABLE purchase_order_item (
    item_id       VARCHAR(36) PRIMARY KEY,
    order_id      VARCHAR(36) NOT NULL,
    product_id    VARCHAR(36) NOT NULL,
    name          VARCHAR(200) NOT NULL,
    qty           NUMERIC(14, 3) NOT NULL,
    unit_cost     NUMERIC(14, 2) NOT NULL
);

CREATE INDEX idx_purchase_order_item_order
    ON purchase_order_item (order_id);
