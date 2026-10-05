import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Khung nhập Tên, Mail, Địa chỉ IP của Client theo phong cách chat_message.
 * Font chữ to, sắc nét, tương phản cao, hỗ trợ đổi Chế độ Sáng/Tối.
 */
public final class ClientLoginForm extends JFrame {
    private final JTextField nameField = MailTheme.styledTextField(18);
    private final JTextField mailField = MailTheme.styledTextField(18);
    private final JTextField clientIpField = MailTheme.styledTextField(18);
    private final JTextField serverHostField = MailTheme.styledTextField(12);
    private final JTextField serverPortField = MailTheme.styledTextField(6);

    private final JButton btnCreateAccount = MailTheme.primaryButton("Tạo Account mới");
    private final JButton btnLogin = MailTheme.successButton("Đăng nhập");

    private MailClient activeClient;
    private JDialog waitingDialog;
    private final AtomicBoolean cancelWaiting = new AtomicBoolean(false);

    public ClientLoginForm() {
        super("VKU Mail & Chat — Đăng nhập / Tạo Account");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(980, 660));
        setSize(1060, 700);
        setLocationRelativeTo(null);

        // Giá trị mặc định
        clientIpField.setText(MailTheme.detectLocalIp());
        serverHostField.setText("127.0.0.1");
        serverPortField.setText(String.valueOf(MailProtocol.DEFAULT_PORT));

        setContentPane(buildUi());

        btnCreateAccount.addActionListener(e -> onActionClicked("CREATE"));
        btnLogin.addActionListener(e -> onActionClicked("LOGIN"));
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
                + "<b>3. Gửi email:</b> Server tạo file email mới trong thư mục người nhận.<br><br>"
                + "<i>Cơ chế Admin Server phê duyệt trước khi cho vào hoạt động.</i>"
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
        JLabel welcome = MailTheme.heading("Tham gia hệ thống", 24);
        JButton btnTheme = MailTheme.secondaryButton("Đổi nền Sáng/Tối");
        btnTheme.setToolTipText("Chuyển đổi giao diện Sáng / Tối để dễ nhìn hơn");
        btnTheme.addActionListener(e -> MailTheme.toggleTheme(this));
        headerRow.add(welcome, BorderLayout.WEST);
        headerRow.add(btnTheme, BorderLayout.EAST);

        c.gridy = 0;
        c.insets = new Insets(0, 0, 4, 0);
        panel.add(headerRow, c);

        JLabel hint = MailTheme.muted("Nhập thông tin người dùng và gửi yêu cầu phê duyệt tới Admin Server", 13);
        c.gridy = 1;
        c.insets = new Insets(0, 0, 16, 0);
        panel.add(hint, c);

        // Khung nhập 1: Tên hiển thị
        addField(panel, c, 2, "Họ và tên của bạn", nameField, "Ví dụ: Lê Cao Sơn Tiến");

        // Khung nhập 2: Địa chỉ Mail / Account
        addField(panel, c, 4, "Địa chỉ Mail / Account", mailField, "Ví dụ: sontien hoặc sontien@vku.udn.vn");

        // Khung nhập 3: Địa chỉ IP của Client
        addField(panel, c, 6, "Địa chỉ IP của Client", clientIpField, "IP nội bộ máy bạn (LAN IP)");

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

        c.gridy = 8;
        c.insets = new Insets(10, 0, 16, 0);
        panel.add(serverRow, c);

        // Hàng 2 nút: Tạo Account mới & Đăng nhập
        JPanel buttonsRow = new JPanel(new GridLayout(1, 2, 10, 0));
        buttonsRow.setOpaque(false);
        buttonsRow.add(btnCreateAccount);
        buttonsRow.add(btnLogin);

        c.gridy = 9;
        c.insets = new Insets(4, 0, 0, 0);
        panel.add(buttonsRow, c);

        JLabel note = MailTheme.muted("Server Admin sẽ duyệt yêu cầu trước khi cấp quyền truy cập hộp thư.", 12);
        note.setHorizontalAlignment(SwingConstants.CENTER);
        c.gridy = 10;
        c.insets = new Insets(14, 0, 0, 0);
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

    private void onActionClicked(String actionType) {
        String name = nameField.getText().trim();
        String rawMail = mailField.getText().trim();
        String clientIp = clientIpField.getText().trim();
        String serverHost = serverHostField.getText().trim();
        String portStr = serverPortField.getText().trim();

        if (name.isBlank()) {
            showError("Vui lòng nhập Họ và tên.");
            nameField.requestFocus();
            return;
        }
        if (rawMail.isBlank()) {
            showError("Vui lòng nhập Địa chỉ Mail / Account.");
            mailField.requestFocus();
            return;
        }
        if (clientIp.isBlank()) {
            showError("Vui lòng nhập Địa chỉ IP của Client.");
            clientIpField.requestFocus();
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            showError("Cổng server không hợp lệ (1..65535).");
            return;
        }

        setButtonsEnabled(false);
        cancelWaiting.set(false);

        String actionLabel = "CREATE".equals(actionType) ? "Tạo Account mới" : "Đăng nhập";

        Thread worker = new Thread(() -> {
            try {
                if (activeClient != null) {
                    activeClient.close();
                }
                activeClient = new MailClient(serverHost, port);

                // Gửi JOIN_REQ kèm type tới Server
                String requestId = activeClient.requestJoin(actionType, name, rawMail, clientIp);

                // Hiển thị dialog chờ phê duyệt trên EDT
                SwingUtilities.invokeLater(() -> showWaitingDialog(actionLabel, name, rawMail, clientIp, requestId));

                // Bắt đầu polling kiểm tra trạng thái phê duyệt từ Admin
                while (!cancelWaiting.get()) {
                    try {
                        Thread.sleep(1200);
                    } catch (InterruptedException e) {
                        break;
                    }

                    if (cancelWaiting.get()) break;

                    try {
                        MailClient.JoinStatus status = activeClient.checkJoinStatus(requestId);
                        if ("APPROVED".equals(status.status())) {
                            // Admin đã duyệt!
                            SwingUtilities.invokeLater(() -> {
                                closeWaitingDialog();
                                openChatFrame(status.name(), status.account(), clientIp, serverHost + ":" + port);
                            });
                            return;
                        } else if ("REJECTED".equals(status.status())) {
                            // Admin đã từ chối!
                            SwingUtilities.invokeLater(() -> {
                                closeWaitingDialog();
                                setButtonsEnabled(true);
                                showError("Admin đã TỪ CHỐI yêu cầu của bạn.\nLý do: " + status.reason());
                            });
                            return;
                        }
                    } catch (IOException ex) {
                        // Thử lại vòng sau
                    }
                }

                SwingUtilities.invokeLater(() -> setButtonsEnabled(true));

            } catch (MailClient.ServerException ex) {
                SwingUtilities.invokeLater(() -> {
                    closeWaitingDialog();
                    setButtonsEnabled(true);
                    showError(ex.getMessage());
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    closeWaitingDialog();
                    setButtonsEnabled(true);
                    showError("Không thể kết nối tới Mail Server: " + ex.getMessage());
                });
            }
        }, "mail-join-worker");
        worker.setDaemon(true);
        worker.start();
    }

    private void setButtonsEnabled(boolean enabled) {
        btnCreateAccount.setEnabled(enabled);
        btnLogin.setEnabled(enabled);
    }

    private void showWaitingDialog(String actionLabel, String name, String mail, String ip, String requestId) {
        waitingDialog = new JDialog(this, "Đang chờ Admin phê duyệt...", true);
        waitingDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        waitingDialog.setSize(460, 300);
        waitingDialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel(new BorderLayout(14, 14));
        panel.setBorder(MailTheme.padding(24, 24, 24, 24));

        JLabel title = MailTheme.heading("Đã gửi yêu cầu: " + actionLabel, 17);
        title.setHorizontalAlignment(SwingConstants.CENTER);

        String textClr = MailTheme.isDark() ? "#FFFFFF" : "#0F172A";
        String subClr = MailTheme.isDark() ? "#CBD5E1" : "#475569";
        JLabel info = new JLabel("<html><center>"
                + "Tên người dùng: <font color='" + textClr + "'><b>" + name + "</b></font><br>"
                + "Địa chỉ Mail: <font color='#6366F1'><b>" + mail + "</b></font><br>"
                + "IP Client: <font color='#D97706'><b>" + ip + "</b></font><br><br>"
                + "<font color='" + subClr + "'>Vui lòng đợi Server Admin bấm <b>[Chấp nhận]</b> để vào hoạt động...</font>"
                + "</center></html>");
        info.setFont(MailTheme.font(Font.PLAIN, 14));
        info.setHorizontalAlignment(SwingConstants.CENTER);

        JProgressBar progress = new JProgressBar();
        progress.setIndeterminate(true);
        progress.setPreferredSize(new Dimension(0, 8));

        JPanel center = new JPanel(new BorderLayout(0, 14));
        center.setOpaque(false);
        center.add(info, BorderLayout.CENTER);
        center.add(progress, BorderLayout.SOUTH);

        JButton btnCancel = MailTheme.secondaryButton("Hủy yêu cầu");
        btnCancel.setFont(MailTheme.font(Font.BOLD, 13));
        btnCancel.addActionListener(e -> {
            cancelWaiting.set(true);
            closeWaitingDialog();
            setButtonsEnabled(true);
        });

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bottom.setOpaque(false);
        bottom.add(btnCancel);

        panel.add(title, BorderLayout.NORTH);
        panel.add(center, BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);

        waitingDialog.setContentPane(panel);
        waitingDialog.setVisible(true);
    }

    private void closeWaitingDialog() {
        if (waitingDialog != null) {
            waitingDialog.dispose();
            waitingDialog = null;
        }
    }

    private void openChatFrame(String name, String account, String clientIp, String serverEndpoint) {
        MailChatFrame chatFrame = new MailChatFrame(name, account, clientIp, serverEndpoint, activeClient);
        chatFrame.setVisible(true);
        dispose();
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Thông báo", JOptionPane.ERROR_MESSAGE);
    }
}
