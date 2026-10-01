CREATE TABLE business_coach_insight (
    insight_id  VARCHAR(36) PRIMARY KEY,
    shop_id     VARCHAR(60) NOT NULL,
    insight_date DATE NOT NULL,
    type        VARCHAR(30) NOT NULL,
    priority    INT NOT NULL,
    title_en    VARCHAR(255) NOT NULL,
    title_bn    VARCHAR(255) NOT NULL,
    body_en     TEXT NOT NULL,
    body_bn     TEXT NOT NULL,
    action_en   VARCHAR(255) NOT NULL,
    action_bn   VARCHAR(255) NOT NULL,
    amount      DOUBLE PRECISION,
    created_at  TIMESTAMP NOT NULL,
    CONSTRAINT uk_business_coach_daily UNIQUE (shop_id, insight_date, type)
);
CREATE INDEX idx_business_coach_shop_date ON business_coach_insight (shop_id, insight_date, created_at);
