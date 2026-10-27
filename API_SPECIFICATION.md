# SMART EVENT MANAGEMENT SYSTEM (SEMS) — API CONTRACT SPECIFICATION
> **Tài liệu Thiết Kế API Dùng Chung Cho Backend (Spring Boot) & Frontend**  
> **Phiên bản:** 1.0.0 | **Chuẩn:** RESTful API & OpenAPI 3.0 | **Format:** JSON

---

## 1. Mục Đích & Nguyên Tắc Hợp Tác Song Song (Contract-First)

Tài liệu này và file chuẩn máy đọc [`openapi.yaml`](./openapi.yaml) đóng vai trò là **Hợp đồng (Contract)** duy nhất giữa đội ngũ **Backend** và **Frontend**:
1. **Frontend không cần đợi Backend hoàn thành:** Có thể khởi tạo ngay Mock Server từ `openapi.yaml` để code UI, bind dữ liệu thật mà không sợ lệch cấu trúc trường.
2. **Backend xây dựng API theo đúng chuẩn:** Mọi tên trường (camelCase), HTTP Method, mã trạng thái (200, 201, 204, 400, 401, 403, 404), kiểu dữ liệu và đường dẫn URL đều tuân thủ đúng tài liệu này.

---

## 2. Hướng Dẫn Frontend Tạo Mock Server Trong 30 Giây

Frontend có thể chạy trực tiếp Mock Server giả lập toàn bộ API với dữ liệu mẫu bằng **Prism**:

```bash
# Không cần cài đặt vĩnh viễn, chạy trực tiếp qua npx:
npx @stoplight/prism-cli mock openapi.yaml -p 4010
```
- Mock Server sẽ chạy tại: `http://localhost:4010`
- Khi Backend sẵn sàng, Frontend chỉ cần đổi `BASE_URL` từ `http://localhost:4010` sang `http://localhost:8080`.
- Có thể import file `openapi.yaml` trực tiếp vào **Postman** (nút *Import*) để tự động sinh toàn bộ bộ Test Request.

---

## 3. Quy Ước Kỹ Thuật Chung

| Mục | Quy ước |
| :--- | :--- |
| **Base URL** | `/api/v1` |
| **Định dạng dữ liệu** | `application/json;charset=UTF-8` |
| **Quy tắc đặt tên trường** | `camelCase` (ví dụ: `startsAt`, `venueName`, `ticketCode`) |
| **Định dạng ngày giờ** | ISO-8601: `YYYY-MM-DDTHH:mm:ss` (ví dụ: `2026-11-15T08:30:00`) |
| **Phân quyền người dùng** | `ADMIN`, `ORGANIZER`, `STAFF`, `ATTENDEE` |
| **Cơ chế xác thực** | Session-based (`JSESSIONID` Cookie) + CSRF Token Protection |
| **Cấu trúc phản hồi lỗi chuẩn** | `{"message": "Mô tả lỗi cụ thể", "timestamp": "2026-10-27T15:30:00"}` |

### Quy trình Authentication & CSRF cho Frontend:
1. Trước khi gửi các request làm thay đổi dữ liệu (`POST`, `PUT`, `DELETE`, `PATCH`), Frontend gọi:
   - `GET /api/v1/auth/csrf` để lấy token, hoặc đọc từ cookie `XSRF-TOKEN`.
   - Đính kèm token vào header: `X-XSRF-TOKEN: <token>`.
2. Đăng nhập: Gọi `POST /api/v1/auth/login` với dữ liệu form (`username`, `password`). Trình duyệt sẽ tự động lưu cookie `JSESSIONID`.

---

## 4. Danh Sách Endpoint Chi Tiết

### 4.1. Xác thực & Tài khoản cá nhân (`/api/v1/auth`, `/api/v1/account`, `/api/v1/me`)

| Method | Endpoint | Quyền hạn | Mô tả | Request Body / Params | Phản hồi chính |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/auth/csrf` | Public | Lấy mã CSRF Token | Không | `{"token": "string"}` |
| `POST` | `/api/v1/auth/login` | Public | Đăng nhập hệ thống | Form: `username`, `password` | `{"username": "...", "roles": [...]}` |
| `POST` | `/api/v1/auth/register` | Public | Đăng ký người tham dự | `NewAttendee` (user, name, email, pass) | `StartResult` (requestId, expiresAt) |
| `POST` | `/api/v1/auth/confirm-registration` | Public | Xác nhận mã OTP kích hoạt | `CodeInput` (requestId, code) | `{"message": "..."}` |
| `POST` | `/api/v1/auth/resend-registration-code` | Public | Gửi lại mã OTP qua email | `RequestIdInput` (requestId) | `{"message": "..."}` |
| `GET` | `/api/v1/me` | Authenticated | Thông tin tài khoản hiện tại | Không | `UserView` |
| `POST` | `/api/v1/account/profile` | Authenticated | Đổi tên hiển thị | `ProfileInput` (displayName) | `UserView` |
| `POST` | `/api/v1/account/change-password` | Authenticated | Đổi mật khẩu | `ChangePasswordInput` (current, new) | `{"message": "..."}` |

---

### 4.2. Sự kiện công khai & Ban tổ chức (`/api/v1/public/events`, `/api/v1/events`)

| Method | Endpoint | Quyền hạn | Mô tả | Request Body / Params | Phản hồi chính |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/public/events` | Public | Xem danh sách sự kiện đã duyệt | Không | `List<PublicEventView>` |
| `GET` | `/api/v1/public/events/{id}` | Public | Xem chi tiết 1 sự kiện công khai | Path: `id` | `PublicEventView` |
| `GET` | `/api/v1/events` | Authenticated | Danh sách sự kiện quản lý | Không | `List<EventView>` |
| `POST` | `/api/v1/events` | `ORGANIZER` | Tạo mới sự kiện (Bản nháp) | `EventInput` (title, startsAt, venueId...) | `201 Created` (`EventView`) |
| `GET` | `/api/v1/events/{id}` | Authenticated | Chi tiết sự kiện nội bộ | Path: `id` | `EventView` |
| `PUT` | `/api/v1/events/{id}` | `ORGANIZER` | Cập nhật thông tin sự kiện | Path: `id`, Body: `EventInput` | `EventView` |
| `DELETE` | `/api/v1/events/{id}` | `ORGANIZER` | Xóa sự kiện nháp | Path: `id` | `204 No Content` |
| `POST` | `/api/v1/events/{id}/submit` | `ORGANIZER` | Nộp sự kiện xin phê duyệt | Path: `id` | `EventView` |
| `POST` | `/api/v1/events/{id}/withdraw` | `ORGANIZER` | Rút đơn phê duyệt về lại Nháp | Path: `id` | `EventView` |
| `POST` | `/api/v1/events/{id}/cancel` | `ADMIN`, `ORGANIZER` | Hủy sự kiện | Path: `id`, Body: `CancelInput` | `EventView` |
| `POST` | `/api/v1/admin/events/{id}/review` | `ADMIN` | Phê duyệt hoặc Từ chối sự kiện | Path: `id`, Body: `ReviewInput` | `EventView` |
| `GET` | `/api/v1/events/{id}/reviews` | `ADMIN`, `ORGANIZER` | Xem lịch sử duyệt sự kiện | Path: `id` | `List<EventReviewView>` |

---

### 4.3. Đăng ký & Quản lý vé (`/api/v1/events/{eventId}/registrations`, `/api/v1/tickets`)

| Method | Endpoint | Quyền hạn | Mô tả | Request Body / Params | Phản hồi chính |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/events/{eventId}/registrations` | `ATTENDEE` | Đăng ký nhận vé sự kiện | `RegistrationInput` (fullName, phone...) | `201 Created` (`TicketView`) |
| `GET` | `/api/v1/events/{eventId}/registrations` | `ADMIN`, `ORGANIZER` | Danh sách người đã đăng ký vé | Path: `eventId` | `List<TicketView>` |
| `GET` | `/api/v1/tickets/mine` | `ATTENDEE` | Danh sách vé của cá nhân tôi | Không | `List<TicketView>` |
| `GET` | `/api/v1/tickets/{code}` | `ADMIN`, `ATTENDEE` | Chi tiết vé theo mã | Path: `code` (ví dụ `TK-2026-X8Y9`) | `TicketView` |
| `POST` | `/api/v1/tickets/{code}/cancel` | `ATTENDEE` | Hủy vé đã đăng ký | Path: `code` | `TicketView` |
| `GET` | `/api/v1/tickets/{code}/qr` | `ADMIN`, `ATTENDEE` | Lấy ảnh mã QR Code của vé | Path: `code` | Binary `image/png` |

---

### 4.4. Trạm Check-in Kiosk / Cổng (`/api/v1/events/{eventId}/check-ins`)

| Method | Endpoint | Quyền hạn | Mô tả | Request Body / Params | Phản hồi chính |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/events/{eventId}/check-ins` | `STAFF` | Quét QR hoặc điểm danh vé | `CheckinInput` (qrToken, ticketCode, method) | `CheckinResult` |
| `GET` | `/api/v1/events/{eventId}/check-ins/search` | `STAFF` | Tìm kiếm người tham dự điểm danh thủ công | Query: `query` (tên/email/mã), `page` | `CheckinSearchView` |

---

### 4.5. Đánh giá sự kiện & Thống kê phản hồi (`/api/v1/tickets/{code}/feedback`, `/api/v1/events/{eventId}/feedback`)

| Method | Endpoint | Quyền hạn | Mô tả | Request Body / Params | Phản hồi chính |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/tickets/{code}/feedback` | `ATTENDEE` | Gửi chấm điểm và nhận xét | `FeedbackInput` (rating 1-5, comment) | `201 Created` (`FeedbackView`) |
| `GET` | `/api/v1/events/{eventId}/feedback` | `ADMIN`, `ORGANIZER` | Thống kê sao, xu hướng đánh giá | Path: `eventId` | `FeedbackSummary` |
| `GET` | `/api/v1/events/{eventId}/feedback/items` | `ADMIN`, `ORGANIZER` | Chi tiết danh sách ý kiến đánh giá | Query: `query`, `rating`, `page` | `FeedbackPage` |
| `GET` | `/api/v1/feedback/mine` | `ATTENDEE` | Lịch sử đánh giá của tôi | Không | `List<MyFeedbackView>` |

---

### 4.6. Dashboard Realtime & Báo cáo xuất file

| Method | Endpoint | Quyền hạn | Mô tả | Request Body / Params | Phản hồi chính |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/events/{eventId}/dashboard` | `ADMIN`, `ORGANIZER` | Thống kê số lượng vé, tỉ lệ check-in | Path: `eventId` | `DashboardView` |
| `GET` | `/api/v1/events/{eventId}/reports/feedback.xlsx`| `ADMIN`, `ORGANIZER` | Tải báo cáo Excel đánh giá | Path: `eventId` | Binary `.xlsx` |
| `GET` | `/api/v1/events/{eventId}/reports/{type}` | `ADMIN`, `ORGANIZER` | Tải báo cáo CSV (registrations/checkins) | Path: `eventId`, `type` | File `text/csv` |
| `WS` | `/ws/dashboard` | `ADMIN`, `ORGANIZER` | WebSocket đẩy tín hiệu cập nhật tự động khi có lượt check-in | WebSocket connection | Push update events |

---

### 4.7. Quản lý Địa điểm (`/api/v1/venues`)

| Method | Endpoint | Quyền hạn | Mô tả | Request Body / Params | Phản hồi chính |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/venues` | `ADMIN`, `ORGANIZER` | Danh sách các địa điểm | Không | `List<VenueView>` |
| `POST` | `/api/v1/venues` | `ORGANIZER` | Tạo địa điểm tổ chức mới | `VenueInput` (name, address, capacity) | `201 Created` (`VenueView`) |
| `PUT` | `/api/v1/venues/{id}` | `ORGANIZER` | Sửa thông tin địa điểm | `VenueInput` | `VenueView` |
| `DELETE` | `/api/v1/venues/{id}` | `ORGANIZER` | Xóa địa điểm | Path: `id` | `204 No Content` |
| `PATCH` | `/api/v1/venues/{id}/active` | `ORGANIZER` | Bật/tắt trạng thái sử dụng | Body: `{"active": true/false}` | `VenueView` |

---

## 5. DTO Schemas Trọng Tâm (Data Transfer Objects)

### `EventInput` (Tạo / Sửa sự kiện):
```json
{
  "title": "Hội thảo Công nghệ 2026",
  "description": "Nội dung chi tiết về AI...",
  "venueId": 1,
  "startsAt": "2026-11-15T08:00:00",
  "endsAt": "2026-11-15T17:00:00",
  "capacity": 200,
  "status": "DRAFT",
  "bannerUrl": "https://example.com/banner.png",
  "category": "CÔNG NGHỆ",
  "onlineUrl": "https://meet.google.com/abc-xyz",
  "agenda": [
    {
      "title": "Khai mạc",
      "startsAt": "2026-11-15T08:00:00",
      "endsAt": "2026-11-15T08:30:00",
      "roomName": "Hội trường 1"
    }
  ],
  "speakers": [
    {
      "name": "GS. Nguyễn Văn B",
      "biography": "Chuyên gia dữ liệu lớn",
      "topic": "Ứng dụng AI",
      "imageUrl": "https://example.com/sp1.jpg",
      "vip": true
    }
  ]
}
```

### `CheckinInput`:
```json
{
  "qrToken": "7d92fb4a-e64e-4e4b-97e3-05448c9dfd38",
  "ticketCode": "TK-2026-XYZ123",
  "method": "QR_SCAN"
}
```

### `FeedbackInput`:
```json
{
  "rating": 5,
  "comment": "Sự kiện được chuẩn bị rất chu đáo và đúng giờ!"
}
```
