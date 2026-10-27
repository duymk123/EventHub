-- SCHEMA THAM CHIEU BC_32_V2 + quyet dinh UUID; KHONG PHAI migration trien khai.
-- Chon dung database eventhub_ticket truoc khi chay tren schema rong.
-- Khong DROP, khong tao database dich vu khac; FK chi trong database nay.

CREATE TABLE ticket_types (
  id CHAR(36) PRIMARY KEY,
  event_id CHAR(36) NOT NULL,
  name VARCHAR(100) NOT NULL,
  price DECIMAL(14,2) NOT NULL,
  total_quota INT NOT NULL,
  available_quantity INT NOT NULL,
  reserved_quantity INT NOT NULL DEFAULT 0,
  sold_quantity INT NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP NOT NULL,
  UNIQUE KEY uk_type_name (event_id, name),
  CHECK (name IN ('VIP','PREMIUM','STANDARD')),
  CHECK (price >= 0),
  CHECK (available_quantity >= 0 AND reserved_quantity >= 0 AND sold_quantity >= 0),
  CHECK (total_quota = available_quantity + reserved_quantity + sold_quantity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE reservations (
  id CHAR(36) PRIMARY KEY,
  order_id CHAR(36) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'RESERVED',
  expires_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP NOT NULL,
  confirmed_at TIMESTAMP NULL,
  released_at TIMESTAMP NULL,
  UNIQUE KEY uk_reservation_order (order_id),
  CHECK (status IN ('RESERVED','CONFIRMED','RELEASED','EXPIRED')),
  INDEX idx_reservation_expiry (status, expires_at)
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

CREATE TABLE tickets (
  id CHAR(36) PRIMARY KEY,
  reservation_id CHAR(36) NOT NULL,
  order_id CHAR(36) NOT NULL,
  ticket_type_id CHAR(36) NOT NULL,
  owner_user_id CHAR(36) NOT NULL,
  holder_name VARCHAR(100) NULL,
  qr_code_value VARCHAR(255) NOT NULL UNIQUE,
  status VARCHAR(20) NOT NULL DEFAULT 'ISSUED',
  issued_at TIMESTAMP NOT NULL,
  cancelled_at TIMESTAMP NULL,
  FOREIGN KEY (reservation_id) REFERENCES reservations(id),
  FOREIGN KEY (ticket_type_id) REFERENCES ticket_types(id),
  CHECK (status IN ('ISSUED','CHECKED_IN','CHECKED_OUT','CANCELLED')),
  INDEX idx_tickets_owner (owner_user_id, issued_at),
  INDEX idx_tickets_order (order_id)
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
