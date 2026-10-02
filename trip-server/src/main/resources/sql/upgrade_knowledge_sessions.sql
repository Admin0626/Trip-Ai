-- Additive and idempotent. Requires existing chat tables and upgrade_local_knowledge.sql.
-- Never execute schema.sql/data.sql on an existing database.
SET @ksddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='llm_chat_session' AND column_name='mode')=0,
 'ALTER TABLE llm_chat_session ADD COLUMN mode VARCHAR(20) NOT NULL DEFAULT ''LEGACY'' COMMENT ''LEGACY / LOCAL_SEARCH''','SELECT 1');
PREPARE ksstmt FROM @ksddl; EXECUTE ksstmt; DEALLOCATE PREPARE ksstmt;
SET @ksddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='llm_chat_session' AND column_name='revision')=0,
 'ALTER TABLE llm_chat_session ADD COLUMN revision BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''会话内容及标题版本''','SELECT 1');
PREPARE ksstmt FROM @ksddl; EXECUTE ksstmt; DEALLOCATE PREPARE ksstmt;
SET @ksddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='llm_chat_message' AND column_name='message_type')=0,
 'ALTER TABLE llm_chat_message ADD COLUMN message_type VARCHAR(20) NOT NULL DEFAULT ''LEGACY'' COMMENT ''LEGACY / LOCAL_QUERY / LOCAL_RESULT''','SELECT 1');
PREPARE ksstmt FROM @ksddl; EXECUTE ksstmt; DEALLOCATE PREPARE ksstmt;
SET @ksddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='llm_chat_message' AND column_name='request_id')=0,
 'ALTER TABLE llm_chat_message ADD COLUMN request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''本次检索UUID''','SELECT 1');
PREPARE ksstmt FROM @ksddl; EXECUTE ksstmt; DEALLOCATE PREPARE ksstmt;
SET @ksddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='llm_chat_message' AND column_name='request_hash')=0,
 'ALTER TABLE llm_chat_message ADD COLUMN request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''规范化问题和topK摘要''','SELECT 1');
PREPARE ksstmt FROM @ksddl; EXECUTE ksstmt; DEALLOCATE PREPARE ksstmt;
SET @ksddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='llm_chat_session' AND index_name='idx_local_session_page')=0,
 'ALTER TABLE llm_chat_session ADD INDEX idx_local_session_page(user_id,mode,deleted,update_time,id)','SELECT 1');
PREPARE ksstmt FROM @ksddl; EXECUTE ksstmt; DEALLOCATE PREPARE ksstmt;
SET @ksddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='llm_chat_message' AND index_name='uk_chat_request_role')=0,
 'ALTER TABLE llm_chat_message ADD UNIQUE INDEX uk_chat_request_role(session_id,request_id,role)','SELECT 1');
PREPARE ksstmt FROM @ksddl; EXECUTE ksstmt; DEALLOCATE PREPARE ksstmt;
