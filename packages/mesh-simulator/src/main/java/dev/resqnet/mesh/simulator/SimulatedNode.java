package dev.resqnet.mesh.simulator;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

import dev.resqnet.mesh.crypto.InMemorySigningIdentity;
import dev.resqnet.mesh.crypto.TrustStore;
import dev.resqnet.mesh.routing.DuplicateCache;
import dev.resqnet.mesh.routing.InMemoryMessageStore;
import dev.resqnet.mesh.routing.MeshClock;
import dev.resqnet.mesh.routing.MeshEvent;
import dev.resqnet.mesh.routing.MeshRouter;
import dev.resqnet.mesh.routing.RouterConfig;
import dev.resqnet.mesh.triage.KeywordTriageClassifier;
import dev.resqnet.mesh.triage.TriageClassifier;

/**
 * One simulated handset.
 *
 * <p>This is a real {@link MeshRouter} with a real EC key, real signatures, real
 * validation and the real durable queue. Only the radio is synthetic. That is
 * what makes a delivery result reported by the simulator a statement about the
 * handset implementation rather than about a model of it.
 */
public final class SimulatedNode implements AutoCloseable {

    private final int index;
    private final String label;
    private final InMemorySigningIdentity identity;
    private final TrustStore trustStore;
    private final InMemoryMessageStore store;
    private final SimulatedRadio radio;
    private final MeshRouter router;
    private final List<SimulationEvent> events = new ArrayList<>();
    private final List<String> inbox = new ArrayList<>();

    private int round = -1;

    SimulatedNode(int index, SimulationConfig config, MeshClock.ManualClock clock,
                  SimulationNetwork network, TriageClassifier classifier) throws GeneralSecurityException {
        this.index = index;
        this.label = "node-" + index;
        this.identity = InMemorySigningIdentity.generate(label);
        this.trustStore = new TrustStore();
        this.store = new InMemoryMessageStore();
        this.radio = new SimulatedRadio(network, index);
        RouterConfig routerConfig = config.routerConfig();
        this.router = new MeshRouter(identity, trustStore, store, radio, clock, routerConfig,
                classifier,
                new DuplicateCache(4096, config.messageLifetimeSeconds() * 1000L,
                        4 * 1024 * 1024),
                config.retryPolicy());
        // The simulator seeds message ids deterministically so a run replays.
        this.router.setIdSource(new java.util.Random(config.seed() * 31 + index));
        this.router.addEventListener(event ->
                events.add(SimulationEvent.from(event, round, index, network::labelOf)));
        this.router.setDeliveryListener((envelope, via, hops) -> inbox.add(envelope.messageId()));
    }

    /**
     * Confirms every other node's key.
     *
     * <p>This models the out-of-band key exchange described in
     * {@code docs/architecture/README.md}, pre-applied for every pair so the
     * simulator measures routing rather than key exchange UI. It is an explicit
     * assumption, and it is favourable to ResQNet: a real deployment may have
     * neighbours whose keys have never been confirmed, whose traffic will be
     * refused.
     */
    void crossTrust(SimulatedNode other) throws GeneralSecurityException {
        trustStore.rememberUnverified(other.label, other.identity.publicKey(),
                java.time.Instant.EPOCH);
    }

    void startRound(int roundIndex) {
        this.round = roundIndex;
    }

    public int index() {
        return index;
    }

    public String label() {
        return label;
    }

    public String deviceId() {
        return identity.deviceId();
    }

    public String shortDeviceId() {
        return dev.resqnet.mesh.crypto.DeviceIdentity.shortDeviceId(deviceId());
    }

    public String displayName() {
        return label;
    }

    public String keyFingerprint() {
        return identity.keyFingerprint();
    }

    public MeshRouter router() {
        return router;
    }

    public InMemoryMessageStore store() {
        return store;
    }

    public TrustStore trustStore() {
        return trustStore;
    }

    public SimulatedRadio radio() {
        return radio;
    }

    public List<SimulationEvent> events() {
        return List.copyOf(events);
    }

    /** Message ids delivered to this node's inbox. */
    public List<String> inbox() {
        return List.copyOf(inbox);
    }

    /** Human-readable short id used by the dashboard and by report JSON. */
    public String shortId() {
        return "n" + index + "-" + shortDeviceId().substring(5, 11);
    }

    @Override
    public void close() {
        router.close();
        radio.stop();
        store.close();
    }

    /** Default classifier for simulation runs: the shipped offline one. */
    static TriageClassifier defaultClassifier() {
        return new KeywordTriageClassifier();
    }

    /** Convenience used by reports. */
    List<MeshEvent> rawEvents() {
        return List.of();
    }
}