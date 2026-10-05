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
                throw new IllegalArgumentException(
                        "Cú pháp lệnh console:\n"
                        + "  create <account> [host] [port]\n"
                        + "  login <account> [host] [port]\n"
                        + "  read <account> <filename> [host] [port]\n"
                        + "  send <recipient> <body> [host] [port]\n"
                        + "  send-file <recipient> <filepath> [host] [port]\n"
                        + "  join <name> <account> <clientIp> [host] [port]");
            }
            String command = args[0];
            int baseCount;
            if (command.equals("send") || command.equals("send-file") || command.equals("read")) {
                baseCount = 3;
            } else if (command.equals("join")) {
                baseCount = 4;
            } else {
                baseCount = 2;
            }

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
                    case "read" -> {
                        System.out.println("Nội dung " + args[2] + " trong hộp thư " + args[1] + ":");
                        System.out.println(client.readMail(args[1], args[2]));
                    }
                    case "send" -> System.out.println("Đã lưu email: " + client.sendMail(args[1], args[2]));
                    case "send-file" -> System.out.println("Đã lưu email: " + client.sendMail(args[1],
                            Files.readString(Path.of(args[2]), StandardCharsets.UTF_8)));
                    case "join" -> {
                        String reqId = client.requestJoin(args[1], args[2], args[3]);
                        System.out.println("Đã gửi yêu cầu tham gia (RequestId: " + reqId + "). Chờ Admin duyệt...");
                        while (true) {
                            Thread.sleep(1000);
                            var status = client.checkJoinStatus(reqId);
                            if ("APPROVED".equals(status.status())) {
                                System.out.println("Admin ĐÃ CHẤP NHẬN! Account: " + status.account() + ", Tên: " + status.name());
                                break;
                            } else if ("REJECTED".equals(status.status())) {
                                System.out.println("Admin ĐÃ TỪ CHỐI! Lý do: " + status.reason());
                                break;
                            }
                        }
                    }
                    default -> throw new IllegalArgumentException("Thao tác phải là create, login, read, send, send-file hoặc join.");
                }
            }
        } catch (IOException | IllegalArgumentException | InterruptedException ex) {
            System.err.println("Lỗi: " + ex.getMessage());
            System.exit(1);
        }
    }
}
