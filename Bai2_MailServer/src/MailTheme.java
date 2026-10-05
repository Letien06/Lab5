import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.FlatLaf;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.function.Supplier;

/**
 * Hệ thống Theme giao diện hiện đại với độ tương phản cao, chữ to rõ ràng.
 * Hỗ trợ chuyển đổi mượt mà giữa Chế độ Tối và Chế độ Sáng.
 * Mọi thành phần đều thích ứng độ tương phản màu sắc tự động.
 */
public final class MailTheme {
    private static boolean isDarkMode = true;

    // Màu thương hiệu nổi bật
    public static final Color PRIMARY = new Color(79, 70, 229);         // Indigo đậm nét
    public static final Color PRIMARY_LIGHT = new Color(99, 102, 241);   // Indigo sáng
    public static final Color PRIMARY_HOVER = new Color(129, 140, 248);
    public static final Color PRIMARY_SOFT = new Color(67, 56, 202);

    // Trạng thái thành công / cảnh báo / nguy hiểm
    public static final Color ONLINE = new Color(22, 163, 74);          // Emerald Green
    public static final Color DANGER = new Color(220, 38, 38);          // Crimson Red
    public static final Color WARNING = new Color(217, 119, 6);         // Amber

    private MailTheme() { }

    public static Color appBg() {
        return isDarkMode ? new Color(15, 23, 42) : new Color(241, 245, 249);
    }

    public static Color surface() {
        return isDarkMode ? new Color(30, 41, 59) : Color.WHITE;
    }

    public static Color surfaceLight() {
        return isDarkMode ? new Color(51, 65, 85) : new Color(248, 250, 252);
    }

    public static Color border() {
        return isDarkMode ? new Color(71, 85, 105) : new Color(203, 213, 225);
    }

    public static Color text() {
        return isDarkMode ? new Color(255, 255, 255) : new Color(15, 23, 42);
    }

    public static Color textMuted() {
        return isDarkMode ? new Color(148, 163, 184) : new Color(71, 85, 105);
    }

    public static void setupTheme() {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        try {
            FlatDarkLaf.setup();
            isDarkMode = true;
            applyThemeDefaults();
        } catch (Exception ex) {
            System.err.println("Không thể khởi tạo FlatLaf: " + ex.getMessage());
        }
    }

    private static void applyThemeDefaults() {
        Font defaultFont = new Font("Segoe UI", Font.PLAIN, 13);
        Font boldFont = new Font("Segoe UI", Font.BOLD, 13);
        Font headerFont = new Font("Segoe UI", Font.BOLD, 14);

        UIManager.put("defaultFont", defaultFont);
        UIManager.put("Component.font", defaultFont);
        UIManager.put("Label.font", defaultFont);
        UIManager.put("Button.font", boldFont);
        UIManager.put("Table.font", defaultFont);
        UIManager.put("TableHeader.font", headerFont);
        UIManager.put("TabbedPane.font", boldFont);
        UIManager.put("TextField.font", defaultFont);
        UIManager.put("TextArea.font", defaultFont);

        UIManager.put("Component.arc", 8);
        UIManager.put("Button.arc", 8);
        UIManager.put("TextComponent.arc", 8);
        UIManager.put("ProgressBar.arc", 8);

        UIManager.put("Table.rowHeight", 34);
        UIManager.put("Table.showHorizontalLines", true);
        UIManager.put("Table.showVerticalLines", false);

        UIManager.put("TabbedPane.tabHeight", 36);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBar.thumbArc", 8);

        if (isDarkMode) {
            UIManager.put("Label.foreground", new Color(255, 255, 255));
            UIManager.put("Panel.background", new Color(15, 23, 42));
            UIManager.put("Table.foreground", new Color(255, 255, 255));
            UIManager.put("Table.background", new Color(30, 41, 59));
            UIManager.put("TableHeader.foreground", new Color(255, 255, 255));
            UIManager.put("TableHeader.background", new Color(51, 65, 85));
            UIManager.put("TabbedPane.selectedBackground", PRIMARY_SOFT);
            UIManager.put("TabbedPane.selectedForeground", Color.WHITE);
        } else {
            UIManager.put("Label.foreground", new Color(15, 23, 42));
            UIManager.put("Panel.background", new Color(241, 245, 249));
            UIManager.put("Table.foreground", new Color(15, 23, 42));
            UIManager.put("Table.background", Color.WHITE);
            UIManager.put("TableHeader.foreground", new Color(15, 23, 42));
            UIManager.put("TableHeader.background", new Color(226, 232, 240));
            UIManager.put("TabbedPane.selectedBackground", PRIMARY_LIGHT);
            UIManager.put("TabbedPane.selectedForeground", Color.WHITE);
        }
    }

    public static void toggleTheme(Window currentWindow) {
        try {
            if (isDarkMode) {
                FlatLightLaf.setup();
                isDarkMode = false;
            } else {
                FlatDarkLaf.setup();
                isDarkMode = true;
            }
            applyThemeDefaults();
            FlatLaf.updateUI();
            if (currentWindow != null) {
                SwingUtilities.updateComponentTreeUI(currentWindow);
                currentWindow.repaint();
            }
        } catch (Exception ex) {
            System.err.println("Lỗi đổi theme: " + ex.getMessage());
        }
    }

    public static boolean isDark() {
        return isDarkMode;
    }

    public static Font font(int style, float size) {
        return new Font("Segoe UI", style, Math.round(size));
    }

    public static Border padding(int top, int left, int bottom, int right) {
        return new EmptyBorder(top, left, bottom, right);
    }

    /**
     * Nhãn chữ tự động thích ứng chế độ Sáng / Tối với độ tương phản cao.
     */
    public static class AdaptiveLabel extends JLabel {
        private final Supplier<Color> colorSupplier;

        public AdaptiveLabel(String text, int style, float size, Supplier<Color> colorSupplier) {
            super(text);
            setFont(MailTheme.font(style, size));
            this.colorSupplier = colorSupplier;
            updateColor();
        }

        public void updateColor() {
            if (colorSupplier != null) {
                setForeground(colorSupplier.get());
            }
        }

        @Override
        public void updateUI() {
            super.updateUI();
            updateColor();
        }
    }

    public static JLabel heading(String text, float size) {
        return new AdaptiveLabel(text, Font.BOLD, size, MailTheme::text);
    }

    public static JLabel body(String text, float size, boolean bold) {
        return new AdaptiveLabel(text, bold ? Font.BOLD : Font.PLAIN, size, MailTheme::text);
    }

    public static JLabel muted(String text, float size) {
        return new AdaptiveLabel(text, Font.PLAIN, size, MailTheme::textMuted);
    }

    public static JLabel fixedLabel(String text, float size, Color color, boolean bold) {
        return new AdaptiveLabel(text, bold ? Font.BOLD : Font.PLAIN, size, () -> color);
    }

    public static JLabel label(String text, int size, Color color, boolean bold) {
        if (color == null || color.equals(Color.WHITE)) {
            return new AdaptiveLabel(text, bold ? Font.BOLD : Font.PLAIN, size, MailTheme::text);
        }
        return new AdaptiveLabel(text, bold ? Font.BOLD : Font.PLAIN, size, () -> color);
    }

    public static JButton primaryButton(String text) {
        return createStyledButton(text, PRIMARY_LIGHT, Color.WHITE, 13);
    }

    public static JButton successButton(String text) {
        return createStyledButton(text, ONLINE, Color.WHITE, 13);
    }

    public static JButton dangerButton(String text) {
        return createStyledButton(text, DANGER, Color.WHITE, 13);
    }

    public static JButton ghostButton(String text) {
        return createStyledButton(text, PRIMARY_SOFT, Color.WHITE, 12);
    }

    public static JButton secondaryButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(font(Font.BOLD, 12));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setMargin(new Insets(7, 14, 7, 14));
        btn.putClientProperty("JComponent.roundRect", true);
        return btn;
    }

    private static JButton createStyledButton(String text, Color bg, Color fg, float fontSize) {
        JButton btn = new JButton(text);
        btn.setFont(font(Font.BOLD, fontSize));
        btn.setForeground(fg);
        btn.setBackground(bg);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setMargin(new Insets(7, 14, 7, 14));
        btn.putClientProperty("JComponent.roundRect", true);
        return btn;
    }

    public static JTextField styledTextField(int columns) {
        JTextField field = new JTextField(columns);
        field.setFont(font(Font.PLAIN, 14));
        field.putClientProperty("JComponent.roundRect", true);
        return field;
    }

    public static JTextArea styledTextArea(int rows, int cols) {
        JTextArea area = new JTextArea(rows, cols);
        area.setFont(font(Font.PLAIN, 14));
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        return area;
    }

    public static JPanel cardPanel(int arc) {
        return new RoundedPanel(MailTheme::surface, arc);
    }

    public static JPanel roundedPanel(Color bg, int arc) {
        return new RoundedPanel(() -> bg, arc);
    }

    public static JPanel roundedPanel(Supplier<Color> bgSupplier, int arc) {
        return new RoundedPanel(bgSupplier, arc);
    }

    public static final class RoundedPanel extends JPanel {
        private final Supplier<Color> bgSupplier;
        private final int arc;

        public RoundedPanel(Supplier<Color> bgSupplier, int arc) {
            this.bgSupplier = bgSupplier;
            this.arc = arc;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color fill = bgSupplier != null ? bgSupplier.get() : surface();
            g.setColor(fill);
            g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc));
            g.setColor(border());
            g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, arc, arc));
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    public static final class Avatar extends JComponent {
        private String name;
        private final int size;
        private boolean online;

        public Avatar(String name, int size, boolean online) {
            this.name = name == null ? "?" : name;
            this.size = size;
            this.online = online;
            setPreferredSize(new Dimension(size, size));
            setMinimumSize(new Dimension(size, size));
        }

        public void setOnline(boolean online) {
            this.online = online;
            repaint();
        }

        public void setName(String name) {
            this.name = name == null ? "?" : name;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(colorFor(name));
            g.fill(new Ellipse2D.Float(1, 1, size - 4, size - 4));
            g.setFont(font(Font.BOLD, size * 0.40f));
            g.setColor(Color.WHITE);
            String initial = name.trim().isEmpty() ? "?" : name.trim().substring(0, 1).toUpperCase();
            FontMetrics metrics = g.getFontMetrics();
            int x = (size - metrics.stringWidth(initial)) / 2;
            int y = (size - metrics.getHeight()) / 2 + metrics.getAscent();
            g.drawString(initial, x, y);
            if (online) {
                int dot = Math.max(8, size / 4);
                int dx = size - dot - 1;
                int dy = size - dot - 1;
                g.setColor(surface());
                g.fillOval(dx - 2, dy - 2, dot + 4, dot + 4);
                g.setColor(ONLINE);
                g.fillOval(dx, dy, dot, dot);
            }
            g.dispose();
        }

        private static Color colorFor(String value) {
            Color[] colors = {
                    new Color(59, 130, 246), new Color(139, 92, 246),
                    new Color(14, 165, 233), new Color(236, 72, 153),
                    new Color(20, 184, 166), new Color(249, 115, 22),
                    new Color(168, 85, 247), new Color(16, 185, 129)
            };
            return colors[Math.floorMod(value.hashCode(), colors.length)];
        }
    }

    public static String detectLocalIp() {
        try {
            try (DatagramSocket s = new DatagramSocket()) {
                s.connect(InetAddress.getByName("8.8.8.8"), 10002);
                String ip = s.getLocalAddress().getHostAddress();
                if (ip != null && !ip.startsWith("0.") && !ip.equals("0.0.0.0")) {
                    return ip;
                }
            }
        } catch (Exception ignored) { }

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress() && addr.getAddress().length == 4) {
                        return addr.getHostAddress();
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ex) {
            return "127.0.0.1";
        }
    }
}
