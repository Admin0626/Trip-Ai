-- Additive, idempotent MySQL upgrade. Never run schema.sql on an existing database.
SET @ragddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='knowledge_doc' AND column_name='source_id')=0,
 'ALTER TABLE knowledge_doc ADD COLUMN source_id BIGINT UNSIGNED NULL COMMENT ''关联目的地或路线ID，空表示独立资料''','SELECT 1');
PREPARE ragstmt FROM @ragddl; EXECUTE ragstmt; DEALLOCATE PREPARE ragstmt;
SET @ragddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='knowledge_doc' AND column_name='revision')=0,
 'ALTER TABLE knowledge_doc ADD COLUMN revision BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''内容与状态版本''','SELECT 1');
PREPARE ragstmt FROM @ragddl; EXECUTE ragstmt; DEALLOCATE PREPARE ragstmt;
SET @ragddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='knowledge_doc' AND column_name='indexed_revision')=0,
 'ALTER TABLE knowledge_doc ADD COLUMN indexed_revision BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''本地索引版本''','SELECT 1');
PREPARE ragstmt FROM @ragddl; EXECUTE ragstmt; DEALLOCATE PREPARE ragstmt;
SET @ragddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='knowledge_doc' AND column_name='index_method')=0,
 'ALTER TABLE knowledge_doc ADD COLUMN index_method VARCHAR(20) NOT NULL DEFAULT ''NONE'' COMMENT ''NONE / LOCAL_NGRAM，非向量索引''','SELECT 1');
PREPARE ragstmt FROM @ragddl; EXECUTE ragstmt; DEALLOCATE PREPARE ragstmt;

CREATE TABLE IF NOT EXISTS knowledge_chunk (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
 doc_id BIGINT UNSIGNED NOT NULL,
 doc_revision BIGINT UNSIGNED NOT NULL,
 chunk_index INT UNSIGNED NOT NULL,
 start_offset INT UNSIGNED NOT NULL,
 end_offset INT UNSIGNED NOT NULL,
 content TEXT NOT NULL,
 PRIMARY KEY(id),
 UNIQUE KEY uk_knowledge_chunk(doc_id,doc_revision,chunk_index),
 CONSTRAINT fk_knowledge_chunk_doc FOREIGN KEY(doc_id) REFERENCES knowledge_doc(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='本地知识分片，偏移量以Unicode字符计';

CREATE TABLE IF NOT EXISTS knowledge_chunk_token (
 chunk_id BIGINT UNSIGNED NOT NULL,
 token VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 PRIMARY KEY(chunk_id,token),
 KEY idx_knowledge_token(token,chunk_id),
 CONSTRAINT fk_knowledge_token_chunk FOREIGN KEY(chunk_id) REFERENCES knowledge_chunk(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='本地字符二元组与英文词倒排索引，非语义向量';
