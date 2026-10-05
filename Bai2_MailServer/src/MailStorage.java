import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;

/** Lưu dữ liệu bằng thư mục/file ngay trên máy server, không dùng database. */
public final class MailStorage {
    public static final String WELCOME = "Thank you for using this service. we hope that you will feel comfortabl........";
    private final Path root;

    public static final class MailException extends Exception {
        private final String code;

        public MailException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    public record MailPage(String account, boolean hasMore, List<String> filenames) { }

    public MailStorage(Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        Files.createDirectories(this.root);
        if (!Files.isDirectory(this.root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Thư mục lưu mail không hợp lệ: " + this.root);
        }
    }

    public Path root() {
        return root;
    }

    public static String accountName(String value) throws MailException {
        String account = value.trim().toLowerCase(Locale.ROOT);
        if (!account.matches("[a-z][a-z0-9_]{2,31}")
                || account.matches("con|prn|aux|nul|com[1-9]|lpt[1-9]")) {
            throw new MailException("INVALID_ACCOUNT",
                    "Account gồm 3–32 ký tự: bắt đầu bằng chữ, dùng chữ không dấu, số hoặc _; không dùng tên hệ thống Windows.");
        }
        return account;
    }

    public String createAccount(String value) throws IOException, MailException {
        String account = accountName(value);
        Path directory = root.resolve(account);
        try {
            Files.createDirectory(directory);
        } catch (FileAlreadyExistsException ex) {
            throw new MailException("ACCOUNT_EXISTS", "Account đã tồn tại.");
        }
        try {
            Files.writeString(directory.resolve("new_email.txt"), WELCOME, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException ex) {
            // Chỉ xóa thư mục vừa tạo nếu còn rỗng, không xóa đệ quy dữ liệu.
            try {
                Files.delete(directory);
            } catch (IOException cleanupError) {
                ex.addSuppressed(cleanupError);
            }
            throw ex;
        }
        return account;
    }

    public String sendMail(String recipient, String body, String requestId) throws IOException, MailException {
        if (!requestId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new MailException("INVALID_REQUEST", "Mã yêu cầu không hợp lệ.");
        }
        Path directory = requireAccount(recipient);
        if (body.isBlank()) {
            throw new MailException("EMPTY_MAIL", "Nội dung email không được để trống.");
        }
        if (body.getBytes(StandardCharsets.UTF_8).length > MailProtocol.MAX_BODY_BYTES) {
            throw new MailException("MAIL_TOO_LARGE", "Nội dung email tối đa 16000 byte UTF-8.");
        }
        // Mã yêu cầu tạo tên duy nhất. Gửi lại cùng yêu cầu không tạo email thứ hai.
        String filename = "mail_" + requestId + ".txt";
        Path file = directory.resolve(filename);
        try {
            Files.writeString(file, body, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (FileAlreadyExistsException ex) {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    || !Files.readString(file, StandardCharsets.UTF_8).equals(body)) {
                throw new MailException("REQUEST_CONFLICT", "Mã yêu cầu đã được dùng cho nội dung khác.");
            }
        }
        return filename;
    }

    public MailPage listMail(String value, String after) throws IOException, MailException {
        String account = accountName(value);
        Path directory = requireAccount(account);
        List<String> names;
        try (var entries = Files.list(directory)) {
            names = entries.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .map(path -> path.getFileName().toString()).sorted()
                    .filter(name -> after.isEmpty() || name.compareTo(after) > 0)
                    .limit(MailProtocol.PAGE_SIZE + 1L).toList();
        }
        boolean hasMore = names.size() > MailProtocol.PAGE_SIZE;
        return new MailPage(account, hasMore,
                hasMore ? names.subList(0, MailProtocol.PAGE_SIZE) : names);
    }

    private Path requireAccount(String value) throws MailException {
        Path directory = root.resolve(accountName(value));
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new MailException("ACCOUNT_NOT_FOUND", "Account không tồn tại.");
        }
        return directory;
    }
}
