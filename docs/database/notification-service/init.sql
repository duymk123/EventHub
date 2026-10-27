-- Ban tham chieu dong bo tu migration V1; KHONG chay thu cong ben canh Flyway.
-- UUID IDs; schema managed by Flyway, never by Hibernate update.
CREATE TABLE notification_templates (
  id CHAR(36) PRIMARY KEY,
  code VARCHAR(50) NOT NULL, channel VARCHAR(10) NOT NULL,
  subject VARCHAR(255) NOT NULL, body_template TEXT NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  UNIQUE KEY uk_template (code, channel));

CREATE TABLE notifications (
  id CHAR(36) PRIMARY KEY,
  dedup_key VARCHAR(400) NOT NULL UNIQUE,
  source_message_id CHAR(36) NOT NULL,
  recipient_user_id CHAR(36) NULL, recipient_email VARCHAR(255) NULL,
  channel VARCHAR(10) NOT NULL, template_code VARCHAR(50) NOT NULL,
  title VARCHAR(255), content TEXT, payload JSON,
  status VARCHAR(10) NOT NULL, attempts INT NOT NULL DEFAULT 0,
  last_error VARCHAR(500), sent_at TIMESTAMP NULL, read_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL,
  INDEX idx_inapp (recipient_user_id, channel, read_at, created_at));

CREATE TABLE processed_events (
  id CHAR(36) PRIMARY KEY, -- chính là messageId, không sinh UUID mới
  processed_at TIMESTAMP NOT NULL);
