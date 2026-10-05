/**
 * Interface lắng nghe sự kiện của MailServer để cập nhật giao diện Admin.
 */
public interface ServerObserver {
    void serverStarted(int port, String storagePath);
    void clientRequested(String requestId, String type, String name, String account, String clientIp, String remoteAddress);
    void clientApproved(String requestId, String name, String account, String clientIp);
    void clientRejected(String requestId, String name, String account, String reason);
    void clientConnected(String account, String name, String clientIp, String remoteAddress);
    void clientDisconnected(String account, String reason);
    void mailSent(String sender, String recipient, String filename);
    void serverLog(String message);
    void serverStopped();
}
