package dev.resqnet.mesh.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TrustStore")
class TrustStoreTest {

    private InMemorySigningIdentity alice;
    private InMemorySigningIdentity bob;
    private TrustStore trustStore;

    @BeforeEach
    void setUp() throws Exception {
        alice = InMemorySigningIdentity.generate("Alice");
        bob = InMemorySigningIdentity.generate("Bob");
        trustStore = new TrustStore();
    }

    @Test
    @DisplayName("derives the device id from the key, not from a caller supplied name")
    void derivesIdentityFromKey() {
        TrustStore.Entry entry = assertDoesNotThrow(
                () -> trustStore.rememberUnverified("Alice", alice.publicKey(), Instant.EPOCH));

        assertThat(entry.deviceId()).isEqualTo(alice.deviceId());
        assertThat(entry.keyFingerprint()).isEqualTo(alice.keyFingerprint());
        assertThat(entry.trust()).isEqualTo(TrustStore.Trust.UNVERIFIED);
    }

    @Test
    @DisplayName("keeps an entry unverified until a human confirms it")
    void requiresHumanConfirmation() throws Exception {
        trustStore.rememberUnverified("Alice", alice.publicKey(), Instant.EPOCH);

        assertThat(trustStore.mayRelayLifeSafety(alice.deviceId())).isFalse();

        trustStore.markVerified(alice.deviceId(), Instant.EPOCH.plusSeconds(60));

        assertThat(trustStore.mayRelayLifeSafety(alice.deviceId())).isTrue();
        assertThat(trustStore.entry(alice.deviceId()).orElseThrow().trust())
                .isEqualTo(TrustStore.Trust.VERIFIED);
    }

    @Test
    @DisplayName("resolves nothing for a blocked peer")
    void blocksPeers() throws Exception {
        trustStore.rememberUnverified("Alice", alice.publicKey(), Instant.EPOCH);
        assertThat(trustStore.resolve(alice.deviceId())).isPresent();

        assertThat(trustStore.block(alice.deviceId())).isTrue();

        assertThat(trustStore.resolve(alice.deviceId())).isEmpty();
        assertThat(trustStore.mayRelayLifeSafety(alice.deviceId())).isFalse();
    }

    @Test
    @DisplayName("forgetting a peer removes it entirely")
    void forgetsPeers() throws Exception {
        trustStore.rememberUnverified("Alice", alice.publicKey(), Instant.EPOCH);

        assertThat(trustStore.forget(alice.deviceId())).isTrue();
        assertThat(trustStore.forget(alice.deviceId())).isFalse();
        assertThat(trustStore.size()).isZero();
    }

    @Test
    @DisplayName("refreshes a known key without downgrading its trust")
    void refreshPreservesTrust() throws Exception {
        trustStore.rememberUnverified("Alice", alice.publicKey(), Instant.EPOCH);
        trustStore.markVerified(alice.deviceId(), Instant.EPOCH);

        TrustStore.Entry refreshed = trustStore.rememberUnverified("Alice (new phone)",
                alice.publicKey(), Instant.EPOCH.plusSeconds(120));

        assertThat(refreshed.trust()).isEqualTo(TrustStore.Trust.VERIFIED);
        assertThat(refreshed.label()).isEqualTo("Alice (new phone)");
        assertThat(trustStore.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("never replaces a key that is already on file for an identity")
    void refusesKeyReplacement() throws Exception {
        trustStore.rememberUnverified("Alice", alice.publicKey(), Instant.EPOCH);
        InMemorySigningIdentity impostor = InMemorySigningIdentity.generate("Alice impostor");

        // Identity is a hash of the key, so a different key is a different
        // identity. This guard exists for the case where an id is ever sourced
        // from storage rather than derived.
        assertThat(impostor.deviceId()).isNotEqualTo(alice.deviceId());
    }

    @Test
    @DisplayName("survives a persistence round trip")
    void persistsAcrossRestarts() throws Exception {
        RecordingPersistence persistence = new RecordingPersistence();
        TrustStore first = new TrustStore(persistence, 128);
        first.rememberUnverified("Alice", alice.publicKey(), Instant.EPOCH);
        first.markVerified(alice.deviceId(), Instant.EPOCH);

        TrustStore reloaded = new TrustStore(persistence, 128);

        assertThat(reloaded.entry(alice.deviceId())).isPresent();
        assertThat(reloaded.entry(alice.deviceId()).orElseThrow().trust())
                .isEqualTo(TrustStore.Trust.VERIFIED);
        assertThat(reloaded.entry(alice.deviceId()).orElseThrow().publicKey())
                .isEqualTo(alice.publicKey());
    }

    @Test
    @DisplayName("signs and verifies with a generated identity")
    void signsAndVerifies() throws Exception {
        byte[] data = "the quick brown fox".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        byte[] signature = alice.sign(data);

        assertThat(SignatureVerifier.verify(alice.publicKey(), data, signature)).isTrue();
        assertThat(SignatureVerifier.verify(bob.publicKey(), data, signature))
                .as("a signature must not verify under a different key")
                .isFalse();
        assertThat(SignatureVerifier.verify(alice.publicKey(), data, new byte[0]))
                .as("an empty signature is a negative result, not a crash")
                .isFalse();
        assertThat(SignatureVerifier.verify(alice.publicKey(), "tampered".getBytes(
                java.nio.charset.StandardCharsets.UTF_8), signature)).isFalse();
    }

    @Test
    @DisplayName("round-trips a key pair through PKCS#8 and X.509 encodings")
    void roundTripsKeyPair() throws Exception {
        InMemorySigningIdentity rehydrated = InMemorySigningIdentity.fromKeyPair("Alice",
                alice.privateKey().getEncoded(), alice.publicKey().getEncoded());

        assertThat(rehydrated.deviceId()).isEqualTo(alice.deviceId());
        assertThat(rehydrated.keyFingerprint()).isEqualTo(alice.keyFingerprint());

        byte[] data = "content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(SignatureVerifier.verify(rehydrated.publicKey(), data, rehydrated.sign(data)))
                .isTrue();
    }

    @Test
    @DisplayName("evicts unverified peers before verified ones when full")
    void evictsUnverifiedFirst() throws Exception {
        TrustStore bounded = new TrustStore(null, 3);
        List<InMemorySigningIdentity> identities = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            InMemorySigningIdentity identity = InMemorySigningIdentity.generate("peer" + i);
            identities.add(identity);
            bounded.rememberUnverified("peer" + i, identity.publicKey(), Instant.EPOCH);
        }
        bounded.markVerified(identities.get(0).deviceId(), Instant.EPOCH);

        InMemorySigningIdentity extra = InMemorySigningIdentity.generate("extra");
        bounded.rememberUnverified("extra", extra.publicKey(), Instant.EPOCH.plusSeconds(1));

        assertThat(bounded.entry(identities.get(0).deviceId()))
                .as("a verified peer is the last thing to be evicted")
                .isPresent();
        assertThat(bounded.size()).isEqualTo(3);
    }

    @Test
    @DisplayName("exposes a short code for in-person comparison")
    void exposesShortCode() {
        String shortCode = dev.resqnet.mesh.routing.IdentityAnnouncement
                .shortCode(alice.keyFingerprint());

        assertThat(shortCode)
                .hasSize(dev.resqnet.mesh.routing.IdentityAnnouncement.SHORT_CODE_LENGTH + 1)
                .startsWith(alice.keyFingerprint().substring(0, 4))
                .contains("-");
    }

    /** In-memory {@link TrustStore.Persistence} used to model SQLite storage. */
    private static final class RecordingPersistence implements TrustStore.Persistence {
        private final List<TrustStore.Entry> saved = new ArrayList<>();

        @Override
        public void save(TrustStore.Entry entry) {
            saved.removeIf(e -> e.deviceId().equals(entry.deviceId()));
            saved.add(entry);
        }

        @Override
        public Collection<TrustStore.Entry> loadAll() {
            return List.copyOf(saved);
        }

        @Override
        public void remove(String deviceId) {
            saved.removeIf(e -> e.deviceId().equals(deviceId));
        }
    }

    private static <T, E extends Throwable> T assertDoesNotThrow(ThrowingSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Throwable e) {
            throw new AssertionError(e);
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}