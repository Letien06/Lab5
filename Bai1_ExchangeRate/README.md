# Lab 5 — Bài 1: ExchangeRate qua UDP

Java 17, dùng thư viện chuẩn Java (DatagramSocket và Swing), không cần Maven hay thư viện ngoài.

## Đối chiếu yêu cầu đề

- `ExchangeRateServer` nhận yêu cầu UDP tại cổng **2345** mặc định.
- Mỗi yêu cầu hợp lệ nhận ba giá trị random: Tokyo, Newyork, Hồng Kông và ngày giờ mới nhất của server.
- `ExchangeRateTable` gửi yêu cầu ngay khi nhấn **Bắt đầu**, sau đó mỗi **1 giây**.
- Client hiển thị bảng, có thể đổi IP/cổng để chạy trên hai máy.
- Client chờ tối đa 800 ms mỗi chu kỳ; nếu mất phản hồi, giữ bảng cũ, báo trạng thái và thử lại chu kỳ tiếp theo.
- Nhận dữ liệu ở luồng nền, cập nhật Swing trên EDT; dừng/đóng cửa sổ sẽ đóng socket.

Các giá trị chỉ là **dữ liệu mô phỏng**, không phải tỷ giá thật. Đề không quy định đồng tiền hoặc khoảng random. Bài chọn Tokyo 100–200, Newyork 200–300, Hồng Kông 50–150, làm tròn hai chữ số. Server random riêng cho mỗi yêu cầu; các client có thể nhận bộ giá trị khác nhau.

## Chạy trên Windows

Mở PowerShell trong folder `Bai1_ExchangeRate`. Cần `java` và `javac` trong PATH.

**Terminal 1 — server:**

```powershell
.\run-server.cmd
```

**Terminal 2 — client:**

```powershell
.\run-client.cmd
```

Giữ `127.0.0.1` và cổng `2345` khi chạy cùng máy, nhấn **Bắt đầu**. Nhấn **Dừng** để ngừng cập nhật; nhấn **Bắt đầu** để chạy lại. Ctrl+C trong terminal server để tắt server.

Nếu chạy hai máy trong LAN: chạy server ở máy thứ nhất, nhập IPv4 của máy server tại client, giữ cổng 2345. Máy server cần cho phép UDP vào cổng 2345 trong firewall. Không dùng `127.0.0.1` để kết nối sang máy khác.

**Client console (tùy chọn):**

```powershell
.\run-client.cmd --console 127.0.0.1 2345 5
```

Lệnh trên thực hiện 5 chu kỳ. Bỏ số 5 hoặc dùng 0 để chạy liên tục. Console vẫn thử lại khi không nhận được phản hồi.

**Biên dịch/chạy thủ công hoặc trong IDE:**

```powershell
.\build.cmd
java -Dfile.encoding=UTF-8 -cp out ExchangeRateServer
java -Dfile.encoding=UTF-8 -cp out ExchangeRateTable
```

Trong IntelliJ/Eclipse: dùng JDK 17, đặt `src` là source root rồi chạy `main` của hai lớp trên. Nếu cần cổng khác, server nhận một tham số: `ExchangeRateServer 3456`; client nhập cổng tương ứng.

## Cấu trúc

```text
Bai1_ExchangeRate/
  src/
    ExchangeRateProtocol.java       Định dạng dữ liệu UTF-8 và kiểm tra dữ liệu
    ExchangeRateServer.java         Nhận yêu cầu, random, trả phản hồi
    ExchangeRateClient.java         Gửi/nhận UDP, timeout, bỏ phản hồi cũ
    ExchangeRateTable.java          Bảng Swing và chế độ console
  tests/
    ExchangeRateIntegrationTest.java
  build.cmd
  run-server.cmd
  run-client.cmd
  test.cmd
```

## Giao thức và luồng

Một thông điệp UTF-8 nằm trong một datagram, các trường ngăn cách bằng `|`:

```text
Client -> Server: RATE_REQUEST|requestId
Server -> Client: RATE_RESPONSE|requestId|ISO_OFFSET_DATE_TIME|tokyo|newYork|hongKong
Yêu cầu sai:      ERROR|INVALID_REQUEST
```

Client dùng UUID làm mã yêu cầu để bỏ qua phản hồi trễ của chu kỳ trước. Socket UDP phía client được `connect` tới IP/cổng server để lọc nguồn phản hồi; đây không phải kết nối/bắt tay TCP. Server trả về IP/cổng nguồn lấy từ DatagramPacket, nên phục vụ được nhiều client mà không cần một socket riêng cho từng client.

Ngày giờ lấy tại server khi xử lý yêu cầu, có thông tin múi giờ. UDP có thể mất gói; chu kỳ tiếp theo gửi yêu cầu mới. Dữ liệu nhỏ, không cần chia gói hoặc xây dựng giao thức truyền tin tin cậy.

## Kiểm tra

```powershell
.\test.cmd
```

Kiểm tra UDP thật trên loopback với cổng tạm: hai client, đủ ba giá trị và thời gian, yêu cầu sai không làm server dừng, timeout, bỏ phản hồi cũ/sai ngày giờ/sai giá trị. Test không cần chạy server trước.

Kiểm tra giao diện thủ công: mở server và client; nhấn Bắt đầu; quan sát dữ liệu đổi mỗi giây; tắt server để thấy thông báo lỗi; chạy lại server để bảng tự cập nhật tiếp; thử Dừng/Bắt đầu và đóng cửa sổ. Chỉ tạo project Bài 1 ở bước này; Bài 2 sẽ nằm ở folder riêng.
