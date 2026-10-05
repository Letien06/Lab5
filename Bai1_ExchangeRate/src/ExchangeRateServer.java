import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.time.OffsetDateTime;
import java.util.concurrent.ThreadLocalRandom;

/** Server nhận yêu cầu UDP và trả dữ liệu về đúng IP/cổng của client. */
public final class ExchangeRateServer implements AutoCloseable {
    private final DatagramSocket socket;

    public ExchangeRateServer(int port) throws SocketException {
        socket = new DatagramSocket(port);
    }

    public int getPort() {
        return socket.getLocalPort();
    }

    public void serve() throws IOException {
        byte[] buffer = new byte[ExchangeRateProtocol.BUFFER_SIZE];
        while (!socket.isClosed()) {
            DatagramPacket request = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(request);
                String message = ExchangeRateProtocol.text(request.getData(), request.getOffset(), request.getLength());
                byte[] response;
                try {
                    String requestId = ExchangeRateProtocol.requestId(message);
                    var rates = new ExchangeRateProtocol.Rates(OffsetDateTime.now(),
                            randomPrice(100, 200), randomPrice(200, 300), randomPrice(50, 150));
                    response = ExchangeRateProtocol.response(requestId, rates);
                    System.out.printf("%s | %s:%d | Tokyo=%.2f, Newyork=%.2f, Hong Kong=%.2f%n",
                            rates.updatedAt(), request.getAddress().getHostAddress(), request.getPort(),
                            rates.tokyo(), rates.newYork(), rates.hongKong());
                } catch (IllegalArgumentException ex) {
                    response = ExchangeRateProtocol.bytes("ERROR|INVALID_REQUEST");
                }
                socket.send(new DatagramPacket(response, response.length, request.getAddress(), request.getPort()));
            } catch (SocketException ex) {
                if (!socket.isClosed()) {
                    throw ex;
                }
            }
        }
    }

    private static double randomPrice(double min, double max) {
        return Math.round(ThreadLocalRandom.current().nextDouble(min, max) * 100) / 100.0;
    }

    @Override
    public void close() {
        socket.close();
    }

    public static void main(String[] args) {
        try {
            int port = args.length == 0 ? ExchangeRateProtocol.DEFAULT_PORT : Integer.parseInt(args[0]);
            if (port < 1 || port > 65535 || args.length > 1) {
                throw new IllegalArgumentException("Cổng phải nằm trong khoảng 1..65535");
            }
            try (var server = new ExchangeRateServer(port)) {
                Runtime.getRuntime().addShutdownHook(new Thread(server::close));
                System.out.println("ExchangeRateServer đang lắng nghe UDP tại cổng " + server.getPort());
                System.out.println("Dữ liệu random mô phỏng; nhấn Ctrl+C để dừng.");
                server.serve();
            }
        } catch (IOException | IllegalArgumentException ex) {
            System.err.println("Không thể chạy server: " + ex.getMessage());
            System.exit(1);
        }
    }
}
