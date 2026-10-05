import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Console tùy chọn để minh họa UDP không cần mở giao diện. */
public final class MailClientConsole {
    private MailClientConsole() { }

    public static void main(String[] args) {
        try {
            if (args.length < 2) {
                throw new IllegalArgumentException("create/login <account> [host] [port]; send/send-file <recipient> <body/file> [host] [port]");
            }
            String command = args[0];
            int baseCount = command.equals("send") || command.equals("send-file") ? 3 : 2;
            if (args.length < baseCount || args.length > baseCount + 2) {
                throw new IllegalArgumentException("Số tham số không đúng.");
            }
            String host = args.length > baseCount ? args[baseCount] : "127.0.0.1";
            int port = args.length > baseCount + 1 ? Integer.parseInt(args[baseCount + 1]) : MailProtocol.DEFAULT_PORT;
            try (var client = new MailClient(host, port)) {
                switch (command) {
                    case "create" -> System.out.println("Đã tạo account: " + client.createAccount(args[1]));
                    case "login" -> {
                        var mailbox = client.login(args[1]);
                        System.out.println("Account: " + mailbox.account() + " — " + mailbox.filenames().size() + " file");
                        mailbox.filenames().forEach(System.out::println);
                    }
                    case "send" -> System.out.println("Đã lưu email: " + client.sendMail(args[1], args[2]));
                    case "send-file" -> System.out.println("Đã lưu email: " + client.sendMail(args[1],
                            Files.readString(Path.of(args[2]), StandardCharsets.UTF_8)));
                    default -> throw new IllegalArgumentException("Thao tác phải là create, login, send hoặc send-file.");
                }
            }
        } catch (IOException | IllegalArgumentException ex) {
            System.err.println("Lỗi: " + ex.getMessage());
            System.exit(1);
        }
    }
}
