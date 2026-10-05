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
import java.awt.Component;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Giao diện Quản trị viên (UI Admin) cho Mail Server:
 * - Font chữ to, sắc nét, tương phản cao trên FlatLaf.
 * - Danh sách Yêu cầu chờ phê duyệt (phân biệt Tạo account mới vs Đăng nhập).
 * - Nút "Chấp nhận" và "Từ chối" từng client hoặc tất cả.
 * - Danh sách Client đang hoạt động với khả năng ngắt kết nối.
 * - Server Log thời gian thực.
 * - Hỗ trợ nút đổi Chế độ Sáng/Tối linh hoạt.
 */
public final class ServerAdminFrame extends JFrame implements ServerObserver {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final MailServer server;
    private final JLabel lblStatus = MailTheme.muted("Đang khởi động...", 13);
    private final JLabel lblPort = MailTheme.heading("-", 28);
    private final JLabel lblActiveClients = MailTheme.heading("0", 28);
    private final JLabel lblPending = MailTheme.fixedLabel("0", 28, MailTheme.WARNING, true);
    private final JLabel lblTotalMails = MailTheme.heading("0", 28);

    // Bảng Yêu cầu chờ duyệt
    private final DefaultTableModel pendingModel = new DefaultTableModel(
            new Object[]{"Mã yêu cầu", "Hành động", "Tên Client", "Địa chỉ Mail", "IP Client", "Cổng mạng", "Thời gian"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable tblPending = new JTable(pendingModel);

    // Bảng Client đang hoạt động
    private final DefaultTableModel activeModel = new DefaultTableModel(
            new Object[]{"Tên Client", "Địa chỉ Mail", "IP Client", "Địa chỉ Socket", "Thời điểm vào", "Trạng thái"}, 0) {
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
        setMinimumSize(new Dimension(1060, 700));
        setSize(1160, 740);
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

        // Tabs bên trái (Pending, Active, Storage)
        tabs.setFont(MailTheme.font(Font.BOLD, 14));
        tabs.removeAll();
        tabs.addTab("Yêu cầu chờ duyệt (" + pendingModel.getRowCount() + ")", buildPendingPanel());
        tabs.addTab("Client đang hoạt động (0)", buildActivePanel());
        tabs.addTab("Dữ liệu Mail trên đĩa (0)", buildStoragePanel());

        // Log panel bên phải
        JPanel logPanel = buildLogPanel();

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tabs, logPanel);
        split.setResizeWeight(0.62);
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
        JLabel subtitle = MailTheme.muted("Bảng điều khiển máy chủ · Quản lý và phê duyệt Client tham gia hệ thống", 13);
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
            lblStatus.setText("Server đã dừng.");
            lblStatus.setForeground(MailTheme.DANGER);
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
        panel.add(metricCard("CLIENT ĐANG HOẠT ĐỘNG", lblActiveClients));
        panel.add(metricCard("YÊU CẦU CHỜ DUYỆT", lblPending));
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

    private JPanel buildPendingPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(MailTheme.padding(12, 12, 12, 12));

        setupTable(tblPending);
        if (tblPending.getColumnCount() >= 7) {
            tblPending.getColumnModel().getColumn(0).setPreferredWidth(90);
            tblPending.getColumnModel().getColumn(1).setPreferredWidth(130);
            tblPending.getColumnModel().getColumn(2).setPreferredWidth(140);
            tblPending.getColumnModel().getColumn(3).setPreferredWidth(120);
            tblPending.getColumnModel().getColumn(4).setPreferredWidth(120);
            tblPending.getColumnModel().getColumn(5).setPreferredWidth(150);
            tblPending.getColumnModel().getColumn(6).setPreferredWidth(80);
        }

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        toolbar.setOpaque(false);

        JButton btnApprove = MailTheme.successButton("Chấp nhận Client");
        btnApprove.setFont(MailTheme.font(Font.BOLD, 13));
        btnApprove.setToolTipText("Chấp nhận client đang chọn để cấp quyền hoạt động và mở hộp thư");
        btnApprove.addActionListener(e -> {
            int row = tblPending.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(this, "Vui lòng chọn một yêu cầu trong danh sách để duyệt.", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            String reqId = (String) pendingModel.getValueAt(row, 0);
            server.approveClient(reqId);
        });

        JButton btnReject = MailTheme.dangerButton("Từ chối");
        btnReject.setFont(MailTheme.font(Font.BOLD, 13));
        btnReject.setToolTipText("Từ chối yêu cầu tham gia của client");
        btnReject.addActionListener(e -> {
            int row = tblPending.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(this, "Vui lòng chọn một yêu cầu để từ chối.", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            String reqId = (String) pendingModel.getValueAt(row, 0);
            String reason = JOptionPane.showInputDialog(this, "Nhập lý do từ chối (tùy chọn):", "Admin từ chối yêu cầu.");
            server.rejectClient(reqId, reason != null && !reason.isBlank() ? reason : "Admin đã từ chối yêu cầu.");
        });

        JButton btnApproveAll = MailTheme.primaryButton("Chấp nhận tất cả");
        btnApproveAll.setFont(MailTheme.font(Font.BOLD, 13));
        btnApproveAll.addActionListener(e -> {
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < pendingModel.getRowCount(); i++) {
                ids.add((String) pendingModel.getValueAt(i, 0));
            }
            ids.forEach(server::approveClient);
        });

        toolbar.add(btnApprove);
        toolbar.add(btnReject);
        toolbar.add(btnApproveAll);

        panel.add(new JScrollPane(tblPending), BorderLayout.CENTER);
        panel.add(toolbar, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildActivePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(MailTheme.padding(12, 12, 12, 12));

        setupTable(tblActive);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        toolbar.setOpaque(false);

        JButton btnKick = MailTheme.dangerButton("Ngắt kết nối Client");
        btnKick.setFont(MailTheme.font(Font.BOLD, 13));
        btnKick.addActionListener(e -> {
            int row = tblActive.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(this, "Vui lòng chọn client để ngắt kết nối.", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            String account = (String) activeModel.getValueAt(row, 1);
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
            lblTotalMails.setText(String.valueOf(storage.countMails()));
            updateTabTitles();
        });
    }

    private void updateTabTitles() {
        if (tabs != null && tabs.getTabCount() >= 3) {
            tabs.setTitleAt(0, "Yêu cầu chờ duyệt (" + pendingModel.getRowCount() + ")");
            tabs.setTitleAt(1, "Client đang hoạt động (" + activeModel.getRowCount() + ")");
            tabs.setTitleAt(2, "Dữ liệu Mail trên đĩa (" + storageModel.getRowCount() + ")");
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
        SwingUtilities.invokeLater(() -> {
            String timeStr = LocalTime.now().format(TIME_FMT);
            String actionDesc;
            if ("CREATE".equals(type)) {
                actionDesc = "Tạo account mới";
            } else if ("LOGIN".equals(type)) {
                actionDesc = "Đăng nhập";
            } else {
                actionDesc = "Tham gia";
            }

            pendingModel.addRow(new Object[]{requestId, actionDesc, name, account, clientIp, remoteAddress, timeStr});
            lblPending.setText(String.valueOf(pendingModel.getRowCount()));
            updateTabTitles();
            appendLog("YÊU CẦU MỚI: Client '" + name + "' xin " + actionDesc + " (mail: " + account + ", IP: " + clientIp + ")");
        });
    }

    @Override
    public void clientApproved(String requestId, String name, String account, String clientIp) {
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < pendingModel.getRowCount(); i++) {
                if (pendingModel.getValueAt(i, 0).equals(requestId)) {
                    pendingModel.removeRow(i);
                    break;
                }
            }
            lblPending.setText(String.valueOf(pendingModel.getRowCount()));
            updateTabTitles();
            appendLog("ADMIN ĐÃ CHẤP NHẬN client '" + name + "' (" + account + ") vào hoạt động!");
            refreshStorageData();
        });
    }

    @Override
    public void clientRejected(String requestId, String name, String account, String reason) {
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < pendingModel.getRowCount(); i++) {
                if (pendingModel.getValueAt(i, 0).equals(requestId)) {
                    pendingModel.removeRow(i);
                    break;
                }
            }
            lblPending.setText(String.valueOf(pendingModel.getRowCount()));
            updateTabTitles();
            appendLog("ADMIN ĐÃ TỪ CHỐI client '" + name + "' (" + account + "). Lý do: " + reason);
        });
    }

    @Override
    public void clientConnected(String account, String name, String clientIp, String remoteAddress) {
        SwingUtilities.invokeLater(() -> {
            boolean found = false;
            for (int i = 0; i < activeModel.getRowCount(); i++) {
                if (activeModel.getValueAt(i, 1).equals(account)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                String timeStr = LocalTime.now().format(TIME_FMT);
                activeModel.addRow(new Object[]{name, account, clientIp, remoteAddress, timeStr, "Đang hoạt động"});
            }
            lblActiveClients.setText(String.valueOf(activeModel.getRowCount()));
            updateTabTitles();
            appendLog("Client '" + name + "' (" + account + ") đã vào trạng thái HOẠT ĐỘNG.");
        });
    }

    @Override
    public void clientDisconnected(String account, String reason) {
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < activeModel.getRowCount(); i++) {
                if (activeModel.getValueAt(i, 1).equals(account)) {
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
            appendLog("GỬI EMAIL: " + (sender.isBlank() ? "Client" : sender) + " -> " + recipient + " (file: " + filename + ")");
            refreshStorageData();
        });
    }

    @Override
    public void serverLog(String message) {
        appendLog(message);
    }

    @Override
    public void serverStopped() {
        SwingUtilities.invokeLater(() -> {
            lblStatus.setText("Server đã dừng.");
            lblStatus.setForeground(MailTheme.DANGER);
            appendLog("Server đã dừng.");
        });
    }
}
