-- Schema nền eventhub_event, tương đương docs/database/event-service/init.sql.
-- Chỉ Hibernate validate schema, không để Hibernate tạo/sửa bảng (xem EVENT-STATUS.md §7).
-- Mọi PK/FK dùng UUID CHAR(36); cột thời gian DATETIME(6), lưu UTC, không dùng logic TIMESTAMP của MySQL.

CREATE TABLE categories (
  id CHAR(36) NOT NULL,
  name VARCHAR(100) NOT NULL,
  description TEXT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_categories_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE events (
  id CHAR(36) NOT NULL,
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
  PRIMARY KEY (id),
  KEY idx_events_public (status, start_time),
  KEY idx_events_owner (organizer_id, created_at),
  KEY fk_events_category (category_id),
  CONSTRAINT fk_events_category FOREIGN KEY (category_id) REFERENCES categories (id),
  CONSTRAINT chk_events_status CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','PUBLISHED','CLOSED')),
  CONSTRAINT chk_events_time CHECK (start_time < end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
