import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.PortUnreachableException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Giao diện client: ba chức năng được đề yêu cầu, mạng chạy trên luồng nền. */
public final class MailClientFrame extends JFrame {
    private final JTextField hostField = new JTextField("127.0.0.1", 14);
    private final JTextField portField = new JTextField("2346", 5);
    private final JTextField accountField = new JTextField(14);
    private final JTextField recipientField = new JTextField(18);
    private final JTextArea bodyArea = new JTextArea(10, 28);
    private final JButton createButton = new JButton("Tạo account");
    private final JButton loginButton = new JButton("Đăng nhập");
    private final JButton refreshButton = new JButton("Làm mới danh sách");
    private final JButton sendButton = new JButton("Gửi email");
    private final JLabel mailboxLabel = new JLabel("Chưa đăng nhập");
    private final JTextArea status = new JTextArea("Sẵn sàng. Account dùng chữ không dấu, số và _; không có mật khẩu trong bài mô phỏng.", 2, 60);
    private final DefaultListModel<String> mailModel = new DefaultListModel<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "mail-client-network");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean closed;
    private MailClient activeClient;
    private String loggedAccount;
    private boolean busy;

    @FunctionalInterface
    private interface Operation<T> {
        T run(MailClient client) throws IOException;
    }

    public MailClientFrame() {
        super("Lab 5 — Mail Client (UDP)");
        JPanel endpoint = new JPanel(new FlowLayout(FlowLayout.LEFT));
        endpoint.add(new JLabel("Server IP:"));
        endpoint.add(hostField);
        endpoint.add(new JLabel("Cổng:"));
        endpoint.add(portField);
        JPanel accounts = new JPanel(new FlowLayout(FlowLayout.LEFT));
        accounts.add(new JLabel("Account:"));
        accounts.add(accountField);
        accounts.add(createButton);
        accounts.add(loginButton);
        JPanel header = new JPanel(new GridLayout(2, 1));
        header.add(endpoint);
        header.add(accounts);

        JPanel inboxHeader = new JPanel(new BorderLayout(8, 8));
        inboxHeader.add(mailboxLabel, BorderLayout.CENTER);
        inboxHeader.add(refreshButton, BorderLayout.SOUTH);
        JPanel inbox = new JPanel(new BorderLayout(8, 8));
        inbox.setBorder(BorderFactory.createTitledBorder("Tên file trong hộp thư"));
        inbox.add(inboxHeader, BorderLayout.NORTH);
        inbox.add(new JScrollPane(new JList<>(mailModel)), BorderLayout.CENTER);

        JPanel recipient = new JPanel(new BorderLayout(8, 8));
        recipient.add(new JLabel("Account người nhận:"), BorderLayout.WEST);
        recipient.add(recipientField, BorderLayout.CENTER);
        bodyArea.setLineWrap(true);
        bodyArea.setWrapStyleWord(true);
        JPanel compose = new JPanel(new BorderLayout(8, 8));
        compose.setBorder(BorderFactory.createTitledBorder("Soạn email — tối đa 16000 byte UTF-8"));
        compose.add(recipient, BorderLayout.NORTH);
        compose.add(new JScrollPane(bodyArea), BorderLayout.CENTER);
        compose.add(sendButton, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, inbox, compose);
        split.setResizeWeight(0.48);
        split.setDividerLocation(420);
        JPanel content = new JPanel(new BorderLayout(10, 12));
        status.setEditable(false);
        status.setLineWrap(true);
        status.setWrapStyleWord(true);
        status.setBackground(content.getBackground());
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(header, BorderLayout.NORTH);
        content.add(split, BorderLayout.CENTER);
        content.add(status, BorderLayout.SOUTH);
        setContentPane(content);
        setSize(960, 560);
        setMinimumSize(new java.awt.Dimension(900, 480));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        refreshButton.setEnabled(false);

        createButton.addActionListener(event -> {
            String account = accountField.getText().trim();
            submit(client -> client.createAccount(account), created -> {
                accountField.setText(created);
                status.setText("Đã tạo account " + created + " và new_email.txt. Nhấn Đăng nhập để xem danh sách.");
            });
        });
        loginButton.addActionListener(event -> login(accountField.getText().trim()));
        refreshButton.addActionListener(event -> login(loggedAccount));
        sendButton.addActionListener(event -> {
            String recipientName = recipientField.getText().trim();
            String body = bodyArea.getText();
            submit(client -> client.sendMail(recipientName, body), filename -> {
                bodyArea.setText("");
                status.setText("Đã lưu email tại account " + recipientName + ": " + filename);
            });
        });
        DocumentListener endpointChanged = new DocumentListener() {
            private void reset() {
                loggedAccount = null;
                mailModel.clear();
                mailboxLabel.setText("Chưa đăng nhập");
                refreshButton.setEnabled(false);
            }
            public void insertUpdate(DocumentEvent event) { reset(); }
            public void removeUpdate(DocumentEvent event) { reset(); }
            public void changedUpdate(DocumentEvent event) { reset(); }
        };
        hostField.getDocument().addDocumentListener(endpointChanged);
        portField.getDocument().addDocumentListener(endpointChanged);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                stopWorker();
            }
            @Override
            public void windowClosed(WindowEvent event) {
                stopWorker();
            }
        });
    }

    private void login(String account) {
        if (account == null) {
            return;
        }
        submit(client -> client.login(account), mailbox -> {
            loggedAccount = mailbox.account();
            accountField.setText(loggedAccount);
            mailModel.clear();
            mailbox.filenames().forEach(mailModel::addElement);
            mailboxLabel.setText("Account: " + loggedAccount + " — " + mailModel.size() + " file");
            refreshButton.setEnabled(true);
            status.setText("Đã nhận tất cả tên file của account " + loggedAccount + ".");
        });
    }

    private <T> void submit(Operation<T> operation, Consumer<T> success) {
        if (busy || closed) {
            return;
        }
        String host = hostField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
            if (host.isEmpty() || port < 1 || port > 65535) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException ex) {
            status.setText("Nhập IP/tên server và cổng hợp lệ từ 1 đến 65535.");
            return;
        }
        setBusy(true);
        status.setText("Đang gửi yêu cầu tới server...");
        worker.submit(() -> {
            try (MailClient client = new MailClient(host, port)) {
                synchronized (this) {
                    if (closed) {
                        return;
                    }
                    activeClient = client;
                }
                T result = operation.run(client);
                SwingUtilities.invokeLater(() -> {
                    if (!closed) {
                        setBusy(false);
                        success.accept(result);
                    }
                });
            } catch (IOException | IllegalArgumentException ex) {
                SwingUtilities.invokeLater(() -> {
                    if (!closed) {
                        setBusy(false);
                        status.setText(errorMessage(ex));
                    }
                });
            } finally {
                synchronized (this) {
                    activeClient = null;
                }
            }
        });
    }

    private void setBusy(boolean value) {
        busy = value;
        hostField.setEnabled(!value);
        portField.setEnabled(!value);
        accountField.setEnabled(!value);
        recipientField.setEnabled(!value);
        bodyArea.setEnabled(!value);
        createButton.setEnabled(!value);
        loginButton.setEnabled(!value);
        sendButton.setEnabled(!value);
        refreshButton.setEnabled(!value && loggedAccount != null);
    }

    private synchronized void stopWorker() {
        closed = true;
        worker.shutdownNow();
        if (activeClient != null) {
            activeClient.close();
        }
    }

    private static String errorMessage(Exception ex) {
        if (ex instanceof PortUnreachableException) {
            return "Không liên lạc được server. Kiểm tra IP, cổng và server đã chạy chưa.";
        }
        if (ex instanceof SocketTimeoutException) {
            return ex.getMessage();
        }
        if (ex instanceof MailClient.ServerException serverError) {
            return serverError.code() + ": " + serverError.getMessage();
        }
        return "Lỗi: " + ex.getMessage();
    }

    public static void main(String[] args) {
        if (args.length > 0) {
            if (!args[0].equals("--console")) {
                System.err.println("Dùng --console create/login/send ..., hoặc không truyền tham số để mở GUI.");
                System.exit(1);
            }
            MailClientConsole.main(Arrays.copyOfRange(args, 1, args.length));
        } else {
            SwingUtilities.invokeLater(() -> new MailClientFrame().setVisible(true));
        }
    }
}
