-- Ban tham chieu dong bo tu migration V1; KHONG chay thu cong ben canh Flyway.
-- UUID IDs; schema managed by Flyway, never by Hibernate update.
CREATE TABLE sales_metrics (
  id CHAR(36) PRIMARY KEY,
  event_id CHAR(36) NOT NULL, ticket_type_id CHAR(36) NOT NULL,
  ticket_type_name VARCHAR(100) NOT NULL, metric_date DATE NOT NULL,
  revenue DECIMAL(14,2) NOT NULL DEFAULT 0,
  tickets_sold INT NOT NULL DEFAULT 0,
  refunded_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
  tickets_refunded INT NOT NULL DEFAULT 0, updated_at TIMESTAMP NOT NULL,
  UNIQUE KEY uk_metric (event_id, ticket_type_id, metric_date));

CREATE TABLE dashboard_snapshot (
  id CHAR(36) PRIMARY KEY,
  event_id CHAR(36) NOT NULL UNIQUE,
  total_revenue DECIMAL(14,2) NOT NULL DEFAULT 0,
  total_tickets_sold INT NOT NULL DEFAULT 0,
  current_attendance INT NOT NULL DEFAULT 0,
  total_checked_in INT NOT NULL DEFAULT 0,
  last_updated TIMESTAMP NOT NULL);

CREATE TABLE attendance_logs (
  id CHAR(36) PRIMARY KEY,
  ticket_id CHAR(36) NOT NULL, event_id CHAR(36) NOT NULL,
  gate_id CHAR(36), action VARCHAR(10) NOT NULL,
  occurred_at TIMESTAMP NOT NULL,
  INDEX idx_att (event_id, occurred_at));

CREATE TABLE processed_events (
  id CHAR(36) PRIMARY KEY, -- messageId
  processed_at TIMESTAMP NOT NULL);
