import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/** Giao thức văn bản UTF-8; mỗi thông điệp nằm trong một UDP datagram. */
public final class ExchangeRateProtocol {
    public static final int DEFAULT_PORT = 2345;
    public static final int BUFFER_SIZE = 2048;

    private ExchangeRateProtocol() { }

    public record Rates(OffsetDateTime updatedAt, double tokyo, double newYork, double hongKong) { }

    public static byte[] request(String requestId) {
        return bytes("RATE_REQUEST|" + requestId);
    }

    public static String requestId(String message) {
        String[] fields = message.split("\\|", -1);
        if (fields.length != 2 || !fields[0].equals("RATE_REQUEST")
                || !fields[1].matches("[A-Za-z0-9-]{1,64}")) {
            throw new IllegalArgumentException("Yêu cầu phải có dạng RATE_REQUEST|requestId");
        }
        return fields[1];
    }

    public static byte[] response(String requestId, Rates rates) {
        return bytes(String.format(Locale.ROOT, "RATE_RESPONSE|%s|%s|%.2f|%.2f|%.2f",
                requestId, rates.updatedAt(), rates.tokyo(), rates.newYork(), rates.hongKong()));
    }

    public static Rates parseResponse(String message, String expectedId) {
        String[] fields = message.split("\\|", -1);
        if (fields.length != 6 || !fields[0].equals("RATE_RESPONSE") || !fields[1].equals(expectedId)) {
            throw new IllegalArgumentException("Phản hồi không khớp yêu cầu");
        }
        try {
            return new Rates(OffsetDateTime.parse(fields[2]), price(fields[3]), price(fields[4]), price(fields[5]));
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Thời gian server không hợp lệ", ex);
        }
    }

    private static double price(String value) {
        double result = Double.parseDouble(value);
        if (!Double.isFinite(result) || result <= 0) {
            throw new IllegalArgumentException("Giá trị thị trường không hợp lệ");
        }
        return result;
    }

    public static byte[] bytes(String message) {
        return message.getBytes(StandardCharsets.UTF_8);
    }

    public static String text(byte[] data, int offset, int length) {
        return new String(data, offset, length, StandardCharsets.UTF_8);
    }
}
