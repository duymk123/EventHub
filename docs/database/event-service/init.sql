-- SCHEMA THAM CHIEU BC_32_V2 + quyet dinh UUID; KHONG PHAI migration trien khai.
-- Chon dung database eventhub_event truoc khi chay tren schema rong.
-- Khong DROP, khong tao database dich vu khac; FK chi trong database nay.

CREATE TABLE categories (
  id CHAR(36) PRIMARY KEY,
  name VARCHAR(100) NOT NULL UNIQUE,
  description TEXT NULL,
  created_at TIMESTAMP NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE events (
  id CHAR(36) PRIMARY KEY,
  organizer_id CHAR(36) NOT NULL,
  category_id CHAR(36) NOT NULL,
  title VARCHAR(255) NOT NULL,
  description TEXT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  start_time TIMESTAMP NOT NULL,
  end_time TIMESTAMP NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  review_reason TEXT NULL,
  FOREIGN KEY (category_id) REFERENCES categories(id),
  CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','PUBLISHED','CLOSED')),
  CHECK (start_time < end_time),
  INDEX idx_events_public (status, start_time),
  INDEX idx_events_owner (organizer_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
