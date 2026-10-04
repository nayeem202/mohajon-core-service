CREATE TABLE IF NOT EXISTS branches (branch_id VARCHAR(80) PRIMARY KEY, shop_id VARCHAR(60) NOT NULL REFERENCES shops(shop_id) ON DELETE CASCADE, name VARCHAR(150) NOT NULL, address VARCHAR(255), phone VARCHAR(30), is_default BOOLEAN NOT NULL DEFAULT FALSE, is_active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL);
CREATE INDEX IF NOT EXISTS idx_branches_shop ON branches(shop_id, is_active);
CREATE TABLE IF NOT EXISTS branch_members (membership_id VARCHAR(36) PRIMARY KEY, branch_id VARCHAR(80) NOT NULL REFERENCES branches(branch_id) ON DELETE CASCADE, user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE, CONSTRAINT uq_branch_member UNIQUE(branch_id,user_id));
CREATE INDEX IF NOT EXISTS idx_branch_members_user ON branch_members(user_id);
INSERT INTO branches(branch_id,shop_id,name,is_default,is_active,created_at,updated_at) SELECT CONCAT(shop_id,'-main'),shop_id,'Main Branch',TRUE,TRUE,NOW(),NOW() FROM shops s WHERE NOT EXISTS (SELECT 1 FROM branches b WHERE b.shop_id=s.shop_id);
-- Only shops whose owner still exists in `users` can get a membership row:
-- `branch_members.user_id` is a foreign key, and a shop left behind by a
-- deleted account would otherwise abort the whole migration.
INSERT INTO branch_members(membership_id,branch_id,user_id) SELECT md5(CONCAT(s.shop_id,'-',s.owner_user_id)),CONCAT(s.shop_id,'-main'),s.owner_user_id FROM shops s WHERE EXISTS (SELECT 1 FROM users u WHERE u.id=s.owner_user_id) AND NOT EXISTS (SELECT 1 FROM branch_members m WHERE m.branch_id=CONCAT(s.shop_id,'-main') AND m.user_id=s.owner_user_id);
