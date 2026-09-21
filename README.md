# Thực hành 3 - TCP Time Server/Client

Chương trình gồm một server phục vụ song song nhiều client. Mỗi client có trạng
thái riêng: khi nhận `PAUSE`, server chỉ ngừng gửi thời gian cho client đó; khi
nhận `RESUME`, server tiếp tục gửi thời gian mỗi giây.

## Yêu cầu và cách chương trình hoạt động

- **Server song song:** mỗi client kết nối được phục vụ bởi một `ClientHandler`
  riêng nên nhiều client có thể dùng đồng thời.
- **Stateful:** biến `paused` thuộc từng `ClientHandler`, vì vậy server nhớ trạng
  thái Pause/Resume độc lập cho từng client.
- **Gửi thời gian:** khi client không bị Pause, server gửi ngày giờ hiện tại sau
  mỗi một giây bằng TCP.
- **Phát hiện thay đổi giờ hệ thống:** một luồng giám sát dùng chung so sánh
  `System.nanoTime()` với `System.currentTimeMillis()` để nhận biết việc chỉnh
  đồng hồ, đồng thời nạp lại múi giờ mặc định của JVM để nhận thay đổi từ mục
  **Settings > Time & language > Date & time > Time zone** trên Windows. Khi có
  thay đổi, mọi client nhận ngay thời gian mới kèm prefix `TIMECHANGE:`, kể cả
  client đang Pause, và hiển thị cảnh báo trong 5 giây.
- **Điều khiển:** nút Pause gửi `PAUSE`, nút Resume gửi `RESUME`. Server vẫn đọc
  lệnh trong lúc tạm dừng gửi thời gian.
- **Kết nối LAN:** client nhập IPv4 của máy chạy server. Cổng TCP `7000` đã được
  đặt cố định trong mã nguồn của cả server và client.

## Biên dịch và chạy trên một máy

```bash
javac timeserver.java timeclient.java
java timeserver
```

Mở terminal khác để chạy một hoặc nhiều client:

```bash
java timeclient
```

Cả server và client luôn dùng cổng `7000`. Khi mở client không có tham số, hộp
thoại nhập IP sẽ xuất hiện trước màn hình đồng hồ:

```bash
java timeclient
```

## Chạy trong mạng LAN

1. Kết nối hai máy vào cùng mạng LAN.
2. Trên máy server, tìm địa chỉ IPv4 bằng `ip addr` (Linux) hoặc `ipconfig`
   (Windows), rồi chạy `java timeserver`.
3. Cho phép TCP port 7000 qua firewall của máy server nếu cần.
4. Trên máy client, thay `192.168.1.10` bằng IPv4 của máy server:

```bash
java timeclient 192.168.1.10
```

Nút **Pause** gửi chuỗi `PAUSE`; nút **Resume** gửi chuỗi `RESUME`. Server vẫn
tiếp tục phục vụ và gửi thời gian cho các client khác trong lúc một client bị
tạm dừng.

## Chạy bằng script

Các script tự tạo thư mục `build/`, biên dịch file `.class` vào đó và chạy từ
classpath `build`. Thư mục này đã được khai báo trong `.gitignore`.

Trên Linux, cấp quyền thực thi một lần rồi chạy:

```bash
chmod +x run_server.sh run_client.sh
./run_server.sh                 # cổng cố định 7000
./run_client.sh                 # hiện hộp thoại nhập IP
./run_client.sh 192.168.1.10    # server LAN, cổng 7000
```

Khi chạy client không có tham số, một hộp thoại sẽ yêu cầu nhập IP server trước
khi mở màn hình đồng hồ. Server chạy thẳng bằng cổng `7000` và không hỏi cổng.

Trên Windows, có thể nhấp đúp các tệp `.bat` hoặc dùng Command Prompt:

```bat
run_server.bat
run_client.bat                  REM hiện hộp thoại nhập IP
run_client.bat 192.168.1.10
```
