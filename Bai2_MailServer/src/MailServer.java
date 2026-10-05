import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;

/**
 * UDP Server cho Mail Server (Lab 5 - Bài 2).
 * Hỗ trợ giao diện Admin duyệt Client, quản lý lưu trữ trên đĩa,
 * và tích hợp hoàn chỉnh với giao diện Client phong cách chat-message.
 */
public final class MailServer implements AutoCloseable {
    private final DatagramSocket socket;
    private final MailStorage storage;

    private record Key(SocketAddress source, String id) { }
    private record Cached(MailProtocol.Message request, byte[] response, long createdAt) { }
    private final Map<Key, Cached> cache = new LinkedHashMap<>();

    // Quản lý phê duyệt và danh sách client
    private final Map<String, ServerModels.PendingRequest> pendingRequests = new ConcurrentHashMap<>();
    private final Map<String, ServerModels.ApprovalDecision> approvalDecisions = new ConcurrentHashMap<>();
    private final Map<String, ServerModels.ActiveClient> activeClients = new ConcurrentHashMap<>();
    private final List<ServerObserver> observers = new CopyOnWriteArrayList<>();

    public MailServer(int port, Path root) throws IOException {
        storage = new MailStorage(root);
        socket = new DatagramSocket(port);
    }

    public void addObserver(ServerObserver observer) {
        if (observer != null && !observers.contains(observer)) {
            observers.add(observer);
            if (!socket.isClosed()) {
                observer.serverStarted(getPort(), storage.root().toString());
            }
        }
    }

    public int getPort() {
        return socket.getLocalPort();
    }

    public Path getStorageRoot() {
        return storage.root();
    }

    public MailStorage getStorage() {
        return storage;
    }

    public Collection<ServerModels.ActiveClient> getActiveClients() {
        return activeClients.values();
    }

    public Collection<ServerModels.PendingRequest> getPendingRequests() {
        return pendingRequests.values();
    }

    public boolean isClientActive(String account) {
        try {
            String norm = MailStorage.accountName(account);
            return activeClients.containsKey(norm);
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Admin chấp nhận client vào hoạt động:
     * - Nếu là yêu cầu tạo account hoặc thư mục chưa có: tạo thư mục và new_email.txt ("Thank you for using this service...").
     * - Thêm vào danh sách active client.
     * - Gửi gói UDP phản hồi chấp thuận ngay lập tức cho client.
     */
    public synchronized void approveClient(String requestId) {
        ServerModels.PendingRequest req = pendingRequests.remove(requestId);
        if (req == null) return;

        try {
            String normAccount = MailStorage.accountName(req.account());
            if (!storage.accountExists(normAccount)) {
                storage.createAccount(normAccount);
            }
            ServerModels.ActiveClient active = new ServerModels.ActiveClient(
                    normAccount, req.name(), req.clientIp(), req.address(), System.currentTimeMillis()
            );
            activeClients.put(normAccount, active);
            approvalDecisions.put(requestId, new ServerModels.ApprovalDecision(
                    ServerModels.ApprovalState.APPROVED, normAccount, req.name(), null
            ));

            // Gửi gói UDP xác nhận cho client
            try {
                MailProtocol.Message approvalMsg = MailProtocol.ok(requestId, "APPROVED", normAccount, req.name());
                byte[] data = MailProtocol.encode(approvalMsg);
                socket.send(new DatagramPacket(data, data.length, req.address()));
            } catch (Exception ex) {
                log("Không thể gửi UDP phản hồi duyệt trực tiếp: " + ex.getMessage());
            }

            for (ServerObserver obs : observers) {
                obs.clientApproved(requestId, req.name(), normAccount, req.clientIp());
                obs.clientConnected(normAccount, req.name(), req.clientIp(), req.address().toString());
            }
        } catch (Exception ex) {
            log("Lỗi khi duyệt client: " + ex.getMessage());
        }
    }

    /**
     * Admin từ chối yêu cầu của client:
     * - Ghi nhận trạng thái REJECTED.
     * - Gửi thông báo từ chối qua UDP cho client.
     */
    public synchronized void rejectClient(String requestId, String reason) {
        ServerModels.PendingRequest req = pendingRequests.remove(requestId);
        String finalReason = (reason != null && !reason.isBlank()) ? reason : "Admin đã từ chối yêu cầu tham gia.";
        String name = req != null ? req.name() : "Client";
        String acc = req != null ? req.account() : "?";

        approvalDecisions.put(requestId, new ServerModels.ApprovalDecision(
                ServerModels.ApprovalState.REJECTED, acc, name, finalReason
        ));

        if (req != null) {
            try {
                MailProtocol.Message rejectMsg = MailProtocol.ok(requestId, "REJECTED", finalReason);
                byte[] data = MailProtocol.encode(rejectMsg);
                socket.send(new DatagramPacket(data, data.length, req.address()));
            } catch (Exception ex) {
                log("Không thể gửi UDP phản hồi từ chối: " + ex.getMessage());
            }
        }

        for (ServerObserver obs : observers) {
            obs.clientRejected(requestId, name, acc, finalReason);
        }
    }

    /**
     * Admin ngắt kết nối một client đang hoạt động.
     */
    public synchronized void disconnectClient(String account) {
        try {
            String norm = MailStorage.accountName(account);
            ServerModels.ActiveClient removed = activeClients.remove(norm);
            if (removed != null) {
                for (ServerObserver obs : observers) {
                    obs.clientDisconnected(norm, "Admin ngắt kết nối");
                }
            }
        } catch (Exception ex) {
            log("Lỗi khi ngắt kết nối client: " + ex.getMessage());
        }
    }

    public void serve() throws IOException {
        for (ServerObserver obs : observers) {
            obs.serverStarted(getPort(), storage.root().toString());
        }

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
            result = process(request, packet);
        } catch (MailStorage.MailException ex) {
            result = MailProtocol.error(request.id(), ex.code(), ex.getMessage());
        } catch (IOException ex) {
            log("Lỗi lưu trữ: " + ex.getMessage());
            result = MailProtocol.error(request.id(), "STORAGE_ERROR", "Server không thể đọc/ghi dữ liệu mail.");
        } catch (IllegalArgumentException ex) {
            result = MailProtocol.error(request.id(), "INVALID_REQUEST", ex.getMessage());
        }

        byte[] response;
        try {
            response = MailProtocol.encode(result);
        } catch (IllegalArgumentException ex) {
            response = MailProtocol.encode(MailProtocol.error(request.id(), "RESPONSE_TOO_LARGE", "Kích thước dữ liệu quá lớn cho gói tin UDP."));
        }

        if (cache.size() >= 256) {
            cache.remove(cache.keySet().iterator().next());
        }
        cache.put(key, new Cached(request, response, now));
        return response;
    }

    private MailProtocol.Message process(MailProtocol.Message request, DatagramPacket packet)
            throws IOException, MailStorage.MailException {
        List<String> fields = request.fields();
        return switch (request.command()) {
            case MailProtocol.CMD_CREATE -> {
                requireFields(fields, 1);
                String acc = storage.createAccount(fields.get(0));
                log("Tạo account: " + acc);
                yield MailProtocol.ok(request.id(), List.of(acc));
            }
            case MailProtocol.CMD_SEND -> {
                requireFields(fields, 2);
                String recipient = fields.get(0);
                String body = fields.get(1);
                String filename = storage.sendMail(recipient, body, request.id());
                for (ServerObserver obs : observers) {
                    obs.mailSent(packet.getSocketAddress().toString(), recipient, filename);
                }
                yield MailProtocol.ok(request.id(), List.of(filename));
            }
            case MailProtocol.CMD_LOGIN, MailProtocol.CMD_LIST -> {
                requireFields(fields, 2);
                var page = storage.listMail(fields.get(0), fields.get(1));
                List<String> payload = new ArrayList<>();
                payload.add(page.account());
                payload.add(Boolean.toString(page.hasMore()));
                payload.addAll(page.filenames());
                yield MailProtocol.ok(request.id(), payload);
            }
            case MailProtocol.CMD_READ -> {
                requireFields(fields, 2);
                String acc = fields.get(0);
                String filename = fields.get(1);
                String content = storage.readMail(acc, filename);
                yield MailProtocol.ok(request.id(), filename, content);
            }
            case MailProtocol.CMD_JOIN_REQ -> {
                if (fields.size() < 3) {
                    throw new IllegalArgumentException("Số trường dữ liệu không đúng.");
                }
                String type;
                String name;
                String rawAccount;
                String clientIp;
                if (fields.size() >= 4) {
                    type = fields.get(0).trim().toUpperCase();
                    name = fields.get(1).trim();
                    rawAccount = fields.get(2).trim();
                    clientIp = fields.get(3).trim();
                } else {
                    type = "CREATE_OR_LOGIN";
                    name = fields.get(0).trim();
                    rawAccount = fields.get(1).trim();
                    clientIp = fields.get(2).trim();
                }

                if (name.isBlank()) {
                    throw new IllegalArgumentException("Tên người dùng không được để trống.");
                }
                String normAccount = MailStorage.accountName(rawAccount);

                if ("CREATE".equals(type) && storage.accountExists(normAccount)) {
                    throw new MailStorage.MailException("ACCOUNT_EXISTS", "Account '" + normAccount + "' đã tồn tại trên Server. Vui lòng bấm Đăng nhập.");
                }
                if ("LOGIN".equals(type) && !storage.accountExists(normAccount)) {
                    throw new MailStorage.MailException("ACCOUNT_NOT_FOUND", "Account '" + normAccount + "' chưa tồn tại trên Server. Vui lòng bấm Tạo Account mới.");
                }

                String requestId = request.id();
                ServerModels.PendingRequest pending = new ServerModels.PendingRequest(
                        requestId, type, name, normAccount, clientIp, packet.getSocketAddress(), System.currentTimeMillis()
                );
                pendingRequests.put(requestId, pending);
                approvalDecisions.put(requestId, new ServerModels.ApprovalDecision(
                        ServerModels.ApprovalState.PENDING, normAccount, name, null
                ));

                for (ServerObserver obs : observers) {
                    obs.clientRequested(requestId, type, name, normAccount, clientIp, packet.getSocketAddress().toString());
                }

                yield MailProtocol.ok(request.id(), "PENDING", requestId);
            }
            case MailProtocol.CMD_CHECK_JOIN -> {
                requireFields(fields, 1);
                String requestId = fields.get(0);
                ServerModels.ApprovalDecision decision = approvalDecisions.get(requestId);
                if (decision == null || decision.state() == ServerModels.ApprovalState.PENDING) {
                    yield MailProtocol.ok(request.id(), "PENDING");
                } else if (decision.state() == ServerModels.ApprovalState.APPROVED) {
                    yield MailProtocol.ok(request.id(), "APPROVED", decision.account(), decision.name());
                } else {
                    yield MailProtocol.ok(request.id(), "REJECTED", decision.reason() != null ? decision.reason() : "Từ chối.");
                }
            }
            case MailProtocol.CMD_CLIENTS_LIST -> {
                List<String> list = new ArrayList<>();
                for (ServerModels.ActiveClient c : activeClients.values()) {
                    list.add(c.account());
                    list.add(c.name());
                    list.add(c.clientIp());
                }
                yield MailProtocol.ok(request.id(), list);
            }
            case MailProtocol.CMD_DISCONNECT -> {
                if (!fields.isEmpty()) {
                    disconnectClient(fields.get(0));
                }
                yield MailProtocol.ok(request.id(), "DISCONNECTED");
            }
            default -> throw new IllegalArgumentException("Thao tác '" + request.command() + "' không được hỗ trợ.");
        };
    }

    private static void requireFields(List<String> fields, int count) {
        if (fields.size() != count) {
            throw new IllegalArgumentException("Số trường dữ liệu không đúng (yêu cầu " + count + ").");
        }
    }

    private void log(String message) {
        for (ServerObserver obs : observers) {
            obs.serverLog(message);
        }
    }

    @Override
    public void close() {
        if (!socket.isClosed()) {
            socket.close();
        }
        for (ServerObserver obs : observers) {
            obs.serverStopped();
        }
    }

    public static void main(String[] args) {
        try {
            boolean headless = false;
            int port = MailProtocol.DEFAULT_PORT;
            Path root = Path.of("mail_data");

            List<String> positional = new ArrayList<>();
            for (String arg : args) {
                if (arg.equalsIgnoreCase("--headless") || arg.equalsIgnoreCase("--no-gui")) {
                    headless = true;
                } else {
                    positional.add(arg);
                }
            }

            if (positional.size() > 2) {
                throw new IllegalArgumentException("Cách chạy: MailServer [port] [storageFolder] [--headless]");
            }
            if (!positional.isEmpty()) {
                port = Integer.parseInt(positional.get(0));
            }
            if (positional.size() > 1) {
                root = Path.of(positional.get(1));
            }

            MailServer server = new MailServer(port, root);
            Runtime.getRuntime().addShutdownHook(new Thread(server::close));

            if (!headless) {
                MailTheme.setupTheme();
                SwingUtilities.invokeLater(() -> {
                    ServerAdminFrame frame = new ServerAdminFrame(server);
                    server.addObserver(frame);
                    frame.setVisible(true);
                });
            }

            System.out.println("MailServer đang lắng nghe UDP tại cổng " + server.getPort());
            System.out.println("Thư mục lưu email: " + server.getStorageRoot());
            server.serve();

        } catch (IOException | IllegalArgumentException ex) {
            System.err.println("Không thể chạy MailServer: " + ex.getMessage());
            System.exit(1);
        }
    }
}
