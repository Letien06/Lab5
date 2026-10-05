import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Một client dùng một socket riêng. requestRates chạy ở luồng nền của giao diện. */
public final class ExchangeRateClient implements AutoCloseable {
    private final DatagramSocket socket;
    private final int timeoutMillis;

    public ExchangeRateClient(String host, int port, int timeoutMillis) throws IOException {
        if (port < 1 || port > 65535 || timeoutMillis < 1) {
            throw new IllegalArgumentException("Cổng hoặc thời gian chờ không hợp lệ");
        }
        this.timeoutMillis = timeoutMillis;
        InetAddress address = InetAddress.getByName(host);
        socket = new DatagramSocket();
        try {
            // connect với UDP chỉ chọn địa chỉ đích và lọc nguồn phản hồi, không bắt tay như TCP.
            socket.connect(address, port);
        } catch (RuntimeException ex) {
            socket.close();
            throw ex;
        }
    }

    public synchronized ExchangeRateProtocol.Rates requestRates() throws IOException {
        String requestId = UUID.randomUUID().toString();
        byte[] request = ExchangeRateProtocol.request(requestId);
        socket.send(new DatagramPacket(request, request.length));
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        byte[] buffer = new byte[ExchangeRateProtocol.BUFFER_SIZE];
        while (true) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                throw new SocketTimeoutException("Server không phản hồi trong " + timeoutMillis + " ms");
            }
            socket.setSoTimeout((int) Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining)));
            DatagramPacket response = new DatagramPacket(buffer, buffer.length);
            socket.receive(response);
            String message = ExchangeRateProtocol.text(response.getData(), response.getOffset(), response.getLength());
            try {
                return ExchangeRateProtocol.parseResponse(message, requestId);
            } catch (IllegalArgumentException ex) {
                // Bỏ qua phản hồi trễ của chu kỳ cũ hoặc dữ liệu không đúng giao thức.
            }
        }
    }

    @Override
    public void close() {
        socket.close();
    }
}
