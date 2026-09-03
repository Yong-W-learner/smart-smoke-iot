-- 本地 AI 助手扩展表（与 forest-schema.sql 一起由 spring.sql.init 执行，全部语句幂等）
-- 命名/主键/时间/字符集沿用现有规范：create_time/update_time、utf8mb4、InnoDB、BIGINT 自增主键。

CREATE TABLE IF NOT EXISTS ai_conversation (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  username VARCHAR(64) NOT NULL,
  title VARCHAR(200) NOT NULL DEFAULT '新的对话',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_ai_conversation_user (username, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_message (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  conversation_id BIGINT NOT NULL,
  role VARCHAR(16) NOT NULL,
  content MEDIUMTEXT NOT NULL,
  source_json MEDIUMTEXT NULL,
  model_name VARCHAR(64) NULL,
  latency_ms BIGINT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_ai_message_conversation (conversation_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_knowledge_document (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  filename VARCHAR(255) NOT NULL,
  relative_path VARCHAR(512) NOT NULL,
  content_hash VARCHAR(64) NOT NULL DEFAULT '',
  status VARCHAR(16) NOT NULL DEFAULT 'pending',
  chunk_count INT NOT NULL DEFAULT 0,
  last_error VARCHAR(500) NULL,
  indexed_at DATETIME NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_ai_knowledge_path (relative_path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_audit_log (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NULL,
  username VARCHAR(64) NULL,
  conversation_id BIGINT NULL,
  action VARCHAR(32) NOT NULL,
  tool_name VARCHAR(64) NULL,
  parameters_json VARCHAR(2000) NULL,
  success TINYINT NOT NULL DEFAULT 1,
  error_message VARCHAR(500) NULL,
  latency_ms BIGINT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_ai_audit_user_time (username, create_time),
  KEY idx_ai_audit_action (action, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- AI 知识库管理员演示账号（课程演示级别，与 ranger 账号同等安全强度；公网部署前必须删除或改密）。
INSERT INTO sys_user(username,role,password,job_num,phone)
VALUES('admin','admin','admin123','A0001','13800000000') ON DUPLICATE KEY UPDATE role=VALUES(role);
