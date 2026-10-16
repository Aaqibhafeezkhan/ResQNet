package dev.resqnet.mesh.routing;

import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import dev.resqnet.mesh.crypto.SignatureVerifier;
import dev.resqnet.mesh.crypto.SigningIdentity;
import dev.resqnet.mesh.crypto.TrustStore;
import dev.resqnet.mesh.protocol.DeliveryState;
import dev.resqnet.mesh.protocol.EnvelopeCodec;
import dev.resqnet.mesh.protocol.EnvelopeValidator;
import dev.resqnet.mesh.protocol.MessageEnvelope;
import dev.resqnet.mesh.protocol.MessageType;
import dev.resqnet.mesh.protocol.Priority;
import dev.resqnet.mesh.protocol.ProtocolException;
import dev.resqnet.mesh.protocol.ProtocolVersion;
import dev.resqnet.mesh.protocol.RejectReason;
import dev.resqnet.mesh.triage.KeywordTriageClassifier;
import dev.resqnet.mesh.triage.TriageClassifier;

/**
 * The ResQNet routing engine: controlled flooding with end-to-end confirmation.
 *
 * <p>Controlled flooding is the right algorithm for this problem, and the
 * reasoning is worth stating plainly. The topology is unknown, ephemeral,
 * peer-to-peer and human-scale: a dozen phones in a valley, none of which
 * knows the route to any other. Any table-driven protocol (DSR, AODV, GPSR)
 * assumes either a link state the mesh does not have or a maintained topology
 * that costs battery to build and goes stale the instant a phone locks its
 * screen. Flooding needs no topology at all, and at this scale its cost is
 * bounded: each message is forwarded at most once per node by the duplicate
 * cache, and at most {@code hopLimit} times overall.
 *
 * <p>What flooding does <em>not</em> give you is proof of delivery. A successful
 * radio transfer proves one hop worked. This engine therefore separates the two
 * explicitly: {@link RoutingOutcome#RELAYED} and
 * {@link RoutingOutcome#SENT_TO_PEER} mean progress, while
 * {@link RoutingOutcome#DELIVERED_LOCALLY} and {@link RoutingOutcome#ACKNOWLEDGED}
 * mean the message actually reached someone. The UI must never conflate them,
 * because in an emergency telling a stranded person "sent" when nobody received
 * it is worse than saying nothing at all.
 *
 * <p>Ordering inside {@link #receive} is a security boundary, not a style
 * choice. Every frame is decoded, structurally validated, checked for expiry,
 * authenticated, and then deduplicated before it can touch the relay queue or
 * the inbox. The cheapest and most damaging attacks (oversized payloads, replay,
 * forged origins, amplification) are all stopped at the top.
 *
 * <p>Thread safe. Inbound traffic is delivered on transport threads while the UI
 * and a retry pump may call in concurrently.
 */
public final class MeshRouter implements AutoCloseable {

    private final SigningIdentity self;
    private final TrustStore trustStore;
    private final MessageStore store;
    private final MeshTransport transport;
    private final MeshClock clock;
    private final RouterConfig config;
    private final EnvelopeValidator strictValidator;
    private final EnvelopeValidator identityValidator;
    private final DuplicateCache duplicateCache;
    private final FloodControlLimiter floodControl;
    private final RetryPolicy retryPolicy;
    private final RoutingMetrics metrics = new RoutingMetrics();
    private final TriageClassifier triageClassifier;

    private final List<MeshEventListener> eventListeners = new CopyOnWriteArrayList<>();
    private volatile LocalDeliveryListener deliveryListener;
    private volatile IdentityAnnouncementListener identityListener;
    private final AtomicLong localSequence = new AtomicLong(1);
    private volatile MessageIdSource idSource = defaultIdSource();

    private static MessageIdSource defaultIdSource() {
        java.util.Random random = new java.util.Random(new java.security.SecureRandom().nextLong());
        return () -> random.nextLong();
    }

    /**
     * @param self this device's signing identity; its key-derived id is the only
     *     address the router recognises as "me"
     * @param trustStore peers whose keys have been confirmed out of band
     * @param store durable queue backing store-and-forward
     * @param transport radio abstraction, may be null in unit tests that drive
     *     {@link #receive} directly
     */
    public MeshRouter(SigningIdentity self, TrustStore trustStore, MessageStore store,
                      MeshTransport transport, MeshClock clock, RouterConfig config) {
        this(self, trustStore, store, transport, clock, config,
                new KeywordTriageClassifier(), new DuplicateCache(),
                RetryPolicy.defaultMobile());
    }

    /** Full-construction seam used by the simulator for deterministic wiring. */
    public MeshRouter(SigningIdentity self, TrustStore trustStore, MessageStore store,
                      MeshTransport transport, MeshClock clock, RouterConfig config,
                      TriageClassifier triageClassifier, DuplicateCache duplicateCache,
                      RetryPolicy retryPolicy) {
        this.self = java.util.Objects.requireNonNull(self, "self");
        this.trustStore = java.util.Objects.requireNonNull(trustStore, "trustStore");
        this.store = java.util.Objects.requireNonNull(store, "store");
        this.transport = transport;
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.config = java.util.Objects.requireNonNull(config, "config");
        this.triageClassifier = triageClassifier == null
                ? TriageClassifier.disabled() : triageClassifier;
        this.duplicateCache = duplicateCache == null ? new DuplicateCache() : duplicateCache;
        this.floodControl = new FloodControlLimiter();
        this.retryPolicy = retryPolicy == null ? RetryPolicy.defaultMobile() : retryPolicy;
        this.strictValidator = new EnvelopeValidator(config.requireSignature(),
                config.maxAcceptedLifetimeMillis(), config.maxAcceptedHopLimit());
        this.identityValidator = new EnvelopeValidator(false,
                config.maxAcceptedLifetimeMillis(), config.maxAcceptedHopLimit());
        if (transport != null) {
            attachTransport(transport);
        }
    }

    /**
     * Wires this router to a radio.
     *
     * <p>Kept out of the constructor body so that no reference to {@code this}
     * escapes before every final field is assigned; a transport that fires a
     * connection callback synchronously during registration would otherwise see
     * a partially constructed router.
     */
    private void attachTransport(MeshTransport radio) {
        radio.setInboundHandler(this::receive);
        radio.setConnectionListener(new MeshTransport.ConnectionListener() {
            @Override
            public void onPeerConnected(String peerDeviceId) {
                handlePeerConnected(peerDeviceId);
            }

            @Override
            public void onPeerDisconnected(String peerDeviceId) {
                handlePeerDisconnected(peerDeviceId);
            }
        });
    }

    // ------------------------------------------------------------------ setup

    public void addEventListener(MeshEventListener listener) {
        eventListeners.add(java.util.Objects.requireNonNull(listener, "listener"));
    }

    public void removeEventListener(MeshEventListener listener) {
        eventListeners.remove(listener);
    }

    public void setDeliveryListener(LocalDeliveryListener listener) {
        this.deliveryListener = listener;
    }

    public void setIdentityAnnouncementListener(IdentityAnnouncementListener listener) {
        this.identityListener = listener;
    }

    /** Exposes metrics for the dashboard and for assertions in tests. */
    public RoutingMetrics metrics() {
        return metrics;
    }

    public SigningIdentity identity() {
        return self;
    }

    public TrustStore trustStore() {
        return trustStore;
    }

    public MessageStore store() {
        return store;
    }

    public RouterConfig config() {
        return config;
    }

    public DuplicateCache duplicateCache() {
        return duplicateCache;
    }

    public MeshClock clock() {
        return clock;
    }

    /** This device's stable, key-derived identifier. */
    public String deviceId() {
        return self.deviceId();
    }

    // ------------------------------------------------------------- origination

    /**
     * Creates, signs, persists and attempts to send a message.
     *
     * <p>Persisting before the first send attempt is deliberate. If the process
     * dies between "user pressed send" and "radio handed over the frame", the
     * message must still exist, because from the user's point of view they sent
     * it. Store-and-forward only works if the write happens first.
     *
     * @param destinationDeviceId recipient, or {@code null} for a broadcast
     * @return the signed envelope
     * @throws ProtocolException when the message violates protocol limits
     * @throws GeneralSecurityException when signing fails
     */
    public MessageEnvelope originate(String destinationDeviceId, MessageType type,
                                     Priority priority, byte[] payload, long lifetimeMillis)
            throws ProtocolException, GeneralSecurityException {
        long now = clock.nowMillis();
        long lifetime = lifetimeMillis > 0
                ? Math.min(lifetimeMillis, config.lifetimeMillis()) : config.lifetimeMillis();

        MessageEnvelope unsigned = MessageEnvelope.builder()
                .messageId(newMessageId(now))
                .origin(self.deviceId())
                .originKeyFingerprint(self.keyFingerprint())
                .destination(destinationDeviceId)
                .type(type)
                .priority(priority)
                .createdAt(now)
                .expiresAt(now + lifetime)
                .hopLimit(config.hopLimit())
                .payload(payload)
                .build();

        MessageEnvelope signed = sign(unsigned);
        byte[] encoded = EnvelopeCodec.encode(signed);

        store.upsert(StoredMessage.of(signed, encoded, now));
        enforceQueueCapacity();
        metrics.recordOriginated(signed.destinationDeviceId().isPresent());

        emit(new MeshEvent.DeliveryStateChanged(now, self.deviceId(), signed.messageId(),
                DeliveryState.CREATED, DeliveryState.QUEUED, "composed on this device"));

        if (destinationDeviceId == null) {
            // A broadcast is addressed to this device too. The sender must see
            // their own SOS in the conversation, otherwise a one-phone mesh
            // looks like nothing happened at all.
            deliverToOwnInbox(signed, now);
        }
        attemptPending();
        return signed;
    }

    /** Convenience overload for a broadcast SOS with the configured lifetime. */
    public MessageEnvelope originateBroadcast(MessageType type, Priority priority, byte[] payload)
            throws ProtocolException, GeneralSecurityException {
        return originate(null, type, priority, payload, 0L);
    }

    /**
     * Signs an envelope with this device's key.
     *
     * <p>The signature covers the canonical unsigned form from
     * {@link EnvelopeCodec#signingBytes}, which deliberately excludes the hop
     * count and hop budget. Relays legitimately change those, and a signature
     * that covered them would break multi-hop forwarding.
     */
    public MessageEnvelope sign(MessageEnvelope envelope)
            throws GeneralSecurityException, ProtocolException {
        if (envelope == null) {
            throw new GeneralSecurityException("Cannot sign a null envelope");
        }
        byte[] signature = self.sign(envelope.signaturePayload());
        return envelope.toBuilder().buildSigned(signature, self.signatureAlgorithm());
    }

    /**
     * Generates a fresh, collision-resistant message identifier.
     *
     * <p>The identifier is built from the clock, a per-router counter and a
     * random suffix. It deliberately does <em>not</em> embed the device id: ids
     * are sorted by the durable store, so a device-derived component would make
     * store iteration order depend on the freshly generated key pair and would
     * destroy run-to-run reproducibility in the simulator.
     *
     * <p>Uniqueness across nodes comes from the random suffix, and a collision
     * would merely cause a spurious duplicate suppression, which is the safe
     * direction to fail.
     */
    public String newMessageId(long nowMillis) {
        return "m" + Long.toHexString(nowMillis)
                + "-" + Long.toHexString(localSequence.getAndIncrement())
                + "-" + Long.toHexString(idSource.nextLong() & 0xFFFFFFFFL);
    }

    /**
     * Replaces the randomness source used for message ids.
     *
     * <p>The simulator injects a seeded generator here so a run replays
     * identically. Production code must leave the default in place: message ids
     * are visible to peers, and a predictable id space invites collisions under
     * concurrent generation across a shared clock.
     */
    public void setIdSource(MessageIdSource idSource) {
        this.idSource = java.util.Objects.requireNonNull(idSource, "idSource");
    }

    /**
     * Entropy source for message identifiers.
     *
     * <p>A plain {@code long} supplier rather than
     * {@code java.util.random.RandomGenerator}, because that package does not
     * exist on the Android platform. See {@link RetryPolicy.JitterSource} for the
     * same reasoning.
     */
    @FunctionalInterface
    public interface MessageIdSource {
        long nextLong();
    }

    // ------------------------------------------------------------------ receive

    /**
     * Entry point for every inbound radio frame.
     *
     * @param fromPeerDeviceId the neighbour that delivered the frame
     * @param payload raw canonical envelope bytes
     * @return what the router decided, for logging, tests and the dashboard
     */
    public RoutingOutcome receive(String fromPeerDeviceId, byte[] payload) {
        long now = clock.nowMillis();
        MessageEnvelope envelope;

        try {
            envelope = EnvelopeCodec.decode(payload);
        } catch (ProtocolException e) {
            metrics.recordMalformedDrop();
            reject(now, null, e.reason(), e.getMessage(), fromPeerDeviceId);
            return outcomeFor(e.reason());
        }

        try {
            // Identity announcements are the one unsigned path: they carry a key
            // claim that a human must confirm before anything else is accepted
            // from it. Everything else must be authenticated by a known key.
            EnvelopeValidator validator = envelope.type() == MessageType.IDENTITY
                    ? identityValidator : strictValidator;
            validator.validate(envelope, now);
        } catch (ProtocolException e) {
            countAndReject(now, envelope, e.reason(), e.getMessage(), fromPeerDeviceId);
            return outcomeFor(e.reason());
        }

        if (envelope.expiredAt(now)) {
            metrics.recordExpired();
            store.updateState(envelope.messageId(), DeliveryState.EXPIRED,
                    "expired after " + (now - envelope.createdAtEpochMillis()) + "ms in flight");
            reject(now, envelope, RejectReason.EXPIRED, "lifetime elapsed", fromPeerDeviceId);
            return RoutingOutcome.DROPPED_EXPIRED;
        }

        if (envelope.type() == MessageType.IDENTITY) {
            return handleIdentityAnnouncement(envelope, fromPeerDeviceId, now);
        }

        // From here on the origin must be a key we have actually confirmed.
        Optional<TrustStore.Entry> originEntry = trustStore.entry(envelope.originDeviceId());
        if (originEntry.isEmpty()) {
            metrics.recordUntrustedDrop();
            reject(now, envelope, RejectReason.UNTRUSTED_ORIGIN,
                    "no key on file for the claimed origin",
                    fromPeerDeviceId);
            return RoutingOutcome.DROPPED_UNTRUSTED;
        }
        TrustStore.Entry origin = originEntry.get();
        if (origin.trust() == TrustStore.Trust.BLOCKED) {
            metrics.recordUntrustedDrop();
            reject(now, envelope, RejectReason.ORIGIN_BLOCKED,
                    "origin is blocked", fromPeerDeviceId);
            return RoutingOutcome.DROPPED_UNTRUSTED;
        }

        boolean authentic;
        try {
            authentic = SignatureVerifier.verifyEnvelope(origin, envelope);
        } catch (GeneralSecurityException e) {
            authentic = false;
        }
        if (!authentic) {
            metrics.recordUntrustedDrop();
            reject(now, envelope, RejectReason.INVALID_SIGNATURE,
                    "signature did not verify against the pinned key for the origin",
                    fromPeerDeviceId);
            return RoutingOutcome.DROPPED_UNTRUSTED;
        }

        // Deduplication is the loop terminator for controlled flooding. It must
        // come after authentication, otherwise an unauthenticated peer could
        // poison the cache and suppress a genuine message with the same id.
        if (!duplicateCache.markIfFirstSeen(envelope.messageId(), now, payload.length)) {
            metrics.recordDuplicate();
            emit(new MeshEvent.Rejected(now, self.deviceId(), envelope.messageId(),
                    envelope.originDeviceId(), RejectReason.DUPLICATE,
                    "already processed", fromPeerDeviceId));
            return RoutingOutcome.DUPLICATE;
        }

        if (envelope.type() == MessageType.ACK) {
            handleAcknowledgement(envelope, fromPeerDeviceId, now);
            // An acknowledgement still has to be relayed. Returning here would
            // leave it stranded at the first relay, and the sender would never
            // learn that its message arrived.
            if (envelope.destinationDeviceId()
                    .map(self.deviceId()::equals).orElse(false)) {
                return RoutingOutcome.ACKNOWLEDGED;
            }
            return forwardIfEligible(envelope, fromPeerDeviceId, now, true);
        }

        /**
         * True when this envelope is ours to act on. A broadcast is addressed to
         * everyone, so it counts as local delivery as well as relaying; getting
         * this wrong means a node forwards a broadcast but never shows it to its
         * own user.
         */
        boolean forSelf = envelope.addressedTo(self.deviceId());
        if (forSelf && envelope.hopExhausted() && envelope.destinationDeviceId().isPresent()) {
            metrics.recordHopLimitDrop();
            reject(now, envelope, RejectReason.HOP_LIMIT_EXCEEDED,
                    "arrived with no hops left", fromPeerDeviceId);
            return RoutingOutcome.DROPPED_HOP_LIMIT;
        }

        metrics.recordAccepted();
        emit(new MeshEvent.Accepted(now, self.deviceId(), envelope,
                envelope.hopCount(), envelope.hopLimit(), fromPeerDeviceId));

        if (forSelf) {
            deliverLocally(envelope, fromPeerDeviceId, now);
        }

        RoutingOutcome forwardOutcome = forwardIfEligible(envelope, fromPeerDeviceId, now, forSelf);

        classifyForAdvisory(envelope, now);

        if (forSelf && forwardOutcome == RoutingOutcome.DROPPED_NO_PATH) {
            return RoutingOutcome.DELIVERED_LOCALLY;
        }
        return forwardOutcome;
    }

    // ------------------------------------------------------------- forwarding

    /**
     * Applies the flooding rules to a validated envelope.
     *
     * <p>Split horizon is used: an envelope is never handed back to the peer it
     * arrived from. That removes the most common two-node loop and, on a phone
     * with a handful of neighbours, removes most of the duplicate radio traffic
     * a naive flood would generate.
     */
    private RoutingOutcome forwardIfEligible(MessageEnvelope envelope, String fromPeerDeviceId,
                                             long now, boolean alreadyDelivered) {
        if (envelope.hopExhausted()) {
            if (!alreadyDelivered) {
                metrics.recordHopLimitDrop();
                reject(now, envelope, RejectReason.HOP_LIMIT_EXCEEDED,
                        "hop budget exhausted at " + envelope.hopCount() + " hops",
                        fromPeerDeviceId);
            }
            return RoutingOutcome.DROPPED_HOP_LIMIT;
        }

        boolean lifeSafety = envelope.priority() == Priority.SOS;
        if (!floodControl.tryAcquire(envelope.originDeviceId(), lifeSafety,
                config.perOriginForwardRatePerSecond(), config.perOriginBurst(), now)) {
            metrics.recordRateLimitDrop();
            reject(now, envelope, RejectReason.RATE_LIMITED,
                    "origin exceeded the forwarding rate allowance", fromPeerDeviceId);
            return RoutingOutcome.DROPPED_RATE_LIMITED;
        }

        List<String> targets = selectForwardTargets(envelope, fromPeerDeviceId);
        if (targets.isEmpty()) {
            // Still remember it: if a peer connects in a moment we can forward
            // it then. A relay that discards on transient isolation loses the
            // message entirely, which is the one thing the mesh must not do.
            persistForRelay(envelope, now, "no eligible peer yet");
            return RoutingOutcome.QUEUED_FOR_RELAY;
        }

        int nextHopLimit = envelope.hopLimit() - 1;
        int nextHopCount = envelope.hopCount() + 1;
        MessageEnvelope relayed = envelope.relayedCopy(nextHopLimit, nextHopCount);

        byte[] encoded;
        try {
            encoded = EnvelopeCodec.encode(relayed);
        } catch (ProtocolException e) {
            metrics.recordOtherDrop();
            reject(now, envelope, RejectReason.PAYLOAD_TOO_LARGE,
                    "relayed envelope exceeded the encode limit", fromPeerDeviceId);
            return RoutingOutcome.DROPPED_MALFORMED;
        }

        List<String> delivered = new ArrayList<>(targets.size());
        for (String peer : targets) {
            Optional<Long> token = transport == null
                    ? Optional.of(1L) : transport.send(peer, encoded);
            if (token.isPresent()) {
                delivered.add(peer);
            }
        }

        if (delivered.isEmpty()) {
            persistForRelay(relayed, now, "peer sessions dropped mid-forward");
            return RoutingOutcome.QUEUED_FOR_RELAY;
        }

        metrics.recordForwarded(delivered.size(), 1);
        metrics.recordHopCount(nextHopCount);

        boolean originatedHere = envelope.originDeviceId().equals(self.deviceId());
        if (originatedHere) {
            store.updateState(envelope.messageId(), DeliveryState.SENT_TO_PEER,
                    "handed to " + delivered.size() + " peer(s); awaiting acknowledgement");
        } else {
            persistForRelay(relayed, now, "relayed via " + delivered.size() + " peer(s)");
        }

        emit(new MeshEvent.Forwarded(now, self.deviceId(), envelope.messageId(), delivered,
                nextHopCount));
        return RoutingOutcome.RELAYED;
    }

    /**
     * Chooses which neighbours receive a copy.
     *
     * <p>A directed message prefers its destination: if the intended recipient is
     * directly connected, only that peer gets the frame. Flooding a directed
     * message to unrelated neighbours would spend everyone's battery for no
     * chance of success. When the destination is not connected, the message
     * floods, because reaching it may require any neighbour.
     *
     * <p>The one exception is this node itself: a directed message that names
     * this device is not forwarded anywhere, because the recipient already has
     * it. Broadcasts have no such destination and keep propagating.
     */
    private List<String> selectForwardTargets(MessageEnvelope envelope, String fromPeerDeviceId) {
        if (transport == null) {
            return List.of();
        }
        // A node that is the *specific* destination of a directed message has
        // nothing left to do with it. Re-broadcasting from here spends every
        // neighbour's battery on a duplicate, and it would also flip this
        // node's own record from DELIVERED back to RELAYED, putting the message
        // back into the retry queue it just left.
        //
        // Note this tests the explicit destination, not addressedTo(id): a
        // broadcast is "addressed to" every node and must keep propagating.
        if (envelope.destinationDeviceId()
                .filter(self.deviceId()::equals)
                .isPresent()) {
            return List.of();
        }
        Set<String> connected = transport.connectedPeerIds();
        if (connected.isEmpty()) {
            return List.of();
        }
        List<String> candidates = new ArrayList<>();
        Optional<String> destination = envelope.destinationDeviceId();
        if (destination.isPresent() && connected.contains(destination.get())) {
            candidates.add(destination.get());
        } else {
            for (String peer : new LinkedHashSet<>(connected)) {
                if (!peer.equals(fromPeerDeviceId)) {
                    candidates.add(peer);
                }
            }
        }
        // The transport's peer order is preserved rather than re-sorted here.
        // Which peers are chosen when the fan-out cap bites must be a stable
        // function of the link table, or two identical runs can disagree about
        // who relayed what and a routing bug becomes unreplayable. Sorting by
        // device id would not achieve that: identity is a hash of a freshly
        // generated key, so its order differs between runs. Transports are
        // therefore required to expose a deterministic order.
        return candidates.size() <= config.maxFanOut()
                ? candidates : new ArrayList<>(candidates.subList(0, config.maxFanOut()));
    }

    // ---------------------------------------------------------- acknowledgement

    /**
     * Builds the application-level acknowledgement for a delivered message.
     *
     * <p>ACKs are flooded back along the same paths rather than returned on a
     * reverse route, because there is no reverse route: the mesh has no
     * connection tracking. The acknowledgement is itself a normal envelope with
     * its own hop budget, and it is exempt from generating another ACK so
     * acknowledgements cannot oscillate.
     */
    public Optional<MessageEnvelope> buildAcknowledgement(MessageEnvelope received, long now) {
        if (received == null || received.type() == MessageType.ACK
                || received.destinationDeviceId().isEmpty()) {
            return Optional.empty();
        }
        long remaining = received.remainingLifetimeMillis(now);
        if (remaining <= 0) {
            return Optional.empty();
        }
        try {
            MessageEnvelope ack = sign(MessageEnvelope.builder()
                    .messageId(newMessageId(now))
                    .origin(self.deviceId())
                    .originKeyFingerprint(self.keyFingerprint())
                    .destination(received.originDeviceId())
                    .type(MessageType.ACK)
                    .priority(received.priority())
                    .createdAt(now)
                    .expiresAt(now + remaining)
                    .hopLimit(config.hopLimit())
                    .correlationId(received.messageId())
                    .payload(new byte[0])
                    .build());
            emit(new MeshEvent.AcknowledgementSent(now, self.deviceId(),
                    received.messageId(), received.originDeviceId()));
            metrics.recordAcknowledgementSent();
            return Optional.of(ack);
        } catch (ProtocolException | GeneralSecurityException e) {
            // Losing an acknowledgement is recoverable: the message was already
            // delivered, and the sender's own expiry will bound its wait.
            return Optional.empty();
        }
    }

    private RoutingOutcome handleAcknowledgement(MessageEnvelope ack, String fromPeerDeviceId,
                                                 long now) {
        String target = ack.correlationId().orElse("");
        Optional<StoredMessage> stored = store.find(target);
        if (stored.isEmpty()) {
            metrics.recordOtherDrop();
            reject(now, ack, RejectReason.MALFORMED,
                    "acknowledgement references a message this device does not hold",
                    fromPeerDeviceId);
            return RoutingOutcome.DROPPED_MALFORMED;
        }
        if (stored.get().state() == DeliveryState.ACKNOWLEDGED) {
            metrics.recordDuplicateAcknowledgement();
            return RoutingOutcome.DUPLICATE;
        }

        // Only the originating device may conclude anything from an ACK. A
        // relay that also treated it as terminal would quietly stop re-forwarding
        // a message it had already forwarded, and would record someone else's
        // round trip in its own delivery-latency metrics.
        if (!stored.get().originDeviceId().equals(self.deviceId())) {
            store.updateState(target, stored.get().state(),
                    "relay copy confirmed delivered end-to-end");
            return RoutingOutcome.ACKNOWLEDGED;
        }

        store.updateState(target, DeliveryState.ACKNOWLEDGED,
                "acknowledged end-to-end by the intended recipient");
        metrics.recordAcknowledgementReceived();
        metrics.recordDeliveryLatency(Math.max(0, now - stored.get().createdAt()));
        emit(new MeshEvent.AcknowledgementReceived(now, self.deviceId(), target,
                ack.originDeviceId()));
        emit(new MeshEvent.DeliveryStateChanged(now, self.deviceId(), target,
                stored.get().state(), DeliveryState.ACKNOWLEDGED,
                "acknowledged end-to-end by the intended recipient"));
        return RoutingOutcome.ACKNOWLEDGED;
    }

    // --------------------------------------------------------------- delivery

    /**
     * Shows a locally composed broadcast in this device's own inbox.
     *
     * <p>Deliberately does not touch the stored delivery state. The record still
     * tracks what happened on the wire, so a broadcast the user made that could
     * not reach anyone stays visibly pending rather than being marked delivered
     * to itself.
     */
    private void deliverToOwnInbox(MessageEnvelope envelope, long now) {
        emit(new MeshEvent.Delivered(now, self.deviceId(), envelope));
        LocalDeliveryListener listener = deliveryListener;
        if (listener == null) {
            return;
        }
        try {
            listener.onDelivered(envelope, null, 0);
        } catch (RuntimeException e) {
            emit(new MeshEvent.Rejected(now, self.deviceId(), envelope.messageId(),
                    envelope.originDeviceId(), RejectReason.MALFORMED,
                    "delivery listener threw " + e.getClass().getSimpleName(), null));
        }
    }

    private void deliverLocally(MessageEnvelope envelope, String fromPeerDeviceId, long now) {
        byte[] encoded;
        try {
            encoded = EnvelopeCodec.encode(envelope);
        } catch (ProtocolException e) {
            reject(now, envelope, RejectReason.PAYLOAD_TOO_LARGE,
                    "could not re-encode for local delivery", fromPeerDeviceId);
            return;
        }
        store.upsert(StoredMessage.of(envelope, encoded, now)
                .withState(DeliveryState.DELIVERED, "received over the mesh"));
        metrics.recordDelivered();
        emit(new MeshEvent.Delivered(now, self.deviceId(), envelope));

        LocalDeliveryListener listener = deliveryListener;
        if (listener != null) {
            try {
                listener.onDelivered(envelope, fromPeerDeviceId, envelope.hopCount());
            } catch (RuntimeException e) {
                // A misbehaving UI listener must never break routing.
                emit(new MeshEvent.Rejected(now, self.deviceId(), envelope.messageId(),
                        envelope.originDeviceId(), RejectReason.MALFORMED,
                        "delivery listener threw " + e.getClass().getSimpleName(),
                        fromPeerDeviceId));
            }
        }

        if (!config.acknowledgementsEnabled()) {
            return;
        }
        buildAcknowledgement(envelope, now).ifPresent(ack -> {
            duplicateCache.markIfFirstSeen(ack.messageId(), now, 128);
            try {
                byte[] ackBytes = EnvelopeCodec.encode(ack);
                if (transport != null) {
                    for (String peer : transport.connectedPeerIds()) {
                        transport.send(peer, ackBytes);
                    }
                }
            } catch (ProtocolException e) {
                reject(now, ack, RejectReason.MALFORMED, "ack encode failed", fromPeerDeviceId);
            }
        });
    }

    // ------------------------------------------------------------ store & retry

    /**
     * Attempts to move everything the store still owes to the mesh.
     *
     * <p>Called on a timer on Android and after every connection change. This is
     * the store-and-forward heart: a message composed an hour ago with no
     * reachable peer is still sitting here, and the moment a link appears it
     * goes out without the user touching anything.
     *
     * @return number of messages handed to a peer during this pass
     */
    public int attemptPending() {
        return attemptPending(false);
    }

    /**
     * One retry pass, optionally ignoring the backoff schedule.
     *
     * @param force true when a meaningful network change (a peer connected)
     *     justifies sending now. Backoff exists to stop a node burning battery
     *     retrying into a dead network; it must not stop a node using a link
     *     that has just appeared, because in an emergency that window is
     *     measured in seconds.
     */
    private int attemptPending(boolean force) {
        long now = clock.nowMillis();
        store.expireStale(now);
        store.enforceCapacity(config.maxPendingMessages());

        int sent = 0;
        for (StoredMessage message : store.pending()) {
            if (!message.readyToAttempt(now) && !(force && !message.expired(now))) {
                continue;
            }
            if (message.expired(now)) {
                continue;
            }
            MessageEnvelope envelope;
            try {
                envelope = EnvelopeCodec.decode(message.envelope());
            } catch (ProtocolException e) {
                store.updateState(message.messageId(), DeliveryState.FAILED,
                        "stored envelope could not be decoded: " + e.reason());
                continue;
            }

            if (retryPolicy.exhausted(message.attemptCount())) {
                store.updateState(message.messageId(), DeliveryState.FAILED,
                        "gave up after " + message.attemptCount() + " attempts");
                continue;
            }

            RoutingOutcome outcome = forwardIfEligible(envelope, null, now,
                    envelope.addressedTo(self.deviceId()));
            if (outcome == RoutingOutcome.RELAYED || outcome == RoutingOutcome.QUEUED_FOR_RELAY) {
                int attempts = message.attemptCount() + 1;
                long nextAttempt = retryPolicy.delayBeforeAttempt(attempts, now);
                // Re-read rather than reusing the snapshot: the forward attempt
                // above may already have moved this record to SENT_TO_PEER, and
                // writing the stale QUEUED state back would erase real progress
                // and make the UI show a message as "not sent" after it was.
                store.find(message.messageId())
                        .ifPresent(latest -> store.upsert(latest.withRetry(
                                attempts, nextAttempt)));
                if (outcome == RoutingOutcome.RELAYED) {
                    sent++;
                }
            }
        }
        return sent;
    }

    /** Applies queue capacity, evicting the least valuable work first. */
    public void enforceQueueCapacity() {
        int evicted = store.enforceCapacity(config.maxPendingMessages());
        if (evicted > 0) {
            metrics.recordRelayQueueEviction();
            emit(new MeshEvent.BackpressureApplied(clock.nowMillis(), self.deviceId(),
                    store.pendingCount(), config.maxPendingMessages()));
        }
    }

    /** Called by the transport when a peer becomes reachable. */
    public void handlePeerConnected(String peerDeviceId) {
        emit(new MeshEvent.PeerConnectionChanged(clock.nowMillis(), peerDeviceId, true));
        // A new link is exactly the meaningful network change worth retrying
        // for. Attempt immediately rather than waiting for the next timer tick,
        // because in an emergency the difference is seconds.
        attemptPending(true);
    }

    /** Called by the transport when a peer disappears. */
    public void handlePeerDisconnected(String peerDeviceId) {
        floodControl.forget(peerDeviceId);
        emit(new MeshEvent.PeerConnectionChanged(clock.nowMillis(), peerDeviceId, false));
    }

    // ---------------------------------------------------------------- identity

    /** Hop budget for an identity claim. See {@link #publishIdentity()}. */
    public static final int IDENTITY_HOP_LIMIT = 4;

    private RoutingOutcome handleIdentityAnnouncement(MessageEnvelope envelope,
                                                      String fromPeerDeviceId, long now) {
        if (!IdentityAnnouncement.looksLikeIdentity(envelope.payload())) {
            metrics.recordMalformedDrop();
            reject(now, envelope, RejectReason.MALFORMED,
                    "identity envelope payload is not an announcement", fromPeerDeviceId);
            return RoutingOutcome.DROPPED_MALFORMED;
        }
        IdentityAnnouncement announcement;
        try {
            announcement = IdentityAnnouncement.decode(envelope.payload());
        } catch (IllegalArgumentException e) {
            metrics.recordMalformedDrop();
            reject(now, envelope, RejectReason.MALFORMED, e.getMessage(), fromPeerDeviceId);
            return RoutingOutcome.DROPPED_MALFORMED;
        }
        IdentityAnnouncementListener listener = identityListener;
        if (listener != null) {
            listener.onIdentityAnnouncement(envelope, announcement, fromPeerDeviceId);
        }

        // Actually relay it.
        //
        // This used to return QUEUED_FOR_RELAY without relaying or storing
        // anything, which is simply a lie: the claim arrived, was parsed, and
        // died. A device could therefore only ever learn about its direct
        // neighbours, which is the same defect as a hop budget of one, and the
        // two compounded into "no trust is ever established across a mesh".
        //
        // Relayed like any other envelope, so split horizon applies and the
        // claim still cannot loop.
        RoutingOutcome forwarded = forwardIfEligible(envelope, fromPeerDeviceId, now, false);
        if (forwarded == RoutingOutcome.QUEUED_FOR_RELAY) {
            persistForRelay(envelope, now, "identity claim awaiting a peer");
        }
        return forwarded;
    }

    /**
     * Records a peer key the user has just confirmed out of band.
     *
     * <p>Until this is called the peer can send identity announcements but no
     * other traffic, because there is no key to verify its signatures against.
     *
     * @return the trust entry created or refreshed
     */
    public TrustStore.Entry acceptPeerKey(String label, java.security.PublicKey publicKey)
            throws GeneralSecurityException {
        TrustStore.Entry entry = trustStore.rememberUnverified(label, publicKey,
                Instant.ofEpochMilli(clock.nowMillis()));
        emit(new MeshEvent.PeerConnectionChanged(clock.nowMillis(), entry.deviceId(), true));
        return entry;
    }

    /**
     * Broadcasts this device's own key claim so neighbours can prompt their
     * owners to verify it.
     */
    public Optional<MessageEnvelope> publishIdentity() {
        IdentityAnnouncementListener listener = identityListener;
        try {
            MessageEnvelope claim = sign(MessageEnvelope.builder()
                    .messageId(newMessageId(clock.nowMillis()))
                    .origin(self.deviceId())
                    .originKeyFingerprint(self.keyFingerprint())
                    .broadcast()
                    .type(MessageType.IDENTITY)
                    .priority(Priority.LOW)
                    .createdAt(clock.nowMillis())
                    .expiresAt(clock.nowMillis() + 10L * 60 * 1000)
                    // Enough hops to cross a realistic mesh, not just the link
                    // it was born on. A key claim with a budget of one reaches
                    // only direct neighbours, so a device three phones away
                    // could never learn who it was talking to and every later
                    // message from it would be refused as untrusted.
                    //
                    // Bounded rather than broadcast-to-infinity because an
                    // identity claim is the cheapest payload an attacker can
                    // forge, and unbounded flooding of cheap payloads is how a
                    // battery gets drained. Four hops covers a site.
                    .hopLimit(IDENTITY_HOP_LIMIT)
                    .payload(IdentityAnnouncement.of(self).encode())
                    .build());
            if (listener != null) {
                // Nothing to announce to yet; the transport decides reachability.
                listener.onIdentityPublished(claim);
            }
            return Optional.of(claim);
        } catch (ProtocolException | GeneralSecurityException e) {
            return Optional.empty();
        }
    }

    // ----------------------------------------------------------------- triage

    /**
     * Runs advisory triage after the message has already been forwarded.
     *
     * <p>Ordering is the point: an SOS must never wait on a text classifier,
     * even a local one. The result is an event, not a dispatch decision, and a
     * classifier failure is swallowed.
     */
    private void classifyForAdvisory(MessageEnvelope envelope, long now) {
        if (envelope.type() == MessageType.ACK || envelope.payloadLength() == 0) {
            return;
        }
        try {
            String text = new String(envelope.payload(),
                    java.nio.charset.StandardCharsets.UTF_8);
            var result = triageClassifier.classify(text);
            emit(new MeshEvent.TriageSuggestion(now, self.deviceId(), envelope.messageId(), result));
        } catch (RuntimeException e) {
            // Advisory only: never let it affect the message path.
        }
    }

    // ----------------------------------------------------------------- helpers

    private void persistForRelay(MessageEnvelope envelope, long now, String detail) {
        if (envelope.originDeviceId().equals(self.deviceId())) {
            return;
        }
        byte[] encoded;
        try {
            encoded = EnvelopeCodec.encode(envelope);
        } catch (ProtocolException e) {
            return;
        }
        if (store.find(envelope.messageId()).isPresent()) {
            return;
        }
        store.upsert(StoredMessage.of(envelope, encoded, now)
                .withState(DeliveryState.RELAYED, detail));
    }

    private void countAndReject(long now, MessageEnvelope envelope, RejectReason reason,
                                String detail, String fromPeerDeviceId) {
        switch (reason) {
            case EXPIRED -> metrics.recordExpired();
            case HOP_LIMIT_EXCEEDED -> metrics.recordHopLimitDrop();
            case RATE_LIMITED -> metrics.recordRateLimitDrop();
            case INVALID_SIGNATURE, UNTRUSTED_ORIGIN, ORIGIN_BLOCKED -> metrics.recordUntrustedDrop();
            case DUPLICATE -> metrics.recordDuplicate();
            default -> metrics.recordMalformedDrop();
        }
        reject(now, envelope, reason, detail, fromPeerDeviceId);
    }

    private void reject(long now, MessageEnvelope envelope, RejectReason reason,
                        String detail, String fromPeerDeviceId) {
        emit(new MeshEvent.Rejected(now, self.deviceId(),
                envelope == null ? "undecodable" : envelope.messageId(),
                envelope == null ? null : envelope.originDeviceId(),
                reason, detail, fromPeerDeviceId));
    }

    private static RoutingOutcome outcomeFor(RejectReason reason) {
        return switch (reason) {
            case EXPIRED -> RoutingOutcome.DROPPED_EXPIRED;
            case HOP_LIMIT_EXCEEDED -> RoutingOutcome.DROPPED_HOP_LIMIT;
            case RATE_LIMITED -> RoutingOutcome.DROPPED_RATE_LIMITED;
            case INVALID_SIGNATURE, UNTRUSTED_ORIGIN, ORIGIN_BLOCKED ->
                    RoutingOutcome.DROPPED_UNTRUSTED;
            case DUPLICATE -> RoutingOutcome.DUPLICATE;
            default -> RoutingOutcome.DROPPED_MALFORMED;
        };
    }

    private void emit(MeshEvent event) {
        for (MeshEventListener listener : eventListeners) {
            try {
                listener.onEvent(event);
            } catch (RuntimeException e) {
                // Observability must never break the data path.
            }
        }
    }

    @Override
    public void close() {
        eventListeners.clear();
        deliveryListener = null;
        identityListener = null;
    }

    /** Snapshot of this node's view of the mesh, for the dashboard. */
    public NodeSnapshot snapshot() {
        return new NodeSnapshot(self.deviceId(), self.displayName(), self.keyFingerprint(),
                transport == null ? Set.of() : Set.copyOf(transport.connectedPeerIds()),
                store.pendingCount(), metrics.forwardedCount(), metrics.acceptedCount());
    }

    /**
     * Immutable view of one node.
     *
     * @param deviceId stable key-derived identity
     * @param displayName human label
     * @param keyFingerprint short code shown for in-person verification
     * @param connectedPeerIds directly reachable neighbours right now
     * @param pendingMessages non-terminal records in the store
     * @param forwardedCount lifetime forwards performed by this node
     * @param acceptedCount lifetime validated messages accepted by this node
     */
    public record NodeSnapshot(String deviceId, String displayName, String keyFingerprint,
                               Set<String> connectedPeerIds, int pendingMessages,
                               long forwardedCount, long acceptedCount) {
    }

    /** Surfaces unsigned key claims so the UI can ask the user to verify them. */
    @FunctionalInterface
    public interface IdentityAnnouncementListener {

        /**
         * @param envelope the claim envelope, for freshness checks
         * @param announcement the parsed claim
         * @param fromPeerDeviceId the neighbour that relayed it
         */
        void onIdentityAnnouncement(MessageEnvelope envelope, IdentityAnnouncement announcement,
                                    String fromPeerDeviceId);

        /** Notification that this device published its own claim. */
        default void onIdentityPublished(MessageEnvelope claim) {
        }
    }
}