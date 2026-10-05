import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Giao diện chính của Client sau khi được Admin phê duyệt (phong cách chat-message).
 * Font chữ to, sắc nét, tương phản cao, hỗ trợ đổi Chế độ Sáng/Tối linh hoạt.
 */
public final class MailChatFrame extends JFrame {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final String username;
    private final String account;
    private final String clientIp;
    private final String serverEndpoint;
    private final MailClient client;

    // Danh sách email
    private final DefaultListModel<String> mailModel = new DefaultListModel<>();
    private final JList<String> mailList = new JList<>(mailModel);

    // Danh sách user online
    private final DefaultListModel<MailClient.RemoteClient> usersModel = new DefaultListModel<>();
    private final JList<MailClient.RemoteClient> userList = new JList<>(usersModel);

    // Khung xem nội dung thư
    private final JLabel lblSelectedMailTitle = MailTheme.heading("Chọn một email để đọc", 16);
    private final JLabel lblSelectedMailMeta = MailTheme.muted("", 13);
    private final JTextArea mailContentArea = new JTextArea();

    // Khung soạn thư
    private final JTextField recipientField = MailTheme.styledTextField(16);
    private final JTextArea bodyArea = MailTheme.styledTextArea(4, 25);
    private final JButton btnSend = MailTheme.primaryButton("Gửi Email");
    private final JLabel lblComposerStatus = MailTheme.muted("Sẵn sàng.", 13);

    // Timer tự động làm mới hộp thư & danh sách online mỗi 3.5 giây
    private final Timer autoRefreshTimer;

    public MailChatFrame(String username, String account, String clientIp, String serverEndpoint, MailClient client) {
        super("VKU Mail & Chat — " + username + " (" + account + ")");
        this.username = username;
        this.account = account;
        this.clientIp = clientIp;
        this.serverEndpoint = serverEndpoint;
        this.client = client;

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1020, 680));
        setSize(1180, 740);
        setLocationRelativeTo(null);
        setContentPane(buildUi());

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                logout();
            }
        });

        // Tải dữ liệu ban đầu
        refreshMailbox();
        refreshOnlineUsers();

        // Khởi động auto-refresh
        autoRefreshTimer = new Timer(3500, e -> {
            refreshMailboxSilent();
            refreshOnlineUsersSilent();
        });
        autoRefreshTimer.start();
    }

    private JPanel buildUi() {
        JPanel root = new JPanel(new BorderLayout(0, 0));

        // Sidebar bên trái
        JPanel sidebar = buildSidebar();

        // Main content bên phải (Reader + Composer)
        JPanel mainContent = buildMainContent();

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebar, mainContent);
        split.setDividerLocation(380);
        split.setResizeWeight(0.33);
        split.setBorder(null);
        split.setOpaque(false);

        root.add(split, BorderLayout.CENTER);
        return root;
    }

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout(0, 12));
        sidebar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(71, 85, 105)),
                MailTheme.padding(16, 16, 16, 16)
        ));

        // 1. Profile header
        JPanel profile = new JPanel(new BorderLayout(12, 0));
        profile.setOpaque(false);

        MailTheme.Avatar avatar = new MailTheme.Avatar(username, 48, true);
        profile.add(avatar, BorderLayout.WEST);

        JPanel profileDetails = new JPanel();
        profileDetails.setOpaque(false);
        profileDetails.setLayout(new javax.swing.BoxLayout(profileDetails, javax.swing.BoxLayout.Y_AXIS));

        JLabel nameLbl = MailTheme.heading(username, 16);
        JLabel accLbl = MailTheme.muted("Mail: " + account, 13);
        JLabel ipLbl = MailTheme.muted("IP: " + clientIp, 12);
        JLabel statusBadge = MailTheme.fixedLabel("● Đã duyệt · Đang hoạt động", 12, MailTheme.ONLINE, true);

        profileDetails.add(nameLbl);
        profileDetails.add(javax.swing.Box.createVerticalStrut(2));
        profileDetails.add(accLbl);
        profileDetails.add(javax.swing.Box.createVerticalStrut(2));
        profileDetails.add(ipLbl);
        profileDetails.add(javax.swing.Box.createVerticalStrut(2));
        profileDetails.add(statusBadge);

        profile.add(profileDetails, BorderLayout.CENTER);

        JPanel headerButtons = new JPanel(new GridLayout(2, 1, 0, 4));
        headerButtons.setOpaque(false);

        JButton btnTheme = MailTheme.secondaryButton("Đổi nền");
        btnTheme.setToolTipText("Đổi giao diện Sáng / Tối");
        btnTheme.setFont(MailTheme.font(Font.PLAIN, 10));
        btnTheme.setMargin(new Insets(2, 6, 2, 6));
        btnTheme.addActionListener(e -> MailTheme.toggleTheme(this));

        JButton btnLogout = MailTheme.secondaryButton("Thoát");
        btnLogout.setFont(MailTheme.font(Font.PLAIN, 11));
        btnLogout.setMargin(new Insets(2, 6, 2, 6));
        btnLogout.addActionListener(e -> logout());

        headerButtons.add(btnTheme);
        headerButtons.add(btnLogout);
        profile.add(headerButtons, BorderLayout.EAST);

        sidebar.add(profile, BorderLayout.NORTH);

        // 2. Middle: Hộp thư đến (File list) & Online Users
        JPanel listsContainer = new JPanel(new GridLayout(2, 1, 0, 12));
        listsContainer.setOpaque(false);

        // Panel Hộp thư
        JPanel inboxPanel = new JPanel(new BorderLayout(0, 6));
        inboxPanel.setOpaque(false);

        JPanel inboxHeader = new JPanel(new BorderLayout());
        inboxHeader.setOpaque(false);
        inboxHeader.add(MailTheme.heading("HỘP THƯ ĐẾN", 13), BorderLayout.WEST);

        JButton btnRefresh = MailTheme.ghostButton("Làm mới");
        btnRefresh.setFont(MailTheme.font(Font.BOLD, 11));
        btnRefresh.setMargin(new Insets(2, 8, 2, 8));
        btnRefresh.addActionListener(e -> refreshMailbox());
        inboxHeader.add(btnRefresh, BorderLayout.EAST);

        mailList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        mailList.setFixedCellHeight(52);
        mailList.setCellRenderer(new MailCellRenderer());
        mailList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selected = mailList.getSelectedValue();
                if (selected != null) {
                    loadMailContent(selected);
                }
            }
        });

        inboxPanel.add(inboxHeader, BorderLayout.NORTH);
        inboxPanel.add(new JScrollPane(mailList), BorderLayout.CENTER);

        // Panel Online Users
        JPanel usersPanel = new JPanel(new BorderLayout(0, 6));
        usersPanel.setOpaque(false);

        JLabel usersTitle = MailTheme.heading("THÀNH VIÊN ONLINE (Bấm để gửi mail)", 13);
        usersPanel.add(usersTitle, BorderLayout.NORTH);

        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userList.setFixedCellHeight(46);
        userList.setCellRenderer(new UserCellRenderer());
        userList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                MailClient.RemoteClient u = userList.getSelectedValue();
                if (u != null) {
                    recipientField.setText(u.account());
                    bodyArea.requestFocus();
                }
            }
        });

        usersPanel.add(new JScrollPane(userList), BorderLayout.CENTER);

        listsContainer.add(inboxPanel);
        listsContainer.add(usersPanel);

        sidebar.add(listsContainer, BorderLayout.CENTER);

        // Footer server info
        JLabel serverInfo = MailTheme.label("Server: " + serverEndpoint + " (UDP)", 12, new Color(203, 213, 225), false);
        serverInfo.setHorizontalAlignment(SwingConstants.CENTER);
        sidebar.add(serverInfo, BorderLayout.SOUTH);

        return sidebar;
    }

    private JPanel buildMainContent() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));

        // Upper Section: Mail Reader (Message Card)
        JPanel readerPanel = new JPanel(new BorderLayout(0, 10));
        readerPanel.setOpaque(false);
        readerPanel.setBorder(MailTheme.padding(20, 24, 16, 24));

        JPanel mailHeader = new JPanel(new BorderLayout(0, 4));
        mailHeader.setOpaque(false);
        mailHeader.add(lblSelectedMailTitle, BorderLayout.NORTH);
        mailHeader.add(lblSelectedMailMeta, BorderLayout.SOUTH);
        mailHeader.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(71, 85, 105)),
                MailTheme.padding(0, 0, 12, 0)
        ));

        mailContentArea.setEditable(false);
        mailContentArea.setFont(MailTheme.font(Font.PLAIN, 15));
        mailContentArea.setLineWrap(true);
        mailContentArea.setWrapStyleWord(true);
        mailContentArea.setBorder(MailTheme.padding(18, 20, 18, 20));

        JPanel bubbleWrap = new JPanel(new BorderLayout());
        bubbleWrap.setOpaque(false);
        bubbleWrap.add(new JScrollPane(mailContentArea), BorderLayout.CENTER);

        readerPanel.add(mailHeader, BorderLayout.NORTH);
        readerPanel.add(bubbleWrap, BorderLayout.CENTER);

        // Lower Section: Mail Composer (Giống form chat composer)
        JPanel composerPanel = buildComposerPanel();

        JSplitPane verticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, readerPanel, composerPanel);
        verticalSplit.setDividerLocation(390);
        verticalSplit.setResizeWeight(0.65);
        verticalSplit.setBorder(null);
        verticalSplit.setOpaque(false);

        panel.add(verticalSplit, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildComposerPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(71, 85, 105)),
                MailTheme.padding(16, 24, 18, 24)
        ));

        // Row 1: Recipient input
        JPanel recipientRow = new JPanel(new BorderLayout(10, 0));
        recipientRow.setOpaque(false);
        JLabel lblTo = MailTheme.heading("Gửi tới Account:", 13);
        recipientRow.add(lblTo, BorderLayout.WEST);
        recipientRow.add(recipientField, BorderLayout.CENTER);

        // Row 2: Message body & Send button
        JPanel bodyRow = new JPanel(new BorderLayout(10, 0));
        bodyRow.setOpaque(false);

        bodyArea.setFont(MailTheme.font(Font.PLAIN, 14));
        bodyArea.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_ENTER) {
                    sendMail();
                }
            }
        });

        btnSend.setPreferredSize(new Dimension(130, 60));
        btnSend.setFont(MailTheme.font(Font.BOLD, 14));
        btnSend.addActionListener(e -> sendMail());

        bodyRow.add(new JScrollPane(bodyArea), BorderLayout.CENTER);
        bodyRow.add(btnSend, BorderLayout.EAST);

        // Footer status row
        JPanel statusRow = new JPanel(new BorderLayout());
        statusRow.setOpaque(false);
        statusRow.add(lblComposerStatus, BorderLayout.WEST);
        JLabel tip = MailTheme.muted("Nhấn Ctrl + Enter để gửi nhanh", 12);
        statusRow.add(tip, BorderLayout.EAST);

        panel.add(recipientRow, BorderLayout.NORTH);
        panel.add(bodyRow, BorderLayout.CENTER);
        panel.add(statusRow, BorderLayout.SOUTH);

        return panel;
    }

    private void sendMail() {
        String recipient = recipientField.getText().trim();
        String body = bodyArea.getText();

        if (recipient.isBlank()) {
            lblComposerStatus.setText("Lỗi: Chưa nhập Account người nhận.");
            lblComposerStatus.setForeground(MailTheme.DANGER);
            recipientField.requestFocus();
            return;
        }
        if (body.isBlank()) {
            lblComposerStatus.setText("Lỗi: Nội dung email không được để trống.");
            lblComposerStatus.setForeground(MailTheme.DANGER);
            bodyArea.requestFocus();
            return;
        }

        btnSend.setEnabled(false);
        lblComposerStatus.setText("Đang gửi qua UDP...");
        lblComposerStatus.setForeground(new Color(203, 213, 225));

        Thread t = new Thread(() -> {
            try {
                String filename = client.sendMail(recipient, body);
                SwingUtilities.invokeLater(() -> {
                    btnSend.setEnabled(true);
                    lblComposerStatus.setText("Đã gửi email thành công tới '" + recipient + "' (File: " + filename + ")");
                    lblComposerStatus.setForeground(MailTheme.ONLINE);
                    bodyArea.setText("");
                    if (recipient.equalsIgnoreCase(account)) {
                        refreshMailbox();
                    }
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    btnSend.setEnabled(true);
                    lblComposerStatus.setText("Lỗi khi gửi email: " + ex.getMessage());
                    lblComposerStatus.setForeground(MailTheme.DANGER);
                });
            }
        }, "mail-sender");
        t.setDaemon(true);
        t.start();
    }

    private void loadMailContent(String filename) {
        lblSelectedMailTitle.setText("Đang đọc: " + filename);
        lblSelectedMailMeta.setText("Đang tải dữ liệu từ máy chủ...");
        mailContentArea.setText("");

        Thread t = new Thread(() -> {
            try {
                String content = client.readMail(account, filename);
                SwingUtilities.invokeLater(() -> {
                    lblSelectedMailTitle.setText(filename);
                    if ("new_email.txt".equals(filename)) {
                        lblSelectedMailMeta.setText("Thư chào mừng hệ thống khi tạo tài khoản");
                    } else {
                        lblSelectedMailMeta.setText("Email lưu trữ tại thư mục " + account + "/" + filename);
                    }
                    mailContentArea.setText(content);
                    mailContentArea.setCaretPosition(0);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    lblSelectedMailTitle.setText("Lỗi khi đọc: " + filename);
                    lblSelectedMailMeta.setText(ex.getMessage());
                    mailContentArea.setText("Không thể đọc nội dung email: " + ex.getMessage());
                });
            }
        }, "mail-reader");
        t.setDaemon(true);
        t.start();
    }

    public void refreshMailbox() {
        Thread t = new Thread(this::refreshMailboxSilent, "mail-refresh");
        t.setDaemon(true);
        t.start();
    }

    private void refreshMailboxSilent() {
        try {
            MailClient.Mailbox mailbox = client.login(account);
            SwingUtilities.invokeLater(() -> {
                String currentSelected = mailList.getSelectedValue();
                mailModel.clear();
                for (String fn : mailbox.filenames()) {
                    mailModel.addElement(fn);
                }
                if (currentSelected != null && mailModel.contains(currentSelected)) {
                    mailList.setSelectedValue(currentSelected, true);
                } else if (!mailModel.isEmpty() && mailContentArea.getText().isEmpty()) {
                    mailList.setSelectedIndex(0);
                }
            });
        } catch (Exception ignored) { }
    }

    public void refreshOnlineUsers() {
        Thread t = new Thread(this::refreshOnlineUsersSilent, "users-refresh");
        t.setDaemon(true);
        t.start();
    }

    private void refreshOnlineUsersSilent() {
        try {
            List<MailClient.RemoteClient> users = client.listActiveClients();
            SwingUtilities.invokeLater(() -> {
                usersModel.clear();
                for (MailClient.RemoteClient u : users) {
                    if (!u.account().equalsIgnoreCase(account)) {
                        usersModel.addElement(u);
                    }
                }
            });
        } catch (Exception ignored) { }
    }

    private void logout() {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Bạn có muốn đăng xuất khỏi hệ thống?",
                "Xác nhận đăng xuất",
                JOptionPane.YES_NO_OPTION
        );
        if (confirm == JOptionPane.YES_OPTION) {
            autoRefreshTimer.stop();
            client.disconnect(account);
            client.close();
            dispose();

            SwingUtilities.invokeLater(() -> {
                ClientLoginForm login = new ClientLoginForm();
                login.setVisible(true);
            });
        }
    }

    // ==========================================
    // Custom Cell Renderers phong cách hiện đại
    // ==========================================

    private static class MailCellRenderer extends JPanel implements ListCellRenderer<String> {
        private final JLabel tagBadge = new JLabel();
        private final JLabel nameLabel = new JLabel();
        private final JLabel subLabel = new JLabel();

        public MailCellRenderer() {
            setLayout(new BorderLayout(10, 0));
            setOpaque(true);
            setBorder(MailTheme.padding(6, 12, 6, 12));

            tagBadge.setOpaque(true);
            tagBadge.setFont(MailTheme.font(Font.BOLD, 10));
            tagBadge.setHorizontalAlignment(SwingConstants.CENTER);
            tagBadge.setPreferredSize(new Dimension(50, 24));

            nameLabel.setFont(MailTheme.font(Font.BOLD, 13));
            subLabel.setFont(MailTheme.font(Font.PLAIN, 11));

            JPanel textPanel = new JPanel(new GridLayout(2, 1, 0, 2));
            textPanel.setOpaque(false);
            textPanel.add(nameLabel);
            textPanel.add(subLabel);

            add(tagBadge, BorderLayout.WEST);
            add(textPanel, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list, String value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            if ("new_email.txt".equals(value)) {
                tagBadge.setText("SYS");
                tagBadge.setBackground(new Color(245, 158, 11));
                tagBadge.setForeground(Color.WHITE);
                nameLabel.setText("new_email.txt");
                subLabel.setText("Thư chào mừng hệ thống");
            } else {
                tagBadge.setText("MAIL");
                tagBadge.setBackground(MailTheme.PRIMARY);
                tagBadge.setForeground(Color.WHITE);

                // Cắt gọn UUID dài cho dễ nhìn
                String display = value;
                if (value.startsWith("mail_") && value.length() > 25) {
                    display = "mail_" + value.substring(5, 13) + "..." + value.substring(value.length() - 8);
                }
                nameLabel.setText(display);
                subLabel.setText("Email nhận được");
            }

            if (isSelected) {
                setBackground(new Color(67, 56, 202));
                nameLabel.setForeground(Color.WHITE);
                subLabel.setForeground(new Color(224, 231, 255));
            } else {
                setBackground(list.getBackground());
                nameLabel.setForeground(list.getForeground());
                subLabel.setForeground(MailTheme.textMuted());
            }
            return this;
        }
    }

    private static class UserCellRenderer extends JPanel implements ListCellRenderer<MailClient.RemoteClient> {
        private final MailTheme.Avatar avatar = new MailTheme.Avatar("?", 30, true);
        private final JLabel nameLabel = new JLabel();
        private final JLabel ipLabel = new JLabel();

        public UserCellRenderer() {
            setLayout(new BorderLayout(8, 0));
            setOpaque(true);
            setBorder(MailTheme.padding(6, 12, 6, 12));

            nameLabel.setFont(MailTheme.font(Font.BOLD, 13));
            ipLabel.setFont(MailTheme.font(Font.PLAIN, 11));

            JPanel textPanel = new JPanel(new GridLayout(2, 1, 0, 2));
            textPanel.setOpaque(false);
            textPanel.add(nameLabel);
            textPanel.add(ipLabel);

            add(avatar, BorderLayout.WEST);
            add(textPanel, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends MailClient.RemoteClient> list,
                                                      MailClient.RemoteClient value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            if (value != null) {
                avatar.setName(value.name());
                nameLabel.setText(value.name() + " (" + value.account() + ")");
                ipLabel.setText("IP: " + value.ip());
            }

            if (isSelected) {
                setBackground(new Color(67, 56, 202));
                nameLabel.setForeground(Color.WHITE);
                ipLabel.setForeground(new Color(224, 231, 255));
            } else {
                setBackground(list.getBackground());
                nameLabel.setForeground(list.getForeground());
                ipLabel.setForeground(MailTheme.textMuted());
            }
            return this;
        }
    }
}
