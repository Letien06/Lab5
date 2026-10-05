import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Client UDP dùng được từ GUI, console và kiểm tra tích hợp. */
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

    public MailClient(String host, int port) throws IOException {
        this(host, port, 800, 3);
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
            // UDP connect chọn server và lọc nguồn phản hồi, không bắt tay TCP.
            socket.connect(address, port);
        } catch (RuntimeException ex) {
            socket.close();
            throw ex;
        }
    }

    public String createAccount(String account) throws IOException {
        return oneField(exchange(MailProtocol.request("CREATE", account)));
    }

    public String sendMail(String recipient, String body) throws IOException {
        if (body.isBlank() || body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MailProtocol.MAX_BODY_BYTES) {
            throw new IllegalArgumentException("Nhập nội dung email, tối đa 16000 byte UTF-8.");
        }
        return oneField(exchange(MailProtocol.request("SEND", recipient, body)));
    }

    public Mailbox login(String account) throws IOException {
        List<String> names = new ArrayList<>();
        String after = "";
        String normalizedAccount = null;
        boolean more;
        do {
            List<String> page = exchange(MailProtocol.request(after.isEmpty() ? "LOGIN" : "LIST", account, after));
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
        throw new SocketTimeoutException("Không nhận được xác nhận sau " + attempts
                + " lần gửi. Với thao tác gửi thư, hãy kiểm tra hộp thư người nhận trước khi gửi mới.");
    }

    @Override
    public void close() {
        socket.close();
    }
}
