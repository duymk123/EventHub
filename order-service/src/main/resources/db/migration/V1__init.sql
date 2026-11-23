-- V1 (C12, C14, C06, C15): schema Order. Noi dung bang docs/database/order-service/init.sql.
-- Khong sua file nay sau khi da chay; thay doi schema them file V2__..., V3__...

CREATE TABLE orders (
  id CHAR(36) PRIMARY KEY,
  customer_id CHAR(36) NOT NULL,
  customer_email VARCHAR(255) NOT NULL,
  event_id CHAR(36) NOT NULL,
  reservation_id CHAR(36) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING_PAYMENT',
  subtotal DECIMAL(14,2) NOT NULL,
  total_amount DECIMAL(14,2) NOT NULL,
  currency VARCHAR(3) NOT NULL DEFAULT 'VND',
  expires_at TIMESTAMP NOT NULL,
  paid_at TIMESTAMP NULL,
  tickets_confirmed_at TIMESTAMP NULL,
  payment_id CHAR(36) NULL,
  event_start_time TIMESTAMP NULL,
  idempotency_key VARCHAR(100) NULL,
  request_hash CHAR(64) NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  -- C20: moi khach chi mot don PENDING_PAYMENT cho moi su kien; NULL khi da het cho nen khong chan don cu.
  pending_slot VARCHAR(73) GENERATED ALWAYS AS (
    CASE WHEN status = 'PENDING_PAYMENT' THEN CONCAT(customer_id, ':', event_id) END) STORED,
  UNIQUE KEY uk_order_idempotency (customer_id, idempotency_key),
  UNIQUE KEY uk_order_pending_slot (pending_slot),
  CHECK (status IN ('PENDING_PAYMENT','PAID','FAILED','EXPIRED','CANCELLED','REFUNDED')),
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
  seat_id CHAR(36) NOT NULL,
  seat_label VARCHAR(8) NOT NULL,
  holder_name VARCHAR(100) NOT NULL,
  holder_email VARCHAR(255) NULL,
  holder_phone VARCHAR(20) NULL,
  FOREIGN KEY (order_item_id) REFERENCES order_items(id),
  UNIQUE KEY uk_holder_seat (order_item_id, seat_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payments (
  id CHAR(36) PRIMARY KEY,
  order_id CHAR(36) NOT NULL,
  provider VARCHAR(50) NOT NULL,
  provider_txn_ref VARCHAR(40) NOT NULL,
  provider_transaction_id VARCHAR(100) NULL,
  provider_pay_date VARCHAR(14) NULL,
  amount DECIMAL(14,2) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'CREATED',
  payment_url TEXT NULL,
  idempotency_key VARCHAR(100) NOT NULL UNIQUE,
  reconciled_at TIMESTAMP NULL,
  reconciled_by CHAR(36) NULL,
  reconciliation_note VARCHAR(500) NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  FOREIGN KEY (order_id) REFERENCES orders(id),
  UNIQUE KEY uk_provider_transaction (provider, provider_transaction_id),
  UNIQUE KEY uk_provider_txn_ref (provider, provider_txn_ref),
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
  provider_request_id VARCHAR(40) NULL,
  provider_refund_id VARCHAR(100) NULL,
  failure_reason VARCHAR(255) NULL,
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
