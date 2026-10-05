import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.io.IOException;

/**
 * Giao diện Đăng nhập / Đăng ký cho Mail Client:
 * - Chỉ yêu cầu Tài khoản và Mật khẩu.
 * - Không cần chờ phê duyệt từ Server, kết nối trực tiếp vào hệ thống.
 * - Hỗ trợ Chế độ Sáng/Tối với độ tương phản cao.
 */
public final class ClientLoginForm extends JFrame {
    private final JTextField accountField = MailTheme.styledTextField(18);
    private final JPasswordField passwordField = new JPasswordField(18);
    private final JTextField serverHostField = MailTheme.styledTextField(12);
    private final JTextField serverPortField = MailTheme.styledTextField(6);

    private final JButton btnLogin = MailTheme.successButton("Đăng nhập");
    private final JButton btnCreateAccount = MailTheme.primaryButton("Tạo tài khoản mới");

    public ClientLoginForm() {
        super("VKU Mail & Chat — Đăng nhập / Tạo tài khoản");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(960, 640));
        setSize(1040, 680);
        setLocationRelativeTo(null);

        // Giá trị mặc định
        serverHostField.setText("127.0.0.1");
        serverPortField.setText(String.valueOf(MailProtocol.DEFAULT_PORT));

        passwordField.setFont(MailTheme.font(Font.PLAIN, 14));
        passwordField.putClientProperty("JComponent.roundRect", true);
        passwordField.putClientProperty("JTextField.placeholderText", "Nhập mật khẩu");

        setContentPane(buildUi());

        btnLogin.addActionListener(e -> onLogin());
        btnCreateAccount.addActionListener(e -> onCreateAccount());

        // Hỗ trợ phím Enter để đăng nhập nhanh
        accountField.addActionListener(e -> passwordField.requestFocus());
        passwordField.addActionListener(e -> onLogin());
    }

    private JPanel buildUi() {
        JPanel root = new JPanel(new GridBagLayout());
        root.setBorder(MailTheme.padding(24, 24, 24, 24));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;

        JPanel card = MailTheme.cardPanel(20);
        card.setLayout(new GridLayout(1, 2, 0, 0));
        card.add(buildBrandPanel());
        card.add(buildFormPanel());

        root.add(card, gbc);
        return root;
    }

    private JPanel buildBrandPanel() {
        JPanel panel = MailTheme.roundedPanel(MailTheme.PRIMARY, 18);
        panel.setLayout(new GridBagLayout());
        panel.setBorder(MailTheme.padding(36, 36, 36, 36));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1.0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        // Logo Mark "M"
        JLabel mark = new JLabel("M");
        mark.setOpaque(true);
        mark.setBackground(Color.WHITE);
        mark.setForeground(MailTheme.PRIMARY);
        mark.setFont(MailTheme.font(Font.BOLD, 36));
        mark.setHorizontalAlignment(SwingConstants.CENTER);
        mark.setPreferredSize(new Dimension(68, 68));

        c.gridy = 0;
        panel.add(mark, c);

        JLabel title = MailTheme.fixedLabel("VKU Mail & Chat", 30, Color.WHITE, true);
        c.gridy = 1;
        c.insets = new Insets(20, 0, 0, 0);
        panel.add(title, c);

        JLabel subtitle = new JLabel("<html>Mạng truyền thông UDP Socket.<br>Giao diện hiện đại phong cách chat-message.</html>");
        subtitle.setFont(MailTheme.font(Font.PLAIN, 15));
        subtitle.setForeground(new Color(238, 242, 255));
        c.gridy = 2;
        c.insets = new Insets(10, 0, 0, 0);
        panel.add(subtitle, c);

        JLabel desc = new JLabel("<html><body style='width: 320px;'>"
                + "<font color='#FFFFFF'><b>Quy chuẩn Lab 5 - Bài 2:</b></font><br><br>"
                + "<b>1. Tạo account mới:</b> Server tạo thư mục trên máy chủ và tạo file <code>new_email.txt</code> chứa câu chào mừng.<br><br>"
                + "<b>2. Đăng nhập:</b> Server mở thư mục account và gửi danh sách tất cả file về client.<br><br>"
                + "<b>3. Gửi email:</b> Server tạo file email mới trong thư mục người nhận và ghi nhận IP người gửi.<br><br>"
                + "<i>Kết nối trực tiếp qua UDP, không cần chờ phê duyệt.</i>"
                + "</body></html>");
        desc.setFont(MailTheme.font(Font.PLAIN, 13));
        desc.setForeground(new Color(224, 231, 255));
        c.gridy = 3;
        c.insets = new Insets(20, 0, 0, 0);
        panel.add(desc, c);

        return panel;
    }

    private JPanel buildFormPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(MailTheme.padding(28, 36, 28, 36));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;

        // Header với nút đổi theme Sáng/Tối
        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setOpaque(false);
        JLabel welcome = MailTheme.heading("Đăng nhập hệ thống", 24);
        JButton btnTheme = MailTheme.secondaryButton("Đổi nền Sáng/Tối");
        btnTheme.setToolTipText("Chuyển đổi giao diện Sáng / Tối để dễ nhìn hơn");
        btnTheme.addActionListener(e -> MailTheme.toggleTheme(this));
        headerRow.add(welcome, BorderLayout.WEST);
        headerRow.add(btnTheme, BorderLayout.EAST);

        c.gridy = 0;
        c.insets = new Insets(0, 0, 4, 0);
        panel.add(headerRow, c);

        JLabel hint = MailTheme.muted("Nhập tài khoản và mật khẩu để kết nối trực tiếp tới Mail Server", 13);
        c.gridy = 1;
        c.insets = new Insets(0, 0, 20, 0);
        panel.add(hint, c);

        // Khung nhập 1: Tài khoản
        addField(panel, c, 2, "Tài khoản (Account / Username)", accountField, "Ví dụ: sontien hoặc vietdat");

        // Khung nhập 2: Mật khẩu
        addPasswordField(panel, c, 4, "Mật khẩu (Password)", passwordField, "Nhập mật khẩu của bạn");

        // Cấu hình Server (IP & Port)
        JPanel serverRow = new JPanel(new GridLayout(1, 2, 10, 0));
        serverRow.setOpaque(false);

        JPanel hostBox = new JPanel(new BorderLayout(0, 4));
        hostBox.setOpaque(false);
        hostBox.add(MailTheme.body("IP Server", 12, true), BorderLayout.NORTH);
        hostBox.add(serverHostField, BorderLayout.CENTER);

        JPanel portBox = new JPanel(new BorderLayout(0, 4));
        portBox.setOpaque(false);
        portBox.add(MailTheme.body("Cổng UDP", 12, true), BorderLayout.NORTH);
        portBox.add(serverPortField, BorderLayout.CENTER);

        serverRow.add(hostBox);
        serverRow.add(portBox);

        c.gridy = 6;
        c.insets = new Insets(12, 0, 20, 0);
        panel.add(serverRow, c);

        // Hàng 2 nút: Đăng nhập & Tạo tài khoản mới
        JPanel buttonsRow = new JPanel(new GridLayout(1, 2, 10, 0));
        buttonsRow.setOpaque(false);
        buttonsRow.add(btnLogin);
        buttonsRow.add(btnCreateAccount);

        c.gridy = 7;
        c.insets = new Insets(4, 0, 0, 0);
        panel.add(buttonsRow, c);

        JLabel note = MailTheme.muted("Kết nối trực tiếp qua UDP Socket tới Server.", 12);
        note.setHorizontalAlignment(SwingConstants.CENTER);
        c.gridy = 8;
        c.insets = new Insets(16, 0, 0, 0);
        panel.add(note, c);

        return panel;
    }

    private void addField(JPanel panel, GridBagConstraints c, int row, String labelText, JTextField field, String placeholder) {
        JLabel title = MailTheme.body(labelText, 13, true);
        c.gridy = row;
        c.insets = new Insets(row == 2 ? 0 : 8, 0, 4, 0);
        panel.add(title, c);

        field.setPreferredSize(new Dimension(280, 38));
        field.putClientProperty("JTextField.placeholderText", placeholder);
        field.setToolTipText(placeholder);
        c.gridy = row + 1;
        c.insets = new Insets(0, 0, 0, 0);
        panel.add(field, c);
    }

    private void addPasswordField(JPanel panel, GridBagConstraints c, int row, String labelText, JPasswordField field, String placeholder) {
        JLabel title = MailTheme.body(labelText, 13, true);
        c.gridy = row;
        c.insets = new Insets(8, 0, 4, 0);
        panel.add(title, c);

        field.setPreferredSize(new Dimension(280, 38));
        field.putClientProperty("JTextField.placeholderText", placeholder);
        field.setToolTipText(placeholder);
        c.gridy = row + 1;
        c.insets = new Insets(0, 0, 0, 0);
        panel.add(field, c);
    }

    private void onLogin() {
        String account = accountField.getText().trim();
        String password = new String(passwordField.getPassword());
        String serverHost = serverHostField.getText().trim();
        String portStr = serverPortField.getText().trim();

        if (account.isBlank()) {
            showError("Vui lòng nhập Tài khoản.");
            accountField.requestFocus();
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            showError("Cổng UDP server không hợp lệ (1–65535).");
            serverPortField.requestFocus();
            return;
        }

        setButtonsEnabled(false);

        Thread t = new Thread(() -> {
            try {
                MailClient client = new MailClient(serverHost, port);
                client.login(account, password);

                SwingUtilities.invokeLater(() -> {
                    String clientIp = MailTheme.detectLocalIp();
                    openChatFrame(account, account, clientIp, serverHost + ":" + port, client);
                });
            } catch (MailClient.ServerException ex) {
                SwingUtilities.invokeLater(() -> {
                    setButtonsEnabled(true);
                    if ("WRONG_PASSWORD".equals(ex.code())) {
                        showError("Mật khẩu không chính xác. Vui lòng kiểm tra lại!");
                        passwordField.requestFocus();
                    } else if ("ACCOUNT_NOT_FOUND".equals(ex.code())) {
                        showError("Tài khoản '" + account + "' không tồn tại trên Server.\nVui lòng bấm [Tạo tài khoản mới] để đăng ký!");
                    } else {
                        showError("Lỗi đăng nhập: " + ex.getMessage());
                    }
                });
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> {
                    setButtonsEnabled(true);
                    showError("Không thể kết nối tới Server UDP (" + serverHost + ":" + port + "):\n" + ex.getMessage()
                            + "\nVui lòng kiểm tra xem Server đã được khởi chạy chưa.");
                });
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void onCreateAccount() {
        String account = accountField.getText().trim();
        String password = new String(passwordField.getPassword());
        String serverHost = serverHostField.getText().trim();
        String portStr = serverPortField.getText().trim();

        if (account.isBlank()) {
            showError("Vui lòng nhập Tài khoản muốn tạo.");
            accountField.requestFocus();
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            showError("Cổng UDP server không hợp lệ (1–65535).");
            serverPortField.requestFocus();
            return;
        }

        setButtonsEnabled(false);

        Thread t = new Thread(() -> {
            try {
                MailClient client = new MailClient(serverHost, port);
                String createdAcc = client.createAccount(account, password);
                client.login(createdAcc, password);

                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this,
                            "Tạo tài khoản '" + createdAcc + "' thành công!\n"
                            + "Server đã tạo thư mục và file 'new_email.txt' chào mừng.\n"
                            + "Đang chuyển vào giao diện Hộp thư...",
                            "Thành công",
                            JOptionPane.INFORMATION_MESSAGE);

                    String clientIp = MailTheme.detectLocalIp();
                    openChatFrame(createdAcc, createdAcc, clientIp, serverHost + ":" + port, client);
                });
            } catch (MailClient.ServerException ex) {
                SwingUtilities.invokeLater(() -> {
                    setButtonsEnabled(true);
                    if ("ACCOUNT_EXISTS".equals(ex.code())) {
                        showError("Tài khoản '" + account + "' đã tồn tại trên Server!\nVui lòng bấm [Đăng nhập].");
                    } else {
                        showError("Lỗi tạo tài khoản: " + ex.getMessage());
                    }
                });
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> {
                    setButtonsEnabled(true);
                    showError("Không thể kết nối tới Server UDP (" + serverHost + ":" + port + "):\n" + ex.getMessage()
                            + "\nVui lòng kiểm tra xem Server đã được khởi chạy chưa.");
                });
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void setButtonsEnabled(boolean enabled) {
        btnLogin.setEnabled(enabled);
        btnCreateAccount.setEnabled(enabled);
    }

    private void openChatFrame(String name, String account, String clientIp, String serverEndpoint, MailClient client) {
        MailChatFrame chatFrame = new MailChatFrame(name, account, clientIp, serverEndpoint, client);
        chatFrame.setVisible(true);
        dispose();
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Thông báo", JOptionPane.ERROR_MESSAGE);
    }
}
