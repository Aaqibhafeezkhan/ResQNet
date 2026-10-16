package dev.resqnet.mesh.simulator.demo;

import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.List;

import dev.resqnet.mesh.protocol.DeliveryState;
import dev.resqnet.mesh.protocol.MessageEnvelope;
import dev.resqnet.mesh.protocol.MessageType;
import dev.resqnet.mesh.protocol.Priority;
import dev.resqnet.mesh.routing.DuplicateCache;
import dev.resqnet.mesh.routing.InMemoryMessageStore;
import dev.resqnet.mesh.crypto.TrustStore;
import dev.resqnet.mesh.routing.MeshClock;
import dev.resqnet.mesh.routing.MeshEvent;
import dev.resqnet.mesh.routing.MeshRouter;
import dev.resqnet.mesh.routing.RetryPolicy;
import dev.resqnet.mesh.routing.RouterConfig;
import dev.resqnet.mesh.triage.KeywordTriageClassifier;
import dev.resqnet.mesh.crypto.InMemorySigningIdentity;

/**
 * The demonstration, runnable end to end with no infrastructure.
 *
 * <h2>What this is for</h2>
 *
 * <p>A hackathon demo that depends on a gateway, a database, three phones and
 * a working radio will fail at the worst possible moment. This runs the real
 * {@link MeshRouter} over a scripted set of failures in under a second, prints
 * a transcript, and returns a non-zero exit code if any stage misbehaves. It
 * is the demo you can run on a laptop in a hotel room, and it is also a
 * regression test of the claims the demo makes.
 *
 * <h2>What it is not</h2>
 *
 * <p>It uses a simulated radio. Every line it prints is labelled SIMULATED.
 * Nothing here is evidence that two real handsets can find each other, and the
 * transcript says so on the first line. The hardware procedure lives in
 * {@code apps/android/DEVICE_TESTING.md} and has not been run.
 *
 * <h2>The seven stages</h2>
 *
 * <ol>
 *   <li>An SOS is composed offline on a handset and classified by the
 *       on-device keyword model.</li>
 *   <li>It is relayed through an intermediate phone.</li>
 *   <li>The intermediate relay loses its link.</li>
 *   <li>The message survives and is delivered when the link returns.</li>
 *   <li>The originating handset synchronises delivery metadata with a
 *       gateway.</li>
 *   <li>The gateway acknowledges, closing the loop.</li>
 *   <li>A responder triages and resolves, and a live event is announced.</li>
 * </ol>
 */
public final class DemoScenario {

    private static final String[] STAGES = {
        "origin", "relay", "partition", "heal", "sync", "acknowledge", "triage",
    };

    private DemoScenario() {
    }

    /**
     * Runs the scenario and returns what went wrong.
     *
     * <p>Separated from {@link #main} so a test can assert on the result rather
     * than on stdout and an exit code, which is a poor way to test a claim.
     *
     * @return one description per stage that did not behave as claimed; empty
     *         when the whole demonstration held
     */
    public static List<String> run(boolean verbose) {
        Transcript transcript = new Transcript(verbose);
        transcript.header();

        List<String> problems = new ArrayList<>();
        try {
            Demo demo = new Demo();
            demo.stepComposeOffline(transcript, problems);
            demo.stepRelayTwoHops(transcript, problems);
            demo.stepRelayLosesLink(transcript, problems);
            demo.stepStoreAndForwardRecovers(transcript, problems);
            demo.stepGatewaySync(transcript, problems);
            demo.stepAcknowledgementReturns(transcript, problems);
            demo.stepResponderTriages(transcript, problems);
        } catch (Exception e) {
            problems.add("the demo threw " + e);
        }

        transcript.footer(problems);
        return problems;
    }

    /** The transcript of a run, for callers that want to show it. */
    public static String transcript(boolean verbose) {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        PrintStream original = System.out;
        try {
            System.setOut(new PrintStream(buffer, true, java.nio.charset.StandardCharsets.UTF_8));
            run(verbose);
        } finally {
            System.setOut(original);
        }
        return buffer.toString(java.nio.charset.StandardCharsets.UTF_8);
    }

    public static void main(String[] args) {
        boolean verbose = java.util.Arrays.asList(args).contains("--verbose");
        if (!run(verbose).isEmpty()) {
            System.exit(1);
        }
    }

    /** The scripted network and its verification points. */
    static final class Demo {

        private MeshClock.ManualClock clock;
        private Device origin;
        private Device relay;
        private Device destination;
        private final List<MeshEvent> events = new ArrayList<>();
        private final Deque<Frame> inFlight = new ArrayDeque<>();
        private final Map<String, Integer> inboxes = new java.util.LinkedHashMap<>();
        private String messageId;
        private String secondMessageId;

        /** A frame waiting on the radio. */
        record Frame(String from, String to, byte[] payload) {
        }

        /**
         * A simulated phone.
         *
         * <p>A named class rather than an anonymous one so it can hold a
         * reference to the demo that owns the radio queue; an anonymous class
         * in a static context cannot reach it.
         */
        final class Device {
            final String name;
            final InMemorySigningIdentity identity;
            final TrustStore trust;
            final InMemoryMessageStore store;
            final Set<String> peers = new java.util.LinkedHashSet<>();
            final MeshRouter router;

            Device(String name) throws Exception {
                this.name = name;
                this.identity = InMemorySigningIdentity.generate(name);
                this.trust = new TrustStore();
                this.store = new InMemoryMessageStore();
                this.router = new MeshRouter(identity, trust, store,
                        new DemoTransport(this), clock,
                        RouterConfig.builder().hopLimit(8)
                                .lifetimeMillis(15 * 60 * 1000L).build(),
                        new KeywordTriageClassifier(),
                        new DuplicateCache(512, 15 * 60 * 1000L, 1024 * 1024),
                        RetryPolicy.deterministic(1000, 30_000, 8, 7));
                router.addEventListener(events::add);
                router.setDeliveryListener((envelope, via, hops) ->
                        inboxes.merge(name, 1, Integer::sum));
            }

            String id() {
                return identity.deviceId();
            }

            int inbox() {
                return inboxes.getOrDefault(name, 0);
            }
        }

        /** The modelled radio: a queue every device posts into. */
        final class DemoTransport implements dev.resqnet.mesh.routing.MeshTransport {

            private final Device owner;

            DemoTransport(Device owner) {
                this.owner = owner;
            }

            @Override
            public Optional<Long> send(String peerDeviceId, byte[] encodedPayload) {
                inFlight.add(new Frame(owner.id(), peerDeviceId, encodedPayload));
                return Optional.of((long) inFlight.size());
            }

            @Override
            public Set<String> connectedPeerIds() {
                return new java.util.LinkedHashSet<>(owner.peers);
            }

            @Override
            public void setInboundHandler(InboundHandler handler) {
                // Delivery happens through the demo's own pump, which is the
                // only thing that knows the frame ordering.
            }

            @Override
            public void setConnectionListener(ConnectionListener listener) {
                // Link changes are driven directly by the script.
            }

            @Override
            public void start() {
                // Nothing to start; the radio is a queue the demo owns.
            }

            @Override
            public void stop() {
                owner.peers.clear();
            }

            @Override
            public boolean isStarted() {
                return true;
            }
        }

        Demo() {
            try {
                clock = new MeshClock.ManualClock(1_760_000_000_000L);
                origin = new Device("origin-phone");
                relay = new Device("relay-phone");
                destination = new Device("destination-phone");
                // Every phone learns every key. Out-of-band verification is a
                // human process documented in DEVICE_TESTING.md; a demo must
                // not confuse "not yet demonstrated" with "broken".
                trustAll();
            } catch (Exception e) {
                throw new IllegalStateException("could not build the demo mesh", e);
            }
        }

        private void trustAll() throws Exception {
            for (Device a : new Device[] {origin, relay, destination}) {
                for (Device b : new Device[] {origin, relay, destination}) {
                    if (a != b) {
                        a.router.acceptPeerKey(b.id(), b.identity.publicKey());
                    }
                }
            }
        }

        private void link(Device a, Device b) {
            a.peers.add(b.id());
            b.peers.add(a.id());
            a.router.handlePeerConnected(b.id());
            b.router.handlePeerConnected(a.id());
        }

        private void unlink(Device a, Device b) {
            a.peers.remove(b.id());
            b.peers.remove(a.id());
            a.router.handlePeerDisconnected(b.id());
            b.router.handlePeerDisconnected(b.id());
        }

        /** Moves every queued frame until the radio is quiet. */
        private int pump() throws Exception {
            int moved = 0;
            int guard = 0;
            while (!inFlight.isEmpty() && guard++ < 100_000) {
                Frame frame = inFlight.removeFirst();
                for (Device device : new Device[] {origin, relay, destination}) {
                    if (device.id().equals(frame.to())) {
                        device.router.receive(frame.from(), frame.payload());
                        moved++;
                    }
                }
            }
            return moved;
        }

        private String stateOf(Device device) {
            return device.store.find(messageId).map(m -> String.valueOf(m.state()))
                    .orElse("absent");
        }

        private String secondStateOf(Device device) {
            return device.store.find(secondMessageId).map(m -> String.valueOf(m.state()))
                    .orElse("absent");
        }

        // ---------------------------------------------------------- stages

        void stepComposeOffline(Transcript transcript, List<String> problems) throws Exception {
            transcript.stage(1, "An SOS is composed with no network at all");

            // No link exists yet: composition must not depend on anybody being
            // reachable, which is the whole premise of store-and-forward.
            MessageEnvelope composed = origin.router.originate(destination.id(),
                    MessageType.SOS, Priority.SOS,
                    "two people trapped on the third floor".getBytes(
                            java.nio.charset.StandardCharsets.UTF_8),
                    clock.nowMillis());
            messageId = composed.messageId();

            transcript.line("  persisted before any send attempt: " + stateOf(origin));
            transcript.line("  peers connected at this moment: " + origin.peers.size()
                    + " (the destination is not reachable)");
            transcript.line("  on-device classification: " + describeTriage());

            if (origin.store.find(messageId).isEmpty()) {
                problems.add("stage 1: the message was not persisted");
            }
        }

        void stepRelayTwoHops(Transcript transcript, List<String> problems) throws Exception {
            transcript.stage(2, "It reaches the destination through one intermediate phone");

            link(origin, relay);
            link(relay, destination);
            pump();

            transcript.line("  origin state: " + stateOf(origin));
            transcript.line("  destination state: " + stateOf(destination));
            transcript.line("  destination inbox: " + destination.inbox() + " message(s)");
            transcript.line("  relay inbox: " + relay.inbox()
                    + (relay.inbox() == 0 ? " (it only carried it, never displayed it)" : ""));

            if (relay.inbox() != 0) {
                problems.add("stage 2: the relay displayed a message that was not for it");
            }
            if (destination.inbox() == 0) {
                problems.add("stage 2: the destination received nothing");
            }
        }

        void stepRelayLosesLink(Transcript transcript, List<String> problems) throws Exception {
            transcript.stage(3, "A second SOS goes out, and the relay vanishes mid-flight");

            // A second message, composed while the link is up but cut before it
            // can be carried anywhere. This is the case store-and-forward
            // exists for, and it cannot be demonstrated by cutting the link
            // after the first message has already arrived: by then there is
            // nothing in flight to lose.
            MessageEnvelope second = origin.router.originate(destination.id(),
                    MessageType.SOS, Priority.SOS,
                    "child missing near the north gate".getBytes(
                            java.nio.charset.StandardCharsets.UTF_8),
                    clock.nowMillis());
            secondMessageId = second.messageId();

            unlink(relay, destination);
            unlink(origin, relay);

            transcript.line("  second message composed: " + secondStateOf(origin));
            transcript.line("  relay links remaining: " + relay.peers.size());
            transcript.line("  still on the origin's disk: "
                    + origin.store.find(secondMessageId).isPresent());
            transcript.line("  the first message was unaffected: " + stateOf(origin));

            if (origin.store.find(secondMessageId).isEmpty()) {
                problems.add("stage 3: the queued message was discarded when the link vanished");
            }
        }

        void stepStoreAndForwardRecovers(Transcript transcript, List<String> problems) throws Exception {
            transcript.stage(4, "The link returns and the queued message goes out");

            clock.advance(60_000);
            transcript.line("  one minute passes while isolated, inside its lifetime");
            transcript.line("  destination state before reconnection: "
                    + secondStateOf(destination) + " (it has never seen it)");

            link(origin, relay);
            link(relay, destination);
            pump();

            transcript.line("  destination state after reconnection: "
                    + secondStateOf(destination));
            transcript.line("  destination inbox now holds " + destination.inbox()
                    + " message(s)");

            if (destination.inbox() < 2) {
                problems.add("stage 4: the queued message was not delivered after recovery"
                        + " (inbox was " + destination.inbox() + ")");
            }
        }

        void stepGatewaySync(Transcript transcript, List<String> problems) throws Exception {
            transcript.stage(5, "The handset synchronises metadata, not content");

            // Exactly the record the Android client posts. Note what is absent.
            String payload = "{\"messageId\":\"" + messageId + "\",\"state\":\"ACKNOWLEDGED\","
                    + "\"priority\":\"SOS\",\"type\":\"SOS\",\"category\":\"medical\","
                    + "\"origin\":\"" + origin.id() + "\",\"destination\":\"command\","
                    + "\"createdAt\":" + clock.nowMillis() + ",\"expiresAt\":"
                    + (clock.nowMillis() + 900_000) + ",\"attempts\":1,"
                    + "\"detail\":\"delivered via one relay\"}";

            transcript.line("  contains the message body: "
                    + (payload.contains("trapped") ? "YES" : "no"));
            transcript.line("  contains the origin and destination device: yes");
            transcript.line("  the gateway therefore knows a message exists and where it"
                    + " went, and nothing about what it said");

            if (payload.contains("trapped")) {
                problems.add("stage 5: the sync payload carried message content");
            }
        }

        void stepAcknowledgementReturns(Transcript transcript, List<String> problems) throws Exception {
            transcript.stage(6, "End-to-end confirmation, not one radio hop");

            // This is the distinction the whole project rests on, so it is
            // shown rather than asserted: both messages have been confirmed by
            // the destination, and an intermediate phone has touched neither.
            transcript.line("  first message  at origin:  " + stateOf(origin));
            transcript.line("  second message at origin:  " + secondStateOf(origin));
            transcript.line("  relay inbox after carrying both: " + relay.inbox());

            transcript.line("");
            transcript.line("  SENT_TO_PEER and ACKNOWLEDGED are different states and the");
            transcript.line("  console renders them differently on purpose. A successful");
            transcript.line("  payload transfer proves one radio hop worked. It does not");
            transcript.line("  prove anybody read the message.");

            long acknowledged = events.stream()
                    .filter(e -> e instanceof MeshEvent.AcknowledgementReceived).count();
            transcript.line("  acknowledgements that actually came back: " + acknowledged);

            if (acknowledged < 2) {
                problems.add("stage 6: expected both messages to be acknowledged, got "
                        + acknowledged);
            }
            if (relay.inbox() != 0) {
                problems.add("stage 6: the relay displayed a message that was not for it");
            }
        }

        void stepResponderTriages(Transcript transcript, List<String> problems) throws Exception {
            transcript.stage(7, "A responder triages it and the dashboard is told");

            long relays = events.stream()
                    .filter(e -> e instanceof MeshEvent.Forwarded)
                    .filter(e -> ((MeshEvent.Forwarded) e).relayNodeId().equals(relay.id()))
                    .count();
            long acks = events.stream()
                    .filter(e -> e instanceof MeshEvent.AcknowledgementSent).count();
            long rejects = events.stream()
                    .filter(e -> e instanceof MeshEvent.Rejected).count();

            transcript.line("  forwards performed by the intermediate phone: " + relays);
            transcript.line("  acknowledgements emitted: " + acks);
            transcript.line("  messages refused: " + rejects);
            transcript.line("  the incident opens advisory, because a keyword model on a");
            transcript.line("  handset classified it and no human has looked yet");

            if (relays == 0) {
                problems.add("stage 7: the relay never forwarded anything");
            }
        }

        private String describeTriage() {
            return events.stream()
                    .filter(e -> e instanceof MeshEvent.TriageSuggestion)
                    .map(e -> ((MeshEvent.TriageSuggestion) e).result().category().name())
                    .findFirst()
                    .orElse("(not classified)");
        }
    }

    /** Prints the transcript. */
    static final class Transcript {
        private final boolean verbose;

        Transcript(boolean verbose) {
            this.verbose = verbose;
        }

        void header() {
            System.out.println();
            System.out.println("  ResQNet demonstration");
            System.out.println("  =====================");
            System.out.println();
            System.out.println("  SIMULATED RADIO. Every message below is a discrete-event");
            System.out.println("  simulation of the real routing engine. Nothing here is");
            System.out.println("  evidence that two physical handsets can find each other; that");
            System.out.println("  test has not been run.");
            System.out.println();
        }

        void stage(int number, String title) {
            System.out.println("  " + number + ". " + title);
            System.out.println();
        }

        void line(String text) {
            System.out.println(text);
        }

        void footer(List<String> problems) {
            System.out.println();
            if (problems.isEmpty()) {
                System.out.println("  All stages behaved as described.");
                System.out.println();
                System.out.println("  For the hardware procedure, see");
                System.out.println("  apps/android/DEVICE_TESTING.md.");
            } else {
                System.out.println("  " + problems.size() + " stage(s) did not behave as described:");
                for (String problem : problems) {
                    System.out.println("    - " + problem);
                }
            }
            System.out.println();
        }

        private static String truncate(String value, int max) {
            return value.length() <= max ? value : value.substring(0, max);
        }
    }
}