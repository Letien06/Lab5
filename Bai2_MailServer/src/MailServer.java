import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** UDP server: CREATE, SEND, LOGIN/LIST. Xử lý tuần tự nên không có race khi tạo account. */
public final class MailServer implements AutoCloseable {
    private final DatagramSocket socket;
    private final MailStorage storage;
    private record Key(SocketAddress source, String id) { }
    private record Cached(MailProtocol.Message request, byte[] response, long createdAt) { }
    private final Map<Key, Cached> cache = new LinkedHashMap<>();

    public MailServer(int port, Path root) throws IOException {
        storage = new MailStorage(root);
        socket = new DatagramSocket(port);
    }

    public int getPort() {
        return socket.getLocalPort();
    }

    public Path getStorageRoot() {
        return storage.root();
    }

    public void serve() throws IOException {
        byte[] buffer = new byte[MailProtocol.MAX_PACKET_BYTES + 1];
        while (!socket.isClosed()) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
                byte[] response = handlePacket(packet);
                socket.send(new DatagramPacket(response, response.length, packet.getSocketAddress()));
            } catch (SocketException ex) {
                if (!socket.isClosed()) {
                    throw ex;
                }
            }
        }
    }

    private byte[] handlePacket(DatagramPacket packet) {
        MailProtocol.Message request;
        try {
            request = MailProtocol.decode(packet.getData(), packet.getOffset(), packet.getLength());
        } catch (IllegalArgumentException ex) {
            return MailProtocol.encode(MailProtocol.error("00000000-0000-0000-0000-000000000000",
                    "INVALID_PACKET", ex.getMessage()));
        }
        long now = System.nanoTime();
        cache.entrySet().removeIf(entry -> now - entry.getValue().createdAt() > TimeUnit.MINUTES.toNanos(1));
        Key key = new Key(packet.getSocketAddress(), request.id());
        Cached existing = cache.get(key);
        if (existing != null) {
            return existing.request().equals(request) ? existing.response()
                    : MailProtocol.encode(MailProtocol.error(request.id(), "REQUEST_CONFLICT", "Mã yêu cầu đã được dùng."));
        }
        MailProtocol.Message result;
        try {
            result = process(request);
        } catch (MailStorage.MailException ex) {
            result = MailProtocol.error(request.id(), ex.code(), ex.getMessage());
        } catch (IOException ex) {
            System.err.println("Lỗi lưu trữ: " + ex.getMessage());
            result = MailProtocol.error(request.id(), "STORAGE_ERROR", "Server không thể đọc/ghi dữ liệu mail.");
        } catch (IllegalArgumentException ex) {
            result = MailProtocol.error(request.id(), "INVALID_REQUEST", ex.getMessage());
        }
        byte[] response;
        try {
            response = MailProtocol.encode(result);
        } catch (IllegalArgumentException ex) {
            response = MailProtocol.encode(MailProtocol.error(request.id(), "RESPONSE_TOO_LARGE", "Danh sách tên file quá lớn."));
        }
        if (cache.size() >= 256) {
            cache.remove(cache.keySet().iterator().next());
        }
        cache.put(key, new Cached(request, response, now));
        System.out.printf("%s | %s | %s%n", packet.getSocketAddress(), request.command(), result.command());
        return response;
    }

    private MailProtocol.Message process(MailProtocol.Message request) throws IOException, MailStorage.MailException {
        List<String> fields = request.fields();
        return switch (request.command()) {
            case "CREATE" -> {
                requireFields(fields, 1);
                yield MailProtocol.ok(request.id(), List.of(storage.createAccount(fields.get(0))));
            }
            case "SEND" -> {
                requireFields(fields, 2);
                yield MailProtocol.ok(request.id(), List.of(storage.sendMail(fields.get(0), fields.get(1), request.id())));
            }
            case "LOGIN", "LIST" -> {
                requireFields(fields, 2);
                var page = storage.listMail(fields.get(0), fields.get(1));
                List<String> payload = new ArrayList<>();
                payload.add(page.account());
                payload.add(Boolean.toString(page.hasMore()));
                payload.addAll(page.filenames());
                yield MailProtocol.ok(request.id(), payload);
            }
            default -> throw new IllegalArgumentException("Thao tác không được hỗ trợ.");
        };
    }

    private static void requireFields(List<String> fields, int count) {
        if (fields.size() != count) {
            throw new IllegalArgumentException("Số trường dữ liệu không đúng.");
        }
    }

    @Override
    public void close() {
        socket.close();
    }

    public static void main(String[] args) {
        try {
            if (args.length > 2) {
                throw new IllegalArgumentException("Cách chạy: MailServer [port] [storageFolder]");
            }
            int port = args.length > 0 ? Integer.parseInt(args[0]) : MailProtocol.DEFAULT_PORT;
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Cổng phải nằm trong khoảng 1..65535.");
            }
            Path root = Path.of(args.length > 1 ? args[1] : "mail_data");
            try (var server = new MailServer(port, root)) {
                Runtime.getRuntime().addShutdownHook(new Thread(server::close));
                System.out.println("MailServer đang lắng nghe UDP tại cổng " + server.getPort());
                System.out.println("Thư mục lưu email: " + server.getStorageRoot());
                System.out.println("Nhấn Ctrl+C để dừng.");
                server.serve();
            }
        } catch (IOException | IllegalArgumentException ex) {
            System.err.println("Không thể chạy MailServer: " + ex.getMessage());
            System.exit(1);
        }
    }
}
