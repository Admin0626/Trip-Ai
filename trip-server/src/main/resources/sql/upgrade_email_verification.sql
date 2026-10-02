-- Additive/idempotent; existing addresses are NOT verified by this migration.
-- Select the existing application database before executing. Do not rerun schema/data.sql.
SET @emailddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sys_user' AND column_name='email_verified')=0,
 'ALTER TABLE sys_user ADD COLUMN email_verified TINYINT NOT NULL DEFAULT 0 COMMENT ''邮箱所有权已验证：0否1是''','SELECT 1');
PREPARE emailstmt FROM @emailddl;
EXECUTE emailstmt;
DEALLOCATE PREPARE emailstmt;
