CREATE TABLE IF NOT EXISTS sale_item_batch_allocations (
    allocation_id VARCHAR(36) PRIMARY KEY,
    sale_id VARCHAR(36) NOT NULL REFERENCES sales (sale_id) ON DELETE CASCADE,
    sale_item_id VARCHAR(36) NOT NULL REFERENCES sale_items (id) ON DELETE CASCADE,
    batch_id VARCHAR(36) NOT NULL REFERENCES product_batches (batch_id),
    quantity INT NOT NULL CHECK (quantity > 0),
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sale_batch_allocations_sale
    ON sale_item_batch_allocations (sale_id);
CREATE INDEX IF NOT EXISTS idx_sale_batch_allocations_item
    ON sale_item_batch_allocations (sale_item_id);
