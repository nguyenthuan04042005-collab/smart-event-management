DROP DATABASE IF EXISTS smart_event_management;

CREATE DATABASE smart_event_management
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE smart_event_management;
SET NAMES utf8mb4;
SET time_zone = '+07:00';

-- Tai khoan dang nhap, mat khau chi luu duoi dang BCrypt.
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(32) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    email VARCHAR(190) NOT NULL,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    password_hash VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_users_username (username),
    UNIQUE KEY uq_users_email (email)
) ENGINE=InnoDB;

-- Cau hinh thu di do Admin quan ly; mat khau SMTP duoc ma hoa truoc khi luu.
CREATE TABLE mail_settings (
    id BIGINT PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    host VARCHAR(255) NOT NULL,
    port INT NOT NULL,
    username VARCHAR(190) NOT NULL,
    from_address VARCHAR(190) NOT NULL,
    encrypted_password VARCHAR(1000),
    updated_at DATETIME NOT NULL,
    CHECK (port BETWEEN 1 AND 65535)
) ENGINE=InnoDB;

-- Lich su cac lan gui thu; SMTP chap nhan khong dong nghia nguoi nhan da doc thu.
CREATE TABLE mail_deliveries (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    message_type VARCHAR(40) NOT NULL,
    recipient VARCHAR(190) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    error_code VARCHAR(40) NULL,
    created_at DATETIME NOT NULL,
    completed_at DATETIME NULL,
    CHECK (status IN ('PENDING', 'SMTP_ACCEPTED', 'FAILED')),
    INDEX idx_mail_deliveries_created (created_at)
) ENGINE=InnoDB;

-- Ma xac minh email va dat lai mat khau chi luu ban bam, co han va dung mot lan.
CREATE TABLE account_tokens (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    email_address VARCHAR(190) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    purpose VARCHAR(20) NOT NULL,
    expires_at DATETIME NOT NULL,
    used_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id),
    UNIQUE KEY uq_account_tokens_hash (token_hash),
    INDEX idx_account_tokens_user (user_id, purpose, created_at),
    CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD'))
) ENGINE=InnoDB;

-- JWT đã đăng xuất không còn hiệu lực trước khi hết hạn.
CREATE TABLE revoked_jwts (
    token_id VARCHAR(36) PRIMARY KEY,
    expires_at DATETIME NOT NULL,
    INDEX idx_revoked_jwts_expires (expires_at)
) ENGINE=InnoDB;

-- Yêu cầu đăng ký/đổi email tạm; chỉ ghi vào users sau khi nhập đúng mã.
CREATE TABLE pending_email_actions (
    request_id VARCHAR(36) PRIMARY KEY,
    purpose VARCHAR(20) NOT NULL,
    user_id BIGINT NULL,
    previous_email VARCHAR(190) NULL,
    username VARCHAR(32) NULL,
    display_name VARCHAR(80) NULL,
    email VARCHAR(190) NOT NULL,
    password_hash VARCHAR(255) NULL,
    code_hash CHAR(64) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    expires_at DATETIME NOT NULL,
    last_sent_at DATETIME NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_pending_email_created (email, created_at),
    CHECK (purpose IN ('SIGNUP', 'CHANGE_EMAIL'))
) ENGINE=InnoDB;

CREATE TABLE roles (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(20) NOT NULL,
    UNIQUE KEY uq_roles_name (name)
) ENGINE=InnoDB;

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (role_id) REFERENCES roles(id)
) ENGINE=InnoDB;

INSERT INTO roles (name) VALUES ('ADMIN'), ('ORGANIZER'), ('STAFF'), ('ATTENDEE');

-- Tai khoan demo; mat khau goc duoc ghi trong README, CSDL chi luu BCrypt.
INSERT INTO users (username, display_name, email, password_hash) VALUES
('admin', 'Quản trị viên', 'admin@sems.example',
 '$2a$10$0iAD8uG1OBJ0dm/mwT3Y/.AfviEMGu5pcRnu1.edRyIx3NQm45JSK'),
('organizer', 'Ban tổ chức sự kiện', 'organizer@sems.example',
 '$2a$10$cPoeI4Fn2nL6tDdEsVxfeewJ0gD3AN5sTNlMARWkCrtbd5ScQVjWO'),
('organizer2', 'Câu lạc bộ Sáng tạo', 'organizer2@sems.example',
 '$2a$10$wBudmgukmhFmgim5XnaF/Oa8RL6qEG81rbf08ye52tDtlIYo2XufC'),
('staff', 'Nhân viên check-in 1', 'staff@sems.example',
 '$2a$10$hjejHnJHuHSq5QrOvsjA5.sElIo4fO7meYwew6/AtYEYj5pNCftnq'),
('staff2', 'Nhân viên check-in 2', 'staff2@sems.example',
 '$2a$10$3AXCR8jN7U1n9DrfGK.Q/e4pIpDkn55uz5PkhTjvrNEosT89eWLxO'),
('khach1', 'Nguyễn Minh An', 'khach1@sems.example',
 '$2a$10$3R1eKUaHLUKo3TrEzr5KiOUhrDFOfRk13EcXCFpUhO5xskXQ0FMnq'),
('khach2', 'Trần Hoài Bình', 'khach2@sems.example',
 '$2a$10$7Xcb1twFgGcfiQp5a6mYm.kbHZGQI3.1rC6jougzhTTbK8iuknp5K'),
('khach3', 'Lê Gia Hân', 'khach3@sems.example',
 '$2a$10$AhhuHhJlkTarwcVkZTMaVugW5TSEjub9AR7jPkiVFeLWVMgFYhZqe');

-- Muoi khach mau bo sung dung chung mat khau demo Khach1@123456.
INSERT INTO users (username, display_name, email, password_hash) VALUES
('khach4', 'Phạm Ngọc Lan', 'khach4@sems.example', '$2a$10$3R1eKUaHLUKo3TrEzr5KiOUhrDFOfRk13EcXCFpUhO5xskXQ0FMnq'),
('khach5', 'Đỗ Minh Quân', 'khach5@sems.example', '$2a$10$3R1eKUaHLUKo3TrEzr5KiOUhrDFOfRk13EcXCFpUhO5xskXQ0FMnq'),
('khach6', 'Vũ Thanh Trúc', 'khach6@sems.example', '$2a$10$3R1eKUaHLUKo3TrEzr5KiOUhrDFOfRk13EcXCFpUhO5xskXQ0FMnq'),
('khach7', 'Bùi Anh Tú', 'khach7@sems.example', '$2a$10$3R1eKUaHLUKo3TrEzr5KiOUhrDFOfRk13EcXCFpUhO5xskXQ0FMnq'),
('khach8', 'Ngô Thu Hà', 'khach8@sems.example', '$2a$10$3R1eKUaHLUKo3TrEzr5KiOUhrDFOfRk13EcXCFpUhO5xskXQ0FMnq'),
('khach9', 'Trịnh Đức Huy', 'khach9@sems.example', '$2a$10$3R1eKUaHLUKo3TrEzr5KiOUhrDFOfRk13EcXCFpUhO5xskXQ0FMnq'),
('khach10', 'Lý Hoàng Yến', 'khach10@sems.example', '$2a$10$3R1eKUaHLUKo3TrEzr5KiOUhrDFOfRk13EcXCFpUhO5xskXQ0FMnq'),
('khach11', 'Mai Quốc Bảo', 'khach11@sems.example', '$2a$10$3R1eKUaHLUKo3TrEzr5KiOUhrDFOfRk13EcXCFpUhO5xskXQ0FMnq'),
('khach12', 'Hoàng Khánh Chi', 'khach12@sems.example', '$2a$10$3R1eKUaHLUKo3TrEzr5KiOUhrDFOfRk13EcXCFpUhO5xskXQ0FMnq'),
('khach13', 'Phan Minh Đức', 'khach13@sems.example', '$2a$10$3R1eKUaHLUKo3TrEzr5KiOUhrDFOfRk13EcXCFpUhO5xskXQ0FMnq'),
('khach14', 'Trần Minh Ngọc', 'khach14@sems.example', '$2a$10$3R1eKUaHLUKo3TrEzr5KiOUhrDFOfRk13EcXCFpUhO5xskXQ0FMnq'),
('khach15', 'Nguyễn Quốc Khánh', 'khach15@sems.example', '$2a$10$3R1eKUaHLUKo3TrEzr5KiOUhrDFOfRk13EcXCFpUhO5xskXQ0FMnq'),
('khach16', 'Đặng Thùy Dương', 'khach16@sems.example', '$2a$10$3R1eKUaHLUKo3TrEzr5KiOUhrDFOfRk13EcXCFpUhO5xskXQ0FMnq'),
('khach17', 'Phạm Tuấn Kiệt', 'khach17@sems.example', '$2a$10$3R1eKUaHLUKo3TrEzr5KiOUhrDFOfRk13EcXCFpUhO5xskXQ0FMnq'),
('khach18', 'Lê Phương Anh', 'khach18@sems.example', '$2a$10$3R1eKUaHLUKo3TrEzr5KiOUhrDFOfRk13EcXCFpUhO5xskXQ0FMnq'),
('khach19', 'Vũ Đức Thành', 'khach19@sems.example', '$2a$10$3R1eKUaHLUKo3TrEzr5KiOUhrDFOfRk13EcXCFpUhO5xskXQ0FMnq'),
('khach20', 'Hoàng Mai Linh', 'khach20@sems.example', '$2a$10$3R1eKUaHLUKo3TrEzr5KiOUhrDFOfRk13EcXCFpUhO5xskXQ0FMnq'),
('khach21', 'Bùi Gia Huy', 'khach21@sems.example', '$2a$10$3R1eKUaHLUKo3TrEzr5KiOUhrDFOfRk13EcXCFpUhO5xskXQ0FMnq'),
('khach22', 'Đỗ Ngọc Ánh', 'khach22@sems.example', '$2a$10$3R1eKUaHLUKo3TrEzr5KiOUhrDFOfRk13EcXCFpUhO5xskXQ0FMnq'),
('khach23', 'Nguyễn Văn Phúc', 'khach23@sems.example', '$2a$10$3R1eKUaHLUKo3TrEzr5KiOUhrDFOfRk13EcXCFpUhO5xskXQ0FMnq'),
('khach24', 'Trương Bảo Vy', 'khach24@sems.example', '$2a$10$3R1eKUaHLUKo3TrEzr5KiOUhrDFOfRk13EcXCFpUhO5xskXQ0FMnq'),
('khach25', 'Lương Minh Nhật', 'khach25@sems.example', '$2a$10$3R1eKUaHLUKo3TrEzr5KiOUhrDFOfRk13EcXCFpUhO5xskXQ0FMnq'),
('khach26', 'Phan Thu Trang', 'khach26@sems.example', '$2a$10$3R1eKUaHLUKo3TrEzr5KiOUhrDFOfRk13EcXCFpUhO5xskXQ0FMnq'),
('khach27', 'Trịnh Anh Dũng', 'khach27@sems.example', '$2a$10$3R1eKUaHLUKo3TrEzr5KiOUhrDFOfRk13EcXCFpUhO5xskXQ0FMnq'),
('khach28', 'Mai Hà My', 'khach28@sems.example', '$2a$10$3R1eKUaHLUKo3TrEzr5KiOUhrDFOfRk13EcXCFpUhO5xskXQ0FMnq'),
('khach29', 'Ngô Quang Vinh', 'khach29@sems.example', '$2a$10$3R1eKUaHLUKo3TrEzr5KiOUhrDFOfRk13EcXCFpUhO5xskXQ0FMnq');

-- Tài khoản mẫu dùng địa chỉ .example và được đánh dấu đã xác minh để demo.
UPDATE users SET email_verified = TRUE WHERE id >= 1;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.name = 'ADMIN' WHERE u.username = 'admin';

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.name = 'STAFF' WHERE u.username IN ('staff', 'staff2');

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.name = 'ORGANIZER'
WHERE u.username IN ('organizer', 'organizer2');

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.name = 'ATTENDEE'
WHERE u.username IN ('khach1', 'khach2', 'khach3');

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.name = 'ATTENDEE'
WHERE u.username IN ('khach4', 'khach5', 'khach6', 'khach7', 'khach8',
                     'khach9', 'khach10', 'khach11', 'khach12', 'khach13',
                     'khach14', 'khach15', 'khach16', 'khach17', 'khach18',
                     'khach19', 'khach20', 'khach21', 'khach22', 'khach23',
                     'khach24', 'khach25', 'khach26', 'khach27', 'khach28', 'khach29');

-- Dia diem va suc chua vat ly (BR02).
CREATE TABLE venues (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    organizer_id BIGINT NOT NULL,
    name VARCHAR(160) NOT NULL,
    address VARCHAR(255) NOT NULL,
    capacity INT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    FOREIGN KEY (organizer_id) REFERENCES users(id),
    CHECK (capacity > 0),
    INDEX idx_venues_organizer (organizer_id, name)
) ENGINE=InnoDB;

-- Su kien duoc ban to chuc tao va cap nhat.
CREATE TABLE events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    venue_id BIGINT NULL,
    organizer_id BIGINT NOT NULL,
    title VARCHAR(180) NOT NULL,
    description TEXT,
    banner_url VARCHAR(500),
    category VARCHAR(30) NOT NULL DEFAULT 'OTHER',
    online_url VARCHAR(500),
    approval_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    review_note VARCHAR(500),
    lifecycle_status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    cancel_reason VARCHAR(500),
    cancelled_at DATETIME NULL,
    cancelled_by_id BIGINT NULL,
    deleted_at DATETIME NULL,
    starts_at DATETIME NOT NULL,
    ends_at DATETIME NOT NULL,
    capacity INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (venue_id) REFERENCES venues(id),
    FOREIGN KEY (organizer_id) REFERENCES users(id),
    FOREIGN KEY (cancelled_by_id) REFERENCES users(id),
    CHECK (capacity > 0),
    CHECK (ends_at >= DATE_ADD(starts_at, INTERVAL 30 MINUTE)),
    CHECK (status IN ('OPEN', 'CLOSED')),
    CHECK (approval_status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED')),
    CHECK (lifecycle_status IN ('SCHEDULED', 'CANCELLED')),
    CHECK (category IN ('TECHNOLOGY', 'ENTERTAINMENT', 'EDUCATION', 'OTHER')),
    CHECK (venue_id IS NOT NULL OR online_url IS NOT NULL),
    INDEX idx_events_organizer (organizer_id, approval_status),
    INDEX idx_events_time (starts_at, ends_at)
) ENGINE=InnoDB;

-- Lich su phe duyet giu lai moi quyet dinh, ke ca khi su kien duoc gui lai.
CREATE TABLE event_reviews (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id BIGINT NOT NULL,
    reviewer_id BIGINT NOT NULL,
    previous_status VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    note VARCHAR(500),
    reviewed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (event_id) REFERENCES events(id),
    FOREIGN KEY (reviewer_id) REFERENCES users(id),
    CHECK (previous_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CHECK (status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED')),
    INDEX idx_event_reviews_event (event_id, reviewed_at)
) ENGINE=InnoDB;

-- Nhan vien chi duoc tim khach va check-in tai su kien duoc phan cong.
CREATE TABLE event_staff (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id BIGINT NOT NULL,
    staff_id BIGINT NOT NULL,
    FOREIGN KEY (event_id) REFERENCES events(id),
    FOREIGN KEY (staff_id) REFERENCES users(id),
    UNIQUE KEY uq_event_staff (event_id, staff_id),
    INDEX idx_event_staff_staff (staff_id, event_id)
) ENGINE=InnoDB;

-- Lich trinh va dien gia cua tung su kien; thong tin nay duoc duyet cung su kien.
CREATE TABLE agenda_items (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id BIGINT NOT NULL,
    title VARCHAR(180) NOT NULL,
    starts_at DATETIME NOT NULL,
    ends_at DATETIME NOT NULL,
    room_name VARCHAR(160),
    sort_order INT NOT NULL DEFAULT 0,
    FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE,
    CHECK (ends_at > starts_at),
    INDEX idx_agenda_event (event_id, sort_order)
) ENGINE=InnoDB;

CREATE TABLE speakers (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    biography VARCHAR(500),
    topic VARCHAR(180),
    image_url VARCHAR(500),
    is_vip BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE,
    INDEX idx_speakers_event (event_id)
) ENGINE=InnoDB;

-- Nguoi tham du co the dang ky nhieu su kien khac nhau.
CREATE TABLE attendees (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(190) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    organization VARCHAR(160) NULL,
    position_title VARCHAR(120) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_attendees_email (email),
    INDEX idx_attendees_phone (phone)
) ENGINE=InnoDB;

-- Moi su kien chi co mot hang ve mien phi trong ban demo.
CREATE TABLE ticket_types (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id BIGINT NOT NULL,
    name VARCHAR(80) NOT NULL DEFAULT 'Ve mien phi',
    quota INT NOT NULL,
    price DECIMAL(12,2) NOT NULL DEFAULT 0,
    FOREIGN KEY (event_id) REFERENCES events(id),
    UNIQUE KEY uq_ticket_types_event (event_id),
    CHECK (quota > 0),
    CHECK (price = 0)
) ENGINE=InnoDB;

-- Moi ve thuoc tai khoan da dang ky; ma code chi dung de tra cuu trong pham vi tai khoan do.
CREATE TABLE tickets (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id BIGINT NOT NULL,
    ticket_type_id BIGINT NOT NULL,
    attendee_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    code VARCHAR(64) NOT NULL,
    qr_token VARCHAR(700) NOT NULL,
    email_key VARCHAR(190) NOT NULL,
    phone_key VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ISSUED',
    issued_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    checked_in_at DATETIME NULL,
    cancelled_at DATETIME NULL,
    -- MySQL cho phép nhiều NULL trong UNIQUE: vé hủy giữ lịch sử nhưng không giữ chỗ.
    active_user_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN status = 'CANCELLED' THEN NULL ELSE user_id END) STORED,
    active_email_key VARCHAR(190) GENERATED ALWAYS AS
        (CASE WHEN status = 'CANCELLED' THEN NULL ELSE email_key END) STORED,
    active_phone_key VARCHAR(20) GENERATED ALWAYS AS
        (CASE WHEN status = 'CANCELLED' THEN NULL ELSE phone_key END) STORED,
    FOREIGN KEY (event_id) REFERENCES events(id),
    FOREIGN KEY (ticket_type_id) REFERENCES ticket_types(id),
    FOREIGN KEY (attendee_id) REFERENCES attendees(id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    UNIQUE KEY uq_tickets_code (code),
    UNIQUE KEY uq_tickets_event_user (event_id, active_user_id),
    UNIQUE KEY uq_tickets_event_email (event_id, active_email_key),
    UNIQUE KEY uq_tickets_event_phone (event_id, active_phone_key),
    CHECK (status IN ('ISSUED', 'CHECKED_IN', 'CANCELLED')),
    INDEX idx_tickets_event_status (event_id, status)
) ENGINE=InnoDB;

-- Hang doi chung cho email ve QR va thu moi danh gia.
CREATE TABLE mail_jobs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    ticket_id BIGINT NOT NULL,
    message_type VARCHAR(30) NOT NULL,
    recipient VARCHAR(190) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME NULL,
    last_error_code VARCHAR(40) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME NULL,
    FOREIGN KEY (ticket_id) REFERENCES tickets(id),
    UNIQUE KEY uq_mail_job_ticket_type (ticket_id, message_type),
    INDEX idx_mail_job_due (status, next_attempt_at),
    CHECK (message_type IN ('TICKET', 'SURVEY_INVITATION')),
    CHECK (status IN ('PENDING', 'PAUSED', 'SMTP_ACCEPTED', 'FAILED', 'SKIPPED')),
    CHECK (attempts BETWEEN 0 AND 3)
) ENGINE=InnoDB;

-- Chi luu check-in thanh cong; UNIQUE ngan hai luot thanh cong cho mot ve.
CREATE TABLE checkin_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    ticket_id BIGINT NOT NULL,
    event_id BIGINT NOT NULL,
    method VARCHAR(12) NOT NULL,
    operator_name VARCHAR(80) NOT NULL,
    checked_in_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (ticket_id) REFERENCES tickets(id),
    FOREIGN KEY (event_id) REFERENCES events(id),
    UNIQUE KEY uq_checkin_ticket (ticket_id),
    CHECK (method IN ('QR', 'MANUAL')),
    INDEX idx_checkin_event_time (event_id, checked_in_at)
) ENGINE=InnoDB;

-- Mot ve da check-in chi duoc danh gia mot lan (BR09, BR10).
CREATE TABLE feedback (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    ticket_id BIGINT NOT NULL,
    event_id BIGINT NOT NULL,
    rating INT NOT NULL,
    comment_text VARCHAR(1000),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (ticket_id) REFERENCES tickets(id),
    FOREIGN KEY (event_id) REFERENCES events(id),
    UNIQUE KEY uq_feedback_ticket (ticket_id),
    CHECK (rating BETWEEN 1 AND 5),
    INDEX idx_feedback_event (event_id)
) ENGINE=InnoDB;

-- Mỗi địa điểm thuộc ban tổ chức quản lý; dữ liệu mẫu có hai ban tổ chức.
INSERT INTO venues (organizer_id, name, address, capacity) VALUES
((SELECT id FROM users WHERE username = 'organizer'), 'Hội trường A', 'Học viện PTIT, Hà Nội', 120),
((SELECT id FROM users WHERE username = 'organizer'), 'Phòng hội thảo B', 'Tầng 2, tòa A2', 60),
((SELECT id FROM users WHERE username = 'organizer2'), 'Sân khấu ngoài trời', 'Sân trung tâm cơ sở Hà Nội', 300),
((SELECT id FROM users WHERE username = 'organizer'), 'Phòng sáng tạo C', 'Tầng 3, tòa A1', 40),
((SELECT id FROM users WHERE username = 'organizer'), 'Phòng hội nghị D', 'Tầng 5, tòa A2', 100),
((SELECT id FROM users WHERE username = 'organizer2'), 'Phòng hội thảo E', 'Tầng 4, tòa A2', 70);

-- Thoi gian tinh tu luc import: su kien cong nghe dang dien ra, cac su kien khac o tuong lai.
-- Su kien dau da dien ra 30 phut: co luot check-in mau, van con mo dang ky.
-- Làm tròn về phút vì biểu mẫu datetime-local trên website không nhập phần giây.
SET @seed_now = CAST(DATE_FORMAT(CURRENT_TIMESTAMP, '%Y-%m-%d %H:%i:00') AS DATETIME);
SET @tech_start = DATE_SUB(@seed_now, INTERVAL 30 MINUTE);
INSERT INTO events
    (venue_id, organizer_id, title, description, banner_url, category,
     starts_at, ends_at, capacity, status, approval_status)
VALUES
    ((SELECT id FROM venues WHERE name = 'Hội trường A'),
     (SELECT id FROM users WHERE username = 'organizer'),
     'Ngày hội Công nghệ và Khởi nghiệp',
     'Gặp gỡ sinh viên, giảng viên và các nhóm khởi nghiệp. Chương trình gồm chia sẻ xu hướng công nghệ, giới thiệu dự án và phiên giao lưu mở.',
     '/api/v1/public/banners/11111111-1111-4111-8111-111111111111.png', 'TECHNOLOGY',
     @tech_start, DATE_ADD(@tech_start, INTERVAL 4 HOUR), 100, 'OPEN', 'APPROVED');
SET @tech_event = LAST_INSERT_ID();

SET @design_start = DATE_ADD(@seed_now, INTERVAL 1 DAY);
INSERT INTO events
    (venue_id, organizer_id, title, description, banner_url, category,
     starts_at, ends_at, capacity, status, approval_status)
VALUES
    ((SELECT id FROM venues WHERE name = 'Phòng hội thảo B'),
     (SELECT id FROM users WHERE username = 'organizer'),
     'Talkshow Thiết kế sản phẩm số',
     'Tìm hiểu hành trình từ nhu cầu người dùng đến bản mẫu giao diện. Người tham dự được đặt câu hỏi và trao đổi về kinh nghiệm thiết kế sản phẩm thực tế.',
     '/api/v1/public/banners/22222222-2222-4222-8222-222222222222.png', 'EDUCATION',
     @design_start, DATE_ADD(@design_start, INTERVAL 3 HOUR), 50, 'OPEN', 'APPROVED');
SET @design_event = LAST_INSERT_ID();

SET @music_start = DATE_ADD(@seed_now, INTERVAL 2 DAY);
INSERT INTO events
    (venue_id, organizer_id, title, description, banner_url, category,
     starts_at, ends_at, capacity, status, approval_status)
VALUES
    ((SELECT id FROM venues WHERE name = 'Sân khấu ngoài trời'),
     (SELECT id FROM users WHERE username = 'organizer2'),
     'Đêm nhạc Kết nối sinh viên',
     'Một buổi tối âm nhạc dành cho cộng đồng sinh viên với các tiết mục biểu diễn, giao lưu câu lạc bộ và khu vực chụp ảnh kỷ niệm.',
     '/api/v1/public/banners/33333333-3333-4333-8333-333333333333.png', 'ENTERTAINMENT',
     @music_start, DATE_ADD(@music_start, INTERVAL 3 HOUR), 250, 'OPEN', 'APPROVED');
SET @music_event = LAST_INSERT_ID();

-- Liên kết họp chỉ là địa chỉ mẫu; sự kiện đang đóng đăng ký để tránh phát vé có link chưa sẵn sàng.
SET @online_start = DATE_ADD(@seed_now, INTERVAL 3 DAY);
INSERT INTO events
    (venue_id, organizer_id, title, description, banner_url, category, online_url,
     starts_at, ends_at, capacity, status, approval_status)
VALUES
    (NULL, (SELECT id FROM users WHERE username = 'organizer2'),
     'Hội thảo An toàn dữ liệu trực tuyến',
     'Chương trình trực tuyến giới thiệu các nguyên tắc bảo vệ dữ liệu cá nhân, quản lý mật khẩu và nhận biết rủi ro khi làm việc trên mạng.',
     '/api/v1/public/banners/22222222-2222-4222-8222-222222222222.png', 'EDUCATION', 'https://example.org/online-room-demo',
     @online_start, DATE_ADD(@online_start, INTERVAL 2 HOUR), 200, 'CLOSED', 'APPROVED');
SET @online_event = LAST_INSERT_ID();

SET @expo_start = DATE_ADD(@seed_now, INTERVAL 4 DAY);
INSERT INTO events
    (venue_id, organizer_id, title, description, banner_url, category,
     starts_at, ends_at, capacity, status, approval_status)
VALUES
    ((SELECT id FROM venues WHERE name = 'Phòng hội nghị D'),
     (SELECT id FROM users WHERE username = 'organizer'),
     'Triển lãm Dự án Sinh viên',
     'Không gian trưng bày các sản phẩm học tập và nghiên cứu của sinh viên. Khách tham dự có thể xem demo, trò chuyện với nhóm thực hiện và góp ý cho sản phẩm.',
     '/api/v1/public/banners/44444444-4444-4444-8444-444444444444.png', 'OTHER',
     @expo_start, DATE_ADD(@expo_start, INTERVAL 5 HOUR), 80, 'OPEN', 'APPROVED');
SET @expo_event = LAST_INSERT_ID();

-- Ba trạng thái nội bộ để demo duyệt, từ chối và gửi lại sự kiện.
SET @planning_start = DATE_ADD(@seed_now, INTERVAL 5 DAY);
INSERT INTO events
    (venue_id, organizer_id, title, description, banner_url, category,
     starts_at, ends_at, capacity, status, approval_status)
VALUES
    ((SELECT id FROM venues WHERE name = 'Phòng sáng tạo C'),
     (SELECT id FROM users WHERE username = 'organizer'),
     'Workshop Lập kế hoạch dự án phần mềm',
     'Thực hành xác định phạm vi, phân rã công việc và nhận diện rủi ro cho một dự án phần mềm quy mô nhỏ.',
     '/api/v1/public/banners/22222222-2222-4222-8222-222222222222.png', 'EDUCATION',
     @planning_start, DATE_ADD(@planning_start, INTERVAL 3 HOUR), 35, 'OPEN', 'PENDING');
SET @planning_event = LAST_INSERT_ID();

SET @data_start = DATE_ADD(@seed_now, INTERVAL 6 DAY);
INSERT INTO events
    (venue_id, organizer_id, title, description, banner_url, category,
     starts_at, ends_at, capacity, status, approval_status, review_note)
VALUES
    ((SELECT id FROM venues WHERE name = 'Phòng hội thảo E'),
     (SELECT id FROM users WHERE username = 'organizer2'),
     'Hội thảo Dữ liệu lớn trong thực tế',
     'Giới thiệu các tình huống sử dụng dữ liệu trong doanh nghiệp.',
     '/api/v1/public/banners/11111111-1111-4111-8111-111111111111.png', 'TECHNOLOGY',
     @data_start, DATE_ADD(@data_start, INTERVAL 2 HOUR), 50, 'OPEN', 'REJECTED',
     'Cần bổ sung nội dung phiên thảo luận và thông tin diễn giả.');
SET @data_event = LAST_INSERT_ID();

SET @creative_start = DATE_ADD(@seed_now, INTERVAL 7 DAY);
INSERT INTO events
    (venue_id, organizer_id, title, description, banner_url, category,
     starts_at, ends_at, capacity, status, approval_status)
VALUES
    ((SELECT id FROM venues WHERE name = 'Sân khấu ngoài trời'),
     (SELECT id FROM users WHERE username = 'organizer2'),
     'Ngày hội Sáng tạo Mùa thu',
     'Bản nháp chương trình kết nối các câu lạc bộ và giới thiệu ý tưởng mới.',
     '/api/v1/public/banners/44444444-4444-4444-8444-444444444444.png', 'OTHER',
     @creative_start, DATE_ADD(@creative_start, INTERVAL 3 HOUR), 200, 'OPEN', 'DRAFT');
SET @creative_event = LAST_INSERT_ID();

-- Phan cong nhan vien mau cho cac su kien co check-in tai cong.
INSERT INTO event_staff (event_id, staff_id) VALUES
(@tech_event, (SELECT id FROM users WHERE username = 'staff')),
(@tech_event, (SELECT id FROM users WHERE username = 'staff2')),
(@design_event, (SELECT id FROM users WHERE username = 'staff')),
(@music_event, (SELECT id FROM users WHERE username = 'staff2')),
(@expo_event, (SELECT id FROM users WHERE username = 'staff'));

INSERT INTO event_reviews (event_id, reviewer_id, previous_status, status, note, reviewed_at) VALUES
(@tech_event, (SELECT id FROM users WHERE username = 'admin'), 'PENDING', 'APPROVED', NULL, @seed_now),
(@design_event, (SELECT id FROM users WHERE username = 'admin'), 'PENDING', 'APPROVED', NULL, @seed_now),
(@music_event, (SELECT id FROM users WHERE username = 'admin'), 'PENDING', 'APPROVED', NULL, @seed_now),
(@online_event, (SELECT id FROM users WHERE username = 'admin'), 'PENDING', 'APPROVED', NULL, @seed_now),
(@expo_event, (SELECT id FROM users WHERE username = 'admin'), 'PENDING', 'APPROVED', NULL, @seed_now),
(@data_event, (SELECT id FROM users WHERE username = 'admin'), 'PENDING', 'REJECTED',
 (SELECT review_note FROM events WHERE id = @data_event), @seed_now);

-- Mỗi sự kiện vẫn chỉ có một hạng vé miễn phí, kể cả sự kiện chưa công khai.
INSERT INTO ticket_types (event_id, name, quota, price)
SELECT id, 'Vé miễn phí', capacity, 0 FROM events;

-- Lịch trình mẫu gắn đúng từng sự kiện và nằm trong thời gian tổ chức.
INSERT INTO agenda_items (event_id, title, starts_at, ends_at, room_name, sort_order) VALUES
(@tech_event, 'Đón khách và check-in', @tech_start, DATE_ADD(@tech_start, INTERVAL 30 MINUTE), 'Sảnh Hội trường A', 0),
(@tech_event, 'Khai mạc và xu hướng công nghệ', DATE_ADD(@tech_start, INTERVAL 30 MINUTE), DATE_ADD(@tech_start, INTERVAL 90 MINUTE), 'Hội trường A', 1),
(@tech_event, 'Giới thiệu dự án khởi nghiệp', DATE_ADD(@tech_start, INTERVAL 90 MINUTE), DATE_ADD(@tech_start, INTERVAL 180 MINUTE), 'Hội trường A', 2),
(@tech_event, 'Giao lưu và tổng kết', DATE_ADD(@tech_start, INTERVAL 180 MINUTE), DATE_ADD(@tech_start, INTERVAL 240 MINUTE), 'Hội trường A', 3),
(@design_event, 'Giới thiệu quy trình thiết kế', @design_start, DATE_ADD(@design_start, INTERVAL 60 MINUTE), 'Phòng hội thảo B', 0),
(@design_event, 'Từ nghiên cứu người dùng đến mockup', DATE_ADD(@design_start, INTERVAL 60 MINUTE), DATE_ADD(@design_start, INTERVAL 120 MINUTE), 'Phòng hội thảo B', 1),
(@design_event, 'Hỏi đáp và góp ý sản phẩm', DATE_ADD(@design_start, INTERVAL 120 MINUTE), DATE_ADD(@design_start, INTERVAL 180 MINUTE), 'Phòng hội thảo B', 2),
(@music_event, 'Mở cửa và đón khách', @music_start, DATE_ADD(@music_start, INTERVAL 30 MINUTE), 'Cổng sân khấu', 0),
(@music_event, 'Biểu diễn các câu lạc bộ', DATE_ADD(@music_start, INTERVAL 30 MINUTE), DATE_ADD(@music_start, INTERVAL 120 MINUTE), 'Sân khấu chính', 1),
(@music_event, 'Giao lưu âm nhạc', DATE_ADD(@music_start, INTERVAL 120 MINUTE), DATE_ADD(@music_start, INTERVAL 180 MINUTE), 'Sân khấu chính', 2),
(@online_event, 'Bảo vệ dữ liệu cá nhân', @online_start, DATE_ADD(@online_start, INTERVAL 60 MINUTE), 'Phòng họp trực tuyến', 0),
(@online_event, 'Tình huống thực tế và hỏi đáp', DATE_ADD(@online_start, INTERVAL 60 MINUTE), DATE_ADD(@online_start, INTERVAL 120 MINUTE), 'Phòng họp trực tuyến', 1),
(@expo_event, 'Mở cửa khu triển lãm', @expo_start, DATE_ADD(@expo_start, INTERVAL 60 MINUTE), 'Khu trưng bày', 0),
(@expo_event, 'Demo dự án sinh viên', DATE_ADD(@expo_start, INTERVAL 60 MINUTE), DATE_ADD(@expo_start, INTERVAL 240 MINUTE), 'Phòng hội nghị D', 1),
(@expo_event, 'Bình chọn và bế mạc', DATE_ADD(@expo_start, INTERVAL 240 MINUTE), DATE_ADD(@expo_start, INTERVAL 300 MINUTE), 'Phòng hội nghị D', 2),
(@planning_event, 'Xác định mục tiêu và phạm vi', @planning_start, DATE_ADD(@planning_start, INTERVAL 60 MINUTE), 'Phòng sáng tạo C', 0),
(@planning_event, 'Lập WBS và phân công', DATE_ADD(@planning_start, INTERVAL 60 MINUTE), DATE_ADD(@planning_start, INTERVAL 120 MINUTE), 'Phòng sáng tạo C', 1),
(@planning_event, 'Nhận diện rủi ro và thảo luận', DATE_ADD(@planning_start, INTERVAL 120 MINUTE), DATE_ADD(@planning_start, INTERVAL 180 MINUTE), 'Phòng sáng tạo C', 2),
(@data_event, 'Ứng dụng dữ liệu trong doanh nghiệp', @data_start, DATE_ADD(@data_start, INTERVAL 90 MINUTE), 'Phòng hội thảo E', 0),
(@creative_event, 'Giới thiệu ý tưởng sáng tạo', @creative_start, DATE_ADD(@creative_start, INTERVAL 90 MINUTE), 'Sân khấu chính', 0);

-- Tên và tiểu sử dưới đây là dữ liệu giả định để trình bày giao diện.
INSERT INTO speakers (event_id, name, biography, topic, image_url, is_vip) VALUES
(@tech_event, 'Nguyễn Minh Khoa', 'Giảng viên quan tâm đến ứng dụng công nghệ trong giáo dục.', 'Xu hướng công nghệ mới', NULL, TRUE),
(@tech_event, 'Trần Hà Linh', 'Thành viên nhóm phát triển sản phẩm khởi nghiệp sinh viên.', 'Từ ý tưởng đến sản phẩm', NULL, FALSE),
(@design_event, 'Phạm Gia Bảo', 'Nhà thiết kế sản phẩm số với kinh nghiệm xây dựng giao diện web.', 'Thiết kế lấy người dùng làm trung tâm', NULL, TRUE),
(@design_event, 'Lê Thanh Mai', 'Hướng dẫn thực hành phác thảo và kiểm thử bản mẫu.', 'Mockup và thử nghiệm nhanh', NULL, FALSE),
(@music_event, 'CLB Giai điệu trẻ', 'Nhóm sinh viên biểu diễn các tiết mục acoustic và nhạc trẻ.', 'Biểu diễn mở màn', NULL, FALSE),
(@music_event, 'Nguyễn Khánh Vy', 'Khách mời giao lưu âm nhạc cùng sinh viên.', 'Giao lưu cuối chương trình', NULL, TRUE),
(@online_event, 'Đỗ Quốc Anh', 'Chuyên gia chia sẻ kiến thức nền tảng về an toàn thông tin.', 'Bảo vệ dữ liệu cá nhân', NULL, TRUE),
(@expo_event, 'Vũ Minh Châu', 'Đại diện nhóm tổ chức hoạt động giới thiệu dự án sinh viên.', 'Giới thiệu khu triển lãm', NULL, FALSE),
(@expo_event, 'Hoàng Đức Long', 'Khách mời góp ý cho các sản phẩm phần mềm trưng bày.', 'Nhận xét sản phẩm', NULL, TRUE),
(@planning_event, 'Trần Thu Hà', 'Giảng viên hướng dẫn lập kế hoạch và quản lý rủi ro dự án.', 'Từ phạm vi đến WBS', NULL, TRUE),
(@planning_event, 'Nguyễn Thành Nam', 'Điều phối phần thực hành theo nhóm.', 'Phân công và theo dõi tiến độ', NULL, FALSE),
(@creative_event, 'Ban điều phối CLB Sáng tạo', 'Nhóm phụ trách xây dựng nội dung ngày hội.', 'Giới thiệu ý tưởng', NULL, FALSE);

-- Hai muoi sau khach da dang ky: 20 ve cong nghe, 3 ve thiet ke, 2 ve am nhac, 1 ve trien lam.
-- Token QR duoc ung dung ky khi khoi dong bang TICKET_SECRET dang su dung.
INSERT INTO attendees (full_name, email, phone) VALUES
('Phạm Ngọc Lan', 'khach4@sems.example', '0900000004'),
('Đỗ Minh Quân', 'khach5@sems.example', '0900000005'),
('Vũ Thanh Trúc', 'khach6@sems.example', '0900000006'),
('Bùi Anh Tú', 'khach7@sems.example', '0900000007'),
('Ngô Thu Hà', 'khach8@sems.example', '0900000008'),
('Trịnh Đức Huy', 'khach9@sems.example', '0900000009'),
('Lý Hoàng Yến', 'khach10@sems.example', '0900000010'),
('Mai Quốc Bảo', 'khach11@sems.example', '0900000011'),
('Hoàng Khánh Chi', 'khach12@sems.example', '0900000012'),
('Phan Minh Đức', 'khach13@sems.example', '0900000013'),
('Trần Minh Ngọc', 'khach14@sems.example', '0900000014'),
('Nguyễn Quốc Khánh', 'khach15@sems.example', '0900000015'),
('Đặng Thùy Dương', 'khach16@sems.example', '0900000016'),
('Phạm Tuấn Kiệt', 'khach17@sems.example', '0900000017'),
('Lê Phương Anh', 'khach18@sems.example', '0900000018'),
('Vũ Đức Thành', 'khach19@sems.example', '0900000019'),
('Hoàng Mai Linh', 'khach20@sems.example', '0900000020'),
('Bùi Gia Huy', 'khach21@sems.example', '0900000021'),
('Đỗ Ngọc Ánh', 'khach22@sems.example', '0900000022'),
('Nguyễn Văn Phúc', 'khach23@sems.example', '0900000023'),
('Trương Bảo Vy', 'khach24@sems.example', '0900000024'),
('Lương Minh Nhật', 'khach25@sems.example', '0900000025'),
('Phan Thu Trang', 'khach26@sems.example', '0900000026'),
('Trịnh Anh Dũng', 'khach27@sems.example', '0900000027'),
('Mai Hà My', 'khach28@sems.example', '0900000028'),
('Ngô Quang Vinh', 'khach29@sems.example', '0900000029');

-- Thong tin bo sung tuy chon theo tung lan dang ky.
UPDATE attendees SET organization = 'Đại học PTIT', position_title = 'Sinh viên'
WHERE email IN ('khach4@sems.example', 'khach5@sems.example', 'khach18@sems.example');
UPDATE attendees SET organization = 'Công ty Công nghệ Sao Việt', position_title = 'Kỹ sư phần mềm'
WHERE email IN ('khach9@sems.example', 'khach19@sems.example');

INSERT INTO tickets
    (event_id, ticket_type_id, attendee_id, user_id, code, qr_token,
     email_key, phone_key, status, issued_at)
SELECT seed.event_id, tt.id, a.id, u.id, UUID(), 'DEMO_PENDING',
       a.email, a.phone, 'ISSUED', DATE_SUB(@seed_now, INTERVAL 45 MINUTE)
FROM (
    SELECT 'khach4' AS username, 'khach4@sems.example' AS email, @tech_event AS event_id
    UNION ALL SELECT 'khach5', 'khach5@sems.example', @tech_event
    UNION ALL SELECT 'khach6', 'khach6@sems.example', @tech_event
    UNION ALL SELECT 'khach7', 'khach7@sems.example', @tech_event
    UNION ALL SELECT 'khach8', 'khach8@sems.example', @design_event
    UNION ALL SELECT 'khach9', 'khach9@sems.example', @design_event
    UNION ALL SELECT 'khach10', 'khach10@sems.example', @design_event
    UNION ALL SELECT 'khach11', 'khach11@sems.example', @music_event
    UNION ALL SELECT 'khach12', 'khach12@sems.example', @music_event
    UNION ALL SELECT 'khach13', 'khach13@sems.example', @expo_event
    UNION ALL SELECT 'khach14', 'khach14@sems.example', @tech_event
    UNION ALL SELECT 'khach15', 'khach15@sems.example', @tech_event
    UNION ALL SELECT 'khach16', 'khach16@sems.example', @tech_event
    UNION ALL SELECT 'khach17', 'khach17@sems.example', @tech_event
    UNION ALL SELECT 'khach18', 'khach18@sems.example', @tech_event
    UNION ALL SELECT 'khach19', 'khach19@sems.example', @tech_event
    UNION ALL SELECT 'khach20', 'khach20@sems.example', @tech_event
    UNION ALL SELECT 'khach21', 'khach21@sems.example', @tech_event
    UNION ALL SELECT 'khach22', 'khach22@sems.example', @tech_event
    UNION ALL SELECT 'khach23', 'khach23@sems.example', @tech_event
    UNION ALL SELECT 'khach24', 'khach24@sems.example', @tech_event
    UNION ALL SELECT 'khach25', 'khach25@sems.example', @tech_event
    UNION ALL SELECT 'khach26', 'khach26@sems.example', @tech_event
    UNION ALL SELECT 'khach27', 'khach27@sems.example', @tech_event
    UNION ALL SELECT 'khach28', 'khach28@sems.example', @tech_event
    UNION ALL SELECT 'khach29', 'khach29@sems.example', @tech_event
) AS seed
JOIN users u ON u.username = seed.username
JOIN attendees a ON a.email = seed.email
JOIN ticket_types tt ON tt.event_id = seed.event_id;

-- Muoi khach da qua cong trong cac moc thoi gian khac nhau, de Dashboard co bieu do.
UPDATE tickets t JOIN users u ON u.id = t.user_id
SET t.status = 'CHECKED_IN',
    t.checked_in_at = TIMESTAMPADD(MINUTE,
        CASE u.username
            WHEN 'khach4' THEN 5 WHEN 'khach5' THEN 5
            WHEN 'khach6' THEN 10 WHEN 'khach7' THEN 10
            WHEN 'khach14' THEN 15 WHEN 'khach15' THEN 15
            WHEN 'khach16' THEN 20 WHEN 'khach17' THEN 20
            WHEN 'khach18' THEN 25 WHEN 'khach19' THEN 29
        END, @tech_start)
WHERE t.event_id = @tech_event
  AND u.username IN ('khach4', 'khach5', 'khach6', 'khach7',
                     'khach14', 'khach15', 'khach16', 'khach17', 'khach18', 'khach19');

INSERT INTO checkin_logs (ticket_id, event_id, method, operator_name, checked_in_at)
SELECT t.id, t.event_id, 'QR', 'staff', t.checked_in_at
FROM tickets t WHERE t.event_id = @tech_event AND t.status = 'CHECKED_IN';

-- Su kien da ket thuc dung de minh hoa thong ke danh gia sau khi tham du.
SET @review_start = DATE_SUB(@seed_now, INTERVAL 2 DAY);
INSERT INTO events
    (venue_id, organizer_id, title, description, banner_url, category,
     starts_at, ends_at, capacity, status, approval_status, created_at)
VALUES
    ((SELECT id FROM venues WHERE name = 'Hội trường A'),
     (SELECT id FROM users WHERE username = 'organizer'),
     'Hội thảo Công nghệ số đã diễn ra',
     'Chương trình chia sẻ kinh nghiệm công nghệ và giới thiệu dự án của sinh viên.',
     '/api/v1/public/banners/11111111-1111-4111-8111-111111111111.png', 'TECHNOLOGY',
     @review_start, DATE_ADD(@review_start, INTERVAL 4 HOUR), 100, 'CLOSED', 'APPROVED',
     DATE_SUB(@review_start, INTERVAL 3 DAY));
SET @review_event = LAST_INSERT_ID();
INSERT INTO event_reviews (event_id, reviewer_id, previous_status, status, note, reviewed_at)
VALUES (@review_event, (SELECT id FROM users WHERE username = 'admin'),
        'PENDING', 'APPROVED', NULL, DATE_SUB(@review_start, INTERVAL 2 DAY));
INSERT INTO event_staff (event_id, staff_id)
VALUES (@review_event, (SELECT id FROM users WHERE username = 'staff'));
INSERT INTO ticket_types (event_id, name, quota, price)
VALUES (@review_event, 'Vé miễn phí', 100, 0);
INSERT INTO tickets
    (event_id, ticket_type_id, attendee_id, user_id, code, qr_token,
     email_key, phone_key, status, issued_at, checked_in_at)
SELECT @review_event, tt.id, a.id, u.id, UUID(), 'DEMO_PENDING',
       a.email, a.phone, 'CHECKED_IN', DATE_SUB(@review_start, INTERVAL 1 DAY),
       TIMESTAMPADD(MINUTE, (u.id MOD 8) * 15 + 5, @review_start)
FROM tickets t
JOIN users u ON u.id = t.user_id
JOIN attendees a ON a.id = t.attendee_id
JOIN ticket_types tt ON tt.event_id = @review_event
WHERE t.event_id = @tech_event
  AND u.username IN ('khach4', 'khach5', 'khach6', 'khach7',
                     'khach14', 'khach15', 'khach16', 'khach17', 'khach18', 'khach19');
INSERT INTO checkin_logs (ticket_id, event_id, method, operator_name, checked_in_at)
SELECT t.id, t.event_id, 'QR', 'staff', t.checked_in_at
FROM tickets t WHERE t.event_id = @review_event;

-- Tam nhan xet mau cua su kien da ket thuc.
INSERT INTO feedback (ticket_id, event_id, rating, comment_text, created_at)
SELECT t.id, t.event_id,
       CASE u.username
           WHEN 'khach4' THEN 5 WHEN 'khach5' THEN 4
           WHEN 'khach6' THEN 5 WHEN 'khach7' THEN 3
           WHEN 'khach14' THEN 4 WHEN 'khach15' THEN 5
           WHEN 'khach16' THEN 4 WHEN 'khach17' THEN 2
       END,
       CASE u.username
           WHEN 'khach4' THEN 'Check-in nhanh, chỉ dẫn ở cổng rất rõ ràng.'
           WHEN 'khach5' THEN 'Nội dung chia sẻ công nghệ dễ hiểu và hữu ích.'
           WHEN 'khach6' THEN 'Khu trưng bày dự án có nhiều ý tưởng hay.'
           WHEN 'khach7' THEN 'Phần đón khách tốt, nhưng khu vực ngồi hơi chật.'
           WHEN 'khach14' THEN 'Diễn giả trả lời câu hỏi cụ thể, dễ theo dõi.'
           WHEN 'khach15' THEN 'Mong chương trình có thêm hoạt động giao lưu.'
           WHEN 'khach16' THEN 'Thời gian check-in hợp lý, nhân viên hỗ trợ nhiệt tình.'
           WHEN 'khach17' THEN 'Âm thanh lúc bắt đầu còn khó nghe, cần cải thiện.'
       END,
       TIMESTAMPADD(MINUTE, 2, t.checked_in_at)
FROM tickets t JOIN users u ON u.id = t.user_id
WHERE t.event_id = @review_event
  AND u.username IN ('khach4', 'khach5', 'khach6', 'khach7',
                     'khach14', 'khach15', 'khach16', 'khach17');
