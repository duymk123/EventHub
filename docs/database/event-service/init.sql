-- SCHEMA THAM CHIEU BC_32_V2 + quyet dinh UUID; KHONG PHAI migration trien khai.
-- Migration trien khai: event-service/src/main/resources/db/migration/V1__init.sql (Flyway chay tu dong).
-- Chon dung database eventhub_event truoc khi chay tren schema rong.
-- Khong DROP, khong tao database dich vu khac; FK chi trong database nay.
-- Cot thoi gian DATETIME(6) UTC (khop Instant khi Hibernate validate; TIMESTAMP cua MySQL co logic timezone
-- va gioi han 2038). Tai lieu mapping cot: fields.md.

CREATE TABLE categories (
  id CHAR(36) PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  description TEXT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  UNIQUE KEY uk_categories_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE events (
  id CHAR(36) PRIMARY KEY,
  organizer_id CHAR(36) NOT NULL,
  category_id CHAR(36) NOT NULL,
  title VARCHAR(255) NOT NULL,
  description TEXT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  start_time DATETIME(6) NOT NULL,
  end_time DATETIME(6) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  review_reason TEXT NULL,
  CONSTRAINT fk_events_category FOREIGN KEY (category_id) REFERENCES categories(id),
  CONSTRAINT chk_events_status CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','PUBLISHED','CLOSED')),
  CONSTRAINT chk_events_time CHECK (start_time < end_time),
  KEY idx_events_public (status, start_time),
  KEY idx_events_owner (organizer_id, created_at),
  KEY fk_events_category (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
