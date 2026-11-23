-- HELPER cho database cũ do Hibernate ddl-auto: update tạo, trước khi event-service chuyển sang Flyway.
-- File KHÔNG có "V<n>__" nên Flyway không chạy tự động và không ghi vào flyway_schema_history.
-- Đường dẫn chuẩn KHÔNG cần file này: build cũ (image chưa có Flyway) chạy một lần với
--   --spring.jpa.hibernate.ddl-auto=update để Hibernate thêm cột/index/FK, rồi baseline như EVENT-STATUS.md §7
--   và chuyển sang validate.
-- File này để thay cho bước "build cũ" khi chỉ có artifact mới (vd. bản chạy pipeline) hoặc muốn sửa schema
-- mà không kéo image cũ về. Chạy tay:
--   mysql -h127.0.0.1 -P3306 -uroot -p eventhub_event < event-service/src/main/resources/db/migration/helper_legacy_datetime.sql
--
-- DATETIME(6) đổi cấu trúc cột (ALTER ... MODIFY trên bảng lớn giữ lock), MySQL không có
-- "DROP INDEX IF EXISTS", và FK chặn xoá categories; vậy helper kiểm tra trước từng bước và dừng với lỗi
-- rõ ràng thay vì sửa nửa chừng. Chạy lại sau khi sửa dữ liệu là an toàn: bước đã xong được bỏ qua.
--
-- Nhận diện DB cũ theo cấu trúc, không theo DEFAULT: Hibernate tạo status kiểu ENUM, không tạo FK/CHECK/
-- UNIQUE trên events/categories, còn V1 dùng VARCHAR(20) cho status. Mọi bước đều kiểm tra "nếu thiếu" nên
-- chạy trên schema đã đúng chỉ ghi "skipped", không tạo bảng/procedure thừa ngoài _fh tạm.

DROP PROCEDURE IF EXISTS eventhub_upgrade_legacy_datetime;
DROP TABLE IF EXISTS _fh;
CREATE TABLE IF NOT EXISTS _fh (
  step VARCHAR(60) NOT NULL,
  detail VARCHAR(255) NOT NULL
) ENGINE=InnoDB;

DELIMITER //
CREATE PROCEDURE eventhub_upgrade_legacy_datetime()
proc: BEGIN
  DECLARE legacy INT DEFAULT 0;
  DECLARE category_fks INT DEFAULT 0;

  IF (SELECT COUNT(*) FROM information_schema.TABLES
       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('categories', 'events')) < 2 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Thiếu bảng categories/events: DB rỗng thì để Flyway chạy V1, không chạy helper này.';
  END IF;

  -- DB cũ: status là ENUM (Hibernate) thay vì VARCHAR(20) như V1.
  SELECT COUNT(*) INTO legacy FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'events' AND COLUMN_NAME = 'status'
     AND DATA_TYPE = 'enum';
  -- DB cũ cũng có thể đã là VARCHAR nhưng thiếu FK/CHECK/index/UNIQUE của V1.
  IF legacy = 0 AND (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
                     WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'events'
                       AND CONSTRAINT_NAME = 'fk_events_category') = 0 THEN
    SET legacy = 1;
  END IF;
  IF legacy = 0 THEN
    INSERT INTO _fh VALUES ('skipped', 'Không thấy cấu trúc legacy: schema đã đồng bộ V1, không cần sửa.');
    LEAVE proc;
  END IF;

  -- 1. Dữ liệu mà service không bao giờ tạo (start >= end) phải sửa tay trước; CHECK ở bước 3 sẽ chặn.
  IF (SELECT COUNT(*) FROM events WHERE start_time >= end_time) > 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
      'Có event với start_time >= end_time; sửa dữ liệu trước khi chạy helper (bước 1).';
  END IF;
  INSERT INTO _fh VALUES ('1. time-rows', 'OK');

  -- 2. Đổi kiểu trước khi thêm CHECK. DATETIME(6) không có logic timezone của TIMESTAMP; Connector/J ghi
  --    giờ UTC nên value hiển thị trong MySQL giữ nguyên. MODIFY bỏ DEFAULT/ON UPDATE của Hibernate.
  ALTER TABLE categories MODIFY created_at DATETIME(6) NOT NULL,
                         MODIFY updated_at DATETIME(6) NULL;
  UPDATE categories SET updated_at = created_at WHERE updated_at IS NULL;
  ALTER TABLE categories MODIFY updated_at DATETIME(6) NOT NULL;
  INSERT INTO _fh VALUES ('2. categories.datetime(6)', 'OK');

  ALTER TABLE events MODIFY created_at DATETIME(6) NOT NULL,
                     MODIFY updated_at DATETIME(6) NULL;
  UPDATE events SET updated_at = created_at WHERE updated_at IS NULL;
  ALTER TABLE events MODIFY updated_at DATETIME(6) NOT NULL,
                     MODIFY start_time DATETIME(6) NOT NULL,
                     MODIFY end_time DATETIME(6) NOT NULL;
  -- Hibernate tạo status kiểu ENUM; V1 dùng VARCHAR(20) với cùng tập giá trị nên đổi được mà không mất dữ liệu
  -- (giá trị lạ sẽ bị MySQL từ chối). Kiểm tra DATA_TYPE để chạy lại không lỗi.
  IF (SELECT DATA_TYPE FROM information_schema.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'events' AND COLUMN_NAME = 'status') = 'enum' THEN
    ALTER TABLE events MODIFY status VARCHAR(20) NOT NULL DEFAULT 'DRAFT';
  END IF;
  INSERT INTO _fh VALUES ('2. events.datetime(6)', 'OK');

  -- 3. FK chưa đặt tên của Hibernate được thay bằng fk_events_category (tên cố định, dễ so sánh schema).
  SELECT COUNT(*) INTO category_fks FROM information_schema.TABLE_CONSTRAINTS
   WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'events'
     AND CONSTRAINT_TYPE = 'FOREIGN KEY' AND CONSTRAINT_NAME <> 'fk_events_category';
  IF category_fks > 0 THEN
    SET @drop_fks = (SELECT GROUP_CONCAT(CONCAT('DROP FOREIGN KEY ', CONSTRAINT_NAME) SEPARATOR ', ')
                       FROM information_schema.TABLE_CONSTRAINTS
                      WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'events'
                        AND CONSTRAINT_TYPE = 'FOREIGN KEY' AND CONSTRAINT_NAME <> 'fk_events_category');
    SET @sql = CONCAT('ALTER TABLE events ', @drop_fks);
    PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
                 WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'events'
                   AND CONSTRAINT_NAME = 'fk_events_category') THEN
    ALTER TABLE events ADD CONSTRAINT fk_events_category FOREIGN KEY (category_id) REFERENCES categories (id);
  END IF;
  INSERT INTO _fh VALUES ('3. fk_events_category', 'OK');

  -- CHECK_CLAUSE nằm ở information_schema.CHECK_CONSTRAINTS, ghép theo tên constraint trong cùng schema;
  -- MySQL trả dấu \' lồng trong literal nên bỏ ký tự \ trước khi so.
  IF NOT EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS tc
                   JOIN information_schema.CHECK_CONSTRAINTS cc
                     ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
                 WHERE tc.CONSTRAINT_SCHEMA = DATABASE() AND tc.TABLE_NAME = 'events'
                   AND tc.CONSTRAINT_TYPE = 'CHECK' AND REPLACE(cc.CHECK_CLAUSE, '\\', '') LIKE '(%''DRAFT''%') THEN
    ALTER TABLE events ADD CONSTRAINT chk_events_status
      CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','PUBLISHED','CLOSED'));
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS tc
                   JOIN information_schema.CHECK_CONSTRAINTS cc
                     ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
                 WHERE tc.CONSTRAINT_SCHEMA = DATABASE() AND tc.TABLE_NAME = 'events'
                   AND tc.CONSTRAINT_TYPE = 'CHECK' AND REPLACE(cc.CHECK_CLAUSE, '\\', '') LIKE '%`start_time` < `end_time`%') THEN
    ALTER TABLE events ADD CONSTRAINT chk_events_time CHECK (start_time < end_time);
  END IF;
  INSERT INTO _fh VALUES ('3. check events', 'OK');

  -- Một UNIQUE trên đúng cột name (bất kể tên index) đã đủ đáp ứng UNIQUE(name).
  IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'categories'
                   AND COLUMN_NAME = 'name' AND NON_UNIQUE = 0 AND SEQ_IN_INDEX = 1
                   AND INDEX_NAME <> 'PRIMARY') THEN
    ALTER TABLE categories ADD UNIQUE KEY uk_categories_name (name);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'events'
                   AND INDEX_NAME = 'idx_events_public') THEN
    ALTER TABLE events ADD KEY idx_events_public (status, start_time);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'events'
                   AND INDEX_NAME = 'idx_events_owner') THEN
    ALTER TABLE events ADD KEY idx_events_owner (organizer_id, created_at);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'events'
                   AND INDEX_NAME = 'fk_events_category') THEN
    ALTER TABLE events ADD KEY fk_events_category (category_id);
  END IF;
  INSERT INTO _fh VALUES ('3. index', 'OK');

  INSERT INTO _fh VALUES ('done', 'Schema đã khớp V1. Chạy service một lần với --spring.flyway.baseline-on-migrate=true --spring.flyway.baseline-version=1 rồi khởi động lại bình thường.');
  LEAVE proc;
END //
DELIMITER ;

CALL eventhub_upgrade_legacy_datetime();
DROP PROCEDURE IF EXISTS eventhub_upgrade_legacy_datetime;
SELECT * FROM _fh;
DROP TABLE IF EXISTS _fh;
