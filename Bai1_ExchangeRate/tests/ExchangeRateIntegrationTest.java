import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.time.OffsetDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Kiểm tra UDP thật trên loopback, không dùng thư viện ngoài. */
public final class ExchangeRateIntegrationTest {
    public static void main(String[] args) throws Exception {
        try (var server = new ExchangeRateServer(0)) {
            var workers = Executors.newFixedThreadPool(3);
            var serverTask = workers.submit(() -> {
                server.serve();
                return null;
            });
            try {
                try (var first = new ExchangeRateClient("127.0.0.1", server.getPort(), 800);
                     var second = new ExchangeRateClient("127.0.0.1", server.getPort(), 800)) {
                    var firstTask = workers.submit(first::requestRates);
                    var secondTask = workers.submit(second::requestRates);
                    validate(firstTask.get(2, TimeUnit.SECONDS));
                    validate(secondTask.get(2, TimeUnit.SECONDS));
                    System.out.println("PASS: hai client nhan du thi truong va thoi gian server");
                }
                try (var socket = new DatagramSocket()) {
                    socket.setSoTimeout(1000);
                    byte[] invalid = ExchangeRateProtocol.bytes("INVALID");
                    socket.send(new DatagramPacket(invalid, invalid.length, InetAddress.getLoopbackAddress(), server.getPort()));
                    DatagramPacket response = new DatagramPacket(new byte[2048], 2048);
                    socket.receive(response);
                    check(ExchangeRateProtocol.text(response.getData(), response.getOffset(), response.getLength())
                            .equals("ERROR|INVALID_REQUEST"), "Server phai bao loi yeu cau sai");
                }
                try (var client = new ExchangeRateClient("127.0.0.1", server.getPort(), 800)) {
                    validate(client.requestRates());
                    System.out.println("PASS: server tiep tuc phuc vu sau goi tin sai");
                }
            } finally {
                server.close();
                workers.shutdownNow();
                serverTask.get(2, TimeUnit.SECONDS);
            }
        }
        testTimeout();
        testStaleAndMalformedResponse();
        System.out.println("ALL TESTS PASSED");
    }

    private static void testTimeout() throws Exception {
        // Cổng có socket nhận nhưng không trả lời: timeout nhất quán trên Windows/Linux.
        try (var silent = new DatagramSocket(0);
             var client = new ExchangeRateClient("127.0.0.1", silent.getLocalPort(), 150)) {
            long start = System.nanoTime();
            try {
                client.requestRates();
                throw new AssertionError("Phai timeout");
            } catch (SocketTimeoutException expected) {
                long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                check(elapsed >= 100 && elapsed < 1500, "Timeout phai huu han");
                System.out.println("PASS: client timeout khi server khong tra loi");
            }
        }
    }

    private static void testStaleAndMalformedResponse() throws Exception {
        try (var fakeServer = new DatagramSocket(0);
             var client = new ExchangeRateClient("127.0.0.1", fakeServer.getLocalPort(), 800)) {
            fakeServer.setSoTimeout(1500);
            var worker = Executors.newSingleThreadExecutor();
            try {
                var task = worker.submit(() -> {
                    DatagramPacket request = new DatagramPacket(new byte[2048], 2048);
                    fakeServer.receive(request);
                    String id = ExchangeRateProtocol.requestId(ExchangeRateProtocol.text(
                            request.getData(), request.getOffset(), request.getLength()));
                    var rates = new ExchangeRateProtocol.Rates(OffsetDateTime.now(), 123.45, 234.56, 78.90);
                    send(fakeServer, request, ExchangeRateProtocol.response("old-request", rates));
                    send(fakeServer, request, ExchangeRateProtocol.bytes("RATE_RESPONSE|" + id + "|bad-date|123|234|78"));
                    send(fakeServer, request, ExchangeRateProtocol.bytes("RATE_RESPONSE|" + id + "|" + rates.updatedAt() + "|NaN|234|78"));
                    send(fakeServer, request, ExchangeRateProtocol.response(id, rates));
                    return null;
                });
                var actual = client.requestRates();
                check(actual.tokyo() == 123.45 && actual.newYork() == 234.56 && actual.hongKong() == 78.90,
                        "Client chi chap nhan phan hoi hop le cua yeu cau hien tai");
                task.get(2, TimeUnit.SECONDS);
                System.out.println("PASS: bo qua phan hoi tre va phan hoi sai du lieu");
            } finally {
                worker.shutdownNow();
            }
        }
    }

    private static void send(DatagramSocket socket, DatagramPacket destination, byte[] message) throws Exception {
        socket.send(new DatagramPacket(message, message.length, destination.getAddress(), destination.getPort()));
    }

    private static void validate(ExchangeRateProtocol.Rates rates) {
        check(rates.tokyo() >= 100 && rates.tokyo() <= 200, "Tokyo ngoai khoang");
        check(rates.newYork() >= 200 && rates.newYork() <= 300, "Newyork ngoai khoang");
        check(rates.hongKong() >= 50 && rates.hongKong() <= 150, "Hong Kong ngoai khoang");
        check(Math.abs(OffsetDateTime.now().toEpochSecond() - rates.updatedAt().toEpochSecond()) <= 5,
                "Thoi gian server phai la thoi gian moi nhat");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
