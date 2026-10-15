package dev.resqnet.mesh.protocol;

/**
 * Raised when an envelope cannot be encoded, decoded or accepted.
 *
 * <p>Always carries a machine readable {@link RejectReason} so routing metrics
 * and audit logs can aggregate failures without string matching.
 */
public class ProtocolException extends Exception {

    private static final long serialVersionUID = 1L;

    private final transient RejectReason reason;

    public ProtocolException(RejectReason reason, String message) {
        super(message);
        this.reason = reason == null ? RejectReason.MALFORMED : reason;
    }

    public ProtocolException(RejectReason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason == null ? RejectReason.MALFORMED : reason;
    }

    public RejectReason reason() {
        return reason;
    }
}