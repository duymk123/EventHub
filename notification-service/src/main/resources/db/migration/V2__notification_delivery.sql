CREATE TABLE notification_batches (
  id CHAR(36) PRIMARY KEY,
  target_fingerprint VARCHAR(64) NOT NULL,
  expected_count INT NOT NULL,
  status VARCHAR(10) NOT NULL,
  created_at TIMESTAMP NOT NULL);

ALTER TABLE notifications
  ADD COLUMN lease_token CHAR(36) NULL,
  ADD COLUMN lease_until TIMESTAMP NULL,
  ADD INDEX idx_delivery_message (source_message_id, status);
