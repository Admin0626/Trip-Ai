-- Non-destructive upgrade; never run schema.sql to upgrade an existing database.
USE trip_llm;
CREATE TABLE IF NOT EXISTS catalog_import_batch (
    id bigint unsigned NOT NULL AUTO_INCREMENT,
    user_id bigint unsigned NOT NULL,
    request_id char(36) NOT NULL,
    payload_hash char(64) NOT NULL,
    result_json text NOT NULL,
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_owner_request(user_id,request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='目录导入幂等结果（不存文件内容）';
