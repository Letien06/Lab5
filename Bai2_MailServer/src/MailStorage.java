import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
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
        if (value == null) {
            throw new MailException("INVALID_ACCOUNT", "Account không được để trống.");
        }
        String account = value.trim().toLowerCase(Locale.ROOT);
        // Nếu người dùng nhập dạng email (vd: sontien@vku.udn.vn), lấy phần username
        if (account.contains("@")) {
            account = account.substring(0, account.indexOf('@')).trim();
        }
        if (!account.matches("[a-z][a-z0-9_]{2,31}")
                || account.matches("con|prn|aux|nul|com[1-9]|lpt[1-9]")) {
            throw new MailException("INVALID_ACCOUNT",
                    "Account gồm 3–32 ký tự: bắt đầu bằng chữ, dùng chữ không dấu, số hoặc _; không dùng tên hệ thống Windows.");
        }
        return account;
    }

    public boolean accountExists(String value) {
        try {
            String acc = accountName(value);
            return Files.isDirectory(root.resolve(acc), LinkOption.NOFOLLOW_LINKS);
        } catch (MailException ex) {
            return false;
        }
    }

    public String createAccount(String value, String password) throws IOException, MailException {
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
            if (password != null && !password.isBlank()) {
                Files.writeString(directory.resolve(".password"), password.trim(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            }
        } catch (IOException ex) {
            try {
                Files.deleteIfExists(directory.resolve(".password"));
                Files.deleteIfExists(directory.resolve("new_email.txt"));
                Files.delete(directory);
            } catch (IOException cleanupError) {
                ex.addSuppressed(cleanupError);
            }
            throw ex;
        }
        return account;
    }

    public String createAccount(String value) throws IOException, MailException {
        return createAccount(value, "");
    }

    public boolean verifyPassword(String value, String password) throws MailException, IOException {
        String account = accountName(value);
        Path directory = requireAccount(account);
        Path passFile = directory.resolve(".password");
        if (!Files.exists(passFile, LinkOption.NOFOLLOW_LINKS)) {
            // Nếu chưa có file password, lưu lại nếu người dùng nhập
            if (password != null && !password.isBlank()) {
                Files.writeString(passFile, password.trim(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            }
            return true;
        }
        String stored = Files.readString(passFile, StandardCharsets.UTF_8).trim();
        String input = password == null ? "" : password.trim();
        if (!stored.isEmpty() && !stored.equals(input)) {
            throw new MailException("WRONG_PASSWORD", "Mật khẩu không chính xác.");
        }
        return true;
    }

    public String sendMail(String recipient, String body, String senderIp, String senderAccount, String requestId)
            throws IOException, MailException {
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

        // Lưu nội dung trên đĩa: kèm IP người gửi và nội dung vừa chat
        String fileContent;
        if (senderIp != null && !senderIp.isBlank()) {
            String header = (senderAccount != null && !senderAccount.isBlank())
                    ? "[IP người gửi: " + senderIp + " | Người gửi: " + senderAccount + "]\n"
                    : "[IP người gửi: " + senderIp + "]\n";
            fileContent = body.startsWith("[IP người gửi:") ? body : (header + body);
        } else {
            fileContent = body;
        }

        String filename = "mail_" + requestId + ".txt";
        Path file = directory.resolve(filename);
        try {
            Files.writeString(file, fileContent, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (FileAlreadyExistsException ex) {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    || !Files.readString(file, StandardCharsets.UTF_8).equals(fileContent)) {
                throw new MailException("REQUEST_CONFLICT", "Mã yêu cầu đã được dùng cho nội dung khác.");
            }
        }
        return filename;
    }

    public String sendMail(String recipient, String body, String requestId) throws IOException, MailException {
        return sendMail(recipient, body, null, null, requestId);
    }

    public MailPage listMail(String value, String after) throws IOException, MailException {
        String account = accountName(value);
        Path directory = requireAccount(account);
        List<String> names;
        try (var entries = Files.list(directory)) {
            names = entries.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                            && !path.getFileName().toString().startsWith("."))
                    .map(path -> path.getFileName().toString()).sorted()
                    .filter(name -> after.isEmpty() || name.compareTo(after) > 0)
                    .limit(MailProtocol.PAGE_SIZE + 1L).toList();
        }
        boolean hasMore = names.size() > MailProtocol.PAGE_SIZE;
        return new MailPage(account, hasMore,
                hasMore ? names.subList(0, MailProtocol.PAGE_SIZE) : names);
    }

    public String readMail(String value, String filename) throws IOException, MailException {
        String account = accountName(value);
        Path directory = requireAccount(account);
        if (filename == null || filename.isBlank() || filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            throw new MailException("INVALID_FILENAME", "Tên file không hợp lệ.");
        }
        Path file = directory.resolve(filename).normalize();
        if (!file.startsWith(directory) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new MailException("FILE_NOT_FOUND", "Không tìm thấy file email trong hộp thư: " + filename);
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    public int countAccounts() {
        try (var stream = Files.list(root)) {
            return (int) stream.filter(p -> Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)).count();
        } catch (IOException e) {
            return 0;
        }
    }

    public int countMails() {
        int count = 0;
        try (var stream = Files.list(root)) {
            List<Path> dirs = stream.filter(p -> Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)).toList();
            for (Path dir : dirs) {
                try (var mailStream = Files.list(dir)) {
                    count += (int) mailStream.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)
                            && !p.getFileName().toString().startsWith(".")).count();
                } catch (IOException ignored) { }
            }
        } catch (IOException e) {
            return 0;
        }
        return count;
    }

    public List<String> listAllAccounts() {
        try (var stream = Files.list(root)) {
            return stream.filter(p -> Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS))
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    private Path requireAccount(String value) throws MailException {
        Path directory = root.resolve(accountName(value));
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new MailException("ACCOUNT_NOT_FOUND", "Account không tồn tại.");
        }
        return directory;
    }
}
