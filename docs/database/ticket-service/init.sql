-- SCHEMA THAM CHIEU BC_32_V2 + quyet dinh UUID; KHONG PHAI migration trien khai.
-- Chon dung database eventhub_ticket truoc khi chay tren schema rong.
-- Khong DROP, khong tao database dich vu khac; FK chi trong database nay.
-- C12 (2026-11-22): ban ve theo ghe. Zone = hang ve; ghe sinh tu row_count x seats_per_row cua tung su kien.
-- Ton kho tinh tu seats.status, ticket_types khong con bo dem available/reserved/sold.
-- C15: file nay la noi dung Flyway V1__init.sql cua ticket-service (ddl-auto: validate).

CREATE TABLE ticket_types (
  id CHAR(36) PRIMARY KEY,
  event_id CHAR(36) NOT NULL,
  name VARCHAR(100) NOT NULL,
  price DECIMAL(14,2) NOT NULL,
  row_count INT NOT NULL DEFAULT 0,
  seats_per_row INT NOT NULL DEFAULT 0,
  total_quota INT NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP NOT NULL,
  UNIQUE KEY uk_type_name (event_id, name),
  CHECK (name IN ('VIP','PREMIUM','STANDARD')),
  CHECK (price >= 0),
  CHECK (row_count BETWEEN 0 AND 30 AND seats_per_row BETWEEN 0 AND 60),
  CHECK (total_quota = row_count * seats_per_row)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE reservations (
  id CHAR(36) PRIMARY KEY,
  order_id CHAR(36) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'RESERVED',
  expires_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP NOT NULL,
  confirmed_at TIMESTAMP NULL,
  released_at TIMESTAMP NULL,
  event_start_time TIMESTAMP NULL,
  UNIQUE KEY uk_reservation_order (order_id),
  CHECK (status IN ('RESERVED','CONFIRMED','RELEASED','EXPIRED')),
  INDEX idx_reservation_expiry (status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE seats (
  id CHAR(36) PRIMARY KEY,
  ticket_type_id CHAR(36) NOT NULL,
  row_index INT NOT NULL,
  row_label VARCHAR(3) NOT NULL,
  seat_number INT NOT NULL,
  status VARCHAR(10) NOT NULL DEFAULT 'AVAILABLE',
  reservation_id CHAR(36) NULL,
  updated_at TIMESTAMP NOT NULL,
  FOREIGN KEY (ticket_type_id) REFERENCES ticket_types(id),
  FOREIGN KEY (reservation_id) REFERENCES reservations(id),
  UNIQUE KEY uk_seat_position (ticket_type_id, row_index, seat_number),
  CHECK (status IN ('AVAILABLE','HELD','SOLD')),
  CHECK ((status = 'AVAILABLE' AND reservation_id IS NULL) OR (status <> 'AVAILABLE' AND reservation_id IS NOT NULL)),
  CHECK (row_index >= 1 AND seat_number >= 1),
  INDEX idx_seat_type_status (ticket_type_id, status),
  INDEX idx_seat_reservation (reservation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE reservation_items (
  id CHAR(36) PRIMARY KEY,
  reservation_id CHAR(36) NOT NULL,
  ticket_type_id CHAR(36) NOT NULL,
  quantity INT NOT NULL,
  unit_price DECIMAL(14,2) NOT NULL,
  FOREIGN KEY (reservation_id) REFERENCES reservations(id),
  FOREIGN KEY (ticket_type_id) REFERENCES ticket_types(id),
  UNIQUE KEY uk_reserved_type (reservation_id, ticket_type_id),
  CHECK (quantity > 0 AND unit_price >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE reservation_seats (
  id CHAR(36) PRIMARY KEY,
  reservation_id CHAR(36) NOT NULL,
  seat_id CHAR(36) NOT NULL,
  seat_label VARCHAR(8) NOT NULL,
  FOREIGN KEY (reservation_id) REFERENCES reservations(id),
  FOREIGN KEY (seat_id) REFERENCES seats(id),
  UNIQUE KEY uk_reservation_seat (reservation_id, seat_id),
  INDEX idx_reservation_seat_seat (seat_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tickets (
  id CHAR(36) PRIMARY KEY,
  reservation_id CHAR(36) NOT NULL,
  order_id CHAR(36) NOT NULL,
  ticket_type_id CHAR(36) NOT NULL,
  seat_id CHAR(36) NOT NULL,
  seat_label VARCHAR(8) NOT NULL,
  owner_user_id CHAR(36) NOT NULL,
  holder_name VARCHAR(100) NULL,
  qr_code_value VARCHAR(255) NOT NULL UNIQUE,
  status VARCHAR(20) NOT NULL DEFAULT 'ISSUED',
  issued_at TIMESTAMP NOT NULL,
  cancelled_at TIMESTAMP NULL,
  FOREIGN KEY (reservation_id) REFERENCES reservations(id),
  FOREIGN KEY (ticket_type_id) REFERENCES ticket_types(id),
  FOREIGN KEY (seat_id) REFERENCES seats(id),
  CHECK (status IN ('ISSUED','CHECKED_IN','CHECKED_OUT','CANCELLED')),
  INDEX idx_tickets_owner (owner_user_id, issued_at),
  INDEX idx_tickets_order (order_id),
  INDEX idx_tickets_seat (seat_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ticket_scans (
  id CHAR(36) PRIMARY KEY,
  ticket_id CHAR(36) NOT NULL,
  gate_id CHAR(36) NOT NULL,
  scan_type VARCHAR(10) NOT NULL,
  scanned_at TIMESTAMP NOT NULL,
  FOREIGN KEY (ticket_id) REFERENCES tickets(id),
  CHECK (scan_type IN ('CHECK_IN','CHECK_OUT')),
  INDEX idx_scan_ticket (ticket_id, scanned_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE outbox_events (
  id CHAR(36) PRIMARY KEY,
  aggregate_id CHAR(36) NOT NULL,
  event_type VARCHAR(100) NOT NULL,
  payload JSON NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  created_at TIMESTAMP NOT NULL,
  published_at TIMESTAMP NULL,
  INDEX idx_outbox_pending (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
