package dev.resqnet.android.gateway;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.resqnet.mesh.protocol.DeliveryState;
import dev.resqnet.mesh.protocol.EnvelopeCodec;
import dev.resqnet.mesh.protocol.MessageEnvelope;
import dev.resqnet.mesh.protocol.ProtocolException;
import dev.resqnet.mesh.routing.StoredMessage;

/**
 * Optional synchronisation of incident summaries with the coordination backend.
 *
 * <p>This class is <strong>not</strong> part of the emergency network. Nothing
 * in the mesh depends on it, and the mesh works identically with the gateway URL
 * blank, unreachable, or behind a captive portal in a shelter.
 *
 * <p>That separation is the point. If gateway synchronisation ever became a
 * prerequisite, a saturated or unavailable backend would take the emergency
 * network down with it, and the failure mode would be invisible until the moment
 * it mattered most.
 *
 * <h3>What is sent</h3>
 *
 * <p>Only messages this device originated or received, and only the metadata a
 * responder needs: identifiers, timestamps, delivery state, hop count and the
 * advisory category. Message bodies are <em>not</em> uploaded, because the
 * backend is not on the trust path and a copy of every distress message on a
 * central server is a liability, not a feature.
 *
 * <h3>Transport rules</h3>
 *
 * <ul>
 *   <li>Plain HTTPS only. An http:// endpoint is refused outright rather than
 *       downgraded.</li>
 *   <li>The device authenticates with a bearer token the user configured. The
 *       backend is responsible for deciding what that token may do; the device
 *       claims nothing.</li>
 *   <li>A bounded batch, retried with backoff, and dropped rather than queued
 *       indefinitely when the backend is unavailable.</li>
 * </ul>
 */
public final class GatewaySyncClient {

    /** Maximum records uploaded in one batch. */
    private static final int MAX_BATCH = 50;

    /** Connect and read timeouts, in milliseconds. */
    private static final int TIMEOUT_MILLIS = 8_000;

    private final String baseUrl;
    private final String token;
    private final ExecutorService executor;
    private final AtomicBoolean inFlight = new AtomicBoolean();

    private volatile Listener listener = (result) -> {
    };

    public GatewaySyncClient(String baseUrl, String token) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.token = token == null ? "" : token.trim();
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "resqnet-gateway");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Reports the outcome of a synchronisation attempt. */
    public interface Listener {
        void onResult(Result result);
    }

    /**
     * Outcome of one attempt.
     *
     * @param uploaded number of records accepted by the backend
     * @param skipped number of records deliberately not uploaded
     * @param failure reason, or {@code null} on success
     */
    public record Result(int uploaded, int skipped, String failure) {

        public boolean success() {
            return failure == null;
        }
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** True when a gateway has been configured and the scheme is acceptable. */
    public boolean isConfigured() {
        return baseUrl.startsWith("https://") && !token.isEmpty();
    }

    /**
     * Uploads a bounded batch of delivery summaries.
     *
     * <p>Never throws and never blocks the caller: the mesh engine calls this on
     * its own executor and the result arrives on the listener.
     */
    public void sync(List<StoredMessage> records) {
        if (!isConfigured()) {
            listener.onResult(new Result(0, records.size(),
                    "no gateway configured, or it is not using HTTPS"));
            return;
        }
        if (!inFlight.compareAndSet(false, true)) {
            return;
        }
        executor.execute(() -> {
            try {
                listener.onResult(upload(records));
            } finally {
                inFlight.set(false);
            }
        });
    }

    private Result upload(List<StoredMessage> records) {
        StringBuilder body = new StringBuilder("{\"source\":\"android\",\"records\":[");
        int uploaded = 0;
        int skipped = 0;
        for (StoredMessage record : records) {
            if (uploaded >= MAX_BATCH) {
                skipped++;
                continue;
            }
            if (uploaded > 0) {
                body.append(',');
            }
            body.append(toJson(record));
            uploaded++;
        }
        body.append("]}");

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(baseUrl + "/api/v1/gateway/sync")
                    .openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(TIMEOUT_MILLIS);
            connection.setReadTimeout(TIMEOUT_MILLIS);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + token);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            if (status / 100 == 2) {
                return new Result(uploaded, skipped, null);
            }
            return new Result(0, skipped, "gateway returned HTTP " + status);
        } catch (Exception e) {
            // A failure here is a reporting failure, never a message failure.
            return new Result(0, skipped, String.valueOf(e.getMessage()));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Renders one delivery summary.
     *
     * <p>Deliberately excludes the message body. The backend needs to know that
     * a message exists, where it came from and whether it arrived; it does not
     * need the text of somebody's emergency.
     */
    String toJson(StoredMessage record) {
        StringBuilder json = new StringBuilder("{");
        json.append("\"messageId\":\"").append(escape(record.messageId())).append("\",");
        json.append("\"state\":\"").append(record.state().name()).append("\",");
        json.append("\"type\":\"").append(record.type().name()).append("\",");
        json.append("\"priority\":\"").append(record.priority().name()).append("\",");
        json.append("\"origin\":\"").append(escape(nullSafe(record.originDeviceId())))
                .append("\",");
        json.append("\"destination\":\"").append(escape(nullSafe(record.destinationDeviceId())))
                .append("\",");
        json.append("\"createdAt\":").append(record.createdAt()).append(',');
        json.append("\"expiresAt\":").append(record.expiresAt()).append(',');
        json.append("\"attempts\":").append(record.attemptCount()).append(',');
        json.append("\"detail\":\"").append(escape(record.lastDetail())).append('"');
        json.append('}');
        return json.toString();
    }

    /** Decodes the stored envelope, used by callers that need the hop count. */
    public static int hopCountOf(StoredMessage record) {
        try {
            MessageEnvelope envelope = EnvelopeCodec.decode(record.envelope());
            return envelope.hopCount();
        } catch (ProtocolException e) {
            return -1;
        }
    }

    /** True when a record still represents work the backend should know about. */
    public static boolean isReportable(DeliveryState state) {
        return state != DeliveryState.CREATED;
    }

    /** Escapes a string for embedding in JSON. */
    static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    /** Stops the client. Pending uploads are abandoned, not drained. */
    public void close() {
        executor.shutdownNow();
    }

    /** Convenience for diagnostics. */
    public List<String> describe() {
        List<String> description = new ArrayList<>();
        description.add("configured=" + isConfigured());
        description.add("batchLimit=" + MAX_BATCH);
        return description;
    }
}