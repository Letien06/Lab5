import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Gửi UDP thật và kiểm tra dữ liệu được lưu trên disk; không cần thư viện test. */
public final class MailIntegrationTest {
    @FunctionalInterface
    interface Action { void run() throws Exception; }

    private static final class RunningServer implements AutoCloseable {
        final MailServer server;
        final java.util.concurrent.ExecutorService worker = Executors.newSingleThreadExecutor();
        final java.util.concurrent.Future<?> task;

        RunningServer(Path root) throws IOException {
            server = new MailServer(0, root);
            task = worker.submit(() -> { server.serve(); return null; });
        }

        MailClient client() throws IOException {
            return new MailClient("127.0.0.1", server.getPort());
        }

        public void close() throws Exception {
            server.close();
            try {
                task.get(2, TimeUnit.SECONDS);
            } finally {
                worker.shutdownNow();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Files.createDirectories(Path.of("out"));
        Path root = Files.createTempDirectory(Path.of("out"), "integration-").toAbsolutePath();
        String original = "Xin chào thầy!\nEmail tiếng Việt có dấu: ă â ê ô ơ ư đ.\nKý tự | và +/= được giữ nguyên.\n";
        List<String> expectedNames = new ArrayList<>();
        expectedNames.add("new_email.txt");
        MailProtocol.Message repeated;
        try (var running = new RunningServer(root); var client = running.client()) {
            check(client.createAccount(" Alice ").equals("alice"), "Chuan hoa account");
            check(client.createAccount("bob").equals("bob"), "Tao bob");
            check(Files.readString(root.resolve("alice/new_email.txt"), StandardCharsets.UTF_8)
                    .equals(MailStorage.WELCOME), "Thu chao mung phai dung nguyen van");
            check(client.login("alice").filenames().equals(List.of("new_email.txt")), "Login account moi");
            expectError("ACCOUNT_EXISTS", () -> client.createAccount("ALICE"));
            expectError("ACCOUNT_NOT_FOUND", () -> client.login("missing"));
            expectError("ACCOUNT_NOT_FOUND", () -> client.sendMail("missing", original));
            for (String invalid : List.of("../outside", "a/b", "a\\b", "con", "", "ab")) {
                expectError("INVALID_ACCOUNT", () -> client.createAccount(invalid));
            }
            try (var accounts = Files.list(root)) {
                check(accounts.count() == 2, "Account loi khong tao thu muc moi");
            }
            System.out.println("PASS: tao account, welcome, login, account trung/khong ton tai/ten sai");

            String filename = client.sendMail("bob", original);
            expectedNames.add(filename);
            check(Files.readString(root.resolve("bob").resolve(filename), StandardCharsets.UTF_8).equals(original),
                    "Noi dung email tieng Viet/xuong dong phai giu nguyen");
            check(!Files.exists(root.resolve("alice").resolve(filename)), "Thu phai nam o nguoi nhan");
            String second = client.sendMail("bob", "Email thứ hai");
            expectedNames.add(second);
            check(!filename.equals(second), "Khong ghi de thu cu");

            try (var socket = new DatagramSocket()) {
                socket.setSoTimeout(1000);
                MailProtocol.Message create = MailProtocol.request("CREATE", "carol");
                check(exchange(socket, running.server.getPort(), create).command().equals("OK"), "Tao carol");
                check(exchange(socket, running.server.getPort(), create).command().equals("OK"), "Replay CREATE phai OK");
                repeated = MailProtocol.request("SEND", "bob", "Thư gửi lại cùng request ID");
                var first = exchange(socket, running.server.getPort(), repeated);
                var duplicate = exchange(socket, running.server.getPort(), repeated);
                check(first.equals(duplicate), "Replay SEND phai cung response");
                expectedNames.add(first.fields().get(0));
                var conflict = new MailProtocol.Message("SEND", repeated.id(), List.of("bob", "Noi dung khac"));
                check(exchange(socket, running.server.getPort(), conflict).fields().get(0).equals("REQUEST_CONFLICT"),
                        "Request ID khong duoc dung lai voi noi dung khac");
                var blank = exchange(socket, running.server.getPort(), MailProtocol.request("SEND", "bob", " \n"));
                check(blank.fields().get(0).equals("EMPTY_MAIL"), "Server tu kiem tra thu rong");
                var large = exchange(socket, running.server.getPort(), MailProtocol.request("SEND", "bob", "x".repeat(16001)));
                check(large.fields().get(0).equals("MAIL_TOO_LARGE"), "Server tu kiem tra gioi han thu");
                var unsupported = exchange(socket, running.server.getPort(), MailProtocol.request("DELETE", "bob"));
                check(unsupported.fields().get(0).equals("INVALID_REQUEST"), "Tu choi thao tac sai");
                byte[] bad = "not a protocol packet".getBytes(StandardCharsets.UTF_8);
                socket.send(new DatagramPacket(bad, bad.length, InetAddress.getLoopbackAddress(), running.server.getPort()));
                DatagramPacket response = new DatagramPacket(new byte[24001], 24001);
                socket.receive(response);
                check(MailProtocol.decode(response.getData(), response.getOffset(), response.getLength())
                        .fields().get(0).equals("INVALID_PACKET"), "Server xu ly goi tin sai");
            }
            System.out.println("PASS: luu dung nguoi nhan/noi dung, khong ghi de, replay va du lieu sai");

            for (int i = 0; i < 42; i++) {
                expectedNames.add(client.sendMail("bob", "Nội dung thư số " + i));
            }
            try (var secondClient = running.client()) {
                check(secondClient.login("bob").filenames().equals(expectedNames.stream().sorted().toList()),
                        "Phai lay tat ca ten file qua nhieu trang");
            }
            check(client.login("alice").filenames().equals(List.of("new_email.txt")), "Khong lan hop thu");
            System.out.println("PASS: nhieu client, nhieu trang, du tat ca ten file va khong lan account");
        }
        try (var restarted = new RunningServer(root); var client = restarted.client(); var socket = new DatagramSocket()) {
            socket.setSoTimeout(1000);
            check(exchange(socket, restarted.server.getPort(), repeated).command().equals("OK"),
                    "SEND lap lai sau restart phai nhan lai file cu");
            check(client.login("bob").filenames().equals(expectedNames.stream().sorted().toList()),
                    "Restart phai giu du lieu, khong tao thu trung");
            expectError("ACCOUNT_EXISTS", () -> client.createAccount("bob"));
            System.out.println("PASS: du lieu ton tai sau restart va SEND replay khong tao trung");
        }

        // Kiem tra co che Server Admin phe duyet Client va doc email (readMail)
        try (var running = new RunningServer(root); var client = running.client()) {
            // 1. Client gui yeu cau tham gia
            String reqId = client.requestJoin("Le Cao Son Tien", "sontien", "192.168.1.15");
            check(reqId != null && !reqId.isBlank(), "Phai co requestId");
            var statusBefore = client.checkJoinStatus(reqId);
            check("PENDING".equals(statusBefore.status()), "Trang thai ban dau phai la PENDING");

            // 2. Server Admin phe duyet
            running.server.approveClient(reqId);
            var statusAfter = client.checkJoinStatus(reqId);
            check("APPROVED".equals(statusAfter.status()), "Trang thai sau khi Admin duyet phai la APPROVED");
            check("sontien".equals(statusAfter.account()), "Account phai la sontien");
            check(Files.exists(root.resolve("sontien/new_email.txt")), "Admin duyet phai tao thu muc va new_email.txt");

            // 3. Doc noi dung email qua readMail
            String welcomeRead = client.readMail("sontien", "new_email.txt");
            check(welcomeRead.equals(MailStorage.WELCOME), "Noi dung new_email.txt qua readMail phai dung nguyen van");

            // 4. Gui email va doc lai
            String sentFile = client.sendMail("sontien", "Tin nhan kiem thu tu chat!");
            String sentRead = client.readMail("sontien", sentFile);
            check(sentRead.equals("Tin nhan kiem thu tu chat!"), "Noi dung email gui phai doc dung");

            // 5. Test Admin tu choi
            String reqIdReject = client.requestJoin("Hacker", "hacker", "10.0.0.1");
            running.server.rejectClient(reqIdReject, "IP khong hop le");
            var statusReject = client.checkJoinStatus(reqIdReject);
            check("REJECTED".equals(statusReject.status()), "Trang thai phai la REJECTED");
            check("IP khong hop le".equals(statusReject.reason()), "Ly do tu choi phai dung");
        }
        System.out.println("PASS: co che Server Admin phe duyet/tu choi client va doc email");

        testTimeoutAndRetry();
        System.out.println("ALL TESTS PASSED — data: " + root);
    }

    private static MailProtocol.Message exchange(DatagramSocket socket, int port, MailProtocol.Message request) throws Exception {
        byte[] bytes = MailProtocol.encode(request);
        socket.send(new DatagramPacket(bytes, bytes.length, InetAddress.getLoopbackAddress(), port));
        DatagramPacket response = new DatagramPacket(new byte[24001], 24001);
        socket.receive(response);
        return MailProtocol.decode(response.getData(), response.getOffset(), response.getLength());
    }

    private static void testTimeoutAndRetry() throws Exception {
        try (var silent = new DatagramSocket(0);
             var client = new MailClient("127.0.0.1", silent.getLocalPort(), 120, 2)) {
            long start = System.nanoTime();
            try {
                client.login("alice");
                throw new AssertionError("Phai timeout");
            } catch (SocketTimeoutException expected) {
                long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                check(elapsed >= 180 && elapsed < 2000, "Timeout phai huu han");
            }
        }
        try (var fake = new DatagramSocket(0);
             var client = new MailClient("127.0.0.1", fake.getLocalPort(), 120, 2)) {
            fake.setSoTimeout(1500);
            var worker = Executors.newSingleThreadExecutor();
            try {
                var responding = worker.submit(() -> {
                    DatagramPacket first = new DatagramPacket(new byte[24001], 24001);
                    fake.receive(first); // Mô phỏng mất phản hồi lần đầu.
                    var request = MailProtocol.decode(first.getData(), first.getOffset(), first.getLength());
                    DatagramPacket second = new DatagramPacket(new byte[24001], 24001);
                    fake.receive(second);
                    var retry = MailProtocol.decode(second.getData(), second.getOffset(), second.getLength());
                    check(request.equals(retry), "Retry phai giu nguyen request ID va noi dung");
                    byte[] response = MailProtocol.encode(MailProtocol.ok(retry.id(), List.of("alice")));
                    fake.send(new DatagramPacket(response, response.length, second.getSocketAddress()));
                    return null;
                });
                check(client.createAccount("alice").equals("alice"), "Client phai thu lai thanh cong");
                responding.get(2, TimeUnit.SECONDS);
            } finally {
                worker.shutdownNow();
            }
        }
        System.out.println("PASS: timeout va retry cung request ID khi mat phan hoi");
    }

    private static void expectError(String code, Action action) throws Exception {
        try {
            action.run();
            throw new AssertionError("Phai bao loi " + code);
        } catch (MailClient.ServerException expected) {
            check(expected.code().equals(code), "Sai ma loi: " + expected.code());
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
