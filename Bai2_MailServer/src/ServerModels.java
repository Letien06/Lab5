import java.net.SocketAddress;

/**
 * Các đối tượng mô hình dữ liệu cho Server Dashboard và cơ chế kiểm soát Client.
 */
public final class ServerModels {
    private ServerModels() { }

    public enum ApprovalState {
        PENDING,
        APPROVED,
        REJECTED
    }

    public record PendingRequest(
            String requestId,
            String type, // "CREATE" hoặc "LOGIN" hoặc "CREATE_OR_LOGIN"
            String name,
            String account,
            String clientIp,
            SocketAddress address,
            long timestampMillis
    ) { }

    public record ActiveClient(
            String account,
            String name,
            String clientIp,
            SocketAddress address,
            long joinedAtMillis
    ) { }

    public record ApprovalDecision(
            ApprovalState state,
            String account,
            String name,
            String reason
    ) { }
}
