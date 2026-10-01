CREATE TABLE daily_cash_closing (
    closing_id VARCHAR(36) PRIMARY KEY,
    shop_id VARCHAR(60) NOT NULL,
    business_date DATE NOT NULL,
    opened_by VARCHAR(36) NOT NULL,
    closed_by VARCHAR(36),
    opening_cash DOUBLE PRECISION NOT NULL DEFAULT 0,
    cash_sales DOUBLE PRECISION NOT NULL DEFAULT 0,
    digital_sales DOUBLE PRECISION NOT NULL DEFAULT 0,
    expenses DOUBLE PRECISION NOT NULL DEFAULT 0,
    expected_cash DOUBLE PRECISION NOT NULL DEFAULT 0,
    actual_cash DOUBLE PRECISION,
    variance DOUBLE PRECISION,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    note TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_daily_cash_closing_shop_date UNIQUE (shop_id, business_date)
);
CREATE INDEX idx_daily_cash_closing_shop_date ON daily_cash_closing (shop_id, business_date DESC);
