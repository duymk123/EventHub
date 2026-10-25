-- Khởi tạo Database (tuỳ chọn)
CREATE DATABASE IF NOT EXISTS ticketing_service
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE ticketing_service;

-- ==========================================
-- 1. Bảng: ticket_types (Phân loại vé)
-- ==========================================
CREATE TABLE ticket_types (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    event_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(255) NOT NULL,
    price DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    total_quota INT UNSIGNED NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, INACTIVE, SOLD_OUT',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- Ràng buộc logic dữ liệu
    CONSTRAINT chk_ticket_types_price CHECK (price >= 0),

    -- Index tối ưu khi load danh sách loại vé theo sự kiện
    INDEX idx_ticket_types_event_status (event_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ==========================================
-- 2. Bảng: ticket_inventory (Tồn kho vé - Quan hệ 1-1 với ticket_types)
-- ==========================================
CREATE TABLE ticket_inventory (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    ticket_type_id BIGINT UNSIGNED NOT NULL,
    total_quantity INT UNSIGNED NOT NULL DEFAULT 0,
    available_quantity INT UNSIGNED NOT NULL DEFAULT 0,
    reserved_quantity INT UNSIGNED NOT NULL DEFAULT 0,
    sold_quantity INT UNSIGNED NOT NULL DEFAULT 0,
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Dùng cho Optimistic Locking chống overselling',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- Đảm bảo quan hệ 1-1 chặt chẽ: mỗi loại vé chỉ có duy nhất 1 dòng tồn kho
    CONSTRAINT uq_inventory_ticket_type UNIQUE (ticket_type_id),

    -- Khóa ngoại
    CONSTRAINT fk_inventory_ticket_type
        FOREIGN KEY (ticket_type_id) REFERENCES ticket_types(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    -- Đảm bảo tổng tồn kho luôn khớp, không bao giờ bị âm (chống bán lố ở tầng DB)
    CONSTRAINT chk_inventory_balance
        CHECK (total_quantity = available_quantity + reserved_quantity + sold_quantity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ==========================================
-- 3. Bảng: reservations (Phiên giữ chỗ tạm thời)
-- ==========================================
CREATE TABLE reservations (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    reservation_code VARCHAR(64) NOT NULL,
    order_id BIGINT UNSIGNED NULL COMMENT 'Có thể NULL lúc mới giữ chỗ, cập nhật khi tạo Order',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, CONFIRMED, EXPIRED, CANCELLED',
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- Mã giữ chỗ phải là duy nhất
    CONSTRAINT uq_reservations_code UNIQUE (reservation_code),

    -- Index phục vụ tra cứu theo đơn hàng
    INDEX idx_reservations_order_id (order_id),

    -- Composite Index cực kỳ quan trọng cho Cronjob/Worker quét các phiên hết hạn để nhả vé
    INDEX idx_reservations_status_expires (status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ==========================================
-- 4. Bảng: reservation_items (Chi tiết các loại vé trong phiên giữ chỗ)
-- ==========================================
CREATE TABLE reservation_items (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    reservation_id BIGINT UNSIGNED NOT NULL,
    ticket_type_id BIGINT UNSIGNED NOT NULL,
    quantity INT UNSIGNED NOT NULL,
    unit_price DECIMAL(12, 2) NOT NULL,

    -- Tránh việc 1 loại vé bị tách thành 2 dòng trùng lặp trong cùng 1 phiên giữ chỗ
    CONSTRAINT uq_res_items_res_type UNIQUE (reservation_id, ticket_type_id),

    -- Khóa ngoại
    CONSTRAINT fk_res_items_reservation
        FOREIGN KEY (reservation_id) REFERENCES reservations(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_res_items_ticket_type
        FOREIGN KEY (ticket_type_id) REFERENCES ticket_types(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,

    -- Ràng buộc số lượng và đơn giá hợp lệ
    CONSTRAINT chk_res_items_quantity CHECK (quantity > 0),
    CONSTRAINT chk_res_items_price CHECK (unit_price >= 0),

    -- Index cho khóa ngoại thứ 2 (reservation_id đã đứng đầu trong UNIQUE constraint ở trên)
    INDEX idx_res_items_ticket_type (ticket_type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ==========================================
-- 5. Bảng: tickets (Vé điện tử xuất cho khách sau khi thanh toán)
-- ==========================================
CREATE TABLE tickets (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    ticket_code VARCHAR(64) NOT NULL,
    order_id BIGINT UNSIGNED NOT NULL,
    ticket_type_id BIGINT UNSIGNED NOT NULL,
    qr_code VARCHAR(512) NOT NULL COMMENT 'Chuỗi mã hóa hoặc payload của mã QR',
    status VARCHAR(30) NOT NULL DEFAULT 'VALID' COMMENT 'VALID, USED, CANCELLED, REFUNDED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- Mã vé và mã QR bắt buộc duy nhất trên toàn hệ thống
    CONSTRAINT uq_tickets_ticket_code UNIQUE (ticket_code),
    CONSTRAINT uq_tickets_qr_code UNIQUE (qr_code),

    -- Khóa ngoại
    CONSTRAINT fk_tickets_ticket_type
        FOREIGN KEY (ticket_type_id) REFERENCES ticket_types(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,

    -- Index phục vụ hiển thị danh sách vé trong đơn hàng của khách
    INDEX idx_tickets_order_id (order_id),
    -- Index phục vụ thống kê vé theo loại và trạng thái
    INDEX idx_tickets_type_status (ticket_type_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ==========================================
-- 6. Bảng: ticket_check_logs (Lịch sử quét vé / soát vé tại cổng)
-- ==========================================
CREATE TABLE ticket_check_logs (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    ticket_id BIGINT UNSIGNED NOT NULL,
    action VARCHAR(50) NOT NULL COMMENT 'CHECK_IN, CHECK_OUT, VERIFY',
    checked_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    result VARCHAR(50) NOT NULL COMMENT 'SUCCESS, ALREADY_USED, INVALID_STATUS, EXPIRED',

    -- Khóa ngoại
    CONSTRAINT fk_check_logs_ticket
        FOREIGN KEY (ticket_id) REFERENCES tickets(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,

    -- Composite Index giúp truy xuất nhanh lịch sử quét của 1 vé theo thứ tự thời gian mới nhất
    INDEX idx_check_logs_ticket_time (ticket_id, checked_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;