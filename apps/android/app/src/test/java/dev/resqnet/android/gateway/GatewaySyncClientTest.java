package dev.resqnet.android.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import dev.resqnet.mesh.protocol.DeliveryState;
import dev.resqnet.mesh.protocol.MessageType;
import dev.resqnet.mesh.protocol.Priority;
import dev.resqnet.mesh.routing.StoredMessage;

/**
 * Gateway synchronisation is a reporting feature, not a transport feature.
 *
 * <p>These tests pin the two properties that keep it that way: it refuses to
 * send anything over plain HTTP, and it never uploads the body of somebody's
 * emergency message.
 */
public class GatewaySyncClientTest {

    private static StoredMessage record(String id, DeliveryState state) {
        return new StoredMessage(id, "envelope-bytes".getBytes(StandardCharsets.UTF_8), state,
                "rqn1_origin", "rqn1_dest", MessageType.SOS, Priority.SOS,
                1_700_000_000_000L, 1_700_000_900_000L, 2, 0L, "confirmed", 7L);
    }

    @Test
    public void refusesToRunWithoutHttps() {
        assertThat(new GatewaySyncClient("http://example.test", "token").isConfigured())
                .as("a plaintext endpoint must be refused, not downgraded")
                .isFalse();
    }

    @Test
    public void refusesToRunWithoutAToken() {
        assertThat(new GatewaySyncClient("https://example.test", "  ").isConfigured()).isFalse();
        assertThat(new GatewaySyncClient("", "").isConfigured()).isFalse();
    }

    @Test
    public void acceptsAConfiguredHttpsEndpoint() {
        assertThat(new GatewaySyncClient("https://gateway.example", "abc123").isConfigured())
                .isTrue();
    }

    @Test
    public void reportsDeliveryMetadataWithoutTheMessageBody() {
        String json = new GatewaySyncClient("https://gateway.example", "t")
                .toJson(record("m1", DeliveryState.ACKNOWLEDGED));

        assertThat(json).contains("\"messageId\":\"m1\"");
        assertThat(json).contains("\"state\":\"ACKNOWLEDGED\"");
        assertThat(json).contains("\"attempts\":2");
        assertThat(json).doesNotContain("envelope-bytes");
    }

    @Test
    public void escapesHostileIdentifiers() {
        StoredMessage hostile = new StoredMessage("m\"1", new byte[0], DeliveryState.QUEUED,
                "rqn1_o", null, MessageType.TEXT, Priority.LOW, 0L, 1L, 0, 0L,
                "line\nbreak\ttab", -1L);

        String json = new GatewaySyncClient("https://gateway.example", "t").toJson(hostile);

        assertThat(json).contains("\\\"");
        assertThat(json).contains("\\n");
        assertThat(json).doesNotContain("\n");
    }

    @Test
    public void treatsAnAbsentDestinationAsEmptyRatherThanNull() {
        StoredMessage broadcast = new StoredMessage("m2", new byte[0], DeliveryState.SENT_TO_PEER,
                "rqn1_o", null, MessageType.SOS, Priority.SOS, 0L, 1L, 1, 0L, "queued", -1L);

        String json = new GatewaySyncClient("https://gateway.example", "t").toJson(broadcast);

        assertThat(json).contains("\"destination\":\"\"");
        assertThat(json).doesNotContain("null");
    }

    @Test
    public void onlyReportsStatesThatMeanSomethingToAResponder() {
        assertThat(GatewaySyncClient.isReportable(DeliveryState.CREATED)).isFalse();
        assertThat(GatewaySyncClient.isReportable(DeliveryState.ACKNOWLEDGED)).isTrue();
        assertThat(GatewaySyncClient.isReportable(DeliveryState.EXPIRED)).isTrue();
    }

    @Test
    public void reportsTheHopCountOfAStoredEnvelope() {
        assertThat(GatewaySyncClient.hopCountOf(record("m1", DeliveryState.DELIVERED)))
                .as("an undecodable envelope reports -1 rather than a fabricated hop count")
                .isEqualTo(-1);
    }
}
