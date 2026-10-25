-- Khởi tạo Database cho Ticket Service
CREATE DATABASE IF NOT EXISTS ticket_service
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE ticket_service;

-- Xóa bảng cũ nếu cần chạy lại từ đầu (theo thứ tự bảng con trước, bảng cha sau)
DROP TABLE IF EXISTS processed_events;
DROP TABLE IF EXISTS ticket_scans;
DROP TABLE IF EXISTS tickets;
DROP TABLE IF EXISTS reservation_items;
DROP TABLE IF EXISTS reservations;
DROP TABLE IF EXISTS ticket_types;
DROP TABLE IF EXISTS events;

-- ==========================================
-- 1. Bảng: events (Sự kiện)
-- ==========================================
CREATE TABLE events (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    location VARCHAR(500) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'UPCOMING' COMMENT 'UPCOMING, ONGOING, COMPLETED, CANCELLED',

    -- Đảm bảo thời gian kết thúc phải sau thời gian bắt đầu
    CONSTRAINT chk_events_time CHECK (end_time > start_time),

    -- Index tối ưu lọc sự kiện theo trạng thái và thời gian diễn ra
    INDEX idx_events_status_start (status, start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ==========================================
-- 2. Bảng: ticket_types (Hạng vé & Tồn kho)
-- ==========================================
CREATE TABLE ticket_types (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    event_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(255) NOT NULL,
    price DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    total_quota INT UNSIGNED NOT NULL DEFAULT 0,
    available_quantity INT UNSIGNED NOT NULL DEFAULT 0,
    reserved_quantity INT UNSIGNED NOT NULL DEFAULT 0,
    sold_quantity INT UNSIGNED NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, INACTIVE, SOLD_OUT',

    -- Khóa ngoại nối tới bảng events
    CONSTRAINT fk_ticket_types_event
        FOREIGN KEY (event_id) REFERENCES events(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,

    -- Ràng buộc giá không âm và tổng tồn kho luôn cân bằng (chống bán âm vé)
    CONSTRAINT chk_ticket_types_price CHECK (price >= 0),
    CONSTRAINT chk_ticket_types_quota_balance CHECK (
        total_quota = available_quantity + reserved_quantity + sold_quantity
    ),

    -- Index tối ưu khi load danh sách hạng vé của 1 sự kiện
    INDEX idx_ticket_types_event_status (event_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ==========================================
-- 3. Bảng: reservations (Phiên giữ chỗ tạm thời)
-- ==========================================
CREATE TABLE reservations (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT UNSIGNED NOT NULL COMMENT 'ID tham chiếu sang bảng orders bên Order Service',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, CONFIRMED, EXPIRED, CANCELLED',
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Index phục vụ tra cứu phiên giữ chỗ theo order_id
    INDEX idx_reservations_order_id (order_id),

    -- Index cho Cronjob/Worker quét các phiên giữ chỗ quá hạn để hoàn lại available_quantity
    INDEX idx_reservations_status_expires (status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ==========================================
-- 4. Bảng: reservation_items (Chi tiết vé giữ chỗ)
-- ==========================================
CREATE TABLE reservation_items (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    reservation_id BIGINT UNSIGNED NOT NULL,
    ticket_type_id BIGINT UNSIGNED NOT NULL,
    quantity INT UNSIGNED NOT NULL,

    -- Khóa ngoại nội bộ
    CONSTRAINT fk_res_items_reservation
        FOREIGN KEY (reservation_id) REFERENCES reservations(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_res_items_ticket_type
        FOREIGN KEY (ticket_type_id) REFERENCES ticket_types(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,

    -- Tránh trùng lặp cùng 1 hạng vé thành nhiều dòng trong cùng 1 phiên giữ chỗ
    CONSTRAINT uq_res_items_res_type UNIQUE (reservation_id, ticket_type_id),

    -- Số lượng giữ chỗ phải lớn hơn 0
    CONSTRAINT chk_res_items_quantity CHECK (quantity > 0),

    INDEX idx_res_items_ticket_type_id (ticket_type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ==========================================
-- 5. Bảng: tickets (Vé điện tử đã phát hành)
-- ==========================================
CREATE TABLE tickets (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    ticket_type_id BIGINT UNSIGNED NOT NULL,
    event_id BIGINT UNSIGNED NOT NULL,
    owner_user_id BIGINT UNSIGNED NOT NULL COMMENT 'ID người sở hữu vé (tham chiếu User Service)',
    order_id BIGINT UNSIGNED NOT NULL COMMENT 'ID đơn hàng (tham chiếu Order Service)',
    qr_code_value VARCHAR(512) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ISSUED' COMMENT 'ISSUED, CHECKED_IN, CHECKED_OUT, CANCELLED',
    issued_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    checked_in_at DATETIME NULL,
    checked_out_at DATETIME NULL,
    cancelled_at DATETIME NULL,

    -- Mã QR của mỗi vé phải là duy nhất trên toàn hệ thống
    CONSTRAINT uq_tickets_qr_code_value UNIQUE (qr_code_value),

    -- Khóa ngoại nội bộ
    CONSTRAINT fk_tickets_ticket_type
        FOREIGN KEY (ticket_type_id) REFERENCES ticket_types(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_tickets_event
        FOREIGN KEY (event_id) REFERENCES events(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,

    -- Các Index phục vụ tra cứu vé theo User, theo Đơn hàng và thống kê tại Sự kiện
    INDEX idx_tickets_owner_user (owner_user_id, issued_at DESC),
    INDEX idx_tickets_order_id (order_id),
    INDEX idx_tickets_event_status (event_id, status),
    INDEX idx_tickets_type_id (ticket_type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ==========================================
-- 6. Bảng: ticket_scans (Nhật ký quét mã QR tại cổng)
-- ==========================================
CREATE TABLE ticket_scans (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    ticket_id BIGINT UNSIGNED NOT NULL,
    gate_id BIGINT UNSIGNED NOT NULL COMMENT 'ID cổng soát vé',
    scan_type VARCHAR(30) NOT NULL COMMENT 'CHECK_IN, CHECK_OUT',
    scanned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Khóa ngoại nối tới bảng tickets
    CONSTRAINT fk_ticket_scans_ticket
        FOREIGN KEY (ticket_id) REFERENCES tickets(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,

    -- Index truy xuất nhanh lịch sử quét của 1 vé hoặc thống kê lưu lượng theo cổng
    INDEX idx_ticket_scans_ticket_time (ticket_id, scanned_at DESC),
    INDEX idx_ticket_scans_gate_time (gate_id, scanned_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ==========================================
-- 7. Bảng: processed_events (Chống xử lý trùng Message/Event)
-- ==========================================
CREATE TABLE processed_events (
    event_id VARCHAR(128) PRIMARY KEY COMMENT 'Message ID / Event UUID từ Message Broker',
    processed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Index hỗ trợ dọn dẹp (cleanup) các event log cũ định kỳ
    INDEX idx_processed_events_time (processed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;