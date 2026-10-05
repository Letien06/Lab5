# Lab 5 — Bài 2: Mail Server qua UDP

Project riêng với Bài 1, dùng **Java 17**, thư viện chuẩn Java và giao diện Swing. Không cần Maven hoặc database.

## Đúng ba chức năng của đề

| Chức năng | Server thực hiện |
|---|---|
| Tạo account | Tạo thư mục của account và file `new_email.txt` |
| Gửi email | Xác định account nhận, tạo file mới chứa nguyên nội dung email trong thư mục người nhận |
| Đăng nhập | Kiểm tra account tồn tại, gửi tất cả tên file trong thư mục về client |

Nội dung `new_email.txt` giữ nguyên câu trong đề, không thêm dấu ngoặc kép:

```text
Thank you for using this service. we hope that you will feel comfortabl........
```

Đề chưa quy định mật khẩu nên đăng nhập ở bài này là truy cập account bằng tên, **chưa có xác thực bằng mật khẩu**. Client hiển thị danh sách **tên file**; chức năng đọc/xóa thư không nằm trong phạm vi bài. Gửi thư không bắt buộc đăng nhập trước, vì đề không quy định người gửi hoặc phiên đăng nhập.

## Chạy trên Windows

Mở PowerShell trong folder `Bai2_MailServer`, cần `java` và `javac` trong PATH.

**Terminal 1 — server:**

```powershell
.\run-server.cmd
```

**Terminal 2 — client:**

```powershell
.\run-client.cmd
```

Cổng mặc định **2346** được chọn cho Bài 2 vì đề không chỉ định cổng. Bài 1 vẫn dùng 2345 nên hai bài có thể chạy đồng thời.

Client mặc định dùng `127.0.0.1:2346`. Nếu chạy hai máy trong LAN, nhập IPv4 của máy server tại client; máy server cần cho phép UDP vào cổng 2346 trong firewall.

## Demo từng bước

1. Chạy server rồi mở client.
2. Nhập account `alice`, nhấn **Tạo account**. Server tạo `mail_data/alice/new_email.txt`.
3. Nhấn **Đăng nhập**. Danh sách có `new_email.txt`.
4. Nhập account `bob`, nhấn **Tạo account** rồi **Đăng nhập**.
5. Ở phần soạn thư, nhập người nhận `bob` và nội dung có thể gồm tiếng Việt, nhiều dòng; nhấn **Gửi email**.
6. Nhấn **Làm mới danh sách** của bob. Có file mail mới cùng `new_email.txt`.
7. Mở file mail ở máy server để kiểm tra nội dung đã lưu đúng.
8. Tắt server bằng Ctrl+C, chạy lại và đăng nhập bob: các file vẫn còn.

Có thể mở hai client để demo: client thứ nhất tạo alice/gửi tới bob; client thứ hai đăng nhập bob rồi làm mới danh sách. Danh sách hiển thị account đã đăng nhập ở dòng nhãn; sửa ô Account chỉ chọn account cho lần đăng nhập tiếp theo.

## Dữ liệu và quy tắc

Server tạo thư mục `mail_data` trong folder project khi chạy bằng script:

```text
mail_data/
  alice/
    new_email.txt
  bob/
    new_email.txt
    mail_<UUID>.txt
    mail_<UUID-khác>.txt
```

- Account được chuyển về chữ thường và bỏ khoảng trắng đầu/cuối, dài 3–32 ký tự, bắt đầu bằng chữ `a-z`, sau đó dùng chữ, số hoặc `_`.
- Không cho phép dấu phân cách đường dẫn, `..` hoặc tên thiết bị Windows như `con`, `nul`, `com1`.
- Tạo account trùng: báo lỗi, giữ nguyên thư cũ.
- Người nhận hoặc account đăng nhập chưa tồn tại: báo lỗi; không tự tạo account.
- Nội dung thư được lưu UTF-8, giữ nguyên xuống dòng và ký tự đặc biệt. Nội dung rỗng/toàn khoảng trắng bị từ chối.
- Mỗi thư có tên riêng dựa trên UUID của yêu cầu gửi. File chỉ chứa **nội dung email**, không tự thêm header, người gửi hoặc tiêu đề.
- Thư tối đa **16000 byte UTF-8**, không phải 16000 ký tự tiếng Việt. Đây là giới hạn thiết kế cho bài lab, không phải giới hạn thầy ghi trong đề.
- Bài dùng UDP trên mạng nội bộ. Gói lớn có thể bị phân mảnh IP; client có timeout/gửi lại, chưa xây dựng cơ chế chia nội dung thư thành các gói nhỏ.
- Đăng nhập lấy danh sách theo thứ tự tên file, mỗi trang tối đa 40 tên. Client tự lấy tiếp các trang và hiển thị danh sách hoàn chỉnh. Khi có thư mới trong lúc đang lấy danh sách, nhấn Làm mới để lấy danh sách mới.

## Cấu trúc code

```text
src/
  MailProtocol.java       Định dạng thông điệp và giới hạn UDP
  MailStorage.java        Tạo account, lưu thư, liệt kê tên file
  MailServer.java         Nhận UDP, xử lý thao tác, trả kết quả
  MailClient.java         Gửi/nhận, timeout/retry, lấy đủ các trang
  MailClientFrame.java    Giao diện Swing
  MailClientConsole.java  Console tùy chọn
tests/
  MailIntegrationTest.java
build.cmd
run-server.cmd
run-client.cmd
test.cmd
```

Mạng chạy trên luồng nền, thay đổi giao diện thực hiện trên Swing EDT. Đóng cửa sổ sẽ đóng socket đang chạy và dừng worker. Khi đổi IP/cổng, danh sách của server cũ được xóa.

## Giao thức UDP

Mỗi datagram tối đa 24000 byte, cấu trúc:

```text
COMMAND|requestId|Base64(field1)|Base64(field2)|...
```

Các trường mã hóa từ UTF-8 sang Base64 để nội dung email chứa `|`, xuống dòng hoặc tiếng Việt không làm sai cấu trúc. Base64 là cách đóng gói, không phải mã hóa bảo mật.

| Command | Các trường yêu cầu | Các trường trong phản hồi OK |
|---|---|---|
| CREATE | account | account đã chuẩn hóa |
| SEND | account người nhận, nội dung | tên file đã lưu |
| LOGIN / LIST | account, tên cuối trang trước (rỗng ở trang đầu) | account, hasMore, các tên file |

Phản hồi có command `OK` hoặc `ERROR`, kèm requestId của yêu cầu. Lỗi gồm mã lỗi và thông báo. Gói tin không thể đọc được trả `INVALID_PACKET` với UUID bằng 0 vì server chưa xác định được mã yêu cầu.

Client chờ tối đa 800 ms mỗi lần, thử tối đa 3 lần với **cùng requestId**. Server ghi nhớ 256 phản hồi gần nhất trong tối đa một phút để trả lại kết quả khi nhận gói lặp. Riêng SEND dùng requestId làm tên file, nên gửi lại cùng yêu cầu sau khi server khởi động lại cũng không tạo thư trùng. Client bỏ qua phản hồi không khớp requestId.

Nếu cả ba lần đều không nhận được xác nhận, chưa thể khẳng định server chưa lưu thư. Kiểm tra danh sách của người nhận trước khi nhấn Gửi lại, vì lần nhấn mới tạo requestId mới.

## Console và IDE

Khi server đang chạy:

```powershell
.\run-client.cmd --console create alice
.\run-client.cmd --console create bob
.\run-client.cmd --console send bob "Hello, this is a test email."
.\run-client.cmd --console login bob
```

Với tiếng Việt hoặc nội dung nhiều dòng, nên dùng GUI hoặc file UTF-8 vì JDK 17 trên Windows có thể làm mất dấu trong tham số dòng lệnh. Ví dụ PowerShell tạo file UTF-8 không BOM rồi gửi:

```powershell
[System.IO.File]::WriteAllText((Join-Path (Get-Location) 'email.txt'), "Xin chào thầy!`nEmail tiếng Việt.", [System.Text.UTF8Encoding]::new($false))
.\run-client.cmd --console send-file bob email.txt
```

IP và cổng là hai tham số tùy chọn ở cuối, ví dụ:

```powershell
.\run-client.cmd --console login bob 192.168.1.10 2346
```

Chạy server với cổng/thư mục khác:

```powershell
.\run-server.cmd 3456 "D:\MailLabData"
```

Chạy trong IntelliJ/Eclipse: chọn JDK 17, đặt `src` là source root, chạy `main` của `MailServer` và `MailClientFrame`. Với server, đặt working directory là folder project để dữ liệu nằm ở `mail_data` của project. Không truyền tham số để mở GUI.

## Kiểm tra

```powershell
.\test.cmd
```

Test dùng server ở cổng tạm và thư mục dữ liệu riêng trong `out`, không đụng đến `mail_data`:

- Tạo account, nội dung welcome chính xác, tên account sai/trùng.
- Đăng nhập/gửi thư đến account không tồn tại.
- Lưu đúng người nhận, giữ nguyên tiếng Việt/xuống dòng, không ghi đè.
- Yêu cầu trùng, requestId xung đột, thư rỗng/quá lớn, gói tin sai.
- Danh sách nhiều hơn một trang, nhiều client, không lẫn hộp thư.
- Khởi động lại server và gửi lại cùng yêu cầu không tạo thư thứ hai.
- Timeout và gửi lại cùng requestId khi mất phản hồi.

Dữ liệu test, file biên dịch và `mail_data` được bỏ qua trong `.gitignore`.
