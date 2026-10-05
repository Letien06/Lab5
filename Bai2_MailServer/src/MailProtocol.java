import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/** Một datagram chứa command|UUID|các trường UTF-8 mã hóa Base64. */
public final class MailProtocol {
    public static final int DEFAULT_PORT = 2346;
    public static final int MAX_PACKET_BYTES = 24000;
    public static final int MAX_BODY_BYTES = 16000;
    public static final int PAGE_SIZE = 40;

    private MailProtocol() { }

    public record Message(String command, String id, List<String> fields) {
        public Message {
            fields = List.copyOf(fields);
        }
    }

    public static Message request(String command, String... fields) {
        return new Message(command, UUID.randomUUID().toString(), List.of(fields));
    }

    public static Message ok(String id, List<String> fields) {
        return new Message("OK", id, fields);
    }

    public static Message error(String id, String code, String detail) {
        return new Message("ERROR", id, List.of(code, detail));
    }

    public static byte[] encode(Message message) {
        StringBuilder result = new StringBuilder(message.command()).append('|').append(message.id());
        for (String field : message.fields()) {
            result.append('|').append(Base64.getEncoder().encodeToString(field.getBytes(StandardCharsets.UTF_8)));
        }
        byte[] bytes = result.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("Thông điệp vượt giới hạn gói UDP của bài lab.");
        }
        return bytes;
    }

    public static Message decode(byte[] data, int offset, int length) {
        if (length > MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("Gói tin quá lớn.");
        }
        String text = utf8(ByteBuffer.wrap(data, offset, length));
        String[] parts = text.split("\\|", -1);
        if (parts.length < 2 || parts.length > 64 || !parts[0].matches("[A-Z_]{1,24}")
                || !parts[1].matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new IllegalArgumentException("Gói tin không đúng giao thức.");
        }
        List<String> fields = new ArrayList<>();
        for (int i = 2; i < parts.length; i++) {
            fields.add(utf8(ByteBuffer.wrap(Base64.getDecoder().decode(parts[i]))));
        }
        return new Message(parts[0], parts[1], fields);
    }

    private static String utf8(ByteBuffer buffer) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(buffer).toString();
        } catch (CharacterCodingException ex) {
            throw new IllegalArgumentException("Dữ liệu không phải UTF-8 hợp lệ.", ex);
        }
    }
}
