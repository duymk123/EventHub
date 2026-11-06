CREATE TABLE attendance_state (
  id CHAR(36) PRIMARY KEY,
  ticket_id CHAR(36) NOT NULL UNIQUE,
  event_id CHAR(36) NOT NULL,
  checked_in_at TIMESTAMP NULL,
  checked_out_at TIMESTAMP NULL);
