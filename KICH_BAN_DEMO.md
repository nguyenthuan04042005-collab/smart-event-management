# Kịch bản demo SEMS

## Chuẩn bị

1. Nhập `smart_event_management.sql`, khởi động ứng dụng theo [README.md](README.md). Nên nhập SQL gần giờ demo vì sự kiện mẫu **Ngày hội Công nghệ và Khởi nghiệp** có thời gian tính từ lúc nhập dữ liệu.
2. Mở bốn tab cho `organizer`, `admin`, `staff` và `khach1`. JWT lưu riêng trong từng tab nên có thể đăng nhập đồng thời bằng bốn tài khoản.
3. Chuẩn bị webcam để quét mã QR hiển thị trên điện thoại hoặc màn hình khác. Có thể dùng mã vé để thử check-in thủ công.

## 1. Sự kiện và vé QR

1. **Organizer:** mở danh sách sự kiện, xem chi tiết sự kiện mẫu và danh sách đăng ký. Tạo sự kiện mới với địa điểm, thời gian, mô tả, ảnh banner tải từ máy và lịch trình nếu muốn trình diễn quy trình duyệt.
2. **Admin:** mở sự kiện mới đang chờ, xem nội dung rồi phê duyệt. Nếu từ chối, nhập lý do để Organizer xem và sửa trước khi gửi lại.
3. **Khách (`khach1`):** đăng nhập và trở lại trang chủ, mở chi tiết **Ngày hội Công nghệ và Khởi nghiệp**, bấm đăng ký vé. Vé dùng email tài khoản; thêm đơn vị/chức vụ tùy chọn. Vào **Vé của tôi** xem mã QR; đăng ký lại cùng sự kiện để thấy hệ thống ngăn trùng.

## 2. Check-in và Dashboard

1. **Organizer:** mở Dashboard của sự kiện mẫu, ghi nhận số đăng ký và số đã đến trước khi quét.
2. **Staff:** chọn đúng sự kiện, bật camera và quét QR của `khach1`; nếu không dùng camera, nhập mã vé để check-in thủ công.
3. **Organizer:** xem số đã đến và tỷ lệ tham dự tự cập nhật trên Dashboard; xem khách vừa check-in trong danh sách.
4. **Staff:** quét lại cùng vé để thấy âm báo lỗi, viền khung camera đỏ và thông báo vé đã check-in. Có thể dùng ô tìm tên/email khi khách quên vé.

## 3. Đánh giá và báo cáo

1. **Khách (`khach1`):** vé vừa check-in của sự kiện đang diễn ra chưa có nút đánh giá.
2. **Khách (`khach18`):** mở **Vé của tôi**, chọn vé của **Hội thảo Công nghệ số đã diễn ra**, chấm 1–5 sao và gửi nhận xét. Thử gửi lại để thấy giới hạn một lần cho mỗi vé.
3. **Organizer:** mở **Phản hồi** của hội thảo đã kết thúc, xem điểm trung bình, phân bố sao, biểu đồ và danh sách nhận xét; thử lọc/tìm kiếm và tải báo cáo.
