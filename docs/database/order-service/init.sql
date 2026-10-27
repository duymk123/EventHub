-- SCHEMA THAM CHIEU BC_32_V2 + quyet dinh UUID; KHONG PHAI migration trien khai.
-- Chon dung database eventhub_order truoc khi chay tren schema rong.
-- Khong DROP, khong tao database dich vu khac; FK chi trong database nay.

CREATE TABLE orders (
  id CHAR(36) PRIMARY KEY,
  customer_id CHAR(36) NOT NULL,
  event_id CHAR(36) NOT NULL,
  reservation_id CHAR(36) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING_PAYMENT',
  subtotal DECIMAL(14,2) NOT NULL,
  total_amount DECIMAL(14,2) NOT NULL,
  currency VARCHAR(3) NOT NULL DEFAULT 'VND',
  expires_at TIMESTAMP NOT NULL,
  paid_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  CHECK (status IN ('PENDING_PAYMENT','PAID','FAILED','EXPIRED','REFUNDED')),
  CHECK (subtotal >= 0 AND total_amount >= 0),
  INDEX idx_order_customer (customer_id, created_at),
  INDEX idx_order_expiry (status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE order_items (
  id CHAR(36) PRIMARY KEY,
  order_id CHAR(36) NOT NULL,
  ticket_type_id CHAR(36) NOT NULL,
  ticket_type_name VARCHAR(100) NOT NULL,
  quantity INT NOT NULL,
  unit_price DECIMAL(14,2) NOT NULL,
  line_total DECIMAL(14,2) NOT NULL,
  FOREIGN KEY (order_id) REFERENCES orders(id),
  UNIQUE KEY uk_order_type (order_id, ticket_type_id),
  CHECK (quantity > 0 AND unit_price >= 0 AND line_total = quantity * unit_price)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE order_holders (
  id CHAR(36) PRIMARY KEY,
  order_item_id CHAR(36) NOT NULL,
  holder_name VARCHAR(100) NOT NULL,
  holder_email VARCHAR(255) NULL,
  holder_phone VARCHAR(20) NULL,
  FOREIGN KEY (order_item_id) REFERENCES order_items(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payments (
  id CHAR(36) PRIMARY KEY,
  order_id CHAR(36) NOT NULL,
  provider VARCHAR(50) NOT NULL,
  provider_transaction_id VARCHAR(100) NULL,
  amount DECIMAL(14,2) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'CREATED',
  payment_url TEXT NULL,
  idempotency_key VARCHAR(100) NOT NULL UNIQUE,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  FOREIGN KEY (order_id) REFERENCES orders(id),
  UNIQUE KEY uk_provider_transaction (provider, provider_transaction_id),
  CHECK (status IN ('CREATED','PENDING','SUCCEEDED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE refund_requests (
  id CHAR(36) PRIMARY KEY,
  order_id CHAR(36) NOT NULL,
  requested_by CHAR(36) NOT NULL,
  amount DECIMAL(14,2) NOT NULL,
  reason TEXT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
  approved_by CHAR(36) NULL,
  review_reason TEXT NULL,
  requested_at TIMESTAMP NOT NULL,
  reviewed_at TIMESTAMP NULL,
  refunded_at TIMESTAMP NULL,
  FOREIGN KEY (order_id) REFERENCES orders(id),
  CHECK (status IN ('REQUESTED','APPROVED','REJECTED','PROCESSING','REFUNDED','FAILED')),
  CHECK (amount > 0),
  INDEX idx_refund_order (order_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payment_webhooks (
  id CHAR(36) PRIMARY KEY,
  provider VARCHAR(50) NOT NULL,
  provider_event_id VARCHAR(100) NOT NULL,
  order_id CHAR(36) NOT NULL,
  payload_hash VARCHAR(255) NOT NULL,
  received_at TIMESTAMP NOT NULL,
  processed_at TIMESTAMP NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
  FOREIGN KEY (order_id) REFERENCES orders(id),
  UNIQUE KEY uk_provider_event (provider, provider_event_id),
  CHECK (status IN ('RECEIVED','PROCESSED','FAILED'))
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
