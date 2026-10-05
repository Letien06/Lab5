import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Giao diện Quản trị viên (Admin Dashboard) cho Mail Server:
 * - Hiển thị cổng UDP, IP lắng nghe, số client online, số tài khoản, số email lưu trữ.
 * - Danh sách Client đang online với khả năng ngắt kết nối.
 * - Danh sách Dữ liệu Mail trên đĩa với nút mở thư mục trực tiếp.
 * - Server Log thời gian thực (ghi rõ IP người gửi khi có email/chat).
 * - Nút chuyển đổi Sáng/Tối linh hoạt.
 */
public final class ServerAdminFrame extends JFrame implements ServerObserver {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final MailServer server;
    private final JLabel lblStatus = MailTheme.muted("Đang khởi động...", 13);
    private final JLabel lblPort = MailTheme.heading("-", 28);
    private final JLabel lblActiveClients = MailTheme.heading("0", 28);
    private final JLabel lblTotalAccounts = MailTheme.heading("0", 28);
    private final JLabel lblTotalMails = MailTheme.heading("0", 28);

    // Bảng Client đang hoạt động
    private final DefaultTableModel activeModel = new DefaultTableModel(
            new Object[]{"Tài khoản", "Địa chỉ IP", "Địa chỉ Socket", "Thời điểm vào", "Trạng thái"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable tblActive = new JTable(activeModel);

    // Bảng Tài khoản trên đĩa
    private final DefaultTableModel storageModel = new DefaultTableModel(
            new Object[]{"Tài khoản", "Số email trong thư mục", "Đường dẫn thư mục"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable tblStorage = new JTable(storageModel);

    private final JTextArea logArea = new JTextArea();
    private final JTabbedPane tabs = new JTabbedPane();

    public ServerAdminFrame(MailServer server) {
        super("VKU UDP Mail Server — Admin Dashboard");
        this.server = server;
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1060, 680));
        setSize(1160, 720);
        setLocationRelativeTo(null);
        setContentPane(buildUi());

        if (server != null && server.getPort() > 0) {
            lblPort.setText(String.valueOf(server.getPort()));
            lblStatus.setText("Đang lắng nghe UDP tại cổng " + server.getPort() + " · Lưu trữ: " + server.getStorageRoot());
            lblStatus.setForeground(MailTheme.ONLINE);
            refreshStorageData();
        }
        updateTabTitles();

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                int confirm = JOptionPane.showConfirmDialog(
                        ServerAdminFrame.this,
                        "Bạn có chắc muốn dừng Mail Server và đóng giao diện Admin?",
                        "Xác nhận thoát",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE
                );
                if (confirm == JOptionPane.YES_OPTION) {
                    server.close();
                    dispose();
                    System.exit(0);
                }
            }
        });
    }

    private JPanel buildUi() {
        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setBorder(MailTheme.padding(18, 20, 18, 20));

        // Header Panel
        root.add(buildHeader(), BorderLayout.NORTH);

        // Center Panel: Metrics + Split (Tabs & Log)
        JPanel center = new JPanel(new BorderLayout(0, 14));
        center.setOpaque(false);
        center.add(buildMetricsPanel(), BorderLayout.NORTH);

        // Tabs bên trái (Active, Storage)
        tabs.setFont(MailTheme.font(Font.BOLD, 14));
        tabs.removeAll();
        tabs.addTab("Client đang hoạt động (0)", buildActivePanel());
        tabs.addTab("Dữ liệu Mail trên đĩa (0)", buildStoragePanel());

        // Log panel bên phải
        JPanel logPanel = buildLogPanel();

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tabs, logPanel);
        split.setResizeWeight(0.60);
        split.setBorder(null);
        split.setOpaque(false);

        center.add(split, BorderLayout.CENTER);
        root.add(center, BorderLayout.CENTER);

        // Footer status bar
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(lblStatus, BorderLayout.WEST);
        JLabel copyright = MailTheme.muted("Lập trình mạng · Lab 5 Bài 2 (UDP Mail Server)", 12);
        footer.add(copyright, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);

        return root;
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new javax.swing.BoxLayout(titleBox, javax.swing.BoxLayout.Y_AXIS));

        JLabel title = MailTheme.heading("VKU Mail Server — Admin Dashboard", 24);
        JLabel subtitle = MailTheme.muted("Bảng điều khiển máy chủ · Quản lý kết nối Client và dữ liệu Mail", 13);
        titleBox.add(title);
        titleBox.add(javax.swing.Box.createVerticalStrut(4));
        titleBox.add(subtitle);
        header.add(titleBox, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);

        JButton btnTheme = MailTheme.secondaryButton("Đổi nền Sáng/Tối");
        btnTheme.setToolTipText("Chuyển đổi giao diện Sáng / Tối để dễ nhìn hơn");
        btnTheme.addActionListener(e -> MailTheme.toggleTheme(this));

        JButton btnOpenFolder = MailTheme.secondaryButton("Mở thư mục mail_data");
        btnOpenFolder.addActionListener(e -> {
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(server.getStorageRoot().toFile());
                } else {
                    JOptionPane.showMessageDialog(this, "Đường dẫn lưu trữ: " + server.getStorageRoot());
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Không thể mở thư mục: " + ex.getMessage());
            }
        });

        JButton btnStop = MailTheme.dangerButton("Dừng Server");
        btnStop.addActionListener(e -> {
            server.close();
            dispose();
            System.exit(0);
        });

        actions.add(btnTheme);
        actions.add(btnOpenFolder);
        actions.add(btnStop);
        header.add(actions, BorderLayout.EAST);

        return header;
    }

    private JPanel buildMetricsPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 4, 12, 0));
        panel.setOpaque(false);
        panel.add(metricCard("CỔNG UDP", lblPort));
        panel.add(metricCard("CLIENT ĐANG ONLINE", lblActiveClients));
        panel.add(metricCard("TỔNG TÀI KHOẢN", lblTotalAccounts));
        panel.add(metricCard("TỔNG MAIL ĐÃ LƯU", lblTotalMails));
        return panel;
    }

    private JPanel metricCard(String title, JLabel valueLabel) {
        JPanel card = MailTheme.cardPanel(14);
        card.setLayout(new BorderLayout(0, 8));
        card.setBorder(MailTheme.padding(14, 16, 14, 16));
        card.add(MailTheme.muted(title, 12), BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildActivePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(MailTheme.padding(12, 12, 12, 12));

        setupTable(tblActive);
        if (tblActive.getColumnCount() >= 5) {
            tblActive.getColumnModel().getColumn(0).setPreferredWidth(120);
            tblActive.getColumnModel().getColumn(1).setPreferredWidth(110);
            tblActive.getColumnModel().getColumn(2).setPreferredWidth(160);
            tblActive.getColumnModel().getColumn(3).setPreferredWidth(90);
            tblActive.getColumnModel().getColumn(4).setPreferredWidth(110);
        }

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        toolbar.setOpaque(false);

        JButton btnKick = MailTheme.dangerButton("Ngắt kết nối Client");
        btnKick.setFont(MailTheme.font(Font.BOLD, 13));
        btnKick.addActionListener(e -> {
            int row = tblActive.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(this, "Vui lòng chọn một client để ngắt kết nối.", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            String account = (String) activeModel.getValueAt(row, 0);
            server.disconnectClient(account);
        });

        toolbar.add(btnKick);

        panel.add(new JScrollPane(tblActive), BorderLayout.CENTER);
        panel.add(toolbar, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildStoragePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(MailTheme.padding(12, 12, 12, 12));

        setupTable(tblStorage);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        toolbar.setOpaque(false);

        JButton btnRefreshStorage = MailTheme.secondaryButton("Làm mới danh sách trên đĩa");
        btnRefreshStorage.addActionListener(e -> refreshStorageData());
        toolbar.add(btnRefreshStorage);

        panel.add(new JScrollPane(tblStorage), BorderLayout.CENTER);
        panel.add(toolbar, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildLogPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(71, 85, 105), 1),
                MailTheme.padding(12, 14, 12, 14)
        ));

        JPanel logHeader = new JPanel(new BorderLayout());
        logHeader.setOpaque(false);
        logHeader.add(MailTheme.heading("Nhật ký máy chủ (Server Log)", 13), BorderLayout.WEST);

        JButton btnClear = MailTheme.secondaryButton("Xóa");
        btnClear.setFont(MailTheme.font(Font.PLAIN, 11));
        btnClear.setMargin(new Insets(3, 8, 3, 8));
        btnClear.addActionListener(e -> logArea.setText(""));
        logHeader.add(btnClear, BorderLayout.EAST);

        logArea.setEditable(false);
        logArea.setBackground(new Color(15, 23, 42));
        logArea.setForeground(new Color(226, 232, 240));
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setBorder(MailTheme.padding(8, 8, 8, 8));

        panel.add(logHeader, BorderLayout.NORTH);
        panel.add(new JScrollPane(logArea), BorderLayout.CENTER);
        return panel;
    }

    private void setupTable(JTable table) {
        table.setRowHeight(36);
        table.setFont(MailTheme.font(Font.PLAIN, 13));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setFont(MailTheme.font(Font.BOLD, 13));
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
        renderer.setHorizontalAlignment(SwingConstants.LEFT);
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }
    }

    public void refreshStorageData() {
        SwingUtilities.invokeLater(() -> {
            storageModel.setRowCount(0);
            var storage = server.getStorage();
            if (storage == null) return;
            var accounts = storage.listAllAccounts();
            for (String acc : accounts) {
                try {
                    var page = storage.listMail(acc, "");
                    storageModel.addRow(new Object[]{
                            acc,
                            page.filenames().size() + " file (gồm new_email.txt)",
                            server.getStorageRoot().resolve(acc).toString()
                    });
                } catch (Exception ignored) { }
            }
            lblTotalAccounts.setText(String.valueOf(storage.countAccounts()));
            lblTotalMails.setText(String.valueOf(storage.countMails()));
            updateTabTitles();
        });
    }

    private void updateTabTitles() {
        if (tabs != null && tabs.getTabCount() >= 2) {
            tabs.setTitleAt(0, "Client đang hoạt động (" + activeModel.getRowCount() + ")");
            tabs.setTitleAt(1, "Dữ liệu Mail trên đĩa (" + storageModel.getRowCount() + ")");
        }
    }

    private void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            String time = LocalTime.now().format(TIME_FMT);
            logArea.append("[" + time + "] " + message + System.lineSeparator());
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    // ==========================================
    // ServerObserver Implementation
    // ==========================================

    @Override
    public void serverStarted(int port, String storagePath) {
        SwingUtilities.invokeLater(() -> {
            lblPort.setText(String.valueOf(port));
            lblStatus.setText("Đang lắng nghe UDP tại cổng " + port + " · Lưu trữ: " + storagePath);
            lblStatus.setForeground(MailTheme.ONLINE);
            appendLog("Server UDP đã khởi động tại cổng " + port);
            appendLog("Thư mục lưu mail: " + storagePath);
            refreshStorageData();
            updateTabTitles();
        });
    }

    @Override
    public void clientRequested(String requestId, String type, String name, String account, String clientIp, String remoteAddress) {
        // Tự động xử lý, không cần chờ admin
    }

    @Override
    public void clientApproved(String requestId, String name, String account, String clientIp) {
        // Không dùng
    }

    @Override
    public void clientRejected(String requestId, String name, String account, String reason) {
        // Không dùng
    }

    @Override
    public void clientConnected(String account, String name, String clientIp, String remoteAddress) {
        SwingUtilities.invokeLater(() -> {
            boolean found = false;
            for (int i = 0; i < activeModel.getRowCount(); i++) {
                if (activeModel.getValueAt(i, 0).equals(account)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                String timeStr = LocalTime.now().format(TIME_FMT);
                activeModel.addRow(new Object[]{account, clientIp, remoteAddress, timeStr, "Đang online"});
            }
            lblActiveClients.setText(String.valueOf(activeModel.getRowCount()));
            updateTabTitles();
            appendLog("Client '" + account + "' (IP: " + clientIp + ") đã ĐĂNG NHẬP thành công.");
        });
    }

    @Override
    public void clientDisconnected(String account, String reason) {
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < activeModel.getRowCount(); i++) {
                if (activeModel.getValueAt(i, 0).equals(account)) {
                    activeModel.removeRow(i);
                    break;
                }
            }
            lblActiveClients.setText(String.valueOf(activeModel.getRowCount()));
            updateTabTitles();
            appendLog("Client '" + account + "' đã ngắt kết nối (" + reason + ").");
        });
    }

    @Override
    public void mailSent(String sender, String recipient, String filename) {
        SwingUtilities.invokeLater(() -> {
            appendLog("GỬI EMAIL: " + sender + " -> '" + recipient + "' (File: " + filename + ")");
            refreshStorageData();
        });
    }

    @Override
    public void serverStopped() {
        SwingUtilities.invokeLater(() -> {
            lblStatus.setText("Server đã dừng.");
            lblStatus.setForeground(MailTheme.DANGER);
            appendLog("Server đã dừng.");
        });
    }

    @Override
    public void serverLog(String message) {
        appendLog(message);
    }
}
