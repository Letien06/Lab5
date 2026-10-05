import javax.swing.SwingUtilities;

/**
 * Điểm khởi chạy giao diện Client cho Lab 5 Bài 2.
 * Thiết lập FlatLaf với độ tương phản cao, chữ to rõ ràng trước khi mở Form.
 */
public class MailClientFrame {
    public static void main(String[] args) {
        MailTheme.setupTheme();
        SwingUtilities.invokeLater(() -> {
            ClientLoginForm form = new ClientLoginForm();
            form.setVisible(true);
        });
    }
}
