# Lab 5 — Bài 2: Mail Server qua UDP (Giao diện Chat-Message & Admin Dashboard)

Dự án được xây dựng theo yêu cầu môn Lập trình mạng (Lab 5 - Bài 2), được nâng cấp toàn diện với giao diện đồ họa hiện đại phong cách **chat-message**, tích hợp **UI Admin** trên Server và cơ chế **kiểm soát/phê duyệt Client** trước khi cho vào hoạt động.

Dự án viết bằng **Java 17**, dùng thư viện chuẩn Java (Java Swing + UDP Socket), không phụ thuộc Maven bên ngoài, biên dịch và chạy trực tiếp.

---

## 🌟 Tính năng nổi bật & Đáp ứng yêu cầu

### 1. Phía Server (UI Admin Dashboard)
- **Bảng điều khiển Admin trực quan:** Hỗ trợ cả **Chế độ Tối (Dark)** và **Chế độ Sáng (Light)** với nút **[Đổi nền Sáng/Tối]**, chữ to sắc nét, tương phản cao, khử răng cưa chữ antialiasing trên mọi màn hình.
  - Cổng UDP đang lắng nghe (mặc định: `2346`).
  - Số lượng Client đang hoạt động.
  - Số lượng yêu cầu đang chờ phê duyệt (màu cam Amber nổi bật).
  - Tổng số file email đã lưu trên đĩa.
- **Cơ chế phê duyệt Client (Admin Approval):**
  - Khi một Client gửi yêu cầu tham gia, thông tin (**Họ tên, Mail/Account, Địa chỉ IP Client, Port, Thời gian**) sẽ lập tức xuất hiện trong bảng *Yêu cầu chờ duyệt*.
  - Admin có toàn quyền bấm:
    - **[Chấp nhận Client]**: Cấp quyền hoạt động, tự động tạo thư mục và file `new_email.txt` trên Server, thông báo tức thì qua UDP cho Client mở giao diện chat.
    - **[Từ chối]**: Từ chối yêu cầu, gửi thông báo kèm lý do cho Client.
    - **[Chấp nhận tất cả]**: Duyệt hàng loạt nhanh chóng.
- **Quản lý Client đang hoạt động:** Theo dõi danh sách Client online, hỗ trợ nút *Ngắt kết nối Client*.
- **Quản lý Lưu trữ:** Hiển thị danh sách các thư mục account và số file email, hỗ trợ nút **"Mở thư mục mail_data"** trực tiếp trong File Explorer của Windows.
- **Server Log Real-time:** Ghi lại mọi sự kiện gói tin, yêu cầu tham gia, quyết định duyệt, email gửi đi theo mốc thời gian `[HH:mm:ss]`.

### 2. Phía Client (Form Kết nối & Chat-Message)
- **Khung nhập thông tin Client:**
  - **Họ và tên của bạn** (Display Name).
  - **Địa chỉ Mail / Account** (Hỗ trợ cả dạng `sontien` hoặc `sontien@vku.udn.vn`).
  - **Địa chỉ IP của Client** (Tự động nhận diện IP LAN mạng nội bộ của máy, cho phép chỉnh sửa).
  - Cấu hình IP Server và Cổng UDP (mặc định `127.0.0.1:2346`).
- **Màn hình chờ phê duyệt:**
  - Sau khi bấm *Tham gia hệ thống*, Client hiển thị trạng thái chờ Admin duyệt với thanh tiến trình và thông tin đã gửi.
  - Khi Admin bấm **Chấp nhận**, Client lập tức chuyển vào giao diện chính.
  - Nếu Admin bấm **Từ chối**, Client nhận được thông báo lý do và quay lại form nhập.
- **Giao diện Chat-Message hiện đại sau khi vào hoạt động:**
  - **Cột trái:**
    - Profile người dùng: Avatar chữ cái đầu với màu sắc riêng, Họ tên, Mail, IP Client, huy hiệu *● Đã duyệt · Đang hoạt động*.
    - Nút **[Đổi nền]** chuyển đổi Sáng/Tối tức thì và nút **[Thoát]**.
    - Hộp thư đến (Inbox): Danh sách các file email (`new_email.txt` gắn nhãn `[SYS]`, các `mail_*.txt` gắn nhãn `[MAIL]`), nút *Làm mới*.
    - Danh sách thành viên Online: Bấm vào bất kỳ ai để tự động điền người nhận thư.
  - **Khung phải:**
    - Khung xem nội dung email (Mail Reader): Hiển thị nội dung thư dạng thẻ tin nhắn sạch đẹp. Khi click `new_email.txt`, hiển thị nguyên văn thư chào mừng của hệ thống.
    - Khung soạn & gửi email (Mail Composer): Nhập account người nhận, nội dung email, phím tắt `Ctrl + Enter` để gửi nhanh qua UDP.
    - Cơ chế tự động làm mới hộp thư ngầm mỗi 3.5 giây giúp thư mới gửi tới xuất hiện tức thì!

### 3. Đảm bảo 100% yêu cầu đề bài Lab 5 Bài 2
- Giao thức UDP: Dùng `DatagramSocket`, `DatagramPacket`.
- Khi tạo account mới / được Admin duyệt: Server tạo thư mục tương ứng trong `mail_data`, đồng thời tạo file `new_email.txt` với nội dung nguyên văn:
  ```text
  Thank you for using this service. we hope that you will feel comfortabl........
  ```
- Khi gửi email: Server nhận email, xác định account người nhận, tạo file `mail_<UUID>.txt` trong thư mục account đó với nội dung chính là email gửi.
- Khi đăng nhập/mở hộp thư: Server gửi toàn bộ danh sách tên file trong thư mục của account về Client.
- Hỗ trợ thêm lệnh `READ`: Cho phép đọc nội dung text của file email để hiển thị lên form chat.

---

## 🚀 Hướng dẫn Chạy chương trình

Cần cài đặt **JDK 17+** (`java` và `javac` có sẵn trong biến môi trường PATH).

### Cách 1: Sử dụng file Command Prompt (`.cmd`)

**1. Khởi động Server (Giao diện Admin Dashboard):**
```cmd
.\run-server.cmd
```
*(Nếu muốn chạy server ở chế độ terminal không mở GUI, thêm cờ: `.\run-server.cmd 2346 mail_data --headless`)*

**2. Khởi động Client (Giao diện Chat-Message):**
```cmd
.\run-client.cmd
```
*(Có thể mở nhiều cửa sổ Client khác nhau để mô phỏng nhiều người dùng chat/gửi email qua lại).*

### Cách 2: Sử dụng PowerShell (`.ps1`)

**Terminal 1 — Server:**
```powershell
.\run-server.ps1
```

**Terminal 2 & 3 — Client:**
```powershell
.\run-client.ps1
```

---

## 🧪 Chạy Kiểm thử Tự động (Integration Tests)

Chương trình đi kèm bộ test tích hợp toàn diện, kiểm tra:
1. Tạo account, thư mục đĩa và nguyên văn nội dung `new_email.txt`.
2. Kiểm tra tài khoản trùng, sai quy cách, tài khoản không tồn tại.
3. Gửi email tiếng Việt UTF-8, kiểm tra chống ghi đè và tính toàn vẹn dữ liệu.
4. Cơ chế **Server Admin phê duyệt Client** (`JOIN_REQ` -> `PENDING` -> `APPROVED`).
5. Cơ chế **Server Admin từ chối Client** (`REJECTED` kèm lý do).
6. Đọc nội dung email qua UDP (`READ`).
7. Cơ chế Timeout và gửi lại gói tin khi mạng chập chờn.

Để chạy test:
```cmd
.\test.cmd
```
Kết quả mong đợi:
```text
PASS: tao account, welcome, login, account trung/khong ton tai/ten sai
PASS: luu dung nguoi nhan/noi dung, khong ghi de, replay va du lieu sai
PASS: nhieu client, nhieu trang, du tat ca ten file va khong lan account
PASS: du lieu ton tai sau restart va SEND replay khong tao trung
PASS: co che Server Admin phe duyet/tu choi client va doc email
PASS: timeout va retry cung request ID khi mat phan hoi
ALL TESTS PASSED
```

---

## 📁 Cấu trúc Mã nguồn

```text
Lab5/Bai2_MailServer/
├── src/
│   ├── MailProtocol.java       # Định nghĩa khuôn dạng gói UDP, command và Base64 UTF-8
│   ├── MailStorage.java        # Quản lý đọc/ghi file, thư mục account trên máy Server
│   ├── ServerModels.java       # Model: PendingRequest, ActiveClient, ApprovalDecision
│   ├── ServerObserver.java     # Observer interface kết nối sự kiện Server với UI Admin
│   ├── MailServer.java         # Core UDP Socket Server, xử lý gói tin và logic duyệt
│   ├── ServerAdminFrame.java   # Giao diện UI Admin Dashboard (Thống kê, Duyệt, Log)
│   ├── MailClient.java         # UDP Client SDK (gửi/nhận, join request, read mail)
│   ├── MailTheme.java          # Bộ phong cách giao diện Dark Theme hiện đại
│   ├── ClientLoginForm.java    # Khung nhập Tên, Mail, IP Client & Màn hình chờ duyệt
│   ├── MailChatFrame.java      # Giao diện chính Client sau khi duyệt (Chat-Message UI)
│   ├── MailClientFrame.java    # Điểm khởi chạy Client chính
│   └── MailClientConsole.java  # Client dòng lệnh (Console) tùy chọn
├── tests/
│   └── MailIntegrationTest.java# Bộ kiểm thử UDP tự động
├── mail_data/                  # Thư mục dữ liệu mail thực tế trên Server
├── build.cmd                   # Script biên dịch
├── run-server.cmd / .ps1       # Script khởi động Server
├── run-client.cmd / .ps1       # Script khởi động Client
└── test.cmd                    # Script chạy kiểm thử
```
