import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Client UDP dùng được từ GUI (form chat-message), console và test tích hợp.
 */
public final class MailClient implements AutoCloseable {
    private final DatagramSocket socket;
    private final int timeoutMillis;
    private final int attempts;

    public static final class ServerException extends IOException {
        private final String code;

        public ServerException(String code, String detail) {
            super(detail);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    public record Mailbox(String account, List<String> filenames) { }

    public record JoinStatus(String status, String account, String name, String reason) { }

    public record RemoteClient(String account, String name, String ip) { }

    public MailClient(String host, int port) throws IOException {
        this(host, port, 1000, 3);
    }

    public MailClient(String host, int port, int timeoutMillis, int attempts) throws IOException {
        if (host.isBlank() || port < 1 || port > 65535 || timeoutMillis < 1 || attempts < 1) {
            throw new IllegalArgumentException("Địa chỉ server, cổng hoặc thời gian chờ không hợp lệ.");
        }
        InetAddress address = InetAddress.getByName(host);
        this.timeoutMillis = timeoutMillis;
        this.attempts = attempts;
        socket = new DatagramSocket();
        try {
            // UDP connect chọn server và lọc nguồn phản hồi
            socket.connect(address, port);
        } catch (RuntimeException ex) {
            socket.close();
            throw ex;
        }
    }

    /**
     * Gửi yêu cầu xin tham gia hệ thống tới Server:
     * - type: "CREATE" (tạo mới) hoặc "LOGIN" (đăng nhập)
     * - name: Tên người dùng
     * - account: Địa chỉ mail/tài khoản
     * - clientIp: Địa chỉ IP của client
     * Trả về requestId để kiểm tra trạng thái phê duyệt từ Admin.
     */
    public String requestJoin(String type, String name, String account, String clientIp) throws IOException {
        List<String> res = exchange(MailProtocol.request(MailProtocol.CMD_JOIN_REQ, type, name, account, clientIp));
        if (res.size() < 2) {
            throw new IOException("Phản hồi JOIN_REQ không hợp lệ.");
        }
        return res.get(1);
    }

    public String requestJoin(String name, String account, String clientIp) throws IOException {
        return requestJoin("CREATE_OR_LOGIN", name, account, clientIp);
    }

    /**
     * Kiểm tra xem Server Admin đã phê duyệt yêu cầu chưa.
     */
    public JoinStatus checkJoinStatus(String requestId) throws IOException {
        List<String> res = exchange(MailProtocol.request(MailProtocol.CMD_CHECK_JOIN, requestId));
        if (res.isEmpty()) {
            throw new IOException("Phản hồi CHECK_JOIN rỗng.");
        }
        String status = res.get(0);
        if ("APPROVED".equals(status)) {
            String acc = res.size() > 1 ? res.get(1) : "";
            String name = res.size() > 2 ? res.get(2) : "";
            return new JoinStatus("APPROVED", acc, name, null);
        } else if ("REJECTED".equals(status)) {
            String reason = res.size() > 1 ? res.get(1) : "Admin đã từ chối.";
            return new JoinStatus("REJECTED", null, null, reason);
        } else {
            return new JoinStatus("PENDING", null, null, null);
        }
    }

    public String createAccount(String account) throws IOException {
        return oneField(exchange(MailProtocol.request(MailProtocol.CMD_CREATE, account)));
    }

    public String sendMail(String recipient, String body) throws IOException {
        if (body.isBlank() || body.getBytes(StandardCharsets.UTF_8).length > MailProtocol.MAX_BODY_BYTES) {
            throw new IllegalArgumentException("Nhập nội dung email, tối đa 16000 byte UTF-8.");
        }
        return oneField(exchange(MailProtocol.request(MailProtocol.CMD_SEND, recipient, body)));
    }

    /**
     * Đọc nội dung email từ server (bao gồm new_email.txt hoặc file thư gửi đến).
     */
    public String readMail(String account, String filename) throws IOException {
        List<String> res = exchange(MailProtocol.request(MailProtocol.CMD_READ, account, filename));
        if (res.size() < 2) {
            throw new IOException("Dữ liệu đọc email không hợp lệ.");
        }
        return res.get(1);
    }

    public Mailbox login(String account) throws IOException {
        List<String> names = new ArrayList<>();
        String after = "";
        String normalizedAccount = null;
        boolean more;
        do {
            List<String> page = exchange(MailProtocol.request(after.isEmpty() ? MailProtocol.CMD_LOGIN : MailProtocol.CMD_LIST, account, after));
            if (page.size() < 2 || !(page.get(1).equals("true") || page.get(1).equals("false"))) {
                throw new IOException("Danh sách thư không đúng định dạng.");
            }
            if (normalizedAccount != null && !normalizedAccount.equals(page.get(0))) {
                throw new IOException("Account trong phản hồi không khớp.");
            }
            normalizedAccount = page.get(0);
            List<String> filenames = page.subList(2, page.size());
            for (String filename : filenames) {
                if (filename.compareTo(after) <= 0) {
                    throw new IOException("Thứ tự danh sách thư không hợp lệ.");
                }
                names.add(filename);
                after = filename;
            }
            more = Boolean.parseBoolean(page.get(1));
            if (more && filenames.isEmpty()) {
                throw new IOException("Trang danh sách thư bị rỗng.");
            }
        } while (more);
        return new Mailbox(normalizedAccount, List.copyOf(names));
    }

    /**
     * Lấy danh sách client đang hoạt động trên Server.
     */
    public List<RemoteClient> listActiveClients() throws IOException {
        List<String> res = exchange(MailProtocol.request(MailProtocol.CMD_CLIENTS_LIST));
        List<RemoteClient> clients = new ArrayList<>();
        for (int i = 0; i + 2 < res.size(); i += 3) {
            clients.add(new RemoteClient(res.get(i), res.get(i + 1), res.get(i + 2)));
        }
        return clients;
    }

    public void disconnect(String account) {
        try {
            exchange(MailProtocol.request(MailProtocol.CMD_DISCONNECT, account));
        } catch (Exception ignored) { }
    }

    private static String oneField(List<String> fields) throws IOException {
        if (fields.size() != 1) {
            throw new IOException("Phản hồi server không đúng định dạng.");
        }
        return fields.get(0);
    }

    private synchronized List<String> exchange(MailProtocol.Message request) throws IOException {
        byte[] bytes = MailProtocol.encode(request);
        byte[] buffer = new byte[MailProtocol.MAX_PACKET_BYTES + 1];
        for (int attempt = 0; attempt < attempts; attempt++) {
            socket.send(new DatagramPacket(bytes, bytes.length));
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            while (true) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    break;
                }
                socket.setSoTimeout((int) Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining)));
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(packet);
                } catch (SocketTimeoutException ex) {
                    break;
                }
                MailProtocol.Message response;
                try {
                    response = MailProtocol.decode(packet.getData(), packet.getOffset(), packet.getLength());
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                if (!response.id().equals(request.id())) {
                    continue;
                }
                if (response.command().equals("OK")) {
                    return response.fields();
                }
                if (response.command().equals("ERROR") && response.fields().size() == 2) {
                    throw new ServerException(response.fields().get(0), response.fields().get(1));
                }
            }
        }
        throw new SocketTimeoutException("Không nhận được phản hồi sau " + attempts + " lần thử.");
    }

    @Override
    public void close() {
        socket.close();
    }
}
