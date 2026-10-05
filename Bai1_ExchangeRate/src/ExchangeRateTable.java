import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.PortUnreachableException;
import java.net.SocketTimeoutException;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

/** Bảng Swing cập nhật mỗi giây; hỗ trợ --console để chạy không cần GUI. */
public final class ExchangeRateTable extends JFrame {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss XXX");
    private final JTextField hostField = new JTextField("127.0.0.1", 14);
    private final JTextField portField = new JTextField("2345", 5);
    private final JButton startButton = new JButton("Bắt đầu");
    private final JButton stopButton = new JButton("Dừng");
    private final JLabel status = new JLabel("Chưa chạy — dữ liệu random mô phỏng.");
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[][] {{"Tokyo", "—", "—"}, {"Newyork", "—", "—"}, {"Hồng Kông", "—", "—"}},
            new String[] {"Thị trường", "Giá trị mô phỏng", "Thời gian server"}) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private PollingSession session;

    public ExchangeRateTable() {
        super("Lab 5 — ExchangeRateTable (UDP)");
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(new JLabel("Server IP:"));
        controls.add(hostField);
        controls.add(new JLabel("Cổng:"));
        controls.add(portField);
        controls.add(startButton);
        controls.add(stopButton);
        JTable table = new JTable(model);
        table.setRowHeight(36);
        table.getColumnModel().getColumn(2).setPreferredWidth(260);
        JPanel content = new JPanel(new BorderLayout(8, 12));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(controls, BorderLayout.NORTH);
        content.add(new JScrollPane(table), BorderLayout.CENTER);
        content.add(status, BorderLayout.SOUTH);
        setContentPane(content);
        setSize(760, 300);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        stopButton.setEnabled(false);
        startButton.addActionListener(event -> startPolling());
        stopButton.addActionListener(event -> stopPolling());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                stopPolling();
            }
        });
    }

    private void startPolling() {
        try {
            String host = hostField.getText().trim();
            int port = Integer.parseInt(portField.getText().trim());
            if (host.isEmpty() || port < 1 || port > 65535) {
                throw new IllegalArgumentException("Nhập IP/tên máy chủ và cổng 1..65535.");
            }
            session = new PollingSession(host, port);
            setRunning(true);
            status.setText("Đang yêu cầu dữ liệu mỗi giây...");
            session.start();
        } catch (IllegalArgumentException ex) {
            status.setText("Thông tin server không hợp lệ: " + ex.getMessage());
        }
    }

    private void stopPolling() {
        PollingSession oldSession = session;
        session = null;
        if (oldSession != null) {
            oldSession.close();
        }
        setRunning(false);
        status.setText("Đã dừng cập nhật.");
    }

    private void setRunning(boolean running) {
        startButton.setEnabled(!running);
        stopButton.setEnabled(running);
        hostField.setEnabled(!running);
        portField.setEnabled(!running);
    }

    private void showRates(ExchangeRateProtocol.Rates rates) {
        double[] values = {rates.tokyo(), rates.newYork(), rates.hongKong()};
        for (int row = 0; row < values.length; row++) {
            model.setValueAt(String.format(Locale.ROOT, "%.2f", values[row]), row, 1);
            model.setValueAt(TIME_FORMAT.format(rates.updatedAt()), row, 2);
        }
        status.setText("Đã cập nhật — chu kỳ 1 giây; dữ liệu random mô phỏng.");
    }

    private final class PollingSession implements AutoCloseable {
        private final String host;
        private final int port;
        private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "exchange-rate-polling");
            thread.setDaemon(true);
            return thread;
        });
        private volatile boolean closed;
        private volatile ExchangeRateClient client;

        PollingSession(String host, int port) {
            this.host = host;
            this.port = port;
        }

        void start() {
            // Timeout 800 ms nhỏ hơn chu kỳ 1 giây. Không nhận mạng trên luồng giao diện.
            worker.scheduleAtFixedRate(this::poll, 0, 1, TimeUnit.SECONDS);
        }

        private void poll() {
            if (closed) {
                return;
            }
            try {
                if (client == null) {
                    ExchangeRateClient created = new ExchangeRateClient(host, port, 800);
                    synchronized (this) {
                        if (closed) {
                            created.close();
                            return;
                        }
                        client = created;
                    }
                }
                var rates = client.requestRates();
                SwingUtilities.invokeLater(() -> {
                    if (session == this) {
                        showRates(rates);
                    }
                });
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> {
                    if (session == this) {
                        status.setText(errorMessage(ex) + " Dữ liệu đang hiển thị là lần cập nhật trước; sẽ thử lại.");
                    }
                });
            }
        }

        @Override
        public synchronized void close() {
            closed = true;
            worker.shutdownNow();
            if (client != null) {
                client.close();
            }
        }
    }

    private static String errorMessage(IOException ex) {
        if (ex instanceof SocketTimeoutException || ex instanceof PortUnreachableException) {
            return "Chưa nhận được phản hồi từ server.";
        }
        return "Lỗi kết nối: " + ex.getMessage() + ".";
    }

    private static void runConsole(String[] args) throws IOException, InterruptedException {
        if (args.length > 4) {
            throw new IllegalArgumentException("Cách chạy: --console [host] [port] [count]");
        }
        String host = args.length > 1 ? args[1] : "127.0.0.1";
        int port = args.length > 2 ? Integer.parseInt(args[2]) : ExchangeRateProtocol.DEFAULT_PORT;
        int count = args.length > 3 ? Integer.parseInt(args[3]) : 0;
        if (count < 0) {
            throw new IllegalArgumentException("count >= 0; 0 nghĩa là chạy liên tục");
        }
        try (var client = new ExchangeRateClient(host, port, 800)) {
            for (long cycle = 0; count == 0 || cycle < count; cycle++) {
                long start = System.nanoTime();
                try {
                    var rates = client.requestRates();
                    System.out.printf(Locale.ROOT, "%s | Tokyo=%.2f | Newyork=%.2f | Hong Kong=%.2f%n",
                            TIME_FORMAT.format(rates.updatedAt()), rates.tokyo(), rates.newYork(), rates.hongKong());
                } catch (IOException ex) {
                    System.err.println(errorMessage(ex) + " Sẽ thử lại ở chu kỳ tiếp theo.");
                }
                if (count == 0 || cycle + 1 < count) {
                    long remaining = TimeUnit.SECONDS.toNanos(1) - (System.nanoTime() - start);
                    if (remaining > 0) {
                        TimeUnit.NANOSECONDS.sleep(remaining);
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        if (args.length > 0) {
            try {
                if (!args[0].equals("--console")) {
                    throw new IllegalArgumentException("Dùng --console [host] [port] [count], hoặc không truyền tham số để mở GUI");
                }
                runConsole(args);
            } catch (IOException | IllegalArgumentException ex) {
                System.err.println(ex.getMessage());
                System.exit(1);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        } else {
            SwingUtilities.invokeLater(() -> new ExchangeRateTable().setVisible(true));
        }
    }
}
