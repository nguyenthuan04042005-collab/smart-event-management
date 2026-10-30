# SEMS – Quản lý sự kiện thông minh

Website quản lý sự kiện và vé QR, check-in kèm Dashboard trực tiếp, đánh giá sau sự kiện. Backend dùng Java Spring Boot, MySQL; giao diện dùng HTML, CSS và JavaScript.

## 1. Chuẩn bị MySQL

Cần JDK 17 trở lên và MySQL 8. Chạy toàn bộ file `smart_event_management.sql` trong MySQL Workbench. File sẽ xóa database `smart_event_management` cũ, tạo lại bảng và thêm dữ liệu mẫu.

## 2. Chạy chương trình

Mở `src/main/resources/application.properties`, sửa cấu hình MySQL cho đúng máy của bạn. Ví dụ:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/smart_event_management?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Ho_Chi_Minh
spring.datasource.username=root
spring.datasource.password=MAT_KHAU_MYSQL_CUA_BAN
app.ticket-secret=CHUOI_BI_MAT_RIENG_DAI_IT_NHAT_32_KY_TU
app.jwt-secret=CHUOI_BI_MAT_JWT_RIENG_DAI_IT_NHAT_32_KY_TU
```

Nếu dùng đúng địa chỉ `localhost:3306` và tài khoản `root`, bạn chỉ cần sửa mật khẩu MySQL và đặt hai khóa bí mật riêng. Khóa ký vé cần giữ cố định: đổi khóa sau khi đã phát vé sẽ làm mã QR cũ không còn xác thực được. Đổi khóa JWT sẽ làm các phiên đăng nhập hiện tại hết hiệu lực.

Mở PowerShell tại thư mục dự án và chạy `.\mvnw.cmd spring-boot:run`. Mở `http://localhost:8080`. Không cần cài Maven riêng. Sau khi nhập lại SQL, khởi động lại ứng dụng để ký mã QR cho các vé mẫu.

## 3. Tài khoản mẫu

| Vai trò | Tài khoản | Mật khẩu |
|---|---|---|
| Quản trị viên | `admin` | `Admin@123456` |
| Ban tổ chức | `organizer` | `Organizer@123456` |
| Nhân viên check-in | `staff` | `Staff@123456` |
| Người tham dự chưa có vé | `khach1` | `Khach1@123456` |
| Người tham dự có vé của sự kiện đã kết thúc, chưa đánh giá | `khach18` | `Khach1@123456` |

SQL còn có `organizer2`, `staff2` và các tài khoản `khach2`–`khach29`. Có thể đăng ký tài khoản người tham dự mới trên website.

Mọi tài khoản mẫu có email riêng dạng `ten-dang-nhap@sems.example` và được đánh dấu đã xác minh để demo. Địa chỉ này không nhận thư; hãy đổi sang email thật của bạn để thử gửi mail.

## 4. Chức năng chính

- **Ban tổ chức:** tạo địa điểm và sự kiện, tải ảnh banner PNG/JPEG, quản lý đăng ký, theo dõi Dashboard và phản hồi.
- **Quản trị viên:** quản lý tài khoản và duyệt sự kiện.
- **Người tham dự:** sau đăng nhập trở lại trang khám phá, xem chi tiết sự kiện, đăng ký vé, xem vé QR, hủy vé hợp lệ và đánh giá sau khi đã check-in và sự kiện kết thúc. Menu tên/avatar có Thông tin cá nhân và Vé của tôi.
- **Nhân viên:** quét QR có âm báo hoặc tra cứu khách để check-in.

Các bước trình diễn nằm trong [KICH_BAN_DEMO.md](KICH_BAN_DEMO.md).

Vé được gửi đến email tài khoản và lưu trong **Vé của tôi**. Đơn vị/công ty/trường và chức vụ/vai trò là tùy chọn, được đưa vào danh sách và CSV đăng ký.

Sau khi sự kiện kết thúc, nếu bật SMTP, hệ thống gửi một email mời đánh giá cho chủ vé đã check-in và chưa đánh giá. Liên kết trong thư mở trang đánh giá; khách cũng có thể vào từ **Vé của tôi**.

Nếu lỗi gửi tạm thời, hệ thống thử tối đa 3 lần, cách nhau 5 phút. Admin xem lịch sử gửi và thư mời đã thất bại tại **Cấu hình email**. Thư gửi cho người dùng có bản HTML và văn bản thuần; email thử SMTP là văn bản thuần.

Email vé QR cũng có hàng đợi trong MySQL: khi SMTP lỗi tạm thời, hệ thống thử lại sau 5 phút, tối đa 3 lần. Admin xem trạng thái tại **Cấu hình email**. Sau khi SMTP đã nhận thư, hệ thống dừng thử lại; thư bị Gmail trả về sau đó không được tự phát hiện. Ảnh banner tải lên và 4 ảnh mẫu PNG được lưu trong `uploads/banners` khi chạy ứng dụng; giữ thư mục này cùng project.

Đăng nhập dùng JWT Bearer có hạn 8 giờ. Mỗi tab giữ token riêng trong `sessionStorage`, nên có thể mở các tài khoản khác nhau ở nhiều tab để demo. Đăng xuất thu hồi token; khóa tài khoản hoặc đổi mật khẩu cũng làm token cũ hết hiệu lực.

## 5. Gửi email bằng Gmail (tùy chọn)

1. Vào [Tài khoản Google → Bảo mật và đăng nhập](https://myaccount.google.com/security). Trong phần **Cách bạn đăng nhập vào Google**, bật **Xác minh 2 bước** và làm theo hướng dẫn của Google.
2. Khi xác minh 2 bước đã bật, mở [Mật khẩu ứng dụng](https://myaccount.google.com/apppasswords), đăng nhập lại nếu được yêu cầu, đặt tên như `SEMS` rồi tạo mật khẩu. Sao chép mật khẩu ứng dụng Google vừa cấp; đây **không phải** mật khẩu đăng nhập Gmail thông thường. Nếu không thấy mục này, xem [các trường hợp Google không cho dùng mật khẩu ứng dụng](https://support.google.com/accounts/answer/185833?hl=vi).
3. Đăng nhập SEMS bằng tài khoản `admin`, mở **Cấu hình email**. Nhập **Máy chủ SMTP** `smtp.gmail.com`, **Cổng** `587`, **Tài khoản SMTP** là địa chỉ Gmail đầy đủ, **Địa chỉ người gửi** là cùng địa chỉ Gmail, và **Mật khẩu ứng dụng** vừa tạo (nhập liền, không có dấu cách). Bật gửi email rồi bấm **Lưu cấu hình**.
4. Bấm **Gửi email thử** tới hộp thư bạn kiểm soát. Kiểm tra cả thư mục Spam nếu chưa thấy thư. SEMS chỉ dùng SMTP để gửi; không cần bật POP hoặc IMAP trong Gmail.

Khi gửi email đã bật, người dùng nhập mã xác minh ngay lúc đăng ký hoặc đổi email. Khi tắt, hệ thống bỏ qua bước mã theo quy ước demo; vé vẫn xem được trong **Vé của tôi**. Các địa chỉ mẫu `@sems.example` không nhận thư. Cấu hình SMTP được lưu trong MySQL, còn mật khẩu ứng dụng được mã hóa bằng khóa cấu hình của server. Nếu đặt `MAIL_CONFIG_KEY` riêng, hãy giữ giá trị đó cố định.

## 6. Kiểm thử

```powershell
.\mvnw.cmd test
```
