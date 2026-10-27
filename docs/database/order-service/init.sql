-- Khởi tạo Database cho Order Service
CREATE DATABASE IF NOT EXISTS eventhub_order
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE eventhub_order;

-- Xóa bảng cũ nếu cần chạy lại từ đầu (theo thứ tự bảng con trước, bảng cha sau)
DROP TABLE IF EXISTS refunds;
DROP TABLE IF EXISTS payments;
DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS orders;

-- ==========================================
-- 1. Bảng: orders
-- ==========================================
CREATE TABLE orders (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    total_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    payment_status VARCHAR(30) NOT NULL DEFAULT 'UNPAID',
    idempotency_key VARCHAR(100) NOT NULL,
    expired_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- Chống tạo đơn hàng trùng lặp khi người dùng bấm thanh toán nhiều lần
    CONSTRAINT uq_orders_idempotency_key UNIQUE (idempotency_key),

    -- Đảm bảo tổng tiền không âm
    CONSTRAINT chk_orders_total_amount CHECK (total_amount >= 0),

    -- Index tối ưu tra cứu đơn theo người dùng và quét đơn hết hạn thanh toán
    INDEX idx_orders_user_created (user_id, created_at DESC),
    INDEX idx_orders_status_expired (status, expired_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ==========================================
-- 2. Bảng: order_items
-- ==========================================
CREATE TABLE order_items (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT UNSIGNED NOT NULL,
    ticket_type_id BIGINT UNSIGNED NOT NULL COMMENT 'ID tham chiếu sang Ticket Service, không tạo FK liên DB',
    quantity INT UNSIGNED NOT NULL,
    unit_price DECIMAL(12, 2) NOT NULL,
    subtotal DECIMAL(12, 2) NOT NULL,

    -- Khóa ngoại nội bộ nối tới bảng orders
    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id) REFERENCES orders(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    -- Mỗi loại vé chỉ nằm trên 1 dòng trong cùng 1 đơn hàng
    CONSTRAINT uq_order_items_order_ticket UNIQUE (order_id, ticket_type_id),

    -- Đảm bảo số lượng, đơn giá và thành tiền hợp lệ
    CONSTRAINT chk_order_items_calc CHECK (
        quantity > 0 AND
        unit_price >= 0 AND
        subtotal = quantity * unit_price
    ),

    -- Index phục vụ thống kê theo loại vé
    INDEX idx_order_items_ticket_type_id (ticket_type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ==========================================
-- 3. Bảng: payments
-- ==========================================
CREATE TABLE payments (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT UNSIGNED NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    transaction_id VARCHAR(150) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- Khóa ngoại nội bộ nối tới bảng orders
    CONSTRAINT fk_payments_order
        FOREIGN KEY (order_id) REFERENCES orders(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,

    -- Mã giao dịch từ cổng thanh toán phải duy nhất (chống trùng lặp khi nhận Webhook/IPN)
    CONSTRAINT uq_payments_transaction_id UNIQUE (transaction_id),

    CONSTRAINT chk_payments_amount CHECK (amount >= 0),

    INDEX idx_payments_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ==========================================
-- 4. Bảng: refunds
-- ==========================================
CREATE TABLE refunds (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT UNSIGNED NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- Khóa ngoại nội bộ nối tới bảng orders
    CONSTRAINT fk_refunds_order
        FOREIGN KEY (order_id) REFERENCES orders(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,

    CONSTRAINT chk_refunds_amount CHECK (amount > 0),

    INDEX idx_refunds_order_id (order_id),
    INDEX idx_refunds_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;